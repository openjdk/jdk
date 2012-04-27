/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2002-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: XPathExpressionImpl.java,v 1.2.4.1 2005/09/10 04:06:55 jeffsuttor Exp $
 */


package com.sun.org.apache.xpath.internal.domapi;

import javax.xml.transform.TransformerException;

import com.sun.org.apache.xpath.internal.XPath;
import com.sun.org.apache.xpath.internal.XPathContext;
import com.sun.org.apache.xpath.internal.objects.XObject;
import com.sun.org.apache.xpath.internal.res.XPATHErrorResources;
import com.sun.org.apache.xpath.internal.res.XPATHMessages;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.xpath.XPathException;
import org.w3c.dom.xpath.XPathExpression;
import org.w3c.dom.xpath.XPathNamespace;

/**
 *
 * The class provides an implementation of XPathExpression according
 * to the DOM L3 XPath Specification, Working Group Note 26 February 2004.
 *
 * <p>See also the <a href='http://www.w3.org/TR/2004/NOTE-DOM-Level-3-XPath-20040226'>Document Object Model (DOM) Level 3 XPath Specification</a>.</p>
 *
 * <p>The <code>XPathExpression</code> interface represents a parsed and resolved
 * XPath expression.</p>
 *
 * @see org.w3c.dom.xpath.XPathExpression
 *
 * @xsl.usage internal
 */
class XPathExpressionImpl implements XPathExpression {

  /**
   * The xpath object that this expression wraps
   */
  final private XPath m_xpath;

  /**
   * The document to be searched to parallel the case where the XPathEvaluator
   * is obtained by casting a Document.
   */
  final private Document m_doc;

    /**
     * Constructor for XPathExpressionImpl.
     *
     * @param xpath The wrapped XPath object.
     * @param doc The document to be searched, to parallel the case where''
     *            the XPathEvaluator is obtained by casting the document.
     */
    XPathExpressionImpl(XPath xpath, Document doc) {
        m_xpath = xpath;
        m_doc = doc;
    }

    /**
     *
     * This method provides an implementation XPathResult.evaluate according
     * to the DOM L3 XPath Specification, Working Group Note 26 February 2004.
     *
     * <p>See also the <a href='http://www.w3.org/TR/2004/NOTE-DOM-Level-3-XPath-20040226'>Document Object Model (DOM) Level 3 XPath Specification</a>.</p>
     *
     * <p>Evaluates this XPath expression and returns a result.</p>
     * @param contextNode The <code>context</code> is context node for the
     *   evaluation of this XPath expression.If the XPathEvaluator was
     *   obtained by casting the <code>Document</code> then this must be
     *   owned by the same document and must be a <code>Document</code>,
     *   <code>Element</code>, <code>Attribute</code>, <code>Text</code>,
     *   <code>CDATASection</code>, <code>Comment</code>,
     *   <code>ProcessingInstruction</code>, or <code>XPathNamespace</code>
     *   node.If the context node is a <code>Text</code> or a
     *   <code>CDATASection</code>, then the context is interpreted as the
     *   whole logical text node as seen by XPath, unless the node is empty
     *   in which case it may not serve as the XPath context.
     * @param type If a specific <code>type</code> is specified, then the
     *   result will be coerced to return the specified type relying on
     *   XPath conversions and fail if the desired coercion is not possible.
     *   This must be one of the type codes of <code>XPathResult</code>.
    *  @param result The <code>result</code> specifies a specific result
     *   object which may be reused and returned by this method. If this is
     *   specified as <code>null</code>or the implementation does not reuse
     *   the specified result, a new result object will be constructed and
     *   returned.For XPath 1.0 results, this object will be of type
     *   <code>XPathResult</code>.
     * @return The result of the evaluation of the XPath expression.For XPath
     *   1.0 results, this object will be of type <code>XPathResult</code>.
     * @exception XPathException
     *   TYPE_ERR: Raised if the result cannot be converted to return the
     *   specified type.
     * @exception DOMException
     *   WRONG_DOCUMENT_ERR: The Node is from a document that is not supported
     *   by the XPathEvaluator that created this
     *   <code>XPathExpression</code>.
     *   <br>NOT_SUPPORTED_ERR: The Node is not a type permitted as an XPath
     *   context node.
     *
     * @see org.w3c.dom.xpath.XPathExpression#evaluate(Node, short, XPathResult)
     * @xsl.usage internal
     */
    public Object evaluate(
        Node contextNode,
        short type,
        Object result)
        throws XPathException, DOMException {

        // If the XPathEvaluator was determined by "casting" the document
        if (m_doc != null) {

            // Check that the context node is owned by the same document
            if ((contextNode != m_doc) && (!contextNode.getOwnerDocument().equals(m_doc))) {
                String fmsg = XPATHMessages.createXPATHMessage(XPATHErrorResources.ER_WRONG_DOCUMENT, null);
                throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, fmsg);
            }

            // Check that the context node is an acceptable node type
            short nodeType = contextNode.getNodeType();
            if ((nodeType != Document.DOCUMENT_NODE) &&
                (nodeType != Document.ELEMENT_NODE) &&
                (nodeType != Document.ATTRIBUTE_NODE) &&
                (nodeType != Document.TEXT_NODE) &&
                (nodeType != Document.CDATA_SECTION_NODE) &&
                (nodeType != Document.COMMENT_NODE) &&
                (nodeType != Document.PROCESSING_INSTRUCTION_NODE) &&
                (nodeType != XPathNamespace.XPATH_NAMESPACE_NODE)) {
                    String fmsg = XPATHMessages.createXPATHMessage(XPATHErrorResources.ER_WRONG_NODETYPE, null);
                    throw new DOMException(DOMException.NOT_SUPPORTED_ERR, fmsg);
            }
        }

        //
        // If the type is not a supported type, throw an exception and be
        // done with it!
        if (!XPathResultImpl.isValidType(type)) {
            String fmsg = XPATHMessages.createXPATHMessage(XPATHErrorResources.ER_INVALID_XPATH_TYPE, new Object[] {new Integer(type)});
            throw new XPathException(XPathException.TYPE_ERR,fmsg); // Invalid XPath type argument: {0}
        }

        // Cache xpath context?
        XPathContext xpathSupport = new XPathContext();

        // if m_document is not null, build the DTM from the document
        if (null != m_doc) {
            xpathSupport.getDTMHandleFromNode(m_doc);
        }

        XObject xobj = null;
        try {
            xobj = m_xpath.execute(xpathSupport, contextNode, null);
        } catch (TransformerException te) {
            // What should we do here?
            throw new XPathException(XPathException.INVALID_EXPRESSION_ERR,te.getMessageAndLocation());
        }

        // Create a new XPathResult object
        // Reuse result object passed in?
        // The constructor will check the compatibility of type and xobj and
        // throw an exception if they are not compatible.
        return new XPathResultImpl(type,xobj,contextNode, m_xpath);
    }

}
