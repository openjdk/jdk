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
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import static javax.xml.xpath.XPathConstants.BOOLEAN;
import static javax.xml.xpath.XPathConstants.NODE;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.NUMBER;
import static javax.xml.xpath.XPathConstants.STRING;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/*
 * @test
 * @bug 8284400 8284548
 * @run testng xpath.XPathExceptionTest
 * @summary This is a general test for Exception handling in XPath. This test
 * covers the followings:
 * NPE: refer to DataProvider NPE for more details.
 * IAE: covered by existing tests: Bug4991939, XPathAnyTypeTest, XPathExpAnyTypeTest
 *      and XPathTest
 * XPathExpressionException: all other cass other than NPE and IAE.
 */
public class XPathExceptionTest {
    private final String XPATH_EXPRESSION = "ext:helloWorld()";

    /*
     * DataProvider: used for NPE test, provides the following fields:
     *     expression, context, useSource, source, QName, class type, provided
     * Refer to testNPEWithEvaluate.
     */
    @DataProvider(name = "NPE")
    public Object[][] getNullParameter() throws Exception {
        return new Object[][]{
            /**
             * Existing NPE tests:
             *     for XPath::evaluate:
             *     Bug4992788: expression != null, source = null
             *     Bug4992793: expression = null, source != null
             *     Bug4992805: source != null, QName = null
             *
             *     for XPath::evaluateExpression
             *     XPathAnyTypeTest: expression = null or classType = null
            */
            // NPE if expression = null
            {null, (Node)null, false, null, STRING, String.class, true},
            {null, getDummyDoc(), false, null, STRING, String.class, true},
            {null, (Node)null, false, null, null, null, false},
            {null, getDummyDoc(), false, null, null, null, false},
            // NPE if returnType = null
            {"exp", (Node)null, false, null, null, null, true},
            {"exp", getDummyDoc(), false, null, null, null, true},
            // NPE if source = null
            {"exp", (Node)null, true, null, STRING, String.class, true},
            {"exp", getDummyDoc(), true, null, STRING, String.class, true},
            {"exp", (Node)null, true, null, null, null, false},
            {"exp", getDummyDoc(), true, null, null, null, false},
        };
    }

    /*
     * DataProvider: used for compile-time error test, provides:
     *     invalid XPath expressions
     */
    @DataProvider(name = "invalidExp")
    public Object[][] getInvalidExp() throws Exception {
        return new Object[][]{
            {"8|b"},
            {"8[x=2]|b"},
            {"8/a|b"},
            {"a|7"},
            {"a|7|b"},
            {"a|7[x=2]"},
            {"b|'literal'"},
            {"b|\"literal\""},
            {"a|$x:y"},
            {"a|(x or y)"},
            {"a|(x and y)"},
            {"a|(x=2)"},
            {"a|string-length(\"xy\")"},
            {"/a/b/preceding-sibling::comment()|7"},
            // @bug JDK-8284548: expressions ending with relational operators
            // throw StringIndexOutOfBoundsException instead of XPathExpressionException
            {"/a/b/c[@d >"},
            {"/a/b/c[@d <"},
            {"/a/b/c[@d >="},
            {">>"},
        };
    }

    /*
     * DataProvider: expressions that cause exceptions in the given context.
     */
    @DataProvider(name = "expInContext1")
    public Object[][] getExpressionAndContext1() throws Exception {
        InputSource source = new InputSource(new StringReader("<A/>"));
        return new Object[][]{

            // expressions invalid for the null context, return type not provided
            {"x+1", (Node)null, false, null, null, null, false},
            {"5 mod a", (Node)null, false, null, null, null, false},
            {"8 div ", (Node)null, false, null, null, null, false},
            {"/bookstore/book[price>xx]", (Node)null, false, null, null, null, false},
            // expressions invalid for the null context, return type is irrelevant
            // for the eval, but needs to be a valid one when used
            // Note that invalid class type was tested in XPathAnyTypeTest,
            // and invalid QName tested in Bug4991939.
            {"x+1", (Node)null, false, null, STRING, String.class, true},
            {"5 mod a", (Node)null, false, null, STRING, String.class, true},
            {"8 div ", (Node)null, false, null, STRING, String.class, true},
            {"/bookstore/book[price>xx]", (Node)null, false, null, STRING, String.class, true},

            // undefined variable, context not relevant, return type not provided
            {"/  *     [n*$d2]/s", getDummyDoc(), false, null, null, null, false},
            {"/  *               [n|$d1]/s", getDummyDoc(), false, null, null, null, false},
            {"/  *     [n*$d2]/s", null, true, source, null, null, false},
            {"/  *               [n|$d1]/s", null, true, source, null, null, false},
            // undefined variable, context/return type not relevant for the eval
            // but need to be valid when provided
            {"/  *     [n*$d2]/s", getDummyDoc(), false, null, STRING, String.class, true},
            {"/  *               [n|$d1]/s", getDummyDoc(), false, null, STRING, String.class, true},
            {"/  *     [n*$d2]/s", null, true, source, STRING, String.class, true},
            {"/  *               [n|$d1]/s", null, true, source, STRING, String.class, true},
        };
    }

    /*
     * DataProvider: provides edge cases that are valid
     */
    @DataProvider(name = "expInContext2")
    public Object[][] getExpressionAndContext2() throws Exception {
        return new Object[][]{
            // The context can be empty
            {"/node[x=2]", getEmptyDocument(), false, null, STRING, String.class, true},
            {"/a/b/c", getEmptyDocument(), false, null, BOOLEAN, Boolean.class, true},
        };
    }

    /*
     * DataProvider: provides expressions that contain function calls.
     */
    @DataProvider(name = "functions")
    public Object[][] getExpressionWithFunctions() throws Exception {
        InputSource source = new InputSource(new StringReader("<A/>"));
        return new Object[][]{
            // expression with a function call
            {XPATH_EXPRESSION, getEmptyDocument(), false, null, STRING, String.class, true},
            {XPATH_EXPRESSION, null, true, source, BOOLEAN, Boolean.class, true},
        };
    }

    /**
     * Verifies that NPE is thrown if the expression, source, or returnType is
     * null.
     * This test tests these methods:
     *     XPath::evaluate and XPathExpression::evaluate.
     *     XPath::evaluateExpression and XPathExpression::evaluateExpression.
     *
     * @param expression the expression
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param cls the return type in the form of a class type, can be null
     * @param provided a flag indicating whether the return type is provided
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "NPE")
    public void testNPEWithEvaluate(String expression, Object item, boolean useSource,
            InputSource source, QName qn, Class<?> cls, boolean provided)
            throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        // test with XPath::evaluate
        Assert.assertThrows(NullPointerException.class, () -> xpathEvaluate(
                xPath, expression, item, useSource, source, qn, provided));

        // test with XPathExpression::evaluate
        Assert.assertThrows(NullPointerException.class, () -> xpathExpressionEvaluate(
                xPath, expression, item, useSource, source, qn, provided));

        // test with XPath::evaluateExpression
        Assert.assertThrows(NullPointerException.class, () -> xpathEvaluateExpression(
                xPath, expression, item, useSource, source, cls, provided));

        // test with XPathExpression::evaluateExpression
        Assert.assertThrows(NullPointerException.class, () -> xpathExpressionEvaluateExpression(
                xPath, expression, item, useSource, source, cls, provided));
    }

    /**
     * Verifies that XPathExpressionException is thrown upon encountering illegal
     * XPath expressions when XPath::compile is called.
     *
     * @param invalidExp an illegal XPath expression
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "invalidExp")
    public void testXPathCompile(String invalidExp) throws Exception {
        Assert.assertThrows(XPathExpressionException.class, () -> xpathCompile(invalidExp));
    }

    /**
     * Verifies that XPathExpressionException is thrown upon encountering illegal
     * XPath expressions.
     * This test tests these methods:
     *     XPath::evaluate and XPathExpression::evaluate.
     *     XPath::evaluateExpression and XPathExpression::evaluateExpression.

     *
     * @param expression an illegal XPath expression
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "invalidExp")
    public void testCompileErrorWithEvaluate(String expression)
            throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        // test with XPath::evaluate
        Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluate(
                xPath, expression, (Node)null, false, null, null, false));

        // test with XPathExpression::evaluate
        Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluate(
                xPath, expression, (Node)null, false, null, null, false));

        // test with XPath::evaluateExpression
        Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluateExpression(
                xPath, expression, (Node)null, false, null, null, false));

        // test with XPathExpression::evaluateExpression
        Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluateExpression(
                xPath, expression, (Node)null, false, null, null, false));
    }

    /**
     * Verifies that XPathExpressionException is thrown if the expression is
     * invalid with the given context.
     * This test tests these methods:
     *     XPath::evaluate and XPathExpression::evaluate.
     *     XPath::evaluateExpression and XPathExpression::evaluateExpression.
     *
     * @param expression the expression
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param cls the return type in the form of a class type, can be null
     * @param provided a flag indicating whether the return type is provided
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "expInContext1")
    public void testExpInContextEval1(String expression, Object item, boolean useSource,
            InputSource source, QName qn, Class<?> cls, boolean provided)
            throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        // test with XPath::evaluate
        Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluate(
                xPath, expression, item, useSource, source, qn, provided));

        // test with XPathExpression::evaluate
        Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluate(
                xPath, expression, item, useSource, source, qn, provided));

        // test with XPath::evaluateExpression
        Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluateExpression(
                xPath, expression, item, useSource, source, cls, provided));

        // test with XPathExpression::evaluateExpression
        Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluateExpression(
                xPath, expression, item, useSource, source, cls, provided));
    }

    /**
     * Verifies that XPathExpressionException is thrown if the expression is
     * invalid with the given context.
     * This test tests these methods:
     *     XPath::evaluate and XPathExpression::evaluate.
     *     XPath::evaluateExpression and XPathExpression::evaluateExpression.
     *
     * @param expression the expression
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param cls the return type in the form of a class type, can be null
     * @param provided a flag indicating whether the return type is provided
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "expInContext1")
    public void testExpInContext(String expression, Object item, boolean useSource,
            InputSource source, QName qn, Class<?> cls, boolean provided)
            throws Exception {
        QName[] qns = {NUMBER, STRING, BOOLEAN, NODESET, NODE};
        Class[] classes = {Integer.class, Boolean.class, String.class, XPathNodes.class, Node.class};
        XPath xPath = XPathFactory.newInstance().newXPath();

        for (QName qn1 : qns) {
            // test with XPath::evaluate
            Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluate(
                    xPath, expression, item, useSource, source, qn1, provided));

            // test with XPathExpression::evaluate
            Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluate(
                    xPath, expression, item, useSource, source, qn1, provided));
        }

        for (Class<?> cls1 : classes) {
            // test with XPath::evaluateExpression
            Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluateExpression(
                    xPath, expression, item, useSource, source, cls1, provided));

            // test with XPathExpression::evaluateExpression
            Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluateExpression(
                    xPath, expression, item, useSource, source, cls1, provided));
        }
    }

    /**
     * Verifies that the expression is valid with the given context.
     * This test tests these methods:
     *     XPath::evaluate and XPathExpression::evaluate.
     *     XPath::evaluateExpression and XPathExpression::evaluateExpression.
     *
     * @param expression the expression
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param cls the return type in the form of a class type, can be null
     * @param provided a flag indicating whether the return type is provided
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "expInContext2")
    public void testExpInContextEval2(String expression, Object item, boolean useSource,
            InputSource source, QName qn, Class<?> cls, boolean provided)
            throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        // test with XPath::evaluate
        xpathEvaluate(xPath, expression, item, useSource, source, qn, provided);

        // test with XPathExpression::evaluate
        xpathExpressionEvaluate(xPath, expression, item, useSource, source, qn, provided);

        // test with XPath::evaluateExpression
        xpathEvaluateExpression(xPath, expression, item, useSource, source, cls, provided);

        // test with XPathExpression::evaluateExpression
        xpathExpressionEvaluateExpression(xPath, expression, item, useSource, source, cls, provided);
    }

    /**
     * Verifies that the XPath processor without XPathFunctionResolver throws
     * XPathExpressionException upon processing an XPath expression that attempts
     * to call a function.
     *
     * @param expression the expression
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param cls the return type in the form of a class type, can be null
     * @param provided a flag indicating whether the return type is provided
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "functions")
    public void testFunction(String expression, Object item, boolean useSource,
            InputSource source, QName qn, Class<?> cls, boolean provided)
            throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        // test with XPath::evaluate
        Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluate(
                xPath, expression, item, useSource, source, qn, provided));

        // test with XPathExpression::evaluate
        Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluate(
                xPath, expression, item, useSource, source, qn, provided));

        // test with XPath::evaluateExpression
        Assert.assertThrows(XPathExpressionException.class, () -> xpathEvaluateExpression(
                xPath, expression, item, useSource, source, cls, provided));

        // test with XPathExpression::evaluateExpression
        Assert.assertThrows(XPathExpressionException.class, () -> xpathExpressionEvaluateExpression(
                xPath, expression, item, useSource, source, cls, provided));
    }

// ---- utility methods ----
    /**
     * Compiles the specified expression.
     * @param s the expression
     * @throws XPathExpressionException if the expression is invalid
     */
    private void xpathCompile(String s) throws XPathExpressionException {
        XPath xp = XPathFactory.newInstance().newXPath();
        XPathExpression xe = xp.compile(s);
    }

    /**
     * Runs evaluation using the XPath::evaluate methods.
     *
     * @param xPath the XPath object
     * @param expression the expression
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param qnProvided a flag indicating whether the QName is provided
     * @throws XPathExpressionException
     */
    private void xpathEvaluate(XPath xPath, String expression, Object item,
            boolean useSource, InputSource source, QName qn, boolean qnProvided)
            throws XPathExpressionException {
        if (useSource) {
            if (!qnProvided) {
                xPath.evaluate(expression, source);
            } else {
                xPath.evaluate(expression, source, qn);
            }
        } else {
            if (!qnProvided) {
                xPath.evaluate(expression, item);
            } else {
                xPath.evaluate(expression, item, qn);
            }
        }
    }

    /**
     * Runs evaluation using the XPathExpression::evaluate methods.
     *
     * @param xe the XPathExpression object
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param qnProvided a flag indicating whether the QName is provided
     * @throws XPathExpressionException
     */
    private void xpathExpressionEvaluate(XPath xPath, String expression, Object item,
            boolean useSource, InputSource source, QName qn, boolean qnProvided)
            throws XPathExpressionException {
        XPathExpression xe = xPath.compile(expression);
        if (useSource) {
            if (!qnProvided) {
                xe.evaluate(source);
            } else {
                xe.evaluate(source, qn);
            }
        } else {
            if (!qnProvided) {
                xe.evaluate(item);
            } else {
                xe.evaluate(item, qn);
            }
        }
    }

    /**
     * Runs evaluation using the XPath::evaluateExpression methods.
     *
     * @param xPath the XPath object
     * @param expression the expression
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param cls the class type, can be null
     * @param clsProvided a flag indicating whether the class type is provided
     * @throws XPathExpressionException
     */
    private void xpathEvaluateExpression(XPath xPath, String expression, Object item,
            boolean useSource, InputSource source, Class<?> cls, boolean clsProvided)
            throws XPathExpressionException {
        if (useSource) {
            if (!clsProvided) {
                xPath.evaluateExpression(expression, source);
            } else {
                xPath.evaluateExpression(expression, source, cls);
            }
        } else {
            if (!clsProvided) {
                xPath.evaluateExpression(expression, item);
            } else {
                xPath.evaluateExpression(expression, item, cls);
            }
        }
    }

    /**
     * Runs evaluation using the XPathExpression::evaluateExpression methods.
     *
     * @param xe the XPathExpression object
     * @param item the context item, can be null (for non-context dependent expressions)
     * @param useSource a flag indicating whether the source shall be used instead
     *                  of the context item
     * @param source the source
     * @param qn the return type in the form of a QName, can be null
     * @param qnProvided a flag indicating whether the QName is provided
     * @throws XPathExpressionException
     */
    private void xpathExpressionEvaluateExpression(XPath xPath, String expression,
            Object item, boolean useSource, InputSource source, Class<?> cls, boolean qnProvided)
            throws XPathExpressionException {
        XPathExpression xe = xPath.compile(expression);
        if (useSource) {
            if (!qnProvided) {
                xe.evaluateExpression(source);
            } else {
                xe.evaluateExpression(source, cls);
            }
        } else {
            if (!qnProvided) {
                xe.evaluateExpression(item);
            } else {
                xe.evaluateExpression(item, cls);
            }
        }
    }

    /**
     * Returns an empty {@link org.w3c.dom.Document}.
     * @return a DOM Document, null in case of Exception
     */
    private Document getEmptyDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            return null;
        }
    }

    /**
     * Returns a DOM Document with dummy content.
     * @return a DOM Document
     * @throws Exception
     */
    private Document getDummyDoc() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader("<A/>")));
    }

    /**
     * Returns a DOM Document with the specified source.
     * @param s the source
     * @return a DOM Document
     * @throws Exception
     */
    private Document getDoc(InputSource s) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(s);
    }
}
