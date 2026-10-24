/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util.function;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ExceptionBox extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static RuntimeException toUnchecked(Exception ex) {
        switch (ex) {
            case RuntimeException rex -> {
                return rex;
            }
            case InvocationTargetException itex -> {
                var t = itex.getCause();
                if (t instanceof Exception cause) {
                    return toUnchecked(cause);
                } else {
                    throw (Error)t;
                }
            }
            case InterruptedException _ -> {
                Thread.currentThread().interrupt();
                return new ExceptionBox(ex);
            }
            default -> {
                return new ExceptionBox(ex);
            }
        }
    }

    public static Exception unbox(Throwable t) {
        switch (t) {
            case ExceptionBox ex -> {
                return unbox(ex.getCause());
            }
            case InvocationTargetException ex -> {
                return unbox(ex.getCause());
            }
            case Exception ex -> {
                return ex;
            }
            case Error err -> {
                throw err;
            }
            default -> {
                // Unreachable
                throw reachedUnreachable();
            }
        }
    }

    /**
     * Unboxes the specified throwable and its suppressed throwables recursively.
     * <p>
     * Calls {@link #unbox(Throwable)} on the specified throwable and nested
     * suppressed throwables, passing the result to the {@code visitor}.
     * <p>
     * Throwables will be traversed in the "cause before consequence" order. E.g.:
     * say exception "A" suppresses exceptions "B" and "C", and "B" suppresses "D".
     * The traverse order will be "D", "B", "C", "A".
     * <p>
     * If the method encounters cyclic suppressed throwables, it will fall into an
     * infinite recursion loop, eventually causing a {@code StackOverflowError}.
     * <p>
     * If the specified throwable or any of its nested suppressed throwables are of
     * type {@link Error}, the method will keep notifying the {@code visitor} until
     * it hits the first such throwable. When it happens, the method will throw this
     * throwable.
     *
     * @param t       the exception to visit
     * @param visitor the callback to apply to every subsequently visited exception
     */
    public static void visitUnboxedExceptionsRecursively(Throwable t, Consumer<Exception> visitor) {
        Objects.requireNonNull(t);
        Objects.requireNonNull(visitor);

        var ex = unbox(t);

        Stream.of(ex.getSuppressed()).forEach(suppressed -> {
            visitUnboxedExceptionsRecursively(suppressed, visitor);
        });

        visitor.accept(ex);
    }

    public static Error reachedUnreachable() {
        return new AssertionError("Reached unreachable!");
    }

    private ExceptionBox(Exception ex) {
        super(ex);
    }
}
