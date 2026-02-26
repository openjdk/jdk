/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network.testvm.java;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.test.network.MessageTag;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static compiler.lib.ir_framework.test.network.MessageTag.*;

/**
 * Dedicated parser for {@link JavaMessages} received from the Test VM. Depending on the parsed {@link MessageTag}, the
 * message is parsed differently.
 */
public class JavaMessageParser {
    private static final Pattern TAG_PATTERN = Pattern.compile("^(\\[[^]]+])\\s*(.*)$");

    private final List<String> stdoutMessages;
    private final List<String> executedTests;
    private final Map<String, Long> methodTimes;
    private final StringBuilder vmInfoBuilder;
    private final StringBuilder applicableIrRules;

    private StringBuilder currentBuilder;

    public JavaMessageParser() {
        this.stdoutMessages = new ArrayList<>();
        this.methodTimes = new HashMap<>();
        this.executedTests = new ArrayList<>();
        this.vmInfoBuilder = new StringBuilder();
        this.applicableIrRules = new StringBuilder();
        this.currentBuilder = null;
    }

    public void parseLine(String line) {
        line = line.trim();
        Matcher tagLineMatcher = TAG_PATTERN.matcher(line);
        if (tagLineMatcher.matches()) {
            // New tag
            assertNoActiveParser();
            parseTagLine(tagLineMatcher);
            return;
        }

        assertActiveParser();
        if (line.equals(END_MARKER)) {
            // End tag
            parseEndTag();
            return;
        }

        // Multi-line message for single tag.
        currentBuilder.append(line).append(System.lineSeparator());
    }

    private void assertNoActiveParser() {
        TestFramework.check(currentBuilder == null, "Unexpected new tag while parsing block");
    }

    private void parseTagLine(Matcher tagLineMatcher) {
        String tag = tagLineMatcher.group(1);
        String message = tagLineMatcher.group(2);
        switch (tag) {
            case STDOUT -> stdoutMessages.add(message);
            case TEST_LIST -> executedTests.add(message);
            case PRINT_TIMES -> parsePrintTimes(message);
            case VM_INFO -> currentBuilder = vmInfoBuilder;
            case APPLICABLE_IR_RULES -> currentBuilder = applicableIrRules;
            default -> throw new TestFrameworkException("unknown tag");
        }
    }

    private void parsePrintTimes(String message) {
        String[] split = message.split(",");
        TestFramework.check(split.length == 2, "unexpected format");
        String methodName = split[0];
        try {
            long duration = Long.parseLong(split[1]);
            methodTimes.put(methodName, duration);
        } catch (NumberFormatException e) {
            throw new TestFrameworkException("invalid duration", e);
        }
    }

    private void assertActiveParser() {
        TestFramework.check(currentBuilder != null, "Received non-tag line outside of any tag block");
    }

    private void parseEndTag() {
        currentBuilder = null;
    }

    public JavaMessages output() {
        return new JavaMessages(new StdoutMessages(stdoutMessages),
                                new ExecutedTests(executedTests),
                                new MethodTimes(methodTimes),
                                applicableIrRules.toString(),
                                vmInfoBuilder.toString());
    }
}
