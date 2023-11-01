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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
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
 * @run testng SystemMapTest
 */
public class SystemMapTest {
    public void run(CommandExecutor executor) {
        OutputAnalyzer output = executor.execute("System.map");
        output.reportDiagnosticSummary();
        boolean NMTOff = output.contains("NMT is disabled");

        String regexBase = ".*0x\\p{XDigit}+ - 0x\\p{XDigit}+ +\\d+";
        output.shouldMatch(regexBase + ".*jvm.*");
        if (!NMTOff) { // expect VM annotations if NMT is on
            output.shouldMatch(regexBase + ".*JAVAHEAP.*");
            output.shouldMatch(regexBase + ".*META.*");
            output.shouldMatch(regexBase + ".*CODE.*");
            output.shouldMatch(regexBase + ".*STACK.*main.*");
        }
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
