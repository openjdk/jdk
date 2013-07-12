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
import static jdk.nashorn.internal.codegen.CompilerConstants.LITERAL_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SWITCH_TAG_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.ir.Symbol.IS_ALWAYS_DEFINED;
import static jdk.nashorn.internal.ir.Symbol.IS_CONSTANT;
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
import jdk.nashorn.internal.ir.Expression;
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
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TemporarySymbols;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;

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

final class Attr extends NodeOperatorVisitor<LexicalContext> {

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

    private int catchNestingLevel;

    private static final DebugLogger LOG   = new DebugLogger("attr");
    private static final boolean     DEBUG = LOG.isEnabled();

    private final TemporarySymbols temporarySymbols;

    /**
     * Constructor.
     */
    Attr(final TemporarySymbols temporarySymbols) {
        super(new LexicalContext());
        this.temporarySymbols = temporarySymbols;
        this.localDefs   = new ArrayDeque<>();
        this.localUses   = new ArrayDeque<>();
        this.returnTypes = new ArrayDeque<>();
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
        //While Object type is assigned here, Access Specialization in FinalizeTypes may narrow this, that
        //is why we can't set the access node base to be an object here, that will ruin access specialization
        //for example for a.x | 17.
        return end(ensureSymbol(Type.OBJECT, accessNode));
    }

    private void initFunctionWideVariables(final FunctionNode functionNode, final Block body) {
        initCompileConstant(CALLEE, body, IS_PARAM | IS_INTERNAL);
        initCompileConstant(THIS, body, IS_PARAM | IS_THIS, Type.OBJECT);

        if (functionNode.isVarArg()) {
            initCompileConstant(VARARGS, body, IS_PARAM | IS_INTERNAL);
            if (functionNode.needsArguments()) {
                initCompileConstant(ARGUMENTS, body, IS_VAR | IS_INTERNAL | IS_ALWAYS_DEFINED);
                addLocalDef(ARGUMENTS.symbolName());
            }
        }

        initParameters(functionNode, body);
        initCompileConstant(SCOPE, body, IS_VAR | IS_INTERNAL | IS_ALWAYS_DEFINED);
        initCompileConstant(RETURN, body, IS_VAR | IS_INTERNAL | IS_ALWAYS_DEFINED, Type.OBJECT);
    }


    /**
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
     * see NASHORN-73
     *
     * @param functionNode the FunctionNode we are entering
     * @param body the body of the FunctionNode we are entering
     */
    private void acceptDeclarations(final FunctionNode functionNode, final Block body) {
        // This visitor will assign symbol to all declared variables, except function declarations (which are taken care
        // in a separate step above) and "var" declarations in for loop initializers.
        //
        // It also handles the case that a variable can be undefined, e.g
        // if (cond) {
        //    x = x.y;
        // }
        // var x = 17;
        //
        // by making sure that no identifier has been found earlier in the body than the
        // declaration - if such is the case the identifier is flagged as caBeUndefined to
        // be safe if it turns into a local var. Otherwise corrupt bytecode results

        body.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            private final Set<String> uses = new HashSet<>();
            private final Set<String> canBeUndefined = new HashSet<>();

            @Override
            public boolean enterFunctionNode(final FunctionNode nestedFn) {
                return false;
            }

            @Override
            public Node leaveIdentNode(final IdentNode identNode) {
                uses.add(identNode.getName());
                return identNode;
            }

            @Override
            public boolean enterVarNode(final VarNode varNode) {
                final String name = varNode.getName().getName();
                //if this is used before the var node, the var node symbol needs to be tagged as can be undefined
                if (uses.contains(name)) {
                    canBeUndefined.add(name);
                }

                // all uses of the declared varnode inside the var node are potentially undefined
                // however this is a bit conservative as e.g. var x = 17; var x = 1 + x; does work
                if (!varNode.isFunctionDeclaration() && varNode.getInit() != null) {
                    varNode.getInit().accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                       @Override
                       public boolean enterIdentNode(final IdentNode identNode) {
                           if (name.equals(identNode.getName())) {
                              canBeUndefined.add(name);
                           }
                           return false;
                       }
                    });
                }

                return true;
            }

            @Override
            public Node leaveVarNode(final VarNode varNode) {
                // any declared symbols that aren't visited need to be typed as well, hence the list
                if (varNode.isStatement()) {
                    final IdentNode ident  = varNode.getName();
                    final Symbol    symbol = defineSymbol(body, ident.getName(), IS_VAR);
                    if (canBeUndefined.contains(ident.getName())) {
                        symbol.setType(Type.OBJECT);
                        symbol.setCanBeUndefined();
                    }
                    functionNode.addDeclaredSymbol(symbol);
                    if (varNode.isFunctionDeclaration()) {
                        newType(symbol, FunctionNode.FUNCTION_TYPE);
                    }
                    return varNode.setName((IdentNode)ident.setSymbol(lc, symbol));
                }

                return varNode;
            }
        });
    }

    private void enterFunctionBody() {

        final FunctionNode functionNode = lc.getCurrentFunction();
        final Block body = lc.getCurrentBlock();

        initFunctionWideVariables(functionNode, body);

        if (functionNode.isProgram()) {
            initFromPropertyMap(body);
        } else if (!functionNode.isDeclared()) {
            // It's neither declared nor program - it's a function expression then; assign it a self-symbol.
            assert functionNode.getSymbol() == null;

            final boolean anonymous = functionNode.isAnonymous();
            final String  name      = anonymous ? null : functionNode.getIdent().getName();
            if (!(anonymous || body.getExistingSymbol(name) != null)) {
                assert !anonymous && name != null;
                newType(defineSymbol(body, name, IS_VAR | IS_FUNCTION_SELF), Type.OBJECT);
            }
        }

        acceptDeclarations(functionNode, body);
    }

    @Override
    public boolean enterBlock(final Block block) {
        start(block);
        //ensure that we don't use information from a previous compile. This is very ugly TODO
        //the symbols in the block should really be stateless
        block.clearSymbols();

        if (lc.isFunctionBody()) {
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
    public boolean enterCallNode(final CallNode callNode) {
        return start(callNode);
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        return end(ensureSymbol(callNode.getType(), callNode));
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        final IdentNode exception = catchNode.getException();
        final Block     block     = lc.getCurrentBlock();

        start(catchNode);
        catchNestingLevel++;

        // define block-local exception variable
        final String exname = exception.getName();
        final Symbol def = defineSymbol(block, exname, IS_VAR | IS_LET | IS_ALWAYS_DEFINED);
        newType(def, Type.OBJECT); //we can catch anything, not just ecma exceptions

        addLocalDef(exname);

        return true;
    }

    @Override
    public Node leaveCatchNode(final CatchNode catchNode) {
        final IdentNode exception = catchNode.getException();
        final Block  block        = lc.getCurrentBlock();
        final Symbol symbol       = findSymbol(block, exception.getName());

        catchNestingLevel--;

        assert symbol != null;
        return end(catchNode.setException((IdentNode)exception.setSymbol(lc, symbol)));
    }

    /**
     * Declare the definition of a new symbol.
     *
     * @param name         Name of symbol.
     * @param symbolFlags  Symbol flags.
     *
     * @return Symbol for given name or null for redefinition.
     */
    private Symbol defineSymbol(final Block block, final String name, final int symbolFlags) {
        int    flags  = symbolFlags;
        Symbol symbol = findSymbol(block, name); // Locate symbol.

        if ((flags & KINDMASK) == IS_GLOBAL) {
            flags |= IS_SCOPE;
        }

        final FunctionNode function = lc.getFunction(block);
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
                symbolBlock = lc.getFunctionBody(function);
            }

            // Create and add to appropriate block.
            symbol = new Symbol(name, flags);
            symbolBlock.putSymbol(lc, symbol);

            if ((flags & Symbol.KINDMASK) != IS_GLOBAL) {
                symbol.setNeedsSlot(true);
            }
        } else if (symbol.less(flags)) {
            symbol.setFlags(flags);
        }

        return symbol;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        start(functionNode, false);

        if (functionNode.isLazy()) {
            return false;
        }

        //an outermost function in our lexical context that is not a program (runScript)
        //is possible - it is a function being compiled lazily
        if (functionNode.isDeclared()) {
            final Iterator<Block> blocks = lc.getBlocks();
            if (blocks.hasNext()) {
                defineSymbol(blocks.next(), functionNode.getIdent().getName(), IS_VAR);
            }
        }

        returnTypes.push(functionNode.getReturnType());
        pushLocalsFunction();

        return true;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        FunctionNode newFunctionNode = functionNode;

        final Block body = newFunctionNode.getBody();

        //look for this function in the parent block
        if (functionNode.isDeclared()) {
            final Iterator<Block> blocks = lc.getBlocks();
            if (blocks.hasNext()) {
                newFunctionNode = (FunctionNode)newFunctionNode.setSymbol(lc, findSymbol(blocks.next(), functionNode.getIdent().getName()));
            }
        } else if (!functionNode.isProgram()) {
            final boolean anonymous = functionNode.isAnonymous();
            final String  name      = anonymous ? null : functionNode.getIdent().getName();
            if (anonymous || body.getExistingSymbol(name) != null) {
                newFunctionNode = (FunctionNode)ensureSymbol(FunctionNode.FUNCTION_TYPE, newFunctionNode);
            } else {
                assert name != null;
                final Symbol self = body.getExistingSymbol(name);
                assert self != null && self.isFunctionSelf();
                newFunctionNode = (FunctionNode)newFunctionNode.setSymbol(lc, body.getExistingSymbol(name));
            }
        }

        //unknown parameters are promoted to object type.
        newFunctionNode = finalizeParameters(newFunctionNode);
        newFunctionNode = finalizeTypes(newFunctionNode);
        for (final Symbol symbol : newFunctionNode.getDeclaredSymbols()) {
            if (symbol.getSymbolType().isUnknown()) {
                symbol.setType(Type.OBJECT);
                symbol.setCanBeUndefined();
            }
        }

        if (newFunctionNode.hasLazyChildren()) {
            //the final body has already been assigned as we have left the function node block body by now
            objectifySymbols(body);
        }

        if (body.getFlag(Block.NEEDS_SELF_SYMBOL)) {
            final IdentNode callee = compilerConstant(CALLEE);
            VarNode selfInit =
                new VarNode(
                    newFunctionNode.getLineNumber(),
                    newFunctionNode.getToken(),
                    newFunctionNode.getFinish(),
                    newFunctionNode.getIdent(),
                    callee);

            LOG.info("Accepting self symbol init ", selfInit, " for ", newFunctionNode.getName());

            final List<Statement> newStatements = new ArrayList<>();
            assert callee.getSymbol() != null && callee.getSymbol().hasSlot();

            final IdentNode name       = selfInit.getName();
            final Symbol    nameSymbol = body.getExistingSymbol(name.getName());

            assert nameSymbol != null;

            selfInit = selfInit.setName((IdentNode)name.setSymbol(lc, nameSymbol));

            newStatements.add(selfInit);
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

        return newFunctionNode;
    }

    @Override
    public Node leaveCONVERT(final UnaryNode unaryNode) {
        assert false : "There should be no convert operators in IR during Attribution";
        return end(unaryNode);
    }

    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        final String name = identNode.getName();

        start(identNode);

        if (identNode.isPropertyName()) {
            // assign a pseudo symbol to property name
            final Symbol pseudoSymbol = pseudoSymbol(name);
            LOG.info("IdentNode is property name -> assigning pseudo symbol ", pseudoSymbol);
            LOG.unindent();
            return end(identNode.setSymbol(lc, pseudoSymbol));
        }

        final Block block = lc.getCurrentBlock();

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
            } else if (!identNode.isInitializedHere()) {
                /*
                 * See NASHORN-448, JDK-8016235
                 *
                 * Here is a use outside the local def scope
                 * the inCatch check is a conservative approach to handle things that might have only been
                 * defined in the try block, but with variable declarations, which due to JavaScript rules
                 * have to be lifted up into the function scope outside the try block anyway, but as the
                 * flow can fault at almost any place in the try block and get us to the catch block, all we
                 * know is that we have a declaration, not a definition. This can be made better and less
                 * conservative once we superimpose a CFG onto the AST.
                 */
                if (!isLocalDef(name) || inCatch()) {
                    newType(symbol, Type.OBJECT);
                    symbol.setCanBeUndefined();
                }
            }

            // if symbol is non-local or we're in a with block, we need to put symbol in scope (if it isn't already)
            maybeForceScope(symbol);
        } else {
            LOG.info("No symbol exists. Declare undefined: ", symbol);
            symbol = defineSymbol(block, name, IS_GLOBAL);
            // we have never seen this before, it can be undefined
            newType(symbol, Type.OBJECT); // TODO unknown -we have explicit casts anyway?
            symbol.setCanBeUndefined();
            Symbol.setSymbolIsScope(lc, symbol);
        }

        setBlockScope(name, symbol);

        if (!identNode.isInitializedHere()) {
            symbol.increaseUseCount();
        }
        addLocalUse(identNode.getName());

        return end(identNode.setSymbol(lc, symbol));
    }

    private boolean inCatch() {
        return catchNestingLevel > 0;
    }

    /**
     * If the symbol isn't already a scope symbol, and it is either not local to the current function, or it is being
     * referenced from within a with block, we force it to be a scope symbol.
     * @param symbol the symbol that might be scoped
     */
    private void maybeForceScope(final Symbol symbol) {
        if (!symbol.isScope() && symbolNeedsToBeScope(symbol)) {
            Symbol.setSymbolIsScope(lc, symbol);
        }
    }

    private boolean symbolNeedsToBeScope(Symbol symbol) {
        if (symbol.isThis() || symbol.isInternal()) {
            return false;
        }
        boolean previousWasBlock = false;
        for (final Iterator<LexicalContextNode> it = lc.getAllNodes(); it.hasNext();) {
            final LexicalContextNode node = it.next();
            if (node instanceof FunctionNode) {
                // We reached the function boundary without seeing a definition for the symbol - it needs to be in
                // scope.
                return true;
            } else if (node instanceof WithNode) {
                if (previousWasBlock) {
                    // We reached a WithNode; the symbol must be scoped. Note that if the WithNode was not immediately
                    // preceded by a block, this means we're currently processing its expression, not its body,
                    // therefore it doesn't count.
                    return true;
                }
                previousWasBlock = false;
            } else if (node instanceof Block) {
                if (((Block)node).getExistingSymbol(symbol.getName()) == symbol) {
                    // We reached the block that defines the symbol without reaching either the function boundary, or a
                    // WithNode. The symbol need not be scoped.
                    return false;
                }
                previousWasBlock = true;
            } else {
                previousWasBlock = false;
            }
        }
        throw new AssertionError();
    }

    private void setBlockScope(final String name, final Symbol symbol) {
        assert symbol != null;
        if (symbol.isGlobal()) {
            setUsesGlobalSymbol();
            return;
        }

        if (symbol.isScope()) {
            Block scopeBlock = null;
            for (final Iterator<LexicalContextNode> contextNodeIter = lc.getAllNodes(); contextNodeIter.hasNext(); ) {
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
                assert lc.contains(scopeBlock);
                lc.setBlockNeedsScope(scopeBlock);
            }
        }
    }

    /**
     * Marks the current function as one using any global symbol. The function and all its parent functions will all be
     * marked as needing parent scope.
     * @see #needsParentScope()
     */
    private void setUsesGlobalSymbol() {
        for (final Iterator<FunctionNode> fns = lc.getFunctions(); fns.hasNext();) {
            lc.setFlag(fns.next(), FunctionNode.USES_ANCESTOR_SCOPE);
        }
    }

    /**
     * Search for symbol in the lexical context starting from the given block.
     * @param name Symbol name.
     * @return Found symbol or null if not found.
     */
    private Symbol findSymbol(final Block block, final String name) {
        // Search up block chain to locate symbol.

        for (final Iterator<Block> blocks = lc.getBlocks(block); blocks.hasNext();) {
            // Find name.
            final Symbol symbol = blocks.next().getExistingSymbol(name);
            // If found then we are good.
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    @Override
    public Node leaveIndexNode(final IndexNode indexNode) {
        return end(ensureSymbol(Type.OBJECT, indexNode));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Node leaveLiteralNode(final LiteralNode literalNode) {
        assert !literalNode.isTokenType(TokenType.THIS) : "tokentype for " + literalNode + " is this"; //guard against old dead code case. literal nodes should never inherit tokens
        assert literalNode instanceof ArrayLiteralNode || !(literalNode.getValue() instanceof Node) : "literals with Node values not supported";
        final Symbol symbol = new Symbol(lc.getCurrentFunction().uniqueName(LITERAL_PREFIX.symbolName()), IS_CONSTANT, literalNode.getType());
        if (literalNode instanceof ArrayLiteralNode) {
            ((ArrayLiteralNode)literalNode).analyze();
        }
        return end(literalNode.setSymbol(lc, symbol));
    }

    @Override
    public boolean enterObjectNode(final ObjectNode objectNode) {
        return start(objectNode);
    }

    @Override
    public Node leaveObjectNode(final ObjectNode objectNode) {
        return end(ensureSymbol(Type.OBJECT, objectNode));
    }

    @Override
    public Node leaveReturnNode(final ReturnNode returnNode) {
        final Expression expr = returnNode.getExpression();
        final Type returnType;

        if (expr != null) {
            //we can't do parameter specialization if we return something that hasn't been typed yet
            final Symbol symbol = expr.getSymbol();
            if (expr.getType().isUnknown() && symbol.isParam()) {
                symbol.setType(Type.OBJECT);
            }

            returnType = Type.widest(returnTypes.pop(), symbol.getSymbolType());
        } else {
            returnType = Type.OBJECT; //undefined
        }
        LOG.info("Returntype is now ", returnType);
        returnTypes.push(returnType);

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
                            newCaseNode = caseNode.setTest((Expression)LiteralNode.newInstance(lit, lit.getInt32()).accept(this));
                        }
                    }
                } else {
                    // the "all integer" case that CodeGenerator optimizes for currently assumes literals only
                    type = Type.OBJECT;
                }

                type = Type.widest(type, newCaseNode.getTest().getType());
                if (type.isBoolean()) {
                    type = Type.OBJECT; //booleans and integers aren't assignment compatible
                }
            }

            newCases.add(newCaseNode);
        }

        //only optimize for all integers
        if (!type.isInteger()) {
            type = Type.OBJECT;
        }

        switchNode.setTag(newInternal(lc.getCurrentFunction().uniqueName(SWITCH_TAG_PREFIX.symbolName()), type));

        end(switchNode);

        return switchNode.setCases(lc, newCases);
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

        final Symbol symbol = defineSymbol(lc.getCurrentBlock(), name, IS_VAR);
        assert symbol != null;

        // NASHORN-467 - use before definition of vars - conservative
        if (isLocalUse(ident.getName())) {
            newType(symbol, Type.OBJECT);
            symbol.setCanBeUndefined();
        }

        return true;
    }

    @Override
    public Node leaveVarNode(final VarNode varNode) {
        final Expression init  = varNode.getInit();
        final IdentNode  ident = varNode.getName();
        final String     name  = ident.getName();

        final Symbol  symbol = findSymbol(lc.getCurrentBlock(), name);
        assert ident.getSymbol() == symbol;

        if (init == null) {
            // var x; with no init will be treated like a use of x by
            // leaveIdentNode unless we remove the name from the localdef list.
            removeLocalDef(name);
            return end(varNode);
        }

        addLocalDef(name);

        assert symbol != null;

        final IdentNode newIdent = (IdentNode)ident.setSymbol(lc, symbol);

        final VarNode newVarNode = varNode.setName(newIdent);

        final boolean isScript = lc.getDefiningFunction(symbol).isProgram(); //see NASHORN-56
        if ((init.getType().isNumeric() || init.getType().isBoolean()) && !isScript) {
            // Forbid integers as local vars for now as we have no way to treat them as undefined
            newType(symbol, init.getType());
        } else {
            newType(symbol, Type.OBJECT);
        }

        assert newVarNode.getName().hasType() : newVarNode + " has no type";

        return end(newVarNode);
    }

    @Override
    public Node leaveADD(final UnaryNode unaryNode) {
        return end(ensureSymbol(arithType(), unaryNode));
    }

    @Override
    public Node leaveBIT_NOT(final UnaryNode unaryNode) {
        return end(ensureSymbol(Type.INT, unaryNode));
    }

    @Override
    public Node leaveDECINC(final UnaryNode unaryNode) {
        // @see assignOffset
        final UnaryNode newUnaryNode = unaryNode.setRHS(ensureAssignmentSlots(unaryNode.rhs()));
        final Type type = arithType();
        newType(newUnaryNode.rhs().getSymbol(), type);
        return end(ensureSymbol(type, newUnaryNode));
    }

    @Override
    public Node leaveDELETE(final UnaryNode unaryNode) {
        final FunctionNode   currentFunctionNode = lc.getCurrentFunction();
        final boolean        strictMode          = currentFunctionNode.isStrict();
        final Expression     rhs                 = unaryNode.rhs();
        final Expression     strictFlagNode      = (Expression)LiteralNode.newInstance(unaryNode, strictMode).accept(this);

        Request request = Request.DELETE;
        final List<Expression> args = new ArrayList<>();

        if (rhs instanceof IdentNode) {
            // If this is a declared variable or a function parameter, delete always fails (except for globals).
            final String name = ((IdentNode)rhs).getName();

            final boolean failDelete = strictMode || rhs.getSymbol().isParam() || (rhs.getSymbol().isVar() && !isProgramLevelSymbol(name));

            if (failDelete && rhs.getSymbol().isThis()) {
                return LiteralNode.newInstance(unaryNode, true).accept(this);
            }
            final Expression literalNode = (Expression)LiteralNode.newInstance(unaryNode, name).accept(this);

            if (!failDelete) {
                args.add(compilerConstant(SCOPE));
            }
            args.add(literalNode);
            args.add(strictFlagNode);

            if (failDelete) {
                request = Request.FAIL_DELETE;
            }
        } else if (rhs instanceof AccessNode) {
            final Expression base     = ((AccessNode)rhs).getBase();
            final IdentNode  property = ((AccessNode)rhs).getProperty();

            args.add(base);
            args.add((Expression)LiteralNode.newInstance(unaryNode, property.getName()).accept(this));
            args.add(strictFlagNode);

        } else if (rhs instanceof IndexNode) {
            final IndexNode indexNode = (IndexNode)rhs;
            final Expression base  = indexNode.getBase();
            final Expression index = indexNode.getIndex();

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
        for(final Iterator<Block> it = lc.getBlocks(); it.hasNext();) {
            final Block next = it.next();
            if(next.getExistingSymbol(name) != null) {
                return next == lc.getFunctionBody(lc.getOutermostFunction());
            }
        }
        throw new AssertionError("Couldn't find symbol " + name + " in the context");
    }

    @Override
    public Node leaveNEW(final UnaryNode unaryNode) {
        return end(ensureSymbol(Type.OBJECT, unaryNode));
    }

    @Override
    public Node leaveNOT(final UnaryNode unaryNode) {
        return end(ensureSymbol(Type.BOOLEAN, unaryNode));
    }

    private IdentNode compilerConstant(CompilerConstants cc) {
        final FunctionNode functionNode = lc.getCurrentFunction();
        return (IdentNode)
            new IdentNode(
                functionNode.getToken(),
                functionNode.getFinish(),
                cc.symbolName()).
                setSymbol(
                    lc,
                    functionNode.compilerConstant(cc));
    }

    @Override
    public Node leaveTYPEOF(final UnaryNode unaryNode) {
        final Expression rhs = unaryNode.rhs();

        List<Expression> args = new ArrayList<>();
        if (rhs instanceof IdentNode && !rhs.getSymbol().isParam() && !rhs.getSymbol().isVar()) {
            args.add(compilerConstant(SCOPE));
            args.add((Expression)LiteralNode.newInstance(rhs, ((IdentNode)rhs).getName()).accept(this)); //null
        } else {
            args.add(rhs);
            args.add((Expression)LiteralNode.newInstance(unaryNode).accept(this)); //null, do not reuse token of identifier rhs, it can be e.g. 'this'
        }

        RuntimeNode runtimeNode = new RuntimeNode(unaryNode, Request.TYPEOF, args);
        assert runtimeNode.getSymbol() == unaryNode.getSymbol();

        runtimeNode = (RuntimeNode)leaveRuntimeNode(runtimeNode);

        end(unaryNode);

        return runtimeNode;
    }

    @Override
    public Node leaveRuntimeNode(final RuntimeNode runtimeNode) {
        return end(ensureSymbol(runtimeNode.getRequest().getReturnType(), runtimeNode));
    }

    @Override
    public Node leaveSUB(final UnaryNode unaryNode) {
        return end(ensureSymbol(arithType(), unaryNode));
    }

    @Override
    public Node leaveVOID(final UnaryNode unaryNode) {
        return end(ensureSymbol(Type.OBJECT, unaryNode));
    }

    /**
     * Add is a special binary, as it works not only on arithmetic, but for
     * strings etc as well.
     */
    @Override
    public Node leaveADD(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        ensureTypeNotUnknown(lhs);
        ensureTypeNotUnknown(rhs);
        //even if we are adding two known types, this can overflow. i.e.
        //int and number -> number.
        //int and int are also number though.
        //something and object is object
        return end(ensureSymbol(Type.widest(arithType(), Type.widest(lhs.getType(), rhs.getType())), binaryNode));
    }

    @Override
    public Node leaveAND(final BinaryNode binaryNode) {
        return end(ensureSymbol(Type.OBJECT, binaryNode));
    }

    /**
     * This is a helper called before an assignment.
     * @param binaryNode assignment node
     */
    private boolean enterAssignmentNode(final BinaryNode binaryNode) {
        start(binaryNode);

        final Node lhs = binaryNode.lhs();

        if (lhs instanceof IdentNode) {
            final Block     block = lc.getCurrentBlock();
            final IdentNode ident = (IdentNode)lhs;
            final String    name  = ident.getName();

            Symbol symbol = findSymbol(block, name);

            if (symbol == null) {
                symbol = defineSymbol(block, name, IS_GLOBAL);
            } else {
                maybeForceScope(symbol);
            }

            addLocalDef(name);
        }

        return true;
    }


    /**
     * This assign helper is called after an assignment, when all children of
     * the assign has been processed. It fixes the types and recursively makes
     * sure that everyhing has slots that should have them in the chain.
     *
     * @param binaryNode assignment node
     */
    private Node leaveAssignmentNode(final BinaryNode binaryNode) {
        BinaryNode newBinaryNode = binaryNode;

        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();
        final Type type;

        if (rhs.getType().isNumeric()) {
            type = Type.widest(binaryNode.lhs().getType(), binaryNode.rhs().getType());
        } else {
            type = Type.OBJECT; //force lhs to be an object if not numeric assignment, e.g. strings too.
        }

        newType(lhs.getSymbol(), type);
        return end(ensureSymbol(type, newBinaryNode));
    }

    private boolean isLocal(FunctionNode function, Symbol symbol) {
        final FunctionNode definingFn = lc.getDefiningFunction(symbol);
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
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

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
        return end(ensureSymbol(binaryNode.rhs().getType(), binaryNode));
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        return end(ensureSymbol(binaryNode.lhs().getType(), binaryNode));
    }

    @Override
    public Node leaveDIV(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    private Node leaveCmp(final BinaryNode binaryNode) {
        ensureTypeNotUnknown(binaryNode.lhs());
        ensureTypeNotUnknown(binaryNode.rhs());

        return end(ensureSymbol(Type.BOOLEAN, binaryNode));
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
        return ensureSymbol(destType, binaryNode);
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
        return end(ensureSymbol(Type.OBJECT, binaryNode));
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
            forNode.setIterator(newInternal(lc.getCurrentFunction().uniqueName(ITERATOR_PREFIX.symbolName()), Type.typeFor(ITERATOR_PREFIX.type()))); //NASHORN-73
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
        final Expression trueExpr  = ternaryNode.getTrueExpression();
        final Expression falseExpr = ternaryNode.getFalseExpression();

        ensureTypeNotUnknown(trueExpr);
        ensureTypeNotUnknown(falseExpr);

        final Type type = Type.widest(trueExpr.getType(), falseExpr.getType());
        return end(ensureSymbol(type, ternaryNode));
    }

    private void initCompileConstant(final CompilerConstants cc, final Block block, final int flags) {
        final Class<?> type = cc.type();
        // Must not call this method for constants with no explicit types; use the one with (..., Type) signature instead.
        assert type != null;
        initCompileConstant(cc, block, flags, Type.typeFor(type));
    }

    private void initCompileConstant(final CompilerConstants cc, final Block block, final int flags, final Type type) {
        final Symbol symbol = defineSymbol(block, cc.symbolName(), flags);
        symbol.setTypeOverride(type);
        symbol.setNeedsSlot(true);
    }

    /**
     * Initialize parameters for function node. This may require specializing
     * types if a specialization profile is known
     *
     * @param functionNode the function node
     */
    private void initParameters(final FunctionNode functionNode, final Block body) {
        int pos = 0;
        for (final IdentNode param : functionNode.getParameters()) {
            addLocalDef(param.getName());

            final Type callSiteParamType = functionNode.getHints().getParameterType(pos);
            int flags = IS_PARAM;
            if (callSiteParamType != null) {
                LOG.info("Param ", param, " has a callsite type ", callSiteParamType, ". Using that.");
                flags |= Symbol.IS_SPECIALIZED_PARAM;
            }

            final Symbol paramSymbol = defineSymbol(body, param.getName(), flags);
            assert paramSymbol != null;

            newType(paramSymbol, callSiteParamType == null ? Type.UNKNOWN : callSiteParamType);

            LOG.info("Initialized param ", pos, "=", paramSymbol);
            pos++;
        }

    }

    /**
     * This has to run before fix assignment types, store any type specializations for
     * paramters, then turn then to objects for the generic version of this method
     *
     * @param functionNode functionNode
     */
    private FunctionNode finalizeParameters(final FunctionNode functionNode) {
        final List<IdentNode> newParams = new ArrayList<>();
        final boolean isVarArg = functionNode.isVarArg();
        final int nparams = functionNode.getParameters().size();

        int specialize = 0;
        int pos = 0;
        for (final IdentNode param : functionNode.getParameters()) {
            final Symbol paramSymbol = functionNode.getBody().getExistingSymbol(param.getName());
            assert paramSymbol != null;
            assert paramSymbol.isParam();
            newParams.add((IdentNode)param.setSymbol(lc, paramSymbol));

            assert paramSymbol != null;
            Type type = functionNode.getHints().getParameterType(pos);
            if (type == null) {
                type = Type.OBJECT;
            }

            // if we know that a parameter is only used as a certain type throughout
            // this function, we can tell the runtime system that no matter what the
            // call site is, use this information:
            // we also need more than half of the parameters to be specializable
            // for the heuristic to be worth it, and we need more than one use of
            // the parameter to consider it, i.e. function(x) { call(x); } doens't count
            if (paramSymbol.getUseCount() > 1 && !paramSymbol.getSymbolType().isObject()) {
                LOG.finest("Parameter ", param, " could profit from specialization to ", paramSymbol.getSymbolType());
                specialize++;
            }

            newType(paramSymbol, Type.widest(type, paramSymbol.getSymbolType()));

            // parameters should not be slots for a function that uses variable arity signature
            if (isVarArg) {
                paramSymbol.setNeedsSlot(false);
            }

            pos++;
        }

        FunctionNode newFunctionNode = functionNode;

        if (nparams == 0 || (specialize * 2) < nparams) {
            newFunctionNode = newFunctionNode.clearSnapshot(lc);
        }

        return newFunctionNode.setParameters(lc, newParams);
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
            final Symbol symbol = defineSymbol(block, key, IS_GLOBAL);
            newType(symbol, Type.OBJECT);
            LOG.info("Added global symbol from property map ", symbol);
        }
    }

    private static void ensureTypeNotUnknown(final Expression node) {

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
        if (node.getType().isUnknown() || (symbol.isParam() && !symbol.isSpecializedParam())) {
            newType(symbol, Type.OBJECT);
            symbol.setCanBeUndefined();
         }
    }

    private static Symbol pseudoSymbol(final String name) {
        return new Symbol(name, 0, Type.OBJECT);
    }

    private Symbol exceptionSymbol() {
        return newInternal(lc.getCurrentFunction().uniqueName(EXCEPTION_PREFIX.symbolName()), Type.typeFor(EXCEPTION_PREFIX.type()));
    }

    /**
     * In an assignment, recursively make sure that there are slots for
     * everything that has to be laid out as temporary storage, which is the
     * case if we are assign-op:ing a BaseNode subclass. This has to be
     * recursive to handle things like multi dimensional arrays as lhs
     *
     * see NASHORN-258
     *
     * @param assignmentDest the destination node of the assignment, e.g. lhs for binary nodes
     */
    private Expression ensureAssignmentSlots(final Expression assignmentDest) {
        final LexicalContext attrLexicalContext = lc;
        return (Expression)assignmentDest.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public Node leaveIndexNode(final IndexNode indexNode) {
                assert indexNode.getSymbol().isTemp();
                final Expression index = indexNode.getIndex();
                //only temps can be set as needing slots. the others will self resolve
                //it is illegal to take a scope var and force it to be a slot, that breaks
                Symbol indexSymbol = index.getSymbol();
                if (indexSymbol.isTemp() && !indexSymbol.isConstant() && !indexSymbol.hasSlot()) {
                    if(indexSymbol.isShared()) {
                        indexSymbol = temporarySymbols.createUnshared(indexSymbol);
                    }
                    indexSymbol.setNeedsSlot(true);
                    attrLexicalContext.getCurrentBlock().putSymbol(attrLexicalContext, indexSymbol);
                    return indexNode.setIndex(index.setSymbol(attrLexicalContext, indexSymbol));
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
    private FunctionNode finalizeTypes(final FunctionNode functionNode) {
        final Set<Node> changed = new HashSet<>();
        FunctionNode currentFunctionNode = functionNode;
        do {
            changed.clear();
            final FunctionNode newFunctionNode = (FunctionNode)currentFunctionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {

                private Expression widen(final Expression node, final Type to) {
                    if (node instanceof LiteralNode) {
                        return node;
                    }
                    Type from = node.getType();
                    if (!Type.areEquivalent(from, to) && Type.widest(from, to) == to) {
                        LOG.fine("Had to post pass widen '", node, "' ", Debug.id(node), " from ", node.getType(), " to ", to);
                        Symbol symbol = node.getSymbol();
                        if(symbol.isShared() && symbol.wouldChangeType(to)) {
                            symbol = temporarySymbols.getTypedTemporarySymbol(to);
                        }
                        newType(symbol, to);
                        final Expression newNode = node.setSymbol(lc, symbol);
                        changed.add(newNode);
                        return newNode;
                    }
                    return node;
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
                    BinaryNode newBinaryNode = binaryNode;
                    switch (binaryNode.tokenType()) {
                    default:
                        if (!binaryNode.isAssignment() || binaryNode.isSelfModifying()) {
                            break;
                        }
                        newBinaryNode = newBinaryNode.setLHS(widen(newBinaryNode.lhs(), widest));
                    case ADD:
                        newBinaryNode = (BinaryNode)widen(newBinaryNode, widest);
                    }
                    return newBinaryNode;
                }
            });
            lc.replace(currentFunctionNode, newFunctionNode);
            currentFunctionNode = newFunctionNode;
        } while (!changed.isEmpty());
        return currentFunctionNode;
    }

    private Node leaveSelfModifyingAssignmentNode(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode, binaryNode.getWidestOperationType());
    }

    private Node leaveSelfModifyingAssignmentNode(final BinaryNode binaryNode, final Type destType) {
        //e.g. for -=, Number, no wider, destType (binaryNode.getWidestOperationType())  is the coerce type
        final Expression lhs = binaryNode.lhs();

        newType(lhs.getSymbol(), destType); //may not narrow if dest is already wider than destType
//        ensureSymbol(destType, binaryNode); //for OP= nodes, the node can carry a narrower types than its lhs rhs. This is perfectly fine

        return end(ensureSymbol(destType, ensureAssignmentSlots(binaryNode)));
    }

    private Expression ensureSymbol(final Type type, final Expression expr) {
        LOG.info("New TEMPORARY added to ", lc.getCurrentFunction().getName(), " type=", type);
        return temporarySymbols.ensureSymbol(lc, type, expr);
    }

    private Symbol newInternal(final String name, final Type type) {
        final Symbol iter = defineSymbol(lc.getCurrentBlock(), name, IS_VAR | IS_INTERNAL);
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
        localDefs.push(new HashSet<>(localDefs.peek()));
        localUses.push(new HashSet<>(localUses.peek()));
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
        body.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            private void toObject(final Block block) {
                for (final Symbol symbol : block.getSymbols()) {
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
                append(lc.getCurrentFunction().getName()).
                append("'");
            LOG.info(sb);
            LOG.indent();
        }

        return true;
    }

    private <T extends Node> T end(final T node) {
        return end(node, true);
    }

    private <T extends Node> T end(final T node, final boolean printNode) {
        if(node instanceof Statement) {
            // If we're done with a statement, all temporaries can be reused.
            temporarySymbols.reuse();
        }
        if (DEBUG) {
            final StringBuilder sb = new StringBuilder();

            sb.append("[LEAVE ").
                append(name(node)).
                append("] ").
                append(printNode ? node.toString() : "").
                append(" in '").
                append(lc.getCurrentFunction().getName());

            if(node instanceof Expression) {
                final Symbol symbol = ((Expression)node).getSymbol();
                if (symbol == null) {
                    sb.append(" <NO SYMBOL>");
                } else {
                    sb.append(" <symbol=").append(symbol).append('>');
                }
            }

            LOG.unindent();
            LOG.info(sb);
        }

        return node;
    }
}
