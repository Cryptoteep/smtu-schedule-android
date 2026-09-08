<h1 align="center">Расписание СПбГМТУ</h1>

<p align="center">
  Неофициальное приложение расписания <a href="https://www.smtu.ru/">Санкт-Петербургского
  государственного морского технического университета</a>.<br>
  Виджеты, планы корпусов, работа без интернета — 89 КБ и ничего о вас не собирает.
</p>

<p align="center">
  <a href="https://cryptoteep.github.io/smtu-schedule-android/">
    <img alt="Скачать приложение" src="https://img.shields.io/github/v/release/Cryptoteep/smtu-schedule-android?label=%D1%81%D0%BA%D0%B0%D1%87%D0%B0%D1%82%D1%8C%20%D0%BF%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5&style=for-the-badge&color=1A3E8C"></a>
</p>

<p align="center">
  <a href="https://cryptoteep.github.io/smtu-schedule-android/">Страница установки</a> ·
  <a href="https://cryptoteep.github.io/smtu-schedule-android/app/">веб-версия</a> (iPhone и без установки)
</p>

<p align="center">
  <a href="https://github.com/Cryptoteep/smtu-schedule-android/actions/workflows/android.yml">
    <img alt="Сборка" src="https://github.com/Cryptoteep/smtu-schedule-android/actions/workflows/android.yml/badge.svg"></a>
  <img alt="Android 5.0+" src="https://img.shields.io/badge/Android-5.0%2B-3DDC84">
  <img alt="Размер APK" src="https://img.shields.io/badge/APK-89%20%D0%9A%D0%91-1A3E8C">
  <img alt="Зависимостей нет" src="https://img.shields.io/badge/%D0%B7%D0%B0%D0%B2%D0%B8%D1%81%D0%B8%D0%BC%D0%BE%D1%81%D1%82%D0%B5%D0%B9-0-brightgreen">
  <img alt="Тестов 68" src="https://img.shields.io/badge/%D1%82%D0%B5%D1%81%D1%82%D0%BE%D0%B2-68-brightgreen">
  <a href="LICENSE"><img alt="MIT" src="https://img.shields.io/badge/license-MIT-blue"></a>
</p>

<p align="center">
  <img src="docs/demo.gif" width="300" alt="Неделя, день, свайпы, детали занятия, расписание преподавателя">
</p>

## Скачать и поставить

1. Откройте [страницу установки](https://cryptoteep.github.io/smtu-schedule-android/)
   и скачайте APK прямо на телефон (там же — почему Android показывает предупреждения
   и чем проверить файл).
2. Откройте скачанный файл. Android спросит разрешение ставить приложения из
   этого источника — разрешите (обычный вопрос для APK не из Play Store).
3. Запустите, выберите свою группу из списка — дальше она запомнится.

Нужен Android 5.0 или новее. Приложение просит одно разрешение — интернет:
без него не с чего загружать расписание.

## Что умеет

<p align="center">
  <img src="docs/screenshot-week.png" width="30%" alt="Неделя">
  <img src="docs/screenshot-day.png" width="30%" alt="День">
  <img src="docs/screenshot-lesson.png" width="30%" alt="Занятие">
</p>

- **Группа выбирается один раз** — поиск по всем 449 группам, дальше запоминается.
- **Неделя и день.** Свайп влево-вправо листает день или неделю, кнопка
  «Сегодня» возвращает к текущему дню. Весь семестр скачивается одним запросом,
  поэтому листание мгновенное и работает офлайн.
- **Счётчик до пары** в шапке: сколько осталось до конца текущей или сколько
  ждать ближайшую. Текущая пара подсвечена, прошедшие сегодня — приглушены.
- **Расписание преподавателя** открывается прямо в приложении — со всеми его
  группами; оттуда можно перейти к расписанию любой из них.
- **Поиск** по предмету, преподавателю, аудитории, типу занятия и группе.
- **Пара добавляется в календарь** телефона, день или неделя отправляется
  текстом в мессенджер.
- **Тёмная тема** включается вместе с системной.
- **Офлайн.** Каждая загрузка сливается с кэшем: приложение стартует мгновенно
  и показывает расписание без сети, а пары, которые университет позже убрал со
  страницы, остаются в истории.
- **Три виджета на домашний экран:** кольцо обратного отсчёта 1×1, текущая пара
  2×1 с аудиторией и расписание на день 3×2. Обновляются сами и открывают
  приложение по тапу; ставятся из меню «⋮ → Виджет на домашний экран».
- **Планы корпусов.** У занятия в корпусе А, Б или У — официальный поэтажный
  план: скачивается один раз, дальше открывается офлайн, этажи листаются
  стрелками, масштаб щипком.
- **Работает под VPN.** Сайт вуза не пускает часть зарубежных адресов — тогда
  приложение берёт расписание из резервного среза на GitHub и говорит об этом.
  Оба источника запрашиваются одновременно, так что расписание появляется за
  секунды, а не после минуты ожидания.
- **Говорит о новой версии** — раз в сутки и не больше трёх раз; ставить или нет,
  решает человек.

<p align="center">
  <img src="docs/screenshot-dark-week.png" width="30%" alt="Тёмная тема, неделя">
  <img src="docs/screenshot-dark-day.png" width="30%" alt="Тёмная тема, день">
</p>

<p align="center">
  <img src="docs/screenshot-widgets.png" width="62%" alt="Три виджета на домашнем экране">
</p>

## Приватность

Приложение ходит на `www.smtu.ru` — за расписанием, при запуске и по кнопке ⟳ —
и на страницы этого же проекта на GitHub: за резервной копией расписания, когда
сайт вуза недоступен, и за номером последней версии. Планы корпусов скачиваются
с `isu.smtu.ru`, когда их открывают. Ни аналитики, ни рекламы, ни своих серверов, ни аккаунтов. Выбранная
группа и кэш лежат в приватной папке приложения на телефоне. Код открыт
целиком, APK собирается из него же в GitHub Actions.

## Как это устроено

У сайта СПбГМТУ нет API, поэтому приложение разбирает его обычные страницы —
тот самый источник, который правит сам университет:

```
GET /ru/listschedule/                   -> 449 групп: id и названия
GET /ru/viewschedule_new/<gid>/         -> расписание группы на весь семестр
GET /ru/viewschedule_new/teacher/<pid>/ -> расписание преподавателя на семестр
```

Дальше — два решения, из-за которых приложение показывает больше, чем сайт на
телефоне.

### Читаем таблицу, а не карточки

Каждая страница расписания рендерит одни и те же данные дважды: карточками
(`#card-container`) и таблицей (`#table-container`). Карточки выглядят
дружелюбнее, но **в них не указана группа** — а без неё расписание
преподавателя превращается в список пар неизвестно для кого. Парсер читает
таблицу:

```html
<tr class="js-week-container js-week-1">          <!-- 1 — верхняя, 2 — нижняя -->
  <th>08:30 - 10:00</th>
  <td>верхняя</td>
  <td title="14.09.2026, 28.09.2026, …">14 сентября — 21 декабря 2026</td>
  <td>167 Корпус У</td>
  <td>12826-11</td>
  <td><span>Предмет</span><br><small class="text-muted">Лекция</small></td>
  <td><a href='/ru/viewperson/105760/'>Фамилия Имя Отчество</a></td>
```

Колонки ищутся по заголовкам таблицы, а не по номерам, — добавленный или
переставленный столбец не сдвинет данные молча. Карточный разбор остался
запасным путём на случай, если таблица со страницы исчезнет.

Атрибут `title` со **всеми точными датами** каждой пары — то, что делает
просмотр по дням честным: приложение не гадает, «выпадает ли пара на эту
неделю», а знает это от университета.

### Чётность недели считается из данных, а не от даты в коде

Сайт нигде не пишет машиночитаемо, какая неделя сейчас. Обычное решение —
зашить в код опорную дату («неделя такого-то числа — верхняя») и считать от
неё; оно ломается после первого же переноса занятий и устаревает к следующему
семестру.

Здесь цикл восстанавливается из самого расписания: каждая строка помечена
`js-week-1`/`js-week-2` и несёт список своих дат, то есть данные сами говорят,
какие календарные недели верхние. Опорной берётся неделя с самым уверенным
большинством, затем по ней проверяется весь семестр; если цикл где-то рвётся,
приложение честно пишет «чётность недель неточная» в строке состояния, а не
делает вид, что всё в порядке.

## Структура кода

```
app/src/main/java/com/korabel/schedule/
  Dates.java            даты в epoch-днях + русские названия (без сюрпризов Locale и TimeZone)
  Html.java             снятие тегов и декодирование HTML-сущностей
  Lesson.java           занятие: время, чётность, точные даты, преподаватель, группа
  Group.java            группа с натуральной сортировкой (9 раньше 12)
  ScheduleParser.java   парсер страниц smtu.ru (таблица + карточки как запасной путь)
  WeekParity.java       восстановление цикла верхняя/нижняя по данным
  Schedule.java         запросы: день, неделя, «что сейчас», поиск, слияние с кэшем
  Mirror.java           разбор резервного среза на GitHub Pages
  Smtu.java             сеть и кэш: гонка «сайт против копии», единый вход в Android
  Now.java              «что сейчас»: идёт пара, перемена, следующий учебный день
  Version.java          сравнение номеров версий
  Updates.java          проверка обновлений по latest.json
  Maps.java             поэтажные планы корпусов (PdfRenderer, зум щипком)
  Widgets.java          перерисовка виджетов и самозаводящийся будильник
  TimerWidget/NowWidget/AgendaWidget.java   три виджета
  WidgetTick/SystemEvents.java              будильник и BOOT/TIME_SET
  Ui.java               палитра (светлая/тёмная) и построители вью
  MainActivity.java     весь экран и диалоги
app/src/test/java/…                68 JVM-тестов
app/src/test/resources/fixtures/   реальные страницы smtu.ru, на которых они гоняются

docs/                 страница установки и политика конфиденциальности (GitHub Pages)
docs/latest.json      номер последней версии — по нему приложение узнаёт об обновлении
docs/app/             веб-версия: parser.js — общий парсер, app.js — приложение
docs/data/            расписания в JSON, их обновляет workflow `data`
tools/build-data.mjs  сборщик этих данных (запускается в GitHub Actions)
```

Ядро не зависит от Android — поэтому парсер, даты и вся логика запросов покрыты
обычными JUnit-тестами, которые проходят за секунду.

## Тесты

```bash
./gradlew test
```

68 тестов гоняются **на настоящих страницах** сайта (осенний семестр
2026/2027), сохранённых в `app/src/test/resources/fixtures/`. Проверяется, среди
прочего:

- все 449 групп разбираются и сортируются натурально;
- у группы 12826-11 читаются все 47 строк со всеми полями (предмет, тип,
  аудитория, преподаватель и его id, группа, диапазон и 8 точных дат);
- преподаватель, напечатанный без ссылки на карточку персоны, всё равно
  распознаётся, а примечание «С 26.10 по 14.12» не принимается за ФИО;
- на странице преподавателя у каждой пары есть группа;
- карточный запасной разбор даёт тот же результат, что и таблица;
- переставленные колонки не ломают разбор, обрезанная страница не роняет парсер;
- цикл чётности восстанавливается из данных и переживает одну ошибочную строку;
- арифметика дат совпадает с `GregorianCalendar` на 11 лет вперёд;
- состояние «что сейчас» отвечает правильно на идущей паре, на перемене, до
  первой пары и после последней — на этом же расчёте живут все три виджета;
- «2.10» считается новее «2.9», а строка вроде «2.5-beta» не принимается за версию.

Если университет поменяет вёрстку, тесты упадут раньше, чем это заметит
пользователь.

## Сборка

Нужны JDK 17 и Android SDK (`platforms;android-36`, `build-tools;36.1.0`);
Gradle-обёртка лежит в репозитории.

```bash
./gradlew test              # юнит-тесты
./gradlew lintDebug         # статический анализ
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Release проходит R8 и сжатие ресурсов. Для подписи своим ключом положите в
корень `keystore.properties`:

```properties
storeFile=.keys/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Он подхватится автоматически (схемы подписи v1+v2+v3). Без него release
подписывается отладочным ключом — годится для себя, но не для раздачи.

Каждый push собирается в GitHub Actions: тесты, lint и APK в артефактах.

## Вопросы

**А для iPhone?** Есть [веб-версия](https://cryptoteep.github.io/smtu-schedule-android/app/):
то же расписание в браузере, добавляется на экран «Домой» и работает офлайн. Она
читает данные, которые четыре раза в сутки собирает со smtu.ru сборка на GitHub
(`tools/build-data.mjs` + workflow `data`), — у сайта университета нет CORS-заголовков,
и так браузеру не нужен никакой посредник. Нативное
приложение под iOS выпустить бесплатно нельзя — Apple берёт 99 $ в год за возможность
раздавать сборку, а бесплатная подпись живёт 7 дней и требует компьютера.

**Почему не в Google Play?** Приложение неофициальное и заточено под один
университет; проще раздавать APK. Установка из файла — единственный шаг, где
Android спросит подтверждение.

**Расписание не загружается под VPN.** Сервер университета отклоняет часть
зарубежных адресов. Приложение это переживает: если сайт не ответил, оно берёт
тот же семестр из резервного среза на GitHub Pages (его собирает workflow
`data`) и помечает в строке состояния, что данные из резервной копии. Когда
сайт снова доступен, обновление опять идёт напрямую с smtu.ru.

**Расписание изменилось, а в приложении старое.** Кнопка ⟳ в шапке. Если
университет переписал страницу целиком — меню ⋮ → «Очистить кэш и загрузить
заново».

**Это безопасно?** Код открыт целиком, разрешение одно (интернет), сборка
воспроизводится в CI. APK подписан ключом автора — Android проверит подпись
при обновлении.

## Лицензия

MIT — см. [LICENSE](LICENSE). Приложение не связано с СПбГМТУ; все данные
расписания принадлежат университету.

Основано на [первой версии](https://gitlab.com/trigger337/smtu-schedule-android-app)
и продолжает переглядываться с ней: виджеты, поэтажные планы корпусов и проверка
обновлений — идеи оттуда, переписанные под здешнюю модель данных. Спасибо
[trigger337](https://gitlab.com/trigger337).

История изменений — в [CHANGELOG.md](CHANGELOG.md).

---

## English

Unofficial schedule viewer for [SPbSMTU](https://www.smtu.ru/) (Saint Petersburg
State Marine Technical University). Pure Java, zero third-party libraries (no
AndroidX), all views built in code; the signed release APK is 89 KB.

**Features.** Pick your group once from all 449; week and day views with swipe
navigation; the lesson happening now is highlighted; a teacher's full schedule
opens in place, across all their groups; search over subject, teacher, room,
type and group; add a lesson to the phone's calendar or share a day as text;
three home-screen widgets (a 1x1 countdown ring, a 2x1 current-lesson card, a
3x2 day agenda); official floor plans for buildings A, B and U; dark theme;
fully offline after the first fetch. When the university's server refuses the
phone's exit IP (common on a VPN), the app falls back to a snapshot published on
this project's own GitHub Pages and says so.

**How it works.** The site has no API, so the app parses its server-rendered
pages. It reads the **table** view rather than the cards: the table is the only
one that names the group of each lesson, and its `title` attribute lists every
exact date a lesson occurs on. Columns are located by their header text, so a
reordered column cannot shift data silently.

**Week parity is derived from the data**, not hard-coded: every row is tagged
`js-week-1`/`js-week-2` and carries its dates, so the upper/lower cycle is
reconstructed from the schedule itself — and reported as uncertain when the
cycle does not add up.

**Tests.** `./gradlew test` runs 50 JVM tests against real pages saved from
smtu.ru. The core (`Dates`, `Html`, `Lesson`, `Group`, `ScheduleParser`,
`WeekParity`, `Schedule`) has no Android dependencies; `Smtu` is the only class
that knows about `Context`.

**Build.** JDK 17 + Android SDK 34, wrapper included: `./gradlew assembleRelease`.
Drop a `keystore.properties` in the project root to sign with your own key.

Min SDK 21, target SDK 36, internet permission only. MIT licensed; not
affiliated with SPbSMTU.
