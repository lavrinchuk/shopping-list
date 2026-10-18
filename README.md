# Список покупок

Android MVP: Java, Views/XML, RecyclerView та локальна SQLite-база (SQLiteOpenHelper).
Один екран: назва й необов’язкова кількість (довільний текст, наприклад «2 кг»),
додавання, позначка придбання та видалення. Непридбані товари йдуть першими;
всередині групи зберігається порядок додавання. Дані записуються одразу.

## Запуск

Відкрити проєкт в Android Studio, дочекатися Gradle Sync, запустити конфігурацію `app`
на пристрої або емуляторі Android 8.0/API 26 чи новішому.
Наявна конфігурація використовує SDK 37.0, Gradle 9.6.0 і JDK 25.

Для цього середовища (кеш Gradle залишається всередині проєкту):

```bash
export JAVA_HOME=/home/oleksandr/Android/android-studio/jbr
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Встановлення: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Архітектура та збереження даних

`MainActivity → ShoppingViewModel → ShoppingRepository → ShoppingDatabase`.

- `ShoppingApplication` тримає одне сховище та одну послідовну чергу SQLite-операцій
  протягом життя процесу. Зміна конфігурації не скасовує записи.
- ViewModel тримає список, стан додавання та чернетку. `SavedStateHandle` зберігає
  чернетку для відновлення стану. Фонові задачі не захоплюють Activity чи View.
- Поля залишаються доступними під час запису. Успішне додавання очищає лише
  незмінену чернетку; повторне надсилання під час запису заблоковане.
- Успішний запис та перечитування — окремі результати. Після помилки читання
  Snackbar пропонує «Повторити», що виконує тільки читання.
- SQLiteException логуються із причиною; програмні RuntimeException логуються
  та не маскуються повідомленням про помилку сховища.
- ListAdapter/DiffUtil обчислює різницю у фоні та адресно оновлює RecyclerView.

Ім’я `shopping.db`, версія 1 та схема бази залишилися незмінними. Видалення,
пересоздання чи міграція користувацької бази не виконуються. Знищення процесу ОС
може перервати незавершену операцію; гарантія завершення тут стосується зміни
конфігурації та повторного відкриття Activity в тому самому процесі.

## Перевірки

- `ShoppingItemTest`: валідація назви, пробіли, необов’язкова кількість.
- `ShoppingDatabaseTest`: CRUD, сортування, повторне відкриття й сумісність
  з базою початкового MVP версії 1, включно з ID та позначкою придбання.
- `ShoppingScreenTest`: основний сценарій екрана; поворот під час запису
  зі зміненою та незміненою чернеткою; повторне відкриття під час запису;
  успішний запис із невдалим читанням та повторним читанням; повторна спроба
  після невдалого запису; швидкі зміни статусу; сортування/видалення на 500
  елементах; ізоляція тестової бази.

Власний `ShoppingTestRunner` підміняє Application на тестову, яка відкриває
лише `instrumentation-<UUID>.db`. Тести БД також використовують окремі імена.
Жоден тест не відкриває та не очищає `shopping.db`.
Espresso IdlingResource чекає завершення черги сховища, main-thread callbacks
та застосування DiffUtil. Для відтворення незавершеного запису застосовано
CountDownLatch з обмеженням часу, без sleep або часових затримок.

Android-тести потребують емулятора або підключеного тестового пристрою.
Фізичний пристрій та інші версії Android, крім API 37, не перевірено.
Для ручної перевірки: встановити APK поверх попередньої версії без видалення
застосунку, перевірити старі покупки; додати товар, повернути екран,
перевірити нове введення; закрити процес та повторно відкрити застосунок.

Результат остаточного прогону після рефакторингу: `assembleDebug`,
`testDebugUnitTest` (5 тестів), `connectedDebugAndroidTest` (13 тестів на API 37)
та `lintDebug` виконані успішно. Lint: 0 помилок; залишилися попередження про
версії залежностей, autofill та два невикористані кольори початкового шаблону.


## Material 3 UI

Форма використовує TextInputLayout і MaterialButton. Кількість та додавання
розташовані поруч за достатньої ширини; на вузькому екрані чи з великим шрифтом
переходять у вертикальне компонування. На низькому екрані форма прокручується
окремо, залишаючи місце списку. Клавіша Done у полі кількості викликає ту саму
дію додавання. Світла й темна палітри визначені в ресурсах кольорів.

Після редизайну успішно виконані assembleDebug, testDebugUnitTest (5 тестів),
connectedDebugAndroidTest (13 тестів) і lintDebug. Додатковий UI-сценарій
materialAppearanceAndKeyboard пройшов на емуляторі API 37 у трьох конфігураціях:
звичайний екран, 320dp/100% та 320dp/200%. Він перевіряє теми, клавіатуру,
IME-додавання й чернетку після відтворення Activity. IdlingResource також
очікує завершення анімацій RecyclerView. Lint залишив лише попередження про
новішу версію Gradle. TalkBack і фізичний пристрій не перевірені.

Скриншоти цієї перевірки: `app/build/ui-previews/` (артефакти збірки).
Префікси `default`, `narrow`, `narrow-large`; стани `empty-light`, `list-light`,
`list-dark` та `keyboard`. Знімки зроблені з ізольованою тестовою базою.

## Firebase App Distribution (debug APK)

Ідентифікатор застосунку: `ua.lavrinchuk.shoppinglist`; Java namespace залишається
`com.example.shoppinglist`. Android вважає це окремим застосунком від попереднього
`com.example.shoppinglist`: стара SQLite-база залишається у старому застосунку,
новий починає з порожніх даних. Автоматичного перенесення немає. Не видаляйте
старий застосунок, якщо його локальні дані ще потрібні.

Одноразово у Firebase Console створіть або виберіть проєкт, зареєструйте Android
app з package name **`ua.lavrinchuk.shoppinglist`**, увімкніть App Distribution
(Get started) і створіть групу з alias **`friends`**. Додавайте email тестувальників
лише в Console. Обліковий запис публікатора повинен мати права на App Distribution.
З Project settings → General → Your apps скопіюйте **Firebase App ID**: це не
package name і не Firebase Project ID.

Використовується офіційний App Distribution Gradle plugin 5.3.0 через Version
Catalog. Firebase SDK, Google Services plugin, `google-services.json`, `firebase init`
та `firebase.json` для цього способу розповсюдження не потрібні.

Перша публікація з кореня проєкту (після налаштування JDK/Android SDK з розділу
«Запуск»):

```bash
# Install Firebase CLI if it is not already installed.
npm install -g firebase-tools
firebase login
# Paste the real Firebase App ID from the Console when prompted.
read -r -p 'Firebase App ID: ' FIREBASE_APP_ID
export FIREBASE_APP_ID
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew assembleDebug --no-daemon
./gradlew appDistributionUploadDebug --no-daemon --no-configuration-cache
```

`appDistributionUploadDebug` залежить від `assembleDebug`, тому перед завантаженням
завжди збирає актуальний APK і розповсюджує його групі `friends`. Замість змінної
оточення можна передати `-PfirebaseAppId=...` (ця властивість має пріоритет).
Без App ID звичайні збірки й тести працюють; публікація потребує реального App ID
та авторизації. Upload запускайте окремою командою без configuration cache. `firebase login` зберігає авторизацію локально поза репозиторієм.
Не додавайте до Git токени, приватні JSON-ключі, паролі, signing keystore чи списки
email. Для автоматизації можна задати `GOOGLE_APPLICATION_CREDENTIALS` як шлях до
service account JSON **поза проєктом**, з роллю Firebase App Distribution Admin.
`.gitignore` додатково виключає типові локальні ключі, `.secrets/`, `.env*`,
списки `testers.txt` / `*-testers.txt` та Firebase debug logs; не покладайтеся лише
на назву файлу для захисту секретів.

Перед кожною наступною тестовою версією збільшуйте `versionCode` у
`app/build.gradle.kts` (зараз `2`, наступний `3`, далі `4` тощо). `versionName`
є видимим номером версії та змінюється окремо. Повторіть команду upload.

Debug APK автоматично підписується локальним Android debug key. Firebase
App Distribution не перепідписує APK. Для оновлення зі збереженням локальних
даних потрібні той самий applicationId, сумісний підпис і не нижчий versionCode
(для нових публікацій завжди збільшуйте його). На іншому комп’ютері debug key
зазвичай інший: використовуйте той самий захищений ключ для всіх тестових оновлень,
не додаючи його до Git, і збережіть резервну копію. Перехід на release key також
змінює підпис; звичайне оновлення з іншим ключем буде відхилено Android.
Видалення застосунку перед перевстановленням видаляє його локальні дані.

Документація: [Gradle plugin та авторизація](https://firebase.google.com/docs/app-distribution/android/distribute-gradle),
[Firebase CLI](https://firebase.google.com/docs/cli),
[підписи Android](https://developer.android.com/studio/publish/app-signing).
