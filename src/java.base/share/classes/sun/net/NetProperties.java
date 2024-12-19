/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
package sun.net;

import jdk.internal.util.StaticProperty;

import java.io.*;
import java.util.Properties;

/*
 * This class allows for centralized access to Networking properties.
 * Default values are loaded from the file jre/lib/net.properties
 *
 *
 * @author Jean-Christophe Collet
 *
 */
public class NetProperties {
    private static final Properties props = loadDefaultProperties(new Properties());

    private NetProperties() { };


    /*
     * Loads the default networking system properties
     * the file is in jre/lib/net.properties
     */
    private static Properties loadDefaultProperties(Properties props) {
        String fname = StaticProperty.javaHome();
        if (fname == null) {
            throw new Error("Can't find java.home ??");
        }
        try {
            File f = new File(fname, "conf");
            f = new File(f, "net.properties");
            fname = f.getCanonicalPath();
            try (FileInputStream in = new FileInputStream(fname)) {
                props.load(in);
            }
        } catch (Exception e) {
            // Do nothing. We couldn't find or access the file
            // so we won't have default properties...
        }
        return props;
    }

    /**
     * Get a networking system property. If no system property was defined
     * returns the default value, if it exists, otherwise returns
     * <code>null</code>.
     * @param      key  the property name.
     * @return the <code>String</code> value for the property,
     *         or <code>null</code>
     */
    public static String get(String key) {
        String def = props.getProperty(key);
        try {
            return System.getProperty(key, def);
        } catch (IllegalArgumentException | NullPointerException e) {
        }
        return null;
    }

    /**
     * Get an Integer networking system property. If no system property was
     * defined returns the default value, if it exists, otherwise returns
     * <code>null</code>.
     * @param   key     the property name.
     * @param   defval  the default value to use if the property is not found
     * @return the <code>Integer</code> value for the property,
     *         or <code>null</code>
     */
    public static Integer getInteger(String key, int defval) {
        String val = null;

        try {
            val = System.getProperty(key, props.getProperty(key));
        } catch (IllegalArgumentException | NullPointerException e) {
        }

        if (val != null) {
            try {
                return Integer.decode(val);
            } catch (NumberFormatException ex) {
            }
        }
        return defval;
    }

    /**
     * Get a Boolean networking system property. If no system property was
     * defined returns the default value, if it exists, otherwise returns
     * <code>null</code>.
     * @param   key     the property name.
     * @return the <code>Boolean</code> value for the property,
     *         or <code>null</code>
     */
    public static Boolean getBoolean(String key) {
        String val = null;

        try {
            val = System.getProperty(key, props.getProperty(key));
        } catch (IllegalArgumentException | NullPointerException e) {
        }

        if (val != null) {
            try {
                return Boolean.valueOf(val);
            } catch (NumberFormatException ex) {
            }
        }
        return null;
    }

}
