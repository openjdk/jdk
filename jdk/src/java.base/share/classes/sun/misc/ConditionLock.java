/*
 * Copyright (c) 1994, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

/**
 * ConditionLock is a Lock with a built in state variable.  This class
 * provides the ability to wait for the state variable to be set to a
 * desired value and then acquire the lock.<p>
 *
 * The lockWhen() and unlockWith() methods can be safely intermixed
 * with the lock() and unlock() methods. However if there is a thread
 * waiting for the state variable to become a particular value and you
 * simply call Unlock(), that thread will not be able to acquire the
 * lock until the state variable equals its desired value. <p>
 *
 * @author      Peter King
 */
public final
class ConditionLock extends Lock {
    private int state = 0;

    /**
     * Creates a ConditionLock.
     */
    public ConditionLock () {
    }

    /**
     * Creates a ConditionLock in an initialState.
     */
    public ConditionLock (int initialState) {
        state = initialState;
    }

    /**
     * Acquires the lock when the state variable equals the desired state.
     *
     * @param desiredState the desired state
     * @exception  java.lang.InterruptedException if any thread has
     *               interrupted this thread.
     */
    public synchronized void lockWhen(int desiredState)
        throws InterruptedException
    {
        while (state != desiredState) {
            wait();
        }
        lock();
    }

    /**
     * Releases the lock, and sets the state to a new value.
     * @param newState the new state
     */
    public synchronized void unlockWith(int newState) {
        state = newState;
        unlock();
    }
}
