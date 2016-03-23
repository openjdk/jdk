/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8148316 8148317
 * @summary Tests for output customization
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @build KullaTesting TestingInputStream ToolBox Compiler
 * @run testng ToolFormatTest
 */
import org.testng.annotations.Test;

@Test
public class ToolFormatTest extends ReplToolTesting {

    public void testSetFormat() {
        try {
            test(
                    (a) -> assertCommandOutputStartsWith(a, "/set newmode test command", "|  Created new feedback mode: test"),
                    (a) -> assertCommand(a, "/set field test pre '$ '", ""),
                    (a) -> assertCommand(a, "/set field test post ''", ""),
                    (a) -> assertCommand(a, "/set field test action 'ADD ' added-primary", ""),
                    (a) -> assertCommand(a, "/set field test action 'MOD ' modified-primary", ""),
                    (a) -> assertCommand(a, "/set field test action 'REP ' replaced-primary", ""),
                    (a) -> assertCommand(a, "/set field test action 'UP-ADD ' added-update", ""),
                    (a) -> assertCommand(a, "/set field test action 'UP-MOD ' modified-update", ""),
                    (a) -> assertCommand(a, "/set field test action 'UP-REP ' replaced-update", ""),
                    (a) -> assertCommand(a, "/set field test resolve 'OK' ok-*", ""),
                    (a) -> assertCommand(a, "/set field test resolve 'DEF' defined-*", ""),
                    (a) -> assertCommand(a, "/set field test resolve 'NODEF' notdefined-*", ""),
                    (a) -> assertCommand(a, "/set field test name ':%s ' ", ""),
                    (a) -> assertCommand(a, "/set field test type '[%s]' ", ""),
                    (a) -> assertCommand(a, "/set field test result '=%s ' ", ""),
                    (a) -> assertCommand(a, "/set format test '{pre}{action}{type}{name}{result}{resolve}' *-*-*", ""),
                    (a) -> assertCommand(a, "/set format test '{pre}HI this is enum' enum", ""),
                    (a) -> assertCommand(a, "/set feedback test", "$ Feedback mode: test"),
                    (a) -> assertCommand(a, "class D {}", "$ ADD :D OK"),
                    (a) -> assertCommand(a, "void m() {}", "$ ADD []:m OK"),
                    (a) -> assertCommand(a, "interface EX extends EEX {}", "$ ADD :EX NODEF"),
                    (a) -> assertCommand(a, "56", "$ ADD [int]:$4 =56 OK"),
                    (a) -> assertCommand(a, "class D { int hh; }", "$ REP :D OK$ OVERWROTE-UPDATE:D OK"),
                    (a) -> assertCommandOutputStartsWith(a, "/set feedback normal", "|  Feedback mode: normal")
            );
        } finally {
            assertCommandCheckOutput(false, "/set feedback normal", s -> {
            });
        }
    }

    public void testNewModeQuiet() {
        try {
            test(
                    (a) -> assertCommandOutputStartsWith(a, "/set newmode nmq quiet normal", "|  Created new feedback mode: nmq"),
                    (a) -> assertCommand(a, "/set feedback nmq", ""),
                    (a) -> assertCommand(a, "/se ne nmq2 q nor", ""),
                    (a) -> assertCommand(a, "/se fee nmq2", ""),
                    (a) -> assertCommand(a, "/set newmode nmc command normal", ""),
                    (a) -> assertCommandOutputStartsWith(a, "/set feedback nmc", "|  Feedback mode: nmc"),
                    (a) -> assertCommandOutputStartsWith(a, "/set newmode nm", "|  Created new feedback mode: nm"),
                    (a) -> assertCommandOutputStartsWith(a, "/set feedback nm", "|  Feedback mode: nm")
            );
        } finally {
            assertCommandCheckOutput(false, "/set feedback normal", s -> {
            });
        }
    }

    public void testSetError() {
        try {
            test(
                    (a) -> assertCommandOutputStartsWith(a, "/set newmode te command normal", "|  Created new feedback mode: te"),
                    (a) -> assertCommand(a, "/set field te errorpre 'ERROR: '", ""),
                    (a) -> assertCommandOutputStartsWith(a, "/set feedback te", ""),
                    (a) -> assertCommandCheckOutput(a, "/set ", assertStartsWith("ERROR: The /set command requires arguments")),
                    (a) -> assertCommandCheckOutput(a, "/set xyz", assertStartsWith("ERROR: Not a valid argument to /set")),
                    (a) -> assertCommandCheckOutput(a, "/set f", assertStartsWith("ERROR: Ambiguous argument to /set")),
                    (a) -> assertCommandCheckOutput(a, "/set feedback", assertStartsWith("ERROR: Expected a feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set feedback xyz", assertStartsWith("ERROR: Does not match any current feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set format", assertStartsWith("ERROR: Expected a feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set format xyz", assertStartsWith("ERROR: Does not match any current feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set format te", assertStartsWith("ERROR: Expected format missing")),
                    (a) -> assertCommandCheckOutput(a, "/set format te aaa", assertStartsWith("ERROR: Format 'aaa' must be quoted")),
                    (a) -> assertCommandCheckOutput(a, "/set format te 'aaa'", assertStartsWith("ERROR: At least one selector required")),
                    (a) -> assertCommandCheckOutput(a, "/set format te 'aaa' frog", assertStartsWith("ERROR: Not a valid case")),
                    (a) -> assertCommandCheckOutput(a, "/set format te 'aaa' import-frog", assertStartsWith("ERROR: Not a valid action")),
                    (a) -> assertCommandCheckOutput(a, "/set newmode", assertStartsWith("ERROR: Expected new feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set newmode te", assertStartsWith("ERROR: Expected a new feedback mode name")),
                    (a) -> assertCommandCheckOutput(a, "/set newmode x xyz", assertStartsWith("ERROR: Specify either 'command' or 'quiet'")),
                    (a) -> assertCommandCheckOutput(a, "/set newmode x quiet y", assertStartsWith("ERROR: Does not match any current feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt", assertStartsWith("ERROR: Expected a feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt te", assertStartsWith("ERROR: Expected format missing")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt te aaa xyz", assertStartsWith("ERROR: Format 'aaa' must be quoted")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt te 'aaa' xyz", assertStartsWith("ERROR: Format 'xyz' must be quoted")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt", assertStartsWith("ERROR: Expected a feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt te", assertStartsWith("ERROR: Expected format missing")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt te aaa", assertStartsWith("ERROR: Format 'aaa' must be quoted")),
                    (a) -> assertCommandCheckOutput(a, "/set prompt te 'aaa'", assertStartsWith("ERROR: Expected format missing")),
                    (a) -> assertCommandCheckOutput(a, "/set field", assertStartsWith("ERROR: Expected a feedback mode")),
                    (a) -> assertCommandCheckOutput(a, "/set field xyz", assertStartsWith("ERROR: Does not match any current feedback mode: xyz")),
                    (a) -> assertCommandCheckOutput(a, "/set field te xyz", assertStartsWith("ERROR: Not a valid field: xyz, must be one of: when")),
                    (a) -> assertCommandCheckOutput(a, "/set field te action", assertStartsWith("ERROR: Expected format missing")),
                    (a) -> assertCommandCheckOutput(a, "/set field te action 'act'", assertStartsWith("ERROR: At least one selector required"))
            );
        } finally {
            assertCommandCheckOutput(false, "/set feedback normal", s -> {
            });
        }
    }

    public void testSetHelp() {
        try {
            test(
                    (a) -> assertCommandOutputContains(a, "/help /set", "command to launch"),
                    (a) -> assertCommandOutputContains(a, "/help /set format", "vardecl"),
                    (a) -> assertCommandOutputContains(a, "/hel /se for", "vardecl"),
                    (a) -> assertCommandOutputContains(a, "/help /set editor", "temporary file")
            );
        } finally {
            assertCommandCheckOutput(false, "/set feedback normal", s -> {
            });
        }
    }

    public void testSetHelpError() {
        try {
            test(
                    (a) -> assertCommandOutputStartsWith(a, "/set newmode te command normal", "|  Created new feedback mode: te"),
                    (a) -> assertCommand(a, "/set field te errorpre 'ERROR: '", ""),
                    (a) -> assertCommandOutputStartsWith(a, "/set feedback te", "|  Feedback mode: te"),
                    (a) -> assertCommandOutputContains(a, "/help /set xyz", "ERROR: Not a valid argument to /set: xyz"),
                    (a) -> assertCommandOutputContains(a, "/help /set f", "ERROR: Ambiguous argument to /set: f")
            );
        } finally {
            assertCommandCheckOutput(false, "/set feedback normal", s -> {
            });
        }
    }
}
