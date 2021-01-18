/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

/**
 * Container for volume/file id to uniquely identify file.
 */

class WindowsFileKey {
    private final int volSerialNumber;
    private final long fileId;

    WindowsFileKey(int volSerialNumber, long fileId) {
        this.volSerialNumber = volSerialNumber;
        this.fileId = fileId;
    }

    @Override
    public int hashCode() {
        return (int)(volSerialNumber ^ (volSerialNumber >>> 16)) +
               (int)(fileId ^ (fileId >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof WindowsFileKey))
            return false;
        WindowsFileKey other = (WindowsFileKey)obj;
        return (this.volSerialNumber == other.volSerialNumber) && (this.fileId == other.fileId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(volId=")
          .append(Integer.toHexString(volSerialNumber))
          .append(",fileId=")
          .append(Long.toHexString(fileId))
          .append(')');
        return sb.toString();
    }
}
