/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.tools.internal.ws.wsdl.parser;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.io.UnsupportedEncodingException;

import javax.xml.namespace.QName;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.tools.internal.xjc.util.DOMUtils;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import com.sun.tools.internal.ws.util.xml.XmlUtil;


/**
 * Internalizes external binding declarations.
 * @author Vivek Pandey
 */
public class Internalizer {
    private Map<String, Document> wsdlDocuments;
    private Map<String, Document> jaxwsBindings;
    private static final XPathFactory xpf = XPathFactory.newInstance();
    private final XPath xpath = xpf.newXPath();
    private final  LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.wsdl");;
    private ProcessorEnvironment env;
    public  void transform(Map<String, Document> jaxwsBindings, Map<String, Document> wsdlDocuments, ProcessorEnvironment env) {
        if(jaxwsBindings == null)
            return;
        this.env = env;
        this.wsdlDocuments = wsdlDocuments;
        this.jaxwsBindings = jaxwsBindings;
        Map targetNodes = new HashMap<Element, Node>();

        // identify target nodes for all <JAXWS:bindings>
        for(Map.Entry<String, Document> jaxwsBinding : jaxwsBindings.entrySet()) {
            Element e = jaxwsBinding.getValue().getDocumentElement();
            // initially, the inherited context is itself
            buildTargetNodeMap( e, e, targetNodes );
        }

        // then move them to their respective positions.
        for(Map.Entry<String, Document> jaxwsBinding : jaxwsBindings.entrySet()) {
            Element e = jaxwsBinding.getValue().getDocumentElement();
            move( e, targetNodes );
        }

    }

    /**
     * Validates attributes of a &lt;JAXWS:bindings> element.
     */
    private void validate( Element bindings ) {
        NamedNodeMap atts = bindings.getAttributes();
        for( int i=0; i<atts.getLength(); i++ ) {
            Attr a = (Attr)atts.item(i);
            if( a.getNamespaceURI()!=null )
                continue;   // all foreign namespace OK.
            if( a.getLocalName().equals("node") )
                continue;
            if( a.getLocalName().equals("wsdlLocation"))
                continue;

            // TODO: flag error for this undefined attribute
        }
    }

    /**
     * Gets the DOM tree associated with the specified system ID,
     * or null if none is found.
     */
    public Document get( String systemId ) {
        Document doc = wsdlDocuments.get(systemId);

        if( doc==null && systemId.startsWith("file:/") && !systemId.startsWith("file://") ) {
            // As of JDK1.4, java.net.URL.toExternal method returns URLs like
            // "file:/abc/def/ghi" which is an incorrect file protocol URL according to RFC1738.
            // Some other correctly functioning parts return the correct URLs ("file:///abc/def/ghi"),
            // and this descripancy breaks DOM look up by system ID.

            // this extra check solves this problem.
            doc = wsdlDocuments.get( "file://"+systemId.substring(5) );
        }

        if( doc==null && systemId.startsWith("file:") ) {
            // on Windows, filenames are case insensitive.
            // perform case-insensitive search for improved user experience
            String systemPath = getPath(systemId);
            for (String key : wsdlDocuments.keySet()) {
                if(key.startsWith("file:") && getPath(key).equalsIgnoreCase(systemPath)) {
                    doc = wsdlDocuments.get(key);
                    break;
                }
            }
        }
        return doc;
    }

    /**
     * Strips off the leading 'file:///' portion from an URL.
     */
    private String getPath(String key) {
        key = key.substring(5); // skip 'file:'
        while(key.length()>0 && key.charAt(0)=='/')
            key = key.substring(1);
        return key;
    }

    /**
     * Determines the target node of the "bindings" element
     * by using the inherited target node, then put
     * the result into the "result" map.
     */
    private void buildTargetNodeMap( Element bindings, Node inheritedTarget, Map<Element, Node> result ) {
        // start by the inherited target
        Node target = inheritedTarget;

        validate(bindings); // validate this node

        // look for @wsdlLocation
        if( bindings.getAttributeNode("wsdlLocation")!=null ) {
            String wsdlLocation = bindings.getAttribute("wsdlLocation");

            try {
                // absolutize this URI.
                // TODO: use the URI class
                // TODO: honor xml:base
                wsdlLocation = new URL(new URL(getSystemId(bindings.getOwnerDocument())),
                        wsdlLocation ).toExternalForm();
            } catch( MalformedURLException e ) {
                wsdlLocation = JAXWSUtils.absolutize(JAXWSUtils.getFileOrURLName(wsdlLocation));
            }

            target = get(wsdlLocation);
            if(target==null) {
                error("internalizer.targetNotFound", new Object[]{wsdlLocation});
                return; // abort processing this <JAXWS:bindings>
            }
        }

        boolean hasNode = true;
        if(isJAXWSBindings(bindings) && bindings.getAttributeNode("node")!=null ) {
            target = evaluateXPathNode(target, bindings.getAttribute("node"), new NamespaceContextImpl(bindings));
        }else if(isJAXWSBindings(bindings) && (bindings.getAttributeNode("node")==null) && !isTopLevelBinding(bindings)) {
            hasNode = false;
        }else if(isGlobalBinding(bindings) && !isWSDLDefinition(target) && isTopLevelBinding(bindings.getParentNode())){
            target = getWSDLDefintionNode(target);
        }

        //if target is null it means the xpath evaluation has some problem,
        // just return
        if(target == null)
            return;

        // update the result map
        if(hasNode)
            result.put( bindings, target );

        // look for child <JAXWS:bindings> and process them recursively
        Element[] children = getChildElements( bindings, JAXWSBindingsConstants.NS_JAXWS_BINDINGS);
        for( int i=0; i<children.length; i++ )
            buildTargetNodeMap( children[i], target, result );
    }

    private Node getWSDLDefintionNode(Node target){
        return evaluateXPathNode(target, "wsdl:definitions",
            new javax.xml.namespace.NamespaceContext(){
                public String getNamespaceURI(String prefix){
                    return "http://schemas.xmlsoap.org/wsdl/";
                }
                public String getPrefix(String nsURI){
                    throw new UnsupportedOperationException();
                }
                public Iterator getPrefixes(String namespaceURI) {
                    throw new UnsupportedOperationException();
                }});
    }

    private boolean isWSDLDefinition(Node target){
        if(target == null)
            return false;
        String localName = target.getLocalName();
        String nsURI = target.getNamespaceURI();
        if(((localName != null) && localName.equals("definitions")) &&
            (nsURI != null && nsURI.equals("http://schemas.xmlsoap.org/wsdl/")))
            return true;
        return false;

    }

    private boolean isTopLevelBinding(Node node){
        if(node instanceof Document)
            node = ((Document)node).getDocumentElement();
        return ((node != null) && (((Element)node).getAttributeNode("wsdlLocation") != null));
    }

    private boolean isJAXWSBindings(Node bindings){
        return (bindings.getNamespaceURI().equals(JAXWSBindingsConstants.NS_JAXWS_BINDINGS) && bindings.getLocalName().equals("bindings"));
    }

    private boolean isGlobalBinding(Node bindings){
        if((bindings.getNamespaceURI() == null)){
            warn("invalid.customization.namespace", new Object[]{bindings.getLocalName()});
            return false;
        }
        return  (bindings.getNamespaceURI().equals(JAXWSBindingsConstants.NS_JAXWS_BINDINGS) &&
                (bindings.getLocalName().equals("package") ||
                bindings.getLocalName().equals("enableAsyncMapping") ||
                bindings.getLocalName().equals("enableAdditionalSOAPHeaderMapping") ||
                bindings.getLocalName().equals("enableWrapperStyle") ||
                bindings.getLocalName().equals("enableMIMEContent")));
    }

    private static Element[] getChildElements(Element parent, String nsUri) {
        ArrayList a = new ArrayList();
        NodeList children = parent.getChildNodes();
        for( int i=0; i<children.getLength(); i++ ) {
            Node item = children.item(i);
            if(!(item instanceof Element ))     continue;

            if(nsUri.equals(item.getNamespaceURI()))
                a.add(item);
        }
        return (Element[]) a.toArray(new Element[a.size()]);
    }

    private Node evaluateXPathNode(Node target, String expression, NamespaceContext namespaceContext) {
        NodeList nlst;
        try {
            xpath.setNamespaceContext(namespaceContext);
            nlst = (NodeList)xpath.evaluate(expression, target, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            error("internalizer.XPathEvaluationError", new Object[]{e.getMessage()});
            if(env.verbose())
                e.printStackTrace();
            return null; // abort processing this <jaxb:bindings>
        }

        if( nlst.getLength()==0 ) {
            error("internalizer.XPathEvaluatesToNoTarget", new Object[]{expression});
            return null; // abort
        }

        if( nlst.getLength()!=1 ) {
            error("internalizer.XPathEvaulatesToTooManyTargets", new Object[]{expression, nlst.getLength()});
            return null; // abort
        }

        Node rnode = nlst.item(0);
        if(!(rnode instanceof Element )) {
            error("internalizer.XPathEvaluatesToNonElement", new Object[]{expression});
            return null; // abort
        }
        return (Element)rnode;
    }

    /**
     * Moves JAXWS customizations under their respective target nodes.
     */
    private void move( Element bindings, Map<Element, Node> targetNodes ) {
        Node target = targetNodes.get(bindings);
        if(target==null)
            // this must be the result of an error on the external binding.
            // recover from the error by ignoring this node
            return;

        Element[] children = DOMUtils.getChildElements(bindings);

        for (Element item : children) {
            if ("bindings".equals(item.getLocalName())){
            // process child <jaxws:bindings> recursively
                move(item, targetNodes);
            }else if(isGlobalBinding(item)){
                target = targetNodes.get(item);
                moveUnder(item,(Element)target);
            }else {
                if (!(target instanceof Element)) {
                    return; // abort
                }
                // move this node under the target
                moveUnder(item,(Element)target);
            }
        }
    }

    private boolean isJAXBBindingElement(Element e){
        if((e.getNamespaceURI() != null ) && e.getNamespaceURI().equals(JAXWSBindingsConstants.NS_JAXB_BINDINGS))
            return true;
        return false;
    }

    private boolean isJAXWSBindingElement(Element e){
        if((e.getNamespaceURI() != null ) && e.getNamespaceURI().equals(JAXWSBindingsConstants.NS_JAXWS_BINDINGS))
            return true;
        return false;
    }

    /**
     * Moves the "decl" node under the "target" node.
     *
     * @param decl
     *      A JAXWS customization element (e.g., &lt;JAXWS:class>)
     *
     * @param target
     *      XML wsdl element under which the declaration should move.
     *      For example, &lt;xs:element>
     */
    private void moveUnder( Element decl, Element target ) {

        //if there is @node on decl and has a child element jaxb:bindings, move it under the target
        //Element jaxb = getJAXBBindingElement(decl);
        if(isJAXBBindingElement(decl)){
            //add jaxb namespace declaration
            if(!target.hasAttributeNS(Constants.NS_XMLNS, "jaxb")){
                target.setAttributeNS(Constants.NS_XMLNS, "xmlns:jaxb", JAXWSBindingsConstants.NS_JAXB_BINDINGS);
            }

            //add jaxb:bindings version info. Lets put it to 1.0, may need to change latter
            if(!target.hasAttributeNS(JAXWSBindingsConstants.NS_JAXB_BINDINGS, "version")){
                target.setAttributeNS(JAXWSBindingsConstants.NS_JAXB_BINDINGS, "jaxb:version", JAXWSBindingsConstants.JAXB_BINDING_VERSION);
            }

            //insert xs:annotation/xs:appinfo where in jaxb:binding will be put
            target = refineSchemaTarget(target);
            copyInscopeNSAttributes(decl);
        }else if(isJAXWSBindingElement(decl)){
            //add jaxb namespace declaration
            if(!target.hasAttributeNS(Constants.NS_XMLNS, "JAXWS")){
                target.setAttributeNS(Constants.NS_XMLNS, "xmlns:JAXWS", JAXWSBindingsConstants.NS_JAXWS_BINDINGS);
            }

            //insert xs:annotation/xs:appinfo where in jaxb:binding will be put
            target = refineWSDLTarget(target);
            copyInscopeNSAttributes(decl);
        }else{
            return;
        }

        // finally move the declaration to the target node.
        if( target.getOwnerDocument()!=decl.getOwnerDocument() ) {
            // if they belong to different DOM documents, we need to clone them
            Element original = decl;
            decl = (Element)target.getOwnerDocument().importNode(decl,true);

        }

        target.appendChild( decl );
    }

    /**
     *  Copy in-scope namespace declarations of the decl node
     *  to the decl node itself so that this move won't change
     *  the in-scope namespace bindings.
     */
    private void copyInscopeNSAttributes(Element e){
        Element p = e;
        Set inscopes = new HashSet();
        while(true) {
            NamedNodeMap atts = p.getAttributes();
            for( int i=0; i<atts.getLength(); i++ ) {
                Attr a = (Attr)atts.item(i);
                if( Constants.NS_XMLNS.equals(a.getNamespaceURI()) ) {
                    String prefix;
                    if( a.getName().indexOf(':')==-1 )  prefix = "";
                    else                                prefix = a.getLocalName();

                    if( inscopes.add(prefix) && p!=e ) {
                        // if this is the first time we see this namespace bindings,
                        // copy the declaration.
                        // if p==decl, there's no need to. Note that
                        // we want to add prefix to inscopes even if p==Decl

                        e.setAttributeNodeNS( (Attr)a.cloneNode(true) );
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
            e.setAttributeNS(Constants.NS_XMLNS,"xmlns","");
        }
    }

    public Element refineSchemaTarget(Element target) {
        // look for existing xs:annotation
        Element annotation = DOMUtils.getFirstChildElement(target, Constants.NS_XSD, "annotation");
        if(annotation==null)
            // none exists. need to make one
            annotation = insertXMLSchemaElement( target, "annotation" );

        // then look for appinfo
        Element appinfo = DOMUtils.getFirstChildElement(annotation, Constants.NS_XSD, "appinfo" );
        if(appinfo==null)
            // none exists. need to make one
            appinfo = insertXMLSchemaElement( annotation, "appinfo" );

        return appinfo;
    }

    public Element refineWSDLTarget(Element target) {
        // look for existing xs:annotation
        Element JAXWSBindings = DOMUtils.getFirstChildElement(target, JAXWSBindingsConstants.NS_JAXWS_BINDINGS, "bindings");
        if(JAXWSBindings==null)
            // none exists. need to make one
            JAXWSBindings = insertJAXWSBindingsElement(target, "bindings" );
        return JAXWSBindings;
    }

    /**
     * Creates a new XML Schema element of the given local name
     * and insert it as the first child of the given parent node.
     *
     * @return
     *      Newly create element.
     */
    private Element insertXMLSchemaElement( Element parent, String localName ) {
        // use the same prefix as the parent node to avoid modifying
        // the namespace binding.
        String qname = parent.getTagName();
        int idx = qname.indexOf(':');
        if(idx==-1)     qname = localName;
        else            qname = qname.substring(0,idx+1)+localName;

        Element child = parent.getOwnerDocument().createElementNS( Constants.NS_XSD, qname );

        NodeList children = parent.getChildNodes();

        if( children.getLength()==0 )
            parent.appendChild(child);
        else
            parent.insertBefore( child, children.item(0) );

        return child;
    }

    private Element insertJAXWSBindingsElement( Element parent, String localName ) {
        String qname = "JAXWS:"+localName;

        Element child = parent.getOwnerDocument().createElementNS(JAXWSBindingsConstants.NS_JAXWS_BINDINGS, qname );

        NodeList children = parent.getChildNodes();

        if( children.getLength()==0 )
            parent.appendChild(child);
        else
            parent.insertBefore( child, children.item(0) );

        return child;
    }

    private String getSystemId(Document doc){
        for(Map.Entry<String, Document> e:jaxwsBindings.entrySet()){
            if (e.getValue() == doc)
                return e.getKey();
        }
        return null;
    }

    protected void warn(Localizable msg) {
        env.warn(msg);
    }


    protected void error(String key, Object[] args) {
        env.error(messageFactory.getMessage(key, args));
    }

    protected void warn(String key) {
        env.warn(messageFactory.getMessage(key));
    }

    protected void warn(String key, Object[] args) {
        env.warn(messageFactory.getMessage(key, args));
    }

    protected void info(String key) {
        env.info(messageFactory.getMessage(key));
    }

    protected void info(String key, String arg) {
        env.info(messageFactory.getMessage(key, arg));
    }

}
