/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.newrmic;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Provides resource support for rmic.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author Peter Jones
 **/
public final class Resources {

    private static ResourceBundle resources = null;
    private static ResourceBundle resourcesExt = null;
    static {
        try {
            resources =
                ResourceBundle.getBundle("sun.rmi.rmic.resources.rmic");
        } catch (MissingResourceException e) {
            // gracefully handle this later
        }
        try {
            resourcesExt =
                ResourceBundle.getBundle("sun.rmi.rmic.resources.rmicext");
        } catch (MissingResourceException e) {
            // OK if this isn't found
        }
    }

    private Resources() { throw new AssertionError(); }

    /**
     * Returns the text of the rmic resource for the specified key
     * formatted with the specified arguments.
     **/
    public static String getText(String key, String... args) {
        String format = getString(key);
        if (format == null) {
            format = "missing resource key: key = \"" + key + "\", " +
                "arguments = \"{0}\", \"{1}\", \"{2}\"";
        }
        return MessageFormat.format(format, (Object[]) args);
    }

    /**
     * Returns the rmic resource string for the specified key.
     **/
    private static String getString(String key) {
        if (resourcesExt != null) {
            try {
                return resourcesExt.getString(key);
            } catch (MissingResourceException e) {
            }
        }
        if (resources != null) {
            try {
                return resources.getString(key);
            } catch (MissingResourceException e) {
                return null;
            }
        }
        return "missing resource bundle: key = \"" + key + "\", " +
            "arguments = \"{0}\", \"{1}\", \"{2}\"";
    }
}
