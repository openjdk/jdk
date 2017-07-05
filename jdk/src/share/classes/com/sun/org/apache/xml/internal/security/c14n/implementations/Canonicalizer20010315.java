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



import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.helper.C14nHelper;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


/**
 * Implements <A HREF="http://www.w3.org/TR/2001/REC-xml-c14n-20010315">Canonical
 * XML Version 1.0</A>, a W3C Recommendation from 15 March 2001.
 *
 * @author Christian Geuer-Pollmann <geuerp@apache.org>
 * @version $Revision: 1.5 $
 */
public abstract class Canonicalizer20010315 extends CanonicalizerBase {
        boolean firstCall=true;
        final SortedSet result= new TreeSet(COMPARE);
    static final String XMLNS_URI=Constants.NamespaceSpecNS;
    static final String XML_LANG_URI=Constants.XML_LANG_SPACE_SpecNS;
    static class XmlAttrStack {
        int currentLevel=0;
        int lastlevel=0;
        XmlsStackElement cur;
        static class XmlsStackElement {
                int level;
                boolean rendered=false;
                List nodes=new ArrayList();
        };
        List levels=new ArrayList();
        void push(int level) {
                currentLevel=level;
                if (currentLevel==-1)
                        return;
                cur=null;
                while (lastlevel>=currentLevel) {
                        levels.remove(levels.size()-1);
                        if (levels.size()==0) {
                                lastlevel=0;
                                return;
                        }
                        lastlevel=((XmlsStackElement)levels.get(levels.size()-1)).level;
                }
        }
        void addXmlnsAttr(Attr n) {
                if (cur==null) {
                        cur=new XmlsStackElement();
                        cur.level=currentLevel;
                        levels.add(cur);
                        lastlevel=currentLevel;
                }
                cur.nodes.add(n);
        }
        void getXmlnsAttr(Collection col) {
                int size=levels.size()-1;
                if (cur==null) {
                        cur=new XmlsStackElement();
                        cur.level=currentLevel;
                        lastlevel=currentLevel;
                        levels.add(cur);
                }
                boolean parentRendered=false;
                XmlsStackElement e=null;
                if (size==-1) {
                        parentRendered=true;
                } else {
                        e=(XmlsStackElement)levels.get(size);
                        if (e.rendered && e.level+1==currentLevel)
                                parentRendered=true;

                }
                if (parentRendered) {
                                col.addAll(cur.nodes);
                                cur.rendered=true;
                                return;
                        }

                        Map loa = new HashMap();
                for (;size>=0;size--) {
                        e=(XmlsStackElement)levels.get(size);
                        Iterator it=e.nodes.iterator();
                        while (it.hasNext()) {
                                Attr n=(Attr)it.next();
                                if (!loa.containsKey(n.getName()))
                                        loa.put(n.getName(),n);
                        }
                        //if (e.rendered)
                                //break;

                };
                //cur.nodes.clear();
                //cur.nodes.addAll(loa.values());
                        cur.rendered=true;
                col.addAll(loa.values());
        }

    }
    XmlAttrStack xmlattrStack=new XmlAttrStack();
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
         String NUri =N.getNamespaceURI();

         if (XMLNS_URI!=NUri) {
                //It's not a namespace attr node. Add to the result and continue.
            result.add(N);
            continue;
         }

         String NName=N.getLocalName();
         String NValue=N.getValue();
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
        xmlattrStack.getXmlnsAttr(result);
                firstCall=false;
      }

      return result.iterator();
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
        xmlattrStack.push(ns.getLevel());
    boolean isRealVisible=isVisibleDO(E,ns.getLevel())==1;
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
       String NUri =N.getNamespaceURI();

       if (XMLNS_URI!=NUri) {
          //A non namespace definition node.
           if (XML_LANG_URI==NUri) {
                          xmlattrStack.addXmlnsAttr(N);
           } else if (isRealVisible){
                //The node is visible add the attribute to the list of output attributes.
                result.add(N);
          }
          //keep working
          continue;
       }

       String NName=N.getLocalName();
       String NValue=N.getValue();
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
                if (!isRealVisible && ns.removeMappingIfRender(NName)) {
                        continue;
                }
                        //The xpath select this node output it if needed.
                //Node n=ns.addMappingAndRenderXNodeSet(NName,NValue,N,isRealVisible);
                Node n=ns.addMappingAndRender(NName,NValue,N);
                        if (n!=null) {
                                        result.add(n);
                    if (C14nHelper.namespaceIsRelative(N)) {
                       Object exArgs[] = { E.getTagName(), NName, N.getNodeValue() };
                       throw new CanonicalizationException(
                          "c14n.Canonicalizer.RelativeNamespace", exArgs);
                   }
                         }
        } else {
                if (isRealVisible && NName!=XMLNS) {
                        ns.removeMapping(NName);
                } else {
                        ns.addMapping(NName,NValue,N);
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
                n=ns.addMappingAndRender(XMLNS,"",nullNode);
        }
        //output the xmlns def if needed.
        if (n!=null) {
                        result.add(n);
        }
        //Float all xml:* attributes of the unselected parent elements to this one.
        //addXmlAttributes(E,result);
        xmlattrStack.getXmlnsAttr(result);
        ns.getUnrenderedNodes(result);

    }

    return result.iterator();
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
   void circumventBugIfNeeded(XMLSignatureInput input) throws CanonicalizationException, ParserConfigurationException, IOException, SAXException {
           if (!input.isNeedsToBeExpanded())
                   return;
           Document doc = null;
       if (input.getSubNode() != null) {
           doc=XMLUtils.getOwnerDocument(input.getSubNode());
       } else {
           doc=XMLUtils.getOwnerDocument(input.getNodeSet());
       }
           XMLUtils.circumventBug2650(doc);

   }

   void handleParent(Element e, NameSpaceSymbTable ns) {
           if (!e.hasAttributes()) {
                        return;
           }
           xmlattrStack.push(-1);
           NamedNodeMap attrs = e.getAttributes();
           int attrsLength = attrs.getLength();
           for (int i = 0; i < attrsLength; i++) {
                   Attr N = (Attr) attrs.item(i);
                   if (Constants.NamespaceSpecNS!=N.getNamespaceURI()) {
                           //Not a namespace definition, ignore.
                           if (XML_LANG_URI==N.getNamespaceURI()) {
                                   xmlattrStack.addXmlnsAttr(N);
                           }
                           continue;
                   }

                   String NName=N.getLocalName();
                   String NValue=N.getNodeValue();
                   if (XML.equals(NName)
                                   && Constants.XML_LANG_SPACE_SpecNS.equals(NValue)) {
                                continue;
                   }
                   ns.addMapping(NName,NValue,N);
           }
   }
}
