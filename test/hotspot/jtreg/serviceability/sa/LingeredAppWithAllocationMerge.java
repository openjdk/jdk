/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.apps.LingeredApp;

// Derived from test/hotspot/jtreg/compiler/c2/TestReduceAllocationAndHeapDump.java
public class LingeredAppWithAllocationMerge extends LingeredApp {
    // Helper class
    static class Point {
        public int x;

        public Point(int xx) {
            this.x = xx;
        }
    }


    public static Point p = new Point(0);

    public static void main(String[] args) {
        for (int i = 0; i < 5000; i++) {
            testIt(i, args);
        }
    }

    public static void testIt(int i, String[] args) {
        Point p = (i % 2 == 0) ? new Point(i) : new Point(i);

        dummy(i, args);

        if (i < 5000) {
            dummy(i, args);
        } else {
            dummy(p.x + i, args);
        }
    }

    public static void dummy(int x, String[] args) {
        if (x > 4900) {
            LingeredApp.main(args);
            throw new InternalError("should never return");
        }
    }
}
