/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/* @test
 * @summary Unit tests for formatter
 * @library /test/lib
 * @compile Basic.java
 * @compile BasicBoolean.java
 * @compile BasicBooleanObject.java
 * @compile BasicByte.java
 * @compile BasicByteObject.java
 * @compile BasicChar.java
 * @compile BasicCharObject.java
 * @compile BasicShort.java
 * @compile BasicShortObject.java
 * @compile BasicInt.java
 * @compile BasicIntObject.java
 * @compile BasicLong.java
 * @compile BasicLongObject.java
 * @compile BasicBigInteger.java
 * @compile BasicFloat.java
 * @compile BasicFloatObject.java
 * @compile BasicDouble.java
 * @compile BasicDoubleObject.java
 * @compile BasicBigDecimal.java
 * @compile BasicDateTime.java
 * @bug 4906370 4962433 4973103 4989961 5005818 5031150 4970931 4989491 5002937
 *      5005104 5007745 5061412 5055180 5066788 5088703 6317248 6318369 6320122
 *      6344623 6369500 6534606 6282094 6286592 6476425 5063507 6469160 6476168
 *      8059175 8204229 8300869
 *
 * @run junit BasicTestLauncher
 */
public class BasicTestLauncher {

    // Test class
    private static final String TEST_CLASS = "Basic";

    /**
     * Executes Formatter Basic tests
     * @param timeZone the time zone to run tests against
     */
    @ParameterizedTest
    @ValueSource(strings = { "US/Pacific", "Asia/Novosibirsk" })
    void testTimeZone(String timeZone) throws IOException{
        System.out.printf("$$$ Testing against %s!%n", timeZone);
        OutputAnalyzer output = RunTest(timeZone);
        CheckTest(output);
        System.out.printf("$$$ %s passed as expected!%n", timeZone);
    }

    /**
     * Creates and runs the testJVM process using Basic class
     * @param timeZone the time zone to be set in the testJVM environment
     */
    private static OutputAnalyzer RunTest(String timeZone) throws IOException{
        // Build and run Basic class with correct configuration
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(TEST_CLASS);
        pb.environment().put("TZ", timeZone);
        Process process = pb.start();
        return new OutputAnalyzer(process);
    }

    /**
     * Validates if the testJVM process passed all tests
     * @param output is an Output Analyzer for the testJVM
     * @throws RuntimeException for all testJVM failures
     */
    private static void CheckTest(OutputAnalyzer output){
        output.shouldHaveExitValue(0)
                .reportDiagnosticSummary();
    }
}
