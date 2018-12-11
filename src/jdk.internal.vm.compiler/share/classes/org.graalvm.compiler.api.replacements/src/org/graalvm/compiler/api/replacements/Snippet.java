/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.api.replacements;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A snippet is a Graal graph expressed as a Java source method. Snippets are used for lowering
 * nodes that have runtime dependent semantics (e.g. the {@code CHECKCAST} bytecode).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Snippet {

    /**
     * A partial intrinsic exits by (effectively) calling the intrinsified method. Normally, this
     * call must use exactly the same arguments as the call that is being intrinsified. For well
     * known snippets that are used after frame state assignment, we want to relax this restriction.
     */
    boolean allowPartialIntrinsicArgumentMismatch() default false;

    /**
     * Denotes a snippet parameter representing 0 or more arguments that will be bound during
     * snippet template instantiation. During snippet template creation, its value must be an array
     * whose length specifies the number of arguments (the contents of the array are ignored) bound
     * to the parameter during instantiation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface VarargsParameter {
    }

    /**
     * Denotes a snippet parameter that will bound to a constant value during snippet template
     * instantiation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ConstantParameter {
    }

    /**
     * Denotes a snippet parameter that will bound to a non-null value during snippet template
     * instantiation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface NonNullParameter {
    }
}
