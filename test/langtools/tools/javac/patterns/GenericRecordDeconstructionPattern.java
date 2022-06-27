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
 * @compile --enable-preview -source ${jdk.version} GenericRecordDeconstructionPattern.java
 * @run main/othervm --enable-preview GenericRecordDeconstructionPattern
 */

import java.util.Objects;
import java.util.function.Function;

public class GenericRecordDeconstructionPattern {

    public static void main(String... args) throws Throwable {
        new GenericRecordDeconstructionPattern().run();
    }

    void run() {
        runTest(this::runIf);
        runTest(this::runSwitch);
        runTest(this::runSwitchExpression);
    }

    void runTest(Function<Box<String>, Integer> test) {
        Box<String> b = new Box<>(null);
        assertEquals(1, test.apply(b));
    }

    int runIf(Box<String> b) {
        if (b instanceof Box<String>(String s)) return 1;
        return -1;
    }

    int runSwitch(Box<String> b) {
        switch (b) {
            case Box<String>(String s): return 1;
            default: return -1;
        }
    }

    int runSwitchExpression(Box<String> b) {
        return switch (b) {
            case Box<String>(String s) -> 1;
            default -> -1;
        };
    }

    record Box<V>(V v) {
    }

    void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + "," +
                                     "got: " + actual);
        }
    }

    void fail(String message) {
        throw new AssertionError(message);
    }

    public static class TestPatternFailed extends AssertionError {

        public TestPatternFailed(String message) {
            super(message);
        }

    }

}
