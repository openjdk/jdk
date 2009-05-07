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
package com.sun.xml.internal.ws.resources;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class ModelerMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.modeler");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableNESTED_MODELER_ERROR(Object arg0) {
        return messageFactory.getMessage("nestedModelerError", arg0);
    }

    /**
     * runtime modeler error: {0}
     *
     */
    public static String NESTED_MODELER_ERROR(Object arg0) {
        return localizer.localize(localizableNESTED_MODELER_ERROR(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_WSFEATURE_NO_FTRCONSTRUCTOR(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.wsfeature.no.ftrconstructor", arg0, arg1);
    }

    /**
     * Annotation {0} is not recognizable, atleast one constructor of {1} should be marked with @FeatureConstructor
     *
     */
    public static String RUNTIME_MODELER_WSFEATURE_NO_FTRCONSTRUCTOR(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_WSFEATURE_NO_FTRCONSTRUCTOR(arg0, arg1));
    }

    public static Localizable localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_PUBLIC(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.webmethod.must.be.public", arg0);
    }

    /**
     * @WebMethod is not allowed on a non-public method {0}
     *
     */
    public static String RUNTIME_MODELER_WEBMETHOD_MUST_BE_PUBLIC(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_PUBLIC(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_WRAPPER_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.wrapper.not.found", arg0);
    }

    /**
     * Wrapper class {0} is not found. Have you run APT to generate them?
     *
     */
    public static String RUNTIME_MODELER_WRAPPER_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_WRAPPER_NOT_FOUND(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_MTOM_CONFLICT(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.mtom.conflict", arg0, arg1);
    }

    /**
     * Error in  @BindingType: MTOM Configuration in binding identifier {0} conflicts with feature @MTOM {1}
     *
     */
    public static String RUNTIME_MODELER_MTOM_CONFLICT(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_MTOM_CONFLICT(arg0, arg1));
    }

    public static Localizable localizableRUNTIME_MODELER_FEATURE_CONFLICT(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.feature.conflict", arg0, arg1);
    }

    /**
     * Feature {0} in implementation conflicts with {1} in WSDL configuration
     *
     */
    public static String RUNTIME_MODELER_FEATURE_CONFLICT(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_FEATURE_CONFLICT(arg0, arg1));
    }

    public static Localizable localizableRUNTIME_MODELER_WSFEATURE_MORETHANONE_FTRCONSTRUCTOR(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.wsfeature.morethanone.ftrconstructor", arg0, arg1);
    }

    /**
     * Annotation {0} is illegal, Only one constructor of {1} can be marked as @FeatureConstructor
     *
     */
    public static String RUNTIME_MODELER_WSFEATURE_MORETHANONE_FTRCONSTRUCTOR(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_WSFEATURE_MORETHANONE_FTRCONSTRUCTOR(arg0, arg1));
    }

    public static Localizable localizableNOT_A_VALID_BARE_METHOD(Object arg0, Object arg1) {
        return messageFactory.getMessage("not.a.valid.bare.method", arg0, arg1);
    }

    /**
     * SEI {0} has method {1} annotated as BARE but it has more than one parameter bound to body. This is invalid. Please annotate the method with annotation: @SOAPBinding(parameterStyle=SOAPBinding.ParameterStyle.WRAPPED)
     *
     */
    public static String NOT_A_VALID_BARE_METHOD(Object arg0, Object arg1) {
        return localizer.localize(localizableNOT_A_VALID_BARE_METHOD(arg0, arg1));
    }

    public static Localizable localizableRUNTIME_MODELER_NO_PACKAGE(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.no.package", arg0);
    }

    /**
     * A @WebService.targetNamespace must be specified on classes with no package.  Class: {0}
     *
     */
    public static String RUNTIME_MODELER_NO_PACKAGE(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_NO_PACKAGE(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_NO_WEBSERVICE_ANNOTATION(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.no.webservice.annotation", arg0);
    }

    /**
     * A WebService annotation is not present on class: {0}
     *
     */
    public static String RUNTIME_MODELER_NO_WEBSERVICE_ANNOTATION(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_NO_WEBSERVICE_ANNOTATION(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_ONEWAY_OPERATION_NO_OUT_PARAMETERS(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.oneway.operation.no.out.parameters", arg0, arg1);
    }

    /**
     * oneway operation should not have out parameters class: {0} method: {1}
     *
     */
    public static String RUNTIME_MODELER_ONEWAY_OPERATION_NO_OUT_PARAMETERS(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_ONEWAY_OPERATION_NO_OUT_PARAMETERS(arg0, arg1));
    }

    public static Localizable localizableUNABLE_TO_CREATE_JAXB_CONTEXT() {
        return messageFactory.getMessage("unable.to.create.JAXBContext");
    }

    /**
     * Unable to create JAXBContext due to the security restriction
     *
     */
    public static String UNABLE_TO_CREATE_JAXB_CONTEXT() {
        return localizer.localize(localizableUNABLE_TO_CREATE_JAXB_CONTEXT());
    }

    public static Localizable localizableRUNTIME_MODELER_NO_OPERATIONS(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.no.operations", arg0);
    }

    /**
     * The web service defined by the class {0} does not contain any valid WebMethods.
     *
     */
    public static String RUNTIME_MODELER_NO_OPERATIONS(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_NO_OPERATIONS(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_NONSTATIC(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.webmethod.must.be.nonstatic", arg0);
    }

    /**
     * @WebMethod is not allowed on a static method {0}
     *
     */
    public static String RUNTIME_MODELER_WEBMETHOD_MUST_BE_NONSTATIC(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_NONSTATIC(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_METHOD_NOT_FOUND(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.method.not.found", arg0, arg1);
    }

    /**
     * method: {0} could not be found on class: {1}
     *
     */
    public static String RUNTIME_MODELER_METHOD_NOT_FOUND(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_METHOD_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableRUNTIME_MODELER_CLASS_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.class.not.found", arg0);
    }

    /**
     * class: {0} could not be found
     *
     */
    public static String RUNTIME_MODELER_CLASS_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_CLASS_NOT_FOUND(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_CANNOT_GET_SERVICE_NAME_FROM_INTERFACE(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.cannot.get.serviceName.from.interface", arg0);
    }

    /**
     * The serviceName cannot be retrieved from an interface.  class {0}
     *
     */
    public static String RUNTIME_MODELER_CANNOT_GET_SERVICE_NAME_FROM_INTERFACE(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_CANNOT_GET_SERVICE_NAME_FROM_INTERFACE(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_ENDPOINT_INTERFACE_NO_WEBSERVICE(Object arg0) {
        return messageFactory.getMessage("runtime.modeler.endpoint.interface.no.webservice", arg0);
    }

    /**
     * The Endpoint Interface: {0} does not have WebService Annotation
     *
     */
    public static String RUNTIME_MODELER_ENDPOINT_INTERFACE_NO_WEBSERVICE(Object arg0) {
        return localizer.localize(localizableRUNTIME_MODELER_ENDPOINT_INTERFACE_NO_WEBSERVICE(arg0));
    }

    public static Localizable localizableRUNTIME_MODELER_PORTNAME_SERVICENAME_NAMESPACE_MISMATCH(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.portname.servicename.namespace.mismatch", arg0, arg1);
    }

    /**
     * The namespace of the serviceName "{0}" and the namespace of the portName "{1}" must match
     *
     */
    public static String RUNTIME_MODELER_PORTNAME_SERVICENAME_NAMESPACE_MISMATCH(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_PORTNAME_SERVICENAME_NAMESPACE_MISMATCH(arg0, arg1));
    }

    public static Localizable localizableRUNTIME_MODELER_WSFEATURE_ILLEGAL_FTRCONSTRUCTOR(Object arg0, Object arg1) {
        return messageFactory.getMessage("runtime.modeler.wsfeature.illegal.ftrconstructor", arg0, arg1);
    }

    /**
     * Annotation {0} is illegal, In {1} @FeatureConstructor value does n't match the constructor parameters
     *
     */
    public static String RUNTIME_MODELER_WSFEATURE_ILLEGAL_FTRCONSTRUCTOR(Object arg0, Object arg1) {
        return localizer.localize(localizableRUNTIME_MODELER_WSFEATURE_ILLEGAL_FTRCONSTRUCTOR(arg0, arg1));
    }

}
