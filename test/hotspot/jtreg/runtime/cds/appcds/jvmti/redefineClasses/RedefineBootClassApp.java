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
 *
 */

import java.io.File;

public class RedefineBootClassApp {
    public static void main(String args[]) throws Throwable {
        File bootJar = new File(args[0]);

        Class<?> superCls = Class.forName("BootSuper", false, null);
        System.out.println("BootSuper>> loader = " + superCls.getClassLoader());

        {
            BootSuper obj = (BootSuper)superCls.newInstance();
            System.out.println("(before transform) BootSuper>> doit() = " + obj.doit());
        }

        // Redefine the class
        byte[] bytes = Util.getClassFileFromJar(bootJar, "BootSuper");
        Util.replace(bytes, "Hello", "HELLO");
        RedefineClassHelper.redefineClass(superCls, bytes);

        {
            BootSuper obj = (BootSuper)superCls.newInstance();
            String s = obj.doit();
            System.out.println("(after transform) BootSuper>> doit() = " + s);
            if (!s.equals("HELLO")) {
                throw new RuntimeException("BootSuper doit() should be HELLO but got " + s);
            }
        }

        Class<?> childCls = Class.forName("BootChild", false, null);
        System.out.println("BootChild>> loader = " + childCls.getClassLoader());


        {
            BootSuper obj = (BootSuper)childCls.newInstance();
            String s = obj.doit();
            System.out.println("(after transform) BootChild>> doit() = " + s);
            if (!s.equals("HELLO")) {
                throw new RuntimeException("BootChild doit() should be HELLO but got " + s);
            }
        }
    }
}
