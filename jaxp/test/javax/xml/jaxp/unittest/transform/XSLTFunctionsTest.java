/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/*
 * @summary This class contains tests for XSLT functions.
 */

public class XSLTFunctionsTest {

    /**
     * @bug 8062518
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
}
