/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.xalan.internal;

import com.sun.org.apache.xalan.internal.utils.SecuritySupport;

/**
 * Commonly used constants.
 *
 * @author Huizhe Wang, Oracle
 *
 */
public final class XalanConstants {

    //
    // Constants
    //
    //Xerces security manager
    public static final String SECURITY_MANAGER =
            "http://apache.org/xml/properties/security-manager";

    //
    // Implementation limits: API properties
    //
    /** Oracle JAXP property prefix ("http://www.oracle.com/xml/jaxp/properties/"). */
    public static final String ORACLE_JAXP_PROPERTY_PREFIX =
        "http://www.oracle.com/xml/jaxp/properties/";
    /**
     * JDK entity expansion limit; Note that the existing system property
     * "entityExpansionLimit" with no prefix is still observed
     */
    public static final String JDK_ENTITY_EXPANSION_LIMIT =
            ORACLE_JAXP_PROPERTY_PREFIX + "entityExpansionLimit";

    /**
     * JDK element attribute limit; Note that the existing system property
     * "elementAttributeLimit" with no prefix is still observed
     */
    public static final String JDK_ELEMENT_ATTRIBUTE_LIMIT =
            ORACLE_JAXP_PROPERTY_PREFIX + "elementAttributeLimit";

    /**
     * JDK maxOccur limit; Note that the existing system property
     * "maxOccurLimit" with no prefix is still observed
     */
    public static final String JDK_MAX_OCCUR_LIMIT =
            ORACLE_JAXP_PROPERTY_PREFIX + "maxOccurLimit";

    /**
     * JDK total entity size limit
     */
    public static final String JDK_TOTAL_ENTITY_SIZE_LIMIT =
            ORACLE_JAXP_PROPERTY_PREFIX + "totalEntitySizeLimit";

    /**
     * JDK maximum general entity size limit
     */
    public static final String JDK_GENERAL_ENTITY_SIZE_LIMIT =
            ORACLE_JAXP_PROPERTY_PREFIX + "maxGeneralEntitySizeLimit";
    /**
     * JDK maximum parameter entity size limit
     */
    public static final String JDK_PARAMETER_ENTITY_SIZE_LIMIT =
            ORACLE_JAXP_PROPERTY_PREFIX + "maxParameterEntitySizeLimit";
    /**
     * JDK maximum XML name limit
     */
    public static final String JDK_XML_NAME_LIMIT =
            ORACLE_JAXP_PROPERTY_PREFIX + "maxXMLNameLimit";

    /**
     * JDK maxElementDepth limit
     */
    public static final String JDK_MAX_ELEMENT_DEPTH =
            ORACLE_JAXP_PROPERTY_PREFIX + "maxElementDepth";

    /**
     * JDK property indicating whether the parser shall print out entity
     * count information
     * Value: a string "yes" means print, "no" or any other string means not.
     */
    public static final String JDK_ENTITY_COUNT_INFO =
            ORACLE_JAXP_PROPERTY_PREFIX + "getEntityCountInfo";

    //
    // Implementation limits: corresponding System Properties of the above
    // API properties
    //
    /**
     * JDK entity expansion limit; Note that the existing system property
     * "entityExpansionLimit" with no prefix is still observed
     */
    public static final String SP_ENTITY_EXPANSION_LIMIT = "jdk.xml.entityExpansionLimit";

    /**
     * JDK element attribute limit; Note that the existing system property
     * "elementAttributeLimit" with no prefix is still observed
     */
    public static final String SP_ELEMENT_ATTRIBUTE_LIMIT =  "jdk.xml.elementAttributeLimit";

    /**
     * JDK maxOccur limit; Note that the existing system property
     * "maxOccurLimit" with no prefix is still observed
     */
    public static final String SP_MAX_OCCUR_LIMIT = "jdk.xml.maxOccurLimit";

    /**
     * JDK total entity size limit
     */
    public static final String SP_TOTAL_ENTITY_SIZE_LIMIT = "jdk.xml.totalEntitySizeLimit";

    /**
     * JDK maximum general entity size limit
     */
    public static final String SP_GENERAL_ENTITY_SIZE_LIMIT = "jdk.xml.maxGeneralEntitySizeLimit";
    /**
     * JDK maximum parameter entity size limit
     */
    public static final String SP_PARAMETER_ENTITY_SIZE_LIMIT = "jdk.xml.maxParameterEntitySizeLimit";
    /**
     * JDK maximum XML name limit
     */
    public static final String SP_XML_NAME_LIMIT = "jdk.xml.maxXMLNameLimit";

    /**
     * JDK maxElementDepth limit
     */
    public static final String SP_MAX_ELEMENT_DEPTH = "jdk.xml.maxElementDepth";

    /**
     * JDK TransformerFactory and Transformer attribute that specifies a class
     * loader that will be used for extension functions class loading
     * Value: a "null", the default value, means that the default EF class loading
     * path will be used.
     * Instance of ClassLoader: the specified instance of ClassLoader will be used
     * for extension functions loading during translation process
     */
    public static final String JDK_EXTENSION_CLASSLOADER = "jdk.xml.transform.extensionClassLoader";

    //legacy System Properties
    public final static String ENTITY_EXPANSION_LIMIT = "entityExpansionLimit";
    public static final String ELEMENT_ATTRIBUTE_LIMIT = "elementAttributeLimit" ;
    public final static String MAX_OCCUR_LIMIT = "maxOccurLimit";

    /**
     * A string "yes" that can be used for properties such as getEntityCountInfo
     */
    public static final String JDK_YES = "yes";

    // Oracle Feature:
    /**
     * <p>Use Service Mechanism</p>
     *
     * <ul>
     *   <li>
         * {@code true} instruct an object to use service mechanism to
         * find a service implementation. This is the default behavior.
         *   </li>
         *   <li>
         * {@code false} instruct an object to skip service mechanism and
         * use the default implementation for that service.
         *   </li>
         * </ul>
         */
    public static final String ORACLE_FEATURE_SERVICE_MECHANISM = "http://www.oracle.com/feature/use-service-mechanism";


    //System Properties corresponding to ACCESS_EXTERNAL_* properties
    public static final String SP_ACCESS_EXTERNAL_STYLESHEET = "javax.xml.accessExternalStylesheet";
    public static final String SP_ACCESS_EXTERNAL_DTD = "javax.xml.accessExternalDTD";

    //all access keyword
    public static final String ACCESS_EXTERNAL_ALL = "all";

    /**
     * Default value when FEATURE_SECURE_PROCESSING (FSP) is set to true
     */
    public static final String EXTERNAL_ACCESS_DEFAULT_FSP = "";

    /**
     * FEATURE_SECURE_PROCESSING (FSP) is false by default
     */
    public static final String EXTERNAL_ACCESS_DEFAULT = ACCESS_EXTERNAL_ALL;

    public static final String XML_SECURITY_PROPERTY_MANAGER =
            ORACLE_JAXP_PROPERTY_PREFIX + "xmlSecurityPropertyManager";

    /**
     * Feature enableExtensionFunctions
     */
    public static final String ORACLE_ENABLE_EXTENSION_FUNCTION =
            ORACLE_JAXP_PROPERTY_PREFIX + "enableExtensionFunctions";
    public static final String SP_ORACLE_ENABLE_EXTENSION_FUNCTION = "javax.xml.enableExtensionFunctions";

    /**
     * Values for a feature
     */
    public static final String FEATURE_TRUE = "true";
    public static final String FEATURE_FALSE = "false";

    /**
     * Check if we're in jdk8 or above
     */
    public static final boolean IS_JDK8_OR_ABOVE = isJavaVersionAtLeast(8);

    /*
     * Check the major version of the current JDK against that specified
     * in the parameter
     *
     * In JDK9 the java version string was changed to comply with JEP-223
     * so this method was modified to handle that new format as well
     *
     * @param compareTo a JDK major version to be compared to
     * @return true if the current major version is the same or above
     * that represented by the parameter
     */
    public static boolean isJavaVersionAtLeast(int compareTo) {
        String javaVersion = SecuritySupport.getSystemProperty("java.version");
        javaVersion = (javaVersion.matches("[1-9][0-9]*(\\.(0|[1-9][0-9]*))*\\-.*")) ?
                          javaVersion.split("-|\\.")[0] :
                          javaVersion.split("\\.", 3)[1];
        return Integer.parseInt(javaVersion) >= compareTo;
    }
} // class Constants
