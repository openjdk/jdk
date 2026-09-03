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
 */

/**
 * @test
 * @summary Test NullPointerException messages thrown in for NPE in null restricted type.
 * @bug 8341120
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @enablePreview
 * @compile -g NPEInPreviewTest.java
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowCodeDetailsInExceptionMessages NPEInPreviewTest interpreter
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1 -XX:+UnlockDiagnosticVMOptions -XX:+ShowCodeDetailsInExceptionMessages NPEInPreviewTest c1
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+ShowCodeDetailsInExceptionMessages NPEInPreviewTest c2
 */

import jdk.internal.vm.annotation.NullRestricted;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

public class NPEInPreviewTest {

    static boolean c1Mode;

    @LooselyConsistentValue
    static value class MyValue {
        int i;
        MyValue() { i = 0; }
    }

    static value class NotFlatValue {
        int i;
        int j;
        int k;
        NotFlatValue() { i = 5; j = 6; k = 7; }
    }

    @NullRestricted
    MyValue val;

    // Not null restricted.
    MyValue nullVal;

    NPEInPreviewTest() {
      val = new MyValue();
      super();
    }

    @NullRestricted
    static MyValue staticVal;
    // Not null restricted and null.
    static MyValue nullStaticVal;

    static void testNullRestrictedFieldError() {
        String expectedMessage = "Cannot assign field \"val\" because \"test\" is null or \"val\" is a null restricted field and there's an attempt to store null in it";
        try {
            var test = new NPEInPreviewTest();
            test.val = null;
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            System.out.println("*** " + message);
            Asserts.assertEquals(expectedMessage, message);
        }
    }

    static void testNullRestrictedFieldStoredInNullError() {
        String expectedMessage = "Cannot assign field \"val\" because \"test\" is null or \"val\" is a null restricted field and there's an attempt to store null in it";
        try {
            NPEInPreviewTest test = null;
            test.val = new MyValue();
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            System.out.println("*** " + message);
            Asserts.assertEquals(expectedMessage, message);
        }
    }

    static void testActualNullFieldError() {
        String expectedMessage = "Cannot assign field \"nullVal\" because \"self\" is null";
        // In C1 Xcomp mode, the field or klass isn't resolved, so no idea which this is.
        String c1ExpectedMessage = "Cannot assign field \"nullVal\" because \"self\" is null or \"nullVal\" is a null " +
                                   "restricted field and there's an attempt to store null in it";

        try {
            NPEInPreviewTest self = null;
            self.nullVal = null;
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            System.out.println("*** " + message);
            if (c1Mode) {
                Asserts.assertEquals(c1ExpectedMessage, message);
            } else {
                Asserts.assertEquals(expectedMessage, message);
            }
        }
    }


    // Should not get the message:
    // There cannot be a NullPointerException at bci 4 of method void NPEInPreviewTest.testNullRestrictedStaticFieldError()
    static void testNullRestrictedStaticFieldError() {
        String expectedMessage = "Cannot assign field \"staticVal\" because \"null\" cannot be stored into a null restricted field";

        try {
            staticVal = null;
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            System.out.println("*** " + message);
            Asserts.assertEquals(expectedMessage, message);
        }
    }

    static void testNullRestrictedStaticFieldError2() {
        String expectedMessage = "Cannot assign field \"staticVal\" because \"NPEInPreviewTest.nullStaticVal\" cannot be stored into a null restricted field";

        try {
            staticVal = nullStaticVal;
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            System.out.println("*** " + message);
            Asserts.assertEquals(expectedMessage, message);
        }
    }

    static void testNullRestrictedArrayError() {
        // This message comes from the interpreter/runtime code so is not processed by Helpful NPE.
        String expectedMessage = "Cannot store null in a null-restricted array";
        // This message comes from the c1 null check, so is processed by Helpful NPE.
        String c1ExpectedMessage = "Cannot store to object array because \"a\" is null or is a null-free array and there's an attempt to store null in it";

        try {
            var a = ValueClass.newNullRestrictedAtomicArray(MyValue.class, 10, new MyValue());
            a[4] = null;
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            System.out.println("*** " + message);
            if (c1Mode) {
                Asserts.assertEquals(c1ExpectedMessage, message);
            } else {
                Asserts.assertEquals(expectedMessage, message);
            }
        }
    }

    static void testNullRestrictedNotFlatArrayError() {
        String expectedMessage = "Cannot store to object array because \"a\" is null or is a null-free array and there's an attempt to store null in it";
        try {
            var a = ValueClass.newNullRestrictedAtomicArray(NotFlatValue.class, 10, new NotFlatValue());
            a[4] = null;
        } catch (NullPointerException npe) {
            String message = npe.getMessage();
            System.out.println("*** " + message);
            Asserts.assertEquals(expectedMessage, message);
        }
    }

    static {
        staticVal = new MyValue();
    }

    public static void main(String[] args) {
        c1Mode = args[0].equals("c1");
        testNullRestrictedFieldError();
        testNullRestrictedFieldStoredInNullError();
        testActualNullFieldError();
        testNullRestrictedStaticFieldError();
        testNullRestrictedStaticFieldError2();
        testNullRestrictedArrayError();
        testNullRestrictedNotFlatArrayError();
    }
}
