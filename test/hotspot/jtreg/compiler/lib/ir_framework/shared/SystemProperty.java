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

package compiler.lib.ir_framework.shared;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SystemProperty {
    public record Mode(boolean caseSensitive, String def) {
        public static final Mode CASE_INSENSITIVE_EMPTY_DEFAULT = make().withCaseSensitive(false).withDefault("");

        public static Mode make() {
            return new Mode(false, null);
        }

        public Mode withCaseSensitive(boolean c) {
            return new Mode(c, this.def);
        }

        public Mode withDefault(String def) {
            return new Mode(this.caseSensitive, def);
        }
    }

    static public String getCaseInsensitive(Mode mode, String... keys) {
        Function<String, String> normalize =
                mode.caseSensitive()
                        ? (x -> x)
                        : String::toLowerCase;
        List<String> normalizedKeys = Arrays.stream(keys).map(normalize).toList();
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k instanceof String && v instanceof String) {
                String ks = normalize.apply((String)k);
                if (normalizedKeys.contains(ks)) {
                    return (String)v;
                }
            }
        }
        return mode.def();
    }

    static public String getTestList() {
        return getCaseInsensitive(Mode.CASE_INSENSITIVE_EMPTY_DEFAULT, "test", "tests");
    }

    static public String getExcludeList() {
        return getCaseInsensitive(Mode.CASE_INSENSITIVE_EMPTY_DEFAULT, "exclude", "excludes");
    }
}
