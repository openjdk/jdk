/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.tools.internal.ws.resources.WscompileMessages;
import com.sun.tools.internal.ws.resources.WsdlMessages;
import com.sun.tools.internal.ws.wscompile.AbortException;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.wsdl.document.WSDLConstants;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaConstants;
import com.sun.tools.internal.ws.wsdl.framework.ParseException;
import com.sun.xml.internal.ws.api.wsdl.parser.MetaDataResolver;
import com.sun.xml.internal.ws.api.wsdl.parser.MetadataResolverFactory;
import com.sun.xml.internal.ws.api.wsdl.parser.ServiceDescriptor;
import com.sun.xml.internal.ws.util.DOMUtil;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.ServiceFinder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

/**
 * @author Vivek Pandey
 */
public final class MetadataFinder extends DOMForest{

    public boolean isMexMetadata;
    private String rootWSDL;
    private final Set<String> rootWsdls = new HashSet<String>();

    public MetadataFinder(InternalizationLogic logic, WsimportOptions options, ErrorReceiver errReceiver) {
        super(logic, new WSEntityResolver(options,errReceiver), options, errReceiver);

    }

    @SuppressWarnings("element-type-mismatch")
    public void parseWSDL(){
        // parse source grammars
        for (InputSource value : options.getWSDLs()) {
            String systemID = value.getSystemId();
            errorReceiver.pollAbort();

            Document dom;
            Element doc;

            try {
                //if there is entity resolver use it
                if (options.entityResolver != null) {
                    value = options.entityResolver.resolveEntity(null, systemID);
                }
                if (value == null) {
                    value = new InputSource(systemID);
                }
                dom = parse(value, true);

                doc = dom.getDocumentElement();
                if (doc == null) {
                    continue;
                }
                //if its not a WSDL document, retry with MEX
                if (doc.getNamespaceURI() == null || !doc.getNamespaceURI().equals(WSDLConstants.NS_WSDL) || !doc.getLocalName().equals("definitions")) {
                    throw new SAXParseException(WsdlMessages.INVALID_WSDL(systemID,
                            com.sun.xml.internal.ws.wsdl.parser.WSDLConstants.QNAME_DEFINITIONS, doc.getNodeName(), locatorTable.getStartLocation(doc).getLineNumber()), locatorTable.getStartLocation(doc));
                }
            } catch (FileNotFoundException e) {
                errorReceiver.error(WsdlMessages.FILE_NOT_FOUND(systemID), e);
                return;
            } catch (IOException e) {
                doc = getFromMetadataResolver(systemID, e);
            } catch (SAXParseException e) {
                doc = getFromMetadataResolver(systemID, e);
            } catch (SAXException e) {
                doc = getFromMetadataResolver(systemID, e);
            }

            if (doc == null) {
                continue;
            }

            NodeList schemas = doc.getElementsByTagNameNS(SchemaConstants.NS_XSD, "schema");
            for (int i = 0; i < schemas.getLength(); i++) {
                if (!inlinedSchemaElements.contains(schemas.item(i))) {
                    inlinedSchemaElements.add((Element) schemas.item(i));
                }
            }
        }
        identifyRootWsdls();
    }

    public static class WSEntityResolver implements EntityResolver {
        WsimportOptions options;
        ErrorReceiver errorReceiver;

        private URLConnection c = null;
        private boolean doReset = false;

        public WSEntityResolver(WsimportOptions options, ErrorReceiver errReceiver) {
            this.options = options;
            this.errorReceiver = errReceiver;
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            InputSource inputSource = null;

            if(options.entityResolver != null ) {
                inputSource = options.entityResolver.resolveEntity(null, systemId);
            }
            if (inputSource == null) {
                inputSource = new InputSource(systemId);
                InputStream is = null;
                int redirects = 0;
                boolean redirect;
                URL url = JAXWSUtils.getFileOrURL(inputSource.getSystemId());
                URLConnection conn = url.openConnection();
                do {
                    if (conn instanceof HttpsURLConnection) {
                        if (options.disableSSLHostnameVerification) {
                            ((HttpsURLConnection) conn).setHostnameVerifier(new HttpClientVerifier());
                        }
                    }
                    redirect = false;
                    if (conn instanceof HttpURLConnection) {
                        ((HttpURLConnection) conn).setInstanceFollowRedirects(false);
                    }

                    if (conn instanceof JarURLConnection) {
                        if (conn.getUseCaches()) {
                            doReset = true;
                            conn.setDefaultUseCaches(false);
                            c = conn;
                        }
                    }

                    try {
                        is = conn.getInputStream();
                        //is = sun.net.www.protocol.http.HttpURLConnection.openConnectionCheckRedirects(conn);
                    } catch (IOException e) {
                        if (conn instanceof HttpURLConnection) {
                            HttpURLConnection httpConn = ((HttpURLConnection) conn);
                            int code = httpConn.getResponseCode();
                            if (code == 401) {
                                errorReceiver.error(new SAXParseException(WscompileMessages.WSIMPORT_AUTH_INFO_NEEDED(e.getMessage(),
                                        systemId, WsimportOptions.defaultAuthfile), null, e));
                                throw new AbortException();
                            }
                            //FOR other code we will retry with MEX
                        }
                        throw e;
                    }

                    //handle 302 or 303, JDK does not seem to handle 302 very well.
                    //Need to redesign this a bit as we need to throw better error message for IOException in this case
                    if (conn instanceof HttpURLConnection) {
                        HttpURLConnection httpConn = ((HttpURLConnection) conn);
                        int code = httpConn.getResponseCode();
                        if (code == 302 || code == 303) {
                            //retry with the value in Location header
                            List<String> seeOther = httpConn.getHeaderFields().get("Location");
                            if (seeOther != null && seeOther.size() > 0) {
                                URL newurl = new URL(url, seeOther.get(0));
                                if (!newurl.equals(url)) {
                                    errorReceiver.info(new SAXParseException(WscompileMessages.WSIMPORT_HTTP_REDIRECT(code, seeOther.get(0)), null));
                                    url = newurl;
                                    httpConn.disconnect();
                                    if (redirects >= 5) {
                                        errorReceiver.error(new SAXParseException(WscompileMessages.WSIMPORT_MAX_REDIRECT_ATTEMPT(), null));
                                        throw new AbortException();
                                    }
                                    conn = url.openConnection();
                                    inputSource.setSystemId(url.toExternalForm());
                                    redirects++;
                                    redirect = true;
                                }
                            }
                        }
                    }
                } while (redirect);
                inputSource.setByteStream(is);
            }

            return inputSource;
        }

        @Override
        protected void finalize() throws Throwable {
            //see http://java.net/jira/browse/JAX_WS-1087
            if (doReset) {
                c.setDefaultUseCaches(true);
            }
        }
    }

    // overide default SSL HttpClientVerifier to always return true
    // effectively overiding Hostname client verification when using SSL
    private static class HttpClientVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

    /**
     * Gives the root wsdl document systemId. A root wsdl document is the one which has wsdl:service.
     * @return null if there is no root wsdl
     */
    public @Nullable
    String getRootWSDL(){
        return rootWSDL;
    }

    /**
     * Gives all the WSDL documents.
     */
    public @NotNull
    Set<String> getRootWSDLs(){
        return rootWsdls;
    }


    /**
     * Identifies WSDL documents from the {@link DOMForest}. Also identifies the root wsdl document.
     */
    private void identifyRootWsdls(){
        for(String location: rootDocuments){
            Document doc = get(location);
            if(doc!=null){
                Element definition = doc.getDocumentElement();
                if(definition == null || definition.getLocalName() == null || definition.getNamespaceURI() == null)
                    continue;
                if(definition.getNamespaceURI().equals(WSDLConstants.NS_WSDL) && definition.getLocalName().equals("definitions")){
                    rootWsdls.add(location);
                    //set the root wsdl at this point. Root wsdl is one which has wsdl:service in it
                    NodeList nl = definition.getElementsByTagNameNS(WSDLConstants.NS_WSDL, "service");

                    //TODO:what if there are more than one wsdl with wsdl:service element. Probably such cases
                    //are rare and we will take any one of them, this logic should still work
                    if(nl.getLength() > 0)
                        rootWSDL = location;
                }
            }
        }
        //no wsdl with wsdl:service found, throw error
        if(rootWSDL == null){
            StringBuilder strbuf = new StringBuilder();
            for(String str : rootWsdls){
                strbuf.append(str);
                strbuf.append('\n');
            }
            errorReceiver.error(null, WsdlMessages.FAILED_NOSERVICE(strbuf.toString()));
        }
    }

    /*
    * If source and target namespace are also passed in,
    * then if the mex resolver is found and it cannot get
    * the data, wsimport attempts to add ?wsdl to the
    * address and retrieve the data with a normal http get.
    * This behavior should only happen when trying a
    * mex request first.
    */
    private @Nullable Element getFromMetadataResolver(String systemId, Exception ex) {
        //try MEX
        MetaDataResolver resolver;
        ServiceDescriptor serviceDescriptor = null;
        for (MetadataResolverFactory resolverFactory : ServiceFinder.find(MetadataResolverFactory.class)) {
            resolver = resolverFactory.metadataResolver(options.entityResolver);
            try {
                serviceDescriptor = resolver.resolve(new URI(systemId));
                //we got the ServiceDescriptor, now break
                if (serviceDescriptor != null)
                    break;
            } catch (URISyntaxException e) {
                throw new ParseException(e);
            }
        }

        if (serviceDescriptor != null) {
            errorReceiver.warning(new SAXParseException(WsdlMessages.TRY_WITH_MEX(ex.getMessage()), null, ex));
            return parseMetadata(systemId, serviceDescriptor);
        } else {
            errorReceiver.error(null, WsdlMessages.PARSING_UNABLE_TO_GET_METADATA(ex.getMessage(), WscompileMessages.WSIMPORT_NO_WSDL(systemId)), ex);
        }
        return null;
    }

    private Element parseMetadata(@NotNull String systemId, @NotNull ServiceDescriptor serviceDescriptor) {
        List<? extends Source> mexWsdls = serviceDescriptor.getWSDLs();
        List<? extends Source> mexSchemas = serviceDescriptor.getSchemas();
        Document root = null;
        for (Source src : mexWsdls) {
            if (src instanceof DOMSource) {
                Node n = ((DOMSource) src).getNode();
                Document doc;
                if (n.getNodeType() == Node.ELEMENT_NODE && n.getOwnerDocument() == null) {
                    doc = DOMUtil.createDom();
                    doc.importNode(n, true);
                } else {
                    doc = n.getOwnerDocument();
                }

//                Element e = (n.getNodeType() == Node.ELEMENT_NODE)?(Element)n: DOMUtil.getFirstElementChild(n);
                if (root == null) {
                    //check if its main wsdl, then set it to root
                    NodeList nl = doc.getDocumentElement().getElementsByTagNameNS(WSDLConstants.NS_WSDL, "service");
                    if (nl.getLength() > 0) {
                        root = doc;
                        rootWSDL = src.getSystemId();
                    }
                }
                NodeList nl = doc.getDocumentElement().getElementsByTagNameNS(WSDLConstants.NS_WSDL, "import");
                for(int i = 0; i < nl.getLength(); i++){
                    Element imp = (Element) nl.item(i);
                    String loc = imp.getAttribute("location");
                    if (loc != null) {
                        if (!externalReferences.contains(loc))
                            externalReferences.add(loc);
                    }
                }
                if (core.keySet().contains(systemId))
                    core.remove(systemId);
                core.put(src.getSystemId(), doc);
                resolvedCache.put(systemId, doc.getDocumentURI());
                isMexMetadata = true;
            }

            //TODO:handle SAXSource
            //TODO:handler StreamSource
        }

        for (Source src : mexSchemas) {
            if (src instanceof DOMSource) {
                Node n = ((DOMSource) src).getNode();
                Element e = (n.getNodeType() == Node.ELEMENT_NODE) ? (Element) n : DOMUtil.getFirstElementChild(n);
                inlinedSchemaElements.add(e);
            }
            //TODO:handle SAXSource
            //TODO:handler StreamSource
        }
        return root.getDocumentElement();
    }
}
