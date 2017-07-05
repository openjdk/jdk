/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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

package javax.accessibility;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * <p>Base class used to maintain a strongly typed enumeration.  This is
 * the superclass of {@link AccessibleState} and {@link AccessibleRole}.
 * <p>The toDisplayString method allows you to obtain the localized string
 * for a locale independent key from a predefined ResourceBundle for the
 * keys defined in this class.  This localized string is intended to be
 * readable by humans.
 *
 * @see AccessibleRole
 * @see AccessibleState
 *
 * @author      Willie Walker
 * @author      Peter Korn
 * @author      Lynn Monsanto
 */
public abstract class AccessibleBundle {

    private static Hashtable table = new Hashtable();
    private final String defaultResourceBundleName
        = "com.sun.accessibility.internal.resources.accessibility";

    public AccessibleBundle() {
    }

    /**
     * The locale independent name of the state.  This is a programmatic
     * name that is not intended to be read by humans.
     * @see #toDisplayString
     */
    protected String key = null;

    /**
     * Obtains the key as a localized string.
     * If a localized string cannot be found for the key, the
     * locale independent key stored in the role will be returned.
     * This method is intended to be used only by subclasses so that they
     * can specify their own resource bundles which contain localized
     * strings for their keys.
     * @param resourceBundleName the name of the resource bundle to use for
     * lookup
     * @param locale the locale for which to obtain a localized string
     * @return a localized String for the key.
     */
    protected String toDisplayString(String resourceBundleName,
                                     Locale locale) {

        // loads the resource bundle if necessary
        loadResourceBundle(resourceBundleName, locale);

        // returns the localized string
        Object o = table.get(locale);
        if (o != null && o instanceof Hashtable) {
                Hashtable resourceTable = (Hashtable) o;
                o = resourceTable.get(key);

                if (o != null && o instanceof String) {
                    return (String)o;
                }
        }
        return key;
    }

    /**
     * Obtains the key as a localized string.
     * If a localized string cannot be found for the key, the
     * locale independent key stored in the role will be returned.
     *
     * @param locale the locale for which to obtain a localized string
     * @return a localized String for the key.
     */
    public String toDisplayString(Locale locale) {
        return toDisplayString(defaultResourceBundleName, locale);
    }

    /**
     * Gets localized string describing the key using the default locale.
     * @return a localized String describing the key for the default locale
     */
    public String toDisplayString() {
        return toDisplayString(Locale.getDefault());
    }

    /**
     * Gets localized string describing the key using the default locale.
     * @return a localized String describing the key using the default locale
     * @see #toDisplayString
     */
    public String toString() {
        return toDisplayString();
    }

    /*
     * Loads the Accessibility resource bundle if necessary.
     */
    private void loadResourceBundle(String resourceBundleName,
                                    Locale locale) {
        if (! table.contains(locale)) {

            try {
                Hashtable resourceTable = new Hashtable();

                ResourceBundle bundle = ResourceBundle.getBundle(resourceBundleName, locale);

                Enumeration iter = bundle.getKeys();
                while(iter.hasMoreElements()) {
                    String key = (String)iter.nextElement();
                    resourceTable.put(key, bundle.getObject(key));
                }

                table.put(locale, resourceTable);
            }
            catch (MissingResourceException e) {
                System.err.println("loadResourceBundle: " + e);
                // Just return so toDisplayString() returns the
                // non-localized key.
                return;
            }
        }
    }

}
