/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify that no deadlock will happen during JVM shutdown hooks
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm/timeout=10  jdk.jfr.api.recording.deadlock.TestShutdownDeadLock
 */
package jdk.jfr.api.recording.deadlock;

import jdk.jfr.Recording;
import jdk.test.lib.Asserts;

public class TestShutdownDeadLock {

    public static void main(String[] args) {
        // Initialize JFR subsystem
        Recording r = new Recording();
        r.start();
        r.stop();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Exception thrownException = null;
            try {
                Thread.sleep(100);
                new Recording().start();
            } catch (Exception e) {
                thrownException = e;
            }
            Asserts.assertNotNull(thrownException);
            Asserts.assertEquals(thrownException.getClass(), IllegalStateException.class);
            Asserts.assertEquals(thrownException.getMessage(), "Can not start new recording after VM shutdown");
        }));

        System.out.println("Exiting");
    }
}