/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.lang;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public final class Class_forName05 extends JTTTest {

    public static String test(int i) throws ClassNotFoundException {
        final URLClassLoader classLoader = new URLClassLoader(new URL[0], String.class.getClassLoader());
        if (i == 0) {
            return Class.forName("java.lang.String", false, classLoader).toString();
        } else if (i == 1) {
            return Class.forName("[Ljava.lang.String;", false, classLoader).toString();
        }
        return null;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 5);
    }

}
