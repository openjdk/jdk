#include <assert.h>
#include <jni.h>
#include <alloca.h>

#include <pthread.h>

union env_union
{
  void *void_env;
  JNIEnv *jni_env;
};

union env_union tmp;
JNIEnv* env;
JavaVM* jvm;
JavaVMInitArgs vm_args;
JavaVMOption options[1];
jclass class_id;
jmethodID method_id;
jint result;

long product(unsigned long n, unsigned long m) {
    if (m == 1) {
      return n;
    } else {
      int *p = alloca(sizeof (int));
      *p = n;
      return product (n, m-1) + *p;
    }
}

void *
floobydust (void *p)
{
  (*jvm)->AttachCurrentThread(jvm, &tmp.void_env, NULL);
  env = tmp.jni_env;

  class_id = (*env)->FindClass (env, "T");
  assert (class_id);

  method_id = (*env)->GetStaticMethodID (env, class_id, "printIt", "()V");
  assert (method_id);

  (*env)->CallStaticVoidMethod (env, class_id, method_id, NULL);

  (*jvm)->DetachCurrentThread(jvm);

  printf("%ld\n", product(5000,5000));

  (*jvm)->AttachCurrentThread(jvm, &tmp.void_env, NULL);
  env = tmp.jni_env;

  class_id = (*env)->FindClass (env, "T");
  assert (class_id);

  method_id = (*env)->GetStaticMethodID (env, class_id, "printIt", "()V");
  assert (method_id);

  (*env)->CallStaticVoidMethod (env, class_id, method_id, NULL);

  (*jvm)->DetachCurrentThread(jvm);

  printf("%ld\n", product(5000,5000));

  return NULL;
}

int
main (int argc, const char** argv)
{
  options[0].optionString = "-Xss320k";

  vm_args.version = JNI_VERSION_1_2;
  vm_args.ignoreUnrecognized = JNI_TRUE;
  vm_args.options = options;
  vm_args.nOptions = 1;

  result = JNI_CreateJavaVM (&jvm, &tmp.void_env, &vm_args);
  assert (result >= 0);

  env = tmp.jni_env;

  floobydust (NULL);

  pthread_t thr;
  pthread_create (&thr, NULL, floobydust, NULL);
  pthread_join (thr, NULL);

  return 0;
}
