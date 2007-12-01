/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

public class ArrayOpsTest {
    public static boolean verify(int a[], int b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nInt array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nInt array contents differ " +
                    "at index " + i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

    public static boolean verify(byte a[], byte b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nByte array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nByte array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

    public static boolean verify(char a[], char b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nChar array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nChar array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

    public static boolean verify(short a[], short b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nShort array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nShort array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

    public static boolean verify(boolean a[], boolean b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nBoolean array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nBoolean array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

    public static boolean verify(float a[], float b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nFloat array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nFloat array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

    public static boolean verify(double a[], double b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nDouble array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nDouble array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

    public static boolean verify(long a[], long b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nLong array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("\nLong array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }

   public static boolean verify(String a[], String b[]) {
       boolean result = true;

       if (a.length != b.length) {
           System.err.println("\nString array lengths differ: " +
               a.length + " != " + b.length);
           result = false;
       }

       for (int i = 0; i < a.length; i++) {
           if (!a[i].equals(b[i])) {
               System.err.println("\nString array contents differ at index " +
                    i + ": " + a[i] + " != " + b[i]);
               result = false;
           }
       }
       return result;
    }

    public static boolean verify(PrimitivesTest a[], PrimitivesTest b[]) {
        boolean result = true;

        if (a.length != b.length) {
            System.err.println("\nPrimitivesTest array lengths differ: " +
                a.length + " != " + b.length);
            result = false;
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] == null && b[i] == null) {
                continue;
            }

            if (!a[i].equals(b[i])) {
                System.err.println("\nPrimitivesTest array contents differ at " +
                    "index " + i + ": " + a[i] + " != " + b[i]);
                result = false;
            }
        }
        return result;
    }
}
