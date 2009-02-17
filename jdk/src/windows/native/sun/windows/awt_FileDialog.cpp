/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "awt_FileDialog.h"
#include "awt_Dialog.h"
#include "awt_Toolkit.h"
#include "ComCtl32Util.h"
#include <commdlg.h>
#include <cderr.h>
#include <shlobj.h>


/************************************************************************
 * AwtFileDialog fields
 */

/* WFileDialogPeer ids */
jfieldID AwtFileDialog::parentID;
jfieldID AwtFileDialog::fileFilterID;
jmethodID AwtFileDialog::setHWndMID;
jmethodID AwtFileDialog::handleSelectedMID;
jmethodID AwtFileDialog::handleCancelMID;
jmethodID AwtFileDialog::checkFilenameFilterMID;

/* FileDialog ids */
jfieldID AwtFileDialog::modeID;
jfieldID AwtFileDialog::dirID;
jfieldID AwtFileDialog::fileID;
jfieldID AwtFileDialog::filterID;

/* Localized filter string */
#define MAX_FILTER_STRING       128
static TCHAR s_fileFilterString[MAX_FILTER_STRING];
/* Non-localized suffix of the filter string */
static const TCHAR s_additionalString[] = TEXT(" (*.*)\0*.*\0");

/***********************************************************************/

void
AwtFileDialog::Initialize(JNIEnv *env, jstring filterDescription)
{
    int length = env->GetStringLength(filterDescription);
    DASSERT(length + 1 < MAX_FILTER_STRING);
    LPCTSTR tmp = JNU_GetStringPlatformChars(env, filterDescription, NULL);
    _tcscpy(s_fileFilterString, tmp);
    JNU_ReleaseStringPlatformChars(env, filterDescription, tmp);

    //AdditionalString should be terminated by two NULL characters (Windows
    //requirement), so we have to organize the following cycle and use memcpy
    //unstead of, for example, strcat.
    LPTSTR s = s_fileFilterString;
    while (*s) {
        ++s;
        DASSERT(s < s_fileFilterString + MAX_FILTER_STRING);
    }
    DASSERT(s + sizeof(s_additionalString) < s_fileFilterString + MAX_FILTER_STRING);
    memcpy(s, s_additionalString, sizeof(s_additionalString));
}

LRESULT CALLBACK FileDialogWndProc(HWND hWnd, UINT message,
                                        WPARAM wParam, LPARAM lParam)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    switch (message) {
        case WM_COMMAND: {
            if (LOWORD(wParam) == IDCANCEL)
            {
                // Unlike Print/Page dialogs, we only handle IDCANCEL here and
                // don't handle IDOK. This is because user can press OK button
                // when no file is selected, and the dialog is not closed. So
                // OK button is handled in the CDN_FILEOK notification handler
                // (see FileDialogHookProc below)
                jobject peer = (jobject)(::GetProp(hWnd, ModalDialogPeerProp));
                env->CallVoidMethod(peer, AwtFileDialog::setHWndMID, (jlong)0);
            }
            break;
        }
    }

    WNDPROC lpfnWndProc = (WNDPROC)(::GetProp(hWnd, NativeDialogWndProcProp));
    return ComCtl32Util::GetInstance().DefWindowProc(lpfnWndProc, hWnd, message, wParam, lParam);
}

static UINT_PTR CALLBACK
FileDialogHookProc(HWND hdlg, UINT uiMsg, WPARAM wParam, LPARAM lParam)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    TRY;

    HWND parent = ::GetParent(hdlg);

    switch(uiMsg) {
        case WM_INITDIALOG: {
            OPENFILENAME *ofn = (OPENFILENAME *)lParam;
            jobject peer = (jobject)(ofn->lCustData);
            env->CallVoidMethod(peer, AwtFileDialog::setHWndMID,
                                (jlong)parent);
            ::SetProp(parent, ModalDialogPeerProp, reinterpret_cast<HANDLE>(peer));

            // fix for 4508670 - disable CS_SAVEBITS
            DWORD style = ::GetClassLong(hdlg,GCL_STYLE);
            ::SetClassLong(hdlg,GCL_STYLE,style & ~CS_SAVEBITS);

            // set appropriate icon for parentless dialogs
            jobject awtParent = env->GetObjectField(peer, AwtFileDialog::parentID);
            if (awtParent == NULL) {
                ::SendMessage(parent, WM_SETICON, (WPARAM)ICON_BIG,
                              (LPARAM)AwtToolkit::GetInstance().GetAwtIcon());
            } else {
                env->DeleteLocalRef(awtParent);
            }

            // subclass dialog's parent to receive additional messages
            WNDPROC lpfnWndProc = ComCtl32Util::GetInstance().SubclassHWND(parent,
                                                                           FileDialogWndProc);
            ::SetProp(parent, NativeDialogWndProcProp, reinterpret_cast<HANDLE>(lpfnWndProc));

            break;
        }
        case WM_DESTROY: {
            WNDPROC lpfnWndProc = (WNDPROC)(::GetProp(parent, NativeDialogWndProcProp));
            ComCtl32Util::GetInstance().UnsubclassHWND(parent,
                                                       FileDialogWndProc,
                                                       lpfnWndProc);
            ::RemoveProp(parent, ModalDialogPeerProp);
            ::RemoveProp(parent, NativeDialogWndProcProp);
            break;
        }
        case WM_NOTIFY: {
            OFNOTIFYEX *notifyEx = (OFNOTIFYEX *)lParam;
            if (notifyEx) {
                jobject peer = (jobject)(::GetProp(parent, ModalDialogPeerProp));
                if (notifyEx->hdr.code == CDN_INCLUDEITEM) {
                    LPITEMIDLIST pidl = (LPITEMIDLIST)notifyEx->pidl;
                    // Get the filename and directory
                    TCHAR szPath[MAX_PATH];
                    if (!::SHGetPathFromIDList(pidl, szPath)) {
                        return TRUE;
                    }
                    jstring strPath = JNU_NewStringPlatform(env, szPath);
                    // Call FilenameFilter.accept with path and filename
                    UINT uRes = (env->CallBooleanMethod(peer,
                        AwtFileDialog::checkFilenameFilterMID, strPath) == JNI_TRUE);
                    env->DeleteLocalRef(strPath);
                    return uRes;
                } else if (notifyEx->hdr.code == CDN_FILEOK) {
                    // This notification is sent when user selects some file and presses
                    // OK button; it is not sent when no file is selected. So it's time
                    // to unblock all the windows blocked by this dialog as it will
                    // be closed soon
                    env->CallVoidMethod(peer, AwtFileDialog::setHWndMID, (jlong)0);
                }
            }
            break;
        }
    }

    return FALSE;

    CATCH_BAD_ALLOC_RET(TRUE);
}

void
AwtFileDialog::Show(void *p)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject peer;
    WCHAR unicodeChar = L' ';
    LPTSTR fileBuffer = NULL;
    LPTSTR currentDirectory = NULL;
    OPENFILENAME ofn;
    jint mode = 0;
    BOOL result = FALSE;
    DWORD dlgerr;
    jstring directory = NULL;
    jstring title = NULL;
    jstring file = NULL;
    jobject fileFilter = NULL;
    jobject target = NULL;
    jobject parent = NULL;
    AwtComponent* awtParent = NULL;

    /*
     * There's a situation (see bug 4906972) when InvokeFunction (by which this method is called)
     * returnes earlier than this method returnes. Probably it's caused due to ReplyMessage system call.
     * So for the avoidance of this mistiming we need to make new global reference here
     * (not local as it's used by the hook) and then manage it independently of the calling thread.
     */
    peer = env->NewGlobalRef((jobject)p);

    try {
        DASSERT(peer);
        target = env->GetObjectField(peer, AwtObject::targetID);
        parent = env->GetObjectField(peer, AwtFileDialog::parentID);
        if (parent != NULL) {
            awtParent = (AwtComponent *)JNI_GET_PDATA(parent);
        }
//      DASSERT(awtParent);
        title = (jstring)(env)->GetObjectField(target, AwtDialog::titleID);
        HWND hwndOwner = awtParent ? awtParent->GetHWnd() : NULL;

        if (title == NULL || env->GetStringLength(title)==0) {
            title = JNU_NewStringPlatform(env, &unicodeChar);
        }

        JavaStringBuffer titleBuffer(env, title);
        directory =
            (jstring)env->GetObjectField(target, AwtFileDialog::dirID);
        JavaStringBuffer directoryBuffer(env, directory);

        fileBuffer = new TCHAR[MAX_PATH+1];

        file = (jstring)env->GetObjectField(target, AwtFileDialog::fileID);
        if (file != NULL) {
            LPCTSTR tmp = JNU_GetStringPlatformChars(env, file, NULL);
            _tcscpy(fileBuffer, tmp);
            JNU_ReleaseStringPlatformChars(env, file, tmp);
        } else {
            fileBuffer[0] = _T('\0');
        }

        memset(&ofn, 0, sizeof(ofn));

        ofn.lStructSize = sizeof(ofn);
        ofn.lpstrFilter = s_fileFilterString;
        ofn.nFilterIndex = 1;
        /*
          Fix for 6488834.
          To disable Win32 native parent modality we have to set
          hwndOwner field to either NULL or some hidden window. For
          parentless dialogs we use NULL to show them in the taskbar,
          and for all other dialogs AwtToolkit's HWND is used.
        */
        if (awtParent != NULL)
        {
            ofn.hwndOwner = AwtToolkit::GetInstance().GetHWnd();
        }
        else
        {
            ofn.hwndOwner = NULL;
        }
        ofn.lpstrFile = fileBuffer;
        ofn.nMaxFile = MAX_PATH;
        ofn.lpstrTitle = titleBuffer;
        ofn.lpstrInitialDir = directoryBuffer;
        ofn.Flags = OFN_LONGNAMES | OFN_OVERWRITEPROMPT | OFN_HIDEREADONLY |
                    OFN_ENABLEHOOK | OFN_EXPLORER | OFN_ENABLESIZING;
        fileFilter = env->GetObjectField(peer,
        AwtFileDialog::fileFilterID);
    if (!JNU_IsNull(env,fileFilter)) {
        ofn.Flags |= OFN_ENABLEINCLUDENOTIFY;
    }
        ofn.lCustData = (LPARAM)peer;
        ofn.lpfnHook = (LPOFNHOOKPROC)FileDialogHookProc;

        // Save current directory, so we can reset if it changes.
        currentDirectory = new TCHAR[MAX_PATH+1];

        VERIFY(::GetCurrentDirectory(MAX_PATH, currentDirectory) > 0);

        mode = env->GetIntField(target, AwtFileDialog::modeID);

        AwtDialog::CheckInstallModalHook();

        // show the Win32 file dialog
        if (mode == java_awt_FileDialog_LOAD) {
            result = AwtFileDialog::GetOpenFileName(&ofn);
        } else {
            result = AwtFileDialog::GetSaveFileName(&ofn);
        }
        // Fix for 4181310: FileDialog does not show up.
        // If the dialog is not shown because of invalid file name
        // replace the file name by empty string.
        if (!result) {
            dlgerr = ::CommDlgExtendedError();
            if (dlgerr == FNERR_INVALIDFILENAME) {
                _tcscpy(fileBuffer, TEXT(""));
                if (mode == java_awt_FileDialog_LOAD) {
                    result = AwtFileDialog::GetOpenFileName(&ofn);
                } else {
                    result = AwtFileDialog::GetSaveFileName(&ofn);
                }
            }
        }

        AwtDialog::CheckUninstallModalHook();

        DASSERT(env->GetLongField(peer, AwtComponent::hwndID) == 0L);

        AwtDialog::ModalActivateNextWindow(NULL, target, peer);

        VERIFY(::SetCurrentDirectory(currentDirectory));

        // Report result to peer.
        if (result) {
            jstring tmpJString = (_tcslen(ofn.lpstrFile) == 0 ?
                JNU_NewStringPlatform(env, L"") :
                JNU_NewStringPlatform(env, ofn.lpstrFile));
            env->CallVoidMethod(peer, AwtFileDialog::handleSelectedMID, tmpJString);
            env->DeleteLocalRef(tmpJString);
        } else {
            env->CallVoidMethod(peer, AwtFileDialog::handleCancelMID);
        }
        DASSERT(!safe_ExceptionOccurred(env));
    } catch (...) {

        env->DeleteLocalRef(target);
        env->DeleteLocalRef(parent);
        env->DeleteLocalRef(title);
        env->DeleteLocalRef(directory);
        env->DeleteLocalRef(file);
        env->DeleteLocalRef(fileFilter);
        env->DeleteGlobalRef(peer);

        delete[] currentDirectory;
        delete[] fileBuffer;
        throw;
    }

    env->DeleteLocalRef(target);
    env->DeleteLocalRef(parent);
    env->DeleteLocalRef(title);
    env->DeleteLocalRef(directory);
    env->DeleteLocalRef(file);
    env->DeleteLocalRef(fileFilter);
    env->DeleteGlobalRef(peer);

    delete[] currentDirectory;
    delete[] fileBuffer;
}

BOOL
AwtFileDialog::GetOpenFileName(LPOPENFILENAME data) {
    return static_cast<BOOL>(reinterpret_cast<INT_PTR>(
        AwtToolkit::GetInstance().InvokeFunction((void*(*)(void*))
                     ::GetOpenFileName, data)));

}

BOOL
AwtFileDialog::GetSaveFileName(LPOPENFILENAME data) {
    return static_cast<BOOL>(reinterpret_cast<INT_PTR>(
        AwtToolkit::GetInstance().InvokeFunction((void *(*)(void *))
                     ::GetSaveFileName, data)));

}

BOOL AwtFileDialog::InheritsNativeMouseWheelBehavior() {return true;}

void AwtFileDialog::_DisposeOrHide(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jobject self = (jobject)param;

    HWND hdlg = (HWND)(env->GetLongField(self, AwtComponent::hwndID));
    if (::IsWindow(hdlg))
    {
        ::SendMessage(hdlg, WM_COMMAND, MAKEWPARAM(IDCANCEL, 0),
                      (LPARAM)hdlg);
    }

    env->DeleteGlobalRef(self);
}

void AwtFileDialog::_ToFront(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jobject self = (jobject)param;
    HWND hdlg = (HWND)(env->GetLongField(self, AwtComponent::hwndID));
    if (::IsWindow(hdlg))
    {
        ::SetWindowPos(hdlg, HWND_TOP, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
    }

    env->DeleteGlobalRef(self);
}

void AwtFileDialog::_ToBack(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jobject self = (jobject)param;
    HWND hdlg = (HWND)(env->GetLongField(self, AwtComponent::hwndID));
    if (::IsWindow(hdlg))
    {
        ::SetWindowPos(hdlg, HWND_BOTTOM, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
    }

    env->DeleteGlobalRef(self);
}

/************************************************************************
 * WFileDialogPeer native methods
 */

extern "C" {

JNIEXPORT void JNICALL
Java_sun_awt_windows_WFileDialogPeer_initIDs(JNIEnv *env, jclass cls)
{
    TRY;

    AwtFileDialog::parentID =
        env->GetFieldID(cls, "parent", "Lsun/awt/windows/WComponentPeer;");
    AwtFileDialog::fileFilterID =
        env->GetFieldID(cls, "fileFilter", "Ljava/io/FilenameFilter;");
    AwtFileDialog::setHWndMID =
        env->GetMethodID(cls, "setHWnd", "(J)V");
    AwtFileDialog::handleSelectedMID =
        env->GetMethodID(cls, "handleSelected", "(Ljava/lang/String;)V");
    AwtFileDialog::handleCancelMID =
        env->GetMethodID(cls, "handleCancel", "()V");
    AwtFileDialog::checkFilenameFilterMID =
        env->GetMethodID(cls, "checkFilenameFilter", "(Ljava/lang/String;)Z");

    /* java.awt.FileDialog fields */
    cls = env->FindClass("java/awt/FileDialog");
    if (cls == NULL) {
        return;
    }
    AwtFileDialog::modeID = env->GetFieldID(cls, "mode", "I");
    AwtFileDialog::dirID = env->GetFieldID(cls, "dir", "Ljava/lang/String;");
    AwtFileDialog::fileID = env->GetFieldID(cls, "file", "Ljava/lang/String;");
    AwtFileDialog::filterID =
        env->GetFieldID(cls, "filter", "Ljava/io/FilenameFilter;");

    DASSERT(AwtFileDialog::parentID != NULL);
    DASSERT(AwtFileDialog::setHWndMID != NULL);
    DASSERT(AwtFileDialog::handleSelectedMID != NULL);
    DASSERT(AwtFileDialog::handleCancelMID != NULL);

    DASSERT(AwtFileDialog::modeID != NULL);
    DASSERT(AwtFileDialog::dirID != NULL);
    DASSERT(AwtFileDialog::fileID != NULL);
    DASSERT(AwtFileDialog::filterID != NULL);

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WFileDialogPeer_setFilterString(JNIEnv *env, jclass cls,
                                                     jstring filterDescription)
{
    TRY;

    AwtFileDialog::Initialize(env, filterDescription);

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WFileDialogPeer__1show(JNIEnv *env, jobject peer)
{
    TRY;

    /*
     * Fix for 4906972.
     * 'peer' reference has to be global as it's used further in another thread.
     */
    jobject peerGlobal = env->NewGlobalRef(peer);

    AwtToolkit::GetInstance().InvokeFunction(AwtFileDialog::Show, peerGlobal);

    env->DeleteGlobalRef(peerGlobal);

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WFileDialogPeer__1dispose(JNIEnv *env, jobject peer)
{
    TRY_NO_VERIFY;

    jobject peerGlobal = env->NewGlobalRef(peer);

    AwtToolkit::GetInstance().SyncCall(AwtFileDialog::_DisposeOrHide,
        (void *)peerGlobal);
    // peerGlobal ref is deleted in _DisposeOrHide

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WFileDialogPeer__1hide(JNIEnv *env, jobject peer)
{
    TRY;

    jobject peerGlobal = env->NewGlobalRef(peer);

    AwtToolkit::GetInstance().SyncCall(AwtFileDialog::_DisposeOrHide,
        (void *)peerGlobal);
    // peerGlobal ref is deleted in _DisposeOrHide

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WFileDialogPeer_toFront(JNIEnv *env, jobject peer)
{
    TRY;

    AwtToolkit::GetInstance().SyncCall(AwtFileDialog::_ToFront,
                                       (void *)(env->NewGlobalRef(peer)));
    // global ref is deleted in _ToFront

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WFileDialogPeer_toBack(JNIEnv *env, jobject peer)
{
    TRY;

    AwtToolkit::GetInstance().SyncCall(AwtFileDialog::_ToBack,
                                       (void *)(env->NewGlobalRef(peer)));
    // global ref is deleted in _ToBack

    CATCH_BAD_ALLOC;
}

} /* extern "C" */
