/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef HPROF_TRACKER_H
#define HPROF_TRACKER_H

/* The internal qualified classname */

#define OBJECT_CLASS_SIG        "Ljava/lang/Object;"
#define OBJECT_INIT_NAME        "<init>"
#define OBJECT_INIT_SIG         "()V"

#define TRACKER_PACKAGE         "com/sun/demo/jvmti/hprof"
#define TRACKER_CLASS_NAME      TRACKER_PACKAGE "/Tracker"
#define TRACKER_CLASS_SIG       "L" TRACKER_CLASS_NAME ";"

#define TRACKER_NEWARRAY_NAME        "NewArray"
#define TRACKER_NEWARRAY_SIG         "(Ljava/lang/Object;)V"
#define TRACKER_NEWARRAY_NATIVE_NAME "nativeNewArray"
#define TRACKER_NEWARRAY_NATIVE_SIG  "(Ljava/lang/Object;Ljava/lang/Object;)V"

#define TRACKER_OBJECT_INIT_NAME        "ObjectInit"
#define TRACKER_OBJECT_INIT_SIG         "(Ljava/lang/Object;)V"
#define TRACKER_OBJECT_INIT_NATIVE_NAME "nativeObjectInit"
#define TRACKER_OBJECT_INIT_NATIVE_SIG  "(Ljava/lang/Object;Ljava/lang/Object;)V"

#define TRACKER_CALL_NAME               "CallSite"
#define TRACKER_CALL_SIG                "(II)V"
#define TRACKER_CALL_NATIVE_NAME        "nativeCallSite"
#define TRACKER_CALL_NATIVE_SIG         "(Ljava/lang/Object;II)V"


#define TRACKER_RETURN_NAME             "ReturnSite"
#define TRACKER_RETURN_SIG              "(II)V"
#define TRACKER_RETURN_NATIVE_NAME      "nativeReturnSite"
#define TRACKER_RETURN_NATIVE_SIG       "(Ljava/lang/Object;II)V"

#define TRACKER_ENGAGED_NAME               "engaged"
#define TRACKER_ENGAGED_SIG                "I"

void     tracker_setup_class(void);
void     tracker_setup_methods(JNIEnv *env);
void     tracker_engage(JNIEnv *env);
void     tracker_disengage(JNIEnv *env);
jboolean tracker_method(jmethodID method);

#endif
