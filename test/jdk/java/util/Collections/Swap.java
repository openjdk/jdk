/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4323074
 * @summary Basic test for newly public swap algorithm
 * @author  Josh Bloch
 * @library /test/lib
 */

import jdk.test.lib.valueclass.VClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Swap {
    static final int SIZE = 100;

    public static void main(String[] args) throws Exception {
        test(Boolean.TRUE, Boolean.FALSE);
        test(new VClass(1, new int[] { 1 }), new VClass(0, new int[] { 0 }));
    }

    static <T> void test(T marked, T unmarked) throws Exception {
        List<T> l = new ArrayList<>(Collections.nCopies(SIZE, unmarked));
        l.set(0, marked);
        for (int i = 0; i < SIZE - 1; i++)
            Collections.swap(l, i, i + 1);

        List<T> golden = new ArrayList<>(Collections.nCopies(SIZE, unmarked));
        golden.set(SIZE - 1, marked);
        if (!l.equals(golden))
            throw new RuntimeException("Wrong result");
    }
}
