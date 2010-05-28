/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4116016
   @summary Ensure that finalizers are not invoked more than once when on-exit
            finalization is enabled and a finalizer invokes System.exit after
            System.exit has already been invoked
   @build FinExit
   @run shell FinExit.sh
 */


public class FinExit {

    boolean finalized = false;

    public void finalize() {
        if (finalized) {
            System.out.println("2");
        } else {
            finalized = true;
            System.out.println("1");
            System.exit(0);
        }
    }

    public static void main(String[] args) throws Exception {
        System.runFinalizersOnExit(true);
        Object o = new FinExit();
        System.exit(0);
    }

}
