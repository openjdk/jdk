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


package org.graalvm.compiler.lir.util;

import java.util.function.BiConsumer;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;

public class RegisterMap<T> {
    private final Object[] values;
    private final Architecture architecture;

    public RegisterMap(Architecture arch) {
        assert checkArchitecture(arch);
        this.values = new Object[arch.getRegisters().size()];
        this.architecture = arch;
    }

    @SuppressWarnings("unchecked")
    public T get(Register reg) {
        return (T) values[index(reg)];
    }

    public void remove(Register reg) {
        values[index(reg)] = null;
    }

    public void put(Register reg, T value) {
        values[index(reg)] = value;
    }

    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super Register, ? super T> consumer) {
        for (int i = 0; i < values.length; ++i) {
            T value = (T) values[i];
            if (value != null) {
                consumer.accept(architecture.getRegisters().get(i), value);
            }
        }
    }

    private static int index(Register reg) {
        return reg.number;
    }

    private static boolean checkArchitecture(Architecture arch) {
        RegisterArray registers = arch.getRegisters();
        for (int i = 0; i < registers.size(); ++i) {
            assert registers.get(i).number == i : registers.get(i) + ": " + registers.get(i).number + "!=" + i;
        }
        return true;
    }
}
