/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.jpackage.test;

import static jdk.jpackage.internal.util.function.ExceptionBox.toUnchecked;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public enum JPackageStringBundle {

    MAIN("jdk.jpackage.internal.I18N"),
    ;

    JPackageStringBundle(String i18nClassName) {
        try {
            i18nClass = Class.forName(i18nClassName);

            i18nClass_getString = i18nClass.getDeclaredMethod("getString", String.class);
            i18nClass_getString.setAccessible(true);
        } catch (ClassNotFoundException|NoSuchMethodException ex) {
            throw toUnchecked(ex);
        }
        formatter = (String key, Object[] args) -> {
            return createFormattedMessage(key, args).value();
        };
    }

    /**
     * Return a string value of the given key from jpackage resources.
     */
    private String getString(String key) {
        try {
            return (String)i18nClass_getString.invoke(i18nClass, key);
        } catch (IllegalAccessException|InvocationTargetException ex) {
            throw toUnchecked(ex);
        }
    }

    public CannedFormattedString cannedFormattedString(String key, Object ... args) {
        return new CannedFormattedString(formatter, key, List.of(args));
    }

    public Pattern cannedFormattedStringAsPattern(String key, Function<Object, Pattern> formatArgMapper, Object ... args) {
        return createFormattedMessage(key, args).toPattern(formatArgMapper);
    }

    public Pattern cannedFormattedStringAsPattern(String key, Object ... args) {
        return createFormattedMessage(key, args).toPattern();
    }

    private CannedMessageFormat createFormattedMessage(String key, Object ... args) {
        return CannedMessageFormat.create(
                CannedMessageFormat.defaultInvalidFormatArgumentCountExceptionSupplier(key, args.length),
                getString(key),
                args);
    }

    private final Class<?> i18nClass;
    private final Method i18nClass_getString;
    private final BiFunction<String, Object[], String> formatter;
}
