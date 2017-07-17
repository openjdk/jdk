/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/**
 *
 * Provides an <em>object-model neutral</em> API for the
 * evaluation of XPath expressions and access to the evaluation
 * environment.
 *
 * <p>
 * The XPath API supports <a href="http://www.w3.org/TR/xpath">
 *     XML Path Language (XPath) Version 1.0</a>
 *
 * <hr>
 *
 * <ul>
 *     <li><a href='#XPath.Overview'>1. XPath Overview</a></li>
 *     <li><a href='#XPath.Expressions'>2. XPath Expressions</a></li>
 *     <li><a href='#XPath.Datatypes'>3. XPath Data Types</a>
 *         <ul>
 *             <li><a href='#XPath.Datatypes.QName'>3.1 QName Types</a>
 *             <li><a href='#XPath.Datatypes.Class'>3.2 Class Types</a>
 *             <li><a href='#XPath.Datatypes.Enum'>3.3 Enum Types</a>
 *         </ul>
 *     </li>
 *     <li><a href='#XPath.Context'>4. XPath Context</a></li>
 *     <li><a href='#XPath.Use'>5. Using the XPath API</a></li>
 * </ul>
 * <p>
 * <a id="XPath.Overview"></a>
 * <h3>1. XPath Overview</h3>
 *
 * <p>
 * The XPath language provides a simple, concise syntax for selecting
 * nodes from an XML document. XPath also provides rules for converting a
 * node in an XML document object model (DOM) tree to a boolean, double,
 * or string value. XPath is a W3C-defined language and an official W3C
 * recommendation; the W3C hosts the XML Path Language (XPath) Version
 * 1.0 specification.
 *
 *
 * <p>
 * XPath started in life in 1999 as a supplement to the XSLT and
 * XPointer languages, but has more recently become popular as a
 * stand-alone language, as a single XPath expression can be used to
 * replace many lines of DOM API code.
 *
 *
 * <a id="XPath.Expressions"></a>
 * <h3>2. XPath Expressions</h3>
 *
 * <p>
 * An XPath <em>expression</em> is composed of a <em>location
 * path</em> and one or more optional <em>predicates</em>. Expressions
 * may also include XPath variables.
 *
 *
 * <p>
 * The following is an example of a simple XPath expression:
 *
 * <blockquote>
 * <pre>
 *     /foo/bar
 * </pre>
 * </blockquote>
 *
 * <p>
 * This example would select the <code>&lt;bar&gt;</code> element in
 * an XML document such as the following:
 *
 * <blockquote>
 * <pre>
 *     &lt;foo&gt;
 *         &lt;bar/&gt;
 *     &lt;/foo&gt;
 * </pre>
 * </blockquote>
 *
 * <p>The expression <code>/foo/bar</code> is an example of a location
 * path. While XPath location paths resemble Unix-style file system
 * paths, an important distinction is that XPath expressions return
 * <em>all</em> nodes that match the expression. Thus, all three
 * <code>&lt;bar&gt;</code> elements in the following document would be
 * selected by the <code>/foo/bar</code> expression:
 *
 * <blockquote>
 * <pre>
 *     &lt;foo&gt;
 *         &lt;bar/&gt;
 *         &lt;bar/&gt;
 *         &lt;bar/&gt;
 *     &lt;/foo&gt;
 * </pre>
 * </blockquote>
 *
 * <p>
 * A special location path operator, <code>//</code>, selects nodes at
 * any depth in an XML document. The following example selects all
 * <code>&lt;bar&gt;</code> elements regardless of their location in a
 * document:
 *
 * <blockquote>
 * <pre>
 *     //bar
 * </pre>
 * </blockquote>
 *
 * <p>
 * A wildcard operator, *, causes all element nodes to be selected.
 * The following example selects all children elements of a
 * <code>&lt;foo&gt;</code> element:
 *
 * <blockquote>
 * <pre>
 *     /foo/*
 * </pre>
 * </blockquote>
 *
 * <p>
 * In addition to element nodes, XPath location paths may also address
 * attribute nodes, text nodes, comment nodes, and processing instruction
 * nodes. The following table gives examples of location paths for each
 * of these node types:
 *
 * <table class="striped">
 *     <caption>Examples of Location Path</caption>
 *     <thead>
 *         <tr>
 *             <th scope="col">Location Path</th>
 *             <th scope="col">Description</th>
 *         </tr>
 *     </thead>
 *     <tbody>
 *         <tr>
 *             <th scope="row">
 *                 <code>/foo/bar/<strong>@id</strong></code>
 *             </th>
 *             <td>
 *                 Selects the attribute <code>id</code> of the <code>&lt;bar&gt;</code> element
 *             </td>
 *         </tr>
 *         <tr>
 *             <th scope="row"><code>/foo/bar/<strong>text()</strong></code>
 *             </th>
 *             <td>
 *                 Selects the text nodes of the <code>&lt;bar&gt;</code> element. No
 *                 distinction is made between escaped and non-escaped character data.
 *             </td>
 *         </tr>
 *         <tr>
 *             <th scope="row"><code>/foo/bar/<strong>comment()</strong></code>
 *             </th>
 *             <td>
 *                 Selects all comment nodes contained in the <code>&lt;bar&gt;</code> element.
 *             </td>
 *         </tr>
 *         <tr>
 *             <th scope="row"><code>/foo/bar/<strong>processing-instruction()</strong></code>
 *             </th>
 *             <td>
 *                 Selects all processing-instruction nodes contained in the
 *                 <code>&lt;bar&gt;</code> element.
 *             </td>
 *         </tr>
 *     </tbody>
 * </table>
 *
 * <p>
 * Predicates allow for refining the nodes selected by an XPath
 * location path. Predicates are of the form
 * <code>[<em>expression</em>]</code>. The following example selects all
 * <code>&lt;foo&gt;</code> elements that contain an <code>include</code>
 * attribute with the value of <code>true</code>:
 *
 * <blockquote>
 * <pre>
 *     //foo[@include='true']
 * </pre>
 * </blockquote>
 *
 * <p>
 * Predicates may be appended to each other to further refine an
 * expression, such as:
 *
 * <blockquote>
 * <pre>
 *     //foo[@include='true'][@mode='bar']
 * </pre>
 * </blockquote>
 *
 * <a id="XPath.Datatypes"></a>
 * <h3>3. XPath Data Types</h3>
 *
 * <p>
 * While XPath expressions select nodes in the XML document, the XPath
 * API allows the selected nodes to be coalesced into one of the
 * following data types:
 *
 * <ul>
 *     <li><code>Boolean</code></li>
 *     <li><code>Number</code></li>
 *     <li><code>String</code></li>
 * </ul>
 *
 * <a id="XPath.Datatypes.QName"></a>
 * <h3>3.1 QName types</h3>
 * The XPath API defines the following {@link javax.xml.namespace.QName} types to
 * represent return types of an XPath evaluation:
 * <ul>
 *     <li>{@link javax.xml.xpath.XPathConstants#NODESET}</li>
 *     <li>{@link javax.xml.xpath.XPathConstants#NODE}</li>
 *     <li>{@link javax.xml.xpath.XPathConstants#STRING}</li>
 *     <li>{@link javax.xml.xpath.XPathConstants#BOOLEAN}</li>
 *     <li>{@link javax.xml.xpath.XPathConstants#NUMBER}</li>
 * </ul>
 *
 * <p>
 * The return type is specified by a {@link javax.xml.namespace.QName} parameter
 * in method call used to evaluate the expression, which is either a call to
 * <code>XPathExpression.evalute(...)</code> or <code>XPath.evaluate(...)</code>
 * methods.
 *
 * <p>
 * When a <code>Boolean</code> return type is requested,
 * <code>Boolean.TRUE</code> is returned if one or more nodes were
 * selected; otherwise, <code>Boolean.FALSE</code> is returned.
 *
 * <p>
 * The <code>String</code> return type is a convenience for retrieving
 * the character data from a text node, attribute node, comment node, or
 * processing-instruction node. When used on an element node, the value
 * of the child text nodes is returned.
 *
 * <p>
 * The <code>Number</code> return type attempts to coalesce the text
 * of a node to a <code>double</code> data type.
 *
 * <a id="XPath.Datatypes.Class"></a>
 * <h3>3.2 Class types</h3>
 * In addition to the QName types, the XPath API supports the use of Class types
 * through the <code>XPathExpression.evaluteExpression(...)</code> or
 * <code>XPath.evaluateExpression(...)</code> methods.
 *
 * The XPath data types are mapped to Class types as follows:
 * <ul>
 *     <li><code>Boolean</code> -- <code>Boolean.class</code></li>
 *     <li><code>Number</code> -- <code>Number.class</code></li>
 *     <li><code>String</code> -- <code>String.class</code></li>
 *     <li><code>Nodeset</code> -- <code>XPathNodes.class</code></li>
 *     <li><code>Node</code> -- <code>Node.class</code></li>
 * </ul>
 *
 * <p>
 * Of the subtypes of Number, only Double, Integer and Long are supported.
 *
 * <a id="XPath.Datatypes.Enum"></a>
 * <h3>3.3 Enum types</h3>
 * Enum types are defined in {@link javax.xml.xpath.XPathEvaluationResult.XPathResultType}
 * that provide mappings between the QName and Class types above. The result of
 * evaluating an expression using the <code>XPathExpression.evaluteExpression(...)</code>
 * or <code>XPath.evaluateExpression(...)</code> methods will be of one of these types.
 *
 * <a id="XPath.Context"></a>
 * <h3>4. XPath Context</h3>
 *
 * <p>
 * XPath location paths may be relative to a particular node in the
 * document, known as the <code>context</code>. A context consists of:
 * <ul>
 *     <li>a node (the context node)</li>
 *     <li>a pair of non-zero positive integers (the context position and the context size)</li>
 *     <li>a set of variable bindings</li>
 *     <li>a function library</li>
 *     <li>the set of namespace declarations in scope for the expression</li>
 * </ul>
 *
 * <p>
 * It is an XML document tree represented as a hierarchy of nodes, a
 * {@link org.w3c.dom.Node} for example, in the JDK implementation.
 *
 * <a id="XPath.Use"></a>
 * <h3>5. Using the XPath API</h3>
 *
 * Consider the following XML document:
 * <blockquote>
 * <pre>
 * &lt;widgets&gt;
 * &lt;widget&gt;
 * &lt;manufacturer/&gt;
 * &lt;dimensions/&gt;
 * &lt;/widget&gt;
 * &lt;/widgets&gt;
 * </pre>
 * </blockquote>
 *
 * <p>
 * The <code>&lt;widget&gt;</code> element can be selected with the following process:
 *
 * <blockquote>
 * <pre>
 *     // parse the XML as a W3C Document
 *     DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
 *     Document document = builder.parse(new File("/widgets.xml"));
 *
 *     //Get an XPath object and evaluate the expression
 *     XPath xpath = XPathFactory.newInstance().newXPath();
 *     String expression = "/widgets/widget";
 *     Node widgetNode = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
 *
 *     //or using the evaluateExpression method
 *     Node widgetNode = xpath.evaluateExpression(expression, document, Node.class);
 * </pre>
 * </blockquote>
 *
 * <p>
 * With a reference to the <code>&lt;widget&gt;</code> element, a
 * relative XPath expression can be written to select the
 * <code>&lt;manufacturer&gt;</code> child element:
 *
 * <blockquote>
 * <pre>
 *     XPath xpath = XPathFactory.newInstance().newXPath();
 *     String expression = <b>"manufacturer";</b>
 *     Node manufacturerNode = (Node) xpath.evaluate(expression, <b>widgetNode</b>, XPathConstants.NODE);
 *
 *     //or using the evaluateExpression method
 *     Node manufacturerNode = xpath.evaluateExpression(expression, <b>widgetNode</b>, Node.class);
 * </pre>
 * </blockquote>
 *
 * <p>
 * In the above example, the XML file is read into a DOM Document before being passed
 * to the XPath API. The following code demonstrates the use of InputSource to
 * leave it to the XPath implementation to process it:
 *
 * <blockquote>
 * <pre>
 *     XPath xpath = XPathFactory.newInstance().newXPath();
 *     String expression = "/widgets/widget";
 *     InputSource inputSource = new InputSource("widgets.xml");
 *     NodeList nodes = (NodeList) xpath.evaluate(expression, inputSource, XPathConstants.NODESET);
 *
 *     //or using the evaluateExpression method
 *     XPathNodes nodes = xpath.evaluate(expression, inputSource, XPathNodes.class);
 * </pre>
 * </blockquote>
 *
 * <p>
 * In the above cases, the type of the expected results are known. In case where
 * the result type is unknown or any type, the {@link javax.xml.xpath.XPathEvaluationResult}
 * may be used to determine the return type. The following code demonstrates the usage:
 * <blockquote>
 * <pre>
 *     XPathEvaluationResult&lt;?&gt; result = xpath.evaluateExpression(expression, document);
 *     switch (result.type()) {
 *         case NODESET:
 *             XPathNodes nodes = (XPathNodes)result.value();
 *             ...
 *             break;
 *     }
 * </pre>
 * </blockquote>
 *
 * <p>
 * The XPath 1.0 Number data type is defined as a double. However, the XPath
 * specification also provides functions that returns Integer type. To facilitate
 * such operations, the XPath API allows Integer and Long to be used in
 * {@code evaluateExpression} method such as the following code:
 * <blockquote>
 * <pre>
 *     int count = xpath.evaluate("count(/widgets/widget)", document, Integer.class);
 * </pre>
 * </blockquote>
 *
 * @since 1.5
 *
 */

package javax.xml.xpath;
