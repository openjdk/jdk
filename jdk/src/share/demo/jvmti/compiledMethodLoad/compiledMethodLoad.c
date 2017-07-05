/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 *   - Neither the name of Sun Microsystems nor the names of its
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jvmti.h"
#include "jvmticmlr.h"

#include "agent_util.h"

/* Global static data */
static char          OUTPUT_FILE[] = "compiledMethodLoad.txt";
static FILE         *fp;
static jvmtiEnv     *jvmti;
static jrawMonitorID lock;

/* print a jvmtiCompiledMethodLoadDummyRecord */
void
print_dummy_record(jvmtiCompiledMethodLoadDummyRecord* record,
    jvmtiEnv* jvmti, FILE* fp) {

    if (record != NULL) {
        fprintf(fp, "Dummy record detected containing message: %s\n",
            (char *)record->message);
    }
}

/* print the specified stack frames */
void
print_stack_frames(PCStackInfo* record, jvmtiEnv *jvmti, FILE* fp) {
    if (record != NULL && record->methods != NULL) {
        int i;

        for (i = 0; i < record->numstackframes; i++) {
            jvmtiError err;
            char* method_name = NULL;
            char* class_name = NULL;
            char* method_signature = NULL;
            char* class_signature = NULL;
            char* generic_ptr_method = NULL;
            char* generic_ptr_class = NULL;
            jmethodID id;
            jclass declaringclassptr;
            id = record->methods[i];

            err = (*jvmti)->GetMethodDeclaringClass(jvmti, id,
                      &declaringclassptr);
            check_jvmti_error(jvmti, err, "get method declaring class");

            err = (*jvmti)->GetClassSignature(jvmti, declaringclassptr,
                      &class_signature, &generic_ptr_class);
            check_jvmti_error(jvmti, err, "get class signature");

            err = (*jvmti)->GetMethodName(jvmti, id, &method_name,
                      &method_signature, &generic_ptr_method);
            check_jvmti_error(jvmti, err, "get method name");

            fprintf(fp, "%s::%s %s %s @%d\n", class_signature, method_name,
                method_signature,
                generic_ptr_method == NULL ? "" : generic_ptr_method,
                record->bcis[i]);

            if (method_name != NULL) {
                err = (*jvmti)->Deallocate(jvmti, (unsigned char*)method_name);
                check_jvmti_error(jvmti, err, "deallocate method_name");
            }
            if (method_signature != NULL) {
                err = (*jvmti)->Deallocate(jvmti,
                          (unsigned char*)method_signature);
                check_jvmti_error(jvmti, err, "deallocate method_signature");
            }
            if (generic_ptr_method != NULL) {
                err = (*jvmti)->Deallocate(jvmti,
                          (unsigned char*)generic_ptr_method);
                check_jvmti_error(jvmti, err, "deallocate generic_ptr_method");
            }
            if (class_name != NULL) {
                err = (*jvmti)->Deallocate(jvmti, (unsigned char*)class_name);
                check_jvmti_error(jvmti, err, "deallocate class_name");
            }
            if (class_signature != NULL) {
                err = (*jvmti)->Deallocate(jvmti,
                          (unsigned char*)class_signature);
                check_jvmti_error(jvmti, err, "deallocate class_signature");
            }
            if (generic_ptr_class != NULL) {
                err = (*jvmti)->Deallocate(jvmti,
                          (unsigned char*)generic_ptr_class);
                check_jvmti_error(jvmti, err, "deallocate generic_ptr_class");
            }
        }
    }
}

/* print a jvmtiCompiledMethodLoadInlineRecord */
void
print_inline_info_record(jvmtiCompiledMethodLoadInlineRecord* record,
    jvmtiEnv *jvmti, FILE* fp) {

    if (record != NULL && record->pcinfo != NULL) {
        int numpcs = record->numpcs;
        int i;

        for (i = 0; i < numpcs; i++) {
            PCStackInfo pcrecord = (record->pcinfo[i]);
            fprintf(fp, "PcDescriptor(pc=0x%lx):\n", (jint)(pcrecord.pc));
            print_stack_frames(&pcrecord, jvmti, fp);
        }
    }
}

/* decode kind of CompiledMethodLoadRecord and print */
void
print_records(jvmtiCompiledMethodLoadRecordHeader* list, jvmtiEnv *jvmti,
    FILE* fp)
{
    jvmtiCompiledMethodLoadRecordHeader* curr = list;
    fprintf(fp, "\nPrinting PC Descriptors\n\n");
    while (curr != NULL) {
        switch (curr->kind) {
        case JVMTI_CMLR_DUMMY:
            print_dummy_record((jvmtiCompiledMethodLoadDummyRecord *)curr,
                jvmti, fp);
            break;

        case JVMTI_CMLR_INLINE_INFO:
            print_inline_info_record(
                (jvmtiCompiledMethodLoadInlineRecord *)curr, jvmti, fp);
            break;

        default:
            fprintf(fp, "Warning: unrecognized record: kind=%d\n", curr->kind);
            break;
        }

        curr = (jvmtiCompiledMethodLoadRecordHeader *)curr->next;
    }
}

/* Callback for JVMTI_EVENT_COMPILED_METHOD_LOAD */
void JNICALL
compiled_method_load(jvmtiEnv *jvmti, jmethodID method, jint code_size,
    const void* code_addr, jint map_length, const jvmtiAddrLocationMap* map,
    const void* compile_info)
{
    jvmtiError err;
    char* name = NULL;
    char* signature = NULL;
    char* generic_ptr = NULL;
    jvmtiCompiledMethodLoadRecordHeader* pcs;

    err = (*jvmti)->RawMonitorEnter(jvmti, lock);
    check_jvmti_error(jvmti, err, "raw monitor enter");

    err = (*jvmti)->GetMethodName(jvmti, method, &name, &signature,
              &generic_ptr);
    check_jvmti_error(jvmti, err, "get method name");

    fprintf(fp, "\nCompiled method load event\n");
    fprintf(fp, "Method name %s %s %s\n\n", name, signature,
        generic_ptr == NULL ? "" : generic_ptr);
    pcs = (jvmtiCompiledMethodLoadRecordHeader *)compile_info;
    if (pcs != NULL) {
        print_records(pcs, jvmti, fp);
    }

    if (name != NULL) {
        err = (*jvmti)->Deallocate(jvmti, (unsigned char*)name);
        check_jvmti_error(jvmti, err, "deallocate name");
    }
    if (signature != NULL) {
        err = (*jvmti)->Deallocate(jvmti, (unsigned char*)signature);
        check_jvmti_error(jvmti, err, "deallocate signature");
    }
    if (generic_ptr != NULL) {
        err = (*jvmti)->Deallocate(jvmti, (unsigned char*)generic_ptr);
        check_jvmti_error(jvmti, err, "deallocate generic_ptr");
    }

    err = (*jvmti)->RawMonitorExit(jvmti, lock);
    check_jvmti_error(jvmti, err, "raw monitor exit");
}

/* Agent_OnLoad() is called first, we prepare for a COMPILED_METHOD_LOAD
 * event here.
 */
JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
    jint                rc;
    jvmtiError          err;
    jvmtiCapabilities   capabilities;
    jvmtiEventCallbacks callbacks;

    fp = fopen(OUTPUT_FILE, "w");
    if (fp == NULL) {
        fatal_error("ERROR: %s: Unable to create output file\n", OUTPUT_FILE);
        return -1;
    }

    /* Get JVMTI environment */
    rc = (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION);
    if (rc != JNI_OK) {
        fatal_error(
            "ERROR: Unable to create jvmtiEnv, GetEnv failed, error=%d\n", rc);
        return -1;
    }

    /* add JVMTI capabilities */
    memset(&capabilities,0, sizeof(capabilities));
    capabilities.can_generate_compiled_method_load_events = 1;
    err = (*jvmti)->AddCapabilities(jvmti, &capabilities);
    check_jvmti_error(jvmti, err, "add capabilities");

    /* set JVMTI callbacks for events */
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.CompiledMethodLoad = &compiled_method_load;
    err = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
    check_jvmti_error(jvmti, err, "set event callbacks");

    /* enable JVMTI events */
    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                        JVMTI_EVENT_COMPILED_METHOD_LOAD, NULL);
    check_jvmti_error(jvmti, err, "set event notify");

    /* create coordination monitor */
    err = (*jvmti)->CreateRawMonitor(jvmti, "agent lock", &lock);
    check_jvmti_error(jvmti, err, "create raw monitor");

    return 0;
}

/* Agent_OnUnload() is called last */
JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM *vm)
{
}
