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

package jdk.test.lib.hotspot.ir_framework;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to report a {@link TestFormatException}.
 */
class TestFormat {
    private static final List<String> FAILURES = new ArrayList<>();

    public static void check(boolean test, String failureMessage) {
        if (!test) {
            FAILURES.add(failureMessage);
            throw new TestFormatException(failureMessage);
        }
    }

    public static void checkNoThrow(boolean test, String failureMessage) {
        if (!test) {
            FAILURES.add(failureMessage);
        }
    }

    public static void fail(String failureMessage) {
        FAILURES.add(failureMessage);
        throw new TestFormatException(failureMessage);
    }

    public static void failNoThrow(String failureMessage) {
        FAILURES.add(failureMessage);
    }

    public static void reportIfAnyFailures() {
        if (FAILURES.isEmpty()) {
            // No format violation detected.
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\nOne or more format violations have been detected:\n\n");
        builder.append("Violations (").append(FAILURES.size()).append(")\n");
        builder.append("-------------").append("-".repeat(String.valueOf(FAILURES.size()).length())).append("\n");
        for (String failure : FAILURES) {
            builder.append(" - ").append(failure).append("\n");
        }
        builder.append("/============/");
        FAILURES.clear();
        throw new TestFormatException(builder.toString());
    }
}
