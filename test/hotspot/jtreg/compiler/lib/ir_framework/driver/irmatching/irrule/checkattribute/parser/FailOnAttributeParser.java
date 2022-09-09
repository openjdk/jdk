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

package compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parser;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawFailOnConstraint;

/**
 * This class parses a the {@link IR#failOn()}) check attribute as found in a {@link IR @IR} annotation. It returns a
 * list of {@link RawConstraint} containing {@link RawFailOnConstraint} objects.
 *
 * @see IR#counts()
 * @see RawFailOnConstraint
 */
public class FailOnAttributeParser extends CheckAttributeParser {

    public FailOnAttributeParser(String[] failOnAttribute) {
        super(failOnAttribute);
    }

    @Override
    protected RawConstraint parseNextConstraint() {
        String rawNodeString = checkAttributeIterator.getNextElement();
        String userProvidedPostfix = getUserProvidedPostfix();
        return new RawFailOnConstraint(rawNodeString, userProvidedPostfix,
                                       checkAttributeIterator.getCurrentConstraintIndex());
    }
}
