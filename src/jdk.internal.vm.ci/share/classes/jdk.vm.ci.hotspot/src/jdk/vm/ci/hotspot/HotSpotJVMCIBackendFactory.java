/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.runtime.JVMCIBackend;

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
     * @param outFeatures the set to which the converted values are added
     * @param constants VM constants. Each entry whose key starts with {@code "VM_Version::CPU_"}
     *            specifies a CPU feature and its value is a mask for the bit in
     *            {@code supportedFeatures} representing the feature
     * @param features bits specifying CPU features
     * @param nameToFeature maps a feature name to a feature enum constant or throws
     *            {@link IllegalArgumentException}
     * @throws IllegalArgumentException if any VM CPU feature constant cannot be converted to an
     *             enum value
     */
    static <CPUFeatureType extends Enum<CPUFeatureType>> void convertFeatures(
                    EnumSet<CPUFeatureType> outFeatures,
                    Map<String, Long> constants,
                    long features,
                    Function<String, CPUFeatureType> nameToFeature) {

        List<String> missing = new ArrayList<>();
        for (Entry<String, Long> e : constants.entrySet()) {
            long bitMask = e.getValue();
            String key = e.getKey();
            if (key.startsWith("VM_Version::CPU_")) {
                String name = key.substring("VM_Version::CPU_".length());
                try {
                    CPUFeatureType feature = nameToFeature.apply(name);
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
    }
}
