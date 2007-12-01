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

import org.xml.sax.InputSource;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.MalformedURLException;

import com.sun.xml.internal.ws.util.SunStAXReflection;
import com.sun.xml.internal.ws.util.FastInfosetReflection;

/**
 * <p>A factory to create XML and FI parsers.</p>
 *
 * @author Santiago.PericasGeertsen@sun.com
 */
public class XMLStreamReaderFactory {

    /**
     * StAX input factory shared by all threads.
     */
    static XMLInputFactory xmlInputFactory;

    /**
     * FI stream reader for each thread.
     */
    static ThreadLocal fiStreamReader = new ThreadLocal();

    /**
     * Zephyr's stream reader for each thread.
     */
    static ThreadLocal xmlStreamReader = new ThreadLocal();

    static {
        // Use StAX pluggability layer to get factory instance
        xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);

        try {
            // Turn OFF internal factory caching in Zephyr -- not thread safe
            xmlInputFactory.setProperty("reuse-instance", Boolean.FALSE);
        }
        catch (IllegalArgumentException e) {
            // falls through
        }
    }

    // -- XML ------------------------------------------------------------

    /**
     * Returns a fresh StAX parser created from an InputSource. Use this
     * method when concurrent instances are needed within a single thread.
     *
     * TODO: Reject DTDs?
     */
    public static XMLStreamReader createFreshXMLStreamReader(InputSource source,
        boolean rejectDTDs) {
        try {
            synchronized (xmlInputFactory) {
                // Char stream available?
                if (source.getCharacterStream() != null) {
                    return xmlInputFactory.createXMLStreamReader(
                        source.getSystemId(), source.getCharacterStream());
                }

                // Byte stream available?
                if (source.getByteStream() != null) {
                    return xmlInputFactory.createXMLStreamReader(
                        source.getSystemId(), source.getByteStream());
                }

                // Otherwise, open URI
                return xmlInputFactory.createXMLStreamReader(source.getSystemId(),
                    new URL(source.getSystemId()).openStream());
            }
        }
        catch (Exception e) {
            throw new XMLReaderException("stax.cantCreate",e);
        }
    }

    /**
     * This factory method would be used for example when caller wants to close the stream.
     */
    public static XMLStreamReader createFreshXMLStreamReader(String systemId, InputStream stream) {
        try {
            synchronized (xmlInputFactory) {
                // Otherwise, open URI
                return xmlInputFactory.createXMLStreamReader(systemId,
                    stream);
            }
        }
        catch (Exception e) {
            throw new XMLReaderException("stax.cantCreate",e);
        }
    }

    /**
     * This factory method would be used for example when caller wants to close the stream.
     */
    public static XMLStreamReader createFreshXMLStreamReader(String systemId, Reader reader) {
        try {
            synchronized (xmlInputFactory) {
                // Otherwise, open URI
                return xmlInputFactory.createXMLStreamReader(systemId,
                    reader);
            }
        }
        catch (Exception e) {
            throw new XMLReaderException("stax.cantCreate",e);
        }
    }



    /**
     * Returns a StAX parser from an InputStream.
     *
     * TODO: Reject DTDs?
     */
    public static XMLStreamReader createXMLStreamReader(InputStream in,
        boolean rejectDTDs) {
        return createXMLStreamReader(null, in, rejectDTDs);
    }

    /**
     * Returns a StAX parser from an InputStream. Attemps to re-use parsers if
     * underlying representation is Zephyr.
     *
     * TODO: Reject DTDs?
     */
    public static XMLStreamReader createXMLStreamReader(String systemId,
        InputStream in, boolean rejectDTDs) {
        try {
            // If using Zephyr, try re-using the last instance
            if (SunStAXReflection.XMLReaderImpl_setInputSource != null) {
                Object xsr = xmlStreamReader.get();
                if (xsr == null) {
                    synchronized (xmlInputFactory) {
                        xmlStreamReader.set(
                            xsr = xmlInputFactory.createXMLStreamReader(systemId, in));
                    }
                }
                else {
                    SunStAXReflection.XMLReaderImpl_reset.invoke(xsr);
                    InputSource inputSource = new InputSource(in);
                    inputSource.setSystemId(systemId);
                    SunStAXReflection.XMLReaderImpl_setInputSource.invoke(xsr, inputSource);
                }
                return (XMLStreamReader) xsr;
            }
            else {
                synchronized (xmlInputFactory) {
                    return xmlInputFactory.createXMLStreamReader(systemId, in);
                }
            }
        } catch (Exception e) {
            throw new XMLReaderException("stax.cantCreate",e);
        }
    }

    /**
     * Returns a StAX parser from a Reader. Attemps to re-use parsers if
     * underlying representation is Zephyr.
     *
     * TODO: Reject DTDs?
     */
    public static XMLStreamReader createXMLStreamReader(Reader reader,
        boolean rejectDTDs) {
        try {
            // If using Zephyr, try re-using the last instance
            if (SunStAXReflection.XMLReaderImpl_setInputSource != null) {
                Object xsr = xmlStreamReader.get();
                if (xsr == null) {
                    synchronized (xmlInputFactory) {
                        xmlStreamReader.set(
                            xsr = xmlInputFactory.createXMLStreamReader(reader));
                    }
                }
                else {
                    SunStAXReflection.XMLReaderImpl_reset.invoke(xsr);
                    SunStAXReflection.XMLReaderImpl_setInputSource.invoke(xsr, new InputSource(reader));
                }
                return (XMLStreamReader) xsr;
            }
            else {
                synchronized (xmlInputFactory) {
                    return xmlInputFactory.createXMLStreamReader(reader);
                }
            }
        }
        catch (Exception e) {
            throw new XMLReaderException("stax.cantCreate",e);
        }
    }

    // -- Fast Infoset ---------------------------------------------------

    public static XMLStreamReader createFIStreamReader(InputSource source) {
        return createFIStreamReader(source.getByteStream());
    }

    /**
     * Returns the FI parser allocated for this thread.
     */
    public static XMLStreamReader createFIStreamReader(InputStream in) {
        // Check if compatible implementation of FI was found
        if (FastInfosetReflection.fiStAXDocumentParser_new == null) {
            throw new XMLReaderException("fastinfoset.noImplementation");
        }

        try {
            Object sdp = fiStreamReader.get();
            if (sdp == null) {
                // Do not use StAX pluggable layer for FI
                fiStreamReader.set(sdp = FastInfosetReflection.fiStAXDocumentParser_new.newInstance());
                FastInfosetReflection.fiStAXDocumentParser_setStringInterning.invoke(sdp, Boolean.TRUE);
            }
            FastInfosetReflection.fiStAXDocumentParser_setInputStream.invoke(sdp, in);
            return (XMLStreamReader) sdp;
        }
        catch (Exception e) {
            throw new XMLStreamReaderException(e);
        }
    }

}
