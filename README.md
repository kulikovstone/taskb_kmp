Проект Kotlin Multiplatform для Android, iOS, Web, настольных приложений (JVM) и сервера.

Основная разработка сейчас ведётся для Desktop; следующая платформа — Android.
Модули и целевые платформы iOS и Web сохранены как каркас на будущее.

* [/app/iosApp](./app/iosApp/iosApp) содержит приложение для iOS. Даже при использовании общего
  интерфейса на Compose Multiplatform для iOS нужна отдельная точка входа.
  Здесь также следует размещать код SwiftUI.

* [/app/shared](./app/shared/src) содержит общий код приложений на Compose Multiplatform.
  Внутри расположены следующие каталоги:
    - [commonMain](./app/shared/src/commonMain/kotlin) — код, общий для всех платформ.
    - Остальные каталоги содержат точки входа для конкретных платформ: Android, Desktop,
      iOS и Web.

* [/core](./core) содержит модули общей инфраструктуры: MVI, навигацию и базу данных.

* [/server](./server/src/main/kotlin) содержит каркас серверного приложения на Ktor.
  Прикладные маршруты пока не реализованы.

### Модули приложения

Код из `taskb_desktop` перенесён в следующую структуру модулей:

| Модуль | Назначение |
| --- | --- |
| `:core:mvi` | Общие контракты Store и инфраструктура MVI. |
| `:core:navigation` | Инфраструктура навигации и навигационный контейнер Compose. |
| `:core:database` | Настройка локальной базы данных и доступ к ней. |
| `:ui` | Тема TaskB, палитра, иконки и переиспользуемые компоненты интерфейса. |
| `:feature:hello` / `:feature:hello:api` | Реализация экрана приветствия и публичные контракты. |
| `:feature:home` / `:feature:home:api` | Реализация главного экрана и публичные контракты. |
| `:feature:settings` / `:feature:settings:api` | Реализация настроек приложения и публичные контракты. |
| `:feature:group` / `:feature:group:api` | Группы и их редактор, модели, DAO и публичный контракт репозитория групп. |
| `:feature:task` / `:feature:task:api` | Задачи и их редактор с отдельными публичными контрактами. |

`:app:shared` объединяет модули приложения для платформенных точек входа.
Каталог `core` объединяет самостоятельные модули `:core:mvi`, `:core:navigation`
и `:core:database`; отдельной библиотеки `:core` нет.
Зависимости между модулями повторяют структуру настольного проекта: фичи обращаются к другим
фичам через их модули `api`, а `:core:database` зависит от API-модулей групп, задач и настроек.
Списки и редактор задач получают группы через `IGroupRepository` из `:feature:group:api`;
реализацию репозитория и навигацию групп регистрирует `groupModule` в `:app:shared`.

В общие модули перенесены MVI, навигация на Navigation 3, база данных на Room 3 и настройки
приложения. Подключены Coroutines, Koin, Compose Material и Material Symbols.
Экраны приветствия, главного меню, задач, групп и их редакторов находятся в `commonMain`
вместе со Store, состояниями, Intent, reducer и репозиториями. Desktop и Android используют
эти реализации через `:app:shared`. Интерфейс TaskB поддерживает светлую и тёмную темы,
компактную и широкую компоновки с отдельными экранами создания и редактирования.
Черновики и открытые селекторы сохраняются при изменении ширины окна и переходах
к другим экранам поверх редактора. Публичные `TaskEditorRoute.Create(initialDate, initialGroupId)`,
`TaskEditorRoute.Edit(taskId)` из `:feature:task:api`, а также `GroupEditorRoute.Create`
и `GroupEditorRoute.Edit(groupId)` из `:feature:group:api` можно открывать через
`IAppNavigator.navigate` из любой фичи. Маршрут списка `GroupsRoute` также находится
в `:feature:group:api`.
После сохранения, удаления или возврата редактор закрывается к вызывающему экрану;
фильтры списков сохраняются. На широких экранах формы выровнены по центру,
действия находятся в экшенбаре, статистика группы — над полями формы.

Перенесены создание, редактирование и удаление задач и групп, поиск, сортировка,
фильтрация, отметка выполнения, выбор группы и даты. Работа с датами использует
`kotlinx-datetime`; в базе по-прежнему хранится число дней от 1970-01-01, поэтому
существующие сроки задач остаются совместимыми. Новые тексты интерфейса — на английском.
На Desktop сохранён системный шрифт Noto Sans, на остальных платформах используется
системный sans-serif. Цвета и иконки задаются через `Palette`, `TaskBTheme` и `TaskBIcons`.

Исходники используют единый базовый пакет `com.mkulikov.taskb`. Имя класса базы данных
сохранено, чтобы использовать прежнюю историю схем Room.

Общие настройки сборки вынесены в Gradle convention plugins в [/build-logic](./build-logic):

* `taskb.multiplatform.library` настраивает целевые платформы Android, JVM, iOS ARM64,
  iOS Simulator ARM64, браузерные JS и Wasm, а также `kotlin-test` в `commonTest` и тесты
  Android, выполняемые на JVM. Байт-код библиотек для Android и JVM рассчитан на Java 11,
  как и в Android-приложении.
* `taskb.compose.library` дополнительно включает Compose, компилятор Compose, ресурсы Android
  и зависимости для Compose Preview: аннотацию в `commonMain` и tooling в `androidRuntimeClasspath`.

Версии плагинов и Android SDK берутся из `gradle/libs.versions.toml`.
Общий код каждого модуля следует размещать в `src/commonMain/kotlin`, а тесты — в
`src/commonTest/kotlin`. Каталоги с кодом для отдельных платформ добавляются по мере необходимости.
Для компиляции iOS и запуска тестов в симуляторе требуется macOS.

### База данных и внедрение зависимостей

Сущности и DAO находятся в `commonMain` API-модулей групп, задач и настроек. `:core:database`
содержит общую конфигурацию Room и схемы версий 1–3. Версия базы осталась равна 3:
при обновлении с версии 2 данные сохраняются благодаря автоматической миграции,
для версии 1 сохранено прежнее пересоздание базы. Более новые версии схемы не принимаются.
Room KSP генерирует реализацию базы и её конструктор отдельно для каждой целевой платформы.

| Платформа | Хранение | Драйвер |
| --- | --- | --- |
| Android | `taskb.db` в каталоге баз данных приложения | BundledSQLiteDriver |
| Desktop | Прежний путь TaskB с учётом ОС: XDG data, LocalAppData или Application Support | BundledSQLiteDriver |
| iOS | Пока не реализовано | — |
| Web, JS и Wasm | Пока не реализовано | — |

`appModule` объединяет модули базы данных, настроек, навигации и всех перенесённых фич. Реализации `App()` для
Desktop и Android передают фабрику базы общему приложению. Koin создаёт базу при первом обращении
и закрывает её при закрытии контейнера. Жизненным циклом контейнера управляет Compose.

Для iOS и Web оставлен минимальный экран-заглушка TaskB без инициализации базы и DI-контейнера.
Общие исходники и объявления targets сохраняются, а платформенное хранение и запуск
инфраструктуры будут добавлены, когда начнётся разработка этих версий.

### Версия приложения

Версия рассчитывается автоматически по тем же правилам, что в прежнем Desktop-проекте.
Файл [version-major.txt](./version-major.txt) задаёт базовую версию из двух целых чисел,
например `1.38` или `2.0`.

Ко второй части базовой версии прибавляется число коммитов основной линии истории
(`--first-parent`) после коммита, задавшего эту базу. Например, `1.38` становится `1.39`
после следующего коммита. Повторная сборка, незакоммиченные изменения кода и изменения
только пробелов в базовом файле не сбрасывают счётчик. Merge-коммит считается один раз.
Чтобы начать новую серию, измените базу, например на `2.0`, и закоммитьте файл.
До первого коммита новой базы используется её значение без прибавления коммитов.

Посмотреть текущую версию:

```bash
./gradlew -q printVersion
```

Рассчитанная версия задаёт `project.version` всех модулей, отображается на главном экране
и используется как `versionName` Android и версия Desktop-пакетов. Для Windows сохраняется
прежнее преобразование: `1.38` → `1.0.38`, чтобы соответствовать формату MSI.
Общий `AppVersion.kt` генерируется при сборке; редактировать его вручную не нужно.

Android `versionCode` равен общему числу коммитов основной линии истории, начиная с первого.
Он растёт при новых коммитах и не сбрасывается при смене базовой версии.
Для расчёта версии нужен установленный Git и полная история репозитория; в CI используйте
`fetch-depth: 0`, для shallow clone выполните `git fetch --unshallow`.

Реализация находится в convention plugin `taskb.versioning` в `build-logic`.
Перенесённые тесты проверяют смену базы, merges, повторные сборки, detached HEAD и ошибки
неполной истории. Интеграционный тест проверяет обновление версии после коммита с включённым
configuration cache и наследование версии модулями:

```bash
./gradlew :build-logic:test
```

### Запуск приложений

Используйте конфигурации запуска на панели инструментов IDE или следующие команды:

- Android-приложение, сборка отладочного APK: `./gradlew :app:androidApp:assembleDebug`
- Настольное приложение:
    - С горячей перезагрузкой: `./gradlew :app:desktopApp:hotRun --auto`
    - Обычный запуск: `./gradlew :app:desktopApp:run`
- Сервер: `./gradlew :server:run`
- Веб-приложение:
    - Wasm (быстрее, для современных браузеров): `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
    - JS (медленнее, поддерживает старые браузеры): `./gradlew :app:webApp:jsBrowserDevelopmentRun`
- iOS-приложение: откройте каталог [/app/iosApp](./app/iosApp) в Xcode и запустите приложение оттуда.

### Сборка Desktop для распространения

Настройки находятся в [app/desktopApp/build.gradle.kts](./app/desktopApp/build.gradle.kts).
Из прежнего Desktop-проекта перенесены имя пакета `TaskB`, иконки для Linux, Windows и macOS,
настройка JDK 21 и [compose-desktop.pro](./app/desktopApp/compose-desktop.pro) с правилами
для Room и SQLite. Минификация ProGuard в релизной сборке отключена, как в прежнем проекте.
Версия пакетов автоматически рассчитывается из `version-major.txt` и истории Git.

Готовое приложение со встроенной Java, без установщика:

```bash
./gradlew :app:desktopApp:createDistributable
```

На Linux результат находится в `app/desktopApp/build/compose/binaries/main/app/TaskB`.
Запуск: `app/desktopApp/build/compose/binaries/main/app/TaskB/bin/TaskB`.
Релизный вариант собирается задачей `:app:desktopApp:createReleaseDistributable` в
`app/desktopApp/build/compose/binaries/main-release/app/TaskB`.

Установщики собираются на соответствующей ОС:

| Система | Команда | Каталог результата |
| --- | --- | --- |
| Linux, RPM | `./gradlew :app:desktopApp:packageRpm` | `app/desktopApp/build/compose/binaries/main/rpm` |
| Linux, DEB | `./gradlew :app:desktopApp:packageDeb` | `app/desktopApp/build/compose/binaries/main/deb` |
| Windows, MSI | `./gradlew :app:desktopApp:packageMsi` | `app/desktopApp/build/compose/binaries/main/msi` |
| macOS, DMG | `./gradlew :app:desktopApp:packageDmg` | `app/desktopApp/build/compose/binaries/main/dmg` |

Для RPM нужен `rpmbuild`, для DEB — `dpkg-deb`. Подробнее об инструментах упаковки — в
[документации Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).

Чтобы собрать приложение и добавить ярлык в меню приложений Linux:

```bash
./gradlew :app:desktopApp:installLinuxDesktopEntry
```

Задача записывает `applications/com.mkulikov.taskb.desktop` в `$XDG_DATA_HOME`, а если
переменная не задана — в `~/.local/share`. Ярлык указывает на приложение в каталоге `build`;
после очистки сборки выполните задачу повторно.

### Запуск тестов

Используйте кнопку запуска рядом с кодом теста в редакторе IDE или задачи Gradle:

- Общие тесты фич на JVM Android: `./gradlew :feature:hello:testAndroidHostTest :feature:group:testAndroidHostTest :feature:task:testAndroidHostTest`
- Тесты настольного приложения: `./gradlew :app:shared:jvmTest`

Проверки перенесённой инфраструктуры и фич на JVM:

```bash
./gradlew :core:mvi:jvmTest :core:navigation:jvmTest :core:database:jvmTest \
  :feature:settings:jvmTest :feature:hello:jvmTest :feature:group:jvmTest \
  :feature:task:jvmTest :app:shared:jvmTest
```

Тесты проверяют обработку и отмену MVI-эффектов, навигационный стек, освобождение ресурсов,
миграции базы, внешние ключи, транзакции и сохранение настроек между запусками DI-контейнера.
Тесты фич проверяют фильтры, редактирование, обработку ошибок и повторные запросы;
UI-тесты Desktop проходят сценарии навигации и CRUD, выбор дат, диалоги, изменение размера
окна и освобождение ресурсов экранов. Снимки интерфейса сохраняются в
`app/shared/build/reports/screenshots`.

Общие тесты Store также можно выполнить на JVM Android:

```bash
./gradlew :feature:hello:testAndroidHostTest :feature:group:testAndroidHostTest :feature:task:testAndroidHostTest
```
