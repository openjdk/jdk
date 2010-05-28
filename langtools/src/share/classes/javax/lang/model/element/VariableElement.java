/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.element;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;


/**
 * Represents a field, {@code enum} constant, method or constructor
 * parameter, local variable, or exception parameter.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */

public interface VariableElement extends Element {

    /**
     * Returns the value of this variable if this is a {@code final}
     * field initialized to a compile-time constant.  Returns {@code
     * null} otherwise.  The value will be of a primitive type or a
     * {@code String}.  If the value is of a primitive type, it is
     * wrapped in the appropriate wrapper class (such as {@link
     * Integer}).
     *
     * <p>Note that not all {@code final} fields will have
     * constant values.  In particular, {@code enum} constants are
     * <em>not</em> considered to be compile-time constants.  To have a
     * constant value, a field's type must be either a primitive type
     * or {@code String}.
     *
     * @return the value of this variable if this is a {@code final}
     * field initialized to a compile-time constant, or {@code null}
     * otherwise
     *
     * @see Elements#getConstantExpression(Object)
     * @jls3 15.28 Constant Expression
     * @jls3 4.12.4 final Variables
     */
    Object getConstantValue();
}
