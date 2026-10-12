# Code Editor (Android)

Мобільний редактор коду для Android на **Jetpack Compose** з підсвіткою синтаксису,
вкладками, пошуком/заміною, автодоповненням, темами та лігатурним шрифтом.

## Можливості

- **Редагування**: багаторядковість, автовідступ, авто-закриття дужок/лапок,
  дублювання й переміщення рядків, коментування (`Ctrl+/`), мульти-курсор (`Ctrl+D`).
- **Підсвітка синтаксису** для Kotlin, Java, C/C++, Python, JS/TS, HTML, CSS, JSON,
  Markdown, Bash — через змінний `SyntaxHighlighter`.
- **Вкладки та файли**: SAF (`OpenDocument` / `OpenDocumentTree`), дерево проєкту,
  адаптивний layout (бічна панель ≥720dp, інакше bottom-sheet), сесія відновлюється.
- **Пошук і заміна**: literal / regex / ціле слово / регістр, «Замінити все» одним кроком undo.
- **Автодоповнення**: ранжування слів буфера за частотою + ключові слова/типи мови.
- **Мобільний UX**: рядок символів, pinch-to-zoom, подвійний тап — виділення слова.
- **Теми**: Default, Dracula, One Dark, Solarized (light/dark), GitHub (light).
- **Шрифти**: monospace / sans / serif та **JetBrains Mono** з лігатурами (`calt`).
- **Кодування**: визначення/запис BOM і UTF-8/UTF-16/Windows-1251; збереження на місце.

## Стек

| Шар | Технологія |
|-----|-----------|
| UI | Jetpack Compose, Material 3 |
| Архітектура | MVVM + Clean-ish (core / domain / data / ui) |
| DI | Hilt 2.52 |
| Персистенція | Room 2.6.1, DataStore Preferences 1.1.1 |
| Збірка | AGP 8.6.1, Kotlin 2.0.21, KSP, Gradle 8.9 |
| Тести | JUnit4, Turbine, Compose UI Test |

`minSdk 26`, `target/compileSdk 35`, JVM 17, namespace `com.codeeditor`.

## Структура

```
app/src/main/java/com/codeeditor/
├── core/            # чистий Kotlin, без Android — легко юніт-тестується
│   ├── editor/      # TextDocument, Selection, UndoRedo, highlight/, edit/
│   ├── encoding/    # TextEncoding, TextCodec (BOM, UTF-8/16, CP1251)
│   ├── search/      # TextSearch, SearchOptions, SearchMatch
│   ├── autocomplete/# WordIndex, CompletionEngine, BufferCompletionProvider
│   ├── session/     # EditorSession — фасад над документом/виділенням/undo
│   ├── model/       # LanguageDefinition, TokenType
│   └── util/
├── domain/          # model/, repository/, usecase/
├── data/            # db/ (Room), file/ (SAF), session/, settings/ (DataStore)
├── di/              # Hilt-модулі
└── ui/              # theme/, editor/, workspace/, files/, settings/
```

## Збірка

Потрібні JDK 17 та Android SDK (API 35). Створіть `local.properties`:

```properties
sdk.dir=/шлях/до/Android/Sdk
```

Команди:

```bash
gradle wrapper            # лише якщо gradle-wrapper.jar відсутній
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # потрібен емулятор/пристрій
```

## Release-збірка з власним підписом

1. Згенеруйте keystore:
   ```bash
   keytool -genkeypair -v -keystore codeeditor-release.jks \
     -alias codeeditor -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Скопіюйте `keystore.properties.template` → `keystore.properties` і заповніть значення.
3. Зберіть:
   ```bash
   ./gradlew :app:assembleRelease
   ```
   APK з'явиться в `app/build/outputs/apk/release/`.

Якщо `keystore.properties` відсутній, release тимчасово підписується debug-ключем
(зручно для локальної перевірки R8/resource shrinking). Файли `*.jks` і
`keystore.properties` додані в `.gitignore` — не комітьте їх.

## Тести

- Юніт (JVM, без пристрою): `app/src/test/...` — документ, undo/redo, сесія,
  пошук, автодоповнення, кодування, підсвітка.
- Інструментальні (Compose UI): `app/src/androidTest/...` — палітри тем і лист
  налаштувань.

## Історія етапів

1. Скафолд Gradle, `core/editor`, підсвітка, базова тема.
2. Вкладки, SAF, дерево файлів, сесія, Room + DataStore.
3. Розширене редагування: мульти-курсор, коментарі, рух рядків.
4. Пошук і заміна (regex / слово / регістр).
5. Автодоповнення (word index + провайдери).
6. Мобільний UX: row з символами, pinch-to-zoom.
7. Теми (Dracula/One Dark/Solarized/GitHub), шрифти + лігатури, UI-тести.
8. Git, README, release-підпис і R8.

## Ліцензія

Шрифт **JetBrains Mono** — SIL Open Font License 1.1.
