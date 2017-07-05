/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime;

import javax.xml.namespace.NamespaceContext;

import com.sun.istack.internal.NotNull;

/**
 * Maintains {@code namespace <-> prefix} bindings.
 *
 * <p>
 * This interface extends {@link NamespaceContext} and provides
 * an additional functionality, which is necessary to declare
 * namespaced attributes on elements. The added method is for
 * self-consumption by the marshaller.
 *
 * This object is composed into a Serializer.
 */
public interface NamespaceContext2 extends NamespaceContext
{
    /**
     * Declares a new namespace binding within the current context.
     *
     * <p>
     * The prefix is automatically assigned by MarshallingContext. If
     * a given namespace URI is already declared, nothing happens.
     *
     * <p>
     * It is <b>NOT</b> an error to declare the same namespace URI
     * more than once.
     *
     * <p>
     * For marshalling to work correctly, all namespace bindings
     * for an element must be declared between its startElement method and
     * its endAttributes event. Calling the same method with the same
     * parameter between the endAttributes and the endElement returns
     * the same prefix.
     *
     * @param   requirePrefix
     *      If this parameter is true, this method must assign a prefix
     *      to this namespace, even if it's already bound to the default
     *      namespace. IOW, this method will never return null if this
     *      flag is true. This functionality is necessary to declare
     *      namespace URI used for attribute names.
     * @param   preferedPrefix
     *      If the caller has any particular preference to the
     *      prefix, pass that as a parameter. The callee will try
     *      to honor it. Set null if there's no particular preference.
     *
     * @return
     *      returns the assigned prefix. If the namespace is bound to
     *      the default namespace, null is returned.
     */
    String declareNamespace( String namespaceUri, String preferedPrefix, boolean requirePrefix );

    /**
     * Forcibly make a namespace declaration in effect.
     *
     * If the (prefix,uri) binding is already in-scope, this method
     * simply returns the assigned prefix index. Otherwise a new
     * declaration will be put.
     */
    int force(@NotNull String uri, @NotNull String prefix);
}
