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

/*
 * @test
 * @bug 8387729
 * @summary Verify that VM long options accept whitespace as an argument separator
 * @library /test/lib
 * @run main ${test.main.class}
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestLongOptionsWithWhiteSpace {
    private static final String[][] VALID_OPTIONS = {
        {"--enable-final-field-mutation", "ALL-UNNAMED"},
        {"--illegal-native-access", "warn"},
        {"--illegal-final-field-mutation", "warn"},
        {"--sun-misc-unsafe-memory-access", "warn"},
        {"--finalization", "disabled"}
    };

    private static final String[][] INVALID_OPTIONS = {
        {"--enable-final-field-mutation",
         "requires modules to be specified"},
        {"--illegal-native-access",
         "Value specified to --illegal-native-access"},
        {"--illegal-final-field-mutation",
         "Value specified to --illegal-final-field-mutation"},
        {"--sun-misc-unsafe-memory-access",
         "Value specified to --sun-misc-unsafe-memory-access"},
        {"--finalization",
         "Invalid finalization value"}
    };

    public static void main(String[] args) throws Exception {
        for (String[] option : VALID_OPTIONS) {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    option[0], option[1], "-version");

            output.shouldHaveExitValue(0);
            output.shouldContain("version");
        }

        for (String[] option : INVALID_OPTIONS) {
            // Verify the behavior when the whitespace-separated value is missing.
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(option[0],
                                                                        "-version");

            output.shouldNotHaveExitValue(0);
            output.shouldContain(option[1]);

            // Verify the behavior with an invalid whitespace-separated value.
            output = ProcessTools.executeLimitedTestJava(option[0],
                                                         "badOption",
                                                         "-version");

            if ("--enable-final-field-mutation".equals(option[0])){
                output.shouldHaveExitValue(0);
                output.shouldContain("Unknown module");
            } else {
                output.shouldNotHaveExitValue(0);
                output.shouldContain(option[1]);
            }
        }
    }
}
