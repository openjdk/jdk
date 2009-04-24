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

package com.sun.xml.internal.ws.streaming;

import com.sun.istack.internal.Nullable;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import java.util.Map;
import java.io.OutputStream;

/**
 * <p>XMLStreamWriterUtil provides some utility methods intended to be used
 * in conjunction with a StAX XMLStreamWriter. </p>
 *
 * @author Santiago.PericasGeertsen@sun.com
 */
public class XMLStreamWriterUtil {

    private XMLStreamWriterUtil() {
    }

    /**
     * Gives the underlying stream for XMLStreamWriter. It closes any start elements, and returns the stream so
     * that JAXB can write infoset directly to the stream.
     *
     * @param writer XMLStreamWriter for which stream is required
     * @return  underlying OutputStream, null if writer doesn't provide a way to get it
     * @throws XMLStreamException if any of writer operations throw the exception
     */
    public static @Nullable OutputStream getOutputStream(XMLStreamWriter writer) throws XMLStreamException {
        // SJSXP
        if (writer instanceof Map) {
            Object obj = ((Map) writer).get("sjsxp-outputstream");
            if (obj != null) {
                writer.writeCharacters("");  // Force completion of open elems
                return (OutputStream)obj;
            }
        }
        // woodstox
        try {
            Object obj = writer.getProperty("com.ctc.wstx.outputUnderlyingStream");
            if (obj != null) {
                writer.writeCharacters("");  // Force completion of open elems
                writer.flush();
                return (OutputStream)obj;
            }
        } catch(Exception ie) {
            //Above property lookup causes NPE on JDK6u1, should be ignored.
            // We should not fail due to such lookups.

            // nothing to do here
        }
        return null;
    }


    public static String encodeQName(XMLStreamWriter writer, QName qname,
        PrefixFactory prefixFactory)
    {
        // NOTE: Here it is assumed that we do not serialize using default
        // namespace declarations and therefore that writer.getPrefix will
        // never return ""

        try {
            String namespaceURI = qname.getNamespaceURI();
            String localPart = qname.getLocalPart();

            if (namespaceURI == null || namespaceURI.equals("")) {
                return localPart;
            }
            else {
                String prefix = writer.getPrefix(namespaceURI);
                if (prefix == null) {
                    prefix = prefixFactory.getPrefix(namespaceURI);
                    writer.writeNamespace(prefix, namespaceURI);
                }
                return prefix + ":" + localPart;
            }
        }
        catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }
}
