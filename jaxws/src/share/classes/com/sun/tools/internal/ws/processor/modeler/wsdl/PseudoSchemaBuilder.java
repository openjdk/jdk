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
package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.tools.internal.ws.processor.generator.Names;
import static com.sun.tools.internal.ws.processor.modeler.wsdl.WSDLModelerBase.getExtensionOfType;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.wscompile.Options;
import com.sun.tools.internal.ws.wsdl.document.*;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBinding;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAP12Binding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBinding;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.*;


/**
 * Builds all possible pseudo schemas for async operation ResponseBean to feed to XJC.
 *
 * @author Vivek Pandey
 */
public class PseudoSchemaBuilder {

    private final StringWriter buf = new StringWriter();
    private final WSDLDocument wsdlDocument;
    private WSDLModeler wsdlModeler;
    private final List<InputSource> schemas = new ArrayList<InputSource>();
    private final HashMap<QName, Port> bindingNameToPortMap = new HashMap<QName, Port>();
    private static final String w3ceprSchemaBinding = "<bindings\n" +
            "  xmlns=\"http://java.sun.com/xml/ns/jaxb\"\n" +
            "  xmlns:wsa=\"http://www.w3.org/2005/08/addressing\"\n" +
            "  version=\"2.1\">\n" +
            "  \n" +
            "  <bindings scd=\"x-schema::wsa\" if-exists=\"true\">\n" +
            "    <schemaBindings map=\"false\" />\n" +
            "    <bindings scd=\"wsa:EndpointReference\">\n" +
            "      <class ref=\"javax.xml.ws.wsaddressing.W3CEndpointReference\"/>\n" +
            "    </bindings>\n" +
            "    <bindings scd=\"~wsa:EndpointReferenceType\">\n" +
            "      <class ref=\"javax.xml.ws.wsaddressing.W3CEndpointReference\"/>\n" +
            "    </bindings>\n" +
            "  </bindings>\n" +
            "</bindings>";

    private static final String memberSubmissionEPR = "<bindings\n" +
            "  xmlns=\"http://java.sun.com/xml/ns/jaxb\"\n" +
            "  xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\"\n" +
            "  version=\"2.1\">\n" +
            "  \n" +
            "  <bindings scd=\"x-schema::wsa\" if-exists=\"true\">\n" +
            "    <schemaBindings map=\"false\" />\n" +
            "    <bindings scd=\"wsa:EndpointReference\">\n" +
            "      <class ref=\"com.sun.xml.internal.ws.developer.MemberSubmissionEndpointReference\"/>\n" +
            "    </bindings>\n" +
            "    <bindings scd=\"~wsa:EndpointReferenceType\">\n" +
            "      <class ref=\"com.sun.xml.internal.ws.developer.MemberSubmissionEndpointReference\"/>\n" +
            "    </bindings>\n" +
            "  </bindings>\n" +
            "</bindings>";

    private final static String sysId = "http://dummy.pseudo-schema#schema";

    private WsimportOptions options;
    public static List<InputSource> build(WSDLModeler wsdlModeler, WsimportOptions options, ErrorReceiver errReceiver) {
        PseudoSchemaBuilder b = new PseudoSchemaBuilder(wsdlModeler.document);
        b.wsdlModeler = wsdlModeler;
        b.options = options;
        b.build();
        int i;
        for(i = 0; i < b.schemas.size(); i++){
            InputSource is = b.schemas.get(i);
            is.setSystemId(sysId+(i + 1));
        }
        //add w3c EPR binding
        if(!(options.noAddressingBbinding) && options.target.isLaterThan(Options.Target.V2_1)){
            InputSource is = new InputSource(new ByteArrayInputStream(w3ceprSchemaBinding.getBytes()));
            is.setSystemId(sysId+(++i +1));
            b.schemas.add(is);
        }


        //TODO: uncomment after JAXB fixes the issue related to passing multiples of such bindings
        //add member submission EPR binding
//        InputSource is1 = new InputSource(new ByteArrayInputStream(memberSubmissionEPR.getBytes()));
//        is1.setSystemId(sysId+(++i + 1));
//        b.schemas.add(is1);

        return b.schemas;
    }


    private PseudoSchemaBuilder(WSDLDocument _wsdl) {
        this.wsdlDocument = _wsdl;
    }

    private void build() {
        for(Iterator<Service> itr=wsdlDocument.getDefinitions().services(); itr.hasNext(); )
            build(itr.next());
    }

    private void build(Service service) {
        for( Iterator<Port> itr=service.ports(); itr.hasNext(); )
            build(itr.next() );
    }

    private void build(Port port) {
        if(wsdlModeler.isProvider(port))
            return;
        Binding binding = port.resolveBinding(wsdlDocument);

        SOAPBinding soapBinding =
                    (SOAPBinding)getExtensionOfType(binding, SOAPBinding.class);
        //lets try and see if its SOAP 1.2. dont worry about extension flag, its
        // handled much earlier
        if (soapBinding == null) {
                    soapBinding =
                            (SOAPBinding)getExtensionOfType(binding, SOAP12Binding.class);
        }
        if(soapBinding == null)
            return;
        PortType portType = binding.resolvePortType(wsdlDocument);

        QName bindingName = WSDLModelerBase.getQNameOf(binding);

        //we dont want to process the port bound to the binding processed earlier
        if(bindingNameToPortMap.containsKey(bindingName))
            return;

        bindingNameToPortMap.put(bindingName, port);


        for(Iterator itr=binding.operations(); itr.hasNext();){
            BindingOperation bindingOperation = (BindingOperation)itr.next();

            // get only the bounded operations
            Set boundedOps = portType.getOperationsNamed(bindingOperation.getName());
            if(boundedOps.size() != 1)
                continue;
            Operation operation = (Operation)boundedOps.iterator().next();

            // No pseudo schema required for doc/lit
            if(wsdlModeler.isAsync(portType, operation)){
                buildAsync(portType, operation, bindingOperation);
            }
        }
    }

    /**
     * @param portType
     * @param operation
     * @param bindingOperation
     */
    private void buildAsync(PortType portType, Operation operation, BindingOperation bindingOperation) {
        String operationName = getCustomizedOperationName(operation);//operation.getName();
        if(operationName == null)
            return;
        Message outputMessage = null;
        if(operation.getOutput() != null)
            outputMessage = operation.getOutput().resolveMessage(wsdlDocument);
        if(outputMessage != null){
            List<MessagePart> allParts = new ArrayList<MessagePart>(outputMessage.getParts());
            if(allParts.size() > 1)
                build(getOperationName(operationName), allParts);
        }

    }

    private String getCustomizedOperationName(Operation operation) {
        JAXWSBinding jaxwsCustomization = (JAXWSBinding)getExtensionOfType(operation, JAXWSBinding.class);
        String operationName = (jaxwsCustomization != null)?((jaxwsCustomization.getMethodName() != null)?jaxwsCustomization.getMethodName().getName():null):null;
        if(operationName != null){
            if(Names.isJavaReservedWord(operationName)){
                return null;
            }

            return operationName;
        }
        return operation.getName();
    }

    private void writeImports(QName elementName, List<MessagePart> parts){
        Set<String> uris = new HashSet<String>();
        for(MessagePart p:parts){
            String ns = p.getDescriptor().getNamespaceURI();
            if(!uris.contains(ns) && !ns.equals("http://www.w3.org/2001/XMLSchema") && !ns.equals(elementName.getNamespaceURI())){
                print("<xs:import namespace=''{0}''/>", ns);
                uris.add(ns);
            }
        }
    }

    boolean asyncRespBeanBinding = false;
    private void build(QName elementName, List<MessagePart> allParts){

        print(
                "<xs:schema xmlns:xs=''http://www.w3.org/2001/XMLSchema''" +
                "           xmlns:jaxb=''http://java.sun.com/xml/ns/jaxb''" +
                "           xmlns:xjc=''http://java.sun.com/xml/ns/jaxb/xjc''" +
                "           jaxb:extensionBindingPrefixes=''xjc''" +
                "           jaxb:version=''1.0''" +
                "           targetNamespace=''{0}''>",
                elementName.getNamespaceURI());

        writeImports(elementName, allParts);

        if(!asyncRespBeanBinding){
            print(
                    "<xs:annotation><xs:appinfo>" +
                    "  <jaxb:schemaBindings>" +
                    "    <jaxb:package name=''{0}'' />" +
                    "  </jaxb:schemaBindings>" +
                    "</xs:appinfo></xs:annotation>",
                    wsdlModeler.getJavaPackage() );
            asyncRespBeanBinding = true;
        }

        print("<xs:element name=''{0}''>", elementName.getLocalPart());
        print("<xs:complexType>");
        print("<xs:sequence>");


        for(MessagePart p:allParts) {
            //rpclit wsdl:part must reference schema type not element, also it must exclude headers and mime parts
            if(p.getDescriptorKind() == SchemaKinds.XSD_ELEMENT){
                print("<xs:element ref=''types:{0}'' xmlns:types=''{1}''/>",p.getDescriptor().getLocalPart(), p.getDescriptor().getNamespaceURI());
            }else{
                print("<xs:element name=''{0}'' type=''{1}'' xmlns=''{2}'' />",
                    p.getName(),
                    p.getDescriptor().getLocalPart(),
                    p.getDescriptor().getNamespaceURI() );
            }
        }

        print("</xs:sequence>");
        print("</xs:complexType>");
        print("</xs:element>");
        print("</xs:schema>");

        // reset the StringWriter, so that next operation element could be written
        if(buf.toString().length() > 0){
            //System.out.println("Response bean Schema for operation========> "+ elementName+"\n\n"+buf);
            InputSource is = new InputSource(new StringReader(buf.toString()));
            schemas.add(is);
            buf.getBuffer().setLength(0);
        }
    }

    private QName getOperationName(String operationName){
        if(operationName == null)
            return null;
//        String namespaceURI = wsdlDocument.getDefinitions().getTargetNamespaceURI()+"?"+portType.getName()+"?" + operationName;
        String namespaceURI = "";
        return new QName(namespaceURI, operationName+"Response");
    }

    private void print( String msg ) {
        print( msg, new Object[0] );
    }
    private void print( String msg, Object arg1 ) {
        print( msg, new Object[]{arg1} );
    }
    private void print( String msg, Object arg1, Object arg2 ) {
        print( msg, new Object[]{arg1, arg2} );
    }
    private void print( String msg, Object arg1, Object arg2, Object arg3 ) {
        print( msg, new Object[]{arg1,arg2,arg3} );
    }
    private void print( String msg, Object[] args ) {
        buf.write(MessageFormat.format(msg,args));
        buf.write('\n');
    }

}
