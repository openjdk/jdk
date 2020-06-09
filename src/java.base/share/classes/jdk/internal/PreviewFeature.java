/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal;

import java.lang.annotation.*;

/**
 * Indicates the API declaration in question is associated with a
 * <em>preview feature</em>. See JEP 12: "Preview Language and VM
 * Features" (http://openjdk.java.net/jeps/12).
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

    public boolean essentialAPI() default false;

    public enum Feature {
        PATTERN_MATCHING_IN_INSTANCEOF,
        // 8242284:
        // The TEXT_BLOCKS enum constant is not used in the JDK 15 codebase, but
        // exists to support the bootcycle build of JDK 15. The bootcycle build
        // of JDK 15 is performed with JDK 14 and the PreviewFeature type from
        // JDK 15. Since the JDK 14 codebase uses the enum constant, it is
        // necessary for PreviewFeature in JDK 15 to declare the enum constant.
        TEXT_BLOCKS,
        RECORDS,
        SEALED_CLASSES,
        ;
    }
}
