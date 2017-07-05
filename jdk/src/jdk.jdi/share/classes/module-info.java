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

module jdk.jdi {
    requires jdk.attach;

    exports com.sun.jdi;
    exports com.sun.jdi.connect;
    exports com.sun.jdi.connect.spi;
    exports com.sun.jdi.event;
    exports com.sun.jdi.request;
    exports com.sun.tools.jdi to jdk.hotspot.agent;

    uses com.sun.jdi.connect.Connector;
    uses com.sun.jdi.connect.spi.TransportService;

    // windows shared memory connector providers are added at build time
    provides com.sun.jdi.connect.Connector with com.sun.tools.jdi.ProcessAttachingConnector;
    provides com.sun.jdi.connect.Connector with com.sun.tools.jdi.RawCommandLineLauncher;
    provides com.sun.jdi.connect.Connector with com.sun.tools.jdi.SocketAttachingConnector;
    provides com.sun.jdi.connect.Connector with com.sun.tools.jdi.SocketListeningConnector;
    provides com.sun.jdi.connect.Connector with com.sun.tools.jdi.SunCommandLineLauncher;
}

