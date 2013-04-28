/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.internal.xjc.reader.internalizer;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.ParseException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.sun.istack.internal.SAXParseException2;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.tools.internal.xjc.util.DOMUtils;
import com.sun.xml.internal.bind.v2.util.EditDistance;
import com.sun.xml.internal.bind.v2.util.XmlFactory;
import com.sun.xml.internal.xsom.SCD;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

/**
 * Internalizes external binding declarations.
 *
 * <p>
 * The {@link #transform(DOMForest,boolean)} method is the entry point.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
class Internalizer {

    private static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/";

    private static XPathFactory xpf = null;

    private final XPath xpath;

    /**
     * Internalize all &lt;jaxb:bindings> customizations in the given forest.
     *
     * @return
     *      if the SCD support is enabled, the return bindings need to be applied
     *      after schema components are parsed.
     *      If disabled, the returned binding set will be empty.
     *      SCDs are only for XML Schema, and doesn't make any sense for other
     *      schema languages.
     */
    static SCDBasedBindingSet transform( DOMForest forest, boolean enableSCD, boolean disableSecureProcessing ) {
        return new Internalizer(forest, enableSCD, disableSecureProcessing).transform();
    }


    private Internalizer(DOMForest forest, boolean enableSCD, boolean disableSecureProcessing) {
        this.errorHandler = forest.getErrorHandler();
        this.forest = forest;
        this.enableSCD = enableSCD;
        synchronized (this) {
            if (xpf == null) {
                xpf = XmlFactory.createXPathFactory(disableSecureProcessing);
            }
        }
        xpath = xpf.newXPath();
    }

    /**
     * DOMForest object currently being processed.
     */
    private final DOMForest forest;

    /**
     * All errors found during the transformation is sent to this object.
     */
    private ErrorReceiver errorHandler;

    /**
     * If true, the SCD-based target selection is supported.
     */
    private boolean enableSCD;


    private SCDBasedBindingSet transform() {

        // either target nodes are conventional DOM nodes (as per spec),
        Map<Element,List<Node>> targetNodes = new HashMap<Element,List<Node>>();
        // ... or it will be schema components by means of SCD (RI extension)
        SCDBasedBindingSet scd = new SCDBasedBindingSet(forest);

        //
        // identify target nodes for all <jaxb:bindings>
        //
        for (Element jaxbBindings : forest.outerMostBindings) {
            // initially, the inherited context is itself
            buildTargetNodeMap(jaxbBindings, jaxbBindings, null, targetNodes, scd);
        }

        //
        // then move them to their respective positions.
        //
        for (Element jaxbBindings : forest.outerMostBindings) {
            move(jaxbBindings, targetNodes);
        }

        return scd;
    }

    /**
     * Validates attributes of a &lt;jaxb:bindings> element.
     */
    private void validate( Element bindings ) {
        NamedNodeMap atts = bindings.getAttributes();
        for( int i=0; i<atts.getLength(); i++ ) {
            Attr a = (Attr)atts.item(i);
            if( a.getNamespaceURI()!=null )
                continue;   // all foreign namespace OK.
            if( a.getLocalName().equals("node") )
                continue;
            if( a.getLocalName().equals("schemaLocation"))
                continue;
            if( a.getLocalName().equals("scd") )
                continue;

            // enhancements
            if( a.getLocalName().equals("required") ) //
                continue;
            if( a.getLocalName().equals("multiple") ) //
                continue;


            // TODO: flag error for this undefined attribute
        }
    }

    /**
     * Determines the target node of the "bindings" element
     * by using the inherited target node, then put
     * the result into the "result" map and the "scd" map.
     *
     * @param inheritedTarget
     *      The current target node. This always exists, even if
     *      the user starts specifying targets via SCD (in that case
     *      this inherited target is just not going to be used.)
     * @param inheritedSCD
     *      If the ancestor &lt;bindings> node specifies @scd to
     *      specify the target via SCD, then this parameter represents that context.
     */
    private void buildTargetNodeMap( Element bindings, @NotNull Node inheritedTarget,
                                     @Nullable SCDBasedBindingSet.Target inheritedSCD,
                                     Map<Element,List<Node>> result, SCDBasedBindingSet scdResult ) {
        // start by the inherited target
        Node target = inheritedTarget;
        ArrayList<Node> targetMultiple = null;

        validate(bindings); // validate this node

        boolean required = true;
        boolean multiple = false;

        if(bindings.getAttribute("required") != null) {
            String requiredAttr = bindings.getAttribute("required");

            if(requiredAttr.equals("no") || requiredAttr.equals("false") || requiredAttr.equals("0"))
                required = false;
        }

        if(bindings.getAttribute("multiple") != null) {
            String requiredAttr = bindings.getAttribute("multiple");

            if(requiredAttr.equals("yes") || requiredAttr.equals("true") || requiredAttr.equals("1"))
                multiple = true;
        }


        // look for @schemaLocation
        if( bindings.getAttributeNode("schemaLocation")!=null ) {
            String schemaLocation = bindings.getAttribute("schemaLocation");

            // enhancement - schemaLocation="*" = bind to all schemas..
            if(schemaLocation.equals("*")) {
                for(String systemId : forest.listSystemIDs()) {
                    if (result.get(bindings) == null)
                        result.put(bindings, new ArrayList<Node>());
                    result.get(bindings).add(forest.get(systemId).getDocumentElement());

                    Element[] children = DOMUtils.getChildElements(bindings, Const.JAXB_NSURI, "bindings");
                    for (Element value : children)
                        buildTargetNodeMap(value, forest.get(systemId).getDocumentElement(), inheritedSCD, result, scdResult);
                }
                return;
            } else {
                try {
                    // TODO: use the URI class
                    // TODO: honor xml:base
                    URL loc = new URL(
                                new URL(forest.getSystemId(bindings.getOwnerDocument())), schemaLocation
                              );
                    schemaLocation = loc.toExternalForm();
                    target = forest.get(schemaLocation);
                    if ((target == null) && (loc.getProtocol().startsWith("file"))) {
                        File f = new File(loc.getFile());
                        schemaLocation = new File(f.getCanonicalPath()).toURI().toString();
                    }
                } catch( MalformedURLException e ) {
                } catch( IOException e ) {
                    Logger.getLogger(Internalizer.class.getName()).log(Level.FINEST, e.getLocalizedMessage());
                }

                target = forest.get(schemaLocation);
                if(target==null) {
                    reportError( bindings,
                        Messages.format(Messages.ERR_INCORRECT_SCHEMA_REFERENCE,
                            schemaLocation,
                            EditDistance.findNearest(schemaLocation,forest.listSystemIDs())));

                    return; // abort processing this <jaxb:bindings>
                }

                target = ((Document)target).getDocumentElement();
            }
        }

        // look for @node
        if( bindings.getAttributeNode("node")!=null ) {
            String nodeXPath = bindings.getAttribute("node");

            // evaluate this XPath
            NodeList nlst;
            try {
                xpath.setNamespaceContext(new NamespaceContextImpl(bindings));
                nlst = (NodeList)xpath.evaluate(nodeXPath,target,XPathConstants.NODESET);
            } catch (XPathExpressionException e) {
                if(required) {
                    reportError( bindings,
                        Messages.format(Messages.ERR_XPATH_EVAL,e.getMessage()), e );
                    return; // abort processing this <jaxb:bindings>
                } else {
                    return;
                }
            }

            if( nlst.getLength()==0 ) {
                if(required)
                    reportError( bindings,
                        Messages.format(Messages.NO_XPATH_EVAL_TO_NO_TARGET, nodeXPath) );
                return; // abort
            }

            if( nlst.getLength()!=1 ) {
                if(!multiple) {
                    reportError( bindings,
                        Messages.format(Messages.NO_XPATH_EVAL_TOO_MANY_TARGETS, nodeXPath,nlst.getLength()) );

                    return; // abort
                } else {
                    if(targetMultiple == null) targetMultiple = new ArrayList<Node>();
                    for(int i = 0; i < nlst.getLength(); i++) {
                        targetMultiple.add(nlst.item(i));
                    }
                }
            }

            // check
            if(!multiple || nlst.getLength() == 1) {
                Node rnode = nlst.item(0);
                if (!(rnode instanceof Element)) {
                    reportError(bindings,
                            Messages.format(Messages.NO_XPATH_EVAL_TO_NON_ELEMENT, nodeXPath));
                    return; // abort
                }

                if (!forest.logic.checkIfValidTargetNode(forest, bindings, (Element) rnode)) {
                    reportError(bindings,
                            Messages.format(Messages.XPATH_EVAL_TO_NON_SCHEMA_ELEMENT,
                            nodeXPath, rnode.getNodeName()));
                    return; // abort
                }

                target = rnode;
            } else {
                for(Node rnode : targetMultiple) {
                    if (!(rnode instanceof Element)) {
                        reportError(bindings,
                                Messages.format(Messages.NO_XPATH_EVAL_TO_NON_ELEMENT, nodeXPath));
                        return; // abort
                    }

                    if (!forest.logic.checkIfValidTargetNode(forest, bindings, (Element) rnode)) {
                        reportError(bindings,
                                Messages.format(Messages.XPATH_EVAL_TO_NON_SCHEMA_ELEMENT,
                                nodeXPath, rnode.getNodeName()));
                        return; // abort
                    }
                }
            }
        }

        // look for @scd
        if( bindings.getAttributeNode("scd")!=null ) {
            String scdPath = bindings.getAttribute("scd");
            if(!enableSCD) {
                // SCD selector was found, but it's not activated. report an error
                // but recover by handling it anyway. this also avoids repeated error messages.
                reportError(bindings,
                    Messages.format(Messages.SCD_NOT_ENABLED));
                enableSCD = true;
            }

            try {
                inheritedSCD = scdResult.createNewTarget( inheritedSCD, bindings,
                        SCD.create(scdPath, new NamespaceContextImpl(bindings)) );
            } catch (ParseException e) {
                reportError( bindings, Messages.format(Messages.ERR_SCD_EVAL,e.getMessage()),e );
                return; // abort processing this bindings
            }
        }

        // update the result map
        if (inheritedSCD != null) {
            inheritedSCD.addBinidng(bindings);
        } else if (!multiple || targetMultiple == null) {
            if (result.get(bindings) == null)
                result.put(bindings, new ArrayList<Node>());
            result.get(bindings).add(target);
        } else {
            for (Node rnode : targetMultiple) {
                if (result.get(bindings) == null)
                    result.put(bindings, new ArrayList<Node>());

                result.get(bindings).add(rnode);
            }

        }


        // look for child <jaxb:bindings> and process them recursively
        Element[] children = DOMUtils.getChildElements( bindings, Const.JAXB_NSURI, "bindings" );
        for (Element value : children)
            if(!multiple || targetMultiple == null)
                buildTargetNodeMap(value, target, inheritedSCD, result, scdResult);
            else {
                for(Node rnode : targetMultiple) {
                    buildTargetNodeMap(value, rnode, inheritedSCD, result, scdResult);
                }
            }
    }

    /**
     * Moves JAXB customizations under their respective target nodes.
     */
    private void move(Element bindings, Map<Element, List<Node>> targetNodes) {
        List<Node> nodelist = targetNodes.get(bindings);

        if(nodelist == null) {
                return; // abort
        }

        for (Node target : nodelist) {
            if (target == null) // this must be the result of an error on the external binding.
            // recover from the error by ignoring this node
            {
                return;
            }

            for (Element item : DOMUtils.getChildElements(bindings)) {
                String localName = item.getLocalName();

                if ("bindings".equals(localName)) {
                    // process child <jaxb:bindings> recursively
                    move(item, targetNodes);
                } else if ("globalBindings".equals(localName)) {
                        // <jaxb:globalBindings> always go to the root of document.
                    Element root = forest.getOneDocument().getDocumentElement();
                    if (root.getNamespaceURI().equals(WSDL_NS)) {
                        NodeList elements = root.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema");
                        if ((elements == null) || (elements.getLength() < 1)) {
                            reportError(item, Messages.format(Messages.ORPHANED_CUSTOMIZATION, item.getNodeName()));
                            return;
                        } else {
                            moveUnder(item, (Element)elements.item(0));
                        }
                    } else {
                        moveUnder(item, root);
                    }
                } else {
                    if (!(target instanceof Element)) {
                        reportError(item,
                                Messages.format(Messages.CONTEXT_NODE_IS_NOT_ELEMENT));
                        return; // abort
                    }

                    if (!forest.logic.checkIfValidTargetNode(forest, item, (Element) target)) {
                        reportError(item,
                                Messages.format(Messages.ORPHANED_CUSTOMIZATION, item.getNodeName()));
                        return; // abort
                    }

                    // move this node under the target
                    moveUnder(item, (Element) target);
                }
            }
        }
    }

    /**
     * Moves the "decl" node under the "target" node.
     *
     * @param decl
     *      A JAXB customization element (e.g., &lt;jaxb:class>)
     *
     * @param target
     *      XML Schema element under which the declaration should move.
     *      For example, &lt;xs:element>
     */
    private void moveUnder( Element decl, Element target ) {
        Element realTarget = forest.logic.refineTarget(target);

        declExtensionNamespace( decl, target );

        // copy in-scope namespace declarations of the decl node
        // to the decl node itself so that this move won't change
        // the in-scope namespace bindings.
        Element p = decl;
        Set<String> inscopes = new HashSet<String>();
        while(true) {
            NamedNodeMap atts = p.getAttributes();
            for( int i=0; i<atts.getLength(); i++ ) {
                Attr a = (Attr)atts.item(i);
                if( Const.XMLNS_URI.equals(a.getNamespaceURI()) ) {
                    String prefix;
                    if( a.getName().indexOf(':')==-1 )  prefix = "";
                    else                                prefix = a.getLocalName();

                    if( inscopes.add(prefix) && p!=decl ) {
                        // if this is the first time we see this namespace bindings,
                        // copy the declaration.
                        // if p==decl, there's no need to. Note that
                        // we want to add prefix to inscopes even if p==Decl

                        decl.setAttributeNodeNS( (Attr)a.cloneNode(true) );
                    }
                }
            }

            if( p.getParentNode() instanceof Document )
                break;

            p = (Element)p.getParentNode();
        }

        if( !inscopes.contains("") ) {
            // if the default namespace was undeclared in the context of decl,
            // it must be explicitly set to "" since the new environment might
            // have a different default namespace URI.
            decl.setAttributeNS(Const.XMLNS_URI,"xmlns","");
        }


        // finally move the declaration to the target node.
        if( realTarget.getOwnerDocument()!=decl.getOwnerDocument() ) {
            // if they belong to different DOM documents, we need to clone them
            Element original = decl;
            decl = (Element)realTarget.getOwnerDocument().importNode(decl,true);

            // this effectively clones a ndoe,, so we need to copy locators.
            copyLocators( original, decl );
        }

        realTarget.appendChild( decl );
    }

    /**
     * Recursively visits sub-elements and declare all used namespaces.
     * TODO: the fact that we recognize all namespaces in the extension
     * is a bad design.
     */
    private void declExtensionNamespace(Element decl, Element target) {
        // if this comes from external namespaces, add the namespace to
        // @extensionBindingPrefixes.
        if( !Const.JAXB_NSURI.equals(decl.getNamespaceURI()) )
            declareExtensionNamespace( target, decl.getNamespaceURI() );

        NodeList lst = decl.getChildNodes();
        for( int i=0; i<lst.getLength(); i++ ) {
            Node n = lst.item(i);
            if( n instanceof Element )
                declExtensionNamespace( (Element)n, target );
        }
    }


    /** Attribute name. */
    private static final String EXTENSION_PREFIXES = "extensionBindingPrefixes";

    /**
     * Adds the specified namespace URI to the jaxb:extensionBindingPrefixes
     * attribute of the target document.
     */
    private void declareExtensionNamespace( Element target, String nsUri ) {
        // look for the attribute
        Element root = target.getOwnerDocument().getDocumentElement();
        Attr att = root.getAttributeNodeNS(Const.JAXB_NSURI,EXTENSION_PREFIXES);
        if( att==null ) {
            String jaxbPrefix = allocatePrefix(root,Const.JAXB_NSURI);
            // no such attribute. Create one.
            att = target.getOwnerDocument().createAttributeNS(
                Const.JAXB_NSURI,jaxbPrefix+':'+EXTENSION_PREFIXES);
            root.setAttributeNodeNS(att);
        }

        String prefix = allocatePrefix(root,nsUri);
        if( att.getValue().indexOf(prefix)==-1 )
            // avoid redeclaring the same namespace twice.
            att.setValue( att.getValue()+' '+prefix);
    }

    /**
     * Declares a new prefix on the given element and associates it
     * with the specified namespace URI.
     * <p>
     * Note that this method doesn't use the default namespace
     * even if it can.
     */
    private String allocatePrefix( Element e, String nsUri ) {
        // look for existing namespaces.
        NamedNodeMap atts = e.getAttributes();
        for( int i=0; i<atts.getLength(); i++ ) {
            Attr a = (Attr)atts.item(i);
            if( Const.XMLNS_URI.equals(a.getNamespaceURI()) ) {
                if( a.getName().indexOf(':')==-1 )  continue;

                if( a.getValue().equals(nsUri) )
                    return a.getLocalName();    // found one
            }
        }

        // none found. allocate new.
        while(true) {
            String prefix = "p"+(int)(Math.random()*1000000)+'_';
            if(e.getAttributeNodeNS(Const.XMLNS_URI,prefix)!=null)
                continue;   // this prefix is already allocated.

            e.setAttributeNS(Const.XMLNS_URI,"xmlns:"+prefix,nsUri);
            return prefix;
        }
    }


    /**
     * Copies location information attached to the "src" node to the "dst" node.
     */
    private void copyLocators( Element src, Element dst ) {
        forest.locatorTable.storeStartLocation(
            dst, forest.locatorTable.getStartLocation(src) );
        forest.locatorTable.storeEndLocation(
            dst, forest.locatorTable.getEndLocation(src) );

        // recursively process child elements
        Element[] srcChilds = DOMUtils.getChildElements(src);
        Element[] dstChilds = DOMUtils.getChildElements(dst);

        for( int i=0; i<srcChilds.length; i++ )
            copyLocators( srcChilds[i], dstChilds[i] );
    }


    private void reportError( Element errorSource, String formattedMsg ) {
        reportError( errorSource, formattedMsg, null );
    }

    private void reportError( Element errorSource,
        String formattedMsg, Exception nestedException ) {

        SAXParseException e = new SAXParseException2( formattedMsg,
            forest.locatorTable.getStartLocation(errorSource),
            nestedException );
        errorHandler.error(e);
    }
}
