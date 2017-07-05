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
 */
public abstract class Canonicalizer20010315 extends CanonicalizerBase {
    private static final String XMLNS_URI = Constants.NamespaceSpecNS;
    private static final String XML_LANG_URI = Constants.XML_LANG_SPACE_SpecNS;

    private boolean firstCall = true;
    private final SortedSet<Attr> result = new TreeSet<Attr>(COMPARE);

    private static class XmlAttrStack {
        static class XmlsStackElement {
            int level;
            boolean rendered = false;
            List<Attr> nodes = new ArrayList<Attr>();
        };

        int currentLevel = 0;
        int lastlevel = 0;
        XmlsStackElement cur;
        List<XmlsStackElement> levels = new ArrayList<XmlsStackElement>();

        void push(int level) {
            currentLevel = level;
            if (currentLevel == -1) {
                return;
            }
            cur = null;
            while (lastlevel >= currentLevel) {
                levels.remove(levels.size() - 1);
                int newSize = levels.size();
                if (newSize == 0) {
                    lastlevel = 0;
                    return;
                }
                lastlevel = (levels.get(newSize - 1)).level;
            }
        }

        void addXmlnsAttr(Attr n) {
            if (cur == null) {
                cur = new XmlsStackElement();
                cur.level = currentLevel;
                levels.add(cur);
                lastlevel = currentLevel;
            }
            cur.nodes.add(n);
        }

        void getXmlnsAttr(Collection<Attr> col) {
            int size = levels.size() - 1;
            if (cur == null) {
                cur = new XmlsStackElement();
                cur.level = currentLevel;
                lastlevel = currentLevel;
                levels.add(cur);
            }
            boolean parentRendered = false;
            XmlsStackElement e = null;
            if (size == -1) {
                parentRendered = true;
            } else {
                e = levels.get(size);
                if (e.rendered && e.level + 1 == currentLevel) {
                    parentRendered = true;
                }
            }
            if (parentRendered) {
                col.addAll(cur.nodes);
                cur.rendered = true;
                return;
            }

            Map<String, Attr> loa = new HashMap<String, Attr>();
            for (; size >= 0; size--) {
                e = levels.get(size);
                Iterator<Attr> it = e.nodes.iterator();
                while (it.hasNext()) {
                    Attr n = it.next();
                    if (!loa.containsKey(n.getName())) {
                        loa.put(n.getName(), n);
                    }
                }
            }

            cur.rendered = true;
            col.addAll(loa.values());
        }

    }

    private XmlAttrStack xmlattrStack = new XmlAttrStack();

    /**
     * Constructor Canonicalizer20010315
     *
     * @param includeComments
     */
    public Canonicalizer20010315(boolean includeComments) {
        super(includeComments);
    }

    /**
     * Always throws a CanonicalizationException because this is inclusive c14n.
     *
     * @param xpathNodeSet
     * @param inclusiveNamespaces
     * @return none it always fails
     * @throws CanonicalizationException always
     */
    public byte[] engineCanonicalizeXPathNodeSet(Set<Node> xpathNodeSet, String inclusiveNamespaces)
        throws CanonicalizationException {

        /** $todo$ well, should we throw UnsupportedOperationException ? */
        throw new CanonicalizationException("c14n.Canonicalizer.UnsupportedOperation");
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
        throw new CanonicalizationException("c14n.Canonicalizer.UnsupportedOperation");
    }

    /**
     * Returns the Attr[]s to be output for the given element.
     * <br>
     * The code of this method is a copy of {@link #handleAttributes(Element,
     * NameSpaceSymbTable)},
     * whereas it takes into account that subtree-c14n is -- well -- subtree-based.
     * So if the element in question isRoot of c14n, it's parent is not in the
     * node set, as well as all other ancestors.
     *
     * @param element
     * @param ns
     * @return the Attr[]s to be output
     * @throws CanonicalizationException
     */
    @Override
    protected Iterator<Attr> handleAttributesSubtree(Element element, NameSpaceSymbTable ns)
        throws CanonicalizationException {
        if (!element.hasAttributes() && !firstCall) {
            return null;
        }
        // result will contain the attrs which have to be output
        final SortedSet<Attr> result = this.result;
        result.clear();

        if (element.hasAttributes()) {
            NamedNodeMap attrs = element.getAttributes();
            int attrsLength = attrs.getLength();

            for (int i = 0; i < attrsLength; i++) {
                Attr attribute = (Attr) attrs.item(i);
                String NUri = attribute.getNamespaceURI();
                String NName = attribute.getLocalName();
                String NValue = attribute.getValue();

                if (!XMLNS_URI.equals(NUri)) {
                    //It's not a namespace attr node. Add to the result and continue.
                    result.add(attribute);
                } else if (!(XML.equals(NName) && XML_LANG_URI.equals(NValue))) {
                    //The default mapping for xml must not be output.
                    Node n = ns.addMappingAndRender(NName, NValue, attribute);

                    if (n != null) {
                        //Render the ns definition
                        result.add((Attr)n);
                        if (C14nHelper.namespaceIsRelative(attribute)) {
                            Object exArgs[] = { element.getTagName(), NName, attribute.getNodeValue() };
                            throw new CanonicalizationException(
                                "c14n.Canonicalizer.RelativeNamespace", exArgs
                            );
                        }
                    }
                }
            }
        }

        if (firstCall) {
            //It is the first node of the subtree
            //Obtain all the namespaces defined in the parents, and added to the output.
            ns.getUnrenderedNodes(result);
            //output the attributes in the xml namespace.
            xmlattrStack.getXmlnsAttr(result);
            firstCall = false;
        }

        return result.iterator();
    }

    /**
     * Returns the Attr[]s to be output for the given element.
     * <br>
     * IMPORTANT: This method expects to work on a modified DOM tree, i.e. a DOM which has
     * been prepared using {@link com.sun.org.apache.xml.internal.security.utils.XMLUtils#circumventBug2650(
     * org.w3c.dom.Document)}.
     *
     * @param element
     * @param ns
     * @return the Attr[]s to be output
     * @throws CanonicalizationException
     */
    @Override
    protected Iterator<Attr> handleAttributes(Element element, NameSpaceSymbTable ns)
        throws CanonicalizationException {
        // result will contain the attrs which have to be output
        xmlattrStack.push(ns.getLevel());
        boolean isRealVisible = isVisibleDO(element, ns.getLevel()) == 1;
        final SortedSet<Attr> result = this.result;
        result.clear();

        if (element.hasAttributes()) {
            NamedNodeMap attrs = element.getAttributes();
            int attrsLength = attrs.getLength();

            for (int i = 0; i < attrsLength; i++) {
                Attr attribute = (Attr) attrs.item(i);
                String NUri = attribute.getNamespaceURI();
                String NName = attribute.getLocalName();
                String NValue = attribute.getValue();

                if (!XMLNS_URI.equals(NUri)) {
                    //A non namespace definition node.
                    if (XML_LANG_URI.equals(NUri)) {
                        xmlattrStack.addXmlnsAttr(attribute);
                    } else if (isRealVisible) {
                        //The node is visible add the attribute to the list of output attributes.
                        result.add(attribute);
                    }
                } else if (!XML.equals(NName) || !XML_LANG_URI.equals(NValue)) {
                    /* except omit namespace node with local name xml, which defines
                     * the xml prefix, if its string value is http://www.w3.org/XML/1998/namespace.
                     */
                    //add the prefix binding to the ns symb table.
                    if (isVisible(attribute))  {
                        if (isRealVisible || !ns.removeMappingIfRender(NName)) {
                            //The xpath select this node output it if needed.
                            Node n = ns.addMappingAndRender(NName, NValue, attribute);
                            if (n != null) {
                                result.add((Attr)n);
                                if (C14nHelper.namespaceIsRelative(attribute)) {
                                    Object exArgs[] = { element.getTagName(), NName, attribute.getNodeValue() };
                                    throw new CanonicalizationException(
                                        "c14n.Canonicalizer.RelativeNamespace", exArgs
                                    );
                                }
                            }
                        }
                    } else {
                        if (isRealVisible && !XMLNS.equals(NName)) {
                            ns.removeMapping(NName);
                        } else {
                            ns.addMapping(NName, NValue, attribute);
                        }
                    }
                }
            }
        }
        if (isRealVisible) {
            //The element is visible, handle the xmlns definition
            Attr xmlns = element.getAttributeNodeNS(XMLNS_URI, XMLNS);
            Node n = null;
            if (xmlns == null) {
                //No xmlns def just get the already defined.
                n = ns.getMapping(XMLNS);
            } else if (!isVisible(xmlns)) {
                //There is a definition but the xmlns is not selected by the xpath.
                //then xmlns=""
                n = ns.addMappingAndRender(XMLNS, "", nullNode);
            }
            //output the xmlns def if needed.
            if (n != null) {
                result.add((Attr)n);
            }
            //Float all xml:* attributes of the unselected parent elements to this one.
            xmlattrStack.getXmlnsAttr(result);
            ns.getUnrenderedNodes(result);
        }

        return result.iterator();
    }

    protected void circumventBugIfNeeded(XMLSignatureInput input)
        throws CanonicalizationException, ParserConfigurationException, IOException, SAXException {
        if (!input.isNeedsToBeExpanded()) {
            return;
        }
        Document doc = null;
        if (input.getSubNode() != null) {
            doc = XMLUtils.getOwnerDocument(input.getSubNode());
        } else {
            doc = XMLUtils.getOwnerDocument(input.getNodeSet());
        }
        XMLUtils.circumventBug2650(doc);
    }

    @Override
    protected void handleParent(Element e, NameSpaceSymbTable ns) {
        if (!e.hasAttributes() && e.getNamespaceURI() == null) {
            return;
        }
        xmlattrStack.push(-1);
        NamedNodeMap attrs = e.getAttributes();
        int attrsLength = attrs.getLength();
        for (int i = 0; i < attrsLength; i++) {
            Attr attribute = (Attr) attrs.item(i);
            String NName = attribute.getLocalName();
            String NValue = attribute.getNodeValue();

            if (Constants.NamespaceSpecNS.equals(attribute.getNamespaceURI())) {
                if (!XML.equals(NName) || !Constants.XML_LANG_SPACE_SpecNS.equals(NValue)) {
                    ns.addMapping(NName, NValue, attribute);
                }
            } else if (XML_LANG_URI.equals(attribute.getNamespaceURI())) {
                xmlattrStack.addXmlnsAttr(attribute);
            }
        }
        if (e.getNamespaceURI() != null) {
            String NName = e.getPrefix();
            String NValue = e.getNamespaceURI();
            String Name;
            if (NName == null || NName.equals("")) {
                NName = "xmlns";
                Name = "xmlns";
            } else {
                Name = "xmlns:" + NName;
            }
            Attr n = e.getOwnerDocument().createAttributeNS("http://www.w3.org/2000/xmlns/", Name);
            n.setValue(NValue);
            ns.addMapping(NName, NValue, n);
        }
    }
}
