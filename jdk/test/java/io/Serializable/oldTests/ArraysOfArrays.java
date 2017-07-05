/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary it is new version of old test which was under
 *          /src/share/test/serialization/piotest.java
 *          Test of serialization/deserialization of
 *          objects as arrays of arrays
 */

import java.io.*;

public class ArraysOfArrays {
    public static void main (String argv[]) {
        System.err.println("\nRegression test for testing of " +
            "serialization/deserialization of objects as " +
            "arrays of arrays \n");

        FileInputStream istream = null;
        try {
            FileOutputStream ostream = new FileOutputStream("piotest5.tmp");
            ObjectOutputStream p = new ObjectOutputStream(ostream);

            byte b[][] = {{ 0, 1}, {2,3}};
            p.writeObject((Object)b);

            short s[][] = {{ 0, 1, 2}, {3,4,5}};
            p.writeObject((Object)s);

            char c[][] = {{ 0, 1, 2, 3}, {4, 5, 6, 7}};
            p.writeObject((Object)c);

            int i[][] = {{ 0, 1, 2, 3, 4}, {5, 6, 7, 8, 9}};
            p.writeObject((Object)i);

            long l[][] = {{ 0, 1, 2, 3, 4, 5}, {6,7,8,9,10,11}};
            p.writeObject((Object)l);

            boolean z[][] = new boolean[2][2];

            z[0][0] = true;
            z[0][1] = false;
            z[1] = z[0];        // Use first row same as second

            p.writeObject((Object)z);

            float f[][] = {{ 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f},
                { 1.1f, 2.1f, 3.1f, 4.1f, 5.1f, 6.1f}};
            p.writeObject((Object)f);

            double d[][] = {{ 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0d},
                { 1.1f, 2.1f, 3.1f, 4.1f, 5.1f, 6.1f, 7.1d}};
            p.writeObject((Object)d);

            Integer Int[][] = {{ new Integer(3), new Integer(2)},
                { new Integer(1), new Integer(0)}};
            p.writeObject((Object)Int);

            p.flush();

            /* Now read them back and verify
             */
            istream = new FileInputStream("piotest5.tmp");
            ObjectInputStream q = new ObjectInputStream(istream);

            byte b_u[][] = (byte [][]) (q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nByte array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }


            short s_u[][] = (short [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nshort array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }

            char c_u[][] = (char [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nchar array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }

            int i_u[][] = (int [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nint array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }

            long l_u[][] = (long [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nlong array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }

            boolean z_u[][] = (boolean [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nboolean array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }

            float f_u[][] = (float [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nfloat array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }

            double d_u[][] = (double [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\ndouble array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }

            Integer Int_u[][] = (Integer [][])(q.readObject());
            for (int ix = 0; ix < b_u.length; ix++) {
                for(int iy = 0; iy < b_u[ix].length; iy++) {
                    if (b[ix][iy] != b_u[ix][iy]) {
                        System.err.println("\nInteger array mismatch [" +
                            ix + "][" + iy + " expected " + b[ix][iy] +
                            " actual = " + b_u[ix][iy]);
                        throw new Error();
                    }
                }
            }
            System.err.println("\nTEST PASSED");
        } catch (Exception e) {
            System.err.print("TEST FAILED: ");
            e.printStackTrace();

            System.err.println("\nInput remaining");
            int ch;
            try {
                while ((ch = istream.read()) != -1) {
                    System.err.print("\n " +Integer.toString(ch, 16) + " ");
                }
                System.err.println("\n ");
            } catch (Exception f) {
                throw new Error();
            }
            throw new Error();
        }
    }
}
