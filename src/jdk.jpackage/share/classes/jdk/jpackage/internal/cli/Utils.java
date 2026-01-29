/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundlingEnvironment;
import jdk.jpackage.internal.model.SelfContainedException;

final class Utils {

    private Utils() {
    }

    /**
     * Returns stream of option values with option specs defined in the specified
     * class.
     * <p>
     * The method uses reflection to get public and package private fields of type
     * {@link OptionValue} and filters those associated with {@link OptionSpec}
     * instances.
     *
     * @param c the target class
     * @return stream of option values with option specs defined in the specified
     *         class
     */
    static Stream<? extends OptionValue<?>> getOptionsWithSpecs(Class<?> c) {
        return Stream.of(c.getDeclaredFields()).filter(f -> {
            return Modifier.isStatic(f.getModifiers());
        }).filter(f -> {
            if (Modifier.isPublic(f.getModifiers())) {
                // Public is OK.
                return true;
            } else {
                // Package-private is OK.
                return Stream.<IntPredicate>of(Modifier::isPublic, Modifier::isPrivate, Modifier::isProtected).map(p -> {
                    return p.test(f.getModifiers());
                }).allMatch(v -> !v);
            }
        }).map(f -> {
            f.setAccessible(true);
            return toFunction(f::get).apply(null);
        }).map(v -> {
            OptionValue<?> result;
            if (v instanceof OptionValue<?> ov && ov.asOption().isPresent()) {
                result = ov;
            } else {
                result = null;
            }
            return result;
        }).filter(Objects::nonNull);
    }

    static JOptSimpleOptionsBuilder buildParser(OperatingSystem os, BundlingEnvironment bundlingEnv) {
        return new JOptSimpleOptionsBuilder()
                .options(StandardOption.options())
                .optionSpecMapper(OptionsProcessor.optionSpecMapper(os, bundlingEnv))
                .jOptSimpleParserErrorHandler(errDesc -> {
                    final String formatId;
                    switch (errDesc.type()) {
                        case UNRECOGNIZED_OPTION -> {
                            formatId = "ERR_InvalidOption";
                        }
                        case OPTION_MISSING_REQUIRED_ARGUMENT -> {
                            formatId = "ERR_InvalidOption";
                        }
                        default -> {
                            throw new AssertionError();
                        }
                    }
                    return new ParseException(I18N.format(formatId, errDesc.optionName().formatForCommandLine()));
                });
    }

    @SelfContainedException
    static final class ParseException extends RuntimeException {

        ParseException(String msg) {
            super(msg);
        }

        private static final long serialVersionUID = 1L;
    }
}
