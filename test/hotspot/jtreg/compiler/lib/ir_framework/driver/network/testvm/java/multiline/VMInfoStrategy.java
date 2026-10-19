/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network.testvm.java.multiline;

import compiler.lib.ir_framework.driver.network.testvm.java.VMInfo;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated strategy to parse the multi-line VM info message into a new {@link VMInfo} object.
 */
public class VMInfoStrategy implements MultiLineParsingStrategy<VMInfo> {
    private final Map<String, String> keyValueMap;

    public VMInfoStrategy() {
        this.keyValueMap = new HashMap<>();
    }

    @Override
    public void parseLine(String line) {
        String[] splitLine = line.split(":", 2);
        if (splitLine.length != 2) {
            throw new TestFrameworkException("Invalid VmInfo key:value encoding. Found: " + splitLine[0]);
        }
        String key = splitLine[0];
        String value = splitLine[1];
        keyValueMap.put(key, value);
    }

    @Override
    public VMInfo output() {
        return new VMInfo(keyValueMap);
    }
}
