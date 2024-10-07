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
 * @test TestCompileThresholdScaling
 * @bug 8283807
 * @summary With a very large value of CompileThresholdScaling all scaled
 *          thresholds should be outside the allowed range
 * @library /test/lib
 * @run driver compiler.arguments.TestCompileThresholdScaling
 */

package compiler.arguments;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;

public class TestCompileThresholdScaling {

    public static void main(String args[]) throws Throwable {
        checkCompileThresholdScaling(Double.MAX_VALUE, true);
        checkCompileThresholdScaling(Double.valueOf(Integer.MAX_VALUE), true);
        checkCompileThresholdScaling(1.0, false);
    }

    static void checkCompileThresholdScaling(double value, boolean fail) throws Throwable {
        OutputAnalyzer out = ProcessTools.executeTestJava("-XX:CompileThresholdScaling=" + value, "--version");
        out.shouldHaveExitValue(0);
        String output = out.getOutput();

        List<String> thresholdList = List.of(
        "Tier0InvokeNotifyFreqLog", "Tier0BackedgeNotifyFreqLog", "Tier3InvocationThreshold",
        "Tier3MinInvocationThreshold", "Tier3CompileThreshold", "Tier3BackEdgeThreshold",
        "Tier2InvokeNotifyFreqLog", "Tier2BackedgeNotifyFreqLog", "Tier3InvokeNotifyFreqLog",
        "Tier3BackedgeNotifyFreqLog", "Tier23InlineeNotifyFreqLog", "Tier4InvocationThreshold",
        "Tier4MinInvocationThreshold", "Tier4CompileThreshold", "Tier4BackEdgeThreshold");

        String pattern = ".*CompileThreshold .* must be between .* and .*";
        boolean found = Pattern.compile(pattern).matcher(output).find();
        Asserts.assertEquals(found, fail, "Unexpected result");

        for (String threshold : thresholdList) {
            pattern = ".*" + threshold + "=.* is outside the allowed range";
            Asserts.assertEquals(found, fail, "Unexpected result");
        }
    }

}
