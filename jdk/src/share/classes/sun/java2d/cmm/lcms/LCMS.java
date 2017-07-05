/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.color.CMMException;
import sun.java2d.cmm.ColorTransform;
import sun.java2d.cmm.PCMM;
import sun.java2d.cmm.lcms.LCMS;
import sun.java2d.cmm.lcms.LCMSTransform;

public class LCMS implements PCMM {

    /* methods invoked from ICC_Profile */
    public native long loadProfile(byte[] data);

    public native void freeProfile(long profileID);

    public native synchronized int getProfileSize(long profileID);

    public native synchronized void getProfileData(long profileID, byte[] data);

    public native synchronized int getTagSize(long profileID, int tagSignature);
    public native synchronized void getTagData(long profileID, int tagSignature,
                                               byte[] data);
    public native synchronized void setTagData(long profileID, int tagSignature,
                                               byte[] data);

    public static native long getProfileID(ICC_Profile profile);

    public static native long createNativeTransform(
        long[] profileIDs, int renderType, Object disposerRef);

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

    /* the class initializer which loads the CMM */
    static {
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
            }
        );

        initLCMS(LCMSTransform.class, LCMSImageLayout.class, ICC_Profile.class);
    }
}
