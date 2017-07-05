/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt.h"
#include "awt_dlls.h"

/*
 * To reduce memory footprint we don't statically link to COMDLG32.DLL
 * and SHELL32.  Instead we programatically load them only when they are
 * needed.
 */

PrintDlgType AwtCommDialog::do_print_dlg;
PageSetupDlgType AwtCommDialog::do_page_setup_dlg;
GetOpenFileNameType AwtCommDialog::get_open_file_name;
GetSaveFileNameType AwtCommDialog::get_save_file_name;
GetExtendedErrorType AwtCommDialog::get_dlg_extended_error;

/***********************************************************************/

DWORD
AwtCommDialog::CommDlgExtendedError(VOID) {
    AwtCommDialog::load_comdlg_procs();
    return static_cast<DWORD>(reinterpret_cast<INT_PTR>(
        AwtToolkit::GetInstance().
        InvokeFunction(reinterpret_cast<void *(*)(void)>
            (AwtCommDialog::GetExtendedErrorWrapper))));
}

BOOL
AwtCommDialog::PrintDlg(LPPRINTDLG data) {
    AwtCommDialog::load_comdlg_procs();
    return static_cast<BOOL>(reinterpret_cast<INT_PTR>(
        AwtToolkit::GetInstance().
        InvokeFunction(reinterpret_cast<void *(*)(void *)>
            (AwtCommDialog::PrintDlgWrapper), data)));
}

BOOL
AwtCommDialog::PageSetupDlg(LPPAGESETUPDLG data) {
    AwtCommDialog::load_comdlg_procs();
    return static_cast<BOOL>(reinterpret_cast<INT_PTR>(
        AwtToolkit::GetInstance().
        InvokeFunction(reinterpret_cast<void *(*)(void *)>
            (AwtCommDialog::PageSetupDlgWrapper), data)));
}

/*
 * Load the COMDLG32.dll and get pointers to various procedures.
 */

void
AwtCommDialog::load_comdlg_procs()
{
    static int initialized = 0;
    HMODULE lib = NULL;
    if (initialized) {
        return;
    }
    lib = LoadLibrary(TEXT("COMDLG32.DLL"));
    HMODULE libUnicows = UnicowsLoader::GetModuleHandle();
    do_print_dlg = (PrintDlgType)GetProcAddress(IS_WIN95 ? libUnicows : lib, "PrintDlgW");
    do_page_setup_dlg = (PageSetupDlgType)GetProcAddress(IS_WIN95 ? libUnicows : lib, "PageSetupDlgW");
    get_open_file_name = (GetOpenFileNameType)GetProcAddress(IS_WIN95 ? libUnicows : lib, "GetOpenFileNameW");
    get_save_file_name = (GetSaveFileNameType)GetProcAddress(IS_WIN95 ? libUnicows : lib, "GetSaveFileNameW");
    get_dlg_extended_error = (GetExtendedErrorType)GetProcAddress(lib, "CommDlgExtendedError");
    initialized = 1;
}

/***********************************************************************/

DragQueryFileType do_drag_query_file;
GetPathFromIDListType get_path_from_idlist;

/*
 * Load the SHELL32.dll and get pointers to various procedures.
 */

void
load_shell_procs()
{
    static int initialized = 0;
    HMODULE lib = NULL;
    if (initialized) {
        return;
    }

    if (IS_WIN95) {
        lib = UnicowsLoader::GetModuleHandle();
    } else {
        lib = LoadLibrary(TEXT("SHELL32.DLL"));
    }

    do_drag_query_file = (DragQueryFileType)GetProcAddress(lib, "DragQueryFileW");
    get_path_from_idlist = (GetPathFromIDListType)GetProcAddress(lib,
                "SHGetPathFromIDListW");
    initialized = 1;
}

/***********************************************************************/

AnimateWindowType fn_animate_window;
ChangeDisplaySettingsExType fn_change_display_settings_ex;

/*
 * Load the USER32.dll and get pointers to various procedures.
 */

void
load_user_procs()
{
    static int initialized = 0;
    HMODULE lib = NULL;
    if (initialized) {
        return;
    }
    lib = LoadLibrary(TEXT("USER32.DLL"));
    HMODULE libUnicows = UnicowsLoader::GetModuleHandle();
    fn_animate_window = (AnimateWindowType)GetProcAddress(lib, "AnimateWindow");
    fn_change_display_settings_ex = (ChangeDisplaySettingsExType)
        GetProcAddress(IS_WIN95 ? libUnicows : lib, "ChangeDisplaySettingsExW");
    initialized = 1;
}

/***********************************************************************/

GetFileVersionInfoSizeType get_file_version_info_size;
GetFileVersionInfoType get_file_version_info;
VerQueryValueType do_ver_query_value;

/*
 * Load the VERSION.dll and get pointers to various procedures.
 */

void
load_version_procs()
{
    static int initialized = 0;
    HMODULE lib = NULL;
    if (initialized) {
        return;
    }

    if (IS_WIN95) {
        lib = UnicowsLoader::GetModuleHandle();
    } else {
        lib = LoadLibrary(TEXT("VERSION.DLL"));
    }

    get_file_version_info_size =
        (GetFileVersionInfoSizeType)GetProcAddress(lib, "GetFileVersionInfoSizeW");
    get_file_version_info =
        (GetFileVersionInfoType)GetProcAddress(lib, "GetFileVersionInfoW");
    do_ver_query_value =
        (VerQueryValueType)GetProcAddress(lib, "VerQueryValueW");
    initialized = 1;
}

/***********************************************************************/

#define MAX_KNOWN_VERSION 4

/*
 * We are going to use an undocumented procedure RSRC32.DLL.
 * The safest will be to use it only for a finite set of known versions.
 */

DWORD known_versions[MAX_KNOWN_VERSION][2] = {
    { 0x00040000, 0x000003B6 },    // WIN95\RETAIL
                                   // WIN95\RETAIL\UPGRADE
                                   // WIN95\OSR2
                                   // WIN95\OSR25
                                   // WIN95\international\RETAIL
                                   // WIN95\international\OSR2

    { 0x0004000A, 0x00000672 },    // WIN98\international\win98beta3

    { 0x0004000A, 0x000007CE },    // WIN98\RETAIL
                                   // WIN98\international\RETAIL
                                   // WIN98\SE

    { 0x0004005A, 0x00000BB8 }     // WIN98ME
};

GetFreeSystemResourcesType get_free_system_resources = NULL;

/*
 * Load the RSRC32.dll, check that it is a known version
 * and get the pointer to the undocumented procedure.
 */

void
load_rsrc32_procs()
{
    static int initialized = 0;
    if (initialized) {
        return;
    }
    if (IS_NT) {
        // 4310028: Only load library on non-NT systems. The load
        // will always fail anyways. However, if a Win 9x OS is
        // also installed on the system, and the user's path
        // includes C:\WINDOWS\SYSTEM, or the equivalent, a really
        // ugly and annoying warning dialog will appear.
        initialized = 1;
        return;
    }
    HMODULE lib = LoadLibrary(TEXT("RSRC32.DLL"));
    if (lib != NULL) {
        TCHAR     szFullPath[_MAX_PATH];
        DWORD     dwVerHnd = 0;
        DWORD     dwVersionInfoSize;
        LPBYTE    lpVersionInfo;
        LPVOID    lpBuffer;
        UINT      uLength = 0;

        /*
         * We use undocumented procedure exported by RSRC32.DLL, so the
         * safest will be to check the library's version and only attempt
         * to get the procedure address if it's a known version.
         */
        if (::GetModuleFileName(lib, szFullPath, sizeof(szFullPath))) {
            load_version_procs();
            dwVersionInfoSize = (*get_file_version_info_size)(szFullPath, &dwVerHnd);
            if (dwVersionInfoSize) {
                lpVersionInfo = new BYTE[dwVersionInfoSize];
                (*get_file_version_info)(szFullPath, dwVerHnd,
                                         dwVersionInfoSize, lpVersionInfo);
                if ((*do_ver_query_value)(lpVersionInfo, TEXT("\\"), &lpBuffer, &uLength)) {
                    VS_FIXEDFILEINFO *lpvsFixedFileInfo = (VS_FIXEDFILEINFO *)lpBuffer;
                    DWORD dwFileVersionMS = lpvsFixedFileInfo->dwFileVersionMS;
                    DWORD dwFileVersionLS = lpvsFixedFileInfo->dwFileVersionLS;
                    for (int i = 0; i < MAX_KNOWN_VERSION; i++) {
                        if ((known_versions[i][0] == dwFileVersionMS) &&
                            (known_versions[i][1] == dwFileVersionLS)) {
                            get_free_system_resources =
                                (GetFreeSystemResourcesType)
                                ::GetProcAddress(lib, "_MyGetFreeSystemResources32@4");
                            break;
                        }
                    }
                }
                delete[] lpVersionInfo;
            }
        }
    }
    initialized = 1;
}

void
load_rich_edit_library() {
    static int initialized = 0;
    BOOL isRichEdit32Needed = IS_WIN95 && !IS_WIN98;

    if (initialized) {
        return;
    }

    HMODULE lib = NULL;
    if (isRichEdit32Needed) {
        lib = ::LoadLibrary(TEXT("RICHED32.DLL"));
    } else {
        lib = ::LoadLibrary(TEXT("RICHED20.DLL"));
    }
    if (lib == NULL) {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        JNU_ThrowInternalError(env, "Can't load a rich edit DLL");
    } else if (isRichEdit32Needed) {
        // Richedit language checking logic is needed for RICHED32.DLL only.
        LPTSTR    szFullPath = new TCHAR[_MAX_PATH];
        DWORD     dwVerHnd = 0;
        DWORD     dwVersionInfoSize;
        LPVOID    lpVersionInfo;
        UINT      uLength = 0;
        struct LANGANDCODEPAGE {
            WORD wLanguage;
            WORD wCodePage;
        } *lpTranslate;

        try {
            if (!IS_WIN2000 && ::GetModuleFileName(lib, szFullPath, _MAX_PATH)) {
                load_version_procs();
                dwVersionInfoSize = (*get_file_version_info_size)(szFullPath, &dwVerHnd);
                if (dwVersionInfoSize) {
                    lpVersionInfo = new BYTE[dwVersionInfoSize];
                    try {
                        if ((*get_file_version_info)(szFullPath,
                                                     dwVerHnd,
                                                     dwVersionInfoSize,
                                                     lpVersionInfo)
                            && (*do_ver_query_value)(lpVersionInfo,
                                                     TEXT("\\VarFileInfo\\Translation"),
                                                     (LPVOID*)&lpTranslate,
                                                     &uLength)) {

                            if (::GetSystemMetrics(SM_DBCSENABLED)
                                && LANGIDFROMLCID(::GetThreadLocale()) != lpTranslate[0].wLanguage) {

                                JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
                                if (env->PushLocalFrame(6) >= 0) {
                                    jstring keystr = env->NewStringUTF("AWT.InconsistentDLLsWarning");
                                    jstring defstr = env->NewStringUTF(
"Text based operations may not work correctly due to \
an inconsistent set of dynamic linking libraries (DLLs) installed on your \
system. For more information on this problem and a suggested workaround \
please see the Java(TM) 2 SDK, Standard Edition Release Notes \
on java.sun.com.");

                                    jstring retstr =
                                        (jstring) JNU_CallStaticMethodByName(
                                            env,
                                            NULL,
                                            "java/awt/Toolkit",
                                            "getProperty",
                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                            keystr,
                                            defstr).l;

                                    jboolean exception;
                                    jstring pkgstr = env->NewStringUTF("java.awt");
                                    jobject logger =
                                        (jobject) JNU_CallStaticMethodByName(
                                            env,
                                            &exception,
                                            "java/util/logging/Logger",
                                            "getLogger",
                                            "(Ljava/lang/String;)Ljava/util/logging/Logger;",
                                            pkgstr).l;

                                    jstring msgstr = (retstr) ? retstr : defstr;
                                    if (!exception) {
                                        JNU_CallMethodByName(
                                            env,
                                            NULL,
                                            logger,
                                            "warning",
                                            "(Ljava/lang/String;)V",
                                            msgstr);
                                    } else {
                                        LPCTSTR outstr = JNU_GetStringPlatformChars(env, msgstr, NULL);
                                        _ftprintf(stdout, TEXT("\nWARNING: %s\n"), outstr);
                                        fflush(stdout);
                                        JNU_ReleaseStringPlatformChars(env, msgstr, outstr);
                                    }

                                    env->PopLocalFrame(NULL);
                                }
                            }
                        }
                    } catch (...) {
                        delete[] lpVersionInfo;
                        throw;
                    }
                    delete[] lpVersionInfo;
                }
            }
        } catch (...) {
            delete[] szFullPath;
            throw;
        }
        delete[] szFullPath;
    }
    initialized = 1;
}

/***********************************************************************/

bool AwtWinMM::initialized = false;
AwtWinMM::PlaySoundWFunc* AwtWinMM::playSoundFunc = NULL;

BOOL AwtWinMM::PlaySoundWrapper(LPCTSTR pszSound, HMODULE hmod, DWORD fdwSound) {
    load_winmm_procs();
    if (playSoundFunc == NULL) {
        return FALSE;
    }
    return (*playSoundFunc)(pszSound, hmod, fdwSound);
}

void AwtWinMM::load_winmm_procs() {
    if (initialized) {
        return;
    }
    HMODULE dll = NULL;

    if (IS_WIN95) {
        dll = UnicowsLoader::GetModuleHandle();
    } else {
        dll = ::LoadLibrary(TEXT("winmm.dll"));
    }

    if (dll == NULL) {
        return;
    }
    playSoundFunc =
        (PlaySoundWFunc*) GetProcAddress(dll, "PlaySoundW");
    if (playSoundFunc == NULL) {
        return;
    }
    initialized = true;
}
