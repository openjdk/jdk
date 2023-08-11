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

/*
 * @test
 * @bug 8293578
 * @summary Ensure constant types are removed correctly for <string>.getClass().
 * @compile ConstantTypes.java
 * @run main ConstantTypes
 */

import java.util.Objects;

public class ConstantTypes {
    public static void main(String... args) throws Throwable {
        new ConstantTypes().testStringCreation1();
        new ConstantTypes().testStringCreation2();
        new ConstantTypes().testStringCreation3();
        new ConstantTypes().testStringCreation4();
        new ConstantTypes().testStringFolding();
    }

    private void testStringCreation1() throws Throwable {
        var testC = "incorrect".getClass();
        var testV = testC.getConstructor(String.class)
                         .newInstance("correct");
        String actual = testV;
        String expected = "correct";
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Unexpected result: " + actual);
        }
    }

    private void testStringCreation2() throws Throwable {
        var test = "incorrect".getClass()
                              .getConstructor(String.class)
                              .newInstance("correct");
        String actual = test;
        String expected = "correct";
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Unexpected result: " + actual);
        }
    }

    private void testStringCreation3() throws Throwable {
        final var testC = "incorrect";
        var testV = testC.getClass()
                         .getConstructor(String.class)
                         .newInstance("correct");
        String actual = testV;
        String expected = "correct";
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Unexpected result: " + actual);
        }
    }

    private void testStringCreation4() throws Throwable {
        var testC = "incorrect";
        var testV = testC.getClass()
                         .getConstructor(String.class)
                         .newInstance("correct");
        String actual = testV;
        String expected = "correct";
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Unexpected result: " + actual);
        }
    }

    private void testStringFolding() {
        final var v1 = "1";
        final var v2 = "2";
        String actual = v1 + v2;
        String expected = "12";
        if (actual != expected) { //intentional reference comparison
            throw new AssertionError("Value not interned!");
        }
    }

}