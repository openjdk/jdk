/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifdef HEADLESS
#error This file should not be included in headless library
#endif

#include <dlfcn.h>
#include "jni_util.h"
#include "awt.h"
#include "screencast_pipewire.h"
#include "fp_pipewire.h"
#include <stdio.h>

#include "gtk_interface.h"
#include "gtk3_interface.h"

int DEBUG_SCREENCAST_ENABLED = FALSE;

#define EXCEPTION_CHECK_DESCRIBE() if ((*env)->ExceptionCheck(env)) { \
                                      (*env)->ExceptionDescribe(env); \
                                   }

static gboolean hasPipewireFailed = FALSE;
static gboolean sessionClosed = TRUE;
static GString *activeSessionToken;

struct ScreenSpace screenSpace = {0};
static struct PwLoopData pw = {0};

jclass tokenStorageClass = NULL;
jmethodID storeTokenMethodID = NULL;

#if defined(AIX) && defined(__open_xl_version__) && __open_xl_version__ >= 17
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wformat-nonliteral"
#endif

inline void debug_screencast(
        const char *__restrict fmt,
        ...
) {
    if (DEBUG_SCREENCAST_ENABLED) {
        va_list myargs;
        va_start(myargs, fmt);
        vfprintf(stdout, fmt, myargs);
        va_end(myargs);
    }
}

/**
 * @return TRUE on success
 */
static gboolean initScreenSpace() {
    screenSpace.screenCount = 0;
    screenSpace.allocated = SCREEN_SPACE_DEFAULT_ALLOCATED;
    screenSpace.screens = calloc(
            SCREEN_SPACE_DEFAULT_ALLOCATED,
            sizeof(struct ScreenProps)
    );

    if (!screenSpace.screens) {
        ERR("failed to allocate memory\n");
        return FALSE;
    }
    return TRUE;
}

static void doCleanup() {
    if (pw.loop) {
        DEBUG_SCREENCAST("STOPPING loop\n", NULL);
        fp_pw_thread_loop_stop(pw.loop);
    }

    for (int i = 0; i < screenSpace.screenCount; ++i) {
        struct ScreenProps *screenProps = &screenSpace.screens[i];
        if (screenProps->data) {
            if (screenProps->data->stream) {
                fp_pw_thread_loop_lock(pw.loop);
                fp_pw_stream_disconnect(screenProps->data->stream);
                fp_pw_stream_destroy(screenProps->data->stream);
                fp_pw_thread_loop_unlock(pw.loop);
                screenProps->data->stream = NULL;
            }
            free(screenProps->data);
            screenProps->data = NULL;
        }
    }

    if (pw.pwFd > 0) {
        close(pw.pwFd);
        pw.pwFd = -1;
    }

    portalScreenCastCleanup();

    if (pw.core) {
        fp_pw_core_disconnect(pw.core);
        pw.core = NULL;
    }

    if (pw.loop) {
        fp_pw_thread_loop_destroy(pw.loop);
        pw.loop = NULL;
    }

    if (screenSpace.screens) {
        free(screenSpace.screens);
        screenSpace.screens = NULL;
        screenSpace.screenCount = 0;
    }

    if (!sessionClosed) {
        fp_pw_deinit();
    }

    gtk->g_string_set_size(activeSessionToken, 0);
    sessionClosed = TRUE;
}

/**
 * @return TRUE on success
 */
static gboolean initScreencast(const gchar *token,
                               GdkRectangle *affectedBounds,
                               gint affectedBoundsLength) {
    gboolean isSameToken = !token
            ? FALSE
            : strcmp(token, activeSessionToken->str) == 0;

    if (!sessionClosed) {
        if (isSameToken) {
            DEBUG_SCREENCAST("Reusing active session.\n", NULL);
            return TRUE;
        } else {
            DEBUG_SCREENCAST(
                    "Active session has a different token |%s| -> |%s|,"
                    " closing current session.\n",
                    activeSessionToken->str, token
            );
            doCleanup();
        }
    }

    fp_pw_init(NULL, NULL);

    pw.pwFd = RESULT_ERROR;

    if (!initScreenSpace()
        || !initXdgDesktopPortal()
        || (pw.pwFd = getPipewireFd(token,
                                    affectedBounds,
                                    affectedBoundsLength)) < 0) {
        doCleanup();
        return FALSE;
    }

    gtk->g_string_printf(activeSessionToken, "%s", token);
    hasPipewireFailed = FALSE;
    sessionClosed = FALSE;
    return TRUE;
}

static void onStreamParamChanged(
        void *userdata,
        uint32_t id,
        const struct spa_pod *param
) {
    struct PwStreamData *data = userdata;
    uint32_t mediaType;
    uint32_t mediaSubtype;

    DEBUG_SCREEN_PREFIX(data->screenProps, "param event id %i\n", id);

    if (param == NULL || id != SPA_PARAM_Format) {
        return;
    }

    if (spa_format_parse(param,
                         &mediaType,
                         &mediaSubtype) < 0) {
        return;
    }

    if (mediaType != SPA_MEDIA_TYPE_video ||
        mediaSubtype != SPA_MEDIA_SUBTYPE_raw) {
        return;
    }

    if (spa_format_video_raw_parse(param, &data->rawFormat) < 0) {
        return;
    }

    DEBUG_SCREEN_PREFIX(data->screenProps, "stream format: %s (%d)\t%dx%d\n",
                     spa_debug_type_find_name(
                             spa_type_video_format,
                             data->rawFormat.format
                     ),
                     data->rawFormat.format,
                     data->rawFormat.size.width,
                     data->rawFormat.size.height);

    data->hasFormat = TRUE;
    fp_pw_thread_loop_signal(pw.loop, TRUE);
}

static void onStreamProcess(void *userdata) {
    struct PwStreamData *data = userdata;

    struct ScreenProps *screen = data->screenProps;

    DEBUG_SCREEN_PREFIX(screen,
                        "hasFormat %i "
                        "captureDataReady %i shouldCapture %i\n",
                        data->hasFormat,
                        screen->captureDataReady,
                        screen->shouldCapture
    );
    if (
            !data->hasFormat
            || !screen->shouldCapture
            || screen->captureDataReady
    ) {
        return;
    }

    struct pw_buffer *pwBuffer;
    struct spa_buffer *spaBuffer;

    if (!data->stream
        || (pwBuffer = fp_pw_stream_dequeue_buffer(data->stream)) == NULL) {
        DEBUG_SCREEN_PREFIX(screen, "!!! out of buffers\n", NULL);
        return;
    }

    spaBuffer = pwBuffer->buffer;
    if (!spaBuffer
        || spaBuffer->n_datas < 1
        || spaBuffer->datas[0].data == NULL) {
        DEBUG_SCREEN_PREFIX(screen, "!!! no data, n_datas %d\n",
                            spaBuffer->n_datas);
        return;
    }

    struct spa_data spaData = spaBuffer->datas[0];

    gint streamWidth = data->rawFormat.size.width;
    gint streamHeight = data->rawFormat.size.height;

    DEBUG_SCREEN(screen);
    DEBUG_SCREEN_PREFIX(screen,
                        "got a frame of size %d offset %d stride %d "
                        "flags %d FD %li captureDataReady %i of stream %dx%d\n",
                        spaBuffer->datas[0].chunk->size,
                        spaData.chunk->offset,
                        spaData.chunk->stride,
                        spaData.chunk->flags,
                        spaData.fd,
                        screen->captureDataReady,
                        streamWidth,
                        streamHeight
    );

    GdkRectangle captureArea = screen->captureArea;
    GdkRectangle screenBounds = screen->bounds;

    GdkPixbuf *pixbuf = gtk->gdk_pixbuf_new_from_data(spaData.data,
                                                      GDK_COLORSPACE_RGB,
                                                      TRUE,
                                                      8,
                                                      streamWidth,
                                                      streamHeight,
                                                      spaData.chunk->stride,
                                                      NULL,
                                                      NULL);

    if (screen->bounds.width != streamWidth
        || screen->bounds.height != streamHeight) {

        DEBUG_SCREEN_PREFIX(screen, "scaling stream data %dx%d -> %dx%d\n",
                         streamWidth, streamHeight,
                         screen->bounds.width, screen->bounds.height
        );

        GdkPixbuf *scaled = gtk->gdk_pixbuf_scale_simple(pixbuf,
                                                         screen->bounds.width,
                                                         screen->bounds.height,
                                                         GDK_INTERP_BILINEAR);

        gtk->g_object_unref(pixbuf);
        pixbuf = scaled;
    }

    GdkPixbuf *cropped = NULL;
    if (captureArea.width != screenBounds.width
        || captureArea.height != screenBounds.height) {

        cropped = gtk->gdk_pixbuf_new(GDK_COLORSPACE_RGB,
                                      TRUE,
                                      8,
                                      captureArea.width,
                                      captureArea.height);
        if (cropped) {
            gtk->gdk_pixbuf_copy_area(pixbuf,
                                      captureArea.x,
                                      captureArea.y,
                                      captureArea.width,
                                      captureArea.height,
                                      cropped,
                                      0, 0);
        } else {
            ERR("Cannot create a new pixbuf.\n");
        }

        gtk->g_object_unref(pixbuf);
        pixbuf = NULL;

        data->screenProps->captureDataPixbuf = cropped;
    } else {
        data->screenProps->captureDataPixbuf = pixbuf;
    }

    screen->captureDataReady = TRUE;

    DEBUG_SCREEN_PREFIX(screen, "data ready\n", NULL);
    fp_pw_stream_queue_buffer(data->stream, pwBuffer);

    fp_pw_thread_loop_signal(pw.loop, FALSE);
}

static void onStreamStateChanged(
        void *userdata,
        enum pw_stream_state old,
        enum pw_stream_state state,
        const char *error
) {
    struct PwStreamData *data = userdata;
    DEBUG_SCREEN_PREFIX(data->screenProps, "state %i (%s) -> %i (%s) err %s\n",
                     old, fp_pw_stream_state_as_string(old),
                     state, fp_pw_stream_state_as_string(state),
                     error);
    if (state == PW_STREAM_STATE_ERROR
        || state == PW_STREAM_STATE_UNCONNECTED) {

        hasPipewireFailed = TRUE;
        fp_pw_thread_loop_signal(pw.loop, FALSE);
    }
}

static const struct pw_stream_events streamEvents = {
        PW_VERSION_STREAM_EVENTS,
        .param_changed = onStreamParamChanged,
        .process = onStreamProcess,
        .state_changed = onStreamStateChanged,
};


static bool startStream(
        struct pw_stream *stream,
        uint32_t node
) {
    char buffer[1024];
    struct spa_pod_builder builder =
            SPA_POD_BUILDER_INIT(buffer, sizeof(buffer));
    const struct spa_pod *param;


    param = spa_pod_builder_add_object(
            &builder,
            SPA_TYPE_OBJECT_Format,
            SPA_PARAM_EnumFormat,
            SPA_FORMAT_mediaType,
            SPA_POD_Id(SPA_MEDIA_TYPE_video),
            SPA_FORMAT_mediaSubtype,
            SPA_POD_Id(SPA_MEDIA_SUBTYPE_raw),
            SPA_FORMAT_VIDEO_format,
            SPA_POD_Id(SPA_VIDEO_FORMAT_BGRx),
            SPA_FORMAT_VIDEO_size,
            SPA_POD_CHOICE_RANGE_Rectangle(
                    &SPA_RECTANGLE(320, 240),
                    &SPA_RECTANGLE(1, 1),
                    &SPA_RECTANGLE(8192, 8192)
            ),
            SPA_FORMAT_VIDEO_framerate,
            SPA_POD_CHOICE_RANGE_Fraction(
                    &SPA_FRACTION(25, 1),
                    &SPA_FRACTION(0, 1),
                    &SPA_FRACTION(1000, 1)
            )
    );

    DEBUG_SCREENCAST("screenId#%i: stream connecting %p\n", node, stream);

    return fp_pw_stream_connect(
            stream,
            PW_DIRECTION_INPUT,
            node,
            PW_STREAM_FLAG_AUTOCONNECT
            | PW_STREAM_FLAG_MAP_BUFFERS,
            &param,
            1
    ) >= 0;
}

/**
 * @param index of a screen
 * @return TRUE on success
 */
static gboolean connectStream(int index) {
    DEBUG_SCREENCAST("@@@ using screen %i\n", index);
    if (index >= screenSpace.screenCount) {
        DEBUG_SCREENCAST("!!! Wrong index for screen\n", NULL);
        return FALSE;
    }

    struct PwStreamData *data = screenSpace.screens[index].data;

    data->screenProps = &screenSpace.screens[index];

    if (!sessionClosed && data->stream) {
        fp_pw_thread_loop_lock(pw.loop);
        int result = fp_pw_stream_set_active(data->stream, TRUE);
        fp_pw_thread_loop_unlock(pw.loop);

        DEBUG_SCREEN_PREFIX(data->screenProps,
                            "stream %p: activate result |%i|\n",
                            data->stream, result);

        return result == 0; // 0 - success
    };

    data->hasFormat = FALSE;

    data->stream = fp_pw_stream_new(
            pw.core,
            "AWT Screen Stream",
            fp_pw_properties_new(
                    PW_KEY_MEDIA_TYPE, "Video",
                    PW_KEY_MEDIA_CATEGORY, "Capture",
                    PW_KEY_MEDIA_ROLE, "Screen",
                    NULL
            )
    );

    if (!data->stream) {
        DEBUG_SCREEN_PREFIX(data->screenProps,
                            "!!! Could not create a pipewire stream\n", NULL);
        fp_pw_thread_loop_unlock(pw.loop);
        return FALSE;
    }

    fp_pw_stream_add_listener(
            data->stream,
            &data->streamListener,
            &streamEvents,
            data
    );

    DEBUG_SCREEN(data->screenProps);

    if (!startStream(data->stream, screenSpace.screens[index].id)){
        DEBUG_SCREEN_PREFIX(data->screenProps,
                            "!!! Could not start a pipewire stream\n", NULL);
        fp_pw_thread_loop_unlock(pw.loop);
        return FALSE;
    }

    while (!data->hasFormat) {
        fp_pw_thread_loop_wait(pw.loop);
        fp_pw_thread_loop_accept(pw.loop);
        if (hasPipewireFailed) {
            fp_pw_thread_loop_unlock(pw.loop);
            return FALSE;
        }
    }

    DEBUG_SCREEN_PREFIX(data->screenProps,
            "frame size: %dx%d\n",
            data->rawFormat.size.width, data->rawFormat.size.height
    );

    return TRUE;
}

/**
 * @return TRUE if requested screenshot area intersects with a screen
 */
static gboolean checkScreen(int index, GdkRectangle requestedArea) {
    if (index >= screenSpace.screenCount) {
        DEBUG_SCREENCAST("!!! Wrong index for screen %i >= %i\n",
                         index, screenSpace.screenCount);
        return FALSE;
    }

    struct ScreenProps * screen = &screenSpace.screens[index];

    int x1 = MAX(requestedArea.x, screen->bounds.x);
    int y1 = MAX(requestedArea.y, screen->bounds.y);

    int x2 = MIN(
            requestedArea.x + requestedArea.width,
            screen->bounds.x + screen->bounds.width
    );
    int y2 = MIN(
            requestedArea.y + requestedArea.height,
            screen->bounds.y + screen->bounds.height
    );

    screen->shouldCapture = x2 > x1 && y2 > y1;

    if (screen->shouldCapture) {  //intersects
        //in screen coords:
        GdkRectangle * captureArea = &(screen->captureArea);

        captureArea->x = x1 - screen->bounds.x;
        captureArea->y = y1 - screen->bounds.y;
        captureArea->width = x2 - x1;
        captureArea->height = y2 - y1;

        screen->captureArea.x = x1 - screen->bounds.x;
    }

    DEBUG_SCREEN(screen);
    return screen->shouldCapture;
}


static void onCoreError(
        void *data,
        uint32_t id,
        int seq,
        int res,
        const char *message
) {
    DEBUG_SCREENCAST(
            "!!! pipewire error: id %u, seq: %d, res: %d (%s): %s\n",
            id, seq, res, strerror(res), message
    );
    if (id == PW_ID_CORE) {
        fp_pw_thread_loop_lock(pw.loop);
        hasPipewireFailed = TRUE;
        fp_pw_thread_loop_signal(pw.loop, FALSE);
        fp_pw_thread_loop_unlock(pw.loop);
    }
}

static const struct pw_core_events coreEvents = {
        PW_VERSION_CORE_EVENTS,
        .error = onCoreError,
};

/**
 *
 * @param requestedArea requested screenshot area
 * @return TRUE on success
 */
static gboolean doLoop(GdkRectangle requestedArea) {
    gboolean isLoopLockTaken = FALSE;
    if (!pw.loop && !sessionClosed) {
        pw.loop = fp_pw_thread_loop_new("AWT Pipewire Thread", NULL);

        if (!pw.loop) {
            DEBUG_SCREENCAST("!!! Could not create a loop\n", NULL);
            doCleanup();
            return FALSE;
        }

        pw.context = fp_pw_context_new(
                fp_pw_thread_loop_get_loop(pw.loop),
                NULL,
                0
        );

        if (!pw.context) {
            DEBUG_SCREENCAST("!!! Could not create a pipewire context\n", NULL);
            doCleanup();
            return FALSE;
        }

        if (fp_pw_thread_loop_start(pw.loop) != 0) {
            DEBUG_SCREENCAST("!!! Could not start pipewire thread loop\n", NULL);
            doCleanup();
            return FALSE;
        }

        fp_pw_thread_loop_lock(pw.loop);
        isLoopLockTaken = TRUE;

        pw.core = fp_pw_context_connect_fd(
                pw.context,
                pw.pwFd,
                NULL,
                0
        );

        if (!pw.core) {
            DEBUG_SCREENCAST("!!! Could not create pipewire core\n", NULL);
            goto fail;
        }

        pw_core_add_listener(pw.core, &pw.coreListener, &coreEvents, NULL);
    }

    for (int i = 0; i < screenSpace.screenCount; ++i) {
        struct ScreenProps *screen = &screenSpace.screens[i];
        if (!screen->data && !sessionClosed) {
            struct PwStreamData *data =
                    (struct PwStreamData*) malloc(sizeof (struct PwStreamData));
            if (!data) {
                ERR("failed to allocate memory\n");
                goto fail;
            }

            memset(data, 0, sizeof (struct PwStreamData));

            screen->data = data;
        }

        DEBUG_SCREEN_PREFIX(screen, "@@@ adding screen %i\n", i);
        if (checkScreen(i, requestedArea)) {
            if (!connectStream(i)){
                goto fail;
            }
        }
        DEBUG_SCREEN_PREFIX(screen, "@@@ screen processed %i\n", i);
    }

    if (isLoopLockTaken) {
        fp_pw_thread_loop_unlock(pw.loop);
    }

    return TRUE;

    fail:
        if (isLoopLockTaken) {
            fp_pw_thread_loop_unlock(pw.loop);
        }
        doCleanup();
        return FALSE;
}

static gboolean isAllDataReady() {
    for (int i = 0; i < screenSpace.screenCount; ++i) {
        if (!screenSpace.screens[i].shouldCapture) {
            continue;
        }
        if (!screenSpace.screens[i].captureDataReady ) {
            return FALSE;
        }
    }
    return TRUE;
}


static void *pipewire_libhandle = NULL;
//glib_version_2_68 false for gtk2, as it comes from gtk3_interface.c

extern gboolean glib_version_2_68;

#define LOAD_SYMBOL(fp_name, name) do {                             \
    (fp_name) = dlsym(pipewire_libhandle, name);                    \
    if (!(fp_name)) {                                               \
       debug_screencast("!!! %s:%i error loading dl_symbol %s\n",   \
                        __func__, __LINE__, name);                  \
       goto fail;                                                   \
    }                                                               \
} while(0);

static gboolean loadSymbols() {
    if (!glib_version_2_68) {
        DEBUG_SCREENCAST("glib version 2.68+ required\n", NULL);
        return FALSE;
    }

    pipewire_libhandle = dlopen(VERSIONED_JNI_LIB_NAME("pipewire-0.3", "0"),
            RTLD_LAZY | RTLD_LOCAL);

    if (!pipewire_libhandle) {
        DEBUG_SCREENCAST("could not load pipewire library\n", NULL);
        return FALSE;
    }

    LOAD_SYMBOL(fp_pw_stream_dequeue_buffer, "pw_stream_dequeue_buffer");
    LOAD_SYMBOL(fp_pw_stream_state_as_string, "pw_stream_state_as_string");
    LOAD_SYMBOL(fp_pw_stream_queue_buffer, "pw_stream_queue_buffer");
    LOAD_SYMBOL(fp_pw_stream_set_active, "pw_stream_set_active");
    LOAD_SYMBOL(fp_pw_stream_connect, "pw_stream_connect");
    LOAD_SYMBOL(fp_pw_stream_new, "pw_stream_new");
    LOAD_SYMBOL(fp_pw_stream_add_listener, "pw_stream_add_listener");
    LOAD_SYMBOL(fp_pw_stream_disconnect, "pw_stream_disconnect");
    LOAD_SYMBOL(fp_pw_stream_destroy, "pw_stream_destroy");
    LOAD_SYMBOL(fp_pw_init, "pw_init");
    LOAD_SYMBOL(fp_pw_deinit, "pw_deinit");
    LOAD_SYMBOL(fp_pw_context_connect_fd, "pw_context_connect_fd");
    LOAD_SYMBOL(fp_pw_core_disconnect, "pw_core_disconnect");
    LOAD_SYMBOL(fp_pw_context_new, "pw_context_new");
    LOAD_SYMBOL(fp_pw_thread_loop_new, "pw_thread_loop_new");
    LOAD_SYMBOL(fp_pw_thread_loop_get_loop, "pw_thread_loop_get_loop");
    LOAD_SYMBOL(fp_pw_thread_loop_signal, "pw_thread_loop_signal");
    LOAD_SYMBOL(fp_pw_thread_loop_wait, "pw_thread_loop_wait");
    LOAD_SYMBOL(fp_pw_thread_loop_accept, "pw_thread_loop_accept");
    LOAD_SYMBOL(fp_pw_thread_loop_start, "pw_thread_loop_start");
    LOAD_SYMBOL(fp_pw_thread_loop_stop, "pw_thread_loop_stop");
    LOAD_SYMBOL(fp_pw_thread_loop_destroy, "pw_thread_loop_destroy");
    LOAD_SYMBOL(fp_pw_thread_loop_lock, "pw_thread_loop_lock");
    LOAD_SYMBOL(fp_pw_thread_loop_unlock, "pw_thread_loop_unlock");
    LOAD_SYMBOL(fp_pw_properties_new, "pw_properties_new");

    return TRUE;

    fail:
        dlclose(pipewire_libhandle);
        pipewire_libhandle = NULL;
    return FALSE;
}

void storeRestoreToken(const gchar* oldToken, const gchar* newToken) {

    JNIEnv* env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);
    DEBUG_SCREENCAST("saving token, old: |%s| > new: |%s|\n", oldToken, newToken);
    if (env) {
        jstring jOldToken = NULL;
        if (oldToken) {
            jOldToken = (*env)->NewStringUTF(env, oldToken);
            EXCEPTION_CHECK_DESCRIBE();
            if (!jOldToken) {
                return;
            }
        }
        jstring jNewToken = (*env)->NewStringUTF(env, newToken);
        EXCEPTION_CHECK_DESCRIBE();
        if (!jNewToken) {
            (*env)->DeleteLocalRef(env, jOldToken);
            return;
        }

        jintArray allowedBounds = NULL;
        if (screenSpace.screenCount > 0) {
            allowedBounds = (*env)->NewIntArray(env, screenSpace.screenCount*4);
            EXCEPTION_CHECK_DESCRIBE();
            if (!allowedBounds) {
                return;
            }
            jint* elements = (*env)->GetIntArrayElements(env, allowedBounds, NULL);
            EXCEPTION_CHECK_DESCRIBE();
            if (!elements) {
                return;
            }

            for (int i = 0; i < screenSpace.screenCount; ++i) {
                GdkRectangle bounds = screenSpace.screens[i].bounds;
                elements[4 * i] = bounds.x;
                elements[4 * i + 1] = bounds.y;
                elements[4 * i + 2] = bounds.width;
                elements[4 * i + 3] = bounds.height;
            }

            (*env)->ReleaseIntArrayElements(env, allowedBounds, elements, 0);

            (*env)->CallStaticVoidMethod(env, tokenStorageClass,
                                         storeTokenMethodID,
                                         jOldToken, jNewToken,
                                         allowedBounds);
            EXCEPTION_CHECK_DESCRIBE();
        }
        (*env)->DeleteLocalRef(env, jOldToken);
        (*env)->DeleteLocalRef(env, jNewToken);
    } else {
        DEBUG_SCREENCAST("!!! Could not get env\n", NULL);
    }
}

/*
 * Class:     sun_awt_UNIXToolkit
 * Method:    load_gtk
 * Signature: (IZ)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_screencast_ScreencastHelper_loadPipewire(
        JNIEnv *env, jclass cls, jboolean screencastDebug
) {
    DEBUG_SCREENCAST_ENABLED = screencastDebug;

    if (!loadSymbols()) {
        return JNI_FALSE;
    }

    tokenStorageClass = (*env)->FindClass(env, "sun/awt/screencast/TokenStorage");
    if (!tokenStorageClass) {
        return JNI_FALSE;
    }

    tokenStorageClass = (*env)->NewGlobalRef(env, tokenStorageClass);

    if (tokenStorageClass) {
        storeTokenMethodID = (*env)->GetStaticMethodID(
                env,
                tokenStorageClass,
                "storeTokenFromNative",
                "(Ljava/lang/String;Ljava/lang/String;[I)V"
                );
        if (!storeTokenMethodID) {
            return JNI_FALSE;
        }
    } else {
        DEBUG_SCREENCAST("!!! @@@ tokenStorageClass %p\n",
                         tokenStorageClass);
        return JNI_FALSE;
    }

    activeSessionToken = gtk->g_string_new("");

    gboolean usable = initXdgDesktopPortal();
    portalScreenCastCleanup();
    return usable;
}

static void releaseToken(JNIEnv *env, jstring jtoken, const gchar *token) {
    if (token) {
        (*env)->ReleaseStringUTFChars(env, jtoken, token);
    }
}

static void arrayToRectangles(JNIEnv *env,
                             jintArray boundsArray,
                             jint boundsLen,
                             GdkRectangle *out
) {
    if (!boundsArray) {
        return;
    }

    jint * body = (*env)->GetIntArrayElements(env, boundsArray, 0);
    EXCEPTION_CHECK_DESCRIBE();
    if (!body) {
        return;
    }

    for (int i = 0; i < boundsLen; i += 4) {
        GdkRectangle screenBounds = {
                body[i], body[i + 1],
                body[i + 2], body[i + 3]
        };
        out[i / 4] = screenBounds;
    }

    (*env)->ReleaseIntArrayElements(env, boundsArray, body, 0);
}

static int makeScreencast(
        const gchar *token,
        GdkRectangle *requestedArea,
        GdkRectangle *affectedScreenBounds,
        gint affectedBoundsLength
) {
    if (!initScreencast(token, affectedScreenBounds, affectedBoundsLength)) {
        return pw.pwFd;
    }

    if (!doLoop(*requestedArea)) {
        return RESULT_ERROR;
    }

    while (!isAllDataReady()) {
        fp_pw_thread_loop_lock(pw.loop);
        fp_pw_thread_loop_wait(pw.loop);
        fp_pw_thread_loop_unlock(pw.loop);
        if (hasPipewireFailed) {
            doCleanup();
            return RESULT_ERROR;
        }
    }

    return RESULT_OK;
}

/*
 * Class:     sun_awt_screencast_ScreencastHelper
 * Method:    closeSession
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_screencast_ScreencastHelper_closeSession(JNIEnv *env, jclass cls) {
    DEBUG_SCREENCAST("closing screencast session\n\n", NULL);
    doCleanup();
}

/*
 * Class:     sun_awt_screencast_ScreencastHelper
 * Method:    getRGBPixelsImpl
 * Signature: (IIII[I[ILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_screencast_ScreencastHelper_getRGBPixelsImpl(
        JNIEnv *env,
        jclass cls,
        jint jx,
        jint jy,
        jint jwidth,
        jint jheight,
        jintArray pixelArray,
        jintArray affectedScreensBoundsArray,
        jstring jtoken
) {
    jsize boundsLen = 0;
    gint affectedBoundsLength = 0;
    if (affectedScreensBoundsArray) {
        boundsLen = (*env)->GetArrayLength(env, affectedScreensBoundsArray);
        EXCEPTION_CHECK_DESCRIBE();
        if (boundsLen % 4 != 0) {
            DEBUG_SCREENCAST("incorrect array length\n", NULL);
            return RESULT_ERROR;
        }
        affectedBoundsLength = boundsLen / 4;
    }

    GdkRectangle affectedScreenBounds[affectedBoundsLength];
    arrayToRectangles(env,
                     affectedScreensBoundsArray,
                     boundsLen,
                     (GdkRectangle *) &affectedScreenBounds);

    GdkRectangle requestedArea = { jx, jy, jwidth, jheight};

    const gchar *token = jtoken
                         ? (*env)->GetStringUTFChars(env, jtoken, NULL)
                         : NULL;

    DEBUG_SCREENCAST(
            "taking screenshot at \n\tx: %5i y %5i w %5i h %5i with token |%s|\n",
            jx, jy, jwidth, jheight, token
    );

    int attemptResult = makeScreencast(
        token, &requestedArea, affectedScreenBounds, affectedBoundsLength);

    if (attemptResult) {
        if (attemptResult == RESULT_DENIED) {
            releaseToken(env, jtoken, token);
            return attemptResult;
        }
        DEBUG_SCREENCAST("Screencast attempt failed with %i, re-trying...\n",
                         attemptResult);
        attemptResult = makeScreencast(
            token, &requestedArea, affectedScreenBounds, affectedBoundsLength);
        if (attemptResult) {
            releaseToken(env, jtoken, token);
            return attemptResult;
        }
    }

    DEBUG_SCREENCAST("\nall data ready\n", NULL);

    for (int i = 0; i < screenSpace.screenCount; ++i) {
        struct ScreenProps * screenProps = &screenSpace.screens[i];

        if (screenProps->shouldCapture) {
            GdkRectangle bounds = screenProps->bounds;
            GdkRectangle captureArea  = screenProps->captureArea;
            DEBUG_SCREEN_PREFIX(screenProps,
                                "@@@ copying screen data %i, captureData %p\n"
                                "\t||\tx %5i y %5i w %5i h %5i %s\n"
                                "\t||\tx %5i y %5i w %5i h %5i %s\n"
                                "\t||\tx %5i y %5i w %5i h %5i %s\n\n",
                                i, screenProps->captureDataPixbuf,
                                requestedArea.x, requestedArea.y,
                                requestedArea.width, requestedArea.height,
                                "requested area",

                                bounds.x, bounds.y,
                                bounds.width, bounds.height,
                                "screen bound",

                                captureArea.x, captureArea.y,
                                captureArea.width, captureArea.height,
                                "in-screen coords capture area"
            );

            if (screenProps->captureDataPixbuf) {
                for (int y = 0; y < captureArea.height; y++) {
                    jsize preY = (requestedArea.y > screenProps->bounds.y)
                            ? 0
                            : screenProps->bounds.y - requestedArea.y;
                    jsize preX = (requestedArea.x > screenProps->bounds.x)
                            ? 0
                            : screenProps->bounds.x - requestedArea.x;
                    jsize start = jwidth * (preY + y) + preX;

                    jsize len = captureArea.width;

                    (*env)->SetIntArrayRegion(
                            env, pixelArray,
                            start, len,
                            ((jint *) gtk->gdk_pixbuf_get_pixels(
                                    screenProps->captureDataPixbuf
                            ))
                            + (captureArea.width * y)
                    );
                }
            }

            if (screenProps->captureDataPixbuf) {
                gtk->g_object_unref(screenProps->captureDataPixbuf);
                screenProps->captureDataPixbuf = NULL;
            }
            screenProps->shouldCapture = FALSE;

            fp_pw_thread_loop_lock(pw.loop);
            fp_pw_stream_set_active(screenProps->data->stream, FALSE);
            fp_pw_thread_loop_unlock(pw.loop);

            screenProps->captureDataReady = FALSE;
        }
    }

    releaseToken(env, jtoken, token);
    return 0;
}
