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

package compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.shared.TestFormat;

/**
 * This class represents a "raw IR node" as read from a check attribute. It has a node part that either represents an
 * {@link IRNode} placeholder string or a user defined regex. In the former case, we could additionally have a user
 * postfix string. This raw IR node is not specific to any compile phases (i.e. the placeholder is not replaced by an
 * actual regex, yet, and thus is named "raw").
 *
 * @see IRNode
 */
public class RawIRNode {
    private final String node;
    private final CheckAttributeString userPostfix;
    private final int nodeIndex;

    public RawIRNode(CheckAttributeStringIterator it) {
        nodeIndex = it.nextIndex();
        node = it.next().value();
        if (IRNode.isCompositeIRNode(node)) {
            this.userPostfix = it.next();
            checkValidUserPostfix();
        } else {
            this.userPostfix = CheckAttributeString.getInvalid();
        }
    }

    private void checkValidUserPostfix() {
        String irNode = IRNode.getIRNodeAccessString(node);
        TestFormat.checkNoReport(userPostfix.isValid(), "Must provide additional value at index " +
                                                        nodeIndex + " right after " + irNode);
        TestFormat.checkNoReport(!userPostfix.value().isEmpty(), "Provided empty string for composite node " +
                                                                 irNode + " at index " + nodeIndex);
    }

    public String irNodePlaceholder() {
        return node;
    }

    public CompilePhase defaultCompilePhase() {
        return IRNode.getDefaultPhaseForIRNode(node);
    }

    public String regex(CompilePhase compilePhase) {
        String nodeRegex = node;
        if (IRNode.isIRNode(node)) {
            nodeRegex = IRNode.getRegexForPhaseOfIRNode(node, compilePhase);
            if (userPostfix.isValid()) {
                nodeRegex = nodeRegex.replaceAll(IRNode.IS_REPLACED, userPostfix.value());
            }
        }
        return nodeRegex;
    }
}
