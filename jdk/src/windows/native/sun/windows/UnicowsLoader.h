/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef UNICOWSLOADER_H
#define UNICOWSLOADER_H

#if !defined(UNICODE) || !defined(_UNICODE)
#error UnicowsLoader module needs UNICODE and _UNICODE flags to be set on compiling
#endif

#include <winspool.h>

// A class to load the Microsoft Layer for Unicode (unicows.dll)
class UnicowsLoader {
public:
    // this is called when the client DLL (this case, AWT) is loaded
    static HMODULE __stdcall LoadUnicows(void);

    // this is provided to pass the MSLU module handle
    static HMODULE GetModuleHandle(void);

    // member functions that implements functions that MSLU does not support
    static BOOL __stdcall GetPrinterWImpl(HANDLE, DWORD, LPBYTE, DWORD, LPDWORD);
    static BOOL __stdcall EnumPrintersWImpl(DWORD, LPWSTR, DWORD, LPBYTE,
                        DWORD, LPDWORD, LPDWORD);

    // member functions that implements functions that VC6 CRT does not support
    // on Win9x
    static wchar_t * __cdecl _wfullpathImpl(wchar_t *, const wchar_t *, size_t);

private:
    // The module handle
    static HMODULE hmodUnicows;

    // utility member functions
    static void DevModeA2DevModeW(const DEVMODEA *, DEVMODEW *);
    static void PrinterInfo1A2W(const LPPRINTER_INFO_1A, LPPRINTER_INFO_1W, const DWORD);
    static void PrinterInfo2A2W(const LPPRINTER_INFO_2A, LPPRINTER_INFO_2W, const DWORD);
    static void PrinterInfo5A2W(const LPPRINTER_INFO_5A, LPPRINTER_INFO_5W, const DWORD);
    static void PrinterInfoA2W(const PVOID, PVOID, DWORD, DWORD);
    static void StringA2W(LPCSTR, LPWSTR *, LPWSTR *);
};

#ifndef AWT_H
// copied from awt.h
#if defined (WIN32)
    #define IS_WIN32 TRUE
#else
    #define IS_WIN32 FALSE
#endif
#define IS_NT      (IS_WIN32 && !(::GetVersion() & 0x80000000))
#endif // AWT_H

// Now the platform encoding is Unicode (UTF-16), re-define JNU_ functions
// to proper JNI functions.
#define JNU_NewStringPlatform(env, x) env->NewString(x, static_cast<jsize>(_tcslen(x)))
#define JNU_GetStringPlatformChars(env, x, y) (LPWSTR)env->GetStringChars(x, y)
#define JNU_ReleaseStringPlatformChars(env, x, y) env->ReleaseStringChars(x, y)

// The following Windows W-APIs are not supported by the MSLU.
// You need to implement a stub to use these APIs. Or, if it is
// apparent that the API is used only on WindowsNT/2K/XP, wrap
// the call site with #undef - #define, e.g:
//
// #undef SomeFunctionW
// call SomeFunctionW
// #define SomeFunctionW NotSupportedByMSLU

#define AcquireCredentialsHandleW               NotSupportedByMSLU
#define CreateNamedPipeW                        NotSupportedByMSLU
#define CryptAcquireContextW                    NotSupportedByMSLU
#define CryptEnumProvidersW                     NotSupportedByMSLU
#define CryptEnumProviderTypesW                 NotSupportedByMSLU
#define CryptGetDefaultProviderW                NotSupportedByMSLU
#define CryptSetProviderW                       NotSupportedByMSLU
#define CryptSetProviderExW                     NotSupportedByMSLU
#define CryptSignHashW                          NotSupportedByMSLU
#define CryptVerifySignatureW                   NotSupportedByMSLU
#define EnumerateSecurityPackagesW              NotSupportedByMSLU
#define EnumMonitorsW                           NotSupportedByMSLU
#define EnumPortsW                              NotSupportedByMSLU
#define EnumPrinterDriversW                     NotSupportedByMSLU
//#define EnumPrintersW                         NotSupportedByMSLU
#define EnumPrintProcessorDatatypesW            NotSupportedByMSLU
#define EnumPrintProcessorsW                    NotSupportedByMSLU
#define FreeContextBufferW                      NotSupportedByMSLU
#define GetCharABCWidthsFloatW                  NotSupportedByMSLU
#define GetJobW                                 NotSupportedByMSLU
#define GetOpenFileNamePreviewW                 NotSupportedByMSLU
//#define GetPrinterW                           NotSupportedByMSLU
#define GetPrinterDataW                         NotSupportedByMSLU
#define GetPrinterDriverW                       NotSupportedByMSLU
#define GetSaveFileNamePreviewW                 NotSupportedByMSLU
#define InitializeSecurityContextW              NotSupportedByMSLU
#define mciSendCommandW                         NotSupportedByMSLU
#define mixerGetControlDetailsW                 NotSupportedByMSLU
#define mixerGetLineControlsW                   NotSupportedByMSLU
#define mixerGetLineInfoW                       NotSupportedByMSLU
#define mmioInstallIOProcW                      NotSupportedByMSLU
#define OleUIChangeSourceW                      NotSupportedByMSLU
#define OleUIConvertW                           NotSupportedByMSLU
#define OleUIEditLinksW                         NotSupportedByMSLU
#define OleUIInsertObjectW                      NotSupportedByMSLU
#define OleUIObjectPropertiesW                  NotSupportedByMSLU
#define OleUIPasteSpecialW                      NotSupportedByMSLU
#define OleUIPromptUserW                        NotSupportedByMSLU
#define OleUIUpdateLinksW                       NotSupportedByMSLU
#define PolyTextOutW                            NotSupportedByMSLU
#define QueryContextAttributesW                 NotSupportedByMSLU
#define QueryCredentialsAttributesW             NotSupportedByMSLU
#define QuerySecurityPackageInfoW               NotSupportedByMSLU
#define RasDeleteSubEntryW                      NotSupportedByMSLU
#define RasSetSubEntryPropertiesW               NotSupportedByMSLU
#define ResetPrinterW                           NotSupportedByMSLU

// The following Shell COM interfaces are not supported by the MSLU.
// See ShellFolder2.cpp
#define IID_IFileViewerW                        NotSupportedByMSLU
#define IID_IShellLinkW                         NotSupportedByMSLU
#define IID_IExtractIconW                       NotSupportedByMSLU
#define IID_IShellCopyHookW                     NotSupportedByMSLU
#define IID_IShellExecuteHookW                  NotSupportedByMSLU
#define IID_INewShortcutHookW                   NotSupportedByMSLU

// The following CRT functions should fail on compiling, as it does not work on
// Win9x/ME platform.  If you need these CRTs, write a wrapper for ANSI version
// equivalents, in which it converts to/from Unicode using WideCharToMultiByte.
//
// Or, if it is apparent that the function is used only on WindowsNT/2K/XP, wrap
// the call site with #undef - #define, e.g:
//
// #undef _wsomefunc
// call _wsomefunc
// #define _wsomefunc NotSupportedOnWin9X

#define _waccess        NotSupportedOnWin9X
#define _wchmod         NotSupportedOnWin9X
#define _wfullpath      UnicowsLoader::_wfullpathImpl
#define _wremove        NotSupportedOnWin9X
#define _wrename        NotSupportedOnWin9X
#define _wstat          NotSupportedOnWin9X
#define _wstati64       NotSupportedOnWin9X
#define _wstat64        NotSupportedOnWin9X
#define _wunlink        NotSupportedOnWin9X
#define _wfopen         NotSupportedOnWin9X
#define _wfreopen       NotSupportedOnWin9X
#define _wfsopen        NotSupportedOnWin9X
#define _wcreat         NotSupportedOnWin9X
#define _wopen          NotSupportedOnWin9X
#define _wsopen         NotSupportedOnWin9X
#define _wfindfirst     NotSupportedOnWin9X
#define _wfindfirst64   NotSupportedOnWin9X
#define _wfindnext      NotSupportedOnWin9X
#define _wfindnext64    NotSupportedOnWin9X
#define _wsystem        NotSupportedOnWin9X
#define _wexcel         NotSupportedOnWin9X
#define _wexcele        NotSupportedOnWin9X
#define _wexelp         NotSupportedOnWin9X
#define _wexelpe        NotSupportedOnWin9X
#define _wexecv         NotSupportedOnWin9X
#define _wexecve        NotSupportedOnWin9X
#define _wexecvp        NotSupportedOnWin9X
#define _wexecvpe       NotSupportedOnWin9X
#define _wpopen         NotSupportedOnWin9X
#define _wputenv        NotSupportedOnWin9X
#define _wspawnl        NotSupportedOnWin9X
#define _wspawnle       NotSupportedOnWin9X
#define _wspawnlp       NotSupportedOnWin9X
#define _wspawnlpe      NotSupportedOnWin9X
#define _wspawnv        NotSupportedOnWin9X
#define _wspawnve       NotSupportedOnWin9X
#define _wspawnvp       NotSupportedOnWin9X
#define _wspawnvpe      NotSupportedOnWin9X


#endif // UNICOWSLOADER_H
