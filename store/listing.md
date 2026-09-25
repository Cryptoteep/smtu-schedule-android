# Тексты карточки приложения

Копировать как есть. Длины подогнаны под ограничения RuStore и Galaxy Store.

## Название (до 50 символов)

```
Расписание СПбГМТУ
```

## Краткое описание (до 80 символов)

```
Расписание занятий СПбГМТУ: неделя и день, преподаватели, работает офлайн
```

## Полное описание (до 4000 символов)

```
Неофициальное приложение расписания Санкт-Петербургского государственного морского технического университета. Показывает то же, что опубликовано на сайте университета, но удобно и без интернета.

ЧТО УМЕЕТ

• Расписание своей группы — выбираете один раз из списка всех групп университета (их больше 400), дальше приложение открывается сразу на нужном.
• Просмотр по неделям и по дням. Свайп листает дни и недели, кнопка «Сегодня» возвращает к текущему дню.
• Счётчик до пары в шапке: сколько осталось до конца текущей или сколько ждать следующую. Текущая пара подсвечена, прошедшие сегодня — приглушены.
• Три виджета на домашний экран: кольцо обратного отсчёта, текущая пара с аудиторией и расписание на день.
• Планы корпусов А, Б и У: официальный поэтажный план открывается на нужном этаже прямо из карточки занятия и дальше работает без интернета.
• Расписание любого преподавателя — со всеми его группами. Открывается из карточки занятия, оттуда же можно перейти к расписанию другой группы.
• Верхняя и нижняя неделя — по данным самого сайта университета.
• Поиск по предмету, преподавателю, аудитории, типу занятия и группе: найденная пара открывается в тот день, когда она действительно идёт.
• Пара добавляется в календарь телефона одним касанием, день или неделя отправляется текстом в мессенджер.
• Тёмная тема включается вместе с системной.

РАБОТАЕТ БЕЗ ИНТЕРНЕТА И ПОД VPN

Расписание на весь семестр скачивается одним запросом и остаётся на телефоне: в корпусе без связи, в метро, за городом приложение открывается мгновенно. Если сайт университета недоступен (так бывает под VPN), расписание приходит из резервной копии за пару секунд — и приложение прямо пишет, что данные из копии и когда их собрали.

ЧЕСТНО О ДАННЫХ

Приложение не собирает о вас ничего: ни аналитики, ни рекламы, ни регистрации, ни своих серверов. Расписание оно берёт с официального сайта www.smtu.ru, планы корпусов — с сервера университета, резервную копию и номер новой версии — со страницы проекта на GitHub. Единственное разрешение, которое оно просит, — доступ в интернет.

Исходный код открыт целиком, а устанавливаемый файл собирается из него автоматически, и лог каждой сборки виден всем: github.com/Cryptoteep/smtu-schedule-android

РАЗМЕР

Около 90 КБ — приложение написано на чистой Java без единой сторонней библиотеки. Без виджетов оно не работает в фоне; виджеты просыпаются только тогда, когда картинка на них должна измениться.

Приложение неофициальное и не связано с СПбГМТУ. Все данные расписания принадлежат университету; если ошибка есть и на сайте — её исправляет деканат, а приложение покажет исправление после обновления.
```

## Что нового (для обновления после 2.7)

```
• Найденная поиском пара открывается в тот день, когда она действительно идёт.
• Виджеты ведут обратный отсчёт последние полтора часа перед парой.
• Если расписание не обновилось или взято из резервной копии, это видно на экране.
• Исправлены занятия, у которых вместо названия показывалось «Лекция».
```

## Прочие поля

| Поле | Значение |
|---|---|
| Категория | Образование |
| Возрастной рейтинг | 0+ |
| Цена | бесплатно |
| Реклама | нет |
| Покупки в приложении | нет |
| Сбор персональных данных | нет |
| Сайт | https://cryptoteep.github.io/smtu-schedule-android/ |
| Политика конфиденциальности | https://cryptoteep.github.io/smtu-schedule-android/privacy.html |
| Обратная связь | https://github.com/Cryptoteep/smtu-schedule-android/issues |
| Ключевые слова | расписание, СПбГМТУ, Корабелка, университет, студентам, пары, занятия |

## English (Galaxy Store)

**Title:** SPbSMTU Schedule

**Short description (80):**
```
Class schedule for SPbSMTU students: week and day views, teachers, works offline
```

**Full description:**
```
Unofficial class schedule viewer for Saint Petersburg State Marine Technical University (SPbSMTU). It shows exactly what the university publishes on its website — but conveniently, and without an internet connection.

• Your group's schedule: pick it once from all of the university's 400+ groups.
• Week and day views, swipe to move between them, "today" button.
• A countdown to the end of the current lesson or the start of the next; the lesson happening now is highlighted.
• Three home-screen widgets: a countdown ring, the current lesson with its room, and the day's agenda.
• Official floor plans for buildings A, B and U, opened on the right floor and available offline.
• Any teacher's full schedule across all their groups, one tap from a lesson.
• Upper/lower week comes from the university site itself.
• Search by subject, teacher, room, lesson type and group; a result opens on the day the lesson actually takes place.
• Add a lesson to your calendar or share a day as text.
• Dark theme follows the system.

Works offline: the whole semester is downloaded at once and stays on the phone. When the university site is unreachable (common on a VPN), the schedule comes from a backup copy within seconds, and the app says so.

No analytics, no ads, no accounts, no servers of our own. The app talks to the university site (schedules and floor plans) and to this project's GitHub page (backup copy and the latest version number). The only permission it asks for is internet access. The source code is open and every build is public: github.com/Cryptoteep/smtu-schedule-android

About 90 KB, pure Java, zero third-party libraries. Not affiliated with SPbSMTU.
```
