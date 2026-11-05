# Стратегія тестів ShoppingList: користь, а не відсотки

Мета: перевіряти поведінку, що може втратити або зіпсувати дані, без дорогого тестування кожного методу Activity.

## Рівні тестування

| Що | Підхід | Де виконується |
|---|---|---|
| Моделі `ShoppingItem`, `ShoppingList` | JUnit4 unit-тести: нормалізація й валідація, поля | кожний PR, без Android |
| `ShoppingListTextFormatter` | JUnit4: порядок, статуси, Unicode, кількість, порожні списки | кожний PR |
| `FormLayoutMeasurements` | JUnit4: граничні ширини й обмеження висоти | кожний PR |
| `ShoppingDatabase` | **Android instrumented інтеграційні тести** на справжній SQLite: CRUD, міграція v1→v2, транзакції, архів/відновлення, timestamps | окремий workflow на PR зі змінами даних та вручну |
| `ShoppingRepository`, `ShoppingViewModel` | існуючі end-to-end Android сценарії, перевірки асинхронності та ротації | запускати перед значущим релізом |
| Activity, Adapter, `AdaptiveFormLayoutController` | ручний smoke/regression QA плюс Lint; критичні сценарії вже є у Espresso | перевірка на телефоні/емуляторі перед релізом |

**Важливо:** перевірка компіляції `compileDebugAndroidTestJavaWithJavac` не означає виконання інтеграційних тестів.

## Як виконати перевірки

Швидка локальна перевірка:

```bash
./gradlew spotlessCheck :app:testDebugUnitTest :app:lintDebug --no-daemon --no-configuration-cache
./gradlew :app:createDebugUnitTestCoverageReport --no-daemon --no-configuration-cache
python3 scripts/report-focused-coverage.py
```

SQLite на підключеному пристрої/емуляторі:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.shoppinglist.ShoppingDatabaseTest \
  --no-daemon --no-configuration-cache
```

Або GitHub Actions → **SQLite Android integration tests** → Run workflow.

Перед релізом, коли є телефон/емулятор, запусти всі instrumented UI тести:

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon --no-configuration-cache
```

## Coverage: два різні показники

1. **Повний JaCoCo**: `app/build/reports/coverage/test/debug/index.html`, у ньому усі скомпільовані класи, але рахуються **лише JVM unit-тести**.
2. **Focused JVM summary**: `python3 scripts/report-focused-coverage.py` читає **той самий** `report.xml`, друкує:
   - загальні covered/total lines для контексту;
   - окремо covered/total для `model/*`, `share/*`, `ui/FormLayoutMeasurements*`.

Це **не приховування помилок і не відключення тестів**. Відфільтровується лише другий, спеціалізований показник. Оригінальний JaCoCo XML/HTML не змінюється. **Немає довільного порогу 80%**.

### Що не входить до focused JVM metric

- Activity, RecyclerView Adapter і Android UI: тести інтерфейсу/ручний QA.
- `AdaptiveFormLayoutController`: візуальна адаптація, а математичні правила перевіряє `FormLayoutMeasurementsTest`.
- `ShoppingDatabase`: окремі інтеграційні SQLite-тести на Android.
- `ShoppingRepository`, `ShoppingViewModel`, `ListsViewModel`: асинхронні та lifecycle-сценарії; їх не можна вважати перевіреними лише через виключення зі звіту. Перевіряти конкретні ризикові переходи через Android-тести перед випуском.

Якщо в майбутньому бізнес-логіка переїде до цих класів або з'являться регресії, тестувати її напряму, а не додавати чергове виключення.

### Обмеження

SQLite workflow не приєднує instrumented coverage до JVM JaCoCo. Це **окремий факт проходження інтеграційних тестів**, а не спосіб автоматично збільшити загальний JVM %. Якщо вимагатиметься повне комбіноване покриття, треба окремо налаштовувати Android instrumentation coverage і злиття execution data, поки це зайве.

**Критерій успіху:** зелені unit-тести і Lint, реальні SQLite інтеграційні тести при змінах БД, зелений SpotBugs і ручна перевірка основних сценаріїв перед випуском.
