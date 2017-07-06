/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.LazyEnvelope;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.StaxBridge;
import com.sun.xml.internal.messaging.saaj.soap.StaxLazySourceBridge;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.FastInfosetReflection;
import com.sun.xml.internal.messaging.saaj.util.stax.LazyEnvelopeStaxReader;
import com.sun.xml.internal.messaging.saaj.util.transform.EfficientStreamingTransformer;

import com.sun.xml.internal.org.jvnet.staxex.util.DOMStreamReader;
import com.sun.xml.internal.org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;
import org.w3c.dom.Element;

/**
 * Our implementation of the SOAP envelope.
 *
 * @author Anil Vijendran (anil@sun.com)
 */
public abstract class EnvelopeImpl extends ElementImpl implements LazyEnvelope {
    protected HeaderImpl header;
    protected BodyImpl body;
    String omitXmlDecl = "yes";
    String charset = "utf-8";
    String xmlDecl = null;

    protected EnvelopeImpl(SOAPDocumentImpl ownerDoc, Name name) {
        super(ownerDoc, name);
    }

    protected EnvelopeImpl(SOAPDocumentImpl ownerDoc, QName name) {
        super(ownerDoc, name);
    }

    protected EnvelopeImpl(
        SOAPDocumentImpl ownerDoc,
        NameImpl name,
        boolean createHeader,
        boolean createBody)
        throws SOAPException {
        this(ownerDoc, name);

        ensureNamespaceIsDeclared(
            getElementQName().getPrefix(), getElementQName().getNamespaceURI());

        // XXX
        if (createHeader)
            addHeader();

        if (createBody)
            addBody();
    }

    public EnvelopeImpl(SOAPDocumentImpl ownerDoc, Element domElement) {
        super(ownerDoc, domElement);
    }

    protected abstract NameImpl getHeaderName(String prefix);
    protected abstract NameImpl getBodyName(String prefix);

    @Override
    public SOAPHeader addHeader() throws SOAPException {
        return addHeader(null);
    }

    public SOAPHeader addHeader(String prefix) throws SOAPException {

        if (prefix == null || prefix.equals("")) {
            prefix = getPrefix();
        }

        NameImpl headerName = getHeaderName(prefix);
        NameImpl bodyName = getBodyName(prefix);

        HeaderImpl header = null;
        SOAPElement firstChild = (SOAPElement) getFirstChildElement();

        if (firstChild != null) {
            if (firstChild.getElementName().equals(headerName)) {
                log.severe("SAAJ0120.impl.header.already.exists");
                throw new SOAPExceptionImpl("Can't add a header when one is already present.");
            } else if (!firstChild.getElementName().equals(bodyName)) {
                log.severe("SAAJ0121.impl.invalid.first.child.of.envelope");
                throw new SOAPExceptionImpl("First child of Envelope must be either a Header or Body");
            }
        }

        header = (HeaderImpl) createElement(headerName);
        insertBefore(header.getDomElement(), firstChild);
        header.ensureNamespaceIsDeclared(headerName.getPrefix(), headerName.getURI());

        return header;
    }

    protected void lookForHeader() throws SOAPException {
        NameImpl headerName = getHeaderName(null);

        HeaderImpl hdr = (HeaderImpl) findChild(headerName);
        header = hdr;
    }

    @Override
    public SOAPHeader getHeader() throws SOAPException {
        lookForHeader();
        return header;
    }

    protected void lookForBody() throws SOAPException {
        NameImpl bodyName = getBodyName(null);

        BodyImpl bodyChildElement = (BodyImpl) findChild(bodyName);
        body = bodyChildElement;
    }

    @Override
    public SOAPBody addBody() throws SOAPException {
        return addBody(null);
    }

    public SOAPBody addBody(String prefix) throws SOAPException {
        lookForBody();

        if (prefix == null || prefix.equals("")) {
            prefix = getPrefix();
        }

        if (body == null) {
            NameImpl bodyName = getBodyName(prefix);
            body = (BodyImpl) createElement(bodyName);
            insertBefore(body.getDomElement(), null);
            body.ensureNamespaceIsDeclared(bodyName.getPrefix(), bodyName.getURI());
        } else {
            log.severe("SAAJ0122.impl.body.already.exists");
            throw new SOAPExceptionImpl("Can't add a body when one is already present.");
        }

        return body;
    }

    @Override
    protected SOAPElement addElement(Name name) throws SOAPException {
        if (getBodyName(null).equals(name)) {
            return addBody(name.getPrefix());
        }
        if (getHeaderName(null).equals(name)) {
            return addHeader(name.getPrefix());
        }

        return super.addElement(name);
    }

    @Override
    protected SOAPElement addElement(QName name) throws SOAPException {
        if (getBodyName(null).equals(NameImpl.convertToName(name))) {
            return addBody(name.getPrefix());
        }
        if (getHeaderName(null).equals(NameImpl.convertToName(name))) {
            return addHeader(name.getPrefix());
        }

        return super.addElement(name);
    }

    @Override
    public SOAPBody getBody() throws SOAPException {
        lookForBody();
        return body;
    }

    @Override
    public Source getContent() {
        return new DOMSource(getOwnerDocument());
    }

    @Override
    public Name createName(String localName, String prefix, String uri)
        throws SOAPException {

        // validating parameters before passing them on
        // to make sure that the namespace specification rules are followed

        // reserved xmlns prefix cannot be used.
        if ("xmlns".equals(prefix)) {
            log.severe("SAAJ0123.impl.no.reserved.xmlns");
            throw new SOAPExceptionImpl("Cannot declare reserved xmlns prefix");
        }
        // Qualified name cannot be xmlns.
        if ((prefix == null) && ("xmlns".equals(localName))) {
            log.severe("SAAJ0124.impl.qualified.name.cannot.be.xmlns");
            throw new SOAPExceptionImpl("Qualified name cannot be xmlns");
        }

        return NameImpl.create(localName, prefix, uri);
    }

    public Name createName(String localName, String prefix)
        throws SOAPException {
        String namespace = getNamespaceURI(prefix);
        if (namespace == null) {
            log.log(
                Level.SEVERE,
                "SAAJ0126.impl.cannot.locate.ns",
                new String[] { prefix });
            throw new SOAPExceptionImpl(
                "Unable to locate namespace for prefix " + prefix);
        }
        return NameImpl.create(localName, prefix, namespace);
    }

    @Override
    public Name createName(String localName) throws SOAPException {
        return NameImpl.createFromUnqualifiedName(localName);
    }

    public void setOmitXmlDecl(String value) {
        this.omitXmlDecl = value;
    }

    public void setXmlDecl(String value) {
        this.xmlDecl = value;
    }

    public void setCharsetEncoding(String value) {
        charset = value;
    }

    @Override
    public void output(OutputStream out) throws IOException {
        try {
//            materializeBody();
            Transformer transformer =
                EfficientStreamingTransformer.newTransformer();

            transformer.setOutputProperty(
                OutputKeys.OMIT_XML_DECLARATION, "yes");
                /*omitXmlDecl);*/
            // no equivalent for "setExpandEmptyElements"
            transformer.setOutputProperty(
                OutputKeys.ENCODING,
                charset);

            if (omitXmlDecl.equals("no") && xmlDecl == null) {
                xmlDecl = "<?xml version=\"" + getOwnerDocument().getXmlVersion() + "\" encoding=\"" +
                    charset + "\" ?>";
            }

           StreamResult result = new StreamResult(out);
            if (xmlDecl != null) {
                OutputStreamWriter writer = new OutputStreamWriter(out, charset);
                writer.write(xmlDecl);
                writer.flush();
                result = new StreamResult(writer);
            }

            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "SAAJ0190.impl.set.xml.declaration",
                        new String[] { omitXmlDecl });
                log.log(Level.FINE, "SAAJ0191.impl.set.encoding",
                        new String[] { charset });
            }

            //StreamResult result = new StreamResult(out);
            transformer.transform(getContent(), result);
        } catch (Exception ex) {
            throw new IOException(ex.getMessage());
        }
    }

    /**
     * Serialize to FI if boolean parameter set.
     */
    @Override
    public void output(OutputStream out, boolean isFastInfoset)
        throws IOException
    {
        if (!isFastInfoset) {
            output(out);
        }
        else {
            try {
                // Run transform and generate FI output from content
                Transformer transformer = EfficientStreamingTransformer.newTransformer();
                    transformer.transform(getContent(),
                        FastInfosetReflection.FastInfosetResult_new(out));
            }
            catch (Exception ex) {
                throw new IOException(ex.getMessage());
            }
        }
    }

    //    public void prettyPrint(OutputStream out) throws IOException {
    //        if (getDocument() == null)
    //            initDocument();
    //
    //        OutputFormat format = OutputFormat.createPrettyPrint();
    //
    //        format.setIndentSize(2);
    //        format.setNewlines(true);
    //        format.setTrimText(true);
    //        format.setPadText(true);
    //        format.setExpandEmptyElements(false);
    //
    //        XMLWriter writer = new XMLWriter(out, format);
    //        writer.write(getDocument());
    //    }
    //
    //    public void prettyPrint(Writer out) throws IOException {
    //        if (getDocument() == null)
    //            initDocument();
    //
    //        OutputFormat format = OutputFormat.createPrettyPrint();
    //
    //        format.setIndentSize(2);
    //        format.setNewlines(true);
    //        format.setTrimText(true);
    //        format.setPadText(true);
    //        format.setExpandEmptyElements(false);
    //
    //        XMLWriter writer = new XMLWriter(out, format);
    //        writer.write(getDocument());
    //    }


    @Override
     public SOAPElement setElementQName(QName newName) throws SOAPException {
        log.log(Level.SEVERE,
                "SAAJ0146.impl.invalid.name.change.requested",
                new Object[] {elementQName.getLocalPart(),
                              newName.getLocalPart()});
        throw new SOAPException("Cannot change name for "
                                + elementQName.getLocalPart() + " to "
                                + newName.getLocalPart());
     }

    @Override
    public void setStaxBridge(StaxBridge bridge) throws SOAPException {
        //set it on the body
        ((BodyImpl) getBody()).setStaxBridge(bridge);
    }

    @Override
    public StaxBridge getStaxBridge() throws SOAPException {
        return ((BodyImpl) getBody()).getStaxBridge();
    }

    @Override
    public XMLStreamReader getPayloadReader() throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadReader();
    }

    @Override
    public void writeTo(final XMLStreamWriter writer) throws XMLStreamException, SOAPException {
        StaxBridge readBridge = this.getStaxBridge();
        if (readBridge != null && readBridge instanceof StaxLazySourceBridge) {
//              StaxSoapWriteBridge writingBridge =  new StaxSoapWriteBridge(this);
//              writingBridge.write(writer);
                final String soapEnvNS = this.getNamespaceURI();
                final DOMStreamReader reader = new DOMStreamReader(this);
                XMLStreamReaderToXMLStreamWriter writingBridge =  new XMLStreamReaderToXMLStreamWriter();
                writingBridge.bridge( new XMLStreamReaderToXMLStreamWriter.Breakpoint(reader, writer) {
                        @Override
                        public boolean proceedAfterStartElement()  {
                                if ("Body".equals(reader.getLocalName()) && soapEnvNS.equals(reader.getNamespaceURI()) ){
                                        return false;
                                } else
                                        return true;
                        }
            });//bridgeToBodyStartTag
            ((StaxLazySourceBridge)readBridge).writePayloadTo(writer);
            writer.writeEndElement();//body
            writer.writeEndElement();//env
            writer.writeEndDocument();
            writer.flush();
        } else {
                LazyEnvelopeStaxReader lazyEnvReader = new LazyEnvelopeStaxReader(this);
                XMLStreamReaderToXMLStreamWriter writingBridge = new XMLStreamReaderToXMLStreamWriter();
                writingBridge.bridge(lazyEnvReader, writer);
//            writingBridge.bridge(new XMLStreamReaderToXMLStreamWriter.Breakpoint(lazyEnvReader, writer));
        }
        //Assume the staxBridge is exhausted now since we would have read the body reader
        ((BodyImpl) getBody()).setPayloadStreamRead();
    }

    @Override
    public QName getPayloadQName() throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadQName();
    }

    @Override
    public String getPayloadAttributeValue(String localName) throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadAttributeValue(localName);
    }

    @Override
    public String getPayloadAttributeValue(QName qName) throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadAttributeValue(qName);
    }

    @Override
    public boolean isLazy() {
        try {
            return ((BodyImpl) getBody()).isLazy();
        } catch (SOAPException e) {
            return false;
        }
    }

}
