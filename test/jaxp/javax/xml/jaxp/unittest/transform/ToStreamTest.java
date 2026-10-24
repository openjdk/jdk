/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

/*
 * @test
 * @bug 8387291
 * @run testng transform.ToStreamTest
 * @summary HTML XSLT output with accented UTF-8 characters must not produce
 *          named HTML entities (e.g. &eacute;) that are undefined in XML.
 *          Characters representable in the output encoding must be written
 *          literally.
 */

package transform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ToStreamTest {

    /*
     * XML source that reproduces the original issue.
     *
     * The accented characters are present in an attribute value:
     *   é (U+00E9) in "complément" and "appétissants"
     */
    private static final String XML_WITH_ACCENTS =
            "<Order DocumentType=\"0001\" EnterpriseCode=\"XYZ\">\n" +
            "  <OrderLines>\n" +
            "    <OrderLine OrderedQty=\"1\" PrimeLineNo=\"1\" SubLineNo=\"1\">\n" +
            "      <Item ItemID=\"XYZItem10\" UnitOfMeasure=\"EACH\"\n" +
            "            ItemShortDesc=\"75 compl\u00E9ments premium et app\u00E9tissants au poulet 300 gr\"/>\n" +
            "    </OrderLine>\n" +
            "  </OrderLines>\n" +
            "  <PersonInfoShipTo Country=\"US\"/>\n" +
            "  <PersonInfoBillTo Country=\"US\"/>\n" +
            "</Order>\n";

    /*
     * XSLT stylesheet that reproduces the original issue.
     *
     * The important part is:
     *     method="html" encoding="UTF-8"
     */
    private static final String XSL_HTML_UTF8 =
            "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\">\n" +
            "  <xsl:output method=\"html\" encoding=\"UTF-8\" indent=\"yes\"/>\n" +
            "  <xsl:template match=\"/\">\n" +
            "    <xsl:apply-templates select=\"Order\"/>\n" +
            "  </xsl:template>\n" +
            "  <xsl:template match=\"Order\">\n" +
            "    <Order>\n" +
            "      <xsl:copy-of select=\"@*\"/>\n" +
            "      <xsl:apply-templates select=\"OrderLines\"/>\n" +
            "      <xsl:apply-templates select=\"PersonInfoShipTo\"/>\n" +
            "      <xsl:apply-templates select=\"PersonInfoBillTo\"/>\n" +
            "    </Order>\n" +
            "  </xsl:template>\n" +
            "  <xsl:template match=\"OrderLines\">\n" +
            "    <OrderLines>\n" +
            "      <xsl:apply-templates select=\"OrderLine\"/>\n" +
            "    </OrderLines>\n" +
            "  </xsl:template>\n" +
            "  <xsl:template match=\"OrderLine\">\n" +
            "    <OrderLine>\n" +
            "      <xsl:copy-of select=\"@*\"/>\n" +
            "      <xsl:apply-templates select=\"Item\"/>\n" +
            "    </OrderLine>\n" +
            "  </xsl:template>\n" +
            "  <xsl:template match=\"Item\">\n" +
            "    <Item>\n" +
            "      <xsl:copy-of select=\"@*\"/>\n" +
            "      <xsl:attribute name=\"ManufacturerItemDesc\">\n" +
            "        <xsl:value-of select=\"@ItemShortDesc\"/>\n" +
            "      </xsl:attribute>\n" +
            "    </Item>\n" +
            "  </xsl:template>\n" +
            "  <xsl:template match=\"PersonInfoShipTo | PersonInfoBillTo\">\n" +
            "    <xsl:copy>\n" +
            "      <xsl:copy-of select=\"@*\"/>\n" +
            "    </xsl:copy>\n" +
            "  </xsl:template>\n" +
            "</xsl:stylesheet>\n";

    /**
     * Regression test for the original issue.
     *
     * The HTML serializer must produce output that can be parsed as XML
     * when the output is UTF-8 encoded. In particular, accented characters
     * must not be serialized as named HTML entities such as &eacute;.
     */
    @Test
    public void testHtmlOutputWithAccentedCharacters() throws Exception {
        byte[] output = transformToBytes(XML_WITH_ACCENTS, XSL_HTML_UTF8);

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();

        // This must not throw:
        // SAXParseException: The entity "eacute" was referenced,
        // but not declared.
        builder.parse(new ByteArrayInputStream(output));
    }

    /**
     * Verify that an accented character in an attribute value is written
     * literally instead of being converted to a named HTML entity.
     */
    @Test
    public void testHtmlAttributeWithAccentedCharacter() throws Exception {
        String input = "<root attr=\"caf\u00E9\"/>";
        String output = transformIdentity(input, "html");

        Assert.assertTrue(output.contains("\u00E9"),
                "Literal \u00E9 must appear in HTML output: " + output);

        Assert.assertFalse(output.contains("&eacute;"),
                "HTML entity &eacute; must not appear in HTML output: "
                        + output);
    }

    /**
     * Verify that common UTF-8 encodable accented characters are written
     * literally and are not converted to named HTML entities.
     */
    @DataProvider(name = "accentedCharacters")
    public Object[][] accentedCharacters() {
        return new Object[][] {
                {"\u00E9", "&eacute;"},  // é
                {"\u00F1", "&ntilde;"},  // ñ
                {"\u00E0", "&agrave;"},   // à
                {"\u00E2", "&acirc;"},    // â
                {"\u00FC", "&uuml;"},     // ü
        };
    }

    @Test(dataProvider = "accentedCharacters")
    public void testAccentedCharacterNotSerializedAsNamedEntity(
            String character, String entity) throws Exception {

        String input = "<root>" + character + "</root>";
        String output = transformIdentity(input, "html");

        Assert.assertTrue(output.contains(character),
                "Literal '" + character
                        + "' must appear in HTML output: " + output);

        Assert.assertFalse(output.contains(entity),
                "'" + entity
                        + "' must not appear in HTML output: " + output);
    }

    /**
     * Apply an identity transformation using the specified output method
     * and UTF-8 encoding.
     */
    private static String transformIdentity(String xml, String method)
            throws Exception {

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();

        transformer.setOutputProperty(OutputKeys.METHOD, method);
        transformer.setOutputProperty(
                OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(
                OutputKeys.OMIT_XML_DECLARATION, "yes");

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        transformer.transform(
                new StreamSource(new StringReader(xml)),
                new StreamResult(output));

        return output.toString(StandardCharsets.UTF_8);
    }

    /**
     * Apply the XSLT stylesheet and return the raw serialized bytes.
     */
    private static byte[] transformToBytes(
            String xmlString, String xslString) throws Exception {

        StreamSource xmlSource =
                new StreamSource(new StringReader(xmlString));
        StreamSource xslSource =
                new StreamSource(new StringReader(xslString));

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer(xslSource);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        transformer.transform(
                xmlSource,
                new StreamResult(output));

        return output.toByteArray();
    }
}
