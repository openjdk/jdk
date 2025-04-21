/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8350808
 * @summary Check for proper formatting of SnippetEvent.toString()
 * @run testng SnippetEventToStringTest
 */

import java.util.Map;
import java.util.List;

import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import jdk.jshell.execution.LocalExecutionControlProvider;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class SnippetEventToStringTest {

    @DataProvider(name = "cases")
    public String[][] sourceLevels() {
        return new String[][] {
            { "*",                              ",causeSnippet=null" },
            { "123",                            ",value=123" },
            { "throw new Exception(\"foo\");",  ",exception=jdk.jshell.EvalException: foo" }
        };
    }

    @Test(dataProvider = "cases")
    private void verifySnippetEvent(String source, String match) {
        try (JShell jsh = JShell.builder().executionEngine(new LocalExecutionControlProvider(), Map.of()).build()) {
            List<SnippetEvent> result = jsh.eval(source);
            assertEquals(result.size(), 1);
            String string = result.get(0).toString();
            if (!string.contains(match))
                throw new AssertionError(String.format("\"%s\" not found in \"%s\"", match, string));
        }
    }
}
