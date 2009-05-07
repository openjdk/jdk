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
package com.sun.tools.internal.ws.resources;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class WebserviceapMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.webserviceap");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableWEBSERVICEAP_RPC_LITERAL_MUST_NOT_BE_BARE(Object arg0) {
        return messageFactory.getMessage("webserviceap.rpc.literal.must.not.be.bare", arg0);
    }

    /**
     * RPC literal SOAPBindings must have parameterStyle WRAPPPED. Class: {0}.
     *
     */
    public static String WEBSERVICEAP_RPC_LITERAL_MUST_NOT_BE_BARE(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_RPC_LITERAL_MUST_NOT_BE_BARE(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT_EXCLUDE(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.invalid.sei.annotation.element.exclude", arg0, arg1, arg2);
    }

    /**
     * The @javax.jws.WebMethod({0}) cannot be used on a service endpoint interface. Class: {1} method: {2}
     *
     */
    public static String WEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT_EXCLUDE(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT_EXCLUDE(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_WEBSERVICE_CLASS_IS_INNERCLASS_NOT_STATIC(Object arg0) {
        return messageFactory.getMessage("webserviceap.webservice.class.is.innerclass.not.static", arg0);
    }

    /**
     * Inner classes annotated with @javax.jws.WebService must be static. Class: {0}
     *
     */
    public static String WEBSERVICEAP_WEBSERVICE_CLASS_IS_INNERCLASS_NOT_STATIC(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_WEBSERVICE_CLASS_IS_INNERCLASS_NOT_STATIC(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_WEBSERVICE_METHOD_IS_ABSTRACT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.webservice.method.is.abstract", arg0, arg1);
    }

    /**
     * Classes annotated with @javax.jws.WebService must not have abstract methods. Class: {0} Method: {1}
     *
     */
    public static String WEBSERVICEAP_WEBSERVICE_METHOD_IS_ABSTRACT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_WEBSERVICE_METHOD_IS_ABSTRACT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_ONEWAY_OPERATION_CANNOT_HAVE_RETURN_TYPE(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.oneway.operation.cannot.have.return.type", arg0, arg1);
    }

    /**
     * The method {1} of class {0} is annotated @Oneway but has a return type.
     *
     */
    public static String WEBSERVICEAP_ONEWAY_OPERATION_CANNOT_HAVE_RETURN_TYPE(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_ONEWAY_OPERATION_CANNOT_HAVE_RETURN_TYPE(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_WARNING(Object arg0) {
        return messageFactory.getMessage("webserviceap.warning", arg0);
    }

    /**
     * warning: {0}
     *
     */
    public static String WEBSERVICEAP_WARNING(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_WARNING(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_RPC_SOAPBINDING_NOT_ALLOWED_ON_METHOD(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.rpc.soapbinding.not.allowed.on.method", arg0, arg1);
    }

    /**
     * SOAPBinding.Style.RPC binding annotations are not allowed on methods.  Class: {0} Method: {1}
     *
     */
    public static String WEBSERVICEAP_RPC_SOAPBINDING_NOT_ALLOWED_ON_METHOD(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_RPC_SOAPBINDING_NOT_ALLOWED_ON_METHOD(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_COULD_NOT_FIND_HANDLERCHAIN(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.could.not.find.handlerchain", arg0, arg1);
    }

    /**
     * Could not find the handlerchain {0} in the handler file {1}
     *
     */
    public static String WEBSERVICEAP_COULD_NOT_FIND_HANDLERCHAIN(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_COULD_NOT_FIND_HANDLERCHAIN(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_NO_PACKAGE_CLASS_MUST_HAVE_TARGETNAMESPACE(Object arg0) {
        return messageFactory.getMessage("webserviceap.no.package.class.must.have.targetnamespace", arg0);
    }

    /**
     * @javax.jws.Webservice annotated classes that do not belong to a package must have the @javax.jws.Webservice.targetNamespace element.  Class: {0}
     *
     */
    public static String WEBSERVICEAP_NO_PACKAGE_CLASS_MUST_HAVE_TARGETNAMESPACE(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_NO_PACKAGE_CLASS_MUST_HAVE_TARGETNAMESPACE(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_CLASS_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("webserviceap.class.not.found", arg0);
    }

    /**
     * Class Not Found: {0}
     *
     */
    public static String WEBSERVICEAP_CLASS_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_CLASS_NOT_FOUND(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_DOC_BARE_NO_RETURN_AND_NO_OUT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.doc.bare.no.return.and.no.out", arg0, arg1);
    }

    /**
     * Document literal bare methods that do not have a return value must have a single OUT/INOUT parameter.  Class: {0} Method: {1}
     *
     */
    public static String WEBSERVICEAP_DOC_BARE_NO_RETURN_AND_NO_OUT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_DOC_BARE_NO_RETURN_AND_NO_OUT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_METHOD_RETURN_NOT_UNIQUE(Object arg0, Object arg1, Object arg2, Object arg3) {
        return messageFactory.getMessage("webserviceap.document.literal.bare.method.return.not.unique", arg0, arg1, arg2, arg3);
    }

    /**
     * Document literal bare methods must have a unique result name return type combination.  Class {0} method: {1}, result name: {2} return type: {3}
     *
     */
    public static String WEBSERVICEAP_DOCUMENT_LITERAL_BARE_METHOD_RETURN_NOT_UNIQUE(Object arg0, Object arg1, Object arg2, Object arg3) {
        return localizer.localize(localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_METHOD_RETURN_NOT_UNIQUE(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableWEBSERVICEAP_DOC_BARE_NO_OUT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.doc.bare.no.out", arg0, arg1);
    }

    /**
     * Document/literal bare methods with no return type or OUT/INOUT parameters must be annotated as @Oneway. Class: {0}, method: {1}
     *
     */
    public static String WEBSERVICEAP_DOC_BARE_NO_OUT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_DOC_BARE_NO_OUT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_RPC_LITERAL_PARAMETERS_MUST_HAVE_WEBPARAM(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.rpc.literal.parameters.must.have.webparam", arg0, arg1, arg2);
    }

    /**
     * All rpc literal parameters must have a WebParam annotation.  Class: {0} method: {1} parameter {2}
     *
     */
    public static String WEBSERVICEAP_RPC_LITERAL_PARAMETERS_MUST_HAVE_WEBPARAM(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_RPC_LITERAL_PARAMETERS_MUST_HAVE_WEBPARAM(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_MODEL_ALREADY_EXISTS() {
        return messageFactory.getMessage("webserviceap.model.already.exists");
    }

    /**
     * model already exists
     *
     */
    public static String WEBSERVICEAP_MODEL_ALREADY_EXISTS() {
        return localizer.localize(localizableWEBSERVICEAP_MODEL_ALREADY_EXISTS());
    }

    public static Localizable localizableWEBSERVICEAP_ENDPOINTINTERFACE_ON_INTERFACE(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.endpointinterface.on.interface", arg0, arg1);
    }

    /**
     * Service endpointpoint interface: {0} has cannot have a WebService.endpointInterface annotation: {1}
     *
     */
    public static String WEBSERVICEAP_ENDPOINTINTERFACE_ON_INTERFACE(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_ENDPOINTINTERFACE_ON_INTERFACE(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_METHOD_NOT_ANNOTATED(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.method.not.annotated", arg0, arg1);
    }

    /**
     * The method {0} on class {1} is not annotated.
     *
     */
    public static String WEBSERVICEAP_METHOD_NOT_ANNOTATED(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_METHOD_NOT_ANNOTATED(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_NON_IN_PARAMETERS_MUST_BE_HOLDER(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.non.in.parameters.must.be.holder", arg0, arg1, arg2);
    }

    /**
     * Class: {0}, method: {1}, parameter: {2} is not WebParam.Mode.IN and is not of type javax.xml.ws.Holder.
     *
     */
    public static String WEBSERVICEAP_NON_IN_PARAMETERS_MUST_BE_HOLDER(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_NON_IN_PARAMETERS_MUST_BE_HOLDER(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_FAILED_TO_FIND_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.failed.to.find.handlerchain.file", arg0, arg1);
    }

    /**
     * Cannot find HandlerChain file. class: {0}, file: {1}
     *
     */
    public static String WEBSERVICEAP_FAILED_TO_FIND_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_FAILED_TO_FIND_HANDLERCHAIN_FILE(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_OPERATION_NAME_NOT_UNIQUE(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.operation.name.not.unique", arg0, arg1, arg2);
    }

    /**
     * Operation names must be unique.  Class: {0} method: {1} operation name: {2}
     *
     */
    public static String WEBSERVICEAP_OPERATION_NAME_NOT_UNIQUE(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_OPERATION_NAME_NOT_UNIQUE(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_METHOD_NOT_IMPLEMENTED(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.method.not.implemented", arg0, arg1, arg2);
    }

    /**
     * Methods in an endpointInterface must be implemented in the implementation class.  Interface Class:{0} Implementation Class:{1} Method: {2}
     *
     */
    public static String WEBSERVICEAP_METHOD_NOT_IMPLEMENTED(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_METHOD_NOT_IMPLEMENTED(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_HEADER_PARAMETERS_MUST_HAVE_WEBPARAM_NAME(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.header.parameters.must.have.webparam.name", arg0, arg1, arg2);
    }

    /**
     * All WebParam annotations on header parameters must specify a name.  Class: {0} method {1} paramter {2}
     *
     */
    public static String WEBSERVICEAP_HEADER_PARAMETERS_MUST_HAVE_WEBPARAM_NAME(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_HEADER_PARAMETERS_MUST_HAVE_WEBPARAM_NAME(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_INVALID_HANDLERCHAIN_FILE_NOHANDLER_CONFIG(Object arg0) {
        return messageFactory.getMessage("webserviceap.invalid.handlerchain.file.nohandler-config", arg0);
    }

    /**
     * The handlerchain file {0} is invalid, it does not contain a handler-config element
     *
     */
    public static String WEBSERVICEAP_INVALID_HANDLERCHAIN_FILE_NOHANDLER_CONFIG(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_INVALID_HANDLERCHAIN_FILE_NOHANDLER_CONFIG(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_ONEWAY_OPERATION_CANNOT_DECLARE_EXCEPTIONS(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.oneway.operation.cannot.declare.exceptions", arg0, arg1, arg2);
    }

    /**
     * The method {1} of class {0} is annotated @Oneway but declares the exception {2}
     *
     */
    public static String WEBSERVICEAP_ONEWAY_OPERATION_CANNOT_DECLARE_EXCEPTIONS(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_ONEWAY_OPERATION_CANNOT_DECLARE_EXCEPTIONS(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_ONEWAY_OPERATION_CANNOT_HAVE_HOLDERS(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.oneway.operation.cannot.have.holders", arg0, arg1);
    }

    /**
     * The method {1} of class {0} is annotated @Oneway but contains inout or out paramerters (javax.xml.ws.Holder)
     *
     */
    public static String WEBSERVICEAP_ONEWAY_OPERATION_CANNOT_HAVE_HOLDERS(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_ONEWAY_OPERATION_CANNOT_HAVE_HOLDERS(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_ONEWAY_AND_NOT_ONE_IN(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.oneway.and.not.one.in", arg0, arg1);
    }

    /**
     * Document literal bare methods annotated with @javax.jws.Oneway must have one non-header IN parameter.  Class: {0} Method: {1}
     *
     */
    public static String WEBSERVICEAP_ONEWAY_AND_NOT_ONE_IN(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_ONEWAY_AND_NOT_ONE_IN(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_RPC_ENCODED_NOT_SUPPORTED(Object arg0) {
        return messageFactory.getMessage("webserviceap.rpc.encoded.not.supported", arg0);
    }

    /**
     * The {0} class has a rpc/encoded SOAPBinding.  Rpc/encoded SOAPBindings are not supported in JAXWS 2.0.
     *
     */
    public static String WEBSERVICEAP_RPC_ENCODED_NOT_SUPPORTED(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_RPC_ENCODED_NOT_SUPPORTED(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_JAVA_TYPE_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("webserviceap.java.typeNotFound", arg0);
    }

    /**
     * The type: {0} was not found in the mapping
     *
     */
    public static String WEBSERVICEAP_JAVA_TYPE_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_JAVA_TYPE_NOT_FOUND(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_INVALID_SEI_ANNOTATION(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.invalid.sei.annotation", arg0, arg1);
    }

    /**
     * The @{0} annotation cannot be used on a service endpoint interface. Class: {1}
     *
     */
    public static String WEBSERVICEAP_INVALID_SEI_ANNOTATION(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_INVALID_SEI_ANNOTATION(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_NO_WEBSERVICE_ENDPOINT_FOUND() {
        return messageFactory.getMessage("webserviceap.no.webservice.endpoint.found");
    }

    /**
     * A web service endpoint could not be found
     *
     */
    public static String WEBSERVICEAP_NO_WEBSERVICE_ENDPOINT_FOUND() {
        return localizer.localize(localizableWEBSERVICEAP_NO_WEBSERVICE_ENDPOINT_FOUND());
    }

    public static Localizable localizableWEBSERVICEAP_INVALID_WEBMETHOD_ELEMENT_WITH_EXCLUDE(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.invalid.webmethod.element.with.exclude", arg0, arg1, arg2);
    }

    /**
     * The @javax.jws.WebMethod.{0} element cannot be specified with the @javax.jws.WebMethod.exclude element. Class: {1} method: {2}
     *
     */
    public static String WEBSERVICEAP_INVALID_WEBMETHOD_ELEMENT_WITH_EXCLUDE(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_INVALID_WEBMETHOD_ELEMENT_WITH_EXCLUDE(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_COULD_NOT_FIND_TYPEDECL(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.could.not.find.typedecl", arg0, arg1);
    }

    /**
     * Could not get TypeDeclaration for: {0} in apt round: {1}
     *
     */
    public static String WEBSERVICEAP_COULD_NOT_FIND_TYPEDECL(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_COULD_NOT_FIND_TYPEDECL(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_CANNOT_HAVE_MORE_THAN_ONE_OUT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.document.literal.bare.cannot.have.more.than.one.out", arg0, arg1);
    }

    /**
     * Document literal bare methods must have a return value or one out parameter.  Class: {0} Method: {1}
     *
     */
    public static String WEBSERVICEAP_DOCUMENT_LITERAL_BARE_CANNOT_HAVE_MORE_THAN_ONE_OUT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_CANNOT_HAVE_MORE_THAN_ONE_OUT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICE_ENCODED_NOT_SUPPORTED(Object arg0, Object arg1) {
        return messageFactory.getMessage("webservice.encoded.not.supported", arg0, arg1);
    }

    /**
     * The {0} class has invalid SOAPBinding annotation. {1}/encoded SOAPBinding is not supported
     *
     */
    public static String WEBSERVICE_ENCODED_NOT_SUPPORTED(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICE_ENCODED_NOT_SUPPORTED(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_WEBSERVICE_CLASS_IS_FINAL(Object arg0) {
        return messageFactory.getMessage("webserviceap.webservice.class.is.final", arg0);
    }

    /**
     * Classes annotated with @javax.jws.WebService must not be final. Class: {0}
     *
     */
    public static String WEBSERVICEAP_WEBSERVICE_CLASS_IS_FINAL(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_WEBSERVICE_CLASS_IS_FINAL(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_WEBSERVICE_NO_DEFAULT_CONSTRUCTOR(Object arg0) {
        return messageFactory.getMessage("webserviceap.webservice.no.default.constructor", arg0);
    }

    /**
     * Classes annotated with @javax.jws.WebService must have a public default constructor. Class: {0}
     *
     */
    public static String WEBSERVICEAP_WEBSERVICE_NO_DEFAULT_CONSTRUCTOR(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_WEBSERVICE_NO_DEFAULT_CONSTRUCTOR(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_SEI_CANNOT_CONTAIN_CONSTANT_VALUES(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.sei.cannot.contain.constant.values", arg0, arg1);
    }

    /**
     * An service endpoint interface cannot contain constant declaration: Interface: {0} field: {1}.
     *
     */
    public static String WEBSERVICEAP_SEI_CANNOT_CONTAIN_CONSTANT_VALUES(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_SEI_CANNOT_CONTAIN_CONSTANT_VALUES(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_ENDPOINTINTERFACE_CLASS_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("webserviceap.endpointinterface.class.not.found", arg0);
    }

    /**
     * The endpointInterface class {0} could not be found
     *
     */
    public static String WEBSERVICEAP_ENDPOINTINTERFACE_CLASS_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_ENDPOINTINTERFACE_CLASS_NOT_FOUND(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_MUST_HAVE_ONLY_ONE_IN_PARAMETER(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.document.literal.bare.must.have.only.one.in.parameter", arg0, arg1, arg2);
    }

    /**
     * Document literal bare methods must have no more than 1 non-header in parameter. Class: {0} method: {1} number of non-header parameters: {2}
     *
     */
    public static String WEBSERVICEAP_DOCUMENT_LITERAL_BARE_MUST_HAVE_ONLY_ONE_IN_PARAMETER(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_MUST_HAVE_ONLY_ONE_IN_PARAMETER(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_INFO(Object arg0) {
        return messageFactory.getMessage("webserviceap.info", arg0);
    }

    /**
     * info: {0}
     *
     */
    public static String WEBSERVICEAP_INFO(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_INFO(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_HANDLERCLASS_NOTSPECIFIED(Object arg0) {
        return messageFactory.getMessage("webserviceap.handlerclass.notspecified", arg0);
    }

    /**
     * A handler in the HandlerChain file: {0} does not specify a handler-class
     *
     */
    public static String WEBSERVICEAP_HANDLERCLASS_NOTSPECIFIED(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_HANDLERCLASS_NOTSPECIFIED(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.invalid.sei.annotation.element", arg0, arg1);
    }

    /**
     * The @javax.jws.WebService.{0} element cannot be specified on a service endpoint interface. Class: {1}
     *
     */
    public static String WEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_INVALID_SEI_ANNOTATION_ELEMENT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_METHOD_NOT_UNIQUE(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.document.literal.bare.method.not.unique", arg0, arg1, arg2);
    }

    /**
     * Document literal bare methods must have unique parameter names.  Class: {0} method: {1} parameter name: {2}
     *
     */
    public static String WEBSERVICEAP_DOCUMENT_LITERAL_BARE_METHOD_NOT_UNIQUE(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_METHOD_NOT_UNIQUE(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_METHOD_EXCEPTION_BEAN_NAME_NOT_UNIQUE(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.method.exception.bean.name.not.unique", arg0, arg1);
    }

    /**
     * Exception bean names must be unique and must not clash with other generated classes.  Class: {0} exception {1}
     *
     */
    public static String WEBSERVICEAP_METHOD_EXCEPTION_BEAN_NAME_NOT_UNIQUE(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_METHOD_EXCEPTION_BEAN_NAME_NOT_UNIQUE(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_HOLDER_PARAMETERS_MUST_NOT_BE_IN_ONLY(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.holder.parameters.must.not.be.in.only", arg0, arg1, arg2);
    }

    /**
     * javax.xml.ws.Holder parameters must not be annotated with the WebParam.Mode.IN property.  Class: {0} method: {1} parameter: {2}
     *
     */
    public static String WEBSERVICEAP_HOLDER_PARAMETERS_MUST_NOT_BE_IN_ONLY(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_HOLDER_PARAMETERS_MUST_NOT_BE_IN_ONLY(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_DOC_BARE_AND_NO_ONE_IN(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.doc.bare.and.no.one.in", arg0, arg1);
    }

    /**
     * Document literal bare methods must have one non-header, IN/INOUT parameter.  Class: {0} Method: {1}
     *
     */
    public static String WEBSERVICEAP_DOC_BARE_AND_NO_ONE_IN(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_DOC_BARE_AND_NO_ONE_IN(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_RPC_LITERAL_WEBPARAMS_MUST_SPECIFY_NAME(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.rpc.literal.webparams.must.specify.name", arg0, arg1, arg2);
    }

    /**
     * All rpc literal WebParams must specify a name.  Class: {0} method {1} paramter {2}
     *
     */
    public static String WEBSERVICEAP_RPC_LITERAL_WEBPARAMS_MUST_SPECIFY_NAME(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_RPC_LITERAL_WEBPARAMS_MUST_SPECIFY_NAME(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_ENDPOINTINTERFACE_HAS_NO_WEBSERVICE_ANNOTATION(Object arg0) {
        return messageFactory.getMessage("webserviceap.endpointinterface.has.no.webservice.annotation", arg0);
    }

    /**
     * The endpoint interface {0} must have a WebService annotation
     *
     */
    public static String WEBSERVICEAP_ENDPOINTINTERFACE_HAS_NO_WEBSERVICE_ANNOTATION(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_ENDPOINTINTERFACE_HAS_NO_WEBSERVICE_ANNOTATION(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_CANNOT_COMBINE_HANDLERCHAIN_SOAPMESSAGEHANDLERS() {
        return messageFactory.getMessage("webserviceap.cannot.combine.handlerchain.soapmessagehandlers");
    }

    /**
     * You cannot specify both HanlderChain and SOAPMessageHandlers annotations
     *
     */
    public static String WEBSERVICEAP_CANNOT_COMBINE_HANDLERCHAIN_SOAPMESSAGEHANDLERS() {
        return localizer.localize(localizableWEBSERVICEAP_CANNOT_COMBINE_HANDLERCHAIN_SOAPMESSAGEHANDLERS());
    }

    public static Localizable localizableWEBSERVICEAP_NESTED_MODEL_ERROR(Object arg0) {
        return messageFactory.getMessage("webserviceap.nestedModelError", arg0);
    }

    /**
     * modeler error: {0}
     *
     */
    public static String WEBSERVICEAP_NESTED_MODEL_ERROR(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_NESTED_MODEL_ERROR(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_METHOD_REQUEST_WRAPPER_BEAN_NAME_NOT_UNIQUE(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.method.request.wrapper.bean.name.not.unique", arg0, arg1);
    }

    /**
     * Request wrapper bean names must be unique and must not clash with other generated classes.  Class: {0} method {1}
     *
     */
    public static String WEBSERVICEAP_METHOD_REQUEST_WRAPPER_BEAN_NAME_NOT_UNIQUE(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_METHOD_REQUEST_WRAPPER_BEAN_NAME_NOT_UNIQUE(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_WEBSERVICE_CLASS_NOT_PUBLIC(Object arg0) {
        return messageFactory.getMessage("webserviceap.webservice.class.not.public", arg0);
    }

    /**
     * Classes annotated with @javax.jws.WebService must be public. Class: {0}
     *
     */
    public static String WEBSERVICEAP_WEBSERVICE_CLASS_NOT_PUBLIC(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_WEBSERVICE_CLASS_NOT_PUBLIC(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_MIXED_BINDING_STYLE(Object arg0) {
        return messageFactory.getMessage("webserviceap.mixed.binding.style", arg0);
    }

    /**
     * Class: {0} contains mixed bindings.  SOAPBinding.Style.RPC and SOAPBinding.Style.DOCUMENT cannot be mixed.
     *
     */
    public static String WEBSERVICEAP_MIXED_BINDING_STYLE(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_MIXED_BINDING_STYLE(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_FILE_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("webserviceap.fileNotFound", arg0);
    }

    /**
     * error: file not found: {0}
     *
     */
    public static String WEBSERVICEAP_FILE_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_FILE_NOT_FOUND(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_ONEWAY_AND_OUT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.oneway.and.out", arg0, arg1);
    }

    /**
     * @Oneway methods cannot have out parameters. Class: {0} method {1}
     *
     */
    public static String WEBSERVICEAP_ONEWAY_AND_OUT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_ONEWAY_AND_OUT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_METHOD_RESPONSE_WRAPPER_BEAN_NAME_NOT_UNIQUE(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.method.response.wrapper.bean.name.not.unique", arg0, arg1);
    }

    /**
     * Response wrapper bean names must be unique and must not clash with other generated classes.  Class: {0} method {1}
     *
     */
    public static String WEBSERVICEAP_METHOD_RESPONSE_WRAPPER_BEAN_NAME_NOT_UNIQUE(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_METHOD_RESPONSE_WRAPPER_BEAN_NAME_NOT_UNIQUE(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_COMPILATION_FAILED() {
        return messageFactory.getMessage("webserviceap.compilationFailed");
    }

    /**
     * compilation failed, errors should have been reported
     *
     */
    public static String WEBSERVICEAP_COMPILATION_FAILED() {
        return localizer.localize(localizableWEBSERVICEAP_COMPILATION_FAILED());
    }

    public static Localizable localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_MUST_HAVE_ONE_IN_OR_OUT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.document.literal.bare.must.have.one.in.or.out", arg0, arg1);
    }

    /**
     * Document literal bare methods must have at least one of: a return, an in parameter or an out parameter.  Class: {0} Method: {1}
     *
     */
    public static String WEBSERVICEAP_DOCUMENT_LITERAL_BARE_MUST_HAVE_ONE_IN_OR_OUT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_DOCUMENT_LITERAL_BARE_MUST_HAVE_ONE_IN_OR_OUT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ELEMENT(Object arg0) {
        return messageFactory.getMessage("webserviceap.endpointinteface.plus.element", arg0);
    }

    /**
     * The @javax.jws.WebService.{0} element cannot be used in with @javax.jws.WebService.endpointInterface element.
     *
     */
    public static String WEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ELEMENT(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ELEMENT(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_DOC_BARE_RETURN_AND_OUT(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.doc.bare.return.and.out", arg0, arg1);
    }

    /**
     * Document/literal bare methods cannot have a return type and out parameters. Class: {0}, method: {1}
     *
     */
    public static String WEBSERVICEAP_DOC_BARE_RETURN_AND_OUT(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_DOC_BARE_RETURN_AND_OUT(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_SUCCEEDED() {
        return messageFactory.getMessage("webserviceap.succeeded");
    }

    /**
     * Success
     *
     */
    public static String WEBSERVICEAP_SUCCEEDED() {
        return localizer.localize(localizableWEBSERVICEAP_SUCCEEDED());
    }

    public static Localizable localizableWEBSERVICEAP_DOCUMENT_BARE_HOLDER_PARAMETERS_MUST_NOT_BE_INOUT(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.document.bare.holder.parameters.must.not.be.inout", arg0, arg1, arg2);
    }

    /**
     * javax.xml.ws.Holder parameters in document bare operations must be WebParam.Mode.INOUT;  Class: {0} method: {1} parameter: {2}
     *
     */
    public static String WEBSERVICEAP_DOCUMENT_BARE_HOLDER_PARAMETERS_MUST_NOT_BE_INOUT(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_DOCUMENT_BARE_HOLDER_PARAMETERS_MUST_NOT_BE_INOUT(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_WEBSERVICE_AND_WEBSERVICEPROVIDER(Object arg0) {
        return messageFactory.getMessage("webserviceap.webservice.and.webserviceprovider", arg0);
    }

    /**
     * Classes cannot be annotated with both @javax.jws.WebService and @javax.xml.ws.WebServiceProvider.  Class: {0}
     *
     */
    public static String WEBSERVICEAP_WEBSERVICE_AND_WEBSERVICEPROVIDER(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_WEBSERVICE_AND_WEBSERVICEPROVIDER(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_ENDPOINTINTERFACES_DO_NOT_MATCH(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.endpointinterfaces.do.not.match", arg0, arg1);
    }

    /**
     * The endpoint interface {0} does not match the interface {1}.
     *
     */
    public static String WEBSERVICEAP_ENDPOINTINTERFACES_DO_NOT_MATCH(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_ENDPOINTINTERFACES_DO_NOT_MATCH(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ANNOTATION(Object arg0) {
        return messageFactory.getMessage("webserviceap.endpointinteface.plus.annotation", arg0);
    }

    /**
     * The @{0} annotation cannot be used in with @javax.jws.WebService.endpointInterface element.
     *
     */
    public static String WEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ANNOTATION(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_ENDPOINTINTEFACE_PLUS_ANNOTATION(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_FAILED_TO_PARSE_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return messageFactory.getMessage("webserviceap.failed.to.parse.handlerchain.file", arg0, arg1);
    }

    /**
     * Failed to parse HandlerChain file. Class: {0}, file: {1}
     *
     */
    public static String WEBSERVICEAP_FAILED_TO_PARSE_HANDLERCHAIN_FILE(Object arg0, Object arg1) {
        return localizer.localize(localizableWEBSERVICEAP_FAILED_TO_PARSE_HANDLERCHAIN_FILE(arg0, arg1));
    }

    public static Localizable localizableWEBSERVICEAP_METHOD_PARAMETER_TYPES_CANNOT_IMPLEMENT_REMOTE(Object arg0, Object arg1, Object arg2, Object arg3) {
        return messageFactory.getMessage("webserviceap.method.parameter.types.cannot.implement.remote", arg0, arg1, arg2, arg3);
    }

    /**
     * Method parameter types cannot implement java.rmi.Remote.  Class: {0} method: {1} parameter: {2} type: {3}
     *
     */
    public static String WEBSERVICEAP_METHOD_PARAMETER_TYPES_CANNOT_IMPLEMENT_REMOTE(Object arg0, Object arg1, Object arg2, Object arg3) {
        return localizer.localize(localizableWEBSERVICEAP_METHOD_PARAMETER_TYPES_CANNOT_IMPLEMENT_REMOTE(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableWEBSERVICEAP_METHOD_RETURN_TYPE_CANNOT_IMPLEMENT_REMOTE(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("webserviceap.method.return.type.cannot.implement.remote", arg0, arg1, arg2);
    }

    /**
     * Method return types cannot implement java.rmi.Remote.  Class: {0} method: {1} return type: {2}
     *
     */
    public static String WEBSERVICEAP_METHOD_RETURN_TYPE_CANNOT_IMPLEMENT_REMOTE(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWEBSERVICEAP_METHOD_RETURN_TYPE_CANNOT_IMPLEMENT_REMOTE(arg0, arg1, arg2));
    }

    public static Localizable localizableWEBSERVICEAP_ERROR(Object arg0) {
        return messageFactory.getMessage("webserviceap.error", arg0);
    }

    /**
     * error: {0}
     *
     */
    public static String WEBSERVICEAP_ERROR(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_ERROR(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_WEBSERVICE_CLASS_IS_ABSTRACT(Object arg0) {
        return messageFactory.getMessage("webserviceap.webservice.class.is.abstract", arg0);
    }

    /**
     * Classes annotated with @javax.jws.WebService must not be abstract. Class: {0}
     *
     */
    public static String WEBSERVICEAP_WEBSERVICE_CLASS_IS_ABSTRACT(Object arg0) {
        return localizer.localize(localizableWEBSERVICEAP_WEBSERVICE_CLASS_IS_ABSTRACT(arg0));
    }

    public static Localizable localizableWEBSERVICEAP_INIT_PARAM_FORMAT_ERROR() {
        return messageFactory.getMessage("webserviceap.init_param.format.error");
    }

    /**
     * a <init-param> element must have exactly 1 <param-name> and 1 <param-value>
     *
     */
    public static String WEBSERVICEAP_INIT_PARAM_FORMAT_ERROR() {
        return localizer.localize(localizableWEBSERVICEAP_INIT_PARAM_FORMAT_ERROR());
    }

}
