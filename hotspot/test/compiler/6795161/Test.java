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

/*
 * @test
 * @bug 6795161
 * @summary Escape analysis leads to data corruption
 * @run main/othervm -server -Xcomp -XX:CompileOnly=Test -XX:+DoEscapeAnalysis Test
 */

class Test_Class_1 {
    static String var_1;

    static void badFunc(int size)
    {
        try {
          for (int i = 0; i < 1; (new byte[size-i])[0] = 0, i++) {}
        } catch (Exception e) {
          // don't comment it out, it will lead to correct results ;)
          //System.out.println("Got exception: " + e);
        }
    }
}

public class Test {
    static String var_1_copy = Test_Class_1.var_1;

    static byte var_check;

    public static void main(String[] args)
    {
        var_check = 1;

        Test_Class_1.badFunc(-1);

        System.out.println("EATester.var_check = " + Test.var_check + " (expected 1)\n");
    }
}

