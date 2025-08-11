/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class manages security related properties
 *
 */
public final class XMLSecurityPropertyManager extends FeaturePropertyBase implements Cloneable {

    /**
     * Properties managed by the security property manager
     */
    public static enum Property {
        ACCESS_EXTERNAL_DTD(XMLConstants.ACCESS_EXTERNAL_DTD, JdkConstants.SP_ACCESS_EXTERNAL_DTD,
                JdkConstants.EXTERNAL_ACCESS_DEFAULT),
        ACCESS_EXTERNAL_SCHEMA(XMLConstants.ACCESS_EXTERNAL_SCHEMA, JdkConstants.SP_ACCESS_EXTERNAL_SCHEMA,
                JdkConstants.EXTERNAL_ACCESS_DEFAULT),
        ACCESS_EXTERNAL_STYLESHEET(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, JdkConstants.SP_ACCESS_EXTERNAL_STYLESHEET,
                JdkConstants.EXTERNAL_ACCESS_DEFAULT);

        final String apiProperty;
        final String systemProperty;
        final String defaultValue;

        Property(String apiProperty, String systemProperty, String value) {
            this.apiProperty = apiProperty;
            this.systemProperty = systemProperty;
            this.defaultValue = value;
        }

        public boolean equalsName(String propertyName) {
            return (propertyName == null) ? false :
                    (apiProperty.equals(propertyName) || systemProperty.equals(propertyName));
        }

        public String propertyName() {
            return apiProperty;
        }
        public String systemProperty() {
            return systemProperty;
        }
        String defaultValue() {
            return defaultValue;
        }
    }

    /**
     * Default constructor. Establishes default values
     */
    public XMLSecurityPropertyManager() {
        values = new String[Property.values().length];
        for (Property property : Property.values()) {
            values[property.ordinal()] = property.defaultValue();
        }
    }

    /**
     * Returns a copy of the XMLSecurityManager.
     * @return a copy of the XMLSecurityManager
     */
    public XMLSecurityPropertyManager clone() {
        try {
            XMLSecurityPropertyManager copy = (XMLSecurityPropertyManager) super.clone();
            copy.values = this.values.clone();
            copy.states = this.states.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            // shouldn't happen as this class is Cloneable
            throw new InternalError(e);
        }
    }

    /**
     * Returns a copy of the XMLSecurityPropertyManager that is updated with the
     * current System Properties.
     * @return a copy of the XMLSecurityPropertyManager
     */
    public XMLSecurityPropertyManager cloneAndUpdate() {
        XMLSecurityPropertyManager copy = clone();
        copy.readSystemProperties();
        return copy;
    }

    /**
     * Finds the property with the given name.
     * @param propertyName the property name specified
     * @return the property name if found, null otherwise
     */
    public String find(String propertyName) {
        for (Property property : Property.values()) {
            if (property.equalsName(propertyName)) {
                return property.propertyName();
            }
        }
        return null;
    }

    /**
     * Get the index by property name
     * @param propertyName property name
     * @return the index of the property if found; return -1 if not
     */
    public int getIndex(String propertyName){
        for (Property property : Property.values()) {
            if (property.equalsName(propertyName)) {
                //internally, ordinal is used as index
                return property.ordinal();
            }
        }
        return -1;
    }

    /**
     * Set the value for a specific property.
     *
     * @param property the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setValue(Property property, State state, String value) {
        //only update if it shall override
        if (state.compareTo(states[property.ordinal()]) >= 0) {
            values[property.ordinal()] = value;
            states[property.ordinal()] = state;
        }
    }

    /**
     * Return the value of the specified property
     *
     * @param property the property
     * @return the value of the property
     */
    public String getValue(Property property) {
        return values[property.ordinal()];
    }

    /**
     * Read from system properties, or those in jaxp.properties
     */
    public void readSystemProperties() {
        for (Property property : Property.values()) {
            if (State.SYSTEMPROPERTY.compareTo(states[property.ordinal()]) >= 0 &&
                    property.systemProperty() != null) {
                // attempts to read the System Property
                if (!getSystemProperty(property, property.systemProperty())) {
                    //if system property is not found, try the config file
                    if (State.JAXPDOTPROPERTIES.compareTo(states[property.ordinal()]) >= 0) {
                        getPropertyConfig(property, property.systemProperty());
                    }
                }
            }
        }
    }
}
