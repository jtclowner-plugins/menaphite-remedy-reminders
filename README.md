# Preserve & Menaphite Optimiser

Prompts you to use Preserve prayer and/or Menaphite remedies at optimal times to maximise the duration of divine potion and saturated heart boosts.

You are prompted to sip a Menaphite remedy shortly before a timed boost expires, converting it into a regularly decaying boost. **Preserve is timed against the stat-decay cycle so the first post-Menaphite stat decay occurs as close as possible to the full 90 seconds later.**

* Handles overlapping boosts independently, and accounts for existing regularly decaying boosts when optimising Preserve.
* Re-potting refreshes the tracked timer and moves the reminders to the new expiry.
* Configurable reminder timing, with notifications, countdown infoboxes, and overhead text.
* Per-potion toggles. Saturated heart is also supported and can be limited to when the heart is no longer in your inventory (e.g. wilderness use cases).
