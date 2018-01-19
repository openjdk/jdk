/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.xml.internal;

import javax.xml.XMLConstants;
import static jdk.xml.internal.JdkXmlUtils.OVERRIDE_PARSER;
import static jdk.xml.internal.JdkXmlUtils.SP_USE_CATALOG;
import static jdk.xml.internal.JdkXmlUtils.RESET_SYMBOL_TABLE;

/**
 * This class manages JDK's XML Features. Previously added features and properties
 * may be gradually moved to this class.
 */
public class JdkXmlFeatures {
    public static final String ORACLE_JAXP_PROPERTY_PREFIX =
        "http://www.oracle.com/xml/jaxp/properties/";

    public static final String XML_FEATURE_MANAGER =
            ORACLE_JAXP_PROPERTY_PREFIX + "XmlFeatureManager";

    public static final String ORACLE_FEATURE_SERVICE_MECHANISM =
            "http://www.oracle.com/feature/use-service-mechanism";

    /**
     * Feature enableExtensionFunctions
     */
    public static final String ORACLE_ENABLE_EXTENSION_FUNCTION =
            ORACLE_JAXP_PROPERTY_PREFIX + "enableExtensionFunctions";
    public static final String SP_ENABLE_EXTENSION_FUNCTION =
            "javax.xml.enableExtensionFunctions";
    // This is the correct name by the spec
    public static final String SP_ENABLE_EXTENSION_FUNCTION_SPEC =
            "jdk.xml.enableExtensionFunctions";
    public static final String CATALOG_FEATURES = "javax.xml.catalog.catalogFeatures";

    public final static String PROPERTY_USE_CATALOG = XMLConstants.USE_CATALOG;

    public static enum XmlFeature {
        /**
         * Feature enableExtensionFunctions
         * FSP: extension function is enforced by FSP. When FSP is on, extension
         * function is disabled.
         */
        ENABLE_EXTENSION_FUNCTION(ORACLE_ENABLE_EXTENSION_FUNCTION, SP_ENABLE_EXTENSION_FUNCTION_SPEC,
                ORACLE_ENABLE_EXTENSION_FUNCTION, SP_ENABLE_EXTENSION_FUNCTION,
                true, false, true, true),
        /**
         * The {@link javax.xml.XMLConstants.USE_CATALOG} feature.
         * FSP: USE_CATALOG is not enforced by FSP.
         */
        USE_CATALOG(PROPERTY_USE_CATALOG, SP_USE_CATALOG,
                null, null,
                true, false, true, false),

        /**
         * Feature resetSymbolTable
         * FSP: RESET_SYMBOL_TABLE_FEATURE is not enforced by FSP.
         */
        RESET_SYMBOL_TABLE_FEATURE(RESET_SYMBOL_TABLE, RESET_SYMBOL_TABLE,
                null, null,
                false, false, true, false),

        /**
         * Feature overrideDefaultParser
         * FSP: not enforced by FSP.
         */
        JDK_OVERRIDE_PARSER(OVERRIDE_PARSER, OVERRIDE_PARSER,
                ORACLE_FEATURE_SERVICE_MECHANISM, ORACLE_FEATURE_SERVICE_MECHANISM,
                false, false, true, false);

        private final String name;
        private final String nameSP;
        private final String nameOld;
        private final String nameOldSP;
        private final boolean valueDefault;
        private final boolean valueEnforced;
        private final boolean hasSystem;
        private final boolean enforced;

        /**
         * Constructs an XmlFeature instance.
         * @param name the name of the feature
         * @param nameSP the name of the System Property
         * @param nameOld the name of the corresponding legacy property
         * @param nameOldSP the system property of the legacy property
         * @param value the value of the feature
         * @param hasSystem a flag to indicate whether the feature is supported
         * @param enforced a flag indicating whether the feature is
         * FSP (Feature_Secure_Processing) enforced
         * with a System property
         */
        XmlFeature(String name, String nameSP, String nameOld, String nameOldSP,
                boolean value, boolean valueEnforced, boolean hasSystem, boolean enforced) {
            this.name = name;
            this.nameSP = nameSP;
            this.nameOld = nameOld;
            this.nameOldSP = nameOldSP;
            this.valueDefault = value;
            this.valueEnforced = valueEnforced;
            this.hasSystem = hasSystem;
            this.enforced = enforced;
        }

        /**
         * Checks whether the specified property is equal to the current property.
         * @param propertyName the name of a property
         * @return true if the specified property is the current property, false
         * otherwise
         */
        boolean equalsPropertyName(String propertyName) {
            return name.equals(propertyName) ||
                    (nameOld != null && nameOld.equals(propertyName));
        }

        /**
         * Returns the name of the property.
         *
         * @return the name of the property
         */
        public String apiProperty() {
            return name;
        }

        /**
         * Returns the name of the corresponding System Property.
         *
         * @return the name of the System Property
         */
        String systemProperty() {
            return nameSP;
        }

        /**
         * Returns the name of the legacy System Property.
         *
         * @return the name of the legacy System Property
         */
        String systemPropertyOld() {
            return nameOldSP;
        }

        /**
         * Returns the default value of the property.
         * @return the default value of the property
         */
        public boolean defaultValue() {
            return valueDefault;
        }

        /**
         * Returns the FSP-enforced value.
         * @return the FSP-enforced value
         */
        public boolean enforcedValue() {
            return valueEnforced;
        }

        /**
         * Checks whether System property is supported for the feature.
         * @return true it is supported, false otherwise
         */
        boolean hasSystemProperty() {
            return hasSystem;
        }

        /**
         * Checks whether the property is enforced by FSP
         * @return true it is, false otherwise
         */
        boolean enforced() {
            return enforced;
        }

    }

    /**
     * States of the settings of a property, in the order: default value, value
     * set by FEATURE_SECURE_PROCESSING, jaxp.properties file, jaxp system
     * properties, and jaxp api properties
     */
    public static enum State {
        //this order reflects the overriding order

        DEFAULT("default"), FSP("FEATURE_SECURE_PROCESSING"),
        JAXPDOTPROPERTIES("jaxp.properties"), SYSTEMPROPERTY("system property"),
        APIPROPERTY("property");

        final String literal;
        State(String literal) {
            this.literal = literal;
        }

        String literal() {
            return literal;
        }
    }

    /**
     * Values of the features
     */
    private final boolean[] featureValues;

    /**
     * States of the settings for each property
     */
    private final State[] states;

    /**
     * Flag indicating if secure processing is set
     */
    boolean secureProcessing;

    /**
     * Instantiate JdkXmlFeatures and initialize the fields
     * @param secureProcessing
     */
    public JdkXmlFeatures(boolean secureProcessing) {
        featureValues = new boolean[XmlFeature.values().length];
        states = new State[XmlFeature.values().length];
        this.secureProcessing = secureProcessing;
        for (XmlFeature f : XmlFeature.values()) {
            if (secureProcessing && f.enforced()) {
                featureValues[f.ordinal()] = f.enforcedValue();
                states[f.ordinal()] = State.FSP;
            } else {
                featureValues[f.ordinal()] = f.defaultValue();
                states[f.ordinal()] = State.DEFAULT;
            }
        }
        //read system properties or jaxp.properties
        readSystemProperties();
    }

    /**
     * Updates the JdkXmlFeatures instance by reading the system properties again.
     * This will become necessary in case the system properties are set after
     * the instance has been created.
     */
    public void update() {
        readSystemProperties();
    }

    /**
     * Set feature by property name and state
     * @param propertyName property name
     * @param state the state of the property
     * @param value the value of the property
     * @return true if the property is managed by the JdkXmlFeatures instance;
     *         false otherwise.
     */
    public boolean setFeature(String propertyName, State state, Object value) {
        int index = getIndex(propertyName);
        if (index > -1) {
            setFeature(index, state, value);
            return true;
        }
        return false;
    }

    /**
     * Set the value for a specific feature.
     *
     * @param feature the feature
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setFeature(XmlFeature feature, State state, boolean value) {
        setFeature(feature.ordinal(), state, value);
    }

    /**
     * Return the value of the specified property
     *
     * @param feature the property
     * @return the value of the property
     */
    public boolean getFeature(XmlFeature feature) {
        return featureValues[feature.ordinal()];
    }

    /**
     * Return the value of a feature by its index (the Feature's ordinal)
     * @param index the index of a feature
     * @return value of a feature
     */
    public boolean getFeature(int index) {
        return featureValues[index];
    }

    /**
     * Set the value of a property by its index
     *
     * @param index the index of the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setFeature(int index, State state, Object value) {
        boolean temp;
        if (Boolean.class.isAssignableFrom(value.getClass())) {
            temp = (Boolean)value;
        } else {
            temp = Boolean.parseBoolean((String) value);
        }
        setFeature(index, state, temp);
    }

    /**
     * Set the value of a property by its index
     *
     * @param index the index of the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setFeature(int index, State state, boolean value) {
        //only update if it shall override
        if (state.compareTo(states[index]) >= 0) {
            featureValues[index] = value;
            states[index] = state;
        }
    }

    /**
     * Get the index by property name
     *
     * @param propertyName property name
     * @return the index of the property if found; return -1 if not
     */
    public int getIndex(String propertyName) {
        for (XmlFeature feature : XmlFeature.values()) {
            if (feature.equalsPropertyName(propertyName)) {
                //internally, ordinal is used as index
                return feature.ordinal();
            }
        }
        return -1;
    }

    /**
     * Read from system properties, or those in jaxp.properties
     */
    private void readSystemProperties() {
        for (XmlFeature feature : XmlFeature.values()) {
            if (!getSystemProperty(feature, feature.systemProperty())) {
                //if system property is not found, try the older form if any
                String oldName = feature.systemPropertyOld();
                if (oldName != null) {
                    getSystemProperty(feature, oldName);
                }
            }
        }
    }

    /**
     * Read from system properties, or those in jaxp.properties
     *
     * @param property the type of the property
     * @param sysPropertyName the name of system property
     * @return true if the system property is found, false otherwise
     */
    private boolean getSystemProperty(XmlFeature feature, String sysPropertyName) {
        try {
            String value = SecuritySupport.getSystemProperty(sysPropertyName);
            if (value != null && !value.equals("")) {
                setFeature(feature, State.SYSTEMPROPERTY, Boolean.parseBoolean(value));
                return true;
            }

            value = SecuritySupport.readJAXPProperty(sysPropertyName);
            if (value != null && !value.equals("")) {
                setFeature(feature, State.JAXPDOTPROPERTIES, Boolean.parseBoolean(value));
                return true;
            }
        } catch (NumberFormatException e) {
            //invalid setting
            throw new NumberFormatException("Invalid setting for system property: " + feature.systemProperty());
        }
        return false;
    }

}
