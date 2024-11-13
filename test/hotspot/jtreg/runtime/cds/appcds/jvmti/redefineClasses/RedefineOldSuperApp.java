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

public class RedefineOldSuperApp {
    public static void main(String args[]) throws Throwable {
        File bootJar = new File(args[0]);
        ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();

        Class<?> superCls = Class.forName("OldSuper", false, appClassLoader);
        System.out.println("OldSuper>> loader = " + superCls.getClassLoader());

        {
            OldSuper obj = (OldSuper)superCls.newInstance();
            System.out.println("(before transform) OldSuper>> doit() = " + obj.doit());
        }

        // Redefine the class
        byte[] bytes = Util.getClassFileFromJar(bootJar, "OldSuper");
        Util.replace(bytes, "Hello", "HELLO");
        RedefineClassHelper.redefineClass(superCls, bytes);

        {
            OldSuper obj = (OldSuper)superCls.newInstance();
            String s = obj.doit();
            System.out.println("(after transform) OldSuper>> doit() = " + s);
            if (!s.equals("HELLO")) {
                throw new RuntimeException("OldSuper doit() should be HELLO but got " + s);
            }
        }

        Class<?> childCls = Class.forName("NewChild", false, appClassLoader);
        System.out.println("NewChild>> loader = " + childCls.getClassLoader());


        {
            OldSuper obj = (OldSuper)childCls.newInstance();
            String s = obj.doit();
            System.out.println("(after transform) NewChild>> doit() = " + s);
            if (!s.equals("HELLO")) {
                throw new RuntimeException("NewChild doit() should be HELLO but got " + s);
            }
        }
    }
}
