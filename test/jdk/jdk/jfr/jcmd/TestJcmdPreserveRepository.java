/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import jdk.test.lib.jfr.FileHelper;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;
/**
 * @test
 * @summary Test verifies that files are left after preserve-repository has been set using jcmd JFR.configure
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jcmd.TestJcmdPreserveRepository
 */
public class TestJcmdPreserveRepository {

    public static class TestProcess {
        public static void main(String... args) {
            OutputAnalyzer output = JcmdHelper.jcmd("JFR.configure", "preserve-repository=true");
            System.exit(output.getExitValue());
        }
    }

    public static void main(String[] args) throws Throwable {
        Path path = Path.of("./preserved");
        String[] arguments = {
            "-XX:StartFlightRecording",
            "-XX:FlightRecorderOptions:repository=" + path,
            "-Dtest.jdk=" + System.getProperty("test.jdk"),
            TestProcess.class.getName()
        };
        OutputAnalyzer output = ProcessTools.executeTestJava(arguments);
        output.shouldHaveExitValue(0);
        Optional<Path> p = Files.find(path, 99, (a,b) -> a.getFileName().toString().endsWith(".jfr")).findAny();
        if (p.isEmpty()) {
            throw new Exception("Could not find preserved files in repository");
        }
    }

}
