/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.amd64;

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

/**
 * Used to access AMD64 specific native configuration details.
 */
class AMD64HotSpotVMConfig extends HotSpotVMConfigAccess {

    AMD64HotSpotVMConfig(HotSpotVMConfigStore config) {
        super(config);
    }

    final boolean useCompressedOops = getFlag("UseCompressedOops", Boolean.class);
    final long vmVersionFeatures = getFieldAddress("VM_Version::_features", "VM_Version::VM_Features");
    final long vmFeaturesFeaturesOffset = getFieldOffset("VM_Version::VM_Features::_features_bitmap[0]", Long.class, "uint64_t");
    final long vmFeaturesFeaturesSize = getFieldValue("VM_Version::VM_Features::_features_bitmap_size", Long.class, "int");
}
