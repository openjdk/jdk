/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


#include <windows.h>
#include <jni.h>

#ifdef JINVOKEREEXPORT
#define JINVOKERAPI __declspec(dllexport)
#else
#define JINVOKERAPI __declspec(dllimport)
#endif

// Create JNI_CreateJavaVM() args structures
extern "C" int  JINVOKERAPI MakeJavaVMInitArgs( void** ppArgs );

// Free JNI_CreateJavaVM() args structures
extern "C" void JINVOKERAPI FreeJavaVMInitArgs( void* pArgs );

// Static wrapper on FindClass() JNI function.
extern "C" int  JINVOKERAPI FindClass( JNIEnv* pEnv,
                                       const char* szName,
                                       jclass*     ppClass );

// Static wrapper on GetStaticMethodID() JNI function.
extern "C" int JINVOKERAPI GetStaticMethodID( JNIEnv*     pEnv,
                                              jclass      pClass,
                                              const char* szName,
                                              const char* szArgs,
                                              jmethodID*  ppMid );

// Static wrapper on NewObjectArray() JNI function.
extern "C" int JINVOKERAPI NewObjectArray( JNIEnv*       pEnv,
                                           int           nDimension,
                                           const char*   szType,
                                           jobjectArray* pArray );

// Static wrapper on CallStaticVoidMethod() JNI function.
extern "C" int JINVOKERAPI CallStaticVoidMethod( JNIEnv*   pEnv,
                                                 jclass    pClass,
                                                 jmethodID pMid,
                                                 void*     pArgs);

// Static wrapper on DestroyJavaVM() JNI function.
extern "C" int JINVOKERAPI DestroyJavaVM( JavaVM* pEnv );
