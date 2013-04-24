/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.color.ICC_Profile;
import java.util.Arrays;
import java.util.HashMap;
import sun.java2d.cmm.ColorTransform;
import sun.java2d.cmm.PCMM;

public class LCMS implements PCMM {

    /* methods invoked from ICC_Profile */
    @Override
    public long loadProfile(byte[] data) {
        long id = loadProfileNative(data);

        if (id != 0L) {
            if (profiles == null) {
                profiles = new HashMap<>();
            }
            profiles.put(id, new TagCache(id));
        }
        return id;
    }

    private native long loadProfileNative(byte[] data);

    @Override
    public void freeProfile(long profileID) {
        TagCache c = profiles.remove(profileID);
        if (c != null) {
            c.clear();
        }
        if (profiles.isEmpty()) {
            profiles = null;
        }
        freeProfileNative(profileID);
    }

    private native void freeProfileNative(long profileID);

    public native synchronized int getProfileSize(long profileID);

    public native synchronized void getProfileData(long profileID, byte[] data);

    @Override
    public synchronized int getTagSize(long profileID, int tagSignature) {
        TagCache cache = profiles.get(profileID);

        if (cache ==  null) {
            cache = new TagCache(profileID);
            profiles.put(profileID, cache);
        }

        TagData t = cache.getTag(tagSignature);
        return t == null ? 0 : t.getSize();
    }

    private static native byte[] getTagNative(long profileID, int signature);

    @Override
    public synchronized void getTagData(long profileID, int tagSignature,
                                               byte[] data)
    {
        TagCache cache = profiles.get(profileID);

        if (cache ==  null) {
            cache = new TagCache(profileID);
            profiles.put(profileID, cache);
        }

        TagData t = cache.getTag(tagSignature);
        if (t != null) {
            t.copyDataTo(data);
        }
    }

    @Override
    public synchronized void setTagData(long profileID, int tagSignature, byte[] data) {
        TagCache cache = profiles.get(profileID);

        if (cache != null) {
            cache.clear();
        }
        setTagDataNative(profileID, tagSignature, data);
    }

    private native synchronized void setTagDataNative(long profileID, int tagSignature,
                                               byte[] data);

    public static native long getProfileID(ICC_Profile profile);

    public static native long createNativeTransform(
        long[] profileIDs, int renderType,
        int inFormatter, boolean isInIntPacked,
        int outFormatter, boolean isOutIntPacked,
        Object disposerRef);

   /**
     * Constructs ColorTransform object corresponding to an ICC_profile
     */
    public ColorTransform createTransform(ICC_Profile profile,
                                                       int renderType,
                                                       int transformType)
    {
        return new LCMSTransform(profile, renderType, renderType);
    }

    /**
     * Constructs an ColorTransform object from a list of ColorTransform
     * objects
     */
    public synchronized ColorTransform createTransform(
        ColorTransform[] transforms)
    {
        return new LCMSTransform(transforms);
    }

    /* methods invoked from LCMSTransform */
    public static native void colorConvert(LCMSTransform trans,
                                           LCMSImageLayout src,
                                           LCMSImageLayout dest);
    public static native void freeTransform(long ID);

    public static native void initLCMS(Class Trans, Class IL, Class Pf);

    private LCMS() {};

    private static LCMS theLcms = null;

    static synchronized PCMM getModule() {
        if (theLcms != null) {
            return theLcms;
        }

        java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction() {
                    public Object run() {
                        /* We need to load awt here because of usage trace and
                         * disposer frameworks
                         */
                        System.loadLibrary("awt");
                        System.loadLibrary("lcms");
                        return null;
                    }
                });

        initLCMS(LCMSTransform.class, LCMSImageLayout.class, ICC_Profile.class);

        theLcms = new LCMS();

        return theLcms;
    }

    private static class TagData {
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

    private static class TagCache  {
        private long profileID;
        private HashMap<Integer, TagData> tags;

        TagCache(long id) {
            profileID = id;

            tags = new HashMap<>();
        }

        TagData getTag(int sig) {
            TagData t = tags.get(sig);
            if (t == null) {
                byte[] tagData = getTagNative(profileID, sig);
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

    private static HashMap<Long, TagCache> profiles;
}
