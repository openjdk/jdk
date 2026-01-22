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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;

public final class JPackageOutputValidator {

    public JPackageOutputValidator stdout() {
        stdout = true;
        return this;
    }

    public JPackageOutputValidator stderr() {
        stdout = false;
        return this;
    }

    public JPackageOutputValidator matchTimestamps() {
        matchTimestamps = true;
        return this;
    }

    public JPackageOutputValidator stripTimestamps() {
        stripTimestamps = true;
        return this;
    }

    public void applyTo(JPackageCommand cmd) {
        validators.stream().map(v -> {
            return toStringIteratorConsumer(cmd, v);
        }).flatMap(Optional::stream).reduce(Consumer::andThen).ifPresent(validator -> {

            if (stripTimestamps) {
                validator = stripTimestamps(validator, label());
            }

            if (matchTimestamps) {
                validator = matchTimestamps(validator, label());
            }

            if (stdout) {
                cmd.validateOut(validator);
            } else {
                cmd.validateErr(validator);
            }
        });
    }

    public JPackageOutputValidator validator(TKit.TextStreamVerifier validator) {
        validators.add(validator);
        return this;
    }

    public JPackageOutputValidator validator(Consumer<Iterator<String>> validator) {
        validators.add(validator);
        return this;
    }

    public JPackageOutputValidator validator(TKit.TextStreamVerifier.Group validator) {
        validators.add(validator);
        return this;
    }

    public JPackageOutputValidator expectMatchingStrings(CannedFormattedString... strings) {
        validators.add(strings);
        return this;
    }

    @SuppressWarnings("unchecked")
    private Optional<Consumer<Iterator<String>>> toStringIteratorConsumer(JPackageCommand cmd, Object validator) {
        switch (validator) {
            case TKit.TextStreamVerifier tv -> {
                return Optional.of(tv.copy().label(label())::apply);
            }
            case TKit.TextStreamVerifier.Group g -> {
                return g.copy().label(label()).tryCreate();
            }
            case CannedFormattedString[] strings -> {
                // Will look up the given strings in the order they are specified.
                return toStringIteratorConsumer(cmd, Stream.of(strings)
                        .map(cmd::getValue)
                        .map(TKit::assertTextStream)
                        .map(verifier -> {
                            return verifier.predicate(String::equals);
                        })
                        .reduce(TKit.TextStreamVerifier.group(),
                                TKit.TextStreamVerifier.Group::add,
                                TKit.TextStreamVerifier.Group::add));
            }
            case Consumer<?> c -> {
                return Optional.of((Consumer<Iterator<String>>)c);
            }
            default -> {
                throw ExceptionBox.reachedUnreachable();
            }
        }
    }

    private String label() {
        return stdout ? "'stdout'" : "'stderr'";
    }

    private static Consumer<Iterator<String>> stripTimestamps(Consumer<Iterator<String>> consumer, String label) {
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(label);
        return it -> {
            Objects.requireNonNull(it);
            TKit.trace(String.format("Strip timestamps in %s...", label));
            consumer.accept(new Iterator<String>() {

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public String next() {
                    var str = it.next();
                    var strippedStr = JPackageCommand.stripTimestamp(str);
                    if (str.length() == strippedStr.length()) {
                        throw new IllegalArgumentException(String.format("String [%s] doesn't have a timestamp", str));
                    } else {
                        return strippedStr;
                    }
                }

            });
            TKit.trace("Done");
        };
    }

    private static Consumer<Iterator<String>> matchTimestamps(Consumer<Iterator<String>> consumer, String label) {
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(label);
        return it -> {
            Objects.requireNonNull(it);
            TKit.trace(String.format("Match lines with timestamps in %s...", label));
            consumer.accept(new FilteringIterator<>(it, JPackageCommand::withTimestamp));
            TKit.trace("Done");
        };
    }

    final static class FilteringIterator<T> implements Iterator<T> {

        public FilteringIterator(Iterator<T> it, Predicate<T> predicate) {
            this.it = Objects.requireNonNull(it);
            this.predicate = Objects.requireNonNull(predicate);
        }

        @Override
        public boolean hasNext() {
            while (!nextReady && it.hasNext()) {
                var v = it.next();
                if (predicate.test(v)) {
                    next = v;
                    nextReady = true;
                }
            }
            return nextReady;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            nextReady = false;
            return next;
        }

        @Override
        public void remove() {
            it.remove();
        }

        private final Iterator<T> it;
        private final Predicate<T> predicate;
        private T next;
        private boolean nextReady;
    }

    boolean stdout = true;
    boolean matchTimestamps;
    boolean stripTimestamps;
    private final List<Object> validators = new ArrayList<>();
}
