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

package jdk.jfr.jcmd;

import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary The test verifies JFR.configure command can only set certain options before JFR is started.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jcmd.TestJcmdConfigureReadOnly
 */
public class TestJcmdConfigureReadOnly {
    public static void main(String[] args) throws Exception {
        // Set an option before initializing JFR.
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.configure", "stackdepth=" + 128);
        output.shouldContain("Stack depth: 128");
        // JFR.start will initialize JFR.
        output = JcmdHelper.jcmd("JFR.start");
        JcmdAsserts.assertRecordingHasStarted(output);
        // Attempt to set a new value after JFR initialization.
        output = JcmdHelper.jcmd("JFR.configure", "stackdepth=" + 256);
        // After initialization, the option is considered read-only.
        output.shouldContain("Stack depth: 128");
    }
}
