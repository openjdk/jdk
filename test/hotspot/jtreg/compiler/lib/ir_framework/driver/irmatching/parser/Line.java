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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class representing a normal line read from the hotspot_pid* file.
 */
class Line extends AbstractLine {
    private final Pattern compileIdPatternForTestClass;

    public Line(BufferedReader reader, Pattern compileIdPatternForTestClass) {
        super(reader);
        this.compileIdPatternForTestClass = compileIdPatternForTestClass;
    }

    /**
     * Is this line a start of a @Test annotated method? We only care about test class entries. There might be non-class
     * entries as well if user specified additional compile commands. Ignore these.
     */
    public boolean isTestClassCompilation() {
        if (isCompilation()) {
            Matcher matcher = compileIdPatternForTestClass.matcher(line);
            return matcher.find();
        }
        return false;
    }

    /**
     * Is this header a C2 non-OSR compilation header entry?
     */
    public boolean isCompilation() {
        return line.startsWith("<task_queued") && notOSRCompilation() && notC2Compilation();
    }

    /**
     * OSR compilations have compile_kind set.
     */
    private boolean notOSRCompilation() {
        return !line.contains("compile_kind='");
    }

    /**
     * Non-C2 compilations have level set.
     */
    private boolean notC2Compilation() {
        return !line.contains("level='");
    }

    /**
     * Is this line a start of a PrintIdeal or PrintOptoAssembly output block?
     */
    public boolean isBlockStart() {
        return isPrintIdealStart() || isPrintOptoAssemblyStart();
    }

    /**
     * Is this line a start of a PrintIdeal output block?
     */
    public boolean isPrintIdealStart() {
        // Ignore OSR compilations which have compile_kind set.
        return line.startsWith("<ideal") && notOSRCompilation();
    }

    /**
     * Is this line a start of a PrintOptoAssembly output block?
     */
    private boolean isPrintOptoAssemblyStart() {
        // Ignore OSR compilations which have compile_kind set.
        return line.startsWith("<opto_assembly") && notOSRCompilation();
    }
}

