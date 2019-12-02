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
package com.sun.org.apache.xml.internal.security.c14n;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Set;

import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Base class which all Canonicalization algorithms extend.
 *
 */
public abstract class CanonicalizerSpi {

    /** Reset the writer after a c14n */
    protected boolean reset = false;
    protected boolean secureValidation;

    /**
     * Method canonicalize
     *
     * @param inputBytes
     * @return the c14n bytes.
     *
     * @throws CanonicalizationException
     * @throws java.io.IOException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public byte[] engineCanonicalize(byte[] inputBytes)
        throws javax.xml.parsers.ParserConfigurationException, java.io.IOException,
        org.xml.sax.SAXException, CanonicalizationException {

        Document document = null;
        try (java.io.InputStream bais = new ByteArrayInputStream(inputBytes)) {
            InputSource in = new InputSource(bais);

            document = XMLUtils.read(in, secureValidation);
        }
        return this.engineCanonicalizeSubTree(document);
    }

    /**
     * Method engineCanonicalizeXPathNodeSet
     *
     * @param xpathNodeSet
     * @return the c14n bytes
     * @throws CanonicalizationException
     */
    public byte[] engineCanonicalizeXPathNodeSet(NodeList xpathNodeSet)
        throws CanonicalizationException {
        return this.engineCanonicalizeXPathNodeSet(
            XMLUtils.convertNodelistToSet(xpathNodeSet)
        );
    }

    /**
     * Method engineCanonicalizeXPathNodeSet
     *
     * @param xpathNodeSet
     * @param inclusiveNamespaces
     * @return the c14n bytes
     * @throws CanonicalizationException
     */
    public byte[] engineCanonicalizeXPathNodeSet(NodeList xpathNodeSet, String inclusiveNamespaces)
        throws CanonicalizationException {
        return this.engineCanonicalizeXPathNodeSet(
            XMLUtils.convertNodelistToSet(xpathNodeSet), inclusiveNamespaces
        );
    }

    /**
     * Returns the URI of this engine.
     * @return the URI
     */
    public abstract String engineGetURI();

    /**
     * Returns true if comments are included
     * @return true if comments are included
     */
    public abstract boolean engineGetIncludeComments();

    /**
     * C14n a nodeset
     *
     * @param xpathNodeSet
     * @return the c14n bytes
     * @throws CanonicalizationException
     */
    public abstract byte[] engineCanonicalizeXPathNodeSet(Set<Node> xpathNodeSet)
        throws CanonicalizationException;

    /**
     * C14n a nodeset
     *
     * @param xpathNodeSet
     * @param inclusiveNamespaces
     * @return the c14n bytes
     * @throws CanonicalizationException
     */
    public abstract byte[] engineCanonicalizeXPathNodeSet(
        Set<Node> xpathNodeSet, String inclusiveNamespaces
    ) throws CanonicalizationException;

    /**
     * C14n a node tree.
     *
     * @param rootNode
     * @return the c14n bytes
     * @throws CanonicalizationException
     */
    public abstract byte[] engineCanonicalizeSubTree(Node rootNode)
        throws CanonicalizationException;

    /**
     * C14n a node tree.
     *
     * @param rootNode
     * @param inclusiveNamespaces
     * @return the c14n bytes
     * @throws CanonicalizationException
     */
    public abstract byte[] engineCanonicalizeSubTree(Node rootNode, String inclusiveNamespaces)
        throws CanonicalizationException;

    /**
     * C14n a node tree.
     *
     * @param rootNode
     * @param inclusiveNamespaces
     * @param propagateDefaultNamespace If true the default namespace will be propagated to the c14n-ized root element
     * @return the c14n bytes
     * @throws CanonicalizationException
     */
    public abstract byte[] engineCanonicalizeSubTree(
            Node rootNode, String inclusiveNamespaces, boolean propagateDefaultNamespace)
            throws CanonicalizationException;

    /**
     * Sets the writer where the canonicalization ends. ByteArrayOutputStream if
     * none is set.
     * @param os
     */
    public abstract void setWriter(OutputStream os);

    public boolean isSecureValidation() {
        return secureValidation;
    }

    public void setSecureValidation(boolean secureValidation) {
        this.secureValidation = secureValidation;
    }

}
