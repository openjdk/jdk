/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.runtime.JVMCIBackend;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

public interface HotSpotJVMCIBackendFactory {

    JVMCIBackend createJVMCIBackend(HotSpotJVMCIRuntime runtime, JVMCIBackend host);

    /**
     * Gets the CPU architecture of this backend.
     */
    String getArchitecture();

    /**
     * Converts a bit mask of CPU features to enum constants.
     *
     * @param <CPUFeatureType> CPU feature enum type
     * @param enumType the class of {@code CPUFeatureType}
     * @param constants VM constants. Each entry whose key starts with {@code "VM_Version::CPU_"}
     *            specifies a CPU feature and its value is a mask for a bit in {@code features}
     * @param features bits specifying CPU features
     * @param renaming maps from VM feature names to enum constant names where the two differ
     * @throws IllegalArgumentException if any VM CPU feature constant cannot be converted to an
     *             enum value
     * @return the set of converted values
     */
    static <CPUFeatureType extends Enum<CPUFeatureType>> EnumSet<CPUFeatureType> convertFeatures(
                    Class<CPUFeatureType> enumType,
                    Map<String, Long> constants,
                    long features,
                    Map<String, String> renaming) {
        EnumSet<CPUFeatureType> outFeatures = EnumSet.noneOf(enumType);
        List<String> missing = new ArrayList<>();
        for (Entry<String, Long> e : constants.entrySet()) {
            long bitMask = e.getValue();
            String key = e.getKey();
            if (key.startsWith("VM_Version::CPU_")) {
                String name = key.substring("VM_Version::CPU_".length());
                try {
                    CPUFeatureType feature = Enum.valueOf(enumType, renaming.getOrDefault(name, name));
                    if ((features & bitMask) != 0) {
                        outFeatures.add(feature);
                    }
                } catch (IllegalArgumentException iae) {
                    missing.add(name);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new JVMCIError("Missing CPU feature constants: %s", missing);
        }
        return outFeatures;
    }

    /**
     * Converts a dynamically sized CPU features vector into enum constants.
     *
     * @param <CPUFeatureType> CPU feature enum type
     * @param enumType the class of {@code CPUFeatureType}
     * @param constants VM constants. Each entry whose key starts with {@code "VM_Version::CPU_"}
     *            specifies a CPU feature and its value is a mask for a bit in {@code features}
     * @param dynamic_features_vector_pointer pointer to dynamic feature bit vector of CPU features
     * @param dynamic_features_vector_offset offset of dynamic_feature_vector field in {@code VM_Features}
     * @param dynamic_features_vector_size_offset offset of dynamic_feature_vector_size field in {@code VM_Features}
     * @param dynamic_features_element_shift_count_offset offset of dynamic_features_element_shift_count field in {@code VM_Features}
     *             ,it holds the base2 logarithmic value of dynamic feature bit vector lanesize in bits.
     * @param renaming maps from VM feature names to enum constant names where the two differ
     * @throws IllegalArgumentException if any VM CPU feature constant cannot be converted to an
     *             enum value
     * @return the set of converted values
     */
    static <CPUFeatureType extends Enum<CPUFeatureType>> EnumSet<CPUFeatureType> convertDynamicFeaturesVector(
                    Class<CPUFeatureType> enumType,
                    Map<String, Long> constants,
                    long dynamic_features_pointer,
                    long dynamic_features_vector_offset,
                    long dynamic_features_vector_size_offset,
                    long dynamic_features_element_shift_count_offset,
                    Map<String, String> renaming) {
        EnumSet<CPUFeatureType> outFeatures = EnumSet.noneOf(enumType);
        List<String> missing = new ArrayList<>();

        long dynamic_features_vector_pointer = UNSAFE.getLong(dynamic_features_pointer + dynamic_features_vector_offset);
        long dynamic_features_vector_size = UNSAFE.getLong(dynamic_features_pointer + dynamic_features_vector_size_offset);
        long dynamic_features_element_shift_count = UNSAFE.getLong(dynamic_features_pointer + dynamic_features_element_shift_count_offset);

        for (Entry<String, Long> e : constants.entrySet()) {
            String key = e.getKey();
            long bitIndex = e.getValue();
            if (key.startsWith("VM_Version::CPU_")) {
                String name = key.substring("VM_Version::CPU_".length());
                try {
                    CPUFeatureType feature = Enum.valueOf(enumType, renaming.getOrDefault(name, name));
                    long dynamic_features_vector_index = bitIndex >>> dynamic_features_element_shift_count;
                    assert dynamic_features_vector_index < dynamic_features_vector_size;

                    long  dynamic_features_element_bitsize = (1L << dynamic_features_element_shift_count);
                    assert (dynamic_features_element_bitsize & (dynamic_features_element_bitsize - 1)) == 0;

                    long  dynamic_features_element_size = dynamic_features_element_bitsize / Byte.SIZE;
                    long features = UNSAFE.getLong(dynamic_features_vector_pointer +
                                                   dynamic_features_vector_index * dynamic_features_element_size);

                    long effective_bitMask = 1L << (bitIndex & (dynamic_features_element_bitsize - 1));
                    if ((features & effective_bitMask) != 0) {
                        outFeatures.add(feature);
                    }
                } catch (IllegalArgumentException iae) {
                    missing.add(name);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new JVMCIError("Missing CPU feature constants: %s", missing);
        }
        return outFeatures;
    }
}
