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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.sun.xml.internal.org.jvnet.staxex.XMLStreamReaderEx;
import org.xml.sax.SAXException;

/**
 * Reads XML from StAX {@link XMLStreamReader} and
 * feeds events to {@link XmlVisitor}.
 *
 * @author Ryan.Shoemaker@Sun.COM
 * @author Kohsuke Kawaguchi
 * @version JAXB 2.0
 */
final class StAXExConnector extends StAXStreamConnector {

    // StAX event source
    private final XMLStreamReaderEx in;

    public StAXExConnector(XMLStreamReaderEx in, XmlVisitor visitor) {
        super(in,visitor);
        this.in = in;
    }

    @Override
    protected void handleCharacters() throws XMLStreamException, SAXException {
        if( predictor.expectText() ) {
            CharSequence pcdata = in.getPCDATA();
            if(pcdata instanceof com.sun.xml.internal.org.jvnet.staxex.Base64Data) {
                com.sun.xml.internal.org.jvnet.staxex.Base64Data bd = (com.sun.xml.internal.org.jvnet.staxex.Base64Data) pcdata;
                Base64Data binary = new Base64Data();
                if(!bd.hasData())
                    binary.set(bd.getDataHandler());
                else
                    binary.set( bd.get(), bd.getDataLen(), bd.getMimeType() );
                // we make an assumption here that the binary data shows up on its own
                // not adjacent to other text. So it's OK to fire it off right now.
                visitor.text(binary);
                textReported = true;
            } else {
                buffer.append(pcdata);
            }
        }
    }
}
