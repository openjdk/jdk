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
package com.sun.xml.internal.ws.model.soap;

import com.sun.xml.internal.ws.encoding.soap.SOAPVersion;

/**
 * Binding object that represents soap:binding
 *
  * @author Vivek Pandey
 */
public class SOAPBinding {
    public SOAPBinding() {
    }

    public SOAPBinding(SOAPBinding sb){
        this.use = sb.use;
        this.style = sb.style;
        this.soapVersion = sb.soapVersion;
        this.soapAction = sb.soapAction;
    }

    /**
     * @return Returns the use.
     */
    public Use getUse() {
        return use;
    }

    /**
     * @param use
     *            The use to set.
     */
    public void setUse(Use use) {
        this.use = use;
    }

    /**
     * @return Returns the style.
     */
    public Style getStyle() {
        return style;
    }

    /**
     * @param style
     *            The style to set.
     */
    public void setStyle(Style style) {
        this.style = style;
    }

    /**
     * @param version
     */
    public void setSOAPVersion(SOAPVersion version) {
        this.soapVersion = version;
    }

    /**
     * @return the SOAPVersion of this SOAPBinding
     */
    public SOAPVersion getSOAPVersion() {
        return soapVersion;
    }

    /**
     * @return true if this is a document/literal SOAPBinding
     */
    public boolean isDocLit() {
        return style.equals(Style.DOCUMENT) && use.equals(Use.LITERAL);
    }

    /**
     * @return true if this is a rpc/literal SOAPBinding
     */
    public boolean isRpcLit() {
        return style.equals(Style.RPC) && use.equals(Use.LITERAL);
    }

    /**
     * @return the soapAction header value. It's always non-null. soap
     *         message serializer needs to generated SOAPAction HTTP header with
     *         the return of this method enclosed in quotes("").
     */
    public String getSOAPAction() {
        if (soapAction == null)
            return "";
        return soapAction;
    }

    /**
     * @param soapAction
     *            The soapAction to set.
     */
    public void setSOAPAction(String soapAction) {
        this.soapAction = soapAction;
    }

    private Style style = Style.DOCUMENT;

    private Use use = Use.LITERAL;

    private SOAPVersion soapVersion = SOAPVersion.SOAP_11;

    private String soapAction;

}
