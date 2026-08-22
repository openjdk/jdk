/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.cli.OptionSource.isCommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A formatted message used while parsing option values.
 * <p>
 * A formatted message consists of an I18N key and formatting arguments.
 * <p>
 * Formatting arguments can include strings, an option name, an option value,
 * a bundle type, and other attributes that describe the parsing context.
 * <p>
 * This class captures formatted-message data in a limited context and converts
 * it to a string when the complete parsing context is available.
 */
record CannedFormattedMessage(String key, List<FormatArgument> args) {

    CannedFormattedMessage {
        Objects.requireNonNull(key);
        Objects.requireNonNull(args);
        args.forEach(Objects::requireNonNull);
    }

    @Override
    public String toString() {
        if (args.isEmpty()) {
            return String.format("%s", key);
        } else {
            return String.format("%s+%s", key, args);
        }
    }

    static Builder build(String key) {
        return new Builder().key(Objects.requireNonNull(key));
    }

    static class Builder {

        Builder key(String v) {
            key = v;
            return this;
        }

        Builder optionName() {
            args.add(PlaceholderFormatArgument.OPTION_NAME);
            return this;
        }

        Builder optionValue() {
            args.add(PlaceholderFormatArgument.OPTION_VALUE);
            return this;
        }

        Builder bundleTypeName() {
            args.add(PlaceholderFormatArgument.BUNDLE_TYPE_NAME);
            return this;
        }

        Builder str(String v) {
            args.add(new StringValueFormatArgument(v));
            return this;
        }

        CannedFormattedMessage create() {
            return new CannedFormattedMessage(key, args);
        }

        private String key;
        private List<FormatArgument> args = new ArrayList<>();
    }

    String resolve(Context ctx) {
        return I18N.format(key, args.stream().map(arg -> {
            return arg.getValue(ctx);
        }).toArray());
    }

    record Context(OptionName optionName, String optionValue, StandardOptionContext optionContext) {

        Context {
            Objects.requireNonNull(optionName);
            Objects.requireNonNull(optionValue);
            Objects.requireNonNull(optionContext);
        }
    }

    private sealed interface FormatArgument {

        String getValue(Context ctx);
    }

    private enum PlaceholderFormatArgument implements FormatArgument {
        OPTION_NAME,
        OPTION_VALUE,
        BUNDLE_TYPE_NAME,
        ;

        @Override
        public String getValue(Context ctx) {
            return switch (this) {
                case BUNDLE_TYPE_NAME -> {
                    yield ctx.optionContext().bundlingOperation().orElseThrow().bundleType().label();
                }
                case OPTION_NAME -> {
                    if (isCommandLine(ctx.optionContext().src())) {
                        yield ctx.optionName().formatForCommandLine();
                    } else {
                        yield ctx.optionName().name();
                    }
                }
                case OPTION_VALUE -> {
                    yield ctx.optionValue();
                }
            };
        }
    }

    private record StringValueFormatArgument(String value) implements FormatArgument {

        StringValueFormatArgument {
            Objects.requireNonNull(value);
        }

        @Override
        public String getValue(Context ctx) {
            return value;
        }
    }
}
