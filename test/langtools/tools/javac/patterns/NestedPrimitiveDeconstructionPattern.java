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

/**
 * @test
 * @compile --enable-preview -source ${jdk.version} NestedPrimitiveDeconstructionPattern.java
 * @run main/othervm --enable-preview NestedPrimitiveDeconstructionPattern
 */

import java.util.Objects;

public class NestedPrimitiveDeconstructionPattern {

    public static void main(String... args) throws Throwable {
        new NestedPrimitiveDeconstructionPattern().doTestR();
    }

    void doTestR() {
        assertEquals("OK", switchR1(new R(3, 42d)));
        assertEquals("OK", switchR1_int_double(new R_i(3, 42d)));
    }

    record R(Integer x, Double y) {}

    String switchR1(R r) {
        return switch (r) {
            case R(Integer x, Double y) -> "OK";
        };
    }

    record R_i(int x, double y) {}

    String switchR1_int_double(R_i r) {
        return switch (r) {
            case R_i(int x, double y) -> "OK";
        };
    }

    private void assertEquals(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
