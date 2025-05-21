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
 * @bug 8356165
 * @summary Check user input works properly
 * @modules
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 *     jdk.jshell/jdk.internal.jshell.tool:open
 *     jdk.jshell/jdk.internal.jshell.tool.resources:open
 *     jdk.jshell/jdk.jshell:open
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build Compiler UITesting
 * @compile InputUITest.java
 * @run testng/othervm -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8 -Dstdout.encoding=UTF-8 InputUITest
 */

import java.util.function.Function;
import org.testng.annotations.Test;

@Test
public class InputUITest extends UITesting {

    static final String LINE_SEPARATOR = System.getProperty("line.separator");
    static final String LINE_SEPARATOR_ESCAPED = LINE_SEPARATOR.replace("\n", "\\n")
                                                               .replace("\r", "\\r");

    public InputUITest() {
        super(true);
    }

    public void testUserInputWithSurrogates() throws Exception {
        Function<Integer, String> genSnippet =
                realCharsToRead -> "new String(System.in.readNBytes(" +
                                   (realCharsToRead + LINE_SEPARATOR.length()) +
                                   "))\n";
        doRunTest((inputSink, out) -> {
            inputSink.write(genSnippet.apply(4) + "\uD83D\uDE03\n");
            waitOutput(out, patternQuote("\"\uD83D\uDE03" + LINE_SEPARATOR_ESCAPED + "\""));
            inputSink.write(genSnippet.apply(1) + "\uD83D\n");
            waitOutput(out, patternQuote("\"?" + LINE_SEPARATOR_ESCAPED + "\""));
            inputSink.write(genSnippet.apply(1) + "\uDE03\n");
            waitOutput(out, patternQuote("\"?" + LINE_SEPARATOR_ESCAPED + "\""));
        }, false);
    }

}