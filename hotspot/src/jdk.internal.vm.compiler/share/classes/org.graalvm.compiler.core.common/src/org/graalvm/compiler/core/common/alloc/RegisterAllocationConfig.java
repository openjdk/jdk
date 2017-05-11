/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.alloc;

import static org.graalvm.compiler.core.common.GraalOptions.RegisterPressure;

import java.util.HashMap;
import java.util.Map;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.PlatformKind;

import org.graalvm.compiler.core.common.GraalOptions;

/**
 * Configuration for register allocation. This is different to {@link RegisterConfig} as it only
 * returns registers specified by {@link GraalOptions#RegisterPressure}.
 */
public class RegisterAllocationConfig {

    public static final class AllocatableRegisters {
        public final Register[] allocatableRegisters;
        public final int minRegisterNumber;
        public final int maxRegisterNumber;

        public AllocatableRegisters(RegisterArray allocatableRegisters, int minRegisterNumber, int maxRegisterNumber) {
            this.allocatableRegisters = allocatableRegisters.toArray();
            this.minRegisterNumber = minRegisterNumber;
            this.maxRegisterNumber = maxRegisterNumber;
            assert verify(allocatableRegisters, minRegisterNumber, maxRegisterNumber);
        }

        private static boolean verify(RegisterArray allocatableRegisters, int minRegisterNumber, int maxRegisterNumber) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (Register reg : allocatableRegisters) {
                int number = reg.number;
                if (number < min) {
                    min = number;
                }
                if (number > max) {
                    max = number;
                }
            }
            assert minRegisterNumber == min;
            assert maxRegisterNumber == max;
            return true;
        }
    }

    public static final String ALL_REGISTERS = "<all>";

    private static Register findRegister(String name, RegisterArray all) {
        for (Register reg : all) {
            if (reg.name.equals(name)) {
                return reg;
            }
        }
        throw new IllegalArgumentException("register " + name + " is not allocatable");
    }

    protected RegisterArray initAllocatable(RegisterArray registers) {
        if (RegisterPressure.getValue() != null && !RegisterPressure.getValue().equals(ALL_REGISTERS)) {
            String[] names = RegisterPressure.getValue().split(",");
            Register[] regs = new Register[names.length];
            for (int i = 0; i < names.length; i++) {
                regs[i] = findRegister(names[i], registers);
            }
            return new RegisterArray(regs);
        }

        return registers;
    }

    protected final RegisterConfig registerConfig;
    private final Map<PlatformKind.Key, AllocatableRegisters> categorized = new HashMap<>();
    private RegisterArray cachedRegisters;

    public RegisterAllocationConfig(RegisterConfig registerConfig) {
        assert registerConfig != null;
        this.registerConfig = registerConfig;
    }

    /**
     * Gets the set of registers that can be used by the register allocator for a value of a
     * particular kind.
     */
    public AllocatableRegisters getAllocatableRegisters(PlatformKind kind) {
        PlatformKind.Key key = kind.getKey();
        if (categorized.containsKey(key)) {
            AllocatableRegisters val = categorized.get(key);
            return val;
        }
        AllocatableRegisters ret = createAllocatableRegisters(registerConfig.filterAllocatableRegisters(kind, getAllocatableRegisters()));
        categorized.put(key, ret);
        return ret;
    }

    protected AllocatableRegisters createAllocatableRegisters(RegisterArray registers) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Register reg : registers) {
            int number = reg.number;
            if (number < min) {
                min = number;
            }
            if (number > max) {
                max = number;
            }
        }
        assert min < max;
        return new AllocatableRegisters(registers, min, max);

    }

    /**
     * Gets the set of registers that can be used by the register allocator.
     */
    public RegisterArray getAllocatableRegisters() {
        if (cachedRegisters == null) {
            cachedRegisters = initAllocatable(registerConfig.getAllocatableRegisters());
        }
        assert cachedRegisters != null;
        return cachedRegisters;
    }

    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }

}
