/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private String getFormattedString(String key, Object[] args) {
        return new FormattedMessage(key, args).value();
    }

    public CannedFormattedString cannedFormattedString(String key, Object ... args) {
        return new CannedFormattedString(this::getFormattedString, key, args);
    }

    public Pattern cannedFormattedStringAsPattern(String key, Function<Object, Pattern> formatArgMapper, Object ... args) {
        var fm = new FormattedMessage(key, args);
        return fm.messageFormat().map(mf -> {
            return toPattern(mf, formatArgMapper, args);
        }).orElseGet(() -> {
            return Pattern.compile(Pattern.quote(fm.value()));
        });
    }

    static Pattern toPattern(MessageFormat mf, Function<Object, Pattern> formatArgMapper, Object ... args) {
        Objects.requireNonNull(mf);
        Objects.requireNonNull(formatArgMapper);

        var patternSb = new StringBuilder();
        var runSb = new StringBuilder();

        var it = mf.formatToCharacterIterator(args);
        while (it.getIndex() < it.getEndIndex()) {
            var runBegin = it.getRunStart();
            var runEnd = it.getRunLimit();
            if (runEnd < runBegin) {
                throw new IllegalStateException();
            }

            var attrs = it.getAttributes();
            if (attrs.isEmpty()) {
                // Regular text run.
                runSb.setLength(0);
                it.setIndex(runBegin);
                for (int counter = runEnd - runBegin; counter != 0; --counter) {
                    runSb.append(it.current());
                    it.next();
                }
                patternSb.append(Pattern.quote(runSb.toString()));
            } else {
                // Format run.
                int argi = (Integer)attrs.get(MessageFormat.Field.ARGUMENT);
                var arg = args[argi];
                var pattern = Objects.requireNonNull(formatArgMapper.apply(arg));
                patternSb.append(pattern.toString());
                it.setIndex(runEnd);
            }
        }

        return Pattern.compile(patternSb.toString());
    }

    private final class FormattedMessage {

        FormattedMessage(String key, Object[] args) {
            List.of(args).forEach(Objects::requireNonNull);

            var formatter = getString(key);

            var mf = new MessageFormat(formatter);
            var formatCount = mf.getFormatsByArgumentIndex().length;
            if (formatCount != args.length) {
                throw new IllegalArgumentException(String.format(
                        "Expected %d arguments for [%s] string, but given %d", formatCount, key, args.length));
            }

            if (formatCount == 0) {
                this.mf = null;
                value = formatter;
            } else {
                this.mf = mf;
                value = mf.format(args);
            }
        }

        String value() {
            return value;
        }

        Optional<MessageFormat> messageFormat() {
            return Optional.ofNullable(mf);
        }

        private final String value;
        private final MessageFormat mf;
    }

    private final Class<?> i18nClass;
    private final Method i18nClass_getString;
}
