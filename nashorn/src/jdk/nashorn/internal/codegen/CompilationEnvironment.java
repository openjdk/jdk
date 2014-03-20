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

import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;

/**
 * Class for managing metadata during a compilation, e.g. which phases
 * should be run, should we use optimistic types, is this a lazy compilation
 * and various parameter types known to the runtime system
 */
public final class CompilationEnvironment {
    private final CompilationPhases phases;
    private final boolean optimistic;

    private final ParamTypeMap paramTypes;

    private RecompilableScriptFunctionData compiledFunction;

    private boolean strict;

    private final boolean onDemand;

    /**
     * If this is a recompilation, this is how we pass in the invalidations, e.g. programPoint=17, Type == int means
     * that using whatever was at program point 17 as an int failed.
     */
    private final Map<Integer, Type> invalidatedProgramPoints;

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
     * Compilation phases that a compilation goes through
     */
    public static final class CompilationPhases implements Iterable<CompilationPhase> {

        /**
         * Standard (non-lazy) compilation, that basically will take an entire script
         * and JIT it at once. This can lead to long startup time and fewer type
         * specializations
         */
        final static CompilationPhase[] SEQUENCE_EAGER_ARRAY = new CompilationPhase[] {
            CompilationPhase.CONSTANT_FOLDING_PHASE,
            CompilationPhase.LOWERING_PHASE,
            CompilationPhase.SPLITTING_PHASE,
            CompilationPhase.ATTRIBUTION_PHASE,
            CompilationPhase.RANGE_ANALYSIS_PHASE,
            CompilationPhase.TYPE_FINALIZATION_PHASE,
            CompilationPhase.SCOPE_DEPTH_COMPUTATION_PHASE,
            CompilationPhase.BYTECODE_GENERATION_PHASE
        };

        private final static List<CompilationPhase> SEQUENCE_EAGER;
        static {
            final LinkedList<CompilationPhase> eager = new LinkedList<>();
            for (final CompilationPhase phase : SEQUENCE_EAGER_ARRAY) {
                eager.add(phase);
            }
            SEQUENCE_EAGER = Collections.unmodifiableList(eager);
        }

        /** Singleton that describes a standard eager compilation */
        public static CompilationPhases EAGER = new CompilationPhases(SEQUENCE_EAGER);

        private final List<CompilationPhase> phases;

        private CompilationPhases(final List<CompilationPhase> phases) {
            this.phases = phases;
        }

        @SuppressWarnings("unused")
        private CompilationPhases addFirst(final CompilationPhase phase) {
            if (phases.contains(phase)) {
                return this;
            }
            final LinkedList<CompilationPhase> list = new LinkedList<>(phases);
            list.addFirst(phase);
            return new CompilationPhases(Collections.unmodifiableList(list));
        }

        private CompilationPhases addAfter(final CompilationPhase phase, final CompilationPhase newPhase) {
            final LinkedList<CompilationPhase> list = new LinkedList<>();
            for (final CompilationPhase p : phases) {
                list.add(p);
                if (p == phase) {
                    list.add(newPhase);
                }
            }
            return new CompilationPhases(Collections.unmodifiableList(list));
        }

        /**
         * Turn a CompilationPhases into an optimistic one. NOP if already optimistic
         * @param isOptimistic should this be optimistic
         * @return new CompilationPhases that is optimistic or same if already optimistic
         */
        public CompilationPhases makeOptimistic(final boolean isOptimistic) {
            return isOptimistic ? addAfter(CompilationPhase.LOWERING_PHASE, CompilationPhase.PROGRAM_POINT_PHASE) : this;
        }

        /**
         * Turn a CompilationPhases into an optimistic one. NOP if already optimistic
         * @return new CompilationPhases that is optimistic or same if already optimistic
         */
        public CompilationPhases makeOptimistic() {
            return makeOptimistic(true);
        }

        private boolean contains(final CompilationPhase phase) {
            return phases.contains(phase);
        }

        @Override
        public Iterator<CompilationPhase> iterator() {
            return phases.iterator();
        }

    }

    /**
     * Constructor
     * @param phases compilation phases
     * @param strict strict mode
     */
    public CompilationEnvironment(
        final CompilationPhases phases,
        final boolean strict) {
        this(phases, null, null, null, null, strict, false);
    }

    /**
     * Constructor for compilation environment of the rest-of method
     * @param phases compilation phases
     * @param strict strict mode
     * @param compiledFunction recompiled function
     * @param paramTypeMap known parameter types if any exist
     * @param invalidatedProgramPoints map of invalidated program points to their type
     * @param continuationEntryPoint program points used as the continuation entry points in the current rest-of sequence
     * @param onDemand is this an on demand compilation
     */
    public CompilationEnvironment(
        final CompilationPhases phases,
        final boolean strict,
        final RecompilableScriptFunctionData compiledFunction,
        final ParamTypeMap paramTypeMap,
        final Map<Integer, Type> invalidatedProgramPoints,
        final int[] continuationEntryPoint,
        final boolean onDemand) {
            this(phases, paramTypeMap, invalidatedProgramPoints, compiledFunction, continuationEntryPoint, strict, onDemand);
    }

    /**
     * Constructor
     * @param phases compilation phases
     * @param strict strict mode
     * @param compiledFunction recompiled function
     * @param paramTypeMap known parameter types
     * @param invalidatedProgramPoints map of invalidated program points to their type
     * @param onDemand is this an on demand compilation
     */
    public CompilationEnvironment(
        final CompilationPhases phases,
        final boolean strict,
        final RecompilableScriptFunctionData compiledFunction,
        final ParamTypeMap paramTypeMap,
        final Map<Integer, Type> invalidatedProgramPoints,
        final boolean onDemand) {
        this(phases, paramTypeMap, invalidatedProgramPoints, compiledFunction, null, strict, onDemand);
    }

    private CompilationEnvironment(
            final CompilationPhases phases,
            final ParamTypeMap paramTypes,
            final Map<Integer, Type> invalidatedProgramPoints,
            final RecompilableScriptFunctionData compiledFunction,
            final int[] continuationEntryPoints,
            final boolean strict,
            final boolean onDemand) {
        this.phases                   = phases;
        this.paramTypes               = paramTypes;
        this.continuationEntryPoints  = continuationEntryPoints;
        this.invalidatedProgramPoints =
            invalidatedProgramPoints == null ?
                Collections.unmodifiableMap(new HashMap<Integer, Type>()) :
                invalidatedProgramPoints;
        this.compiledFunction         = compiledFunction;
        this.strict                   = strict;
        this.optimistic               = phases.contains(CompilationPhase.PROGRAM_POINT_PHASE);
        this.onDemand                 = onDemand;

        // If entry point array is passed, it must have at least one element
        assert continuationEntryPoints == null || continuationEntryPoints.length > 0;
        assert !isCompileRestOf() || isOnDemandCompilation(); // isCompileRestOf => isRecompilation
        // continuation entry points must be among the invalidated program points
        assert !isCompileRestOf() || invalidatedProgramPoints != null && containsAll(invalidatedProgramPoints.keySet(), continuationEntryPoints);
    }

    private static boolean containsAll(final Set<Integer> set, final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            if (!set.contains(array[i])) {
                return false;
            }
        }
        return true;
    }

    void setData(final RecompilableScriptFunctionData data) {
        assert this.compiledFunction == null : data;
        this.compiledFunction = data;
    }

    boolean isStrict() {
        return strict;
    }

    void setIsStrict(final boolean strict) {
        this.strict = strict;
    }

    CompilationPhases getPhases() {
        return phases;
    }

    /**
     * Check if a program point was invalidated during a previous run
     * of this method, i.e. we did an optimistic assumption that now is wrong.
     * This is the basis of generating a wider type. getOptimisticType
     * in this class will query for invalidation and suggest a wider type
     * upon recompilation if this info exists.
     * @param programPoint program point to check
     * @return true if it was invalidated during a previous run
     */
    boolean isInvalidated(final int programPoint) {
        return invalidatedProgramPoints.get(programPoint) != null;
    }

    /**
     * Get the parameter type at a parameter position if known from previous runtime calls
     * or optimistic profiles.
     *
     * @param functionNode function node to query
     * @param pos parameter position
     * @return known type of parameter 'pos' or null if no information exists
     */
    Type getParamType(final FunctionNode functionNode, final int pos) {
        return paramTypes == null ? null : paramTypes.get(functionNode, pos);
    }

    /**
     * Is this a compilation that generates the rest of method
     * @return true if rest of generation
     */
    boolean isCompileRestOf() {
        return continuationEntryPoints != null;
    }

    /**
     * Is this an on-demand compilation triggered by a {@code RecompilableScriptFunctionData} - either a type
     * specializing compilation, a deoptimizing recompilation, or a rest-of method compilation.
     * @return true if this is an on-demand compilation, false if this is an eager compilation.
     */
    boolean isOnDemandCompilation() {
        return onDemand; //data != null;
    }

    /**
     * Is this program point one of the continuation entry points for the rest-of method being compiled?
     * @param programPoint program point
     * @return true if it is a continuation entry point
     */
    boolean isContinuationEntryPoint(final int programPoint) {
        if (continuationEntryPoints != null) {
            for (final int continuationEntryPoint : continuationEntryPoints) {
                if (continuationEntryPoint == programPoint) {
                    return true;
                }
            }
        }
        return false;
    }

    int[] getContinuationEntryPoints() {
        return continuationEntryPoints;
    }

    /**
     * Is this program point the continuation entry points for the current rest-of method being compiled?
     * @param programPoint program point
     * @return true if it is the current continuation entry point
     */
    boolean isCurrentContinuationEntryPoint(final int programPoint) {
        return hasCurrentContinuationEntryPoint() && getCurrentContinuationEntryPoint() == programPoint;
    }

    boolean hasCurrentContinuationEntryPoint() {
        return continuationEntryPoints != null;
    }

    int getCurrentContinuationEntryPoint() {
        // NOTE: we assert in the constructor that if the array is non-null, it has at least one element
        return hasCurrentContinuationEntryPoint() ? continuationEntryPoints[0] : INVALID_PROGRAM_POINT;
    }

    /**
     * Are optimistic types enabled ?
     * @param node get the optimistic type for a node
     * @return most optimistic type in current environment
     */
    Type getOptimisticType(final Optimistic node) {
        assert useOptimisticTypes();
        final Type invalidType = invalidatedProgramPoints.get(node.getProgramPoint());
        if (invalidType != null) {
            return invalidType;//.nextWider();
        }
        return node.getMostOptimisticType();
    }

    /**
     * Should this compilation use optimistic types in general.
     * If this is false we will only set non-object types to things that can
     * be statically proven to be true.
     * @return true if optimistic types should be used.
     */
    boolean useOptimisticTypes() {
        return optimistic;
    }

    RecompilableScriptFunctionData getScriptFunctionData(final int functionId) {
        return compiledFunction == null ? null : compiledFunction.getScriptFunctionData(functionId);
    }

}
