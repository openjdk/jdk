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

package transform;

import java.io.StringReader;
import java.util.Properties;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8219705
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng transform.OutputPropertiesTest
 * @summary Verifies the output properties are set correctly
 */
public class OutputPropertiesTest {
    @Test
    public void testOutputProperties() throws Exception {
        String xslData = "<?xml version='1.0'?>"
                + "<xsl:stylesheet"
                + " xmlns:xsl='http://www.w3.org/1999/XSL/Transform'"
                + " version='1.0'"
                + ">\n"
                + "   <xsl:output method='html'/>\n"
                + "   <xsl:template match='/'>\n"
                + "     Hello World! \n"
                + "   </xsl:template>\n"
                + "</xsl:stylesheet>";

        System.out.println(xslData);

        Templates templates = TransformerFactory.newInstance()
                    .newTemplates(new StreamSource(new StringReader(xslData)));

        Properties properties = templates.getOutputProperties();
        String[] prNames = new String[]{"method", "version", "indent", "media-type"};
        String[] prValues = new String[]{"html", "4.0", "yes", "text/html"};

        for (int i = 0; i < prNames.length; i++) {
            String value = properties.getProperty(prNames[i]);
            String msg = "The value of the property '" + prNames[i] + "' should be '"
                    + prValues[i] + "' when the method is '" + prValues[0] + "'. \n";
            Assert.assertEquals(value, prValues[i], msg);
            System.out.println(
                    prNames[i] + ": actual: " + value + ", expected: " + prValues[i]);
        }
    }
}
