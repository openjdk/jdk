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

package com.sun.tools.internal.xjc;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;

import com.sun.xml.internal.bind.v2.util.XmlFactory;
import javax.xml.XMLConstants;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import static com.sun.xml.internal.bind.v2.util.XmlFactory.allowExternalAccess;

/**
 * Wraps a JAXP {@link Schema} object and lazily instantiate it.
 *
 * This object is thread-safe. There should be only one instance of
 * this for the whole VM.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SchemaCache {

    private final boolean createResolver;
    private final String resourceName;
    private final Class<?> clazz;

    private Schema schema;

    public SchemaCache(String resourceName, Class<?> classToResolveResources) {
        this(resourceName, classToResolveResources, false);
    }

    public SchemaCache(String resourceName, Class<?> classToResolveResources, boolean createResolver) {
        this.resourceName = resourceName;
        this.createResolver = createResolver;
        this.clazz = classToResolveResources;
    }

    public ValidatorHandler newValidator() {
        if (schema==null) {
            synchronized (this) {
                if (schema == null) {

                    ResourceResolver resourceResolver = null;
                    try (InputStream is = clazz.getResourceAsStream(resourceName)) {

                        StreamSource source = new StreamSource(is);
                        source.setSystemId(resourceName);
                        // do not disable secure processing - these are well-known schemas

                        SchemaFactory sf = XmlFactory.createSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI, false);
                        SchemaFactory schemaFactory = allowExternalAccess(sf, "file", false);

                        if (createResolver) {
                            resourceResolver = new ResourceResolver(clazz);
                            schemaFactory.setResourceResolver(resourceResolver);
                        }
                        schema = schemaFactory.newSchema(source);

                    } catch (IOException | SAXException e) {
                        InternalError ie = new InternalError(e.getMessage());
                        ie.initCause(e);
                        throw ie;
                    } finally {
                        if (resourceResolver != null) resourceResolver.closeStreams();
                    }
                }
            }
        }
        return schema.newValidatorHandler();
    }

    class ResourceResolver implements LSResourceResolver {

        private List<InputStream> streamsToClose = Collections.synchronizedList(new ArrayList<InputStream>());
        private Class<?> clazz;

        ResourceResolver(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            // XSOM passes the namespace URI to the publicID parameter.
            // we do the same here .
            InputStream is = clazz.getResourceAsStream(systemId);
            streamsToClose.add(is);
            return new Input(is, publicId, systemId);
        }

        void closeStreams() {
            for (InputStream is : streamsToClose) {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // nothing to do ...
                    }
                }
            }
        }
    }

}

class Input implements LSInput {

    private InputStream is;
    private String publicId;
    private String systemId;

    public Input(InputStream is, String publicId, String systemId) {
        this.is = is;
        this.publicId = publicId;
        this.systemId = systemId;
    }

    @Override
    public Reader getCharacterStream() {
        return null;
    }

    @Override
    public void setCharacterStream(Reader characterStream) {
    }

    @Override
    public InputStream getByteStream() {
        return is;
    }

    @Override
    public void setByteStream(InputStream byteStream) {
    }

    @Override
    public String getStringData() {
        return null;
    }

    @Override
    public void setStringData(String stringData) {
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public void setSystemId(String systemId) {
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    @Override
    public void setPublicId(String publicId) {
    }

    @Override
    public String getBaseURI() {
        return null;
    }

    @Override
    public void setBaseURI(String baseURI) {
    }

    @Override
    public String getEncoding() {
        return null;
    }

    @Override
    public void setEncoding(String encoding) {
    }

    @Override
    public boolean getCertifiedText() {
        return false;
    }

    @Override
    public void setCertifiedText(boolean certifiedText) {
    }
}
