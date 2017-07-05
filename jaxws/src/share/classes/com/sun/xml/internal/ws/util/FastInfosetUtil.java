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

import java.io.OutputStream;
import java.io.StringReader;
import java.util.List;
import java.util.StringTokenizer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPException;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.messaging.saaj.soap.MessageImpl;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

import static com.sun.xml.internal.ws.developer.JAXWSProperties.CONTENT_NEGOTIATION_PROPERTY;

public class FastInfosetUtil {

    public static boolean isFastInfosetAccepted(String[] accepts) {
        if (accepts != null) {
            for (String accept : accepts) {
                if (isFastInfosetAccepted(accept)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isFastInfosetAccepted(String accept) {
        StringTokenizer st = new StringTokenizer(accept, ",");
        while (st.hasMoreTokens()) {
            final String token = st.nextToken().trim();
            if (token.equalsIgnoreCase("application/fastinfoset")) {
                return true;
            }
        }
        return false;
    }

    public static String getFastInfosetFromAccept(List<String> accepts) {
        for (String accept : accepts) {
            StringTokenizer st = new StringTokenizer(accept, ",");
            while (st.hasMoreTokens()) {
                final String token = st.nextToken().trim();
                if (token.equalsIgnoreCase("application/fastinfoset")) {
                    return "application/fastinfoset";
                }
                if (token.equalsIgnoreCase("application/soap+fastinfoset")) {
                    return "application/soap+fastinfoset";
                }
            }
        }
        return null;
    }

    public static void transcodeXMLStringToFI(String xml, OutputStream out) {
        try {
            XmlUtil.newTransformer().transform(
                new StreamSource(new java.io.StringReader(xml)),
                FastInfosetReflection.FastInfosetResult_new(out));
        }
        catch (Exception e) {
            // Ignore
        }
    }

    public static void ensureCorrectEncoding(MessageInfo messageInfo,
        SOAPMessage message)
    {
        String conneg = (String) messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY);
        if (conneg == "optimistic") {
            ((MessageImpl) message).setIsFastInfoset(true);
        }
    }

}
