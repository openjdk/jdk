/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.source.util;

import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;

/**
 * Provides details about work that has been done by the JDK Java Compiler, javac.
 *
 * @author Jonathan Gibbons
 * @since 1.6
 */
@jdk.Exported
public final class TaskEvent
{
    /**
     * Kind of task event.
     * @since 1.6
     */
    @jdk.Exported
    public enum Kind {
        /**
         * For events related to the parsing of a file.
         */
        PARSE,
        /**
         * For events relating to elements being entered.
         **/
        ENTER,
        /**
         * For events relating to elements being analyzed for errors.
         **/
        ANALYZE,
        /**
         * For events relating to class files being generated.
         **/
        GENERATE,
        /**
         * For events relating to overall annotation processing.
         **/
        ANNOTATION_PROCESSING,
        /**
         * For events relating to an individual annotation processing round.
         **/
        ANNOTATION_PROCESSING_ROUND
    }

    public TaskEvent(Kind kind) {
        this(kind, null, null, null);
    }

    public TaskEvent(Kind kind, JavaFileObject sourceFile) {
        this(kind, sourceFile, null, null);
    }

    public TaskEvent(Kind kind, CompilationUnitTree unit) {
        this(kind, unit.getSourceFile(), unit, null);
    }

    public TaskEvent(Kind kind, CompilationUnitTree unit, TypeElement clazz) {
        this(kind, unit.getSourceFile(), unit, clazz);
    }

    private TaskEvent(Kind kind, JavaFileObject file, CompilationUnitTree unit, TypeElement clazz) {
        this.kind = kind;
        this.file = file;
        this.unit = unit;
        this.clazz = clazz;
    }

    public Kind getKind() {
        return kind;
    }

    public JavaFileObject getSourceFile() {
        return file;
    }

    public CompilationUnitTree getCompilationUnit() {
        return unit;
    }

    public TypeElement getTypeElement() {
        return clazz;
    }

    public String toString() {
        return "TaskEvent["
            + kind + ","
            + file + ","
            // the compilation unit is identified by the file
            + clazz + "]";
    }

    private Kind kind;
    private JavaFileObject file;
    private CompilationUnitTree unit;
    private TypeElement clazz;
}
