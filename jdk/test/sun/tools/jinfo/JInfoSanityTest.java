/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.testlibrary.Asserts.assertNotEquals;
import static jdk.testlibrary.Asserts.assertTrue;
import static jdk.testlibrary.Asserts.assertFalse;
import jdk.testlibrary.OutputAnalyzer;

/**
 * @test
 * @summary The test sanity checks functionality of 'jinfo -h', 'jinfo -help',
 *          and verifies jinfo exits abnormally if started with invalid options.
 * @library /lib/testlibrary
 * @modules java.management
 * @build jdk.testlibrary.* JInfoHelper
 * @run main JInfoSanityTest
 */
public class JInfoSanityTest {

    public static void main(String[] args) throws Exception {
        test_h();
        test_help();
        testVersion();
        testUnknownHost();
    }

    private static void test_h() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfoNoPid("-h");
        output.shouldHaveExitValue(0);
        assertFalse(output.getStderr().isEmpty(), "'jinfo -h' stderr should not be empty");
        assertTrue(output.getStdout().isEmpty(), "'jinfo -h' stdout should be empty");
    }

    private static void test_help() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfoNoPid("-help");
        output.shouldHaveExitValue(0);
        assertFalse(output.getStderr().isEmpty(), "'jinfo -help' stderr should not be empty");
        assertTrue(output.getStdout().isEmpty(), "'jinfo -help' stdout should be empty");
    }

    private static void testVersion() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfoNoPid("-version");
        output.shouldHaveExitValue(1);
        assertFalse(output.getStderr().isEmpty(), "'jinfo -version' stderr should not be empty");
        assertTrue(output.getStdout().isEmpty(), "'jinfo -version' stdout should be empty");
    }

    private static void testUnknownHost() throws Exception {
        String unknownHost = "Oja781nh2ev7vcvbajdg-Sda1-C";
        OutputAnalyzer output = JInfoHelper.jinfoNoPid("med@" + unknownHost);
        assertNotEquals(output.getExitValue(), 0, "A non-zero exit code should be returned for invalid operation");
        output.shouldMatch(".*(Connection refused to host\\:|UnknownHostException\\:) " + unknownHost + ".*");
    }

}
