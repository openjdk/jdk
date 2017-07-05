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

package jdk.nashorn.internal.ir;

import jdk.nashorn.internal.codegen.types.Type;

/**
 * A type override makes it possible to change the return type of a node, if we know
 * that the linker can provide it directly. For example, an identity node that is
 * in the scope, can very well look like an object to the compiler of the method it
 * is in, but if someone does (int)x, it make senses to ask for it directly
 * with an int getter instead of loading it as an object and explicitly converting it
 * by using JSType.toInt32. Especially in scenarios where the field is already stored
 * as a primitive, this will be much faster than the "object is all I see" scope
 * available in the method
 * @param <T> the type of the node implementing the interface
 */

public interface TypeOverride<T extends Node> {
    /**
     * Set the override type
     *
     * @param ts temporary symbols
     * @param lc the current lexical context
     * @param type the type
     * @return a node equivalent to this one except for the requested change.
     */
    public T setType(final TemporarySymbols ts, final LexicalContext lc, final Type type);

    /**
     * Returns true if this node can have a callsite override, e.g. all scope ident nodes
     * which lead to dynamic getters can have it, local variable nodes (slots) can't.
     * Call nodes can have it unconditionally and so on
     *
     * @return true if it is possible to assign a type override to this node
     */
    public boolean canHaveCallSiteType();

}
