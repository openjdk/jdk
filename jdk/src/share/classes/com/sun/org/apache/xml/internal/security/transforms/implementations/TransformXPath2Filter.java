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
package com.sun.org.apache.xml.internal.security.transforms.implementations;



import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.signature.NodeFilter;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.transforms.TransformSpi;
import com.sun.org.apache.xml.internal.security.transforms.TransformationException;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.transforms.params.XPath2FilterContainer;
import com.sun.org.apache.xml.internal.security.utils.CachedXPathAPIHolder;
import com.sun.org.apache.xml.internal.security.utils.CachedXPathFuncHereAPI;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Implements the <I>XML Signature XPath Filter v2.0</I>
 *
 * @author $Author: mullan $
 * @see <A HREF="http://www.w3.org/TR/xmldsig-filter2/">XPath Filter v2.0 (TR)</A>
 * @see <a HREF="http://www.w3.org/Signature/Drafts/xmldsig-xfilter2/">XPath Filter v2.0 (editors copy)</a>
 */
public class TransformXPath2Filter extends TransformSpi {

   /** {@link java.util.logging} logging facility */
//    static java.util.logging.Logger log =
//        java.util.logging.Logger.getLogger(
//                            TransformXPath2Filter.class.getName());

   /** Field implementedTransformURI */
   public static final String implementedTransformURI =
      Transforms.TRANSFORM_XPATH2FILTER;
   //J-
   // contains the type of the filter

   // contains the node set

   /**
    * Method engineGetURI
    *
    * @inheritDoc
    */
   protected String engineGetURI() {
      return implementedTransformURI;
   }



   /**
    * Method enginePerformTransform
    * @inheritDoc
    * @param input
    *
    * @throws TransformationException
    */
   protected XMLSignatureInput enginePerformTransform(XMLSignatureInput input)
           throws TransformationException {
          CachedXPathAPIHolder.setDoc(this._transformObject.getElement().getOwnerDocument());
      try {
          List unionNodes=new ArrayList();
           List substractNodes=new ArrayList();
           List intersectNodes=new ArrayList();

         CachedXPathFuncHereAPI xPathFuncHereAPI =
            new CachedXPathFuncHereAPI(CachedXPathAPIHolder.getCachedXPathAPI());


         Element []xpathElements =XMLUtils.selectNodes(
                this._transformObject.getElement().getFirstChild(),
                   XPath2FilterContainer.XPathFilter2NS,
                   XPath2FilterContainer._TAG_XPATH2);
         int noOfSteps = xpathElements.length;


         if (noOfSteps == 0) {
            Object exArgs[] = { Transforms.TRANSFORM_XPATH2FILTER, "XPath" };

            throw new TransformationException("xml.WrongContent", exArgs);
         }

         Document inputDoc = null;
         if (input.getSubNode() != null) {
            inputDoc = XMLUtils.getOwnerDocument(input.getSubNode());
         } else {
            inputDoc = XMLUtils.getOwnerDocument(input.getNodeSet());
         }

         for (int i = 0; i < noOfSteps; i++) {
            Element xpathElement =XMLUtils.selectNode(
               this._transformObject.getElement().getFirstChild(),
                  XPath2FilterContainer.XPathFilter2NS,
                  XPath2FilterContainer._TAG_XPATH2,i);
            XPath2FilterContainer xpathContainer =
               XPath2FilterContainer.newInstance(xpathElement,
                                                   input.getSourceURI());


            NodeList subtreeRoots = xPathFuncHereAPI.selectNodeList(inputDoc,
                                       xpathContainer.getXPathFilterTextNode(),
                                       CachedXPathFuncHereAPI.getStrFromNode(xpathContainer.getXPathFilterTextNode()),
                                       xpathContainer.getElement());
            if (xpathContainer.isIntersect()) {
                intersectNodes.add(subtreeRoots);
             } else if (xpathContainer.isSubtract()) {
                 substractNodes.add(subtreeRoots);
             } else if (xpathContainer.isUnion()) {
                unionNodes.add(subtreeRoots);
             }
         }

         input.setNeedsToBeExpanded(true);

         input.addNodeFilter(new XPath2NodeFilter(unionNodes,substractNodes,intersectNodes));
         input.setNodeSet(true);
         return input;
      } catch (TransformerException ex) {
         throw new TransformationException("empty", ex);
      } catch (DOMException ex) {
         throw new TransformationException("empty", ex);
      } catch (CanonicalizationException ex) {
         throw new TransformationException("empty", ex);
      } catch (InvalidCanonicalizerException ex) {
         throw new TransformationException("empty", ex);
      } catch (XMLSecurityException ex) {
         throw new TransformationException("empty", ex);
      } catch (SAXException ex) {
         throw new TransformationException("empty", ex);
      } catch (IOException ex) {
         throw new TransformationException("empty", ex);
      } catch (ParserConfigurationException ex) {
         throw new TransformationException("empty", ex);
      }
   }
}

class XPath2NodeFilter implements NodeFilter {
        XPath2NodeFilter(List unionNodes, List substractNodes,
                        List intersectNodes) {
                this.unionNodes=unionNodes;
                this.substractNodes=substractNodes;
                this.intersectNodes=intersectNodes;
        }
        List unionNodes=new ArrayList();
        List substractNodes=new ArrayList();
        List intersectNodes=new ArrayList();


   /**
    * @see com.sun.org.apache.xml.internal.security.signature.NodeFilter#isNodeInclude(org.w3c.dom.Node)
    */
   public boolean isNodeInclude(Node currentNode) {
           boolean notIncluded=false;
           if (rooted(currentNode,substractNodes)) {
                   notIncluded=true;
           } else if (!rooted(currentNode,intersectNodes)) {
                   notIncluded=true;
           }
           if (notIncluded && rooted(currentNode,unionNodes)) {
                   notIncluded=false;
           }

      return !notIncluded;

   }

   /**
    * Method rooted
    * @param currentNode
    * @param nodeList
    *
    * @return if rooted bye the rootnodes
    */
   boolean rooted(Node currentNode, List nodeList ) {
           for (int j=0;j<nodeList.size();j++) {
                   NodeList rootNodes=(NodeList) nodeList.get(j);
      int length = rootNodes.getLength();

      for (int i = 0; i < length; i++) {
         Node rootNode = rootNodes.item(i);

         if (XMLUtils.isDescendantOrSelf(rootNode,currentNode)) {
            return true;
         }
      }

           }
           return false;
   }
}
