/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "compiler/compilationPolicy.hpp"
#include "memory/resourceArea.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"

#define FOREIGN_ABI "jdk/internal/foreign/abi/"

extern struct JavaVM_ main_vm;

// When an upcall is invoked from a thread that is not attached to the VM, we need to attach it,
// and then to detach it at some point later. Detaching a thread as soon as the upcall completes
// is suboptimal, as the same thread could later upcall to Java again, at which point the VM would
// create multiple Java views of the same native thread. For this reason, we use thread local storage
// to keep track of the fact that we have attached a native thread to the VM. When the thread local
// storage is destroyed (which happens when the native threads is terminated), we check if the
// storage has an attached thread and, if so, we detach it from the VM.
struct UpcallContext {
  Thread* attachedThread;

  UpcallContext() {} // Explicit constructor to address XL C compiler bug.
  ~UpcallContext() {
    if (attachedThread != nullptr) {
      JavaVM_ *vm = (JavaVM *)(&main_vm);
      vm->functions->DetachCurrentThread(vm);
    }
  }
};

APPROVED_CPP_THREAD_LOCAL UpcallContext threadContext;

JavaThread* UpcallLinker::maybe_attach_and_get_thread() {
  JavaThread* thread = JavaThread::current_or_null();
  if (thread == nullptr) {
    JavaVM_ *vm = (JavaVM *)(&main_vm);
    JNIEnv* p_env = nullptr; // unused
    jint result = vm->functions->AttachCurrentThreadAsDaemon(vm, (void**) &p_env, nullptr);
    guarantee(result == JNI_OK, "Could not attach thread for upcall. JNI error code: %d", result);
    thread = JavaThread::current();
    threadContext.attachedThread = thread;
    assert(!thread->has_last_Java_frame(), "newly-attached thread not expected to have last Java frame");
  }
  return thread;
}

// modelled after JavaCallWrapper::JavaCallWrapper
JavaThread* UpcallLinker::on_entry(UpcallStub::FrameData* context, jobject receiver) {
  JavaThread* thread = maybe_attach_and_get_thread();
  guarantee(thread->thread_state() == _thread_in_native, "wrong thread state for upcall");
  context->thread = thread;

  guarantee(thread->can_call_java(), "must be able to call Java");

  // Allocate handle block for Java code. This must be done before we change thread_state to _thread_in_Java,
  // since it can potentially block.
  context->new_handles = JNIHandleBlock::allocate_block(thread);

  // clear any pending exception in thread (native calls start with no exception pending)
  thread->clear_pending_exception();

  // The call to transition_from_native below contains a safepoint check
  // which needs the code cache to be writable.
  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, thread));

  // After this, we are officially in Java Code. This needs to be done before we change any of the thread local
  // info, since we cannot find oops before the new information is set up completely.
  ThreadStateTransition::transition_from_native(thread, _thread_in_Java, true /* check_asyncs */);

  context->old_handles = thread->active_handles();

  // For the profiler, the last_Java_frame information in thread must always be in
  // legal state. We have no last Java frame if last_Java_sp == nullptr so
  // the valid transition is to clear _last_Java_sp and then reset the rest of
  // the (platform specific) state.

  context->jfa.copy(thread->frame_anchor());
  thread->frame_anchor()->clear();

  debug_only(thread->inc_java_call_counter());
  thread->set_active_handles(context->new_handles);     // install new handle block and reset Java frame linkage

  thread->set_vm_result(JNIHandles::resolve(receiver));

  return thread;
}

// modelled after JavaCallWrapper::~JavaCallWrapper
void UpcallLinker::on_exit(UpcallStub::FrameData* context) {
  JavaThread* thread = context->thread;
  assert(thread == JavaThread::current(), "must still be the same thread");

  // restore previous handle block
  thread->set_active_handles(context->old_handles);

  thread->frame_anchor()->zap();

  debug_only(thread->dec_java_call_counter());

  // Old thread-local info. has been restored. We are now back in native code.
  ThreadStateTransition::transition_from_java(thread, _thread_in_native);

  thread->frame_anchor()->copy(&context->jfa);

  // Release handles after we are marked as being in native code again, since this
  // operation might block
  JNIHandleBlock::release_block(context->new_handles, thread);

  assert(!thread->has_pending_exception(), "Upcall can not throw an exception");
}

void UpcallLinker::handle_uncaught_exception(oop exception) {
  ResourceMark rm;
  // Based on CATCH macro
  tty->print_cr("Uncaught exception:");
  exception->print();
  ShouldNotReachHere();
}

JVM_ENTRY(jlong, UL_MakeUpcallStub(JNIEnv *env, jclass unused, jobject mh, jobject abi, jobject conv,
                                                 jboolean needs_return_buffer, jlong ret_buf_size))
  ResourceMark rm(THREAD);
  Handle mh_h(THREAD, JNIHandles::resolve(mh));
  jobject mh_j = JNIHandles::make_global(mh_h);

  oop lform = java_lang_invoke_MethodHandle::form(mh_h());
  oop vmentry = java_lang_invoke_LambdaForm::vmentry(lform);
  Method* entry = java_lang_invoke_MemberName::vmtarget(vmentry);
  const methodHandle mh_entry(THREAD, entry);

  assert(entry->method_holder()->is_initialized(), "no clinit barrier");
  CompilationPolicy::compile_if_required(mh_entry, CHECK_0);

  assert(entry->is_static(), "static only");
  // Fill in the signature array, for the calling-convention call.
  const int total_out_args = entry->size_of_parameters();
  assert(total_out_args > 0, "receiver arg");

  BasicType* out_sig_bt = NEW_RESOURCE_ARRAY(BasicType, total_out_args);
  BasicType ret_type;
  {
    int i = 0;
    SignatureStream ss(entry->signature());
    for (; !ss.at_return_type(); ss.next()) {
      out_sig_bt[i++] = ss.type();  // Collect remaining bits of signature
      if (ss.type() == T_LONG || ss.type() == T_DOUBLE)
        out_sig_bt[i++] = T_VOID;   // Longs & doubles take 2 Java slots
    }
    assert(i == total_out_args, "");
    ret_type = ss.type();
  }

  return (jlong) UpcallLinker::make_upcall_stub(
    mh_j, entry, out_sig_bt, total_out_args, ret_type,
    abi, conv, needs_return_buffer, checked_cast<int>(ret_buf_size));
JVM_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod UL_methods[] = {
  {CC "makeUpcallStub", CC "(" "Ljava/lang/invoke/MethodHandle;" "L" FOREIGN_ABI "ABIDescriptor;" "L" FOREIGN_ABI "UpcallLinker$CallRegs;" "ZJ)J", FN_PTR(UL_MakeUpcallStub)},
};

/**
 * This one function is exported, used by NativeLookup.
 */
JNI_ENTRY(void, JVM_RegisterUpcallLinkerMethods(JNIEnv *env, jclass UL_class))
  ThreadToNativeFromVM ttnfv(thread);
  int status = env->RegisterNatives(UL_class, UL_methods, sizeof(UL_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.foreign.abi.UpcallLinker natives");
JNI_END
