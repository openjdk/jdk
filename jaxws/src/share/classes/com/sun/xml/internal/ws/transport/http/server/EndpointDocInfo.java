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
package com.sun.xml.internal.ws.transport.http.server;

import com.sun.xml.internal.ws.server.DocInfo;
import com.sun.xml.internal.ws.wsdl.parser.Service;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;

public class EndpointDocInfo implements DocInfo {
    private URL resourceUrl;
    private String queryString;
    private ByteArrayBuffer buf;
    private DOC_TYPE docType;
    private String tns;
    private Service service;
    private boolean portType;

    public EndpointDocInfo(URL resourceUrl, ByteArrayBuffer buf) {
        this.resourceUrl = resourceUrl;
        this.buf = buf;
    }

    public InputStream getDoc() {
        return buf.newInputStream();
    }

    public String getPath() {
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

    public void setTargetNamespace(String tns) {
        this.tns = tns;
    }

    public String getTargetNamespace() {
        return tns;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Service getService() {
        return service;
    }

    public void setHavingPortType(boolean portType) {
        this.portType = portType;
    }

    public boolean isHavingPortType() {
        return portType;
    }

    public URL getUrl() {
        return resourceUrl;
    }

}
