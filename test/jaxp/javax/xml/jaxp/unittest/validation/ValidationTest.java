/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


import java.io.File;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.testng.annotations.DataProvider;

import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.xml.sax.SAXParseException;

/*
 * @test
 * @bug 8220818
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm validation.ValidationTest
 * @summary Runs validations with schemas and sources
 */
@Listeners({jaxp.library.FilePolicy.class})
public class ValidationTest {
    static final String FILE_PATH = "files/";
    /*
     DataProvider: valid xml
     */
    @DataProvider(name = "valid")
    Object[][] getValid() {
        return new Object[][]{
            {"JDK8220818a.xsd", "JDK8220818a_Valid.xml"},
            {"JDK8220818a.xsd", "JDK8220818a_Valid1.xml"},
            {"JDK8220818b.xsd", "JDK8220818b_Valid.xml"},
        };
    }

    /*
     DataProvider: invalid xml
     */
    @DataProvider(name = "invalid")
    Object[][] getInvalid() {
        return new Object[][]{
            {"JDK8220818a.xsd", "JDK8220818a_Invalid.xml"},
            {"JDK8220818b.xsd", "JDK8220818b_Invalid.xml"},
        };
    }

    @Test(dataProvider = "invalid", expectedExceptions = SAXParseException.class)
    public void testValidateRefType(String xsd, String xml) throws Exception {
        validate(xsd, xml);
    }

    @Test(dataProvider = "valid")
    public void testValidateRefType1(String xsd, String xml) throws Exception {
        validate(xsd, xml);
    }

    private void validate(String xsd, String xml) throws Exception {
        final SchemaFactory schemaFactory = SchemaFactory.newInstance(
                XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final Schema schema = schemaFactory.newSchema(
                new File(getClass().getResource(FILE_PATH + xsd).getFile()));
        final Validator validator = schema.newValidator();
        validator.validate(new StreamSource(
                new File(getClass().getResource(FILE_PATH + xml).getFile())));
    }
}
