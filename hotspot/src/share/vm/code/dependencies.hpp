/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

//** Dependencies represent assertions (approximate invariants) within
// the class hierarchy.  An example is an assertion that a given
// method is not overridden; another example is that a type has only
// one concrete subtype.  Compiled code which relies on such
// assertions must be discarded if they are overturned by changes in
// the class hierarchy.  We can think of these assertions as
// approximate invariants, because we expect them to be overturned
// very infrequently.  We are willing to perform expensive recovery
// operations when they are overturned.  The benefit, of course, is
// performing optimistic optimizations (!) on the object code.
//
// Changes in the class hierarchy due to dynamic linking or
// class evolution can violate dependencies.  There is enough
// indexing between classes and nmethods to make dependency
// checking reasonably efficient.

class ciEnv;
class nmethod;
class OopRecorder;
class xmlStream;
class CompileLog;
class DepChange;
class No_Safepoint_Verifier;

class Dependencies: public ResourceObj {
 public:
  // Note: In the comments on dependency types, most uses of the terms
  // subtype and supertype are used in a "non-strict" or "inclusive"
  // sense, and are starred to remind the reader of this fact.
  // Strict uses of the terms use the word "proper".
  //
  // Specifically, every class is its own subtype* and supertype*.
  // (This trick is easier than continually saying things like "Y is a
  // subtype of X or X itself".)
  //
  // Sometimes we write X > Y to mean X is a proper supertype of Y.
  // The notation X > {Y, Z} means X has proper subtypes Y, Z.
  // The notation X.m > Y means that Y inherits m from X, while
  // X.m > Y.m means Y overrides X.m.  A star denotes abstractness,
  // as *I > A, meaning (abstract) interface I is a super type of A,
  // or A.*m > B.m, meaning B.m implements abstract method A.m.
  //
  // In this module, the terms "subtype" and "supertype" refer to
  // Java-level reference type conversions, as detected by
  // "instanceof" and performed by "checkcast" operations.  The method
  // Klass::is_subtype_of tests these relations.  Note that "subtype"
  // is richer than "subclass" (as tested by Klass::is_subclass_of),
  // since it takes account of relations involving interface and array
  // types.
  //
  // To avoid needless complexity, dependencies involving array types
  // are not accepted.  If you need to make an assertion about an
  // array type, make the assertion about its corresponding element
  // types.  Any assertion that might change about an array type can
  // be converted to an assertion about its element type.
  //
  // Most dependencies are evaluated over a "context type" CX, which
  // stands for the set Subtypes(CX) of every Java type that is a subtype*
  // of CX.  When the system loads a new class or interface N, it is
  // responsible for re-evaluating changed dependencies whose context
  // type now includes N, that is, all super types of N.
  //
  enum DepType {
    end_marker = 0,

    // An 'evol' dependency simply notes that the contents of the
    // method were used.  If it evolves (is replaced), the nmethod
    // must be recompiled.  No other dependencies are implied.
    evol_method,
    FIRST_TYPE = evol_method,

    // A context type CX is a leaf it if has no proper subtype.
    leaf_type,

    // An abstract class CX has exactly one concrete subtype CC.
    abstract_with_unique_concrete_subtype,

    // The type CX is purely abstract, with no concrete subtype* at all.
    abstract_with_no_concrete_subtype,

    // The concrete CX is free of concrete proper subtypes.
    concrete_with_no_concrete_subtype,

    // Given a method M1 and a context class CX, the set MM(CX, M1) of
    // "concrete matching methods" in CX of M1 is the set of every
    // concrete M2 for which it is possible to create an invokevirtual
    // or invokeinterface call site that can reach either M1 or M2.
    // That is, M1 and M2 share a name, signature, and vtable index.
    // We wish to notice when the set MM(CX, M1) is just {M1}, or
    // perhaps a set of two {M1,M2}, and issue dependencies on this.

    // The set MM(CX, M1) can be computed by starting with any matching
    // concrete M2 that is inherited into CX, and then walking the
    // subtypes* of CX looking for concrete definitions.

    // The parameters to this dependency are the method M1 and the
    // context class CX.  M1 must be either inherited in CX or defined
    // in a subtype* of CX.  It asserts that MM(CX, M1) is no greater
    // than {M1}.
    unique_concrete_method,       // one unique concrete method under CX

    // An "exclusive" assertion concerns two methods or subtypes, and
    // declares that there are at most two (or perhaps later N>2)
    // specific items that jointly satisfy the restriction.
    // We list all items explicitly rather than just giving their
    // count, for robustness in the face of complex schema changes.

    // A context class CX (which may be either abstract or concrete)
    // has two exclusive concrete subtypes* C1, C2 if every concrete
    // subtype* of CX is either C1 or C2.  Note that if neither C1 or C2
    // are equal to CX, then CX itself must be abstract.  But it is
    // also possible (for example) that C1 is CX (a concrete class)
    // and C2 is a proper subtype of C1.
    abstract_with_exclusive_concrete_subtypes_2,

    // This dependency asserts that MM(CX, M1) is no greater than {M1,M2}.
    exclusive_concrete_methods_2,

    // This dependency asserts that no instances of class or it's
    // subclasses require finalization registration.
    no_finalizable_subclasses,

    TYPE_LIMIT
  };
  enum {
    LG2_TYPE_LIMIT = 4,  // assert(TYPE_LIMIT <= (1<<LG2_TYPE_LIMIT))

    // handy categorizations of dependency types:
    all_types      = ((1<<TYPE_LIMIT)-1) & ((-1)<<FIRST_TYPE),
    non_ctxk_types = (1<<evol_method),
    ctxk_types     = all_types & ~non_ctxk_types,

    max_arg_count = 3,   // current maximum number of arguments (incl. ctxk)

    // A "context type" is a class or interface that
    // provides context for evaluating a dependency.
    // When present, it is one of the arguments (dep_context_arg).
    //
    // If a dependency does not have a context type, there is a
    // default context, depending on the type of the dependency.
    // This bit signals that a default context has been compressed away.
    default_context_type_bit = (1<<LG2_TYPE_LIMIT)
  };

  static const char* dep_name(DepType dept);
  static int         dep_args(DepType dept);
  static int  dep_context_arg(DepType dept) {
    return dept_in_mask(dept, ctxk_types)? 0: -1;
  }

 private:
  // State for writing a new set of dependencies:
  GrowableArray<int>*       _dep_seen;  // (seen[h->ident] & (1<<dept))
  GrowableArray<ciObject*>* _deps[TYPE_LIMIT];

  static const char* _dep_name[TYPE_LIMIT];
  static int         _dep_args[TYPE_LIMIT];

  static bool dept_in_mask(DepType dept, int mask) {
    return (int)dept >= 0 && dept < TYPE_LIMIT && ((1<<dept) & mask) != 0;
  }

  bool note_dep_seen(int dept, ciObject* x) {
    assert(dept < BitsPerInt, "oob");
    int x_id = x->ident();
    assert(_dep_seen != NULL, "deps must be writable");
    int seen = _dep_seen->at_grow(x_id, 0);
    _dep_seen->at_put(x_id, seen | (1<<dept));
    // return true if we've already seen dept/x
    return (seen & (1<<dept)) != 0;
  }

  bool maybe_merge_ctxk(GrowableArray<ciObject*>* deps,
                        int ctxk_i, ciKlass* ctxk);

  void sort_all_deps();
  size_t estimate_size_in_bytes();

  // Initialize _deps, etc.
  void initialize(ciEnv* env);

  // State for making a new set of dependencies:
  OopRecorder* _oop_recorder;

  // Logging support
  CompileLog* _log;

  address  _content_bytes;  // everything but the oop references, encoded
  size_t   _size_in_bytes;

 public:
  // Make a new empty dependencies set.
  Dependencies(ciEnv* env) {
    initialize(env);
  }

 private:
  // Check for a valid context type.
  // Enforce the restriction against array types.
  static void check_ctxk(ciKlass* ctxk) {
    assert(ctxk->is_instance_klass(), "java types only");
  }
  static void check_ctxk_concrete(ciKlass* ctxk) {
    assert(is_concrete_klass(ctxk->as_instance_klass()), "must be concrete");
  }
  static void check_ctxk_abstract(ciKlass* ctxk) {
    check_ctxk(ctxk);
    assert(!is_concrete_klass(ctxk->as_instance_klass()), "must be abstract");
  }

  void assert_common_1(DepType dept, ciObject* x);
  void assert_common_2(DepType dept, ciKlass* ctxk, ciObject* x);
  void assert_common_3(DepType dept, ciKlass* ctxk, ciObject* x, ciObject* x2);

 public:
  // Adding assertions to a new dependency set at compile time:
  void assert_evol_method(ciMethod* m);
  void assert_leaf_type(ciKlass* ctxk);
  void assert_abstract_with_unique_concrete_subtype(ciKlass* ctxk, ciKlass* conck);
  void assert_abstract_with_no_concrete_subtype(ciKlass* ctxk);
  void assert_concrete_with_no_concrete_subtype(ciKlass* ctxk);
  void assert_unique_concrete_method(ciKlass* ctxk, ciMethod* uniqm);
  void assert_abstract_with_exclusive_concrete_subtypes(ciKlass* ctxk, ciKlass* k1, ciKlass* k2);
  void assert_exclusive_concrete_methods(ciKlass* ctxk, ciMethod* m1, ciMethod* m2);
  void assert_has_no_finalizable_subclasses(ciKlass* ctxk);

  // Define whether a given method or type is concrete.
  // These methods define the term "concrete" as used in this module.
  // For this module, an "abstract" class is one which is non-concrete.
  //
  // Future optimizations may allow some classes to remain
  // non-concrete until their first instantiation, and allow some
  // methods to remain non-concrete until their first invocation.
  // In that case, there would be a middle ground between concrete
  // and abstract (as defined by the Java language and VM).
  static bool is_concrete_klass(klassOop k);    // k is instantiable
  static bool is_concrete_method(methodOop m);  // m is invocable
  static Klass* find_finalizable_subclass(Klass* k);

  // These versions of the concreteness queries work through the CI.
  // The CI versions are allowed to skew sometimes from the VM
  // (oop-based) versions.  The cost of such a difference is a
  // (safely) aborted compilation, or a deoptimization, or a missed
  // optimization opportunity.
  //
  // In order to prevent spurious assertions, query results must
  // remain stable within any single ciEnv instance.  (I.e., they must
  // not go back into the VM to get their value; they must cache the
  // bit in the CI, either eagerly or lazily.)
  static bool is_concrete_klass(ciInstanceKlass* k); // k appears instantiable
  static bool is_concrete_method(ciMethod* m);       // m appears invocable
  static bool has_finalizable_subclass(ciInstanceKlass* k);

  // As a general rule, it is OK to compile under the assumption that
  // a given type or method is concrete, even if it at some future
  // point becomes abstract.  So dependency checking is one-sided, in
  // that it permits supposedly concrete classes or methods to turn up
  // as really abstract.  (This shouldn't happen, except during class
  // evolution, but that's the logic of the checking.)  However, if a
  // supposedly abstract class or method suddenly becomes concrete, a
  // dependency on it must fail.

  // Checking old assertions at run-time (in the VM only):
  static klassOop check_evol_method(methodOop m);
  static klassOop check_leaf_type(klassOop ctxk);
  static klassOop check_abstract_with_unique_concrete_subtype(klassOop ctxk, klassOop conck,
                                                              DepChange* changes = NULL);
  static klassOop check_abstract_with_no_concrete_subtype(klassOop ctxk,
                                                          DepChange* changes = NULL);
  static klassOop check_concrete_with_no_concrete_subtype(klassOop ctxk,
                                                          DepChange* changes = NULL);
  static klassOop check_unique_concrete_method(klassOop ctxk, methodOop uniqm,
                                               DepChange* changes = NULL);
  static klassOop check_abstract_with_exclusive_concrete_subtypes(klassOop ctxk, klassOop k1, klassOop k2,
                                                                  DepChange* changes = NULL);
  static klassOop check_exclusive_concrete_methods(klassOop ctxk, methodOop m1, methodOop m2,
                                                   DepChange* changes = NULL);
  static klassOop check_has_no_finalizable_subclasses(klassOop ctxk,
                                                      DepChange* changes = NULL);
  // A returned klassOop is NULL if the dependency assertion is still
  // valid.  A non-NULL klassOop is a 'witness' to the assertion
  // failure, a point in the class hierarchy where the assertion has
  // been proven false.  For example, if check_leaf_type returns
  // non-NULL, the value is a subtype of the supposed leaf type.  This
  // witness value may be useful for logging the dependency failure.
  // Note that, when a dependency fails, there may be several possible
  // witnesses to the failure.  The value returned from the check_foo
  // method is chosen arbitrarily.

  // The 'changes' value, if non-null, requests a limited spot-check
  // near the indicated recent changes in the class hierarchy.
  // It is used by DepStream::spot_check_dependency_at.

  // Detecting possible new assertions:
  static klassOop  find_unique_concrete_subtype(klassOop ctxk);
  static methodOop find_unique_concrete_method(klassOop ctxk, methodOop m);
  static int       find_exclusive_concrete_subtypes(klassOop ctxk, int klen, klassOop k[]);
  static int       find_exclusive_concrete_methods(klassOop ctxk, int mlen, methodOop m[]);

  // Create the encoding which will be stored in an nmethod.
  void encode_content_bytes();

  address content_bytes() {
    assert(_content_bytes != NULL, "encode it first");
    return _content_bytes;
  }
  size_t size_in_bytes() {
    assert(_content_bytes != NULL, "encode it first");
    return _size_in_bytes;
  }

  OopRecorder* oop_recorder() { return _oop_recorder; }
  CompileLog*  log()          { return _log; }

  void copy_to(nmethod* nm);

  void log_all_dependencies();
  void log_dependency(DepType dept, int nargs, ciObject* args[]) {
    write_dependency_to(log(), dept, nargs, args);
  }
  void log_dependency(DepType dept,
                      ciObject* x0,
                      ciObject* x1 = NULL,
                      ciObject* x2 = NULL) {
    if (log() == NULL)  return;
    ciObject* args[max_arg_count];
    args[0] = x0;
    args[1] = x1;
    args[2] = x2;
    assert(2 < max_arg_count, "");
    log_dependency(dept, dep_args(dept), args);
  }

  static void write_dependency_to(CompileLog* log,
                                  DepType dept,
                                  int nargs, ciObject* args[],
                                  klassOop witness = NULL);
  static void write_dependency_to(CompileLog* log,
                                  DepType dept,
                                  int nargs, oop args[],
                                  klassOop witness = NULL);
  static void write_dependency_to(xmlStream* xtty,
                                  DepType dept,
                                  int nargs, oop args[],
                                  klassOop witness = NULL);
  static void print_dependency(DepType dept,
                               int nargs, oop args[],
                               klassOop witness = NULL);

 private:
  // helper for encoding common context types as zero:
  static ciKlass* ctxk_encoded_as_null(DepType dept, ciObject* x);

  static klassOop ctxk_encoded_as_null(DepType dept, oop x);

 public:
  // Use this to iterate over an nmethod's dependency set.
  // Works on new and old dependency sets.
  // Usage:
  //
  // ;
  // Dependencies::DepType dept;
  // for (Dependencies::DepStream deps(nm); deps.next(); ) {
  //   ...
  // }
  //
  // The caller must be in the VM, since oops are not wrapped in handles.
  class DepStream {
  private:
    nmethod*              _code;   // null if in a compiler thread
    Dependencies*         _deps;   // null if not in a compiler thread
    CompressedReadStream  _bytes;
#ifdef ASSERT
    size_t                _byte_limit;
#endif

    // iteration variables:
    DepType               _type;
    int                   _xi[max_arg_count+1];

    void initial_asserts(size_t byte_limit) NOT_DEBUG({});

    inline oop recorded_oop_at(int i);
        // => _code? _code->oop_at(i): *_deps->_oop_recorder->handle_at(i)

    klassOop check_dependency_impl(DepChange* changes);

  public:
    DepStream(Dependencies* deps)
      : _deps(deps),
        _code(NULL),
        _bytes(deps->content_bytes())
    {
      initial_asserts(deps->size_in_bytes());
    }
    DepStream(nmethod* code)
      : _deps(NULL),
        _code(code),
        _bytes(code->dependencies_begin())
    {
      initial_asserts(code->dependencies_size());
    }

    bool next();

    DepType type()               { return _type; }
    int argument_count()         { return dep_args(type()); }
    int argument_index(int i)    { assert(0 <= i && i < argument_count(), "oob");
                                   return _xi[i]; }
    oop argument(int i);         // => recorded_oop_at(argument_index(i))
    klassOop context_type();

    methodOop method_argument(int i) {
      oop x = argument(i);
      assert(x->is_method(), "type");
      return (methodOop) x;
    }
    klassOop type_argument(int i) {
      oop x = argument(i);
      assert(x->is_klass(), "type");
      return (klassOop) x;
    }

    // The point of the whole exercise:  Is this dep is still OK?
    klassOop check_dependency() {
      return check_dependency_impl(NULL);
    }
    // A lighter version:  Checks only around recent changes in a class
    // hierarchy.  (See Universe::flush_dependents_on.)
    klassOop spot_check_dependency_at(DepChange& changes);

    // Log the current dependency to xtty or compilation log.
    void log_dependency(klassOop witness = NULL);

    // Print the current dependency to tty.
    void print_dependency(klassOop witness = NULL, bool verbose = false);
  };
  friend class Dependencies::DepStream;

  static void print_statistics() PRODUCT_RETURN;
};

// A class hierarchy change coming through the VM (under the Compile_lock).
// The change is structured as a single new type with any number of supers
// and implemented interface types.  Other than the new type, any of the
// super types can be context types for a relevant dependency, which the
// new type could invalidate.
class DepChange : public StackObj {
 public:
  enum ChangeType {
    NO_CHANGE = 0,              // an uninvolved klass
    Change_new_type,            // a newly loaded type
    Change_new_sub,             // a super with a new subtype
    Change_new_impl,            // an interface with a new implementation
    CHANGE_LIMIT,
    Start_Klass = CHANGE_LIMIT  // internal indicator for ContextStream
  };

 private:
  // each change set is rooted in exactly one new type (at present):
  KlassHandle _new_type;

  void initialize();

 public:
  // notes the new type, marks it and all its super-types
  DepChange(KlassHandle new_type)
    : _new_type(new_type)
  {
    initialize();
  }

  // cleans up the marks
  ~DepChange();

  klassOop new_type()                   { return _new_type(); }

  // involves_context(k) is true if k is new_type or any of the super types
  bool involves_context(klassOop k);

  // Usage:
  // for (DepChange::ContextStream str(changes); str.next(); ) {
  //   klassOop k = str.klass();
  //   switch (str.change_type()) {
  //     ...
  //   }
  // }
  class ContextStream : public StackObj {
   private:
    DepChange&  _changes;
    friend class DepChange;

    // iteration variables:
    ChangeType  _change_type;
    klassOop    _klass;
    objArrayOop _ti_base;    // i.e., transitive_interfaces
    int         _ti_index;
    int         _ti_limit;

    // start at the beginning:
    void start() {
      klassOop new_type = _changes.new_type();
      _change_type = (new_type == NULL ? NO_CHANGE: Start_Klass);
      _klass = new_type;
      _ti_base = NULL;
      _ti_index = 0;
      _ti_limit = 0;
    }

   public:
    ContextStream(DepChange& changes)
      : _changes(changes)
    { start(); }

    ContextStream(DepChange& changes, No_Safepoint_Verifier& nsv)
      : _changes(changes)
      // the nsv argument makes it safe to hold oops like _klass
    { start(); }

    bool next();

    ChangeType change_type()     { return _change_type; }
    klassOop   klass()           { return _klass; }
  };
  friend class DepChange::ContextStream;

  void print();
};
