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

import static jdk.nashorn.internal.codegen.ClassEmitter.Flag.PRIVATE;
import static jdk.nashorn.internal.codegen.ClassEmitter.Flag.STATIC;
import static jdk.nashorn.internal.codegen.CompilerConstants.ARGUMENTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.CALLEE;
import static jdk.nashorn.internal.codegen.CompilerConstants.CREATE_PROGRAM_FUNCTION;
import static jdk.nashorn.internal.codegen.CompilerConstants.GET_MAP;
import static jdk.nashorn.internal.codegen.CompilerConstants.GET_STRING;
import static jdk.nashorn.internal.codegen.CompilerConstants.QUICK_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.REGEX_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SPLIT_ARRAY_ARG;
import static jdk.nashorn.internal.codegen.CompilerConstants.SPLIT_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.codegen.CompilerConstants.constructorNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.interfaceCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.methodDescriptor;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.typeDescriptor;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.OBJECT_FIELDS_ONLY;
import static jdk.nashorn.internal.ir.Symbol.IS_INTERNAL;
import static jdk.nashorn.internal.ir.Symbol.IS_TEMP;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_APPLY_TO_CALL;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_FAST_SCOPE;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_OPTIMISTIC;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_PROGRAM_POINT_SHIFT;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_SCOPE;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.TreeMap;

import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.RuntimeCallSite.SpecializedRuntimeNode;
import jdk.nashorn.internal.codegen.types.ArrayType;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.BreakableNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LexicalContextNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode.ArrayUnit;
import jdk.nashorn.internal.ir.LoopNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.objects.ScriptFunctionImpl;
import jdk.nashorn.internal.parser.Lexer.RegexToken;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.OptimisticReturnFilters;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.RewriteException;
import jdk.nashorn.internal.runtime.Scope;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.UnwarrantedOptimismException;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.linker.LinkerCallSite;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * This is the lowest tier of the code generator. It takes lowered ASTs emitted
 * from Lower and emits Java byte code. The byte code emission logic is broken
 * out into MethodEmitter. MethodEmitter works internally with a type stack, and
 * keeps track of the contents of the byte code stack. This way we avoid a large
 * number of special cases on the form
 * <pre>
 * if (type == INT) {
 *     visitInsn(ILOAD, slot);
 * } else if (type == DOUBLE) {
 *     visitInsn(DOUBLE, slot);
 * }
 * </pre>
 * This quickly became apparent when the code generator was generalized to work
 * with all types, and not just numbers or objects.
 * <p>
 * The CodeGenerator visits nodes only once, tags them as resolved and emits
 * bytecode for them.
 */
@Logger(name="codegen")
final class CodeGenerator extends NodeOperatorVisitor<CodeGeneratorLexicalContext> implements Loggable {

    private static final Type SCOPE_TYPE = Type.typeFor(ScriptObject.class);

    private static final String GLOBAL_OBJECT = Type.getInternalName(Global.class);

    private static final String SCRIPTFUNCTION_IMPL_NAME = Type.getInternalName(ScriptFunctionImpl.class);
    private static final Type   SCRIPTFUNCTION_IMPL_TYPE   = Type.typeFor(ScriptFunction.class);

    private static final Call INIT_REWRITE_EXCEPTION = CompilerConstants.specialCallNoLookup(RewriteException.class,
            "<init>", void.class, UnwarrantedOptimismException.class, Object[].class, String[].class, ScriptObject.class);
    private static final Call INIT_REWRITE_EXCEPTION_REST_OF = CompilerConstants.specialCallNoLookup(RewriteException.class,
            "<init>", void.class, UnwarrantedOptimismException.class, Object[].class, String[].class, ScriptObject.class, int[].class);

    private static final Call ENSURE_INT = CompilerConstants.staticCallNoLookup(OptimisticReturnFilters.class,
            "ensureInt", int.class, Object.class, int.class);
    private static final Call ENSURE_LONG = CompilerConstants.staticCallNoLookup(OptimisticReturnFilters.class,
            "ensureLong", long.class, Object.class, int.class);
    private static final Call ENSURE_NUMBER = CompilerConstants.staticCallNoLookup(OptimisticReturnFilters.class,
            "ensureNumber", double.class, Object.class, int.class);

    /** Constant data & installation. The only reason the compiler keeps this is because it is assigned
     *  by reflection in class installation */
    private final Compiler compiler;

    /** Call site flags given to the code generator to be used for all generated call sites */
    private final int callSiteFlags;

    /** How many regexp fields have been emitted */
    private int regexFieldCount;

    /** Line number for last statement. If we encounter a new line number, line number bytecode information
     *  needs to be generated */
    private int lastLineNumber = -1;

    /** When should we stop caching regexp expressions in fields to limit bytecode size? */
    private static final int MAX_REGEX_FIELDS = 2 * 1024;

    /** Current method emitter */
    private MethodEmitter method;

    /** Current compile unit */
    private CompileUnit unit;

    private final DebugLogger log;

    /** From what size should we use spill instead of fields for JavaScript objects? */
    private static final int OBJECT_SPILL_THRESHOLD = Options.getIntProperty("nashorn.spill.threshold", 256);

    private final Set<String> emittedMethods = new HashSet<>();

    // Function Id -> ContinuationInfo. Used by compilation of rest-of function only.
    private final Map<Integer, ContinuationInfo> fnIdToContinuationInfo = new HashMap<>();

    private final Deque<Label> scopeEntryLabels = new ArrayDeque<>();

    private final Set<Integer> initializedFunctionIds = new HashSet<>();

    /**
     * Constructor.
     *
     * @param compiler
     */
    CodeGenerator(final Compiler compiler) {
        super(new CodeGeneratorLexicalContext());
        this.compiler      = compiler;
        this.callSiteFlags = compiler.getEnv()._callsite_flags;
        this.log           = initLogger(Global.instance());
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Global global) {
        return global.getLogger(this.getClass());
    }

    /**
     * Gets the call site flags, adding the strict flag if the current function
     * being generated is in strict mode
     *
     * @return the correct flags for a call site in the current function
     */
    int getCallSiteFlags() {
        return lc.getCurrentFunction().isStrict() ? callSiteFlags | CALLSITE_STRICT : callSiteFlags;
    }

    /**
     * For an optimistic call site, we need to tag the callsite optimistic and
     * encode the program point of the callsite into it
     *
     * @param node node that can be optimistic
     * @return
     */
    private int getCallSiteFlagsOptimistic(final Optimistic node) {
        int flags = getCallSiteFlags();
        if (node.isOptimistic()) {
            flags |= CALLSITE_OPTIMISTIC;
            flags |= node.getProgramPoint() << CALLSITE_PROGRAM_POINT_SHIFT; //encode program point in high bits
        }
        return flags;
    }

    private static boolean isOptimistic(final int flags) {
        return (flags & CALLSITE_OPTIMISTIC) != 0;
    }

    /**
     * Load an identity node
     *
     * @param identNode an identity node to load
     * @return the method generator used
     */
    private MethodEmitter loadIdent(final IdentNode identNode, final Type type) {
        final Symbol symbol = identNode.getSymbol();

        if (!symbol.isScope()) {
            assert symbol.hasSlot() || symbol.isParam();
            return method.load(symbol).convert(type);
        }

        // If this is either __FILE__, __DIR__, or __LINE__ then load the property initially as Object as we'd convert
        // it anyway for replaceLocationPropertyPlaceholder.
        final boolean isCompileTimePropertyName = identNode.isCompileTimePropertyName();

        assert identNode.getSymbol().isScope() : identNode + " is not in scope!";
        final int flags = CALLSITE_SCOPE | getCallSiteFlagsOptimistic(identNode);
        if (isFastScope(symbol)) {
            // Only generate shared scope getter for fast-scope symbols so we know we can dial in correct scope.
            if (symbol.getUseCount() > SharedScopeCall.FAST_SCOPE_GET_THRESHOLD && !isOptimisticOrRestOf()) {
                method.loadCompilerConstant(SCOPE);
                loadSharedScopeVar(type, symbol, flags);
            } else {
                loadFastScopeVar(identNode, type, flags, isCompileTimePropertyName);
            }
        } else {
            //slow scope load, we have no proto depth
            new OptimisticOperation() {
                @Override
                void loadStack() {
                    method.loadCompilerConstant(SCOPE);
                }
                @Override
                void consumeStack() {
                    dynamicGet(method, identNode, isCompileTimePropertyName ? Type.OBJECT : type, identNode.getName(), flags, identNode.isFunction());
                    if(isCompileTimePropertyName) {
                        replaceCompileTimeProperty(identNode, type);
                    }
                }
            }.emit(identNode, type);
        }

        return method;
    }

    private void replaceCompileTimeProperty(final IdentNode identNode, final Type type) {
        final String name = identNode.getSymbol().getName();
        if (CompilerConstants.__FILE__.name().equals(name)) {
            replaceCompileTimeProperty(identNode, type, getCurrentSource().getName());
        } else if (CompilerConstants.__DIR__.name().equals(name)) {
            replaceCompileTimeProperty(identNode, type, getCurrentSource().getBase());
        } else if (CompilerConstants.__LINE__.name().equals(name)) {
            replaceCompileTimeProperty(identNode, type, getCurrentSource().getLine(identNode.position()));
        }
    }

    /**
     * When an ident with name __FILE__, __DIR__, or __LINE__ is loaded, we'll try to look it up as any other
     * identifier. However, if it gets all the way up to the Global object, it will send back a special value that
     * represents a placeholder for these compile-time location properties. This method will generate code that loads
     * the value of the compile-time location property and then invokes a method in Global that will replace the
     * placeholder with the value. Effectively, if the symbol for these properties is defined anywhere in the lexical
     * scope, they take precedence, but if they aren't, then they resolve to the compile-time location property.
     * @param identNode the ident node
     * @param type the desired return type for the ident node
     * @param propertyValue the actual value of the property
     */
    private void replaceCompileTimeProperty(final IdentNode identNode, final Type type, final Object propertyValue) {
        assert method.peekType().isObject();
        if(propertyValue instanceof String) {
            method.load((String)propertyValue);
        } else if(propertyValue instanceof Integer) {
            method.load(((Integer)propertyValue).intValue());
            method.convert(Type.OBJECT);
        } else {
            throw new AssertionError();
        }
        globalReplaceLocationPropertyPlaceholder();
        convertOptimisticReturnValue(identNode, type);
    }

    private boolean isOptimisticOrRestOf() {
        return useOptimisticTypes() || compiler.getCompilationEnvironment().isCompileRestOf();
    }

    /**
     * Check if this symbol can be accessed directly with a putfield or getfield or dynamic load
     *
     * @param symbol symbol to check for fast scope
     * @return true if fast scope
     */
    private boolean isFastScope(final Symbol symbol) {
        if (!symbol.isScope()) {
            return false;
        }

        if (!lc.inDynamicScope()) {
            // If there's no with or eval in context, and the symbol is marked as scoped, it is fast scoped. Such a
            // symbol must either be global, or its defining block must need scope.
            assert symbol.isGlobal() || lc.getDefiningBlock(symbol).needsScope() : symbol.getName();
            return true;
        }

        if (symbol.isGlobal()) {
            // Shortcut: if there's a with or eval in context, globals can't be fast scoped
            return false;
        }

        // Otherwise, check if there's a dynamic scope between use of the symbol and its definition
        final String name = symbol.getName();
        boolean previousWasBlock = false;
        for (final Iterator<LexicalContextNode> it = lc.getAllNodes(); it.hasNext();) {
            final LexicalContextNode node = it.next();
            if (node instanceof Block) {
                // If this block defines the symbol, then we can fast scope the symbol.
                final Block block = (Block)node;
                if (block.getExistingSymbol(name) == symbol) {
                    assert block.needsScope();
                    return true;
                }
                previousWasBlock = true;
            } else {
                if (node instanceof WithNode && previousWasBlock || node instanceof FunctionNode && CodeGeneratorLexicalContext.isFunctionDynamicScope((FunctionNode)node)) {
                    // If we hit a scope that can have symbols introduced into it at run time before finding the defining
                    // block, the symbol can't be fast scoped. A WithNode only counts if we've immediately seen a block
                    // before - its block. Otherwise, we are currently processing the WithNode's expression, and that's
                    // obviously not subjected to introducing new symbols.
                    return false;
                }
                previousWasBlock = false;
            }
        }
        // Should've found the symbol defined in a block
        throw new AssertionError();
    }

    private MethodEmitter loadSharedScopeVar(final Type valueType, final Symbol symbol, final int flags) {
        assert !isOptimisticOrRestOf();
        if (isFastScope(symbol)) {
            method.load(getScopeProtoDepth(lc.getCurrentBlock(), symbol));
        } else {
            method.load(-1);
        }
        return lc.getScopeGet(unit, symbol, valueType, flags | CALLSITE_FAST_SCOPE).generateInvoke(method);
    }

    private MethodEmitter loadFastScopeVar(final IdentNode identNode, final Type type, final int flags, final boolean isCompileTimePropertyName) {
        return new OptimisticOperation() {
            @Override
            void loadStack() {
                method.loadCompilerConstant(SCOPE);
                loadFastScopeProto(identNode.getSymbol(), false);
            }
            @Override
            void consumeStack() {
                dynamicGet(method, identNode, isCompileTimePropertyName ? Type.OBJECT : type, identNode.getSymbol().getName(), flags | CALLSITE_FAST_SCOPE, identNode.isFunction());
                if (isCompileTimePropertyName) {
                    replaceCompileTimeProperty(identNode, type);
                }
            }
        }.emit(identNode, type);
    }

    private MethodEmitter storeFastScopeVar(final Symbol symbol, final int flags) {
        loadFastScopeProto(symbol, true);
        method.dynamicSet(symbol.getName(), flags | CALLSITE_FAST_SCOPE);
        return method;
    }

    private int getScopeProtoDepth(final Block startingBlock, final Symbol symbol) {
        //walk up the chain from startingblock and when we bump into the current function boundary, add the external
        //information.
        final FunctionNode fn   = lc.getCurrentFunction();
        final int          fnId = fn.getId();
        final int externalDepth = compiler.getCompilationEnvironment().getScriptFunctionData(fnId).getExternalSymbolDepth(symbol.getName());

        //count the number of scopes from this place to the start of the function

        final int internalDepth = FindScopeDepths.findInternalDepth(lc, fn, startingBlock, symbol);
        final int scopesToStart = FindScopeDepths.findScopesToStart(lc, fn, startingBlock);
        int depth = 0;
        if (internalDepth == -1) {
            depth = scopesToStart + externalDepth;
        } else {
            assert internalDepth <= scopesToStart;
            depth = internalDepth;
        }

        return depth;
    }

    private void loadFastScopeProto(final Symbol symbol, final boolean swap) {
        final int depth = getScopeProtoDepth(lc.getCurrentBlock(), symbol);
        assert depth != -1 : "Couldn't find scope depth for symbol " + symbol.getName() + " in " + lc.getCurrentFunction();
        if (depth > 0) {
            if (swap) {
                method.swap();
            }
            for (int i = 0; i < depth; i++) {
                method.invoke(ScriptObject.GET_PROTO);
            }
            if (swap) {
                method.swap();
            }
        }
    }

    /**
     * Generate code that loads this node to the stack. This method is only
     * public to be accessible from the maps sub package. Do not call externally
     *
     * @param node node to load
     *
     * @return the method emitter used
     */
    MethodEmitter load(final Expression node) {
        return load(node, node.hasType() ? node.getType() : null);
    }

    // Test whether conversion from source to target involves a call of ES 9.1 ToPrimitive
    // with possible side effects from calling an object's toString or valueOf methods.
    private static boolean noToPrimitiveConversion(final Type source, final Type target) {
        // Object to boolean conversion does not cause ToPrimitive call
        return source.isJSPrimitive() || !target.isJSPrimitive() || target.isBoolean();
    }

    MethodEmitter loadBinaryOperands(final Expression lhs, final Expression rhs, final Type type) {
        return loadBinaryOperands(lhs, rhs, type, false);
    }

    private MethodEmitter loadBinaryOperands(final Expression lhs, final Expression rhs, final Type type, final boolean baseAlreadyOnStack) {
        // ECMAScript 5.1 specification (sections 11.5-11.11 and 11.13) prescribes that when evaluating a binary
        // expression "LEFT op RIGHT", the order of operations must be: LOAD LEFT, LOAD RIGHT, CONVERT LEFT, CONVERT
        // RIGHT, EXECUTE OP. Unfortunately, doing it in this order defeats potential optimizations that arise when we
        // can combine a LOAD with a CONVERT operation (e.g. use a dynamic getter with the conversion target type as its
        // return value). What we do here is reorder LOAD RIGHT and CONVERT LEFT when possible; it is possible only when
        // we can prove that executing CONVERT LEFT can't have a side effect that changes the value of LOAD RIGHT.
        // Basically, if we know that either LEFT already is a primitive value, or does not have to be converted to
        // a primitive value, or RIGHT is an expression that loads without side effects, then we can do the
        // reordering and collapse LOAD/CONVERT into a single operation; otherwise we need to do the more costly
        // separate operations to preserve specification semantics.
        if (noToPrimitiveConversion(lhs.getType(), type) || rhs.isLocal()) {
            // Can reorder. Combine load and convert into single operations.
            load(lhs, type, baseAlreadyOnStack);
            load(rhs, type, false);
        } else {
            // Can't reorder. Load and convert separately.
            load(lhs, lhs.getType(), baseAlreadyOnStack);
            load(rhs, rhs.getType(), false);
            method.swap().convert(type).swap().convert(type);
        }

        return method;
    }

    MethodEmitter loadBinaryOperands(final BinaryNode node) {
        return loadBinaryOperands(node.lhs(), node.rhs(), node.getType(), false);
    }

    MethodEmitter load(final Expression node, final Type type) {
        return load(node, type, false);
    }

    private MethodEmitter load(final Expression node, final Type type, final boolean baseAlreadyOnStack) {
        final Symbol symbol = node.getSymbol();

        // If we lack symbols, we just generate what we see.
        if (symbol == null || type == null) {
            node.accept(this);
            return method;
        }

        assert !type.isUnknown();

        /*
         * The load may be of type IdentNode, e.g. "x", AccessNode, e.g. "x.y"
         * or IndexNode e.g. "x[y]". Both AccessNodes and IndexNodes are
         * BaseNodes and the logic for loading the base object is reused
         */
        final CodeGenerator codegen = this;

        node.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterIdentNode(final IdentNode identNode) {
                loadIdent(identNode, type);
                return false;
            }

            @Override
            public boolean enterAccessNode(final AccessNode accessNode) {
                new OptimisticOperation() {
                    @Override
                    void loadStack() {
                        if (!baseAlreadyOnStack) {
                            load(accessNode.getBase(), Type.OBJECT);
                        }
                        assert method.peekType().isObject();
                    }
                    @Override
                    void consumeStack() {
                        final int flags = getCallSiteFlagsOptimistic(accessNode);
                        dynamicGet(method, accessNode, type, accessNode.getProperty().getName(), flags, accessNode.isFunction());
                    }
                }.emit(accessNode, baseAlreadyOnStack ? 1 : 0);
                return false;
            }

            @Override
            public boolean enterIndexNode(final IndexNode indexNode) {
                new OptimisticOperation() {
                    @Override
                    void loadStack() {
                        if (!baseAlreadyOnStack) {
                            load(indexNode.getBase(), Type.OBJECT);
                            load(indexNode.getIndex());
                        }
                    }
                    @Override
                    void consumeStack() {
                        final int flags = getCallSiteFlagsOptimistic(indexNode);
                        dynamicGetIndex(method, indexNode, type, flags, indexNode.isFunction());
                    }
                }.emit(indexNode, baseAlreadyOnStack ? 2 : 0);
                return false;
            }

            @Override
            public boolean enterFunctionNode(final FunctionNode functionNode) {
                // function nodes will always leave a constructed function object on stack, no need to load the symbol
                // separately as in enterDefault()
                lc.pop(functionNode);
                functionNode.accept(codegen);
                // NOTE: functionNode.accept() will produce a different FunctionNode that we discard. This incidentally
                // doesn't cause problems as we're never touching FunctionNode again after it's visited here - codegen
                // is the last element in the compilation pipeline, the AST it produces is not used externally. So, we
                // re-push the original functionNode.
                lc.push(functionNode);
                method.convert(type);
                return false;
            }

            @Override
            public boolean enterCallNode(final CallNode callNode) {
                return codegen.enterCallNode(callNode, type);
            }

            @Override
            public boolean enterLiteralNode(final LiteralNode<?> literalNode) {
                return codegen.enterLiteralNode(literalNode, type);
            }

            @Override
            public boolean enterDefault(final Node otherNode) {
                final Node currentDiscard = codegen.lc.getCurrentDiscard();
                otherNode.accept(codegen); // generate code for whatever we are looking at.
                if(currentDiscard != otherNode) {
                    method.load(symbol); // load the final symbol to the stack (or nop if no slot, then result is already there)
                    assert method.peekType() != null;
                    method.convert(type);
                }
                return false;
            }
        });

        return method;
    }

    @Override
    public boolean enterAccessNode(final AccessNode accessNode) {
        load(accessNode);
        return false;
    }

    /**
     * Initialize a specific set of vars to undefined. This has to be done at
     * the start of each method for local variables that aren't passed as
     * parameters.
     *
     * @param symbols list of symbols.
     */
    private void initSymbols(final Iterable<Symbol> symbols) {
        final LinkedList<Symbol> numbers = new LinkedList<>();
        final LinkedList<Symbol> objects = new LinkedList<>();
        final boolean useOptimistic = useOptimisticTypes();

        for (final Symbol symbol : symbols) {
            /*
             * The following symbols are guaranteed to be defined and thus safe
             * from having undefined written to them: parameters internals this
             *
             * Otherwise we must, unless we perform control/escape analysis,
             * assign them undefined.
             */
            final boolean isInternal = symbol.isParam() || symbol.isInternal() || symbol.isThis();

            if (symbol.hasSlot()) {
                final Type type = symbol.getSymbolType();
                if (symbol.canBeUndefined() && !isInternal) {
                    if (type.isNumber()) {
                        numbers.add(symbol);
                    } else if (type.isObject()) {
                        objects.add(symbol);
                    } else {
                        throw new AssertionError("no potentially undefined narrower local vars than doubles are allowed: " + symbol + " in " + lc.getCurrentFunction());
                    }
                } else if(useOptimistic && !symbol.isAlwaysDefined()) {
                    method.loadForcedInitializer(type);
                    method.store(symbol);
                }
            }
        }

        initSymbols(numbers, Type.NUMBER);
        initSymbols(objects, Type.OBJECT);
    }

    private void initSymbols(final LinkedList<Symbol> symbols, final Type type) {
        final Iterator<Symbol> it = symbols.iterator();
        if(it.hasNext()) {
            method.loadUndefined(type);
            boolean hasNext;
            do {
                final Symbol symbol = it.next();
                hasNext = it.hasNext();
                if(hasNext) {
                    method.dup();
                }
                method.store(symbol);
            } while(hasNext);
        }
    }

    /**
     * Create symbol debug information.
     *
     * @param block block containing symbols.
     */
    private void symbolInfo(final Block block) {
        for (final Symbol symbol : block.getSymbols()) {
            if (symbol.hasSlot()) {
                method.localVariable(symbol, block.getEntryLabel(), block.getBreakLabel());
            }
        }
    }

    @Override
    public boolean enterBlock(final Block block) {
        if(lc.isFunctionBody() && emittedMethods.contains(lc.getCurrentFunction().getName())) {
            return false;
        }
        method.label(block.getEntryLabel());
        initLocals(block);

        return true;
    }

    private boolean useOptimisticTypes() {
        return !lc.inSplitNode() && compiler.getCompilationEnvironment().useOptimisticTypes();
    }

    @Override
    public Node leaveBlock(final Block block) {

        popBlockScope(block);
        lc.releaseBlockSlots(useOptimisticTypes());

        symbolInfo(block);
        return block;
    }

    private void popBlockScope(final Block block) {
        if(!block.needsScope() || lc.isFunctionBody()) {
            method.label(block.getBreakLabel());
            return;
        }

        final Label entry = scopeEntryLabels.pop();
        final Label afterCatchLabel;
        final Label recoveryLabel = new Label("block_popscope_catch");

        /* pop scope a la try-finally */
        if(block.isTerminal()) {
            // Block is terminal; there's no normal-flow path for popping the scope. Label current position as the end
            // of the try block, and mark after-catch to be the block's break label.
            final Label endTryLabel = new Label("block_popscope_end_try");
            method._try(entry, endTryLabel, recoveryLabel);
            method.label(endTryLabel);
            afterCatchLabel = block.getBreakLabel();
        } else {
            // Block is non-terminal; Label current position as the block's break label (as it'll need to execute the
            // scope popping when it gets here) and as the end of the try block. Mark after-catch with a new label.
            final Label endTryLabel = block.getBreakLabel();
            method._try(entry, endTryLabel, recoveryLabel);
            method.label(endTryLabel);
            popScope();
            afterCatchLabel = new Label("block_after_catch");
            method._goto(afterCatchLabel);
        }

        method._catch(recoveryLabel);
        popScope();
        method.athrow();
        method.label(afterCatchLabel);
    }

    private void popScope() {
        popScopes(1);
    }

    private void popScopesUntil(final LexicalContextNode until) {
        popScopes(lc.getScopeNestingLevelTo(until));
    }

    private void popScopes(final int count) {
        if(count == 0) {
            return;
        }
        assert count > 0; // together with count == 0 check, asserts nonnegative count
        assert method.hasScope();
        method.loadCompilerConstant(SCOPE);
        for(int i = 0; i < count; ++i) {
            method.invoke(ScriptObject.GET_PROTO);
        }
        method.storeCompilerConstant(SCOPE);
    }

    @Override
    public boolean enterBreakNode(final BreakNode breakNode) {
        enterStatement(breakNode);

        final BreakableNode breakFrom = lc.getBreakable(breakNode.getLabel());
        popScopesUntil(breakFrom);
        method.splitAwareGoto(lc, breakFrom.getBreakLabel());

        return false;
    }

    private int loadArgs(final List<Expression> args) {
        return loadArgs(args, args.size());
    }

    private int loadArgs(final List<Expression> args, final int argCount) {
        return loadArgs(args, null, false, argCount);
    }

    private int loadArgs(final List<Expression> args, final String signature, final boolean isVarArg, final int argCount) {
        // arg have already been converted to objects here.
        if (isVarArg || argCount > LinkerCallSite.ARGLIMIT) {
            loadArgsArray(args);
            return 1;
        }

        // pad with undefined if size is too short. argCount is the real number of args
        int n = 0;
        final Type[] params = signature == null ? null : Type.getMethodArguments(signature);
        for (final Expression arg : args) {
            assert arg != null;
            if (n >= argCount) {
                load(arg);
                method.pop(); // we had to load the arg for its side effects
            } else if (params != null) {
                load(arg, params[n]);
            } else {
                load(arg);
            }
            n++;
        }

        while (n < argCount) {
            method.loadUndefined(Type.OBJECT);
            n++;
        }

        return argCount;
    }


    @Override
    public boolean enterCallNode(final CallNode callNode) {
        return enterCallNode(callNode, callNode.getType());
    }

    private boolean enterCallNode(final CallNode callNode, final Type callNodeType) {
        lineNumber(callNode.getLineNumber());

        final List<Expression> args = callNode.getArgs();
        final Expression function = callNode.getFunction();
        final Block currentBlock = lc.getCurrentBlock();
        final CodeGeneratorLexicalContext codegenLexicalContext = lc;

        function.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {

            private MethodEmitter sharedScopeCall(final IdentNode identNode, final int flags) {
                final Symbol symbol = identNode.getSymbol();
                final boolean isFastScope = isFastScope(symbol);
                final int scopeCallFlags = flags | (isFastScope ? CALLSITE_FAST_SCOPE : 0);
                new OptimisticOperation() {
                    @Override
                    void loadStack() {
                        method.loadCompilerConstant(SCOPE);
                        if (isFastScope) {
                            method.load(getScopeProtoDepth(currentBlock, symbol));
                        } else {
                            method.load(-1); // Bypass fast-scope code in shared callsite
                        }
                        loadArgs(args);
                    }
                    @Override
                    void consumeStack() {
                        final Type[] paramTypes = method.getTypesFromStack(args.size());
                        final SharedScopeCall scopeCall = codegenLexicalContext.getScopeCall(unit, symbol, identNode.getType(), callNodeType, paramTypes, scopeCallFlags);
                        scopeCall.generateInvoke(method);
                    }
                }.emit(callNode);
                return method;
            }

            private void scopeCall(final IdentNode node, final int flags) {
                new OptimisticOperation() {
                    int argsCount;
                    @Override
                    void loadStack() {
                        load(node, Type.OBJECT); // foo() makes no sense if foo == 3
                        // ScriptFunction will see CALLSITE_SCOPE and will bind scope accordingly.
                        method.loadUndefined(Type.OBJECT); //the 'this'
                        argsCount = loadArgs(args);
                    }
                    @Override
                    void consumeStack() {
                        dynamicCall(method, callNode, callNodeType, 2 + argsCount, flags);
                    }
                }.emit(callNode);
            }

            private void evalCall(final IdentNode node, final int flags) {
                final Label invoke_direct_eval  = new Label("invoke_direct_eval");
                final Label is_not_eval  = new Label("is_not_eval");
                final Label eval_done = new Label("eval_done");

                new OptimisticOperation() {
                    int argsCount;
                    @Override
                    void loadStack() {
                        load(node, Type.OBJECT); // Type.OBJECT as foo() makes no sense if foo == 3
                        method.dup();
                        globalIsEval();
                        method.ifeq(is_not_eval);

                        // We don't need ScriptFunction object for 'eval'
                        method.pop();
                        // Load up self (scope).
                        method.loadCompilerConstant(SCOPE);
                        final CallNode.EvalArgs evalArgs = callNode.getEvalArgs();
                        // load evaluated code
                        load(evalArgs.getCode(), Type.OBJECT);
                        // load second and subsequent args for side-effect
                        final List<Expression> callArgs = callNode.getArgs();
                        final int numArgs = callArgs.size();
                        for (int i = 1; i < numArgs; i++) {
                            load(callArgs.get(i)).pop();
                        }
                        // special/extra 'eval' arguments
                        load(evalArgs.getThis());
                        method.load(evalArgs.getLocation());
                        method.load(evalArgs.getStrictMode());
                        method.convert(Type.OBJECT);
                        method._goto(invoke_direct_eval);

                        method.label(is_not_eval);
                        // This is some scope 'eval' or global eval replaced by user
                        // but not the built-in ECMAScript 'eval' function call
                        method.loadNull();
                        argsCount = loadArgs(callArgs);
                    }

                    @Override
                    void consumeStack() {
                        // Ordinary call
                        dynamicCall(method, callNode, callNodeType, 2 + argsCount, flags);
                        method._goto(eval_done);

                        method.label(invoke_direct_eval);
                        // direct call to Global.directEval
                        globalDirectEval();
                        convertOptimisticReturnValue(callNode, callNodeType);
                        method.convert(callNodeType);
                    }
                }.emit(callNode);

                method.label(eval_done);
            }

            @Override
            public boolean enterIdentNode(final IdentNode node) {
                final Symbol symbol = node.getSymbol();

                if (symbol.isScope()) {
                    final int flags = getCallSiteFlagsOptimistic(callNode) | CALLSITE_SCOPE;
                    final int useCount = symbol.getUseCount();

                    // Threshold for generating shared scope callsite is lower for fast scope symbols because we know
                    // we can dial in the correct scope. However, we also need to enable it for non-fast scopes to
                    // support huge scripts like mandreel.js.
                    if (callNode.isEval()) {
                        evalCall(node, flags);
                    } else if (useCount <= SharedScopeCall.FAST_SCOPE_CALL_THRESHOLD
                            || !isFastScope(symbol) && useCount <= SharedScopeCall.SLOW_SCOPE_CALL_THRESHOLD
                            || CodeGenerator.this.lc.inDynamicScope()
                            || isOptimisticOrRestOf()) {
                        scopeCall(node, flags);
                    } else {
                        sharedScopeCall(node, flags);
                    }
                    assert method.peekType().equals(callNodeType) : method.peekType() + "!=" + callNode.getType();
                } else {
                    enterDefault(node);
                }

                return false;
            }

            @Override
            public boolean enterAccessNode(final AccessNode node) {
                //check if this is an apply to call node. only real applies, that haven't been
                //shadowed from their way to the global scope counts

                //call nodes have program points.

                new OptimisticOperation() {
                    int argCount;
                    @Override
                    void loadStack() {
                        load(node.getBase(), Type.OBJECT);
                        method.dup();
                        // NOTE: not using a nested OptimisticOperation on this dynamicGet, as we expect to get back
                        // a callable object. Nobody in their right mind would optimistically type this call site.
                        assert !node.isOptimistic();
                        final int flags = getCallSiteFlags() | (callNode.isApplyToCall() ? CALLSITE_APPLY_TO_CALL : 0);
                        method.dynamicGet(node.getType(), node.getProperty().getName(), flags, true);
                        method.swap();
                        argCount = loadArgs(args);
                    }
                    @Override
                    void consumeStack() {
                        dynamicCall(method, callNode, callNodeType, 2 + argCount, getCallSiteFlagsOptimistic(callNode) | (callNode.isApplyToCall() ? CALLSITE_APPLY_TO_CALL : 0));
                    }
                }.emit(callNode);

                return false;
            }

            @Override
            public boolean enterFunctionNode(final FunctionNode origCallee) {
                new OptimisticOperation() {
                    FunctionNode callee;
                    int argsCount;
                    @Override
                    void loadStack() {
                        callee = (FunctionNode)origCallee.accept(CodeGenerator.this);
                        if (callee.isStrict()) { // "this" is undefined
                            method.loadUndefined(Type.OBJECT);
                        } else { // get global from scope (which is the self)
                            globalInstance();
                        }
                        argsCount = loadArgs(args);
                    }

                    @Override
                    void consumeStack() {
                        final int flags = getCallSiteFlagsOptimistic(callNode);
                        //assert callNodeType.equals(callee.getReturnType()) : callNodeType + " != " + callee.getReturnType();
                        dynamicCall(method, callNode, callNodeType, 2 + argsCount, flags);
                    }
                }.emit(callNode);
                method.convert(callNodeType);
                return false;
            }

            @Override
            public boolean enterIndexNode(final IndexNode node) {
                new OptimisticOperation() {
                    int argsCount;
                    @Override
                    void loadStack() {
                        load(node.getBase(), Type.OBJECT);
                        method.dup();
                        final Type indexType = node.getIndex().getType();
                        if (indexType.isObject() || indexType.isBoolean()) {
                            load(node.getIndex(), Type.OBJECT); //TODO
                        } else {
                            load(node.getIndex());
                        }
                        // NOTE: not using a nested OptimisticOperation on this dynamicGetIndex, as we expect to get
                        // back a callable object. Nobody in their right mind would optimistically type this call site.
                        assert !node.isOptimistic();
                        method.dynamicGetIndex(node.getType(), getCallSiteFlags(), true);
                        method.swap();
                        argsCount = loadArgs(args);
                    }
                    @Override
                    void consumeStack() {
                        final int flags = getCallSiteFlagsOptimistic(callNode);
                        dynamicCall(method, callNode, callNodeType, 2 + argsCount, flags);
                    }
                }.emit(callNode);
                return false;
            }

            @Override
            protected boolean enterDefault(final Node node) {
                new OptimisticOperation() {
                    int argsCount;
                    @Override
                    void loadStack() {
                        // Load up function.
                        load(function, Type.OBJECT); //TODO, e.g. booleans can be used as functions
                        method.loadUndefined(Type.OBJECT); // ScriptFunction will figure out the correct this when it sees CALLSITE_SCOPE
                        argsCount = loadArgs(args);
                        }
                        @Override
                        void consumeStack() {
                            final int flags = getCallSiteFlagsOptimistic(callNode) | CALLSITE_SCOPE;
                            dynamicCall(method, callNode, callNodeType, 2 + argsCount, flags);
                        }
                }.emit(callNode);
                return false;
            }
        });

        method.store(callNode.getSymbol());

        return false;
    }

    private void convertOptimisticReturnValue(final Optimistic expr, final Type desiredType) {
        if (expr.isOptimistic()) {
            final Type optimisticType = getOptimisticCoercedType(desiredType, (Expression)expr);
            if(!optimisticType.isObject()) {
                method.load(expr.getProgramPoint());
                if(optimisticType.isInteger()) {
                    method.invoke(ENSURE_INT);
                } else if(optimisticType.isLong()) {
                    method.invoke(ENSURE_LONG);
                } else if(optimisticType.isNumber()) {
                    method.invoke(ENSURE_NUMBER);
                } else {
                    throw new AssertionError(optimisticType);
                }
            }
        }
        method.convert(desiredType);
    }

    /**
     * Emits the correct dynamic getter code. Normally just delegates to method emitter, except when the target
     * expression is optimistic, and the desired type is narrower than the optimistic type. In that case, it'll emit a
     * dynamic getter with its original optimistic type, and explicitly insert a narrowing conversion. This way we can
     * preserve the optimism of the values even if they're subsequently immediately coerced into a narrower type. This
     * is beneficial because in this case we can still presume that since the original getter was optimistic, the
     * conversion has no side effects.
     * @param method the method emitter
     * @param expr the expression that is being loaded through the getter
     * @param desiredType the desired type for the loaded expression (coercible from its original type)
     * @param name the name of the property being get
     * @param flags call site flags
     * @param isMethod whether we're preferrably retrieving a function
     * @return the passed in method emitter
     */
    private static MethodEmitter dynamicGet(final MethodEmitter method, final Expression expr, final Type desiredType, final String name, final int flags, final boolean isMethod) {
        final int finalFlags = maybeRemoveOptimisticFlags(desiredType, flags);
        if(isOptimistic(finalFlags)) {
            return method.dynamicGet(getOptimisticCoercedType(desiredType, expr), name, finalFlags, isMethod).convert(desiredType);
        }
        return method.dynamicGet(desiredType, name, finalFlags, isMethod);
    }

    private static MethodEmitter dynamicGetIndex(final MethodEmitter method, final Expression expr, final Type desiredType, final int flags, final boolean isMethod) {
        final int finalFlags = maybeRemoveOptimisticFlags(desiredType, flags);
        if(isOptimistic(finalFlags)) {
            return method.dynamicGetIndex(getOptimisticCoercedType(desiredType, expr), finalFlags, isMethod).convert(desiredType);
        }
        return method.dynamicGetIndex(desiredType, finalFlags, isMethod);
    }

    private static MethodEmitter dynamicCall(final MethodEmitter method, final Expression expr, final Type desiredType, final int argCount, final int flags) {
        final int finalFlags = maybeRemoveOptimisticFlags(desiredType, flags);
        if (isOptimistic(finalFlags)) {
            return method.dynamicCall(getOptimisticCoercedType(desiredType, expr), argCount, finalFlags).convert(desiredType);
        }
        return method.dynamicCall(desiredType, argCount, finalFlags);
    }

    /**
     * Given an optimistic expression and a desired coercing type, returns the type that should be used as the return
     * type of the dynamic invocation that is emitted as the code for the expression load. If the coercing type is
     * either boolean or narrower than the expression's optimistic type, then the optimistic type is returned, otherwise
     * the coercing type. Note that if you use this method to determine the return type of the code for the expression,
     * you will need to add an explicit {@link MethodEmitter#convert(Type)} after it to make sure that any further
     * coercing is done into the final type in case the returned type here was the optimistic type. Effectively, this
     * method allows for moving the coercion into the optimistic type when it won't adversely affect the optimistic
     * evaluation semantics, and for preserving the optimistic type and doing a separate coercion when it would affect
     * it.
     * @param coercingType the type into which the expression will ultimately be coerced
     * @param optimisticExpr the optimistic expression that will be coerced after evaluation.
     * @return
     */
    private static Type getOptimisticCoercedType(final Type coercingType, final Expression optimisticExpr) {
        assert optimisticExpr instanceof Optimistic && ((Optimistic)optimisticExpr).isOptimistic();
        final Type optimisticType = optimisticExpr.getType();
        if(coercingType.isBoolean() || coercingType.narrowerThan(optimisticType)) {
            return optimisticType;
        }
        return coercingType;
    }

    /**
     * If given an object type, ensures that the flags have their optimism removed (object return valued expressions are
     * never optimistic).
     * @param type the return value type
     * @param flags original flags
     * @return either the original flags, or flags with optimism stripped, if the return value type is object
     */
    private static int maybeRemoveOptimisticFlags(final Type type, final int flags) {
        return type.isObject() ? nonOptimisticFlags(flags) : flags;
    }

    /**
     * Returns the flags with optimistic flag and program point removed.
     * @param flags the flags that need optimism stripped from them.
     * @return flags without optimism
     */
    static int nonOptimisticFlags(final int flags) {
        return flags & ~(CALLSITE_OPTIMISTIC | (-1 << CALLSITE_PROGRAM_POINT_SHIFT));
    }

    @Override
    public boolean enterContinueNode(final ContinueNode continueNode) {
        enterStatement(continueNode);

        final LoopNode continueTo = lc.getContinueTo(continueNode.getLabel());
        popScopesUntil(continueTo);
        method.splitAwareGoto(lc, continueTo.getContinueLabel());

        return false;
    }

    @Override
    public boolean enterEmptyNode(final EmptyNode emptyNode) {
        enterStatement(emptyNode);

        return false;
    }

    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        enterStatement(expressionStatement);

        final Expression expr = expressionStatement.getExpression();
        assert expr.isTokenType(TokenType.DISCARD);
        expr.accept(this);

        return false;
    }

    @Override
    public boolean enterBlockStatement(final BlockStatement blockStatement) {
        enterStatement(blockStatement);

        blockStatement.getBlock().accept(this);

        return false;
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        enterStatement(forNode);

        if (forNode.isForIn()) {
            enterForIn(forNode);
        } else {
            enterFor(forNode);
        }

        return false;
    }

    private void enterFor(final ForNode forNode) {
        final Expression init   = forNode.getInit();
        final Expression test   = forNode.getTest();
        final Block      body   = forNode.getBody();
        final Expression modify = forNode.getModify();

        if (init != null) {
            init.accept(this);
        }

        final Label loopLabel = new Label("loop");
        final Label testLabel = new Label("test");

        method._goto(testLabel);
        method.label(loopLabel);
        body.accept(this);
        method.label(forNode.getContinueLabel());

        lineNumber(forNode);

        if (!body.isTerminal() && modify != null) {
            load(modify);
        }

        method.label(testLabel);
        if (test != null) {
            new BranchOptimizer(this, method).execute(test, loopLabel, true);
        } else {
            method._goto(loopLabel);
        }

        method.label(forNode.getBreakLabel());
    }

    private void enterForIn(final ForNode forNode) {
        final Block body   = forNode.getBody();
        final Expression  modify = forNode.getModify();

        final Symbol iter      = forNode.getIterator();
        final Label  loopLabel = new Label("loop");

        final Expression init = forNode.getInit();

        load(modify, Type.OBJECT);
        method.invoke(forNode.isForEach() ? ScriptRuntime.TO_VALUE_ITERATOR : ScriptRuntime.TO_PROPERTY_ITERATOR);
        method.store(iter);
        method._goto(forNode.getContinueLabel());
        method.label(loopLabel);

        new Store<Expression>(init) {
            @Override
            protected void storeNonDiscard() {
                //empty
            }

            @Override
            protected void evaluate() {
                method.load(iter);
                method.invoke(interfaceCallNoLookup(Iterator.class, "next", Object.class));
            }
        }.store();

        body.accept(this);

        method.label(forNode.getContinueLabel());
        method.load(iter);
        method.invoke(interfaceCallNoLookup(Iterator.class, "hasNext", boolean.class));
        method.ifne(loopLabel);
        method.label(forNode.getBreakLabel());
    }

    /**
     * Initialize the slots in a frame to undefined.
     *
     * @param block block with local vars.
     */
    private void initLocals(final Block block) {
        lc.nextFreeSlot(block);

        final boolean isFunctionBody = lc.isFunctionBody();
        final FunctionNode function = lc.getCurrentFunction();
        if (isFunctionBody) {
            if (method.hasScope()) {
                if (function.needsParentScope()) {
                    method.loadCompilerConstant(CALLEE);
                    method.invoke(ScriptFunction.GET_SCOPE);
                } else {
                    assert function.hasScopeBlock();
                    method.loadNull();
                }
                method.storeCompilerConstant(SCOPE);
            }
            if (function.needsArguments()) {
                initArguments(function);
            }
            final Symbol returnSymbol = block.getExistingSymbol(RETURN.symbolName());
            if(returnSymbol.hasSlot() && useOptimisticTypes() &&
               // NOTE: a program that has no declared functions will assign ":return = UNDEFINED" first thing as it
               // starts to run, so we don't have to force initialize :return (see Lower.enterBlock()).
               !(function.isProgram() && !function.hasDeclaredFunctions()))
            {
                method.loadForcedInitializer(returnSymbol.getSymbolType());
                method.store(returnSymbol);
            }
        }

        /*
         * Determine if block needs scope, if not, just do initSymbols for this block.
         */
        if (block.needsScope()) {
            /*
             * Determine if function is varargs and consequently variables have to
             * be in the scope.
             */
            final boolean varsInScope = function.allVarsInScope();

            // TODO for LET we can do better: if *block* does not contain any eval/with, we don't need its vars in scope.

            final List<Symbol> localsToInitialize = new ArrayList<>();
            final boolean hasArguments = function.needsArguments();
            final List<MapTuple<Symbol>> tuples = new ArrayList<>();

            for (final Symbol symbol : block.getSymbols()) {
                if (symbol.isInternal() && !symbol.isThis()) {
                    if (symbol.hasSlot()) {
                        localsToInitialize.add(symbol);
                    }
                    continue;
                }

                if (symbol.isThis() || symbol.isTemp()) {
                    continue;
                }

                if (symbol.isVar()) {
                    assert !varsInScope || symbol.isScope();
                    if (varsInScope || symbol.isScope()) {
                        assert symbol.isScope()   : "scope for " + symbol + " should have been set in Lower already " + function.getName();
                        assert !symbol.hasSlot()  : "slot for " + symbol + " should have been removed in Lower already" + function.getName();
                        tuples.add(new MapTuple<Symbol>(symbol.getName(), symbol) {
                            //this tuple will not be put fielded, as it has no value, just a symbol
                            @Override
                            public boolean isPrimitive() {
                                return symbol.getSymbolType().isPrimitive();
                            }
                        });
                    } else {
                        assert symbol.hasSlot() : symbol + " should have a slot only, no scope";
                        localsToInitialize.add(symbol);
                    }
                } else if (symbol.isParam() && (varsInScope || hasArguments || symbol.isScope())) {
                    assert symbol.isScope()   : "scope for " + symbol + " should have been set in Lower already " + function.getName() + " varsInScope="+varsInScope+" hasArguments="+hasArguments+" symbol.isScope()=" + symbol.isScope();
                    assert !(hasArguments && symbol.hasSlot())  : "slot for " + symbol + " should have been removed in Lower already " + function.getName();
                    tuples.add(new MapTuple<Symbol>(symbol.getName(), symbol, hasArguments ? null : symbol) {
                        //this symbol will be put fielded, we can't initialize it as undefined with a known type
                        @Override
                        public Class<?> getValueType() {
                            return OBJECT_FIELDS_ONLY || value == null || value.getSymbolType().isBoolean() ? Object.class : value.getSymbolType().getTypeClass();
                            //return OBJECT_FIELDS_ONLY ? Object.class : symbol.getSymbolType().getTypeClass();
                        }
                    });
                }
            }

            // we may have locals that need to be initialized
            initSymbols(localsToInitialize);

            /*
             * Create a new object based on the symbols and values, generate
             * bootstrap code for object
             */
            new FieldObjectCreator<Symbol>(this, tuples, true, hasArguments) {
                @Override
                protected void loadValue(final Symbol value) {
                    method.load(value);
                }
            }.makeObject(method);
            // program function: merge scope into global
            if (isFunctionBody && function.isProgram()) {
                method.invoke(ScriptRuntime.MERGE_SCOPE);
            }

            method.storeCompilerConstant(SCOPE);
            if (!isFunctionBody) {
                // Function body doesn't need a try/catch to restore scope, as it'd be a dead store anyway. Allowing it
                // actually causes issues with UnwarrantedOptimismException handlers as ASM will sort this handler to
                // the top of the exception handler table, so it'll be triggered instead of the UOE handlers.
                final Label scopeEntryLabel = new Label("");
                scopeEntryLabels.push(scopeEntryLabel);
                method.label(scopeEntryLabel);
            }
        } else {
            // Since we don't have a scope, parameters didn't get assigned array indices by the FieldObjectCreator, so
            // we need to assign them separately here.
            int nextParam = 0;
            if (isFunctionBody && function.isVarArg()) {
                for (final IdentNode param : function.getParameters()) {
                    param.getSymbol().setFieldIndex(nextParam++);
                }
            }

            initSymbols(block.getSymbols());
        }

        // Debugging: print symbols? @see --print-symbols flag
        printSymbols(block, (isFunctionBody ? "Function " : "Block in ") + (function.getIdent() == null ? "<anonymous>" : function.getIdent().getName()));
    }

    private void initArguments(final FunctionNode function) {
        method.loadCompilerConstant(VARARGS);
        if (function.needsCallee()) {
            method.loadCompilerConstant(CALLEE);
        } else {
            // If function is strict mode, "arguments.callee" is not populated, so we don't necessarily need the
            // caller.
            assert function.isStrict();
            method.loadNull();
        }
        method.load(function.getParameters().size());
        globalAllocateArguments();
        method.storeCompilerConstant(ARGUMENTS);
    }

    /**
     * Should this code generator skip generating code for inner functions? If lazy compilation is on, or we're
     * doing an on-demand ("just-in-time") compilation, then we aren't generating code for inner functions.
     */
    private boolean compileOutermostOnly() {
        return RecompilableScriptFunctionData.LAZY_COMPILATION || compiler.getCompilationEnvironment().isOnDemandCompilation();
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        final int fnId = functionNode.getId();

        if (compileOutermostOnly() && lc.getOutermostFunction() != functionNode) {
            // In case we are not generating code for the function, we must create or retrieve the function object and
            // load it on the stack here.
            newFunctionObject(functionNode, false);
            return false;
        }

        final String fnName = functionNode.getName();

        // NOTE: we only emit the method for a function with the given name once. We can have multiple functions with
        // the same name as a result of inlining finally blocks. However, in the future -- with type specialization,
        // notably -- we might need to check for both name *and* signature. Of course, even that might not be
        // sufficient; the function might have a code dependency on the type of the variables in its enclosing scopes,
        // and the type of such a variable can be different in catch and finally blocks. So, in the future we will have
        // to decide to either generate a unique method for each inlined copy of the function, maybe figure out its
        // exact type closure and deduplicate based on that, or just decide that functions in finally blocks aren't
        // worth it, and generate one method with most generic type closure.
        if (!emittedMethods.contains(fnName)) {
            log.info("=== BEGIN ", fnName);

            assert functionNode.getCompileUnit() != null : "no compile unit for " + fnName + " " + Debug.id(functionNode);
            unit = lc.pushCompileUnit(functionNode.getCompileUnit());
            assert lc.hasCompileUnits();

            final CompilationEnvironment compEnv = compiler.getCompilationEnvironment();
            final boolean isRestOf = compEnv.isCompileRestOf();
            final ClassEmitter classEmitter = unit.getClassEmitter();
            method = lc.pushMethodEmitter(isRestOf ? classEmitter.restOfMethod(functionNode) : classEmitter.method(functionNode));
            if(useOptimisticTypes()) {
                lc.pushUnwarrantedOptimismHandlers();
            }

            // new method - reset last line number
            lastLineNumber = -1;
            // Mark end for variable tables.
            method.begin();

            if (isRestOf) {
                final ContinuationInfo ci = new ContinuationInfo();
                fnIdToContinuationInfo.put(fnId, ci);
                method._goto(ci.getHandlerLabel());
            }
        }

        return true;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        try {
            final boolean markOptimistic;
            if (emittedMethods.add(functionNode.getName())) {
                markOptimistic = generateUnwarrantedOptimismExceptionHandlers(functionNode);
                generateContinuationHandler();
                method.end(); // wrap up this method
                unit   = lc.popCompileUnit(functionNode.getCompileUnit());
                method = lc.popMethodEmitter(method);
                log.info("=== END ", functionNode.getName());
            } else {
                markOptimistic = false;
            }

            FunctionNode newFunctionNode = functionNode.setState(lc, CompilationState.EMITTED);
            if (markOptimistic) {
                newFunctionNode = newFunctionNode.setFlag(lc, FunctionNode.IS_OPTIMISTIC);
            }

            newFunctionObject(newFunctionNode, true);

            return newFunctionNode;
        } catch (final Throwable t) {
            Context.printStackTrace(t);
            final VerifyError e = new VerifyError("Code generation bug in \"" + functionNode.getName() + "\": likely stack misaligned: " + t + " " + functionNode.getSource().getName());
            e.initCause(t);
            throw e;
        }
    }

    @Override
    public boolean enterIdentNode(final IdentNode identNode) {
        return false;
    }

    @Override
    public boolean enterIfNode(final IfNode ifNode) {
        enterStatement(ifNode);

        final Expression test = ifNode.getTest();
        final Block pass = ifNode.getPass();
        final Block fail = ifNode.getFail();

        final Label failLabel  = new Label("if_fail");
        final Label afterLabel = fail == null ? failLabel : new Label("if_done");

        new BranchOptimizer(this, method).execute(test, failLabel, false);

        boolean passTerminal = false;
        boolean failTerminal = false;

        pass.accept(this);
        if (!pass.hasTerminalFlags()) {
            method._goto(afterLabel); //don't fallthru to fail block
        } else {
            passTerminal = pass.isTerminal();
        }

        if (fail != null) {
            method.label(failLabel);
            fail.accept(this);
            failTerminal = fail.isTerminal();
        }

        //if if terminates, put the after label there
        if (!passTerminal || !failTerminal) {
            method.label(afterLabel);
        }

        return false;
    }

    @Override
    public boolean enterIndexNode(final IndexNode indexNode) {
        load(indexNode);
        return false;
    }

    private void enterStatement(final Statement statement) {
        lineNumber(statement);
    }

    private void lineNumber(final Statement statement) {
        lineNumber(statement.getLineNumber());
    }

    private void lineNumber(final int lineNumber) {
        if (lineNumber != lastLineNumber) {
            method.lineNumber(lineNumber);
        }
        lastLineNumber = lineNumber;
    }

    /**
     * Load a list of nodes as an array of a specific type
     * The array will contain the visited nodes.
     *
     * @param arrayLiteralNode the array of contents
     * @param arrayType        the type of the array, e.g. ARRAY_NUMBER or ARRAY_OBJECT
     *
     * @return the method generator that was used
     */
    private MethodEmitter loadArray(final ArrayLiteralNode arrayLiteralNode, final ArrayType arrayType) {
        assert arrayType == Type.INT_ARRAY || arrayType == Type.LONG_ARRAY || arrayType == Type.NUMBER_ARRAY || arrayType == Type.OBJECT_ARRAY;

        final Expression[]    nodes    = arrayLiteralNode.getValue();
        final Object          presets  = arrayLiteralNode.getPresets();
        final int[]           postsets = arrayLiteralNode.getPostsets();
        final Class<?>        type     = arrayType.getTypeClass();
        final List<ArrayUnit> units    = arrayLiteralNode.getUnits();

        loadConstant(presets);

        final Type elementType = arrayType.getElementType();

        if (units != null) {
            lc.enterSplitNode();
            final MethodEmitter savedMethod     = method;
            final FunctionNode  currentFunction = lc.getCurrentFunction();

            for (final ArrayUnit arrayUnit : units) {
                unit = lc.pushCompileUnit(arrayUnit.getCompileUnit());

                final String className = unit.getUnitClassName();
                final String name      = currentFunction.uniqueName(SPLIT_PREFIX.symbolName());
                final String signature = methodDescriptor(type, ScriptFunction.class, Object.class, ScriptObject.class, type);

                final MethodEmitter me = unit.getClassEmitter().method(EnumSet.of(Flag.PUBLIC, Flag.STATIC), name, signature);
                method = lc.pushMethodEmitter(me);

                method.setFunctionNode(currentFunction);
                method.begin();

                fixScopeSlot(currentFunction);

                method.load(arrayType, SPLIT_ARRAY_ARG.slot());

                for (int i = arrayUnit.getLo(); i < arrayUnit.getHi(); i++) {
                    storeElement(nodes, elementType, postsets[i]);
                }

                method._return();
                method.end();
                method = lc.popMethodEmitter(me);

                assert method == savedMethod;
                method.loadCompilerConstant(CALLEE);
                method.swap();
                method.loadCompilerConstant(THIS);
                method.swap();
                method.loadCompilerConstant(SCOPE);
                method.swap();
                method.invokestatic(className, name, signature);

                unit = lc.popCompileUnit(unit);
            }
            lc.exitSplitNode();

            return method;
        }

        for (final int postset : postsets) {
            storeElement(nodes, elementType, postset);
        }

        return method;
    }

    private void storeElement(final Expression[] nodes, final Type elementType, final int index) {
        method.dup();
        method.load(index);

        final Expression element = nodes[index];

        if (element == null) {
            method.loadEmpty(elementType);
        } else {
            load(element, elementType);
        }

        method.arraystore();
    }

    private MethodEmitter loadArgsArray(final List<Expression> args) {
        final Object[] array = new Object[args.size()];
        loadConstant(array);

        for (int i = 0; i < args.size(); i++) {
            method.dup();
            method.load(i);
            load(args.get(i), Type.OBJECT); //has to be upcast to object or we fail
            method.arraystore();
        }

        return method;
    }

    /**
     * Load a constant from the constant array. This is only public to be callable from the objects
     * subpackage. Do not call directly.
     *
     * @param string string to load
     */
    void loadConstant(final String string) {
        final String       unitClassName = unit.getUnitClassName();
        final ClassEmitter classEmitter  = unit.getClassEmitter();
        final int          index         = compiler.getConstantData().add(string);

        method.load(index);
        method.invokestatic(unitClassName, GET_STRING.symbolName(), methodDescriptor(String.class, int.class));
        classEmitter.needGetConstantMethod(String.class);
    }

    /**
     * Load a constant from the constant array. This is only public to be callable from the objects
     * subpackage. Do not call directly.
     *
     * @param object object to load
     */
    void loadConstant(final Object object) {
        loadConstant(object, unit, method);
    }

    private void loadConstant(final Object object, final CompileUnit compileUnit, final MethodEmitter methodEmitter) {
        final String       unitClassName = compileUnit.getUnitClassName();
        final ClassEmitter classEmitter  = compileUnit.getClassEmitter();
        final int          index         = compiler.getConstantData().add(object);
        final Class<?>     cls           = object.getClass();

        if (cls == PropertyMap.class) {
            methodEmitter.load(index);
            methodEmitter.invokestatic(unitClassName, GET_MAP.symbolName(), methodDescriptor(PropertyMap.class, int.class));
            classEmitter.needGetConstantMethod(PropertyMap.class);
        } else if (cls.isArray()) {
            methodEmitter.load(index);
            final String methodName = ClassEmitter.getArrayMethodName(cls);
            methodEmitter.invokestatic(unitClassName, methodName, methodDescriptor(cls, int.class));
            classEmitter.needGetConstantMethod(cls);
        } else {
            methodEmitter.loadConstants().load(index).arrayload();
            if (object instanceof ArrayData) {
                // avoid cast to non-public ArrayData subclass
                methodEmitter.checkcast(ArrayData.class);
                methodEmitter.invoke(virtualCallNoLookup(ArrayData.class, "copy", ArrayData.class));
            } else if (cls != Object.class) {
                methodEmitter.checkcast(cls);
            }
        }
    }

    // literal values
    private MethodEmitter loadLiteral(final LiteralNode<?> node, final Type type) {
        final Object value = node.getValue();

        if (value == null) {
            method.loadNull();
        } else if (value instanceof Undefined) {
            method.loadUndefined(Type.OBJECT);
        } else if (value instanceof String) {
            final String string = (String)value;

            if (string.length() > MethodEmitter.LARGE_STRING_THRESHOLD / 3) { // 3 == max bytes per encoded char
                loadConstant(string);
            } else {
                method.load(string);
            }
        } else if (value instanceof RegexToken) {
            loadRegex((RegexToken)value);
        } else if (value instanceof Boolean) {
            method.load((Boolean)value);
        } else if (value instanceof Integer) {
            if(type.isEquivalentTo(Type.NUMBER)) {
                method.load(((Integer)value).doubleValue());
            } else if(type.isEquivalentTo(Type.LONG)) {
                method.load(((Integer)value).longValue());
            } else {
                method.load((Integer)value);
            }
        } else if (value instanceof Long) {
            if(type.isEquivalentTo(Type.NUMBER)) {
                method.load(((Long)value).doubleValue());
            } else {
                method.load((Long)value);
            }
        } else if (value instanceof Double) {
            method.load((Double)value);
        } else if (node instanceof ArrayLiteralNode) {
            final ArrayLiteralNode arrayLiteral = (ArrayLiteralNode)node;
            final ArrayType atype = arrayLiteral.getArrayType();
            loadArray(arrayLiteral, atype);
            globalAllocateArray(atype);
        } else {
            throw new UnsupportedOperationException("Unknown literal for " + node.getClass() + " " + value.getClass() + " " + value);
        }

        return method;
    }

    private MethodEmitter loadRegexToken(final RegexToken value) {
        method.load(value.getExpression());
        method.load(value.getOptions());
        return globalNewRegExp();
    }

    private MethodEmitter loadRegex(final RegexToken regexToken) {
        if (regexFieldCount > MAX_REGEX_FIELDS) {
            return loadRegexToken(regexToken);
        }
        // emit field
        final String       regexName    = lc.getCurrentFunction().uniqueName(REGEX_PREFIX.symbolName());
        final ClassEmitter classEmitter = unit.getClassEmitter();

        classEmitter.field(EnumSet.of(PRIVATE, STATIC), regexName, Object.class);
        regexFieldCount++;

        // get field, if null create new regex, finally clone regex object
        method.getStatic(unit.getUnitClassName(), regexName, typeDescriptor(Object.class));
        method.dup();
        final Label cachedLabel = new Label("cached");
        method.ifnonnull(cachedLabel);

        method.pop();
        loadRegexToken(regexToken);
        method.dup();
        method.putStatic(unit.getUnitClassName(), regexName, typeDescriptor(Object.class));

        method.label(cachedLabel);
        globalRegExpCopy();

        return method;
    }

    @Override
    public boolean enterLiteralNode(final LiteralNode<?> literalNode) {
        return enterLiteralNode(literalNode, literalNode.getType());
    }

    private boolean enterLiteralNode(final LiteralNode<?> literalNode, final Type type) {
        assert literalNode.getSymbol() != null : literalNode + " has no symbol";
        loadLiteral(literalNode, type).convert(type).store(literalNode.getSymbol());
        return false;
    }

    @Override
    public boolean enterObjectNode(final ObjectNode objectNode) {
        final List<PropertyNode> elements = objectNode.getElements();

        final List<MapTuple<Expression>> tuples = new ArrayList<>();
        final List<PropertyNode> gettersSetters = new ArrayList<>();
        Expression protoNode = null;

        boolean restOfProperty = false;
        final CompilationEnvironment env = compiler.getCompilationEnvironment();
        final int ccp = env.getCurrentContinuationEntryPoint();

        for (final PropertyNode propertyNode : elements) {
            final Expression   value  = propertyNode.getValue();
            final String       key    = propertyNode.getKeyName();
            final Symbol       symbol = value == null ? null : propertyNode.getKey().getSymbol();

            if (value == null) {
                gettersSetters.add(propertyNode);
            } else if (key.equals(ScriptObject.PROTO_PROPERTY_NAME)) {
                protoNode = value;
                continue;
            }

            restOfProperty |=
                value != null &&
                isValid(ccp) &&
                value instanceof Optimistic &&
                ((Optimistic)value).getProgramPoint() == ccp;

            //for literals, a value of null means object type, i.e. the value null or getter setter function
            //(I think)
            tuples.add(new MapTuple<Expression>(key, symbol, value) {
                @Override
                public Class<?> getValueType() {
                    return OBJECT_FIELDS_ONLY || value == null || value.getType().isBoolean() ? Object.class : value.getType().getTypeClass();
                }
            });
        }

        final ObjectCreator<?> oc;
        if (elements.size() > OBJECT_SPILL_THRESHOLD) {
            oc = new SpillObjectCreator(this, tuples);
        } else {
            oc = new FieldObjectCreator<Expression>(this, tuples) {
                @Override
                protected void loadValue(final Expression node) {
                    load(node);
                }};
        }
        oc.makeObject(method);
        //if this is a rest of method and our continuation point was found as one of the values
        //in the properties above, we need to reset the map to oc.getMap() in the continuation
        //handler
        if (restOfProperty) {
            getContinuationInfo().setObjectLiteralMap(oc.getMap());
        }

        method.dup();
        if (protoNode != null) {
            load(protoNode);
            method.invoke(ScriptObject.SET_PROTO_CHECK);
        } else {
            globalObjectPrototype();
            method.invoke(ScriptObject.SET_PROTO);
        }

        for (final PropertyNode propertyNode : gettersSetters) {
            final FunctionNode getter = propertyNode.getGetter();
            final FunctionNode setter = propertyNode.getSetter();

            assert getter != null || setter != null;

            method.dup().loadKey(propertyNode.getKey());
            if (getter == null) {
                method.loadNull();
            } else {
                getter.accept(this);
            }

            if (setter == null) {
                method.loadNull();
            } else {
                setter.accept(this);
            }

            method.invoke(ScriptObject.SET_USER_ACCESSORS);
        }

        method.store(objectNode.getSymbol());
        return false;
    }

    @Override
    public boolean enterReturnNode(final ReturnNode returnNode) {
        enterStatement(returnNode);

        method.registerReturn();

        final Type returnType = lc.getCurrentFunction().getReturnType();

        final Expression expression = returnNode.getExpression();
        if (expression != null) {
            load(expression);
        } else {
            method.loadUndefined(returnType);
        }

        method._return(returnType);

        return false;
    }

    private static boolean isNullLiteral(final Node node) {
        return node instanceof LiteralNode<?> && ((LiteralNode<?>) node).isNull();
    }


    private boolean undefinedCheck(final RuntimeNode runtimeNode, final List<Expression> args) {
        final Request request = runtimeNode.getRequest();

        if (!Request.isUndefinedCheck(request)) {
            return false;
        }

        final Expression lhs = args.get(0);
        final Expression rhs = args.get(1);

        final Symbol lhsSymbol = lhs.getSymbol();
        final Symbol rhsSymbol = rhs.getSymbol();

        final Symbol undefinedSymbol = "undefined".equals(lhsSymbol.getName()) ? lhsSymbol : rhsSymbol;
        final Expression expr = undefinedSymbol == lhsSymbol ? rhs : lhs;

        if (!undefinedSymbol.isScope()) {
            return false; //disallow undefined as local var or parameter
        }

        if (lhsSymbol == undefinedSymbol && lhs.getType().isPrimitive()) {
            //we load the undefined first. never mind, because this will deoptimize anyway
            return false;
        }

        if (compiler.getCompilationEnvironment().isCompileRestOf()) {
            return false;
        }

        //make sure that undefined has not been overridden or scoped as a local var
        //between us and global
        final CompilationEnvironment env = compiler.getCompilationEnvironment();
        if (!env.isGlobalSymbol(lc.getCurrentFunction(), "undefined")) {
            return false;
        }

        load(expr);

        if (expr.getType().isPrimitive()) {
            method.pop(); //throw away lhs, but it still needs to be evaluated for side effects, even if not in scope, as it can be optimistic
            method.load(request == Request.IS_NOT_UNDEFINED);
        } else {
            final Label isUndefined  = new Label("ud_check_true");
            final Label notUndefined = new Label("ud_check_false");
            final Label end          = new Label("end");
            method.loadUndefined(Type.OBJECT);
            method.if_acmpeq(isUndefined);
            method.label(notUndefined);
            method.load(request == Request.IS_NOT_UNDEFINED);
            method._goto(end);
            method.label(isUndefined);
            method.load(request == Request.IS_UNDEFINED);
            method.label(end);
        }

        method.store(runtimeNode.getSymbol());
        return true;
    }

    private boolean nullCheck(final RuntimeNode runtimeNode, final List<Expression> args) {
        final Request request = runtimeNode.getRequest();

        if (!Request.isEQ(request) && !Request.isNE(request)) {
            return false;
        }

        assert args.size() == 2 : "EQ or NE or TYPEOF need two args";

        Expression lhs = args.get(0);
        Expression rhs = args.get(1);

        if (isNullLiteral(lhs)) {
            final Expression tmp = lhs;
            lhs = rhs;
            rhs = tmp;
        }

        if (!isNullLiteral(rhs)) {
            return false;
        }

        if (!lhs.getType().isObject()) {
            return false;
        }

        // this is a null literal check, so if there is implicit coercion
        // involved like {D}x=null, we will fail - this is very rare
        final Label trueLabel  = new Label("trueLabel");
        final Label falseLabel = new Label("falseLabel");
        final Label endLabel   = new Label("end");

        load(lhs);    //lhs
        final Label popLabel;
        if (!Request.isStrict(request)) {
            method.dup(); //lhs lhs
            popLabel = new Label("pop");
        } else {
            popLabel = null;
        }

        if (Request.isEQ(request)) {
            method.ifnull(!Request.isStrict(request) ? popLabel : trueLabel);
            if (!Request.isStrict(request)) {
                method.loadUndefined(Type.OBJECT);
                method.if_acmpeq(trueLabel);
            }
            method.label(falseLabel);
            method.load(false);
            method._goto(endLabel);
            if (!Request.isStrict(request)) {
                method.label(popLabel);
                method.pop();
            }
            method.label(trueLabel);
            method.load(true);
            method.label(endLabel);
        } else if (Request.isNE(request)) {
            method.ifnull(!Request.isStrict(request) ? popLabel : falseLabel);
            if (!Request.isStrict(request)) {
                method.loadUndefined(Type.OBJECT);
                method.if_acmpeq(falseLabel);
            }
            method.label(trueLabel);
            method.load(true);
            method._goto(endLabel);
            if (!Request.isStrict(request)) {
                method.label(popLabel);
                method.pop();
            }
            method.label(falseLabel);
            method.load(false);
            method.label(endLabel);
        }

        assert runtimeNode.getType().isBoolean();
        method.convert(runtimeNode.getType());
        method.store(runtimeNode.getSymbol());

        return true;
    }

    private boolean specializationCheck(final RuntimeNode.Request request, final RuntimeNode node, final List<Expression> args) {
        if (!request.canSpecialize()) {
            return false;
        }

        assert args.size() == 2 : node;
        final Type returnType = node.getType();

        new OptimisticOperation() {
            private Request finalRequest = request;

            @Override
            void loadStack() {
                load(args.get(0));
                load(args.get(1));

                //if the request is a comparison, i.e. one that can be reversed
                //it keeps its semantic, but make sure that the object comes in
                //last
                final Request reverse = Request.reverse(request);
                if (method.peekType().isObject() && reverse != null) { //rhs is object
                    if (!method.peekType(1).isObject()) { //lhs is not object
                        method.swap(); //prefer object as lhs
                        finalRequest = reverse;
                    }
                }
            }
            @Override
            void consumeStack() {
                method.dynamicRuntimeCall(
                        new SpecializedRuntimeNode(
                            finalRequest,
                            new Type[] {
                                method.peekType(1),
                                method.peekType()
                            },
                            returnType).getInitialName(),
                        returnType,
                        finalRequest);

            }
        }.emit(node);

        method.convert(node.getType());
        method.store(node.getSymbol());

        return true;
    }

    private static boolean isReducible(final Request request) {
        return Request.isComparison(request) || request == Request.ADD;
    }

    @Override
    public boolean enterRuntimeNode(final RuntimeNode runtimeNode) {
        /*
         * First check if this should be something other than a runtime node
         * AccessSpecializer might have changed the type
         *
         * TODO - remove this - Access Specializer will always know after Attr/Lower
         */
        final List<Expression> args = new ArrayList<>(runtimeNode.getArgs());
        if (runtimeNode.isPrimitive() && !runtimeNode.isFinal() && isReducible(runtimeNode.getRequest())) {
            final Expression lhs = args.get(0);

            final Type   type   = runtimeNode.getType();
            final Symbol symbol = runtimeNode.getSymbol();

            switch (runtimeNode.getRequest()) {
            case EQ:
            case EQ_STRICT:
                return enterCmp(lhs, args.get(1), Condition.EQ, type, symbol);
            case NE:
            case NE_STRICT:
                return enterCmp(lhs, args.get(1), Condition.NE, type, symbol);
            case LE:
                return enterCmp(lhs, args.get(1), Condition.LE, type, symbol);
            case LT:
                return enterCmp(lhs, args.get(1), Condition.LT, type, symbol);
            case GE:
                return enterCmp(lhs, args.get(1), Condition.GE, type, symbol);
            case GT:
                return enterCmp(lhs, args.get(1), Condition.GT, type, symbol);
            case ADD:
                final Expression rhs = args.get(1);
                final Type widest = Type.widest(lhs.getType(), rhs.getType());
                new OptimisticOperation() {
                    @Override
                    void loadStack() {
                        load(lhs, widest);
                        load(rhs, widest);
                    }

                    @Override
                    void consumeStack() {
                        method.add(runtimeNode.getProgramPoint());
                    }
                }.emit(runtimeNode);
                method.convert(type);
                method.store(symbol);
                return false;
            default:
                // it's ok to send this one on with only primitive arguments, maybe INSTANCEOF(true, true) or similar
                // assert false : runtimeNode + " has all primitive arguments. This is an inconsistent state";
                break;
            }
        }

        if (nullCheck(runtimeNode, args)) {
           return false;
        }

        if (undefinedCheck(runtimeNode, args)) {
            return false;
        }

        final RuntimeNode newRuntimeNode;
        if (Request.isUndefinedCheck(runtimeNode.getRequest())) {
            newRuntimeNode = runtimeNode.setRequest(runtimeNode.getRequest() == Request.IS_UNDEFINED ? Request.EQ_STRICT : Request.NE_STRICT);
        } else {
            newRuntimeNode = runtimeNode;
        }

        if (!newRuntimeNode.isFinal() && specializationCheck(newRuntimeNode.getRequest(), newRuntimeNode, args)) {
           return false;
        }

        new OptimisticOperation() {
            @Override
            void loadStack() {
                for (final Expression arg : newRuntimeNode.getArgs()) {
                    load(arg, Type.OBJECT);
                }
            }
            @Override
            void consumeStack() {
                method.invokestatic(
                        CompilerConstants.className(ScriptRuntime.class),
                        newRuntimeNode.getRequest().toString(),
                        new FunctionSignature(
                            false,
                            false,
                            newRuntimeNode.getType(),
                            newRuntimeNode.getArgs().size()).toString());
            }
        }.emit(newRuntimeNode);

        method.convert(newRuntimeNode.getType());
        method.store(newRuntimeNode.getSymbol());

        return false;
    }

    @Override
    public boolean enterSplitNode(final SplitNode splitNode) {
        final CompileUnit splitCompileUnit = splitNode.getCompileUnit();

        final FunctionNode fn   = lc.getCurrentFunction();
        final String className  = splitCompileUnit.getUnitClassName();
        final String name       = splitNode.getName();

        final Class<?>   rtype          = fn.getReturnType().getTypeClass();
        final boolean    needsArguments = fn.needsArguments();
        final Class<?>[] ptypes         = needsArguments ?
                new Class<?>[] {ScriptFunction.class, Object.class, ScriptObject.class, ScriptObject.class} :
                new Class<?>[] {ScriptFunction.class, Object.class, ScriptObject.class};

        final MethodEmitter caller = method;
        unit = lc.pushCompileUnit(splitCompileUnit);

        final Call splitCall = staticCallNoLookup(
            className,
            name,
            methodDescriptor(rtype, ptypes));

        final MethodEmitter splitEmitter =
                splitCompileUnit.getClassEmitter().method(
                        splitNode,
                        name,
                        rtype,
                        ptypes);

        method = lc.pushMethodEmitter(splitEmitter);
        method.setFunctionNode(fn);

        assert fn.needsCallee() : "split function should require callee";
        caller.loadCompilerConstant(CALLEE);
        caller.loadCompilerConstant(THIS);
        caller.loadCompilerConstant(SCOPE);
        if (needsArguments) {
            caller.loadCompilerConstant(ARGUMENTS);
        }
        caller.invoke(splitCall);
        caller.storeCompilerConstant(RETURN);

        method.begin();
        // Copy scope to its target slot as first thing because the original slot could be used by return symbol.
        fixScopeSlot(fn);

        method.loadUndefined(fn.getReturnType());
        method.storeCompilerConstant(RETURN);

        return true;
    }

    private void fixScopeSlot(final FunctionNode functionNode) {
        // TODO hack to move the scope to the expected slot (needed because split methods reuse the same slots as the root method)
        if (functionNode.compilerConstant(SCOPE).getSlot() != SCOPE.slot()) {
            method.load(SCOPE_TYPE, SCOPE.slot());
            method.storeCompilerConstant(SCOPE);
        }
    }

    @Override
    public Node leaveSplitNode(final SplitNode splitNode) {
        assert method instanceof SplitMethodEmitter;
        final boolean     hasReturn = method.hasReturn();
        final List<Label> targets   = method.getExternalTargets();

        try {
            // Wrap up this method.

            method.loadCompilerConstant(RETURN);
            method._return(lc.getCurrentFunction().getReturnType());
            method.end();

            unit   = lc.popCompileUnit(splitNode.getCompileUnit());
            method = lc.popMethodEmitter(method);

        } catch (final Throwable t) {
            Context.printStackTrace(t);
            final VerifyError e = new VerifyError("Code generation bug in \"" + splitNode.getName() + "\": likely stack misaligned: " + t + " " + getCurrentSource().getName());
            e.initCause(t);
            throw e;
        }

        // Handle return from split method if there was one.
        final MethodEmitter caller = method;
        final int     targetCount = targets.size();

        //no external jump targets or return in switch node
        if (!hasReturn && targets.isEmpty()) {
            return splitNode;
        }

        caller.loadCompilerConstant(SCOPE);
        caller.checkcast(Scope.class);
        caller.invoke(Scope.GET_SPLIT_STATE);

        final Label breakLabel = new Label("no_split_state");
        // Split state is -1 for no split state, 0 for return, 1..n+1 for break/continue

        //the common case is that we don't need a switch
        if (targetCount == 0) {
            assert hasReturn;
            caller.ifne(breakLabel);
            //has to be zero
            caller.label(new Label("split_return"));
            caller.loadCompilerConstant(RETURN);
            caller._return(lc.getCurrentFunction().getReturnType());
            caller.label(breakLabel);
        } else {
            assert !targets.isEmpty();

            final int     low         = hasReturn ? 0 : 1;
            final int     labelCount  = targetCount + 1 - low;
            final Label[] labels      = new Label[labelCount];

            for (int i = 0; i < labelCount; i++) {
                labels[i] = new Label(i == 0 ? "split_return" : "split_" + targets.get(i - 1));
            }
            caller.tableswitch(low, targetCount, breakLabel, labels);
            for (int i = low; i <= targetCount; i++) {
                caller.label(labels[i - low]);
                if (i == 0) {
                    caller.loadCompilerConstant(RETURN);
                    caller._return(lc.getCurrentFunction().getReturnType());
                } else {
                    // Clear split state.
                    caller.loadCompilerConstant(SCOPE);
                    caller.checkcast(Scope.class);
                    caller.load(-1);
                    caller.invoke(Scope.SET_SPLIT_STATE);
                    caller.splitAwareGoto(lc, targets.get(i - 1));
                }
            }
            caller.label(breakLabel);
        }

        // If split has a return and caller is itself a split method it needs to propagate the return.
        if (hasReturn) {
            caller.setHasReturn();
        }

        return splitNode;
    }

    @Override
    public boolean enterSwitchNode(final SwitchNode switchNode) {
        enterStatement(switchNode);

        final Expression     expression  = switchNode.getExpression();
        final Symbol         tag         = switchNode.getTag();
        final boolean        allInteger  = tag.getSymbolType().isInteger();
        final List<CaseNode> cases       = switchNode.getCases();
        final CaseNode       defaultCase = switchNode.getDefaultCase();
        final Label          breakLabel  = switchNode.getBreakLabel();

        Label defaultLabel = breakLabel;
        boolean hasDefault = false;

        if (defaultCase != null) {
            defaultLabel = defaultCase.getEntry();
            hasDefault = true;
        }

        if (cases.isEmpty()) {
            // still evaluate expression for side-effects.
            load(expression).pop();
            method.label(breakLabel);
            return false;
        }

        if (allInteger) {
            // Tree for sorting values.
            final TreeMap<Integer, Label> tree = new TreeMap<>();

            // Build up sorted tree.
            for (final CaseNode caseNode : cases) {
                final Node test = caseNode.getTest();

                if (test != null) {
                    final Integer value = (Integer)((LiteralNode<?>)test).getValue();
                    final Label   entry = caseNode.getEntry();

                    // Take first duplicate.
                    if (!tree.containsKey(value)) {
                        tree.put(value, entry);
                    }
                }
            }

            // Copy values and labels to arrays.
            final int       size   = tree.size();
            final Integer[] values = tree.keySet().toArray(new Integer[size]);
            final Label[]   labels = tree.values().toArray(new Label[size]);

            // Discern low, high and range.
            final int lo    = values[0];
            final int hi    = values[size - 1];
            final int range = hi - lo + 1;

            // Find an unused value for default.
            int deflt = Integer.MIN_VALUE;
            for (final int value : values) {
                if (deflt == value) {
                    deflt++;
                } else if (deflt < value) {
                    break;
                }
            }

            // Load switch expression.
            load(expression);
            final Type type = expression.getType();

            // If expression not int see if we can convert, if not use deflt to trigger default.
            if (!type.isInteger()) {
                method.load(deflt);
                final Class<?> exprClass = type.getTypeClass();
                method.invoke(staticCallNoLookup(ScriptRuntime.class, "switchTagAsInt", int.class, exprClass.isPrimitive()? exprClass : Object.class, int.class));
            }

            // If reasonable size and not too sparse (80%), use table otherwise use lookup.
            if (range > 0 && range < 4096 && range <= size * 5 / 4) {
                final Label[] table = new Label[range];
                Arrays.fill(table, defaultLabel);
                for (int i = 0; i < size; i++) {
                    final int value = values[i];
                    table[value - lo] = labels[i];
                }

                method.tableswitch(lo, hi, defaultLabel, table);
            } else {
                final int[] ints = new int[size];
                for (int i = 0; i < size; i++) {
                    ints[i] = values[i];
                }

                method.lookupswitch(defaultLabel, ints, labels);
            }
        } else {
            load(expression, Type.OBJECT);
            method.store(tag);

            for (final CaseNode caseNode : cases) {
                final Expression test = caseNode.getTest();

                if (test != null) {
                    method.load(tag);
                    load(test, Type.OBJECT);
                    method.invoke(ScriptRuntime.EQ_STRICT);
                    method.ifne(caseNode.getEntry());
                }
            }

            method._goto(hasDefault ? defaultLabel : breakLabel);
        }

        for (final CaseNode caseNode : cases) {
            method.label(caseNode.getEntry());
            caseNode.getBody().accept(this);
        }

        if (!switchNode.isTerminal()) {
            method.label(breakLabel);
        }

        return false;
    }

    @Override
    public boolean enterThrowNode(final ThrowNode throwNode) {
        enterStatement(throwNode);

        if (throwNode.isSyntheticRethrow()) {
            //do not wrap whatever this is in an ecma exception, just rethrow it
            load(throwNode.getExpression());
            method.athrow();
            return false;
        }

        final Source     source     = getCurrentSource();
        final Expression expression = throwNode.getExpression();
        final int        position   = throwNode.position();
        final int        line       = throwNode.getLineNumber();
        final int        column     = source.getColumn(position);

        // NOTE: we first evaluate the expression, and only after it was evaluated do we create the new ECMAException
        // object and then somewhat cumbersomely move it beneath the evaluated expression on the stack. The reason for
        // this is that if expression is optimistic (or contains an optimistic subexpression), we'd potentially access
        // the not-yet-<init>ialized object on the stack from the UnwarrantedOptimismException handler, and bytecode
        // verifier forbids that.
        load(expression, Type.OBJECT);

        method.load(source.getName());
        method.load(line);
        method.load(column);
        method.invoke(ECMAException.CREATE);

        method.athrow();

        return false;
    }

    private Source getCurrentSource() {
        return lc.getCurrentFunction().getSource();
    }

    @Override
    public boolean enterTryNode(final TryNode tryNode) {
        enterStatement(tryNode);

        final Block       body        = tryNode.getBody();
        final List<Block> catchBlocks = tryNode.getCatchBlocks();
        final Symbol      symbol      = tryNode.getException();
        final Label       entry       = new Label("try");
        final Label       recovery    = new Label("catch");
        final Label       exit        = tryNode.getExit();
        final Label       skip        = new Label("skip");
        method.label(entry);

        body.accept(this);

        if (!body.hasTerminalFlags()) {
            method._goto(skip);
        }

        method._try(entry, exit, recovery, Throwable.class);
        method.label(exit);

        method._catch(recovery);
        method.store(symbol);

        final int catchBlockCount = catchBlocks.size();
        for (int i = 0; i < catchBlockCount; i++) {
            final Block catchBlock = catchBlocks.get(i);

            //TODO this is very ugly - try not to call enter/leave methods directly
            //better to use the implicit lexical context scoping given by the visitor's
            //accept method.
            lc.push(catchBlock);
            enterBlock(catchBlock);

            final CatchNode  catchNode          = (CatchNode)catchBlocks.get(i).getStatements().get(0);
            final IdentNode  exception          = catchNode.getException();
            final Expression exceptionCondition = catchNode.getExceptionCondition();
            final Block      catchBody          = catchNode.getBody();

            new Store<IdentNode>(exception) {
                @Override
                protected void storeNonDiscard() {
                    //empty
                }

                @Override
                protected void evaluate() {
                    if (catchNode.isSyntheticRethrow()) {
                        method.load(symbol);
                        return;
                    }
                    /*
                     * If caught object is an instance of ECMAException, then
                     * bind obj.thrown to the script catch var. Or else bind the
                     * caught object itself to the script catch var.
                     */
                    final Label notEcmaException = new Label("no_ecma_exception");
                    method.load(symbol).dup()._instanceof(ECMAException.class).ifeq(notEcmaException);
                    method.checkcast(ECMAException.class); //TODO is this necessary?
                    method.getField(ECMAException.THROWN);
                    method.label(notEcmaException);
                }
            }.store();

            final boolean isConditionalCatch = exceptionCondition != null;
            if (isConditionalCatch) {
                load(exceptionCondition, Type.BOOLEAN);
                // If catch body doesn't terminate the flow, then when we reach its break label, we could've come in
                // through either true or false branch, so we'll need a copy of the boolean evaluation on the stack to
                // know which path we took. On the other hand, if it does terminate the flow, then we won't have the
                // boolean on the top of the stack at the jump join point, so we must not push it on the stack.
                if(!catchBody.hasTerminalFlags()) {
                    method.dup();
                }
                method.ifeq(catchBlock.getBreakLabel());
            }

            catchBody.accept(this);

            leaveBlock(catchBlock);
            lc.pop(catchBlock);

            if(isConditionalCatch) {
                if(!catchBody.hasTerminalFlags()) {
                    // If it was executed, skip. Note the dup() above that left us this value on stack. On the other
                    // hand, if the catch body terminates the flow, we can reach here only if it was not executed, so
                    // IFEQ is implied.
                    method.ifne(skip);
                }
                if(i + 1 == catchBlockCount) {
                    // No next catch block - rethrow if condition failed
                    method.load(symbol).athrow();
                }
            } else {
                assert i + 1 == catchBlockCount;
            }
        }

        method.label(skip);

        // Finally body is always inlined elsewhere so it doesn't need to be emitted

        return false;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {

        final Expression init = varNode.getInit();

        if (init == null) {
            return false;
        }

        enterStatement(varNode);

        final IdentNode identNode = varNode.getName();
        final Symbol identSymbol = identNode.getSymbol();
        assert identSymbol != null : "variable node " + varNode + " requires a name with a symbol";

        assert method != null;

        final boolean needsScope = identSymbol.isScope();
        if (needsScope) {
            method.loadCompilerConstant(SCOPE);
        }

        if (needsScope) {
            load(init);
            final int flags = CALLSITE_SCOPE | getCallSiteFlags();
            if (isFastScope(identSymbol)) {
                storeFastScopeVar(identSymbol, flags);
            } else {
                method.dynamicSet(identNode.getName(), flags);
            }
        } else {
            load(init, identNode.getType());
            method.store(identSymbol);
        }

        return false;
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        final Expression test          = whileNode.getTest();
        final Block      body          = whileNode.getBody();
        final Label      breakLabel    = whileNode.getBreakLabel();
        final Label      continueLabel = whileNode.getContinueLabel();
        final boolean    isDoWhile     = whileNode.isDoWhile();
        final Label      loopLabel     = new Label("loop");

        if (!isDoWhile) {
            method._goto(continueLabel);
        }

        method.label(loopLabel);
        body.accept(this);
        if (!whileNode.isTerminal()) {
            method.label(continueLabel);
            enterStatement(whileNode);
            new BranchOptimizer(this, method).execute(test, loopLabel, true);
            method.label(breakLabel);
        }

        return false;
    }

    @Override
    public boolean enterWithNode(final WithNode withNode) {
        final Expression expression = withNode.getExpression();
        final Node       body       = withNode.getBody();

        // It is possible to have a "pathological" case where the with block does not reference *any* identifiers. It's
        // pointless, but legal. In that case, if nothing else in the method forced the assignment of a slot to the
        // scope object, its' possible that it won't have a slot assigned. In this case we'll only evaluate expression
        // for its side effect and visit the body, and not bother opening and closing a WithObject.
        final boolean hasScope = method.hasScope();

        if (hasScope) {
            method.loadCompilerConstant(SCOPE);
        }

        load(expression, Type.OBJECT);

        final Label tryLabel;
        if (hasScope) {
            // Construct a WithObject if we have a scope
            method.invoke(ScriptRuntime.OPEN_WITH);
            method.storeCompilerConstant(SCOPE);
            tryLabel = new Label("with_try");
            method.label(tryLabel);
        } else {
            // We just loaded the expression for its side effect and to check
            // for null or undefined value.
            globalCheckObjectCoercible();
            tryLabel = null;
        }

        // Always process body
        body.accept(this);

        if (hasScope) {
            // Ensure we always close the WithObject
            final Label endLabel   = new Label("with_end");
            final Label catchLabel = new Label("with_catch");
            final Label exitLabel  = new Label("with_exit");

            if (!body.isTerminal()) {
                popScope();
                method._goto(exitLabel);
            }

            method._try(tryLabel, endLabel, catchLabel);
            method.label(endLabel);

            method._catch(catchLabel);
            popScope();
            method.athrow();

            method.label(exitLabel);

        }
        return false;
    }

    @Override
    public boolean enterADD(final UnaryNode unaryNode) {
        load(unaryNode.getExpression(), unaryNode.getType());
        assert unaryNode.getType().isNumeric();
        method.store(unaryNode.getSymbol());
        return false;
    }

    @Override
    public boolean enterBIT_NOT(final UnaryNode unaryNode) {
        load(unaryNode.getExpression(), Type.INT).load(-1).xor().store(unaryNode.getSymbol());
        return false;
    }

    @Override
    public boolean enterDECINC(final UnaryNode unaryNode) {
        final Expression rhs         = unaryNode.getExpression();
        final Type       type        = unaryNode.getType();
        final TokenType  tokenType   = unaryNode.tokenType();
        final boolean    isPostfix   = tokenType == TokenType.DECPOSTFIX || tokenType == TokenType.INCPOSTFIX;
        final boolean    isIncrement = tokenType == TokenType.INCPREFIX || tokenType == TokenType.INCPOSTFIX;

        assert !type.isObject();

        new SelfModifyingStore<UnaryNode>(unaryNode, rhs) {

            private void loadRhs() {
                load(rhs, type, true);
            }

            @Override
            protected void evaluate() {
                if(isPostfix) {
                    loadRhs();
                } else {
                    new OptimisticOperation() {
                        @Override
                        void loadStack() {
                            loadRhs();
                            loadMinusOne();
                        }
                        @Override
                        void consumeStack() {
                            doDecInc();
                        }
                    }.emit(unaryNode, getOptimisticIgnoreCountForSelfModifyingExpression(rhs));
                }
            }

            @Override
            protected void storeNonDiscard() {
                super.storeNonDiscard();
                if (isPostfix) {
                    new OptimisticOperation() {
                        @Override
                        void loadStack() {
                            loadMinusOne();
                        }
                        @Override
                        void consumeStack() {
                            doDecInc();
                        }
                    }.emit(unaryNode, 1); // 1 for non-incremented result on the top of the stack pushed in evaluate()
                }
            }

            private void loadMinusOne() {
                if (type.isInteger()) {
                    method.load(isIncrement ? 1 : -1);
                } else if (type.isLong()) {
                    method.load(isIncrement ? 1L : -1L);
                } else {
                    method.load(isIncrement ? 1.0 : -1.0);
                }
            }

            private void doDecInc() {
                method.add(unaryNode.getProgramPoint());
            }
        }.store();

        return false;
    }

    private static int getOptimisticIgnoreCountForSelfModifyingExpression(final Expression target) {
        return target instanceof AccessNode ? 1 : target instanceof IndexNode ? 2 : 0;
    }

    @Override
    public boolean enterDISCARD(final UnaryNode unaryNode) {
        final Expression rhs = unaryNode.getExpression();

        lc.pushDiscard(rhs);
        load(rhs);

        if (lc.getCurrentDiscard() == rhs) {
            assert !rhs.isAssignment();
            method.pop();
            lc.popDiscard();
        }

        return false;
    }

    @Override
    public boolean enterNEW(final UnaryNode unaryNode) {
        final CallNode callNode = (CallNode)unaryNode.getExpression();
        final List<Expression> args   = callNode.getArgs();

        // Load function reference.
        load(callNode.getFunction(), Type.OBJECT); // must detect type error

        method.dynamicNew(1 + loadArgs(args), getCallSiteFlags());
        method.store(unaryNode.getSymbol());

        return false;
    }

    @Override
    public boolean enterNOT(final UnaryNode unaryNode) {
        final Expression rhs = unaryNode.getExpression();

        load(rhs, Type.BOOLEAN);

        final Label trueLabel  = new Label("true");
        final Label afterLabel = new Label("after");

        method.ifne(trueLabel);
        method.load(true);
        method._goto(afterLabel);
        method.label(trueLabel);
        method.load(false);
        method.label(afterLabel);
        method.store(unaryNode.getSymbol());

        return false;
    }

    @Override
    public boolean enterSUB(final UnaryNode unaryNode) {
        assert unaryNode.getType().isNumeric();
        new OptimisticOperation() {
            @Override
            void loadStack() {
                load(unaryNode.getExpression(), unaryNode.getType());
            }
            @Override
            void consumeStack() {
                method.neg(unaryNode.getProgramPoint());
            }
        }.emit(unaryNode);
        method.store(unaryNode.getSymbol());

        return false;
    }

    @Override
    public boolean enterVOID(final UnaryNode unaryNode) {
        load(unaryNode.getExpression()).pop();
        method.loadUndefined(Type.OBJECT);

        return false;
    }

    private void enterNumericAdd(final BinaryNode binaryNode, final Expression lhs, final Expression rhs, final Type type) {
        new OptimisticOperation() {
            @Override
            void loadStack() {
                loadBinaryOperands(lhs, rhs, type);
            }
            @Override
            void consumeStack() {
                method.add(binaryNode.getProgramPoint()); //if the symbol is optimistic, it always needs to be written, not on the stack?
           }
        }.emit(binaryNode);
        method.store(binaryNode.getSymbol());
    }

    @Override
    public boolean enterADD(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        final Type type = binaryNode.getType();
        if (type.isNumeric()) {
            enterNumericAdd(binaryNode, lhs, rhs, type);
        } else {
            loadBinaryOperands(binaryNode);
            method.add(INVALID_PROGRAM_POINT);
            method.store(binaryNode.getSymbol());
        }

        return false;
    }

    private boolean enterAND_OR(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        final Label skip = new Label("skip");

        load(lhs, Type.OBJECT).dup().convert(Type.BOOLEAN);

        if (binaryNode.tokenType() == TokenType.AND) {
            method.ifeq(skip);
        } else {
            method.ifne(skip);
        }

        method.pop();
        load(rhs, Type.OBJECT);
        method.label(skip);
        method.store(binaryNode.getSymbol());

        return false;
    }

    @Override
    public boolean enterAND(final BinaryNode binaryNode) {
        return enterAND_OR(binaryNode);
    }

    @Override
    public boolean enterASSIGN(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        final Type lhsType = lhs.getType();
        final Type rhsType = rhs.getType();

        if (!lhsType.isEquivalentTo(rhsType)) {
            //this is OK if scoped, only locals are wrong
        }

        new Store<BinaryNode>(binaryNode, lhs) {
            @Override
            protected void evaluate() {
                if (lhs instanceof IdentNode && !lhs.getSymbol().isScope()) {
                    load(rhs, lhsType);
                } else {
                    load(rhs);
                }
            }
        }.store();

        return false;
    }

    /**
     * Helper class for assignment ops, e.g. *=, += and so on..
     */
    private abstract class AssignOp extends SelfModifyingStore<BinaryNode> {

        /** The type of the resulting operation */
        private final Type opType;

        /**
         * Constructor
         *
         * @param node the assign op node
         */
        AssignOp(final BinaryNode node) {
            this(node.getType(), node);
        }

        /**
         * Constructor
         *
         * @param opType type of the computation - overriding the type of the node
         * @param node the assign op node
         */
        AssignOp(final Type opType, final BinaryNode node) {
            super(node, node.lhs());
            this.opType = opType;
        }

        protected abstract void op();

        @Override
        protected void evaluate() {
            final Expression lhs = assignNode.lhs();
            new OptimisticOperation() {
                @Override
                void loadStack() {
                    loadBinaryOperands(lhs, assignNode.rhs(), opType, true);
                }
                @Override
                void consumeStack() {
                    op();
                }
            }.emit(assignNode, getOptimisticIgnoreCountForSelfModifyingExpression(lhs));
            method.convert(assignNode.getType());
        }
    }

    @Override
    public boolean enterASSIGN_ADD(final BinaryNode binaryNode) {
        assert RuntimeNode.Request.ADD.canSpecialize();
        final Type lhsType = binaryNode.lhs().getType();
        final Type rhsType = binaryNode.rhs().getType();
        final boolean specialize = binaryNode.getType() == Type.OBJECT;

        new AssignOp(binaryNode) {

            @Override
            protected void op() {
                if (specialize) {
                    method.dynamicRuntimeCall(
                            new SpecializedRuntimeNode(
                                Request.ADD,
                                new Type[] {
                                    lhsType,
                                    rhsType,
                                },
                                Type.OBJECT).getInitialName(),
                            Type.OBJECT,
                            Request.ADD);
                } else {
                    method.add(binaryNode.getProgramPoint());
                }
            }

            @Override
            protected void evaluate() {
                super.evaluate();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_BIT_AND(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.and();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_BIT_OR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.or();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.xor();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_DIV(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.div(binaryNode.getProgramPoint());
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_MOD(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.rem();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_MUL(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.mul(binaryNode.getProgramPoint());
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_SAR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.sar();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_SHL(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.shl();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_SHR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.shr();
                method.convert(Type.LONG).load(JSType.MAX_UINT).and();
            }
        }.store();

        return false;
    }

    @Override
    public boolean enterASSIGN_SUB(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.sub(binaryNode.getProgramPoint());
            }
        }.store();

        return false;
    }

    /**
     * Helper class for binary arithmetic ops
     */
    private abstract class BinaryArith {

        protected abstract void op();

        protected void evaluate(final BinaryNode node) {
            new OptimisticOperation() {
                @Override
                void loadStack() {
                    loadBinaryOperands(node);
                }
                @Override
                void consumeStack() {
                    op();
                }
            }.emit(node);
            method.store(node.getSymbol());
        }
    }

    @Override
    public boolean enterBIT_AND(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.and();
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterBIT_OR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.or();
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterBIT_XOR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.xor();
            }
        }.evaluate(binaryNode);

        return false;
    }

    private boolean enterComma(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        assert lhs.isTokenType(TokenType.DISCARD);
        load(lhs);
        load(rhs);
        method.store(binaryNode.getSymbol());

        return false;
    }

    @Override
    public boolean enterCOMMARIGHT(final BinaryNode binaryNode) {
        return enterComma(binaryNode);
    }

    @Override
    public boolean enterCOMMALEFT(final BinaryNode binaryNode) {
        return enterComma(binaryNode);
    }

    @Override
    public boolean enterDIV(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.div(binaryNode.getProgramPoint());
            }
        }.evaluate(binaryNode);

        return false;
    }

    private boolean enterCmp(final Expression lhs, final Expression rhs, final Condition cond, final Type type, final Symbol symbol) {
        final Type lhsType = lhs.getType();
        final Type rhsType = rhs.getType();

        final Type widest = Type.widest(lhsType, rhsType);
        assert widest.isNumeric() || widest.isBoolean() : widest;

        loadBinaryOperands(lhs, rhs, widest);
        final Label trueLabel  = new Label("trueLabel");
        final Label afterLabel = new Label("skip");

        method.conditionalJump(cond, trueLabel);

        method.load(Boolean.FALSE);
        method._goto(afterLabel);
        method.label(trueLabel);
        method.load(Boolean.TRUE);
        method.label(afterLabel);

        method.convert(type);
        method.store(symbol);

        return false;
    }

    private boolean enterCmp(final BinaryNode binaryNode, final Condition cond) {
        return enterCmp(binaryNode.lhs(), binaryNode.rhs(), cond, binaryNode.getType(), binaryNode.getSymbol());
    }

    @Override
    public boolean enterEQ(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.EQ);
    }

    @Override
    public boolean enterEQ_STRICT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.EQ);
    }

    @Override
    public boolean enterGE(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.GE);
    }

    @Override
    public boolean enterGT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.GT);
    }

    @Override
    public boolean enterLE(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.LE);
    }

    @Override
    public boolean enterLT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.LT);
    }

    @Override
    public boolean enterMOD(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.rem();
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterMUL(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.mul(binaryNode.getProgramPoint());
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterNE(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.NE);
    }

    @Override
    public boolean enterNE_STRICT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.NE);
    }

    @Override
    public boolean enterOR(final BinaryNode binaryNode) {
        return enterAND_OR(binaryNode);
    }

    @Override
    public boolean enterSAR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.sar();
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterSHL(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.shl();
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterSHR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void evaluate(final BinaryNode node) {
                loadBinaryOperands(node.lhs(), node.rhs(), Type.INT);
                op();
                method.store(node.getSymbol());
            }
            @Override
            protected void op() {
                method.shr();
                method.convert(Type.LONG).load(JSType.MAX_UINT).and();
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterSUB(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.sub(binaryNode.getProgramPoint());
            }
        }.evaluate(binaryNode);

        return false;
    }

    @Override
    public boolean enterTernaryNode(final TernaryNode ternaryNode) {
        final Expression test      = ternaryNode.getTest();
        final Expression trueExpr  = ternaryNode.getTrueExpression();
        final Expression falseExpr = ternaryNode.getFalseExpression();

        final Symbol symbol     = ternaryNode.getSymbol();
        final Label  falseLabel = new Label("ternary_false");
        final Label  exitLabel  = new Label("ternary_exit");

        Type widest = Type.widest(ternaryNode.getType(), Type.widest(trueExpr.getType(), falseExpr.getType()));
        if (trueExpr.getType().isArray() || falseExpr.getType().isArray()) { //loadArray creates a Java array type on the stack, calls global allocate, which creates a native array type
            widest = Type.OBJECT;
        }

        load(test, Type.BOOLEAN);
        // we still keep the conversion here as the AccessSpecializer can have separated the types, e.g. var y = x ? x=55 : 17
        // will left as (Object)x=55 : (Object)17 by Lower. Then the first term can be {I}x=55 of type int, which breaks the
        // symmetry for the temporary slot for this TernaryNode. This is evidence that we assign types and explicit conversions
        // too early, or Apply the AccessSpecializer too late. We are mostly probably looking for a separate type pass to
        // do this property. Then we never need any conversions in CodeGenerator
        method.ifeq(falseLabel);
        load(trueExpr, widest);
        method._goto(exitLabel);
        method.label(falseLabel);
        load(falseExpr, widest);
        method.label(exitLabel);
        method.store(symbol);

        return false;
    }

    /**
     * Generate all shared scope calls generated during codegen.
     */
    void generateScopeCalls() {
        for (final SharedScopeCall scopeAccess : lc.getScopeCalls()) {
            scopeAccess.generateScopeCall();
        }
    }

    /**
     * Debug code used to print symbols
     *
     * @param block the block we are in
     * @param ident identifier for block or function where applicable
     */
    private void printSymbols(final Block block, final String ident) {
        if (!compiler.getEnv()._print_symbols) {
            return;
        }

        final PrintWriter out = compiler.getEnv().getErr();
        out.println("[BLOCK in '" + ident + "']");
        if (!block.printSymbols(out)) {
            out.println("<no symbols>");
        }
        out.println();
    }


    /**
     * The difference between a store and a self modifying store is that
     * the latter may load part of the target on the stack, e.g. the base
     * of an AccessNode or the base and index of an IndexNode. These are used
     * both as target and as an extra source. Previously it was problematic
     * for self modifying stores if the target/lhs didn't belong to one
     * of three trivial categories: IdentNode, AcessNodes, IndexNodes. In that
     * case it was evaluated and tagged as "resolved", which meant at the second
     * time the lhs of this store was read (e.g. in a = a (second) + b for a += b,
     * it would be evaluated to a nop in the scope and cause stack underflow
     *
     * see NASHORN-703
     *
     * @param <T>
     */
    private abstract class SelfModifyingStore<T extends Expression> extends Store<T> {
        protected SelfModifyingStore(final T assignNode, final Expression target) {
            super(assignNode, target);
        }

        @Override
        protected boolean isSelfModifying() {
            return true;
        }
    }

    /**
     * Helper class to generate stores
     */
    private abstract class Store<T extends Expression> {

        /** An assignment node, e.g. x += y */
        protected final T assignNode;

        /** The target node to store to, e.g. x */
        private final Expression target;

        /** How deep on the stack do the arguments go if this generates an indy call */
        private int depth;

        /** If we have too many arguments, we need temporary storage, this is stored in 'quick' */
        private Symbol quick;

        /**
         * Constructor
         *
         * @param assignNode the node representing the whole assignment
         * @param target     the target node of the assignment (destination)
         */
        protected Store(final T assignNode, final Expression target) {
            this.assignNode = assignNode;
            this.target = target;
        }

        /**
         * Constructor
         *
         * @param assignNode the node representing the whole assignment
         */
        protected Store(final T assignNode) {
            this(assignNode, assignNode);
        }

        /**
         * Is this a self modifying store operation, e.g. *= or ++
         * @return true if self modifying store
         */
        protected boolean isSelfModifying() {
            return false;
        }

        private void prologue() {
            final Symbol targetSymbol = target.getSymbol();
            final Symbol scopeSymbol  = lc.getCurrentFunction().compilerConstant(SCOPE);

            /**
             * This loads the parts of the target, e.g base and index. they are kept
             * on the stack throughout the store and used at the end to execute it
             */

            target.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                @Override
                public boolean enterIdentNode(final IdentNode node) {
                    if (targetSymbol.isScope()) {
                        method.load(scopeSymbol);
                        depth++;
                        assert depth == 1;
                    }
                    return false;
                }

                private void enterBaseNode() {
                    assert target instanceof BaseNode : "error - base node " + target + " must be instanceof BaseNode";
                    final BaseNode   baseNode = (BaseNode)target;
                    final Expression base     = baseNode.getBase();

                    load(base, Type.OBJECT);
                    depth += Type.OBJECT.getSlots();
                    assert depth == 1;

                    if (isSelfModifying()) {
                        method.dup();
                    }
                }

                @Override
                public boolean enterAccessNode(final AccessNode node) {
                    enterBaseNode();
                    return false;
                }

                @Override
                public boolean enterIndexNode(final IndexNode node) {
                    enterBaseNode();

                    final Expression index = node.getIndex();
                    if (!index.getType().isNumeric()) {
                        // could be boolean here as well
                        load(index, Type.OBJECT);
                    } else {
                        load(index);
                    }
                    depth += index.getType().getSlots();

                    if (isSelfModifying()) {
                        //convert "base base index" to "base index base index"
                        method.dup(1);
                    }

                    return false;
                }

            });
        }

        private Symbol quickSymbol(final Type type) {
            return quickSymbol(type, QUICK_PREFIX.symbolName());
        }

        /**
         * Quick symbol generates an extra local variable, always using the same
         * slot, one that is available after the end of the frame.
         *
         * @param type the type of the symbol
         * @param prefix the prefix for the variable name for the symbol
         *
         * @return the quick symbol
         */
        private Symbol quickSymbol(final Type type, final String prefix) {
            final String name = lc.getCurrentFunction().uniqueName(prefix);
            final Symbol symbol = new Symbol(name, IS_TEMP | IS_INTERNAL);

            symbol.setType(type);

            symbol.setSlot(lc.quickSlot(symbol));

            return symbol;
        }

        // store the result that "lives on" after the op, e.g. "i" in i++ postfix.
        protected void storeNonDiscard() {
            if (lc.getCurrentDiscard() == assignNode) {
                assert assignNode.isAssignment();
                lc.popDiscard();
                return;
            }

            final Symbol symbol = assignNode.getSymbol();
            if (symbol.hasSlot()) {
                method.dup().store(symbol);
                return;
            }

            if (method.dup(depth) == null) {
                method.dup();
                this.quick = quickSymbol(method.peekType());
                method.store(quick);
            }
        }

        private void epilogue() {
            /**
             * Take the original target args from the stack and use them
             * together with the value to be stored to emit the store code
             *
             * The case that targetSymbol is in scope (!hasSlot) and we actually
             * need to do a conversion on non-equivalent types exists, but is
             * very rare. See for example test/script/basic/access-specializer.js
             */
            target.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                @Override
                protected boolean enterDefault(final Node node) {
                    throw new AssertionError("Unexpected node " + node + " in store epilogue");
                }

                @Override
                public boolean enterIdentNode(final IdentNode node) {
                    final Symbol symbol = node.getSymbol();
                    assert symbol != null;
                    if (symbol.isScope()) {
                        final int flags = CALLSITE_SCOPE | getCallSiteFlags();
                        if (isFastScope(symbol)) {
                            storeFastScopeVar(symbol, flags);
                        } else {
                            method.dynamicSet(node.getName(), flags);
                        }
                    } else {
                        method.convert(node.getType());
                        method.store(symbol);
                    }
                    return false;

                }

                @Override
                public boolean enterAccessNode(final AccessNode node) {
                    method.dynamicSet(node.getProperty().getName(), getCallSiteFlags());
                    return false;
                }

                @Override
                public boolean enterIndexNode(final IndexNode node) {
                    method.dynamicSetIndex(getCallSiteFlags());
                    return false;
                }
            });


            // whatever is on the stack now is the final answer
        }

        protected abstract void evaluate();

        void store() {
            prologue();
            evaluate(); // leaves an operation of whatever the operationType was on the stack
            storeNonDiscard();
            epilogue();
            if (quick != null) {
                method.load(quick);
            }
        }
    }

    private void newFunctionObject(final FunctionNode functionNode, final boolean addInitializer) {
        assert lc.peek() == functionNode;

        final int fnId = functionNode.getId();
        final CompilationEnvironment env = compiler.getCompilationEnvironment();
        final RecompilableScriptFunctionData data = env.getScriptFunctionData(fnId);

        assert data != null : functionNode.getName() + " has no data";

        final FunctionNode parentFn = lc.getParentFunction(functionNode);
        if (parentFn == null && functionNode.isProgram()) {
            final CompileUnit fnUnit = functionNode.getCompileUnit();
            final MethodEmitter createFunction = fnUnit.getClassEmitter().method(
                    EnumSet.of(Flag.PUBLIC, Flag.STATIC), CREATE_PROGRAM_FUNCTION.symbolName(),
                    ScriptFunction.class, ScriptObject.class);
            createFunction.begin();
            createFunction._new(SCRIPTFUNCTION_IMPL_NAME, SCRIPTFUNCTION_IMPL_TYPE).dup();
            loadConstant(data, fnUnit, createFunction);
            createFunction.load(SCOPE_TYPE, 0);
            createFunction.invoke(constructorNoLookup(SCRIPTFUNCTION_IMPL_NAME, RecompilableScriptFunctionData.class, ScriptObject.class));
            createFunction._return();
            createFunction.end();
        }

        if (addInitializer && !initializedFunctionIds.contains(fnId) && !env.isOnDemandCompilation()) {
            functionNode.getCompileUnit().addFunctionInitializer(data, functionNode);
            initializedFunctionIds.add(fnId);
        }

        // We don't emit a ScriptFunction on stack for the outermost compiled function (as there's no code being
        // generated in its outer context that'd need it as a callee).
        if (lc.getOutermostFunction() == functionNode) {
            return;
        }

        method._new(SCRIPTFUNCTION_IMPL_NAME, SCRIPTFUNCTION_IMPL_TYPE).dup();
        loadConstant(data);

        if (functionNode.needsParentScope()) {
            method.loadCompilerConstant(SCOPE);
        } else {
            method.loadNull();
        }

        method.invoke(constructorNoLookup(SCRIPTFUNCTION_IMPL_NAME, RecompilableScriptFunctionData.class, ScriptObject.class));
    }

    // calls on Global class.
    private MethodEmitter globalInstance() {
        return method.invokestatic(GLOBAL_OBJECT, "instance", "()L" + GLOBAL_OBJECT + ';');
    }

    private MethodEmitter globalObjectPrototype() {
        return method.invokestatic(GLOBAL_OBJECT, "objectPrototype", methodDescriptor(ScriptObject.class));
    }

    private MethodEmitter globalAllocateArguments() {
        return method.invokestatic(GLOBAL_OBJECT, "allocateArguments", methodDescriptor(ScriptObject.class, Object[].class, Object.class, int.class));
    }

    private MethodEmitter globalNewRegExp() {
        return method.invokestatic(GLOBAL_OBJECT, "newRegExp", methodDescriptor(Object.class, String.class, String.class));
    }

    private MethodEmitter globalRegExpCopy() {
        return method.invokestatic(GLOBAL_OBJECT, "regExpCopy", methodDescriptor(Object.class, Object.class));
    }

    private MethodEmitter globalAllocateArray(final ArrayType type) {
        //make sure the native array is treated as an array type
        return method.invokestatic(GLOBAL_OBJECT, "allocate", "(" + type.getDescriptor() + ")Ljdk/nashorn/internal/objects/NativeArray;");
    }

    private MethodEmitter globalIsEval() {
        return method.invokestatic(GLOBAL_OBJECT, "isEval", methodDescriptor(boolean.class, Object.class));
    }

    private MethodEmitter globalReplaceLocationPropertyPlaceholder() {
        return method.invokestatic(GLOBAL_OBJECT, "replaceLocationPropertyPlaceholder", methodDescriptor(Object.class, Object.class, Object.class));
    }

    private MethodEmitter globalCheckObjectCoercible() {
        return method.invokestatic(GLOBAL_OBJECT, "checkObjectCoercible", methodDescriptor(void.class, Object.class));
    }

    private MethodEmitter globalDirectEval() {
        return method.invokestatic(GLOBAL_OBJECT, "directEval",
                methodDescriptor(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class));
    }

    private abstract class OptimisticOperation {
        MethodEmitter emit(final Optimistic optimistic) {
            return emit(optimistic, 0);
        }

        MethodEmitter emit(final Optimistic optimistic, final Type desiredType) {
            return emit(optimistic, desiredType, 0);
        }

        MethodEmitter emit(final Optimistic optimistic, final Type desiredType, final int ignoredArgCount) {
            return emit(optimistic.isOptimistic() && !desiredType.isObject(), optimistic.getProgramPoint(), ignoredArgCount);
        }

        MethodEmitter emit(final Optimistic optimistic, final int ignoredArgCount) {
            return emit(optimistic.isOptimistic(), optimistic.getProgramPoint(), ignoredArgCount);
        }

        MethodEmitter emit(final boolean isOptimistic, final int programPoint, final int ignoredArgCount) {
            final CompilationEnvironment env = compiler.getCompilationEnvironment();
            final boolean reallyOptimistic = isOptimistic && useOptimisticTypes();
            final boolean optimisticOrContinuation = reallyOptimistic || env.isContinuationEntryPoint(programPoint);
            final boolean currentContinuationEntryPoint = env.isCurrentContinuationEntryPoint(programPoint);
            final int stackSizeOnEntry = method.getStackSize() - ignoredArgCount;

            // First store the values on the stack opportunistically into local variables. Doing it before loadStack()
            // allows us to not have to pop/load any arguments that are pushed onto it by loadStack() in the second
            // storeStack().
            storeStack(ignoredArgCount, optimisticOrContinuation);

            // Now, load the stack
            loadStack();

            // Now store the values on the stack ultimately into local variables . In vast majority of cases, this is
            // (aside from creating the local types map) a no-op, as the first opportunistic stack store will already
            // store all variables. However, there can be operations in the loadStack() that invalidate some of the
            // stack stores, e.g. in "x[i] = x[++i]", "++i" will invalidate the already stored value for "i". In such
            // unfortunate cases this second storeStack() will restore the invariant that everything on the stack is
            // stored into a local variable, although at the cost of doing a store/load on the loaded arguments as well.
            final int liveLocalsCount = storeStack(method.getStackSize() - stackSizeOnEntry, optimisticOrContinuation);
            assert optimisticOrContinuation == (liveLocalsCount != -1);
            assert !optimisticOrContinuation || everyTypeIsKnown(method.getLocalVariableTypes(), liveLocalsCount);

            final Label beginTry;
            final Label catchLabel;
            final Label afterConsumeStack = reallyOptimistic || currentContinuationEntryPoint ? new Label("") : null;
            if(reallyOptimistic) {
                beginTry = new Label("");
                catchLabel = new Label("");
                method.label(beginTry);
            } else {
                beginTry = catchLabel = null;
            }

            consumeStack();

            if(reallyOptimistic) {
                method._try(beginTry, afterConsumeStack, catchLabel, UnwarrantedOptimismException.class);
            }

            if(reallyOptimistic || currentContinuationEntryPoint) {
                method.label(afterConsumeStack);

                final int[] localLoads = method.getLocalLoadsOnStack(0, stackSizeOnEntry);
                assert everyStackValueIsLocalLoad(localLoads) : Arrays.toString(localLoads) + ", " + stackSizeOnEntry + ", " + ignoredArgCount;
                final List<Type> localTypesList = method.getLocalVariableTypes();
                final int usedLocals = getUsedSlotsWithLiveTemporaries(localTypesList, localLoads);
                final Type[] localTypes = localTypesList.subList(0, usedLocals).toArray(new Type[usedLocals]);
                assert everyLocalLoadIsValid(localLoads, usedLocals) : Arrays.toString(localLoads) + " ~ " + Arrays.toString(localTypes);

                if(reallyOptimistic) {
                    addUnwarrantedOptimismHandlerLabel(localTypes, catchLabel);
                }
                if(currentContinuationEntryPoint) {
                    final ContinuationInfo ci = getContinuationInfo();
                    assert !ci.hasTargetLabel(); // No duplicate program points
                    ci.setTargetLabel(afterConsumeStack);
                    ci.setLocalVariableTypes(localTypes);
                    ci.setStackStoreSpec(localLoads);
                    ci.setStackTypes(Arrays.copyOf(method.getTypesFromStack(method.getStackSize()), stackSizeOnEntry));
                    assert ci.getStackStoreSpec().length == ci.getStackTypes().length;
                    ci.setReturnValueType(method.peekType());
                }
            }
            return method;
        }

        /**
         * Stores the current contents of the stack into local variables so they are not lost before invoking something that
         * can result in an {@code UnwarantedOptimizationException}.
         * @param ignoreArgCount the number of topmost arguments on stack to ignore when deciding on the shape of the catch
         * block. Those are used in the situations when we could not place the call to {@code storeStack} early enough
         * (before emitting code for pushing the arguments that the optimistic call will pop). This is admittedly a
         * deficiency in the design of the code generator when it deals with self-assignments and we should probably look
         * into fixing it.
         * @return types of the significant local variables after the stack was stored (types for local variables used
         * for temporary storage of ignored arguments are not returned).
         * @param optimisticOrContinuation if false, this method should not execute
         * a label for a catch block for the {@code UnwarantedOptimizationException}, suitable for capturing the
         * currently live local variables, tailored to their types.
         */
        private final int storeStack(final int ignoreArgCount, final boolean optimisticOrContinuation) {
            if(!optimisticOrContinuation) {
                return -1; // NOTE: correct value to return is lc.getUsedSlotCount(), but it wouldn't be used anyway
            }

            final int stackSize = method.getStackSize();
            final Type[] stackTypes = method.getTypesFromStack(stackSize);
            final int[] localLoadsOnStack = method.getLocalLoadsOnStack(0, stackSize);
            final int usedSlots = getUsedSlotsWithLiveTemporaries(method.getLocalVariableTypes(), localLoadsOnStack);

            final int firstIgnored = stackSize - ignoreArgCount;
            // Find the first value on the stack (from the bottom) that is not a load from a local variable.
            int firstNonLoad = 0;
            while(firstNonLoad < firstIgnored && localLoadsOnStack[firstNonLoad] != Label.Stack.NON_LOAD) {
                firstNonLoad++;
            }

            // Only do the store/load if first non-load is not an ignored argument. Otherwise, do nothing and return
            // the number of used slots as the number of live local variables.
            if(firstNonLoad >= firstIgnored) {
                return usedSlots;
            }

            // Find the number of new temporary local variables that we need; it's the number of values on the stack that
            // are not direct loads of existing local variables.
            int tempSlotsNeeded = 0;
            for(int i = firstNonLoad; i < stackSize; ++i) {
                if(localLoadsOnStack[i] == Label.Stack.NON_LOAD) {
                    tempSlotsNeeded += stackTypes[i].getSlots();
                }
            }

            // Ensure all values on the stack that weren't directly loaded from a local variable are stored in a local
            // variable. We're starting from highest local variable index, so that in case ignoreArgCount > 0 the ignored
            // ones end up at the end of the local variable table.
            int lastTempSlot = usedSlots + tempSlotsNeeded;
            int ignoreSlotCount = 0;
            for(int i = stackSize; i -- > firstNonLoad;) {
                final int loadSlot = localLoadsOnStack[i];
                if(loadSlot == Label.Stack.NON_LOAD) {
                    final Type type = stackTypes[i];
                    final int slots = type.getSlots();
                    lastTempSlot -= slots;
                    if(i >= firstIgnored) {
                        ignoreSlotCount += slots;
                    }
                    method.store(type, lastTempSlot);
                } else {
                    method.pop();
                }
            }
            assert lastTempSlot == usedSlots; // used all temporary locals

            final List<Type> localTypesList = method.getLocalVariableTypes();

            // Load values back on stack.
            for(int i = firstNonLoad; i < stackSize; ++i) {
                final int loadSlot = localLoadsOnStack[i];
                final Type stackType = stackTypes[i];
                final boolean isLoad = loadSlot != Label.Stack.NON_LOAD;
                final int lvarSlot = isLoad ? loadSlot : lastTempSlot;
                final Type lvarType = localTypesList.get(lvarSlot);
                method.load(lvarType, lvarSlot);
                if(isLoad) {
                    // Conversion operators (I2L etc.) preserve "load"-ness of the value despite the fact that, in the
                    // strict sense they are creating a derived value from the loaded value. This special behavior of
                    // on-stack conversion operators is necessary to accommodate for differences in local variable types
                    // after deoptimization; having a conversion operator throw away "load"-ness would create different
                    // local variable table shapes between optimism-failed code and its deoptimized rest-of method).
                    // After we load the value back, we need to redo the conversion to the stack type if stack type is
                    // different.
                    // NOTE: this would only strictly be necessary for widening conversions (I2L, L2D, I2D), and not for
                    // narrowing ones (L2I, D2L, D2I) as only widening conversions are the ones that can get eliminated
                    // in a deoptimized method, as their original input argument got widened. Maybe experiment with
                    // throwing away "load"-ness for narrowing conversions in MethodEmitter.convert()?
                    method.convert(stackType);
                } else {
                    // temporary stores never needs a convert, as their type is always the same as the stack type.
                    assert lvarType == stackType;
                    lastTempSlot += lvarType.getSlots();
                }
            }
            // used all temporaries
            assert lastTempSlot == usedSlots + tempSlotsNeeded;

            return lastTempSlot - ignoreSlotCount;
        }

        private void addUnwarrantedOptimismHandlerLabel(final Type[] localTypes, final Label label) {
            final String lvarTypesDescriptor = getLvarTypesDescriptor(localTypes);
            final Map<String, Collection<Label>> unwarrantedOptimismHandlers = lc.getUnwarrantedOptimismHandlers();
            Collection<Label> labels = unwarrantedOptimismHandlers.get(lvarTypesDescriptor);
            if(labels == null) {
                labels = new LinkedList<>();
                unwarrantedOptimismHandlers.put(lvarTypesDescriptor, labels);
            }
            labels.add(label);
        }

        /**
         * Returns the number of used local variable slots, including all live stack-store temporaries.
         * @param localVariableTypes the current local variable types
         * @param localLoadsOnStack the current local variable loads on the stack
         * @return the number of used local variable slots, including all live stack-store temporaries.
         */
        private final int getUsedSlotsWithLiveTemporaries(final List<Type> localVariableTypes, final int[] localLoadsOnStack) {
            // There are at least as many as are declared by the current blocks.
            int usedSlots = lc.getUsedSlotCount();
            // Look at every load on the stack, and bump the number of used slots up by the temporaries seen there.
            for (final int slot : localLoadsOnStack) {
                if(slot != Label.Stack.NON_LOAD) {
                    final int afterSlot = slot + localVariableTypes.get(slot).getSlots();
                    if(afterSlot > usedSlots) {
                        usedSlots = afterSlot;
                    }
                }
            }
            return usedSlots;
        }

        abstract void loadStack();

        // Make sure that whatever indy call site you emit from this method uses {@code getCallSiteFlagsOptimistic(node)}
        // or otherwise ensure optimistic flag is correctly set in the call site, otherwise it doesn't make much sense
        // to use OptimisticExpression for emitting it.
        abstract void consumeStack();
    }

    private static boolean everyLocalLoadIsValid(final int[] loads, final int localCount) {
        for (final int load : loads) {
            if(load < 0 || load >= localCount) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyTypeIsKnown(final List<Type> types, final int liveLocalsCount) {
        assert types instanceof RandomAccess;
        for(int i = 0; i < liveLocalsCount;) {
            final Type t = types.get(i);
            if(t == Type.UNKNOWN) {
                return false;
            }
            i += t.getSlots();
        }
        return true;
    }

    private static boolean everyStackValueIsLocalLoad(final int[] loads) {
        for (final int load : loads) {
            if(load == Label.Stack.NON_LOAD) {
                return false;
            }
        }
        return true;
    }

    private static String getLvarTypesDescriptor(final Type[] localVarTypes) {
        final StringBuilder desc = new StringBuilder(localVarTypes.length);
        for(int i = 0; i < localVarTypes.length;) {
            i += appendType(desc, localVarTypes[i]);
        }
        // Trailing unknown types are unnecessary. (These don't actually occur though as long as we conservatively
        // force-initialize all potentially-top values.)
        for(int l = desc.length(); l-- > 0;) {
            if(desc.charAt(l) != 'U') {
                desc.setLength(l + 1);
                break;
            }
        }
        return desc.toString();
    }

    private static int appendType(final StringBuilder b, final Type t) {
        b.append(t.getBytecodeStackType());
        return t.getSlots();
    }

    /**
     * Generates all the required {@code UnwarrantedOptimismException} handlers for the current function. The employed
     * strategy strives to maximize code reuse. Every handler constructs an array to hold the local variables, then
     * fills in some trailing part of the local variables (those for which it has a unique suffix in the descriptor),
     * then jumps to a handler for a prefix that's shared with other handlers. A handler that fills up locals up to
     * position 0 will not jump to a prefix handler (as it has no prefix), but instead end with constructing and
     * throwing a {@code RewriteException}. Since we lexicographically sort the entries, we only need to check every
     * entry to its immediately preceding one for longest matching prefix.
     * @return true if there is at least one exception handler
     */
    private boolean generateUnwarrantedOptimismExceptionHandlers(final FunctionNode fn) {
        if(!useOptimisticTypes()) {
            return false;
        }

        // Take the mapping of lvarSpecs -> labels, and turn them into a descending lexicographically sorted list of
        // handler specifications.
        final Map<String, Collection<Label>> unwarrantedOptimismHandlers = lc.popUnwarrantedOptimismHandlers();
        if(unwarrantedOptimismHandlers.isEmpty()) {
            return false;
        }
        final List<OptimismExceptionHandlerSpec> handlerSpecs = new ArrayList<>(unwarrantedOptimismHandlers.size() * 4/3);
        for(final String spec: unwarrantedOptimismHandlers.keySet()) {
            handlerSpecs.add(new OptimismExceptionHandlerSpec(spec, true));
        }
        Collections.sort(handlerSpecs, Collections.reverseOrder());

        // Map of local variable specifications to labels for populating the array for that local variable spec.
        final Map<String, Label> delegationLabels = new HashMap<>();

        // Do everything in a single pass over the handlerSpecs list. Note that the list can actually grow as we're
        // passing through it as we might add new prefix handlers into it, so can't hoist size() outside of the loop.
        for(int handlerIndex = 0; handlerIndex < handlerSpecs.size(); ++handlerIndex) {
            final OptimismExceptionHandlerSpec spec = handlerSpecs.get(handlerIndex);
            final String lvarSpec = spec.lvarSpec;
            if(spec.catchTarget) {
                // Start a catch block and assign the labels for this lvarSpec with it.
                method._catch(unwarrantedOptimismHandlers.get(lvarSpec));
                // This spec is a catch target, so emit array creation code
                method.load(spec.lvarSpec.length());
                method.newarray(Type.OBJECT_ARRAY);
            }
            if(spec.delegationTarget) {
                // If another handler can delegate to this handler as its prefix, then put a jump target here for the
                // shared code (after the array creation code, which is never shared).
                method.label(delegationLabels.get(lvarSpec)); // label must exist
            }

            final boolean lastHandler = handlerIndex == handlerSpecs.size() - 1;

            int lvarIndex;
            final int firstArrayIndex;
            Label delegationLabel;
            final String commonLvarSpec;
            if(lastHandler) {
                // Last handler block, doesn't delegate to anything.
                lvarIndex = 0;
                firstArrayIndex = 0;
                delegationLabel = null;
                commonLvarSpec = null;
            } else {
                // Not yet the last handler block, will definitely delegate to another handler; let's figure out which
                // one. It can be an already declared handler further down the list, or it might need to declare a new
                // prefix handler.

                // Since we're lexicographically ordered, the common prefix handler is defined by the common prefix of
                // this handler and the next handler on the list.
                final int nextHandlerIndex = handlerIndex + 1;
                final String nextLvarSpec = handlerSpecs.get(nextHandlerIndex).lvarSpec;
                commonLvarSpec = commonPrefix(lvarSpec, nextLvarSpec);

                // Let's find if we already have a declaration for such handler, or we need to insert it.
                {
                    boolean addNewHandler = true;
                    int commonHandlerIndex = nextHandlerIndex;
                    for(; commonHandlerIndex < handlerSpecs.size(); ++commonHandlerIndex) {
                        final OptimismExceptionHandlerSpec forwardHandlerSpec = handlerSpecs.get(commonHandlerIndex);
                        final String forwardLvarSpec = forwardHandlerSpec.lvarSpec;
                        if(forwardLvarSpec.equals(commonLvarSpec)) {
                            // We already have a handler for the common prefix.
                            addNewHandler = false;
                            // Make sure we mark it as a delegation target.
                            forwardHandlerSpec.delegationTarget = true;
                            break;
                        } else if(!forwardLvarSpec.startsWith(commonLvarSpec)) {
                            break;
                        }
                    }
                    if(addNewHandler) {
                        // We need to insert a common prefix handler. Note handlers created with catchTarget == false
                        // will automatically have delegationTarget == true (because that's the only reason for their
                        // existence).
                        handlerSpecs.add(commonHandlerIndex, new OptimismExceptionHandlerSpec(commonLvarSpec, false));
                    }
                }

                // Calculate the local variable index at the end of the common prefix
                firstArrayIndex = commonLvarSpec.length();
                lvarIndex = 0;
                for(int j = 0; j < firstArrayIndex; ++j) {
                    lvarIndex += CodeGeneratorLexicalContext.getTypeForSlotDescriptor(commonLvarSpec.charAt(j)).getSlots();
                }

                // Create a delegation label if not already present
                delegationLabel = delegationLabels.get(commonLvarSpec);
                if(delegationLabel == null) {
                    // uo_pa == "unwarranted optimism, populate array"
                    delegationLabel = new Label("uo_pa_" + commonLvarSpec);
                    delegationLabels.put(commonLvarSpec, delegationLabel);
                }
            }

            // Load local variables handled by this handler on stack
            int args = 0;
            for(int arrayIndex = firstArrayIndex; arrayIndex < lvarSpec.length(); ++arrayIndex) {
                final Type lvarType = CodeGeneratorLexicalContext.getTypeForSlotDescriptor(lvarSpec.charAt(arrayIndex));
                if (!lvarType.isUnknown()) {
                    method.load(lvarType, lvarIndex);
                    args++;
                }
                lvarIndex += lvarType.getSlots();
            }
            // Delegate actual storing into array to an array populator utility method. These are reused within a
            // compilation unit.
            //on the stack:
            // object array to be populated
            // start index
            // a lot of types
            method.dynamicArrayPopulatorCall(args + 1, firstArrayIndex);

            if(delegationLabel != null) {
                // We cascade to a prefix handler to fill out the rest of the local variables and throw the
                // RewriteException.
                assert !lastHandler;
                assert commonLvarSpec != null;
                final OptimismExceptionHandlerSpec nextSpec = handlerSpecs.get(handlerIndex + 1);
                // If the delegate immediately follows, and it's not a catch target (so it doesn't have array setup
                // code) don't bother emitting a jump, as we'd just jump to the next instruction.
                if(!nextSpec.lvarSpec.equals(commonLvarSpec) || nextSpec.catchTarget) {
                    method._goto(delegationLabel);
                }
            } else {
                assert lastHandler;
                // Nothing to delegate to, so this handler must create and throw the RewriteException.
                // At this point we have the UnwarrantedOptimismException and the Object[] with local variables on
                // stack. We need to create a RewriteException, push two references to it below the constructor
                // arguments, invoke the constructor, and throw the exception.
                method._new(RewriteException.class);
                method.dup(2);
                method.dup(2);
                method.pop();
                loadConstant(getByteCodeSymbolNames(fn));
                if (fn.compilerConstant(SCOPE).hasSlot()) {
                    method.loadCompilerConstant(SCOPE);
                } else {
                    method.loadNull();
                }
                final CompilationEnvironment env = compiler.getCompilationEnvironment();
                if (env.isCompileRestOf()) {
                    loadConstant(env.getContinuationEntryPoints());
                    method.invoke(INIT_REWRITE_EXCEPTION_REST_OF);
                } else {
                    method.invoke(INIT_REWRITE_EXCEPTION);
                }

                method.athrow();
            }
        }
        return true;
    }

    private static String[] getByteCodeSymbolNames(final FunctionNode fn) {
        // Only names of local variables on the function level are captured. This information is used to reduce
        // deoptimizations, so as much as we can capture will help. We rely on the fact that function wide variables are
        // all live all the time, so the array passed to rewrite exception contains one element for every slotted symbol
        // here.
        final List<String> names = new ArrayList<>();
        for (final Symbol symbol: fn.getBody().getSymbols()) {
            if (symbol.hasSlot()) {
                if (symbol.isScope()) {
                    // slot + scope can only be true for parameters
                    assert symbol.isParam();
                    names.add(null);
                } else {
                    names.add(symbol.getName());
                }
            }
        }
        return names.toArray(new String[names.size()]);
    }

    private static String commonPrefix(final String s1, final String s2) {
        final int l1 = s1.length();
        final int l = Math.min(l1, s2.length());
        for(int i = 0; i < l; ++i) {
            if(s1.charAt(i) != s2.charAt(i)) {
                return s1.substring(0, i);
            }
        }
        return l == l1 ? s1 : s2;
    }

    private static class OptimismExceptionHandlerSpec implements Comparable<OptimismExceptionHandlerSpec> {
        private final String lvarSpec;
        private final boolean catchTarget;
        private boolean delegationTarget;

        OptimismExceptionHandlerSpec(final String lvarSpec, final boolean catchTarget) {
            this.lvarSpec = lvarSpec;
            this.catchTarget = catchTarget;
            if(!catchTarget) {
                delegationTarget = true;
            }
        }

        @Override
        public int compareTo(final OptimismExceptionHandlerSpec o) {
            return lvarSpec.compareTo(o.lvarSpec);
        }

        @Override
        public String toString() {
            final StringBuilder b = new StringBuilder(64).append("[HandlerSpec ").append(lvarSpec);
            if(catchTarget) {
                b.append(", catchTarget");
            }
            if(delegationTarget) {
                b.append(", delegationTarget");
            }
            return b.append("]").toString();
        }
    }

    private static class ContinuationInfo {
        private final Label handlerLabel;
        private Label targetLabel; // Label for the target instruction.
        // Types the local variable slots have to have when this node completes
        private Type[] localVariableTypes;
        // Indices of local variables that need to be loaded on the stack when this node completes
        private int[] stackStoreSpec;
        // Types of values loaded on the stack
        private Type[] stackTypes;
        // If non-null, this node should perform the requisite type conversion
        private Type returnValueType;
        // If we are in the middle of an object literal initialization, we need to update the map
        private PropertyMap objectLiteralMap;

        ContinuationInfo() {
            this.handlerLabel = new Label("continuation_handler");
        }

        Label getHandlerLabel() {
            return handlerLabel;
        }

        boolean hasTargetLabel() {
            return targetLabel != null;
        }

        Label getTargetLabel() {
            return targetLabel;
        }

        void setTargetLabel(final Label targetLabel) {
            this.targetLabel = targetLabel;
        }

        Type[] getLocalVariableTypes() {
            return localVariableTypes.clone();
        }

        void setLocalVariableTypes(final Type[] localVariableTypes) {
            this.localVariableTypes = localVariableTypes;
        }

        int[] getStackStoreSpec() {
            return stackStoreSpec.clone();
        }

        void setStackStoreSpec(final int[] stackStoreSpec) {
            this.stackStoreSpec = stackStoreSpec;
        }

        Type[] getStackTypes() {
            return stackTypes.clone();
        }

        void setStackTypes(final Type[] stackTypes) {
            this.stackTypes = stackTypes;
        }

        Type getReturnValueType() {
            return returnValueType;
        }

        void setReturnValueType(final Type returnValueType) {
            this.returnValueType = returnValueType;
        }

        PropertyMap getObjectLiteralMap() {
            return objectLiteralMap;
        }

        void setObjectLiteralMap(final PropertyMap objectLiteralMap) {
            this.objectLiteralMap = objectLiteralMap;
        }

        @Override
        public String toString() {
             return "[localVariableTypes=" + Arrays.toString(localVariableTypes) + ", stackStoreSpec=" +
                     Arrays.toString(stackStoreSpec) + ", returnValueType=" + returnValueType + "]";
        }
    }

    private ContinuationInfo getContinuationInfo() {
        return fnIdToContinuationInfo.get(lc.getCurrentFunction().getId());
    }

    private void generateContinuationHandler() {
        if (!compiler.getCompilationEnvironment().isCompileRestOf()) {
            return;
        }

        final ContinuationInfo ci = getContinuationInfo();
        method.label(ci.getHandlerLabel());

        // There should never be an exception thrown from the continuation handler, but in case there is (meaning,
        // Nashorn has a bug), then line number 0 will be an indication of where it came from (line numbers are Uint16).
        method.lineNumber(0);

        final Type[] lvarTypes = ci.getLocalVariableTypes();
        final int    lvarCount = lvarTypes.length;

        final Type rewriteExceptionType = Type.typeFor(RewriteException.class);
        method.load(rewriteExceptionType, 0);
        method.dup();
        // Get local variable array
        method.invoke(RewriteException.GET_BYTECODE_SLOTS);
        // Store local variables
        for(int lvarIndex = 0, arrayIndex = 0; lvarIndex < lvarCount; ++arrayIndex) {
            final Type lvarType = lvarTypes[lvarIndex];
            final int nextLvarIndex = lvarIndex + lvarType.getSlots();
            if(nextLvarIndex < lvarCount) {
                // keep local variable array on the stack unless this is the last lvar
                method.dup();
            }
            method.load(arrayIndex).arrayload();
            method.convert(lvarType);
            method.store(lvarType, lvarIndex);
            lvarIndex = nextLvarIndex;
        }

        final int[]   stackStoreSpec = ci.getStackStoreSpec();
        final Type[]  stackTypes     = ci.getStackTypes();
        final boolean isStackEmpty   = stackStoreSpec.length == 0;
        if(!isStackEmpty) {
            // Store the RewriteException into an unused local variable slot.
            method.store(rewriteExceptionType, lvarCount);
            // Load arguments on the stack
            for(int i = 0; i < stackStoreSpec.length; ++i) {
                final int slot = stackStoreSpec[i];
                method.load(lvarTypes[slot], slot);
                method.convert(stackTypes[i]);
            }

            // stack: s0=object literal being initialized
            // change map of s0 so that the property we are initilizing when we failed
            // is now ci.returnValueType
            if (ci.getObjectLiteralMap() != null) {
                method.dup(); //dup script object
                assert ScriptObject.class.isAssignableFrom(method.peekType().getTypeClass()) : method.peekType().getTypeClass() + " is not a script object";
                loadConstant(ci.getObjectLiteralMap());
                method.invoke(ScriptObject.SET_MAP);
            }

            // Load RewriteException back; get rid of the stored reference.
            method.load(Type.OBJECT, lvarCount);
            method.loadNull();
            method.store(Type.OBJECT, lvarCount);
        }

        // Load return value on the stack
        method.invoke(RewriteException.GET_RETURN_VALUE);
        method.convert(ci.getReturnValueType());

        // Jump to continuation point
        method._goto(ci.getTargetLabel());
    }
}
