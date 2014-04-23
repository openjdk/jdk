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
import static jdk.nashorn.internal.codegen.CompilerConstants.CONSTANTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;

import java.io.File;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import jdk.internal.dynalink.support.NameCodec;
import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.CompilationEnvironment.CompilationPhases;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.TemporarySymbols;
import jdk.nashorn.internal.ir.debug.ClassHistogramElement;
import jdk.nashorn.internal.ir.debug.ObjectSizeCalculator;
import jdk.nashorn.internal.runtime.CodeInstaller;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.Timing;
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

    private Source source;

    private String sourceName;
    private String sourceURL;

    private final Map<String, byte[]> bytecode;

    private final Set<CompileUnit> compileUnits;

    private final ConstantData constantData;

    private final CompilationEnvironment compilationEnv;

    private final ScriptEnvironment scriptEnv;

    private String scriptName;

    private final CodeInstaller<ScriptEnvironment> installer;

    private final TemporarySymbols temporarySymbols = new TemporarySymbols();

    /** logger for compiler, trampolines, splits and related code generation events
     *  that affect classes */
    private final DebugLogger log;

    private static boolean initialized = false;

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

    private void initCompiler(final String className, final FunctionNode functionNode) {
        this.source = functionNode.getSource();
        this.sourceName = functionNode.getSourceName();
        this.sourceURL = functionNode.getSourceURL();

        if (functionNode.isStrict()) {
            compilationEnv.setIsStrict(true);
        }

        final String name       = className + '$' + safeSourceName(functionNode.getSource());
        final String uniqueName = functionNode.uniqueName(name);

        this.scriptName = uniqueName;
    }

    private Compiler(final CompilationEnvironment compilationEnv, final ScriptEnvironment scriptEnv, final CodeInstaller<ScriptEnvironment> installer) {
        this.scriptEnv      = scriptEnv;
        this.compilationEnv = compilationEnv;
        this.installer      = installer;
        this.constantData   = new ConstantData();
        this.compileUnits   = new TreeSet<>();
        this.bytecode       = new LinkedHashMap<>();
        this.log            = initLogger(compilationEnv.getContext());

        if (!initialized) {
            initialized = true;
            if (!ScriptEnvironment.globalOptimistic()) {
                log.warning("Running without optimistic types. This is a configuration that may be deprecated.");
            }
        }
    }

    /**
     * Constructor - common entry point for generating code.
     * @param env compilation environment
     * @param installer code installer
     */
    public Compiler(final CompilationEnvironment env, final CodeInstaller<ScriptEnvironment> installer) {
        this(env, installer.getOwner(), installer);
    }

    /**
     * ScriptEnvironment constructor for compiler. Used only from Shell and --compile-only flag
     * No code installer supplied
     * @param scriptEnv script environment
     */
    public Compiler(final ScriptEnvironment scriptEnv) {
        this(new CompilationEnvironment(Context.getContext(), CompilationPhases.EAGER, scriptEnv._strict), scriptEnv, null);
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    private void printMemoryUsage(final String phaseName, final FunctionNode functionNode) {
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

    CompilationEnvironment getCompilationEnvironment() {
        return compilationEnv;
    }

    /**
     * Execute the compilation this Compiler was created with with default class name
     * @param functionNode function node to compile from its current state
     * @throws CompilationException if something goes wrong
     * @return function node that results from code transforms
     */
    public FunctionNode compile(final FunctionNode functionNode) throws CompilationException {
        return compile(CompilerConstants.DEFAULT_SCRIPT_NAME.symbolName(), functionNode);
    }

    /**
     * Execute the compilation this Compiler was created with
     * @param className    class name for the compile
     * @param functionNode function node to compile from its current state
     * @throws CompilationException if something goes wrong
     * @return function node that results from code transforms
     */
    public FunctionNode compile(final String className, final FunctionNode functionNode) throws CompilationException {
        try {
            return compileInternal(className, functionNode);
        } catch (final AssertionError e) {
            throw new AssertionError("Assertion failure compiling " + functionNode.getSource(), e);
        }
    }

    private FunctionNode compileInternal(final String className, final FunctionNode functionNode) throws CompilationException {
        FunctionNode newFunctionNode = functionNode;

        initCompiler(className, newFunctionNode); //TODO move this state into functionnode?

        for (final String reservedName : RESERVED_NAMES) {
            newFunctionNode.uniqueName(reservedName);
        }

        final boolean fine = log.levelFinerThanOrEqual(Level.FINE);
        final boolean info = log.levelFinerThanOrEqual(Level.INFO);

        long time = 0L;

        for (final CompilationPhase phase : compilationEnv.getPhases()) {
            newFunctionNode = phase.apply(this, newFunctionNode);

            if (scriptEnv._print_mem_usage) {
                printMemoryUsage(phase.toString(), newFunctionNode);
            }

            final long duration = Timing.isEnabled() ? phase.getEndTime() - phase.getStartTime() : 0L;
            time += duration;

            if (fine) {
                final StringBuilder sb = new StringBuilder();

                sb.append(phase.toString()).
                    append(" done for function '").
                    append(newFunctionNode.getName()).
                    append('\'');

                if (duration > 0L) {
                    sb.append(" in ").
                        append(duration).
                        append(" ms ");
                }

                log.fine(sb);
            }
        }

        if (info) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Compile job for '").
                append(newFunctionNode.getSource()).
                append(':').
                append(newFunctionNode.getName()).
                append("' finished");

            if (time > 0L) {
                sb.append(" in ").
                    append(time).
                    append(" ms");
            }

            log.info(sb);
        }

        return newFunctionNode;
    }

    private Class<?> install(final String className, final byte[] code) {
        log.fine("Installing class ", className);

        final Class<?> clazz = installer.install(Compiler.binaryName(className), code);

        try {
            final Object[] constants = getConstantData().toArray();
            // Need doPrivileged because these fields are private
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws Exception {
                    //use reflection to write source and constants table to installed classes
                    final Field sourceField    = clazz.getDeclaredField(SOURCE.symbolName());
                    final Field constantsField = clazz.getDeclaredField(CONSTANTS.symbolName());
                    sourceField.setAccessible(true);
                    constantsField.setAccessible(true);
                    sourceField.set(null, source);
                    constantsField.set(null, constants);
                    return null;
                }
            });
        } catch (final PrivilegedActionException e) {
            throw new RuntimeException(e);
        }

        return clazz;
    }

    /**
     * Install compiled classes into a given loader
     * @param functionNode function node to install - must be in {@link CompilationState#EMITTED} state
     * @return root script class - if there are several compile units they will also be installed
     */
    public Class<?> install(final FunctionNode functionNode) {
        final long t0 = Timing.isEnabled() ? System.currentTimeMillis() : 0L;

        assert functionNode.hasState(CompilationState.EMITTED) : functionNode.getName() + " has unexpected compilation state";

        final Map<String, Class<?>> installedClasses = new HashMap<>();

        final String   rootClassName = firstCompileUnitName();
        final byte[]   rootByteCode  = bytecode.get(rootClassName);
        final Class<?> rootClass     = install(rootClassName, rootByteCode);

        int length = rootByteCode.length;

        installedClasses.put(rootClassName, rootClass);

        for (final Entry<String, byte[]> entry : bytecode.entrySet()) {
            final String className = entry.getKey();
            if (className.equals(rootClassName)) {
                continue;
            }
            final byte[] code = entry.getValue();
            length += code.length;

            installedClasses.put(className, install(className, code));
        }

        final Map<RecompilableScriptFunctionData, RecompilableScriptFunctionData> rfns = new IdentityHashMap<>();
        for(final Object constant: getConstantData().constants) {
            if(constant instanceof RecompilableScriptFunctionData) {
                final RecompilableScriptFunctionData rfn = (RecompilableScriptFunctionData)constant;
                rfns.put(rfn, rfn);
            }
        }

        for (final CompileUnit unit : compileUnits) {
            unit.setCode(installedClasses.get(unit.getUnitClassName()));
            unit.initializeFunctionsCode();
        }

        final StringBuilder sb;
        if (log.isEnabled()) {
            sb = new StringBuilder();
            sb.append("Installed class '").
                append(rootClass.getSimpleName()).
                append('\'').
                append(" bytes=").
                append(length).
                append('.');
            if (bytecode.size() > 1) {
                sb.append(' ').append(bytecode.size()).append(" compile units.");
            }
        } else {
            sb = null;
        }

        if (Timing.isEnabled()) {
            final long duration = System.currentTimeMillis() - t0;
            Timing.accumulateTime("[Code Installation]", duration);
            if (sb != null) {
                sb.append(" Install time: ").append(duration).append(" ms");
            }
        }

        if (sb != null) {
            log.fine(sb);
        }

        return rootClass;
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

    TemporarySymbols getTemporarySymbols() {
        return temporarySymbols;
    }

    void addClass(final String name, final byte[] code) {
        bytecode.put(name, code);
    }

    ScriptEnvironment getEnv() {
        return this.scriptEnv;
    }

    String getSourceURL() {
        return sourceURL;
    }

    private String safeSourceName(final Source src) {
        String baseName = new File(src.getName()).getName();

        final int index = baseName.lastIndexOf(".js");
        if (index != -1) {
            baseName = baseName.substring(0, index);
        }

        baseName = baseName.replace('.', '_').replace('-', '_');
        if (!scriptEnv._loader_per_compile) {
            baseName = baseName + installer.getUniqueScriptId();
        }

        final String mangled = NameCodec.encode(baseName);
        return mangled != null ? mangled : baseName;
    }

    private int nextCompileUnitIndex() {
        return compileUnits.size() + 1;
    }

    String firstCompileUnitName() {
        return SCRIPTS_PACKAGE + '/' + scriptName;
    }

    private String nextCompileUnitName() {
        return firstCompileUnitName() + '$' + nextCompileUnitIndex();
    }

    CompileUnit addCompileUnit(final long initialWeight) {
        return addCompileUnit(nextCompileUnitName(), initialWeight);
    }

    CompileUnit addCompileUnit(final String unitClassName) {
        return addCompileUnit(unitClassName, 0L);
    }

    private CompileUnit addCompileUnit(final String unitClassName, final long initialWeight) {
        final CompileUnit compileUnit = initCompileUnit(unitClassName, initialWeight);
        compileUnits.add(compileUnit);
        log.fine("Added compile unit ", compileUnit);
        return compileUnit;
    }

    private CompileUnit initCompileUnit(final String unitClassName, final long initialWeight) {
        final ClassEmitter classEmitter = new ClassEmitter(compilationEnv.getContext(), sourceName, unitClassName, compilationEnv.isStrict());
        final CompileUnit  compileUnit  = new CompileUnit(unitClassName, classEmitter, initialWeight);

        classEmitter.begin();

        final MethodEmitter initMethod = classEmitter.init(EnumSet.of(Flag.PRIVATE));
        initMethod.begin();
        initMethod.load(Type.OBJECT, 0);
        initMethod.newInstance(jdk.nashorn.internal.scripts.JS.class);
        initMethod.returnVoid();
        initMethod.end();

        return compileUnit;
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

}
