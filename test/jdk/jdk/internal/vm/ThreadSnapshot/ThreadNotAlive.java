/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test jdk.internal.vm.ThreadSnapshot.of(Thread) when thread is not alive
 * @modules java.base/jdk.internal.vm
 * @compile/module=java.base jdk/internal/vm/Helper.java
 * @run junit ThreadNotAlive
 */

import jdk.internal.vm.Helper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadNotAlive {

    @Test
    void unstartedPlatformThread() {
        Thread t = Thread.ofPlatform().unstarted(() -> { });
        assertFalse(Helper.isAlive(t));
    }

    @Test
    void terminatedPlatformThread() throws InterruptedException {
        Thread t = Thread.ofPlatform().start(() -> { });
        t.join();
        assertFalse(Helper.isAlive(t));
    }

    @Test
    void unstartedVirtualhread() {
        Thread t = Thread.ofVirtual().unstarted(() -> { });
        assertFalse(Helper.isAlive(t));
    }

    @Test
    void terminatedVirtualThread() throws InterruptedException {
        Thread t = Thread.ofVirtual().start(() -> { });
        t.join();
        assertFalse(Helper.isAlive(t));
    }

}
