/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser.hotspot;

import compiler.lib.ir_framework.CompilePhase;

/**
 * This class represents a single compile phase block of a {@link LoggedMethod}.
 */
class CompilePhaseBlock {

    /**
     * Dummy object for a block that we do not need to parse.
     */
    public static final CompilePhaseBlock DONT_CARE = new CompilePhaseBlock(CompilePhase.DEFAULT);

    private final CompilePhase compilePhase;
    private final StringBuilder builder;

    public CompilePhaseBlock(CompilePhase compilePhase) {
        this.compilePhase = compilePhase;
        String blockHeader = "> Phase \"" + compilePhase.getName() + "\":" + System.lineSeparator();
        this.builder = new StringBuilder(blockHeader);
    }

    public CompilePhase compilePhase() {
        return compilePhase;
    }

    /**
     * Is this line a start of an ideal or opto assembly output block?
     */
    public static boolean isBlockStartLine(String line) {
        return (isPrintIdealStart(line) || isPrintOptoAssemblyStart(line)) && notOSRCompilation(line);
    }

    /**
     * Is this line a start of an ideal output block?
     */
    public static boolean isPrintIdealStart(String line) {
        // Ignore OSR compilations which have compile_kind set.
        return line.startsWith("<ideal");
    }

    /**
     * Is this line a start of an opto assembly output block?
     */
    public static boolean isPrintOptoAssemblyStart(String line) {
        // Ignore OSR compilations which have compile_kind set.
        return line.startsWith("<opto_assembly");
    }

    /**
     * OSR compilations have compile_kind set.
     */
    private static boolean notOSRCompilation(String content) {
        return !content.contains("compile_kind='");
    }

    /**
     * Is this line an end of an ideal or opto assembly output block?
     */
    public static boolean isBlockEndLine(String line) {
        return line.startsWith("</ideal") || line.startsWith("</opto_assembly");
    }

    public void addLine(String line) {
        builder.append(escapeXML(line)).append(System.lineSeparator());

    }

    public String content() {
        return builder.toString();
    }

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
