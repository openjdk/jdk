/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.io.File;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.FileHelper;

/**
 * @test
 * @summary The test verifies JFR.start/dump/stop commands
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:StartFlightRecording=name=test,filename=output_%p.jfr jdk.jfr.jcmd.TestFilenameExpansion
 */
public class TestFilenameExpansion {

    public static void main(String[] args) throws Exception {
        String pid = Long.toString(ProcessHandle.current().pid());

        JcmdHelper.jcmd("JFR.stop name=test");
        checkFileAndDelete("output_" + pid + ".jfr");

        testJcmd("output.jfr", "output.jfr");
        testJcmd("output_%p.jfr", "output_" + pid + ".jfr");
        testJcmd("%p_output_%p.jfr", pid + "_output_" + pid + ".jfr");
        testJcmd("%%_output_%p.jfr", "%_output_" + pid + ".jfr");
        testJcmd("%a_output_%%", "%a_output_%");
    }

    private static void testJcmd(String name, String finalName) throws Exception {
        // set when JFR.start
        JcmdHelper.jcmd("JFR.start name=test filename=" + name);
        JcmdHelper.jcmd("JFR.dump name=test");
        checkFileAndDelete(finalName);
        JcmdHelper.jcmd("JFR.stop name=test");

        // set when JFR.dump
        JcmdHelper.jcmd("JFR.start name=test");
        JcmdHelper.jcmd("JFR.dump name=test filename=" + name);
        checkFileAndDelete(finalName);
        JcmdHelper.jcmd("JFR.stop name=test");

        // set when JFR.stop
        JcmdHelper.jcmd("JFR.start name=test");
        JcmdHelper.jcmd("JFR.stop name=test filename=" + name);
        checkFileAndDelete(finalName);
    }

    private static void checkFileAndDelete(String filename) {
        File file = new File(filename);
        Asserts.assertTrue(file.exists(), file.getAbsolutePath() + " does not exist");
        Asserts.assertTrue(file.isFile(), file.getAbsolutePath() + " is not a file");
        Asserts.assertTrue(file.delete(), "Delete " + file.getAbsolutePath() + " failed");
        Asserts.assertFalse(file.exists(), file.getAbsolutePath() + " should be deleted");
    }
}
