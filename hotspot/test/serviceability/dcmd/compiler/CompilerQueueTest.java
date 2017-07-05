/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test CompilerQueueTest
 * @bug 8054889
 * @library /testlibrary
 * @ignore 8069160
 * @build com.oracle.java.testlibrary.*
 * @build com.oracle.java.testlibrary.dcmd.*
 * @run testng CompilerQueueTest
 * @run testng/othervm -XX:-TieredCompilation CompilerQueueTest
 * @run testng/othervm -Xint CompilerQueueTest
 * @summary Test of diagnostic command Compiler.queue
 */

import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.dcmd.CommandExecutor;
import com.oracle.java.testlibrary.dcmd.JMXExecutor;
import org.testng.annotations.Test;

import java.util.Iterator;

public class CompilerQueueTest {

    /**
     * This test calls Jcmd (diagnostic command tool) Compiler.queue and
     * then parses the output, making sure that the output look ok.
     *
     *
     * Output example:
     *
     * Contents of C1 compile queue
     * ----------------------------
     * 73       3       java.lang.AbstractStringBuilder::append (50 bytes)
     * 74       1       java.util.TreeMap::size (5 bytes)
     * 75       3       java.lang.StringBuilder::append (8 bytes)
     * 83       3       java.util.TreeMap$ValueIterator::next (8 bytes)
     * 84       1       javax.management.MBeanFeatureInfo::getName (5 bytes)
     * ----------------------------
     * Contents of C2 compile queue
     * ----------------------------
     * Empty
     * ----------------------------
     *
     **/

    public void run(CommandExecutor executor) {

        // Get output from dcmd (diagnostic command)
        OutputAnalyzer output = executor.execute("Compiler.queue");
        Iterator<String> lines = output.asLines().iterator();

        while (lines.hasNext()) {
            String str = lines.next();
            if (str.startsWith("Contents of C")) {
                match(lines.next(), "----------------------------");
                str = lines.next();
                if (!str.equals("Empty")) {
                    while (str.charAt(0) != '-') {
                        validateMethodLine(str);
                        str = lines.next();
                    }
                } else {
                    str = lines.next();
                }
                match(str,"----------------------------");
            } else {
                Assert.fail("Failed parsing dcmd queue, line: " + str);
            }
        }
    }

    private static void validateMethodLine(String str) {
        // Skip until package/class name begins. Trim to remove whitespace that
        // may differ.
        String name = str.substring(14).trim();
        int sep = name.indexOf("::");
        if (sep == -1) {
            Assert.fail("Failed dcmd queue, didn't find separator :: in: " + name);
        }
        try {
            Class.forName(name.substring(0, sep));
        } catch (ClassNotFoundException e) {
            Assert.fail("Failed dcmd queue, Class for name: " + str);
        }
    }

    public static void match(String line, String str) {
        if (!line.equals(str)) {
            Assert.fail("String equals: " + line + ", " + str);
        }
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
