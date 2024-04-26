/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "CallHelper.hpp"
#ifdef AIX
#include <pthread.h>
#endif //AIX

/*
 * Test for JDK-8280902
 *
 * A call to ResourceBundle::getBundle() should not throw NPE when
 * called with a null caller.  This test fetches a simple bundle in
 * the test module and makes sure it can read the expected value of
 * 'Hello!' using the key 'message'.
 *
 * This also tests that ResourceBundle::clearCache() doesn't throw an
 * NPE when called with a null caller.
 */
void getBundle(JNIEnv* env) {
    StaticCall m_ResourceBundle_getBundle { env,
        "java/util/ResourceBundle", "getBundle",
        "(Ljava/lang/String;)Ljava/util/ResourceBundle;" };
    InstanceCall m_ResourceBundle_getString { env,
        "java/util/ResourceBundle", "getString",
        "(Ljava/lang/String;)Ljava/lang/String;" };
    StaticCall m_ResourceBundle_clearCache { env,
        "java/util/ResourceBundle", "clearCache", "()V"
    };

    // b = ResourceBundle.getBundle("open/NullCallerResource");
    jobject b = m_ResourceBundle_getBundle.callReturnNotNull(
        env->NewStringUTF("open/NullCallerResource"));

    // msg = b.getString("message");
    jstring msg = (jstring) m_ResourceBundle_getString.callReturnNotNull(b, env->NewStringUTF("message"));

    const char* chars = env->GetStringUTFChars(msg, nullptr);
    if (std::string("Hello!") != chars) {
        emitErrorMessageAndExit("Bundle didn't contain expected content");
    }
    env->ReleaseStringUTFChars(msg, chars);

    // ResourceBundle.clearCache()
    m_ResourceBundle_clearCache.callVoidMethod();
}

/*
 * Test for JDK-8281000
 *
 * This test checks to make sure that calling ClassLoader::registerAsParallelCapable()
 * with a null caller results in an ICE being thrown.
 */
void registerAsParallelCapable(JNIEnv* env) {
    StaticCall m_ClassLoader_registerAsParallelCapable { env,
        "java/lang/ClassLoader", "registerAsParallelCapable", "()Z" };

    // ClassLoader.registerAsParallelCapable();
    m_ClassLoader_registerAsParallelCapable.
        callBooleanMethodWithException("java/lang/IllegalCallerException");
}

/*
 * Test for JDK-8281001
 *
 * Try and load a class using Class::forName in the module n which should be
 * found with the system classloader (to match FindClass() used above).
 * Class exp = Class.forName("open.OpenResources");
 */
void forName(JNIEnv* env) {
    StaticCall m_Class_forName { env,
        "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;" };

    m_Class_forName.callReturnNotNull(env->NewStringUTF("open.OpenResources"));
}

/*
 * Test for JDK-8281003
 *
 * The call to MethodHandles::lookup should throw ICE when called with
 * a null caller.
 */
void lookup(JNIEnv* env) {
    StaticCall m_MethodHandles_lookup { env,
        "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;" };

    m_MethodHandles_lookup.
        callObjectMethodWithException("java/lang/IllegalCallerException");
}

/*
 * This function tests changes made for JDK-8281006
 * Module::getResourceAsStream should check if the resource is open
 * unconditionally when caller is null
 *
 * The java test running this native test creates a test module named 'n'
 * which opens the package 'open'.  It has a text file resource named
 * 'test.txt' in the open package.  It also has a class called
 * open.OpenResources.  One should be able to get the resource through
 * either the Class or the Module with getResourceAsStream.
 */
void getResourceAsStream(JNIEnv *env) {
    InstanceCall m_InputStream_close { env,
        "java/io/InputStream", "close", "()V" };
    InstanceCall m_Class_getModule {env,
        "java/lang/Class", "getModule", "()Ljava/lang/Module;" };
    InstanceCall m_Module_getResourceAsStream { env,
        "java/lang/Module", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;" };
    InstanceCall m_Class_getResourceAsStream { env,
        "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;" };

    // fetch the open and closed classes
    jclass class_OpenResources = env->FindClass("open/OpenResources");
    assert(class_OpenResources != nullptr);
    jclass class_ClosedResources = env->FindClass("closed/ClosedResources");
    assert(class_ClosedResources != nullptr);

    // Fetch the Module from one of the classes in the module
    jobject n = m_Class_getModule.callReturnNotNull(class_OpenResources);

    // Attempt to fetch an open resource from the module.  It should return a valid stream.
    // InputStream in1 = n.getResourceAsStream("open/test.txt");
    // in1.close();
    jobject in1 = m_Module_getResourceAsStream.callReturnNotNull(n, env->NewStringUTF("open/test.txt"));
    m_InputStream_close.callVoidMethod(in1);

    // Attempt to fetch closed resource from the module.  It should return null.
    // InputStream in2 = n.getResourceAsStream("closed/test.txt");
    m_Module_getResourceAsStream.callReturnIsNull(n, env->NewStringUTF("closed/test.txt"));

    // Attempt to fetch open resource from the class.  It should return a valid stream.
    // InputStream in3 = open.OpenReosurces.class.getResourceAsStream("test.txt");
    // in3.close();
    jobject in3 = m_Class_getResourceAsStream.callReturnNotNull(
        class_OpenResources, env->NewStringUTF("test.txt"));
    m_InputStream_close.callVoidMethod(in3);

    // Attempt to fetch closed resource from the class.  It should return null.
    // InputStream in4 = closed.ClosedResources.class.getResourceAsStream("test.txt");
    m_Class_getResourceAsStream.callReturnIsNull(
        class_ClosedResources, env->NewStringUTF("test.txt"));
}

static void* run(void *arg) {
    JavaVM *jvm;
    JNIEnv *env;
    JavaVMInitArgs vm_args;
    JavaVMOption options[4];
    jint rc;

    options[0].optionString = (char*) "--module-path=mods";
    options[1].optionString = (char*) "--add-modules=n";

    vm_args.version = JNI_VERSION_9;
    vm_args.nOptions = 2;
    vm_args.options = options;

    if ((rc = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args)) != JNI_OK) {
        emitErrorMessageAndExit("Cannot create VM.");
    }

    getBundle(env);
    registerAsParallelCapable(env);
    forName(env);
    lookup(env);
    getResourceAsStream(env);

    jvm->DestroyJavaVM();
    return 0;
}

int main(int argc, char *argv[]) {
#ifdef AIX
    size_t adjusted_stack_size = 1024*1024;
    pthread_t id;
    int result;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, adjusted_stack_size);
    result = pthread_create(&id, &attr, run, (void *)argv);
    if (result != 0) {
      fprintf(stderr, "Error: pthread_create failed with error code %d \n", result);
      return -1;
    }
    pthread_join(id, nullptr);
#else
    run(&argv);
#endif //AIX
}

