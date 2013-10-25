/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.xerces.internal.utils;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.util.SecurityManager;

/**
 * This class manages standard and implementation-specific limitations.
 *
 */
public final class XMLSecurityManager {

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
     * Limits managed by the security manager
     */
    public static enum Limit {

        ENTITY_EXPANSION_LIMIT(Constants.JDK_ENTITY_EXPANSION_LIMIT, Constants.SP_ENTITY_EXPANSION_LIMIT, 0, 64000),
        MAX_OCCUR_NODE_LIMIT(Constants.JDK_MAX_OCCUR_LIMIT, Constants.SP_MAX_OCCUR_LIMIT, 0, 5000),
        ELEMENT_ATTRIBUTE_LIMIT(Constants.JDK_ELEMENT_ATTRIBUTE_LIMIT, Constants.SP_ELEMENT_ATTRIBUTE_LIMIT, 0, 10000),
        TOTAL_ENTITY_SIZE_LIMIT(Constants.JDK_TOTAL_ENTITY_SIZE_LIMIT, Constants.SP_TOTAL_ENTITY_SIZE_LIMIT, 0, 50000000),
        GENEAL_ENTITY_SIZE_LIMIT(Constants.JDK_GENEAL_ENTITY_SIZE_LIMIT, Constants.SP_GENEAL_ENTITY_SIZE_LIMIT, 0, 0),
        PARAMETER_ENTITY_SIZE_LIMIT(Constants.JDK_PARAMETER_ENTITY_SIZE_LIMIT, Constants.SP_PARAMETER_ENTITY_SIZE_LIMIT, 0, 1000000);

        final String apiProperty;
        final String systemProperty;
        final int defaultValue;
        final int secureValue;

        Limit(String apiProperty, String systemProperty, int value, int secureValue) {
            this.apiProperty = apiProperty;
            this.systemProperty = systemProperty;
            this.defaultValue = value;
            this.secureValue = secureValue;
        }

        public boolean equalsAPIPropertyName(String propertyName) {
            return (propertyName == null) ? false : apiProperty.equals(propertyName);
        }

        public boolean equalsSystemPropertyName(String propertyName) {
            return (propertyName == null) ? false : systemProperty.equals(propertyName);
        }

        public String apiProperty() {
            return apiProperty;
        }

        String systemProperty() {
            return systemProperty;
        }

        int defaultValue() {
            return defaultValue;
        }

        int secureValue() {
            return secureValue;
        }
    }

    /**
     * Map old property names with the new ones
     */
    public static enum NameMap {

        ENTITY_EXPANSION_LIMIT(Constants.SP_ENTITY_EXPANSION_LIMIT, Constants.ENTITY_EXPANSION_LIMIT),
        MAX_OCCUR_NODE_LIMIT(Constants.SP_MAX_OCCUR_LIMIT, Constants.MAX_OCCUR_LIMIT),
        ELEMENT_ATTRIBUTE_LIMIT(Constants.SP_ELEMENT_ATTRIBUTE_LIMIT, Constants.ELEMENT_ATTRIBUTE_LIMIT);
        final String newName;
        final String oldName;

        NameMap(String newName, String oldName) {
            this.newName = newName;
            this.oldName = oldName;
        }

        String getOldName(String newName) {
            if (newName.equals(this.newName)) {
                return oldName;
            }
            return null;
        }
    }
    private static final int NO_LIMIT = 0;
    /**
     * Values of the properties
     */
    private final int[] values;
    /**
     * States of the settings for each property
     */
    private State[] states;
    /**
     * Flag indicating if secure processing is set
     */
    boolean secureProcessing;

    /**
     * States that determine if properties are set explicitly
     */
    private boolean[] isSet;


    private XMLLimitAnalyzer limitAnalyzer;
    /**
     * Index of the special entityCountInfo property
     */
    private int indexEntityCountInfo = 10000;
    private String printEntityCountInfo = "";

    /**
     * Default constructor. Establishes default values for known security
     * vulnerabilities.
     */
    public XMLSecurityManager() {
        this(false);
    }

    /**
     * Instantiate Security Manager in accordance with the status of
     * secure processing
     * @param secureProcessing
     */
    public XMLSecurityManager(boolean secureProcessing) {
        limitAnalyzer = new XMLLimitAnalyzer(this);
        values = new int[Limit.values().length];
        states = new State[Limit.values().length];
        isSet = new boolean[Limit.values().length];
        this.secureProcessing = secureProcessing;
        for (Limit limit : Limit.values()) {
            if (secureProcessing) {
                values[limit.ordinal()] = limit.secureValue;
                states[limit.ordinal()] = State.FSP;
            } else {
                values[limit.ordinal()] = limit.defaultValue();
                states[limit.ordinal()] = State.DEFAULT;
            }
        }
        //read system properties or jaxp.properties
        readSystemProperties();
    }

    /**
     * Setting FEATURE_SECURE_PROCESSING explicitly
     */
    public void setSecureProcessing(boolean secure) {
        secureProcessing = secure;
        for (Limit limit : Limit.values()) {
            if (secure) {
                setLimit(limit.ordinal(), State.FSP, limit.secureValue());
            } else {
                setLimit(limit.ordinal(), State.FSP, limit.defaultValue());
            }
        }
    }

    /**
     * Return the state of secure processing
     * @return the state of secure processing
     */
    public boolean isSecureProcessing() {
        return secureProcessing;
    }


    /**
     * Set limit by property name and state
     * @param propertyName property name
     * @param state the state of the property
     * @param value the value of the property
     * @return true if the property is managed by the security manager; false
     *              if otherwise.
     */
    public boolean setLimit(String propertyName, State state, Object value) {
        int index = getIndex(propertyName);
        if (index > -1) {
            setLimit(index, state, value);
            return true;
        }
        return false;
    }

    /**
     * Set the value for a specific limit.
     *
     * @param limit the limit
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(Limit limit, State state, int value) {
        setLimit(limit.ordinal(), state, value);
    }

    /**
     * Set the value of a property by its index
     *
     * @param index the index of the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(int index, State state, Object value) {
        if (index == indexEntityCountInfo) {
            printEntityCountInfo = (String)value;
        } else {
            int temp = 0;
            try {
                temp = Integer.parseInt((String) value);
                if (temp < 0) {
                    temp = 0;
                }
            } catch (NumberFormatException e) {}
            setLimit(index, state, temp);
        }
    }

    /**
     * Set the value of a property by its index
     *
     * @param index the index of the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(int index, State state, int value) {
        if (index == indexEntityCountInfo) {
            //if it's explicitly set, it's treated as yes no matter the value
            printEntityCountInfo = Constants.JDK_YES;
        } else {
            //only update if it shall override
            if (state.compareTo(states[index]) >= 0) {
                values[index] = value;
                states[index] = state;
                isSet[index] = true;
            }
        }
    }

    /**
     * Return the value of the specified property
     *
     * @param propertyName the property name
     * @return the value of the property as a string. If a property is managed
     * by this manager, its value shall not be null.
     */
    public String getLimitAsString(String propertyName) {
        int index = getIndex(propertyName);
        if (index > -1) {
            return getLimitValueByIndex(index);
        }

        return null;
    }
    /**
     * Return the value of the specified property
     *
     * @param limit the property
     * @return the value of the property
     */
    public int getLimit(Limit limit) {
        return values[limit.ordinal()];
    }

    /**
     * Return the value of a property by its ordinal
     *
     * @param limit the property
     * @return value of a property
     */
    public String getLimitValueAsString(Limit limit) {
        return Integer.toString(values[limit.ordinal()]);
    }

    /**
     * Return the value of a property by its ordinal
     *
     * @param index the index of a property
     * @return limit of a property as a string
     */
    public String getLimitValueByIndex(int index) {
        if (index == indexEntityCountInfo) {
            return printEntityCountInfo;
        }

        return Integer.toString(values[index]);
    }

    /**
     * Return the state of the limit property
     *
     * @param limit the limit
     * @return the state of the limit property
     */
    public State getState(Limit limit) {
        return states[limit.ordinal()];
    }

    /**
     * Return the state of the limit property
     *
     * @param limit the limit
     * @return the state of the limit property
     */
    public String getStateLiteral(Limit limit) {
        return states[limit.ordinal()].literal();
    }

    /**
     * Get the index by property name
     *
     * @param propertyName property name
     * @return the index of the property if found; return -1 if not
     */
    public int getIndex(String propertyName) {
        for (Limit limit : Limit.values()) {
            if (limit.equalsAPIPropertyName(propertyName)) {
                //internally, ordinal is used as index
                return limit.ordinal();
            }
        }
        //special property to return entity count info
        if (propertyName.equals(Constants.JDK_ENTITY_COUNT_INFO)) {
            return indexEntityCountInfo;
        }
        return -1;
    }

    /**
     * Check if there's no limit defined by the Security Manager
     * @param limit
     * @return
     */
    public boolean isNoLimit(int limit) {
        return limit==NO_LIMIT;
    }
    /**
     * Check if the size (length or count) of the specified limit property is
     * over the limit
     *
     * @param limit the type of the limit property
     * @param entityName the name of the entity
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(Limit limit, String entityName, int size) {
        return isOverLimit(limit.ordinal(), entityName, size);
    }

    /**
     * Check if the value (length or count) of the specified limit property is
     * over the limit
     *
     * @param index the index of the limit property
     * @param entityName the name of the entity
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(int index, String entityName, int size) {
        if (values[index] == NO_LIMIT) {
            return false;
        }
        if (size > values[index]) {
            limitAnalyzer.addValue(index, entityName, size);
            return true;
        }
        return false;
    }

    /**
     * Check against cumulated value
     *
     * @param limit the type of the limit property
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(Limit limit) {
        return isOverLimit(limit.ordinal());
    }

    public boolean isOverLimit(int index) {
        if (values[index] == NO_LIMIT) {
            return false;
        }

        if (index==Limit.ELEMENT_ATTRIBUTE_LIMIT.ordinal() ||
                index==Limit.ENTITY_EXPANSION_LIMIT.ordinal() ||
                index==Limit.TOTAL_ENTITY_SIZE_LIMIT.ordinal()) {
            return (limitAnalyzer.getTotalValue(index) > values[index]);
        } else {
            return (limitAnalyzer.getValue(index) > values[index]);
        }
    }

    public void debugPrint() {
        if (printEntityCountInfo.equals(Constants.JDK_YES)) {
            limitAnalyzer.debugPrint();
        }
    }

    /**
     * Return the limit analyzer
     *
     * @return the limit analyzer
     */
    public XMLLimitAnalyzer getLimitAnalyzer() {
        return limitAnalyzer;
    }

    /**
     * Set limit analyzer
     *
     * @param analyzer a limit analyzer
     */
    public void setLimitAnalyzer(XMLLimitAnalyzer analyzer) {
        limitAnalyzer = analyzer;
    }

    /**
     * Indicate if a property is set explicitly
     * @param index
     * @return
     */
    public boolean isSet(int index) {
        return isSet[index];
    }

    public boolean printEntityCountInfo() {
        return printEntityCountInfo.equals(Constants.JDK_YES);
    }

    /**
     * Read from system properties, or those in jaxp.properties
     */
    private void readSystemProperties() {

        for (Limit limit : Limit.values()) {
            if (!getSystemProperty(limit, limit.systemProperty())) {
                //if system property is not found, try the older form if any
                for (NameMap nameMap : NameMap.values()) {
                    String oldName = nameMap.getOldName(limit.systemProperty());
                    if (oldName != null) {
                        getSystemProperty(limit, oldName);
                    }
                }
            }
        }

    }

    /**
     * Read from system properties, or those in jaxp.properties
     *
     * @param property the type of the property
     * @param sysPropertyName the name of system property
     */
    private boolean getSystemProperty(Limit limit, String sysPropertyName) {
        try {
            String value = SecuritySupport.getSystemProperty(sysPropertyName);
            if (value != null && !value.equals("")) {
                values[limit.ordinal()] = Integer.parseInt(value);
                states[limit.ordinal()] = State.SYSTEMPROPERTY;
                return true;
            }

            value = SecuritySupport.readJAXPProperty(sysPropertyName);
            if (value != null && !value.equals("")) {
                values[limit.ordinal()] = Integer.parseInt(value);
                states[limit.ordinal()] = State.JAXPDOTPROPERTIES;
                return true;
            }
        } catch (NumberFormatException e) {
            //invalid setting
            throw new NumberFormatException("Invalid setting for system property: " + limit.systemProperty());
        }
        return false;
    }


    /**
     * Convert a value set through setProperty to XMLSecurityManager.
     * If the value is an instance of XMLSecurityManager, use it to override the default;
     * If the value is an old SecurityManager, convert to the new XMLSecurityManager.
     *
     * @param value user specified security manager
     * @param securityManager an instance of XMLSecurityManager
     * @return an instance of the new security manager XMLSecurityManager
     */
    static public XMLSecurityManager convert(Object value, XMLSecurityManager securityManager) {
        if (value == null) {
            if (securityManager == null) {
                securityManager = new XMLSecurityManager(true);
            }
            return securityManager;
        }
        if (XMLSecurityManager.class.isAssignableFrom(value.getClass())) {
            return (XMLSecurityManager)value;
        } else {
            if (securityManager == null) {
                securityManager = new XMLSecurityManager(true);
            }
            if (SecurityManager.class.isAssignableFrom(value.getClass())) {
                SecurityManager origSM = (SecurityManager)value;
                securityManager.setLimit(Limit.MAX_OCCUR_NODE_LIMIT, State.APIPROPERTY, origSM.getMaxOccurNodeLimit());
                securityManager.setLimit(Limit.ENTITY_EXPANSION_LIMIT, State.APIPROPERTY, origSM.getEntityExpansionLimit());
                securityManager.setLimit(Limit.ELEMENT_ATTRIBUTE_LIMIT, State.APIPROPERTY, origSM.getElementAttrLimit());
            }
            return securityManager;
        }
    }
}
