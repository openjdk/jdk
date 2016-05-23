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
 * @bug 8157395 8157393 8157517
 * @summary Tests of jshell comand options, and undoing operations
 * @modules jdk.jshell/jdk.internal.jshell.tool
 * @build ToolCommandOptionTest ReplToolTesting
 * @run testng ToolCommandOptionTest
 */
import org.testng.annotations.Test;
import static org.testng.Assert.assertFalse;

@Test
public class ToolCommandOptionTest extends ReplToolTesting {

    public void listTest() {
        test(
                (a) -> assertCommand(a, "int x;",
                        "x ==> 0"),
                (a) -> assertCommand(a, "/li",
                        "1 : int x;"),
                (a) -> assertCommandOutputStartsWith(a, "/lis -st",
                        "\n  s1 : import"),
                (a) -> assertCommandOutputStartsWith(a, "/list -all",
                        "\n  s1 : import"),
                (a) -> assertCommandOutputContains(a, "/list -all",
                        "1 : int x;"),
                (a) -> assertCommandOutputContains(a, "/list -history",
                        "int x;"),
                (a) -> assertCommandOutputContains(a, "/li -h",
                        "/lis -st"),
                (a) -> assertCommand(a, "/list -furball",
                        "|  Unknown option: -furball -- /list -furball"),
                (a) -> assertCommand(a, "/list x",
                        "1 : int x;"),
                (a) -> assertCommand(a, "/li x -start",
                        "|  Options and snippets must not both be used: /list x -start"),
                (a) -> assertCommand(a, "/l -st -al",
                        "|  Conflicting options -- /list -st -al")
        );
    }

    public void typesTest() {
        test(
                (a) -> assertCommand(a, "int x",
                        "x ==> 0"),
                (a) -> assertCommand(a, "/types x",
                        "|  This command does not accept the snippet 'x' : int x;"),
                (a) -> assertCommand(a, "class C {}",
                        "|  created class C"),
                (a) -> assertCommand(a, "/ty",
                        "|    class C"),
                (a) -> assertCommand(a, "/ty -st",
                        ""),
                (a) -> assertCommand(a, "/types -all",
                        "|    class C"),
                (a) -> assertCommand(a, "/types -furball",
                        "|  Unknown option: -furball -- /types -furball"),
                (a) -> assertCommand(a, "/types C",
                        "|    class C"),
                (a) -> assertCommand(a, "/types C -start",
                        "|  Options and snippets must not both be used: /types C -start"),
                (a) -> assertCommand(a, "/ty -st -al",
                        "|  Conflicting options -- /types -st -al")
        );
    }

    public void dropTest() {
        test(false, new String[]{"-nostartup"},
                (a) -> assertCommand(a, "int x = 5;",
                        "x ==> 5"),
                (a) -> assertCommand(a, "x",
                        "x ==> 5"),
                (a) -> assertCommand(a, "long y;",
                        "y ==> 0"),
                (a) -> assertCommand(a, "/drop -furball",
                        "|  Unknown option: -furball -- /drop -furball"),
                (a) -> assertCommand(a, "/drop -all",
                        "|  Unknown option: -all -- /drop -all"),
                (a) -> assertCommandOutputStartsWith(a, "/drop z",
                        "|  No such snippet: z"),
                (a) -> assertCommandOutputStartsWith(a, "/drop 2",
                        "|  This command does not accept the snippet '2' : x"),
                (a) -> assertCommand(a, "/dr x y",
                        "|  dropped variable x\n" +
                        "|  dropped variable y"),
                (a) -> assertCommand(a, "/list",
                        "2 : x")
        );
    }

    public void setEditorTest() {
        test(
                (a) -> assertCommand(a, "/set editor -furball",
                        "|  Unknown option: -furball -- /set editor -furball"),
                (a) -> assertCommand(a, "/set editor -furball prog",
                        "|  Unknown option: -furball -- /set editor -furball prog"),
                (a) -> assertCommand(a, "/set editor -furball -mattress",
                        "|  Unknown option: -furball -mattress -- /set editor -furball -mattress"),
                (a) -> assertCommand(a, "/set editor -default prog",
                        "|  Specify -default option or program, not both -- /set editor -default prog"),
                (a) -> assertCommand(a, "/set editor prog",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/set editor prog -default",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/se ed prog -furball",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/set editor prog arg1 -furball arg3 -default arg4",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/set editor -default",
                        ""),
                (a) -> assertCommand(a, "/se edi -def",
                        ""),
                (a) -> assertCommand(a, "/set editor",
                        "|  The '/set editor' command requires a path argument")
        );
    }

    public void retainEditorTest() {
        test(
                (a) -> assertCommand(a, "/retain editor -furball",
                        "|  Unknown option: -furball -- /retain editor -furball"),
                (a) -> assertCommand(a, "/retain editor -furball prog",
                        "|  Unknown option: -furball -- /retain editor -furball prog"),
                (a) -> assertCommand(a, "/retain editor -furball -mattress",
                        "|  Unknown option: -furball -mattress -- /retain editor -furball -mattress"),
                (a) -> assertCommand(a, "/retain editor -default prog",
                        "|  Specify -default option or program, not both -- /retain editor -default prog"),
                (a) -> assertCommand(a, "/retain editor prog",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/retain editor prog -default",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/ret ed prog -furball",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/retain editor prog arg1 -furball arg3 -default arg4",
                        "|  Editor set to: prog"),
                (a) -> assertCommand(a, "/retain editor -default",
                        ""),
                (a) -> assertCommand(a, "/reta edi -def",
                        ""),
                (a) -> assertCommand(a, "/retain editor",
                        "")
        );
    }

    public void setStartTest() {
        test(
                (a) -> assertCommand(a, "/set start -furball",
                        "|  Unknown option: -furball -- /set start -furball"),
                (a) -> assertCommand(a, "/set start -furball pyle",
                        "|  Unknown option: -furball -- /set start -furball pyle"),
                (a) -> assertCommand(a, "/se st pyle -furball",
                        "|  Unknown option: -furball -- /set st pyle -furball"),
                (a) -> assertCommand(a, "/set start -furball -mattress",
                        "|  Unknown option: -furball -mattress -- /set start -furball -mattress"),
                (a) -> assertCommand(a, "/set start foo -default",
                        "|  Specify either one option or a startup file name -- /set start foo -default"),
                (a) -> assertCommand(a, "/set start frfg",
                        "|  File 'frfg' for '/set start' is not found."),
                (a) -> assertCommand(a, "/set start -default",
                        ""),
                (a) -> assertCommand(a, "/se sta -no",
                        ""),
                (a) -> assertCommand(a, "/set start",
                        "|  Specify either one option or a startup file name -- /set start")
        );
    }

    public void retainStartTest() {
        test(
                (a) -> assertCommand(a, "/retain start -furball",
                        "|  Unknown option: -furball -- /retain start -furball"),
                (a) -> assertCommand(a, "/retain start -furball pyle",
                        "|  Unknown option: -furball -- /retain start -furball pyle"),
                (a) -> assertCommand(a, "/ret st pyle -furball",
                        "|  Unknown option: -furball -- /retain st pyle -furball"),
                (a) -> assertCommand(a, "/retain start -furball -mattress",
                        "|  Unknown option: -furball -mattress -- /retain start -furball -mattress"),
                (a) -> assertCommand(a, "/retain start foo -default",
                        "|  Specify either one option or a startup file name -- /retain start foo -default"),
                (a) -> assertCommand(a, "/retain start frfg",
                        "|  File 'frfg' for '/retain start' is not found."),
                (a) -> assertCommand(a, "/retain start -default",
                        ""),
                (a) -> assertCommand(a, "/ret sta -no",
                        ""),
                (a) -> assertCommand(a, "/retain start",
                        "")
        );
    }

    public void setModeTest() {
        test(
                (a) -> assertCommandOutputStartsWith(a, "/set mode",
                        "|  Missing the feedback mode"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode *",
                        "|  Expected a feedback mode name: *"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode -quiet",
                        "|  Missing the feedback mode"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode -quiet *",
                        "|  Expected a feedback mode name: *"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode amode normal thing",
                        "|  Unexpected arguments at end of command: thing"),
                (a) -> assertCommand(a, "/set mode mymode",
                        "|  Created new feedback mode: mymode"),
                (a) -> assertCommand(a, "/set mode mymode -delete",
                        ""),
                (a) -> assertCommand(a, "/set mode mymode normal",
                        "|  Created new feedback mode: mymode"),
                (a) -> assertCommand(a, "/set mode -del mymode",
                        ""),
                (a) -> assertCommandOutputStartsWith(a, "/set mode mymode -command -quiet",
                        "|  Conflicting options"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode mymode -delete -quiet",
                        "|  Conflicting options"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode mymode -command -delete",
                        "|  Conflicting options"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode mymode -d",
                        "|  No feedback mode named: mymode"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode normal",
                        "|  Not valid with a predefined mode: normal"),
                (a) -> assertCommand(a, "/se mo -c mymode",
                        "|  Created new feedback mode: mymode"),
                (a) -> assertCommand(a, "/set feedback mymode",
                        "|  Feedback mode: mymode"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode mymode -delete",
                        "|  The current feedback mode 'mymode' cannot be deleted"),
                (a) -> assertCommand(a, "/set feedback no",
                        "|  Feedback mode: normal"),
                (a) -> assertCommandOutputStartsWith(a, "/set mode mymode -delete",
                        ""),
                (a) -> assertCommandCheckOutput(a, "/set feedback",
                        (s) -> assertFalse(s.contains("mymode"), "Didn't delete: " + s))
        );
    }

    public void setModeSmashTest() {
        test(
                (a) -> assertCommand(a, "/set mode mymode -command",
                        "|  Created new feedback mode: mymode"),
                (a) -> assertCommand(a, "/set feedback mymode",
                        "|  Feedback mode: mymode"),
                (a) -> assertCommand(a, "/set format mymode display 'blurb'",
                        ""),
                (a) -> assertCommand(a, "45",
                        "blurb"),
                (a) -> assertCommand(a, "/set mode mymode normal",
                        "|  Created new feedback mode: mymode"),
                (a) -> assertCommandOutputContains(a, "45",
                        " ==> 45")
        );
    }

    public void retainModeTest() {
        test(
                (a) -> assertCommandOutputStartsWith(a, "/retain mode",
                        "|  Missing the feedback mode"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode *",
                        "|  Expected a feedback mode name: *"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode amode normal",
                        "|  Unexpected arguments at end of command: normal"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode mymode",
                        "|  Does not match any current feedback mode: mymode"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode mymode -delete",
                        "|  No feedback mode named: mymode"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode -d mymode",
                        "|  No feedback mode named: mymode"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode normal",
                        "|  Not valid with a predefined mode: normal"),
                (a) -> assertCommand(a, "/set mode mymode verbose",
                        "|  Created new feedback mode: mymode"),
                (a) -> assertCommand(a, "/retain mode mymode",
                        ""),
                (a) -> assertCommand(a, "/set mode mymode -delete",
                        ""),
                (a) -> assertCommand(a, "/retain mode mymode -delete",
                        ""),
                (a) -> assertCommand(a, "/set mode kmode normal",
                        "|  Created new feedback mode: kmode"),
                (a) -> assertCommand(a, "/retain mode kmode",
                        ""),
                (a) -> assertCommand(a, "/set mode kmode -delete",
                        ""),
                (a) -> assertCommand(a, "/set mode tmode normal",
                        "|  Created new feedback mode: tmode"),
                (a) -> assertCommandOutputStartsWith(a, "/retain feedback tmode",
                        "|  '/retain feedback <mode>' requires that <mode> is predefined or has been retained with '/retain mode'"),
                (a) -> assertCommand(a, "/set format tmode display 'YES'",
                        ""),
                (a) -> assertCommand(a, "/set feedback tmode",
                        "|  Feedback mode: tmode"),
                (a) -> assertCommand(a, "45",
                        "YES"),
                (a) -> assertCommand(a, "/retain mode tmode",
                        ""),
                (a) -> assertCommand(a, "/retain feedback tmode",
                        "|  Feedback mode: tmode"),
                (a) -> assertCommand(a, "/set format tmode display 'blurb'",
                        ""),
                (a) -> assertCommand(a, "45",
                        "blurb")
        );
        test(
                (a) -> assertCommand(a, "45",
                        "YES"),
                (a) -> assertCommand(a, "/set feedback kmode",
                        "|  Feedback mode: kmode"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode kmode -delete",
                        "|  The current feedback mode 'kmode' cannot be deleted"),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode tmode -delete",
                        "|  The retained feedback mode 'tmode' cannot be deleted"),
                (a) -> assertCommand(a, "/retain feedback normal",
                        "|  Feedback mode: normal"),
                (a) -> assertCommand(a, "/retain mode tmode -delete",
                        ""),
                (a) -> assertCommandOutputStartsWith(a, "/retain mode kmode -delete",
                        "")
        );
        test(
                (a) -> assertCommandOutputStartsWith(a, "/set feedback tmode",
                        "|  Does not match any current feedback mode: tmode"),
                (a) -> assertCommandOutputStartsWith(a, "/set feedback kmode",
                        "|  Does not match any current feedback mode: kmode"),
                (a) -> assertCommandOutputStartsWith(a, "/set feedback mymode",
                        "|  Does not match any current feedback mode: mymode"),
                (a) -> assertCommandCheckOutput(a, "/set feedback",
                        (s) -> assertFalse(s.contains("mymode"), "Didn't delete mymode: " + s)),
                (a) -> assertCommandCheckOutput(a, "/set feedback",
                        (s) -> assertFalse(s.contains("kmode"), "Didn't delete kmode: " + s)),
                (a) -> assertCommandCheckOutput(a, "/set feedback",
                        (s) -> assertFalse(s.contains("tmode"), "Didn't delete tmode: " + s))
        );
    }

}
