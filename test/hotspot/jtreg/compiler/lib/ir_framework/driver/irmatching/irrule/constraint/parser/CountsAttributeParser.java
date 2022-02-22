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

package compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * This class parses a {@link IR#counts()} attribute. It returns a list of {@link RawCountsConstraint} which does not have
 * any default regexes defined in {@link IRNode} replaced, yet. This is done later in the {@link RawCountsConstraintParser}.
 */
public class CountsAttributeParser extends CheckAttributeParser<RawCountsConstraint> {

    private CountsAttributeParser() {}

    public static List<RawCountsConstraint> parse(String[] countsAttribute) {
        List<RawCountsConstraint> rawCountsConstraintResultList = new ArrayList<>();
        new CountsAttributeParser().parseCheckAttribute(rawCountsConstraintResultList, countsAttribute);
        return rawCountsConstraintResultList;
    }

    @Override
    protected void parseNextConstraint(List<RawCountsConstraint> rawConstraintResultList,
                                       CheckAttributeIterator checkAttributeIterator) {
        String rawNodeString = checkAttributeIterator.getNextElement();
        String userProvidedPostfix = getUserProvidedPostfix(checkAttributeIterator);
        String countString = getCountString(rawNodeString, checkAttributeIterator);
        rawConstraintResultList.add(new RawCountsConstraint(rawNodeString, userProvidedPostfix,
                                                             countString, checkAttributeIterator.getCurrentConstraintIndex()));
    }

    private static String getCountString(String rawNodeString, CheckAttributeIterator checkAttributeIterator) {
        TestFormat.checkNoReport(checkAttributeIterator.hasConstraintsLeft(), "Missing count for node " + rawNodeString);
        return checkAttributeIterator.nextElement();
    }
}
