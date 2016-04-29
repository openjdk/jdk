/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include <dlfcn.h>
#include <stdlib.h>
#include "jvm_md.h"
#include "gtk_interface.h"

GtkApi* gtk2_load(JNIEnv *env, const char* lib_name);
GtkApi* gtk3_load(JNIEnv *env, const char* lib_name);

gboolean gtk2_check(const char* lib_name, int flags);
gboolean gtk3_check(const char* lib_name, int flags);

GtkApi *gtk;

typedef struct {
    GtkVersion version;
    const char* name;
    const char* vname;
    GtkApi* (*load)(JNIEnv *env, const char* lib_name);
    gboolean (*check)(const char* lib_name, int flags);
} GtkLib;

static GtkLib libs[] = {
    {
        GTK_2,
        JNI_LIB_NAME("gtk-x11-2.0"),
        VERSIONED_JNI_LIB_NAME("gtk-x11-2.0", "0"),
        &gtk2_load,
        &gtk2_check
    },
    {
        GTK_3,
        JNI_LIB_NAME("gtk-3"),
        VERSIONED_JNI_LIB_NAME("gtk-3", "0"),
        &gtk3_load,
        &gtk3_check
    },
    {
        0,
        NULL,
        NULL,
        NULL,
        NULL
    }
};

static GtkLib* get_loaded() {
    GtkLib* lib = libs;
    while(!gtk && lib->version) {
        if (lib->check(lib->vname, RTLD_NOLOAD)) {
            return lib;
        }
        if (lib->check(lib->name, RTLD_NOLOAD)) {
            return lib;
        }
        lib++;
    }
    return NULL;
}

gboolean gtk_load(JNIEnv *env, GtkVersion version, gboolean verbose) {
    if (gtk == NULL) {
        GtkLib* lib = get_loaded();
        if (lib) {
            if (version != GTK_ANY && lib->version != version) {
                if (verbose) {
                    fprintf(stderr, "WARNING: Cannot load GTK%d library: \
                         GTK%d has already been loaded\n", version, lib->version);
                }
                return FALSE;
            }
            if (verbose) {
                fprintf(stderr, "Looking for GTK%d library...\n", version);
            }
            gtk = lib->load(env, lib->vname);
            if (!gtk) {
                gtk = lib->load(env, lib->name);
            }
        } else {
            lib = libs;
            while (!gtk && lib->version) {
                if (version == GTK_ANY || lib->version == version) {
                    if (verbose) {
                        fprintf(stderr, "Looking for GTK%d library...\n",
                                                                  lib->version);
                    }
                    gtk = lib->load(env, lib->vname);
                    if (!gtk) {
                        gtk = lib->load(env, lib->name);
                    }
                    if (verbose && !gtk) {
                        fprintf(stderr, "Not found.\n");
                    }
                }
                lib++;
            }
            lib--;
        }
        if (verbose) {
            if (gtk) {
                fprintf(stderr, "GTK%d library loaded.\n", lib->version);
            } else {
                fprintf(stderr, "Failed to load GTK library.\n");
            }
        }
    }
    return gtk != NULL;
}

static gboolean check_version(GtkVersion version, int flags) {
    GtkLib* lib = libs;
    while (lib->version) {
        if (version == GTK_ANY || lib->version == version) {
            if (lib->check(lib->vname, flags)) {
                return TRUE;
            }
            if (lib->check(lib->name, flags)) {
                return TRUE;
            }
        }
        lib++;
    }
    return FALSE;
}

gboolean gtk_check_version(GtkVersion version) {
    if (gtk) {
        return TRUE;
    }
    if (check_version(version, RTLD_NOLOAD)) {
        return TRUE;
    }
    return check_version(version, RTLD_LAZY | RTLD_LOCAL);
}

