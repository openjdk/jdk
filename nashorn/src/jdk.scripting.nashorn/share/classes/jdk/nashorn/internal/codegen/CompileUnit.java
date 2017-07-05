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

import java.util.Set;
import java.util.TreeSet;

/**
 * Used to track split class compilation.
 */
public final class CompileUnit implements Comparable<CompileUnit> {
    /** Current class name */
    private final String className;

    /** Current class generator */
    private ClassEmitter classEmitter;

    private long weight;

    private Class<?> clazz;

    CompileUnit(final String className, final ClassEmitter classEmitter, final long initialWeight) {
        this.className    = className;
        this.weight       = initialWeight;
        this.classEmitter = classEmitter;
    }

    static Set<CompileUnit> createCompileUnitSet() {
        return new TreeSet<>();
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
        clazz.getClass(); // null check
        this.clazz = clazz;
        // Revisit this - refactor to avoid null-ed out non-final fields
        // null out emitter
        this.classEmitter = null;
    }

    /**
     * Add weight to this compile unit
     * @param w weight to add
     */
    void addWeight(final long w) {
        this.weight += w;
    }

    /**
     * Get the current weight of the compile unit.
     * @return the unit's weight
     */
    long getWeight() {
        return weight;
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
        return name.lastIndexOf('/') == -1 ? name : name.substring(name.lastIndexOf('/') + 1);
    }

    @Override
    public String toString() {
        return "[CompileUnit className=" + shortName(className) + " weight=" + weight + '/' + Splitter.SPLIT_THRESHOLD + ']';
    }

    @Override
    public int compareTo(final CompileUnit o) {
        return className.compareTo(o.className);
    }
}
