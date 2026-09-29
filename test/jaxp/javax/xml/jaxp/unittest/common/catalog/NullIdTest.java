/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package common.catalog;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.catalog.CatalogFeatures;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static javax.xml.catalog.CatalogManager.catalogResolver;
import javax.xml.catalog.CatalogResolver;
import javax.xml.validation.Validator;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8323571
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng common.catalog.NullIdTest
 * @summary Verifies null values are handled properly in the source resolution
 * process.
 */
public class NullIdTest {
    private static final Map<String, String> SCHEMAS;
    // Source Level JDK 8
    static {
        Map<String, String> map = new HashMap<>();
        map.put("https://schemas.opentest4j.org/reporting/events/0.1.0", "events.xsd");
        map.put("https://schemas.opentest4j.org/reporting/core/0.1.0", "core.xsd");
        SCHEMAS = Collections.unmodifiableMap(map);
    }

    /*
     * Verifies that the source resolution process recognizes the custom InputSource
     * correctly even though the public and system IDs are null.
    */
    @Test
    public void test() throws Exception {
        String xml = "<events xmlns=\"https://schemas.opentest4j.org/reporting/events/0.1.0\"/>";
        validate(new StreamSource(new StringReader(xml)));
        System.out.println("Successfully validated");
    }

    private static void validate(Source source) throws SAXException, IOException {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
        Validator validator = schemaFactory.newSchema().newValidator();
        validator.setResourceResolver(createResourceResolver());
        validator.validate(source);
    }

    private static LSResourceResolver createResourceResolver() {
        return (type, namespaceURI, publicId, systemId, baseURI) -> {
            if (namespaceURI != null) {
                if (SCHEMAS.containsKey(namespaceURI)) {
                    CustomLSInputImpl input = new CustomLSInputImpl();
                    input.setPublicId(publicId);
                    String schema = SCHEMAS.get(namespaceURI);
                    input.setSystemId(requireNonNull(NullIdTest.class.getResource(schema)).toExternalForm());
                    input.setBaseURI(baseURI);
                    InputStream stream = NullIdTest.class.getResourceAsStream(schema);
                    input.setCharacterStream(new InputStreamReader(requireNonNull(stream)));
                    return input;
                }
            }
            if (systemId != null) {
                CatalogFeatures features = CatalogFeatures.builder()
                        .with(CatalogFeatures.Feature.RESOLVE, "continue")
                        .build();
                CatalogResolver catalogResolver = catalogResolver(features);
                return catalogResolver.resolveResource(type, namespaceURI, publicId, systemId, baseURI);
            }
            return null;
        };
    }

    static class CustomLSInputImpl implements LSInput {

        private Reader characterStream;
        private InputStream byteStream;
        private String stringData;
        private String systemId;
        private String publicId;
        private String baseURI;
        private String encoding;
        private boolean certifiedText;

        @Override
        public Reader getCharacterStream() {
            return characterStream;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            this.characterStream = characterStream;
        }

        @Override
        public InputStream getByteStream() {
            return byteStream;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            this.byteStream = byteStream;
        }

        @Override
        public String getStringData() {
            return stringData;
        }

        @Override
        public void setStringData(String stringData) {
            this.stringData = stringData;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            this.systemId = systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            this.publicId = publicId;
        }

        @Override
        public String getBaseURI() {
            return baseURI;
        }

        @Override
        public void setBaseURI(String baseURI) {
            this.baseURI = baseURI;
        }

        @Override
        public String getEncoding() {
            return encoding;
        }

        @Override
        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        @Override
        public boolean getCertifiedText() {
            return certifiedText;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            this.certifiedText = certifiedText;
        }
    }
}
