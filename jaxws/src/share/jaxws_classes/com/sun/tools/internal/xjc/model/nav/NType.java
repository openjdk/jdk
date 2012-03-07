/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model.nav;

import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;

/**
 * A type.
 *
 * See the package documentaion for details.
 *
 * @author Kohsuke Kawaguchi
 */
public interface NType {
    /**
     * Returns the representation of this type in code model.
     * <p>
     * This operation requires the whole model to be built,
     * and hence it takes {@link Outline}.
     * <p>
     * Under some code generation strategy, some bean classes
     * are considered implementation specific (such as impl.FooImpl class)
     * These classes always have accompanying "exposed" type (such as
     * the Foo interface).
     * <p>
     * For such Jekyll and Hyde type, the aspect parameter determines
     * which personality is returned.
     *
     * @param aspect
     *      If {@link Aspect#IMPLEMENTATION}, this method returns the
     *      implementation specific class that this type represents.
     *      If {@link Aspect#EXPOSED}, this method returns the
     *      publicly exposed type that this type represents.
     *
     *      For ordinary classes, the aspect parameter is meaningless.
     *
     */
    JType toType(Outline o, Aspect aspect);

    /**
     * Returns true iff this type represents a class that has a unboxed form.
     *
     * For example, for {@link String} this is false, but for {@link Integer}
     * this is true.
     */
    boolean isBoxedType();

    /**
     * Human readable name of this type.
     */
    String fullName();
}
