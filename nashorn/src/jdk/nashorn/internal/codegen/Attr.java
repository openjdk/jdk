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
import static jdk.nashorn.internal.codegen.CompilerConstants.ARGUMENTS_VAR;
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
import static jdk.nashorn.internal.ir.Symbol.IS_PROGRAM_LEVEL;
import static jdk.nashorn.internal.ir.Symbol.IS_SCOPE;
import static jdk.nashorn.internal.ir.Symbol.IS_THIS;
import static jdk.nashorn.internal.ir.Symbol.IS_VAR;
import static jdk.nashorn.internal.ir.Symbol.KINDMASK;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionCall;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LexicalContextNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.ir.OptimisticLexicalContext;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TemporarySymbols;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
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

final class Attr extends NodeOperatorVisitor<OptimisticLexicalContext> {

    private final CompilationEnvironment env;

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

    private final Set<Long> optimistic = new HashSet<>();
    private final Set<Long> neverOptimistic = new HashSet<>();
    private final Map<String, Symbol> globalSymbols = new HashMap<>(); //reuse the same global symbol

    private int catchNestingLevel;

    private static final DebugLogger LOG   = new DebugLogger("attr");
    private static final boolean     DEBUG = LOG.isEnabled();

    private final TemporarySymbols temporarySymbols;

    /**
     * Constructor.
     */
    Attr(final CompilationEnvironment env, final TemporarySymbols temporarySymbols) {
        super(new OptimisticLexicalContext(env.useOptimisticTypes()));
        this.env              = env;
        this.temporarySymbols = temporarySymbols;
        this.localDefs        = new ArrayDeque<>();
        this.localUses        = new ArrayDeque<>();
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
    public boolean enterAccessNode(AccessNode accessNode) {
        tagNeverOptimistic(accessNode.getBase());
        tagNeverOptimistic(accessNode.getProperty());
        return true;
    };

    @Override
    public Node leaveAccessNode(final AccessNode accessNode) {
        return end(ensureSymbolTypeOverride(accessNode, Type.OBJECT));
    }

    private void initFunctionWideVariables(final FunctionNode functionNode, final Block body) {
        initCompileConstant(CALLEE, body, IS_PARAM | IS_INTERNAL);
        initCompileConstant(THIS, body, IS_PARAM | IS_THIS, Type.OBJECT);

        if (functionNode.isVarArg()) {
            initCompileConstant(VARARGS, body, IS_PARAM | IS_INTERNAL);
            if (functionNode.needsArguments()) {
                initCompileConstant(ARGUMENTS, body, IS_VAR | IS_INTERNAL | IS_ALWAYS_DEFINED);
                final String argumentsName = ARGUMENTS_VAR.symbolName();
                newType(defineSymbol(body, argumentsName, IS_VAR | IS_ALWAYS_DEFINED), Type.typeFor(ARGUMENTS_VAR.type()));
                addLocalDef(argumentsName);
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
                final Expression init = varNode.getInit();
                if (init != null) {
                    tagOptimistic(init);
                }

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
                    if (canBeUndefined.contains(ident.getName()) && varNode.getInit() == null) {
                        symbol.setType(Type.OBJECT);
                        symbol.setCanBeUndefined();
                    }
                    functionNode.addDeclaredSymbol(symbol);
                    if (varNode.isFunctionDeclaration()) {
                        newType(symbol, FunctionNode.FUNCTION_TYPE);
                        symbol.setIsFunctionDeclaration();
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
                if(functionNode.allVarsInScope()) { // basically, has deep eval
                    lc.setFlag(body, Block.USES_SELF_SYMBOL);
                }
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

    private boolean useOptimisticTypes() {
        return env.useOptimisticTypes() &&
                // an inner function in on-demand compilation is not compiled, so no need to evaluate expressions in it
                (!env.isOnDemandCompilation() || lc.getOutermostFunction() == lc.getCurrentFunction()) &&
                // No optimistic compilation within split nodes for now
                !lc.isInSplitNode();
    }

    @Override
    public boolean enterCallNode(final CallNode callNode) {
        for (final Expression arg : callNode.getArgs()) {
            tagOptimistic(arg);
        }
        tagNeverOptimistic(callNode.getFunction());
        return true;
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        for (final Expression arg : callNode.getArgs()) {
            inferParameter(arg, arg.getType());
        }
        return end(ensureSymbolTypeOverride(callNode, Type.OBJECT));
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        final IdentNode exception = catchNode.getException();
        final Block     block     = lc.getCurrentBlock();

        start(catchNode);
        catchNestingLevel++;

        // define block-local exception variable
        final String exname = exception.getName();
        // If the name of the exception starts with ":e", this is a synthetic catch block, likely a catch-all. Its
        // symbol is naturally internal, and should be treated as such.
        final boolean isInternal = exname.startsWith(EXCEPTION_PREFIX.symbolName());
        final Symbol def = defineSymbol(block, exname, IS_VAR | IS_LET | IS_ALWAYS_DEFINED | (isInternal ? IS_INTERNAL : 0));
        // Normally, we can catch anything, not just ECMAExceptions, hence Type.OBJECT. However, for catches with
        // internal symbol, we can be sure the caught type is a Throwable.
        newType(def, isInternal ? Type.typeFor(EXCEPTION_PREFIX.type()) : Type.OBJECT);

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
        int     flags    = symbolFlags;
        Symbol  symbol   = findSymbol(block, name); // Locate symbol.
        boolean isGlobal = (flags & KINDMASK) == IS_GLOBAL;

        if (isGlobal) {
            flags |= IS_SCOPE;
        }

        if (lc.getCurrentFunction().isProgram()) {
            flags |= IS_PROGRAM_LEVEL;
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
            } else if (isGlobal) {
                symbolBlock = lc.getOutermostFunction().getBody();
            } else {
                symbolBlock = lc.getFunctionBody(function);
            }

            // Create and add to appropriate block.
            symbol = createSymbol(name, flags);
            symbolBlock.putSymbol(lc, symbol);

            if ((flags & Symbol.KINDMASK) != IS_GLOBAL) {
                symbol.setNeedsSlot(true);
            }
        } else if (symbol.less(flags)) {
            symbol.setFlags(flags);
        }

        return symbol;
    }

    private Symbol createSymbol(final String name, final int flags) {
        if ((flags & Symbol.KINDMASK) == IS_GLOBAL) {
            //reuse global symbols so they can be hashed
            Symbol global = globalSymbols.get(name);
            if (global == null) {
                global = new Symbol(name, flags);
                globalSymbols.put(name, global);
            }
            return global;
        }
        return new Symbol(name, flags);
    }

    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        final Expression expr = expressionStatement.getExpression();
        if (!expr.isSelfModifying()) { //self modifying ops like i++ need the optimistic type for their own operation, not just the return value, as there is no difference. gah.
            tagNeverOptimistic(expr);
        }
        return true;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        start(functionNode, false);

        //an outermost function in our lexical context that is not a program
        //is possible - it is a function being compiled lazily
        if (functionNode.isDeclared()) {
            final Iterator<Block> blocks = lc.getBlocks();
            if (blocks.hasNext()) {
                defineSymbol(blocks.next(), functionNode.getIdent().getName(), IS_VAR);
            }
        }

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
                newFunctionNode = (FunctionNode)ensureSymbol(newFunctionNode, FunctionNode.FUNCTION_TYPE);
            } else {
                assert name != null;
                final Symbol self = body.getExistingSymbol(name);
                assert self != null && self.isFunctionSelf();
                newFunctionNode = (FunctionNode)newFunctionNode.setSymbol(lc, body.getExistingSymbol(name));
            }
        }

        newFunctionNode = finalizeParameters(newFunctionNode);
        newFunctionNode = finalizeTypes(newFunctionNode);
        for (final Symbol symbol : newFunctionNode.getDeclaredSymbols()) {
            if (symbol.getSymbolType().isUnknown()) {
                symbol.setType(Type.OBJECT);
                symbol.setCanBeUndefined();
            }
        }

        List<VarNode> syntheticInitializers = null;

        if (newFunctionNode.usesSelfSymbol()) {
            syntheticInitializers = new ArrayList<>(2);
            LOG.info("Accepting self symbol init for ", newFunctionNode.getName());
            // "var fn = :callee"
            syntheticInitializers.add(createSyntheticInitializer(newFunctionNode.getIdent(), CALLEE, newFunctionNode));
        }

        if (newFunctionNode.needsArguments()) {
            if (syntheticInitializers == null) {
                syntheticInitializers = new ArrayList<>(1);
            }
            // "var arguments = :arguments"
            syntheticInitializers.add(createSyntheticInitializer(createImplicitIdentifier(ARGUMENTS_VAR.symbolName()),
                    ARGUMENTS, newFunctionNode));
        }

        if (syntheticInitializers != null) {
            final List<Statement> stmts = newFunctionNode.getBody().getStatements();
            final List<Statement> newStatements = new ArrayList<>(stmts.size() + syntheticInitializers.size());
            newStatements.addAll(syntheticInitializers);
            newStatements.addAll(stmts);
            newFunctionNode = newFunctionNode.setBody(lc, newFunctionNode.getBody().setStatements(lc, newStatements));
        }

        final int optimisticFlag = lc.hasOptimisticAssumptions() ? FunctionNode.IS_OPTIMISTIC : 0;

        newFunctionNode = newFunctionNode.setState(lc, CompilationState.ATTR).setFlag(lc, optimisticFlag);

        popLocals();

        if (!env.isOnDemandCompilation() && newFunctionNode.isProgram()) {
            newFunctionNode = newFunctionNode.setBody(lc, newFunctionNode.getBody().setFlag(lc, Block.IS_GLOBAL_SCOPE));
            assert newFunctionNode.getId() == 1;
        }

        return end(newFunctionNode, false);
    }

    /**
     * Creates a synthetic initializer for a variable (a var statement that doesn't occur in the source code). Typically
     * used to create assignmnent of {@code :callee} to the function name symbol in self-referential function
     * expressions as well as for assignment of {@code :arguments} to {@code arguments}.
     *
     * @param name the ident node identifying the variable to initialize
     * @param initConstant the compiler constant it is initialized to
     * @param fn the function node the assignment is for
     * @return a var node with the appropriate assignment
     */
    private VarNode createSyntheticInitializer(final IdentNode name, final CompilerConstants initConstant, final FunctionNode fn) {
        final IdentNode init = compilerConstant(initConstant);
        assert init.getSymbol() != null && init.getSymbol().hasSlot();

        final VarNode synthVar = new VarNode(fn.getLineNumber(), fn.getToken(), fn.getFinish(), name, init);

        final Symbol nameSymbol = fn.getBody().getExistingSymbol(name.getName());
        assert nameSymbol != null;

        return synthVar.setName((IdentNode)name.setSymbol(lc, nameSymbol));
    }

    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        final String name = identNode.getName();

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
                lc.setFlag(functionNode.getBody(), Block.USES_SELF_SYMBOL);
                newType(symbol, FunctionNode.FUNCTION_TYPE);
            } else if (!(identNode.isInitializedHere() || symbol.isAlwaysDefined())) {
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
            symbol = defineGlobalSymbol(block, name);
            // we have never seen this before, it can be undefined
            newType(symbol, Type.OBJECT); // TODO unknown -we have explicit casts anyway?
            symbol.setCanBeUndefined();
            Symbol.setSymbolIsScope(lc, symbol);
        }

        setBlockScope(name, symbol);

        if (!identNode.isInitializedHere()) {
            symbol.increaseUseCount();
        }
        addLocalUse(name);
        IdentNode node = (IdentNode)identNode.setSymbol(lc, symbol);
        if (isTaggedOptimistic(identNode) && symbol.isScope()) {
            node = ensureSymbolTypeOverride(node, symbol.getSymbolType());
        }

        return end(node);
    }

    private Symbol defineGlobalSymbol(final Block block, final String name) {
        return defineSymbol(block, name, IS_GLOBAL);
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

    private boolean symbolNeedsToBeScope(final Symbol symbol) {
        if (symbol.isThis() || symbol.isInternal()) {
            return false;
        }

        if (lc.getCurrentFunction().allVarsInScope()) {
            return true;
        }

        boolean previousWasBlock = false;
        for (final Iterator<LexicalContextNode> it = lc.getAllNodes(); it.hasNext();) {
            final LexicalContextNode node = it.next();
            if (node instanceof FunctionNode || node instanceof SplitNode) {
                // We reached the function boundary or a splitting boundary without seeing a definition for the symbol.
                // It needs to be in scope.
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
    public boolean enterIndexNode(IndexNode indexNode) {
        tagNeverOptimistic(indexNode.getBase());
        return true;
    }

    @Override
    public Node leaveIndexNode(final IndexNode indexNode) {
       // return end(ensureSymbolOptimistic(Type.OBJECT, indexNode));
        return end(ensureSymbolTypeOverride(indexNode, Type.OBJECT));
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
        return end(ensureSymbol(objectNode, Type.OBJECT));
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

                final Type newCaseType = newCaseNode.getTest().getType();
                if (newCaseType.isBoolean()) {
                    type = Type.OBJECT; //booleans and integers aren't assignment compatible
                } else {
                    type = Type.widest(type, newCaseType);
                }
            }

            newCases.add(newCaseNode);
        }

        //only optimize for all integers
        if (!type.isInteger()) {
            type = Type.OBJECT;
        }

        switchNode.setTag(newInternal(lc.getCurrentFunction().uniqueName(SWITCH_TAG_PREFIX.symbolName()), type));

        return end(switchNode.setCases(lc, newCases));
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
        //function each(iterator, context) {
        //  for (var i = 0, length = this.length >>> 0; i < length; i++) { if (i in this) iterator.call(context, this[i], i, this); }
        //
        if (isLocalUse(ident.getName()) && varNode.getInit() == null) {
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
    public boolean enterNOT(final UnaryNode unaryNode) {
        tagNeverOptimistic(unaryNode.getExpression());
        return true;
    }

    public boolean enterUnaryArithmetic(final UnaryNode unaryNode) {
        tagOptimistic(unaryNode.getExpression());
        return true;
    }

    private UnaryNode leaveUnaryArithmetic(final UnaryNode unaryNode) {
        return end(coerce(unaryNode, unaryNode.getMostPessimisticType(), unaryNode.getExpression().getType()));
    }

    @Override
    public boolean enterADD(final UnaryNode unaryNode) {
        return enterUnaryArithmetic(unaryNode);
    }

    @Override
    public Node leaveADD(final UnaryNode unaryNode) {
        return leaveUnaryArithmetic(unaryNode);
    }

    @Override
    public Node leaveBIT_NOT(final UnaryNode unaryNode) {
        return end(coerce(unaryNode, Type.INT));
    }

    @Override
    public boolean enterDECINC(final UnaryNode unaryNode) {
        return enterUnaryArithmetic(unaryNode);
    }

    @Override
    public Node leaveDECINC(final UnaryNode unaryNode) {
        // @see assignOffset
        final Type pessimisticType = unaryNode.getMostPessimisticType();
        final UnaryNode newUnaryNode = ensureSymbolTypeOverride(unaryNode, pessimisticType, unaryNode.getExpression().getType());
        newType(newUnaryNode.getExpression().getSymbol(), newUnaryNode.getType());
        return end(newUnaryNode);
    }

    @Override
    public Node leaveDELETE(final UnaryNode unaryNode) {
        final FunctionNode currentFunctionNode = lc.getCurrentFunction();
        final boolean      strictMode          = currentFunctionNode.isStrict();
        final Expression   rhs                 = unaryNode.getExpression();
        final Expression   strictFlagNode      = (Expression)LiteralNode.newInstance(unaryNode, strictMode).accept(this);

        Request request = Request.DELETE;
        final List<Expression> args = new ArrayList<>();

        if (rhs instanceof IdentNode) {
            // If this is a declared variable or a function parameter, delete always fails (except for globals).
            final String name = ((IdentNode)rhs).getName();

            final boolean isParam         = rhs.getSymbol().isParam();
            final boolean isVar           = rhs.getSymbol().isVar();
            final boolean isNonProgramVar = isVar && !rhs.getSymbol().isProgramLevel();
            final boolean failDelete      = strictMode || isParam || isNonProgramVar;

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

    @Override
    public Node leaveNEW(final UnaryNode unaryNode) {
        return end(coerce(unaryNode.setExpression(((CallNode)unaryNode.getExpression()).setIsNew()), Type.OBJECT));
    }

    @Override
    public Node leaveNOT(final UnaryNode unaryNode) {
        return end(coerce(unaryNode, Type.BOOLEAN));
    }

    private IdentNode compilerConstant(final CompilerConstants cc) {
        return (IdentNode)createImplicitIdentifier(cc.symbolName()).setSymbol(lc, lc.getCurrentFunction().compilerConstant(cc));
    }

    /**
     * Creates an ident node for an implicit identifier within the function (one not declared in the script source
     * code). These identifiers are defined with function's token and finish.
     * @param name the name of the identifier
     * @return an ident node representing the implicit identifier.
     */
    private IdentNode createImplicitIdentifier(final String name) {
        final FunctionNode fn = lc.getCurrentFunction();
        return new IdentNode(fn.getToken(), fn.getFinish(), name);
    }

    @Override
    public Node leaveTYPEOF(final UnaryNode unaryNode) {
        final Expression rhs = unaryNode.getExpression();

        final List<Expression> args = new ArrayList<>();
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
        return end(ensureSymbol(runtimeNode, runtimeNode.getRequest().getReturnType()));
    }

    @Override
    public boolean enterSUB(final UnaryNode unaryNode) {
        return enterUnaryArithmetic(unaryNode);
    }

    @Override
    public Node leaveSUB(final UnaryNode unaryNode) {
        return leaveUnaryArithmetic(unaryNode);
    }

    @Override
    public Node leaveVOID(final UnaryNode unaryNode) {
        return end(ensureSymbol(unaryNode, Type.OBJECT));
    }

    @Override
    public boolean enterADD(final BinaryNode binaryNode) {
        tagOptimistic(binaryNode.lhs());
        tagOptimistic(binaryNode.rhs());
        return true;
    }

    /**
     * Add is a special binary, as it works not only on arithmetic, but for
     * strings etc as well.
     */
    @Override
    public Node leaveADD(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        //an add is at least as wide as the current arithmetic type, possibly wider in the case of objects
        //which will be corrected in the post pass if unknown at this stage

        Type argumentsType = Type.widest(lhs.getType(), rhs.getType());
        if (argumentsType.getTypeClass() == String.class) {
            assert binaryNode.isTokenType(TokenType.ADD);
            argumentsType = Type.OBJECT;
        }
        final Type pessimisticType = Type.widest(Type.NUMBER, argumentsType);

        return end(ensureSymbolTypeOverride(binaryNode, pessimisticType, argumentsType));
    }

    @Override
    public Node leaveAND(final BinaryNode binaryNode) {
        return end(ensureSymbol(binaryNode, Type.OBJECT));
    }

    /**
     * This is a helper called before an assignment.
     * @param binaryNode assignment node
     */
    private boolean enterAssignmentNode(final BinaryNode binaryNode) {
        start(binaryNode);
        final Expression lhs = binaryNode.lhs();
        if (lhs instanceof IdentNode) {
            if (CompilerConstants.isCompilerConstant(((IdentNode)lhs).getName())) {
                tagNeverOptimistic(binaryNode.rhs());
            }
        }
        tagOptimistic(binaryNode.rhs());

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
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();
        final Type type;

        if (lhs instanceof IdentNode) {
            final Block     block = lc.getCurrentBlock();
            final IdentNode ident = (IdentNode)lhs;
            final String    name  = ident.getName();

            final Symbol symbol = findSymbol(block, name);

            if (symbol == null) {
                defineGlobalSymbol(block, name);
            } else {
                maybeForceScope(symbol);
            }

            addLocalDef(name);
        }

        if (rhs.getType().isNumeric()) {
            type = Type.widest(lhs.getType(), rhs.getType());
        } else {
            type = Type.OBJECT; //force lhs to be an object if not numeric assignment, e.g. strings too.
        }

        newType(lhs.getSymbol(), type);
        return end(ensureSymbol(binaryNode, type));
    }

    private boolean isLocal(final FunctionNode function, final Symbol symbol) {
        final FunctionNode definingFn = lc.getDefiningFunction(symbol);
        // Temp symbols are not assigned to a block, so their defining fn is null; those can be assumed local
        return definingFn == null || definingFn == function;
    }

    @Override
    public boolean enterASSIGN(final BinaryNode binaryNode) {
        // left hand side of an ordinary assignment need never be optimistic (it's written only, not read).
        tagNeverOptimistic(binaryNode.lhs());
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
        final Expression lhs    = binaryNode.lhs();
        final Expression rhs    = binaryNode.rhs();
        final Type       widest = Type.widest(lhs.getType(), rhs.getType());
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
    public boolean enterBIT_AND(final BinaryNode binaryNode) {
        return enterBitwiseOperator(binaryNode);
    }

    @Override
    public Node leaveBIT_AND(final BinaryNode binaryNode) {
        return leaveBitwiseOperator(binaryNode);
    }

    @Override
    public boolean enterBIT_OR(final BinaryNode binaryNode) {
        return enterBitwiseOperator(binaryNode);
    }

    @Override
    public Node leaveBIT_OR(final BinaryNode binaryNode) {
        return leaveBitwiseOperator(binaryNode);
    }

    @Override
    public boolean enterBIT_XOR(final BinaryNode binaryNode) {
        return enterBitwiseOperator(binaryNode);
    }

    @Override
    public Node leaveBIT_XOR(final BinaryNode binaryNode) {
        return leaveBitwiseOperator(binaryNode);
    }

    public boolean enterBitwiseOperator(final BinaryNode binaryNode) {
        start(binaryNode);
        tagOptimistic(binaryNode.lhs());
        tagOptimistic(binaryNode.rhs());
        return true;
    }

    private Node leaveBitwiseOperator(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.INT));
    }

    @Override
    public Node leaveCOMMARIGHT(final BinaryNode binaryNode) {
//        return end(ensureSymbol(binaryNode, binaryNode.rhs().getType()));
        return leaveComma(binaryNode, binaryNode.rhs());
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        return leaveComma(binaryNode, binaryNode.lhs());
    }

    private Node leaveComma(final BinaryNode commaNode, final Expression effectiveExpr) {
        Type type = effectiveExpr.getType();
        if (type.isUnknown()) { //TODO more optimistic
            type = Type.OBJECT;
        }
        return end(ensureSymbol(commaNode, type));
    }

    @Override
    public Node leaveDIV(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    private Node leaveCmp(final BinaryNode binaryNode) {
        //infect untyped comp with opportunistic type from other
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();
        final Type type = Type.narrowest(lhs.getType(), rhs.getType(), Type.INT);
        inferParameter(lhs, type);
        inferParameter(rhs, type);
        final Type widest = Type.widest(lhs.getType(), rhs.getType());
        ensureSymbol(lhs, widest);
        ensureSymbol(rhs, widest);
        return end(ensureSymbol(binaryNode, Type.BOOLEAN));
    }

    private boolean enterBinaryArithmetic(final BinaryNode binaryNode) {
        tagOptimistic(binaryNode.lhs());
        tagOptimistic(binaryNode.rhs());
        return true;
    }

    //leave a binary node and inherit the widest type of lhs , rhs
    private Node leaveBinaryArithmetic(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, binaryNode.getMostPessimisticType(), Type.widest(binaryNode.lhs().getType(), binaryNode.rhs().getType())));
    }

    @Override
    public boolean enterEQ(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveEQ(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public boolean enterEQ_STRICT(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveEQ_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public boolean enterGE(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveGE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public boolean enterGT(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
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
    public boolean enterLE(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveLE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public boolean enterLT(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
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
    public boolean enterMUL(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveMUL(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    @Override
    public boolean enterNE(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveNE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public boolean enterNE_STRICT(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveNE_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode);
    }

    @Override
    public Node leaveOR(final BinaryNode binaryNode) {
        return end(ensureSymbol(binaryNode, Type.OBJECT));
    }

    @Override
    public boolean enterSAR(final BinaryNode binaryNode) {
        return enterBitwiseOperator(binaryNode);
    }

    @Override
    public Node leaveSAR(final BinaryNode binaryNode) {
        return leaveBitwiseOperator(binaryNode);
    }

    @Override
    public boolean enterSHL(final BinaryNode binaryNode) {
        return enterBitwiseOperator(binaryNode);
    }

    @Override
    public Node leaveSHL(final BinaryNode binaryNode) {
        return leaveBitwiseOperator(binaryNode);
    }

    @Override
    public boolean enterSHR(final BinaryNode binaryNode) {
        return enterBitwiseOperator(binaryNode);
    }

    @Override
    public Node leaveSHR(final BinaryNode binaryNode) {
        return end(coerce(binaryNode, Type.LONG));
    }

    @Override
    public boolean enterSUB(final BinaryNode binaryNode) {
        return enterBinaryArithmetic(binaryNode);
    }

    @Override
    public Node leaveSUB(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        tagNeverOptimistic(forNode.getTest());
        return true;
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

        return end(forNode);
    }

    @Override
    public boolean enterTernaryNode(final TernaryNode ternaryNode) {
        tagNeverOptimistic(ternaryNode.getTest());
        return true;
    }

    @Override
    public Node leaveTernaryNode(final TernaryNode ternaryNode) {
        final Type trueType  = ternaryNode.getTrueExpression().getType();
        final Type falseType = ternaryNode.getFalseExpression().getType();
        final Type type;
        if (trueType.isUnknown() || falseType.isUnknown()) {
            type = Type.UNKNOWN;
        } else {
            type = widestReturnType(trueType, falseType);
        }
        return end(ensureSymbol(ternaryNode, type));
    }

    /**
     * When doing widening for return types of a function or a ternary operator, it is not valid to widen a boolean to
     * anything other than object. Note that this wouldn't be necessary if {@code Type.widest} did not allow
     * boolean-to-number widening. Eventually, we should address it there, but it affects too many other parts of the
     * system and is sometimes legitimate (e.g. whenever a boolean value would undergo ToNumber conversion anyway).
     * @param t1 type 1
     * @param t2 type 2
     * @return wider of t1 and t2, except if one is boolean and the other is neither boolean nor unknown, in which case
     * {@code Type.OBJECT} is returned.
     */
    private static Type widestReturnType(final Type t1, final Type t2) {
        if (t1.isUnknown()) {
            return t2;
        } else if (t2.isUnknown()) {
            return t1;
        } else if(t1.isBoolean() != t2.isBoolean() || t1.isNumeric() != t2.isNumeric()) {
            return Type.OBJECT;
        }
        return Type.widest(t1, t2);
    }

    private void initCompileConstant(final CompilerConstants cc, final Block block, final int flags) {
        // Must not call this method for constants with no explicit types; use the one with (..., Type) signature instead.
        assert cc.type() != null;
        initCompileConstant(cc, block, flags, Type.typeFor(cc.type()));
    }

    private void initCompileConstant(final CompilerConstants cc, final Block block, final int flags, final Type type) {
        defineSymbol(block, cc.symbolName(), flags).
            setTypeOverride(type).
            setNeedsSlot(true);
    }

    /**
     * Initialize parameters for function node. This may require specializing
     * types if a specialization profile is known
     *
     * @param functionNode the function node
     */
    private void initParameters(final FunctionNode functionNode, final Block body) {
        final boolean isOptimistic = env.useOptimisticTypes();
        int pos = 0;
        for (final IdentNode param : functionNode.getParameters()) {
            addLocalDef(param.getName());

            final Symbol paramSymbol = defineSymbol(body, param.getName(), IS_PARAM);
            assert paramSymbol != null;

            final Type callSiteParamType = env.getParamType(functionNode, pos);
            if (callSiteParamType != null) {
                LOG.info("Callsite type override for parameter " + pos + " " + paramSymbol + " => " + callSiteParamType);
                newType(paramSymbol, callSiteParamType);
            } else {
                // When we're using optimistic compilation, we'll generate specialized versions of the functions anyway
                // based on their input type, so if we're doing a compilation without parameter types explicitly
                // specified in the compilation environment, just pre-initialize them all to Object. Note that this is
                // not merely an optimization; it has correctness implications as Type.UNKNOWN is narrower than all
                // other types, which when combined with optimistic typing can cause invalid coercions to be introduced
                // in the generated code. E.g. "var b = { x: 0 }; (function (i) { this.x += i }).apply(b, [1.1])" would
                // erroneously allow coercion of "i" to int when "this.x" is an optimistic-int and "i" starts out
                // with Type.UNKNOWN.
                newType(paramSymbol, isOptimistic ? Type.OBJECT : Type.UNKNOWN);
            }
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
        final boolean pessimistic = !useOptimisticTypes();

        for (final IdentNode param : functionNode.getParameters()) {
            final Symbol paramSymbol = functionNode.getBody().getExistingSymbol(param.getName());
            assert paramSymbol != null;
            assert paramSymbol.isParam() : paramSymbol + " " + paramSymbol.getFlags();
            newParams.add((IdentNode)param.setSymbol(lc, paramSymbol));

            assert paramSymbol != null;
            final Type type = paramSymbol.getSymbolType();

            // all param types are initialized to unknown
            // first we check if we do have a type (inferred during generation)
            // and it's not an object. In that case we make an optimistic
            // assumption
            if (!type.isUnknown() && !type.isObject()) {
                //optimistically inferred
                lc.logOptimisticAssumption(paramSymbol, type);
            }

            //known runtime types are hardcoded already in initParameters so avoid any
            //overly optimistic assumptions, e.g. a double parameter known from
            //RecompilableScriptFunctionData is with us all the way
            if (type.isUnknown()) {
                newType(paramSymbol, Type.OBJECT);
            }

            // if we are pessimistic, we are always an object
            if (pessimistic) {
                newType(paramSymbol, Type.OBJECT);
            }

            // parameters should not be slots for a function that uses variable arity signature
            if (isVarArg) {
                paramSymbol.setNeedsSlot(false);
                newType(paramSymbol, Type.OBJECT);
            }
        }

        final FunctionNode newFunctionNode = functionNode;

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
            final Symbol symbol = defineGlobalSymbol(block, key);
            newType(symbol, Type.OBJECT);
            LOG.info("Added global symbol from property map ", symbol);
        }
    }

    private static Symbol pseudoSymbol(final String name) {
        return new Symbol(name, 0, Type.OBJECT);
    }

    private Symbol exceptionSymbol() {
        return newInternal(lc.getCurrentFunction().uniqueName(EXCEPTION_PREFIX.symbolName()), Type.typeFor(EXCEPTION_PREFIX.type()));
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
        final Deque<Type> returnTypes = new ArrayDeque<>();

        FunctionNode currentFunctionNode = functionNode;
        int fixedPointIterations = 0;
        do {
            fixedPointIterations++;
            assert fixedPointIterations < 0x100 : "too many fixed point iterations for " + functionNode.getName() + " -> most likely infinite loop";
            changed.clear();

            final FunctionNode newFunctionNode = (FunctionNode)currentFunctionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {

                private Expression widen(final Expression node, final Type to) {
                    if (node instanceof LiteralNode) {
                        return node;
                    }
                    final Type from = node.getType();
                    if (!Type.areEquivalent(from, to) && Type.widest(from, to) == to) {
                        if (LOG.isEnabled()) {
                            LOG.fine("Had to post pass widen '", node, "' ", Debug.id(node), " from ", node.getType(), " to ", to);
                        }
                        Symbol symbol = node.getSymbol();
                        if (symbol.isShared() && symbol.wouldChangeType(to)) {
                            symbol = temporarySymbols.getTypedTemporarySymbol(to);
                        }
                        newType(symbol, to);
                        final Expression newNode = node.setSymbol(lc, symbol);
                        if (node != newNode) {
                            changed.add(newNode);
                        }
                        return newNode;
                    }
                    return node;
                }

                @Override
                public boolean enterFunctionNode(final FunctionNode node) {
                    returnTypes.push(Type.UNKNOWN);
                    return true;
                }

                @Override
                public Node leaveFunctionNode(final FunctionNode node) {
                    Type returnType = returnTypes.pop();
                    if (returnType.isUnknown()) {
                        returnType = Type.OBJECT;
                    }
                    return node.setReturnType(lc, returnType);
                }

                @Override
                public Node leaveReturnNode(final ReturnNode returnNode) {
                    Type returnType = returnTypes.pop();
                    if (returnNode.hasExpression()) {
                        returnType = widestReturnType(returnType, returnNode.getExpression().getType()); //getSymbol().getSymbolType());
                    } else {
                        returnType = Type.OBJECT; //undefined
                    }

                    returnTypes.push(returnType);

                    return returnNode;
                }

                //
                // Eg.
                //
                // var d = 17;
                // var e;
                // e = d; //initially typed as int for node type, should retype as double
                // e = object;
                //
                // var d = 17;
                // var e;
                // e -= d; //initially type number, should number remain with a final conversion supplied by Store. ugly, but the computation result of the sub is numeric
                // e = object;
                //
                @SuppressWarnings("fallthrough")
                @Override
                public Node leaveBinaryNode(final BinaryNode binaryNode) {
                    Type widest = Type.widest(binaryNode.lhs().getType(), binaryNode.rhs().getType());
                    BinaryNode newBinaryNode = binaryNode;

                    if (isAdd(binaryNode)) {
                        if(widest.getTypeClass() == String.class) {
                            // Erase "String" to "Object" as we have trouble with optimistically typed operands that
                            // would be typed "String" in the code generator as they are always loaded using the type
                            // of the operation.
                            widest = Type.OBJECT;
                        }
                        newBinaryNode = (BinaryNode)widen(newBinaryNode, widest);
                        if (newBinaryNode.getType().isObject() && !isAddString(newBinaryNode)) {
                            return new RuntimeNode(newBinaryNode, Request.ADD);
                        }
                    } else if (binaryNode.isComparison()) {
                        final Expression lhs = newBinaryNode.lhs();
                        final Expression rhs = newBinaryNode.rhs();

                        Type cmpWidest = Type.widest(lhs.getType(), rhs.getType());

                        boolean newRuntimeNode = false, finalized = false;
                        switch (newBinaryNode.tokenType()) {
                        case EQ_STRICT:
                        case NE_STRICT:
                            if (lhs.getType().isBoolean() != rhs.getType().isBoolean()) {
                                newRuntimeNode = true;
                                cmpWidest = Type.OBJECT;
                                finalized = true;
                            }
                            //fallthru
                        default:
                            if (newRuntimeNode || cmpWidest.isObject()) {
                                return new RuntimeNode(newBinaryNode, Request.requestFor(binaryNode)).setIsFinal(finalized);
                            }
                            break;
                        }

                        return newBinaryNode;
                    } else {
                        if (!binaryNode.isAssignment() || binaryNode.isSelfModifying()) {
                            return newBinaryNode;
                        }
                        checkThisAssignment(binaryNode);
                        newBinaryNode = newBinaryNode.setLHS(widen(newBinaryNode.lhs(), widest));
                        newBinaryNode = (BinaryNode)widen(newBinaryNode, widest);
                    }

                    return newBinaryNode;

                }

                @Override
                public Node leaveTernaryNode(final TernaryNode ternaryNode) {
                    return widen(ternaryNode, Type.widest(ternaryNode.getTrueExpression().getType(), ternaryNode.getFalseExpression().getType()));
                }

                private boolean isAdd(final Node node) {
                    return node.isTokenType(TokenType.ADD);
                }

                /**
                 * Determine if the outcome of + operator is a string.
                 *
                 * @param node  Node to test.
                 * @return true if a string result.
                 */
                private boolean isAddString(final Node node) {
                    if (node instanceof BinaryNode && isAdd(node)) {
                        final BinaryNode binaryNode = (BinaryNode)node;
                        final Node lhs = binaryNode.lhs();
                        final Node rhs = binaryNode.rhs();

                        return isAddString(lhs) || isAddString(rhs);
                    }

                    return node instanceof LiteralNode<?> && ((LiteralNode<?>)node).isString();
                }

                private void checkThisAssignment(final BinaryNode binaryNode) {
                    if (binaryNode.isAssignment()) {
                        if (binaryNode.lhs() instanceof AccessNode) {
                            final AccessNode accessNode = (AccessNode) binaryNode.lhs();

                            if (accessNode.getBase().getSymbol().isThis()) {
                                lc.getCurrentFunction().addThisProperty(accessNode.getProperty().getName());
                            }
                        }
                    }
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

    private Node leaveSelfModifyingAssignmentNode(final BinaryNode binaryNode, final Type pessimisticType) {
        //e.g. for -=, Number, no wider, destType (binaryNode.getWidestOperationType())  is the coerce type
        final Expression lhs = binaryNode.lhs();
        final BinaryNode newBinaryNode = ensureSymbolTypeOverride(binaryNode, pessimisticType, Type.widest(lhs.getType(), binaryNode.rhs().getType()));
        newType(lhs.getSymbol(), newBinaryNode.getType()); //may not narrow if dest is already wider than destType
        return end(newBinaryNode);
    }

    private Expression ensureSymbol(final Expression expr, final Type type) {
        LOG.info("New TEMPORARY added to ", lc.getCurrentFunction().getName(), " type=", type);
        return temporarySymbols.ensureSymbol(lc, type, expr);
    }

    @Override
    public boolean enterReturnNode(final ReturnNode returnNode) {
        tagOptimistic(returnNode.getExpression());
        return true;
    }

    @Override
    public boolean enterIfNode(final IfNode ifNode) {
        tagNeverOptimistic(ifNode.getTest());
        return true;
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        tagNeverOptimistic(whileNode.getTest());
        return true;
    }

    /**
     * Used to signal that children should be optimistic. Otherwise every identnode
     * in the entire program would basically start out as being guessed as an int
     * and warmup would take an ENORMOUS time. This is also used while we get all
     * the logic up and running, as we currently can't afford to debug every potential
     * situtation that has to do with unwarranted optimism. We currently only tag
     * type overrides, all other nodes are nops in this function
     *
     * @param expr an expression that is to be tagged as optimistic.
     */
    private long tag(final Optimistic expr) {
        return (long)lc.getCurrentFunction().getId() << 32 | expr.getProgramPoint();
    }

    /**
     * This is used to guarantee that there are no optimistic setters, something that
     * doesn't make sense in our current model, where only optimistic getters can exist.
     * If we set something, we use the callSiteType. We might want to use dual fields
     * though and incorporate this later for the option of putting something wider than
     * is currently in the field causing an UnwarrantedOptimismException.
     *
     * @param expr expression to be tagged as never optimistic
     */
    private void tagNeverOptimistic(final Expression expr) {
        if (expr instanceof Optimistic) {
            LOG.info("Tagging TypeOverride node '" + expr + "' never optimistic");
            neverOptimistic.add(tag((Optimistic)expr));
        }
    }

    private void tagOptimistic(final Expression expr) {
        if (expr instanceof Optimistic) {
            LOG.info("Tagging TypeOverride node '" + expr + "' as optimistic");
            optimistic.add(tag((Optimistic)expr));
        }
    }

    private boolean isTaggedNeverOptimistic(final Optimistic expr) {
        return neverOptimistic.contains(tag(expr));
    }

    private boolean isTaggedOptimistic(final Optimistic expr) {
        return optimistic.contains(tag(expr));
    }

    private Type getOptimisticType(final Optimistic expr) {
        return useOptimisticTypes() ? env.getOptimisticType(expr) : expr.getMostPessimisticType();
    }

    /**
     *  This is the base function for typing a TypeOverride as optimistic. For any expression that
     *  can also be a type override (call, ident node (scope load), access node, index node) we use
     *  the override type to communicate optimism.
     *
     *  @param pessimisticType conservative always guaranteed to work for this operation
     *  @param to node to set type for
     */
    private <T extends Expression & Optimistic> T ensureSymbolTypeOverride(final T node, final Type pessimisticType) {
        return ensureSymbolTypeOverride(node, pessimisticType, null);
    }

    @SuppressWarnings("unchecked")
    private <T extends Expression & Optimistic> T ensureSymbolTypeOverride(final T node, final Type pessimisticType, final Type argumentsType) {
        // check what the most optimistic type for this node should be
        // if we are running with optimistic types, this starts out as e.g. int, and based on previous
        // failed assumptions it can be wider, for example double if we have failed this assumption
        // in a previous run
        final boolean isNeverOptimistic = isTaggedNeverOptimistic(node);

        // avoid optimistic type evaluation if the node is never optimistic
        Type optimisticType = isNeverOptimistic ? node.getMostPessimisticType() : getOptimisticType(node);

        if (argumentsType != null) {
            optimisticType = Type.widest(optimisticType, argumentsType);
        }

        // the symbol of the expression is the pessimistic one, i.e. IndexNodes are always Object for consistency
        // with the type system.
        T expr = (T)ensureSymbol(node, pessimisticType);

        if (optimisticType.isObject()) {
            return expr;
        }

        if (isNeverOptimistic) {
            return expr;
        }

        if(!(node instanceof FunctionCall && ((FunctionCall)node).isFunction())) {
            // in the case that we have an optimistic type, set the type override (setType is inherited from TypeOverride)
            // but maintain the symbol type set above. Also flag the function as optimistic. Don't do this for any
            // expressions that are used as the callee of a function call.
            if (optimisticType.narrowerThan(pessimisticType)) {
                expr = (T)expr.setType(temporarySymbols, optimisticType);
                expr = (T)Node.setIsOptimistic(expr, true);
                if (optimisticType.isPrimitive()) {
                    final Symbol symbol = expr.getSymbol();
                    if (symbol.isShared()) {
                        expr = (T)expr.setSymbol(lc, symbol.createUnshared(symbol.getName()));
                    }
                }
                LOG.fine(expr, " turned optimistic with type=", optimisticType);
                assert ((Optimistic)expr).isOptimistic();
            }
        }
        return expr;
    }


    private Symbol newInternal(final String name, final Type type) {
        return defineSymbol(lc.getCurrentBlock(), name, IS_VAR | IS_INTERNAL).setType(type); //NASHORN-73
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

    private  void inferParameter(final Expression node, final Type type) {
        final Symbol symbol = node.getSymbol();
        if (useOptimisticTypes() && symbol.isParam()) {
            final Type symbolType = symbol.getSymbolType();
            if(symbolType.isBoolean() && !(type.isBoolean() || type == Type.OBJECT)) {
                // boolean parameters can only legally be widened to Object
                return;
            }
            if (symbolType != type) {
               LOG.info("Infer parameter type " + symbol + " ==> " + type + " " + lc.getCurrentFunction().getSource().getName() + " " + lc.getCurrentFunction().getName());
            }
            symbol.setType(type); //will be overwritten by object later if pessimistic anyway
            lc.logOptimisticAssumption(symbol, type);
        }
    }

    private BinaryNode coerce(final BinaryNode binaryNode, final Type pessimisticType) {
        return coerce(binaryNode, pessimisticType, null);
    }

    private BinaryNode coerce(final BinaryNode binaryNode, final Type pessimisticType, final Type argumentsType) {
        final BinaryNode newNode = ensureSymbolTypeOverride(binaryNode, pessimisticType, argumentsType);
        inferParameter(binaryNode.lhs(), newNode.getType());
        inferParameter(binaryNode.rhs(), newNode.getType());
        return newNode;
    }

    private UnaryNode coerce(final UnaryNode unaryNode, final Type pessimisticType) {
        return coerce(unaryNode, pessimisticType, null);
    }

    private UnaryNode coerce(final UnaryNode unaryNode, final Type pessimisticType, final Type argumentType) {
        UnaryNode newNode = ensureSymbolTypeOverride(unaryNode, pessimisticType, argumentType);
        if (newNode.isOptimistic()) {
            if (unaryNode.getExpression() instanceof Optimistic) {
               newNode = newNode.setExpression(Node.setIsOptimistic(unaryNode.getExpression(), true));
            }
        }
        inferParameter(unaryNode.getExpression(), newNode.getType());
        return newNode;
    }

    private static String name(final Node node) {
        final String cn = node.getClass().getName();
        final int lastDot = cn.lastIndexOf('.');
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
                append(lc.getCurrentFunction().getName()).
                append('\'');

            if (node instanceof Expression) {
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
