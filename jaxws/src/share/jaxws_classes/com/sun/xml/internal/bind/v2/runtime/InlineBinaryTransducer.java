/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.api.AccessorException;

import org.xml.sax.SAXException;

/**
 * Transducer that signals the runtime that this binary data shall be always inlined.
 *
 * @author Kohsuke Kawaguchi
 */
public class InlineBinaryTransducer<V> extends FilterTransducer<V> {
    public InlineBinaryTransducer(Transducer<V> core) {
        super(core);
    }

    @Override
    public @NotNull CharSequence print(@NotNull V o) throws AccessorException {
        XMLSerializer w = XMLSerializer.getInstance();
        boolean old = w.setInlineBinaryFlag(true);
        try {
            return core.print(o);
        } finally {
            w.setInlineBinaryFlag(old);
        }
    }

    @Override
    public void writeText(XMLSerializer w, V o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        boolean old = w.setInlineBinaryFlag(true);
        try {
            core.writeText(w,o,fieldName);
        } finally {
            w.setInlineBinaryFlag(old);
        }
    }

    @Override
    public void writeLeafElement(XMLSerializer w, Name tagName, V o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        boolean old = w.setInlineBinaryFlag(true);
        try {
            core.writeLeafElement(w, tagName, o, fieldName);
        } finally {
            w.setInlineBinaryFlag(old);
        }
    }
}
