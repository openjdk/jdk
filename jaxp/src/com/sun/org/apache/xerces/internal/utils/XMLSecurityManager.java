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
        DEFAULT, FSP, JAXPDOTPROPERTIES, SYSTEMPROPERTY, APIPROPERTY
    }

    /**
     * Limits managed by the security manager
     */
    public static enum Limit {
        ENTITY_EXPANSION_LIMIT(64000),
        MAX_OCCUR_NODE_LIMIT(5000),
        ELEMENT_ATTRIBUTE_LIMIT(10000);

        final int defaultValue;

        Limit(int value) {
            this.defaultValue = value;
        }

        int defaultValue() {
            return defaultValue;
        }
    }

    /**
     * Values of the limits as defined in enum Limit
     */
    private final int[] limits;
    /**
     * States of the settings for each limit in limits above
     */
    private State[] states = {State.DEFAULT, State.DEFAULT, State.DEFAULT, State.DEFAULT};

    /**
     * Default constructor. Establishes default values for known security
     * vulnerabilities.
     */
    public XMLSecurityManager() {
        limits = new int[Limit.values().length];
        for (Limit limit : Limit.values()) {
            limits[limit.ordinal()] = limit.defaultValue();
        }
        //read system properties or jaxp.properties
        readSystemProperties();
    }

    /**
     * Sets the limit for a specific type of XML constructs. This can be either
     * the size or the number of the constructs.
     *
     * @param type the type of limitation
     * @param state the state of limitation
     * @param limit the limit to the type
     */
    public void setLimit(Limit limit, State state, int value) {
        //only update if it shall override
        if (state.compareTo(states[limit.ordinal()]) >= 0) {
            limits[limit.ordinal()] = value;
            states[limit.ordinal()] = state;
        }
    }

    /**
     * Returns the limit set for the type specified
     *
     * @param limit the type of limitation
     * @return the limit to the type
     */
    public int getLimit(Limit limit) {
        return limits[limit.ordinal()];
    }

    /**
     * Read from system properties, or those in jaxp.properties
     */
    private void readSystemProperties() {
        getSystemProperty(Limit.ENTITY_EXPANSION_LIMIT, Constants.ENTITY_EXPANSION_LIMIT);
        getSystemProperty(Limit.MAX_OCCUR_NODE_LIMIT, Constants.MAX_OCCUR_LIMIT);
        getSystemProperty(Limit.ELEMENT_ATTRIBUTE_LIMIT,
                Constants.SYSTEM_PROPERTY_ELEMENT_ATTRIBUTE_LIMIT);
    }

    /**
     * Read from system properties, or those in jaxp.properties
     *
     * @param limit the type of the property
     * @param property the property name
     */
    private void getSystemProperty(Limit limit, String property) {
        try {
            String value = SecuritySupport.getSystemProperty(property);
            if (value != null && !value.equals("")) {
                limits[limit.ordinal()] = Integer.parseInt(value);
                states[limit.ordinal()] = State.SYSTEMPROPERTY;
                return;
            }

            value = SecuritySupport.readJAXPProperty(property);
            if (value != null && !value.equals("")) {
                limits[limit.ordinal()] = Integer.parseInt(value);
                states[limit.ordinal()] = State.JAXPDOTPROPERTIES;
            }
        } catch (NumberFormatException e) {
            //invalid setting ignored
        }
    }
}
