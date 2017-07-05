/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test ExecuteInternalVMTests
 * @bug 8004691
 * @summary Add a jtreg test that exercises the ExecuteInternalVMTests flag
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+ExecuteInternalVMTests ExecuteInternalVMTests
 */
public class ExecuteInternalVMTests {
    public static void main(String[] args) throws Exception {
        // The tests that are run are the HotSpot internal tests which are
        // executed only when the flag -XX:+ExecuteInternalVMTests is used.

        // The flag -XX:+ExecuteInternalVMTests can only be used for
        // non-product builds of HotSpot. Therefore, the flag
        // -XX:+IgnoreUnrecognizedVMOptions is also used, which means that this
        // test will do nothing on a product build.
    }
}
