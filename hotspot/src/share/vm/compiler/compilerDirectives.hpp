/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_COMPILER_COMPILERDIRECTIVES_HPP
#define SHARE_VM_COMPILER_COMPILERDIRECTIVES_HPP

#include "ci/ciMetadata.hpp"
#include "ci/ciMethod.hpp"
#include "ci/ciUtilities.hpp"
#include "compiler/methodMatcher.hpp"
#include "compiler/compilerOracle.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/exceptions.hpp"

  //      Directives flag name,    type, default value, compile command name
  #define compilerdirectives_common_flags(cflags) \
    cflags(Enable,                  bool, false, X) \
    cflags(Exclude,                 bool, false, X) \
    cflags(BreakAtExecute,          bool, false, X) \
    cflags(BreakAtCompile,          bool, false, X) \
    cflags(Log,                     bool, false, X) \
    cflags(PrintAssembly,           bool, PrintAssembly, PrintAssembly) \
    cflags(PrintInlining,           bool, PrintInlining, PrintInlining) \
    cflags(PrintNMethods,           bool, PrintNMethods, PrintNMethods) \
    cflags(ReplayInline,            bool, false, ReplayInline) \
    cflags(DumpReplay,              bool, false, DumpReplay) \
    cflags(DumpInline,              bool, false, DumpInline) \
    cflags(CompilerDirectivesIgnoreCompileCommands, bool, CompilerDirectivesIgnoreCompileCommands, X) \
    cflags(DisableIntrinsic,        ccstrlist, DisableIntrinsic, DisableIntrinsic)

#ifdef COMPILER1
  #define compilerdirectives_c1_flags(cflags)
#else
  #define compilerdirectives_c1_flags(cflags)
#endif

#ifdef COMPILER2
  #define compilerdirectives_c2_flags(cflags) \
    cflags(BlockLayoutByFrequency,  bool, BlockLayoutByFrequency,  BlockLayoutByFrequency) \
    cflags(PrintOptoAssembly,       bool, PrintOptoAssembly, PrintOptoAssembly) \
    cflags(PrintIntrinsics,         bool, PrintIntrinsics, PrintIntrinsics) \
    cflags(TraceOptoPipelining,     bool, false, TraceOptoPipelining) \
    cflags(TraceOptoOutput,         bool, false, TraceOptoOutput) \
    cflags(TraceSpilling,           bool, TraceSpilling, TraceSpilling) \
    cflags(Vectorize,               bool, false, Vectorize) \
    cflags(VectorizeDebug,          bool, false, VectorizeDebug) \
    cflags(CloneMapDebug,           bool, false, CloneMapDebug) \
    cflags(DoReserveCopyInSuperWordDebug, bool, false, DoReserveCopyInSuperWordDebug) \
    cflags(IGVPrintLevel,           intx, PrintIdealGraphLevel, IGVPrintLevel) \
    cflags(MaxNodeLimit,            intx, MaxNodeLimit, MaxNodeLimit)
#else
  #define compilerdirectives_c2_flags(cflags)
#endif

class CompilerDirectives;
class DirectiveSet;

class DirectivesStack : AllStatic {
private:
  static CompilerDirectives* _top;
  static CompilerDirectives* _bottom;
  static int _depth;

  static void pop_inner(); // no lock version of pop
public:
  static void init();
  static DirectiveSet* getMatchingDirective(methodHandle mh, AbstractCompiler* comp);
  static DirectiveSet* getDefaultDirective(AbstractCompiler* comp);
  static void push(CompilerDirectives* directive);
  static void pop();
  static bool check_capacity(int request_size, outputStream* st);
  static void clear();
  static void print(outputStream* st);
  static void release(DirectiveSet* set);
  static void release(CompilerDirectives* dir);
};

class DirectiveSet : public CHeapObj<mtCompiler> {
private:
  InlineMatcher* _inlinematchers;
  CompilerDirectives* _directive;

  static ccstrlist canonicalize_disableintrinsic(ccstrlist option_value);

public:
  DirectiveSet(CompilerDirectives* directive);
  ~DirectiveSet();
  CompilerDirectives* directive();
  bool parse_and_add_inline(char* str, const char*& error_msg);
  void append_inline(InlineMatcher* m);
  bool should_inline(ciMethod* inlinee);
  bool should_not_inline(ciMethod* inlinee);
  void print_inline(outputStream* st);
  DirectiveSet* compilecommand_compatibility_init(methodHandle method);
  bool is_exclusive_copy() { return _directive == NULL; }
  bool matches_inline(methodHandle method, int inline_action);
  static DirectiveSet* clone(DirectiveSet const* src);
  bool is_intrinsic_disabled(methodHandle method);
  void finalize();

  typedef enum {
#define enum_of_flags(name, type, dvalue, cc_flag) name##Index,
    compilerdirectives_common_flags(enum_of_flags)
    compilerdirectives_c2_flags(enum_of_flags)
    compilerdirectives_c1_flags(enum_of_flags)
    number_of_flags
  } flags;

  bool _modified[number_of_flags];

#define flag_store_definition(name, type, dvalue, cc_flag) type name##Option;
  compilerdirectives_common_flags(flag_store_definition)
  compilerdirectives_c2_flags(flag_store_definition)
  compilerdirectives_c1_flags(flag_store_definition)

// Casting to get the same function signature for all setters. Used from parser.
#define set_function_definition(name, type, dvalue, cc_flag) void set_##name(void* value) { type val = *(type*)value; name##Option = val; _modified[name##Index] = 1; }
  compilerdirectives_common_flags(set_function_definition)
  compilerdirectives_c2_flags(set_function_definition)
  compilerdirectives_c1_flags(set_function_definition)

  void print_intx(outputStream* st, ccstr n, intx v, bool mod) { if (mod) { st->print("%s:" INTX_FORMAT " ", n, v); } }
  void print_bool(outputStream* st, ccstr n, bool v, bool mod) { if (mod) { st->print("%s:%s ", n, v ? "true" : "false"); } }
  void print_double(outputStream* st, ccstr n, double v, bool mod) { if (mod) { st->print("%s:%f ", n, v); } }
  void print_ccstr(outputStream* st, ccstr n, ccstr v, bool mod) { if (mod) { st->print("%s:%s ", n, v); } }
  void print_ccstrlist(outputStream* st, ccstr n, ccstr v, bool mod) { print_ccstr(st, n, v, mod); }

void print(outputStream* st) {
    print_inline(st);
    st->print("  ");
#define print_function_definition(name, type, dvalue, cc_flag) print_##type(st, #name, this->name##Option, true);//(bool)_modified[name##Index]);
    compilerdirectives_common_flags(print_function_definition)
    compilerdirectives_c2_flags(print_function_definition)
    compilerdirectives_c1_flags(print_function_definition)
    st->cr();
  }
};

class CompilerDirectives : public CHeapObj<mtCompiler> {
private:
  CompilerDirectives* _next;
  BasicMatcher* _match;
  int _ref_count;

public:

  CompilerDirectives();
  ~CompilerDirectives();

  CompilerDirectives* next();
  void set_next(CompilerDirectives* next) {_next = next; }

  bool match(methodHandle method);
  BasicMatcher* match() { return _match; }
  bool add_match(char* str, const char*& error_msg);
  DirectiveSet* get_for(AbstractCompiler *comp);
  void print(outputStream* st);
  bool is_default_directive() { return _next == NULL; }
  void finalize();

  void inc_refcount();
  void dec_refcount();
  int refcount();

  DirectiveSet* _c1_store;
  DirectiveSet* _c2_store;
};

#endif // SHARE_VM_COMPILER_COMPILERDIRECTIVES_HPP
