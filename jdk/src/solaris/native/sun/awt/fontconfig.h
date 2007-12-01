/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * $RCSId: xc/lib/fontconfig/fontconfig/fontconfig.h,v 1.30 2002/09/26 00:17:27 keithp Exp $
 *
 * Copyright © 2001 Keith Packard
 *
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation, and that the name of Keith Packard not be used in
 * advertising or publicity pertaining to distribution of the software without
 * specific, written prior permission.  Keith Packard makes no
 * representations about the suitability of this software for any purpose.  It
 * is provided "as is" without express or implied warranty.
 *
 * KEITH PACKARD DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
 * INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO
 * EVENT SHALL KEITH PACKARD BE LIABLE FOR ANY SPECIAL, INDIRECT OR
 * CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

#ifndef _FONTCONFIG_H_
#define _FONTCONFIG_H_

#include <stdarg.h>

typedef unsigned char   FcChar8;
typedef unsigned short  FcChar16;
typedef unsigned int    FcChar32;
typedef int             FcBool;

/*
 * Current Fontconfig version number.  This same number
 * must appear in the fontconfig configure.in file. Yes,
 * it'a a pain to synchronize version numbers like this.
 */

#define FC_MAJOR        2
#define FC_MINOR        2
#define FC_REVISION     0

#define FC_VERSION      ((FC_MAJOR * 10000) + (FC_MINOR * 100) + (FC_REVISION))

/*
 * Current font cache file format version
 * This is appended to the cache files so that multiple
 * versions of the library will peacefully coexist
 *
 * Change this value whenever the disk format for the cache file
 * changes in any non-compatible way.  Try to avoid such changes as
 * it means multiple copies of the font information.
 */

#define FC_CACHE_VERSION    "1"

#define FcTrue          1
#define FcFalse         0

#define FC_FAMILY           "family"            /* String */
#define FC_STYLE            "style"             /* String */
#define FC_SLANT            "slant"             /* Int */
#define FC_WEIGHT           "weight"            /* Int */
#define FC_SIZE             "size"              /* Double */
#define FC_ASPECT           "aspect"            /* Double */
#define FC_PIXEL_SIZE       "pixelsize"         /* Double */
#define FC_SPACING          "spacing"           /* Int */
#define FC_FOUNDRY          "foundry"           /* String */
#define FC_ANTIALIAS        "antialias"         /* Bool (depends) */
#define FC_HINTING          "hinting"           /* Bool (true) */
#define FC_VERTICAL_LAYOUT  "verticallayout"    /* Bool (false) */
#define FC_AUTOHINT         "autohint"          /* Bool (false) */
#define FC_GLOBAL_ADVANCE   "globaladvance"     /* Bool (true) */
#define FC_WIDTH            "width"             /* Int */
#define FC_FILE             "file"              /* String */
#define FC_INDEX            "index"             /* Int */
#define FC_FT_FACE          "ftface"            /* FT_Face */
#define FC_RASTERIZER       "rasterizer"        /* String */
#define FC_OUTLINE          "outline"           /* Bool */
#define FC_SCALABLE         "scalable"          /* Bool */
#define FC_SCALE            "scale"             /* double */
#define FC_DPI              "dpi"               /* double */
#define FC_RGBA             "rgba"              /* Int */
#define FC_MINSPACE         "minspace"          /* Bool use minimum line spacing */
#define FC_SOURCE           "source"            /* String (X11, freetype) */
#define FC_CHARSET          "charset"           /* CharSet */
#define FC_LANG             "lang"              /* String RFC 3066 langs */
#define FC_FONTVERSION      "fontversion"       /* Int from 'head' table */

#define FC_DIR_CACHE_FILE           "fonts.cache-"FC_CACHE_VERSION
#define FC_USER_CACHE_FILE          ".fonts.cache-"FC_CACHE_VERSION

/* Adjust outline rasterizer */
#define FC_CHAR_WIDTH       "charwidth" /* Int */
#define FC_CHAR_HEIGHT      "charheight"/* Int */
#define FC_MATRIX           "matrix"    /* FcMatrix */

#define FC_WEIGHT_THIN              0
#define FC_WEIGHT_EXTRALIGHT        40
#define FC_WEIGHT_ULTRALIGHT        FC_WEIGHT_EXTRALIGHT
#define FC_WEIGHT_LIGHT             50
#define FC_WEIGHT_REGULAR           80
#define FC_WEIGHT_NORMAL            FC_WEIGHT_REGULAR
#define FC_WEIGHT_MEDIUM            100
#define FC_WEIGHT_DEMIBOLD          180
#define FC_WEIGHT_SEMIBOLD          FC_WEIGHT_DEMIBOLD
#define FC_WEIGHT_BOLD              200
#define FC_WEIGHT_EXTRABOLD         205
#define FC_WEIGHT_ULTRABOLD         FC_WEIGHT_EXTRABOLD
#define FC_WEIGHT_BLACK             210
#define FC_WEIGHT_HEAVY             FC_WEIGHT_BLACK

#define FC_SLANT_ROMAN              0
#define FC_SLANT_ITALIC             100
#define FC_SLANT_OBLIQUE            110

#define FC_WIDTH_ULTRACONDENSED     50
#define FC_WIDTH_EXTRACONDENSED     63
#define FC_WIDTH_CONDENSED          75
#define FC_WIDTH_SEMICONDENSED      87
#define FC_WIDTH_NORMAL             100
#define FC_WIDTH_SEMIEXPANDED       113
#define FC_WIDTH_EXPANDED           125
#define FC_WIDTH_EXTRAEXPANDED      150
#define FC_WIDTH_ULTRAEXPANDED      200

#define FC_PROPORTIONAL             0
#define FC_MONO                     100
#define FC_CHARCELL                 110

/* sub-pixel order */
#define FC_RGBA_UNKNOWN     0
#define FC_RGBA_RGB         1
#define FC_RGBA_BGR         2
#define FC_RGBA_VRGB        3
#define FC_RGBA_VBGR        4
#define FC_RGBA_NONE        5

typedef enum _FcType {
    FcTypeVoid,
    FcTypeInteger,
    FcTypeDouble,
    FcTypeString,
    FcTypeBool,
    FcTypeMatrix,
    FcTypeCharSet,
    FcTypeFTFace,
    FcTypeLangSet
} FcType;

typedef struct _FcMatrix {
    double xx, xy, yx, yy;
} FcMatrix;

#define FcMatrixInit(m) ((m)->xx = (m)->yy = 1, \
                         (m)->xy = (m)->yx = 0)

/*
 * A data structure to represent the available glyphs in a font.
 * This is represented as a sparse boolean btree.
 */

typedef struct _FcCharSet FcCharSet;

typedef struct _FcObjectType {
    const char  *object;
    FcType      type;
} FcObjectType;

typedef struct _FcConstant {
    const FcChar8  *name;
    const char  *object;
    int         value;
} FcConstant;

typedef enum _FcResult {
    FcResultMatch, FcResultNoMatch, FcResultTypeMismatch, FcResultNoId
} FcResult;

typedef struct _FcPattern   FcPattern;

typedef struct _FcLangSet   FcLangSet;

typedef struct _FcValue {
    FcType      type;
    union {
        const FcChar8   *s;
        int             i;
        FcBool          b;
        double          d;
        const FcMatrix  *m;
        const FcCharSet *c;
        void            *f;
        const FcPattern *p;
        const FcLangSet *l;
    } u;
} FcValue;

typedef struct _FcFontSet {
    int         nfont;
    int         sfont;
    FcPattern   **fonts;
} FcFontSet;

typedef struct _FcObjectSet {
    int         nobject;
    int         sobject;
    const char  **objects;
} FcObjectSet;

typedef enum _FcMatchKind {
    FcMatchPattern, FcMatchFont
} FcMatchKind;

typedef enum _FcLangResult {
    FcLangEqual, FcLangDifferentCountry, FcLangDifferentLang
} FcLangResult;

typedef enum _FcSetName {
    FcSetSystem = 0,
    FcSetApplication = 1
} FcSetName;

typedef struct _FcAtomic FcAtomic;

#if defined(__cplusplus) || defined(c_plusplus) /* for C++ V2.0 */
#define _FCFUNCPROTOBEGIN extern "C" {  /* do not leave open across includes */
#define _FCFUNCPROTOEND }
#else
#define _FCFUNCPROTOBEGIN
#define _FCFUNCPROTOEND
#endif

typedef enum { FcEndianBig, FcEndianLittle } FcEndian;

typedef struct _FcConfig    FcConfig;

typedef struct _FcGlobalCache   FcFileCache;

typedef struct _FcBlanks    FcBlanks;

typedef struct _FcStrList   FcStrList;

typedef struct _FcStrSet    FcStrSet;

_FCFUNCPROTOBEGIN

FcBool
FcDirCacheValid (const FcChar8 *cache_file);

/* fcblanks.c */
FcBlanks *
FcBlanksCreate (void);

void
FcBlanksDestroy (FcBlanks *b);

FcBool
FcBlanksAdd (FcBlanks *b, FcChar32 ucs4);

FcBool
FcBlanksIsMember (FcBlanks *b, FcChar32 ucs4);

/* fccfg.c */
FcChar8 *
FcConfigHome (void);

FcBool
FcConfigEnableHome (FcBool enable);

FcChar8 *
FcConfigFilename (const FcChar8 *url);

FcConfig *
FcConfigCreate (void);

void
FcConfigDestroy (FcConfig *config);

FcBool
FcConfigSetCurrent (FcConfig *config);

FcConfig *
FcConfigGetCurrent (void);

FcBool
FcConfigUptoDate (FcConfig *config);

FcBool
FcConfigBuildFonts (FcConfig *config);

FcStrList *
FcConfigGetFontDirs (FcConfig   *config);

FcStrList *
FcConfigGetConfigDirs (FcConfig   *config);

FcStrList *
FcConfigGetConfigFiles (FcConfig    *config);

FcChar8 *
FcConfigGetCache (FcConfig  *config);

FcBlanks *
FcConfigGetBlanks (FcConfig *config);

int
FcConfigGetRescanInverval (FcConfig *config);

FcBool
FcConfigSetRescanInverval (FcConfig *config, int rescanInterval);

FcFontSet *
FcConfigGetFonts (FcConfig      *config,
                  FcSetName     set);

FcBool
FcConfigAppFontAddFile (FcConfig    *config,
                        const FcChar8  *file);

FcBool
FcConfigAppFontAddDir (FcConfig     *config,
                       const FcChar8   *dir);

void
FcConfigAppFontClear (FcConfig      *config);

FcBool
FcConfigSubstituteWithPat (FcConfig     *config,
                           FcPattern    *p,
                           FcPattern    *p_pat,
                           FcMatchKind  kind);

FcBool
FcConfigSubstitute (FcConfig    *config,
                    FcPattern   *p,
                    FcMatchKind kind);

/* fccharset.c */
FcCharSet *
FcCharSetCreate (void);

void
FcCharSetDestroy (FcCharSet *fcs);

FcBool
FcCharSetAddChar (FcCharSet *fcs, FcChar32 ucs4);

FcCharSet *
FcCharSetCopy (FcCharSet *src);

FcBool
FcCharSetEqual (const FcCharSet *a, const FcCharSet *b);

FcCharSet *
FcCharSetIntersect (const FcCharSet *a, const FcCharSet *b);

FcCharSet *
FcCharSetUnion (const FcCharSet *a, const FcCharSet *b);

FcCharSet *
FcCharSetSubtract (const FcCharSet *a, const FcCharSet *b);

FcBool
FcCharSetHasChar (const FcCharSet *fcs, FcChar32 ucs4);

FcChar32
FcCharSetCount (const FcCharSet *a);

FcChar32
FcCharSetIntersectCount (const FcCharSet *a, const FcCharSet *b);

FcChar32
FcCharSetSubtractCount (const FcCharSet *a, const FcCharSet *b);

FcBool
FcCharSetIsSubset (const FcCharSet *a, const FcCharSet *b);

#define FC_CHARSET_MAP_SIZE (256/32)
#define FC_CHARSET_DONE ((FcChar32) -1)

FcChar32
FcCharSetFirstPage (const FcCharSet *a,
                    FcChar32        map[FC_CHARSET_MAP_SIZE],
                    FcChar32        *next);

FcChar32
FcCharSetNextPage (const FcCharSet  *a,
                   FcChar32         map[FC_CHARSET_MAP_SIZE],
                   FcChar32         *next);


/* fcdbg.c */
void
FcValuePrint (const FcValue v);

void
FcPatternPrint (const FcPattern *p);

void
FcFontSetPrint (const FcFontSet *s);

/* fcdefault.c */
void
FcDefaultSubstitute (FcPattern *pattern);

/* fcdir.c */
FcBool
FcFileScan (FcFontSet       *set,
            FcStrSet        *dirs,
            FcFileCache     *cache,
            FcBlanks        *blanks,
            const FcChar8   *file,
            FcBool          force);

FcBool
FcDirScan (FcFontSet        *set,
           FcStrSet         *dirs,
           FcFileCache      *cache,
           FcBlanks         *blanks,
           const FcChar8    *dir,
           FcBool           force);

FcBool
FcDirSave (FcFontSet *set, FcStrSet *dirs, const FcChar8 *dir);

/* fcfreetype.c */
FcPattern *
FcFreeTypeQuery (const FcChar8 *file, int id, FcBlanks *blanks, int *count);

/* fcfs.c */

FcFontSet *
FcFontSetCreate (void);

void
FcFontSetDestroy (FcFontSet *s);

FcBool
FcFontSetAdd (FcFontSet *s, FcPattern *font);

/* fcinit.c */
FcConfig *
FcInitLoadConfig (void);

FcConfig *
FcInitLoadConfigAndFonts (void);

FcBool
FcInit (void);

int
FcGetVersion (void);

FcBool
FcInitReinitialize (void);

FcBool
FcInitBringUptoDate (void);

/* fclang.c */
FcLangSet *
FcLangSetCreate (void);

void
FcLangSetDestroy (FcLangSet *ls);

FcLangSet *
FcLangSetCopy (const FcLangSet *ls);

FcBool
FcLangSetAdd (FcLangSet *ls, const FcChar8 *lang);

FcLangResult
FcLangSetHasLang (const FcLangSet *ls, const FcChar8 *lang);

FcLangResult
FcLangSetCompare (const FcLangSet *lsa, const FcLangSet *lsb);

FcBool
FcLangSetContains (const FcLangSet *lsa, const FcLangSet *lsb);

FcBool
FcLangSetEqual (const FcLangSet *lsa, const FcLangSet *lsb);

FcChar32
FcLangSetHash (const FcLangSet *ls);

/* fclist.c */
FcObjectSet *
FcObjectSetCreate (void);

FcBool
FcObjectSetAdd (FcObjectSet *os, const char *object);

void
FcObjectSetDestroy (FcObjectSet *os);

FcObjectSet *
FcObjectSetVaBuild (const char *first, va_list va);

FcObjectSet *
FcObjectSetBuild (const char *first, ...);

FcFontSet *
FcFontSetList (FcConfig     *config,
               FcFontSet    **sets,
               int          nsets,
               FcPattern    *p,
               FcObjectSet  *os);

FcFontSet *
FcFontList (FcConfig    *config,
            FcPattern   *p,
            FcObjectSet *os);

/* fcatomic.c */

FcAtomic *
FcAtomicCreate (const FcChar8   *file);

FcBool
FcAtomicLock (FcAtomic *atomic);

FcChar8 *
FcAtomicNewFile (FcAtomic *atomic);

FcChar8 *
FcAtomicOrigFile (FcAtomic *atomic);

FcBool
FcAtomicReplaceOrig (FcAtomic *atomic);

void
FcAtomicDeleteNew (FcAtomic *atomic);

void
FcAtomicUnlock (FcAtomic *atomic);

void
FcAtomicDestroy (FcAtomic *atomic);

/* fcmatch.c */
FcPattern *
FcFontSetMatch (FcConfig    *config,
                FcFontSet   **sets,
                int         nsets,
                FcPattern   *p,
                FcResult    *result);

FcPattern *
FcFontMatch (FcConfig   *config,
             FcPattern  *p,
             FcResult   *result);

FcPattern *
FcFontRenderPrepare (FcConfig       *config,
                     FcPattern      *pat,
                     FcPattern      *font);

FcFontSet *
FcFontSetSort (FcConfig     *config,
               FcFontSet    **sets,
               int          nsets,
               FcPattern    *p,
               FcBool       trim,
               FcCharSet    **csp,
               FcResult     *result);

FcFontSet *
FcFontSort (FcConfig     *config,
            FcPattern    *p,
            FcBool       trim,
            FcCharSet    **csp,
            FcResult     *result);

void
FcFontSetSortDestroy (FcFontSet *fs);

/* fcmatrix.c */
FcMatrix *
FcMatrixCopy (const FcMatrix *mat);

FcBool
FcMatrixEqual (const FcMatrix *mat1, const FcMatrix *mat2);

void
FcMatrixMultiply (FcMatrix *result, const FcMatrix *a, const FcMatrix *b);

void
FcMatrixRotate (FcMatrix *m, double c, double s);

void
FcMatrixScale (FcMatrix *m, double sx, double sy);

void
FcMatrixShear (FcMatrix *m, double sh, double sv);

/* fcname.c */

FcBool
FcNameRegisterObjectTypes (const FcObjectType *types, int ntype);

FcBool
FcNameUnregisterObjectTypes (const FcObjectType *types, int ntype);

const FcObjectType *
FcNameGetObjectType (const char *object);

FcBool
FcNameRegisterConstants (const FcConstant *consts, int nconsts);

FcBool
FcNameUnregisterConstants (const FcConstant *consts, int nconsts);

const FcConstant *
FcNameGetConstant (FcChar8 *string);

FcBool
FcNameConstant (FcChar8 *string, int *result);

FcPattern *
FcNameParse (const FcChar8 *name);

FcChar8 *
FcNameUnparse (FcPattern *pat);

/* fcpat.c */
FcPattern *
FcPatternCreate (void);

FcPattern *
FcPatternDuplicate (const FcPattern *p);

void
FcPatternReference (FcPattern *p);

void
FcValueDestroy (FcValue v);

FcBool
FcValueEqual (FcValue va, FcValue vb);

FcValue
FcValueSave (FcValue v);

void
FcPatternDestroy (FcPattern *p);

FcBool
FcPatternEqual (const FcPattern *pa, const FcPattern *pb);

FcBool
FcPatternEqualSubset (const FcPattern *pa, const FcPattern *pb, const FcObjectSet *os);

FcChar32
FcPatternHash (const FcPattern *p);

FcBool
FcPatternAdd (FcPattern *p, const char *object, FcValue value, FcBool append);

FcBool
FcPatternAddWeak (FcPattern *p, const char *object, FcValue value, FcBool append);

FcResult
FcPatternGet (const FcPattern *p, const char *object, int id, FcValue *v);

FcBool
FcPatternDel (FcPattern *p, const char *object);

FcBool
FcPatternAddInteger (FcPattern *p, const char *object, int i);

FcBool
FcPatternAddDouble (FcPattern *p, const char *object, double d);

FcBool
FcPatternAddString (FcPattern *p, const char *object, const FcChar8 *s);

FcBool
FcPatternAddMatrix (FcPattern *p, const char *object, const FcMatrix *s);

FcBool
FcPatternAddCharSet (FcPattern *p, const char *object, const FcCharSet *c);

FcBool
FcPatternAddBool (FcPattern *p, const char *object, FcBool b);

FcBool
FcPatternAddLangSet (FcPattern *p, const char *object, const FcLangSet *ls);

FcResult
FcPatternGetInteger (const FcPattern *p, const char *object, int n, int *i);

FcResult
FcPatternGetDouble (const FcPattern *p, const char *object, int n, double *d);

FcResult
FcPatternGetString (const FcPattern *p, const char *object, int n, FcChar8 ** s);

FcResult
FcPatternGetMatrix (const FcPattern *p, const char *object, int n, FcMatrix **s);

FcResult
FcPatternGetCharSet (const FcPattern *p, const char *object, int n, FcCharSet **c);

FcResult
FcPatternGetBool (const FcPattern *p, const char *object, int n, FcBool *b);

FcResult
FcPatternGetLangSet (const FcPattern *p, const char *object, int n, FcLangSet **ls);

FcPattern *
FcPatternVaBuild (FcPattern *orig, va_list va);

FcPattern *
FcPatternBuild (FcPattern *orig, ...);

/* fcstr.c */

FcChar8 *
FcStrCopy (const FcChar8 *s);

FcChar8 *
FcStrCopyFilename (const FcChar8 *s);

#define FcIsUpper(c)    (('A' <= (c) && (c) <= 'Z'))
#define FcIsLower(c)    (('a' <= (c) && (c) <= 'z'))
#define FcToLower(c)    (FcIsUpper(c) ? (c) - 'A' + 'a' : (c))

int
FcStrCmpIgnoreCase (const FcChar8 *s1, const FcChar8 *s2);

int
FcStrCmp (const FcChar8 *s1, const FcChar8 *s2);

int
FcUtf8ToUcs4 (const FcChar8 *src_orig,
              FcChar32      *dst,
              int           len);

FcBool
FcUtf8Len (const FcChar8    *string,
           int              len,
           int              *nchar,
           int              *wchar);

#define FC_UTF8_MAX_LEN 6

int
FcUcs4ToUtf8 (FcChar32  ucs4,
              FcChar8   dest[FC_UTF8_MAX_LEN]);

int
FcUtf16ToUcs4 (const FcChar8    *src_orig,
               FcEndian         endian,
               FcChar32         *dst,
               int              len);       /* in bytes */

FcBool
FcUtf16Len (const FcChar8   *string,
            FcEndian        endian,
            int             len,            /* in bytes */
            int             *nchar,
            int             *wchar);

FcChar8 *
FcStrDirname (const FcChar8 *file);

FcChar8 *
FcStrBasename (const FcChar8 *file);

FcStrSet *
FcStrSetCreate (void);

FcBool
FcStrSetMember (FcStrSet *set, const FcChar8 *s);

FcBool
FcStrSetEqual (FcStrSet *sa, FcStrSet *sb);

FcBool
FcStrSetAdd (FcStrSet *set, const FcChar8 *s);

FcBool
FcStrSetAddFilename (FcStrSet *set, const FcChar8 *s);

FcBool
FcStrSetDel (FcStrSet *set, const FcChar8 *s);

void
FcStrSetDestroy (FcStrSet *set);

FcStrList *
FcStrListCreate (FcStrSet *set);

FcChar8 *
FcStrListNext (FcStrList *list);

void
FcStrListDone (FcStrList *list);

/* fcxml.c */
FcBool
FcConfigParseAndLoad (FcConfig *config, const FcChar8 *file, FcBool complain);

_FCFUNCPROTOEND

#endif /* _FONTCONFIG_H_ */
