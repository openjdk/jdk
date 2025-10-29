/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.intrinsics;

import compiler.lib.ir_framework.*;

import jdk.jfr.Event;
import jdk.jfr.Recording;

/**
 * @test
 * @summary Tests that the getEventWriter call to write_checkpoint correctly
 *          reports returning an oop
 * @bug 8347463
 * @requires vm.hasJFR
 * @library /test/lib /
 * @run driver compiler.intrinsics.TestReturnOopSetForJFRWriteCheckpoint
 */
public class TestReturnOopSetForJFRWriteCheckpoint {

    private static class TestEvent extends Event {
    }

    public static void main(String... args) {
        TestFramework.run();
    }

    // Crash was due to the return_oop field not being set
    // for the write_checkpoint call. Instead of explicitly checking for
    // it, we look for an non-void return type (which comes hand-in-hand
    // with the return_oop information).
    @Test
    @IR(failOn = { IRNode.STATIC_CALL_OF_METHOD, "write_checkpoint.*void"})
    public void testWriteCheckpointReturnType() {
        try (Recording r = new Recording()) {
            r.start();
            emitEvent();
        }
    }

    @ForceInline
    public void emitEvent() {
        TestEvent t = new TestEvent();
        t.commit();
    }
}
