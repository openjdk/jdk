/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

//
// DownloadDialog.h : Declaration of the CDownloadDialog
//

#ifndef __DOWNLOADDIALOG_H_
#define __DOWNLOADDIALOG_H_

#include "resource.h"       // main symbols
#include <time.h>
#include "jni.h"

#ifndef BUFFER_SIZE
#define BUFFER_SIZE 2048
#endif

#define iTimerID    1000
#define destroyWindowTimerID    2000

#define E_JDHELPER_TIMEOUT               12002
#define E_JDHELPER_NAME_NOT_RESOLVED     12007
#define E_JDHELPER_CANNOT_CONNECT        12029

/* Following lines were copied from the new version of commctrl.h
   These definitions are not available in default version of
   this header file in VS 2003 but they are needed to use
   new Vista task dialog API.
*/
#ifndef TD_ERROR_ICON

/* These modifiers have sense with new VS only,
   reset them to get code to compile */
#define __in
#define __in_opt
#define __out_opt

#ifdef _WIN32
#include <pshpack1.h>
#endif


typedef HRESULT (CALLBACK *PFTASKDIALOGCALLBACK)(HWND hwnd, __in UINT msg, __in WPARAM wParam, __in LPARAM lParam, __in LONG_PTR lpRefData);

enum _TASKDIALOG_FLAGS
{
    TDF_ENABLE_HYPERLINKS               = 0x0001,
    TDF_USE_HICON_MAIN                  = 0x0002,
    TDF_USE_HICON_FOOTER                = 0x0004,
    TDF_ALLOW_DIALOG_CANCELLATION       = 0x0008,
    TDF_USE_COMMAND_LINKS               = 0x0010,
    TDF_USE_COMMAND_LINKS_NO_ICON       = 0x0020,
    TDF_EXPAND_FOOTER_AREA              = 0x0040,
    TDF_EXPANDED_BY_DEFAULT             = 0x0080,
    TDF_VERIFICATION_FLAG_CHECKED       = 0x0100,
    TDF_SHOW_PROGRESS_BAR               = 0x0200,
    TDF_SHOW_MARQUEE_PROGRESS_BAR       = 0x0400,
    TDF_CALLBACK_TIMER                  = 0x0800,
    TDF_POSITION_RELATIVE_TO_WINDOW     = 0x1000,
    TDF_RTL_LAYOUT                      = 0x2000,
    TDF_NO_DEFAULT_RADIO_BUTTON         = 0x4000,
    TDF_CAN_BE_MINIMIZED                = 0x8000
};
typedef int TASKDIALOG_FLAGS;                         // Note: _TASKDIALOG_FLAGS is an int

typedef enum _TASKDIALOG_MESSAGES
{
    TDM_NAVIGATE_PAGE                   = WM_USER+101,
    TDM_CLICK_BUTTON                    = WM_USER+102, // wParam = Button ID
    TDM_SET_MARQUEE_PROGRESS_BAR        = WM_USER+103, // wParam = 0 (nonMarque) wParam != 0 (Marquee)
    TDM_SET_PROGRESS_BAR_STATE          = WM_USER+104, // wParam = new progress state
    TDM_SET_PROGRESS_BAR_RANGE          = WM_USER+105, // lParam = MAKELPARAM(nMinRange, nMaxRange)
    TDM_SET_PROGRESS_BAR_POS            = WM_USER+106, // wParam = new position
    TDM_SET_PROGRESS_BAR_MARQUEE        = WM_USER+107, // wParam = 0 (stop marquee), wParam != 0 (start marquee), lparam = speed (milliseconds between repaints)
    TDM_SET_ELEMENT_TEXT                = WM_USER+108, // wParam = element (TASKDIALOG_ELEMENTS), lParam = new element text (LPCWSTR)
    TDM_CLICK_RADIO_BUTTON              = WM_USER+110, // wParam = Radio Button ID
    TDM_ENABLE_BUTTON                   = WM_USER+111, // lParam = 0 (disable), lParam != 0 (enable), wParam = Button ID
    TDM_ENABLE_RADIO_BUTTON             = WM_USER+112, // lParam = 0 (disable), lParam != 0 (enable), wParam = Radio Button ID
    TDM_CLICK_VERIFICATION              = WM_USER+113, // wParam = 0 (unchecked), 1 (checked), lParam = 1 (set key focus)
    TDM_UPDATE_ELEMENT_TEXT             = WM_USER+114, // wParam = element (TASKDIALOG_ELEMENTS), lParam = new element text (LPCWSTR)
    TDM_SET_BUTTON_ELEVATION_REQUIRED_STATE = WM_USER+115, // wParam = Button ID, lParam = 0 (elevation not required), lParam != 0 (elevation required)
    TDM_UPDATE_ICON                     = WM_USER+116  // wParam = icon element (TASKDIALOG_ICON_ELEMENTS), lParam = new icon (hIcon if TDF_USE_HICON_* was set, PCWSTR otherwise)
} TASKDIALOG_MESSAGES;

typedef enum _TASKDIALOG_NOTIFICATIONS
{
    TDN_CREATED                         = 0,
    TDN_NAVIGATED                       = 1,
    TDN_BUTTON_CLICKED                  = 2,            // wParam = Button ID
    TDN_HYPERLINK_CLICKED               = 3,            // lParam = (LPCWSTR)pszHREF
    TDN_TIMER                           = 4,            // wParam = Milliseconds since dialog created or timer reset
    TDN_DESTROYED                       = 5,
    TDN_RADIO_BUTTON_CLICKED            = 6,            // wParam = Radio Button ID
    TDN_DIALOG_CONSTRUCTED              = 7,
    TDN_VERIFICATION_CLICKED            = 8,             // wParam = 1 if checkbox checked, 0 if not, lParam is unused and always 0
    TDN_HELP                            = 9,
    TDN_EXPANDO_BUTTON_CLICKED          = 10            // wParam = 0 (dialog is now collapsed), wParam != 0 (dialog is now expanded)
} TASKDIALOG_NOTIFICATIONS;

typedef struct _TASKDIALOG_BUTTON
{
    int     nButtonID;
    PCWSTR  pszButtonText;
} TASKDIALOG_BUTTON;

typedef enum _TASKDIALOG_ELEMENTS
{
    TDE_CONTENT,
    TDE_EXPANDED_INFORMATION,
    TDE_FOOTER,
    TDE_MAIN_INSTRUCTION
} TASKDIALOG_ELEMENTS;

typedef enum _TASKDIALOG_ICON_ELEMENTS
{
    TDIE_ICON_MAIN,
    TDIE_ICON_FOOTER
} TASKDIALOG_ICON_ELEMENTS;

#define TD_WARNING_ICON         MAKEINTRESOURCEW(-1)
#define TD_ERROR_ICON           MAKEINTRESOURCEW(-2)
#define TD_INFORMATION_ICON     MAKEINTRESOURCEW(-3)
#define TD_SHIELD_ICON          MAKEINTRESOURCEW(-4)


enum _TASKDIALOG_COMMON_BUTTON_FLAGS
{
    TDCBF_OK_BUTTON            = 0x0001, // selected control return value IDOK
    TDCBF_YES_BUTTON           = 0x0002, // selected control return value IDYES
    TDCBF_NO_BUTTON            = 0x0004, // selected control return value IDNO
    TDCBF_CANCEL_BUTTON        = 0x0008, // selected control return value IDCANCEL
    TDCBF_RETRY_BUTTON         = 0x0010, // selected control return value IDRETRY
    TDCBF_CLOSE_BUTTON         = 0x0020  // selected control return value IDCLOSE
};
typedef int TASKDIALOG_COMMON_BUTTON_FLAGS;           // Note: _TASKDIALOG_COMMON_BUTTON_FLAGS is an int

typedef struct _TASKDIALOGCONFIG
{
    UINT        cbSize;
    HWND        hwndParent;
    HINSTANCE   hInstance;                              // used for MAKEINTRESOURCE() strings
    TASKDIALOG_FLAGS                dwFlags;            // TASKDIALOG_FLAGS (TDF_XXX) flags
    TASKDIALOG_COMMON_BUTTON_FLAGS  dwCommonButtons;    // TASKDIALOG_COMMON_BUTTON (TDCBF_XXX) flags
    PCWSTR      pszWindowTitle;                         // string or MAKEINTRESOURCE()
    union
    {
        HICON   hMainIcon;
        PCWSTR  pszMainIcon;
    };
    PCWSTR      pszMainInstruction;
    PCWSTR      pszContent;
    UINT        cButtons;
    const TASKDIALOG_BUTTON  *pButtons;
    int         nDefaultButton;
    UINT        cRadioButtons;
    const TASKDIALOG_BUTTON  *pRadioButtons;
    int         nDefaultRadioButton;
    PCWSTR      pszVerificationText;
    PCWSTR      pszExpandedInformation;
    PCWSTR      pszExpandedControlText;
    PCWSTR      pszCollapsedControlText;
    union
    {
        HICON   hFooterIcon;
        PCWSTR  pszFooterIcon;
    };
    PCWSTR      pszFooter;
    PFTASKDIALOGCALLBACK pfCallback;
    LONG_PTR    lpCallbackData;
    UINT        cxWidth;                                // width of the Task Dialog's client area in DLU's. If 0, Task Dialog will calculate the ideal width.
} TASKDIALOGCONFIG;

WINCOMMCTRLAPI HRESULT WINAPI TaskDialogIndirect(const TASKDIALOGCONFIG *pTaskConfig, __out_opt int *pnButton, __out_opt int *pnRadioButton, __out_opt BOOL *pfVerificationFlagChecked);
WINCOMMCTRLAPI HRESULT WINAPI TaskDialog(__in_opt HWND hwndParent, __in_opt HINSTANCE hInstance, __in_opt PCWSTR pszWindowTitle, __in_opt PCWSTR pszMainInstruction, __in_opt PCWSTR pszContent, TASKDIALOG_COMMON_BUTTON_FLAGS dwCommonButtons, __in_opt PCWSTR pszIcon, __out_opt int *pnButton);

#ifdef _WIN32
#include <poppack.h>
#endif

#endif /* end of copy from commctrl.h */

typedef HRESULT (WINAPI *TaskDialogIndirectFn) (const TASKDIALOGCONFIG *pTaskConfig, __out_opt int *pnButton, __out_opt int *pnRadioButton, __out_opt BOOL *pfVerificationFlagChecked);

typedef enum {
    DIALOG_ERROR_RETRYCANCEL = 0,
    DIALOG_WARNING_CANCELOK
} DialogType;


/////////////////////////////////////////////////////////////////////////////
// CDownloadDialog
class CDownloadDialog :
        public CAxDialogImpl<CDownloadDialog>
{
public:
        CDownloadDialog();
        ~CDownloadDialog();

        enum { IDD = IDD_DOWNLOAD_DIALOG };

BEGIN_MSG_MAP(CDownloadDialog)
        MESSAGE_HANDLER(WM_INITDIALOG, OnInitDialog)
        MESSAGE_HANDLER(WM_TIMER, OnTimer)
        MESSAGE_HANDLER(WM_CTLCOLORSTATIC, OnCtlColorStatic)
        COMMAND_ID_HANDLER(IDOK, OnOK)
        COMMAND_ID_HANDLER(IDCANCEL, OnCancel)
END_MSG_MAP()

        LRESULT OnInitDialog(UINT uMsg, WPARAM wParam, LPARAM lParam, BOOL& bHandled);
        LRESULT OnOK(WORD wNotifyCode, WORD wID, HWND hWndCtl, BOOL& bHandled);
        LRESULT OnCancel(WORD wNotifyCode, WORD wID, HWND hWndCtl, BOOL& bHandled);
        LRESULT OnTimer(UINT uMsg, WPARAM wParam, LPARAM lParam, BOOL& bHandled);
        LRESULT OnCtlColorStatic(UINT uMsg, WPARAM wParam, LPARAM lParam, BOOL& bHandled);

        STDMETHODIMP OnStartBinding();

        STDMETHODIMP OnProgress(ULONG ulProgress);

        void initDialogText(LPCTSTR pszDownloadURL, LPCTSTR pszBundleName);

        BOOL isDownloading();
        BOOL isDownloadCancelled();

        void addToTotalContentLength(DWORD contentLength);

        void decrementProgressMax(ULONG contentLength, ULONG readSoFar);

        void bundleInstallStart();
        void bundleInstallComplete();

        void waitUntilInitialized();

        void log(char *msg);
        void logProgress();

        void setFile(LPCTSTR pszFileName)
        {
            m_pszFileName = pszFileName;
        }

        void setURL(LPCTSTR pszURL)
        {
            m_pszURL = pszURL;
        }

        void setNameText(LPTSTR pszNameText)
        {
            m_pszNameText = pszNameText;
        }


        JNIEnv* getJNIEnv();


        void setJavaVM(JavaVM *jvm)
        {
            m_jvm = jvm;
        }


        HRESULT DownloadConfiguration(LPTSTR pszConfigURL, LPTSTR pszConfigFile);

        void delayedDoModal();

        int SafeMessageBox(UINT details, UINT mainInstruction, UINT caption,
                           DialogType type, LPCWSTR instructionArg = NULL,
                           LPCWSTR detailsArg = NULL);

        void destroyDialog();

    private:

        HFONT CreateDialogFont (HDC hdc, LPCTSTR lpszFaceName, int ptSize, int isBold = 0);
        void  FreeGDIResources ();

        BOOL                    m_feedbackOnCancel;
        TaskDialogIndirectFn    taskDialogFn;
        LPCTSTR                 m_pszFileName;
        LPCTSTR                 m_pszURL;
        time_t                  m_startTime;
        ULONG                   m_ulProgress;
        ULONG                   m_ulProgressMax;
        int                     m_iProgressFactor;
        int                     m_iMaxProgressFactor;
        int                     m_numDownloadThreadsRunning;
        BOOL            m_destroyWindowTimerStarted;
        volatile BOOL           m_dialogUp;
        CComAutoCriticalSection m_csDownload;
        CComAutoCriticalSection m_csNumDownloadThreads;
        HANDLE                  m_hCancelEvent;
        HANDLE                  m_hDownloadThreadExitEvent;
        HANDLE                  m_hDialogInitializedEvent;
        HFONT                   m_hMastheadFont;
        HFONT                   m_hDialogFont;
        HFONT                   m_hSixPointFont;
        LPTSTR                  m_pszNameText;
        BITMAP                  m_bmMasthead;
        HBITMAP                 m_hBitmap;
        HDC                     m_hMemDC;
        TCHAR                   m_szUrlPath[BUFFER_SIZE];
        TCHAR                   m_szHostName[BUFFER_SIZE];
        JavaVM*                 m_jvm;
        CComAutoCriticalSection m_csMessageBox;
};

#endif //__DOWNLOADDIALOG_H_
