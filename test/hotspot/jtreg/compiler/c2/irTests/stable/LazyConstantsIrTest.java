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

/*
 * @test
 * @requires os.arch=="aarch64" | os.arch=="riscv64" | os.arch=="x86_64" | os.arch=="amd64"
 * @requires vm.gc.Parallel
 * @requires vm.compiler2.enabled
 * @summary Check LazyConstant and lazy collection folding
 * @modules java.base/jdk.internal.lang
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.c2.irTests.stable.LazyConstantsIrTest
 */

package compiler.c2.irTests.stable;

import compiler.lib.ir_framework.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LazyConstantsIrTest {

    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.addTestClassesToBootClassPath();
        tf.addFlags(
                "--enable-preview",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:CompileThreshold=100",
                "-XX:-TieredCompilation",
                "-XX:+UseParallelGC"
        );
        tf.start();
    }

    static final int THE_VALUE = 42;

    static final LazyConstant<Integer> LAZY_CONSTANT = LazyConstant.of(() -> THE_VALUE);
    static final List<Integer> LAZY_LIST = List.ofLazy(1, _ -> THE_VALUE);
    static final Set<Integer> LAZY_SET = Set.ofLazy(Set.of(THE_VALUE), _ -> true);
    static final Map<Integer, Integer> LAZY_MAP = Map.ofLazy(Set.of(0), _ -> THE_VALUE);

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int foldLazyConstant() {
        // Access should be folded.
        // No barriers expected for a folded access (as opposed to a non-folded).
        return LAZY_CONSTANT.get();
    }

    @Test
    @IR(failOn = { IRNode.LOAD /*, IRNode.MEMBAR */}) // Reenable when 8377541 is fixed
    static int foldLazyList() {
        // Access should be folded.
        // No barriers expected for a folded access (as opposed to a non-folded).
        return LAZY_LIST.get(0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD /*, IRNode.MEMBAR */}) // Reenable when 8377541 is fixed
    static boolean foldLazySet() {
        // Access should be folded.
        // No barriers expected for a folded access (as opposed to a non-folded).
        return LAZY_SET.contains(THE_VALUE);
    }

    @Test
    @IR(failOn = { IRNode.LOAD /*, IRNode.MEMBAR */}) // Reenable when 8377541 is fixed
    static int foldLazyMap() {
        // Access should be folded.
        // No barriers expected for a folded access (as opposed to a non-folded).
        return LAZY_MAP.get(0);
    }

}