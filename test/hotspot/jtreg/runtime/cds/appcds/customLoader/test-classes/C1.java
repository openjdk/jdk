/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.*;

/**
 * Helper class used by the UberJarTest for loading various classes using
 * the UberJarUtils.
 */
public class C1 {
    public static void main(String args[]) throws Throwable {
        String source = "none";
        if (args.length == 1) {
            source = args[0];
        }

        // Find the UberJarUtils class and its loadClass method.
        Class utilCls = Class.forName("jdk.internal.misc.UberJarUtils");
        Method utilMth = utilCls.getMethod("loadClass", String.class, String.class);

        // Call UberJarUtils.loadClass reflectively to load C2.
        System.out.println("C1: getting C2");
        String[] inputArgs = {"jar:file:" + source + "!/x2.jar!/", "C2"};
        Object o = utilMth.invoke(null, inputArgs);
        Class cls = (Class)o;

        // Invoke C2's main reflectively.
        System.out.println("C1: invoking C2");
        Method mth = cls.getMethod("main",new Class[]{String[].class});
        mth.invoke(null,new Object[]{args});

        // Call UberJarUtils.loadClass reflectively to load SimpleHello.
        System.out.println("C1: getting SimpleHello");
        String[] inputArgs2 = {"jar:file:" + source + "!/subDir!/", "SimpleHello"};
        o = utilMth.invoke(null, inputArgs2);
        cls = (Class)o;

        // Invoke SimpleHello's main reflectively.
        System.out.println("C1: invoking SimpleHello");
        mth = cls.getMethod("main",new Class[]{String[].class});
        mth.invoke(null,new Object[]{args});
    }
}
