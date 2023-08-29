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

#include "stdlib.h"
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <pwd.h>
#include <unistd.h>
#include "screencast_pipewire.h"
#include "screencast_portal.h"


extern struct ScreenSpace screenSpace;

struct XdgDesktopPortalApi *portal = NULL;

void errHandle(
        GError *error,
        const gchar *functionName,
        int lineNum
) {
    if (error) {
        fprintf(stderr, "!!! %s:%i Error: domain %i code %i message: \"%s\"\n",
                functionName, lineNum,
                error->domain, error->code, error->message);
    }
    if (error) {
        gtk->g_error_free(error);
    }
    error = NULL;
}

gboolean validateToken(const gchar *token) {
    if (!token) {
        return FALSE;
    }

    gboolean isValid = gtk->g_uuid_string_is_valid(token);
    if (!isValid) {
        DEBUG_SCREENCAST("!!! restore token "
                         "is not a valid UUID string:\n\"%s\"\n",
                         token);
    }
    return isValid;
}

/**
 * @return TRUE on success
 */
gboolean rebuildScreenData(GVariantIter *iterStreams, gboolean isTheOnlyMon) {
    guint32 nodeID;
    GVariant* prop = NULL;

    int screenIndex = 0;

    gboolean hasFailures = FALSE;

    while (gtk->g_variant_iter_loop(
            iterStreams,
            "(u@a{sv})",
            &nodeID,
            &prop
    )) {
        DEBUG_SCREENCAST("\n==== screenId#%i\n", nodeID);

        if (screenIndex >= screenSpace.allocated) {
            screenSpace.screens = realloc(
                    screenSpace.screens,
                    ++screenSpace.allocated * sizeof(struct ScreenProps)
            );
            if (!screenSpace.screens) {
                ERR("failed to allocate memory\n");
                return FALSE;
            }
        }

        struct ScreenProps * screen = &screenSpace.screens[screenIndex];
        memset(screen, 0, sizeof(struct ScreenProps));

        screenSpace.screenCount = screenIndex + 1;

        screen->id = nodeID;

        if (
                !gtk->g_variant_lookup(
                        prop,
                        "size",
                        "(ii)",
                        &screen->bounds.width,
                        &screen->bounds.height
                )
                || (
                        !gtk->g_variant_lookup(
                                prop,
                                "position",
                                "(ii)",
                                &screen->bounds.x,
                                &screen->bounds.y
                        )
                        //Screen position is not specified in some cases
                        //(e.g. on Plasma).
                        //In this case, proceed only if there is only one screen.
                        && !isTheOnlyMon
                )
                ) {
            hasFailures = TRUE;
        }

        DEBUG_SCREENCAST("-----------------------\n", NULL);
        DEBUG_SCREEN(screen);
        DEBUG_SCREENCAST("#---------------------#\n\n", NULL);

        gtk->g_variant_unref(prop);
        screenIndex++;
    };

    if (hasFailures) {
        DEBUG_SCREENCAST("screenId#%i hasFailures\n", nodeID);
    }

    return !hasFailures;
}

/**
 * Checks screencast protocol version
 * @return FALSE if version < 4, or could not be determined
 */
gboolean checkVersion() {
    static guint32 version = 0;
    if (version == 0) {
        GError *error = NULL;
        GVariant *retVersion = gtk->g_dbus_proxy_call_sync(
                portal->screenCastProxy,
                "org.freedesktop.DBus.Properties.Get",
                gtk->g_variant_new("(ss)",
                                   "org.freedesktop.portal.ScreenCast",
                                   "version"),
                G_DBUS_CALL_FLAGS_NONE,
                -1, NULL, NULL
        );

        if (!retVersion) { //no backend on system
            DEBUG_SCREENCAST("!!! could not detect the screencast version\n",
                             NULL);
            return FALSE;
        }

        ERR_HANDLE(error);

        GVariant *varVersion = NULL;
        gtk->g_variant_get(retVersion, "(v)", &varVersion);

        if (!varVersion){
            gtk->g_variant_unref(retVersion);
            DEBUG_SCREENCAST("!!! could not get the screencast version\n",
                             NULL);
            return FALSE;
        }

        version = gtk->g_variant_get_uint32(varVersion);

        gtk->g_variant_unref(varVersion);
        gtk->g_variant_unref(retVersion);

    }

    DEBUG_SCREENCAST("ScreenCast protocol version %d\n", version);
    if (version < 4) {
        DEBUG_SCREENCAST("!!! ScreenCast protocol version %d < 4,"
                         " session restore is not available\n",
                         version);
    }

    // restore_token was added in version 4, without it,
    // user confirmation is required for every screenshot.
    return version >= 4;
}

/**
 * @return TRUE on success
 */
gboolean initXdgDesktopPortal() {
    portal = calloc(1, sizeof(*portal));

    if (!portal) {
        ERR("failed to allocate memory\n");
        return FALSE;
    }

    GError* err = NULL;

    portal->connection = gtk->g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, &err);

    if (err) {
        ERR_HANDLE(err);
        return FALSE;
    }

    const gchar *name = gtk
            ->g_dbus_connection_get_unique_name(portal->connection);
    if (!name) {
        ERR("Failed to get unique connection name\n");
        return FALSE;
    }

    GString * nameStr = gtk->g_string_new(name);
    gtk->g_string_erase(nameStr, 0, 1); //remove leading colon ":"
    gtk->g_string_replace(nameStr, ".", "_", 0);
    portal->senderName = nameStr->str;

    gtk->g_string_free(nameStr, FALSE);

    DEBUG_SCREENCAST("connection/sender name %s / %s\n",
                     name,
                     portal->senderName);

    portal->screenCastProxy = gtk->g_dbus_proxy_new_sync(
            portal->connection,
            G_DBUS_PROXY_FLAGS_NONE,
            NULL,
            "org.freedesktop.portal.Desktop",
            "/org/freedesktop/portal/desktop",
            "org.freedesktop.portal.ScreenCast",
            NULL,
            &err
    );

    if (err) {
        DEBUG_SCREENCAST("Failed to get ScreenCast portal: %s", err->message);
        ERR_HANDLE(err);
        return FALSE;
    }

    return checkVersion();
}

static void updateRequestPath(
        gchar **path,
        gchar **token
) {
    static uint64_t counter = 0;
    ++counter;

    GString *tokenStr = gtk->g_string_new(NULL);
    gtk->g_string_printf(
            tokenStr,
            PORTAL_TOKEN_TEMPLATE,
            counter
    );

    *token = tokenStr->str;
    gtk->g_string_free(tokenStr, FALSE);

    GString *pathStr = gtk->g_string_new(NULL);

    gtk->g_string_printf(
            pathStr,
            PORTAL_REQUEST_TEMPLATE,
            portal->senderName,
            counter
    );

    *path = pathStr->str;
    gtk->g_string_free(pathStr, FALSE);
}

static void updateSessionToken(
        gchar **token
) {
    static uint64_t counter = 0;
    counter++;

    GString *tokenStr = gtk->g_string_new(NULL);

    gtk->g_string_printf(
            tokenStr,
            PORTAL_TOKEN_TEMPLATE,
            counter
    );

    *token = tokenStr->str;
    gtk->g_string_free(tokenStr, FALSE);
}

static void registerScreenCastCallback(
        const char *path,
        struct DBusCallbackHelper *helper,
        GDBusSignalCallback callback
) {
    helper->id = gtk->g_dbus_connection_signal_subscribe(
            portal->connection,
            "org.freedesktop.portal.Desktop",
            "org.freedesktop.portal.Request",
            "Response",
            path,
            NULL,
            G_DBUS_SIGNAL_FLAGS_NO_MATCH_RULE,
            callback,
            helper,
            NULL
    );
}

static void unregisterScreenCastCallback(
        struct DBusCallbackHelper *helper
) {
    if (helper->id) {
        gtk->g_dbus_connection_signal_unsubscribe(
                portal->connection,
                helper->id
        );
    }
}

static void callbackScreenCastCreateSession(
        GDBusConnection *connection,
        const char *senderName,
        const char *objectPath,
        const char *interfaceName,
        const char *signalName,
        GVariant *parameters,
        void *data
) {
    struct DBusCallbackHelper *helper = data;
    uint32_t status;
    GVariant *result = NULL;

    gtk->g_variant_get(
            parameters,
            "(u@a{sv})",
            &status,
            &result
    );

    if (status != 0) {
        DEBUG_SCREENCAST("Failed to create ScreenCast: %u\n", status);
    } else {
        gtk->g_variant_lookup(result, "session_handle", "s", helper->data);
    }

    helper->isDone = TRUE;
}

gboolean portalScreenCastCreateSession() {
    GError *err = NULL;

    gchar *requestPath = NULL;
    gchar *requestToken = NULL;
    gchar *sessionToken = NULL;

    struct DBusCallbackHelper helper = {
            .id = 0,
            .data = &portal->screenCastSessionHandle
    };

    updateRequestPath(
            &requestPath,
            &requestToken
    );
    updateSessionToken(&sessionToken);

    portal->screenCastSessionHandle = NULL;

    registerScreenCastCallback(
            requestPath,
            &helper,
            callbackScreenCastCreateSession
    );

    GVariantBuilder builder;

    gtk->g_variant_builder_init(
            &builder,
            G_VARIANT_TYPE_VARDICT
    );

    gtk->g_variant_builder_add(
            &builder,
            "{sv}",
            "handle_token",
            gtk->g_variant_new_string(requestToken)
    );

    gtk->g_variant_builder_add(
            &builder,
            "{sv}",
            "session_handle_token",
            gtk->g_variant_new_string(sessionToken)
    );

    GVariant *response = gtk->g_dbus_proxy_call_sync(
            portal->screenCastProxy,
            "CreateSession",
            gtk->g_variant_new("(a{sv})", &builder),
            G_DBUS_CALL_FLAGS_NONE,
            -1,
            NULL,
            &err
    );

    if (err) {
        DEBUG_SCREENCAST("Failed to create ScreenCast session: %s\n",
                         err->message);
        ERR_HANDLE(err);
    } else {
        while (!helper.isDone) {
            gtk->g_main_context_iteration(NULL, TRUE);
        }
    }

    unregisterScreenCastCallback(&helper);
    if (response) {
        gtk->g_variant_unref(response);
    }

    free(sessionToken);
    free(requestPath);
    free(requestToken);

    return portal->screenCastSessionHandle != NULL;
}

static void callbackScreenCastSelectSources(
        GDBusConnection *connection,
        const char *senderName,
        const char *objectPath,
        const char *interfaceName,
        const char *signalName,
        GVariant *parameters,
        void *data
) {
    struct DBusCallbackHelper *helper = data;

    helper->data = (void *) 0;

    uint32_t status;
    GVariant* result = NULL;

    gtk->g_variant_get(parameters, "(u@a{sv})", &status, &result);

    if (status != 0) {
        DEBUG_SCREENCAST("Failed select sources: %u\n", status);
    } else {
        helper->data = (void *) 1;
    }

    helper->isDone = TRUE;

    if (result) {
        gtk->g_variant_unref(result);
    }
}

gboolean portalScreenCastSelectSources(const gchar *token) {
    GError* err = NULL;

    gchar *requestPath = NULL;
    gchar *requestToken = NULL;

    struct DBusCallbackHelper helper = {0};

    updateRequestPath(
            &requestPath,
            &requestToken
    );

    registerScreenCastCallback(
            requestPath,
            &helper,
            callbackScreenCastSelectSources
    );

    GVariantBuilder builder;

    gtk->g_variant_builder_init(
            &builder,
            G_VARIANT_TYPE_VARDICT
    );

    gtk->g_variant_builder_add(
            &builder,
            "{sv}", "handle_token",
            gtk->g_variant_new_string(requestToken)
    );

    gtk->g_variant_builder_add(
            &builder,
            "{sv}", "multiple",
            gtk->g_variant_new_boolean(TRUE));

    // 1: MONITOR
    // 2: WINDOW
    // 4: VIRTUAL
    gtk->g_variant_builder_add(
            &builder, "{sv}", "types",
            gtk->g_variant_new_uint32(1)
    );

    // 0: Do not persist (default)
    // 1: Permissions persist as long as the application is running
    // 2: Permissions persist until explicitly revoked
    gtk->g_variant_builder_add(
            &builder,
            "{sv}",
            "persist_mode",
            gtk->g_variant_new_uint32(2)
    );

    if (validateToken(token)) {
        gtk->g_variant_builder_add(
                &builder,
                "{sv}",
                "restore_token",
                gtk->g_variant_new_string(token)
        );
    }

    GVariant *response = gtk->g_dbus_proxy_call_sync(
            portal->screenCastProxy,
            "SelectSources",
            gtk->g_variant_new("(oa{sv})", portal->screenCastSessionHandle, &builder),
            G_DBUS_CALL_FLAGS_NONE,
            -1,
            NULL,
            &err
    );

    if (err) {
        DEBUG_SCREENCAST("Failed to call SelectSources: %s\n", err->message);
        ERR_HANDLE(err);
    } else {
        while (!helper.isDone) {
            gtk->g_main_context_iteration(NULL, TRUE);
        }
    }

    unregisterScreenCastCallback(&helper);
    if (response) {
        gtk->g_variant_unref(response);
    }

    free(requestPath);
    free(requestToken);

    return helper.data != NULL;
}

static void callbackScreenCastStart(
        GDBusConnection *connection,
        const char *senderName,
        const char *objectPath,
        const char *interfaceName,
        const char *signalName,
        GVariant *parameters,
        void *data
) {
    struct DBusCallbackHelper *helper = data;
    struct StartHelper *startHelper = helper->data;

    uint32_t status;
    GVariant* result = NULL;
    const gchar *oldToken = startHelper->token;

    gtk->g_variant_get(parameters, "(u@a{sv})", &status, &result);

    if (status != 0) {
        // Cancel pressed on the system dialog
        DEBUG_SCREENCAST("Failed to start screencast: %u\n", status);
        startHelper->result = RESULT_DENIED;
        helper->isDone = TRUE;
        return;
    }

    GVariant *streams = gtk->g_variant_lookup_value(
            result,
            "streams",
            G_VARIANT_TYPE_ARRAY
    );

    GVariantIter iter;
    gtk->g_variant_iter_init(
            &iter,
            streams
    );

    size_t count = gtk->g_variant_iter_n_children(&iter);

    DEBUG_SCREENCAST("available screen count %i\n", count);

    startHelper->result = (rebuildScreenData(&iter, count == 1))
                   ? RESULT_OK
                   : RESULT_ERROR;

    DEBUG_SCREENCAST("rebuildScreenData result |%i|\n", startHelper->result);

    if (startHelper->result == RESULT_OK) {
        GVariant *restoreTokenVar = gtk->g_variant_lookup_value(
                result,
                "restore_token",
                G_VARIANT_TYPE_STRING
        );

        if (restoreTokenVar) {
            gsize len;
            const gchar *newToken = gtk->
                    g_variant_get_string(restoreTokenVar, &len);
            DEBUG_SCREENCAST("restore_token |%s|\n", newToken);

            storeRestoreToken(oldToken, newToken);

            gtk->g_variant_unref(restoreTokenVar);
        }
    }

    helper->isDone = TRUE;

    if (streams) {
        gtk->g_variant_unref(streams);
    }
}

ScreenCastResult portalScreenCastStart(const gchar *token) {
    GError *err = NULL;

    gchar *requestPath = NULL;
    gchar *requestToken = NULL;

    struct StartHelper startHelper = { 0 };
    startHelper.token = token;

    struct DBusCallbackHelper helper = { 0 };
    helper.data = &startHelper;

    updateRequestPath(
            &requestPath,
            &requestToken
    );

    registerScreenCastCallback(
            requestPath,
            &helper,
            callbackScreenCastStart
    );

    GVariantBuilder builder;

    gtk->g_variant_builder_init(
            &builder,
            G_VARIANT_TYPE_VARDICT
    );

    gtk->g_variant_builder_add(
            &builder,
            "{sv}",
            "handle_token",
            gtk->g_variant_new_string(requestToken)
    );

    GVariant *response = gtk->g_dbus_proxy_call_sync(
            portal->screenCastProxy,
            "Start",
            gtk->g_variant_new("(osa{sv})", portal->screenCastSessionHandle, "", &builder),
            G_DBUS_CALL_FLAGS_NONE,
            -1,
            NULL,
            &err
    );

    if (err) {
        DEBUG_SCREENCAST("Failed to start session: %s\n", err->message);
        ERR_HANDLE(err);
    } else {
        while (!helper.isDone) {
            gtk->g_main_context_iteration(NULL, TRUE);
        }
    }

    unregisterScreenCastCallback(&helper);
    if (response) {
        gtk->g_variant_unref(response);
    }

    free(requestPath);
    free(requestToken);

    DEBUG_SCREENCAST("ScreenCastResult |%i|\n", startHelper.result);

    return startHelper.result;
}

int portalScreenCastOpenPipewireRemote() {
    GError* err = NULL;
    GUnixFDList* fdList = NULL;

    GVariantBuilder builder;

    gtk->g_variant_builder_init(
            &builder, G_VARIANT_TYPE_VARDICT
    );

    GVariant *response = gtk->g_dbus_proxy_call_with_unix_fd_list_sync(
            portal->screenCastProxy,
            "OpenPipeWireRemote",
            gtk->g_variant_new("(oa{sv})", portal->screenCastSessionHandle, &builder),
            G_DBUS_CALL_FLAGS_NONE,
            -1,
            NULL,
            &fdList,
            NULL,
            &err
    );

    if (err || !response) {
        DEBUG_SCREENCAST("Failed to call OpenPipeWireRemote on session: %s\n",
                         err->message);
        ERR_HANDLE(err);
        return RESULT_ERROR;
    }

    gint32 index;
    gtk->g_variant_get(
            response,
            "(h)",
            &index,
            &err
    );

    gtk->g_variant_unref(response);

    if (err) {
        DEBUG_SCREENCAST("Failed to get pipewire fd index: %s\n",
                         err->message);
        ERR_HANDLE(err);
        return RESULT_ERROR;
    }

    int fd = gtk->g_unix_fd_list_get(
            fdList,
            index,
            &err
    );

    if (fdList) {
        gtk->g_object_unref(fdList);
    }

    if (err) {
        DEBUG_SCREENCAST("Failed to get pipewire fd: %s\n", err->message);
        ERR_HANDLE(err);
        return RESULT_ERROR;
    }

    return fd;
}

void portalScreenCastCleanup() {
    if (!portal) {
        return;
    }

    if (portal->screenCastSessionHandle) {
        gtk->g_dbus_connection_call_sync(
                portal->connection,
                "org.freedesktop.portal.Desktop",
                portal->screenCastSessionHandle,
                "org.freedesktop.portal.Session",
                "Close",
                NULL,
                NULL,
                G_DBUS_CALL_FLAGS_NONE,
                -1,
                NULL,
                NULL
        );

        gtk->g_free(portal->screenCastSessionHandle);
        portal->screenCastSessionHandle = NULL;
    }

    if (portal->connection) {
        gtk->g_object_unref(portal->connection);
        portal->connection = NULL;
    }

    if (portal->screenCastProxy) {
        gtk->g_object_unref(portal->screenCastProxy);
        portal->screenCastProxy = NULL;
    }

    if (portal->senderName) {
        free(portal->senderName);
        portal->senderName = NULL;
    }

    free(portal);
    portal = NULL;
}

gboolean rectanglesEqual(GdkRectangle rect1, GdkRectangle rect2) {
    return rect1.x == rect2.x
           && rect1.y == rect2.y
           && rect1.width == rect2.width
           && rect1.height == rect2.height;
}

gboolean checkCanCaptureAllRequiredScreens(GdkRectangle *affectedBounds,
                        gint affectedBoundsLength) {


    if (affectedBoundsLength > screenSpace.screenCount) {
        DEBUG_SCREENCAST("Requested screen count is greater "
                         "than allowed with token (%i > %i)\n",
                         affectedBoundsLength, screenSpace.screenCount);
        return false;
    }


    for (int i = 0; i < affectedBoundsLength; ++i) {
        gboolean found = false;
        GdkRectangle affBounds = affectedBounds[i];
        for (int j = 0; j < screenSpace.screenCount; ++j) {
            GdkRectangle allowedBounds = screenSpace.screens[j].bounds;

            if (rectanglesEqual(allowedBounds, affBounds)) {
                DEBUG_SCREENCAST("Found allowed screen bounds in affected "
                                 "screen bounds %i %i %i %i\n",
                                 affBounds.x, affBounds.y,
                                 affBounds.width, affBounds.height);
                found = true;
                break;
            }
        }
        if (!found) {
            DEBUG_SCREENCAST("Could not find required screen %i %i %i %i "
                             "in allowed bounds\n",
                             affBounds.x, affBounds.y,
                             affBounds.width, affBounds.height);
            return false;
        }
    }

    return true;
}


int getPipewireFd(const gchar *token,
                  GdkRectangle *affectedBounds,
                  gint affectedBoundsLength) {
    if (!portalScreenCastCreateSession())  {
        DEBUG_SCREENCAST("Failed to create ScreenCast session\n", NULL);
        return RESULT_ERROR;
    }

    if (!portalScreenCastSelectSources(token)) {
        DEBUG_SCREENCAST("Failed to select sources\n", NULL);
        return RESULT_ERROR;
    }

    ScreenCastResult startResult = portalScreenCastStart(token);
    DEBUG_SCREENCAST("portalScreenCastStart result |%i|\n", startResult);
    if (startResult != RESULT_OK) {
        DEBUG_SCREENCAST("Failed to start\n", NULL);
        return startResult;
    } else {
        if (!checkCanCaptureAllRequiredScreens(affectedBounds,
                                               affectedBoundsLength)) {
            DEBUG_SCREENCAST("The location of the screens has changed, "
                             "the capture area is outside the allowed "
                             "area.\n", NULL)
            return RESULT_OUT_OF_BOUNDS;
        }
    }

    DEBUG_SCREENCAST("--- portalScreenCastStart\n", NULL);

    int pipewireFd = portalScreenCastOpenPipewireRemote();
    if (pipewireFd < 0) {
        DEBUG_SCREENCAST("!!! Failed to get pipewire fd\n", NULL);
    }

    DEBUG_SCREENCAST("pwFd %i\n", pipewireFd);
    return pipewireFd;
}
