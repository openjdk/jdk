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

package com.sun.xml.internal.ws.encoding;

import com.sun.xml.internal.ws.api.pipe.ContentType;

/**
 * @author Vivek Pandey
 */
public final class ContentTypeImpl implements ContentType {
    private final String contentType;
    private final String soapAction;
    private final String accept;

    public ContentTypeImpl(String contentType) {
        this.contentType = contentType;
        this.soapAction = null;
        this.accept = null;
    }

    public ContentTypeImpl(String contentType, String soapAction) {
        this.contentType = contentType;
        this.soapAction = getQuotedSOAPAction(soapAction);
        this.accept = null;
    }

    public ContentTypeImpl(String contentType, String soapAction, String accept) {
        this.contentType = contentType;
        this.accept = accept;
        this.soapAction = getQuotedSOAPAction(soapAction);
    }

    /** BP 1.1 R1109 requires SOAPAction too be a quoted value **/
    private String getQuotedSOAPAction(String soapAction){
        if(soapAction == null || soapAction.length() == 0){
            return "\"\"";
        }else if(soapAction.charAt(0) != '"' && soapAction.charAt(soapAction.length() -1) != '"'){
            //surround soapAction by double quotes for BP R1109
            return "\"" + soapAction + "\"";
        }else{
            return soapAction;
        }
    }

    public String getContentType() {
        return contentType;
    }

    public String getSOAPActionHeader() {
        return soapAction;
    }

    public String getAcceptHeader() {
        return accept;
    }
}
