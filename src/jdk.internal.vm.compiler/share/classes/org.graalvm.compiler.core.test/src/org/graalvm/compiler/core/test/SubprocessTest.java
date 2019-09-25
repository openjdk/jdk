/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.java;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.IOException;
import java.util.List;

import org.graalvm.compiler.test.SubprocessUtil;
import org.junit.Assume;
import org.junit.Before;

public abstract class SubprocessTest extends GraalCompilerTest {

    @Before
    public void checkJavaAgent() {
        Assume.assumeFalse("Java Agent found -> skipping", SubprocessUtil.isJavaAgentAttached());
    }

    public void launchSubprocess(Runnable runnable) throws InterruptedException, IOException {
        String recursionPropName = getClass().getSimpleName() + ".Subprocess";
        if (Boolean.getBoolean(recursionPropName)) {
            runnable.run();
        } else {
            List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
            vmArgs.addAll(SubprocessUtil.getPackageOpeningOptions());
            vmArgs.add("-D" + recursionPropName + "=true");
            configSubprocess(vmArgs);
            SubprocessUtil.Subprocess proc = java(vmArgs, "com.oracle.mxtool.junit.MxJUnitWrapper", getClass().getName());
            assertTrue(proc.exitCode == 0, proc.toString() + " failed with exit code " + proc.exitCode);
        }
    }

    @SuppressWarnings("unused")
    public void configSubprocess(List<String> vmArgs) {
    }

}
