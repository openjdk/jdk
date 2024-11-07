/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.model;

import jdk.jpackage.internal.util.LocalizedExceptionBuilder;
import jdk.jpackage.internal.util.StringBundle;

public class ConfigException extends Exception {
    private static final long serialVersionUID = 1L;
    final String advice;

    public ConfigException(String msg, String advice) {
        super(msg);
        this.advice = advice;
    }

    public ConfigException(String msg, String advice, Throwable cause) {
        super(msg, cause);
        this.advice = advice;
    }

    public ConfigException(Throwable cause) {
        super(cause);
        this.advice = null;
    }

    public String getAdvice() {
        return advice;
    }

    public static Builder build(StringBundle i18n) {
        return new Builder(i18n);
    }

    public static Builder build(StringBundle i18n, String msgId, Object ... args) {
        return build(i18n).message(msgId, args);
    }

    public static Builder build(StringBundle i18n, Throwable t) {
        return build(i18n).causeAndMessage(t);
    }

    public static class Builder extends LocalizedExceptionBuilder<Builder> {

        public Builder advice(String adviceId, Object ... args) {
            advice = formatString(adviceId, args);
            return this;
        }

        private Builder(StringBundle i18n) {
            super(i18n);
        }

        public ConfigException create() {
            return create(this::create);
        }

        private ConfigException create(String msg, Throwable cause) {
            return new ConfigException(msg, advice, cause);
        }

        private String advice;
    }

    public static RuntimeException rethrowConfigException(RuntimeException ex) throws ConfigException {
        if (ex.getCause() instanceof ConfigException configEx) {
            throw configEx;
        } else {
            throw ex;
        }
    }
}
