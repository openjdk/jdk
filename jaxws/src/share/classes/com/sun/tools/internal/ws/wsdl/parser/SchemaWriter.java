/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.wsdl.parser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import javax.xml.namespace.QName;

import com.sun.tools.internal.ws.wsdl.document.schema.Schema;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaAttribute;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaDocument;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaElement;
import com.sun.tools.internal.ws.wsdl.framework.WriterContext;

/**
 * A writer for XML Schema fragments within a WSDL document.
 *
 * @author WS Development Team
 */
public class SchemaWriter {

    public SchemaWriter() {
    }

    public void write(SchemaDocument document, OutputStream os)
        throws IOException {
        WriterContext context = new WriterContext(os);
        writeSchema(context, document.getSchema());
        context.flush();
    }

    public void writeSchema(WriterContext context, Schema schema)
        throws IOException {
        context.push();
        try {
            writeTopSchemaElement(context, schema);
        } catch (Exception e) {
        } finally {
            context.pop();
        }
    }

    protected void writeTopSchemaElement(WriterContext context, Schema schema)
        throws IOException {
        SchemaElement schemaElement = schema.getContent();
        QName name = schemaElement.getQName();

        // make sure that all namespaces we expect are actually declared
        for (Iterator iter = schema.prefixes(); iter.hasNext();) {
            String prefix = (String) iter.next();
            String expectedURI = schema.getURIForPrefix(prefix);
            if (!expectedURI.equals(context.getNamespaceURI(prefix))) {
                context.declarePrefix(prefix, expectedURI);
            }
        }

        for (Iterator iter = schemaElement.prefixes(); iter.hasNext();) {
            String prefix = (String) iter.next();
            String uri = schemaElement.getURIForPrefix(prefix);
            context.declarePrefix(prefix, uri);
        }

        context.writeStartTag(name);

        for (Iterator iter = schemaElement.attributes(); iter.hasNext();) {
            SchemaAttribute attribute = (SchemaAttribute) iter.next();
            if (attribute.getNamespaceURI() == null) {
                context.writeAttribute(
                    attribute.getLocalName(),
                    attribute.getValue(context));
            } else {
                context.writeAttribute(
                    context.getQNameString(attribute.getQName()),
                    attribute.getValue(context));
            }
        }

        context.writeAllPendingNamespaceDeclarations();

        for (Iterator iter = schemaElement.children(); iter.hasNext();) {
            SchemaElement child = (SchemaElement) iter.next();
            writeSchemaElement(context, child);
        }

        context.writeEndTag(name);
    }

    protected void writeSchemaElement(
        WriterContext context,
        SchemaElement schemaElement)
        throws IOException {
        QName name = schemaElement.getQName();

        if (schemaElement.declaresPrefixes()) {
            context.push();
        }

        context.writeStartTag(name);

        if (schemaElement.declaresPrefixes()) {
            for (Iterator iter = schemaElement.prefixes(); iter.hasNext();) {
                String prefix = (String) iter.next();
                String uri = schemaElement.getURIForPrefix(prefix);
                context.writeNamespaceDeclaration(prefix, uri);
                context.declarePrefix(prefix, uri);
            }
        }

        for (Iterator iter = schemaElement.attributes(); iter.hasNext();) {
            SchemaAttribute attribute = (SchemaAttribute) iter.next();
            if (attribute.getNamespaceURI() == null) {
                context.writeAttribute(
                    attribute.getLocalName(),
                    attribute.getValue(context));
            } else {
                context.writeAttribute(
                    context.getQNameString(attribute.getQName()),
                    attribute.getValue(context));
            }
        }

        for (Iterator iter = schemaElement.children(); iter.hasNext();) {
            SchemaElement child = (SchemaElement) iter.next();
            writeSchemaElement(context, child);
        }

        context.writeEndTag(name);

        if (schemaElement.declaresPrefixes()) {
            context.pop();
        }
    }
}
