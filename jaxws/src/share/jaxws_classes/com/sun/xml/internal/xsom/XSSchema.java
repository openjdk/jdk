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

package com.sun.xml.internal.xsom;

import com.sun.xml.internal.xsom.parser.SchemaDocument;

import java.util.Iterator;
import java.util.Map;

/**
 * Schema.
 *
 * Container of declarations that belong to the same target namespace.
 *
 * @author
 *  Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface XSSchema extends XSComponent
{
    /**
     * Gets the target namespace of the schema.
     *
     * @return
     *      can be empty, but never be null.
     */
    String getTargetNamespace();

    /**
     * Gets all the {@link XSAttributeDecl}s in this schema
     * keyed by their local names.
     */
    Map<String,XSAttributeDecl> getAttributeDecls();
    Iterator<XSAttributeDecl> iterateAttributeDecls();
    XSAttributeDecl getAttributeDecl(String localName);

    /**
     * Gets all the {@link XSElementDecl}s in this schema.
     */
    Map<String,XSElementDecl> getElementDecls();
    Iterator<XSElementDecl> iterateElementDecls();
    XSElementDecl getElementDecl(String localName);

    /**
     * Gets all the {@link XSAttGroupDecl}s in this schema.
     */
    Map<String,XSAttGroupDecl> getAttGroupDecls();
    Iterator<XSAttGroupDecl> iterateAttGroupDecls();
    XSAttGroupDecl getAttGroupDecl(String localName);

    /**
     * Gets all the {@link XSModelGroupDecl}s in this schema.
     */
    Map<String,XSModelGroupDecl> getModelGroupDecls();
    Iterator<XSModelGroupDecl> iterateModelGroupDecls();
    XSModelGroupDecl getModelGroupDecl(String localName);

    /**
     * Gets all the {@link XSType}s in this schema (union of
     * {@link #getSimpleTypes()} and {@link #getComplexTypes()}
     */
    Map<String,XSType> getTypes();
    Iterator<XSType> iterateTypes();
    XSType getType(String localName);

    /**
     * Gets all the {@link XSSimpleType}s in this schema.
     */
    Map<String,XSSimpleType> getSimpleTypes();
    Iterator<XSSimpleType> iterateSimpleTypes();
    XSSimpleType getSimpleType(String localName);

    /**
     * Gets all the {@link XSComplexType}s in this schema.
     */
    Map<String,XSComplexType> getComplexTypes();
    Iterator<XSComplexType> iterateComplexTypes();
    XSComplexType getComplexType(String localName);

    /**
     * Gets all the {@link XSNotation}s in this schema.
     */
    Map<String,XSNotation> getNotations();
    Iterator<XSNotation> iterateNotations();
    XSNotation getNotation(String localName);

    /**
     * Gets all the {@link XSIdentityConstraint}s in this schema,
     * keyed by their names.
     */
    Map<String,XSIdentityConstraint> getIdentityConstraints();

    /**
     * Gets the identity constraint of the given name, or null if not found.
     */
    XSIdentityConstraint getIdentityConstraint(String localName);

    /**
     * Sine an {@link XSSchema} is not necessarily defined in
     * one schema document (for example one schema can span across
     * many documents through &lt;xs:include>s.),
     * so this method always returns null.
     *
     * @deprecated
     *      Since this method always returns null, if you are calling
     *      this method from {@link XSSchema} and not from {@link XSComponent},
     *      there's something wrong with your code.
     */
    SchemaDocument getSourceDocument();

    /**
     * Gets the root schema set that includes this schema.
     *
     * @return never null.
     */
    XSSchemaSet getRoot();
}
