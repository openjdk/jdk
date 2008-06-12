/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef AWT_TRAY_ICON_H
#define AWT_TRAY_ICON_H

#include "awt_Object.h"
#include "awt_Component.h"

#include "java_awt_TrayIcon.h"
#include "sun_awt_windows_WTrayIconPeer.h"
#include "java_awt_event_ActionEvent.h"

#define TRAY_ICON_X_HOTSPOT 0
#define TRAY_ICON_Y_HOTSPOT 0

#define TRAY_ICON_TOOLTIP_MAX_SIZE (IS_WIN2000 ? 128 : 64)

#define TRAY_ICON_BALLOON_TITLE_MAX_SIZE 64
#define TRAY_ICON_BALLOON_INFO_MAX_SIZE  256

// **********************************************************************
// The following definitions are duplicates for those from the shellapi.h
// **********************************************************************

#define AWT_NOTIFYICON_VERSION 3

#define AWT_NIM_SETVERSION  0x00000004

#define AWT_NIN_SELECT          (WM_USER + 0)
#define AWT_NINF_KEY            0x1
#define AWT_NIN_KEYSELECT       (AWT_NIN_SELECT | AWT_NINF_KEY)
#define AWT_NIN_BALLOONSHOW     (WM_USER + 2)
#define AWT_NIN_BALLOONHIDE     (WM_USER + 3)
#define AWT_NIN_BALLOONTIMEOUT  (WM_USER + 4)
#define AWT_NIN_BALLOONUSERCLICK (WM_USER + 5)

#define AWT_NIIF_NONE       0x00000000
#define AWT_NIIF_INFO       0x00000001
#define AWT_NIIF_WARNING    0x00000002
#define AWT_NIIF_ERROR      0x00000003

#define AWT_NIF_INFO        0x00000010

typedef struct _AWT_NOTIFYICONDATA {
    DWORD cbSize;
    HWND hWnd;
    UINT uID;
    UINT uFlags;
    UINT uCallbackMessage;
    HICON hIcon;
    TCHAR szTip[128];

    DWORD dwState;        // _WIN32_IE >= 0x0500
    DWORD dwStateMask;
    TCHAR szInfo[256];
    union {
        UINT  uTimeout;
        UINT  uVersion;
    } DUMMYUNIONNAME;
    TCHAR szInfoTitle[64];
    DWORD dwInfoFlags;

    GUID guidItem;        // _WIN32_IE >= 0x600
} AWT_NOTIFYICONDATA, *PAWT_NOTIFYICONDATA;


/************************************************************************
 * AwtTrayIcon class
 */

class AwtTrayIcon: public AwtObject {
public:
    AwtTrayIcon();
    virtual ~AwtTrayIcon();

    virtual void Dispose();

    BOOL SendTrayMessage(DWORD dwMessage);
    void LinkObjects(JNIEnv *env, jobject peer);
    void UnlinkObjects();

    void InitNID(UINT uID);

    void InitMessage(MSG* msg, UINT message, WPARAM wParam, LPARAM lParam,
                     int x = 0, int y = 0);

    void SendMouseEvent(jint id, jlong when, jint x, jint y, jint modifiers, jint clickCount,
                        jboolean popupTrigger, jint button = 0, MSG *pMsg = NULL);
    void SendActionEvent(jint id, jlong when, jint modifiers, MSG *pMsg = NULL);

    virtual MsgRouting WmAwtTrayNotify(WPARAM wParam, LPARAM lParam);
    virtual MsgRouting WmMouseDown(UINT flags, int x, int y, int button);
    virtual MsgRouting WmMouseUp(UINT flags, int x, int y, int button);
    virtual MsgRouting WmMouseMove(UINT flags, int x, int y);
    virtual MsgRouting WmBalloonUserClick(UINT flags, int x, int y);
    virtual MsgRouting WmKeySelect(UINT flags, int x, int y);
    virtual MsgRouting WmSelect(UINT flags, int x, int y);
    virtual MsgRouting WmContextMenu(UINT flags, int x, int y);
    static MsgRouting WmTaskbarCreated();

    INLINE void SetID(int ID) { m_nid.uID = ID; }
    INLINE int GetID() { return m_nid.uID; }

    void SetToolTip(LPCTSTR tooltip);
    INLINE LPTSTR GetToolTip() { return m_nid.szTip; }

    void SetIcon(HICON hIcon);
    INLINE HICON GetIcon() { return m_nid.hIcon; }

    void DisplayMessage(LPCTSTR caption, LPCTSTR text, LPCTSTR msgType);

    // Adds to the head of the list
    INLINE void AddTrayIconItem(UINT id) {
        TrayIconListItem* item = new TrayIconListItem(id, this);
        item->m_next = sm_trayIconList;
        sm_trayIconList = item;
    }

    static AwtTrayIcon* SearchTrayIconItem(UINT id);
    static void RemoveTrayIconItem(UINT id);

    static LPCTSTR GetClassName();
    static void FillClassInfo(WNDCLASS *lpwc);
    static void RegisterClass();
    static void UnregisterClass();

    static LRESULT CALLBACK TrayWindowProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam);

    static AwtTrayIcon* Create(jobject self, jobject parent);

    static HWND CreateMessageWindow();
    static void DestroyMessageWindow();

    static HBITMAP CreateBMP(HWND hW,int* imageData,int nSS, int nW, int nH);

    // methods called on Toolkit thread
    static void _SetToolTip(void *param);
    static void _SetIcon(void *param);
    static void _UpdateIcon(void *param);
    static void _DisplayMessage(void *param);

    /*
     * java.awt.TrayIcon fields
     */
    static jfieldID idID;
    static jfieldID actionCommandID;

    // ************************

    static HWND sm_msgWindow;
    static int sm_instCount;

private:
    AWT_NOTIFYICONDATA m_nid;

    /* A bitmask keeps the button's numbers as MK_LBUTTON, MK_MBUTTON, MK_RBUTTON
     * which are allowed to
     * generate the CLICK event after the RELEASE has happened.
     * There are conditions that must be true for that sending CLICK event:
     * 1) button was initially PRESSED
     * 2) no movement or drag has happened until RELEASE
    */
    UINT m_mouseButtonClickAllowed;

    class TrayIconListItem {
      public:
        TrayIconListItem(UINT id, AwtTrayIcon* trayIcon) {
            m_ID = id;
            m_trayIcon = trayIcon;
            m_next = NULL;
        }
        UINT m_ID;
        AwtTrayIcon* m_trayIcon;
        TrayIconListItem* m_next;
    };

public:
    static TrayIconListItem* sm_trayIconList;
};

#endif /* AWT_TRAY_ICON_H */
