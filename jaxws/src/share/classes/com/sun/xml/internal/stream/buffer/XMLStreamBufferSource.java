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
package com.sun.xml.internal.stream.buffer;

import com.sun.xml.internal.stream.buffer.sax.SAXBufferProcessor;
import java.io.ByteArrayInputStream;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * A JAXP Source implementation that supports the parsing
 * of {@link XMLStreamBuffer} for use by applications that expect a Source.
 *
 * <p>
 * The derivation of XMLStreamBufferSource from SAXSource is an implementation
 * detail.
 *
 * <p>Applications shall obey the following restrictions:
 * <ul>
 * <li>The setXMLReader and setInputSource shall not be called.</li>
 * <li>The XMLReader object obtained by the getXMLReader method shall
 *     be used only for parsing the InputSource object returned by
 *     the getInputSource method.</li>
 * <li>The InputSource object obtained by the getInputSource method shall
 *     be used only for being parsed by the XMLReader object returned by
 *     the getXMLReader method.</li>
 * </ul>
 */
public class XMLStreamBufferSource extends SAXSource {
    protected XMLStreamBuffer _buffer;
    protected SAXBufferProcessor _bufferProcessor;

    /**
     * XMLStreamBufferSource constructor.
     *
     * @param buffer the {@link XMLStreamBuffer} to use.
     */
    public XMLStreamBufferSource(XMLStreamBuffer buffer) {
        super(new InputSource(
                new ByteArrayInputStream(new byte[0])));
        setXMLStreamBuffer(buffer);
    }

    /**
     * Get the {@link XMLStreamBuffer} that is used.
     *
     * @return the {@link XMLStreamBuffer}.
     */
    public XMLStreamBuffer getXMLStreamBuffer() {
        return _buffer;
    }

    /**
     * Set the {@link XMLStreamBuffer} to use.
     *
     * @param buffer the {@link XMLStreamBuffer}.
     */
    public void setXMLStreamBuffer(XMLStreamBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer cannot be null");
        }
        _buffer = buffer;

        if (_bufferProcessor != null) {
            _bufferProcessor.setBuffer(_buffer,false);
        }
    }

    public XMLReader getXMLReader() {
        if (_bufferProcessor == null) {
            _bufferProcessor = new SAXBufferProcessor(_buffer,false);
            setXMLReader(_bufferProcessor);
        } else if (super.getXMLReader() == null) {
            setXMLReader(_bufferProcessor);
        }

        return _bufferProcessor;
    }
}
