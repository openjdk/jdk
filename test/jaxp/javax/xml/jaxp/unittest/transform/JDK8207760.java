/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.Random;
import javax.xml.transform.OutputKeys;
import org.testng.annotations.DataProvider;

/*
 * @test
 * @bug 8207760 8349699
 * @summary Verifies that a surrogate pair at the edge of a buffer is properly handled
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm transform.JDK8207760
 */
public class JDK8207760 {
    final String xsl8207760 =
        "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
        "    <xsl:output omit-xml-declaration=\"yes\" indent=\"no\" />\n" +
        "\n" +
        "    <xsl:template match=\"node()|@*\">\n" +
        "        <xsl:copy>\n" +
        "            <xsl:apply-templates select=\"node()|@*\" />\n" +
        "        </xsl:copy>\n" +
        "    </xsl:template>\n" +
        "</xsl:stylesheet>\n";

    final String xsl8207760_2 = "<xsl:stylesheet \n" +
        "  version=\"1.0\" \n" +
        "  xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
        "\n" +
        "  <xsl:output method=\"xml\" indent=\"no\" cdata-section-elements=\"source\"/>\n" +
        "\n" +
        "  <xsl:template match=\"source\">\n" +
                "        <xsl:copy>\n" +
                "            <xsl:apply-templates select=\"node()\" />\n" +
                "        </xsl:copy>\n" +
        "  </xsl:template>\n" +
        "\n" +
        "</xsl:stylesheet>";

    final String xsl8207760_3 = "<xsl:stylesheet \n" +
        "  version=\"1.0\" \n" +
        "  xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
        "\n" +
        "  <xsl:output method=\"xml\" indent=\"no\" cdata-section-elements=\"source\"/>\n" +
        "\n" +
        "  <xsl:template match=\"source\">\n" +
        "    <xsl:copy>\n" +
        "      <!-- Copy the attributes -->\n" +
        "      <xsl:apply-templates select=\"@*\"/>\n" +
        "      <!-- Convert the contained nodes (elements and text) into text -->\n" +
        "      <xsl:variable name=\"subElementsText\">\n" +
        "        <xsl:apply-templates select=\"node()\"/>\n" +
        "      </xsl:variable>\n" +
        "      <!-- Output the XML directive and the converted nodes -->\n" +
        "      <xsl:value-of select=\"$subElementsText\"/>\n" +
        "    </xsl:copy>\n" +
        "  </xsl:template>\n" +
        "\n" +
        "</xsl:stylesheet>";

    final String xsl8349699 = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output encoding="UTF-8" method="text" />
                  <xsl:template match="/"><xsl:apply-templates select="node()" /></xsl:template>
                </xsl:stylesheet>
                """;

    @DataProvider(name = "xsls")
    public Object[][] getDataBug8207760_cdata() {
        return new Object[][]{
            {xsl8207760_2},
            {xsl8207760_3},
        };
    }

    /*
     * Data for verifying the patch for JDK8349699
     * @see testBug8349699
     */
    @DataProvider(name = "surrogatePair")
    public Object[][] getDataFor8349699() {
        return new Object[][]{
            // a surrogate pair in an XML element placed anywhere in a string
            {getXML(1024, 1024, "<b>\uD835\uDF00</b>"), getString(1024, 1024, "\uD835\uDF00")},
            {getXML(1023, 1023, "<b>\uD835\uDF00</b>"), getString(1023, 1023, "\uD835\uDF00")},
            {getXML(1023,0, "<b>\uD835\uDF00</b>"), getString(1023,0, "\uD835\uDF00")},
            {getXML(1023,120, "<b>\uD835\uDF00</b>"), getString(1023,120, "\uD835\uDF00")},
            // this is the original test as demonstrated in the bug report
            {getXML(1017,1017, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uD835\uDF00</b>\uD835\uDF00"),
                    getString(1017,1017, "\uD835\uDF03\uD835\uDF00\uD835\uDF00\uD835\uDF00\uD835\uDF00")},
            {getXML(1017,0, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uD835\uDF00</b>\uD835\uDF00"),
                    getString(1017,0, "\uD835\uDF03\uD835\uDF00\uD835\uDF00\uD835\uDF00\uD835\uDF00")},
            {getXML(1017,120, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uD835\uDF00</b>\uD835\uDF00"),
                    getString(1017,120, "\uD835\uDF03\uD835\uDF00\uD835\uDF00\uD835\uDF00\uD835\uDF00")},
        };
    }

    /*
     * Data for verifying the patch for JDK8349699
     * @see testBug8349699N
     */
    @DataProvider(name = "invalidSurrogatePair")
    public Object[][] getDataFor8349699N() {
        return new Object[][]{
                // invalid pair: high/high
                {getXML(1024, 1024, "<b>\uD835\uD835</b>")},
                {getXML(1023, 1023, "<b>\uD835\uD835</b>")},
                {getXML(1023,0, "<b>\uD835\uD835</b>")},
                {getXML(1023,120, "<b>\uD835\uD835</b>")},
                // invalid pair: low/low
                {getXML(1024, 1024, "<b>\uDF00\uDF00</b>")},
                {getXML(1023, 1023, "<b>\uDF00\uDF00</b>")},
                {getXML(1023,0, "<b>\uDF00\uDF00</b>")},
                {getXML(1023,120, "<b>\uDF00\uDF00</b>")},
                // invalid pair in the original test string
                {getXML(1017,1017, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uD835\uD835</b>\uD835\uDF00")},
                {getXML(1017,0, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uD835\uD835</b>\uD835\uDF00")},
                {getXML(1017,120, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uD835\uD835</b>\uD835\uDF00")},
                {getXML(1017,1017, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uDF00\uDF00</b>\uD835\uDF00")},
                {getXML(1017,0, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uDF00\uDF00</b>\uD835\uDF00")},
                {getXML(1017,120, "\uD835\uDF03\uD835\uDF00\uD835\uDF00<b>\uDF00\uDF00</b>\uD835\uDF00")},
        };
    }

    /*
     * @bug 8349699
     * Verifies that a surrogate pair at the edge of a buffer is properly handled
     * when serializing into a Character section.
     */
    @Test(dataProvider = "surrogatePair")
    public final void testBug8349699(String xml, String expected) throws Exception {
        Transformer t = createTransformerFromInputstream(
                new ByteArrayInputStream(xsl8349699.getBytes(StandardCharsets.UTF_8)));
        StringWriter sw = new StringWriter();
        t.transform(new StreamSource(new StringReader(xml)), new StreamResult(sw));
        Assert.assertEquals(sw.toString(), expected);
    }

    /*
     * @bug 8349699
     * Verifies that invalid surrogate pairs are caught.
     */
    @Test(dataProvider = "invalidSurrogatePair")
    public final void testBug8349699N(String xml) throws Exception {
        Assert.assertThrows(TransformerException.class, () -> {
            Transformer t = createTransformerFromInputstream(
                    new ByteArrayInputStream(xsl8349699.getBytes(StandardCharsets.UTF_8)));
            StringWriter sw = new StringWriter();
            t.transform(new StreamSource(new StringReader(xml)), new StreamResult(sw));
        });
    }

    /**
     * Returns an XML with the input string inserted in a text of length 'len' at
     * the position 'pos'.
     * @param len the length of the text to be placed in the XML
     * @param pos the position at which the input string will be inserted into the text
     * @param input the input string
     * @return an XML
     */
    private String getXML(int len, int pos, String input) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" ?><a>");
        sb.append(getString(len, pos, input));
        sb.append("</a>");
        return sb.toString();
    }

    /**
     * Returns a text string with the input string inserted at the specified position.
     * @param len the length of the text to be returned
     * @param pos the position at which the input string will be inserted into the text
     * @param input the input string
     * @return a text string
     */
    private String getString(int len, int pos, String input) {
        StringBuilder sb = new StringBuilder();
        if (pos == 0) {
            sb.append(input).append("x".repeat(len));
        } else if (pos == len) {
            sb.append("x".repeat(len)).append(input);
        } else {
            sb.append("x".repeat(pos)).append(input).append("x".repeat(len - pos));
        }
        return sb.toString();
    }

    /*
     * @bug 8207760
     * Verifies that a surrogate pair at the edge of a buffer is properly handled
     * when serializing into a Character section.
     */
    @Test
    public final void testBug8207760() throws Exception {
        String[] xmls = prepareXML(false);
        Transformer t = createTransformerFromInputstream(
                new ByteArrayInputStream(xsl8207760.getBytes(StandardCharsets.UTF_8)));
        t.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        StringWriter sw = new StringWriter();
        t.transform(new StreamSource(new StringReader(xmls[0])), new StreamResult(sw));
        Assert.assertEquals(sw.toString().replaceAll(System.lineSeparator(), "\n"), xmls[1]);
    }

    /*
     * @bug 8207760
     * Verifies that a surrogate pair at the edge of a buffer is properly handled
     * when serializing into a CDATA section.
     */
    @Test(dataProvider = "xsls")
    public final void testBug8207760_cdata(String xsl) throws Exception {
        String[] xmls = prepareXML(true);
        Transformer t = createTransformerFromInputstream(
                new ByteArrayInputStream(xsl.getBytes(StandardCharsets.UTF_8)));
        t.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        StringWriter sw = new StringWriter();
        t.transform(new StreamSource(new StringReader(xmls[0])), new StreamResult(sw));
        Assert.assertEquals(sw.toString().replaceAll(System.lineSeparator(), "\n"), xmls[1]);
    }

    private String[] prepareXML(boolean cdata) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><source>";
        if (cdata) {
            xml += "<![CDATA[";
        }
        String tail = "abc 123 </source>";
        if (cdata) {
            tail = "abc 123 ]]></source>";
        }
        String temp = generateString(1023);
        xml = xml + temp + '\uD83C' + '\uDF42' + tail;
        //xml = xml + temp + tail;
        String expected = (!cdata) ? "<source>" + temp + "&#127810;" + tail
                : xml;

        return new String[]{xml, expected};
    }

    static final char[] CHARS = "abcdefghijklmnopqrstuvwxyz \n".toCharArray();
    StringBuilder sb = new StringBuilder(1024 << 4);
    Random random = new Random();

    private String generateString(int size) {
        sb.setLength(0);
        for (int i = 0; i < size; i++) {
            char c = CHARS[random.nextInt(CHARS.length)];
            sb.append(c);
        }

        return sb.toString();
    }

    private Transformer createTransformerFromInputstream(InputStream xslStream)
            throws TransformerException {
        return TransformerFactory.newInstance().newTransformer(new StreamSource(xslStream));
    }
}
