/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.util.Collections;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.hotspot.meta.HotSpotJITClassInitializationPlugin;
import org.graalvm.compiler.java.LambdaUtils;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class LambdaStableNameTest {
    private String findStableLambdaName(ResolvedJavaType type) {
        OptionValues options = new OptionValues(OptionValues.newOptionMap());
        DebugContext debug = new Builder(options, Collections.emptyList()).build();
        GraalJVMCICompiler compiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        Providers providers = compiler.getGraalRuntime().getCapability(RuntimeProvider.class).getHostBackend().getProviders();
        final HotSpotJITClassInitializationPlugin initializationPlugin = new HotSpotJITClassInitializationPlugin();
        return LambdaUtils.findStableLambdaName(initializationPlugin, providers, type, options, debug, this);
    }

    @Test
    public void checkStableLamdaNameForRunnableAndAutoCloseable() {
        Runnable r = this::checkStableLamdaNameForRunnableAndAutoCloseable;
        ResolvedJavaType rType = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(r.getClass());

        String name = findStableLambdaName(rType);
        assertLambdaName(name);

        AutoCloseable ac = this::checkStableLamdaNameForRunnableAndAutoCloseable;
        ResolvedJavaType acType = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(ac.getClass());
        String acName = findStableLambdaName(acType);
        assertEquals("Both stable lambda names are the same as they reference the same method", name, acName);

        assertEquals("The name known in 19.3 version is computed", "Lorg/graalvm/compiler/hotspot/test/LambdaStableNameTest$$Lambda$3b571858be38d19370199ac2c3ec212a511e6f55;", name);
    }

    private static void assertLambdaName(String name) {
        String expectedPrefix = "L" + LambdaStableNameTest.class.getCanonicalName().replace('.', '/') +
                        "$$Lambda$";
        if (!name.startsWith(expectedPrefix)) {
            fail("Expecting " + expectedPrefix + " as prefix in lambda class name: " + name);
        }
        assertTrue("semicolon at the end", name.endsWith(";"));

        int last = name.lastIndexOf('$');

        String hash = name.substring(last + 1, name.length() - 1);

        BigInteger aValue = new BigInteger(hash, 16);
        assertNotNull("Hash can be parsed as a hex number: " + hash, aValue);
    }
}
