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

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.action.ConstraintAction;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.*;

/**
 * This class reads the check attribute strings as found in ({@link IR#failOn()} or {@link IR#counts()}) and groups them
 * into constraints. For each constraint, a {@link ConstraintAction} is performed which creates an object. These objects
 * are then returned to the caller.
 *
 * @see IR#failOn()
 * @see IR#counts()
 * @see ConstraintAction
 */
public class CheckAttributeReader<R> {
    private final ListIterator<String> iterator;
    private final ConstraintAction<R> constraintAction;

    public CheckAttributeReader(String[] checkAttributeStrings, ConstraintAction<R> constraintAction) {
        this.iterator = Arrays.stream(checkAttributeStrings).toList().listIterator();
        this.constraintAction = constraintAction;
    }

    public void read(Collection<R> result) {
        int index = 1;
        while (iterator.hasNext()) {
            String node = iterator.next();
            CheckAttributeString userPostfix = readUserPostfix(node);
            RawIRNode rawIRNode = new RawIRNode(node, userPostfix);
            result.add(constraintAction.apply(iterator, rawIRNode, index++));
        }
    }

    public final CheckAttributeString readUserPostfix(String node) {
        if (IRNode.isCompositeIRNode(node)) {
            return readUserPostfixForCompositeIRNode(node);
        } else if (IRNode.isVectorIRNode(node)) {
            return readUserPostfixForVectorIRNode(node);
        } else {
            return CheckAttributeString.invalid();
        }
    }

    private final CheckAttributeString readUserPostfixForCompositeIRNode(String node) {
        String irNode = IRNode.getIRNodeAccessString(node);
        int nextIndex = iterator.nextIndex();
        TestFormat.checkNoReport(iterator.hasNext(), "Must provide additional value at index " +
                                                     nextIndex + " right after " + irNode);
        CheckAttributeString userPostfix = new CheckAttributeString(iterator.next());
        TestFormat.checkNoReport(userPostfix.isValidUserPostfix(), "Provided empty string for composite node " +
                                                                   irNode + " at index " + nextIndex);
        return userPostfix;
    }

    private final CheckAttributeString readUserPostfixForVectorIRNode(String node) {
        if (iterator.hasNext()) {
            String maybeVectorType = iterator.next();
            if (IRNode.isVectorSize(maybeVectorType)) {
                return new CheckAttributeString(maybeVectorType);
            }
            // If we do not find that pattern, then revert the iterator once
            iterator.previous();
        }
        return CheckAttributeString.invalid();
    }
}
