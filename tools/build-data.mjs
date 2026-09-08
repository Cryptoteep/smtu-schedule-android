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
import { writeFile, mkdir, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { parseHTML } from 'linkedom';

const HOST = 'https://www.smtu.ru';
const OUT = new URL('../docs/data/', import.meta.url);
const UA = 'Mozilla/5.0 (Linux; Android 14) Chrome/126.0 Mobile';
const PAUSE_MS = 250;              // не долбим сайт университета
const RETRIES = 3;

const limitArg = process.argv.indexOf('--limit');
const LIMIT = limitArg > 0 ? Number(process.argv[limitArg + 1]) : Infinity;

// Парсер один на всех: тот же файл подключает и браузер.
const parserSource = await readFile(new URL('../docs/app/parser.js', import.meta.url), 'utf8');
const parser = new Function('DOMParser', parserSource + '\nreturn { parseSchedule, parseGroups, parseTitle };')(
  class {
    parseFromString(html) { return parseHTML(html).document; }
  }
);

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
let lessonsTotal = 0, failed = 0;

for (const [i, group] of groups.slice(0, LIMIT).entries()) {
  try {
    const html = await fetchPage('/ru/viewschedule_new/' + group.id + '/');
    const lessons = parser.parseSchedule(html);
    const title = parser.parseTitle(html) || group.name;

    await writeFile(new URL('g/' + group.id + '.json', OUT),
      JSON.stringify({ id: group.id, name: title, lessons }));

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
for (const [id, box] of byTeacher) {
  box.lessons.sort((a, b) => (a.days[0] || 0) - (b.days[0] || 0));
  await writeFile(new URL('t/' + id + '.json', OUT),
    JSON.stringify({ id, name: box.name, lessons: box.lessons }));
  index.teachers.push({ id, name: box.name, lessons: box.lessons.length });
}
index.teachers.sort((a, b) => a.name.localeCompare(b.name, 'ru'));

index.groups.sort((a, b) => a.name.localeCompare(b.name, 'ru', { numeric: true }));
await writeFile(new URL('index.json', OUT), JSON.stringify(index));

const seconds = Math.round((Date.now() - started) / 1000);
console.log(`готово за ${seconds} с: ${index.groups.length} групп, ${index.teachers.length} преподавателей, ${lessonsTotal} занятий, ошибок ${failed}`);
if (failed > groups.length * 0.2) {
  console.error('слишком много ошибок — не публикуем такой срез');
  process.exit(1);
}
