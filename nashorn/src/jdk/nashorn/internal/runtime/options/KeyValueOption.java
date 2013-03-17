/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime.options;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Key Value option such as logger. It comes on the format
 * such as:
 *
 * {@code --log=module1:level1,module2:level2... }
 */
public class KeyValueOption extends Option<String> {
    /**
     * Map of keys given
     */
    protected Map<String, String> map;

    KeyValueOption(final String value) {
        super(value);
        initialize();
    }

    Map<String, String> getValues() {
        return Collections.unmodifiableMap(map);
    }

    /**
     * Check if the key value option has a value or if it has not
     * been initialized
     * @param key the key
     * @return value, or null if not initialized
     */
    public boolean hasValue(final String key) {
        return map != null && map.get(key) != null;
    }

    String getValue(final String key) {
        if (map == null) {
            return null;
        }
        final String val = map.get(key);
        return "".equals(val) ? null : val;
    }

    private void initialize() {
        if (getValue() == null) {
            return;
        }

        map = new LinkedHashMap<>();

        final StringTokenizer st = new StringTokenizer(getValue(), ",");
        while (st.hasMoreElements()) {
            final String   token    = st.nextToken();
            final String[] keyValue = token.split(":");

            if (keyValue.length == 1) {
                map.put(keyValue[0], "");
            } else if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            } else {
                throw new IllegalArgumentException(token);
            }
        }
    }
}
