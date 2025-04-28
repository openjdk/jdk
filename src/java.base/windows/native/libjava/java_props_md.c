/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"

#include <windows.h>
#include <shlobj.h>
#include <objidl.h>
#include <locale.h>
#include <sys/types.h>
#include <sys/timeb.h>
#include <tchar.h>

#include <stdlib.h>
#include <Wincon.h>

#include "locale_str.h"
#include "java_props.h"

#ifndef PROCESSOR_ARCHITECTURE_AMD64
#define PROCESSOR_ARCHITECTURE_AMD64 9
#endif

typedef void (WINAPI *PGNSI)(LPSYSTEM_INFO);
static BOOL SetupI18nProps(LCID lcid, char** language, char** script, char** country,
               char** variant);

#define PROPSIZE 9      // eight-letter + null terminator
#define SNAMESIZE 86    // max number of chars for LOCALE_SNAME is 85

static char *
getEncodingInternal(LCID lcid)
{
    int codepage = 0;
    char * ret = malloc(16);
    if (ret == NULL) {
        return NULL;
    }

    if (lcid == 0) { // for sun.jnu.encoding
        codepage = GetACP();
        _itoa_s(codepage, ret + 2, 14, 10);
    } else if (GetLocaleInfo(lcid,
                      LOCALE_IDEFAULTANSICODEPAGE,
                      ret + 2, 14) != 0) {
        codepage = atoi(ret + 2);
    }

    switch (codepage) {
    case 0:
    case 65001:
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

static char* getConsoleEncoding(BOOL output)
{
    size_t buflen = 16;
    char* buf = malloc(buflen);
    int cp;
    if (buf == NULL) {
        return NULL;
    }
    if (output) {
        cp = GetConsoleOutputCP();
    } else {
        cp = GetConsoleCP();
    }
    if (cp >= 874 && cp <= 950) {
        snprintf(buf, buflen, "ms%d", cp);
    } else if (cp == 65001) {
        snprintf(buf, buflen, "UTF-8");
    } else if (cp == 0) {
        // Failed to get the console code page
        free(buf);
        buf = NULL;
    } else {
        snprintf(buf, buflen, "cp%d", cp);
    }
    return buf;
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
    char * elems[4]; // lang, script, ctry, variant
    char * ret;
    int index;

    ret = malloc(SNAMESIZE);
    if (ret == NULL) {
        return NULL;
    }

    for (index = 0; index < 4; index++) {
        elems[index] = NULL;
    }

    if (SetupI18nProps(MAKELCID(langID, SORT_DEFAULT),
                   &(elems[0]), &(elems[1]), &(elems[2]), &(elems[3]))) {

        // there always is the "language" tag
        strcpy(ret, elems[0]);

        // append other elements, if any
        for (index = 1; index < 4; index++) {
            if ((elems[index])[0] != '\0') {
                strcat(ret, "-");
                strcat(ret, elems[index]);
            }
        }
    } else {
        free(ret);
        ret = NULL;
    }

    for (index = 0; index < 4; index++) {
        if (elems[index] != NULL) {
            free(elems[index]);
        }
    }

    return ret;
}

/*
 * Code to figure out the user's home directory using shell32.dll
 */
WCHAR*
getHomeFromShell32()
{
    /*
     * Note that we don't free the memory allocated
     * by getHomeFromShell32.
     */
    static WCHAR *u_path = NULL;
    if (u_path == NULL) {
        WCHAR *tmpPath = NULL;
        HRESULT hr = SHGetKnownFolderPath(&FOLDERID_Profile, KF_FLAG_DONT_VERIFY, NULL, &tmpPath);

        if (FAILED(hr)) {
            CoTaskMemFree(tmpPath);
        } else {
            u_path = tmpPath;
        }
    }
    return u_path;
}

static BOOL
haveMMX(void)
{
    return IsProcessorFeaturePresent(PF_MMX_INSTRUCTIONS_AVAILABLE);
}

static const char *
cpu_isalist(void)
{
    SYSTEM_INFO info;
    GetSystemInfo(&info);
    switch (info.wProcessorArchitecture) {
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

static BOOL
SetupI18nProps(LCID lcid, char** language, char** script, char** country,
               char** variant) {
    /* script */
    char tmp[SNAMESIZE];
    *script = malloc(PROPSIZE);
    if (*script == NULL) {
        return FALSE;
    }
    if (GetLocaleInfo(lcid,
                      LOCALE_SNAME, tmp, SNAMESIZE) == 0 ||
        sscanf(tmp, "%*[a-z\\-]%1[A-Z]%[a-z]", *script, &((*script)[1])) == 0 ||
        strlen(*script) != 4) {
        (*script)[0] = '\0';
    }

    /* country */
    *country = malloc(PROPSIZE);
    if (*country == NULL) {
        return FALSE;
    }
    if (GetLocaleInfo(lcid,
                      LOCALE_SISO3166CTRYNAME, *country, PROPSIZE) == 0 &&
        GetLocaleInfo(lcid,
                      LOCALE_SISO3166CTRYNAME2, *country, PROPSIZE) == 0) {
        (*country)[0] = '\0';
    }

    /* language */
    *language = malloc(PROPSIZE);
    if (*language == NULL) {
        return FALSE;
    }
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
    if (*variant == NULL) {
        return FALSE;
    }
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

    return TRUE;
}

// GetVersionEx is deprecated; disable the warning until a replacement is found
#pragma warning(disable : 4996)
java_props_t *
GetJavaProperties(JNIEnv* env)
{
    static java_props_t sprops = {0};
    int majorVersion;
    int minorVersion;
    int buildNumber = 0;

    if (sprops.line_separator) {
        return &sprops;
    }

    /* tmp dir */
    {
        WCHAR tmpdir[MAX_PATH + 1];
        /* we might want to check that this succeed */
        GetTempPathW(MAX_PATH + 1, tmpdir);
        sprops.tmp_dir = _wcsdup(tmpdir);
    }

    /* OS properties */
    {
        char buf[100];
        BOOL is_workstation;
        BOOL is_64bit;
        DWORD platformId;
        {
            OSVERSIONINFOEX ver;
            ver.dwOSVersionInfoSize = sizeof(ver);
            GetVersionEx((OSVERSIONINFO *) &ver);
            majorVersion = ver.dwMajorVersion;
            minorVersion = ver.dwMinorVersion;
            /* distinguish Windows Server 2016+ by build number */
            buildNumber = ver.dwBuildNumber;
            is_workstation = (ver.wProductType == VER_NT_WORKSTATION);
            platformId = ver.dwPlatformId;
            sprops.patch_level = _strdup(ver.szCSDVersion);
        }

        {
            SYSTEM_INFO si;
            ZeroMemory(&si, sizeof(SYSTEM_INFO));
            GetNativeSystemInfo(&si);

            is_64bit = (si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64);
        }
        do {
            // Read the major and minor version number from kernel32.dll
            VS_FIXEDFILEINFO *file_info;
            WCHAR kernel32_path[MAX_PATH];
            DWORD version_size;
            LPTSTR version_info;
            UINT len, ret;

            // Get the full path to \Windows\System32\kernel32.dll and use that for
            // determining what version of Windows we're running on.
            len = MAX_PATH - (UINT)strlen("\\kernel32.dll") - 1;
            ret = GetSystemDirectoryW(kernel32_path, len);
            if (ret == 0 || ret > len) {
                break;
            }
            wcsncat(kernel32_path, L"\\kernel32.dll", MAX_PATH - ret);

            version_size = GetFileVersionInfoSizeW(kernel32_path, NULL);
            if (version_size == 0) {
                break;
            }

            version_info = (LPTSTR)malloc(version_size);
            if (version_info == NULL) {
                break;
            }

            if (!GetFileVersionInfoW(kernel32_path, 0, version_size, version_info)) {
                free(version_info);
                break;
            }

            if (!VerQueryValueW(version_info, L"\\", (LPVOID*)&file_info, &len)) {
                free(version_info);
                break;
            }
            majorVersion = HIWORD(file_info->dwProductVersionMS);
            minorVersion = LOWORD(file_info->dwProductVersionMS);
            buildNumber  = HIWORD(file_info->dwProductVersionLS);
            free(version_info);
        } while (0);

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
         * Windows 8                    6               2  (VER_NT_WORKSTATION)
         * Windows Server 2012          6               2  (!VER_NT_WORKSTATION)
         * Windows Server 2012 R2       6               3  (!VER_NT_WORKSTATION)
         * Windows 10                   10              0  (VER_NT_WORKSTATION)
         * Windows 11                   10              0  (VER_NT_WORKSTATION)
         *       where (buildNumber >= 22000)
         * Windows Server 2016          10              0  (!VER_NT_WORKSTATION)
         * Windows Server 2019          10              0  (!VER_NT_WORKSTATION)
         *       where (buildNumber > 17762)
         * Windows Server 2022          10              0  (!VER_NT_WORKSTATION)
         *       where (buildNumber > 20347)
         * Windows Server 2025          10              0  (!VER_NT_WORKSTATION)
         *       where (buildNumber > 26039)
         *
         * This mapping will presumably be augmented as new Windows
         * versions are released.
         */
        switch (platformId) {
        case VER_PLATFORM_WIN32_WINDOWS:
           if (majorVersion == 4) {
                switch (minorVersion) {
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
            if (majorVersion <= 4) {
                sprops.os_name = "Windows NT";
            } else if (majorVersion == 5) {
                switch (minorVersion) {
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
                    if (is_workstation && is_64bit) {
                        sprops.os_name = "Windows XP"; /* 64 bit */
                    } else {
                        sprops.os_name = "Windows 2003";
                    }
                    break;
                default: sprops.os_name = "Windows NT (unknown)"; break;
                }
            } else if (majorVersion == 6) {
                /*
                 * See table in MSDN OSVERSIONINFOEX documentation.
                 */
                if (is_workstation) {
                    switch (minorVersion) {
                    case  0: sprops.os_name = "Windows Vista";        break;
                    case  1: sprops.os_name = "Windows 7";            break;
                    case  2: sprops.os_name = "Windows 8";            break;
                    case  3: sprops.os_name = "Windows 8.1";          break;
                    default: sprops.os_name = "Windows NT (unknown)";
                    }
                } else {
                    switch (minorVersion) {
                    case  0: sprops.os_name = "Windows Server 2008";    break;
                    case  1: sprops.os_name = "Windows Server 2008 R2"; break;
                    case  2: sprops.os_name = "Windows Server 2012";    break;
                    case  3: sprops.os_name = "Windows Server 2012 R2"; break;
                    default: sprops.os_name = "Windows NT (unknown)";
                    }
                }
            } else if (majorVersion == 10) {
                if (is_workstation) {
                    switch (minorVersion) {
                    case  0:
                        /* Windows 11 21H2 (original release) build number is 22000 */
                        if (buildNumber >= 22000) {
                            sprops.os_name = "Windows 11";
                        } else {
                            sprops.os_name = "Windows 10";
                        }
                        break;
                    default: sprops.os_name = "Windows NT (unknown)";
                    }
                } else {
                    switch (minorVersion) {
                    case  0:
                        /* Windows server 2019 GA 10/2018 build number is 17763 */
                        /* Windows server 2022 build number is 20348 */
                        /* Windows server 2025 Preview build is 26040 */
                        if (buildNumber > 26039) {
                            sprops.os_name = "Windows Server 2025";
                        } else if (buildNumber > 20347) {
                            sprops.os_name = "Windows Server 2022";
                        } else if (buildNumber > 17762) {
                            sprops.os_name = "Windows Server 2019";
                        } else {
                            sprops.os_name = "Windows Server 2016";
                        }
                        break;
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
        snprintf(buf, sizeof(buf), "%d.%d", majorVersion, minorVersion);
        sprops.os_version = _strdup(buf);
#if defined(_M_AMD64)
        sprops.os_arch = "amd64";
#elif defined(_M_ARM64)
        sprops.os_arch = "aarch64";
#else
        sprops.os_arch = "unknown";
#endif
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
            DWORD buflen = 0;
            if (GetUserNameW(NULL, &buflen) == 0 &&
                GetLastError() == ERROR_INSUFFICIENT_BUFFER)
            {
                uname = (WCHAR*)malloc(buflen * sizeof(WCHAR));
                if (uname != NULL && GetUserNameW(uname, &buflen) == 0) {
                    free(uname);
                    uname = NULL;
                }
            } else {
                uname = NULL;
            }
            sprops.user_name = (uname != NULL) ? uname : L"unknown";
        }
    }

    /*
     * Home directory
     *
     * The normal result is that for a given user name XXX:
     *     On multi-user NT, user.home gets set to c:\winnt\profiles\XXX.
     *     On multi-user Win95, user.home gets set to c:\windows\profiles\XXX.
     *     On single-user Win95, user.home gets set to c:\windows.
     */
    {
        WCHAR *homep = getHomeFromShell32();
        if (homep == NULL) {
            homep = L"C:\\";
        }
        sprops.user_home = homep;
    }

    /*
     *  user.language
     *  user.script, user.country, user.variant (if user's environment specifies them)
     *  file.encoding
     */
    {
        /*
         * query the system for the current system default locale
         * (which is a Windows LCID value),
         */
        LCID userDefaultLCID = GetUserDefaultLCID();
        LANGID userDefaultUILang = GetUserDefaultUILanguage();
        LCID userDefaultUILCID = MAKELCID(userDefaultUILang, SORTIDFROMLCID(userDefaultLCID));

        {
            HANDLE hStdHandle;

            // Windows UI Language selection list only cares "language"
            // information of the UI Language. For example, the list
            // just lists "English" but it actually means "en_US", and
            // the user cannot select "en_GB" (if exists) in the list.
            // So, this hack is to use the user LCID region information
            // for the UI Language, if the "language" portion of those
            // two locales are the same.
            if (PRIMARYLANGID(LANGIDFROMLCID(userDefaultLCID)) ==
                PRIMARYLANGID(userDefaultUILang)) {
                userDefaultUILCID = userDefaultLCID;
            }

            SetupI18nProps(userDefaultLCID,
                           &sprops.format_language,
                           &sprops.format_script,
                           &sprops.format_country,
                           &sprops.format_variant);
            SetupI18nProps(userDefaultUILCID,
                           &sprops.display_language,
                           &sprops.display_script,
                           &sprops.display_country,
                           &sprops.display_variant);

            sprops.sun_jnu_encoding = getEncodingInternal(0);
            if (sprops.sun_jnu_encoding == NULL) {
                sprops.sun_jnu_encoding = "UTF-8";
            }
            sprops.encoding = sprops.sun_jnu_encoding;

            if (LANGIDFROMLCID(userDefaultLCID) == 0x0c04 && majorVersion == 6) {
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

            hStdHandle = GetStdHandle(STD_INPUT_HANDLE);
            if (hStdHandle != INVALID_HANDLE_VALUE &&
                GetFileType(hStdHandle) == FILE_TYPE_CHAR) {
                sprops.stdin_encoding = getConsoleEncoding(FALSE);
            }
            hStdHandle = GetStdHandle(STD_OUTPUT_HANDLE);
            if (hStdHandle != INVALID_HANDLE_VALUE &&
                GetFileType(hStdHandle) == FILE_TYPE_CHAR) {
                sprops.stdout_encoding = getConsoleEncoding(TRUE);
            }
            hStdHandle = GetStdHandle(STD_ERROR_HANDLE);
            if (hStdHandle != INVALID_HANDLE_VALUE &&
                GetFileType(hStdHandle) == FILE_TYPE_CHAR) {
                if (sprops.stdout_encoding != NULL)
                    sprops.stderr_encoding = sprops.stdout_encoding;
                else
                    sprops.stderr_encoding = getConsoleEncoding(TRUE);
            }
        }
    }

    sprops.unicode_encoding = "UnicodeLittle";

    /* User TIMEZONE
     * We defer setting up timezone until it's actually necessary.
     * Refer to TimeZone.getDefault(). The system property
     * is able to be set by the command line interface -Duser.timezone.
     */

    /* Current directory */
    {
        WCHAR buf[MAX_PATH];
        if (GetCurrentDirectoryW(sizeof(buf)/sizeof(WCHAR), buf) != 0)
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
    return (*env)->NewString(env, wcstr, (jsize)wcslen(wcstr));
}
