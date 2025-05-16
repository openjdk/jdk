/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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


#ifndef _SCREENCAST_PIPEWIRE_H
#define _SCREENCAST_PIPEWIRE_H

#include "screencast_portal.h"

#include <pipewire/stream.h>
#include <pipewire/keys.h>

#include <spa/param/video/format-utils.h>
#include <spa/debug/types.h>

void storeRestoreToken(const gchar* oldToken, const gchar* newToken);

void print_gvariant_content(gchar *caption, GVariant *response);

struct ScreenProps {
    guint32 id;
    GdkRectangle bounds;

    GdkRectangle captureArea;
    struct PwStreamData *data;

    GdkPixbuf *captureDataPixbuf;
    volatile gboolean shouldCapture;
    volatile gboolean captureDataReady;
};


#define SCREEN_SPACE_DEFAULT_ALLOCATED 2
struct ScreenSpace {
    struct ScreenProps *screens;
    int screenCount;
    int allocated;
};

#define DEBUG_SCREENCAST(FORMAT, ...) debug_screencast("%s:%i " FORMAT, \
                                        __func__, __LINE__, __VA_ARGS__);

#define DEBUG_SCREEN(SCREEN)                                            \
    DEBUG_SCREENCAST("screenId#%i\n"                                    \
    "||\tbounds         x %5i y %5i w %5i h %5i\n"                      \
    "||\tcapture area   x %5i y %5i w %5i h %5i shouldCapture %i\n\n",  \
    (SCREEN)->id,                                                       \
    (SCREEN)->bounds.x,          (SCREEN)->bounds.y,                    \
    (SCREEN)->bounds.width,      (SCREEN)->bounds.height,               \
    (SCREEN)->captureArea.x,     (SCREEN)->captureArea.y,               \
    (SCREEN)->captureArea.width, (SCREEN)->captureArea.height,          \
    (SCREEN)->shouldCapture);

#define DEBUG_SCREEN_PREFIX(SCREEN, FORMAT, ...)                        \
    DEBUG_SCREENCAST("screenId#%i[loc(%d,%d) size(%dx%d)] "FORMAT,      \
    (SCREEN)->id, (SCREEN)->bounds.x, (SCREEN)->bounds.y,               \
    (SCREEN)->bounds.width, (SCREEN)->bounds.height, __VA_ARGS__);

#define ERR(MSG) fprintf(stderr, "%s:%i " MSG, __func__, __LINE__);
#define ERR_HANDLE(ERROR) errHandle((ERROR), __func__, __LINE__);

struct PwLoopData {
    struct pw_thread_loop *loop;
    struct pw_context *context;
    struct pw_core *core;
    struct spa_hook coreListener;
    int pwFd; //negative values can also be used to store a failure reason
};

struct PwStreamData {
    struct pw_stream *stream;
    struct spa_hook streamListener;

    struct spa_video_info_raw rawFormat;
    struct ScreenProps *screenProps;

    gboolean hasFormat;
};

#endif //_SCREENCAST_PIPEWIRE_H
