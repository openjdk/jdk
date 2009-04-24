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
package com.sun.tools.internal.ws.processor.modeler.annotation;


/**
 *
 * @author  dkohlert
 */
public interface WebServiceConstants { //extends RmiConstants {

    public static final String RETURN                       = "return";
    public static final String RETURN_CAPPED                = "Return";
    public static final String RETURN_VALUE                 = "_return";
    public static final String SERVICE                      = "Service";
    public static final String PD                           = ".";
    public static final String JAXWS                        = "jaxws";
    public static final String JAXWS_PACKAGE_PD             = JAXWS+PD;
    public static final String PD_JAXWS_PACKAGE_PD          = PD+JAXWS+PD;
    public static final String BEAN                         = "Bean";
    public static final String GET_PREFIX                   = "get";
    public static final String IS_PREFIX                    = "is";
    public static final String FAULT_INFO                   = "faultInfo";
    public static final String GET_FAULT_INFO               = "getFaultInfo";
    public static final String HTTP_PREFIX                  = "http://";
    public static final String JAVA_LANG_OBJECT             = "java.lang.Object";
    public static final String EMTPY_NAMESPACE_ID           = "";


    public static final char SIGC_INNERCLASS  = '$';
    public static final char SIGC_UNDERSCORE  = '_';

    public static final String DOT = ".";
    public static final String PORT = "WSDLPort";
    public static final String BINDING = "Binding";
    public static final String RESPONSE = "Response";

    /*
     * Identifiers potentially useful for all Generators
     */
    public static final String EXCEPTION_CLASSNAME =
        java.lang.Exception.class.getName();
    public static final String REMOTE_CLASSNAME =
        java.rmi.Remote.class.getName();
    public static final String REMOTE_EXCEPTION_CLASSNAME =
        java.rmi.RemoteException.class.getName();
    public static final String RUNTIME_EXCEPTION_CLASSNAME =
        java.lang.RuntimeException.class.getName();
    public static final String SERIALIZABLE_CLASSNAME =
        java.io.Serializable.class.getName();
    public static final String HOLDER_CLASSNAME =
        javax.xml.ws.Holder.class.getName();
    public static final String COLLECTION_CLASSNAME =
        java.util.Collection.class.getName();
    public static final String MAP_CLASSNAME =
        java.util.Map.class.getName();


    // 181 constants
    public static final String WEBSERVICE_NAMESPACE         = "http://www.bea.com/xml/ns/jws";
    public static final String HANDLER_CONFIG               = "handler-config";
    public static final String HANDLER_CHAIN                = "handler-chain";
    public static final String HANDLER_CHAIN_NAME           = "handler-chain-name";
    public static final String HANDLER                      = "handler";
    public static final String HANDLER_NAME                 = "handler-name";
    public static final String HANDLER_CLASS                = "handler-class";
    public static final String INIT_PARAM                   = "init-param";
    public static final String SOAP_ROLE                    = "soap-role";
    public static final String SOAP_HEADER                  = "soap-header";
    public static final String PARAM_NAME                   = "param-name";
    public static final String PARAM_VALUE                  = "param-value";
}
