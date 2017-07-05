/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2008 The Apache Software Foundation.
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
import java.net.URI;
import java.net.URISyntaxException;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.util.logging.Logger;
import java.util.logging.Logger;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.helper.C14nHelper;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;

/**
 * Implements <A HREF="http://www.w3.org/TR/2008/PR-xml-c14n11-20080129/">
 * Canonical XML Version 1.1</A>, a W3C Proposed Recommendation from 29
 * January 2008.
 *
 * @author Sean Mullan
 * @author Raul Benito
 * @version $Revision: 1.2 $
 */
public abstract class Canonicalizer11 extends CanonicalizerBase {
    boolean firstCall = true;
    final SortedSet result = new TreeSet(COMPARE);
    static final String XMLNS_URI = Constants.NamespaceSpecNS;
    static final String XML_LANG_URI = Constants.XML_LANG_SPACE_SpecNS;

    static Logger log = Logger.getLogger(Canonicalizer11.class.getName());

    static class XmlAttrStack {
        int currentLevel = 0;
        int lastlevel = 0;
        XmlsStackElement cur;
        static class XmlsStackElement {
            int level;
            boolean rendered = false;
            List nodes = new ArrayList();
        };
        List levels = new ArrayList();
        void push(int level) {
            currentLevel = level;
            if (currentLevel == -1)
                return;
            cur = null;
            while (lastlevel >= currentLevel) {
                levels.remove(levels.size() - 1);
                if (levels.size() == 0) {
                    lastlevel = 0;
                    return;
                }
                lastlevel=((XmlsStackElement)levels.get(levels.size()-1)).level;
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
        void getXmlnsAttr(Collection col) {
            if (cur == null) {
                cur = new XmlsStackElement();
                cur.level = currentLevel;
                lastlevel = currentLevel;
                levels.add(cur);
            }
            int size = levels.size() - 2;
            boolean parentRendered = false;
            XmlsStackElement e = null;
            if (size == -1) {
                parentRendered = true;
            } else {
                e = (XmlsStackElement) levels.get(size);
                if (e.rendered && e.level+1 == currentLevel)
                    parentRendered = true;
            }
            if (parentRendered) {
                col.addAll(cur.nodes);
                cur.rendered = true;
                return;
            }

            Map loa = new HashMap();
            List baseAttrs = new ArrayList();
            boolean successiveOmitted = true;
            for (;size>=0;size--) {
                e = (XmlsStackElement) levels.get(size);
                if (e.rendered) {
                    successiveOmitted = false;
                }
                Iterator it = e.nodes.iterator();
                while (it.hasNext() && successiveOmitted) {
                    Attr n = (Attr) it.next();
                    if (n.getLocalName().equals("base")) {
                        if (!e.rendered) {
                            baseAttrs.add(n);
                        }
                    } else if (!loa.containsKey(n.getName()))
                        loa.put(n.getName(), n);
                }
            }
            if (!baseAttrs.isEmpty()) {
                Iterator it = cur.nodes.iterator();
                String base = null;
                Attr baseAttr = null;
                while (it.hasNext()) {
                    Attr n = (Attr) it.next();
                    if (n.getLocalName().equals("base")) {
                        base = n.getValue();
                        baseAttr = n;
                        break;
                    }
                }
                it = baseAttrs.iterator();
                while (it.hasNext()) {
                    Attr n = (Attr) it.next();
                    if (base == null) {
                        base = n.getValue();
                        baseAttr = n;
                    } else {
                        try {
                            base = joinURI(n.getValue(), base);
                        } catch (URISyntaxException ue) {
                            ue.printStackTrace();
                        }
                    }
                }
                if (base != null && base.length() != 0) {
                    baseAttr.setValue(base);
                    col.add(baseAttr);
                }
            }

            cur.rendered = true;
            col.addAll(loa.values());
        }
    };
    XmlAttrStack xmlattrStack = new XmlAttrStack();

    /**
     * Constructor Canonicalizer11
     *
     * @param includeComments
     */
    public Canonicalizer11(boolean includeComments) {
        super(includeComments);
    }

    /**
     * Returns the Attr[]s to be outputted for the given element.
     * <br>
     * The code of this method is a copy of {@link #handleAttributes(Element,
     * NameSpaceSymbTable)},
     * whereas it takes into account that subtree-c14n is -- well --
     * subtree-based.
     * So if the element in question isRoot of c14n, it's parent is not in the
     * node set, as well as all other ancestors.
     *
     * @param E
     * @param ns
     * @return the Attr[]s to be outputted
     * @throws CanonicalizationException
     */
    Iterator handleAttributesSubtree(Element E, NameSpaceSymbTable ns)
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
            String NUri = N.getNamespaceURI();

            if (XMLNS_URI != NUri) {
                // It's not a namespace attr node. Add to the result and
                // continue.
                result.add(N);
                continue;
            }

            String NName = N.getLocalName();
            String NValue = N.getValue();
            if (XML.equals(NName)
                && XML_LANG_URI.equals(NValue)) {
                // The default mapping for xml must not be output.
                continue;
            }

            Node n = ns.addMappingAndRender(NName, NValue, N);

            if (n != null) {
                // Render the ns definition
                result.add(n);
                if (C14nHelper.namespaceIsRelative(N)) {
                    Object exArgs[] = {E.getTagName(), NName, N.getNodeValue()};
                    throw new CanonicalizationException(
                        "c14n.Canonicalizer.RelativeNamespace", exArgs);
                }
            }
        }

        if (firstCall) {
            // It is the first node of the subtree
            // Obtain all the namespaces defined in the parents, and added
            // to the output.
            ns.getUnrenderedNodes(result);
            // output the attributes in the xml namespace.
            xmlattrStack.getXmlnsAttr(result);
            firstCall = false;
        }

        return result.iterator();
    }

    /**
     * Returns the Attr[]s to be outputted for the given element.
     * <br>
     * IMPORTANT: This method expects to work on a modified DOM tree, i.e. a
     * DOM which has been prepared using
     * {@link com.sun.org.apache.xml.internal.security.utils.XMLUtils#circumventBug2650(
     * org.w3c.dom.Document)}.
     *
     * @param E
     * @param ns
     * @return the Attr[]s to be outputted
     * @throws CanonicalizationException
     */
    Iterator handleAttributes(Element E, NameSpaceSymbTable ns)
        throws CanonicalizationException {
        // result will contain the attrs which have to be output
        xmlattrStack.push(ns.getLevel());
        boolean isRealVisible = isVisibleDO(E, ns.getLevel()) == 1;
        NamedNodeMap attrs = null;
        int attrsLength = 0;
        if (E.hasAttributes()) {
            attrs = E.getAttributes();
            attrsLength = attrs.getLength();
        }

        SortedSet result = this.result;
        result.clear();

        for (int i = 0; i < attrsLength; i++) {
            Attr N = (Attr) attrs.item(i);
            String NUri = N.getNamespaceURI();

            if (XMLNS_URI != NUri) {
                // A non namespace definition node.
                if (XML_LANG_URI == NUri) {
                    if (N.getLocalName().equals("id")) {
                        if (isRealVisible) {
                            // treat xml:id like any other attribute
                            // (emit it, but don't inherit it)
                            result.add(N);
                        }
                    } else {
                        xmlattrStack.addXmlnsAttr(N);
                    }
                } else if (isRealVisible) {
                    // The node is visible add the attribute to the list of
                    // output attributes.
                    result.add(N);
                }
                // keep working
                continue;
            }

            String NName = N.getLocalName();
            String NValue = N.getValue();
            if ("xml".equals(NName)
                && XML_LANG_URI.equals(NValue)) {
                /* except omit namespace node with local name xml, which defines
                 * the xml prefix, if its string value is
                 * http://www.w3.org/XML/1998/namespace.
                 */
                continue;
            }
            // add the prefix binding to the ns symb table.
            // ns.addInclusiveMapping(NName,NValue,N,isRealVisible);
            if (isVisible(N))  {
                if (!isRealVisible && ns.removeMappingIfRender(NName)) {
                    continue;
                }
                // The xpath select this node output it if needed.
                // Node n = ns.addMappingAndRenderXNodeSet
                //      (NName, NValue, N, isRealVisible);
                Node n = ns.addMappingAndRender(NName, NValue, N);
                if (n != null) {
                    result.add(n);
                    if (C14nHelper.namespaceIsRelative(N)) {
                        Object exArgs[] =
                            { E.getTagName(), NName, N.getNodeValue() };
                        throw new CanonicalizationException(
                            "c14n.Canonicalizer.RelativeNamespace", exArgs);
                    }
                }
            } else {
                if (isRealVisible && NName != XMLNS) {
                    ns.removeMapping(NName);
                } else {
                    ns.addMapping(NName, NValue, N);
                }
            }
        }
        if (isRealVisible) {
            // The element is visible, handle the xmlns definition
            Attr xmlns = E.getAttributeNodeNS(XMLNS_URI, XMLNS);
            Node n = null;
            if (xmlns == null) {
                // No xmlns def just get the already defined.
                n = ns.getMapping(XMLNS);
            } else if (!isVisible(xmlns)) {
                // There is a defn but the xmlns is not selected by the xpath.
                // then xmlns=""
                n = ns.addMappingAndRender(XMLNS, "", nullNode);
            }
            // output the xmlns def if needed.
            if (n != null) {
                result.add(n);
            }
            // Float all xml:* attributes of the unselected parent elements to
            // this one. addXmlAttributes(E,result);
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
    public byte[] engineCanonicalizeXPathNodeSet(Set xpathNodeSet,
        String inclusiveNamespaces) throws CanonicalizationException {
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
    public byte[] engineCanonicalizeSubTree(Node rootNode,
        String inclusiveNamespaces) throws CanonicalizationException {
        throw new CanonicalizationException(
            "c14n.Canonicalizer.UnsupportedOperation");
    }

    void circumventBugIfNeeded(XMLSignatureInput input)
        throws CanonicalizationException, ParserConfigurationException,
        IOException, SAXException {
        if (!input.isNeedsToBeExpanded())
            return;
        Document doc = null;
        if (input.getSubNode() != null) {
            doc = XMLUtils.getOwnerDocument(input.getSubNode());
        } else {
            doc = XMLUtils.getOwnerDocument(input.getNodeSet());
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
            if (Constants.NamespaceSpecNS != N.getNamespaceURI()) {
                // Not a namespace definition, ignore.
                if (XML_LANG_URI == N.getNamespaceURI()) {
                    xmlattrStack.addXmlnsAttr(N);
                }
                continue;
            }

            String NName = N.getLocalName();
            String NValue = N.getNodeValue();
            if (XML.equals(NName)
                && Constants.XML_LANG_SPACE_SpecNS.equals(NValue)) {
                continue;
            }
            ns.addMapping(NName,NValue,N);
        }
    }

    private static String joinURI(String baseURI, String relativeURI)
        throws URISyntaxException {
        String bscheme = null;
        String bauthority = null;
        String bpath = "";
        String bquery = null;
        String bfragment = null; // Is this correct?

        // pre-parse the baseURI
        if (baseURI != null) {
            if (baseURI.endsWith("..")) {
                baseURI = baseURI + "/";
            }
            URI base = new URI(baseURI);
            bscheme = base.getScheme();
            bauthority = base.getAuthority();
            bpath = base.getPath();
            bquery = base.getQuery();
            bfragment = base.getFragment();
        }

        URI r = new URI(relativeURI);
        String rscheme = r.getScheme();
        String rauthority = r.getAuthority();
        String rpath = r.getPath();
        String rquery = r.getQuery();
        String rfragment = null;

        String tscheme, tauthority, tpath, tquery, tfragment;
        if (rscheme != null && rscheme.equals(bscheme)) {
            rscheme = null;
        }
        if (rscheme != null) {
            tscheme = rscheme;
            tauthority = rauthority;
            tpath = removeDotSegments(rpath);
            tquery = rquery;
        } else {
            if (rauthority != null) {
                tauthority = rauthority;
                tpath = removeDotSegments(rpath);
                tquery = rquery;
            } else {
                if (rpath.length() == 0) {
                    tpath = bpath;
                    if (rquery != null) {
                        tquery = rquery;
                    } else {
                        tquery = bquery;
                    }
                } else {
                    if (rpath.startsWith("/")) {
                        tpath = removeDotSegments(rpath);
                    } else {
                        if (bauthority != null && bpath.length() == 0) {
                            tpath = "/" + rpath;
                        } else {
                            int last = bpath.lastIndexOf('/');
                            if (last == -1) {
                                tpath = rpath;
                            } else {
                                tpath = bpath.substring(0, last+1) + rpath;
                            }
                        }
                        tpath = removeDotSegments(tpath);
                    }
                    tquery = rquery;
                }
                tauthority = bauthority;
            }
            tscheme = bscheme;
        }
        tfragment = rfragment;
        return new URI(tscheme, tauthority, tpath, tquery, tfragment).toString();
    }

    private static String removeDotSegments(String path) {

        log.log(java.util.logging.Level.FINE, "STEP   OUTPUT BUFFER\t\tINPUT BUFFER");

        // 1. The input buffer is initialized with the now-appended path
        // components then replace occurrences of "//" in the input buffer
        // with "/" until no more occurrences of "//" are in the input buffer.
        String input = path;
        while (input.indexOf("//") > -1) {
            input = input.replaceAll("//", "/");
        }

        // Initialize the output buffer with the empty string.
        StringBuffer output = new StringBuffer();

        // If the input buffer starts with a root slash "/" then move this
        // character to the output buffer.
        if (input.charAt(0) == '/') {
            output.append("/");
            input = input.substring(1);
        }

        printStep("1 ", output.toString(), input);

        // While the input buffer is not empty, loop as follows
        while (input.length() != 0) {
            // 2A. If the input buffer begins with a prefix of "./",
            // then remove that prefix from the input buffer
            // else if the input buffer begins with a prefix of "../", then
            // if also the output does not contain the root slash "/" only,
            // then move this prefix to the end of the output buffer else
            // remove that prefix
            if (input.startsWith("./")) {
                input = input.substring(2);
                printStep("2A", output.toString(), input);
            } else if (input.startsWith("../")) {
                input = input.substring(3);
                if (!output.toString().equals("/")) {
                    output.append("../");
                }
                printStep("2A", output.toString(), input);
            // 2B. if the input buffer begins with a prefix of "/./" or "/.",
            // where "." is a complete path segment, then replace that prefix
            // with "/" in the input buffer; otherwise,
            } else if (input.startsWith("/./")) {
                input = input.substring(2);
                printStep("2B", output.toString(), input);
            } else if (input.equals("/.")) {
                // FIXME: what is complete path segment?
                input = input.replaceFirst("/.", "/");
                printStep("2B", output.toString(), input);
            // 2C. if the input buffer begins with a prefix of "/../" or "/..",
            // where ".." is a complete path segment, then replace that prefix
            // with "/" in the input buffer and if also the output buffer is
            // empty, last segment in the output buffer equals "../" or "..",
            // where ".." is a complete path segment, then append ".." or "/.."
            // for the latter case respectively to the output buffer else
            // remove the last segment and its preceding "/" (if any) from the
            // output buffer and if hereby the first character in the output
            // buffer was removed and it was not the root slash then delete a
            // leading slash from the input buffer; otherwise,
            } else if (input.startsWith("/../")) {
                input = input.substring(3);
                if (output.length() == 0) {
                    output.append("/");
                } else if (output.toString().endsWith("../")) {
                    output.append("..");
                } else if (output.toString().endsWith("..")) {
                    output.append("/..");
                } else {
                    int index = output.lastIndexOf("/");
                    if (index == -1) {
                        output = new StringBuffer();
                        if (input.charAt(0) == '/') {
                            input = input.substring(1);
                        }
                    } else {
                        output = output.delete(index, output.length());
                    }
                }
                printStep("2C", output.toString(), input);
            } else if (input.equals("/..")) {
                // FIXME: what is complete path segment?
                input = input.replaceFirst("/..", "/");
                if (output.length() == 0) {
                    output.append("/");
                } else if (output.toString().endsWith("../")) {
                    output.append("..");
                } else if (output.toString().endsWith("..")) {
                    output.append("/..");
                } else {
                    int index = output.lastIndexOf("/");
                    if (index == -1) {
                        output = new StringBuffer();
                        if (input.charAt(0) == '/') {
                            input = input.substring(1);
                        }
                    } else {
                        output = output.delete(index, output.length());
                    }
                }
                printStep("2C", output.toString(), input);
            // 2D. if the input buffer consists only of ".", then remove
            // that from the input buffer else if the input buffer consists
            // only of ".." and if the output buffer does not contain only
            // the root slash "/", then move the ".." to the output buffer
            // else delte it.; otherwise,
            } else if (input.equals(".")) {
                input = "";
                printStep("2D", output.toString(), input);
            } else if (input.equals("..")) {
                if (!output.toString().equals("/"))
                    output.append("..");
                input = "";
                printStep("2D", output.toString(), input);
            // 2E. move the first path segment (if any) in the input buffer
            // to the end of the output buffer, including the initial "/"
            // character (if any) and any subsequent characters up to, but not
            // including, the next "/" character or the end of the input buffer.
            } else {
                int end = -1;
                int begin = input.indexOf('/');
                if (begin == 0) {
                    end = input.indexOf('/', 1);
                } else {
                    end = begin;
                    begin = 0;
                }
                String segment;
                if (end == -1) {
                    segment = input.substring(begin);
                    input = "";
                } else {
                    segment = input.substring(begin, end);
                    input = input.substring(end);
                }
                output.append(segment);
                printStep("2E", output.toString(), input);
            }
        }

        // 3. Finally, if the only or last segment of the output buffer is
        // "..", where ".." is a complete path segment not followed by a slash
        // then append a slash "/". The output buffer is returned as the result
        // of remove_dot_segments
        if (output.toString().endsWith("..")) {
            output.append("/");
            printStep("3 ", output.toString(), input);
        }

        return output.toString();
    }

    private static void printStep(String step, String output, String input) {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, " " + step + ":   " + output);
            if (output.length() == 0) {
                log.log(java.util.logging.Level.FINE, "\t\t\t\t" + input);
            } else {
                log.log(java.util.logging.Level.FINE, "\t\t\t" + input);
            }
        }
    }
}
