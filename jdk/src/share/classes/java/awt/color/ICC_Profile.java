/*
 * Portions Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**********************************************************************
 **********************************************************************
 **********************************************************************
 *** COPYRIGHT (c) Eastman Kodak Company, 1997                      ***
 *** As  an unpublished  work pursuant to Title 17 of the United    ***
 *** States Code.  All rights reserved.                             ***
 **********************************************************************
 **********************************************************************
 **********************************************************************/

package java.awt.color;

import sun.java2d.cmm.PCMM;
import sun.java2d.cmm.CMSManager;
import sun.java2d.cmm.ProfileDeferralMgr;
import sun.java2d.cmm.ProfileDeferralInfo;
import sun.java2d.cmm.ProfileActivator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;

import java.util.StringTokenizer;

import java.security.AccessController;
import java.security.PrivilegedAction;

import sun.misc.BootClassLoaderHook;

/**
 * A representation of color profile data for device independent and
 * device dependent color spaces based on the International Color
 * Consortium Specification ICC.1:2001-12, File Format for Color Profiles,
 * (see <A href="http://www.color.org"> http://www.color.org</A>).
 * <p>
 * An ICC_ColorSpace object can be constructed from an appropriate
 * ICC_Profile.
 * Typically, an ICC_ColorSpace would be associated with an ICC
 * Profile which is either an input, display, or output profile (see
 * the ICC specification).  There are also device link, abstract,
 * color space conversion, and named color profiles.  These are less
 * useful for tagging a color or image, but are useful for other
 * purposes (in particular device link profiles can provide improved
 * performance for converting from one device's color space to
 * another's).
 * <p>
 * ICC Profiles represent transformations from the color space of
 * the profile (e.g. a monitor) to a Profile Connection Space (PCS).
 * Profiles of interest for tagging images or colors have a PCS
 * which is one of the two specific device independent
 * spaces (one CIEXYZ space and one CIELab space) defined in the
 * ICC Profile Format Specification.  Most profiles of interest
 * either have invertible transformations or explicitly specify
 * transformations going both directions.
 * <p>
 * @see ICC_ColorSpace
 */


public class ICC_Profile implements Serializable {

    private static final long serialVersionUID = -3938515861990936766L;

    transient long ID;

    private transient ProfileDeferralInfo deferralInfo;
    private transient ProfileActivator profileActivator;

    // Registry of singleton profile objects for specific color spaces
    // defined in the ColorSpace class (e.g. CS_sRGB), see
    // getInstance(int cspace) factory method.
    private static ICC_Profile sRGBprofile;
    private static ICC_Profile XYZprofile;
    private static ICC_Profile PYCCprofile;
    private static ICC_Profile GRAYprofile;
    private static ICC_Profile LINEAR_RGBprofile;


    /**
     * Profile class is input.
     */
    public static final int CLASS_INPUT = 0;

    /**
     * Profile class is display.
     */
    public static final int CLASS_DISPLAY = 1;

    /**
     * Profile class is output.
     */
    public static final int CLASS_OUTPUT = 2;

    /**
     * Profile class is device link.
     */
    public static final int CLASS_DEVICELINK = 3;

    /**
     * Profile class is color space conversion.
     */
    public static final int CLASS_COLORSPACECONVERSION = 4;

    /**
     * Profile class is abstract.
     */
    public static final int CLASS_ABSTRACT = 5;

    /**
     * Profile class is named color.
     */
    public static final int CLASS_NAMEDCOLOR = 6;


    /**
     * ICC Profile Color Space Type Signature: 'XYZ '.
     */
    public static final int icSigXYZData        = 0x58595A20;    /* 'XYZ ' */

    /**
     * ICC Profile Color Space Type Signature: 'Lab '.
     */
    public static final int icSigLabData        = 0x4C616220;    /* 'Lab ' */

    /**
     * ICC Profile Color Space Type Signature: 'Luv '.
     */
    public static final int icSigLuvData        = 0x4C757620;    /* 'Luv ' */

    /**
     * ICC Profile Color Space Type Signature: 'YCbr'.
     */
    public static final int icSigYCbCrData        = 0x59436272;    /* 'YCbr' */

    /**
     * ICC Profile Color Space Type Signature: 'Yxy '.
     */
    public static final int icSigYxyData        = 0x59787920;    /* 'Yxy ' */

    /**
     * ICC Profile Color Space Type Signature: 'RGB '.
     */
    public static final int icSigRgbData        = 0x52474220;    /* 'RGB ' */

    /**
     * ICC Profile Color Space Type Signature: 'GRAY'.
     */
    public static final int icSigGrayData        = 0x47524159;    /* 'GRAY' */

    /**
     * ICC Profile Color Space Type Signature: 'HSV'.
     */
    public static final int icSigHsvData        = 0x48535620;    /* 'HSV ' */

    /**
     * ICC Profile Color Space Type Signature: 'HLS'.
     */
    public static final int icSigHlsData        = 0x484C5320;    /* 'HLS ' */

    /**
     * ICC Profile Color Space Type Signature: 'CMYK'.
     */
    public static final int icSigCmykData        = 0x434D594B;    /* 'CMYK' */

    /**
     * ICC Profile Color Space Type Signature: 'CMY '.
     */
    public static final int icSigCmyData        = 0x434D5920;    /* 'CMY ' */

    /**
     * ICC Profile Color Space Type Signature: '2CLR'.
     */
    public static final int icSigSpace2CLR        = 0x32434C52;    /* '2CLR' */

    /**
     * ICC Profile Color Space Type Signature: '3CLR'.
     */
    public static final int icSigSpace3CLR        = 0x33434C52;    /* '3CLR' */

    /**
     * ICC Profile Color Space Type Signature: '4CLR'.
     */
    public static final int icSigSpace4CLR        = 0x34434C52;    /* '4CLR' */

    /**
     * ICC Profile Color Space Type Signature: '5CLR'.
     */
    public static final int icSigSpace5CLR        = 0x35434C52;    /* '5CLR' */

    /**
     * ICC Profile Color Space Type Signature: '6CLR'.
     */
    public static final int icSigSpace6CLR        = 0x36434C52;    /* '6CLR' */

    /**
     * ICC Profile Color Space Type Signature: '7CLR'.
     */
    public static final int icSigSpace7CLR        = 0x37434C52;    /* '7CLR' */

    /**
     * ICC Profile Color Space Type Signature: '8CLR'.
     */
    public static final int icSigSpace8CLR        = 0x38434C52;    /* '8CLR' */

    /**
     * ICC Profile Color Space Type Signature: '9CLR'.
     */
    public static final int icSigSpace9CLR        = 0x39434C52;    /* '9CLR' */

    /**
     * ICC Profile Color Space Type Signature: 'ACLR'.
     */
    public static final int icSigSpaceACLR        = 0x41434C52;    /* 'ACLR' */

    /**
     * ICC Profile Color Space Type Signature: 'BCLR'.
     */
    public static final int icSigSpaceBCLR        = 0x42434C52;    /* 'BCLR' */

    /**
     * ICC Profile Color Space Type Signature: 'CCLR'.
     */
    public static final int icSigSpaceCCLR        = 0x43434C52;    /* 'CCLR' */

    /**
     * ICC Profile Color Space Type Signature: 'DCLR'.
     */
    public static final int icSigSpaceDCLR        = 0x44434C52;    /* 'DCLR' */

    /**
     * ICC Profile Color Space Type Signature: 'ECLR'.
     */
    public static final int icSigSpaceECLR        = 0x45434C52;    /* 'ECLR' */

    /**
     * ICC Profile Color Space Type Signature: 'FCLR'.
     */
    public static final int icSigSpaceFCLR        = 0x46434C52;    /* 'FCLR' */


    /**
     * ICC Profile Class Signature: 'scnr'.
     */
    public static final int icSigInputClass       = 0x73636E72;    /* 'scnr' */

    /**
     * ICC Profile Class Signature: 'mntr'.
     */
    public static final int icSigDisplayClass     = 0x6D6E7472;    /* 'mntr' */

    /**
     * ICC Profile Class Signature: 'prtr'.
     */
    public static final int icSigOutputClass      = 0x70727472;    /* 'prtr' */

    /**
     * ICC Profile Class Signature: 'link'.
     */
    public static final int icSigLinkClass        = 0x6C696E6B;    /* 'link' */

    /**
     * ICC Profile Class Signature: 'abst'.
     */
    public static final int icSigAbstractClass    = 0x61627374;    /* 'abst' */

    /**
     * ICC Profile Class Signature: 'spac'.
     */
    public static final int icSigColorSpaceClass  = 0x73706163;    /* 'spac' */

    /**
     * ICC Profile Class Signature: 'nmcl'.
     */
    public static final int icSigNamedColorClass  = 0x6e6d636c;    /* 'nmcl' */


    /**
     * ICC Profile Rendering Intent: Perceptual.
     */
    public static final int icPerceptual            = 0;

    /**
     * ICC Profile Rendering Intent: RelativeColorimetric.
     */
    public static final int icRelativeColorimetric    = 1;

    /**
     * ICC Profile Rendering Intent: Media-RelativeColorimetric.
     * @since 1.5
     */
    public static final int icMediaRelativeColorimetric = 1;

    /**
     * ICC Profile Rendering Intent: Saturation.
     */
    public static final int icSaturation            = 2;

    /**
     * ICC Profile Rendering Intent: AbsoluteColorimetric.
     */
    public static final int icAbsoluteColorimetric    = 3;

    /**
     * ICC Profile Rendering Intent: ICC-AbsoluteColorimetric.
     * @since 1.5
     */
    public static final int icICCAbsoluteColorimetric = 3;


    /**
     * ICC Profile Tag Signature: 'head' - special.
     */
    public static final int icSigHead      = 0x68656164; /* 'head' - special */

    /**
     * ICC Profile Tag Signature: 'A2B0'.
     */
    public static final int icSigAToB0Tag         = 0x41324230;    /* 'A2B0' */

    /**
     * ICC Profile Tag Signature: 'A2B1'.
     */
    public static final int icSigAToB1Tag         = 0x41324231;    /* 'A2B1' */

    /**
     * ICC Profile Tag Signature: 'A2B2'.
     */
    public static final int icSigAToB2Tag         = 0x41324232;    /* 'A2B2' */

    /**
     * ICC Profile Tag Signature: 'bXYZ'.
     */
    public static final int icSigBlueColorantTag  = 0x6258595A;    /* 'bXYZ' */

    /**
     * ICC Profile Tag Signature: 'bXYZ'.
     * @since 1.5
     */
    public static final int icSigBlueMatrixColumnTag = 0x6258595A; /* 'bXYZ' */

    /**
     * ICC Profile Tag Signature: 'bTRC'.
     */
    public static final int icSigBlueTRCTag       = 0x62545243;    /* 'bTRC' */

    /**
     * ICC Profile Tag Signature: 'B2A0'.
     */
    public static final int icSigBToA0Tag         = 0x42324130;    /* 'B2A0' */

    /**
     * ICC Profile Tag Signature: 'B2A1'.
     */
    public static final int icSigBToA1Tag         = 0x42324131;    /* 'B2A1' */

    /**
     * ICC Profile Tag Signature: 'B2A2'.
     */
    public static final int icSigBToA2Tag         = 0x42324132;    /* 'B2A2' */

    /**
     * ICC Profile Tag Signature: 'calt'.
     */
    public static final int icSigCalibrationDateTimeTag = 0x63616C74;
                                                                   /* 'calt' */

    /**
     * ICC Profile Tag Signature: 'targ'.
     */
    public static final int icSigCharTargetTag    = 0x74617267;    /* 'targ' */

    /**
     * ICC Profile Tag Signature: 'cprt'.
     */
    public static final int icSigCopyrightTag     = 0x63707274;    /* 'cprt' */

    /**
     * ICC Profile Tag Signature: 'crdi'.
     */
    public static final int icSigCrdInfoTag       = 0x63726469;    /* 'crdi' */

    /**
     * ICC Profile Tag Signature: 'dmnd'.
     */
    public static final int icSigDeviceMfgDescTag = 0x646D6E64;    /* 'dmnd' */

    /**
     * ICC Profile Tag Signature: 'dmdd'.
     */
    public static final int icSigDeviceModelDescTag = 0x646D6464;  /* 'dmdd' */

    /**
     * ICC Profile Tag Signature: 'devs'.
     */
    public static final int icSigDeviceSettingsTag =  0x64657673;  /* 'devs' */

    /**
     * ICC Profile Tag Signature: 'gamt'.
     */
    public static final int icSigGamutTag         = 0x67616D74;    /* 'gamt' */

    /**
     * ICC Profile Tag Signature: 'kTRC'.
     */
    public static final int icSigGrayTRCTag       = 0x6b545243;    /* 'kTRC' */

    /**
     * ICC Profile Tag Signature: 'gXYZ'.
     */
    public static final int icSigGreenColorantTag = 0x6758595A;    /* 'gXYZ' */

    /**
     * ICC Profile Tag Signature: 'gXYZ'.
     * @since 1.5
     */
    public static final int icSigGreenMatrixColumnTag = 0x6758595A;/* 'gXYZ' */

    /**
     * ICC Profile Tag Signature: 'gTRC'.
     */
    public static final int icSigGreenTRCTag      = 0x67545243;    /* 'gTRC' */

    /**
     * ICC Profile Tag Signature: 'lumi'.
     */
    public static final int icSigLuminanceTag     = 0x6C756d69;    /* 'lumi' */

    /**
     * ICC Profile Tag Signature: 'meas'.
     */
    public static final int icSigMeasurementTag   = 0x6D656173;    /* 'meas' */

    /**
     * ICC Profile Tag Signature: 'bkpt'.
     */
    public static final int icSigMediaBlackPointTag = 0x626B7074;  /* 'bkpt' */

    /**
     * ICC Profile Tag Signature: 'wtpt'.
     */
    public static final int icSigMediaWhitePointTag = 0x77747074;  /* 'wtpt' */

    /**
     * ICC Profile Tag Signature: 'ncl2'.
     */
    public static final int icSigNamedColor2Tag   = 0x6E636C32;    /* 'ncl2' */

    /**
     * ICC Profile Tag Signature: 'resp'.
     */
    public static final int icSigOutputResponseTag = 0x72657370;   /* 'resp' */

    /**
     * ICC Profile Tag Signature: 'pre0'.
     */
    public static final int icSigPreview0Tag      = 0x70726530;    /* 'pre0' */

    /**
     * ICC Profile Tag Signature: 'pre1'.
     */
    public static final int icSigPreview1Tag      = 0x70726531;    /* 'pre1' */

    /**
     * ICC Profile Tag Signature: 'pre2'.
     */
    public static final int icSigPreview2Tag      = 0x70726532;    /* 'pre2' */

    /**
     * ICC Profile Tag Signature: 'desc'.
     */
    public static final int icSigProfileDescriptionTag = 0x64657363;
                                                                   /* 'desc' */

    /**
     * ICC Profile Tag Signature: 'pseq'.
     */
    public static final int icSigProfileSequenceDescTag = 0x70736571;
                                                                   /* 'pseq' */

    /**
     * ICC Profile Tag Signature: 'psd0'.
     */
    public static final int icSigPs2CRD0Tag       = 0x70736430;    /* 'psd0' */

    /**
     * ICC Profile Tag Signature: 'psd1'.
     */
    public static final int icSigPs2CRD1Tag       = 0x70736431;    /* 'psd1' */

    /**
     * ICC Profile Tag Signature: 'psd2'.
     */
    public static final int icSigPs2CRD2Tag       = 0x70736432;    /* 'psd2' */

    /**
     * ICC Profile Tag Signature: 'psd3'.
     */
    public static final int icSigPs2CRD3Tag       = 0x70736433;    /* 'psd3' */

    /**
     * ICC Profile Tag Signature: 'ps2s'.
     */
    public static final int icSigPs2CSATag        = 0x70733273;    /* 'ps2s' */

    /**
     * ICC Profile Tag Signature: 'ps2i'.
     */
    public static final int icSigPs2RenderingIntentTag = 0x70733269;
                                                                   /* 'ps2i' */

    /**
     * ICC Profile Tag Signature: 'rXYZ'.
     */
    public static final int icSigRedColorantTag   = 0x7258595A;    /* 'rXYZ' */

    /**
     * ICC Profile Tag Signature: 'rXYZ'.
     * @since 1.5
     */
    public static final int icSigRedMatrixColumnTag = 0x7258595A;  /* 'rXYZ' */

    /**
     * ICC Profile Tag Signature: 'rTRC'.
     */
    public static final int icSigRedTRCTag        = 0x72545243;    /* 'rTRC' */

    /**
     * ICC Profile Tag Signature: 'scrd'.
     */
    public static final int icSigScreeningDescTag = 0x73637264;    /* 'scrd' */

    /**
     * ICC Profile Tag Signature: 'scrn'.
     */
    public static final int icSigScreeningTag     = 0x7363726E;    /* 'scrn' */

    /**
     * ICC Profile Tag Signature: 'tech'.
     */
    public static final int icSigTechnologyTag    = 0x74656368;    /* 'tech' */

    /**
     * ICC Profile Tag Signature: 'bfd '.
     */
    public static final int icSigUcrBgTag         = 0x62666420;    /* 'bfd ' */

    /**
     * ICC Profile Tag Signature: 'vued'.
     */
    public static final int icSigViewingCondDescTag = 0x76756564;  /* 'vued' */

    /**
     * ICC Profile Tag Signature: 'view'.
     */
    public static final int icSigViewingConditionsTag = 0x76696577;/* 'view' */

    /**
     * ICC Profile Tag Signature: 'chrm'.
     */
    public static final int icSigChromaticityTag  = 0x6368726d;    /* 'chrm' */

    /**
     * ICC Profile Tag Signature: 'chad'.
     * @since 1.5
     */
    public static final int icSigChromaticAdaptationTag = 0x63686164;/* 'chad' */

    /**
     * ICC Profile Tag Signature: 'clro'.
     * @since 1.5
     */
    public static final int icSigColorantOrderTag = 0x636C726F;    /* 'clro' */

    /**
     * ICC Profile Tag Signature: 'clrt'.
     * @since 1.5
     */
    public static final int icSigColorantTableTag = 0x636C7274;    /* 'clrt' */


    /**
     * ICC Profile Header Location: profile size in bytes.
     */
    public static final int icHdrSize         = 0;  /* Profile size in bytes */

    /**
     * ICC Profile Header Location: CMM for this profile.
     */
    public static final int icHdrCmmId        = 4;  /* CMM for this profile */

    /**
     * ICC Profile Header Location: format version number.
     */
    public static final int icHdrVersion      = 8;  /* Format version number */

    /**
     * ICC Profile Header Location: type of profile.
     */
    public static final int icHdrDeviceClass  = 12; /* Type of profile */

    /**
     * ICC Profile Header Location: color space of data.
     */
    public static final int icHdrColorSpace   = 16; /* Color space of data */

    /**
     * ICC Profile Header Location: PCS - XYZ or Lab only.
     */
    public static final int icHdrPcs          = 20; /* PCS - XYZ or Lab only */

    /**
     * ICC Profile Header Location: date profile was created.
     */
    public static final int icHdrDate       = 24; /* Date profile was created */

    /**
     * ICC Profile Header Location: icMagicNumber.
     */
    public static final int icHdrMagic        = 36; /* icMagicNumber */

    /**
     * ICC Profile Header Location: primary platform.
     */
    public static final int icHdrPlatform     = 40; /* Primary Platform */

    /**
     * ICC Profile Header Location: various bit settings.
     */
    public static final int icHdrFlags        = 44; /* Various bit settings */

    /**
     * ICC Profile Header Location: device manufacturer.
     */
    public static final int icHdrManufacturer = 48; /* Device manufacturer */

    /**
     * ICC Profile Header Location: device model number.
     */
    public static final int icHdrModel        = 52; /* Device model number */

    /**
     * ICC Profile Header Location: device attributes.
     */
    public static final int icHdrAttributes   = 56; /* Device attributes */

    /**
     * ICC Profile Header Location: rendering intent.
     */
    public static final int icHdrRenderingIntent = 64; /* Rendering intent */

    /**
     * ICC Profile Header Location: profile illuminant.
     */
    public static final int icHdrIlluminant   = 68; /* Profile illuminant */

    /**
     * ICC Profile Header Location: profile creator.
     */
    public static final int icHdrCreator      = 80; /* Profile creator */

    /**
     * ICC Profile Header Location: profile's ID.
     * @since 1.5
     */
    public static final int icHdrProfileID = 84; /* Profile's ID */


    /**
     * ICC Profile Constant: tag type signaturE.
     */
    public static final int icTagType          = 0;    /* tag type signature */

    /**
     * ICC Profile Constant: reserved.
     */
    public static final int icTagReserved      = 4;    /* reserved */

    /**
     * ICC Profile Constant: curveType count.
     */
    public static final int icCurveCount       = 8;    /* curveType count */

    /**
     * ICC Profile Constant: curveType data.
     */
    public static final int icCurveData        = 12;   /* curveType data */

    /**
     * ICC Profile Constant: XYZNumber X.
     */
    public static final int icXYZNumberX       = 8;    /* XYZNumber X */


    /**
     * Constructs an ICC_Profile object with a given ID.
     */
    ICC_Profile(long ID) {
        this.ID = ID;
    }


    /**
     * Constructs an ICC_Profile object whose loading will be deferred.
     * The ID will be 0 until the profile is loaded.
     */
    ICC_Profile(ProfileDeferralInfo pdi) {
        this.deferralInfo = pdi;
        this.profileActivator = new ProfileActivator() {
            public void activate() throws ProfileDataException {
                activateDeferredProfile();
            }
        };
        ProfileDeferralMgr.registerDeferral(this.profileActivator);
    }


    /**
     * Frees the resources associated with an ICC_Profile object.
     */
    protected void finalize () {
        if (ID != 0) {
            CMSManager.getModule().freeProfile(ID);
        } else if (profileActivator != null) {
            ProfileDeferralMgr.unregisterDeferral(profileActivator);
        }
    }


    /**
     * Constructs an ICC_Profile object corresponding to the data in
     * a byte array.  Throws an IllegalArgumentException if the data
     * does not correspond to a valid ICC Profile.
     * @param data the specified ICC Profile data
     * @return an <code>ICC_Profile</code> object corresponding to
     *          the data in the specified <code>data</code> array.
     */
    public static ICC_Profile getInstance(byte[] data) {
    ICC_Profile thisProfile;

        long theID;

        if (ProfileDeferralMgr.deferring) {
            ProfileDeferralMgr.activateProfiles();
        }

        try {
            theID = CMSManager.getModule().loadProfile(data);
        } catch (CMMException c) {
            throw new IllegalArgumentException("Invalid ICC Profile Data");
        }

        try {
            if ((getColorSpaceType (theID) == ColorSpace.TYPE_GRAY) &&
                (getData (theID, icSigMediaWhitePointTag) != null) &&
                (getData (theID, icSigGrayTRCTag) != null)) {
                thisProfile = new ICC_ProfileGray (theID);
            }
            else if ((getColorSpaceType (theID) == ColorSpace.TYPE_RGB) &&
                (getData (theID, icSigMediaWhitePointTag) != null) &&
                (getData (theID, icSigRedColorantTag) != null) &&
                (getData (theID, icSigGreenColorantTag) != null) &&
                (getData (theID, icSigBlueColorantTag) != null) &&
                (getData (theID, icSigRedTRCTag) != null) &&
                (getData (theID, icSigGreenTRCTag) != null) &&
                (getData (theID, icSigBlueTRCTag) != null)) {
                thisProfile = new ICC_ProfileRGB (theID);
            }
            else {
                thisProfile = new ICC_Profile (theID);
            }
        } catch (CMMException c) {
            thisProfile = new ICC_Profile (theID);
        }
        return thisProfile;
    }



    /**
     * Constructs an ICC_Profile corresponding to one of the specific color
     * spaces defined by the ColorSpace class (for example CS_sRGB).
     * Throws an IllegalArgumentException if cspace is not one of the
     * defined color spaces.
     *
     * @param cspace the type of color space to create a profile for.
     * The specified type is one of the color
     * space constants defined in the  <CODE>ColorSpace</CODE> class.
     *
     * @return an <code>ICC_Profile</code> object corresponding to
     *          the specified <code>ColorSpace</code> type.
     * @exception IllegalArgumentException If <CODE>cspace</CODE> is not
     * one of the predefined color space types.
     */
    public static ICC_Profile getInstance (int cspace) {
        ICC_Profile thisProfile = null;
        String fileName;

        switch (cspace) {
        case ColorSpace.CS_sRGB:
            synchronized(ICC_Profile.class) {
                if (sRGBprofile == null) {
                    /*
                     * Deferral is only used for standard profiles.
                     * Enabling the appropriate access privileges is handled
                     * at a lower level.
                     */
                    ProfileDeferralInfo pInfo =
                        new ProfileDeferralInfo("sRGB.pf",
                                                ColorSpace.TYPE_RGB, 3,
                                                CLASS_DISPLAY);
                    sRGBprofile = getDeferredInstance(pInfo);
                }
                thisProfile = sRGBprofile;
            }

            break;

        case ColorSpace.CS_CIEXYZ:
            synchronized(ICC_Profile.class) {
                if (XYZprofile == null) {
                    ProfileDeferralInfo pInfo =
                        new ProfileDeferralInfo("CIEXYZ.pf",
                                                ColorSpace.TYPE_XYZ, 3,
                                                CLASS_DISPLAY);
                    XYZprofile = getDeferredInstance(pInfo);
                }
                thisProfile = XYZprofile;
            }

            break;

        case ColorSpace.CS_PYCC:
            synchronized(ICC_Profile.class) {
                if (PYCCprofile == null) {
                    if (BootClassLoaderHook.getHook() != null ||
                        standardProfileExists("PYCC.pf"))
                    {
                        ProfileDeferralInfo pInfo =
                            new ProfileDeferralInfo("PYCC.pf",
                                                    ColorSpace.TYPE_3CLR, 3,
                                                    CLASS_DISPLAY);
                        PYCCprofile = getDeferredInstance(pInfo);
                    } else {
                        throw new IllegalArgumentException(
                                "Can't load standard profile: PYCC.pf");
                    }
                }
                thisProfile = PYCCprofile;
            }

            break;

        case ColorSpace.CS_GRAY:
            synchronized(ICC_Profile.class) {
                if (GRAYprofile == null) {
                    ProfileDeferralInfo pInfo =
                        new ProfileDeferralInfo("GRAY.pf",
                                                ColorSpace.TYPE_GRAY, 1,
                                                CLASS_DISPLAY);
                    GRAYprofile = getDeferredInstance(pInfo);
                }
                thisProfile = GRAYprofile;
            }

            break;

        case ColorSpace.CS_LINEAR_RGB:
            synchronized(ICC_Profile.class) {
                if (LINEAR_RGBprofile == null) {
                    ProfileDeferralInfo pInfo =
                        new ProfileDeferralInfo("LINEAR_RGB.pf",
                                                ColorSpace.TYPE_RGB, 3,
                                                CLASS_DISPLAY);
                    LINEAR_RGBprofile = getDeferredInstance(pInfo);
                }
                thisProfile = LINEAR_RGBprofile;
            }

            break;

        default:
            throw new IllegalArgumentException("Unknown color space");
        }

        return thisProfile;
    }

    /* This asserts system privileges, so is used only for the
     * standard profiles.
     */
    private static ICC_Profile getStandardProfile(final String name) {

        return (ICC_Profile) AccessController.doPrivileged(
            new PrivilegedAction() {
                 public Object run() {
                     ICC_Profile p = null;
                     try {
                         p = getInstance (name);
                     } catch (IOException ex) {
                         throw new IllegalArgumentException(
                               "Can't load standard profile: " + name);
                     }
                     return p;
                 }
             });
    }

    /**
     * Constructs an ICC_Profile corresponding to the data in a file.
     * fileName may be an absolute or a relative file specification.
     * Relative file names are looked for in several places: first, relative
     * to any directories specified by the java.iccprofile.path property;
     * second, relative to any directories specified by the java.class.path
     * property; finally, in a directory used to store profiles always
     * available, such as the profile for sRGB.  Built-in profiles use .pf as
     * the file name extension for profiles, e.g. sRGB.pf.
     * This method throws an IOException if the specified file cannot be
     * opened or if an I/O error occurs while reading the file.  It throws
     * an IllegalArgumentException if the file does not contain valid ICC
     * Profile data.
     * @param fileName The file that contains the data for the profile.
     *
     * @return an <code>ICC_Profile</code> object corresponding to
     *          the data in the specified file.
     * @exception IOException If the specified file cannot be opened or
     * an I/O error occurs while reading the file.
     *
     * @exception IllegalArgumentException If the file does not
     * contain valid ICC Profile data.
     *
     * @exception SecurityException If a security manager is installed
     * and it does not permit read access to the given file.
     */
    public static ICC_Profile getInstance(String fileName) throws IOException {
        ICC_Profile thisProfile;
        FileInputStream fis = null;


        File f = getProfileFile(fileName);
        if (f != null) {
            fis = new FileInputStream(f);
        }
        if (fis == null) {
            throw new IOException("Cannot open file " + fileName);
        }

        thisProfile = getInstance(fis);

        fis.close();    /* close the file */

        return thisProfile;
    }


    /**
     * Constructs an ICC_Profile corresponding to the data in an InputStream.
     * This method throws an IllegalArgumentException if the stream does not
     * contain valid ICC Profile data.  It throws an IOException if an I/O
     * error occurs while reading the stream.
     * @param s The input stream from which to read the profile data.
     *
     * @return an <CODE>ICC_Profile</CODE> object corresponding to the
     *     data in the specified <code>InputStream</code>.
     *
     * @exception IOException If an I/O error occurs while reading the stream.
     *
     * @exception IllegalArgumentException If the stream does not
     * contain valid ICC Profile data.
     */
    public static ICC_Profile getInstance(InputStream s) throws IOException {
    byte profileData[];

        if (s instanceof ProfileDeferralInfo) {
            /* hack to detect profiles whose loading can be deferred */
            return getDeferredInstance((ProfileDeferralInfo) s);
        }

        if ((profileData = getProfileDataFromStream(s)) == null) {
            throw new IllegalArgumentException("Invalid ICC Profile Data");
        }

        return getInstance(profileData);
    }


    static byte[] getProfileDataFromStream(InputStream s) throws IOException {
    byte profileData[];
    int profileSize;

        byte header[] = new byte[128];
        int bytestoread = 128;
        int bytesread = 0;
        int n;

        while (bytestoread != 0) {
            if ((n = s.read(header, bytesread, bytestoread)) < 0) {
                return null;
            }
            bytesread += n;
            bytestoread -= n;
        }
        if (header[36] != 0x61 || header[37] != 0x63 ||
            header[38] != 0x73 || header[39] != 0x70) {
            return null;   /* not a valid profile */
        }
        profileSize = ((header[0] & 0xff) << 24) |
                      ((header[1] & 0xff) << 16) |
                      ((header[2] & 0xff) <<  8) |
                       (header[3] & 0xff);
        profileData = new byte[profileSize];
        System.arraycopy(header, 0, profileData, 0, 128);
        bytestoread = profileSize - 128;
        bytesread = 128;
        while (bytestoread != 0) {
            if ((n = s.read(profileData, bytesread, bytestoread)) < 0) {
                return null;
            }
            bytesread += n;
            bytestoread -= n;
        }

        return profileData;
    }


    /**
     * Constructs an ICC_Profile for which the actual loading of the
     * profile data from a file and the initialization of the CMM should
     * be deferred as long as possible.
     * Deferral is only used for standard profiles.
     * If deferring is disabled, then getStandardProfile() ensures
     * that all of the appropriate access privileges are granted
     * when loading this profile.
     * If deferring is enabled, then the deferred activation
     * code will take care of access privileges.
     * @see activateDeferredProfile()
     */
    static ICC_Profile getDeferredInstance(ProfileDeferralInfo pdi) {
        if (!ProfileDeferralMgr.deferring) {
            return getStandardProfile(pdi.filename);
        }
        if (pdi.colorSpaceType == ColorSpace.TYPE_RGB) {
            return new ICC_ProfileRGB(pdi);
        } else if (pdi.colorSpaceType == ColorSpace.TYPE_GRAY) {
            return new ICC_ProfileGray(pdi);
        } else {
            return new ICC_Profile(pdi);
        }
    }


    void activateDeferredProfile() throws ProfileDataException {
        byte profileData[];
        FileInputStream fis;
        final String fileName = deferralInfo.filename;

        profileActivator = null;
        deferralInfo = null;
        PrivilegedAction<FileInputStream> pa = new PrivilegedAction<FileInputStream>() {
            public FileInputStream run() {
                File f = getStandardProfileFile(fileName);
                if (f != null) {
                    try {
                        return new FileInputStream(f);
                    } catch (FileNotFoundException e) {}
                }
                return null;
            }
        };
        if ((fis = AccessController.doPrivileged(pa)) == null) {
            throw new ProfileDataException("Cannot open file " + fileName);
        }
        try {
            profileData = getProfileDataFromStream(fis);
            fis.close();    /* close the file */
        }
        catch (IOException e) {
            ProfileDataException pde = new
                ProfileDataException("Invalid ICC Profile Data" + fileName);
            pde.initCause(e);
            throw pde;
        }
        if (profileData == null) {
            throw new ProfileDataException("Invalid ICC Profile Data" +
                fileName);
        }
        try {
            ID = CMSManager.getModule().loadProfile(profileData);
        } catch (CMMException c) {
            ProfileDataException pde = new
                ProfileDataException("Invalid ICC Profile Data" + fileName);
            pde.initCause(c);
            throw pde;
        }
    }


    /**
     * Returns profile major version.
     * @return  The major version of the profile.
     */
    public int getMajorVersion() {
    byte[] theHeader;

        theHeader = getData(icSigHead); /* getData will activate deferred
                                           profiles if necessary */

        return (int) theHeader[8];
    }

    /**
     * Returns profile minor version.
     * @return The minor version of the profile.
     */
    public int getMinorVersion() {
    byte[] theHeader;

        theHeader = getData(icSigHead); /* getData will activate deferred
                                           profiles if necessary */

        return (int) theHeader[9];
    }

    /**
     * Returns the profile class.
     * @return One of the predefined profile class constants.
     */
    public int getProfileClass() {
    byte[] theHeader;
    int theClassSig, theClass;

        if (deferralInfo != null) {
            return deferralInfo.profileClass; /* Need to have this info for
                                                 ICC_ColorSpace without
                                                 causing a deferred profile
                                                 to be loaded */
        }

        theHeader = getData(icSigHead);

        theClassSig = intFromBigEndian (theHeader, icHdrDeviceClass);

        switch (theClassSig) {
        case icSigInputClass:
            theClass = CLASS_INPUT;
            break;

        case icSigDisplayClass:
            theClass = CLASS_DISPLAY;
            break;

        case icSigOutputClass:
            theClass = CLASS_OUTPUT;
            break;

        case icSigLinkClass:
            theClass = CLASS_DEVICELINK;
            break;

        case icSigColorSpaceClass:
            theClass = CLASS_COLORSPACECONVERSION;
            break;

        case icSigAbstractClass:
            theClass = CLASS_ABSTRACT;
            break;

        case icSigNamedColorClass:
            theClass = CLASS_NAMEDCOLOR;
            break;

        default:
            throw new IllegalArgumentException("Unknown profile class");
        }

        return theClass;
    }

    /**
     * Returns the color space type.  Returns one of the color space type
     * constants defined by the ColorSpace class.  This is the
     * "input" color space of the profile.  The type defines the
     * number of components of the color space and the interpretation,
     * e.g. TYPE_RGB identifies a color space with three components - red,
     * green, and blue.  It does not define the particular color
     * characteristics of the space, e.g. the chromaticities of the
     * primaries.
     * @return One of the color space type constants defined in the
     * <CODE>ColorSpace</CODE> class.
     */
    public int getColorSpaceType() {
        if (deferralInfo != null) {
            return deferralInfo.colorSpaceType; /* Need to have this info for
                                                   ICC_ColorSpace without
                                                   causing a deferred profile
                                                   to be loaded */
        }
        return    getColorSpaceType(ID);
    }

    static int getColorSpaceType(long profileID) {
    byte[] theHeader;
    int theColorSpaceSig, theColorSpace;

        theHeader = getData(profileID, icSigHead);
        theColorSpaceSig = intFromBigEndian(theHeader, icHdrColorSpace);
        theColorSpace = iccCStoJCS (theColorSpaceSig);
        return theColorSpace;
    }

    /**
     * Returns the color space type of the Profile Connection Space (PCS).
     * Returns one of the color space type constants defined by the
     * ColorSpace class.  This is the "output" color space of the
     * profile.  For an input, display, or output profile useful
     * for tagging colors or images, this will be either TYPE_XYZ or
     * TYPE_Lab and should be interpreted as the corresponding specific
     * color space defined in the ICC specification.  For a device
     * link profile, this could be any of the color space type constants.
     * @return One of the color space type constants defined in the
     * <CODE>ColorSpace</CODE> class.
     */
    public int getPCSType() {
        if (ProfileDeferralMgr.deferring) {
            ProfileDeferralMgr.activateProfiles();
        }
        return getPCSType(ID);
    }


    static int getPCSType(long profileID) {
    byte[] theHeader;
    int thePCSSig, thePCS;

        theHeader = getData(profileID, icSigHead);
        thePCSSig = intFromBigEndian(theHeader, icHdrPcs);
        thePCS = iccCStoJCS(thePCSSig);
        return thePCS;
    }


    /**
     * Write this ICC_Profile to a file.
     *
     * @param fileName The file to write the profile data to.
     *
     * @exception IOException If the file cannot be opened for writing
     * or an I/O error occurs while writing to the file.
     */
    public void write(String fileName) throws IOException {
    FileOutputStream outputFile;
    byte profileData[];

        profileData = getData(); /* this will activate deferred
                                    profiles if necessary */
        outputFile = new FileOutputStream(fileName);
        outputFile.write(profileData);
        outputFile.close ();
    }


    /**
     * Write this ICC_Profile to an OutputStream.
     *
     * @param s The stream to write the profile data to.
     *
     * @exception IOException If an I/O error occurs while writing to the
     * stream.
     */
    public void write(OutputStream s) throws IOException {
    byte profileData[];

        profileData = getData(); /* this will activate deferred
                                    profiles if necessary */
        s.write(profileData);
    }


    /**
     * Returns a byte array corresponding to the data of this ICC_Profile.
     * @return A byte array that contains the profile data.
     * @see #setData(int, byte[])
     */
    public byte[] getData() {
    int profileSize;
    byte[] profileData;

        if (ProfileDeferralMgr.deferring) {
            ProfileDeferralMgr.activateProfiles();
        }

        PCMM mdl = CMSManager.getModule();

        /* get the number of bytes needed for this profile */
        profileSize = mdl.getProfileSize(ID);

        profileData = new byte [profileSize];

        /* get the data for the profile */
        mdl.getProfileData(ID, profileData);

        return profileData;
    }


    /**
     * Returns a particular tagged data element from the profile as
     * a byte array.  Elements are identified by signatures
     * as defined in the ICC specification.  The signature
     * icSigHead can be used to get the header.  This method is useful
     * for advanced applets or applications which need to access
     * profile data directly.
     *
     * @param tagSignature The ICC tag signature for the data element you
     * want to get.
     *
     * @return A byte array that contains the tagged data element. Returns
     * <code>null</code> if the specified tag doesn't exist.
     * @see #setData(int, byte[])
     */
    public byte[] getData(int tagSignature) {

        if (ProfileDeferralMgr.deferring) {
            ProfileDeferralMgr.activateProfiles();
        }

        return getData(ID, tagSignature);
    }


    static byte[] getData(long profileID, int tagSignature) {
    int tagSize;
    byte[] tagData;

        try {
            PCMM mdl = CMSManager.getModule();

            /* get the number of bytes needed for this tag */
            tagSize = mdl.getTagSize(profileID, tagSignature);

            tagData = new byte[tagSize]; /* get an array for the tag */

            /* get the tag's data */
            mdl.getTagData(profileID, tagSignature, tagData);
        } catch(CMMException c) {
            tagData = null;
        }

        return tagData;
    }

    /**
     * Sets a particular tagged data element in the profile from
     * a byte array.  This method is useful
     * for advanced applets or applications which need to access
     * profile data directly.
     *
     * @param tagSignature The ICC tag signature for the data element
     * you want to set.
     * @param tagData the data to set for the specified tag signature
     * @see #getData
     */
    public void setData(int tagSignature, byte[] tagData) {

        if (ProfileDeferralMgr.deferring) {
            ProfileDeferralMgr.activateProfiles();
        }

        CMSManager.getModule().setTagData(ID, tagSignature, tagData);
    }

    /**
     * Sets the rendering intent of the profile.
     * This is used to select the proper transform from a profile that
     * has multiple transforms.
     */
    void setRenderingIntent(int renderingIntent) {
        byte[] theHeader = getData(icSigHead);/* getData will activate deferred
                                                 profiles if necessary */
        intToBigEndian (renderingIntent, theHeader, icHdrRenderingIntent);
                                                 /* set the rendering intent */
        setData (icSigHead, theHeader);
    }


    /**
     * Returns the rendering intent of the profile.
     * This is used to select the proper transform from a profile that
     * has multiple transforms.  It is typically set in a source profile
     * to select a transform from an output profile.
     */
    int getRenderingIntent() {
        byte[] theHeader = getData(icSigHead);/* getData will activate deferred
                                                 profiles if necessary */

        int renderingIntent = intFromBigEndian(theHeader, icHdrRenderingIntent);
                                                 /* set the rendering intent */
        return renderingIntent;
    }


    /**
     * Returns the number of color components in the "input" color
     * space of this profile.  For example if the color space type
     * of this profile is TYPE_RGB, then this method will return 3.
     *
     * @return The number of color components in the profile's input
     * color space.
     *
     * @throws ProfileDataException if color space is in the profile
     *         is invalid
     */
    public int getNumComponents() {
    byte[]    theHeader;
    int    theColorSpaceSig, theNumComponents;

        if (deferralInfo != null) {
            return deferralInfo.numComponents; /* Need to have this info for
                                                  ICC_ColorSpace without
                                                  causing a deferred profile
                                                  to be loaded */
        }
        theHeader = getData(icSigHead);

        theColorSpaceSig = intFromBigEndian (theHeader, icHdrColorSpace);

        switch (theColorSpaceSig) {
        case icSigGrayData:
            theNumComponents = 1;
            break;

        case icSigSpace2CLR:
            theNumComponents = 2;
            break;

        case icSigXYZData:
        case icSigLabData:
        case icSigLuvData:
        case icSigYCbCrData:
        case icSigYxyData:
        case icSigRgbData:
        case icSigHsvData:
        case icSigHlsData:
        case icSigCmyData:
        case icSigSpace3CLR:
            theNumComponents = 3;
            break;

        case icSigCmykData:
        case icSigSpace4CLR:
            theNumComponents = 4;
            break;

        case icSigSpace5CLR:
            theNumComponents = 5;
            break;

        case icSigSpace6CLR:
            theNumComponents = 6;
            break;

        case icSigSpace7CLR:
            theNumComponents = 7;
            break;

        case icSigSpace8CLR:
            theNumComponents = 8;
            break;

        case icSigSpace9CLR:
            theNumComponents = 9;
            break;

        case icSigSpaceACLR:
            theNumComponents = 10;
            break;

        case icSigSpaceBCLR:
            theNumComponents = 11;
            break;

        case icSigSpaceCCLR:
            theNumComponents = 12;
            break;

        case icSigSpaceDCLR:
            theNumComponents = 13;
            break;

        case icSigSpaceECLR:
            theNumComponents = 14;
            break;

        case icSigSpaceFCLR:
            theNumComponents = 15;
            break;

        default:
            throw new ProfileDataException ("invalid ICC color space");
        }

        return theNumComponents;
    }


    /**
     * Returns a float array of length 3 containing the X, Y, and Z
     * components of the mediaWhitePointTag in the ICC profile.
     */
    float[] getMediaWhitePoint() {
        return getXYZTag(icSigMediaWhitePointTag);
                                           /* get the media white point tag */
    }


    /**
     * Returns a float array of length 3 containing the X, Y, and Z
     * components encoded in an XYZType tag.
     */
    float[] getXYZTag(int theTagSignature) {
    byte[] theData;
    float[] theXYZNumber;
    int i1, i2, theS15Fixed16;

        theData = getData(theTagSignature); /* get the tag data */
                                            /* getData will activate deferred
                                               profiles if necessary */

        theXYZNumber = new float [3];        /* array to return */

        /* convert s15Fixed16Number to float */
        for (i1 = 0, i2 = icXYZNumberX; i1 < 3; i1++, i2 += 4) {
            theS15Fixed16 = intFromBigEndian(theData, i2);
            theXYZNumber [i1] = ((float) theS15Fixed16) / 65536.0f;
        }
        return theXYZNumber;
    }


    /**
     * Returns a gamma value representing a tone reproduction
     * curve (TRC).  If the profile represents the TRC as a table rather
     * than a single gamma value, then an exception is thrown.  In this
     * case the actual table can be obtained via getTRC().
     * theTagSignature should be one of icSigGrayTRCTag, icSigRedTRCTag,
     * icSigGreenTRCTag, or icSigBlueTRCTag.
     * @return the gamma value as a float.
     * @exception ProfileDataException if the profile does not specify
     *            the TRC as a single gamma value.
     */
    float getGamma(int theTagSignature) {
    byte[] theTRCData;
    float theGamma;
    int theU8Fixed8;

        theTRCData = getData(theTagSignature); /* get the TRC */
                                               /* getData will activate deferred
                                                  profiles if necessary */

        if (intFromBigEndian (theTRCData, icCurveCount) != 1) {
            throw new ProfileDataException ("TRC is not a gamma");
        }

        /* convert u8Fixed8 to float */
        theU8Fixed8 = (shortFromBigEndian(theTRCData, icCurveData)) & 0xffff;

        theGamma = ((float) theU8Fixed8) / 256.0f;

        return theGamma;
    }


    /**
     * Returns the TRC as an array of shorts.  If the profile has
     * specified the TRC as linear (gamma = 1.0) or as a simple gamma
     * value, this method throws an exception, and the getGamma() method
     * should be used to get the gamma value.  Otherwise the short array
     * returned here represents a lookup table where the input Gray value
     * is conceptually in the range [0.0, 1.0].  Value 0.0 maps
     * to array index 0 and value 1.0 maps to array index length-1.
     * Interpolation may be used to generate output values for
     * input values which do not map exactly to an index in the
     * array.  Output values also map linearly to the range [0.0, 1.0].
     * Value 0.0 is represented by an array value of 0x0000 and
     * value 1.0 by 0xFFFF, i.e. the values are really unsigned
     * short values, although they are returned in a short array.
     * theTagSignature should be one of icSigGrayTRCTag, icSigRedTRCTag,
     * icSigGreenTRCTag, or icSigBlueTRCTag.
     * @return a short array representing the TRC.
     * @exception ProfileDataException if the profile does not specify
     *            the TRC as a table.
     */
    short[] getTRC(int theTagSignature) {
    byte[] theTRCData;
    short[] theTRC;
    int i1, i2, nElements, theU8Fixed8;

        theTRCData = getData(theTagSignature); /* get the TRC */
                                               /* getData will activate deferred
                                                  profiles if necessary */

        nElements = intFromBigEndian(theTRCData, icCurveCount);

        if (nElements == 1) {
            throw new ProfileDataException("TRC is not a table");
        }

        /* make the short array */
        theTRC = new short [nElements];

        for (i1 = 0, i2 = icCurveData; i1 < nElements; i1++, i2 += 2) {
            theTRC[i1] = shortFromBigEndian(theTRCData, i2);
        }

        return theTRC;
    }


    /* convert an ICC color space signature into a Java color space type */
    static int iccCStoJCS(int theColorSpaceSig) {
    int theColorSpace;

        switch (theColorSpaceSig) {
        case icSigXYZData:
            theColorSpace = ColorSpace.TYPE_XYZ;
            break;

        case icSigLabData:
            theColorSpace = ColorSpace.TYPE_Lab;
            break;

        case icSigLuvData:
            theColorSpace = ColorSpace.TYPE_Luv;
            break;

        case icSigYCbCrData:
            theColorSpace = ColorSpace.TYPE_YCbCr;
            break;

        case icSigYxyData:
            theColorSpace = ColorSpace.TYPE_Yxy;
            break;

        case icSigRgbData:
            theColorSpace = ColorSpace.TYPE_RGB;
            break;

        case icSigGrayData:
            theColorSpace = ColorSpace.TYPE_GRAY;
            break;

        case icSigHsvData:
            theColorSpace = ColorSpace.TYPE_HSV;
            break;

        case icSigHlsData:
            theColorSpace = ColorSpace.TYPE_HLS;
            break;

        case icSigCmykData:
            theColorSpace = ColorSpace.TYPE_CMYK;
            break;

        case icSigCmyData:
            theColorSpace = ColorSpace.TYPE_CMY;
            break;

        case icSigSpace2CLR:
            theColorSpace = ColorSpace.TYPE_2CLR;
            break;

        case icSigSpace3CLR:
            theColorSpace = ColorSpace.TYPE_3CLR;
            break;

        case icSigSpace4CLR:
            theColorSpace = ColorSpace.TYPE_4CLR;
            break;

        case icSigSpace5CLR:
            theColorSpace = ColorSpace.TYPE_5CLR;
            break;

        case icSigSpace6CLR:
            theColorSpace = ColorSpace.TYPE_6CLR;
            break;

        case icSigSpace7CLR:
            theColorSpace = ColorSpace.TYPE_7CLR;
            break;

        case icSigSpace8CLR:
            theColorSpace = ColorSpace.TYPE_8CLR;
            break;

        case icSigSpace9CLR:
            theColorSpace = ColorSpace.TYPE_9CLR;
            break;

        case icSigSpaceACLR:
            theColorSpace = ColorSpace.TYPE_ACLR;
            break;

        case icSigSpaceBCLR:
            theColorSpace = ColorSpace.TYPE_BCLR;
            break;

        case icSigSpaceCCLR:
            theColorSpace = ColorSpace.TYPE_CCLR;
            break;

        case icSigSpaceDCLR:
            theColorSpace = ColorSpace.TYPE_DCLR;
            break;

        case icSigSpaceECLR:
            theColorSpace = ColorSpace.TYPE_ECLR;
            break;

        case icSigSpaceFCLR:
            theColorSpace = ColorSpace.TYPE_FCLR;
            break;

        default:
            throw new IllegalArgumentException ("Unknown color space");
        }

        return theColorSpace;
    }


    static int intFromBigEndian(byte[] array, int index) {
        return (((array[index]   & 0xff) << 24) |
                ((array[index+1] & 0xff) << 16) |
                ((array[index+2] & 0xff) <<  8) |
                 (array[index+3] & 0xff));
    }


    static void intToBigEndian(int value, byte[] array, int index) {
            array[index]   = (byte) (value >> 24);
            array[index+1] = (byte) (value >> 16);
            array[index+2] = (byte) (value >>  8);
            array[index+3] = (byte) (value);
    }


    static short shortFromBigEndian(byte[] array, int index) {
        return (short) (((array[index]   & 0xff) << 8) |
                         (array[index+1] & 0xff));
    }


    static void shortToBigEndian(short value, byte[] array, int index) {
            array[index]   = (byte) (value >> 8);
            array[index+1] = (byte) (value);
    }


    /*
     * fileName may be an absolute or a relative file specification.
     * Relative file names are looked for in several places: first, relative
     * to any directories specified by the java.iccprofile.path property;
     * second, relative to any directories specified by the java.class.path
     * property; finally, in a directory used to store profiles always
     * available, such as a profile for sRGB.  Built-in profiles use .pf as
     * the file name extension for profiles, e.g. sRGB.pf.
     */
    private static File getProfileFile(String fileName) {
        String path, dir, fullPath;

        File f = new File(fileName); /* try absolute file name */
        if (f.isAbsolute()) {
            /* Rest of code has little sense for an absolute pathname,
               so return here. */
            return f.isFile() ? f : null;
        }
        if ((!f.isFile()) &&
                ((path = System.getProperty("java.iccprofile.path")) != null)){
                                    /* try relative to java.iccprofile.path */
                StringTokenizer st =
                    new StringTokenizer(path, File.pathSeparator);
                while (st.hasMoreTokens() && ((f == null) || (!f.isFile()))) {
                    dir = st.nextToken();
                        fullPath = dir + File.separatorChar + fileName;
                    f = new File(fullPath);
                    if (!isChildOf(f, dir)) {
                        f = null;
                    }
                }
            }

        if (((f == null) || (!f.isFile())) &&
                ((path = System.getProperty("java.class.path")) != null)) {
                                    /* try relative to java.class.path */
                StringTokenizer st =
                    new StringTokenizer(path, File.pathSeparator);
                while (st.hasMoreTokens() && ((f == null) || (!f.isFile()))) {
                    dir = st.nextToken();
                        fullPath = dir + File.separatorChar + fileName;
                    f = new File(fullPath);
                }
            }

        if ((f == null) || (!f.isFile())) {
            /* try the directory of built-in profiles */
            f = getStandardProfileFile(fileName);
        }
        if (f != null && f.isFile()) {
            return f;
        }
        return null;
    }

    /**
     * Returns a file object corresponding to a built-in profile
     * specified by fileName.
     * If there is no built-in profile with such name, then the method
     * returns null.
     */
    private static File getStandardProfileFile(String fileName) {
        String dir = System.getProperty("java.home") +
            File.separatorChar + "lib" + File.separatorChar + "cmm";
        String fullPath = dir + File.separatorChar + fileName;
        File f = new File(fullPath);
        if (!f.isFile()) {
            //make sure file was installed in the kernel mode
            BootClassLoaderHook hook = BootClassLoaderHook.getHook();
            if (hook != null) {
                hook.prefetchFile("lib/cmm/"+fileName);
            }
        }
        return (f.isFile() && isChildOf(f, dir)) ? f : null;
    }

    /**
     * Checks whether given file resides inside give directory.
     */
    private static boolean isChildOf(File f, String dirName) {
        try {
            File dir = new File(dirName);
            String canonicalDirName = dir.getCanonicalPath();
            if (!canonicalDirName.endsWith(File.separator)) {
                canonicalDirName += File.separator;
            }
            String canonicalFileName = f.getCanonicalPath();
            return canonicalFileName.startsWith(canonicalDirName);
        } catch (IOException e) {
            /* we do not expect the IOException here, because invocation
             * of this function is always preceeded by isFile() call.
             */
            return false;
        }
    }

    /**
     * Checks whether built-in profile specified by fileName exists.
     */
    private static boolean standardProfileExists(final String fileName) {
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    return getStandardProfileFile(fileName) != null;
                }
            });
    }


    /*
     * Serialization support.
     *
     * Directly deserialized profiles are useless since they are not
     * registered with CMM.  We don't allow constructor to be called
     * directly and instead have clients to call one of getInstance
     * factory methods that will register the profile with CMM.  For
     * deserialization we implement readResolve method that will
     * resolve the bogus deserialized profile object with one obtained
     * with getInstance as well.
     *
     * There're two primary factory methods for construction of ICC
     * profiles: getInstance(int cspace) and getInstance(byte[] data).
     * This implementation of ICC_Profile uses the former to return a
     * cached singleton profile object, other implementations will
     * likely use this technique too.  To preserve the singleton
     * pattern across serialization we serialize cached singleton
     * profiles in such a way that deserializing VM could call
     * getInstance(int cspace) method that will resolve deserialized
     * object into the corresponding singleton as well.
     *
     * Since the singletons are private to ICC_Profile the readResolve
     * method have to be `protected' instead of `private' so that
     * singletons that are instances of subclasses of ICC_Profile
     * could be correctly deserialized.
     */


    /**
     * Version of the format of additional serialized data in the
     * stream.  Version&nbsp;<code>1</code> corresponds to Java&nbsp;2
     * Platform,&nbsp;v1.3.
     * @since 1.3
     * @serial
     */
    private int iccProfileSerializedDataVersion = 1;


    /**
     * Writes default serializable fields to the stream.  Writes a
     * string and an array of bytes to the stream as additional data.
     *
     * @param s stream used for serialization.
     * @throws IOException
     *     thrown by <code>ObjectInputStream</code>.
     * @serialData
     *     The <code>String</code> is the name of one of
     *     <code>CS_<var>*</var></code> constants defined in the
     *     {@link ColorSpace} class if the profile object is a profile
     *     for a predefined color space (for example
     *     <code>"CS_sRGB"</code>).  The string is <code>null</code>
     *     otherwise.
     *     <p>
     *     The <code>byte[]</code> array is the profile data for the
     *     profile.  For predefined color spaces <code>null</code> is
     *     written instead of the profile data.  If in the future
     *     versions of Java API new predefined color spaces will be
     *     added, future versions of this class may choose to write
     *     for new predefined color spaces not only the color space
     *     name, but the profile data as well so that older versions
     *     could still deserialize the object.
     */
    private void writeObject(ObjectOutputStream s)
      throws IOException
    {
        s.defaultWriteObject();

        String csName = null;
        if (this == sRGBprofile) {
            csName = "CS_sRGB";
        } else if (this == XYZprofile) {
            csName = "CS_CIEXYZ";
        } else if (this == PYCCprofile) {
            csName = "CS_PYCC";
        } else if (this == GRAYprofile) {
            csName = "CS_GRAY";
        } else if (this == LINEAR_RGBprofile) {
            csName = "CS_LINEAR_RGB";
        }

        // Future versions may choose to write profile data for new
        // predefined color spaces as well, if any will be introduced,
        // so that old versions that don't recognize the new CS name
        // may fall back to constructing profile from the data.
        byte[] data = null;
        if (csName == null) {
            // getData will activate deferred profile if necessary
            data = getData();
        }

        s.writeObject(csName);
        s.writeObject(data);
    }

    // Temporary storage used by readObject to store resolved profile
    // (obtained with getInstance) for readResolve to return.
    private transient ICC_Profile resolvedDeserializedProfile;

    /**
     * Reads default serializable fields from the stream.  Reads from
     * the stream a string and an array of bytes as additional data.
     *
     * @param s stream used for deserialization.
     * @throws IOException
     *     thrown by <code>ObjectInputStream</code>.
     * @throws ClassNotFoundException
     *     thrown by <code>ObjectInputStream</code>.
     * @serialData
     *     The <code>String</code> is the name of one of
     *     <code>CS_<var>*</var></code> constants defined in the
     *     {@link ColorSpace} class if the profile object is a profile
     *     for a predefined color space (for example
     *     <code>"CS_sRGB"</code>).  The string is <code>null</code>
     *     otherwise.
     *     <p>
     *     The <code>byte[]</code> array is the profile data for the
     *     profile.  It will usually be <code>null</code> for the
     *     predefined profiles.
     *     <p>
     *     If the string is recognized as a constant name for
     *     predefined color space the object will be resolved into
     *     profile obtained with
     *     <code>getInstance(int&nbsp;cspace)</code> and the profile
     *     data are ignored.  Otherwise the object will be resolved
     *     into profile obtained with
     *     <code>getInstance(byte[]&nbsp;data)</code>.
     * @see #readResolve()
     * @see #getInstance(int)
     * @see #getInstance(byte[])
     */
    private void readObject(ObjectInputStream s)
      throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();

        String csName = (String)s.readObject();
        byte[] data = (byte[])s.readObject();

        int cspace = 0;         // ColorSpace.CS_* constant if known
        boolean isKnownPredefinedCS = false;
        if (csName != null) {
            isKnownPredefinedCS = true;
            if (csName.equals("CS_sRGB")) {
                cspace = ColorSpace.CS_sRGB;
            } else if (csName.equals("CS_CIEXYZ")) {
                cspace = ColorSpace.CS_CIEXYZ;
            } else if (csName.equals("CS_PYCC")) {
                cspace = ColorSpace.CS_PYCC;
            } else if (csName.equals("CS_GRAY")) {
                cspace = ColorSpace.CS_GRAY;
            } else if (csName.equals("CS_LINEAR_RGB")) {
                cspace = ColorSpace.CS_LINEAR_RGB;
            } else {
                isKnownPredefinedCS = false;
            }
        }

        if (isKnownPredefinedCS) {
            resolvedDeserializedProfile = getInstance(cspace);
        } else {
            resolvedDeserializedProfile = getInstance(data);
        }
    }

    /**
     * Resolves instances being deserialized into instances registered
     * with CMM.
     * @return ICC_Profile object for profile registered with CMM.
     * @throws ObjectStreamException
     *     never thrown, but mandated by the serialization spec.
     * @since 1.3
     */
    protected Object readResolve() throws ObjectStreamException {
        return resolvedDeserializedProfile;
    }
}
