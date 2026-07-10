# Xiaomi Screen Share Protection Bypass

This document records the Xiaomi/HyperOS screen share protection call chain and
Island Recorder's privileged implementation.

## Problem

When Xiaomi screen share protection is enabled, MediaProjection output can hide
system surfaces such as the notification shade, Quick Settings panel, status bar,
IME popup windows, and lock screen surfaces.

Root-based simulation of the Security Center toggle worked, but the previous
Shizuku implementation only appeared to work. It wrote one setting and attempted
to send a broadcast, but the broadcast path did not reliably execute the Xiaomi
receiver under Shizuku's shell identity.

## Xiaomi Call Chain

Security Center toggle:

```java
AbstractC25639p.m34364w(enabled);
Intent intent = new Intent("com.miui.action.open_screen_share_protection");
intent.putExtra("open_screen_share_protection", enabled);
activity.sendBroadcast(intent, "miui.permission.READ_AND_WIRTE_PERMISSION_MANAGER");
```

Observed in:

- `D:/code/jadx/系统界面_16.03.251211.r.jadx.cache/code/sources/ec/0001cfec.java`

The helper only writes the user-facing toggle key:

```java
Settings.Secure.putInt(..., "screen_share_protection", enabled ? 1 : 0);
```

Observed in:

- `D:/code/jadx/系统界面_16.03.251211.r.jadx.cache/code/sources/07/0001d007.java`

The receiver is dynamically registered with a receiver permission:

```java
registerReceiver(
    new C0174w(handler),
    intentFilter,
    "miui.permission.READ_AND_WIRTE_PERMISSION_MANAGER",
    null,
    flags
);
```

Observed in:

- `D:/code/jadx/系统界面_16.03.251211.r.jadx.cache/code/sources/04/00000104.java`

When the receiver handles
`com.miui.action.open_screen_share_protection`, it posts handler message `2454`.
The handler then calls:

- `m812e0()` when protection is enabled
- `m793H()` when protection is disabled

Close path:

```java
Settings.Secure.putInt(..., "screen_share_protection_on", 0);
WindowManagerGlobal.getWindowManagerService()
    .setScreenShareProjectBlackList(null);
```

Open path:

```java
Settings.Secure.putInt(..., "screen_share_protection_on", 1);
ArrayList list = new ArrayList();
list.add("NotificationShade");
list.add("StatusBar");
list.add(InputMethod.TAG);
list.add("com.miui.securitycenter/com.miui.permcenter.capsule.ScreenShareProtectionActivity");
WindowManagerGlobal.getWindowManagerService()
    .setScreenShareProjectBlackList(list);
```

Observed in:

- `D:/code/jadx/系统界面_16.03.251211.r.jadx.cache/code/sources/04/00000104.java`

## WindowManager Behavior

WMS registers a `ContentObserver` on:

```text
Settings.Secure["screen_share_protection_on"]
```

When this key changes, WMS calls `updateScreenShareProjectFlag()`, which iterates
existing windows and reapplies surface visibility flags.

Observed in:

- `D:/code/jadx/系统界面_16.03.251211.r.jadx.cache/code/sources/6c/00013e6c.java`
- `D:/code/jadx/系统界面_16.03.251211.r.jadx.cache/code/sources/bd/00013ebd.java`

The actual hide decision is made by
`WindowManagerServiceImpl.getScreenShareProjectAndPrivateCastFlag(...)`.
It checks:

- `Settings.Secure["screen_share_protection_on"] == 1`
- app-op `10041` for app windows
- `IMiuiScreenProjectionStub.getScreenShareProjectBlackList()`

If the window title contains a blacklisted name, WMS applies flag `128`
(`EXTRA_FLAG_IS_SCREEN_SHARE_PROTECTION_HIDE`).

Important blacklist entries:

```text
NotificationShade
StatusBar
InputMethod
com.miui.securitycenter/com.miui.permcenter.capsule.ScreenShareProtectionActivity
```

## Why The Broadcast Path Failed Under Shizuku

The broadcast receiver is dynamically registered with
`miui.permission.READ_AND_WIRTE_PERMISSION_MANAGER`.

The previous app implementation did this:

1. Write `Settings.Secure["screen_share_protection"]`.
2. Send `com.miui.action.open_screen_share_protection`.
3. Read back only `screen_share_protection`.

This can log success even when Xiaomi's receiver never runs. In that failure
case:

- `screen_share_protection` changes, so the SystemUI privacy提示岛 can disappear.
- `screen_share_protection_on` may remain unchanged.
- WMS screen share blacklist may remain unchanged.
- Notification shade, QS, status bar, and lock screen surfaces can still be
  hidden from MediaProjection.

Root succeeds because the root `app_process` binder hook has a stronger calling
identity for this protected path, or can directly mutate the state that WMS uses.
Shizuku's binder identity is shell, which is not a reliable sender for the MIUI
protected receiver.

## Island Recorder Implementation

Implementation entry:

- `app/src/main/java/com/island/recorder/framework/privileged/core/execution/runtime/DefaultPrivilegedService.kt`

The current `setScreenShareProtectionEnabled(enabled)` implementation directly
performs the state changes that Xiaomi's receiver would have performed:

1. Write `Settings.Secure["screen_share_protection"]`.
   This key is retained because it controls the visible Security Center/SystemUI
   privacy prompt state.
2. Write `Settings.Secure["screen_share_protection_on"]`.
   This is the WMS runtime key that triggers surface flag refresh.
3. Call `IWindowManager.setScreenShareProjectBlackList(...)` through the active
   privileged binder hook.
4. Read back both secure settings.

Close behavior before recording:

```text
screen_share_protection = 0
screen_share_protection_on = 0
IWindowManager.setScreenShareProjectBlackList(null)
```

Restore behavior after recording:

```text
screen_share_protection = 1
screen_share_protection_on = 1
IWindowManager.setScreenShareProjectBlackList(default Xiaomi blacklist)
```

The broadcast path was removed from this operation.

## Hidden API And Binder Hook

Minimal hidden API declaration:

- `hidden-api/src/main/java/android/view/IWindowManager.java`

Runtime wiring:

- `app/src/main/java/com/island/recorder/framework/privileged/core/context/hook/ShizukuHook.kt`
- `app/src/main/java/com/island/recorder/framework/privileged/core/execution/runtime/PrivilegedRuntime.kt`

Shizuku mode wraps the `window` service binder with `ShizukuBinderWrapper`.
Root mode wraps `ServiceManager.getService(Context.WINDOW_SERVICE)` with the
root `app_process` binder wrapper.

## Diagnostics

Useful app logs:

- `Set screen share protection to ...`
- `windowBlacklistUpdated=...`
- `Set screen share project blacklist enabled=...`
- `Screen share protection readback mismatch ...`

If notification shade/QS/lock screen still do not appear in recordings, verify:

1. `screen_share_protection` readback matches the requested state.
2. `screen_share_protection_on` readback matches the requested state.
3. `IWindowManager.setScreenShareProjectBlackList(...)` did not throw.
4. WMS logs do not show blacklist update failures.
5. The device firmware still uses the same blacklist titles and flag behavior.

## Verification

After implementing the direct WMS path, these builds passed:

```powershell
.\gradlew.bat :app:assembleUnstableDebug
.\gradlew.bat assembleDebug
```
