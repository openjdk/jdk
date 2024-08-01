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

/*
 * @test
 * @bug 8334421
 * @summary C2 incorrectly marks not-escaped locks for elimination after
 *          coarsened locks were eliminated and created unbalanced regions.
 * @requires vm.compMode != "Xint"
 * @run main/othervm -XX:-TieredCompilation TestCoarsenedAndNotEscapedLocksElimination
 * @run main TestCoarsenedAndNotEscapedLocksElimination
 */

import java.util.Vector;

class TestVector extends Vector<Object> {

    TestVector() {
        super();
    }

    TestVector(int initialCapacity) {
        super(initialCapacity);
    }

    TestVector(int initialCapacity, int capacityIncrement) {
        super(initialCapacity, capacityIncrement);
    }

    Object[] getElementData () {
        return elementData; // access protected field
    }
}

public class TestCoarsenedAndNotEscapedLocksElimination {

    public static void main(String[] strArr) {
        TestCoarsenedAndNotEscapedLocksElimination tc = new TestCoarsenedAndNotEscapedLocksElimination();
        String result = null;
        for (int i = 0; i < 12000; ++i) {
            result = tc.test();
            if (result != null) break;
        }
        System.out.println(result == null? "passed" : result);
    }

    int [][] vector_types = {
       {-1, -1},
       {0, -1},
       {1, -1},
       {2, -1},
       {1025, -1},
       {0, -2},
       {1, -2},
       {2, -2},
       {1025, -2},
       {0, 0},
       {1, 0},
       {2, 0},
       {1025, 0},
       {0, 1},
       {1, 1},
       {2, 1},
       {1025, 1},
       {0, 1025 },
       {1, 1025 },
       {2, 1025 },
       {1025, 1025 }
    };

    Object [] elems = {
        null,
        new Object(),
        new Vector(),
        new Object[0]
    };

    int cntr = 0, mode = 0;

    void reset() {
        cntr = 0;
        mode = 0;
    }

    TestVector nextVector() {
        if (cntr == vector_types.length) {
            return null;
        } else {
            TestVector vect;
            if (vector_types[cntr][0] < 0) {
                vect = new TestVector();
            } else if (vector_types[cntr][1] == -2) {
                vect = new TestVector(vector_types[cntr][0]);
            } else {
                vect = new TestVector(vector_types[cntr][0], vector_types[cntr][1]);
            }
            if (mode == 1) {
                vect.addElement(null);
                vect.addElement(new Object());
                vect.addElement(new Vector());
                vect.addElement(new Object[0]);
            } else if (mode == 2) {
                int cap = vect.capacity();
                vect.addElement(null);
                for (int i = 0; i < cap; i++) {
                    vect.addElement(new Object());
                }
            }
            if (++mode == 3) {
                mode = 0;
                cntr++;
            }
            return vect;
        }
    }

    public String test() {
        reset();
        TestVector vect = (TestVector)nextVector();
        while (vect != null) {
            Object [] backup_array = new Object[vect.size()];
            System.arraycopy(vect.getElementData(),0,backup_array,0,vect.size());

            int old_size = vect.size();
            vect.setSize(vect.size());
            if (vect.size() != old_size) {
                return "Vector: "+vect+" size changed after setSize(size())";
            }
            for (int i = 0; i < vect.size(); i++) {
                if (vect.elementAt(i) != backup_array[i]) {
                    return "Vector: "+vect+" : "+i+"th element changed after setSize(size())";
                }
            }

            old_size = vect.size();
            vect.setSize(vect.size()*2);
            if (vect.size() != old_size*2) {
                return "Vector: "+vect+" size incorrectly changed after setSize(size()*2)";
            }
            for (int i = 0; i < old_size; i++) {
                if (vect.elementAt(i) != backup_array[i]) {
                    return "Vector: "+vect+" : "+i+"th element changed after setSize(size()*2)";
                }
            }
            for (int i = old_size; i < old_size*2; i++) {
                if (vect.elementAt(i) != null) {
                    return "Vector: "+vect+" : "+i+"th element not null after setSize(size()*2)";
                }
            }

            old_size = vect.size();
            int old_cap = vect.capacity();
            vect.setSize(vect.capacity()+1);
            if (vect.size() != old_cap+1) {
                return "Vector: "+vect+" size incorrectly changed after setSize(capacity()+1)";
            }
            for (int i = 0; i < old_size && i < backup_array.length; i++) {
                if (vect.elementAt(i) != backup_array[i]) {
                    return "Vector: "+vect+" : "+i+"th element changed after setSize(capacity()+1)";
                }
            }
            for (int i = old_size; i < old_cap + 1; i++) {
                if (vect.elementAt(i) != null) {
                    return "Vector: "+vect+" : "+i+"th element not null after setSize(capacity()+1)";
                }
            }

            old_size = vect.size();
            vect.setSize(vect.size()/2);
            if (vect.size() != old_size/2) {
                return "Vector: "+vect+" size incorrectly changed after setSize(size()/2)";
            }
            for (int i = 0; i < old_size/2 && i < backup_array.length; i++) {
                if (vect.elementAt(i) != backup_array[i]) {
                    return "Vector: "+vect+" : "+i+"th element changed after setSize(size()/2)";
                }
            }

            vect = nextVector();
        }
        return null;
    }

}

