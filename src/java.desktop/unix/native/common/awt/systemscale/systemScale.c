/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "systemScale.h"
#include "jni.h"
#include "jni_util.h"
#include "jvm_md.h"
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef void* g_settings_schema_source_get_default();
typedef void* g_settings_schema_source_ref(void *);
typedef void g_settings_schema_source_unref(void *);
typedef void* g_settings_schema_source_lookup(void *, char *, int);
typedef int g_settings_schema_has_key(void *, char *);
typedef void* g_settings_new_full(void *, void *, char *);
typedef void* g_settings_get_value(void *, char *);
typedef int g_variant_is_of_type(void *, char *);
typedef unsigned long g_variant_n_children(void *);
typedef void* g_variant_get_child_value(void *, unsigned long);
typedef void  g_variant_unref(void *);
typedef char*  g_variant_get_string(void *, unsigned long *);
typedef int  g_variant_get_int32(void *);
typedef double  g_variant_get_double(void *);

static g_settings_schema_has_key* fp_g_settings_schema_has_key;
static g_settings_new_full* fp_g_settings_new_full;
static g_settings_get_value* fp_g_settings_get_value;
static g_variant_is_of_type* fp_g_variant_is_of_type;
static g_variant_n_children* fp_g_variant_n_children;
static g_variant_get_child_value* fp_g_variant_get_child_value;
static g_variant_get_string* fp_g_variant_get_string;
static g_variant_get_int32* fp_g_variant_get_int32;
static g_variant_get_double* fp_g_variant_get_double;
static g_variant_unref* fp_g_variant_unref;

static void* get_schema_value(char *name, char *key) {
    static void *lib_handle;
    static int initialized = 0;
    static void * default_schema;
    static g_settings_schema_source_lookup* schema_lookup;
    void *schema = NULL, *fp = NULL;
    if (!initialized) {
        initialized = 1;
        lib_handle = dlopen(JNI_LIB_NAME("gio-2.0"), RTLD_GLOBAL | RTLD_LAZY);
        if (!lib_handle) {
            CHECK_NULL_RETURN(lib_handle =
                          dlopen(VERSIONED_JNI_LIB_NAME("gio-2.0", "0"),
                                                RTLD_GLOBAL | RTLD_LAZY), NULL);
        }
        CHECK_NULL_RETURN(fp_g_settings_schema_has_key =
                          (g_settings_schema_has_key*)
                          dlsym(lib_handle, "g_settings_schema_has_key"), NULL);
        CHECK_NULL_RETURN(fp_g_settings_new_full =
                          (g_settings_new_full*)
                          dlsym(lib_handle, "g_settings_new_full"), NULL);
        CHECK_NULL_RETURN(fp_g_settings_get_value =
                          (g_settings_get_value*)
                          dlsym(lib_handle, "g_settings_get_value"), NULL);
        CHECK_NULL_RETURN(fp_g_variant_is_of_type =
                          (g_variant_is_of_type*)
                          dlsym(lib_handle, "g_variant_is_of_type"), NULL);
        CHECK_NULL_RETURN(fp_g_variant_n_children =
                          (g_variant_n_children*)
                          dlsym(lib_handle, "g_variant_n_children"), NULL);
        CHECK_NULL_RETURN(fp_g_variant_get_child_value =
                          (g_variant_get_child_value*)
                          dlsym(lib_handle, "g_variant_get_child_value"), NULL);
        CHECK_NULL_RETURN(fp_g_variant_get_string =
                          (g_variant_get_string*)
                          dlsym(lib_handle, "g_variant_get_string"), NULL);
        CHECK_NULL_RETURN(fp_g_variant_get_int32 =
                          (g_variant_get_int32*)
                          dlsym(lib_handle, "g_variant_get_int32"), NULL);
        CHECK_NULL_RETURN(fp_g_variant_get_double =
                          (g_variant_get_double*)
                          dlsym(lib_handle, "g_variant_get_double"), NULL);
        CHECK_NULL_RETURN(fp_g_variant_unref =
                          (g_variant_unref*)
                          dlsym(lib_handle, "g_variant_unref"), NULL);

        fp = dlsym(lib_handle, "g_settings_schema_source_get_default");
        if (fp) {
            default_schema = ((g_settings_schema_source_get_default*)fp)();
        }
        if (default_schema) {
            fp = dlsym(lib_handle, "g_settings_schema_source_ref");
            if (fp) {
                ((g_settings_schema_source_ref*)fp)(default_schema);
            }
        }
        schema_lookup = (g_settings_schema_source_lookup*)
                           dlsym(lib_handle, "g_settings_schema_source_lookup");
    }

    if (!default_schema || !schema_lookup) {
        return NULL;
    }

    schema = schema_lookup(default_schema, name, 1);
    if (schema) {
        if (fp_g_settings_schema_has_key(schema, key)) {
            void *settings = fp_g_settings_new_full(schema, NULL, NULL);
            if (settings) {
                return fp_g_settings_get_value(settings, key);
            }
        }
    }
    return NULL;
}


static double getDesktopScale(char *output_name) {
    double result = -1;
    if(output_name) {
        void *value = get_schema_value("com.ubuntu.user-interface",
                                                                "scale-factor");
        if (value) {
            if(fp_g_variant_is_of_type(value, "a{si}")) {
                int num = fp_g_variant_n_children(value);
                int i = 0;
                while (i < num) {
                    void *entry = fp_g_variant_get_child_value(value, i++);
                    if (entry) {
                        void *screen = fp_g_variant_get_child_value(entry, 0);
                        void *scale = fp_g_variant_get_child_value(entry, 1);
                        if (screen && scale) {
                            char *name = fp_g_variant_get_string(screen, NULL);
                            if (name && !strcmp(name, output_name)) {
                                result = fp_g_variant_get_int32(scale) / 8.;
                            }
                            fp_g_variant_unref(screen);
                            fp_g_variant_unref(scale);
                        }
                        fp_g_variant_unref(entry);
                    }
                    if (result > 0) {
                        break;
                    }
                }
            }
            fp_g_variant_unref(value);
        }
        if (result > 0) {
            value = get_schema_value("com.canonical.Unity.Interface",
                                                           "text-scale-factor");
            if (value && fp_g_variant_is_of_type(value, "d")) {
                result *= fp_g_variant_get_double(value);
                fp_g_variant_unref(value);
            }
        }
    }

    if (result <= 0) {
        void *value = get_schema_value("org.gnome.desktop.interface",
                                                         "text-scaling-factor");
        if (value && fp_g_variant_is_of_type(value, "d")) {
            result = fp_g_variant_get_double(value);
            fp_g_variant_unref(value);
        }
    }

    return result;

}

static int getScale(const char *name) {
    char *uiScale = getenv(name);
    if (uiScale != NULL) {
        double scale = strtod(uiScale, NULL);
        if (scale < 1) {
            return -1;
        }
        return (int) scale;
    }
    return -1;
}

double getNativeScaleFactor(char *output_name) {
    static int scale = -2.0;
    double native_scale = 0;
    int gdk_scale = 0;

    if (scale == -2) {
        scale = getScale("J2D_UISCALE");
    }

    if (scale > 0) {
        return scale;
    }

    native_scale = getDesktopScale(output_name);

    if (native_scale <= 0) {
        native_scale = 1;
    }

    gdk_scale = getScale("GDK_SCALE");

    return gdk_scale > 0 ? native_scale * gdk_scale : native_scale;
}
