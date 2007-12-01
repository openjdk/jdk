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
import com.sun.xml.internal.ws.wsdl.parser.Service;
import java.io.InputStream;
import java.net.URL;


public interface DocInfo {

    public enum DOC_TYPE { WSDL, SCHEMA, OTHER };

    /*
     * The implemenation needs to work for multiple invocations of this method
     */
    public InputStream getDoc();

    /*
     * @return wsdl=a, xsd=c etc
     */
    public String getQueryString();

    /*
     * set wsdl=a, xsd=c etc as queryString
     */
    public void setQueryString(String queryString);

    /*
     * Sets document type : WSDL, or Schema ?
     */
    public void setDocType(DOC_TYPE docType);

    /*
     * return document type : WSDL, or Schema ?
     */
    public DOC_TYPE getDocType();

    /*
     * Sets targetNamespace of WSDL, and schema
     */
    public void setTargetNamespace(String ns);

    /*
     * Sets targetNamespace of WSDL, and schema
     */
    public String getTargetNamespace();

    /*
     * Sets if the endpoint service is defined in this document
     */
    public void setService(Service service);

    /*
     * returns true if endpoint service is present in this document
     */
    public Service getService();

    /*
     * Sets if the endpoint Port Type is defined in this document
     */
    public void setHavingPortType(boolean portType);

    /*
     * returns true if endpoint PortType is present in this document
     */
    public boolean isHavingPortType();

    /*
     * @return /WEB-INF/wsdl/xxx.wsdl
     */
    public String getPath();

    /*
     * @return URL for /WEB-INF/wsdl/xxx.wsdl
     */
    public URL getUrl();

}
