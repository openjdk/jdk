/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.encoding;

import com.sun.xml.internal.ws.util.xml.XmlUtil;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;

/**
 * JAF data handler for XML content
 *
 * @author Jitendra Kotamraju
 */
public class XmlDataContentHandler implements DataContentHandler {

    private final DataFlavor[] flavors;

    public XmlDataContentHandler() throws ClassNotFoundException {
        flavors = new DataFlavor[3];
        flavors[0] = new ActivationDataFlavor(StreamSource.class, "text/xml", "XML");
        flavors[1] = new ActivationDataFlavor(StreamSource.class, "application/xml", "XML");
        flavors[2] = new ActivationDataFlavor(String.class, "text/xml", "XML String");
    }

    public DataFlavor[] getTransferDataFlavors() {
        return Arrays.copyOf(flavors, flavors.length);
    }

    public Object getTransferData(DataFlavor df, DataSource ds)
        throws IOException {

        for (DataFlavor aFlavor : flavors) {
            if (aFlavor.equals(df)) {
                return getContent(ds);
            }
        }
        return null;
    }

    /**
     * Create an object from the input stream
     */
    public Object getContent(DataSource ds) throws IOException {
        String ctStr = ds.getContentType();
        String charset = null;
        if (ctStr != null) {
            ContentType ct = new ContentType(ctStr);
            if (!isXml(ct)) {
                throw new IOException(
                    "Cannot convert DataSource with content type \""
                            + ctStr + "\" to object in XmlDataContentHandler");
            }
            charset = ct.getParameter("charset");
        }
        return (charset != null)
                ? new StreamSource(new InputStreamReader(ds.getInputStream()), charset)
                : new StreamSource(ds.getInputStream());
    }

    /**
     * Convert the object to a byte stream
     */
    public void writeTo(Object obj, String mimeType, OutputStream os)
        throws IOException {

        if (!(obj instanceof DataSource || obj instanceof Source || obj instanceof String)) {
             throw new IOException("Invalid Object type = "+obj.getClass()+
                ". XmlDataContentHandler can only convert DataSource|Source|String to XML.");
        }

        ContentType ct = new ContentType(mimeType);
        if (!isXml(ct)) {
            throw new IOException(
                "Invalid content type \"" + mimeType + "\" for XmlDataContentHandler");
        }

        String charset = ct.getParameter("charset");
        if (obj instanceof String) {
            String s = (String) obj;
            if (charset == null) {
                charset = "utf-8";
            }
            OutputStreamWriter osw = new OutputStreamWriter(os, charset);
            osw.write(s, 0, s.length());
            osw.flush();
            return;
        }

        Source source = (obj instanceof DataSource)
                ? (Source)getContent((DataSource)obj) : (Source)obj;
        try {
            Transformer transformer = XmlUtil.newTransformer();
            if (charset != null) {
                transformer.setOutputProperty(OutputKeys.ENCODING, charset);
            }
            StreamResult result = new StreamResult(os);
            transformer.transform(source, result);
        } catch (Exception ex) {
            throw new IOException(
                "Unable to run the JAXP transformer in XmlDataContentHandler "
                    + ex.getMessage());
        }
    }

    private boolean isXml(ContentType ct) {
        final String primaryType = ct.getPrimaryType();
        return ct.getSubType().equalsIgnoreCase("xml") &&
                    (primaryType.equalsIgnoreCase("text") || primaryType.equalsIgnoreCase("application"));
    }

}
