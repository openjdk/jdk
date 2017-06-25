/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8182489
 * @summary test history with multiline snippets
 * @modules
 *     jdk.jshell/jdk.internal.jshell.tool.resources:open
 *     jdk.jshell/jdk.jshell:open
 * @build UITesting
 * @build ToolMultilineSnippetHistoryTest
 * @run testng ToolMultilineSnippetHistoryTest
 */

import org.testng.annotations.Test;

@Test
public class ToolMultilineSnippetHistoryTest extends UITesting {

    public void testUpArrow() throws Exception {
        doRunTest((inputSink, out) -> {
            inputSink.write("int x=\n44\n");
            waitOutput(out, "x ==> 44\n\u0005");
            inputSink.write("/!\n");
            waitOutput(out, "int x=\n44;\nx ==> 44\n\u0005");
            inputSink.write("\020");
            waitOutput(out,   "44;");
            inputSink.write("\020");
            waitOutput(out,  "int x=");
        });
    }

}
