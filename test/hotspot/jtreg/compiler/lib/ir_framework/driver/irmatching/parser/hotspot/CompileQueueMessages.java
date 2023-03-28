/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.parser.IREncodingParser;
import compiler.lib.ir_framework.driver.irmatching.parser.TestMethods;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class parses compile queue messages found in the hotspot_pid* files and keeps track of those that need to be
 * IR matched (i.e. identified by {@link IREncodingParser}.
 *
 * @see IREncodingParser
 */
class CompileQueueMessages {
    private static final Pattern COMPILE_ID_PATTERN = Pattern.compile("compile_id='(\\d+)'");

    private final Pattern compileIdTestMethodPattern;
    private final Pattern testMethodPattern;
    private final TestMethods testMethods;
    Map<Integer, String> compileIdToMethodName = new HashMap<>();

    public CompileQueueMessages(String testClassName, TestMethods testMethods) {
        this.compileIdTestMethodPattern = Pattern.compile("compile_id='(\\d+)'.*" + Pattern.quote(testClassName) + " (\\S+)");
        this.testMethodPattern = Pattern.compile(Pattern.quote(testClassName) + " (\\S+)");
        this.testMethods = testMethods;
    }

    public boolean isTestMethodQueuedLine(String line) {
        return isCompilation(line) && isTestMethod(line);
    }

    /**
     * Is this header a C2 non-OSR compilation header entry?
     */
    private boolean isCompilation(String line) {
        return line.startsWith("<task_queued") && notOSRCompilation(line) && notC2Compilation(line);
    }

    /**
     * OSR compilations have compile_kind set.
     */
    private boolean notOSRCompilation(String line) {
        return !line.contains("compile_kind='");
    }

    /**
     * Non-C2 compilations have level set.
     */
    private boolean notC2Compilation(String line) {
        return !line.contains("level='");
    }

    private boolean isTestMethod(String line) {
        Matcher matcher = testMethodPattern.matcher(line);
        if (matcher.find()) {
            return testMethods.isTestMethod(matcher.group(1));
        }
        return false;
    }

    public String parse(String line) {
        Matcher matcher = compileIdTestMethodPattern.matcher(line);
        TestFramework.check(matcher.find(), "must find match on line: \"" + line + "\"");
        int compileId = Integer.parseInt(matcher.group(1)); // parse from line
        String methodName = matcher.group(2); // parse from line
        compileIdToMethodName.put(compileId, methodName);
        return methodName;
    }

    /**
     * Return the method name associated with the compile id contained in this line if the method is IR matched.
     * Otherwise, return an empty string.
     */
    public String findTestMethodName(String line) {
        int compileId = parseCompileId(line);
        return compileIdToMethodName.getOrDefault(compileId, "");
    }

    private static int parseCompileId(String line) {
        Matcher matcher = COMPILE_ID_PATTERN.matcher(line);
        TestFramework.check(matcher.find(), "must find compile id on line: \"" + line + "\"");
        return Integer.parseInt(matcher.group(1));
    }
}
