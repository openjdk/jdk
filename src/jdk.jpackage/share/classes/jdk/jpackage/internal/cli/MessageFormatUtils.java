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

package jdk.jpackage.internal.cli;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

final class MessageFormatUtils {

    private MessageFormatUtils() {
    }

    static String createMessage(Options cmdline, String formatId, Object... args) {
        return I18N.format(formatId, mapFormatArguments(cmdline, args));
    }

    static String createMessage(String formatId, Object... args) {
        return I18N.format(formatId, mapFormatArguments(args));
    }

    static Object[] mapFormatArguments(Object... args) {
        return mapFormatArguments(optionSpec -> {
            return optionSpec.name().formatForCommandLine();
        }, args);
    }

    static Object[] mapFormatArguments(Options cmdline, Object... args) {
        Objects.requireNonNull(cmdline);
        return mapFormatArguments(optionSpec -> {
            return optionSpec.getFirstNameIn(cmdline).formatForCommandLine();
        }, args);
    }

    private static Object[] mapFormatArguments(Function<OptionSpec<?>, String> optionSpecFormatter, Object... args) {
        Objects.requireNonNull(optionSpecFormatter);
        return Stream.of(args).map(arg -> {
            return asOptionSpec(arg).map(optionSpecFormatter).orElseGet(() -> {
                return Optional.ofNullable(arg).map(Object::toString).orElse(null);
            });
        }).toArray();
    }

    private static Optional<OptionSpec<?>> asOptionSpec(Object v) {
        if (v instanceof OptionSpec<?> optionSpec) {
            return Optional.of(optionSpec);
        } else if (v instanceof OptionValue<?> ov) {
            return asOptionSpec(ov.getSpec());
        } else if (v instanceof Option option) {
            return asOptionSpec(option.spec());
        } else {
            return Optional.empty();
        }
    }
}
