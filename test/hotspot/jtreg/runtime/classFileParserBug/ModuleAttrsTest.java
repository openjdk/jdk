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
 */

/*
 * @test
 * @bug 8267650
 * @summary Test classes with attributes named Module, ModulePackages, and
 *          ModuleMainClass.
 * @compile ModuleAttrs.jcod
 * @run main ModuleAttrsTest
 */

public class ModuleAttrsTest {
    public static void main(String args[]) throws Throwable {

        // Test that loading a class with an attribute named Module causes a
        // ClassFormatError exception for class file versions 53 or later.
        try {
            Class newClass = Class.forName("ModuleAttr");
            throw new RuntimeException("Expected ClassFormatError exception not thrown");
        } catch (java.lang.ClassFormatError e) {
            if (!e.getMessage().contains("Unexpected Module attribute in class file")) {
                throw new RuntimeException("Wrong ClassFormatError exception: " + e.getMessage());
            }
        }

        // Test that loading a class with an attribute named ModulePackages causes
        // a ClassFormatError exception for class file versions 53 or later.
        try {
            Class newClass = Class.forName("ModulePackagesAttr");
            throw new RuntimeException("Expected ClassFormatError exception not thrown");
        } catch (java.lang.ClassFormatError e) {
            if (!e.getMessage().contains("Unexpected ModulePackages attribute in class file")) {
                throw new RuntimeException("Wrong ClassFormatError exception: " + e.getMessage());
            }
        }

        // Test that loading a class with an attribute named ModuleMainClass causes a
        // ClassFormatError exception for class file versions 53 or later.
        try {
            Class newClass = Class.forName("ModuleMainClassAttr");
            throw new RuntimeException("Expected ClassFormatError exception not thrown");
        } catch (java.lang.ClassFormatError e) {
            if (!e.getMessage().contains("Unexpected ModuleMainClass attribute in class file")) {
                throw new RuntimeException("Wrong ClassFormatError exception: " + e.getMessage());
            }
        }

        // Test that attributes named Module, ModulePackages, and ModuleMainClass are ignored
        // when loading a class with class file version < 53  (JDK-9).
        Class newClass = Class.forName("ModuleAttrs");
    }
}
