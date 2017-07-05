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
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SPLIT_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.codegen.CompilerConstants.interfaceCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.methodDescriptor;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.typeDescriptor;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.ir.Symbol.HAS_SLOT;
import static jdk.nashorn.internal.ir.Symbol.IS_INTERNAL;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_APPLY_TO_CALL;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_DECLARE;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_FAST_SCOPE;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_OPTIMISTIC;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_PROGRAM_POINT_SHIFT;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_SCOPE;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import jdk.nashorn.internal.AssertsEnabled;
import jdk.nashorn.internal.IntDeque;
import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.types.ArrayType;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.GetSplitState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.JoinPredecessorExpression;
import jdk.nashorn.internal.ir.JumpStatement;
import jdk.nashorn.internal.ir.JumpToInlinedFinally;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LexicalContextNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode.ArrayUnit;
import jdk.nashorn.internal.ir.LiteralNode.PrimitiveLiteralNode;
import jdk.nashorn.internal.ir.LocalVariableConversion;
import jdk.nashorn.internal.ir.LoopNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SetSplitState;
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
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.objects.Global;
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
import jdk.nashorn.internal.runtime.ScriptEnvironment;
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
 * The CodeGenerator visits nodes only once and emits bytecode for them.
 */
@Logger(name="codegen")
final class CodeGenerator extends NodeOperatorVisitor<CodeGeneratorLexicalContext> implements Loggable {

    private static final Type SCOPE_TYPE = Type.typeFor(ScriptObject.class);

    private static final String GLOBAL_OBJECT = Type.getInternalName(Global.class);

    private static final Call CREATE_REWRITE_EXCEPTION = CompilerConstants.staticCallNoLookup(RewriteException.class,
            "create", RewriteException.class, UnwarrantedOptimismException.class, Object[].class, String[].class);
    private static final Call CREATE_REWRITE_EXCEPTION_REST_OF = CompilerConstants.staticCallNoLookup(RewriteException.class,
            "create", RewriteException.class, UnwarrantedOptimismException.class, Object[].class, String[].class, int[].class);

    private static final Call ENSURE_INT = CompilerConstants.staticCallNoLookup(OptimisticReturnFilters.class,
            "ensureInt", int.class, Object.class, int.class);
    private static final Call ENSURE_LONG = CompilerConstants.staticCallNoLookup(OptimisticReturnFilters.class,
            "ensureLong", long.class, Object.class, int.class);
    private static final Call ENSURE_NUMBER = CompilerConstants.staticCallNoLookup(OptimisticReturnFilters.class,
            "ensureNumber", double.class, Object.class, int.class);

    private static final Call CREATE_FUNCTION_OBJECT = CompilerConstants.staticCallNoLookup(ScriptFunction.class,
            "create", ScriptFunction.class, Object[].class, int.class, ScriptObject.class);
    private static final Call CREATE_FUNCTION_OBJECT_NO_SCOPE = CompilerConstants.staticCallNoLookup(ScriptFunction.class,
            "create", ScriptFunction.class, Object[].class, int.class);

    private static final Call TO_NUMBER_FOR_EQ = CompilerConstants.staticCallNoLookup(JSType.class,
            "toNumberForEq", double.class, Object.class);
    private static final Call TO_NUMBER_FOR_STRICT_EQ = CompilerConstants.staticCallNoLookup(JSType.class,
            "toNumberForStrictEq", double.class, Object.class);


    private static final Class<?> ITERATOR_CLASS = Iterator.class;
    static {
        assert ITERATOR_CLASS == CompilerConstants.ITERATOR_PREFIX.type();
    }
    private static final Type ITERATOR_TYPE = Type.typeFor(ITERATOR_CLASS);
    private static final Type EXCEPTION_TYPE = Type.typeFor(CompilerConstants.EXCEPTION_PREFIX.type());

    private static final Integer INT_ZERO = 0;

    /** Constant data & installation. The only reason the compiler keeps this is because it is assigned
     *  by reflection in class installation */
    private final Compiler compiler;

    /** Is the current code submitted by 'eval' call? */
    private final boolean evalCode;

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
    private ContinuationInfo continuationInfo;

    private final Deque<Label> scopeEntryLabels = new ArrayDeque<>();

    private static final Label METHOD_BOUNDARY = new Label("");
    private final Deque<Label> catchLabels = new ArrayDeque<>();
    // Number of live locals on entry to (and thus also break from) labeled blocks.
    private final IntDeque labeledBlockBreakLiveLocals = new IntDeque();

    //is this a rest of compilation
    private final int[] continuationEntryPoints;

    /**
     * Constructor.
     *
     * @param compiler
     */
    CodeGenerator(final Compiler compiler, final int[] continuationEntryPoints) {
        super(new CodeGeneratorLexicalContext());
        this.compiler                = compiler;
        this.evalCode                = compiler.getSource().isEvalCode();
        this.continuationEntryPoints = continuationEntryPoints;
        this.callSiteFlags           = compiler.getScriptEnvironment()._callsite_flags;
        this.log                     = initLogger(compiler.getContext());
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
     * Gets the call site flags, adding the strict flag if the current function
     * being generated is in strict mode
     *
     * @return the correct flags for a call site in the current function
     */
    int getCallSiteFlags() {
        return lc.getCurrentFunction().getCallSiteFlags() | callSiteFlags;
    }

    /**
     * Gets the flags for a scope call site.
     * @param symbol a scope symbol
     * @return the correct flags for the scope call site
     */
    private int getScopeCallSiteFlags(final Symbol symbol) {
        assert symbol.isScope();
        final int flags = getCallSiteFlags() | CALLSITE_SCOPE;
        if (isEvalCode() && symbol.isGlobal()) {
            return flags; // Don't set fast-scope flag on non-declared globals in eval code - see JDK-8077955.
        }
        return isFastScope(symbol) ? flags | CALLSITE_FAST_SCOPE : flags;
    }

    /**
     * Are we generating code for 'eval' code?
     * @return true if currently compiled code is 'eval' code.
     */
    boolean isEvalCode() {
        return evalCode;
    }

    /**
     * Are we using dual primitive/object field representation?
     * @return true if using dual field representation, false for object-only fields
     */
    boolean useDualFields() {
        return compiler.getContext().useDualFields();
    }

    /**
     * Load an identity node
     *
     * @param identNode an identity node to load
     * @return the method generator used
     */
    private MethodEmitter loadIdent(final IdentNode identNode, final TypeBounds resultBounds) {
        checkTemporalDeadZone(identNode);
        final Symbol symbol = identNode.getSymbol();

        if (!symbol.isScope()) {
            final Type type = identNode.getType();
            if(type == Type.UNDEFINED) {
                return method.loadUndefined(resultBounds.widest);
            }

            assert symbol.hasSlot() || symbol.isParam();
            return method.load(identNode);
        }

        assert identNode.getSymbol().isScope() : identNode + " is not in scope!";
        final int flags = getScopeCallSiteFlags(symbol);
        if (isFastScope(symbol)) {
            // Only generate shared scope getter for fast-scope symbols so we know we can dial in correct scope.
            if (symbol.getUseCount() > SharedScopeCall.FAST_SCOPE_GET_THRESHOLD && !identNode.isOptimistic()) {
                // As shared scope vars are only used with non-optimistic identifiers, we switch from using TypeBounds to
                // just a single definitive type, resultBounds.widest.
                new OptimisticOperation(identNode, TypeBounds.OBJECT) {
                    @Override
                    void loadStack() {
                        method.loadCompilerConstant(SCOPE);
                    }

                    @Override
                    void consumeStack() {
                        loadSharedScopeVar(resultBounds.widest, symbol, flags);
                    }
                }.emit();
            } else {
                new LoadFastScopeVar(identNode, resultBounds, flags).emit();
            }
        } else {
            //slow scope load, we have no proto depth
            new LoadScopeVar(identNode, resultBounds, flags).emit();
        }

        return method;
    }

    // Any access to LET and CONST variables before their declaration must throw ReferenceError.
    // This is called the temporal dead zone (TDZ). See https://gist.github.com/rwaldron/f0807a758aa03bcdd58a
    private void checkTemporalDeadZone(final IdentNode identNode) {
        if (identNode.isDead()) {
            method.load(identNode.getSymbol().getName()).invoke(ScriptRuntime.THROW_REFERENCE_ERROR);
        }
    }

    // Runtime check for assignment to ES6 const
    private void checkAssignTarget(final Expression expression) {
        if (expression instanceof IdentNode && ((IdentNode)expression).getSymbol().isConst()) {
            method.load(((IdentNode)expression).getSymbol().getName()).invoke(ScriptRuntime.THROW_CONST_TYPE_ERROR);
        }
    }

    private boolean isRestOf() {
        return continuationEntryPoints != null;
    }

    private boolean isCurrentContinuationEntryPoint(final int programPoint) {
        return isRestOf() && getCurrentContinuationEntryPoint() == programPoint;
    }

    private int[] getContinuationEntryPoints() {
        return isRestOf() ? continuationEntryPoints : null;
    }

    private int getCurrentContinuationEntryPoint() {
        return isRestOf() ? continuationEntryPoints[0] : INVALID_PROGRAM_POINT;
    }

    private boolean isContinuationEntryPoint(final int programPoint) {
        if (isRestOf()) {
            assert continuationEntryPoints != null;
            for (final int cep : continuationEntryPoints) {
                if (cep == programPoint) {
                    return true;
                }
            }
        }
        return false;
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
                if (node instanceof WithNode && previousWasBlock || node instanceof FunctionNode && ((FunctionNode)node).needsDynamicScope()) {
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
        assert isFastScope(symbol);
        method.load(getScopeProtoDepth(lc.getCurrentBlock(), symbol));
        return lc.getScopeGet(unit, symbol, valueType, flags).generateInvoke(method);
    }

    private class LoadScopeVar extends OptimisticOperation {
        final IdentNode identNode;
        private final int flags;

        LoadScopeVar(final IdentNode identNode, final TypeBounds resultBounds, final int flags) {
            super(identNode, resultBounds);
            this.identNode = identNode;
            this.flags = flags;
        }

        @Override
        void loadStack() {
            method.loadCompilerConstant(SCOPE);
            getProto();
        }

        void getProto() {
            //empty
        }

        @Override
        void consumeStack() {
            // If this is either __FILE__, __DIR__, or __LINE__ then load the property initially as Object as we'd convert
            // it anyway for replaceLocationPropertyPlaceholder.
            if(identNode.isCompileTimePropertyName()) {
                method.dynamicGet(Type.OBJECT, identNode.getSymbol().getName(), flags, identNode.isFunction(), false);
                replaceCompileTimeProperty();
            } else {
                dynamicGet(identNode.getSymbol().getName(), flags, identNode.isFunction(), false);
            }
        }
    }

    private class LoadFastScopeVar extends LoadScopeVar {
        LoadFastScopeVar(final IdentNode identNode, final TypeBounds resultBounds, final int flags) {
            super(identNode, resultBounds, flags);
        }

        @Override
        void getProto() {
            loadFastScopeProto(identNode.getSymbol(), false);
        }
    }

    private MethodEmitter storeFastScopeVar(final Symbol symbol, final int flags) {
        loadFastScopeProto(symbol, true);
        method.dynamicSet(symbol.getName(), flags, false);
        return method;
    }

    private int getScopeProtoDepth(final Block startingBlock, final Symbol symbol) {
        //walk up the chain from starting block and when we bump into the current function boundary, add the external
        //information.
        final FunctionNode fn   = lc.getCurrentFunction();
        final int externalDepth = compiler.getScriptFunctionData(fn.getId()).getExternalSymbolDepth(symbol.getName());

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
     * Generate code that loads this node to the stack, not constraining its type
     *
     * @param expr node to load
     *
     * @return the method emitter used
     */
    private MethodEmitter loadExpressionUnbounded(final Expression expr) {
        return loadExpression(expr, TypeBounds.UNBOUNDED);
    }

    private MethodEmitter loadExpressionAsObject(final Expression expr) {
        return loadExpression(expr, TypeBounds.OBJECT);
    }

    MethodEmitter loadExpressionAsBoolean(final Expression expr) {
        return loadExpression(expr, TypeBounds.BOOLEAN);
    }

    // Test whether conversion from source to target involves a call of ES 9.1 ToPrimitive
    // with possible side effects from calling an object's toString or valueOf methods.
    private static boolean noToPrimitiveConversion(final Type source, final Type target) {
        // Object to boolean conversion does not cause ToPrimitive call
        return source.isJSPrimitive() || !target.isJSPrimitive() || target.isBoolean();
    }

    MethodEmitter loadBinaryOperands(final BinaryNode binaryNode) {
        return loadBinaryOperands(binaryNode.lhs(), binaryNode.rhs(), TypeBounds.UNBOUNDED.notWiderThan(binaryNode.getWidestOperandType()), false, false);
    }

    private MethodEmitter loadBinaryOperands(final Expression lhs, final Expression rhs, final TypeBounds explicitOperandBounds, final boolean baseAlreadyOnStack, final boolean forceConversionSeparation) {
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

        // Operands' load type should not be narrower than the narrowest of the individual operand types, nor narrower
        // than the lower explicit bound, but it should also not be wider than
        final Type lhsType = undefinedToNumber(lhs.getType());
        final Type rhsType = undefinedToNumber(rhs.getType());
        final Type narrowestOperandType = Type.narrowest(Type.widest(lhsType, rhsType), explicitOperandBounds.widest);
        final TypeBounds operandBounds = explicitOperandBounds.notNarrowerThan(narrowestOperandType);
        if (noToPrimitiveConversion(lhsType, explicitOperandBounds.widest) || rhs.isLocal()) {
            // Can reorder. We might still need to separate conversion, but at least we can do it with reordering
            if (forceConversionSeparation) {
                // Can reorder, but can't move conversion into the operand as the operation depends on operands
                // exact types for its overflow guarantees. E.g. with {L}{%I}expr1 {L}* {L}{%I}expr2 we are not allowed
                // to merge {L}{%I} into {%L}, as that can cause subsequent overflows; test for JDK-8058610 contains
                // concrete cases where this could happen.
                final TypeBounds safeConvertBounds = TypeBounds.UNBOUNDED.notNarrowerThan(narrowestOperandType);
                loadExpression(lhs, safeConvertBounds, baseAlreadyOnStack);
                method.convert(operandBounds.within(method.peekType()));
                loadExpression(rhs, safeConvertBounds, false);
                method.convert(operandBounds.within(method.peekType()));
            } else {
                // Can reorder and move conversion into the operand. Combine load and convert into single operations.
                loadExpression(lhs, operandBounds, baseAlreadyOnStack);
                loadExpression(rhs, operandBounds, false);
            }
        } else {
            // Can't reorder. Load and convert separately.
            final TypeBounds safeConvertBounds = TypeBounds.UNBOUNDED.notNarrowerThan(narrowestOperandType);
            loadExpression(lhs, safeConvertBounds, baseAlreadyOnStack);
            final Type lhsLoadedType = method.peekType();
            loadExpression(rhs, safeConvertBounds, false);
            final Type convertedLhsType = operandBounds.within(method.peekType());
            if (convertedLhsType != lhsLoadedType) {
                // Do it conditionally, so that if conversion is a no-op we don't introduce a SWAP, SWAP.
                method.swap().convert(convertedLhsType).swap();
            }
            method.convert(operandBounds.within(method.peekType()));
        }
        assert Type.generic(method.peekType()) == operandBounds.narrowest;
        assert Type.generic(method.peekType(1)) == operandBounds.narrowest;

        return method;
    }

    /**
     * Similar to {@link #loadBinaryOperands(BinaryNode)} but used specifically for loading operands of
     * relational and equality comparison operators where at least one argument is non-object. (When both
     * arguments are objects, we use {@link ScriptRuntime#EQ(Object, Object)}, {@link ScriptRuntime#LT(Object, Object)}
     * etc. methods instead. Additionally, {@code ScriptRuntime} methods are used for strict (in)equality comparison
     * of a boolean to anything that isn't a boolean.) This method handles the special case where one argument
     * is an object and another is a primitive. Naively, these could also be delegated to {@code ScriptRuntime} methods
     * by boxing the primitive. However, in all such cases the comparison is performed on numeric values, so it is
     * possible to strength-reduce the operation by taking the number value of the object argument instead and
     * comparing that to the primitive value ("primitive" will always be int, long, double, or boolean, and booleans
     * compare as ints in these cases, so they're essentially numbers too). This method will emit code for loading
     * arguments for such strength-reduced comparison. When both arguments are primitives, it just delegates to
     * {@link #loadBinaryOperands(BinaryNode)}.
     *
     * @param cmp the comparison operation for which the operands need to be loaded on stack.
     * @return the current method emitter.
     */
    MethodEmitter loadComparisonOperands(final BinaryNode cmp) {
        final Expression lhs = cmp.lhs();
        final Expression rhs = cmp.rhs();
        final Type lhsType = lhs.getType();
        final Type rhsType = rhs.getType();

        // Only used when not both are object, for that we have ScriptRuntime.LT etc.
        assert !(lhsType.isObject() && rhsType.isObject());

        if (lhsType.isObject() || rhsType.isObject()) {
            // We can reorder CONVERT LEFT and LOAD RIGHT only if either the left is a primitive, or the right
            // is a local. This is more strict than loadBinaryNode reorder criteria, as it can allow JS primitive
            // types too (notably: String is a JS primitive, but not a JVM primitive). We disallow String otherwise
            // we would prematurely convert it to number when comparing to an optimistic expression, e.g. in
            // "Hello" === String("Hello") the RHS starts out as an optimistic-int function call. If we allowed
            // reordering, we'd end up with ToNumber("Hello") === {I%}String("Hello") that is obviously incorrect.
            final boolean canReorder = lhsType.isPrimitive() || rhs.isLocal();
            // If reordering is allowed, and we're using a relational operator (that is, <, <=, >, >=) and not an
            // (in)equality operator, then we encourage combining of LOAD and CONVERT into a single operation.
            // This is because relational operators' semantics prescribes vanilla ToNumber() conversion, while
            // (in)equality operators need the specialized JSType.toNumberFor[Strict]Equals. E.g. in the code snippet
            // "i < obj.size" (where i is primitive and obj.size is statically an object), ".size" will thus be allowed
            // to compile as:
            //   invokedynamic dyn:getProp|getElem|getMethod:size(Object;)D
            // instead of the more costly:
            //   invokedynamic dyn:getProp|getElem|getMethod:size(Object;)Object
            //   invokestatic JSType.toNumber(Object)D
            // Note also that even if this is allowed, we're only using it on operands that are non-optimistic, as
            // otherwise the logic for determining effective optimistic-ness would turn an optimistic double return
            // into a freely coercible one, which would be wrong.
            final boolean canCombineLoadAndConvert = canReorder && cmp.isRelational();

            // LOAD LEFT
            loadExpression(lhs, canCombineLoadAndConvert && !lhs.isOptimistic() ? TypeBounds.NUMBER : TypeBounds.UNBOUNDED);

            final Type lhsLoadedType = method.peekType();
            final TokenType tt = cmp.tokenType();
            if (canReorder) {
                // Can reorder CONVERT LEFT and LOAD RIGHT
                emitObjectToNumberComparisonConversion(method, tt);
                loadExpression(rhs, canCombineLoadAndConvert && !rhs.isOptimistic() ? TypeBounds.NUMBER : TypeBounds.UNBOUNDED);
            } else {
                // Can't reorder CONVERT LEFT and LOAD RIGHT
                loadExpression(rhs, TypeBounds.UNBOUNDED);
                if (lhsLoadedType != Type.NUMBER) {
                    method.swap();
                    emitObjectToNumberComparisonConversion(method, tt);
                    method.swap();
                }
            }

            // CONVERT RIGHT
            emitObjectToNumberComparisonConversion(method, tt);
            return method;
        }
        // For primitive operands, just don't do anything special.
        return loadBinaryOperands(cmp);
    }

    private static void emitObjectToNumberComparisonConversion(final MethodEmitter method, final TokenType tt) {
        switch(tt) {
        case EQ:
        case NE:
            if (method.peekType().isObject()) {
                TO_NUMBER_FOR_EQ.invoke(method);
                return;
            }
            break;
        case EQ_STRICT:
        case NE_STRICT:
            if (method.peekType().isObject()) {
                TO_NUMBER_FOR_STRICT_EQ.invoke(method);
                return;
            }
            break;
        default:
            break;
        }
        method.convert(Type.NUMBER);
    }

    private static Type undefinedToNumber(final Type type) {
        return type == Type.UNDEFINED ? Type.NUMBER : type;
    }

    private static final class TypeBounds {
        final Type narrowest;
        final Type widest;

        static final TypeBounds UNBOUNDED = new TypeBounds(Type.UNKNOWN, Type.OBJECT);
        static final TypeBounds INT = exact(Type.INT);
        static final TypeBounds NUMBER = exact(Type.NUMBER);
        static final TypeBounds OBJECT = exact(Type.OBJECT);
        static final TypeBounds BOOLEAN = exact(Type.BOOLEAN);

        static TypeBounds exact(final Type type) {
            return new TypeBounds(type, type);
        }

        TypeBounds(final Type narrowest, final Type widest) {
            assert widest    != null && widest    != Type.UNDEFINED && widest != Type.UNKNOWN : widest;
            assert narrowest != null && narrowest != Type.UNDEFINED : narrowest;
            assert !narrowest.widerThan(widest) : narrowest + " wider than " + widest;
            assert !widest.narrowerThan(narrowest);
            this.narrowest = Type.generic(narrowest);
            this.widest = Type.generic(widest);
        }

        TypeBounds notNarrowerThan(final Type type) {
            return maybeNew(Type.narrowest(Type.widest(narrowest, type), widest), widest);
        }

        TypeBounds notWiderThan(final Type type) {
            return maybeNew(Type.narrowest(narrowest, type), Type.narrowest(widest, type));
        }

        boolean canBeNarrowerThan(final Type type) {
            return narrowest.narrowerThan(type);
        }

        TypeBounds maybeNew(final Type newNarrowest, final Type newWidest) {
            if(newNarrowest == narrowest && newWidest == widest) {
                return this;
            }
            return new TypeBounds(newNarrowest, newWidest);
        }

        TypeBounds booleanToInt() {
            return maybeNew(CodeGenerator.booleanToInt(narrowest), CodeGenerator.booleanToInt(widest));
        }

        TypeBounds objectToNumber() {
            return maybeNew(CodeGenerator.objectToNumber(narrowest), CodeGenerator.objectToNumber(widest));
        }

        Type within(final Type type) {
            if(type.narrowerThan(narrowest)) {
                return narrowest;
            }
            if(type.widerThan(widest)) {
                return widest;
            }
            return type;
        }

        @Override
        public String toString() {
            return "[" + narrowest + ", " + widest + "]";
        }
    }

    private static Type booleanToInt(final Type t) {
        return t == Type.BOOLEAN ? Type.INT : t;
    }

    private static Type objectToNumber(final Type t) {
        return t.isObject() ? Type.NUMBER : t;
    }

    MethodEmitter loadExpressionAsType(final Expression expr, final Type type) {
        if(type == Type.BOOLEAN) {
            return loadExpressionAsBoolean(expr);
        } else if(type == Type.UNDEFINED) {
            assert expr.getType() == Type.UNDEFINED;
            return loadExpressionAsObject(expr);
        }
        // having no upper bound preserves semantics of optimistic operations in the expression (by not having them
        // converted early) and then applies explicit conversion afterwards.
        return loadExpression(expr, TypeBounds.UNBOUNDED.notNarrowerThan(type)).convert(type);
    }

    private MethodEmitter loadExpression(final Expression expr, final TypeBounds resultBounds) {
        return loadExpression(expr, resultBounds, false);
    }

    /**
     * Emits code for evaluating an expression and leaving its value on top of the stack, narrowing or widening it if
     * necessary.
     * @param expr the expression to load
     * @param resultBounds the incoming type bounds. The value on the top of the stack is guaranteed to not be of narrower
     * type than the narrowest bound, or wider type than the widest bound after it is loaded.
     * @param baseAlreadyOnStack true if the base of an access or index node is already on the stack. Used to avoid
     * double evaluation of bases in self-assignment expressions to access and index nodes. {@code Type.OBJECT} is used
     * to indicate the widest possible type.
     * @return the method emitter
     */
    private MethodEmitter loadExpression(final Expression expr, final TypeBounds resultBounds, final boolean baseAlreadyOnStack) {

        /*
         * The load may be of type IdentNode, e.g. "x", AccessNode, e.g. "x.y"
         * or IndexNode e.g. "x[y]". Both AccessNodes and IndexNodes are
         * BaseNodes and the logic for loading the base object is reused
         */
        final CodeGenerator codegen = this;

        final boolean isCurrentDiscard = codegen.lc.isCurrentDiscard(expr);
        expr.accept(new NodeOperatorVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterIdentNode(final IdentNode identNode) {
                loadIdent(identNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterAccessNode(final AccessNode accessNode) {
                new OptimisticOperation(accessNode, resultBounds) {
                    @Override
                    void loadStack() {
                        if (!baseAlreadyOnStack) {
                            loadExpressionAsObject(accessNode.getBase());
                        }
                        assert method.peekType().isObject();
                    }
                    @Override
                    void consumeStack() {
                        final int flags = getCallSiteFlags();
                        dynamicGet(accessNode.getProperty(), flags, accessNode.isFunction(), accessNode.isIndex());
                    }
                }.emit(baseAlreadyOnStack ? 1 : 0);
                return false;
            }

            @Override
            public boolean enterIndexNode(final IndexNode indexNode) {
                new OptimisticOperation(indexNode, resultBounds) {
                    @Override
                    void loadStack() {
                        if (!baseAlreadyOnStack) {
                            loadExpressionAsObject(indexNode.getBase());
                            loadExpressionUnbounded(indexNode.getIndex());
                        }
                    }
                    @Override
                    void consumeStack() {
                        final int flags = getCallSiteFlags();
                        dynamicGetIndex(flags, indexNode.isFunction());
                    }
                }.emit(baseAlreadyOnStack ? 2 : 0);
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
                return false;
            }

            @Override
            public boolean enterASSIGN(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_ADD(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_ADD(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_BIT_AND(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_BIT_AND(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_BIT_OR(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_BIT_OR(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_BIT_XOR(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_BIT_XOR(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_DIV(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_DIV(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_MOD(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_MOD(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_MUL(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_MUL(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_SAR(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_SAR(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_SHL(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_SHL(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_SHR(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_SHR(binaryNode);
                return false;
            }

            @Override
            public boolean enterASSIGN_SUB(final BinaryNode binaryNode) {
                checkAssignTarget(binaryNode.lhs());
                loadASSIGN_SUB(binaryNode);
                return false;
            }

            @Override
            public boolean enterCallNode(final CallNode callNode) {
                return loadCallNode(callNode, resultBounds);
            }

            @Override
            public boolean enterLiteralNode(final LiteralNode<?> literalNode) {
                loadLiteral(literalNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterTernaryNode(final TernaryNode ternaryNode) {
                loadTernaryNode(ternaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterADD(final BinaryNode binaryNode) {
                loadADD(binaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterSUB(final UnaryNode unaryNode) {
                loadSUB(unaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterSUB(final BinaryNode binaryNode) {
                loadSUB(binaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterMUL(final BinaryNode binaryNode) {
                loadMUL(binaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterDIV(final BinaryNode binaryNode) {
                loadDIV(binaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterMOD(final BinaryNode binaryNode) {
                loadMOD(binaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterSAR(final BinaryNode binaryNode) {
                loadSAR(binaryNode);
                return false;
            }

            @Override
            public boolean enterSHL(final BinaryNode binaryNode) {
                loadSHL(binaryNode);
                return false;
            }

            @Override
            public boolean enterSHR(final BinaryNode binaryNode) {
                loadSHR(binaryNode);
                return false;
            }

            @Override
            public boolean enterCOMMALEFT(final BinaryNode binaryNode) {
                loadCOMMALEFT(binaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterCOMMARIGHT(final BinaryNode binaryNode) {
                loadCOMMARIGHT(binaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterAND(final BinaryNode binaryNode) {
                loadAND_OR(binaryNode, resultBounds, true);
                return false;
            }

            @Override
            public boolean enterOR(final BinaryNode binaryNode) {
                loadAND_OR(binaryNode, resultBounds, false);
                return false;
            }

            @Override
            public boolean enterNOT(final UnaryNode unaryNode) {
                loadNOT(unaryNode);
                return false;
            }

            @Override
            public boolean enterADD(final UnaryNode unaryNode) {
                loadADD(unaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterBIT_NOT(final UnaryNode unaryNode) {
                loadBIT_NOT(unaryNode);
                return false;
            }

            @Override
            public boolean enterBIT_AND(final BinaryNode binaryNode) {
                loadBIT_AND(binaryNode);
                return false;
            }

            @Override
            public boolean enterBIT_OR(final BinaryNode binaryNode) {
                loadBIT_OR(binaryNode);
                return false;
            }

            @Override
            public boolean enterBIT_XOR(final BinaryNode binaryNode) {
                loadBIT_XOR(binaryNode);
                return false;
            }

            @Override
            public boolean enterVOID(final UnaryNode unaryNode) {
                loadVOID(unaryNode, resultBounds);
                return false;
            }

            @Override
            public boolean enterEQ(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.EQ);
                return false;
            }

            @Override
            public boolean enterEQ_STRICT(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.EQ);
                return false;
            }

            @Override
            public boolean enterGE(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.GE);
                return false;
            }

            @Override
            public boolean enterGT(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.GT);
                return false;
            }

            @Override
            public boolean enterLE(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.LE);
                return false;
            }

            @Override
            public boolean enterLT(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.LT);
                return false;
            }

            @Override
            public boolean enterNE(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.NE);
                return false;
            }

            @Override
            public boolean enterNE_STRICT(final BinaryNode binaryNode) {
                loadCmp(binaryNode, Condition.NE);
                return false;
            }

            @Override
            public boolean enterObjectNode(final ObjectNode objectNode) {
                loadObjectNode(objectNode);
                return false;
            }

            @Override
            public boolean enterRuntimeNode(final RuntimeNode runtimeNode) {
                loadRuntimeNode(runtimeNode);
                return false;
            }

            @Override
            public boolean enterNEW(final UnaryNode unaryNode) {
                loadNEW(unaryNode);
                return false;
            }

            @Override
            public boolean enterDECINC(final UnaryNode unaryNode) {
                checkAssignTarget(unaryNode.getExpression());
                loadDECINC(unaryNode);
                return false;
            }

            @Override
            public boolean enterJoinPredecessorExpression(final JoinPredecessorExpression joinExpr) {
                loadMaybeDiscard(joinExpr, joinExpr.getExpression(), resultBounds);
                return false;
            }

            @Override
            public boolean enterGetSplitState(final GetSplitState getSplitState) {
                method.loadScope();
                method.invoke(Scope.GET_SPLIT_STATE);
                return false;
            }

            @Override
            public boolean enterDefault(final Node otherNode) {
                // Must have handled all expressions that can legally be encountered.
                throw new AssertionError(otherNode.getClass().getName());
            }
        });
        if(!isCurrentDiscard) {
            coerceStackTop(resultBounds);
        }
        return method;
    }

    private MethodEmitter coerceStackTop(final TypeBounds typeBounds) {
        return method.convert(typeBounds.within(method.peekType()));
    }

    /**
     * Closes any still open entries for this block's local variables in the bytecode local variable table.
     *
     * @param block block containing symbols.
     */
    private void closeBlockVariables(final Block block) {
        for (final Symbol symbol : block.getSymbols()) {
            if (symbol.isBytecodeLocal()) {
                method.closeLocalVariable(symbol, block.getBreakLabel());
            }
        }
    }

    @Override
    public boolean enterBlock(final Block block) {
        final Label entryLabel = block.getEntryLabel();
        if (entryLabel.isBreakTarget()) {
            // Entry label is a break target only for an inlined finally block.
            assert !method.isReachable();
            method.breakLabel(entryLabel, lc.getUsedSlotCount());
        } else {
            method.label(entryLabel);
        }
        if(!method.isReachable()) {
            return false;
        }
        if(lc.isFunctionBody() && emittedMethods.contains(lc.getCurrentFunction().getName())) {
            return false;
        }
        initLocals(block);

        assert lc.getUsedSlotCount() == method.getFirstTemp();
        return true;
    }

    boolean useOptimisticTypes() {
        return !lc.inSplitNode() && compiler.useOptimisticTypes();
    }

    @Override
    public Node leaveBlock(final Block block) {
        popBlockScope(block);
        method.beforeJoinPoint(block);

        closeBlockVariables(block);
        lc.releaseSlots();
        assert !method.isReachable() || (lc.isFunctionBody() ? 0 : lc.getUsedSlotCount()) == method.getFirstTemp() :
            "reachable="+method.isReachable() +
            " isFunctionBody=" + lc.isFunctionBody() +
            " usedSlotCount=" + lc.getUsedSlotCount() +
            " firstTemp=" + method.getFirstTemp();

        return block;
    }

    private void popBlockScope(final Block block) {
        final Label breakLabel = block.getBreakLabel();

        if(!block.needsScope() || lc.isFunctionBody()) {
            emitBlockBreakLabel(breakLabel);
            return;
        }

        final Label beginTryLabel = scopeEntryLabels.pop();
        final Label recoveryLabel = new Label("block_popscope_catch");
        emitBlockBreakLabel(breakLabel);
        final boolean bodyCanThrow = breakLabel.isAfter(beginTryLabel);
        if(bodyCanThrow) {
            method._try(beginTryLabel, breakLabel, recoveryLabel);
        }

        Label afterCatchLabel = null;

        if(method.isReachable()) {
            popScope();
            if(bodyCanThrow) {
                afterCatchLabel = new Label("block_after_catch");
                method._goto(afterCatchLabel);
            }
        }

        if(bodyCanThrow) {
            assert !method.isReachable();
            method._catch(recoveryLabel);
            popScopeException();
            method.athrow();
        }
        if(afterCatchLabel != null) {
            method.label(afterCatchLabel);
        }
    }

    private void emitBlockBreakLabel(final Label breakLabel) {
        // TODO: this is totally backwards. Block should not be breakable, LabelNode should be breakable.
        final LabelNode labelNode = lc.getCurrentBlockLabelNode();
        if(labelNode != null) {
            // Only have conversions if we're reachable
            assert labelNode.getLocalVariableConversion() == null || method.isReachable();
            method.beforeJoinPoint(labelNode);
            method.breakLabel(breakLabel, labeledBlockBreakLiveLocals.pop());
        } else {
            method.label(breakLabel);
        }
    }

    private void popScope() {
        popScopes(1);
    }

    /**
     * Pop scope as part of an exception handler. Similar to {@code popScope()} but also takes care of adjusting the
     * number of scopes that needs to be popped in case a rest-of continuation handler encounters an exception while
     * performing a ToPrimitive conversion.
     */
    private void popScopeException() {
        popScope();
        final ContinuationInfo ci = getContinuationInfo();
        if(ci != null) {
            final Label catchLabel = ci.catchLabel;
            if(catchLabel != METHOD_BOUNDARY && catchLabel == catchLabels.peek()) {
                ++ci.exceptionScopePops;
            }
        }
    }

    private void popScopesUntil(final LexicalContextNode until) {
        popScopes(lc.getScopeNestingLevelTo(until));
    }

    private void popScopes(final int count) {
        if(count == 0) {
            return;
        }
        assert count > 0; // together with count == 0 check, asserts nonnegative count
        if (!method.hasScope()) {
            // We can sometimes invoke this method even if the method has no slot for the scope object. Typical example:
            // for(;;) { with({}) { break; } }. WithNode normally creates a scope, but if it uses no identifiers and
            // nothing else forces creation of a scope in the method, we just won't have the :scope local variable.
            return;
        }
        method.loadCompilerConstant(SCOPE);
        for(int i = 0; i < count; ++i) {
            method.invoke(ScriptObject.GET_PROTO);
        }
        method.storeCompilerConstant(SCOPE);
    }

    @Override
    public boolean enterBreakNode(final BreakNode breakNode) {
        return enterJumpStatement(breakNode);
    }

    @Override
    public boolean enterJumpToInlinedFinally(final JumpToInlinedFinally jumpToInlinedFinally) {
        return enterJumpStatement(jumpToInlinedFinally);
    }

    private boolean enterJumpStatement(final JumpStatement jump) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(jump);

        method.beforeJoinPoint(jump);
        popScopesUntil(jump.getPopScopeLimit(lc));
        final Label targetLabel = jump.getTargetLabel(lc);
        targetLabel.markAsBreakTarget();
        method._goto(targetLabel);

        return false;
    }

    private int loadArgs(final List<Expression> args) {
        final int argCount = args.size();
        // arg have already been converted to objects here.
        if (argCount > LinkerCallSite.ARGLIMIT) {
            loadArgsArray(args);
            return 1;
        }

        for (final Expression arg : args) {
            assert arg != null;
            loadExpressionUnbounded(arg);
        }
        return argCount;
    }

    private boolean loadCallNode(final CallNode callNode, final TypeBounds resultBounds) {
        lineNumber(callNode.getLineNumber());

        final List<Expression> args = callNode.getArgs();
        final Expression function = callNode.getFunction();
        final Block currentBlock = lc.getCurrentBlock();
        final CodeGeneratorLexicalContext codegenLexicalContext = lc;

        function.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {

            private MethodEmitter sharedScopeCall(final IdentNode identNode, final int flags) {
                final Symbol symbol = identNode.getSymbol();
                final boolean isFastScope = isFastScope(symbol);
                new OptimisticOperation(callNode, resultBounds) {
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
                        // We have trouble finding e.g. in Type.typeFor(asm.Type) because it can't see the Context class
                        // loader, so we need to weaken reference signatures to Object.
                        for(int i = 0; i < paramTypes.length; ++i) {
                            paramTypes[i] = Type.generic(paramTypes[i]);
                        }
                        // As shared scope calls are only used in non-optimistic compilation, we switch from using
                        // TypeBounds to just a single definitive type, resultBounds.widest.
                        final SharedScopeCall scopeCall = codegenLexicalContext.getScopeCall(unit, symbol,
                                identNode.getType(), resultBounds.widest, paramTypes, flags);
                        scopeCall.generateInvoke(method);
                    }
                }.emit();
                return method;
            }

            private void scopeCall(final IdentNode ident, final int flags) {
                new OptimisticOperation(callNode, resultBounds) {
                    int argsCount;
                    @Override
                    void loadStack() {
                        loadExpressionAsObject(ident); // foo() makes no sense if foo == 3
                        // ScriptFunction will see CALLSITE_SCOPE and will bind scope accordingly.
                        method.loadUndefined(Type.OBJECT); //the 'this'
                        argsCount = loadArgs(args);
                    }
                    @Override
                    void consumeStack() {
                        dynamicCall(2 + argsCount, flags, ident.getName());
                    }
                }.emit();
            }

            private void evalCall(final IdentNode ident, final int flags) {
                final Label invoke_direct_eval  = new Label("invoke_direct_eval");
                final Label is_not_eval  = new Label("is_not_eval");
                final Label eval_done = new Label("eval_done");

                new OptimisticOperation(callNode, resultBounds) {
                    int argsCount;
                    @Override
                    void loadStack() {
                        /*
                         * We want to load 'eval' to check if it is indeed global builtin eval.
                         * If this eval call is inside a 'with' statement, dyn:getMethod|getProp|getElem
                         * would be generated if ident is a "isFunction". But, that would result in a
                         * bound function from WithObject. We don't want that as bound function as that
                         * won't be detected as builtin eval. So, we make ident as "not a function" which
                         * results in "dyn:getProp|getElem|getMethod" being generated and so WithObject
                         * would return unbounded eval function.
                         *
                         * Example:
                         *
                         *  var global = this;
                         *  function func() {
                         *      with({ eval: global.eval) { eval("var x = 10;") }
                         *  }
                         */
                        loadExpressionAsObject(ident.setIsNotFunction()); // Type.OBJECT as foo() makes no sense if foo == 3
                        globalIsEval();
                        method.ifeq(is_not_eval);

                        // Load up self (scope).
                        method.loadCompilerConstant(SCOPE);
                        final List<Expression> evalArgs = callNode.getEvalArgs().getArgs();
                        // load evaluated code
                        loadExpressionAsObject(evalArgs.get(0));
                        // load second and subsequent args for side-effect
                        final int numArgs = evalArgs.size();
                        for (int i = 1; i < numArgs; i++) {
                            loadAndDiscard(evalArgs.get(i));
                        }
                        method._goto(invoke_direct_eval);

                        method.label(is_not_eval);
                        // load this time but with dyn:getMethod|getProp|getElem
                        loadExpressionAsObject(ident); // Type.OBJECT as foo() makes no sense if foo == 3
                        // This is some scope 'eval' or global eval replaced by user
                        // but not the built-in ECMAScript 'eval' function call
                        method.loadNull();
                        argsCount = loadArgs(callNode.getArgs());
                    }

                    @Override
                    void consumeStack() {
                        // Ordinary call
                        dynamicCall(2 + argsCount, flags, "eval");
                        method._goto(eval_done);

                        method.label(invoke_direct_eval);
                        // Special/extra 'eval' arguments. These can be loaded late (in consumeStack) as we know none of
                        // them can ever be optimistic.
                        method.loadCompilerConstant(THIS);
                        method.load(callNode.getEvalArgs().getLocation());
                        method.load(CodeGenerator.this.lc.getCurrentFunction().isStrict());
                        // direct call to Global.directEval
                        globalDirectEval();
                        convertOptimisticReturnValue();
                        coerceStackTop(resultBounds);
                    }
                }.emit();

                method.label(eval_done);
            }

            @Override
            public boolean enterIdentNode(final IdentNode node) {
                final Symbol symbol = node.getSymbol();

                if (symbol.isScope()) {
                    final int flags = getScopeCallSiteFlags(symbol);
                    final int useCount = symbol.getUseCount();

                    // Threshold for generating shared scope callsite is lower for fast scope symbols because we know
                    // we can dial in the correct scope. However, we also need to enable it for non-fast scopes to
                    // support huge scripts like mandreel.js.
                    if (callNode.isEval()) {
                        evalCall(node, flags);
                    } else if (useCount <= SharedScopeCall.FAST_SCOPE_CALL_THRESHOLD
                            || !isFastScope(symbol) && useCount <= SharedScopeCall.SLOW_SCOPE_CALL_THRESHOLD
                            || CodeGenerator.this.lc.inDynamicScope()
                            || callNode.isOptimistic()) {
                        scopeCall(node, flags);
                    } else {
                        sharedScopeCall(node, flags);
                    }
                    assert method.peekType().equals(resultBounds.within(callNode.getType())) : method.peekType() + " != " + resultBounds + "(" + callNode.getType() + ")";
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

                final int flags = getCallSiteFlags() | (callNode.isApplyToCall() ? CALLSITE_APPLY_TO_CALL : 0);

                new OptimisticOperation(callNode, resultBounds) {
                    int argCount;
                    @Override
                    void loadStack() {
                        loadExpressionAsObject(node.getBase());
                        method.dup();
                        // NOTE: not using a nested OptimisticOperation on this dynamicGet, as we expect to get back
                        // a callable object. Nobody in their right mind would optimistically type this call site.
                        assert !node.isOptimistic();
                        method.dynamicGet(node.getType(), node.getProperty(), flags, true, node.isIndex());
                        method.swap();
                        argCount = loadArgs(args);
                    }
                    @Override
                    void consumeStack() {
                        dynamicCall(2 + argCount, flags, node.toString(false));
                    }
                }.emit();

                return false;
            }

            @Override
            public boolean enterFunctionNode(final FunctionNode origCallee) {
                new OptimisticOperation(callNode, resultBounds) {
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
                        dynamicCall(2 + argsCount, getCallSiteFlags(), origCallee.getName());
                    }
                }.emit();
                return false;
            }

            @Override
            public boolean enterIndexNode(final IndexNode node) {
                new OptimisticOperation(callNode, resultBounds) {
                    int argsCount;
                    @Override
                    void loadStack() {
                        loadExpressionAsObject(node.getBase());
                        method.dup();
                        final Type indexType = node.getIndex().getType();
                        if (indexType.isObject() || indexType.isBoolean()) {
                            loadExpressionAsObject(node.getIndex()); //TODO boolean
                        } else {
                            loadExpressionUnbounded(node.getIndex());
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
                        dynamicCall(2 + argsCount, getCallSiteFlags(), node.toString(false));
                    }
                }.emit();
                return false;
            }

            @Override
            protected boolean enterDefault(final Node node) {
                new OptimisticOperation(callNode, resultBounds) {
                    int argsCount;
                    @Override
                    void loadStack() {
                        // Load up function.
                        loadExpressionAsObject(function); //TODO, e.g. booleans can be used as functions
                        method.loadUndefined(Type.OBJECT); // ScriptFunction will figure out the correct this when it sees CALLSITE_SCOPE
                        argsCount = loadArgs(args);
                        }
                        @Override
                        void consumeStack() {
                            final int flags = getCallSiteFlags() | CALLSITE_SCOPE;
                            dynamicCall(2 + argsCount, flags, node.toString(false));
                        }
                }.emit();
                return false;
            }
        });

        return false;
    }

    /**
     * Returns the flags with optimistic flag and program point removed.
     * @param flags the flags that need optimism stripped from them.
     * @return flags without optimism
     */
    static int nonOptimisticFlags(final int flags) {
        return flags & ~(CALLSITE_OPTIMISTIC | -1 << CALLSITE_PROGRAM_POINT_SHIFT);
    }

    @Override
    public boolean enterContinueNode(final ContinueNode continueNode) {
        return enterJumpStatement(continueNode);
    }

    @Override
    public boolean enterEmptyNode(final EmptyNode emptyNode) {
        // Don't even record the line number, it's irrelevant as there's no code.
        return false;
    }

    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(expressionStatement);

        loadAndDiscard(expressionStatement.getExpression());
        assert method.getStackSize() == 0;

        return false;
    }

    @Override
    public boolean enterBlockStatement(final BlockStatement blockStatement) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(blockStatement);

        blockStatement.getBlock().accept(this);

        return false;
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(forNode);
        if (forNode.isForIn()) {
            enterForIn(forNode);
        } else {
            final Expression init = forNode.getInit();
            if (init != null) {
                loadAndDiscard(init);
            }
            enterForOrWhile(forNode, forNode.getModify());
        }

        return false;
    }

    private void enterForIn(final ForNode forNode) {
        loadExpression(forNode.getModify(), TypeBounds.OBJECT);
        method.invoke(forNode.isForEach() ? ScriptRuntime.TO_VALUE_ITERATOR : ScriptRuntime.TO_PROPERTY_ITERATOR);
        final Symbol iterSymbol = forNode.getIterator();
        final int iterSlot = iterSymbol.getSlot(Type.OBJECT);
        method.store(iterSymbol, ITERATOR_TYPE);

        method.beforeJoinPoint(forNode);

        final Label continueLabel = forNode.getContinueLabel();
        final Label breakLabel    = forNode.getBreakLabel();

        method.label(continueLabel);
        method.load(ITERATOR_TYPE, iterSlot);
        method.invoke(interfaceCallNoLookup(ITERATOR_CLASS, "hasNext", boolean.class));
        final JoinPredecessorExpression test = forNode.getTest();
        final Block body = forNode.getBody();
        if(LocalVariableConversion.hasLiveConversion(test)) {
            final Label afterConversion = new Label("for_in_after_test_conv");
            method.ifne(afterConversion);
            method.beforeJoinPoint(test);
            method._goto(breakLabel);
            method.label(afterConversion);
        } else {
            method.ifeq(breakLabel);
        }

        new Store<Expression>(forNode.getInit()) {
            @Override
            protected void storeNonDiscard() {
                // This expression is neither part of a discard, nor needs to be left on the stack after it was
                // stored, so we override storeNonDiscard to be a no-op.
            }

            @Override
            protected void evaluate() {
                new OptimisticOperation((Optimistic)forNode.getInit(), TypeBounds.UNBOUNDED) {
                    @Override
                    void loadStack() {
                        method.load(ITERATOR_TYPE, iterSlot);
                    }

                    @Override
                    void consumeStack() {
                        method.invoke(interfaceCallNoLookup(ITERATOR_CLASS, "next", Object.class));
                        convertOptimisticReturnValue();
                    }
                }.emit();
            }
        }.store();
        body.accept(this);

        if(method.isReachable()) {
            method._goto(continueLabel);
        }
        method.label(breakLabel);
    }

    /**
     * Initialize the slots in a frame to undefined.
     *
     * @param block block with local vars.
     */
    private void initLocals(final Block block) {
        lc.onEnterBlock(block);

        final boolean isFunctionBody = lc.isFunctionBody();
        final FunctionNode function = lc.getCurrentFunction();
        if (isFunctionBody) {
            initializeMethodParameters(function);
            if(!function.isVarArg()) {
                expandParameterSlots(function);
            }
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

            final boolean hasArguments = function.needsArguments();
            final List<MapTuple<Symbol>> tuples = new ArrayList<>();
            final Iterator<IdentNode> paramIter = function.getParameters().iterator();
            for (final Symbol symbol : block.getSymbols()) {
                if (symbol.isInternal() || symbol.isThis()) {
                    continue;
                }

                if (symbol.isVar()) {
                    assert !varsInScope || symbol.isScope();
                    if (varsInScope || symbol.isScope()) {
                        assert symbol.isScope()   : "scope for " + symbol + " should have been set in Lower already " + function.getName();
                        assert !symbol.hasSlot()  : "slot for " + symbol + " should have been removed in Lower already" + function.getName();

                        //this tuple will not be put fielded, as it has no value, just a symbol
                        tuples.add(new MapTuple<Symbol>(symbol.getName(), symbol, null));
                    } else {
                        assert symbol.hasSlot() || symbol.slotCount() == 0 : symbol + " should have a slot only, no scope";
                    }
                } else if (symbol.isParam() && (varsInScope || hasArguments || symbol.isScope())) {
                    assert symbol.isScope()   : "scope for " + symbol + " should have been set in AssignSymbols already " + function.getName() + " varsInScope="+varsInScope+" hasArguments="+hasArguments+" symbol.isScope()=" + symbol.isScope();
                    assert !(hasArguments && symbol.hasSlot())  : "slot for " + symbol + " should have been removed in Lower already " + function.getName();

                    final Type   paramType;
                    final Symbol paramSymbol;

                    if (hasArguments) {
                        assert !symbol.hasSlot()  : "slot for " + symbol + " should have been removed in Lower already ";
                        paramSymbol = null;
                        paramType   = null;
                    } else {
                        paramSymbol = symbol;
                        // NOTE: We're relying on the fact here that Block.symbols is a LinkedHashMap, hence it will
                        // return symbols in the order they were defined, and parameters are defined in the same order
                        // they appear in the function. That's why we can have a single pass over the parameter list
                        // with an iterator, always just scanning forward for the next parameter that matches the symbol
                        // name.
                        for(;;) {
                            final IdentNode nextParam = paramIter.next();
                            if(nextParam.getName().equals(symbol.getName())) {
                                paramType = nextParam.getType();
                                break;
                            }
                        }
                    }

                    tuples.add(new MapTuple<Symbol>(symbol.getName(), symbol, paramType, paramSymbol) {
                        //this symbol will be put fielded, we can't initialize it as undefined with a known type
                        @Override
                        public Class<?> getValueType() {
                            if (!useDualFields() ||  value == null || paramType == null || paramType.isBoolean()) {
                                return Object.class;
                            }
                            return paramType.getTypeClass();
                        }
                    });
                }
            }

            /*
             * Create a new object based on the symbols and values, generate
             * bootstrap code for object
             */
            new FieldObjectCreator<Symbol>(this, tuples, true, hasArguments) {
                @Override
                protected void loadValue(final Symbol value, final Type type) {
                    method.load(value, type);
                }
            }.makeObject(method);
            // program function: merge scope into global
            if (isFunctionBody && function.isProgram()) {
                method.invoke(ScriptRuntime.MERGE_SCOPE);
            }

            method.storeCompilerConstant(SCOPE);
            if(!isFunctionBody) {
                // Function body doesn't need a try/catch to restore scope, as it'd be a dead store anyway. Allowing it
                // actually causes issues with UnwarrantedOptimismException handlers as ASM will sort this handler to
                // the top of the exception handler table, so it'll be triggered instead of the UOE handlers.
                final Label scopeEntryLabel = new Label("scope_entry");
                scopeEntryLabels.push(scopeEntryLabel);
                method.label(scopeEntryLabel);
            }
        } else if (isFunctionBody && function.isVarArg()) {
            // Since we don't have a scope, parameters didn't get assigned array indices by the FieldObjectCreator, so
            // we need to assign them separately here.
            int nextParam = 0;
            for (final IdentNode param : function.getParameters()) {
                param.getSymbol().setFieldIndex(nextParam++);
            }
        }

        // Debugging: print symbols? @see --print-symbols flag
        printSymbols(block, function, (isFunctionBody ? "Function " : "Block in ") + (function.getIdent() == null ? "<anonymous>" : function.getIdent().getName()));
    }

    /**
     * Incoming method parameters are always declared on method entry; declare them in the local variable table.
     * @param function function for which code is being generated.
     */
    private void initializeMethodParameters(final FunctionNode function) {
        final Label functionStart = new Label("fn_start");
        method.label(functionStart);
        int nextSlot = 0;
        if(function.needsCallee()) {
            initializeInternalFunctionParameter(CALLEE, function, functionStart, nextSlot++);
        }
        initializeInternalFunctionParameter(THIS, function, functionStart, nextSlot++);
        if(function.isVarArg()) {
            initializeInternalFunctionParameter(VARARGS, function, functionStart, nextSlot++);
        } else {
            for(final IdentNode param: function.getParameters()) {
                final Symbol symbol = param.getSymbol();
                if(symbol.isBytecodeLocal()) {
                    method.initializeMethodParameter(symbol, param.getType(), functionStart);
                }
            }
        }
    }

    private void initializeInternalFunctionParameter(final CompilerConstants cc, final FunctionNode fn, final Label functionStart, final int slot) {
        final Symbol symbol = initializeInternalFunctionOrSplitParameter(cc, fn, functionStart, slot);
        // Internal function params (:callee, this, and :varargs) are never expanded to multiple slots
        assert symbol.getFirstSlot() == slot;
    }

    private Symbol initializeInternalFunctionOrSplitParameter(final CompilerConstants cc, final FunctionNode fn, final Label functionStart, final int slot) {
        final Symbol symbol = fn.getBody().getExistingSymbol(cc.symbolName());
        final Type type = Type.typeFor(cc.type());
        method.initializeMethodParameter(symbol, type, functionStart);
        method.onLocalStore(type, slot);
        return symbol;
    }

    /**
     * Parameters come into the method packed into local variable slots next to each other. Nashorn on the other hand
     * can use 1-6 slots for a local variable depending on all the types it needs to store. When this method is invoked,
     * the symbols are already allocated such wider slots, but the values are still in tightly packed incoming slots,
     * and we need to spread them into their new locations.
     * @param function the function for which parameter-spreading code needs to be emitted
     */
    private void expandParameterSlots(final FunctionNode function) {
        final List<IdentNode> parameters = function.getParameters();
        // Calculate the total number of incoming parameter slots
        int currentIncomingSlot = function.needsCallee() ? 2 : 1;
        for(final IdentNode parameter: parameters) {
            currentIncomingSlot += parameter.getType().getSlots();
        }
        // Starting from last parameter going backwards, move the parameter values into their new slots.
        for(int i = parameters.size(); i-- > 0;) {
            final IdentNode parameter = parameters.get(i);
            final Type parameterType = parameter.getType();
            final int typeWidth = parameterType.getSlots();
            currentIncomingSlot -= typeWidth;
            final Symbol symbol = parameter.getSymbol();
            final int slotCount = symbol.slotCount();
            assert slotCount > 0;
            // Scoped parameters must not hold more than one value
            assert symbol.isBytecodeLocal() || slotCount == typeWidth;

            // Mark it as having its value stored into it by the method invocation.
            method.onLocalStore(parameterType, currentIncomingSlot);
            if(currentIncomingSlot != symbol.getSlot(parameterType)) {
                method.load(parameterType, currentIncomingSlot);
                method.store(symbol, parameterType);
            }
        }
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

    private boolean skipFunction(final FunctionNode functionNode) {
        final ScriptEnvironment env = compiler.getScriptEnvironment();
        final boolean lazy = env._lazy_compilation;
        final boolean onDemand = compiler.isOnDemandCompilation();

        // If this is on-demand or lazy compilation, don't compile a nested (not topmost) function.
        if((onDemand || lazy) && lc.getOutermostFunction() != functionNode) {
            return true;
        }

        // If lazy compiling with optimistic types, don't compile the program eagerly either. It will soon be
        // invalidated anyway. In presence of a class cache, this further means that an obsoleted program version
        // lingers around. Also, currently loading previously persisted optimistic types information only works if
        // we're on-demand compiling a function, so with this strategy the :program method can also have the warmup
        // benefit of using previously persisted types.
        //
        // NOTE that this means the first compiled class will effectively just have a :createProgramFunction method, and
        // the RecompilableScriptFunctionData (RSFD) object in its constants array. It won't even have the :program
        // method. This is by design. It does mean that we're wasting one compiler execution (and we could minimize this
        // by just running it up to scope depth calculation, which creates the RSFDs and then this limited codegen).
        // We could emit an initial separate compile unit with the initial version of :program in it to better utilize
        // the compilation pipeline, but that would need more invasive changes, as currently the assumption that
        // :program is emitted into the first compilation unit of the function lives in many places.
        return !onDemand && lazy && env._optimistic_types && functionNode.isProgram();
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if (skipFunction(functionNode)) {
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

            final ClassEmitter classEmitter = unit.getClassEmitter();
            pushMethodEmitter(isRestOf() ? classEmitter.restOfMethod(functionNode) : classEmitter.method(functionNode));
            method.setPreventUndefinedLoad();
            if(useOptimisticTypes()) {
                lc.pushUnwarrantedOptimismHandlers();
            }

            // new method - reset last line number
            lastLineNumber = -1;

            method.begin();

            if (isRestOf()) {
                assert continuationInfo == null;
                continuationInfo = new ContinuationInfo();
                method.gotoLoopStart(continuationInfo.getHandlerLabel());
            }
        }

        return true;
    }

    private void pushMethodEmitter(final MethodEmitter newMethod) {
        method = lc.pushMethodEmitter(newMethod);
        catchLabels.push(METHOD_BOUNDARY);
    }

    private void popMethodEmitter() {
        method = lc.popMethodEmitter(method);
        assert catchLabels.peek() == METHOD_BOUNDARY;
        catchLabels.pop();
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
                popMethodEmitter();
                log.info("=== END ", functionNode.getName());
            } else {
                markOptimistic = false;
            }

            FunctionNode newFunctionNode = functionNode;
            if (markOptimistic) {
                newFunctionNode = newFunctionNode.setFlag(lc, FunctionNode.IS_DEOPTIMIZABLE);
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
    public boolean enterIfNode(final IfNode ifNode) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(ifNode);

        final Expression test = ifNode.getTest();
        final Block pass = ifNode.getPass();
        final Block fail = ifNode.getFail();

        if (Expression.isAlwaysTrue(test)) {
            loadAndDiscard(test);
            pass.accept(this);
            return false;
        } else if (Expression.isAlwaysFalse(test)) {
            loadAndDiscard(test);
            if (fail != null) {
                fail.accept(this);
            }
            return false;
        }

        final boolean hasFailConversion = LocalVariableConversion.hasLiveConversion(ifNode);

        final Label failLabel  = new Label("if_fail");
        final Label afterLabel = (fail == null && !hasFailConversion) ? null : new Label("if_done");

        emitBranch(test, failLabel, false);

        pass.accept(this);
        if(method.isReachable() && afterLabel != null) {
            method._goto(afterLabel); //don't fallthru to fail block
        }
        method.label(failLabel);

        if (fail != null) {
            fail.accept(this);
        } else if(hasFailConversion) {
            method.beforeJoinPoint(ifNode);
        }

        if(afterLabel != null && afterLabel.isReachable()) {
            method.label(afterLabel);
        }

        return false;
    }

    private void emitBranch(final Expression test, final Label label, final boolean jumpWhenTrue) {
        new BranchOptimizer(this, method).execute(test, label, jumpWhenTrue);
    }

    private void enterStatement(final Statement statement) {
        lineNumber(statement);
    }

    private void lineNumber(final Statement statement) {
        lineNumber(statement.getLineNumber());
    }

    private void lineNumber(final int lineNumber) {
        if (lineNumber != lastLineNumber && lineNumber != Node.NO_LINE_NUMBER) {
            method.lineNumber(lineNumber);
            lastLineNumber = lineNumber;
        }
    }

    int getLastLineNumber() {
        return lastLineNumber;
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
            final MethodEmitter savedMethod     = method;
            final FunctionNode  currentFunction = lc.getCurrentFunction();

            for (final ArrayUnit arrayUnit : units) {
                unit = lc.pushCompileUnit(arrayUnit.getCompileUnit());

                final String className = unit.getUnitClassName();
                assert unit != null;
                final String name      = currentFunction.uniqueName(SPLIT_PREFIX.symbolName());
                final String signature = methodDescriptor(type, ScriptFunction.class, Object.class, ScriptObject.class, type);

                pushMethodEmitter(unit.getClassEmitter().method(EnumSet.of(Flag.PUBLIC, Flag.STATIC), name, signature));

                method.setFunctionNode(currentFunction);
                method.begin();

                defineCommonSplitMethodParameters();
                defineSplitMethodParameter(CompilerConstants.SPLIT_ARRAY_ARG.slot(), arrayType);

                // NOTE: when this is no longer needed, SplitIntoFunctions will no longer have to add IS_SPLIT
                // to synthetic functions, and FunctionNode.needsCallee() will no longer need to test for isSplit().
                final int arraySlot = fixScopeSlot(currentFunction, 3);

                lc.enterSplitNode();

                for (int i = arrayUnit.getLo(); i < arrayUnit.getHi(); i++) {
                    method.load(arrayType, arraySlot);
                    storeElement(nodes, elementType, postsets[i]);
                }

                method.load(arrayType, arraySlot);
                method._return();
                lc.exitSplitNode();
                method.end();
                lc.releaseSlots();
                popMethodEmitter();

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

            return method;
        }

        if(postsets.length > 0) {
            final int arraySlot = method.getUsedSlotsWithLiveTemporaries();
            method.storeTemp(arrayType, arraySlot);
            for (final int postset : postsets) {
                method.load(arrayType, arraySlot);
                storeElement(nodes, elementType, postset);
            }
            method.load(arrayType, arraySlot);
        }
        return method;
    }

    private void storeElement(final Expression[] nodes, final Type elementType, final int index) {
        method.load(index);

        final Expression element = nodes[index];

        if (element == null) {
            method.loadEmpty(elementType);
        } else {
            loadExpressionAsType(element, elementType);
        }

        method.arraystore();
    }

    private MethodEmitter loadArgsArray(final List<Expression> args) {
        final Object[] array = new Object[args.size()];
        loadConstant(array);

        for (int i = 0; i < args.size(); i++) {
            method.dup();
            method.load(i);
            loadExpression(args.get(i), TypeBounds.OBJECT); // variable arity methods always take objects
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
                methodEmitter.checkcast(ArrayData.class);
                methodEmitter.invoke(virtualCallNoLookup(ArrayData.class, "copy", ArrayData.class));
            } else if (cls != Object.class) {
                methodEmitter.checkcast(cls);
            }
        }
    }

    private void loadConstantsAndIndex(final Object object, final MethodEmitter methodEmitter) {
        methodEmitter.loadConstants().load(compiler.getConstantData().add(object));
    }

    // literal values
    private void loadLiteral(final LiteralNode<?> node, final TypeBounds resultBounds) {
        final Object value = node.getValue();

        if (value == null) {
            method.loadNull();
        } else if (value instanceof Undefined) {
            method.loadUndefined(resultBounds.within(Type.OBJECT));
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
            if(!resultBounds.canBeNarrowerThan(Type.OBJECT)) {
                method.load((Integer)value);
                method.convert(Type.OBJECT);
            } else if(!resultBounds.canBeNarrowerThan(Type.NUMBER)) {
                method.load(((Integer)value).doubleValue());
            } else if(!resultBounds.canBeNarrowerThan(Type.LONG)) {
                method.load(((Integer)value).longValue());
            } else {
                method.load((Integer)value);
            }
        } else if (value instanceof Long) {
            if(!resultBounds.canBeNarrowerThan(Type.OBJECT)) {
                method.load((Long)value);
                method.convert(Type.OBJECT);
            } else if(!resultBounds.canBeNarrowerThan(Type.NUMBER)) {
                method.load(((Long)value).doubleValue());
            } else {
                method.load((Long)value);
            }
        } else if (value instanceof Double) {
            if(!resultBounds.canBeNarrowerThan(Type.OBJECT)) {
                method.load((Double)value);
                method.convert(Type.OBJECT);
            } else {
                method.load((Double)value);
            }
        } else if (node instanceof ArrayLiteralNode) {
            final ArrayLiteralNode arrayLiteral = (ArrayLiteralNode)node;
            final ArrayType atype = arrayLiteral.getArrayType();
            loadArray(arrayLiteral, atype);
            globalAllocateArray(atype);
        } else {
            throw new UnsupportedOperationException("Unknown literal for " + node.getClass() + " " + value.getClass() + " " + value);
        }
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

    /**
     * Check if a property value contains a particular program point
     * @param value value
     * @param pp    program point
     * @return true if it's there.
     */
    private static boolean propertyValueContains(final Expression value, final int pp) {
        return new Supplier<Boolean>() {
            boolean contains;

            @Override
            public Boolean get() {
                value.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                    @Override
                    public boolean enterFunctionNode(final FunctionNode functionNode) {
                        return false;
                    }

                    @Override
                    public boolean enterObjectNode(final ObjectNode objectNode) {
                        return false;
                    }

                    @Override
                    public boolean enterDefault(final Node node) {
                        if (contains) {
                            return false;
                        }
                        if (node instanceof Optimistic && ((Optimistic)node).getProgramPoint() == pp) {
                            contains = true;
                            return false;
                        }
                        return true;
                    }
                });

                return contains;
            }
        }.get();
    }

    private void loadObjectNode(final ObjectNode objectNode) {
        final List<PropertyNode> elements = objectNode.getElements();

        final List<MapTuple<Expression>> tuples = new ArrayList<>();
        final List<PropertyNode> gettersSetters = new ArrayList<>();
        final int ccp = getCurrentContinuationEntryPoint();

        Expression protoNode = null;
        boolean restOfProperty = false;

        for (final PropertyNode propertyNode : elements) {
            final Expression value = propertyNode.getValue();
            final String key = propertyNode.getKeyName();
            // Just use a pseudo-symbol. We just need something non null; use the name and zero flags.
            final Symbol symbol = value == null ? null : new Symbol(key, 0);

            if (value == null) {
                gettersSetters.add(propertyNode);
            } else if (propertyNode.getKey() instanceof IdentNode &&
                       key.equals(ScriptObject.PROTO_PROPERTY_NAME)) {
                // ES6 draft compliant __proto__ inside object literal
                // Identifier key and name is __proto__
                protoNode = value;
                continue;
            }

            restOfProperty |=
                value != null &&
                isValid(ccp) &&
                propertyValueContains(value, ccp);

            //for literals, a value of null means object type, i.e. the value null or getter setter function
            //(I think)
            final Class<?> valueType = (!useDualFields() || value == null || value.getType().isBoolean()) ? Object.class : value.getType().getTypeClass();
            tuples.add(new MapTuple<Expression>(key, symbol, Type.typeFor(valueType), value) {
                @Override
                public Class<?> getValueType() {
                    return type.getTypeClass();
                }
            });
        }

        final ObjectCreator<?> oc;
        if (elements.size() > OBJECT_SPILL_THRESHOLD) {
            oc = new SpillObjectCreator(this, tuples);
        } else {
            oc = new FieldObjectCreator<Expression>(this, tuples) {
                @Override
                protected void loadValue(final Expression node, final Type type) {
                    loadExpressionAsType(node, type);
                }};
        }
        oc.makeObject(method);

        //if this is a rest of method and our continuation point was found as one of the values
        //in the properties above, we need to reset the map to oc.getMap() in the continuation
        //handler
        if (restOfProperty) {
            final ContinuationInfo ci = getContinuationInfo();
            // Can be set at most once for a single rest-of method
            assert ci.getObjectLiteralMap() == null;
            ci.setObjectLiteralMap(oc.getMap());
            ci.setObjectLiteralStackDepth(method.getStackSize());
        }

        method.dup();
        if (protoNode != null) {
            loadExpressionAsObject(protoNode);
            // take care of { __proto__: 34 } or some such!
            method.convert(Type.OBJECT);
            method.invoke(ScriptObject.SET_PROTO_FROM_LITERAL);
        } else {
            method.invoke(ScriptObject.SET_GLOBAL_OBJECT_PROTO);
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
    }

    @Override
    public boolean enterReturnNode(final ReturnNode returnNode) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(returnNode);

        final Type returnType = lc.getCurrentFunction().getReturnType();

        final Expression expression = returnNode.getExpression();
        if (expression != null) {
            loadExpressionUnbounded(expression);
        } else {
            method.loadUndefined(returnType);
        }

        method._return(returnType);

        return false;
    }

    private boolean undefinedCheck(final RuntimeNode runtimeNode, final List<Expression> args) {
        final Request request = runtimeNode.getRequest();

        if (!Request.isUndefinedCheck(request)) {
            return false;
        }

        final Expression lhs = args.get(0);
        final Expression rhs = args.get(1);

        final Symbol lhsSymbol = lhs instanceof IdentNode ? ((IdentNode)lhs).getSymbol() : null;
        final Symbol rhsSymbol = rhs instanceof IdentNode ? ((IdentNode)rhs).getSymbol() : null;
        // One must be a "undefined" identifier, otherwise we can't get here
        assert lhsSymbol != null || rhsSymbol != null;

        final Symbol undefinedSymbol;
        if (isUndefinedSymbol(lhsSymbol)) {
            undefinedSymbol = lhsSymbol;
        } else {
            assert isUndefinedSymbol(rhsSymbol);
            undefinedSymbol = rhsSymbol;
        }

        assert undefinedSymbol != null; //remove warning
        if (!undefinedSymbol.isScope()) {
            return false; //disallow undefined as local var or parameter
        }

        if (lhsSymbol == undefinedSymbol && lhs.getType().isPrimitive()) {
            //we load the undefined first. never mind, because this will deoptimize anyway
            return false;
        }

        if(isDeoptimizedExpression(lhs)) {
            // This is actually related to "lhs.getType().isPrimitive()" above: any expression being deoptimized in
            // the current chain of rest-of compilations used to have a type narrower than Object (so it was primitive).
            // We must not perform undefined check specialization for them, as then we'd violate the basic rule of
            // "Thou shalt not alter the stack shape between a deoptimized method and any of its (transitive) rest-ofs."
            return false;
        }

        //make sure that undefined has not been overridden or scoped as a local var
        //between us and global
        if (!compiler.isGlobalSymbol(lc.getCurrentFunction(), "undefined")) {
            return false;
        }

        final boolean isUndefinedCheck = request == Request.IS_UNDEFINED;
        final Expression expr = undefinedSymbol == lhsSymbol ? rhs : lhs;
        if (expr.getType().isPrimitive()) {
            loadAndDiscard(expr); //throw away lhs, but it still needs to be evaluated for side effects, even if not in scope, as it can be optimistic
            method.load(!isUndefinedCheck);
        } else {
            final Label checkTrue  = new Label("ud_check_true");
            final Label end        = new Label("end");
            loadExpressionAsObject(expr);
            method.loadUndefined(Type.OBJECT);
            method.if_acmpeq(checkTrue);
            method.load(!isUndefinedCheck);
            method._goto(end);
            method.label(checkTrue);
            method.load(isUndefinedCheck);
            method.label(end);
        }

        return true;
    }

    private static boolean isUndefinedSymbol(final Symbol symbol) {
        return symbol != null && "undefined".equals(symbol.getName());
    }

    private static boolean isNullLiteral(final Node node) {
        return node instanceof LiteralNode<?> && ((LiteralNode<?>) node).isNull();
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

        if(isDeoptimizedExpression(lhs)) {
            // This is actually related to "!lhs.getType().isObject()" above: any expression being deoptimized in
            // the current chain of rest-of compilations used to have a type narrower than Object. We must not
            // perform null check specialization for them, as then we'd no longer be loading aconst_null on stack
            // and thus violate the basic rule of "Thou shalt not alter the stack shape between a deoptimized
            // method and any of its (transitive) rest-ofs."
            // NOTE also that if we had a representation for well-known constants (e.g. null, 0, 1, -1, etc.) in
            // Label$Stack.localLoads then this wouldn't be an issue, as we would never (somewhat ridiculously)
            // allocate a temporary local to hold the result of aconst_null before attempting an optimistic
            // operation.
            return false;
        }

        // this is a null literal check, so if there is implicit coercion
        // involved like {D}x=null, we will fail - this is very rare
        final Label trueLabel  = new Label("trueLabel");
        final Label falseLabel = new Label("falseLabel");
        final Label endLabel   = new Label("end");

        loadExpressionUnbounded(lhs);    //lhs
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

        return true;
    }

    /**
     * Was this expression or any of its subexpressions deoptimized in the current recompilation chain of rest-of methods?
     * @param rootExpr the expression being tested
     * @return true if the expression or any of its subexpressions was deoptimized in the current recompilation chain.
     */
    private boolean isDeoptimizedExpression(final Expression rootExpr) {
        if(!isRestOf()) {
            return false;
        }
        return new Supplier<Boolean>() {
            boolean contains;
            @Override
            public Boolean get() {
                rootExpr.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                    @Override
                    public boolean enterFunctionNode(final FunctionNode functionNode) {
                        return false;
                    }
                    @Override
                    public boolean enterDefault(final Node node) {
                        if(!contains && node instanceof Optimistic) {
                            final int pp = ((Optimistic)node).getProgramPoint();
                            contains = isValid(pp) && isContinuationEntryPoint(pp);
                        }
                        return !contains;
                    }
                });
                return contains;
            }
        }.get();
    }

    private void loadRuntimeNode(final RuntimeNode runtimeNode) {
        final List<Expression> args = new ArrayList<>(runtimeNode.getArgs());
        if (nullCheck(runtimeNode, args)) {
           return;
        } else if(undefinedCheck(runtimeNode, args)) {
            return;
        }
        // Revert a false undefined check to a strict equality check
        final RuntimeNode newRuntimeNode;
        final Request request = runtimeNode.getRequest();
        if (Request.isUndefinedCheck(request)) {
            newRuntimeNode = runtimeNode.setRequest(request == Request.IS_UNDEFINED ? Request.EQ_STRICT : Request.NE_STRICT);
        } else {
            newRuntimeNode = runtimeNode;
        }

        for (final Expression arg : args) {
            loadExpression(arg, TypeBounds.OBJECT);
        }

        method.invokestatic(
                CompilerConstants.className(ScriptRuntime.class),
                newRuntimeNode.getRequest().toString(),
                new FunctionSignature(
                    false,
                    false,
                    newRuntimeNode.getType(),
                    args.size()).toString());

        method.convert(newRuntimeNode.getType());
    }

    private void defineCommonSplitMethodParameters() {
        defineSplitMethodParameter(0, CALLEE);
        defineSplitMethodParameter(1, THIS);
        defineSplitMethodParameter(2, SCOPE);
    }

    private void defineSplitMethodParameter(final int slot, final CompilerConstants cc) {
        defineSplitMethodParameter(slot, Type.typeFor(cc.type()));
    }

    private void defineSplitMethodParameter(final int slot, final Type type) {
        method.defineBlockLocalVariable(slot, slot + type.getSlots());
        method.onLocalStore(type, slot);
    }

    private int fixScopeSlot(final FunctionNode functionNode, final int extraSlot) {
        // TODO hack to move the scope to the expected slot (needed because split methods reuse the same slots as the root method)
        final int actualScopeSlot = functionNode.compilerConstant(SCOPE).getSlot(SCOPE_TYPE);
        final int defaultScopeSlot = SCOPE.slot();
        int newExtraSlot = extraSlot;
        if (actualScopeSlot != defaultScopeSlot) {
            if (actualScopeSlot == extraSlot) {
                newExtraSlot = extraSlot + 1;
                method.defineBlockLocalVariable(newExtraSlot, newExtraSlot + 1);
                method.load(Type.OBJECT, extraSlot);
                method.storeHidden(Type.OBJECT, newExtraSlot);
            } else {
                method.defineBlockLocalVariable(actualScopeSlot, actualScopeSlot + 1);
            }
            method.load(SCOPE_TYPE, defaultScopeSlot);
            method.storeCompilerConstant(SCOPE);
        }
        return newExtraSlot;
    }

    @Override
    public boolean enterSplitReturn(final SplitReturn splitReturn) {
        if (method.isReachable()) {
            method.loadUndefined(lc.getCurrentFunction().getReturnType())._return();
        }
        return false;
    }

    @Override
    public boolean enterSetSplitState(final SetSplitState setSplitState) {
        if (method.isReachable()) {
            method.setSplitState(setSplitState.getState());
        }
        return false;
    }

    @Override
    public boolean enterSwitchNode(final SwitchNode switchNode) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(switchNode);

        final Expression     expression  = switchNode.getExpression();
        final List<CaseNode> cases       = switchNode.getCases();

        if (cases.isEmpty()) {
            // still evaluate expression for side-effects.
            loadAndDiscard(expression);
            return false;
        }

        final CaseNode defaultCase       = switchNode.getDefaultCase();
        final Label    breakLabel        = switchNode.getBreakLabel();
        final int      liveLocalsOnBreak = method.getUsedSlotsWithLiveTemporaries();

        if (defaultCase != null && cases.size() == 1) {
            // default case only
            assert cases.get(0) == defaultCase;
            loadAndDiscard(expression);
            defaultCase.getBody().accept(this);
            method.breakLabel(breakLabel, liveLocalsOnBreak);
            return false;
        }

        // NOTE: it can still change in the tableswitch/lookupswitch case if there's no default case
        // but we need to add a synthetic default case for local variable conversions
        Label defaultLabel = defaultCase != null ? defaultCase.getEntry() : breakLabel;
        final boolean hasSkipConversion = LocalVariableConversion.hasLiveConversion(switchNode);

        if (switchNode.isUniqueInteger()) {
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
            final long range = (long)hi - (long)lo + 1;

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
            loadExpressionUnbounded(expression);
            final Type type = expression.getType();

            // If expression not int see if we can convert, if not use deflt to trigger default.
            if (!type.isInteger()) {
                method.load(deflt);
                final Class<?> exprClass = type.getTypeClass();
                method.invoke(staticCallNoLookup(ScriptRuntime.class, "switchTagAsInt", int.class, exprClass.isPrimitive()? exprClass : Object.class, int.class));
            }

            if(hasSkipConversion) {
                assert defaultLabel == breakLabel;
                defaultLabel = new Label("switch_skip");
            }
            // TABLESWITCH needs (range + 3) 32-bit values; LOOKUPSWITCH needs ((size * 2) + 2). Choose the one with
            // smaller representation, favor TABLESWITCH when they're equal size.
            if (range + 1 <= (size * 2) && range <= Integer.MAX_VALUE) {
                final Label[] table = new Label[(int)range];
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
            // This is a synthetic "default case" used in absence of actual default case, created if we need to apply
            // local variable conversions if neither case is taken.
            if(hasSkipConversion) {
                method.label(defaultLabel);
                method.beforeJoinPoint(switchNode);
                method._goto(breakLabel);
            }
        } else {
            final Symbol tagSymbol = switchNode.getTag();
            // TODO: we could have non-object tag
            final int tagSlot = tagSymbol.getSlot(Type.OBJECT);
            loadExpressionAsObject(expression);
            method.store(tagSymbol, Type.OBJECT);

            for (final CaseNode caseNode : cases) {
                final Expression test = caseNode.getTest();

                if (test != null) {
                    method.load(Type.OBJECT, tagSlot);
                    loadExpressionAsObject(test);
                    method.invoke(ScriptRuntime.EQ_STRICT);
                    method.ifne(caseNode.getEntry());
                }
            }

            if (defaultCase != null) {
                method._goto(defaultLabel);
            } else {
                method.beforeJoinPoint(switchNode);
                method._goto(breakLabel);
            }
        }

        // First case is only reachable through jump
        assert !method.isReachable();

        for (final CaseNode caseNode : cases) {
            final Label fallThroughLabel;
            if(caseNode.getLocalVariableConversion() != null && method.isReachable()) {
                fallThroughLabel = new Label("fallthrough");
                method._goto(fallThroughLabel);
            } else {
                fallThroughLabel = null;
            }
            method.label(caseNode.getEntry());
            method.beforeJoinPoint(caseNode);
            if(fallThroughLabel != null) {
                method.label(fallThroughLabel);
            }
            caseNode.getBody().accept(this);
        }

        method.breakLabel(breakLabel, liveLocalsOnBreak);

        return false;
    }

    @Override
    public boolean enterThrowNode(final ThrowNode throwNode) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(throwNode);

        if (throwNode.isSyntheticRethrow()) {
            method.beforeJoinPoint(throwNode);

            //do not wrap whatever this is in an ecma exception, just rethrow it
            final IdentNode exceptionExpr = (IdentNode)throwNode.getExpression();
            final Symbol exceptionSymbol = exceptionExpr.getSymbol();
            method.load(exceptionSymbol, EXCEPTION_TYPE);
            method.checkcast(EXCEPTION_TYPE.getTypeClass());
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
        loadExpressionAsObject(expression);

        method.load(source.getName());
        method.load(line);
        method.load(column);
        method.invoke(ECMAException.CREATE);

        method.beforeJoinPoint(throwNode);
        method.athrow();

        return false;
    }

    private Source getCurrentSource() {
        return lc.getCurrentFunction().getSource();
    }

    @Override
    public boolean enterTryNode(final TryNode tryNode) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(tryNode);

        final Block       body        = tryNode.getBody();
        final List<Block> catchBlocks = tryNode.getCatchBlocks();
        final Symbol      vmException = tryNode.getException();
        final Label       entry       = new Label("try");
        final Label       recovery    = new Label("catch");
        final Label       exit        = new Label("end_try");
        final Label       skip        = new Label("skip");

        method.canThrow(recovery);
        // Effect any conversions that might be observed at the entry of the catch node before entering the try node.
        // This is because even the first instruction in the try block must be presumed to be able to transfer control
        // to the catch block. Note that this doesn't kill the original values; in this regard it works a lot like
        // conversions of assignments within the try block.
        method.beforeTry(tryNode, recovery);
        method.label(entry);
        catchLabels.push(recovery);
        try {
            body.accept(this);
        } finally {
            assert catchLabels.peek() == recovery;
            catchLabels.pop();
        }

        method.label(exit);
        final boolean bodyCanThrow = exit.isAfter(entry);
        if(!bodyCanThrow) {
            // The body can't throw an exception; don't even bother emitting the catch handlers, they're all dead code.
            return false;
        }

        method._try(entry, exit, recovery, Throwable.class);

        if (method.isReachable()) {
            method._goto(skip);
        }

        for (final Block inlinedFinally : tryNode.getInlinedFinallies()) {
            TryNode.getLabelledInlinedFinallyBlock(inlinedFinally).accept(this);
            // All inlined finallies end with a jump or a return
            assert !method.isReachable();
        }


        method._catch(recovery);
        method.store(vmException, EXCEPTION_TYPE);

        final int catchBlockCount = catchBlocks.size();
        final Label afterCatch = new Label("after_catch");
        for (int i = 0; i < catchBlockCount; i++) {
            assert method.isReachable();
            final Block catchBlock = catchBlocks.get(i);

            // Because of the peculiarities of the flow control, we need to use an explicit push/enterBlock/leaveBlock
            // here.
            lc.push(catchBlock);
            enterBlock(catchBlock);

            final CatchNode  catchNode          = (CatchNode)catchBlocks.get(i).getStatements().get(0);
            final IdentNode  exception          = catchNode.getException();
            final Expression exceptionCondition = catchNode.getExceptionCondition();
            final Block      catchBody          = catchNode.getBody();

            new Store<IdentNode>(exception) {
                @Override
                protected void storeNonDiscard() {
                    // This expression is neither part of a discard, nor needs to be left on the stack after it was
                    // stored, so we override storeNonDiscard to be a no-op.
                }

                @Override
                protected void evaluate() {
                    if (catchNode.isSyntheticRethrow()) {
                        method.load(vmException, EXCEPTION_TYPE);
                        return;
                    }
                    /*
                     * If caught object is an instance of ECMAException, then
                     * bind obj.thrown to the script catch var. Or else bind the
                     * caught object itself to the script catch var.
                     */
                    final Label notEcmaException = new Label("no_ecma_exception");
                    method.load(vmException, EXCEPTION_TYPE).dup()._instanceof(ECMAException.class).ifeq(notEcmaException);
                    method.checkcast(ECMAException.class); //TODO is this necessary?
                    method.getField(ECMAException.THROWN);
                    method.label(notEcmaException);
                }
            }.store();

            final boolean isConditionalCatch = exceptionCondition != null;
            final Label nextCatch;
            if (isConditionalCatch) {
                loadExpressionAsBoolean(exceptionCondition);
                nextCatch = new Label("next_catch");
                nextCatch.markAsBreakTarget();
                method.ifeq(nextCatch);
            } else {
                nextCatch = null;
            }

            catchBody.accept(this);
            leaveBlock(catchBlock);
            lc.pop(catchBlock);
            if(nextCatch != null) {
                if(method.isReachable()) {
                    method._goto(afterCatch);
                }
                method.breakLabel(nextCatch, lc.getUsedSlotCount());
            }
        }

        // afterCatch could be the same as skip, except that we need to establish that the vmException is dead.
        method.label(afterCatch);
        if(method.isReachable()) {
            method.markDeadLocalVariable(vmException);
        }
        method.label(skip);

        // Finally body is always inlined elsewhere so it doesn't need to be emitted
        assert tryNode.getFinallyBody() == null;

        return false;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        if(!method.isReachable()) {
            return false;
        }
        final Expression init = varNode.getInit();
        final IdentNode identNode = varNode.getName();
        final Symbol identSymbol = identNode.getSymbol();
        assert identSymbol != null : "variable node " + varNode + " requires a name with a symbol";
        final boolean needsScope = identSymbol.isScope();

        if (init == null) {
            if (needsScope && varNode.isBlockScoped()) {
                // block scoped variables need a DECLARE flag to signal end of temporal dead zone (TDZ)
                method.loadCompilerConstant(SCOPE);
                method.loadUndefined(Type.OBJECT);
                final int flags = getScopeCallSiteFlags(identSymbol) | (varNode.isBlockScoped() ? CALLSITE_DECLARE : 0);
                assert isFastScope(identSymbol);
                storeFastScopeVar(identSymbol, flags);
            }
            return false;
        }

        enterStatement(varNode);
        assert method != null;

        if (needsScope) {
            method.loadCompilerConstant(SCOPE);
        }

        if (needsScope) {
            loadExpressionUnbounded(init);
            // block scoped variables need a DECLARE flag to signal end of temporal dead zone (TDZ)
            final int flags = getScopeCallSiteFlags(identSymbol) | (varNode.isBlockScoped() ? CALLSITE_DECLARE : 0);
            if (isFastScope(identSymbol)) {
                storeFastScopeVar(identSymbol, flags);
            } else {
                method.dynamicSet(identNode.getName(), flags, false);
            }
        } else {
            final Type identType = identNode.getType();
            if(identType == Type.UNDEFINED) {
                // The initializer is either itself undefined (explicit assignment of undefined to undefined),
                // or the left hand side is a dead variable.
                assert init.getType() == Type.UNDEFINED || identNode.getSymbol().slotCount() == 0;
                loadAndDiscard(init);
                return false;
            }
            loadExpressionAsType(init, identType);
            storeIdentWithCatchConversion(identNode, identType);
        }

        return false;
    }

    private void storeIdentWithCatchConversion(final IdentNode identNode, final Type type) {
        // Assignments happening in try/catch blocks need to ensure that they also store a possibly wider typed value
        // that will be live at the exit from the try block
        final LocalVariableConversion conversion = identNode.getLocalVariableConversion();
        final Symbol symbol = identNode.getSymbol();
        if(conversion != null && conversion.isLive()) {
            assert symbol == conversion.getSymbol();
            assert symbol.isBytecodeLocal();
            // Only a single conversion from the target type to the join type is expected.
            assert conversion.getNext() == null;
            assert conversion.getFrom() == type;
            // We must propagate potential type change to the catch block
            final Label catchLabel = catchLabels.peek();
            assert catchLabel != METHOD_BOUNDARY; // ident conversion only exists in try blocks
            assert catchLabel.isReachable();
            final Type joinType = conversion.getTo();
            final Label.Stack catchStack = catchLabel.getStack();
            final int joinSlot = symbol.getSlot(joinType);
            // With nested try/catch blocks (incl. synthetic ones for finally), we can have a supposed conversion for
            // the exception symbol in the nested catch, but it isn't live in the outer catch block, so prevent doing
            // conversions for it. E.g. in "try { try { ... } catch(e) { e = 1; } } catch(e2) { ... }", we must not
            // introduce an I->O conversion on "e = 1" assignment as "e" is not live in "catch(e2)".
            if(catchStack.getUsedSlotsWithLiveTemporaries() > joinSlot) {
                method.dup();
                method.convert(joinType);
                method.store(symbol, joinType);
                catchLabel.getStack().onLocalStore(joinType, joinSlot, true);
                method.canThrow(catchLabel);
                // Store but keep the previous store live too.
                method.store(symbol, type, false);
                return;
            }
        }

        method.store(symbol, type, true);
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        if(!method.isReachable()) {
            return false;
        }
        if(whileNode.isDoWhile()) {
            enterDoWhile(whileNode);
        } else {
            enterStatement(whileNode);
            enterForOrWhile(whileNode, null);
        }
        return false;
    }

    private void enterForOrWhile(final LoopNode loopNode, final JoinPredecessorExpression modify) {
        // NOTE: the usual pattern for compiling test-first loops is "GOTO test; body; test; IFNE body". We use the less
        // conventional "test; IFEQ break; body; GOTO test; break;". It has one extra unconditional GOTO in each repeat
        // of the loop, but it's not a problem for modern JIT compilers. We do this because our local variable type
        // tracking is unfortunately not really prepared for out-of-order execution, e.g. compiling the following
        // contrived but legal JavaScript code snippet would fail because the test changes the type of "i" from object
        // to double: var i = {valueOf: function() { return 1} }; while(--i >= 0) { ... }
        // Instead of adding more complexity to the local variable type tracking, we instead choose to emit this
        // different code shape.
        final int liveLocalsOnBreak = method.getUsedSlotsWithLiveTemporaries();
        final JoinPredecessorExpression test = loopNode.getTest();
        if(Expression.isAlwaysFalse(test)) {
            loadAndDiscard(test);
            return;
        }

        method.beforeJoinPoint(loopNode);

        final Label continueLabel = loopNode.getContinueLabel();
        final Label repeatLabel = modify != null ? new Label("for_repeat") : continueLabel;
        method.label(repeatLabel);
        final int liveLocalsOnContinue = method.getUsedSlotsWithLiveTemporaries();

        final Block   body                  = loopNode.getBody();
        final Label   breakLabel            = loopNode.getBreakLabel();
        final boolean testHasLiveConversion = test != null && LocalVariableConversion.hasLiveConversion(test);

        if(Expression.isAlwaysTrue(test)) {
            if(test != null) {
                loadAndDiscard(test);
                if(testHasLiveConversion) {
                    method.beforeJoinPoint(test);
                }
            }
        } else if (test != null) {
            if (testHasLiveConversion) {
                emitBranch(test.getExpression(), body.getEntryLabel(), true);
                method.beforeJoinPoint(test);
                method._goto(breakLabel);
            } else {
                emitBranch(test.getExpression(), breakLabel, false);
            }
        }

        body.accept(this);
        if(repeatLabel != continueLabel) {
            emitContinueLabel(continueLabel, liveLocalsOnContinue);
        }

        if (loopNode.hasPerIterationScope() && lc.getCurrentBlock().needsScope()) {
            // ES6 for loops with LET init need a new scope for each iteration. We just create a shallow copy here.
            method.loadCompilerConstant(SCOPE);
            method.invoke(virtualCallNoLookup(ScriptObject.class, "copy", ScriptObject.class));
            method.storeCompilerConstant(SCOPE);
        }

        if(method.isReachable()) {
            if(modify != null) {
                lineNumber(loopNode);
                loadAndDiscard(modify);
                method.beforeJoinPoint(modify);
            }
            method._goto(repeatLabel);
        }

        method.breakLabel(breakLabel, liveLocalsOnBreak);
    }

    private void emitContinueLabel(final Label continueLabel, final int liveLocals) {
        final boolean reachable = method.isReachable();
        method.breakLabel(continueLabel, liveLocals);
        // If we reach here only through a continue statement (e.g. body does not exit normally) then the
        // continueLabel can have extra non-temp symbols (e.g. exception from a try/catch contained in the body). We
        // must make sure those are thrown away.
        if(!reachable) {
            method.undefineLocalVariables(lc.getUsedSlotCount(), false);
        }
    }

    private void enterDoWhile(final WhileNode whileNode) {
        final int liveLocalsOnContinueOrBreak = method.getUsedSlotsWithLiveTemporaries();
        method.beforeJoinPoint(whileNode);

        final Block body = whileNode.getBody();
        body.accept(this);

        emitContinueLabel(whileNode.getContinueLabel(), liveLocalsOnContinueOrBreak);
        if(method.isReachable()) {
            lineNumber(whileNode);
            final JoinPredecessorExpression test = whileNode.getTest();
            final Label bodyEntryLabel = body.getEntryLabel();
            final boolean testHasLiveConversion = LocalVariableConversion.hasLiveConversion(test);
            if(Expression.isAlwaysFalse(test)) {
                loadAndDiscard(test);
                if(testHasLiveConversion) {
                    method.beforeJoinPoint(test);
                }
            } else if(testHasLiveConversion) {
                // If we have conversions after the test in do-while, they need to be effected on both branches.
                final Label beforeExit = new Label("do_while_preexit");
                emitBranch(test.getExpression(), beforeExit, false);
                method.beforeJoinPoint(test);
                method._goto(bodyEntryLabel);
                method.label(beforeExit);
                method.beforeJoinPoint(test);
            } else {
                emitBranch(test.getExpression(), bodyEntryLabel, true);
            }
        }
        method.breakLabel(whileNode.getBreakLabel(), liveLocalsOnContinueOrBreak);
    }


    @Override
    public boolean enterWithNode(final WithNode withNode) {
        if(!method.isReachable()) {
            return false;
        }
        enterStatement(withNode);
        final Expression expression = withNode.getExpression();
        final Block      body       = withNode.getBody();

        // It is possible to have a "pathological" case where the with block does not reference *any* identifiers. It's
        // pointless, but legal. In that case, if nothing else in the method forced the assignment of a slot to the
        // scope object, its' possible that it won't have a slot assigned. In this case we'll only evaluate expression
        // for its side effect and visit the body, and not bother opening and closing a WithObject.
        final boolean hasScope = method.hasScope();

        if (hasScope) {
            method.loadCompilerConstant(SCOPE);
        }

        loadExpressionAsObject(expression);

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

            method.label(endLabel);
            // Somewhat conservatively presume that if the body is not empty, it can throw an exception. In any case,
            // we must prevent trying to emit a try-catch for empty range, as it causes a verification error.
            final boolean bodyCanThrow = endLabel.isAfter(tryLabel);
            if(bodyCanThrow) {
                method._try(tryLabel, endLabel, catchLabel);
            }

            final boolean reachable = method.isReachable();
            if(reachable) {
                popScope();
                if(bodyCanThrow) {
                    method._goto(exitLabel);
                }
            }

            if(bodyCanThrow) {
                method._catch(catchLabel);
                popScopeException();
                method.athrow();
                if(reachable) {
                    method.label(exitLabel);
                }
            }
        }
        return false;
    }

    private void loadADD(final UnaryNode unaryNode, final TypeBounds resultBounds) {
        loadExpression(unaryNode.getExpression(), resultBounds.booleanToInt().notWiderThan(Type.NUMBER));
        if(method.peekType() == Type.BOOLEAN) {
            // It's a no-op in bytecode, but we must make sure it is treated as an int for purposes of type signatures
            method.convert(Type.INT);
        }
    }

    private void loadBIT_NOT(final UnaryNode unaryNode) {
        loadExpression(unaryNode.getExpression(), TypeBounds.INT).load(-1).xor();
    }

    private void loadDECINC(final UnaryNode unaryNode) {
        final Expression operand     = unaryNode.getExpression();
        final Type       type        = unaryNode.getType();
        final TypeBounds typeBounds  = new TypeBounds(type, Type.NUMBER);
        final TokenType  tokenType   = unaryNode.tokenType();
        final boolean    isPostfix   = tokenType == TokenType.DECPOSTFIX || tokenType == TokenType.INCPOSTFIX;
        final boolean    isIncrement = tokenType == TokenType.INCPREFIX || tokenType == TokenType.INCPOSTFIX;

        assert !type.isObject();

        new SelfModifyingStore<UnaryNode>(unaryNode, operand) {

            private void loadRhs() {
                loadExpression(operand, typeBounds, true);
            }

            @Override
            protected void evaluate() {
                if(isPostfix) {
                    loadRhs();
                } else {
                    new OptimisticOperation(unaryNode, typeBounds) {
                        @Override
                        void loadStack() {
                            loadRhs();
                            loadMinusOne();
                        }
                        @Override
                        void consumeStack() {
                            doDecInc(getProgramPoint());
                        }
                    }.emit(getOptimisticIgnoreCountForSelfModifyingExpression(operand));
                }
            }

            @Override
            protected void storeNonDiscard() {
                super.storeNonDiscard();
                if (isPostfix) {
                    new OptimisticOperation(unaryNode, typeBounds) {
                        @Override
                        void loadStack() {
                            loadMinusOne();
                        }
                        @Override
                        void consumeStack() {
                            doDecInc(getProgramPoint());
                        }
                    }.emit(1); // 1 for non-incremented result on the top of the stack pushed in evaluate()
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

            private void doDecInc(final int programPoint) {
                method.add(programPoint);
            }
        }.store();
    }

    private static int getOptimisticIgnoreCountForSelfModifyingExpression(final Expression target) {
        return target instanceof AccessNode ? 1 : target instanceof IndexNode ? 2 : 0;
    }

    private void loadAndDiscard(final Expression expr) {
        // TODO: move checks for discarding to actual expression load code (e.g. as we do with void). That way we might
        // be able to eliminate even more checks.
        if(expr instanceof PrimitiveLiteralNode | isLocalVariable(expr)) {
            assert !lc.isCurrentDiscard(expr);
            // Don't bother evaluating expressions without side effects. Typical usage is "void 0" for reliably generating
            // undefined.
            return;
        }

        lc.pushDiscard(expr);
        loadExpression(expr, TypeBounds.UNBOUNDED);
        if (lc.popDiscardIfCurrent(expr)) {
            assert !expr.isAssignment();
            // NOTE: if we had a way to load with type void, we could avoid popping
            method.pop();
        }
    }

    /**
     * Loads the expression with the specified type bounds, but if the parent expression is the current discard,
     * then instead loads and discards the expression.
     * @param parent the parent expression that's tested for being the current discard
     * @param expr the expression that's either normally loaded or discard-loaded
     * @param resultBounds result bounds for when loading the expression normally
     */
    private void loadMaybeDiscard(final Expression parent, final Expression expr, final TypeBounds resultBounds) {
        loadMaybeDiscard(lc.popDiscardIfCurrent(parent), expr, resultBounds);
    }

    /**
     * Loads the expression with the specified type bounds, or loads and discards the expression, depending on the
     * value of the discard flag. Useful as a helper for expressions with control flow where you often can't combine
     * testing for being the current discard and loading the subexpressions.
     * @param discard if true, the expression is loaded and discarded
     * @param expr the expression that's either normally loaded or discard-loaded
     * @param resultBounds result bounds for when loading the expression normally
     */
    private void loadMaybeDiscard(final boolean discard, final Expression expr, final TypeBounds resultBounds) {
        if (discard) {
            loadAndDiscard(expr);
        } else {
            loadExpression(expr, resultBounds);
        }
    }

    private void loadNEW(final UnaryNode unaryNode) {
        final CallNode callNode = (CallNode)unaryNode.getExpression();
        final List<Expression> args   = callNode.getArgs();

        final Expression func = callNode.getFunction();
        // Load function reference.
        loadExpressionAsObject(func); // must detect type error

        method.dynamicNew(1 + loadArgs(args), getCallSiteFlags(), func.toString(false));
    }

    private void loadNOT(final UnaryNode unaryNode) {
        final Expression expr = unaryNode.getExpression();
        if(expr instanceof UnaryNode && expr.isTokenType(TokenType.NOT)) {
            // !!x is idiomatic boolean cast in JavaScript
            loadExpressionAsBoolean(((UnaryNode)expr).getExpression());
        } else {
            final Label trueLabel  = new Label("true");
            final Label afterLabel = new Label("after");

            emitBranch(expr, trueLabel, true);
            method.load(true);
            method._goto(afterLabel);
            method.label(trueLabel);
            method.load(false);
            method.label(afterLabel);
        }
    }

    private void loadSUB(final UnaryNode unaryNode, final TypeBounds resultBounds) {
        final Type type = unaryNode.getType();
        assert type.isNumeric();
        final TypeBounds numericBounds = resultBounds.booleanToInt();
        new OptimisticOperation(unaryNode, numericBounds) {
            @Override
            void loadStack() {
                final Expression expr = unaryNode.getExpression();
                loadExpression(expr, numericBounds.notWiderThan(Type.NUMBER));
            }
            @Override
            void consumeStack() {
                // Must do an explicit conversion to the operation's type when it's double so that we correctly handle
                // negation of an int 0 to a double -0. With this, we get the correct negation of a local variable after
                // it deoptimized, e.g. "iload_2; i2d; dneg". Without this, we get "iload_2; ineg; i2d".
                if(type.isNumber()) {
                    method.convert(type);
                }
                method.neg(getProgramPoint());
            }
        }.emit();
    }

    public void loadVOID(final UnaryNode unaryNode, final TypeBounds resultBounds) {
        loadAndDiscard(unaryNode.getExpression());
        if (!lc.popDiscardIfCurrent(unaryNode)) {
            method.loadUndefined(resultBounds.widest);
        }
    }

    public void loadADD(final BinaryNode binaryNode, final TypeBounds resultBounds) {
        new OptimisticOperation(binaryNode, resultBounds) {
            @Override
            void loadStack() {
                final TypeBounds operandBounds;
                final boolean isOptimistic = isValid(getProgramPoint());
                boolean forceConversionSeparation = false;
                if(isOptimistic) {
                    operandBounds = new TypeBounds(binaryNode.getType(), Type.OBJECT);
                } else {
                    // Non-optimistic, non-FP +. Allow it to overflow.
                    final Type widestOperationType = binaryNode.getWidestOperationType();
                    operandBounds = new TypeBounds(Type.narrowest(binaryNode.getWidestOperandType(), resultBounds.widest), widestOperationType);
                    forceConversionSeparation = widestOperationType.narrowerThan(resultBounds.widest);
                }
                loadBinaryOperands(binaryNode.lhs(), binaryNode.rhs(), operandBounds, false, forceConversionSeparation);
            }

            @Override
            void consumeStack() {
                method.add(getProgramPoint());
            }
        }.emit();
    }

    private void loadAND_OR(final BinaryNode binaryNode, final TypeBounds resultBounds, final boolean isAnd) {
        final Type narrowestOperandType = Type.widestReturnType(binaryNode.lhs().getType(), binaryNode.rhs().getType());

        final boolean isCurrentDiscard = lc.popDiscardIfCurrent(binaryNode);

        final Label skip = new Label("skip");
        if(narrowestOperandType == Type.BOOLEAN) {
            // optimize all-boolean logical expressions
            final Label onTrue = new Label("andor_true");
            emitBranch(binaryNode, onTrue, true);
            if (isCurrentDiscard) {
                method.label(onTrue);
            } else {
                method.load(false);
                method._goto(skip);
                method.label(onTrue);
                method.load(true);
                method.label(skip);
            }
            return;
        }

        final TypeBounds outBounds = resultBounds.notNarrowerThan(narrowestOperandType);
        final JoinPredecessorExpression lhs = (JoinPredecessorExpression)binaryNode.lhs();
        final boolean lhsConvert = LocalVariableConversion.hasLiveConversion(lhs);
        final Label evalRhs = lhsConvert ? new Label("eval_rhs") : null;

        loadExpression(lhs, outBounds);
        if (!isCurrentDiscard) {
            method.dup();
        }
        method.convert(Type.BOOLEAN);
        if (isAnd) {
            if(lhsConvert) {
                method.ifne(evalRhs);
            } else {
                method.ifeq(skip);
            }
        } else if(lhsConvert) {
            method.ifeq(evalRhs);
        } else {
            method.ifne(skip);
        }

        if(lhsConvert) {
            method.beforeJoinPoint(lhs);
            method._goto(skip);
            method.label(evalRhs);
        }

        if (!isCurrentDiscard) {
            method.pop();
        }
        final JoinPredecessorExpression rhs = (JoinPredecessorExpression)binaryNode.rhs();
        loadMaybeDiscard(isCurrentDiscard, rhs, outBounds);
        method.beforeJoinPoint(rhs);
        method.label(skip);
    }

    private static boolean isLocalVariable(final Expression lhs) {
        return lhs instanceof IdentNode && isLocalVariable((IdentNode)lhs);
    }

    private static boolean isLocalVariable(final IdentNode lhs) {
        return lhs.getSymbol().isBytecodeLocal();
    }

    // NOTE: does not use resultBounds as the assignment is driven by the type of the RHS
    private void loadASSIGN(final BinaryNode binaryNode) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        final Type rhsType = rhs.getType();
        // Detect dead assignments
        if(lhs instanceof IdentNode) {
            final Symbol symbol = ((IdentNode)lhs).getSymbol();
            if(!symbol.isScope() && !symbol.hasSlotFor(rhsType) && lc.popDiscardIfCurrent(binaryNode)) {
                loadAndDiscard(rhs);
                method.markDeadLocalVariable(symbol);
                return;
            }
        }

        new Store<BinaryNode>(binaryNode, lhs) {
            @Override
            protected void evaluate() {
                // NOTE: we're loading with "at least as wide as" so optimistic operations on the right hand side
                // remain optimistic, and then explicitly convert to the required type if needed.
                loadExpressionAsType(rhs, rhsType);
            }
        }.store();
    }

    /**
     * Binary self-assignment that can be optimistic: +=, -=, *=, and /=.
     */
    private abstract class BinaryOptimisticSelfAssignment extends SelfModifyingStore<BinaryNode> {

        /**
         * Constructor
         *
         * @param node the assign op node
         */
        BinaryOptimisticSelfAssignment(final BinaryNode node) {
            super(node, node.lhs());
        }

        protected abstract void op(OptimisticOperation oo);

        @Override
        protected void evaluate() {
            final Expression lhs = assignNode.lhs();
            final Expression rhs = assignNode.rhs();
            final Type widestOperationType = assignNode.getWidestOperationType();
            final TypeBounds bounds = new TypeBounds(assignNode.getType(), widestOperationType);
            new OptimisticOperation(assignNode, bounds) {
                @Override
                void loadStack() {
                    final boolean forceConversionSeparation;
                    if (isValid(getProgramPoint()) || widestOperationType == Type.NUMBER) {
                        forceConversionSeparation = false;
                    } else {
                        final Type operandType = Type.widest(booleanToInt(objectToNumber(lhs.getType())), booleanToInt(objectToNumber(rhs.getType())));
                        forceConversionSeparation = operandType.narrowerThan(widestOperationType);
                    }
                    loadBinaryOperands(lhs, rhs, bounds, true, forceConversionSeparation);
                }
                @Override
                void consumeStack() {
                    op(this);
                }
            }.emit(getOptimisticIgnoreCountForSelfModifyingExpression(lhs));
            method.convert(assignNode.getType());
        }
    }

    /**
     * Non-optimistic binary self-assignment operation. Basically, everything except +=, -=, *=, and /=.
     */
    private abstract class BinarySelfAssignment extends SelfModifyingStore<BinaryNode> {
        BinarySelfAssignment(final BinaryNode node) {
            super(node, node.lhs());
        }

        protected abstract void op();

        @Override
        protected void evaluate() {
            loadBinaryOperands(assignNode.lhs(), assignNode.rhs(), TypeBounds.UNBOUNDED.notWiderThan(assignNode.getWidestOperandType()), true, false);
            op();
        }
    }

    private void loadASSIGN_ADD(final BinaryNode binaryNode) {
        new BinaryOptimisticSelfAssignment(binaryNode) {
            @Override
            protected void op(final OptimisticOperation oo) {
                assert !(binaryNode.getType().isObject() && oo.isOptimistic);
                method.add(oo.getProgramPoint());
            }
        }.store();
    }

    private void loadASSIGN_BIT_AND(final BinaryNode binaryNode) {
        new BinarySelfAssignment(binaryNode) {
            @Override
            protected void op() {
                method.and();
            }
        }.store();
    }

    private void loadASSIGN_BIT_OR(final BinaryNode binaryNode) {
        new BinarySelfAssignment(binaryNode) {
            @Override
            protected void op() {
                method.or();
            }
        }.store();
    }

    private void loadASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        new BinarySelfAssignment(binaryNode) {
            @Override
            protected void op() {
                method.xor();
            }
        }.store();
    }

    private void loadASSIGN_DIV(final BinaryNode binaryNode) {
        new BinaryOptimisticSelfAssignment(binaryNode) {
            @Override
            protected void op(final OptimisticOperation oo) {
                method.div(oo.getProgramPoint());
            }
        }.store();
    }

    private void loadASSIGN_MOD(final BinaryNode binaryNode) {
        new BinaryOptimisticSelfAssignment(binaryNode) {
            @Override
            protected void op(final OptimisticOperation oo) {
                method.rem(oo.getProgramPoint());
            }
        }.store();
    }

    private void loadASSIGN_MUL(final BinaryNode binaryNode) {
        new BinaryOptimisticSelfAssignment(binaryNode) {
            @Override
            protected void op(final OptimisticOperation oo) {
                method.mul(oo.getProgramPoint());
            }
        }.store();
    }

    private void loadASSIGN_SAR(final BinaryNode binaryNode) {
        new BinarySelfAssignment(binaryNode) {
            @Override
            protected void op() {
                method.sar();
            }
        }.store();
    }

    private void loadASSIGN_SHL(final BinaryNode binaryNode) {
        new BinarySelfAssignment(binaryNode) {
            @Override
            protected void op() {
                method.shl();
            }
        }.store();
    }

    private void loadASSIGN_SHR(final BinaryNode binaryNode) {
        new BinarySelfAssignment(binaryNode) {
            @Override
            protected void op() {
                doSHR();
            }

        }.store();
    }

    private void doSHR() {
        // TODO: make SHR optimistic
        method.shr();
        toUint();
    }

    private void toUint() {
        JSType.TO_UINT32_I.invoke(method);
    }

    private void loadASSIGN_SUB(final BinaryNode binaryNode) {
        new BinaryOptimisticSelfAssignment(binaryNode) {
            @Override
            protected void op(final OptimisticOperation oo) {
                method.sub(oo.getProgramPoint());
            }
        }.store();
    }

    /**
     * Helper class for binary arithmetic ops
     */
    private abstract class BinaryArith {
        protected abstract void op(int programPoint);

        protected void evaluate(final BinaryNode node, final TypeBounds resultBounds) {
            final TypeBounds numericBounds = resultBounds.booleanToInt().objectToNumber();
            new OptimisticOperation(node, numericBounds) {
                @Override
                void loadStack() {
                    final TypeBounds operandBounds;
                    boolean forceConversionSeparation = false;
                    if(numericBounds.narrowest == Type.NUMBER) {
                        // Result should be double always. Propagate it into the operands so we don't have lots of I2D
                        // and L2D after operand evaluation.
                        assert numericBounds.widest == Type.NUMBER;
                        operandBounds = numericBounds;
                    } else {
                        final boolean isOptimistic = isValid(getProgramPoint());
                        if(isOptimistic || node.isTokenType(TokenType.DIV) || node.isTokenType(TokenType.MOD)) {
                            operandBounds = new TypeBounds(node.getType(), Type.NUMBER);
                        } else {
                            // Non-optimistic, non-FP subtraction or multiplication. Allow them to overflow.
                            operandBounds = new TypeBounds(Type.narrowest(node.getWidestOperandType(),
                                    numericBounds.widest), Type.NUMBER);
                            forceConversionSeparation = node.getWidestOperationType().narrowerThan(numericBounds.widest);
                        }
                    }
                    loadBinaryOperands(node.lhs(), node.rhs(), operandBounds, false, forceConversionSeparation);
                }

                @Override
                void consumeStack() {
                    op(getProgramPoint());
                }
            }.emit();
        }
    }

    private void loadBIT_AND(final BinaryNode binaryNode) {
        loadBinaryOperands(binaryNode);
        method.and();
    }

    private void loadBIT_OR(final BinaryNode binaryNode) {
        // Optimize x|0 to (int)x
        if (isRhsZero(binaryNode)) {
            loadExpressionAsType(binaryNode.lhs(), Type.INT);
        } else {
            loadBinaryOperands(binaryNode);
            method.or();
        }
    }

    private static boolean isRhsZero(final BinaryNode binaryNode) {
        final Expression rhs = binaryNode.rhs();
        return rhs instanceof LiteralNode && INT_ZERO.equals(((LiteralNode<?>)rhs).getValue());
    }

    private void loadBIT_XOR(final BinaryNode binaryNode) {
        loadBinaryOperands(binaryNode);
        method.xor();
    }

    private void loadCOMMARIGHT(final BinaryNode binaryNode, final TypeBounds resultBounds) {
        loadAndDiscard(binaryNode.lhs());
        loadMaybeDiscard(binaryNode, binaryNode.rhs(), resultBounds);
    }

    private void loadCOMMALEFT(final BinaryNode binaryNode, final TypeBounds resultBounds) {
        loadMaybeDiscard(binaryNode, binaryNode.lhs(), resultBounds);
        loadAndDiscard(binaryNode.rhs());
    }

    private void loadDIV(final BinaryNode binaryNode, final TypeBounds resultBounds) {
        new BinaryArith() {
            @Override
            protected void op(final int programPoint) {
                method.div(programPoint);
            }
        }.evaluate(binaryNode, resultBounds);
    }

    private void loadCmp(final BinaryNode binaryNode, final Condition cond) {
        loadComparisonOperands(binaryNode);

        final Label trueLabel  = new Label("trueLabel");
        final Label afterLabel = new Label("skip");

        method.conditionalJump(cond, trueLabel);

        method.load(Boolean.FALSE);
        method._goto(afterLabel);
        method.label(trueLabel);
        method.load(Boolean.TRUE);
        method.label(afterLabel);
    }

    private void loadMOD(final BinaryNode binaryNode, final TypeBounds resultBounds) {
        new BinaryArith() {
            @Override
            protected void op(final int programPoint) {
                method.rem(programPoint);
            }
        }.evaluate(binaryNode, resultBounds);
    }

    private void loadMUL(final BinaryNode binaryNode, final TypeBounds resultBounds) {
        new BinaryArith() {
            @Override
            protected void op(final int programPoint) {
                method.mul(programPoint);
            }
        }.evaluate(binaryNode, resultBounds);
    }

    private void loadSAR(final BinaryNode binaryNode) {
        loadBinaryOperands(binaryNode);
        method.sar();
    }

    private void loadSHL(final BinaryNode binaryNode) {
        loadBinaryOperands(binaryNode);
        method.shl();
    }

    private void loadSHR(final BinaryNode binaryNode) {
        // Optimize x >>> 0 to (uint)x
        if (isRhsZero(binaryNode)) {
            loadExpressionAsType(binaryNode.lhs(), Type.INT);
            toUint();
        } else {
            loadBinaryOperands(binaryNode);
            doSHR();
        }
    }

    private void loadSUB(final BinaryNode binaryNode, final TypeBounds resultBounds) {
        new BinaryArith() {
            @Override
            protected void op(final int programPoint) {
                method.sub(programPoint);
            }
        }.evaluate(binaryNode, resultBounds);
    }

    @Override
    public boolean enterLabelNode(final LabelNode labelNode) {
        labeledBlockBreakLiveLocals.push(lc.getUsedSlotCount());
        return true;
    }

    @Override
    protected boolean enterDefault(final Node node) {
        throw new AssertionError("Code generator entered node of type " + node.getClass().getName());
    }

    private void loadTernaryNode(final TernaryNode ternaryNode, final TypeBounds resultBounds) {
        final Expression test = ternaryNode.getTest();
        final JoinPredecessorExpression trueExpr  = ternaryNode.getTrueExpression();
        final JoinPredecessorExpression falseExpr = ternaryNode.getFalseExpression();

        final Label falseLabel = new Label("ternary_false");
        final Label exitLabel  = new Label("ternary_exit");

        final Type outNarrowest = Type.narrowest(resultBounds.widest, Type.generic(Type.widestReturnType(trueExpr.getType(), falseExpr.getType())));
        final TypeBounds outBounds = resultBounds.notNarrowerThan(outNarrowest);

        emitBranch(test, falseLabel, false);

        final boolean isCurrentDiscard = lc.popDiscardIfCurrent(ternaryNode);
        loadMaybeDiscard(isCurrentDiscard, trueExpr.getExpression(), outBounds);
        assert isCurrentDiscard || Type.generic(method.peekType()) == outBounds.narrowest;
        method.beforeJoinPoint(trueExpr);
        method._goto(exitLabel);
        method.label(falseLabel);
        loadMaybeDiscard(isCurrentDiscard, falseExpr.getExpression(), outBounds);
        assert isCurrentDiscard || Type.generic(method.peekType()) == outBounds.narrowest;
        method.beforeJoinPoint(falseExpr);
        method.label(exitLabel);
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
     * @param function the function we are in
     * @param ident identifier for block or function where applicable
     */
    private void printSymbols(final Block block, final FunctionNode function, final String ident) {
        if (compiler.getScriptEnvironment()._print_symbols || function.getFlag(FunctionNode.IS_PRINT_SYMBOLS)) {
            final PrintWriter out = compiler.getScriptEnvironment().getErr();
            out.println("[BLOCK in '" + ident + "']");
            if (!block.printSymbols(out)) {
                out.println("<no symbols>");
            }
            out.println();
        }
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
        private IdentNode quick;

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
            /*
             * This loads the parts of the target, e.g base and index. they are kept
             * on the stack throughout the store and used at the end to execute it
             */

            target.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                @Override
                public boolean enterIdentNode(final IdentNode node) {
                    if (node.getSymbol().isScope()) {
                        method.loadCompilerConstant(SCOPE);
                        depth += Type.SCOPE.getSlots();
                        assert depth == 1;
                    }
                    return false;
                }

                private void enterBaseNode() {
                    assert target instanceof BaseNode : "error - base node " + target + " must be instanceof BaseNode";
                    final BaseNode   baseNode = (BaseNode)target;
                    final Expression base     = baseNode.getBase();

                    loadExpressionAsObject(base);
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
                        loadExpressionAsObject(index);
                    } else {
                        loadExpressionUnbounded(index);
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

        /**
         * Generates an extra local variable, always using the same slot, one that is available after the end of the
         * frame.
         *
         * @param type the type of the variable
         *
         * @return the quick variable
         */
        private IdentNode quickLocalVariable(final Type type) {
            final String name = lc.getCurrentFunction().uniqueName(QUICK_PREFIX.symbolName());
            final Symbol symbol = new Symbol(name, IS_INTERNAL | HAS_SLOT);
            symbol.setHasSlotFor(type);
            symbol.setFirstSlot(lc.quickSlot(type));

            final IdentNode quickIdent = IdentNode.createInternalIdentifier(symbol).setType(type);

            return quickIdent;
        }

        // store the result that "lives on" after the op, e.g. "i" in i++ postfix.
        protected void storeNonDiscard() {
            if (lc.popDiscardIfCurrent(assignNode)) {
                assert assignNode.isAssignment();
                return;
            }

            if (method.dup(depth) == null) {
                method.dup();
                final Type quickType = method.peekType();
                this.quick = quickLocalVariable(quickType);
                final Symbol quickSymbol = quick.getSymbol();
                method.storeTemp(quickType, quickSymbol.getFirstSlot());
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
                        final int flags = getScopeCallSiteFlags(symbol);
                        if (isFastScope(symbol)) {
                            storeFastScopeVar(symbol, flags);
                        } else {
                            method.dynamicSet(node.getName(), flags, false);
                        }
                    } else {
                        final Type storeType = assignNode.getType();
                        if (symbol.hasSlotFor(storeType)) {
                            // Only emit a convert for a store known to be live; converts for dead stores can
                            // give us an unnecessary ClassCastException.
                            method.convert(storeType);
                        }
                        storeIdentWithCatchConversion(node, storeType);
                    }
                    return false;

                }

                @Override
                public boolean enterAccessNode(final AccessNode node) {
                    method.dynamicSet(node.getProperty(), getCallSiteFlags(), node.isIndex());
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
            if (target instanceof IdentNode) {
                checkTemporalDeadZone((IdentNode)target);
            }
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

        final RecompilableScriptFunctionData data = compiler.getScriptFunctionData(functionNode.getId());

        if (functionNode.isProgram() && !compiler.isOnDemandCompilation()) {
            final MethodEmitter createFunction = functionNode.getCompileUnit().getClassEmitter().method(
                    EnumSet.of(Flag.PUBLIC, Flag.STATIC), CREATE_PROGRAM_FUNCTION.symbolName(),
                    ScriptFunction.class, ScriptObject.class);
            createFunction.begin();
            loadConstantsAndIndex(data, createFunction);
            createFunction.load(SCOPE_TYPE, 0);
            createFunction.invoke(CREATE_FUNCTION_OBJECT);
            createFunction._return();
            createFunction.end();
        }

        if (addInitializer && !compiler.isOnDemandCompilation()) {
            functionNode.getCompileUnit().addFunctionInitializer(data, functionNode);
        }

        // We don't emit a ScriptFunction on stack for the outermost compiled function (as there's no code being
        // generated in its outer context that'd need it as a callee).
        if (lc.getOutermostFunction() == functionNode) {
            return;
        }

        loadConstantsAndIndex(data, method);

        if (functionNode.needsParentScope()) {
            method.loadCompilerConstant(SCOPE);
            method.invoke(CREATE_FUNCTION_OBJECT);
        } else {
            method.invoke(CREATE_FUNCTION_OBJECT_NO_SCOPE);
        }
    }

    // calls on Global class.
    private MethodEmitter globalInstance() {
        return method.invokestatic(GLOBAL_OBJECT, "instance", "()L" + GLOBAL_OBJECT + ';');
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
                methodDescriptor(Object.class, Object.class, Object.class, Object.class, Object.class, boolean.class));
    }

    private abstract class OptimisticOperation {
        private final boolean isOptimistic;
        // expression and optimistic are the same reference
        private final Expression expression;
        private final Optimistic optimistic;
        private final TypeBounds resultBounds;

        OptimisticOperation(final Optimistic optimistic, final TypeBounds resultBounds) {
            this.optimistic = optimistic;
            this.expression = (Expression)optimistic;
            this.resultBounds = resultBounds;
            this.isOptimistic = isOptimistic(optimistic) && useOptimisticTypes() &&
                    // Operation is only effectively optimistic if its type, after being coerced into the result bounds
                    // is narrower than the upper bound.
                    resultBounds.within(Type.generic(((Expression)optimistic).getType())).narrowerThan(resultBounds.widest);
        }

        MethodEmitter emit() {
            return emit(0);
        }

        MethodEmitter emit(final int ignoredArgCount) {
            final int     programPoint                  = optimistic.getProgramPoint();
            final boolean optimisticOrContinuation      = isOptimistic || isContinuationEntryPoint(programPoint);
            final boolean currentContinuationEntryPoint = isCurrentContinuationEntryPoint(programPoint);
            final int     stackSizeOnEntry              = method.getStackSize() - ignoredArgCount;

            // First store the values on the stack opportunistically into local variables. Doing it before loadStack()
            // allows us to not have to pop/load any arguments that are pushed onto it by loadStack() in the second
            // storeStack().
            storeStack(ignoredArgCount, optimisticOrContinuation);

            // Now, load the stack
            loadStack();

            // Now store the values on the stack ultimately into local variables. In vast majority of cases, this is
            // (aside from creating the local types map) a no-op, as the first opportunistic stack store will already
            // store all variables. However, there can be operations in the loadStack() that invalidate some of the
            // stack stores, e.g. in "x[i] = x[++i]", "++i" will invalidate the already stored value for "i". In such
            // unfortunate cases this second storeStack() will restore the invariant that everything on the stack is
            // stored into a local variable, although at the cost of doing a store/load on the loaded arguments as well.
            final int liveLocalsCount = storeStack(method.getStackSize() - stackSizeOnEntry, optimisticOrContinuation);
            assert optimisticOrContinuation == (liveLocalsCount != -1);

            final Label beginTry;
            final Label catchLabel;
            final Label afterConsumeStack = isOptimistic || currentContinuationEntryPoint ? new Label("after_consume_stack") : null;
            if(isOptimistic) {
                beginTry = new Label("try_optimistic");
                final String catchLabelName = (afterConsumeStack == null ? "" : afterConsumeStack.toString()) + "_handler";
                catchLabel = new Label(catchLabelName);
                method.label(beginTry);
            } else {
                beginTry = catchLabel = null;
            }

            consumeStack();

            if(isOptimistic) {
                method._try(beginTry, afterConsumeStack, catchLabel, UnwarrantedOptimismException.class);
            }

            if(isOptimistic || currentContinuationEntryPoint) {
                method.label(afterConsumeStack);

                final int[] localLoads = method.getLocalLoadsOnStack(0, stackSizeOnEntry);
                assert everyStackValueIsLocalLoad(localLoads) : Arrays.toString(localLoads) + ", " + stackSizeOnEntry + ", " + ignoredArgCount;
                final List<Type> localTypesList = method.getLocalVariableTypes();
                final int usedLocals = method.getUsedSlotsWithLiveTemporaries();
                final List<Type> localTypes = method.getWidestLiveLocals(localTypesList.subList(0, usedLocals));
                assert everyLocalLoadIsValid(localLoads, usedLocals) : Arrays.toString(localLoads) + " ~ " + localTypes;

                if(isOptimistic) {
                    addUnwarrantedOptimismHandlerLabel(localTypes, catchLabel);
                }
                if(currentContinuationEntryPoint) {
                    final ContinuationInfo ci = getContinuationInfo();
                    assert ci != null : "no continuation info found for " + lc.getCurrentFunction();
                    assert !ci.hasTargetLabel(); // No duplicate program points
                    ci.setTargetLabel(afterConsumeStack);
                    ci.getHandlerLabel().markAsOptimisticContinuationHandlerFor(afterConsumeStack);
                    // Can't rely on targetLabel.stack.localVariableTypes.length, as it can be higher due to effectively
                    // dead local variables.
                    ci.lvarCount = localTypes.size();
                    ci.setStackStoreSpec(localLoads);
                    ci.setStackTypes(Arrays.copyOf(method.getTypesFromStack(method.getStackSize()), stackSizeOnEntry));
                    assert ci.getStackStoreSpec().length == ci.getStackTypes().length;
                    ci.setReturnValueType(method.peekType());
                    ci.lineNumber = getLastLineNumber();
                    ci.catchLabel = catchLabels.peek();
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
        private int storeStack(final int ignoreArgCount, final boolean optimisticOrContinuation) {
            if(!optimisticOrContinuation) {
                return -1; // NOTE: correct value to return is lc.getUsedSlotCount(), but it wouldn't be used anyway
            }

            final int stackSize = method.getStackSize();
            final Type[] stackTypes = method.getTypesFromStack(stackSize);
            final int[] localLoadsOnStack = method.getLocalLoadsOnStack(0, stackSize);
            final int usedSlots = method.getUsedSlotsWithLiveTemporaries();

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
                    method.storeTemp(type, lastTempSlot);
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

        private void addUnwarrantedOptimismHandlerLabel(final List<Type> localTypes, final Label label) {
            final String lvarTypesDescriptor = getLvarTypesDescriptor(localTypes);
            final Map<String, Collection<Label>> unwarrantedOptimismHandlers = lc.getUnwarrantedOptimismHandlers();
            Collection<Label> labels = unwarrantedOptimismHandlers.get(lvarTypesDescriptor);
            if(labels == null) {
                labels = new LinkedList<>();
                unwarrantedOptimismHandlers.put(lvarTypesDescriptor, labels);
            }
            method.markLabelAsOptimisticCatchHandler(label, localTypes.size());
            labels.add(label);
        }

        abstract void loadStack();

        // Make sure that whatever indy call site you emit from this method uses {@code getCallSiteFlagsOptimistic(node)}
        // or otherwise ensure optimistic flag is correctly set in the call site, otherwise it doesn't make much sense
        // to use OptimisticExpression for emitting it.
        abstract void consumeStack();

        /**
         * Emits the correct dynamic getter code. Normally just delegates to method emitter, except when the target
         * expression is optimistic, and the desired type is narrower than the optimistic type. In that case, it'll emit a
         * dynamic getter with its original optimistic type, and explicitly insert a narrowing conversion. This way we can
         * preserve the optimism of the values even if they're subsequently immediately coerced into a narrower type. This
         * is beneficial because in this case we can still presume that since the original getter was optimistic, the
         * conversion has no side effects.
         * @param name the name of the property being get
         * @param flags call site flags
         * @param isMethod whether we're preferably retrieving a function
         * @return the current method emitter
         */
        MethodEmitter dynamicGet(final String name, final int flags, final boolean isMethod, final boolean isIndex) {
            if(isOptimistic) {
                return method.dynamicGet(getOptimisticCoercedType(), name, getOptimisticFlags(flags), isMethod, isIndex);
            }
            return method.dynamicGet(resultBounds.within(expression.getType()), name, nonOptimisticFlags(flags), isMethod, isIndex);
        }

        MethodEmitter dynamicGetIndex(final int flags, final boolean isMethod) {
            if(isOptimistic) {
                return method.dynamicGetIndex(getOptimisticCoercedType(), getOptimisticFlags(flags), isMethod);
            }
            return method.dynamicGetIndex(resultBounds.within(expression.getType()), nonOptimisticFlags(flags), isMethod);
        }

        MethodEmitter dynamicCall(final int argCount, final int flags, final String msg) {
            if (isOptimistic) {
                return method.dynamicCall(getOptimisticCoercedType(), argCount, getOptimisticFlags(flags), msg);
            }
            return method.dynamicCall(resultBounds.within(expression.getType()), argCount, nonOptimisticFlags(flags), msg);
        }

        int getOptimisticFlags(final int flags) {
            return flags | CALLSITE_OPTIMISTIC | (optimistic.getProgramPoint() << CALLSITE_PROGRAM_POINT_SHIFT); //encode program point in high bits
        }

        int getProgramPoint() {
            return isOptimistic ? optimistic.getProgramPoint() : INVALID_PROGRAM_POINT;
        }

        void convertOptimisticReturnValue() {
            if (isOptimistic) {
                final Type optimisticType = getOptimisticCoercedType();
                if(!optimisticType.isObject()) {
                    method.load(optimistic.getProgramPoint());
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
        }

        void replaceCompileTimeProperty() {
            final IdentNode identNode = (IdentNode)expression;
            final String name = identNode.getSymbol().getName();
            if (CompilerConstants.__FILE__.name().equals(name)) {
                replaceCompileTimeProperty(getCurrentSource().getName());
            } else if (CompilerConstants.__DIR__.name().equals(name)) {
                replaceCompileTimeProperty(getCurrentSource().getBase());
            } else if (CompilerConstants.__LINE__.name().equals(name)) {
                replaceCompileTimeProperty(getCurrentSource().getLine(identNode.position()));
            }
        }

        /**
         * When an ident with name __FILE__, __DIR__, or __LINE__ is loaded, we'll try to look it up as any other
         * identifier. However, if it gets all the way up to the Global object, it will send back a special value that
         * represents a placeholder for these compile-time location properties. This method will generate code that loads
         * the value of the compile-time location property and then invokes a method in Global that will replace the
         * placeholder with the value. Effectively, if the symbol for these properties is defined anywhere in the lexical
         * scope, they take precedence, but if they aren't, then they resolve to the compile-time location property.
         * @param propertyValue the actual value of the property
         */
        private void replaceCompileTimeProperty(final Object propertyValue) {
            assert method.peekType().isObject();
            if(propertyValue instanceof String || propertyValue == null) {
                method.load((String)propertyValue);
            } else if(propertyValue instanceof Integer) {
                method.load(((Integer)propertyValue));
                method.convert(Type.OBJECT);
            } else {
                throw new AssertionError();
            }
            globalReplaceLocationPropertyPlaceholder();
            convertOptimisticReturnValue();
        }

        /**
         * Returns the type that should be used as the return type of the dynamic invocation that is emitted as the code
         * for the current optimistic operation. If the type bounds is exact boolean or narrower than the expression's
         * optimistic type, then the optimistic type is returned, otherwise the coercing type. Effectively, this method
         * allows for moving the coercion into the optimistic type when it won't adversely affect the optimistic
         * evaluation semantics, and for preserving the optimistic type and doing a separate coercion when it would
         * affect it.
         * @return
         */
        private Type getOptimisticCoercedType() {
            final Type optimisticType = expression.getType();
            assert resultBounds.widest.widerThan(optimisticType);
            final Type narrowest = resultBounds.narrowest;

            if(narrowest.isBoolean() || narrowest.narrowerThan(optimisticType)) {
                assert !optimisticType.isObject();
                return optimisticType;
            }
            assert !narrowest.isObject();
            return narrowest;
        }
    }

    private static boolean isOptimistic(final Optimistic optimistic) {
        if(!optimistic.canBeOptimistic()) {
            return false;
        }
        final Expression expr = (Expression)optimistic;
        return expr.getType().narrowerThan(expr.getWidestOperationType());
    }

    private static boolean everyLocalLoadIsValid(final int[] loads, final int localCount) {
        for (final int load : loads) {
            if(load < 0 || load >= localCount) {
                return false;
            }
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

    private String getLvarTypesDescriptor(final List<Type> localVarTypes) {
        final int count = localVarTypes.size();
        final StringBuilder desc = new StringBuilder(count);
        for(int i = 0; i < count;) {
            i += appendType(desc, localVarTypes.get(i));
        }
        return method.markSymbolBoundariesInLvarTypesDescriptor(desc.toString());
    }

    private static int appendType(final StringBuilder b, final Type t) {
        b.append(t.getBytecodeStackType());
        return t.getSlots();
    }

    private static int countSymbolsInLvarTypeDescriptor(final String lvarTypeDescriptor) {
        int count = 0;
        for(int i = 0; i < lvarTypeDescriptor.length(); ++i) {
            if(Character.isUpperCase(lvarTypeDescriptor.charAt(i))) {
                ++count;
            }
        }
        return count;

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

        method.lineNumber(0);

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
                assert !method.isReachable();
                // Start a catch block and assign the labels for this lvarSpec with it.
                method._catch(unwarrantedOptimismHandlers.get(lvarSpec));
                // This spec is a catch target, so emit array creation code. The length of the array is the number of
                // symbols - the number of uppercase characters.
                method.load(countSymbolsInLvarTypeDescriptor(lvarSpec));
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
            final int firstLvarIndex;
            Label delegationLabel;
            final String commonLvarSpec;
            if(lastHandler) {
                // Last handler block, doesn't delegate to anything.
                lvarIndex = 0;
                firstLvarIndex = 0;
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
                // We don't chop symbols in half
                assert Character.isUpperCase(commonLvarSpec.charAt(commonLvarSpec.length() - 1));

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

                firstArrayIndex = countSymbolsInLvarTypeDescriptor(commonLvarSpec);
                lvarIndex = 0;
                for(int j = 0; j < commonLvarSpec.length(); ++j) {
                    lvarIndex += CodeGeneratorLexicalContext.getTypeForSlotDescriptor(commonLvarSpec.charAt(j)).getSlots();
                }
                firstLvarIndex = lvarIndex;

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
            boolean symbolHadValue = false;
            for(int typeIndex = commonLvarSpec == null ? 0 : commonLvarSpec.length(); typeIndex < lvarSpec.length(); ++typeIndex) {
                final char typeDesc = lvarSpec.charAt(typeIndex);
                final Type lvarType = CodeGeneratorLexicalContext.getTypeForSlotDescriptor(typeDesc);
                if (!lvarType.isUnknown()) {
                    method.load(lvarType, lvarIndex);
                    symbolHadValue = true;
                    args++;
                } else if(typeDesc == 'U' && !symbolHadValue) {
                    // Symbol boundary with undefined last value. Check if all previous values for this symbol were also
                    // undefined; if so, emit one explicit Undefined. This serves to ensure that we're emiting exactly
                    // one value for every symbol that uses local slots. While we could in theory ignore symbols that
                    // are undefined (in other words, dead) at the point where this exception was thrown, unfortunately
                    // we can't do it in practice. The reason for this is that currently our liveness analysis is
                    // coarse (it can determine whether a symbol has not been read with a particular type anywhere in
                    // the function being compiled, but that's it), and a symbol being promoted to Object due to a
                    // deoptimization will suddenly show up as "live for Object type", and previously dead U->O
                    // conversions on loop entries will suddenly become alive in the deoptimized method which will then
                    // expect a value for that slot in its continuation handler. If we had precise liveness analysis, we
                    // could go back to excluding known dead symbols from the payload of the RewriteException.
                    if(method.peekType() == Type.UNDEFINED) {
                        method.dup();
                    } else {
                        method.loadUndefined(Type.OBJECT);
                    }
                    args++;
                }
                if(Character.isUpperCase(typeDesc)) {
                    // Reached symbol boundary; reset flag for the next symbol.
                    symbolHadValue = false;
                }
                lvarIndex += lvarType.getSlots();
            }
            assert args > 0;
            // Delegate actual storing into array to an array populator utility method.
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
                // Must undefine the local variables that we have already processed for the sake of correct join on the
                // delegate label
                method.undefineLocalVariables(firstLvarIndex, true);
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
                loadConstant(getByteCodeSymbolNames(fn));
                if (isRestOf()) {
                    loadConstant(getContinuationEntryPoints());
                    method.invoke(CREATE_REWRITE_EXCEPTION_REST_OF);
                } else {
                    method.invoke(CREATE_REWRITE_EXCEPTION);
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
        int lms = -1; // last matching symbol
        for(int i = 0; i < l; ++i) {
            final char c1 = s1.charAt(i);
            if(c1 != s2.charAt(i)) {
                return s1.substring(0, lms + 1);
            } else if(Character.isUpperCase(c1)) {
                lms = i;
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
        int lvarCount;
        // Indices of local variables that need to be loaded on the stack when this node completes
        private int[] stackStoreSpec;
        // Types of values loaded on the stack
        private Type[] stackTypes;
        // If non-null, this node should perform the requisite type conversion
        private Type returnValueType;
        // If we are in the middle of an object literal initialization, we need to update the map
        private PropertyMap objectLiteralMap;
        // Object literal stack depth for object literal - not necessarily top if property is a tree
        private int objectLiteralStackDepth = -1;
        // The line number at the continuation point
        private int lineNumber;
        // The active catch label, in case the continuation point is in a try/catch block
        private Label catchLabel;
        // The number of scopes that need to be popped before control is transferred to the catch label.
        private int exceptionScopePops;

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

        int getObjectLiteralStackDepth() {
            return objectLiteralStackDepth;
        }

        void setObjectLiteralStackDepth(final int objectLiteralStackDepth) {
            this.objectLiteralStackDepth = objectLiteralStackDepth;
        }

        PropertyMap getObjectLiteralMap() {
            return objectLiteralMap;
        }

        void setObjectLiteralMap(final PropertyMap objectLiteralMap) {
            this.objectLiteralMap = objectLiteralMap;
        }

        @Override
        public String toString() {
             return "[localVariableTypes=" + targetLabel.getStack().getLocalVariableTypesCopy() + ", stackStoreSpec=" +
                     Arrays.toString(stackStoreSpec) + ", returnValueType=" + returnValueType + "]";
        }
    }

    private ContinuationInfo getContinuationInfo() {
        return continuationInfo;
    }

    private void generateContinuationHandler() {
        if (!isRestOf()) {
            return;
        }

        final ContinuationInfo ci = getContinuationInfo();
        method.label(ci.getHandlerLabel());

        // There should never be an exception thrown from the continuation handler, but in case there is (meaning,
        // Nashorn has a bug), then line number 0 will be an indication of where it came from (line numbers are Uint16).
        method.lineNumber(0);

        final Label.Stack stack = ci.getTargetLabel().getStack();
        final List<Type> lvarTypes = stack.getLocalVariableTypesCopy();
        final BitSet symbolBoundary = stack.getSymbolBoundaryCopy();
        final int lvarCount = ci.lvarCount;

        final Type rewriteExceptionType = Type.typeFor(RewriteException.class);
        // Store the RewriteException into an unused local variable slot.
        method.load(rewriteExceptionType, 0);
        method.storeTemp(rewriteExceptionType, lvarCount);
        // Get local variable array
        method.load(rewriteExceptionType, 0);
        method.invoke(RewriteException.GET_BYTECODE_SLOTS);
        // Store local variables. Note that deoptimization might introduce new value types for existing local variables,
        // so we must use both liveLocals and symbolBoundary, as in some cases (when the continuation is inside of a try
        // block) we need to store the incoming value into multiple slots. The optimism exception handlers will have
        // exactly one array element for every symbol that uses bytecode storage. If in the originating method the value
        // was undefined, there will be an explicit Undefined value in the array.
        int arrayIndex = 0;
        for(int lvarIndex = 0; lvarIndex < lvarCount;) {
            final Type lvarType = lvarTypes.get(lvarIndex);
            if(!lvarType.isUnknown()) {
                method.dup();
                method.load(arrayIndex).arrayload();
                final Class<?> typeClass = lvarType.getTypeClass();
                // Deoptimization in array initializers can cause arrays to undergo component type widening
                if(typeClass == long[].class) {
                    method.load(rewriteExceptionType, lvarCount);
                    method.invoke(RewriteException.TO_LONG_ARRAY);
                } else if(typeClass == double[].class) {
                    method.load(rewriteExceptionType, lvarCount);
                    method.invoke(RewriteException.TO_DOUBLE_ARRAY);
                } else if(typeClass == Object[].class) {
                    method.load(rewriteExceptionType, lvarCount);
                    method.invoke(RewriteException.TO_OBJECT_ARRAY);
                } else {
                    if(!(typeClass.isPrimitive() || typeClass == Object.class)) {
                        // NOTE: this can only happen with dead stores. E.g. for the program "1; []; f();" in which the
                        // call to f() will deoptimize the call site, but it'll expect :return to have the type
                        // NativeArray. However, in the more optimal version, :return's only live type is int, therefore
                        // "{O}:return = []" is a dead store, and the variable will be sent into the continuation as
                        // Undefined, however NativeArray can't hold Undefined instance.
                        method.loadType(Type.getInternalName(typeClass));
                        method.invoke(RewriteException.INSTANCE_OR_NULL);
                    }
                    method.convert(lvarType);
                }
                method.storeHidden(lvarType, lvarIndex, false);
            }
            final int nextLvarIndex = lvarIndex + lvarType.getSlots();
            if(symbolBoundary.get(nextLvarIndex - 1)) {
                ++arrayIndex;
            }
            lvarIndex = nextLvarIndex;
        }
        if (AssertsEnabled.assertsEnabled()) {
            method.load(arrayIndex);
            method.invoke(RewriteException.ASSERT_ARRAY_LENGTH);
        } else {
            method.pop();
        }

        final int[]   stackStoreSpec = ci.getStackStoreSpec();
        final Type[]  stackTypes     = ci.getStackTypes();
        final boolean isStackEmpty   = stackStoreSpec.length == 0;
        boolean replacedObjectLiteralMap = false;
        if(!isStackEmpty) {
            // Load arguments on the stack
            final int objectLiteralStackDepth = ci.getObjectLiteralStackDepth();
            for(int i = 0; i < stackStoreSpec.length; ++i) {
                final int slot = stackStoreSpec[i];
                method.load(lvarTypes.get(slot), slot);
                method.convert(stackTypes[i]);
                // stack: s0=object literal being initialized
                // change map of s0 so that the property we are initializing when we failed
                // is now ci.returnValueType
                if (i == objectLiteralStackDepth) {
                    method.dup();
                    assert ci.getObjectLiteralMap() != null;
                    assert ScriptObject.class.isAssignableFrom(method.peekType().getTypeClass()) : method.peekType().getTypeClass() + " is not a script object";
                    loadConstant(ci.getObjectLiteralMap());
                    method.invoke(ScriptObject.SET_MAP);
                    replacedObjectLiteralMap = true;
                }
            }
        }
        // Must have emitted the code for replacing the map of an object literal if we have a set object literal stack depth
        assert ci.getObjectLiteralStackDepth() == -1 || replacedObjectLiteralMap;
        // Load RewriteException back.
        method.load(rewriteExceptionType, lvarCount);
        // Get rid of the stored reference
        method.loadNull();
        method.storeHidden(Type.OBJECT, lvarCount);
        // Mark it dead
        method.markDeadSlots(lvarCount, Type.OBJECT.getSlots());

        // Load return value on the stack
        method.invoke(RewriteException.GET_RETURN_VALUE);

        final Type returnValueType = ci.getReturnValueType();

        // Set up an exception handler for primitive type conversion of return value if needed
        boolean needsCatch = false;
        final Label targetCatchLabel = ci.catchLabel;
        Label _try = null;
        if(returnValueType.isPrimitive()) {
            // If the conversion throws an exception, we want to report the line number of the continuation point.
            method.lineNumber(ci.lineNumber);

            if(targetCatchLabel != METHOD_BOUNDARY) {
                _try = new Label("");
                method.label(_try);
                needsCatch = true;
            }
        }

        // Convert return value
        method.convert(returnValueType);

        final int scopePopCount = needsCatch ? ci.exceptionScopePops : 0;

        // Declare a try/catch for the conversion. If no scopes need to be popped until the target catch block, just
        // jump into it. Otherwise, we'll need to create a scope-popping catch block below.
        final Label catchLabel = scopePopCount > 0 ? new Label("") : targetCatchLabel;
        if(needsCatch) {
            final Label _end_try = new Label("");
            method.label(_end_try);
            method._try(_try, _end_try, catchLabel);
        }

        // Jump to continuation point
        method._goto(ci.getTargetLabel());

        // Make a scope-popping exception delegate if needed
        if(catchLabel != targetCatchLabel) {
            method.lineNumber(0);
            assert scopePopCount > 0;
            method._catch(catchLabel);
            popScopes(scopePopCount);
            method.uncheckedGoto(targetCatchLabel);
        }
    }
}
