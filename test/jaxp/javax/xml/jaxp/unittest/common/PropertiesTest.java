/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package common;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import jaxp.library.JUnitTestUtil.Processor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.XMLReader;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8354774
 * @summary Verifies JAXP API Properties as specified in the java.xml module.
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest /test/lib
 * @run junit/othervm common.PropertiesTest
 */
public class PropertiesTest {
    private static final String ACCESS_EXTERNAL_DTD = XMLConstants.ACCESS_EXTERNAL_DTD;
    private static final String ACCESS_EXTERNAL_SCHEMA = XMLConstants.ACCESS_EXTERNAL_SCHEMA;
    private static final String ACCESS_EXTERNAL_STYLESHEET = XMLConstants.ACCESS_EXTERNAL_STYLESHEET;
    private static final String SP_ACCESS_EXTERNAL_DTD = "javax.xml.accessExternalDTD";
    private static final String SP_ACCESS_EXTERNAL_SCHEMA = "javax.xml.accessExternalSchema";
    private static final String SP_ACCESS_EXTERNAL_STYLESHEET = "javax.xml.accessExternalStylesheet";
    private static final String DEFAULT_VALUE = "all";
    /**
     * Returns test data for testAccessExternalProperties
     * @return test data for testAccessExternalProperties
     */
    private static Stream<Arguments> testData() {
        // Supported processors for Access External Properties
        Set<Processor> supportedProcessors1 = EnumSet.of(Processor.DOM, Processor.SAX, Processor.XMLREADER,
                Processor.StAX, Processor.VALIDATION);
        Set<Processor> supportedProcessors2 = EnumSet.of(Processor.TRANSFORM);

        return Stream.of(
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_DTD, null, SP_ACCESS_EXTERNAL_DTD, null, DEFAULT_VALUE),
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_DTD, "http", SP_ACCESS_EXTERNAL_DTD, null, "http"),
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_DTD, null, SP_ACCESS_EXTERNAL_DTD, "https", "https"),
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_DTD, "http", SP_ACCESS_EXTERNAL_DTD, "https", "http"),
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_SCHEMA, null, SP_ACCESS_EXTERNAL_SCHEMA, null, DEFAULT_VALUE),
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_SCHEMA, "http", SP_ACCESS_EXTERNAL_SCHEMA, null, "http"),
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_SCHEMA, null, SP_ACCESS_EXTERNAL_SCHEMA, "https", "https"),
                Arguments.of(supportedProcessors1, ACCESS_EXTERNAL_SCHEMA, "http", SP_ACCESS_EXTERNAL_SCHEMA, "https", "http"),
                Arguments.of(supportedProcessors2, ACCESS_EXTERNAL_STYLESHEET, null, SP_ACCESS_EXTERNAL_STYLESHEET, null, DEFAULT_VALUE),
                Arguments.of(supportedProcessors2, ACCESS_EXTERNAL_STYLESHEET, "http", SP_ACCESS_EXTERNAL_STYLESHEET, null, "http"),
                Arguments.of(supportedProcessors2, ACCESS_EXTERNAL_STYLESHEET, null, SP_ACCESS_EXTERNAL_STYLESHEET, "https", "https"),
                Arguments.of(supportedProcessors2, ACCESS_EXTERNAL_STYLESHEET, "http", SP_ACCESS_EXTERNAL_STYLESHEET, "https", "http")
        );
    }

    /**
     * Verifies that the Access External Properties are supported throughout the
     * JAXP APIs.
     * @param supportedProcessors the supported processors for the property
     * @param apiProperty the API property
     * @param apiValue the value of the API property
     * @param sysProperty the System property corresponding to the API property
     * @param sysValue the value of the System property
     * @param expected the expected result
     * @throws Exception if the test fails due to test configuration issues other
     * than the expected result
     */
    @ParameterizedTest
    @MethodSource("testData")
    public void testAccessExternalProperties(Set<Processor> supportedProcessors,
           String apiProperty, String apiValue, String sysProperty, String sysValue,
           String expected) throws Exception {
        for (Processor p : supportedProcessors) {
            testProperties(p, apiProperty, apiValue, sysProperty, sysValue,
                    expected);
        }
    }

    /**
     * Verifies that properties can be set via the JAXP APIs and their corresponding
     * System Properties.
     * @param processor the processor type
     * @param apiProperty the API property
     * @param apiValue the value to be set via the API property
     * @param sysProperty the System Property
     * @param sysValue the value to be set via the System property
     * @param expected the expected result
     * @throws Exception if the test fails, which can only happen if the property
     * is set incorrectly.
     */
    void testProperties(Processor processor, String apiProperty, String apiValue,
            String sysProperty, String sysValue, String expected)
            throws Exception {
        Object ret1 = null;
        if (sysValue != null) {
            System.setProperty(sysProperty, sysValue);
        }
        switch (processor) {
            case DOM:
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
                if (apiValue != null) dbf.setAttribute(apiProperty, apiValue);
                ret1 = dbf.getAttribute(apiProperty);
                break;
            case SAX:
                SAXParser sp = SAXParserFactory.newDefaultInstance().newSAXParser();
                if (apiValue != null) sp.setProperty(apiProperty, apiValue);
                ret1 = sp.getProperty(apiProperty);
                break;
            case XMLREADER:
                XMLReader reader = SAXParserFactory.newDefaultInstance().newSAXParser().getXMLReader();
                if (apiValue != null) reader.setProperty(apiProperty, apiValue);
                ret1 = reader.getProperty(apiProperty);
                break;
            case StAX:
                XMLInputFactory xif = XMLInputFactory.newDefaultFactory();
                if (apiValue != null) xif.setProperty(apiProperty, apiValue);
                ret1 = xif.getProperty(apiProperty);
                break;
            case VALIDATION:
                SchemaFactory sf = SchemaFactory.newDefaultInstance();
                if (apiValue != null) sf.setProperty(apiProperty, apiValue);
                ret1 = sf.getProperty(apiProperty);
                break;
            case TRANSFORM:
                TransformerFactory tf = TransformerFactory.newDefaultInstance();
                if (apiValue != null) tf.setAttribute(apiProperty, apiValue);
                ret1 = tf.getAttribute(apiProperty);
                break;
        }

        if (sysValue != null) System.clearProperty(sysProperty);
        // property value is as expected
        assertEquals(expected, ret1);
    }
}
