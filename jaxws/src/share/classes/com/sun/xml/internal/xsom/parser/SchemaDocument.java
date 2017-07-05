/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.xsom.parser;

import com.sun.xml.internal.xsom.XSSchema;

import java.util.Set;

/**
 * Represents a parsed XML schema document.
 *
 * <p>
 * Unlike schema components defined in <tt>XS****</tt> interfaces,
 * which are inherently de-coupled from where it was loaded from,
 * {@link SchemaDocument} represents a single XML infoset that
 * is a schema document.
 *
 * <p>
 * This concept is often useful in tracking down the reference
 * relationship among schema documents.
 *
 * @see XSOMParser#getDocuments()
 * @author Kohsuke Kawaguchi
 */
public interface SchemaDocument {
    /**
     * Gets the system ID of the schema document.
     *
     * @return
     *      null if {@link XSOMParser} was not given the system Id.
     */
    String getSystemId();

    /**
     * The namespace that this schema defines.
     *
     * <p>
     * More precisely, this method simply returns the <tt>targetNamespace</tt> attribute
     * of the schema document. When schemas are referenced in certain ways
     * (AKA chameleon schema), schema components in this schema document
     * may end up defining components in other namespaces.
     *
     * @return
     *      can be "" but never null.
     */
    String getTargetNamespace();

    /**
     * Gets {@link XSSchema} component that contains all the schema
     * components defined in this namespace.
     *
     * <p>
     * The returned {@link XSSchema} contains not just components
     * defined in this {@link SchemaDocument} but all the other components
     * defined in all the schemas that collectively define this namespace.
     *
     * @return
     *      never null.
     */
    XSSchema getSchema();

    /**
     * Set of {@link SchemaDocument}s that are included/imported from this document.
     *
     * @return
     *      can be empty but never null. read-only.
     */
    Set<SchemaDocument> getReferencedDocuments();

    /**
     * Gets the {@link SchemaDocument}s that are included from this document.
     *
     * @return
     *      can be empty but never null. read-only.
     *      this set is always a subset of {@link #getReferencedDocuments()}.
     */
    Set<SchemaDocument> getIncludedDocuments();

    /**
     * Gets the {@link SchemaDocument}s that are imported from this document.
     *
     * @param targetNamespace
     *      The namespace URI of the import that you want to
     *      get {@link SchemaDocument}s for.
     * @return
     *      can be empty but never null. read-only.
     *      this set is always a subset of {@link #getReferencedDocuments()}.
     */
    Set<SchemaDocument> getImportedDocuments(String targetNamespace);

    /**
     * Returns true if this document includes the given document.
     *
     * <p>
     * Note that this method returns false if this document
     * imports the given document.
     */
    boolean includes(SchemaDocument doc);

    /**
     * Returns true if this document imports the given document.
     *
     * <p>
     * Note that this method returns false if this document
     * includes the given document.
     */
    boolean imports(SchemaDocument doc);

    /**
     * Set of {@link SchemaDocument}s that include/import this document.
     *
     * <p>
     * This works as the opposite of {@link #getReferencedDocuments()}.
     *
     * @return
     *      can be empty but never null. read-only.
     */
    Set<SchemaDocument> getReferers();
}
