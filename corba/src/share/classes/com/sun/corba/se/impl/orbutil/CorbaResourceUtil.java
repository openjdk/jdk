/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil;

import java.util.ResourceBundle;
import java.util.MissingResourceException;

public class CorbaResourceUtil {
    public static String getString(String key) {
        if (!resourcesInitialized) {
            initResources();
        }

        try {
            return resources.getString(key);
        } catch (MissingResourceException ignore) {
        }
        return null;
    }

    public static String getText(String key) {
        String message = getString(key);
        if (message == null) {
            message = "no text found: \"" + key + "\"";
        }
        return message;
    }

    public static String getText(String key, int num) {
        return getText(key, Integer.toString(num), null, null);
    }

    public static String getText(String key, String arg0) {
        return getText(key, arg0, null, null);
    }

    public static String getText(String key, String arg0, String arg1) {
        return getText(key, arg0, arg1, null);
    }

    public static String getText(String key,
                                 String arg0, String arg1, String arg2)
    {
        String format = getString(key);
        if (format == null) {
            format = "no text found: key = \"" + key + "\", " +
                "arguments = \"{0}\", \"{1}\", \"{2}\"";
        }

        String[] args = new String[3];
        args[0] = (arg0 != null ? arg0.toString() : "null");
        args[1] = (arg1 != null ? arg1.toString() : "null");
        args[2] = (arg2 != null ? arg2.toString() : "null");

        return java.text.MessageFormat.format(format, args);
    }

    private static boolean resourcesInitialized = false;
    private static ResourceBundle resources;

    private static void initResources() {
        try {
            resources =
                ResourceBundle.getBundle("com.sun.corba.se.impl.orbutil.resources.sunorb");
            resourcesInitialized = true;
        } catch (MissingResourceException e) {
            throw new Error("fatal: missing resource bundle: " +
                            e.getClassName());
        }
    }

}
