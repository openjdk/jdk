/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include "jdk_tools_jaotc_jnilibelf_JNILibELFAPI.h"

// For file open and close
#include <fcntl.h>
#include <unistd.h>
#include <err.h>
#include <sysexits.h>
#include <stdlib.h>
#include <string.h>

#include <assert.h>

// For libelf interfaces
#include <libelf.h>
#include <gelf.h>

// Convenience macro to shut the compiler warnings
#ifdef UNUSED
#elif defined(__GNUC__)
# define UNUSED(x) UNUSED_ ## x __attribute__((unused))
#elif defined(__LCLINT__)
# define UNUSED(x) /*@unused@*/ x
#else
# define UNUSED(x) x
#endif

/**
 * libelfshim version
 */
#ifndef AOT_VERSION_STRING
  #error AOT_VERSION_STRING must be defined
#endif

JNIEXPORT jstring JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elfshim_1version
(JNIEnv* env, jclass UNUSED(c))  {
   const char* ver = AOT_VERSION_STRING;
   return (*env)->NewStringUTF(env, ver);
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1version
(JNIEnv* UNUSED(env), jclass UNUSED(c), jint v)  {
    return elf_version(v);
}

/**
 * Unbox the Pointer object the encapsulated native address.
 */

static jlong getNativeAddress(JNIEnv* env, jobject ptrObj) {
   jlong nativeAddress = -1;
   assert (ptrObj != NULL);
   // Get a reference to ptr object's class
   jclass ptrClass = (*env)->GetObjectClass(env, ptrObj);
   if (ptrClass != NULL) {
       // Get the Field ID of the instance variables "address"
       jfieldID fidNumber = (*env)->GetFieldID(env, ptrClass, "address", "J");
       if (fidNumber != NULL) {
           // Get the long given the Field ID
           nativeAddress = (*env)->GetLongField(env, ptrObj, fidNumber);
       }
   }
   // fprintf(stderr, "Native address : %lx\n", nativeAddress);
   return nativeAddress;
}

/**
 * Box the nativeAddress as a Pointer object.
 */
static jobject makePointerObject(JNIEnv* env, jlong nativeAddr) {
   jobject retObj = NULL;
   jclass ptrClass = (*env)->FindClass(env, "jdk/tools/jaotc/jnilibelf/Pointer");
   if (ptrClass != NULL) {
       // Call back constructor to allocate a Pointer object, with an int argument
       jmethodID constructorId = (*env)->GetMethodID(env, ptrClass, "<init>", "(J)V");
       if (constructorId != NULL) {
           retObj = (*env)->NewObject(env, ptrClass, constructorId, nativeAddr);
       }
   }
   return retObj;
}

JNIEXPORT jobject JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1begin
(JNIEnv* env, jclass UNUSED(class), jint filedes, jint cmd, jobject ptrObj) {

   Elf* elfPtr = NULL;
   jlong addr = getNativeAddress(env, ptrObj);

   if (addr != -1) {
       // Call libelf function
       if ((elfPtr = elf_begin(filedes, cmd, (Elf *) addr)) == NULL) {
           errx(EX_SOFTWARE, "elf_begin() failed: %s.", elf_errmsg(-1));
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_begin()\n");
   }

   return makePointerObject(env, (jlong) elfPtr);
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1end
(JNIEnv* env, jclass UNUSED(class), jobject ptrObj) {

   jlong addr = getNativeAddress(env, ptrObj);

   if (addr != -1) {
       // Call libelf function
       return elf_end((Elf *) addr);
   } else {
       fprintf(stderr, "Failed to get native address to call elf_end()\n");
       return -1;
   }
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1kind
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj) {
   jlong addr = getNativeAddress(env, ptrObj);

   if (addr != -1) {
       // Call libelf function
       return elf_kind((Elf *) addr);
   } else {
       fprintf(stderr, "Failed to get native address to call elf_kind()\n");
       return -1;
   }
}
JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1flagphdr
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint cmd, jint flags) {

   jlong addr = getNativeAddress(env, ptrObj);
   unsigned int retVal = 0;

   if (addr != -1) {
       // Call libelf function
       if ((retVal = elf_flagphdr((Elf *) addr, cmd, flags)) == 0) {
           errx(EX_SOFTWARE, "elf_flagphdr() failed: %s.", elf_errmsg(-1));
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_flagphdr()\n");
   }
   return retVal;
}

JNIEXPORT jobject JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1newscn
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj) {

   Elf_Scn* elfSecPtr = NULL;
   jlong addr = getNativeAddress(env, ptrObj);

   if (addr != -1) {
       // Call libelf function
       if ((elfSecPtr = elf_newscn((Elf *) addr)) == NULL) {
           errx(EX_SOFTWARE, "elf_newscn() failed: %s.", elf_errmsg(-1));
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_newscn()\n");
   }

   return makePointerObject(env, (jlong) elfSecPtr);
}

JNIEXPORT jobject JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1newdata
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj) {

   Elf_Data* elfDataPtr = NULL;
   jlong addr = getNativeAddress(env, ptrObj);

   if (addr != -1) {
       // Call libelf function
       if ((elfDataPtr = elf_newdata((Elf_Scn *) addr)) == NULL) {
           errx(EX_SOFTWARE, "elf_newdata() failed: %s.", elf_errmsg(-1));
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_newdata()\n");
   }
   return makePointerObject(env, (jlong) elfDataPtr);
}

JNIEXPORT jobject JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf64_1getshdr
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj) {

   Elf64_Shdr* elf64ShdrPtr = NULL;
   jlong addr = getNativeAddress(env, ptrObj);

   if (addr != -1) {
       // Call libelf function
       if ((elf64ShdrPtr = elf64_getshdr((Elf_Scn *) addr)) == NULL) {
           errx(EX_SOFTWARE, "elf64_getshdr() failed: %s.", elf_errmsg(-1));
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_getshdr()\n");
   }
   return makePointerObject(env, (jlong) elf64ShdrPtr);
}

JNIEXPORT jlong JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1update
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint cmd) {

   off_t size = -1;
   jlong addr = getNativeAddress(env, ptrObj);

   if (addr != -1) {
       // Call libelf function
       if ((size = elf_update((Elf*) addr, cmd)) == -1) {
           errx(EX_SOFTWARE, "elf_update() failed: %s size (%d) cmd (%d).", elf_errmsg(-1), (int)size, cmd);
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_update()\n");
   }
   return size;
}

JNIEXPORT jstring JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1errmsg
(JNIEnv* env, jclass UNUSED(c), jint errno) {

   const char * retPtr = NULL;
   // Call libelf function
   if ((retPtr = elf_errmsg(errno)) == NULL) {
       errx(EX_SOFTWARE, "elf_errmsg() failed: %s.", elf_errmsg(-1));
   }
   return (*env)->NewStringUTF(env, retPtr);
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_elf_1ndxscn
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj) {
   jint secnum = SHN_UNDEF;
   jlong addr = getNativeAddress(env, ptrObj);
   if (addr != -1) {
       // Call libelf function
       secnum = elf_ndxscn((Elf_Scn*) addr);
   } else {
       fprintf(stderr, "Failed to get native address to call elf_ndxscn()\n");
   }
   return secnum;
}

JNIEXPORT jobject JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_gelf_1newehdr
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint elfClass) {
   unsigned long int retPtr = 0;
   jlong addr = getNativeAddress(env, ptrObj);
   if (addr != -1) {
       // Call libelf function
       if ((retPtr = gelf_newehdr((Elf*) addr, elfClass)) == 0) {
           errx(EX_SOFTWARE, "gelf_newehdr() failed: %s.", elf_errmsg(-1));
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_newehdr()\n");
   }
   return makePointerObject(env, (jlong) retPtr);
}

JNIEXPORT jobject JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_gelf_1newphdr
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint phnum) {
   unsigned long int retPtr = 0;
   jlong addr = getNativeAddress(env, ptrObj);
   if (addr != -1) {
       // Call libelf function
       if ((retPtr = gelf_newphdr((Elf*) addr, phnum)) == 0) {
           errx(EX_SOFTWARE, "gelf_newphdr() failed: %s.", elf_errmsg(-1));
       }
   } else {
       fprintf(stderr, "Failed to get native address to call elf_newphdr()\n");
   }
   return makePointerObject(env, (jlong) retPtr);
}


/* File operations */

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_open_1rw
(JNIEnv * env, jclass UNUSED(class), jstring jfileName)  {
    int flags = O_RDWR | O_CREAT | O_TRUNC;
    int mode  = 0666;
    int retVal;
    const char* cfileName = (*env)->GetStringUTFChars(env, jfileName, NULL);
    if (cfileName == NULL) {
        return -1;
    }
    retVal = open(cfileName, flags, mode);
    if (retVal < 0) {
       err(EX_NOINPUT, "open %s failed", cfileName);
    }
    (*env)->ReleaseStringUTFChars(env, jfileName, cfileName);

    return retVal;
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_open__Ljava_lang_String_2I
(JNIEnv * env, jclass UNUSED(class), jstring jfileName, jint flags)  {
    int retVal;
    const char* cfileName = (*env)->GetStringUTFChars(env, jfileName, NULL);
    if (cfileName == NULL) {
        return -1;
    }
    retVal = open(cfileName, flags);
    if (retVal < 0) {
       err(EX_NOINPUT, "open %s failed", cfileName);
    }
    (*env)->ReleaseStringUTFChars(env, jfileName, cfileName);

    return retVal;
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_open__Ljava_lang_String_2II
(JNIEnv * env, jclass UNUSED(class), jstring jfileName, jint flags, jint mode)  {
    int retVal;
    const char* cfileName = (*env)->GetStringUTFChars(env, jfileName, NULL);
    if (cfileName == NULL) {
        return -1;
    }
    retVal = open(cfileName, flags, mode);
    if (retVal < 0) {
       err(EX_NOINPUT, "open %s failed", cfileName);
    }
    (*env)->ReleaseStringUTFChars(env, jfileName, cfileName);

    return retVal;
}


JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_close
(JNIEnv* UNUSED(env), jclass UNUSED(class), jint fd) {
    return close(fd);
}

/**
 * Miscellaneous ELF data structure peek-poke functions in
 * shim_functions.c. No corresponding .h file exists yet.
 * So each function needs to be declared as extern
 */

extern int size_of_Sym(int elfclass);
extern int size_of_Rel(int elfclass);
extern int size_of_Rela(int elfclass);

extern void ehdr_set_data_encoding(void * ehdr, int val);
extern void set_Ehdr_e_machine(int elfclass, void * structPtr, int val);
extern void set_Ehdr_e_type(int elfclass, void * structPtr, int val);
extern void set_Ehdr_e_version(int elfclass, void * structPtr, int val);
extern void set_Ehdr_e_shstrndx(int elfclass, void * structPtr, int val);

extern void phdr_set_type_self(int elfclass, void * ehdr, void * phdr);
extern void phdr_set_type_self(int elfclass, void * ehdr, void * phdr);

extern void set_Shdr_sh_name(int elfclass, void* structPtr, int val);
extern void set_Shdr_sh_type(int elfclass, void* structPtr, int val);
extern void set_Shdr_sh_flags(int elfclass, void* structPtr, int val);
extern void set_Shdr_sh_entsize(int elfclass, void* structPtr, int val);
extern void set_Shdr_sh_link(int elfclass, void* structPtr, int val);
extern void set_Shdr_sh_info(int elfclass, void* structPtr, int val);

extern void set_Data_d_align(void* structPtr, int val);
extern void set_Data_d_off(void* structPtr, int val);
extern void set_Data_d_buf(void* structPtr, void* val);
extern void set_Data_d_type(void* structPtr, int val);
extern void set_Data_d_size(void* structPtr, int val);
extern void set_Data_d_version(void* structPtr, int val);

extern void* create_sym_entry(int elfclass, int index, int type, int bind,
                               int shndx, int size, int value);
extern void * create_reloc_entry(int elfclass, int roffset, int symtabIdx,
                                 int relocType, int raddend, int reloca);

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_size_1of_1Sym
(JNIEnv* UNUSED(env), jclass UNUSED(c), jint elfClass) {
    return size_of_Sym(elfClass);
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_size_1of_1Rela
(JNIEnv* UNUSED(env), jclass UNUSED(c), jint elfClass) {
    return size_of_Rela(elfClass);
}

JNIEXPORT jint JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_size_1of_1Rel
(JNIEnv* UNUSED(env), jclass UNUSED(c), jint elfClass) {
    return size_of_Rel(elfClass);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_ehdr_1set_1data_1encoding
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint val) {
    void* ehdr = (void*) getNativeAddress(env, ptrObj);
    ehdr_set_data_encoding(ehdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Ehdr_1e_1machine
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* ehdr = (void*) getNativeAddress(env, ptrObj);
    set_Ehdr_e_machine(elfClass, ehdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Ehdr_1e_1type
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* ehdr = (void*) getNativeAddress(env, ptrObj);
    set_Ehdr_e_type(elfClass, ehdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Ehdr_1e_1version
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* ehdr = (void*) getNativeAddress(env, ptrObj);
    set_Ehdr_e_version(elfClass, ehdr, val);
}
JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Ehdr_1e_1shstrndx
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* shdr = (void*) getNativeAddress(env, ptrObj);
    set_Ehdr_e_shstrndx(elfClass, shdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_phdr_1set_1type_1self
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ehdrPtr, jobject phdrPtr) {
    void* ehdr = (void*) getNativeAddress(env, ehdrPtr);
    void* phdr = (void*) getNativeAddress(env, phdrPtr);
    phdr_set_type_self(elfClass, ehdr, phdr);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Shdr_1sh_1name
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* shdr = (void*) getNativeAddress(env, ptrObj);
    set_Shdr_sh_name(elfClass, shdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Shdr_1sh_1type
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* shdr = (void*) getNativeAddress(env, ptrObj);
    set_Shdr_sh_type(elfClass, shdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Shdr_1sh_1flags
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* shdr = (void*) getNativeAddress(env, ptrObj);
    set_Shdr_sh_flags(elfClass, shdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Shdr_1sh_1entsize
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* shdr = (void*) getNativeAddress(env, ptrObj);
    set_Shdr_sh_entsize(elfClass, shdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Shdr_1sh_1info
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* shdr = (void*) getNativeAddress(env, ptrObj);
    set_Shdr_sh_info(elfClass, shdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Shdr_1sh_1link
(JNIEnv* env, jclass UNUSED(c), jint elfClass, jobject ptrObj, jint val) {
    void* shdr = (void*) getNativeAddress(env, ptrObj);
    set_Shdr_sh_link(elfClass, shdr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Data_1d_1align
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint val) {
    void* dptr = (void*) getNativeAddress(env, ptrObj);
    set_Data_d_align(dptr, val);
}


JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Data_1d_1off
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint val) {
    void* dptr = (void*) getNativeAddress(env, ptrObj);
    set_Data_d_off(dptr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Data_1d_1buf
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jobject bufPtr) {
    void* dptr = (void*) getNativeAddress(env, ptrObj);
    void* bptr = (void*) getNativeAddress(env, bufPtr);
    set_Data_d_buf(dptr, bptr);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Data_1d_1type
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint val) {
    void* dptr = (void*) getNativeAddress(env, ptrObj);
    set_Data_d_type(dptr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Data_1d_1size
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint val) {
    void* dptr = (void*) getNativeAddress(env, ptrObj);
    set_Data_d_size(dptr, val);
}

JNIEXPORT void JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_set_1Data_1d_1version
(JNIEnv* env, jclass UNUSED(c), jobject ptrObj, jint val) {
    void* dptr = (void*) getNativeAddress(env, ptrObj);
    set_Data_d_version(dptr, val);
}

JNIEXPORT jlong JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_create_1sym_1entry
(JNIEnv* UNUSED(env), jclass UNUSED(c), jint elfClass, jint index, jint type,
 jint bind, jint shndx, jint size, jint value) {
   void * retVal = create_sym_entry(elfClass, index, type, bind,
                                    shndx, size, value);
   return (jlong)retVal;
}

JNIEXPORT jlong JNICALL Java_jdk_tools_jaotc_jnilibelf_JNILibELFAPI_create_1reloc_1entry
(JNIEnv* UNUSED(env), jclass UNUSED(c), jint elfClass, jint roffset,
 jint symTabIdx, jint relocType, jint raddend, jint reloca) {
   void * retVal = create_reloc_entry(elfClass, roffset, symTabIdx,
                                      relocType, raddend, reloca);
   return (jlong)retVal;
}

