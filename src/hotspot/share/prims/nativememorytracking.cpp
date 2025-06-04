#include "jni.h"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "nmt/memTagFactory.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/mallocHeader.hpp"
#include "nmt/mallocHeader.inline.hpp"

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

JVM_ENTRY(jlong, NMT_makeTag(JNIEnv *env, jobject ignored_this, jobject tag_name_string)) {
  oop tn_oop = JNIHandles::resolve(tag_name_string);
  Handle tnh(THREAD, tn_oop);
  if (tnh.is_null()) {
    // TODO: Throw exception
  }

  if (!java_lang_String::is_instance(tn_oop)) {
    // TODO: Throw exception
  }

  if (!MemTracker::enabled()) {
    return (jlong)mtNone;
  }

  ResourceMark rm;
  const char* tag_name = java_lang_String::as_utf8_string(tn_oop);
  MemTag tag = MemTagFactory::tag(tag_name);
  MemTagFactory::set_human_readable_name_of(tag, tag_name);
  return (jlong)tag;
}
JVM_END

// Warning:
// If you do this then you better make sure no other thread has access to the allocated object.
JVM_ENTRY(long, NMT_allocate0(JNIEnv *env, jobject ignored_this, jlong size, jlong mem_tag)) {
  MemTag tag = (MemTag)mem_tag;
  size_t sz = (size_t)size;
  return (jlong)os::malloc(sz, tag);
}
JVM_END

static JNINativeMethod NMT_methods[] = {
  {CC "makeTag", CC "(Ljava/lang/String;)J", FN_PTR(NMT_makeTag)},
  {CC "allocate0", CC "(JJ)J", FN_PTR(NMT_allocate0)}
};

JVM_ENTRY(void, JVM_RegisterNativeMemoryTrackingMethods(JNIEnv *env, jclass NMT_class)) {
  ThreadToNativeFromVM ttnfv(thread);

  int status = env->RegisterNatives(NMT_class, NMT_methods, sizeof(NMT_methods) / sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionCheck(),
            "register java.lang.invoke.MethodHandleNative natives");
}
JVM_END
