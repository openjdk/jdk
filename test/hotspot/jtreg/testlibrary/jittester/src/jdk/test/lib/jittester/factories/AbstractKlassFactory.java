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
import java.util.Iterator;
import java.util.LinkedList;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.functions.FunctionDeclarationBlock;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

abstract class AbstractKlassFactory<T extends Klass> extends Factory<T> {
    private final String name;
    private final long complexityLimit;
    private final int statementsInFunctionLimit;
    private final int operatorLimit;
    private final int memberFunctionsArgLimit;
    private final int level;
    private final ArrayList<TypeKlass> interfaces;
    private TypeKlass thisKlass;
    private TypeKlass parent;
    private int memberFunctionsLimit;
    protected double abstractProbabilityAdjustment;

    AbstractKlassFactory(String name, long complexityLimit,
            int memberFunctionsLimit, int memberFunctionsArgLimit, int statementsInFunctionLimit,
            int operatorLimit, int level) {
        this.name = name;
        this.complexityLimit = complexityLimit;
        this.memberFunctionsLimit = memberFunctionsLimit;
        this.memberFunctionsArgLimit = memberFunctionsArgLimit;
        this.statementsInFunctionLimit = statementsInFunctionLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        interfaces = new ArrayList<>();
        abstractProbabilityAdjustment = 0.2; // Probability to consider making class abstract
    }

    @Override
    public final T produce() throws ProductionFailedException {
        HashSet<Symbol> abstractSet = new HashSet<>();
        HashSet<Symbol> overrideSet = new HashSet<>();
        thisKlass = createThisKlass(name);

        // Do we want to inherit something?
        if (!ProductionParams.disableInheritance.value()) {
            inheritClass();
            inheritInterfaces();
            // Now, we should carefully construct a set of all methods with are still abstract.
            // In order to do that, we will make two sets of methods: abstract and non-abstract.
            // Then by substracting non-abstract from abstract we'll get what we want.
            HashSet<Symbol> nonAbstractSet = new HashSet<>();
            for (Symbol symbol : SymbolTable.getAllCombined(thisKlass, FunctionInfo.class)) {
                FunctionInfo functionInfo = (FunctionInfo) symbol;
                // There could be multiple definitions or declarations encountered,
                // but all we interested in are signatures.
                if ((functionInfo.flags & FunctionInfo.ABSTRACT) > 0) {
                    abstractSet.add(functionInfo);
                } else {
                    nonAbstractSet.add(functionInfo);
                }
            }
            abstractSet.removeAll(nonAbstractSet);

            if (PseudoRandom.randomBoolean(abstractProbabilityAdjustment)
               && (abstractSet.removeIf((_unused) -> PseudoRandom.randomBoolean(0.2)))) {
                    thisKlass.setAbstract();
            }

            if (PseudoRandom.randomBoolean(0.2)) {
                int redefineLimit = (int) (memberFunctionsLimit * PseudoRandom.random());
                if (redefineLimit > 0) {
                    // We may also select some functions from the hierarchy that we want
                    // to redefine..
                    int i = 0;
                    for (Symbol symbol : getShuffledOverrideCandidates(nonAbstractSet)) {
                        if (++i > redefineLimit) {
                            break;
                        }
                        FunctionInfo functionInfo = (FunctionInfo) symbol;
                        if ((functionInfo.flags & FunctionInfo.FINAL) > 0) {
                            continue;
                        }
                        overrideSet.add(functionInfo);
                    }
                }
            }
            memberFunctionsLimit -= abstractSet.size() + overrideSet.size();
            // Ok, remove the symbols from the table which are going to be overrided.
            // Because the redefiner would probably modify them and put them back into table.
            for (Symbol symbol : abstractSet) {
                SymbolTable.remove(symbol);
            }
            for (Symbol symbol : overrideSet) {
                SymbolTable.remove(symbol);
            }
        } else {
            parent = TypeList.OBJECT;
            thisKlass.addParent(parent.getName());
            thisKlass.setParent(parent);
            parent.addChild(name);
        }
        VariableInfo thizzVariable = new VariableInfo("this", thisKlass, thisKlass,
                VariableInfo.FINAL | VariableInfo.LOCAL | VariableInfo.INITIALIZED);
        SymbolTable.add(thizzVariable);

        IRNode variableDeclarations = null;
        IRNode constructorDefinitions = null;
        IRNode functionDefinitions = null;
        IRNode functionDeclarations = null;
        IRNode abstractFunctionsRedefinitions = null;
        IRNode overridenFunctionsRedefinitions = null;
        IRNodeBuilder builder = new IRNodeBuilder().setOwnerKlass(thisKlass)
                .setExceptionSafe(true);
        try {
            builder.setLevel(level + 1)
                    .setOperatorLimit(operatorLimit)
                    .setStatementLimit(statementsInFunctionLimit)
                    .setMemberFunctionsArgLimit(memberFunctionsArgLimit);
            variableDeclarations = produceVariableDeclarations(builder,
                    (long) (complexityLimit * 0.001 * PseudoRandom.random()));
            if (!ProductionParams.disableFunctions.value()) {
                // Try to implement all methods.
                abstractFunctionsRedefinitions = builder.setComplexityLimit((long) (complexityLimit * 0.3 * PseudoRandom.random()))
                        .setLevel(level + 1)
                        .getFunctionRedefinitionBlockFactory(abstractSet)
                        .produce();
                overridenFunctionsRedefinitions = builder.setComplexityLimit((long) (complexityLimit * 0.3 * PseudoRandom.random()))
                        .getFunctionRedefinitionBlockFactory(overrideSet)
                        .produce();
                if (PseudoRandom.randomBoolean(0.2)) { // wanna be abstract ?
                    functionDeclarations = builder.setMemberFunctionsLimit((int) (memberFunctionsLimit * 0.2
                                    * PseudoRandom.random()))
                            .getFunctionDeclarationBlockFactory()
                            .produce();
                    if (((FunctionDeclarationBlock) functionDeclarations).size() > 0) {
                        thisKlass.setAbstract();
                    }
                }
                functionDefinitions = produceFunctionDefinitions(builder,
                        (long) (complexityLimit * 0.5 * PseudoRandom.random()),
                        (int) (memberFunctionsLimit * 0.6 * PseudoRandom.random()));

                constructorDefinitions = builder
                        .setComplexityLimit((long) (complexityLimit * 0.2 * PseudoRandom.random()))
                        .setMemberFunctionsLimit((int) (memberFunctionsLimit * 0.2 * PseudoRandom.random()))
                        .setStatementLimit(statementsInFunctionLimit)
                        .setOperatorLimit(operatorLimit)
                        .setLevel(level + 1)
                        .getConstructorDefinitionBlockFactory()
                        .produce();
            }
        } catch (ProductionFailedException e) {
            System.out.println("Exception during klass production process:");
            e.printStackTrace(System.out);
            throw e;
        } finally {
            SymbolTable.remove(new Symbol("this", thisKlass, thisKlass, VariableInfo.NONE));
        }

        finalizeClassFlags(thisKlass);
        TypeList.add(thisKlass);
        IRNode printVariables = builder.setLevel(2).getPrintVariablesFactory().produce();
        return createKlassNode(thisKlass, parent, interfaces, name, level,
                variableDeclarations, constructorDefinitions, functionDefinitions,
                abstractFunctionsRedefinitions, overridenFunctionsRedefinitions,
                functionDeclarations, printVariables);
    }

    protected abstract TypeKlass createThisKlass(String name);

    protected abstract boolean canInheritFrom(Type type);

    protected abstract IRNode produceVariableDeclarations(IRNodeBuilder builder, long complexity)
            throws ProductionFailedException;

    protected IRNode produceFunctionDefinitions(IRNodeBuilder builder, long complexity, int memberLimit)
            throws ProductionFailedException {
        return builder.setComplexityLimit(complexity)
                .setMemberFunctionsLimit(memberLimit)
                .setFlags(FunctionInfo.NONE)
                .getFunctionDefinitionBlockFactory()
                .produce();
    }

    protected ArrayList<Symbol> getShuffledOverrideCandidates(HashSet<Symbol> nonAbstractSet) {
        ArrayList<Symbol> candidates = new ArrayList<>(nonAbstractSet);
        PseudoRandom.shuffle(candidates);
        return candidates;
    }

    protected abstract void finalizeClassFlags(TypeKlass thisKlass);

    protected abstract T createKlassNode(TypeKlass thisKlass, TypeKlass parent,
            ArrayList<TypeKlass> interfaces, String name, int level,
            IRNode variableDeclarations, IRNode constructorDefinitions,
            IRNode functionDefinitions, IRNode abstractFunctionRedefinitions,
            IRNode overridenFunctionRedefinitions, IRNode functionDeclarations,
            IRNode printVariables);

    private void inheritClass() {
        // Grab all Klasses from the TypeList and select one to be a parent
        LinkedList<Type> probableParents = new LinkedList<>(TypeList.getAll());
        for (Iterator<Type> i = probableParents.iterator(); i.hasNext();) {
            if (!canInheritFrom(i.next())) {
                i.remove();
            }
        }
        if (probableParents.isEmpty()) {
            parent = TypeList.OBJECT;
        } else {
            parent = (TypeKlass) PseudoRandom.randomElement(probableParents);
        }
        thisKlass.addParent(parent.getName());
        thisKlass.setParent(parent);
        parent.addChild(thisKlass.getName());
        for (Symbol symbol : SymbolTable.getAllCombined(parent)) {
            if ((symbol.flags & Symbol.PRIVATE) == 0) {
                Symbol symbolCopy = symbol.deepCopy();
                if (symbolCopy instanceof FunctionInfo) {
                    FunctionInfo functionInfo = (FunctionInfo) symbolCopy;
                    if (functionInfo.isConstructor()) {
                        continue;
                    }
                    if ((functionInfo.flags & FunctionInfo.STATIC) == 0) {
                        functionInfo.argTypes.get(0).type = thisKlass;
                    }
                }
                symbolCopy.owner = thisKlass;
                SymbolTable.add(symbolCopy);
            }
        }
    }

    private void inheritInterfaces() {
        // Select interfaces that we'll implement.
        LinkedList<Type> probableInterfaces = new LinkedList<>(TypeList.getAll());
        for (Iterator<Type> i = probableInterfaces.iterator(); i.hasNext();) {
            Type klass = i.next();
            if (!(klass instanceof TypeKlass) || !((TypeKlass) klass).isInterface()) {
                i.remove();
            }
        }
        PseudoRandom.shuffle(probableInterfaces);
        int implLimit = (int) (ProductionParams.implementationLimit.value() * PseudoRandom.random());
        // Mulitiple inheritance compatibility check
        compatibility_check:
        for (Iterator<Type> i = probableInterfaces.iterator(); i.hasNext() && implLimit > 0; implLimit--) {
            TypeKlass iface = (TypeKlass) i.next();
            ArrayList<Symbol> ifaceFuncSet = SymbolTable.getAllCombined(iface, FunctionInfo.class);
            for (Symbol symbol : SymbolTable.getAllCombined(thisKlass, FunctionInfo.class)) {
                if (FunctionDefinition.isInvalidOverride((FunctionInfo) symbol, ifaceFuncSet)) {
                    continue compatibility_check;
                }
            }
            interfaces.add(iface);
            iface.addChild(thisKlass.getName());
            thisKlass.addParent(iface.getName());
            for (Symbol symbol : SymbolTable.getAllCombined(iface, FunctionInfo.class)) {
                FunctionInfo functionInfo = (FunctionInfo) symbol.deepCopy();
                functionInfo.owner = thisKlass;
                functionInfo.argTypes.get(0).type = thisKlass;
                SymbolTable.add(functionInfo);
            }
        }
    }
}
