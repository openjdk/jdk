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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record CannedFormattedString(BiFunction<String, Object[], String> formatter, String format, List<Object> args) implements CannedArgument {

    public CannedFormattedString mapArgs(UnaryOperator<Object> mapper) {
        return new CannedFormattedString(formatter, format, args.stream().map(mapper).toList());
    }

    public CannedFormattedString {
        Objects.requireNonNull(formatter);
        Objects.requireNonNull(format);
        Objects.requireNonNull(args);
        args.forEach(Objects::requireNonNull);
    }

    @Override
    public String getValue() {
        return formatter.apply(format, args.stream().map(arg -> {
            if (arg instanceof CannedArgument cannedArg) {
                return cannedArg.getValue();
            } else {
                return arg;
            }
        }).toArray());
    }

    public CannedFormattedString addPrefix(String prefixFormat) {
        return new CannedFormattedString(
                new AddPrefixFormatter(formatter), prefixFormat, Stream.concat(Stream.of(format), args.stream()).toList());
    }

    @Override
    public String toString() {
        if (args.isEmpty()) {
            return String.format("%s", format);
        } else {
            return String.format("%s+%s", format, args);
        }
    }

    public interface Spec {

        String format();
        List<Object> modelArgs();

        default CannedFormattedString asCannedFormattedString(Object ... args) {
            if (args.length != modelArgs().size()) {
                throw new IllegalArgumentException();
            }
            return JPackageStringBundle.MAIN.cannedFormattedString(format(), args);
        }

        default Pattern asPattern() {
            return JPackageStringBundle.MAIN.cannedFormattedStringAsPattern(format(), modelArgs().toArray());
        }
    }

    public static CannedFormattedString createFromMessageFormat(String messageFormatStr, Object... args) {
        return new CannedFormattedString(MESSAGE_FORMAT_FORMATTER, messageFormatStr, List.of(args));
    }

    private record AddPrefixFormatter(BiFunction<String, Object[], String> formatter) implements BiFunction<String, Object[], String> {

        AddPrefixFormatter {
            Objects.requireNonNull(formatter);
        }

        @Override
        public String apply(String format, Object[] formatArgs) {
            var str = formatter.apply((String)formatArgs[0], Arrays.copyOfRange(formatArgs, 1, formatArgs.length));
            return formatter.apply(format, new Object[] {str});
        }
    }

    private static final BiFunction<String, Object[], String> MESSAGE_FORMAT_FORMATTER = (String format, Object[] args) -> {
        return CannedMessageFormat.create(format, args).value();
    };
}
