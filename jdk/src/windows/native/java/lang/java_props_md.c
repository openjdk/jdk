/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include <shlobj.h>
#include <objidl.h>
#include <locale.h>
#include <sys/types.h>
#include <sys/timeb.h>
#include <tchar.h>

#include "locale_str.h"
#include "java_props.h"

#ifndef VER_PLATFORM_WIN32_WINDOWS
#define VER_PLATFORM_WIN32_WINDOWS 1
#endif

#ifndef PROCESSOR_ARCHITECTURE_AMD64
#define PROCESSOR_ARCHITECTURE_AMD64 9
#endif

typedef void (WINAPI *PGNSI)(LPSYSTEM_INFO);
static void SetupI18nProps(LCID lcid, char** language, char** script, char** country,
               char** variant, char** encoding);

#define SHELL_KEY "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders"

#define PROPSIZE 9      // eight-letter + null terminator
#define SNAMESIZE 86    // max number of chars for LOCALE_SNAME is 85

static char *
getEncodingInternal(LCID lcid)
{
    char * ret = malloc(16);
    int codepage;

    if (GetLocaleInfo(lcid,
                      LOCALE_IDEFAULTANSICODEPAGE,
                      ret+2, 14) == 0) {
        codepage = 1252;
    } else {
        codepage = atoi(ret+2);
    }

    switch (codepage) {
    case 0:
        strcpy(ret, "UTF-8");
        break;
    case 874:     /*  9:Thai     */
    case 932:     /* 10:Japanese */
    case 949:     /* 12:Korean Extended Wansung */
    case 950:     /* 13:Chinese (Taiwan, Hongkong, Macau) */
    case 1361:    /* 15:Korean Johab */
        ret[0] = 'M';
        ret[1] = 'S';
        break;
    case 936:
        strcpy(ret, "GBK");
        break;
    case 54936:
        strcpy(ret, "GB18030");
        break;
    default:
        ret[0] = 'C';
        ret[1] = 'p';
        break;
    }

    //Traditional Chinese Windows should use MS950_HKSCS_XP as the
    //default encoding, if HKSCS patch has been installed.
    // "old" MS950 0xfa41 -> u+e001
    // "new" MS950 0xfa41 -> u+92db
    if (strcmp(ret, "MS950") == 0) {
        TCHAR  mbChar[2] = {(char)0xfa, (char)0x41};
        WCHAR  unicodeChar;
        MultiByteToWideChar(CP_ACP, 0, mbChar, 2, &unicodeChar, 1);
        if (unicodeChar == 0x92db) {
            strcpy(ret, "MS950_HKSCS_XP");
        }
    } else {
        //SimpChinese Windows should use GB18030 as the default
        //encoding, if gb18030 patch has been installed (on windows
        //2000/XP, (1)Codepage 54936 will be available
        //(2)simsun18030.ttc will exist under system fonts dir )
        if (strcmp(ret, "GBK") == 0 && IsValidCodePage(54936)) {
            char systemPath[MAX_PATH + 1];
            char* gb18030Font = "\\FONTS\\SimSun18030.ttc";
            FILE *f = NULL;
            if (GetWindowsDirectory(systemPath, MAX_PATH + 1) != 0 &&
                strlen(systemPath) + strlen(gb18030Font) < MAX_PATH + 1) {
                strcat(systemPath, "\\FONTS\\SimSun18030.ttc");
                if ((f = fopen(systemPath, "r")) != NULL) {
                    fclose(f);
                    strcpy(ret, "GB18030");
                }
            }
        }
    }

    return ret;
}

// Exported entries for AWT
DllExport const char *
getEncodingFromLangID(LANGID langID)
{
    return getEncodingInternal(MAKELCID(langID, SORT_DEFAULT));
}

// Returns BCP47 Language Tag
DllExport const char *
getJavaIDFromLangID(LANGID langID)
{
    char * elems[5]; // lang, script, ctry, variant, encoding
    char * ret = malloc(SNAMESIZE);
    int index;

    SetupI18nProps(MAKELCID(langID, SORT_DEFAULT),
                   &(elems[0]), &(elems[1]), &(elems[2]), &(elems[3]), &(elems[4]));

    // there always is the "language" tag
    strcpy(ret, elems[0]);

    // append other elements, if any
    for (index = 1; index < 4; index++) {
        if ((elems[index])[0] != '\0') {
            strcat(ret, "-");
            strcat(ret, elems[index]);
        }
    }

    for (index = 0; index < 5; index++) {
        free(elems[index]);
    }

    return ret;
}

/*
 * Code to figure out the user's home directory using the registry
*/
static WCHAR*
getHomeFromRegistry()
{
    HKEY key;
    int rc;
    DWORD type;
    WCHAR *p;
    WCHAR path[MAX_PATH+1];
    int size = MAX_PATH+1;

    rc = RegOpenKeyEx(HKEY_CURRENT_USER, SHELL_KEY, 0, KEY_READ, &key);
    if (rc != ERROR_SUCCESS) {
        // Shell folder doesn't exist??!!
        return NULL;
    }

    path[0] = 0;
    rc = RegQueryValueExW(key, L"Desktop", 0, &type, (LPBYTE)path, &size);
    if (rc != ERROR_SUCCESS || type != REG_SZ) {
        return NULL;
    }
    RegCloseKey(key);
    /* Get the parent of Desktop directory */
    p = wcsrchr(path, L'\\');
    if (p == NULL) {
        return NULL;
    }
    *p = L'\0';
    return _wcsdup(path);
}

/*
 * Code to figure out the user's home directory using shell32.dll
 */
typedef HRESULT (WINAPI *GetSpecialFolderType)(HWND, int, LPITEMIDLIST *);
typedef BOOL (WINAPI *GetPathFromIDListType)(LPCITEMIDLIST, LPSTR);

WCHAR*
getHomeFromShell32()
{
    HMODULE lib = LoadLibraryW(L"SHELL32.DLL");
    GetSpecialFolderType do_get_folder;
    GetPathFromIDListType do_get_path;
    HRESULT rc;
    LPITEMIDLIST item_list = 0;
    WCHAR *p;
    WCHAR path[MAX_PATH+1];
    int size = MAX_PATH+1;

    if (lib == 0) {
        // We can't load the library !!??
        return NULL;
    }

    do_get_folder = (GetSpecialFolderType)GetProcAddress(lib, "SHGetSpecialFolderLocation");
    do_get_path = (GetPathFromIDListType)GetProcAddress(lib, "SHGetPathFromIDListW");

    if (do_get_folder == 0 || do_get_path == 0) {
        // the library doesn't hold the right functions !!??
        return NULL;
    }

    rc = (*do_get_folder)(NULL, CSIDL_DESKTOPDIRECTORY, &item_list);
    if (!SUCCEEDED(rc)) {
        // we can't find the shell folder.
        return NULL;
    }

    path[0] = 0;
    (*do_get_path)(item_list, (LPSTR)path);

    /* Get the parent of Desktop directory */
    p = wcsrchr(path, L'\\');
    if (p) {
        *p = 0;
    }

    /*
     * We've been successful.  Note that we don't free the memory allocated
     * by ShGetSpecialFolderLocation.  We only ever come through here once,
     * and only if the registry lookup failed, so it's just not worth it.
     *
     * We also don't unload the SHELL32 DLL.  We've paid the hit for loading
     * it and we may need it again later.
     */
    return _wcsdup(path);
}

static boolean
haveMMX(void)
{
    boolean mmx = 0;
    HMODULE lib = LoadLibrary("KERNEL32");
    if (lib != NULL) {
        BOOL (WINAPI *isProcessorFeaturePresent)(DWORD) =
            (BOOL (WINAPI *)(DWORD))
            GetProcAddress(lib, "IsProcessorFeaturePresent");
        if (isProcessorFeaturePresent != NULL)
            mmx = isProcessorFeaturePresent(PF_MMX_INSTRUCTIONS_AVAILABLE);
        FreeLibrary(lib);
    }
    return mmx;
}

static const char *
cpu_isalist(void)
{
    SYSTEM_INFO info;
    GetSystemInfo(&info);
    switch (info.wProcessorArchitecture) {
#ifdef PROCESSOR_ARCHITECTURE_IA64
    case PROCESSOR_ARCHITECTURE_IA64: return "ia64";
#endif
#ifdef PROCESSOR_ARCHITECTURE_AMD64
    case PROCESSOR_ARCHITECTURE_AMD64: return "amd64";
#endif
    case PROCESSOR_ARCHITECTURE_INTEL:
        switch (info.wProcessorLevel) {
        case 6: return haveMMX()
            ? "pentium_pro+mmx pentium_pro pentium+mmx pentium i486 i386 i86"
            : "pentium_pro pentium i486 i386 i86";
        case 5: return haveMMX()
            ? "pentium+mmx pentium i486 i386 i86"
            : "pentium i486 i386 i86";
        case 4: return "i486 i386 i86";
        case 3: return "i386 i86";
        }
    }
    return NULL;
}

static void
SetupI18nProps(LCID lcid, char** language, char** script, char** country,
               char** variant, char** encoding) {
    /* script */
    char tmp[SNAMESIZE];
    *script = malloc(PROPSIZE);
    if (GetLocaleInfo(lcid,
                      LOCALE_SNAME, tmp, SNAMESIZE) == 0 ||
        sscanf(tmp, "%*[a-z\\-]%1[A-Z]%[a-z]", *script, &((*script)[1])) == 0 ||
        strlen(*script) != 4) {
        (*script)[0] = '\0';
    }

    /* country */
    *country = malloc(PROPSIZE);
    if (GetLocaleInfo(lcid,
                      LOCALE_SISO3166CTRYNAME, *country, PROPSIZE) == 0 &&
        GetLocaleInfo(lcid,
                      LOCALE_SISO3166CTRYNAME2, *country, PROPSIZE) == 0) {
        (*country)[0] = '\0';
    }

    /* language */
    *language = malloc(PROPSIZE);
    if (GetLocaleInfo(lcid,
                      LOCALE_SISO639LANGNAME, *language, PROPSIZE) == 0 &&
        GetLocaleInfo(lcid,
                      LOCALE_SISO639LANGNAME2, *language, PROPSIZE) == 0) {
            /* defaults to en_US */
            strcpy(*language, "en");
            strcpy(*country, "US");
        }

    /* variant */
    *variant = malloc(PROPSIZE);
    (*variant)[0] = '\0';

    /* handling for Norwegian */
    if (strcmp(*language, "nb") == 0) {
        strcpy(*language, "no");
        strcpy(*country , "NO");
    } else if (strcmp(*language, "nn") == 0) {
        strcpy(*language, "no");
        strcpy(*country , "NO");
        strcpy(*variant, "NY");
    }

    /* encoding */
    *encoding = getEncodingInternal(lcid);
}

java_props_t *
GetJavaProperties(JNIEnv* env)
{
    static java_props_t sprops = {0};

    OSVERSIONINFOEX ver;

    if (sprops.user_dir) {
        return &sprops;
    }

    /* AWT properties */
    sprops.awt_toolkit = "sun.awt.windows.WToolkit";

    /* tmp dir */
    {
        WCHAR tmpdir[MAX_PATH + 1];
        /* we might want to check that this succeed */
        GetTempPathW(MAX_PATH + 1, tmpdir);
        sprops.tmp_dir = _wcsdup(tmpdir);
    }

    /* Printing properties */
    sprops.printerJob = "sun.awt.windows.WPrinterJob";

    /* Java2D properties */
    sprops.graphics_env = "sun.awt.Win32GraphicsEnvironment";

    {    /* This is used only for debugging of font problems. */
        WCHAR *path = _wgetenv(L"JAVA2D_FONTPATH");
        sprops.font_dir = (path != NULL) ? _wcsdup(path) : NULL;
    }

    /* OS properties */
    {
        char buf[100];
        SYSTEM_INFO si;
        PGNSI pGNSI;

        ver.dwOSVersionInfoSize = sizeof(ver);
        GetVersionEx((OSVERSIONINFO *) &ver);

        ZeroMemory(&si, sizeof(SYSTEM_INFO));
        // Call GetNativeSystemInfo if supported or GetSystemInfo otherwise.
        pGNSI = (PGNSI) GetProcAddress(
                GetModuleHandle(TEXT("kernel32.dll")),
                "GetNativeSystemInfo");
        if(NULL != pGNSI)
            pGNSI(&si);
        else
            GetSystemInfo(&si);

        /*
         * From msdn page on OSVERSIONINFOEX, current as of this
         * writing, decoding of dwMajorVersion and dwMinorVersion.
         *
         *  Operating system            dwMajorVersion  dwMinorVersion
         * ==================           ==============  ==============
         *
         * Windows 95                   4               0
         * Windows 98                   4               10
         * Windows ME                   4               90
         * Windows 3.51                 3               51
         * Windows NT 4.0               4               0
         * Windows 2000                 5               0
         * Windows XP 32 bit            5               1
         * Windows Server 2003 family   5               2
         * Windows XP 64 bit            5               2
         *       where ((&ver.wServicePackMinor) + 2) = 1
         *       and  si.wProcessorArchitecture = 9
         * Windows Vista family         6               0  (VER_NT_WORKSTATION)
         * Windows Server 2008          6               0  (!VER_NT_WORKSTATION)
         * Windows 7                    6               1  (VER_NT_WORKSTATION)
         * Windows Server 2008 R2       6               1  (!VER_NT_WORKSTATION)
         *
         * This mapping will presumably be augmented as new Windows
         * versions are released.
         */
        switch (ver.dwPlatformId) {
        case VER_PLATFORM_WIN32s:
            sprops.os_name = "Windows 3.1";
            break;
        case VER_PLATFORM_WIN32_WINDOWS:
           if (ver.dwMajorVersion == 4) {
                switch (ver.dwMinorVersion) {
                case  0: sprops.os_name = "Windows 95";           break;
                case 10: sprops.os_name = "Windows 98";           break;
                case 90: sprops.os_name = "Windows Me";           break;
                default: sprops.os_name = "Windows 9X (unknown)"; break;
                }
            } else {
                sprops.os_name = "Windows 9X (unknown)";
            }
            break;
        case VER_PLATFORM_WIN32_NT:
            if (ver.dwMajorVersion <= 4) {
                sprops.os_name = "Windows NT";
            } else if (ver.dwMajorVersion == 5) {
                switch (ver.dwMinorVersion) {
                case  0: sprops.os_name = "Windows 2000";         break;
                case  1: sprops.os_name = "Windows XP";           break;
                case  2:
                   /*
                    * From MSDN OSVERSIONINFOEX and SYSTEM_INFO documentation:
                    *
                    * "Because the version numbers for Windows Server 2003
                    * and Windows XP 6u4 bit are identical, you must also test
                    * whether the wProductType member is VER_NT_WORKSTATION.
                    * and si.wProcessorArchitecture is
                    * PROCESSOR_ARCHITECTURE_AMD64 (which is 9)
                    * If it is, the operating system is Windows XP 64 bit;
                    * otherwise, it is Windows Server 2003."
                    */
                    if(ver.wProductType == VER_NT_WORKSTATION &&
                       si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64) {
                        sprops.os_name = "Windows XP"; /* 64 bit */
                    } else {
                        sprops.os_name = "Windows 2003";
                    }
                    break;
                default: sprops.os_name = "Windows NT (unknown)"; break;
                }
            } else if (ver.dwMajorVersion == 6) {
                /*
                 * See table in MSDN OSVERSIONINFOEX documentation.
                 */
                if (ver.wProductType == VER_NT_WORKSTATION) {
                    switch (ver.dwMinorVersion) {
                    case  0: sprops.os_name = "Windows Vista";        break;
                    case  1: sprops.os_name = "Windows 7";            break;
                    default: sprops.os_name = "Windows NT (unknown)";
                    }
                } else {
                    switch (ver.dwMinorVersion) {
                    case  0: sprops.os_name = "Windows Server 2008";    break;
                    case  1: sprops.os_name = "Windows Server 2008 R2"; break;
                    default: sprops.os_name = "Windows NT (unknown)";
                    }
                }
            } else {
                sprops.os_name = "Windows NT (unknown)";
            }
            break;
        default:
            sprops.os_name = "Windows (unknown)";
            break;
        }
        sprintf(buf, "%d.%d", ver.dwMajorVersion, ver.dwMinorVersion);
        sprops.os_version = _strdup(buf);
#if _M_IA64
        sprops.os_arch = "ia64";
#elif _M_AMD64
        sprops.os_arch = "amd64";
#elif _X86_
        sprops.os_arch = "x86";
#else
        sprops.os_arch = "unknown";
#endif

        sprops.patch_level = _strdup(ver.szCSDVersion);

        sprops.desktop = "windows";
    }

    /* Endianness of platform */
    {
        unsigned int endianTest = 0xff000000;
        if (((char*)(&endianTest))[0] != 0) {
            sprops.cpu_endian = "big";
        } else {
            sprops.cpu_endian = "little";
        }
    }

    /* CPU ISA list */
    sprops.cpu_isalist = cpu_isalist();

    /*
     * User name
     * We try to avoid calling GetUserName as it turns out to
     * be surprisingly expensive on NT.  It pulls in an extra
     * 100 K of footprint.
     */
    {
        WCHAR *uname = _wgetenv(L"USERNAME");
        if (uname != NULL && wcslen(uname) > 0) {
            sprops.user_name = _wcsdup(uname);
        } else {
            WCHAR buf[100];
            int buflen = sizeof(buf);
            sprops.user_name =
                GetUserNameW(buf, &buflen) ? _wcsdup(buf) : L"unknown";
        }
    }

    /*
     * Home directory/
     *
     * We first look under a standard registry key.  If that fails we
     * fall back on using a SHELL32.DLL API.  If that fails we use a
     * default value.
     *
     * Note: To save space we want to avoid loading SHELL32.DLL
     * unless really necessary.  However if we do load it, we leave it
     * in memory, as it may be needed again later.
     *
     * The normal result is that for a given user name XXX:
     *     On multi-user NT, user.home gets set to c:\winnt\profiles\XXX.
     *     On multi-user Win95, user.home gets set to c:\windows\profiles\XXX.
     *     On single-user Win95, user.home gets set to c:\windows.
     */
    {
        WCHAR *homep = getHomeFromRegistry();
        if (homep == NULL) {
            homep = getHomeFromShell32();
            if (homep == NULL)
                homep = L"C:\\";
        }
        sprops.user_home = _wcsdup(homep);
    }

    /*
     *  user.language
     *  user.script, user.country, user.variant (if user's environment specifies them)
     *  file.encoding
     *  file.encoding.pkg
     */
    {
        /*
         * query the system for the current system default locale
         * (which is a Windows LCID value),
         */
        LCID userDefaultLCID = GetUserDefaultLCID();
        LCID systemDefaultLCID = GetSystemDefaultLCID();
        LCID userDefaultUILang = GetUserDefaultUILanguage();

        {
            char * display_encoding;

            SetupI18nProps(userDefaultUILang,
                           &sprops.language,
                           &sprops.script,
                           &sprops.country,
                           &sprops.variant,
                           &display_encoding);
            SetupI18nProps(userDefaultLCID,
                           &sprops.format_language,
                           &sprops.format_script,
                           &sprops.format_country,
                           &sprops.format_variant,
                           &sprops.encoding);
            SetupI18nProps(userDefaultUILang,
                           &sprops.display_language,
                           &sprops.display_script,
                           &sprops.display_country,
                           &sprops.display_variant,
                           &display_encoding);

            sprops.sun_jnu_encoding = getEncodingInternal(systemDefaultLCID);
            if (LANGIDFROMLCID(userDefaultLCID) == 0x0c04 && ver.dwMajorVersion == 6) {
                // MS claims "Vista has built-in support for HKSCS-2004.
                // All of the HKSCS-2004 characters have Unicode 4.1.
                // PUA code point assignments". But what it really means
                // is that the HKSCS-2004 is ONLY supported in Unicode.
                // Test indicates the MS950 in its zh_HK locale is a
                // "regular" MS950 which does not handle HKSCS-2004 at
                // all. Set encoding to MS950_HKSCS.
                sprops.encoding = "MS950_HKSCS";
                sprops.sun_jnu_encoding = "MS950_HKSCS";
            }
        }
    }

    sprops.unicode_encoding = "UnicodeLittle";
    /* User TIMEZONE */
    {
        /*
         * We defer setting up timezone until it's actually necessary.
         * Refer to TimeZone.getDefault(). However, the system
         * property is necessary to be able to be set by the command
         * line interface -D. Here temporarily set a null string to
         * timezone.
         */
        sprops.timezone = "";
    }

    /* Current directory */
    {
        WCHAR buf[MAX_PATH];
        GetCurrentDirectoryW(sizeof(buf), buf);
        sprops.user_dir = _wcsdup(buf);
    }

    sprops.file_separator = "\\";
    sprops.path_separator = ";";
    sprops.line_separator = "\r\n";

    return &sprops;
}

jstring
GetStringPlatform(JNIEnv *env, nchar* wcstr)
{
    return (*env)->NewString(env, wcstr, wcslen(wcstr));
}
