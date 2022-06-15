/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;

import jdk.internal.vm.annotation.ForceInline;

/**
 * A confined session state, which features an owner thread. Because of this restriction, acquire and release
 * can be implemented cheaply, with a plain field update. Closing the state is also cheap (since the liveness
 * bit cannot be updated concurrently from other threads). Some extra complexity is required to support release
 * operations triggered by threads other than the owner thread, which we support.
 */
final class ConfinedSessionState extends MemorySessionImpl.State {

    private int asyncReleaseCount = 0;

    static final VarHandle ASYNC_RELEASE_COUNT;

    static {
        try {
            ASYNC_RELEASE_COUNT = MethodHandles.lookup().findVarHandle(ConfinedSessionState.class, "asyncReleaseCount", int.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    ConfinedSessionState(Thread owner, Cleaner cleaner) {
        super(owner, new ConfinedList(), cleaner);
    }

    @Override
    boolean isAlive() {
        return state != CLOSED;
    }

    @Override
    @ForceInline
    public void acquire() {
        checkValidStateWrapException();
        if (state == MAX_FORKS) {
            throw tooManyAcquires();
        }
        state++;
    }

    @Override
    @ForceInline
    public void release() {
        if (Thread.currentThread() == owner) {
            state--;
        } else {
            // It is possible to end up here in two cases: this session was kept alive by some other confined session
            // which is implicitly released (in which case the release call comes from the cleaner thread). Or,
            // this session might be kept alive by a shared session, which means the release call can come from any
            // thread.
            ASYNC_RELEASE_COUNT.getAndAdd(this, 1);
        }
    }

    void justClose() {
        checkValidStateWrapException();
        if (state == 0 || state - ((int)ASYNC_RELEASE_COUNT.getVolatile(this)) == 0) {
            state = CLOSED;
        } else {
            throw alreadyAcquired(state);
        }
    }

    /**
     * A confined resource list; no races are possible here.
     */
    static final class ConfinedList extends ResourceList {
        @Override
        void add(ResourceCleanup cleanup) {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                cleanup.next = fst;
                fst = cleanup;
            } else {
                throw alreadyClosed();
            }
        }

        @Override
        void cleanup() {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                ResourceCleanup prev = fst;
                fst = ResourceCleanup.CLOSED_LIST;
                cleanup(prev);
            } else {
                throw alreadyClosed();
            }
        }
    }
}
