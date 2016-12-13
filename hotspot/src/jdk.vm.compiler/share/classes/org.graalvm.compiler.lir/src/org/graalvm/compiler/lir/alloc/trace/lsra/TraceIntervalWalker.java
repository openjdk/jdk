/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.lir.alloc.trace.lsra;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.alloc.trace.lsra.FixedInterval.FixedList;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceInterval.AnyList;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceInterval.RegisterBinding;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceInterval.State;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase.TraceLinearScan;

/**
 */
class TraceIntervalWalker {

    protected final TraceLinearScan allocator;

    /**
     * Sorted list of intervals, not live before the current position.
     */
    protected AnyList unhandledAnyList;

    /**
     * Sorted list of intervals, live at the current position.
     */
    protected AnyList activeAnyList;
    protected FixedList activeFixedList;

    /**
     * Sorted list of intervals in a life time hole at the current position.
     */
    protected FixedList inactiveFixedList;

    /**
     * The current position (intercept point through the intervals).
     */
    protected int currentPosition;

    /**
     * Processes the {@code currentInterval} interval in an attempt to allocate a physical register
     * to it and thus allow it to be moved to a list of {@linkplain #activeAnyList active}
     * intervals.
     *
     * @param currentInterval The interval to be activated.
     *
     * @return {@code true} if a register was allocated to the {@code currentInterval} interval
     */
    protected boolean activateCurrent(TraceInterval currentInterval) {
        if (Debug.isLogEnabled()) {
            logCurrentStatus();
        }
        return true;
    }

    @SuppressWarnings("try")
    protected void logCurrentStatus() {
        try (Indent i = Debug.logAndIndent("active:")) {
            logList(activeFixedList.getFixed());
            logList(activeAnyList.getAny());
        }
        try (Indent i = Debug.logAndIndent("inactive(fixed):")) {
            logList(inactiveFixedList.getFixed());
        }
    }

    private static void logList(FixedInterval i) {
        for (FixedInterval interval = i; interval != FixedInterval.EndMarker; interval = interval.next) {
            Debug.log("%s", interval.logString());
        }
    }

    private static void logList(TraceInterval i) {
        for (TraceInterval interval = i; interval != TraceInterval.EndMarker; interval = interval.next) {
            Debug.log("%s", interval.logString());
        }
    }

    void walkBefore(int lirOpId) {
        walkTo(lirOpId - 1);
    }

    void walk() {
        walkTo(Integer.MAX_VALUE);
    }

    /**
     * Creates a new interval walker.
     *
     * @param allocator the register allocator context
     * @param unhandledFixed the list of unhandled {@linkplain RegisterBinding#Fixed fixed}
     *            intervals
     * @param unhandledAny the list of unhandled {@linkplain RegisterBinding#Any non-fixed}
     *            intervals
     */
    TraceIntervalWalker(TraceLinearScan allocator, FixedInterval unhandledFixed, TraceInterval unhandledAny) {
        this.allocator = allocator;

        unhandledAnyList = new AnyList(unhandledAny);
        activeAnyList = new AnyList(TraceInterval.EndMarker);
        activeFixedList = new FixedList(FixedInterval.EndMarker);
        // we don't need a separate unhandled list for fixed.
        inactiveFixedList = new FixedList(unhandledFixed);
        currentPosition = -1;
    }

    protected void removeFromList(TraceInterval interval) {
        activeAnyList.removeAny(interval);
    }

    /**
     * Walks up to {@code from} and updates the state of {@link FixedInterval fixed intervals}.
     *
     * Fixed intervals can switch back and forth between the states {@link State#Active} and
     * {@link State#Inactive} (and eventually to {@link State#Handled} but handled intervals are not
     * managed).
     */
    @SuppressWarnings("try")
    private void walkToFixed(State state, int from) {
        assert state == State.Active || state == State.Inactive : "wrong state";
        FixedInterval prevprev = null;
        FixedInterval prev = (state == State.Active) ? activeFixedList.getFixed() : inactiveFixedList.getFixed();
        FixedInterval next = prev;
        if (Debug.isLogEnabled()) {
            try (Indent i = Debug.logAndIndent("walkToFixed(%s, %d):", state, from)) {
                logList(next);
            }
        }
        while (next.currentFrom() <= from) {
            FixedInterval cur = next;
            next = cur.next;

            boolean rangeHasChanged = false;
            while (cur.currentTo() <= from) {
                cur.nextRange();
                rangeHasChanged = true;
            }

            // also handle move from inactive list to active list
            rangeHasChanged = rangeHasChanged || (state == State.Inactive && cur.currentFrom() <= from);

            if (rangeHasChanged) {
                // remove cur from list
                if (prevprev == null) {
                    if (state == State.Active) {
                        activeFixedList.setFixed(next);
                    } else {
                        inactiveFixedList.setFixed(next);
                    }
                } else {
                    prevprev.next = next;
                }
                prev = next;
                TraceInterval.State newState;
                if (cur.currentAtEnd()) {
                    // move to handled state (not maintained as a list)
                    newState = State.Handled;
                } else {
                    if (cur.currentFrom() <= from) {
                        // sort into active list
                        activeFixedList.addToListSortedByCurrentFromPositions(cur);
                        newState = State.Active;
                    } else {
                        // sort into inactive list
                        inactiveFixedList.addToListSortedByCurrentFromPositions(cur);
                        newState = State.Inactive;
                    }
                    if (prev == cur) {
                        assert state == newState;
                        prevprev = prev;
                        prev = cur.next;
                    }
                }
                intervalMoved(cur, state, newState);
            } else {
                prevprev = prev;
                prev = cur.next;
            }
        }
    }

    /**
     * Walks up to {@code from} and updates the state of {@link TraceInterval intervals}.
     *
     * Trace intervals can switch once from {@link State#Unhandled} to {@link State#Active} and then
     * to {@link State#Handled} but handled intervals are not managed.
     */
    @SuppressWarnings("try")
    private void walkToAny(int from) {
        TraceInterval prevprev = null;
        TraceInterval prev = activeAnyList.getAny();
        TraceInterval next = prev;
        if (Debug.isLogEnabled()) {
            try (Indent i = Debug.logAndIndent("walkToAny(%d):", from)) {
                logList(next);
            }
        }
        while (next.from() <= from) {
            TraceInterval cur = next;
            next = cur.next;

            if (cur.to() <= from) {
                // remove cur from list
                if (prevprev == null) {
                    activeAnyList.setAny(next);
                } else {
                    prevprev.next = next;
                }
                intervalMoved(cur, State.Active, State.Handled);
            } else {
                prevprev = prev;
            }
            prev = next;
        }
    }

    /**
     * Get the next interval from {@linkplain #unhandledAnyList} which starts before or at
     * {@code toOpId}. The returned interval is removed.
     *
     * @postcondition all intervals in {@linkplain #unhandledAnyList} start after {@code toOpId}.
     *
     * @return The next interval or null if there is no {@linkplain #unhandledAnyList unhandled}
     *         interval at position {@code toOpId}.
     */
    private TraceInterval nextInterval(int toOpId) {
        TraceInterval any = unhandledAnyList.getAny();

        if (any != TraceInterval.EndMarker) {
            TraceInterval currentInterval = unhandledAnyList.getAny();
            if (toOpId < currentInterval.from()) {
                return null;
            }

            unhandledAnyList.setAny(currentInterval.next);
            currentInterval.next = TraceInterval.EndMarker;
            return currentInterval;
        }
        return null;

    }

    /**
     * Walk up to {@code toOpId}.
     *
     * @postcondition {@link #currentPosition} is set to {@code toOpId}, {@link #activeFixedList}
     *                and {@link #inactiveFixedList} are populated.
     */
    @SuppressWarnings("try")
    protected void walkTo(int toOpId) {
        assert currentPosition <= toOpId : "can not walk backwards";
        for (TraceInterval currentInterval = nextInterval(toOpId); currentInterval != null; currentInterval = nextInterval(toOpId)) {
            int opId = currentInterval.from();

            // set currentPosition prior to call of walkTo
            currentPosition = opId;

            // update unhandled stack intervals
            // updateUnhandledStackIntervals(opId);

            // call walkTo even if currentPosition == id
            walkToFixed(State.Active, opId);
            walkToFixed(State.Inactive, opId);
            walkToAny(opId);

            try (Indent indent = Debug.logAndIndent("walk to op %d", opId)) {
                if (activateCurrent(currentInterval)) {
                    activeAnyList.addToListSortedByFromPositions(currentInterval);
                    intervalMoved(currentInterval, State.Unhandled, State.Active);
                }
            }
        }
        // set currentPosition prior to call of walkTo
        currentPosition = toOpId;

        if (currentPosition <= allocator.maxOpId()) {
            // update unhandled stack intervals
            // updateUnhandledStackIntervals(toOpId);

            // call walkTo if still in range
            walkToFixed(State.Active, toOpId);
            walkToFixed(State.Inactive, toOpId);
            walkToAny(toOpId);
        }
    }

    private static void intervalMoved(IntervalHint interval, State from, State to) {
        // intervalMoved() is called whenever an interval moves from one interval list to another.
        // In the implementation of this method it is prohibited to move the interval to any list.
        if (Debug.isLogEnabled()) {
            Debug.log("interval moved from %s to %s: %s", from, to, interval.logString());
        }
    }
}
