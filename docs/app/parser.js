/*
 * Разбор страниц расписания www.smtu.ru.
 *
 * Один и тот же файл работает в двух местах: в браузере (подключается тегом
 * script) и в Node внутри GitHub Actions, где сборщик данных подсовывает ему
 * DOMParser от linkedom. Поэтому здесь нет ни обращений к window, ни к
 * глобальному document — только к документу, полученному из DOMParser.
 *
 * Логика та же, что в Android-приложении: читаем табличный вид
 * (#table-container) — единственный, где у занятия указана группа, — а колонки
 * ищем по заголовкам, чтобы переставленный столбец не сдвинул данные молча.
 *
 * Вёрстка сменилась 15.09.2026: точных дат проведения на странице больше нет,
 * чётность переехала из класса строки в её id (week-up/down/both-container), и
 * появилось занятие «обе недели». Прежний разбор оставлен рядом — он нужен для
 * сохранённых страниц в тестах и на случай отката.
 */
'use strict';

/** Строки расписания со страницы группы или преподавателя. */
function parseSchedule(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const table = doc.querySelector('#table-container');
  const out = [];
  if (!table) return out;

  let blocks = [...table.querySelectorAll('.js-day-block')];
  if (!blocks.length) blocks = [...table.querySelectorAll('div.card')];

  for (const block of blocks) {
    const heading = block.querySelector('h2, h3');
    const day = heading ? heading.textContent.trim() : '';

    const cols = {};
    let i = 0;
    for (const th of block.querySelectorAll('th[scope="col"]')) {
      const name = th.textContent.trim().toLowerCase();
      if (name.startsWith('время')) cols.time = i;
      else if (name.startsWith('дат')) cols.dates = i;
      else if (name.startsWith('аудитор')) cols.room = i;
      else if (name.startsWith('групп')) cols.group = i;
      else if (name.startsWith('предмет') || name.startsWith('дисциплин')) cols.subject = i;
      else if (name.startsWith('преподават')) cols.teacher = i;
      i++;
    }
    const col = Object.assign({ time: 0, dates: -1, room: 2, group: 3, subject: 4, teacher: 5 }, cols);

    for (const tr of block.querySelectorAll('tr.js-week-container')) {
      const week = weekOf(tr);
      if (!week) continue;
      const cells = tr.querySelectorAll('th, td');
      const cell = n => (n >= 0 && cells[n]) || null;
      const text = n => {
        const c = cell(n);
        return c ? c.textContent.replace(/\s+/g, ' ').trim() : '';
      };

      const subjectCell = cell(col.subject);
      if (!subjectCell) continue;

      const lines = subjectCell.innerHTML.split(/<br\s*\/?>/i)
        .map(part => {
          const box = doc.createElement('div');
          box.innerHTML = part;
          return box.textContent.replace(/\s+/g, ' ').trim();
        })
        .filter(Boolean);
      if (!lines.length) continue;

      const timeMatch = text(col.time).match(/(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})/);
      const lesson = {
        day,
        upper: week !== 'down',
        both: week === 'both',
        time: timeMatch ? timeMatch[1] + ' - ' + timeMatch[2] : '',
        dateRange: text(col.dates),
        room: text(col.room),
        group: text(col.group),
        subject: lines[0],
        type: lines[1] || '',
        note: '',
        teacher: text(col.teacher),
        teacherId: '',
        days: []
      };

      // третья строка предмета — либо преподаватель без карточки, либо примечание
      for (const extra of lines.slice(2)) {
        if (!lesson.teacher && /^\p{Lu}[\p{L}-]+(\s+\p{Lu}[\p{L}]*\.?){1,3}$/u.test(extra)) lesson.teacher = extra;
        else lesson.note = lesson.note ? lesson.note + '; ' + extra : extra;
      }

      const teacherCell = cell(col.teacher);
      const link = teacherCell && teacherCell.querySelector('a[href*="/viewperson/"]');
      if (link) {
        const id = link.getAttribute('href').match(/(\d+)/);
        if (id) lesson.teacherId = id[0];
      }

      const datesCell = cell(col.dates);
      const title = datesCell ? (datesCell.getAttribute('title') || '') : '';
      for (const d of title.split(/\s*,\s*/)) {
        const m = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(d.trim());
        if (!m) continue;
        const ed = Math.floor(Date.UTC(+m[3], +m[2] - 1, +m[1]) / 86400000);
        if (!lesson.days.includes(ed)) lesson.days.push(ed);
      }
      lesson.days.sort((a, b) => a - b);

      if (lesson.subject) out.push(lesson);
    }
  }
  return out;
}

/** Чётность строки: id с 15.09.2026, класс — прежняя вёрстка. */
function weekOf(tr) {
  const id = tr.getAttribute('id') || '';
  const m = /^week-(up|down|both)-container$/.exec(id);
  if (m) return m[1];
  if (tr.classList.contains('js-week-1')) return 'up';
  if (tr.classList.contains('js-week-2')) return 'down';
  return null;
}

/**
 * Чётность текущей недели: «Сегодня: 20 Сентября 2026 года, Воскресенье,
 * верхняя неделя». Единственный машиночитаемый признак, оставшийся на странице
 * после того, как оттуда убрали даты занятий.
 *
 * @return {{day: number, upper: boolean}|null} день в epoch-днях и чётность
 */
function parseWeekAnchor(html) {
  const months = ['январ', 'феврал', 'март', 'апрел', 'мая', 'июн', 'июл',
                  'август', 'сентябр', 'октябр', 'ноябр', 'декабр'];
  const m = /Сегодня:\s*(\d{1,2})\s+(\p{L}+)\s+(\d{4})[^<]*?(верхн|нижн)/iu.exec(html);
  if (!m) return null;
  const name = m[2].toLowerCase();
  const month = months.findIndex(root => name.startsWith(root.slice(0, 4)));
  if (month < 0) return null;
  return {
    day: Math.floor(Date.UTC(+m[3], month, +m[1]) / 86400000),
    upper: m[4].toLowerCase() === 'верхн'
  };
}

/** Список групп со страницы /ru/listschedule/. */
function parseGroups(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const seen = new Map();
  for (const a of doc.querySelectorAll('a[href*="/viewschedule_new/"]')) {
    const id = (a.getAttribute('href').match(/viewschedule_new\/(\d+)/) || [])[1];
    const name = a.textContent.replace(/\s+/g, ' ').trim();
    if (id && name && !seen.has(id)) seen.set(id, { id, name });
  }
  return [...seen.values()].sort((a, b) => a.name.localeCompare(b.name, 'ru', { numeric: true }));
}

/** «Расписание занятий группы 12826-11» → «12826-11». */
function parseTitle(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  for (const h1 of doc.querySelectorAll('h1')) {
    const t = h1.textContent.replace(/\s+/g, ' ').trim();
    const i = t.indexOf('Расписание занятий');
    if (i < 0) continue;
    let rest = t.slice(i + 'Расписание занятий'.length).trim();
    for (const p of ['группы', 'группа', 'преподавателя', 'преподаватель', 'аудитории'])
      if (rest.startsWith(p)) { rest = rest.slice(p.length).trim(); break; }
    return rest;
  }
  return '';
}

/** Проставляет время начала и конца в минутах — их не хранят в JSON. */
function hydrate(lessons) {
  for (const l of lessons) {
    const m = /(\d{2}):(\d{2})\s*-\s*(\d{2}):(\d{2})/.exec(l.time || '');
    l.start = m ? +m[1] * 60 + +m[2] : -1;
    l.end = m ? +m[3] * 60 + +m[4] : -1;
  }
  return lessons;
}
