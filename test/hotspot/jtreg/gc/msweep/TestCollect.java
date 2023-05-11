/*
 * Copyright (c) 2023, Kirill Garbar, Red Hat, Inc. All rights reserved.
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

package gc.msweep;

/**
 * @test TestCollect
 * @summary MSweep is able to continue working after multiple collections
 * @library /test/lib
 *
 * @run main/othervm -XX:-UseTLAB -Xmx4m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseMSweepGC
 *                   -XX:-UseCompressedOops -Xlog:gc
 *                   gc.msweep.TestCollect
 */

import jdk.test.lib.Utils;

public class TestCollect {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello world");
        allocate(2000, 200);    //Around 4 collection cycles
    }

    public static void allocate(int x, int n) throws Exception { 
        int[] sink;
        for(int i = 0; i < n; i++) {
            sink = new int[x];
            sink[i] = i;
        }
    }
}