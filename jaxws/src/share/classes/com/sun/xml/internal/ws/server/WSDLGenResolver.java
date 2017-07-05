/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.server;

import com.sun.xml.internal.ws.server.DocInfo.DOC_TYPE;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.wsdl.parser.Service;
import com.sun.xml.internal.ws.wsdl.writer.WSDLOutputResolver;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.Holder;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author WS Development Team
 */

public class WSDLGenResolver implements WSDLOutputResolver {

    private Map<String, DocInfo> docs;
    private DocInfo abstractWsdl;
    private DocInfo concreteWsdl;
    private Map<String, List<String>> nsMapping;    // targetNS -> system id list

    public WSDLGenResolver(Map<String, DocInfo> docs) {
        this.docs = docs;
        nsMapping = new HashMap<String, List<String>>();
        Set<Entry<String, DocInfo>> docEntries = docs.entrySet();
        for(Entry<String, DocInfo> entry : docEntries) {
            DocInfo docInfo = entry.getValue();
            if (docInfo.isHavingPortType()) {
                abstractWsdl = docInfo;
            }
            if (docInfo.getDocType() == DOC_TYPE.SCHEMA) {
                List<String> sysIds = nsMapping.get(docInfo.getTargetNamespace());
                if (sysIds == null) {
                    sysIds = new ArrayList<String>();
                    nsMapping.put(docInfo.getTargetNamespace(), sysIds);
                }
                sysIds.add(docInfo.getUrl().toString());
            }
        }
    }

    public String getWSDLFile() {
        return concreteWsdl.getUrl().toString();
    }

    public Map<String, DocInfo> getDocs() {
        return docs;
    }

    /*
    public Result getWSDLOutput(String suggestedFileName) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        StreamDocInfo docInfo = new StreamDocInfo(suggestedFileName, bout);

        if (wsdlFile == null) {
            docInfo.setQueryString("wsdl");
            wsdlFile = suggestedFileName;
        } else {
            docInfo.setQueryString("wsdl="+suggestedFileName);
        }
        docs.put(docInfo.getPath(),  docInfo);

        StreamResult result = new StreamResult();
        result.setOutputStream(bout);
        result.setSystemId(suggestedFileName);
        return result;
    }
     */

    public Result getSchemaOutput(String namespaceUri, String suggestedFileName) {
        ByteArrayBuffer bout = new ByteArrayBuffer();

        StreamDocInfo docInfo = new StreamDocInfo(suggestedFileName, bout);
        docInfo.setQueryString("xsd="+suggestedFileName);
        docInfo.setDocType(DOC_TYPE.SCHEMA);
        docs.put(docInfo.getUrl().toString(),  docInfo);

        StreamResult result = new StreamResult();
        result.setOutputStream(bout);
        result.setSystemId(docInfo.getUrl().toString());
        return result;
    }

    /*
     * return null if concrete WSDL need not be generated
     */
    public Result getWSDLOutput(String filename) {
        ByteArrayBuffer bout = new ByteArrayBuffer();
        StreamDocInfo docInfo = new StreamDocInfo(filename, bout);
        docInfo.setDocType(DOC_TYPE.WSDL);
        docInfo.setQueryString("wsdl");
        concreteWsdl = docInfo;
        docs.put(docInfo.getUrl().toString(),  docInfo);
        StreamResult result = new StreamResult();
        result.setOutputStream(bout);
        result.setSystemId(docInfo.getUrl().toString());
        return result;
    }

    /*
     * Updates filename if the suggested filename need to be changed in
     * wsdl:import
     *
     * return null if abstract WSDL need not be generated
     */
    public Result getAbstractWSDLOutput(Holder<String> filename) {
        if (abstractWsdl != null) {
            filename.value = abstractWsdl.getUrl().toString();
            return null;                // Don't generate abstract WSDL
        }
        ByteArrayBuffer bout = new ByteArrayBuffer();
        StreamDocInfo abstractWsdl = new StreamDocInfo(filename.value, bout);
        abstractWsdl.setDocType(DOC_TYPE.WSDL);
        //abstractWsdl.setQueryString("wsdl="+filename.value);
        docs.put(abstractWsdl.getUrl().toString(),  abstractWsdl);
        StreamResult result = new StreamResult();
        result.setOutputStream(bout);
        result.setSystemId(abstractWsdl.getUrl().toString());
        return result;
    }

    /*
     * Updates filename if the suggested filename need to be changed in
     * xsd:import
     *
     * return null if schema need not be generated
     */
    public Result getSchemaOutput(String namespace, Holder<String> filename) {
        List<String> schemas = nsMapping.get(namespace);
        if (schemas != null) {
            if (schemas.size() > 1) {
                throw new ServerRtException("server.rt.err",
                    "More than one schema for the target namespace "+namespace);
            }
            filename.value = schemas.get(0);
            return null;            // Don't generate schema
        }
        ByteArrayBuffer bout = new ByteArrayBuffer();
        StreamDocInfo docInfo = new StreamDocInfo(filename.value, bout);
        docInfo.setDocType(DOC_TYPE.SCHEMA);
        //docInfo.setQueryString("xsd="+filename.value);
        docs.put(docInfo.getUrl().toString(),  docInfo);
        StreamResult result = new StreamResult();
        result.setOutputStream(bout);
        result.setSystemId(docInfo.getUrl().toString());
        return result;
    }

    private static class StreamDocInfo implements DocInfo {
        private ByteArrayBuffer bout;
        private String resource;
        private String queryString;
                private DOC_TYPE docType;

        public StreamDocInfo(String resource, ByteArrayBuffer bout) {
            this.resource = resource;
            this.bout = bout;
        }

        public InputStream getDoc() {
            bout.close();
            return bout.newInputStream();
        }

        public String getPath() {
            return resource;
        }

        public URL getUrl() {
            try {
                return new URL("file:///"+resource);
            } catch(Exception e) {

            }
            return null;
        }

        public String getQueryString() {
            return queryString;
        }

        public void setQueryString(String queryString) {
            this.queryString = queryString;
        }

        public void setDocType(DOC_TYPE docType) {
                        this.docType = docType;
        }

        public DOC_TYPE getDocType() {
            return docType;
        }

        public void setTargetNamespace(String ns) {

        }

        public String getTargetNamespace() {
            return null;
        }

        public void setService(Service service) {

        }

        public Service getService() {
            return null;
        }

        public void setHavingPortType(boolean portType) {

        }

        public boolean isHavingPortType() {
            return false;
        }
    }

}
