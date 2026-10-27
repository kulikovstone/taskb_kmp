Проект Kotlin Multiplatform для Android, iOS, Web, настольных приложений (JVM) и сервера.

Основная разработка сейчас ведётся для Desktop; следующая платформа — Android.
Модули и целевые платформы iOS и Web сохранены как каркас на будущее.

* [/app/iosApp](./app/iosApp/iosApp) содержит приложение для iOS. Даже при использовании общего
  интерфейса на Compose Multiplatform для iOS нужна отдельная точка входа.
  Здесь также следует размещать код SwiftUI.

* [/app/shared](./app/shared/src) содержит общий код приложений на Compose Multiplatform.
  Внутри расположены следующие каталоги:
    - [commonMain](./app/shared/src/commonMain/kotlin) — код, общий для всех платформ.
    - Остальные каталоги содержат Kotlin-код для платформы, указанной в названии каталога.
      Например, вызовы Apple CoreCrypto для iOS следует размещать в
      [iosMain](./app/shared/src/iosMain/kotlin), а код для настольного приложения (JVM) — в
      [jvmMain](./app/shared/src/jvmMain/kotlin).

* [/core](./core/src) содержит код, общий для клиентских приложений и сервера.
  Основной каталог — [commonMain](./core/src/commonMain/kotlin).
  При необходимости здесь также можно добавлять каталоги с кодом для отдельных платформ.

* [/server](./server/src/main/kotlin) содержит серверное приложение на Ktor.

### Модули приложения

Для переноса кода из `taskb_desktop` подготовлена следующая структура модулей:

| Модуль | Назначение |
| --- | --- |
| `:core:mvi` | Общие контракты Store и инфраструктура MVI. |
| `:core:navigation` | Инфраструктура навигации и навигационный контейнер Compose. |
| `:core:database` | Настройка локальной базы данных и доступ к ней. |
| `:ui` | Тема TaskB, палитра, иконки и переиспользуемые компоненты интерфейса. |
| `:feature:hello` / `:feature:hello:api` | Реализация экрана приветствия и публичные контракты. |
| `:feature:home` / `:feature:home:api` | Реализация главного экрана и публичные контракты. |
| `:feature:settings` / `:feature:settings:api` | Реализация настроек приложения и публичные контракты. |
| `:feature:task` / `:feature:task:api` | Задачи, группы и редакторы с отдельными публичными контрактами. |

`:app:shared` объединяет модули приложения для платформенных точек входа.
Существующий модуль `:core` остаётся общим с `:server` и не зависит от клиентских фич.
Зависимости между модулями повторяют структуру настольного проекта: фичи обращаются к другим
фичам через их модули `api`, а `:core:database` зависит от API-модулей задач и настроек.

В общие модули перенесены MVI, навигация на Navigation 3, база данных на Room 3 и настройки
приложения. Подключены Coroutines, Koin, Compose Material и Material Symbols.
Реализации экранов фич и остальных репозиториев будут перенесены следующим шагом;
в приложении пока отображается стартовый экран шаблона.

Существующие пакеты `com.mkulikov.taskb` сохранены, в том числе имя класса базы данных,
чтобы использовать прежнюю историю схем Room. Точки входа нового проекта находятся
в пакете `com.mkulikov.taskb_kmp`.

Общие настройки сборки вынесены в Gradle convention plugins в [/build-logic](./build-logic):

* `taskb.multiplatform.library` настраивает целевые платформы Android, JVM, iOS ARM64,
  iOS Simulator ARM64, браузерные JS и Wasm, а также `kotlin-test` в `commonTest` и тесты
  Android, выполняемые на JVM. Байт-код библиотек для Android и JVM рассчитан на Java 11,
  как и в Android-приложении.
* `taskb.compose.library` дополнительно включает Compose, компилятор Compose и ресурсы Android.

Версии плагинов и Android SDK берутся из `gradle/libs.versions.toml`.
Общий код каждого модуля следует размещать в `src/commonMain/kotlin`, а тесты — в
`src/commonTest/kotlin`. Каталоги с кодом для отдельных платформ добавляются по мере необходимости.
Для компиляции iOS и запуска тестов в симуляторе требуется macOS.

### База данных и внедрение зависимостей

Сущности и DAO находятся в `commonMain` API-модулей задач и настроек. `:core:database`
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

`appModule` объединяет модули базы данных, настроек и навигации. Реализации `App()` для
Desktop и Android передают фабрику базы общему приложению. Koin создаёт базу при первом обращении
и закрывает её при закрытии контейнера. Жизненным циклом контейнера управляет Compose.

Для iOS и Web оставлен стартовый интерфейс шаблона без инициализации базы и DI-контейнера.
Общие исходники и объявления targets сохраняются, а платформенное хранение и запуск
инфраструктуры будут добавлены, когда начнётся разработка этих версий.

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

### Запуск тестов

Используйте кнопку запуска рядом с кодом теста в редакторе IDE или задачи Gradle:

- Тесты Android: `./gradlew :app:shared:testAndroidHostTest`
- Тесты настольного приложения: `./gradlew :app:shared:jvmTest`
- Тесты сервера: `./gradlew :server:test`
- Тесты веб-приложения:
    - Wasm: `./gradlew :app:shared:wasmJsTest`
    - JS: `./gradlew :app:shared:jsTest`
- Тесты iOS: `./gradlew :app:shared:iosSimulatorArm64Test`

Проверки перенесённой инфраструктуры на JVM:

```bash
./gradlew :core:mvi:jvmTest :core:navigation:jvmTest :core:database:jvmTest :feature:settings:jvmTest :app:shared:jvmTest
```

Тесты проверяют обработку и отмену MVI-эффектов, навигационный стек, освобождение ресурсов,
миграции базы, внешние ключи, транзакции и сохранение настроек между запусками DI-контейнера.

---

Подробнее о [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

Отзывы о Compose/Web и Kotlin/Wasm можно оставить в открытом Slack-канале
[#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
Об обнаруженных проблемах можно сообщить в [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
