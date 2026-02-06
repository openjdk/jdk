/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @requires os.arch=="aarch64" | os.arch=="riscv64" | os.arch=="x86_64" | os.arch=="amd64"
 * @requires vm.gc.Parallel
 * @requires vm.compiler2.enabled
 * @summary Check LazyConstant folding
 * @modules java.base/jdk.internal.lang
 * @library /test/lib /
 * @enablePreview
 * @run driver compiler.c2.irTests.stable.LazyConstantTest
 */

package compiler.c2.irTests.stable;

import compiler.lib.ir_framework.*;
import jdk.internal.lang.LazyConstantImpl;

import java.util.Map;

public class LazyConstantTest {

    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.addTestClassesToBootClassPath();
        tf.addFlags(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:CompileThreshold=100",
            "-XX:-TieredCompilation",
            "-XX:+UseParallelGC",
            "--add-opens", "java.base/jdk.internal.lang=ALL-UNNAMED"
        );
        tf.start();
    }

    static final LazyConstantImpl<Integer> LAZY_42 = LazyConstantImpl.ofLazy(() -> 42);

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int foldLazyConstant() {
        // Access should be folded.
        // No barriers expected for a folded access (as opposed to a non-folded).
        return LAZY_42.get();
    }

}
