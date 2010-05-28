/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

// AccessFlags is an abstraction over Java access flags.


enum {
  // See jvm.h for shared JVM_ACC_XXX access flags

  // HotSpot-specific access flags

  // flags actually put in .class file
  JVM_ACC_WRITTEN_FLAGS           = 0x00007FFF,

  // methodOop flags
  JVM_ACC_MONITOR_MATCH           = 0x10000000,     // True if we know that monitorenter/monitorexit bytecodes match
  JVM_ACC_HAS_MONITOR_BYTECODES   = 0x20000000,     // Method contains monitorenter/monitorexit bytecodes
  JVM_ACC_HAS_LOOPS               = 0x40000000,     // Method has loops
  JVM_ACC_LOOPS_FLAG_INIT         = (int)0x80000000,// The loop flag has been initialized
  JVM_ACC_QUEUED                  = 0x01000000,     // Queued for compilation
  JVM_ACC_NOT_TIER1_COMPILABLE    = 0x04000000,
  JVM_ACC_NOT_OSR_COMPILABLE      = 0x08000000,
  JVM_ACC_HAS_LINE_NUMBER_TABLE   = 0x00100000,
  JVM_ACC_HAS_CHECKED_EXCEPTIONS  = 0x00400000,
  JVM_ACC_HAS_JSRS                = 0x00800000,
  JVM_ACC_IS_OLD                  = 0x00010000,     // RedefineClasses() has replaced this method
  JVM_ACC_IS_OBSOLETE             = 0x00020000,     // RedefineClasses() has made method obsolete
  JVM_ACC_IS_PREFIXED_NATIVE      = 0x00040000,     // JVMTI has prefixed this native method
  JVM_MH_INVOKE_BITS           // = 0x10001100      // MethodHandle.invoke quasi-native
                                  = (JVM_ACC_NATIVE | JVM_ACC_SYNTHETIC | JVM_ACC_MONITOR_MATCH),

  // klassOop flags
  JVM_ACC_HAS_MIRANDA_METHODS     = 0x10000000,     // True if this class has miranda methods in it's vtable
  JVM_ACC_HAS_VANILLA_CONSTRUCTOR = 0x20000000,     // True if klass has a vanilla default constructor
  JVM_ACC_HAS_FINALIZER           = 0x40000000,     // True if klass has a non-empty finalize() method
  JVM_ACC_IS_CLONEABLE            = (int)0x80000000,// True if klass supports the Clonable interface
  JVM_ACC_HAS_FINAL_METHOD        = 0x01000000,     // True if klass has final method

  // klassOop and methodOop flags
  JVM_ACC_HAS_LOCAL_VARIABLE_TABLE= 0x00200000,

  JVM_ACC_PROMOTED_FLAGS          = 0x00200000,     // flags promoted from methods to the holding klass

  // field flags
  // Note: these flags must be defined in the low order 16 bits because
  // instanceKlass only stores a ushort worth of information from the
  // AccessFlags value.
  // These bits must not conflict with any other field-related access flags
  // (e.g., ACC_ENUM).
  // Note that the class-related ACC_ANNOTATION bit conflicts with these flags.
  JVM_ACC_FIELD_ACCESS_WATCHED       = 0x00002000,  // field access is watched by JVMTI
  JVM_ACC_FIELD_MODIFICATION_WATCHED = 0x00008000,  // field modification is watched by JVMTI

                                                    // flags accepted by set_field_flags()
  JVM_ACC_FIELD_FLAGS                = 0x00008000 | JVM_ACC_WRITTEN_FLAGS

};


class AccessFlags VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  jint _flags;

 public:
  // Java access flags
  bool is_public      () const         { return (_flags & JVM_ACC_PUBLIC      ) != 0; }
  bool is_private     () const         { return (_flags & JVM_ACC_PRIVATE     ) != 0; }
  bool is_protected   () const         { return (_flags & JVM_ACC_PROTECTED   ) != 0; }
  bool is_static      () const         { return (_flags & JVM_ACC_STATIC      ) != 0; }
  bool is_final       () const         { return (_flags & JVM_ACC_FINAL       ) != 0; }
  bool is_synchronized() const         { return (_flags & JVM_ACC_SYNCHRONIZED) != 0; }
  bool is_super       () const         { return (_flags & JVM_ACC_SUPER       ) != 0; }
  bool is_volatile    () const         { return (_flags & JVM_ACC_VOLATILE    ) != 0; }
  bool is_transient   () const         { return (_flags & JVM_ACC_TRANSIENT   ) != 0; }
  bool is_native      () const         { return (_flags & JVM_ACC_NATIVE      ) != 0; }
  bool is_interface   () const         { return (_flags & JVM_ACC_INTERFACE   ) != 0; }
  bool is_abstract    () const         { return (_flags & JVM_ACC_ABSTRACT    ) != 0; }
  bool is_strict      () const         { return (_flags & JVM_ACC_STRICT      ) != 0; }

  // Attribute flags
  bool is_synthetic   () const         { return (_flags & JVM_ACC_SYNTHETIC   ) != 0; }

  // methodOop flags
  bool is_monitor_matching     () const { return (_flags & JVM_ACC_MONITOR_MATCH          ) != 0; }
  bool has_monitor_bytecodes   () const { return (_flags & JVM_ACC_HAS_MONITOR_BYTECODES  ) != 0; }
  bool has_loops               () const { return (_flags & JVM_ACC_HAS_LOOPS              ) != 0; }
  bool loops_flag_init         () const { return (_flags & JVM_ACC_LOOPS_FLAG_INIT        ) != 0; }
  bool queued_for_compilation  () const { return (_flags & JVM_ACC_QUEUED                 ) != 0; }
  bool is_not_tier1_compilable  () const { return (_flags & JVM_ACC_NOT_TIER1_COMPILABLE  ) != 0; }
  bool is_not_osr_compilable   () const { return (_flags & JVM_ACC_NOT_OSR_COMPILABLE     ) != 0; }
  bool has_linenumber_table    () const { return (_flags & JVM_ACC_HAS_LINE_NUMBER_TABLE  ) != 0; }
  bool has_checked_exceptions  () const { return (_flags & JVM_ACC_HAS_CHECKED_EXCEPTIONS ) != 0; }
  bool has_jsrs                () const { return (_flags & JVM_ACC_HAS_JSRS               ) != 0; }
  bool is_old                  () const { return (_flags & JVM_ACC_IS_OLD                 ) != 0; }
  bool is_obsolete             () const { return (_flags & JVM_ACC_IS_OBSOLETE            ) != 0; }
  bool is_prefixed_native      () const { return (_flags & JVM_ACC_IS_PREFIXED_NATIVE     ) != 0; }

  // JSR 292:  A method of the form MethodHandle.invoke(A...)R method is
  // neither bytecoded nor a JNI native, but rather a fast call through
  // a lightweight method handle object.  Because it is not bytecoded,
  // it has the native bit set, but the monitor-match bit is also set
  // to distinguish it from a JNI native (which never has the match bit set).
  // The synthetic bit is also present, because such a method is never
  // explicitly defined in Java code.
  bool is_method_handle_invoke () const { return (_flags & JVM_MH_INVOKE_BITS) == JVM_MH_INVOKE_BITS; }

  // klassOop flags
  bool has_miranda_methods     () const { return (_flags & JVM_ACC_HAS_MIRANDA_METHODS    ) != 0; }
  bool has_vanilla_constructor () const { return (_flags & JVM_ACC_HAS_VANILLA_CONSTRUCTOR) != 0; }
  bool has_finalizer           () const { return (_flags & JVM_ACC_HAS_FINALIZER          ) != 0; }
  bool has_final_method        () const { return (_flags & JVM_ACC_HAS_FINAL_METHOD       ) != 0; }
  bool is_cloneable            () const { return (_flags & JVM_ACC_IS_CLONEABLE           ) != 0; }
  // klassOop and methodOop flags
  bool has_localvariable_table () const { return (_flags & JVM_ACC_HAS_LOCAL_VARIABLE_TABLE) != 0; }
  void set_has_localvariable_table()    { atomic_set_bits(JVM_ACC_HAS_LOCAL_VARIABLE_TABLE); }
  void clear_has_localvariable_table()  { atomic_clear_bits(JVM_ACC_HAS_LOCAL_VARIABLE_TABLE); }

  // field flags
  bool is_field_access_watched() const  { return (_flags & JVM_ACC_FIELD_ACCESS_WATCHED) != 0; }
  bool is_field_modification_watched() const
                                        { return (_flags & JVM_ACC_FIELD_MODIFICATION_WATCHED) != 0; }

  // get .class file flags
  jint get_flags               () const { return (_flags & JVM_ACC_WRITTEN_FLAGS); }

  // Initialization
  void add_promoted_flags(jint flags)   { _flags |= (flags & JVM_ACC_PROMOTED_FLAGS); }
  void set_field_flags(jint flags)      { _flags = (flags & JVM_ACC_FIELD_FLAGS); }
  void set_flags(jint flags)            { _flags = (flags & JVM_ACC_WRITTEN_FLAGS); }

  void set_queued_for_compilation()    { atomic_set_bits(JVM_ACC_QUEUED); }
  void clear_queued_for_compilation()  { atomic_clear_bits(JVM_ACC_QUEUED); }

  // Atomic update of flags
  void atomic_set_bits(jint bits);
  void atomic_clear_bits(jint bits);

 private:
  friend class methodOopDesc;
  friend class Klass;
  friend class ClassFileParser;
  // the functions below should only be called on the _access_flags inst var directly,
  // otherwise they are just changing a copy of the flags

  // attribute flags
  void set_is_synthetic()              { atomic_set_bits(JVM_ACC_SYNTHETIC);               }

  // methodOop flags
  void set_monitor_matching()          { atomic_set_bits(JVM_ACC_MONITOR_MATCH);           }
  void set_has_monitor_bytecodes()     { atomic_set_bits(JVM_ACC_HAS_MONITOR_BYTECODES);   }
  void set_has_loops()                 { atomic_set_bits(JVM_ACC_HAS_LOOPS);               }
  void set_loops_flag_init()           { atomic_set_bits(JVM_ACC_LOOPS_FLAG_INIT);         }
  void set_not_tier1_compilable()      { atomic_set_bits(JVM_ACC_NOT_TIER1_COMPILABLE);    }
  void set_not_osr_compilable()        { atomic_set_bits(JVM_ACC_NOT_OSR_COMPILABLE);      }
  void set_has_linenumber_table()      { atomic_set_bits(JVM_ACC_HAS_LINE_NUMBER_TABLE);   }
  void set_has_checked_exceptions()    { atomic_set_bits(JVM_ACC_HAS_CHECKED_EXCEPTIONS);  }
  void set_has_jsrs()                  { atomic_set_bits(JVM_ACC_HAS_JSRS);                }
  void set_is_old()                    { atomic_set_bits(JVM_ACC_IS_OLD);                  }
  void set_is_obsolete()               { atomic_set_bits(JVM_ACC_IS_OBSOLETE);             }
  void set_is_prefixed_native()        { atomic_set_bits(JVM_ACC_IS_PREFIXED_NATIVE);      }

  // klassOop flags
  void set_has_vanilla_constructor()   { atomic_set_bits(JVM_ACC_HAS_VANILLA_CONSTRUCTOR); }
  void set_has_finalizer()             { atomic_set_bits(JVM_ACC_HAS_FINALIZER);           }
  void set_has_final_method()          { atomic_set_bits(JVM_ACC_HAS_FINAL_METHOD);        }
  void set_is_cloneable()              { atomic_set_bits(JVM_ACC_IS_CLONEABLE);            }
  void set_has_miranda_methods()       { atomic_set_bits(JVM_ACC_HAS_MIRANDA_METHODS);     }

 public:
  // field flags
  void set_is_field_access_watched(const bool value)
                                       {
                                         if (value) {
                                           atomic_set_bits(JVM_ACC_FIELD_ACCESS_WATCHED);
                                         } else {
                                           atomic_clear_bits(JVM_ACC_FIELD_ACCESS_WATCHED);
                                         }
                                       }
  void set_is_field_modification_watched(const bool value)
                                       {
                                         if (value) {
                                           atomic_set_bits(JVM_ACC_FIELD_MODIFICATION_WATCHED);
                                         } else {
                                           atomic_clear_bits(JVM_ACC_FIELD_MODIFICATION_WATCHED);
                                         }
                                       }

  // Conversion
  jshort as_short()                    { return (jshort)_flags; }
  jint   as_int()                      { return _flags; }

  inline friend AccessFlags accessFlags_from(jint flags);

  // Printing/debugging
  void print_on(outputStream* st) const PRODUCT_RETURN;
};

inline AccessFlags accessFlags_from(jint flags) {
  AccessFlags af;
  af._flags = flags;
  return af;
}
