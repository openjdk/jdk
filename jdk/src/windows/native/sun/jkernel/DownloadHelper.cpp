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

#include "resource.h"       // main symbols
#include "DownloadHelper.h"

DownloadHelper::DownloadHelper() {

    m_showProgressDialog = TRUE;
    m_pszURL = NULL;
    m_pszFileName = NULL;
    m_pszNameText = NULL;
}

DownloadHelper::~DownloadHelper() {

}

HRESULT DownloadHelper::doDownload() {
    return DownloadFile(m_pszURL, m_pszFileName, FALSE, m_showProgressDialog);
}

HRESULT DownloadHelper::DownloadFile(const TCHAR* szURL,
        const TCHAR* szLocalFile, BOOL bResumable, BOOL bUIFeedback) {
    HINTERNET hOpen = NULL;
    HINTERNET hConnect = NULL;
    HINTERNET hRequest = NULL;
    HANDLE hFile = INVALID_HANDLE_VALUE;
    DWORD dwDownloadError = 0;
    DWORD nContentLength = 0;

    /* Some of error messages use drive letter.
       Result is something like "(C:)".
       NB: Parentheses are added here because in some other places
           we same message but can not provide disk label info */
    TCHAR drivePath[5];
    /* assuming szLocalFile is not NULL */
    _sntprintf(drivePath, 5, "(%c:)", szLocalFile[0]);
    WCHAR* wName = CT2CW(drivePath);

    __try {
        m_csDownload.Lock();

        time(&m_startTime);

    }
    __finally {
        m_csDownload.Unlock();
    }

    __try {
        // block potential security hole
        if (strstr(szURL, TEXT("file://")) != NULL) {
            dwDownloadError = 1;
            __leave;
        }

        HWND hProgressInfo = NULL;
        TCHAR szStatus[BUFFER_SIZE];

        if (bUIFeedback) {
            // init download dialg text
            m_dlg->initDialogText(m_pszURL, m_pszNameText);
        }

        // Open Internet Call
        hOpen = ::InternetOpen("deployHelper", INTERNET_OPEN_TYPE_PRECONFIG,
                NULL, NULL, NULL);

        if (hOpen == NULL) {
            dwDownloadError = 1;
            __leave;
        }

        // URL components
        URL_COMPONENTS url_components;
        ::ZeroMemory(&url_components, sizeof(URL_COMPONENTS));

        TCHAR szHostName[BUFFER_SIZE], szUrlPath[BUFFER_SIZE],
                szExtraInfo[BUFFER_SIZE];
        url_components.dwStructSize = sizeof(URL_COMPONENTS);
        url_components.lpszHostName = szHostName;
        url_components.dwHostNameLength = BUFFER_SIZE;
        url_components.nPort = NULL;
        url_components.lpszUrlPath = szUrlPath;
        url_components.dwUrlPathLength = BUFFER_SIZE;
        url_components.lpszExtraInfo = szExtraInfo;
        url_components.dwExtraInfoLength = BUFFER_SIZE;

        // Crack the URL into pieces
        ::InternetCrackUrl(szURL, lstrlen(szURL), NULL, &url_components);

        // Open Internet Connection
        hConnect = ::InternetConnect(hOpen, url_components.lpszHostName,
                url_components.nPort, "", "", INTERNET_SERVICE_HTTP, NULL,
                NULL);

        if (hConnect == NULL) {
            dwDownloadError = 1;
            __leave;
        }

        // Determine the relative URL path by combining
        // Path and ExtraInfo
        char szURL[4096];

        if (url_components.dwUrlPathLength !=  0)
            lstrcpy(szURL, url_components.lpszUrlPath);
        else
            lstrcpy(szURL, "/");

        if (url_components.dwExtraInfoLength != 0)
            lstrcat(szURL, url_components.lpszExtraInfo);

        BOOL bRetryHttpRequest = FALSE;
        int numberOfRetry = 0;
        long secondsToWait = 60;

        do {
            bRetryHttpRequest = FALSE;

            // Make a HTTP GET request
            hRequest = ::HttpOpenRequest(hConnect, "GET", szURL, "HTTP/1.1",
                    "", NULL,
                    INTERNET_FLAG_KEEP_CONNECTION | INTERNET_FLAG_DONT_CACHE,
                    0);

            if (hRequest == NULL) {
                dwDownloadError = 1;
                __leave;
            }

            // Create or open existing destination file
            hFile = ::CreateFile(szLocalFile, GENERIC_WRITE, 0, NULL,
                    OPEN_ALWAYS, FILE_ATTRIBUTE_ARCHIVE, NULL);

            if (hFile == INVALID_HANDLE_VALUE) {
                if (bUIFeedback) {
                    if (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_DISK_WRITE_ERROR,
                                            IDS_DISK_WRITE_ERROR_CAPTION,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            wName)) {
                         bRetryHttpRequest = TRUE;
                         continue;
                    }
                }
                dwDownloadError = 1;
                __leave;
            }
            DWORD fileSize = GetFileSize(hFile, NULL);

            // Check if resumable download is enabled
            if (bResumable == FALSE) {
                // Start from scratch
                fileSize = 0;
            }

            FILETIME tWrite;
            BOOL rangereq = FALSE;
            if ((fileSize != 0) && (fileSize != 0xFFFFFFFF) &&
                    GetFileTime(hFile, NULL, NULL, &tWrite)) {
                char szHead[100];
                SYSTEMTIME tLocal;
                char buf[INTERNET_RFC1123_BUFSIZE];

                FileTimeToSystemTime(&tWrite, &tLocal);
                InternetTimeFromSystemTime(&tLocal, INTERNET_RFC1123_FORMAT,
                        buf, INTERNET_RFC1123_BUFSIZE);
                sprintf(szHead, "Range: bytes=%d-\r\nIf-Range: %s\r\n",
                        fileSize, buf);
                HttpAddRequestHeaders(hRequest, szHead, lstrlen(szHead),
                        HTTP_ADDREQ_FLAG_ADD|HTTP_ADDREQ_FLAG_REPLACE);
                rangereq = TRUE;
            }

            // This is a loop to handle various potential error when the
            // connection is made
            BOOL bCont = TRUE;

            while ((FALSE == ::HttpSendRequest(hRequest, NULL, NULL, NULL, NULL))
            && bCont ) {
                // We might have an invalid CA.
                DWORD dwErrorCode = GetLastError();

                switch(dwErrorCode) {
                    case E_JDHELPER_TIMEOUT:
                    case E_JDHELPER_NAME_NOT_RESOLVED:
                    case E_JDHELPER_CANNOT_CONNECT: {
                        bCont = FALSE;
                        // Display the information dialog
                        if (bUIFeedback) {
                            // decrement download counter to prevent progress
                            // dialog from popping up while the message box is
                            // up
                            m_dlg->bundleInstallComplete();
                            if (dwErrorCode == E_JDHELPER_TIMEOUT) {
                                bRetryHttpRequest =
                                    (IDRETRY == m_dlg->SafeMessageBox(
                                       IDS_HTTP_STATUS_REQUEST_TIMEOUT,
                                       IDS_HTTP_INSTRUCTION_REQUEST_TIMEOUT,
                                       IDS_ERROR_CAPTION,
                                       DIALOG_ERROR_RETRYCANCEL));
                            } else {
                                bRetryHttpRequest =
                                    (IDRETRY == m_dlg->SafeMessageBox(
                                       IDS_HTTP_STATUS_SERVER_NOT_REACHABLE,
                                       IDS_HTTP_INSTRUCTION_SERVER_NOT_REACHABLE,
                                       IDS_ERROR_CAPTION,
                                       DIALOG_ERROR_RETRYCANCEL));
                            }
                            // re-increment counter because it will be decremented
                            // again upon return
                            m_dlg->bundleInstallStart();
                            bCont = bRetryHttpRequest;
                        }
                        break;
                    }
                    case ERROR_INTERNET_INVALID_CA:
                    case ERROR_INTERNET_SEC_CERT_CN_INVALID:
                    case ERROR_INTERNET_SEC_CERT_DATE_INVALID:
                    case ERROR_INTERNET_HTTP_TO_HTTPS_ON_REDIR:
                    case ERROR_INTERNET_INCORRECT_PASSWORD:
                    case ERROR_INTERNET_CLIENT_AUTH_CERT_NEEDED:
                    default: {
                        // Unless the user agrees to continue, we just
                        // abandon now !
                        bCont = FALSE;

                        // Make sure to test the return code from
                        // InternetErrorDlg user may click OK or Cancel. In
                        // case of Cancel, request should not be resubmitted
                        if (bUIFeedback) {
                            if (ERROR_SUCCESS == ::InternetErrorDlg(
                                    NULL, hRequest,
                                    dwErrorCode,
                                    FLAGS_ERROR_UI_FILTER_FOR_ERRORS |
                                    FLAGS_ERROR_UI_FLAGS_GENERATE_DATA |
                                    FLAGS_ERROR_UI_FLAGS_CHANGE_OPTIONS,
                                    NULL))
                                bCont = TRUE;
                        }
                    }
                }
            }

            if (bCont == FALSE) {
                // User has denied the request
                dwDownloadError = 1;
                __leave;
            }

            //
            // Read HTTP status code
            //
            DWORD dwErrorCode = GetLastError();
            DWORD dwStatus=0;
            DWORD dwStatusSize = sizeof(DWORD);

            if (FALSE == ::HttpQueryInfo(hRequest, HTTP_QUERY_FLAG_NUMBER |
                    HTTP_QUERY_STATUS_CODE, &dwStatus, &dwStatusSize, NULL)) {
                dwErrorCode = GetLastError();
            }

            bCont = TRUE;
            while ((dwStatus == HTTP_STATUS_PROXY_AUTH_REQ ||
                    dwStatus == HTTP_STATUS_DENIED) &&
                    bCont) {
                int result = ::InternetErrorDlg(GetDesktopWindow(), hRequest, ERROR_INTERNET_INCORRECT_PASSWORD,
                        FLAGS_ERROR_UI_FILTER_FOR_ERRORS |
                        FLAGS_ERROR_UI_FLAGS_CHANGE_OPTIONS |
                        FLAGS_ERROR_UI_FLAGS_GENERATE_DATA,
                        NULL);
                if (ERROR_CANCELLED == result) {
                    bCont = FALSE;
                }
                else {
                    ::HttpSendRequest(hRequest, NULL, 0, NULL, 0);

                    // Reset buffer length
                    dwStatusSize = sizeof(DWORD);

                    ::HttpQueryInfo(hRequest, HTTP_QUERY_FLAG_NUMBER |
                            HTTP_QUERY_STATUS_CODE, &dwStatus, &dwStatusSize,
                            NULL);
                }
            }

            if (dwStatus == HTTP_STATUS_OK ||
                    dwStatus == HTTP_STATUS_PARTIAL_CONTENT) {
                // Determine content length, so we may show the progress bar
                // meaningfully
                //
                nContentLength = 0;
                DWORD nLengthSize = sizeof(DWORD);
                ::HttpQueryInfo(hRequest,
                        HTTP_QUERY_CONTENT_LENGTH | HTTP_QUERY_FLAG_NUMBER,
                        &nContentLength, &nLengthSize, NULL);

                if (nContentLength <= 0) {
                    // If can't estimate content length, estimate it
                    // to be 6MB
                    nContentLength = 15000000;
                }
                else if (rangereq && (fileSize != 0) &&
                        (nContentLength == fileSize)) {
                    // If the file is already downloaded completely and then
                    // we send a range request, the whole file is sent instead
                    // of nothing. So avoid downloading again.
                    // Some times return value is 206, even when whole file
                    // is sent. So check if "Content-range:" is present in the
                    // reply
                    char buffer[256];
                    DWORD length = sizeof(buffer);
                    if(!HttpQueryInfo(hRequest, HTTP_QUERY_CONTENT_RANGE,
                            buffer, &length, NULL)) {
                        if(HttpQueryInfo(hRequest, HTTP_QUERY_LAST_MODIFIED,
                                buffer, &length, NULL)) {
                            SYSTEMTIME systime;
                            FILETIME filtime;
                            InternetTimeToSystemTime(buffer, &systime, NULL);
                            SystemTimeToFileTime(&systime, &filtime);
                            if ((CompareFileTime(&tWrite, &filtime)) == 1) {
                                // no need to download
                                dwDownloadError = 0;
                                __leave;
                            }
                        }
                        else {
                            ::SetFilePointer(hFile, 0, 0, FILE_BEGIN);
                            ::SetEndOfFile(hFile); // truncate the file
                        }
                    }

                }

                TCHAR szBuffer[8096];
                DWORD dwBufferSize = 8096;

                // Read from HTTP connection and write into
                // destination file
                //
                DWORD nRead = 0;
                DWORD dwTotalRead = 0;
                BOOL bCancel = FALSE;

                if (dwStatus == HTTP_STATUS_PARTIAL_CONTENT) {
                    // If we are using resumable download, fake
                    // start time so it looks like we have begun
                    // the download several minutes again.
                    //
                    m_startTime = m_startTime - 100;

                    ::SetFilePointer(hFile, 0, 0, FILE_END); // seek to end
                }
                else {
                    ::SetFilePointer(hFile, 0, 0, FILE_BEGIN);
                    ::SetEndOfFile(hFile); // truncate the file
                }

                do {
                    nRead=0;

                    if (::InternetReadFile(hRequest, szBuffer, dwBufferSize,
                            &nRead)) {
                        if (nRead) {
                            DWORD dwNumberOfBytesWritten = NULL;

                            BOOL ret = WriteFile(hFile, szBuffer, nRead,
                                    &dwNumberOfBytesWritten, NULL);

                            if (!ret) {
                                // WriteFile failed
                                if (bUIFeedback) {
                                    if (GetLastError() == ERROR_DISK_FULL) {
                                       bRetryHttpRequest =
                                            (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_DISK_FULL_ERROR,
                                            IDS_DISK_FULL_ERROR_CAPTION,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            wName));
                                    } else {
                                        bRetryHttpRequest =
                                            (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_DISK_WRITE_ERROR,
                                            IDS_DISK_WRITE_ERROR_CAPTION,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            wName));
                                    }
                                    if (!bRetryHttpRequest) {
                                        dwDownloadError = 1;
                                        break;
                                    }
                                }
                                continue;
                            }
                        }

                        dwTotalRead += nRead;

                        // update download progress dialog
                        m_dlg->OnProgress(nRead);
                        // Check if download has been cancelled
                        if (m_dlg->isDownloadCancelled()) {
                            m_dlg->decrementProgressMax(nContentLength,
                                    dwTotalRead);
                            bCancel = TRUE;
                            break;
                        }

                    }
                    else {
                        bCancel = TRUE;
                        break;
                    }
                }
                while (nRead);


                if (bCancel) {
                    // User has cancelled the operation or InternetRead failed
                    // don't do return here, we need to cleanup
                    dwDownloadError = 1;
                    __leave;
                }
            }
            else if (dwStatus == 416 && (fileSize != 0) &&
                    (fileSize != 0xFFFFFFFF)) {
                // This error could be returned, When the full file exists
                // and a range request is sent with range beyond filessize.
                // The best way to fix this is in future is, to send HEAD
                // request and get filelength before sending range request.
                dwDownloadError = 0;
                __leave;
            }
            else if (dwStatus == 403) { // Forbidden from Akamai means we need to get a new download token
                JNIEnv *env = m_dlg->getJNIEnv();
                jclass exceptionClass = env->FindClass("java/net/HttpRetryException");
                if (exceptionClass == NULL) {
                    /* Unable to find the exception class, give up. */
                    __leave;
                }
                jmethodID constructor;
                constructor = env->GetMethodID(exceptionClass,
                               "<init>", "(Ljava/lang/String;I)V");
                if (constructor != NULL) {
                    jobject exception = env->NewObject(exceptionClass,
                            constructor, env->NewStringUTF("Forbidden"),
                            403);
                    env->Throw((jthrowable) exception);
                }
                __leave;
            }
            else if(dwStatus >= 400 && dwStatus < 600) {
                /* NB: Following case seems to be never used!

                   HTTP_STATUS_FORBIDDEN is the same as 403 and
                   403 was specially handled few lines above! */
                if (dwStatus == HTTP_STATUS_FORBIDDEN) {
                    if (bUIFeedback) {
                        bRetryHttpRequest = (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_HTTP_STATUS_FORBIDDEN,
                                            IDS_HTTP_INSTRUCTION_FORBIDDEN,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            L"403"));
                    }
                }
                else if (dwStatus == HTTP_STATUS_SERVER_ERROR) {
                    if (bUIFeedback) {
                       bRetryHttpRequest = (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_HTTP_STATUS_SERVER_ERROR,
                                            IDS_HTTP_INSTRUCTION_UNKNOWN_ERROR,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            L"500"));
                    }
                }
                else if (dwStatus == HTTP_STATUS_SERVICE_UNAVAIL) {
                    if (numberOfRetry < 5) {
                        // If the server is busy, automatically retry

                        // We wait couple seconds before retry to avoid
                        // congestion
                        for (long i = (long) secondsToWait; i >= 0; i--) {
                            // Update status
                            if (bUIFeedback) {
                                char szBuffer[BUFFER_SIZE];
                                ::LoadString(_Module.GetResourceInstance(),
                                        IDS_DOWNLOAD_STATUS_RETRY, szStatus,
                                        BUFFER_SIZE);
                                wsprintf(szBuffer, szStatus, i);

                                ::SetWindowText(hProgressInfo, szBuffer);
                            }

                            // Sleep 1 second
                            ::Sleep(1000);
                        }

                        // We use a semi-binary backoff algorithm to
                        // determine seconds to wait
                        numberOfRetry += 1;
                        secondsToWait = secondsToWait + 30;
                        bRetryHttpRequest = TRUE;

                        continue;
                    }
                    else {
                        if (bUIFeedback) {
                            bRetryHttpRequest = (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_HTTP_STATUS_SERVICE_UNAVAIL,
                                            IDS_HTTP_INSTRUCTION_SERVICE_UNAVAIL,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            L"503"));

                            if (bRetryHttpRequest) {
                                numberOfRetry = 0;
                                secondsToWait = 60;
                                continue;
                            }
                        }
                    }
                }
                else {
                    if (bUIFeedback) {
                        WCHAR szBuffer[10];
                        _snwprintf(szBuffer, 10, L"%d", dwStatus);
                        bRetryHttpRequest = (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_HTTP_STATUS_OTHER,
                                            IDS_HTTP_INSTRUCTION_UNKNOWN_ERROR,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            szBuffer));
                    }
                }
                if (!bRetryHttpRequest) {
                    dwDownloadError = 1;
                }
            }
            else {
                if (bUIFeedback) {
                    WCHAR szBuffer[10];
                    _snwprintf(szBuffer, 10, L"%d", dwStatus);
                    bRetryHttpRequest = (IDRETRY == m_dlg->SafeMessageBox(
                                            IDS_HTTP_STATUS_OTHER,
                                            IDS_HTTP_INSTRUCTION_UNKNOWN_ERROR,
                                            IDS_ERROR_CAPTION,
                                            DIALOG_ERROR_RETRYCANCEL,
                                            szBuffer));
                }
                if (!bRetryHttpRequest) {
                    dwDownloadError = 1;
                }
            }



            // Close HTTP request
            //
            // This is necessary if the HTTP request
            // is retried
            if (hRequest)
                ::InternetCloseHandle(hRequest);
            if (hFile != INVALID_HANDLE_VALUE) {
                ::CloseHandle(hFile);
                hFile = INVALID_HANDLE_VALUE;
            }
        }
        while (bRetryHttpRequest);
    }
    __finally {
        if (hRequest)
            ::InternetCloseHandle(hRequest);

        if (hConnect)
            ::InternetCloseHandle(hConnect);

        if (hOpen)
            ::InternetCloseHandle(hOpen);

        if (hFile != INVALID_HANDLE_VALUE)
            ::CloseHandle(hFile);
    }



    // Exit dialog
    if (dwDownloadError == 0) {
        return S_OK;
    } else {
        DeleteFile(szLocalFile);
        return E_FAIL;
    }
}
