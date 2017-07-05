/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.inittimer.SuppressFBWarnings;

public class HotSpotMetaData {
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private byte[] pcDescBytes;
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private byte[] scopesDescBytes;
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private byte[] relocBytes;
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private byte[] exceptionBytes;
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private byte[] oopMaps;
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private String[] metadata;

    public byte[] pcDescBytes() {
        return pcDescBytes != null ? pcDescBytes : new byte[0];
    }

    public byte[] scopesDescBytes() {
        return scopesDescBytes != null ? scopesDescBytes : new byte[0];
    }

    public byte[] relocBytes() {
        return relocBytes != null ? relocBytes : new byte[0];
    }

    public byte[] exceptionBytes() {
        return exceptionBytes != null ? exceptionBytes : new byte[0];
    }

    public byte[] oopMaps() {
        return oopMaps != null ? oopMaps : new byte[0];
    }

    public String[] metadataEntries() {
        return metadata != null ? metadata : new String[0];
    }
}
