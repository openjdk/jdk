/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.testlibrary.OutputAnalyzer;
import static jdk.testlibrary.Asserts.assertTrue;

/**
 * @test
 * @summary The test sanity checks functionality of 'jinfo', 'jinfo -sysprops' and 'jinfo -flags'
 * @library /lib/testlibrary
 * @build jdk.testlibrary.* JInfoHelper
 * @run main/othervm -XX:+HeapDumpOnOutOfMemoryError JInfoRunningProcessTest
 */
public class JInfoRunningProcessTest {

    public static void main(String[] args) throws Exception {
        testNoOptions();
        testSysprops();
        testFlags();
    }

    private static void testNoOptions() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo();
        output.shouldHaveExitValue(0);
        assertTrue(output.getStderr().isEmpty(), "'jinfo' stderr should be empty");
        output.shouldContain("+HeapDumpOnOutOfMemoryError");
    }

    private static void testSysprops() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo("-sysprops");
        output.shouldHaveExitValue(0);
        assertTrue(output.getStderr().isEmpty(), "'jinfo -sysprops' stderr should be empty");
    }

    private static void testFlags() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo("-flags");
        output.shouldHaveExitValue(0);
        assertTrue(output.getStderr().isEmpty(), "'jinfo -flags' stderr should be empty");
        output.shouldContain("+HeapDumpOnOutOfMemoryError");
    }

}
