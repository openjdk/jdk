/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8163449
 * @summary Allow per protocol setting for URLConnection defaultUseCaches
 * @run main/othervm SetDefaultUseCaches
 */

import java.net.*;
import java.io.*;

public class SetDefaultUseCaches {
    static void testAssert(boolean value, boolean comparator) {
        if (value != comparator) {
            System.err.println("Expected " + comparator + " Got " + value);
            throw new RuntimeException("Test failed:");
        } else
            System.err.println("OK");
    }

    public static void main(String s[]) throws Exception {
        URL url = new URL("http://www.foo.com/");
        URL url1 = new URL("file:///a/b.txt");

        // check default default is true
        URLConnection urlc = url.openConnection();
        testAssert(urlc.getDefaultUseCaches(), true);

        // set default for http to false and check
        URLConnection.setDefaultUseCaches("HTTP", false);

        urlc = url.openConnection();
        testAssert(urlc.getDefaultUseCaches(), true);
        testAssert(urlc.getUseCaches(), false);
        testAssert(URLConnection.getDefaultUseCaches("http"), false);

        URLConnection urlc1 = url1.openConnection();
        testAssert(urlc1.getDefaultUseCaches(), true);

        // set default default to false and check other values the same
        urlc.setDefaultUseCaches(false);
        urlc1.setDefaultUseCaches("fiLe", true);
        testAssert(urlc1.getDefaultUseCaches(), false);
        testAssert(URLConnection.getDefaultUseCaches("fiLE"), true);
    }
}
