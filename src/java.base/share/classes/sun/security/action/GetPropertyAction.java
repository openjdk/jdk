/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.action;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.Properties;
import sun.security.util.Debug;

/**
 * A convenience class for retrieving the string value of a system
 * property as a privileged action.
 *
 * <p>An instance of this class can be used as the argument of
 * <code>AccessController.doPrivileged</code>.
 *
 * <p>The following code retrieves the value of the system
 * property named <code>"prop"</code> as a privileged action:
 *
 * <pre>
 * String s = java.security.AccessController.doPrivileged
 *                      (new GetPropertyAction("prop"));
 * </pre>
 *
 * @author Roland Schemers
 * @see java.security.PrivilegedAction
 * @see java.security.AccessController
 * @since 1.2
 */

public class GetPropertyAction implements PrivilegedAction<String> {
    private final String theProp;
    private final String defaultVal;

    /**
     * Constructor that takes the name of the system property whose
     * string value needs to be determined.
     *
     * @param theProp the name of the system property.
     */
    public GetPropertyAction(String theProp) {
        this.theProp = theProp;
        this.defaultVal = null;
    }

    /**
     * Constructor that takes the name of the system property and the default
     * value of that property.
     *
     * @param theProp the name of the system property.
     * @param defaultVal the default value.
     */
    public GetPropertyAction(String theProp, String defaultVal) {
        this.theProp = theProp;
        this.defaultVal = defaultVal;
    }

    /**
     * Determines the string value of the system property whose
     * name was specified in the constructor.
     *
     * @return the string value of the system property,
     *         or the default value if there is no property with that key.
     */
    public String run() {
        String value = System.getProperty(theProp);
        return (value == null) ? defaultVal : value;
    }

    /**
     * Convenience method to get a property without going through doPrivileged
     * if no security manager is present. This is unsafe for inclusion in a
     * public API but allowable here since this class is now encapsulated.
     *
     * Note that this method performs a privileged action using caller-provided
     * inputs. The caller of this method should take care to ensure that the
     * inputs are not tainted and the returned property is not made accessible
     * to untrusted code if it contains sensitive information.
     *
     * @param theProp the name of the system property.
     */
    @SuppressWarnings("removal")
    public static String privilegedGetProperty(String theProp) {
        if (System.getSecurityManager() == null) {
            return System.getProperty(theProp);
        } else {
            return AccessController.doPrivileged(
                    new GetPropertyAction(theProp));
        }
    }

    /**
     * Convenience method to get a property without going through doPrivileged
     * if no security manager is present. This is unsafe for inclusion in a
     * public API but allowable here since this class is now encapsulated.
     *
     * Note that this method performs a privileged action using caller-provided
     * inputs. The caller of this method should take care to ensure that the
     * inputs are not tainted and the returned property is not made accessible
     * to untrusted code if it contains sensitive information.
     *
     * @param theProp the name of the system property.
     * @param defaultVal the default value.
     */
    @SuppressWarnings("removal")
    public static String privilegedGetProperty(String theProp,
            String defaultVal) {
        if (System.getSecurityManager() == null) {
            return System.getProperty(theProp, defaultVal);
        } else {
            return AccessController.doPrivileged(
                    new GetPropertyAction(theProp, defaultVal));
        }
    }

    /**
     * Convenience method to call <code>System.getProperties</code> without
     * having to go through doPrivileged if no security manager is present.
     * This is unsafe for inclusion in a public API but allowable here since
     * this class is now encapsulated.
     *
     * Note that this method performs a privileged action, and callers of
     * this method should take care to ensure that the returned properties
     * are not made accessible to untrusted code since it may contain
     * sensitive information.
     */
    @SuppressWarnings("removal")
    public static Properties privilegedGetProperties() {
        if (System.getSecurityManager() == null) {
            return System.getProperties();
        } else {
            return AccessController.doPrivileged(
                    new PrivilegedAction<Properties>() {
                        public Properties run() {
                            return System.getProperties();
                        }
                    }
            );
        }
    }

    /**
     * Convenience method for fetching System property values that are timeouts.
     * Accepted timeout values may be purely numeric, a numeric value
     * followed by "s" (both interpreted as seconds), or a numeric value
     * followed by "ms" (interpreted as milliseconds).
     *
     * @param prop the name of the System property
     * @param def a default value (in milliseconds)
     * @param dbg a Debug object, if null no debug messages will be sent
     *
     * @return an integer value corresponding to the timeout value in the System
     *      property in milliseconds.  If the property value is empty, negative,
     *      or contains non-numeric characters (besides a trailing "s" or "ms")
     *      then the default value will be returned.  If a negative value for
     *      the "def" parameter is supplied, zero will be returned if the
     *      property's value does not conform to the allowed syntax.
     */
    public static int privilegedGetTimeoutProp(String prop, int def, Debug dbg) {
        if (def < 0) {
            def = 0;
        }

        String rawPropVal = privilegedGetProperty(prop, "").trim();
        if (rawPropVal.length() == 0) {
            return def;
        }

        // Determine if "ms" or just "s" is on the end of the string.
        // We may do a little surgery on the value so we'll retain
        // the original value in rawPropVal for debug messages.
        boolean isMillis = false;
        String propVal = rawPropVal;
        if (rawPropVal.toLowerCase(Locale.ROOT).endsWith("ms")) {
            propVal = rawPropVal.substring(0, rawPropVal.length() - 2);
            isMillis = true;
        } else if (rawPropVal.toLowerCase(Locale.ROOT).endsWith("s")) {
            propVal = rawPropVal.substring(0, rawPropVal.length() - 1);
        }

        // Next check to make sure the string is built only from digits
        if (propVal.matches("^\\d+$")) {
            try {
                int timeout = Integer.parseInt(propVal);
                return isMillis ? timeout : timeout * 1000;
            } catch (NumberFormatException nfe) {
                if (dbg != null) {
                    dbg.println("Warning: Unexpected " + nfe +
                            " for timeout value " + rawPropVal +
                            ". Using default value of " + def + " msec.");
                }
                return def;
            }
        } else {
            if (dbg != null) {
                dbg.println("Warning: Incorrect syntax for timeout value " +
                        rawPropVal + ". Using default value of " + def +
                        " msec.");
            }
            return def;
        }
    }

    /**
     * Convenience method for fetching System property values that are booleans.
     *
     * @param prop the name of the System property
     * @param def a default value
     * @param dbg a Debug object, if null no debug messages will be sent
     *
     * @return a boolean value corresponding to the value in the System property.
     *      If the property value is neither "true" or "false", the default value
     *      will be returned.
     */
    public static boolean privilegedGetBooleanProp(String prop, boolean def, Debug dbg) {
        String rawPropVal = privilegedGetProperty(prop, "");
        if ("".equals(rawPropVal)) {
            return def;
        }

        String lower = rawPropVal.toLowerCase(Locale.ROOT);
        if ("true".equals(lower)) {
            return true;
        } else if ("false".equals(lower)) {
            return false;
        } else {
            if (dbg != null) {
                dbg.println("Warning: Unexpected value for " + prop +
                            ": " + rawPropVal +
                            ". Using default value: " + def);
            }
            return def;
        }
    }

}
