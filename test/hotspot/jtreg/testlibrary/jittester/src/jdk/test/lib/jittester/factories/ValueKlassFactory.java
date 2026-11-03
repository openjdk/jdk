/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.classes.ValueKlass;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class ValueKlassFactory extends AbstractKlassFactory<ValueKlass> {

    ValueKlassFactory(String name, long complexityLimit,
            int memberFunctionsLimit, int memberFunctionsArgLimit, int statementsInFunctionLimit,
            int operatorLimit, int level) {
        super(name, complexityLimit, memberFunctionsLimit, memberFunctionsArgLimit,
                statementsInFunctionLimit, operatorLimit, level);
        abstractProbabilityAdjustment = 1.0; // We want more abstract value classes
    }

    @Override
    protected TypeKlass createThisKlass(String name) {
        TypeKlass thisKlass = new TypeKlass(name);
        thisKlass.setValueKlass();
        return thisKlass;
    }

    @Override
    protected boolean canInheritFrom(Type type) {
        if (!(type instanceof TypeKlass)) {
            return false;
        }
        TypeKlass klass = (TypeKlass) type;
        return klass.isAbstract() && klass.isValueKlass();
    }

    @Override
    protected IRNode produceVariableDeclarations(IRNodeBuilder builder, long complexity)
            throws ProductionFailedException {
        return builder.setComplexityLimit(complexity).getConstantVariableDeclarationBlockFactory().produce();
    }

    @Override
    protected IRNode produceFunctionDefinitions(IRNodeBuilder builder, long complexity, int memberLimit)
            throws ProductionFailedException {
        return builder.setComplexityLimit(complexity)
                .setMemberFunctionsLimit(memberLimit)
                .setFlags(FunctionInfo.NONE)
                .setIsSynchronizedAllowed(false)
                .getFunctionDefinitionBlockFactory()
                .produce();
    }

    @Override
    protected ArrayList<Symbol> getShuffledOverrideCandidates(HashSet<Symbol> nonAbstractSet) {
        nonAbstractSet.removeIf(symbol -> (((FunctionInfo) symbol).flags & FunctionInfo.FINAL) > 0);
        return super.getShuffledOverrideCandidates(nonAbstractSet);
    }

    @Override
    protected void finalizeClassFlags(TypeKlass thisKlass) {
        if (!thisKlass.isAbstract()) {
            thisKlass.setFinal();
        }
    }

    @Override
    protected ValueKlass createKlassNode(TypeKlass thisKlass, TypeKlass parent,
            ArrayList<TypeKlass> interfaces, String name, int level,
            IRNode variableDeclarations, IRNode constructorDefinitions,
            IRNode functionDefinitions, IRNode abstractFunctionRedefinitions,
            IRNode overridenFunctionRedefinitions, IRNode functionDeclarations,
            IRNode printVariables) {
        return new ValueKlass(thisKlass, parent, interfaces, name, level,
                variableDeclarations, constructorDefinitions, functionDefinitions,
                abstractFunctionRedefinitions, overridenFunctionRedefinitions,
                functionDeclarations, printVariables);
    }
}
