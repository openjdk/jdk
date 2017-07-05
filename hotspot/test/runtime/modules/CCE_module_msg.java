/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm CCE_module_msg
 */

// Test that the message in a runtime ClassCastException contains module info.
public class CCE_module_msg {

    public static void main(String[] args) {
        invalidCastTest();
    }

    public static void invalidCastTest() {
        java.lang.Object instance = new java.lang.Object();
        int left = 23;
        int right = 42;
        try {
            for (int i = 0; i < 1; i += 1) {
                left = ((Derived) instance).method(left, right);
            }
            throw new RuntimeException("ClassCastException wasn't thrown, test failed.");
        } catch (ClassCastException cce) {
            System.out.println(cce.getMessage());
            if (!cce.getMessage().contains("java.lang.Object (in module: java.base) cannot be cast")) {
                throw new RuntimeException("Wrong message: " + cce.getMessage());
            }
        }
    }
}

class Derived extends java.lang.Object {
    public int method(int left, int right) {
        return right;
    }
}
