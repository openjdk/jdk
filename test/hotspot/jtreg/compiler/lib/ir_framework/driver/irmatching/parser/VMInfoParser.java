/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.test.VMInfoPrinter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse the VMInfo emitted by the test VM and creating {@link VMInfo} objects for each entry.
 *
 * @see VMInfo
 */
public class VMInfoParser {

    private static final Pattern VM_INFO_PATTERN =
            Pattern.compile("(?<=" + VMInfoPrinter.START_VM_INFO + "\r?\n).*\\R([\\s\\S]*)(?=" + VMInfoPrinter.END_VM_INFO + ")");

    /**
     * Extract VMInfo from the irEncoding.
     */
    public static VMInfo parseVMInfo(String irEncoding) {
        Map<String, String> map = new HashMap<>();
        String[] lines = getVMInfoLines(irEncoding);
        for (String s : lines) {
            String line = s.trim();
            String[] splitLine = line.split(":", 2);
            if (splitLine.length != 2) {
                throw new TestFrameworkException("Invalid VMInfo key:value encoding. Found: " + splitLine[0]);
            }
            String key = splitLine[0];
            String value = splitLine[1];
            map.put(key, value);
        }
        return new VMInfo(map);
    }

    /**
     * Extract the VMInfo from the irEncoding string, strip away the header and return the individual key-value lines.
     */
    private static String[] getVMInfoLines(String irEncoding) {
        Matcher matcher = VM_INFO_PATTERN.matcher(irEncoding);
        TestFramework.check(matcher.find(), "Did not find VMInfo in:" + System.lineSeparator() + irEncoding);
        String lines = matcher.group(1).trim();
        if (lines.isEmpty()) {
            // Nothing to IR match.
            return new String[0];
        }
        return lines.split("\\R");
    }
}
