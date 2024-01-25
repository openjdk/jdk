/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.shared.Comparison;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;
import compiler.lib.ir_framework.driver.SuccessOnlyConstraintException;

/**
 * This class represents a "raw IR node" as read from a check attribute. It has a node part that either represents an
 * {@link IRNode} placeholder string or a user defined regex. In the former case, we could additionally have a user
 * postfix string. This raw IR node is not specific to any compile phase (i.e. the IR node placeholder is not replaced
 * by an actual regex, yet, and thus is named "raw").
 *
 * @see IRNode
 */
public class RawIRNode {
    private final String node;
    private final CheckAttributeString userPostfix;

    public RawIRNode(String node, CheckAttributeString userPostfix) {
        this.node = node;
        this.userPostfix = userPostfix;
    }

    public String irNodePlaceholder() {
        return IRNode.getIRNodeAccessString(node);
    }

    public CompilePhase defaultCompilePhase() {
        return IRNode.getDefaultPhase(node);
    }

    public String regex(CompilePhase compilePhase, VMInfo vmInfo, Comparison.Bound bound) {
        String nodeRegex = node;
        if (IRNode.isIRNode(node)) {
            nodeRegex = IRNode.getRegexForCompilePhase(node, compilePhase);
            if (IRNode.isVectorIRNode(node)) {
                nodeRegex = regexForVectorIRNode(nodeRegex, vmInfo, bound);
            } else if (userPostfix.isValid()) {
                nodeRegex = nodeRegex.replaceAll(IRNode.IS_REPLACED, userPostfix.value());
            }
        }
        return nodeRegex;
    }

    private String regexForVectorIRNode(String nodeRegex, VMInfo vmInfo, Comparison.Bound bound) {
        String type = IRNode.getVectorNodeType(node);
        TestFormat.checkNoReport(IRNode.getTypeSizeInBytes(type) > 0,
                                 "Vector node's type must have valid type, got \"" + type + "\" for \"" + node + "\"");
        String size = null;
        if (userPostfix.isValid()) {
            // Size is explicitly stated
            String value = userPostfix.value();
            TestFormat.checkNoReport(value.startsWith(IRNode.VECTOR_SIZE),
                                     "Vector node's vector size must start with IRNode.VECTOR_SIZE, got: \"" + value + "\"");
            size = value.substring(2);

            if (!vmInfo.canTrustVectorSize()) {
                // Parse it already just to get errors before we overwrite size
                IRNode.parseVectorNodeSize(size, type, vmInfo);
            }
        }

        // Set default values in some cases:
        if (!userPostfix.isValid() || !vmInfo.canTrustVectorSize()) {
            switch (bound) {
                case LOWER -> {
                    // For lower bound we check for the maximal size by default. But if we cannot trust the
                    // vector size we at least check there are vectors of any size.
                    if (vmInfo.canTrustVectorSize()) {
                        // No size specified, so assume maximal size
                        size = IRNode.VECTOR_SIZE_TAG_MAX;
                    } else {
                        System.out.println("WARNING: you are on a system with \"canTrustVectorSize == false\" (default Cascade Lake).");
                        System.out.println("         The lower bound rule for \"" + node + "\" is now performed with");
                        System.out.println("         \"IRNode.VECTOR_SIZE_TAG_ANY\" instead of \"IRNode.VECTOR_SIZE_TAG_MAX\".");
                        size = IRNode.VECTOR_SIZE_TAG_ANY;
                    }
                }
                case UPPER -> {
                    if (userPostfix.isValid()) {
                        TestFormat.checkNoReport(!vmInfo.canTrustVectorSize(), "sanity");
                        // If we have a size specified but cannot trust the size, and must check an upper
                        // bound, this can be impossible to count correctly - if we have an incorrect size
                        // we may count either too many nodes. We just create an impossible regex which will
                        // always have count zero and make the upper bound rule pass.
                        System.out.println("WARNING: you are on a system with \"canTrustVectorSize == false\" (default Cascade Lake).");
                        System.out.println("         The upper bound rule for \"" + node + "\" cannot be checked.");
                        throw new SuccessOnlyConstraintException("upper bound with specified size");
                    } else {
                        // For upper bound we check for vectors of any size by default.
                        size = IRNode.VECTOR_SIZE_TAG_ANY;
                    }
                }
                case EQUAL -> {
                    if (vmInfo.canTrustVectorSize()) {
                        // No size specified, so assume maximal size
                        size = IRNode.VECTOR_SIZE_TAG_MAX;
                    } else {
                        // Equal comparison to a strictly positive number would lead us to an impossible
                        // situation: we might have to know the exact vector size or else we count too many
                        // or too few cases. We therefore skip such a constraint and treat it as success.
                        System.out.println("WARNING: you are on a system with \"canTrustVectorSize == false\" (default Cascade Lake).");
                        System.out.println("         The equal count comparison rule for \"" + node + "\" cannot be checked.");
                        throw new SuccessOnlyConstraintException("equal count comparison");
                    }
                }
            }
        }
        String sizeRegex = IRNode.parseVectorNodeSize(size, type, vmInfo);
        return nodeRegex.replaceAll(IRNode.IS_REPLACED,
                                    "vector[A-Za-z]\\\\[" + sizeRegex + "\\\\]:\\\\{" + type + "\\\\}");
    }
}
