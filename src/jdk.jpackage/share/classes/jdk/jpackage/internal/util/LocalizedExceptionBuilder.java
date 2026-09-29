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

import java.util.function.BiFunction;

/**
 * Builder of exceptions with localized messages.
 * @param <T> Subclass extending {@link LocalizedExceptionBuilder} class.
 */
public class LocalizedExceptionBuilder<T extends LocalizedExceptionBuilder<T>> {

    protected LocalizedExceptionBuilder(StringBundle i18n) {
        this.i18n = i18n;
    }

    /**
     * Creates an exception builder with the given source of error messages.
     *
     * @param i18n the source of error messages
     * @return the exception builder
     */
    public static LocalizedExceptionBuilder<?> buildLocalizedException(StringBundle i18n) {
        return new LocalizedExceptionBuilder<>(i18n);
    }

    /**
     * Creates an instance of type extending {@link Exception} class from the
     * configured message and cause.
     * <p>
     * Use {@link #message(String, Object...)}, {@link #causeAndMessage(Throwable)},
     * and {@link #cause(Throwable)} methods to initialize message and/or cause.
     *
     * @param <U>           the exception class
     * @param exceptionCtor the exception constructor
     * @return the exception
     */
    public final <U extends Exception> U create(BiFunction<String, Throwable, U> exceptionCtor) {
        return exceptionCtor.apply(msg, cause);
    }

    /**
     * Configures this builder if strings from the associated string bundle should
     * be used as patterns for message formatting or not.
     *
     * Affects the behavior of the subsequent {@link #message(String, Object...)}
     * calls.
     *
     * @param v <code>true</code> if strings from the associated string bundle
     *          should be used as patterns for message formatting by this builder or
     *          <code>false</code> otherwise
     * @return this
     *
     * @see #noformat()
     */
    public final T format(boolean v) {
        format = v;
        return thiz();
    }

    /**
     * A shortcut for <code>format(false)</code> call.
     *
     * @return this
     *
     * @see #format(boolean)
     */
    public final T noformat() {
        return format(false);
    }

    /**
     * Sets the message.
     *
     * @param msgId key of the string in the associated string bundle for the
     *              formatting pattern
     * @param args  the arguments for formatting message
     * @return this
     */
    public final T message(String msgId, Object... args) {
        msg = formatString(msgId, args);
        return thiz();
    }

    /**
     * Sets the cause.
     *
     * @param v the cause. A null value is permitted, and indicates that the cause
     *          is nonexistent or unknown.
     * @return this
     */
    public final T cause(Throwable v) {
        cause = v;
        return thiz();
    }

    /**
     * Sets the cause and the message. The message is copied from the given
     * {@link Throwable} object as is.
     *
     * @param t the cause. Must not be null.
     * @return this
     */
    public final T causeAndMessage(Throwable t) {
        boolean oldformat = format;
        return noformat().message(t.getMessage()).cause(t).format(oldformat);
    }

    protected final String formatString(String keyId, Object... args) {
        if (format) {
            return i18n.format(keyId, args);
        } else if (args.length == 0) {
            return keyId;
        } else {
            throw new IllegalArgumentException("Formatting arguments not allowed in no format mode");
        }
    }

    @SuppressWarnings("unchecked")
    private T thiz() {
        return (T)this;
    }

    private boolean format = true;
    private String msg;
    private Throwable cause;

    private final StringBundle i18n;
}
