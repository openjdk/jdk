/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.staxex;

import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;

/**
 * Extended {@link NamespaceContext}.
 *
 * @author Kohsuke Kawaguchi
 * @author Paul Sandoz
 */
public interface NamespaceContextEx extends NamespaceContext, Iterable<NamespaceContextEx.Binding> {

    /**
     * Iterates all the in-scope namespace bindings.
     *
     * <p>
     * This method enumerates all the active in-scope namespace bindings.
     * This does not include implicit bindings, such as
     * {@code "xml"->"http://www.w3.org/XML/1998/namespace"}
     * or {@code ""->""} (the implicit default namespace URI.)
     *
     * <p>
     * The returned iterator may not include the same prefix more than once.
     * For example, the returned iterator may only contain {@code f=ns2}
     * if the document is as follows and this method is used at the bar element.
     *
     * <pre>{@code
     * <foo xmlns:f='ns1'>
     *   <bar xmlns:f='ns2'>
     *     ...
     * }</pre>
     *
     * <p>
     * The iteration may be done in no particular order.
     *
     * @return
     *      may return an empty iterator, but never null.
     */
    Iterator<Binding> iterator();

    /**
     * Prefix to namespace URI binding.
     */
    interface Binding {
        /**
         * Gets the prefix.
         *
         * <p>
         * The default namespace URI is represented by using an
         * empty string "", not null.
         *
         * @return
         *      never null. String like "foo", "ns12", or "".
         */
        String getPrefix();

        /**
         * Gets the namespace URI.
         *
         * <p>
         * The empty namespace URI is represented by using
         * an empty string "", not null.
         *
         * @return
         *      never null. String like "http://www.w3.org/XML/1998/namespace",
         *      "urn:oasis:names:specification:docbook:dtd:xml:4.1.2", or "".
         */
        String getNamespaceURI();
    }
}
