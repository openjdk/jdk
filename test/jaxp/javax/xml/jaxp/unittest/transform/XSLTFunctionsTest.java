/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

package transform;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import static jaxp.library.JAXPTestUtilities.SRC_DIR;
import static jaxp.library.JAXPTestUtilities.assertDoesNotThrow;
import static jaxp.library.JAXPTestUtilities.getSystemId;
import static jaxp.library.JAXPTestUtilities.getSystemProperty;
import org.testng.Assert;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8062518 8153082 8165116 8343001
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @compile DocumentExtFunc.java
 * @run testng/othervm transform.XSLTFunctionsTest
 * @summary This class contains tests for XSLT functions.
 */

public class XSLTFunctionsTest {
    @DataProvider(name = "propertyName")
    public static Object[][] getSettings() {
        return new Object[][] {
            // legacy property name
            {ORACLE_ENABLE_EXTENSION_FUNCTION, true, true, null},
            {ORACLE_ENABLE_EXTENSION_FUNCTION, true, false, TransformerException.class},
            // legacy system property name
            {SP_ENABLE_EXTENSION_FUNCTION, false, true, null},
            {SP_ENABLE_EXTENSION_FUNCTION, false, false, TransformerException.class},
            // current property and system property name
            {SP_ENABLE_EXTENSION_FUNCTION_SPEC, true, true, null},
            {SP_ENABLE_EXTENSION_FUNCTION_SPEC, true, false, TransformerException.class},
        };
    }

    /**
     * @bug 8165116
     * Verifies that redirect works properly when extension function is enabled
     *
     * @param xml the XML source
     * @param xsl the stylesheet that redirect output to a file
     * @param output the output file
     * @param redirect the redirect file
     * @throws Exception if the test fails
     **/
    @Test(dataProvider = "redirect")
    public void testRedirect(String xml, String xsl, String output, String redirect) throws Exception {

        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(ORACLE_ENABLE_EXTENSION_FUNCTION, true);
        Transformer t = tf.newTransformer(new StreamSource(new StringReader(xsl)));

        //Transform the xml
        t.transform(new StreamSource(new StringReader(xml)), new StreamResult(new StringWriter()));

        // Verifies that the output is redirected successfully
        String userDir = getSystemProperty("user.dir");
        Path pathOutput = Paths.get(userDir, output);
        Path pathRedirect = Paths.get(userDir, redirect);
        Assert.assertTrue(Files.exists(pathOutput));
        Assert.assertTrue(Files.exists(pathRedirect));
        System.out.println("Output to " + pathOutput + " successful.");
        System.out.println("Redirect to " + pathRedirect + " successful.");
        Files.deleteIfExists(pathOutput);
        Files.deleteIfExists(pathRedirect);
    }

    /**
     * @bug 8161454 8343001
     * Verifies that legacy property names are continually supported for compatibility.
     *
     * @param property the property name
     * @param isAPIProperty indicates whether the property can be set via the factory
     * @param value the property value
     * @param expectedThrow the expected throw if the specified DTD can not be
     *                      resolved.
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "propertyName")
    public void testNameChange(String property, boolean isAPIProperty,
            boolean value, Class<Throwable> expectedThrow)
            throws Exception {
        if (expectedThrow == null) {
            assertDoesNotThrow(() -> runTransform(property, isAPIProperty, value),
                    "Extension Functions property is set to " + value + " but exception is thrown.");
        } else {
            Assert.assertThrows(expectedThrow,
                () -> runTransform(property, isAPIProperty, value));
        }
    }

    private void runTransform(String property, boolean isAPIProperty, boolean value)
            throws Exception {
        StreamSource xslSource = new StreamSource(getSystemId(SRC_DIR + "/SecureProcessingTest.xsl"));
        StreamSource xmlSource = new StreamSource(getSystemId(SRC_DIR + "/SecureProcessingTest.xml"));

        // the xml result
        StringWriter xmlResultString = new StringWriter();
        StreamResult xmlResultStream = new StreamResult(xmlResultString);

        if (!isAPIProperty) {
            System.setProperty(property, Boolean.toString(value));
        }
        TransformerFactory tf = TransformerFactory.newInstance();
        if (isAPIProperty) {
            tf.setFeature(property, value);
        }
        Transformer transformer = tf.newTransformer(xslSource);
        if (!isAPIProperty) {
            System.clearProperty(property);
        }
        transformer.transform(xmlSource, xmlResultStream);
    }

    /**
     * @bug 8062518 8153082
     * Verifies that a reference to the DTM created by XSLT document function is
     * actually read from the DTM by an extension function.
     * @param xml Content of xml file to process
     * @param xsl stylesheet content that loads external document {@code externalDoc}
     *        with XSLT 'document' function and then reads it with
     *        DocumentExtFunc.test() function
     * @param externalDoc Content of the external xml document
     * @param expectedResult Expected transformation result
     **/
    @Test(dataProvider = "document")
    public void testDocument(final String xml, final String xsl,
                             final String externalDoc, final String expectedResult) throws Exception {
        // Prepare sources for transormation
        Source src = new StreamSource(new StringReader(xml));
        Source xslsrc = new StreamSource(new StringReader(xsl));

        // Create factory and transformer
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(ORACLE_ENABLE_EXTENSION_FUNCTION, true);
        tf.setAttribute(EXTENSION_CLASS_LOADER,
                Thread.currentThread().getContextClassLoader());
        Transformer t = tf.newTransformer( xslsrc );
        t.setErrorListener(tf.getErrorListener());

        // Set URI Resolver to return the newly constructed xml
        // stream source object from xml test string
        t.setURIResolver(new URIResolver() {
            @Override
            public Source resolve(String href, String base)
                    throws TransformerException {
                if (href.contains("externalDoc")) {
                    return new StreamSource(new StringReader(externalDoc));
                } else {
                    return new StreamSource(new StringReader(xml));
                }
            }
        });

        // Prepare output stream
        StringWriter xmlResultString = new StringWriter();
        StreamResult xmlResultStream = new StreamResult(xmlResultString);

        //Transform the xml
        t.transform(src, xmlResultStream);

        // If the document can't be accessed and the bug is in place then
        // reported exception will be thrown during transformation
        System.out.println("Transformation result:"+xmlResultString.toString().trim());

        // Check the result - it should contain two (node name, node values) entries -
        // one for original document, another for a document created with
        // call to 'document' function
        assertEquals(xmlResultString.toString().trim(), expectedResult);
    }

    @DataProvider(name = "document")
    public static Object[][] documentTestData() {
        return new Object[][] {
            {documentTestXml, documentTestXsl, documentTestExternalDoc, documentTesteExpectedResult},
        };
    }

    @DataProvider(name = "redirect")
    public static Object[][] getData() {
        return new Object[][] {
            {documentTestXml, xslRedirect, "testoutput.xml", "testredirect.xml"},
        };
    }

    static final String documentTestXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Test>Doc</Test>";

    static final String documentTestExternalDoc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Test>External Doc</Test>";

    static final String documentTestXsl = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<xsl:transform version=\"1.0\""
            + " xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
            + " xmlns:cfunc=\"http://xml.apache.org/xalan/java/\">"
            + "<xsl:template match=\"/\">"
            + "<xsl:element name=\"root\">"
            + "<xsl:variable name=\"other_doc\" select=\"document(&#39;externalDoc&#39;)\"/>"
            + "<!-- Source -->"
            + "<xsl:value-of select=\"cfunc:transform.DocumentExtFunc.test(/Test)\"/>"
            + "<!-- document() -->"
            + "<xsl:value-of select=\"cfunc:transform.DocumentExtFunc.test($other_doc/Test)\"/>"
            + "</xsl:element></xsl:template></xsl:transform>";

    static final String documentTesteExpectedResult = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                                    + "<root>[Test:Doc][Test:External Doc]</root>";

    static String xslRedirect = " <xsl:stylesheet \n"
            + "   xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
            + "   xmlns:xsltc=\"http://xml.apache.org/xalan/xsltc\"\n"
            + "   xmlns:redirect=\"http://xml.apache.org/xalan/redirect\"\n"
            + "   extension-element-prefixes=\"xsltc redirect\"\n"
            + "   version=\"1.0\">\n"
            + "   <xsl:template match=\"/\">\n"
            + "     <xsl:text>This goes to standard output</xsl:text>\n"
            + "     <xsltc:output file=\"testoutput.xml\">\n"
            + "       <xsl:text>This ends up in the file 'testoutput.xml'</xsl:text>\n"
            + "     </xsltc:output>\n"
            + "     <redirect:write file=\"testredirect.xml\">\n"
            + "       <xsl:text>This ends up in the file 'testredirect.xml'</xsl:text>\n"
            + "     </redirect:write>\n"
            + "   </xsl:template>\n"
            + "</xsl:stylesheet>";

    public static final String ORACLE_JAXP_PROPERTY_PREFIX =
        "http://www.oracle.com/xml/jaxp/properties/";
    /**
     * Feature enableExtensionFunctions
     */
    public static final String ORACLE_ENABLE_EXTENSION_FUNCTION =
            ORACLE_JAXP_PROPERTY_PREFIX + "enableExtensionFunctions";
    static final String SP_ENABLE_EXTENSION_FUNCTION = "javax.xml.enableExtensionFunctions";
    // This is the correct name by the spec
    static final String SP_ENABLE_EXTENSION_FUNCTION_SPEC = "jdk.xml.enableExtensionFunctions";
    private static final String EXTENSION_CLASS_LOADER = "jdk.xml.transform.extensionClassLoader";
}
