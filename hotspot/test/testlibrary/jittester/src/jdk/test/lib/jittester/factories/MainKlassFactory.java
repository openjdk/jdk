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
import java.util.List;
import java.util.stream.Collectors;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.classes.MainKlass;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.types.TypeVoid;
import jdk.test.lib.jittester.utils.PseudoRandom;

class MainKlassFactory extends Factory {
    private final String name;
    private final long complexityLimit;
    private final int statementsInTestFunctionLimit;
    private final int statementsInFunctionLimit;
    private final int operatorLimit;
    private final int memberFunctionsLimit;
    private final int memberFunctionsArgLimit;
    private TypeKlass thisKlass;

    MainKlassFactory(String name, long complexityLimit, int memberFunctionsLimit,
            int memberFunctionsArgLimit, int statementsInFunctionLimit,
            int statementsInTestFunctionLimit, int operatorLimit) {
        this.name = name;
        this.complexityLimit = complexityLimit;
        this.memberFunctionsLimit = memberFunctionsLimit;
        this.memberFunctionsArgLimit = memberFunctionsArgLimit;
        this.statementsInFunctionLimit = statementsInFunctionLimit;
        this.statementsInTestFunctionLimit = statementsInTestFunctionLimit;
        this.operatorLimit = operatorLimit;
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        TypeKlass parent = (TypeKlass) TypeList.find("java.lang.Object");
        thisKlass = new TypeKlass(name);
        thisKlass.addParent(parent.getName());
        thisKlass.setParent(parent);
        parent.addChild(name);
        parent.addChild(thisKlass);
        SymbolTable.add(new VariableInfo("this", thisKlass, thisKlass,
                VariableInfo.FINAL | VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        IRNodeBuilder builder = new IRNodeBuilder()
                .setOwnerKlass(thisKlass)
                .setOperatorLimit(operatorLimit)
                .setMemberFunctionsLimit(memberFunctionsLimit)
                .setMemberFunctionsArgLimit(memberFunctionsArgLimit)
                .setStatementLimit(statementsInFunctionLimit)
                .setLevel(1)
                .setExceptionSafe(true)
                .setPrinterName("Printer");
        IRNode variableDeclarations = builder
                .setComplexityLimit((long) (complexityLimit * 0.05))
                .getVariableDeclarationBlockFactory().produce();
        IRNode functionDefinitions = null;
        if (!ProductionParams.disableFunctions.value()) {
            functionDefinitions = builder
                    .setComplexityLimit((long) (complexityLimit * 0.01 * PseudoRandom.random()))
                    .setFlags(FunctionInfo.NONRECURSIVE)
                    .getFunctionDefinitionBlockFactory()
                    .produce();
        }
        IRNode testFunction = builder.setResultType(new TypeVoid())
                .setComplexityLimit(complexityLimit)
                .setStatementLimit(statementsInTestFunctionLimit)
                .getBlockFactory()
                .produce();
        SymbolTable.remove(new Symbol("this", thisKlass, thisKlass, VariableInfo.NONE));
        IRNode printVariables = builder.setLevel(2)
                .getPrintVariablesFactory()
                .produce();
        List<IRNode> childs = new ArrayList<>();
        childs.add(variableDeclarations);
        childs.add(functionDefinitions);
        childs.add(testFunction);
        childs.add(printVariables);
        ensureMinDepth(childs, builder);
        ensureMaxDepth(childs);
        return new MainKlass(name, thisKlass, variableDeclarations,
                functionDefinitions, testFunction, printVariables);
    }

    private void ensureMaxDepth(List<IRNode> childs) {
        int maxDepth = ProductionParams.maxCfgDepth.value();
        List<IRNode> filtered = childs.stream()
            .filter(c -> c.isCFDeviation() && c.countDepth() > maxDepth)
            .collect(Collectors.toList());
        for (IRNode child : filtered) {
            List<IRNode> leaves = null;
            do {
                long depth = Math.max(child.countDepth(), maxDepth + 1);
                leaves = child.getDeviantBlocks(depth);
                leaves.get(0).removeSelf();
            } while (!leaves.isEmpty() && child.countDepth() > maxDepth);
        }
    }

    private void ensureMinDepth(List<IRNode> childs, IRNodeBuilder builder)
            throws ProductionFailedException {
        int minDepth = ProductionParams.minCfgDepth.value();
        List<IRNode> filtered = new ArrayList<>(childs);
        addMoreChildren(filtered, minDepth, builder);
    }

    private void addMoreChildren(List<IRNode> childs, int minDepth, IRNodeBuilder builder)
            throws ProductionFailedException {
        while (!childs.isEmpty() && IRNode.countDepth(childs) < minDepth) {
            PseudoRandom.shuffle(childs);
            IRNode randomChild = childs.get(0);
            List<IRNode> leaves = randomChild.getStackableLeaves();
            if (!leaves.isEmpty()) {
                PseudoRandom.shuffle(leaves);
                Block randomLeaf = (Block) leaves.get(0);
                TypeKlass klass = (TypeKlass) randomChild.getKlass();
                int newLevel = randomLeaf.getLevel() + 1;
                Type retType = randomLeaf.getReturnType();
                IRNode newBlock = builder.setOwnerKlass(klass)
                        .setResultType(retType)
                        .setComplexityLimit(complexityLimit)
                        .setStatementLimit(statementsInFunctionLimit)
                        .setLevel(newLevel)
                        .getBlockFactory()
                        .produce();
                List<IRNode> siblings = randomLeaf.getChildren();
                // to avoid break;
                int index = PseudoRandom.randomNotZero(siblings.size() - 1);
                siblings.add(index, newBlock);
            }
        }
    }
}
