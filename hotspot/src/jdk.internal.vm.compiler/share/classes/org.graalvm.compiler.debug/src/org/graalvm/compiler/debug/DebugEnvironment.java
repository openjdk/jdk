/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Dump;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Log;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Count;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.MethodFilter;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Time;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.TrackMemUse;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Verify;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.MethodMeter;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.runtime.JVMCI;

public class DebugEnvironment {

    public static GraalDebugConfig initialize(PrintStream log, Object... extraArgs) {
        // Initialize JVMCI before loading class Debug
        JVMCI.initialize();
        if (!Debug.isEnabled()) {
            log.println("WARNING: Scope debugging needs to be enabled with -esa");
            return null;
        }
        List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
        List<DebugVerifyHandler> verifyHandlers = new ArrayList<>();
        GraalDebugConfig debugConfig = new GraalDebugConfig(Log.getValue(), Count.getValue(), TrackMemUse.getValue(), Time.getValue(), Dump.getValue(), Verify.getValue(), MethodFilter.getValue(),
                        MethodMeter.getValue(),
                        log, dumpHandlers, verifyHandlers);

        for (DebugConfigCustomizer customizer : GraalServices.load(DebugConfigCustomizer.class)) {
            customizer.customize(debugConfig, extraArgs);
        }

        Debug.setConfig(debugConfig);
        return debugConfig;
    }
}
