/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package common.config;

/**
 * Implementation Specific Properties:
 * https://docs.oracle.com/en/java/javase/23/docs/api/java.xml/module-summary.html#IN_ISFP
 */
public class ImplProperties {
    // JDK Implementation Specific Properties, refer to module description
    public static final String[] PROPERTY_KEYS = {
        "jdk.xml.enableExtensionFunctions",
        "jdk.xml.overrideDefaultParser",
        "jdk.xml.jdkcatalog.resolve",
        "jdk.xml.dtd.support",
        "jdk.xml.entityExpansionLimit",
        "jdk.xml.totalEntitySizeLimit",
        "jdk.xml.maxGeneralEntitySizeLimit",
        "jdk.xml.maxParameterEntitySizeLimit",
        "jdk.xml.entityReplacementLimit",
        "jdk.xml.elementAttributeLimit",
        "jdk.xml.maxOccurLimit",
        "jdk.xml.maxElementDepth",
        "jdk.xml.maxXMLNameLimit",
        "jdk.xml.xpathExprGrpLimit",
        "jdk.xml.xpathExprOpLimit",
        "jdk.xml.xpathTotalOpLimit"
    };

    static final int INDEX_EXTFUNC = 0;
    static final int INDEX_OVERRIDE = 1;
    static final int INDEX_JDKCATALOG = 2;
    static final int INDEX_DTD = 3;
    static final int INDEX_EE = 4;
    static final int INDEX_TE = 5;
    static final int INDEX_GE = 6;
    static final int INDEX_PE = 7;
    static final int INDEX_ER = 8;
    static final int INDEX_ATTR = 9;
    static final int INDEX_MAXOCCUR = 10;
    static final int INDEX_DEPTH = 11;
    static final int INDEX_NAME = 12;
    static final int INDEX_XPATHGRP = 13;
    static final int INDEX_XPATHOP = 14;
    static final int INDEX_XPATHTOTAL = 15;

    /**
     * Type of properties
     */
    public static enum PropertyType {
        BOOLEAN,
        INTEGER,
        STRING;
    }

    public static final PropertyType[] PROPERTY_TYPE = new PropertyType[16];
    static {
        PROPERTY_TYPE[0] = PropertyType.BOOLEAN;
        PROPERTY_TYPE[1] = PropertyType.BOOLEAN;
        PROPERTY_TYPE[2] = PropertyType.STRING;
        PROPERTY_TYPE[3] = PropertyType.STRING;
        for (int i = 4; i < PROPERTY_TYPE.length; i++) {
            PROPERTY_TYPE[i] = PropertyType.INTEGER;
        }
    }

    public static final boolean[] propertyIsFeature ={true, true, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false};

    // default values in JDK 23 and older
    public static final int PROPERTY_VALUE_JDK23 = 0;

    // default values in JDK 24
    public static final int PROPERTY_VALUE_JDK24 = 1;

    // default values in jaxp-strict.properties.template, since JDK 23
    public static final int PROPERTY_VALUE_JDK23STRICT = 2;

    public static final String[][] PROPERTY_VALUE = {
        // default values in JDK 23 and older
        {"true", "false", "continue", "allow", "64000", "50000000",
        "0", "1000000", "3000000", "10000", "5000", "0", "1000", "10", "100", "10000"},

        // default values in JDK 24
        {"false", "false", "continue", "allow", "2500", "100000",
        "100000", "15000", "100000", "200", "5000", "100", "1000", "10", "100", "10000"},

        // default values in jaxp-strict.properties.template, since JDK 23
        {"false", "false", "strict", "allow", "2500", "100000",
        "100000", "15000", "100000", "200", "5000", "100", "1000", "10", "100", "10000"}
    };
}
