/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test
 * @bug 8333791
 * @requires os.arch=="aarch64" | os.arch=="riscv64" | os.arch=="x86_64" | os.arch=="amd64"
 * @requires vm.gc.Parallel
 * @requires vm.compiler2.enabled
 * @summary Check stable field folding and barriers
 * @modules java.base/jdk.internal.vm.annotation
 * @library /test/lib /
 * @run driver compiler.c2.irTests.stable.StableRefPlainTest
 */

package compiler.c2.irTests.stable;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import jdk.internal.vm.annotation.Stable;

public class StableRefPlainTest {

    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.addTestClassesToBootClassPath();
        tf.addFlags(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:CompileThreshold=100",
            "-XX:-TieredCompilation",
            "-XX:+UseParallelGC"
        );
        tf.start();
    }

    static final Integer INTEGER = 42;

    static class Carrier {
        @Stable
        Integer field;

        @ForceInline
        public Carrier(boolean init) {
            if (init) {
                field = INTEGER;
            }
        }

        @ForceInline
        public void init() {
            field = INTEGER;
        }
    }

    static final Carrier BLANK_CARRIER = new Carrier(false);
    static final Carrier INIT_CARRIER = new Carrier(true);

    @Test
    @IR(counts = { IRNode.LOAD, ">0" })
    @IR(applyIf = {"enable-valhalla", "false"}, failOn = { IRNode.MEMBAR })
    // We have barriers with valhalla from the atomic expansion of the LoadFlatNode
    // Indeed, since the field is not initialized, it is not known to be constant yet,
    // and so, the LoadFlat cannot be expanded non-atomically. We need barriers to synchronize
    // the LoadFlat and potential updates to sub-field of the flatten field.
    @IR(applyIfAnd = {"UseFieldFlattening", "true", "enable-valhalla", "true"}, counts = { IRNode.MEMBAR, ">0" })
    static int testNoFold() {
        // Access should not be folded.
        Integer i = BLANK_CARRIER.field;
        return i != null ? i : 0;
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFold() {
        // Access should be completely folded.
        Integer i = INIT_CARRIER.field;
        return i != null ? i : 0;
    }

    @Test
    @IR(counts = { IRNode.MEMBAR_STORESTORE, "1" })
    static Carrier testConstructorBlankInit() {
        // Only the header barrier.
        return new Carrier(false);
    }

    @Test
    @IR(counts = { IRNode.MEMBAR_STORESTORE, "1" })
    static Carrier testConstructorFullInit() {
        // Only the header barrier.
        return new Carrier(true);
    }

    @Test
    @IR(applyIf = {"enable-valhalla", "false"}, failOn = { IRNode.MEMBAR })
    // We have barriers from the atomic expansion of the StoreFlatNode. Store is not eliminated with
    // or without Valhalla, but Valhalla's StoreFlat require barriers.
    @IR(applyIfAnd = {"UseFieldFlattening", "true", "enable-valhalla", "true"}, counts = { IRNode.MEMBAR, ">0" })
    static void testMethodInit() {
        INIT_CARRIER.init();
    }

}
