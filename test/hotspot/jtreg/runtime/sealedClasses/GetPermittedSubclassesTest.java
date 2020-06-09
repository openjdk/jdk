/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8225056
 * @compile GetPermittedSubclasses.jcod
 * @compile --enable-preview -source ${jdk.version} GetPermittedSubclassesTest.java
 * @run main/othervm --enable-preview GetPermittedSubclassesTest
 */

import java.lang.constant.ClassDesc;
import java.util.ArrayList;

// Test Class GetPermittedSubtpes() and Class.isSealed() APIs.
public class GetPermittedSubclassesTest {

    sealed class Sealed1 permits Sub1 {}

    final class Sub1 extends Sealed1 implements SealedI1 {}

    sealed interface SealedI1 permits NotSealed, Sub1, Extender {}

    non-sealed interface Extender extends SealedI1 { }

    final class FinalC implements Extender {}

    final class NotSealed implements SealedI1 {}

    final class Final4 {}

    public static void testSealedInfo(Class<?> c, String[] expected) {
        Object[] permitted = c.permittedSubclasses();

        if (permitted.length != expected.length) {
            throw new RuntimeException(
                "Unexpected number of permitted subclasses for: " + c.toString());
        }

        if (permitted.length > 0) {
            if (!c.isSealed()) {
                throw new RuntimeException("Expected sealed class: " + c.toString());
            }

            // Create ArrayList of permitted subclasses class names.
            ArrayList<String> permittedNames = new ArrayList<String>();
            for (int i = 0; i < permitted.length; i++) {
                permittedNames.add(((ClassDesc)permitted[i]).descriptorString());
            }

            if (permittedNames.size() != expected.length) {
                throw new RuntimeException(
                    "Unexpected number of permitted names for: " + c.toString());
            }

            // Check that expected class names are in the permitted subclasses list.
            for (int i = 0; i < expected.length; i++) {
                if (!permittedNames.contains(expected[i])) {
                    throw new RuntimeException(
                         "Expected class not found in permitted subclases list, super class: " +
                         c.getName() + ", expected class: " + expected[i]);
                }
            }
        } else {
            // Must not be sealed if no permitted subclasses.
            if (c.isSealed()) {
                throw new RuntimeException("Unexpected sealed class: " + c.toString());
            }
        }
    }

    public static void testBadSealedClass(String className, String expectedCFEMessage) throws Throwable {
        try {
            Class.forName(className);
            throw new RuntimeException("Expected ClassFormatError exception not thrown for " + className);
        } catch (ClassFormatError cfe) {
            if (!cfe.getMessage().contains(expectedCFEMessage)) {
                throw new RuntimeException(
                    "Class " + className + " got unexpected ClassFormatError exception: " + cfe.getMessage());
            }
        }
    }

    public static void main(String... args) throws Throwable {
        testSealedInfo(SealedI1.class, new String[] {"LGetPermittedSubclassesTest$NotSealed;",
                                                     "LGetPermittedSubclassesTest$Sub1;",
                                                     "LGetPermittedSubclassesTest$Extender;"});
        testSealedInfo(Sealed1.class, new String[] {"LGetPermittedSubclassesTest$Sub1;"});
        testSealedInfo(Final4.class, new String[] { });
        testSealedInfo(NotSealed.class, new String[] { });

        // Test class with PermittedSubclasses attribute but old class file version.
        testSealedInfo(OldClassFile.class, new String[] { });

        // Test class with an empty PermittedSubclasses attribute.
        testBadSealedClass("NoSubclasses", "PermittedSubclasses attribute is empty");

        // Test returning names of non-existing classes.
        testSealedInfo(NoLoadSubclasses.class, new String[]{"LiDontExist;", "LI/Dont/Exist/Either;"});

        // Test that loading a class with a corrupted PermittedSubclasses attribute
        // causes a ClassFormatError.
        testBadSealedClass("BadPermittedAttr",
                          "Permitted subclass class_info_index 15 has bad constant type");

        // Test that loading a sealed final class with a PermittedSubclasses
        // attribute causes a ClassFormatError.
        testBadSealedClass("SealedButFinal", "PermittedSubclasses attribute in final class");

        // Test that loading a sealed class with a bad class name in its PermittedSubclasses
        // attribute causes a ClassFormatError.
        testBadSealedClass("BadPermittedSubclassEntry", "Illegal class name \"iDont;;Exist\" in class file");

        // Test that loading a sealed class with an empty class name in its PermittedSubclasses
        // attribute causes a ClassFormatError.
        testBadSealedClass("EmptyPermittedSubclassEntry", "Illegal class name \"\" in class file");
    }
}
