/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

/* Header file guard bands */
#ifndef ICC_H
#define ICC_H

/*
 * This version of the header file corresponds to the profile
 * specification version 3.4.
 *
 * All header file entries are pre-fixed with "ic" to help
 * avoid name space collisions. Signatures are pre-fixed with
 * icSig.
 *
 * The structures defined in this header file were created to
 * represent a description of an ICC profile on disk. Rather
 * than use pointers a technique is used where a single byte array
 * was placed at the end of each structure. This allows us in "C"
 * to extend the structure by allocating more data than is needed
 * to account for variable length structures.
 *
 * This also ensures that data following is allocated
 * contiguously and makes it easier to write and read data from
 * the file.
 *
 * For example to allocate space for a 256 count length UCR
 * and BG array, and fill the allocated data.  Note strlen + 1
 * to remember NULL terminator.
 *
        icUcrBgCurve    *ucrCurve, *bgCurve;
        int             ucr_nbytes, bg_nbytes, string_bytes;
        icUcrBg         *ucrBgWrite;
        char            ucr_string[100], *ucr_char;

        strcpy(ucr_string, "Example ucrBG curves");
        ucr_nbytes = sizeof(icUInt32Number) +
                 (UCR_CURVE_SIZE * sizeof(icUInt16Number));
        bg_nbytes = sizeof(icUInt32Number) +
                 (BG_CURVE_SIZE * sizeof(icUInt16Number));
        string_bytes = strlen(ucr_string) + 1;

        ucrBgWrite = (icUcrBg *)malloc(
                                (ucr_nbytes + bg_nbytes + string_bytes));

        ucrCurve = (icUcrBgCurve *)ucrBgWrite->data;
        ucrCurve->count = UCR_CURVE_SIZE;
        for (i=0; i<ucrCurve->count; i++)
                ucrCurve->curve[i] = (icUInt16Number)i;

        bgCurve = (icUcrBgCurve *)((char *)ucrCurve + ucr_nbytes);
        bgCurve->count = BG_CURVE_SIZE;
        for (i=0; i<bgCurve->count; i++)
                bgCurve->curve[i] = 255 - (icUInt16Number)i;

        ucr_char = (char *)((char *)bgCurve + bg_nbytes);
        memcpy(ucr_char, ucr_string, string_bytes);
 *
 */

/*
 * Many of the structures contain variable length arrays. This
 * is represented by the use of the convention.
 *
 *      type    data[icAny];
 */

/*------------------------------------------------------------------------*/
/*
 * Defines used in the specification
 */
#define icMagicNumber                   0x61637370L     /* 'acsp' */
#define icVersionNumber                 0x02100000L     /* 2.1.0, BCD */

/* Screening Encodings */
#define icPrtrDefaultScreensFalse       0x00000000L     /* Bit pos 0 */
#define icPrtrDefaultScreensTrue        0x00000001L     /* Bit pos 0 */
#define icLinesPerInch                  0x00000002L     /* Bit pos 1 */
#define icLinesPerCm                    0x00000000L     /* Bit pos 1 */

/*
 * Device attributes, currently defined values correspond
 * to the low 4 bytes of the 8 byte attribute quantity, see
 * the header for their location.
 */
#define icReflective                    0x00000000L     /* Bit pos 0 */
#define icTransparency                  0x00000001L     /* Bit pos 0 */
#define icGlossy                        0x00000000L     /* Bit pos 1 */
#define icMatte                         0x00000002L     /* Bit pos 1 */

/*
 * Profile header flags, the low 16 bits are reserved for consortium
 * use.
 */
#define icEmbeddedProfileFalse          0x00000000L     /* Bit pos 0 */
#define icEmbeddedProfileTrue           0x00000001L     /* Bit pos 0 */
#define icUseAnywhere                   0x00000000L     /* Bit pos 1 */
#define icUseWithEmbeddedDataOnly       0x00000002L     /* Bit pos 1 */

/* Ascii or Binary data */
#define icAsciiData                     0x00000000L
#define icBinaryData                    0x00000001L

/*
 * Define used to indicate that this is a variable length array
 */
#define icAny                           1


/*------------------------------------------------------------------------*/
/*
 * Use this area to translate platform definitions of long
 * etc into icXXX form. The rest of the header uses the icXXX
 * typedefs. Signatures are 4 byte quantities.
 *
 */


#ifdef PACKAGE_NAME
/*
  June 9, 2003, Adapted for use with configure by Bob Friesenhahn
  Added the stupid check for autoconf by Marti Maria.
  PACKAGE_NAME is defined if autoconf is being used
*/

typedef @UINT8_T@       icUInt8Number;
typedef @UINT16_T@      icUInt16Number;
typedef @UINT32_T@      icUInt32Number;
typedef @UINT32_T@      icUInt64Number[2];

typedef @INT8_T@        icInt8Number;
typedef @INT16_T@       icInt16Number;
typedef @INT32_T@       icInt32Number;
typedef @INT32_T@       icInt64Number[2];

#else

/*
 *Apr-17-2002: Modified by Marti Maria in order to provide wider portability.
 */

#if defined (__digital__) && defined (__unix__)

/* Tru64 */

#include <inttypes.h>

typedef uint8_t   icUInt8Number;
typedef uint16_t  icUInt16Number;
typedef uint32_t  icUInt32Number;
typedef uint32_t  icUInt64Number[2];

typedef int8_t     icInt8Number;
typedef int16_t    icInt16Number;
typedef int32_t    icInt32Number;
typedef int32_t    icInt64Number[2];

#else
#ifdef __sgi
#include "sgidefs.h"


/*
 * Number definitions
 */

/* Unsigned integer numbers */
typedef unsigned char   icUInt8Number;
typedef unsigned short  icUInt16Number;
typedef __uint32_t      icUInt32Number;
typedef __uint32_t      icUInt64Number[2];

/* Signed numbers */
typedef char            icInt8Number;
typedef short           icInt16Number;
typedef __int32_t       icInt32Number;
typedef __int32_t       icInt64Number[2];


#else
#if defined(__GNUC__) || defined(__unix__) || defined(__unix)

#include <sys/types.h>

#if defined(__sun) || defined(__hpux) || defined (__MINGW) || defined(__MINGW32__)

#if defined (__MINGW) || defined(__MINGW32__)
#include <stdint.h>
#endif


typedef uint8_t   icUInt8Number;
typedef uint16_t  icUInt16Number;
typedef uint32_t  icUInt32Number;
typedef uint32_t  icUInt64Number[2];

#else

/* Unsigned integer numbers */
typedef u_int8_t   icUInt8Number;
typedef u_int16_t  icUInt16Number;
typedef u_int32_t  icUInt32Number;
typedef u_int32_t  icUInt64Number[2];

#endif


/* Signed numbers */
typedef int8_t     icInt8Number;
typedef int16_t    icInt16Number;
typedef int32_t    icInt32Number;
typedef int32_t    icInt64Number[2];


#else /* default definitions */

/*
 * Number definitions
 */

/* Unsigned integer numbers */
typedef unsigned char   icUInt8Number;
typedef unsigned short  icUInt16Number;
typedef unsigned long   icUInt32Number;
typedef unsigned long   icUInt64Number[2];

/* Signed numbers */
typedef char            icInt8Number;
typedef short           icInt16Number;
typedef long            icInt32Number;
typedef long            icInt64Number[2];


#endif  /* default defs */
#endif
#endif
#endif

/* Base types */

typedef icInt32Number    icSignature;
typedef icInt32Number    icS15Fixed16Number;
typedef icUInt32Number   icU16Fixed16Number;


/*------------------------------------------------------------------------*/
/* public tags and sizes */
typedef enum {
    icSigAToB0Tag                       = 0x41324230L,  /* 'A2B0' */
    icSigAToB1Tag                       = 0x41324231L,  /* 'A2B1' */
    icSigAToB2Tag                       = 0x41324232L,  /* 'A2B2' */
    icSigBlueColorantTag                = 0x6258595AL,  /* 'bXYZ' */
    icSigBlueTRCTag                     = 0x62545243L,  /* 'bTRC' */
    icSigBToA0Tag                       = 0x42324130L,  /* 'B2A0' */
    icSigBToA1Tag                       = 0x42324131L,  /* 'B2A1' */
    icSigBToA2Tag                       = 0x42324132L,  /* 'B2A2' */
    icSigCalibrationDateTimeTag         = 0x63616C74L,  /* 'calt' */
    icSigCharTargetTag                  = 0x74617267L,  /* 'targ' */
    icSigCopyrightTag                   = 0x63707274L,  /* 'cprt' */
    icSigCrdInfoTag                     = 0x63726469L,  /* 'crdi' */
    icSigDeviceMfgDescTag               = 0x646D6E64L,  /* 'dmnd' */
    icSigDeviceModelDescTag             = 0x646D6464L,  /* 'dmdd' */
    icSigGamutTag                       = 0x67616D74L,  /* 'gamt ' */
    icSigGrayTRCTag                     = 0x6b545243L,  /* 'kTRC' */
    icSigGreenColorantTag               = 0x6758595AL,  /* 'gXYZ' */
    icSigGreenTRCTag                    = 0x67545243L,  /* 'gTRC' */
    icSigLuminanceTag                   = 0x6C756d69L,  /* 'lumi' */
    icSigMeasurementTag                 = 0x6D656173L,  /* 'meas' */
    icSigMediaBlackPointTag             = 0x626B7074L,  /* 'bkpt' */
    icSigMediaWhitePointTag             = 0x77747074L,  /* 'wtpt' */
    icSigNamedColorTag                  = 0x6E636f6CL,  /* 'ncol'
                                                         * OBSOLETE, use ncl2 */
    icSigNamedColor2Tag                 = 0x6E636C32L,  /* 'ncl2' */
    icSigPreview0Tag                    = 0x70726530L,  /* 'pre0' */
    icSigPreview1Tag                    = 0x70726531L,  /* 'pre1' */
    icSigPreview2Tag                    = 0x70726532L,  /* 'pre2' */
    icSigProfileDescriptionTag          = 0x64657363L,  /* 'desc' */
    icSigProfileSequenceDescTag         = 0x70736571L,  /* 'pseq' */
    icSigPs2CRD0Tag                     = 0x70736430L,  /* 'psd0' */
    icSigPs2CRD1Tag                     = 0x70736431L,  /* 'psd1' */
    icSigPs2CRD2Tag                     = 0x70736432L,  /* 'psd2' */
    icSigPs2CRD3Tag                     = 0x70736433L,  /* 'psd3' */
    icSigPs2CSATag                      = 0x70733273L,  /* 'ps2s' */
    icSigPs2RenderingIntentTag          = 0x70733269L,  /* 'ps2i' */
    icSigRedColorantTag                 = 0x7258595AL,  /* 'rXYZ' */
    icSigRedTRCTag                      = 0x72545243L,  /* 'rTRC' */
    icSigScreeningDescTag               = 0x73637264L,  /* 'scrd' */
    icSigScreeningTag                   = 0x7363726EL,  /* 'scrn' */
    icSigTechnologyTag                  = 0x74656368L,  /* 'tech' */
    icSigUcrBgTag                       = 0x62666420L,  /* 'bfd ' */
    icSigViewingCondDescTag             = 0x76756564L,  /* 'vued' */
    icSigViewingConditionsTag           = 0x76696577L,  /* 'view' */
    icMaxEnumTag                        = 0xFFFFFFFFL
} icTagSignature;

/* technology signature descriptions */
typedef enum {
    icSigDigitalCamera                  = 0x6463616DL,  /* 'dcam' */
    icSigFilmScanner                    = 0x6673636EL,  /* 'fscn' */
    icSigReflectiveScanner              = 0x7273636EL,  /* 'rscn' */
    icSigInkJetPrinter                  = 0x696A6574L,  /* 'ijet' */
    icSigThermalWaxPrinter              = 0x74776178L,  /* 'twax' */
    icSigElectrophotographicPrinter     = 0x6570686FL,  /* 'epho' */
    icSigElectrostaticPrinter           = 0x65737461L,  /* 'esta' */
    icSigDyeSublimationPrinter          = 0x64737562L,  /* 'dsub' */
    icSigPhotographicPaperPrinter       = 0x7270686FL,  /* 'rpho' */
    icSigFilmWriter                     = 0x6670726EL,  /* 'fprn' */
    icSigVideoMonitor                   = 0x7669646DL,  /* 'vidm' */
    icSigVideoCamera                    = 0x76696463L,  /* 'vidc' */
    icSigProjectionTelevision           = 0x706A7476L,  /* 'pjtv' */
    icSigCRTDisplay                     = 0x43525420L,  /* 'CRT ' */
    icSigPMDisplay                      = 0x504D4420L,  /* 'PMD ' */
    icSigAMDisplay                      = 0x414D4420L,  /* 'AMD ' */
    icSigPhotoCD                        = 0x4B504344L,  /* 'KPCD' */
    icSigPhotoImageSetter               = 0x696D6773L,  /* 'imgs' */
    icSigGravure                        = 0x67726176L,  /* 'grav' */
    icSigOffsetLithography              = 0x6F666673L,  /* 'offs' */
    icSigSilkscreen                     = 0x73696C6BL,  /* 'silk' */
    icSigFlexography                    = 0x666C6578L,  /* 'flex' */
    icMaxEnumTechnology                 = 0xFFFFFFFFL
} icTechnologySignature;

/* type signatures */
typedef enum {
    icSigCurveType                      = 0x63757276L,  /* 'curv' */
    icSigDataType                       = 0x64617461L,  /* 'data' */
    icSigDateTimeType                   = 0x6474696DL,  /* 'dtim' */
    icSigLut16Type                      = 0x6d667432L,  /* 'mft2' */
    icSigLut8Type                       = 0x6d667431L,  /* 'mft1' */
    icSigMeasurementType                = 0x6D656173L,  /* 'meas' */
    icSigNamedColorType                 = 0x6E636f6CL,  /* 'ncol'
                                                         * OBSOLETE, use ncl2 */
    icSigProfileSequenceDescType        = 0x70736571L,  /* 'pseq' */
    icSigS15Fixed16ArrayType            = 0x73663332L,  /* 'sf32' */
    icSigScreeningType                  = 0x7363726EL,  /* 'scrn' */
    icSigSignatureType                  = 0x73696720L,  /* 'sig ' */
    icSigTextType                       = 0x74657874L,  /* 'text' */
    icSigTextDescriptionType            = 0x64657363L,  /* 'desc' */
    icSigU16Fixed16ArrayType            = 0x75663332L,  /* 'uf32' */
    icSigUcrBgType                      = 0x62666420L,  /* 'bfd ' */
    icSigUInt16ArrayType                = 0x75693136L,  /* 'ui16' */
    icSigUInt32ArrayType                = 0x75693332L,  /* 'ui32' */
    icSigUInt64ArrayType                = 0x75693634L,  /* 'ui64' */
    icSigUInt8ArrayType                 = 0x75693038L,  /* 'ui08' */
    icSigViewingConditionsType          = 0x76696577L,  /* 'view' */
    icSigXYZType                        = 0x58595A20L,  /* 'XYZ ' */
    icSigXYZArrayType                   = 0x58595A20L,  /* 'XYZ ' */
    icSigNamedColor2Type                = 0x6E636C32L,  /* 'ncl2' */
    icSigCrdInfoType                    = 0x63726469L,  /* 'crdi' */
    icMaxEnumType                       = 0xFFFFFFFFL
} icTagTypeSignature;

/*
 * Color Space Signatures
 * Note that only icSigXYZData and icSigLabData are valid
 * Profile Connection Spaces (PCSs)
 */
typedef enum {
    icSigXYZData                        = 0x58595A20L,  /* 'XYZ ' */
    icSigLabData                        = 0x4C616220L,  /* 'Lab ' */
    icSigLuvData                        = 0x4C757620L,  /* 'Luv ' */
    icSigYCbCrData                      = 0x59436272L,  /* 'YCbr' */
    icSigYxyData                        = 0x59787920L,  /* 'Yxy ' */
    icSigRgbData                        = 0x52474220L,  /* 'RGB ' */
    icSigGrayData                       = 0x47524159L,  /* 'GRAY' */
    icSigHsvData                        = 0x48535620L,  /* 'HSV ' */
    icSigHlsData                        = 0x484C5320L,  /* 'HLS ' */
    icSigCmykData                       = 0x434D594BL,  /* 'CMYK' */
    icSigCmyData                        = 0x434D5920L,  /* 'CMY ' */
    icSig2colorData                     = 0x32434C52L,  /* '2CLR' */
    icSig3colorData                     = 0x33434C52L,  /* '3CLR' */
    icSig4colorData                     = 0x34434C52L,  /* '4CLR' */
    icSig5colorData                     = 0x35434C52L,  /* '5CLR' */
    icSig6colorData                     = 0x36434C52L,  /* '6CLR' */
    icSig7colorData                     = 0x37434C52L,  /* '7CLR' */
    icSig8colorData                     = 0x38434C52L,  /* '8CLR' */
    icSig9colorData                     = 0x39434C52L,  /* '9CLR' */
    icSig10colorData                    = 0x41434C52L,  /* 'ACLR' */
    icSig11colorData                    = 0x42434C52L,  /* 'BCLR' */
    icSig12colorData                    = 0x43434C52L,  /* 'CCLR' */
    icSig13colorData                    = 0x44434C52L,  /* 'DCLR' */
    icSig14colorData                    = 0x45434C52L,  /* 'ECLR' */
    icSig15colorData                    = 0x46434C52L,  /* 'FCLR' */
    icMaxEnumData                       = 0xFFFFFFFFL
} icColorSpaceSignature;

/* profileClass enumerations */
typedef enum {
    icSigInputClass                     = 0x73636E72L,  /* 'scnr' */
    icSigDisplayClass                   = 0x6D6E7472L,  /* 'mntr' */
    icSigOutputClass                    = 0x70727472L,  /* 'prtr' */
    icSigLinkClass                      = 0x6C696E6BL,  /* 'link' */
    icSigAbstractClass                  = 0x61627374L,  /* 'abst' */
    icSigColorSpaceClass                = 0x73706163L,  /* 'spac' */
    icSigNamedColorClass                = 0x6e6d636cL,  /* 'nmcl' */
    icMaxEnumClass                      = 0xFFFFFFFFL
} icProfileClassSignature;

/* Platform Signatures */
typedef enum {
    icSigMacintosh                      = 0x4150504CL,  /* 'APPL' */
    icSigMicrosoft                      = 0x4D534654L,  /* 'MSFT' */
    icSigSolaris                        = 0x53554E57L,  /* 'SUNW' */
    icSigSGI                            = 0x53474920L,  /* 'SGI ' */
    icSigTaligent                       = 0x54474E54L,  /* 'TGNT' */
    icMaxEnumPlatform                   = 0xFFFFFFFFL
} icPlatformSignature;

/*------------------------------------------------------------------------*/
/*
 * Other enums
 */

/* Measurement Flare, used in the measurmentType tag */
typedef enum {
    icFlare0                            = 0x00000000L,  /* 0% flare */
    icFlare100                          = 0x00000001L,  /* 100% flare */
    icMaxFlare                          = 0xFFFFFFFFL
} icMeasurementFlare;

/* Measurement Geometry, used in the measurmentType tag */
typedef enum {
    icGeometryUnknown                   = 0x00000000L,  /* Unknown */
    icGeometry045or450                  = 0x00000001L,  /* 0/45, 45/0 */
    icGeometry0dord0                    = 0x00000002L,  /* 0/d or d/0 */
    icMaxGeometry                       = 0xFFFFFFFFL
} icMeasurementGeometry;

/* Rendering Intents, used in the profile header */
typedef enum {
    icPerceptual                        = 0,
    icRelativeColorimetric              = 1,
    icSaturation                        = 2,
    icAbsoluteColorimetric              = 3,
    icMaxEnumIntent                     = 0xFFFFFFFFL
} icRenderingIntent;

/* Different Spot Shapes currently defined, used for screeningType */
typedef enum {
    icSpotShapeUnknown                  = 0,
    icSpotShapePrinterDefault           = 1,
    icSpotShapeRound                    = 2,
    icSpotShapeDiamond                  = 3,
    icSpotShapeEllipse                  = 4,
    icSpotShapeLine                     = 5,
    icSpotShapeSquare                   = 6,
    icSpotShapeCross                    = 7,
    icMaxEnumSpot                       = 0xFFFFFFFFL
} icSpotShape;

/* Standard Observer, used in the measurmentType tag */
typedef enum {
    icStdObsUnknown                     = 0x00000000L,  /* Unknown */
    icStdObs1931TwoDegrees              = 0x00000001L,  /* 2 deg */
    icStdObs1964TenDegrees              = 0x00000002L,  /* 10 deg */
    icMaxStdObs                         = 0xFFFFFFFFL
} icStandardObserver;

/* Pre-defined illuminants, used in measurement and viewing conditions type */
typedef enum {
    icIlluminantUnknown                 = 0x00000000L,
    icIlluminantD50                     = 0x00000001L,
    icIlluminantD65                     = 0x00000002L,
    icIlluminantD93                     = 0x00000003L,
    icIlluminantF2                      = 0x00000004L,
    icIlluminantD55                     = 0x00000005L,
    icIlluminantA                       = 0x00000006L,
    icIlluminantEquiPowerE              = 0x00000007L,
    icIlluminantF8                      = 0x00000008L,
    icMaxEnumIluminant                  = 0xFFFFFFFFL
} icIlluminant;


/*------------------------------------------------------------------------*/
/*
 * Arrays of numbers
 */

/* Int8 Array */
typedef struct {
    icInt8Number        data[icAny];    /* Variable array of values */
} icInt8Array;

/* UInt8 Array */
typedef struct {
    icUInt8Number       data[icAny];    /* Variable array of values */
} icUInt8Array;

/* uInt16 Array */
typedef struct {
    icUInt16Number      data[icAny];    /* Variable array of values */
} icUInt16Array;

/* Int16 Array */
typedef struct {
    icInt16Number       data[icAny];    /* Variable array of values */
} icInt16Array;

/* uInt32 Array */
typedef struct {
    icUInt32Number      data[icAny];    /* Variable array of values */
} icUInt32Array;

/* Int32 Array */
typedef struct {
    icInt32Number       data[icAny];    /* Variable array of values */
} icInt32Array;

/* UInt64 Array */
typedef struct {
    icUInt64Number      data[icAny];    /* Variable array of values */
} icUInt64Array;

/* Int64 Array */
typedef struct {
    icInt64Number       data[icAny];    /* Variable array of values */
} icInt64Array;

/* u16Fixed16 Array */
typedef struct {
    icU16Fixed16Number  data[icAny];    /* Variable array of values */
} icU16Fixed16Array;

/* s15Fixed16 Array */
typedef struct {
    icS15Fixed16Number  data[icAny];    /* Variable array of values */
} icS15Fixed16Array;

/* The base date time number */
typedef struct {
    icUInt16Number      year;
    icUInt16Number      month;
    icUInt16Number      day;
    icUInt16Number      hours;
    icUInt16Number      minutes;
    icUInt16Number      seconds;
} icDateTimeNumber;

/* XYZ Number  */
typedef struct {
    icS15Fixed16Number  X;
    icS15Fixed16Number  Y;
    icS15Fixed16Number  Z;
} icXYZNumber;

/* XYZ Array */
typedef struct {
    icXYZNumber         data[icAny];    /* Variable array of XYZ numbers */
} icXYZArray;

/* Curve */
typedef struct {
    icUInt32Number      count;          /* Number of entries */
    icUInt16Number      data[icAny];    /* The actual table data, real
                                         * number is determined by count
                                         * Interpretation depends on how
                                         * data is used with a given tag
                                         */
} icCurve;

/* Data */
typedef struct {
    icUInt32Number      dataFlag;       /* 0 = ascii, 1 = binary */
    icInt8Number        data[icAny];    /* Data, size from tag */
} icData;

/* lut16 */
typedef struct {
    icUInt8Number       inputChan;      /* Number of input channels */
    icUInt8Number       outputChan;     /* Number of output channels */
    icUInt8Number       clutPoints;     /* Number of grid points */
    icInt8Number        pad;            /* Padding for byte alignment */
    icS15Fixed16Number  e00;            /* e00 in the 3 * 3 */
    icS15Fixed16Number  e01;            /* e01 in the 3 * 3 */
    icS15Fixed16Number  e02;            /* e02 in the 3 * 3 */
    icS15Fixed16Number  e10;            /* e10 in the 3 * 3 */
    icS15Fixed16Number  e11;            /* e11 in the 3 * 3 */
    icS15Fixed16Number  e12;            /* e12 in the 3 * 3 */
    icS15Fixed16Number  e20;            /* e20 in the 3 * 3 */
    icS15Fixed16Number  e21;            /* e21 in the 3 * 3 */
    icS15Fixed16Number  e22;            /* e22 in the 3 * 3 */
    icUInt16Number      inputEnt;       /* Num of in-table entries */
    icUInt16Number      outputEnt;      /* Num of out-table entries */
    icUInt16Number      data[icAny];    /* Data follows see spec */
/*
 *  Data that follows is of this form
 *
 *  icUInt16Number      inputTable[inputChan][icAny];   * The in-table
 *  icUInt16Number      clutTable[icAny];               * The clut
 *  icUInt16Number      outputTable[outputChan][icAny]; * The out-table
 */
} icLut16;

/* lut8, input & output tables are always 256 bytes in length */
typedef struct {
    icUInt8Number       inputChan;      /* Num of input channels */
    icUInt8Number       outputChan;     /* Num of output channels */
    icUInt8Number       clutPoints;     /* Num of grid points */
    icInt8Number        pad;
    icS15Fixed16Number  e00;            /* e00 in the 3 * 3 */
    icS15Fixed16Number  e01;            /* e01 in the 3 * 3 */
    icS15Fixed16Number  e02;            /* e02 in the 3 * 3 */
    icS15Fixed16Number  e10;            /* e10 in the 3 * 3 */
    icS15Fixed16Number  e11;            /* e11 in the 3 * 3 */
    icS15Fixed16Number  e12;            /* e12 in the 3 * 3 */
    icS15Fixed16Number  e20;            /* e20 in the 3 * 3 */
    icS15Fixed16Number  e21;            /* e21 in the 3 * 3 */
    icS15Fixed16Number  e22;            /* e22 in the 3 * 3 */
    icUInt8Number       data[icAny];    /* Data follows see spec */
/*
 *  Data that follows is of this form
 *
 *  icUInt8Number       inputTable[inputChan][256];     * The in-table
 *  icUInt8Number       clutTable[icAny];               * The clut
 *  icUInt8Number       outputTable[outputChan][256];   * The out-table
 */
} icLut8;

/* Measurement Data */
typedef struct {
    icStandardObserver          stdObserver;    /* Standard observer */
    icXYZNumber                 backing;        /* XYZ for backing */
    icMeasurementGeometry       geometry;       /* Meas. geometry */
    icMeasurementFlare          flare;          /* Measurement flare */
    icIlluminant                illuminant;     /* Illuminant */
} icMeasurement;

/* Named color */

/*
 * icNamedColor2 takes the place of icNamedColor
 */
typedef struct {
    icUInt32Number      vendorFlag;     /* Bottom 16 bits for IC use */
    icUInt32Number      count;          /* Count of named colors */
    icUInt32Number      nDeviceCoords;  /* Num of device coordinates */
    icInt8Number        prefix[32];     /* Prefix for each color name */
    icInt8Number        suffix[32];     /* Suffix for each color name */
    icInt8Number        data[icAny];    /* Named color data follows */
/*
 *  Data that follows is of this form
 *
 * icInt8Number         root1[32];              * Root name for 1st color
 * icUInt16Number       pcsCoords1[icAny];      * PCS coords of 1st color
 * icUInt16Number       deviceCoords1[icAny];   * Dev coords of 1st color
 * icInt8Number         root2[32];              * Root name for 2nd color
 * icUInt16Number       pcsCoords2[icAny];      * PCS coords of 2nd color
 * icUInt16Number       deviceCoords2[icAny];   * Dev coords of 2nd color
 *                      :
 *                      :
 * Repeat for name and PCS and device color coordinates up to (count-1)
 *
 * NOTES:
 * PCS and device space can be determined from the header.
 *
 * PCS coordinates are icUInt16 numbers and are described in Annex A of
 * the ICC spec. Only 16 bit L*a*b* and XYZ are allowed. The number of
 * coordinates is consistent with the headers PCS.
 *
 * Device coordinates are icUInt16 numbers where 0x0000 represents
 * the minimum value and 0xFFFF represents the maximum value.
 * If the nDeviceCoords value is 0 this field is not given.
 */
} icNamedColor2;

/* Profile sequence structure */
typedef struct {
    icSignature                 deviceMfg;      /* Dev Manufacturer */
    icSignature                 deviceModel;    /* Dev Model */
    icUInt64Number              attributes;     /* Dev attributes */
    icTechnologySignature       technology;     /* Technology sig */
    icInt8Number                data[icAny];    /* Desc text follows */
/*
 *  Data that follows is of this form, this is an icInt8Number
 *  to avoid problems with a compiler generating  bad code as
 *  these arrays are variable in length.
 *
 * icTextDescription            deviceMfgDesc;  * Manufacturer text
 * icTextDescription            modelDesc;      * Model text
 */
} icDescStruct;

/* Profile sequence description */
typedef struct {
    icUInt32Number      count;          /* Number of descriptions */
    icUInt8Number       data[icAny];    /* Array of desc structs */
} icProfileSequenceDesc;

/* textDescription */
typedef struct {
    icUInt32Number      count;          /* Description length */
    icInt8Number        data[icAny];    /* Descriptions follow */
/*
 *  Data that follows is of this form
 *
 * icInt8Number         desc[count]     * NULL terminated ascii string
 * icUInt32Number       ucLangCode;     * UniCode language code
 * icUInt32Number       ucCount;        * UniCode description length
 * icInt16Number        ucDesc[ucCount];* The UniCode description
 * icUInt16Number       scCode;         * ScriptCode code
 * icUInt8Number        scCount;        * ScriptCode count
 * icInt8Number         scDesc[67];     * ScriptCode Description
 */
} icTextDescription;

/* Screening Data */
typedef struct {
    icS15Fixed16Number  frequency;      /* Frequency */
    icS15Fixed16Number  angle;          /* Screen angle */
    icSpotShape         spotShape;      /* Spot Shape encodings below */
} icScreeningData;

typedef struct {
    icUInt32Number      screeningFlag;  /* Screening flag */
    icUInt32Number      channels;       /* Number of channels */
    icScreeningData     data[icAny];    /* Array of screening data */
} icScreening;

/* Text Data */
typedef struct {
    icInt8Number        data[icAny];    /* Variable array of chars */
} icText;

/* Structure describing either a UCR or BG curve */
typedef struct {
    icUInt32Number      count;          /* Curve length */
    icUInt16Number      curve[icAny];   /* The array of curve values */
} icUcrBgCurve;

/* Under color removal, black generation */
typedef struct {
    icInt8Number        data[icAny];            /* The Ucr BG data */
/*
 *  Data that follows is of this form, this is a icInt8Number
 *  to avoid problems with a compiler generating  bad code as
 *  these arrays are variable in length.
 *
 * icUcrBgCurve         ucr;            * Ucr curve
 * icUcrBgCurve         bg;             * Bg curve
 * icInt8Number         string;         * UcrBg description
 */
} icUcrBg;

/* viewingConditionsType */
typedef struct {
    icXYZNumber         illuminant;     /* In candelas per sq. meter */
    icXYZNumber         surround;       /* In candelas per sq. meter */
    icIlluminant        stdIluminant;   /* See icIlluminant defines */
} icViewingCondition;

/* CrdInfo type */
typedef struct {
    icUInt32Number      count;          /* Char count includes NULL */
    icInt8Number        desc[icAny];    /* Null terminated string */
} icCrdInfo;

/*------------------------------------------------------------------------*/
/*
 * Tag Type definitions
 */

/*
 * Many of the structures contain variable length arrays. This
 * is represented by the use of the convention.
 *
 *      type    data[icAny];
 */

/* The base part of each tag */
typedef struct {
    icTagTypeSignature  sig;            /* Signature */
    icInt8Number        reserved[4];    /* Reserved, set to 0 */
} icTagBase;

/* curveType */
typedef struct {
    icTagBase           base;           /* Signature, "curv" */
    icCurve             curve;          /* The curve data */
} icCurveType;

/* dataType */
typedef struct {
    icTagBase           base;           /* Signature, "data" */
    icData              data;           /* The data structure */
} icDataType;

/* dateTimeType */
typedef struct {
    icTagBase           base;           /* Signature, "dtim" */
    icDateTimeNumber    date;           /* The date */
} icDateTimeType;

/* lut16Type */
typedef struct {
    icTagBase           base;           /* Signature, "mft2" */
    icLut16             lut;            /* Lut16 data */
} icLut16Type;

/* lut8Type, input & output tables are always 256 bytes in length */
typedef struct {
    icTagBase           base;           /* Signature, "mft1" */
    icLut8              lut;            /* Lut8 data */
} icLut8Type;

/* Measurement Type */
typedef struct {
    icTagBase           base;           /* Signature, "meas" */
    icMeasurement       measurement;    /* Measurement data */
} icMeasurementType;

/* Named color type */
/* icNamedColor2Type, replaces icNamedColorType */
typedef struct {
    icTagBase           base;           /* Signature, "ncl2" */
    icNamedColor2       ncolor;         /* Named color data */
} icNamedColor2Type;

/* Profile sequence description type */
typedef struct {
    icTagBase                   base;   /* Signature, "pseq" */
    icProfileSequenceDesc       desc;   /* The seq description */
} icProfileSequenceDescType;

/* textDescriptionType */
typedef struct {
    icTagBase                   base;   /* Signature, "desc" */
    icTextDescription           desc;   /* The description */
} icTextDescriptionType;

/* s15Fixed16Type */
typedef struct {
    icTagBase           base;           /* Signature, "sf32" */
    icS15Fixed16Array   data;           /* Array of values */
} icS15Fixed16ArrayType;

typedef struct {
    icTagBase           base;           /* Signature, "scrn" */
    icScreening         screen;         /* Screening structure */
} icScreeningType;

/* sigType */
typedef struct {
    icTagBase           base;           /* Signature, "sig" */
    icSignature         signature;      /* The signature data */
} icSignatureType;

/* textType */
typedef struct {
    icTagBase           base;           /* Signature, "text" */
    icText              data;           /* Variable array of chars */
} icTextType;

/* u16Fixed16Type */
typedef struct {
    icTagBase           base;           /* Signature, "uf32" */
    icU16Fixed16Array   data;           /* Variable array of values */
} icU16Fixed16ArrayType;

/* Under color removal, black generation type */
typedef struct {
    icTagBase           base;           /* Signature, "bfd " */
    icUcrBg             data;           /* ucrBg structure */
} icUcrBgType;

/* uInt16Type */
typedef struct {
    icTagBase           base;           /* Signature, "ui16" */
    icUInt16Array       data;           /* Variable array of values */
} icUInt16ArrayType;

/* uInt32Type */
typedef struct {
    icTagBase           base;           /* Signature, "ui32" */
    icUInt32Array       data;           /* Variable array of values */
} icUInt32ArrayType;

/* uInt64Type */
typedef struct {
    icTagBase           base;           /* Signature, "ui64" */
    icUInt64Array       data;           /* Variable array of values */
} icUInt64ArrayType;

/* uInt8Type */
typedef struct {
    icTagBase           base;           /* Signature, "ui08" */
    icUInt8Array        data;           /* Variable array of values */
} icUInt8ArrayType;

/* viewingConditionsType */
typedef struct {
    icTagBase           base;           /* Signature, "view" */
    icViewingCondition  view;           /* Viewing conditions */
} icViewingConditionType;

/* XYZ Type */
typedef struct {
    icTagBase           base;           /* Signature, "XYZ" */
    icXYZArray          data;           /* Variable array of XYZ nums */
} icXYZType;

/* CRDInfoType where [0] is the CRD product name count and string and
 * [1] -[5] are the rendering intents 0-4 counts and strings
 */
typedef struct {
    icTagBase           base;           /* Signature, "crdi" */
    icCrdInfo           info;           /* 5 sets of counts & strings */
}icCrdInfoType;
     /*   icCrdInfo       productName;     PS product count/string */
     /*   icCrdInfo       CRDName0;        CRD name for intent 0 */
     /*   icCrdInfo       CRDName1;        CRD name for intent 1 */
     /*   icCrdInfo       CRDName2;        CRD name for intent 2 */
     /*   icCrdInfo       CRDName3;        CRD name for intent 3 */

/*------------------------------------------------------------------------*/

/*
 * Lists of tags, tags, profile header and profile structure
 */

/* A tag */
typedef struct {
    icTagSignature      sig;            /* The tag signature */
    icUInt32Number      offset;         /* Start of tag relative to
                                         * start of header, Spec
                                         * Clause 5 */
    icUInt32Number      size;           /* Size in bytes */
} icTag;

/* A Structure that may be used independently for a list of tags */
typedef struct {
    icUInt32Number      count;          /* Num tags in the profile */
    icTag               tags[icAny];    /* Variable array of tags */
} icTagList;

/* The Profile header */
typedef struct {
    icUInt32Number              size;           /* Prof size in bytes */
    icSignature                 cmmId;          /* CMM for profile */
    icUInt32Number              version;        /* Format version */
    icProfileClassSignature     deviceClass;    /* Type of profile */
    icColorSpaceSignature       colorSpace;     /* Clr space of data */
    icColorSpaceSignature       pcs;            /* PCS, XYZ or Lab */
    icDateTimeNumber            date;           /* Creation Date */
    icSignature                 magic;          /* icMagicNumber */
    icPlatformSignature         platform;       /* Primary Platform */
    icUInt32Number              flags;          /* Various bits */
    icSignature                 manufacturer;   /* Dev manufacturer */
    icUInt32Number              model;          /* Dev model number */
    icUInt64Number              attributes;     /* Device attributes */
    icUInt32Number              renderingIntent;/* Rendering intent */
    icXYZNumber                 illuminant;     /* Profile illuminant */
    icSignature                 creator;        /* Profile creator */
    icInt8Number                reserved[44];   /* Reserved */
} icHeader;

/*
 * A profile,
 * we can't use icTagList here because its not at the end of the structure
 */
typedef struct {
    icHeader            header;         /* The header */
    icUInt32Number      count;          /* Num tags in the profile */
    icInt8Number        data[icAny];    /* The tagTable and tagData */
/*
 * Data that follows is of the form
 *
 * icTag        tagTable[icAny];        * The tag table
 * icInt8Number tagData[icAny];         * The tag data
 */
} icProfile;

/*------------------------------------------------------------------------*/
/* Obsolete entries */

/* icNamedColor was replaced with icNamedColor2 */
typedef struct {
    icUInt32Number      vendorFlag;     /* Bottom 16 bits for IC use */
    icUInt32Number      count;          /* Count of named colors */
    icInt8Number        data[icAny];    /* Named color data follows */
/*
 *  Data that follows is of this form
 *
 * icInt8Number         prefix[icAny];  * Prefix
 * icInt8Number         suffix[icAny];  * Suffix
 * icInt8Number         root1[icAny];   * Root name
 * icInt8Number         coords1[icAny]; * Color coordinates
 * icInt8Number         root2[icAny];   * Root name
 * icInt8Number         coords2[icAny]; * Color coordinates
 *                      :
 *                      :
 * Repeat for root name and color coordinates up to (count-1)
 */
} icNamedColor;

/* icNamedColorType was replaced by icNamedColor2Type */
typedef struct {
    icTagBase           base;           /* Signature, "ncol" */
    icNamedColor        ncolor;         /* Named color data */
} icNamedColorType;

#endif /* ICC_H */
