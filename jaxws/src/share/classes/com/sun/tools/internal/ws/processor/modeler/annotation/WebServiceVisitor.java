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
package com.sun.tools.internal.ws.processor.modeler.annotation;


import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.ConstructorDeclaration;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.declaration.FieldDeclaration;
import com.sun.mirror.declaration.InterfaceDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.Modifier;
import com.sun.mirror.declaration.PackageDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.ClassType;
import com.sun.mirror.type.DeclaredType;
import com.sun.mirror.type.InterfaceType;
import com.sun.mirror.type.ReferenceType;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.type.VoidType;
import com.sun.mirror.util.DeclarationVisitor;
import com.sun.mirror.util.SimpleDeclarationVisitor;
import com.sun.mirror.util.SourcePosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

import com.sun.xml.internal.ws.modeler.RuntimeModeler;
import com.sun.tools.internal.ws.processor.model.Parameter;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.processor.model.java.JavaSimpleType;
import com.sun.tools.internal.ws.processor.model.java.JavaType;
import com.sun.tools.internal.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.internal.ws.processor.modeler.annotation.AnnotationProcessorContext.SEIContext;
import com.sun.tools.internal.ws.util.ClassNameInfo;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPStyle;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPUse;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;

import javax.jws.HandlerChain;
import javax.jws.Oneway;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.api.Reference;
import com.sun.tools.internal.ws.processor.modeler.annotation.AnnotationProcessorContext;
import com.sun.tools.internal.ws.processor.modeler.annotation.ModelBuilder;
import com.sun.tools.internal.ws.processor.modeler.annotation.WebServiceConstants;

/**
 *
 * @author  WS Development Team
 */
public abstract class WebServiceVisitor extends SimpleDeclarationVisitor implements WebServiceConstants {
    protected ModelBuilder builder;
    protected String wsdlNamespace;
    protected String typeNamespace;
    protected Stack<SOAPBinding> soapBindingStack;
    protected SOAPBinding typeDeclSOAPBinding;
    protected SOAPUse soapUse = SOAPUse.LITERAL;
    protected SOAPStyle soapStyle = SOAPStyle.DOCUMENT;
    protected boolean wrapped = true;
    protected HandlerChain hChain;
    protected Port port;
    protected String serviceImplName;
    protected String endpointInterfaceName;
    protected AnnotationProcessorContext context;
    protected SEIContext seiContext;
    protected boolean processingSEI = false;
    protected String serviceName;
    protected String packageName;
    protected String portName;
    protected boolean endpointReferencesInterface = false;
    protected boolean hasWebMethods = false;
    protected JavaSimpleTypeCreator simpleTypeCreator;
    protected TypeDeclaration typeDecl;
    protected Set<String> processedMethods;
    protected boolean pushedSOAPBinding = false;
    protected static final String ANNOTATION_ELEMENT_ERROR = "webserviceap.endpointinteface.plus.element";



    public WebServiceVisitor(ModelBuilder builder, AnnotationProcessorContext context) {
        this.builder = builder;
        this.context = context;
        this.simpleTypeCreator = new JavaSimpleTypeCreator();
        soapBindingStack = new Stack<SOAPBinding>();
        processedMethods = new HashSet<String>();
    }

    public void visitInterfaceDeclaration(InterfaceDeclaration d) {
        WebService webService = (WebService)d.getAnnotation(WebService.class);
        if (!shouldProcessWebService(webService, d))
            return;
        if (builder.checkAndSetProcessed(d))
            return;
        typeDecl = d;
        if (endpointInterfaceName != null && !endpointInterfaceName.equals(d.getQualifiedName())) {
            builder.onError(d.getPosition(), "webserviceap.endpointinterfaces.do.not.match", new Object[]
            {endpointInterfaceName, d.getQualifiedName()});
        }
        verifySEIAnnotations(webService, d);
        endpointInterfaceName = d.getQualifiedName();
        processingSEI = true;
        preProcessWebService(webService, d);
        processWebService(webService, d);
        postProcessWebService(webService, d);
    }

    public void visitClassDeclaration(ClassDeclaration d) {
        WebService webService = d.getAnnotation(WebService.class);
        if (!shouldProcessWebService(webService, d))
            return;
        if (builder.checkAndSetProcessed(d))
            return;
        typeDeclSOAPBinding = d.getAnnotation(SOAPBinding.class);
        typeDecl = d;
        if (serviceImplName == null)
            serviceImplName = d.getQualifiedName();
        String endpointInterfaceName = webService != null ? webService.endpointInterface() : null;
        if (endpointInterfaceName != null && endpointInterfaceName.length() > 0) {
            SourcePosition pos = pos = d.getPosition();
            checkForInvalidImplAnnotation(d, SOAPBinding.class);
            if (webService.name().length() > 0)
                annotationError(pos, ANNOTATION_ELEMENT_ERROR,"name");
            endpointReferencesInterface = true;
            verifyImplAnnotations(d);
            inspectEndpointInterface(endpointInterfaceName, d);
            serviceImplName = null;
            return;
        }
        processingSEI = false;
        preProcessWebService(webService, d);
        processWebService(webService, d);
        serviceImplName = null;
        postProcessWebService(webService, d);
        serviceImplName = null;
    }

    protected void verifySEIAnnotations(WebService webService, InterfaceDeclaration d) {
        if (webService.endpointInterface().length() > 0) {
            builder.onError(d.getPosition(), "webservicefactory.endpointinterface.on.interface",
                    new Object[] {d.getQualifiedName(), webService.endpointInterface()});
        }
        if (webService.serviceName().length() > 0) {
            builder.onError(d.getPosition(), "webserviceap.invalid.sei.annotation.element",
                    new Object[] {"serviceName", d.getQualifiedName()});
        }
        if (webService.portName().length() > 0) {
            builder.onError(d.getPosition(), "webserviceap.invalid.sei.annotation.element",
                    new Object[] {"portName", d.getQualifiedName()});
        }
    }

    protected void verifyImplAnnotations(ClassDeclaration d) {
        for (MethodDeclaration method : d.getMethods()) {
            checkForInvalidImplAnnotation(method, WebMethod.class);
            checkForInvalidImplAnnotation(method, Oneway.class);
            checkForInvalidImplAnnotation(method, WebResult.class);
            for (ParameterDeclaration param : method.getParameters()) {
                checkForInvalidImplAnnotation(param, WebParam.class);
            }
        }
    }

    protected void checkForInvalidSEIAnnotation(InterfaceDeclaration d, Class annotationClass) {
        Object annotation = d.getAnnotation(annotationClass);
        if (annotation != null) {
            SourcePosition pos = d.getPosition();
            annotationError(pos, "webserviceap.invalid.sei.annotation",
                    new Object[] {annotationClass.getName(), d.getQualifiedName()});
        }
    }

    protected void checkForInvalidImplAnnotation(Declaration d, Class annotationClass) {
        Object annotation = d.getAnnotation(annotationClass);
        if (annotation != null) {
            SourcePosition pos = d.getPosition();
            annotationError(pos, "webserviceap.endpointinteface.plus.annotation",
                    annotationClass.getName());
        }
    }

    protected void annotationError(SourcePosition pos, String key, String element) {
        annotationError(pos, key, new Object[] {element});
    }

    protected void annotationError(SourcePosition pos, String key, Object[] args) {
        builder.onError(pos, key, args);
    }


    protected void preProcessWebService(WebService webService, TypeDeclaration d) {
        seiContext = context.getSEIContext(d);
        String targetNamespace = null;
        if (webService != null)
            targetNamespace = webService.targetNamespace();
        if (targetNamespace == null || targetNamespace.length() == 0) {
            String packageName = d.getPackage().getQualifiedName();
            if (packageName == null || packageName.length() == 0) {
                builder.onError(d.getPosition(), "webserviceap.no.package.class.must.have.targetnamespace",
                        new Object[] {d.getQualifiedName()});
            }
            targetNamespace = getNamespace(d.getPackage());
        }
        seiContext.setNamespaceURI(targetNamespace);
        if (serviceImplName == null)
            serviceImplName = seiContext.getSEIImplName();
        if (serviceImplName != null) {
            seiContext.setSEIImplName(serviceImplName);
            context.addSEIContext(serviceImplName, seiContext);
        }
        portName = ClassNameInfo.getName(
                d.getSimpleName().replace(
                SIGC_INNERCLASS,
                SIGC_UNDERSCORE));;
                packageName = d.getPackage().getQualifiedName();
                portName = webService != null && webService.name() != null && webService.name().length() >0 ?
                    webService.name() : portName;
                serviceName = ClassNameInfo.getName(d.getQualifiedName())+SERVICE;
                serviceName = webService != null && webService.serviceName() != null &&
                        webService.serviceName().length() > 0 ?
                            webService.serviceName() : serviceName;
                wsdlNamespace = seiContext.getNamespaceURI();
                typeNamespace = wsdlNamespace;

                SOAPBinding soapBinding = d.getAnnotation(SOAPBinding.class);
                if (soapBinding != null) {
                    pushedSOAPBinding = pushSOAPBinding(soapBinding, d, d);
                } else if (d.equals(typeDecl)) {
                    pushedSOAPBinding = pushSOAPBinding(new MySOAPBinding(), d, d);
                }
    }

    public static boolean sameStyle(SOAPBinding.Style style, SOAPStyle soapStyle) {
        if (style.equals(SOAPBinding.Style.DOCUMENT) &&
                soapStyle.equals(SOAPStyle.DOCUMENT))
            return true;
        if (style.equals(SOAPBinding.Style.RPC) &&
                soapStyle.equals(SOAPStyle.RPC))
            return true;
        return false;
    }

    protected boolean pushSOAPBinding(SOAPBinding soapBinding, Declaration bindingDecl,
            TypeDeclaration classDecl) {
        boolean changed = false;
        if (!sameStyle(soapBinding.style(), soapStyle)) {
            changed = true;
            if (pushedSOAPBinding)
                builder.onError(bindingDecl.getPosition(), "webserviceap.mixed.binding.style",
                        new Object[] {classDecl.getQualifiedName()});
        }
        if (soapBinding.style().equals(SOAPBinding.Style.RPC)) {
            soapStyle = SOAPStyle.RPC;
            wrapped = true;
            if (soapBinding.parameterStyle().equals(ParameterStyle.BARE)) {
                builder.onError(bindingDecl.getPosition(), "webserviceap.rpc.literal.must.not.be.bare",
                        new Object[] {classDecl.getQualifiedName()});
            }

        } else {
            soapStyle = SOAPStyle.DOCUMENT;
            if (wrapped != soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED)) {
                wrapped = soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED);
                changed = true;
            }
        }
        if (soapBinding.use().equals(SOAPBinding.Use.ENCODED)) {
            builder.onError(bindingDecl.getPosition(), "webserviceap.rpc.encoded.not.supported",
                    new Object[] {classDecl.getQualifiedName()});
        }
        if (changed || soapBindingStack.empty()) {
            soapBindingStack.push(soapBinding);
            pushedSOAPBinding = true;
        }
        return changed;
    }


    protected SOAPBinding popSOAPBinding() {
        if (pushedSOAPBinding)
            soapBindingStack.pop();
        SOAPBinding soapBinding = null;
        if (!soapBindingStack.empty()) {
            soapBinding = soapBindingStack.peek();
            if (soapBinding.style().equals(SOAPBinding.Style.RPC)) {
                soapStyle = SOAPStyle.RPC;
                wrapped = true;
            } else {
                soapStyle = SOAPStyle.DOCUMENT;
                wrapped = soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED);
            }
        }
        return soapBinding;
    }

    protected String getNamespace(PackageDeclaration packageDecl) {
        return RuntimeModeler.getNamespace(packageDecl.getQualifiedName());
    }

//    abstract protected boolean shouldProcessWebService(WebService webService, InterfaceDeclaration intf);

//    abstract protected boolean shouldProcessWebService(WebService webService, ClassDeclaration decl);
    protected boolean shouldProcessWebService(WebService webService, InterfaceDeclaration intf) {
        hasWebMethods = false;
        if (webService == null)
            builder.onError(intf.getPosition(), "webserviceap.endpointinterface.has.no.webservice.annotation",
                    new Object[] {intf.getQualifiedName()});
        if (isLegalSEI(intf))
            return true;
        return false;
    }

    protected boolean shouldProcessWebService(WebService webService, ClassDeclaration classDecl) {
        if (webService == null)
            return false;
        hasWebMethods = hasWebMethods(classDecl);
        return isLegalImplementation(webService, classDecl);
    }

    abstract protected void processWebService(WebService webService, TypeDeclaration d);

    protected void postProcessWebService(WebService webService, InterfaceDeclaration d) {
        processMethods(d);
        popSOAPBinding();
    }

    protected void postProcessWebService(WebService webService, ClassDeclaration d) {
        processMethods(d);
        popSOAPBinding();
    }


    protected boolean hasWebMethods(ClassDeclaration d) {
        if (d.getQualifiedName().equals(JAVA_LANG_OBJECT))
            return false;
        WebMethod webMethod;
        for (MethodDeclaration method : d.getMethods()) {
            webMethod = method.getAnnotation(WebMethod.class);
            if (webMethod != null) {
                if (webMethod.exclude()) {
                    if (webMethod.operationName().length() > 0)
                        builder.onError(method.getPosition(), "webserviceap.invalid.webmethod.element.with.exclude",
                                new Object[] {"operationName", d.getQualifiedName(), method.toString()});
                                if (webMethod.action().length() > 0)
                                    builder.onError(method.getPosition(), "webserviceap.invalid.webmethod.element.with.exclude",
                                            new Object[] {"action", d.getQualifiedName(), method.toString()});
                } else {
                    return true;
                }
            }
        }
        return false;//hasWebMethods(d.getSuperclass().getDeclaration());
    }

    protected void processMethods(InterfaceDeclaration d) {
        builder.log("ProcessedMethods Interface: "+d);
        hasWebMethods = false;
        for (MethodDeclaration methodDecl : d.getMethods()) {
            methodDecl.accept((DeclarationVisitor)this);
        }
        for (InterfaceType superType : d.getSuperinterfaces())
            processMethods(superType.getDeclaration());
    }

    protected void processMethods(ClassDeclaration d) {
        builder.log("ProcessedMethods Class: "+d);
        hasWebMethods = hasWebMethods(d);
        if (d.getQualifiedName().equals(JAVA_LANG_OBJECT))
            return;
        if (d.getAnnotation(WebService.class) != null) {
            // Super classes must have @WebService annotations to pick up their methods
            for (MethodDeclaration methodDecl : d.getMethods()) {
                methodDecl.accept((DeclarationVisitor)this);
            }
        }
        if (d.getSuperclass() != null) {
            processMethods(d.getSuperclass().getDeclaration());
        }
    }

    private InterfaceDeclaration getEndpointInterfaceDecl(String endpointInterfaceName,
            ClassDeclaration d) {
        InterfaceDeclaration intTypeDecl = null;
        for (InterfaceType interfaceType : d.getSuperinterfaces()) {
            if (endpointInterfaceName.equals(interfaceType.toString())) {
                intTypeDecl = interfaceType.getDeclaration();
                seiContext = context.getSEIContext(intTypeDecl.getQualifiedName());
                assert(seiContext != null);
                seiContext.setImplementsSEI(true);
                break;
            }
        }
        if (intTypeDecl == null) {
            intTypeDecl = (InterfaceDeclaration)builder.getTypeDeclaration(endpointInterfaceName);
        }
        if (intTypeDecl == null)
            builder.onError("webserviceap.endpointinterface.class.not.found",
                    new Object[] {endpointInterfaceName});
                    return intTypeDecl;
    }


    private void inspectEndpointInterface(String endpointInterfaceName, ClassDeclaration d) {
        TypeDeclaration intTypeDecl = getEndpointInterfaceDecl(endpointInterfaceName, d);
        if (intTypeDecl != null)
            intTypeDecl.accept((DeclarationVisitor)this);
    }

    public void visitMethodDeclaration(MethodDeclaration method) {
        // Methods must be public
        if (!method.getModifiers().contains(Modifier.PUBLIC))
            return;
        if (processedMethod(method))
            return;
        WebMethod webMethod = method.getAnnotation(WebMethod.class);
        if (webMethod != null && webMethod.exclude())
            return;
        SOAPBinding soapBinding = method.getAnnotation(SOAPBinding.class);
        if (soapBinding == null && !method.getDeclaringType().equals(typeDecl)) {
            if (method.getDeclaringType() instanceof ClassDeclaration) {
                soapBinding = method.getDeclaringType().getAnnotation(SOAPBinding.class);
                if (soapBinding != null)
                    builder.log("using "+method.getDeclaringType()+"'s SOAPBinding.");
                else {
                    soapBinding = new MySOAPBinding();
                }
            }
        }
        boolean newBinding = false;
        if (soapBinding != null) {
            newBinding = pushSOAPBinding(soapBinding, method, typeDecl);
        }
        try {
            if (shouldProcessMethod(method, webMethod)) {
                processMethod(method, webMethod);
            }
        } finally {
            if (newBinding) {
                popSOAPBinding();
            }
        }
    }

    protected boolean processedMethod(MethodDeclaration method) {
        String id = method.toString();
        if (processedMethods.contains(id))
            return true;
        processedMethods.add(id);
        return false;
    }


    protected boolean shouldProcessMethod(MethodDeclaration method, WebMethod webMethod) {
        builder.log("should process method: "+method.getSimpleName()+" hasWebMethods: "+ hasWebMethods+" ");
        if (hasWebMethods && webMethod == null) {
            builder.log("webMethod == null");
            return false;
        }
        boolean retval = (endpointReferencesInterface ||
                method.getDeclaringType().equals(typeDecl) ||
                (method.getDeclaringType().getAnnotation(WebService.class) != null));
        builder.log("endpointReferencesInterface: "+endpointReferencesInterface);
        builder.log("declaring class has WebSevice: "+(method.getDeclaringType().getAnnotation(WebService.class) != null));
        builder.log("returning: "+retval);
        return  retval;
    }

    abstract protected void processMethod(MethodDeclaration method, WebMethod webMethod);


    protected boolean isLegalImplementation(WebService webService, ClassDeclaration classDecl) {
        Collection<Modifier> modifiers = classDecl.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC)){
            builder.onError(classDecl.getPosition(), "webserviceap.webservice.class.not.public",
                    new Object[] {classDecl.getQualifiedName()});
                    return false;
        }
        if (modifiers.contains(Modifier.FINAL)) {
            builder.onError(classDecl.getPosition(), "webserviceap.webservice.class.is.final",
                    new Object[] {classDecl.getQualifiedName()});
                    return false;
        }
        if (modifiers.contains(Modifier.ABSTRACT)) {
            builder.onError(classDecl.getPosition(), "webserviceap.webservice.class.is.abstract",
                    new Object[] {classDecl.getQualifiedName()});
                    return false;
        }
        if (classDecl.getDeclaringType() != null && !modifiers.contains(Modifier.STATIC)) {
            builder.onError(classDecl.getPosition(), "webserviceap.webservice.class.is.innerclass.not.static",
                    new Object[] {classDecl.getQualifiedName()});
                    return false;
        }
        boolean hasDefaultConstructor = false;
        for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
            if (constructor.getModifiers().contains(Modifier.PUBLIC) &&
                    constructor.getParameters().size() == 0) {
                hasDefaultConstructor = true;
                break;
            }
        }
        if (!hasDefaultConstructor) {
            builder.onError(classDecl.getPosition(), "webserviceap.webservice.no.default.constructor",
                    new Object[] {classDecl.getQualifiedName()});
                    return false;
        }
        if (webService.endpointInterface().length() == 0) {
            if (!methodsAreLegal(classDecl))
                return false;
        } else {
            InterfaceDeclaration intfDecl = getEndpointInterfaceDecl(webService.endpointInterface(), classDecl);
            if (!classImplementsSEI(classDecl, intfDecl))
                return false;
        }

        return true;
    }

    protected boolean classImplementsSEI(ClassDeclaration classDecl,
            InterfaceDeclaration intfDecl) {
        for (InterfaceType interfaceType : classDecl.getSuperinterfaces()) {
            if (interfaceType.getDeclaration().equals(intfDecl))
                return true;
        }
        boolean implementsMethod;
        for (MethodDeclaration method : intfDecl.getMethods()) {
            implementsMethod = false;
            for (MethodDeclaration classMethod : classDecl.getMethods()) {
                if (sameMethod(method, classMethod)) {
                    implementsMethod = true;
                    break;
                }
            }
            if (!implementsMethod) {
                builder.onError(method.getPosition(), "webserviceap.method.not.implemented",
                        new Object[] {intfDecl.getSimpleName(), classDecl.getSimpleName(),
                                method});
                                return false;
            }
        }
        return true;
    }

    protected boolean sameMethod(MethodDeclaration method1, MethodDeclaration method2) {
        if (!method1.getSimpleName().equals(method2.getSimpleName()))
            return false;
        if (!method1.getReturnType().equals(method2.getReturnType()))
            return false;
        ParameterDeclaration[] params1 = method1.getParameters().toArray(new ParameterDeclaration[0]);
        ParameterDeclaration[] params2 = method2.getParameters().toArray(new ParameterDeclaration[0]);
        if (params1.length != params2.length)
            return false;
        int pos = 0;
        for (ParameterDeclaration param1 : method1.getParameters()) {
            if (!param1.getType().equals(params2[pos++].getType()))
                return false;
        }
        return true;
    }

    protected boolean isLegalSEI(InterfaceDeclaration intf) {
        for (FieldDeclaration field : intf.getFields()) {
            if (field.getConstantValue() != null) {
                builder.onError("webserviceap.sei.cannot.contain.constant.values",
                        new Object[] {intf.getQualifiedName(), field.getSimpleName()});
                        return false;
            }
        }
        if (!methodsAreLegal(intf))
            return false;
        return true;
    }

    protected boolean methodsAreLegal(InterfaceDeclaration intfDecl) {
        hasWebMethods = false;
        for (MethodDeclaration method : intfDecl.getMethods()) {
            if (!isLegalMethod(method, intfDecl))
                return false;
        }
        for (InterfaceType superIntf : intfDecl.getSuperinterfaces()) {
            if (!methodsAreLegal(superIntf.getDeclaration()))
                return false;
        }
        return true;
    }

    protected boolean methodsAreLegal(ClassDeclaration classDecl) {
        hasWebMethods = hasWebMethods(classDecl);
        for (MethodDeclaration method : classDecl.getMethods()) {
            if (!isLegalMethod(method, classDecl))
                return false;
        }
        ClassType superClass = classDecl.getSuperclass();
        if (superClass != null && !methodsAreLegal(superClass.getDeclaration())) {
            return false;
        }
        return true;
    }


    protected boolean isLegalMethod(MethodDeclaration method, TypeDeclaration typeDecl) {
        if (hasWebMethods && method.getAnnotation(WebMethod.class) == null)
            return true;
        if (typeDecl instanceof ClassDeclaration && method.getModifiers().contains(Modifier.ABSTRACT)) {
            builder.onError(method.getPosition(), "webserviceap.webservice.method.is.abstract",
                    new Object[] {typeDecl.getQualifiedName(), method.getSimpleName()});
                    return false;
        }

        if (!isLegalType(method.getReturnType())) {
            builder.onError(method.getPosition(), "webserviceap.method.return.type.cannot.implement.remote",
                    new Object[] {typeDecl.getQualifiedName(),
                            method.getSimpleName(),
                            method.getReturnType()});
        }
        boolean isOneway = method.getAnnotation(Oneway.class) != null;
        if (isOneway && !isValidOnewayMethod(method, typeDecl))
            return false;


        SOAPBinding soapBinding = method.getAnnotation(SOAPBinding.class);
        if (soapBinding != null) {
            if (soapBinding.style().equals(SOAPBinding.Style.RPC)) {
                builder.onError(method.getPosition(),"webserviceap.rpc.soapbinding.not.allowed.on.method",
                        new Object[] {typeDecl.getQualifiedName(), method.toString()});
            }
        }

        int paramIndex = 0;
        for (ParameterDeclaration parameter : method.getParameters()) {
            if (!isLegalParameter(parameter, method, typeDecl, paramIndex++))
                return false;
        }

        if (!isDocLitWrapped() &&
                soapStyle.equals(SOAPStyle.DOCUMENT)) {
            ParameterDeclaration outParam = getOutParameter(method);
            int inParams = getModeParameterCount(method, WebParam.Mode.IN);
            int outParams = getModeParameterCount(method, WebParam.Mode.OUT);
            if (inParams != 1) {
                builder.onError(method.getPosition(),
                        "webserviceap.doc.bare.and.no.one.in",
                        new Object[] {typeDecl.getQualifiedName(), method.toString()});
            }
            if (method.getReturnType() instanceof VoidType) {
                if (outParam == null && !isOneway) {
                    builder.onError(method.getPosition(),
                            "webserviceap.doc.bare.no.out",
                            new Object[] {typeDecl.getQualifiedName(), method.toString()});
                }
                if (outParams != 1) {
                    if (!isOneway && outParams != 0)
                        builder.onError(method.getPosition(),
                                "webserviceap.doc.bare.no.return.and.no.out",
                                new Object[] {typeDecl.getQualifiedName(), method.toString()});
                }
            } else {
                if (outParams > 0) {
                    builder.onError(outParam.getPosition(),
                            "webserviceap.doc.bare.return.and.out",
                            new Object[] {typeDecl.getQualifiedName(), method.toString()});
                }
            }
        }
        return true;
    }

    protected boolean isLegalParameter(ParameterDeclaration param,
            MethodDeclaration method,
            TypeDeclaration typeDecl,
            int paramIndex) {
        if (!isLegalType(param.getType())) {
            builder.onError(param.getPosition(), "webserviceap.method.parameter.types.cannot.implement.remote",
                    new Object[] {typeDecl.getQualifiedName(),
                            method.getSimpleName(),
                            param.getSimpleName(),
                            param.getType().toString()});
                            return false;
        }
        TypeMirror holderType;
        holderType = builder.getHolderValueType(param.getType());
        WebParam webParam = param.getAnnotation(WebParam.class);
        WebParam.Mode mode = null;
        if (webParam != null)
            mode = webParam.mode();

        if (holderType != null) {
            if (mode != null &&  mode.equals(WebParam.Mode.IN))
                builder.onError(param.getPosition(), "webserviceap.holder.parameters.must.not.be.in.only",
                        new Object[] {typeDecl.getQualifiedName(), method.toString(), paramIndex});
        } else if (mode != null && !mode.equals(WebParam.Mode.IN)) {
            builder.onError(param.getPosition(), "webserviceap.non.in.parameters.must.be.holder",
                    new Object[] {typeDecl.getQualifiedName(), method.toString(), paramIndex});
        }

        return true;
    }

    protected boolean isDocLitWrapped() {
        return soapStyle.equals(SOAPStyle.DOCUMENT) && wrapped;
    }

    protected boolean isValidOnewayMethod(MethodDeclaration method, TypeDeclaration typeDecl) {
        boolean valid = true;
        if (!(method.getReturnType() instanceof VoidType)) {
            // this is an error, cannot be Oneway and have a return type
            builder.onError(method.getPosition(), "webserviceap.oneway.operation.cannot.have.return.type",
                    new Object[] {typeDecl.getQualifiedName(), method.toString()});
            valid = false;
        }
        ParameterDeclaration outParam = getOutParameter(method);
        if (outParam != null) {
            builder.onError(outParam.getPosition(),
                    "webserviceap.oneway.and.out",
                    new Object[] {typeDecl.getQualifiedName(), method.toString()});
            valid = false;
        }
        if (!isDocLitWrapped() && soapStyle.equals(SOAPStyle.DOCUMENT)) {
            int inCnt = getModeParameterCount(method, WebParam.Mode.IN);
            if (inCnt != 1) {
                builder.onError(method.getPosition(),
                        "webserviceap.oneway.and.not.one.in",
                        new Object[] {typeDecl.getQualifiedName(), method.toString()});
                valid = false;
            }
        }
        ClassDeclaration exDecl;
        for (ReferenceType thrownType : method.getThrownTypes()) {
            exDecl = ((ClassType)thrownType).getDeclaration();
            if (!builder.isRemoteException(exDecl)) {
                builder.onError(method.getPosition(), "webserviceap.oneway.operation.cannot.declare.exceptions",
                        new Object[] {typeDecl.getQualifiedName(), method.toString(), exDecl.getQualifiedName()});
                valid = false;
            }
        }
        return valid;
    }

    protected int getModeParameterCount(MethodDeclaration method, WebParam.Mode mode) {
        WebParam webParam;
        int cnt = 0;
        for (ParameterDeclaration param : method.getParameters()) {
            webParam = param.getAnnotation(WebParam.class);
            if (webParam != null) {
                if (webParam.header())
                    continue;
                if (isEquivalentModes(mode, webParam.mode()))
                    cnt++;
            } else {
                if (isEquivalentModes(mode, WebParam.Mode.IN)) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    protected boolean isEquivalentModes(WebParam.Mode mode1, WebParam.Mode mode2) {
        if (mode1.equals(mode2))
            return true;
        assert(mode1.equals(WebParam.Mode.IN) ||
                mode1.equals(WebParam.Mode.OUT));
        if (mode1.equals(WebParam.Mode.IN) &&
                !(mode2.equals(WebParam.Mode.OUT)))
            return true;
        if (mode1.equals(WebParam.Mode.OUT) &&
                !(mode2.equals(WebParam.Mode.IN)))
            return true;
        return false;
    }

    protected boolean isHolder(ParameterDeclaration param) {
        return builder.getHolderValueType(param.getType()) != null;
    }

    protected boolean isLegalType(TypeMirror type) {
        if (!(type instanceof DeclaredType))
            return true;
        return !builder.isRemote(((DeclaredType)type).getDeclaration());
    }

    public void addSchemaElements(MethodDeclaration method, boolean isDocLitWrapped) {
        addReturnSchemaElement(method, isDocLitWrapped);
        boolean hasInParam = false;
        for (ParameterDeclaration param : method.getParameters()) {
            hasInParam |= addParamSchemaElement(param, method, isDocLitWrapped);
        }
        if (!hasInParam && soapStyle.equals(SOAPStyle.DOCUMENT) && !isDocLitWrapped) {
            QName paramQName = new QName(wsdlNamespace, method.getSimpleName());
            seiContext.addSchemaElement(paramQName, null);
        }
    }

    public void addReturnSchemaElement(MethodDeclaration method, boolean isDocLitWrapped) {
        TypeMirror returnType = method.getReturnType();
        WebResult webResult = method.getAnnotation(WebResult.class);
        String responseName = builder.getResponseName(method.getSimpleName());
        String responseNamespace = wsdlNamespace;
        boolean isResultHeader = false;
        if (webResult != null) {
            responseName = webResult.name().length() > 0 ? webResult.name() : responseName;
            responseNamespace = webResult.targetNamespace().length() > 0 ? webResult.targetNamespace() : responseNamespace;
            isResultHeader = webResult.header();
        }
        QName typeName = new QName(responseNamespace, responseName);
        if (!(returnType instanceof VoidType) &&
                (!isDocLitWrapped || isResultHeader)) {
            Reference ref = seiContext.addReference(method);
            if (!soapStyle.equals(SOAPStyle.RPC))
                seiContext.addSchemaElement(typeName, ref);
        }
    }

    public boolean addParamSchemaElement(ParameterDeclaration param, MethodDeclaration method, boolean isDocLitWrappped) {
        boolean isInParam = false;
        WebParam webParam = param.getAnnotation(WebParam.class);
        String paramName = param.getSimpleName();
        String paramNamespace = wsdlNamespace;
        TypeMirror paramType = param.getType();
        TypeMirror holderType = builder.getHolderValueType(paramType);
        boolean isHeader = false;
        if (soapStyle.equals(SOAPStyle.DOCUMENT) && !wrapped) {
            paramName = method.getSimpleName();
        }
        if (webParam != null) {
            paramName = webParam.name() != null && webParam.name().length() > 0 ? webParam.name() : paramName;
            isHeader = webParam.header();
            paramNamespace = webParam.targetNamespace().length() > 0 ? webParam.targetNamespace() : paramNamespace;
        }
        if (holderType != null) {
            paramType = holderType;
        }
        if (isHeader || soapStyle.equals(SOAPStyle.DOCUMENT)) {
            if (isHeader || !isDocLitWrappped) {
                QName paramQName = new QName(paramNamespace, paramName);
                Reference ref = seiContext.addReference(paramType, param);
                seiContext.addSchemaElement(paramQName, ref);
            }
        } else
            seiContext.addReference(paramType, param);
        if (!isHeader && (holderType == null ||
                (webParam == null || !webParam.mode().equals(WebParam.Mode.OUT)))) {
            isInParam = true;
        }
        return isInParam;
    }

    protected ParameterDeclaration getOutParameter(MethodDeclaration method) {
        WebParam webParam;
        for (ParameterDeclaration param : method.getParameters()) {
            webParam = (WebParam)param.getAnnotation(WebParam.class);
            if (webParam != null &&
                    !webParam.mode().equals(WebParam.Mode.IN)) {
                return param;
            }
        }
        return null;
    }

    protected static class MySOAPBinding implements SOAPBinding {
        public Style style() {return SOAPBinding.Style.DOCUMENT;}
        public Use use() {return SOAPBinding.Use.LITERAL; }
        public ParameterStyle parameterStyle() { return SOAPBinding.ParameterStyle.WRAPPED;}
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return SOAPBinding.class;
        }
    }
}
