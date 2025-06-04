/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.naming.internal;

import sun.security.util.SecurityProperties;

import javax.naming.Reference;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.FilterInfo;
import java.io.ObjectInputFilter.Status;

/**
 * This class implements the filter that validates object factories classes instantiated
 * during {@link Reference} lookups.
 * There is one system-wide filter instance per VM that can be set via
 * the {@code "jdk.jndi.object.factoriesFilter"} system property value, or via
 * setting the property in the security properties file. The system property value supersedes
 * the security property value. If none of the properties are specified the default
 * "*" value is used.
 * The filter is implemented as {@link ObjectInputFilter} with capabilities limited to the
 * validation of a factory's class types only ({@linkplain FilterInfo#serialClass()}).
 * Array length, number of object references, depth, and stream size filtering capabilities are
 * not supported by the filter.
 */
public final class ObjectFactoriesFilter {

    /**
     * Checks if serial filter configured with {@code "jdk.jndi.object.factoriesFilter"}
     * system property value allows instantiation of the specified objects factory class.
     * If the filter result is {@linkplain Status#ALLOWED ALLOWED}, the filter will
     * allow the instantiation of objects factory class.
     *
     * @param serialClass objects factory class
     * @return true - if the factory is allowed to be instantiated; false - otherwise
     */
    public static boolean checkGlobalFilter(Class<?> serialClass) {
        return checkInput(GLOBAL_FILTER, () -> serialClass);
    }

    /**
     * Checks if the factory filters allow the given factory class for LDAP.
     * This method combines the global and LDAP specific filter results to determine
     * if the given factory class is allowed.
     * The given factory class is rejected if any of these two filters reject
     * it, or if none of them allow it.
     *
     * @param serialClass objects factory class
     * @return true - if the factory is allowed to be instantiated; false - otherwise
     */
    public static boolean checkLdapFilter(Class<?> serialClass) {
        return checkInput(LDAP_FILTER, () -> serialClass);
    }

    /**
     * Checks if the factory filters allow the given factory class for RMI.
     * This method combines the global and RMI specific filter results to determine
     * if the given factory class is allowed.
     * The given factory class is rejected if any of these two filters reject
     * it, or if none of them allow it.
     *
     * @param serialClass objects factory class
     * @return true - if the factory is allowed to be instantiated; false - otherwise
     */
    public static boolean checkRmiFilter(Class<?> serialClass) {
        return checkInput(RMI_FILTER, () -> serialClass);
    }

    private static boolean checkInput(ConfiguredFilter filter, FactoryInfo serialClass) {
        var globalFilter = GLOBAL_FILTER.filter();
        var specificFilter = filter.filter();
        Status globalResult = globalFilter.checkInput(serialClass);

        // Check if a specific filter is the global one
        if (filter == GLOBAL_FILTER) {
            return globalResult == Status.ALLOWED;
        }
        return switch (globalResult) {
            case ALLOWED -> specificFilter.checkInput(serialClass) != Status.REJECTED;
            case REJECTED -> false;
            case UNDECIDED -> specificFilter.checkInput(serialClass) == Status.ALLOWED;
        };
    }

    // FilterInfo to check if objects factory class is allowed by the system-wide
    // filter. Array length, number of object references, depth, and stream size
    // capabilities are ignored.
    @FunctionalInterface
    private interface FactoryInfo extends FilterInfo {
        @Override
        default long arrayLength() {
            return -1;
        }

        @Override
        default long depth() {
            return 1;
        }

        @Override
        default long references() {
            return 0;
        }

        @Override
        default long streamBytes() {
            return 0;
        }
    }

    // Prevent instantiation of the factories filter class
     private ObjectFactoriesFilter() {
         throw new InternalError("Not instantiable");
     }

    // System property name that contains the patterns to filter object factory names
    private static final String GLOBAL_FACTORIES_FILTER_PROPNAME =
            "jdk.jndi.object.factoriesFilter";

    // System property name that contains the patterns to filter LDAP object factory
    // names
    private static final String LDAP_FACTORIES_FILTER_PROPNAME =
            "jdk.jndi.ldap.object.factoriesFilter";

    // System property name that contains the patterns to filter RMI object factory
    // names
    private static final String RMI_FACTORIES_FILTER_PROPNAME =
            "jdk.jndi.rmi.object.factoriesFilter";

    // Default system property value that allows the load of any object factory
    // classes
    private static final String DEFAULT_GLOBAL_SP_VALUE = "*";

    // Default system property value that allows the load of any object factory
    // class provided by the JDK LDAP provider implementation
    private static final String DEFAULT_LDAP_SP_VALUE =
            "java.naming/com.sun.jndi.ldap.**;!*";

    // Default system property value that allows the load of any object factory
    // class provided by the JDK RMI provider implementation
    private static final String DEFAULT_RMI_SP_VALUE =
            "jdk.naming.rmi/com.sun.jndi.rmi.**;!*";

    // A system-wide global object factories filter constructed from the system
    // property
    private static final ConfiguredFilter GLOBAL_FILTER =
            initializeFilter(GLOBAL_FACTORIES_FILTER_PROPNAME, DEFAULT_GLOBAL_SP_VALUE);

    // A system-wide LDAP specific object factories filter constructed from the system
    // property
    private static final ConfiguredFilter LDAP_FILTER =
            initializeFilter(LDAP_FACTORIES_FILTER_PROPNAME, DEFAULT_LDAP_SP_VALUE);

    // A system-wide RMI specific object factories filter constructed from the system
    // property
    private static final ConfiguredFilter RMI_FILTER =
            initializeFilter(RMI_FACTORIES_FILTER_PROPNAME, DEFAULT_RMI_SP_VALUE);

    // Record for storing a factory filter configuration
    private interface ConfiguredFilter {
        ObjectInputFilter filter();
    }

    // Record to store an object input filter constructed from a valid filter
    // pattern string
    private record ValidFilter(ObjectInputFilter filter)
            implements ConfiguredFilter {
    }

    // Record to store parsing results for a filter with
    // illegal or malformed pattern string
    private record InvalidFilter(String filterPropertyName,
                                 IllegalArgumentException error)
            implements ConfiguredFilter {

        @Override
        public ObjectInputFilter filter() {
            // Report a filter property name and an error message
            throw new IllegalArgumentException(filterPropertyName +
                    ": " + error.getMessage());
        }
    }

    // Read filter pattern value from a system/security property
    // and create a filter record from it (valid or invalid).
    private static ConfiguredFilter initializeFilter(String filterPropertyName,
                                                     String filterDefaultValue) {
        try {
            var filter = ObjectInputFilter.Config.createFilter(
                    getFilterPropertyValue(filterPropertyName,
                            filterDefaultValue));
            return new ValidFilter(filter);
        } catch (IllegalArgumentException iae) {
            return new InvalidFilter(filterPropertyName, iae);
        }
    }

    // Get security or system property value
    private static String getFilterPropertyValue(String propertyName,
                                                 String defaultValue) {
        String propVal = SecurityProperties.getOverridableProperty(propertyName);
        return propVal != null ? propVal : defaultValue;
    }
}
