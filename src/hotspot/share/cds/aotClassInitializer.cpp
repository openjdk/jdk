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

// Detector for class names we wish to handle specially.
// It is either an exact string match or a string prefix match.
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
    assert(_class_name != nullptr, "caller resp.");
    if (_is_prefix) {
      return len >= _len && name->starts_with(_class_name);
    } else {
      return len == _len && name->equals(_class_name);
    }
  }
};


// Tell if ik has a name that matches one of the given specs.
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

  if (ik->is_enum_subclass()) {
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
  // When a class is aot-inited, its static fields are already set up
  // by executing the <clinit> method at AOT assembly time.  Later on
  // in the production run, when the class would normally be
  // initialized, the VM performs guarding and synchronization as if
  // it were going to run the <clinit> again, but instead it simply
  // observes that that class was aot-inited.  The VM assumes that, if
  // it were to run <clinit> again, it would get a semantically
  // equivalent set of final field values, so it just adopts the
  // existing field values (from AOT assembly) and skips the call to
  // <clinit>.  There may at that point be fixups performed by ad hoc
  // code, if the VM recognizes a request in the library.
  //
  // It is true that this is not generally correct for all possible
  // Java code.  A <clinit> method might have a side effect beyond
  // initializing the static fields.  It might send an email somewhere
  // noting the current time of day.  In that case, such an email
  // would have been sent during the AOT assembly phase, and the email
  // would NOT be sent again during production.  This is clearly NOT
  // what a user would want, if this were a general purpose facility.
  // But in fact it is only for certain well-behaved classes, which
  // are known NOT to have such side effects.  We know this because
  // the optimization (of skipping <clinit> for aot-init classes) is
  // only applied to classes fully defined by the JDK.
  //
  // (A day may come when we figure out how to gracefully extend this
  // optimization to untrusted third parties, but it is not this day.)
  //
  // Manual selection
  // ================
  //
  // There are important cases where one aot-init class has a side
  // effect on another aot-class, a side effect which is not captured
  // in any static field value in either class.  The simplest example
  // is class A forces the initialization of class B.  In that case,
  // we need to aot-init either both classes or neither.  From looking
  // at the JDK state after AOT assembly is done, it is hard to tell
  // that A "touched" B and B might escape our notice.  Another common
  // example is A copying a field value from B.  We don't know where A
  // got the value, but it would be wrong to re-initialize B at
  // startup, while keeping the snapshot of the old B value in A.  In
  // general, if we aot-init A, we need to aot-init every class B that
  // somehow contributed to A's initial state, and every class C that
  // was somehow side-effected by A's initialization.  We say that the
  // aot-init of A is "init-coupled" to those of B and C.
  //
  // So there are init-coupled classes that cannot be automatically discovered. For
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
  // Luckily we do not need to be all-knowing in order to choose which
  // items to add to that table.  We have tools to help detect couplings.
  //
  // Automatic validation
  // ====================
  //
  // CDSHeapVerifier is used to detect potential problems with identity equality.
  //
  // A class B is assumed to be init-coupled to some aot-init class if
  // B has a field which points to a live object X in the AOT heap.
  // The live object X was created by some other class A which somehow
  // used B's reference to X, perhaps with the help of an intermediate
  // class Z.  Or, B pulled the reference to X from some other class
  // Y, and B obtained that reference from Y (or an intermediate Z).
  // It is not certain how X got into the heap, nor whether B
  // contributed it, but it is a good heuristic that B is init-coupled
  // to X's class or some other aot-init class.  In any case, B should
  // be made an aot-init class as well, unless a manual inspection
  // shows that would be a problem.  If there is a problem, then the
  // JDK code for B and/or X probably needs refactoring.  If there is
  // no problem, we add B to the list.  Typically the same scan will
  // find any other accomplices Y, Z, etc.  One failure would be a
  // class Q whose only initialization action is to scribble a special
  // value into B, from which the value X is derived and then makes
  // its way into the heap.  In that case, the heuristic does not
  // identify Q.  It is (currently) a human responsibility, of JDK
  // engineers, not to write such dirty JDK code, or to repair it if
  // it crops up.  Eventually we may have tools, or even a user mode
  // with design rules and checks, that will vet our code base more
  // automatically.
  //
  // To see how the tool detects the problem with MethodHandles::IMPL_NAMES:
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
  //
  // Note that these potential problems only occur when one class gets
  // the aot-init treatment, AND another class is init-coupled to it,
  // AND the coupling is not detected.  Currently there are a number
  // classes that get the aot-init treatment, in java.lang.invoke
  // because of invokedynamic.  They are few enough for now to be
  // manually tracked.  There may be more in the future.

  // IS_PREFIX means that we match all class names that start with a
  // prefix.  Otherwise, it is an exact match, of just one class name.
  const bool IS_PREFIX = true;

  {
    static AllowedSpec specs[] = {
      // everybody's favorite super
      {"java/lang/Object"},

      // above we selected all enums; we must include their super as well
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
    //
    // We hope we can reduce the size of this list over time, and move
    // the responsibility for identifying such classes into the JDK
    // code itself.  See tracking RFE JDK-8342481.
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
      {"jdk/internal/constant/ConstantUtils"},
      {"jdk/internal/constant/PrimitiveClassDescImpl"},
      {"jdk/internal/constant/ReferenceClassDescImpl"},

    // Can't include this, as it will pull in MethodHandleStatics which has many environment
    // dependencies (on system properties, etc).
    // MethodHandleStatics is an example of a class that must NOT get the aot-init treatment,
    // because of its strong reliance on (a) final fields which are (b) environmentally determined.
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
// See JDK-8342481.
bool AOTClassInitializer::is_runtime_setup_required(InstanceKlass* ik) {
  return ik == vmClasses::Class_klass() ||
         ik == vmClasses::internal_Unsafe_klass() ||
         ik == vmClasses::ConcurrentHashMap_klass();
}

void AOTClassInitializer::call_runtime_setup(JavaThread* current, InstanceKlass* ik) {
  assert(ik->has_aot_initialized_mirror(), "sanity");
  if (ik->is_runtime_setup_required()) {
    if (log_is_enabled(Info, cds, init)) {
      ResourceMark rm;
      log_info(cds, init)("Calling %s::runtimeSetup()", ik->external_name());
    }
    JavaValue result(T_VOID);
    JavaCalls::call_static(&result, ik,
                           vmSymbols::runtimeSetup(),
                           vmSymbols::void_method_signature(), current);
    if (current->has_pending_exception()) {
      // We cannot continue, as we might have cached instances of ik in the heap, but propagating the
      // exception would cause ik to be in an error state.
      AOTLinkedClassBulkLoader::exit_on_exception(current);
    }
  }
}

