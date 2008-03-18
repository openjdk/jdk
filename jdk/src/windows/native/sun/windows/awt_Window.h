/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef AWT_WINDOW_H
#define AWT_WINDOW_H

#include "awt_Canvas.h"

#include "java_awt_Window.h"
#include "sun_awt_windows_WWindowPeer.h"

// property name tagging windows disabled by modality
static LPCTSTR ModalBlockerProp = TEXT("SunAwtModalBlockerProp");
static LPCTSTR ModalDialogPeerProp = TEXT("SunAwtModalDialogPeerProp");

#ifndef WH_MOUSE_LL
#define WH_MOUSE_LL 14
#endif

// WS_EX_NOACTIVATE is not defined in the headers we build with
#define AWT_WS_EX_NOACTIVATE        0x08000000L

class AwtFrame;

/************************************************************************
 * AwtWindow class
 */

class AwtWindow : public AwtCanvas {
public:

    /* java.awt.Window field ids */
    static jfieldID warningStringID;
    static jfieldID locationByPlatformID;
    static jfieldID screenID; /* screen number passed over from WindowPeer */
    static jfieldID autoRequestFocusID;

    /* WWindowPeer class */
    static jclass wwindowPeerCls;
    /* long[] getActiveWindowHandles() method in WWindowPeer */
    static jmethodID getActiveWindowsMID;

    // The coordinates at the peer.
    static jfieldID sysXID;
    static jfieldID sysYID;
    static jfieldID sysWID;
    static jfieldID sysHID;

    AwtWindow();
    virtual ~AwtWindow();

    virtual void Dispose();

    virtual LPCTSTR GetClassName();
    virtual void FillClassInfo(WNDCLASSEX *lpwc);

    static AwtWindow* Create(jobject self, jobject parent);

    // Returns TRUE if this Window is equal to or one of owners of wnd
    BOOL IsOneOfOwnersOf(AwtWindow * wnd);

    /* Update the insets for this Window (container), its peer &
     * optional other
     */
    BOOL UpdateInsets(jobject insets = 0);
    BOOL HasValidRect();

    static BOOL CALLBACK UpdateOwnedIconCallback(HWND hwnd, LPARAM param);

    INLINE AwtFrame * GetOwningFrameOrDialog() { return m_owningFrameDialog; }

    HWND GetTopLevelHWnd();

    /* Subtract inset values from a window origin. */
    INLINE void SubtractInsetPoint(int& x, int& y) {
        x -= m_insets.left;
        y -= m_insets.top;
    }

    virtual void GetInsets(RECT* rect) {
        VERIFY(::CopyRect(rect, &m_insets));
    }

    /* to make embedded frames easier */
    virtual BOOL IsEmbeddedFrame() { return FALSE;}

    /* We can hold children */
    virtual BOOL IsContainer() { return TRUE;}

    virtual BOOL IsUndecorated() { return TRUE; }

    INLINE virtual BOOL IsSimpleWindow() { return TRUE; }

    INLINE BOOL IsRetainingHierarchyZOrder() { return m_isRetainingHierarchyZOrder; }

    /* WARNING: don't invoke on Toolkit thread! */
    INLINE BOOL IsAutoRequestFocus() {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        return env->GetBooleanField(GetTarget(env), AwtWindow::autoRequestFocusID);
    }

    INLINE virtual BOOL IsFocusedWindowModalBlocker() {
        return FALSE;
    }

    virtual void Invalidate(RECT* r);
    virtual void Show();
    virtual void SetResizable(BOOL isResizable);
    BOOL IsResizable();
    virtual void RecalcNonClient();
    virtual void RedrawNonClient();
    virtual int  GetScreenImOn();
    virtual void CheckIfOnNewScreen();
    virtual void Grab();
    virtual void Ungrab();
    virtual void Ungrab(BOOL doPost);
    virtual void SetIconData(JNIEnv* env, jintArray iconData, jint w, jint h,
                             jintArray smallIconData, jint smw, jint smh);
    virtual void DoUpdateIcon();
    INLINE HICON GetHIcon() {return m_hIcon;};
    INLINE HICON GetHIconSm() {return m_hIconSm;};
    INLINE BOOL IsIconInherited() {return m_iconInherited;};

    /* Post events to the EventQueue */
    void SendComponentEvent(jint eventId);
    void SendWindowEvent(jint id, HWND opposite = NULL,
                         jint oldState = 0, jint newState = 0);

    BOOL IsFocusableWindow();

    /* some helper methods about blocking windows by modal dialogs */
    INLINE static HWND GetModalBlocker(HWND window) {
        return reinterpret_cast<HWND>(::GetProp(window, ModalBlockerProp));
    }
    static void SetModalBlocker(HWND window, HWND blocker);
    static void SetAndActivateModalBlocker(HWND window, HWND blocker);

    /*
     * Windows message handler functions
     */
    virtual MsgRouting WmActivate(UINT nState, BOOL fMinimized, HWND opposite);
    static void BounceActivation(void *self); // used by WmActivate
    virtual MsgRouting WmCreate();
    virtual MsgRouting WmClose();
    virtual MsgRouting WmDestroy();
    virtual MsgRouting WmShowWindow(BOOL show, UINT status);
    virtual MsgRouting WmDDEnterFullScreen(HMONITOR monitor);
    virtual MsgRouting WmDDExitFullScreen(HMONITOR monitor);
    virtual MsgRouting WmGetMinMaxInfo(LPMINMAXINFO lpmmi);
    virtual MsgRouting WmMove(int x, int y);
    virtual MsgRouting WmSize(UINT type, int w, int h);
    virtual MsgRouting WmSizing();
    virtual MsgRouting WmPaint(HDC hDC);
    virtual MsgRouting WmSettingChange(UINT wFlag, LPCTSTR pszSection);
    virtual MsgRouting WmNcCalcSize(BOOL fCalcValidRects,
                                    LPNCCALCSIZE_PARAMS lpncsp, LRESULT& retVal);
    virtual MsgRouting WmNcPaint(HRGN hrgn);
    virtual MsgRouting WmNcHitTest(UINT x, UINT y, LRESULT& retVal);
    virtual MsgRouting WmNcMouseDown(WPARAM hitTest, int x, int y, int button);
    virtual MsgRouting WmGetIcon(WPARAM iconType, LRESULT& retVal);
    virtual LRESULT WindowProc(UINT message, WPARAM wParam, LPARAM lParam);
    virtual MsgRouting WmWindowPosChanging(LPARAM windowPos);
    virtual MsgRouting WmWindowPosChanged(LPARAM windowPos);

    virtual MsgRouting HandleEvent(MSG *msg, BOOL synthetic);
    virtual void WindowResized();

    void moveToDefaultLocation(); /* moves Window to X,Y specified by Window Manger */

    INLINE virtual BOOL IsTopLevel() { return TRUE; }
    static AwtWindow * GetGrabbedWindow() { return m_grabbedWindow; }

    static void FlashWindowEx(HWND hWnd, UINT count, DWORD timeout, DWORD flags);

    // some methods invoked on Toolkit thread
    static void _ToFront(void *param);
    static void _ToBack(void *param);
    static void _Grab(void *param);
    static void _Ungrab(void *param);
    static void _SetAlwaysOnTop(void *param);
    static void _SetTitle(void *param);
    static void _SetResizable(void *param);
    static void _UpdateInsets(void *param);
    static void _ReshapeFrame(void *param);
    static void _SetIconImagesData(void * param);
    static void _SetMinSize(void* param);
    static jint _GetScreenImOn(void *param);
    static void _SetFocusableWindow(void *param);
    static void _SetModalExcludedNativeProp(void *param);
    static void _ModalDisable(void *param);
    static void _ModalEnable(void *param);

    inline static BOOL IsResizing() {
        return sm_resizing;
    }

private:
    static int ms_instanceCounter;
    static HHOOK ms_hCBTFilter;
    static LRESULT CALLBACK CBTFilter(int nCode, WPARAM wParam, LPARAM lParam);
    static HWND sm_retainingHierarchyZOrderInShow; // a referred window in the process of show
    static BOOL sm_resizing;        /* in the middle of a resizing operation */

    RECT m_insets;          /* a cache of the insets being used */
    RECT m_old_insets;      /* help determine if insets change */
    POINT m_sizePt;         /* the last value of WM_SIZE */
    RECT m_warningRect;     /* The window's warning banner area, if any. */
    AwtFrame *m_owningFrameDialog; /* The nearest Frame/Dialog which owns us */
    BOOL m_isFocusableWindow; /* a cache of Window.isFocusableWindow() return value */
    POINT m_minSize;          /* Minimum size of the window for WM_GETMINMAXINFO message */
    BOOL m_grabbed; // Whether the current window is grabbed
    BOOL m_isRetainingHierarchyZOrder; // Is this a window that shouldn't change z-order of any window
                                       // from its hierarchy when shown. Currently applied to instances of
                                       // javax/swing/Popup$HeavyWeightWindow class.

protected:
    BOOL m_isResizable;
    static AwtWindow* m_grabbedWindow; // Current grabbing window
    HICON m_hIcon;            /* Icon for this window. It can be set explicitely or inherited from the owner */
    HICON m_hIconSm;          /* Small icon for this window. It can be set explicitely or inherited from the owner */
    BOOL m_iconInherited;     /* TRUE if icon is inherited from the owner */
    BOOL m_filterFocusAndActivation; /* Used in the WH_CBT hook */


private:
    int m_screenNum;

    void InitOwner(AwtWindow *owner);
};

#endif /* AWT_WINDOW_H */
