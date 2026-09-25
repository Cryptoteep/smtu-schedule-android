/*
 * Общий парсер docs/app/parser.js для Node.
 *
 * Файл написан для браузера (подключается тегом script и берёт DOMParser из
 * окружения), поэтому в Node его приходится собирать вручную, подсунув
 * DOMParser от linkedom. Отдельным модулем — чтобы сборщик данных и тесты
 * гоняли ровно один и тот же код, а не две копии загрузчика.
 */
import { readFile } from 'node:fs/promises';
import { parseHTML } from 'linkedom';

export async function loadParser() {
  const source = await readFile(new URL('../docs/app/parser.js', import.meta.url), 'utf8');
  return new Function('DOMParser', source +
    '\nreturn { parseSchedule, parseGroups, parseTitle, parseWeekAnchor, typeSecond };')(
    class {
      parseFromString(html) { return parseHTML(html).document; }
    }
  );
}
