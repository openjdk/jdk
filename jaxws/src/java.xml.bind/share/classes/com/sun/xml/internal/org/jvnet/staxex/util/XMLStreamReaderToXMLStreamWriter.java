/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.staxex.util;

import java.io.IOException;

import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.XMLConstants;

import com.sun.xml.internal.org.jvnet.staxex.Base64Data;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamReaderEx;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx;

/**
 * Reads a sub-tree from {@link XMLStreamReader} and writes to {@link XMLStreamWriter}
 * as-is.
 *
 * <p>
 * This class can be sub-classed to implement a simple transformation logic.
 *
 * @author Kohsuke Kawaguchi
 * @author Ryan Shoemaker
 */
public class XMLStreamReaderToXMLStreamWriter {

    static public class Breakpoint {
        protected XMLStreamReader reader;
        protected XMLStreamWriter writer;

        public Breakpoint(XMLStreamReader r, XMLStreamWriter w) { reader = r; writer = w; }

        public XMLStreamReader reader() { return reader; }
        public XMLStreamWriter writer() { return writer; }
        public boolean proceedBeforeStartElement() { return true; }
        public boolean proceedAfterStartElement()  { return true; }
    }

    private static final int BUF_SIZE = 4096;

    protected XMLStreamReader in;
    protected XMLStreamWriter out;

    private char[] buf;

    boolean optimizeBase64Data = false;

    AttachmentMarshaller mtomAttachmentMarshaller;

    /**
     * Reads one subtree and writes it out.
     *
     * <p>
     * The {@link XMLStreamWriter} never receives a start/end document event.
     * Those need to be written separately by the caller.
     */
    public void bridge(XMLStreamReader in, XMLStreamWriter out) throws XMLStreamException {
        bridge(in, out, null);
    }

    public void bridge(Breakpoint breakPoint) throws XMLStreamException {
        bridge(breakPoint.reader(), breakPoint.writer(), breakPoint);
    }

    private void bridge(XMLStreamReader in, XMLStreamWriter out, Breakpoint breakPoint) throws XMLStreamException {
        assert in!=null && out!=null;
        this.in = in;
        this.out = out;

        optimizeBase64Data = (in instanceof XMLStreamReaderEx);

        if (out instanceof XMLStreamWriterEx && out instanceof MtomStreamWriter) {
            mtomAttachmentMarshaller = ((MtomStreamWriter) out).getAttachmentMarshaller();
        }
        // remembers the nest level of elements to know when we are done.
        int depth=0;

        buf = new char[BUF_SIZE];

        // if the parser is at the start tag, proceed to the first element
        int event = getEventType();

        if( event!=XMLStreamConstants.START_ELEMENT)
            throw new IllegalStateException("The current event is not START_ELEMENT\n but " + event);

        do {
            // These are all of the events listed in the javadoc for
            // XMLEvent.
            // The spec only really describes 11 of them.
            switch (event) {
                case XMLStreamConstants.START_ELEMENT :
                    if (breakPoint != null && !breakPoint.proceedBeforeStartElement()) return;
                    depth++;
                    handleStartElement();
                    if (breakPoint != null && !breakPoint.proceedAfterStartElement()) return;
                    break;
                case XMLStreamConstants.END_ELEMENT :
                    handleEndElement();
                    depth--;
                    if(depth==0)
                        return;
                    break;
                case XMLStreamConstants.CHARACTERS :
                    handleCharacters();
                    break;
                case XMLStreamConstants.ENTITY_REFERENCE :
                    handleEntityReference();
                    break;
                case XMLStreamConstants.PROCESSING_INSTRUCTION :
                    handlePI();
                    break;
                case XMLStreamConstants.COMMENT :
                    handleComment();
                    break;
                case XMLStreamConstants.DTD :
                    handleDTD();
                    break;
                case XMLStreamConstants.CDATA :
                    handleCDATA();
                    break;
                case XMLStreamConstants.SPACE :
                    handleSpace();
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    throw new XMLStreamException("Malformed XML at depth="+depth+", Reached EOF. Event="+event);
                default :
                    throw new XMLStreamException("Cannot process event: " + event);
            }

            event=getNextEvent();
        } while (depth!=0);
    }

    protected void handlePI() throws XMLStreamException {
        out.writeProcessingInstruction(
            in.getPITarget(),
            in.getPIData());
    }


    protected void handleCharacters() throws XMLStreamException {

        CharSequence c = null;

        if (optimizeBase64Data) {
            c = ((XMLStreamReaderEx)in).getPCDATA();
        }

        if ((c != null) && (c instanceof Base64Data)) {
            if (mtomAttachmentMarshaller != null) {
                Base64Data b64d = (Base64Data) c;
                ((XMLStreamWriterEx)out).writeBinary(b64d.getDataHandler());
            } else {
                try {
                    ((Base64Data)c).writeTo(out);
                } catch (IOException e) {
                    throw new XMLStreamException(e);
                }
            }
        } else {
            for (int start=0,read=buf.length; read == buf.length; start+=buf.length) {
                read = in.getTextCharacters(start, buf, 0, buf.length);
                out.writeCharacters(buf, 0, read);
            }
        }
    }

    protected void handleEndElement() throws XMLStreamException {
        out.writeEndElement();
    }

    protected void handleStartElement() throws XMLStreamException {
        String nsUri = in.getNamespaceURI();
        if(nsUri==null)
            out.writeStartElement(in.getLocalName());
        else
            out.writeStartElement(
                fixNull(in.getPrefix()),
                in.getLocalName(),
                nsUri
            );

        // start namespace bindings
        int nsCount = in.getNamespaceCount();
        for (int i = 0; i < nsCount; i++) {
            out.writeNamespace(
                fixNull(in.getNamespacePrefix(i)), //StAX reader will return null for default NS
                fixNull(in.getNamespaceURI(i)));    // zephyr doesn't like null, I don't know what is correct, so just fix null to "" for now
        }

        // write attributes
        int attCount = in.getAttributeCount();
        for (int i = 0; i < attCount; i++) {
            handleAttribute(i);
        }
    }

    /**
     * Writes out the {@code i}-th attribute of the current element.
     *
     * <p>
     * Used from {@link #handleStartElement()}.
     */
    protected void handleAttribute(int i) throws XMLStreamException {
        String nsUri = in.getAttributeNamespace(i);
        String prefix = in.getAttributePrefix(i);
         if (fixNull(nsUri).equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
             //Its a namespace decl, ignore as it is already written.
             return;
         }

        if(nsUri==null || prefix == null || prefix.equals("")) {
            out.writeAttribute(
                in.getAttributeLocalName(i),
                in.getAttributeValue(i)
            );
        } else {
            out.writeAttribute(
                prefix,
                nsUri,
                in.getAttributeLocalName(i),
                in.getAttributeValue(i)
            );
        }
    }

    protected void handleDTD() throws XMLStreamException {
        out.writeDTD(in.getText());
    }

    protected void handleComment() throws XMLStreamException {
        out.writeComment(in.getText());
    }

    protected void handleEntityReference() throws XMLStreamException {
        out.writeEntityRef(in.getText());
    }

    protected void handleSpace() throws XMLStreamException {
        handleCharacters();
    }

    protected void handleCDATA() throws XMLStreamException {
        out.writeCData(in.getText());
    }

    private static String fixNull(String s) {
        if(s==null)     return "";
        else            return s;
    }

    private int getEventType() throws XMLStreamException {
        int event = in.getEventType();
     // if the parser is at the start tag, proceed to the first element
        //Note - need to do this every time because we could be using a composite reader
        if(event == XMLStreamConstants.START_DOCUMENT) {
            // nextTag doesn't correctly handle DTDs
            while( !in.isStartElement() ) {
                event = in.next();
                if (event == XMLStreamConstants.COMMENT)
                    handleComment();
            }
        }
        return event;
    }

    private int getNextEvent() throws XMLStreamException {
        in.next();
        return getEventType();
    }
}
