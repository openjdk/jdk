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

#include "stdlib.h"

#include "minst.h"
#include "java_crw_demo.h"


/* ------------------------------------------------------------------- */
/* Some constant maximum sizes */

#define MAX_TOKEN_LENGTH        80
#define MAX_METHOD_NAME_LENGTH  256

/* Some constant names that tie to Java class/method names.
 *    We assume the Java class whose static methods we will be calling
 *    looks like:
 *
 * public class Minst {
 *     private static int engaged;
 *     private static native void _method_entry(Object thr, int cnum, int mnum);
 *     public static void method_entry(int cnum, int mnum)
 *     {
 *         ...
 *     }
 * }
 *
 */

#define MINST_class        Minst            /* Name of class we are using */
#define MINST_entry        method_entry    /* Name of java entry method */
#define MINST_engaged      engaged         /* Name of java static field */

/* C macros to create strings from tokens */
#define _STRING(s) #s
#define STRING(s) _STRING(s)

/* ------------------------------------------------------------------- */

/* Global agent data structure */

typedef struct {
    /* JVMTI Environment */
    jvmtiEnv      *jvmti;
    jboolean       vm_is_dead;
    jboolean       vm_is_started;
    /* Data access Lock */
    jrawMonitorID  lock;
    /* Options */
    char           *include;
    char           *exclude;
    /* Class Count/ID */
    jint            ccount;
} GlobalAgentData;

static GlobalAgentData *gdata;

/* Enter a critical section by doing a JVMTI Raw Monitor Enter */
static void
enter_critical_section(jvmtiEnv *jvmti)
{
    jvmtiError error;

    error = (*jvmti)->RawMonitorEnter(jvmti, gdata->lock);
    check_jvmti_error(jvmti, error, "Cannot enter with raw monitor");
}

/* Exit a critical section by doing a JVMTI Raw Monitor Exit */
static void
exit_critical_section(jvmtiEnv *jvmti)
{
    jvmtiError error;

    error = (*jvmti)->RawMonitorExit(jvmti, gdata->lock);
    check_jvmti_error(jvmti, error, "Cannot exit with raw monitor");
}

/* Callback for JVMTI_EVENT_VM_START */
static void JNICALL
cbVMStart(jvmtiEnv *jvmti, JNIEnv *env)
{
    enter_critical_section(jvmti); {
        /* Indicate VM has started */
        gdata->vm_is_started = JNI_TRUE;
    } exit_critical_section(jvmti);
}

/* Callback for JVMTI_EVENT_VM_INIT */
static void JNICALL
cbVMInit(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    enter_critical_section(jvmti); {
        jclass   klass;
        jfieldID field;

        /* Register Natives for class whose methods we use */
        klass = (*env)->FindClass(env, STRING(MINST_class));
        if ( klass == NULL ) {
            fatal_error("ERROR: JNI: Cannot find %s with FindClass\n",
                        STRING(MINST_class));
        }

        /* Engage calls. */
        field = (*env)->GetStaticFieldID(env, klass, STRING(MINST_engaged), "I");
        if ( field == NULL ) {
            fatal_error("ERROR: JNI: Cannot get field from %s\n",
                        STRING(MINST_class));
        }
        (*env)->SetStaticIntField(env, klass, field, 1);
    } exit_critical_section(jvmti);
}

/* Callback for JVMTI_EVENT_VM_DEATH */
static void JNICALL
cbVMDeath(jvmtiEnv *jvmti, JNIEnv *env)
{
    enter_critical_section(jvmti); {
        jclass   klass;
        jfieldID field;

        /* The VM has died. */
        stdout_message("VMDeath\n");

        /* Disengage calls in MINST_class. */
        klass = (*env)->FindClass(env, STRING(MINST_class));
        if ( klass == NULL ) {
            fatal_error("ERROR: JNI: Cannot find %s with FindClass\n",
                        STRING(MINST_class));
        }
        field = (*env)->GetStaticFieldID(env, klass, STRING(MINST_engaged), "I");
        if ( field == NULL ) {
            fatal_error("ERROR: JNI: Cannot get field from %s\n",
                        STRING(MINST_class));
        }
        (*env)->SetStaticIntField(env, klass, field, -1);

        /* The critical section here is important to hold back the VM death
         *    until all other callbacks have completed.
         */

        /* Since this critical section could be holding up other threads
         *   in other event callbacks, we need to indicate that the VM is
         *   dead so that the other callbacks can short circuit their work.
         *   We don't expect any further events after VmDeath but we do need
         *   to be careful that existing threads might be in our own agent
         *   callback code.
         */
        gdata->vm_is_dead = JNI_TRUE;

    } exit_critical_section(jvmti);

}

/* Callback for JVMTI_EVENT_CLASS_FILE_LOAD_HOOK */
static void JNICALL
cbClassFileLoadHook(jvmtiEnv *jvmti, JNIEnv* env,
                jclass class_being_redefined, jobject loader,
                const char* name, jobject protection_domain,
                jint class_data_len, const unsigned char* class_data,
                jint* new_class_data_len, unsigned char** new_class_data)
{
    enter_critical_section(jvmti); {
        /* It's possible we get here right after VmDeath event, be careful */
        if ( !gdata->vm_is_dead ) {

            const char *classname;

            /* Name could be NULL */
            if ( name == NULL ) {
                classname = java_crw_demo_classname(class_data, class_data_len,
                        NULL);
                if ( classname == NULL ) {
                    fatal_error("ERROR: No classname inside classfile\n");
                }
            } else {
                classname = strdup(name);
                if ( classname == NULL ) {
                    fatal_error("ERROR: Out of malloc memory\n");
                }
            }

            *new_class_data_len = 0;
            *new_class_data     = NULL;

            /* The tracker class itself? */
            if ( interested((char*)classname, "", gdata->include, gdata->exclude)
                  &&  strcmp(classname, STRING(MINST_class)) != 0 ) {
                jint           cnum;
                int            system_class;
                unsigned char *new_image;
                long           new_length;

                /* Get unique number for every class file image loaded */
                cnum = gdata->ccount++;

                /* Is it a system class? If the class load is before VmStart
                 *   then we will consider it a system class that should
                 *   be treated carefully. (See java_crw_demo)
                 */
                system_class = 0;
                if ( !gdata->vm_is_started ) {
                    system_class = 1;
                }

                new_image = NULL;
                new_length = 0;

                /* Call the class file reader/write demo code */
                java_crw_demo(cnum,
                    classname,
                    class_data,
                    class_data_len,
                    system_class,
                    STRING(MINST_class), "L" STRING(MINST_class) ";",
                    STRING(MINST_entry), "(II)V",
                    NULL, NULL,
                    NULL, NULL,
                    NULL, NULL,
                    &new_image,
                    &new_length,
                    NULL,
                    NULL);

                /* If we got back a new class image, return it back as "the"
                 *   new class image. This must be JVMTI Allocate space.
                 */
                if ( new_length > 0 ) {
                    unsigned char *jvmti_space;

                    jvmti_space = (unsigned char *)allocate(jvmti, (jint)new_length);
                    (void)memcpy((void*)jvmti_space, (void*)new_image, (int)new_length);
                    *new_class_data_len = (jint)new_length;
                    *new_class_data     = jvmti_space; /* VM will deallocate */
                }

                /* Always free up the space we get from java_crw_demo() */
                if ( new_image != NULL ) {
                    (void)free((void*)new_image); /* Free malloc() space with free() */
                }
            }
            (void)free((void*)classname);
        }
    } exit_critical_section(jvmti);
}

/* Parse the options for this minst agent */
static void
parse_agent_options(char *options)
{
    char token[MAX_TOKEN_LENGTH];
    char *next;

    /* Parse options and set flags in gdata */
    if ( options==NULL ) {
        return;
    }

    /* Get the first token from the options string. */
    next = get_token(options, ",=", token, sizeof(token));

    /* While not at the end of the options string, process this option. */
    while ( next != NULL ) {
        if ( strcmp(token,"help")==0 ) {
            stdout_message("The minst JVMTI demo agent\n");
            stdout_message("\n");
            stdout_message(" java -agent:minst[=options] ...\n");
            stdout_message("\n");
            stdout_message("The options are comma separated:\n");
            stdout_message("\t help\t\t\t Print help information\n");
            stdout_message("\t include=item\t\t Only these classes/methods\n");
            stdout_message("\t exclude=item\t\t Exclude these classes/methods\n");
            stdout_message("\n");
            stdout_message("item\t Qualified class and/or method names\n");
            stdout_message("\t\t e.g. (*.<init>;Foobar.method;sun.*)\n");
            stdout_message("\n");
            exit(0);
        } else if ( strcmp(token,"include")==0 ) {
            int   used;
            int   maxlen;

            maxlen = MAX_METHOD_NAME_LENGTH;
            if ( gdata->include == NULL ) {
                gdata->include = (char*)calloc(maxlen+1, 1);
                used = 0;
            } else {
                used  = (int)strlen(gdata->include);
                gdata->include[used++] = ',';
                gdata->include[used] = 0;
                gdata->include = (char*)
                             realloc((void*)gdata->include, used+maxlen+1);
            }
            if ( gdata->include == NULL ) {
                fatal_error("ERROR: Out of malloc memory\n");
            }
            /* Add this item to the list */
            next = get_token(next, ",=", gdata->include+used, maxlen);
            /* Check for token scan error */
            if ( next==NULL ) {
                fatal_error("ERROR: include option error\n");
            }
        } else if ( strcmp(token,"exclude")==0 ) {
            int   used;
            int   maxlen;

            maxlen = MAX_METHOD_NAME_LENGTH;
            if ( gdata->exclude == NULL ) {
                gdata->exclude = (char*)calloc(maxlen+1, 1);
                used = 0;
            } else {
                used  = (int)strlen(gdata->exclude);
                gdata->exclude[used++] = ',';
                gdata->exclude[used] = 0;
                gdata->exclude = (char*)
                             realloc((void*)gdata->exclude, used+maxlen+1);
            }
            if ( gdata->exclude == NULL ) {
                fatal_error("ERROR: Out of malloc memory\n");
            }
            /* Add this item to the list */
            next = get_token(next, ",=", gdata->exclude+used, maxlen);
            /* Check for token scan error */
            if ( next==NULL ) {
                fatal_error("ERROR: exclude option error\n");
            }
        } else if ( token[0]!=0 ) {
            /* We got a non-empty token and we don't know what it is. */
            fatal_error("ERROR: Unknown option: %s\n", token);
        }
        /* Get the next token (returns NULL if there are no more) */
        next = get_token(next, ",=", token, sizeof(token));
    }
}

/* Agent_OnLoad: This is called immediately after the shared library is
 *   loaded. This is the first code executed.
 */
JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
    static GlobalAgentData data;
    jvmtiEnv              *jvmti;
    jvmtiError             error;
    jint                   res;
    jvmtiCapabilities      capabilities;
    jvmtiEventCallbacks    callbacks;

    /* Setup initial global agent data area
     *   Use of static/extern data should be handled carefully here.
     *   We need to make sure that we are able to cleanup after ourselves
     *     so anything allocated in this library needs to be freed in
     *     the Agent_OnUnload() function.
     */
    (void)memset((void*)&data, 0, sizeof(data));
    gdata = &data;

    /* First thing we need to do is get the jvmtiEnv* or JVMTI environment */
    res = (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION_1);
    if (res != JNI_OK) {
        /* This means that the VM was unable to obtain this version of the
         *   JVMTI interface, this is a fatal error.
         */
        fatal_error("ERROR: Unable to access JVMTI Version 1 (0x%x),"
                " is your JDK a 5.0 or newer version?"
                " JNIEnv's GetEnv() returned %d\n",
               JVMTI_VERSION_1, res);
    }

    /* Here we save the jvmtiEnv* for Agent_OnUnload(). */
    gdata->jvmti = jvmti;

    /* Parse any options supplied on java command line */
    parse_agent_options(options);

    /* Immediately after getting the jvmtiEnv* we need to ask for the
     *   capabilities this agent will need. In this case we need to make
     *   sure that we can get all class load hooks.
     */
    (void)memset(&capabilities,0, sizeof(capabilities));
    capabilities.can_generate_all_class_hook_events  = 1;
    error = (*jvmti)->AddCapabilities(jvmti, &capabilities);
    check_jvmti_error(jvmti, error, "Unable to get necessary JVMTI capabilities.");

    /* Next we need to provide the pointers to the callback functions to
     *   to this jvmtiEnv*
     */
    (void)memset(&callbacks,0, sizeof(callbacks));
    /* JVMTI_EVENT_VM_START */
    callbacks.VMStart           = &cbVMStart;
    /* JVMTI_EVENT_VM_INIT */
    callbacks.VMInit           = &cbVMInit;
    /* JVMTI_EVENT_VM_DEATH */
    callbacks.VMDeath           = &cbVMDeath;
    /* JVMTI_EVENT_CLASS_FILE_LOAD_HOOK */
    callbacks.ClassFileLoadHook = &cbClassFileLoadHook;
    error = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, (jint)sizeof(callbacks));
    check_jvmti_error(jvmti, error, "Cannot set jvmti callbacks");

    /* At first the only initial events we are interested in are VM
     *   initialization, VM death, and Class File Loads.
     *   Once the VM is initialized we will request more events.
     */
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_VM_START, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_VM_INIT, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_VM_DEATH, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");

    /* Here we create a raw monitor for our use in this agent to
     *   protect critical sections of code.
     */
    error = (*jvmti)->CreateRawMonitor(jvmti, "agent data", &(gdata->lock));
    check_jvmti_error(jvmti, error, "Cannot create raw monitor");

    /* Add demo jar file to boot classpath */
    add_demo_jar_to_bootclasspath(jvmti, "minst");

    /* We return JNI_OK to signify success */
    return JNI_OK;
}

/* Agent_OnUnload: This is called immediately before the shared library is
 *   unloaded. This is the last code executed.
 */
JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM *vm)
{
    /* Make sure all malloc/calloc/strdup space is freed */
    if ( gdata->include != NULL ) {
        (void)free((void*)gdata->include);
        gdata->include = NULL;
    }
    if ( gdata->exclude != NULL ) {
        (void)free((void*)gdata->exclude);
        gdata->exclude = NULL;
    }
}
