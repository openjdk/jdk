/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

package validation;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import javax.xml.XMLConstants;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;

/*
 * @test
 * @bug 6925531
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm validation.Bug6925531Test
 * @summary Test Validator can validate SAXSource when FEATURE_SECURE_PROCESSING is on.
 * Note that the run with the Java Security Manager was removed.
 */
public class Bug6925531Test {
    static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    static final String SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";
    String xsd = "<?xml version='1.0'?>\n" + "<schema xmlns='http://www.w3.org/2001/XMLSchema'\n"
            + "        xmlns:test='jaxp13_test'\n"
            + "        targetNamespace='jaxp13_test'\n"
            + "        elementFormDefault='qualified'>\n"
            + "    <element name='test' type='string'/>\n"
            + "</schema>\n";

    String xml = "<?xml version='1.0'?>\n"
            + "<ns:test xmlns:ns='jaxp13_test'>\n"
            + "    abc\n"
            + "</ns:test>\n";

    StreamSource xsdSource;
    SAXSource xmlSource;

    public void init() {
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(xsd.getBytes()));
        xsdSource = new StreamSource(reader);
        reader = new InputStreamReader(new ByteArrayInputStream(xml.getBytes()));
        InputSource inSource = new InputSource(reader);
        xmlSource = new SAXSource(inSource);
    }

    /**
     * Verifies validation with FEATURE_SECURE_PROCESSING (FSP) turned on explicitly
     * on SchemaFactory.
     * Note: the test with Java Security Manager was removed.
     * @throws Exception if the test fails
     */
    @Test
    public void test_SF() throws Exception {
        init();

        SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Schema schema = schemaFactory.newSchema(xsdSource);
        Validator validator = schema.newValidator();
        validator.validate(xmlSource, null);
    }

    /**
     * Verifies validation with FEATURE_SECURE_PROCESSING (FSP) turned on explicitly
     * on the Validator.
     * @throws Exception if the test fails
     */
    @Test
    public void test_Val() throws Exception {
        init();
        SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        Schema schema = schemaFactory.newSchema(xsdSource);
        Validator validator = schema.newValidator();
        validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        validator.validate(xmlSource, null);
    }
}
