/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.startupargs;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 *
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jfr
 *
 * @run main jdk.jfr.startupargs.TestBadOptionValues
 */
public class TestBadOptionValues {

    private static final String START_FLIGHT_RECORDING = "-XX:StartFlightRecording=";
    private static final String FLIGHT_RECORDER_OPTIONS = "-XX:FlightRecorderOptions=";

    private static void test(String prepend, String expectedOutput, String... options) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer output;

        Asserts.assertGreaterThan(options.length, 0);

        for (String option : options) {
            pb = ProcessTools.createJavaProcessBuilder(prepend + option, "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain(expectedOutput);
        }
    }

    private static void testBoolean(String prepend, String... options) throws Exception {
        String[] splitOption;

        Asserts.assertGreaterThan(options.length, 0);

        for (String option : options) {
            splitOption = option.split("=", 2);
            test(prepend, String.format("Boolean parsing error in command argument '%s'. Could not parse: %s.", splitOption[0], splitOption[1]), option);
        }
    }

    private static void testJlong(String prepend, String... options) throws Exception {
        String[] splitOption;

        Asserts.assertGreaterThan(options.length, 0);

        for (String option : options) {
            splitOption = option.split("=", 2);
            test(prepend, String.format("Integer parsing error in command argument '%s'. Could not parse: %s.",
                splitOption[0], splitOption.length > 1 ? splitOption[1]: ""), option);
        }
    }

    public static void main(String[] args) throws Exception {
        // Nanotime options
        test(START_FLIGHT_RECORDING, "Integer parsing error nanotime value: syntax error",
            "delay=ms",
            "duration=",
            "maxage=q");
        test(START_FLIGHT_RECORDING, "Integer parsing error nanotime value: syntax error, value is null",
            "duration");
        test(START_FLIGHT_RECORDING, "Integer parsing error nanotime value: illegal unit",
            "delay=1000mq",
            "duration=2000mss",
            "maxage=-1000");
        test(START_FLIGHT_RECORDING, "Integer parsing error nanotime value: unit required",
            "delay=3037",
            "maxage=1");

        // Memory size options
        test(START_FLIGHT_RECORDING, "Parsing error memory size value: negative values not allowed",
            "maxsize=-1",
            "maxsize=-10k");

        test(FLIGHT_RECORDER_OPTIONS, "Parsing error memory size value: negative values not allowed",
            "threadbuffersize=-1M",
            "memorysize=-1g",
            "globalbuffersize=-0",
            "maxchunksize=-");

        test(START_FLIGHT_RECORDING, "Parsing error memory size value: syntax error, value is null",
            "maxsize");

        test(FLIGHT_RECORDER_OPTIONS, "Parsing error memory size value: syntax error, value is null",
            "threadbuffersize",
            "memorysize",
            "globalbuffersize",
            "maxchunksize");

        test(START_FLIGHT_RECORDING, "Parsing error memory size value: invalid value",
            "maxsize=");

        test(FLIGHT_RECORDER_OPTIONS, "Parsing error memory size value: invalid value",
            "threadbuffersize=a",
            "globalbuffersize=G",
            "maxchunksize=M10");

        // Jlong options
        testJlong(FLIGHT_RECORDER_OPTIONS,
            "stackdepth=q",
            "stackdepth=",
            "numglobalbuffers=10m",
            "numglobalbuffers",
            "numglobalbuffers=0x15");

        // Boolean options
        testBoolean(START_FLIGHT_RECORDING,
            "disk=on",
            "dumponexit=truee");

        testBoolean(FLIGHT_RECORDER_OPTIONS,
            "samplethreads=falseq",
            "retransform=0");

        // Not existing options
        test(START_FLIGHT_RECORDING, "Unknown argument 'dumponexitt' in diagnostic command.",
            "dumponexitt=true");
        test(FLIGHT_RECORDER_OPTIONS, "Unknown argument 'notexistoption' in diagnostic command.",
            "notexistoption");
    }
}
