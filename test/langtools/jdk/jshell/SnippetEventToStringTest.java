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
 * @run junit SnippetEventToStringTest
 */

import java.util.Map;
import java.util.List;

import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import jdk.jshell.execution.LocalExecutionControlProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SnippetEventToStringTest {

    public String[][] sourceLevels() {
        return new String[][] {
            { "*",                              ",causeSnippet=null" },
            { "123",                            ",value=123" },
            { "throw new Exception(\"foo\");",  ",exception=jdk.jshell.EvalException: foo" }
        };
    }

    @ParameterizedTest
    @MethodSource("sourceLevels")
    void verifySnippetEvent(String source, String match) {
        try (JShell jsh = JShell.builder().executionEngine(new LocalExecutionControlProvider(), Map.of()).build()) {
            List<SnippetEvent> result = jsh.eval(source);
            assertEquals(1, result.size());
            String string = result.get(0).toString();
            if (!string.contains(match))
                throw new AssertionError(String.format("\"%s\" not found in \"%s\"", match, string));
        }
    }
}
