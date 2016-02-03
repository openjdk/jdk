/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.code.site;

import java.util.Objects;

/**
 * Represents a mark in the machine code that can be used by the runtime for its own purposes. A
 * mark can reference other marks.
 */
public final class Mark extends Site {

    public final Object id;

    public Mark(int pcOffset, Object id) {
        super(pcOffset);
        this.id = id;
    }

    @Override
    public String toString() {
        if (id == null) {
            return String.format("%d[<mar>]", pcOffset);
        } else if (id instanceof Integer) {
            return String.format("%d[<mark with id %s>]", pcOffset, Integer.toHexString((Integer) id));
        } else {
            return String.format("%d[<mark with id %s>]", pcOffset, id.toString());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Mark) {
            Mark that = (Mark) obj;
            if (this.pcOffset == that.pcOffset && Objects.equals(this.id, that.id)) {
                return true;
            }
        }
        return false;
    }
}
