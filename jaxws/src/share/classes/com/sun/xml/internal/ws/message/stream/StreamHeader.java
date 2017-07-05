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
package com.sun.xml.internal.ws.message.stream;

import com.sun.istack.internal.FinalArrayList;
import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferSource;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.message.AbstractHeaderImpl;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import java.util.List;
import java.util.Set;

/**
 * {@link Header} whose physical data representation is an XMLStreamBuffer.
 *
 * @author Paul.Sandoz@Sun.Com
 */
public abstract class StreamHeader extends AbstractHeaderImpl {
    protected final XMLStreamBuffer _mark;

    protected boolean _isMustUnderstand;

    /**
     * Role or actor value.
     */
    protected @NotNull String _role;

    protected boolean _isRelay;

    protected String _localName;

    protected String _namespaceURI;

    /**
     * Keep the information about an attribute on the header element.
     *
     * TODO: this whole attribute handling could be done better, I think.
     */
    protected static final class Attribute {
        /**
         * Can be empty but never null.
         */
        final String nsUri;
        final String localName;
        final String value;

        public Attribute(String nsUri, String localName, String value) {
            this.nsUri = fixNull(nsUri);
            this.localName = localName;
            this.value = value;
        }
    }

    /**
     * The attributes on the header element.
     * We expect there to be only a small number of them,
     * so the use of {@link List} would be justified.
     *
     * Null if no attribute is present.
     */
    private final FinalArrayList<Attribute> attributes;

    /**
     * Creates a {@link StreamHeader}.
     *
     * @param reader
     *      The parser pointing at the start of the mark.
     *      Technically this information is redundant,
     *      but it achieves a better performance.
     * @param mark
     *      The start of the buffered header content.
     */
    protected StreamHeader(XMLStreamReader reader, XMLStreamBuffer mark) {
        assert reader!=null && mark!=null;
        _mark = mark;
        _localName = reader.getLocalName();
        _namespaceURI = reader.getNamespaceURI();
        attributes = processHeaderAttributes(reader);
    }

    /**
     * Creates a {@link StreamHeader}.
     *
     * @param reader
     *      The parser that points to the start tag of the header.
     *      By the end of this method, the parser will point at
     *      the end tag of this element.
     */
    protected StreamHeader(XMLStreamReader reader) throws XMLStreamException {
        _localName = reader.getLocalName();
        _namespaceURI = reader.getNamespaceURI();
        attributes = processHeaderAttributes(reader);
        // cache the body
        _mark = XMLStreamBuffer.createNewBufferFromXMLStreamReader(reader);
    }

    public final boolean isIgnorable(@NotNull SOAPVersion soapVersion, @NotNull Set<String> roles) {
        // check mustUnderstand
        if(!_isMustUnderstand) return true;

        if (roles == null)
            return true;

        // now role
        return !roles.contains(_role);
    }

    public @NotNull String getRole(@NotNull SOAPVersion soapVersion) {
        assert _role!=null;
        return _role;
    }

    public boolean isRelay() {
        return _isRelay;
    }

    public @NotNull String getNamespaceURI() {
        return _namespaceURI;
    }

    public @NotNull String getLocalPart() {
        return _localName;
    }

    public String getAttribute(String nsUri, String localName) {
        if(attributes!=null) {
            for(int i=attributes.size()-1; i>=0; i-- ) {
                Attribute a = attributes.get(i);
                if(a.localName.equals(localName) && a.nsUri.equals(nsUri))
                    return a.value;
            }
        }
        return null;
    }

    /**
     * Reads the header as a {@link XMLStreamReader}
     */
    public XMLStreamReader readHeader() throws XMLStreamException {
        return _mark.readAsXMLStreamReader();
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        // TODO what about in-scope namespaces
        _mark.writeToXMLStreamWriter(w);
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        try {
            // TODO what about in-scope namespaces
            // Not very efficient consider implementing a stream buffer
            // processor that produces a DOM node from the buffer.
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            XMLStreamBufferSource source = new XMLStreamBufferSource(_mark);
            DOMResult result = new DOMResult();
            t.transform(source, result);
            Node d = result.getNode();
            if(d.getNodeType() == Node.DOCUMENT_NODE)
                d = d.getFirstChild();
            SOAPHeader header = saaj.getSOAPHeader();
            Node node = header.getOwnerDocument().importNode(d, true);
            header.appendChild(node);
        } catch (Exception e) {
            throw new SOAPException(e);
        }
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        _mark.writeTo(contentHandler);
    }

    /**
     * Creates an EPR without copying infoset.
     *
     * This is the most common implementation on which {@link Header#readAsEPR(AddressingVersion)}
     * is invoked on.
     */
    @Override @NotNull
    public WSEndpointReference readAsEPR(AddressingVersion expected) throws XMLStreamException {
        return new WSEndpointReference(_mark,expected);
    }

    protected abstract FinalArrayList<Attribute> processHeaderAttributes(XMLStreamReader reader);

    /**
     * Convert null to "".
     */
    private static String fixNull(String s) {
        if(s==null) return "";
        else        return s;
    }
}
