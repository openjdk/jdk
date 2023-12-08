/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.management.remote.rest.json;

import java.util.LinkedHashMap;

/**
 */
public class JSONObject extends LinkedHashMap<String, JSONElement> implements JSONElement {

    private static final long serialVersionUID = -9148596129640441014L;

    public JSONObject() {
        super();
    }

    public JSONObject(JSONObject jsonObject) {
        super(jsonObject);
    }

    public JSONElement put(String key, String value) {
        return super.put(key, new JSONPrimitive(value));
    }

    @Override
    public String toJsonString() {
        if (isEmpty()) {
            return null;
        }

        StringBuilder sbuild = new StringBuilder();
        sbuild.append("{");
        keySet().forEach((s) -> {
            sbuild.append("\"").append(s).append("\"").append(": ").
                    append((get(s) != null) ? get(s).toJsonString() : "null").append(",");
        });

        sbuild.deleteCharAt(sbuild.lastIndexOf(","));
        sbuild.append("}");
        return sbuild.toString();
    }
}
