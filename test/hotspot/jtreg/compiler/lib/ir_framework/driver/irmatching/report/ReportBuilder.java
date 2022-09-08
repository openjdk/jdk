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

package compiler.lib.ir_framework.driver.irmatching.report;

import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.driver.irmatching.TestClassResult;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;

/**
 * Base class to build an IR matching failure report in text form that is eventually reported to the user by the
 * {@link IRMatcher}.
 */
abstract class ReportBuilder {
    protected final StringBuilder msg = new StringBuilder();
    private int methodNumber = 0;
    private final TestClassResult testClassResult;

    public ReportBuilder(TestClassResult testClassResult) {
        this.testClassResult = testClassResult;
    }

    protected int getMethodNumber() {
        return methodNumber;
    }

    abstract public String build();

    /**
     * Start visiting the IR matching results of the test class to build a report.
     */
    protected void visitResults(MatchResultVisitor visitor) {
        testClassResult.accept(visitor);
    }

    protected void appendIRMethodPrefix() {
        methodNumber++;
        if (methodNumber > 1) {
            msg.append(System.lineSeparator());
        }
        msg.append(methodNumber).append(") ");
    }

    /**
     * Return a string of {@code indentationSize} many whitespaces.
     */
    public static String getIndentation(int indentationSize) {
        return " ".repeat(indentationSize);
    }

    /**
     * Return the number of digits of {@code digit}.
     */
    protected static int digitCount(int digit) {
        return String.valueOf(digit).length();
    }
}
