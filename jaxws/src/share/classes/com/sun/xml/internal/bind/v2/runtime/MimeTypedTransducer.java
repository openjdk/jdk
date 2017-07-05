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

import java.awt.*;
import java.io.IOException;

import javax.activation.MimeType;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;

import org.xml.sax.SAXException;

/**
 * {@link Transducer} decorator that wraps another {@link Transducer}
 * and sets the expected MIME type to the context.
 *
 * <p>
 * Combined with {@link Transducer} implementations (such as one for {@link Image}),
 * this is used to control the marshalling of the BLOB types.
 *
 * @author Kohsuke Kawaguchi
 */
public final class MimeTypedTransducer<V> extends FilterTransducer<V> {
    private final MimeType expectedMimeType;

    public MimeTypedTransducer(Transducer<V> core,MimeType expectedMimeType) {
        super(core);
        this.expectedMimeType = expectedMimeType;
    }

    @Override
    public CharSequence print(V o) throws AccessorException {
        XMLSerializer w = XMLSerializer.getInstance();
        MimeType old = w.setExpectedMimeType(expectedMimeType);
        try {
            return core.print(o);
        } finally {
            w.setExpectedMimeType(old);
        }
    }

    @Override
    public void writeText(XMLSerializer w, V o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        MimeType old = w.setExpectedMimeType(expectedMimeType);
        try {
            core.writeText(w, o, fieldName);
        } finally {
            w.setExpectedMimeType(old);
        }
    }

    @Override
    public void writeLeafElement(XMLSerializer w, Name tagName, V o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        MimeType old = w.setExpectedMimeType(expectedMimeType);
        try {
            core.writeLeafElement(w, tagName, o, fieldName);
        } finally {
            w.setExpectedMimeType(old);
        }
    }
}
