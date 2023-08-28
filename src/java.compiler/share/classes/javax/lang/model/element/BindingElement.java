/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Represents a binding.
 *
 * @since 22
 */
// TODO: Add JLS citation
public interface BindingElement extends Element {
    /**
     * {@return the enclosing element of this binding}
     *
     * The enclosing element of a binding is the matcher declaring the
     * binding.
     */
    @Override
    Element getEnclosingElement();

    /**
     * {@return the simple name of this binding}
     *
     * TODO: define this value
     */
    @Override
    Name getSimpleName();

    /**
     * {@return the type defined by this binding}
     *
     * TODO: define this value
     */
    @Override
    TypeMirror asType();
}
