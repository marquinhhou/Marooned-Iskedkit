# Marooned IskedKit

An Android home screen widget set for your UP CRS class schedule.
(Formerly CRS Scheduler -- same app, same data, new name.)

**Author:** [marquinhhou](https://github.com/marquinhhou)

**Credits:** Marc Dizon, BS Statistics

This is an independent, unofficial student project. It is **not affiliated
with, endorsed by, or connected to** the University of the Philippines
Diliman, the UP School of Statistics, the UP Computerized Registration
System (CRS), or Nothing Technology Limited.

**NOTICE:** This README (except for this notice) and the comments inside 
of the program are written and organized with the help of AI, with the main 
purpose of helping the repo audiences understand the project easier and explain
it better than I could personally do myself. Thank you!

---

## App theme

Switchable any time from the config screen ("APP THEME" card), no reinstall
needed:

- **Adaptive** (default) -- matches the device's own Material You wallpaper
  colors. Needs Android 12+; the chip is disabled on older versions, falling
  back to Nothing.
- **Nothing** -- fixed dark, red-on-black, Nothing-OS-inspired look. Doesn't
  change with the system setting. (Formerly called "NE".)
- **Custom** -- build your own theme instead of picking a preset:
  - **Color** mode picks a solid background from a hex input, with a live
    swatch preview.
  - **Photo** mode picks a photo from your gallery (copied into the app's own
    storage, so it never depends on that photo staying where you found it),
    with opacity and blur sliders and an accent swatch drawn from the
    photo's own colors.
  Every color -- background, text, icons, chips, dialogs -- is derived from
  your choice, independently of Adaptive or Nothing.

Whichever is picked applies to all three widgets, the full schedule screen, and
the exported schedule image alike. (The original "GE" UP-maroon theme was
retired in v3.0.0; anyone who had it selected is migrated to Adaptive, or
Nothing on pre-Android-12 devices, automatically.)

## What's in the box

Three separate home-screen widgets. The Today and This Week pair are sized
the same so your launcher can stack them into a single "smart stack" card
(drag one on top of the other on most launchers that support widget
stacking); Notes stands on its own since it isn't part of that stack:

1. **Today** -- a hero card for your current/next class (with a countdown
   ring), plus a list of the rest of today's classes.
2. **This Week** -- a compact summary (class count, units, next upcoming
   class, a week-at-a-glance dot row) that opens the full schedule when
   tapped.
3. **Notes** -- subject-specific notes, deadlines, and to-dos, plus a
   Miscellaneous spot for anything not tied to a class. See below.

The **entire week schedule** lives as a real in-app screen
(`WeekScheduleActivity`), not crammed into a widget -- it's fully scrollable
and every class is tappable, unlike a RemoteViews-hosted list.

Tapping any class (in either widget, or in the full-schedule screen) offers to
open your phone's maps app for that room, if a room is set.

## Features

- **University affiliation** -- the first-run wizard (and Settings ->
  University, any time after) asks whether you're a UP student. Say yes and
  you pick your campus (all nine constituent universities are covered);
  say no and you just give your school's name. Switching later keeps your
  existing data and re-gates the import tooling below to match.
- **CRS HTML parsing (UP Diliman only)** -- paste the saved page source (or
  pick a saved `.html` file) of your CRS Registration ("My Enlisted
  Classes") or Preenlistment ("My Desired Classes") page in the config
  screen; the app finds the right table and extracts code, name, credits,
  days, times, type, room, and instructor. Either the All or Enlisted
  schedule tab works -- both are present in a saved page regardless of
  which was active. Column positions are detected from the table's own
  header labels rather than assumed, and rowspan-grouped rows (CRS splits a
  multi-component course, e.g. lecture + discussion, across several rows
  sharing one Rank/Status) are resolved to their full logical columns before
  reading, so those rows import correctly instead of silently misaligning.
  On Preenlistment, only rows actually marked Enlisted are imported --
  merely Desired or With-Conflict ranks are skipped. This is a Java/Jsoup
  port of the original web widget's parser, so both stay in sync on what
  counts as a valid row. Every other UP campus or school skips straight to
  manual entry, with instructions pointing at their own portal (SAIS on
  other UP campuses) -- a portal screenshot/PDF can still be attached as a
  reference.
- **.ics import** -- the same "choose a file" picker also accepts a
  `.ics` calendar file (one exported from here previously, or from another
  calendar/university system) as an alternate way to load a schedule,
  no HTML page needed. Since a calendar file has no concept of academic
  credits, imported classes come in at 0 units, flagged as excluded from
  the unit total rather than silently faking a number -- correct them
  under Edit Class Info if you want them counted.
- **Edit Class Info** -- expand the dropdown in the config screen to see
  every loaded class, tap one, and manually set/correct its days,
  start/end time, room, instructor, or unit count at any time (not just for
  TBA rows or blank fields). Also where classes get added (for anything CRS
  doesn't know about, like a standalone lab session) or removed, each with
  its own confirmation. From here you can also attach a PDF/Word/text/image
  syllabus file to any class -- it's kept across re-imports (keyed by class
  code) and openable straight from that class's row on the Today widget via
  a small document icon.
- **Semester dates** -- optionally set when the schedule actually starts
  and ends. Outside that range the widgets and full schedule show a
  "not in session" state instead of treating every day as a normal school
  day; once the end date passes, the schedule is auto-archived to Saved
  Schedules and cleared, same as a manual Clear would do.
- **Saved Schedules** -- every schedule you replace (by loading a new one,
  clearing, or letting a semester end) is auto-archived with a
  "date · N classes · X.X units" label. Browse, reactivate, or delete
  past schedules from the config screen at any time.
- **Class reminders** -- optionally get a local notification 5/10/15/30
  minutes before each class starts. Alarms are rescheduled automatically
  after a reboot, and the permission prompts (notifications, exact alarms)
  only ever show up once you actually turn reminders on.
- **Widget preview & tomorrow toggles** -- before a semester starts, tapping
  the widget header lets you preview what it'll look like once classes
  begin. The Today widget also has a TMRW chip to peek at tomorrow's
  classes instead of today's.
- **Export your schedule** -- from the full schedule screen, save it as an
  image to your gallery, or export it as a standard `.ics` calendar file to
  import into Google Calendar, Outlook, or any other calendar app. The image
  matches whichever app theme is currently active.
- **Profile Card** -- tap the circular avatar (top-right) to open your
  profile: name, school line (from your university affiliation), mail,
  phone, student no., course, year, social links, website, and
  dorm/address, plus a photo or initials disc. Everything's editable inline
  with a live themed preview, and **Save as Image** exports it as a PNG to
  your gallery -- handy as a shareable digital ID card. Nothing shows up
  anywhere else in the app; it only appears if you open the card yourself.
- **Form 5** -- optionally attach a copy of your Form 5 (official study
  load) PDF for quick access from the config screen. Stored as a reference
  to the file you picked, never copied or uploaded anywhere.
- **Maps hand-off** -- tapping a class with a room set asks "Open Maps for
  X?" and, if you say yes, launches a `geo:` search intent so whichever maps
  app you have installed can handle it. An optional "Campus / school name"
  field (config screen) gets appended to the search to help disambiguate
  (e.g. "SS 301 UP Diliman" instead of just "SS 301"). A switch in the same
  Maps section turns the whole prompt off, everywhere it appears -- on by
  default. A second switch controls, separately, whether importing a CRS
  HTML file is allowed to auto-fill that campus field the first time --
  also on by default, though it's only actionable while Maps itself is on
  (with Maps off, the campus hint has nothing to feed into).
- **Notes widget** -- its own home-screen widget for notes tied to a
  specific subject (picked from whatever's currently loaded, whether that
  came in via CRS HTML or `.ics`) or filed under a standing Miscellaneous
  spot for anything not tied to a class. Each note can optionally carry a
  deadline -- a date, and optionally an exact time on top of that -- shown
  as a color-coded "due in Nd" / "due today" / "overdue" badge, and can be
  flagged Urgent independent of any deadline. Doubles as a to-do: tap the
  dot on a row to mark it done right from the widget, no need to open
  anything. Add/edit from the widget's "+ NOTE" button or by tapping an
  existing row. A LIST/GROUPED toggle in the header switches between a
  flat list and a collapsible by-subject view. Completed notes can be
  archived from their row and reviewed later from a dedicated Archived
  Notes screen (restore or delete permanently from there). A copy icon in
  the header (and on the edit screen, and on each archived note) copies
  notes to the clipboard as plain text. Once a note has a deadline, its
  editor offers an optional reminder (1/3 hours or 1/3 days ahead, off by
  default) -- set independently per note, same permission prompts as
  class reminders. A saved note can also carry an attachment of any file
  type, openable straight from its widget row via the same document icon
  used for class syllabi.
- **First-run Terms of Use** -- shown once before the widget can be added;
  covers where the data comes from (CRS page or .ics import), that
  room/instructor/unit edits aren't verified by the app, that class and
  deadline reminders are scheduled entirely on-device, that everything
  stays on-device, and that there's no warranty.
- **Everything stays on-device.** All schedule data and settings -- including
  profile fields, notes, and the Form 5 reference -- are stored in local
  `SharedPreferences` only. Nothing is ever uploaded anywhere -- there is no
  network permission in this app at all. Exporting a schedule (image or
  `.ics`) just writes a file locally for you to share however you choose.

## Project structure

```
app/src/main/java/dev/marquinhhou/crsscheduler/
  model/     ClassSession.java          -- the parsed-class data model
             ScheduleSnapshot.java      -- an archived schedule + its auto-generated label
             Note.java                  -- a subject (or Miscellaneous) note/deadline/to-do
  data/      ScheduleParser.java        -- Jsoup-based CRS table parser
             IcsImporter.java           -- parses a .ics file back into ClassSessions
             ScheduleStore.java         -- SharedPreferences persistence (active schedule)
             ScheduleHistoryStore.java  -- SharedPreferences persistence (archived schedules)
             SemesterArchiver.java      -- auto-archives the schedule once semesterEnd passes
             SettingsStore.java         -- campus hint, semester dates, reminders, theme family, terms-accepted flag
             NotesStore.java            -- SharedPreferences persistence (notes)
  reminders/ ClassReminderScheduler.java-- schedules/cancels the per-class AlarmManager alarms
             ClassReminderReceiver.java -- fires the actual notification when an alarm goes off
             DeadlineReminderScheduler.java-- same, one-shot per note with a deadline
             DeadlineReminderReceiver.java -- fires the actual deadline notification
             NotificationHelper.java    -- notification channels + builders
             BootReceiver.java          -- re-schedules reminders after a device reboot
             TimeChangeReceiver.java    -- re-schedules reminders after the clock/timezone changes
  widget/    TodayWidgetProvider.java   -- tier 1 widget
             WeekWidgetProvider.java    -- tier 2 widget (summary + entry point)
             NotesWidgetProvider.java   -- tier 3 widget (subject notes, deadlines, to-dos)
             WidgetRenderer.java        -- shared RemoteViews builder for all three; resolves the
                                           active theme family's layouts/drawables/colors on every build
             WidgetRefreshScheduler.java-- battery-friendly 15-min refresh alarm
             RingBitmapFactory.java     -- draws the countdown ring -- Nothing's dot glyph or the
                                           Adaptive/Custom stroked arc
             TodayClassesRemoteViewsService.java -- ListView adapter for the Today widget
             NotesRemoteViewsService.java -- ListView adapter for the Notes widget
  ui/        Theming.java               -- resolves ThemeFamily into concrete resource ids/colors
             ConfigureActivity.java     -- import/parse/save, Edit Class Info, Terms gate, theme picker
             WeekScheduleActivity.java  -- the full in-app schedule screen
             ScheduleHistoryActivity.java -- browse/reactivate/delete Saved Schedules
             IcsExporter.java           -- builds a .ics file from the current schedule
             ScheduleImageExporter.java -- renders the schedule to a bitmap and saves it to the gallery
             WidgetActionActivity.java  -- dialog-themed "open maps?" popup
             NoteEditActivity.java      -- add/edit screen for a single note (subject picker,
                                           deadline date + time, urgent toggle, mark-as-done,
                                           copy-to-clipboard, delete)
             ArchivedNotesActivity.java -- browse/restore/permanently-delete archived notes
```

Every themed layout/drawable exists as resource variants (a `_ge`/`_ne`/
`_adaptive` suffix) rather than being switched via `?attr/`, since
RemoteViews (the widgets) can't apply a runtime Activity theme --
`Theming.pick()` is the one place that decides which variant to use. The
`_ge` set is dead code kept for reference only (GE was retired as a
selectable theme in v3.0.0); Custom reuses the `_adaptive` layouts as a
positional skeleton and recolors them at runtime instead of shipping a
fourth resource set.

## Setting it up in Android Studio

1. Open the project folder in Android Studio and let it sync (it'll fetch
   the Gradle wrapper jar itself on first sync if it isn't already cached).
2. Build & run once to install the app -- there's no launcher icon by
   design (it's a widget-only app), so you won't see it in the app drawer.
   That's expected.
3. Long-press your home screen -> Widgets -> **Marooned IskedKit** -> drag the
   **Today** widget onto your home screen. This triggers the required Terms
   of Use, then the four-step setup wizard (University -> Import ->
   Review -> Profile & Extras).
4. Optionally add the **This Week** widget too, and drag it onto the Today
   widget if your launcher supports stacking widgets together.
5. Optionally add the **Notes** widget for subject notes, deadlines, and
   to-dos -- it works on its own, no schedule required (just use the
   Miscellaneous spot).

### Getting your CRS schedule into the app

This path only appears if you set your affiliation to UP Diliman during
setup; everyone else adds classes manually (or imports a `.ics` file, which
works the same for everyone).

1. Open your CRS Registration or Preenlistment page (the one showing your
   Enlisted or Desired Classes table) in a desktop browser. Either the All
   or Enlisted schedule tab is fine -- both get saved either way.
2. Save it (`Ctrl+S` -> "Webpage, HTML only") or view source (`Ctrl+U`,
   select all, copy).
3. In the widget's setup screen, either pick the saved `.html` file or paste
   the copied source into the box, then tap **Parse & Save**.
4. Whenever your enlistment changes, just repeat this -- the app doesn't
   talk to CRS directly and won't know about changes on its own.

Already have a `.ics` calendar file instead -- e.g. one exported from this
app previously, or from another calendar/university system? Pick it with the
same file button; it's detected and imported automatically, no HTML needed.
Since a calendar file doesn't carry a credit count, imported classes come in
at 0 units (excluded from the unit total) -- fix that under Edit Class Info
if you want them counted.

## Known limitations / design notes

- **Refresh granularity:** the class list/ring redraw on a 15-minute cadence
  generally, but the moment a class actually starts or ends is caught
  separately by its own precise, Doze-aware alarm -- so the countdown never
  drifts into negative numbers or shows a class that's already over.
- **No online room lookup.** An earlier draft of this feature tried to
  web-search for TBA rooms automatically, but that would've required every
  user to register their own Google Custom Search API key just to use the
  app -- too much friction for what it's worth. Manually editing a class's
  room via **Edit Class Info** covers the same need without the setup
  burden.
- **Maps accuracy isn't guaranteed,** especially for manually-typed rooms --
  it's a plain text search handed to whatever maps app you have, not a
  verified campus room directory.
- **Adaptive theme needs Android 12+** (`android.R.color.system_accent1_*`
  / `system_neutral*_*` didn't exist before then). The chip is disabled on
  older devices; picking it programmatically falls back to Nothing.
- **Package/App ID:** `dev.marquinhhou.crsscheduler`. Change this in
  `app/build.gradle` (`namespace` / `applicationId`) if you want to publish
  under your own identifier.
- **Launcher icon:** regenerate it any time with `python3 tools/generate_icon.py`
  (requires Pillow: `pip install Pillow`) -- it writes fresh adaptive-icon
  layers (background/foreground/monochrome) and legacy fallbacks to every
  `mipmap-*` density directly. Edit the colors/glyph at the top of that
  script rather than hand-editing PNGs. The launcher icon itself doesn't
  change with the in-app theme picker.

## License

MIT -- see [LICENSE](LICENSE). Third-party assets keep their own original
open-source licenses:

- Bundled fonts (JetBrains Mono, DotGothic16), used by the Nothing theme --
  SIL OFL 1.1, see [licenses/fonts](licenses/fonts).
- A handful of toolbar/dialog icon drawables redrawn from Google Material
  Icons -- Apache License 2.0, see [licenses/icons](licenses/icons).
