/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.core.GraalCompilerOptions.ExitVMOnException;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.CompileTheWorld;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;

/**
 * Tests {@link CompileTheWorld} functionality.
 */
public class CompileTheWorldTest extends GraalCompilerTest {

    @Test
    public void testJDK() throws Throwable {
        boolean originalSetting = ExitVMOnException.getValue(getInitialOptions());
        // Compile a couple classes in rt.jar
        HotSpotJVMCIRuntimeProvider runtime = HotSpotJVMCIRuntime.runtime();
        System.setProperty(CompileTheWorld.LIMITMODS_PROPERTY_NAME, "java.base");
        OptionValues initialOptions = getInitialOptions();
        EconomicMap<OptionKey<?>, Object> compilationOptions = CompileTheWorld.parseOptions("Inline=false");
        new CompileTheWorld(runtime, (HotSpotGraalCompiler) runtime.getCompiler(), CompileTheWorld.SUN_BOOT_CLASS_PATH, 1, 5, null, null, true, initialOptions, compilationOptions).compile();
        assert ExitVMOnException.getValue(initialOptions) == originalSetting;
    }
}
