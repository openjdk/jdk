/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassInitializer.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/vmSymbols.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/javaCalls.hpp"

class AOTClassInitializer::AllowedSpec {
  const char* _class_name;
  bool _is_prefix;
  int _len;
public:
  AllowedSpec(const char* class_name, bool is_prefix = false)
    : _class_name(class_name), _is_prefix(is_prefix)
  {
    _len = (class_name == nullptr) ? 0 : (int)strlen(class_name);
  }
  const char* class_name() { return _class_name; }

  bool matches(Symbol* name, int len) {
    if (_is_prefix) {
      return len >= _len && name->starts_with(_class_name);
    } else {
      return len == _len && name->equals(_class_name);
    }
  }
};


bool AOTClassInitializer::is_allowed(AllowedSpec* specs, InstanceKlass* ik) {
  Symbol* name = ik->name();
  int len = name->utf8_length();
  for (AllowedSpec* s = specs; s->class_name() != nullptr; s++) {
    if (s->matches(name, len)) {
      // If a type is included in the tables inside can_archive_initialized_mirror(), we require that
      //   - all super classes must be included
      //   - all super interfaces that have <clinit> must be included.
      // This ensures that in the production run, we don't run the <clinit> of a supertype but skips
      // ik's <clinit>.
      if (ik->java_super() != nullptr) {
        DEBUG_ONLY(ResourceMark rm);
        assert(AOTClassInitializer::can_archive_initialized_mirror(ik->java_super()),
               "super class %s of %s must be aot-initialized", ik->java_super()->external_name(),
               ik->external_name());
      }

      Array<InstanceKlass*>* interfaces = ik->local_interfaces();
      int len = interfaces->length();
      for (int i = 0; i < len; i++) {
        InstanceKlass* intf = interfaces->at(i);
        if (intf->class_initializer() != nullptr) {
          assert(AOTClassInitializer::can_archive_initialized_mirror(intf),
                 "super interface %s (which has <clinit>) of %s must be aot-initialized", intf->external_name(),
                 ik->external_name());
        }
      }

      return true;
    }
  }
  return false;
}


bool AOTClassInitializer::can_archive_initialized_mirror(InstanceKlass* ik) {
  assert(!ArchiveBuilder::current()->is_in_buffer_space(ik), "must be source klass");
  if (!CDSConfig::is_initing_classes_at_dump_time()) {
    return false;
  }

  if (!ik->is_initialized()) {
    return false;
  }

  if (ik->is_hidden()) {
    return HeapShared::is_archivable_hidden_klass(ik);
  }

  if (ik->java_super() == vmClasses::Enum_klass()) {
    return true;
  }

  // About "static field that may hold a different value" errors:
  //
  // Automatic selection for aot-inited classes
  // ==========================================
  //
  // When CDSConfig::is_initing_classes_at_dump_time() is enabled,
  // HeapShared::find_all_aot_initialized_classes() finds the classes of all
  // heap objects that are reachable from HeapShared::_run_time_special_subgraph,
  // and mark these classes as aot-inited. This preserves the initialized
  // mirrors of these classes, and their <clinit> methods are NOT executed
  // at runtime.
  //
  // For example, with -XX:+AOTInvokeDynamicLinking, _run_time_special_subgraph
  // will contain some DirectMethodHandle objects. As a result, the DirectMethodHandle
  // class is automatically marked as aot-inited.
  //
  // Manual selection
  // ================
  //
  // However, there are cases that cannot be automatically discovered. For
  // example, DirectMethodHandle::IMPL_NAMES points to MethodHandles::IMPL_NAMES,
  // but the MethodHandles class is not automatically marked because there are
  // no archived instances of the MethodHandles type.
  //
  // If we aot-initialize DirectMethodHandle, but allow MethodHandles to be
  // initialized at runtime, MethodHandles::IMPL_NAMES will get a different
  // value than DirectMethodHandle::IMPL_NAMES. This *may or may not* be a problem,
  // but to ensure compatibility, we should try to preserve the identity equality
  // of these two fields.
  //
  // To do that, we add MethodHandles to the indy_specs[] table below.
  //
  // Automatic validation
  // ====================
  //
  // CDSHeapVerifier is used to detect potential problems with identity equality.
  // To see how it detects the problem with MethodHandles::IMPL_NAMES:
  //
  // - Comment out all the lines in indy_specs[] except the {nullptr} line.
  // - Rebuild the JDK
  //
  // Then run the following:
  //    java -XX:AOTMode=record -XX:AOTConfiguration=jc.aotconfig com.sun.tools.javac.Main
  //    java -XX:AOTMode=create -Xlog:cds -XX:AOTCache=jc.aot -XX:AOTConfiguration=jc.aotconfig
  //
  // You will see an error like this:
  //
  // Archive heap points to a static field that may hold a different value at runtime:
  // Field: java/lang/invoke/MethodHandles::IMPL_NAMES
  // Value: java.lang.invoke.MemberName$Factory
  // {0x000000060e906ae8} - klass: 'java/lang/invoke/MemberName$Factory' - flags:
  //
  //  - ---- fields (total size 2 words):
  // --- trace begin ---
  // [ 0] {0x000000060e8deeb0} java.lang.Class (java.lang.invoke.DirectMethodHandle::IMPL_NAMES)
  // [ 1] {0x000000060e906ae8} java.lang.invoke.MemberName$Factory
  // --- trace end ---
  //
  // Trouble-shooting
  // ================
  //
  // If you see a "static field that may hold a different value" error, it's probably
  // because you've made some changes in the JDK core libraries (most likely
  // java.lang.invoke).
  //
  //  - Did you add a new static field to a class that could be referenced by
  //    cached object instances of MethodType, MethodHandle, etc? You may need
  //    to add that class to indy_specs[].
  //  - Did you modify the <clinit> of the classes in java.lang.invoke such that
  //    a static field now points to an object that should not be cached (e.g.,
  //    a native resource such as a file descriptior, or a Thread)?

  const bool IS_PREFIX = true;

  {
    static AllowedSpec specs[] = {
      {"java/lang/Object"},
      {"java/lang/Enum"},
     {nullptr}
    };
    if (is_allowed(specs, ik)) {
      return true;
    }
  }

  if (CDSConfig::is_dumping_invokedynamic()) {
    // This table was created with the help of CDSHeapVerifier.
    // Also, some $Holder classes are needed. E.g., Invokers.<clinit> explicitly
    // initializes Invokers$Holder. Since Invokers.<clinit> won't be executed
    // at runtime, we need to make sure Invokers$Holder is also aot-inited.
    static AllowedSpec indy_specs[] = {
      {"java/lang/constant/ConstantDescs"},
      {"java/lang/constant/DynamicConstantDesc"},
      {"java/lang/invoke/BoundMethodHandle"},
      {"java/lang/invoke/BoundMethodHandle$Specializer"},
      {"java/lang/invoke/BoundMethodHandle$Species_", IS_PREFIX},
      {"java/lang/invoke/ClassSpecializer"},
      {"java/lang/invoke/ClassSpecializer$", IS_PREFIX},
      {"java/lang/invoke/DelegatingMethodHandle"},
      {"java/lang/invoke/DelegatingMethodHandle$Holder"},     // UNSAFE.ensureClassInitialized()
      {"java/lang/invoke/DirectMethodHandle"},
      {"java/lang/invoke/DirectMethodHandle$Constructor"},
      {"java/lang/invoke/DirectMethodHandle$Holder"},         // UNSAFE.ensureClassInitialized()
      {"java/lang/invoke/Invokers"},
      {"java/lang/invoke/Invokers$Holder"},                   // UNSAFE.ensureClassInitialized()
      {"java/lang/invoke/LambdaForm"},
      {"java/lang/invoke/LambdaForm$Holder"},                 // UNSAFE.ensureClassInitialized()
      {"java/lang/invoke/LambdaForm$NamedFunction"},
      {"java/lang/invoke/MethodHandle"},
      {"java/lang/invoke/MethodHandles"},
      {"java/lang/invoke/SimpleMethodHandle"},
      {"java/util/Collections"},
      {"java/util/stream/Collectors"},
      {"jdk/internal/constant/PrimitiveClassDescImpl"},
      {"jdk/internal/constant/ReferenceClassDescImpl"},

    // Can't include this, as it will pull in MethodHandleStatics which has many environment
    // dependencies (on system properties, etc).
    //{"java/lang/invoke/InvokerBytecodeGenerator"},

      {nullptr}
    };
    if (is_allowed(indy_specs, ik)) {
      return true;
    }
  }

  return false;
}

// TODO: currently we have a hard-coded list. We should turn this into
// an annotation: @jdk.internal.vm.annotation.RuntimeSetupRequired
bool AOTClassInitializer::is_runtime_setup_required(InstanceKlass* ik) {
  return ik == vmClasses::Class_klass() ||
         ik == vmClasses::internal_Unsafe_klass() ||
         ik == vmClasses::ConcurrentHashMap_klass();
}

void AOTClassInitializer::call_runtime_setup(InstanceKlass* ik, TRAPS) {
  assert(ik->has_aot_initialized_mirror(), "sanity");
  if (ik->is_runtime_setup_required()) {
    if (log_is_enabled(Info, cds, init)) {
      ResourceMark rm;
      log_info(cds, init)("Calling %s::runtimeSetup()", ik->external_name());
    }
    JavaValue result(T_VOID);
    JavaCalls::call_static(&result, ik,
                           vmSymbols::runtimeSetup(),
                           vmSymbols::void_method_signature(), CHECK);
  }
}

#ifdef ASSERT
void AOTClassInitializer::assert_no_clinit_will_run_for_aot_init_class(InstanceKlass* ik) {
  assert(ik->has_aot_initialized_mirror(), "must be");

  InstanceKlass* s = ik->java_super();
  if (s != nullptr) {
    DEBUG_ONLY(ResourceMark rm);
    assert(s->is_initialized(), "super class %s of aot-inited class %s must have been initialized",
           s->external_name(), ik->external_name());
    AOTClassInitializer::assert_no_clinit_will_run_for_aot_init_class(s);
  }

  Array<InstanceKlass*>* interfaces = ik->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    InstanceKlass* intf = interfaces->at(i);
    if (!intf->is_initialized()) {
      // Note: an interface needs to be marked as is_initialized() only if
      // - it has a <clinit>
      // - it has at least one default method.
      assert(!intf->has_nonstatic_concrete_methods() || intf->class_initializer() == nullptr, "uninitialized super interface %s of aot-inited class %s must not have <clinit>",
             intf->external_name(), ik->external_name());
    }
  }
}
#endif
