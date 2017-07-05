/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.streaming;

import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.util.FastInfosetUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URL;

/**
 * @author Santiago.PericasGeertsen@sun.com
 */
public class SourceReaderFactory {

    /**
     * FI FastInfosetSource class.
     */
    static Class fastInfosetSourceClass;

    /**
     * FI <code>StAXDocumentSerializer.setEncoding()</code> method via reflection.
     */
    static Method fastInfosetSource_getInputStream;

    static {
        // Use reflection to avoid static dependency with FI jar
        try {
            fastInfosetSourceClass =
                Class.forName("com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetSource");
            fastInfosetSource_getInputStream =
                fastInfosetSourceClass.getMethod("getInputStream");
        }
        catch (Exception e) {
            fastInfosetSourceClass = null;
        }
    }

    public static XMLStreamReader createSourceReader(Source source, boolean rejectDTDs) {
        return createSourceReader(source, rejectDTDs, null);
    }

    public static XMLStreamReader createSourceReader(Source source, boolean rejectDTDs, String charsetName) {
        try {
            if (source instanceof StreamSource) {
                StreamSource streamSource = (StreamSource) source;
                InputStream is = streamSource.getInputStream();

                if (is != null) {
                    // Wrap input stream in Reader if charset is specified
                    if (charsetName != null) {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), new InputStreamReader(is, charsetName), rejectDTDs);
                    }
                    else {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), is, rejectDTDs);
                    }
                }
                else {
                    Reader reader = streamSource.getReader();
                    if (reader != null) {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), reader, rejectDTDs);
                    }
                    else {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), new URL(source.getSystemId()).openStream(), rejectDTDs );
                    }
                }
            }
            else if (source.getClass() == fastInfosetSourceClass) {
                return FastInfosetUtil.createFIStreamReader((InputStream)
                    fastInfosetSource_getInputStream.invoke(source));
            }
            else if (source instanceof DOMSource) {
                DOMStreamReader dsr =  new DOMStreamReader();
                dsr.setCurrentNode(((DOMSource) source).getNode());
                return dsr;
            }
            else if (source instanceof SAXSource) {
                // TODO: need SAX to StAX adapter here -- Use transformer for now
                Transformer tx =  XmlUtil.newTransformer();
                DOMResult domResult = new DOMResult();
                tx.transform(source, domResult);
                return createSourceReader(
                    new DOMSource(domResult.getNode()),
                    rejectDTDs);
            }
            else {
                throw new XMLReaderException("sourceReader.invalidSource",
                        source.getClass().getName());
            }
        }
        catch (Exception e) {
            throw new XMLReaderException(e);
        }
    }

}
