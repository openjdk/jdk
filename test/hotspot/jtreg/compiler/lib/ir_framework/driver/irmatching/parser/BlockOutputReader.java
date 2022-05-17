/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Class to read all lines of a PrintIdeal or PrintOptoAssembly block.
 */
class BlockOutputReader {
    private final BlockLine line;

    public BlockOutputReader(BufferedReader reader, Pattern compileIdPatternForTestClass) {
        this.line = new BlockLine(reader, compileIdPatternForTestClass);
    }

    /**
     * Read all lines belonging to a PrintIdeal or PrintOptoAssembly output block.
     */
    public Block readBlock() throws IOException {
        StringBuilder builder = new StringBuilder();
        List<String> testClassCompilations = new ArrayList<>();
        while (line.readLine() && !line.isBlockEnd()) {
            if (line.isTestClassCompilation()) {
                // Could have safepointed while writing the block (see IRMatcher.SAFEPOINT_WHILE_PRINTING_MESSAGE)
                // and enqueuing the next test class method for compilation during the interruption. Record this
                // method to ensure that we read the PrintIdeal/PrintOptoAssembly blocks for that method later.
                testClassCompilations.add(line.getLine());
            }
            builder.append(escapeXML(line.getLine())).append(System.lineSeparator());
        }
        return new Block(builder.toString(), testClassCompilations);
    }

    /**
     * Need to escape XML special characters.
     */
    private static String escapeXML(String line) {
        if (line.contains("&")) {
            line = line.replace("&lt;", "<");
            line = line.replace("&gt;", ">");
            line = line.replace("&quot;", "\"");
            line = line.replace("&apos;", "'");
            line = line.replace("&amp;", "&");
        }
        return line;
    }
}
