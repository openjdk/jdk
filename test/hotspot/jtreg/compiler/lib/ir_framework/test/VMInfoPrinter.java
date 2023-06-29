/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.test;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.*;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Prints some test VM info to the socket. 
 */
public class VMInfoPrinter {
    public static final String START_VM_INFO = "##### IRMatchingVMInfo - used by TestFramework #####";
    public static final String END_VM_INFO = "----- END VMInfo -----";

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void emit() {
        StringBuilder vmInfo = new StringBuilder();
        vmInfo.append(START_VM_INFO).append(System.lineSeparator());
        vmInfo.append("<key>:<value>").append(System.lineSeparator());
        String cpuFeatures = WHITE_BOX.getCPUFeatures();
        vmInfo.append("cpuFeatures:" + cpuFeatures).append(System.lineSeparator());
        long maxVectorSize = WHITE_BOX.getIntxVMFlag("MaxVectorSize");
        vmInfo.append("MaxVectorSize:" + maxVectorSize).append(System.lineSeparator());
        long loopMaxUnroll = WHITE_BOX.getIntxVMFlag("LoopMaxUnroll");
        vmInfo.append("LoopMaxUnroll:" + loopMaxUnroll).append(System.lineSeparator());
        vmInfo.append(END_VM_INFO);
        TestFrameworkSocket.write(vmInfo.toString(), "VMInfo");
    }
}
