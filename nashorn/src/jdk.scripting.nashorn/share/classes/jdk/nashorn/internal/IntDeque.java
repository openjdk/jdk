/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal;

/**
 * Small helper class for fast int deques
 */
public class IntDeque {
    private int[] deque = new int[16];
    private int nextFree = 0;

    /**
     * Push an int value
     * @param value value
     */
    public void push(final int value) {
        if (nextFree == deque.length) {
            final int[] newDeque = new int[nextFree * 2];
            System.arraycopy(deque, 0, newDeque, 0, nextFree);
            deque = newDeque;
        }
        deque[nextFree++] = value;
    }

    /**
     * Pop an int value
     * @return value
     */
    public int pop() {
        return deque[--nextFree];
    }

    /**
     * Peek
     * @return top value
     */
    public int peek() {
        return deque[nextFree - 1];
    }

    /**
     * Get the value of the top element and increment it.
     * @return top value
     */
    public int getAndIncrement() {
        return deque[nextFree - 1]++;
    }

    /**
     * Decrement the value of the top element and return it.
     * @return decremented top value
     */
    public int decrementAndGet() {
        return --deque[nextFree - 1];
    }

    /**
     * Check if deque is empty
     * @return true if empty
     */
    public boolean isEmpty() {
        return nextFree == 0;
    }
}
