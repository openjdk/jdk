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

package com.sun.xml.internal.ws.util.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.api.server.DocumentAddressResolver;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.api.server.SDDocumentSource;
import com.sun.xml.internal.ws.developer.SchemaValidationFeature;
import com.sun.xml.internal.ws.developer.ValidationErrorHandler;
import com.sun.xml.internal.ws.server.SDDocumentImpl;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.wsdl.SDDocumentResolver;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import org.w3c.dom.*;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.xml.internal.ws.util.xml.XmlUtil.allowExternalAccess;

/**
 * {@link Tube} that does the schema validation.
 *
 * @author Jitendra Kotamraju
 */
public abstract class AbstractSchemaValidationTube extends AbstractFilterTubeImpl {

    private static final Logger LOGGER = Logger.getLogger(AbstractSchemaValidationTube.class.getName());

    protected final WSBinding binding;
    protected final SchemaValidationFeature feature;
    protected final DocumentAddressResolver resolver = new ValidationDocumentAddressResolver();
    protected final SchemaFactory sf;

    public AbstractSchemaValidationTube(WSBinding binding, Tube next) {
        super(next);
        this.binding = binding;
        feature = binding.getFeature(SchemaValidationFeature.class);
        sf = allowExternalAccess(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), "all", false);
    }

    protected AbstractSchemaValidationTube(AbstractSchemaValidationTube that, TubeCloner cloner) {
        super(that, cloner);
        this.binding = that.binding;
        this.feature = that.feature;
        this.sf = that.sf;
    }

    protected abstract Validator getValidator();

    protected abstract boolean isNoValidation();

    private static class ValidationDocumentAddressResolver implements DocumentAddressResolver {

        @Nullable
        @Override
        public String getRelativeAddressFor(@NotNull SDDocument current, @NotNull SDDocument referenced) {
            LOGGER.log(Level.FINE, "Current = {0} resolved relative={1}", new Object[]{current.getURL(), referenced.getURL()});
            return referenced.getURL().toExternalForm();
        }
    }

    private Document createDOM(SDDocument doc) {
        // Get infoset
        ByteArrayBuffer bab = new ByteArrayBuffer();
        try {
            doc.writeTo(null, resolver, bab);
        } catch (IOException ioe) {
            throw new WebServiceException(ioe);
        }

        // Convert infoset to DOM
        Transformer trans = XmlUtil.newTransformer();
        Source source = new StreamSource(bab.newInputStream(), null); //doc.getURL().toExternalForm());
        DOMResult result = new DOMResult();
        try {
            trans.transform(source, result);
        } catch(TransformerException te) {
            throw new WebServiceException(te);
        }
        return (Document)result.getNode();
    }

    protected class MetadataResolverImpl implements SDDocumentResolver, LSResourceResolver {

        // systemID --> SDDocument
        final Map<String, SDDocument> docs = new HashMap<String, SDDocument>();

        // targetnamespace --> SDDocument
        final Map<String, SDDocument> nsMapping = new HashMap<String, SDDocument>();

        public MetadataResolverImpl() {
        }

        public MetadataResolverImpl(Iterable<SDDocument> it) {
            for(SDDocument doc : it) {
                if (doc.isSchema()) {
                    docs.put(doc.getURL().toExternalForm(), doc);
                    nsMapping.put(((SDDocument.Schema)doc).getTargetNamespace(), doc);
                }
            }
        }

        void addSchema(Source schema) {
            assert schema.getSystemId() != null;

            String systemId = schema.getSystemId();
            try {
                XMLStreamBufferResult xsbr = XmlUtil.identityTransform(schema, new XMLStreamBufferResult());
                SDDocumentSource sds = SDDocumentSource.create(new URL(systemId), xsbr.getXMLStreamBuffer());
                SDDocument sdoc = SDDocumentImpl.create(sds, new QName(""), new QName(""));
                docs.put(systemId, sdoc);
                nsMapping.put(((SDDocument.Schema)sdoc).getTargetNamespace(), sdoc);
            } catch(Exception ex) {
                LOGGER.log(Level.WARNING, "Exception in adding schemas to resolver", ex);
            }
        }

        void addSchemas(Collection<? extends Source> schemas) {
            for(Source src :  schemas) {
                addSchema(src);
            }
        }

        @Override
        public SDDocument resolve(String systemId) {
            SDDocument sdi = docs.get(systemId);
            if (sdi == null) {
                SDDocumentSource sds;
                try {
                    sds = SDDocumentSource.create(new URL(systemId));
                } catch(MalformedURLException e) {
                    throw new WebServiceException(e);
                }
                sdi = SDDocumentImpl.create(sds, new QName(""), new QName(""));
                docs.put(systemId, sdi);
            }
            return sdi;
        }

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, final String systemId, final String baseURI) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "type={0} namespaceURI={1} publicId={2} systemId={3} baseURI={4}", new Object[]{type, namespaceURI, publicId, systemId, baseURI});
            }
            try {
                final SDDocument doc;
                if (systemId == null) {
                    doc = nsMapping.get(namespaceURI);
                } else {
                    URI rel = (baseURI != null)
                        ? new URI(baseURI).resolve(systemId)
                        : new URI(systemId);
                    doc = docs.get(rel.toString());
                }
                if (doc != null) {
                    return new LSInput() {

                        @Override
                        public Reader getCharacterStream() {
                            return null;
                        }

                        @Override
                        public void setCharacterStream(Reader characterStream) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public InputStream getByteStream() {
                            ByteArrayBuffer bab = new ByteArrayBuffer();
                            try {
                                doc.writeTo(null, resolver, bab);
                            } catch (IOException ioe) {
                                throw new WebServiceException(ioe);
                            }
                            return bab.newInputStream();
                        }

                        @Override
                        public void setByteStream(InputStream byteStream) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public String getStringData() {
                            return null;
                        }

                        @Override
                        public void setStringData(String stringData) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public String getSystemId() {
                            return doc.getURL().toExternalForm();
                        }

                        @Override
                        public void setSystemId(String systemId) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public String getPublicId() {
                            return null;
                        }

                        @Override
                        public void setPublicId(String publicId) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public String getBaseURI() {
                            return doc.getURL().toExternalForm();
                        }

                        @Override
                        public void setBaseURI(String baseURI) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public String getEncoding() {
                            return null;
                        }

                        @Override
                        public void setEncoding(String encoding) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public boolean getCertifiedText() {
                            return false;
                        }

                        @Override
                        public void setCertifiedText(boolean certifiedText) {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            } catch(Exception e) {
                LOGGER.log(Level.WARNING, "Exception in LSResourceResolver impl", e);
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Don''t know about systemId={0} baseURI={1}", new Object[]{systemId, baseURI});
            }
            return null;
        }

    }

    private void updateMultiSchemaForTns(String tns, String systemId, Map<String, List<String>> schemas) {
        List<String> docIdList = schemas.get(tns);
        if (docIdList == null) {
            docIdList = new ArrayList<String>();
            schemas.put(tns, docIdList);
        }
        docIdList.add(systemId);
    }

    /*
     * Using the following algorithm described in the xerces discussion thread:
     *
     * "If you're synthesizing schema documents to glue together the ones in
     * the WSDL then you may not even need to use "honour-all-schemaLocations".
     * Create a schema document for each namespace with <xs:include>s
     * (for each schema document in the WSDL with that target namespace)
     * and then combine those together with <xs:import>s for each of those
     * namespaces in a "master" schema document.
     *
     * That should work with any schema processor, not just those which
     * honour multiple imports for the same namespace."
     */
    protected Source[] getSchemaSources(Iterable<SDDocument> docs, MetadataResolverImpl mdresolver) {
        // All schema fragments in WSDLs are put inlinedSchemas
        // systemID --> DOMSource
        Map<String, DOMSource> inlinedSchemas = new HashMap<String, DOMSource>();

        // Consolidates all the schemas(inlined and external) for a tns
        // tns --> list of systemId
        Map<String, List<String>> multiSchemaForTns = new HashMap<String, List<String>>();

        for(SDDocument sdoc: docs) {
            if (sdoc.isWSDL()) {
                Document dom = createDOM(sdoc);
                // Get xsd:schema node from WSDL's DOM
                addSchemaFragmentSource(dom, sdoc.getURL().toExternalForm(), inlinedSchemas);
            } else if (sdoc.isSchema()) {
                updateMultiSchemaForTns(((SDDocument.Schema)sdoc).getTargetNamespace(), sdoc.getURL().toExternalForm(), multiSchemaForTns);
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "WSDL inlined schema fragment documents(these are used to create a pseudo schema) = {0}", inlinedSchemas.keySet());
        }
        for(DOMSource src: inlinedSchemas.values()) {
            String tns = getTargetNamespace(src);
            updateMultiSchemaForTns(tns, src.getSystemId(), multiSchemaForTns);
        }

        if (multiSchemaForTns.isEmpty()) {
            return new Source[0];   // WSDL doesn't have any schema fragments
        } else if (multiSchemaForTns.size() == 1 && multiSchemaForTns.values().iterator().next().size() == 1) {
            // It must be a inlined schema, otherwise there would be at least two schemas
            String systemId = multiSchemaForTns.values().iterator().next().get(0);
            return new Source[] {inlinedSchemas.get(systemId)};
        }

        // need to resolve these inlined schema fragments
        mdresolver.addSchemas(inlinedSchemas.values());

        // If there are multiple schema fragments for the same tns, create a
        // pseudo schema for that tns by using <xsd:include> of those.
        // tns --> systemId of a pseudo schema document (consolidated for that tns)
        Map<String, String> oneSchemaForTns = new HashMap<String, String>();
        int i = 0;
        for(Map.Entry<String, List<String>> e: multiSchemaForTns.entrySet()) {
            String systemId;
            List<String> sameTnsSchemas = e.getValue();
            if (sameTnsSchemas.size() > 1) {
                // SDDocumentSource should be changed to take String systemId
                // String pseudoSystemId = "urn:x-jax-ws-include-"+i++;
                systemId = "file:x-jax-ws-include-"+i++;
                Source src = createSameTnsPseudoSchema(e.getKey(), sameTnsSchemas, systemId);
                mdresolver.addSchema(src);
            } else {
                systemId = sameTnsSchemas.get(0);
            }
            oneSchemaForTns.put(e.getKey(), systemId);
        }

        // create a master pseudo schema with all the different tns
        Source pseudoSchema = createMasterPseudoSchema(oneSchemaForTns);
        return new Source[] { pseudoSchema };
    }

    private @Nullable void addSchemaFragmentSource(Document doc, String systemId, Map<String, DOMSource> map) {
        Element e = doc.getDocumentElement();
        assert e.getNamespaceURI().equals(WSDLConstants.NS_WSDL);
        assert e.getLocalName().equals("definitions");

        NodeList typesList = e.getElementsByTagNameNS(WSDLConstants.NS_WSDL, "types");
        for(int i=0; i < typesList.getLength(); i++) {
            NodeList schemaList = ((Element)typesList.item(i)).getElementsByTagNameNS(WSDLConstants.NS_XMLNS, "schema");
            for(int j=0; j < schemaList.getLength(); j++) {
                Element elem = (Element)schemaList.item(j);
                NamespaceSupport nss = new NamespaceSupport();
                // Doing this because transformer is not picking up inscope namespaces
                // why doesn't transformer pickup the inscope namespaces ??
                buildNamespaceSupport(nss, elem);
                patchDOMFragment(nss, elem);
                String docId = systemId+"#schema"+j;
                map.put(docId, new DOMSource(elem, docId));
            }
        }
    }


    /*
     * Recursively visit ancestors and build up {@link org.xml.sax.helpers.NamespaceSupport} object.
     */
    private void buildNamespaceSupport(NamespaceSupport nss, Node node) {
        if (node==null || node.getNodeType()!=Node.ELEMENT_NODE) {
            return;
        }

        buildNamespaceSupport( nss, node.getParentNode() );

        nss.pushContext();
        NamedNodeMap atts = node.getAttributes();
        for( int i=0; i<atts.getLength(); i++ ) {
            Attr a = (Attr)atts.item(i);
            if( "xmlns".equals(a.getPrefix()) ) {
                nss.declarePrefix( a.getLocalName(), a.getValue() );
                continue;
            }
            if( "xmlns".equals(a.getName()) ) {
                nss.declarePrefix( "", a.getValue() );
                //continue;
            }
        }
    }

    /**
     * Adds inscope namespaces as attributes to  <xsd:schema> fragment nodes.
     *
     * @param nss namespace context info
     * @param elem that is patched with inscope namespaces
     */
    private @Nullable void patchDOMFragment(NamespaceSupport nss, Element elem) {
        NamedNodeMap atts = elem.getAttributes();
        for( Enumeration en = nss.getPrefixes(); en.hasMoreElements(); ) {
            String prefix = (String)en.nextElement();

            for( int i=0; i<atts.getLength(); i++ ) {
                Attr a = (Attr)atts.item(i);
                if (!"xmlns".equals(a.getPrefix()) || !a.getLocalName().equals(prefix)) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Patching with xmlns:{0}={1}", new Object[]{prefix, nss.getURI(prefix)});
                    }
                    elem.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:"+prefix, nss.getURI(prefix));
                }
            }
        }
    }

    /*
     * Creates a pseudo schema for the WSDL schema fragments that have the same
     * targetNamespace.
     *
     * <xsd:schema targetNamespace="X">
     *   <xsd:include schemaLocation="Y1"/>
     *   <xsd:include schemaLocation="Y2"/>
     * </xsd:schema>
     *
     * @param tns targetNamespace of the the schema documents
     * @param docs collection of systemId for the schema documents that have the
     *        same tns, the collection must have more than one document
     * @param psuedoSystemId for the created pseudo schema
     * @return Source of pseudo schema that can be used multiple times
     */
    private @Nullable Source createSameTnsPseudoSchema(String tns, Collection<String> docs, String pseudoSystemId) {
        assert docs.size() > 1;

        final StringBuilder sb = new StringBuilder("<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'");
        if (tns != null && !("".equals(tns)) && !("null".equals(tns))) {
            sb.append(" targetNamespace='").append(tns).append("'");
        }
        sb.append(">\n");
        for(String systemId : docs) {
            sb.append("<xsd:include schemaLocation='").append(systemId).append("'/>\n");
        }
        sb.append("</xsd:schema>\n");
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Pseudo Schema for the same tns={0}is {1}", new Object[]{tns, sb});
        }

        // override getReader() so that the same source can be used multiple times
        return new StreamSource(pseudoSystemId) {
            @Override
            public Reader getReader() {
                return new StringReader(sb.toString());
            }
        };
    }

    /*
     * Creates a master pseudo schema importing all WSDL schema fragments with
     * different tns+pseudo schema for same tns.
     * <xsd:schema targetNamespace="urn:x-jax-ws-master">
     *   <xsd:import schemaLocation="Y1" namespace="X1"/>
     *   <xsd:import schemaLocation="Y2" namespace="X2"/>
     * </xsd:schema>
     *
     * @param pseudo a map(tns-->systemId) of schema documents
     * @return Source of pseudo schema that can be used multiple times
     */
    private Source createMasterPseudoSchema(Map<String, String> docs) {
        final StringBuilder sb = new StringBuilder("<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:x-jax-ws-master'>\n");
        for(Map.Entry<String, String> e : docs.entrySet()) {
            String systemId = e.getValue();
            String ns = e.getKey();
            sb.append("<xsd:import schemaLocation='").append(systemId).append("'");
            if (ns != null && !("".equals(ns))) {
                sb.append(" namespace='").append(ns).append("'");
            }
            sb.append("/>\n");
        }
        sb.append("</xsd:schema>");
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Master Pseudo Schema = {0}", sb);
        }

        // override getReader() so that the same source can be used multiple times
        return new StreamSource("file:x-jax-ws-master-doc") {
            @Override
            public Reader getReader() {
                return new StringReader(sb.toString());
            }
        };
    }

    protected void doProcess(Packet packet) throws SAXException {
        getValidator().reset();
        Class<? extends ValidationErrorHandler> handlerClass = feature.getErrorHandler();
        ValidationErrorHandler handler;
        try {
            handler = handlerClass.newInstance();
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
        handler.setPacket(packet);
        getValidator().setErrorHandler(handler);
        Message msg = packet.getMessage().copy();
        Source source = msg.readPayloadAsSource();
        try {
            // Validator javadoc allows ONLY SAX, and DOM Sources
            // But the impl seems to handle all kinds.
            getValidator().validate(source);
        } catch(IOException e) {
            throw new WebServiceException(e);
        }
    }

    private String getTargetNamespace(DOMSource src) {
        Element elem = (Element)src.getNode();
        return elem.getAttribute("targetNamespace");
    }

//    protected static void printSource(Source src) {
//        try {
//            ByteArrayBuffer bos = new ByteArrayBuffer();
//            StreamResult sr = new StreamResult(bos );
//            Transformer trans = TransformerFactory.newInstance().newTransformer();
//            trans.transform(src, sr);
//            LOGGER.info("**** src ******"+bos.toString());
//            bos.close();
//        } catch(Exception e) {
//            e.printStackTrace();
//        }
//    }

}
