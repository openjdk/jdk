/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.auto;

public class ComAuto {
    public static void main(String args[]) {
        Module unnamed = ComAuto.class.getClassLoader().getUnnamedModule();
        Module com_auto = ComAuto.class.getModule();
        Module com_test = ModuleLayer.boot()
            .findModule("com.test")
            .orElseThrow(() -> new IllegalStateException("Run with --add-modules com.test"));
        Module java_logging = ModuleLayer.boot()
            .findModule("java.logging")
            .orElseThrow(() -> new IllegalStateException("Run with --add-modules java.logging"));

        System.out.println("OUT: com.auto.isNamed() = " + com_auto.isNamed());
        System.out.println("OUT: com.auto.getName() = " + com_auto.getName());
        System.out.println("OUT: com.test.canRead(java.logging) = " + com_test.canRead(java_logging)); // must be true
        System.out.println("OUT: com.auto.canRead(java.logging) = " + com_auto.canRead(java_logging)); // must be true
        System.out.println("OUT: unnamed.canRead(java.logging) = " + unnamed.canRead(java_logging)); // must be true
        System.out.println("OUT: unnamed.canRead(com.auto) = " + unnamed.canRead(com_auto)); // must be true
        System.out.println("OUT: com.auto.canRead(com.test) = " + com_auto.canRead(com_test)); // must be true
        System.out.println("OUT: com.auto.canRead(unnamed) = " + com_auto.canRead(unnamed)); // must be true
        System.out.println("OUT: com.test.canRead(com.auto) = " + com_test.canRead(com_auto)); // must be true
    }
}
