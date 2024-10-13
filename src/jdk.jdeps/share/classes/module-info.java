/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.javac.ParticipatesInPreview;

/**
 * Defines tools for analysing dependencies in Java libraries and programs,
 * including the <em>{@index jdeps jdeps tool}</em>,
 * <em>{@index javap javap tool}</em>,
 * <em>{@index jdeprscan jdeprscan tool}</em>, and
 * <em>{@index jnativescan jnativescan tool}</em> tools.
 *
 * <p>
 * This module provides the equivalent of command-line access to the
 * <em>javap</em>, <em>jdeps</em>, and <em>jnativescan</em> tools via the
 * {@link java.util.spi.ToolProvider ToolProvider} service provider
 * interface (SPI)</p>
 *
 * <p> Instances of the tools can be obtained by calling
 * {@link java.util.spi.ToolProvider#findFirst ToolProvider.findFirst}
 * or the {@linkplain java.util.ServiceLoader service loader} with the name
 * {@code "javap"} or {@code "jdeps"} as appropriate.
 *
 * <p>
 * <em>jdeprscan</em> only exists as a command line tool, and does not provide
 * any direct API.
 *
 * @toolGuide javap
 * @toolGuide jdeprscan
 * @toolGuide jdeps
 * @toolGuide jnativescan
 *
 * @provides java.util.spi.ToolProvider
 *     Use {@link java.util.spi.ToolProvider#findFirst ToolProvider.findFirst("javap")},
 *     {@link java.util.spi.ToolProvider#findFirst ToolProvider.findFirst("jdeps")},
 *     or {@link java.util.spi.ToolProvider#findFirst ToolProvider.findFirst("jnativescan")}
 *     to obtain an instance of a {@code ToolProvider} that provides the equivalent
 *     of command-line access to the {@code javap}, {@code jdeps}, {@code jnativescan} tool.
 *
 * @moduleGraph
 * @since 9
 */
@ParticipatesInPreview
module jdk.jdeps {
    requires java.compiler;
    requires jdk.compiler;
    requires jdk.internal.opt;

    uses com.sun.tools.javac.platform.PlatformProvider;

    exports com.sun.tools.classfile to jdk.jlink;

    provides java.util.spi.ToolProvider with
        com.sun.tools.javap.Main.JavapToolProvider,
        com.sun.tools.jdeps.Main.JDepsToolProvider,
        com.sun.tools.jnativescan.Main.Provider;
}
