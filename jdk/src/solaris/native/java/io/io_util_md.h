/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "jni.h"
#include "jni_util.h"

/*
 * Macros to use the right data type for file descriptors
 */
#define FD jint

/*
 * Macros to set/get fd from the java.io.FileDescriptor.  These
 * macros rely on having an appropriately defined 'this' object
 * within the scope in which they're used.
 * If GetObjectField returns null, SET_FD will stop and GET_FD
 * will simply return -1 to avoid crashing VM.
 */

#define SET_FD(this, fd, fid) \
    if ((*env)->GetObjectField(env, (this), (fid)) != NULL) \
        (*env)->SetIntField(env, (*env)->GetObjectField(env, (this), (fid)),IO_fd_fdID, (fd))

#define GET_FD(this, fid) \
    (*env)->GetObjectField(env, (this), (fid)) == NULL ? \
        -1 : (*env)->GetIntField(env, (*env)->GetObjectField(env, (this), (fid)), IO_fd_fdID)

/*
 * Macros to set/get fd when inside java.io.FileDescriptor
 */
#define THIS_FD(obj) (*env)->GetIntField(env, obj, IO_fd_fdID)

/*
 * Route the routines through HPI
 */
#define IO_Write JVM_Write
#define IO_Sync JVM_Sync
#define IO_Read JVM_Read
#define IO_Lseek JVM_Lseek
#define IO_Available JVM_Available
#define IO_SetLength JVM_SetLength

/*
 * On Solaris, the handle field is unused
 */
#define SET_HANDLE(fd) return (jlong)-1

/*
 * IO helper function(s)
 */
void fileClose(JNIEnv *env, jobject this, jfieldID fid);
