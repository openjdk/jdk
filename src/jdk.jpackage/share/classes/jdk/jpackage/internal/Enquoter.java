/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Add quotes to the given string in a configurable way.
 */
final class Enquoter {

    private Enquoter() {
        setQuoteChar('"');
    }

    static Enquoter forPropertyValues() {
        return new Enquoter()
                .setEnquotePredicate(QUOTE_IF_WHITESPACES)
                .setEscaper(PREPEND_BACKSLASH);
    }

    static Enquoter forShellLiterals() {
        return forShellLiterals('\'');
    }

    static Enquoter forShellLiterals(char quoteChar) {
        return new Enquoter()
                .setQuoteChar(quoteChar)
                .setEnquotePredicate(x -> true)
                .setEscaper(PREPEND_BACKSLASH);
    }

    String applyTo(String v) {
        if (!needQuotes.test(v)) {
            return v;
        } else {
            var buf = new StringBuilder();
            buf.appendCodePoint(beginQuoteChr);
            Optional.of(escaper).ifPresentOrElse(op -> {
                v.codePoints().forEachOrdered(chr -> {
                    if (chr == beginQuoteChr || chr == endQuoteChr) {
                        escaper.accept(chr, buf);
                    } else {
                        buf.appendCodePoint(chr);
                    }
                });
            }, () -> {
                buf.append(v);
            });
            buf.appendCodePoint(endQuoteChr);
            return buf.toString();
        }
    }

    Enquoter setQuoteChar(char chr) {
        beginQuoteChr = chr;
        endQuoteChr = chr;
        return this;
    }

    Enquoter setEscaper(BiConsumer<Integer, StringBuilder> v) {
        escaper = v;
        return this;
    }

    Enquoter setEnquotePredicate(Predicate<String> v) {
        needQuotes = v;
        return this;
    }

    private int beginQuoteChr;
    private int endQuoteChr;
    private BiConsumer<Integer, StringBuilder> escaper;
    private Predicate<String> needQuotes = str -> false;

    private static final Predicate<String> QUOTE_IF_WHITESPACES = new Predicate<String>() {
        @Override
        public boolean test(String t) {
            return pattern.matcher(t).find();
        }
        private final Pattern pattern = Pattern.compile("\\s");
    };

    private static final BiConsumer<Integer, StringBuilder> PREPEND_BACKSLASH = (chr, buf) -> {
        buf.append('\\');
        buf.appendCodePoint(chr);
    };
}
