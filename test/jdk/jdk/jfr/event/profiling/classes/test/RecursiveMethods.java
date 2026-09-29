/*
 * Copyright (c) 2025 SAP SE. All rights reserved.
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

 package test;

 import java.time.Duration;

 /*
  * A class used to create a simple deep call stack for testing purposes
  */
 public class RecursiveMethods {

    /** Method that uses recursion to produce a call stack of at least {@code depth} depth */
    public static int entry(int depth) {
        return method2(--depth);
    }

    private static int method2(int depth) {
        return method3(--depth);
    }

    private static int method3(int depth) {
        return method4(--depth);
    }

    private static int method4(int depth) {
        return method5(--depth);
    }

    private static int method5(int depth) {
        return method6(--depth);
    }

    private static int method6(int depth) {
        return method7(--depth);
    }

    private static int method7(int depth) {
        return method8(--depth);
    }

    private static int method8(int depth) {
        return method9(--depth);
    }

    private static int method9(int depth) {
        return method10(--depth);
    }

    private static int method10(int depth) {
        if (depth > 0) {
            return entry(--depth);
        }
        return depth;
    }
}
