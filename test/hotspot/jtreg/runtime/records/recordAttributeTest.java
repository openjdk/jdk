/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @compile abstractRecord.jcod notFinalRecord.jcod oldRecordAttribute.jcod superNotJLRecord.jcod
 * @compile shortRecordAttribute.jcod twoRecordAttributes.jcod badRecordAttribute.jcod
 *
 * @run main/othervm --enable-preview recordAttributeTest
 */


import java.lang.reflect.Method;

public class recordAttributeTest {

    public static void runTest(String className, String cfeMessage) {
        try {
            Class newClass = Class.forName(className);
            throw new RuntimeException("Expected ClasFormatError exception not thrown");
        } catch (java.lang.ClassFormatError e) {
            String eMsg = e.getMessage();
            if (!eMsg.contains(cfeMessage)) {
                throw new RuntimeException("Unexpected exception: " + eMsg);
            }
        } catch (java.lang.ClassNotFoundException f) {
            throw new RuntimeException("Unexpected exception: " + f.getMessage());
        }
    }

    public static void main(String... args) throws Throwable {

        // Test loading a class with two Record attributes. This should throw ClassFormatError.
        runTest("twoRecordAttributes",
                "Multiple Record attributes in class");

        // Test loading a Record type marked abstract. This should throw ClassFormatError.
        runTest("abstractRecord",
                "Record attribute in non-final or abstract class");

        // Test loading a Record type that is not final. This should throw ClassFormatError.
        runTest("notFinalRecord",
                "Record attribute in non-final or abstract class");

        // Test loading a Record type that is badly formed. This should throw ClassFormatError.
        runTest("badRecordAttribute",
                "Invalid constant pool index 13 for descriptor in Record attribute");

        // Test loading a Record type that is too small. This should throw ClassFormatError.
        runTest("shortRecordAttribute", "Truncated class file");

        // Test that loading a class with an old class file version ignores a
        // badly formed Record attribute. No exception should be thrown.
        Class newClass = Class.forName("oldRecordAttribute");

        // Test that loading a class whose super class is not java.lang.Record
        // ignores a badly formed Record attribute. No exception should be thrown.
        newClass = Class.forName("superNotJLRecord");
    }
}
