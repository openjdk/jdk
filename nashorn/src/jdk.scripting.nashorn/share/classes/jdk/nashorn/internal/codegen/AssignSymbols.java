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
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SWITCH_TAG_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.ir.Symbol.HAS_OBJECT_VALUE;
import static jdk.nashorn.internal.ir.Symbol.IS_CONST;
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
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
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode.ArrayUnit;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.JSErrorType;
import jdk.nashorn.internal.runtime.ParserException;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * This visitor assigns symbols to identifiers denoting variables. It does few more minor calculations that are only
 * possible after symbols have been assigned; such is the transformation of "delete" and "typeof" operators into runtime
 * nodes and counting of number of properties assigned to "this" in constructor functions. This visitor is also notable
 * for what it doesn't do, most significantly it does no type calculations as in JavaScript variables can change types
 * during runtime and as such symbols don't have types. Calculation of expression types is performed by a separate
 * visitor.
 */
@Logger(name="symbols")
final class AssignSymbols extends NodeVisitor<LexicalContext> implements Loggable {
    private final DebugLogger log;
    private final boolean     debug;

    private static boolean isParamOrVar(final IdentNode identNode) {
        final Symbol symbol = identNode.getSymbol();
        return symbol.isParam() || symbol.isVar();
    }

    private static String name(final Node node) {
        final String cn = node.getClass().getName();
        final int lastDot = cn.lastIndexOf('.');
        if (lastDot == -1) {
            return cn;
        }
        return cn.substring(lastDot + 1);
    }

    /**
     * Checks if various symbols that were provisionally marked as needing a slot ended up unused, and marks them as not
     * needing a slot after all.
     * @param functionNode the function node
     * @return the passed in node, for easy chaining
     */
    private static FunctionNode removeUnusedSlots(final FunctionNode functionNode) {
        if (!functionNode.needsCallee()) {
            functionNode.compilerConstant(CALLEE).setNeedsSlot(false);
        }
        if (!(functionNode.hasScopeBlock() || functionNode.needsParentScope())) {
            functionNode.compilerConstant(SCOPE).setNeedsSlot(false);
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
        return functionNode;
    }

    private final Deque<Set<String>> thisProperties = new ArrayDeque<>();
    private final Map<String, Symbol> globalSymbols = new HashMap<>(); //reuse the same global symbol
    private final Compiler compiler;

    public AssignSymbols(final Compiler compiler) {
        super(new LexicalContext());
        this.compiler = compiler;
        this.log   = initLogger(compiler.getContext());
        this.debug = log.isEnabled();
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    /**
     * Define symbols for all variable declarations at the top of the function scope. This way we can get around
     * problems like
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
        // This visitor will assign symbol to all declared variables, except "var" declarations in for loop initializers.
        body.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            protected boolean enterDefault(final Node node) {
                // Don't bother visiting expressions; var is a statement, it can't be inside an expression.
                // This will also prevent visiting nested functions (as FunctionNode is an expression).
                return !(node instanceof Expression);
            }

            @Override
            public Node leaveVarNode(final VarNode varNode) {
                if (varNode.isStatement()) {
                    final IdentNode ident  = varNode.getName();
                    final Block block = varNode.isBlockScoped() ? getLexicalContext().getCurrentBlock() : body;
                    final Symbol symbol = defineSymbol(block, ident.getName(), ident, varNode.getSymbolFlags());
                    if (varNode.isFunctionDeclaration()) {
                        symbol.setIsFunctionDeclaration();
                    }
                    return varNode.setName(ident.setSymbol(symbol));
                }
                return varNode;
            }
        });
    }

    private IdentNode compilerConstantIdentifier(final CompilerConstants cc) {
        return createImplicitIdentifier(cc.symbolName()).setSymbol(lc.getCurrentFunction().compilerConstant(cc));
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
        final IdentNode init = compilerConstantIdentifier(initConstant);
        assert init.getSymbol() != null && init.getSymbol().isBytecodeLocal();

        final VarNode synthVar = new VarNode(fn.getLineNumber(), fn.getToken(), fn.getFinish(), name, init);

        final Symbol nameSymbol = fn.getBody().getExistingSymbol(name.getName());
        assert nameSymbol != null;

        return (VarNode)synthVar.setName(name.setSymbol(nameSymbol)).accept(this);
    }

    private FunctionNode createSyntheticInitializers(final FunctionNode functionNode) {
        final List<VarNode> syntheticInitializers = new ArrayList<>(2);

        // Must visit the new var nodes in the context of the body. We could also just set the new statements into the
        // block and then revisit the entire block, but that seems to be too much double work.
        final Block body = functionNode.getBody();
        lc.push(body);
        try {
            if (functionNode.usesSelfSymbol()) {
                // "var fn = :callee"
                syntheticInitializers.add(createSyntheticInitializer(functionNode.getIdent(), CALLEE, functionNode));
            }

            if (functionNode.needsArguments()) {
                // "var arguments = :arguments"
                syntheticInitializers.add(createSyntheticInitializer(createImplicitIdentifier(ARGUMENTS_VAR.symbolName()),
                        ARGUMENTS, functionNode));
            }

            if (syntheticInitializers.isEmpty()) {
                return functionNode;
            }

            for(final ListIterator<VarNode> it = syntheticInitializers.listIterator(); it.hasNext();) {
                it.set((VarNode)it.next().accept(this));
            }
        } finally {
            lc.pop(body);
        }

        final List<Statement> stmts = body.getStatements();
        final List<Statement> newStatements = new ArrayList<>(stmts.size() + syntheticInitializers.size());
        newStatements.addAll(syntheticInitializers);
        newStatements.addAll(stmts);
        return functionNode.setBody(lc, body.setStatements(lc, newStatements));
    }

    /**
     * Defines a new symbol in the given block.
     *
     * @param block        the block in which to define the symbol
     * @param name         name of symbol.
     * @param origin       origin node
     * @param symbolFlags  Symbol flags.
     *
     * @return Symbol for given name or null for redefinition.
     */
    private Symbol defineSymbol(final Block block, final String name, final Node origin, final int symbolFlags) {
        int    flags  = symbolFlags;
        final boolean isBlockScope = (flags & IS_LET) != 0 || (flags & IS_CONST) != 0;
        final boolean isGlobal     = (flags & KINDMASK) == IS_GLOBAL;

        Symbol symbol;
        final FunctionNode function;
        if (isBlockScope) {
            // block scoped variables always live in current block, no need to look for existing symbols in parent blocks.
            symbol = block.getExistingSymbol(name);
            function = lc.getCurrentFunction();
        } else {
            symbol = findSymbol(block, name);
            function = lc.getFunction(block);
        }

        // Global variables are implicitly always scope variables too.
        if (isGlobal) {
            flags |= IS_SCOPE;
        }

        if (lc.getCurrentFunction().isProgram()) {
            flags |= IS_PROGRAM_LEVEL;
        }

        final boolean isParam = (flags & KINDMASK) == IS_PARAM;
        final boolean isVar =   (flags & KINDMASK) == IS_VAR;

        if (symbol != null) {
            // Symbol was already defined. Check if it needs to be redefined.
            if (isParam) {
                if (!isLocal(function, symbol)) {
                    // Not defined in this function. Create a new definition.
                    symbol = null;
                } else if (symbol.isParam()) {
                    // Duplicate parameter. Null return will force an error.
                    throw new AssertionError("duplicate parameter");
                }
            } else if (isVar) {
                if (isBlockScope) {
                    // Check redeclaration in same block
                    if (symbol.hasBeenDeclared()) {
                        throwParserException(ECMAErrors.getMessage("syntax.error.redeclare.variable", name), origin);
                    } else {
                        symbol.setHasBeenDeclared();
                    }
                } else if ((flags & IS_INTERNAL) != 0) {
                    // Always create a new definition.
                    symbol = null;
                } else {
                    // Found LET or CONST in parent scope of same function - s SyntaxError
                    if (symbol.isBlockScoped() && isLocal(lc.getCurrentFunction(), symbol)) {
                        throwParserException(ECMAErrors.getMessage("syntax.error.redeclare.variable", name), origin);
                    }
                    // Not defined in this function. Create a new definition.
                    if (!isLocal(function, symbol) || symbol.less(IS_VAR)) {
                        symbol = null;
                    }
                }
            }
        }

        if (symbol == null) {
            // If not found, then create a new one.
            final Block symbolBlock;

            // Determine where to create it.
            if (isVar && ((flags & IS_INTERNAL) != 0 || isBlockScope)) {
                symbolBlock = block; //internal vars are always defined in the block closest to them
            } else if (isGlobal) {
                symbolBlock = lc.getOutermostFunction().getBody();
            } else {
                symbolBlock = lc.getFunctionBody(function);
            }

            // Create and add to appropriate block.
            symbol = createSymbol(name, flags);
            symbolBlock.putSymbol(lc, symbol);

            if ((flags & IS_SCOPE) == 0) {
                // Initial assumption; symbol can lose its slot later
                symbol.setNeedsSlot(true);
            }
        } else if (symbol.less(flags)) {
            symbol.setFlags(flags);
        }

        return symbol;
    }

    private <T extends Node> T end(final T node) {
        return end(node, true);
    }

    private <T extends Node> T end(final T node, final boolean printNode) {
        if (debug) {
            final StringBuilder sb = new StringBuilder();

            sb.append("[LEAVE ").
                append(name(node)).
                append("] ").
                append(printNode ? node.toString() : "").
                append(" in '").
                append(lc.getCurrentFunction().getName()).
                append('\'');

            if (node instanceof IdentNode) {
                final Symbol symbol = ((IdentNode)node).getSymbol();
                if (symbol == null) {
                    sb.append(" <NO SYMBOL>");
                } else {
                    sb.append(" <symbol=").append(symbol).append('>');
                }
            }

            log.unindent();
            log.info(sb);
        }

        return node;
    }

    @Override
    public boolean enterBlock(final Block block) {
        start(block);

        if (lc.isFunctionBody()) {
            block.clearSymbols();
            final FunctionNode fn = lc.getCurrentFunction();
            if (isUnparsedFunction(fn)) {
                // It's a skipped nested function. Just mark the symbols being used by it as being in use.
                for(final String name: compiler.getScriptFunctionData(fn.getId()).getExternalSymbolNames()) {
                    nameIsUsed(name, null);
                }
                // Don't bother descending into it, it must be empty anyway.
                assert block.getStatements().isEmpty();
                return false;
            }

            enterFunctionBody();
        }

        return true;
    }

    private boolean isUnparsedFunction(final FunctionNode fn) {
        return compiler.isOnDemandCompilation() && fn != lc.getOutermostFunction();
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        final IdentNode exception = catchNode.getException();
        final Block     block     = lc.getCurrentBlock();

        start(catchNode);

        // define block-local exception variable
        final String exname = exception.getName();
        // If the name of the exception starts with ":e", this is a synthetic catch block, likely a catch-all. Its
        // symbol is naturally internal, and should be treated as such.
        final boolean isInternal = exname.startsWith(EXCEPTION_PREFIX.symbolName());
        // IS_LET flag is required to make sure symbol is not visible outside catch block. However, we need to
        // clear the IS_LET flag after creation to allow redefinition of symbol inside the catch block.
        final Symbol symbol = defineSymbol(block, exname, catchNode, IS_VAR | IS_LET | (isInternal ? IS_INTERNAL : 0) | HAS_OBJECT_VALUE);
        symbol.clearFlag(IS_LET);

        return true;
    }

    private void enterFunctionBody() {
        final FunctionNode functionNode = lc.getCurrentFunction();
        final Block body = lc.getCurrentBlock();

        initFunctionWideVariables(functionNode, body);

        if (!functionNode.isProgram() && !functionNode.isDeclared() && !functionNode.isAnonymous()) {
            // It's neither declared nor program - it's a function expression then; assign it a self-symbol unless it's
            // anonymous.
            final String name = functionNode.getIdent().getName();
            assert name != null;
            assert body.getExistingSymbol(name) == null;
            defineSymbol(body, name, functionNode, IS_VAR | IS_FUNCTION_SELF | HAS_OBJECT_VALUE);
            if(functionNode.allVarsInScope()) { // basically, has deep eval
                lc.setFlag(functionNode, FunctionNode.USES_SELF_SYMBOL);
            }
        }

        acceptDeclarations(functionNode, body);
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        start(functionNode, false);

        thisProperties.push(new HashSet<String>());

        // Every function has a body, even the ones skipped on reparse (they have an empty one). We're
        // asserting this as even for those, enterBlock() must be invoked to correctly process symbols that
        // are used in them.
        assert functionNode.getBody() != null;

        return true;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        start(varNode);
        // Normally, a symbol assigned in a var statement is not live for its RHS. Since we also represent function
        // declarations as VarNodes, they are exception to the rule, as they need to have the symbol visible to the
        // body of the declared function for self-reference.
        if (varNode.isFunctionDeclaration()) {
            defineVarIdent(varNode);
        }
        return true;
    }

    @Override
    public Node leaveVarNode(final VarNode varNode) {
        if (!varNode.isFunctionDeclaration()) {
            defineVarIdent(varNode);
        }
        return super.leaveVarNode(varNode);
    }

    private void defineVarIdent(final VarNode varNode) {
        final IdentNode ident = varNode.getName();
        final int flags;
        if (varNode.isAnonymousFunctionDeclaration()) {
            flags = IS_INTERNAL;
        } else if (lc.getCurrentFunction().isProgram()) {
            flags = IS_SCOPE;
        } else {
            flags = 0;
        }
        defineSymbol(lc.getCurrentBlock(), ident.getName(), ident, varNode.getSymbolFlags() | flags);
    }

    private Symbol exceptionSymbol() {
        return newObjectInternal(EXCEPTION_PREFIX);
    }

    /**
     * This has to run before fix assignment types, store any type specializations for
     * parameters, then turn them into objects for the generic version of this method.
     *
     * @param functionNode functionNode
     */
    private FunctionNode finalizeParameters(final FunctionNode functionNode) {
        final List<IdentNode> newParams = new ArrayList<>();
        final boolean isVarArg = functionNode.isVarArg();

        final Block body = functionNode.getBody();
        for (final IdentNode param : functionNode.getParameters()) {
            final Symbol paramSymbol = body.getExistingSymbol(param.getName());
            assert paramSymbol != null;
            assert paramSymbol.isParam() : paramSymbol + " " + paramSymbol.getFlags();
            newParams.add(param.setSymbol(paramSymbol));

            // parameters should not be slots for a function that uses variable arity signature
            if (isVarArg) {
                paramSymbol.setNeedsSlot(false);
            }
        }

        return functionNode.setParameters(lc, newParams);
    }

    /**
     * Search for symbol in the lexical context starting from the given block.
     * @param name Symbol name.
     * @return Found symbol or null if not found.
     */
    private Symbol findSymbol(final Block block, final String name) {
        for (final Iterator<Block> blocks = lc.getBlocks(block); blocks.hasNext();) {
            final Symbol symbol = blocks.next().getExistingSymbol(name);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    /**
     * Marks the current function as one using any global symbol. The function and all its parent functions will all be
     * marked as needing parent scope.
     * @see FunctionNode#needsParentScope()
     */
    private void functionUsesGlobalSymbol() {
        for (final Iterator<FunctionNode> fns = lc.getFunctions(); fns.hasNext();) {
            lc.setFlag(fns.next(), FunctionNode.USES_ANCESTOR_SCOPE);
        }
    }

    /**
     * Marks the current function as one using a scoped symbol. The block defining the symbol will be marked as needing
     * its own scope to hold the variable. If the symbol is defined outside of the current function, it and all
     * functions up to (but not including) the function containing the defining block will be marked as needing parent
     * function scope.
     * @see FunctionNode#needsParentScope()
     */
    private void functionUsesScopeSymbol(final Symbol symbol) {
        final String name = symbol.getName();
        for (final Iterator<LexicalContextNode> contextNodeIter = lc.getAllNodes(); contextNodeIter.hasNext(); ) {
            final LexicalContextNode node = contextNodeIter.next();
            if (node instanceof Block) {
                final Block block = (Block)node;
                if (block.getExistingSymbol(name) != null) {
                    assert lc.contains(block);
                    lc.setBlockNeedsScope(block);
                    break;
                }
            } else if (node instanceof FunctionNode) {
                lc.setFlag(node, FunctionNode.USES_ANCESTOR_SCOPE);
            }
        }
    }

    /**
     * Declares that the current function is using the symbol.
     * @param symbol the symbol used by the current function.
     */
    private void functionUsesSymbol(final Symbol symbol) {
        assert symbol != null;
        if (symbol.isScope()) {
            if (symbol.isGlobal()) {
                functionUsesGlobalSymbol();
            } else {
                functionUsesScopeSymbol(symbol);
            }
        } else {
            assert !symbol.isGlobal(); // Every global is also scope
        }
    }

    private void initCompileConstant(final CompilerConstants cc, final Block block, final int flags) {
        defineSymbol(block, cc.symbolName(), null, flags).setNeedsSlot(true);
    }

    private void initFunctionWideVariables(final FunctionNode functionNode, final Block body) {
        initCompileConstant(CALLEE, body, IS_PARAM | IS_INTERNAL | HAS_OBJECT_VALUE);
        initCompileConstant(THIS, body, IS_PARAM | IS_THIS | HAS_OBJECT_VALUE);

        if (functionNode.isVarArg()) {
            initCompileConstant(VARARGS, body, IS_PARAM | IS_INTERNAL | HAS_OBJECT_VALUE);
            if (functionNode.needsArguments()) {
                initCompileConstant(ARGUMENTS, body, IS_VAR | IS_INTERNAL | HAS_OBJECT_VALUE);
                defineSymbol(body, ARGUMENTS_VAR.symbolName(), null, IS_VAR | HAS_OBJECT_VALUE);
            }
        }

        initParameters(functionNode, body);
        initCompileConstant(SCOPE, body, IS_VAR | IS_INTERNAL | HAS_OBJECT_VALUE);
        initCompileConstant(RETURN, body, IS_VAR | IS_INTERNAL);
    }

    /**
     * Initialize parameters for function node.
     * @param functionNode the function node
     */
    private void initParameters(final FunctionNode functionNode, final Block body) {
        final boolean isVarArg = functionNode.isVarArg();
        final boolean scopeParams = functionNode.allVarsInScope() || isVarArg;
        for (final IdentNode param : functionNode.getParameters()) {
            final Symbol symbol = defineSymbol(body, param.getName(), param, IS_PARAM);
            if(scopeParams) {
                // NOTE: this "set is scope" is a poor substitute for clear expression of where the symbol is stored.
                // It will force creation of scopes where they would otherwise not necessarily be needed (functions
                // using arguments object and other variable arity functions). Tracked by JDK-8038942.
                symbol.setIsScope();
                assert symbol.hasSlot();
                if(isVarArg) {
                    symbol.setNeedsSlot(false);
                }
            }
        }
    }

    /**
     * Is the symbol local to (that is, defined in) the specified function?
     * @param function the function
     * @param symbol the symbol
     * @return true if the symbol is defined in the specified function
     */
    private boolean isLocal(final FunctionNode function, final Symbol symbol) {
        final FunctionNode definingFn = lc.getDefiningFunction(symbol);
        assert definingFn != null;
        return definingFn == function;
    }

    private void checkConstAssignment(final IdentNode ident) {
        // Check for reassignment of constant
        final Symbol symbol = ident.getSymbol();
        if (symbol.isConst()) {
            throwParserException(ECMAErrors.getMessage("syntax.error.assign.constant", symbol.getName()), ident);
        }
    }

    @Override
    public Node leaveBinaryNode(final BinaryNode binaryNode) {
        if (binaryNode.isAssignment() && binaryNode.lhs() instanceof IdentNode) {
            checkConstAssignment((IdentNode) binaryNode.lhs());
        }
        switch (binaryNode.tokenType()) {
        case ASSIGN:
            return leaveASSIGN(binaryNode);
        default:
            return super.leaveBinaryNode(binaryNode);
        }
    }

    private Node leaveASSIGN(final BinaryNode binaryNode) {
        // If we're assigning a property of the this object ("this.foo = ..."), record it.
        final Expression lhs = binaryNode.lhs();
        if (lhs instanceof AccessNode) {
            final AccessNode accessNode = (AccessNode) lhs;
            final Expression base = accessNode.getBase();
            if (base instanceof IdentNode) {
                final Symbol symbol = ((IdentNode)base).getSymbol();
                if(symbol.isThis()) {
                    thisProperties.peek().add(accessNode.getProperty());
                }
            }
        }
        return binaryNode;
    }

    @Override
    public Node leaveUnaryNode(final UnaryNode unaryNode) {
        if (unaryNode.isAssignment() && unaryNode.getExpression() instanceof IdentNode) {
            checkConstAssignment((IdentNode) unaryNode.getExpression());
        }
        switch (unaryNode.tokenType()) {
        case DELETE:
            return leaveDELETE(unaryNode);
        case TYPEOF:
            return leaveTYPEOF(unaryNode);
        default:
            return super.leaveUnaryNode(unaryNode);
        }
    }

    @Override
    public Node leaveBlock(final Block block) {
        // It's not necessary to guard the marking of symbols as locals with this "if" condition for
        // correctness, it's just an optimization -- runtime type calculation is not used when the compilation
        // is not an on-demand optimistic compilation, so we can skip locals marking then.
        if (compiler.useOptimisticTypes() && compiler.isOnDemandCompilation()) {
            // OTOH, we must not declare symbols from nested functions to be locals. As we're doing on-demand
            // compilation, and we're skipping parsing the function bodies for nested functions, this
            // basically only means their parameters. It'd be enough to mistakenly declare to be a local a
            // symbol in the outer function named the same as one of the parameters, though.
            if (lc.getFunction(block) == lc.getOutermostFunction()) {
                for (final Symbol symbol: block.getSymbols()) {
                    if (!symbol.isScope()) {
                        assert symbol.isVar() || symbol.isParam();
                        compiler.declareLocalSymbol(symbol.getName());
                    }
                }
            }
        }
        return block;
    }

    private Node leaveDELETE(final UnaryNode unaryNode) {
        final FunctionNode currentFunctionNode = lc.getCurrentFunction();
        final boolean      strictMode          = currentFunctionNode.isStrict();
        final Expression   rhs                 = unaryNode.getExpression();
        final Expression   strictFlagNode      = (Expression)LiteralNode.newInstance(unaryNode, strictMode).accept(this);

        Request request = Request.DELETE;
        final List<Expression> args = new ArrayList<>();

        if (rhs instanceof IdentNode) {
            final IdentNode ident = (IdentNode)rhs;
            // If this is a declared variable or a function parameter, delete always fails (except for globals).
            final String name = ident.getName();
            final Symbol symbol = ident.getSymbol();
            final boolean failDelete = strictMode || (!symbol.isScope() && (symbol.isParam() || (symbol.isVar() && !symbol.isProgramLevel())));

            if (failDelete && symbol.isThis()) {
                return LiteralNode.newInstance(unaryNode, true).accept(this);
            }
            final Expression literalNode = (Expression)LiteralNode.newInstance(unaryNode, name).accept(this);

            if (!failDelete) {
                args.add(compilerConstantIdentifier(SCOPE));
            }
            args.add(literalNode);
            args.add(strictFlagNode);

            if (failDelete) {
                request = Request.FAIL_DELETE;
            }
        } else if (rhs instanceof AccessNode) {
            final Expression base     = ((AccessNode)rhs).getBase();
            final String     property = ((AccessNode)rhs).getProperty();

            args.add(base);
            args.add((Expression)LiteralNode.newInstance(unaryNode, property).accept(this));
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
        return new RuntimeNode(unaryNode, request, args).accept(this);
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        if (forNode.isForIn()) {
            forNode.setIterator(newObjectInternal(ITERATOR_PREFIX)); //NASHORN-73
        }

        return end(forNode);
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        final FunctionNode finalizedFunction;
        if (isUnparsedFunction(functionNode)) {
            finalizedFunction = functionNode;
        } else {
            finalizedFunction =
               markProgramBlock(
               removeUnusedSlots(
               createSyntheticInitializers(
               finalizeParameters(
                       lc.applyTopFlags(functionNode))))
                       .setThisProperties(lc, thisProperties.pop().size()));
        }
        return finalizedFunction.setState(lc, CompilationState.SYMBOLS_ASSIGNED);
    }

    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        if (identNode.isPropertyName()) {
            return identNode;
        }

        final Symbol symbol = nameIsUsed(identNode.getName(), identNode);

        if (!identNode.isInitializedHere()) {
            symbol.increaseUseCount();
        }

        IdentNode newIdentNode = identNode.setSymbol(symbol);

        // If a block-scoped var is used before its declaration mark it as dead.
        // We can only statically detect this for local vars, cross-function symbols require runtime checks.
        if (symbol.isBlockScoped() && !symbol.hasBeenDeclared() && !identNode.isDeclaredHere() && isLocal(lc.getCurrentFunction(), symbol)) {
            newIdentNode = newIdentNode.markDead();
        }

        return end(newIdentNode);
    }

    private Symbol nameIsUsed(final String name, final IdentNode origin) {
        final Block block = lc.getCurrentBlock();

        Symbol symbol = findSymbol(block, name);

        //If an existing symbol with the name is found, use that otherwise, declare a new one
        if (symbol != null) {
            log.info("Existing symbol = ", symbol);
            if (symbol.isFunctionSelf()) {
                final FunctionNode functionNode = lc.getDefiningFunction(symbol);
                assert functionNode != null;
                assert lc.getFunctionBody(functionNode).getExistingSymbol(CALLEE.symbolName()) != null;
                lc.setFlag(functionNode, FunctionNode.USES_SELF_SYMBOL);
            }

            // if symbol is non-local or we're in a with block, we need to put symbol in scope (if it isn't already)
            maybeForceScope(symbol);
        } else {
            log.info("No symbol exists. Declare as global: ", name);
            symbol = defineSymbol(block, name, origin, IS_GLOBAL | IS_SCOPE);
        }

        functionUsesSymbol(symbol);
        return symbol;
    }

    @Override
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        // We only need a symbol for the tag if it's not an integer switch node
        if(!switchNode.isInteger()) {
            switchNode.setTag(newObjectInternal(SWITCH_TAG_PREFIX));
        }
        return switchNode;
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

    private Node leaveTYPEOF(final UnaryNode unaryNode) {
        final Expression rhs = unaryNode.getExpression();

        final List<Expression> args = new ArrayList<>();
        if (rhs instanceof IdentNode && !isParamOrVar((IdentNode)rhs)) {
            args.add(compilerConstantIdentifier(SCOPE));
            args.add((Expression)LiteralNode.newInstance(rhs, ((IdentNode)rhs).getName()).accept(this)); //null
        } else {
            args.add(rhs);
            args.add((Expression)LiteralNode.newInstance(unaryNode).accept(this)); //null, do not reuse token of identifier rhs, it can be e.g. 'this'
        }

        final Node runtimeNode = new RuntimeNode(unaryNode, Request.TYPEOF, args).accept(this);

        end(unaryNode);

        return runtimeNode;
    }

    private FunctionNode markProgramBlock(final FunctionNode functionNode) {
        if (compiler.isOnDemandCompilation() || !functionNode.isProgram()) {
            return functionNode;
        }

        return functionNode.setBody(lc, functionNode.getBody().setFlag(lc, Block.IS_GLOBAL_SCOPE));
    }

    /**
     * If the symbol isn't already a scope symbol, but it needs to be (see {@link #symbolNeedsToBeScope(Symbol)}, it is
     * promoted to a scope symbol and its block marked as needing a scope.
     * @param symbol the symbol that might be scoped
     */
    private void maybeForceScope(final Symbol symbol) {
        if (!symbol.isScope() && symbolNeedsToBeScope(symbol)) {
            Symbol.setSymbolIsScope(lc, symbol);
        }
    }

    private Symbol newInternal(final CompilerConstants cc, final int flags) {
        return defineSymbol(lc.getCurrentBlock(), lc.getCurrentFunction().uniqueName(cc.symbolName()), null, IS_VAR | IS_INTERNAL | flags); //NASHORN-73
    }

    private Symbol newObjectInternal(final CompilerConstants cc) {
        return newInternal(cc, HAS_OBJECT_VALUE);
    }

    private boolean start(final Node node) {
        return start(node, true);
    }

    private boolean start(final Node node, final boolean printNode) {
        if (debug) {
            final StringBuilder sb = new StringBuilder();

            sb.append("[ENTER ").
                append(name(node)).
                append("] ").
                append(printNode ? node.toString() : "").
                append(" in '").
                append(lc.getCurrentFunction().getName()).
                append("'");
            log.info(sb);
            log.indent();
        }

        return true;
    }

    /**
     * Determines if the symbol has to be a scope symbol. In general terms, it has to be a scope symbol if it can only
     * be reached from the current block by traversing a function node, a split node, or a with node.
     * @param symbol the symbol checked for needing to be a scope symbol
     * @return true if the symbol has to be a scope symbol.
     */
    private boolean symbolNeedsToBeScope(final Symbol symbol) {
        if (symbol.isThis() || symbol.isInternal()) {
            return false;
        }

        final FunctionNode func = lc.getCurrentFunction();
        if ( func.allVarsInScope() || (!symbol.isBlockScoped() && func.isProgram())) {
            return true;
        }

        boolean previousWasBlock = false;
        for (final Iterator<LexicalContextNode> it = lc.getAllNodes(); it.hasNext();) {
            final LexicalContextNode node = it.next();
            if (node instanceof FunctionNode || isSplitArray(node)) {
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

    private static boolean isSplitArray(final LexicalContextNode expr) {
        if(!(expr instanceof ArrayLiteralNode)) {
            return false;
        }
        final List<ArrayUnit> units = ((ArrayLiteralNode)expr).getUnits();
        return !(units == null || units.isEmpty());
    }

    private void throwParserException(final String message, final Node origin) {
        if (origin == null) {
            throw new ParserException(message);
        }
        final Source source = compiler.getSource();
        final long token = origin.getToken();
        final int line = source.getLine(origin.getStart());
        final int column = source.getColumn(origin.getStart());
        final String formatted = ErrorManager.format(message, source, line, column, token);
        throw new ParserException(JSErrorType.SYNTAX_ERROR, formatted, source, line, column, token);
    }
}
