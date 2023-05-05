/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import static java.util.Collections.unmodifiableList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/*
 * @test
 * @bug 8298087
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng validation.ErrorHandlingTest
 * @summary Verifies error handling.
 */
public class ErrorHandlingTest {
    private final static String xsd = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
            +"<schema xmlns=\"http://www.w3.org/2001/XMLSchema\""
            +"       targetNamespace=\"https://errorhandlingtest/schema/Example\""
            +"        elementFormDefault=\"qualified\">"
            +"\n"
            +"    <element name=\"root\">"
            +"        <complexType>"
            +"            <sequence>"
            +"                <element name=\"a\">"
            +"                    <complexType>"
            +"                        <simpleContent>"
            +"                            <extension base=\"string\">"
            +"                                <attribute name=\"enabled\" type=\"boolean\" use=\"required\"/>"
            +"                            </extension>"
            +"                        </simpleContent>"
            +"                    </complexType>"
            +"                </element>"
            +"            </sequence>"
            +"        </complexType>"
            +"    </element>"
            +"\n"
            +"</schema>";

    private final static String xml = "<e:root xmlns:e=\"https://errorhandlingtest/schema/Example\">\n"
            +"    <e:a>string</e:a>\n"
            +"</e:root>";

    /**
     * Verifies that validation error is reported properly (once rather than twice).
     *
     * @throws Exception if the test fails
     */
    @Test
    public void test() throws Exception {
        List<SAXParseException> ex = validateXMLWithSchema(new StreamSource(new StringReader(xsd)),
                new StreamSource(new StringReader(xml)));
        Assert.assertEquals(ex.size(), 1);
    }

    public static List<SAXParseException> validateXMLWithSchema(final Source xsd, final Source xml) {
        final List<SAXParseException> exceptions = new ArrayList<>();
        try {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final Schema schema = factory.newSchema(xsd);
            final Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(final SAXParseException exception) {
                    System.err.printf("Warning: %s%n", exception);
                    exceptions.add(exception);
                }

                @Override
                public void error(final SAXParseException exception) {
                    System.err.printf("Error: %s%n", exception);
                    exceptions.add(exception);
                }

                @Override
                public void fatalError(final SAXParseException exception) {
                    System.err.printf("Fatal: %s%n", exception);
                    exceptions.add(exception);
                }
            });

            validator.validate(xml);

        } catch (final SAXException | IOException e) {
            System.err.printf("Exception: %s%n", e.getMessage());
        }
        return unmodifiableList(exceptions);
    }

}
