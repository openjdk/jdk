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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizerSpi;
import com.sun.org.apache.xml.internal.security.c14n.helper.AttrCompare;
import com.sun.org.apache.xml.internal.security.signature.NodeFilter;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.UnsyncByteArrayOutputStream;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.SAXException;

/**
 * Abstract base class for canonicalization algorithms.
 *
 * @author Christian Geuer-Pollmann <geuerp@apache.org>
 */
public abstract class CanonicalizerBase extends CanonicalizerSpi {
    public static final String XML = "xml";
    public static final String XMLNS = "xmlns";

    protected static final AttrCompare COMPARE = new AttrCompare();

    private static final byte[] END_PI = {'?','>'};
    private static final byte[] BEGIN_PI = {'<','?'};
    private static final byte[] END_COMM = {'-','-','>'};
    private static final byte[] BEGIN_COMM = {'<','!','-','-'};
    private static final byte[] XA = {'&','#','x','A',';'};
    private static final byte[] X9 = {'&','#','x','9',';'};
    private static final byte[] QUOT = {'&','q','u','o','t',';'};
    private static final byte[] XD = {'&','#','x','D',';'};
    private static final byte[] GT = {'&','g','t',';'};
    private static final byte[] LT = {'&','l','t',';'};
    private static final byte[] END_TAG = {'<','/'};
    private static final byte[] AMP = {'&','a','m','p',';'};
    private static final byte[] equalsStr = {'=','\"'};

    protected static final int NODE_BEFORE_DOCUMENT_ELEMENT = -1;
    protected static final int NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT = 0;
    protected static final int NODE_AFTER_DOCUMENT_ELEMENT = 1;

    private List<NodeFilter> nodeFilter;

    private boolean includeComments;
    private Set<Node> xpathNodeSet;

    /**
     * The node to be skipped/excluded from the DOM tree
     * in subtree canonicalizations.
     */
    private Node excludeNode;
    private OutputStream writer = new ByteArrayOutputStream();

    /**
     * The null xmlns definition.
     */
    private Attr nullNode;

    /**
     * Constructor CanonicalizerBase
     *
     * @param includeComments
     */
    public CanonicalizerBase(boolean includeComments) {
        this.includeComments = includeComments;
    }

    /**
     * Method engineCanonicalizeSubTree
     * @inheritDoc
     * @param rootNode
     * @throws CanonicalizationException
     */
    public byte[] engineCanonicalizeSubTree(Node rootNode)
        throws CanonicalizationException {
        return engineCanonicalizeSubTree(rootNode, (Node)null);
    }

    /**
     * Method engineCanonicalizeXPathNodeSet
     * @inheritDoc
     * @param xpathNodeSet
     * @throws CanonicalizationException
     */
    public byte[] engineCanonicalizeXPathNodeSet(Set<Node> xpathNodeSet)
        throws CanonicalizationException {
        this.xpathNodeSet = xpathNodeSet;
        return engineCanonicalizeXPathNodeSetInternal(XMLUtils.getOwnerDocument(this.xpathNodeSet));
    }

    /**
     * Canonicalizes a Subtree node.
     * @param input the root of the subtree to canicalize
     * @return The canonicalize stream.
     * @throws CanonicalizationException
     */
    public byte[] engineCanonicalize(XMLSignatureInput input) throws CanonicalizationException {
        try {
            if (input.isExcludeComments()) {
                includeComments = false;
            }
            if (input.isOctetStream()) {
                return engineCanonicalize(input.getBytes());
            }
            if (input.isElement()) {
                return engineCanonicalizeSubTree(input.getSubNode(), input.getExcludeNode());
            } else if (input.isNodeSet()) {
                nodeFilter = input.getNodeFilters();

                circumventBugIfNeeded(input);

                if (input.getSubNode() != null) {
                    return engineCanonicalizeXPathNodeSetInternal(input.getSubNode());
                } else {
                    return engineCanonicalizeXPathNodeSet(input.getNodeSet());
                }
            }
            return null;
        } catch (CanonicalizationException ex) {
            throw new CanonicalizationException("empty", ex);
        } catch (ParserConfigurationException ex) {
            throw new CanonicalizationException("empty", ex);
        } catch (IOException ex) {
            throw new CanonicalizationException("empty", ex);
        } catch (SAXException ex) {
            throw new CanonicalizationException("empty", ex);
        }
    }

    /**
     * @param writer The writer to set.
     */
    public void setWriter(OutputStream writer) {
        this.writer = writer;
    }

    /**
     * Canonicalizes a Subtree node.
     *
     * @param rootNode
     *            the root of the subtree to canonicalize
     * @param excludeNode
     *            a node to be excluded from the canonicalize operation
     * @return The canonicalize stream.
     * @throws CanonicalizationException
     */
    protected byte[] engineCanonicalizeSubTree(Node rootNode, Node excludeNode)
        throws CanonicalizationException {
        this.excludeNode = excludeNode;
        try {
            NameSpaceSymbTable ns = new NameSpaceSymbTable();
            int nodeLevel = NODE_BEFORE_DOCUMENT_ELEMENT;
            if (rootNode != null && Node.ELEMENT_NODE == rootNode.getNodeType()) {
                //Fills the nssymbtable with the definitions of the parent of the root subnode
                getParentNameSpaces((Element)rootNode, ns);
                nodeLevel = NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT;
            }
            this.canonicalizeSubTree(rootNode, ns, rootNode, nodeLevel);
            this.writer.flush();
            if (this.writer instanceof ByteArrayOutputStream) {
                byte[] result = ((ByteArrayOutputStream)this.writer).toByteArray();
                if (reset) {
                    ((ByteArrayOutputStream)this.writer).reset();
                } else {
                    this.writer.close();
                }
                return result;
            } else if (this.writer instanceof UnsyncByteArrayOutputStream) {
                byte[] result = ((UnsyncByteArrayOutputStream)this.writer).toByteArray();
                if (reset) {
                    ((UnsyncByteArrayOutputStream)this.writer).reset();
                } else {
                    this.writer.close();
                }
                return result;
            } else {
                this.writer.close();
            }
            return null;

        } catch (UnsupportedEncodingException ex) {
            throw new CanonicalizationException("empty", ex);
        } catch (IOException ex) {
            throw new CanonicalizationException("empty", ex);
        }
    }


    /**
     * Method canonicalizeSubTree, this function is a recursive one.
     *
     * @param currentNode
     * @param ns
     * @param endnode
     * @throws CanonicalizationException
     * @throws IOException
     */
    protected final void canonicalizeSubTree(
        Node currentNode, NameSpaceSymbTable ns, Node endnode, int documentLevel
    ) throws CanonicalizationException, IOException {
        if (isVisibleInt(currentNode) == -1) {
            return;
        }
        Node sibling = null;
        Node parentNode = null;
        final OutputStream writer = this.writer;
        final Node excludeNode = this.excludeNode;
        final boolean includeComments = this.includeComments;
        Map<String, byte[]> cache = new HashMap<String, byte[]>();
        do {
            switch (currentNode.getNodeType()) {

            case Node.ENTITY_NODE :
            case Node.NOTATION_NODE :
            case Node.ATTRIBUTE_NODE :
                // illegal node type during traversal
                throw new CanonicalizationException("empty");

            case Node.DOCUMENT_FRAGMENT_NODE :
            case Node.DOCUMENT_NODE :
                ns.outputNodePush();
                sibling = currentNode.getFirstChild();
                break;

            case Node.COMMENT_NODE :
                if (includeComments) {
                    outputCommentToWriter((Comment) currentNode, writer, documentLevel);
                }
                break;

            case Node.PROCESSING_INSTRUCTION_NODE :
                outputPItoWriter((ProcessingInstruction) currentNode, writer, documentLevel);
                break;

            case Node.TEXT_NODE :
            case Node.CDATA_SECTION_NODE :
                outputTextToWriter(currentNode.getNodeValue(), writer);
                break;

            case Node.ELEMENT_NODE :
                documentLevel = NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT;
                if (currentNode == excludeNode) {
                    break;
                }
                Element currentElement = (Element)currentNode;
                //Add a level to the nssymbtable. So latter can be pop-back.
                ns.outputNodePush();
                writer.write('<');
                String name = currentElement.getTagName();
                UtfHelpper.writeByte(name, writer, cache);

                Iterator<Attr> attrs = this.handleAttributesSubtree(currentElement, ns);
                if (attrs != null) {
                    //we output all Attrs which are available
                    while (attrs.hasNext()) {
                        Attr attr = attrs.next();
                        outputAttrToWriter(attr.getNodeName(), attr.getNodeValue(), writer, cache);
                    }
                }
                writer.write('>');
                sibling = currentNode.getFirstChild();
                if (sibling == null) {
                    writer.write(END_TAG);
                    UtfHelpper.writeStringToUtf8(name, writer);
                    writer.write('>');
                    //We finished with this level, pop to the previous definitions.
                    ns.outputNodePop();
                    if (parentNode != null) {
                        sibling = currentNode.getNextSibling();
                    }
                } else {
                    parentNode = currentElement;
                }
                break;

            case Node.DOCUMENT_TYPE_NODE :
            default :
                break;
            }
            while (sibling == null && parentNode != null) {
                writer.write(END_TAG);
                UtfHelpper.writeByte(((Element)parentNode).getTagName(), writer, cache);
                writer.write('>');
                //We finished with this level, pop to the previous definitions.
                ns.outputNodePop();
                if (parentNode == endnode) {
                    return;
                }
                sibling = parentNode.getNextSibling();
                parentNode = parentNode.getParentNode();
                if (parentNode == null || Node.ELEMENT_NODE != parentNode.getNodeType()) {
                    documentLevel = NODE_AFTER_DOCUMENT_ELEMENT;
                    parentNode = null;
                }
            }
            if (sibling == null) {
                return;
            }
            currentNode = sibling;
            sibling = currentNode.getNextSibling();
        } while(true);
    }


    private byte[] engineCanonicalizeXPathNodeSetInternal(Node doc)
        throws CanonicalizationException {
        try {
            this.canonicalizeXPathNodeSet(doc, doc);
            this.writer.flush();
            if (this.writer instanceof ByteArrayOutputStream) {
                byte[] sol = ((ByteArrayOutputStream)this.writer).toByteArray();
                if (reset) {
                    ((ByteArrayOutputStream)this.writer).reset();
                } else {
                    this.writer.close();
                }
                return sol;
            } else if (this.writer instanceof UnsyncByteArrayOutputStream) {
                byte[] result = ((UnsyncByteArrayOutputStream)this.writer).toByteArray();
                if (reset) {
                    ((UnsyncByteArrayOutputStream)this.writer).reset();
                } else {
                    this.writer.close();
                }
                return result;
            } else {
                this.writer.close();
            }
            return null;
        } catch (UnsupportedEncodingException ex) {
            throw new CanonicalizationException("empty", ex);
        } catch (IOException ex) {
            throw new CanonicalizationException("empty", ex);
        }
    }

    /**
     * Canonicalizes all the nodes included in the currentNode and contained in the
     * xpathNodeSet field.
     *
     * @param currentNode
     * @param endnode
     * @throws CanonicalizationException
     * @throws IOException
     */
    protected final void canonicalizeXPathNodeSet(Node currentNode, Node endnode)
        throws CanonicalizationException, IOException {
        if (isVisibleInt(currentNode) == -1) {
            return;
        }
        boolean currentNodeIsVisible = false;
        NameSpaceSymbTable ns = new NameSpaceSymbTable();
        if (currentNode != null && Node.ELEMENT_NODE == currentNode.getNodeType()) {
            getParentNameSpaces((Element)currentNode, ns);
        }
        if (currentNode == null) {
            return;
        }
        Node sibling = null;
        Node parentNode = null;
        OutputStream writer = this.writer;
        int documentLevel = NODE_BEFORE_DOCUMENT_ELEMENT;
        Map<String, byte[]> cache = new HashMap<String, byte[]>();
        do {
            switch (currentNode.getNodeType()) {

            case Node.ENTITY_NODE :
            case Node.NOTATION_NODE :
            case Node.ATTRIBUTE_NODE :
                // illegal node type during traversal
                throw new CanonicalizationException("empty");

            case Node.DOCUMENT_FRAGMENT_NODE :
            case Node.DOCUMENT_NODE :
                ns.outputNodePush();
                sibling = currentNode.getFirstChild();
                break;

            case Node.COMMENT_NODE :
                if (this.includeComments && (isVisibleDO(currentNode, ns.getLevel()) == 1)) {
                    outputCommentToWriter((Comment) currentNode, writer, documentLevel);
                }
                break;

            case Node.PROCESSING_INSTRUCTION_NODE :
                if (isVisible(currentNode)) {
                    outputPItoWriter((ProcessingInstruction) currentNode, writer, documentLevel);
                }
                break;

            case Node.TEXT_NODE :
            case Node.CDATA_SECTION_NODE :
                if (isVisible(currentNode)) {
                    outputTextToWriter(currentNode.getNodeValue(), writer);
                    for (Node nextSibling = currentNode.getNextSibling();
                        (nextSibling != null) && ((nextSibling.getNodeType() == Node.TEXT_NODE)
                            || (nextSibling.getNodeType() == Node.CDATA_SECTION_NODE));
                        nextSibling = nextSibling.getNextSibling()) {
                        outputTextToWriter(nextSibling.getNodeValue(), writer);
                        currentNode = nextSibling;
                        sibling = currentNode.getNextSibling();
                    }
                }
                break;

            case Node.ELEMENT_NODE :
                documentLevel = NODE_NOT_BEFORE_OR_AFTER_DOCUMENT_ELEMENT;
                Element currentElement = (Element) currentNode;
                //Add a level to the nssymbtable. So latter can be pop-back.
                String name = null;
                int i = isVisibleDO(currentNode, ns.getLevel());
                if (i == -1) {
                    sibling = currentNode.getNextSibling();
                    break;
                }
                currentNodeIsVisible = (i == 1);
                if (currentNodeIsVisible) {
                    ns.outputNodePush();
                    writer.write('<');
                    name = currentElement.getTagName();
                    UtfHelpper.writeByte(name, writer, cache);
                } else {
                    ns.push();
                }

                Iterator<Attr> attrs = handleAttributes(currentElement,ns);
                if (attrs != null) {
                    //we output all Attrs which are available
                    while (attrs.hasNext()) {
                        Attr attr = attrs.next();
                        outputAttrToWriter(attr.getNodeName(), attr.getNodeValue(), writer, cache);
                    }
                }
                if (currentNodeIsVisible) {
                    writer.write('>');
                }
                sibling = currentNode.getFirstChild();

                if (sibling == null) {
                    if (currentNodeIsVisible) {
                        writer.write(END_TAG);
                        UtfHelpper.writeByte(name, writer, cache);
                        writer.write('>');
                        //We finished with this level, pop to the previous definitions.
                        ns.outputNodePop();
                    } else {
                        ns.pop();
                    }
                    if (parentNode != null) {
                        sibling = currentNode.getNextSibling();
                    }
                } else {
                    parentNode = currentElement;
                }
                break;

            case Node.DOCUMENT_TYPE_NODE :
            default :
                break;
            }
            while (sibling == null && parentNode != null) {
                if (isVisible(parentNode)) {
                    writer.write(END_TAG);
                    UtfHelpper.writeByte(((Element)parentNode).getTagName(), writer, cache);
                    writer.write('>');
                    //We finished with this level, pop to the previous definitions.
                    ns.outputNodePop();
                } else {
                    ns.pop();
                }
                if (parentNode == endnode) {
                    return;
                }
                sibling = parentNode.getNextSibling();
                parentNode = parentNode.getParentNode();
                if (parentNode == null || Node.ELEMENT_NODE != parentNode.getNodeType()) {
                    parentNode = null;
                    documentLevel = NODE_AFTER_DOCUMENT_ELEMENT;
                }
            }
            if (sibling == null) {
                return;
            }
            currentNode = sibling;
            sibling = currentNode.getNextSibling();
        } while(true);
    }

    protected int isVisibleDO(Node currentNode, int level) {
        if (nodeFilter != null) {
            Iterator<NodeFilter> it = nodeFilter.iterator();
            while (it.hasNext()) {
                int i = (it.next()).isNodeIncludeDO(currentNode, level);
                if (i != 1) {
                    return i;
                }
            }
        }
        if ((this.xpathNodeSet != null) && !this.xpathNodeSet.contains(currentNode)) {
            return 0;
        }
        return 1;
    }

    protected int isVisibleInt(Node currentNode) {
        if (nodeFilter != null) {
            Iterator<NodeFilter> it = nodeFilter.iterator();
            while (it.hasNext()) {
                int i = (it.next()).isNodeInclude(currentNode);
                if (i != 1) {
                    return i;
                }
            }
        }
        if ((this.xpathNodeSet != null) && !this.xpathNodeSet.contains(currentNode)) {
            return 0;
        }
        return 1;
    }

    protected boolean isVisible(Node currentNode) {
        if (nodeFilter != null) {
            Iterator<NodeFilter> it = nodeFilter.iterator();
            while (it.hasNext()) {
                if (it.next().isNodeInclude(currentNode) != 1) {
                    return false;
                }
            }
        }
        if ((this.xpathNodeSet != null) && !this.xpathNodeSet.contains(currentNode)) {
            return false;
        }
        return true;
    }

    protected void handleParent(Element e, NameSpaceSymbTable ns) {
        if (!e.hasAttributes() && e.getNamespaceURI() == null) {
            return;
        }
        NamedNodeMap attrs = e.getAttributes();
        int attrsLength = attrs.getLength();
        for (int i = 0; i < attrsLength; i++) {
            Attr attribute = (Attr) attrs.item(i);
            String NName = attribute.getLocalName();
            String NValue = attribute.getNodeValue();

            if (Constants.NamespaceSpecNS.equals(attribute.getNamespaceURI())
                && (!XML.equals(NName) || !Constants.XML_LANG_SPACE_SpecNS.equals(NValue))) {
                ns.addMapping(NName, NValue, attribute);
            }
        }
        if (e.getNamespaceURI() != null) {
            String NName = e.getPrefix();
            String NValue = e.getNamespaceURI();
            String Name;
            if (NName == null || NName.equals("")) {
                NName = XMLNS;
                Name = XMLNS;
            } else {
                Name = XMLNS + ":" + NName;
            }
            Attr n = e.getOwnerDocument().createAttributeNS("http://www.w3.org/2000/xmlns/", Name);
            n.setValue(NValue);
            ns.addMapping(NName, NValue, n);
        }
    }

    /**
     * Adds to ns the definitions from the parent elements of el
     * @param el
     * @param ns
     */
    protected final void getParentNameSpaces(Element el, NameSpaceSymbTable ns)  {
        Node n1 = el.getParentNode();
        if (n1 == null || Node.ELEMENT_NODE != n1.getNodeType()) {
            return;
        }
        //Obtain all the parents of the element
        List<Element> parents = new ArrayList<Element>();
        Node parent = n1;
        while (parent != null && Node.ELEMENT_NODE == parent.getNodeType()) {
            parents.add((Element)parent);
            parent = parent.getParentNode();
        }
        //Visit them in reverse order.
        ListIterator<Element> it = parents.listIterator(parents.size());
        while (it.hasPrevious()) {
            Element ele = it.previous();
            handleParent(ele, ns);
        }
        parents.clear();
        Attr nsprefix;
        if (((nsprefix = ns.getMappingWithoutRendered(XMLNS)) != null)
                && "".equals(nsprefix.getValue())) {
            ns.addMappingAndRender(
                    XMLNS, "", getNullNode(nsprefix.getOwnerDocument()));
        }
    }

    /**
     * Obtain the attributes to output for this node in XPathNodeSet c14n.
     *
     * @param element
     * @param ns
     * @return the attributes nodes to output.
     * @throws CanonicalizationException
     */
    abstract Iterator<Attr> handleAttributes(Element element, NameSpaceSymbTable ns)
        throws CanonicalizationException;

    /**
     * Obtain the attributes to output for this node in a Subtree c14n.
     *
     * @param element
     * @param ns
     * @return the attributes nodes to output.
     * @throws CanonicalizationException
     */
    abstract Iterator<Attr> handleAttributesSubtree(Element element, NameSpaceSymbTable ns)
        throws CanonicalizationException;

    abstract void circumventBugIfNeeded(XMLSignatureInput input)
        throws CanonicalizationException, ParserConfigurationException, IOException, SAXException;

    /**
     * Outputs an Attribute to the internal Writer.
     *
     * The string value of the node is modified by replacing
     * <UL>
     * <LI>all ampersands (&) with <CODE>&amp;amp;</CODE></LI>
     * <LI>all open angle brackets (<) with <CODE>&amp;lt;</CODE></LI>
     * <LI>all quotation mark characters with <CODE>&amp;quot;</CODE></LI>
     * <LI>and the whitespace characters <CODE>#x9</CODE>, #xA, and #xD, with character
     * references. The character references are written in uppercase
     * hexadecimal with no leading zeroes (for example, <CODE>#xD</CODE> is represented
     * by the character reference <CODE>&amp;#xD;</CODE>)</LI>
     * </UL>
     *
     * @param name
     * @param value
     * @param writer
     * @throws IOException
     */
    protected static final void outputAttrToWriter(
        final String name, final String value,
        final OutputStream writer, final Map<String, byte[]> cache
    ) throws IOException {
        writer.write(' ');
        UtfHelpper.writeByte(name, writer, cache);
        writer.write(equalsStr);
        byte[] toWrite;
        final int length = value.length();
        int i = 0;
        while (i < length) {
            char c = value.charAt(i++);

            switch (c) {

            case '&' :
                toWrite = AMP;
                break;

            case '<' :
                toWrite = LT;
                break;

            case '"' :
                toWrite = QUOT;
                break;

            case 0x09 :    // '\t'
                toWrite = X9;
                break;

            case 0x0A :    // '\n'
                toWrite = XA;
                break;

            case 0x0D :    // '\r'
                toWrite = XD;
                break;

            default :
                if (c < 0x80) {
                    writer.write(c);
                } else {
                    UtfHelpper.writeCharToUtf8(c, writer);
                }
                continue;
            }
            writer.write(toWrite);
        }

        writer.write('\"');
    }

    /**
     * Outputs a PI to the internal Writer.
     *
     * @param currentPI
     * @param writer where to write the things
     * @throws IOException
     */
    protected void outputPItoWriter(
        ProcessingInstruction currentPI, OutputStream writer, int position
    ) throws IOException {
        if (position == NODE_AFTER_DOCUMENT_ELEMENT) {
            writer.write('\n');
        }
        writer.write(BEGIN_PI);

        final String target = currentPI.getTarget();
        int length = target.length();

        for (int i = 0; i < length; i++) {
            char c = target.charAt(i);
            if (c == 0x0D) {
                writer.write(XD);
            } else {
                if (c < 0x80) {
                    writer.write(c);
                } else {
                    UtfHelpper.writeCharToUtf8(c, writer);
                }
            }
        }

        final String data = currentPI.getData();

        length = data.length();

        if (length > 0) {
            writer.write(' ');

            for (int i = 0; i < length; i++) {
                char c = data.charAt(i);
                if (c == 0x0D) {
                    writer.write(XD);
                } else {
                    UtfHelpper.writeCharToUtf8(c, writer);
                }
            }
        }

        writer.write(END_PI);
        if (position == NODE_BEFORE_DOCUMENT_ELEMENT) {
            writer.write('\n');
        }
    }

    /**
     * Method outputCommentToWriter
     *
     * @param currentComment
     * @param writer writer where to write the things
     * @throws IOException
     */
    protected void outputCommentToWriter(
        Comment currentComment, OutputStream writer, int position
    ) throws IOException {
        if (position == NODE_AFTER_DOCUMENT_ELEMENT) {
            writer.write('\n');
        }
        writer.write(BEGIN_COMM);

        final String data = currentComment.getData();
        final int length = data.length();

        for (int i = 0; i < length; i++) {
            char c = data.charAt(i);
            if (c == 0x0D) {
                writer.write(XD);
            } else {
                if (c < 0x80) {
                    writer.write(c);
                } else {
                    UtfHelpper.writeCharToUtf8(c, writer);
                }
            }
        }

        writer.write(END_COMM);
        if (position == NODE_BEFORE_DOCUMENT_ELEMENT) {
            writer.write('\n');
        }
    }

    /**
     * Outputs a Text of CDATA section to the internal Writer.
     *
     * @param text
     * @param writer writer where to write the things
     * @throws IOException
     */
    protected static final void outputTextToWriter(
        final String text, final OutputStream writer
    ) throws IOException {
        final int length = text.length();
        byte[] toWrite;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            switch (c) {

            case '&' :
                toWrite = AMP;
                break;

            case '<' :
                toWrite = LT;
                break;

            case '>' :
                toWrite = GT;
                break;

            case 0xD :
                toWrite = XD;
                break;

            default :
                if (c < 0x80) {
                    writer.write(c);
                } else {
                    UtfHelpper.writeCharToUtf8(c, writer);
                }
                continue;
            }
            writer.write(toWrite);
        }
    }

    // The null xmlns definition.
    protected Attr getNullNode(Document ownerDocument) {
        if (nullNode == null) {
            try {
                nullNode = ownerDocument.createAttributeNS(
                                    Constants.NamespaceSpecNS, XMLNS);
                nullNode.setValue("");
            } catch (Exception e) {
                throw new RuntimeException("Unable to create nullNode: " + e);
            }
        }
        return nullNode;
    }

}
