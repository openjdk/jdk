/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.ir.Expression.isAlwaysFalse;
import static jdk.nashorn.internal.ir.Expression.isAlwaysTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.BreakableNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.JoinPredecessor;
import jdk.nashorn.internal.ir.JoinPredecessorExpression;
import jdk.nashorn.internal.ir.JumpStatement;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LexicalContextNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LocalVariableConversion;
import jdk.nashorn.internal.ir.LoopNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SplitReturn;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * Calculates types for local variables. For purposes of local variable type calculation, the only types used are
 * Undefined, boolean, int, long, double, and Object. The calculation eagerly widens types of local variable to their
 * widest at control flow join points.
 * TODO: investigate a more sophisticated solution that uses use/def information to only widens the type of a local
 * variable to its widest used type after the join point. That would eliminate some widenings of undefined variables to
 * object, most notably those used only in loops. We need a full liveness analysis for that. Currently, we can establish
 * per-type liveness, which eliminates most of unwanted dead widenings.
 */
final class LocalVariableTypesCalculator extends NodeVisitor<LexicalContext>{

    private static class JumpOrigin {
        final JoinPredecessor node;
        final Map<Symbol, LvarType> types;

        JumpOrigin(final JoinPredecessor node, final Map<Symbol, LvarType> types) {
            this.node = node;
            this.types = types;
        }
    }

    private static class JumpTarget {
        private final List<JumpOrigin> origins = new LinkedList<>();
        private Map<Symbol, LvarType> types = Collections.emptyMap();

        void addOrigin(final JoinPredecessor originNode, final Map<Symbol, LvarType> originTypes) {
            origins.add(new JumpOrigin(originNode, originTypes));
            this.types = getUnionTypes(this.types, originTypes);
        }
    }
    private enum LvarType {
        UNDEFINED(Type.UNDEFINED),
        BOOLEAN(Type.BOOLEAN),
        INT(Type.INT),
        LONG(Type.LONG),
        DOUBLE(Type.NUMBER),
        OBJECT(Type.OBJECT);

        private final Type type;
        private LvarType(final Type type) {
            this.type = type;
        }
    }

    private static final Map<Type, LvarType> TO_LVAR_TYPE = new IdentityHashMap<>();

    static {
        for(final LvarType lvarType: LvarType.values()) {
            TO_LVAR_TYPE.put(lvarType.type, lvarType);
        }
    }

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<Symbol, LvarType> cloneMap(final Map<Symbol, LvarType> map) {
        return (IdentityHashMap<Symbol, LvarType>)((IdentityHashMap<?,?>)map).clone();
    }

    private LocalVariableConversion createConversion(final Symbol symbol, final LvarType branchLvarType,
            final Map<Symbol, LvarType> joinLvarTypes, final LocalVariableConversion next) {
        final LvarType targetType = joinLvarTypes.get(symbol);
        assert targetType != null;
        if(targetType == branchLvarType) {
            return next;
        }
        // NOTE: we could naively just use symbolIsUsed(symbol, branchLvarType) here, but that'd be wrong. While
        // technically a conversion will read the value of the symbol with that type, but it will also write it to a new
        // type, and that type might be dead (we can't know yet). For this reason, we don't treat conversion reads as
        // real uses until we know their target type is live. If we didn't do this, and just did a symbolIsUsed here,
        // we'd introduce false live variables which could nevertheless turn into dead ones in a subsequent
        // deoptimization, causing a shift in the list of live locals that'd cause erroneous restoration of
        // continuations (since RewriteException's byteCodeSlots carries an array and not a name-value map).

        symbolIsConverted(symbol, branchLvarType, targetType);
        //symbolIsUsed(symbol, branchLvarType);
        return new LocalVariableConversion(symbol, branchLvarType.type, targetType.type, next);
    }

    private static Map<Symbol, LvarType> getUnionTypes(final Map<Symbol, LvarType> types1, final Map<Symbol, LvarType> types2) {
        if(types1 == types2 || types1.isEmpty()) {
            return types2;
        } else if(types2.isEmpty()) {
            return types1;
        }
        final Set<Symbol> commonSymbols = new HashSet<>(types1.keySet());
        commonSymbols.retainAll(types2.keySet());
        // We have a chance of returning an unmodified set if both sets have the same keys and one is strictly wider
        // than the other.
        final int commonSize = commonSymbols.size();
        final int types1Size = types1.size();
        final int types2Size = types2.size();
        if(commonSize == types1Size && commonSize == types2Size) {
            boolean matches1 = true, matches2 = true;
            Map<Symbol, LvarType> union = null;
            for(final Symbol symbol: commonSymbols) {
                final LvarType type1 = types1.get(symbol);
                final LvarType type2 = types2.get(symbol);
                final LvarType widest = widestLvarType(type1,  type2);
                if(widest != type1 && matches1) {
                    matches1 = false;
                    if(!matches2) {
                        union = cloneMap(types1);
                    }
                }
                if (widest != type2 && matches2) {
                    matches2 = false;
                    if(!matches1) {
                        union = cloneMap(types2);
                    }
                }
                if(!(matches1 || matches2) && union != null) { //remove overly enthusiastic "union can be null" warning
                    assert union != null;
                    union.put(symbol, widest);
                }
            }
            return matches1 ? types1 : matches2 ? types2 : union;
        }
        // General case
        final Map<Symbol, LvarType> union;
        if(types1Size > types2Size) {
            union = cloneMap(types1);
            union.putAll(types2);
        } else {
            union = cloneMap(types2);
            union.putAll(types1);
        }
        for(final Symbol symbol: commonSymbols) {
            final LvarType type1 = types1.get(symbol);
            final LvarType type2 = types2.get(symbol);
            union.put(symbol, widestLvarType(type1,  type2));
        }
        return union;
    }

    private static void symbolIsUsed(final Symbol symbol, final LvarType type) {
        if(type != LvarType.UNDEFINED) {
            symbol.setHasSlotFor(type.type);
        }
    }

    private static class SymbolConversions {
        private static byte I2L = 1 << 0;
        private static byte I2D = 1 << 1;
        private static byte I2O = 1 << 2;
        private static byte L2D = 1 << 3;
        private static byte L2O = 1 << 4;
        private static byte D2O = 1 << 5;

        private byte conversions;

        void recordConversion(final LvarType from, final LvarType to) {
            switch(from) {
            case UNDEFINED:
                return;
            case INT:
            case BOOLEAN:
                switch(to) {
                case LONG:
                    recordConversion(I2L);
                    return;
                case DOUBLE:
                    recordConversion(I2D);
                    return;
                case OBJECT:
                    recordConversion(I2O);
                    return;
                default:
                    illegalConversion(from, to);
                    return;
                }
            case LONG:
                switch(to) {
                case DOUBLE:
                    recordConversion(L2D);
                    return;
                case OBJECT:
                    recordConversion(L2O);
                    return;
                default:
                    illegalConversion(from, to);
                    return;
                }
            case DOUBLE:
                if(to == LvarType.OBJECT) {
                    recordConversion(D2O);
                }
                return;
            default:
                illegalConversion(from, to);
            }
        }

        private static void illegalConversion(final LvarType from, final LvarType to) {
            throw new AssertionError("Invalid conversion from " + from + " to " + to);
        }

        void recordConversion(final byte convFlag) {
            conversions = (byte)(conversions | convFlag);
        }

        boolean hasConversion(final byte convFlag) {
            return (conversions & convFlag) != 0;
        }

        void calculateTypeLiveness(final Symbol symbol) {
            if(symbol.hasSlotFor(Type.OBJECT)) {
                if(hasConversion(D2O)) {
                    symbol.setHasSlotFor(Type.NUMBER);
                }
                if(hasConversion(L2O)) {
                    symbol.setHasSlotFor(Type.LONG);
                }
                if(hasConversion(I2O)) {
                    symbol.setHasSlotFor(Type.INT);
                }
            }
            if(symbol.hasSlotFor(Type.NUMBER)) {
                if(hasConversion(L2D)) {
                    symbol.setHasSlotFor(Type.LONG);
                }
                if(hasConversion(I2D)) {
                    symbol.setHasSlotFor(Type.INT);
                }
            }
            if(symbol.hasSlotFor(Type.LONG)) {
                if(hasConversion(I2L)) {
                    symbol.setHasSlotFor(Type.INT);
                }
            }
        }
    }

    private void symbolIsConverted(final Symbol symbol, final LvarType from, final LvarType to) {
        SymbolConversions conversions = symbolConversions.get(symbol);
        if(conversions == null) {
            conversions = new SymbolConversions();
            symbolConversions.put(symbol, conversions);
        }
        conversions.recordConversion(from, to);
    }

    private static LvarType toLvarType(final Type type) {
        assert type != null;
        final LvarType lvarType = TO_LVAR_TYPE.get(type);
        if(lvarType != null) {
            return lvarType;
        }
        assert type.isObject();
        return LvarType.OBJECT;
    }
    private static LvarType widestLvarType(final LvarType t1, final LvarType t2) {
        if(t1 == t2) {
            return t1;
        }
        // Undefined or boolean to anything always widens to object.
        if(t1.ordinal() < LvarType.INT.ordinal() || t2.ordinal() < LvarType.INT.ordinal()) {
            return LvarType.OBJECT;
        }
        // NOTE: we allow "widening" of long to double even though it can lose precision. ECMAScript doesn't have an
        // Int64 type anyway, so this loss of precision is actually more conformant to the specification...
        return LvarType.values()[Math.max(t1.ordinal(), t2.ordinal())];
    }
    private final Compiler compiler;
    private final Map<Label, JumpTarget> jumpTargets = new IdentityHashMap<>();
    // Local variable type mapping at the currently evaluated point. No map instance is ever modified; setLvarType() always
    // allocates a new map. Immutability of maps allows for cheap snapshots by just keeping the reference to the current
    // value.
    private Map<Symbol, LvarType> localVariableTypes = new IdentityHashMap<>();

    // Whether the current point in the AST is reachable code
    private boolean reachable = true;
    // Return type of the function
    private Type returnType = Type.UNKNOWN;
    // Synthetic return node that we must insert at the end of the function if it's end is reachable.
    private ReturnNode syntheticReturn;

    private boolean alreadyEnteredTopLevelFunction;

    // LvarType and conversion information gathered during the top-down pass; applied to nodes in the bottom-up pass.
    private final Map<JoinPredecessor, LocalVariableConversion> localVariableConversions = new IdentityHashMap<>();

    private final Map<IdentNode, LvarType> identifierLvarTypes = new IdentityHashMap<>();
    private final Map<Symbol, SymbolConversions> symbolConversions = new IdentityHashMap<>();

    private SymbolToType symbolToType = new SymbolToType();

    // Stack of open labels for starts of catch blocks, one for every currently traversed try block; for inserting
    // control flow edges to them. Note that we currently don't insert actual control flow edges, but instead edges that
    // help us with type calculations. This means that some operations that can result in an exception being thrown
    // aren't considered (function calls, side effecting property getters and setters etc.), while some operations that
    // don't result in control flow transfers do originate an edge to the catch blocks (namely, assignments to local
    // variables).
    private final Deque<Label> catchLabels = new ArrayDeque<>();

    LocalVariableTypesCalculator(final Compiler compiler) {
        super(new LexicalContext());
        this.compiler = compiler;
    }

    private JumpTarget createJumpTarget(final Label label) {
        assert !jumpTargets.containsKey(label);
        final JumpTarget jumpTarget = new JumpTarget();
        jumpTargets.put(label, jumpTarget);
        return jumpTarget;
    }

    private void doesNotContinueSequentially() {
        reachable = false;
        localVariableTypes = Collections.emptyMap();
    }


    @Override
    public boolean enterBinaryNode(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();
        final boolean isAssignment = binaryNode.isAssignment();

        final TokenType tokenType = Token.descType(binaryNode.getToken());
        if(tokenType.isLeftAssociative()) {
            assert !isAssignment;
            final boolean isLogical = binaryNode.isLogical();
            final Label joinLabel = isLogical ? new Label("") : null;
            lhs.accept(this);
            if(isLogical) {
                jumpToLabel((JoinPredecessor)lhs, joinLabel);
            }
            rhs.accept(this);
            if(isLogical) {
                jumpToLabel((JoinPredecessor)rhs, joinLabel);
            }
            joinOnLabel(joinLabel);
        } else {
            rhs.accept(this);
            if(isAssignment) {
                if(lhs instanceof BaseNode) {
                    ((BaseNode)lhs).getBase().accept(this);
                    if(lhs instanceof IndexNode) {
                        ((IndexNode)lhs).getIndex().accept(this);
                    } else {
                        assert lhs instanceof AccessNode;
                    }
                } else {
                    assert lhs instanceof IdentNode;
                    if(binaryNode.isSelfModifying()) {
                        ((IdentNode)lhs).accept(this);
                    }
                }
            } else {
                lhs.accept(this);
            }
        }

        if(isAssignment && lhs instanceof IdentNode) {
            if(binaryNode.isSelfModifying()) {
                onSelfAssignment((IdentNode)lhs, binaryNode);
            } else {
                onAssignment((IdentNode)lhs, rhs);
            }
        }
        return false;
    }

    @Override
    public boolean enterBlock(final Block block) {
        for(final Symbol symbol: block.getSymbols()) {
            if(symbol.isBytecodeLocal() && getLocalVariableTypeOrNull(symbol) == null) {
                setType(symbol, LvarType.UNDEFINED);
            }
        }
        return true;
    }

    @Override
    public boolean enterBreakNode(final BreakNode breakNode) {
        return enterJumpStatement(breakNode);
    }

    @Override
    public boolean enterContinueNode(final ContinueNode continueNode) {
        return enterJumpStatement(continueNode);
    }

    private boolean enterJumpStatement(final JumpStatement jump) {
        if(!reachable) {
            return false;
        }
        final BreakableNode target = jump.getTarget(lc);
        jumpToLabel(jump, jump.getTargetLabel(target), getBreakTargetTypes(target));
        doesNotContinueSequentially();
        return false;
    }

    @Override
    protected boolean enterDefault(final Node node) {
        return reachable;
    }

    private void enterDoWhileLoop(final WhileNode loopNode) {
        final JoinPredecessorExpression test = loopNode.getTest();
        final Block body = loopNode.getBody();
        final Label continueLabel = loopNode.getContinueLabel();
        final Label breakLabel = loopNode.getBreakLabel();
        final Map<Symbol, LvarType> beforeLoopTypes = localVariableTypes;
        final Label repeatLabel = new Label("");
        for(;;) {
            jumpToLabel(loopNode, repeatLabel, beforeLoopTypes);
            final Map<Symbol, LvarType> beforeRepeatTypes = localVariableTypes;
            body.accept(this);
            if(reachable) {
                jumpToLabel(body, continueLabel);
            }
            joinOnLabel(continueLabel);
            if(!reachable) {
                break;
            }
            test.accept(this);
            jumpToLabel(test, breakLabel);
            if(isAlwaysFalse(test)) {
                break;
            }
            jumpToLabel(test, repeatLabel);
            joinOnLabel(repeatLabel);
            if(localVariableTypes.equals(beforeRepeatTypes)) {
                break;
            }
            resetJoinPoint(continueLabel);
            resetJoinPoint(breakLabel);
            resetJoinPoint(repeatLabel);
        }

        if(isAlwaysTrue(test)) {
            doesNotContinueSequentially();
        }

        leaveBreakable(loopNode);
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        if(!reachable) {
            return false;
        }

        final Expression init = forNode.getInit();
        if(forNode.isForIn()) {
            final JoinPredecessorExpression iterable = forNode.getModify();
            iterable.accept(this);
            enterTestFirstLoop(forNode, null, init,
                    // If we're iterating over property names, and we can discern from the runtime environment
                    // of the compilation that the object being iterated over must use strings for property
                    // names (e.g., it is a native JS object or array), then we'll not bother trying to treat
                    // the property names optimistically.
                    !compiler.useOptimisticTypes() || (!forNode.isForEach() && compiler.hasStringPropertyIterator(iterable.getExpression())));
        } else {
            if(init != null) {
                init.accept(this);
            }
            enterTestFirstLoop(forNode, forNode.getModify(), null, false);
        }
        return false;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if(alreadyEnteredTopLevelFunction) {
            return false;
        }
        int pos = 0;
        if(!functionNode.isVarArg()) {
            for (final IdentNode param : functionNode.getParameters()) {
                final Symbol symbol = param.getSymbol();
                // Parameter is not necessarily bytecode local as it can be scoped due to nested context use, but it
                // must have a slot if we aren't in a function with vararg signature.
                assert symbol.hasSlot();
                final Type callSiteParamType = compiler.getParamType(functionNode, pos);
                final LvarType paramType = callSiteParamType == null ? LvarType.OBJECT : toLvarType(callSiteParamType);
                setType(symbol, paramType);
                // Make sure parameter slot for its incoming value is not marked dead. NOTE: this is a heuristic. Right
                // now, CodeGenerator.expandParameters() relies on the fact that every parameter's final slot width will
                // be at least the same as incoming width, therefore even if a parameter is never read, we'll still keep
                // its slot.
                symbolIsUsed(symbol);
                setIdentifierLvarType(param, paramType);
                pos++;
            }
        }
        setCompilerConstantAsObject(functionNode, CompilerConstants.THIS);

        // TODO: coarse-grained. If we wanted to solve it completely precisely,
        // we'd also need to push/pop its type when handling WithNode (so that
        // it can go back to undefined after a 'with' block.
        if(functionNode.hasScopeBlock() || functionNode.needsParentScope()) {
            setCompilerConstantAsObject(functionNode, CompilerConstants.SCOPE);
        }
        if(functionNode.needsCallee()) {
            setCompilerConstantAsObject(functionNode, CompilerConstants.CALLEE);
        }
        if(functionNode.needsArguments()) {
            setCompilerConstantAsObject(functionNode, CompilerConstants.ARGUMENTS);
        }

        alreadyEnteredTopLevelFunction = true;
        return true;
    }

    @Override
    public boolean enterIdentNode(final IdentNode identNode) {
        final Symbol symbol = identNode.getSymbol();
        if(symbol.isBytecodeLocal()) {
            symbolIsUsed(symbol);
            setIdentifierLvarType(identNode, getLocalVariableType(symbol));
        }
        return false;
    }

    @Override
    public boolean enterIfNode(final IfNode ifNode) {
        if(!reachable) {
            return false;
        }

        final Expression test = ifNode.getTest();
        final Block pass = ifNode.getPass();
        final Block fail = ifNode.getFail();

        test.accept(this);

        final Map<Symbol, LvarType> afterTestLvarTypes = localVariableTypes;
        if(!isAlwaysFalse(test)) {
            pass.accept(this);
        }
        final Map<Symbol, LvarType> passLvarTypes = localVariableTypes;
        final boolean reachableFromPass = reachable;

        reachable = true;
        localVariableTypes = afterTestLvarTypes;
        if(!isAlwaysTrue(test) && fail != null) {
            fail.accept(this);
            final boolean reachableFromFail = reachable;
            reachable |= reachableFromPass;
            if(!reachable) {
                return false;
            }

            if(reachableFromFail) {
                if(reachableFromPass) {
                    final Map<Symbol, LvarType> failLvarTypes = localVariableTypes;
                    localVariableTypes = getUnionTypes(passLvarTypes, failLvarTypes);
                    setConversion(pass, passLvarTypes, localVariableTypes);
                    setConversion(fail, failLvarTypes, localVariableTypes);
                }
                return false;
            }
        }

        if(reachableFromPass) {
            localVariableTypes = getUnionTypes(afterTestLvarTypes, passLvarTypes);
            // IfNode itself is associated with conversions that might need to be performed after the test if there's no
            // else branch. E.g.
            // if(x = 1, cond) { x = 1.0 } must widen "x = 1" to a double.
            setConversion(pass, passLvarTypes, localVariableTypes);
            setConversion(ifNode, afterTestLvarTypes, localVariableTypes);
        } else {
            localVariableTypes = afterTestLvarTypes;
        }

        return false;
    }

    @Override
    public boolean enterPropertyNode(final PropertyNode propertyNode) {
        // Avoid falsely adding property keys to the control flow graph
        if(propertyNode.getValue() != null) {
            propertyNode.getValue().accept(this);
        }
        return false;
    }

    @Override
    public boolean enterReturnNode(final ReturnNode returnNode) {
        if(!reachable) {
            return false;
        }

        final Expression returnExpr = returnNode.getExpression();
        final Type returnExprType;
        if(returnExpr != null) {
            returnExpr.accept(this);
            returnExprType = getType(returnExpr);
        } else {
            returnExprType = Type.UNDEFINED;
        }
        returnType = Type.widestReturnType(returnType, returnExprType);
        doesNotContinueSequentially();
        return false;
    }

    @Override
    public boolean enterSplitReturn(final SplitReturn splitReturn) {
        doesNotContinueSequentially();
        return false;
    }

    @Override
    public boolean enterSwitchNode(final SwitchNode switchNode) {
        if(!reachable) {
            return false;
        }

        final Expression expr = switchNode.getExpression();
        expr.accept(this);

        final List<CaseNode> cases = switchNode.getCases();
        if(cases.isEmpty()) {
            return false;
        }

        // Control flow is different for all-integer cases where we dispatch by switch table, and for all other cases
        // where we do sequential comparison. Note that CaseNode objects act as join points.
        final boolean isInteger = switchNode.isInteger();
        final Label breakLabel = switchNode.getBreakLabel();
        final boolean hasDefault = switchNode.getDefaultCase() != null;

        boolean tagUsed = false;
        for(final CaseNode caseNode: cases) {
            final Expression test = caseNode.getTest();
            if(!isInteger && test != null) {
                test.accept(this);
                if(!tagUsed) {
                    symbolIsUsed(switchNode.getTag(), LvarType.OBJECT);
                    tagUsed = true;
                }
            }
            // CaseNode carries the conversions that need to be performed on its entry from the test.
            // CodeGenerator ensures these are only emitted when arriving on the branch and not through a
            // fallthrough.
            jumpToLabel(caseNode, caseNode.getBody().getEntryLabel());
        }
        if(!hasDefault) {
            // No default case means we can arrive at the break label without entering any cases. In that case
            // SwitchNode will carry the conversions that need to be performed before it does that jump.
            jumpToLabel(switchNode, breakLabel);
        }

        // All cases are arrived at through jumps
        doesNotContinueSequentially();

        Block previousBlock = null;
        for(final CaseNode caseNode: cases) {
            final Block body = caseNode.getBody();
            final Label entryLabel = body.getEntryLabel();
            if(previousBlock != null && reachable) {
                jumpToLabel(previousBlock, entryLabel);
            }
            joinOnLabel(entryLabel);
            assert reachable == true;
            body.accept(this);
            previousBlock = body;
        }
        if(previousBlock != null && reachable) {
            jumpToLabel(previousBlock, breakLabel);
        }
        leaveBreakable(switchNode);
        return false;
    }

    @Override
    public boolean enterTernaryNode(final TernaryNode ternaryNode) {
        final Expression test = ternaryNode.getTest();
        final Expression trueExpr = ternaryNode.getTrueExpression();
        final Expression falseExpr = ternaryNode.getFalseExpression();

        test.accept(this);

        final Map<Symbol, LvarType> testExitLvarTypes = localVariableTypes;
        if(!isAlwaysFalse(test)) {
            trueExpr.accept(this);
        }
        final Map<Symbol, LvarType> trueExitLvarTypes = localVariableTypes;
        localVariableTypes = testExitLvarTypes;
        if(!isAlwaysTrue(test)) {
            falseExpr.accept(this);
        }
        final Map<Symbol, LvarType> falseExitLvarTypes = localVariableTypes;
        localVariableTypes = getUnionTypes(trueExitLvarTypes, falseExitLvarTypes);
        setConversion((JoinPredecessor)trueExpr, trueExitLvarTypes, localVariableTypes);
        setConversion((JoinPredecessor)falseExpr, falseExitLvarTypes, localVariableTypes);
        return false;
    }

    private void enterTestFirstLoop(final LoopNode loopNode, final JoinPredecessorExpression modify,
            final Expression iteratorValues, final boolean iteratorValuesAreObject) {
        final JoinPredecessorExpression test = loopNode.getTest();
        if(isAlwaysFalse(test)) {
            test.accept(this);
            return;
        }

        final Label continueLabel = loopNode.getContinueLabel();
        final Label breakLabel = loopNode.getBreakLabel();

        final Label repeatLabel = modify == null ? continueLabel : new Label("");
        final Map<Symbol, LvarType> beforeLoopTypes = localVariableTypes;
        for(;;) {
            jumpToLabel(loopNode, repeatLabel, beforeLoopTypes);
            final Map<Symbol, LvarType> beforeRepeatTypes = localVariableTypes;
            if(test != null) {
                test.accept(this);
            }
            if(!isAlwaysTrue(test)) {
                jumpToLabel(test, breakLabel);
            }
            if(iteratorValues instanceof IdentNode) {
                final IdentNode ident = (IdentNode)iteratorValues;
                // Receives iterator values; the optimistic type of the iterator values is tracked on the
                // identifier, but we override optimism if it's known that the object being iterated over will
                // never have primitive property names.
                onAssignment(ident, iteratorValuesAreObject ? LvarType.OBJECT :
                    toLvarType(compiler.getOptimisticType(ident)));
            }
            final Block body = loopNode.getBody();
            body.accept(this);
            if(reachable) {
                jumpToLabel(body, continueLabel);
            }
            joinOnLabel(continueLabel);
            if(!reachable) {
                break;
            }
            if(modify != null) {
                modify.accept(this);
                jumpToLabel(modify, repeatLabel);
                joinOnLabel(repeatLabel);
            }
            if(localVariableTypes.equals(beforeRepeatTypes)) {
                break;
            }
            // Reset the join points and repeat the analysis
            resetJoinPoint(continueLabel);
            resetJoinPoint(breakLabel);
            resetJoinPoint(repeatLabel);
        }

        if(isAlwaysTrue(test) && iteratorValues == null) {
            doesNotContinueSequentially();
        }

        leaveBreakable(loopNode);
    }

    @Override
    public boolean enterThrowNode(final ThrowNode throwNode) {
        if(!reachable) {
            return false;
        }

        throwNode.getExpression().accept(this);
        jumpToCatchBlock(throwNode);
        doesNotContinueSequentially();
        return false;
    }

    @Override
    public boolean enterTryNode(final TryNode tryNode) {
        if(!reachable) {
            return false;
        }

        // This is the label for the join point at the entry of the catch blocks.
        final Label catchLabel = new Label("");
        catchLabels.push(catchLabel);

        // Presume that even the start of the try block can immediately go to the catch
        jumpToLabel(tryNode, catchLabel);

        final Block body = tryNode.getBody();
        body.accept(this);
        catchLabels.pop();

        // Final exit label for the whole try/catch construct (after the try block and after all catches).
        final Label endLabel = new Label("");

        boolean canExit = false;
        if(reachable) {
            jumpToLabel(body, endLabel);
            canExit = true;
        }
        doesNotContinueSequentially();

        joinOnLabel(catchLabel);
        for(final CatchNode catchNode: tryNode.getCatches()) {
            final IdentNode exception = catchNode.getException();
            onAssignment(exception, LvarType.OBJECT);
            final Expression condition = catchNode.getExceptionCondition();
            if(condition != null) {
                condition.accept(this);
            }
            final Map<Symbol, LvarType> afterConditionTypes = localVariableTypes;
            final Block catchBody = catchNode.getBody();
            // TODO: currently, we consider that the catch blocks are always reachable from the try block as currently
            // we lack enough analysis to prove that no statement before a break/continue/return in the try block can
            // throw an exception.
            reachable = true;
            catchBody.accept(this);
            final Symbol exceptionSymbol = exception.getSymbol();
            if(reachable) {
                localVariableTypes = cloneMap(localVariableTypes);
                localVariableTypes.remove(exceptionSymbol);
                jumpToLabel(catchBody, endLabel);
                canExit = true;
            }
            localVariableTypes = cloneMap(afterConditionTypes);
            localVariableTypes.remove(exceptionSymbol);
        }
        // NOTE: if we had one or more conditional catch blocks with no unconditional catch block following them, then
        // there will be an unconditional rethrow, so the join point can never be reached from the last
        // conditionExpression.
        doesNotContinueSequentially();

        if(canExit) {
            joinOnLabel(endLabel);
        }

        return false;
    }


    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        final Expression expr = unaryNode.getExpression();
        expr.accept(this);

        if(unaryNode.isSelfModifying()) {
            if(expr instanceof IdentNode) {
                onSelfAssignment((IdentNode)expr, unaryNode);
            }
        }
        return false;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        if (!reachable) {
            return false;
        }
        final Expression init = varNode.getInit();
        if(init != null) {
            init.accept(this);
            onAssignment(varNode.getName(), init);
        }
        return false;
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        if(!reachable) {
            return false;
        }
        if(whileNode.isDoWhile()) {
            enterDoWhileLoop(whileNode);
        } else {
            enterTestFirstLoop(whileNode, null, null, false);
        }
        return false;
    }

    private Map<Symbol, LvarType> getBreakTargetTypes(final BreakableNode target) {
        // Remove symbols defined in the the blocks that are being broken out of.
        Map<Symbol, LvarType> types = localVariableTypes;
        for(final Iterator<LexicalContextNode> it = lc.getAllNodes(); it.hasNext();) {
            final LexicalContextNode node = it.next();
            if(node instanceof Block) {
                for(final Symbol symbol: ((Block)node).getSymbols()) {
                    if(localVariableTypes.containsKey(symbol)) {
                        if(types == localVariableTypes) {
                            types = cloneMap(localVariableTypes);
                        }
                        types.remove(symbol);
                    }
                }
            }
            if(node == target) {
                break;
            }
        }
        return types;
    }

    private LvarType getLocalVariableType(final Symbol symbol) {
        final LvarType type = getLocalVariableTypeOrNull(symbol);
        assert type != null;
        return type;
    }

    private LvarType getLocalVariableTypeOrNull(final Symbol symbol) {
        return localVariableTypes.get(symbol);
    }

    private JumpTarget getOrCreateJumpTarget(final Label label) {
        JumpTarget jumpTarget = jumpTargets.get(label);
        if(jumpTarget == null) {
            jumpTarget = createJumpTarget(label);
        }
        return jumpTarget;
    }


    /**
     * If there's a join point associated with a label, insert the join point into the flow.
     * @param label the label to insert a join point for.
     */
    private void joinOnLabel(final Label label) {
        final JumpTarget jumpTarget = jumpTargets.remove(label);
        if(jumpTarget == null) {
            return;
        }
        assert !jumpTarget.origins.isEmpty();
        reachable = true;
        localVariableTypes = getUnionTypes(jumpTarget.types, localVariableTypes);
        for(final JumpOrigin jumpOrigin: jumpTarget.origins) {
            setConversion(jumpOrigin.node, jumpOrigin.types, localVariableTypes);
        }
    }

    /**
     * If we're in a try/catch block, add an edge from the specified node to the try node's pre-catch label.
     */
    private void jumpToCatchBlock(final JoinPredecessor jumpOrigin) {
        final Label currentCatchLabel = catchLabels.peek();
        if(currentCatchLabel != null) {
            jumpToLabel(jumpOrigin, currentCatchLabel);
        }
    }

    private void jumpToLabel(final JoinPredecessor jumpOrigin, final Label label) {
        jumpToLabel(jumpOrigin, label, localVariableTypes);
    }

    private void jumpToLabel(final JoinPredecessor jumpOrigin, final Label label, final Map<Symbol, LvarType> types) {
        getOrCreateJumpTarget(label).addOrigin(jumpOrigin, types);
    }

    @Override
    public Node leaveBlock(final Block block) {
        if(lc.isFunctionBody()) {
            if(reachable) {
                // reachable==true means we can reach the end of the function without an explicit return statement. We
                // need to insert a synthetic one then. This logic used to be in Lower.leaveBlock(), but Lower's
                // reachability analysis (through Terminal.isTerminal() flags) is not precise enough so
                // Lower$BlockLexicalContext.afterSetStatements will sometimes think the control flow terminates even
                // when it didn't. Example: function() { switch((z)) { default: {break; } throw x; } }.
                createSyntheticReturn(block);
                assert !reachable;
            }
            // We must calculate the return type here (and not in leaveFunctionNode) as it can affect the liveness of
            // the :return symbol and thus affect conversion type liveness calculations for it.
            calculateReturnType();
        }

        boolean cloned = false;
        for(final Symbol symbol: block.getSymbols()) {
            // Undefine the symbol outside the block
            if(localVariableTypes.containsKey(symbol)) {
                if(!cloned) {
                    localVariableTypes = cloneMap(localVariableTypes);
                    cloned = true;
                }
                localVariableTypes.remove(symbol);
            }

            if(symbol.hasSlot()) {
                final SymbolConversions conversions = symbolConversions.get(symbol);
                if(conversions != null) {
                    // Potentially make some currently dead types live if they're needed as a source of a type
                    // conversion at a join.
                    conversions.calculateTypeLiveness(symbol);
                }
                if(symbol.slotCount() == 0) {
                    // This is a local variable that is never read. It won't need a slot.
                    symbol.setNeedsSlot(false);
                }
            }
        }

        if(reachable) {
            // TODO: this is totally backwards. Block should not be breakable, LabelNode should be breakable.
            final LabelNode labelNode = lc.getCurrentBlockLabelNode();
            if(labelNode != null) {
                jumpToLabel(labelNode, block.getBreakLabel());
            }
        }
        leaveBreakable(block);
        return block;
    }

    private void calculateReturnType() {
        // NOTE: if return type is unknown, then the function does not explicitly return a value. Such a function under
        // ECMAScript rules returns Undefined, which has Type.OBJECT. We might consider an optimization in the future
        // where we can return void functions.
        if(returnType.isUnknown()) {
            returnType = Type.OBJECT;
        }
    }

    private void createSyntheticReturn(final Block body) {
        final FunctionNode functionNode = lc.getCurrentFunction();
        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();
        final List<Statement> statements = body.getStatements();
        final int lineNumber = statements.isEmpty() ? functionNode.getLineNumber() : statements.get(statements.size() - 1).getLineNumber();
        final IdentNode returnExpr;
        if(functionNode.isProgram()) {
            returnExpr = new IdentNode(token, finish, RETURN.symbolName()).setSymbol(getCompilerConstantSymbol(functionNode, RETURN));
        } else {
            returnExpr = null;
        }
        syntheticReturn = new ReturnNode(lineNumber, token, finish, returnExpr);
        syntheticReturn.accept(this);
    }

    /**
     * Leave a breakable node. If there's a join point associated with its break label (meaning there was at least one
     * break statement to the end of the node), insert the join point into the flow.
     * @param breakable the breakable node being left.
     */
    private void leaveBreakable(final BreakableNode breakable) {
        joinOnLabel(breakable.getBreakLabel());
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        // Sets the return type of the function and also performs the bottom-up pass of applying type and conversion
        // information to nodes as well as doing the calculation on nested functions as required.
        FunctionNode newFunction = functionNode;
        final NodeVisitor<LexicalContext> applyChangesVisitor = new NodeVisitor<LexicalContext>(new LexicalContext()) {
            private boolean inOuterFunction = true;
            private final Deque<JoinPredecessor> joinPredecessors = new ArrayDeque<>();

            @Override
            protected boolean enterDefault(final Node node) {
                if(!inOuterFunction) {
                    return false;
                }
                if(node instanceof JoinPredecessor) {
                    joinPredecessors.push((JoinPredecessor)node);
                }
                return inOuterFunction;
            }

            @Override
            public boolean enterFunctionNode(final FunctionNode fn) {
                if(compiler.isOnDemandCompilation()) {
                    // Only calculate nested function local variable types if we're doing eager compilation
                    return false;
                }
                inOuterFunction = false;
                return true;
            }

            @SuppressWarnings("fallthrough")
            @Override
            public Node leaveBinaryNode(final BinaryNode binaryNode) {
                if(binaryNode.isComparison()) {
                    final Expression lhs = binaryNode.lhs();
                    final Expression rhs = binaryNode.rhs();

                    Type cmpWidest = Type.widest(lhs.getType(), rhs.getType());
                    boolean newRuntimeNode = false, finalized = false;
                    final TokenType tt = binaryNode.tokenType();
                    switch (tt) {
                    case EQ_STRICT:
                    case NE_STRICT:
                        // Specialize comparison with undefined
                        final Expression undefinedNode = createIsUndefined(binaryNode, lhs, rhs,
                                tt == TokenType.EQ_STRICT ? Request.IS_UNDEFINED : Request.IS_NOT_UNDEFINED);
                        if(undefinedNode != binaryNode) {
                            return undefinedNode;
                        }
                        // Specialize comparison of boolean with non-boolean
                        if (lhs.getType().isBoolean() != rhs.getType().isBoolean()) {
                            newRuntimeNode = true;
                            cmpWidest = Type.OBJECT;
                            finalized = true;
                        }
                        // fallthrough
                    default:
                        if (newRuntimeNode || cmpWidest.isObject()) {
                            return new RuntimeNode(binaryNode).setIsFinal(finalized);
                        }
                    }
                } else if(binaryNode.isOptimisticUndecidedType()) {
                    // At this point, we can assign a static type to the optimistic binary ADD operator as now we know
                    // the types of its operands.
                    return binaryNode.decideType();
                }
                return binaryNode;
            }

            @Override
            protected Node leaveDefault(final Node node) {
                if(node instanceof JoinPredecessor) {
                    final JoinPredecessor original = joinPredecessors.pop();
                    assert original.getClass() == node.getClass() : original.getClass().getName() + "!=" + node.getClass().getName();
                    return (Node)setLocalVariableConversion(original, (JoinPredecessor)node);
                }
                return node;
            }

            @Override
            public Node leaveBlock(final Block block) {
                if(inOuterFunction && syntheticReturn != null && lc.isFunctionBody()) {
                    final ArrayList<Statement> stmts = new ArrayList<>(block.getStatements());
                    stmts.add((ReturnNode)syntheticReturn.accept(this));
                    return block.setStatements(lc, stmts);
                }
                return super.leaveBlock(block);
            }

            @Override
            public Node leaveFunctionNode(final FunctionNode nestedFunctionNode) {
                inOuterFunction = true;
                final FunctionNode newNestedFunction = (FunctionNode)nestedFunctionNode.accept(
                        new LocalVariableTypesCalculator(compiler));
                lc.replace(nestedFunctionNode, newNestedFunction);
                return newNestedFunction;
            }

            @Override
            public Node leaveIdentNode(final IdentNode identNode) {
                final IdentNode original = (IdentNode)joinPredecessors.pop();
                final Symbol symbol = identNode.getSymbol();
                if(symbol == null) {
                    assert identNode.isPropertyName();
                    return identNode;
                } else if(symbol.hasSlot()) {
                    assert !symbol.isScope() || symbol.isParam(); // Only params can be slotted and scoped.
                    assert original.getName().equals(identNode.getName());
                    final LvarType lvarType = identifierLvarTypes.remove(original);
                    if(lvarType != null) {
                        return setLocalVariableConversion(original, identNode.setType(lvarType.type));
                    }
                    // If there's no type, then the identifier must've been in unreachable code. In that case, it can't
                    // have assigned conversions either.
                    assert localVariableConversions.get(original) == null;
                } else {
                    assert identIsDeadAndHasNoLiveConversions(original);
                }
                return identNode;
            }

            @Override
            public Node leaveLiteralNode(final LiteralNode<?> literalNode) {
                //for e.g. ArrayLiteralNodes the initial types may have been narrowed due to the
                //introduction of optimistic behavior - hence ensure that all literal nodes are
                //reinitialized
                return literalNode.initialize(lc);
            }

            @Override
            public Node leaveRuntimeNode(final RuntimeNode runtimeNode) {
                final Request request = runtimeNode.getRequest();
                final boolean isEqStrict = request == Request.EQ_STRICT;
                if(isEqStrict || request == Request.NE_STRICT) {
                    return createIsUndefined(runtimeNode, runtimeNode.getArgs().get(0), runtimeNode.getArgs().get(1),
                            isEqStrict ? Request.IS_UNDEFINED : Request.IS_NOT_UNDEFINED);
                }
                return runtimeNode;
            }

            @SuppressWarnings("unchecked")
            private <T extends JoinPredecessor> T setLocalVariableConversion(final JoinPredecessor original, final T jp) {
                // NOTE: can't use Map.remove() as our copy-on-write AST semantics means some nodes appear twice (in
                // finally blocks), so we need to be able to access conversions for them multiple times.
                return (T)jp.setLocalVariableConversion(lc, localVariableConversions.get(original));
            }
        };

        newFunction = newFunction.setBody(lc, (Block)newFunction.getBody().accept(applyChangesVisitor));
        newFunction = newFunction.setReturnType(lc, returnType);


        newFunction = newFunction.setState(lc, CompilationState.LOCAL_VARIABLE_TYPES_CALCULATED);
        newFunction = newFunction.setParameters(lc, newFunction.visitParameters(applyChangesVisitor));
        return newFunction;
    }

    private static Expression createIsUndefined(final Expression parent, final Expression lhs, final Expression rhs, final Request request) {
        if (isUndefinedIdent(lhs) || isUndefinedIdent(rhs)) {
            return new RuntimeNode(parent, request, lhs, rhs);
        }
        return parent;
    }

    private static boolean isUndefinedIdent(final Expression expr) {
        return expr instanceof IdentNode && "undefined".equals(((IdentNode)expr).getName());
    }

    private boolean identIsDeadAndHasNoLiveConversions(final IdentNode identNode) {
        final LocalVariableConversion conv = localVariableConversions.get(identNode);
        return conv == null || !conv.isLive();
    }

    private void onAssignment(final IdentNode identNode, final Expression rhs) {
        onAssignment(identNode, toLvarType(getType(rhs)));
    }

    private void onAssignment(final IdentNode identNode, final LvarType type) {
        final Symbol symbol = identNode.getSymbol();
        assert symbol != null : identNode.getName();
        if(!symbol.isBytecodeLocal()) {
            return;
        }
        assert type != null;
        final LvarType finalType;
        if(type == LvarType.UNDEFINED && getLocalVariableType(symbol) != LvarType.UNDEFINED) {
            // Explicit assignment of a known undefined local variable to a local variable that is not undefined will
            // materialize that undefined in the assignment target. Note that assigning known undefined to known
            // undefined will *not* initialize the variable, e.g. "var x; var y = x;" compiles to no-op.
            finalType = LvarType.OBJECT;
            symbol.setFlag(Symbol.HAS_OBJECT_VALUE);
        } else {
            finalType = type;
        }
        setType(symbol, finalType);
        // Explicit assignment of an undefined value. Make sure the variable can store an object
        // TODO: if we communicated the fact to codegen with a flag on the IdentNode that the value was already
        // undefined before the assignment, we could just ignore it. In general, we could ignore an assignment if we
        // know that the value assigned is the same as the current value of the variable, but we'd need constant
        // propagation for that.
        setIdentifierLvarType(identNode, finalType);
        // For purposes of type calculation, we consider an assignment to a local variable to be followed by
        // the catch nodes of the current (if any) try block. This will effectively enforce that narrower
        // assignments to a local variable in a try block will also have to store a widened value as well. Code
        // within the try block will be able to keep loading the narrower value, but after the try block only
        // the widest value will remain live.
        // Rationale for this is that if there's an use for that variable in any of the catch blocks, or
        // following the catch blocks, they must use the widest type.
        // Example:
        /*
            Originally:
            ===========
            var x;
            try {
              x = 1; <-- stores into int slot for x
              f(x); <-- loads the int slot for x
              x = 3.14 <-- stores into the double slot for x
              f(x); <-- loads the double slot for x
              x = 1; <-- stores into int slot for x
              f(x); <-- loads the int slot for x
            } finally {
              f(x); <-- loads the double slot for x, but can be reached by a path where x is int, so we need
                           to go back and ensure that double values are also always stored along with int
                           values.
            }

            After correction:
            =================

            var x;
            try {
              x = 1; <-- stores into both int and double slots for x
              f(x); <-- loads the int slot for x
              x = 3.14 <-- stores into the double slot for x
              f(x); <-- loads the double slot for x
              x = 1; <-- stores into both int and double slots for x
              f(x); <-- loads the int slot for x
            } finally {
              f(x); <-- loads the double slot for x
            }
         */
        jumpToCatchBlock(identNode);
    }

    private void onSelfAssignment(final IdentNode identNode, final Expression assignment) {
        final Symbol symbol = identNode.getSymbol();
        assert symbol != null : identNode.getName();
        if(!symbol.isBytecodeLocal()) {
            return;
        }
        final LvarType type = toLvarType(getType(assignment));
        // Self-assignment never produce either a boolean or undefined
        assert type != null && type != LvarType.UNDEFINED && type != LvarType.BOOLEAN;
        setType(symbol, type);
        jumpToCatchBlock(identNode);
    }

    private void resetJoinPoint(final Label label) {
        jumpTargets.remove(label);
    }

    private void setCompilerConstantAsObject(final FunctionNode functionNode, final CompilerConstants cc) {
        final Symbol symbol = getCompilerConstantSymbol(functionNode, cc);
        setType(symbol, LvarType.OBJECT);
        // never mark compiler constants as dead
        symbolIsUsed(symbol);
    }

    private static Symbol getCompilerConstantSymbol(final FunctionNode functionNode, final CompilerConstants cc) {
        return functionNode.getBody().getExistingSymbol(cc.symbolName());
    }

    private void setConversion(final JoinPredecessor node, final Map<Symbol, LvarType> branchLvarTypes, final Map<Symbol, LvarType> joinLvarTypes) {
        if(node == null) {
            return;
        }
        if(branchLvarTypes.isEmpty() || joinLvarTypes.isEmpty()) {
            localVariableConversions.remove(node);
        }

        LocalVariableConversion conversion = null;
        if(node instanceof IdentNode) {
            // conversions on variable assignment in try block are special cases, as they only apply to the variable
            // being assigned and all other conversions should be ignored.
            final Symbol symbol = ((IdentNode)node).getSymbol();
            conversion = createConversion(symbol, branchLvarTypes.get(symbol), joinLvarTypes, null);
        } else {
            for(final Map.Entry<Symbol, LvarType> entry: branchLvarTypes.entrySet()) {
                final Symbol symbol = entry.getKey();
                final LvarType branchLvarType = entry.getValue();
                conversion = createConversion(symbol, branchLvarType, joinLvarTypes, conversion);
            }
        }
        if(conversion != null) {
            localVariableConversions.put(node, conversion);
        } else {
            localVariableConversions.remove(node);
        }
    }

    private void setIdentifierLvarType(final IdentNode identNode, final LvarType type) {
        assert type != null;
        identifierLvarTypes.put(identNode, type);
    }

    /**
     * Marks a local variable as having a specific type from this point onward. Invoked by stores to local variables.
     * @param symbol the symbol representing the variable
     * @param type the type
     */
    private void setType(final Symbol symbol, final LvarType type) {
        if(getLocalVariableTypeOrNull(symbol) == type) {
            return;
        }
        assert symbol.hasSlot();
        assert !symbol.isGlobal();
        localVariableTypes = localVariableTypes.isEmpty() ? new IdentityHashMap<Symbol, LvarType>() : cloneMap(localVariableTypes);
        localVariableTypes.put(symbol, type);
    }

    /**
     * Set a flag in the symbol marking it as needing to be able to store a value of a particular type. Every symbol for
     * a local variable will be assigned between 1 and 6 local variable slots for storing all types it is known to need
     * to store.
     * @param symbol the symbol
     */
    private void symbolIsUsed(final Symbol symbol) {
        symbolIsUsed(symbol, getLocalVariableType(symbol));
    }

    private Type getType(final Expression expr) {
        return expr.getType(getSymbolToType());
    }

    private Function<Symbol, Type> getSymbolToType() {
        // BinaryNode uses identity of the function to cache type calculations. Therefore, we must use different
        // function instances for different localVariableTypes instances.
        if(symbolToType.isStale()) {
            symbolToType = new SymbolToType();
        }
        return symbolToType;
    }

    private class SymbolToType implements Function<Symbol, Type> {
        private final Object boundTypes = localVariableTypes;
        @Override
        public Type apply(final Symbol t) {
            return getLocalVariableType(t).type;
        }

        boolean isStale() {
            return boundTypes != localVariableTypes;
        }
    }
}
