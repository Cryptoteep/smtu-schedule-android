/*
 * Общий парсер docs/app/parser.js на сохранённых страницах smtu.ru.
 *
 * Запуск: node --test tools/*.test.mjs   (нужен linkedom: npm install --no-save linkedom)
 *
 * Зачем, если есть JVM-тесты: из этого файла, а не из ScheduleParser.java,
 * собирается срез в docs/data — его читают веб-версия и Android под VPN. До
 * этих тестов у него не было ни одной проверки, и правка «под новую вёрстку»
 * могла молча сломать прежнюю или разойтись с Java. Числа здесь те же, что в
 * ScheduleParserTest и ScheduleParserV2Test: оба парсера обязаны видеть одно.
 *
 * Смену вёрстки живого сайта эти тесты не ловят (копия с ним не меняется) —
 * это работа tools/snapshot-check.mjs.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { loadParser } from './parser.mjs';

const parser = await loadParser();
const page = name => readFileSync(
  new URL('../app/src/test/resources/fixtures/' + name, import.meta.url), 'utf8');

test('нынешняя вёрстка: все 32 строки и все поля первой', () => {
  const lessons = parser.parseSchedule(page('group_7798_v2.html'));
  assert.equal(lessons.length, 32);
  for (const l of lessons) {
    assert.ok(l.subject, 'у занятия есть предмет');
    assert.ok(l.day, 'и день недели: ' + l.subject);
    assert.match(l.time, /^\d\d:\d\d - \d\d:\d\d$/, 'и время: ' + l.subject);
  }
  const first = lessons[0];
  assert.equal(first.day, 'Понедельник');
  assert.equal(first.time, '11:50 - 13:20');
  assert.equal(first.subject, 'Общая и неорганическая химия');
  assert.equal(first.type, 'Лекция');
  assert.equal(first.room, 'У 167');
  assert.equal(first.group, '12226-11');
  assert.equal(first.teacher, 'Ходжаев Рустам Саломович');
  assert.equal(first.teacherId, '105760');
  assert.deepEqual(first.days, [], 'точных дат на странице больше нет');
});

test('три вида недели, как в Java', () => {
  const lessons = parser.parseSchedule(page('group_7798_v2.html'));
  const both = lessons.filter(l => l.both).length;
  const upper = lessons.filter(l => !l.both && l.upper).length;
  assert.deepEqual([both, upper, lessons.length - both - upper], [13, 9, 10]);
});

test('чётность берётся из строки «Сегодня: … верхняя неделя»', () => {
  const anchor = parser.parseWeekAnchor(page('group_7798_v2.html'));
  assert.deepEqual(anchor, { day: Math.floor(Date.UTC(2026, 8, 20) / 864e5), upper: true });
});

test('прежняя вёрстка по-прежнему читается — на случай отката', () => {
  const lessons = parser.parseSchedule(page('group_7798.html'));
  assert.equal(lessons.length, 47);
  assert.ok(lessons.some(l => l.days.length === 8), 'с точными датами');
});

test('на странице преподавателя у каждой пары есть группа', () => {
  const lessons = parser.parseSchedule(page('teacher_105760_v2.html'));
  assert.ok(lessons.length > 0);
  for (const l of lessons) assert.ok(l.group, 'группа у ' + l.subject);
});

test('список групп: обе вёрстки', () => {
  assert.equal(parser.parseGroups(page('listschedule.html')).length, 449);
  assert.equal(parser.parseGroups(page('listschedule_v2.html')).length, 412);
});

test('тип, напечатанный первым, не становится предметом', () => {
  assert.deepEqual(parser.typeSecond(['Лекция', 'Военная подготовка']), ['Военная подготовка', 'Лекция']);
  assert.deepEqual(parser.typeSecond(['Практическая психология', 'Лекция']), ['Практическая психология', 'Лекция']);
  assert.deepEqual(parser.typeSecond(['Физика']), ['Физика']);
});

test('обрезанная или чужая страница не роняет парсер', () => {
  const html = page('group_7798_v2.html');
  assert.doesNotThrow(() => parser.parseSchedule(html.slice(0, html.length / 2)));
  assert.deepEqual(parser.parseSchedule('<html><body>502 Bad Gateway</body></html>'), []);
  assert.equal(parser.parseWeekAnchor('<p>ничего</p>'), null);
});
