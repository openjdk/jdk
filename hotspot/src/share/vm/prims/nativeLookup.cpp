/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_nativeLookup.cpp.incl"


static void mangle_name_on(outputStream* st, symbolOop name, int begin, int end) {
  char* bytes = (char*)name->bytes() + begin;
  char* end_bytes = (char*)name->bytes() + end;
  while (bytes < end_bytes) {
    jchar c;
    bytes = UTF8::next(bytes, &c);
    if (c <= 0x7f && isalnum(c)) {
      st->put((char) c);
    } else {
           if (c == '_') st->print("_1");
      else if (c == '/') st->print("_");
      else if (c == ';') st->print("_2");
      else if (c == '[') st->print("_3");
      else               st->print("_%.5x", c);
    }
  }
}


static void mangle_name_on(outputStream* st, symbolOop name) {
  mangle_name_on(st, name, 0, name->utf8_length());
}


char* NativeLookup::pure_jni_name(methodHandle method) {
  stringStream st;
  // Prefix
  st.print("Java_");
  // Klass name
  mangle_name_on(&st, method->klass_name());
  st.print("_");
  // Method name
  mangle_name_on(&st, method->name());
  return st.as_string();
}


char* NativeLookup::long_jni_name(methodHandle method) {
  // Signature ignore the wrapping parenteses and the trailing return type
  stringStream st;
  symbolOop signature = method->signature();
  st.print("__");
  // find ')'
  int end;
  for (end = 0; end < signature->utf8_length() && signature->byte_at(end) != ')'; end++);
  // skip first '('
  mangle_name_on(&st, signature, 1, end);
  return st.as_string();
}

extern "C" {
  void JNICALL JVM_RegisterUnsafeMethods(JNIEnv *env, jclass unsafecls);
  void JNICALL JVM_RegisterMethodHandleMethods(JNIEnv *env, jclass unsafecls);
  void JNICALL JVM_RegisterPerfMethods(JNIEnv *env, jclass perfclass);
}

static address lookup_special_native(char* jni_name) {
  // NB: To ignore the jni prefix and jni postfix strstr is used matching.
  if (!JDK_Version::is_gte_jdk14x_version()) {
    // These functions only exist for compatibility with 1.3.1 and earlier
    // Intercept ObjectOutputStream getPrimitiveFieldValues for faster serialization
    if (strstr(jni_name, "Java_java_io_ObjectOutputStream_getPrimitiveFieldValues") != NULL) {
      return CAST_FROM_FN_PTR(address, JVM_GetPrimitiveFieldValues);
    }
    // Intercept ObjectInputStream setPrimitiveFieldValues for faster serialization
    if (strstr(jni_name, "Java_java_io_ObjectInputStream_setPrimitiveFieldValues") != NULL) {
      return CAST_FROM_FN_PTR(address, JVM_SetPrimitiveFieldValues);
    }
  }
  if (strstr(jni_name, "Java_sun_misc_Unsafe_registerNatives") != NULL) {
    return CAST_FROM_FN_PTR(address, JVM_RegisterUnsafeMethods);
  }
  if (strstr(jni_name, "Java_sun_dyn_MethodHandleNatives_registerNatives") != NULL) {
    return CAST_FROM_FN_PTR(address, JVM_RegisterMethodHandleMethods);
  }
  if (strstr(jni_name, "Java_sun_misc_Perf_registerNatives") != NULL) {
    return CAST_FROM_FN_PTR(address, JVM_RegisterPerfMethods);
  }

  return NULL;
}

address NativeLookup::lookup_style(methodHandle method, char* pure_name, const char* long_name, int args_size, bool os_style, bool& in_base_library, TRAPS) {
  address entry;
  // Compute complete JNI name for style
  stringStream st;
  if (os_style) os::print_jni_name_prefix_on(&st, args_size);
  st.print_raw(pure_name);
  st.print_raw(long_name);
  if (os_style) os::print_jni_name_suffix_on(&st, args_size);
  char* jni_name = st.as_string();

  // If the loader is null we have a system class, so we attempt a lookup in
  // the native Java library. This takes care of any bootstrapping problems.
  // Note: It is critical for bootstrapping that Java_java_lang_ClassLoader_00024NativeLibrary_find
  // gets found the first time around - otherwise an infinite loop can occure. This is
  // another VM/library dependency
  Handle loader(THREAD,
                instanceKlass::cast(method->method_holder())->class_loader());
  if (loader.is_null()) {
    entry = lookup_special_native(jni_name);
    if (entry == NULL) {
       entry = (address) hpi::dll_lookup(os::native_java_library(), jni_name);
    }
    if (entry != NULL) {
      in_base_library = true;
      return entry;
    }
  }

  // Otherwise call static method findNative in ClassLoader
  KlassHandle   klass (THREAD, SystemDictionary::classloader_klass());
  Handle name_arg = java_lang_String::create_from_str(jni_name, CHECK_NULL);

  JavaValue result(T_LONG);
  JavaCalls::call_static(&result,
                         klass,
                         vmSymbolHandles::findNative_name(),
                         vmSymbolHandles::classloader_string_long_signature(),
                         // Arguments
                         loader,
                         name_arg,
                         CHECK_NULL);
  entry = (address) (intptr_t) result.get_jlong();

  if (entry == NULL) {
    // findNative didn't find it, if there are any agent libraries look in them
    AgentLibrary* agent;
    for (agent = Arguments::agents(); agent != NULL; agent = agent->next()) {
      entry = (address) hpi::dll_lookup(agent->os_lib(), jni_name);
      if (entry != NULL) {
        return entry;
      }
    }
  }

  return entry;
}


// Check all the formats of native implementation name to see if there is one
// for the specified method.
address NativeLookup::lookup_entry(methodHandle method, bool& in_base_library, TRAPS) {
  address entry = NULL;
  in_base_library = false;
  // Compute pure name
  char* pure_name = pure_jni_name(method);

  // Compute argument size
  int args_size = 1                             // JNIEnv
                + (method->is_static() ? 1 : 0) // class for static methods
                + method->size_of_parameters(); // actual parameters


  // 1) Try JNI short style
  entry = lookup_style(method, pure_name, "",        args_size, true,  in_base_library, CHECK_NULL);
  if (entry != NULL) return entry;

  // Compute long name
  char* long_name = long_jni_name(method);

  // 2) Try JNI long style
  entry = lookup_style(method, pure_name, long_name, args_size, true,  in_base_library, CHECK_NULL);
  if (entry != NULL) return entry;

  // 3) Try JNI short style without os prefix/suffix
  entry = lookup_style(method, pure_name, "",        args_size, false, in_base_library, CHECK_NULL);
  if (entry != NULL) return entry;

  // 4) Try JNI long style without os prefix/suffix
  entry = lookup_style(method, pure_name, long_name, args_size, false, in_base_library, CHECK_NULL);

  return entry; // NULL indicates not found
}

// Check if there are any JVM TI prefixes which have been applied to the native method name.
// If any are found, remove them before attemping the look up of the
// native implementation again.
// See SetNativeMethodPrefix in the JVM TI Spec for more details.
address NativeLookup::lookup_entry_prefixed(methodHandle method, bool& in_base_library, TRAPS) {
  ResourceMark rm(THREAD);

  int prefix_count;
  char** prefixes = JvmtiExport::get_all_native_method_prefixes(&prefix_count);
  char* in_name = method->name()->as_C_string();
  char* wrapper_name = in_name;
  // last applied prefix will be first -- go backwards
  for (int i = prefix_count-1; i >= 0; i--) {
    char* prefix = prefixes[i];
    size_t prefix_len = strlen(prefix);
    if (strncmp(prefix, wrapper_name, prefix_len) == 0) {
      // has this prefix remove it
      wrapper_name += prefix_len;
    }
  }
  if (wrapper_name != in_name) {
    // we have a name for a wrapping method
    int wrapper_name_len = (int)strlen(wrapper_name);
    symbolHandle wrapper_symbol(THREAD, SymbolTable::probe(wrapper_name, wrapper_name_len));
    if (!wrapper_symbol.is_null()) {
      KlassHandle kh(method->method_holder());
      methodOop wrapper_method = Klass::cast(kh())->lookup_method(wrapper_symbol(),
                                                                  method->signature());
      if (wrapper_method != NULL && !wrapper_method->is_native()) {
        // we found a wrapper method, use its native entry
        method->set_is_prefixed_native();
        return lookup_entry(wrapper_method, in_base_library, THREAD);
      }
    }
  }
  return NULL;
}

address NativeLookup::lookup_base(methodHandle method, bool& in_base_library, TRAPS) {
  address entry = NULL;
  ResourceMark rm(THREAD);

  entry = lookup_entry(method, in_base_library, THREAD);
  if (entry != NULL) return entry;

  // standard native method resolution has failed.  Check if there are any
  // JVM TI prefixes which have been applied to the native method name.
  entry = lookup_entry_prefixed(method, in_base_library, THREAD);
  if (entry != NULL) return entry;

  // Native function not found, throw UnsatisfiedLinkError
  THROW_MSG_0(vmSymbols::java_lang_UnsatisfiedLinkError(),
              method->name_and_sig_as_C_string());
}


address NativeLookup::lookup(methodHandle method, bool& in_base_library, TRAPS) {
  if (!method->has_native_function()) {
    address entry = lookup_base(method, in_base_library, CHECK_NULL);
    method->set_native_function(entry,
      methodOopDesc::native_bind_event_is_interesting);
    // -verbose:jni printing
    if (PrintJNIResolving) {
      ResourceMark rm(THREAD);
      tty->print_cr("[Dynamic-linking native method %s.%s ... JNI]",
        Klass::cast(method->method_holder())->external_name(),
        method->name()->as_C_string());
    }
  }
  return method->native_function();
}

address NativeLookup::base_library_lookup(const char* class_name, const char* method_name, const char* signature) {
  EXCEPTION_MARK;
  bool in_base_library = true;  // SharedRuntime inits some math methods.
  symbolHandle c_name = oopFactory::new_symbol_handle(class_name,  CATCH);
  symbolHandle m_name = oopFactory::new_symbol_handle(method_name, CATCH);
  symbolHandle s_name = oopFactory::new_symbol_handle(signature,   CATCH);

  // Find the class
  klassOop k = SystemDictionary::resolve_or_fail(c_name, true, CATCH);
  instanceKlassHandle klass (THREAD, k);

  // Find method and invoke standard lookup
  methodHandle method (THREAD,
                       klass->uncached_lookup_method(m_name(), s_name()));
  address result = lookup(method, in_base_library, CATCH);
  assert(in_base_library, "must be in basic library");
  guarantee(result != NULL, "must be non NULL");
  return result;
}
