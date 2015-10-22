/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/stackMapTable.hpp"
#include "classfile/stackMapFrame.hpp"
#include "classfile/stackMapTableFormat.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/verifier.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.hpp"
#include "prims/jvm.h"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "services/threadService.hpp"
#include "utilities/bytes.hpp"

#define NOFAILOVER_MAJOR_VERSION                       51
#define NONZERO_PADDING_BYTES_IN_SWITCH_MAJOR_VERSION  51
#define STATIC_METHOD_IN_INTERFACE_MAJOR_VERSION       52

// Access to external entry for VerifyClassCodes - old byte code verifier

extern "C" {
  typedef jboolean (*verify_byte_codes_fn_t)(JNIEnv *, jclass, char *, jint);
  typedef jboolean (*verify_byte_codes_fn_new_t)(JNIEnv *, jclass, char *, jint, jint);
}

static void* volatile _verify_byte_codes_fn = NULL;

static volatile jint _is_new_verify_byte_codes_fn = (jint) true;

static void* verify_byte_codes_fn() {
  if (_verify_byte_codes_fn == NULL) {
    void *lib_handle = os::native_java_library();
    void *func = os::dll_lookup(lib_handle, "VerifyClassCodesForMajorVersion");
    OrderAccess::release_store_ptr(&_verify_byte_codes_fn, func);
    if (func == NULL) {
      OrderAccess::release_store(&_is_new_verify_byte_codes_fn, false);
      func = os::dll_lookup(lib_handle, "VerifyClassCodes");
      OrderAccess::release_store_ptr(&_verify_byte_codes_fn, func);
    }
  }
  return (void*)_verify_byte_codes_fn;
}


// Methods in Verifier

bool Verifier::should_verify_for(oop class_loader, bool should_verify_class) {
  return (class_loader == NULL || !should_verify_class) ?
    BytecodeVerificationLocal : BytecodeVerificationRemote;
}

bool Verifier::relax_verify_for(oop loader) {
  bool trusted = java_lang_ClassLoader::is_trusted_loader(loader);
  bool need_verify =
    // verifyAll
    (BytecodeVerificationLocal && BytecodeVerificationRemote) ||
    // verifyRemote
    (!BytecodeVerificationLocal && BytecodeVerificationRemote && !trusted);
  return !need_verify;
}

void Verifier::trace_class_resolution(Klass* resolve_class, InstanceKlass* verify_class) {
  assert(verify_class != NULL, "Unexpected null verify_class");
  ResourceMark rm;
  Symbol* s = verify_class->source_file_name();
  const char* source_file = (s != NULL ? s->as_C_string() : NULL);
  const char* verify = verify_class->external_name();
  const char* resolve = resolve_class->external_name();
  // print in a single call to reduce interleaving between threads
  if (source_file != NULL) {
    tty->print("RESOLVE %s %s %s (verification)\n", verify, resolve, source_file);
  } else {
    tty->print("RESOLVE %s %s (verification)\n", verify, resolve);
  }
}

bool Verifier::verify(instanceKlassHandle klass, Verifier::Mode mode, bool should_verify_class, TRAPS) {
  HandleMark hm;
  ResourceMark rm(THREAD);

  // Eagerly allocate the identity hash code for a klass. This is a fallout
  // from 6320749 and 8059924: hash code generator is not supposed to be called
  // during the safepoint, but it allows to sneak the hashcode in during
  // verification. Without this eager hashcode generation, we may end up
  // installing the hashcode during some other operation, which may be at
  // safepoint -- blowing up the checks. It was previously done as the side
  // effect (sic!) for external_name(), but instead of doing that, we opt to
  // explicitly push the hashcode in here. This is signify the following block
  // is IMPORTANT:
  if (klass->java_mirror() != NULL) {
    klass->java_mirror()->identity_hash();
  }

  if (!is_eligible_for_verification(klass, should_verify_class)) {
    return true;
  }

  // Timer includes any side effects of class verification (resolution,
  // etc), but not recursive calls to Verifier::verify().
  JavaThread* jt = (JavaThread*)THREAD;
  PerfClassTraceTime timer(ClassLoader::perf_class_verify_time(),
                           ClassLoader::perf_class_verify_selftime(),
                           ClassLoader::perf_classes_verified(),
                           jt->get_thread_stat()->perf_recursion_counts_addr(),
                           jt->get_thread_stat()->perf_timers_addr(),
                           PerfClassTraceTime::CLASS_VERIFY);

  // If the class should be verified, first see if we can use the split
  // verifier.  If not, or if verification fails and FailOverToOldVerifier
  // is set, then call the inference verifier.

  Symbol* exception_name = NULL;
  const size_t message_buffer_len = klass->name()->utf8_length() + 1024;
  char* message_buffer = NEW_RESOURCE_ARRAY(char, message_buffer_len);
  char* exception_message = message_buffer;

  const char* klassName = klass->external_name();
  bool can_failover = FailOverToOldVerifier &&
     klass->major_version() < NOFAILOVER_MAJOR_VERSION;

  if (TraceClassInitialization) {
    tty->print_cr("Start class verification for: %s", klassName);
  }
  if (klass->major_version() >= STACKMAP_ATTRIBUTE_MAJOR_VERSION) {
    ClassVerifier split_verifier(klass, THREAD);
    split_verifier.verify_class(THREAD);
    exception_name = split_verifier.result();
    if (can_failover && !HAS_PENDING_EXCEPTION &&
        (exception_name == vmSymbols::java_lang_VerifyError() ||
         exception_name == vmSymbols::java_lang_ClassFormatError())) {
      if (TraceClassInitialization || VerboseVerification) {
        tty->print_cr(
          "Fail over class verification to old verifier for: %s", klassName);
      }
      exception_name = inference_verify(
        klass, message_buffer, message_buffer_len, THREAD);
    }
    if (exception_name != NULL) {
      exception_message = split_verifier.exception_message();
    }
  } else {
    exception_name = inference_verify(
        klass, message_buffer, message_buffer_len, THREAD);
  }

  if (TraceClassInitialization || VerboseVerification) {
    if (HAS_PENDING_EXCEPTION) {
      tty->print("Verification for %s has", klassName);
      tty->print_cr(" exception pending %s ",
        InstanceKlass::cast(PENDING_EXCEPTION->klass())->external_name());
    } else if (exception_name != NULL) {
      tty->print_cr("Verification for %s failed", klassName);
    }
    tty->print_cr("End class verification for: %s", klassName);
  }

  if (HAS_PENDING_EXCEPTION) {
    return false; // use the existing exception
  } else if (exception_name == NULL) {
    return true; // verifcation succeeded
  } else { // VerifyError or ClassFormatError to be created and thrown
    ResourceMark rm(THREAD);
    instanceKlassHandle kls =
      SystemDictionary::resolve_or_fail(exception_name, true, CHECK_false);
    if (TraceClassResolution) {
      Verifier::trace_class_resolution(kls(), klass());
    }

    while (!kls.is_null()) {
      if (kls == klass) {
        // If the class being verified is the exception we're creating
        // or one of it's superclasses, we're in trouble and are going
        // to infinitely recurse when we try to initialize the exception.
        // So bail out here by throwing the preallocated VM error.
        THROW_OOP_(Universe::virtual_machine_error_instance(), false);
      }
      kls = kls->super();
    }
    message_buffer[message_buffer_len - 1] = '\0'; // just to be sure
    THROW_MSG_(exception_name, exception_message, false);
  }
}

bool Verifier::is_eligible_for_verification(instanceKlassHandle klass, bool should_verify_class) {
  Symbol* name = klass->name();
  Klass* refl_magic_klass = SystemDictionary::reflect_MagicAccessorImpl_klass();

  bool is_reflect = refl_magic_klass != NULL && klass->is_subtype_of(refl_magic_klass);

  return (should_verify_for(klass->class_loader(), should_verify_class) &&
    // return if the class is a bootstrapping class
    // or defineClass specified not to verify by default (flags override passed arg)
    // We need to skip the following four for bootstraping
    name != vmSymbols::java_lang_Object() &&
    name != vmSymbols::java_lang_Class() &&
    name != vmSymbols::java_lang_String() &&
    name != vmSymbols::java_lang_Throwable() &&

    // Can not verify the bytecodes for shared classes because they have
    // already been rewritten to contain constant pool cache indices,
    // which the verifier can't understand.
    // Shared classes shouldn't have stackmaps either.
    !klass()->is_shared() &&

    // As of the fix for 4486457 we disable verification for all of the
    // dynamically-generated bytecodes associated with the 1.4
    // reflection implementation, not just those associated with
    // sun/reflect/SerializationConstructorAccessor.
    // NOTE: this is called too early in the bootstrapping process to be
    // guarded by Universe::is_gte_jdk14x_version().
    // Also for lambda generated code, gte jdk8
    (!is_reflect));
}

Symbol* Verifier::inference_verify(
    instanceKlassHandle klass, char* message, size_t message_len, TRAPS) {
  JavaThread* thread = (JavaThread*)THREAD;
  JNIEnv *env = thread->jni_environment();

  void* verify_func = verify_byte_codes_fn();

  if (verify_func == NULL) {
    jio_snprintf(message, message_len, "Could not link verifier");
    return vmSymbols::java_lang_VerifyError();
  }

  ResourceMark rm(THREAD);
  if (VerboseVerification) {
    tty->print_cr("Verifying class %s with old format", klass->external_name());
  }

  jclass cls = (jclass) JNIHandles::make_local(env, klass->java_mirror());
  jint result;

  {
    HandleMark hm(thread);
    ThreadToNativeFromVM ttn(thread);
    // ThreadToNativeFromVM takes care of changing thread_state, so safepoint
    // code knows that we have left the VM

    if (_is_new_verify_byte_codes_fn) {
      verify_byte_codes_fn_new_t func =
        CAST_TO_FN_PTR(verify_byte_codes_fn_new_t, verify_func);
      result = (*func)(env, cls, message, (int)message_len,
          klass->major_version());
    } else {
      verify_byte_codes_fn_t func =
        CAST_TO_FN_PTR(verify_byte_codes_fn_t, verify_func);
      result = (*func)(env, cls, message, (int)message_len);
    }
  }

  JNIHandles::destroy_local(cls);

  // These numbers are chosen so that VerifyClassCodes interface doesn't need
  // to be changed (still return jboolean (unsigned char)), and result is
  // 1 when verification is passed.
  if (result == 0) {
    return vmSymbols::java_lang_VerifyError();
  } else if (result == 1) {
    return NULL; // verified.
  } else if (result == 2) {
    THROW_MSG_(vmSymbols::java_lang_OutOfMemoryError(), message, NULL);
  } else if (result == 3) {
    return vmSymbols::java_lang_ClassFormatError();
  } else {
    ShouldNotReachHere();
    return NULL;
  }
}

TypeOrigin TypeOrigin::null() {
  return TypeOrigin();
}
TypeOrigin TypeOrigin::local(u2 index, StackMapFrame* frame) {
  assert(frame != NULL, "Must have a frame");
  return TypeOrigin(CF_LOCALS, index, StackMapFrame::copy(frame),
     frame->local_at(index));
}
TypeOrigin TypeOrigin::stack(u2 index, StackMapFrame* frame) {
  assert(frame != NULL, "Must have a frame");
  return TypeOrigin(CF_STACK, index, StackMapFrame::copy(frame),
      frame->stack_at(index));
}
TypeOrigin TypeOrigin::sm_local(u2 index, StackMapFrame* frame) {
  assert(frame != NULL, "Must have a frame");
  return TypeOrigin(SM_LOCALS, index, StackMapFrame::copy(frame),
      frame->local_at(index));
}
TypeOrigin TypeOrigin::sm_stack(u2 index, StackMapFrame* frame) {
  assert(frame != NULL, "Must have a frame");
  return TypeOrigin(SM_STACK, index, StackMapFrame::copy(frame),
      frame->stack_at(index));
}
TypeOrigin TypeOrigin::bad_index(u2 index) {
  return TypeOrigin(BAD_INDEX, index, NULL, VerificationType::bogus_type());
}
TypeOrigin TypeOrigin::cp(u2 index, VerificationType vt) {
  return TypeOrigin(CONST_POOL, index, NULL, vt);
}
TypeOrigin TypeOrigin::signature(VerificationType vt) {
  return TypeOrigin(SIG, 0, NULL, vt);
}
TypeOrigin TypeOrigin::implicit(VerificationType t) {
  return TypeOrigin(IMPLICIT, 0, NULL, t);
}
TypeOrigin TypeOrigin::frame(StackMapFrame* frame) {
  return TypeOrigin(FRAME_ONLY, 0, StackMapFrame::copy(frame),
                    VerificationType::bogus_type());
}

void TypeOrigin::reset_frame() {
  if (_frame != NULL) {
    _frame->restore();
  }
}

void TypeOrigin::details(outputStream* ss) const {
  _type.print_on(ss);
  switch (_origin) {
    case CF_LOCALS:
      ss->print(" (current frame, locals[%d])", _index);
      break;
    case CF_STACK:
      ss->print(" (current frame, stack[%d])", _index);
      break;
    case SM_LOCALS:
      ss->print(" (stack map, locals[%d])", _index);
      break;
    case SM_STACK:
      ss->print(" (stack map, stack[%d])", _index);
      break;
    case CONST_POOL:
      ss->print(" (constant pool %d)", _index);
      break;
    case SIG:
      ss->print(" (from method signature)");
      break;
    case IMPLICIT:
    case FRAME_ONLY:
    case NONE:
    default:
      ;
  }
}

#ifdef ASSERT
void TypeOrigin::print_on(outputStream* str) const {
  str->print("{%d,%d,%p:", _origin, _index, _frame);
  if (_frame != NULL) {
    _frame->print_on(str);
  } else {
    str->print("null");
  }
  str->print(",");
  _type.print_on(str);
  str->print("}");
}
#endif

void ErrorContext::details(outputStream* ss, const Method* method) const {
  if (is_valid()) {
    ss->cr();
    ss->print_cr("Exception Details:");
    location_details(ss, method);
    reason_details(ss);
    frame_details(ss);
    bytecode_details(ss, method);
    handler_details(ss, method);
    stackmap_details(ss, method);
  }
}

void ErrorContext::reason_details(outputStream* ss) const {
  streamIndentor si(ss);
  ss->indent().print_cr("Reason:");
  streamIndentor si2(ss);
  ss->indent().print("%s", "");
  switch (_fault) {
    case INVALID_BYTECODE:
      ss->print("Error exists in the bytecode");
      break;
    case WRONG_TYPE:
      if (_expected.is_valid()) {
        ss->print("Type ");
        _type.details(ss);
        ss->print(" is not assignable to ");
        _expected.details(ss);
      } else {
        ss->print("Invalid type: ");
        _type.details(ss);
      }
      break;
    case FLAGS_MISMATCH:
      if (_expected.is_valid()) {
        ss->print("Current frame's flags are not assignable "
                  "to stack map frame's.");
      } else {
        ss->print("Current frame's flags are invalid in this context.");
      }
      break;
    case BAD_CP_INDEX:
      ss->print("Constant pool index %d is invalid", _type.index());
      break;
    case BAD_LOCAL_INDEX:
      ss->print("Local index %d is invalid", _type.index());
      break;
    case LOCALS_SIZE_MISMATCH:
      ss->print("Current frame's local size doesn't match stackmap.");
      break;
    case STACK_SIZE_MISMATCH:
      ss->print("Current frame's stack size doesn't match stackmap.");
      break;
    case STACK_OVERFLOW:
      ss->print("Exceeded max stack size.");
      break;
    case STACK_UNDERFLOW:
      ss->print("Attempt to pop empty stack.");
      break;
    case MISSING_STACKMAP:
      ss->print("Expected stackmap frame at this location.");
      break;
    case BAD_STACKMAP:
      ss->print("Invalid stackmap specification.");
      break;
    case UNKNOWN:
    default:
      ShouldNotReachHere();
      ss->print_cr("Unknown");
  }
  ss->cr();
}

void ErrorContext::location_details(outputStream* ss, const Method* method) const {
  if (_bci != -1 && method != NULL) {
    streamIndentor si(ss);
    const char* bytecode_name = "<invalid>";
    if (method->validate_bci(_bci) != -1) {
      Bytecodes::Code code = Bytecodes::code_or_bp_at(method->bcp_from(_bci));
      if (Bytecodes::is_defined(code)) {
          bytecode_name = Bytecodes::name(code);
      } else {
          bytecode_name = "<illegal>";
      }
    }
    InstanceKlass* ik = method->method_holder();
    ss->indent().print_cr("Location:");
    streamIndentor si2(ss);
    ss->indent().print_cr("%s.%s%s @%d: %s",
        ik->name()->as_C_string(), method->name()->as_C_string(),
        method->signature()->as_C_string(), _bci, bytecode_name);
  }
}

void ErrorContext::frame_details(outputStream* ss) const {
  streamIndentor si(ss);
  if (_type.is_valid() && _type.frame() != NULL) {
    ss->indent().print_cr("Current Frame:");
    streamIndentor si2(ss);
    _type.frame()->print_on(ss);
  }
  if (_expected.is_valid() && _expected.frame() != NULL) {
    ss->indent().print_cr("Stackmap Frame:");
    streamIndentor si2(ss);
    _expected.frame()->print_on(ss);
  }
}

void ErrorContext::bytecode_details(outputStream* ss, const Method* method) const {
  if (method != NULL) {
    streamIndentor si(ss);
    ss->indent().print_cr("Bytecode:");
    streamIndentor si2(ss);
    ss->print_data(method->code_base(), method->code_size(), false);
  }
}

void ErrorContext::handler_details(outputStream* ss, const Method* method) const {
  if (method != NULL) {
    streamIndentor si(ss);
    ExceptionTable table(method);
    if (table.length() > 0) {
      ss->indent().print_cr("Exception Handler Table:");
      streamIndentor si2(ss);
      for (int i = 0; i < table.length(); ++i) {
        ss->indent().print_cr("bci [%d, %d] => handler: %d", table.start_pc(i),
            table.end_pc(i), table.handler_pc(i));
      }
    }
  }
}

void ErrorContext::stackmap_details(outputStream* ss, const Method* method) const {
  if (method != NULL && method->has_stackmap_table()) {
    streamIndentor si(ss);
    ss->indent().print_cr("Stackmap Table:");
    Array<u1>* data = method->stackmap_data();
    stack_map_table* sm_table =
        stack_map_table::at((address)data->adr_at(0));
    stack_map_frame* sm_frame = sm_table->entries();
    streamIndentor si2(ss);
    int current_offset = -1;
    for (u2 i = 0; i < sm_table->number_of_entries(); ++i) {
      ss->indent();
      sm_frame->print_on(ss, current_offset);
      ss->cr();
      current_offset += sm_frame->offset_delta();
      sm_frame = sm_frame->next();
    }
  }
}

// Methods in ClassVerifier

ClassVerifier::ClassVerifier(
    instanceKlassHandle klass, TRAPS)
    : _thread(THREAD), _exception_type(NULL), _message(NULL), _klass(klass) {
  _this_type = VerificationType::reference_type(klass->name());
  // Create list to hold symbols in reference area.
  _symbols = new GrowableArray<Symbol*>(100, 0, NULL);
}

ClassVerifier::~ClassVerifier() {
  // Decrement the reference count for any symbols created.
  for (int i = 0; i < _symbols->length(); i++) {
    Symbol* s = _symbols->at(i);
    s->decrement_refcount();
  }
}

VerificationType ClassVerifier::object_type() const {
  return VerificationType::reference_type(vmSymbols::java_lang_Object());
}

TypeOrigin ClassVerifier::ref_ctx(const char* sig, TRAPS) {
  VerificationType vt = VerificationType::reference_type(
      create_temporary_symbol(sig, (int)strlen(sig), THREAD));
  return TypeOrigin::implicit(vt);
}

void ClassVerifier::verify_class(TRAPS) {
  if (VerboseVerification) {
    tty->print_cr("Verifying class %s with new format",
      _klass->external_name());
  }

  Array<Method*>* methods = _klass->methods();
  int num_methods = methods->length();

  for (int index = 0; index < num_methods; index++) {
    // Check for recursive re-verification before each method.
    if (was_recursively_verified())  return;

    Method* m = methods->at(index);
    if (m->is_native() || m->is_abstract() || m->is_overpass()) {
      // If m is native or abstract, skip it.  It is checked in class file
      // parser that methods do not override a final method.  Overpass methods
      // are trusted since the VM generates them.
      continue;
    }
    verify_method(methodHandle(THREAD, m), CHECK_VERIFY(this));
  }

  if (VerboseVerification || TraceClassInitialization) {
    if (was_recursively_verified())
      tty->print_cr("Recursive verification detected for: %s",
          _klass->external_name());
  }
}

void ClassVerifier::verify_method(methodHandle m, TRAPS) {
  HandleMark hm(THREAD);
  _method = m;   // initialize _method
  if (VerboseVerification) {
    tty->print_cr("Verifying method %s", m->name_and_sig_as_C_string());
  }

// For clang, the only good constant format string is a literal constant format string.
#define bad_type_msg "Bad type on operand stack in %s"

  int32_t max_stack = m->verifier_max_stack();
  int32_t max_locals = m->max_locals();
  constantPoolHandle cp(THREAD, m->constants());

  if (!SignatureVerifier::is_valid_method_signature(m->signature())) {
    class_format_error("Invalid method signature");
    return;
  }

  // Initial stack map frame: offset is 0, stack is initially empty.
  StackMapFrame current_frame(max_locals, max_stack, this);
  // Set initial locals
  VerificationType return_type = current_frame.set_locals_from_arg(
    m, current_type(), CHECK_VERIFY(this));

  int32_t stackmap_index = 0; // index to the stackmap array

  u4 code_length = m->code_size();

  // Scan the bytecode and map each instruction's start offset to a number.
  char* code_data = generate_code_data(m, code_length, CHECK_VERIFY(this));

  int ex_min = code_length;
  int ex_max = -1;
  // Look through each item on the exception table. Each of the fields must refer
  // to a legal instruction.
  verify_exception_handler_table(
    code_length, code_data, ex_min, ex_max, CHECK_VERIFY(this));

  // Look through each entry on the local variable table and make sure
  // its range of code array offsets is valid. (4169817)
  if (m->has_localvariable_table()) {
    verify_local_variable_table(code_length, code_data, CHECK_VERIFY(this));
  }

  Array<u1>* stackmap_data = m->stackmap_data();
  StackMapStream stream(stackmap_data);
  StackMapReader reader(this, &stream, code_data, code_length, THREAD);
  StackMapTable stackmap_table(&reader, &current_frame, max_locals, max_stack,
                               code_data, code_length, CHECK_VERIFY(this));

  if (VerboseVerification) {
    stackmap_table.print_on(tty);
  }

  RawBytecodeStream bcs(m);

  // Scan the byte code linearly from the start to the end
  bool no_control_flow = false; // Set to true when there is no direct control
                                // flow from current instruction to the next
                                // instruction in sequence

  Bytecodes::Code opcode;
  while (!bcs.is_last_bytecode()) {
    // Check for recursive re-verification before each bytecode.
    if (was_recursively_verified())  return;

    opcode = bcs.raw_next();
    u2 bci = bcs.bci();

    // Set current frame's offset to bci
    current_frame.set_offset(bci);
    current_frame.set_mark();

    // Make sure every offset in stackmap table point to the beginning to
    // an instruction. Match current_frame to stackmap_table entry with
    // the same offset if exists.
    stackmap_index = verify_stackmap_table(
      stackmap_index, bci, &current_frame, &stackmap_table,
      no_control_flow, CHECK_VERIFY(this));


    bool this_uninit = false;  // Set to true when invokespecial <init> initialized 'this'
    bool verified_exc_handlers = false;

    // Merge with the next instruction
    {
      u2 index;
      int target;
      VerificationType type, type2;
      VerificationType atype;

#ifndef PRODUCT
      if (VerboseVerification) {
        current_frame.print_on(tty);
        tty->print_cr("offset = %d,  opcode = %s", bci, Bytecodes::name(opcode));
      }
#endif

      // Make sure wide instruction is in correct format
      if (bcs.is_wide()) {
        if (opcode != Bytecodes::_iinc   && opcode != Bytecodes::_iload  &&
            opcode != Bytecodes::_aload  && opcode != Bytecodes::_lload  &&
            opcode != Bytecodes::_istore && opcode != Bytecodes::_astore &&
            opcode != Bytecodes::_lstore && opcode != Bytecodes::_fload  &&
            opcode != Bytecodes::_dload  && opcode != Bytecodes::_fstore &&
            opcode != Bytecodes::_dstore) {
          /* Unreachable?  RawBytecodeStream's raw_next() returns 'illegal'
           * if we encounter a wide instruction that modifies an invalid
           * opcode (not one of the ones listed above) */
          verify_error(ErrorContext::bad_code(bci), "Bad wide instruction");
          return;
        }
      }

      // Look for possible jump target in exception handlers and see if it
      // matches current_frame.  Do this check here for astore*, dstore*,
      // fstore*, istore*, and lstore* opcodes because they can change the type
      // state by adding a local.  JVM Spec says that the incoming type state
      // should be used for this check.  So, do the check here before a possible
      // local is added to the type state.
      if (Bytecodes::is_store_into_local(opcode) && bci >= ex_min && bci < ex_max) {
        verify_exception_handler_targets(
          bci, this_uninit, &current_frame, &stackmap_table, CHECK_VERIFY(this));
        verified_exc_handlers = true;
      }

      switch (opcode) {
        case Bytecodes::_nop :
          no_control_flow = false; break;
        case Bytecodes::_aconst_null :
          current_frame.push_stack(
            VerificationType::null_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_iconst_m1 :
        case Bytecodes::_iconst_0 :
        case Bytecodes::_iconst_1 :
        case Bytecodes::_iconst_2 :
        case Bytecodes::_iconst_3 :
        case Bytecodes::_iconst_4 :
        case Bytecodes::_iconst_5 :
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_lconst_0 :
        case Bytecodes::_lconst_1 :
          current_frame.push_stack_2(
            VerificationType::long_type(),
            VerificationType::long2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_fconst_0 :
        case Bytecodes::_fconst_1 :
        case Bytecodes::_fconst_2 :
          current_frame.push_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dconst_0 :
        case Bytecodes::_dconst_1 :
          current_frame.push_stack_2(
            VerificationType::double_type(),
            VerificationType::double2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_sipush :
        case Bytecodes::_bipush :
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_ldc :
          verify_ldc(
            opcode, bcs.get_index_u1(), &current_frame,
            cp, bci, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_ldc_w :
        case Bytecodes::_ldc2_w :
          verify_ldc(
            opcode, bcs.get_index_u2(), &current_frame,
            cp, bci, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_iload :
          verify_iload(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_iload_0 :
        case Bytecodes::_iload_1 :
        case Bytecodes::_iload_2 :
        case Bytecodes::_iload_3 :
          index = opcode - Bytecodes::_iload_0;
          verify_iload(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_lload :
          verify_lload(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_lload_0 :
        case Bytecodes::_lload_1 :
        case Bytecodes::_lload_2 :
        case Bytecodes::_lload_3 :
          index = opcode - Bytecodes::_lload_0;
          verify_lload(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_fload :
          verify_fload(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_fload_0 :
        case Bytecodes::_fload_1 :
        case Bytecodes::_fload_2 :
        case Bytecodes::_fload_3 :
          index = opcode - Bytecodes::_fload_0;
          verify_fload(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dload :
          verify_dload(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dload_0 :
        case Bytecodes::_dload_1 :
        case Bytecodes::_dload_2 :
        case Bytecodes::_dload_3 :
          index = opcode - Bytecodes::_dload_0;
          verify_dload(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_aload :
          verify_aload(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_aload_0 :
        case Bytecodes::_aload_1 :
        case Bytecodes::_aload_2 :
        case Bytecodes::_aload_3 :
          index = opcode - Bytecodes::_aload_0;
          verify_aload(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_iaload :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_int_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[I", THREAD)),
                bad_type_msg, "iaload");
            return;
          }
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_baload :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_bool_array() && !atype.is_byte_array()) {
            verify_error(
                ErrorContext::bad_type(bci, current_frame.stack_top_ctx()),
                bad_type_msg, "baload");
            return;
          }
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_caload :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_char_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[C", THREAD)),
                bad_type_msg, "caload");
            return;
          }
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_saload :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_short_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[S", THREAD)),
                bad_type_msg, "saload");
            return;
          }
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_laload :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_long_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[J", THREAD)),
                bad_type_msg, "laload");
            return;
          }
          current_frame.push_stack_2(
            VerificationType::long_type(),
            VerificationType::long2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_faload :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_float_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[F", THREAD)),
                bad_type_msg, "faload");
            return;
          }
          current_frame.push_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_daload :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_double_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[D", THREAD)),
                bad_type_msg, "daload");
            return;
          }
          current_frame.push_stack_2(
            VerificationType::double_type(),
            VerificationType::double2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_aaload : {
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_reference_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(),
                TypeOrigin::implicit(VerificationType::reference_check())),
                bad_type_msg, "aaload");
            return;
          }
          if (atype.is_null()) {
            current_frame.push_stack(
              VerificationType::null_type(), CHECK_VERIFY(this));
          } else {
            VerificationType component =
              atype.get_component(this, CHECK_VERIFY(this));
            current_frame.push_stack(component, CHECK_VERIFY(this));
          }
          no_control_flow = false; break;
        }
        case Bytecodes::_istore :
          verify_istore(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_istore_0 :
        case Bytecodes::_istore_1 :
        case Bytecodes::_istore_2 :
        case Bytecodes::_istore_3 :
          index = opcode - Bytecodes::_istore_0;
          verify_istore(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_lstore :
          verify_lstore(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_lstore_0 :
        case Bytecodes::_lstore_1 :
        case Bytecodes::_lstore_2 :
        case Bytecodes::_lstore_3 :
          index = opcode - Bytecodes::_lstore_0;
          verify_lstore(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_fstore :
          verify_fstore(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_fstore_0 :
        case Bytecodes::_fstore_1 :
        case Bytecodes::_fstore_2 :
        case Bytecodes::_fstore_3 :
          index = opcode - Bytecodes::_fstore_0;
          verify_fstore(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dstore :
          verify_dstore(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dstore_0 :
        case Bytecodes::_dstore_1 :
        case Bytecodes::_dstore_2 :
        case Bytecodes::_dstore_3 :
          index = opcode - Bytecodes::_dstore_0;
          verify_dstore(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_astore :
          verify_astore(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_astore_0 :
        case Bytecodes::_astore_1 :
        case Bytecodes::_astore_2 :
        case Bytecodes::_astore_3 :
          index = opcode - Bytecodes::_astore_0;
          verify_astore(index, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_iastore :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          type2 = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_int_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[I", THREAD)),
                bad_type_msg, "iastore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_bastore :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          type2 = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_bool_array() && !atype.is_byte_array()) {
            verify_error(
                ErrorContext::bad_type(bci, current_frame.stack_top_ctx()),
                bad_type_msg, "bastore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_castore :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_char_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[C", THREAD)),
                bad_type_msg, "castore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_sastore :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_short_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[S", THREAD)),
                bad_type_msg, "sastore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_lastore :
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_long_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[J", THREAD)),
                bad_type_msg, "lastore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_fastore :
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          current_frame.pop_stack
            (VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_float_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[F", THREAD)),
                bad_type_msg, "fastore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_dastore :
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!atype.is_double_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(), ref_ctx("[D", THREAD)),
                bad_type_msg, "dastore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_aastore :
          type = current_frame.pop_stack(object_type(), CHECK_VERIFY(this));
          type2 = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          // more type-checking is done at runtime
          if (!atype.is_reference_array()) {
            verify_error(ErrorContext::bad_type(bci,
                current_frame.stack_top_ctx(),
                TypeOrigin::implicit(VerificationType::reference_check())),
                bad_type_msg, "aastore");
            return;
          }
          // 4938384: relaxed constraint in JVMS 3nd edition.
          no_control_flow = false; break;
        case Bytecodes::_pop :
          current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_pop2 :
          type = current_frame.pop_stack(CHECK_VERIFY(this));
          if (type.is_category1()) {
            current_frame.pop_stack(
              VerificationType::category1_check(), CHECK_VERIFY(this));
          } else if (type.is_category2_2nd()) {
            current_frame.pop_stack(
              VerificationType::category2_check(), CHECK_VERIFY(this));
          } else {
            /* Unreachable? Would need a category2_1st on TOS
             * which does not appear possible. */
            verify_error(
                ErrorContext::bad_type(bci, current_frame.stack_top_ctx()),
                bad_type_msg, "pop2");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_dup :
          type = current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dup_x1 :
          type = current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          type2 = current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dup_x2 :
        {
          VerificationType type3;
          type = current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          type2 = current_frame.pop_stack(CHECK_VERIFY(this));
          if (type2.is_category1()) {
            type3 = current_frame.pop_stack(
              VerificationType::category1_check(), CHECK_VERIFY(this));
          } else if (type2.is_category2_2nd()) {
            type3 = current_frame.pop_stack(
              VerificationType::category2_check(), CHECK_VERIFY(this));
          } else {
            /* Unreachable? Would need a category2_1st at stack depth 2 with
             * a category1 on TOS which does not appear possible. */
            verify_error(ErrorContext::bad_type(
                bci, current_frame.stack_top_ctx()), bad_type_msg, "dup_x2");
            return;
          }
          current_frame.push_stack(type, CHECK_VERIFY(this));
          current_frame.push_stack(type3, CHECK_VERIFY(this));
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_dup2 :
          type = current_frame.pop_stack(CHECK_VERIFY(this));
          if (type.is_category1()) {
            type2 = current_frame.pop_stack(
              VerificationType::category1_check(), CHECK_VERIFY(this));
          } else if (type.is_category2_2nd()) {
            type2 = current_frame.pop_stack(
              VerificationType::category2_check(), CHECK_VERIFY(this));
          } else {
            /* Unreachable?  Would need a category2_1st on TOS which does not
             * appear possible. */
            verify_error(
                ErrorContext::bad_type(bci, current_frame.stack_top_ctx()),
                bad_type_msg, "dup2");
            return;
          }
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dup2_x1 :
        {
          VerificationType type3;
          type = current_frame.pop_stack(CHECK_VERIFY(this));
          if (type.is_category1()) {
            type2 = current_frame.pop_stack(
              VerificationType::category1_check(), CHECK_VERIFY(this));
          } else if (type.is_category2_2nd()) {
            type2 = current_frame.pop_stack(
              VerificationType::category2_check(), CHECK_VERIFY(this));
          } else {
            /* Unreachable?  Would need a category2_1st on TOS which does
             * not appear possible. */
            verify_error(
                ErrorContext::bad_type(bci, current_frame.stack_top_ctx()),
                bad_type_msg, "dup2_x1");
            return;
          }
          type3 = current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          current_frame.push_stack(type3, CHECK_VERIFY(this));
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_dup2_x2 :
        {
          VerificationType type3, type4;
          type = current_frame.pop_stack(CHECK_VERIFY(this));
          if (type.is_category1()) {
            type2 = current_frame.pop_stack(
              VerificationType::category1_check(), CHECK_VERIFY(this));
          } else if (type.is_category2_2nd()) {
            type2 = current_frame.pop_stack(
              VerificationType::category2_check(), CHECK_VERIFY(this));
          } else {
            /* Unreachable?  Would need a category2_1st on TOS which does
             * not appear possible. */
            verify_error(
                ErrorContext::bad_type(bci, current_frame.stack_top_ctx()),
                bad_type_msg, "dup2_x2");
            return;
          }
          type3 = current_frame.pop_stack(CHECK_VERIFY(this));
          if (type3.is_category1()) {
            type4 = current_frame.pop_stack(
              VerificationType::category1_check(), CHECK_VERIFY(this));
          } else if (type3.is_category2_2nd()) {
            type4 = current_frame.pop_stack(
              VerificationType::category2_check(), CHECK_VERIFY(this));
          } else {
            /* Unreachable?  Would need a category2_1st on TOS after popping
             * a long/double or two category 1's, which does not
             * appear possible. */
            verify_error(
                ErrorContext::bad_type(bci, current_frame.stack_top_ctx()),
                bad_type_msg, "dup2_x2");
            return;
          }
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          current_frame.push_stack(type4, CHECK_VERIFY(this));
          current_frame.push_stack(type3, CHECK_VERIFY(this));
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_swap :
          type = current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          type2 = current_frame.pop_stack(
            VerificationType::category1_check(), CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          current_frame.push_stack(type2, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_iadd :
        case Bytecodes::_isub :
        case Bytecodes::_imul :
        case Bytecodes::_idiv :
        case Bytecodes::_irem :
        case Bytecodes::_ishl :
        case Bytecodes::_ishr :
        case Bytecodes::_iushr :
        case Bytecodes::_ior :
        case Bytecodes::_ixor :
        case Bytecodes::_iand :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          // fall through
        case Bytecodes::_ineg :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_ladd :
        case Bytecodes::_lsub :
        case Bytecodes::_lmul :
        case Bytecodes::_ldiv :
        case Bytecodes::_lrem :
        case Bytecodes::_land :
        case Bytecodes::_lor :
        case Bytecodes::_lxor :
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          // fall through
        case Bytecodes::_lneg :
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::long_type(),
            VerificationType::long2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_lshl :
        case Bytecodes::_lshr :
        case Bytecodes::_lushr :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::long_type(),
            VerificationType::long2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_fadd :
        case Bytecodes::_fsub :
        case Bytecodes::_fmul :
        case Bytecodes::_fdiv :
        case Bytecodes::_frem :
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          // fall through
        case Bytecodes::_fneg :
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dadd :
        case Bytecodes::_dsub :
        case Bytecodes::_dmul :
        case Bytecodes::_ddiv :
        case Bytecodes::_drem :
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          // fall through
        case Bytecodes::_dneg :
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::double_type(),
            VerificationType::double2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_iinc :
          verify_iinc(bcs.get_index(), &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_i2l :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::long_type(),
            VerificationType::long2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
       case Bytecodes::_l2i :
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_i2f :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_i2d :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::double_type(),
            VerificationType::double2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_l2f :
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_l2d :
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::double_type(),
            VerificationType::double2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_f2i :
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_f2l :
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::long_type(),
            VerificationType::long2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_f2d :
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::double_type(),
            VerificationType::double2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_d2i :
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_d2l :
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          current_frame.push_stack_2(
            VerificationType::long_type(),
            VerificationType::long2_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_d2f :
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_i2b :
        case Bytecodes::_i2c :
        case Bytecodes::_i2s :
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_lcmp :
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.pop_stack_2(
            VerificationType::long2_type(),
            VerificationType::long_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_fcmpl :
        case Bytecodes::_fcmpg :
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_dcmpl :
        case Bytecodes::_dcmpg :
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          current_frame.pop_stack_2(
            VerificationType::double2_type(),
            VerificationType::double_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_if_icmpeq:
        case Bytecodes::_if_icmpne:
        case Bytecodes::_if_icmplt:
        case Bytecodes::_if_icmpge:
        case Bytecodes::_if_icmpgt:
        case Bytecodes::_if_icmple:
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          // fall through
        case Bytecodes::_ifeq:
        case Bytecodes::_ifne:
        case Bytecodes::_iflt:
        case Bytecodes::_ifge:
        case Bytecodes::_ifgt:
        case Bytecodes::_ifle:
          current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          target = bcs.dest();
          stackmap_table.check_jump_target(
            &current_frame, target, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_if_acmpeq :
        case Bytecodes::_if_acmpne :
          current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          // fall through
        case Bytecodes::_ifnull :
        case Bytecodes::_ifnonnull :
          current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          target = bcs.dest();
          stackmap_table.check_jump_target
            (&current_frame, target, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_goto :
          target = bcs.dest();
          stackmap_table.check_jump_target(
            &current_frame, target, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_goto_w :
          target = bcs.dest_w();
          stackmap_table.check_jump_target(
            &current_frame, target, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_tableswitch :
        case Bytecodes::_lookupswitch :
          verify_switch(
            &bcs, code_length, code_data, &current_frame,
            &stackmap_table, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_ireturn :
          type = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci,
                              &current_frame, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_lreturn :
          type2 = current_frame.pop_stack(
            VerificationType::long2_type(), CHECK_VERIFY(this));
          type = current_frame.pop_stack(
            VerificationType::long_type(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci,
                              &current_frame, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_freturn :
          type = current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci,
                              &current_frame, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_dreturn :
          type2 = current_frame.pop_stack(
            VerificationType::double2_type(),  CHECK_VERIFY(this));
          type = current_frame.pop_stack(
            VerificationType::double_type(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci,
                              &current_frame, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_areturn :
          type = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci,
                              &current_frame, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_return :
          if (return_type != VerificationType::bogus_type()) {
            verify_error(ErrorContext::bad_code(bci),
                         "Method expects a return value");
            return;
          }
          // Make sure "this" has been initialized if current method is an
          // <init>.
          if (_method->name() == vmSymbols::object_initializer_name() &&
              current_frame.flag_this_uninit()) {
            verify_error(ErrorContext::bad_code(bci),
                         "Constructor must call super() or this() "
                         "before return");
            return;
          }
          no_control_flow = true; break;
        case Bytecodes::_getstatic :
        case Bytecodes::_putstatic :
          // pass TRUE, operand can be an array type for getstatic/putstatic.
          verify_field_instructions(
            &bcs, &current_frame, cp, true, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_getfield :
        case Bytecodes::_putfield :
          // pass FALSE, operand can't be an array type for getfield/putfield.
          verify_field_instructions(
            &bcs, &current_frame, cp, false, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_invokevirtual :
        case Bytecodes::_invokespecial :
        case Bytecodes::_invokestatic :
          verify_invoke_instructions(
            &bcs, code_length, &current_frame, (bci >= ex_min && bci < ex_max),
            &this_uninit, return_type, cp, &stackmap_table, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_invokeinterface :
        case Bytecodes::_invokedynamic :
          verify_invoke_instructions(
            &bcs, code_length, &current_frame, (bci >= ex_min && bci < ex_max),
            &this_uninit, return_type, cp, &stackmap_table, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_new :
        {
          index = bcs.get_index_u2();
          verify_cp_class_type(bci, index, cp, CHECK_VERIFY(this));
          VerificationType new_class_type =
            cp_index_to_type(index, cp, CHECK_VERIFY(this));
          if (!new_class_type.is_object()) {
            verify_error(ErrorContext::bad_type(bci,
                TypeOrigin::cp(index, new_class_type)),
                "Illegal new instruction");
            return;
          }
          type = VerificationType::uninitialized_type(bci);
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_newarray :
          type = get_newarray_type(bcs.get_index(), bci, CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::integer_type(),  CHECK_VERIFY(this));
          current_frame.push_stack(type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_anewarray :
          verify_anewarray(
            bci, bcs.get_index_u2(), cp, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_arraylength :
          type = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!(type.is_null() || type.is_array())) {
            verify_error(ErrorContext::bad_type(
                bci, current_frame.stack_top_ctx()),
                bad_type_msg, "arraylength");
          }
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_checkcast :
        {
          index = bcs.get_index_u2();
          verify_cp_class_type(bci, index, cp, CHECK_VERIFY(this));
          current_frame.pop_stack(object_type(), CHECK_VERIFY(this));
          VerificationType klass_type = cp_index_to_type(
            index, cp, CHECK_VERIFY(this));
          current_frame.push_stack(klass_type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_instanceof : {
          index = bcs.get_index_u2();
          verify_cp_class_type(bci, index, cp, CHECK_VERIFY(this));
          current_frame.pop_stack(object_type(), CHECK_VERIFY(this));
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_monitorenter :
        case Bytecodes::_monitorexit :
          current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_multianewarray :
        {
          index = bcs.get_index_u2();
          u2 dim = *(bcs.bcp()+3);
          verify_cp_class_type(bci, index, cp, CHECK_VERIFY(this));
          VerificationType new_array_type =
            cp_index_to_type(index, cp, CHECK_VERIFY(this));
          if (!new_array_type.is_array()) {
            verify_error(ErrorContext::bad_type(bci,
                TypeOrigin::cp(index, new_array_type)),
                "Illegal constant pool index in multianewarray instruction");
            return;
          }
          if (dim < 1 || new_array_type.dimensions() < dim) {
            verify_error(ErrorContext::bad_code(bci),
                "Illegal dimension in multianewarray instruction: %d", dim);
            return;
          }
          for (int i = 0; i < dim; i++) {
            current_frame.pop_stack(
              VerificationType::integer_type(), CHECK_VERIFY(this));
          }
          current_frame.push_stack(new_array_type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_athrow :
          type = VerificationType::reference_type(
            vmSymbols::java_lang_Throwable());
          current_frame.pop_stack(type, CHECK_VERIFY(this));
          no_control_flow = true; break;
        default:
          // We only need to check the valid bytecodes in class file.
          // And jsr and ret are not in the new class file format in JDK1.5.
          verify_error(ErrorContext::bad_code(bci),
              "Bad instruction: %02x", opcode);
          no_control_flow = false;
          return;
      }  // end switch
    }  // end Merge with the next instruction

    // Look for possible jump target in exception handlers and see if it matches
    // current_frame.  Don't do this check if it has already been done (for
    // ([a,d,f,i,l]store* opcodes).  This check cannot be done earlier because
    // opcodes, such as invokespecial, may set the this_uninit flag.
    assert(!(verified_exc_handlers && this_uninit),
      "Exception handler targets got verified before this_uninit got set");
    if (!verified_exc_handlers && bci >= ex_min && bci < ex_max) {
      verify_exception_handler_targets(
        bci, this_uninit, &current_frame, &stackmap_table, CHECK_VERIFY(this));
    }
  } // end while

  // Make sure that control flow does not fall through end of the method
  if (!no_control_flow) {
    verify_error(ErrorContext::bad_code(code_length),
        "Control flow falls through code end");
    return;
  }
}

#undef bad_type_message

char* ClassVerifier::generate_code_data(methodHandle m, u4 code_length, TRAPS) {
  char* code_data = NEW_RESOURCE_ARRAY(char, code_length);
  memset(code_data, 0, sizeof(char) * code_length);
  RawBytecodeStream bcs(m);

  while (!bcs.is_last_bytecode()) {
    if (bcs.raw_next() != Bytecodes::_illegal) {
      int bci = bcs.bci();
      if (bcs.raw_code() == Bytecodes::_new) {
        code_data[bci] = NEW_OFFSET;
      } else {
        code_data[bci] = BYTECODE_OFFSET;
      }
    } else {
      verify_error(ErrorContext::bad_code(bcs.bci()), "Bad instruction");
      return NULL;
    }
  }

  return code_data;
}

void ClassVerifier::verify_exception_handler_table(u4 code_length, char* code_data, int& min, int& max, TRAPS) {
  ExceptionTable exhandlers(_method());
  int exlength = exhandlers.length();
  constantPoolHandle cp (THREAD, _method->constants());

  for(int i = 0; i < exlength; i++) {
    u2 start_pc = exhandlers.start_pc(i);
    u2 end_pc = exhandlers.end_pc(i);
    u2 handler_pc = exhandlers.handler_pc(i);
    if (start_pc >= code_length || code_data[start_pc] == 0) {
      class_format_error("Illegal exception table start_pc %d", start_pc);
      return;
    }
    if (end_pc != code_length) {   // special case: end_pc == code_length
      if (end_pc > code_length || code_data[end_pc] == 0) {
        class_format_error("Illegal exception table end_pc %d", end_pc);
        return;
      }
    }
    if (handler_pc >= code_length || code_data[handler_pc] == 0) {
      class_format_error("Illegal exception table handler_pc %d", handler_pc);
      return;
    }
    int catch_type_index = exhandlers.catch_type_index(i);
    if (catch_type_index != 0) {
      VerificationType catch_type = cp_index_to_type(
        catch_type_index, cp, CHECK_VERIFY(this));
      VerificationType throwable =
        VerificationType::reference_type(vmSymbols::java_lang_Throwable());
      bool is_subclass = throwable.is_assignable_from(
        catch_type, this, false, CHECK_VERIFY(this));
      if (!is_subclass) {
        // 4286534: should throw VerifyError according to recent spec change
        verify_error(ErrorContext::bad_type(handler_pc,
            TypeOrigin::cp(catch_type_index, catch_type),
            TypeOrigin::implicit(throwable)),
            "Catch type is not a subclass "
            "of Throwable in exception handler %d", handler_pc);
        return;
      }
    }
    if (start_pc < min) min = start_pc;
    if (end_pc > max) max = end_pc;
  }
}

void ClassVerifier::verify_local_variable_table(u4 code_length, char* code_data, TRAPS) {
  int localvariable_table_length = _method()->localvariable_table_length();
  if (localvariable_table_length > 0) {
    LocalVariableTableElement* table = _method()->localvariable_table_start();
    for (int i = 0; i < localvariable_table_length; i++) {
      u2 start_bci = table[i].start_bci;
      u2 length = table[i].length;

      if (start_bci >= code_length || code_data[start_bci] == 0) {
        class_format_error(
          "Illegal local variable table start_pc %d", start_bci);
        return;
      }
      u4 end_bci = (u4)(start_bci + length);
      if (end_bci != code_length) {
        if (end_bci >= code_length || code_data[end_bci] == 0) {
          class_format_error( "Illegal local variable table length %d", length);
          return;
        }
      }
    }
  }
}

u2 ClassVerifier::verify_stackmap_table(u2 stackmap_index, u2 bci,
                                        StackMapFrame* current_frame,
                                        StackMapTable* stackmap_table,
                                        bool no_control_flow, TRAPS) {
  if (stackmap_index < stackmap_table->get_frame_count()) {
    u2 this_offset = stackmap_table->get_offset(stackmap_index);
    if (no_control_flow && this_offset > bci) {
      verify_error(ErrorContext::missing_stackmap(bci),
                   "Expecting a stack map frame");
      return 0;
    }
    if (this_offset == bci) {
      ErrorContext ctx;
      // See if current stack map can be assigned to the frame in table.
      // current_frame is the stackmap frame got from the last instruction.
      // If matched, current_frame will be updated by this method.
      bool matches = stackmap_table->match_stackmap(
        current_frame, this_offset, stackmap_index,
        !no_control_flow, true, false, &ctx, CHECK_VERIFY_(this, 0));
      if (!matches) {
        // report type error
        verify_error(ctx, "Instruction type does not match stack map");
        return 0;
      }
      stackmap_index++;
    } else if (this_offset < bci) {
      // current_offset should have met this_offset.
      class_format_error("Bad stack map offset %d", this_offset);
      return 0;
    }
  } else if (no_control_flow) {
    verify_error(ErrorContext::bad_code(bci), "Expecting a stack map frame");
    return 0;
  }
  return stackmap_index;
}

void ClassVerifier::verify_exception_handler_targets(u2 bci, bool this_uninit, StackMapFrame* current_frame,
                                                     StackMapTable* stackmap_table, TRAPS) {
  constantPoolHandle cp (THREAD, _method->constants());
  ExceptionTable exhandlers(_method());
  int exlength = exhandlers.length();
  for(int i = 0; i < exlength; i++) {
    u2 start_pc = exhandlers.start_pc(i);
    u2 end_pc = exhandlers.end_pc(i);
    u2 handler_pc = exhandlers.handler_pc(i);
    int catch_type_index = exhandlers.catch_type_index(i);
    if(bci >= start_pc && bci < end_pc) {
      u1 flags = current_frame->flags();
      if (this_uninit) {  flags |= FLAG_THIS_UNINIT; }
      StackMapFrame* new_frame = current_frame->frame_in_exception_handler(flags);
      if (catch_type_index != 0) {
        // We know that this index refers to a subclass of Throwable
        VerificationType catch_type = cp_index_to_type(
          catch_type_index, cp, CHECK_VERIFY(this));
        new_frame->push_stack(catch_type, CHECK_VERIFY(this));
      } else {
        VerificationType throwable =
          VerificationType::reference_type(vmSymbols::java_lang_Throwable());
        new_frame->push_stack(throwable, CHECK_VERIFY(this));
      }
      ErrorContext ctx;
      bool matches = stackmap_table->match_stackmap(
        new_frame, handler_pc, true, false, true, &ctx, CHECK_VERIFY(this));
      if (!matches) {
        verify_error(ctx, "Stack map does not match the one at "
            "exception handler %d", handler_pc);
        return;
      }
    }
  }
}

void ClassVerifier::verify_cp_index(
    u2 bci, constantPoolHandle cp, int index, TRAPS) {
  int nconstants = cp->length();
  if ((index <= 0) || (index >= nconstants)) {
    verify_error(ErrorContext::bad_cp_index(bci, index),
        "Illegal constant pool index %d in class %s",
        index, cp->pool_holder()->external_name());
    return;
  }
}

void ClassVerifier::verify_cp_type(
    u2 bci, int index, constantPoolHandle cp, unsigned int types, TRAPS) {

  // In some situations, bytecode rewriting may occur while we're verifying.
  // In this case, a constant pool cache exists and some indices refer to that
  // instead.  Be sure we don't pick up such indices by accident.
  // We must check was_recursively_verified() before we get here.
  guarantee(cp->cache() == NULL, "not rewritten yet");

  verify_cp_index(bci, cp, index, CHECK_VERIFY(this));
  unsigned int tag = cp->tag_at(index).value();
  if ((types & (1 << tag)) == 0) {
    verify_error(ErrorContext::bad_cp_index(bci, index),
      "Illegal type at constant pool entry %d in class %s",
      index, cp->pool_holder()->external_name());
    return;
  }
}

void ClassVerifier::verify_cp_class_type(
    u2 bci, int index, constantPoolHandle cp, TRAPS) {
  verify_cp_index(bci, cp, index, CHECK_VERIFY(this));
  constantTag tag = cp->tag_at(index);
  if (!tag.is_klass() && !tag.is_unresolved_klass()) {
    verify_error(ErrorContext::bad_cp_index(bci, index),
        "Illegal type at constant pool entry %d in class %s",
        index, cp->pool_holder()->external_name());
    return;
  }
}

void ClassVerifier::verify_error(ErrorContext ctx, const char* msg, ...) {
  stringStream ss;

  ctx.reset_frames();
  _exception_type = vmSymbols::java_lang_VerifyError();
  _error_context = ctx;
  va_list va;
  va_start(va, msg);
  ss.vprint(msg, va);
  va_end(va);
  _message = ss.as_string();
#ifdef ASSERT
  ResourceMark rm;
  const char* exception_name = _exception_type->as_C_string();
  Exceptions::debug_check_abort(exception_name, NULL);
#endif // ndef ASSERT
}

void ClassVerifier::class_format_error(const char* msg, ...) {
  stringStream ss;
  _exception_type = vmSymbols::java_lang_ClassFormatError();
  va_list va;
  va_start(va, msg);
  ss.vprint(msg, va);
  va_end(va);
  if (!_method.is_null()) {
    ss.print(" in method %s", _method->name_and_sig_as_C_string());
  }
  _message = ss.as_string();
}

Klass* ClassVerifier::load_class(Symbol* name, TRAPS) {
  // Get current loader and protection domain first.
  oop loader = current_class()->class_loader();
  oop protection_domain = current_class()->protection_domain();

  Klass* kls = SystemDictionary::resolve_or_fail(
    name, Handle(THREAD, loader), Handle(THREAD, protection_domain),
    true, THREAD);

  if (TraceClassResolution) {
    instanceKlassHandle cur_class = current_class();
    Verifier::trace_class_resolution(kls, cur_class());
  }
  return kls;
}

bool ClassVerifier::is_protected_access(instanceKlassHandle this_class,
                                        Klass* target_class,
                                        Symbol* field_name,
                                        Symbol* field_sig,
                                        bool is_method) {
  No_Safepoint_Verifier nosafepoint;

  // If target class isn't a super class of this class, we don't worry about this case
  if (!this_class->is_subclass_of(target_class)) {
    return false;
  }
  // Check if the specified method or field is protected
  InstanceKlass* target_instance = InstanceKlass::cast(target_class);
  fieldDescriptor fd;
  if (is_method) {
    Method* m = target_instance->uncached_lookup_method(field_name, field_sig, Klass::find_overpass);
    if (m != NULL && m->is_protected()) {
      if (!this_class->is_same_class_package(m->method_holder())) {
        return true;
      }
    }
  } else {
    Klass* member_klass = target_instance->find_field(field_name, field_sig, &fd);
    if (member_klass != NULL && fd.is_protected()) {
      if (!this_class->is_same_class_package(member_klass)) {
        return true;
      }
    }
  }
  return false;
}

void ClassVerifier::verify_ldc(
    int opcode, u2 index, StackMapFrame* current_frame,
    constantPoolHandle cp, u2 bci, TRAPS) {
  verify_cp_index(bci, cp, index, CHECK_VERIFY(this));
  constantTag tag = cp->tag_at(index);
  unsigned int types;
  if (opcode == Bytecodes::_ldc || opcode == Bytecodes::_ldc_w) {
    if (!tag.is_unresolved_klass()) {
      types = (1 << JVM_CONSTANT_Integer) | (1 << JVM_CONSTANT_Float)
            | (1 << JVM_CONSTANT_String)  | (1 << JVM_CONSTANT_Class)
            | (1 << JVM_CONSTANT_MethodHandle) | (1 << JVM_CONSTANT_MethodType);
      // Note:  The class file parser already verified the legality of
      // MethodHandle and MethodType constants.
      verify_cp_type(bci, index, cp, types, CHECK_VERIFY(this));
    }
  } else {
    assert(opcode == Bytecodes::_ldc2_w, "must be ldc2_w");
    types = (1 << JVM_CONSTANT_Double) | (1 << JVM_CONSTANT_Long);
    verify_cp_type(bci, index, cp, types, CHECK_VERIFY(this));
  }
  if (tag.is_string() && cp->is_pseudo_string_at(index)) {
    current_frame->push_stack(object_type(), CHECK_VERIFY(this));
  } else if (tag.is_string()) {
    current_frame->push_stack(
      VerificationType::reference_type(
        vmSymbols::java_lang_String()), CHECK_VERIFY(this));
  } else if (tag.is_klass() || tag.is_unresolved_klass()) {
    current_frame->push_stack(
      VerificationType::reference_type(
        vmSymbols::java_lang_Class()), CHECK_VERIFY(this));
  } else if (tag.is_int()) {
    current_frame->push_stack(
      VerificationType::integer_type(), CHECK_VERIFY(this));
  } else if (tag.is_float()) {
    current_frame->push_stack(
      VerificationType::float_type(), CHECK_VERIFY(this));
  } else if (tag.is_double()) {
    current_frame->push_stack_2(
      VerificationType::double_type(),
      VerificationType::double2_type(), CHECK_VERIFY(this));
  } else if (tag.is_long()) {
    current_frame->push_stack_2(
      VerificationType::long_type(),
      VerificationType::long2_type(), CHECK_VERIFY(this));
  } else if (tag.is_method_handle()) {
    current_frame->push_stack(
      VerificationType::reference_type(
        vmSymbols::java_lang_invoke_MethodHandle()), CHECK_VERIFY(this));
  } else if (tag.is_method_type()) {
    current_frame->push_stack(
      VerificationType::reference_type(
        vmSymbols::java_lang_invoke_MethodType()), CHECK_VERIFY(this));
  } else {
    /* Unreachable? verify_cp_type has already validated the cp type. */
    verify_error(
        ErrorContext::bad_cp_index(bci, index), "Invalid index in ldc");
    return;
  }
}

void ClassVerifier::verify_switch(
    RawBytecodeStream* bcs, u4 code_length, char* code_data,
    StackMapFrame* current_frame, StackMapTable* stackmap_table, TRAPS) {
  int bci = bcs->bci();
  address bcp = bcs->bcp();
  address aligned_bcp = (address) round_to((intptr_t)(bcp + 1), jintSize);

  if (_klass->major_version() < NONZERO_PADDING_BYTES_IN_SWITCH_MAJOR_VERSION) {
    // 4639449 & 4647081: padding bytes must be 0
    u2 padding_offset = 1;
    while ((bcp + padding_offset) < aligned_bcp) {
      if(*(bcp + padding_offset) != 0) {
        verify_error(ErrorContext::bad_code(bci),
                     "Nonzero padding byte in lookupswitch or tableswitch");
        return;
      }
      padding_offset++;
    }
  }

  int default_offset = (int) Bytes::get_Java_u4(aligned_bcp);
  int keys, delta;
  current_frame->pop_stack(
    VerificationType::integer_type(), CHECK_VERIFY(this));
  if (bcs->raw_code() == Bytecodes::_tableswitch) {
    jint low = (jint)Bytes::get_Java_u4(aligned_bcp + jintSize);
    jint high = (jint)Bytes::get_Java_u4(aligned_bcp + 2*jintSize);
    if (low > high) {
      verify_error(ErrorContext::bad_code(bci),
          "low must be less than or equal to high in tableswitch");
      return;
    }
    keys = high - low + 1;
    if (keys < 0) {
      verify_error(ErrorContext::bad_code(bci), "too many keys in tableswitch");
      return;
    }
    delta = 1;
  } else {
    keys = (int)Bytes::get_Java_u4(aligned_bcp + jintSize);
    if (keys < 0) {
      verify_error(ErrorContext::bad_code(bci),
                   "number of keys in lookupswitch less than 0");
      return;
    }
    delta = 2;
    // Make sure that the lookupswitch items are sorted
    for (int i = 0; i < (keys - 1); i++) {
      jint this_key = Bytes::get_Java_u4(aligned_bcp + (2+2*i)*jintSize);
      jint next_key = Bytes::get_Java_u4(aligned_bcp + (2+2*i+2)*jintSize);
      if (this_key >= next_key) {
        verify_error(ErrorContext::bad_code(bci),
                     "Bad lookupswitch instruction");
        return;
      }
    }
  }
  int target = bci + default_offset;
  stackmap_table->check_jump_target(current_frame, target, CHECK_VERIFY(this));
  for (int i = 0; i < keys; i++) {
    // Because check_jump_target() may safepoint, the bytecode could have
    // moved, which means 'aligned_bcp' is no good and needs to be recalculated.
    aligned_bcp = (address)round_to((intptr_t)(bcs->bcp() + 1), jintSize);
    target = bci + (jint)Bytes::get_Java_u4(aligned_bcp+(3+i*delta)*jintSize);
    stackmap_table->check_jump_target(
      current_frame, target, CHECK_VERIFY(this));
  }
  NOT_PRODUCT(aligned_bcp = NULL);  // no longer valid at this point
}

bool ClassVerifier::name_in_supers(
    Symbol* ref_name, instanceKlassHandle current) {
  Klass* super = current->super();
  while (super != NULL) {
    if (super->name() == ref_name) {
      return true;
    }
    super = super->super();
  }
  return false;
}

void ClassVerifier::verify_field_instructions(RawBytecodeStream* bcs,
                                              StackMapFrame* current_frame,
                                              constantPoolHandle cp,
                                              bool allow_arrays,
                                              TRAPS) {
  u2 index = bcs->get_index_u2();
  verify_cp_type(bcs->bci(), index, cp,
      1 << JVM_CONSTANT_Fieldref, CHECK_VERIFY(this));

  // Get field name and signature
  Symbol* field_name = cp->name_ref_at(index);
  Symbol* field_sig = cp->signature_ref_at(index);

  if (!SignatureVerifier::is_valid_type_signature(field_sig)) {
    class_format_error(
      "Invalid signature for field in class %s referenced "
      "from constant pool index %d", _klass->external_name(), index);
    return;
  }

  // Get referenced class type
  VerificationType ref_class_type = cp_ref_index_to_type(
    index, cp, CHECK_VERIFY(this));
  if (!ref_class_type.is_object() &&
    (!allow_arrays || !ref_class_type.is_array())) {
    verify_error(ErrorContext::bad_type(bcs->bci(),
        TypeOrigin::cp(index, ref_class_type)),
        "Expecting reference to class in class %s at constant pool index %d",
        _klass->external_name(), index);
    return;
  }
  VerificationType target_class_type = ref_class_type;

  assert(sizeof(VerificationType) == sizeof(uintptr_t),
        "buffer type must match VerificationType size");
  uintptr_t field_type_buffer[2];
  VerificationType* field_type = (VerificationType*)field_type_buffer;
  // If we make a VerificationType[2] array directly, the compiler calls
  // to the c-runtime library to do the allocation instead of just
  // stack allocating it.  Plus it would run constructors.  This shows up
  // in performance profiles.

  SignatureStream sig_stream(field_sig, false);
  VerificationType stack_object_type;
  int n = change_sig_to_verificationType(
    &sig_stream, field_type, CHECK_VERIFY(this));
  u2 bci = bcs->bci();
  bool is_assignable;
  switch (bcs->raw_code()) {
    case Bytecodes::_getstatic: {
      for (int i = 0; i < n; i++) {
        current_frame->push_stack(field_type[i], CHECK_VERIFY(this));
      }
      break;
    }
    case Bytecodes::_putstatic: {
      for (int i = n - 1; i >= 0; i--) {
        current_frame->pop_stack(field_type[i], CHECK_VERIFY(this));
      }
      break;
    }
    case Bytecodes::_getfield: {
      stack_object_type = current_frame->pop_stack(
        target_class_type, CHECK_VERIFY(this));
      for (int i = 0; i < n; i++) {
        current_frame->push_stack(field_type[i], CHECK_VERIFY(this));
      }
      goto check_protected;
    }
    case Bytecodes::_putfield: {
      for (int i = n - 1; i >= 0; i--) {
        current_frame->pop_stack(field_type[i], CHECK_VERIFY(this));
      }
      stack_object_type = current_frame->pop_stack(CHECK_VERIFY(this));

      // The JVMS 2nd edition allows field initialization before the superclass
      // initializer, if the field is defined within the current class.
      fieldDescriptor fd;
      if (stack_object_type == VerificationType::uninitialized_this_type() &&
          target_class_type.equals(current_type()) &&
          _klass->find_local_field(field_name, field_sig, &fd)) {
        stack_object_type = current_type();
      }
      is_assignable = target_class_type.is_assignable_from(
        stack_object_type, this, false, CHECK_VERIFY(this));
      if (!is_assignable) {
        verify_error(ErrorContext::bad_type(bci,
            current_frame->stack_top_ctx(),
            TypeOrigin::cp(index, target_class_type)),
            "Bad type on operand stack in putfield");
        return;
      }
    }
    check_protected: {
      if (_this_type == stack_object_type)
        break; // stack_object_type must be assignable to _current_class_type
      Symbol* ref_class_name =
        cp->klass_name_at(cp->klass_ref_index_at(index));
      if (!name_in_supers(ref_class_name, current_class()))
        // stack_object_type must be assignable to _current_class_type since:
        // 1. stack_object_type must be assignable to ref_class.
        // 2. ref_class must be _current_class or a subclass of it. It can't
        //    be a superclass of it. See revised JVMS 5.4.4.
        break;

      Klass* ref_class_oop = load_class(ref_class_name, CHECK);
      if (is_protected_access(current_class(), ref_class_oop, field_name,
                              field_sig, false)) {
        // It's protected access, check if stack object is assignable to
        // current class.
        is_assignable = current_type().is_assignable_from(
          stack_object_type, this, true, CHECK_VERIFY(this));
        if (!is_assignable) {
          verify_error(ErrorContext::bad_type(bci,
              current_frame->stack_top_ctx(),
              TypeOrigin::implicit(current_type())),
              "Bad access to protected data in getfield");
          return;
        }
      }
      break;
    }
    default: ShouldNotReachHere();
  }
}

// Look at the method's handlers.  If the bci is in the handler's try block
// then check if the handler_pc is already on the stack.  If not, push it
// unless the handler has already been scanned.
void ClassVerifier::push_handlers(ExceptionTable* exhandlers,
                                  GrowableArray<u4>* handler_list,
                                  GrowableArray<u4>* handler_stack,
                                  u4 bci) {
  int exlength = exhandlers->length();
  for(int x = 0; x < exlength; x++) {
    if (bci >= exhandlers->start_pc(x) && bci < exhandlers->end_pc(x)) {
      u4 exhandler_pc = exhandlers->handler_pc(x);
      if (!handler_list->contains(exhandler_pc)) {
        handler_stack->append_if_missing(exhandler_pc);
        handler_list->append(exhandler_pc);
      }
    }
  }
}

// Return TRUE if all code paths starting with start_bc_offset end in
// bytecode athrow or loop.
bool ClassVerifier::ends_in_athrow(u4 start_bc_offset) {
  ResourceMark rm;
  // Create bytecode stream.
  RawBytecodeStream bcs(method());
  u4 code_length = method()->code_size();
  bcs.set_start(start_bc_offset);
  u4 target;
  // Create stack for storing bytecode start offsets for if* and *switch.
  GrowableArray<u4>* bci_stack = new GrowableArray<u4>(30);
  // Create stack for handlers for try blocks containing this handler.
  GrowableArray<u4>* handler_stack = new GrowableArray<u4>(30);
  // Create list of handlers that have been pushed onto the handler_stack
  // so that handlers embedded inside of their own TRY blocks only get
  // scanned once.
  GrowableArray<u4>* handler_list = new GrowableArray<u4>(30);
  // Create list of visited branch opcodes (goto* and if*).
  GrowableArray<u4>* visited_branches = new GrowableArray<u4>(30);
  ExceptionTable exhandlers(_method());

  while (true) {
    if (bcs.is_last_bytecode()) {
      // if no more starting offsets to parse or if at the end of the
      // method then return false.
      if ((bci_stack->is_empty()) || ((u4)bcs.end_bci() == code_length))
        return false;
      // Pop a bytecode starting offset and scan from there.
      bcs.set_start(bci_stack->pop());
    }
    Bytecodes::Code opcode = bcs.raw_next();
    u4 bci = bcs.bci();

    // If the bytecode is in a TRY block, push its handlers so they
    // will get parsed.
    push_handlers(&exhandlers, handler_list, handler_stack, bci);

    switch (opcode) {
      case Bytecodes::_if_icmpeq:
      case Bytecodes::_if_icmpne:
      case Bytecodes::_if_icmplt:
      case Bytecodes::_if_icmpge:
      case Bytecodes::_if_icmpgt:
      case Bytecodes::_if_icmple:
      case Bytecodes::_ifeq:
      case Bytecodes::_ifne:
      case Bytecodes::_iflt:
      case Bytecodes::_ifge:
      case Bytecodes::_ifgt:
      case Bytecodes::_ifle:
      case Bytecodes::_if_acmpeq:
      case Bytecodes::_if_acmpne:
      case Bytecodes::_ifnull:
      case Bytecodes::_ifnonnull:
        target = bcs.dest();
        if (visited_branches->contains(bci)) {
          if (bci_stack->is_empty()) return true;
          // Pop a bytecode starting offset and scan from there.
          bcs.set_start(bci_stack->pop());
        } else {
          if (target > bci) { // forward branch
            if (target >= code_length) return false;
            // Push the branch target onto the stack.
            bci_stack->push(target);
            // then, scan bytecodes starting with next.
            bcs.set_start(bcs.next_bci());
          } else { // backward branch
            // Push bytecode offset following backward branch onto the stack.
            bci_stack->push(bcs.next_bci());
            // Check bytecodes starting with branch target.
            bcs.set_start(target);
          }
          // Record target so we don't branch here again.
          visited_branches->append(bci);
        }
        break;

      case Bytecodes::_goto:
      case Bytecodes::_goto_w:
        target = (opcode == Bytecodes::_goto ? bcs.dest() : bcs.dest_w());
        if (visited_branches->contains(bci)) {
          if (bci_stack->is_empty()) return true;
          // Been here before, pop new starting offset from stack.
          bcs.set_start(bci_stack->pop());
        } else {
          if (target >= code_length) return false;
          // Continue scanning from the target onward.
          bcs.set_start(target);
          // Record target so we don't branch here again.
          visited_branches->append(bci);
        }
        break;

      // Check that all switch alternatives end in 'athrow' bytecodes. Since it
      // is  difficult to determine where each switch alternative ends, parse
      // each switch alternative until either hit a 'return', 'athrow', or reach
      // the end of the method's bytecodes.  This is gross but should be okay
      // because:
      // 1. tableswitch and lookupswitch byte codes in handlers for ctor explicit
      //    constructor invocations should be rare.
      // 2. if each switch alternative ends in an athrow then the parsing should be
      //    short.  If there is no athrow then it is bogus code, anyway.
      case Bytecodes::_lookupswitch:
      case Bytecodes::_tableswitch:
        {
          address aligned_bcp = (address) round_to((intptr_t)(bcs.bcp() + 1), jintSize);
          u4 default_offset = Bytes::get_Java_u4(aligned_bcp) + bci;
          int keys, delta;
          if (opcode == Bytecodes::_tableswitch) {
            jint low = (jint)Bytes::get_Java_u4(aligned_bcp + jintSize);
            jint high = (jint)Bytes::get_Java_u4(aligned_bcp + 2*jintSize);
            // This is invalid, but let the regular bytecode verifier
            // report this because the user will get a better error message.
            if (low > high) return true;
            keys = high - low + 1;
            delta = 1;
          } else {
            keys = (int)Bytes::get_Java_u4(aligned_bcp + jintSize);
            delta = 2;
          }
          // Invalid, let the regular bytecode verifier deal with it.
          if (keys < 0) return true;

          // Push the offset of the next bytecode onto the stack.
          bci_stack->push(bcs.next_bci());

          // Push the switch alternatives onto the stack.
          for (int i = 0; i < keys; i++) {
            u4 target = bci + (jint)Bytes::get_Java_u4(aligned_bcp+(3+i*delta)*jintSize);
            if (target > code_length) return false;
            bci_stack->push(target);
          }

          // Start bytecode parsing for the switch at the default alternative.
          if (default_offset > code_length) return false;
          bcs.set_start(default_offset);
          break;
        }

      case Bytecodes::_return:
        return false;

      case Bytecodes::_athrow:
        {
          if (bci_stack->is_empty()) {
            if (handler_stack->is_empty()) {
              return true;
            } else {
              // Parse the catch handlers for try blocks containing athrow.
              bcs.set_start(handler_stack->pop());
            }
          } else {
            // Pop a bytecode offset and starting scanning from there.
            bcs.set_start(bci_stack->pop());
          }
        }
        break;

      default:
        ;
    } // end switch
  } // end while loop

  return false;
}

void ClassVerifier::verify_invoke_init(
    RawBytecodeStream* bcs, u2 ref_class_index, VerificationType ref_class_type,
    StackMapFrame* current_frame, u4 code_length, bool in_try_block,
    bool *this_uninit, constantPoolHandle cp, StackMapTable* stackmap_table,
    TRAPS) {
  u2 bci = bcs->bci();
  VerificationType type = current_frame->pop_stack(
    VerificationType::reference_check(), CHECK_VERIFY(this));
  if (type == VerificationType::uninitialized_this_type()) {
    // The method must be an <init> method of this class or its superclass
    Klass* superk = current_class()->super();
    if (ref_class_type.name() != current_class()->name() &&
        ref_class_type.name() != superk->name()) {
      verify_error(ErrorContext::bad_type(bci,
          TypeOrigin::implicit(ref_class_type),
          TypeOrigin::implicit(current_type())),
          "Bad <init> method call");
      return;
    }

    // If this invokespecial call is done from inside of a TRY block then make
    // sure that all catch clause paths end in a throw.  Otherwise, this can
    // result in returning an incomplete object.
    if (in_try_block) {
      ExceptionTable exhandlers(_method());
      int exlength = exhandlers.length();
      for(int i = 0; i < exlength; i++) {
        u2 start_pc = exhandlers.start_pc(i);
        u2 end_pc = exhandlers.end_pc(i);

        if (bci >= start_pc && bci < end_pc) {
          if (!ends_in_athrow(exhandlers.handler_pc(i))) {
            verify_error(ErrorContext::bad_code(bci),
              "Bad <init> method call from after the start of a try block");
            return;
          } else if (VerboseVerification) {
            ResourceMark rm;
            tty->print_cr(
              "Survived call to ends_in_athrow(): %s",
              current_class()->name()->as_C_string());
          }
        }
      }

      // Check the exception handler target stackmaps with the locals from the
      // incoming stackmap (before initialize_object() changes them to outgoing
      // state).
      verify_exception_handler_targets(bci, true, current_frame,
                                       stackmap_table, CHECK_VERIFY(this));
    } // in_try_block

    current_frame->initialize_object(type, current_type());
    *this_uninit = true;
  } else if (type.is_uninitialized()) {
    u2 new_offset = type.bci();
    address new_bcp = bcs->bcp() - bci + new_offset;
    if (new_offset > (code_length - 3) || (*new_bcp) != Bytecodes::_new) {
      /* Unreachable?  Stack map parsing ensures valid type and new
       * instructions have a valid BCI. */
      verify_error(ErrorContext::bad_code(new_offset),
                   "Expecting new instruction");
      return;
    }
    u2 new_class_index = Bytes::get_Java_u2(new_bcp + 1);
    verify_cp_class_type(bci, new_class_index, cp, CHECK_VERIFY(this));

    // The method must be an <init> method of the indicated class
    VerificationType new_class_type = cp_index_to_type(
      new_class_index, cp, CHECK_VERIFY(this));
    if (!new_class_type.equals(ref_class_type)) {
      verify_error(ErrorContext::bad_type(bci,
          TypeOrigin::cp(new_class_index, new_class_type),
          TypeOrigin::cp(ref_class_index, ref_class_type)),
          "Call to wrong <init> method");
      return;
    }
    // According to the VM spec, if the referent class is a superclass of the
    // current class, and is in a different runtime package, and the method is
    // protected, then the objectref must be the current class or a subclass
    // of the current class.
    VerificationType objectref_type = new_class_type;
    if (name_in_supers(ref_class_type.name(), current_class())) {
      Klass* ref_klass = load_class(ref_class_type.name(), CHECK);
      Method* m = InstanceKlass::cast(ref_klass)->uncached_lookup_method(
        vmSymbols::object_initializer_name(),
        cp->signature_ref_at(bcs->get_index_u2()),
        Klass::find_overpass);
      // Do nothing if method is not found.  Let resolution detect the error.
      if (m != NULL) {
        instanceKlassHandle mh(THREAD, m->method_holder());
        if (m->is_protected() && !mh->is_same_class_package(_klass())) {
          bool assignable = current_type().is_assignable_from(
            objectref_type, this, true, CHECK_VERIFY(this));
          if (!assignable) {
            verify_error(ErrorContext::bad_type(bci,
                TypeOrigin::cp(new_class_index, objectref_type),
                TypeOrigin::implicit(current_type())),
                "Bad access to protected <init> method");
            return;
          }
        }
      }
    }
    // Check the exception handler target stackmaps with the locals from the
    // incoming stackmap (before initialize_object() changes them to outgoing
    // state).
    if (in_try_block) {
      verify_exception_handler_targets(bci, *this_uninit, current_frame,
                                       stackmap_table, CHECK_VERIFY(this));
    }
    current_frame->initialize_object(type, new_class_type);
  } else {
    verify_error(ErrorContext::bad_type(bci, current_frame->stack_top_ctx()),
        "Bad operand type when invoking <init>");
    return;
  }
}

bool ClassVerifier::is_same_or_direct_interface(
    instanceKlassHandle klass,
    VerificationType klass_type,
    VerificationType ref_class_type) {
  if (ref_class_type.equals(klass_type)) return true;
  Array<Klass*>* local_interfaces = klass->local_interfaces();
  if (local_interfaces != NULL) {
    for (int x = 0; x < local_interfaces->length(); x++) {
      Klass* k = local_interfaces->at(x);
      assert (k != NULL && k->is_interface(), "invalid interface");
      if (ref_class_type.equals(VerificationType::reference_type(k->name()))) {
        return true;
      }
    }
  }
  return false;
}

void ClassVerifier::verify_invoke_instructions(
    RawBytecodeStream* bcs, u4 code_length, StackMapFrame* current_frame,
    bool in_try_block, bool *this_uninit, VerificationType return_type,
    constantPoolHandle cp, StackMapTable* stackmap_table, TRAPS) {
  // Make sure the constant pool item is the right type
  u2 index = bcs->get_index_u2();
  Bytecodes::Code opcode = bcs->raw_code();
  unsigned int types;
  switch (opcode) {
    case Bytecodes::_invokeinterface:
      types = 1 << JVM_CONSTANT_InterfaceMethodref;
      break;
    case Bytecodes::_invokedynamic:
      types = 1 << JVM_CONSTANT_InvokeDynamic;
      break;
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
      types = (_klass->major_version() < STATIC_METHOD_IN_INTERFACE_MAJOR_VERSION) ?
        (1 << JVM_CONSTANT_Methodref) :
        ((1 << JVM_CONSTANT_InterfaceMethodref) | (1 << JVM_CONSTANT_Methodref));
      break;
    default:
      types = 1 << JVM_CONSTANT_Methodref;
  }
  verify_cp_type(bcs->bci(), index, cp, types, CHECK_VERIFY(this));

  // Get method name and signature
  Symbol* method_name = cp->name_ref_at(index);
  Symbol* method_sig = cp->signature_ref_at(index);

  if (!SignatureVerifier::is_valid_method_signature(method_sig)) {
    class_format_error(
      "Invalid method signature in class %s referenced "
      "from constant pool index %d", _klass->external_name(), index);
    return;
  }

  // Get referenced class type
  VerificationType ref_class_type;
  if (opcode == Bytecodes::_invokedynamic) {
    if (_klass->major_version() < Verifier::INVOKEDYNAMIC_MAJOR_VERSION) {
      class_format_error(
        "invokedynamic instructions not supported by this class file version (%d), class %s",
        _klass->major_version(), _klass->external_name());
      return;
    }
  } else {
    ref_class_type = cp_ref_index_to_type(index, cp, CHECK_VERIFY(this));
  }

  // For a small signature length, we just allocate 128 bytes instead
  // of parsing the signature once to find its size.
  // -3 is for '(', ')' and return descriptor; multiply by 2 is for
  // longs/doubles to be consertive.
  assert(sizeof(VerificationType) == sizeof(uintptr_t),
        "buffer type must match VerificationType size");
  uintptr_t on_stack_sig_types_buffer[128];
  // If we make a VerificationType[128] array directly, the compiler calls
  // to the c-runtime library to do the allocation instead of just
  // stack allocating it.  Plus it would run constructors.  This shows up
  // in performance profiles.

  VerificationType* sig_types;
  int size = (method_sig->utf8_length() - 3) * 2;
  if (size > 128) {
    // Long and double occupies two slots here.
    ArgumentSizeComputer size_it(method_sig);
    size = size_it.size();
    sig_types = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, VerificationType, size);
  } else{
    sig_types = (VerificationType*)on_stack_sig_types_buffer;
  }
  SignatureStream sig_stream(method_sig);
  int sig_i = 0;
  while (!sig_stream.at_return_type()) {
    sig_i += change_sig_to_verificationType(
      &sig_stream, &sig_types[sig_i], CHECK_VERIFY(this));
    sig_stream.next();
  }
  int nargs = sig_i;

#ifdef ASSERT
  {
    ArgumentSizeComputer size_it(method_sig);
    assert(nargs == size_it.size(), "Argument sizes do not match");
    assert(nargs <= (method_sig->utf8_length() - 3) * 2, "estimate of max size isn't conservative enough");
  }
#endif

  // Check instruction operands
  u2 bci = bcs->bci();
  if (opcode == Bytecodes::_invokeinterface) {
    address bcp = bcs->bcp();
    // 4905268: count operand in invokeinterface should be nargs+1, not nargs.
    // JSR202 spec: The count operand of an invokeinterface instruction is valid if it is
    // the difference between the size of the operand stack before and after the instruction
    // executes.
    if (*(bcp+3) != (nargs+1)) {
      verify_error(ErrorContext::bad_code(bci),
          "Inconsistent args count operand in invokeinterface");
      return;
    }
    if (*(bcp+4) != 0) {
      verify_error(ErrorContext::bad_code(bci),
          "Fourth operand byte of invokeinterface must be zero");
      return;
    }
  }

  if (opcode == Bytecodes::_invokedynamic) {
    address bcp = bcs->bcp();
    if (*(bcp+3) != 0 || *(bcp+4) != 0) {
      verify_error(ErrorContext::bad_code(bci),
          "Third and fourth operand bytes of invokedynamic must be zero");
      return;
    }
  }

  if (method_name->byte_at(0) == '<') {
    // Make sure <init> can only be invoked by invokespecial
    if (opcode != Bytecodes::_invokespecial ||
        method_name != vmSymbols::object_initializer_name()) {
      verify_error(ErrorContext::bad_code(bci),
          "Illegal call to internal method");
      return;
    }
  } else if (opcode == Bytecodes::_invokespecial
             && !is_same_or_direct_interface(current_class(), current_type(), ref_class_type)
             && !ref_class_type.equals(VerificationType::reference_type(
                  current_class()->super()->name()))) {
    bool subtype = false;
    bool have_imr_indirect = cp->tag_at(index).value() == JVM_CONSTANT_InterfaceMethodref;
    if (!current_class()->is_anonymous()) {
      subtype = ref_class_type.is_assignable_from(
                 current_type(), this, false, CHECK_VERIFY(this));
    } else {
      VerificationType host_klass_type =
                        VerificationType::reference_type(current_class()->host_klass()->name());
      subtype = ref_class_type.is_assignable_from(host_klass_type, this, false, CHECK_VERIFY(this));

      // If invokespecial of IMR, need to recheck for same or
      // direct interface relative to the host class
      have_imr_indirect = (have_imr_indirect &&
                           !is_same_or_direct_interface(
                             InstanceKlass::cast(current_class()->host_klass()),
                             host_klass_type, ref_class_type));
    }
    if (!subtype) {
      verify_error(ErrorContext::bad_code(bci),
          "Bad invokespecial instruction: "
          "current class isn't assignable to reference class.");
       return;
    } else if (have_imr_indirect) {
      verify_error(ErrorContext::bad_code(bci),
          "Bad invokespecial instruction: "
          "interface method reference is in an indirect superinterface.");
      return;
    }

  }
  // Match method descriptor with operand stack
  for (int i = nargs - 1; i >= 0; i--) {  // Run backwards
    current_frame->pop_stack(sig_types[i], CHECK_VERIFY(this));
  }
  // Check objectref on operand stack
  if (opcode != Bytecodes::_invokestatic &&
      opcode != Bytecodes::_invokedynamic) {
    if (method_name == vmSymbols::object_initializer_name()) {  // <init> method
      verify_invoke_init(bcs, index, ref_class_type, current_frame,
        code_length, in_try_block, this_uninit, cp, stackmap_table,
        CHECK_VERIFY(this));
    } else {   // other methods
      // Ensures that target class is assignable to method class.
      if (opcode == Bytecodes::_invokespecial) {
        if (!current_class()->is_anonymous()) {
          current_frame->pop_stack(current_type(), CHECK_VERIFY(this));
        } else {
          // anonymous class invokespecial calls: check if the
          // objectref is a subtype of the host_klass of the current class
          // to allow an anonymous class to reference methods in the host_klass
          VerificationType top = current_frame->pop_stack(CHECK_VERIFY(this));
          VerificationType hosttype =
            VerificationType::reference_type(current_class()->host_klass()->name());
          bool subtype = hosttype.is_assignable_from(top, this, false, CHECK_VERIFY(this));
          if (!subtype) {
            verify_error( ErrorContext::bad_type(current_frame->offset(),
              current_frame->stack_top_ctx(),
              TypeOrigin::implicit(top)),
              "Bad type on operand stack");
            return;
          }
        }
      } else if (opcode == Bytecodes::_invokevirtual) {
        VerificationType stack_object_type =
          current_frame->pop_stack(ref_class_type, CHECK_VERIFY(this));
        if (current_type() != stack_object_type) {
          assert(cp->cache() == NULL, "not rewritten yet");
          Symbol* ref_class_name =
            cp->klass_name_at(cp->klass_ref_index_at(index));
          // See the comments in verify_field_instructions() for
          // the rationale behind this.
          if (name_in_supers(ref_class_name, current_class())) {
            Klass* ref_class = load_class(ref_class_name, CHECK);
            if (is_protected_access(
                  _klass, ref_class, method_name, method_sig, true)) {
              // It's protected access, check if stack object is
              // assignable to current class.
              bool is_assignable = current_type().is_assignable_from(
                stack_object_type, this, true, CHECK_VERIFY(this));
              if (!is_assignable) {
                if (ref_class_type.name() == vmSymbols::java_lang_Object()
                    && stack_object_type.is_array()
                    && method_name == vmSymbols::clone_name()) {
                  // Special case: arrays pretend to implement public Object
                  // clone().
                } else {
                  verify_error(ErrorContext::bad_type(bci,
                      current_frame->stack_top_ctx(),
                      TypeOrigin::implicit(current_type())),
                      "Bad access to protected data in invokevirtual");
                  return;
                }
              }
            }
          }
        }
      } else {
        assert(opcode == Bytecodes::_invokeinterface, "Unexpected opcode encountered");
        current_frame->pop_stack(ref_class_type, CHECK_VERIFY(this));
      }
    }
  }
  // Push the result type.
  if (sig_stream.type() != T_VOID) {
    if (method_name == vmSymbols::object_initializer_name()) {
      // <init> method must have a void return type
      /* Unreachable?  Class file parser verifies that <init> methods have
       * void return */
      verify_error(ErrorContext::bad_code(bci),
          "Return type must be void in <init> method");
      return;
    }
    VerificationType return_type[2];
    int n = change_sig_to_verificationType(
      &sig_stream, return_type, CHECK_VERIFY(this));
    for (int i = 0; i < n; i++) {
      current_frame->push_stack(return_type[i], CHECK_VERIFY(this)); // push types backwards
    }
  }
}

VerificationType ClassVerifier::get_newarray_type(
    u2 index, u2 bci, TRAPS) {
  const char* from_bt[] = {
    NULL, NULL, NULL, NULL, "[Z", "[C", "[F", "[D", "[B", "[S", "[I", "[J",
  };
  if (index < T_BOOLEAN || index > T_LONG) {
    verify_error(ErrorContext::bad_code(bci), "Illegal newarray instruction");
    return VerificationType::bogus_type();
  }

  // from_bt[index] contains the array signature which has a length of 2
  Symbol* sig = create_temporary_symbol(
    from_bt[index], 2, CHECK_(VerificationType::bogus_type()));
  return VerificationType::reference_type(sig);
}

void ClassVerifier::verify_anewarray(
    u2 bci, u2 index, constantPoolHandle cp,
    StackMapFrame* current_frame, TRAPS) {
  verify_cp_class_type(bci, index, cp, CHECK_VERIFY(this));
  current_frame->pop_stack(
    VerificationType::integer_type(), CHECK_VERIFY(this));

  VerificationType component_type =
    cp_index_to_type(index, cp, CHECK_VERIFY(this));
  int length;
  char* arr_sig_str;
  if (component_type.is_array()) {     // it's an array
    const char* component_name = component_type.name()->as_utf8();
    // add one dimension to component
    length = (int)strlen(component_name) + 1;
    arr_sig_str = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, length);
    arr_sig_str[0] = '[';
    strncpy(&arr_sig_str[1], component_name, length - 1);
  } else {         // it's an object or interface
    const char* component_name = component_type.name()->as_utf8();
    // add one dimension to component with 'L' prepended and ';' postpended.
    length = (int)strlen(component_name) + 3;
    arr_sig_str = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, length);
    arr_sig_str[0] = '[';
    arr_sig_str[1] = 'L';
    strncpy(&arr_sig_str[2], component_name, length - 2);
    arr_sig_str[length - 1] = ';';
  }
  Symbol* arr_sig = create_temporary_symbol(
    arr_sig_str, length, CHECK_VERIFY(this));
  VerificationType new_array_type = VerificationType::reference_type(arr_sig);
  current_frame->push_stack(new_array_type, CHECK_VERIFY(this));
}

void ClassVerifier::verify_iload(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->get_local(
    index, VerificationType::integer_type(), CHECK_VERIFY(this));
  current_frame->push_stack(
    VerificationType::integer_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_lload(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->get_local_2(
    index, VerificationType::long_type(),
    VerificationType::long2_type(), CHECK_VERIFY(this));
  current_frame->push_stack_2(
    VerificationType::long_type(),
    VerificationType::long2_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_fload(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->get_local(
    index, VerificationType::float_type(), CHECK_VERIFY(this));
  current_frame->push_stack(
    VerificationType::float_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_dload(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->get_local_2(
    index, VerificationType::double_type(),
    VerificationType::double2_type(), CHECK_VERIFY(this));
  current_frame->push_stack_2(
    VerificationType::double_type(),
    VerificationType::double2_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_aload(u2 index, StackMapFrame* current_frame, TRAPS) {
  VerificationType type = current_frame->get_local(
    index, VerificationType::reference_check(), CHECK_VERIFY(this));
  current_frame->push_stack(type, CHECK_VERIFY(this));
}

void ClassVerifier::verify_istore(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->pop_stack(
    VerificationType::integer_type(), CHECK_VERIFY(this));
  current_frame->set_local(
    index, VerificationType::integer_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_lstore(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->pop_stack_2(
    VerificationType::long2_type(),
    VerificationType::long_type(), CHECK_VERIFY(this));
  current_frame->set_local_2(
    index, VerificationType::long_type(),
    VerificationType::long2_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_fstore(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->pop_stack(VerificationType::float_type(), CHECK_VERIFY(this));
  current_frame->set_local(
    index, VerificationType::float_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_dstore(u2 index, StackMapFrame* current_frame, TRAPS) {
  current_frame->pop_stack_2(
    VerificationType::double2_type(),
    VerificationType::double_type(), CHECK_VERIFY(this));
  current_frame->set_local_2(
    index, VerificationType::double_type(),
    VerificationType::double2_type(), CHECK_VERIFY(this));
}

void ClassVerifier::verify_astore(u2 index, StackMapFrame* current_frame, TRAPS) {
  VerificationType type = current_frame->pop_stack(
    VerificationType::reference_check(), CHECK_VERIFY(this));
  current_frame->set_local(index, type, CHECK_VERIFY(this));
}

void ClassVerifier::verify_iinc(u2 index, StackMapFrame* current_frame, TRAPS) {
  VerificationType type = current_frame->get_local(
    index, VerificationType::integer_type(), CHECK_VERIFY(this));
  current_frame->set_local(index, type, CHECK_VERIFY(this));
}

void ClassVerifier::verify_return_value(
    VerificationType return_type, VerificationType type, u2 bci,
    StackMapFrame* current_frame, TRAPS) {
  if (return_type == VerificationType::bogus_type()) {
    verify_error(ErrorContext::bad_type(bci,
        current_frame->stack_top_ctx(), TypeOrigin::signature(return_type)),
        "Method expects a return value");
    return;
  }
  bool match = return_type.is_assignable_from(type, this, false, CHECK_VERIFY(this));
  if (!match) {
    verify_error(ErrorContext::bad_type(bci,
        current_frame->stack_top_ctx(), TypeOrigin::signature(return_type)),
        "Bad return type");
    return;
  }
}

// The verifier creates symbols which are substrings of Symbols.
// These are stored in the verifier until the end of verification so that
// they can be reference counted.
Symbol* ClassVerifier::create_temporary_symbol(const Symbol *s, int begin,
                                               int end, TRAPS) {
  Symbol* sym = SymbolTable::new_symbol(s, begin, end, CHECK_NULL);
  _symbols->push(sym);
  return sym;
}

Symbol* ClassVerifier::create_temporary_symbol(const char *s, int length, TRAPS) {
  Symbol* sym = SymbolTable::new_symbol(s, length, CHECK_NULL);
  _symbols->push(sym);
  return sym;
}
