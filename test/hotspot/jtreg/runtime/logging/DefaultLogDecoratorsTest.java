/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @requires vm.flagless
 * @summary Running -Xlog with tags which have disabled default decorators should not yield decorated logs
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver DefaultLogDecoratorsTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class DefaultLogDecoratorsTest {
    private static Pattern DECORATED_LINE = Pattern.compile("(\\[.+\\])+ .*");

    private static void doTest(boolean shouldHave, String... xlog) throws Exception {
        List<String> argsList = new ArrayList(Arrays.asList(xlog));
        argsList.add(InnerClass.class.getName());
        String[] args = argsList.toArray(new String[0]);
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        List<String> allLines = Files.readAllLines(Path.of("decorators.log"));
        for (String line : allLines) {
            if (DECORATED_LINE.matcher(line).find() == !shouldHave) {
                throw new RuntimeException("Logging should " + (shouldHave ? "" : "not ") + "contain decorators!");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // JIT inlining logging, as per defaults, shall have all decorators disabled
        doTest(false, "-Xlog:jit+inlining*=trace:decorators.log");

        // If decorators are specified, the defaults are not taken into account
        doTest(true, "-Xlog:jit+inlining*=trace:decorators.log:time");

        // Even if decorators are only supplied for another tag(s), the defaults are not taken into account
        doTest(true, "-Xlog:jit+inlining*=trace:decorators.log", "-Xlog:gc*=info:decorators.log:time");

        // Defaults are not taken into account also when another tag implicitly imposes the "standard" defaults
        doTest(true, "-Xlog:jit+inlining*=trace:decorators.log", "-Xlog:gc*=info:decorators.log");

        // Other logging shall not be affected by a tag with defaults
        doTest(true, "-Xlog:gc*=trace:decorators.log");
    }

    public static class InnerClass {
        public static void main(String[] args) throws Exception {
            System.out.println("DefaultLogDecorators test");
        }
    }
}
