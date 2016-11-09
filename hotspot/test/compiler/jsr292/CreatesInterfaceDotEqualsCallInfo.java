/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8026124
 * @summary Javascript file provoked assertion failure in linkResolver.cpp
 * @modules jdk.scripting.nashorn/jdk.nashorn.tools
 *
 * @run main/othervm compiler.jsr292.CreatesInterfaceDotEqualsCallInfo
 */

package compiler.jsr292;

public class CreatesInterfaceDotEqualsCallInfo {
    public static void main(String[] args) throws java.io.IOException {
        String[] jsargs = {System.getProperty("test.src", ".") +
                "/createsInterfaceDotEqualsCallInfo.js"};
        jdk.nashorn.tools.Shell.main(System.in, System.out, System.err, jsargs);
        System.out.println("PASS, did not crash running Javascript");
    }
}
