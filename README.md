# Расписание СПбГМТУ — minimal native Android app

## Русский

Неофициальное приложение для просмотра расписания
[СПбГМТУ](https://www.smtu.ru/). Чистая Java, ноль зависимостей (без AndroidX
и внешних библиотек, без ресурсов), один Activity с интерфейсом, построенным
кодом. Размер APK — около 25 КБ.

### Источник данных: только www.smtu.ru

У сайта нет API, поэтому приложение разбирает его серверные страницы —
единственный авторитетный источник, который университет правит сам:

    GET /ru/listschedule/                   -> все 449 id и названия групп
    GET /ru/viewschedule_new/<gid>/         -> расписание группы на весь семестр
    GET /ru/viewschedule_new/teacher/<pid>/ -> расписание преподавателя на семестр
                                               (pid = id из ссылки /ru/viewperson/<pid>/,
                                                которая стоит у каждого преподавателя)

Каждая страница расписания содержит весь семестр одним ответом. Парсер
ориентирован на карточное представление, общее для всех этих страниц:
дневные блоки → карточки по времени → контейнеры верхней/нижней недели.
Каждое занятие несёт:

- предмет, вид (Лекция/Практическое занятие/…), аудиторию
- класс `js-week-1`/`js-week-2` = верхняя/нижняя неделя
- атрибут `title` со **всеми точными датами проведения** — именно это делает
  просмотр по дням и неделям точным
- имя преподавателя и его viewperson-id (открывает его собственное расписание)

Чётность недели для плашки в шапке считается от якоря, который пересчитывается
при каждой загрузке по строке сайта «Сегодня: … верхняя/нижняя неделя», поэтому
остаётся верным и в следующие семестры. Запасной вариант: неделя 2026-08-31 —
верхняя.

Каждая загрузка сливается в кэш-файл конкретного расписания в приватном хранилище
приложения: офлайн-работа, мгновенный запуск, история переживает правки на сайте.

### Возможности

- Группа выбирается один раз (поиск по всем 449 группам) и сохраняется;
  смена — тапом по названию группы в шапке
- Просмотр по неделе (Пн–Вс) и по дню с быстрым переключением дней, навигация
  ‹ › на весь семестр (одна загрузка — и всё расписание уже на устройстве)
- Кнопка ⟳ в шапке обновляет расписание с smtu.ru
- Кнопка ⓘ (справа вверху) открывает диалог «О приложении»
- Цветная плашка верхней/нижней недели
- Тап по занятию: день недели, время, вид, аудитория, чётность, диапазон дат
  и все точные даты
- «О предмете»: все вхождения предмета
- «Преподаватель»: полное расписание преподавателя по всем группам,
  загружается с smtu.ru по его viewperson-id
- Для занятий без списка дат — запасное сопоставление по чётности и дню недели

### Структура

    app/src/main/
      AndroidManifest.xml
      java/com/korabel/schedule/
        Smtu.java          клиент smtu.ru: загрузка, парсер карточек, кэш, чётность
        MainActivity.java  весь интерфейс (кнопки/списки кодом, без XML)

### Сборка

Требуются JDK 17, Android SDK (cmdline-tools + `platforms;android-34` +
`build-tools;34.0.0`), Gradle 8.7+. Версия AGP: 8.5.2.

С переменной `ANDROID_HOME`, указывающей на SDK:

    gradle assembleDebug
    # -> app/build/outputs/apk/debug/app-debug.apk
    adb install app/build/outputs/apk/debug/app-debug.apk

Продакшен-сборка:

    gradle assembleRelease
    # -> app/build/outputs/apk/release/app-release.apk

Release собирается с R8-минификацией и сжатием ресурсов. Для подписи: создайте
keystore и файл `keystore.properties` в корне проекта (ожидаемые ключи — в
`app/build.gradle`) — он подхватится автоматически. Без него release-APK
подписывается отладочным ключом: годится для личной установки, но не для
публикации.

Gradle wrapper в репозиторий не вложен; сгенерируйте его `gradle wrapper` или
запускайте Gradle напрямую.

### Замечания

- Приложение обращается к `www.smtu.ru` напрямую с телефона. Сервер
  университета, похоже, отклоняет часть не-российских IP, поэтому при VPN с
  зарубежным выходом первая загрузка может не пройти — приложение продолжит
  работать из кэша. Сеть используется только при запуске и по кнопке ⟳.
- Min SDK 21 (Android 5.0), target SDK 34.

### Лицензия

MIT — см. [LICENSE](LICENSE). Приложение не связано с СПбГМТУ; все данные
расписания принадлежат университету.

---

## English

Unofficial schedule viewer for [SPbSMTU](https://www.smtu.ru/) (Saint Petersburg
State Marine Technical University). Pure Java, zero dependencies (no AndroidX,
no external libraries, no resources), one Activity with programmatic views.
The APK is tiny (~25 KB release).

### Data source: www.smtu.ru only

The site has no API, so the app parses its server-rendered pages — the
authoritative source, corrected and updated by the university directly:

    GET /ru/listschedule/                   -> all 449 group ids and names
    GET /ru/viewschedule_new/<gid>/         -> full-semester group schedule
    GET /ru/viewschedule_new/teacher/<pid>/ -> full-semester teacher schedule
                                               (pid = the /ru/viewperson/<pid>/ id
                                                linked from every lesson's teacher)

Each schedule page contains the whole semester in one response. The parser
targets the card view all these pages share: day blocks → time cards →
upper/lower week containers, where each lesson carries:

- subject, type (Лекция/Практическое занятие/…), room
- `js-week-1`/`js-week-2` class = верхняя/нижняя неделя
- a `title` attribute listing **every exact occurrence date** — this is what
  makes day/week views exact
- teacher name + viewperson id (enables the teacher's own full schedule)

Week parity for the navigation badge is computed from an anchor that is
recalibrated on every fetch from the site's own "Сегодня: … верхняя/нижняя
неделя" header, so it stays correct across semesters. Fallback constant: the
week of 2026-08-31 is upper.

Every fetch merges into a per-schedule cache file in the app's private storage:
offline support, instant startup, and past lessons survive page edits.

### Features

- Pick your group once (searchable list of all 449 groups) — saved on device;
  tap the group name in the header to change it later
- Week view (Mon–Sun) and day view with weekday quick-switch, ‹ › navigation
  across the whole semester (one fetch has it all)
- ⟳ button in the header re-fetches the schedule from smtu.ru
- ⓘ button (top-right) opens the about/info dialog
- Upper/lower week badge, color-coded
- Tap a lesson: weekday, time, type, room, parity, date range + every exact date
- "О предмете": all occurrences of the subject
- "Преподаватель": the teacher's full schedule across all groups, fetched
  live from smtu.ru via their viewperson id
- Lessons whose rows carry no exact dates fall back to parity + weekday matching

### Layout

    app/src/main/
      AndroidManifest.xml
      java/com/korabel/schedule/
        Smtu.java          smtu.ru client: fetch, card-view parser, cache, parity
        MainActivity.java  all UI (programmatic views, no XML resources)

### Building

Requirements: JDK 17, Android SDK (cmdline-tools + `platforms;android-34` +
`build-tools;34.0.0`), Gradle 8.7+. AGP version: 8.5.2.

With `ANDROID_HOME` set to the SDK root:

    gradle assembleDebug
    # -> app/build/outputs/apk/debug/app-debug.apk
    adb install app/build/outputs/apk/debug/app-debug.apk

Production build:

    gradle assembleRelease
    # -> app/build/outputs/apk/release/app-release.apk

The release build is minified and resource-shrunk. For signing: create a
keystore and a `keystore.properties` in the project root (see
`app/build.gradle` for the expected keys) — it is picked up automatically.
Without it the release APK is signed with the debug key, which is fine for
personal sideloading but not for publishing.

No Gradle wrapper jar is committed; generate one with `gradle wrapper` or run
Gradle directly.

### Notes

- The app talks to `www.smtu.ru` directly from the phone. The university's
  server appears to refuse some non-Russian exit IPs, so on a VPN with a
  foreign endpoint the initial fetch may time out — the app then keeps working
  from cache. Fetches happen only at startup and on ⟳.
- Min SDK 21 (Android 5.0), target SDK 34.

### License

MIT — see [LICENSE](LICENSE). Not affiliated with SPbSMTU; all schedule data
belongs to the university.
