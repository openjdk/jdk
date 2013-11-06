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

import java.awt.color.CMMException;
import java.awt.color.ICC_Profile;
import sun.java2d.cmm.ColorTransform;
import sun.java2d.cmm.PCMM;
import sun.java2d.cmm.Profile;
import sun.java2d.cmm.lcms.LCMSProfile.TagData;

public class LCMS implements PCMM {

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

    private native long loadProfileNative(byte[] data, Object ref);

    private LCMSProfile getLcmsProfile(Profile p) {
        if (p instanceof LCMSProfile) {
            return (LCMSProfile)p;
        }
        throw new CMMException("Invalid profile: " + p);
    }


    @Override
    public void freeProfile(Profile p) {
        // we use disposer, so this method does nothing
    }

    @Override
    public int getProfileSize(final Profile p) {
        synchronized (p) {
            return getProfileSizeNative(getLcmsProfile(p).getLcmsPtr());
        }
    }

    private native int getProfileSizeNative(long ptr);

    @Override
    public void getProfileData(final Profile p, byte[] data) {
        synchronized (p) {
            getProfileDataNative(getLcmsProfile(p).getLcmsPtr(), data);
        }
    }

    private native void getProfileDataNative(long ptr, byte[] data);

    @Override
    public int getTagSize(Profile p, int tagSignature) {
        final LCMSProfile profile = getLcmsProfile(p);

        synchronized (profile) {
            TagData t = profile.getTag(tagSignature);
            return t == null ? 0 : t.getSize();
        }
    }

    static native byte[] getTagNative(long profileID, int signature);

    @Override
    public void getTagData(Profile p, int tagSignature, byte[] data)
    {
        final LCMSProfile profile = getLcmsProfile(p);

        synchronized (profile) {
            TagData t = profile.getTag(tagSignature);
            if (t != null) {
                t.copyDataTo(data);
            }
        }
    }

    @Override
    public synchronized void setTagData(Profile p, int tagSignature, byte[] data) {
        final LCMSProfile profile = getLcmsProfile(p);

        synchronized (profile) {
            profile.clearTagCache();

            // Now we are going to update the profile with new tag data
            // In some cases, we may change the pointer to the native
            // profile.
            //
            // If we fail to write tag data for any reason, the old pointer
            // should be used.
            setTagDataNative(profile.getLcmsPtr(),
                    tagSignature, data);
        }
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
    private native void setTagDataNative(long ptr, int tagSignature,
                                               byte[] data);

    public synchronized static native LCMSProfile getProfileID(ICC_Profile profile);

    /* Helper method used from LCMSColorTransfrom */
    static long createTransform(
        LCMSProfile[] profiles, int renderType,
        int inFormatter, boolean isInIntPacked,
        int outFormatter, boolean isOutIntPacked,
        Object disposerRef)
    {
        long[] ptrs = new long[profiles.length];

        for (int i = 0; i < profiles.length; i++) {
            if (profiles[i] == null) throw new CMMException("Unknown profile ID");

            ptrs[i] = profiles[i].getLcmsPtr();
        }

        return createNativeTransform(ptrs, renderType, inFormatter,
                isInIntPacked, outFormatter, isOutIntPacked, disposerRef);
    }

    private static native long createNativeTransform(
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
}
