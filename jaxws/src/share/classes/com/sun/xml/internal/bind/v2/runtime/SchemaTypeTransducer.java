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
package com.sun.xml.internal.bind.v2.runtime;

import java.io.IOException;

import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;

import org.xml.sax.SAXException;

/**
 * {@link Transducer} that signals the runtime that this datatype
 * is marshalled to a different XML Schema type.
 *
 * <p>
 * This transducer is used to implement the semantics of {@link XmlSchemaType} annotation.
 *
 *
 * @see XMLSerializer#schemaType
 * @author Kohsuke Kawaguchi
 */
public class SchemaTypeTransducer<V> extends FilterTransducer<V> {
    private final QName schemaType;

    public SchemaTypeTransducer(Transducer<V> core, QName schemaType) {
        super(core);
        this.schemaType = schemaType;
    }

    @Override
    public CharSequence print(V o) throws AccessorException {
        XMLSerializer w = XMLSerializer.getInstance();
        QName old = w.setSchemaType(schemaType);
        try {
            return core.print(o);
        } finally {
            w.setSchemaType(old);
        }
    }

    @Override
    public void writeText(XMLSerializer w, V o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        QName old = w.setSchemaType(schemaType);
        try {
            core.writeText(w,o,fieldName);
        } finally {
            w.setSchemaType(old);
        }
    }

    @Override
    public void writeLeafElement(XMLSerializer w, Name tagName, V o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        QName old = w.setSchemaType(schemaType);
        try {
            core.writeLeafElement(w, tagName, o, fieldName);
        } finally {
            w.setSchemaType(old);
        }
    }
}
