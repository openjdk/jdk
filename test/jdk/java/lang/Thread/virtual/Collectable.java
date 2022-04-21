/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that virtual threads are GC'ed
 * @compile --enable-preview -source ${jdk.version} Collectable.java
 * @run testng/othervm --enable-preview Collectable
 */

import java.lang.ref.WeakReference;
import java.util.concurrent.locks.LockSupport;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class Collectable {

    /**
     * Test that an unstarted virtual thread can be GC"ed.
     */
    @Test
    public void testUnstartedThread() {
        var thread = Thread.ofVirtual().unstarted(() -> { });
        var ref = new WeakReference<>(thread);
        thread = null;
        waitUntilCleared(ref);
    }

    /**
     * Test that a terminated virtual thread can be GC'ed.
     */
    @Test
    public void testTerminatedThread() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        thread.join();
        var ref = new WeakReference<>(thread);
        thread = null;
        waitUntilCleared(ref);
    }

    private static void waitUntilCleared(WeakReference<?> ref) {
        while (ref.get() != null) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignore) { }
        }
    }
}
