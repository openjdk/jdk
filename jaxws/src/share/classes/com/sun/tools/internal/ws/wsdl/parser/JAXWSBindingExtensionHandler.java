/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext;
import com.sun.tools.internal.ws.util.xml.XmlUtil;
import com.sun.tools.internal.ws.wsdl.document.*;
import com.sun.tools.internal.ws.wsdl.document.jaxws.CustomName;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBinding;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import com.sun.tools.internal.ws.wsdl.document.jaxws.Parameter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.Iterator;
import java.util.Map;


/**
 * @author Vivek Pandey
 *
 * jaxws:bindings exension handler.
 *
 */
public class JAXWSBindingExtensionHandler extends AbstractExtensionHandler {

    private static final XPathFactory xpf = XPathFactory.newInstance();
    private final XPath xpath = xpf.newXPath();

    public JAXWSBindingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        super(extensionHandlerMap);
    }

    /* (non-Javadoc)
     * @see AbstractExtensionHandler#getNamespaceURI()
     */
    public String getNamespaceURI() {
        return JAXWSBindingsConstants.NS_JAXWS_BINDINGS;
    }

    /**
     * @param context
     * @param parent
     * @param e
     */
    private boolean parseGlobalJAXWSBindings(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        context.push();
        context.registerNamespaces(e);

        JAXWSBinding jaxwsBinding =  getJAXWSExtension(parent);
        if(jaxwsBinding == null)
            jaxwsBinding = new JAXWSBinding(context.getLocation(e));
        String attr = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.WSDL_LOCATION_ATTR);
        if (attr != null) {
            jaxwsBinding.setWsdlLocation(attr);
        }

        attr = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.NODE_ATTR);
        if (attr != null) {
            jaxwsBinding.setNode(attr);
        }

        attr = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.VERSION_ATTR);
        if (attr != null) {
            jaxwsBinding.setVersion(attr);
        }

        for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.PACKAGE)){
                parsePackage(context, jaxwsBinding, e2);
                if((jaxwsBinding.getJaxwsPackage() != null) && (jaxwsBinding.getJaxwsPackage().getJavaDoc() != null)){
                    ((Definitions)parent).setDocumentation(new Documentation(jaxwsBinding.getJaxwsPackage().getJavaDoc()));
                }
            }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_WRAPPER_STYLE)){
                parseWrapperStyle(context, jaxwsBinding, e2);
            }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_ASYNC_MAPPING)){
                parseAsynMapping(context, jaxwsBinding, e2);
            }
//            else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_ADDITIONAL_SOAPHEADER_MAPPING)){
//                parseAdditionalSOAPHeaderMapping(context, jaxwsBinding, e2);
//            }
            else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_MIME_CONTENT)){
                parseMimeContent(context, jaxwsBinding, e2);
            }else{
                Util.fail(
                    "parsing.invalidExtensionElement",
                    e2.getTagName(),
                    e2.getNamespaceURI());
                return false;
            }
        }
        parent.addExtension(jaxwsBinding);
        context.pop();
//        context.fireDoneParsingEntity(
//                JAXWSBindingsConstants.JAXWS_BINDINGS,
//                jaxwsBinding);
        return true;
    }

    private static JAXWSBinding getJAXWSExtension(TWSDLExtensible extensible) {
        for (TWSDLExtension extension:extensible.extensions()) {
            if (extension.getClass().equals(JAXWSBinding.class)) {
                return (JAXWSBinding)extension;
            }
        }

        return null;
    }

    /**
     * @param context
     * @param parent
     * @param e
     */
    private void parseProvider(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding parent, Element e) {
        String val = e.getTextContent();
        if(val == null)
            return;
        if(val.equals("false") || val.equals("0")){
            ((JAXWSBinding)parent).setProvider(Boolean.FALSE);
        }else if(val.equals("true") || val.equals("1")){
            ((JAXWSBinding)parent).setProvider(Boolean.TRUE);
        }

    }

    /**
     *
     * @param context
     * @param parent
     * @param e
     */
    private void parseJAXBBindings(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        JAXWSBinding binding = (JAXWSBinding)parent;
        binding.addJaxbBindings(e);
    }

    /**
     * @param context
     * @param parent
     * @param e
     */
    private void parsePackage(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding parent, Element e) {
        //System.out.println("In handlePackageExtension: " + e.getNodeName());
        String packageName = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.NAME_ATTR);
        JAXWSBinding binding = (JAXWSBinding)parent;
        binding.setJaxwsPackage(new CustomName(packageName, getJavaDoc(e)));
    }

    /**
     * @param context
     * @param parent
     * @param e
     */
    private void parseWrapperStyle(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding parent, Element e) {
        //System.out.println("In handleWrapperStyleExtension: " + e.getNodeName());
        String val = e.getTextContent();
        if(val == null)
            return;
        if(val.equals("false") || val.equals("0")){
            ((JAXWSBinding)parent).setEnableWrapperStyle(Boolean.FALSE);
        }else if(val.equals("true") || val.equals("1")){
            ((JAXWSBinding)parent).setEnableWrapperStyle(Boolean.TRUE);
        }
    }

    /**
     * @param context
     * @param parent
     * @param e
     */
//    private void parseAdditionalSOAPHeaderMapping(TWSDLParserContextImpl context, TWSDLExtensible parent, Element e) {
//        //System.out.println("In handleAdditionalSOAPHeaderExtension: " + e.getNodeName());
//        String val = e.getTextContent();
//        if(val == null)
//            return;
//        if(val.equals("false") || val.equals("0")){
//            ((JAXWSBinding)parent).setEnableAdditionalHeaderMapping(Boolean.FALSE);
//        }else if(val.equals("true") || val.equals("1")){
//            ((JAXWSBinding)parent).setEnableAdditionalHeaderMapping(Boolean.TRUE);
//        }
//    }

    /**
     * @param context
     * @param parent
     * @param e
     */
    private void parseAsynMapping(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding parent, Element e) {
        //System.out.println("In handleAsynMappingExtension: " + e.getNodeName());
        String val = e.getTextContent();
        if(val == null)
            return;
        if(val.equals("false") || val.equals("0")){
            ((JAXWSBinding)parent).setEnableAsyncMapping(Boolean.FALSE);
        }else if(val.equals("true") || val.equals("1")){
            ((JAXWSBinding)parent).setEnableAsyncMapping(Boolean.TRUE);
        }
    }

    /**
     * @param context
     * @param parent
     * @param e
     */
    private void parseMimeContent(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding parent, Element e) {
        //System.out.println("In handleMimeContentExtension: " + e.getNodeName());
        String val = e.getTextContent();
        if(val == null)
            return;
        if(val.equals("false") || val.equals("0")){
            ((JAXWSBinding)parent).setEnableMimeContentMapping(Boolean.FALSE);
        }else if(val.equals("true") || val.equals("1")){
            ((JAXWSBinding)parent).setEnableMimeContentMapping(Boolean.TRUE);
        }
    }

    /**
     * @param context
     * @param jaxwsBinding
     * @param e
     */
    private void parseMethod(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding jaxwsBinding, Element e) {
        String methodName = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.NAME_ATTR);
        String javaDoc = getJavaDoc(e);
        CustomName name = new CustomName(methodName, javaDoc);
        jaxwsBinding.setMethodName(name);
    }

    /**
     * @param context
     * @param jaxwsBinding
     * @param e
     */
    private void parseParameter(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding jaxwsBinding, Element e) {
        String part = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.PART_ATTR);
        Element msgPartElm = evaluateXPathNode(e.getOwnerDocument(), part, new NamespaceContextImpl(e));
        Node msgElm = msgPartElm.getParentNode();
        //MessagePart msgPart = new MessagePart();

        String partName = XmlUtil.getAttributeOrNull(msgPartElm, "name");
        String msgName = XmlUtil.getAttributeOrNull((Element)msgElm, "name");
        if((partName == null) || (msgName == null))
            return;

        String val = XmlUtil.getAttributeOrNull(msgPartElm, "element");

        String element = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.ELEMENT_ATTR);
        String name = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.NAME_ATTR);

        QName elementName = null;
        if(element != null){
            String uri = e.lookupNamespaceURI(XmlUtil.getPrefix(element));
            elementName = (uri == null)?null:new QName(uri, XmlUtil.getLocalPart(element));
        }

        jaxwsBinding.addParameter(new Parameter(msgName, partName, elementName, name));
    }

    private Element evaluateXPathNode(Node target, String expression, NamespaceContext namespaceContext) {
        NodeList nlst;
        try {
            xpath.setNamespaceContext(namespaceContext);
            nlst = (NodeList)xpath.evaluate(expression, target, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            Util.fail("internalizer.XPathEvaluationError", e.getMessage());
            return null; // abort processing this <jaxb:bindings>
        }

        if( nlst.getLength()==0 ) {
            Util.fail("internalizer.XPathEvaluatesToNoTarget", new Object[]{expression});
            return null; // abort
        }

        if( nlst.getLength()!=1 ) {
            Util.fail("internalizer.XPathEvaulatesToTooManyTargets", new Object[]{expression, nlst.getLength()});
            return null; // abort
        }

        Node rnode = nlst.item(0);
        if(!(rnode instanceof Element )) {
            Util.fail("internalizer.XPathEvaluatesToNonElement", new Object[]{expression});
            return null; // abort
        }
        return (Element)rnode;
    }

    /**
     * @param context
     * @param jaxwsBinding
     * @param e
     */
    private void parseClass(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding jaxwsBinding, Element e) {
        String className = XmlUtil.getAttributeOrNull(e, JAXWSBindingsConstants.NAME_ATTR);
        String javaDoc = getJavaDoc(e);
        jaxwsBinding.setClassName(new CustomName(className, javaDoc));
    }


    /**
     * @param context
     * @param jaxwsBinding
     * @param e
     */
    private void parseException(com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context, JAXWSBinding jaxwsBinding, Element e) {
        for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;
            if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.CLASS)){
                String className = XmlUtil.getAttributeOrNull(e2, JAXWSBindingsConstants.NAME_ATTR);
                String javaDoc = getJavaDoc(e2);
                jaxwsBinding.setException(new com.sun.tools.internal.ws.wsdl.document.jaxws.Exception(new CustomName(className, javaDoc)));
            }
        }
    }

    public boolean handleDefinitionsExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        return parseGlobalJAXWSBindings(context, parent, e);
    }

    public boolean handlePortTypeExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if(XmlUtil.matchesTagNS(e, JAXWSBindingsConstants.JAXWS_BINDINGS)){
            context.push();
            context.registerNamespaces(e);
            JAXWSBinding jaxwsBinding = new JAXWSBinding(context.getLocation(e));

            for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;

                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_WRAPPER_STYLE)){
                    parseWrapperStyle(context, jaxwsBinding, e2);
                }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_ASYNC_MAPPING)){
                    parseAsynMapping(context, jaxwsBinding, e2);
                }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.CLASS)){
                    parseClass(context, jaxwsBinding, e2);
                    if((jaxwsBinding.getClassName() != null) && (jaxwsBinding.getClassName().getJavaDoc() != null)){
                        ((PortType)parent).setDocumentation(new Documentation(jaxwsBinding.getClassName().getJavaDoc()));
                    }
                }else{
                    Util.fail(
                        "parsing.invalidExtensionElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                    return false;
                }
            }
            parent.addExtension(jaxwsBinding);
            context.pop();
//            context.fireDoneParsingEntity(
//                    JAXWSBindingsConstants.JAXWS_BINDINGS,
//                    jaxwsBinding);
            return true;
        }else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    public boolean handleOperationExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if(XmlUtil.matchesTagNS(e, JAXWSBindingsConstants.JAXWS_BINDINGS)){
            if(parent instanceof Operation){
                return handlePortTypeOperation(context, (Operation)parent, e);
            }else if(parent instanceof BindingOperation){
                return handleBindingOperation(context, (BindingOperation)parent, e);
            }
        }else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
        return false;
    }

    private boolean handleBindingOperation(TWSDLParserContext context, BindingOperation operation, Element e) {
        if(XmlUtil.matchesTagNS(e, JAXWSBindingsConstants.JAXWS_BINDINGS)){
            context.push();
            context.registerNamespaces(e);
            JAXWSBinding jaxwsBinding = new JAXWSBinding(context.getLocation(e));

            for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;

//                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_ADDITIONAL_SOAPHEADER_MAPPING)){
//                    parseAdditionalSOAPHeaderMapping(context, jaxwsBinding, e2);
//                }else
                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_MIME_CONTENT)){
                    parseMimeContent(context, jaxwsBinding, e2);
                }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.PARAMETER)){
                    parseParameter(context, jaxwsBinding, e2);
                }else{
                    Util.fail(
                        "parsing.invalidExtensionElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                    return false;
                }
            }
            operation.addExtension(jaxwsBinding);
            context.pop();
//            context.fireDoneParsingEntity(
//                    JAXWSBindingsConstants.JAXWS_BINDINGS,
//                    jaxwsBinding);
            return true;
        }else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    private boolean handlePortTypeOperation(TWSDLParserContext context, Operation parent, Element e) {
        context.push();
        context.registerNamespaces(e);
        JAXWSBinding jaxwsBinding = new JAXWSBinding(context.getLocation(e));

        for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_WRAPPER_STYLE)){
                parseWrapperStyle(context, jaxwsBinding, e2);
            }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_ASYNC_MAPPING)){
                parseAsynMapping(context, jaxwsBinding, e2);
            }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.METHOD)){
                parseMethod(context, jaxwsBinding, e2);
                if((jaxwsBinding.getMethodName() != null) && (jaxwsBinding.getMethodName().getJavaDoc() != null)){
                    parent.setDocumentation(new Documentation(jaxwsBinding.getMethodName().getJavaDoc()));
                }
            }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.PARAMETER)){
                parseParameter(context, jaxwsBinding, e2);
            }else{
                Util.fail(
                    "parsing.invalidExtensionElement",
                    e2.getTagName(),
                    e2.getNamespaceURI());
                return false;
            }
        }
        parent.addExtension(jaxwsBinding);
        context.pop();
//        context.fireDoneParsingEntity(
//                JAXWSBindingsConstants.JAXWS_BINDINGS,
//                jaxwsBinding);
        return true;
    }

    public boolean handleBindingExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if(XmlUtil.matchesTagNS(e, JAXWSBindingsConstants.JAXWS_BINDINGS)){
            context.push();
            context.registerNamespaces(e);
            JAXWSBinding jaxwsBinding = new JAXWSBinding(context.getLocation(e));

            for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;

//                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_ADDITIONAL_SOAPHEADER_MAPPING)){
//                    parseAdditionalSOAPHeaderMapping(context, jaxwsBinding, e2);
//                }else
                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.ENABLE_MIME_CONTENT)){
                    parseMimeContent(context, jaxwsBinding, e2);
                }else{
                    Util.fail(
                        "parsing.invalidExtensionElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                    return false;
                }
            }
            parent.addExtension(jaxwsBinding);
            context.pop();
//            context.fireDoneParsingEntity(
//                    JAXWSBindingsConstants.JAXWS_BINDINGS,
//                    jaxwsBinding);
            return true;
        }else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    /* (non-Javadoc)
     * @see ExtensionHandlerBase#handleFaultExtension(TWSDLParserContextImpl, TWSDLExtensible, org.w3c.dom.Element)
     */
    public boolean handleFaultExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if(XmlUtil.matchesTagNS(e, JAXWSBindingsConstants.JAXWS_BINDINGS)){
            context.push();
            context.registerNamespaces(e);
            JAXWSBinding jaxwsBinding = new JAXWSBinding(context.getLocation(e));

            for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;
                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.CLASS)){
                    parseClass(context, jaxwsBinding, e2);
                    if((jaxwsBinding.getClassName() != null) && (jaxwsBinding.getClassName().getJavaDoc() != null)){
                        ((Fault)parent).setDocumentation(new Documentation(jaxwsBinding.getClassName().getJavaDoc()));
                    }
                }else{
                    Util.fail(
                        "parsing.invalidExtensionElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                    return false;
                }
            }
            parent.addExtension(jaxwsBinding);
            context.pop();
//            context.fireDoneParsingEntity(
//                    JAXWSBindingsConstants.JAXWS_BINDINGS,
//                    jaxwsBinding);
            return true;
        }else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    public boolean handleServiceExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if(XmlUtil.matchesTagNS(e, JAXWSBindingsConstants.JAXWS_BINDINGS)){
            context.push();
            context.registerNamespaces(e);
            JAXWSBinding jaxwsBinding = new JAXWSBinding(context.getLocation(e));

            for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;
                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.CLASS)){
                    parseClass(context, jaxwsBinding, e2);
                    if((jaxwsBinding.getClassName() != null) && (jaxwsBinding.getClassName().getJavaDoc() != null)){
                        ((Service)parent).setDocumentation(new Documentation(jaxwsBinding.getClassName().getJavaDoc()));
                    }
                }else{
                    Util.fail(
                        "parsing.invalidExtensionElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                    return false;
                }
            }
            parent.addExtension(jaxwsBinding);
            context.pop();
//            context.fireDoneParsingEntity(
//                    JAXWSBindingsConstants.JAXWS_BINDINGS,
//                    jaxwsBinding);
            return true;
        }else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    public boolean handlePortExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if(XmlUtil.matchesTagNS(e, JAXWSBindingsConstants.JAXWS_BINDINGS)){
            context.push();
            context.registerNamespaces(e);
            JAXWSBinding jaxwsBinding = new JAXWSBinding(context.getLocation(e));

            for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;

                if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.PROVIDER)){
                    parseProvider(context, jaxwsBinding, e2);
                }else if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.METHOD)){
                    parseMethod(context, jaxwsBinding, e2);
                    if((jaxwsBinding.getMethodName() != null) && (jaxwsBinding.getMethodName().getJavaDoc() != null)){
                        ((Port)parent).setDocumentation(new Documentation(jaxwsBinding.getMethodName().getJavaDoc()));
                    }
                }else{
                    Util.fail(
                        "parsing.invalidExtensionElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                    return false;
                }
            }
            parent.addExtension(jaxwsBinding);
            context.pop();
//            context.fireDoneParsingEntity(
//                    JAXWSBindingsConstants.JAXWS_BINDINGS,
//                    jaxwsBinding);
            return true;
        }else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    private String getJavaDoc(Element e){
        for(Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();){
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;
            if(XmlUtil.matchesTagNS(e2, JAXWSBindingsConstants.JAVADOC)){
                return XmlUtil.getTextForNode(e2);
            }
        }
        return null;
    }
}
