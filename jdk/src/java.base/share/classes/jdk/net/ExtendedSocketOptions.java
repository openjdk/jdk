/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.net;

import java.net.SocketOption;

/**
 * Defines extended socket options, beyond those defined in
 * {@link java.net.StandardSocketOptions}. These options may be platform
 * specific.
 *
 * @since 1.8
 */
@jdk.Exported
public final class ExtendedSocketOptions {

    private static class ExtSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;
        ExtSocketOption(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }
        @Override public String name() { return name; }
        @Override public Class<T> type() { return type; }
        @Override public String toString() { return name; }
    }

    private ExtendedSocketOptions() {}

    /**
     * Service level properties. When a security manager is installed,
     * setting or getting this option requires a {@link NetworkPermission}
     * {@code ("setOption.SO_FLOW_SLA")} or {@code "getOption.SO_FLOW_SLA"}
     * respectively.
     */
    public static final SocketOption<SocketFlow> SO_FLOW_SLA = new
        ExtSocketOption<SocketFlow>("SO_FLOW_SLA", SocketFlow.class);
}
