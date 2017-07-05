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

package com.sun.xml.internal.ws.encoding;

import com.sun.istack.internal.XMLStreamException2;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx;

import javax.activation.DataHandler;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Partial default implementation of {@link XMLStreamWriterEx}.
 *
 * TODO: find a good home for this class.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractXMLStreamWriterExImpl implements XMLStreamWriterEx {

    private StreamImpl stream;

    public void writeBinary(DataHandler data) throws XMLStreamException {
        try {
            StreamImpl stream = _writeBinary(data.getContentType());
            stream.write(data.getInputStream());
            stream.close();
        } catch (IOException e) {
            throw new XMLStreamException2(e);
        }
    }
    public OutputStream writeBinary(String contentType) throws XMLStreamException {
        return _writeBinary(contentType);
    }

    private StreamImpl _writeBinary(String contentType) {
        if(stream==null)
            stream = new StreamImpl();
        else
            stream.reset();
        stream.contentType = contentType;
        return stream;
    }

    private final class StreamImpl extends ByteArrayBuffer {
        private String contentType;
        public void close() throws IOException {
            super.close();
            try {
                writeBinary(buf,0,size(),contentType);
            } catch (XMLStreamException e) {
                IOException x = new IOException();
                x.initCause(e);
                throw x;
            }
        }
    }
}
