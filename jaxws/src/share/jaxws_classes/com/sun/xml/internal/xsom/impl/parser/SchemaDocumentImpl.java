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

package com.sun.xml.internal.xsom.impl.parser;

import com.sun.xml.internal.xsom.impl.SchemaImpl;
import com.sun.xml.internal.xsom.parser.SchemaDocument;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link SchemaDocument} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SchemaDocumentImpl implements SchemaDocument
{
    private final SchemaImpl schema;

    /**
     * URI of the schema document to be parsed. Can be null.
     */
    private final String schemaDocumentURI;

    /**
     * {@link SchemaDocumentImpl}s that are referenced from this document.
     */
    final Set<SchemaDocumentImpl> references = new HashSet<SchemaDocumentImpl>();

    /**
     * {@link SchemaDocumentImpl}s that are referencing this document.
     */
    final Set<SchemaDocumentImpl> referers = new HashSet<SchemaDocumentImpl>();

    protected SchemaDocumentImpl(SchemaImpl schema, String _schemaDocumentURI) {
        this.schema = schema;
        this.schemaDocumentURI = _schemaDocumentURI;
    }

    public String getSystemId() {
        return schemaDocumentURI;
    }

    public String getTargetNamespace() {
        return schema.getTargetNamespace();
    }

    public SchemaImpl getSchema() {
        return schema;
    }

    public Set<SchemaDocument> getReferencedDocuments() {
        return Collections.<SchemaDocument>unmodifiableSet(references);
    }

    public Set<SchemaDocument> getIncludedDocuments() {
        return getImportedDocuments(this.getTargetNamespace());
    }

    public Set<SchemaDocument> getImportedDocuments(String targetNamespace) {
        if(targetNamespace==null)
            throw new IllegalArgumentException();
        Set<SchemaDocument> r = new HashSet<SchemaDocument>();
        for (SchemaDocumentImpl doc : references) {
            if(doc.getTargetNamespace().equals(targetNamespace))
                r.add(doc);
        }
        return Collections.unmodifiableSet(r);
    }

    public boolean includes(SchemaDocument doc) {
        if(!references.contains(doc))
            return false;
        return doc.getSchema()==schema;
    }

    public boolean imports(SchemaDocument doc) {
        if(!references.contains(doc))
            return false;
        return doc.getSchema()!=schema;
    }

    public Set<SchemaDocument> getReferers() {
        return Collections.<SchemaDocument>unmodifiableSet(referers);
    }

    @Override
    public boolean equals(Object o) {
        SchemaDocumentImpl rhs = (SchemaDocumentImpl) o;

        if( this.schemaDocumentURI==null || rhs.schemaDocumentURI==null)
            return this==rhs;
        if(!schemaDocumentURI.equals(rhs.schemaDocumentURI) )
            return false;
        return this.schema==rhs.schema;
    }

    @Override
    public int hashCode() {
        if (schemaDocumentURI==null)
            return super.hashCode();
        if (schema == null) {
            return schemaDocumentURI.hashCode();
        }
        return schemaDocumentURI.hashCode()^this.schema.hashCode();
    }
}
