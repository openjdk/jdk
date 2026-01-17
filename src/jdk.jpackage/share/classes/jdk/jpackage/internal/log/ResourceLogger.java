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
package jdk.jpackage.internal.log;

import java.util.Objects;
import java.util.function.Consumer;

public interface ResourceLogger extends Logger {

    void useResource(String localizedMsg);

    static ResourceLogger create(ConsoleLogger sink) {
        return new Details.DefaultLogger(sink.addTimestampsToOut().out());
    }

    static ResourceLogger create(System.Logger sink) {
        return new Details.DefaultLogger(Utils.toStringConsumer(sink, System.Logger.Level.INFO));
    }

    final static class Details {

        private Details() {
        }

        private record DefaultLogger(Consumer<String> sink) implements ResourceLogger {

            DefaultLogger {
                Objects.requireNonNull(sink);
            }

            @Override
            public void useResource(String localizedMsg) {
                Objects.requireNonNull(localizedMsg);
                sink.accept(localizedMsg);
            }

        }
    }

    static final ResourceLogger DISCARDING_LOGGER = Utils.discardingLogger(ResourceLogger.class);
}
