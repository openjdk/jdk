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

/**
 * @test
 * @bug 8376296
 * @summary Test that ZGC does not crash with boundary values for MaxVirtMemFraction
 * @library /test/lib
 * @library /
 * @requires vm.gc.Z
 * @run main/othervm ${test.main.class}
 */

import jdk.test.lib.process.OutputAnalyzer;

public class TestMaxVirtMemFractionZGC {
    public static void main(String[] args) throws Exception {

        OutputAnalyzer output = GCArguments.executeTestJava("-XX:+UseZGC", "-XX:MaxVirtMemFraction=1", "-version");
        output.shouldHaveExitValue(0);

        long val = ((long) 2) << 59;
        output = GCArguments.executeTestJava("-XX:+UseZGC", "-XX:MaxVirtMemFraction=" + val, "-version");
        output.shouldContain("Error occurred during initialization of VM");
        output.shouldContain("Too small maximum heap");
        output.shouldHaveExitValue(1);
    }
}
