/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.text.MessageFormat;
import java.util.ResourceBundle;


/**
 * String bundle contains locale-specific strings.
 * It can be viewed a specialized variant of {@link ResourceBundle}.
 * <p>
 * Use {@link #fromResourceBundle(ResourceBundle)} to obtain {@link StringBundle}
 * instance from {@link ResourceBundle} object.
 */
@FunctionalInterface
public interface StringBundle {

    /**
     * Gets a string for the given key from this string bundle.
     * @param key the key for the desired string
     * @return the string for the given key
     *
     * @see ResourceBundle#getString(String)
     */
    String getString(String key);

    /**
     * Gets a formatted message built from the pattern string matching
     * the given key in this string bundle and the given arguments.
     * <p>
     * If non-empty list of arguments provided the function calls {@link MessageFormat#format(String, Object...)}.
     * Otherwise, it returns the result of {@link #getString(String)} method call.
     *
     * @param key the key for the desired pattern
     * @param args the array of arguments for formatting or an empty array for no formatting
     * @return the formatted message
     */
    default String format(String key, Object ... args) {
        var str = getString(key);
        if (args.length != 0) {
            return MessageFormat.format(str, args);
        } else {
            return str;
        }
    }

    /**
     * Gets {@link StringBundle} instance from the given {@link ResourceBundle} object.
     * @param bundle the resource bundle
     * @return the string bundle
     */
    public static StringBundle fromResourceBundle(ResourceBundle bundle) {
        return bundle::getString;
    }
}
