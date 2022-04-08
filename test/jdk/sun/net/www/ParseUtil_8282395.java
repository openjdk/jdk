/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary URL.openConnection can throw IOOBE
 * @bug 8282395
 * @modules java.base/sun.net.www
 */


import sun.net.www.*;

public class ParseUtil_8282395 {
    public static void main(String[] args) throws Exception {
        test("%", "%");
        test("%25%s%1G", "%%s%1G");
    }

    private static void test(String data, String expect) throws Exception {
        String result = ParseUtil.decode(data);
        if (!result.equals(expect)) {
            throw new RuntimeException("Decode does not match. expect: " +
                expect + ", actual: " + result);
        }
    }
}
