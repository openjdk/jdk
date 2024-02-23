/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.javac;

import java.lang.annotation.*;

/**
 * Indicates the API declaration in question is associated with a
 * <em>preview feature</em>. See JEP 12: "Preview Language and VM
 * Features" (https://openjdk.org/jeps/12).
 *
 * Note this internal annotation is handled specially by the javac compiler.
 * To work properly with {@code --release older-release}, it requires special
 * handling in {@code make/langtools/src/classes/build/tools/symbolgenerator/CreateSymbols.java}
 * and {@code src/jdk.compiler/share/classes/com/sun/tools/javac/jvm/ClassReader.java}.
 *
 * @since 14
 */
// Match the meaningful targets of java.lang.Deprecated, omit local
// variables and parameter declarations
@Target({ElementType.METHOD,
         ElementType.CONSTRUCTOR,
         ElementType.FIELD,
         ElementType.PACKAGE,
         ElementType.MODULE,
         ElementType.TYPE})
 // CLASS retention will hopefully be sufficient for the purposes at hand
@Retention(RetentionPolicy.CLASS)
// *Not* @Documented
public @interface PreviewFeature {
    /**
     * Name of the preview feature the annotated API is associated
     * with.
     */
    public Feature feature();

    public boolean reflective() default false;

    /**
     * Enum of preview features in the current release.
     * Values should be annotated with the feature's {@code JEP}.
     */
    public enum Feature {
        // not used, but required for interim javac to not warn.
        VIRTUAL_THREADS,
        FOREIGN,
        @JEP(number=459, title="String Templates", status="Second Preview")
        STRING_TEMPLATES,
        @JEP(number=445, title="Unnamed Classes and Instance Main Methods", status="Deprecated")
        UNNAMED_CLASSES,
        @JEP(number=463, title="Implicitly Declared Classes and Instance Main Methods", status="Preview")
        IMPLICIT_CLASSES,
        @JEP(number=464, title="Scoped Values", status="Second Preview")
        SCOPED_VALUES,
        @JEP(number=462, title="Structured Concurrency", status="Second Preview")
        STRUCTURED_CONCURRENCY,
        @JEP(number=457, title="ClassFile API", status="Preview")
        CLASSFILE_API,
        @JEP(number=461, title="Stream Gatherers", status="Preview")
        STREAM_GATHERERS,
        /**
         * A key for testing.
         */
        @JEP(number=0, title="Test Feature")
        TEST,
        ;
    }

    /**
     * Annotation identifying the JEP associated with a preview feature.
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.CLASS)
    @interface JEP {
        /** JEP number */
        int number() default 0;
        /** JEP title in plain text */
        String title();
        /** JEP status such as "Preview", "Second Preview", etc */
        String status() default "Preview";
    }
}
