/*
 * Проверка сторожа, который решает, публиковать ли собранный срез.
 *
 * Запуск: node --test tools/*.test.mjs
 *
 * Почему эти тесты важнее, чем кажется: тесты парсера гоняются на сохранённых
 * страницах и по своей природе не могут заметить, что сайт переделали, — копия
 * не меняется вместе с оригиналом. Смену вёрстки видит только этот сторож,
 * потому что он смотрит на результат обхода живого сайта.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { verdict } from './snapshot-check.mjs';

const HEALTHY = {
  groups: 412, lessons: 9888, empty: 2, failed: 0, anchor: { day: 20717, upper: false },
  withTime: 9888, withRoom: 9883, withTeacher: 9805
};

test('здоровый срез публикуется', () => {
  assert.equal(verdict(HEALTHY), null);
});

test('ноль занятий — это поломка вёрстки, а не пустой семестр', () => {
  const stop = verdict({ ...HEALTHY, lessons: 0, empty: 412 });
  assert.match(stop, /ни одного занятия/);
});

test('так выглядел обход 15.09.2026, когда сайт переделали', () => {
  // страницы отвечали 200, ошибок не было, занятий не нашлось ни у кого
  const stop = verdict({ groups: 412, lessons: 0, empty: 412, failed: 0, anchor: null });
  assert.ok(stop, 'такой срез публиковать нельзя');
});

test('половина групп без расписания — тоже не публикуем', () => {
  assert.match(verdict({ ...HEALTHY, lessons: 120, empty: 300 }), /расписание пустое/);
  assert.equal(verdict({ ...HEALTHY, empty: 200 }), null, 'а меньше половины — бывает');
});

test('массовые сетевые ошибки останавливают публикацию', () => {
  assert.match(verdict({ ...HEALTHY, failed: 100 }), /не ответили/);
  assert.equal(verdict({ ...HEALTHY, failed: 40 }), null, 'единичные — не беда');
});

test('без чётности со страницы срез бесполезен', () => {
  assert.match(verdict({ ...HEALTHY, anchor: null }), /Сегодня/);
});

test('пустой список групп — первый признак, что всё сломалось', () => {
  assert.match(verdict({ groups: 0, lessons: 0, empty: 0, failed: 0, anchor: null }), /список групп/);
  assert.match(verdict(), /список групп/);
});

test('занятия есть, а колонка пропала — тоже поломка вёрстки', () => {
  assert.match(verdict({ ...HEALTHY, withTeacher: 0 }), /преподаватель/);
  assert.match(verdict({ ...HEALTHY, withRoom: 300 }), /аудитория/);
  assert.match(verdict({ ...HEALTHY, withTime: 4000 }), /время/);
  assert.equal(verdict({ ...HEALTHY, withTeacher: 8000 }), null, 'часть без преподавателя — бывает');
});

test('старые вызовы без счётчиков полей не ломаются', () => {
  const { withTime, withRoom, withTeacher, ...bare } = HEALTHY;
  assert.equal(verdict(bare), null);
});
