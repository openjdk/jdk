/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.util.ArrayList;
import java.util.List;

final class StatusRequestType {

    final int id;
    final String name;
    static List<StatusRequestType> knownTypes = new ArrayList<>(4);

    private StatusRequestType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    static StatusRequestType get(int id) {
        for (StatusRequestType ext : knownTypes) {
            if (ext.id == id) {
                return ext;
            }
        }
        return new StatusRequestType(id, "type_" + id);
    }

    private static StatusRequestType e(int id, String name) {
        StatusRequestType ext = new StatusRequestType(id, name);
        knownTypes.add(ext);
        return ext;
    }

    @Override
    public String toString() {
        return (name == null || name.isEmpty()) ?
                String.format("Unknown (0x%04X", id) : name;
    }

    // Status request types defined in RFC 6066 and 6961
    static final StatusRequestType OCSP = e(0x01, "ocsp");
    static final StatusRequestType OCSP_MULTI = e(0x02, "ocsp_multi");
}
