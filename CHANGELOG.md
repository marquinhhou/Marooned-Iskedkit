# Changelog: Marooned IskedKit v3.0.0

## Rebrand

- **CRS Scheduler is now Marooned IskedKit.** In-place update: package ID (`dev.marquinhhou.crsscheduler`), signing key, and all existing schedules/notes/settings are unchanged, so current installs just update normally with no data loss. App display name, tagline ("Your semester in a kit!"), exported-image footer, `.ics` PRODID, and clipboard label are all updated to match. A one-time "Welcome to Marooned IskedKit" screen greets the first launch after updating (shown exactly once per install; existing data is untouched).

## Added

- **University affiliation** (Settings → University card): pick UP -- with a campus picker covering all nine constituent universities (Diliman, Los Baños, Manila, Visayas, Open University, Mindanao, Baguio, Cebu, Tacloban) -- or any other school by name. Switching after data already exists asks once ("Your data will be preserved") and re-gates the import tooling live.
- **Onboarding wizard**, rebuilt to four steps -- University → Import → Review → Profile & Extras -- with uniform progress segments (completed / current / upcoming). Manual class entry is available from step 1 (Import), not just step 2.
- **Profile Card**: a circular avatar button (top-right of the app) opens a shareable identity card -- name, school line derived from your affiliation, mail/phone/student no/course/year, social links, website, and dorm/address -- with a live themed preview, an inline editor, and **Save as Image** PNG export to the gallery.
- **Custom theme**, alongside the existing Adaptive and Nothing options, with two modes:
  - **Color** -- pick a solid background via hex input, with a live swatch preview.
  - **Photo** -- pick a photo (via Android's Photo Picker, copied into the app's own storage so it never depends on an external URI staying valid), with opacity and blur controls and an accent swatch picker drawn from the photo's own colors.
  Custom derives every color -- background, text, icons, chips, dialogs -- independently of the other themes, and is applied consistently across every screen and every widget; switching to or from Custom refreshes widgets immediately instead of waiting for their next periodic tick.
- **Class syllabus attachments.** Edit Class Info can attach a PDF/Word/text/image file to any class, kept across re-imports since it's keyed by class code. A document icon on the class's Today-widget row opens the attachment directly, no in-app detour.
- **Note attachments.** A saved note can attach any file type from its edit screen; the same document icon on its Notes-widget row opens the first attachment directly.

## Changed

- **CRS parser is Diliman-only.** Every other affiliation sees "ADD YOUR CLASSES" with manual entry (+ADD CLASS) as the only path in, with instructions pointing to the right portal (SAIS on other UP campuses; a screenshot/PDF picker stored as a reference attachment for other schools). `.ics` import and manual entry still work identically for everyone.
- **GE is retired.** Any existing GE preference silently migrates to Adaptive (or Nothing, on pre-Android-12 devices) the next time settings are read; the theme picker now offers Adaptive / Nothing / Custom only.
- **Edit Class Info reaches parity with Add Class**: it can now correct a class's days and start/end time, not just room/instructor/units, with the same validation (at least one day, end after start). It's now a collapsible dropdown (a settings-style header with a rotating chevron) rather than a switch, and its tools moved inside the import card, just below the parser zone.
- **"ADD YOUR CLASSES" reordered**: instructions → + ADD CLASS → CLEAR → SAVED SCHEDULES → (Diliman-only: parser zone + status box) → hairline → EDIT CLASS INFO.
- **Map auto-fill can be turned off independently of Maps itself** -- ON pre-fills room + campus context from your schedule into a maps search; OFF opens free-typing with no injected context, in both the widget flow and Full Schedule.
- **New launcher icon**, regenerated across every density and every adaptive layer (background/foreground/monochrome), sized to stay clear of the round-icon safe zone.
- **Uniform control sizing**: every Primary/Secondary/Quiet control (buttons, chips, dialog actions) now shares the same 48dp-floor height and consistent padding throughout the app.
- **Week schedule table simplified**: occupied cells are now bare, appropriately-scaled labels instead of colored pill/tile backgrounds; today's column is still emphasized through bolder text.
- **README and the Gradle project name caught up with the rebrand** -- both now say Marooned IskedKit; `applicationId`/`namespace` deliberately stay `dev.marquinhhou.crsscheduler` so existing installs keep updating in place.

## Fixed

- **`assembleRelease` compiles again.** A dialog-button-spacing fix had used `android.R.id.buttonPanel`, an AOSP-internal id that was never part of the public SDK; it's now resolved via `getParent()` on the first available dialog button instead.
- **Settings crash on Edit Class Info** ("child already has a parent") when the merge assumed those tools already lived inside the import card -- fixed by properly detaching views before re-attaching them.
- **Widgets are crash-resistant now.** Every provider, the shared refresh scheduler, and both list services catch any rendering failure and fall back to a minimal card instead of crashing or blanking the home screen; Settings also now shows the real stack trace (with a Copy Trace button) if anything else ever fails.
- **Widget-launched screens no longer leave stale screens behind on Back** -- widget taps now start a clean task (`NEW_TASK | CLEAR_TASK`) instead of resurfacing whatever was already open.
- **Custom theme reaches every screen and widget correctly**, the result of a long series of fixes: colors bypassed by `android:backgroundTint`, cards that were never actually translucent, an alpha-multiplication bug that left widget rows/cards nearly invisible, a backwards frost-blend calculation, and several screens (WidgetSaveActivity, WidgetForm5PromptActivity, WidgetActionActivity, ArchivedNotesActivity, ScheduleHistoryActivity, WeekScheduleActivity, and dynamically-rebuilt lists on ConfigureActivity) that never applied Custom theming at all are all fixed at their source. Text/icon contrast is now resolved locally against each element's own background rather than by role alone, so labels stay legible regardless of the chosen color or photo.
- **Custom Photo mode is reliable.** Photo picking moved to Android's built-in Photo Picker and copies the file into local storage immediately, removing the dependence on an external URI that caused earlier crashes and "photo reverted to blank" reports; decoding has a resilient fallback path and a properly downsampled preview so it no longer runs out of memory on large camera photos.

# Changelog: CRS Scheduler v2.1

## New

- **Notes widget** — a third home-screen widget, separate from the Today/Week pair, for notes tied to a specific subject (picked from whatever's currently loaded, however it got there — CRS HTML or `.ics`) or filed under a standing Miscellaneous spot for anything not tied to a class.
  - Each note can optionally carry a deadline — a date, and optionally an exact time on top of that — shown as a color-coded badge, and can be flagged Urgent independent of any deadline.
  - Doubles as a to-do list: tap the dot on a widget row to mark a note done/undone right there, no need to open anything. Done notes get a strikethrough and sink to the bottom of the list.
  - Add or edit from the widget's "+ NOTE" button or by tapping an existing row — opens a dedicated screen with a subject-chip picker, title/details fields, a deadline date + time picker, an Urgent toggle, mark-as-done, copy-to-clipboard, and delete (with confirmation).
  - Archive a done note right from its widget row; a dedicated Archived Notes screen lets you restore or permanently delete anything sent there.
  - Copy all active notes to the clipboard as plain text from the widget header, or copy just one note from its edit screen.
  - A LIST/GROUPED toggle in the header switches between the flat list and a collapsible by-subject view.
  - Same on-device-only storage as the rest of the app; no new permissions.
- **Deadline reminders** — once a note has a deadline, optionally get a notification before it arrives (1 hour, 3 hours, 1 day, or 3 days ahead), set right there in the note editor. Off by default and independent per note. Same permission prompts and on-device scheduling as class reminders. Marking a note done or archiving it — from its edit screen or right from the widget row — cancels its pending reminder.

## Fixed

- **Deadline and time fields in the note editor are now the same size** — the time row used to be visibly shorter, use a different background, and lacked tap feedback; both rows now match.
- **Permanently deleting an archived note now refreshes widgets and reminders**, same as restoring one already did.

## Changed

- **First-run setup wizard: manual class entry is now available on step 1** (Import), not just step 2 (Review) -- so anyone without a CRS page or `.ics` file to import can build their schedule by hand from the very start.
- **The "tap empty space to add a note" toggle moved to step 3** (Profile & Extras) of the first-run wizard, instead of always showing regardless of step.

# Changelog: CRS Scheduler v2.0

## New

- **Profile section** (config screen, collapsed by default) — optional name, student number, course, and year/standing, each with its own switch for whether it appears on the exported schedule image. Off by default.
- **Form 5 section** (config screen, collapsed by default) — attach a Form 5 PDF for quick access (open, replace, or remove) later. Targeted for freshmen with no physical IDs yet.
- **First-run setup wizard** — the very first time you open the config screen with nothing loaded, it walks you through three steps (Import, Review your classes, then Profile & Extras) instead of showing every card at once.
- **Save and Form 5 buttons on both widgets** (Today and Week, all sizes) — Save opens a chooser for Image or `.ics`; Form 5 opens your attached PDF directly or prompts you to attach one.
- **Exported image footer** — the app credit line is now smaller and more subtle, and whichever profile fields are switched on now appear as a line near the class/unit count.
- **Three app themes** — pick from a card in settings, no reinstall needed: GE (UP-maroon, follows system light/dark), NE (fixed dark, red-on-black), or Adaptive (matches your wallpaper, Android 12+). Applies everywhere: widgets, full schedule screen, and exported images.
- **Add a class manually** — for anything CRS doesn't cover (lab sessions, study groups, etc.), add one by hand with name, days, start/end time, and optional room/instructor/units.
- **Delete a class** — remove one outright with a confirmation first; it's saved to Saved Schedules first so it's recoverable.
- **New progress ring style** — GE and Adaptive get a smooth stroked arc; NE keeps the original 28-dot glyph ring.

## Widget header rework

Getting Save and Form 5 icons onto every widget size meant reworking tight header layouts. On the compact Today widget and both Week widget sizes, "VIEW FULL" became a small arrow icon instead of a text chip, and the TODAY/WEEKLY label now shrinks first under space pressure so the icons and clock never overlap. The full-size Today widget didn't need these changes — the new buttons just joined the existing row.

## Fixed

- **Countdown no longer goes negative** — the widget now catches the exact moment a class ends or starts and refreshes automatically, instead of relying on a 15-minute check-in.
- **Widgets recover properly after a phone restart** — the refresh alarms are restored automatically, so placed widgets don't go quiet.
- **Class list no longer gets cropped or dimmed near the bottom** of the Today widget on some sizes.
- **Weekly grid always shows your full week** — instead of hiding extra slots behind a "+N more" row, it shrinks everything slightly so the whole week is visible.
- **Fixed occasional squished rows** in the Today widget's class list when the list recycled rows.
- **Exported schedule images now match your chosen theme** (colors and font) instead of always coming out in the old dark/mono look.
