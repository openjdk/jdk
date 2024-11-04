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

    public static Builder build() {
        return new Builder();
    }
    
    public static Builder build(String msgId, Object ... args) {
        return build().message(msgId, args);
    }
    
    public static Builder build(Throwable t) {
        return build().causeAndMessage(t);
    }

    public static final class Builder {

        private Builder() {
        }

        public ConfigException create() {
            return new ConfigException(msg, advice, cause);
        }

        public Builder format(boolean v) {
            noFormat = !v;
            return this;
        }

        public Builder noformat() {
            return format(false);
        }

        public Builder message(String msgId, Object ... args) {
            msg = formatString(msgId, args);
            return this;
        }

        public Builder advice(String adviceId, Object ... args) {
            advice = formatString(adviceId, args);
            return this;
        }

        public Builder cause(Throwable v) {
            cause = v;
            return this;
        }

        public Builder causeAndMessage(Throwable t) {
            var oldNoFormat = noFormat;
            return noformat().message(t.getMessage()).cause(t).format(oldNoFormat);
        }

        private String formatString(String keyId, Object ... args) {
            if (!noFormat) {
                return I18N.format(keyId, args);
            } if (args.length == 0) {
                return keyId;
            } else {
                throw new IllegalArgumentException("Formatting arguments not allowed in no format mode");
            }
        }

        private boolean noFormat;
        private String msg;
        private String advice;
        private Throwable cause;
    }

    public static RuntimeException rethrowConfigException(RuntimeException ex) throws ConfigException {
        if (ex.getCause() instanceof ConfigException configEx) {
            throw configEx;
        } else {
            throw ex;
        }
    }
}
