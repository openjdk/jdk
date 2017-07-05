/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <float.h>
#include "alloc.h"
#include "UnicowsLoader.h"

/*
 * Support functions for the Microsoft Layer for Unicode (MSLU).
 *
 * The MSLU maps the wide char version of Windows APIs with strings
 * to their ANSI version equivalent on Win98/ME platforms.
 *
 * For more details on the MSLU, please refer to the MSDN webpage at:
 * http://msdn.microsoft.com/library/default.asp?url=/library/en-us/mslu/winprog/microsoft_layer_for_unicode_on_windows_95_98_me_systems.asp
 */

// The MSLU module handle.  Only initialized on Win9x/ME.
HMODULE UnicowsLoader::hmodUnicows = NULL;

// MSLU loader entry point, which is called when the module
// is initialized.
extern "C" HMODULE (__stdcall *_PfnLoadUnicows)(void) =
        &UnicowsLoader::LoadUnicows;

// Overriede APIs that are not supported by MSLU.
extern "C" FARPROC Unicows_GetPrinterW =
        (FARPROC)&UnicowsLoader::GetPrinterWImpl;
extern "C" FARPROC Unicows_EnumPrintersW =
        (FARPROC)&UnicowsLoader::EnumPrintersWImpl;

HMODULE __stdcall UnicowsLoader::LoadUnicows(void)
{
    if (hmodUnicows != NULL) {
        return hmodUnicows;
    }

    // Unfortunately, some DLLs that are loaded in conjunction with
    // unicows.dll may blow the FPU's control word.  So save it here.
    unsigned int fpu_cw = _CW_DEFAULT;
    fpu_cw = _control87(0, 0);

    // Loads the DLL, assuming that the DLL resides in the same directory
    // as the AWT(_G).DLL.  We cannot use "sun.boot.library.path" system
    // property since there is no way to issue JNI calls at this point
    // (JNI_OnLoad is not yet called so it cannot obtain JavaVM structure)
    //
    // To obtain the AWT module handle, call GetModuleHandleA() directly,
    // instead of AwtToolkit.GetModuleHandle().  Otherwise it could cause
    // an infinite loop if some W call were made inside AwtToolkit class
    // initialization.
    HMODULE hmodAWT = GetModuleHandleA("awt");
    LPSTR abspath = (LPSTR)safe_Malloc(MAX_PATH);
    if (abspath != NULL) {
        GetModuleFileNameA(hmodAWT, abspath, MAX_PATH);
        *strrchr(abspath, '\\') = '\0';
        strcat(abspath, "\\unicows.dll");
        hmodUnicows = LoadLibraryA(abspath);
        free(abspath);
    }

    // Restore the FPU control word if needed.
    if ( _control87(0, 0) != fpu_cw) {
        _control87(fpu_cw, 0xfffff);
    }

    return hmodUnicows;
}

HMODULE UnicowsLoader::GetModuleHandle(void)
{
    return hmodUnicows;
}


// Convenient functions to convert DEVMODEA -> DEVMODEW
void UnicowsLoader::DevModeA2DevModeW(
    const DEVMODEA * dma,
    DEVMODEW * dmw)
{
    // convert string portions
    ::MultiByteToWideChar(CP_ACP, 0, (CHAR *)dma->dmDeviceName, CCHDEVICENAME,
        dmw->dmDeviceName, CCHDEVICENAME);
    ::MultiByteToWideChar(CP_ACP, 0, (CHAR *)dma->dmFormName, CCHDEVICENAME,
        dmw->dmFormName, CCHDEVICENAME);

    // copy driver specific data if exists
    if (dma->dmDriverExtra != 0) {
        PBYTE pExtraA = (PBYTE)(dma + 1);
        PBYTE pExtraW = (PBYTE)(dmw + 1);
        memcpy(pExtraW, pExtraA, dma->dmDriverExtra);
    }

    // copy normal struct members
    dmw->dmSpecVersion = dma->dmSpecVersion;
    dmw->dmDriverVersion = dma->dmDriverVersion;
    dmw->dmSize = dma->dmSize;
    dmw->dmDriverExtra = dma->dmDriverExtra;
    dmw->dmFields = dma->dmFields;
    dmw->dmPosition = dma->dmPosition;
    dmw->dmScale = dma->dmScale;
    dmw->dmCopies = dma->dmCopies;
    dmw->dmDefaultSource = dma->dmDefaultSource;
    dmw->dmPrintQuality = dma->dmPrintQuality;
    dmw->dmColor = dma->dmColor;
    dmw->dmDuplex = dma->dmDuplex;
    dmw->dmYResolution = dma->dmYResolution;
    dmw->dmTTOption = dma->dmTTOption;
    dmw->dmCollate = dma->dmCollate;
    dmw->dmLogPixels = dma->dmLogPixels;
    dmw->dmBitsPerPel = dma->dmBitsPerPel;
    dmw->dmPelsWidth = dma->dmPelsWidth;
    dmw->dmPelsHeight = dma->dmPelsHeight;
    dmw->dmDisplayFlags = dma->dmDisplayFlags;
    dmw->dmDisplayFrequency = dma->dmDisplayFrequency;
#if(WINVER >= 0x0400)
    dmw->dmICMMethod = dma->dmICMMethod;
    dmw->dmICMIntent = dma->dmICMIntent;
    dmw->dmMediaType = dma->dmMediaType;
    dmw->dmDitherType = dma->dmDitherType;
    dmw->dmReserved1 = dma->dmReserved1;
    dmw->dmReserved2 = dma->dmReserved2;
#if (WINVER >= 0x0500) || (_WIN32_WINNT >= 0x0400)
    dmw->dmPanningWidth = dma->dmPanningWidth;
    dmw->dmPanningHeight = dma->dmPanningHeight;
#endif
#endif /* WINVER >= 0x0400 */
}

// PRINTER_INFO_1 struct converter
void UnicowsLoader::PrinterInfo1A2W(
    const LPPRINTER_INFO_1A pi1A,
    LPPRINTER_INFO_1W pi1W,
    const DWORD num)
{
    LPWSTR pwstrbuf = (LPWSTR)(pi1W + num);
    DWORD current;

    // loop through all structures
    for (current = 0; current < num; current ++) {
        LPPRINTER_INFO_1A curPi1A = pi1A + current;
        LPPRINTER_INFO_1W curPi1W = pi1W + current;

        // copy the structure itself
        memcpy(curPi1W, curPi1A, sizeof(_PRINTER_INFO_1W));

        // copy string members
        StringA2W(curPi1A->pDescription, &(curPi1W->pDescription), &pwstrbuf);
        StringA2W(curPi1A->pName, &(curPi1W->pName), &pwstrbuf);
        StringA2W(curPi1A->pComment, &(curPi1W->pComment), &pwstrbuf);
    }
}

// PRINTER_INFO_2 struct converter
void UnicowsLoader::PrinterInfo2A2W(
    const LPPRINTER_INFO_2A pi2A,
    LPPRINTER_INFO_2W pi2W,
    const DWORD num)
{
    PBYTE pbytebuf = (PBYTE)(pi2W + num);
    DWORD current;

    // loop through all structures
    for (current = 0; current < num; current ++) {
        LPPRINTER_INFO_2A curPi2A = pi2A + current;
        LPPRINTER_INFO_2W curPi2W = pi2W + current;
        // copy the structure itself
        memcpy(curPi2W, curPi2A, sizeof(_PRINTER_INFO_2W));

        // copy string members
        StringA2W(curPi2A->pServerName, &(curPi2W->pServerName), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pPrinterName, &(curPi2W->pPrinterName), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pShareName, &(curPi2W->pShareName), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pPortName, &(curPi2W->pPortName), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pDriverName, &(curPi2W->pDriverName), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pComment, &(curPi2W->pComment), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pLocation, &(curPi2W->pLocation), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pSepFile, &(curPi2W->pSepFile), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pPrintProcessor, &(curPi2W->pPrintProcessor), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pDatatype, &(curPi2W->pDatatype), (LPWSTR *)&pbytebuf);
        StringA2W(curPi2A->pParameters, &(curPi2W->pParameters), (LPWSTR *)&pbytebuf);

        // copy DEVMODE structure
        if (curPi2A->pDevMode != NULL) {
            curPi2W->pDevMode = (LPDEVMODEW)pbytebuf;
            DevModeA2DevModeW(curPi2A->pDevMode, curPi2W->pDevMode);
            pbytebuf += sizeof(DEVMODEW) + curPi2A->pDevMode->dmDriverExtra;
        }
    }
}

// PRINTER_INFO_5 struct converter
void UnicowsLoader::PrinterInfo5A2W(
    const LPPRINTER_INFO_5A pi5A,
    LPPRINTER_INFO_5W pi5W,
    const DWORD num)
{
    LPWSTR pbuf = (LPWSTR)(pi5W + num);
    DWORD current;

    // loop through all structures
    for (current = 0; current < num; current ++) {
        LPPRINTER_INFO_5A curPi5A = pi5A + current;
        LPPRINTER_INFO_5W curPi5W = pi5W + current;

        // copy the structure itself
        memcpy(curPi5W, curPi5A, sizeof(_PRINTER_INFO_5W));

        // copy string members
        StringA2W(curPi5A->pPrinterName, &(curPi5W->pPrinterName), &pbuf);
        StringA2W(curPi5A->pPortName, &(curPi5W->pPortName), &pbuf);
    }
}

// PRINTER_INFO_* struct converter.  Supported levels are 1, 2, and 5.
void UnicowsLoader::PrinterInfoA2W(
    const PVOID piA,
    PVOID piW,
    const DWORD Level,
    const DWORD num)
{
    switch (Level) {
    case 1:
        PrinterInfo1A2W((LPPRINTER_INFO_1A)piA, (LPPRINTER_INFO_1W)piW, num);
        break;

    case 2:
        PrinterInfo2A2W((LPPRINTER_INFO_2A)piA, (LPPRINTER_INFO_2W)piW, num);
        break;

    case 5:
        PrinterInfo5A2W((LPPRINTER_INFO_5A)piA, (LPPRINTER_INFO_5W)piW, num);
        break;
    }
}

// converts string members in PRINTER_INFO_* struct.
void UnicowsLoader::StringA2W(
    LPCSTR pSrcA,
    LPWSTR * ppwstrDest,
    LPWSTR * ppwstrbuf)
{
    if (pSrcA != NULL) {
        DWORD cchWideChar = ::MultiByteToWideChar(CP_ACP, 0, pSrcA, -1, NULL, 0);
        *ppwstrDest = *ppwstrbuf;
        ::MultiByteToWideChar(CP_ACP, 0, pSrcA, -1, *ppwstrbuf, cchWideChar);
        *ppwstrbuf += cchWideChar;
    } else {
        *ppwstrDest = NULL;
    }
}

// GetPrinterW implementation.  Level 1, 2, and 5 are the only supported levels.
BOOL __stdcall UnicowsLoader::GetPrinterWImpl(
    HANDLE  hPrinter,
    DWORD   Level,
    LPBYTE  pPrinter,
    DWORD   cbBuf,
    LPDWORD pcbNeeded)
{
    if ((Level != 1) && (Level != 2) && (Level != 5)) {
        SetLastError(ERROR_CALL_NOT_IMPLEMENTED);
        return FALSE;
    }

    DWORD cbBufA = (cbBuf != 0 ? cbBuf / 2 : 0); // dirty estimation...
    LPBYTE pPrinterA = NULL;
    DWORD cbNeededA = 0;
    BOOL ret;

    if (cbBufA != 0) {
        pPrinterA = (LPBYTE)safe_Malloc(cbBufA);
        memset(pPrinterA, 0, cbBufA);
    }

    ret = ::GetPrinterA(hPrinter, Level, pPrinterA, cbBufA, &cbNeededA);
    *pcbNeeded = cbNeededA * 2; // dirty estimation...

    if (pPrinterA != NULL) {
        if (ret) {
            PrinterInfoA2W(pPrinterA, pPrinter, Level, 1);
        }
        free(pPrinterA);
    }

    return ret;
}

// EnumPrintersW implementation.  Level 1, 2, and 5 are the only supported levels.
BOOL __stdcall UnicowsLoader::EnumPrintersWImpl(
    DWORD   Flags,
    LPWSTR Name,
    DWORD   Level,
    LPBYTE  pPrinterEnum,
    DWORD   cbBuf,
    LPDWORD pcbNeeded,
    LPDWORD pcReturned)
{
    if ((Level != 1) && (Level != 2) && (Level != 5)) {
        SetLastError(ERROR_CALL_NOT_IMPLEMENTED);
        return FALSE;
    }

    LPSTR pNameA = NULL;
    DWORD cbBufA = (cbBuf != 0 ? cbBuf / 2 : 0); // dirty estimation...
    LPBYTE pPrinterEnumA = NULL;
    DWORD cbNeededA = 0;
    BOOL ret;

    if (Name != NULL) {
        DWORD len = static_cast<DWORD>(wcslen(Name)) + 1;
        pNameA = (LPSTR)safe_Malloc(len);
        ::WideCharToMultiByte(CP_ACP, 0, Name, -1, pNameA, len, NULL, NULL);
    }

    if (cbBufA != 0) {
        pPrinterEnumA = (LPBYTE)safe_Malloc(cbBufA);
        memset(pPrinterEnumA, 0, cbBufA);
    }

    ret = ::EnumPrintersA(Flags, pNameA, Level, pPrinterEnumA,
                        cbBufA, &cbNeededA, pcReturned);
    *pcbNeeded = cbNeededA * 2; // dirty estimation...

    if (pPrinterEnumA != NULL) {
        if (ret) {
            PrinterInfoA2W(pPrinterEnumA, pPrinterEnum, Level, *pcReturned);
        }
        free(pPrinterEnumA);
    }

    if (pNameA != NULL) {
        free(pNameA);
    }

    return ret;
}

// wchar CRT implementations that VC6 does not support on Win9x.
// These implementations are used on both Win9x/ME *and* WinNT/2K/XP.
#undef _waccess
#undef _wchmod
#undef _wfullpath
#undef _wremove
#undef _wrename
#undef _wstat
#undef _wstati64
#undef _wstat64
#undef _wunlink
#undef _wfopen
#undef _wfreopen
#undef _wfsopen
#undef _wcreat
#undef _wopen
#undef _wsopen
#undef _wfindfirst
#undef _wfindfirst64
#undef _wfindnext
#undef _wfindnext64
#undef _wsystem
#undef _wexcel
#undef _wexcele
#undef _wexelp
#undef _wexelpe
#undef _wexecv
#undef _wexecve
#undef _wexecvp
#undef _wexecvpe
#undef _wpopen
#undef _wputenv
#undef _wspawnl
#undef _wspawnle
#undef _wspawnlp
#undef _wspawnlpe
#undef _wspawnv
#undef _wspawnve
#undef _wspawnvp
#undef _wspawnvpe

// _wfullpath implementation
wchar_t * __cdecl UnicowsLoader::_wfullpathImpl(
    wchar_t * absPath,
    const wchar_t * relPath,
    size_t maxLength)
{
    if (IS_NT) {
        return _wfullpath(absPath, relPath, maxLength);
    } else {
        wchar_t * ret = NULL;
        char * absPathA = (char *)safe_Malloc(maxLength);
        char * relPathA = (char *)safe_Malloc(maxLength);
        ::WideCharToMultiByte(CP_ACP, 0, relPath, -1, relPathA,
            static_cast<DWORD>(maxLength), NULL, NULL);

        char * retA = _fullpath(absPathA, relPathA, maxLength);

        if (retA != NULL) {
            ::MultiByteToWideChar(CP_ACP, 0, absPathA, -1,
                absPath, static_cast<DWORD>(maxLength));
            ret = absPath;
        }

        free(absPathA);
        free(relPathA);

        return ret;
    }
}
