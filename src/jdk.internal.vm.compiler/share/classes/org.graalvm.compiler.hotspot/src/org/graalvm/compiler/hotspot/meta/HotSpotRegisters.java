/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import jdk.vm.ci.code.Register;

public class HotSpotRegisters implements HotSpotRegistersProvider {

    private final Register threadRegister;
    private final Register heapBaseRegister;
    private final Register stackPointerRegister;

    public HotSpotRegisters(Register threadRegister, Register heapBaseRegister, Register stackPointerRegister) {
        this.threadRegister = threadRegister;
        this.heapBaseRegister = heapBaseRegister;
        this.stackPointerRegister = stackPointerRegister;
    }

    @Override
    public Register getThreadRegister() {
        assert !threadRegister.equals(Register.None) : "thread register is not defined";
        return threadRegister;
    }

    @Override
    public Register getHeapBaseRegister() {
        assert !heapBaseRegister.equals(Register.None) : "heap base register is not defined";
        return heapBaseRegister;
    }

    @Override
    public Register getStackPointerRegister() {
        assert !stackPointerRegister.equals(Register.None) : "stack pointer register is not defined";
        return stackPointerRegister;
    }
}
