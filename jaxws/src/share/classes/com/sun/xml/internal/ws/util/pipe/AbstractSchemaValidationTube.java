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
package com.sun.xml.internal.ws.util.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.api.server.DocumentAddressResolver;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.developer.SchemaValidationFeature;
import com.sun.xml.internal.ws.developer.ValidationErrorHandler;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import org.w3c.dom.*;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

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

    public AbstractSchemaValidationTube(WSBinding binding, Tube next) {
        super(next);
        this.binding = binding;
        feature = binding.getFeature(SchemaValidationFeature.class);
    }

    protected AbstractSchemaValidationTube(AbstractSchemaValidationTube that, TubeCloner cloner) {
        super(that, cloner);
        this.binding = that.binding;
        this.feature = that.feature;
    }

    protected abstract Validator getValidator();

    protected abstract boolean isNoValidation();

    private static class ValidationDocumentAddressResolver implements DocumentAddressResolver {

        @Nullable
        public String getRelativeAddressFor(@NotNull SDDocument current, @NotNull SDDocument referenced) {
            LOGGER.fine("Current = "+current.getURL()+" resolved relative="+referenced.getURL());
            return referenced.getURL().toExternalForm();
        }
    }

    protected Document createDOM(SDDocument doc) {
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


    /**
     * Locates xsd:schema elements in the WSDL and creates DOMSource and adds them to the list
     *
     * @param doc WSDL document
     * @param systemId systemId for WSDL document
     * @param list xsd:schema DOMSource list
     */
    protected @Nullable void addSchemaFragmentSource(Document doc, String systemId, List<Source> list) {

        Element e = doc.getDocumentElement();
        assert e.getNamespaceURI().equals(WSDLConstants.NS_WSDL);
        assert e.getLocalName().equals("definitions");

        NodeList typesList = e.getElementsByTagNameNS(WSDLConstants.NS_WSDL, "types");
        for(int i=0; i < typesList.getLength(); i++) {
            NodeList schemaList = ((Element)typesList.item(i)).getElementsByTagNameNS(WSDLConstants.NS_XMLNS, "schema");
            for(int j=0; j < schemaList.getLength(); j++) {
                Element elem = (Element)schemaList.item(j);
                NamespaceSupport nss = new NamespaceSupport();
                buildNamespaceSupport(nss, elem);
                patchDOMFragment(nss, elem);
                list.add(new DOMSource(elem, systemId+"#schema"+j));
            }
        }
    }


    /**
     * Recursively visit ancestors and build up {@link org.xml.sax.helpers.NamespaceSupport} oject.
     */
    private void buildNamespaceSupport(NamespaceSupport nss, Node node) {
        if(node==null || node.getNodeType()!=Node.ELEMENT_NODE)
            return;

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
                continue;
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
                if (!"xmlns".equals(a.getPrefix()) || !a.getLocalName().equals("prefix")) {
                    LOGGER.fine("Patching with xmlns:"+prefix+"="+nss.getURI(prefix));
                    elem.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:"+prefix, nss.getURI(prefix));
                }
            }
        }
    }

    @Override
    public NextAction processRequest(Packet request) {
        if (isNoValidation() || !request.getMessage().hasPayload() || request.getMessage().isFault()) {
            return super.processRequest(request);
        }
        doProcess(request);
        return super.processRequest(request);
    }

    @Override
    public NextAction processResponse(Packet response) {
        if (isNoValidation() || response.getMessage() == null || !response.getMessage().hasPayload() || response.getMessage().isFault()) {
            return super.processResponse(response);
        }
        doProcess(response);
        return super.processResponse(response);
    }

    private void doProcess(Packet packet) {
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
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
    }

    protected DOMSource toDOMSource(Source source) {
        if (source instanceof DOMSource) {
            return (DOMSource)source;
        }
        Transformer trans = XmlUtil.newTransformer();
        DOMResult result = new DOMResult();
        try {
            trans.transform(source, result);
        } catch(TransformerException te) {
            throw new WebServiceException(te);
        }
        return new DOMSource(result.getNode());
    }

    protected static void printDOM(DOMSource src) {
        try {
            ByteArrayBuffer bos = new ByteArrayBuffer();
            StreamResult sr = new StreamResult(bos );
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            trans.transform(src, sr);
            LOGGER.info("**** src ******"+bos.toString());
            bos.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
