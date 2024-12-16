/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.color.ICC_Profile;
import java.util.concurrent.locks.StampedLock;

import sun.java2d.cmm.ColorTransform;
import sun.java2d.cmm.PCMM;
import sun.java2d.cmm.Profile;

final class LCMS implements PCMM {

    /**
     * Prevent changing profiles data during transform creation.
     */
    private static final StampedLock lock = new StampedLock();

    /* methods invoked from ICC_Profile */
    @Override
    public Profile loadProfile(byte[] data) {
        final Object disposerRef = new Object();

        final long ptr = loadProfileNative(data, disposerRef);

        if (ptr != 0L) {
            return new LCMSProfile(ptr, disposerRef);
        }
        return null;
    }

    static LCMSProfile getLcmsProfile(Profile p) {
        if (p instanceof LCMSProfile) {
            return (LCMSProfile)p;
        }
        throw new CMMException("Invalid profile: " + p);
    }

    /**
     * Writes supplied data as a tag into the profile.
     * Destroys old profile, if new one was successfully
     * created.
     *
     * Returns valid pointer to new profile.
     *
     * Throws CMMException if operation fails, preserve old profile from
     * destruction.
     */
    static native void setTagDataNative(long ptr, int tagSignature, byte[] data);
    static native byte[] getProfileDataNative(long ptr);
    static native byte[] getTagNative(long profileID, int signature);
    private static native long loadProfileNative(byte[] data, Object ref);

    @Override
    public byte[] getProfileData(Profile p) {
        return getLcmsProfile(p).getProfileData();
    }

    @Override
    public byte[] getTagData(Profile p, int tagSignature) {
        return getLcmsProfile(p).getTag(tagSignature);
    }

    @Override
    public void setTagData(Profile p, int tagSignature, byte[] data) {
        long stamp = lock.writeLock();
        try {
            getLcmsProfile(p).setTag(tagSignature, data);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /* Helper method used from LCMSColorTransform */
    static long createTransform(LCMSProfile[] profiles, int renderingIntent,
                                int inFormatter, int outFormatter,
                                Object disposerRef)
    {
        long[] ptrs = new long[profiles.length];
        long stamp = lock.readLock();
        try {
            for (int i = 0; i < profiles.length; i++) {
                if (profiles[i] == null) {
                    throw new CMMException("Unknown profile ID");
                }
                ptrs[i] = profiles[i].getLcmsPtr();
            }

            return createNativeTransform(ptrs, renderingIntent, inFormatter,
                                         outFormatter, disposerRef);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    private static native long createNativeTransform(long[] profileIDs,
                                                     int renderingIntent,
                                                     int inFormatter,
                                                     int outFormatter,
                                                     Object disposerRef);

    /**
     * Constructs ColorTransform object corresponding to the ICC_profiles.
     */
    public ColorTransform createTransform(int renderingIntent,
                                          ICC_Profile... profiles)
    {
        return new LCMSTransform(renderingIntent, profiles);
    }

    /* methods invoked from LCMSTransform */
    static native void colorConvert(long trans, int width, int height,
                                    int srcOffset, int srcNextRowOffset,
                                    int dstOffset, int dstNextRowOffset,
                                    Object srcData, Object dstData,
                                    int srcType, int dstType);

    private LCMS() {}

    private static LCMS theLcms = null;

    @SuppressWarnings("restricted")
    static synchronized PCMM getModule() {
        if (theLcms != null) {
            return theLcms;
        }

        /* We need to load awt here because of usage trace and
         * disposer frameworks
         */
        System.loadLibrary("awt");
        System.loadLibrary("lcms");

        theLcms = new LCMS();

        return theLcms;
    }
}
