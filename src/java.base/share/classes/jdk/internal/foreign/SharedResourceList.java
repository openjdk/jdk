/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.MemorySessionImpl.ResourceList;
import jdk.internal.foreign.MemorySessionImpl.ResourceList.ResourceCleanup;
import jdk.internal.invoke.MhUtil;


/**
 * A shared resource list; this implementation has to handle add vs. add races, as well as add vs. cleanup races.
 */
class SharedResourceList extends ResourceList {

    static final VarHandle FST = MhUtil.findVarHandle(
            MethodHandles.lookup(), ResourceList.class, "fst", ResourceCleanup.class);

    @Override
    void add(ResourceCleanup cleanup) {
        while (true) {
            ResourceCleanup prev = (ResourceCleanup) FST.getVolatile(this);
            if (prev == ResourceCleanup.CLOSED_LIST) {
                // too late
                throw MemorySessionImpl.alreadyClosed();
            }
            cleanup.next = prev;
            if (FST.compareAndSet(this, prev, cleanup)) {
                return; //victory
            }
            // keep trying
        }
    }

    void cleanup() {
        // At this point we are only interested about add vs. close races - not close vs. close
        // (because MemorySessionImpl::justClose ensured that this thread won the race to close the session).
        // So, the only "bad" thing that could happen is that some other thread adds to this list
        // while we're closing it.
        if (FST.getAcquire(this) != ResourceCleanup.CLOSED_LIST) {
            //ok now we're really closing down
            ResourceCleanup prev = null;
            while (true) {
                prev = (ResourceCleanup) FST.getVolatile(this);
                // no need to check for DUMMY, since only one thread can get here!
                if (FST.compareAndSet(this, prev, ResourceCleanup.CLOSED_LIST)) {
                    break;
                }
            }
            cleanup(prev);
        } else {
            throw MemorySessionImpl.alreadyClosed();
        }
    }
}
