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

package com.sun.xml.internal.messaging.saaj.soap;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeBodyPart;
import com.sun.xml.internal.messaging.saaj.soap.impl.ElementImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.EnvelopeImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.messaging.saaj.util.FastInfosetReflection;
import com.sun.xml.internal.messaging.saaj.util.JAXMStreamSource;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import com.sun.xml.internal.messaging.saaj.util.MimeHeadersUtil;
import com.sun.xml.internal.messaging.saaj.util.SAAJUtil;
import com.sun.xml.internal.messaging.saaj.util.XMLDeclarationParser;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.UserDataHandler;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.soap.MimeHeader;

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

    static final boolean lazyContentLength;
    static {
            lazyContentLength = SAAJUtil.getSystemBoolean("saaj.lazy.contentlength");
    }

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

    @Override
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
            document.insertBefore(((EnvelopeImpl) envelope).getDomElement(), null);
        }
        return envelope;
    }

    protected void lookForEnvelope() throws SOAPException {
        Element envelopeChildElement = document.doGetDocumentElement();
        org.w3c.dom.Node soapEnvelope = document.findIfPresent(envelopeChildElement);
        if (soapEnvelope == null || soapEnvelope instanceof Envelope) {
            envelope = (EnvelopeImpl) soapEnvelope;
        } else if (document.find(envelopeChildElement) == null) {
            log.severe("SAAJ0512.soap.incorrect.factory.used");
            throw new SOAPExceptionImpl("Unable to create envelope: incorrect factory used during tree construction");
        } else {
            ElementImpl soapElement = (ElementImpl) document.find(envelopeChildElement);
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

    @Override
    public void removeAllMimeHeaders() {
        headers.removeAllHeaders();
    }

    @Override
    public void removeMimeHeader(String header) {
        headers.removeHeader(header);
    }

    @Override
    public String[] getMimeHeader(String name) {
        return headers.getHeader(name);
    }

    @Override
    public void setMimeHeader(String name, String value) {
        headers.setHeader(name, value);
    }

    @Override
    public void addMimeHeader(String name, String value) {
        headers.addHeader(name, value);
    }

    @Override
    public Iterator<MimeHeader> getAllMimeHeaders() {
        return headers.getAllHeaders();
    }

    @Override
    public Iterator<MimeHeader> getMatchingMimeHeaders(String[] names) {
        return headers.getMatchingHeaders(names);
    }

    @Override
    public Iterator<MimeHeader> getNonMatchingMimeHeaders(String[] names) {
        return headers.getNonMatchingHeaders(names);
    }

    @Override
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

    @Override
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
                    ByteOutputStream bout = null;
                    try {
                        bout = new ByteOutputStream();
                        bout.write(is);

                        // source.setInputStream(new ByteInputStream(...))
                        FastInfosetReflection.FastInfosetSource_setInputStream(
                                source, bout.newInputStream());
                    } finally {
                        if (bout != null)
                            bout.close();
                    }
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

    public InputStream getContentAsStream() throws IOException {
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
                if (lazyContentLength) {
                    return is;
                }
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
            @Override
            public OutputStream getOutputStream() throws IOException {
                throw new IOException("Illegal Operation");
            }

            @Override
            public String getContentType() {
                return getContentTypeString();
            }

            @Override
            public String getName() {
                return getContentId();
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return getContentAsStream();
            }
        };
        return new DataHandler(ds);
    }

    @Override
    public SOAPDocumentImpl getDocument() {
        handleNewSource();
        return document;
    }

    @Override
    public SOAPPartImpl getSOAPPart() {
        return this;
    }

    @Override
    public DocumentType getDoctype() {
        return document.getDoctype();
    }

    // Forward all of these calls to the document to ensure that they work the
    // same way whether they are called from here or directly from the document.
    // If the document needs any help from this SOAPPart then
    // Make it use a call-back as in doGetDocumentElement() below
    @Override
    public DOMImplementation getImplementation() {
        return document.getImplementation();
    }

    @Override
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

    @Override
    public Element createElement(String tagName) throws DOMException {
        return document.createElement(tagName);
    }

    @Override
    public DocumentFragment createDocumentFragment() {
        return document.createDocumentFragment();
    }

    @Override
    public org.w3c.dom.Text createTextNode(String data) {
        return document.createTextNode(data);
    }

    @Override
    public Comment createComment(String data) {
        return document.createComment(data);
    }

    @Override
    public CDATASection createCDATASection(String data) throws DOMException {
        return document.createCDATASection(data);
    }

    @Override
    public ProcessingInstruction createProcessingInstruction(
    String target,
    String data)
    throws DOMException {
        return document.createProcessingInstruction(target, data);
    }

    @Override
    public Attr createAttribute(String name) throws DOMException {
        return document.createAttribute(name);
    }

    @Override
    public EntityReference createEntityReference(String name)
    throws DOMException {
        return document.createEntityReference(name);
    }

    @Override
    public NodeList getElementsByTagName(String tagname) {
        handleNewSource();
        return document.getElementsByTagName(tagname);
    }

    @Override
    public org.w3c.dom.Node importNode(
        org.w3c.dom.Node importedNode,
        boolean deep)
        throws DOMException {
        handleNewSource();
        return document.importNode(importedNode, deep);
    }

    @Override
    public Element createElementNS(String namespaceURI, String qualifiedName)
    throws DOMException {
        return document.createElementNS(namespaceURI, qualifiedName);
    }

    @Override
    public Attr createAttributeNS(String namespaceURI, String qualifiedName)
    throws DOMException {
        return document.createAttributeNS(namespaceURI, qualifiedName);
    }

    @Override
    public NodeList getElementsByTagNameNS(
        String namespaceURI,
        String localName) {
        handleNewSource();
        return document.getElementsByTagNameNS(namespaceURI, localName);
    }

    @Override
    public Element getElementById(String elementId) {
        handleNewSource();
        return document.getElementById(elementId);
    }
    @Override
    public org.w3c.dom.Node appendChild(org.w3c.dom.Node newChild)
        throws DOMException {
        handleNewSource();
        return document.appendChild(newChild);
    }

    @Override
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

    @Override
    public NamedNodeMap getAttributes() {
        return document.getDomDocument().getAttributes();
    }

    @Override
    public NodeList getChildNodes() {
        handleNewSource();
        return document.getChildNodes();
    }

    @Override
    public org.w3c.dom.Node getFirstChild() {
        handleNewSource();
        return document.getFirstChild();
    }

    @Override
    public org.w3c.dom.Node getLastChild() {
        handleNewSource();
        return document.getLastChild();
    }

    @Override
    public String getLocalName() {
        return document.getDomDocument().getLocalName();
    }

    @Override
    public String getNamespaceURI() {
        return document.getDomDocument().getNamespaceURI();
    }

    @Override
    public org.w3c.dom.Node getNextSibling() {
        handleNewSource();
        return document.getNextSibling();
    }

    @Override
    public String getNodeName() {
        return document.getDomDocument().getNodeName();
    }

    @Override
    public short getNodeType() {
        return document.getDomDocument().getNodeType();
    }

    @Override
    public String getNodeValue() throws DOMException {
        return document.getNodeValue();
    }

    @Override
    public Document getOwnerDocument() {
        return document;
    }

    @Override
    public org.w3c.dom.Node getParentNode() {
        return document.getDomDocument().getParentNode();
    }

    @Override
    public String getPrefix() {
        return document.getDomDocument().getPrefix();
    }

    @Override
    public org.w3c.dom.Node getPreviousSibling() {
        return document.getDomDocument().getPreviousSibling();
    }

    @Override
    public boolean hasAttributes() {
        return document.getDomDocument().hasAttributes();
    }

    @Override
    public boolean hasChildNodes() {
        handleNewSource();
        return document.hasChildNodes();
    }

    @Override
    public org.w3c.dom.Node insertBefore(
        org.w3c.dom.Node arg0,
        org.w3c.dom.Node arg1)
        throws DOMException {
        handleNewSource();
        return document.insertBefore(arg0, arg1);
    }

    @Override
    public boolean isSupported(String arg0, String arg1) {
        return document.getDomDocument().isSupported(arg0, arg1);
    }

    @Override
    public void normalize() {
        handleNewSource();
        document.normalize();
    }

    @Override
    public org.w3c.dom.Node removeChild(org.w3c.dom.Node arg0)
        throws DOMException {
        handleNewSource();
        return document.removeChild(arg0);
    }

    @Override
    public org.w3c.dom.Node replaceChild(
        org.w3c.dom.Node arg0,
        org.w3c.dom.Node arg1)
        throws DOMException {
        handleNewSource();
        return document.replaceChild(arg0, arg1);
    }

    @Override
    public void setNodeValue(String arg0) throws DOMException {
        document.setNodeValue(arg0);
    }

    @Override
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
                if (getSourceCharsetEncoding() == null) {
                    reader = new InputStreamReader(inputStream);
                } else {
                    try {
                        reader =
                            new InputStreamReader(
                                inputStream, getSourceCharsetEncoding());
                    } catch (UnsupportedEncodingException uee) {
                        log.log(
                            Level.SEVERE,
                            "SAAJ0551.soap.unsupported.encoding",
                            new Object[] {getSourceCharsetEncoding()});
                        throw new SOAPExceptionImpl(
                            "Unsupported encoding " + getSourceCharsetEncoding(),
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
                if ((xmlDecl != null) && (xmlDecl.length() > 0)) {
                    this.omitXmlDecl = false;
                }
                if (lazyContentLength) {
                    source = new StreamSource(pushbackReader);
                }
                return ev;
            }
        } else if ((source != null) && (source instanceof DOMSource)) {
           //TODO: A Domsource maynot contain XMLDecl ?.
        }
        return null;
    }

    public void setSourceCharsetEncoding(String charset) {
        this.sourceCharsetEncoding = charset;
    }

    @Override
    public org.w3c.dom.Node renameNode(org.w3c.dom.Node n, String namespaceURI, String qualifiedName)
        throws DOMException {
        handleNewSource();
        return document.renameNode(n, namespaceURI, qualifiedName);
    }

    @Override
    public void normalizeDocument() {
        document.normalizeDocument();
    }

    @Override
    public DOMConfiguration getDomConfig() {
        return document.getDomDocument().getDomConfig();
    }

    @Override
    public org.w3c.dom.Node adoptNode(org.w3c.dom.Node source) throws DOMException {
        handleNewSource();
        return document.adoptNode(source);
    }

    @Override
    public void setDocumentURI(String documentURI) {
        document.setDocumentURI(documentURI);
    }

    @Override
    public String getDocumentURI() {
        return document.getDomDocument().getDocumentURI();
    }

    @Override
    public void  setStrictErrorChecking(boolean strictErrorChecking) {
        document.setStrictErrorChecking(strictErrorChecking);
    }

    @Override
    public String getInputEncoding() {
        return document.getDomDocument().getInputEncoding();
    }

    @Override
    public String getXmlEncoding() {
        return document.getDomDocument().getXmlEncoding();
    }

    @Override
    public boolean getXmlStandalone() {
        return document.getDomDocument().getXmlStandalone();
    }

    @Override
    public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
        document.setXmlStandalone(xmlStandalone);
    }

    @Override
    public String getXmlVersion() {
        return document.getDomDocument().getXmlVersion();
    }

    @Override
    public void setXmlVersion(String xmlVersion) throws DOMException {
        document.setXmlVersion(xmlVersion);
    }

    @Override
    public boolean  getStrictErrorChecking() {
        return document.getDomDocument().getStrictErrorChecking();
    }

    // DOM L3 methods from org.w3c.dom.Node
    @Override
    public String getBaseURI() {
        return document.getDomDocument().getBaseURI();
    }

    @Override
    public short compareDocumentPosition(org.w3c.dom.Node other)
                              throws DOMException {
        return document.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent()
                      throws DOMException {
        return document.getTextContent();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
         document.setTextContent(textContent);
    }

    @Override
    public boolean isSameNode(org.w3c.dom.Node other) {
        return document.isSameNode(other);
    }

    @Override
    public String lookupPrefix(String namespaceURI) {
        return document.getDomDocument().lookupPrefix(namespaceURI);
    }

    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        return document.isDefaultNamespace(namespaceURI);
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
        return document.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(org.w3c.dom.Node arg) {
        return document.getDomDocument().isEqualNode(arg);
    }

    @Override
    public Object getFeature(String feature,
                  String version) {
        return  document.getFeature(feature,version);
    }

    @Override
    public Object setUserData(String key,
                   Object data,
                  UserDataHandler handler) {
        return document.setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(String key) {
        return document.getDomDocument().getUserData(key);
    }

    @Override
    public void recycleNode() {
        // Nothing seems to be required to be done here
    }

    @Override
    public String getValue() {
        return null;
    }

    @Override
    public void setValue(String value) {
        log.severe("SAAJ0571.soappart.setValue.not.defined");
        throw new IllegalStateException("Setting value of a soap part is not defined");
    }

    @Override
    public void setParentElement(SOAPElement parent) throws SOAPException {
        log.severe("SAAJ0570.soappart.parent.element.not.defined");
        throw new SOAPExceptionImpl("The parent element of a soap part is not defined");
    }

    @Override
    public SOAPElement getParentElement() {
        return null;
    }

    @Override
    public void detachNode() {
        // Nothing seems to be required to be done here
    }

    public String getSourceCharsetEncoding() {
        return sourceCharsetEncoding;
    }

    public abstract String getSOAPNamespace();
}
