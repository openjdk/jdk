/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines the JDK-specific HTTP server API, and provides the jwebserver tool
 * for running a minimal HTTP server.
 *
 * <p>The {@link com.sun.net.httpserver} package defines a high-level API for
 * building servers that support HTTP and HTTPS. The SimpleFileServer class
 * implements a simple HTTP-only file server intended for testing, development
 * and debugging purposes. A default implementation is provided via the
 * {@code jwebserver} tool and the main entry point of the module, which can
 * also be invoked with {@code java -m jdk.httpserver}.
 *
 * <p>The {@link com.sun.net.httpserver.spi} package specifies a Service Provider
 * Interface (SPI) for locating HTTP server implementations based on the
 * {@code com.sun.net.httpserver} API.
 *
 * @toolGuide jwebserver
 *
 * @uses com.sun.net.httpserver.spi.HttpServerProvider
 *
 * @moduleGraph
 * @since 9
 */
module jdk.httpserver {

    exports com.sun.net.httpserver;
    exports com.sun.net.httpserver.spi;

    uses com.sun.net.httpserver.spi.HttpServerProvider;
}
