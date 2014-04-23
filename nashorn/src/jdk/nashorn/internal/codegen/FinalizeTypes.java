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

import static jdk.nashorn.internal.codegen.CompilerConstants.CALLEE;
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Lower to more primitive operations. After lowering, an AST has symbols and
 * types. Lowering may also add specialized versions of methods to the script if
 * the optimizer is turned on.
 *
 * Any expression that requires temporary storage as part of computation will
 * also be detected here and give a temporary symbol
 *
 * For any op that we process in FinalizeTypes it is an absolute guarantee
 * that scope and slot information is correct. This enables e.g. AccessSpecialization
 * and frame optimizations
 */
@Logger(name="finalize")
final class FinalizeTypes extends NodeOperatorVisitor<LexicalContext> implements Loggable {

    private final DebugLogger log;

    FinalizeTypes(final CompilationEnvironment env) {
        super(new LexicalContext());
        this.log = initLogger(env.getContext());
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        if (forNode.isForIn()) {
            return forNode;
        }

        final Expression init   = forNode.getInit();
        final Expression test   = forNode.getTest();
        final Expression modify = forNode.getModify();

        assert test != null || forNode.hasGoto() : "forNode " + forNode + " needs goto and is missing it in " + lc.getCurrentFunction();

        return forNode.
            setInit(lc, init == null ? null : discard(init)).
            setModify(lc, modify == null ? null : discard(modify));
    }

    private static Node createIsUndefined(final Expression parent, final Expression lhs, final Expression rhs, final Request request) {
        if ("undefined".equals(lhs.getSymbol().getName()) || "undefined".equals(rhs.getSymbol().getName())) {
            return new RuntimeNode(parent, request, lhs, rhs);
        }
        return parent;
    }

    @Override
    public Node leaveEQ_STRICT(final BinaryNode binaryNode) {
        return createIsUndefined(binaryNode, binaryNode.lhs(), binaryNode.rhs(), Request.IS_UNDEFINED);
    }

    @Override
    public Node leaveNE_STRICT(final BinaryNode binaryNode) {
        return createIsUndefined(binaryNode, binaryNode.lhs(), binaryNode.rhs(), Request.IS_NOT_UNDEFINED);
    }

    @Override
    public Node leaveRuntimeNode(final RuntimeNode runtimeNode) {
        switch (runtimeNode.getRequest()) {
        case EQ_STRICT:
            return createIsUndefined(runtimeNode, runtimeNode.getArgs().get(0), runtimeNode.getArgs().get(1), Request.IS_UNDEFINED);
        case NE_STRICT:
            return createIsUndefined(runtimeNode, runtimeNode.getArgs().get(0), runtimeNode.getArgs().get(1), Request.IS_NOT_UNDEFINED);
        default:
            return runtimeNode;
        }
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null;
        return binaryNode.setRHS(discard(binaryNode.rhs()));
    }

    @Override
    public Node leaveCOMMARIGHT(final BinaryNode binaryNode) {
        assert binaryNode.getSymbol() != null;
        return binaryNode.setLHS(discard(binaryNode.lhs()));
    }

    @Override
    public boolean enterBlock(final Block block) {
        updateSymbols(block);
        return true;
    }

    @Override
    public Node leaveExpressionStatement(final ExpressionStatement expressionStatement) {
        return expressionStatement.setExpression(discard(expressionStatement.getExpression()));
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        // TODO: now that Splitter comes before Attr, these can probably all be moved to Attr.

        // If the function doesn't need a callee, we ensure its CALLEE symbol doesn't get a slot. We can't do this
        // earlier, as access to scoped variables, self symbol, etc. in previous phases can all trigger the need for the
        // callee.
        if (!functionNode.needsCallee()) {
            functionNode.compilerConstant(CALLEE).setNeedsSlot(false);
        }
        // Similar reasoning applies to SCOPE symbol: if the function doesn't need either parent scope and none of its
        // blocks create a scope, we ensure it doesn't get a slot, but we can't determine whether it needs a scope
        // earlier than this phase.
        if (!(functionNode.hasScopeBlock() || functionNode.needsParentScope())) {
            functionNode.compilerConstant(SCOPE).setNeedsSlot(false);
        }
        // Also, we must wait until after Splitter to see if the function ended up needing the RETURN symbol.
        if (!functionNode.usesReturnSymbol()) {
            functionNode.compilerConstant(RETURN).setNeedsSlot(false);
        }
        // Named function expressions that end up not referencing themselves won't need a local slot for the self symbol.
        if(!functionNode.isDeclared() && !functionNode.usesSelfSymbol() && !functionNode.isAnonymous()) {
            final Symbol selfSymbol = functionNode.getBody().getExistingSymbol(functionNode.getIdent().getName());
            if(selfSymbol != null) {
                if(selfSymbol.isFunctionSelf()) {
                    selfSymbol.setNeedsSlot(false);
                    selfSymbol.clearFlag(Symbol.IS_VAR);
                }
            } else {
                assert functionNode.isProgram();
            }
        }
        return true;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        return functionNode.setState(lc, CompilationState.FINALIZED);
    }

    private void updateSymbolsLog(final FunctionNode functionNode, final Symbol symbol, final boolean loseSlot) {
        if (log.isEnabled()) {
            if (!symbol.isScope()) {
                log.finest("updateSymbols: ", symbol, " => scope, because all vars in ", functionNode.getName(), " are in scope");
            }
            if (loseSlot && symbol.hasSlot()) {
                log.finest("updateSymbols: ", symbol, " => no slot, because all vars in ", functionNode.getName(), " are in scope");
            }
        }
    }

    /**
     * Called after a block or function node (subclass of block) is finished. Guarantees
     * that scope and slot information is correct for every symbol
     * @param block block for which to to finalize type info.
     */
    private void updateSymbols(final Block block) {
        if (!block.needsScope()) {
            return; // nothing to do
        }

        final FunctionNode   functionNode   = lc.getFunction(block);
        final boolean        allVarsInScope = functionNode.allVarsInScope();
        final boolean        isVarArg       = functionNode.isVarArg();

        for (final Symbol symbol : block.getSymbols()) {
            if (symbol.isInternal() || symbol.isThis() || symbol.isTemp()) {
                continue;
            }

            if (symbol.isVar()) {
                if (allVarsInScope || symbol.isScope()) {
                    updateSymbolsLog(functionNode, symbol, true);
                    Symbol.setSymbolIsScope(lc, symbol);
                    symbol.setNeedsSlot(false);
                } else {
                    assert symbol.hasSlot() : symbol + " should have a slot only, no scope";
                }
            } else if (symbol.isParam() && (allVarsInScope || isVarArg || symbol.isScope())) {
                updateSymbolsLog(functionNode, symbol, isVarArg);
                Symbol.setSymbolIsScope(lc, symbol);
                symbol.setNeedsSlot(!isVarArg);
            }
        }
    }

    private static Expression discard(final Expression expr) {
        if (expr.getSymbol() != null) {
            final UnaryNode discard = new UnaryNode(Token.recast(expr.getToken(), TokenType.DISCARD), expr);
            //discard never has a symbol in the discard node - then it would be a nop
            assert !expr.isTerminal();
            return discard;
        }

        // node has no result (symbol) so we can keep it the way it is
        return expr;
    }


}
