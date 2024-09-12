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
 * @summary Running -Xlog with tags which have default decorators should pick them
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

public class DefaultLogDecoratorsTest { //
    private static Pattern DECORATED_LINE_JIT = Pattern.compile("\\[[0-9]*\\.?[0-9]*s\\]\\[\\w+\\s*\\]\\[jit(,\\w+)*\\s*\\] .*");
    private static Pattern DECORATED_LINE_GC  = Pattern.compile("\\[[0-9]*\\.?[0-9]*s\\]\\[\\w+\\s*\\]\\[gc(,\\w+)*\\s*\\] .*");

    public static void main(String[] args) throws Exception {
        // JIT logging, as per defaults, shall have all decorators disabled
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:jit*=trace:decorators.log",
                                                                             InnerClass.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        List<String> allLines = Files.readAllLines(Path.of("decorators.log"));
        for (String line : allLines) {
            if (DECORATED_LINE_JIT.matcher(line).find()) {
                throw new RuntimeException("JIT logging should not contain decorators!");
            }
        }


        // Other logging shall not be affected by a tag with defaults
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:gc*=trace:decorators.log",
                                                              InnerClass.class.getName());
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        allLines = Files.readAllLines(Path.of("decorators.log"));
        for (String line : allLines) {
            if (!DECORATED_LINE_GC.matcher(line).find()) {
                throw new RuntimeException("GC logging should contain decorators!");
            }
        }
    }

    public static class InnerClass {
        public static void main(String[] args) throws Exception {
            System.out.println("Compressed Oops (gc+heap+coops) test");
        }
    }
}
