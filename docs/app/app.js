/*
 * Расписание СПбГМТУ — веб-версия.
 *
 * Тот же подход, что и в Android-приложении: читаем табличный вид страницы
 * (только там у занятия указана группа), чётность недели выводим из точных дат
 * самих занятий, весь семестр держим в localStorage — дальше приложение
 * работает офлайн.
 *
 * У сайта университета нет ни API, ни CORS-заголовков, поэтому страницы идут
 * через прокси, который только пробрасывает запрос и добавляет CORS.
 */
'use strict';

/*
 * Данные лежат рядом с приложением: их раз в несколько часов собирает
 * GitHub Actions прямо со страниц smtu.ru (см. tools/build-data.mjs).
 * Поэтому приложению не нужны ни прокси, ни чужие серверы — только свой адрес.
 */
const DATA = '../data';
const STORE = 'smtu.';
const FETCH_TIMEOUT = 20000;

const DAY_FULL = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'];
const DAY_SHORT = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
const MONTH_GEN = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
                   'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];

// ------------------------------------------------------------------ даты

const DAY_MS = 86400000;

/** Дни от эпохи для локальной даты — без сюрпризов часового пояса. */
function toEpochDay(y, m, d) { return Math.floor(Date.UTC(y, m - 1, d) / DAY_MS); }
function fromEpochDay(ed) { const t = new Date(ed * DAY_MS); return [t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate()]; }
function dayOfWeek(ed) { return ((ed + 3) % 7 + 7) % 7; }          // 0 = понедельник
function monday(ed) { return ed - dayOfWeek(ed); }
function today() { const n = new Date(); return toEpochDay(n.getFullYear(), n.getMonth() + 1, n.getDate()); }
function nowMinutes() { const n = new Date(); return n.getHours() * 60 + n.getMinutes(); }
function dayMonth(ed) { const p = fromEpochDay(ed); return p[2] + ' ' + MONTH_GEN[p[1] - 1]; }
function dayMonthYear(ed) { return dayMonth(ed) + ' ' + fromEpochDay(ed)[0]; }
function formatRu(ed) { const p = fromEpochDay(ed); return String(p[2]).padStart(2, '0') + '.' + String(p[1]).padStart(2, '0') + '.' + p[0]; }
function parseRu(s) {
  const m = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(s.trim());
  return m ? toEpochDay(+m[3], +m[2], +m[1]) : null;
}
function parseTime(s) {
  const m = /(\d{2}):(\d{2})/.exec(s || '');
  return m ? +m[1] * 60 + +m[2] : -1;
}
function weekRange(mon) {
  const a = fromEpochDay(mon), b = fromEpochDay(mon + 6);
  if (a[0] !== b[0]) return dayMonthYear(mon) + ' – ' + dayMonthYear(mon + 6);
  if (a[1] !== b[1]) return dayMonth(mon) + ' – ' + dayMonth(mon + 6);
  return a[2] + '–' + b[2] + ' ' + MONTH_GEN[b[1] - 1];
}

// -------------------------------------------------------------- чётность

/**
 * Какие календарные недели верхние. Сайт нигде не пишет это машиночитаемо,
 * зато каждая строка помечена js-week-1/2 и несёт свои даты — цикл считается
 * из данных и не устаревает к следующему семестру.
 */
function deriveParity(lessons) {
  const votes = new Map();
  for (const l of lessons)
    for (const d of l.days) {
      const key = monday(d);
      const v = votes.get(key) || [0, 0];
      v[l.upper ? 0 : 1]++;
      votes.set(key, v);
    }
  if (!votes.size) return { mon: toEpochDay(2026, 8, 31), upper: true, derived: false };

  let best = null;
  for (const [mon, v] of votes) {
    const margin = Math.abs(v[0] - v[1]);
    if (!best || margin > best.margin) best = { mon, upper: v[0] >= v[1], margin };
  }
  return { mon: best.mon, upper: best.upper, derived: true };
}

function isUpper(parity, ed) {
  const weeks = (monday(ed) - parity.mon) / 7;
  return (((weeks % 2) + 2) % 2 === 0) === parity.upper;
}

// ------------------------------------------------------------------ сеть

async function fetchJson(path) {
  const stop = new AbortController();
  const timer = setTimeout(() => stop.abort(), FETCH_TIMEOUT);
  try {
    const res = await fetch(DATA + path, { signal: stop.signal });
    if (!res.ok) throw new Error(res.status === 404 ? 'нет данных' : 'ответ ' + res.status);
    return await res.json();
  } catch (e) {
    throw new Error(e.name === 'AbortError' ? 'сервер не ответил'
                  : e.name === 'TypeError' ? 'нет интернета'
                  : e.message);
  } finally {
    clearTimeout(timer);
  }
}

/** Список групп: id, название и сколько у неё занятий. */
async function fetchGroups() {
  const index = await fetchJson('/index.json');
  return { groups: index.groups, updated: Date.parse(index.updated) || 0 };
}

/** Расписание одной группы. */
async function fetchGroupSchedule(id) {
  const box = await fetchJson('/g/' + id + '.json');
  return { lessons: hydrate(box.lessons || []), title: box.name || '', at: Date.parse(box.updated) || 0 };
}

// --------------------------------------------------------------- состояние

const state = {
  groupId: localStorage.getItem(STORE + 'groupId'),
  groupName: localStorage.getItem(STORE + 'groupName'),
  teacherId: null,
  teacherName: '',
  lessons: [],
  parity: { mon: toEpochDay(2026, 8, 31), upper: true, derived: false },
  fetchedAt: 0,
  dayMode: localStorage.getItem(STORE + 'dayMode') === '1',
  mon: monday(today()),
  offset: dayOfWeek(today()),
  loading: false
};

const $ = id => document.getElementById(id);

function cacheKey(teacher, id) { return STORE + (teacher ? 't' : 'g') + id; }

function loadCache(teacher, id) {
  try {
    const raw = localStorage.getItem(cacheKey(teacher, id));
    if (!raw) return null;
    const box = JSON.parse(raw);
    return { lessons: box.lessons || [], title: box.title || '', at: box.at || 0 };
  } catch (e) { return null; }
}

function saveCache(teacher, id, lessons, title) {
  try {
    localStorage.setItem(cacheKey(teacher, id),
      JSON.stringify({ lessons, title, at: Date.now() }));
  } catch (e) { /* переполнение хранилища — не беда, просто не кэшируем */ }
}

function setLessons(lessons, title, at) {
  state.lessons = lessons;
  state.parity = deriveParity(lessons);
  state.fetchedAt = at || 0;
  if (title) {
    if (state.teacherId) state.teacherName = title;
    else { state.groupName = title; localStorage.setItem(STORE + 'groupName', title); }
  }
  render();
}

async function load(teacher, id, name) {
  const cached = loadCache(teacher, id);
  if (cached) setLessons(hydrate(cached.lessons), cached.title || name, cached.at);
  else setLessons([], name, 0);

  state.loading = true;
  render();
  try {
    const box = await fetchJson('/' + (teacher ? 't' : 'g') + '/' + id + '.json');
    const lessons = hydrate(box.lessons || []);
    const title = box.name || name || '';
    if (lessons.length) {
      saveCache(teacher, id, lessons, title);
      setLessons(lessons, title, await updatedAt());
    }
  } catch (e) {
    if (!cached) toast('Не удалось загрузить: ' + e.message);
  } finally {
    state.loading = false;
    render();
  }
}

/** Когда данные последний раз собирали со smtu.ru. */
let updatedCache = null;
async function updatedAt() {
  if (updatedCache !== null) return updatedCache;
  try {
    const index = await fetchJson('/index.json');
    updatedCache = Date.parse(index.updated) || Date.now();
  } catch (e) {
    updatedCache = Date.now();
  }
  return updatedCache;
}

// ----------------------------------------------------------------- запросы

function lessonsOn(ed) {
  const upper = isUpper(state.parity, ed);
  const weekday = DAY_FULL[dayOfWeek(ed)];
  return state.lessons
    .filter(l => l.days.length ? l.days.includes(ed)
                               : l.upper === upper && l.day.toLowerCase() === weekday.toLowerCase())
    .sort((a, b) => a.start - b.start);
}

function semester() {
  let min = Infinity, max = -Infinity;
  for (const l of state.lessons) for (const d of l.days) { if (d < min) min = d; if (d > max) max = d; }
  return min === Infinity ? null : { first: min, last: max };
}

function shownDay() { return state.dayMode ? state.mon + state.offset : state.mon; }

// ------------------------------------------------------------------- вид

function typeColor(type) {
  const t = (type || '').toLowerCase();
  if (t.startsWith('лекц')) return 'var(--lecture)';
  if (t.startsWith('практ') || t.startsWith('семинар')) return 'var(--practice)';
  if (t.startsWith('лаб')) return 'var(--lab)';
  if (t.includes('экзамен') || t.includes('зач') || t.includes('консультац')) return 'var(--exam)';
  return 'var(--other)';
}

function render() {
  const teacherMode = !!state.teacherId;
  $('title').textContent = teacherMode ? state.teacherName || 'Преподаватель'
                                       : state.groupName || 'Группа';

  const shown = shownDay();
  const rangeText = state.dayMode
    ? DAY_FULL[dayOfWeek(shown)] + ', ' + dayMonth(shown) + (shown === today() ? ' · сегодня' : '')
    : weekRange(state.mon) + (teacherMode ? ' · все группы' : '');
  $('range').textContent = rangeText;

  $('mode').textContent = state.dayMode ? 'День' : 'Неделя';

  const parity = $('parity');
  if (state.lessons.length) {
    parity.hidden = false;
    const up = isUpper(state.parity, shown);
    parity.textContent = up ? 'ВЕРХНЯЯ' : 'НИЖНЯЯ';
    parity.className = 'pill week ' + (up ? 'upper' : 'lower');
  } else parity.hidden = true;

  const atToday = state.dayMode ? shown === today() : state.mon === monday(today());
  $('today').hidden = atToday;

  renderDays();
  renderNowBar();
  renderBody();
  renderStatus();
}

/**
 * Сколько осталось до конца идущей пары — или до начала ближайшей сегодня.
 * Считается от текущего момента независимо от того, какой день открыт.
 */
function renderNowBar() {
  const bar = $('nowbar');
  const day = today();
  const minutes = nowMinutes();
  const lessons = lessonsOn(day);

  const running = lessons.find(l => minutes >= l.start && minutes < l.end);
  if (running) {
    bar.textContent = 'Идёт: ' + running.subject + ' · осталось ' + humanMinutes(running.end - minutes);
    bar.hidden = false;
    return;
  }
  const next = lessons.find(l => l.start > minutes);
  if (next) {
    bar.textContent = 'Следующая: ' + next.subject + ' через ' + humanMinutes(next.start - minutes) +
      ' · в ' + next.time.split('-')[0].trim();
    bar.hidden = false;
    return;
  }
  bar.hidden = true;
}

/** 45 -> «45 минут», 95 -> «1 час 35 минут». */
function humanMinutes(minutes) {
  if (minutes < 1) return 'меньше минуты';
  if (minutes < 60) return minutes + ' ' + plural(minutes, 'минуту', 'минуты', 'минут');
  const h = Math.floor(minutes / 60), m = minutes % 60;
  const hours = h + ' ' + plural(h, 'час', 'часа', 'часов');
  return m === 0 ? hours : hours + ' ' + m + ' ' + plural(m, 'минуту', 'минуты', 'минут');
}

function renderDays() {
  const box = $('days');
  box.hidden = !state.dayMode;
  if (!state.dayMode) return;
  box.innerHTML = '';
  for (let i = 0; i < 7; i++) {
    const count = lessonsOn(state.mon + i).length;
    const b = document.createElement('button');
    b.className = i === state.offset ? 'sel' : '';
    b.innerHTML = '<b>' + DAY_SHORT[i] + '</b><span>' + (count || '·') + '</span>';
    b.onclick = () => { state.offset = i; render(); };
    box.appendChild(b);
  }
}

function renderBody() {
  const main = $('body');
  main.innerHTML = '';

  if (!state.lessons.length) {
    main.innerHTML = '<div class="hint">' +
      (state.loading ? 'Загружаю расписание…'
                     : state.groupId ? 'Расписание не загружено.\nНажмите ⟳ при подключении к сети.'
                                     : 'Выберите свою группу.') + '</div>';
    return;
  }

  const days = state.dayMode ? [shownDay()] : [...Array(7).keys()].map(i => state.mon + i);
  let printed = 0;

  for (const ed of days) {
    const lessons = lessonsOn(ed);
    if (!lessons.length && (state.dayMode || dayOfWeek(ed) >= 5)) continue;

    const head = document.createElement('div');
    head.className = 'dayhead' + (ed === today() ? ' today' : '');
    head.textContent = DAY_FULL[dayOfWeek(ed)] + ', ' + dayMonth(ed) + (ed === today() ? '  ·  сегодня' : '');
    main.appendChild(head);

    if (!lessons.length) {
      const free = document.createElement('div');
      free.className = 'free';
      free.textContent = 'свободно';
      main.appendChild(free);
      continue;
    }
    printed += lessons.length;
    for (const l of lessons) main.appendChild(lessonCard(l, ed));
  }

  if (!printed) {
    const sem = semester();
    const shown = shownDay();
    main.innerHTML = '<div class="hint">' + (sem && (shown < sem.first || shown > sem.last)
      ? 'Вне семестра.\nРасписание есть с ' + dayMonth(sem.first) + ' по ' + dayMonthYear(sem.last) + '.'
      : state.dayMode ? 'Занятий нет — свободный день.' : 'На этой неделе занятий нет.') + '</div>';
  }
}

function lessonCard(l, ed) {
  const card = document.createElement('div');
  card.className = 'lesson';
  if (ed === today()) {
    const now = nowMinutes();
    if (now >= l.start && now < l.end) card.classList.add('now');
    else if (now >= l.end) card.classList.add('past');
  }
  const who = state.teacherId ? l.group : l.teacher;
  const meta = [l.type, who, l.note].filter(Boolean).join(' · ');

  card.innerHTML =
    '<div class="stripe" style="background:' + typeColor(l.type) + '"></div>' +
    '<div class="time">' + l.time.replace(' - ', '<br>') + '</div>' +
    '<div class="body"><div class="subject"></div><div class="meta"></div></div>' +
    '<div class="room"></div>';
  card.querySelector('.subject').textContent = l.subject;
  card.querySelector('.meta').textContent = meta;
  card.querySelector('.room').textContent = l.room;
  card.onclick = () => showLesson(l, ed);
  return card;
}

function renderStatus() {
  const parts = [];
  if (state.loading) parts.push('обновляю с smtu.ru…');
  if (state.lessons.length) {
    parts.push(state.lessons.length + ' ' + plural(state.lessons.length, 'занятие', 'занятия', 'занятий'));
    if (state.fetchedAt) {
      const d = new Date(state.fetchedAt);
      const when = toEpochDay(d.getFullYear(), d.getMonth() + 1, d.getDate()) === today()
        ? 'сегодня, ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
        : formatRu(toEpochDay(d.getFullYear(), d.getMonth() + 1, d.getDate()));
      parts.push('обновлено ' + when);
    }
    if (!state.parity.derived) parts.push('чётность недель неточная');
  }
  $('status').textContent = parts.join(' · ');
}

function plural(n, one, few, many) {
  const m10 = n % 10, m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

function toast(text) {
  $('status').textContent = text;
}

// --------------------------------------------------------------- диалоги

function showLesson(l, ed) {
  const box = $('lessonBody');
  const dates = l.days.length
    ? '<p class="dim">Всего занятий: ' + l.days.length + '<br>' + l.days.map(formatRu).join(', ') + '</p>'
    : '';
  box.innerHTML = '<h2></h2>' +
    '<p id="l1"></p>' +
    (l.teacher ? '<p id="l2"></p>' : '') +
    (l.dateRange ? '<p class="dim" id="l3"></p>' : '') + dates;
  box.querySelector('h2').textContent = l.subject;
  box.querySelector('#l1').textContent =
    DAY_FULL[dayOfWeek(ed)] + ', ' + dayMonth(ed) + ', ' + l.time.replace(' - ', ' – ') + '\n' +
    [l.type, l.room].filter(Boolean).join(' · ') + '\n' +
    (l.upper ? 'Верхняя' : 'Нижняя') + ' неделя' + (l.group ? ' · группа ' + l.group : '') +
    (l.note ? '\n' + l.note : '');
  box.querySelector('#l1').style.whiteSpace = 'pre-line';
  if (l.teacher) box.querySelector('#l2').textContent = 'Преподаватель: ' + l.teacher;
  if (l.dateRange) box.querySelector('#l3').textContent = l.dateRange;

  const btn = $('lessonTeacher');
  btn.hidden = !l.teacherId;
  btn.onclick = () => {
    $('lesson').close();
    state.teacherId = l.teacherId;
    state.teacherName = l.teacher;
    load(true, l.teacherId, l.teacher);
  };
  $('lesson').showModal();
}

async function openPicker() {
  const dlg = $('picker');
  const list = $('groups');
  const status = $('pickerStatus');
  const retry = $('pickerRetry');
  list.innerHTML = '';
  status.textContent = 'Загружаю список групп…';
  retry.hidden = true;
  dlg.showModal();

  let groups = [];
  const cached = localStorage.getItem(STORE + 'groups');
  if (cached) { try { groups = JSON.parse(cached); show(groups); } catch (e) { groups = []; } }

  try {
    const index = await fetchJson('/index.json');
    groups = index.groups;
    updatedCache = Date.parse(index.updated) || Date.now();
    localStorage.setItem(STORE + 'groups', JSON.stringify(groups));
    show(groups);
  } catch (e) {
    if (!groups.length) {
      status.textContent = 'Не удалось получить список групп: ' + e.message +
        '.\nПроверьте интернет и нажмите «Повторить».';
      retry.hidden = false;
      retry.onclick = () => { dlg.close(); openPicker(); };
    }
  }

  function show(all) {
    status.textContent = all.length + ' ' + plural(all.length, 'группа', 'группы', 'групп') + ' · начните вводить номер';
    const draw = () => {
      const q = $('search').value.trim().toLowerCase();
      const shown = all.filter(g => !q || g.name.toLowerCase().includes(q)).slice(0, 300);
      list.innerHTML = '';
      for (const g of shown) {
        const b = document.createElement('button');
        b.textContent = g.name;
        b.onclick = () => {
          state.groupId = g.id;
          state.groupName = g.name;
          state.teacherId = null;
          localStorage.setItem(STORE + 'groupId', g.id);
          localStorage.setItem(STORE + 'groupName', g.name);
          dlg.close();
          load(false, g.id, g.name);
        };
        list.appendChild(b);
      }
    };
    $('search').oninput = draw;
    draw();
  }
}

function openFinder() {
  const dlg = $('finder');
  const results = $('results');
  results.innerHTML = '';
  $('query').value = '';
  dlg.showModal();
  $('query').oninput = () => {
    const q = $('query').value.trim().toLowerCase();
    results.innerHTML = '';
    if (!q) return;
    const found = state.lessons.filter(l =>
      (l.subject + ' ' + l.teacher + ' ' + l.room + ' ' + l.type + ' ' + l.group + ' ' + l.note)
        .toLowerCase().includes(q)).slice(0, 60);
    for (const l of found) {
      const when = l.days.length ? DAY_SHORT[dayOfWeek(l.days[0])] + ' ' + formatRu(l.days[0]) : l.day;
      const b = document.createElement('button');
      b.textContent = when + ' · ' + l.time.replace(' - ', ' – ') + ' · ' + l.subject +
        (l.room ? ' · ' + l.room : '') + (l.teacher ? ' · ' + l.teacher : '');
      b.onclick = () => {
        dlg.close();
        if (l.days.length) {
          state.dayMode = true;
          state.mon = monday(l.days[0]);
          state.offset = dayOfWeek(l.days[0]);
          render();
          showLesson(l, l.days[0]);
        }
      };
      results.appendChild(b);
    }
    if (!found.length) results.innerHTML = '<p class="dim" style="padding:10px 8px">Ничего не найдено</p>';
  };
}

// ------------------------------------------------------------- управление

function shift(dir) {
  if (state.dayMode) {
    state.offset += dir;
    while (state.offset > 6) { state.offset -= 7; state.mon += 7; }
    while (state.offset < 0) { state.offset += 7; state.mon -= 7; }
  } else state.mon += 7 * dir;
  render();
}

function goToday() {
  state.mon = monday(today());
  state.offset = dayOfWeek(today());
  render();
}

$('prev').onclick = () => shift(-1);
$('next').onclick = () => shift(1);
$('mode').onclick = () => {
  state.dayMode = !state.dayMode;
  localStorage.setItem(STORE + 'dayMode', state.dayMode ? '1' : '0');
  render();
};
$('today').onclick = goToday;
$('find').onclick = openFinder;
$('reload').onclick = () => {
  if (state.teacherId) load(true, state.teacherId, state.teacherName);
  else if (state.groupId) load(false, state.groupId, state.groupName);
};
$('titleBox').onclick = () => {
  if (state.teacherId) {
    state.teacherId = null;
    state.teacherName = '';
    load(false, state.groupId, state.groupName);
  } else openPicker();
};
$('pickerClose').onclick = () => $('picker').close();
$('lessonClose').onclick = () => $('lesson').close();
$('finderClose').onclick = () => $('finder').close();

// свайпы: листают день или неделю в любом месте экрана
let touchX = 0, touchY = 0;
document.addEventListener('touchstart', e => {
  touchX = e.touches[0].clientX; touchY = e.touches[0].clientY;
}, { passive: true });
document.addEventListener('touchend', e => {
  const dx = e.changedTouches[0].clientX - touchX;
  const dy = e.changedTouches[0].clientY - touchY;
  if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) shift(dx < 0 ? 1 : -1);
}, { passive: true });

// подсветка «идёт сейчас» стареет — обновляем при возврате на вкладку и раз в минуту
document.addEventListener('visibilitychange', () => { if (!document.hidden) render(); });
setInterval(() => { if (!document.hidden) render(); }, 30000);   // счётчик до пары должен идти

if ('serviceWorker' in navigator)
  navigator.serviceWorker.register('sw.js').catch(() => { /* не критично */ });

// -------------------------------------------------------------------- старт

if (state.groupId) load(false, state.groupId, state.groupName);
else { render(); openPicker(); }
