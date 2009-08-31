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

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.api.AccessorException;

import org.xml.sax.SAXException;

/**
 * {@link Transducer} that delegates to another {@link Transducer}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class FilterTransducer<T> implements Transducer<T> {
    protected final Transducer<T> core;

    protected FilterTransducer(Transducer<T> core) {
        this.core = core;
    }

    public final boolean isDefault() {
        // this must be used as a decorating transducer,
        // so it may never be default.
        return false;
    }

    public boolean useNamespace() {
        return core.useNamespace();
    }

    public void declareNamespace(T o, XMLSerializer w) throws AccessorException {
        core.declareNamespace(o, w);
    }

    public @NotNull CharSequence print(@NotNull T o) throws AccessorException {
        return core.print(o);
    }

    public T parse(CharSequence lexical) throws AccessorException, SAXException {
        return core.parse(lexical);
    }

    public void writeText(XMLSerializer w, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        core.writeText(w, o, fieldName);
    }

    public void writeLeafElement(XMLSerializer w, Name tagName, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        core.writeLeafElement(w,tagName,o,fieldName);
    }

    public QName getTypeName(T instance) {
        return null;
    }
}
