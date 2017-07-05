/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */

/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.sun.org.apache.xml.internal.security.c14n.implementations;

import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.helper.C14nHelper;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.transforms.params.InclusiveNamespaces;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
/**
 * Implements &quot; <A
 * HREF="http://www.w3.org/TR/2002/REC-xml-exc-c14n-20020718/">Exclusive XML
 * Canonicalization, Version 1.0 </A>&quot; <BR />
 * Credits: During restructuring of the Canonicalizer framework, Ren??
 * Kollmorgen from Software AG submitted an implementation of ExclC14n which
 * fitted into the old architecture and which based heavily on my old (and slow)
 * implementation of "Canonical XML". A big "thank you" to Ren?? for this.
 * <BR />
 * <i>THIS </i> implementation is a complete rewrite of the algorithm.
 *
 * @author Christian Geuer-Pollmann <geuerp@apache.org>
 * @see <a href="http://www.w3.org/TR/2002/REC-xml-exc-c14n-20020718/ Exclusive#">
 *          XML Canonicalization, Version 1.0</a>
 */
public abstract class Canonicalizer20010315Excl extends CanonicalizerBase {
    /**
      * This Set contains the names (Strings like "xmlns" or "xmlns:foo") of
      * the inclusive namespaces.
      */
    TreeSet _inclusiveNSSet = null;
    static final String XMLNS_URI=Constants.NamespaceSpecNS;
    final SortedSet result = new TreeSet(COMPARE);
        /**
         * Constructor Canonicalizer20010315Excl
         *
         * @param includeComments
         */
        public Canonicalizer20010315Excl(boolean includeComments) {
                super(includeComments);
        }

        /**
         * Method engineCanonicalizeSubTree
         * @inheritDoc
         * @param rootNode
         *
         * @throws CanonicalizationException
         */
        public byte[] engineCanonicalizeSubTree(Node rootNode)
                        throws CanonicalizationException {
                return this.engineCanonicalizeSubTree(rootNode, "",null);
        }
        /**
         * Method engineCanonicalizeSubTree
         *  @inheritDoc
         * @param rootNode
         * @param inclusiveNamespaces
         *
         * @throws CanonicalizationException
         */
        public byte[] engineCanonicalizeSubTree(Node rootNode,
                        String inclusiveNamespaces) throws CanonicalizationException {
                return this.engineCanonicalizeSubTree(rootNode, inclusiveNamespaces,null);
        }
        /**
         * Method engineCanonicalizeSubTree
         * @param rootNode
     * @param inclusiveNamespaces
     * @param excl A element to exclude from the c14n process.
         * @return the rootNode c14n.
         * @throws CanonicalizationException
         */
        public byte[] engineCanonicalizeSubTree(Node rootNode,
                        String inclusiveNamespaces,Node excl) throws CanonicalizationException {
                        this._inclusiveNSSet = (TreeSet)InclusiveNamespaces
                                        .prefixStr2Set(inclusiveNamespaces);
                        return super.engineCanonicalizeSubTree(rootNode,excl);
        }
        /**
         *
         * @param rootNode
         * @param inclusiveNamespaces
         * @return the rootNode c14n.
         * @throws CanonicalizationException
         */
        public byte[] engineCanonicalize(XMLSignatureInput rootNode,
                        String inclusiveNamespaces) throws CanonicalizationException {
                        this._inclusiveNSSet = (TreeSet)InclusiveNamespaces
                                        .prefixStr2Set(inclusiveNamespaces);
                        return super.engineCanonicalize(rootNode);
        }

        /**
         * Method handleAttributesSubtree
         * @inheritDoc
         * @param E
         * @throws CanonicalizationException
         */
        Iterator handleAttributesSubtree(Element E,NameSpaceSymbTable ns)
                        throws CanonicalizationException {
                // System.out.println("During the traversal, I encountered " +
                // XMLUtils.getXPath(E));
                // result will contain the attrs which have to be outputted
                SortedSet result = this.result;
            result.clear();
                NamedNodeMap attrs=null;

                int attrsLength = 0;
        if (E.hasAttributes()) {
            attrs = E.getAttributes();
                attrsLength = attrs.getLength();
        }
                //The prefix visibly utilized(in the attribute or in the name) in the element
                SortedSet visiblyUtilized =(SortedSet) _inclusiveNSSet.clone();

                for (int i = 0; i < attrsLength; i++) {
                        Attr N = (Attr) attrs.item(i);
                        String NName=N.getLocalName();
                        String NNodeValue=N.getNodeValue();

                        if (!XMLNS_URI.equals(N.getNamespaceURI())) {
                                //Not a namespace definition.
                                //The Element is output element, add his prefix(if used) to visibyUtilized
                                String prefix = N.getPrefix();
                                if ( (prefix != null) && (!prefix.equals(XML) && !prefix.equals(XMLNS)) ) {
                                                visiblyUtilized.add(prefix);
                                }
                                //Add to the result.
                                 result.add(N);
                                continue;
                        }

                        if (ns.addMapping(NName, NNodeValue,N)) {
                                //New definition check if it is relative.
                if (C14nHelper.namespaceIsRelative(NNodeValue)) {
                    Object exArgs[] = {E.getTagName(), NName,
                            N.getNodeValue()};
                    throw new CanonicalizationException(
                            "c14n.Canonicalizer.RelativeNamespace", exArgs);
                }
            }
                }

                if (E.getNamespaceURI() != null) {
                        String prefix = E.getPrefix();
                        if ((prefix == null) || (prefix.length() == 0)) {
                                visiblyUtilized.add(XMLNS);
                        } else {
                                visiblyUtilized.add(prefix);
                        }
                } else {
                        visiblyUtilized.add(XMLNS);
                }

                //This can be optimezed by I don't have time
                Iterator it=visiblyUtilized.iterator();
                while (it.hasNext()) {
                        String s=(String)it.next();
                        Attr key=ns.getMapping(s);
                        if (key==null) {
                                continue;
                        }
                        result.add(key);
                }

                return result.iterator();
        }

        /**
         * Method engineCanonicalizeXPathNodeSet
         * @inheritDoc
         * @param xpathNodeSet
         * @param inclusiveNamespaces
         * @throws CanonicalizationException
         */
        public byte[] engineCanonicalizeXPathNodeSet(Set xpathNodeSet,
                        String inclusiveNamespaces) throws CanonicalizationException {


                        this._inclusiveNSSet = (TreeSet)InclusiveNamespaces
                                        .prefixStr2Set(inclusiveNamespaces);
                        return super.engineCanonicalizeXPathNodeSet(xpathNodeSet);

        }

    /** @inheritDoc */
    public byte[] engineCanonicalizeXPathNodeSet(Set xpathNodeSet
            ) throws CanonicalizationException {
        return engineCanonicalizeXPathNodeSet(xpathNodeSet,"");
    }

        /**
     * @inheritDoc
         * @param E
         * @throws CanonicalizationException
         */
        final Iterator handleAttributes(Element E, NameSpaceSymbTable ns)
                        throws CanonicalizationException {
                // result will contain the attrs which have to be outputted
                SortedSet result = this.result;
            result.clear();
                NamedNodeMap attrs = null;
                int attrsLength = 0;
        if (E.hasAttributes()) {
            attrs = E.getAttributes();
                attrsLength = attrs.getLength();
        }
                //The prefix visibly utilized(in the attribute or in the name) in the element
                Set visiblyUtilized =null;
                //It's the output selected.
                boolean isOutputElement = isVisible(E);
                if (isOutputElement) {
                        visiblyUtilized =  (Set) this._inclusiveNSSet.clone();
                }

                for (int i = 0; i < attrsLength; i++) {
                        Attr N = (Attr) attrs.item(i);
                        String NName=N.getLocalName();
                        String NNodeValue=N.getNodeValue();
                        if ( !isVisible(N) )  {
                                //The node is not in the nodeset(if there is a nodeset)
                                continue;
                        }

                        if (!XMLNS_URI.equals(N.getNamespaceURI())) {
                                //Not a namespace definition.
                                if (isOutputElement) {
                                        //The Element is output element, add his prefix(if used) to visibyUtilized
                                        String prefix = N.getPrefix();
                                        if ((prefix != null) && (!prefix.equals(XML) && !prefix.equals(XMLNS)) ){
                                                        visiblyUtilized.add(prefix);
                                        }
                                        //Add to the result.
                                    result.add(N);
                                }
                                continue;
                        }


                        if (ns.addMapping(NName, NNodeValue,N)) {
                //New definiton check if it is relative
                if (C14nHelper.namespaceIsRelative(NNodeValue)) {
                    Object exArgs[] = {E.getTagName(), NName,
                            N.getNodeValue()};
                    throw new CanonicalizationException(
                            "c14n.Canonicalizer.RelativeNamespace", exArgs);
                }
            }
                }

                if (isOutputElement) {
           //The element is visible, handle the xmlns definition
           Attr xmlns = E.getAttributeNodeNS(XMLNS_URI, XMLNS);
           if ((xmlns!=null) &&  (!isVisible(xmlns))) {
              //There is a definition but the xmlns is not selected by the xpath.
              //then xmlns=""
              ns.addMapping(XMLNS,"",nullNode);
            }

                        if (E.getNamespaceURI() != null) {
                                String prefix = E.getPrefix();
                                if ((prefix == null) || (prefix.length() == 0)) {
                                        visiblyUtilized.add(XMLNS);
                                } else {
                                        visiblyUtilized.add( prefix);
                                }
                        } else {
                                visiblyUtilized.add(XMLNS);
                        }
                        //This can be optimezed by I don't have time
                        //visiblyUtilized.addAll(this._inclusiveNSSet);
                        Iterator it=visiblyUtilized.iterator();
                        while (it.hasNext()) {
                                String s=(String)it.next();
                                Attr key=ns.getMapping(s);
                                if (key==null) {
                                        continue;
                                }
                                result.add(key);
                        }
                } else /*if (_circunvented)*/ {
                        Iterator it=this._inclusiveNSSet.iterator();
                        while (it.hasNext()) {
                                String s=(String)it.next();
                                Attr key=ns.getMappingWithoutRendered(s);
                                if (key==null) {
                                        continue;
                                }
                                result.add(key);
                        }
                }

                return result.iterator();
        }
}
