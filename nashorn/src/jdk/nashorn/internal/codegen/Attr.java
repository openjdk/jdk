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

import static jdk.nashorn.internal.codegen.CompilerConstants.ARGUMENTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.CALLEE;
import static jdk.nashorn.internal.codegen.CompilerConstants.EXCEPTION_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.ITERATOR_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SWITCH_TAG_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.ir.Symbol.IS_FUNCTION_SELF;
import static jdk.nashorn.internal.ir.Symbol.IS_GLOBAL;
import static jdk.nashorn.internal.ir.Symbol.IS_INTERNAL;
import static jdk.nashorn.internal.ir.Symbol.IS_LET;
import static jdk.nashorn.internal.ir.Symbol.IS_PARAM;
import static jdk.nashorn.internal.ir.Symbol.IS_SCOPE;
import static jdk.nashorn.internal.ir.Symbol.IS_THIS;
import static jdk.nashorn.internal.ir.Symbol.IS_VAR;
import static jdk.nashorn.internal.ir.Symbol.KINDMASK;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LexicalContextNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * This is the attribution pass of the code generator. Attr takes Lowered IR,
 * that is, IR where control flow has been computed and high level to low level
 * substitions for operations have been performed.
 *
 * After Attr, every symbol will have a conservative correct type.
 *
 * Any expression that requires temporary storage as part of computation will
 * also be detected here and give a temporary symbol
 *
 * Types can be narrowed after Attr by Access Specialization in FinalizeTypes,
 * but in general, this is where the main symbol type information is
 * computed.
 */

final class Attr extends NodeOperatorVisitor {

    /**
     * Local definitions in current block (to discriminate from function
     * declarations always defined in the function scope. This is for
     * "can be undefined" analysis.
     */
    private final Deque<Set<String>> localDefs;

    /**
     * Local definitions in current block to guard against cases like
     * NASHORN-467 when things can be undefined as they are used before
     * their local var definition. *sigh* JavaScript...
     */
    private final Deque<Set<String>> localUses;

    private final Deque<Type> returnTypes;

    private static final DebugLogger LOG   = new DebugLogger("attr");
    private static final boolean     DEBUG = LOG.isEnabled();

    /**
     * Constructor.
     */
    Attr() {
        localDefs = new ArrayDeque<>();
        localUses = new ArrayDeque<>();
        returnTypes = new ArrayDeque<>();
    }

    @Override
    protected boolean enterDefault(final Node node) {
        return start(node);
    }

    @Override
    protected Node leaveDefault(final Node node) {
        return end(node);
    }

    @Override
    public Node leaveAccessNode(final AccessNode accessNode) {
        ensureSymbol(Type.OBJECT, accessNode);  //While Object type is assigned here, Access Specialization in FinalizeTypes may narrow this
        end(accessNode);
        return accessNode;
    }

    private void enterFunctionBody() {

        final FunctionNode functionNode = getLexicalContext().getCurrentFunction();
        final Block body = getLexicalContext().getCurrentBlock();
        initCallee(body);
        initThis(body);
        if (functionNode.isVarArg()) {
            initVarArg(body, functionNode.needsArguments());
        }

        initParameters(functionNode, body);
        initScope(body);
        initReturn(body);

        if (functionNode.isProgram()) {
            initFromPropertyMap(body);
        } else if(!functionNode.isDeclared()) {
            // It's neither declared nor program - it's a function expression then; assign it a self-symbol.

            if (functionNode.getSymbol() != null) {
                // a temporary left over from an earlier pass when the function was lazy
                assert functionNode.getSymbol().isTemp();
                // remove it
                functionNode.setSymbol(null);
            }
            final boolean anonymous = functionNode.isAnonymous();
            final String name = anonymous ? null : functionNode.getIdent().getName();
            if (anonymous || body.getExistingSymbol(name) != null) {
                // The function is either anonymous, or another local identifier already trumps its name on entry:
                // either it has the same name as one of its parameters, or is named "arguments" and also references the
                // "arguments" identifier in its body.
                ensureSymbol(functionNode, Type.typeFor(ScriptFunction.class), functionNode);
            } else {
                final Symbol selfSymbol = defineSymbol(body, name, IS_VAR | IS_FUNCTION_SELF, functionNode);
                assert selfSymbol.isFunctionSelf();
                newType(selfSymbol, Type.OBJECT);
            }
        }

        /*
         * This pushes all declarations (except for non-statements, i.e. for
         * node temporaries) to the top of the function scope. This way we can
         * get around problems like
         *
         * while (true) {
         *   break;
         *   if (true) {
         *     var s;
         *   }
         * }
         *
         * to an arbitrary nesting depth.
         *
         * @see NASHORN-73
         */

        // This visitor will assign symbol to all declared variables, except function declarations (which are taken care
        // in a separate step above) and "var" declarations in for loop initializers.
        body.accept(new NodeOperatorVisitor() {
            @Override
            public boolean enterFunctionNode(final FunctionNode nestedFn) {
                return false;
            }

            @Override
            public boolean enterVarNode(final VarNode varNode) {

                // any declared symbols that aren't visited need to be typed as well, hence the list

                if (varNode.isStatement()) {

                    final IdentNode ident = varNode.getName();
                    final Symbol symbol = defineSymbol(body, ident.getName(), IS_VAR, new IdentNode(ident));
                    functionNode.addDeclaredSymbol(symbol);
                    if (varNode.isFunctionDeclaration()) {
                        newType(symbol, FunctionNode.FUNCTION_TYPE);
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean enterBlock(final Block block) {
        start(block);

        if (getLexicalContext().isFunctionBody()) {
            enterFunctionBody();
        }
        pushLocalsBlock();

        return true;
    }

    @Override
    public Node leaveBlock(final Block block) {
        popLocals();
        return end(block);
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        ensureSymbol(callNode.getType(), callNode);
        return end(callNode);
    }

    @Override
    public boolean enterCallNode(final CallNode callNode) {
        return start(callNode);
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        final IdentNode exception = catchNode.getException();
        final Block     block     = getLexicalContext().getCurrentBlock();

        start(catchNode);

        // define block-local exception variable
        final Symbol def = defineSymbol(block, exception.getName(), IS_VAR | IS_LET, exception);
        newType(def, Type.OBJECT);
        addLocalDef(exception.getName());

        return true;
    }

    /**
     * Declare the definition of a new symbol.
     *
     * @param name         Name of symbol.
     * @param symbolFlags  Symbol flags.
     * @param node         Defining Node.
     *
     * @return Symbol for given name or null for redefinition.
     */
    private Symbol defineSymbol(final Block block, final String name, final int symbolFlags, final Node node) {
        int    flags  = symbolFlags;
        Symbol symbol = findSymbol(block, name); // Locate symbol.

        if ((flags & KINDMASK) == IS_GLOBAL) {
            flags |= IS_SCOPE;
        }

        final FunctionNode function = getLexicalContext().getFunction(block);
        if (symbol != null) {
            // Symbol was already defined. Check if it needs to be redefined.
            if ((flags & KINDMASK) == IS_PARAM) {
                if (!isLocal(function, symbol)) {
                    // Not defined in this function. Create a new definition.
                    symbol = null;
                } else if (symbol.isParam()) {
                    // Duplicate parameter. Null return will force an error.
                    assert false : "duplicate parameter";
                    return null;
                }
            } else if ((flags & KINDMASK) == IS_VAR) {
                if ((flags & IS_INTERNAL) == IS_INTERNAL || (flags & IS_LET) == IS_LET) {
                    // Always create a new definition.
                    symbol = null;
                } else {
                    // Not defined in this function. Create a new definition.
                    if (!isLocal(function, symbol) || symbol.less(IS_VAR)) {
                        symbol = null;
                    }
                }
            }
        }

        if (symbol == null) {
            // If not found, then create a new one.
            Block symbolBlock;

            // Determine where to create it.
            if ((flags & Symbol.KINDMASK) == IS_VAR && ((flags & IS_INTERNAL) == IS_INTERNAL || (flags & IS_LET) == IS_LET)) {
                symbolBlock = block; //internal vars are always defined in the block closest to them
            } else {
                symbolBlock = getLexicalContext().getFunctionBody(function);
            }

            // Create and add to appropriate block.
            symbol = new Symbol(name, flags);
            symbolBlock.putSymbol(name, symbol);

            if ((flags & Symbol.KINDMASK) != IS_GLOBAL) {
                symbol.setNeedsSlot(true);
            }
        } else if (symbol.less(flags)) {
            symbol.setFlags(flags);
        }

        if (node != null) {
            node.setSymbol(symbol);
        }

        return symbol;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        start(functionNode, false);

        if (functionNode.isDeclared()) {
            final Iterator<Block> blocks = getLexicalContext().getBlocks();
            if (blocks.hasNext()) {
                defineSymbol(
                    blocks.next(),
                    functionNode.getIdent().getName(),
                    IS_VAR,
                    functionNode);
            } else {
                // Q: What's an outermost function in a lexical context that is not a program?
                // A: It's a function being compiled lazily!
                assert getLexicalContext().getOutermostFunction() == functionNode && !functionNode.isProgram();
            }
        }

        if (functionNode.isLazy()) {
            LOG.info("LAZY: ", functionNode.getName(), " => Promoting to OBJECT");
            ensureSymbol(getLexicalContext().getCurrentFunction(), Type.OBJECT, functionNode);
            end(functionNode);
            return false;
        }

        returnTypes.push(functionNode.getReturnType());
        pushLocalsFunction();
        return true;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        FunctionNode newFunctionNode = functionNode;

        final LexicalContext lc = getLexicalContext();

        //unknown parameters are promoted to object type.
        finalizeParameters(newFunctionNode);
        finalizeTypes(newFunctionNode);
        for (final Symbol symbol : newFunctionNode.getDeclaredSymbols()) {
            if (symbol.getSymbolType().isUnknown()) {
                symbol.setType(Type.OBJECT);
                symbol.setCanBeUndefined();
            }
        }

        final Block body = newFunctionNode.getBody();

        if (newFunctionNode.hasLazyChildren()) {
            //the final body has already been assigned as we have left the function node block body by now
            objectifySymbols(body);
        }

        if (body.getFlag(Block.NEEDS_SELF_SYMBOL)) {
            final IdentNode callee = compilerConstant(CALLEE);
            final VarNode selfInit =
                new VarNode(
                    newFunctionNode.getSource(),
                    newFunctionNode.getToken(),
                    newFunctionNode.getFinish(),
                    newFunctionNode.getIdent(),
                    callee);

            LOG.info("Accepting self symbol init ", selfInit, " for ", newFunctionNode.getName());

            final List<Node> newStatements = new ArrayList<>();
            newStatements.add(selfInit);
            assert callee.getSymbol() != null && callee.getSymbol().hasSlot();

            final IdentNode name       = selfInit.getName();
            final Symbol    nameSymbol = body.getExistingSymbol(name.getName());

            assert nameSymbol != null;

            name.setSymbol(nameSymbol);
            selfInit.setSymbol(nameSymbol);

            newStatements.addAll(body.getStatements());
            newFunctionNode = newFunctionNode.setBody(lc, body.setStatements(lc, newStatements));
        }

        if (returnTypes.peek().isUnknown()) {
            LOG.info("Unknown return type promoted to object");
            newFunctionNode = newFunctionNode.setReturnType(lc, Type.OBJECT);
        }
        final Type returnType = returnTypes.pop();
        newFunctionNode = newFunctionNode.setReturnType(lc, returnType.isUnknown() ? Type.OBJECT : returnType);
        newFunctionNode = newFunctionNode.setState(lc, CompilationState.ATTR);

        popLocals();

        end(newFunctionNode, false);

        return newFunctionNode; //.setFlag(lc, lc.getFlags(functionNode));
    }

    @Override
    public Node leaveCONVERT(final UnaryNode unaryNode) {
        assert false : "There should be no convert operators in IR during Attribution";
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public boolean enterIdentNode(final IdentNode identNode) {
        final String name = identNode.getName();

        start(identNode);

        if (identNode.isPropertyName()) {
            // assign a pseudo symbol to property name
            final Symbol pseudoSymbol = pseudoSymbol(name);
            LOG.info("IdentNode is property name -> assigning pseudo symbol ", pseudoSymbol);
            LOG.unindent();
            identNode.setSymbol(pseudoSymbol);
            return false;
        }

        final LexicalContext lc        = getLexicalContext();
        final Block          block     = lc.getCurrentBlock();
        final Symbol         oldSymbol = identNode.getSymbol();

        Symbol symbol = findSymbol(block, name);

        //If an existing symbol with the name is found, use that otherwise, declare a new one
        if (symbol != null) {
            LOG.info("Existing symbol = ", symbol);
            if (symbol.isFunctionSelf()) {
                final FunctionNode functionNode = lc.getDefiningFunction(symbol);
                assert functionNode != null;
                assert lc.getFunctionBody(functionNode).getExistingSymbol(CALLEE.symbolName()) != null;
                lc.setFlag(functionNode.getBody(), Block.NEEDS_SELF_SYMBOL);
                newType(symbol, FunctionNode.FUNCTION_TYPE);
            } else if (!identNode.isInitializedHere()) { // NASHORN-448
                // here is a use outside the local def scope
                if (!isLocalDef(name)) {
                    newType(symbol, Type.OBJECT);
                    symbol.setCanBeUndefined();
                }
            }

            identNode.setSymbol(symbol);
            // non-local: we need to put symbol in scope (if it isn't already)
            if (!isLocal(lc.getCurrentFunction(), symbol) && !symbol.isScope()) {
                Symbol.setSymbolIsScope(lc, symbol);
            }
        } else {
            LOG.info("No symbol exists. Declare undefined: ", symbol);
            symbol = defineSymbol(block, name, IS_GLOBAL, identNode);
            // we have never seen this before, it can be undefined
            newType(symbol, Type.OBJECT); // TODO unknown -we have explicit casts anyway?
            symbol.setCanBeUndefined();
            Symbol.setSymbolIsScope(lc, symbol);
        }

        setBlockScope(name, symbol);

        if (symbol != oldSymbol && !identNode.isInitializedHere()) {
            symbol.increaseUseCount();
        }
        addLocalUse(identNode.getName());

        end(identNode);

        return false;
    }

    private void setBlockScope(final String name, final Symbol symbol) {
        assert symbol != null;
        if (symbol.isGlobal()) {
            setUsesGlobalSymbol();
            return;
        }

        if (symbol.isScope()) {
            final LexicalContext lc = getLexicalContext();

            Block scopeBlock = null;
            for (final Iterator<LexicalContextNode> contextNodeIter = getLexicalContext().getAllNodes(); contextNodeIter.hasNext(); ) {
                final LexicalContextNode node = contextNodeIter.next();
                if (node instanceof Block) {
                    if (((Block)node).getExistingSymbol(name) != null) {
                        scopeBlock = (Block)node;
                        break;
                    }
                } else if (node instanceof FunctionNode) {
                    lc.setFlag(node, FunctionNode.USES_ANCESTOR_SCOPE);
                }
            }

            if (scopeBlock != null) {
                assert getLexicalContext().contains(scopeBlock);
                lc.setFlag(scopeBlock, Block.NEEDS_SCOPE);
            }
        }
    }

    /**
     * Marks the current function as one using any global symbol. The function and all its parent functions will all be
     * marked as needing parent scope.
     * @see #needsParentScope()
     */
    private void setUsesGlobalSymbol() {
        for (final Iterator<FunctionNode> fns = getLexicalContext().getFunctions(); fns.hasNext();) {
            getLexicalContext().setFlag(fns.next(), FunctionNode.USES_ANCESTOR_SCOPE);
        }
    }

    /**
     * Search for symbol in the lexical context starting from the given block.
     * @param name Symbol name.
     * @return Found symbol or null if not found.
     */
    private Symbol findSymbol(final Block block, final String name) {
        // Search up block chain to locate symbol.

        for(final Iterator<Block> blocks = getLexicalContext().getBlocks(block); blocks.hasNext();) {
            // Find name.
            final Symbol symbol = blocks.next().getExistingSymbol(name);
            // If found then we are good.
            if(symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    @Override
    public Node leaveIndexNode(final IndexNode indexNode) {
        ensureSymbol(Type.OBJECT, indexNode); //TODO
        return indexNode;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean enterLiteralNode(final LiteralNode literalNode) {
        try {
            start(literalNode);
            assert !literalNode.isTokenType(TokenType.THIS) : "tokentype for " + literalNode + " is this"; //guard against old dead code case. literal nodes should never inherit tokens

            if (literalNode instanceof ArrayLiteralNode) {
                final ArrayLiteralNode arrayLiteralNode = (ArrayLiteralNode)literalNode;
                final Node[]           array            = arrayLiteralNode.getValue();

                for (int i = 0; i < array.length; i++) {
                    final Node element = array[i];
                    if (element != null) {
                        array[i] = element.accept(this);
                    }
                }
                arrayLiteralNode.analyze();
                //array literal node now has an element type and all elements are attributed
            } else {
                assert !(literalNode.getValue() instanceof Node) : "literals with Node values not supported";
            }

            getLexicalContext().getCurrentFunction().newLiteral(literalNode);
        } finally {
            end(literalNode);
        }

        return false;
    }

    @Override
    public boolean enterObjectNode(final ObjectNode objectNode) {
        return start(objectNode);
    }

    @Override
    public Node leaveObjectNode(final ObjectNode objectNode) {
        ensureSymbol(Type.OBJECT, objectNode);
        return end(objectNode);
    }

    //TODO is this correct why not leave?
    @Override
    public boolean enterPropertyNode(final PropertyNode propertyNode) {
        // assign a pseudo symbol to property name, see NASHORN-710
        start(propertyNode);
        propertyNode.setSymbol(new Symbol(propertyNode.getKeyName(), 0, Type.OBJECT));
        end(propertyNode);
        return true;
    }

    @Override
    public Node leaveReturnNode(final ReturnNode returnNode) {
        final Node expr = returnNode.getExpression();

        if (expr != null) {
            //we can't do parameter specialization if we return something that hasn't been typed yet
            final Symbol symbol = expr.getSymbol();
            if (expr.getType().isUnknown() && symbol.isParam()) {
                symbol.setType(Type.OBJECT);
            }

            final Type returnType = Type.widest(returnTypes.pop(), symbol.getSymbolType());
            returnTypes.push(returnType);
            LOG.info("Returntype is now ", returnType);
        }

        end(returnNode);

        return returnNode;
    }

    @Override
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        Type type = Type.UNKNOWN;

        final List<CaseNode> newCases = new ArrayList<>();
        for (final CaseNode caseNode : switchNode.getCases()) {
            final Node test = caseNode.getTest();

            CaseNode newCaseNode = caseNode;
            if (test != null) {
                if (test instanceof LiteralNode) {
                    //go down to integers if we can
                    final LiteralNode<?> lit = (LiteralNode<?>)test;
                    if (lit.isNumeric() && !(lit.getValue() instanceof Integer)) {
                        if (JSType.isRepresentableAsInt(lit.getNumber())) {
                            newCaseNode = caseNode.setTest(LiteralNode.newInstance(lit, lit.getInt32()).accept(this));
                        }
                    }
                } else {
                    // the "all integer" case that CodeGenerator optimizes for currently assumes literals only
                    type = Type.OBJECT;
                }

                type = Type.widest(type, newCaseNode.getTest().getType());
            }

            newCases.add(newCaseNode);
        }

        //only optimize for all integers
        if (!type.isInteger()) {
            type = Type.OBJECT;
        }

        switchNode.setTag(newInternal(getLexicalContext().getCurrentFunction().uniqueName(SWITCH_TAG_PREFIX.symbolName()), type));

        end(switchNode);

        return switchNode.setCases(getLexicalContext(), newCases);
    }

    @Override
    public Node leaveTryNode(final TryNode tryNode) {
        tryNode.setException(exceptionSymbol());

        if (tryNode.getFinallyBody() != null) {
            tryNode.setFinallyCatchAll(exceptionSymbol());
        }

        end(tryNode);

        return tryNode;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        start(varNode);

        final IdentNode ident = varNode.getName();
        final String    name  = ident.getName();

        final Symbol symbol = defineSymbol(getLexicalContext().getCurrentBlock(), name, IS_VAR, ident);
        assert symbol != null;

        LOG.info("VarNode ", varNode, " set symbol ", symbol);
        varNode.setSymbol(symbol);

        // NASHORN-467 - use before definition of vars - conservative
        if (isLocalUse(ident.getName())) {
            newType(symbol, Type.OBJECT);
            symbol.setCanBeUndefined();
        }

        return true;
    }

    @Override
    public Node leaveVarNode(final VarNode varNode) {
        final Node      init  = varNode.getInit();
        final IdentNode ident = varNode.getName();
        final String    name  = ident.getName();

        if (init == null) {
            // var x; with no init will be treated like a use of x by
            // visit(IdentNode) unless we remove the name
            // from the localdef list.
            removeLocalDef(name);
            return varNode;
        }

        addLocalDef(name);

        final Symbol  symbol   = varNode.getSymbol();
        final boolean isScript = getLexicalContext().getDefiningFunction(symbol).isProgram(); //see NASHORN-56
        if ((init.getType().isNumeric() || init.getType().isBoolean()) && !isScript) {
            // Forbid integers as local vars for now as we have no way to treat them as undefined
            newType(symbol, init.getType());
        } else {
            newType(symbol, Type.OBJECT);
        }

        assert varNode.hasType() : varNode;

        end(varNode);

        return varNode;
    }

    @Override
    public Node leaveADD(final UnaryNode unaryNode) {
        ensureSymbol(arithType(), unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveBIT_NOT(final UnaryNode unaryNode) {
        ensureSymbol(Type.INT, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveDECINC(final UnaryNode unaryNode) {
        // @see assignOffset
        ensureAssignmentSlots(getLexicalContext().getCurrentFunction(), unaryNode.rhs());
        final Type type = arithType();
        newType(unaryNode.rhs().getSymbol(), type);
        ensureSymbol(type, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveDELETE(final UnaryNode unaryNode) {
        final FunctionNode   currentFunctionNode = getLexicalContext().getCurrentFunction();
        final boolean        strictMode          = currentFunctionNode.isStrict();
        final Node           rhs                 = unaryNode.rhs();
        final Node           strictFlagNode      = LiteralNode.newInstance(unaryNode, strictMode).accept(this);

        Request request = Request.DELETE;
        final List<Node> args = new ArrayList<>();

        if (rhs instanceof IdentNode) {
            // If this is a declared variable or a function parameter, delete always fails (except for globals).
            final String name = ((IdentNode)rhs).getName();

            final boolean failDelete = strictMode || rhs.getSymbol().isParam() || (rhs.getSymbol().isVar() && !isProgramLevelSymbol(name));

            if (failDelete && rhs.getSymbol().isThis()) {
                return LiteralNode.newInstance(unaryNode, true).accept(this);
            }
            final Node literalNode = LiteralNode.newInstance(unaryNode, name).accept(this);

            if (!failDelete) {
                args.add(compilerConstant(SCOPE));
            }
            args.add(literalNode);
            args.add(strictFlagNode);

            if (failDelete) {
                request = Request.FAIL_DELETE;
            }
        } else if (rhs instanceof AccessNode) {
            final Node      base     = ((AccessNode)rhs).getBase();
            final IdentNode property = ((AccessNode)rhs).getProperty();

            args.add(base);
            args.add(LiteralNode.newInstance(unaryNode, property.getName()).accept(this));
            args.add(strictFlagNode);

        } else if (rhs instanceof IndexNode) {
            final Node base  = ((IndexNode)rhs).getBase();
            final Node index = ((IndexNode)rhs).getIndex();

            args.add(base);
            args.add(index);
            args.add(strictFlagNode);

        } else {
            return LiteralNode.newInstance(unaryNode, true).accept(this);
        }

        final RuntimeNode runtimeNode = new RuntimeNode(unaryNode, request, args);
        assert runtimeNode.getSymbol() == unaryNode.getSymbol(); //unary parent constructor should do this

        return leaveRuntimeNode(runtimeNode);
    }

    /**
     * Is the symbol denoted by the specified name in the current lexical context defined in the program level
     * @param name the name of the symbol
     * @return true if the symbol denoted by the specified name in the current lexical context defined in the program level.
     */
    private boolean isProgramLevelSymbol(final String name) {
        for(final Iterator<Block> it = getLexicalContext().getBlocks(); it.hasNext();) {
            final Block next = it.next();
            if(next.getExistingSymbol(name) != null) {
                return next == getLexicalContext().getFunctionBody(getLexicalContext().getOutermostFunction());
            }
        }
        throw new AssertionError("Couldn't find symbol " + name + " in the context");
    }

    @Override
    public Node leaveNEW(final UnaryNode unaryNode) {
        ensureSymbol(Type.OBJECT, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveNOT(final UnaryNode unaryNode) {
        ensureSymbol(Type.BOOLEAN, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    private IdentNode compilerConstant(CompilerConstants cc) {
        final FunctionNode functionNode = getLexicalContext().getCurrentFunction();
        final IdentNode node = new IdentNode(functionNode.getSource(), functionNode.getToken(), functionNode.getFinish(), cc.symbolName());
        node.setSymbol(functionNode.compilerConstant(cc));
        return node;
    }

    @Override
    public Node leaveTYPEOF(final UnaryNode unaryNode) {
        final Node rhs = unaryNode.rhs();

        List<Node> args = new ArrayList<>();
        if (rhs instanceof IdentNode && !rhs.getSymbol().isParam() && !rhs.getSymbol().isVar()) {
            args.add(compilerConstant(SCOPE));
            args.add(LiteralNode.newInstance(rhs, ((IdentNode)rhs).getName()).accept(this)); //null
        } else {
            args.add(rhs);
            args.add(LiteralNode.newInstance(unaryNode).accept(this)); //null, do not reuse token of identifier rhs, it can be e.g. 'this'
        }

        RuntimeNode runtimeNode = new RuntimeNode(unaryNode, Request.TYPEOF, args);
        assert runtimeNode.getSymbol() == unaryNode.getSymbol();

        runtimeNode = (RuntimeNode)leaveRuntimeNode(runtimeNode);

        end(unaryNode);

        return runtimeNode;
    }

    @Override
    public Node leaveRuntimeNode(final RuntimeNode runtimeNode) {
        ensureSymbol(runtimeNode.getRequest().getReturnType(), runtimeNode);
        return runtimeNode;
    }

    @Override
    public Node leaveSUB(final UnaryNode unaryNode) {
        ensureSymbol(arithType(), unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveVOID(final UnaryNode unaryNode) {
        final RuntimeNode runtimeNode = (RuntimeNode)new RuntimeNode(unaryNode, Request.VOID).accept(this);
        assert runtimeNode.getSymbol().getSymbolType().isObject();
        end(unaryNode);
        return runtimeNode;
    }

    /**
     * Add is a special binary, as it works not only on arithmetic, but for
     * strings etc as well.
     */
    @Override
    public Node leaveADD(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        ensureTypeNotUnknown(lhs);
        ensureTypeNotUnknown(rhs);
        ensureSymbol(Type.widest(lhs.getType(), rhs.getType()), binaryNode);

        end(binaryNode);

        return binaryNode;
    }

    @Override
    public Node leaveAND(final BinaryNode binaryNode) {
        ensureSymbol(Type.OBJECT, binaryNode);
        end(binaryNode);
        return binaryNode;
    }

    /**
     * This is a helper called before an assignment.
     * @param binaryNode assignment node
     */
    private boolean enterAssignmentNode(final BinaryNode binaryNode) {
        start(binaryNode);

        final Node lhs = binaryNode.lhs();

        if (lhs instanceof IdentNode) {
            final LexicalContext lc    = getLexicalContext();
            final Block          block = lc.getCurrentBlock();
            final IdentNode      ident = (IdentNode)lhs;
            final String         name  = ident.getName();

            Symbol symbol = findSymbol(block, name);

            if (symbol == null) {
                symbol = defineSymbol(block, name, IS_GLOBAL, ident);
                binaryNode.setSymbol(symbol);
            } else if (!isLocal(lc.getCurrentFunction(), symbol)) {
                Symbol.setSymbolIsScope(lc, symbol);
            }

            addLocalDef(name);
        }

        return true;
    }

    private boolean isLocal(FunctionNode function, Symbol symbol) {
        final FunctionNode definingFn = getLexicalContext().getDefiningFunction(symbol);
        // Temp symbols are not assigned to a block, so their defining fn is null; those can be assumed local
        return definingFn == null || definingFn == function;
    }

    @Override
    public boolean enterASSIGN(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN(final BinaryNode binaryNode) {
        return leaveAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_ADD(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_ADD(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        final Type widest = Type.widest(lhs.getType(), rhs.getType());
        //Type.NUMBER if we can't prove that the add doesn't overflow. todo
        return leaveSelfModifyingAssignmentNode(binaryNode, widest.isNumeric() ? Type.NUMBER : Type.OBJECT);
    }

    @Override
    public boolean enterASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_DIV(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_DIV(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_MOD(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MOD(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_MUL(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MUL(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_SAR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SAR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_SHL(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHL(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_SHR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public boolean enterASSIGN_SUB(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SUB(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveBIT_AND(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.INT));
    }

    @Override
    public Node leaveBIT_OR(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.INT));
    }

    @Override
    public Node leaveBIT_XOR(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.INT));
    }

    @Override
    public Node leaveCOMMARIGHT(final BinaryNode binaryNode) {
        ensureSymbol(binaryNode.rhs().getType(), binaryNode);
        return binaryNode;
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        ensureSymbol(binaryNode.lhs().getType(), binaryNode);
        return binaryNode;
    }

    @Override
    public Node leaveDIV(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    private Node leaveCmp(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        ensureSymbol(Type.BOOLEAN, binaryNode);
        ensureTypeNotUnknown(lhs);
        ensureTypeNotUnknown(rhs);

        end(binaryNode);
        return binaryNode;
    }

    private Node coerce(final BinaryNode binaryNode, final Type operandType, final Type destType) {
        // TODO we currently don't support changing inferred type based on uses, only on
        // definitions. we would need some additional logic. We probably want to do that
        // in the future, if e.g. a specialized method gets parameter that is only used
        // as, say, an int : function(x) { return x & 4711 }, and x is not defined in
        // the function. to make this work, uncomment the following two type inferences
        // and debug.

        //newType(binaryNode.lhs().getSymbol(), operandType);
        //newType(binaryNode.rhs().getSymbol(), operandType);
        ensureSymbol(destType, binaryNode);
        return binaryNode;
    }

    private Node coerce(final BinaryNode binaryNode, final Type type) {
        return coerce(binaryNode, type, type);
    }

    //leave a binary node and inherit the widest type of lhs , rhs
    private Node leaveBinaryArithmetic(final BinaryNode binaryNode) {
        assert !Compiler.shouldUseIntegerArithmetic();
        return end(coerce(binaryNode, Type.NUMBER));
    }

    @Override
    public Node leaveEQ(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveEQ_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveGE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveGT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveIN(final BinaryNode binaryNode) {
        return leaveBinaryRuntimeOperator(binaryNode, Request.IN);
    }

    @Override
    public Node leaveINSTANCEOF(final BinaryNode binaryNode) {
        return leaveBinaryRuntimeOperator(binaryNode, Request.INSTANCEOF);
    }

    private Node leaveBinaryRuntimeOperator(final BinaryNode binaryNode, final Request request) {
        try {
            // Don't do a full RuntimeNode.accept, as we don't want to double-visit the binary node operands
            return leaveRuntimeNode(new RuntimeNode(binaryNode, request));
        } finally {
            end(binaryNode);
        }
    }

    @Override
    public Node leaveLE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveLT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveMOD(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveMUL(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveNE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveNE_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveOR(final BinaryNode binaryNode) {
        ensureSymbol(Type.OBJECT, binaryNode);
        end(binaryNode);
        return binaryNode;
    }

    @Override
    public Node leaveSAR(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.INT));
    }

    @Override
    public Node leaveSHL(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.INT));
    }

    @Override
    public Node leaveSHR(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.LONG));
    }

    @Override
    public Node leaveSUB(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        if (forNode.isForIn()) {
            forNode.setIterator(newInternal(getLexicalContext().getCurrentFunction().uniqueName(ITERATOR_PREFIX.symbolName()), Type.OBJECT)); //NASHORN-73
            /*
             * Iterators return objects, so we need to widen the scope of the
             * init variable if it, for example, has been assigned double type
             * see NASHORN-50
             */
            newType(forNode.getInit().getSymbol(), Type.OBJECT);
        }

        end(forNode);

        return forNode;
    }

    @Override
    public Node leaveTernaryNode(final TernaryNode ternaryNode) {
        final Node lhs  = ternaryNode.rhs();
        final Node rhs  = ternaryNode.third();

        ensureTypeNotUnknown(lhs);
        ensureTypeNotUnknown(rhs);

        final Type type = Type.widest(lhs.getType(), rhs.getType());
        ensureSymbol(type, ternaryNode);

        end(ternaryNode);
        assert ternaryNode.getSymbol() != null;

        return ternaryNode;
    }

    private void initThis(final Block block) {
        final Symbol thisSymbol = defineSymbol(block, THIS.symbolName(), IS_PARAM | IS_THIS, null);
        newType(thisSymbol, Type.OBJECT);
        thisSymbol.setNeedsSlot(true);
    }

    private void initScope(final Block block) {
        final Symbol scopeSymbol = defineSymbol(block, SCOPE.symbolName(), IS_VAR | IS_INTERNAL, null);
        newType(scopeSymbol, Type.typeFor(ScriptObject.class));
        scopeSymbol.setNeedsSlot(true);
    }

    private void initReturn(final Block block) {
        final Symbol returnSymbol = defineSymbol(block, RETURN.symbolName(), IS_VAR | IS_INTERNAL, null);
        newType(returnSymbol, Type.OBJECT);
        returnSymbol.setNeedsSlot(true);
        //return symbol is always object as it's the __return__ thing. What returnType is is another matter though
    }

    private void initVarArg(final Block block, final boolean needsArguments) {
        final Symbol varArgsSymbol = defineSymbol(block, VARARGS.symbolName(), IS_PARAM | IS_INTERNAL, null);
        varArgsSymbol.setTypeOverride(Type.OBJECT_ARRAY);
        varArgsSymbol.setNeedsSlot(true);

        if (needsArguments) {
            final Symbol    argumentsSymbol = defineSymbol(block, ARGUMENTS.symbolName(), IS_VAR | IS_INTERNAL, null);
            newType(argumentsSymbol, Type.typeFor(ScriptObject.class));
            argumentsSymbol.setNeedsSlot(true);
            addLocalDef(ARGUMENTS.symbolName());
        }
    }

    private void initCallee(final Block block) {
        final Symbol calleeSymbol = defineSymbol(block, CALLEE.symbolName(), IS_PARAM | IS_INTERNAL, null);
        newType(calleeSymbol, FunctionNode.FUNCTION_TYPE);
        calleeSymbol.setNeedsSlot(true);
    }

    /**
     * Initialize parameters for function node. This may require specializing
     * types if a specialization profile is known
     *
     * @param functionNode the function node
     */
    private void initParameters(final FunctionNode functionNode, final Block body) {
        for (final IdentNode param : functionNode.getParameters()) {
            addLocalDef(param.getName());
            final Symbol paramSymbol = defineSymbol(body, param.getName(), IS_PARAM, param);
            if (paramSymbol != null) {
                final Type callSiteParamType = functionNode.getSpecializedType(param);
                if (callSiteParamType != null) {
                    LOG.info("Param ", paramSymbol, " has a callsite type ", callSiteParamType, ". Using that.");
                }
                newType(paramSymbol, callSiteParamType == null ? Type.UNKNOWN : callSiteParamType);
            }

            LOG.info("Initialized param ", paramSymbol);
        }
    }

    /**
     * This has to run before fix assignment types, store any type specializations for
     * paramters, then turn then to objects for the generic version of this method
     *
     * @param functionNode functionNode
     */
    private static void finalizeParameters(final FunctionNode functionNode) {
        final boolean isVarArg = functionNode.isVarArg();

        for (final IdentNode ident : functionNode.getParameters()) {
            final Symbol paramSymbol = ident.getSymbol();

            assert paramSymbol != null;
            Type type = functionNode.getSpecializedType(ident);
            if (type == null) {
                type = Type.OBJECT;
            }

            // if we know that a parameter is only used as a certain type throughout
            // this function, we can tell the runtime system that no matter what the
            // call site is, use this information. TODO
            if (!paramSymbol.getSymbolType().isObject()) {
                LOG.finest("Parameter ", ident, " could profit from specialization to ", paramSymbol.getSymbolType());
            }

            newType(paramSymbol, Type.widest(type, paramSymbol.getSymbolType()));

            // parameters should not be slots for a function that uses variable arity signature
            if (isVarArg) {
                paramSymbol.setNeedsSlot(false);
            }
        }
    }

    /**
     * Move any properties from a global map into the scope of this method
     * @param block the function node body for which to init scope vars
     */
    private void initFromPropertyMap(final Block block) {
        // For a script, add scope symbols as defined in the property map

        final PropertyMap map = Context.getGlobalMap();

        for (final Property property : map.getProperties()) {
            final String key    = property.getKey();
            final Symbol symbol = defineSymbol(block, key, IS_GLOBAL, null);
            newType(symbol, Type.OBJECT);
            LOG.info("Added global symbol from property map ", symbol);
        }
    }

    private static void ensureTypeNotUnknown(final Node node) {

        final Symbol symbol = node.getSymbol();

        LOG.info("Ensure type not unknown for: ", symbol);

        /*
         * Note that not just unknowns, but params need to be blown
         * up to objects, because we can have something like
         *
         * function f(a) {
         *    var b = ~a; //b and a are inferred to be int
         *    return b;
         * }
         *
         * In this case, it would be correct to say that "if you have
         * an int at the callsite, just pass it".
         *
         * However
         *
         * function f(a) {
         *    var b = ~a;      //b and a are inferred to be int
         *    return b == 17;  //b is still inferred to be int.
         * }
         *
         * can be called with f("17") and if we assume that b is an
         * int and don't blow it up to an object in the comparison, we
         * are screwed. I hate JavaScript.
         *
         * This check has to be done for any operation that might take
         * objects as parameters, for example +, but not *, which is known
         * to coerce types into doubles
         */
        if (node.getType().isUnknown() || symbol.isParam()) {
            newType(symbol, Type.OBJECT);
            symbol.setCanBeUndefined();
         }
    }

    private static Symbol pseudoSymbol(final String name) {
        return new Symbol(name, 0, Type.OBJECT);
    }

    private Symbol exceptionSymbol() {
        return newInternal(getLexicalContext().getCurrentFunction().uniqueName(EXCEPTION_PREFIX.symbolName()), Type.typeFor(ECMAException.class));
    }

    /**
     * In an assignment, recursively make sure that there are slots for
     * everything that has to be laid out as temporary storage, which is the
     * case if we are assign-op:ing a BaseNode subclass. This has to be
     * recursive to handle things like multi dimensional arrays as lhs
     *
     * see NASHORN-258
     *
     * @param functionNode   the current function node (has to be passed as it changes in the visitor below)
     * @param assignmentDest the destination node of the assignment, e.g. lhs for binary nodes
     */
    private static void ensureAssignmentSlots(final FunctionNode functionNode, final Node assignmentDest) {
        assignmentDest.accept(new NodeVisitor() {
            @Override
            public Node leaveIndexNode(final IndexNode indexNode) {
                assert indexNode.getSymbol().isTemp();
                final Node index = indexNode.getIndex();
                //only temps can be set as needing slots. the others will self resolve
                //it is illegal to take a scope var and force it to be a slot, that breaks
                if (index.getSymbol().isTemp() && !index.getSymbol().isConstant()) {
                     index.getSymbol().setNeedsSlot(true);
                }
                return indexNode;
            }
        });
    }

    /**
     * Return the type that arithmetic ops should use. Until we have implemented better type
     * analysis (range based) or overflow checks that are fast enough for int arithmetic,
     * this is the number type
     * @return the arithetic type
     */
    private static Type arithType() {
        return Compiler.shouldUseIntegerArithmetic() ? Type.INT : Type.NUMBER;
    }

    /**
     * If types have changed, we can have failed to update vars. For example
     *
     * var x = 17; //x is int
     * x = "apa";  //x is object. This will be converted fine
     *
     * @param functionNode
     */
    private static void finalizeTypes(final FunctionNode functionNode) {
        final Set<Node> changed = new HashSet<>();
        do {
            changed.clear();
            functionNode.accept(new NodeVisitor() {

                private void widen(final Node node, final Type to) {
                    if (node instanceof LiteralNode) {
                        return;
                    }
                    Type from = node.getType();
                    if (!Type.areEquivalent(from, to) && Type.widest(from, to) == to) {
                        LOG.fine("Had to post pass widen '", node, "' " + Debug.id(node), " from ", node.getType(), " to ", to);
                        newType(node.getSymbol(), to);
                        changed.add(node);
                    }
                }

                @Override
                public boolean enterFunctionNode(final FunctionNode node) {
                    return !node.isLazy();
                }

                /**
                 * Eg.
                 *
                 * var d = 17;
                 * var e;
                 * e = d; //initially typed as int for node type, should retype as double
                 * e = object;
                 *
                 * var d = 17;
                 * var e;
                 * e -= d; //initially type number, should number remain with a final conversion supplied by Store. ugly, but the computation result of the sub is numeric
                 * e = object;
                 *
                 */
                @SuppressWarnings("fallthrough")
                @Override
                public Node leaveBinaryNode(final BinaryNode binaryNode) {
                    final Type widest = Type.widest(binaryNode.lhs().getType(), binaryNode.rhs().getType());
                    switch (binaryNode.tokenType()) {
                    default:
                        if (!binaryNode.isAssignment() || binaryNode.isSelfModifying()) {
                            break;
                        }
                        widen(binaryNode.lhs(), widest);
                    case ADD:
                        widen(binaryNode, widest);
                        break;
                    }
                    return binaryNode;
                }
            });
        } while (!changed.isEmpty());
    }

    /**
     * This assign helper is called after an assignment, when all children of
     * the assign has been processed. It fixes the types and recursively makes
     * sure that everyhing has slots that should have them in the chain.
     *
     * @param binaryNode assignment node
     */
    private Node leaveAssignmentNode(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        final Type type;
        if (rhs.getType().isNumeric()) {
            type = Type.widest(binaryNode.lhs().getType(), binaryNode.rhs().getType());
        } else {
            type = Type.OBJECT; //force lhs to be an object if not numeric assignment, e.g. strings too.
        }
        ensureSymbol(type, binaryNode);
        newType(lhs.getSymbol(), type);
        end(binaryNode);
        return binaryNode;
    }

    private Node leaveSelfModifyingAssignmentNode(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode, binaryNode.getWidestOperationType());
    }

    private Node leaveSelfModifyingAssignmentNode(final BinaryNode binaryNode, final Type destType) {
        //e.g. for -=, Number, no wider, destType (binaryNode.getWidestOperationType())  is the coerce type
        final Node lhs = binaryNode.lhs();

        newType(lhs.getSymbol(), destType); //may not narrow if dest is already wider than destType
        ensureSymbol(destType, binaryNode); //for OP= nodes, the node can carry a narrower types than its lhs rhs. This is perfectly fine

        ensureAssignmentSlots(getLexicalContext().getCurrentFunction(), binaryNode);

        end(binaryNode);
        return binaryNode;
    }

    private Symbol ensureSymbol(final FunctionNode functionNode, final Type type, final Node node) {
        LOG.info("New TEMPORARY added to ", functionNode.getName(), " type=", type);
        return functionNode.ensureSymbol(getLexicalContext().getCurrentBlock(), type, node);
    }

    private Symbol ensureSymbol(final Type type, final Node node) {
        return ensureSymbol(getLexicalContext().getCurrentFunction(), type, node);
    }

    private Symbol newInternal(final String name, final Type type) {
        final Symbol iter = defineSymbol(getLexicalContext().getCurrentBlock(), name, IS_VAR | IS_INTERNAL, null);
        iter.setType(type); // NASHORN-73
        return iter;
    }

    private static void newType(final Symbol symbol, final Type type) {
        final Type oldType = symbol.getSymbolType();
        symbol.setType(type);

        if (symbol.getSymbolType() != oldType) {
            LOG.info("New TYPE ", type, " for ", symbol," (was ", oldType, ")");
        }

        if (symbol.isParam()) {
            symbol.setType(type);
            LOG.info("Param type change ", symbol);
        }
    }

    private void pushLocalsFunction() {
        localDefs.push(new HashSet<String>());
        localUses.push(new HashSet<String>());
    }

    private void pushLocalsBlock() {
        localDefs.push(localDefs.isEmpty() ? new HashSet<String>() : new HashSet<>(localDefs.peek()));
        localUses.push(localUses.isEmpty() ? new HashSet<String>() : new HashSet<>(localUses.peek()));
    }

    private void popLocals() {
        localDefs.pop();
        localUses.pop();
    }

    private boolean isLocalDef(final String name) {
        return localDefs.peek().contains(name);
    }

    private void addLocalDef(final String name) {
        LOG.info("Adding local def of symbol: '", name, "'");
        localDefs.peek().add(name);
    }

    private void removeLocalDef(final String name) {
        LOG.info("Removing local def of symbol: '", name, "'");
        localDefs.peek().remove(name);
    }

    private boolean isLocalUse(final String name) {
        return localUses.peek().contains(name);
    }

    private void addLocalUse(final String name) {
        LOG.info("Adding local use of symbol: '", name, "'");
        localUses.peek().add(name);
    }

    /**
     * Pessimistically promote all symbols in current function node to Object types
     * This is done when the function contains unevaluated black boxes such as
     * lazy sub-function nodes that have not been compiled.
     *
     * @param body body for the function node we are leaving
     */
    private static void objectifySymbols(final Block body) {
        body.accept(new NodeVisitor() {
            private void toObject(final Block block) {
                for (final Iterator<Symbol> iter = block.symbolIterator(); iter.hasNext();) {
                    final Symbol symbol = iter.next();
                    if (!symbol.isTemp()) {
                        newType(symbol, Type.OBJECT);
                    }
                }
            }

            @Override
            public boolean enterBlock(final Block block) {
                toObject(block);
                return true;
            }

            @Override
            public boolean enterFunctionNode(final FunctionNode node) {
                return false;
            }
        });
    }

    private static String name(final Node node) {
        final String cn = node.getClass().getName();
        int lastDot = cn.lastIndexOf('.');
        if (lastDot == -1) {
            return cn;
        }
        return cn.substring(lastDot + 1);
    }

    private boolean start(final Node node) {
        return start(node, true);
    }

    private boolean start(final Node node, final boolean printNode) {
        if (DEBUG) {
            final StringBuilder sb = new StringBuilder();

            sb.append("[ENTER ").
                append(name(node)).
                append("] ").
                append(printNode ? node.toString() : "").
                append(" in '").
                append(getLexicalContext().getCurrentFunction().getName()).
                append("'");
            LOG.info(sb);
            LOG.indent();
        }

        return true;
    }

    private Node end(final Node node) {
        return end(node, true);
    }

    private Node end(final Node node, final boolean printNode) {
        if (DEBUG) {
            final StringBuilder sb = new StringBuilder();

            sb.append("[LEAVE ").
                append(name(node)).
                append("] ").
                append(printNode ? node.toString() : "").
                append(" in '").
                append(getLexicalContext().getCurrentFunction().getName());

            if (node.getSymbol() == null) {
                sb.append(" <NO SYMBOL>");
            } else {
                sb.append(" <symbol=").append(node.getSymbol()).append('>');
            }

            LOG.unindent();
            LOG.info(sb);
        }

        return node;
    }
}
