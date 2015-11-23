/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8140450
 * @summary Basic test for the StackWalker::walk method
 * @run testng Basic
 */

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.stream.Collectors;
import static java.lang.StackWalker.Option.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class Basic {
    private static boolean verbose = false;

    @DataProvider(name = "stackDepths")
    public static Object[][] stackDepths() {
        return new Object[][] {
                { new int[] { 12 },  new int[] { 4, 8, 12}      },
                { new int[] { 18 },  new int[] { 8, 16, 20}     },
                { new int[] { 32 },  new int[] { 16, 32, 64}    },
        };
    }

    /**
     * For a stack of a given depth, it creates a StackWalker with an estimate.
     * Test walking different number of frames
     */
    @Test(dataProvider = "stackDepths")
    public static void test(int[] depth, int[] estimates) {
        Basic test = new Basic(depth[0]);
        for (int estimate : estimates) {
            test.walk(estimate);
        }
    }

    private final int depth;
    Basic(int depth) {
        this.depth = depth;
    }

    /*
     * Setup a stack builder with the expected stack depth
     * Walk the stack and count the frames.
     */
    void walk(int estimate) {
        int limit = Math.min(depth, 16);
        List<StackFrame> frames = new StackBuilder(depth, limit).build();
        System.out.format("depth=%d estimate=%d expected=%d walked=%d%n",
                          depth, estimate, limit, frames.size());
        assertEquals(limit, frames.size());
    }

    class StackBuilder {
        private final int stackDepth;
        private final int limit;
        private int depth = 0;
        private List<StackFrame> result;
        StackBuilder(int stackDepth, int limit) {
            this.stackDepth = stackDepth; // build method;
            this.limit = limit;
        }
        List<StackFrame> build() {
            trace("build");
            m1();
            return result;
        }
        void m1() {
            trace("m1");
            m2();
        }
        void m2() {
            trace("m2");
            m3();
        }
        void m3() {
            trace("m3");
            m4();
        }
        void m4() {
            trace("m4");
            int remaining = stackDepth-depth-1;
            if (remaining >= 4) {
                m1();
            } else {
                filler(remaining);
            }
        }
        void filler(int i) {
            trace("filler");
            if (i == 0)
                walk();
            else
                filler(--i);
        }

        void walk() {
            StackWalker walker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
            result = walker.walk(s -> s.limit(limit).collect(Collectors.toList()));
        }
        void trace(String methodname) {
            ++depth;
            if (verbose)
                System.out.format("%2d: %s%n", depth, methodname);
        }
    }

    static void assertEquals(int x, int y) {
        if (x != y) {
            throw new RuntimeException(x + " != " + y);
        }
    }
}
