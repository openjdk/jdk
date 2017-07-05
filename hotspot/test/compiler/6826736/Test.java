/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 */

/**
 * @test
 * @bug 6826736
 * @summary CMS: core dump with -XX:+UseCompressedOops
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch -XX:+ScavengeALot -XX:+UseCompressedOops -XX:HeapBaseMinAddress=32g -XX:CompileThreshold=100 -XX:CompileOnly=Test.test -XX:-BlockLayoutRotateLoops -XX:LoopUnrollLimit=0 Test
 */

public class Test {
    int[] arr;
    int[] arr2;
    int test(int r) {
        for (int i = 0; i < 100; i++) {
            for (int j = i; j < 100; j++) {
               int a = 0;
               for (long k = 0; k < 100; k++) {
                  a += k;
               }
               if (arr != null)
                   a = arr[j];
               r += a;
            }
        }
        return r;
    }

    public static void main(String[] args) {
        int r = 0;
        Test t = new Test();
        for (int i = 0; i < 100; i++) {
            t.arr = new int[100];
            r = t.test(r);
        }
        System.out.println("Warmup 1 is done.");
        for (int i = 0; i < 100; i++) {
            t.arr = null;
            r = t.test(r);
        }
        System.out.println("Warmup 2 is done.");
        for (int i = 0; i < 100; i++) {
            t.arr = new int[100];
            r = t.test(r);
        }
        System.out.println("Warmup is done.");
        for (int i = 0; i < 100; i++) {
            t.arr = new int[1000000];
            t.arr = null;
            r = t.test(r);
        }
    }
}
