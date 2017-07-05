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

#ifndef BUFFER_SIZE
#define BUFFER_SIZE 2048
#endif

#define E_JDHELPER_TIMEOUT               12002
#define E_JDHELPER_NAME_NOT_RESOLVED     12007
#define E_JDHELPER_CANNOT_CONNECT        12029

#include <jni.h>
#include "DownloadDialog.h"

class DownloadHelper {
public:
    DownloadHelper();
    ~DownloadHelper();

    HRESULT doDownload();

    void setFile(LPCTSTR pszFileName) {
        m_pszFileName = pszFileName;
    }

    void setURL(LPCTSTR pszURL) {
        m_pszURL = pszURL;
    }

    void setNameText(LPTSTR pszNameText) {
        m_pszNameText = pszNameText;
    }

    void setShowProgressDialog(BOOL showProgress) {
        m_showProgressDialog = showProgress;
    }

    void setDownloadDialog(CDownloadDialog* dialog) {
        m_dlg = dialog;
    }

    void setJavaVM(JavaVM *jvm) {
        m_jvm = jvm;
    }

private:
    HRESULT DownloadFile(const TCHAR* szURL, const TCHAR* szLocalFile,
            BOOL bResumable, BOOL bUIFeedback);

    BOOL m_showProgressDialog;
    LPCTSTR m_pszURL;
    LPCTSTR m_pszFileName;
    LPTSTR m_pszNameText;
    time_t m_startTime;
    CComAutoCriticalSection m_csDownload;
    CDownloadDialog* m_dlg;
    JavaVM* m_jvm;
};
