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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import com.sun.tools.internal.ws.wsdl.document.Binding;
import com.sun.tools.internal.ws.wsdl.document.BindingFault;
import com.sun.tools.internal.ws.wsdl.document.BindingInput;
import com.sun.tools.internal.ws.wsdl.document.BindingOperation;
import com.sun.tools.internal.ws.wsdl.document.BindingOutput;
import com.sun.tools.internal.ws.wsdl.document.Definitions;
import com.sun.tools.internal.ws.wsdl.document.Documentation;
import com.sun.tools.internal.ws.wsdl.document.Fault;
import com.sun.tools.internal.ws.wsdl.document.Import;
import com.sun.tools.internal.ws.wsdl.document.Input;
import com.sun.tools.internal.ws.wsdl.document.Message;
import com.sun.tools.internal.ws.wsdl.document.MessagePart;
import com.sun.tools.internal.ws.wsdl.document.Operation;
import com.sun.tools.internal.ws.wsdl.document.Output;
import com.sun.tools.internal.ws.wsdl.document.Port;
import com.sun.tools.internal.ws.wsdl.document.PortType;
import com.sun.tools.internal.ws.wsdl.document.Service;
import com.sun.tools.internal.ws.wsdl.document.Types;
import com.sun.tools.internal.ws.wsdl.document.WSDLConstants;
import com.sun.tools.internal.ws.wsdl.document.WSDLDocument;
import com.sun.tools.internal.ws.wsdl.document.WSDLDocumentVisitor;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import com.sun.tools.internal.ws.wsdl.framework.Extension;
import com.sun.tools.internal.ws.wsdl.framework.Kind;
import com.sun.tools.internal.ws.wsdl.framework.WriterContext;

/**
 * A writer for WSDL documents.
 *
 * @author WS Development Team
 */
public class WSDLWriter {

    public WSDLWriter() throws IOException {
        _extensionHandlers = new HashMap();

        // register handlers for default extensions
        register(new SOAPExtensionHandler());
        register(new HTTPExtensionHandler());
        register(new MIMEExtensionHandler());
        register(new SchemaExtensionHandler());
        register(new JAXWSBindingExtensionHandler());
    }

    public void register(ExtensionHandler h) {
        _extensionHandlers.put(h.getNamespaceURI(), h);
        h.setExtensionHandlers(_extensionHandlers);
    }

    public void unregister(ExtensionHandler h) {
        _extensionHandlers.put(h.getNamespaceURI(), null);
        h.setExtensionHandlers(null);
    }

    public void unregister(String uri) {
        _extensionHandlers.put(uri, null);
    }

    public void write(final WSDLDocument document, OutputStream os)
        throws IOException {
        final WriterContext context = new WriterContext(os);
        try {
            document.accept(new WSDLDocumentVisitor() {
                public void preVisit(Definitions definitions)
                    throws Exception {
                    context.push();
                    initializePrefixes(context, document);
                    context.writeStartTag(definitions.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        definitions.getName());
                    context.writeAttribute(
                        Constants.ATTR_TARGET_NAMESPACE,
                        definitions.getTargetNamespaceURI());
                    context.writeAllPendingNamespaceDeclarations();
                }
                public void postVisit(Definitions definitions)
                    throws Exception {
                    context.writeEndTag(definitions.getElementName());
                    context.pop();
                }
                public void visit(Import i) throws Exception {
                    context.writeStartTag(i.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAMESPACE,
                        i.getNamespace());
                    context.writeAttribute(
                        Constants.ATTR_LOCATION,
                        i.getLocation());
                    context.writeEndTag(i.getElementName());
                }
                public void preVisit(Types types) throws Exception {
                    context.writeStartTag(types.getElementName());
                }
                public void postVisit(Types types) throws Exception {
                    context.writeEndTag(types.getElementName());
                }
                public void preVisit(Message message) throws Exception {
                    context.writeStartTag(message.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        message.getName());
                }
                public void postVisit(Message message) throws Exception {
                    context.writeEndTag(message.getElementName());
                }
                public void visit(MessagePart part) throws Exception {
                    context.writeStartTag(part.getElementName());
                    context.writeAttribute(Constants.ATTR_NAME, part.getName());

                    QName dname = part.getDescriptor();
                    Kind dkind = part.getDescriptorKind();
                    if (dname != null && dkind != null) {
                        if (dkind.equals(SchemaKinds.XSD_ELEMENT)) {
                            context.writeAttribute(
                                Constants.ATTR_ELEMENT,
                                dname);
                        } else if (dkind.equals(SchemaKinds.XSD_TYPE)) {
                            context.writeAttribute(Constants.ATTR_TYPE, dname);
                        } else {
                            // TODO - add support for attribute extensions here
                        }
                    }
                    context.writeEndTag(part.getElementName());
                }
                public void preVisit(PortType portType) throws Exception {
                    context.writeStartTag(portType.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        portType.getName());
                }
                public void postVisit(PortType portType) throws Exception {
                    context.writeEndTag(portType.getElementName());
                }
                public void preVisit(Operation operation) throws Exception {
                    context.writeStartTag(operation.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        operation.getName());
                    //bug fix: 4947340, parameterOder="" should not be generated
                    if(operation.getParameterOrder() != null &&
                        operation.getParameterOrder().length() > 0) {
                        context.writeAttribute(
                            Constants.ATTR_PARAMETER_ORDER,
                            operation.getParameterOrder());
                    }
                }
                public void postVisit(Operation operation) throws Exception {
                    context.writeEndTag(operation.getElementName());
                }
                public void preVisit(Input input) throws Exception {
                    context.writeStartTag(input.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        input.getName());
                    context.writeAttribute(
                        Constants.ATTR_MESSAGE,
                        input.getMessage());
                }
                public void postVisit(Input input) throws Exception {
                    context.writeEndTag(input.getElementName());
                }
                public void preVisit(Output output) throws Exception {
                    context.writeStartTag(output.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        output.getName());
                    context.writeAttribute(
                        Constants.ATTR_MESSAGE,
                        output.getMessage());
                }
                public void postVisit(Output output) throws Exception {
                    context.writeEndTag(output.getElementName());
                }
                public void preVisit(Fault fault) throws Exception {
                    context.writeStartTag(fault.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        fault.getName());
                    context.writeAttribute(
                        Constants.ATTR_MESSAGE,
                        fault.getMessage());
                }
                public void postVisit(Fault fault) throws Exception {
                    context.writeEndTag(fault.getElementName());
                }
                public void preVisit(Binding binding) throws Exception {
                    context.writeStartTag(binding.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        binding.getName());
                    context.writeAttribute(
                        Constants.ATTR_TYPE,
                        binding.getPortType());
                }
                public void postVisit(Binding binding) throws Exception {
                    context.writeEndTag(binding.getElementName());
                }

                public void preVisit(BindingOperation operation)
                    throws Exception {
                    context.writeStartTag(operation.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        operation.getName());
                }
                public void postVisit(BindingOperation operation)
                    throws Exception {
                    context.writeEndTag(operation.getElementName());
                }
                public void preVisit(BindingInput input) throws Exception {
                    context.writeStartTag(input.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        input.getName());
                }
                public void postVisit(BindingInput input) throws Exception {
                    context.writeEndTag(input.getElementName());
                }
                public void preVisit(BindingOutput output) throws Exception {
                    context.writeStartTag(output.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        output.getName());
                }
                public void postVisit(BindingOutput output) throws Exception {
                    context.writeEndTag(output.getElementName());
                }
                public void preVisit(BindingFault fault) throws Exception {
                    context.writeStartTag(fault.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        fault.getName());
                }
                public void postVisit(BindingFault fault) throws Exception {
                    context.writeEndTag(fault.getElementName());
                }

                public void preVisit(Service service) throws Exception {
                    context.writeStartTag(service.getElementName());
                    context.writeAttribute(
                        Constants.ATTR_NAME,
                        service.getName());
                }
                public void postVisit(Service service) throws Exception {
                    context.writeEndTag(service.getElementName());
                }
                public void preVisit(Port port) throws Exception {
                    context.writeStartTag(port.getElementName());
                    context.writeAttribute(Constants.ATTR_NAME, port.getName());
                    context.writeAttribute(
                        Constants.ATTR_BINDING,
                        port.getBinding());
                }
                public void postVisit(Port port) throws Exception {
                    context.writeEndTag(port.getElementName());
                }
                public void preVisit(Extension extension) throws Exception {
                    ExtensionHandler h =
                        (ExtensionHandler) _extensionHandlers.get(
                            extension.getElementName().getNamespaceURI());
                    h.doHandleExtension(context, extension);
                }
                public void postVisit(Extension extension) throws Exception {
                }
                public void visit(Documentation documentation)
                    throws Exception {
                    context.writeTag(WSDLConstants.QNAME_DOCUMENTATION, null);
                }
            });
            context.flush();
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                // entirely unexpected exception
                throw new IllegalStateException();
            }
        }
    }

    private void initializePrefixes(
        WriterContext context,
        WSDLDocument document)
        throws IOException {
        // deal with the target namespace first
        String tnsURI = document.getDefinitions().getTargetNamespaceURI();
        if (tnsURI != null) {
            context.setTargetNamespaceURI(tnsURI);
            context.declarePrefix(TARGET_NAMESPACE_PREFIX, tnsURI);
        }

        // then with the WSDL namespace
//        context.declarePrefix(_commonPrefixes.get(Constants.NS_WSDL), Constants.NS_WSDL);
        context.declarePrefix("", Constants.NS_WSDL);

        // then with all other namespaces
        Set namespaces = document.collectAllNamespaces();
        for (Iterator iter = namespaces.iterator(); iter.hasNext();) {
            String nsURI = (String) iter.next();
            if (context.getPrefixFor(nsURI) != null)
                continue;

            String prefix = (String) _commonPrefixes.get(nsURI);
            if (prefix == null) {
                // create a new prefix for it
                prefix = context.findNewPrefix(NEW_NAMESPACE_PREFIX_BASE);
            }
            context.declarePrefix(prefix, nsURI);
        }
    }

    private Map _extensionHandlers;

    ////////

    private static Map<String, String> _commonPrefixes;

    static {
        _commonPrefixes = new HashMap<String, String>();
        _commonPrefixes.put(Constants.NS_WSDL, "wsdl");
        _commonPrefixes.put(Constants.NS_WSDL_SOAP, "soap");
        _commonPrefixes.put(Constants.NS_WSDL_HTTP, "http");
        _commonPrefixes.put(Constants.NS_WSDL_MIME, "mime");
        _commonPrefixes.put(Constants.NS_XSD, "xsd");
        _commonPrefixes.put(Constants.NS_XSI, "xsi");
        _commonPrefixes.put(JAXWSBindingsConstants.NS_JAXWS_BINDINGS, "jaxws");
    }

    private final static String TARGET_NAMESPACE_PREFIX = "tns";
    private final static String NEW_NAMESPACE_PREFIX_BASE = "ns";
}
