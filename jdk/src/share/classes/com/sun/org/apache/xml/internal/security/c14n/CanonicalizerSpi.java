/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.c14n;



import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;

import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


/**
 * Base class which all Caninicalization algorithms extend.
 *
 * $todo$ cange JavaDoc
 * @author Christian Geuer-Pollmann
 */
public abstract class CanonicalizerSpi {

   /**
    * Method canonicalize
    *
    *
    * @param inputBytes
    * @return the c14n bytes.
    *
    *
    * @throws CanonicalizationException
    * @throws java.io.IOException
    * @throws javax.xml.parsers.ParserConfigurationException
    * @throws org.xml.sax.SAXException
    *
    */
   public byte[] engineCanonicalize(byte[] inputBytes)
           throws javax.xml.parsers.ParserConfigurationException,
                  java.io.IOException, org.xml.sax.SAXException,
                  CanonicalizationException {

      java.io.ByteArrayInputStream bais = new ByteArrayInputStream(inputBytes);
      InputSource in = new InputSource(bais);
      DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
      dfactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);

      // needs to validate for ID attribute nomalization
      dfactory.setNamespaceAware(true);

      DocumentBuilder db = dfactory.newDocumentBuilder();

      /*
       * for some of the test vectors from the specification,
       * there has to be a validatin parser for ID attributes, default
       * attribute values, NMTOKENS, etc.
       * Unfortunaltely, the test vectors do use different DTDs or
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
       *
       */

      // ErrorHandler eh = new C14NErrorHandler();
      // db.setErrorHandler(eh);
      Document document = db.parse(in);
      byte result[] = this.engineCanonicalizeSubTree(document);
      return result;
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

      return this
         .engineCanonicalizeXPathNodeSet(XMLUtils
            .convertNodelistToSet(xpathNodeSet));
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

      return this
         .engineCanonicalizeXPathNodeSet(XMLUtils
            .convertNodelistToSet(xpathNodeSet), inclusiveNamespaces);
   }

   //J-
   /** Returns the URI of this engine.
    * @return the URI
    */
   public abstract String engineGetURI();

   /** Returns the URI if include comments
    * @return true if include.
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
   public abstract byte[] engineCanonicalizeXPathNodeSet(Set<Node> xpathNodeSet, String inclusiveNamespaces)
      throws CanonicalizationException;

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
    * Sets the writter where the cannocalization ends. ByteArrayOutputStream if
    * none is setted.
    * @param os
    */
   public abstract void setWriter(OutputStream os);

   /** Reset the writter after a c14n */
   protected boolean reset=false;
   //J+
}
