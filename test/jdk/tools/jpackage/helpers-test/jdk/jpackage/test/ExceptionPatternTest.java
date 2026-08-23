/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.test.JUnitUtils.ArrayConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

class ExceptionPatternTest {

    @ParameterizedTest
    @CsvSource({
        ",true,true",
        "SKIP,true,true",
        "FOO,true,false",
        "NULL,false,true",
        "NULL_String,false,true",
        "NULL_CannedArgument,false,true",
        "BAR,false,false",
    })
    void test_match_message(MessageMode mode, boolean exWithMessageMatch, boolean exWithoutMessageMatch) {

        var exWithMessage = new Exception(MessageMode.FOO.name());
        var exWithoutMessage = new Exception();

        var builder = ExceptionPattern.build();
        Optional.ofNullable(mode).ifPresent(m -> {
            switch (m) {
                case NULL -> builder.expectNullMessage();
                case NULL_String -> builder.expectMessage((String)null);
                case NULL_CannedArgument -> builder.expectMessage((CannedArgument)null);
                case SKIP -> builder.expectMessage("hello").skipMessageCheck();
                default -> builder.expectMessage(m.name());
            }
        });

        var pattern = builder.create();

        assertEquals(exWithMessageMatch, pattern.match(exWithMessage));
        assertEquals(exWithoutMessageMatch, pattern.match(exWithoutMessage));
    }

    enum MessageMode {
        NULL_String,
        NULL_CannedArgument,
        NULL,
        FOO,
        BAR,
        SKIP,
    }

    @ParameterizedTest
    @CsvSource({
        "SKIP,true,true",
        ",true,true",
        "NULL,false,true",
        "NULL_Class,false,true",
        "NULL_ExceptionPattern,false,true",
        "IllegalArgumentException,true,false",
        "RuntimeException,true,false",
        "Exception,true,false",
        "IOException,false,false",
        "NullPointerException,false,false",
    })
    void test_match_cause(CauseMode mode, boolean exWithCauseMatch, boolean exWithoutCauseMatch) {

        var exWithCause = new Exception(new IllegalArgumentException());
        var exWithoutCause = new Exception();

        var builder = ExceptionPattern.build();

        Consumer<Class<? extends Exception>> addCause = type -> {
            builder.expectCause(ExceptionPattern.build().expectType(type).create());
        };

        Optional.ofNullable(mode).ifPresent(m -> {
            switch (m) {
                case NULL -> builder.expectNullCause();
                case NULL_Class -> builder.expectCause((Class<? extends Exception>)null);
                case NULL_ExceptionPattern -> builder.expectCause((ExceptionPattern)null);
                case IllegalArgumentException -> addCause.accept(IllegalArgumentException.class);
                case RuntimeException -> addCause.accept(RuntimeException.class);
                case Exception -> addCause.accept(Exception.class);
                case IOException -> addCause.accept(IOException.class);
                case NullPointerException -> addCause.accept(NullPointerException.class);
                case SKIP -> { addCause.accept(IOException.class); builder.skipCauseCheck(); }
            }
        });

        var pattern = builder.create();

        assertEquals(exWithCauseMatch, pattern.match(exWithCause));
        assertEquals(exWithoutCauseMatch, pattern.match(exWithoutCause));
    }

    enum CauseMode {
        NULL,
        NULL_Class,
        NULL_ExceptionPattern,
        IllegalArgumentException,
        RuntimeException,
        Exception,
        IOException,
        NullPointerException,
        SKIP,
    }

    @ParameterizedTest
    @CsvSource({
        ",true",
        "NULL,true",
        "SKIP,true",
        "IllegalArgumentException,true",
        "RuntimeException,true",
        "Exception,true",
        "IOException,false,false",
        "NullPointerException,false",
    })
    void test_match_type(TypeMode mode, boolean match) {

        var ex = new IllegalArgumentException();

        var builder = ExceptionPattern.build();

        Optional.ofNullable(mode).ifPresent(m -> {
            switch (m) {
                case NULL -> builder.expectType(null);
                case IllegalArgumentException -> builder.expectType(IllegalArgumentException.class);
                case RuntimeException -> builder.expectType(RuntimeException.class);
                case Exception -> builder.expectType(Exception.class);
                case IOException -> builder.expectType(IOException.class);
                case NullPointerException -> builder.expectType(NullPointerException.class);
                case SKIP -> builder.expectType(IOException.class).skipTypeCheck();
            }
        });

        assertEquals(match, builder.create().match(ex));
    }

    enum TypeMode {
        NULL,
        SKIP,
        IllegalArgumentException,
        RuntimeException,
        Exception,
        NullPointerException,
        IOException,
    }

    @ParameterizedTest
    @CsvSource(delimiter = ':', value = {
        "skip:skip::''",
        ":::message=[null], null-cause",
        "::java.lang.Exception:message=[null], null-cause, class java.lang.Exception",
        "skip:::null-cause",
        "foo:::message=[foo], null-cause",
        "bar:java.lang.Exception::message=[bar], cause=[class java.lang.Exception]",
        "skip:java.lang.Exception::cause=[class java.lang.Exception]",
        "skip:skip:java.lang.Exception:class java.lang.Exception",
        "skip:java.lang.Exception:java.io.IOException:cause=[class java.lang.Exception], class java.io.IOException",
        "bar::java.lang.Exception:message=[bar], null-cause, class java.lang.Exception",
        "skip::java.lang.Exception:null-cause, class java.lang.Exception",
        "bar:skip::message=[bar]",
    })
    void test_toString(String message, String cause, String type, String expected) {

        var builder = ExceptionPattern.build();

        if (!"skip".equals(message)) {
            builder.expectMessage(message);
        }

        Optional.ofNullable(type).map(toFunction(Class::forName)).map(c -> {
            @SuppressWarnings("unchecked")
            var reply = (Class<? extends Exception>)c;
            return reply;
        }).ifPresent(builder::expectType);

        if (!"skip".equals(cause)) {
            builder.expectCause(Optional.ofNullable(cause).map(toFunction(Class::forName)).map(c -> {
                @SuppressWarnings("unchecked")
                var reply = (Class<? extends Exception>)c;
                return reply;
            }).orElse(null));
        }

        assertEquals(expected, builder.create().toString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = ':', value = {
        ":true",
        "MESSAGE_BAR:false",
        "MESSAGE_FOO:true",
        "MESSAGE_BAR,TYPE_NullPointerException:false",
        "CAUSE_IOException:true",
        "CAUSE_NULL:false",
        "MESSAGE_FOO,CAUSE_IOException,CAUSE_MESSAGE_BAR:true",
        "MESSAGE_FOO,CAUSE_IOException,CAUSE_MESSAGE_FOO:false",
        "MESSAGE_FOO,CAUSE_IOException,CAUSE_MESSAGE_BAR,CAUSE_CAUSE_NullPointerException:true",
        "CAUSE_CAUSE_NullPointerException:true",
        "MESSAGE_FOO,CAUSE_IOException,CAUSE_MESSAGE_BAR,CAUSE_CAUSE_IllegalArgumentException:false",
        "MESSAGE_FOO,CAUSE_IOException,CAUSE_MESSAGE_BAR,CAUSE_CAUSE_MESSAGE_BAR:false",
    })
    void test_match_mismatchCallback(@ConvertWith(ArrayConverter.class) MismatchCallbackMode[] modes, boolean match) {

        var ex = new IllegalArgumentException("foo", new IOException("bar", new NullPointerException()));

        var builder = ExceptionPattern.build();
        Slot<ExceptionPattern.Builder> causeBuilder = Slot.createEmpty();
        Slot<ExceptionPattern.Builder> causeCauseBuilder = Slot.createEmpty();

        Function<Slot<ExceptionPattern.Builder>, ExceptionPattern.Builder> ensure = slot -> {
            if (slot.find().isEmpty()) {
                slot.set(ExceptionPattern.build());
            }
            return slot.get();
        };

        Supplier<ExceptionPattern.Builder> buildCause = () -> {
            return ensure.apply(causeBuilder);
        };

        Supplier<ExceptionPattern.Builder> buildCauseCause = () -> {
            return ensure.apply(causeCauseBuilder);
        };

        for (var mode : Optional.ofNullable(modes).map(List::of).orElseGet(List::of)) {
            switch (mode) {
                case MESSAGE_BAR -> builder.expectMessage("bar");
                case MESSAGE_FOO -> builder.expectMessage("foo");
                case TYPE_NullPointerException -> builder.expectType(NullPointerException.class);
                case CAUSE_NULL -> builder.expectNullCause();
                case CAUSE_IOException -> buildCause.get().expectType(IOException.class);
                case CAUSE_MESSAGE_BAR -> buildCause.get().expectMessage("bar");
                case CAUSE_MESSAGE_FOO -> buildCause.get().expectMessage("foo");
                case CAUSE_CAUSE_NullPointerException -> buildCauseCause.get().expectType(NullPointerException.class);
                case CAUSE_CAUSE_MESSAGE_BAR -> buildCauseCause.get().expectMessage("bar");
                case CAUSE_CAUSE_IllegalArgumentException -> buildCauseCause.get().expectType(IllegalArgumentException.class);
            }
        }

        causeCauseBuilder.find().ifPresent(cb -> {
            buildCause.get().expectCause(cb.create());
        });

        causeBuilder.find().ifPresent(cb -> {
            builder.expectCause(cb.create());
        });

        var mismatchCallback = new CountingRunnable();
        assertEquals(match, builder.create().match(ex, mismatchCallback));

        if (match) {
            assertEquals(0, mismatchCallback.counter);
        } else {
            assertEquals(1, mismatchCallback.counter);
        }
    }

    enum MismatchCallbackMode {
        MESSAGE_BAR,
        MESSAGE_FOO,
        TYPE_NullPointerException,
        CAUSE_NULL,
        CAUSE_IOException,
        CAUSE_MESSAGE_BAR,
        CAUSE_MESSAGE_FOO,
        CAUSE_CAUSE_NullPointerException,
        CAUSE_CAUSE_MESSAGE_BAR,
        CAUSE_CAUSE_IllegalArgumentException,
    }

    @Test
    void test_match_mismatchCallbackNotCalled() {

        var ex = new IllegalArgumentException("foo", new IOException("bar"));

        var pattern = ExceptionPattern.build().expectCause(
                copyWithIgnoreMismatchCallback(ExceptionPattern.build().expectMessage("foo").create())).create();

        var matchEerror = assertThrowsExactly(IllegalStateException.class, () -> {
            pattern.match(ex);
        });

        assertEquals("Mismatch callback not called!", matchEerror.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
        ",",
        "NON_RECURSIVE",
        "RECURSIVE",
        "RECURSIVE_IDENTITY_CAUSE",
        "RECURSIVE_NULL_CAUSE",
        "RECURSIVE_SKIP_CAUSE",
    })
    void test_resolveCannedArguments(ResolveCannedArgumentsMode mode) {

        var ex = new IllegalArgumentException("foo", new IOException("bar"));

        Slot<ExceptionPattern> pattern = Slot.createEmpty();

        Supplier<ExceptionPattern> causePattern = () -> {
            return ExceptionPattern.build()
                    .expectNullCause()
                    .expectMessage(CannedArgument.create(unsupported("Yikes!"), "%bar%"))
                    .expectType(IOException.class)
                    .create();
        };

        var builder = ExceptionPattern.build()
                .expectMessage(CannedArgument.create(unsupported("Kaput!"), "%foo%"))
                .expectCause(causePattern.get());

        Optional.ofNullable(mode).ifPresentOrElse(m -> {
            Function<CannedArgument, String> resolver = ca -> {
                return ca.toString().replaceAll("%", "");
            };

            pattern.set(switch (m) {
                case NON_RECURSIVE -> builder.create().resolveCannedArguments(resolver);
                case RECURSIVE_IDENTITY_CAUSE -> builder.expectCause(copyWithIdentityResolveCannedArgumentsRecursive(causePattern.get())).create().resolveCannedArgumentsRecursive(resolver);
                case RECURSIVE -> builder.create().resolveCannedArgumentsRecursive(resolver);
                case RECURSIVE_NULL_CAUSE -> builder.expectNullCause().create().resolveCannedArgumentsRecursive(resolver);
                case RECURSIVE_SKIP_CAUSE -> builder.skipCauseCheck().create().resolveCannedArgumentsRecursive(resolver);
            });
        }, () -> {
            pattern.set(builder.create());
        });

        Optional.ofNullable(mode).map(m -> {
            return switch (m) {
                case NON_RECURSIVE, RECURSIVE_IDENTITY_CAUSE -> Optional.of("Yikes!");
                case RECURSIVE_NULL_CAUSE, RECURSIVE_SKIP_CAUSE, RECURSIVE -> Optional.<String>empty();
            };
        }).orElseGet(() -> Optional.of("Kaput!")).ifPresentOrElse(expectedErrorMessage -> {
            var matchError = assertThrowsExactly(UnresolvedCannedArgument.class, () -> {
                pattern.get().match(ex);
            });
            assertEquals(expectedErrorMessage, matchError.getMessage());
        }, () -> {
            var expectedMatch = switch (mode) {
                case RECURSIVE_NULL_CAUSE -> false;
                default -> true;
            };
            assertEquals(expectedMatch, pattern.get().match(ex));
        });
    }

    enum ResolveCannedArgumentsMode {
        NON_RECURSIVE,
        RECURSIVE,
        RECURSIVE_IDENTITY_CAUSE,
        RECURSIVE_NULL_CAUSE,
        RECURSIVE_SKIP_CAUSE,
    }

    private static ExceptionPattern copyWithIgnoreMismatchCallback(ExceptionPattern pattern) {
        Objects.requireNonNull(pattern);
        return new ExceptionPattern() {

            @Override
            public Optional<Optional<CannedArgument>> message() {
                throw new AssertionError();
            }

            @Override
            public Optional<Optional<ExceptionPattern>> cause() {
                throw new AssertionError();
            }

            @Override
            public Optional<Class<? extends Exception>> type() {
                throw new AssertionError();
            }

            @Override
            public boolean match(Exception ex, Runnable mismatchCallback) {
                return pattern.match(ex, () -> {});
            }
        };
    }

    private static ExceptionPattern copyWithIdentityResolveCannedArgumentsRecursive(ExceptionPattern pattern) {
        Objects.requireNonNull(pattern);
        return new ExceptionPattern() {

            @Override
            public Optional<Optional<CannedArgument>> message() {
                return pattern.message();
            }

            @Override
            public Optional<Optional<ExceptionPattern>> cause() {
                throw new AssertionError();
            }

            @Override
            public Optional<Class<? extends Exception>> type() {
                throw new AssertionError();
            }

            @Override
            public ExceptionPattern resolveCannedArgumentsRecursive(Function<CannedArgument, String> mapper) {
                return this;
            }
        };
    }

    private static Supplier<Object> unsupported(String message) {
        return () -> { throw new UnresolvedCannedArgument(message); };
    };


    private static final class CountingRunnable implements Runnable {

        @Override
        public void run() {
            counter++;
        }

        private int counter;
    }


    private static final class UnresolvedCannedArgument extends RuntimeException {

        UnresolvedCannedArgument(String message) {
            super(message);
        }

        private static final long serialVersionUID = 1L;
    }
}
