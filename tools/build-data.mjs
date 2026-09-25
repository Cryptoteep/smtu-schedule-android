/*
 * Собирает расписания всех групп СПбГМТУ в JSON, который читает веб-версия.
 *
 * Запускается в GitHub Actions по расписанию: раннер ходит на smtu.ru, парсит
 * страницы и кладёт результат в docs/data/. Благодаря этому веб-приложение
 * берёт данные со своего же адреса — без прокси и без чужих серверов.
 *
 *   docs/data/index.json    список групп и преподавателей + когда обновлено
 *   docs/data/g/<id>.json   расписание одной группы
 *   docs/data/t/<id>.json   расписание преподавателя
 *
 * Страницы преподавателей не качаются: их расписание собирается из занятий
 * групп, где у каждой строки есть id преподавателя. Время обновления пишется
 * только в index.json — иначе каждый прогон менял бы все файлы, и репозиторий
 * рос бы на несколько мегабайт в сутки.
 *
 * Запуск: node tools/build-data.mjs [--limit N]
 */
import { writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { loadParser } from './parser.mjs';
import { verdict } from './snapshot-check.mjs';

// адрес можно подменить — так проверка «а упадём ли мы на сломанной вёрстке»
// гоняется на локальной странице, без похода на сайт университета
const HOST = process.env.SMTU_HOST || 'https://www.smtu.ru';
const OUT = new URL('../docs/data/', import.meta.url);
const UA = 'Mozilla/5.0 (Linux; Android 14) Chrome/126.0 Mobile';
const PAUSE_MS = 250;              // не долбим сайт университета
const RETRIES = 3;

const limitArg = process.argv.indexOf('--limit');
const LIMIT = limitArg > 0 ? Number(process.argv[limitArg + 1]) : Infinity;

// Парсер один на всех: тот же файл подключает и браузер.
const parser = await loadParser();

async function fetchPage(path) {
  let lastError;
  for (let attempt = 1; attempt <= RETRIES; attempt++) {
    try {
      const res = await fetch(HOST + path, {
        headers: { 'User-Agent': UA, 'Accept-Language': 'ru' },
        signal: AbortSignal.timeout(30000)
      });
      if (!res.ok) throw new Error('ответ ' + res.status);
      const text = await res.text();
      if (text.length < 1000) throw new Error('слишком короткий ответ');
      return text;
    } catch (e) {
      lastError = e;
      await new Promise(r => setTimeout(r, 1000 * attempt));
    }
  }
  throw lastError;
}

const started = Date.now();
await mkdir(new URL('g/', OUT), { recursive: true });
await mkdir(new URL('t/', OUT), { recursive: true });

const groups = parser.parseGroups(await fetchPage('/ru/listschedule/'));
if (!groups.length) throw new Error('список групп пуст — вёрстка сайта изменилась?');
console.log('групп в списке:', groups.length);

const index = { updated: new Date().toISOString(), groups: [], teachers: [] };
const byTeacher = new Map();          // id преподавателя -> его занятия во всех группах
let lessonsTotal = 0, failed = 0, empty = 0;
const filled = { withTime: 0, withRoom: 0, withTeacher: 0 };   // для сторожа колонок
let anchor = null;                    // чётность недели, как её пишет сайт

for (const [i, group] of groups.slice(0, LIMIT).entries()) {
  try {
    const html = await fetchPage('/ru/viewschedule_new/' + group.id + '/');
    const lessons = parser.parseSchedule(html);
    const title = parser.parseTitle(html) || group.name;
    if (!anchor) anchor = parser.parseWeekAnchor(html);
    if (!lessons.length) empty++;
    for (const l of lessons) {
      if (/^\d\d:\d\d - \d\d:\d\d$/.test(l.time)) filled.withTime++;
      if (l.room) filled.withRoom++;
      if (l.teacher) filled.withTeacher++;
    }

    await writeFile(new URL('g/' + group.id + '.json', OUT),
      JSON.stringify({ id: group.id, name: title, lessons, ...anchorFields() }));

    for (const lesson of lessons) {
      if (!lesson.teacherId) continue;
      if (!byTeacher.has(lesson.teacherId))
        byTeacher.set(lesson.teacherId, { name: lesson.teacher, lessons: [] });
      byTeacher.get(lesson.teacherId).lessons.push(Object.assign({}, lesson, { group: lesson.group || title }));
    }

    index.groups.push({ id: group.id, name: title, lessons: lessons.length });
    lessonsTotal += lessons.length;
    if ((i + 1) % 50 === 0) console.log(`  ${i + 1}/${groups.length}…`);
  } catch (e) {
    failed++;
    console.warn('  не удалось:', group.name, '—', e.message);
    // старый файл оставляем на месте: лучше вчерашнее расписание, чем никакого
    const old = new URL('g/' + group.id + '.json', OUT);
    if (existsSync(old)) index.groups.push({ id: group.id, name: group.name, lessons: -1 });
  }
  await new Promise(r => setTimeout(r, PAUSE_MS));
}

// расписания преподавателей — из уже разобранных занятий, без единого запроса
const WEEKDAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'];

for (const [id, box] of byTeacher) {
  // дат у занятий больше нет, поэтому порядок — по дню недели и времени начала
  box.lessons.sort((a, b) =>
    WEEKDAYS.indexOf(a.day) - WEEKDAYS.indexOf(b.day) || (a.time || '').localeCompare(b.time || ''));
  await writeFile(new URL('t/' + id + '.json', OUT),
    JSON.stringify({ id, name: box.name, lessons: box.lessons, ...anchorFields() }));
  index.teachers.push({ id, name: box.name, lessons: box.lessons.length });
}
index.teachers.sort((a, b) => a.name.localeCompare(b.name, 'ru'));

index.groups.sort((a, b) => a.name.localeCompare(b.name, 'ru', { numeric: true }));
Object.assign(index, anchorFields());
await writeFile(new URL('index.json', OUT), JSON.stringify(index));

const seconds = Math.round((Date.now() - started) / 1000);
console.log(`готово за ${seconds} с: ${index.groups.length} групп, ${index.teachers.length} преподавателей, ${lessonsTotal} занятий, пустых ${empty}, ошибок ${failed}`);

const stop = verdict({
  groups: groups.length, lessons: lessonsTotal, empty, failed, anchor, ...filled
});
if (stop) {
  console.error(stop);
  process.exit(1);
}

/** Чётность недели со страницы сайта — в каждый файл, чтобы её знали и офлайн. */
function anchorFields() {
  return anchor ? { anchorDay: anchor.day, anchorUpper: anchor.upper } : {};
}
