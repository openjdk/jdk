/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api;

import java.util.Collection;
import java.util.Map;

import javax.xml.namespace.QName;

import javax.annotation.processing.ProcessingEnvironment;


/**
 * Java-to-Schema compiler.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface JavaCompiler {

    /**
     * Compiles the given annotated Java source code.
     *
     * <p>
     * This operation takes a set of "root types", then compute the list of
     * all the types that need to be bound by forming a transitive reflexive
     * closure of types that are referenced by the root types.
     *
     * <p>
     * Errors will be sent to {@link javax.annotation.processing.ProcessingEnvironment#getMessager()}.
     *
     * @param rootTypes
     *      The list of types that needs to be bound to XML.
     *      "root references" from JAX-RPC to JAXB is always in the form of (type,annotations) pair.
     *
     * @param additionalElementDecls
     *      Add element declarations for the specified element names to
     *      the XML types mapped from the corresponding {@link Reference}s.
     *      Those {@link Reference}s must be included in the <tt>rootTypes</tt> parameter.
     *      In this map, a {@link Reference} can be null, in which case the element name is
     *      declared to have an empty complex type.
     *      (&lt;xs:element name='foo'>&lt;xs:complexType/>&lt;/xs:element>)
     *      This parameter can be null, in which case the method behaves as if the empty map is given.
     *
     * @param defaultNamespaceRemap
     *      If not-null, all the uses of the empty default namespace ("") will
     *      be replaced by this namespace URI.
     *
     * @param source
     *      The caller supplied view to the annotated source code that JAXB is going to process.
     *
     * @return
     *      Non-null if no error was reported. Otherwise null.
     */
    J2SJAXBModel bind(
            Collection<Reference> rootTypes,
            Map<QName, Reference> additionalElementDecls,
            String defaultNamespaceRemap,
            ProcessingEnvironment source);
}
