# ShoppingList v2

## Screens and state

The launcher opens `ListsActivity`: active lists ordered by updatedAt descending,
with ID descending as the stable tie-breaker. A row shows its name, localized
modification time, purchased/total counts, and progress for nonempty lists.
The Archive action opens the same Activity in archive mode, ordered by archivedAt.

`MainActivity` is the existing item screen, now bound to the immutable listId in
its Intent. Its ViewModel retains that list's draft across configuration changes.
Toolbar Back returns to the parent screen. Parent screens reload on resume.
Archived lists hide the form and deletion controls and disable checkboxes.
Restoring preserves both list/item IDs and purchase states.

The application still owns one repository and one serial worker. Writes and
subsequent reads are ordered; a read failure does not turn a committed write into
a failed write. Overview state lives in `ListsViewModel`; the list-name dialog
uses SavedStateHandle. Configuration changes retain archive confirmation state.

## Database and migration

`shopping.db` upgrades from schema 1 to schema 2:

- `shopping_lists`: AUTOINCREMENT id, required nonblank name, createdAt,
  updatedAt, nullable archivedAt. Names are intentionally not unique.
- `items`: original fields and IDs, plus a required listId foreign key.
- Foreign keys are enabled in onConfigure. Deleting a referenced list is rejected.
- Indexes cover list membership/item ordering and active/archive ordering.

SQLiteOpenHelper executes onUpgrade and user_version changes in a transaction.
Migration creates “Мій список”, copies all old items to a replacement table with
that listId, swaps the table, and preserves the previous AUTOINCREMENT high-water
mark, including IDs of previously deleted items. Failure rolls the transaction
back to schema 1. There is no deleteDatabase, clear-data, or destructive fallback
in production code. Fresh installs start with no lists.

Dates are epoch milliseconds. Migration time becomes the legacy list's initial
dates because schema 1 had no timestamps. Ordinary reads never change updatedAt.
Rename and item mutations update it atomically. Repeating an unchanged purchase
state or deleting a nonexistent item does not touch it. Archive/restore also
update it; archivedAt is set at completion and cleared on restore.

Archive is always explicit. An incomplete list requires confirmation, checked
again inside the database transaction. Archived item/name writes are rejected
by the database helper, regardless of UI state.

## Verification

```bash
export JAVA_HOME=/home/oleksandr/Android/android-studio/jbr
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug
```

The custom instrumentation Application uses a unique test database. Tests never
open shopping.db. Gradle is configured to leave installed APKs after connected
tests rather than uninstalling the target app and its data on completion.

Database tests cover independent lists, duplicate names, scoped writes,
timestamps/no-op reads, archive confirmation, read-only guards, restoration,
legacy field/ID/sequence preservation, foreign keys, empty migration, and rollback
of an injected migration failure. Navigation tests cover dialog/screen recreation,
creation, renaming, isolated contents, return-to-overview refresh, archive
confirmation/cancellation, read-only viewing, and restoration. Existing item,
async-error, lifecycle, and icon tests are retained with explicit test list IDs.

Physical devices, other Android versions, OEM launchers, and force-kill timing
during writes require separate verification. In-process configuration changes
preserve queued writes; killing a process can interrupt an uncommitted operation.
Receipts, camera, OCR, account features, and synchronization are not implemented.

## Completed run

Validated APK versionCode 2 / versionName 2.0 on the API 37 emulator:
assembleDebug, 5 JVM tests, 23 instrumented tests, lintDebug (zero errors; one
newer-Gradle-version warning), and git diff --check. The installed production
Application was also cold-started independently of the test runner. Migration
verification used isolated version-1 files; no physical-device upgrade was run.

Screenshots from isolated navigation tests: app/build/v2-previews/active-lists.png,
archive.png, and archived-items.png. APK: app/build/outputs/apk/debug/app-debug.apk.

## Source changes

New files: ShoppingList, ListsActivity, ListsViewModel, ListsAdapter;
activity_lists.xml, item_list.xml, ids.xml, ic_back.xml, ic_more.xml;
ListsNavigationTest and this document.

Updated: ShoppingDatabase (schema/transactions/guards), ShoppingRepository
(scoped reads/writes and overview operations), ShoppingItem (listId),
ShoppingViewModel (list scope and metadata), MainActivity (title/back/archive
mode), ShoppingAdapter (read-only rendering), activity_main.xml, strings.xml,
AndroidManifest.xml (new launcher Activity), app/build.gradle.kts (version 2.0),
gradle.properties (keep installed test targets), ShoppingDatabaseTest,
ShoppingScreenTest, TestShoppingApplication, and ShoppingItemTest.
