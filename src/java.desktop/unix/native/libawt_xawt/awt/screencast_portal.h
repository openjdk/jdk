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

#ifndef _SCREENCAST_PORTAL_H
#define _SCREENCAST_PORTAL_H

#include "gtk_interface.h"

#define PORTAL_TOKEN_TEMPLATE "awtPipewire%lu"
#define PORTAL_REQUEST_TEMPLATE "/org/freedesktop/portal/desktop/" \
                                "request/%s/awtPipewire%lu"

void debug_screencast(const char *__restrict fmt, ...);

int getPipewireFd(const gchar *token,
                  GdkRectangle *affectedBounds,
                  gint affectedBoundsLength);

void portalScreenCastCleanup();

gboolean initXdgDesktopPortal();

void errHandle(GError *error, const gchar *functionName, int lineNum);

struct XdgDesktopPortalApi {
    GDBusConnection *connection;
    GDBusProxy *screenCastProxy;
    gchar *senderName;
    char *screenCastSessionHandle;
};

struct DBusCallbackHelper {
    guint id;
    void *data;
    gboolean isDone;
};

typedef enum {
    RESULT_OK = 0,
    RESULT_ERROR = -1,
    RESULT_DENIED = -11,
    RESULT_OUT_OF_BOUNDS = -12,
} ScreenCastResult;

struct StartHelper {
    const gchar *token;
    ScreenCastResult result;
};

#endif //_SCREENCAST_PORTAL_H
