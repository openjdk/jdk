/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.processor.modeler.annotation;

import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.resources.WebserviceapMessages;
import com.sun.tools.internal.ws.util.ClassNameInfo;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPStyle;
import com.sun.xml.internal.ws.model.RuntimeModeler;

import javax.annotation.processing.ProcessingEnvironment;
import javax.jws.Oneway;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * @author WS Development Team
 */
public abstract class WebServiceVisitor extends SimpleElementVisitor6<Void, Object> {

    protected ModelBuilder builder;
    protected String wsdlNamespace;
    protected String typeNamespace;
    protected Stack<SOAPBinding> soapBindingStack;
    protected SOAPBinding typeElementSoapBinding;
    protected SOAPStyle soapStyle = SOAPStyle.DOCUMENT;
    protected boolean wrapped = true;
    protected Port port;
    protected Name serviceImplName;
    protected Name endpointInterfaceName;
    protected AnnotationProcessorContext context;
    protected AnnotationProcessorContext.SeiContext seiContext;
    protected boolean processingSei = false;
    protected String serviceName;
    protected Name packageName;
    protected String portName;
    protected boolean endpointReferencesInterface = false;
    protected boolean hasWebMethods = false;
    protected TypeElement typeElement;
    protected Set<String> processedMethods;
    protected boolean pushedSoapBinding = false;

    private static final NoTypeVisitor NO_TYPE_VISITOR = new NoTypeVisitor();

    public WebServiceVisitor(ModelBuilder builder, AnnotationProcessorContext context) {
        this.builder = builder;
        this.context = context;
        soapBindingStack = new Stack<SOAPBinding>();
        processedMethods = new HashSet<String>();
    }

    @Override
    public Void visitType(TypeElement e, Object o) {
        WebService webService = e.getAnnotation(WebService.class);
        if (!shouldProcessWebService(webService, e))
            return null;
        if (builder.checkAndSetProcessed(e))
            return null;
        typeElement = e;

        switch (e.getKind()) {
            case INTERFACE: {
                if (endpointInterfaceName != null && !endpointInterfaceName.equals(e.getQualifiedName())) {
                    builder.processError(WebserviceapMessages.WEBSERVICEAP_ENDPOINTINTERFACES_DO_NOT_MATCH(endpointInterfaceName, e.getQualifiedName()), e);
                }
                verifySeiAnnotations(webService, e);
                endpointInterfaceName = e.getQualifiedName();
                processingSei = true;
                preProcessWebService(webService, e);
                processWebService(webService, e);
                postProcessWebService(webService, e);
                break;
            }
            case CLASS: {
                typeElementSoapBinding = e.getAnnotation(SOAPBinding.class);
                if (serviceImplName == null)
                    serviceImplName = e.getQualifiedName();
                String endpointInterfaceName = webService != null ? webService.endpointInterface() : null;
                if (endpointInterfaceName != null && endpointInterfaceName.length() > 0) {
                    checkForInvalidImplAnnotation(e, SOAPBinding.class);
                    if (webService.name().length() > 0)
                        builder.processError(WebserviceapMessages.WEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ELEMENT("name"), e);
                    endpointReferencesInterface = true;
                    verifyImplAnnotations(e);
                    inspectEndpointInterface(endpointInterfaceName, e);
                    serviceImplName = null;
                    return null;
                }
                processingSei = false;
                preProcessWebService(webService, e);
                processWebService(webService, e);
                serviceImplName = null;
                postProcessWebService(webService, e);
                serviceImplName = null;
                break;
            }
            default:
                break;
        }
        return null;
    }

    protected void verifySeiAnnotations(WebService webService, TypeElement d) {
        if (webService.endpointInterface().length() > 0) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_ENDPOINTINTERFACE_ON_INTERFACE(
                    d.getQualifiedName(), webService.endpointInterface()), d);
        }
        if (webService.serviceName().length() > 0) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT(
                    "serviceName", d.getQualifiedName()), d);
        }
        if (webService.portName().length() > 0) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT(
                    "portName", d.getQualifiedName()), d);
        }
    }

    protected void verifyImplAnnotations(TypeElement d) {
        for (ExecutableElement method : ElementFilter.methodsIn(d.getEnclosedElements())) {
            checkForInvalidImplAnnotation(method, WebMethod.class);
            checkForInvalidImplAnnotation(method, Oneway.class);
            checkForInvalidImplAnnotation(method, WebResult.class);
            for (VariableElement param : method.getParameters()) {
                checkForInvalidImplAnnotation(param, WebParam.class);
            }
        }
    }

    protected void checkForInvalidSeiAnnotation(TypeElement element, Class annotationClass) {
        Object annotation = element.getAnnotation(annotationClass);
        if (annotation != null) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_SEI_ANNOTATION(
                    annotationClass.getName(), element.getQualifiedName()), element);
        }
    }

    protected void checkForInvalidImplAnnotation(Element element, Class annotationClass) {
        Object annotation = element.getAnnotation(annotationClass);
        if (annotation != null) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ANNOTATION(annotationClass.getName()), element);
        }
    }

    protected void preProcessWebService(WebService webService, TypeElement element) {
        processedMethods = new HashSet<String>();
        seiContext = context.getSeiContext(element);
        String targetNamespace = null;
        if (webService != null)
            targetNamespace = webService.targetNamespace();
        PackageElement packageElement = builder.getProcessingEnvironment().getElementUtils().getPackageOf(element);
        if (targetNamespace == null || targetNamespace.length() == 0) {
            String packageName = packageElement.getQualifiedName().toString();
            if (packageName == null || packageName.length() == 0) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_NO_PACKAGE_CLASS_MUST_HAVE_TARGETNAMESPACE(
                        element.getQualifiedName()), element);
            }
            targetNamespace = RuntimeModeler.getNamespace(packageName);
        }
        seiContext.setNamespaceUri(targetNamespace);
        if (serviceImplName == null)
            serviceImplName = seiContext.getSeiImplName();
        if (serviceImplName != null) {
            seiContext.setSeiImplName(serviceImplName);
            context.addSeiContext(serviceImplName, seiContext);
        }
        portName = ClassNameInfo.getName(element.getSimpleName().toString().replace('$', '_'));
        packageName = packageElement.getQualifiedName();
        portName = webService != null && webService.name() != null && webService.name().length() > 0 ?
                webService.name() : portName;
        serviceName = ClassNameInfo.getName(element.getQualifiedName().toString()) + WebServiceConstants.SERVICE.getValue();
        serviceName = webService != null && webService.serviceName() != null && webService.serviceName().length() > 0 ?
                webService.serviceName() : serviceName;
        wsdlNamespace = seiContext.getNamespaceUri();
        typeNamespace = wsdlNamespace;

        SOAPBinding soapBinding = element.getAnnotation(SOAPBinding.class);
        if (soapBinding != null) {
            pushedSoapBinding = pushSoapBinding(soapBinding, element, element);
        } else if (element.equals(typeElement)) {
            pushedSoapBinding = pushSoapBinding(new MySoapBinding(), element, element);
        }
    }

    public static boolean sameStyle(SOAPBinding.Style style, SOAPStyle soapStyle) {
        return style.equals(SOAPBinding.Style.DOCUMENT)
                && soapStyle.equals(SOAPStyle.DOCUMENT)
                || style.equals(SOAPBinding.Style.RPC)
                && soapStyle.equals(SOAPStyle.RPC);
    }

    protected boolean pushSoapBinding(SOAPBinding soapBinding, Element bindingElement, TypeElement classElement) {
        boolean changed = false;
        if (!sameStyle(soapBinding.style(), soapStyle)) {
            changed = true;
            if (pushedSoapBinding)
                builder.processError(WebserviceapMessages.WEBSERVICEAP_MIXED_BINDING_STYLE(
                        classElement.getQualifiedName()), bindingElement);
        }
        if (soapBinding.style().equals(SOAPBinding.Style.RPC)) {
            soapStyle = SOAPStyle.RPC;
            wrapped = true;
            if (soapBinding.parameterStyle().equals(ParameterStyle.BARE)) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_RPC_LITERAL_MUST_NOT_BE_BARE(
                        classElement.getQualifiedName()), bindingElement);
            }
        } else {
            soapStyle = SOAPStyle.DOCUMENT;
            if (wrapped != soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED)) {
                wrapped = soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED);
                changed = true;
            }
        }
        if (soapBinding.use().equals(SOAPBinding.Use.ENCODED)) {
            String style = "rpc";
            if (soapBinding.style().equals(SOAPBinding.Style.DOCUMENT))
                style = "document";
            builder.processError(WebserviceapMessages.WEBSERVICE_ENCODED_NOT_SUPPORTED(
                    classElement.getQualifiedName(), style), bindingElement);
        }
        if (changed || soapBindingStack.empty()) {
            soapBindingStack.push(soapBinding);
            pushedSoapBinding = true;
        }
        return changed;
    }

    protected SOAPBinding popSoapBinding() {
        if (pushedSoapBinding)
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
        } else {
                pushedSoapBinding = false;
        }
        return soapBinding;
    }

    protected String getNamespace(PackageElement packageElement) {
        return RuntimeModeler.getNamespace(packageElement.getQualifiedName().toString());
    }

    protected boolean shouldProcessWebService(WebService webService, TypeElement element) {
        switch (element.getKind()) {
            case INTERFACE: {
                hasWebMethods = false;
                if (webService == null)
                    builder.processError(WebserviceapMessages.WEBSERVICEAP_ENDPOINTINTERFACE_HAS_NO_WEBSERVICE_ANNOTATION(
                            element.getQualifiedName()), element);

                SOAPBinding soapBinding = element.getAnnotation(SOAPBinding.class);
                if (soapBinding != null
                        && soapBinding.style() == SOAPBinding.Style.RPC
                        && soapBinding.parameterStyle() == SOAPBinding.ParameterStyle.BARE) {
                    builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_SOAPBINDING_PARAMETERSTYLE(
                            soapBinding, element), element);
                    return false;
                }
                return isLegalSei(element);
            }
            case CLASS: {
                if (webService == null)
                    return false;
                hasWebMethods = hasWebMethods(element);
                SOAPBinding soapBinding = element.getAnnotation(SOAPBinding.class);
                if (soapBinding != null
                        && soapBinding.style() == SOAPBinding.Style.RPC
                        && soapBinding.parameterStyle() == SOAPBinding.ParameterStyle.BARE) {
                    builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_SOAPBINDING_PARAMETERSTYLE(
                            soapBinding, element), element);
                    return false;
                }
                return isLegalImplementation(webService, element);
            }
            default: {
                throw new IllegalArgumentException("Class or Interface was expecting. But element: " + element);
            }
        }
    }

    abstract protected void processWebService(WebService webService, TypeElement element);

    protected void postProcessWebService(WebService webService, TypeElement element) {
        processMethods(element);
        popSoapBinding();
    }

    protected boolean hasWebMethods(TypeElement element) {
        if (element.getQualifiedName().toString().equals(Object.class.getName()))
            return false;
        WebMethod webMethod;
        for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
            webMethod = method.getAnnotation(WebMethod.class);
            if (webMethod != null) {
                if (webMethod.exclude()) {
                    if (webMethod.operationName().length() > 0)
                        builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_WEBMETHOD_ELEMENT_WITH_EXCLUDE(
                                "operationName", element.getQualifiedName(), method.toString()), method);
                    if (webMethod.action().length() > 0)
                        builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_WEBMETHOD_ELEMENT_WITH_EXCLUDE(
                                "action", element.getQualifiedName(), method.toString()), method);
                } else {
                    return true;
                }
            }
        }
        return false;//hasWebMethods(d.getSuperclass().getDeclaration());
    }

    protected void processMethods(TypeElement element) {
        switch (element.getKind()) {
            case INTERFACE: {
                builder.log("ProcessedMethods Interface: " + element);
                hasWebMethods = false;
                for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
                    method.accept(this, null);
                }
                for (TypeMirror superType : element.getInterfaces())
                    processMethods((TypeElement) ((DeclaredType) superType).asElement());
                break;
            }
            case CLASS: {
                builder.log("ProcessedMethods Class: " + element);
                hasWebMethods = hasWebMethods(element);
                if (element.getQualifiedName().toString().equals(Object.class.getName()))
                    return;
                if (element.getAnnotation(WebService.class) != null) {
                    // Super classes must have @WebService annotations to pick up their methods
                    for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
                        method.accept(this, null);
                    }
                }
                TypeMirror superclass = element.getSuperclass();
                if (!superclass.getKind().equals(TypeKind.NONE)) {
                    processMethods((TypeElement) ((DeclaredType) superclass).asElement());
                }
                break;
            }
            default:
                break;
        }
    }

    private TypeElement getEndpointInterfaceElement(String endpointInterfaceName, TypeElement element) {
        TypeElement intTypeElement = null;
        for (TypeMirror interfaceType : element.getInterfaces()) {
            if (endpointInterfaceName.equals(interfaceType.toString())) {
                intTypeElement = (TypeElement) ((DeclaredType) interfaceType).asElement();
                seiContext = context.getSeiContext(intTypeElement.getQualifiedName());
                assert (seiContext != null);
                seiContext.setImplementsSei(true);
                break;
            }
        }
        if (intTypeElement == null) {
            intTypeElement = builder.getProcessingEnvironment().getElementUtils().getTypeElement(endpointInterfaceName);
        }
        if (intTypeElement == null)
            builder.processError(WebserviceapMessages.WEBSERVICEAP_ENDPOINTINTERFACE_CLASS_NOT_FOUND(endpointInterfaceName));
        return intTypeElement;
    }

    private void inspectEndpointInterface(String endpointInterfaceName, TypeElement d) {
        TypeElement intTypeElement = getEndpointInterfaceElement(endpointInterfaceName, d);
        if (intTypeElement != null)
            intTypeElement.accept(this, null);
    }

    @Override
    public Void visitExecutable(ExecutableElement method, Object o) {
        // Methods must be public
        if (!method.getModifiers().contains(Modifier.PUBLIC))
            return null;
        if (processedMethod(method))
            return null;
        WebMethod webMethod = method.getAnnotation(WebMethod.class);
        if (webMethod != null && webMethod.exclude())
            return null;
        SOAPBinding soapBinding = method.getAnnotation(SOAPBinding.class);
        if (soapBinding == null && !method.getEnclosingElement().equals(typeElement)) {
            if (method.getEnclosingElement().getKind().equals(ElementKind.CLASS)) {
                soapBinding = method.getEnclosingElement().getAnnotation(SOAPBinding.class);
                if (soapBinding != null)
                    builder.log("using " + method.getEnclosingElement() + "'s SOAPBinding.");
                else {
                    soapBinding = new MySoapBinding();
                }
            }
        }
        boolean newBinding = false;
        if (soapBinding != null) {
            newBinding = pushSoapBinding(soapBinding, method, typeElement);
        }
        try {
            if (shouldProcessMethod(method, webMethod)) {
                processMethod(method, webMethod);
            }
        } finally {
            if (newBinding) {
                popSoapBinding();
            }
        }
        return null;
    }

    protected boolean processedMethod(ExecutableElement method) {
        String id = method.toString();
        if (processedMethods.contains(id))
            return true;
        processedMethods.add(id);
        return false;
    }

    protected boolean shouldProcessMethod(ExecutableElement method, WebMethod webMethod) {
        builder.log("should process method: " + method.getSimpleName() + " hasWebMethods: " + hasWebMethods + " ");
        /*
        Fix for https://jax-ws.dev.java.net/issues/show_bug.cgi?id=577
        if (hasWebMethods && webMethod == null) {
            builder.log("webMethod == null");
            return false;
        }
        */
        Collection<Modifier> modifiers = method.getModifiers();
        boolean staticFinal = modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.FINAL);
        if (staticFinal) {
            if (webMethod != null) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_METHOD_IS_STATIC_OR_FINAL(method.getEnclosingElement(),
                        method), method);
            }
            return false;
        }
        boolean result = (endpointReferencesInterface ||
                method.getEnclosingElement().equals(typeElement) ||
                (method.getEnclosingElement().getAnnotation(WebService.class) != null));
        builder.log("endpointReferencesInterface: " + endpointReferencesInterface);
        builder.log("declaring class has WebService: " + (method.getEnclosingElement().getAnnotation(WebService.class) != null));
        builder.log("returning: " + result);
        return result;
    }

    abstract protected void processMethod(ExecutableElement method, WebMethod webMethod);

    protected boolean isLegalImplementation(WebService webService, TypeElement classElement) {
        boolean isStateful = isStateful(classElement);

        Collection<Modifier> modifiers = classElement.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC)) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_CLASS_NOT_PUBLIC(classElement.getQualifiedName()), classElement);
            return false;
        }
        if (modifiers.contains(Modifier.FINAL) && !isStateful) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_CLASS_IS_FINAL(classElement.getQualifiedName()), classElement);
            return false;
        }
        if (modifiers.contains(Modifier.ABSTRACT) && !isStateful) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_CLASS_IS_ABSTRACT(classElement.getQualifiedName()), classElement);
            return false;
        }
        boolean hasDefaultConstructor = false;
        for (ExecutableElement constructor : ElementFilter.constructorsIn(classElement.getEnclosedElements())) {
            if (constructor.getModifiers().contains(Modifier.PUBLIC) &&
                    constructor.getParameters().isEmpty()) {
                hasDefaultConstructor = true;
                break;
            }
        }
        if (!hasDefaultConstructor && !isStateful) {
            if (classElement.getEnclosingElement() != null && !modifiers.contains(Modifier.STATIC)) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_CLASS_IS_INNERCLASS_NOT_STATIC(
                        classElement.getQualifiedName()), classElement);
                return false;
            }

            builder.processError(WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_NO_DEFAULT_CONSTRUCTOR(
                    classElement.getQualifiedName()), classElement);
            return false;
        }
        if (webService.endpointInterface().isEmpty()) {
            if (!methodsAreLegal(classElement))
                return false;
        } else {
            TypeElement interfaceElement = getEndpointInterfaceElement(webService.endpointInterface(), classElement);
            if (!classImplementsSei(classElement, interfaceElement))
                return false;
        }

        return true;
    }

    private boolean isStateful(TypeElement classElement) {
        try {
            // We don't want dependency on rt-ha module as its not integrated in JDK
            return classElement.getAnnotation((Class<? extends Annotation>) Class.forName("com.sun.xml.internal.ws.developer.Stateful")) != null;
        } catch (ClassNotFoundException e) {
            //ignore
        }
        return false;
    }

    protected boolean classImplementsSei(TypeElement classElement, TypeElement interfaceElement) {
        for (TypeMirror interfaceType : classElement.getInterfaces()) {
            if (((DeclaredType) interfaceType).asElement().equals(interfaceElement))
                return true;
        }
        List<ExecutableElement> classMethods = getClassMethods(classElement);
        boolean implementsMethod;
        for (ExecutableElement interfaceMethod : ElementFilter.methodsIn(interfaceElement.getEnclosedElements())) {
            implementsMethod = false;
            for (ExecutableElement classMethod : classMethods) {
                if (sameMethod(interfaceMethod, classMethod)) {
                    implementsMethod = true;
                    classMethods.remove(classMethod);
                    break;
                }
            }
            if (!implementsMethod) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_METHOD_NOT_IMPLEMENTED(interfaceElement.getSimpleName(), classElement.getSimpleName(), interfaceMethod), interfaceMethod);
                return false;
            }
        }
        return true;
    }

    private static List<ExecutableElement> getClassMethods(TypeElement classElement) {
        if (classElement.getQualifiedName().toString().equals(Object.class.getName())) // we don't need Object's methods
            return null;
        TypeElement superclassElement = (TypeElement) ((DeclaredType) classElement.getSuperclass()).asElement();
        List<ExecutableElement> superclassesMethods = getClassMethods(superclassElement);
        List<ExecutableElement> classMethods = ElementFilter.methodsIn(classElement.getEnclosedElements());
        if (superclassesMethods == null)
            return classMethods;
        else
            superclassesMethods.addAll(classMethods);
        return superclassesMethods;
    }

    protected boolean sameMethod(ExecutableElement method1, ExecutableElement method2) {
        if (!method1.getSimpleName().equals(method2.getSimpleName()))
            return false;
        Types typeUtils = builder.getProcessingEnvironment().getTypeUtils();
        if(!typeUtils.isSameType(method1.getReturnType(), method2.getReturnType())
                && !typeUtils.isSubtype(method2.getReturnType(), method1.getReturnType()))
            return false;
        List<? extends VariableElement> parameters1 = method1.getParameters();
        List<? extends VariableElement> parameters2 = method2.getParameters();
        if (parameters1.size() != parameters2.size())
            return false;
        for (int i = 0; i < parameters1.size(); i++) {
            if (!typeUtils.isSameType(parameters1.get(i).asType(), parameters2.get(i).asType()))
                return false;
        }
        return true;
    }

    protected boolean isLegalSei(TypeElement interfaceElement) {
        for (VariableElement field : ElementFilter.fieldsIn(interfaceElement.getEnclosedElements()))
            if (field.getConstantValue() != null) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_SEI_CANNOT_CONTAIN_CONSTANT_VALUES(
                        interfaceElement.getQualifiedName(), field.getSimpleName()));
                return false;
            }
        return methodsAreLegal(interfaceElement);
    }

    protected boolean methodsAreLegal(TypeElement element) {
        switch (element.getKind()) {
            case INTERFACE: {
                hasWebMethods = false;
                for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
                    if (!isLegalMethod(method, element))
                        return false;
                }
                for (TypeMirror superInterface : element.getInterfaces()) {
                    if (!methodsAreLegal((TypeElement) ((DeclaredType) superInterface).asElement()))
                        return false;
                }
                return true;
            }
            case CLASS: {
                hasWebMethods = hasWebMethods(element);
                for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
                    if (!method.getModifiers().contains(Modifier.PUBLIC))
                        continue; // let's validate only public methods
                    if (!isLegalMethod(method, element))
                        return false;
                }
                DeclaredType superClass = (DeclaredType) element.getSuperclass();

                TypeElement tE = (TypeElement) superClass.asElement();
                return tE.getQualifiedName().toString().equals(Object.class.getName())
                        || methodsAreLegal(tE);
            }
            default: {
                throw new IllegalArgumentException("Class or interface was expecting. But element: " + element);
            }
        }
    }

    protected boolean isLegalMethod(ExecutableElement method, TypeElement typeElement) {
        WebMethod webMethod = method.getAnnotation(WebMethod.class);
        //SEI cannot have methods with @WebMethod(exclude=true)
        if (typeElement.getKind().equals(ElementKind.INTERFACE) && webMethod != null && webMethod.exclude())
            builder.processError(WebserviceapMessages.WEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT_EXCLUDE("exclude=true", typeElement.getQualifiedName(), method.toString()), method);
        // With https://jax-ws.dev.java.net/issues/show_bug.cgi?id=577, hasWebMethods has no effect
        if (hasWebMethods && webMethod == null) // backwards compatibility (for legacyWebMethod computation)
            return true;

        if ((webMethod != null) && webMethod.exclude()) {
            return true;
        }
        /*
        This check is not needed as Impl class is already checked that it is not abstract.
        if (typeElement instanceof TypeElement && method.getModifiers().contains(Modifier.ABSTRACT)) {  // use Kind.equals instead of instanceOf
            builder.processError(method.getPosition(), WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_METHOD_IS_ABSTRACT(typeElement.getQualifiedName(), method.getSimpleName()));
            return false;
        }
        */
        TypeMirror returnType = method.getReturnType();
        if (!isLegalType(returnType)) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_METHOD_RETURN_TYPE_CANNOT_IMPLEMENT_REMOTE(typeElement.getQualifiedName(),
                    method.getSimpleName(),
                    returnType), method);
        }
        boolean isOneWay = method.getAnnotation(Oneway.class) != null;
        if (isOneWay && !isValidOneWayMethod(method, typeElement))
            return false;

        SOAPBinding soapBinding = method.getAnnotation(SOAPBinding.class);
        if (soapBinding != null) {
            if (soapBinding.style().equals(SOAPBinding.Style.RPC)) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_RPC_SOAPBINDING_NOT_ALLOWED_ON_METHOD(typeElement.getQualifiedName(), method.toString()), method);
            }
        }

        int paramIndex = 0;
        for (VariableElement parameter : method.getParameters()) {
            if (!isLegalParameter(parameter, method, typeElement, paramIndex++))
                return false;
        }

        if (!isDocLitWrapped() && soapStyle.equals(SOAPStyle.DOCUMENT)) {
            VariableElement outParam = getOutParameter(method);
            int inParams = getModeParameterCount(method, WebParam.Mode.IN);
            int outParams = getModeParameterCount(method, WebParam.Mode.OUT);
            if (inParams != 1) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_DOC_BARE_AND_NO_ONE_IN(typeElement.getQualifiedName(), method.toString()), method);
            }
            if (returnType.accept(NO_TYPE_VISITOR, null)) {
                if (outParam == null && !isOneWay) {
                    builder.processError(WebserviceapMessages.WEBSERVICEAP_DOC_BARE_NO_OUT(typeElement.getQualifiedName(), method.toString()), method);
                }
                if (outParams != 1) {
                    if (!isOneWay && outParams != 0)
                        builder.processError(WebserviceapMessages.WEBSERVICEAP_DOC_BARE_NO_RETURN_AND_NO_OUT(typeElement.getQualifiedName(), method.toString()), method);
                }
            } else {
                if (outParams > 0) {
                    builder.processError(WebserviceapMessages.WEBSERVICEAP_DOC_BARE_RETURN_AND_OUT(typeElement.getQualifiedName(), method.toString()), outParam);
                }
            }
        }
        return true;
    }

    protected boolean isLegalParameter(VariableElement param,
                                       ExecutableElement method,
                                       TypeElement typeElement,
                                       int paramIndex) {
        if (!isLegalType(param.asType())) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_METHOD_PARAMETER_TYPES_CANNOT_IMPLEMENT_REMOTE(typeElement.getQualifiedName(),
                    method.getSimpleName(),
                    param.getSimpleName(),
                    param.asType().toString()), param);
            return false;
        }
        TypeMirror holderType;
        holderType = builder.getHolderValueType(param.asType());
        WebParam webParam = param.getAnnotation(WebParam.class);
        WebParam.Mode mode = null;
        if (webParam != null)
            mode = webParam.mode();

        if (holderType != null) {
            if (mode != null && mode == WebParam.Mode.IN)
                builder.processError(WebserviceapMessages.WEBSERVICEAP_HOLDER_PARAMETERS_MUST_NOT_BE_IN_ONLY(typeElement.getQualifiedName(), method.toString(), paramIndex), param);
        } else if (mode != null && mode != WebParam.Mode.IN) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_NON_IN_PARAMETERS_MUST_BE_HOLDER(typeElement.getQualifiedName(), method.toString(), paramIndex), param);
        }

        return true;
    }

    protected boolean isDocLitWrapped() {
        return soapStyle.equals(SOAPStyle.DOCUMENT) && wrapped;
    }

    private static final class NoTypeVisitor extends SimpleTypeVisitor6<Boolean, Void> {

        @Override
        public Boolean visitNoType(NoType t, Void o) {
            return true;
        }

        @Override
        protected Boolean defaultAction(TypeMirror e, Void aVoid) {
            return false;
        }
    }

    protected boolean isValidOneWayMethod(ExecutableElement method, TypeElement typeElement) {
        boolean valid = true;
        if (!(method.getReturnType().accept(NO_TYPE_VISITOR, null))) {
            // this is an error, cannot be OneWay and have a return type
            builder.processError(WebserviceapMessages.WEBSERVICEAP_ONEWAY_OPERATION_CANNOT_HAVE_RETURN_TYPE(typeElement.getQualifiedName(), method.toString()), method);
            valid = false;
        }
        VariableElement outParam = getOutParameter(method);
        if (outParam != null) {
            builder.processError(WebserviceapMessages.WEBSERVICEAP_ONEWAY_AND_OUT(typeElement.getQualifiedName(), method.toString()), outParam);
            valid = false;
        }
        if (!isDocLitWrapped() && soapStyle.equals(SOAPStyle.DOCUMENT)) {
            int inCnt = getModeParameterCount(method, WebParam.Mode.IN);
            if (inCnt != 1) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_ONEWAY_AND_NOT_ONE_IN(typeElement.getQualifiedName(), method.toString()), method);
                valid = false;
            }
        }
        for (TypeMirror thrownType : method.getThrownTypes()) {
            TypeElement thrownElement = (TypeElement) ((DeclaredType) thrownType).asElement();
            if (builder.isServiceException(thrownType)) {
                builder.processError(WebserviceapMessages.WEBSERVICEAP_ONEWAY_OPERATION_CANNOT_DECLARE_EXCEPTIONS(
                        typeElement.getQualifiedName(), method.toString(), thrownElement.getQualifiedName()), method);
                valid = false;
            }
        }
        return valid;
    }

    protected int getModeParameterCount(ExecutableElement method, WebParam.Mode mode) {
        WebParam webParam;
        int cnt = 0;
        for (VariableElement param : method.getParameters()) {
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
        assert mode1 == WebParam.Mode.IN || mode1 == WebParam.Mode.OUT;
        return (mode1 == WebParam.Mode.IN && mode2 != WebParam.Mode.OUT) || (mode1 == WebParam.Mode.OUT && mode2 != WebParam.Mode.IN);
    }

    protected boolean isHolder(VariableElement param) {
        return builder.getHolderValueType(param.asType()) != null;
    }

    protected boolean isLegalType(TypeMirror type) {
        if (!(type != null && type.getKind().equals(TypeKind.DECLARED)))
            return true;
        TypeElement tE = (TypeElement) ((DeclaredType) type).asElement();
        if (tE == null) {
            // can be null, if this type's declaration is unknown. This may be the result of a processing error, such as a missing class file.
            builder.processError(WebserviceapMessages.WEBSERVICEAP_COULD_NOT_FIND_TYPEDECL(type.toString(), context.getRound()));
        }
        return !builder.isRemote(tE);
    }

    protected VariableElement getOutParameter(ExecutableElement method) {
        WebParam webParam;
        for (VariableElement param : method.getParameters()) {
            webParam = param.getAnnotation(WebParam.class);
            if (webParam != null && webParam.mode() != WebParam.Mode.IN) {
                return param;
            }
        }
        return null;
    }

    protected static class MySoapBinding implements SOAPBinding {

        @Override
        public Style style() {
            return SOAPBinding.Style.DOCUMENT;
        }

        @Override
        public Use use() {
            return SOAPBinding.Use.LITERAL;
        }

        @Override
        public ParameterStyle parameterStyle() {
            return SOAPBinding.ParameterStyle.WRAPPED;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return SOAPBinding.class;
        }
    }
}
