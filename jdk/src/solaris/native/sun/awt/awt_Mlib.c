/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <sys/utsname.h>
#include <sys/types.h>
#include <errno.h>
#include <dlfcn.h>
#include "jni.h"
#include <jni_util.h>
#include "jvm_md.h"
#include "awt_Mlib.h"
#include "java_awt_image_BufferedImage.h"

static void start_timer(int numsec);
static void stop_timer(int numsec, int ntimes);

/*
 * This is called by awt_ImagingLib.initLib() to figure out if we
 * can use the VIS version of medialib
 */
mlib_status awt_getImagingLib(JNIEnv *env, mlibFnS_t *sMlibFns,
                              mlibSysFnS_t *sMlibSysFns) {
    int status;
    jstring jstr = NULL;
    mlibFnS_t *mptr;
    void *(*vPtr)();
    int (*intPtr)();
    mlib_status (*fPtr)();
    int i;
    void *handle = NULL;
    mlibSysFnS_t tempSysFns;
    static int s_timeIt = 0;
    static int s_verbose = 1;
    mlib_status ret = MLIB_SUCCESS;
    struct utsname name;

    /*
     * Find out the machine name. If it is an SUN ultra, we
     * can use the vis library
     */
    if ((uname(&name) >= 0) && (getenv("NO_VIS") == NULL) &&
        (strncmp(name.machine, "sun4u" , 5) == 0) ||
        ((strncmp(name.machine, "sun4v" , 5) == 0) &&
         (getenv("USE_VIS_ON_SUN4V") != NULL)))
    {
        handle = dlopen(JNI_LIB_NAME("mlib_image_v"), RTLD_LAZY);
    }

    if (handle == NULL) {
        handle = dlopen(JNI_LIB_NAME("mlib_image"), RTLD_LAZY);
    }

    if (handle == NULL) {
        if (s_timeIt || s_verbose) {
            printf ("error in dlopen: %s", dlerror());
        }
        return MLIB_FAILURE;
    }

    /* So, if we are here, then either vis or generic version of
     * medialib library was sucessfuly loaded.
     * Let's try to initialize handlers...
     */
    if ((tempSysFns.createFP = (MlibCreateFP_t)dlsym(handle,
                                       "j2d_mlib_ImageCreate")) == NULL) {
        if (s_timeIt) {
            printf ("error in dlsym: %s", dlerror());
        }
        ret = MLIB_FAILURE;
    }

    if (ret == MLIB_SUCCESS) {
        if ((tempSysFns.createStructFP = (MlibCreateStructFP_t)dlsym(handle,
                                          "j2d_mlib_ImageCreateStruct")) == NULL) {
            if (s_timeIt) {
                printf ("error in dlsym: %s", dlerror());
            }
            ret = MLIB_FAILURE;
        }
    }

    if (ret == MLIB_SUCCESS) {
        if ((tempSysFns.deleteImageFP = (MlibDeleteFP_t)dlsym(handle,
                                                 "j2d_mlib_ImageDelete")) == NULL) {
            if (s_timeIt) {
                printf ("error in dlsym: %s", dlerror());
            }
            ret = MLIB_FAILURE;
        }
    }

    /* Set the system functions */
    if (ret == MLIB_SUCCESS) {
        *sMlibSysFns = tempSysFns;
    }

    /* Loop through all of the fns and load them from the next library */
    mptr = sMlibFns;
    i = 0;
    while ((ret == MLIB_SUCCESS) && (mptr[i].fname != NULL)) {
        fPtr = (mlib_status (*)())dlsym(handle, mptr[i].fname);
        if (fPtr != NULL) {
            mptr[i].fptr = fPtr;
        } else {
            ret = MLIB_FAILURE;
        }
        i++;
    }
    if (ret != MLIB_SUCCESS) {
        dlclose(handle);
    }
    return ret;
}

mlib_start_timer awt_setMlibStartTimer() {
    return start_timer;
}

mlib_stop_timer awt_setMlibStopTimer() {
    return stop_timer;
}

void awt_getBIColorOrder(int type, int *colorOrder) {
    switch(type) {
    case java_awt_image_BufferedImage_TYPE_INT_ARGB:
    case java_awt_image_BufferedImage_TYPE_INT_ARGB_PRE:
        colorOrder[0] = 1;
        colorOrder[1] = 2;
        colorOrder[2] = 3;
        colorOrder[3] = 0;
        break;
    case java_awt_image_BufferedImage_TYPE_INT_BGR:
        colorOrder[0] = 2;
        colorOrder[1] = 1;
        colorOrder[2] = 0;
        break;
    case java_awt_image_BufferedImage_TYPE_4BYTE_ABGR:
    case java_awt_image_BufferedImage_TYPE_4BYTE_ABGR_PRE:
        colorOrder[0] = 3;
        colorOrder[1] = 2;
        colorOrder[2] = 1;
        colorOrder[3] = 0;
        break;
    case java_awt_image_BufferedImage_TYPE_3BYTE_BGR:
        colorOrder[0] = 2;
        colorOrder[1] = 1;
        colorOrder[2] = 0;
        break;
    case java_awt_image_BufferedImage_TYPE_INT_RGB:
        colorOrder[0] = 1;
        colorOrder[1] = 2;
        colorOrder[2] = 3;
        break;
    case java_awt_image_BufferedImage_TYPE_USHORT_565_RGB:
    case java_awt_image_BufferedImage_TYPE_USHORT_555_RGB:
        colorOrder[0] = 0;
        colorOrder[1] = 1;
        colorOrder[2] = 2;
        break;
    case java_awt_image_BufferedImage_TYPE_BYTE_GRAY:
    case java_awt_image_BufferedImage_TYPE_USHORT_GRAY:
    case java_awt_image_BufferedImage_TYPE_BYTE_BINARY:
    case java_awt_image_BufferedImage_TYPE_BYTE_INDEXED:
        colorOrder[0] = 0;
        break;
    }
}

/***************************************************************************
 *                          Static Functions                               *
 ***************************************************************************/

static void start_timer(int numsec)
{
    struct itimerval interval;

    interval.it_interval.tv_sec = numsec;
    interval.it_interval.tv_usec = 0;
    interval.it_value.tv_sec = numsec;
    interval.it_value.tv_usec = 0;
    setitimer(ITIMER_REAL, &interval, 0);
}


static void stop_timer(int numsec, int ntimes)
{
    struct itimerval interval;
    double sec;

    getitimer(ITIMER_REAL, &interval);
    sec = (((double) (numsec - 1)) - (double) interval.it_value.tv_sec) +
            (1000000.0 - interval.it_value.tv_usec)/1000000.0;
    sec = sec/((double) ntimes);
    printf("%f msec per update\n", sec * 1000.0);
    interval.it_interval.tv_sec = 0;
    interval.it_interval.tv_usec = 0;
    interval.it_value.tv_sec = 0;
    interval.it_value.tv_usec = 0;
    setitimer(ITIMER_PROF, &interval, 0);
}
