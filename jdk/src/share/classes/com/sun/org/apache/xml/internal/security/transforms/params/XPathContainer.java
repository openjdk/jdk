/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.transforms.params;


import com.sun.org.apache.xml.internal.security.transforms.TransformParam;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * This Object serves both as namespace prefix resolver and as container for
 * the <CODE>ds:XPath</CODE> Element. It implements the {@link org.w3c.dom.Element} interface
 * and can be used directly in a DOM tree.
 *
 * @author Christian Geuer-Pollmann
 */
public class XPathContainer extends SignatureElementProxy implements TransformParam {

    /**
     * Constructor XPathContainer
     *
     * @param doc
     */
    public XPathContainer(Document doc) {
        super(doc);
    }

    /**
     * Sets the TEXT value of the <CODE>ds:XPath</CODE> Element.
     *
     * @param xpath
     */
    public void setXPath(String xpath) {
        if (this.constructionElement.getChildNodes() != null) {
            NodeList nl = this.constructionElement.getChildNodes();

            for (int i = 0; i < nl.getLength(); i++) {
                this.constructionElement.removeChild(nl.item(i));
            }
        }

        Text xpathText = this.doc.createTextNode(xpath);
        this.constructionElement.appendChild(xpathText);
    }

    /**
     * Returns the TEXT value of the <CODE>ds:XPath</CODE> Element.
     *
     * @return the TEXT value of the <CODE>ds:XPath</CODE> Element.
     */
    public String getXPath() {
        return this.getTextFromTextChild();
    }

    /** @inheritDoc */
    public String getBaseLocalName() {
        return Constants._TAG_XPATH;
    }
}
