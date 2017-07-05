/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <X11/Xos.h>
#include <X11/Xatom.h>
#ifdef __linux__
#include <execinfo.h>
#endif

#include <jvm.h>
#include <jni.h>
#include <jlong.h>
#include <jni_util.h>

#include "awt_p.h"
#include "awt_Component.h"
#include "awt_MenuComponent.h"
#include "awt_KeyboardFocusManager.h"
#include "awt_Font.h"

#include "sun_awt_X11_XToolkit.h"
#include "java_awt_SystemColor.h"
#include "java_awt_TrayIcon.h"
#include <X11/extensions/XTest.h>

uint32_t awt_NumLockMask = 0;
Boolean  awt_ModLockIsShiftLock = False;

static int32_t num_buttons = 0;
int32_t getNumButtons();

extern JavaVM *jvm;

// Tracing level
static int tracing = 0;
#ifdef PRINT
#undef PRINT
#endif
#ifdef PRINT2
#undef PRINT2
#endif

#define PRINT if (tracing) printf
#define PRINT2 if (tracing > 1) printf


struct ComponentIDs componentIDs;

struct MenuComponentIDs menuComponentIDs;

struct KeyboardFocusManagerIDs keyboardFocusManagerIDs;

#ifndef HEADLESS

extern Display* awt_init_Display(JNIEnv *env, jobject this);

extern struct MFontPeerIDs mFontPeerIDs;

JNIEXPORT void JNICALL
Java_sun_awt_X11_XFontPeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mFontPeerIDs.xfsname =
      (*env)->GetFieldID(env, cls, "xfsname", "Ljava/lang/String;");
}
#endif /* !HEADLESS */

/* This function gets called from the static initializer for FileDialog.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_FileDialog_initIDs
  (JNIEnv *env, jclass cls)
{

}

JNIEXPORT void JNICALL
Java_sun_awt_X11_XToolkit_initIDs
  (JNIEnv *env, jclass clazz)
{
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "numLockMask", "I");
    awt_NumLockMask = (*env)->GetStaticIntField(env, clazz, fid);
    DTRACE_PRINTLN1("awt_NumLockMask = %u", awt_NumLockMask);
    fid = (*env)->GetStaticFieldID(env, clazz, "modLockIsShiftLock", "I");
    awt_ModLockIsShiftLock = (*env)->GetStaticIntField(env, clazz, fid) != 0 ? True : False;
}

/*
 * Class:     sun_awt_X11_XToolkit
 * Method:    getDefaultXColormap
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_X11_XToolkit_getDefaultXColormap
  (JNIEnv *env, jclass clazz)
{
    AwtGraphicsConfigDataPtr defaultConfig =
        getDefaultConfig(DefaultScreen(awt_display));

    return (jlong) defaultConfig->awt_cmap;
}

JNIEXPORT jlong JNICALL Java_sun_awt_X11_XToolkit_getDefaultScreenData
  (JNIEnv *env, jclass clazz)
{
    return ptr_to_jlong(getDefaultConfig(DefaultScreen(awt_display)));
}


JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
    jvm = vm;
    return JNI_VERSION_1_2;
}

/*
 * Class:     sun_awt_X11_XToolkit
 * Method:    nativeLoadSystemColors
 * Signature: ([I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_X11_XToolkit_nativeLoadSystemColors
  (JNIEnv *env, jobject this, jintArray systemColors)
{
    AwtGraphicsConfigDataPtr defaultConfig =
        getDefaultConfig(DefaultScreen(awt_display));
    awtJNI_CreateColorData(env, defaultConfig, 1);
}

JNIEXPORT void JNICALL
Java_java_awt_Component_initIDs
  (JNIEnv *env, jclass cls)
{
    jclass keyclass = NULL;


    componentIDs.x = (*env)->GetFieldID(env, cls, "x", "I");
    componentIDs.y = (*env)->GetFieldID(env, cls, "y", "I");
    componentIDs.width = (*env)->GetFieldID(env, cls, "width", "I");
    componentIDs.height = (*env)->GetFieldID(env, cls, "height", "I");
    componentIDs.isPacked = (*env)->GetFieldID(env, cls, "isPacked", "Z");
    componentIDs.peer =
      (*env)->GetFieldID(env, cls, "peer", "Ljava/awt/peer/ComponentPeer;");
    componentIDs.background =
      (*env)->GetFieldID(env, cls, "background", "Ljava/awt/Color;");
    componentIDs.foreground =
      (*env)->GetFieldID(env, cls, "foreground", "Ljava/awt/Color;");
    componentIDs.graphicsConfig =
        (*env)->GetFieldID(env, cls, "graphicsConfig",
                           "Ljava/awt/GraphicsConfiguration;");
    componentIDs.name =
      (*env)->GetFieldID(env, cls, "name", "Ljava/lang/String;");

    /* Use _NoClientCode() methods for trusted methods, so that we
     *  know that we are not invoking client code on trusted threads
     */
    componentIDs.getParent =
      (*env)->GetMethodID(env, cls, "getParent_NoClientCode",
                         "()Ljava/awt/Container;");

    componentIDs.getLocationOnScreen =
      (*env)->GetMethodID(env, cls, "getLocationOnScreen_NoTreeLock",
                         "()Ljava/awt/Point;");

    keyclass = (*env)->FindClass(env, "java/awt/event/KeyEvent");
    DASSERT (keyclass != NULL);

    componentIDs.isProxyActive =
        (*env)->GetFieldID(env, keyclass, "isProxyActive",
                           "Z");

    componentIDs.appContext =
        (*env)->GetFieldID(env, cls, "appContext",
                           "Lsun/awt/AppContext;");

    (*env)->DeleteLocalRef(env, keyclass);
}


JNIEXPORT void JNICALL
Java_java_awt_Container_initIDs
  (JNIEnv *env, jclass cls)
{

}


JNIEXPORT void JNICALL
Java_java_awt_Button_initIDs
  (JNIEnv *env, jclass cls)
{

}

JNIEXPORT void JNICALL
Java_java_awt_Scrollbar_initIDs
  (JNIEnv *env, jclass cls)
{

}


JNIEXPORT void JNICALL
Java_java_awt_Window_initIDs
  (JNIEnv *env, jclass cls)
{

}

JNIEXPORT void JNICALL
Java_java_awt_Frame_initIDs
  (JNIEnv *env, jclass cls)
{

}


JNIEXPORT void JNICALL
Java_java_awt_MenuComponent_initIDs(JNIEnv *env, jclass cls)
{
    menuComponentIDs.appContext =
      (*env)->GetFieldID(env, cls, "appContext", "Lsun/awt/AppContext;");
}

JNIEXPORT void JNICALL
Java_java_awt_Cursor_initIDs(JNIEnv *env, jclass cls)
{
}


JNIEXPORT void JNICALL Java_java_awt_MenuItem_initIDs
  (JNIEnv *env, jclass cls)
{
}


JNIEXPORT void JNICALL Java_java_awt_Menu_initIDs
  (JNIEnv *env, jclass cls)
{
}

JNIEXPORT void JNICALL
Java_java_awt_TextArea_initIDs
  (JNIEnv *env, jclass cls)
{
}


JNIEXPORT void JNICALL
Java_java_awt_Checkbox_initIDs
  (JNIEnv *env, jclass cls)
{
}


JNIEXPORT void JNICALL Java_java_awt_ScrollPane_initIDs
  (JNIEnv *env, jclass cls)
{
}

JNIEXPORT void JNICALL
Java_java_awt_TextField_initIDs
  (JNIEnv *env, jclass cls)
{
}

JNIEXPORT jboolean JNICALL AWTIsHeadless() {
#ifdef HEADLESS
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT void JNICALL Java_java_awt_Dialog_initIDs (JNIEnv *env, jclass cls)
{
}


/* ========================== Begin poll section ================================ */

// Includes

#include <sys/time.h>
#include <limits.h>
#include <locale.h>
#include <pthread.h>

#include <dlfcn.h>
#include <fcntl.h>

#include <poll.h>
#ifndef POLLRDNORM
#define POLLRDNORM POLLIN
#endif

// Prototypes

static void     waitForEvents(JNIEnv *, jlong);
static void     awt_pipe_init();
static void     processOneEvent(XtInputMask iMask);
static void     performPoll(JNIEnv *, jlong);
static void     wakeUp();
static void     update_poll_timeout(int timeout_control);
static uint32_t get_poll_timeout(jlong nextTaskTime);

// Defines

#ifndef bzero
#define bzero(a,b) memset(a, 0, b)
#endif

#define AWT_POLL_BUFSIZE        100 /* bytes */
#define AWT_READPIPE            (awt_pipe_fds[0])
#define AWT_WRITEPIPE           (awt_pipe_fds[1])

#define DEF_AWT_MAX_POLL_TIMEOUT ((uint32_t)500) /* milliseconds */
#define DEF_AWT_FLUSH_TIMEOUT ((uint32_t)100) /* milliseconds */
#define AWT_MIN_POLL_TIMEOUT ((uint32_t)0) /* milliseconds */

#define TIMEOUT_TIMEDOUT 0
#define TIMEOUT_EVENTS 1

// Static fields

static uint32_t AWT_FLUSH_TIMEOUT  =  DEF_AWT_FLUSH_TIMEOUT; /* milliseconds */
static uint32_t AWT_MAX_POLL_TIMEOUT = DEF_AWT_MAX_POLL_TIMEOUT; /* milliseconds */
static pthread_t    awt_MainThread = 0;
static int32_t      awt_pipe_fds[2];                   /* fds for wkaeup pipe */
static Boolean      awt_pipe_inited = False;           /* make sure pipe is initialized before write */
static jlong        awt_next_flush_time = 0LL; /* 0 == no scheduled flush */
static jlong        awt_last_flush_time = 0LL; /* 0 == no scheduled flush */
static uint32_t     curPollTimeout;
static struct pollfd pollFds[2];
static jlong        poll_sleep_time = 0LL; // Used for tracing
static jlong        poll_wakeup_time = 0LL; // Used for tracing

// AWT static poll timeout.  Zero means "not set", aging algorithm is
// used.  Static poll timeout values higher than 50 cause application
// look "slow" - they don't respond to user request fast
// enough. Static poll timeout value less than 10 are usually
// considered by schedulers as zero, so this might cause unnecessary
// CPU consumption by Java.  The values between 10 - 50 are suggested
// for single client desktop configurations.  For SunRay servers, it
// is highly recomended to use aging algorithm (set static poll timeout
// to 0).
static int32_t static_poll_timeout = 0;

static Bool isMainThread() {
    return awt_MainThread == pthread_self();
}

/*
 * Creates the AWT utility pipe. This pipe exists solely so that
 * we can cause the main event thread to wake up from a poll() or
 * select() by writing to this pipe.
 */
static void
awt_pipe_init() {

    if (awt_pipe_inited) {
        return;
    }

    if ( pipe ( awt_pipe_fds ) == 0 )
    {
        /*
        ** the write wakes us up from the infinite sleep, which
        ** then we cause a delay of AWT_FLUSHTIME and then we
        ** flush.
        */
        int32_t flags = 0;
        /* set the pipe to be non-blocking */
        flags = fcntl ( AWT_READPIPE, F_GETFL, 0 );
        fcntl( AWT_READPIPE, F_SETFL, flags | O_NDELAY | O_NONBLOCK );
        flags = fcntl ( AWT_WRITEPIPE, F_GETFL, 0 );
        fcntl( AWT_WRITEPIPE, F_SETFL, flags | O_NDELAY | O_NONBLOCK );
        awt_pipe_inited = True;
    }
    else
    {
        AWT_READPIPE = -1;
        AWT_WRITEPIPE = -1;
    }


} /* awt_pipe_init() */

/**
 * Reads environment variables to initialize timeout fields.
 */
static void readEnv() {
    char * value;
    static Boolean env_read = False;
    if (env_read) return;

    env_read = True;

    value = getenv("_AWT_MAX_POLL_TIMEOUT");
    if (value != NULL) {
        AWT_MAX_POLL_TIMEOUT = atoi(value);
        if (AWT_MAX_POLL_TIMEOUT == 0) {
            AWT_MAX_POLL_TIMEOUT = DEF_AWT_MAX_POLL_TIMEOUT;
        }
    }
    curPollTimeout = AWT_MAX_POLL_TIMEOUT/2;

    value = getenv("_AWT_FLUSH_TIMEOUT");
    if (value != NULL) {
        AWT_FLUSH_TIMEOUT = atoi(value);
        if (AWT_FLUSH_TIMEOUT == 0) {
            AWT_FLUSH_TIMEOUT = DEF_AWT_FLUSH_TIMEOUT;
        }
    }

    value = getenv("_AWT_POLL_TRACING");
    if (value != NULL) {
        tracing = atoi(value);
    }

    value = getenv("_AWT_STATIC_POLL_TIMEOUT");
    if (value != NULL) {
        static_poll_timeout = atoi(value);
    }
    if (static_poll_timeout != 0) {
        curPollTimeout = static_poll_timeout;
    }
}

/**
 * Returns the amount of milliseconds similar to System.currentTimeMillis()
 */
static jlong
awtJNI_TimeMillis(void)
{
    struct timeval t;

    gettimeofday(&t, 0);

    return jlong_add(jlong_mul(jint_to_jlong(t.tv_sec), jint_to_jlong(1000)),
             jint_to_jlong(t.tv_usec / 1000));
}

/**
 * Updates curPollTimeout according to the aging algorithm.
 * @param timeout_control Either TIMEOUT_TIMEDOUT or TIMEOUT_EVENTS
 */
static void update_poll_timeout(int timeout_control) {
    PRINT2("tout: %d\n", timeout_control);

    // If static_poll_timeout is set, curPollTimeout has the fixed value
    if (static_poll_timeout != 0) return;

    // Update it otherwise
    if (timeout_control == TIMEOUT_TIMEDOUT) {
        /* add 1/4 (plus 1, in case the division truncates to 0) */
        curPollTimeout += ((curPollTimeout>>2) + 1);
        curPollTimeout = min(AWT_MAX_POLL_TIMEOUT, curPollTimeout);
    } else if (timeout_control == TIMEOUT_EVENTS) {
        /* subtract 1/4 (plus 1, in case the division truncates to 0) */
        curPollTimeout -= ((curPollTimeout>>2) + 1);
        curPollTimeout = max(AWT_MIN_POLL_TIMEOUT, curPollTimeout);
    }
}

/*
 * Gets the best timeout for the next call to poll().
 *
 * @param nextTaskTime -1, if there are no tasks; next time when
 * timeout task needs to be run, in millis(of currentTimeMillis)
 */
static uint32_t get_poll_timeout(jlong nextTaskTime)
{
    jlong curTime = awtJNI_TimeMillis();
    uint32_t timeout = curPollTimeout;
    uint32_t taskTimeout = (nextTaskTime == -1) ? AWT_MAX_POLL_TIMEOUT : (uint32_t)max(0, (int32_t)(nextTaskTime - curTime));
    uint32_t flushTimeout = (awt_next_flush_time > 0) ? (uint32_t)max(0, (int32_t)(awt_next_flush_time - curTime)) : AWT_MAX_POLL_TIMEOUT;

    PRINT2("to: %d, ft: %d, to: %d, tt: %d, mil: %d\n", taskTimeout, flushTimeout, timeout, (int)nextTaskTime, (int)curTime);

    // Adjust timeout to flush_time and task_time
    return min(flushTimeout, min(taskTimeout, timeout));
} /* awt_get_poll_timeout() */

/*
 * Waits for X/Xt events to appear on the pipe. Returns only when
 * it is likely (but not definite) that there are events waiting to
 * be processed.
 *
 * This routine also flushes the outgoing X queue, when the
 * awt_next_flush_time has been reached.
 *
 * If fdAWTPipe is greater or equal than zero the routine also
 * checks if there are events pending on the putback queue.
 */
void
waitForEvents(JNIEnv *env, jlong nextTaskTime) {
    performPoll(env, nextTaskTime);
    if ((awt_next_flush_time > 0) && (awtJNI_TimeMillis() >= awt_next_flush_time)) {
        XFlush(awt_display);
        awt_last_flush_time = awt_next_flush_time;
        awt_next_flush_time = 0LL;
    }
} /* waitForEvents() */

JNIEXPORT void JNICALL Java_sun_awt_X11_XToolkit_waitForEvents (JNIEnv *env, jclass class, jlong nextTaskTime) {
    waitForEvents(env, nextTaskTime);
}

JNIEXPORT void JNICALL Java_sun_awt_X11_XToolkit_awt_1toolkit_1init (JNIEnv *env, jclass class) {
    awt_MainThread = pthread_self();

    awt_pipe_init();
    readEnv();
}

JNIEXPORT void JNICALL Java_sun_awt_X11_XToolkit_awt_1output_1flush (JNIEnv *env, jclass class) {
    awt_output_flush();
}

JNIEXPORT void JNICALL Java_sun_awt_X11_XToolkit_wakeup_1poll (JNIEnv *env, jclass class) {
    wakeUp();
}

/*
 * Polls both the X pipe and our AWT utility pipe. Returns
 * when there is data on one of the pipes, or the operation times
 * out.
 *
 * Not all Xt events come across the X pipe (e.g., timers
 * and alternate inputs), so we must time out every now and
 * then to check the Xt event queue.
 *
 * The fdAWTPipe will be empty when this returns.
 */
static void
performPoll(JNIEnv *env, jlong nextTaskTime) {
    static Bool pollFdsInited = False;
    static char read_buf[AWT_POLL_BUFSIZE + 1];    /* dummy buf to empty pipe */

    uint32_t timeout = get_poll_timeout(nextTaskTime);
    int32_t result;

    if (!pollFdsInited) {
        pollFds[0].fd = ConnectionNumber(awt_display);
        pollFds[0].events = POLLRDNORM;
        pollFds[0].revents = 0;

        pollFds[1].fd = AWT_READPIPE;
        pollFds[1].events = POLLRDNORM;
        pollFds[1].revents = 0;
        pollFdsInited = True;
    } else {
        pollFds[0].revents = 0;
        pollFds[1].revents = 0;
    }

    AWT_NOFLUSH_UNLOCK();

    /* ACTUALLY DO THE POLL() */
    if (timeout == 0) {
        // be sure other threads get a chance
        awtJNI_ThreadYield(env);
    }

    if (tracing) poll_sleep_time = awtJNI_TimeMillis();
    result = poll( pollFds, 2, (int32_t) timeout );
    if (tracing) poll_wakeup_time = awtJNI_TimeMillis();
    PRINT("%d of %d, res: %d\n", (int)(poll_wakeup_time-poll_sleep_time), (int)timeout, result);

    AWT_LOCK();
    if (result == 0) {
        /* poll() timed out -- update timeout value */
        update_poll_timeout(TIMEOUT_TIMEDOUT);
    }
    if (pollFds[1].revents) {
        int count;
        PRINT("Woke up\n");
        /* There is data on the AWT pipe - empty it */
        do {
            count = read(AWT_READPIPE, read_buf, AWT_POLL_BUFSIZE );
        } while (count == AWT_POLL_BUFSIZE );
    }
    if (pollFds[0].revents) {
        // Events in X pipe
        update_poll_timeout(TIMEOUT_EVENTS);
    }
    return;

} /* performPoll() */

/**
 * Schedules next auto-flush event or performs forced flush depending
 * on the time of the previous flush.
 */
void awt_output_flush() {
    if (awt_next_flush_time == 0) {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

        jlong curTime = awtJNI_TimeMillis(); // current time
        jlong l_awt_last_flush_time = awt_last_flush_time; // last time we flushed queue
        jlong next_flush_time = l_awt_last_flush_time + AWT_FLUSH_TIMEOUT;

        if (curTime >= next_flush_time) {
            // Enough time passed from last flush
            PRINT("f1\n");
            AWT_LOCK();
            XFlush(awt_display);
            awt_last_flush_time = curTime;
            AWT_NOFLUSH_UNLOCK();
        } else {
            awt_next_flush_time = next_flush_time;
            PRINT("f2\n");
            wakeUp();
        }
    }
}


/**
 * Wakes-up poll() in performPoll
 */
static void wakeUp() {
    static char wakeUp_char = 'p';
    if (!isMainThread() && awt_pipe_inited) {
        write ( AWT_WRITEPIPE, &wakeUp_char, 1 );
    }
}


/* ========================== End poll section ================================= */

/*
 * Class:     java_awt_KeyboardFocusManager
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_awt_KeyboardFocusManager_initIDs
    (JNIEnv *env, jclass cls)
{
}

/*
 * Class:     sun_awt_X11_XToolkit
 * Method:    getEnv
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_X11_XToolkit_getEnv
(JNIEnv *env , jclass clazz, jstring key) {
    char *ptr = NULL;
    const char *keystr = NULL;
    jstring ret = NULL;

    keystr = JNU_GetStringPlatformChars(env, key, NULL);
    if (keystr) {
        ptr = getenv(keystr);
        if (ptr) {
            ret = JNU_NewStringPlatform(env, (const char *) ptr);
        }
        JNU_ReleaseStringPlatformChars(env, key, (const char*)keystr);
    }
    return ret;
}

#ifdef __linux__
void print_stack(void)
{
  void *array[10];
  size_t size;
  char **strings;
  size_t i;

  size = backtrace (array, 10);
  strings = backtrace_symbols (array, size);

  fprintf (stderr, "Obtained %zd stack frames.\n", size);

  for (i = 0; i < size; i++)
     fprintf (stderr, "%s\n", strings[i]);

  free (strings);
}
#endif

Window get_xawt_root_shell(JNIEnv *env) {
  static jclass classXRootWindow = NULL;
  static jmethodID methodGetXRootWindow = NULL;
  static Window xawt_root_shell = None;

  if (xawt_root_shell == None){
      if (classXRootWindow == NULL){
          jclass cls_tmp = (*env)->FindClass(env, "sun/awt/X11/XRootWindow");
          classXRootWindow = (jclass)(*env)->NewGlobalRef(env, cls_tmp);
          (*env)->DeleteLocalRef(env, cls_tmp);
      }
      if( classXRootWindow != NULL) {
          methodGetXRootWindow = (*env)->GetStaticMethodID(env, classXRootWindow, "getXRootWindow", "()J");
      }
      if( classXRootWindow != NULL && methodGetXRootWindow !=NULL ) {
          xawt_root_shell = (Window) (*env)->CallStaticLongMethod(env, classXRootWindow, methodGetXRootWindow);
      }
      if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
      }
  }
  return xawt_root_shell;
}

/*
 * Old, compatibility, backdoor for DT.  This is a different
 * implementation.  It keeps the signature, but acts on
 * awt_root_shell, not the frame passed as an argument.  Note, that
 * the code that uses the old backdoor doesn't work correctly with
 * gnome session proxy that checks for WM_COMMAND when the window is
 * firts mapped, because DT code calls this old backdoor *after* the
 * frame is shown or it would get NPE with old AWT (previous
 * implementation of this backdoor) otherwise.  Old style session
 * managers (e.g. CDE) that check WM_COMMAND only during session
 * checkpoint should work fine, though.
 *
 * NB: The function name looks deceptively like a JNI native method
 * name.  It's not!  It's just a plain function.
 */

JNIEXPORT void JNICALL
Java_sun_awt_motif_XsessionWMcommand(JNIEnv *env, jobject this,
    jobject frame, jstring jcommand)
{
    const char *command;
    XTextProperty text_prop;
    char *c[1];
    int32_t status;
    Window xawt_root_window;

    AWT_LOCK();
    xawt_root_window = get_xawt_root_shell(env);

    if ( xawt_root_window == None ) {
        JNU_ThrowNullPointerException(env, "AWT root shell is unrealized");
        AWT_UNLOCK();
        return;
    }

    command = (char *) JNU_GetStringPlatformChars(env, jcommand, NULL);
    c[0] = (char *)command;
    status = XmbTextListToTextProperty(awt_display, c, 1,
                                       XStdICCTextStyle, &text_prop);

    if (status == Success || status > 0) {
        XSetTextProperty(awt_display, xawt_root_window,
                         &text_prop, XA_WM_COMMAND);
        if (text_prop.value != NULL)
            XFree(text_prop.value);
    }
    JNU_ReleaseStringPlatformChars(env, jcommand, command);
    AWT_UNLOCK();
}


/*
 * New DT backdoor to set WM_COMMAND.  New code should use this
 * backdoor and call it *before* the first frame is shown so that
 * gnome session proxy can correctly handle it.
 *
 * NB: The function name looks deceptively like a JNI native method
 * name.  It's not!  It's just a plain function.
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_XsessionWMcommand_New(JNIEnv *env, jobjectArray jargv)
{
    static const char empty[] = "";

    int argc;
    const char **cargv;
    XTextProperty text_prop;
    int status;
    int i;
    Window xawt_root_window;

    AWT_LOCK();
    xawt_root_window = get_xawt_root_shell(env);

    if (xawt_root_window == None) {
      JNU_ThrowNullPointerException(env, "AWT root shell is unrealized");
      AWT_UNLOCK();
      return;
    }

    argc = (int)(*env)->GetArrayLength(env, jargv);
    if (argc == 0) {
        AWT_UNLOCK();
        return;
    }

    /* array of C strings */
    cargv = (const char **)calloc(argc, sizeof(char *));
    if (cargv == NULL) {
        JNU_ThrowOutOfMemoryError(env, "Unable to allocate cargv");
        AWT_UNLOCK();
        return;
    }

    /* fill C array with platform chars of java strings */
      for (i = 0; i < argc; ++i) {
        jstring js;
        const char *cs;

        cs = NULL;
        js = (*env)->GetObjectArrayElement(env, jargv, i);
        if (js != NULL) {
            cs = JNU_GetStringPlatformChars(env, js, NULL);
        }
        if (cs == NULL) {
            cs = empty;
        }
        cargv[i] = cs;
        (*env)->DeleteLocalRef(env, js);
    }

    /* grr, X prototype doesn't declare cargv as const, thought it really is */
    status = XmbTextListToTextProperty(awt_display, (char **)cargv, argc,
                                       XStdICCTextStyle, &text_prop);
    if (status < 0) {
        switch (status) {
        case XNoMemory:
            JNU_ThrowOutOfMemoryError(env,
                "XmbTextListToTextProperty: XNoMemory");
            break;
        case XLocaleNotSupported:
            JNU_ThrowInternalError(env,
                "XmbTextListToTextProperty: XLocaleNotSupported");
            break;
        case XConverterNotFound:
            JNU_ThrowNullPointerException(env,
                "XmbTextListToTextProperty: XConverterNotFound");
            break;
        default:
            JNU_ThrowInternalError(env,
                "XmbTextListToTextProperty: unknown error");
        }
    } else {

    XSetTextProperty(awt_display, xawt_root_window,
                         &text_prop, XA_WM_COMMAND);
    }

    for (i = 0; i < argc; ++i) {
        jstring js;

        if (cargv[i] == empty)
            continue;

        js = (*env)->GetObjectArrayElement(env, jargv, i);
        JNU_ReleaseStringPlatformChars(env, js, cargv[i]);
        (*env)->DeleteLocalRef(env, js);
    }
    if (text_prop.value != NULL)
        XFree(text_prop.value);
    AWT_UNLOCK();
}

/*
 * Class:     java_awt_TrayIcon
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_java_awt_TrayIcon_initIDs(JNIEnv *env , jclass clazz)
{
}


/*
 * Class:     java_awt_Cursor
 * Method:    finalizeImpl
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_awt_Cursor_finalizeImpl(JNIEnv *env, jclass clazz, jlong pData)
{
    Cursor xcursor;

    xcursor = (Cursor)pData;
    if (xcursor != None) {
        AWT_LOCK();
        XFreeCursor(awt_display, xcursor);
        AWT_UNLOCK();
    }
}


/*
 * Class:     sun_awt_X11_XToolkit
 * Method:    getNumberOfButtonsImpl
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_X11_XToolkit_getNumberOfButtonsImpl
(JNIEnv * env, jobject cls){
    if (num_buttons == 0) {
        num_buttons = getNumButtons();
    }
    return num_buttons;
}

int32_t getNumButtons() {
    int32_t major_opcode, first_event, first_error;
    int32_t xinputAvailable;
    int32_t numDevices, devIdx, clsIdx;
    XDeviceInfo* devices;
    XDeviceInfo* aDevice;
    XButtonInfo* bInfo;
    int32_t local_num_buttons = 0;

    /* 4700242:
     * If XTest is asked to press a non-existant mouse button
     * (i.e. press Button3 on a system configured with a 2-button mouse),
     * then a crash may happen.  To avoid this, we use the XInput
     * extension to query for the number of buttons on the XPointer, and check
     * before calling XTestFakeButtonEvent().
     */
    xinputAvailable = XQueryExtension(awt_display, INAME, &major_opcode, &first_event, &first_error);
    DTRACE_PRINTLN3("RobotPeer: XQueryExtension(XINPUT) returns major_opcode = %d, first_event = %d, first_error = %d",
                    major_opcode, first_event, first_error);
    if (xinputAvailable) {
        devices = XListInputDevices(awt_display, &numDevices);
        for (devIdx = 0; devIdx < numDevices; devIdx++) {
            aDevice = &(devices[devIdx]);
#ifdef IsXExtensionPointer
            if (aDevice->use == IsXExtensionPointer) {
                for (clsIdx = 0; clsIdx < aDevice->num_classes; clsIdx++) {
                    if (aDevice->inputclassinfo[clsIdx].class == ButtonClass) {
                        bInfo = (XButtonInfo*)(&(aDevice->inputclassinfo[clsIdx]));
                        local_num_buttons = bInfo->num_buttons;
                        DTRACE_PRINTLN1("RobotPeer: XPointer has %d buttons", num_buttons);
                        break;
                    }
                }
                break;
            }
#endif
            if (local_num_buttons <= 0 ) {
                if (aDevice->use == IsXPointer) {
                    for (clsIdx = 0; clsIdx < aDevice->num_classes; clsIdx++) {
                        if (aDevice->inputclassinfo[clsIdx].class == ButtonClass) {
                            bInfo = (XButtonInfo*)(&(aDevice->inputclassinfo[clsIdx]));
                            local_num_buttons = bInfo->num_buttons;
                            DTRACE_PRINTLN1("RobotPeer: XPointer has %d buttons", num_buttons);
                            break;
                        }
                    }
                    break;
                }
            }
        }

        XFreeDeviceList(devices);
    }
    else {
        DTRACE_PRINTLN1("RobotPeer: XINPUT extension is unavailable, assuming %d mouse buttons", num_buttons);
    }
    if (local_num_buttons == 0 ) {
        local_num_buttons = 3;
    }

    return local_num_buttons;
}
