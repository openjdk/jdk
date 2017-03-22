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

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Count;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Dump;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Log;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.MethodFilter;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.MethodMeter;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Time;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.TrackMemUse;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Verify;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.debug.internal.DebugScope;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.runtime.JVMCI;

public class DebugEnvironment {

    /**
     * Create a GraalDebugConfig if {@link Debug#isEnabled()} is true and one hasn't already been
     * created. Additionally add {@code extraArgs} as capabilities to the {@link DebugDumpHandler}s
     * associated with the current config. Capabilities can be added at any time.
     *
     * @return the current {@link GraalDebugConfig} or null if nothing was done
     */
    public static GraalDebugConfig ensureInitialized(OptionValues options, Object... capabilities) {
        return ensureInitializedHelper(options, false, capabilities);
    }

    /**
     * Create a new GraalDebugConfig if {@link Debug#isEnabled()} is true, even if one had already
     * been created. Additionally add {@code extraArgs} as capabilities to the
     * {@link DebugDumpHandler}s associated with the current config. Capabilities can be added at
     * any time.
     *
     * @return the current {@link GraalDebugConfig} or null if nothing was done
     */
    public static GraalDebugConfig forceInitialization(OptionValues options, Object... capabilities) {
        return ensureInitializedHelper(options, true, capabilities);
    }

    private static GraalDebugConfig ensureInitializedHelper(OptionValues options, boolean forceInit, Object... capabilities) {
        if (!Debug.isEnabled()) {
            return null;
        }
        GraalDebugConfig debugConfig = (GraalDebugConfig) DebugScope.getConfig();
        if (debugConfig == null || forceInit) {
            // Initialize JVMCI before loading class Debug
            JVMCI.initialize();
            List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
            List<DebugVerifyHandler> verifyHandlers = new ArrayList<>();
            debugConfig = new GraalDebugConfig(
                            options,
                            Log.getValue(options),
                            Count.getValue(options),
                            TrackMemUse.getValue(options),
                            Time.getValue(options),
                            Dump.getValue(options),
                            Verify.getValue(options),
                            MethodFilter.getValue(options),
                            MethodMeter.getValue(options),
                            TTY.out, dumpHandlers, verifyHandlers);

            for (DebugConfigCustomizer customizer : GraalServices.load(DebugConfigCustomizer.class)) {
                customizer.customize(debugConfig);
            }

            Debug.setConfig(debugConfig);
        }
        if (capabilities != null) {
            for (Object o : capabilities) {
                for (DebugDumpHandler handler : debugConfig.dumpHandlers()) {
                    handler.addCapability(o);
                }
            }
        }
        return debugConfig;
    }
}
