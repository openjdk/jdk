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
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.runtime.logging.DebugLogger.quote;

import java.io.File;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import jdk.internal.dynalink.support.NameCodec;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.ir.debug.ClassHistogramElement;
import jdk.nashorn.internal.ir.debug.ObjectSizeCalculator;
import jdk.nashorn.internal.runtime.CodeInstaller;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.FunctionInitializer;
import jdk.nashorn.internal.runtime.ParserException;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Responsible for converting JavaScripts to java byte code. Main entry
 * point for code generator. The compiler may also install classes given some
 * predefined Code installation policy, given to it at construction time.
 * @see CodeInstaller
 */
@Logger(name="compiler")
public final class Compiler implements Loggable {

    /** Name of the scripts package */
    public static final String SCRIPTS_PACKAGE = "jdk/nashorn/internal/scripts";

    /** Name of the objects package */
    public static final String OBJECTS_PACKAGE = "jdk/nashorn/internal/objects";

    private final ScriptEnvironment env;

    private final Source source;

    private final String sourceName;

    private final ErrorManager errors;

    private final boolean optimistic;

    private final Map<String, byte[]> bytecode;

    private final Set<CompileUnit> compileUnits;

    private final ConstantData constantData;

    private final CodeInstaller<ScriptEnvironment> installer;

    /** logger for compiler, trampolines and related code generation events
     *  that affect classes */
    private final DebugLogger log;

    private final Context context;

    private final TypeMap types;

    // Runtime scope in effect at the time of the compilation. Used to evaluate types of expressions and prevent overly
    // optimistic assumptions (which will lead to unnecessary deoptimizing recompilations).
    private final TypeEvaluator typeEvaluator;

    private final boolean strict;

    private final boolean onDemand;

    /**
     * If this is a recompilation, this is how we pass in the invalidations, e.g. programPoint=17, Type == int means
     * that using whatever was at program point 17 as an int failed.
     */
    private final Map<Integer, Type> invalidatedProgramPoints;

    /**
     * Descriptor of the location where we write the type information after compilation.
     */
    private final Object typeInformationFile;

    /**
     * Compile unit name of first compile unit - this prefix will be used for all
     * classes that a compilation generates.
     */
    private final String firstCompileUnitName;

    /**
     * Contains the program point that should be used as the continuation entry point, as well as all previous
     * continuation entry points executed as part of a single logical invocation of the function. In practical terms, if
     * we execute a rest-of method from the program point 17, but then we hit deoptimization again during it at program
     * point 42, and execute a rest-of method from the program point 42, and then we hit deoptimization again at program
     * point 57 and are compiling a rest-of method for it, the values in the array will be [57, 42, 17]. This is only
     * set when compiling a rest-of method. If this method is a rest-of for a non-rest-of method, the array will have
     * one element. If it is a rest-of for a rest-of, the array will have two elements, and so on.
     */
    private final int[] continuationEntryPoints;

    /**
     * ScriptFunction data for what is being compile, where applicable.
     * TODO: make this immutable, propagate it through the CompilationPhases
     */
    private RecompilableScriptFunctionData compiledFunction;

    /**
     * Most compile unit names are longer than the default StringBuilder buffer,
     * worth startup performance when massive class generation is going on to increase
     * this
     */
    private static final int COMPILE_UNIT_NAME_BUFFER_SIZE = 32;

    private final Map<Integer, byte[]> serializedAsts = new HashMap<>();

    /**
     * Compilation phases that a compilation goes through
     */
    public static class CompilationPhases implements Iterable<CompilationPhase> {

        /**
         * Singleton that describes compilation up to the phase where a function can be serialized.
         */
        private final static CompilationPhases COMPILE_UPTO_SERIALIZABLE = new CompilationPhases(
                "Common initial phases",
                CompilationPhase.CONSTANT_FOLDING_PHASE,
                CompilationPhase.LOWERING_PHASE,
                CompilationPhase.TRANSFORM_BUILTINS_PHASE,
                CompilationPhase.SPLITTING_PHASE,
                CompilationPhase.PROGRAM_POINT_PHASE,
                CompilationPhase.SERIALIZE_SPLIT_PHASE
                );

        private final static CompilationPhases COMPILE_SERIALIZABLE_UPTO_BYTECODE = new CompilationPhases(
                "After common phases, before bytecode generator",
                CompilationPhase.SYMBOL_ASSIGNMENT_PHASE,
                CompilationPhase.SCOPE_DEPTH_COMPUTATION_PHASE,
                CompilationPhase.OPTIMISTIC_TYPE_ASSIGNMENT_PHASE,
                CompilationPhase.LOCAL_VARIABLE_TYPE_CALCULATION_PHASE
                );

        /**
         * Singleton that describes additional steps to be taken after deserializing, all the way up to (but not
         * including) generating and installing code.
         */
        public final static CompilationPhases RECOMPILE_SERIALIZED_UPTO_BYTECODE = new CompilationPhases(
                "Recompile serialized function up to bytecode",
                CompilationPhase.REINITIALIZE_SERIALIZED,
                COMPILE_SERIALIZABLE_UPTO_BYTECODE
                );

        /**
         * Singleton that describes back end of method generation, given that we have generated the normal
         * method up to CodeGenerator as in {@link CompilationPhases#COMPILE_UPTO_BYTECODE}
         */
        public final static CompilationPhases GENERATE_BYTECODE_AND_INSTALL = new CompilationPhases(
                "Generate bytecode and install",
                CompilationPhase.BYTECODE_GENERATION_PHASE,
                CompilationPhase.INSTALL_PHASE
                );

        /** Singleton that describes compilation up to the CodeGenerator, but not actually generating code */
        public final static CompilationPhases COMPILE_UPTO_BYTECODE = new CompilationPhases(
                "Compile upto bytecode",
                COMPILE_UPTO_SERIALIZABLE,
                COMPILE_SERIALIZABLE_UPTO_BYTECODE);

        /** Singleton that describes a standard eager compilation, but no installation, for example used by --compile-only */
        public final static CompilationPhases COMPILE_ALL_NO_INSTALL = new CompilationPhases(
                "Compile without install",
                COMPILE_UPTO_BYTECODE,
                CompilationPhase.BYTECODE_GENERATION_PHASE);

        /** Singleton that describes a standard eager compilation - this includes code installation */
        public final static CompilationPhases COMPILE_ALL = new CompilationPhases(
                "Full eager compilation",
                COMPILE_UPTO_BYTECODE,
                GENERATE_BYTECODE_AND_INSTALL);

        /** Singleton that describes a full compilation - this includes code installation - from serialized state*/
        public final static CompilationPhases COMPILE_ALL_SERIALIZED = new CompilationPhases(
                "Eager compilation from serializaed state",
                RECOMPILE_SERIALIZED_UPTO_BYTECODE,
                GENERATE_BYTECODE_AND_INSTALL);

        /**
         * Singleton that describes restOf method generation, given that we have generated the normal
         * method up to CodeGenerator as in {@link CompilationPhases#COMPILE_UPTO_BYTECODE}
         */
        public final static CompilationPhases GENERATE_BYTECODE_AND_INSTALL_RESTOF = new CompilationPhases(
                "Generate bytecode and install - RestOf method",
                CompilationPhase.REUSE_COMPILE_UNITS_PHASE,
                GENERATE_BYTECODE_AND_INSTALL);

        /** Compile all for a rest of method */
        public final static CompilationPhases COMPILE_ALL_RESTOF = new CompilationPhases(
                "Compile all, rest of",
                COMPILE_UPTO_BYTECODE,
                GENERATE_BYTECODE_AND_INSTALL_RESTOF);

        /** Compile from serialized for a rest of method */
        public final static CompilationPhases COMPILE_SERIALIZED_RESTOF = new CompilationPhases(
                "Compile serialized, rest of",
                RECOMPILE_SERIALIZED_UPTO_BYTECODE,
                GENERATE_BYTECODE_AND_INSTALL_RESTOF);

        private final List<CompilationPhase> phases;

        private final String desc;

        private CompilationPhases(final String desc, final CompilationPhase... phases) {
            this(desc, Arrays.asList(phases));
        }

        private CompilationPhases(final String desc, final CompilationPhases base, final CompilationPhase... phases) {
            this(desc, concat(base.phases, Arrays.asList(phases)));
        }

        private CompilationPhases(final String desc, final CompilationPhase first, final CompilationPhases rest) {
            this(desc, concat(Collections.singletonList(first), rest.phases));
        }

        private CompilationPhases(final String desc, final CompilationPhases base) {
            this(desc, base.phases);
        }

        private CompilationPhases(final String desc, final CompilationPhases... bases) {
            this(desc, concatPhases(bases));
        }

        private CompilationPhases(final String desc, final List<CompilationPhase> phases) {
            this.desc = desc;
            this.phases = phases;
        }

        private static List<CompilationPhase> concatPhases(final CompilationPhases[] bases) {
            final ArrayList<CompilationPhase> l = new ArrayList<>();
            for(final CompilationPhases base: bases) {
                l.addAll(base.phases);
            }
            l.trimToSize();
            return l;
        }

        private static <T> List<T> concat(final List<T> l1, final List<T> l2) {
            final ArrayList<T> l = new ArrayList<>(l1);
            l.addAll(l2);
            l.trimToSize();
            return l;
        }

        @Override
        public String toString() {
            return "'" + desc + "' " + phases.toString();
        }

        boolean contains(final CompilationPhase phase) {
            return phases.contains(phase);
        }

        @Override
        public Iterator<CompilationPhase> iterator() {
            return phases.iterator();
        }

        boolean isRestOfCompilation() {
            return this == COMPILE_ALL_RESTOF || this == GENERATE_BYTECODE_AND_INSTALL_RESTOF || this == COMPILE_SERIALIZED_RESTOF;
        }

        String getDesc() {
            return desc;
        }

        String toString(final String prefix) {
            final StringBuilder sb = new StringBuilder();
            for (final CompilationPhase phase : phases) {
                sb.append(prefix).append(phase).append('\n');
            }
            return sb.toString();
        }
    }

    /**
     * This array contains names that need to be reserved at the start
     * of a compile, to avoid conflict with variable names later introduced.
     * See {@link CompilerConstants} for special names used for structures
     * during a compile.
     */
    private static String[] RESERVED_NAMES = {
        SCOPE.symbolName(),
        THIS.symbolName(),
        RETURN.symbolName(),
        CALLEE.symbolName(),
        VARARGS.symbolName(),
        ARGUMENTS.symbolName()
    };

    // per instance
    private final int compilationId = COMPILATION_ID.getAndIncrement();

    // per instance
    private final AtomicInteger nextCompileUnitId = new AtomicInteger(0);

    private static final AtomicInteger COMPILATION_ID = new AtomicInteger(0);

    /**
     * Constructor
     *
     * @param context   context
     * @param env       script environment
     * @param installer code installer
     * @param source    source to compile
     * @param errors    error manager
     * @param isStrict  is this a strict compilation
     */
    public Compiler(
            final Context context,
            final ScriptEnvironment env,
            final CodeInstaller<ScriptEnvironment> installer,
            final Source source,
            final ErrorManager errors,
            final boolean isStrict) {
        this(context, env, installer, source, errors, isStrict, false, null, null, null, null, null, null);
    }

    /**
     * Constructor
     *
     * @param context                  context
     * @param env                      script environment
     * @param installer                code installer
     * @param source                   source to compile
     * @param errors                   error manager
     * @param isStrict                 is this a strict compilation
     * @param isOnDemand               is this an on demand compilation
     * @param compiledFunction         compiled function, if any
     * @param types                    parameter and return value type information, if any is known
     * @param invalidatedProgramPoints invalidated program points for recompilation
     * @param typeInformationFile      descriptor of the location where type information is persisted
     * @param continuationEntryPoints  continuation entry points for restof method
     * @param runtimeScope             runtime scope for recompilation type lookup in {@code TypeEvaluator}
     */
    @SuppressWarnings("unused")
    public Compiler(
            final Context context,
            final ScriptEnvironment env,
            final CodeInstaller<ScriptEnvironment> installer,
            final Source source,
            final ErrorManager errors,
            final boolean isStrict,
            final boolean isOnDemand,
            final RecompilableScriptFunctionData compiledFunction,
            final TypeMap types,
            final Map<Integer, Type> invalidatedProgramPoints,
            final Object typeInformationFile,
            final int[] continuationEntryPoints,
            final ScriptObject runtimeScope) {
        this.context                  = context;
        this.env                      = env;
        this.installer                = installer;
        this.constantData             = new ConstantData();
        this.compileUnits             = CompileUnit.createCompileUnitSet();
        this.bytecode                 = new LinkedHashMap<>();
        this.log                      = initLogger(context);
        this.source                   = source;
        this.errors                   = errors;
        this.sourceName               = FunctionNode.getSourceName(source);
        this.onDemand                 = isOnDemand;
        this.compiledFunction         = compiledFunction;
        this.types                    = types;
        this.invalidatedProgramPoints = invalidatedProgramPoints == null ? new HashMap<Integer, Type>() : invalidatedProgramPoints;
        this.typeInformationFile      = typeInformationFile;
        this.continuationEntryPoints  = continuationEntryPoints == null ? null: continuationEntryPoints.clone();
        this.typeEvaluator            = new TypeEvaluator(this, runtimeScope);
        this.firstCompileUnitName     = firstCompileUnitName();
        this.strict                   = isStrict;

        this.optimistic = env._optimistic_types;
    }

    private static String safeSourceName(final ScriptEnvironment env, final CodeInstaller<ScriptEnvironment> installer, final Source source) {
        String baseName = new File(source.getName()).getName();

        final int index = baseName.lastIndexOf(".js");
        if (index != -1) {
            baseName = baseName.substring(0, index);
        }

        baseName = baseName.replace('.', '_').replace('-', '_');
        if (!env._loader_per_compile) {
            baseName = baseName + installer.getUniqueScriptId();
        }

        // ASM's bytecode verifier does not allow JVM allowed safe escapes using '\' as escape char.
        // While ASM accepts such escapes for method names, field names, it enforces Java identifier
        // for class names. Workaround that ASM bug here by replacing JVM 'dangerous' chars with '_'
        // rather than safe encoding using '\'.
        final String mangled = env._verify_code? replaceDangerChars(baseName) : NameCodec.encode(baseName);
        return mangled != null ? mangled : baseName;
    }

    private static final String DANGEROUS_CHARS   = "\\/.;:$[]<>";
    private static String replaceDangerChars(final String name) {
        final int len = name.length();
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < len; i++) {
            final char ch = name.charAt(i);
            if (DANGEROUS_CHARS.indexOf(ch) != -1) {
                buf.append('_');
            } else {
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    private String firstCompileUnitName() {
        final StringBuilder sb = new StringBuilder(SCRIPTS_PACKAGE).
                append('/').
                append(CompilerConstants.DEFAULT_SCRIPT_NAME.symbolName()).
                append('$');

        if (isOnDemandCompilation()) {
            sb.append(RecompilableScriptFunctionData.RECOMPILATION_PREFIX);
        }

        if (compilationId > 0) {
            sb.append(compilationId).append('$');
        }

        if (types != null && compiledFunction.getFunctionNodeId() > 0) {
            sb.append(compiledFunction.getFunctionNodeId());
            final Type[] paramTypes = types.getParameterTypes(compiledFunction.getFunctionNodeId());
            for (final Type t : paramTypes) {
                sb.append(Type.getShortSignatureDescriptor(t));
            }
            sb.append('$');
        }

        sb.append(Compiler.safeSourceName(env, installer, source));

        return sb.toString();
    }

    void declareLocalSymbol(final String symbolName) {
        typeEvaluator.declareLocalSymbol(symbolName);
    }

    void setData(final RecompilableScriptFunctionData data) {
        assert this.compiledFunction == null : data;
        this.compiledFunction = data;
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context ctxt) {
        final boolean optimisticTypes = env._optimistic_types;
        final boolean lazyCompilation = env._lazy_compilation;

        return ctxt.getLogger(this.getClass(), new Consumer<DebugLogger>() {
            @Override
            public void accept(final DebugLogger newLogger) {
                if (!lazyCompilation) {
                    newLogger.warning("WARNING: Running with lazy compilation switched off. This is not a default setting.");
                }
                newLogger.warning("Optimistic types are ", optimisticTypes ? "ENABLED." : "DISABLED.");
            }
        });
    }

    ScriptEnvironment getScriptEnvironment() {
        return env;
    }

    boolean isOnDemandCompilation() {
        return onDemand;
    }

    boolean useOptimisticTypes() {
        return optimistic;
    }

    Context getContext() {
        return context;
    }

    Type getOptimisticType(final Optimistic node) {
        return typeEvaluator.getOptimisticType(node);
    }

    /**
     * Returns true if the expression can be safely evaluated, and its value is an object known to always use
     * String as the type of its property names retrieved through
     * {@link ScriptRuntime#toPropertyIterator(Object)}. It is used to avoid optimistic assumptions about its
     * property name types.
     * @param expr the expression to test
     * @return true if the expression can be safely evaluated, and its value is an object known to always use
     * String as the type of its property iterators.
     */
    boolean hasStringPropertyIterator(final Expression expr) {
        return typeEvaluator.hasStringPropertyIterator(expr);
    }

    void addInvalidatedProgramPoint(final int programPoint, final Type type) {
        invalidatedProgramPoints.put(programPoint, type);
    }


    /**
     * Returns a copy of this compiler's current mapping of invalidated optimistic program points to their types. The
     * copy is not live with regard to changes in state in this compiler instance, and is mutable.
     * @return a copy of this compiler's current mapping of invalidated optimistic program points to their types.
     */
    public Map<Integer, Type> getInvalidatedProgramPoints() {
        return invalidatedProgramPoints.isEmpty() ? null : new TreeMap<>(invalidatedProgramPoints);
    }

    TypeMap getTypeMap() {
        return types;
    }

    MethodType getCallSiteType(final FunctionNode fn) {
        if (types == null || !isOnDemandCompilation()) {
            return null;
        }
        return types.getCallSiteType(fn);
    }

    Type getParamType(final FunctionNode fn, final int pos) {
        return types == null ? null : types.get(fn, pos);
    }

    /**
     * Do a compilation job
     *
     * @param functionNode function node to compile
     * @param phases phases of compilation transforms to apply to function

     * @return transformed function
     *
     * @throws CompilationException if error occurs during compilation
     */
    public FunctionNode compile(final FunctionNode functionNode, final CompilationPhases phases) throws CompilationException {
        if (log.isEnabled()) {
            log.info(">> Starting compile job for ", DebugLogger.quote(functionNode.getName()), " phases=", quote(phases.getDesc()));
            log.indent();
        }

        final String name = DebugLogger.quote(functionNode.getName());

        FunctionNode newFunctionNode = functionNode;

        for (final String reservedName : RESERVED_NAMES) {
            newFunctionNode.uniqueName(reservedName);
        }

        final boolean info = log.levelFinerThanOrEqual(Level.INFO);

        final DebugLogger timeLogger = env.isTimingEnabled() ? env._timing.getLogger() : null;

        long time = 0L;

        for (final CompilationPhase phase : phases) {
            log.fine(phase, " starting for ", name);

            try {
                newFunctionNode = phase.apply(this, phases, newFunctionNode);
            } catch (final ParserException error) {
                errors.error(error);
                if (env._dump_on_error) {
                    error.printStackTrace(env.getErr());
                }
                return null;
            }

            log.fine(phase, " done for function ", quote(name));

            if (env._print_mem_usage) {
                printMemoryUsage(functionNode, phase.toString());
            }

            time += (env.isTimingEnabled() ? phase.getEndTime() - phase.getStartTime() : 0L);
        }

        if (typeInformationFile != null && !phases.isRestOfCompilation()) {
            OptimisticTypesPersistence.store(typeInformationFile, invalidatedProgramPoints);
        }

        log.unindent();

        if (info) {
            final StringBuilder sb = new StringBuilder("<< Finished compile job for ");
            sb.append(newFunctionNode.getSource()).
                append(':').
                append(quote(newFunctionNode.getName()));

            if (time > 0L && timeLogger != null) {
                assert env.isTimingEnabled();
                sb.append(" in ").append(time).append(" ms");
            }
            log.info(sb);
        }

        return newFunctionNode;
    }

    Source getSource() {
        return source;
    }

    Map<String, byte[]> getBytecode() {
        return Collections.unmodifiableMap(bytecode);
    }

    /**
     * Reset bytecode cache for compiler reuse.
     */
    void clearBytecode() {
        bytecode.clear();
    }

    CompileUnit getFirstCompileUnit() {
        assert !compileUnits.isEmpty();
        return compileUnits.iterator().next();
    }

    Set<CompileUnit> getCompileUnits() {
        return compileUnits;
    }

    ConstantData getConstantData() {
        return constantData;
    }

    CodeInstaller<ScriptEnvironment> getCodeInstaller() {
        return installer;
    }

    void addClass(final String name, final byte[] code) {
        bytecode.put(name, code);
    }

    String nextCompileUnitName() {
        final StringBuilder sb = new StringBuilder(COMPILE_UNIT_NAME_BUFFER_SIZE);
        sb.append(firstCompileUnitName);
        final int cuid = nextCompileUnitId.getAndIncrement();
        if (cuid > 0) {
            sb.append("$cu").append(cuid);
        }

        return sb.toString();
    }

    /**
     * Persist current compilation with the given {@code cacheKey}.
     * @param cacheKey cache key
     * @param functionNode function node
     */
    public void persistClassInfo(final String cacheKey, final FunctionNode functionNode) {
        if (cacheKey != null && env._persistent_cache) {
            // If this is an on-demand compilation create a function initializer for the function being compiled.
            // Otherwise use function initializer map generated by codegen.
            Map<Integer, FunctionInitializer> initializers = new HashMap<>();
            if (isOnDemandCompilation()) {
                initializers.put(functionNode.getId(), new FunctionInitializer(functionNode, getInvalidatedProgramPoints()));
            } else {
                for (final CompileUnit compileUnit : getCompileUnits()) {
                    for (final FunctionNode fn : compileUnit.getFunctionNodes()) {
                        initializers.put(fn.getId(), new FunctionInitializer(fn));
                    }
                }
            }
            final String mainClassName = getFirstCompileUnit().getUnitClassName();
            installer.storeScript(cacheKey, source, mainClassName, bytecode, initializers, constantData.toArray(), compilationId);
        }
    }

    /**
     * Make sure the next compilation id is greater than {@code value}.
     * @param value compilation id value
     */
    public static void updateCompilationId(final int value) {
        if (value >= COMPILATION_ID.get()) {
            COMPILATION_ID.set(value + 1);
        }
    }

    CompileUnit addCompileUnit(final long initialWeight) {
        final CompileUnit compileUnit = createCompileUnit(initialWeight);
        compileUnits.add(compileUnit);
        log.fine("Added compile unit ", compileUnit);
        return compileUnit;
    }

    CompileUnit createCompileUnit(final String unitClassName, final long initialWeight) {
        final ClassEmitter classEmitter = new ClassEmitter(context, sourceName, unitClassName, isStrict());
        final CompileUnit  compileUnit  = new CompileUnit(unitClassName, classEmitter, initialWeight);
        classEmitter.begin();

        return compileUnit;
    }

    private CompileUnit createCompileUnit(final long initialWeight) {
        return createCompileUnit(nextCompileUnitName(), initialWeight);
    }

    boolean isStrict() {
        return strict;
    }

    void replaceCompileUnits(final Set<CompileUnit> newUnits) {
        compileUnits.clear();
        compileUnits.addAll(newUnits);
    }

    void serializeAst(final FunctionNode fn) {
        serializedAsts.put(fn.getId(), AstSerializer.serialize(fn));
    }

    byte[] removeSerializedAst(final int fnId) {
        return serializedAsts.remove(fnId);
    }

    CompileUnit findUnit(final long weight) {
        for (final CompileUnit unit : compileUnits) {
            if (unit.canHold(weight)) {
                unit.addWeight(weight);
                return unit;
            }
        }

        return addCompileUnit(weight);
    }

    /**
     * Convert a package/class name to a binary name.
     *
     * @param name Package/class name.
     * @return Binary name.
     */
    public static String binaryName(final String name) {
        return name.replace('/', '.');
    }

    RecompilableScriptFunctionData getScriptFunctionData(final int functionId) {
        assert compiledFunction != null;
        final RecompilableScriptFunctionData fn = compiledFunction.getScriptFunctionData(functionId);
        assert fn != null : functionId;
        return fn;
    }

    boolean isGlobalSymbol(final FunctionNode fn, final String name) {
        return getScriptFunctionData(fn.getId()).isGlobalSymbol(fn, name);
    }

    int[] getContinuationEntryPoints() {
        return continuationEntryPoints;
    }

    Type getInvalidatedProgramPointType(final int programPoint) {
        return invalidatedProgramPoints.get(programPoint);
    }

    private void printMemoryUsage(final FunctionNode functionNode, final String phaseName) {
        if (!log.isEnabled()) {
            return;
        }

        log.info(phaseName, "finished. Doing IR size calculation...");

        final ObjectSizeCalculator osc = new ObjectSizeCalculator(ObjectSizeCalculator.getEffectiveMemoryLayoutSpecification());
        osc.calculateObjectSize(functionNode);

        final List<ClassHistogramElement> list      = osc.getClassHistogram();
        final StringBuilder               sb        = new StringBuilder();
        final long                        totalSize = osc.calculateObjectSize(functionNode);

        sb.append(phaseName).
            append(" Total size = ").
            append(totalSize / 1024 / 1024).
            append("MB");
        log.info(sb);

        Collections.sort(list, new Comparator<ClassHistogramElement>() {
            @Override
            public int compare(final ClassHistogramElement o1, final ClassHistogramElement o2) {
                final long diff = o1.getBytes() - o2.getBytes();
                if (diff < 0) {
                    return 1;
                } else if (diff > 0) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
        for (final ClassHistogramElement e : list) {
            final String line = String.format("    %-48s %10d bytes (%8d instances)", e.getClazz(), e.getBytes(), e.getInstances());
            log.info(line);
            if (e.getBytes() < totalSize / 200) {
                log.info("    ...");
                break; // never mind, so little memory anyway
            }
        }
    }
}
