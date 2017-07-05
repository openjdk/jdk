/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_verifier.cpp.incl"

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
    void *func = hpi::dll_lookup(lib_handle, "VerifyClassCodesForMajorVersion");
    OrderAccess::release_store_ptr(&_verify_byte_codes_fn, func);
    if (func == NULL) {
      OrderAccess::release_store(&_is_new_verify_byte_codes_fn, false);
      func = hpi::dll_lookup(lib_handle, "VerifyClassCodes");
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

bool Verifier::verify(instanceKlassHandle klass, Verifier::Mode mode, bool should_verify_class, TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm;

  symbolHandle exception_name;
  const size_t message_buffer_len = klass->name()->utf8_length() + 1024;
  char* message_buffer = NEW_RESOURCE_ARRAY(char, message_buffer_len);

  const char* klassName = klass->external_name();

  // If the class should be verified, first see if we can use the split
  // verifier.  If not, or if verification fails and FailOverToOldVerifier
  // is set, then call the inference verifier.
  if (is_eligible_for_verification(klass, should_verify_class)) {
    if (TraceClassInitialization) {
      tty->print_cr("Start class verification for: %s", klassName);
    }
    if (UseSplitVerifier &&
        klass->major_version() >= STACKMAP_ATTRIBUTE_MAJOR_VERSION) {
        ClassVerifier split_verifier(
          klass, message_buffer, message_buffer_len, THREAD);
        split_verifier.verify_class(THREAD);
        exception_name = split_verifier.result();
      if (FailOverToOldVerifier && !HAS_PENDING_EXCEPTION &&
          (exception_name == vmSymbols::java_lang_VerifyError() ||
           exception_name == vmSymbols::java_lang_ClassFormatError())) {
        if (TraceClassInitialization) {
          tty->print_cr(
            "Fail over class verification to old verifier for: %s", klassName);
        }
        exception_name = inference_verify(
          klass, message_buffer, message_buffer_len, THREAD);
      }
    } else {
      exception_name = inference_verify(
          klass, message_buffer, message_buffer_len, THREAD);
    }

    if (TraceClassInitialization) {
      if (HAS_PENDING_EXCEPTION) {
        tty->print("Verification for %s has", klassName);
        tty->print_cr(" exception pending %s ",
          instanceKlass::cast(PENDING_EXCEPTION->klass())->external_name());
      } else if (!exception_name.is_null()) {
        tty->print_cr("Verification for %s failed", klassName);
      }
      tty->print_cr("End class verification for: %s", klassName);
    }
  }

  if (HAS_PENDING_EXCEPTION) {
    return false; // use the existing exception
  } else if (exception_name.is_null()) {
    return true; // verifcation succeeded
  } else { // VerifyError or ClassFormatError to be created and thrown
    ResourceMark rm(THREAD);
    instanceKlassHandle kls =
      SystemDictionary::resolve_or_fail(exception_name, true, CHECK_false);
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
    THROW_MSG_(exception_name, message_buffer, false);
  }
}

bool Verifier::is_eligible_for_verification(instanceKlassHandle klass, bool should_verify_class) {
  symbolOop name = klass->name();
  klassOop refl_magic_klass = SystemDictionary::reflect_magic_klass();

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
    // guarded by Universe::is_gte_jdk14x_version()/UseNewReflection.
    (refl_magic_klass == NULL ||
     !klass->is_subtype_of(refl_magic_klass) ||
     VerifyReflectionBytecodes)
  );
}

symbolHandle Verifier::inference_verify(
    instanceKlassHandle klass, char* message, size_t message_len, TRAPS) {
  JavaThread* thread = (JavaThread*)THREAD;
  JNIEnv *env = thread->jni_environment();

  void* verify_func = verify_byte_codes_fn();

  if (verify_func == NULL) {
    jio_snprintf(message, message_len, "Could not link verifier");
    return vmSymbols::java_lang_VerifyError();
  }

  ResourceMark rm(THREAD);
  if (ClassVerifier::_verify_verbose) {
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
  symbolHandle nh(NULL);
  if (result == 0) {
    return vmSymbols::java_lang_VerifyError();
  } else if (result == 1) {
    return nh; // verified.
  } else if (result == 2) {
    THROW_MSG_(vmSymbols::java_lang_OutOfMemoryError(), message, nh);
  } else if (result == 3) {
    return vmSymbols::java_lang_ClassFormatError();
  } else {
    ShouldNotReachHere();
    return nh;
  }
}

// Methods in ClassVerifier

bool ClassVerifier::_verify_verbose = false;

ClassVerifier::ClassVerifier(
    instanceKlassHandle klass, char* msg, size_t msg_len, TRAPS)
    : _thread(THREAD), _exception_type(symbolHandle()), _message(msg),
      _message_buffer_len(msg_len), _klass(klass) {
  _this_type = VerificationType::reference_type(klass->name());
}

ClassVerifier::~ClassVerifier() {
}

void ClassVerifier::verify_class(TRAPS) {
  if (_verify_verbose) {
    tty->print_cr("Verifying class %s with new format",
      _klass->external_name());
  }

  objArrayHandle methods(THREAD, _klass->methods());
  int num_methods = methods->length();

  for (int index = 0; index < num_methods; index++) {
    methodOop m = (methodOop)methods->obj_at(index);
    if (m->is_native() || m->is_abstract()) {
      // If m is native or abstract, skip it.  It is checked in class file
      // parser that methods do not override a final method.
      continue;
    }
    verify_method(methodHandle(THREAD, m), CHECK_VERIFY(this));
  }
}

void ClassVerifier::verify_method(methodHandle m, TRAPS) {
  ResourceMark rm(THREAD);
  _method = m;   // initialize _method
  if (_verify_verbose) {
    tty->print_cr("Verifying method %s", m->name_and_sig_as_C_string());
  }

  const char* bad_type_msg = "Bad type on operand stack in %s";

  int32_t max_stack = m->max_stack();
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

  typeArrayHandle stackmap_data(THREAD, m->stackmap_data());
  StackMapStream stream(stackmap_data);
  StackMapReader reader(this, &stream, code_data, code_length, THREAD);
  StackMapTable stackmap_table(&reader, &current_frame, max_locals, max_stack,
                               code_data, code_length, CHECK_VERIFY(this));

  if (_verify_verbose) {
    stackmap_table.print();
  }

  RawBytecodeStream bcs(m);

  // Scan the byte code linearly from the start to the end
  bool no_control_flow = false; // Set to true when there is no direct control
                                // flow from current instruction to the next
                                // instruction in sequence
  Bytecodes::Code opcode;
  while (!bcs.is_last_bytecode()) {
    opcode = bcs.raw_next();
    u2 bci = bcs.bci();

    // Set current frame's offset to bci
    current_frame.set_offset(bci);

    // Make sure every offset in stackmap table point to the beginning to
    // an instruction. Match current_frame to stackmap_table entry with
    // the same offset if exists.
    stackmap_index = verify_stackmap_table(
      stackmap_index, bci, &current_frame, &stackmap_table,
      no_control_flow, CHECK_VERIFY(this));

    bool this_uninit = false;  // Set to true when invokespecial <init> initialized 'this'

    // Merge with the next instruction
    {
      u2 index;
      int target;
      VerificationType type, type2;
      VerificationType atype;

#ifndef PRODUCT
      if (_verify_verbose) {
        current_frame.print();
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
          verify_error(bci, "Bad wide instruction");
          return;
        }
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
            opcode, bcs.get_index(), &current_frame,
            cp, bci, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_ldc_w :
        case Bytecodes::_ldc2_w :
          verify_ldc(
            opcode, bcs.get_index_big(), &current_frame,
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
            verify_error(bci, bad_type_msg, "iaload");
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
            verify_error(bci, bad_type_msg, "baload");
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
            verify_error(bci, bad_type_msg, "caload");
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
            verify_error(bci, bad_type_msg, "saload");
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
            verify_error(bci, bad_type_msg, "laload");
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
            verify_error(bci, bad_type_msg, "faload");
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
            verify_error(bci, bad_type_msg, "daload");
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
            verify_error(bci, bad_type_msg, "aaload");
            return;
          }
          if (atype.is_null()) {
            current_frame.push_stack(
              VerificationType::null_type(), CHECK_VERIFY(this));
          } else {
            VerificationType component =
              atype.get_component(CHECK_VERIFY(this));
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
            verify_error(bci, bad_type_msg, "iastore");
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
            verify_error(bci, bad_type_msg, "bastore");
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
            verify_error(bci, bad_type_msg, "castore");
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
            verify_error(bci, bad_type_msg, "sastore");
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
            verify_error(bci, bad_type_msg, "lastore");
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
            verify_error(bci, bad_type_msg, "fastore");
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
            verify_error(bci, bad_type_msg, "dastore");
            return;
          }
          no_control_flow = false; break;
        case Bytecodes::_aastore :
          type = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          type2 = current_frame.pop_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          atype = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          // more type-checking is done at runtime
          if (!atype.is_reference_array()) {
            verify_error(bci, bad_type_msg, "aastore");
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
            verify_error(bci, bad_type_msg, "pop2");
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
            verify_error(bci, bad_type_msg, "dup_x2");
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
            verify_error(bci, bad_type_msg, "dup2");
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
          } else if(type.is_category2_2nd()) {
            type2 = current_frame.pop_stack
              (VerificationType::category2_check(), CHECK_VERIFY(this));
          } else {
            verify_error(bci, bad_type_msg, "dup2_x1");
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
            verify_error(bci, bad_type_msg, "dup2_x2");
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
            verify_error(bci, bad_type_msg, "dup2_x2");
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
          verify_return_value(return_type, type, bci, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_lreturn :
          type2 = current_frame.pop_stack(
            VerificationType::long2_type(), CHECK_VERIFY(this));
          type = current_frame.pop_stack(
            VerificationType::long_type(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_freturn :
          type = current_frame.pop_stack(
            VerificationType::float_type(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_dreturn :
          type2 = current_frame.pop_stack(
            VerificationType::double2_type(),  CHECK_VERIFY(this));
          type = current_frame.pop_stack(
            VerificationType::double_type(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_areturn :
          type = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          verify_return_value(return_type, type, bci, CHECK_VERIFY(this));
          no_control_flow = true; break;
        case Bytecodes::_return :
          if (return_type != VerificationType::bogus_type()) {
            verify_error(bci, "Method expects no return value");
            return;
          }
          // Make sure "this" has been initialized if current method is an
          // <init>
          if (_method->name() == vmSymbols::object_initializer_name() &&
              current_frame.flag_this_uninit()) {
            verify_error(bci,
              "Constructor must call super() or this() before return");
            return;
          }
          no_control_flow = true; break;
        case Bytecodes::_getstatic :
        case Bytecodes::_putstatic :
        case Bytecodes::_getfield :
        case Bytecodes::_putfield :
          verify_field_instructions(
            &bcs, &current_frame, cp, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_invokevirtual :
        case Bytecodes::_invokespecial :
        case Bytecodes::_invokestatic :
          verify_invoke_instructions(
            &bcs, code_length, &current_frame,
            &this_uninit, return_type, cp, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_invokeinterface :
        case Bytecodes::_invokedynamic :
          verify_invoke_instructions(
            &bcs, code_length, &current_frame,
            &this_uninit, return_type, cp, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_new :
        {
          index = bcs.get_index_big();
          verify_cp_class_type(index, cp, CHECK_VERIFY(this));
          VerificationType new_class_type =
            cp_index_to_type(index, cp, CHECK_VERIFY(this));
          if (!new_class_type.is_object()) {
            verify_error(bci, "Illegal new instruction");
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
            bcs.get_index_big(), cp, &current_frame, CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_arraylength :
          type = current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          if (!(type.is_null() || type.is_array())) {
            verify_error(bci, bad_type_msg, "arraylength");
          }
          current_frame.push_stack(
            VerificationType::integer_type(), CHECK_VERIFY(this));
          no_control_flow = false; break;
        case Bytecodes::_checkcast :
        {
          index = bcs.get_index_big();
          verify_cp_class_type(index, cp, CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
          VerificationType klass_type = cp_index_to_type(
            index, cp, CHECK_VERIFY(this));
          current_frame.push_stack(klass_type, CHECK_VERIFY(this));
          no_control_flow = false; break;
        }
        case Bytecodes::_instanceof : {
          index = bcs.get_index_big();
          verify_cp_class_type(index, cp, CHECK_VERIFY(this));
          current_frame.pop_stack(
            VerificationType::reference_check(), CHECK_VERIFY(this));
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
          index = bcs.get_index_big();
          u2 dim = *(bcs.bcp()+3);
          verify_cp_class_type(index, cp, CHECK_VERIFY(this));
          VerificationType new_array_type =
            cp_index_to_type(index, cp, CHECK_VERIFY(this));
          if (!new_array_type.is_array()) {
            verify_error(bci,
              "Illegal constant pool index in multianewarray instruction");
            return;
          }
          if (dim < 1 || new_array_type.dimensions() < dim) {
            verify_error(bci,
              "Illegal dimension in multianewarray instruction");
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
          verify_error(bci, "Bad instruction");
          no_control_flow = false;
          return;
      }  // end switch
    }  // end Merge with the next instruction

    // Look for possible jump target in exception handlers and see if it
    // matches current_frame
    if (bci >= ex_min && bci < ex_max) {
      verify_exception_handler_targets(
        bci, this_uninit, &current_frame, &stackmap_table, CHECK_VERIFY(this));
    }
  } // end while

  // Make sure that control flow does not fall through end of the method
  if (!no_control_flow) {
    verify_error(code_length, "Control flow falls through code end");
    return;
  }
}

char* ClassVerifier::generate_code_data(methodHandle m, u4 code_length, TRAPS) {
  char* code_data = NEW_RESOURCE_ARRAY(char, code_length);
  memset(code_data, 0, sizeof(char) * code_length);
  RawBytecodeStream bcs(m);

  while (!bcs.is_last_bytecode()) {
    if (bcs.raw_next() != Bytecodes::_illegal) {
      int bci = bcs.bci();
      if (bcs.code() == Bytecodes::_new) {
        code_data[bci] = NEW_OFFSET;
      } else {
        code_data[bci] = BYTECODE_OFFSET;
      }
    } else {
      verify_error(bcs.bci(), "Bad instruction");
      return NULL;
    }
  }

  return code_data;
}

void ClassVerifier::verify_exception_handler_table(u4 code_length, char* code_data, int& min, int& max, TRAPS) {
  typeArrayHandle exhandlers (THREAD, _method->exception_table());
  constantPoolHandle cp (THREAD, _method->constants());

  if (exhandlers() != NULL) {
    for(int i = 0; i < exhandlers->length();) {
      u2 start_pc = exhandlers->int_at(i++);
      u2 end_pc = exhandlers->int_at(i++);
      u2 handler_pc = exhandlers->int_at(i++);
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
      int catch_type_index = exhandlers->int_at(i++);
      if (catch_type_index != 0) {
        VerificationType catch_type = cp_index_to_type(
          catch_type_index, cp, CHECK_VERIFY(this));
        VerificationType throwable =
          VerificationType::reference_type(vmSymbols::java_lang_Throwable());
        bool is_subclass = throwable.is_assignable_from(
          catch_type, current_class(), CHECK_VERIFY(this));
        if (!is_subclass) {
          // 4286534: should throw VerifyError according to recent spec change
          verify_error(
            "Catch type is not a subclass of Throwable in handler %d",
            handler_pc);
          return;
        }
      }
      if (start_pc < min) min = start_pc;
      if (end_pc > max) max = end_pc;
    }
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
      verify_error(bci, "Expecting a stack map frame");
      return 0;
    }
    if (this_offset == bci) {
      // See if current stack map can be assigned to the frame in table.
      // current_frame is the stackmap frame got from the last instruction.
      // If matched, current_frame will be updated by this method.
      bool match = stackmap_table->match_stackmap(
        current_frame, this_offset, stackmap_index,
        !no_control_flow, true, CHECK_VERIFY_(this, 0));
      if (!match) {
        // report type error
        verify_error(bci, "Instruction type does not match stack map");
        return 0;
      }
      stackmap_index++;
    } else if (this_offset < bci) {
      // current_offset should have met this_offset.
      class_format_error("Bad stack map offset %d", this_offset);
      return 0;
    }
  } else if (no_control_flow) {
    verify_error(bci, "Expecting a stack map frame");
    return 0;
  }
  return stackmap_index;
}

void ClassVerifier::verify_exception_handler_targets(u2 bci, bool this_uninit, StackMapFrame* current_frame,
                                                     StackMapTable* stackmap_table, TRAPS) {
  constantPoolHandle cp (THREAD, _method->constants());
  typeArrayHandle exhandlers (THREAD, _method->exception_table());
  if (exhandlers() != NULL) {
    for(int i = 0; i < exhandlers->length();) {
      u2 start_pc = exhandlers->int_at(i++);
      u2 end_pc = exhandlers->int_at(i++);
      u2 handler_pc = exhandlers->int_at(i++);
      int catch_type_index = exhandlers->int_at(i++);
      if(bci >= start_pc && bci < end_pc) {
        u1 flags = current_frame->flags();
        if (this_uninit) {  flags |= FLAG_THIS_UNINIT; }

        ResourceMark rm(THREAD);
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
        bool match = stackmap_table->match_stackmap(
          new_frame, handler_pc, true, false, CHECK_VERIFY(this));
        if (!match) {
          verify_error(bci,
            "Stack map does not match the one at exception handler %d",
            handler_pc);
          return;
        }
      }
    }
  }
}

void ClassVerifier::verify_cp_index(constantPoolHandle cp, int index, TRAPS) {
  int nconstants = cp->length();
  if ((index <= 0) || (index >= nconstants)) {
    verify_error("Illegal constant pool index %d in class %s",
      index, instanceKlass::cast(cp->pool_holder())->external_name());
    return;
  }
}

void ClassVerifier::verify_cp_type(
    int index, constantPoolHandle cp, unsigned int types, TRAPS) {

  // In some situations, bytecode rewriting may occur while we're verifying.
  // In this case, a constant pool cache exists and some indices refer to that
  // instead.  Get the original index for the tag check
  constantPoolCacheOop cache = cp->cache();
  if (cache != NULL &&
       ((types == (1 <<  JVM_CONSTANT_InterfaceMethodref)) ||
        (types == (1 <<  JVM_CONSTANT_Methodref)) ||
        (types == (1 <<  JVM_CONSTANT_Fieldref)))) {
    int native_index = index;
    if (Bytes::is_Java_byte_ordering_different()) {
      native_index = Bytes::swap_u2(index);
    }
    assert((native_index >= 0) && (native_index < cache->length()),
      "Must be a legal index into the cp cache");
    index = cache->entry_at(native_index)->constant_pool_index();
  }

  verify_cp_index(cp, index, CHECK_VERIFY(this));
  unsigned int tag = cp->tag_at(index).value();
  if ((types & (1 << tag)) == 0) {
    verify_error(
      "Illegal type at constant pool entry %d in class %s",
      index, instanceKlass::cast(cp->pool_holder())->external_name());
    return;
  }
}

void ClassVerifier::verify_cp_class_type(
    int index, constantPoolHandle cp, TRAPS) {
  verify_cp_index(cp, index, CHECK_VERIFY(this));
  constantTag tag = cp->tag_at(index);
  if (!tag.is_klass() && !tag.is_unresolved_klass()) {
    verify_error("Illegal type at constant pool entry %d in class %s",
      index, instanceKlass::cast(cp->pool_holder())->external_name());
    return;
  }
}

void ClassVerifier::format_error_message(
    const char* fmt, int offset, va_list va) {
  ResourceMark rm(_thread);
  stringStream message(_message, _message_buffer_len);
  message.vprint(fmt, va);
  if (!_method.is_null()) {
    message.print(" in method %s", _method->name_and_sig_as_C_string());
  }
  if (offset != -1) {
    message.print(" at offset %d", offset);
  }
}

void ClassVerifier::verify_error(u2 offset, const char* fmt, ...) {
  _exception_type = vmSymbols::java_lang_VerifyError();
  va_list va;
  va_start(va, fmt);
  format_error_message(fmt, offset, va);
  va_end(va);
}

void ClassVerifier::verify_error(const char* fmt, ...) {
  _exception_type = vmSymbols::java_lang_VerifyError();
  va_list va;
  va_start(va, fmt);
  format_error_message(fmt, -1, va);
  va_end(va);
}

void ClassVerifier::class_format_error(const char* msg, ...) {
  _exception_type = vmSymbols::java_lang_ClassFormatError();
  va_list va;
  va_start(va, msg);
  format_error_message(msg, -1, va);
  va_end(va);
}

klassOop ClassVerifier::load_class(symbolHandle name, TRAPS) {
  // Get current loader and protection domain first.
  oop loader = current_class()->class_loader();
  oop protection_domain = current_class()->protection_domain();

  return SystemDictionary::resolve_or_fail(
    name, Handle(THREAD, loader), Handle(THREAD, protection_domain),
    true, CHECK_NULL);
}

bool ClassVerifier::is_protected_access(instanceKlassHandle this_class,
                                        klassOop target_class,
                                        symbolOop field_name,
                                        symbolOop field_sig,
                                        bool is_method) {
  No_Safepoint_Verifier nosafepoint;

  // If target class isn't a super class of this class, we don't worry about this case
  if (!this_class->is_subclass_of(target_class)) {
    return false;
  }
  // Check if the specified method or field is protected
  instanceKlass* target_instance = instanceKlass::cast(target_class);
  fieldDescriptor fd;
  if (is_method) {
    methodOop m = target_instance->uncached_lookup_method(field_name, field_sig);
    if (m != NULL && m->is_protected()) {
      if (!this_class->is_same_class_package(m->method_holder())) {
        return true;
      }
    }
  } else {
    klassOop member_klass = target_instance->find_field(field_name, field_sig, &fd);
    if(member_klass != NULL && fd.is_protected()) {
      if (!this_class->is_same_class_package(member_klass)) {
        return true;
      }
    }
  }
  return false;
}

void ClassVerifier::verify_ldc(
    int opcode, u2 index, StackMapFrame *current_frame,
     constantPoolHandle cp, u2 bci, TRAPS) {
  verify_cp_index(cp, index, CHECK_VERIFY(this));
  constantTag tag = cp->tag_at(index);
  unsigned int types;
  if (opcode == Bytecodes::_ldc || opcode == Bytecodes::_ldc_w) {
    if (!tag.is_unresolved_string() && !tag.is_unresolved_klass()) {
      types = (1 << JVM_CONSTANT_Integer) | (1 << JVM_CONSTANT_Float)
            | (1 << JVM_CONSTANT_String)  | (1 << JVM_CONSTANT_Class);
      verify_cp_type(index, cp, types, CHECK_VERIFY(this));
    }
  } else {
    assert(opcode == Bytecodes::_ldc2_w, "must be ldc2_w");
    types = (1 << JVM_CONSTANT_Double) | (1 << JVM_CONSTANT_Long);
    verify_cp_type(index, cp, types, CHECK_VERIFY(this));
  }
  if (tag.is_string() && cp->is_pseudo_string_at(index)) {
    current_frame->push_stack(
      VerificationType::reference_type(
        vmSymbols::java_lang_Object()), CHECK_VERIFY(this));
  } else if (tag.is_string() || tag.is_unresolved_string()) {
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
  } else {
    verify_error(bci, "Invalid index in ldc");
    return;
  }
}

void ClassVerifier::verify_switch(
    RawBytecodeStream* bcs, u4 code_length, char* code_data,
    StackMapFrame* current_frame, StackMapTable* stackmap_table, TRAPS) {
  int bci = bcs->bci();
  address bcp = bcs->bcp();
  address aligned_bcp = (address) round_to((intptr_t)(bcp + 1), jintSize);

  // 4639449 & 4647081: padding bytes must be 0
  u2 padding_offset = 1;
  while ((bcp + padding_offset) < aligned_bcp) {
    if(*(bcp + padding_offset) != 0) {
      verify_error(bci, "Nonzero padding byte in lookswitch or tableswitch");
      return;
    }
    padding_offset++;
  }
  int default_offset = (int) Bytes::get_Java_u4(aligned_bcp);
  int keys, delta;
  current_frame->pop_stack(
    VerificationType::integer_type(), CHECK_VERIFY(this));
  if (bcs->code() == Bytecodes::_tableswitch) {
    jint low = (jint)Bytes::get_Java_u4(aligned_bcp + jintSize);
    jint high = (jint)Bytes::get_Java_u4(aligned_bcp + 2*jintSize);
    if (low > high) {
      verify_error(bci,
        "low must be less than or equal to high in tableswitch");
      return;
    }
    keys = high - low + 1;
    if (keys < 0) {
      verify_error(bci, "too many keys in tableswitch");
      return;
    }
    delta = 1;
  } else {
    keys = (int)Bytes::get_Java_u4(aligned_bcp + jintSize);
    if (keys < 0) {
      verify_error(bci, "number of keys in lookupswitch less than 0");
      return;
    }
    delta = 2;
    // Make sure that the lookupswitch items are sorted
    for (int i = 0; i < (keys - 1); i++) {
      jint this_key = Bytes::get_Java_u4(aligned_bcp + (2+2*i)*jintSize);
      jint next_key = Bytes::get_Java_u4(aligned_bcp + (2+2*i+2)*jintSize);
      if (this_key >= next_key) {
        verify_error(bci, "Bad lookupswitch instruction");
        return;
      }
    }
  }
  int target = bci + default_offset;
  stackmap_table->check_jump_target(current_frame, target, CHECK_VERIFY(this));
  for (int i = 0; i < keys; i++) {
    target = bci + (jint)Bytes::get_Java_u4(aligned_bcp+(3+i*delta)*jintSize);
    stackmap_table->check_jump_target(
      current_frame, target, CHECK_VERIFY(this));
  }
}

bool ClassVerifier::name_in_supers(
    symbolOop ref_name, instanceKlassHandle current) {
  klassOop super = current->super();
  while (super != NULL) {
    if (super->klass_part()->name() == ref_name) {
      return true;
    }
    super = super->klass_part()->super();
  }
  return false;
}

void ClassVerifier::verify_field_instructions(RawBytecodeStream* bcs,
                                              StackMapFrame* current_frame,
                                              constantPoolHandle cp,
                                              TRAPS) {
  u2 index = bcs->get_index_big();
  verify_cp_type(index, cp, 1 << JVM_CONSTANT_Fieldref, CHECK_VERIFY(this));

  // Get field name and signature
  symbolHandle field_name = symbolHandle(THREAD, cp->name_ref_at(index));
  symbolHandle field_sig = symbolHandle(THREAD, cp->signature_ref_at(index));

  if (!SignatureVerifier::is_valid_type_signature(field_sig)) {
    class_format_error(
      "Invalid signature for field in class %s referenced "
      "from constant pool index %d", _klass->external_name(), index);
    return;
  }

  // Get referenced class type
  VerificationType ref_class_type = cp_ref_index_to_type(
    index, cp, CHECK_VERIFY(this));
  if (!ref_class_type.is_object()) {
    verify_error(
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
  switch (bcs->code()) {
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
          _klass->find_local_field(field_name(), field_sig(), &fd)) {
        stack_object_type = current_type();
      }
      is_assignable = target_class_type.is_assignable_from(
        stack_object_type, current_class(), CHECK_VERIFY(this));
      if (!is_assignable) {
        verify_error(bci, "Bad type on operand stack in putfield");
        return;
      }
    }
    check_protected: {
      if (_this_type == stack_object_type)
        break; // stack_object_type must be assignable to _current_class_type
      symbolHandle ref_class_name = symbolHandle(THREAD,
        cp->klass_name_at(cp->klass_ref_index_at(index)));
      if (!name_in_supers(ref_class_name(), current_class()))
        // stack_object_type must be assignable to _current_class_type since:
        // 1. stack_object_type must be assignable to ref_class.
        // 2. ref_class must be _current_class or a subclass of it. It can't
        //    be a superclass of it. See revised JVMS 5.4.4.
        break;

      klassOop ref_class_oop = load_class(ref_class_name, CHECK);
      if (is_protected_access(current_class(), ref_class_oop, field_name(),
                              field_sig(), false)) {
        // It's protected access, check if stack object is assignable to
        // current class.
        is_assignable = current_type().is_assignable_from(
          stack_object_type, current_class(), CHECK_VERIFY(this));
        if (!is_assignable) {
          verify_error(bci, "Bad access to protected data in getfield");
          return;
        }
      }
      break;
    }
    default: ShouldNotReachHere();
  }
}

void ClassVerifier::verify_invoke_init(
    RawBytecodeStream* bcs, VerificationType ref_class_type,
    StackMapFrame* current_frame, u4 code_length, bool *this_uninit,
    constantPoolHandle cp, TRAPS) {
  u2 bci = bcs->bci();
  VerificationType type = current_frame->pop_stack(
    VerificationType::reference_check(), CHECK_VERIFY(this));
  if (type == VerificationType::uninitialized_this_type()) {
    // The method must be an <init> method of either this class, or one of its
    // superclasses
    klassOop oop = current_class()();
    Klass* klass = oop->klass_part();
    while (klass != NULL && ref_class_type.name() != klass->name()) {
      klass = klass->super()->klass_part();
    }
    if (klass == NULL) {
      verify_error(bci, "Bad <init> method call");
      return;
    }
    current_frame->initialize_object(type, current_type());
    *this_uninit = true;
  } else if (type.is_uninitialized()) {
    u2 new_offset = type.bci();
    address new_bcp = bcs->bcp() - bci + new_offset;
    if (new_offset > (code_length - 3) || (*new_bcp) != Bytecodes::_new) {
      verify_error(new_offset, "Expecting new instruction");
      return;
    }
    u2 new_class_index = Bytes::get_Java_u2(new_bcp + 1);
    verify_cp_class_type(new_class_index, cp, CHECK_VERIFY(this));

    // The method must be an <init> method of the indicated class
    VerificationType new_class_type = cp_index_to_type(
      new_class_index, cp, CHECK_VERIFY(this));
    if (!new_class_type.equals(ref_class_type)) {
      verify_error(bci, "Call to wrong <init> method");
      return;
    }
    // According to the VM spec, if the referent class is a superclass of the
    // current class, and is in a different runtime package, and the method is
    // protected, then the objectref must be the current class or a subclass
    // of the current class.
    VerificationType objectref_type = new_class_type;
    if (name_in_supers(ref_class_type.name(), current_class())) {
      klassOop ref_klass = load_class(
        ref_class_type.name(), CHECK_VERIFY(this));
      methodOop m = instanceKlass::cast(ref_klass)->uncached_lookup_method(
        vmSymbols::object_initializer_name(),
        cp->signature_ref_at(bcs->get_index_big()));
      instanceKlassHandle mh(THREAD, m->method_holder());
      if (m->is_protected() && !mh->is_same_class_package(_klass())) {
        bool assignable = current_type().is_assignable_from(
          objectref_type, current_class(), CHECK_VERIFY(this));
        if (!assignable) {
          verify_error(bci, "Bad access to protected <init> method");
          return;
        }
      }
    }
    current_frame->initialize_object(type, new_class_type);
  } else {
    verify_error(bci, "Bad operand type when invoking <init>");
    return;
  }
}

void ClassVerifier::verify_invoke_instructions(
    RawBytecodeStream* bcs, u4 code_length, StackMapFrame* current_frame,
    bool *this_uninit, VerificationType return_type,
    constantPoolHandle cp, TRAPS) {
  // Make sure the constant pool item is the right type
  u2 index = bcs->get_index_big();
  Bytecodes::Code opcode = bcs->code();
  unsigned int types = (opcode == Bytecodes::_invokeinterface
                                ? 1 << JVM_CONSTANT_InterfaceMethodref
                      : opcode == Bytecodes::_invokedynamic
                                ? 1 << JVM_CONSTANT_NameAndType
                                : 1 << JVM_CONSTANT_Methodref);
  verify_cp_type(index, cp, types, CHECK_VERIFY(this));

  // Get method name and signature
  symbolHandle method_name;
  symbolHandle method_sig;
  if (opcode == Bytecodes::_invokedynamic) {
    int name_index = cp->name_ref_index_at(index);
    int sig_index  = cp->signature_ref_index_at(index);
    method_name = symbolHandle(THREAD, cp->symbol_at(name_index));
    method_sig  = symbolHandle(THREAD, cp->symbol_at(sig_index));
  } else {
    method_name = symbolHandle(THREAD, cp->name_ref_at(index));
    method_sig  = symbolHandle(THREAD, cp->signature_ref_at(index));
  }

  if (!SignatureVerifier::is_valid_method_signature(method_sig)) {
    class_format_error(
      "Invalid method signature in class %s referenced "
      "from constant pool index %d", _klass->external_name(), index);
    return;
  }

  // Get referenced class type
  VerificationType ref_class_type;
  if (opcode == Bytecodes::_invokedynamic) {
    if (!EnableInvokeDynamic) {
      class_format_error(
        "invokedynamic instructions not enabled on this JVM",
        _klass->external_name());
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
      verify_error(bci, "Inconsistent args count operand in invokeinterface");
      return;
    }
    if (*(bcp+4) != 0) {
      verify_error(bci, "Fourth operand byte of invokeinterface must be zero");
      return;
    }
  }

  if (opcode == Bytecodes::_invokedynamic) {
    address bcp = bcs->bcp();
    if (*(bcp+3) != 0 || *(bcp+4) != 0) {
      verify_error(bci, "Third and fourth operand bytes of invokedynamic must be zero");
      return;
    }
  }

  if (method_name->byte_at(0) == '<') {
    // Make sure <init> can only be invoked by invokespecial
    if (opcode != Bytecodes::_invokespecial ||
        method_name() != vmSymbols::object_initializer_name()) {
      verify_error(bci, "Illegal call to internal method");
      return;
    }
  } else if (opcode == Bytecodes::_invokespecial
             && !ref_class_type.equals(current_type())
             && !ref_class_type.equals(VerificationType::reference_type(
                  current_class()->super()->klass_part()->name()))) {
    bool subtype = ref_class_type.is_assignable_from(
      current_type(), current_class(), CHECK_VERIFY(this));
    if (!subtype) {
      verify_error(bci, "Bad invokespecial instruction: "
          "current class isn't assignable to reference class.");
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
    if (method_name() == vmSymbols::object_initializer_name()) {  // <init> method
      verify_invoke_init(bcs, ref_class_type, current_frame,
        code_length, this_uninit, cp, CHECK_VERIFY(this));
    } else {   // other methods
      // Ensures that target class is assignable to method class.
      if (opcode == Bytecodes::_invokespecial) {
        current_frame->pop_stack(current_type(), CHECK_VERIFY(this));
      } else if (opcode == Bytecodes::_invokevirtual) {
        VerificationType stack_object_type =
          current_frame->pop_stack(ref_class_type, CHECK_VERIFY(this));
        if (current_type() != stack_object_type) {
          assert(cp->cache() == NULL, "not rewritten yet");
          symbolHandle ref_class_name = symbolHandle(THREAD,
            cp->klass_name_at(cp->klass_ref_index_at(index)));
          // See the comments in verify_field_instructions() for
          // the rationale behind this.
          if (name_in_supers(ref_class_name(), current_class())) {
            klassOop ref_class = load_class(ref_class_name, CHECK);
            if (is_protected_access(
                  _klass, ref_class, method_name(), method_sig(), true)) {
              // It's protected access, check if stack object is
              // assignable to current class.
              bool is_assignable = current_type().is_assignable_from(
                stack_object_type, current_class(), CHECK_VERIFY(this));
              if (!is_assignable) {
                if (ref_class_type.name() == vmSymbols::java_lang_Object()
                    && stack_object_type.is_array()
                    && method_name() == vmSymbols::clone_name()) {
                  // Special case: arrays pretend to implement public Object
                  // clone().
                } else {
                  verify_error(bci,
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
    if (method_name() == vmSymbols::object_initializer_name()) {
      // <init> method must have a void return type
      verify_error(bci, "Return type must be void in <init> method");
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
    verify_error(bci, "Illegal newarray instruction");
    return VerificationType::bogus_type();
  }

  // from_bt[index] contains the array signature which has a length of 2
  symbolHandle sig = oopFactory::new_symbol_handle(
    from_bt[index], 2, CHECK_(VerificationType::bogus_type()));
  return VerificationType::reference_type(sig);
}

void ClassVerifier::verify_anewarray(
    u2 index, constantPoolHandle cp, StackMapFrame* current_frame, TRAPS) {
  verify_cp_class_type(index, cp, CHECK_VERIFY(this));
  current_frame->pop_stack(
    VerificationType::integer_type(), CHECK_VERIFY(this));

  VerificationType component_type =
    cp_index_to_type(index, cp, CHECK_VERIFY(this));
  ResourceMark rm(THREAD);
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
  symbolHandle arr_sig = oopFactory::new_symbol_handle(
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
    VerificationType return_type, VerificationType type, u2 bci, TRAPS) {
  if (return_type == VerificationType::bogus_type()) {
    verify_error(bci, "Method expects a return value");
    return;
  }
  bool match = return_type.is_assignable_from(type, _klass, CHECK_VERIFY(this));
  if (!match) {
    verify_error(bci, "Bad return type");
    return;
  }
}
