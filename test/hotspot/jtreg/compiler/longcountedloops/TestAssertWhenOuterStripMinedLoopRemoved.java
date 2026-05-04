/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8347040
 * @summary C2: assert(!loop->_body.contains(in)) failed
 * @run main/othervm -XX:-BackgroundCompilation TestAssertWhenOuterStripMinedLoopRemoved
 */


import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class TestAssertWhenOuterStripMinedLoopRemoved {
    static final int COUNT = 100000;
    static final MemorySegment segment = Arena.global().allocate(JAVA_LONG, COUNT);

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    static void test() {
        var i = 0;
        var j = 0;
        while (i < 100000) {
            segment.setAtIndex(JAVA_LONG, i++, 0);
            segment.setAtIndex(JAVA_LONG, j++, 0);
        }
    }
}
