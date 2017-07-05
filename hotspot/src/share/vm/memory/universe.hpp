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

// Universe is a name space holding known system classes and objects in the VM.
//
// Loaded classes are accessible through the SystemDictionary.
//
// The object heap is allocated and accessed through Universe, and various allocation
// support is provided. Allocation by the interpreter and compiled code is done inline
// and bails out to Scavenge::invoke_and_allocate.

class CollectedHeap;
class DeferredObjAllocEvent;


// Common parts of a methodOop cache. This cache safely interacts with
// the RedefineClasses API.
//
class CommonMethodOopCache : public CHeapObj {
  // We save the klassOop and the idnum of methodOop in order to get
  // the current cached methodOop.
 private:
  klassOop              _klass;
  int                   _method_idnum;

 public:
  CommonMethodOopCache()   { _klass = NULL; _method_idnum = -1; }
  ~CommonMethodOopCache()  { _klass = NULL; _method_idnum = -1; }

  void     init(klassOop k, methodOop m, TRAPS);
  klassOop klass() const         { return _klass; }
  int      method_idnum() const  { return _method_idnum; }

  // GC support
  void     oops_do(OopClosure* f)  { f->do_oop((oop*)&_klass); }
};


// A helper class for caching a methodOop when the user of the cache
// cares about all versions of the methodOop.
//
class ActiveMethodOopsCache : public CommonMethodOopCache {
  // This subclass adds weak references to older versions of the
  // methodOop and a query method for a methodOop.

 private:
  // If the cached methodOop has not been redefined, then
  // _prev_methods will be NULL. If all of the previous
  // versions of the method have been collected, then
  // _prev_methods can have a length of zero.
  GrowableArray<jweak>* _prev_methods;

 public:
  ActiveMethodOopsCache()   { _prev_methods = NULL; }
  ~ActiveMethodOopsCache();

  void add_previous_version(const methodOop method);
  bool is_same_method(const methodOop method) const;
};


// A helper class for caching a methodOop when the user of the cache
// only cares about the latest version of the methodOop.
//
class LatestMethodOopCache : public CommonMethodOopCache {
  // This subclass adds a getter method for the latest methodOop.

 public:
  methodOop get_methodOop();
};

// For UseCompressedOops.
struct NarrowOopStruct {
  // Base address for oop-within-java-object materialization.
  // NULL if using wide oops or zero based narrow oops.
  address _base;
  // Number of shift bits for encoding/decoding narrow oops.
  // 0 if using wide oops or zero based unscaled narrow oops,
  // LogMinObjAlignmentInBytes otherwise.
  int     _shift;
  // Generate code with implicit null checks for narrow oops.
  bool    _use_implicit_null_checks;
};


class Universe: AllStatic {
  // Ugh.  Universe is much too friendly.
  friend class MarkSweep;
  friend class oopDesc;
  friend class ClassLoader;
  friend class Arguments;
  friend class SystemDictionary;
  friend class VMStructs;
  friend class CompactingPermGenGen;
  friend class VM_PopulateDumpSharedSpace;

  friend jint  universe_init();
  friend void  universe2_init();
  friend bool  universe_post_init();

 private:
  // Known classes in the VM
  static klassOop _boolArrayKlassObj;
  static klassOop _byteArrayKlassObj;
  static klassOop _charArrayKlassObj;
  static klassOop _intArrayKlassObj;
  static klassOop _shortArrayKlassObj;
  static klassOop _longArrayKlassObj;
  static klassOop _singleArrayKlassObj;
  static klassOop _doubleArrayKlassObj;
  static klassOop _typeArrayKlassObjs[T_VOID+1];

  static klassOop _objectArrayKlassObj;

  static klassOop _symbolKlassObj;
  static klassOop _methodKlassObj;
  static klassOop _constMethodKlassObj;
  static klassOop _methodDataKlassObj;
  static klassOop _klassKlassObj;
  static klassOop _arrayKlassKlassObj;
  static klassOop _objArrayKlassKlassObj;
  static klassOop _typeArrayKlassKlassObj;
  static klassOop _instanceKlassKlassObj;
  static klassOop _constantPoolKlassObj;
  static klassOop _constantPoolCacheKlassObj;
  static klassOop _compiledICHolderKlassObj;
  static klassOop _systemObjArrayKlassObj;

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

  static typeArrayOop _the_empty_byte_array;          // Canonicalized byte array
  static typeArrayOop _the_empty_short_array;         // Canonicalized short array
  static typeArrayOop _the_empty_int_array;           // Canonicalized int array
  static objArrayOop  _the_empty_system_obj_array;    // Canonicalized system obj array
  static objArrayOop  _the_empty_class_klass_array;   // Canonicalized obj array of type java.lang.Class
  static objArrayOop  _the_array_interfaces_array;    // Canonicalized 2-array of cloneable & serializable klasses
  static oop          _the_null_string;               // A cache of "null" as a Java string
  static oop          _the_min_jint_string;          // A cache of "-2147483648" as a Java string
  static LatestMethodOopCache* _finalizer_register_cache; // static method for registering finalizable objects
  static LatestMethodOopCache* _loader_addClass_cache;    // method for registering loaded classes in class loader vector
  static ActiveMethodOopsCache* _reflect_invoke_cache;    // method for security checks
  static oop          _out_of_memory_error_java_heap; // preallocated error object (no backtrace)
  static oop          _out_of_memory_error_perm_gen;  // preallocated error object (no backtrace)
  static oop          _out_of_memory_error_array_size;// preallocated error object (no backtrace)
  static oop          _out_of_memory_error_gc_overhead_limit; // preallocated error object (no backtrace)

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

  static oop          _emptySymbol;                   // Canonical empty string ("") symbol

  // The particular choice of collected heap.
  static CollectedHeap* _collectedHeap;

  // For UseCompressedOops.
  static struct NarrowOopStruct _narrow_oop;

  // array of dummy objects used with +FullGCAlot
  debug_only(static objArrayOop _fullgc_alot_dummy_array;)
  // index of next entry to clear
  debug_only(static int         _fullgc_alot_dummy_next;)

  // Compiler/dispatch support
  static int  _base_vtable_size;                      // Java vtbl size of klass Object (in words)

  // Initialization
  static bool _bootstrapping;                         // true during genesis
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
  static void initialize_basic_type_mirrors(TRAPS);
  static void fixup_mirrors(TRAPS);

  static void reinitialize_vtable_of(KlassHandle h_k, TRAPS);
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

  static void compute_verify_oop_data();

 public:
  // Known classes in the VM
  static klassOop boolArrayKlassObj()                 { return _boolArrayKlassObj;   }
  static klassOop byteArrayKlassObj()                 { return _byteArrayKlassObj;   }
  static klassOop charArrayKlassObj()                 { return _charArrayKlassObj;   }
  static klassOop intArrayKlassObj()                  { return _intArrayKlassObj;    }
  static klassOop shortArrayKlassObj()                { return _shortArrayKlassObj;  }
  static klassOop longArrayKlassObj()                 { return _longArrayKlassObj;   }
  static klassOop singleArrayKlassObj()               { return _singleArrayKlassObj; }
  static klassOop doubleArrayKlassObj()               { return _doubleArrayKlassObj; }

  static klassOop objectArrayKlassObj() {
    return _objectArrayKlassObj;
  }

  static klassOop typeArrayKlassObj(BasicType t) {
    assert((uint)t < T_VOID+1, "range check");
    assert(_typeArrayKlassObjs[t] != NULL, "domain check");
    return _typeArrayKlassObjs[t];
  }

  static klassOop symbolKlassObj()                    { return _symbolKlassObj;            }
  static klassOop methodKlassObj()                    { return _methodKlassObj;            }
  static klassOop constMethodKlassObj()               { return _constMethodKlassObj;         }
  static klassOop methodDataKlassObj()                { return _methodDataKlassObj;        }
  static klassOop klassKlassObj()                     { return _klassKlassObj;             }
  static klassOop arrayKlassKlassObj()                { return _arrayKlassKlassObj;        }
  static klassOop objArrayKlassKlassObj()             { return _objArrayKlassKlassObj;     }
  static klassOop typeArrayKlassKlassObj()            { return _typeArrayKlassKlassObj;    }
  static klassOop instanceKlassKlassObj()             { return _instanceKlassKlassObj;     }
  static klassOop constantPoolKlassObj()              { return _constantPoolKlassObj;      }
  static klassOop constantPoolCacheKlassObj()         { return _constantPoolCacheKlassObj; }
  static klassOop compiledICHolderKlassObj()          { return _compiledICHolderKlassObj;  }
  static klassOop systemObjArrayKlassObj()            { return _systemObjArrayKlassObj;    }

  // Known objects in tbe VM
  static oop int_mirror()                   { return check_mirror(_int_mirror);
}
  static oop float_mirror()                 { return check_mirror(_float_mirror); }
  static oop double_mirror()                { return check_mirror(_double_mirror); }
  static oop byte_mirror()                  { return check_mirror(_byte_mirror); }
  static oop bool_mirror()                  { return check_mirror(_bool_mirror); }
  static oop char_mirror()                  { return check_mirror(_char_mirror); }
  static oop long_mirror()                  { return check_mirror(_long_mirror); }
  static oop short_mirror()                 { return check_mirror(_short_mirror); }
  static oop void_mirror()                  { return check_mirror(_void_mirror); }

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

  static typeArrayOop the_empty_byte_array()          { return _the_empty_byte_array;          }
  static typeArrayOop the_empty_short_array()         { return _the_empty_short_array;         }
  static typeArrayOop the_empty_int_array()           { return _the_empty_int_array;           }
  static objArrayOop  the_empty_system_obj_array ()   { return _the_empty_system_obj_array;    }
  static objArrayOop  the_empty_class_klass_array ()  { return _the_empty_class_klass_array;   }
  static objArrayOop  the_array_interfaces_array()    { return _the_array_interfaces_array;    }
  static oop          the_null_string()               { return _the_null_string;               }
  static oop          the_min_jint_string()          { return _the_min_jint_string;          }
  static methodOop    finalizer_register_method()     { return _finalizer_register_cache->get_methodOop(); }
  static methodOop    loader_addClass_method()        { return _loader_addClass_cache->get_methodOop(); }
  static ActiveMethodOopsCache* reflect_invoke_cache() { return _reflect_invoke_cache; }
  static oop          null_ptr_exception_instance()   { return _null_ptr_exception_instance;   }
  static oop          arithmetic_exception_instance() { return _arithmetic_exception_instance; }
  static oop          virtual_machine_error_instance() { return _virtual_machine_error_instance; }
  static oop          vm_exception()                  { return _vm_exception; }
  static oop          emptySymbol()                   { return _emptySymbol; }

  // OutOfMemoryError support. Returns an error with the required message. The returned error
  // may or may not have a backtrace. If error has a backtrace then the stack trace is already
  // filled in.
  static oop out_of_memory_error_java_heap()          { return gen_out_of_memory_error(_out_of_memory_error_java_heap);  }
  static oop out_of_memory_error_perm_gen()           { return gen_out_of_memory_error(_out_of_memory_error_perm_gen);   }
  static oop out_of_memory_error_array_size()         { return gen_out_of_memory_error(_out_of_memory_error_array_size); }
  static oop out_of_memory_error_gc_overhead_limit()  { return gen_out_of_memory_error(_out_of_memory_error_gc_overhead_limit);  }

  // Accessors needed for fast allocation
  static klassOop* boolArrayKlassObj_addr()           { return &_boolArrayKlassObj;   }
  static klassOop* byteArrayKlassObj_addr()           { return &_byteArrayKlassObj;   }
  static klassOop* charArrayKlassObj_addr()           { return &_charArrayKlassObj;   }
  static klassOop* intArrayKlassObj_addr()            { return &_intArrayKlassObj;    }
  static klassOop* shortArrayKlassObj_addr()          { return &_shortArrayKlassObj;  }
  static klassOop* longArrayKlassObj_addr()           { return &_longArrayKlassObj;   }
  static klassOop* singleArrayKlassObj_addr()         { return &_singleArrayKlassObj; }
  static klassOop* doubleArrayKlassObj_addr()         { return &_doubleArrayKlassObj; }

  // The particular choice of collected heap.
  static CollectedHeap* heap() { return _collectedHeap; }

  // For UseCompressedOops
  static address* narrow_oop_base_addr()              { return &_narrow_oop._base; }
  static address  narrow_oop_base()                   { return  _narrow_oop._base; }
  static bool  is_narrow_oop_base(void* addr)         { return (narrow_oop_base() == (address)addr); }
  static int      narrow_oop_shift()                  { return  _narrow_oop._shift; }
  static void     set_narrow_oop_base(address base)   { _narrow_oop._base  = base; }
  static void     set_narrow_oop_shift(int shift)     { _narrow_oop._shift = shift; }
  static bool     narrow_oop_use_implicit_null_checks()             { return  _narrow_oop._use_implicit_null_checks; }
  static void     set_narrow_oop_use_implicit_null_checks(bool use) { _narrow_oop._use_implicit_null_checks = use; }
  // Narrow Oop encoding mode:
  // 0 - Use 32-bits oops without encoding when
  //     NarrowOopHeapBaseMin + heap_size < 4Gb
  // 1 - Use zero based compressed oops with encoding when
  //     NarrowOopHeapBaseMin + heap_size < 32Gb
  // 2 - Use compressed oops with heap base + encoding.
  enum NARROW_OOP_MODE {
    UnscaledNarrowOop  = 0,
    ZeroBasedNarrowOop = 1,
    HeapBasedNarrowOop = 2
  };
  static char* preferred_heap_base(size_t heap_size, NARROW_OOP_MODE mode);

  // Historic gc information
  static size_t get_heap_capacity_at_last_gc()         { return _heap_capacity_at_last_gc; }
  static size_t get_heap_free_at_last_gc()             { return _heap_capacity_at_last_gc - _heap_used_at_last_gc; }
  static size_t get_heap_used_at_last_gc()             { return _heap_used_at_last_gc; }
  static void update_heap_info_at_gc();

  // Testers
  static bool is_bootstrapping()                      { return _bootstrapping; }
  static bool is_fully_initialized()                  { return _fully_initialized; }

  static inline bool element_type_should_be_aligned(BasicType type);
  static inline bool field_type_should_be_aligned(BasicType type);
  static bool        on_page_boundary(void* addr);
  static bool        should_fill_in_stack_trace(Handle throwable);
  static void check_alignment(uintx size, uintx alignment, const char* name);

  // Finalizer support.
  static void run_finalizers_on_exit();

  // Iteration

  // Apply "f" to the addresses of all the direct heap pointers maintained
  // as static fields of "Universe".
  static void oops_do(OopClosure* f, bool do_all = false);

  // Apply "f" to all klasses for basic types (classes not present in
  // SystemDictionary).
  static void basic_type_classes_do(void f(klassOop));

  // Apply "f" to all system klasses (classes not present in SystemDictionary).
  static void system_classes_do(void f(klassOop));

  // For sharing -- fill in a list of known vtable pointers.
  static void init_self_patching_vtbl_list(void** list, int count);

  // Debugging
  static bool verify_in_progress() { return _verify_in_progress; }
  static void verify(bool allow_dirty = true, bool silent = false, bool option = true);
  static int  verify_count()                  { return _verify_count; }
  static void print();
  static void print_on(outputStream* st);
  static void print_heap_at_SIGBREAK();
  static void print_heap_before_gc() { print_heap_before_gc(gclog_or_tty); }
  static void print_heap_after_gc()  { print_heap_after_gc(gclog_or_tty); }
  static void print_heap_before_gc(outputStream* st);
  static void print_heap_after_gc(outputStream* st);

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
  static uintptr_t verify_klass_mask()        PRODUCT_RETURN0;
  static uintptr_t verify_klass_bits()        PRODUCT_RETURN0;

  // Flushing and deoptimization
  static void flush_dependents_on(instanceKlassHandle dependee);
#ifdef HOTSWAP
  // Flushing and deoptimization in case of evolution
  static void flush_evol_dependents_on(instanceKlassHandle dependee);
#endif // HOTSWAP
  // Support for fullspeed debugging
  static void flush_dependents_on_method(methodHandle dependee);

  // Compiler support
  static int base_vtable_size()               { return _base_vtable_size; }
};

class DeferredObjAllocEvent : public CHeapObj {
  private:
    oop    _oop;
    size_t _bytesize;
    jint   _arena_id;

  public:
    DeferredObjAllocEvent(const oop o, const size_t s, const jint id) {
      _oop      = o;
      _bytesize = s;
      _arena_id = id;
    }

    ~DeferredObjAllocEvent() {
    }

    jint   arena_id() { return _arena_id; }
    size_t bytesize() { return _bytesize; }
    oop    get_oop()  { return _oop; }
};
