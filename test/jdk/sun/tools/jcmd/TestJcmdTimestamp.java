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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

import jdk.test.lib.Asserts;
import jdk.test.lib.dcmd.FileJcmdExecutor;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.dcmd.JcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @summary test whether jcmd generic flag "-t" adds a timestamp to the output
 * @library /test/lib
 * @run main/othervm TestJcmdTimestamp
 */
public class TestJcmdTimestamp {

    public static void main(String[] args) throws Exception {
        TestJcmdTimestamp(new PidJcmdExecutor(), "-t VM.version", true /* expectTimestamp */);
        TestJcmdTimestamp(new PidJcmdExecutor(), "VM.version", false /* expectTimestamp */);
        TestJcmdTimestamp(new FileJcmdExecutor(), "-t VM.version", true /* expectTimestamp */);
        TestJcmdTimestamp(new FileJcmdExecutor(), "VM.version", false /* expectTimestamp */);
    }

    // timestamp should be there and it should be recent
    public static void assertTimestamp(final String line) throws java.time.format.DateTimeParseException {
        // ISO 8601. Example "2026-01-21T16:58:49.518+0100"
        final String timePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timePattern);
        final LocalDateTime parsedDateTime = LocalDateTime.parse(line, formatter);

        final Duration duration = Duration.between(parsedDateTime, LocalDateTime.now());
        Asserts.assertLessThan(duration.getSeconds(), 5L, "the timestamp is not recent");
    }


    private static void TestJcmdTimestamp(final JcmdExecutor executor,
            final String command,
            final boolean expectTimestamp) throws Exception {

        OutputAnalyzer output = executor.execute(command);
        output.shouldHaveExitValue(0);

        final String secondLine = output.getOutput().split("\\r?\\n")[1];

        if (expectTimestamp) {
            assertTimestamp(secondLine);
        }
        else {
            Asserts.assertThrows(java.time.format.DateTimeParseException.class, () -> assertTimestamp(secondLine));
        }
    }
}
