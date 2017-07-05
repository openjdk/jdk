/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* FROM mail.jar */
package com.sun.xml.internal.org.jvnet.mimepull;

import java.util.*;

/**
 * Utilities to make it easier to get property values.
 * Properties can be strings or type-specific value objects.
 *
 * @author Bill Shannon
 */
final class PropUtil {

    // No one should instantiate this class.
    private PropUtil() {
    }

    /**
     * Get a boolean valued System property.
     */
    public static boolean getBooleanSystemProperty(String name, boolean def) {
        try {
            return getBoolean(getProp(System.getProperties(), name), def);
        } catch (SecurityException sex) {
            // fall through...
        }

        /*
         * If we can't get the entire System Properties object because
         * of a SecurityException, just ask for the specific property.
         */
        try {
            String value = System.getProperty(name);
            if (value == null) {
                return def;
            }
            if (def) {
                return !value.equalsIgnoreCase("false");
            } else {
                return value.equalsIgnoreCase("true");
            }
        } catch (SecurityException sex) {
            return def;
        }
    }

    /**
     * Get the value of the specified property.
     * If the "get" method returns null, use the getProperty method,
     * which might cascade to a default Properties object.
     */
    private static Object getProp(Properties props, String name) {
        Object val = props.get(name);
        if (val != null) {
            return val;
        } else {
            return props.getProperty(name);
        }
    }

    /**
     * Interpret the value object as a boolean,
     * returning def if unable.
     */
    private static boolean getBoolean(Object value, boolean def) {
        if (value == null) {
            return def;
        }
        if (value instanceof String) {
            /*
             * If the default is true, only "false" turns it off.
             * If the default is false, only "true" turns it on.
             */
            if (def) {
                return !((String)value).equalsIgnoreCase("false");
            } else {
                return ((String)value).equalsIgnoreCase("true");
            }
        }
        if (value instanceof Boolean) {
            return ((Boolean)value).booleanValue();
        }
        return def;
    }
}
