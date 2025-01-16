/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

/**
 * An object that interrupts a thread blocked in an I/O operation.
 */

public interface Interruptible {

    /**
     * Invoked by Thread.interrupt when the given Thread is interrupted. Thread.interrupt
     * invokes this method while holding the given Thread's interrupt lock. This method
     * is also invoked by AbstractInterruptibleChannel when beginning an I/O operation
     * with the current thread's interrupt status set. This method must not block.
     */
    void interrupt(Thread target);

    /**
     * Invoked by Thread.interrupt after releasing the Thread's interrupt lock.
     * It may also be invoked by AbstractInterruptibleChannel or AbstractSelector when
     * beginning an I/O operation with the current thread's interrupt status set, or at
     * the end of an I/O operation when any thread doing I/O on the channel (or selector)
     * has been interrupted. This method closes the channel (or wakes up the Selector) to
     * ensure that AsynchronousCloseException or ClosedByInterruptException is thrown.
     * This method is required to be idempotent.
     */
    void postInterrupt();

}
