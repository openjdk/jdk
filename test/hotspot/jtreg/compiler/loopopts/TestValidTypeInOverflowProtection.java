/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8373525
 * @summary Test for the check of a valid type (long) for the input variable of overflow protection
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileCommand=compileonly,compiler.loopopts.TestValidTypeInOverflowProtection::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.loopopts;

import java.util.Vector;

class TestVector extends Vector {
    TestVector(int initialCapacity) {
        super(initialCapacity);
    }

    Object getElementData() {
        return elementData;
    }
}

public class TestValidTypeInOverflowProtection {
    int cntr;
    int mode;
    int value = 533;
    int one = 1;

    public static void main(String[] args) {
        TestValidTypeInOverflowProtection test = new TestValidTypeInOverflowProtection();
        for (int i = 0; i < 1000; i++) {
            test.test();
        }
    }

    TestVector nextVector() {
        if (cntr == one) {
            return null;
        }
        TestVector vect = new TestVector(value);
        if (mode == 2) {
            int cap = vect.capacity();
            for (int i = 0; i < cap; i++) {
                vect.addElement(new Object());
            }
        }
        if (++mode == 3) {
            mode = cntr++;
        }
        return vect;
    }

    String test() {
        cntr = 0;
        TestVector vect = nextVector();
        while (vect != null) {
            Object backup_array = new Object[vect.size()];
            System.arraycopy(vect.getElementData(), 0, backup_array, 0, vect.size());
            int old_size = vect.size();
            int old_cap = vect.capacity();
            vect.setSize(vect.capacity() + 1);
            for (int i = old_size; i < old_cap; i++) {
                if (vect.elementAt(i) != null) {
                }
            }
            for (int i = 0; i < new MyInteger(old_size).v; i++) {
            }
            vect = nextVector();
        }
        return null;
    }

    class MyInteger {
        int v;

        MyInteger(int v) {
            int M452 = 4;
            int N452 = 8;
            for (int i452 = 0; i452 < M452; i452++) {
                for (int j452 = 0; j452 < N452; j452++) {
                    switch (i452) {
                        case -2:
                        case 0:
                            this.v = v;
                    }
                }
            }
        }
    }
}