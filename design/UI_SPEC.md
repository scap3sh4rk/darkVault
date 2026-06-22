# darkVault — UI/UX Specification

**Version:** Agent 5 — UI/UX Design Pass  
**Date:** 2026-06-22  
**Aesthetic reference:** Background #0A0A0F · Surface #12121A · Accent #00D4FF (Electric Cyan) · Secondary #1E1E2E · Text primary #E8E8F0 · Text secondary #8888AA · Error #FF4D6D  
**Motion rule:** scale+fade M3 tokens, 200ms max, no bounce  
**Signature element:** 1px #00D4FF at 60% opacity horizontal divider (`CyanPrimary.copy(alpha=0.6f), thickness=1.dp`) used as section separator throughout

---

## TASK 1 — UnlockScreen: show signed-in email

### Context

The current UnlockScreen (full vault-lock mode, `authState != AppLocked`) shows VaultLogo, "Unlock Vault" heading, then the password field. There is no indication of which account is associated with the vault. Task: add a non-interactive email chip above the password field.

### Layout (full vault-lock mode)

```
┌──────────────────────────────────────────┐
│              [VaultLogo]                 │  ← existing, unchanged
│           "darkVault" monospace          │
│        "AES-256 ENCRYPTED" label         │
│                                          │
│           Unlock Vault                   │  ← headlineSmall, existing
│                                          │
│  ┌─────────────────────────────────────┐ │  ← NEW: email chip
│  │  [person icon]  user@gmail.com   ▸ │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  ┌─────────────────────────────────────┐ │
│  │  Master password            [eye]   │ │  ← existing VaultTextField
│  └─────────────────────────────────────┘ │
│                                          │
│  [ UNLOCK ]                              │  ← existing CyberButton
│                                          │
│  Forgot password? Use recovery key       │  ← existing TextButton
│                                          │
└──────────────────────────────────────────┘
```

### Email Chip Spec

- **Component:** Custom `SuggestionChip`-inspired composable (do NOT use M3 SuggestionChip — its internal padding conflicts with our dark surface). Use a `Row` inside a `Surface` or `Card`.
- **Container:** `Surface(shape = RoundedCornerShape(8.dp), color = VaultSurfaceVariant (#12121A))` with `border(1.dp, VaultOutline, RoundedCornerShape(8.dp))`.
- **Height:** 40dp.
- **Padding:** horizontal 12dp, vertical 0dp (vertically centered content).
- **Leading icon:** `Icons.Outlined.AccountCircle`, size 18dp, tint `#8888AA` (secondary text color / `MaterialTheme.colorScheme.onSurfaceVariant`).
- **Spacer:** 8dp between icon and text.
- **Text:** email string, `MaterialTheme.typography.bodySmall`, color `#8888AA` (`onSurfaceVariant`). `maxLines = 1`, `overflow = TextOverflow.Ellipsis`. Email truncates at chip boundary.
- **Width:** `fillMaxWidth()` — chip spans full horizontal extent of the content column (same width as VaultTextField below it).
- **Not clickable.** No ripple. `Modifier.semantics { disabled() }`.
- **Spacing above chip:** 24dp below "Unlock Vault" heading.
- **Spacing below chip:** 16dp above VaultTextField.
- **Data source:** `GoogleSignIn.getLastSignedInAccount(context)?.email` read once with `remember`. If `null`, the chip is not rendered at all (no placeholder, no crash).

### State Variants

| State | Appearance |
|---|---|
| Email present | Chip shown as specced |
| Email null / blank | Chip hidden entirely; spacing collapses as if chip never existed (use `AnimatedVisibility` or conditional composition) |
| Long email (>40 chars) | Text truncated with ellipsis at right edge of chip |

### AppLocked mode

The AppLocked mode UI (biometric-first) does NOT show the email chip. That mode already implies a known user. No change needed to the AppLocked branch.

### Transitions

No transition animation needed on the email chip itself; it renders synchronously on composition.

### Copy

No label prefix. Just the raw email address. Let it speak for itself.

---

## TASK 2 — Duplicate File Conflict Dialog

### Context

The current duplicate handling (`findUniqueOriginalName` in `UploadForegroundService`) auto-renames without asking. Task: expose the choice to the user before each upload of a conflicting filename.

### Trigger Condition

During the upload pipeline in `UploadForegroundService.processQueue()`, before encrypting: detect whether an `originalName` already exists in the target folder (the existing `client.findUniqueOriginalName` logic already does this check — repurpose it to detect rather than auto-rename). If a conflict is found, pause that job and emit a new `UploadEvent.ConflictDetected(jobId, originalName, suggestedName, conflictIndex, totalConflicts)` event. HomeViewModel observes and surfaces a `ConflictPending` state to HomeScreen.

### Dialog Layout

```
┌──────────────────────────────────────────────┐
│  "photo.jpg" already exists                  │  ← titleLarge
│  ─────────────────────────────────────────   │  ← 1px cyan divider
│  File 2 of 5                                  │  ← progress indicator (see below)
│                                              │
│  ┌─────────────────────────────────────────┐ │
│  │  Rename to "photo (2).jpg"              │ │  ← outlined button
│  └─────────────────────────────────────────┘ │
│                                              │
│  ┌─────────────────────────────────────────┐ │
│  │  Replace existing file                  │ │  ← outlined button (error color border)
│  └─────────────────────────────────────────┘ │
│                                              │
│  ┌─────────────────────────────────────────┐ │
│  │  Skip this file                         │ │  ← outlined button (subdued)
│  └─────────────────────────────────────────┘ │
│                                              │
└──────────────────────────────────────────────┘
```

### Dialog Component Spec

- **Component:** M3 `AlertDialog`, `containerColor = VaultSurfaceVariant (#12121A)`.
- **Title:** `Text("\"${conflictingName}\" already exists", style = MaterialTheme.typography.titleMedium, color = onSurface)`. Full filename shown. If filename >40 chars, use `maxLines=1, overflow=Ellipsis`.
- **Divider:** 1px `HorizontalDivider(color = CyanPrimary.copy(alpha=0.6f), thickness=1.dp)` immediately below title, before button group.
- **Batch progress indicator** (shown only when total conflicts > 1):
  - `Text("File $conflictIndex of $totalConflicts", style = labelSmall, color = onSurfaceVariant)` — rendered in the `text` slot above the buttons, with 8dp bottom padding.
- **Three option buttons** — rendered in the `text` slot as a stacked Column of `OutlinedButton` components, each `fillMaxWidth()`, 8dp vertical spacing between them:
  1. **Rename** — `OutlinedButton(border = BorderStroke(1.dp, CyanPrimary))`. Label: `"Rename to \"${suggestedName}\""`. On click: resolve conflict as Rename, proceed to next conflict or continue upload.
  2. **Replace** — `OutlinedButton(border = BorderStroke(1.dp, VaultError (#FF4D6D)))`. Label: `"Replace existing file"`. On click: trash the existing Drive file by `originalName`, then upload new file with the same name.
  3. **Skip** — `OutlinedButton(border = BorderStroke(1.dp, VaultOutline))`. Label: `"Skip this file"`. On click: emit `UploadEvent.Skipped(jobId, originalName)`, move to next file.
- **No confirm/dismiss buttons** in the standard AlertDialog button slots — the three options ARE the actions.
- **Dismiss on back** = same as "Skip".
- **Dismiss on scrim tap** = same as "Skip".

### Batch Conflict Sequencing

When multiple files in one upload batch have conflicts, show dialogs sequentially:
- Each dialog resolves one file before the next dialog appears.
- The `File X of Y` counter increments across dialogs in this batch group.
- If the user skips all conflicts, upload continues for non-conflicting files in the queue.

### Edge Cases

- Single file conflict: no batch progress indicator shown.
- Suggested rename suffix: `file (2).ext`, `file (3).ext` etc. (matching existing `findUniqueOriginalName` logic).
- Replace flow: only trash the old file if the new upload completes successfully; do not trash on encryption failure.
- If Drive is offline when conflict dialog appears: show the dialog anyway; the Replace action will fail gracefully with existing error handling.

---

## TASK 3 — Batch Download UI

### Context

Multi-select mode exists for delete only. Task: add Download alongside Delete in the multi-select action bar, add Select All to the top bar, and ensure proper feedback.

### Top Bar in Multi-Select Mode (updated layout)

```
[X close]  "N selected"  [select_all] [download_N] [delete_sweep]
```

- **Select All button:** `Icons.Outlined.SelectAll`, tint `CyanPrimary`. Already exists in code — keep it.
- **Download button with count badge:**
  - Icon: `Icons.Outlined.Download`, tint `CyanPrimary`.
  - Badge: `BadgedBox` wrapping the IconButton; `Badge { Text("$N") }` where N = count of selected non-folder items (folders are not downloadable). Badge color: `CyanPrimary`, text color: `#00363F` (same as CyberButton foreground).
  - If 0 non-folder items selected: button is shown but disabled (`alpha = 0.38f`). Tapping shows no action (no crash).
  - Tooltip (long-press): `"Download $N file(s)"`.
- **Delete button:** existing `Icons.Outlined.DeleteSweep`, unchanged.
- **Close button:** existing `Icons.Outlined.Close`, position leftmost.

### Selection Persistence

- Selection set (`selectedIds: MutableStateFlow<Set<String>>`) in `HomeViewModel` persists within the same ViewModel instance (already NavGraph-scoped).
- When the user navigates into a sub-folder while in selection mode, the selection SET is preserved (items from the parent folder remain selected even if not visible). Selected items count in the top bar reflects the total across all folders.
- Navigating back to a folder shows previously-selected items still highlighted.
- Clearing selection via the X button clears the entire set regardless of current folder.

### Download Progress Feedback

- Progress shown via existing `UploadProgressCard` repurposed for download. The card uses `OperationState.InProgress` with `stage = "Downloading $done of $total files…"` and `progress = done.toFloat() / total`.
- No per-file notification for batch downloads (notification exists for uploads only; batch downloads run in ViewModel scope and stay in-app).

### Completion Feedback

- After all files saved: `SnackbarHostState.showSnackbar("$N file(s) saved to Downloads/darkVault-loc/")`.
- If some files fail: `"$done of $total file(s) saved — $failed failed"`.
- Snackbar duration: `SnackbarDuration.Long`.
- No action button on snackbar.

### Edge Cases

- Folder items selected: the download button count badge shows only non-folder items; folders are silently excluded from the batch download (no error for them).
- All selected items are folders: download button is disabled.
- Zero items selected: multi-select bar should not show; `selectedIds.isEmpty()` returns to normal mode.

---

## TASK 4 — Image/Video Thumbnails in File List

### Context

`VaultFileCard` currently shows a 40×40dp file-type icon inside a rounded box. Task: replace this with an actual thumbnail for image and video files, decrypted in-memory, never written to disk.

### Updated VaultFileCard Left Side

```
List mode card:
┌───────────────────────────────────────────────┐
│ [72×72 thumbnail or icon] │ filename.jpg      │
│     [rounded 8dp]         │ 1.2 MB · Today    │
│                           │          [↓] [🗑] │
└───────────────────────────────────────────────┘
```

### Thumbnail Container

- **Size:** 72×72dp (increased from current 40×40dp icon box).
- **Shape:** `RoundedCornerShape(8.dp)`.
- **Clip:** `Modifier.clip(RoundedCornerShape(8.dp))`.
- **Border:** none (no border on thumbnail, unlike the current icon box).

### Thumbnail Loading

- Use Coil's `AsyncImage` with a custom `ImageRequest` that:
  1. Decrypts the file from Drive in a background coroutine via `HomeViewModel.decryptToMemory(file, password, account)`.
  2. Decodes the resulting `ByteArray` to `Bitmap` in-memory.
  3. Returns the Bitmap as the image source.
- The custom fetcher receives the `VaultFile` object as the data key. Use `file.id` as the Coil cache key (`MemoryCacheKey(file.id)`).
- Memory cache: `MemoryCache` only — never disk cache (`DiskCache` = null for thumbnail requests).
- Thumbnail load triggered by `AsyncImage` entering the Compose viewport via `LazyColumn` key-based recomposition (Coil handles this natively via `rememberAsyncImagePainter`).

### Loading State (shimmer placeholder)

- While thumbnail is loading: show a shimmer box of the same 72×72dp shape.
- Shimmer implementation: `InfiniteTransition` animating `alpha` from 0.2f to 0.5f on `VaultSurfaceVariant`. Alternatively, a simple pulsing `Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(shimmerBrush))`.
- Shimmer brush: `Brush.linearGradient(listOf(VaultSurfaceVariant, CyanPrimary.copy(alpha=0.08f), VaultSurfaceVariant))` with animated position.
- Duration: 1200ms per cycle, `RepeatMode.Restart`.

### Error/Fallback State

- Thumbnail load fails, file is not image/video, or thumbnails are disabled in Settings: show the existing 40×40dp file-type icon centered inside the 72×72dp container with background `VaultBackground`.
- The fallback icon remains at 40×40dp (Modifier.size(40.dp)) — the container scales up but the icon does not fill it.

### Video Files: Play Overlay

- For video files: after thumbnail loads, overlay a `play_circle` icon (M3 outlined) centered over the thumbnail.
- Play icon: `Icons.Outlined.PlayCircleOutline` (or `Icons.Outlined.PlayArrow` inside a circular shape), size 28dp, tint `Color.White.copy(alpha=0.9f)`.
- Semi-transparent scrim behind play icon: `Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.25f)))`.
- If thumbnail fails (fallback to icon): no play overlay; just the VideoFile icon.

### Settings Integration

- `imagePreviewEnabled` DataStore flag (already used for tap-to-preview dialogs) also gates thumbnail loading.
- If `imagePreviewEnabled = false`: ALL file thumbnails are suppressed (images and videos) — show file-type icon fallback.
- `videoPreviewEnabled` flag additionally gates video thumbnails specifically.
- The new "Show thumbnails" toggle in Settings (Task 10) maps to the same `imagePreviewEnabled` flag.

### Grid Mode (Tasks 7)

In grid layouts, thumbnails fill the card's image area:
- Grid 2-col: thumbnail 120×120dp, centered, `ContentScale.Crop`.
- Grid 3-col: thumbnail 80×80dp, centered, `ContentScale.Crop`.
- Fallback icon: centered, 36dp (2-col) / 28dp (3-col).

### Edge Cases

- Thumbnail request rate-limited by Drive 429: Coil retry is not appropriate here (not a URL fetch). Emit Coil `Error` state → show file-type icon fallback silently.
- File deleted from Drive since last sync: thumbnail fails → file-type icon fallback.
- Very large encrypted file (>50MB): do NOT attempt to decrypt-in-full for thumbnail. Cap thumbnail decrypt at 2MB (`downloadFile` with a byte-range header or simply bail with fallback if `file.size > 2_000_000L`).

---

## TASK 5 — Upload Cancel + Pause (Button + Notification)

### Context

The Cancel button in `UploadProgressCard` currently calls `homeViewModel.cancelAllUploads()` which sets `cancelledIds`. Pause does not exist. The notification has a Cancel action. Task: make Cancel actually work end-to-end, add Pause, add Resume.

### Service-Level State Machine

Three additional states beyond the existing running/cancelled pattern:

| State | Meaning |
|---|---|
| `RUNNING` | Actively uploading |
| `PAUSED` | Coroutine suspended at chunk boundary; resumable Drive URI retained in memory |
| `CANCELLED` | Stop current job, zero `encBytes`, delete partial Drive upload session |

#### Pause semantics

- Pause happens at a chunk boundary in `uploadChunked()`. Before emitting the next chunk, check `UploadState.pausedIds.contains(job.id)`.
- If paused: suspend the coroutine via `UploadState.resumeSignal[job.id].receive()` (a `Channel<Unit>`).
- The resumable Drive upload URI is retained in `UploadState.resumeUris: MutableMap<String, String>`.
- Memory of `encBytes` is retained (not zeroed) while paused. On resume, pick up from saved byte offset.
- If the service is killed while paused (process death): the resumable URI is lost. On restart, the job re-encrypts from scratch (Drive resumable sessions survive for 1 week; but re-encrypt rather than track sessions across restarts).

#### Cancel semantics

- Cancel signals `cancelledIds.add(job.id)` (existing).
- Additionally: if the job is currently uploading, call `client.deleteResumableSession(sessionUri)` (a Drive `DELETE` on the session URI) to abort the partial upload and avoid orphaned Drive resumable sessions.
- `encBytes` zeroed in `finally` block (already implemented — MEDIUM-001 fix).
- Emit `UploadEvent.Cancelled(jobId, originalName)`.

### New UploadState Fields

```
object UploadState {
    // existing
    val queue: ConcurrentLinkedQueue<UploadJob>
    val cancelledIds: CopyOnWriteArraySet<String>
    val active: MutableStateFlow<ActiveUpload?>
    val events: MutableSharedFlow<UploadEvent>
    val queueSize: MutableStateFlow<Int>

    // NEW
    val pausedIds: CopyOnWriteArraySet<String>
    val resumeSignals: ConcurrentHashMap<String, Channel<Unit>>
    val resumeUris: ConcurrentHashMap<String, String>   // jobId → Drive resumable session URI
    val totalInQueue: MutableStateFlow<Int>             // total jobs started this batch
    val completedInQueue: MutableStateFlow<Int>         // jobs finished this batch
    val pausedCount: MutableStateFlow<Int>              // jobs currently paused
}
```

### Notification Actions

**While uploading:**
```
[ Pause ]  [ Cancel ]
```

**While paused (at least one job paused):**
```
[ Resume ]  [ Cancel ]
```

**All uploads paused:**
- Notification title: `"darkVault Upload"`
- Notification text: `"$N upload(s) paused"`
- No progress bar while all paused (indeterminate=false, max=0).

**Multi-file progress:**
- Notification text: `"Uploading file $current of $total — $filename"` truncated at 40 chars.
- Subtext: `"$pct%"`.
- Progress: per-file byte progress mapped to overall queue progress: `((completed * 100) + currentFilePct) / total`.

#### Notification PendingIntent Actions

- **Pause:** `Intent(service, UploadForegroundService::class.java).apply { action = ACTION_PAUSE_ALL }` → adds all active job IDs to `pausedIds`.
- **Resume:** `Intent(service, UploadForegroundService::class.java).apply { action = ACTION_RESUME_ALL }` → removes from `pausedIds`, sends unit to all `resumeSignals`.
- **Cancel:** existing `ACTION_CANCEL_ALL` — no change.

### In-App UploadProgressCard (Updated)

Current `UploadProgressCard` has only a Cancel button. Updated layout:

```
┌──────────────────────────────────────────────┐
│  [spinner] Uploading…          72%           │
│  photo.jpg                  1.2 MB / 1.7 MB  │
│  ████████████████░░░░░░░░░░░░░               │ ← LinearProgressIndicator
│                              [Pause] [Cancel] │
└──────────────────────────────────────────────┘
```

**Paused state:**
```
┌──────────────────────────────────────────────┐
│  [pause icon]  Upload paused                 │
│  photo.jpg                  1.2 MB / 1.7 MB  │
│  ████████████████░░░░░░░░░░░░░               │ ← progress frozen
│                             [Resume] [Cancel] │
└──────────────────────────────────────────────┘
```

- **Pause button:** `TextButton`, label `"Pause"`, color `CyanPrimary`.
- **Resume button:** replaces Pause when `job.id in UploadState.pausedIds`. Label `"Resume"`, color `CyanPrimary`.
- **Cancel button:** `TextButton`, label `"Cancel"`, color `VaultError`.
- Button row: `horizontalArrangement = Arrangement.End`.
- The spinner (`CircularProgressIndicator`) changes to `Icons.Outlined.PauseCircle` (20dp, CyanPrimary) while paused.

### Multi-File Queue Header

When multiple files are queued, show a queue summary line above the progress bar in `UploadProgressCard`:

```
│  File 2 of 5                                 │  ← labelSmall, onSurfaceVariant
```

This is derived from `UploadState.completedInQueue.value + 1` and `UploadState.totalInQueue.value`.

### Edge Cases

- User pauses then opens a different app → service stays alive (foreground).
- User pauses then kills app (swipes away from recents) → foreground service is destroyed; on restart, the job re-queues from the beginning (existing queue persistence is in-memory only — not persisted across restarts).
- Pause during encryption phase (before Drive session starts): the encrypted bytes are already in memory. Do not re-encrypt on resume — just hold the `encBytes` allocation and skip to upload.

---

## TASK 6 — Folder Breadcrumb Back-Button Fix

### Context

`HomeScreen` already renders a breadcrumb `LazyRow` inside `folderStack.size > 1` condition. The `navigateUp()` / `navigateTo()` / system back are all wired. Known issue: system back button behavior when deep in sub-folders. Task: formal spec for correct behavior and overflow handling.

### Breadcrumb Row Spec

Position: below `TopAppBar`, rendered in `Column { TopAppBar(); BreadcrumbRow() }` inside the `topBar` slot.

```
darkVault  /  folder1  /  very-long-subfolder-name…
    ↑                            ↑
 clickable                  non-clickable, CyanPrimary
(navigate to)                (current location)
```

**Segments:**
- Each `FolderEntry` in `folderStack` = one clickable text segment.
- Last segment = current folder, non-clickable, color `CyanPrimary`, weight `FontWeight.SemiBold`.
- All other segments: color `#8888AA` (secondary), `TextButton` with `contentPadding = PaddingValues(horizontal=4.dp, vertical=0.dp)`.
- Separator: `Text(" / ", color = VaultOutline, style = labelSmall)` between each pair.
- The entire breadcrumb row is inside `LazyRow(horizontalArrangement = Arrangement.spacedBy(0.dp))` — already implemented.

**Overflow (more than 3 segments):**

When `folderStack.size > 3`:
- Show: `darkVault  /  …  /  subfolder2  /  deepfolder`
- The middle collapsed section shows `…` as a `TextButton`. Tapping `…` expands inline (replaces `…` with all intermediate segments, animated with `AnimatedContent`). The expanded state persists until the user taps away or navigates.
- State: `var breadcrumbExpanded by remember { mutableStateOf(false) }` local to the Composable.
- Always show: first segment (root) + last 2 segments. Middle is collapsed to `…`.

```
Collapsed (folderStack.size == 5):
  darkVault  /  …  /  folder3  /  deepfolder

Expanded (after tapping …):
  darkVault  /  folder1  /  folder2  /  folder3  /  deepfolder
```

**Visibility:**
- Breadcrumb row visible only when `folderStack.size > 1` (inside a sub-folder). Hidden at root.
- Row height: 28dp. `fillMaxWidth()`, background `VaultBackground`, `horizontalPadding = 16dp`.
- Row sits immediately below `TopAppBar`, no vertical padding between bar and row.

### Top Bar Back Arrow

- Shown only when `homeViewModel.canGoBack` is true (i.e., `folderStack.size > 1`).
- Tapping: calls `homeViewModel.navigateUp()` → pops exactly one level.
- At vault root (`folderStack.size == 1`): back arrow hidden entirely. No dead-end.
- System back button (Android back gesture/button): intercept with `BackHandler(enabled = homeViewModel.canGoBack) { homeViewModel.navigateUp() }` in `HomeScreen`. If `!canGoBack`, system back exits the app as normal.

### NavGraph Back Stack

- `navigateUp()` calls `_folderStack.value = _folderStack.value.dropLast(1)` then reloads folder (already implemented).
- No NavGraph destination changes for sub-folder navigation — all folder nav is state-based within `HomeScreen`. The NavGraph back stack does not grow with folder depth.

### Edge Cases

- Very long folder name in last segment: `overflow = TextOverflow.Ellipsis, maxLines = 1` on the current-folder text. Max width set by `fillMaxWidth()` of row minus preceding segments' widths.
- Single-character folder names: renders correctly; min padding still applied.
- Rapid back tapping: `navigateUp()` is idempotent — `folderStack.size <= 1` is a no-op guard.

---

## TASK 7 — Multiple View Layouts

### Context

HomeScreen currently uses a single `LazyColumn` list. Task: add List / Grid 2-col / Grid 3-col layouts, toggled from the top bar and persisted.

### Toggle Control (Top Bar)

Position: in the top bar actions row, immediately left of the Sort icon, right of Refresh.

```
[Search] [Sort] [Refresh] [Layout] [More⋮]
```

- **Icon:** single `IconButton` showing the icon of the CURRENT layout:
  - `LIST` → `Icons.Outlined.ViewList`
  - `GRID2` → `Icons.Outlined.GridView`
  - `GRID3` → `Icons.Outlined.GridOn`
  - Tint: `CyanPrimary`.
- On tap: show a compact `DropdownMenu` with three options:
  ```
  ○ List view                [ViewList icon]
  ○ Grid (2 columns)         [GridView icon]
  ○ Grid (3 columns)         [GridOn icon]
  ```
  Currently selected option shows `CyanPrimary` color on its text + icon.

### DataStore Persistence

- Key: `view_layout` (`stringPreferencesKey`)
- Values: `"LIST"` / `"GRID2"` / `"GRID3"`
- Default: `"LIST"`
- Add `viewLayout: Flow<String>` to `PreferencesManager`.
- `HomeViewModel` reads it as a `StateFlow<ViewLayout>` (enum: `LIST`, `GRID2`, `GRID3`).
- Changes take effect immediately (StateFlow → Compose recomposition).

### List Layout (unchanged)

Existing `VaultFileCard` / `VaultFolderCard` in `LazyColumn`. No change.

### Grid 2-Col Layout

```
LazyVerticalGrid(columns = GridCells.Fixed(2), ...)

┌──────────────────┬──────────────────┐
│  [thumbnail/icon]│  [thumbnail/icon]│  ← 120×120dp, ContentScale.Crop
│                  │                  │
│  filename.jpg    │  another.png     │  ← bodySmall, 2-line max, ellipsis
│  1.2 MB          │  800 KB          │  ← labelSmall, onSurfaceVariant
└──────────────────┴──────────────────┘
```

- **Card:** `RoundedCornerShape(12.dp)`, `containerColor = VaultSurfaceVariant`, border `1.dp VaultOutline`.
- **Thumbnail/icon area:** 120×120dp, `ContentScale.Crop` for thumbnails; file-type icon centered at 40dp on `VaultBackground` background for non-image/non-thumbnail.
- **Filename text:** `MaterialTheme.typography.bodySmall`, `maxLines = 2`, `overflow = Ellipsis`.
- **Metadata line:** size only (`labelSmall`, secondary text). No date in grid mode.
- **Padding inside card:** 8dp all sides.
- **Grid cell spacing:** `verticalArrangement = Arrangement.spacedBy(8.dp)`, `horizontalArrangement = Arrangement.spacedBy(8.dp)`.
- **Selection state:** full-card cyan border + checkmark overlay in top-right corner (24×24dp circle, `CheckCircle` icon, CyanPrimary, behind a semi-transparent scrim patch).
- **No inline action buttons** (Download/Delete icons) in grid mode. On single-tap: open file (image preview) or folder. On long-press: enter selection mode.

### Grid 3-Col Layout

Same as Grid 2-col except:
- `GridCells.Fixed(3)`.
- Thumbnail/icon: 80×80dp.
- Filename: `labelSmall`, `maxLines = 1`, ellipsis.
- No metadata line (size hidden in 3-col to save space).
- Card padding: 4dp all sides.

### Folders in Grid Mode

- Always show `Icons.Outlined.Folder` icon (no thumbnail regardless of settings).
- Icon tint: `CyanPrimary`. Icon size: 40dp (2-col) / 28dp (3-col).
- Label: folder name, same typography rules as above.
- No "Folder" subtitle in grid mode (space too tight).

### Recents Row

Always rendered as horizontal `LazyRow` regardless of the selected layout toggle. Layout toggle only affects the main file grid below Recents.

### Transition Between Layouts

- When toggling between layouts, the content switches with a `crossfade` (200ms). Scroll position resets to top on layout change.
- `AnimatedContent(targetState = viewLayout) { layout -> when(layout) { LIST -> LazyColumn ...; GRID2 -> LazyVerticalGrid(2) ...; GRID3 -> LazyVerticalGrid(3) ... } }`.

### Selection Mode in Grid

- Long-press on any card enters selection mode. Selection indicator: top-right corner circle check.
- Top bar shows selection actions as in list mode (select all, download, delete).
- Select All in grid selects all `displayItems` regardless of layout.

---

## TASK 8 — Light/Dark Mode Toggle

### Context

`Theme.kt` currently provides only `DarkVaultTheme`. Task: add light scheme, a toggle in Settings → Appearance, and immediate effect without restart.

### Light Theme Spec

Derive from M3 `lightColorScheme` seeded from `#00D4FF`:

| Token | Dark value | Light value |
|---|---|---|
| `background` | `#0A0A0F` | `#F5FAFF` |
| `surface` | `#12121A` | `#FFFFFF` |
| `surfaceVariant` | `#1E1E2E` | `#EDF3FB` |
| `primary` | `#00D4FF` | `#006B80` (darkened for contrast on white) |
| `onPrimary` | `#00363F` | `#FFFFFF` |
| `onBackground` | `#E8E8F0` | `#0A0A0F` |
| `onSurface` | `#E8E8F0` | `#0A0A0F` |
| `onSurfaceVariant` | `#8888AA` | `#4A4A6A` |
| `outline` | `#3A3A50` | `#C8C8D8` |
| `error` | `#FF4D6D` | `#B3001B` |

Note: Electric Cyan as the primary interactive accent is preserved in dark mode. In light mode, the seed color produces a darker cyan tint for legibility on white surfaces — use M3's tonal generation from `#00D4FF` seed rather than hardcoding. The `CyanPrimary` constant (`#00D4FF`) is used only in dark mode; in light mode, use `MaterialTheme.colorScheme.primary` everywhere (not the hardcoded constant).

### DataStore Persistence

- Key: `theme_mode` (`stringPreferencesKey`)
- Values: `"SYSTEM"` / `"DARK"` / `"LIGHT"`
- Default: `"SYSTEM"`
- Add to `PreferencesManager`: `val themeMode: Flow<String>`.

### Theme Application

- Read `themeMode` as a `StateFlow` at the **top of the composition** (in `MainActivity` / the root `DarkVaultTheme` call site).
- Wrap the NavHost with:
  ```
  val isDark = when(themeMode) {
      "DARK" -> true
      "LIGHT" -> false
      else -> isSystemInDarkTheme()
  }
  DarkVaultTheme(darkTheme = isDark) { ... }
  ```
- `DarkVaultTheme` receives a `darkTheme: Boolean` parameter and switches between `DarkVaultColorScheme` and `LightVaultColorScheme` accordingly.
- Effect is immediate (StateFlow → recomposition) — no restart required.

### Settings Toggle (see Task 10 for Settings layout)

In Settings → Appearance section:
- Row: `SettingRow(icon = palette_icon, title = "Theme", subtitle = current selection label)` with a trailing `DropdownMenu`.
- Options: `System default`, `Dark`, `Light`.
- Selecting an option updates DataStore immediately.

### Edge Cases

- On Android 12+ with dynamic color: use the static `#00D4FF` seed scheme, NOT dynamic MaterialYou colors. This is already the case (no `dynamicColorScheme` call). Keep it that way.
- On pre-Android 12: same behavior (no `dynamicColorScheme` anyway).
- System default → follows `isSystemInDarkTheme()` at runtime; changes when user changes OS setting.

---

## TASK 9 — Folder Navigation Icon Fix

This task is substantially covered by Task 6. The remaining spec:

### Back Arrow Visibility Rule

In `HomeScreen` `TopAppBar` `navigationIcon` slot:
- `if (homeViewModel.canGoBack)` → show `IconButton(onClick = { homeViewModel.navigateUp() })` with `Icons.AutoMirrored.Outlined.ArrowBack`, tint `CyanPrimary`.
- `else` → render nothing (`navigationIcon = {}`). No empty space reserved. M3 `TopAppBar` collapses the navigation icon slot cleanly.

This is already the pattern in the current code. The fix is ensuring `canGoBack = folderStack.size > 1` is computed correctly after every navigation event. Verify that `navigateUp()`, `navigateTo()`, and `openFolder()` all emit to `_folderStack` synchronously before recomposition.

### Breadcrumb Visibility Rule

Breadcrumb row (below top bar): `if (folderStack.size > 1)` — already the condition in current code.

At root (`folderStack.size == 1`): the email subtitle under "darkVault" is shown instead (current behavior). Breadcrumb row is hidden.

### System Back Intercept

In `HomeScreen`:
```
BackHandler(enabled = homeViewModel.canGoBack) {
    homeViewModel.navigateUp()
}
```
This prevents the system back from exiting the app while inside a sub-folder. When at root, the `BackHandler` is disabled and system back behaves normally.

---

## TASK 10 — Settings Screen Consolidation

### Context

Current `SettingsScreen.kt` has sections: Security, Password, Account, Previews. Task: add Appearance section (new, first), consolidate Files & Storage (includes Export backup), add Developer section (DEBUG only), and reorganize.

### Full Settings Screen Layout

```
◄ Settings

┌──────────────────────────────────────────────┐
│ APPEARANCE                                   │
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤  ← 1px cyan divider
│  [palette]  Theme          System  ▼         │
│                                              │
│  [grid]     Default layout    List  ▼        │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ SECURITY                                     │
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
│  [fingerprint]  Biometric lock     [switch]  │
│  ─────────────────────────────────────────   │
│  [timer]     Auto-lock         Never  ▼      │
│  ─────────────────────────────────────────   │
│  [timer]     Session timeout  Never   ▼      │
│  ─────────────────────────────────────────   │
│  [lock]      Lockout status                  │
│  ─────────────────────────────────────────   │
│  [lock_open] Lock vault now           Lock   │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ PASSWORD                                     │
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
│  [lock]  Change password           Change    │
│  ─────────────────────────────────────────   │
│  [key]   Recovery key              Rotate    │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ FILES & STORAGE                              │
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
│  [folder_zip] Export local backup    Export  │
│  ─────────────────────────────────────────   │
│  [image]   Image previews          [switch]  │
│  ─────────────────────────────────────────   │
│  [video]   Video previews          [switch]  │
│  ─────────────────────────────────────────   │
│  [image_search] Show thumbnails    [switch]  │
│  ─────────────────────────────────────────   │
│  [storage]  Storage quota                    │
│             Vault: X.X MB · Drive free: Y GB │
│             [────────────███░░░░░░░░░──────] │
│             X.X GB of Y.Y GB used            │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ ACCOUNT                                      │
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
│  [account_circle]  Signed in as              │
│                    user@gmail.com            │
│  ─────────────────────────────────────────   │
│  [switch_account]  Switch account    Switch  │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐  ← DEBUG only
│ DEVELOPER                                    │
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
│  [bug_report]  Developer options         ›   │
└──────────────────────────────────────────────┘
```

### Section Card Pattern (reused from current `SettingsCard`)

Each section: `SettingsCard { content }` — existing `Card(RoundedCornerShape(12.dp), containerColor = VaultSurfaceVariant)`.

**Section header:** `SectionHeader(title)` — existing composable (uppercase, CyanPrimary, labelSmall, letterSpacing 2sp, padding horizontal 4dp vertical 8dp).

**Section divider (signature element):** Between the section title and the card content, place a `HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))` — this replaces the current pattern of just jumping into the card. The divider is INSIDE the card, immediately after the card's top edge, before any rows.

Actually, to be precise: the `SectionHeader` is OUTSIDE the card (label above). The card starts immediately after the header. Place the 1px cyan divider as the FIRST element inside `SettingsCard`, spanning full width, before any `SettingRow`. This creates the signature cyan underline at the top of each section card.

```kotlin
SettingsCard {
    HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))
    SettingRow(...)
    HorizontalDivider(color = VaultOutline.copy(alpha = 0.3f))
    SettingRow(...)
    ...
}
```

The intra-section dividers between rows remain `VaultOutline.copy(alpha = 0.3f)` as now.

### Appearance Section (NEW)

**Theme row:**
- Icon: `Icons.Outlined.Palette` (or `Icons.Outlined.ColorLens`), 22dp, CyanPrimary.
- Title: `"Theme"`, subtitle: current value label (`"System default"` / `"Dark"` / `"Light"`).
- Trailing: `ExposedDropdownMenuBox` (same pattern as auto-lock dropdown). Shows a compact dropdown with three items.
- On selection: write to DataStore `theme_mode` key. Applied immediately via StateFlow.

**Default layout row:**
- Icon: `Icons.Outlined.GridView`, 22dp, CyanPrimary.
- Title: `"Default layout"`, subtitle: current value label (`"List"` / `"Grid (2 col)"` / `"Grid (3 col)"`).
- Trailing: `ExposedDropdownMenuBox` with three options.
- On selection: write to DataStore `view_layout` key.

### Security Section (unchanged + one addition)

Existing rows remain in their current order. Add after "Session timeout":

**Lockout status row (NEW):**
- Icon: `Icons.Outlined.Security` (or `Icons.Outlined.ShieldOutlined`), 22dp.
- Title: `"Brute-force protection"`.
- Subtitle logic:
  - `failedAttempts == 0 && lockoutUntilMs <= now`: `"No failed attempts"`. Color: `onSurfaceVariant`.
  - `failedAttempts > 0 && lockoutUntilMs <= now`: `"$failedAttempts failed attempt(s)"`. Color: warning (use `VaultError.copy(alpha=0.7f)`).
  - `lockoutUntilMs > now`: `"Locked until ${formatTime(lockoutUntilMs)}"`. Color: `VaultError`.
- No trailing action. Read-only informational row.
- `formatTime(ms)`: format as `"HH:MM"` in system timezone using `java.time`.
- Data source: `authViewModel.failedAttempts` and `authViewModel.lockoutUntilMs` StateFlows (already exposed).

### Files & Storage Section (NEW)

**Export local backup row:**
- Icon: `Icons.Outlined.FolderZip`, 22dp, CyanPrimary.
- Title: `"Export local backup"`.
- Subtitle: `"Decrypt all vault files to Downloads/darkVault-loc/"`.
- Trailing: `TextButton("Export", color = CyanPrimary)`.
- On tap: call `homeViewModel.exportVaultBackup(password, account)` — this requires `password` and `account` to be available in `SettingsScreen`. Pass them as parameters from the NavGraph.
- If `password == null` (vault locked): show snackbar `"Vault is locked — unlock first"`. Do not crash.

**Image previews row:** Move from existing Previews section — unchanged.

**Video previews row:** Move from existing Previews section — unchanged.

**Show thumbnails row (NEW):**
- Icon: `Icons.Outlined.ImageSearch` (or `Icons.Outlined.BurstMode`), 22dp, CyanPrimary.
- Title: `"Show thumbnails"`.
- Subtitle: `"Decrypt file thumbnails in list view (uses more Drive data)"`.
- Trailing: `Switch` (same colors as other switches).
- Backed by DataStore key: `thumbnails_enabled` (`booleanPreferencesKey`), default `true`.
- When toggled off: clears Coil in-memory thumbnail cache (`ImageLoader.memoryCache?.clear()`).

**Storage quota row:**
- Icon: `Icons.Outlined.Storage`, 22dp, CyanPrimary.
- Title: `"Storage quota"`.
- Trailing: none (full-width display).
- Content: embed the existing `StorageInfoCard` composable directly inside this row's layout, below the title/subtitle line.
- Layout: `SettingRow` with `action = {}` (empty) but the `subtitle` area is replaced by the `StorageInfoCard` inline. Or alternatively use a custom layout: icon + title on first line, then `StorageInfoCard` below spanning full width with `Modifier.padding(start = 34.dp)` (icon indent).

### Account Section (unchanged)

Existing "Signed in as" and "Switch account" rows, no change.

### Developer Section (DEBUG only, NEW)

Shown only when `BuildConfig.DEBUG`:

```kotlin
if (BuildConfig.DEBUG) {
    Spacer(Modifier.height(16.dp))
    SectionHeader("Developer")
    SettingsCard {
        HorizontalDivider(thickness = 1.dp, color = CyanPrimary.copy(alpha = 0.6f))
        SettingRow(
            icon = { Icon(Icons.Outlined.BugReport, null, tint = VaultError, size = 22.dp) },
            title = "Developer options",
            subtitle = "Diagnostics, fault injection, log viewer"
        ) {
            IconButton(onClick = onNavigateToDebugPanel) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = CyanPrimary)
            }
        }
    }
}
```

- `onNavigateToDebugPanel: () -> Unit` added as a parameter to `SettingsScreen`.
- In release builds: this block is completely absent (R8 will dead-code eliminate it given `BuildConfig.DEBUG = false`).

### SettingsScreen Parameter Updates

`SettingsScreen` needs two new parameters:
1. `password: String?` — master password for Export backup action.
2. `account: GoogleSignInAccount?` — for Export backup.
3. `homeViewModel: HomeViewModel` — for calling `exportVaultBackup`.
4. `onNavigateToDebugPanel: () -> Unit` — for Developer row.

These must be threaded through the NavGraph call site.

### Export Backup: Keep in More Menu

Per the task spec, `Export backup` stays in the HomeScreen More (⋮) menu for discoverability alongside its new Settings location. No change to the More menu.

---

## Cross-Cutting Notes

### DataStore Keys Summary (new keys added across all tasks)

| Key | Type | Default | Task |
|---|---|---|---|
| `theme_mode` | String | `"SYSTEM"` | Task 8 |
| `view_layout` | String | `"LIST"` | Task 7 |
| `thumbnails_enabled` | Boolean | `true` | Task 10 |

### Color Token Quick Reference

| Usage | Token | Value |
|---|---|---|
| Background | `VaultBackground` | `#0A0A0F` |
| Surface / Card | `VaultSurfaceVariant` | `#12121A` |
| Elevated surface | secondary surface | `#1E1E2E` |
| Primary accent | `CyanPrimary` | `#00D4FF` |
| Secondary text | `onSurfaceVariant` | `#8888AA` |
| Primary text | `onSurface` | `#E8E8F0` |
| Error | `VaultError` | `#FF4D6D` |
| Outline / divider | `VaultOutline` | `#3A3A50` |
| Signature divider | `CyanPrimary.copy(alpha=0.6f)` | cyan @60% |

### Typography Quick Reference

| Usage | M3 Style |
|---|---|
| Screen headings | `headlineSmall` |
| Card titles | `titleMedium` |
| Body / list item title | `bodyMedium` |
| Subtitles / metadata | `bodySmall` |
| Chips / labels | `labelSmall` |
| Buttons | `labelLarge` |
| Hex / key values | `bodyMedium` + `FontFamily.Monospace` + `letterSpacing=1.5sp` |
| Section headers | `labelSmall` + `letterSpacing=2sp` + uppercase |

### Icon Reference

| Purpose | Icon |
|---|---|
| User / account | `Icons.Outlined.AccountCircle` |
| Palette / theme | `Icons.Outlined.Palette` |
| Grid view toggle | `Icons.Outlined.GridView` |
| Grid 3-col | `Icons.Outlined.GridOn` |
| List view | `Icons.Outlined.ViewList` |
| Storage | `Icons.Outlined.Storage` |
| Image search | `Icons.Outlined.ImageSearch` |
| Shield / security | `Icons.Outlined.Security` |
| Debug | `Icons.Outlined.BugReport` |
| Pause | `Icons.Outlined.PauseCircleOutline` |
| Play overlay | `Icons.Outlined.PlayCircleOutline` |
| Forward arrow | `Icons.AutoMirrored.Outlined.ArrowForward` |
| Export/zip | `Icons.Outlined.FolderZip` |

---

*End of UI_SPEC.md*
