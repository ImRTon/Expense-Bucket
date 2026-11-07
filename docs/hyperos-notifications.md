# HyperOS capture notifications

Capture notifications use the Xiaomi HyperOS 3 focus-notification template when
`notification_focus_protocol >= 3` and the app's `canShowFocus` query returns true.
Other devices, disabled focus permissions, or unavailable providers receive the
ordinary actionable notification. No Xiaomi SDK, cloud push, media session, or
foreground service is added.

- Compact island: exact formatted amount on the left, a lime pencil icon and `記帳`
  action label on the right. The minimized single island also uses the pencil icon.
- Expanded focus card: amount, merchant, and a native `Notification.Action` that
  opens the corresponding transaction editor with its existing data. The booking
  action uses the pencil icon and a contrasting lime button with dark text.
- The compact island expires after 120 seconds; the underlying notification and
  saved draft remain available. Opening the editor dismisses that reminder.
- Cold and warm starts use a unique PendingIntent data URI per transaction. If the
  transaction was deleted, tapping opens the drafts inbox instead.

## System-controlled behavior and eligibility

HyperOS controls compact-island click gestures and final notification eligibility.
The public template does not expose a separate right-half click handler. The
expanded card's `記帳` action opens the editor directly; a single tap on the compact
right side is not guaranteed to skip expansion.

Xiaomi's mainland developer onboarding requires an App ID, signature registration,
and service approval or test-device allowlisting. That documentation explicitly
separates mainland and overseas platforms. Do not infer that every global ROM
requires the mainland onboarding or that a global device automatically permits
focus notifications. This implementation checks the device's actual permission.
The open-source HyperIsland ToolKit uses the same capability/permission checks
without a mandatory App ID parameter; it is reference material, not an app dependency.

On 2026-11-07 the connected Taiwan ROM (`OS3.0.303.0.WOCTWXM`) returned
`canShowFocus=true` for this app. An existing-draft reminder was posted without
installing a diagnostic package or changing ledger data, and the user confirmed
that it appeared on the native island. No Xiaomi App ID registration was needed
for that observed device/app combination.

Android Live Updates have separate eligibility rules for ongoing, user-initiated,
time-sensitive activities. They are not enabled here as a workaround for a completed
purchase reminder.

References:

- [Xiaomi developer guide](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)
- [Xiaomi onboarding](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132)
- [Xiaomi interaction description](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2140)
- [HyperIsland ToolKit source](https://github.com/D4vidDf/HyperIsland-ToolKit)
- [Android Live Updates criteria](https://developer.android.com/develop/ui/compose/notifications/live-update)
