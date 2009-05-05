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
/*
 *
 *
 *
 */


package com.sun.xml.internal.messaging.saaj.soap;

import java.io.*;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.soap.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.*;

import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeBodyPart;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.ElementImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.EnvelopeImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.*;
import javax.xml.transform.sax.SAXSource;

/**
 * SOAPPartImpl is the first attachment. This contains the XML/SOAP document.
 *
 * @author Anil Vijendran (anil@sun.com)
 */
public abstract class SOAPPartImpl extends SOAPPart implements SOAPDocument {
    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.LocalStrings");

    protected MimeHeaders headers;
    protected Envelope envelope;
    protected Source source;
    protected SOAPDocumentImpl document;

    //flag to indicate if a setContent happened.
    private boolean sourceWasSet = false;

    // Records whether the input source had an xml decl or not.
    protected boolean omitXmlDecl = true;

    // Records the charset encoding of the input stream source if provided.
    protected String sourceCharsetEncoding = null;

    /**
     * Reference to containing message (may be null)
     */
    protected MessageImpl message;

    protected SOAPPartImpl() {
        this(null);
    }

    protected SOAPPartImpl(MessageImpl message) {
        document = new SOAPDocumentImpl(this);
        headers = new MimeHeaders();
        this.message = message;
        headers.setHeader("Content-Type", getContentType());
    }

    protected abstract String getContentType();
    protected abstract Envelope createEnvelopeFromSource()
    throws SOAPException;
    protected abstract Envelope createEmptyEnvelope(String prefix)
    throws SOAPException;
    protected abstract SOAPPartImpl duplicateType();

    protected String getContentTypeString() {
        return getContentType();
    }

    public boolean isFastInfoset() {
        return (message != null) ? message.isFastInfoset() : false;
    }

    public SOAPEnvelope getEnvelope() throws SOAPException {

        // If there is no SOAP envelope already created, then create
        // one from a source if one exists. If there is a newer source
        // then use that source.

        if (sourceWasSet)
              sourceWasSet = false;

        lookForEnvelope();
        if (envelope != null) {
            if (source != null) { // there's a newer source, use it
                document.removeChild(envelope);
                envelope = createEnvelopeFromSource();
            }
        } else if (source != null) {
            envelope = createEnvelopeFromSource();
        } else {
            envelope = createEmptyEnvelope(null);
            document.insertBefore(envelope, null);
        }
        return envelope;
    }

    protected void lookForEnvelope() throws SOAPException {
        Element envelopeChildElement = document.doGetDocumentElement();
        if (envelopeChildElement == null || envelopeChildElement instanceof Envelope) {
            envelope = (EnvelopeImpl) envelopeChildElement;
        } else if (!(envelopeChildElement instanceof ElementImpl)) {
            log.severe("SAAJ0512.soap.incorrect.factory.used");
            throw new SOAPExceptionImpl("Unable to create envelope: incorrect factory used during tree construction");
        } else {
            ElementImpl soapElement = (ElementImpl) envelopeChildElement;
            if (soapElement.getLocalName().equalsIgnoreCase("Envelope")) {
                String prefix = soapElement.getPrefix();
                String uri = (prefix == null) ? soapElement.getNamespaceURI() : soapElement.getNamespaceURI(prefix);
                if(!uri.equals(NameImpl.SOAP11_NAMESPACE) && !uri.equals(NameImpl.SOAP12_NAMESPACE)) {
                    log.severe("SAAJ0513.soap.unknown.ns");
                    throw new SOAPVersionMismatchException("Unable to create envelope from given source because the namespace was not recognized");
                }
            } else {
                log.severe("SAAJ0514.soap.root.elem.not.named.envelope");
                throw new SOAPExceptionImpl(
                    "Unable to create envelope from given source because the root element is not named \"Envelope\"");
            }
        }
    }

    public void removeAllMimeHeaders() {
        headers.removeAllHeaders();
    }

    public void removeMimeHeader(String header) {
        headers.removeHeader(header);
    }

    public String[] getMimeHeader(String name) {
        return headers.getHeader(name);
    }

    public void setMimeHeader(String name, String value) {
        headers.setHeader(name, value);
    }

    public void addMimeHeader(String name, String value) {
        headers.addHeader(name, value);
    }

    public Iterator getAllMimeHeaders() {
        return headers.getAllHeaders();
    }

    public Iterator getMatchingMimeHeaders(String[] names) {
        return headers.getMatchingHeaders(names);
    }

    public Iterator getNonMatchingMimeHeaders(String[] names) {
        return headers.getNonMatchingHeaders(names);
    }

    public Source getContent() throws SOAPException {
        if (source != null) {
            InputStream bis = null;
            if (source instanceof JAXMStreamSource) {
                StreamSource streamSource = (StreamSource)source;
                bis = streamSource.getInputStream();
            } else if (FastInfosetReflection.isFastInfosetSource(source)) {
                // FastInfosetSource inherits from SAXSource
                SAXSource saxSource = (SAXSource)source;
                bis = saxSource.getInputSource().getByteStream();
            }

            if (bis != null) {
                try {
                    bis.reset();
                } catch (IOException e) {
                    /* This exception will never be thrown.
                     *
                     * The setContent method will modify the source
                     * if StreamSource to JAXMStreamSource, that uses
                     * a ByteInputStream, and for a FastInfosetSource will
                     * replace the InputStream with a ByteInputStream.
                     */
                }
            }
            return source;
        }

        return ((Envelope) getEnvelope()).getContent();
    }

    public void setContent(Source source) throws SOAPException {
        try {
            if (source instanceof StreamSource) {
                InputStream is = ((StreamSource) source).getInputStream();
                Reader rdr = ((StreamSource) source).getReader();

                if (is != null) {
                    this.source = new JAXMStreamSource(is);
                } else if (rdr != null) {
                    this.source = new JAXMStreamSource(rdr);
                } else {
                    log.severe("SAAJ0544.soap.no.valid.reader.for.src");
                    throw new SOAPExceptionImpl("Source does not have a valid Reader or InputStream");
                }
            }
            else if (FastInfosetReflection.isFastInfosetSource(source)) {
                // InputStream is = source.getInputStream()
                InputStream is = FastInfosetReflection.FastInfosetSource_getInputStream(source);

                /*
                 * Underlying stream must be ByteInputStream for getContentAsStream(). We pay the
                 * cost of copying the underlying bytes here to avoid multiple copies every time
                 * getBytes() is called on a ByteInputStream.
                 */
                if (!(is instanceof ByteInputStream)) {
                    ByteOutputStream bout = new ByteOutputStream();
                    bout.write(is);

                    // source.setInputStream(new ByteInputStream(...))
                    FastInfosetReflection.FastInfosetSource_setInputStream(
                        source, bout.newInputStream());
                }
                this.source = source;
            }
            else {
                this.source = source;
            }
            sourceWasSet = true;
        }
        catch (Exception ex) {
            ex.printStackTrace();

            log.severe("SAAJ0545.soap.cannot.set.src.for.part");
            throw new SOAPExceptionImpl(
            "Error setting the source for SOAPPart: " + ex.getMessage());
        }
    }

    public ByteInputStream getContentAsStream() throws IOException {
        if (source != null) {
            InputStream is = null;

            // Allow message to be transcode if so requested
            if (source instanceof StreamSource && !isFastInfoset()) {
                is = ((StreamSource) source).getInputStream();
            }
            else if (FastInfosetReflection.isFastInfosetSource(source) &&
                isFastInfoset())
            {
                try {
                    // InputStream is = source.getInputStream()
                    is = FastInfosetReflection.FastInfosetSource_getInputStream(source);
                }
                catch (Exception e) {
                    throw new IOException(e.toString());
                }
            }

            if (is != null) {
                if (!(is instanceof ByteInputStream)) {
                    log.severe("SAAJ0546.soap.stream.incorrect.type");
                    throw new IOException("Internal error: stream not of the right type");
                }
                return (ByteInputStream) is;
            }
            // need to do something here for reader...
            // for now we'll see if we can fallback...
        }

        ByteOutputStream b = new ByteOutputStream();

        Envelope env = null;

        try {
            env = (Envelope) getEnvelope();
            env.output(b, isFastInfoset());
        }
        catch (SOAPException soapException) {
            log.severe("SAAJ0547.soap.cannot.externalize");
            throw new SOAPIOException(
            "SOAP exception while trying to externalize: ",
            soapException);
        }

        return b.newInputStream();
    }

    MimeBodyPart getMimePart() throws SOAPException {
        try {
            MimeBodyPart headerEnvelope = new MimeBodyPart();

            headerEnvelope.setDataHandler(getDataHandler());
            AttachmentPartImpl.copyMimeHeaders(headers, headerEnvelope);

            return headerEnvelope;
        } catch (SOAPException ex) {
            throw ex;
        } catch (Exception ex) {
            log.severe("SAAJ0548.soap.cannot.externalize.hdr");
            throw new SOAPExceptionImpl("Unable to externalize header", ex);
        }
    }

    MimeHeaders getMimeHeaders() {
        return headers;
    }

    DataHandler getDataHandler() {
        DataSource ds = new DataSource() {
            public OutputStream getOutputStream() throws IOException {
                throw new IOException("Illegal Operation");
            }

            public String getContentType() {
                return getContentTypeString();
            }

            public String getName() {
                return getContentId();
            }

            public InputStream getInputStream() throws IOException {
                return getContentAsStream();
            }
        };
        return new DataHandler(ds);
    }

    public SOAPDocumentImpl getDocument() {
        handleNewSource();
        return document;
    }

    public SOAPPartImpl getSOAPPart() {
        return this;
    }

    public DocumentType getDoctype() {
        return document.getDoctype();
    }

    // Forward all of these calls to the document to ensure that they work the
    // same way whether they are called from here or directly from the document.
    // If the document needs any help from this SOAPPart then
    // Make it use a call-back as in doGetDocumentElement() below
    public DOMImplementation getImplementation() {
        return document.getImplementation();
    }

    public Element getDocumentElement() {
        // If there is no SOAP envelope already created, then create
        // one from a source if one exists. If there is a newer source
        // then use that source.
        try {
            getEnvelope();
        } catch (SOAPException e) {
        }
        return document.getDocumentElement();
    }

    protected void doGetDocumentElement() {
        handleNewSource();
        try {
            lookForEnvelope();
        } catch (SOAPException e) {
        }
    }

    public Element createElement(String tagName) throws DOMException {
        return document.createElement(tagName);
    }

    public DocumentFragment createDocumentFragment() {
        return document.createDocumentFragment();
    }

    public org.w3c.dom.Text createTextNode(String data) {
        return document.createTextNode(data);
    }

    public Comment createComment(String data) {
        return document.createComment(data);
    }

    public CDATASection createCDATASection(String data) throws DOMException {
        return document.createCDATASection(data);
    }

    public ProcessingInstruction createProcessingInstruction(
    String target,
    String data)
    throws DOMException {
        return document.createProcessingInstruction(target, data);
    }

    public Attr createAttribute(String name) throws DOMException {
        return document.createAttribute(name);
    }

    public EntityReference createEntityReference(String name)
    throws DOMException {
        return document.createEntityReference(name);
    }

    public NodeList getElementsByTagName(String tagname) {
        handleNewSource();
        return document.getElementsByTagName(tagname);
    }

    public org.w3c.dom.Node importNode(
        org.w3c.dom.Node importedNode,
        boolean deep)
        throws DOMException {
        handleNewSource();
        return document.importNode(importedNode, deep);
    }

    public Element createElementNS(String namespaceURI, String qualifiedName)
    throws DOMException {
        return document.createElementNS(namespaceURI, qualifiedName);
    }

    public Attr createAttributeNS(String namespaceURI, String qualifiedName)
    throws DOMException {
        return document.createAttributeNS(namespaceURI, qualifiedName);
    }

    public NodeList getElementsByTagNameNS(
        String namespaceURI,
        String localName) {
        handleNewSource();
        return document.getElementsByTagNameNS(namespaceURI, localName);
    }

    public Element getElementById(String elementId) {
        handleNewSource();
        return document.getElementById(elementId);
    }
    public org.w3c.dom.Node appendChild(org.w3c.dom.Node newChild)
        throws DOMException {
        handleNewSource();
        return document.appendChild(newChild);
    }

    public org.w3c.dom.Node cloneNode(boolean deep) {
        handleNewSource();
        return document.cloneNode(deep);
    }

    protected SOAPPartImpl doCloneNode() {
        handleNewSource();
        SOAPPartImpl newSoapPart = duplicateType();

        newSoapPart.headers = MimeHeadersUtil.copy(this.headers);
        newSoapPart.source = this.source;
        return newSoapPart;
    }

    public NamedNodeMap getAttributes() {
        return document.getAttributes();
    }

    public NodeList getChildNodes() {
        handleNewSource();
        return document.getChildNodes();
    }

    public org.w3c.dom.Node getFirstChild() {
        handleNewSource();
        return document.getFirstChild();
    }

    public org.w3c.dom.Node getLastChild() {
        handleNewSource();
        return document.getLastChild();
    }

    public String getLocalName() {
        return document.getLocalName();
    }

    public String getNamespaceURI() {
        return document.getNamespaceURI();
    }

    public org.w3c.dom.Node getNextSibling() {
        handleNewSource();
        return document.getNextSibling();
    }

    public String getNodeName() {
        return document.getNodeName();
    }

    public short getNodeType() {
        return document.getNodeType();
    }

    public String getNodeValue() throws DOMException {
        return document.getNodeValue();
    }

    public Document getOwnerDocument() {
        return document.getOwnerDocument();
    }

    public org.w3c.dom.Node getParentNode() {
        return document.getParentNode();
    }

    public String getPrefix() {
        return document.getPrefix();
    }

    public org.w3c.dom.Node getPreviousSibling() {
        return document.getPreviousSibling();
    }

    public boolean hasAttributes() {
        return document.hasAttributes();
    }

    public boolean hasChildNodes() {
        handleNewSource();
        return document.hasChildNodes();
    }

    public org.w3c.dom.Node insertBefore(
        org.w3c.dom.Node arg0,
        org.w3c.dom.Node arg1)
        throws DOMException {
        handleNewSource();
        return document.insertBefore(arg0, arg1);
    }

    public boolean isSupported(String arg0, String arg1) {
        return document.isSupported(arg0, arg1);
    }

    public void normalize() {
        handleNewSource();
        document.normalize();
    }

    public org.w3c.dom.Node removeChild(org.w3c.dom.Node arg0)
        throws DOMException {
        handleNewSource();
        return document.removeChild(arg0);
    }

    public org.w3c.dom.Node replaceChild(
        org.w3c.dom.Node arg0,
        org.w3c.dom.Node arg1)
        throws DOMException {
        handleNewSource();
        return document.replaceChild(arg0, arg1);
    }

    public void setNodeValue(String arg0) throws DOMException {
        document.setNodeValue(arg0);
    }

    public void setPrefix(String arg0) throws DOMException {
        document.setPrefix(arg0);
    }

    private void handleNewSource() {
        if (sourceWasSet) {
         // There is a newer source use that source.
         try {
             getEnvelope();
         } catch (SOAPException e) {
         }
      }
    }

    protected XMLDeclarationParser lookForXmlDecl() throws SOAPException {
        if ((source != null) && (source instanceof StreamSource)) {

            Reader reader = null;

            InputStream inputStream = ((StreamSource) source).getInputStream();
            if (inputStream != null) {
                if (sourceCharsetEncoding == null) {
                    reader = new InputStreamReader(inputStream);
                } else {
                    try {
                        reader =
                            new InputStreamReader(
                                inputStream, sourceCharsetEncoding);
                    } catch (UnsupportedEncodingException uee) {
                        log.log(
                            Level.SEVERE,
                            "SAAJ0551.soap.unsupported.encoding",
                            new Object[] {sourceCharsetEncoding});
                        throw new SOAPExceptionImpl(
                            "Unsupported encoding " + sourceCharsetEncoding,
                            uee);
                    }
                }
            } else {
                reader = ((StreamSource) source).getReader();
            }
            if (reader != null) {
                PushbackReader pushbackReader =
                    new PushbackReader(reader, 4096); //some size to unread <?xml ....?>
                XMLDeclarationParser ev =
                        new XMLDeclarationParser(pushbackReader);
                try {
                    ev.parse();
                } catch (Exception e) {
                    log.log(
                        Level.SEVERE,
                        "SAAJ0552.soap.xml.decl.parsing.failed");
                    throw new SOAPExceptionImpl(
                        "XML declaration parsing failed", e);
                }
                String xmlDecl = ev.getXmlDeclaration();
                if ((xmlDecl != null) && (xmlDecl.length() > 0))
                    this.omitXmlDecl = false;
                return ev;
            }
        }
        return null;
    }

    public void setSourceCharsetEncoding(String charset) {
        this.sourceCharsetEncoding = charset;
    }

    public org.w3c.dom.Node renameNode(org.w3c.dom.Node n, String namespaceURI, String qualifiedName)
        throws DOMException {
        handleNewSource();
        return document.renameNode(n, namespaceURI, qualifiedName);
    }

    public void normalizeDocument() {
        document.normalizeDocument();
    }

    public DOMConfiguration getDomConfig() {
        return document.getDomConfig();
    }

    public org.w3c.dom.Node adoptNode(org.w3c.dom.Node source) throws DOMException {
        handleNewSource();
        return document.adoptNode(source);
    }

    public void setDocumentURI(String documentURI) {
        document.setDocumentURI(documentURI);
    }

    public String getDocumentURI() {
        return document.getDocumentURI();
    }

    public void  setStrictErrorChecking(boolean strictErrorChecking) {
        document.setStrictErrorChecking(strictErrorChecking);
    }

    public String getInputEncoding() {
        return document.getInputEncoding();
    }

    public String getXmlEncoding() {
        return document.getXmlEncoding();
    }

    public boolean getXmlStandalone() {
        return document.getXmlStandalone();
    }

    public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
        document.setXmlStandalone(xmlStandalone);
    }

    public String getXmlVersion() {
        return document.getXmlVersion();
    }

    public void setXmlVersion(String xmlVersion) throws DOMException {
        document.setXmlVersion(xmlVersion);
    }

    public boolean  getStrictErrorChecking() {
        return document.getStrictErrorChecking();
    }

    // DOM L3 methods from org.w3c.dom.Node
    public String getBaseURI() {
        return document.getBaseURI();
    }

    public short compareDocumentPosition(org.w3c.dom.Node other)
                              throws DOMException {
        return document.compareDocumentPosition(other);
    }

    public String getTextContent()
                      throws DOMException {
        return document.getTextContent();
    }

    public void setTextContent(String textContent) throws DOMException {
         document.setTextContent(textContent);
    }

    public boolean isSameNode(org.w3c.dom.Node other) {
        return document.isSameNode(other);
    }

    public String lookupPrefix(String namespaceURI) {
        return document.lookupPrefix(namespaceURI);
    }

    public boolean isDefaultNamespace(String namespaceURI) {
        return document.isDefaultNamespace(namespaceURI);
    }

    public String lookupNamespaceURI(String prefix) {
        return document.lookupNamespaceURI(prefix);
    }

    public boolean isEqualNode(org.w3c.dom.Node arg) {
        return document.isEqualNode(arg);
    }

    public Object getFeature(String feature,
                  String version) {
        return  document.getFeature(feature,version);
    }

    public Object setUserData(String key,
                   Object data,
                  UserDataHandler handler) {
        return document.setUserData(key, data, handler);
    }

    public Object getUserData(String key) {
        return document.getUserData(key);
    }

    public void recycleNode() {
        // Nothing seems to be required to be done here
    }

    public String getValue() {
        return null;
    }

    public void setValue(String value) {
        log.severe("SAAJ0571.soappart.setValue.not.defined");
        throw new IllegalStateException("Setting value of a soap part is not defined");
    }

    public void setParentElement(SOAPElement parent) throws SOAPException {
        log.severe("SAAJ0570.soappart.parent.element.not.defined");
        throw new SOAPExceptionImpl("The parent element of a soap part is not defined");
    }

    public SOAPElement getParentElement() {
        return null;
    }

    public void detachNode() {
        // Nothing seems to be required to be done here
    }
}
