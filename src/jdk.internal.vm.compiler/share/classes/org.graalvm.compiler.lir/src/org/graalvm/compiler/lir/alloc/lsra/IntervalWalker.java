/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.alloc.lsra;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterBinding;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterBindingLists;
import org.graalvm.compiler.lir.alloc.lsra.Interval.State;

/**
 */
public class IntervalWalker {

    protected final LinearScan allocator;

    /**
     * Sorted list of intervals, not live before the current position.
     */
    protected RegisterBindingLists unhandledLists;

    /**
     * Sorted list of intervals, live at the current position.
     */
    protected RegisterBindingLists activeLists;

    /**
     * Sorted list of intervals in a life time hole at the current position.
     */
    protected RegisterBindingLists inactiveLists;

    /**
     * The current position (intercept point through the intervals).
     */
    protected int currentPosition;

    /**
     * The binding of the current interval being processed.
     */
    protected RegisterBinding currentBinding;

    /**
     * Processes the {@code currentInterval} interval in an attempt to allocate a physical register
     * to it and thus allow it to be moved to a list of {@linkplain #activeLists active} intervals.
     *
     * @return {@code true} if a register was allocated to the {@code currentInterval} interval
     */
    protected boolean activateCurrent(@SuppressWarnings({"unused"}) Interval currentInterval) {
        return true;
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
    IntervalWalker(LinearScan allocator, Interval unhandledFixed, Interval unhandledAny) {
        this.allocator = allocator;

        unhandledLists = new RegisterBindingLists(unhandledFixed, unhandledAny, allocator.intervalEndMarker);
        activeLists = new RegisterBindingLists(allocator.intervalEndMarker, allocator.intervalEndMarker, allocator.intervalEndMarker);
        inactiveLists = new RegisterBindingLists(allocator.intervalEndMarker, allocator.intervalEndMarker, allocator.intervalEndMarker);
        currentPosition = -1;
    }

    protected void removeFromList(Interval interval) {
        if (interval.state == State.Active) {
            activeLists.remove(RegisterBinding.Any, interval);
        } else {
            assert interval.state == State.Inactive : "invalid state";
            inactiveLists.remove(RegisterBinding.Any, interval);
        }
    }

    private void walkTo(State state, int from) {
        assert state == State.Active || state == State.Inactive : "wrong state";
        for (RegisterBinding binding : RegisterBinding.VALUES) {
            walkTo(state, from, binding);
        }
    }

    private void walkTo(State state, int from, RegisterBinding binding) {
        Interval prevprev = null;
        Interval prev = (state == State.Active) ? activeLists.get(binding) : inactiveLists.get(binding);
        Interval next = prev;
        while (next.currentFrom() <= from) {
            Interval cur = next;
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
                        activeLists.set(binding, next);
                    } else {
                        inactiveLists.set(binding, next);
                    }
                } else {
                    prevprev.next = next;
                }
                prev = next;
                Interval.State newState;
                if (cur.currentAtEnd()) {
                    // move to handled state (not maintained as a list)
                    newState = State.Handled;
                    cur.state = newState;
                } else {
                    if (cur.currentFrom() <= from) {
                        // sort into active list
                        activeLists.addToListSortedByCurrentFromPositions(binding, cur);
                        newState = State.Active;
                    } else {
                        // sort into inactive list
                        inactiveLists.addToListSortedByCurrentFromPositions(binding, cur);
                        newState = State.Inactive;
                    }
                    cur.state = newState;
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
     * Get the next interval from {@linkplain #unhandledLists} which starts before or at
     * {@code toOpId}. The returned interval is removed and {@link #currentBinding} is set.
     *
     * @postcondition all intervals in {@linkplain #unhandledLists} start after {@code toOpId}.
     *
     * @return The next interval or null if there is no {@linkplain #unhandledLists unhandled}
     *         interval at position {@code toOpId}.
     */
    private Interval nextInterval(int toOpId) {
        RegisterBinding binding;
        Interval any = unhandledLists.any;
        Interval fixed = unhandledLists.fixed;

        if (!any.isEndMarker()) {
            // intervals may start at same position . prefer fixed interval
            binding = !fixed.isEndMarker() && fixed.from() <= any.from() ? RegisterBinding.Fixed : RegisterBinding.Any;

            assert binding == RegisterBinding.Fixed && fixed.from() <= any.from() || binding == RegisterBinding.Any && any.from() <= fixed.from() : "wrong interval!!!";
            assert any.isEndMarker() || fixed.isEndMarker() || any.from() != fixed.from() ||
                            binding == RegisterBinding.Fixed : "if fixed and any-Interval start at same position, fixed must be processed first";

        } else if (!fixed.isEndMarker()) {
            binding = RegisterBinding.Fixed;
        } else {
            return null;
        }
        Interval currentInterval = unhandledLists.get(binding);

        if (toOpId < currentInterval.from()) {
            return null;
        }

        currentBinding = binding;
        unhandledLists.set(binding, currentInterval.next);
        currentInterval.next = allocator.intervalEndMarker;
        currentInterval.rewindRange();
        return currentInterval;
    }

    /**
     * Walk up to {@code toOpId}.
     *
     * @postcondition {@link #currentPosition} is set to {@code toOpId}, {@link #activeLists} and
     *                {@link #inactiveLists} are populated and {@link Interval#state}s are up to
     *                date.
     */
    @SuppressWarnings("try")
    protected void walkTo(int toOpId) {
        assert currentPosition <= toOpId : "can not walk backwards";
        for (Interval currentInterval = nextInterval(toOpId); currentInterval != null; currentInterval = nextInterval(toOpId)) {
            int opId = currentInterval.from();

            // set currentPosition prior to call of walkTo
            currentPosition = opId;

            // update unhandled stack intervals
            updateUnhandledStackIntervals(opId);

            // call walkTo even if currentPosition == id
            walkTo(State.Active, opId);
            walkTo(State.Inactive, opId);

            DebugContext debug = allocator.getDebug();
            try (Indent indent = debug.logAndIndent("walk to op %d", opId)) {
                currentInterval.state = State.Active;
                if (activateCurrent(currentInterval)) {
                    activeLists.addToListSortedByCurrentFromPositions(currentBinding, currentInterval);
                    intervalMoved(currentInterval, State.Unhandled, State.Active);
                }
            }
        }
        // set currentPosition prior to call of walkTo
        currentPosition = toOpId;

        if (currentPosition <= allocator.maxOpId()) {
            // update unhandled stack intervals
            updateUnhandledStackIntervals(toOpId);

            // call walkTo if still in range
            walkTo(State.Active, toOpId);
            walkTo(State.Inactive, toOpId);
        }
    }

    private void intervalMoved(Interval interval, State from, State to) {
        // intervalMoved() is called whenever an interval moves from one interval list to another.
        // In the implementation of this method it is prohibited to move the interval to any list.
        DebugContext debug = allocator.getDebug();
        if (debug.isLogEnabled()) {
            debug.log("interval moved from %s to %s: %s", from, to, interval.logString(allocator));
        }
    }

    /**
     * Move {@linkplain #unhandledLists unhandled} stack intervals to
     * {@linkplain IntervalWalker #activeLists active}.
     *
     * Note that for {@linkplain RegisterBinding#Fixed fixed} and {@linkplain RegisterBinding#Any
     * any} intervals this is done in {@link #nextInterval(int)}.
     */
    private void updateUnhandledStackIntervals(int opId) {
        Interval currentInterval = unhandledLists.get(RegisterBinding.Stack);
        while (!currentInterval.isEndMarker() && currentInterval.from() <= opId) {
            Interval next = currentInterval.next;
            if (currentInterval.to() > opId) {
                currentInterval.state = State.Active;
                activeLists.addToListSortedByCurrentFromPositions(RegisterBinding.Stack, currentInterval);
                intervalMoved(currentInterval, State.Unhandled, State.Active);
            } else {
                currentInterval.state = State.Handled;
                intervalMoved(currentInterval, State.Unhandled, State.Handled);
            }
            currentInterval = next;
        }
        unhandledLists.set(RegisterBinding.Stack, currentInterval);
    }

}
