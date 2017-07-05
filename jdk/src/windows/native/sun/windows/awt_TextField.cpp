/*
 * Copyright (c) 1996, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "awt_Toolkit.h"
#include "awt_TextField.h"
#include "awt_TextComponent.h"
#include "awt_Canvas.h"

/* IMPORTANT! Read the README.JNI file for notes on JNI converted AWT code.
 */

/***********************************************************************/
// struct for _SetEchoChar() method
struct SetEchoCharStruct {
    jobject textfield;
    jchar echoChar;
};
/************************************************************************
 * AwtTextField methods
 */

AwtTextField::AwtTextField() {
}

/* Create a new AwtTextField object and window.   */
AwtTextField* AwtTextField::Create(jobject peer, jobject parent)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jobject target = NULL;
    AwtTextField* c = NULL;

    try {
        PDATA pData;
        AwtCanvas* awtParent;
        JNI_CHECK_PEER_GOTO(parent, done);
        awtParent = (AwtCanvas*)pData;

        JNI_CHECK_NULL_GOTO(awtParent, "null awtParent", done);

        target = env->GetObjectField(peer, AwtObject::targetID);
        JNI_CHECK_NULL_GOTO(target, "null target", done);

        c = new AwtTextField();

        {
            DWORD style = WS_CHILD | WS_CLIPSIBLINGS |
                ES_LEFT | ES_AUTOHSCROLL;
            DWORD exStyle = WS_EX_CLIENTEDGE;
            if (GetRTL()) {
                exStyle |= WS_EX_RIGHT | WS_EX_LEFTSCROLLBAR;
                if (GetRTLReadingOrder())
                    exStyle |= WS_EX_RTLREADING;
            }

            jint x = env->GetIntField(target, AwtComponent::xID);
            jint y = env->GetIntField(target, AwtComponent::yID);
            jint width = env->GetIntField(target, AwtComponent::widthID);
            jint height = env->GetIntField(target, AwtComponent::heightID);

            c->CreateHWnd(env, L"", style, exStyle,
                          x, y, width, height,
                          awtParent->GetHWnd(),
                          reinterpret_cast<HMENU>(static_cast<INT_PTR>(
                awtParent->CreateControlID())),
                          ::GetSysColor(COLOR_WINDOWTEXT),
                          ::GetSysColor(COLOR_WINDOW),
                          peer);

            c->m_backgroundColorSet = TRUE;
            /* suppress inheriting parent's color. */
            c->UpdateBackground(env, target);
            c->SendMessage(EM_SETMARGINS, EC_LEFTMARGIN | EC_RIGHTMARGIN,
                           MAKELPARAM(1, 1));
            /*
             * Fix for BugTraq Id 4260109.
             * Set the text limit to the maximum.
             */
            c->SendMessage(EM_SETLIMITTEXT);

        }
    } catch (...) {
        env->DeleteLocalRef(target);
        throw;
    }

done:
    env->DeleteLocalRef(target);

    return c;
}

void AwtTextField::EditSetSel(CHARRANGE &cr) {
    SendMessage(EM_SETSEL, cr.cpMin, cr.cpMax);
}

void AwtTextField::EditGetSel(CHARRANGE &cr) {
    SendMessage(EM_SETSEL, reinterpret_cast<WPARAM>(&cr.cpMin), reinterpret_cast<LPARAM>(&cr.cpMax));
}

LONG AwtTextField::EditGetCharFromPos(POINT& pt) {
    return static_cast<LONG>(SendMessage(EM_CHARFROMPOS, 0, MAKELPARAM(pt.x, pt.y)));
}

LRESULT AwtTextField::WindowProc(UINT message, WPARAM wParam, LPARAM lParam)
{
    if (message == WM_UNDO || message == EM_UNDO || message == EM_CANUNDO) {
        if (GetWindowLong(GetHWnd(), GWL_STYLE) & ES_READONLY) {
            return FALSE;
        }
    }
    return AwtTextComponent::WindowProc(message, wParam, lParam);
}

MsgRouting
AwtTextField::HandleEvent(MSG *msg, BOOL synthetic)
{
    MsgRouting returnVal;
    /*
     * RichEdit 1.0 control starts internal message loop if the
     * left mouse button is pressed while the cursor is not over
     * the current selection or the current selection is empty.
     * Because of this we don't receive WM_MOUSEMOVE messages
     * while the left mouse button is pressed. To work around
     * this behavior we process the relevant mouse messages
     * by ourselves.
     * By consuming WM_MOUSEMOVE messages we also don't give
     * the RichEdit control a chance to recognize a drag gesture
     * and initiate its own drag-n-drop operation.
     *
     * The workaround also allows us to implement synthetic focus mechanism.
     */
    if (IsFocusingMouseMessage(msg)) {
        CHARRANGE cr;

        LONG lCurPos = EditGetCharFromPos(msg->pt);

        EditGetSel(cr);
        /*
         * NOTE: Plain EDIT control always clears selection on mouse
         * button press. We are clearing the current selection only if
         * the mouse pointer is not over the selected region.
         * In this case we sacrifice backward compatibility
         * to allow dnd of the current selection.
         */
        if (msg->message == WM_LBUTTONDBLCLK) {
            SetStartSelectionPos(static_cast<LONG>(SendMessage(
                EM_FINDWORDBREAK, WB_MOVEWORDLEFT, lCurPos)));
            SetEndSelectionPos(static_cast<LONG>(SendMessage(
                EM_FINDWORDBREAK, WB_MOVEWORDRIGHT, lCurPos)));
        } else {
            SetStartSelectionPos(lCurPos);
            SetEndSelectionPos(lCurPos);
        }
        cr.cpMin = GetStartSelectionPos();
        cr.cpMax = GetEndSelectionPos();
        EditSetSel(cr);

        delete msg;
        return mrConsume;
    } else if (msg->message == WM_LBUTTONUP) {

        /*
         * If the left mouse button is pressed on the selected region
         * we don't clear the current selection. We clear it on button
         * release instead. This is to allow dnd of the current selection.
         */
        if (GetStartSelectionPos() == -1 && GetEndSelectionPos() == -1) {
            CHARRANGE cr;

            LONG lCurPos = EditGetCharFromPos(msg->pt);

            cr.cpMin = lCurPos;
            cr.cpMax = lCurPos;
            EditSetSel(cr);
        }

        /*
         * Cleanup the state variables when left mouse button is released.
         * These state variables are designed to reflect the selection state
         * while the left mouse button is pressed and be set to -1 otherwise.
         */
        SetStartSelectionPos(-1);
        SetEndSelectionPos(-1);
        SetLastSelectionPos(-1);

        delete msg;
        return mrConsume;
    } else if (msg->message == WM_MOUSEMOVE && (msg->wParam & MK_LBUTTON)) {

        /*
         * We consume WM_MOUSEMOVE while the left mouse button is pressed,
         * so we have to simulate autoscrolling when mouse is moved outside
         * of the client area.
         */
        POINT p;
        RECT r;
        BOOL bScrollLeft = FALSE;
        BOOL bScrollRight = FALSE;
        BOOL bScrollUp = FALSE;
        BOOL bScrollDown = FALSE;

        p.x = msg->pt.x;
        p.y = msg->pt.y;
        VERIFY(::GetClientRect(GetHWnd(), &r));

        if (p.x < 0) {
            bScrollLeft = TRUE;
            p.x = 0;
        } else if (p.x > r.right) {
            bScrollRight = TRUE;
            p.x = r.right - 1;
        }
        LONG lCurPos = EditGetCharFromPos(p);

        if (GetStartSelectionPos() != -1 &&
            GetEndSelectionPos() != -1 &&
            lCurPos != GetLastSelectionPos()) {

            CHARRANGE cr;

            SetLastSelectionPos(lCurPos);

            cr.cpMin = GetStartSelectionPos();
            cr.cpMax = GetLastSelectionPos();

            EditSetSel(cr);
        }

        if (bScrollLeft == TRUE || bScrollRight == TRUE) {
            SCROLLINFO si;
            memset(&si, 0, sizeof(si));
            si.cbSize = sizeof(si);
            si.fMask = SIF_PAGE | SIF_POS | SIF_RANGE;

            VERIFY(::GetScrollInfo(GetHWnd(), SB_HORZ, &si));
            if (bScrollLeft == TRUE) {
                si.nPos = si.nPos - si.nPage / 2;
                si.nPos = max(si.nMin, si.nPos);
            } else if (bScrollRight == TRUE) {
                si.nPos = si.nPos + si.nPage / 2;
                si.nPos = min(si.nPos, si.nMax);
            }
            /*
             * Okay to use 16-bit position since RichEdit control adjusts
             * its scrollbars so that their range is always 16-bit.
             */
            DASSERT(abs(si.nPos) < 0x8000);
            SendMessage(WM_HSCROLL,
                        MAKEWPARAM(SB_THUMBPOSITION, LOWORD(si.nPos)));
        }
        delete msg;
        return mrConsume;
    }
    /*
     * Store the 'synthetic' parameter so that the WM_PASTE security check
     * happens only for synthetic events.
     */
    m_synthetic = synthetic;
    returnVal = AwtComponent::HandleEvent(msg, synthetic);
    m_synthetic = FALSE;

    return returnVal;
}

void AwtTextField::_SetEchoChar(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    SetEchoCharStruct *secs = (SetEchoCharStruct *)param;
    jobject self = secs->textfield;
    jchar echo = secs->echoChar;

    AwtTextField *c = NULL;

    PDATA pData;
    JNI_CHECK_PEER_GOTO(self, ret);
    c = (AwtTextField *)pData;
    if (::IsWindow(c->GetHWnd()))
    {
        c->SendMessage(EM_SETPASSWORDCHAR, echo);
        // Fix for 4307281: force redraw so that changes will take effect
        VERIFY(::InvalidateRect(c->GetHWnd(), NULL, FALSE));
    }
ret:
    env->DeleteGlobalRef(self);

    delete secs;
}

/************************************************************************
 * WTextFieldPeer native methods
 */

extern "C" {

/*
 * Class:     sun_awt_windows_WTextFieldPeer
 * Method:    create
 * Signature: (Lsun/awt/windows/WComponentPeer;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WTextFieldPeer_create(JNIEnv *env, jobject self,
                                           jobject parent)
{
    TRY;

    PDATA pData;
    JNI_CHECK_PEER_RETURN(parent);
    AwtToolkit::CreateComponent(self, parent,
                                (AwtToolkit::ComponentFactory)
                                AwtTextField::Create);
    JNI_CHECK_PEER_CREATION_RETURN(self);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WTextFieldPeer
 * Method:    setEchoCharacter
 * Signature: (C)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WTextFieldPeer_setEchoCharacter(JNIEnv *env, jobject self,
                                                     jchar ch)
{
    TRY;

    SetEchoCharStruct *secs = new SetEchoCharStruct;
    secs->textfield = env->NewGlobalRef(self);
    secs->echoChar = ch;

    AwtToolkit::GetInstance().SyncCall(AwtTextField::_SetEchoChar, secs);
    // global ref and secs are deleted in _SetEchoChar()

    CATCH_BAD_ALLOC;
}

} /* extern "C" */
