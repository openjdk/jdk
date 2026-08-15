/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class CannedMessageFormat {

    private CannedMessageFormat(MessageFormat messageFormat, Object[] args) {
        this.messageFormat = Optional.of(messageFormat);
        this.args = Arrays.copyOf(args, args.length);
    }

    private CannedMessageFormat(String value) {
        this.messageFormat = Optional.empty();
        this.args = new Object[] {value};
    }

    public String value() {
        return messageFormat.map(mf -> {
            return mf.format(args);
        }).orElseGet(() -> {
            return (String)args[0];
        });
    }

    public Optional<MessageFormat> messageFormat() {
        return messageFormat;
    }

    public Pattern toPattern(Function<Object, Pattern> formatArgMapper) {
        return messageFormat.map(mf -> {
            return toPattern(mf, formatArgMapper, args);
        }).orElseGet(() -> {
            return Pattern.compile(Pattern.quote(value()));
        });
    }

    public Pattern toPattern() {
        return toPattern(MATCH_ANY);
    }

    /**
     * Creates {@code CannedMessageFormat} instance from the given format string and
     * format arguments.
     *
     * @param formatString format string suitable for use as the first argument of
     *                     {@link MessageFormat#format(String, Object...)} call
     * @param args         an array of objects to be formatted and substituted
     * @return the {@code CannedMessageFormat} instance
     */
    public static CannedMessageFormat create(String formatString, Object... args) {
        return create(
                defaultInvalidFormatArgumentCountExceptionSupplier(formatString, args.length),
                formatString,
                args);
    }

    static CannedMessageFormat create(
            Function<Integer, RuntimeException> invalidFormatArgumentCountExceptionSupplier,
            String formatString,
            Object... args) {

        Objects.requireNonNull(invalidFormatArgumentCountExceptionSupplier);
        Objects.requireNonNull(formatString);
        List.of(args).forEach(Objects::requireNonNull);

        var mf = new MessageFormat(formatString);
        var formatCount = mf.getFormatsByArgumentIndex().length;
        if (formatCount != args.length) {
            throw Objects.requireNonNull(invalidFormatArgumentCountExceptionSupplier.apply(formatCount));
        }

        if (formatCount == 0) {
            return new CannedMessageFormat(formatString);
        } else {
            return new CannedMessageFormat(mf, args);
        }
    }

    static Function<Integer, RuntimeException> defaultInvalidFormatArgumentCountExceptionSupplier(String formatString, int argc) {
        Objects.requireNonNull(formatString);
        return formatCount -> {
            return new IllegalArgumentException(String.format(
                    "Expected %d arguments for [%s] string, but given %d", formatCount, formatString, argc));
        };
    }

    private static Pattern toPattern(MessageFormat mf, Function<Object, Pattern> formatArgMapper, Object ... args) {
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

    private final Object[] args;
    private final Optional<MessageFormat> messageFormat;

    private static final Function<Object, Pattern> MATCH_ANY = new Function<>() {

        @Override
        public Pattern apply(Object v) {
            return PATTERN;
        }

        private static final Pattern PATTERN = Pattern.compile(".*");
    };
}
