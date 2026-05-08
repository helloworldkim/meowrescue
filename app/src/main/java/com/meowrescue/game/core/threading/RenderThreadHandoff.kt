package com.meowrescue.game.core.threading

import android.app.Activity

/**
 * Canonical render-thread → Main-thread handoff. Delegates to
 * [Activity.runOnUiThread], which:
 *   - if called from Main, invokes [block] **inline** (no post overhead)
 *   - if called from a background thread, posts [block] via the
 *     Activity's Main-thread Handler.
 *
 * Use this every time a render-thread producer (e.g., ScoreEventQueue drain
 * per ADR-0008, stage-settled notification per ADR-0008 § Settling) needs to
 * touch the View tree, update StateFlow backed by Main collectors, or call
 * any Main-thread-only API (Dialogs, Activity state queries, etc.).
 *
 * ## Never:
 *   - Mutate Views from the render thread — crashes with
 *     [android.view.ViewRootImpl.CalledFromWrongThreadException] or corrupts
 *     View state silently.
 *   - Post through a Handler the render thread does not own.
 *
 * See ADR-0004 § Implementation Guidelines and ADR-0008 § Contact Listener.
 */
fun Activity.postToMain(block: () -> Unit) = runOnUiThread(block)
