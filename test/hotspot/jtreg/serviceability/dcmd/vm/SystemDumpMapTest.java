/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
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

import org.testng.annotations.Test;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;

/*
 * @test
 * @summary Test of diagnostic command System.map
 * @library /test/lib
 * @requires (os.family=="linux")
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run testng SystemDumpMapTest
 */
public class SystemDumpMapTest {

    private void run_test(CommandExecutor executor, boolean useDefaultFileName) {

        String filenameOption = useDefaultFileName ? "" : "-F=test-map.txt";

        OutputAnalyzer output = executor.execute("System.dump_map " + filenameOption);
        output.reportDiagnosticSummary();

        String filename = useDefaultFileName ?
            output.firstMatch("Memory map dumped to \"(\\S*vm_memory_map_\\d+\\.txt)\".*", 1) :
            output.firstMatch("Memory map dumped to \"(\\S*test-map.txt)\".*", 1);

        if (filename == null) {
            throw new RuntimeException("Did not find dump file in output.\n");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            boolean NMTOff = output.contains("NMT is disabled");
            String regexBase = ".*0x\\p{XDigit}+ - 0x\\p{XDigit}+ +\\d+";
            HashSet<Pattern> patterns = new HashSet<>();
            patterns.add(Pattern.compile(regexBase + ".*jvm.*"));
            if (!NMTOff) { // expect VM annotations if NMT is on
                patterns.add(Pattern.compile(regexBase + ".*JAVAHEAP.*"));
                patterns.add(Pattern.compile(regexBase + ".*META.*"));
                patterns.add(Pattern.compile(regexBase + ".*CODE.*"));
                patterns.add(Pattern.compile(regexBase + ".*STACK.*main.*"));
            }
            do {
                String line = reader.readLine();
                if (line != null) {
                    for (Pattern pat : patterns) {
                        if (pat.matcher(line).matches()) {
                            patterns.remove(pat);
                            break;
                        }
                    }
                } else {
                    break;
                }
            } while (patterns.size() > 0);

            if (patterns.size() > 0) {
                System.out.println("Missing patterns in dump:");
                for (Pattern pat : patterns) {
                    System.out.println(pat);
                }
                throw new RuntimeException("Missing patterns");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void run(CommandExecutor executor) {
        run_test(executor, false);
        run_test(executor, true);
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
