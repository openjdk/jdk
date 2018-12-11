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

/**
 * Represents a range of integers from a start (inclusive) to an end (exclusive.
 */
public final class Range {

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
    public Range next;

    boolean intersects(Range r) {
        return intersectsAt(r) != -1;
    }

    /**
     * Creates a new range.
     *
     * @param from the start of the range, inclusive
     * @param to the end of the range, exclusive
     * @param next link to the next range in a linked list
     */
    Range(int from, int to, Range next) {
        this.from = from;
        this.to = to;
        this.next = next;
    }

    public boolean isEndMarker() {
        assert from != Integer.MAX_VALUE || (to == Integer.MAX_VALUE && next == null);
        return from == Integer.MAX_VALUE;
    }

    int intersectsAt(Range other) {
        Range r1 = this;
        Range r2 = other;

        assert r2 != null : "null ranges not allowed";
        assert !r1.isEndMarker() && !r2.isEndMarker() : "empty ranges not allowed";

        do {
            if (r1.from < r2.from) {
                if (r1.to <= r2.from) {
                    r1 = r1.next;
                    if (r1.isEndMarker()) {
                        return -1;
                    }
                } else {
                    return r2.from;
                }
            } else {
                if (r2.from < r1.from) {
                    if (r2.to <= r1.from) {
                        r2 = r2.next;
                        if (r2.isEndMarker()) {
                            return -1;
                        }
                    } else {
                        return r1.from;
                    }
                } else { // r1.from() == r2.from()
                    if (r1.from == r1.to) {
                        r1 = r1.next;
                        if (r1.isEndMarker()) {
                            return -1;
                        }
                    } else {
                        if (r2.from == r2.to) {
                            r2 = r2.next;
                            if (r2.isEndMarker()) {
                                return -1;
                            }
                        } else {
                            return r1.from;
                        }
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
