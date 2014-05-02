/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.internal.dynalink.support.NameCodec;
import jdk.nashorn.internal.codegen.ApplySpecialization;
import jdk.nashorn.internal.codegen.CompilationEnvironment;
import jdk.nashorn.internal.codegen.CompilationEnvironment.CompilationPhases;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.ParamTypeMap;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Parser;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * This is a subclass that represents a script function that may be regenerated,
 * for example with specialization based on call site types, or lazily generated.
 * The common denominator is that it can get new invokers during its lifespan,
 * unlike {@code FinalScriptFunctionData}
 */
@Logger(name="recompile")
public final class RecompilableScriptFunctionData extends ScriptFunctionData implements Loggable {
    /** Is lazy compilation enabled? TODO: this should be the default */
    public static final boolean LAZY_COMPILATION = Options.getBooleanProperty("nashorn.lazy");

    /** Prefix used for all recompiled script classes */
    public static final String RECOMPILATION_PREFIX = "Script$Recompilation$";

    /** Unique function node id for this function node */
    private final int functionNodeId;

    private final String functionName;

    // TODO: try to eliminate the need for this somehow, either by allowing Source to change its name, allowing a
    // function to internally replace its Source with one of a different name, or storing this additional field in the
    // Source object.
    private final String sourceURL;

    private final int lineNumber;

    /** Source from which FunctionNode was parsed. */
    private final Source source;

    /** Token of this function within the source. */
    private final long token;

    /** Allocator map from makeMap() */
    private final PropertyMap allocatorMap;

    /** Code installer used for all further recompilation/specialization of this ScriptFunction */
    private final CodeInstaller<ScriptEnvironment> installer;

    /** Name of class where allocator function resides */
    private final String allocatorClassName;

    /** lazily generated allocator */
    private MethodHandle allocator;

    private final Map<Integer, RecompilableScriptFunctionData> nestedFunctions;

    /** Id to parent function if one exists */
    private RecompilableScriptFunctionData parent;

    private final boolean isDeclared;
    private final boolean isAnonymous;
    private final boolean needsCallee;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /** Unique id for classes needed to wrap recompiled script functions */
    private static final AtomicInteger RECOMPILE_ID = new AtomicInteger(0);

    private final DebugLogger log;

    private final Map<String, Integer> externalScopeDepths;

    private final Set<String> internalSymbols;

    private final Context context;

    private static final int GET_SET_PREFIX_LENGTH = "*et ".length();

    /**
     * Constructor - public as scripts use it
     *
     * @param context             context
     * @param functionNode        functionNode that represents this function code
     * @param installer           installer for code regeneration versions of this function
     * @param allocatorClassName  name of our allocator class, will be looked up dynamically if used as a constructor
     * @param allocatorMap        allocator map to seed instances with, when constructing
     * @param nestedFunctions     nested function map
     * @param sourceURL           source URL
     * @param externalScopeDepths external scope depths
     * @param internalSymbols     internal symbols to method, defined in its scope
     */
    public RecompilableScriptFunctionData(
        final Context context,
        final FunctionNode functionNode,
        final CodeInstaller<ScriptEnvironment> installer,
        final String allocatorClassName,
        final PropertyMap allocatorMap,
        final Map<Integer, RecompilableScriptFunctionData> nestedFunctions,
        final String sourceURL,
        final Map<String, Integer> externalScopeDepths,
        final Set<String> internalSymbols) {

        super(functionName(functionNode),
              Math.min(functionNode.getParameters().size(), MAX_ARITY),
              getFlags(functionNode));

        this.context             = context;
        this.functionName        = functionNode.getName();
        this.lineNumber          = functionNode.getLineNumber();
        this.isDeclared          = functionNode.isDeclared();
        this.needsCallee         = functionNode.needsCallee();
        this.isAnonymous         = functionNode.isAnonymous();
        this.functionNodeId      = functionNode.getId();
        this.source              = functionNode.getSource();
        this.token               = tokenFor(functionNode);
        this.installer           = installer;
        this.sourceURL           = sourceURL;
        this.allocatorClassName  = allocatorClassName;
        this.allocatorMap        = allocatorMap;
        this.nestedFunctions     = nestedFunctions;
        this.externalScopeDepths = externalScopeDepths;
        this.internalSymbols     = internalSymbols;

        for (final RecompilableScriptFunctionData nfn : nestedFunctions.values()) {
            assert nfn.getParent() == null;
            nfn.setParent(this);
        }

        this.log = initLogger(context);
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context ctxt) {
        return ctxt.getLogger(this.getClass());
    }

    /**
     * Check if a symbol is internally defined in a function. For example
     * if "undefined" is internally defined in the outermost program function,
     * it has not been reassigned or overridden and can be optimized
     *
     * @param symbolName symbol name
     * @return true if symbol is internal to this ScriptFunction
     */

    public boolean hasInternalSymbol(final String symbolName) {
        return internalSymbols.contains(symbolName);
    }

    /**
     * Return the external symbol table
     * @param symbolName symbol name
     * @return the external symbol table with proto depths
     */
    public int getExternalSymbolDepth(final String symbolName) {
        final Map<String, Integer> map = externalScopeDepths;
        if (map == null) {
            return -1;
        }
        final Integer depth = map.get(symbolName);
        if (depth == null) {
            return -1;
        }
        return depth;
    }

    /**
     * Get the parent of this RecompilableScriptFunctionData. If we are
     * a nested function, we have a parent. Note that "null" return value
     * can also mean that we have a parent but it is unknown, so this can
     * only be used for conservative assumptions.
     * @return parent data, or null if non exists and also null IF UNKNOWN.
     */
    public RecompilableScriptFunctionData getParent() {
       return parent;
    }

    void setParent(final RecompilableScriptFunctionData parent) {
        this.parent = parent;
    }

    @Override
    String toSource() {
        if (source != null && token != 0) {
            return source.getString(Token.descPosition(token), Token.descLength(token));
        }

        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    @Override
    public String toString() {
        return super.toString() + '@' + functionNodeId;
    }

    @Override
    public String toStringVerbose() {
        final StringBuilder sb = new StringBuilder();

        sb.append("fnId=").append(functionNodeId).append(' ');

        if (source != null) {
            sb.append(source.getName())
                .append(':')
                .append(lineNumber)
                .append(' ');
        }

        return sb.toString() + super.toString();
    }

    @Override
    public String getFunctionName() {
        return functionName;
    }

    @Override
    public boolean inDynamicContext() {
        return (flags & IN_DYNAMIC_CONTEXT) != 0;
    }

    private static String functionName(final FunctionNode fn) {
        if (fn.isAnonymous()) {
            return "";
        }
        final FunctionNode.Kind kind = fn.getKind();
        if (kind == FunctionNode.Kind.GETTER || kind == FunctionNode.Kind.SETTER) {
            final String name = NameCodec.decode(fn.getIdent().getName());
            return name.substring(GET_SET_PREFIX_LENGTH);
        }
        return fn.getIdent().getName();
    }

    private static long tokenFor(final FunctionNode fn) {
        final int  position  = Token.descPosition(fn.getFirstToken());
        final long lastToken = fn.getLastToken();
        // EOL uses length field to store the line number
        final int  length    = Token.descPosition(lastToken) - position + (Token.descType(lastToken) == TokenType.EOL ? 0 : Token.descLength(lastToken));

        return Token.toDesc(TokenType.FUNCTION, position, length);
    }

    private static int getFlags(final FunctionNode functionNode) {
        int flags = IS_CONSTRUCTOR;
        if (functionNode.isStrict()) {
            flags |= IS_STRICT;
        }
        if (functionNode.needsCallee()) {
            flags |= NEEDS_CALLEE;
        }
        if (functionNode.usesThis() || functionNode.hasEval()) {
            flags |= USES_THIS;
        }
        if (functionNode.isVarArg()) {
            flags |= IS_VARIABLE_ARITY;
        }
        if (functionNode.inDynamicContext()) {
            flags |= IN_DYNAMIC_CONTEXT;
        }
        return flags;
    }

    @Override
    PropertyMap getAllocatorMap() {
        return allocatorMap;
    }

    @Override
    ScriptObject allocate(final PropertyMap map) {
        try {
            ensureHasAllocator(); //if allocatorClass name is set to null (e.g. for bound functions) we don't even try
            return allocator == null ? null : (ScriptObject)allocator.invokeExact(map);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void ensureHasAllocator() throws ClassNotFoundException {
        if (allocator == null && allocatorClassName != null) {
            this.allocator = MH.findStatic(LOOKUP, Context.forStructureClass(allocatorClassName), CompilerConstants.ALLOCATE.symbolName(), MH.type(ScriptObject.class, PropertyMap.class));
        }
    }

    private FunctionNode reparse(final String scriptName) {
        final boolean isProgram = functionNodeId == FunctionNode.FIRST_FUNCTION_ID;
        // NOTE: If we aren't recompiling the top-level program, we decrease functionNodeId 'cause we'll have a synthetic program node
        final int descPosition = Token.descPosition(token);
        final Parser parser = new Parser(
            context.getEnv(),
            source,
            new Context.ThrowErrorManager(),
            isStrict(),
            functionNodeId - (isProgram ? 0 : 1),
            lineNumber - 1,
            context.getLogger(Parser.class)); // source starts at line 0, so even though lineNumber is the correct declaration line, back off one to make it exclusive

        if (isAnonymous) {
            parser.setFunctionName(functionName);
        }

        final FunctionNode program = parser.parse(scriptName, descPosition, Token.descLength(token), true);
        // Parser generates a program AST even if we're recompiling a single function, so when we are only recompiling a
        // single function, extract it from the program.
        return (isProgram ? program : extractFunctionFromScript(program)).setName(null, functionName).setSourceURL(null,  sourceURL);
    }

    private static String stringifyInvalidations(final Map<Integer, Type> ipp) {
        if (ipp == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        final Iterator<Map.Entry<Integer, Type>> iter = ipp.entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<Integer, Type> entry = iter.next();
            final char bct = entry.getValue().getBytecodeStackType();
            sb.append('[').
                    append(entry.getKey()).
                    append("->").
                    append(bct == 'A' ? 'O' : bct).
                    append(']');
            if (iter.hasNext()) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private FunctionNodeTransform getNopTransform() {
        return new FunctionNodeTransform() {
            @Override
            FunctionNode apply(final FunctionNode functionNode) {
                return functionNode;
            }

            @Override
            int getArity() {
                return RecompilableScriptFunctionData.this.getArity();
            }

            @Override
            public String toString() {
                return "[NopTransform]";
            }
        };
    }

    FunctionNodeTransform getDefaultTransform(final MethodType callSiteType) {
        return new ApplyToCallTransform(this, callSiteType);
    }

    private FunctionNode compileTypeSpecialization(final MethodType actualCallSiteType, final ScriptObject runtimeScope, final FunctionNodeTransform tr) {
        return compile(actualCallSiteType, null, runtimeScope, "Type specialized compilation", tr);
    }

    private ParamTypeMap typeMap(final MethodType fnCallSiteType, final FunctionNodeTransform tr) {
        if (isVariableArity() && !tr.wasTransformed()) {
            return null;
        }
        return new ParamTypeMap(functionNodeId, explicitParams(fnCallSiteType, tr.getArity()));
    }

    MethodHandle compileRestOfMethod(final MethodType fnCallSiteType, final Map<Integer, Type> invalidatedProgramPoints, final int[] continuationEntryPoints, final ScriptObject runtimeScope, final FunctionNodeTransform tr) {
        if (log.isEnabled()) {
            log.info("Rest-of compilation of '", functionName, "' signature: ", fnCallSiteType, " ", stringifyInvalidations(invalidatedProgramPoints));
        }

        final String scriptName = RECOMPILATION_PREFIX + RECOMPILE_ID.incrementAndGet() + "$restOf";

        FunctionNode fn = tr.apply(reparse(scriptName));
        final ParamTypeMap ptm = typeMap(fnCallSiteType, tr);

        final Compiler compiler = new Compiler(
                new CompilationEnvironment(
                    context,
                    CompilationPhases.EAGER.makeOptimistic(),
                    isStrict(),
                    this,
                    runtimeScope,
                    ptm,
                    invalidatedProgramPoints,
                    continuationEntryPoints,
                    true
                    ),
                installer);

        fn = compiler.compile(scriptName, fn);
        compiler.install(fn);

        // look up the rest of method
        final MethodHandle mh = lookupWithExplicitType(fn, MethodType.methodType(fn.getReturnType().getTypeClass(), RewriteException.class));
        return mh;
    }

    FunctionNode compile(final MethodType actualCallSiteType, final Map<Integer, Type> invalidatedProgramPoints, final ScriptObject runtimeScope, final String reason, final FunctionNodeTransform tr) {
        final String scriptName = RECOMPILATION_PREFIX + RECOMPILE_ID.incrementAndGet();
        final MethodType fnCallSiteType = actualCallSiteType == null ? null : actualCallSiteType.changeParameterType(0, ScriptFunction.class);

        if (log.isEnabled()) {
            log.info(reason, " of '", functionName, "' signature: ", fnCallSiteType, " ", stringifyInvalidations(invalidatedProgramPoints));
        }

        FunctionNode fn = tr.apply(reparse(scriptName));

        final ParamTypeMap ptm = fnCallSiteType == null ? null : typeMap(fnCallSiteType, tr);

        final CompilationPhases phases = CompilationPhases.EAGER;
        final Compiler compiler = new Compiler(
            new CompilationEnvironment(
                    context,
                    phases.makeOptimistic(ScriptEnvironment.globalOptimistic()),
                    isStrict(),
                    this,
                    runtimeScope,
                    ptm,
                    invalidatedProgramPoints,
                    true),
            installer);

        fn = compiler.compile(scriptName, fn);
        compiler.install(fn);

        return fn;
    }

    private static MethodType explicitParams(final MethodType callSiteType, final int arity) {
        final MethodType noCalleeThisType = callSiteType.dropParameterTypes(0, 2); // (callee, this) is always in call site type
        final int callSiteParamCount = noCalleeThisType.parameterCount();

        // Widen parameters of reference types to Object as we currently don't care for specialization among reference
        // types. E.g. call site saying (ScriptFunction, Object, String) should still link to (ScriptFunction, Object, Object)
        final int minParams = Math.min(callSiteParamCount, arity);
        final Class<?>[] paramTypes = noCalleeThisType.parameterArray();
        boolean changed = false;
        for (int i = 0; i < minParams; ++i) {
            final Class<?> paramType = paramTypes[i];
            if (!(paramType.isPrimitive() || paramType == Object.class)) {
                paramTypes[i] = Object.class;
                changed = true;
            }
        }
        final MethodType generalized = changed ? MethodType.methodType(noCalleeThisType.returnType(), paramTypes) : noCalleeThisType;

        // Match arity
        if (callSiteParamCount < arity) {
            return generalized.appendParameterTypes(Collections.<Class<?>>nCopies(arity - callSiteParamCount, Object.class));
        } else if (callSiteParamCount > arity) {
            return generalized.dropParameterTypes(arity, callSiteParamCount);
        } else {
            return generalized;
        }
    }

    private FunctionNode extractFunctionFromScript(final FunctionNode script) {
        final Set<FunctionNode> fns = new HashSet<>();
        script.getBody().accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterFunctionNode(final FunctionNode fn) {
                fns.add(fn);
                return false;
            }
        });
        assert fns.size() == 1 : "got back more than one method in recompilation";
        final FunctionNode f = fns.iterator().next();
        assert f.getId() == functionNodeId;
        if (!isDeclared && f.isDeclared()) {
            return f.clearFlag(null, FunctionNode.IS_DECLARED);
        }
        return f;
    }

    MethodHandle lookup(final FunctionNode fn) {
        return lookupWithExplicitType(fn, new FunctionSignature(fn).getMethodType());
    }

    private MethodHandle lookupWithExplicitType(final FunctionNode fn, final MethodType targetType) {
        return lookupCodeMethod(fn.getCompileUnit(), targetType);
    }

    private MethodHandle lookupCodeMethod(final CompileUnit compileUnit, final MethodType targetType) {
        return MH.findStatic(LOOKUP, compileUnit.getCode(), functionName, targetType);
    }

    /**
     * Initializes this function data with the eagerly generated version of the code. This method can only be invoked
     * by the compiler internals in Nashorn and is public for implementation reasons only. Attempting to invoke it
     * externally will result in an exception.
     * @param functionNode the functionNode belonging to this data
     */
    public void initializeCode(final FunctionNode functionNode) {
        // Since the method is public, we double-check that we aren't invoked with an inappropriate compile unit.
        if(!(code.isEmpty() && functionNode.getCompileUnit().isInitializing(this, functionNode))) {
            throw new IllegalStateException(functionNode.getName() + " id=" + functionNode.getId());
        }
        addCode(functionNode);
    }

    private CompiledFunction addCode(final MethodHandle target, final int fnFlags) {
        final CompiledFunction cfn = new CompiledFunction(target, this, fnFlags);
        code.add(cfn);
        return cfn;
    }

    private CompiledFunction addCode(final FunctionNode fn) {
        return addCode(lookup(fn), fn.getFlags());
    }

    /**
     * Add code with specific call site type. It will adapt the type of the looked up method handle to fit the call site
     * type. This is necessary because even if we request a specialization that takes an "int" parameter, we might end
     * up getting one that takes a "double" etc. because of internal function logic causes widening (e.g. assignment of
     * a wider value to the parameter variable). However, we use the method handle type for matching subsequent lookups
     * for the same specialization, so we must adapt the handle to the expected type.
     * @param fn the function
     * @param callSiteType the call site type
     * @return the compiled function object, with its type matching that of the call site type.
     */
    private CompiledFunction addCode(final FunctionNode fn, final MethodType callSiteType) {
        if (fn.isVarArg()) {
            return addCode(fn);
        }

        final MethodHandle handle = lookup(fn);
        final MethodType fromType = handle.type();
        MethodType toType = needsCallee(fromType) ? callSiteType.changeParameterType(0, ScriptFunction.class) : callSiteType.dropParameterTypes(0, 1);
        toType = toType.changeReturnType(fromType.returnType());

        final int toCount = toType.parameterCount();
        final int fromCount = fromType.parameterCount();
        final int minCount = Math.min(fromCount, toCount);
        for(int i = 0; i < minCount; ++i) {
            final Class<?> fromParam = fromType.parameterType(i);
            final Class<?>   toParam =   toType.parameterType(i);
            // If method has an Object parameter, but call site had String, preserve it as Object. No need to narrow it
            // artificially. Note that this is related to how CompiledFunction.matchesCallSite() works, specifically
            // the fact that various reference types compare to equal (see "fnType.isEquivalentTo(csType)" there).
            if (fromParam != toParam && !fromParam.isPrimitive() && !toParam.isPrimitive()) {
                assert fromParam.isAssignableFrom(toParam);
                toType = toType.changeParameterType(i, fromParam);
            }
        }
        if (fromCount > toCount) {
            toType = toType.appendParameterTypes(fromType.parameterList().subList(toCount, fromCount));
        } else if (fromCount < toCount) {
            toType = toType.dropParameterTypes(fromCount, toCount);
        }

        return addCode(lookup(fn).asType(toType), fn.getFlags());
    }


    @Override
    CompiledFunction getBest(final MethodType callSiteType, final ScriptObject runtimeScope) {
        synchronized (code) {
            CompiledFunction existingBest = super.getBest(callSiteType, runtimeScope);
            if (existingBest == null) {
                existingBest = addCode(compileTypeSpecialization(callSiteType, runtimeScope, getNopTransform()), callSiteType);
            }

            assert existingBest != null;
            boolean applyToCall = existingBest.isVarArg() && !CompiledFunction.isVarArgsType(callSiteType);

            //if the best one is an apply to call, it has to match the callsite exactly
            //or we need to regenerate
            if (existingBest.isApplyToCall()) {
                final CompiledFunction best = code.lookupExactApplyToCall(callSiteType);
                if (best != null) {
                    return best;
                }
                applyToCall = true;
            }

            if (applyToCall) {
                final FunctionNode fn = compileTypeSpecialization(callSiteType, runtimeScope, new ApplyToCallTransform(this, callSiteType));
                if (fn.hasOptimisticApplyToCall()) { //did the specialization work
                    existingBest = addCode(fn, callSiteType);
                    existingBest.setIsApplyToCall();
                }
            }

            return existingBest;
        }
    }

    @Override
    boolean isRecompilable() {
        return true;
    }

    @Override
    public boolean needsCallee() {
        return needsCallee;
    }

    @Override
    MethodType getGenericType() {
        // 2 is for (callee, this)
        if (isVariableArity()) {
            return MethodType.genericMethodType(2, true);
        }
        return MethodType.genericMethodType(2 + getArity());
    }

    /**
     * Return a script function data based on a function id, either this function if
     * the id matches or a nested function based on functionId. This goes down into
     * nested functions until all leaves are exhausted.
     *
     * @param functionId function id
     * @return script function data or null if invalid id
     */
    public RecompilableScriptFunctionData getScriptFunctionData(final int functionId) {
        if (functionId == functionNodeId) {
            return this;
        }
        RecompilableScriptFunctionData data;

        data = nestedFunctions == null ? null : nestedFunctions.get(functionId);
        if (data != null) {
            return data;
        }
        for (final RecompilableScriptFunctionData ndata : nestedFunctions.values()) {
            data = ndata.getScriptFunctionData(functionId);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    /**
     * Get the uppermost parent, the program, for this data
     * @return program
     */
    public RecompilableScriptFunctionData getProgram() {
        RecompilableScriptFunctionData program = this;
        while (true) {
            final RecompilableScriptFunctionData p = program.getParent();
            if (p == null) {
                return program;
            }
            program = p;
        }
    }

    /**
     * Check whether a certain name is a global symbol, i.e. only exists as defined
     * in outermost scope and not shadowed by being parameter or assignment in inner
     * scopes
     *
     * @param functionNode function node to check
     * @param symbolName symbol name
     * @return true if global symbol
     */
    public boolean isGlobalSymbol(final FunctionNode functionNode, final String symbolName) {
        RecompilableScriptFunctionData data = getScriptFunctionData(functionNode.getId());
        assert data != null;
        final RecompilableScriptFunctionData program = getProgram();

        while (data != program) {
            if (data.hasInternalSymbol(symbolName)) {
                return false;
            }
            data = data.getParent();
        }

        return true;
    }

    private static abstract class FunctionNodeTransform {

        abstract int getArity();

        boolean wasTransformed() {
            return false;
        }

        abstract FunctionNode apply(final FunctionNode functionNode);
    }

    /**
     * Helper class for transforming apply calls to calls
     */
    private static class ApplyToCallTransform extends FunctionNodeTransform {
        private final RecompilableScriptFunctionData data;
        private final MethodType actualCallSiteType;
        private int arity;
        private FunctionNode initialFunctionNode;
        private FunctionNode transformedFunctionNode;

        ApplyToCallTransform(final RecompilableScriptFunctionData data, final MethodType actualCallSiteType) {
            this.data = data;
            this.actualCallSiteType = actualCallSiteType;
            this.arity = data.getArity();
        }

        @Override
        public FunctionNode apply(final FunctionNode functionNode) {
            this.initialFunctionNode = functionNode;
            if (data.isVariableArity() && !CompiledFunction.isVarArgsType(actualCallSiteType)) {
                final ApplySpecialization spec = new ApplySpecialization(data.context, data, functionNode, actualCallSiteType);
                if (spec.transform()) {
                    setTransformedFunctionNode(spec.getFunctionNode());
                    return transformedFunctionNode;
                }
            }
            return functionNode;
        }

        private void setTransformedFunctionNode(final FunctionNode transformedFunctionNode) {
            this.transformedFunctionNode = transformedFunctionNode;
            assert !transformedFunctionNode.isVarArg();
            this.arity = transformedFunctionNode.getParameters().size();
        }

        @Override
        public int getArity() {
            return arity;
        }

        @Override
        public boolean wasTransformed() {
            return initialFunctionNode != transformedFunctionNode;
        }

        @Override
        public String toString() {
            return "[ApplyToCallTransform]";
        }
    }
}
