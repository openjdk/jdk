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

package gc.arguments;

/*
 * @test TestSoftRefLRUPolicyMSPerMBOverflow
 * @bug 8383186
 * @summary Verify that too large SoftRefLRUPolicyMSPerMB values are rejected during option validation
 * @key flag-sensitive
 * @requires vm.bits == 64 & vm.opt.SoftRefLRUPolicyMSPerMB == null & vm.opt.AOTCache == null
 * @library /test/lib
 * @library /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver gc.arguments.TestSoftRefLRUPolicyMSPerMBOverflow
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;

public class TestSoftRefLRUPolicyMSPerMBOverflow {
    private static final long M = 1024 * 1024;
    private static final String XMX = "-Xmx4m";

    private static long getMaxHeapSize() throws Exception {
        OutputAnalyzer output = GCArguments.executeTestJava(
                XMX, "-XX:+PrintFlagsFinal", "-version");
        output.shouldHaveExitValue(0);
        Pattern pattern = Pattern.compile("MaxHeapSize\\s+:?=\\s+(\\d+)");
        Matcher matcher = pattern.matcher(output.getStdout());
        if (!matcher.find()) {
            throw new RuntimeException("Could not find MaxHeapSize in output:\n" + output.getOutput());
        }

        return Long.parseLong(matcher.group(1));
    }

    private static long computeOverflowingSoftRefLRUPolicyMSPerMB(long maxHeapSize) {
        if (maxHeapSize <= M) {
            throw new RuntimeException("Expected MaxHeapSize > 1MB, got MaxHeapSize=" + maxHeapSize);
        }

        return (Long.MAX_VALUE / (maxHeapSize / M)) + 1;
    }

    public static void main(String[] args) throws Exception {
        long maxHeapSize = getMaxHeapSize();
        long softRefLRUPolicyMSPerMB = computeOverflowingSoftRefLRUPolicyMSPerMB(maxHeapSize);

        // Test with overflowing SoftRefLRUPolicyMSPerMB
        OutputAnalyzer output = GCArguments.executeTestJava(
                XMX, "-XX:SoftRefLRUPolicyMSPerMB=" + softRefLRUPolicyMSPerMB, "-version");

        output.shouldContain("Desired lifetime of SoftReferences cannot be expressed correctly");
        output.shouldContain("MaxHeapSize (" + maxHeapSize + ") or SoftRefLRUPolicyMSPerMB (" +
                             softRefLRUPolicyMSPerMB + ") is too large");
        output.shouldContain("Error: Could not create the Java Virtual Machine.");
        output.shouldContain("Error: A fatal exception has occurred. Program will exit.");
        output.shouldHaveExitValue(1);

        System.out.println("MaxHeapSize=" + maxHeapSize + " bytes (" + maxHeapSize / M +
                           " MB), SoftRefLRUPolicyMSPerMB=" + softRefLRUPolicyMSPerMB);
        System.out.println(output.getOutput());

        // Sanity check with max allowed SoftRefLRUPolicyMSPerMB
        output = GCArguments.executeTestJava(
                XMX, "-XX:SoftRefLRUPolicyMSPerMB=" + (softRefLRUPolicyMSPerMB - 1), "-version");
        output.shouldHaveExitValue(0);
        System.out.println(output.getOutput());
    }
}
