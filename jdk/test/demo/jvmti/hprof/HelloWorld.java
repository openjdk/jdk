/*
 * Copyright (c) 2004, 2009, Oracle and/or its affiliates. All rights reserved.
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


/* HelloWorld:
 *
 *   Sample target application for HPROF tests
 *
 */

/* Just some classes that create a variety of references */

class AAAA {
    public int                  AAAA_i;
    public static int           AAAA_si;
    public Object               AAAA_j;
    public static Object        AAAA_sj;
    public long                 AAAA_k;
    public static long          AAAA_sk;
}

interface IIII {
    Object o = new Object();
}

class BBBB extends AAAA implements IIII {
    public byte                 BBBB_ii;
    public static byte          BBBB_sii;
    public Object               BBBB_jj;
    public static Object        BBBB_sjj;
    public short                BBBB_kk;
    public static short         BBBB_skk;
}

class REFS {
    private static String s1    = new String("REFS_string1");
    private String is2          = new String("REFS_string2");
    private static String s3    = new String("REFS_string3");
    private static String s4    = new String("REFS_string4");
    private String is5          = new String("REFS_string5");

    private AAAA aaaa;
    private BBBB bbbb;

    public void test() {
        aaaa    = new AAAA();
        bbbb    = new BBBB();

        aaaa.AAAA_i     = 1;
        AAAA.AAAA_si    = 2;
        aaaa.AAAA_j     = s1;
        AAAA.AAAA_sj    = is2;
        aaaa.AAAA_k     = 5;
        AAAA.AAAA_sk    = 6;

        bbbb.BBBB_ii    = 11;
        BBBB.BBBB_sii   = 22;
        bbbb.BBBB_jj    = s3;
        BBBB.BBBB_sjj   = s4;
        bbbb.BBBB_kk    = 55;
        BBBB.BBBB_skk   = 66;

        bbbb.AAAA_i     = 111;
        bbbb.AAAA_j     = is5;
        bbbb.AAAA_k     = 555;
    }
}

/* Fairly simple hello world program that does some exercises first. */

public class HelloWorld {
    public static void main(String args[]) {

        /* References exercise. */
        REFS r = new REFS();
        r.test();

        /* Use a generic type exercise. */
        java.util.List<String> l = new java.util.ArrayList<String>();
        String.format("%s", "");

        /* Create a class that has lots of different bytecodes exercise. */
                     /* (Don't run it!) */
        UseAllBytecodes x = new UseAllBytecodes(1,2);

        /* Just some code with branches exercise. */
        try {
            if ( args.length == 0 ) {
                System.out.println("No arguments passed in (doesn't matter)");
            } else {
                System.out.println("Arguments passed in (doesn't matter)");
            }
        } catch ( Throwable e ) {
            System.out.println("ERROR: System.out.println() did a throw");
        } finally {
            System.out.println("Hello, world!");
        }
    }
}
