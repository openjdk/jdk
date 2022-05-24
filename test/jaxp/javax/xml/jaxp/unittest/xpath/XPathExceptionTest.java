/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package xpath;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/*
 * @test
 * @bug 8284548
 * @run testng xpath.XPathExceptionTest
 * @summary This is a general test for Exception handling. Additional cases may
 * be added with a bug id in the test cases.
 */
public class XPathExceptionTest {

    /*
     * DataProvider: invalid XPath expressions
     * Illegal expressions and structures that may escape the validation check.
     */
    @DataProvider(name = "invalid")
    public Object[][] getInvalid() throws Exception {
        return new Object[][]{
            // @bug JDK-8284548: expressions ending with relational operators
            // throw StringIndexOutOfBoundsException instead of XPathExpressionException
            {"/a/b/c[@d >"},
            {"/a/b/c[@d <"},
            {"/a/b/c[@d >="},
            {">>"},
        };
    }

    /**
     * Verifies that the XPath processor throws XPathExpressionException upon
     * encountering illegal XPath expressions.
     * @param invalidExp an illegal XPath expression
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "invalid")
    public void testIllegalExp(String invalidExp) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader("<A/>")));
        Assert.assertThrows(XPathExpressionException.class, () -> evaluate(doc, invalidExp));
    }

    private void evaluate(Document doc, String s) throws XPathExpressionException {
        XPath xp = XPathFactory.newInstance().newXPath();
        XPathExpression xe = xp.compile(s);
        xe.evaluateExpression(doc, Node.class);
    }
}
