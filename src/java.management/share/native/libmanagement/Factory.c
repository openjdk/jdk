#include <jni.h>
#include "management.h"
#include "sun_management_cmd_Factory.h"

JNIEXPORT void JNICALL Java_sun_management_cmd_Factory_doRegister0
  (JNIEnv *env, jclass cls, jobject factory) {
    jmm_interface->RegisterDiagnosticCommand(env, factory);
}

