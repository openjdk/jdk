/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "JVMTITools.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JNI_ENV_ARG

#ifdef __cplusplus
#define JNI_ENV_ARG(x, y) y
#define JNI_ENV_PTR(x) x
#else
#define JNI_ENV_ARG(x,y) x, y
#define JNI_ENV_PTR(x) (*x)
#endif

#endif

#define PASSED 0
#define STATUS_FAILED 2

typedef struct {
    const char *name;
    const char *sig;
    jint value;
} var_info;

typedef struct {
    jboolean isObsolete;
    const char *name;
    const char *sig;
    jint line;
    jint count;
    var_info *vars;
} frame_info;

static jvmtiEnv *jvmti = NULL;
static jvmtiCapabilities caps;
static jvmtiEventCallbacks callbacks;
static jint result = PASSED;
static jboolean printdump = JNI_FALSE;
static jbyteArray classBytes;
static jmethodID midCheckPoint = NULL;
static jmethodID midRun = NULL;
static jint framesExpected = 0;
static jint framesCount = 0;

static const char *cls_exp = "Lnsk/jvmti/RedefineClasses/redefclass019a;";

static var_info run[] = {
    {"this", "Lnsk/jvmti/RedefineClasses/redefclass019a;", 0}
};

static var_info checkPoint[] = {
    {"this", "Lnsk/jvmti/RedefineClasses/redefclass019a;", 0}
};

static var_info chain1[] = {
    {"this", "Lnsk/jvmti/RedefineClasses/redefclass019a;", 0},
    {"localInt1", "I", 2},
    {"localInt2", "I", 3333}
};

static var_info chain2[] = {
    {"this", "Lnsk/jvmti/RedefineClasses/redefclass019a;", 0}
};

static var_info chain3[] = {
    {"this", "Lnsk/jvmti/RedefineClasses/redefclass019a;", 0}
};

static frame_info frames[] = {
    {JNI_TRUE,  "checkPoint", "()V", 115, 1, checkPoint},
    {JNI_FALSE, "chain3",     "()V", 49, 1, chain3},
    {JNI_FALSE, "chain2",     "()V", 44, 1, chain2},
    {JNI_FALSE, "chain1",     "()V", 39, 3, chain1},
    {JNI_FALSE, "run",        "()V", 32, 1, run},
};


void check(jvmtiEnv *jvmti_env, jthread thr, jmethodID mid, jint i) {
    jvmtiError err;
    jclass cls;
    jlocation loc;
    char *sigClass, *name = NULL, *sig = NULL, *generic;
    jboolean is_obsolete;
    jvmtiLineNumberEntry *lines = NULL;
    jvmtiLocalVariableEntry *table = NULL;
    jint line = -1;
    jint entryCount = 0;
    jint varValue = -1;
    jint j, k;

    if (i >= (jint) (sizeof(frames)/sizeof(frame_info))) {
        printf("(pop %d) too many frames\n", i);
        result = STATUS_FAILED;
        return;
    }

    err = (*jvmti_env)->GetFrameLocation(jvmti_env, thr, 0, &mid, &loc);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetClassSignature#%d) unexpected error: %s (%d)\n",
               i, TranslateError(err), err);
        result = STATUS_FAILED;
    }

    err = (*jvmti_env)->GetMethodDeclaringClass(jvmti_env, mid, &cls);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetMethodDeclaringClass#%d) unexpected error: %s (%d)\n",
               i, TranslateError(err), err);
        result = STATUS_FAILED;
    }

    err = (*jvmti_env)->GetClassSignature(jvmti_env, cls, &sigClass, &generic);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetClassSignature#%d) unexpected error: %s (%d)\n",
               i, TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (sigClass == NULL || strcmp(sigClass, cls_exp) != 0) {
        printf("(pop %d) wrong class sig: \"%s\",", i, sigClass);
        printf(" expected: \"%s\"\n", cls_exp);
        if (sigClass != NULL) {
            (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)sigClass);
        }
        result = STATUS_FAILED;
    } else {
        err = (*jvmti_env)->GetMethodName(jvmti_env, mid, &name, &sig, &generic);
        if (err != JVMTI_ERROR_NONE) {
            printf("(GetMethodName#%d) unexpected error: %s (%d)\n",
                   i, TranslateError(err), err);
            result = STATUS_FAILED;
        }

        err = (*jvmti_env)->IsMethodObsolete(jvmti_env, mid, &is_obsolete);
        if (err != JVMTI_ERROR_NONE) {
            printf("(IsMethodObsolete#%d) unexpected error: %s (%d)\n",
                   i, TranslateError(err), err);
            result = STATUS_FAILED;
        }

        if (printdump == JNI_TRUE) {
            printf(">>> pop %d: \"%s.%s%s\"%s\n", i, sigClass, name, sig,
                (is_obsolete == JNI_TRUE) ? " (obsolete)" : "");
        }
        if (frames[i].isObsolete != is_obsolete) {
            printf("(pop %d) %s obsolete method\n", i,
                (is_obsolete == JNI_TRUE) ? "unexpected" : "should be");
            result = STATUS_FAILED;
        }
        if (name == NULL || strcmp(name, frames[i].name) != 0) {
            printf("(pop %d) wrong method name: \"%s\",", i, name);
            printf(" expected: \"%s\"\n", frames[i].name);
            result = STATUS_FAILED;
        }
        if (sig == NULL || strcmp(sig, frames[i].sig) != 0) {
            printf("(pop %d) wrong method sig: \"%s\",", i, sig);
            printf(" expected: \"%s\"\n", frames[i].sig);
            result = STATUS_FAILED;
        }

        err = (*jvmti_env)->GetLineNumberTable(jvmti_env, mid,
            &entryCount, &lines);
        if (err != JVMTI_ERROR_NONE) {
            printf("(GetClassSignature#%d) unexpected error: %s (%d)\n",
                   i, TranslateError(err), err);
            result = STATUS_FAILED;
        }

        if (lines != NULL && entryCount > 0) {
            for (k = 0; k < entryCount; k++) {
                if (loc < lines[k].start_location) {
                    break;
                }
            }
            line = lines[k-1].line_number;
        }
        if (line != frames[i].line) {
            printf("(pop %d) wrong line number: %d, expected: %d\n",
                   i, line, frames[i].line);
            result = STATUS_FAILED;
        }

        err = (*jvmti_env)->GetLocalVariableTable(jvmti_env, mid,
            &entryCount, &table);
        if (err != JVMTI_ERROR_NONE) {
            printf("(GetLocalVariableTable#%d) unexpected error: %s (%d)\n",
                   i, TranslateError(err), err);
            result = STATUS_FAILED;
        }

        if (frames[i].count != entryCount) {
            printf("(pop %d) wrong number of locals: %d, expected: %d\n",
                   i, entryCount, frames[i].count);
            result = STATUS_FAILED;
        }

        if (table != NULL) {
            for (k = 0; k < frames[i].count; k++) {
                for (j = 0; j < entryCount; j++) {
                    if (strcmp(table[j].name, frames[i].vars[k].name) == 0 &&
                        strcmp(table[j].signature, frames[i].vars[k].sig) == 0) {
                        if (table[j].signature[0] == 'I') {
                            err = (*jvmti_env)->GetLocalInt(jvmti_env, thr, 0,
                                table[j].slot, &varValue);
                            if (err != JVMTI_ERROR_NONE) {
                                printf("(GetLocalInt#%d) unexpected error: %s (%d)\n",
                                       i, TranslateError(err), err);
                                result = STATUS_FAILED;
                            }
                            if (printdump == JNI_TRUE) {
                                printf(">>>   var: \"%s %s\"",
                                       table[j].name, table[j].signature);
                                printf(", value: %d\n", varValue);
                            }
                            if (varValue != frames[i].vars[k].value) {
                                printf("(pop %d) wrong local var value: %d,",
                                       i, varValue);
                                printf(" expected: %d\n", frames[i].vars[k].value);
                                result = STATUS_FAILED;
                            }
                        } else if (printdump == JNI_TRUE) {
                            printf(">>>   var: \"%s %s\"\n",
                                   table[j].name, table[j].signature);
                        }
                        break;
                    }
                }
                if (j == entryCount) {
                    printf("(pop %d) var \"%s %s\" not found\n",
                           i, frames[i].vars[k].name, frames[i].vars[k].sig);
                    result = STATUS_FAILED;
                }
            }
        }
    }

    if (sigClass != NULL) {
        (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)sigClass);
    }
    if (name != NULL) {
        (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)name);
    }
    if (sig != NULL) {
        (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)sig);
    }
    if (lines != NULL) {
        (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)lines);
    }
    if (table != NULL) {
        for (j = 0; j < entryCount; j++) {
            (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)(table[j].name));
            (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)(table[j].signature));
        }
        (*jvmti_env)->Deallocate(jvmti_env, (unsigned char*)table);
    }
}

void JNICALL Breakpoint(jvmtiEnv *jvmti_env, JNIEnv *env,
        jthread thread, jmethodID method, jlocation location) {
    jvmtiError err;
    jclass klass;
    jvmtiClassDefinition classDef;

    if (midCheckPoint != method) {
        printf("bp: don't know where we get called from\n");
        result = STATUS_FAILED;
        return;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> breakpoint in checkPoint\n");
    }

    err = (*jvmti_env)->ClearBreakpoint(jvmti_env, midCheckPoint, 0);
    if (err != JVMTI_ERROR_NONE) {
        printf("(ClearBreakpoint) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    err = (*jvmti_env)->GetMethodDeclaringClass(jvmti_env, method, &klass);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetMethodDeclaringClass) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
        return;
    }

    classDef.klass = klass;
    classDef.class_byte_count =
        JNI_ENV_PTR(env)->GetArrayLength(JNI_ENV_ARG((JNIEnv *)env, classBytes));
    classDef.class_bytes = (unsigned char *)
        JNI_ENV_PTR(env)->GetByteArrayElements(JNI_ENV_ARG((JNIEnv *)env,
            classBytes), NULL);

    if (printdump == JNI_TRUE) {
        printf(">>> about to call RedefineClasses\n");
    }

    err = (*jvmti_env)->RedefineClasses(jvmti_env, 1, &classDef);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RedefineClasses) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    err = (*jvmti_env)->NotifyFramePop(jvmti_env, thread, 0);
    if (err != JVMTI_ERROR_NONE) {
        printf("(NotifyFramePop) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
}

void JNICALL FramePop(jvmtiEnv *jvmti_env, JNIEnv *env, jthread thread,
        jmethodID method, jboolean wasPopedByException) {
    jvmtiError err;

    check(jvmti_env, thread, method, framesCount);
    framesCount++;
    if (method != midRun) {
        err = (*jvmti_env)->NotifyFramePop(jvmti_env, thread, 1);
        if (err != JVMTI_ERROR_NONE) {
            printf("(NotifyFramePop#%d) unexpected error: %s (%d)\n",
                   framesCount, TranslateError(err), err);
            result = STATUS_FAILED;
        }
    } else {
        if (printdump == JNI_TRUE) {
            printf(">>> poped %d frames till method \"run()\"\n",
                   framesCount);
        }
    }
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_redefclass019(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_redefclass019(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_redefclass019(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiError err;
    jint res;

    if (options != NULL && strcmp(options, "printdump") == 0) {
        printdump = JNI_TRUE;
    }

    res = JNI_ENV_PTR(jvm)->GetEnv(JNI_ENV_ARG(jvm, (void **) &jvmti),
        JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == NULL) {
        printf("Wrong result of a valid call to GetEnv!\n");
        return JNI_ERR;
    }

    err = (*jvmti)->GetPotentialCapabilities(jvmti, &caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetPotentialCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = (*jvmti)->AddCapabilities(jvmti, &caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(AddCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = (*jvmti)->GetCapabilities(jvmti, &caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    if (!caps.can_redefine_classes) {
        printf("Warning: RedefineClasses is not implemented\n");
    }

    if (!caps.can_get_line_numbers) {
        printf("Warning: GetLineNumberTable is not implemented\n");
    }

    if (!caps.can_access_local_variables) {
        printf("Warning: access to local variables is not implemented\n");
    }

    if (caps.can_generate_breakpoint_events &&
            caps.can_generate_frame_pop_events) {
        callbacks.Breakpoint = &Breakpoint;
        callbacks.FramePop = &FramePop;
        err = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
        if (err != JVMTI_ERROR_NONE) {
            printf("(SetEventCallbacks) unexpected error: %s (%d)\n",
                   TranslateError(err), err);
            return JNI_ERR;
        }
    } else {
        printf("Warning: Breakpoint event is not implemented\n");
    }

    return JNI_OK;
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_RedefineClasses_redefclass019_getReady(JNIEnv *env, jclass cls,
        jclass clazz, jbyteArray bytes, jint depth) {
    jvmtiError err;

    if (jvmti == NULL) {
        printf("JVMTI client was not properly loaded!\n");
        result = STATUS_FAILED;
        return;
    }

    if (!caps.can_redefine_classes ||
        !caps.can_generate_breakpoint_events ||
        !caps.can_generate_frame_pop_events ||
        !caps.can_get_line_numbers ||
        !caps.can_access_local_variables) return;

    classBytes = JNI_ENV_PTR(env)->NewGlobalRef(JNI_ENV_ARG(env, bytes));

    midRun = JNI_ENV_PTR(env)->GetMethodID(JNI_ENV_ARG(env, clazz),
         "run", "()V");
    if (midRun == NULL) {
        printf("Cannot find Method ID for method run\n");
        result = STATUS_FAILED;
        return;
    }

    midCheckPoint = JNI_ENV_PTR(env)->GetMethodID(JNI_ENV_ARG(env, clazz),
         "checkPoint", "()V");
    if (midCheckPoint == NULL) {
        printf("Cannot find Method ID for method checkPoint\n");
        result = STATUS_FAILED;
        return;
    }

    err = (*jvmti)->SetBreakpoint(jvmti, midCheckPoint, 0);
    if (err != JVMTI_ERROR_NONE) {
        printf("(SetBreakpoint) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
        return;
    }

    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
        JVMTI_EVENT_BREAKPOINT, NULL);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to enable BREAKPOINT event: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
        return;
    }

    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
        JVMTI_EVENT_FRAME_POP, NULL);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to enable FRAME_POP event: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    } else {
        framesExpected = depth;
    }
}

JNIEXPORT jint JNICALL
Java_nsk_jvmti_RedefineClasses_redefclass019_check(JNIEnv *env, jclass cls) {
    if (framesCount != framesExpected) {
        printf("Wrong number of frames: %d, expected: %d\n",
            framesCount, framesExpected);
        result = STATUS_FAILED;
    }
    return result;
}

#ifdef __cplusplus
}
#endif
