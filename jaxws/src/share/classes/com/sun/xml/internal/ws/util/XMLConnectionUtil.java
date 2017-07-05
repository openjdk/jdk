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

package com.sun.xml.internal.ws.util;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.http.HTTPException;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import javax.xml.transform.stream.StreamSource;

import static com.sun.xml.internal.ws.developer.JAXWSProperties.CONTENT_NEGOTIATION_PROPERTY;

/**
 * @author WS Development Team
 */
public class XMLConnectionUtil {

    public static XMLMessage getXMLMessage(WSConnection con, MessageInfo mi) {
        try {
            Map<String, List<String>> headers = con.getHeaders();
            MimeHeaders mh = new MimeHeaders();
            if (headers != null) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet())
                {
                    String name = entry.getKey();
                    for (String value : entry.getValue()) {
                        try {
                            mh.addHeader(name, value);
                        } catch (IllegalArgumentException ie) {
                            // Not a mime header. Ignore it.
                        }
                    }
                }
            }
            return new XMLMessage(mh, con.getInput());
        } catch (Exception e) {
            throw (HTTPException)new HTTPException(HttpURLConnection.HTTP_INTERNAL_ERROR).initCause(e);
        }
    }

    private static void send(WSConnection con, XMLMessage xmlMessage) {
        try {
            Map<String, List<String>> headers = new HashMap<String, List<String>>();
            MimeHeaders mhs = xmlMessage.getMimeHeaders();
            Iterator i = mhs.getAllHeaders();
            while (i.hasNext()) {
                MimeHeader mh = (MimeHeader) i.next();
                String name = mh.getName();
                List<String> values = headers.get(name);
                if (values == null) {
                    values = new ArrayList<String>();
                    headers.put(name, values);
                }
                values.add(mh.getValue());
            }
            con.setHeaders(headers);
            xmlMessage.writeTo(con.getOutput());

        } catch (Exception e) {
            throw (HTTPException)new HTTPException(HttpURLConnection.HTTP_INTERNAL_ERROR).initCause(e);
        }
        try {
            con.closeOutput();
        } catch (Exception e) {
            throw (HTTPException)new HTTPException(HttpURLConnection.HTTP_INTERNAL_ERROR).initCause(e);
        }
    }

    public static void sendResponse(WSConnection con, XMLMessage xmlMessage) {
        setStatus(con, xmlMessage.getStatus());
        send(con, xmlMessage);
    }

    public static void sendResponseOneway(WSConnection con) {
        setStatus(con, WSConnection.ONEWAY);
        con.getOutput();
        con.closeOutput();
    }

    public static void sendResponseError(WSConnection con, MessageInfo messageInfo) {
        try {
            StreamSource source = new StreamSource(
                new ByteArrayInputStream(DEFAULT_SERVER_ERROR.getBytes()));
            String conneg = (String) messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY);
            XMLMessage message = new XMLMessage(source, conneg == "optimistic");
            setStatus(con, WSConnection.INTERNAL_ERR);
            send(con, message);
        }
        catch (Exception e) {
            throw new WebServiceException(e);
        }
    }

    public static Map<String, List<String>> getHeaders(WSConnection con) {
        return con.getHeaders();
    }

    /**
     * sets response headers.
     */
    public static void setHeaders(WSConnection con,
                                  Map<String, List<String>> headers) {
        con.setHeaders(headers);
    }

    public static void setStatus(WSConnection con, int status) {
        con.setStatus(status);
    }

    private final static String DEFAULT_SERVER_ERROR =
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<err>Internal Server Error</err>";

}
