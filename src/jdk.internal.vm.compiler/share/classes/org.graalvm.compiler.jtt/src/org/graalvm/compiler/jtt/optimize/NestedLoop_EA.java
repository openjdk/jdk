/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import java.util.ListIterator;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

public class NestedLoop_EA extends JTTTest {

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        ListIterator<BasePhase<? super HighTierContext>> position = suites.getHighTier().findPhase(PartialEscapePhase.class);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        // incremental canonicalizer of PEA is missing some important canonicalization (TODO?)
        position.add(canonicalizer);
        position.add(new PartialEscapePhase(true, canonicalizer, options));
        return suites;
    }

    static class Frame {
        Object[] objects = new Object[10];
    }

    static final int RESULT_SLOT = 0;
    static final int K_SLOT = 1;
    static final int I_SLOT = 2;
    static final int ARG_SLOT = 3;
    static final int STACK_BASE = 4;

    static class Pointer {
        public int sp = STACK_BASE;
    }

    public static int simpleLoop(int arg) {
        Frame f = new Frame();
        Pointer p = new Pointer();
        f.objects[ARG_SLOT] = arg;
        f.objects[RESULT_SLOT] = 0;
        f.objects[K_SLOT] = 0;
        for (; (int) f.objects[K_SLOT] < (int) f.objects[ARG_SLOT];) {

            f.objects[RESULT_SLOT] = (int) f.objects[RESULT_SLOT] + 5;

            f.objects[++p.sp] = f.objects[K_SLOT];
            f.objects[++p.sp] = 1;
            int result = (int) f.objects[p.sp] + (int) f.objects[p.sp - 1];
            p.sp--;
            f.objects[p.sp] = result;
            f.objects[K_SLOT] = (int) f.objects[p.sp];
            p.sp--;
        }
        return (int) f.objects[RESULT_SLOT];
    }

    @Test
    public void run0() throws Throwable {
        runTest("simpleLoop", 5);
    }

    public static int nestedLoop(int arg) {
        Frame f = new Frame();
        Pointer p = new Pointer();
        f.objects[ARG_SLOT] = arg;
        f.objects[RESULT_SLOT] = 0;
        f.objects[K_SLOT] = 0;
        for (; (int) f.objects[K_SLOT] < (int) f.objects[ARG_SLOT];) {

            f.objects[I_SLOT] = 0;
            for (; (int) f.objects[I_SLOT] < (int) f.objects[ARG_SLOT];) {
                f.objects[RESULT_SLOT] = (int) f.objects[RESULT_SLOT] + 5;

                f.objects[++p.sp] = f.objects[I_SLOT];
                f.objects[++p.sp] = 1;
                int result = (int) f.objects[p.sp] + (int) f.objects[p.sp - 1];
                p.sp--;
                f.objects[p.sp] = result;
                f.objects[I_SLOT] = (int) f.objects[p.sp];
                p.sp--;
            }

            f.objects[++p.sp] = f.objects[K_SLOT];
            f.objects[++p.sp] = 1;
            int result = (int) f.objects[p.sp] + (int) f.objects[p.sp - 1];
            p.sp--;
            f.objects[p.sp] = result;
            f.objects[K_SLOT] = (int) f.objects[p.sp];
            p.sp--;
        }
        return (int) f.objects[RESULT_SLOT];
    }

    @Test
    public void run1() throws Throwable {
        runTest("nestedLoop", 5);
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        assert graph.getNodes().filter(CommitAllocationNode.class).count() == 0 : "all allocations should be virtualized";
        return true;
    }
}
