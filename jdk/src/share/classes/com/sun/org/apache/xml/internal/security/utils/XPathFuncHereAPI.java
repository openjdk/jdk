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
package com.sun.org.apache.xml.internal.security.utils;



import javax.xml.transform.TransformerException;

import com.sun.org.apache.xml.internal.security.transforms.implementations.FuncHereContext;
import com.sun.org.apache.xml.internal.utils.PrefixResolver;
import com.sun.org.apache.xml.internal.utils.PrefixResolverDefault;
import com.sun.org.apache.xpath.internal.XPath;
import com.sun.org.apache.xpath.internal.objects.XObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.traversal.NodeIterator;




/**
 * This class does the same as {@link com.sun.org.apache.xpath.internal.XPathAPI} except that the XPath strings
 * are not supplied as Strings but as {@link Text}, {@link Attr}ibute or
 * {ProcessingInstruction} nodes which contain the XPath string. This enables
 * us to use the <CODE>here()</CODE> function.
 * <BR>
 * The methods in this class are convenience methods into the low-level XPath API.
 * These functions tend to be a little slow, since a number of objects must be
 * created for each evaluation.  A faster way is to precompile the
 * XPaths using the low-level API, and then just use the XPaths
 * over and over.
 *
 * @author $Author: mullan $
 * @see <a href="http://www.w3.org/TR/xpath">XPath Specification</a>
 */
public class XPathFuncHereAPI {

   /**
    * Use an XPath string to select a single node. XPath namespace
    * prefixes are resolved from the context node, which may not
    * be what you want (see the next method).
    *
    * @param contextNode The node to start searching from.
    * @param xpathnode A Node containing a valid XPath string.
    * @return The first node found that matches the XPath, or null.
    *
    * @throws TransformerException
    */
   public static Node selectSingleNode(Node contextNode, Node xpathnode)
           throws TransformerException {
      return selectSingleNode(contextNode, xpathnode, contextNode);
   }

   /**
    * Use an XPath string to select a single node.
    * XPath namespace prefixes are resolved from the namespaceNode.
    *
    * @param contextNode The node to start searching from.
    * @param xpathnode
    * @param namespaceNode The node from which prefixes in the XPath will be resolved to namespaces.
    * @return The first node found that matches the XPath, or null.
    *
    * @throws TransformerException
    */
   public static Node selectSingleNode(
           Node contextNode, Node xpathnode, Node namespaceNode)
              throws TransformerException {

      // Have the XObject return its result as a NodeSetDTM.
      NodeIterator nl = selectNodeIterator(contextNode, xpathnode,
                                           namespaceNode);

      // Return the first node, or null
      return nl.nextNode();
   }

   /**
    *  Use an XPath string to select a nodelist.
    *  XPath namespace prefixes are resolved from the contextNode.
    *
    *  @param contextNode The node to start searching from.
    * @param xpathnode
    *  @return A NodeIterator, should never be null.
    *
    * @throws TransformerException
    */
   public static NodeIterator selectNodeIterator(
           Node contextNode, Node xpathnode) throws TransformerException {
      return selectNodeIterator(contextNode, xpathnode, contextNode);
   }

   /**
    *  Use an XPath string to select a nodelist.
    *  XPath namespace prefixes are resolved from the namespaceNode.
    *
    *  @param contextNode The node to start searching from.
    * @param xpathnode
    *  @param namespaceNode The node from which prefixes in the XPath will be resolved to namespaces.
    *  @return A NodeIterator, should never be null.
    *
    * @throws TransformerException
    */
   public static NodeIterator selectNodeIterator(
           Node contextNode, Node xpathnode, Node namespaceNode)
              throws TransformerException {

      // Execute the XPath, and have it return the result
      XObject list = eval(contextNode, xpathnode, namespaceNode);

      // Have the XObject return its result as a NodeSetDTM.
      return list.nodeset();
   }

   /**
    *  Use an XPath string to select a nodelist.
    *  XPath namespace prefixes are resolved from the contextNode.
    *
    *  @param contextNode The node to start searching from.
    * @param xpathnode
    *  @return A NodeIterator, should never be null.
    *
    * @throws TransformerException
    */
   public static NodeList selectNodeList(Node contextNode, Node xpathnode)
           throws TransformerException {
      return selectNodeList(contextNode, xpathnode, contextNode);
   }

   /**
    *  Use an XPath string to select a nodelist.
    *  XPath namespace prefixes are resolved from the namespaceNode.
    *
    *  @param contextNode The node to start searching from.
    * @param xpathnode
    *  @param namespaceNode The node from which prefixes in the XPath will be resolved to namespaces.
    *  @return A NodeIterator, should never be null.
    *
    * @throws TransformerException
    */
   public static NodeList selectNodeList(
           Node contextNode, Node xpathnode, Node namespaceNode)
              throws TransformerException {

      // Execute the XPath, and have it return the result
      XObject list = eval(contextNode, xpathnode, namespaceNode);

      // Return a NodeList.
      return list.nodelist();
   }

   /**
    *  Evaluate XPath string to an XObject.  Using this method,
    *  XPath namespace prefixes will be resolved from the namespaceNode.
    *  @param contextNode The node to start searching from.
    * @param xpathnode
    *  @return An XObject, which can be used to obtain a string, number, nodelist, etc, should never be null.
    *  @see com.sun.org.apache.xpath.internal.objects.XObject
    *  @see com.sun.org.apache.xpath.internal.objects.XNull
    *  @see com.sun.org.apache.xpath.internal.objects.XBoolean
    *  @see com.sun.org.apache.xpath.internal.objects.XNumber
    *  @see com.sun.org.apache.xpath.internal.objects.XString
    *  @see com.sun.org.apache.xpath.internal.objects.XRTreeFrag
    *
    * @throws TransformerException
    */
   public static XObject eval(Node contextNode, Node xpathnode)
           throws TransformerException {
      return eval(contextNode, xpathnode, contextNode);
   }

   /**
    *  Evaluate XPath string to an XObject.
    *  XPath namespace prefixes are resolved from the namespaceNode.
    *  The implementation of this is a little slow, since it creates
    *  a number of objects each time it is called.  This could be optimized
    *  to keep the same objects around, but then thread-safety issues would arise.
    *
    *  @param contextNode The node to start searching from.
    * @param xpathnode
    *  @param namespaceNode The node from which prefixes in the XPath will be resolved to namespaces.
    *  @return An XObject, which can be used to obtain a string, number, nodelist, etc, should never be null.
    *  @see com.sun.org.apache.xpath.internal.objects.XObject
    *  @see com.sun.org.apache.xpath.internal.objects.XNull
    *  @see com.sun.org.apache.xpath.internal.objects.XBoolean
    *  @see com.sun.org.apache.xpath.internal.objects.XNumber
    *  @see com.sun.org.apache.xpath.internal.objects.XString
    *  @see com.sun.org.apache.xpath.internal.objects.XRTreeFrag
    *
    * @throws TransformerException
    */
   public static XObject eval(
           Node contextNode, Node xpathnode, Node namespaceNode)
              throws TransformerException {

      // Since we don't have a XML Parser involved here, install some default support
      // for things like namespaces, etc.
      // (Changed from: XPathContext xpathSupport = new XPathContext();
      //    because XPathContext is weak in a number of areas... perhaps
      //    XPathContext should be done away with.)
      FuncHereContext xpathSupport = new FuncHereContext(xpathnode);

      // Create an object to resolve namespace prefixes.
      // XPath namespaces are resolved from the input context node's document element
      // if it is a root node, or else the current context node (for lack of a better
      // resolution space, given the simplicity of this sample code).
      PrefixResolverDefault prefixResolver =
         new PrefixResolverDefault((namespaceNode.getNodeType()
                                    == Node.DOCUMENT_NODE)
                                   ? ((Document) namespaceNode)
                                      .getDocumentElement()
                                   : namespaceNode);
      String str = getStrFromNode(xpathnode);

      // Create the XPath object.
      XPath xpath = new XPath(str, null, prefixResolver, XPath.SELECT, null);

      // Execute the XPath, and have it return the result
      // return xpath.execute(xpathSupport, contextNode, prefixResolver);
      int ctxtNode = xpathSupport.getDTMHandleFromNode(contextNode);

      return xpath.execute(xpathSupport, ctxtNode, prefixResolver);
   }

   /**
    *   Evaluate XPath string to an XObject.
    *   XPath namespace prefixes are resolved from the namespaceNode.
    *   The implementation of this is a little slow, since it creates
    *   a number of objects each time it is called.  This could be optimized
    *   to keep the same objects around, but then thread-safety issues would arise.
    *
    *   @param contextNode The node to start searching from.
    * @param xpathnode
    *   @param prefixResolver Will be called if the parser encounters namespace
    *                         prefixes, to resolve the prefixes to URLs.
    *   @return An XObject, which can be used to obtain a string, number, nodelist, etc, should never be null.
    *   @see com.sun.org.apache.xpath.internal.objects.XObject
    *   @see com.sun.org.apache.xpath.internal.objects.XNull
    *   @see com.sun.org.apache.xpath.internal.objects.XBoolean
    *   @see com.sun.org.apache.xpath.internal.objects.XNumber
    *   @see com.sun.org.apache.xpath.internal.objects.XString
    *   @see com.sun.org.apache.xpath.internal.objects.XRTreeFrag
    *
    * @throws TransformerException
    */
   public static XObject eval(
           Node contextNode, Node xpathnode, PrefixResolver prefixResolver)
              throws TransformerException {

      String str = getStrFromNode(xpathnode);

      // Since we don't have a XML Parser involved here, install some default support
      // for things like namespaces, etc.
      // (Changed from: XPathContext xpathSupport = new XPathContext();
      //    because XPathContext is weak in a number of areas... perhaps
      //    XPathContext should be done away with.)
      // Create the XPath object.
      XPath xpath = new XPath(str, null, prefixResolver, XPath.SELECT, null);

      // Execute the XPath, and have it return the result
      FuncHereContext xpathSupport = new FuncHereContext(xpathnode);
      int ctxtNode = xpathSupport.getDTMHandleFromNode(contextNode);

      return xpath.execute(xpathSupport, ctxtNode, prefixResolver);
   }

   /**
    * Method getStrFromNode
    *
    * @param xpathnode
    * @return the string from the node
    */
   private static String getStrFromNode(Node xpathnode) {

      if (xpathnode.getNodeType() == Node.TEXT_NODE) {
         return ((Text) xpathnode).getData();
      } else if (xpathnode.getNodeType() == Node.ATTRIBUTE_NODE) {
         return ((Attr) xpathnode).getNodeValue();
      } else if (xpathnode.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
         return ((ProcessingInstruction) xpathnode).getNodeValue();
      }

      return "";
   }
}
