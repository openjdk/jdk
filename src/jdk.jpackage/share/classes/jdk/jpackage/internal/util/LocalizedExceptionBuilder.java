/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

public class LocalizedExceptionBuilder<T extends LocalizedExceptionBuilder<T>> {

    protected LocalizedExceptionBuilder(StringBundle i18n) {
        this.i18n = i18n;
    }

    public static <R extends LocalizedExceptionBuilder<R>> R buildLocalizedException(StringBundle i18n) {
        return new LocalizedExceptionBuilder<R>(i18n).thiz();
    }

    final public <U extends Exception> U create(BiFunction<String, Throwable, U> exceptionCtor) {
        return exceptionCtor.apply(msg, cause);
    }

    final public T format(boolean v) {
        noFormat = !v;
        return thiz();
    }

    final public T noformat() {
        return format(false);
    }

    final public T message(String msgId, Object... args) {
        msg = formatString(msgId, args);
        return thiz();
    }

    final public T cause(Throwable v) {
        cause = v;
        return thiz();
    }

    final public T causeAndMessage(Throwable t) {
        boolean oldNoFormat = noFormat;
        return noformat().message(t.getMessage()).cause(t).format(oldNoFormat);
    }

    final protected String formatString(String keyId, Object... args) {
        if (!noFormat) {
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

    private boolean noFormat;
    private String msg;
    private Throwable cause;

    private final StringBundle i18n;
}
