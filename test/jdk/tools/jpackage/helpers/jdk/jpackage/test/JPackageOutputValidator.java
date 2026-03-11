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

/**
 * Validates a stream (stderr or stdout) in jpackage console output.
 */
public final class JPackageOutputValidator {

    public JPackageOutputValidator() {
    }

    public JPackageOutputValidator(JPackageOutputValidator other) {
        stdout = other.stdout;
        match = other.match;
        matchTimestamps = other.matchTimestamps;
        stripTimestamps = other.stripTimestamps;
        validators.addAll(other.validators);
    }

    JPackageOutputValidator copy() {
        return new JPackageOutputValidator(this);
    }

    /**
     * Configures this validator to validate stdout.
     * @return this
     */
    public JPackageOutputValidator stdout() {
        stdout = true;
        return this;
    }

    /**
     * Configures this validator to validate stderr.
     * @return this
     */
    public JPackageOutputValidator stderr() {
        stdout = false;
        return this;
    }

    /**
     * Configures the mode of this validator. Similar to how a regexp pattern can be
     * configured for matching or finding.
     *
     * @param v {@code true} if the entire stream should match the criteria of this
     *          validator, and {@code false} if the stream may contain additional
     *          content
     * @return this
     */
    public JPackageOutputValidator match(boolean v) {
        match = v;
        return this;
    }

    /**
     * A shorthand for {@code match(true)}.
     * @see #match(boolean)
     * @return this
     */
    public JPackageOutputValidator match() {
        return match(true);
    }

    /**
     * A shorthand for {@code match(false)}.
     * @see #match(boolean)
     * @return this
     */
    public JPackageOutputValidator find() {
        return match(false);
    }

    /**
     * Configures this validator to filter out lines without a timestamp in the
     * stream to be validated.
     *
     * @return this
     */
    public JPackageOutputValidator matchTimestamps() {
        matchTimestamps = true;
        return this;
    }

    /**
     * Configures this validator to strip the leading timestamp from lines the
     * stream to be validated.
     * <p>
     * If the stream contains lines without timestampts, the validation will fail.
     * <p>
     * Use {@link #matchTimestamps()) to filter out lines without timestamps and
     * prevent validation failure.
     *
     * @return this
     */
    public JPackageOutputValidator stripTimestamps() {
        stripTimestamps = true;
        return this;
    }

    public void applyTo(JPackageCommand cmd) {
        toResultConsumer(cmd).ifPresent(cmd::validateResult);
    }

    public void applyTo(JPackageCommand cmd, Executor.Result result) {
        Objects.requireNonNull(result);
        toResultConsumer(cmd).ifPresent(validator -> {
            validator.accept(result);
        });
    }

    public JPackageOutputValidator add(List<TKit.TextStreamVerifier> validators) {
        this.validators.addAll(validators);
        return this;
    }

    public JPackageOutputValidator add(TKit.TextStreamVerifier... validators) {
        return add(List.of(validators));
    }

    public JPackageOutputValidator validateEndOfStream() {
        validators.add(EndOfStreamValidator.INSTANCE);
        return this;
    }

    public JPackageOutputValidator expectMatchingStrings(List<CannedFormattedString> strings) {
        return expectMatchingStrings(strings.toArray(CannedFormattedString[]::new));
    }

    public JPackageOutputValidator expectMatchingStrings(CannedFormattedString... strings) {
        validators.add(strings);
        return this;
    }

    public JPackageOutputValidator add(JPackageOutputValidator other) {
        validators.addAll(other.validators);
        return this;
    }

    public JPackageOutputValidator compose(JPackageOutputValidator other) {
        if (stdout != other.stdout) {
            throw new IllegalArgumentException();
        }
        if (match != other.match) {
            throw new IllegalArgumentException();
        }
        if (matchTimestamps != other.matchTimestamps) {
            throw new IllegalArgumentException();
        }
        if (stripTimestamps != other.stripTimestamps) {
            throw new IllegalArgumentException();
        }
        return add(other);
    }

    private Optional<Consumer<Executor.Result>> toResultConsumer(JPackageCommand cmd) {
        return toStringIteratorConsumer(cmd).map(validator -> {
            return toResultConsumer(validator, stdout, match, label());
        });
    }

    private Optional<Consumer<Iterator<String>>> toStringIteratorConsumer(JPackageCommand cmd) {
        Objects.requireNonNull(cmd);

        var consumers = validators.stream().map(v -> {
            return toStringIteratorConsumer(cmd, v);
        }).flatMap(Optional::stream);

        if (match && (validators.isEmpty() || !(validators.getLast() instanceof EndOfStreamValidator))) {
            consumers = Stream.concat(consumers, Stream.of(TKit.assertEndOfTextStream(label())));
        }

        return consumers.reduce(Consumer::andThen).map(validator -> {

            if (stripTimestamps) {
                validator = stripTimestamps(validator, label());
            }

            if (matchTimestamps) {
                validator = matchTimestamps(validator, label());
            }

            return validator;
        });
    }

    @SuppressWarnings("unchecked")
    private Optional<Consumer<Iterator<String>>> toStringIteratorConsumer(JPackageCommand cmd, Object validator) {
        Objects.requireNonNull(cmd);
        switch (validator) {
            case TKit.TextStreamVerifier tv -> {
                return Optional.of(decorate(tv.copy().label(label())));
            }
            case CannedFormattedString[] strings -> {
                // Will look up the given strings in the order they are specified.
                return Stream.of(strings)
                        .map(cmd::getValue)
                        .map(TKit::assertTextStream)
                        .map(v -> {
                            return v.predicate(String::equals).label(label());
                        })
                        .map(this::decorate).reduce(Consumer::andThen);
            }
            case EndOfStreamValidator eos -> {
                return Optional.of(TKit.assertEndOfTextStream(label()));
            }
            default -> {
                throw ExceptionBox.reachedUnreachable();
            }
        }
    }

    private String label() {
        return stdout ? "'stdout'" : "'stderr'";
    }

    private Consumer<Iterator<String>> decorate(TKit.TextStreamVerifier validator) {
        if (match) {
            return new OneLineFeeder(validator::apply);
        } else {
            return validator::apply;
        }
    }

    private static Consumer<Executor.Result> toResultConsumer(
            Consumer<Iterator<String>> validator, boolean stdout, boolean match, String label) {
        Objects.requireNonNull(validator);
        Objects.requireNonNull(label);

        return result -> {
            List<String> content;
            if (stdout) {
                content = result.stdout();
            } else {
                content = result.stderr();
            }

            if (match) {
                TKit.trace(String.format("Checking %s for exact match against defined validators...", label));
            }

            validator.accept(content.iterator());

        };
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

    private enum EndOfStreamValidator {
        INSTANCE
    }

    private static final class FilteringIterator<T> implements Iterator<T> {

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

    private record OneLineFeeder(Consumer<Iterator<String>> consumer) implements Consumer<Iterator<String>> {
        OneLineFeeder {
            Objects.requireNonNull(consumer);
        }

        @Override
        public void accept(Iterator<String> it) {
            consumer.accept(List.of(it.next()).iterator());
        }
    }

    boolean stdout = true;
    boolean match;
    boolean matchTimestamps;
    boolean stripTimestamps;
    private final List<Object> validators = new ArrayList<>();
}
