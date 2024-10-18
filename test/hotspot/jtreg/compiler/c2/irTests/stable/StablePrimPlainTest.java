/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @run driver compiler.c2.irTests.stable.StablePrimPlainTest
 */

package compiler.c2.irTests.stable;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import jdk.internal.vm.annotation.Stable;

public class StablePrimPlainTest {

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

    static class Carrier {
        @Stable
        int field;

        @ForceInline
        public Carrier(boolean init) {
            if (init) {
                field = 42;
            }
        }

        @ForceInline
        public void init() {
            field = 42;
        }
    }

    static final Carrier BLANK_CARRIER = new Carrier(false);
    static final Carrier INIT_CARRIER = new Carrier(true);

    @Test
    @IR(counts = { IRNode.LOAD, "1" })
    @IR(failOn = { IRNode.MEMBAR })
    static int testNoFold() {
        // Access should not be folded.
        // No barriers expected for plain fields.
        return BLANK_CARRIER.field;
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFold() {
        // Access should be completely folded.
        return INIT_CARRIER.field;
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
    @IR(failOn = { IRNode.MEMBAR })
    static void testMethodInit() {
        // Primitive inits have no membars.
        INIT_CARRIER.init();
    }

}
