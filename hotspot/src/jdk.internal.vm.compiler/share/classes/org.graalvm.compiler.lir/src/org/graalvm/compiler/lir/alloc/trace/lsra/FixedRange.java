/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Represents a range of integers from a start (inclusive) to an end (exclusive).
 */
final class FixedRange {

    public static final FixedRange EndMarker = new FixedRange(Integer.MAX_VALUE, Integer.MAX_VALUE, null);

    /**
     * The start of the range, inclusive.
     */
    public int from;

    /**
     * The end of the range, exclusive.
     */
    public int to;

    /**
     * A link to allow the range to be put into a singly linked list.
     */
    public FixedRange next;

    boolean intersects(TraceInterval i) {
        return intersectsAt(i) != -1;
    }

    /**
     * Creates a new range.
     *
     * @param from the start of the range, inclusive
     * @param to the end of the range, exclusive
     * @param next link to the next range in a linked list
     */
    FixedRange(int from, int to, FixedRange next) {
        this.from = from;
        this.to = to;
        this.next = next;
    }

    int intersectsAt(TraceInterval other) {
        FixedRange range = this;
        assert other != null : "null ranges not allowed";
        assert range != EndMarker && other != TraceInterval.EndMarker : "empty ranges not allowed";
        int intervalFrom = other.from();
        int intervalTo = other.to();

        do {
            if (range.from < intervalFrom) {
                if (range.to <= intervalFrom) {
                    range = range.next;
                    if (range == EndMarker) {
                        return -1;
                    }
                } else {
                    return intervalFrom;
                }
            } else {
                if (intervalFrom < range.from) {
                    if (intervalTo <= range.from) {
                        return -1;
                    }
                    return range.from;
                } else {
                    assert range.from == intervalFrom;
                    if (range.from == range.to) {
                        range = range.next;
                        if (range == EndMarker) {
                            return -1;
                        }
                    } else {
                        if (intervalFrom == intervalTo) {
                            return -1;
                        }
                        return range.from;
                    }
                }
            }
        } while (true);
    }

    @Override
    public String toString() {
        return "[" + from + ", " + to + "]";
    }
}
