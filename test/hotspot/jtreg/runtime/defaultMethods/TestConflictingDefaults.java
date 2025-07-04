/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8356942
 * @summary Ensure invokeinterface throws the correct exception when
 *          conflicting default methods are found.
 * @compile C.java I1.java I2.java
 * @comment Now replace I2 with a version that renames the default method
 *          to cause a conflict.
 * @compile I2.jasm
 * @run driver TestConflictingDefaults
 *
 */
public class TestConflictingDefaults {
    public static void main(String[] args) {
        String error = "Conflicting default methods: I1.m I2.m";
        try {
            I1 var1 = new C();
            Integer i = var1.m();
            throw new RuntimeException("Invocation should have failed!");
        }
        catch(IncompatibleClassChangeError icce) {
            if (!icce.getMessage().contains(error)) {
                throw new RuntimeException("Unexpected ICCE thrown: " + icce);
            }
        }
    }
}
