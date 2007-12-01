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

package com.sun.xml.internal.ws.streaming;

import com.sun.xml.internal.ws.util.FastInfosetReflection;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.stream.XMLStreamReader;

/**
 * @author Santiago.PericasGeertsen@sun.com
 */
public class SourceReaderFactory {

    /**
     * Thread variable used to store DOMStreamReader for current thread.
     */
    static ThreadLocal<DOMStreamReader> domStreamReader =
        new ThreadLocal<DOMStreamReader>();

    public static XMLStreamReader createSourceReader(Source source,
        boolean rejectDTDs)
    {
        return createSourceReader(source, rejectDTDs, null);
    }

    public static XMLStreamReader createSourceReader(Source source,
        boolean rejectDTDs, String charsetName)
    {
        try {
            if (source instanceof StreamSource) {
                StreamSource streamSource = (StreamSource) source;
                InputStream is = streamSource.getInputStream();

                if (is != null) {
                    // Wrap input stream in Reader if charset is specified
                    if (charsetName != null) {
                        return XMLStreamReaderFactory.createXMLStreamReader(
                            new InputStreamReader(is, charsetName), rejectDTDs);
                    }
                    else {
                        return XMLStreamReaderFactory.createXMLStreamReader(is,
                            rejectDTDs);
                    }
                }
                else {
                    Reader reader = streamSource.getReader();
                    if (reader != null) {
                        return XMLStreamReaderFactory.createXMLStreamReader(reader,
                            rejectDTDs);
                    }
                    else {
                        throw new XMLReaderException("sourceReader.invalidSource",
                            new Object[] { source.getClass().getName() });
                    }
                }
            }
            else if (FastInfosetReflection.isFastInfosetSource(source)) {
                return XMLStreamReaderFactory.createFIStreamReader((InputStream)
                    FastInfosetReflection.FastInfosetSource_getInputStream(source));
            }
            else if (source instanceof DOMSource) {
                DOMStreamReader dsr = domStreamReader.get();
                if (dsr == null) {
                    domStreamReader.set(dsr = new DOMStreamReader());
                }
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
                    new Object[] { source.getClass().getName() });
            }
        }
        catch (Exception e) {
            throw new XMLReaderException(e);
        }
    }

}
