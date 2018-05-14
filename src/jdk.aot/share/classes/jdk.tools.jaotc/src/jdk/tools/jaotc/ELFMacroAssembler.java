/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

import jdk.tools.jaotc.StubInformation;
import jdk.tools.jaotc.amd64.AMD64ELFMacroAssembler;
import jdk.tools.jaotc.aarch64.AArch64ELFMacroAssembler;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;

public interface ELFMacroAssembler {

    public static ELFMacroAssembler getELFMacroAssembler(TargetDescription target) {
        Architecture architecture = target.arch;
        if (architecture instanceof AMD64) {
            return new AMD64ELFMacroAssembler(target);
        } else if (architecture instanceof AArch64) {
            return new AArch64ELFMacroAssembler(target);
        } else {
            throw new InternalError("Unsupported architecture " + architecture);
        }
    }

    public int currentEndOfInstruction();

    public byte[] getPLTJumpCode();

    public byte[] getPLTStaticEntryCode(StubInformation stub);

    public byte[] getPLTVirtualEntryCode(StubInformation stub);

}
