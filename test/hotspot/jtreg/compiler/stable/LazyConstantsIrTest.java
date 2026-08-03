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
 * @summary Check LazyConstant and lazy collection constant folding
 * @modules java.base/jdk.internal.lang
 * @library /test/lib /
 * @enablePreview
 * @run main ${test.main.class}
 */

package compiler.stable;

import compiler.lib.ir_framework.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LazyConstantsIrTest {

    public static void main(String[] args) {
        new TestFramework()
                .addTestClassesToBootClassPath()
                .addFlags(
                        "--enable-preview",
                        "-XX:+UnlockExperimentalVMOptions")
                .addCrossProductScenarios(
                        Set.of("-XX:+TieredCompilation", "-XX:-TieredCompilation"))
                .setDefaultWarmup(5000)
                .start();
    }

    static final int THE_VALUE = 42;

    static final LazyConstant<Integer> LAZY_CONSTANT = LazyConstant.of(() -> THE_VALUE);
    static final List<Integer> LAZY_LIST = List.ofLazy(1, _ -> THE_VALUE);
    static final Set<Integer> LAZY_SET = Set.ofLazy(Set.of(THE_VALUE), _ -> true);
    static final Map<Integer, Integer> LAZY_MAP = Map.ofLazy(Set.of(0), _ -> THE_VALUE);


    // For all tests:
    //  * Access should be folded.
    //  * No barriers expected for a folded access (as opposed to a non-folded).

    // Lazy constant

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int foldLazyConstantGet() {
        return LAZY_CONSTANT.get();
    }

    // Lazy list

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static int foldLazyListGet() {
        return LAZY_LIST.get(0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static int foldLazyListSize() {
        return LAZY_LIST.size();
    }

    // Lazy map

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static int foldLazyMapGet() {
        return LAZY_MAP.get(0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static int foldLazyMapSize() {
        return LAZY_MAP.size();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static int foldLazyMapHashCode() {
        return LAZY_MAP.hashCode();
    }

    // Lazy set

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static boolean foldLazySetContains() {
        return LAZY_SET.contains(THE_VALUE);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static int foldLazySetHashCode() {
        return LAZY_SET.hashCode();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR})
    static int foldLazySetSize() {
        return LAZY_SET.size();
    }

}
