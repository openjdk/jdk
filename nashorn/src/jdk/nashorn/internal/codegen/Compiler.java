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

import static jdk.nashorn.internal.codegen.CompilerConstants.CONSTANTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.DEFAULT_SCRIPT_NAME;
import static jdk.nashorn.internal.codegen.CompilerConstants.LAZY;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;

import java.io.File;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import jdk.internal.dynalink.support.NameCodec;
import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.runtime.CodeInstaller;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.Timing;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Responsible for converting JavaScripts to java byte code. Main entry
 * point for code generator. The compiler may also install classes given some
 * predefined Code installation policy, given to it at construction time.
 * @see CodeInstaller
 */
public final class Compiler {

    /** Name of the scripts package */
    public static final String SCRIPTS_PACKAGE = "jdk/nashorn/internal/scripts";

    /** Name of the objects package */
    public static final String OBJECTS_PACKAGE = "jdk/nashorn/internal/objects";

    static final boolean LAZY_JIT = Options.getBooleanProperty("nashorn.compiler.lazy");

    private final Map<String, byte[]> bytecode;

    private final Set<CompileUnit> compileUnits;

    private final ConstantData constantData;

    private final FunctionNode functionNode;

    private final CompilationSequence sequence;

    private final Context context;

    private final String scriptName;

    private boolean strict;

    private CodeInstaller<Context> installer;

    /** logger for compiler, trampolines, splits and related code generation events
     *  that affect classes */
    public static final DebugLogger LOG = new DebugLogger("compiler");

    /**
     * This array contains names that need to be reserved at the start
     * of a compile, to avoid conflict with variable names later introduced.
     * See {@link CompilerConstants} for special names used for structures
     * during a compile.
     */
    private static String[] RESERVED_NAMES = {
        SCOPE.tag(),
        THIS.tag()
    };

    /**
     * This class makes it possible to do your own compilation sequence
     * from the code generation package. There are predefined compilation
     * sequences already
     */
    @SuppressWarnings("serial")
    static class CompilationSequence extends LinkedList<CompilationPhase> {

        CompilationSequence(final CompilationPhase... phases) {
            super(Arrays.asList(phases));
        }

        CompilationSequence(final CompilationSequence sequence) {
            this(sequence.toArray(new CompilationPhase[sequence.size()]));
        }

        CompilationSequence insertAfter(final CompilationPhase phase, final CompilationPhase newPhase) {
            final CompilationSequence newSeq = new CompilationSequence();
            for (final CompilationPhase elem : this) {
                newSeq.add(phase);
                if (elem.equals(phase)) {
                    newSeq.add(newPhase);
                }
            }
            assert newSeq.contains(newPhase);
            return newSeq;
        }

        CompilationSequence insertBefore(final CompilationPhase phase, final CompilationPhase newPhase) {
            final CompilationSequence newSeq = new CompilationSequence();
            for (final CompilationPhase elem : this) {
                if (elem.equals(phase)) {
                    newSeq.add(newPhase);
                }
                newSeq.add(phase);
            }
            assert newSeq.contains(newPhase);
            return newSeq;
        }

        CompilationSequence insertFirst(final CompilationPhase phase) {
            final CompilationSequence newSeq = new CompilationSequence(this);
            newSeq.addFirst(phase);
            return newSeq;
        }

        CompilationSequence insertLast(final CompilationPhase phase) {
            final CompilationSequence newSeq = new CompilationSequence(this);
            newSeq.addLast(phase);
            return newSeq;
        }
    }

    /**
     * Standard (non-lazy) compilation, that basically will take an entire script
     * and JIT it at once. This can lead to long startup time and fewer type
     * specializations
     */
    final static CompilationSequence SEQUENCE_NORMAL = new CompilationSequence(
        CompilationPhase.CONSTANT_FOLDING_PHASE,
        CompilationPhase.LOWERING_PHASE,
        CompilationPhase.ATTRIBUTION_PHASE,
        CompilationPhase.SPLITTING_PHASE,
        CompilationPhase.TYPE_FINALIZATION_PHASE,
        CompilationPhase.BYTECODE_GENERATION_PHASE);

    final static CompilationSequence SEQUENCE_LAZY =
        SEQUENCE_NORMAL.insertFirst(CompilationPhase.LAZY_INITIALIZATION_PHASE);

    final static CompilationSequence SEQUENCE_DEFAULT =
        LAZY_JIT ?
            SEQUENCE_LAZY :
            SEQUENCE_NORMAL;

    private static String lazyTag(final FunctionNode functionNode) {
        if (functionNode.isLazy()) {
            return '$' + LAZY.tag() + '$' + functionNode.getName();
        }
        return "";
    }

    /**
     * Constructor
     *
     * @param installer    code installer from
     * @param functionNode function node (in any available {@link CompilationState}) to compile
     * @param sequence     {@link Compiler#CompilationSequence} of {@link CompilationPhase}s to apply as this compilation
     * @param strict       should this compilation use strict mode semantics
     */
    //TODO support an array of FunctionNodes for batch lazy compilation
    Compiler(final Context context, final CodeInstaller<Context> installer, final FunctionNode functionNode, final CompilationSequence sequence, final boolean strict) {
        this.context       = context;
        this.functionNode  = functionNode;
        this.sequence      = sequence;
        this.installer     = installer;
        this.strict        = strict || functionNode.isStrictMode();
        this.constantData  = new ConstantData();
        this.compileUnits  = new HashSet<>();
        this.bytecode      = new HashMap<>();

        final StringBuilder sb = new StringBuilder();
        sb.append(functionNode.uniqueName(DEFAULT_SCRIPT_NAME.tag() + lazyTag(functionNode))).
                append('$').
                append(safeSourceName(functionNode.getSource()));

        this.scriptName = sb.toString();

        LOG.info("Initializing compiler for '" + functionNode.getName() + "' scriptName = " + scriptName + ", root function: '" + functionNode.getName() + "'");
        if (functionNode.isLazy()) {
            LOG.info(">>> This is a lazy recompilation triggered by a trampoline");
        }
    }

    /**
     * Constructor
     *
     * @param installer    code installer from context
     * @param functionNode function node (in any available {@link CompilationState}) to compile
     * @param strict       should this compilation use strict mode semantics
     */
    public Compiler(final CodeInstaller<Context> installer, final FunctionNode functionNode, final boolean strict) {
        this(installer.getOwner(), installer, functionNode, SEQUENCE_DEFAULT, strict);
    }

    /**
     * Constructor - compilation will use the same strict semantics as context
     *
     * @param installer    code installer from context
     * @param functionNode function node (in any available {@link CompilationState}) to compile
     */
    public Compiler(final CodeInstaller<Context> installer, final FunctionNode functionNode) {
        this(installer.getOwner(), installer, functionNode, SEQUENCE_DEFAULT, installer.getOwner()._strict);
    }

    /**
     * Constructor - compilation needs no installer, but uses a context
     * Used in "compile only" scenarios
     * @param context a context
     * @param functionNode functionNode to compile
     */
    public Compiler(final Context context, final FunctionNode functionNode) {
        this(context, null, functionNode, SEQUENCE_DEFAULT, context._strict);
    }

    /**
     * Execute the compilation this Compiler was created with
     * @throws CompilationException if something goes wrong
     */
    public void compile() throws CompilationException {
        for (final String reservedName : RESERVED_NAMES) {
            functionNode.uniqueName(reservedName);
        }

        for (final CompilationPhase phase : sequence) {
            phase.apply(this, functionNode);
            final String end = phase.toString() + " done for function '" + functionNode.getName() + "'";
            if (Timing.isEnabled()) {
                final long duration = phase.getEndTime() - phase.getStartTime();
                LOG.info(end + " in " + duration + " ms");
            } else {
                LOG.info(end);
            }
        }
    }

    /**
     * Install compiled classes into a given loader
     * @return root script class - if there are several compile units they will also be installed
     */
    public Class<?> install() {
        final long t0 = Timing.isEnabled() ? System.currentTimeMillis() : 0L;

        assert functionNode.hasState(CompilationState.EMITTED) : functionNode.getName() + " has no bytecode and cannot be installed";

        Class<?> rootClass = null;

        for (final Entry<String, byte[]> entry : bytecode.entrySet()) {
            final String     className = entry.getKey();
            LOG.fine("Installing class " + className);

            final byte[]     code  = entry.getValue();
            final Class<?>   clazz = installer.install(Compiler.binaryName(className), code);

            if (rootClass == null && firstCompileUnitName().equals(className)) {
                rootClass = clazz;
            }

            try {
                final Source source = getSource();
                final Object[] constants = getConstantData().toArray();
                // Need doPrivileged because these fields are private
                AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                    @Override
                    public Void run() throws Exception {
                        //use reflection to write source and constants table to installed classes
                        final Field sourceField = clazz.getDeclaredField(SOURCE.tag());
                        final Field constantsField = clazz.getDeclaredField(CONSTANTS.tag());
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
        }

        LOG.info("Installed root class: " + rootClass + " and " + bytecode.size() + " compile unit classes");
        if (Timing.isEnabled()) {
            final long duration = System.currentTimeMillis() - t0;
            Timing.accumulateTime("[Code Installation]", duration);
            LOG.info("Installation time: " + duration + " ms");
        }

        return rootClass;
    }

    Set<CompileUnit> getCompileUnits() {
        return compileUnits;
    }

    boolean getStrictMode() {
        return strict;
    }

    void setStrictMode(final boolean strict) {
        this.strict = strict;
    }

    FunctionNode getFunctionNode() {
        return functionNode;
    }

    ConstantData getConstantData() {
        return constantData;
    }

    CodeInstaller<Context> getCodeInstaller() {
        return installer;
    }

    Source getSource() {
        return functionNode.getSource();
    }

    void addClass(final String name, final byte[] code) {
        bytecode.put(name, code);
    }

    Context getContext() {
        return this.context;
    }

    private static String safeSourceName(final Source source) {
        String baseName = new File(source.getName()).getName();

        final int index = baseName.lastIndexOf(".js");
        if (index != -1) {
            baseName = baseName.substring(0, index);
        }

        baseName = baseName.replace('.', '_').replace('-', '_');
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
        LOG.fine("Added compile unit " + compileUnit);
        return compileUnit;
    }

    private CompileUnit initCompileUnit(final String unitClassName, final long initialWeight) {
        final ClassEmitter classEmitter = new ClassEmitter(context, functionNode.getSource().getName(), unitClassName, strict);
        final CompileUnit  compileUnit  = new CompileUnit(unitClassName, classEmitter, initialWeight);

        classEmitter.begin();

        final MethodEmitter initMethod = classEmitter.init(EnumSet.of(Flag.PRIVATE));
        initMethod.begin();
        initMethod.load(Type.OBJECT, 0);
        initMethod.newInstance(jdk.nashorn.internal.scripts.JS$.class);
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

    /**
     * Should we use integers for arithmetic operations as well?
     * TODO: We currently generate no overflow checks so this is
     * disabled
     *
     * @see #shouldUseIntegers()
     *
     * @return true if arithmetic operations should not widen integer
     *   operands by default.
     */
    static boolean shouldUseIntegerArithmetic() {
        return USE_INT_ARITH;
    }

    private static final boolean USE_INT_ARITH;

    static {
        USE_INT_ARITH  =  Options.getBooleanProperty("nashorn.compiler.intarithmetic");
        assert !USE_INT_ARITH : "Integer arithmetic is not enabled";
    }

}
