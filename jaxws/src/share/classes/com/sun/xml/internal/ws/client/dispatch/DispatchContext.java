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
package com.sun.xml.internal.ws.client.dispatch;

import java.util.HashMap;

/**
 * $author: JAXWS Development Team
 */
public class DispatchContext {

    private HashMap dprops = null;

    public DispatchContext() {
        dprops = new HashMap();
    }

    public void setProperty(String name, Object value) {
        dprops.put(name, value);
    }

    public Object getProperty(String name) {
        return dprops.get(name);
    }

    public void removeProperty(String name) {
        dprops.remove(name);
    }

    public void clearProperties() {
        dprops.clear();
    }

    public static final String DISPATCH_MESSAGE =
        "com.sun.xml.internal.ws.rt.client.dispatch.messagetype";
    public static final String DISPATCH_MESSAGE_MODE =
        "com.sun.xml.internal.ws.rt.client.dispatch.mode";
    public static final String DISPATCH_MESSAGE_CLASS =
        "com.sun.xml.internal.ws.rt.client.dispatch.messageclass";

    public enum MessageClass {
        SOURCE ,JAXBOBJECT, SOAPMESSAGE, DATASOURCE
    }

    public enum MessageType {
        JAXB_PAYLOAD,             //SOAP Binding
        SOURCE_PAYLOAD,
        JAXB_MESSAGE,
        SOURCE_MESSAGE ,
        SOAPMESSAGE_MESSAGE,
        //HTTP_DATASOURCE_PAYLOAD,  //HTTP Binding
        HTTP_DATASOURCE_MESSAGE,
        HTTP_SOURCE_MESSAGE, //can be allowed with an HTTP GET method
        HTTP_SOURCE_PAYLOAD,
        HTTP_JAXB_PAYLOAD,
        //HTTP_JAXB_MESSAGE
    }
}
