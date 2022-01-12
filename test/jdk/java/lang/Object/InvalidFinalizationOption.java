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

/*
 * @test
 * @bug 8276422
 * @summary Invalid/missing values for the finalization option should be rejected
 * @library /test/lib
 * @run driver InvalidFinalizationOption
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class InvalidFinalizationOption {
    public static void main(String[] args) throws Exception {
        record TestData(String arg, String expected) { }

        TestData[] testData = {
            new TestData("--finalization",        "Unrecognized option"),
            new TestData("--finalization=",       "Invalid finalization value"),
            new TestData("--finalization=azerty", "Invalid finalization value")
        };

        for (var data : testData) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(data.arg);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain(data.expected);
            output.shouldHaveExitValue(1);
        }
    }
}
