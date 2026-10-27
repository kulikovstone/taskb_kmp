Проект Kotlin Multiplatform для Android, iOS, Web, настольных приложений (JVM) и сервера.

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

Новые модули пока представляют собой каркас: код приложения и внешние библиотеки, такие как
Navigation, Koin, Material Symbols и Room/KSP, будут перенесены отдельно. В частности, модуль
базы данных пока не содержит реализации хранилища. При переносе хранения данных потребуется
учесть особенности каждой платформы, включая Web.

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

---

Подробнее о [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

Отзывы о Compose/Web и Kotlin/Wasm можно оставить в открытом Slack-канале
[#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
Об обнаруженных проблемах можно сообщить в [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
