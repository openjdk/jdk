/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network;

import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.driver.network.testvm.java.JavaMessages;
import compiler.lib.ir_framework.shared.TestFrameworkSocket;

/**
 * This class collects all the parsed data received over the {@link TestFrameworkSocket}. This data is required later
 * in the {@link IRMatcher}.
 */
public class TestVMData {
    private final JavaMessages javaMessages;
    private final boolean allowNotCompilable;
    private final String hotspotPidFileName;

    public TestVMData(JavaMessages javaMessages, String hotspotPidFileName, boolean allowNotCompilable) {
        this.javaMessages = javaMessages;
        this.hotspotPidFileName = hotspotPidFileName;
        this.allowNotCompilable = allowNotCompilable;
    }

    public String applicableIRRules() {
        return javaMessages.applicableIRRules();
    }

    public String vmInfo() {
        return javaMessages.vmInfo();
    }

    public String hotspotPidFileName() {
        return hotspotPidFileName;
    }

    public boolean allowNotCompilable() {
        return allowNotCompilable;
    }

    public void printJavaMessages() {
        javaMessages.print();
    }
}
