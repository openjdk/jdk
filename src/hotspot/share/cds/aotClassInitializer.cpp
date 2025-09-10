/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassInitializer.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmSymbols.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"

DEBUG_ONLY(InstanceKlass* _aot_init_class = nullptr;)

bool AOTClassInitializer::can_archive_initialized_mirror(InstanceKlass* ik) {
  assert(!ArchiveBuilder::is_active() || !ArchiveBuilder::current()->is_in_buffer_space(ik), "must be source klass");
  if (!CDSConfig::is_initing_classes_at_dump_time()) {
    return false;
  }

  if (RegeneratedClasses::is_regenerated_object(ik)) {
    ik = RegeneratedClasses::get_original_object(ik);
  }

  if (!ik->is_initialized() && !ik->is_being_initialized()) {
    return false;
  }

  // About "static field that may hold a different value" errors:
  //
  // Automatic selection for aot-inited classes
  // ==========================================
  //
  // When CDSConfig::is_initing_classes_at_dump_time is enabled,
  // AOTArtifactFinder::find_artifacts() finds the classes of all
  // heap objects that are reachable from HeapShared::_run_time_special_subgraph,
  // and mark these classes as aot-inited. This preserves the initialized
  // mirrors of these classes, and their <clinit> methods are NOT executed
  // at runtime. See aotArtifactFinder.hpp for more info.
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
  //    java -XX:AOTMode=create -Xlog:aot -XX:AOTCache=jc.aot -XX:AOTConfiguration=jc.aotconfig
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

  {
    if (ik == vmClasses::Object_klass()) {
      // everybody's favorite super
      return true;
    }
  }

  if (CDSConfig::is_dumping_method_handles()) {
    // The minimal list of @AOTSafeClassInitializer was created with the help of CDSHeapVerifier.
    // Also, some $Holder classes are needed. E.g., Invokers.<clinit> explicitly
    // initializes Invokers$Holder. Since Invokers.<clinit> won't be executed
    // at runtime, we need to make sure Invokers$Holder is also aot-inited.
    if (ik->has_aot_safe_initializer()) {
      return true;
    }
  }

#ifdef ASSERT
  if (ik == _aot_init_class) {
    return true;
  }
#endif

  return false;
}

void AOTClassInitializer::call_runtime_setup(JavaThread* current, InstanceKlass* ik) {
  assert(ik->has_aot_initialized_mirror(), "sanity");
  if (ik->is_runtime_setup_required()) {
    if (log_is_enabled(Info, aot, init)) {
      ResourceMark rm;
      log_info(aot, init)("Calling %s::runtimeSetup()", ik->external_name());
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

#ifdef ASSERT
void AOTClassInitializer::init_test_class(TRAPS) {
  // -XX:AOTInitTestClass is used in regression tests for adding additional AOT-initialized classes
  // and heap objects into the AOT cache. The tests must be carefully written to avoid including
  // any classes that cannot be AOT-initialized.
  //
  // -XX:AOTInitTestClass is NOT a general mechanism for including user-defined objects into
  // the AOT cache. Therefore, this option is NOT available in product JVM.
  if (AOTInitTestClass != nullptr && CDSConfig::is_initing_classes_at_dump_time()) {
    log_info(aot)("Debug build only: force initialization of AOTInitTestClass %s", AOTInitTestClass);
    TempNewSymbol class_name = SymbolTable::new_symbol(AOTInitTestClass);
    Handle app_loader(THREAD, SystemDictionary::java_system_loader());
    Klass* k = SystemDictionary::resolve_or_null(class_name, app_loader, CHECK);
    if (k == nullptr) {
      vm_exit_during_initialization("AOTInitTestClass not found", AOTInitTestClass);
    }
    if (!k->is_instance_klass()) {
      vm_exit_during_initialization("Invalid name for AOTInitTestClass", AOTInitTestClass);
    }

    _aot_init_class = InstanceKlass::cast(k);
    _aot_init_class->initialize(CHECK);
  }
}

bool AOTClassInitializer::has_test_class() {
  return _aot_init_class != nullptr;
}
#endif
