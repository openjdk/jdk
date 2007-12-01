/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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


#include "Utilities.h"


int UTIL_IsBigEndianPlatform() {
#ifdef _LITTLE_ENDIAN
    return 0;
#else
    return 1;
#endif
}

void ThrowJavaMessageException(JNIEnv *e, const char *exClass, const char *msg) {
    jclass newExcCls;

    ERROR1("throw exception: %s\n", msg);
    newExcCls = (*e)->FindClass(e, exClass);
    if (newExcCls == 0) {
        /* Unable to find the new exception class, give up. */
        ERROR0("ThrowJavaMessageException unable to find class!\n");
        return;
    }
    (*e)->ThrowNew(e, newExcCls, msg);
}
