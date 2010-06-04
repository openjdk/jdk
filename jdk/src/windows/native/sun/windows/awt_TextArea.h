/*
 * Copyright (c) 1996, 2008, Oracle and/or its affiliates. All rights reserved.
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

#ifndef AWT_TEXTAREA_H
#define AWT_TEXTAREA_H

#include "awt_TextComponent.h"

#include "java_awt_TextArea.h"
#include "sun_awt_windows_WTextAreaPeer.h"

#include <ole2.h>
#include <richedit.h>
#include <richole.h>

/************************************************************************
 * AwtTextArea class
 */

class AwtTextArea : public AwtTextComponent {

    // inner classes
    class OleCallback;

public:

    /* java.awt.TextArea fields ids */
    static jfieldID scrollbarVisibilityID;

    AwtTextArea();
    virtual ~AwtTextArea();

    virtual void Dispose();

    LPCTSTR GetClassName();

    static AwtTextArea* Create(jobject self, jobject parent);

    static size_t CountNewLines(JNIEnv *env, jstring jStr, size_t maxlen);
    static size_t GetALength(JNIEnv* env, jstring jStr, size_t maxlen);

    MsgRouting PreProcessMsg(MSG& msg);

    LRESULT WindowProc(UINT message, WPARAM wParam, LPARAM lParam);
    static LRESULT CALLBACK EditProc(HWND hWnd, UINT message,
                                     WPARAM wParam, LPARAM lParam);

    MsgRouting WmEnable(BOOL fEnabled);
    MsgRouting WmContextMenu(HWND hCtrl, UINT xPos, UINT yPos);
    MsgRouting WmNotify(UINT notifyCode);
    MsgRouting WmNcHitTest(UINT x, UINT y, LRESULT &retVal);
    MsgRouting HandleEvent(MSG *msg, BOOL synthetic);

    INLINE void SetIgnoreEnChange(BOOL b) { m_bIgnoreEnChange = b; }

    virtual void SetColor(COLORREF c);
    virtual void SetBackgroundColor(COLORREF c);
    virtual void Enable(BOOL bEnable);
    virtual BOOL InheritsNativeMouseWheelBehavior();
    virtual void Reshape(int x, int y, int w, int h);

    virtual LONG getJavaSelPos(LONG orgPos);
    virtual LONG getWin32SelPos(LONG orgPos);
    virtual void SetSelRange(LONG start, LONG end);

    // called on Toolkit thread from JNI
    static void _ReplaceText(void *param);

protected:
    INLINE static OleCallback& GetOleCallback() { return sm_oleCallback; }
    void EditSetSel(CHARRANGE &cr);
    void EditGetSel(CHARRANGE &cr);
    LONG EditGetCharFromPos(POINT& pt);
  private:
    // RichEdit 1.0 control generates EN_CHANGE notifications not only
    // on text changes, but also on any character formatting change.
    // This flag is true when the latter case is detected.
    BOOL    m_bIgnoreEnChange;

    // RichEdit 1.0 control undoes a character formatting change
    // if it is the latest. We don't create our own undo buffer,
    // but just prohibit undo in case if the latest operation
    // is a formatting change.
    BOOL    m_bCanUndo;

    HWND    m_hEditCtrl;
    static WNDPROC sm_pDefWindowProc;

    LONG    m_lHDeltaAccum;
    LONG    m_lVDeltaAccum;


    static OleCallback sm_oleCallback;

    /*****************************************************************
     * Inner class OleCallback declaration.
     */

    class AwtTextArea::OleCallback : public IRichEditOleCallback {
    public:
        OleCallback();

        STDMETHODIMP QueryInterface(REFIID riid, LPVOID * ppvObj);
        STDMETHODIMP_(ULONG) AddRef();
        STDMETHODIMP_(ULONG) Release();
        STDMETHODIMP GetNewStorage(LPSTORAGE FAR * ppstg);
        STDMETHODIMP GetInPlaceContext(LPOLEINPLACEFRAME FAR * ppipframe,
                                       LPOLEINPLACEUIWINDOW FAR* ppipuiDoc,
                                       LPOLEINPLACEFRAMEINFO pipfinfo);
        STDMETHODIMP ShowContainerUI(BOOL fShow);
        STDMETHODIMP QueryInsertObject(LPCLSID pclsid, LPSTORAGE pstg, LONG cp);
        STDMETHODIMP DeleteObject(LPOLEOBJECT poleobj);
        STDMETHODIMP QueryAcceptData(LPDATAOBJECT pdataobj, CLIPFORMAT *pcfFormat,
                                     DWORD reco, BOOL fReally, HGLOBAL hMetaPict);
        STDMETHODIMP ContextSensitiveHelp(BOOL fEnterMode);
        STDMETHODIMP GetClipboardData(CHARRANGE *pchrg, DWORD reco,
                                      LPDATAOBJECT *ppdataobj);
        STDMETHODIMP GetDragDropEffect(BOOL fDrag, DWORD grfKeyState,
                                       LPDWORD pdwEffect);
        STDMETHODIMP GetContextMenu(WORD seltype, LPOLEOBJECT poleobj,
                                    CHARRANGE FAR * pchrg, HMENU FAR * phmenu);
    private:
        ULONG             m_refs; // Reference count
    };

};

#endif /* AWT_TEXTAREA_H */
