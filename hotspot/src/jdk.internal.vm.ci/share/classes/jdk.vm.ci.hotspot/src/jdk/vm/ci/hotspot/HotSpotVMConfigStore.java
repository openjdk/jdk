/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.common.InitTimer;

/**
 * Access to VM configuration data.
 */
public final class HotSpotVMConfigStore {

    /**
     * Gets the C++ symbols whose addresses are exposed by this object.
     *
     * @return an unmodifiable map from the symbol names to their addresses
     */
    public Map<String, Long> getAddresses() {
        return Collections.unmodifiableMap(vmAddresses);
    }

    /**
     * Gets the C++ constants exposed by this object.
     *
     * @return an unmodifiable map from the names of C++ constants to their values
     */
    public Map<String, Long> getConstants() {
        return Collections.unmodifiableMap(vmConstants);
    }

    /**
     * Gets the VM flags exposed by this object.
     *
     * @return an unmodifiable map from VM flag names to {@link VMFlag} objects
     */
    public Map<String, VMFlag> getFlags() {
        return Collections.unmodifiableMap(vmFlags);
    }

    /**
     * Gets the C++ fields exposed by this object.
     *
     * @return an unmodifiable map from VM field names to {@link VMField} objects
     */
    public Map<String, VMField> getFields() {
        return Collections.unmodifiableMap(vmFields);
    }

    /**
     * Gets the VM intrinsic descriptions exposed by this object.
     */
    public List<VMIntrinsicMethod> getIntrinsics() {
        return Collections.unmodifiableList(vmIntrinsics);
    }

    final HashMap<String, VMField> vmFields;
    final HashMap<String, Long> vmConstants;
    final HashMap<String, Long> vmAddresses;
    final HashMap<String, VMFlag> vmFlags;
    final List<VMIntrinsicMethod> vmIntrinsics;
    final CompilerToVM compilerToVm;

    /**
     * Reads the database of VM info. The return value encodes the info in a nested object array
     * that is described by the pseudo Java object {@code info} below:
     *
     * <pre>
     *     info = [
     *         VMField[] vmFields,
     *         [String name, Long value, ...] vmConstants,
     *         [String name, Long value, ...] vmAddresses,
     *         VMFlag[] vmFlags
     *         VMIntrinsicMethod[] vmIntrinsics
     *     ]
     * </pre>
     */
    @SuppressWarnings("try")
    HotSpotVMConfigStore(CompilerToVM compilerToVm) {
        this.compilerToVm = compilerToVm;
        Object[] data;
        try (InitTimer t = timer("CompilerToVm readConfiguration")) {
            data = compilerToVm.readConfiguration();
        }
        assert data.length == 5 : data.length;

        // @formatter:off
        VMField[] vmFieldsInfo    = (VMField[]) data[0];
        Object[] vmConstantsInfo  = (Object[])  data[1];
        Object[] vmAddressesInfo  = (Object[])  data[2];
        VMFlag[] vmFlagsInfo      = (VMFlag[])  data[3];

        vmFields     = new HashMap<>(vmFieldsInfo.length);
        vmConstants  = new HashMap<>(vmConstantsInfo.length);
        vmAddresses  = new HashMap<>(vmAddressesInfo.length);
        vmFlags      = new HashMap<>(vmFlagsInfo.length);
        vmIntrinsics = Arrays.asList((VMIntrinsicMethod[]) data[4]);
        // @formatter:on

        try (InitTimer t = timer("HotSpotVMConfigStore<init> fill maps")) {
            for (VMField vmField : vmFieldsInfo) {
                vmFields.put(vmField.name, vmField);
            }

            for (int i = 0; i < vmConstantsInfo.length / 2; i++) {
                String name = (String) vmConstantsInfo[i * 2];
                Long value = (Long) vmConstantsInfo[i * 2 + 1];
                vmConstants.put(name, value);
            }

            for (int i = 0; i < vmAddressesInfo.length / 2; i++) {
                String name = (String) vmAddressesInfo[i * 2];
                Long value = (Long) vmAddressesInfo[i * 2 + 1];
                vmAddresses.put(name, value);
            }

            for (VMFlag vmFlag : vmFlagsInfo) {
                vmFlags.put(vmFlag.name, vmFlag);
            }
        }
    }
}
