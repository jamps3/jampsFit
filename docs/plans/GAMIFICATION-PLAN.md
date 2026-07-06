# Gamification Plan

This is the living plan for jampsFit gamification. New gamification planning should live under `docs/plans/` and this file should remain the feature source of truth.

## Options
- **Daily Goal Rings**: Compact progress indicators for steps, calories, sleep, and activity count on the Progress tab.
- **XP + Level System**: Award XP for steps, goals, measurements, and healthy consistency.
- **Streaks**: Track consecutive days meeting a step goal, syncing data, or logging sleep.
- **Achievements / Badges**: Unlock badges such as 10k Steps, Sleep Scout, Heart Check, and streak milestones.
- **Weekly Challenges**: Goals such as walking 35,000 steps or sleeping 7h for 3 nights.
- **Personal Bests**: Highlight best steps, longest sleep, highest activity count, and strongest week.
- **Fitness Score**: A daily score from steps, sleep, calories, and measurement completeness.
- **Quest Cards**: A few rotating daily mini-goals like taking a steps reading or reaching the next step milestone.
- **Progress Timeline**: A history of milestone events, level-ups, streaks, and personal records.
- **Avatar / Companion Growth**: A visual companion or emblem that grows as healthy habits build.

## V1 Scope
- Add daily goal progress for steps, active calories, sleep, and activity count.
- Add deterministic XP and levels derived from local watch data.
- Add current step-goal streaks based on daily history.
- Add a compact achievement strip for recently unlocked badges.
- Add a dedicated Progress tab while keeping the existing Watch dashboard intact.
- Avoid Room migrations by deriving V1 from existing `WatchState` and health history.

## V1 Rules
- Step goal uses the watch step goal when available, otherwise `10,000`.
- Default active calorie goal is `500 kcal`.
- Default sleep goal is `7h`.
- Default activity-count goal is `100`.
- XP grants:
  - `1 XP` per `100` steps, capped at `120 XP/day`.
  - `25 XP` for reaching the daily step goal.
  - `20 XP` for reaching the sleep goal.
  - `15 XP` for reaching the calorie goal.
  - `10 XP` for having heart-rate data.
- Level formula: `level = totalXp / 250 + 1`.

## V1 Achievements
- First Sync
- 5k Steps
- 10k Steps
- Calorie Spark
- Sleep Scout
- Heart Check
- Connected Day
- 3-Day Streak
- 7-Day Streak
- Personal Best Steps

## Deferred
- Weekly Challenges
- Quest Cards
- Progress Timeline
- Avatar / Companion Growth
- Cloud, social, or leaderboard features
