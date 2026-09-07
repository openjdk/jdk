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
 * @bug 4323074
 * @summary Basic test for new replaceAll algorithm
 * @library /test/lib
 */

import jdk.test.lib.valueclass.VClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class ReplaceAll {
    static final int SIZE = 20;

    public static void main(String[] args) throws Exception {
        List[] a = {new ArrayList(), new LinkedList(), new Vector()};

        for (int i=0; i<a.length; i++) {
            List lst = a[i];
            for (int j=1; j<=SIZE; j++)
                lst.add(new Integer(j % 3));
            List goal = Collections.nCopies(SIZE, "*");

            for (int j=0; j<3; j++) {
                List before = new ArrayList(lst);
                if (!Collections.replaceAll(lst, new Integer(j), "*"))
                    throw new Exception("False return value: "+i+", "+j);
                if (lst.equals(before))
                    throw new Exception("Unchanged: "+i+", "+j+", "+": "+lst);
                if (lst.equals(goal) != (j==2))
                    throw new Exception("Wrong change:"+i+", "+j);
            }
            if (Collections.replaceAll(lst, "love", "hate"))
                throw new Exception("True return value: "+i);
        }

        List<VClass> values = new ArrayList<>();
        for (int j = 1; j <= SIZE; j++) values.add(new VClass(j % 3, new int[] { j % 3 }));
        if (!Collections.replaceAll(values, new VClass(1, new int[] { 1 }), new VClass(99, new int[] { 99 })))
            throw new Exception("value false return");
        if (Collections.replaceAll(values, new VClass(100, new int[] { 100 }), new VClass(0, new int[] { 0 })))
            throw new Exception("value true return for absent element");
    }
}
