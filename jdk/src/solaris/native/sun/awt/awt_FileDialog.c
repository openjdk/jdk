/*
 * Copyright 1995-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include "awt_p.h"
#include <Xm/AtomMgr.h>
#include <Xm/Protocols.h>
#include <sys/param.h>
#include <string.h>
#include <stdlib.h>
#include "awt_p.h"
#include "java_awt_FileDialog.h"
#include "java_awt_event_MouseWheelEvent.h"
#include "sun_awt_motif_MFileDialogPeer.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "multi_font.h"

#include "awt_Component.h"

#include <jni.h>
#include <jni_util.h>
#include <Xm/FileSB.h>

#define MAX_DIR_PATH_LEN    1024

extern void Text_handlePaste(Widget w, XtPointer client_data, XEvent * event,
                             Boolean * cont);

extern struct MComponentPeerIDs mComponentPeerIDs;

extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

/* fieldIDs for FileDialog fields and methods that may be accessed from C */
static struct FileDialogIDs {
    jfieldID mode;
    jfieldID file;
} fileDialogIDs;

/* the field to store the default search procedure */
static XmSearchProc DefaultSearchProc = NULL;

/* mouse wheel handler for scrolling */
void File_handleWheel(Widget w, XtPointer client_data, XEvent* event, Boolean* cont);

/*
 * Class:     java_awt_FileDialog
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for FileDialog.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_FileDialog_initIDs
  (JNIEnv *env, jclass cls)
{
    fileDialogIDs.mode = (*env)->GetFieldID(env, cls, "mode", "I");
    fileDialogIDs.file =
      (*env)->GetFieldID(env, cls, "file", "Ljava/lang/String;");

    DASSERT(fileDialogIDs.mode != NULL);
    DASSERT(fileDialogIDs.file != NULL);
}

/*
 * client_data is MFileDialogPeer instance pointer
 */
static void
FileDialog_OK(Widget w,
              void *client_data,
              XmFileSelectionBoxCallbackStruct * call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject this = (jobject) client_data;
    struct FrameData *fdata;
    char *file;
    jstring jstr;
    XmStringContext   stringContext;
    XmStringDirection direction;
    XmStringCharSet   charset;
    Boolean           separator;

    fdata = (struct FrameData *)JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if ((*env)->EnsureLocalCapacity(env, 1) < 0)
        return;

    if (!XmStringInitContext(&stringContext, call_data->value))
        return;

    if (!XmStringGetNextSegment(stringContext, &file, &charset,
                                &direction, &separator))
        file = NULL;

    if (file == NULL)
        jstr = NULL;
    else
        jstr = JNU_NewStringPlatform(env, (const char *) file);

    if (jstr != 0) {
        JNU_CallMethodByName(env, NULL, this, "handleSelected",
                             "(Ljava/lang/String;)V", jstr);
        (*env)->DeleteLocalRef(env, jstr);
    }
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    XmStringFreeContext(stringContext);
    if (file != NULL)
        XtFree(file);
}

/*
 * client_data is MFileDialogPeer instance pointer
 */
static void
FileDialog_CANCEL(Widget w,
                  void *client_data,
                  XmFileSelectionBoxCallbackStruct * call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject this = (jobject) client_data;
    struct FrameData *fdata;

    fdata = (struct FrameData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    JNU_CallMethodByName(env, NULL, (jobject) client_data, "handleCancel", "()V");
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}


/*
 * client_data is MFileDialogPeer instance pointer
 */
static void
FileDialog_quit(Widget w,
                XtPointer client_data,
                XtPointer call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    JNU_CallMethodByName(env, NULL, (jobject) client_data, "handleQuit", "()V");
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

static void
setDeleteCallback(jobject this, struct FrameData *wdata)
{
    Atom xa_WM_DELETE_WINDOW;
    Atom xa_WM_PROTOCOLS;

    XtVaSetValues(wdata->winData.shell,
                  XmNdeleteResponse, XmDO_NOTHING,
                  NULL);
    xa_WM_DELETE_WINDOW = XmInternAtom(XtDisplay(wdata->winData.shell),
                                       "WM_DELETE_WINDOW", False);
    xa_WM_PROTOCOLS = XmInternAtom(XtDisplay(wdata->winData.shell),
                                   "WM_PROTOCOLS", False);

    XmAddProtocolCallback(wdata->winData.shell,
                          xa_WM_PROTOCOLS,
                          xa_WM_DELETE_WINDOW,
                          FileDialog_quit, (XtPointer) this);
}

void
setFSBDirAndFile(Widget w, char *dir, char *file,
                 XmString *ffiles, int count)
{
    Widget textField, list;
    char dirbuf[MAX_DIR_PATH_LEN];
    XmString xim, item;
    size_t lastSelect;

    dirbuf[0] = (char) '\0';

    if (dir != NULL && strlen(dir) < MAX_DIR_PATH_LEN)
        strcpy(dirbuf, dir);

    /* -----> make sure dir ends in '/' */
    if (dirbuf[0] != (char) '\0') {
        if (dirbuf[strlen(dirbuf) - 1] != (char) '/')
            strcat(dirbuf, "/");
    } else {
        getcwd(dirbuf, MAX_DIR_PATH_LEN - 16);
        strcat(dirbuf, "/");
    }

    strcat(dirbuf, "[^.]*");
    xim = XmStringCreate(dirbuf, XmSTRING_DEFAULT_CHARSET);
    XtVaSetValues(w, XmNdirMask, xim, NULL);

    if (ffiles != NULL)
      XtVaSetValues(w,
                    XmNfileListItems, (count > 0) ? ffiles : NULL,
                    XmNfileListItemCount, count,
                    XmNlistUpdated, True, NULL);

    XmStringFree(xim);

    /*
     * Select the filename from the filelist if it exists.
     */

    textField = XmFileSelectionBoxGetChild(w, XmDIALOG_TEXT);
    list = XmFileSelectionBoxGetChild(w, XmDIALOG_LIST);

    if (textField != 0 && file != 0) {
        lastSelect = strlen(file);
        XtVaSetValues(textField, XmNvalue, file, NULL);
        XmTextFieldSetSelection(textField, 0, lastSelect, CurrentTime);

        item = XmStringCreateLocalized(file);
        XmListSelectItem(list, item, NULL);
        XmStringFree(item);
    }
}

static void
changeBackground(Widget w, void *bg)
{
    /*
    ** This is a work-around for bug 4325443, caused by motif bug 4345559,
    ** XmCombobox dosn't return all children, so give it some help ...
    */
    Widget grabShell;
    grabShell = XtNameToWidget(w, "GrabShell");
    if (grabShell != NULL) {
        awt_util_mapChildren(grabShell, changeBackground, 0, (void *) bg);
    }

    XmChangeColor(w, (Pixel) bg);
}

void
ourSearchProc(Widget w, XtPointer p) {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    struct FrameData *wdata;
    XtPointer peer;
    jobject this;
    jboolean res;
    char * dir = NULL;
    jstring dir_o;
    int32_t i, filecount = 0;
    XmString * filelist = NULL;
    jobjectArray nffiles = NULL;
    jclass clazz = NULL;
    jstring jfilename = NULL;
    char * cfilename = NULL;
    XmFileSelectionBoxCallbackStruct * vals = (XmFileSelectionBoxCallbackStruct *)p;

    XtVaGetValues(w, XmNuserData, &peer, NULL);
    this = (jobject)peer;
    if (JNU_IsNull(env, this) ) {
        return;
    }
    wdata = (struct FrameData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (wdata == 0 ||
        wdata->winData.comp.widget == 0 ||
        wdata->winData.shell == 0 || p == NULL ) {
        return;
    }

    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return;
    }

    if (DefaultSearchProc != NULL) {
        /* Unmap the widget temporary. If it takes a long time to generate
           the list items some visual artifacts may be caused. However,
           we need to do this to have the widget that works as we expect.
         */
        XtSetMappedWhenManaged(w, False);
        /* Call the default Motif search procedure to take the
           native filtered file list.
         */
        DefaultSearchProc(w, vals);
        XtSetMappedWhenManaged(w, True);
        XtVaGetValues(w,
                      XmNlistItemCount, &filecount,
                      XmNlistItems, &filelist,
                      NULL);
        /* We need to construct the new String array to pass it to
           the Java code.
         */
        clazz = (*env)->FindClass(env, "java/lang/String");
        /* It is ok if filecount is 0. */
        nffiles = (*env)->NewObjectArray(env, filecount, clazz, NULL);
        if (JNU_IsNull(env, nffiles)) {
            nffiles = NULL;
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        } else {
            for (i = 0; i < filecount; i++) {
                DASSERT(filelist[i] != NULL);

                XmStringGetLtoR(filelist[i], XmFONTLIST_DEFAULT_TAG, &cfilename);
                jfilename = JNU_NewStringPlatform(env, cfilename);

                if (JNU_IsNull(env, jfilename)) {
                    XtFree(cfilename);
                    nffiles = NULL;
                    JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
                    break;
                }

                (*env)->SetObjectArrayElement(env, nffiles, i, jfilename);

                (*env)->DeleteLocalRef(env, jfilename);
                XtFree(cfilename);
            }
        }
    }

    XmStringGetLtoR(vals->dir, XmFONTLIST_DEFAULT_TAG, &dir);
    dir_o = JNU_NewStringPlatform(env, dir);
    res = JNU_CallMethodByName(env, NULL, this,
                               "proceedFiltering",
                               "(Ljava/lang/String;[Ljava/lang/String;Z)Z",
                               dir_o, nffiles,
                               awt_currentThreadIsPrivileged(env)).z;

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    XtVaSetValues(w,
                  XmNlistUpdated, res,
                  NULL);
    (*env)->DeleteLocalRef(env, dir_o);
    free(dir);
}

/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_create
  (JNIEnv *env, jobject this, jobject parent)
{
    struct FrameData *fdata;
    struct CanvasData *wdata;
    int32_t argc;
#define MAX_ARGC 20
    Arg args[MAX_ARGC];
    Widget child, textField, dirList, fileList;
    XmString xim;
    Pixel bg;
    jobject target;
    jstring file;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;
#ifndef NOMODALFIX
    extern void awt_shellPoppedUp(Widget shell, XtPointer c, XtPointer d);
    extern void awt_shellPoppedDown(Widget shell, XtPointer c, XtPointer d);
#endif NOMODALFIX

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, parent) || JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();

    adata = copyGraphicsConfigToPeer(env, this);

    wdata = (struct CanvasData *) JNU_GetLongFieldAsPtr(env,parent,mComponentPeerIDs.pData);

    fdata = ZALLOC(FrameData);
    JNU_SetLongFieldFromPtr(env,this,mComponentPeerIDs.pData,fdata);

    if (fdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    XtVaGetValues(wdata->comp.widget, XmNbackground, &bg, NULL);

    /*
     * XXX: this code uses FrameData but doesn't bother to init a lot
     * of its memebers.  This confuses the hell out of the code in
     * awt_TopLevel.c that gets passes such half-inited FrameData.
     */
    fdata->decor = MWM_DECOR_ALL;

    argc = 0;
    XtSetArg(args[argc], XmNmustMatch, False);
    argc++;
    XtSetArg(args[argc], XmNautoUnmanage, False);
    argc++;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNvisual, adata->awt_visInfo.visual);
    argc++;
    XtSetArg(args[argc], XmNdialogStyle, XmDIALOG_FULL_APPLICATION_MODAL);
    argc++;
    XtSetArg (args[argc], XmNscreen,
              ScreenOfDisplay(awt_display, adata->awt_visInfo.screen));
    argc++;
    XtSetArg(args[argc], XmNuserData, (XtPointer)globalRef);
    argc++;
    XtSetArg(args[argc], XmNresizePolicy, XmRESIZE_NONE);
    argc++;

    XtSetArg(args[argc], XmNbuttonFontList,  getMotifFontList());
    argc++;
    XtSetArg(args[argc], XmNlabelFontList,   getMotifFontList());
    argc++;
    XtSetArg(args[argc], XmNtextFontList,    getMotifFontList());
    argc++;

    DASSERT(!(argc > MAX_ARGC));

    fdata->winData.comp.widget = XmCreateFileSelectionDialog(wdata->shell,
                                                             "",
                                                             args,
                                                             argc);
    fdata->winData.shell = XtParent(fdata->winData.comp.widget);
    awt_util_mapChildren(fdata->winData.shell, changeBackground, 0,
                         (void *) bg);
    child = XmFileSelectionBoxGetChild(fdata->winData.comp.widget,
                                       XmDIALOG_HELP_BUTTON);

    /* We should save a pointer to the default search procedure
       to do some things that we cannot do else. For instance,
       apply the native pattern.
     */
    XtVaGetValues(fdata->winData.comp.widget,
                  XmNfileSearchProc, &DefaultSearchProc,
                  NULL);
    XtVaSetValues(fdata->winData.comp.widget,
                  XmNfileSearchProc, ourSearchProc,
                  NULL);

    /*
     * Get textfield in FileDialog.
     */
    textField = XmFileSelectionBoxGetChild(fdata->winData.comp.widget,
                                           XmDIALOG_TEXT);
    if (child != NULL) {
        /*
         * Workaround for Bug Id 4415659.
         * If the dialog child is unmanaged before the dialog is managed,
         * the Motif drop site hierarchy may be broken if we associate
         * a drop target with the dialog before it is shown.
         */
        XtSetMappedWhenManaged(fdata->winData.shell, False);
        XtManageChild(fdata->winData.comp.widget);
        XtUnmanageChild(fdata->winData.comp.widget);
        XtSetMappedWhenManaged(fdata->winData.shell, True);
        XtUnmanageChild(child);
    }
    if (!awtJNI_IsMultiFont(env, awtJNI_GetFont(env, this))) {
        /* This process should not be done other than English language
           locale. */
        child = XmFileSelectionBoxGetChild(fdata->winData.comp.widget,
                                           XmDIALOG_DEFAULT_BUTTON);
        if (child != NULL) {
            XmString xim;

            switch ((*env)->GetIntField(env, target, fileDialogIDs.mode)) {
                case java_awt_FileDialog_LOAD:
                    xim = XmStringCreate("Open", "labelFont");
                    XtVaSetValues(child, XmNlabelString, xim, NULL);
                    XmStringFree(xim);
                    break;

                case java_awt_FileDialog_SAVE:
                    xim = XmStringCreate("Save", "labelFont");
                    XtVaSetValues(child, XmNlabelString, xim, NULL);
                    XmStringFree(xim);
                    break;

                default:
                    break;
            }
        }
    }
    XtAddCallback(fdata->winData.comp.widget,
                  XmNokCallback,
                  (XtCallbackProc) FileDialog_OK,
                  (XtPointer) globalRef);
    XtAddCallback(fdata->winData.comp.widget,
                  XmNcancelCallback,
                  (XtCallbackProc) FileDialog_CANCEL,
                  (XtPointer) globalRef);

#ifndef NOMODALFIX
    XtAddCallback(fdata->winData.shell,
                      XtNpopupCallback,
                      awt_shellPoppedUp,
                      NULL);
    XtAddCallback(fdata->winData.shell,
                      XtNpopdownCallback,
                      awt_shellPoppedDown,
                      NULL);
#endif NOMODALFIX

    setDeleteCallback(globalRef, fdata);

    if (textField != NULL)  {
        /*
         * Insert event handler to correctly process cut/copy/paste keys
         * such that interaction with our own clipboard mechanism will work
         * properly.
         *
         * The Text_handlePaste() event handler is also used by both
         * TextField/TextArea.
         */
        XtInsertEventHandler(textField,
                         KeyPressMask,
                         False, Text_handlePaste, (XtPointer) globalRef,
                         XtListHead);
    }

    /* To get wheel scrolling, we add an event handler to the directory list and
     * file list widgets to handle mouse wheels */
    dirList = XmFileSelectionBoxGetChild(fdata->winData.comp.widget, XmDIALOG_DIR_LIST);
    if (dirList != NULL) {
        XtAddEventHandler(dirList, ButtonPressMask, False, File_handleWheel,
                          (XtPointer) globalRef);
    }

    fileList = XmFileSelectionBoxGetChild(fdata->winData.comp.widget, XmDIALOG_LIST);
    if (fileList != NULL) {
        XtAddEventHandler(fileList, ButtonPressMask, False, File_handleWheel,
                          (XtPointer) globalRef);
    }

    file = (*env)->GetObjectField(env, target, fileDialogIDs.file);
    if (JNU_IsNull(env, file)) {
        setFSBDirAndFile(fdata->winData.comp.widget, ".", "", NULL, -1);
    } else {
        char *fileString;

        fileString = (char *) JNU_GetStringPlatformChars(env, file, NULL);
        setFSBDirAndFile(fdata->winData.comp.widget, ".", fileString, NULL, -1);
        JNU_ReleaseStringPlatformChars(env, file, (const char *) fileString);
    }
    AWT_UNLOCK();
}

/* Event handler for making scrolling happen when the mouse wheel is rotated */
void File_handleWheel(Widget w, XtPointer client_data, XEvent* event, Boolean* cont) {
    unsigned int btn;
    Widget scrolledWindow = NULL;

    /* only registered for ButtonPress, so don't need to check event type  */
    btn = event->xbutton.button;
    /* wheel up and wheel down show up as button 4 and 5, respectively */
    if (btn == 4 || btn == 5) {
        scrolledWindow = XtParent(w);
        if (scrolledWindow == NULL) {
            return;
        }
        awt_util_do_wheel_scroll(scrolledWindow,
                             java_awt_event_MouseWheelEvent_WHEEL_UNIT_SCROLL,
                             3,
                             btn == 4 ? -1 : 1);
    }
}


/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    pReshape
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_pReshape
  (JNIEnv *env, jobject this, jint x, jint y, jint w, jint h)
{
    struct FrameData *wdata;

    AWT_LOCK();
    wdata = (struct FrameData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (wdata == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    /* GES: AVH's hack from awt_util.c:
     * Motif ignores attempts to move a toplevel window to 0,0.
     * Instead we set the position to 1,1. The expected value is
     * returned by Frame.getBounds() since it uses the internally
     * held rectangle rather than querying the peer.
     */

    if ((x == 0) && (y == 0)) {
        XtVaSetValues(wdata->winData.shell, XmNx, 1, XmNy, 1, NULL);
    }
    XtVaSetValues(wdata->winData.shell,
                  XtNx, (XtArgVal) x,
                  XtNy, (XtArgVal) y,
                  NULL);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    pDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_pDispose
  (JNIEnv *env, jobject this)
{
    struct FrameData *wdata;

    AWT_LOCK();
    wdata = (struct FrameData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtUnmanageChild(wdata->winData.shell);
    awt_util_consumeAllXEvents(wdata->winData.shell);
    XtDestroyWidget(wdata->winData.shell);
    free((void *) wdata);
    JNU_SetLongFieldFromPtr(env,this,mComponentPeerIDs.pData,NULL);
    awtJNI_DeleteGlobalRef(env, this);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    pShow
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_pShow
  (JNIEnv *env, jobject this)
{
    struct FrameData *wdata;
    XmString dirMask = NULL;

    AWT_LOCK();
    wdata = (struct FrameData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtManageChild(wdata->winData.comp.widget);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    pHide
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_pHide
  (JNIEnv *env, jobject this)
{
    struct FrameData *wdata;

    AWT_LOCK();
    wdata = (struct FrameData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (XtIsManaged(wdata->winData.comp.widget)) {
        XtUnmanageChild(wdata->winData.comp.widget);
    }

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    setFileEntry
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_setFileEntry
  (JNIEnv *env, jobject this, jstring dir, jstring file, jobjectArray ffiles)
{
    struct ComponentData *cdata;
    char *cdir;
    char *cfile;
    char *cf;
    struct FrameData *wdata;
    int32_t length, i;
    XmString * files = NULL;
    jstring jf;

    AWT_LOCK();
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (wdata == NULL || wdata->winData.comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }

    cdir = (JNU_IsNull(env, dir))
               ? NULL
               : (char *) JNU_GetStringPlatformChars(env, dir, NULL);

    cfile = (JNU_IsNull(env, file))
               ? NULL
               : (char *) JNU_GetStringPlatformChars(env, file, NULL);

    if (ffiles != NULL) {
        length = (*env)->GetArrayLength(env, ffiles);
        files = (XmString*)calloc(length, sizeof(XmString));

        for (i = 0; i < length; i++) {
            jf = (jstring)(*env)->GetObjectArrayElement(env, ffiles, i);
            cf = (char *) JNU_GetStringPlatformChars(env, jf, NULL);

            if ((*env)->GetStringLength(env, jf) == 0 && length == 1) {
              length = 0;
              files[0] = NULL;
            }
            else
              files[i] = XmStringCreateLocalized(cf);

            if (cf)
                JNU_ReleaseStringPlatformChars(env, jf, (const char *) cf);
        }

        setFSBDirAndFile(wdata->winData.comp.widget, (cdir) ? cdir : "",
                         (cfile) ? cfile : "", files, length);
        while(i > 0) {
            XmStringFree(files[--i]);
        }
        if (files != NULL) {
            free(files);
        }
    }
    else
      setFSBDirAndFile(wdata->winData.comp.widget, (cdir) ? cdir : "",
                       (cfile) ? cfile : "", NULL, -1);

    if (cdir) {
        JNU_ReleaseStringPlatformChars(env, dir, (const char *) cdir);
    }

    if (cfile) {
        JNU_ReleaseStringPlatformChars(env, file, (const char *) cfile);
    }

    AWT_FLUSH_UNLOCK();
}

static void
changeFont(Widget w, void *fontList)
{
    XtVaSetValues(w, XmNfontList, fontList, NULL);
}

/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    setFont
 * Signature: (Ljava/awt/Font;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_setFont
  (JNIEnv *env, jobject this, jobject f)
{
    struct ComponentData *tdata;
    struct FontData *fdata;
    XmFontListEntry fontentry;
    XmFontList fontlist;
    char *err;

    if (JNU_IsNull(env, f)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    fdata = awtJNI_GetFontData(env, f, &err);
    if (fdata == NULL) {
        JNU_ThrowInternalError(env, err);
        AWT_UNLOCK();
        return;
    }
    tdata = (struct ComponentData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (awtJNI_IsMultiFont(env, f)) {
        if (fdata->xfs == NULL) {
            fdata->xfs = awtJNI_MakeFontSet(env, f);
        }
        if (fdata->xfs != NULL) {
            fontentry = XmFontListEntryCreate("labelFont",
                                              XmFONT_IS_FONTSET,
                                              (XtPointer) (fdata->xfs));
            fontlist = XmFontListAppendEntry(NULL, fontentry);
            /*
             * Some versions of motif have a bug in
             * XmFontListEntryFree() which causes it to free more than it
             * should.  Use XtFree() instead.  See O'Reilly's
             * Motif Reference Manual for more information.
             */
            XmFontListEntryFree(&fontentry);
        } else {
            fontlist = XmFontListCreate(fdata->xfont, "labelFont");
        }
    } else {
        fontlist = XmFontListCreate(fdata->xfont, "labelFont");
    }

    if (fontlist != NULL) {
     /* setting the fontlist in the FileSelectionBox is not good enough --
        you have to set the resource for all the descendants individually */
        awt_util_mapChildren(tdata->widget, changeFont, 1, (void *)fontlist);
        XmFontListFree(fontlist);
    } else {
        JNU_ThrowNullPointerException(env, "NullPointerException");
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MFileDialogPeer
 * Method:    insertReplaceFileDialogText
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFileDialogPeer_insertReplaceFileDialogText
  (JNIEnv *env, jobject this, jstring l)
{
    struct ComponentData *cdata;
    char *cl;
    XmTextPosition start, end;
    Widget textField;
    jobject font;

    /*
     * Replaces the text in the FileDialog's textfield with the passed
     * string.
     */

    AWT_LOCK();
    cdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    textField = XmFileSelectionBoxGetChild(cdata->widget, XmDIALOG_TEXT);

    if (textField == NULL)  {
        JNU_ThrowNullPointerException(env, "Null TextField in FileDialog");
        AWT_UNLOCK();
        return;
    }

    font = awtJNI_GetFont(env, this);

    if (JNU_IsNull(env, l)) {
        cl = NULL;
    } else {
        /*
         * We use makePlatformCString() to convert unicode to EUC here,
         * although output only components (Label/Button/Menu..)
         * is not using make/allocCString() functions anymore.
         * Because Motif TextFiled widget does not support multi-font
         * compound string.
         */

        cl = (char *) JNU_GetStringPlatformChars(env, l, NULL);
    }

    if (!XmTextGetSelectionPosition(textField, &start, &end)) {
        start = end = XmTextGetInsertionPosition(textField);
    }
    XmTextReplace(textField, start, end, cl);

    if (cl != NULL && cl !="") {
        JNU_ReleaseStringPlatformChars(env, l, cl);
    }
    AWT_FLUSH_UNLOCK();
}
