/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

module jdk.hotspot.agent {
    requires java.datatransfer;
    requires java.desktop;
    requires java.rmi;
    requires java.scripting;
    requires jdk.jcmd;
    requires jdk.jdi;

    // RMI needs to serialize types in this package
    exports sun.jvm.hotspot.debugger.remote to java.rmi;
    provides com.sun.jdi.connect.Connector with sun.jvm.hotspot.jdi.SACoreAttachingConnector;
    provides com.sun.jdi.connect.Connector with sun.jvm.hotspot.jdi.SADebugServerAttachingConnector;
    provides com.sun.jdi.connect.Connector with sun.jvm.hotspot.jdi.SAPIDAttachingConnector;

    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.JStack;
    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.JInfo;
    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.ClassLoaderStats;
    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.FinalizerInfo;
    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.HeapDumper;
    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.HeapSummary;
    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.ObjectHistogram;
    provides jdk.internal.vm.agent.spi.ToolProvider with sun.jvm.hotspot.tools.PMap;
}

