/*
 * Copyright 2008 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#define STRICT
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0400
#endif
#define _ATL_APARTMENT_THREADED

#include <atlbase.h>
//You may derive a class from CComModule and use it if you want to override
//something, but do not change the name of _Module
extern CComModule _Module;
#include <atlcom.h>
#include <atlwin.h>

#include <atlhost.h>
#include <commdlg.h>
#include <commctrl.h>
#include <windowsx.h>
#include <urlmon.h>
#include <wininet.h>
#include <shellapi.h>
#include <time.h>
#include <math.h>
#include <stdio.h>
#include <jni.h>

#include "DownloadDialog.h"

#define UPDATE_INTERVAL 500
#define INITIAL_DELAY 2000
#define POST_DELAY 1000

/////////////////////////////////////////////////////////////////////////////
// CDownloadDialog

typedef BOOL (WINAPI * InitCommonControlsType)();

CDownloadDialog::CDownloadDialog()
{
    m_numDownloadThreadsRunning = 0;

    m_destroyWindowTimerStarted = FALSE;
    m_pszFileName = NULL;
    m_jvm = NULL;

    m_ulProgress = 0;
    m_ulProgressMax = 0;
    m_iProgressFactor = 0;
    m_iMaxProgressFactor = 1;


    m_hCancelEvent = ::CreateEvent(NULL, TRUE, FALSE, NULL);
    m_hDownloadThreadExitEvent = ::CreateEvent(NULL, TRUE, FALSE, NULL);
    m_hDialogInitializedEvent = ::CreateEvent(NULL, TRUE, FALSE, NULL);

    // Load up commctrl.dll
    // Loading dll dynamically we can use latest available version
    // (i.e. latest native components and extended API)
    HMODULE hModComCtl32 = ::LoadLibrary(TEXT("comctl32.dll"));
    if (hModComCtl32 != NULL) {
        /* Initialize controls to ensure proper appearance */
        InitCommonControlsType fn_InitCommonControls = (InitCommonControlsType)
            ::GetProcAddress(hModComCtl32, "InitCommonControls");
        fn_InitCommonControls();

        /* MessageBox replacement introduced in Vista */
        taskDialogFn = (TaskDialogIndirectFn)
            ::GetProcAddress(hModComCtl32, "TaskDialogIndirect");
    }
}


CDownloadDialog::~CDownloadDialog()
{
    ::CloseHandle(m_hCancelEvent);
    ::CloseHandle(m_hDownloadThreadExitEvent);
    ::CloseHandle(m_hDialogInitializedEvent);
}

void CDownloadDialog::addToTotalContentLength(DWORD contentLength) {
     __try
    {
        m_csDownload.Lock();
        if (m_ulProgressMax == 0) {
            // first download this session, initialize start time
            time(&m_startTime);
        }

        m_ulProgressMax = m_ulProgressMax + contentLength;
        logProgress();
    }
    __finally
    {
        m_csDownload.Unlock();
    }
}



void CDownloadDialog::initDialogText(LPCTSTR downloadURL, LPCTSTR bundleName) {

    // reset status text
    HWND hStatusWnd = GetDlgItem(IDC_TIME_REMAINING);
    ::SetWindowText(hStatusWnd, "");

    // reset progress bar
    HWND hProgressWnd = GetDlgItem(IDC_DOWNLOAD_PROGRESS);

    ::PostMessage(hProgressWnd, PBM_SETPOS, (WPARAM) 0, NULL);

    m_hMastheadFont = NULL;
    m_hDialogFont = NULL;
    m_hSixPointFont = NULL;

    m_hMemDC = NULL;

    TCHAR szDownloadText[BUFFER_SIZE];

    HWND hWndDownloadText = GetDlgItem(IDC_DOWNLOAD_TEXT);
    ::LoadString(_Module.GetModuleInstance(), IDS_DOWNLOAD_TEXT, szDownloadText, BUFFER_SIZE);
    ::SetWindowText(hWndDownloadText, szDownloadText);

    TCHAR szMasthead[BUFFER_SIZE];

    HWND hWndMastheadText = GetDlgItem(IDC_MASTHEAD_TEXT);
    ::LoadString(_Module.GetModuleInstance(), IDS_DOWNLOAD, szMasthead, BUFFER_SIZE);
    ::SetWindowText(hWndMastheadText, szMasthead);


}

BOOL CDownloadDialog::isDownloading() {
    return m_numDownloadThreadsRunning > 0;
}


void CDownloadDialog::bundleInstallStart() {
    __try
    {
        m_csNumDownloadThreads.Lock();
        m_numDownloadThreadsRunning++;
        // another download request has came in, kill the destroyWindowTimer
        KillTimer(destroyWindowTimerID);
        m_destroyWindowTimerStarted = FALSE;
    }
    __finally
    {
        m_csNumDownloadThreads.Unlock();
    }
}

void CDownloadDialog::bundleInstallComplete() {
    __try
    {
        m_csNumDownloadThreads.Lock();
        m_numDownloadThreadsRunning = max(m_numDownloadThreadsRunning - 1, 0);
        if (m_numDownloadThreadsRunning == 0) {
            m_ulProgress = m_ulProgressMax;
            logProgress();
        }
        // Signal main thread
        ::SetEvent(m_hDownloadThreadExitEvent);
    }
    __finally
    {
        m_csNumDownloadThreads.Unlock();
    }
}


//=--------------------------------------------------------------------------=
// CDownloadDialog::OnInitDialog
//=--------------------------------------------------------------------------=
// Message handler for WM_INITDIALOG
//
// Parameters:
//      uMsg        Windows Message
//      wParam      WPARAM
//      lParam      LPARAM
//      bHandled    FALSE if not handled
//
// Output:
//      LRESULT
//
// Notes:
//
LRESULT CDownloadDialog::OnInitDialog(UINT uMsg, WPARAM wParam, LPARAM lParam, BOOL& bHandled)
{
     __try
    {
        m_csDownload.Lock();
    }
    __finally
    {
        m_csDownload.Unlock();
    }
    // Set timer
    SetTimer(iTimerID, UPDATE_INTERVAL);

    m_hMastheadFont = NULL;
    m_hDialogFont = NULL;
    m_hSixPointFont = NULL;
    m_feedbackOnCancel = TRUE;

    m_hMemDC = NULL;

    TCHAR szDownloadText[BUFFER_SIZE];

    HWND hWndDownloadText = GetDlgItem(IDC_DOWNLOAD_TEXT);
    ::LoadString(_Module.GetModuleInstance(), IDS_DOWNLOAD_TEXT, szDownloadText, BUFFER_SIZE);
    ::SetWindowText(hWndDownloadText, szDownloadText);

    TCHAR szMasthead[BUFFER_SIZE];

    HWND hWndMastheadText = GetDlgItem(IDC_MASTHEAD_TEXT);
    ::LoadString(_Module.GetModuleInstance(), IDS_DOWNLOAD, szMasthead, BUFFER_SIZE);
    ::SetWindowText(hWndMastheadText, szMasthead);

    HICON javaCupIcon = ::LoadIcon(_Module.GetModuleInstance(), MAKEINTRESOURCE(IDI_JAVA));
    SetIcon(javaCupIcon, FALSE);

    ::SetEvent(m_hDialogInitializedEvent);

    return 0;  // do not set initial focus to cancel button
}


//=--------------------------------------------------------------------------=
// CDownloadDialog::OnOK
//=--------------------------------------------------------------------------=
// Message handler for WM_COMMAND with IDOK
//
// Parameters:
//      wNotifyCode Notify Code
//      wID         ID of control
//      hWndCtl     HWND of control
//      bHandled    FALSE if not handled
//
// Output:
//      LRESULT
//
// Notes:
//
LRESULT CDownloadDialog::OnOK(WORD wNotifyCode, WORD wID, HWND hWndCtl, BOOL& bHandled)
{
    // do nothing for now
    return 0;
}



//=--------------------------------------------------------------------------=
// CDownloadDialog::OnCancel
//=--------------------------------------------------------------------------=
// Message handler for WM_COMMAND with IDCANCEL
//
// Parameters:
//      wNotifyCode Notify Code
//      wID         ID of control
//      hWndCtl     HWND of control
//      bHandled    FALSE if not handled
//
// Output:
//      LRESULT
//
// Notes:
//
LRESULT CDownloadDialog::OnCancel(WORD wNotifyCode, WORD wID, HWND hWndCtl, BOOL& bHandled)
{
    // Disable window first to avoid any keyboard input
    EnableWindow(FALSE);

    if (m_feedbackOnCancel) {
      int r = SafeMessageBox(IDS_DOWNLOAD_CANCEL_MESSAGE,
                       IDS_DOWNLOAD_CANCEL_INSTRUCTION,
                       IDS_DOWNLOAD_CANCEL_CAPTION,
                       DIALOG_WARNING_CANCELOK,
                       NULL, NULL);
      if (!::IsWindow(hWndCtl)) {
         /* It is possible that download was finished and download
            window hidden by the time user close this message box.
            If such case we should simply return. */
         return 0;
      }
      if (r == IDCANCEL) {
        EnableWindow(TRUE);
        return 0;
      }
    }

    __try
    {
        m_csDownload.Lock();
        // if we are downloading, signal download thread to stop downloading
        if (m_numDownloadThreadsRunning > 0) {
            SetEvent(m_hCancelEvent);
        }
    }
    __finally
    {
        m_csDownload.Unlock();
    }

    // Kill timer
    KillTimer(iTimerID);
    KillTimer(destroyWindowTimerID);

    FreeGDIResources();

    // Destroy dialog
    EndDialog(wID);

    return 0;
}

void CDownloadDialog::destroyDialog() {
    m_feedbackOnCancel = FALSE;
    ::PostMessage(m_hWnd, WM_COMMAND, IDCANCEL, NULL);
}


void CDownloadDialog::delayedDoModal() {
     __try
    {
         __try
        {
            m_csMessageBox.Lock();
            m_dialogUp = true;
            Sleep(INITIAL_DELAY);
        }
        __finally
        {
            m_csMessageBox.Unlock();
        }

        if (isDownloading())
            DoModal();
    }
    __finally
    {
        m_dialogUp = false;
    }
}


//=--------------------------------------------------------------------------=
// CDownloadDialog::SafeMessageBox
//=--------------------------------------------------------------------------=
// Helper method that uses best availble API to show native error/information
// dialog. In particular, it uses TaskDialog if availble (Vista specific)
// and MessageBox otherwise.
//
// It also ensures that the message box is always displayed on top of
// the progress dialog instead of underneath
//

//helper structures to define XP vs Vista style differences
static TASKDIALOG_COMMON_BUTTON_FLAGS vistaDialogButtons[] = {
    TDCBF_RETRY_BUTTON | TDCBF_CANCEL_BUTTON,
    TDCBF_OK_BUTTON | TDCBF_CANCEL_BUTTON
};
static PCWSTR vistaIcons[] = {
    TD_ERROR_ICON,
    TD_WARNING_ICON
};

static UINT xpStyle[] = {
    MB_ICONERROR | MB_RETRYCANCEL,
    MB_ICONWARNING | MB_OKCANCEL | MB_DEFBUTTON2
};

int CDownloadDialog::SafeMessageBox(UINT details, UINT mainInstruction, UINT caption, DialogType type, LPCWSTR instructionArg, LPCWSTR detailsArg) {
    WCHAR textCaption[BUFFER_SIZE+1];
    WCHAR textDetails[BUFFER_SIZE+1];
    WCHAR textInstruction[BUFFER_SIZE+1];
    WCHAR tmpBuffer[BUFFER_SIZE+1];

    /* make sure buffers are terminated */
    textCaption[BUFFER_SIZE] = textDetails[BUFFER_SIZE] = 0;
    textInstruction[BUFFER_SIZE] = tmpBuffer[BUFFER_SIZE] = 0;

    if (detailsArg != NULL) {
        ::LoadStringW(_Module.GetResourceInstance(),
                 details,
                 tmpBuffer,
                 BUFFER_SIZE);
        _snwprintf(textDetails, BUFFER_SIZE, tmpBuffer, detailsArg);
    } else {
        ::LoadStringW(_Module.GetResourceInstance(),
                 details,
                 textDetails,
                 BUFFER_SIZE);
    }

    if (instructionArg != NULL) {
        ::LoadStringW(_Module.GetResourceInstance(),
                 mainInstruction,
                 tmpBuffer,
                 BUFFER_SIZE);
        _snwprintf(textInstruction, BUFFER_SIZE, tmpBuffer, instructionArg);
     } else {
        ::LoadStringW(_Module.GetResourceInstance(),
                 mainInstruction,
                 textInstruction,
                 BUFFER_SIZE);
     }

    ::LoadStringW(_Module.GetResourceInstance(),
                 caption,
                 textCaption,
                 BUFFER_SIZE);

    __try
    {
        m_csMessageBox.Lock();
        if (m_dialogUp) {
            waitUntilInitialized();
        }
        /* If TaskDialog availble - use it! */
        if (taskDialogFn != NULL) {
              TASKDIALOGCONFIG tc = { 0 };
              int nButton;

              tc.cbSize = sizeof(tc);
              tc.hwndParent = ::IsWindow(m_hWnd) ? m_hWnd : NULL;
              tc.dwCommonButtons = vistaDialogButtons[type];
              tc.pszWindowTitle = textCaption;
              tc.pszMainInstruction = textInstruction;
              tc.pszContent = textDetails;
              tc.pszMainIcon = vistaIcons[type];
              /* workaround: we need to make sure Cancel is default
                             for this type of Dialog */
              if (type == DIALOG_WARNING_CANCELOK) {
                  tc.nDefaultButton = IDCANCEL;
              }

              taskDialogFn(&tc, &nButton, NULL, NULL);
              return nButton;
        } else { /* default: use MessageBox */
            /* Note that MessageBox API expects content as single string
               and therefore we need to concatenate instruction
               and details as 2 paragraphs.

               The only exception is empty instruction. */
            if (wcslen(textInstruction) > 0) {
                wcsncat(textInstruction, L"\n\n",
                        BUFFER_SIZE - wcslen(textInstruction));
            }
            wcsncat(textInstruction, textDetails,
                    BUFFER_SIZE - wcslen(textInstruction));

            return ::MessageBoxW(::IsWindow(m_hWnd) ? m_hWnd : NULL,
                textInstruction, textCaption, xpStyle[type]);
        }
    }
    __finally
    {
        m_csMessageBox.Unlock();
    }
}


//=--------------------------------------------------------------------------=
// CDownloadDialog::OnTimer
//=--------------------------------------------------------------------------=
// Message handler for WM_TIMER
//
// Parameters:
//      uMsg        Windows Message
//      wParam      WPARAM
//      lParam      LPARAM
//      bHandled    FALSE if not handled
//
// Output:
//      LRESULT
//
// Notes:
//
LRESULT CDownloadDialog::OnTimer(UINT uMsg, WPARAM wParam, LPARAM lParam, BOOL& bHandled)
{
    if (destroyWindowTimerID == (int)wParam) {
        KillTimer(destroyWindowTimerID);
        m_destroyWindowTimerStarted = FALSE;
        m_ulProgressMax = max(0, m_ulProgressMax - m_ulProgress);
        logProgress();
        m_ulProgress = 0;
        logProgress();
        m_feedbackOnCancel = FALSE;
        ::PostMessage(m_hWnd, WM_COMMAND, IDCANCEL, NULL);
    }

    if (iTimerID == (int)wParam)
    {

        __try
        {
            m_csDownload.Lock();

            HWND hStatusWnd = GetDlgItem(IDC_TIME_REMAINING);
            HWND hProgressWnd = GetDlgItem(IDC_DOWNLOAD_PROGRESS);

            if (m_ulProgress && m_ulProgressMax)
            {
                ::PostMessage(hProgressWnd, PBM_SETPOS,
                     (WPARAM) (m_ulProgress * 100
                        / m_ulProgressMax), NULL);

                time_t currentTime;
                time(&currentTime);

                double elapsed_time = difftime(currentTime, m_startTime);
                double remain_time = (elapsed_time / m_ulProgress) *
                                      (m_ulProgressMax - m_ulProgress);
                int hr = 0, min = 0;

                if (remain_time > 60 * 60)
                {
                    hr = int(remain_time / (60 * 60));
                    remain_time = remain_time - hr * 60 * 60;
                }

                if (remain_time > 60)
                {
                    min = int(remain_time / 60);
                    remain_time = remain_time - min * 60;
                }

                TCHAR szBuffer[BUFFER_SIZE];
                TCHAR szTimeBuffer[BUFFER_SIZE];

                if (hr > 0)
                {
                    if (hr > 1)
                        LoadString(_Module.GetResourceInstance(), IDS_HOURSMINUTESECOND,
                                   szTimeBuffer, BUFFER_SIZE);
                    else
                        LoadString(_Module.GetResourceInstance(), IDS_HOURMINUTESECOND,
                                   szTimeBuffer, BUFFER_SIZE);

                    sprintf(szBuffer, szTimeBuffer, hr, min, remain_time);
                }
                else
                {
                    if (min > 0)
                    {
                        LoadString(_Module.GetResourceInstance(), IDS_MINUTESECOND,
                                   szTimeBuffer, BUFFER_SIZE);
                        sprintf(szBuffer, szTimeBuffer, min, remain_time);

                    }
                    else
                    {
                        LoadString(_Module.GetResourceInstance(), IDS_SECOND,
                                   szTimeBuffer, BUFFER_SIZE);
                        sprintf(szBuffer, szTimeBuffer, remain_time);

                    }
                }

                if (m_ulProgress == m_ulProgressMax) {
                    // download is done, unpacking bundle now, and waiting
                    // for another download to take place
                    ::LoadString(_Module.GetResourceInstance(),
                            IDS_DOWNLOAD_UNPACKING, szBuffer, BUFFER_SIZE);
                    __try
                    {
                        m_csNumDownloadThreads.Lock();
                        // both download and unpacking is done, start
                        // timer to destroy the progress window in 500ms
                        if (!m_destroyWindowTimerStarted &&
                               m_numDownloadThreadsRunning == 0) {
                            SetTimer(destroyWindowTimerID, POST_DELAY);
                            m_destroyWindowTimerStarted = TRUE;
                        }
                    }
                    __finally
                    {
                        m_csNumDownloadThreads.Unlock();
                    }
                }

                // Update status message
                ::SetWindowText(hStatusWnd, szBuffer);
            }
        }
        __finally
        {
           m_csDownload.Unlock();
        }
    }

    return 0;
}

// Message handler for WM_ONCTLCOLORSTATIC.
// this message is sent each time a static control is drawn.
// we get the Control ID and then set background color and font
// as appropriate for that control.
LRESULT CDownloadDialog::OnCtlColorStatic(UINT uMsg, WPARAM wParam, LPARAM lParam, BOOL& bHandled)
{
    HDC hdc = (HDC) wParam;
    HWND hwnd = (HWND) lParam;

    int DlgCtrlID = ::GetDlgCtrlID(hwnd);

    if (DlgCtrlID == IDC_DOWNLOAD_TEXT )
    {
        if (m_hDialogFont == NULL)
        {
            m_hDialogFont = CreateDialogFont(hdc, TEXT("MS Shell Dlg"), 8);
        }

        ::SelectObject(hdc, m_hDialogFont);
        return 0;
    }
    else if (DlgCtrlID == IDC_TIME_REMAINING)
    {
        if (m_hSixPointFont == NULL)
        {
            m_hSixPointFont = CreateDialogFont(hdc, TEXT("MS Shell Dlg"), 8);
        }

        ::SelectObject(hdc, m_hSixPointFont);
        return 0;
    }
    else if (DlgCtrlID == IDC_MASTHEAD_TEXT)
    {
        if (m_hMastheadFont == NULL)
        {
            m_hMastheadFont = CreateDialogFont(hdc, TEXT("MS Shell Dlg"), 12, 1);
        }

        ::SelectObject(hdc, m_hMastheadFont);
        return (LRESULT) GetStockObject(WHITE_BRUSH);
    }
    else if (DlgCtrlID == IDC_DOWNLOAD_MASTHEAD)
    {
        if (m_hMemDC == NULL)
        {
            m_hBitmap = LoadBitmap(_Module.GetModuleInstance(),
                                   MAKEINTRESOURCE(IDI_MASTHEAD));
            GetObject(m_hBitmap, sizeof(BITMAP), &m_bmMasthead);
            m_hMemDC = CreateCompatibleDC(NULL);
            SelectObject(m_hMemDC, m_hBitmap);
        }

        RECT rect;
        ::GetClientRect(hwnd, &rect);

        StretchBlt(hdc, rect.left, rect.top, (rect.right - rect.left), (rect.bottom - rect.top),
                   m_hMemDC, 0, 0, m_bmMasthead.bmWidth, m_bmMasthead.bmHeight, SRCCOPY);

        return (LRESULT) GetStockObject(NULL_BRUSH);
    }


    return 0;
}


//=--------------------------------------------------------------------------=
// CDownloadDialog::OnStartBinding
//=--------------------------------------------------------------------------=
// Called when download is started
//
// Parameters:
//
// Output:
//      HRESULT
//
// Notes:
//
STDMETHODIMP CDownloadDialog::OnStartBinding()
{
    __try
    {
        m_csDownload.Lock();
        time(&m_startTime);
    }
    __finally
    {
        m_csDownload.Unlock();
    }

    return S_OK;
}


//=--------------------------------------------------------------------------=
// CDownloadDialog::OnProgress
//=--------------------------------------------------------------------------=
// Called when download is in progress
//
// Parameters: ULONG ulProgress
//
// Output:
//      HRESULT
//
// Notes:
//
STDMETHODIMP CDownloadDialog::OnProgress(ULONG ulProgress)
{
    __try
    {
        m_csDownload.Lock();
        m_ulProgress = m_ulProgress + ulProgress;
        logProgress();

    }
    __finally
    {
        m_csDownload.Unlock();
    }

    return S_OK;
}

void CDownloadDialog::decrementProgressMax(ULONG contentLength, ULONG readSoFar) {
    __try
    {
        m_csDownload.Lock();
        m_ulProgressMax = m_ulProgressMax - contentLength;
        m_ulProgress = m_ulProgress - readSoFar;
        logProgress();
    }
    __finally
    {
        m_csDownload.Unlock();
    }

}

void CDownloadDialog::waitUntilInitialized() {
    // wait until download progress dialog is initialized and ready to show
    WaitForSingleObject(m_hDialogInitializedEvent, INFINITE);
    ResetEvent(m_hDialogInitializedEvent);

}

// Check if download has been cancelled
BOOL CDownloadDialog::isDownloadCancelled() {
    if (WAIT_OBJECT_0 == WaitForSingleObject(m_hCancelEvent, 0)) {
        return TRUE;
    }
    return FALSE;
}



// Create the fonts we need for the download and
// install UE
HFONT CDownloadDialog::CreateDialogFont(HDC hdc, LPCTSTR lpszFaceName, int ptSize, int isBold)
{
    POINT pt;
    FLOAT cxDPI, cyDPI;
    HFONT hFont;
    LOGFONT lf;

    int iDeciPtWidth = 0;
    int iDeciPtHeight = 10 * ptSize;

    int iSavedDC = SaveDC(hdc);

    SetGraphicsMode (hdc, GM_ADVANCED);
    ModifyWorldTransform(hdc, NULL, MWT_IDENTITY);
    SetViewportOrgEx (hdc, 0,0, NULL);
    SetWindowOrgEx (hdc, 0,0, NULL);

    cxDPI = (FLOAT) GetDeviceCaps(hdc, LOGPIXELSX);
    cyDPI = (FLOAT) GetDeviceCaps(hdc, LOGPIXELSY);

    pt.x = (int) (iDeciPtWidth * cxDPI / 72);
    pt.y = (int) (iDeciPtHeight * cyDPI / 72);

    DPtoLP(hdc, &pt, 1);

    lf.lfHeight = - (int) (fabs ((double) pt.y) / 10.0 + 0.5);
    lf.lfWidth = 0;
    lf.lfEscapement = 0;
    lf.lfOrientation = 0;
    lf.lfWeight = (isBold > 0) ? FW_BOLD : 0;
    lf.lfItalic = 0;
    lf.lfUnderline = 0;
    lf.lfStrikeOut = 0;
    lf.lfCharSet = 0;
    lf.lfOutPrecision = 0;
    lf.lfClipPrecision = 0;
    lf.lfQuality = 0;
    lf.lfPitchAndFamily = 0;

    TCHAR szLocaleData[BUFFER_SIZE];
    GetLocaleInfo(LOCALE_SYSTEM_DEFAULT, LOCALE_SENGCOUNTRY,
                  szLocaleData, BUFFER_SIZE);

    if (strncmp(szLocaleData, "Japan", 5) == 0) {
        // need special font for _ja locale
        strcpy (lf.lfFaceName, TEXT("MS UI Gothic"));
    } else {
        strcpy (lf.lfFaceName, lpszFaceName);
    }

    hFont = CreateFontIndirect(&lf);

    RestoreDC (hdc, iSavedDC);
    return hFont;
}

void CDownloadDialog::FreeGDIResources ()
{
    ::DeleteObject(m_hMastheadFont);
    m_hMastheadFont = NULL;

    ::DeleteObject(m_hDialogFont);
    m_hDialogFont = NULL;

    ::DeleteObject(m_hSixPointFont);
    m_hSixPointFont = NULL;

    ::DeleteObject(m_hBitmap);
    m_hBitmap = NULL;

    ::DeleteDC(m_hMemDC);
    m_hMemDC = NULL;
}


JNIEnv* CDownloadDialog::getJNIEnv() {
    if (m_jvm == NULL)
        return NULL;
    JNIEnv *env;
    m_jvm->AttachCurrentThread((void**) &env, NULL);
    return env;
}


void CDownloadDialog::log(char *msg) {
    JNIEnv *env = getJNIEnv();
    if (env != NULL) {
        jclass dm = env->FindClass("sun/jkernel/DownloadManager");
        if (dm == NULL) {
            printf("Cound not find class sun.jkernel.DownloadManager\n");
            return;
        }
        jmethodID log = env->GetStaticMethodID(dm, "log", "(Ljava/lang/String;)V");
        if (log == NULL) {
            printf("Could not find method sun.jkernel.DownloadManager.log(String)\n");
            return;
        }
        jstring string = env->NewStringUTF(msg);
        if (string == NULL) {
            printf("Error creating log string\n");
            return;
        }
        env->CallStaticVoidMethod(dm, log, string);
    }
}


void CDownloadDialog::logProgress() {
    char msg[256];
    sprintf(msg, "Progress: %d / %d", m_ulProgress, m_ulProgressMax);
    log(msg);
}
