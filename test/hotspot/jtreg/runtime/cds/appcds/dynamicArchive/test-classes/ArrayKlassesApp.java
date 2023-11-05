/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.lang.reflect.Array;
import java.util.Date;

public class ArrayKlassesApp {
    public static void main(String args[]) {
        if (args.length == 1) {
            if (args[0].equals("system")) {
                Date[][][] array = new Date[1][2][2];
                int count = 0;
                for (int i=0; i<1; i++) {
                    for (int j=0; j<2; j++) {
                        for (int k=0; k<2; k++) {
                            array[i][j][k] = new Date();
                            count++;
                            array[i][j][k].setTime(20000 * count);
                        }
                    }
                }
            } else if (args[0].equals("primitive")) {
                long[][][] larray = new long[1][2][2];
                long lcount = 0;
                for (int i=0; i<1; i++) {
                    for (int j=0; j<2; j++) {
                        for (int k=0; k<2; k++) {
                            lcount++;
                            larray[i][j][k] = lcount;
                        }
                    }
                }
            } else if (args[0].equals("integer-array")) {
                Integer[][][][] iarray = new Integer[4][4][4][4];
                int count = 0;
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 4; j++) {
                        for (int k = 0; k < 4; k++) {
                            for (int l = 0; l < 4; l++) {
                                count++;
                                iarray[i][j][k][l] = new Integer(count);
                            }
                        }
                    }
                }
                System.out.println(iarray);
                System.out.println(iarray.getClass());
            }
        } else {
            Object x = Array.newInstance(ArrayKlassesApp.class, 3,3,3);
            System.out.println(x);
            System.out.println(x.getClass());
            System.out.println(Array.getLength(x));
        }
    }
}
