/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jcmd;

import java.util.LinkedList;
import java.util.Random;

/**
 * @test
 * @summary Test dumping with path-to-gc-roots and DFS only, excercise the array chunking path
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 *
 * @run main/othervm -Xmx1g jdk.jfr.jcmd.TestJcmdDumpPathToGCRootsDFSArrayChunking
 */
public class TestJcmdDumpPathToGCRootsDFSArrayChunking extends TestJcmdDumpPathToGCRootsDFSBase {

    // Tests that array chunking works correctly. We create an object array; link a second object array
    // into it at its middle; link a third object array into the second at its end; fill the third array with
    // many objects. GC root search should walk successfully through the middle of the first and the end of
    // the second to the third array and sample a good portion of its objects.

    private static final int TOTAL_OBJECTS = 10_000_000;
    private static final int arrayChunkSize = 64; // keep in sync with dfsClosure.cpp
    private Object[] leak;

    @Override
    protected final void buildLeak() {
        final int arraySize = (arrayChunkSize * 10) + arrayChunkSize / 2;
        Object[] first = new Object[arraySize];
        Object[] second = new Object[arraySize];
        Object[] third = new Object[TOTAL_OBJECTS];
        for (int i = 0; i < third.length; i++) {
            third[i] = new Object();
        }
        second[second.length - 1] = third;
        first[first.length / 2] = second;
        leak = first;
    }

    protected final void clearLeak() {
        leak = null;
        System.gc();
    }

    public static void main(String[] args) throws Exception {
        new TestJcmdDumpPathToGCRootsDFSArrayChunking().testDump("TestJcmdDumpPathToGCRootsDFSArrayChunking", 30);
    }

}
