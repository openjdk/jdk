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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


#ifndef JAVA_CRW_DEMO_H
#define JAVA_CRW_DEMO_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/* This callback is used to notify the caller of a fatal error. */

typedef void (*FatalErrorHandler)(const char*message, const char*file, int line);

/* This callback is used to return the method information for a class.
 *   Since the information was already read here, it was useful to
 *   return it here, with no JVMTI phase restrictions.
 *   If the class file does represent a "class" and it has methods, then
 *   this callback will be called with the class number and pointers to
 *   the array of names, array of signatures, and the count of methods.
 */

typedef void (*MethodNumberRegister)(unsigned, const char**, const char**, int);

/* Class file reader/writer interface. Basic input is a classfile image
 *     and details about what to inject. The output is a new classfile image
 *     that was allocated with malloc(), and should be freed by the caller.
 */

/* Names of external symbols to look for. These are the names that we
 *   try and lookup in the shared library. On Windows 2000, the naming
 *   convention is to prefix a "_" and suffix a "@N" where N is 4 times
 *   the number or arguments supplied.It has 19 args, so 76 = 19*4.
 *   On Windows 2003, Linux, and Solaris, the first name will be
 *   found, on Windows 2000 a second try should find the second name.
 *
 *   WARNING: If You change the JavaCrwDemo typedef, you MUST change
 *            multiple things in this file, including this name.
 */

#define JAVA_CRW_DEMO_SYMBOLS { "java_crw_demo", "_java_crw_demo@76" }

/* Typedef needed for type casting in dynamic access situations. */

typedef void (JNICALL *JavaCrwDemo)(
         unsigned class_number,
         const char *name,
         const unsigned char *file_image,
         long file_len,
         int system_class,
         char* tclass_name,
         char* tclass_sig,
         char* call_name,
         char* call_sig,
         char* return_name,
         char* return_sig,
         char* obj_init_name,
         char* obj_init_sig,
         char* newarray_name,
         char* newarray_sig,
         unsigned char **pnew_file_image,
         long *pnew_file_len,
         FatalErrorHandler fatal_error_handler,
         MethodNumberRegister mnum_callback
);

/* Function export (should match typedef above) */

JNIEXPORT void JNICALL java_crw_demo(

         unsigned class_number, /* Caller assigned class number for class */

         const char *name,      /* Internal class name, e.g. java/lang/Object */
                                /*   (Do not use "java.lang.Object" format) */

         const unsigned char
           *file_image,         /* Pointer to classfile image for this class */

         long file_len,         /* Length of the classfile in bytes */

         int system_class,      /* Set to 1 if this is a system class */
                                /*   (prevents injections into empty */
                                /*   <clinit>, finalize, and <init> methods) */

         char* tclass_name,     /* Class that has methods we will call at */
                                /*   the injection sites (tclass) */

         char* tclass_sig,      /* Signature of tclass */
                                /*  (Must be "L" + tclass_name + ";") */

         char* call_name,       /* Method name in tclass to call at offset 0 */
                                /*   for every method */

         char* call_sig,        /* Signature of this call_name method */
                                /*  (Must be "(II)V") */

         char* return_name,     /* Method name in tclass to call at all */
                                /*  return opcodes in every method */

         char* return_sig,      /* Signature of this return_name method */
                                /*  (Must be "(II)V") */

         char* obj_init_name,   /* Method name in tclass to call first thing */
                                /*   when injecting java.lang.Object.<init> */

         char* obj_init_sig,    /* Signature of this obj_init_name method */
                                /*  (Must be "(Ljava/lang/Object;)V") */

         char* newarray_name,   /* Method name in tclass to call after every */
                                /*   newarray opcode in every method */

         char* newarray_sig,    /* Signature of this method */
                                /*  (Must be "(Ljava/lang/Object;II)V") */

         unsigned char
           **pnew_file_image,   /* Returns a pointer to new classfile image */

         long *pnew_file_len,   /* Returns the length of the new image */

         FatalErrorHandler
           fatal_error_handler, /* Pointer to function to call on any */
                                /*  fatal error. NULL sends error to stderr */

         MethodNumberRegister
           mnum_callback        /* Pointer to function that gets called */
                                /*   with all details on methods in this */
                                /*   class. NULL means skip this call. */

           );


/* External to read the class name out of a class file .
 *
 *   WARNING: If You change the typedef, you MUST change
 *            multiple things in this file, including this name.
 */

#define JAVA_CRW_DEMO_CLASSNAME_SYMBOLS \
         { "java_crw_demo_classname", "_java_crw_demo_classname@12" }

/* Typedef needed for type casting in dynamic access situations. */

typedef char * (JNICALL *JavaCrwDemoClassname)(
         const unsigned char *file_image,
         long file_len,
         FatalErrorHandler fatal_error_handler);

JNIEXPORT char * JNICALL java_crw_demo_classname(
         const unsigned char *file_image,
         long file_len,
         FatalErrorHandler fatal_error_handler);

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */

#endif
