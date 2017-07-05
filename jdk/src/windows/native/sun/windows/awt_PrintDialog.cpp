/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "awt_PrintDialog.h"
#include "awt_Dialog.h"
#include "awt_PrintControl.h"
#include "awt_Window.h"
#include "ComCtl32Util.h"
#include <sun_awt_windows_WPrintDialog.h>
#include <sun_awt_windows_WPrintDialogPeer.h>

jfieldID AwtPrintDialog::controlID;
jfieldID AwtPrintDialog::parentID;

jmethodID AwtPrintDialog::setHWndMID;

BOOL
AwtPrintDialog::PrintDlg(LPPRINTDLG data) {
    return static_cast<BOOL>(reinterpret_cast<INT_PTR>(
        AwtToolkit::GetInstance().InvokeFunction(
            reinterpret_cast<void *(*)(void *)>(::PrintDlg), data)));
}

LRESULT CALLBACK PrintDialogWndProc(HWND hWnd, UINT message,
                                    WPARAM wParam, LPARAM lParam)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    switch (message) {
        case WM_COMMAND: {
            if ((LOWORD(wParam) == IDOK) ||
                (LOWORD(wParam) == IDCANCEL))
            {
                // If we recieve on of these two notifications, the dialog
                // is about to be closed. It's time to unblock all the
                // windows blocked by this dialog, as doing so from the
                // WM_DESTROY handler is too late
                jobject peer = (jobject)(::GetProp(hWnd, ModalDialogPeerProp));
                env->CallVoidMethod(peer, AwtPrintDialog::setHWndMID, (jlong)0);
            }
            break;
        }
    }

    return ComCtl32Util::GetInstance().DefWindowProc(NULL, hWnd, message, wParam, lParam);
}

static UINT_PTR CALLBACK
PrintDialogHookProc(HWND hdlg, UINT uiMsg, WPARAM wParam, LPARAM lParam)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    TRY;

    switch(uiMsg) {
        case WM_INITDIALOG: {
            PRINTDLG *pd = (PRINTDLG *)lParam;
            jobject peer = (jobject)(pd->lCustData);
            env->CallVoidMethod(peer, AwtPrintDialog::setHWndMID,
                                (jlong)hdlg);
            ::SetProp(hdlg, ModalDialogPeerProp, reinterpret_cast<HANDLE>(peer));

            // fix for 4632159 - disable CS_SAVEBITS
            DWORD style = ::GetClassLong(hdlg, GCL_STYLE);
            ::SetClassLong(hdlg,GCL_STYLE, style & ~CS_SAVEBITS);

            ::SetFocus(hdlg);

            // set appropriate icon for parentless dialogs
            jobject awtParent = env->GetObjectField(peer, AwtPrintDialog::parentID);
            if (awtParent == NULL) {
                ::SendMessage(hdlg, WM_SETICON, (WPARAM)ICON_BIG,
                              (LPARAM)AwtToolkit::GetInstance().GetAwtIcon());
            } else {
                env->DeleteLocalRef(awtParent);
            }

            // subclass dialog's parent to receive additional messages
            ComCtl32Util::GetInstance().SubclassHWND(hdlg,
                                                     PrintDialogWndProc);

            break;
        }
        case WM_DESTROY: {
            ComCtl32Util::GetInstance().UnsubclassHWND(hdlg,
                                                       PrintDialogWndProc,
                                                       NULL);
            ::RemoveProp(hdlg, ModalDialogPeerProp);
            break;
        }
    }
    return FALSE;

    CATCH_BAD_ALLOC_RET(TRUE);
}

void AwtPrintDialog::_ToFront(void *param)
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

void AwtPrintDialog::_ToBack(void *param)
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


extern "C" {
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrintDialog_initIDs(JNIEnv *env, jclass cls)
{
    TRY;

    AwtPrintDialog::controlID =
        env->GetFieldID(cls, "pjob", "Ljava/awt/print/PrinterJob;");
    DASSERT(AwtPrintDialog::controlID != NULL);

    AwtPrintControl::initIDs(env, cls);

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrintDialog_setPeer(JNIEnv *env, jobject target,
                                          jobject peer)
{
    TRY;

    env->SetObjectField(target, AwtComponent::peerID, peer);

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrintDialogPeer_initIDs(JNIEnv *env, jclass cls)
{
    TRY;

    AwtPrintDialog::parentID =
        env->GetFieldID(cls, "parent", "Lsun/awt/windows/WComponentPeer;");
    AwtPrintDialog::setHWndMID =
        env->GetMethodID(cls, "setHWnd", "(J)V");

    DASSERT(AwtPrintDialog::parentID != NULL);
    DASSERT(AwtPrintDialog::setHWndMID != NULL);

    CATCH_BAD_ALLOC;
}

JNIEXPORT jboolean JNICALL
Java_sun_awt_windows_WPrintDialogPeer__1show(JNIEnv *env, jobject peer)
{
    TRY;

    jboolean result = JNI_FALSE;

    // as peer object is used later on another thread, create a global ref
    jobject peerGlobalRef = env->NewGlobalRef(peer);
    DASSERT(peerGlobalRef != NULL);
    jobject target = env->GetObjectField(peerGlobalRef, AwtObject::targetID);
    DASSERT(target != NULL);
    jobject parent = env->GetObjectField(peerGlobalRef, AwtPrintDialog::parentID);
    jobject control = env->GetObjectField(target, AwtPrintDialog::controlID);
    DASSERT(control != NULL);

    AwtComponent *awtParent = (parent != NULL) ? (AwtComponent *)JNI_GET_PDATA(parent) : NULL;
    HWND hwndOwner = awtParent ? awtParent->GetHWnd() : NULL;

    PRINTDLG pd;
    memset(&pd, 0, sizeof(PRINTDLG));
    pd.lStructSize = sizeof(PRINTDLG);
    pd.lCustData = (LPARAM)peerGlobalRef;
    BOOL ret = AwtPrintControl::InitPrintDialog(env, control, pd);
    if (!ret) {
        /* Couldn't use the printer, or spooler isn't running
         * Call Page dialog with ' PD_RETURNDEFAULT' so it doesn't try
         * to show the dialog, but does prompt the user to install a printer.
         * If this returns false, then they declined and we just return.
         */
        pd.Flags = PD_RETURNDEFAULT | PD_RETURNDC;
        ret = AwtPrintDialog::PrintDlg(&pd);
    }
    if (!ret) {
      result = JNI_FALSE;
    }
    else
    {
      pd.lpfnPrintHook = (LPPRINTHOOKPROC)PrintDialogHookProc;
      pd.lpfnSetupHook = (LPSETUPHOOKPROC)PrintDialogHookProc;
      pd.Flags |= PD_ENABLESETUPHOOK | PD_ENABLEPRINTHOOK;
      /*
          Fix for 6488834.
          To disable Win32 native parent modality we have to set
          hwndOwner field to either NULL or some hidden window. For
          parentless dialogs we use NULL to show them in the taskbar,
          and for all other dialogs AwtToolkit's HWND is used.
      */
      if (awtParent != NULL)
      {
          pd.hwndOwner = AwtToolkit::GetInstance().GetHWnd();
      }
      else
      {
          pd.hwndOwner = NULL;
      }

      AwtDialog::CheckInstallModalHook();

      BOOL ret = AwtPrintDialog::PrintDlg(&pd);
      if (ret)
      {
        AwtPrintControl::UpdateAttributes(env, control, pd);
        result = JNI_TRUE;
      }
      else
      {
        result = JNI_FALSE;
      }

      DASSERT(env->GetLongField(peer, AwtComponent::hwndID) == 0L);

      AwtDialog::CheckUninstallModalHook();

      AwtDialog::ModalActivateNextWindow(NULL, target, peer);
    }

    env->DeleteGlobalRef(peerGlobalRef);
    env->DeleteLocalRef(target);
    if (parent != NULL) {
      env->DeleteLocalRef(parent);
    }
    env->DeleteLocalRef(control);

    return result;

    CATCH_BAD_ALLOC_RET(0);
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrintDialogPeer_toFront(JNIEnv *env, jobject peer)
{
    TRY;

    AwtToolkit::GetInstance().SyncCall(AwtPrintDialog::_ToFront,
                                       (void *)(env->NewGlobalRef(peer)));
    // global ref is deleted in _ToFront

    CATCH_BAD_ALLOC;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrintDialogPeer_toBack(JNIEnv *env, jobject peer)
{
    TRY;

    AwtToolkit::GetInstance().SyncCall(AwtPrintDialog::_ToBack,
                                       (void *)(env->NewGlobalRef(peer)));
    // global ref is deleted in _ToBack

    CATCH_BAD_ALLOC;
}

} /* extern "C" */
