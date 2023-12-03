/*
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
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
 *
 */

/*
 * @test id=test-absolute-limit
 * @summary Verify -XX:RssLimit with an absolute limit
 * @requires vm.flagless
 * @requires os.family != "aix"
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver RssLimitTest test-absolute-limit
 */

/*
 * @test id=test-relative-limit
 * @summary Verify -XX:RssLimit with a relative limit
 * @requires vm.flagless
 * @requires os.family != "aix"
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver RssLimitTest test-relative-limit
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RssLimitTest {

    private static OutputAnalyzer runWithSettings(String... extraSettings) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("-XX:+UnlockDiagnosticVMOptions"); // RssLimit is diagnostic
        args.add("-XX:-CreateCoredumpOnCrash");
        args.add("-Xlog:os+rss");
        args.addAll(Arrays.asList(extraSettings));
        args.add(RssLimitTest.class.getName());
        args.add("sleep");
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
        return new OutputAnalyzer(pb.start());
    }

    private static void testAbsoluteLimit() throws IOException {
        OutputAnalyzer o = runWithSettings(
                "-XX:RssLimit=100m",
                "-XX:RssLimitCheckInterval=500",
                "-Xmx100m",
                "-Xms100m",
                "-XX:+AlwaysPreTouch");
        o.shouldNotHaveExitValue(0);
        o.shouldContain("RssWatcher task: interval=500ms, limit=100M");
        o.shouldMatch("# +Error: Resident Set Size \\(\\d+ bytes\\) reached RssLimit \\(104857600 bytes\\)");
    }

    private static void testRelativeLimit() throws IOException {
        // We start with 0,01% of total memory. Depending on the size of the test machine,
        // this may or may not crash us.
        DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance();
        char dot = format.getDecimalFormatSymbols().getDecimalSeparator();
        String limitPercent = "0" + dot + "01%";
        OutputAnalyzer o = runWithSettings(
                "-XX:RssLimit=" + limitPercent,
                "-XX:RssLimitCheckInterval=500",
                "-Xmx100m",
                "-Xms100m",
                "-XX:+AlwaysPreTouch");
        o.shouldContain("RssWatcher task: interval=500ms, limit=" + limitPercent + " of total memory");
        String pat = "Setting RssWatcher limit to (\\d+) bytes \\(0.01% of total memory of (\\d+) bytes\\)";
        String limitString = o.firstMatch(pat, 1);
        String totalString = o.firstMatch(pat, 2);
        long limit = Long.parseLong(limitString);
        long total = Long.parseLong(totalString);
        long fudge = 100;
        long expectedLimit = total / 10000;
        if (limit > expectedLimit + fudge && limit < expectedLimit - fudge) {
            throw new RuntimeException("strange limit");
        }
        if (limit < 100 * 1024 * 1024) {
            o.shouldNotHaveExitValue(0);
        }
        if (o.getExitValue() != 0) {
            o.shouldMatch("# +Error: Resident Set Size \\(\\d+ bytes\\) reached RssLimit \\(" + limit + " bytes\\).*");
        }
    }

    public static void main(String args[]) throws Exception {
        switch (args[0]) {
            case "sleep":
                Thread.sleep(4000);
                throw new RuntimeException("Did not expect to live at this point.");
            case "test-absolute-limit":
                testAbsoluteLimit();
                break;
            case "test-relative-limit":
                testRelativeLimit();
                break;
            default:
                throw new RuntimeException("Invalid argument (" + args[0] + ")");
        }
    }
}
