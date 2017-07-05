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
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jdk.internal.dynalink.support.NameCodec;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.Compiler.CompilationPhases;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.codegen.ObjectClassGenerator.AllocatorDescriptor;
import jdk.nashorn.internal.codegen.OptimisticTypesPersistence;
import jdk.nashorn.internal.codegen.TypeMap;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.parser.Parser;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
/**
 * This is a subclass that represents a script function that may be regenerated,
 * for example with specialization based on call site types, or lazily generated.
 * The common denominator is that it can get new invokers during its lifespan,
 * unlike {@code FinalScriptFunctionData}
 */
@Logger(name="recompile")
public final class RecompilableScriptFunctionData extends ScriptFunctionData implements Loggable {
    /** Prefix used for all recompiled script classes */
    public static final String RECOMPILATION_PREFIX = "Recompilation$";

    /** Unique function node id for this function node */
    private final int functionNodeId;

    private final String functionName;

    /** The line number where this function begins. */
    private final int lineNumber;

    /** Source from which FunctionNode was parsed. */
    private transient Source source;

    /** Serialized, compressed form of the AST. Used by split functions as they can't be reparsed from source. */
    private final byte[] serializedAst;

    /** Token of this function within the source. */
    private final long token;

    /**
     * Represents the allocation strategy (property map, script object class, and method handle) for when
     * this function is used as a constructor. Note that majority of functions (those not setting any this.*
     * properties) will share a single canonical "default strategy" instance.
     */
    private final AllocationStrategy allocationStrategy;

    /**
     * Opaque object representing parser state at the end of the function. Used when reparsing outer function
     * to help with skipping parsing inner functions.
     */
    private final Object endParserState;

    /** Code installer used for all further recompilation/specialization of this ScriptFunction */
    private transient CodeInstaller<ScriptEnvironment> installer;

    private final Map<Integer, RecompilableScriptFunctionData> nestedFunctions;

    /** Id to parent function if one exists */
    private RecompilableScriptFunctionData parent;

    /** Copy of the {@link FunctionNode} flags. */
    private final int functionFlags;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private transient DebugLogger log;

    private final Map<String, Integer> externalScopeDepths;

    private final Set<String> internalSymbols;

    private static final int GET_SET_PREFIX_LENGTH = "*et ".length();

    private static final long serialVersionUID = 4914839316174633726L;

    /**
     * Constructor - public as scripts use it
     *
     * @param functionNode        functionNode that represents this function code
     * @param installer           installer for code regeneration versions of this function
     * @param allocationDescriptor descriptor for the allocation behavior when this function is used as a constructor
     * @param nestedFunctions     nested function map
     * @param externalScopeDepths external scope depths
     * @param internalSymbols     internal symbols to method, defined in its scope
     * @param serializedAst       a serialized AST representation. Normally only used for split functions.
     */
    public RecompilableScriptFunctionData(
        final FunctionNode functionNode,
        final CodeInstaller<ScriptEnvironment> installer,
        final AllocatorDescriptor allocationDescriptor,
        final Map<Integer, RecompilableScriptFunctionData> nestedFunctions,
        final Map<String, Integer> externalScopeDepths,
        final Set<String> internalSymbols,
        final byte[] serializedAst) {

        super(functionName(functionNode),
              Math.min(functionNode.getParameters().size(), MAX_ARITY),
              getDataFlags(functionNode));

        this.functionName        = functionNode.getName();
        this.lineNumber          = functionNode.getLineNumber();
        this.functionFlags       = functionNode.getFlags() | (functionNode.needsCallee() ? FunctionNode.NEEDS_CALLEE : 0);
        this.functionNodeId      = functionNode.getId();
        this.source              = functionNode.getSource();
        this.endParserState      = functionNode.getEndParserState();
        this.token               = tokenFor(functionNode);
        this.installer           = installer;
        this.allocationStrategy  = AllocationStrategy.get(allocationDescriptor);
        this.nestedFunctions     = smallMap(nestedFunctions);
        this.externalScopeDepths = smallMap(externalScopeDepths);
        this.internalSymbols     = smallSet(new HashSet<>(internalSymbols));

        for (final RecompilableScriptFunctionData nfn : nestedFunctions.values()) {
            assert nfn.getParent() == null;
            nfn.setParent(this);
        }

        this.serializedAst = serializedAst;
        createLogger();
    }

    private static <K, V> Map<K, V> smallMap(final Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        } else if (map.size() == 1) {
            final Map.Entry<K, V> entry = map.entrySet().iterator().next();
            return Collections.singletonMap(entry.getKey(), entry.getValue());
        } else {
            return map;
        }
    }

    private static <T> Set<T> smallSet(final Set<T> set) {
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        } else if (set.size() == 1) {
            return Collections.singleton(set.iterator().next());
        } else {
            return set;
        }
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
        final Integer depth = externalScopeDepths.get(symbolName);
        return depth == null ? -1 : depth;
    }

    /**
     * Returns the names of all external symbols this function uses.
     * @return the names of all external symbols this function uses.
     */
    public Set<String> getExternalSymbolNames() {
        return Collections.unmodifiableSet(externalScopeDepths.keySet());
    }

    /**
     * Returns the opaque object representing the parser state at the end of this function's body, used to
     * skip parsing this function when reparsing its containing outer function.
     * @return the object representing the end parser state
     */
    public Object getEndParserState() {
        return endParserState;
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

    /**
     * Initialize transient fields on deserialized instances
     *
     * @param src source
     * @param inst code installer
     */
    public void initTransients(final Source src, final CodeInstaller<ScriptEnvironment> inst) {
        if (this.source == null && this.installer == null) {
            this.source    = src;
            this.installer = inst;
        } else if (this.source != src || !this.installer.isCompatibleWith(inst)) {
            // Existing values must be same as those passed as parameters
            throw new IllegalArgumentException();
        }
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
        return getFunctionFlag(FunctionNode.IN_DYNAMIC_CONTEXT);
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
        final long lastToken = Token.withDelimiter(fn.getLastToken());
        // EOL uses length field to store the line number
        final int  length    = Token.descPosition(lastToken) - position + (Token.descType(lastToken) == TokenType.EOL ? 0 : Token.descLength(lastToken));

        return Token.toDesc(TokenType.FUNCTION, position, length);
    }

    private static int getDataFlags(final FunctionNode functionNode) {
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
        return flags;
    }

    @Override
    PropertyMap getAllocatorMap() {
        return allocationStrategy.getAllocatorMap();
    }

    @Override
    ScriptObject allocate(final PropertyMap map) {
        return allocationStrategy.allocate(map);
    }

    boolean isSerialized() {
        return serializedAst != null;
    }

    FunctionNode reparse() {
        if (isSerialized()) {
            return deserialize();
        }

        final int descPosition = Token.descPosition(token);
        final Context context = Context.getContextTrusted();
        final Parser parser = new Parser(
            context.getEnv(),
            source,
            new Context.ThrowErrorManager(),
            isStrict(),
            // source starts at line 0, so even though lineNumber is the correct declaration line, back off
            // one to make it exclusive
            lineNumber - 1,
            context.getLogger(Parser.class));

        if (getFunctionFlag(FunctionNode.IS_ANONYMOUS)) {
            parser.setFunctionName(functionName);
        }
        parser.setReparsedFunction(this);

        final FunctionNode program = parser.parse(CompilerConstants.PROGRAM.symbolName(), descPosition,
                Token.descLength(token), true);
        // Parser generates a program AST even if we're recompiling a single function, so when we are only
        // recompiling a single function, extract it from the program.
        return (isProgram() ? program : extractFunctionFromScript(program)).setName(null, functionName);
    }

    private FunctionNode deserialize() {
        final ScriptEnvironment env = installer.getOwner();
        final Timing timing = env._timing;
        final long t1 = System.nanoTime();
        try {
            return AstDeserializer.deserialize(serializedAst).initializeDeserialized(source, new Namespace(env.getNamespace()));
        } finally {
            timing.accumulateTime("'Deserialize'", System.nanoTime() - t1);
        }
    }

    private boolean getFunctionFlag(final int flag) {
        return (functionFlags & flag) != 0;
    }

    private boolean isProgram() {
        return getFunctionFlag(FunctionNode.IS_PROGRAM);
    }

    TypeMap typeMap(final MethodType fnCallSiteType) {
        if (fnCallSiteType == null) {
            return null;
        }

        if (CompiledFunction.isVarArgsType(fnCallSiteType)) {
            return null;
        }

        return new TypeMap(functionNodeId, explicitParams(fnCallSiteType), needsCallee());
    }

    private static ScriptObject newLocals(final ScriptObject runtimeScope) {
        final ScriptObject locals = Global.newEmptyInstance();
        locals.setProto(runtimeScope);
        return locals;
    }

    private Compiler getCompiler(final FunctionNode fn, final MethodType actualCallSiteType, final ScriptObject runtimeScope) {
        return getCompiler(fn, actualCallSiteType, newLocals(runtimeScope), null, null);
    }

    /**
     * Returns a code installer for installing new code. If we're using either optimistic typing or loader-per-compile,
     * then asks for a code installer with a new class loader; otherwise just uses the current installer. We use
     * a new class loader with optimistic typing so that deoptimized code can get reclaimed by GC.
     * @return a code installer for installing new code.
     */
    private CodeInstaller<ScriptEnvironment> getInstallerForNewCode() {
        final ScriptEnvironment env = installer.getOwner();
        return env._optimistic_types || env._loader_per_compile ? installer.withNewLoader() : installer;
    }

    Compiler getCompiler(final FunctionNode functionNode, final MethodType actualCallSiteType,
            final ScriptObject runtimeScope, final Map<Integer, Type> invalidatedProgramPoints,
            final int[] continuationEntryPoints) {
        final TypeMap typeMap = typeMap(actualCallSiteType);
        final Type[] paramTypes = typeMap == null ? null : typeMap.getParameterTypes(functionNodeId);
        final Object typeInformationFile = OptimisticTypesPersistence.getLocationDescriptor(source, functionNodeId, paramTypes);
        final Context context = Context.getContextTrusted();
        return new Compiler(
                context,
                context.getEnv(),
                getInstallerForNewCode(),
                functionNode.getSource(),  // source
                context.getErrorManager(),
                isStrict() | functionNode.isStrict(), // is strict
                true,       // is on demand
                this,       // compiledFunction, i.e. this RecompilableScriptFunctionData
                typeMap,    // type map
                getEffectiveInvalidatedProgramPoints(invalidatedProgramPoints, typeInformationFile), // invalidated program points
                typeInformationFile,
                continuationEntryPoints, // continuation entry points
                runtimeScope); // runtime scope
    }

    /**
     * If the function being compiled already has its own invalidated program points map, use it. Otherwise, attempt to
     * load invalidated program points map from the persistent type info cache.
     * @param invalidatedProgramPoints the function's current invalidated program points map. Null if the function
     * doesn't have it.
     * @param typeInformationFile the object describing the location of the persisted type information.
     * @return either the existing map, or a loaded map from the persistent type info cache, or a new empty map if
     * neither an existing map or a persistent cached type info is available.
     */
    @SuppressWarnings("unused")
    private static Map<Integer, Type> getEffectiveInvalidatedProgramPoints(
            final Map<Integer, Type> invalidatedProgramPoints, final Object typeInformationFile) {
        if(invalidatedProgramPoints != null) {
            return invalidatedProgramPoints;
        }
        final Map<Integer, Type> loadedProgramPoints = OptimisticTypesPersistence.load(typeInformationFile);
        return loadedProgramPoints != null ? loadedProgramPoints : new TreeMap<Integer, Type>();
    }

    private FunctionInitializer compileTypeSpecialization(final MethodType actualCallSiteType, final ScriptObject runtimeScope, final boolean persist) {
        // We're creating an empty script object for holding local variables. AssignSymbols will populate it with
        // explicit Undefined values for undefined local variables (see AssignSymbols#defineSymbol() and
        // CompilationEnvironment#declareLocalSymbol()).

        if (log.isEnabled()) {
            log.info("Parameter type specialization of '", functionName, "' signature: ", actualCallSiteType);
        }

        final boolean persistentCache = usePersistentCodeCache() && persist;
        String cacheKey = null;
        if (persistentCache) {
            final TypeMap typeMap = typeMap(actualCallSiteType);
            final Type[] paramTypes = typeMap == null ? null : typeMap.getParameterTypes(functionNodeId);
            cacheKey = CodeStore.getCacheKey(functionNodeId, paramTypes);
            final CodeInstaller<ScriptEnvironment> newInstaller = getInstallerForNewCode();
            final StoredScript script = newInstaller.loadScript(source, cacheKey);

            if (script != null) {
                Compiler.updateCompilationId(script.getCompilationId());
                return installStoredScript(script, newInstaller);
            }
        }

        final FunctionNode fn = reparse();
        final Compiler compiler = getCompiler(fn, actualCallSiteType, runtimeScope);
        final FunctionNode compiledFn = compiler.compile(fn,
                isSerialized() ? CompilationPhases.COMPILE_ALL_SERIALIZED : CompilationPhases.COMPILE_ALL);

        if (persist && !compiledFn.getFlag(FunctionNode.HAS_APPLY_TO_CALL_SPECIALIZATION)) {
            compiler.persistClassInfo(cacheKey, compiledFn);
        }
        return new FunctionInitializer(compiledFn, compiler.getInvalidatedProgramPoints());
    }

    private static Map<String, Class<?>> installStoredScriptClasses(final StoredScript script, final CodeInstaller<ScriptEnvironment> installer) {
        final Map<String, Class<?>> installedClasses = new HashMap<>();
        final Map<String, byte[]>   classBytes       = script.getClassBytes();
        final String   mainClassName   = script.getMainClassName();
        final byte[]   mainClassBytes  = classBytes.get(mainClassName);

        final Class<?> mainClass       = installer.install(mainClassName, mainClassBytes);

        installedClasses.put(mainClassName, mainClass);

        for (final Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
            final String className = entry.getKey();
            final byte[] bytecode = entry.getValue();

            if (className.equals(mainClassName)) {
                continue;
            }

            installedClasses.put(className, installer.install(className, bytecode));
        }
        return installedClasses;
    }

    /**
     * Install this script using the given {@code installer}.
     *
     * @param script the compiled script
     * @return the function initializer
     */
    private FunctionInitializer installStoredScript(final StoredScript script, final CodeInstaller<ScriptEnvironment> newInstaller) {
        final Map<String, Class<?>> installedClasses = installStoredScriptClasses(script, newInstaller);

        final Map<Integer, FunctionInitializer> initializers = script.getInitializers();
        assert initializers != null;
        assert initializers.size() == 1;
        final FunctionInitializer initializer = initializers.values().iterator().next();

        final Object[] constants = script.getConstants();
        for (int i = 0; i < constants.length; i++) {
            if (constants[i] instanceof RecompilableScriptFunctionData) {
                // replace deserialized function data with the ones we already have
                constants[i] = getScriptFunctionData(((RecompilableScriptFunctionData) constants[i]).getFunctionNodeId());
            }
        }

        newInstaller.initialize(installedClasses.values(), source, constants);
        initializer.setCode(installedClasses.get(initializer.getClassName()));
        return initializer;
    }

    boolean usePersistentCodeCache() {
        final ScriptEnvironment env = installer.getOwner();
        return env._persistent_cache && env._optimistic_types;
    }

    private MethodType explicitParams(final MethodType callSiteType) {
        if (CompiledFunction.isVarArgsType(callSiteType)) {
            return null;
        }

        final MethodType noCalleeThisType = callSiteType.dropParameterTypes(0, 2); // (callee, this) is always in call site type
        final int callSiteParamCount = noCalleeThisType.parameterCount();

        // Widen parameters of reference types to Object as we currently don't care for specialization among reference
        // types. E.g. call site saying (ScriptFunction, Object, String) should still link to (ScriptFunction, Object, Object)
        final Class<?>[] paramTypes = noCalleeThisType.parameterArray();
        boolean changed = false;
        for (int i = 0; i < paramTypes.length; ++i) {
            final Class<?> paramType = paramTypes[i];
            if (!(paramType.isPrimitive() || paramType == Object.class)) {
                paramTypes[i] = Object.class;
                changed = true;
            }
        }
        final MethodType generalized = changed ? MethodType.methodType(noCalleeThisType.returnType(), paramTypes) : noCalleeThisType;

        if (callSiteParamCount < getArity()) {
            return generalized.appendParameterTypes(Collections.<Class<?>>nCopies(getArity() - callSiteParamCount, Object.class));
        }
        return generalized;
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
        if (!getFunctionFlag(FunctionNode.IS_DECLARED) && f.isDeclared()) {
            return f.clearFlag(null, FunctionNode.IS_DECLARED);
        }
        return f;
    }

    private void logLookup(final boolean shouldLog, final MethodType targetType) {
        if (shouldLog && log.isEnabled()) {
            log.info("Looking up ", DebugLogger.quote(functionName), " type=", targetType);
        }
    }

    private MethodHandle lookup(final FunctionInitializer fnInit, final boolean shouldLog) {
        final MethodType type = fnInit.getMethodType();
        logLookup(shouldLog, type);
        return lookupCodeMethod(fnInit.getCode(), type);
    }

    MethodHandle lookup(final FunctionNode fn) {
        final MethodType type = new FunctionSignature(fn).getMethodType();
        logLookup(true, type);
        return lookupCodeMethod(fn.getCompileUnit().getCode(), type);
    }

    MethodHandle lookupCodeMethod(final Class<?> codeClass, final MethodType targetType) {
        return MH.findStatic(LOOKUP, codeClass, functionName, targetType);
    }

    /**
     * Initializes this function data with the eagerly generated version of the code. This method can only be invoked
     * by the compiler internals in Nashorn and is public for implementation reasons only. Attempting to invoke it
     * externally will result in an exception.
     *
     * @param initializer FunctionInitializer for this data
     */
    public void initializeCode(final FunctionInitializer initializer) {
        // Since the method is public, we double-check that we aren't invoked with an inappropriate compile unit.
        if(!code.isEmpty()) {
            throw new IllegalStateException(name);
        }
        addCode(lookup(initializer, true), null, null, initializer.getFlags());
    }

    private CompiledFunction addCode(final MethodHandle target, final Map<Integer, Type> invalidatedProgramPoints,
                                     final MethodType callSiteType, final int fnFlags) {
        final CompiledFunction cfn = new CompiledFunction(target, this, invalidatedProgramPoints, callSiteType, fnFlags);
        code.add(cfn);
        return cfn;
    }

    /**
     * Add code with specific call site type. It will adapt the type of the looked up method handle to fit the call site
     * type. This is necessary because even if we request a specialization that takes an "int" parameter, we might end
     * up getting one that takes a "double" etc. because of internal function logic causes widening (e.g. assignment of
     * a wider value to the parameter variable). However, we use the method handle type for matching subsequent lookups
     * for the same specialization, so we must adapt the handle to the expected type.
     * @param fnInit the function
     * @param callSiteType the call site type
     * @return the compiled function object, with its type matching that of the call site type.
     */
    private CompiledFunction addCode(final FunctionInitializer fnInit, final MethodType callSiteType) {
        if (isVariableArity()) {
            return addCode(lookup(fnInit, true), fnInit.getInvalidatedProgramPoints(), callSiteType, fnInit.getFlags());
        }

        final MethodHandle handle = lookup(fnInit, true);
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

        return addCode(lookup(fnInit, false).asType(toType), fnInit.getInvalidatedProgramPoints(), callSiteType, fnInit.getFlags());
    }

    /**
     * Returns the return type of a function specialization for particular parameter types.<br>
     * <b>Be aware that the way this is implemented, it forces full materialization (compilation and installation) of
     * code for that specialization.</b>
     * @param callSiteType the parameter types at the call site. It must include the mandatory {@code callee} and
     * {@code this} parameters, so it needs to start with at least {@code ScriptFunction.class} and
     * {@code Object.class} class. Since the return type of the function is calculated from the code itself, it is
     * irrelevant and should be set to {@code Object.class}.
     * @param runtimeScope a current runtime scope. Can be null but when it's present it will be used as a source of
     * current runtime values that can improve the compiler's type speculations (and thus reduce the need for later
     * recompilations) if the specialization is not already present and thus needs to be freshly compiled.
     * @return the return type of the function specialization.
     */
    public Class<?> getReturnType(final MethodType callSiteType, final ScriptObject runtimeScope) {
        return getBest(callSiteType, runtimeScope, CompiledFunction.NO_FUNCTIONS).type().returnType();
    }

    @Override
    synchronized CompiledFunction getBest(final MethodType callSiteType, final ScriptObject runtimeScope, final Collection<CompiledFunction> forbidden) {
        CompiledFunction existingBest = super.getBest(callSiteType, runtimeScope, forbidden);
        if (existingBest == null) {
            existingBest = addCode(compileTypeSpecialization(callSiteType, runtimeScope, true), callSiteType);
        }

        assert existingBest != null;
        //we are calling a vararg method with real args
        boolean varArgWithRealArgs = existingBest.isVarArg() && !CompiledFunction.isVarArgsType(callSiteType);

        //if the best one is an apply to call, it has to match the callsite exactly
        //or we need to regenerate
        if (existingBest.isApplyToCall()) {
            final CompiledFunction best = lookupExactApplyToCall(callSiteType);
            if (best != null) {
                return best;
            }
            varArgWithRealArgs = true;
        }

        if (varArgWithRealArgs) {
            // special case: we had an apply to call, but we failed to make it fit.
            // Try to generate a specialized one for this callsite. It may
            // be another apply to call specialization, or it may not, but whatever
            // it is, it is a specialization that is guaranteed to fit
            final FunctionInitializer fnInit = compileTypeSpecialization(callSiteType, runtimeScope, false);
            existingBest = addCode(fnInit, callSiteType);
        }

        return existingBest;
    }

    @Override
    boolean isRecompilable() {
        return true;
    }

    @Override
    public boolean needsCallee() {
        return getFunctionFlag(FunctionNode.NEEDS_CALLEE);
    }

    /**
     * Returns the {@link FunctionNode} flags associated with this function data.
     * @return the {@link FunctionNode} flags associated with this function data.
     */
    public int getFunctionFlags() {
        return functionFlags;
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
     * Return the function node id.
     * @return the function node id
     */
    public int getFunctionNodeId() {
        return functionNodeId;
    }

    /**
     * Get the source for the script
     * @return source
     */
    public Source getSource() {
        return source;
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

        do {
            if (data.hasInternalSymbol(symbolName)) {
                return false;
            }
            data = data.getParent();
        } while(data != null);

        return true;
    }

    /**
     * Restores the {@link #getFunctionFlags()} flags to a function node. During on-demand compilation, we might need
     * to restore flags to a function node that was otherwise not subjected to a full compile pipeline (e.g. its parse
     * was skipped, or it's a nested function of a deserialized function.
     * @param lc current lexical context
     * @param fn the function node to restore flags onto
     * @return the transformed function node
     */
    public FunctionNode restoreFlags(final LexicalContext lc, final FunctionNode fn) {
        assert fn.getId() == functionNodeId;
        FunctionNode newFn = fn.setFlags(lc, functionFlags);
        // This compensates for missing markEval() in case the function contains an inner function
        // that contains eval(), that now we didn't discover since we skipped the inner function.
        if (newFn.hasNestedEval()) {
            assert newFn.hasScopeBlock();
            newFn = newFn.setBody(lc, newFn.getBody().setNeedsScope(null));
        }
        return newFn;
    }

    private void readObject(final java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        createLogger();
    }

    private void createLogger() {
        log = initLogger(Context.getContextTrusted());
    }
}
