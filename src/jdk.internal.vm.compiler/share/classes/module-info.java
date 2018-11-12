/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

module jdk.internal.vm.compiler {
    requires java.instrument;
    requires java.management;
    requires jdk.internal.vm.ci;
    requires jdk.management;
    requires jdk.unsupported;   // sun.misc.Unsafe is used

    uses org.graalvm.compiler.code.DisassemblerProvider;
    uses org.graalvm.compiler.core.match.MatchStatementSet;
    uses org.graalvm.compiler.debug.DebugHandlersFactory;
    uses org.graalvm.compiler.debug.TTYStreamProvider;
    uses org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
    uses org.graalvm.compiler.hotspot.HotSpotBackendFactory;
    uses org.graalvm.compiler.hotspot.HotSpotCodeCacheListener;
    uses org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
    uses org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;
    uses org.graalvm.compiler.phases.common.jmx.HotSpotMBeanOperationProvider;
    uses org.graalvm.compiler.serviceprovider.JMXService;

    exports jdk.internal.vm.compiler.collections        to jdk.internal.vm.compiler.management;
    exports org.graalvm.compiler.api.directives         to jdk.aot;
    exports org.graalvm.compiler.api.runtime            to jdk.aot;
    exports org.graalvm.compiler.api.replacements       to jdk.aot;
    exports org.graalvm.compiler.asm.amd64              to jdk.aot;
    exports org.graalvm.compiler.asm.aarch64            to jdk.aot;
    exports org.graalvm.compiler.bytecode               to jdk.aot;
    exports org.graalvm.compiler.code                   to jdk.aot;
    exports org.graalvm.compiler.core                   to jdk.aot;
    exports org.graalvm.compiler.core.common            to
        jdk.aot,
        jdk.internal.vm.compiler.management;
    exports org.graalvm.compiler.core.target            to jdk.aot;
    exports org.graalvm.compiler.debug                  to
        jdk.aot,
        jdk.internal.vm.compiler.management;
    exports org.graalvm.compiler.graph                  to jdk.aot;
    exports org.graalvm.compiler.hotspot                to
        jdk.aot,
        jdk.internal.vm.compiler.management;
    exports org.graalvm.compiler.hotspot.meta           to jdk.aot;
    exports org.graalvm.compiler.hotspot.replacements   to jdk.aot;
    exports org.graalvm.compiler.hotspot.stubs          to jdk.aot;
    exports org.graalvm.compiler.hotspot.word           to jdk.aot;
    exports org.graalvm.compiler.java                   to jdk.aot;
    exports org.graalvm.compiler.lir.asm                to jdk.aot;
    exports org.graalvm.compiler.lir.phases             to jdk.aot;
    exports org.graalvm.compiler.nodes                  to jdk.aot;
    exports org.graalvm.compiler.nodes.graphbuilderconf to jdk.aot;
    exports org.graalvm.compiler.options                to
        jdk.aot,
        jdk.internal.vm.compiler.management;
    exports org.graalvm.compiler.phases                 to jdk.aot;
    exports org.graalvm.compiler.phases.common.jmx      to jdk.internal.vm.compiler.management;
    exports org.graalvm.compiler.phases.tiers           to jdk.aot;
    exports org.graalvm.compiler.printer                to jdk.aot;
    exports org.graalvm.compiler.runtime                to jdk.aot;
    exports org.graalvm.compiler.replacements           to jdk.aot;
    exports org.graalvm.compiler.serviceprovider        to
        jdk.aot,
        jdk.internal.vm.compiler.management;
    exports org.graalvm.compiler.word                   to jdk.aot;
    exports jdk.internal.vm.compiler.word               to jdk.aot;
}
