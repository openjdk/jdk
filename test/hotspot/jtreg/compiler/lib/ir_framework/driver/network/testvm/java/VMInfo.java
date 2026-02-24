/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network.testvm.java;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class stores the key value mapping from the VMInfo.
 */
public class VMInfo implements JavaMessage {
    private static final Pattern CPU_SKYLAKE_PATTERN =
            Pattern.compile("family 6 model 85 stepping (\\d+) ");

    /**
     * Stores the key-value mapping.
     */
    private final Map<String, String> keyValueMap;

    public VMInfo(Map<String, String> keyValueMap) {
        this.keyValueMap = keyValueMap;
    }

    public boolean hasCPUFeature(String feature) {
        String features = getStringValue("cpuFeatures") + ",";
        return features.contains(" " + feature + ",");
    }

    private String getStringValue(String key) {
        TestFramework.check(keyValueMap.containsKey(key), "VMInfo does not contain \"" + key + "\"");
        return keyValueMap.get(key);
    }

    public long getLongValue(String key) {
        try {
            return Long.parseLong(getStringValue(key));
        } catch (NumberFormatException e) {
            throw new TestFrameworkException("VMInfo value for \"" + key + "\" is not a long, got \"" + getStringValue(key) + "\"");
        }
    }

    /**
     * Some platforms do not behave as expected, and one cannot trust that the vectors
     * make use of the full MaxVectorSize. For Cascade Lake, we only use 32 bytes for
     * SuperWord by default even though MaxVectorSize is 64. But the VectorAPI still
     * uses 64 bytes. Thus MaxVectorSize is not a reliable indicator for the expected
     * maximal vector size on that platform.
     */
    public boolean canTrustVectorSize() {
        return !isDefaultCascadeLake();
    }

    private boolean isDefaultCascadeLake() {
        // See VM_Version::is_default_intel_cascade_lake
        return isCascadeLake() &&
               getLongValue("MaxVectorSizeIsDefault") == 1 &&
               getLongValue("UseAVXIsDefault") == 1 &&
               getLongValue("UseAVX") > 2;
    }

    private boolean isCascadeLake() {
        Matcher matcher = CPU_SKYLAKE_PATTERN.matcher(getStringValue("cpuFeatures"));
        if (!matcher.find()) {
            return false; // skylake pattern not found
        }
        String stepping = matcher.group(1).trim();
        return Long.parseLong(stepping) >= 5; // this makes it Cascade Lake
    }

    @Override
    public void print() {
        if (!TestFramework.VERBOSE) {
            return;
        }

        System.out.println();
        System.out.println("VM Info");
        System.out.println("--------");
        for (var entry : keyValueMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.out.println("- Key: " + key + ", Value: " + value);
        }
    }
}
