/*
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

/******************************************************************************
 * In order to make life a little bit easier when using the GIF file format,
 * this library was written, and which does all the dirty work...
 *
 *                                        Written by Gershon Elber,  Jun. 1989
 *                                        Hacks by Eric S. Raymond,  Sep. 1992
 ******************************************************************************
 * History:
 * 14 Jun 89 - Version 1.0 by Gershon Elber.
 *  3 Sep 90 - Version 1.1 by Gershon Elber (Support for Gif89, Unique names)
 * 15 Sep 90 - Version 2.0 by Eric S. Raymond (Changes to suoport GIF slurp)
 * 26 Jun 96 - Version 3.0 by Eric S. Raymond (Full GIF89 support)
 * 17 Dec 98 - Version 4.0 by Toshio Kuratomi (Fix extension writing code)
 *****************************************************************************/

/* all encoding functionality stripped */

#ifndef _GIF_LIB_H_
#define _GIF_LIB_H_ 1

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define GIF_LIB_VERSION " Version 4.1, "

#define GIF_ERROR   0
#define GIF_OK      1

#ifndef TRUE
#define TRUE        1
#endif /* TRUE */
#ifndef FALSE
#define FALSE       0
#endif /* FALSE */

#ifndef NULL
#define NULL        0
#endif /* NULL */

#define GIF_STAMP "GIFVER"          /* First chars in file - GIF stamp.  */
#define GIF_STAMP_LEN sizeof(GIF_STAMP) - 1
#define GIF_VERSION_POS 3           /* Version first character in stamp. */
#define GIF87_STAMP "GIF87a"        /* First chars in file - GIF stamp.  */
#define GIF89_STAMP "GIF89a"        /* First chars in file - GIF stamp.  */

#define GIF_FILE_BUFFER_SIZE 16384  /* Files uses bigger buffers than usual. */

typedef int GifBooleanType;
typedef unsigned char GifPixelType;
typedef unsigned char *GifRowType;
typedef unsigned char GifByteType;

#define GIF_MESSAGE(Msg) fprintf(stderr, "\n%s: %s\n", PROGRAM_NAME, Msg)
#define GIF_EXIT(Msg)    { GIF_MESSAGE(Msg); exit(-3); }

#ifdef SYSV
#define VoidPtr char *
#else
#define VoidPtr void *
#endif /* SYSV */

typedef struct GifColorType {
    GifByteType Red, Green, Blue;
} GifColorType;

typedef struct ColorMapObject {
    int ColorCount;
    int BitsPerPixel;
    GifColorType *Colors;    /* on malloc(3) heap */
} ColorMapObject;

typedef struct GifImageDesc {
    int Left, Top, Width, Height,   /* Current image dimensions. */
      Interlace;                    /* Sequential/Interlaced lines. */
    ColorMapObject *ColorMap;       /* The local color map */
} GifImageDesc;

typedef struct GifFileType {
    int SWidth, SHeight,        /* Screen dimensions. */
      SColorResolution,         /* How many colors can we generate? */
      SBackGroundColor;         /* I hope you understand this one... */
    ColorMapObject *SColorMap;  /* NULL if not exists. */
    int ImageCount;             /* Number of current image */
    GifImageDesc Image;         /* Block describing current image */
    struct SavedImage *SavedImages; /* Use this to accumulate file state */
    VoidPtr UserData;           /* hook to attach user data (TVT) */
    VoidPtr Private;            /* Don't mess with this! */
} GifFileType;

typedef enum {
    UNDEFINED_RECORD_TYPE,
    SCREEN_DESC_RECORD_TYPE,
    IMAGE_DESC_RECORD_TYPE, /* Begin with ',' */
    EXTENSION_RECORD_TYPE,  /* Begin with '!' */
    TERMINATE_RECORD_TYPE   /* Begin with ';' */
} GifRecordType;

/* DumpScreen2Gif routine constants identify type of window/screen to dump.
 * Note all values below 1000 are reserved for the IBMPC different display
 * devices (it has many!) and are compatible with the numbering TC2.0
 * (Turbo C 2.0 compiler for IBM PC) gives to these devices.
 */
typedef enum {
    GIF_DUMP_SGI_WINDOW = 1000,
    GIF_DUMP_X_WINDOW = 1001
} GifScreenDumpType;

/* func type to read gif data from arbitrary sources (TVT) */
typedef int (*InputFunc) (GifFileType *, GifByteType *, int);

/* func type to write gif data ro arbitrary targets.
 * Returns count of bytes written. (MRB)
 */
typedef int (*OutputFunc) (GifFileType *, const GifByteType *, int);

/******************************************************************************
 *  GIF89 extension function codes
******************************************************************************/

#define COMMENT_EXT_FUNC_CODE     0xfe    /* comment */
#define GRAPHICS_EXT_FUNC_CODE    0xf9    /* graphics control */
#define PLAINTEXT_EXT_FUNC_CODE   0x01    /* plaintext */
#define APPLICATION_EXT_FUNC_CODE 0xff    /* application block */

/******************************************************************************
 * O.K., here are the routines one can access in order to decode GIF file:
 * (GIF_LIB file DGIF_LIB.C).
 *****************************************************************************/

GifFileType *DGifOpenFileName(const char *GifFileName);
GifFileType *DGifOpenFileHandle(int GifFileHandle);
GifFileType *DGifOpen(void *userPtr, InputFunc readFunc);    /* new one
                                                             * (TVT) */
int DGifSlurp(GifFileType * GifFile);
int DGifGetScreenDesc(GifFileType * GifFile);
int DGifGetRecordType(GifFileType * GifFile, GifRecordType * GifType);
int DGifGetImageDesc(GifFileType * GifFile);
int DGifGetLine(GifFileType * GifFile, GifPixelType * GifLine, int GifLineLen);
int DGifGetPixel(GifFileType * GifFile, GifPixelType GifPixel);
int DGifGetComment(GifFileType * GifFile, char *GifComment);
int DGifGetExtension(GifFileType * GifFile, int *GifExtCode,
                     GifByteType ** GifExtension);
int DGifGetExtensionNext(GifFileType * GifFile, GifByteType ** GifExtension);
int DGifGetCode(GifFileType * GifFile, int *GifCodeSize,
                GifByteType ** GifCodeBlock);
int DGifGetCodeNext(GifFileType * GifFile, GifByteType ** GifCodeBlock);
int DGifGetLZCodes(GifFileType * GifFile, int *GifCode);
int DGifCloseFile(GifFileType * GifFile);

#define D_GIF_ERR_OPEN_FAILED    101    /* And DGif possible errors. */
#define D_GIF_ERR_READ_FAILED    102
#define D_GIF_ERR_NOT_GIF_FILE   103
#define D_GIF_ERR_NO_SCRN_DSCR   104
#define D_GIF_ERR_NO_IMAG_DSCR   105
#define D_GIF_ERR_NO_COLOR_MAP   106
#define D_GIF_ERR_WRONG_RECORD   107
#define D_GIF_ERR_DATA_TOO_BIG   108
#define D_GIF_ERR_NOT_ENOUGH_MEM 109
#define D_GIF_ERR_CLOSE_FAILED   110
#define D_GIF_ERR_NOT_READABLE   111
#define D_GIF_ERR_IMAGE_DEFECT   112
#define D_GIF_ERR_EOF_TOO_SOON   113


/******************************************************************************
 * O.K., here are the routines from GIF_LIB file GIF_ERR.C.
******************************************************************************/
extern void PrintGifError(void);
extern int GifLastError(void);

/******************************************************************************
 * O.K., here are the routines from GIF_LIB file DEV2GIF.C.
******************************************************************************/
extern int DumpScreen2Gif(const char *FileName,
                          int ReqGraphDriver,
                          long ReqGraphMode1,
                          long ReqGraphMode2,
                          long ReqGraphMode3);

/*****************************************************************************
 *
 * Everything below this point is new after version 1.2, supporting `slurp
 * mode' for doing I/O in two big belts with all the image-bashing in core.
 *
 *****************************************************************************/

/******************************************************************************
 * Color Map handling from ALLOCGIF.C
 *****************************************************************************/

extern ColorMapObject *MakeMapObject(int ColorCount,
                                     const GifColorType * ColorMap);
extern void FreeMapObject(ColorMapObject * Object);
extern int BitSize(int n);

/******************************************************************************
 * Support for the in-core structures allocation (slurp mode).
 *****************************************************************************/

/* This is the in-core version of an extension record */
typedef struct {
    int ByteCount;
    char *Bytes;    /* on malloc(3) heap */
    int Function;   /* Holds the type of the Extension block. */
} ExtensionBlock;

/* This holds an image header, its unpacked raster bits, and extensions */
typedef struct SavedImage {
    GifImageDesc ImageDesc;
    unsigned char *RasterBits;  /* on malloc(3) heap */
    int Function;   /* DEPRECATED: Use ExtensionBlocks[x].Function instead */
    int ExtensionBlockCount;
    ExtensionBlock *ExtensionBlocks;    /* on malloc(3) heap */
} SavedImage;

extern void MakeExtension(SavedImage * New, int Function);
extern int AddExtensionBlock(SavedImage * New, int Len,
                             unsigned char ExtData[]);
extern void FreeExtension(SavedImage * Image);
extern SavedImage *MakeSavedImage(GifFileType * GifFile,
                                  const SavedImage * CopyFrom);
extern void FreeSavedImages(GifFileType * GifFile);


#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* _GIF_LIB_H */
