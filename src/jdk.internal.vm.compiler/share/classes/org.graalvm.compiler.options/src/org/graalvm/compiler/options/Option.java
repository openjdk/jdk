/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.options;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes the attributes of an option whose {@link OptionKey value} is in a static field
 * annotated by this annotation type.
 *
 * @see OptionDescriptor
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Option {

    /**
     * Gets a help message for the option.
     * <p>
     * The first element of the array is the short help message. This part of the help message is
     * subject to line wrapping when printed.
     * <p>
     * The remaining elements contain a more detailed expansion of the help message and will be
     * printed as is in a left-aligned block (i.e. leading spaces will be preserved).
     * <p>
     * If there is only one element and it starts with {@code "file:"<path>}, then the help message
     * is located in a file located by resolving {@code <path>} against the location of the package
     * in which the option is declared. The first line in the file is the short help message as
     * described above. The remaining lines are the help message expansion.
     */
    String[] help();

    /**
     * The name of the option. By default, the name of the annotated field should be used.
     */
    String name() default "";

    /**
     * Specifies the type of the option.
     */
    OptionType type() default OptionType.Debug;
}
