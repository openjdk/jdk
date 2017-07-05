/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.cmm.lcms;

import java.awt.color.CMMException;
import java.util.Arrays;
import java.util.HashMap;
import sun.java2d.cmm.Profile;

final class LCMSProfile extends Profile {
    private final TagCache tagCache;

    private final Object disposerReferent;

    LCMSProfile(long ptr, Object ref) {
        super(ptr);

        disposerReferent = ref;

        tagCache = new TagCache(this);
    }

    long getLcmsPtr() {
        return this.getNativePtr();
    }

    TagData getTag(int sig) {
        return tagCache.getTag(sig);
    }

    void clearTagCache() {
        tagCache.clear();
    }

    static class TagCache  {
        final LCMSProfile profile;
        private HashMap<Integer, TagData> tags;

        TagCache(LCMSProfile p) {
            profile = p;
            tags = new HashMap<>();
        }

        TagData getTag(int sig) {
            TagData t = tags.get(sig);
            if (t == null) {
                byte[] tagData = LCMS.getTagNative(profile.getNativePtr(), sig);
                if (tagData != null) {
                    t = new TagData(sig, tagData);
                    tags.put(sig, t);
                }
            }
            return t;
        }

        void clear() {
            tags.clear();
        }
    }

    static class TagData {
        private int signature;
        private byte[] data;

        TagData(int sig, byte[] data) {
            this.signature = sig;
            this.data = data;
        }

        int getSize() {
            return data.length;
        }

        byte[] getData() {
            return Arrays.copyOf(data, data.length);
        }

        void copyDataTo(byte[] dst) {
            System.arraycopy(data, 0, dst, 0, data.length);
        }

        int getSignature() {
            return signature;
        }
    }
}
