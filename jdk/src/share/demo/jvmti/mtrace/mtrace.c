/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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


#include "stdlib.h"

#include "mtrace.h"
#include "java_crw_demo.h"


/* ------------------------------------------------------------------- */
/* Some constant maximum sizes */

#define MAX_TOKEN_LENGTH        16
#define MAX_THREAD_NAME_LENGTH  512
#define MAX_METHOD_NAME_LENGTH  1024

/* Some constant names that tie to Java class/method names.
 *    We assume the Java class whose static methods we will be calling
 *    looks like:
 *
 * public class Mtrace {
 *     private static int engaged;
 *     private static native void _method_entry(Object thr, int cnum, int mnum);
 *     public static void method_entry(int cnum, int mnum)
 *     {
 *         if ( engaged != 0 ) {
 *             _method_entry(Thread.currentThread(), cnum, mnum);
 *         }
 *     }
 *     private static native void _method_exit(Object thr, int cnum, int mnum);
 *     public static void method_exit(int cnum, int mnum)
 *     {
 *         if ( engaged != 0 ) {
 *             _method_exit(Thread.currentThread(), cnum, mnum);
 *         }
 *     }
 * }
 *
 *    The engaged field allows us to inject all classes (even system classes)
 *    and delay the actual calls to the native code until the VM has reached
 *    a safe time to call native methods (Past the JVMTI VM_START event).
 *
 */

#define MTRACE_class        Mtrace          /* Name of class we are using */
#define MTRACE_entry        method_entry    /* Name of java entry method */
#define MTRACE_exit         method_exit     /* Name of java exit method */
#define MTRACE_native_entry _method_entry   /* Name of java entry native */
#define MTRACE_native_exit  _method_exit    /* Name of java exit native */
#define MTRACE_engaged      engaged         /* Name of java static field */

/* C macros to create strings from tokens */
#define _STRING(s) #s
#define STRING(s) _STRING(s)

/* ------------------------------------------------------------------- */

/* Data structure to hold method and class information in agent */

typedef struct MethodInfo {
    const char *name;          /* Method name */
    const char *signature;     /* Method signature */
    int         calls;         /* Method call count */
    int         returns;       /* Method return count */
} MethodInfo;

typedef struct ClassInfo {
    const char *name;          /* Class name */
    int         mcount;        /* Method count */
    MethodInfo *methods;       /* Method information */
    int         calls;         /* Method call count for this class */
} ClassInfo;

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
    int             max_count;
    /* ClassInfo Table */
    ClassInfo      *classes;
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

/* Get a name for a jthread */
static void
get_thread_name(jvmtiEnv *jvmti, jthread thread, char *tname, int maxlen)
{
    jvmtiThreadInfo info;
    jvmtiError      error;

    /* Make sure the stack variables are garbage free */
    (void)memset(&info,0, sizeof(info));

    /* Assume the name is unknown for now */
    (void)strcpy(tname, "Unknown");

    /* Get the thread information, which includes the name */
    error = (*jvmti)->GetThreadInfo(jvmti, thread, &info);
    check_jvmti_error(jvmti, error, "Cannot get thread info");

    /* The thread might not have a name, be careful here. */
    if ( info.name != NULL ) {
        int len;

        /* Copy the thread name into tname if it will fit */
        len = (int)strlen(info.name);
        if ( len < maxlen ) {
            (void)strcpy(tname, info.name);
        }

        /* Every string allocated by JVMTI needs to be freed */
        deallocate(jvmti, (void*)info.name);
    }
}

/* Qsort class compare routine */
static int
class_compar(const void *e1, const void *e2)
{
    ClassInfo *c1 = (ClassInfo*)e1;
    ClassInfo *c2 = (ClassInfo*)e2;
    if ( c1->calls > c2->calls ) return  1;
    if ( c1->calls < c2->calls ) return -1;
    return 0;
}

/* Qsort method compare routine */
static int
method_compar(const void *e1, const void *e2)
{
    MethodInfo *m1 = (MethodInfo*)e1;
    MethodInfo *m2 = (MethodInfo*)e2;
    if ( m1->calls > m2->calls ) return  1;
    if ( m1->calls < m2->calls ) return -1;
    return 0;
}

/* Callback from java_crw_demo() that gives us mnum mappings */
static void
mnum_callbacks(unsigned cnum, const char **names, const char**sigs, int mcount)
{
    ClassInfo  *cp;
    int         mnum;

    if ( cnum >= (unsigned)gdata->ccount ) {
        fatal_error("ERROR: Class number out of range\n");
    }
    if ( mcount == 0 ) {
        return;
    }

    cp           = gdata->classes + (int)cnum;
    cp->calls    = 0;
    cp->mcount   = mcount;
    cp->methods  = (MethodInfo*)calloc(mcount, sizeof(MethodInfo));
    if ( cp->methods == NULL ) {
        fatal_error("ERROR: Out of malloc memory\n");
    }

    for ( mnum = 0 ; mnum < mcount ; mnum++ ) {
        MethodInfo *mp;

        mp            = cp->methods + mnum;
        mp->name      = (const char *)strdup(names[mnum]);
        if ( mp->name == NULL ) {
            fatal_error("ERROR: Out of malloc memory\n");
        }
        mp->signature = (const char *)strdup(sigs[mnum]);
        if ( mp->signature == NULL ) {
            fatal_error("ERROR: Out of malloc memory\n");
        }
    }
}

/* Java Native Method for entry */
static void
MTRACE_native_entry(JNIEnv *env, jclass klass, jobject thread, jint cnum, jint mnum)
{
    enter_critical_section(gdata->jvmti); {
        /* It's possible we get here right after VmDeath event, be careful */
        if ( !gdata->vm_is_dead ) {
            ClassInfo  *cp;
            MethodInfo *mp;

            if ( cnum >= gdata->ccount ) {
                fatal_error("ERROR: Class number out of range\n");
            }
            cp = gdata->classes + cnum;
            if ( mnum >= cp->mcount ) {
                fatal_error("ERROR: Method number out of range\n");
            }
            mp = cp->methods + mnum;
            if ( interested((char*)cp->name, (char*)mp->name,
                            gdata->include, gdata->exclude)  ) {
                mp->calls++;
                cp->calls++;
            }
        }
    } exit_critical_section(gdata->jvmti);
}

/* Java Native Method for exit */
static void
MTRACE_native_exit(JNIEnv *env, jclass klass, jobject thread, jint cnum, jint mnum)
{
    enter_critical_section(gdata->jvmti); {
        /* It's possible we get here right after VmDeath event, be careful */
        if ( !gdata->vm_is_dead ) {
            ClassInfo  *cp;
            MethodInfo *mp;

            if ( cnum >= gdata->ccount ) {
                fatal_error("ERROR: Class number out of range\n");
            }
            cp = gdata->classes + cnum;
            if ( mnum >= cp->mcount ) {
                fatal_error("ERROR: Method number out of range\n");
            }
            mp = cp->methods + mnum;
            if ( interested((char*)cp->name, (char*)mp->name,
                            gdata->include, gdata->exclude)  ) {
                mp->returns++;
            }
        }
    } exit_critical_section(gdata->jvmti);
}

/* Callback for JVMTI_EVENT_VM_START */
static void JNICALL
cbVMStart(jvmtiEnv *jvmti, JNIEnv *env)
{
    enter_critical_section(jvmti); {
        jclass   klass;
        jfieldID field;
        int      rc;

        /* Java Native Methods for class */
        static JNINativeMethod registry[2] = {
            {STRING(MTRACE_native_entry), "(Ljava/lang/Object;II)V",
                (void*)&MTRACE_native_entry},
            {STRING(MTRACE_native_exit),  "(Ljava/lang/Object;II)V",
                (void*)&MTRACE_native_exit}
        };

        /* The VM has started. */
        stdout_message("VMStart\n");

        /* Register Natives for class whose methods we use */
        klass = (*env)->FindClass(env, STRING(MTRACE_class));
        if ( klass == NULL ) {
            fatal_error("ERROR: JNI: Cannot find %s with FindClass\n",
                        STRING(MTRACE_class));
        }
        rc = (*env)->RegisterNatives(env, klass, registry, 2);
        if ( rc != 0 ) {
            fatal_error("ERROR: JNI: Cannot register native methods for %s\n",
                        STRING(MTRACE_class));
        }

        /* Engage calls. */
        field = (*env)->GetStaticFieldID(env, klass, STRING(MTRACE_engaged), "I");
        if ( field == NULL ) {
            fatal_error("ERROR: JNI: Cannot get field from %s\n",
                        STRING(MTRACE_class));
        }
        (*env)->SetStaticIntField(env, klass, field, 1);

        /* Indicate VM has started */
        gdata->vm_is_started = JNI_TRUE;

    } exit_critical_section(jvmti);
}

/* Callback for JVMTI_EVENT_VM_INIT */
static void JNICALL
cbVMInit(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    enter_critical_section(jvmti); {
        char  tname[MAX_THREAD_NAME_LENGTH];
        static jvmtiEvent events[] =
                { JVMTI_EVENT_THREAD_START, JVMTI_EVENT_THREAD_END };
        int        i;

        /* The VM has started. */
        get_thread_name(jvmti, thread, tname, sizeof(tname));
        stdout_message("VMInit %s\n", tname);

        /* The VM is now initialized, at this time we make our requests
         *   for additional events.
         */

        for( i=0; i < (int)(sizeof(events)/sizeof(jvmtiEvent)); i++) {
            jvmtiError error;

            /* Setup event  notification modes */
            error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                                  events[i], (jthread)NULL);
            check_jvmti_error(jvmti, error, "Cannot set event notification");
        }

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

        /* Disengage calls in MTRACE_class. */
        klass = (*env)->FindClass(env, STRING(MTRACE_class));
        if ( klass == NULL ) {
            fatal_error("ERROR: JNI: Cannot find %s with FindClass\n",
                        STRING(MTRACE_class));
        }
        field = (*env)->GetStaticFieldID(env, klass, STRING(MTRACE_engaged), "I");
        if ( field == NULL ) {
            fatal_error("ERROR: JNI: Cannot get field from %s\n",
                        STRING(MTRACE_class));
        }
        (*env)->SetStaticIntField(env, klass, field, 0);

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

        /* Dump out stats */
        stdout_message("Begin Class Stats\n");
        if ( gdata->ccount > 0 ) {
            int cnum;

            /* Sort table (in place) by number of method calls into class. */
            /*  Note: Do not use this table after this qsort! */
            qsort(gdata->classes, gdata->ccount, sizeof(ClassInfo),
                        &class_compar);

            /* Dump out gdata->max_count most called classes */
            for ( cnum=gdata->ccount-1 ;
                  cnum >= 0 && cnum >= gdata->ccount - gdata->max_count;
                  cnum-- ) {
                ClassInfo *cp;
                int        mnum;

                cp = gdata->classes + cnum;
                stdout_message("Class %s %d calls\n", cp->name, cp->calls);
                if ( cp->calls==0 ) continue;

                /* Sort method table (in place) by number of method calls. */
                /*  Note: Do not use this table after this qsort! */
                qsort(cp->methods, cp->mcount, sizeof(MethodInfo),
                            &method_compar);
                for ( mnum=cp->mcount-1 ; mnum >= 0 ; mnum-- ) {
                    MethodInfo *mp;

                    mp = cp->methods + mnum;
                    if ( mp->calls==0 ) continue;
                    stdout_message("\tMethod %s %s %d calls %d returns\n",
                        mp->name, mp->signature, mp->calls, mp->returns);
                }
            }
        }
        stdout_message("End Class Stats\n");
        (void)fflush(stdout);

    } exit_critical_section(jvmti);

}

/* Callback for JVMTI_EVENT_THREAD_START */
static void JNICALL
cbThreadStart(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    enter_critical_section(jvmti); {
        /* It's possible we get here right after VmDeath event, be careful */
        if ( !gdata->vm_is_dead ) {
            char  tname[MAX_THREAD_NAME_LENGTH];

            get_thread_name(jvmti, thread, tname, sizeof(tname));
            stdout_message("ThreadStart %s\n", tname);
        }
    } exit_critical_section(jvmti);
}

/* Callback for JVMTI_EVENT_THREAD_END */
static void JNICALL
cbThreadEnd(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    enter_critical_section(jvmti); {
        /* It's possible we get here right after VmDeath event, be careful */
        if ( !gdata->vm_is_dead ) {
            char  tname[MAX_THREAD_NAME_LENGTH];

            get_thread_name(jvmti, thread, tname, sizeof(tname));
            stdout_message("ThreadEnd %s\n", tname);
        }
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
                  &&  strcmp(classname, STRING(MTRACE_class)) != 0 ) {
                jint           cnum;
                int            system_class;
                unsigned char *new_image;
                long           new_length;
                ClassInfo     *cp;

                /* Get unique number for every class file image loaded */
                cnum = gdata->ccount++;

                /* Save away class information */
                if ( gdata->classes == NULL ) {
                    gdata->classes = (ClassInfo*)malloc(
                                gdata->ccount*sizeof(ClassInfo));
                } else {
                    gdata->classes = (ClassInfo*)
                                realloc((void*)gdata->classes,
                                gdata->ccount*sizeof(ClassInfo));
                }
                if ( gdata->classes == NULL ) {
                    fatal_error("ERROR: Out of malloc memory\n");
                }
                cp           = gdata->classes + cnum;
                cp->name     = (const char *)strdup(classname);
                if ( cp->name == NULL ) {
                    fatal_error("ERROR: Out of malloc memory\n");
                }
                cp->calls    = 0;
                cp->mcount   = 0;
                cp->methods  = NULL;

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
                    STRING(MTRACE_class), "L" STRING(MTRACE_class) ";",
                    STRING(MTRACE_entry), "(II)V",
                    STRING(MTRACE_exit), "(II)V",
                    NULL, NULL,
                    NULL, NULL,
                    &new_image,
                    &new_length,
                    NULL,
                    &mnum_callbacks);

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

/* Parse the options for this mtrace agent */
static void
parse_agent_options(char *options)
{
    char token[MAX_TOKEN_LENGTH];
    char *next;

    gdata->max_count = 10; /* Default max=n */

    /* Parse options and set flags in gdata */
    if ( options==NULL ) {
        return;
    }

    /* Get the first token from the options string. */
    next = get_token(options, ",=", token, sizeof(token));

    /* While not at the end of the options string, process this option. */
    while ( next != NULL ) {
        if ( strcmp(token,"help")==0 ) {
            stdout_message("The mtrace JVMTI demo agent\n");
            stdout_message("\n");
            stdout_message(" java -agent:mtrace[=options] ...\n");
            stdout_message("\n");
            stdout_message("The options are comma separated:\n");
            stdout_message("\t help\t\t\t Print help information\n");
            stdout_message("\t max=n\t\t Only list top n classes\n");
            stdout_message("\t include=item\t\t Only these classes/methods\n");
            stdout_message("\t exclude=item\t\t Exclude these classes/methods\n");
            stdout_message("\n");
            stdout_message("item\t Qualified class and/or method names\n");
            stdout_message("\t\t e.g. (*.<init>;Foobar.method;sun.*)\n");
            stdout_message("\n");
            exit(0);
        } else if ( strcmp(token,"max")==0 ) {
            char number[MAX_TOKEN_LENGTH];

            /* Get the numeric option */
            next = get_token(next, ",=", number, (int)sizeof(number));
            /* Check for token scan error */
            if ( next==NULL ) {
                fatal_error("ERROR: max=n option error\n");
            }
            /* Save numeric value */
            gdata->max_count = atoi(number);
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
    callbacks.VMInit            = &cbVMInit;
    /* JVMTI_EVENT_VM_DEATH */
    callbacks.VMDeath           = &cbVMDeath;
    /* JVMTI_EVENT_CLASS_FILE_LOAD_HOOK */
    callbacks.ClassFileLoadHook = &cbClassFileLoadHook;
    /* JVMTI_EVENT_THREAD_START */
    callbacks.ThreadStart       = &cbThreadStart;
    /* JVMTI_EVENT_THREAD_END */
    callbacks.ThreadEnd         = &cbThreadEnd;
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
    add_demo_jar_to_bootclasspath(jvmti, "mtrace");

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
    if ( gdata->classes != NULL ) {
        int cnum;

        for ( cnum = 0 ; cnum < gdata->ccount ; cnum++ ) {
            ClassInfo *cp;

            cp = gdata->classes + cnum;
            (void)free((void*)cp->name);
            if ( cp->mcount > 0 ) {
                int mnum;

                for ( mnum = 0 ; mnum < cp->mcount ; mnum++ ) {
                    MethodInfo *mp;

                    mp = cp->methods + mnum;
                    (void)free((void*)mp->name);
                    (void)free((void*)mp->signature);
                }
                (void)free((void*)cp->methods);
            }
        }
        (void)free((void*)gdata->classes);
        gdata->classes = NULL;
    }
}
