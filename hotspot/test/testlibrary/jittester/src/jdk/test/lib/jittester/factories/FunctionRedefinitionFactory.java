/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester.factories;

import java.util.ArrayList;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.ArgumentDeclaration;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.types.TypeVoid;
import jdk.test.lib.jittester.utils.PseudoRandom;

class FunctionRedefinitionFactory extends Factory {
    private final long complexityLimit;
    private final int statementLimit;
    private final int operatorLimit;
    private final int level;
    private final TypeKlass ownerClass;
    private final FunctionInfo functionInfo;

    FunctionRedefinitionFactory(FunctionInfo functionInfo, TypeKlass ownerClass,
            long complexityLimit, int statementLimit, int operatorLimit, int level, int flags) {
        this.ownerClass = ownerClass;
        this.functionInfo = new FunctionInfo(functionInfo); // do deep coping
        functionInfo.klass = ownerClass; // important! fix klass!
        if ((functionInfo.flags & FunctionInfo.STATIC) == 0) {
            functionInfo.argTypes.get(0).type = ownerClass; // redefine type of this
        }
        functionInfo.flags = flags; // apply new flags.
        // fix the type of class where the args would be declared
        for (VariableInfo varInfo : functionInfo.argTypes) {
            varInfo.klass = ownerClass;
        }
        this.complexityLimit = complexityLimit;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        ArrayList<VariableInfo> argumentsInfo = functionInfo.argTypes;
        SymbolTable.push();
        IRNode body;
        IRNode returnNode;
        ArrayList<ArgumentDeclaration> argumentsDeclaration;
        try {
            if ((functionInfo.flags & FunctionInfo.STATIC) > 0) {
                argumentsDeclaration = new ArrayList<>(argumentsInfo.size());
                for (VariableInfo varInfo : argumentsInfo) {
                    argumentsDeclaration.add(new ArgumentDeclaration(varInfo));
                    SymbolTable.add(varInfo);
                }
            } else {
                argumentsDeclaration = new ArrayList<>(argumentsInfo.size() - 1);
                SymbolTable.add(argumentsInfo.get(0));
                for (int i = 1; i < argumentsInfo.size(); i++) {
                    argumentsDeclaration.add(new ArgumentDeclaration(argumentsInfo.get(i)));
                    SymbolTable.add(argumentsInfo.get(i));
                }
            }
            long blockComplLimit = (long) (PseudoRandom.random() * complexityLimit);
            IRNodeBuilder builder = new IRNodeBuilder()
                    .setOwnerKlass(ownerClass)
                    .setResultType(functionInfo.type)
                    .setStatementLimit(statementLimit)
                    .setOperatorLimit(operatorLimit);
            body = builder.setComplexityLimit(blockComplLimit)
                    .setLevel(level)
                    .setSubBlock(true)
                    .setCanHaveBreaks(false)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(true)
                    .getBlockFactory()
                    .produce();
            if (!functionInfo.type.equals(new TypeVoid())) {
                returnNode = builder.setComplexityLimit(complexityLimit - blockComplLimit)
                        .setExceptionSafe(false)
                        .getReturnFactory()
                        .produce();
            } else {
                returnNode = null;
            }
        } catch (ProductionFailedException e) {
            SymbolTable.pop();
            SymbolTable.add(functionInfo);
            throw e;
        }
        SymbolTable.pop();
        if ((functionInfo.flags & FunctionInfo.STATIC) == 0) {
            functionInfo.flags &= ~FunctionInfo.ABSTRACT;
        }
        // If it's all ok, add the function to the symbol table.
        SymbolTable.add(functionInfo);
        return new FunctionDefinition(functionInfo, argumentsDeclaration, body, returnNode);
    }

}
