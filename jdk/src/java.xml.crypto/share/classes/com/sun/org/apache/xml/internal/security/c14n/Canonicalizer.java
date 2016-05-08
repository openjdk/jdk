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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.sun.org.apache.xml.internal.security.c14n.implementations.Canonicalizer11_OmitComments;
import com.sun.org.apache.xml.internal.security.c14n.implementations.Canonicalizer11_WithComments;
import com.sun.org.apache.xml.internal.security.c14n.implementations.Canonicalizer20010315ExclOmitComments;
import com.sun.org.apache.xml.internal.security.c14n.implementations.Canonicalizer20010315ExclWithComments;
import com.sun.org.apache.xml.internal.security.c14n.implementations.Canonicalizer20010315OmitComments;
import com.sun.org.apache.xml.internal.security.c14n.implementations.Canonicalizer20010315WithComments;
import com.sun.org.apache.xml.internal.security.c14n.implementations.CanonicalizerPhysical;
import com.sun.org.apache.xml.internal.security.exceptions.AlgorithmAlreadyRegisteredException;
import com.sun.org.apache.xml.internal.security.utils.JavaUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 *
 * @author Christian Geuer-Pollmann
 */
public class Canonicalizer {

    /** The output encoding of canonicalized data */
    public static final String ENCODING = "UTF8";

    /**
     * XPath Expression for selecting every node and continuous comments joined
     * in only one node
     */
    public static final String XPATH_C14N_WITH_COMMENTS_SINGLE_NODE =
        "(.//. | .//@* | .//namespace::*)";

    /**
     * The URL defined in XML-SEC Rec for inclusive c14n <b>without</b> comments.
     */
    public static final String ALGO_ID_C14N_OMIT_COMMENTS =
        "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";
    /**
     * The URL defined in XML-SEC Rec for inclusive c14n <b>with</b> comments.
     */
    public static final String ALGO_ID_C14N_WITH_COMMENTS =
        ALGO_ID_C14N_OMIT_COMMENTS + "#WithComments";
    /**
     * The URL defined in XML-SEC Rec for exclusive c14n <b>without</b> comments.
     */
    public static final String ALGO_ID_C14N_EXCL_OMIT_COMMENTS =
        "http://www.w3.org/2001/10/xml-exc-c14n#";
    /**
     * The URL defined in XML-SEC Rec for exclusive c14n <b>with</b> comments.
     */
    public static final String ALGO_ID_C14N_EXCL_WITH_COMMENTS =
        ALGO_ID_C14N_EXCL_OMIT_COMMENTS + "WithComments";
    /**
     * The URI for inclusive c14n 1.1 <b>without</b> comments.
     */
    public static final String ALGO_ID_C14N11_OMIT_COMMENTS =
        "http://www.w3.org/2006/12/xml-c14n11";
    /**
     * The URI for inclusive c14n 1.1 <b>with</b> comments.
     */
    public static final String ALGO_ID_C14N11_WITH_COMMENTS =
        ALGO_ID_C14N11_OMIT_COMMENTS + "#WithComments";
    /**
     * Non-standard algorithm to serialize the physical representation for XML Encryption
     */
    public static final String ALGO_ID_C14N_PHYSICAL =
        "http://santuario.apache.org/c14n/physical";

    private static Map<String, Class<? extends CanonicalizerSpi>> canonicalizerHash =
        new ConcurrentHashMap<String, Class<? extends CanonicalizerSpi>>();

    private final CanonicalizerSpi canonicalizerSpi;

    /**
     * Constructor Canonicalizer
     *
     * @param algorithmURI
     * @throws InvalidCanonicalizerException
     */
    private Canonicalizer(String algorithmURI) throws InvalidCanonicalizerException {
        try {
            Class<? extends CanonicalizerSpi> implementingClass =
                canonicalizerHash.get(algorithmURI);

            @SuppressWarnings("deprecation")
            CanonicalizerSpi tmp = implementingClass.newInstance();
            canonicalizerSpi = tmp;
            canonicalizerSpi.reset = true;
        } catch (Exception e) {
            Object exArgs[] = { algorithmURI };
            throw new InvalidCanonicalizerException(
                "signature.Canonicalizer.UnknownCanonicalizer", exArgs, e
            );
        }
    }

    /**
     * Method getInstance
     *
     * @param algorithmURI
     * @return a Canonicalizer instance ready for the job
     * @throws InvalidCanonicalizerException
     */
    public static final Canonicalizer getInstance(String algorithmURI)
        throws InvalidCanonicalizerException {
        return new Canonicalizer(algorithmURI);
    }

    /**
     * Method register
     *
     * @param algorithmURI
     * @param implementingClass
     * @throws AlgorithmAlreadyRegisteredException
     * @throws SecurityException if a security manager is installed and the
     *    caller does not have permission to register the canonicalizer
     */
    @SuppressWarnings("unchecked")
    public static void register(String algorithmURI, String implementingClass)
        throws AlgorithmAlreadyRegisteredException, ClassNotFoundException {
        JavaUtils.checkRegisterPermission();
        // check whether URI is already registered
        Class<? extends CanonicalizerSpi> registeredClass =
            canonicalizerHash.get(algorithmURI);

        if (registeredClass != null)  {
            Object exArgs[] = { algorithmURI, registeredClass };
            throw new AlgorithmAlreadyRegisteredException("algorithm.alreadyRegistered", exArgs);
        }

        canonicalizerHash.put(
            algorithmURI, (Class<? extends CanonicalizerSpi>)Class.forName(implementingClass)
        );
    }

    /**
     * Method register
     *
     * @param algorithmURI
     * @param implementingClass
     * @throws AlgorithmAlreadyRegisteredException
     * @throws SecurityException if a security manager is installed and the
     *    caller does not have permission to register the canonicalizer
     */
    public static void register(String algorithmURI, Class<? extends CanonicalizerSpi> implementingClass)
        throws AlgorithmAlreadyRegisteredException, ClassNotFoundException {
        JavaUtils.checkRegisterPermission();
        // check whether URI is already registered
        Class<? extends CanonicalizerSpi> registeredClass = canonicalizerHash.get(algorithmURI);

        if (registeredClass != null)  {
            Object exArgs[] = { algorithmURI, registeredClass };
            throw new AlgorithmAlreadyRegisteredException("algorithm.alreadyRegistered", exArgs);
        }

        canonicalizerHash.put(algorithmURI, implementingClass);
    }

    /**
     * This method registers the default algorithms.
     */
    public static void registerDefaultAlgorithms() {
        canonicalizerHash.put(
            Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS,
            Canonicalizer20010315OmitComments.class
        );
        canonicalizerHash.put(
            Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS,
            Canonicalizer20010315WithComments.class
        );
        canonicalizerHash.put(
            Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS,
            Canonicalizer20010315ExclOmitComments.class
        );
        canonicalizerHash.put(
            Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS,
            Canonicalizer20010315ExclWithComments.class
        );
        canonicalizerHash.put(
            Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS,
            Canonicalizer11_OmitComments.class
        );
        canonicalizerHash.put(
            Canonicalizer.ALGO_ID_C14N11_WITH_COMMENTS,
            Canonicalizer11_WithComments.class
        );
        canonicalizerHash.put(
            Canonicalizer.ALGO_ID_C14N_PHYSICAL,
            CanonicalizerPhysical.class
        );
    }

    /**
     * Method getURI
     *
     * @return the URI defined for this c14n instance.
     */
    public final String getURI() {
        return canonicalizerSpi.engineGetURI();
    }

    /**
     * Method getIncludeComments
     *
     * @return true if the c14n respect the comments.
     */
    public boolean getIncludeComments() {
        return canonicalizerSpi.engineGetIncludeComments();
    }

    /**
     * This method tries to canonicalize the given bytes. It's possible to even
     * canonicalize non-wellformed sequences if they are well-formed after being
     * wrapped with a <CODE>&gt;a&lt;...&gt;/a&lt;</CODE>.
     *
     * @param inputBytes
     * @return the result of the canonicalization.
     * @throws CanonicalizationException
     * @throws java.io.IOException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public byte[] canonicalize(byte[] inputBytes)
        throws javax.xml.parsers.ParserConfigurationException,
        java.io.IOException, org.xml.sax.SAXException, CanonicalizationException {
        InputStream bais = new ByteArrayInputStream(inputBytes);
        InputSource in = new InputSource(bais);
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);

        dfactory.setNamespaceAware(true);

        // needs to validate for ID attribute normalization
        dfactory.setValidating(true);

        DocumentBuilder db = dfactory.newDocumentBuilder();

        /*
         * for some of the test vectors from the specification,
         * there has to be a validating parser for ID attributes, default
         * attribute values, NMTOKENS, etc.
         * Unfortunately, the test vectors do use different DTDs or
         * even no DTD. So Xerces 1.3.1 fires many warnings about using
         * ErrorHandlers.
         *
         * Text from the spec:
         *
         * The input octet stream MUST contain a well-formed XML document,
         * but the input need not be validated. However, the attribute
         * value normalization and entity reference resolution MUST be
         * performed in accordance with the behaviors of a validating
         * XML processor. As well, nodes for default attributes (declared
         * in the ATTLIST with an AttValue but not specified) are created
         * in each element. Thus, the declarations in the document type
         * declaration are used to help create the canonical form, even
         * though the document type declaration is not retained in the
         * canonical form.
         */
        db.setErrorHandler(new com.sun.org.apache.xml.internal.security.utils.IgnoreAllErrorHandler());

        Document document = db.parse(in);
        return this.canonicalizeSubtree(document);
    }

    /**
     * Canonicalizes the subtree rooted by <CODE>node</CODE>.
     *
     * @param node The node to canonicalize
     * @return the result of the c14n.
     *
     * @throws CanonicalizationException
     */
    public byte[] canonicalizeSubtree(Node node) throws CanonicalizationException {
        return canonicalizerSpi.engineCanonicalizeSubTree(node);
    }

    /**
     * Canonicalizes the subtree rooted by <CODE>node</CODE>.
     *
     * @param node
     * @param inclusiveNamespaces
     * @return the result of the c14n.
     * @throws CanonicalizationException
     */
    public byte[] canonicalizeSubtree(Node node, String inclusiveNamespaces)
        throws CanonicalizationException {
        return canonicalizerSpi.engineCanonicalizeSubTree(node, inclusiveNamespaces);
    }

    /**
     * Canonicalizes an XPath node set. The <CODE>xpathNodeSet</CODE> is treated
     * as a list of XPath nodes, not as a list of subtrees.
     *
     * @param xpathNodeSet
     * @return the result of the c14n.
     * @throws CanonicalizationException
     */
    public byte[] canonicalizeXPathNodeSet(NodeList xpathNodeSet)
        throws CanonicalizationException {
        return canonicalizerSpi.engineCanonicalizeXPathNodeSet(xpathNodeSet);
    }

    /**
     * Canonicalizes an XPath node set. The <CODE>xpathNodeSet</CODE> is treated
     * as a list of XPath nodes, not as a list of subtrees.
     *
     * @param xpathNodeSet
     * @param inclusiveNamespaces
     * @return the result of the c14n.
     * @throws CanonicalizationException
     */
    public byte[] canonicalizeXPathNodeSet(
        NodeList xpathNodeSet, String inclusiveNamespaces
    ) throws CanonicalizationException {
        return
            canonicalizerSpi.engineCanonicalizeXPathNodeSet(xpathNodeSet, inclusiveNamespaces);
    }

    /**
     * Canonicalizes an XPath node set.
     *
     * @param xpathNodeSet
     * @return the result of the c14n.
     * @throws CanonicalizationException
     */
    public byte[] canonicalizeXPathNodeSet(Set<Node> xpathNodeSet)
        throws CanonicalizationException {
        return canonicalizerSpi.engineCanonicalizeXPathNodeSet(xpathNodeSet);
    }

    /**
     * Canonicalizes an XPath node set.
     *
     * @param xpathNodeSet
     * @param inclusiveNamespaces
     * @return the result of the c14n.
     * @throws CanonicalizationException
     */
    public byte[] canonicalizeXPathNodeSet(
        Set<Node> xpathNodeSet, String inclusiveNamespaces
    ) throws CanonicalizationException {
        return
            canonicalizerSpi.engineCanonicalizeXPathNodeSet(xpathNodeSet, inclusiveNamespaces);
    }

    /**
     * Sets the writer where the canonicalization ends.  ByteArrayOutputStream
     * if none is set.
     * @param os
     */
    public void setWriter(OutputStream os) {
        canonicalizerSpi.setWriter(os);
    }

    /**
     * Returns the name of the implementing {@link CanonicalizerSpi} class
     *
     * @return the name of the implementing {@link CanonicalizerSpi} class
     */
    public String getImplementingCanonicalizerClass() {
        return canonicalizerSpi.getClass().getName();
    }

    /**
     * Set the canonicalizer behaviour to not reset.
     */
    public void notReset() {
        canonicalizerSpi.reset = false;
    }

}
