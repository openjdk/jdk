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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jdk.jpackage.internal.util.Slot;


public interface ExceptionPattern {

    Optional<Optional<CannedArgument>> message();
    Optional<Optional<ExceptionPattern>> cause();
    Optional<Class<? extends Exception>> type();

    default boolean match(Exception ex, Runnable mismatchCallback) {
        Objects.requireNonNull(ex);

        Function<Boolean, Boolean> exit = result -> {
            if (!result) {
                mismatchCallback.run();
            }
            return result;
        };

        if (!message().map(message -> {
            return exit.apply(Objects.equals(message.map(CannedArgument::getValue).orElse(null), ex.getMessage()));
        }).orElse(true)) {
            return false;
        }

        if (!type().map(type -> {
            return exit.apply(type.isInstance(ex));
        }).orElse(true)) {
            return false;
        }

        if (!cause().map(cause -> {
            var actualCause = (Exception)ex.getCause();
            if (actualCause == null != cause.isEmpty()) {
                return exit.apply(false);
            } else {
                Slot<Boolean> nestedMismatchCallbackCalled = Slot.createEmpty();
                nestedMismatchCallbackCalled.set(false);
                var matched = cause.map(v -> {
                    return v.match(actualCause, () -> {
                        nestedMismatchCallbackCalled.set(true);
                        mismatchCallback.run();
                    });
                }).orElse(true);
                if (!nestedMismatchCallbackCalled.get() && !matched) {
                    throw new IllegalStateException("Mismatch callback not called!");
                } else {
                    return matched;
                }
            }
        }).orElse(true)) {
            return false;
        };

        return true;
    }

    default boolean match(Exception ex) {
        return match(ex, () -> {});
    }

    default ExceptionPattern resolveCannedArguments(Function<CannedArgument, String> mapper) {
        Objects.requireNonNull(mapper);
        return message().map(message -> {
            return message.map(mapper::apply).map(this::copyWithMessage).orElse(this);
        }).orElse(this);
    }

    default ExceptionPattern resolveCannedArgumentsRecursive(Function<CannedArgument, String> mapper) {
        var resolved = resolveCannedArguments(mapper);
        return resolveCannedArguments(mapper).cause().map(cause -> {
            return cause.map(v -> {
                var resolvedCause = v.resolveCannedArgumentsRecursive(mapper);
                if (resolvedCause == v) {
                    return resolved;
                } else {
                    return resolved.copyWithCause(resolvedCause);
                }
            }).orElse(resolved);
        }).orElse(resolved);
    }

    default ExceptionPattern copyWithMessage(String message) {
        return build().initFrom(this).expectMessage(message).create();
    }

    default ExceptionPattern copyWithCause(ExceptionPattern cause) {
        return build().initFrom(this).expectCause(cause).create();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        public Builder initFrom(ExceptionPattern pattern) {
            pattern.message().ifPresentOrElse(v -> {
                message = v;
            }, this::skipMessageCheck);
            pattern.cause().ifPresentOrElse(v -> {
                cause = v;
            }, this::skipCauseCheck);
            pattern.type().ifPresentOrElse(v -> {
                type = v;
            }, this::skipTypeCheck);
            return this;
        }

        public Builder expectMessage(String v) {
            return expectMessage(CannedArgument.ofString(v));
        }

        public Builder expectMessage(CannedArgument v) {
            message = Optional.ofNullable(v);
            return this;
        }

        public Builder expectNullMessage() {
            message = Optional.empty();
            return this;
        }

        public Builder expectType(Class<? extends Exception> v) {
            type = v;
            return this;
        }

        public Builder expectCause(ExceptionPattern v) {
            cause = Optional.ofNullable(v);
            return this;
        }

        public Builder expectCause(Class<? extends Exception> v) {
            return expectCause(Optional.ofNullable(v).map(type -> {
                return build().expectType(type).create();
            }).orElse(null));
        }

        public Builder expectNullCause() {
            cause = Optional.empty();
            return this;
        }

        public Builder skipMessageCheck() {
            message = null;
            return this;
        }

        public Builder skipCauseCheck() {
            cause = null;
            return this;
        }

        public Builder skipTypeCheck() {
            return expectType(null);
        }

        public ExceptionPattern create() {
            return new Stub(Optional.ofNullable(message), Optional.ofNullable(cause), Optional.ofNullable(type));
        }

        private Optional<CannedArgument> message;
        private Optional<ExceptionPattern> cause;
        private Class<? extends Exception> type;
    }

    public static void printNullableProperty(Optional<? extends Optional<?>> property, Optional<String> label, StringBuilder sb) {
        Objects.requireNonNull(label);
        Objects.requireNonNull(sb);
        if (label.isEmpty() && property.isEmpty()) {
            throw new IllegalArgumentException();
        }

        property.ifPresent(v -> {
            if (!sb.isEmpty() && (label.isPresent() || v.isPresent())) {
                sb.append(", ");
            }
            v.ifPresentOrElse(vv -> {
                label.ifPresent(l -> {
                    sb.append(l).append("=[");
                });
                sb.append(vv.toString());
                label.ifPresent(l -> {
                    sb.append(']');
                });
            }, () -> {
                label.ifPresent(l -> {
                    sb.append("null-").append(l);
                });
            });
        });
    }

    record Stub(
            Optional<Optional<CannedArgument>> message,
            Optional<Optional<ExceptionPattern>> cause,
            Optional<Class<? extends Exception>> type) implements ExceptionPattern {

        public Stub {
            Objects.requireNonNull(message);
            Objects.requireNonNull(cause);
            Objects.requireNonNull(type);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            printNullableProperty(message, Optional.of("message"), sb);
            printNullableProperty(cause, Optional.of("cause"), sb);
            printNullableProperty(Optional.of(type), Optional.empty(), sb);
            return sb.toString();
        }
    }
}
