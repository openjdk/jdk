/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include <windows.h>
#include <stdio.h>

#include <jni.h>
#include <jni_util.h>
#include <sun_awt_Win32FontManager.h>

#define BSIZE (max(512, MAX_PATH+1))


JNIEXPORT jstring JNICALL Java_sun_awt_Win32FontManager_getFontPath(JNIEnv *env, jobject thiz, jboolean noType1)
{
    char windir[BSIZE];
    char sysdir[BSIZE];
    char fontpath[BSIZE*2];
    char *end;

    /* Locate fonts directories relative to the Windows System directory.
     * If Windows System location is different than the user's window
     * directory location, as in a shared Windows installation,
     * return both locations as potential font directories
     */
    GetSystemDirectory(sysdir, BSIZE);
    end = strrchr(sysdir,'\\');
    if (end && (stricmp(end,"\\System") || stricmp(end,"\\System32"))) {
        *end = 0;
         strcat(sysdir, "\\Fonts");
    }

    GetWindowsDirectory(windir, BSIZE);
    if (strlen(windir) > BSIZE-7) {
        *windir = 0;
    } else {
        strcat(windir, "\\Fonts");
    }

    strcpy(fontpath,sysdir);
    if (stricmp(sysdir,windir)) {
        strcat(fontpath,";");
        strcat(fontpath,windir);
    }

    return JNU_NewStringPlatform(env, fontpath);
}

/* The code below is used to obtain information from the windows font APIS
 * and registry on which fonts are available and what font files hold those
 * fonts. The results are used to speed font lookup.
 */

typedef struct GdiFontMapInfo {
    JNIEnv *env;
    jstring family;
    jobject fontToFamilyMap;
    jobject familyToFontListMap;
    jobject list;
    jmethodID putMID;
    jmethodID containsKeyMID;
    jclass arrayListClass;
    jmethodID arrayListCtr;
    jmethodID addMID;
    jmethodID toLowerCaseMID;
    jobject locale;
} GdiFontMapInfo;

/* IS_NT means NT or later OSes which support Unicode.
 * We have to painfully deal with the ASCII and non-ASCII case we
 * we really want to get the font names as unicode wherever possible.
 * UNICODE_OS is 0 to mean uninitialised, 1 to mean not a unicode OS,
 * 2 to mean a unicode OS.
 */

#define UC_UNKNOWN 0
#define UC_NO     1
#define UC_YES    2
static int UNICODE_OS = UC_UNKNOWN;
static int GetOSVersion () {
    OSVERSIONINFO vinfo;
    vinfo.dwOSVersionInfoSize = sizeof(vinfo);
    GetVersionEx(&vinfo);
    if ((int)vinfo.dwMajorVersion > 4) {
        UNICODE_OS = UC_YES;
    } else if ((int)vinfo.dwMajorVersion < 4) {
        UNICODE_OS = UC_NO;
    } else {
        if ((int)vinfo.dwPlatformId == VER_PLATFORM_WIN32_WINDOWS) {
            UNICODE_OS = UC_NO;
        } else {
            UNICODE_OS = UC_YES;
        }
    }
    return UNICODE_OS;
}

#define IS_NT ((UNICODE_OS == UC_UNKNOWN) \
   ? (GetOSVersion() == UC_YES) : (UNICODE_OS == UC_YES))

/* NT is W2K & XP. WIN is Win9x */
static const char FONTKEY_NT[] =
    "Software\\Microsoft\\Windows NT\\CurrentVersion\\Fonts";
static const char FONTKEY_WIN[] =
    "Software\\Microsoft\\Windows\\CurrentVersion\\Fonts";

/* Callback for call to EnumFontFamiliesEx in the EnumFamilyNames function.
 * Expects to be called once for each face name in the family specified
 * in the call. We extract the full name for the font which is expected
 * to be in the "system encoding" and create canonical and lower case
 * Java strings for the name which are added to the maps. The lower case
 * name is used as key to the family name value in the font to family map,
 * the canonical name is one of the"list" of members of the family.
 */
static int CALLBACK EnumFontFacesInFamilyProcA(
  ENUMLOGFONTEXA *lpelfe,
  NEWTEXTMETRICEX *lpntme,
  int FontType,
  LPARAM lParam )
{
    GdiFontMapInfo *fmi = (GdiFontMapInfo*)lParam;
    JNIEnv *env = fmi->env;
    jstring fullname, fullnameLC;

    /* Both Vista and XP return DEVICE_FONTTYPE for OTF fonts */
    if (FontType != TRUETYPE_FONTTYPE && FontType != DEVICE_FONTTYPE) {
        return 1;
    }

    /* printf("FULL=%s\n",lpelfe->elfFullName);fflush(stdout);  */

    fullname = JNU_NewStringPlatform(env, lpelfe->elfFullName);
    if (fullname == NULL) {
        (*env)->ExceptionClear(env);
        return 1;

    fullnameLC = (*env)->CallObjectMethod(env, fullname,
                                          fmi->toLowerCaseMID, fmi->locale);
    (*env)->CallBooleanMethod(env, fmi->list, fmi->addMID, fullname);
    (*env)->CallObjectMethod(env, fmi->fontToFamilyMap,
                             fmi->putMID, fullnameLC, fmi->family);
    return 1;
}

typedef struct CheckFamilyInfo {
  wchar_t *family;
  wchar_t* fullName;
  int isDifferent;
} CheckFamilyInfo;

static int CALLBACK CheckFontFamilyProcW(
  ENUMLOGFONTEXW *lpelfe,
  NEWTEXTMETRICEX *lpntme,
  int FontType,
  LPARAM lParam)
{
    CheckFamilyInfo *info = (CheckFamilyInfo*)lParam;
    info->isDifferent = wcscmp(lpelfe->elfLogFont.lfFaceName, info->family);

/*     if (!info->isDifferent) { */
/*         wprintf(LFor font %s expected family=%s instead got %s\n", */
/*                 lpelfe->elfFullName, */
/*                 info->family, */
/*                 lpelfe->elfLogFont.lfFaceName); */
/*         fflush(stdout); */
/*     } */
    return 0;
}

/* This HDC is initialised and released in the populate family map
 * JNI entry point, and used within the call which would otherwise
 * create many DCs.
 */
static HDC screenDC = NULL;

static int DifferentFamily(wchar_t *family, wchar_t* fullName) {
    LOGFONTW lfw;
    CheckFamilyInfo info;

    /* If fullName can't be stored in the struct, assume correct family */
    if (wcslen((LPWSTR)fullName) >= LF_FACESIZE) {
        return 0;
    }

    memset(&info, 0, sizeof(CheckFamilyInfo));
    info.family = family;
    info.fullName = fullName;
    info.isDifferent = 0;

    memset(&lfw, 0, sizeof(lfw));
    wcscpy(lfw.lfFaceName, fullName);
    lfw.lfCharSet = DEFAULT_CHARSET;
    EnumFontFamiliesExW(screenDC, &lfw,
                        (FONTENUMPROCW)CheckFontFamilyProcW,
                        (LPARAM)(&info), 0L);

    return info.isDifferent;
}

static int CALLBACK EnumFontFacesInFamilyProcW(
  ENUMLOGFONTEXW *lpelfe,
  NEWTEXTMETRICEX *lpntme,
  int FontType,
  LPARAM lParam)
{
    GdiFontMapInfo *fmi = (GdiFontMapInfo*)lParam;
    JNIEnv *env = fmi->env;
    jstring fullname, fullnameLC;

    /* Both Vista and XP return DEVICE_FONTTYPE for OTF fonts */
    if (FontType != TRUETYPE_FONTTYPE && FontType != DEVICE_FONTTYPE) {
        return 1;
    }

    /* Windows has font aliases and so may enumerate fonts from
     * the aliased family if any actual font of that family is installed.
     * To protect against it ignore fonts which aren't enumerated under
     * their true family.
     */
    if (DifferentFamily(lpelfe->elfLogFont.lfFaceName,
                        lpelfe->elfFullName))  {
      return 1;
    }

    fullname = (*env)->NewString(env, lpelfe->elfFullName,
                                 (jsize)wcslen((LPWSTR)lpelfe->elfFullName));
    if (fullname == NULL) {
        (*env)->ExceptionClear(env);
        return 1;
    }
    fullnameLC = (*env)->CallObjectMethod(env, fullname,
                                          fmi->toLowerCaseMID, fmi->locale);
    (*env)->CallBooleanMethod(env, fmi->list, fmi->addMID, fullname);
    (*env)->CallObjectMethod(env, fmi->fontToFamilyMap,
                             fmi->putMID, fullnameLC, fmi->family);
    return 1;
}

/* Callback for EnumFontFamiliesEx in populateFontFileNameMap.
 * Expects to be called for every charset of every font family.
 * If this is the first time we have been called for this family,
 * add a new mapping to the familyToFontListMap from this family to a
 * list of its members. To populate that list, further enumerate all faces
 * in this family for the matched charset. This assumes that all fonts
 * in a family support the same charset, which is a fairly safe assumption
 * and saves time as the call we make here to EnumFontFamiliesEx will
 * enumerate the members of this family just once each.
 * Because we set fmi->list to be the newly created list the call back
 * can safely add to that list without a search.
 */
static int CALLBACK EnumFamilyNamesA(
  ENUMLOGFONTEXA *lpelfe,    /* pointer to logical-font data */
  NEWTEXTMETRICEX *lpntme,   /* pointer to physical-font data */
  int FontType,              /* type of font */
  LPARAM lParam)             /* application-defined data */
{
    GdiFontMapInfo *fmi = (GdiFontMapInfo*)lParam;
    JNIEnv *env = fmi->env;
    jstring familyLC;
    LOGFONTA lfa;

    /* Both Vista and XP return DEVICE_FONTTYPE for OTF fonts */
    if (FontType != TRUETYPE_FONTTYPE && FontType != DEVICE_FONTTYPE) {
        return 1;
    }

    /* Windows lists fonts which have a vmtx (vertical metrics) table twice.
     * Once using their normal name, and again preceded by '@'. These appear
     * in font lists in some windows apps, such as wordpad. We don't want
     * these so we skip any font where the first character is '@'
     */
    if (lpelfe->elfLogFont.lfFaceName[0] == '@') {
        return 1;
    }
    fmi->family = JNU_NewStringPlatform(env,lpelfe->elfLogFont.lfFaceName);
    if (fmi->family == NULL) {
        (*env)->ExceptionClear(env);
        return 1;
    }
    familyLC = (*env)->CallObjectMethod(env, fmi->family,
                                        fmi->toLowerCaseMID, fmi->locale);
    /* check if already seen this family with a different charset */
    if ((*env)->CallBooleanMethod(env,fmi->familyToFontListMap,
                                  fmi->containsKeyMID, familyLC)) {
        return 1;
    }
    fmi->list = (*env)->NewObject(env,
                                  fmi->arrayListClass, fmi->arrayListCtr, 4);
    if (fmi->list == NULL) {
        (*env)->ExceptionClear(env);
        return 1;
    }
    (*env)->CallObjectMethod(env, fmi->familyToFontListMap,
                             fmi->putMID, familyLC, fmi->list);

/*  printf("FAMILY=%s\n", lpelfe->elfLogFont.lfFaceName);fflush(stdout); */

    memset(&lfa, 0, sizeof(lfa));
    strcpy(lfa.lfFaceName, lpelfe->elfLogFont.lfFaceName);
    lfa.lfCharSet = lpelfe->elfLogFont.lfCharSet;
    EnumFontFamiliesExA(screenDC, &lfa,
                        (FONTENUMPROCA)EnumFontFacesInFamilyProcA,
                        lParam, 0L);
    return 1;
}

static int CALLBACK EnumFamilyNamesW(
  ENUMLOGFONTEXW *lpelfe,    /* pointer to logical-font data */
  NEWTEXTMETRICEX *lpntme,  /* pointer to physical-font data */
  int FontType,             /* type of font */
  LPARAM lParam )           /* application-defined data */
{
    GdiFontMapInfo *fmi = (GdiFontMapInfo*)lParam;
    JNIEnv *env = fmi->env;
    jstring familyLC;
    size_t slen;
    LOGFONTW lfw;

    /* Both Vista and XP return DEVICE_FONTTYPE for OTF fonts */
    if (FontType != TRUETYPE_FONTTYPE && FontType != DEVICE_FONTTYPE) {
        return 1;
    }
/*     wprintf(L"FAMILY=%s charset=%d FULL=%s\n", */
/*          lpelfe->elfLogFont.lfFaceName, */
/*          lpelfe->elfLogFont.lfCharSet, */
/*          lpelfe->elfFullName); */
/*     fflush(stdout); */

    /* Windows lists fonts which have a vmtx (vertical metrics) table twice.
     * Once using their normal name, and again preceded by '@'. These appear
     * in font lists in some windows apps, such as wordpad. We don't want
     * these so we skip any font where the first character is '@'
     */
    if (lpelfe->elfLogFont.lfFaceName[0] == L'@') {
            return 1;
    }
    slen = wcslen(lpelfe->elfLogFont.lfFaceName);
    fmi->family = (*env)->NewString(env,lpelfe->elfLogFont.lfFaceName, (jsize)slen);
    if (fmi->family == NULL) {
        (*env)->ExceptionClear(env);
        return 1;
    }
    familyLC = (*env)->CallObjectMethod(env, fmi->family,
                                        fmi->toLowerCaseMID, fmi->locale);
    /* check if already seen this family with a different charset */
    if ((*env)->CallBooleanMethod(env,fmi->familyToFontListMap,
                                  fmi->containsKeyMID, familyLC)) {
        return 1;
    }
    fmi->list = (*env)->NewObject(env,
                                  fmi->arrayListClass, fmi->arrayListCtr, 4);
    if (fmi->list == NULL) {
        (*env)->ExceptionClear(env);
        return 1;
    }
    (*env)->CallObjectMethod(env, fmi->familyToFontListMap,
                             fmi->putMID, familyLC, fmi->list);

    memset(&lfw, 0, sizeof(lfw));
    wcscpy(lfw.lfFaceName, lpelfe->elfLogFont.lfFaceName);
    lfw.lfCharSet = lpelfe->elfLogFont.lfCharSet;
    EnumFontFamiliesExW(screenDC, &lfw,
                        (FONTENUMPROCW)EnumFontFacesInFamilyProcW,
                        lParam, 0L);
    return 1;
}


/* It looks like TrueType fonts have " (TrueType)" tacked on the end of their
 * name, so we can try to use that to distinguish TT from other fonts.
 * However if a program "installed" a font in the registry the key may
 * not include that. We could also try to "pass" fonts which have no "(..)"
 * at the end. But that turns out to pass a few .FON files that MS supply.
 * If there's no parenthesized type string, we could next try to infer
 * the file type from the file name extension. Since the MS entries that
 * have no type string are very few, and have odd names like "MS-DOS CP 437"
 * and would never return a Java Font anyway its currently OK to put these
 * in the font map, although clearly the returned names must never percolate
 * up into a list of available fonts returned to the application.
 * Additionally for TTC font files the key looks like
 * Font 1 & Font 2 (TrueType)
 * or sometimes even :
 * Font 1 & Font 2 & Font 3 (TrueType)
 * Also if a Font has a name for this locale that name also
 * exists in the registry using the appropriate platform encoding.
 * What do we do then?
 *
 * Note: OpenType fonts seems to have " (TrueType)" suffix on Vista
 *   but " (OpenType)" on XP.
 */

static BOOL RegistryToBaseTTNameA(LPSTR name) {
    static const char TTSUFFIX[] = " (TrueType)";
    static const char OTSUFFIX[] = " (OpenType)";
    size_t TTSLEN = strlen(TTSUFFIX);
    char *suffix;

    size_t len = strlen(name);
    if (len == 0) {
        return FALSE;
    }
    if (name[len-1] != ')') {
        return FALSE;
    }
    if (len <= TTSLEN) {
        return FALSE;
    }

    /* suffix length is the same for truetype and opentype fonts */
    suffix = name + len - TTSLEN;
    if (strcmp(suffix, TTSUFFIX) == 0 || strcmp(suffix, OTSUFFIX) == 0) {
        suffix[0] = '\0'; /* truncate name */
        return TRUE;
    }
    return FALSE;
}

static BOOL RegistryToBaseTTNameW(LPWSTR name) {
    static const wchar_t TTSUFFIX[] = L" (TrueType)";
    static const wchar_t OTSUFFIX[] = L" (OpenType)";
    size_t TTSLEN = wcslen(TTSUFFIX);
    wchar_t *suffix;

    size_t len = wcslen(name);
    if (len == 0) {
        return FALSE;
    }
    if (name[len-1] != L')') {
        return FALSE;
    }
    if (len <= TTSLEN) {
        return FALSE;
    }
    /* suffix length is the same for truetype and opentype fonts */
    suffix = name + (len - TTSLEN);
    if (wcscmp(suffix, TTSUFFIX) == 0 || wcscmp(suffix, OTSUFFIX) == 0) {
        suffix[0] = L'\0'; /* truncate name */
        return TRUE;
    }
    return FALSE;
}

static void registerFontA(GdiFontMapInfo *fmi, jobject fontToFileMap,
                          LPCSTR name, LPCSTR data) {
    LPSTR ptr1, ptr2;
    jstring fontStr;
    JNIEnv *env = fmi->env;
    size_t dslen = strlen(data);
    jstring fileStr = JNU_NewStringPlatform(env, data);
    if (fileStr == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }

    /* TTC or ttc means it may be a collection. Need to parse out
     * multiple font face names separated by " & "
     * By only doing this for fonts which look like collections based on
     * file name we are adhering to MS recommendations for font file names
     * so it seems that we can be sure that this identifies precisely
     * the MS-supplied truetype collections.
     * This avoids any potential issues if a TTF file happens to have
     * a & in the font name (I can't find anything which prohibits this)
     * and also means we only parse the key in cases we know to be
     * worthwhile.
     */
    if ((data[dslen-1] == 'C' || data[dslen-1] == 'c') &&
        (ptr1 = strstr(name, " & ")) != NULL) {
        ptr1+=3;
        while (ptr1 >= name) { /* marginally safer than while (true) */
            while ((ptr2 = strstr(ptr1, " & ")) != NULL) {
                    ptr1 = ptr2+3;
            }
            fontStr = JNU_NewStringPlatform(env, ptr1);
            if (fontStr == NULL) {
                (*env)->ExceptionClear(env);
                return;
            }
            fontStr = (*env)->CallObjectMethod(env, fontStr,
                                               fmi->toLowerCaseMID,
                                               fmi->locale);
            (*env)->CallObjectMethod(env, fontToFileMap, fmi->putMID,
                                     fontStr, fileStr);
            if (ptr1 == name) {
                break;
            } else {
                *(ptr1-3) ='\0';
                ptr1 = (LPSTR)name;
            }
        }
    } else {
        fontStr = JNU_NewStringPlatform(env, name);
        if (fontStr == NULL) {
            (*env)->ExceptionClear(env);
            return;
        }
        fontStr = (*env)->CallObjectMethod(env, fontStr,
                                           fmi->toLowerCaseMID, fmi->locale);
        (*env)->CallObjectMethod(env, fontToFileMap, fmi->putMID,
                                 fontStr, fileStr);
    }
}

static void registerFontW(GdiFontMapInfo *fmi, jobject fontToFileMap,
                          LPWSTR name, LPWSTR data) {

    wchar_t *ptr1, *ptr2;
    jstring fontStr;
    JNIEnv *env = fmi->env;
    size_t dslen = wcslen(data);
    jstring fileStr = (*env)->NewString(env, data, (jsize)dslen);
    if (fileStr == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }

    /* TTC or ttc means it may be a collection. Need to parse out
     * multiple font face names separated by " & "
     * By only doing this for fonts which look like collections based on
     * file name we are adhering to MS recommendations for font file names
     * so it seems that we can be sure that this identifies precisely
     * the MS-supplied truetype collections.
     * This avoids any potential issues if a TTF file happens to have
     * a & in the font name (I can't find anything which prohibits this)
     * and also means we only parse the key in cases we know to be
     * worthwhile.
     */

    if ((data[dslen-1] == L'C' || data[dslen-1] == L'c') &&
        (ptr1 = wcsstr(name, L" & ")) != NULL) {
        ptr1+=3;
        while (ptr1 >= name) { /* marginally safer than while (true) */
            while ((ptr2 = wcsstr(ptr1, L" & ")) != NULL) {
                ptr1 = ptr2+3;
            }
            fontStr = (*env)->NewString(env, ptr1, (jsize)wcslen(ptr1));
            if (fontStr == NULL) {
                (*env)->ExceptionClear(env);
                return;
            }
            fontStr = (*env)->CallObjectMethod(env, fontStr,
                                               fmi->toLowerCaseMID,
                                               fmi->locale);
            (*env)->CallObjectMethod(env, fontToFileMap, fmi->putMID,
                                     fontStr, fileStr);
            if (ptr1 == name) {
                break;
            } else {
                *(ptr1-3) = L'\0';
                ptr1 = name;
            }
        }
    } else {
        fontStr = (*env)->NewString(env, name, (jsize)wcslen(name));
        if (fontStr == NULL) {
            (*env)->ExceptionClear(env);
            return;
        }
        fontStr = (*env)->CallObjectMethod(env, fontStr,
                                           fmi->toLowerCaseMID, fmi->locale);
        (*env)->CallObjectMethod(env, fontToFileMap, fmi->putMID,
                                 fontStr, fileStr);
    }
}

/* Obtain all the fontname -> filename mappings.
 * This is called once and the results returned to Java code which can
 * use it for lookups to reduce or avoid the need to search font files.
 */
JNIEXPORT void JNICALL
Java_sun_awt_Win32FontManager_populateFontFileNameMap0
(JNIEnv *env, jclass obj, jobject fontToFileMap,
 jobject fontToFamilyMap, jobject familyToFontListMap, jobject locale)
{
#define MAX_BUFFER (FILENAME_MAX+1)
    const wchar_t wname[MAX_BUFFER];
    const char cname[MAX_BUFFER];
    const char data[MAX_BUFFER];

    DWORD type;
    LONG ret;
    HKEY hkeyFonts;
    DWORD dwNameSize;
    DWORD dwDataValueSize;
    DWORD nval;
    LPCSTR fontKeyName;
    DWORD dwNumValues, dwMaxValueNameLen, dwMaxValueDataLen;
    DWORD numValues = 0;
    jclass classID;
    jmethodID putMID;
    GdiFontMapInfo fmi;

    /* Check we were passed all the maps we need, and do lookup of
     * methods for JNI up-calls
     */
    if (fontToFileMap == NULL ||
        fontToFamilyMap == NULL ||
        familyToFontListMap == NULL) {
        return;
    }
    classID = (*env)->FindClass(env, "java/util/HashMap");
    if (classID == NULL) {
        return;
    }
    putMID = (*env)->GetMethodID(env, classID, "put",
                 "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    if (putMID == NULL) {
        return;
    }

    fmi.env = env;
    fmi.fontToFamilyMap = fontToFamilyMap;
    fmi.familyToFontListMap = familyToFontListMap;
    fmi.putMID = putMID;
    fmi.locale = locale;
    fmi.containsKeyMID = (*env)->GetMethodID(env, classID, "containsKey",
                                             "(Ljava/lang/Object;)Z");
    if (fmi.containsKeyMID == NULL) {
        return;
    }

    fmi.arrayListClass = (*env)->FindClass(env, "java/util/ArrayList");
    if (fmi.arrayListClass == NULL) {
        return;
    }
    fmi.arrayListCtr = (*env)->GetMethodID(env, fmi.arrayListClass,
                                              "<init>", "(I)V");
    if (fmi.arrayListCtr == NULL) {
        return;
    }
    fmi.addMID = (*env)->GetMethodID(env, fmi.arrayListClass,
                                     "add", "(Ljava/lang/Object;)Z");
    if (fmi.addMID == NULL) {
        return;
    }
    classID = (*env)->FindClass(env, "java/lang/String");
    if (classID == NULL) {
        return;
    }
    fmi.toLowerCaseMID =
        (*env)->GetMethodID(env, classID, "toLowerCase",
                            "(Ljava/util/Locale;)Ljava/lang/String;");
    if (fmi.toLowerCaseMID == NULL) {
        return;
    }

    screenDC = GetDC(NULL);
    if (screenDC == NULL) {
        return;
    }
    /* Enumerate fonts via GDI to build maps of fonts and families */
    if (IS_NT) {
        LOGFONTW lfw;
        memset(&lfw, 0, sizeof(lfw));
        lfw.lfCharSet = DEFAULT_CHARSET;  /* all charsets */
        wcscpy(lfw.lfFaceName, L"");      /* one face per family (CHECK) */
        EnumFontFamiliesExW(screenDC, &lfw,
                            (FONTENUMPROCW)EnumFamilyNamesW,
                            (LPARAM)(&fmi), 0L);
    } else {
        LOGFONT lfa;
        memset(&lfa, 0, sizeof(lfa));
        lfa.lfCharSet = DEFAULT_CHARSET; /* all charsets */
        strcpy(lfa.lfFaceName, "");      /* one face per family */
        ret = EnumFontFamiliesExA(screenDC, &lfa,
                            (FONTENUMPROCA)EnumFamilyNamesA,
                            (LPARAM)(&fmi), 0L);
    }

    /* Use the windows registry to map font names to files */
    fontKeyName = (IS_NT) ? FONTKEY_NT : FONTKEY_WIN;
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                       fontKeyName, 0L, KEY_READ, &hkeyFonts);
    if (ret != ERROR_SUCCESS) {
        ReleaseDC(NULL, screenDC);
        screenDC = NULL;
        return;
    }

    if (IS_NT) {
        ret = RegQueryInfoKeyW(hkeyFonts, NULL, NULL, NULL, NULL, NULL, NULL,
                               &dwNumValues, &dwMaxValueNameLen,
                               &dwMaxValueDataLen, NULL, NULL);
    } else {
        ret = RegQueryInfoKeyA(hkeyFonts, NULL, NULL, NULL, NULL, NULL, NULL,
                               &dwNumValues, &dwMaxValueNameLen,
                               &dwMaxValueDataLen, NULL, NULL);
    }
    if (ret != ERROR_SUCCESS ||
        dwMaxValueNameLen >= MAX_BUFFER ||
        dwMaxValueDataLen >= MAX_BUFFER) {
        RegCloseKey(hkeyFonts);
        ReleaseDC(NULL, screenDC);
        screenDC = NULL;
        return;
    }
    for (nval = 0; nval < dwNumValues; nval++ ) {
        dwNameSize = MAX_BUFFER;
        dwDataValueSize = MAX_BUFFER;
        if (IS_NT) {
            ret = RegEnumValueW(hkeyFonts, nval, (LPWSTR)wname, &dwNameSize,
                                NULL, &type, (LPBYTE)data, &dwDataValueSize);
        } else {
            ret = RegEnumValueA(hkeyFonts, nval, (LPSTR)cname, &dwNameSize,
                                NULL, &type, (LPBYTE)data, &dwDataValueSize);
        }
        if (ret != ERROR_SUCCESS) {
            break;
        }
        if (type != REG_SZ) { /* REG_SZ means a null-terminated string */
            continue;
        }
        if (IS_NT) {
            if (!RegistryToBaseTTNameW((LPWSTR)wname) ) {
                /* If the filename ends with ".ttf" or ".otf" also accept it.
                 * Not expecting to need to do this for .ttc files.
                 * Also note this code is not mirrored in the "A" (win9x) path.
                 */
                LPWSTR dot = wcsrchr((LPWSTR)data, L'.');
                if (dot == NULL || ((wcsicmp(dot, L".ttf") != 0)
                                      && (wcsicmp(dot, L".otf") != 0))) {
                    continue;  /* not a TT font... */
                }
            }
            registerFontW(&fmi, fontToFileMap, (LPWSTR)wname, (LPWSTR)data);
        } else {
            if (!RegistryToBaseTTNameA((LPSTR)cname)) {
                continue; /* not a TT font... */
            }
            registerFontA(&fmi, fontToFileMap, cname, (LPCSTR)data);
        }
    }
    RegCloseKey(hkeyFonts);
    ReleaseDC(NULL, screenDC);
    screenDC = NULL;
}
