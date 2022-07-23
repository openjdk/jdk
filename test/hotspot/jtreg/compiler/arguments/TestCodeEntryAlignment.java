/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /
 * @bug 8281467
 * @requires vm.flagless
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 *
 * @summary Test large CodeEntryAlignments are accepted
 * @run driver compiler.arguments.TestCodeEntryAlignment
 */

package compiler.arguments;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestCodeEntryAlignment {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            driver();
        } else {
            System.out.println("Pass");
        }
    }

    private static List<String> cmdline(String[] args) {
        List<String> r = new ArrayList();
        r.addAll(Arrays.asList(args));
        r.add("compiler.arguments.TestCodeEntryAlignment");
        r.add("run");
        return r;
    }

    public static void shouldPass(String... args) throws IOException {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(cmdline(args));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
    }

    public static void driver() throws IOException {
        for (int align = 16; align < 256; align *= 2) {
            shouldPass(
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:CodeEntryAlignment=" + align
            );
        }
        for (int align = 256; align <= 1024; align *= 2) {
            shouldPass(
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:CodeCacheSegmentSize=" + align,
                "-XX:CodeEntryAlignment=" + align
            );
        }
    }

}
