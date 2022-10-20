/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URLPermission;
import java.io.*;

public class URLTestUtils {

    // super class for all test types
    abstract static class Test {
        boolean expected;
        abstract boolean execute();
    };

    static class URLEqualityTest extends Test {
        String arg1, arg2;

        URLEqualityTest(String arg1, String arg2, boolean expected) {
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.expected = expected;
        }

        @Override
        boolean execute() {
            URLPermission p1 = new URLPermission(arg1);
            URLPermission p2 = new URLPermission(arg2);
            boolean result = p1.equals(p2);

            return result == expected;
        }
    }

    static URLEqualityTest eqtest(String arg1, String arg2, boolean expected) {
        return new URLEqualityTest(arg1, arg2, expected);
    }
}