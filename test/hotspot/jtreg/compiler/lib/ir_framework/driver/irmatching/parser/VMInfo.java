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

import java.util.Map;

/**
 * This class stores the key value mapping from the VMInfo.
 *
 * @see IREncodingParser
 */
public class VMInfo {
    /**
     * Stores the key-value mapping.
     */
    private final Map<String, String> keyValueMap;

    public VMInfo(Map<String, String> map) {
        this.keyValueMap = map;
    }

    public String getLong(String key, String otherwise) {
        if (isKey(key)) {
            return keyValueMap.get(key);
        }
        return otherwise;
    }

    public long getLong(String key, long otherwise) {
        if (isKey(key)) {
            try {
                return Long.parseLong(keyValueMap.get(key));
            } catch (NumberFormatException e) {}
        }
        return otherwise;
    }

    public boolean isKey(String key) {
        return keyValueMap.containsKey(key);
    }
}
