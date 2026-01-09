/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.util.CompositeProxy;

interface ObjectFactory extends ExecutorFactory, RetryExecutorFactory {

    static ObjectFactory.Builder build() {
        return new Builder();
    }

    static ObjectFactory.Builder build(ObjectFactory from) {
        return build().initFrom(from);
    }

    static final class Builder {
        private Builder() {
        }

        ObjectFactory create() {
            return CompositeProxy.build().invokeTunnel(CompositeProxyTunnel.INSTANCE).create(
                    ObjectFactory.class,
                    Optional.ofNullable(executorFactory).orElse(ExecutorFactory.DEFAULT),
                    Optional.ofNullable(retryExecutorFactory).orElse(RetryExecutorFactory.DEFAULT));
        }

        Builder initFrom(ObjectFactory of) {
            Objects.requireNonNull(of);
            return executorFactory(of).retryExecutorFactory(of);
        }

        Builder executorFactory(ExecutorFactory v) {
            executorFactory = v;
            return this;
        }

        Builder retryExecutorFactory(RetryExecutorFactory v) {
            retryExecutorFactory = v;
            return this;
        }

        private ExecutorFactory executorFactory;
        private RetryExecutorFactory retryExecutorFactory;
    }

    static final ObjectFactory DEFAULT = build().create();
}
