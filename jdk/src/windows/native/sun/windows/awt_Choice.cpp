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

#include "awt_Toolkit.h"
#include "awt_Choice.h"
#include "awt_KeyboardFocusManager.h"
#include "awt_Canvas.h"

#include "awt_Dimension.h"
#include "awt_Container.h"

#include <java_awt_Toolkit.h>
#include <java_awt_FontMetrics.h>
#include <java_awt_event_InputEvent.h>

/* IMPORTANT! Read the README.JNI file for notes on JNI converted AWT code.
 */

/************************************************************************/
// Struct for _Reshape() method
struct ReshapeStruct {
    jobject choice;
    jint x, y;
    jint width, height;
};
// Struct for _Select() method
struct SelectStruct {
    jobject choice;
    jint index;
};
// Struct for _AddItems() method
struct AddItemsStruct {
    jobject choice;
    jobjectArray items;
    jint index;
};
// Struct for _Remove() method
struct RemoveStruct {
    jobject choice;
    jint index;
};

/************************************************************************/

/* Bug #4509045: set if SetDragCapture captured mouse */

BOOL AwtChoice::mouseCapture = FALSE;

/* Bug #4338368: consume the spurious MouseUp when the choice loses focus */

BOOL AwtChoice::skipNextMouseUp = FALSE;
/*************************************************************************
 * AwtChoice class methods
 */

AwtChoice::AwtChoice() {
    killFocusRouting = mrPassAlong;
}

LPCTSTR AwtChoice::GetClassName() {
    return TEXT("COMBOBOX");  /* System provided combobox class */
}

AwtChoice* AwtChoice::Create(jobject peer, jobject parent) {


    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jobject target = NULL;
    AwtChoice* c = NULL;
    RECT rc;

    try {
        if (env->EnsureLocalCapacity(1) < 0) {
            return NULL;
        }
        AwtCanvas* awtParent;

        JNI_CHECK_NULL_GOTO(parent, "null parent", done);

        awtParent = (AwtCanvas*)JNI_GET_PDATA(parent);
        JNI_CHECK_NULL_GOTO(awtParent, "null awtParent", done);

        target = env->GetObjectField(peer, AwtObject::targetID);
        JNI_CHECK_NULL_GOTO(target, "null target", done);

        c = new AwtChoice();

        {
            DWORD style = WS_CHILD | WS_CLIPSIBLINGS | WS_VSCROLL |
                          CBS_DROPDOWNLIST | CBS_OWNERDRAWFIXED;
            DWORD exStyle = 0;
            if (GetRTL()) {
                exStyle |= WS_EX_RIGHT | WS_EX_LEFTSCROLLBAR;
                if (GetRTLReadingOrder())
                    exStyle |= WS_EX_RTLREADING;
            }

            /*
             * In OWNER_DRAW, the size of the edit control part of the
             * choice must be determinded in its creation, when the parent
             * cannot get the choice's instance from its handle.  So
             * record the pair of the ID and the instance of the choice.
             */
            UINT myId = awtParent->CreateControlID();
            DASSERT(myId > 0);
            c->m_myControlID = myId;
            awtParent->PushChild(myId, c);

            jint x = env->GetIntField(target, AwtComponent::xID);
            jint y = env->GetIntField(target, AwtComponent::yID);
            jint width = env->GetIntField(target, AwtComponent::widthID);
            jint height = env->GetIntField(target, AwtComponent::heightID);

            jobject dimension = JNU_CallMethodByName(env, NULL, peer,
                                                     "preferredSize",
                                                     "()Ljava/awt/Dimension;").l;
            DASSERT(!safe_ExceptionOccurred(env));

            if (dimension != NULL && width == 0) {
                width = env->GetIntField(dimension, AwtDimension::widthID);
            }
            c->CreateHWnd(env, L"", style, exStyle,
                          x, y, width, height,
                          awtParent->GetHWnd(),
                          reinterpret_cast<HMENU>(static_cast<INT_PTR>(myId)),
                          ::GetSysColor(COLOR_WINDOWTEXT),
                          ::GetSysColor(COLOR_WINDOW),
                          peer);

            /* suppress inheriting parent's color. */
            c->m_backgroundColorSet = TRUE;
            c->UpdateBackground(env, target);

            /* Bug 4255631 Solaris: Size returned by Choice.getSize() does not match
             * actual size
             * Fix: Set the Choice to its actual size in the component.
             */
            ::GetClientRect(c->GetHWnd(), &rc);
            env->SetIntField(target, AwtComponent::widthID,  (jint) rc.right);
            env->SetIntField(target, AwtComponent::heightID, (jint) rc.bottom);

            env->DeleteLocalRef(dimension);
        }
    } catch (...) {
        env->DeleteLocalRef(target);
        throw;
    }

done:
    env->DeleteLocalRef(target);

    return c;
}

BOOL AwtChoice::ActMouseMessage(MSG* pMsg) {
    if (!IsFocusingMessage(pMsg->message)) {
        return FALSE;
    }

    if (pMsg->message == WM_LBUTTONDOWN) {
        SendMessage(CB_SHOWDROPDOWN, ~SendMessage(CB_GETDROPPEDSTATE, 0, 0), 0);
    }
    return TRUE;
}

// calculate height of drop-down list part of the combobox
// to show all the items up to a maximum of eight
int AwtChoice::GetDropDownHeight()
{
    int itemHeight =(int)::SendMessage(GetHWnd(), CB_GETITEMHEIGHT, (UINT)0,0);
    int numItemsToShow = (int)::SendMessage(GetHWnd(), CB_GETCOUNT, 0,0);
    numItemsToShow = numItemsToShow > 8 ? 8 : numItemsToShow;
    // drop-down height snaps to nearest line, so add a
    // fudge factor of 1/2 line to ensure last line shows
    return itemHeight*numItemsToShow + itemHeight/2;
}

// get the height of the field portion of the combobox
int AwtChoice::GetFieldHeight()
{
    int fieldHeight;
    int borderHeight;
    fieldHeight =(int)::SendMessage(GetHWnd(), CB_GETITEMHEIGHT, (UINT)-1, 0);
    // add top and bottom border lines; border size is different for
    // Win 4.x (3d edge) vs 3.x (1 pixel line)
    borderHeight = ::GetSystemMetrics(SM_CYEDGE);
    fieldHeight += borderHeight*2;
    return fieldHeight;
}

// gets the total height of the combobox, including drop down
int AwtChoice::GetTotalHeight()
{
    int dropHeight = GetDropDownHeight();
    int fieldHeight = GetFieldHeight();
    int totalHeight;

    // border on drop-down portion is always non-3d (so don't use SM_CYEDGE)
    int borderHeight = ::GetSystemMetrics(SM_CYBORDER);
    // total height = drop down height + field height + top+bottom drop down border lines
    totalHeight = dropHeight + fieldHeight +borderHeight*2;
    return totalHeight;
}

// Recalculate and set the drop-down height for the Choice.
void AwtChoice::ResetDropDownHeight()
{
    RECT    rcWindow;

    ::GetWindowRect(GetHWnd(), &rcWindow);
    // resize the drop down to accomodate added/removed items
    int     totalHeight = GetTotalHeight();
    ::SetWindowPos(GetHWnd(), NULL,
                    0, 0, rcWindow.right - rcWindow.left, totalHeight,
                    SWP_NOACTIVATE|SWP_NOMOVE|SWP_NOZORDER);
}

/* Fix for the bug 4327666: set the capture for middle
   and right mouse buttons, but leave left button alone */
void AwtChoice::SetDragCapture(UINT flags)
{
    if ((flags & MK_LBUTTON) != 0) {
        if ((::GetCapture() == GetHWnd()) && mouseCapture) {
            /* On MK_LBUTTON ComboBox captures mouse itself
               so we should release capture and clear flag to
               prevent releasing capture by ReleaseDragCapture
             */
            ::ReleaseCapture();
            mouseCapture = FALSE;
        }
        return;
    }
    // don't want to interfere with other controls
    if (::GetCapture() == NULL) {
        ::SetCapture(GetHWnd());
        mouseCapture = TRUE;
    }
}

/* Fix for Bug 4509045: should release capture only if it is set by SetDragCapture */
void AwtChoice::ReleaseDragCapture(UINT flags)
{
    if ((::GetCapture() == GetHWnd()) && ((flags & ALL_MK_BUTTONS) == 0) && mouseCapture) {
        ::ReleaseCapture();
        mouseCapture = FALSE;
    }
}

void AwtChoice::Reshape(int x, int y, int w, int h)
{
    // Choice component height is fixed (when rolled up)
    // so vertically center the choice in it's bounding box
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject target = GetTarget(env);
    jobject parent = env->GetObjectField(target, AwtComponent::parentID);
    RECT rc;

    int fieldHeight = GetFieldHeight();
    if ((parent != NULL && env->GetObjectField(parent, AwtContainer::layoutMgrID) != NULL) &&
        fieldHeight > 0 && fieldHeight < h) {
        y += (h - fieldHeight) / 2;
    }

    /* Fix for 4783342
     * Choice should ignore reshape on height changes,
     * as height is dependent on Font size only.
     */
    AwtComponent* awtParent = GetParent();
    BOOL bReshape = true;
    if (awtParent != NULL) {
        ::GetWindowRect(GetHWnd(), &rc);
        int oldW = rc.right - rc.left;
        RECT parentRc;
        ::GetWindowRect(awtParent->GetHWnd(), &parentRc);
        int oldX = rc.left - parentRc.left;
        int oldY = rc.top - parentRc.top;
        bReshape = (x != oldX || y != oldY || w != oldW);
    }

    if (bReshape)
    {
        int totalHeight = GetTotalHeight();
        AwtComponent::Reshape(x, y, w, totalHeight);
    }

    /* Bug 4255631 Solaris: Size returned by Choice.getSize() does not match
     * actual size
     * Fix: Set the Choice to its actual size in the component.
     */
    ::GetClientRect(GetHWnd(), &rc);
    env->SetIntField(target, AwtComponent::widthID,  (jint)rc.right);
    env->SetIntField(target, AwtComponent::heightID, (jint)rc.bottom);

    env->DeleteLocalRef(target);
    env->DeleteLocalRef(parent);
}

jobject AwtChoice::PreferredItemSize(JNIEnv *env)
{
    jobject dimension = JNU_CallMethodByName(env, NULL, GetPeer(env),
                                             "preferredSize",
                                             "()Ljava/awt/Dimension;").l;
    DASSERT(!safe_ExceptionOccurred(env));
    if (dimension == NULL) {
        return NULL;
    }
    /* This size is window size of choice and it's too big for each
     * drop down item height.
     */
    env->SetIntField(dimension, AwtDimension::heightID,
                       GetFontHeight(env));
    return dimension;
}

void AwtChoice::SetFont(AwtFont* font)
{
    AwtComponent::SetFont(font);

    //Get the text metrics and change the height of each item.
    HDC hDC = ::GetDC(GetHWnd());
    DASSERT(hDC != NULL);
    TEXTMETRIC tm;

    HANDLE hFont = font->GetHFont();
    VERIFY(::SelectObject(hDC, hFont) != NULL);
    VERIFY(::GetTextMetrics(hDC, &tm));
    long h = tm.tmHeight + tm.tmExternalLeading;
    VERIFY(::ReleaseDC(GetHWnd(), hDC) != 0);

    int nCount = (int)::SendMessage(GetHWnd(), CB_GETCOUNT, 0, 0);
    for(int i = 0; i < nCount; ++i) {
        VERIFY(::SendMessage(GetHWnd(), CB_SETITEMHEIGHT, i, MAKELPARAM(h, 0)) != CB_ERR);
    }
    //Change the height of the Edit Box.
    VERIFY(::SendMessage(GetHWnd(), CB_SETITEMHEIGHT, (UINT)-1,
                         MAKELPARAM(h, 0)) != CB_ERR);

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject target = GetTarget(env);
    jint height = env->GetIntField(target, AwtComponent::heightID);

    Reshape(env->GetIntField(target, AwtComponent::xID),
            env->GetIntField(target, AwtComponent::yID),
            env->GetIntField(target, AwtComponent::widthID),
            h);

    env->DeleteLocalRef(target);
}



MsgRouting AwtChoice::WmNotify(UINT notifyCode)
{
    if (notifyCode == CBN_SELCHANGE) {
        int itemSelect = (int)SendMessage(CB_GETCURSEL);
        if (itemSelect != CB_ERR){
            DoCallback("handleAction", "(I)V", itemSelect);
        }
    } else if (notifyCode == CBN_DROPDOWN && !IsFocusable()) {
        // While non-focusable Choice is shown all WM_KILLFOCUS messages should be consumed.
        killFocusRouting = mrConsume;
    } else if (notifyCode == CBN_CLOSEUP && !IsFocusable()) {
        // When non-focusable Choice is about to close, send it synthetic WM_KILLFOCUS
        // message that should be processed by the native widget only. This will allow
        // the native widget to properly process WM_KILLFOCUS that was earlier consumed.
        killFocusRouting = mrDoDefault;
        ::PostMessage(GetHWnd(), WM_KILLFOCUS, (LPARAM)sm_focusOwner, 0);
    }
    return mrDoDefault;
}

MsgRouting
AwtChoice::OwnerDrawItem(UINT /*ctrlId*/, DRAWITEMSTRUCT& drawInfo)
{
    DrawListItem((JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2), drawInfo);
    return mrConsume;
}

MsgRouting
AwtChoice::OwnerMeasureItem(UINT /*ctrlId*/, MEASUREITEMSTRUCT& measureInfo)
{
    MeasureListItem((JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2), measureInfo);
    return mrConsume;
}

/* Bug #4338368: when a choice loses focus, it triggers spurious MouseUp event,
 * even if the focus was lost due to TAB key pressing
 */

MsgRouting
AwtChoice::WmKillFocus(HWND hWndGotFocus)
{
    skipNextMouseUp = TRUE;

    switch (killFocusRouting) {
    case mrConsume:
        return mrConsume;
    case mrDoDefault:
        killFocusRouting = mrPassAlong;
        return mrDoDefault;
    case mrPassAlong:
        return AwtComponent::WmKillFocus(hWndGotFocus);
    }

    DASSERT(false); // must never reach here
    return mrDoDefault;
}

MsgRouting
AwtChoice::WmMouseUp(UINT flags, int x, int y, int button)
{
    if (skipNextMouseUp) {
        skipNextMouseUp = FALSE;
        return mrDoDefault;
    }
    return AwtComponent::WmMouseUp(flags, x, y, button);
}

MsgRouting AwtChoice::HandleEvent(MSG *msg, BOOL synthetic)
{
    /*
     * 6366006
     * Note: the event can be sent in two cases:
     *       1) The Choice is closed and user clicks on it to drop it down.
     *       2) The Choice is non-focusable, it's droped down, user
     *          clicks on it (or outside) to close it.
     *       So, if the Choice is in droped down state, we shouldn't call
     *       heavyweightButtonDown() method. Otherwise it will set a typeahead marker
     *       that won't be removed, because no focus events will be generated.
     */
    if (AwtComponent::sm_focusOwner != GetHWnd() &&
        (msg->message == WM_LBUTTONDOWN || msg->message == WM_LBUTTONDBLCLK) &&
        !IsChoiceOpened())
    {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        jobject target = GetTarget(env);
        env->CallStaticVoidMethod
            (AwtKeyboardFocusManager::keyboardFocusManagerCls,
             AwtKeyboardFocusManager::heavyweightButtonDownMID,
             target, ((jlong)msg->time) & 0xFFFFFFFF);
        env->DeleteLocalRef(target);
    }
    return AwtComponent::HandleEvent(msg, synthetic);
}

BOOL AwtChoice::InheritsNativeMouseWheelBehavior() {return true;}

void AwtChoice::_Reshape(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    ReshapeStruct *rs = (ReshapeStruct *)param;
    jobject choice = rs->choice;
    jint x = rs->x;
    jint y = rs->y;
    jint width = rs->width;
    jint height = rs->height;

    AwtChoice *c = NULL;

    PDATA pData;
    JNI_CHECK_PEER_GOTO(choice, done);

    c = (AwtChoice *)pData;
    if (::IsWindow(c->GetHWnd()))
    {
        c->Reshape(x, y, width, height);
        c->VerifyState();
    }

done:
    env->DeleteGlobalRef(choice);

    delete rs;
}

void AwtChoice::_Select(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    SelectStruct *ss = (SelectStruct *)param;
    jobject choice = ss->choice;
    jint index = ss->index;

    AwtChoice *c = NULL;

    PDATA pData;
    JNI_CHECK_PEER_GOTO(choice, done);

    c = (AwtChoice *)pData;
    if (::IsWindow(c->GetHWnd()))
    {
        c->SendMessage(CB_SETCURSEL, index);
//        c->VerifyState();
    }

done:
    env->DeleteGlobalRef(choice);

    delete ss;
}

void AwtChoice::_AddItems(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    AddItemsStruct *ais = (AddItemsStruct *)param;
    jobject choice = ais->choice;
    jobjectArray items = ais->items;
    jint index = ais->index;

    AwtChoice *c = NULL;

    PDATA pData;
    JNI_CHECK_PEER_GOTO(choice, done);
    JNI_CHECK_NULL_GOTO(items, "null items", done);

    c = (AwtChoice *)pData;
    if (::IsWindow(c->GetHWnd()))
    {
        jsize i;
        int itemCount = env->GetArrayLength(items);
        if (itemCount > 0) {
           c->SendMessage(WM_SETREDRAW, (WPARAM)FALSE, 0);
           for (i = 0; i < itemCount; i++)
           {
               jstring item = (jstring)env->GetObjectArrayElement(items, i);
               JNI_CHECK_NULL_GOTO(item, "null item", next_elem);
               c->SendMessage(CB_INSERTSTRING, index + i, JavaStringBuffer(env, item));
               env->DeleteLocalRef(item);
next_elem:
               ;
           }
           c->SendMessage(WM_SETREDRAW, (WPARAM)TRUE, 0);
           InvalidateRect(c->GetHWnd(), NULL, TRUE);
           c->ResetDropDownHeight();
           c->VerifyState();
        }
    }

done:
    env->DeleteGlobalRef(choice);
    env->DeleteGlobalRef(items);

    delete ais;
}

void AwtChoice::_Remove(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    RemoveStruct *rs = (RemoveStruct *)param;
    jobject choice = rs->choice;
    jint index = rs->index;

    AwtChoice *c = NULL;

    PDATA pData;
    JNI_CHECK_PEER_GOTO(choice, done);

    c = (AwtChoice *)pData;
    if (::IsWindow(c->GetHWnd()))
    {
        c->SendMessage(CB_DELETESTRING, index, 0);
        c->ResetDropDownHeight();
        c->VerifyState();
    }

done:
    env->DeleteGlobalRef(choice);

    delete rs;
}

void AwtChoice::_RemoveAll(void *param)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jobject choice = (jobject)param;

    AwtChoice *c = NULL;

    PDATA pData;
    JNI_CHECK_PEER_GOTO(choice, done);

    c = (AwtChoice *)pData;
    if (::IsWindow(c->GetHWnd()))
    {
        c->SendMessage(CB_RESETCONTENT, 0, 0);
        c->ResetDropDownHeight();
        c->VerifyState();
    }

done:
    env->DeleteGlobalRef(choice);
}

/************************************************************************
 * WChoicePeer native methods
 */

extern "C" {

/*
 * Class:     sun_awt_windows_WChoicePeer
 * Method:    select
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WChoicePeer_select(JNIEnv *env, jobject self,
                                        jint index)
{
    TRY;

    SelectStruct *ss = new SelectStruct;
    ss->choice = env->NewGlobalRef(self);
    ss->index = index;

    AwtToolkit::GetInstance().SyncCall(AwtChoice::_Select, ss);
    // global refs and ss are removed in _Select

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WChoicePeer
 * Method:    remove
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WChoicePeer_remove(JNIEnv *env, jobject self,
                                        jint index)
{
    TRY;

    RemoveStruct *rs = new RemoveStruct;
    rs->choice = env->NewGlobalRef(self);
    rs->index = index;

    AwtToolkit::GetInstance().SyncCall(AwtChoice::_Remove, rs);
    // global ref and rs are deleted in _Remove

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WChoicePeer
 * Method:    removeAll
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WChoicePeer_removeAll(JNIEnv *env, jobject self)
{
    TRY;

    jobject selfGlobalRef = env->NewGlobalRef(self);

    AwtToolkit::GetInstance().SyncCall(AwtChoice::_RemoveAll, (void *)selfGlobalRef);
    // selfGlobalRef is deleted in _RemoveAll

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WChoicePeer
 * Method:    addItems
 * Signature: ([Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WChoicePeer_addItems(JNIEnv *env, jobject self,
                                          jobjectArray items, jint index)
{
    TRY;

    AddItemsStruct *ais = new AddItemsStruct;
    ais->choice = env->NewGlobalRef(self);
    ais->items = (jobjectArray)env->NewGlobalRef(items);
    ais->index = index;

    AwtToolkit::GetInstance().SyncCall(AwtChoice::_AddItems, ais);
    // global refs and ais are deleted in _AddItems

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WChoicePeer
 * Method:    reshape
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WChoicePeer_reshape(JNIEnv *env, jobject self,
                                         jint x, jint y,
                                         jint width, jint height)
{
    TRY;

    ReshapeStruct *rs = new ReshapeStruct;
    rs->choice = env->NewGlobalRef(self);
    rs->x = x;
    rs->y = y;
    rs->width = width;
    rs->height = height;

    AwtToolkit::GetInstance().SyncCall(AwtChoice::_Reshape, rs);
    // global ref and rs are deleted in _Reshape

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WChoicePeer
 * Method:    create
 * Signature: (Lsun/awt/windows/WComponentPeer;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WChoicePeer_create(JNIEnv *env, jobject self,
                                        jobject parent)
{
    TRY;

    PDATA pData;
    JNI_CHECK_PEER_RETURN(parent);
    AwtToolkit::CreateComponent(self, parent,
                                (AwtToolkit::ComponentFactory)
                                AwtChoice::Create);
    JNI_CHECK_PEER_CREATION_RETURN(self);

    CATCH_BAD_ALLOC;
}

} /* extern "C" */


/************************************************************************
 * Diagnostic routines
 */

#ifdef DEBUG

void AwtChoice::VerifyState()
{
    if (AwtToolkit::GetInstance().VerifyComponents() == FALSE) {
        return;
    }

    if (m_callbacksEnabled == FALSE) {
        /* Component is not fully setup yet. */
        return;
    }

    AwtComponent::VerifyState();
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    if (env->PushLocalFrame(1) < 0)
        return;

    jobject target = GetTarget(env);

    // To avoid possibly running client code on the toolkit thread, don't
    // do the following checks if we're running on the toolkit thread.
    if (AwtToolkit::MainThread() != ::GetCurrentThreadId()) {
        // Compare number of items.
        int nTargetItems = JNU_CallMethodByName(env, NULL, target,
                                                "countItems", "()I").i;
        DASSERT(!safe_ExceptionOccurred(env));
        int nPeerItems = (int)::SendMessage(GetHWnd(), CB_GETCOUNT, 0, 0);
        DASSERT(nTargetItems == nPeerItems);

        // Compare selection
        int targetIndex = JNU_CallMethodByName(env, NULL, target,
                                               "getSelectedIndex", "()I").i;
        DASSERT(!safe_ExceptionOccurred(env));
        int peerCurSel = (int)::SendMessage(GetHWnd(), CB_GETCURSEL, 0, 0);
        DASSERT(targetIndex == peerCurSel);
    }
    env->PopLocalFrame(0);
}
#endif //DEBUG
