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

package compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.action;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawIRNode;
import compiler.lib.ir_framework.shared.Comparison;
import compiler.lib.ir_framework.shared.ComparisonConstraintParser;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.util.ListIterator;

/**
 * This class represents a count string of a {@link IR} check attribute.
 */
class CountString {
    private final ListIterator<String> iterator;
    private final RawIRNode rawIRNode;

    public CountString(ListIterator<String> iterator, RawIRNode rawIRNode) {
        this.iterator = iterator;
        this.rawIRNode = rawIRNode;
    }

    public Comparison<Integer> parse() {
        TestFormat.checkNoReport(iterator.hasNext(), "Missing count for node " + rawIRNode.irNodePlaceholder());
        String countsString = iterator.next();
        try {
            return ComparisonConstraintParser.parse(countsString, CountString::parsePositiveInt);
        } catch (TestFormatException e) {
            String irNodeString = rawIRNode.irNodePlaceholder();
            throw new TestFormatException(e.getMessage() + ", node " + irNodeString + ", in count string \"" + countsString + "\"");
        }
    }

    public static int parsePositiveInt(String s) {
        int result = Integer.parseInt(s);
        if (result < 0) {
            throw new NumberFormatException("cannot be negative");
        }
        return result;
    }
}
