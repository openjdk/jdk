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

package java.lang;

/**
 * Thrown to indicate an unexpected failure in pattern matching.
 *
 * <p>{@code MatchException} may be thrown when an exhaustive pattern matching
 * language construct (such as a {@code switch} expression) encounters a value
 * that does not match any of the specified patterns at run time, even though
 * the construct has been deemed exhaustive. This is intentional and can arise
 * from a number of cases:
 *
 * <ul>
 *     <li>Separate compilation anomalies, where parts of the type hierarchy that
 *         the patterns reference have been changed, but the pattern matching
 *         construct has not been recompiled. For example, if a sealed interface
 *         has a different set of permitted subtypes at run time than it had at
 *         compile time, or if an enum class has a different set of enum constants
 *         at runtime than it had at compile time, or if the type hierarchy has
 *         been changed in some incompatible way between compile time and run time.</li>
 *
 *     <li>{@code null} values and nested patterns involving sealed classes. If,
 *         for example, an interface {@code I} is {@code sealed} with two permitted
 *         subclasses {@code A} and {@code B}, and a record class {@code R} has a
 *         single component of type {@code I}, then the two record patterns {@code
 *         R(A a)} and {@code R(B b)} together are considered to be exhaustive for
 *         the type {@code R}, but neither of these patterns will match against the
 *         result of {@code new R(null)}.</li>
 *
 *     <li>{@code null} values and nested record patterns. Given a record class
 *         {@code S} with a single component of type {@code T}, where {@code T} is
 *         another record class with a single component of type {@code String},
 *         then the nested record pattern {@code R(S(var s))} is considered
 *         exhaustive for the type {@code R} but it does not match against the
 *         result of {@code new R(null)} (whereas it does match against the result
 *         of {@code new R(new S(null))} does).</li>
 * </ul>
 *
 * <p>{@code MatchException} may also be thrown by the process of pattern matching
 * a value against a pattern. For example, pattern matching involving a record
 * pattern may require accessor methods to be implicitly invoked in order to
 * extract the component values. If any of these accessor methods throws an
 * exception, pattern matching completes abruptly and throws {@code
 * MatchException}. The original exception will be set as a {@link
 * Throwable#getCause() cause} of the {@code MatchException}. No {@link
 * Throwable#addSuppressed(java.lang.Throwable) suppressed} exceptions will be
 * recorded.
 *
 * @jls 14.11.3 Execution of a {@code switch} Statement
 * @jls 14.30.2 Pattern Matching
 * @jls 15.28.2 Run-Time Evaluation of {@code switch} Expressions
 *
 * @since 21
 */
public final class MatchException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 0L;

    /**
     * Constructs an {@code MatchException} with the specified detail message and
     * cause.
     *
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method). (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public MatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
