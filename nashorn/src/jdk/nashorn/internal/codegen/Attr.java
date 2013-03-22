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
import static jdk.nashorn.internal.codegen.CompilerConstants.EXCEPTION_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.ITERATOR_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCRIPT_RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SWITCH_TAG_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.ir.Symbol.IS_GLOBAL;
import static jdk.nashorn.internal.ir.Symbol.IS_INTERNAL;
import static jdk.nashorn.internal.ir.Symbol.IS_LET;
import static jdk.nashorn.internal.ir.Symbol.IS_PARAM;
import static jdk.nashorn.internal.ir.Symbol.IS_SCOPE;
import static jdk.nashorn.internal.ir.Symbol.IS_THIS;
import static jdk.nashorn.internal.ir.Symbol.IS_VAR;
import static jdk.nashorn.internal.ir.Symbol.KINDMASK;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CallNode.EvalArgs;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LexicalContext;
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
    private Set<String> localDefs;

    /**
     * Local definitions in current block to guard against cases like
     * NASHORN-467 when things can be undefined as they are used before
     * their local var definition. *sigh* JavaScript...
     */
    private Set<String> localUses;

    private final LexicalContext lexicalContext = new LexicalContext();

    private static final DebugLogger LOG   = new DebugLogger("attr");
    private static final boolean     DEBUG = LOG.isEnabled();

    /**
     * Constructor.
     */
    Attr() {
    }

    @Override
    protected Node enterDefault(final Node node) {
        return start(node);
    }

    @Override
    protected Node leaveDefault(final Node node) {
        return end(node);
    }

    @Override
    public Node leaveAccessNode(final AccessNode accessNode) {
        newTemporary(Type.OBJECT, accessNode);  //While Object type is assigned here, Access Specialization in FinalizeTypes may narrow this
        end(accessNode);
        return accessNode;
    }

    @Override
    public Node enterBlock(final Block block) {
        lexicalContext.push(block);
        start(block);

        final Set<String> savedLocalDefs = localDefs;
        final Set<String> savedLocalUses = localUses;

        block.setFrame(getCurrentFunctionNode().pushFrame());

        try {
            // a block starts out by copying the local defs and local uses
            // from the outer level. But we need the copies, as when we
            // leave the block the def and use sets given upon entry must
            // be restored
            localDefs = new HashSet<>(savedLocalDefs);
            localUses = new HashSet<>(savedLocalUses);

            block.visitStatements(this);
        } finally {
            localDefs = savedLocalDefs;
            localUses = savedLocalUses;

            getCurrentFunctionNode().popFrame();
        }

        end(block);

        lexicalContext.pop(block);
        return null;
    }

    @Override
    public Node enterCallNode(final CallNode callNode) {
        start(callNode);

        callNode.getFunction().accept(this);

        final List<Node> acceptedArgs = new ArrayList<>(callNode.getArgs().size());
        for (final Node arg : callNode.getArgs()) {
            LOG.info("Doing call arg " + arg);
            acceptedArgs.add(arg.accept(this));
        }
        callNode.setArgs(acceptedArgs);

        final EvalArgs evalArgs = callNode.getEvalArgs();
        if (evalArgs != null) {
            evalArgs.setCode(evalArgs.getCode().accept(this));

            final IdentNode thisNode = new IdentNode(getCurrentFunctionNode().getThisNode());
            assert thisNode.getSymbol() != null; //should copy attributed symbol and that's it
            evalArgs.setThis(thisNode);
        }

        newTemporary(callNode.getType(), callNode); // access specialization in FinalizeTypes may narrow it further later

        end(callNode);

        return null;
    }

    @Override
    public Node enterCatchNode(final CatchNode catchNode) {
        final IdentNode exception = catchNode.getException();
        final Block     block     = getCurrentBlock();

        start(catchNode);

        // define block-local exception variable
        final Symbol def = defineSymbol(block, exception.getName(), IS_VAR | IS_LET, exception);
        newType(def, Type.OBJECT);
        addLocalDef(exception.getName());

        return catchNode;
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

        final FunctionNode function = lexicalContext.getFunction(block);
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
                    assert !((flags & IS_LET) == IS_LET && symbol.getBlock() == block) : "duplicate let variable in block";
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
                symbolBlock = block;
            } else {
                symbolBlock = function;
            }

            // Create and add to appropriate block.
            symbol = new Symbol(name, flags, node, symbolBlock);
            symbolBlock.putSymbol(name, symbol);

            if ((flags & Symbol.KINDMASK) != IS_GLOBAL) {
                symbolBlock.getFrame().addSymbol(symbol);
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
    public Node enterFunctionNode(final FunctionNode functionNode) {
        start(functionNode, false);
        if (functionNode.isLazy()) {
            LOG.info("LAZY: " + functionNode.getName() + " => Promoting to OBJECT");
            newTemporary(lexicalContext.getCurrentFunction(), Type.OBJECT, functionNode);
            functionNode.setReturnType(Type.OBJECT);
            end(functionNode);
            return null;
        }

        lexicalContext.push(functionNode);

        clearLocalDefs();
        clearLocalUses();

        functionNode.setFrame(functionNode.pushFrame());

        initCallee(functionNode);
        initThis(functionNode);
        if (functionNode.isVarArg()) {
            initVarArg(functionNode);
        }

        initParameters(functionNode);
        initScope(functionNode);
        initReturn(functionNode);

        // Add all nested declared functions as symbols in this function
        for (final FunctionNode nestedFunction : functionNode.getDeclaredFunctions()) {
            final IdentNode ident = nestedFunction.getIdent();
            if (ident != null) {
                assert nestedFunction.isDeclared();
                final Symbol functionSymbol = defineSymbol(functionNode, ident.getName(), IS_VAR, nestedFunction);
                newType(functionSymbol, Type.typeFor(ScriptFunction.class));
            }
        }

        if (functionNode.isProgram()) {
            initFromPropertyMap(functionNode);
        }

        // Add function name as local symbol
        if (!functionNode.isDeclared() && !functionNode.isProgram()) {
            if(functionNode.getSymbol() != null) {
                // a temporary left over from an earlier pass when the function was lazy
                assert functionNode.getSymbol().isTemp();
                // remove it
                functionNode.setSymbol(null);
            }
            final Symbol selfSymbol;
            if(functionNode.isAnonymous()) {
                selfSymbol = newTemporary(functionNode, Type.OBJECT, functionNode);
            } else {
                selfSymbol = defineSymbol(functionNode, functionNode.getIdent().getName(), IS_VAR, functionNode);
                newType(selfSymbol, Type.OBJECT);
                selfSymbol.setNode(functionNode);
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

        final List<Symbol> declaredSymbols = new ArrayList<>();
        // This visitor will assign symbol to all declared variables, except function declarations (which are taken care
        // in a separate step above) and "var" declarations in for loop initializers.
        functionNode.accept(new NodeOperatorVisitor() {
            @Override
            public Node enterFunctionNode(FunctionNode nestedFn) {
                // Don't descend into nested functions
                return nestedFn == functionNode ? nestedFn : null;
            }
            @Override
            public Node enterVarNode(VarNode varNode) {
                if(varNode.isStatement() && !varNode.isFunctionDeclaration()) {
                    final IdentNode ident = varNode.getName();
                    // any declared symbols that aren't visited need to be typed as well, hence the list
                    declaredSymbols.add(defineSymbol(functionNode, ident.getName(), IS_VAR, new IdentNode(ident)));
                }
                return null;
            }
        });

        visitFunctionStatements(functionNode);

        //unknown parameters are promoted to object type.
        finalizeParameters(functionNode);
        finalizeTypes(functionNode);
        for (final Symbol symbol : declaredSymbols) {
            if (symbol.getSymbolType().isUnknown()) {
                symbol.setType(Type.OBJECT);
                symbol.setCanBeUndefined();
            }
        }

        if (functionNode.getReturnType().isUnknown()) {
            LOG.info("Unknown return type promoted to object");
            functionNode.setReturnType(Type.OBJECT);
        }

        if (functionNode.getSelfSymbolInit() != null) {
            LOG.info("Accepting self symbol init " + functionNode.getSelfSymbolInit() + " for " + functionNode.getName());
            final Node init = functionNode.getSelfSymbolInit();
            final List<Node> newStatements = new ArrayList<>();
            newStatements.add(init);
            newStatements.addAll(functionNode.getStatements());
            functionNode.setStatements(newStatements);
            functionNode.setNeedsSelfSymbol(functionNode.getSelfSymbolInit().accept(this));
        }

        if (functionNode.hasLazyChildren()) {
            objectifySymbols(functionNode);
        }

        functionNode.popFrame();

        functionNode.setState(CompilationState.ATTR);

        end(functionNode, false);
        lexicalContext.pop(functionNode);

        return null;
    }

    private void visitFunctionStatements(final FunctionNode functionNode) {
        final List<Node> newStatements = new ArrayList<>(functionNode.getStatements());
        for(ListIterator<Node> stmts = newStatements.listIterator(); stmts.hasNext();) {
            stmts.set(stmts.next().accept(this));
        }
        functionNode.setStatements(newStatements);
    }

    @Override
    public Node leaveCONVERT(final UnaryNode unaryNode) {
        assert false : "There should be no convert operators in IR during Attribution";
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node enterIdentNode(final IdentNode identNode) {
        final String name = identNode.getName();

        start(identNode);

        if (identNode.isPropertyName()) {
            // assign a pseudo symbol to property name
            final Symbol pseudoSymbol = pseudoSymbol(name);
            LOG.info("IdentNode is property name -> assigning pseudo symbol " + pseudoSymbol);
            LOG.unindent();
            identNode.setSymbol(pseudoSymbol);
            return null;
        }

        final Block  block     = getCurrentBlock();
        final Symbol oldSymbol = identNode.getSymbol();

        Symbol symbol = findSymbol(block, name);

        //If an existing symbol with the name is found, use that otherwise, declare a new one
        if (symbol != null) {
            LOG.info("Existing symbol = " + symbol);
            if (isFunctionExpressionSelfReference(symbol)) {
                final FunctionNode functionNode = (FunctionNode)symbol.getNode();
                assert functionNode.getCalleeNode() != null;

                final VarNode var = new VarNode(functionNode.getSource(), functionNode.getToken(), functionNode.getFinish(), functionNode.getIdent(), functionNode.getCalleeNode());
                //newTemporary(Type.OBJECT, var); //ScriptFunction? TODO

                functionNode.setNeedsSelfSymbol(var);
            }

            if (!identNode.isInitializedHere()) { // NASHORN-448
                // here is a use outside the local def scope
                if (!isLocalDef(name)) {
                    newType(symbol, Type.OBJECT);
                    symbol.setCanBeUndefined();
                }
            }

            identNode.setSymbol(symbol);
            // non-local: we need to put symbol in scope (if it isn't already)
            if (!isLocal(getCurrentFunctionNode(), symbol) && !symbol.isScope()) {
                symbol.setIsScope();
            }
        } else {
            LOG.info("No symbol exists. Declare undefined: " + symbol);
            symbol = useSymbol(block, name, identNode);
            // we have never seen this before, it can be undefined
            newType(symbol, Type.OBJECT); // TODO unknown -we have explicit casts anyway?
            symbol.setCanBeUndefined();
            symbol.setIsScope();
        }

        assert symbol != null;
        if(symbol.isGlobal()) {
            setUsesGlobalSymbol();
        } else if(symbol.isScope()) {
            final Iterator<Block> blocks = lexicalContext.getBlocks();
            blocks.next().setUsesScopeSymbol(symbol, blocks);
        }

        if (symbol != oldSymbol && !identNode.isInitializedHere()) {
            symbol.increaseUseCount();
        }
        addLocalUse(identNode.getName());

        end(identNode);

        return null;
    }

    /**
     * Marks the current function as one using any global symbol. The function and all its parent functions will all be
     * marked as needing parent scope.
     * @see #needsParentScope()
     */
    private void setUsesGlobalSymbol() {
        for(final Iterator<FunctionNode> fns = lexicalContext.getFunctions(); fns.hasNext();) {
            fns.next().setUsesAncestorScope();
        }
    }

    /**
     * Declare the use of a symbol in a block.
     *
     * @param block block in which the symbol is used
     * @param name Name of symbol.
     * @param node Using node
     *
     * @return Symbol for given name.
     */
    private Symbol useSymbol(final Block block, final String name, final Node node) {
        Symbol symbol = findSymbol(block, name);

        if (symbol == null) {
            // If not found, declare as a free var.
            symbol = defineSymbol(block, name, IS_GLOBAL, node);
        } else {
            node.setSymbol(symbol);
        }

        return symbol;
    }


    /**
     * Search for symbol in the lexical context starting from the given block.
     * @param name Symbol name.
     * @return Found symbol or null if not found.
     */
    private Symbol findSymbol(final Block block, final String name) {
        // Search up block chain to locate symbol.

        for(final Iterator<Block> blocks = lexicalContext.getBlocks(block); blocks.hasNext();) {
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
        newTemporary(Type.OBJECT, indexNode); //TORO
        return indexNode;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Node enterLiteralNode(final LiteralNode literalNode) {
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

            getCurrentFunctionNode().newLiteral(literalNode);
        } finally {
            end(literalNode);
        }
        return null;
    }

    @Override
    public Node leaveObjectNode(final ObjectNode objectNode) {
        newTemporary(Type.OBJECT, objectNode);
        end(objectNode);
        return objectNode;
    }

    @Override
    public Node enterPropertyNode(final PropertyNode propertyNode) {
        // assign a pseudo symbol to property name, see NASHORN-710
        propertyNode.setSymbol(new Symbol(propertyNode.getKeyName(), 0, Type.OBJECT));
        end(propertyNode);
        return propertyNode;
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
            getCurrentFunctionNode().setReturnType(Type.widest(getCurrentFunctionNode().getReturnType(), symbol.getSymbolType()));
            LOG.info("Returntype is now " + getCurrentFunctionNode().getReturnType());
        }

        end(returnNode);

        return returnNode;
    }

    @Override
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        Type type = Type.UNKNOWN;

        for (final CaseNode caseNode : switchNode.getCases()) {
            final Node test = caseNode.getTest();
            if (test != null) {
                if (test instanceof LiteralNode) {
                    //go down to integers if we can
                    final LiteralNode<?> lit = (LiteralNode<?>)test;
                    if (lit.isNumeric() && !(lit.getValue() instanceof Integer)) {
                        if (JSType.isRepresentableAsInt(lit.getNumber())) {
                            caseNode.setTest(LiteralNode.newInstance(lit, lit.getInt32()).accept(this));
                        }
                    }
                } else {
                    // the "all integer" case that CodeGenerator optimizes for currently assumes literals only
                    type = Type.OBJECT;
                    break;
                }

                type = Type.widest(type, caseNode.getTest().getType());
            }
        }

        //only optimize for all integers
        if (!type.isInteger()) {
            type = Type.OBJECT;
        }

        switchNode.setTag(newInternal(getCurrentFunctionNode().uniqueName(SWITCH_TAG_PREFIX.tag()), type));

        end(switchNode);

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

    @Override
    public Node enterVarNode(final VarNode varNode) {
        start(varNode);

        final IdentNode ident = varNode.getName();
        final String    name  = ident.getName();

        final Symbol symbol = defineSymbol(getCurrentBlock(), name, IS_VAR, ident);
        assert symbol != null;

        LOG.info("VarNode " + varNode + " set symbol " + symbol);
        varNode.setSymbol(symbol);

        // NASHORN-467 - use before definition of vars - conservative
        if (localUses.contains(ident.getName())) {
            newType(symbol, Type.OBJECT);
            symbol.setCanBeUndefined();
        }

        return varNode;
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
        final boolean isScript = lexicalContext.getFunction(symbol.getBlock()).isProgram(); //see NASHORN-56
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
        newTemporary(arithType(), unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveBIT_NOT(final UnaryNode unaryNode) {
        newTemporary(Type.INT, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveDECINC(final UnaryNode unaryNode) {
        // @see assignOffset
        ensureAssignmentSlots(getCurrentFunctionNode(), unaryNode.rhs());
        final Type type = arithType();
        newType(unaryNode.rhs().getSymbol(), type);
        newTemporary(type, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveDELETE(final UnaryNode unaryNode) {
        final FunctionNode   currentFunctionNode = getCurrentFunctionNode();
        final boolean        strictMode          = currentFunctionNode.isStrictMode();
        final Node           rhs                 = unaryNode.rhs();
        final Node           strictFlagNode      = LiteralNode.newInstance(unaryNode, strictMode).accept(this);

        Request request = Request.DELETE;
        final RuntimeNode runtimeNode;
        final List<Node> args = new ArrayList<>();

        if (rhs instanceof IdentNode) {
            // If this is a declared variable or a function parameter, delete always fails (except for globals).
            final String name = ((IdentNode)rhs).getName();

            final boolean failDelete = strictMode || rhs.getSymbol().isParam() || (rhs.getSymbol().isVar() && !rhs.getSymbol().isTopLevel());

            if (failDelete && rhs.getSymbol().isThis()) {
                return LiteralNode.newInstance(unaryNode, true).accept(this);
            }
            final Node literalNode = LiteralNode.newInstance(unaryNode, name).accept(this);

            if (!failDelete) {
                args.add(currentFunctionNode.getScopeNode());
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

        runtimeNode = new RuntimeNode(unaryNode, request, args);
        assert runtimeNode.getSymbol() == unaryNode.getSymbol(); //clone constructor should do this

        return leaveRuntimeNode(runtimeNode);
    }

    @Override
    public Node leaveNEW(final UnaryNode unaryNode) {
        newTemporary(Type.OBJECT, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveNOT(final UnaryNode unaryNode) {
        newTemporary(Type.BOOLEAN, unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveTYPEOF(final UnaryNode unaryNode) {
        final Node rhs    = unaryNode.rhs();

        RuntimeNode runtimeNode;

        List<Node> args = new ArrayList<>();
        if (rhs instanceof IdentNode && !rhs.getSymbol().isParam() && !rhs.getSymbol().isVar()) {
            args.add(getCurrentFunctionNode().getScopeNode());
            args.add(LiteralNode.newInstance(rhs, ((IdentNode)rhs).getName()).accept(this)); //null
        } else {
            args.add(rhs);
            args.add(LiteralNode.newInstance(unaryNode).accept(this)); //null, do not reuse token of identifier rhs, it can be e.g. 'this'
        }

        runtimeNode = new RuntimeNode(unaryNode, Request.TYPEOF, args);
        assert runtimeNode.getSymbol() == unaryNode.getSymbol();

        runtimeNode = (RuntimeNode)leaveRuntimeNode(runtimeNode);

        end(unaryNode);

        return runtimeNode;
    }

    @Override
    public Node leaveRuntimeNode(final RuntimeNode runtimeNode) {
        newTemporary(runtimeNode.getRequest().getReturnType(), runtimeNode);
        return runtimeNode;
    }

    @Override
    public Node leaveSUB(final UnaryNode unaryNode) {
        newTemporary(arithType(), unaryNode);
        end(unaryNode);
        return unaryNode;
    }

    @Override
    public Node leaveVOID(final UnaryNode unaryNode) {
        final RuntimeNode runtimeNode = new RuntimeNode(unaryNode, Request.VOID);
        runtimeNode.accept(this);
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
        newTemporary(Type.widest(lhs.getType(), rhs.getType()), binaryNode);

        end(binaryNode);

        return binaryNode;
    }

    @Override
    public Node leaveAND(final BinaryNode binaryNode) {
        newTemporary(Type.OBJECT, binaryNode);
        end(binaryNode);
        return binaryNode;
    }

    /**
     * This is a helper called before an assignment.
     * @param binaryNode assignment node
     */
    private Node enterAssignmentNode(final BinaryNode binaryNode) {
        start(binaryNode);

        final Node lhs = binaryNode.lhs();

        if (lhs instanceof IdentNode) {
            final Block     block = getCurrentBlock();
            final IdentNode ident = (IdentNode)lhs;
            final String    name  = ident.getName();

            Symbol symbol = findSymbol(getCurrentBlock(), name);

            if (symbol == null) {
                symbol = defineSymbol(block, name, IS_GLOBAL, ident);
                binaryNode.setSymbol(symbol);
            } else if (!isLocal(getCurrentFunctionNode(), symbol)) {
                symbol.setIsScope();
            }

            addLocalDef(name);
        }

        return binaryNode;
    }

    private boolean isLocal(FunctionNode function, Symbol symbol) {
        final Block block = symbol.getBlock();
        // some temp symbols have no block, so can be assumed local
        return block == null || lexicalContext.getFunction(block) == function;
    }

    @Override
    public Node enterASSIGN(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN(final BinaryNode binaryNode) {
        return leaveAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_ADD(final BinaryNode binaryNode) {
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
    public Node enterASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_DIV(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_DIV(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_MOD(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MOD(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_MUL(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MUL(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_SAR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SAR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_SHL(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHL(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_SHR(final BinaryNode binaryNode) {
        return enterAssignmentNode(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHR(final BinaryNode binaryNode) {
        return leaveSelfModifyingAssignmentNode(binaryNode);
    }

    @Override
    public Node enterASSIGN_SUB(final BinaryNode binaryNode) {
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
        newTemporary(binaryNode.rhs().getType(), binaryNode);
        return binaryNode;
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        newTemporary(binaryNode.lhs().getType(), binaryNode);
        return binaryNode;
    }

    @Override
    public Node leaveDIV(final BinaryNode binaryNode) {
        return leaveBinaryArithmetic(binaryNode);
    }

    private Node leaveCmp(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        newTemporary(Type.BOOLEAN, binaryNode);
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
        newTemporary(destType, binaryNode);
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
        newTemporary(Type.OBJECT, binaryNode);
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
            forNode.setIterator(newInternal(getCurrentFunctionNode().uniqueName(ITERATOR_PREFIX.tag()), Type.OBJECT)); //NASHORN-73
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
        newTemporary(type, ternaryNode);

        end(ternaryNode);

        return ternaryNode;
    }

    private void initThis(final FunctionNode functionNode) {
        final Symbol thisSymbol = defineSymbol(functionNode, THIS.tag(), IS_PARAM | IS_THIS, null);
        newType(thisSymbol, Type.OBJECT);
        thisSymbol.setNeedsSlot(true);
        functionNode.getThisNode().setSymbol(thisSymbol);
        LOG.info("Initialized scope symbol: " + thisSymbol);
    }

    private void initScope(final FunctionNode functionNode) {
        final Symbol scopeSymbol = defineSymbol(functionNode, SCOPE.tag(), IS_VAR | IS_INTERNAL, null);
        newType(scopeSymbol, Type.typeFor(ScriptObject.class));
        scopeSymbol.setNeedsSlot(true);
        functionNode.getScopeNode().setSymbol(scopeSymbol);
        LOG.info("Initialized scope symbol: " + scopeSymbol);
    }

    private void initReturn(final FunctionNode functionNode) {
        final Symbol returnSymbol = defineSymbol(functionNode, SCRIPT_RETURN.tag(), IS_VAR | IS_INTERNAL, null);
        newType(returnSymbol, Type.OBJECT);
        returnSymbol.setNeedsSlot(true);
        functionNode.getResultNode().setSymbol(returnSymbol);
        LOG.info("Initialized return symbol: " + returnSymbol);
        //return symbol is always object as it's the __return__ thing. What returnType is is another matter though
    }

    private void initVarArg(final FunctionNode functionNode) {
        if (functionNode.isVarArg()) {
            final Symbol varArgsSymbol = defineSymbol(functionNode, VARARGS.tag(), IS_PARAM | IS_INTERNAL, null);
            varArgsSymbol.setTypeOverride(Type.OBJECT_ARRAY);
            varArgsSymbol.setNeedsSlot(true);
            functionNode.getVarArgsNode().setSymbol(varArgsSymbol);
            LOG.info("Initialized varargs symbol: " + varArgsSymbol);

            if (functionNode.needsArguments()) {
                final String    argumentsName   = functionNode.getArgumentsNode().getName();
                final Symbol    argumentsSymbol = defineSymbol(functionNode, argumentsName, IS_VAR | IS_INTERNAL, null);
                newType(argumentsSymbol, Type.typeFor(ScriptObject.class));
                argumentsSymbol.setNeedsSlot(true);
                functionNode.getArgumentsNode().setSymbol(argumentsSymbol);
                addLocalDef(argumentsName);
                LOG.info("Initialized vararg varArgsSymbol=" + varArgsSymbol + " argumentsSymbol=" + argumentsSymbol);
            }
        }
    }

    private void initCallee(final FunctionNode functionNode) {
        assert functionNode.getCalleeNode() != null : functionNode + " has no callee";
        final Symbol calleeSymbol = defineSymbol(functionNode, CALLEE.tag(), IS_PARAM | IS_INTERNAL, null);
        newType(calleeSymbol, Type.typeFor(ScriptFunction.class));
        calleeSymbol.setNeedsSlot(true);
        functionNode.getCalleeNode().setSymbol(calleeSymbol);
        LOG.info("Initialized callee symbol " + calleeSymbol);
    }

    /**
     * Initialize parameters for function node. This may require specializing
     * types if a specialization profile is known
     *
     * @param functionNode the function node
     */
    private void initParameters(final FunctionNode functionNode) {
        //If a function is specialized, we don't need to tag either it return
        // type or its parameters with the widest (OBJECT) type for safety.
        functionNode.setReturnType(Type.UNKNOWN);

        for (final IdentNode param : functionNode.getParameters()) {
            addLocalDef(param.getName());
            final Symbol paramSymbol = defineSymbol(functionNode, param.getName(), IS_PARAM, param);
            if (paramSymbol != null) {
                final Type callSiteParamType = functionNode.getSpecializedType(param);
                if (callSiteParamType != null) {
                    LOG.info("Param " + paramSymbol + " has a callsite type " + callSiteParamType + ". Using that.");

                    System.err.println("Param " + param + " has a callsite type " + callSiteParamType + ". Using that.");
                }
                newType(paramSymbol, callSiteParamType == null ? Type.UNKNOWN : callSiteParamType);
            }

            LOG.info("Initialized param " + paramSymbol);
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
                LOG.finest("Parameter " + ident + " could profit from specialization to " + paramSymbol.getSymbolType());
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
     * @param functionNode the function node for which to init scope vars
     */
    private void initFromPropertyMap(final FunctionNode functionNode) {
        // For a script, add scope symbols as defined in the property map
        assert functionNode.isProgram();

        final PropertyMap map = Context.getGlobalMap();

        for (final Property property : map.getProperties()) {
            final String key    = property.getKey();
            final Symbol symbol = defineSymbol(functionNode, key, IS_GLOBAL, null);
            newType(symbol, Type.OBJECT);
            LOG.info("Added global symbol from property map " + symbol);
        }
    }

    private static void ensureTypeNotUnknown(final Node node) {

        final Symbol symbol = node.getSymbol();

        LOG.info("Ensure type not unknown for: " + symbol);

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
        return newInternal(getCurrentFunctionNode().uniqueName(EXCEPTION_PREFIX.tag()), Type.typeFor(ECMAException.class));
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
                final Node index = indexNode.getIndex();
                index.getSymbol().setNeedsSlot(!index.getSymbol().isConstant());
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
                        LOG.fine("Had to post pass widen '" + node + "' " + Debug.id(node) + " from " + node.getType() + " to " + to);
                        newType(node.getSymbol(), to);
                        changed.add(node);
                    }
                }

                @Override
                public Node enterFunctionNode(final FunctionNode node) {
                    return node.isLazy() ? null : node;
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
        newTemporary(type, binaryNode);
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
        newTemporary(destType, binaryNode); //for OP= nodes, the node can carry a narrower types than its lhs rhs. This is perfectly fine

        ensureAssignmentSlots(getCurrentFunctionNode(), binaryNode);

        end(binaryNode);
        return binaryNode;
    }

    private static boolean isFunctionExpressionSelfReference(final Symbol symbol) {
        if (symbol.isVar() && symbol.getNode() == symbol.getBlock() && symbol.getNode() instanceof FunctionNode) {
            return ((FunctionNode)symbol.getNode()).getIdent().getName().equals(symbol.getName());
        }
        return false;
    }

    private static Symbol newTemporary(final FunctionNode functionNode, final Type type, final Node node) {
        LOG.info("New TEMPORARY added to " + functionNode.getName() + " type=" + type);
        return functionNode.newTemporary(type, node);
    }

    private Symbol newTemporary(final Type type, final Node node) {
        return newTemporary(getCurrentFunctionNode(), type, node);
    }

    private Symbol newInternal(final String name, final Type type) {
        final Symbol iter = defineSymbol(getCurrentFunctionNode(), name, IS_VAR | IS_INTERNAL, null);
        iter.setType(type); // NASHORN-73
        return iter;
    }

    private static void newType(final Symbol symbol, final Type type) {
        final Type oldType = symbol.getSymbolType();
        symbol.setType(type);

        if (symbol.getSymbolType() != oldType) {
            LOG.info("New TYPE " + type + " for " + symbol + " (was " + oldType + ")");
        }

        if (symbol.isParam()) {
            symbol.setType(type);
            LOG.info("Param type change " + symbol);
        }
    }

    private void clearLocalDefs() {
        localDefs = new HashSet<>();
    }

    private boolean isLocalDef(final String name) {
        return localDefs.contains(name);
    }

    private void addLocalDef(final String name) {
        LOG.info("Adding local def of symbol: '" + name + "'");
        localDefs.add(name);
    }

    private void removeLocalDef(final String name) {
        LOG.info("Removing local def of symbol: '" + name + "'");
        localDefs.remove(name);
    }

    private void clearLocalUses() {
        localUses = new HashSet<>();
    }

    private void addLocalUse(final String name) {
        LOG.info("Adding local use of symbol: '" + name + "'");
        localUses.add(name);
    }

    /**
     * Pessimistically promote all symbols in current function node to Object types
     * This is done when the function contains unevaluated black boxes such as
     * lazy sub-function nodes that have not been compiled.
     *
     * @param functionNode function node in whose scope symbols should conservatively be made objects
     */
    private static void objectifySymbols(final FunctionNode functionNode) {
        functionNode.accept(new NodeVisitor() {
            private void toObject(final Block block) {
                for (final Iterator<Symbol> iter = block.symbolIterator(); iter.hasNext();) {
                    final Symbol symbol = iter.next();
                    newType(symbol, Type.OBJECT);
                }
            }

            @Override
            public Node enterBlock(final Block block) {
                toObject(block);
                return block;
            }

            @Override
            public Node enterFunctionNode(final FunctionNode node) {
                toObject(node);
                if (node.isLazy()) {
                    return null;
                }
                return node;
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

    private Node start(final Node node) {
        return start(node, true);
    }

    private Node start(final Node node, final boolean printNode) {
        if (DEBUG) {
            final StringBuilder sb = new StringBuilder();

            sb.append("[ENTER ").
                append(name(node)).
                append("] ").
                append(printNode ? node.toString() : "").
                append(" in '").
                append(getCurrentFunctionNode().getName()).
                append("'");
            LOG.info(sb.toString());
            LOG.indent();
        }

        return node;
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
                append(getCurrentFunctionNode().getName());

            if (node.getSymbol() == null) {
                sb.append(" <NO SYMBOL>");
            } else {
                sb.append(" <symbol=").append(node.getSymbol()).append('>');
            }

            LOG.unindent();
            LOG.info(sb.toString());
        }

        return node;
    }
}
