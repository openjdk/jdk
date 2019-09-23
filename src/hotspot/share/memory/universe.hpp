/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_UNIVERSE_HPP
#define SHARE_MEMORY_UNIVERSE_HPP

#include "gc/shared/verifyOption.hpp"
#include "oops/array.hpp"
#include "runtime/handles.hpp"
#include "utilities/growableArray.hpp"

// Universe is a name space holding known system classes and objects in the VM.
//
// Loaded classes are accessible through the SystemDictionary.
//
// The object heap is allocated and accessed through Universe, and various allocation
// support is provided. Allocation by the interpreter and compiled code is done inline
// and bails out to Scavenge::invoke_and_allocate.

class CollectedHeap;
class DeferredObjAllocEvent;


// A helper class for caching a Method* when the user of the cache
// only cares about the latest version of the Method*.  This cache safely
// interacts with the RedefineClasses API.

class LatestMethodCache : public CHeapObj<mtClass> {
  // We save the Klass* and the idnum of Method* in order to get
  // the current cached Method*.
 private:
  Klass*                _klass;
  int                   _method_idnum;

 public:
  LatestMethodCache()   { _klass = NULL; _method_idnum = -1; }
  ~LatestMethodCache()  { _klass = NULL; _method_idnum = -1; }

  void   init(Klass* k, Method* m);
  Klass* klass() const           { return _klass; }
  int    method_idnum() const    { return _method_idnum; }

  Method* get_method();

  // CDS support.  Replace the klass in this with the archive version
  // could use this for Enhanced Class Redefinition also.
  void serialize(SerializeClosure* f) {
    f->do_ptr((void**)&_klass);
  }
  void metaspace_pointers_do(MetaspaceClosure* it);
};

class Universe: AllStatic {
  // Ugh.  Universe is much too friendly.
  friend class MarkSweep;
  friend class oopDesc;
  friend class ClassLoader;
  friend class SystemDictionary;
  friend class ReservedHeapSpace;
  friend class VMStructs;
  friend class VM_PopulateDumpSharedSpace;
  friend class Metaspace;
  friend class MetaspaceShared;

  friend jint  universe_init();
  friend void  universe2_init();
  friend bool  universe_post_init();
  friend void  universe_post_module_init();

 private:
  // Known classes in the VM
  static Klass* _typeArrayKlassObjs[T_LONG+1];
  static Klass* _objectArrayKlassObj;

  // Known objects in the VM

  // Primitive objects
  static oop _int_mirror;
  static oop _float_mirror;
  static oop _double_mirror;
  static oop _byte_mirror;
  static oop _bool_mirror;
  static oop _char_mirror;
  static oop _long_mirror;
  static oop _short_mirror;
  static oop _void_mirror;

  static oop          _main_thread_group;             // Reference to the main thread group object
  static oop          _system_thread_group;           // Reference to the system thread group object

  static objArrayOop  _the_empty_class_klass_array;   // Canonicalized obj array of type java.lang.Class
  static oop          _the_null_sentinel;             // A unique object pointer unused except as a sentinel for null.
  static oop          _the_null_string;               // A cache of "null" as a Java string
  static oop          _the_min_jint_string;          // A cache of "-2147483648" as a Java string
  static LatestMethodCache* _finalizer_register_cache; // static method for registering finalizable objects
  static LatestMethodCache* _loader_addClass_cache;    // method for registering loaded classes in class loader vector
  static LatestMethodCache* _throw_illegal_access_error_cache; // Unsafe.throwIllegalAccessError() method
  static LatestMethodCache* _throw_no_such_method_error_cache; // Unsafe.throwNoSuchMethodError() method
  static LatestMethodCache* _do_stack_walk_cache;      // method for stack walker callback

  // preallocated error objects (no backtrace)
  static oop          _out_of_memory_error_java_heap;
  static oop          _out_of_memory_error_metaspace;
  static oop          _out_of_memory_error_class_metaspace;
  static oop          _out_of_memory_error_array_size;
  static oop          _out_of_memory_error_gc_overhead_limit;
  static oop          _out_of_memory_error_realloc_objects;
  static oop          _out_of_memory_error_retry;

  // preallocated cause message for delayed StackOverflowError
  static oop          _delayed_stack_overflow_error_message;

  static Array<int>*            _the_empty_int_array;            // Canonicalized int array
  static Array<u2>*             _the_empty_short_array;          // Canonicalized short array
  static Array<Klass*>*         _the_empty_klass_array;          // Canonicalized klass array
  static Array<InstanceKlass*>* _the_empty_instance_klass_array; // Canonicalized instance klass array
  static Array<Method*>*        _the_empty_method_array;         // Canonicalized method array

  static Array<Klass*>*  _the_array_interfaces_array;

  // array of preallocated error objects with backtrace
  static objArrayOop   _preallocated_out_of_memory_error_array;

  // number of preallocated error objects available for use
  static volatile jint _preallocated_out_of_memory_error_avail_count;

  static oop          _null_ptr_exception_instance;   // preallocated exception object
  static oop          _arithmetic_exception_instance; // preallocated exception object
  static oop          _virtual_machine_error_instance; // preallocated exception object
  // The object used as an exception dummy when exceptions are thrown for
  // the vm thread.
  static oop          _vm_exception;

  // References waiting to be transferred to the ReferenceHandler
  static oop          _reference_pending_list;

  // The particular choice of collected heap.
  static CollectedHeap* _collectedHeap;

  static intptr_t _non_oop_bits;

  // array of dummy objects used with +FullGCAlot
  debug_only(static objArrayOop _fullgc_alot_dummy_array;)
  // index of next entry to clear
  debug_only(static int         _fullgc_alot_dummy_next;)

  // Compiler/dispatch support
  static int  _base_vtable_size;                      // Java vtbl size of klass Object (in words)

  // Initialization
  static bool _bootstrapping;                         // true during genesis
  static bool _module_initialized;                    // true after call_initPhase2 called
  static bool _fully_initialized;                     // true after universe_init and initialize_vtables called

  // the array of preallocated errors with backtraces
  static objArrayOop  preallocated_out_of_memory_errors()     { return _preallocated_out_of_memory_error_array; }

  // generate an out of memory error; if possible using an error with preallocated backtrace;
  // otherwise return the given default error.
  static oop        gen_out_of_memory_error(oop default_err);

  // Historic gc information
  static size_t _heap_capacity_at_last_gc;
  static size_t _heap_used_at_last_gc;

  static jint initialize_heap();
  static void initialize_tlab();
  static void initialize_basic_type_mirrors(TRAPS);
  static void fixup_mirrors(TRAPS);

  static void reinitialize_vtable_of(Klass* k, TRAPS);
  static void reinitialize_vtables(TRAPS);
  static void reinitialize_itables(TRAPS);
  static void compute_base_vtable_size();             // compute vtable size of class Object

  static void genesis(TRAPS);                         // Create the initial world

  // Mirrors for primitive classes (created eagerly)
  static oop check_mirror(oop m) {
    assert(m != NULL, "mirror not initialized");
    return m;
  }

  // Debugging
  static int _verify_count;                           // number of verifies done

  // True during call to verify().  Should only be set/cleared in verify().
  static bool _verify_in_progress;
  static long verify_flags;

  static uintptr_t _verify_oop_mask;
  static uintptr_t _verify_oop_bits;

 public:
  static void calculate_verify_data(HeapWord* low_boundary, HeapWord* high_boundary) PRODUCT_RETURN;

  // Known classes in the VM
  static Klass* boolArrayKlassObj()                 { return typeArrayKlassObj(T_BOOLEAN); }
  static Klass* byteArrayKlassObj()                 { return typeArrayKlassObj(T_BYTE); }
  static Klass* charArrayKlassObj()                 { return typeArrayKlassObj(T_CHAR); }
  static Klass* intArrayKlassObj()                  { return typeArrayKlassObj(T_INT); }
  static Klass* shortArrayKlassObj()                { return typeArrayKlassObj(T_SHORT); }
  static Klass* longArrayKlassObj()                 { return typeArrayKlassObj(T_LONG); }
  static Klass* floatArrayKlassObj()                { return typeArrayKlassObj(T_FLOAT); }
  static Klass* doubleArrayKlassObj()               { return typeArrayKlassObj(T_DOUBLE); }

  static Klass* objectArrayKlassObj()               { return _objectArrayKlassObj; }

  static Klass* typeArrayKlassObj(BasicType t) {
    assert((uint)t >= T_BOOLEAN, "range check for type: %s", type2name(t));
    assert((uint)t < T_LONG+1,   "range check for type: %s", type2name(t));
    assert(_typeArrayKlassObjs[t] != NULL, "domain check");
    return _typeArrayKlassObjs[t];
  }

  // Known objects in the VM
  static oop int_mirror()                   { return check_mirror(_int_mirror); }
  static oop float_mirror()                 { return check_mirror(_float_mirror); }
  static oop double_mirror()                { return check_mirror(_double_mirror); }
  static oop byte_mirror()                  { return check_mirror(_byte_mirror); }
  static oop bool_mirror()                  { return check_mirror(_bool_mirror); }
  static oop char_mirror()                  { return check_mirror(_char_mirror); }
  static oop long_mirror()                  { return check_mirror(_long_mirror); }
  static oop short_mirror()                 { return check_mirror(_short_mirror); }
  static oop void_mirror()                  { return check_mirror(_void_mirror); }

  static void set_int_mirror(oop m)         { _int_mirror = m;    }
  static void set_float_mirror(oop m)       { _float_mirror = m;  }
  static void set_double_mirror(oop m)      { _double_mirror = m; }
  static void set_byte_mirror(oop m)        { _byte_mirror = m;   }
  static void set_bool_mirror(oop m)        { _bool_mirror = m;   }
  static void set_char_mirror(oop m)        { _char_mirror = m;   }
  static void set_long_mirror(oop m)        { _long_mirror = m;   }
  static void set_short_mirror(oop m)       { _short_mirror = m;  }
  static void set_void_mirror(oop m)        { _void_mirror = m;   }

  // table of same
  static oop _mirrors[T_VOID+1];

  static oop java_mirror(BasicType t) {
    assert((uint)t < T_VOID+1, "range check");
    return check_mirror(_mirrors[t]);
  }
  static oop      main_thread_group()                 { return _main_thread_group; }
  static void set_main_thread_group(oop group)        { _main_thread_group = group;}

  static oop      system_thread_group()               { return _system_thread_group; }
  static void set_system_thread_group(oop group)      { _system_thread_group = group;}

  static objArrayOop  the_empty_class_klass_array ()  { return _the_empty_class_klass_array;   }
  static Array<Klass*>* the_array_interfaces_array() { return _the_array_interfaces_array;   }
  static oop          the_null_string()               { return _the_null_string;               }
  static oop          the_min_jint_string()          { return _the_min_jint_string;          }

  static Method*      finalizer_register_method()     { return _finalizer_register_cache->get_method(); }
  static Method*      loader_addClass_method()        { return _loader_addClass_cache->get_method(); }

  static Method*      throw_illegal_access_error()    { return _throw_illegal_access_error_cache->get_method(); }
  static Method*      throw_no_such_method_error()    { return _throw_no_such_method_error_cache->get_method(); }

  static Method*      do_stack_walk_method()          { return _do_stack_walk_cache->get_method(); }

  static oop          the_null_sentinel()             { return _the_null_sentinel;             }
  static address      the_null_sentinel_addr()        { return (address) &_the_null_sentinel;  }

  // Function to initialize these
  static void initialize_known_methods(TRAPS);

  static oop          null_ptr_exception_instance()   { return _null_ptr_exception_instance;   }
  static oop          arithmetic_exception_instance() { return _arithmetic_exception_instance; }
  static oop          virtual_machine_error_instance() { return _virtual_machine_error_instance; }
  static oop          vm_exception()                  { return _vm_exception; }

  // Reference pending list manipulation.  Access is protected by
  // Heap_lock.  The getter, setter and predicate require the caller
  // owns the lock.  Swap is used by parallel non-concurrent reference
  // processing threads, where some higher level controller owns
  // Heap_lock, so requires the lock is locked, but not necessarily by
  // the current thread.
  static oop          reference_pending_list();
  static void         set_reference_pending_list(oop list);
  static bool         has_reference_pending_list();
  static oop          swap_reference_pending_list(oop list);

  static Array<int>*             the_empty_int_array()    { return _the_empty_int_array; }
  static Array<u2>*              the_empty_short_array()  { return _the_empty_short_array; }
  static Array<Method*>*         the_empty_method_array() { return _the_empty_method_array; }
  static Array<Klass*>*          the_empty_klass_array()  { return _the_empty_klass_array; }
  static Array<InstanceKlass*>*  the_empty_instance_klass_array() { return _the_empty_instance_klass_array; }

  // OutOfMemoryError support. Returns an error with the required message. The returned error
  // may or may not have a backtrace. If error has a backtrace then the stack trace is already
  // filled in.
  static oop out_of_memory_error_java_heap()          { return gen_out_of_memory_error(_out_of_memory_error_java_heap);  }
  static oop out_of_memory_error_metaspace()          { return gen_out_of_memory_error(_out_of_memory_error_metaspace);   }
  static oop out_of_memory_error_class_metaspace()    { return gen_out_of_memory_error(_out_of_memory_error_class_metaspace);   }
  static oop out_of_memory_error_array_size()         { return gen_out_of_memory_error(_out_of_memory_error_array_size); }
  static oop out_of_memory_error_gc_overhead_limit()  { return gen_out_of_memory_error(_out_of_memory_error_gc_overhead_limit);  }
  static oop out_of_memory_error_realloc_objects()    { return gen_out_of_memory_error(_out_of_memory_error_realloc_objects);  }
  // Throw default _out_of_memory_error_retry object as it will never propagate out of the VM
  static oop out_of_memory_error_retry()              { return _out_of_memory_error_retry;  }
  static oop delayed_stack_overflow_error_message()   { return _delayed_stack_overflow_error_message; }

  // The particular choice of collected heap.
  static CollectedHeap* heap() { return _collectedHeap; }

  // Reserve Java heap and determine CompressedOops mode
  static ReservedHeapSpace reserve_heap(size_t heap_size, size_t alignment);

  // Historic gc information
  static size_t get_heap_free_at_last_gc()             { return _heap_capacity_at_last_gc - _heap_used_at_last_gc; }
  static size_t get_heap_used_at_last_gc()             { return _heap_used_at_last_gc; }
  static void update_heap_info_at_gc();

  // Testers
  static bool is_bootstrapping()                      { return _bootstrapping; }
  static bool is_module_initialized()                 { return _module_initialized; }
  static bool is_fully_initialized()                  { return _fully_initialized; }

  static bool        on_page_boundary(void* addr);
  static bool        should_fill_in_stack_trace(Handle throwable);
  static void check_alignment(uintx size, uintx alignment, const char* name);

  // Iteration

  // Apply "f" to the addresses of all the direct heap pointers maintained
  // as static fields of "Universe".
  static void oops_do(OopClosure* f);

  // CDS support
  static void serialize(SerializeClosure* f);

  // Apply "f" to all klasses for basic types (classes not present in
  // SystemDictionary).
  static void basic_type_classes_do(void f(Klass*));
  static void basic_type_classes_do(KlassClosure* closure);
  static void metaspace_pointers_do(MetaspaceClosure* it);

  // Debugging
  enum VERIFY_FLAGS {
    Verify_Threads = 1,
    Verify_Heap = 2,
    Verify_SymbolTable = 4,
    Verify_StringTable = 8,
    Verify_CodeCache = 16,
    Verify_SystemDictionary = 32,
    Verify_ClassLoaderDataGraph = 64,
    Verify_MetaspaceUtils = 128,
    Verify_JNIHandles = 256,
    Verify_CodeCacheOops = 512,
    Verify_ResolvedMethodTable = 1024,
    Verify_All = -1
  };
  static void initialize_verify_flags();
  static bool should_verify_subset(uint subset);
  static bool verify_in_progress() { return _verify_in_progress; }
  static void verify(VerifyOption option, const char* prefix);
  static void verify(const char* prefix) {
    verify(VerifyOption_Default, prefix);
  }
  static void verify() {
    verify("");
  }

  static int  verify_count()       { return _verify_count; }
  static void print_on(outputStream* st);
  static void print_heap_at_SIGBREAK();
  static void print_heap_before_gc();
  static void print_heap_after_gc();

  // Change the number of dummy objects kept reachable by the full gc dummy
  // array; this should trigger relocation in a sliding compaction collector.
  debug_only(static bool release_fullgc_alot_dummy();)
  // The non-oop pattern (see compiledIC.hpp, etc)
  static void*   non_oop_word();

  // Oop verification (see MacroAssembler::verify_oop)
  static uintptr_t verify_oop_mask()          PRODUCT_RETURN0;
  static uintptr_t verify_oop_bits()          PRODUCT_RETURN0;
  static uintptr_t verify_mark_bits()         PRODUCT_RETURN0;
  static uintptr_t verify_mark_mask()         PRODUCT_RETURN0;

  // Compiler support
  static int base_vtable_size()               { return _base_vtable_size; }
};

#endif // SHARE_MEMORY_UNIVERSE_HPP
