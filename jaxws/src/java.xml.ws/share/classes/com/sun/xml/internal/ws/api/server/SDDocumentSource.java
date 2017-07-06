/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.server;

import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.streaming.TidyXMLStreamReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * SPI that provides the source of {@link SDDocument}.
 *
 * <p>
 * This abstract class could be implemented by applications, or one of the
 * {@link #create} methods can be used.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SDDocumentSource {
    /**
     * Returns the {@link XMLStreamReader} that reads the document.
     *
     * <p>
     * This method maybe invoked multiple times concurrently.
     *
     * @param xif
     *      The implementation may choose to use this object when it wants to
     *      create a new parser (or it can just ignore this parameter completely.)
     * @return
     *      The caller is responsible for closing the reader to avoid resource leak.
     *
     * @throws XMLStreamException
     *      if something goes wrong while creating a parser.
     * @throws IOException
     *      if something goes wrong trying to read the document.
     */
    public abstract XMLStreamReader read(XMLInputFactory xif) throws IOException, XMLStreamException;

    /**
     * Returns the {@link XMLStreamReader} that reads the document.
     *
     * <p>
     * This method maybe invoked multiple times concurrently.
     *
     * @return
     *      The caller is responsible for closing the reader to avoid resource leak.
     *
     * @throws XMLStreamException
     *      if something goes wrong while creating a parser.
     * @throws IOException
     *      if something goes wrong trying to read the document.
     */
    public abstract XMLStreamReader read() throws IOException, XMLStreamException;

    /**
     * System ID of this document.
     * @return
     */
    public abstract URL getSystemId();

    public static SDDocumentSource create(final Class<?> implClass, final String wsdlLocation) {
        ClassLoader cl = implClass.getClassLoader();
        URL url = cl.getResource(wsdlLocation);
        if (url != null) {
            return create(url);
        } else {
            return create(wsdlLocation, implClass);
        }
    }

    /**
     * Creates {@link SDDocumentSource} from an URL.
     * @param url
     * @return
     */
    public static SDDocumentSource create(final URL url) {
        return new SDDocumentSource() {
            private final URL systemId = url;

            @Override
            public XMLStreamReader read(XMLInputFactory xif) throws IOException, XMLStreamException {
                InputStream is = url.openStream();
                return new TidyXMLStreamReader(
                    xif.createXMLStreamReader(systemId.toExternalForm(),is), is);
            }

            @Override
            public XMLStreamReader read() throws IOException, XMLStreamException {
                InputStream is = url.openStream();
                return new TidyXMLStreamReader(
                   XMLStreamReaderFactory.create(systemId.toExternalForm(),is,false), is);
            }

            @Override
            public URL getSystemId() {
                return systemId;
            }
        };
    }

    /**
     * Creates {@link SDDocumentSource} from resource path using resolvingClass to read the resource.
     * Required for Jigsaw runtime.
     *
     * @param resolvingClass class used to read resource
     * @param path resource path
     */
    private static SDDocumentSource create(final String path, final Class<?> resolvingClass) {
        return new SDDocumentSource() {

            @Override
            public XMLStreamReader read(XMLInputFactory xif) throws IOException, XMLStreamException {
                InputStream is = inputStream();
                return new TidyXMLStreamReader(xif.createXMLStreamReader(path,is), is);
            }

            @Override
            public XMLStreamReader read() throws IOException, XMLStreamException {
                InputStream is = inputStream();
                return new TidyXMLStreamReader(XMLStreamReaderFactory.create(path,is,false), is);
            }

            @Override
            public URL getSystemId() {
                try {
                    return new URL("file://" + path);
                } catch (MalformedURLException e) {
                    return null;
                }
            }

            private InputStream inputStream() throws IOException {
                java.lang.Module module = resolvingClass.getModule();
                InputStream stream = module.getResourceAsStream(path);
                if (stream != null) {
                    return stream;
                }
                throw new ServerRtException("cannot.load.wsdl", path);
            }

        };
    }

    /**
     * Creates a {@link SDDocumentSource} from {@link XMLStreamBuffer}.
     * @param systemId
     * @param xsb
     * @return
     */
    public static SDDocumentSource create(final URL systemId, final XMLStreamBuffer xsb) {
        return new SDDocumentSource() {
            @Override
            public XMLStreamReader read(XMLInputFactory xif) throws XMLStreamException {
                return xsb.readAsXMLStreamReader();
            }

            @Override
            public XMLStreamReader read() throws XMLStreamException {
                return xsb.readAsXMLStreamReader();
            }

            @Override
            public URL getSystemId() {
                return systemId;
            }
        };
    }
}
