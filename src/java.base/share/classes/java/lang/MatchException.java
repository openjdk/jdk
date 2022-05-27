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

import jdk.internal.javac.PreviewFeature;

/**
 * Thrown to indicate an unexpected failure in pattern matching.
 *
 * <p>{@code MatchException} may be thrown when an exhaustive pattern matching language construct
 * (such as a switch expression) encounters a value that does not match any of the provided
 * patterns at runtime. This can arise from a number of cases:
 * <ul>
 *    <li>Separate compilation anomalies, where a sealed interface has a different set of permitted
 *        subtypes at runtime than it had at compilation time, an enum has a different set of
 *        constants at runtime than it had at compilation time, or the type hierarchy has changed
 *        in incompatible ways between compile time and run time.</li>
 *    <li>{@code null} values and nested patterns using sealed types. If an interface or abstract
 *        class {@code C} is sealed to permit {@code A} and {@code B}, then the set of record
 *        patterns {@code R(A a)} and {@code R(B b)} are exhaustive on a record {@code R} whose
 *        sole component is of type {@code C}, but neither of these patterns will match
 *        {@code new R(null)}.</li>
 *    <li>Null targets and nested record patterns. Given a record type {@code R} whose sole
 *        component is {@code S}, which in turn is a record whose sole component is {@code String},
 *        then the nested record pattern {@code R(S(String s))} will not match {@code new R(null)}.</li>
 * </ul>
 *
 * <p>Match failures arising from unexpected inputs will generally throw {@code MatchException} only
 * after all patterns have been tried; even if {@code R(S(String s))} does not match
 * {@code new R(null)}, a later pattern (such as {@code R r}) may still match the target.
 *
 * <p>MatchException may also be thrown when operations performed as part of pattern matching throw
 * an unexpected exception. For example, pattern matching may cause methods such as record component
 * accessors to be implicitly invoked in order to extract pattern bindings. If these methods throw
 * an exception, execution of the pattern matching construct may fail with {@code MatchException}.
 * The original exception will be set as a {@link Throwable#getCause() cause} of
 * the {@code MatchException}. No {@link Throwable#addSuppressed(java.lang.Throwable) suppressed}
 * exceptions will be recorded.
 *
 * @jls 14.11.3 Execution of a switch Statement
 * @jls 14.30.2 Pattern Matching
 * @jls 15.28.2 Run-Time Evaluation of switch Expressions
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.SWITCH_PATTERN_MATCHING)
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
