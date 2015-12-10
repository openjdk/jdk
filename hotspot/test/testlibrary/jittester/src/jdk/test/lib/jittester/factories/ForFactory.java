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

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Initialization;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.types.TypeInt;
import jdk.test.lib.jittester.utils.PseudoRandom;

class ForFactory extends SafeFactory {
    private final Loop loop;
    private final long complexityLimit;
    private final int statementLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type returnType;
    private final int level;
    private long thisLoopIterLimit = 0;
    private final boolean canHaveReturn;

    ForFactory(TypeKlass ownerClass, Type returnType, long complexityLimit, int statementLimit,
            int operatorLimit, int level, boolean canHaveReturn) {
        this.ownerClass = ownerClass;
        this.returnType = returnType;
        this.complexityLimit = complexityLimit;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        loop = new Loop();
        this.canHaveReturn = canHaveReturn;
    }

    @Override
    protected IRNode sproduce() throws ProductionFailedException {
        if (statementLimit <= 0 || complexityLimit <= 0) {
            throw new ProductionFailedException();
        }
        IRNodeBuilder builder = new IRNodeBuilder()
                .setOwnerKlass(ownerClass)
                .setResultType(returnType)
                .setOperatorLimit(operatorLimit)
                .setSemicolon(false)
                .setExceptionSafe(false)
                .setNoConsts(false);
        long complexity = complexityLimit;
        // Loop header parameters
        long headerComplLimit = (long) (0.005 * complexity * PseudoRandom.random());
        complexity -= headerComplLimit;
        int headerStatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        long statement1ComplLimit = (long) (0.005 * complexity * PseudoRandom.random());
        complexity -= statement1ComplLimit;
        // Loop body parameters
        thisLoopIterLimit = (long) (0.0001 * complexity * PseudoRandom.random());
        if (thisLoopIterLimit > Integer.MAX_VALUE || thisLoopIterLimit == 0) {
            throw new ProductionFailedException();
        }
        complexity = thisLoopIterLimit > 0 ? complexity / thisLoopIterLimit : 0;
        long condComplLimit = (long) (complexity * PseudoRandom.random());
        complexity -= condComplLimit;
        long statement2ComplLimit = (long) (complexity * PseudoRandom.random());
        complexity -= statement2ComplLimit;
        long body1ComplLimit = (long) (complexity * PseudoRandom.random());
        complexity -= body1ComplLimit;
        int body1StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        long body2ComplLimit = (long) (complexity * PseudoRandom.random());
        complexity -= body2ComplLimit;
        int body2StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        long body3ComplLimit = complexity;
        int body3StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        // Production
        loop.initialization = builder.getCounterInitializerFactory(0).produce();
        IRNode header;
        try {
            header = builder.setComplexityLimit(headerComplLimit)
                    .setStatementLimit(headerStatementLimit)
                    .setLevel(level - 1)
                    .setSubBlock(true)
                    .setCanHaveBreaks(false)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(false)
                    .getBlockFactory()
                    .produce();
        } catch (ProductionFailedException e) {
            header = new Nothing();
        }
        SymbolTable.push();
        IRNode statement1;
        try {
            Rule rule = new Rule("statement1");
            builder.setComplexityLimit(statement1ComplLimit);
            rule.add("assignment", builder.getAssignmentOperatorFactory());
            rule.add("function", builder.getFunctionFactory(), 0.1);
            rule.add("initialization", builder.setIsConstant(false)
                    .setIsStatic(false)
                    .setIsLocal(true)
                    .getVariableInitializationFactory());
            statement1 = rule.produce();
        } catch (ProductionFailedException e) {
            statement1 = new Nothing();
        }
        LocalVariable counter = new LocalVariable(((Initialization) loop.initialization).get());
        Literal limiter = new Literal(Integer.valueOf((int) thisLoopIterLimit), new TypeInt());
        loop.condition = builder.setComplexityLimit(condComplLimit)
                .setLocalVariable(counter)
                .getLoopingConditionFactory(limiter)
                .produce();
        IRNode statement2;
        try {
            statement2 =  builder.setComplexityLimit(statement2ComplLimit)
                    .getAssignmentOperatorFactory().produce();
        } catch (ProductionFailedException e) {
            statement2 = new Nothing();
        }
        IRNode body1;
        try {
            body1 = builder.setComplexityLimit(body1ComplLimit)
                    .setStatementLimit(body1StatementLimit)
                    .setLevel(level)
                    .setSubBlock(true)
                    .setCanHaveBreaks(true)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(false)
                    .getBlockFactory()
                    .produce();
        } catch (ProductionFailedException e) {
            body1 = new Nothing();
        }
        loop.manipulator = builder.setLocalVariable(counter).getCounterManipulatorFactory().produce();
        IRNode body2;
        try {
            body2 = builder.setComplexityLimit(body2ComplLimit)
                    .setStatementLimit(body2StatementLimit)
                    .setLevel(level)
                    .setSubBlock(true)
                    .setCanHaveBreaks(true)
                    .setCanHaveContinues(true)
                    .setCanHaveReturn(false)
                    .getBlockFactory()
                    .produce();
        } catch (ProductionFailedException e) {
            body2 = new Nothing();
        }
        IRNode body3;
        try {
            body3 = builder.setComplexityLimit(body3ComplLimit)
                    .setStatementLimit(body3StatementLimit)
                    .setLevel(level)
                    .setSubBlock(true)
                    .setCanHaveBreaks(true)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(canHaveReturn)
                    .getBlockFactory()
                    .produce();
        } catch (ProductionFailedException e) {
            body3 = new Nothing();
        }
        SymbolTable.pop();
        return new For(level, loop, thisLoopIterLimit, header, statement1, statement2, body1,
                body2, body3);
    }
}
