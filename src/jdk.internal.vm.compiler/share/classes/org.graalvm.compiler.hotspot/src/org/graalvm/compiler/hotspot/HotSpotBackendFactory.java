/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.phases.tiers.CompilerConfiguration;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

public interface HotSpotBackendFactory {

    /**
     * Gets the name of this backend factory. This should not include the {@link #getArchitecture()
     * architecture}. The {@link CompilerConfigurationFactory} can select alternative backends based
     * on this name.
     */
    String getName();

    /**
     * Gets the class describing the architecture the backend created by this factory is associated
     * with.
     */
    Class<? extends Architecture> getArchitecture();

    HotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntime jvmciRuntime, HotSpotBackend host);
}
