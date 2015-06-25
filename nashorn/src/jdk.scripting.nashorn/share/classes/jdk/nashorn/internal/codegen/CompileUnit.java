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

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import jdk.nashorn.internal.ir.CompileUnitHolder;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;

/**
  * Used to track split class compilation. Note that instances of the class are serializable, but all fields are
  * transient, making the serialized version of the class only useful for tracking the referential topology of other
  * AST nodes referencing the same or different compile units. We do want to preserve this topology though as
  * {@link CompileUnitHolder}s in a deserialized AST will undergo reinitialization.
  */
public final class CompileUnit implements Comparable<CompileUnit>, Serializable {
    private static final long serialVersionUID = 1L;

    /** Current class name */
    private transient final String className;

    /** Current class generator */
    private transient ClassEmitter classEmitter;

    private transient long weight;

    private transient Class<?> clazz;

    private transient Map<FunctionNode, RecompilableScriptFunctionData> functions = new IdentityHashMap<>();

    private transient boolean isUsed;

    private static int emittedUnitCount;

    CompileUnit(final String className, final ClassEmitter classEmitter, final long initialWeight) {
        this.className    = className;
        this.weight       = initialWeight;
        this.classEmitter = classEmitter;
    }

    static Set<CompileUnit> createCompileUnitSet() {
        return new TreeSet<>();
    }

    static void increaseEmitCount() {
        emittedUnitCount++;
    }

    /**
     * Get the amount of emitted compile units so far in the system
     * @return emitted compile unit count
     */
    public static int getEmittedUnitCount() {
        return emittedUnitCount;
    }

    /**
     * Check if this compile unit is used
     * @return true if tagged as in use - i.e active code that needs to be generated
     */
    public boolean isUsed() {
        return isUsed;
    }

    /**
     * Check if a compile unit has code, not counting inits and clinits
     * @return true of if there is "real code" in the compile unit
     */
    public boolean hasCode() {
        return (classEmitter.getMethodCount() - classEmitter.getInitCount() - classEmitter.getClinitCount()) > 0;
    }

    /**
     * Tag this compile unit as used
     */
    public void setUsed() {
        this.isUsed = true;
    }

    /**
     * Return the class that contains the code for this unit, null if not
     * generated yet
     *
     * @return class with compile unit code
     */
    public Class<?> getCode() {
        return clazz;
    }

    /**
     * Set class when it exists. Only accessible from compiler
     * @param clazz class with code for this compile unit
     */
    void setCode(final Class<?> clazz) {
        this.clazz = Objects.requireNonNull(clazz);
        // Revisit this - refactor to avoid null-ed out non-final fields
        // null out emitter
        this.classEmitter = null;
    }

    void addFunctionInitializer(final RecompilableScriptFunctionData data, final FunctionNode functionNode) {
        functions.put(functionNode, data);
    }

    /**
     * Returns true if this compile unit is responsible for initializing the specified function data with specified
     * function node.
     * @param data the function data to check
     * @param functionNode the function node to check
     * @return true if this unit is responsible for initializing the function data with the function node, otherwise
     * false
     */
    public boolean isInitializing(final RecompilableScriptFunctionData data, final FunctionNode functionNode) {
        return functions.get(functionNode) == data;
    }

    void initializeFunctionsCode() {
        for(final Map.Entry<FunctionNode, RecompilableScriptFunctionData> entry : functions.entrySet()) {
            entry.getValue().initializeCode(entry.getKey());
        }
    }

    Collection<FunctionNode> getFunctionNodes() {
        return Collections.unmodifiableCollection(functions.keySet());
    }

    /**
     * Add weight to this compile unit
     * @param w weight to add
     */
    void addWeight(final long w) {
        this.weight += w;
    }

    /**
     * Check if this compile unit can hold {@code weight} more units of weight
     * @param w weight to check if can be added
     * @return true if weight fits in this compile unit
     */
    public boolean canHold(final long w) {
        return (this.weight + w) < Splitter.SPLIT_THRESHOLD;
    }

    /**
     * Get the class emitter for this compile unit
     * @return class emitter
     */
    public ClassEmitter getClassEmitter() {
        return classEmitter;
    }

    /**
     * Get the class name for this compile unit
     * @return the class name
     */
    public String getUnitClassName() {
        return className;
    }

    private static String shortName(final String name) {
        return name == null ? null : name.lastIndexOf('/') == -1 ? name : name.substring(name.lastIndexOf('/') + 1);
    }

    @Override
    public String toString() {
        final String methods = classEmitter != null ? classEmitter.getMethodNames().toString() : "<anon>";
        return "[CompileUnit className=" + shortName(className) + " weight=" + weight + '/' + Splitter.SPLIT_THRESHOLD + " hasCode=" + methods + ']';
    }

    @Override
    public int compareTo(final CompileUnit o) {
        return className.compareTo(o.className);
    }
}
