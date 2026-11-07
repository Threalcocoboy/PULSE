# Architecture

The 0.2.0 integration foundation: `integration/garmin` contains the official bounded HTTPS client, pure JSON-to-domain mapping, token lifecycle, Keystore-encrypted runtime credentials and separate repository/cache. No credentials enter models, resources, settings, saved state or exports. `StudioGrid` has one pointer handler per widget; the down position selects move/resize before activation, with shared preview/commit, hysteresis and edge scrolling. `StudioGestureRules` is pure and tested. `PulseTokens` centralizes typography/geometry. The 0.3.0 audit adds fresh gesture baselines, a transactional widget editor, explicit achievement identities, combined filters and a source/period-specific award ledger. Current evidence: `POST_AI_AUDIT_REPORT.md`.

Single Android Gradle module, separated into packages. Manual constructor injection avoids a DI framework for this small graph.

- `core`: Android-independent model, calendar aggregation, sleep interval union, fixed medal rules, occupancy grid, versioned layout codec and bounded undo/redo.
- `data`: Room daily records, recalculable medal snapshots and confirmed achievement progress; Preferences DataStore; JSON/image export and native share sheet.
- `integration`: `HealthDataRepository`, deterministic in-memory demo, read-only Health Connect and official Garmin Personal API implementations.
- `PulseViewModel`: owns StateFlows and SavedStateHandle. Collects settings, switches provider without fallback, cancels superseded synchronization, persists committed layouts and restores undo/redo on recreation.
- `ui`: Compose design tokens, Canvas dots/charts, Home/Studio, History/calendar, collection/medals, onboarding and settings.
- `MainActivity`: edge-to-edge setup, Health Connect permission contract, lifecycle refresh, three-tab Navigation Compose graph and secondary routes.

Composables do not query Room or Health Connect. Repository work uses IO/Default dispatchers; Room suspending queries and DataStore edits handle their own IO. A source tag on every snapshot prevents a provider switch from showing the other dataset for a frame.

## Adaptive Flow

Each widget has a stable ID, metric, shape, valid footprint, logical column/row and display configuration. Occupancy is explicit. A move/resize reserves the selected widget at the requested clamped location; the remaining widgets scan row-major for the first valid gap in stable input order. This deterministic policy may move several widgets when a large card expands. It never deletes a displaced widget. Maximum 100 widgets; 4 columns; bounded requested rows protect against corrupt input.

Drag reads current callbacks/layout at activation and freezes one immutable baseline for its single pointer gesture, provisional layout and destination outline. This prevents cached handlers from resurrecting a reset layout or an earlier shape configuration. Commit uses that same state. Cancel discards preview. Selected-corner resize chooses a valid footprint with hysteresis; circles/squares use the dominant resize axis. Siblings animate with reduced-motion support. Move and resize auto-scroll at viewport edges. Grid geometry derives from available dp width; rendered circles keep a square size within their footprint.

Committed layouts persist in DataStore. Undo/redo uses up to 50 snapshots and SavedStateHandle; restart restores the committed layout, while Android saved-state recreation can also restore history. Invalid persisted settings/layouts safely fall back to defaults. Empty layouts are valid.

## Database

Room schemas v1, v2 and v3 are exported in `app/schemas`. The explicit 1→2 migration only creates `garmin_daily`. The additive 2→3 migration creates `achievement_progress`, keyed by source scope and definition/period instance. Upserts retain best progress, confirmed unlocks and known dates; the ViewModel evaluates records off the UI thread and updates month boundaries without requiring a restart. Existing HC `(date, zone)` aggregate keys and `(month, zone)` medal snapshots remain intact. Garmin stores curated daily JSON separately using provider calendar dates, and clears its cache when a replacement key is configured. Demo never writes health tables. No destructive migration fallback; backup/device transfer disabled.
