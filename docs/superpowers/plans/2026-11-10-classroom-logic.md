# Classroom logic fixes Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task by task.

**Goal:** Fix the four remaining issues identified in the preceding review.
**Architecture:** Extend the current Java/SQLite services and React UI. Persist account microphone blocks and class chat; close abandoned meetings using an owner heartbeat; aggregate attendance from all stored connection intervals.
**Tech Stack:** Java 21, Spring Boot, SQLite, React, TypeScript. No new dependencies.
**Spec:** User requested all reported issues fixed. Implementation defaults stated in chat: 10 minutes owner absence, close active meetings on server restart, persistent class chat, attendance without overlapping-tab double counting or invented interruption time.

## Global Constraints

- Preserve accounts, membership, messages and history. Existing active meetings close on server restart with an explicit reason.
- Membership and owner authorization remain server enforced.
- Keep the user's running server untouched; package a distinct JAR and test against an isolated database/port.
- Do not invent a pedagogical pass/fail attendance threshold.

## Review Focus

- Reconnecting or opening multiple tabs must not bypass an account microphone block.
- A short owner disconnect must not immediately end everyone’s session.
- A removed member must not read stored chat or attendance.
- Concurrent/overlapping connections count time once; interrupted intervals have unknown duration.
- Older messages/meetings/attendance must be accessible by pagination.

### Task 1: Account microphone blocks

- [x] Extend real socket tests to reconnect and open a second tab after a block; assert all tabs remain blocked and cannot relay audio.
- [x] Change MediaControls microphone blocks to account scope; persist blocks per meeting through ClassroomService; retain room controls until meeting ends.
- [x] Run MediaControlsTest and MediaRelayTest.

### Task 2: Abandoned meetings

- [x] Add tests proving the 10 minute heartbeat boundary and restart closure without deleting history.
- [x] Add owner_seen_at/end_reason session fields in Database; heartbeat on owner join/ping; close expired meetings in the existing scheduler and disconnect peers.
- [x] Show the timeout/restart policy and history closure reason; run backend tests.

### Task 3: Durable chat

- [x] Test message validation, room isolation, member revocation, cursor ordering and restart persistence.
- [x] Add class_messages storage; append before broadcasting, replay recent history on join and expose GET /rooms/{id}/messages?before=.
- [x] Render stored messages with timestamps and an older-message button; test sending, leaving/rejoining and late arrival in browsers.

### Task 4: Attendance and history

- [x] Test overlapping/disjoint/active/interrupted intervals with hand-calculated expected durations and owner authorization.
- [x] Add owner-only paginated attendance per meeting, union intervals per account, and paginate meeting history.
- [x] Add attendance UI and refresh/pagination controls. Run complete backend/frontend suites and build/package.
- [x] Verify the final JAR with two isolated agent-browser sessions, document evidence, and stop QA processes.


## Evidence
Backend 29/29, frontend 9/9, TypeScript/Vite and executable JAR verified. Fresh reviewer found a reconnect chat pagination gap; regression failed before the fix and passed after. Browser evidence and decisions: docs/reports/classroom-logic-2026-11-10.md. Existing dirty workspace preserved; no automatic commit, push or merge.
