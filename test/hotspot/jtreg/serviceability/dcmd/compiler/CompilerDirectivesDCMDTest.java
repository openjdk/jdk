/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test CompilerDirectivesDCMDTest
 * @bug 8137167
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 * @run testng/othervm CompilerDirectivesDCMDTest
 * @summary Test of diagnostic command
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.Platform;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CompilerDirectivesDCMDTest {

    public static String filename;

    public void run(CommandExecutor executor) {

        if (Platform.isServer() && !Platform.isEmulatedClient()) {
            filename = System.getProperty("test.src", ".") + File.separator + "control2.txt";
        } else {
            filename = System.getProperty("test.src", ".") + File.separator + "control1.txt";
        }
        testPrintCommand(executor);
        testAddAndRemoveCommand(executor, false /*don't force deopt*/);
        testAddAndRemoveCommand(executor, true /*force deopt*/);

        filename = System.getProperty("test.src", ".") + File.separator + "control3.txt";
        testForcedDeopt(executor);
    }

    public static void testPrintCommand(CommandExecutor executor) {

        // Get output from dcmd (diagnostic command)
        OutputAnalyzer output = executor.execute("Compiler.directives_print");
        int count = find(output, "Directive:");
        if (count < 1) {
            Assert.fail("Expected at least one directive - found " + count);
        }
    }

    public static void testForcedDeopt(CommandExecutor executor) {
        OutputAnalyzer output;

        // j.l.S.equals is already compiled, so in the output of the Compiler.codelist
        // command we see the method in state 0 (in use). After forced de-optimisation,
        // the method will be shown in state 2 (marked for de-optimisation) or
        // doesn't appear in the output at all.

        output = executor.execute("Compiler.directives_replace " + filename + " -d");
        output = executor.execute("Compiler.codelist ");
        int count = findPattern(output, "[0-9]+ 4 0 java.lang.String.equals\\(.*");
        if (count != 0) {
            Assert.fail("Expected no c2 compiled instances of j.l.S.equals - found " + count);
        }
    }

    public static void testAddAndRemoveCommand(CommandExecutor executor, boolean force_deopt) {
        OutputAnalyzer output;
        int count = 0;
        String fdeo = (force_deopt) ? " -d" : "";

        // Start with clearing stack - expect only default directive left
        output = executor.execute("Compiler.directives_clear" + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 1) {
            Assert.fail("Expected one directives - found " + count);
        }

        // Test that we can not remove the default directive
        output = executor.execute("Compiler.directives_remove" + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 1) {
            Assert.fail("Expected one directives - found " + count);
        }

        // Test adding some directives from file
        output = executor.execute("Compiler.directives_add " + filename + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 3) {
            Assert.fail("Expected three directives - found " + count);
        }

        // Test replace directives from file
        output = executor.execute("Compiler.directives_replace " + filename + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 3) {
            Assert.fail("Expected three directives - found " + count);
        }

        // Test remove one directive
        output = executor.execute("Compiler.directives_remove" + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 2) {
            Assert.fail("Expected two directives - found " + count);
        }

        // Test adding directives again
        output = executor.execute("Compiler.directives_add " + filename + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 4) {
            Assert.fail("Expected four directives - found " + count);
        }

        // Test clearing
        output = executor.execute("Compiler.directives_clear" + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 1) {
            Assert.fail("Expected one directives - found " + count);
        }

        // Test clear when already cleared
        output = executor.execute("Compiler.directives_clear" + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 1) {
            Assert.fail("Expected one directives - found " + count);
        }

        // Test remove one directive when empty
        output = executor.execute("Compiler.directives_remove" + fdeo);
        output = executor.execute("Compiler.directives_print");
        count = find(output, "Directive:");
        if (count != 1) {
            Assert.fail("Expected one directive - found " + count);
        }
    }

    public static int find(OutputAnalyzer output, String find) {
        int count = 0;

        for (String line : output.asLines()) {
            if (line.startsWith(find)) {
                count++;
            }
        }
        return count;
    }

    public static int findPattern(OutputAnalyzer output, String regexp) {
        int count = 0;
        Pattern pattern = Pattern.compile(regexp);

        for (String line : output.asLines()) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                count++;
            }
        }
        return count;
    }


    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
