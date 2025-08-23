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

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Class to replace tokens in strings.
 * <p>
 * Single instance holds a list of tokens. Tokens can be substrings of each other.
 * The implementation performs greedy replacement: longer tokens are replaced first.
 */
public final class TokenReplace {

    private record TokenCut(String[] main, String[] sub) {
        static String[] orderTokens(String... tokens) {
            if (tokens.length == 0) {
                throw new IllegalArgumentException("Empty token list");
            }

            final var orderedTokens = Stream.of(tokens)
                    .sorted(Comparator.<String>naturalOrder().thenComparing(Comparator.comparingInt(String::length)))
                    .distinct()
                    .toArray(String[]::new);

            if (orderedTokens[0].isEmpty()) {
                throw new IllegalArgumentException("Empty token in the list of tokens");
            }

            return orderedTokens;
        }

        static TokenCut createFromOrderedTokens(String... tokens) {
            final List<Integer> subTokens = new ArrayList<>();

            for (var i = 0; i < tokens.length - 1; ++i) {
                final var x = tokens[i];
                for (var j = i + 1; j < tokens.length; ++j) {
                    final var y = tokens[j];
                    if (y.contains(x)) {
                        subTokens.add(i);
                    }
                }
            }

            if (subTokens.isEmpty()) {
                return new TokenCut(tokens, null);
            } else {
                final var main = IntStream.range(0, tokens.length)
                        .mapToObj(Integer::valueOf)
                        .filter(Predicate.not(subTokens::contains))
                        .map(i -> {
                            return tokens[i];
                        }).toArray(String[]::new);
                final var sub = subTokens.stream().map(i -> {
                    return tokens[i];
                }).toArray(String[]::new);
                return new TokenCut(main, sub);
            }
        }

        @Override
        public String toString() {
            return String.format("TokenCut(main=%s, sub=%s)", Arrays.toString(main), Arrays.toString(sub));
        }
    }

    public TokenReplace(String... tokens) {
        tokens = TokenCut.orderTokens(tokens);

        this.tokens = tokens;
        regexps = new ArrayList<>();

        for (;;) {
            final var tokenCut = TokenCut.createFromOrderedTokens(tokens);
            regexps.add(Pattern.compile(Stream.of(tokenCut.main()).map(Pattern::quote).collect(joining("|", "(", ")"))));

            if (tokenCut.sub() == null) {
                break;
            }

            tokens = tokenCut.sub();
        }
    }

    public String applyTo(String str, Function<String, Object> tokenValueSupplier) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(tokenValueSupplier);
        for (final var regexp : regexps) {
            str = regexp.matcher(str).replaceAll(mr -> {
                final var token = mr.group();
                return Matcher.quoteReplacement(Objects.requireNonNull(tokenValueSupplier.apply(token), () -> {
                    return String.format("Null value for token [%s]", token);
                }).toString());
            });
        }
        return str;
    }

    public String recursiveApplyTo(String str, Function<String, Object> tokenValueSupplier) {
        String newStr;
        int counter = tokens.length + 1;
        while (!(newStr = applyTo(str, tokenValueSupplier)).equals(str)) {
            str = newStr;
            if (counter-- == 0) {
                throw new IllegalStateException("Infinite recursion");
            }
        }
        return newStr;
    }

    @Override
    public int hashCode() {
        // Auto generated code
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(tokens);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        // Auto generated code
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        TokenReplace other = (TokenReplace) obj;
        return Arrays.equals(tokens, other.tokens);
    }

    @Override
    public String toString() {
        return "TokenReplace(" + String.join("|", tokens) + ")";
    }

    public static TokenReplace combine(TokenReplace x, TokenReplace y) {
        return new TokenReplace(Stream.of(x.tokens, y.tokens).flatMap(Stream::of).toArray(String[]::new));
    }

    public static Function<String, Object> createCachingTokenValueSupplier(Map<String, Supplier<Object>> tokenValueSuppliers) {
        Objects.requireNonNull(tokenValueSuppliers);
        final Map<String, Object> cache = new HashMap<>();
        return token -> {
            final var value = cache.computeIfAbsent(token, k -> {
                final var tokenValueSupplier = Objects.requireNonNull(tokenValueSuppliers.get(token), () -> {
                    return String.format("No token value supplier for token [%s]", token);
                });
                return Optional.ofNullable(tokenValueSupplier.get()).orElse(NULL_SUPPLIED);
            });

            if (value == NULL_SUPPLIED) {
                throw new NullPointerException(String.format("Null value for token [%s]", token));
            }

            return value;
        };
    }

    private final String[] tokens;
    private final transient List<Pattern> regexps;
    private static final Object NULL_SUPPLIED = new Object();
}
