/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.meta.HotSpotClassInitializationPlugin;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotLazyInitializationTest extends GraalCompilerTest {

    HotSpotClassInitializationPlugin classInitPlugin = new HotSpotClassInitializationPlugin();

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        plugins.setClassInitializationPlugin(classInitPlugin);
        return plugins;
    }

    static boolean initializerRun = false;

    static class X {
        static {
            initializerRun = true;
        }

        static void foo() {
        }
    }

    public static void invokeStatic() {
        X.foo();
    }

    // If constant pool can do eager resolve without eager initialization, then fail if the compiler
    // causes the static initializer to run.
    private void test(String name) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Assume.assumeTrue("skipping for old JVMCI", classInitPlugin.supportsLazyInitialization(method.getConstantPool()));
        parseEager(method, AllowAssumptions.NO);
        Assert.assertFalse(initializerRun);
    }

    @Test
    public void test1() {
        test("invokeStatic");
    }

}
