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

package com.sun.xml.internal.bind.v2.runtime.output;

import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Base64Data;

import com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx;

/**
 * {@link XmlOutput} for {@link XMLStreamWriterEx}.
 *
 * @author Paul Sandoz.
 */
public final class StAXExStreamWriterOutput extends XMLStreamWriterOutput {
    private final XMLStreamWriterEx out;

    public StAXExStreamWriterOutput(XMLStreamWriterEx out) {
        super(out);
        this.out = out;
    }

    public void text(Pcdata value, boolean needsSeparatingWhitespace) throws XMLStreamException {
        if(needsSeparatingWhitespace) {
            out.writeCharacters(" ");
        }

        if (!(value instanceof Base64Data)) {
            out.writeCharacters(value.toString());
        } else {
            Base64Data v = (Base64Data)value;
            out.writeBinary(v.getDataHandler());
        }
    }
}
