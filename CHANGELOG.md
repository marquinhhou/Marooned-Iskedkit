# Changelog: CRS Scheduler v2.2.1

## Changed

- **Campus auto-fill can be turned off separately from Maps.** Importing a CRS HTML file used to always auto-fill the campus hint the first time (if it was empty) from whatever campus it could detect in the page. A new switch in Settings → Maps Search Context, right under the campus field, controls just that auto-fill; turning it off means the campus field is only ever set by typing into it yourself. On by default, and only actionable while the main Maps switch is on.

# Changelog: CRS Scheduler v2.2.0

## Fixed

- **Preenlistment schedule pages now import correctly.** UP CRS spreads a multi-component course (e.g. a lecture + discussion pair) across several table rows using `rowspan`, so the Rank/Status columns only appear once per group. The parser was reading columns by a flat header-index and silently misaligned on every row after the first in a group — on a real Preenlistment page this dropped 6 of 7 classes. The parser now rebuilds each table into its full logical grid (accounting for rowspan/colspan) before reading columns, so every row lines up correctly regardless of which rows carry the shared cells.
- **Preenlistment's "My Desired Classes" table is now found reliably.** CRS sometimes inserts an unrelated "Notes" aside between that section's heading and its table; the previous heading search reset on any non-matching heading in between and missed it. Section detection is now sticky (latches on a match, ignores anything unrelated in between) and recognizes both "…Enlisted…" and "…Desired Classes…" headings.
- **Only actually-enlisted rows are imported from Preenlistment.** A ranked class can be "Desired" or "With Conflict" rather than secured; those are now skipped so the imported schedule reflects classes you're actually in, not just ranked for.

## Changed

- **Registration and Preenlistment are both explicitly supported now** — the in-app import instructions, and the "couldn't find a table" error message, mention both page types (and either the All or Enlisted schedule tab works, whichever CRS happened to save).
- **Maps prompts can be turned off.** A new switch in Settings → Maps Search Context disables the "Open Maps" prompt everywhere it appears (widget tap, full week view, campus-hint field dims along with it). On by default, so no change unless you turn it off.

## Notes widget

- **Each note card is one line shorter.** The relative due-badge ("DUE IN 7D") and the absolute date ("Aug 15, 8:30 AM") used to sit on their own stacked lines; they're now one line, so cards take noticeably less vertical space.
- **Group headers ("MISCELLANEOUS", etc.) no longer look like note cards.** They previously used the same boxed pill as a note row, so sections and notes blended together at a glance. Headers are now a plain label with a thin rule underneath, clearly separate from the cards below them.

# Changelog: CRS Scheduler v2.1.1

## Fixed

- **Importing a CRS schedule page no longer comes back empty.** The "My Enlisted Classes" table parser was reading the wrong columns — thrown off by a Status column CRS adds to that table — so every class row was silently dropped during import. All enlisted classes now import correctly.

## Improved

- **Schedule import is more resilient to CRS layout changes** — column positions are now detected from the table's own header labels instead of assumed fixed positions, so a future column reorder won't silently break import again.
- **Cross-listed classes (two class codes sharing one CRS row) now import as two separate classes** instead of getting merged into one garbled entry.

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
