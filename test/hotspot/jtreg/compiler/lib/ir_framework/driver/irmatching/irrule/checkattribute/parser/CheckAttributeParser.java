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
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class to parse a check attribute ({@link IR#failOn()} or {@link IR#counts()}) as found in a {@link IR @IR}
 * annotation.
 *
 * @see IR#failOn()
 * @see IR#counts()
 */
abstract public class CheckAttributeParser {
    private final List<RawConstraint> rawConstraints = new ArrayList<>();
    protected final CheckAttributeIterator checkAttributeIterator;

    public CheckAttributeParser(String[] checkAttribute) {
        this.checkAttributeIterator = new CheckAttributeIterator(checkAttribute);
    }

    public List<RawConstraint> parse() {
        while (checkAttributeIterator.hasConstraintsLeft()) {
            rawConstraints.add(parseNextConstraint());
        }
        return rawConstraints;
    }

    abstract protected RawConstraint parseNextConstraint();

    protected String getUserProvidedPostfix() {
        if (IRNode.isCompositeIRNode(checkAttributeIterator.getCurrentElement())) {
            return parseUserProvidedPostfix();
        } else {
            return null;
        }
    }

    private String parseUserProvidedPostfix() {
        if (!checkAttributeIterator.hasConstraintsLeft()) {
            reportMissingCompositeValue();
        }
        return checkAttributeIterator.nextElement();
    }

    private void reportMissingCompositeValue() {
        String nodeName = IRNode.getCompositeNodeName(checkAttributeIterator.getCurrentElement());
        throw new TestFormatException("Must provide additional value at index " + checkAttributeIterator.getCurrentIndex()
                                      + " right after IRNode." + nodeName);
    }
}

