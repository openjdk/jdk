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

#ifndef AWT_TEXTCOMPONENT_H
#define AWT_TEXTCOMPONENT_H

#include "awt_Component.h"

#include "java_awt_TextComponent.h"
#include "sun_awt_windows_WTextComponentPeer.h"

#include <ole2.h>
#include <richedit.h>
#include <richole.h>


/************************************************************************
 * AwtTextComponent class
 */

class AwtTextComponent : public AwtComponent {
public:
    /* java.awt.TextComponent canAccessClipboard field ID */
    static jfieldID canAccessClipboardID;

    AwtTextComponent();

    virtual LPCTSTR GetClassName();

    int RemoveCR(WCHAR *pStr);

    virtual LONG getJavaSelPos(LONG orgPos);
    virtual LONG getWin32SelPos(LONG orgPos);

    void CheckLineSeparator(WCHAR *pStr);

    virtual void SetSelRange(LONG start, LONG end);

    INLINE void SetText(LPCTSTR text) {
        ::SetWindowText(GetHWnd(), text);
    }

    INLINE virtual int GetText(LPTSTR buffer, int size) {
        return ::GetWindowText(GetHWnd(), buffer, size);
    }

    // called on Toolkit thread from JNI
    static jstring _GetText(void *param);

    BOOL ActMouseMessage(MSG* pMsg);

    void SetFont(AwtFont* font);

    /*
     * Windows message handler functions
     */
    MsgRouting WmNotify(UINT notifyCode);
    MsgRouting HandleEvent(MSG *msg, BOOL synthetic);
    MsgRouting WmPaste();

/*  To be fully implemented in a future release

    MsgRouting WmKeyDown(UINT wkey, UINT repCnt,
                         UINT flags, BOOL system);  // accessibility support
*/


    //im --- for over the spot composition
    void SetCompositionWindow(RECT& rect);

    INLINE HWND GetDBCSEditHandle() { return GetHWnd(); }

    BOOL m_isLFonly;
    BOOL m_EOLchecked;

    // some methods invoked on Toolkit thread
    static void _SetText(void *param);
    static jint _GetSelectionStart(void *param);
    static jint _GetSelectionEnd(void *param);
    static void _Select(void *param);
    static void _EnableEditing(void *param);

  protected:
    INLINE LONG GetStartSelectionPos() { return m_lStartPos; }
    INLINE LONG GetEndSelectionPos() { return m_lEndPos; }
    INLINE LONG GetLastSelectionPos() { return m_lLastPos; }
    INLINE VOID SetStartSelectionPos(LONG lPos) { m_lStartPos = lPos; }
    INLINE VOID SetEndSelectionPos(LONG lPos) { m_lEndPos = lPos; }
    INLINE VOID SetLastSelectionPos(LONG lPos) { m_lLastPos = lPos; }

    // Used to prevent untrusted code from synthesizing a WM_PASTE message
    // by posting a <CTRL>-V KeyEvent
    BOOL    m_synthetic;
    virtual void EditSetSel(CHARRANGE &cr) = 0;
    virtual void EditGetSel(CHARRANGE &cr) = 0;
    virtual LONG EditGetCharFromPos(POINT& pt) = 0;

private:

    // Fields to track the selection state while the left mouse button is
    // pressed. They are used to simulate autoscrolling.
    LONG    m_lStartPos;
    LONG    m_lEndPos;
    LONG    m_lLastPos;


    HFONT m_hFont;
    //im --- end


    //
    // Accessibility support
    //
//public:
//    jlong javaEventsMask;
};

#endif /* AWT_TEXTCOMPONENT_H */
