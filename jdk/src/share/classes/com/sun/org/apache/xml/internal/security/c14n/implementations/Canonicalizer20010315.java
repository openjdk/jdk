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
package com.sun.org.apache.xml.internal.security.c14n.implementations;



import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.helper.C14nHelper;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;


/**
 * Implements <A HREF="http://www.w3.org/TR/2001/REC-xml-c14n-20010315">Canonical
 * XML Version 1.0</A>, a W3C Recommendation from 15 March 2001.
 *
 * @author Christian Geuer-Pollmann <geuerp@apache.org>
 */
public abstract class Canonicalizer20010315 extends CanonicalizerBase {
        boolean firstCall=true;
        final SortedSet result= new TreeSet(COMPARE);
    static final String XMLNS_URI=Constants.NamespaceSpecNS;
    static final String XML_LANG_URI=Constants.XML_LANG_SPACE_SpecNS;
   /**
    * Constructor Canonicalizer20010315
    *
    * @param includeComments
    */
   public Canonicalizer20010315(boolean includeComments) {
      super(includeComments);
   }

   /**
    * Returns the Attr[]s to be outputted for the given element.
    * <br>
    * The code of this method is a copy of {@link #handleAttributes(Element,
    * NameSpaceSymbTable)},
    * whereas it takes into account that subtree-c14n is -- well -- subtree-based.
    * So if the element in question isRoot of c14n, it's parent is not in the
    * node set, as well as all other ancestors.
    *
    * @param E
    * @param ns
    * @return the Attr[]s to be outputted
    * @throws CanonicalizationException
    */
   Iterator handleAttributesSubtree(Element E,  NameSpaceSymbTable ns )
           throws CanonicalizationException {
          if (!E.hasAttributes() && !firstCall) {
         return null;
      }
      // result will contain the attrs which have to be outputted
      final SortedSet result = this.result;
      result.clear();
      NamedNodeMap attrs = E.getAttributes();
      int attrsLength = attrs.getLength();

      for (int i = 0; i < attrsLength; i++) {
         Attr N = (Attr) attrs.item(i);
         String NName=N.getLocalName();
         String NValue=N.getValue();
         String NUri =N.getNamespaceURI();

         if (!XMLNS_URI.equals(NUri)) {
                //It's not a namespace attr node. Add to the result and continue.
            result.add(N);
            continue;
         }

         if (XML.equals(NName)
                 && XML_LANG_URI.equals(NValue)) {
                //The default mapping for xml must not be output.
                continue;
         }

         Node n=ns.addMappingAndRender(NName,NValue,N);

          if (n!=null) {
                 //Render the ns definition
             result.add(n);
             if (C14nHelper.namespaceIsRelative(N)) {
                Object exArgs[] = { E.getTagName(), NName, N.getNodeValue() };
                throw new CanonicalizationException(
                   "c14n.Canonicalizer.RelativeNamespace", exArgs);
             }
          }
      }

      if (firstCall) {
        //It is the first node of the subtree
        //Obtain all the namespaces defined in the parents, and added to the output.
        ns.getUnrenderedNodes(result);
        //output the attributes in the xml namespace.
                addXmlAttributesSubtree(E, result);
        firstCall=false;
      }

      return result.iterator();
   }

   /**
    * Float the xml:* attributes of the parent nodes to the root node of c14n
    * @param E the root node.
    * @param result the xml:* attributes  to output.
    */
   private void addXmlAttributesSubtree(Element E, SortedSet result) {
         // E is in the node-set
         Node parent = E.getParentNode();
         Map loa = new HashMap();

         if ((parent != null) && (parent.getNodeType() == Node.ELEMENT_NODE)) {

            // parent element is not in node set
            for (Node ancestor = parent;
                    (ancestor != null)
                    && (ancestor.getNodeType() == Node.ELEMENT_NODE);
                    ancestor = ancestor.getParentNode()) {
               Element el=((Element) ancestor);
               if (!el.hasAttributes()) {
                    continue;
               }
               // for all ancestor elements
               NamedNodeMap ancestorAttrs = el.getAttributes();

               for (int i = 0; i < ancestorAttrs.getLength(); i++) {
                  // for all attributes in the ancestor element
                  Attr currentAncestorAttr = (Attr) ancestorAttrs.item(i);

                  if (XML_LANG_URI.equals(
                          currentAncestorAttr.getNamespaceURI())) {

                     // do we have an xml:* ?
                     if (!E.hasAttributeNS(
                             XML_LANG_URI,
                             currentAncestorAttr.getLocalName())) {

                        // the xml:* attr is not in E
                        if (!loa.containsKey(currentAncestorAttr.getName())) {
                           loa.put(currentAncestorAttr.getName(),
                                   currentAncestorAttr);
                        }
                     }
                  }
               }
            }
         }

         result.addAll( loa.values());

      }

   /**
    * Returns the Attr[]s to be outputted for the given element.
    * <br>
    * IMPORTANT: This method expects to work on a modified DOM tree, i.e. a DOM which has
    * been prepared using {@link com.sun.org.apache.xml.internal.security.utils.XMLUtils#circumventBug2650(
    * org.w3c.dom.Document)}.
    *
    * @param E
    * @param ns
    * @return the Attr[]s to be outputted
    * @throws CanonicalizationException
    */
   Iterator handleAttributes(Element E,  NameSpaceSymbTable ns ) throws CanonicalizationException {
    // result will contain the attrs which have to be outputted
    boolean isRealVisible=isVisible(E);
    NamedNodeMap attrs = null;
    int attrsLength = 0;
    if (E.hasAttributes()) {
        attrs=E.getAttributes();
       attrsLength= attrs.getLength();
    }


    SortedSet result = this.result;
    result.clear();


    for (int i = 0; i < attrsLength; i++) {
       Attr N = (Attr) attrs.item(i);
       String NName=N.getLocalName();
       String NValue=N.getValue();
       String NUri =N.getNamespaceURI();

       if (!XMLNS_URI.equals(NUri)) {
          //A non namespace definition node.
          if (isRealVisible){
                //The node is visible add the attribute to the list of output attributes.
                result.add(N);
          }
          //keep working
          continue;
       }


       if ("xml".equals(NName)
               && XML_LANG_URI.equals(NValue)) {
          /* except omit namespace node with local name xml, which defines
           * the xml prefix, if its string value is http://www.w3.org/XML/1998/namespace.
           */
          continue;
       }
       //add the prefix binding to the ns symb table.
       //ns.addInclusiveMapping(NName,NValue,N,isRealVisible);
            if  (isVisible(N))  {
                            //The xpath select this node output it if needed.
                        Node n=ns.addMappingAndRenderXNodeSet(NName,NValue,N,isRealVisible);
                                if (n!=null) {
                                        result.add(n);
                    if (C14nHelper.namespaceIsRelative(N)) {
                       Object exArgs[] = { E.getTagName(), NName, N.getNodeValue() };
                       throw new CanonicalizationException(
                          "c14n.Canonicalizer.RelativeNamespace", exArgs);
                    }
                                }
        }
    }
    if (isRealVisible) {
        //The element is visible, handle the xmlns definition
        Attr xmlns = E.getAttributeNodeNS(XMLNS_URI, XMLNS);
        Node n=null;
        if (xmlns == null) {
                //No xmlns def just get the already defined.
                n=ns.getMapping(XMLNS);
        } else if ( !isVisible(xmlns)) {
                //There is a definition but the xmlns is not selected by the xpath.
                //then xmlns=""
                n=ns.addMappingAndRenderXNodeSet(XMLNS,"",nullNode,true);
        }
        //output the xmlns def if needed.
        if (n!=null) {
                        result.add(n);
        }
        //Float all xml:* attributes of the unselected parent elements to this one.
        addXmlAttributes(E,result);
    }

    return result.iterator();
   }
   /**
    *  Float the xml:* attributes of the unselected parent nodes to the ciurrent node.
    * @param E
    * @param result
    */
   private void addXmlAttributes(Element E, SortedSet result) {
        /* The processing of an element node E MUST be modified slightly when an
       * XPath node-set is given as input and the element's parent is omitted
       * from the node-set. The method for processing the attribute axis of an
       * element E in the node-set is enhanced. All element nodes along E's
       * ancestor axis are examined for nearest occurrences of attributes in
       * the xml namespace, such as xml:lang and xml:space (whether or not they
       * are in the node-set). From this list of attributes, remove any that are
       * in E's attribute axis (whether or not they are in the node-set). Then,
       * lexicographically merge this attribute list with the nodes of E's
       * attribute axis that are in the node-set. The result of visiting the
       * attribute axis is computed by processing the attribute nodes in this
       * merged attribute list.
       */

         // E is in the node-set
         Node parent = E.getParentNode();
         Map loa = new HashMap();

         if ((parent != null) && (parent.getNodeType() == Node.ELEMENT_NODE)
                 &&!isVisible(parent)) {

            // parent element is not in node set
            for (Node ancestor = parent;
                    (ancestor != null)
                    && (ancestor.getNodeType() == Node.ELEMENT_NODE);
                    ancestor = ancestor.getParentNode()) {
                Element el=((Element) ancestor);
                if (!el.hasAttributes()) {
                        continue;
                }
               // for all ancestor elements
               NamedNodeMap ancestorAttrs =el.getAttributes();

               for (int i = 0; i < ancestorAttrs.getLength(); i++) {

                  // for all attributes in the ancestor element
                  Attr currentAncestorAttr = (Attr) ancestorAttrs.item(i);

                  if (XML_LANG_URI.equals(
                          currentAncestorAttr.getNamespaceURI())) {

                     // do we have an xml:* ?
                     if (!E.hasAttributeNS(
                             XML_LANG_URI,
                             currentAncestorAttr.getLocalName())) {

                        // the xml:* attr is not in E
                        if (!loa.containsKey(currentAncestorAttr.getName())) {
                           loa.put(currentAncestorAttr.getName(),
                                   currentAncestorAttr);
                        }
                     }
                  }
               }
            }
         }
         result.addAll(loa.values());

}

   /**
    * Always throws a CanonicalizationException because this is inclusive c14n.
    *
    * @param xpathNodeSet
    * @param inclusiveNamespaces
    * @return none it always fails
    * @throws CanonicalizationException always
    */
   public byte[] engineCanonicalizeXPathNodeSet(Set xpathNodeSet, String inclusiveNamespaces)
           throws CanonicalizationException {

      /** $todo$ well, should we throw UnsupportedOperationException ? */
      throw new CanonicalizationException(
         "c14n.Canonicalizer.UnsupportedOperation");
   }

   /**
    * Always throws a CanonicalizationException because this is inclusive c14n.
    *
    * @param rootNode
    * @param inclusiveNamespaces
    * @return none it always fails
    * @throws CanonicalizationException
    */
   public byte[] engineCanonicalizeSubTree(Node rootNode, String inclusiveNamespaces)
           throws CanonicalizationException {

      /** $todo$ well, should we throw UnsupportedOperationException ? */
      throw new CanonicalizationException(
         "c14n.Canonicalizer.UnsupportedOperation");
   }
}
