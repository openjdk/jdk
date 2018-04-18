/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4327164
 * @summary Basic test for new RandomAccess interface
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.RandomAccess;
import java.util.Vector;

public class Basic {
    public static void main(String[] args) throws Exception {
        List a0 = Arrays.asList(new String[] { "a", "b", "c" });
        List a[] = { a0, new ArrayList(a0), new LinkedList(a0),
                new Vector(a0) };

        if (!(a[0] instanceof RandomAccess))
            throw new Exception("Arrays.asList doesn't implement RandomAccess");
        if (!(a[1] instanceof RandomAccess))
            throw new Exception("ArrayList doesn't implement RandomAccess");
        if (a[2] instanceof RandomAccess)
            throw new Exception("LinkedList implements RandomAccess");
        if (!(a[3] instanceof RandomAccess))
            throw new Exception("Vector doesn't implement RandomAccess");

        for (int i = 0; i < a.length; i++) {
            List t = a[i];
            List ut = Collections.unmodifiableList(t);
            List st = Collections.synchronizedList(t);

            boolean random = t instanceof RandomAccess;
            if ((ut instanceof RandomAccess) != random)
                throw new Exception(
                        "Unmodifiable fails to preserve RandomAccess: " + i);
            if ((st instanceof RandomAccess) != random)
                throw new Exception(
                        "Synchronized fails to preserve RandomAccess: " + i);

            while (t.size() > 0) {
                t = t.subList(0, t.size() - 1);
                if ((t instanceof RandomAccess) != random)
                    throw new Exception(
                            "SubList fails to preserve RandomAccess: " + i
                                    + ", " + t.size());

                ut = ut.subList(0, ut.size() - 1);
                if ((ut instanceof RandomAccess) != random)
                    throw new Exception(
                            "SubList(unmodifiable) fails to preserve RandomAccess: "
                                    + i + ", " + ut.size());

                st = st.subList(0, st.size() - 1);
                if ((st instanceof RandomAccess) != random)
                    throw new Exception(
                            "SubList(synchronized) fails to preserve RandomAccess: "
                                    + i + ", " + st.size());
            }
        }

        // Test that shuffle works the same on random and sequential access
        List al = new ArrayList();
        for (int j = 0; j < 100; j++)
            al.add(Integer.valueOf(2 * j));
        List ll = new LinkedList(al);
        Random r1 = new Random(666), r2 = new Random(666);
        for (int i = 0; i < 100; i++) {
            Collections.shuffle(al, r1);
            Collections.shuffle(ll, r2);
            if (!al.equals(ll))
                throw new Exception("Shuffle failed: " + i);
        }

        // Test that fill works on random & sequential access
        List gumbyParade = Collections.nCopies(100, "gumby");
        Collections.fill(al, "gumby");
        if (!al.equals(gumbyParade))
            throw new Exception("ArrayList fill failed");
        Collections.fill(ll, "gumby");
        if (!ll.equals(gumbyParade))
            throw new Exception("LinkedList fill failed");

        // Test that copy works on random & sequential access
        List pokeyParade = Collections.nCopies(100, "pokey");
        Collections.copy(al, pokeyParade);
        if (!al.equals(pokeyParade))
            throw new Exception("ArrayList copy failed");
        Collections.copy(ll, pokeyParade);
        if (!ll.equals(pokeyParade))
            throw new Exception("LinkedList copy failed");

        // Test that binarySearch works the same on random & sequential access
        al = new ArrayList();
        for (int i = 0; i < 10000; i++)
            al.add(Integer.valueOf(2 * i));
        ll = new LinkedList(al);
        for (int i = 0; i < 500; i++) {
            Integer key = Integer.valueOf(r1.nextInt(20000));
            if (Collections.binarySearch(al, key) != Collections
                    .binarySearch(ll, key))
                throw new Exception("Binary search failed: " + i);
        }
    }
}
