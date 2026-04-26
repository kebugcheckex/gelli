# CAB Unlock Crash Regression Plan

## Scope
Validate that the app no longer crashes on screen unlock due to view state restore mismatch involving `id/cab_stub`.

## 1. Baseline Repro Attempt
- Open `Library` tab with a selectable list (Songs/Albums/Artists/Playlists).
- Long-press an item to activate CAB.
- Lock device, wait 10-30 seconds, unlock.
- Expected: app stays alive; CAB either restores cleanly or exits without crash.

## 2. Rotation + Lock Sequence
- Activate CAB.
- Rotate portrait -> landscape -> portrait.
- Lock/unlock once after each rotation.
- Expected: no crash; no corrupted toolbar/CAB UI.

## 3. Background Pressure Path
- Activate CAB.
- Press Home, open several heavy apps/camera, return to app.
- Lock/unlock.
- Expected: no crash even if activity/fragment was recreated.

## 4. Detail Screens That Also Use `cab_stub`
Repeat baseline test in:
- Album detail
- Artist detail
- Genre detail
- Playlist detail

Expected: no crash on unlock in each screen.

## 5. Multi-select Lifecycle Integrity
- Select multiple items (CAB title shows count).
- Lock/unlock.
- Perform CAB action (`Select all`, menu action, back to dismiss).
- Expected: actions still work; no stale selection state; no crash.

## 6. Process Death Simulation (Developer Option)
- Enable "Don't keep activities" (or force-stop process while backgrounded).
- Enter app, activate CAB, background, return, lock/unlock.
- Expected: no restore crash; UI recovers safely.

## 7. Monkey/Smoke Run
- Run short monkey test with rotations + backgrounding + unlock events if available.
- Expected: no `Wrong state class ... id/cab_stub` in logs.

## Log Checks
- Confirm absence of:
  - `IllegalArgumentException: Wrong state class ... id/cab_stub`
- Capture any new lifecycle/state restore exceptions for follow-up.

## Optional Tracker Fields
- Build/Version:
- Device/OS:
- Scenario #:
- Result (Pass/Fail):
- Notes:
- Log/Crash Link:
