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

#include "precompiled.hpp"
#include "ci/ciMethod.hpp"
#include "ci/ciUtilities.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/compilerOracle.hpp"

CompilerDirectives::CompilerDirectives() :_match(NULL), _next(NULL), _ref_count(0) {
  _c1_store = new DirectiveSet(this);
  _c2_store = new DirectiveSet(this);
};

CompilerDirectives::~CompilerDirectives() {
  if (_c1_store != NULL) {
    delete _c1_store;
  }
  if (_c2_store != NULL) {
    delete _c2_store;
  }

  // remove all linked method matchers
  BasicMatcher* tmp = _match;
  while (tmp != NULL) {
    BasicMatcher* next = tmp->next();
    delete tmp;
    tmp = next;
  }
}

void CompilerDirectives::print(outputStream* st) {
  assert(DirectivesStack_lock->owned_by_self(), "");
  if (_match != NULL) {
    st->cr();
    st->print("Directive:");
    if (is_default_directive()) {
      st->print_cr(" (default)");
    } else {
      st->cr();
    }
    st->print(" matching: ");
    _match->print(st);
    BasicMatcher* tmp = _match->next();
    while (tmp != NULL) {
      st->print(", ");
      tmp->print(st);
      tmp = tmp->next();
    }
    st->cr();
  } else {
    assert(0, "There should always be a match");
  }

  if (_c1_store != NULL) {
    st->print_cr(" c1 directives:");
    _c1_store->print(st);
  }
  if (_c2_store != NULL) {
    st->cr();
    st->print_cr(" c2 directives:");
    _c2_store->print(st);
  }
  //---
}

void CompilerDirectives::finalize(outputStream* st) {
  if (_c1_store != NULL) {
    _c1_store->finalize(st);
  }
  if (_c2_store != NULL) {
    _c2_store->finalize(st);
  }
}

void DirectiveSet::finalize(outputStream* st) {
  // Check LogOption and warn
  if (LogOption && !LogCompilation) {
    st->print_cr("Warning:  +LogCompilation must be set to enable compilation logging from directives");
  }

  // if any flag has been modified - set directive as enabled
  // unless it already has been explicitly set.
  if (!_modified[EnableIndex]) {
    if (_inlinematchers != NULL) {
      EnableOption = true;
      return;
    }
    int i;
    for (i = 0; i < number_of_flags; i++) {
      if (_modified[i]) {
        EnableOption = true;
        return;
      }
    }
  }
}

CompilerDirectives* CompilerDirectives::next() {
  return _next;
}

bool CompilerDirectives::match(methodHandle method) {
  if (is_default_directive()) {
    return true;
  }
  if (method == NULL) {
    return false;
  }
  if (_match->match(method)) {
    return true;
  }
  return false;
}

bool CompilerDirectives::add_match(char* str, const char*& error_msg) {
  BasicMatcher* bm = BasicMatcher::parse_method_pattern(str, error_msg);
  if (bm == NULL) {
    assert(error_msg != NULL, "Must have error message");
    return false;
  } else {
    bm->set_next(_match);
    _match = bm;
    return true;
  }
}

void CompilerDirectives::inc_refcount() {
  assert(DirectivesStack_lock->owned_by_self(), "");
  _ref_count++;
}

void CompilerDirectives::dec_refcount() {
  assert(DirectivesStack_lock->owned_by_self(), "");
  _ref_count--;
}

int CompilerDirectives::refcount() {
  assert(DirectivesStack_lock->owned_by_self(), "");
  return _ref_count;
}

DirectiveSet* CompilerDirectives::get_for(AbstractCompiler *comp) {
  assert(DirectivesStack_lock->owned_by_self(), "");
  inc_refcount(); // The compiling thread is responsible to decrement this when finished.
  if (comp == NULL) { // Xint
    return _c1_store;
  } else if (comp->is_c2()) {
    return _c2_store;
  } else if (comp->is_c1()) {
    return _c1_store;
  } else if (comp->is_shark()) {
    return NULL;
  } else if (comp->is_jvmci()) {
    return NULL;
  }
  ShouldNotReachHere();
  return NULL;
}

// In the list of disabled intrinsics, the ID of the disabled intrinsics can separated:
// - by ',' (if -XX:DisableIntrinsic is used once when invoking the VM) or
// - by '\n' (if -XX:DisableIntrinsic is used multiple times when invoking the VM) or
// - by ' ' (if DisableIntrinsic is used on a per-method level, e.g., with CompileCommand).
//
// To simplify the processing of the list, the canonicalize_disableintrinsic() method
// returns a new copy of the list in which '\n' and ' ' is replaced with ','.
ccstrlist DirectiveSet::canonicalize_disableintrinsic(ccstrlist option_value) {
  char* canonicalized_list = NEW_C_HEAP_ARRAY(char, strlen(option_value) + 1, mtCompiler);
  int i = 0;
  char current;
  while ((current = option_value[i]) != '\0') {
    if (current == '\n' || current == ' ') {
      canonicalized_list[i] = ',';
    } else {
      canonicalized_list[i] = current;
    }
    i++;
  }
  canonicalized_list[i] = '\0';
  return canonicalized_list;
}

DirectiveSet::DirectiveSet(CompilerDirectives* d) :_inlinematchers(NULL), _directive(d) {
#define init_defaults_definition(name, type, dvalue, compiler) this->name##Option = dvalue;
  compilerdirectives_common_flags(init_defaults_definition)
  compilerdirectives_c2_flags(init_defaults_definition)
  compilerdirectives_c1_flags(init_defaults_definition)
  memset(_modified, 0, sizeof _modified);

  // Canonicalize DisableIntrinsic to contain only ',' as a separator.
  this->DisableIntrinsicOption = canonicalize_disableintrinsic(DisableIntrinsic);
}

DirectiveSet::~DirectiveSet() {
  // remove all linked methodmatchers
  InlineMatcher* tmp = _inlinematchers;
  while (tmp != NULL) {
    InlineMatcher* next = tmp->next();
    delete tmp;
    tmp = next;
  }

  // When constructed, DirectiveSet canonicalizes the DisableIntrinsic flag
  // into a new string. Therefore, that string is deallocated when
  // the DirectiveSet is destroyed.
  assert(this->DisableIntrinsicOption != NULL, "");
  FREE_C_HEAP_ARRAY(char, (void *)this->DisableIntrinsicOption);
}

// Backward compatibility for CompileCommands
// Breaks the abstraction and causes lots of extra complexity
// - if some option is changed we need to copy directiveset since it no longer can be shared
// - Need to free copy after use
// - Requires a modified bit so we don't overwrite options that is set by directives

DirectiveSet* DirectiveSet::compilecommand_compatibility_init(methodHandle method) {
  // Early bail out - checking all options is expensive - we rely on them not being used
  // Only set a flag if it has not been modified and value changes.
  // Only copy set if a flag needs to be set
  if (!CompilerDirectivesIgnoreCompileCommandsOption && CompilerOracle::has_any_option()) {
    DirectiveSet* set = DirectiveSet::clone(this);

    bool changed = false; // Track if we actually change anything

    // All CompileCommands are not equal so this gets a bit verbose
    // When CompileCommands have been refactored less clutter will remain.
    if (CompilerOracle::should_break_at(method)) {
      if (!_modified[BreakAtCompileIndex]) {
        set->BreakAtCompileOption = true;
        changed = true;
      }
      if (!_modified[BreakAtExecuteIndex]) {
        set->BreakAtExecuteOption = true;
        changed = true;
      }
    }
    if (!_modified[LogIndex]) {
      bool log = CompilerOracle::should_log(method);
      if (log != set->LogOption) {
        set->LogOption = log;
        changed = true;
      }
    }

    if (CompilerOracle::should_print(method)) {
      if (!_modified[PrintAssemblyIndex]) {
        set->PrintAssemblyOption = true;
        changed = true;
      }
    }
    // Exclude as in should not compile == Enabled
    if (CompilerOracle::should_exclude(method)) {
      if (!_modified[ExcludeIndex]) {
        set->ExcludeOption = true;
        changed = true;
      }
    }

    // inline and dontinline (including exclude) are implemented in the directiveset accessors
#define init_default_cc(name, type, dvalue, cc_flag) { type v; if (!_modified[name##Index] && CompilerOracle::has_option_value(method, #cc_flag, v) && v != this->name##Option) { set->name##Option = v; changed = true;} }
    compilerdirectives_common_flags(init_default_cc)
    compilerdirectives_c2_flags(init_default_cc)
    compilerdirectives_c1_flags(init_default_cc)

    // Canonicalize DisableIntrinsic to contain only ',' as a separator.
    ccstrlist option_value;
    if (!_modified[DisableIntrinsicIndex] &&
        CompilerOracle::has_option_value(method, "DisableIntrinsic", option_value)) {
      set->DisableIntrinsicOption = canonicalize_disableintrinsic(option_value);
    }


    if (!changed) {
      // We didn't actually update anything, discard.
      delete set;
    } else {
      // We are returning a (parentless) copy. The originals parent don't need to account for this.
      DirectivesStack::release(this);
      return set;
    }
  }
  // Nothing changed
  return this;
}

CompilerDirectives* DirectiveSet::directive() {
  assert(_directive != NULL, "Must have been initialized");
  return _directive;
}

bool DirectiveSet::matches_inline(methodHandle method, int inline_action) {
  if (_inlinematchers != NULL) {
    if (_inlinematchers->match(method, inline_action)) {
      return true;
    }
  }
  return false;
}

bool DirectiveSet::should_inline(ciMethod* inlinee) {
  inlinee->check_is_loaded();
  VM_ENTRY_MARK;
  methodHandle mh(THREAD, inlinee->get_Method());

  if (_inlinematchers != NULL) {
    return matches_inline(mh, InlineMatcher::force_inline);
  }
  if (!CompilerDirectivesIgnoreCompileCommandsOption) {
    return CompilerOracle::should_inline(mh);
  }
  return false;
}

bool DirectiveSet::should_not_inline(ciMethod* inlinee) {
  inlinee->check_is_loaded();
  VM_ENTRY_MARK;
  methodHandle mh(THREAD, inlinee->get_Method());

  if (_inlinematchers != NULL) {
    return matches_inline(mh, InlineMatcher::dont_inline);
  }
  if (!CompilerDirectivesIgnoreCompileCommandsOption) {
    return CompilerOracle::should_not_inline(mh);
  }
  return false;
}

bool DirectiveSet::parse_and_add_inline(char* str, const char*& error_msg) {
  InlineMatcher* m = InlineMatcher::parse_inline_pattern(str, error_msg);
  if (m != NULL) {
    // add matcher last in chain - the order is significant
    append_inline(m);
    return true;
  } else {
    assert(error_msg != NULL, "Error message must be set");
    return false;
  }
}

void DirectiveSet::append_inline(InlineMatcher* m) {
  if (_inlinematchers == NULL) {
    _inlinematchers = m;
    return;
  }
  InlineMatcher* tmp = _inlinematchers;
  while (tmp->next() != NULL) {
    tmp = tmp->next();
  }
  tmp->set_next(m);
}

void DirectiveSet::print_inline(outputStream* st) {
  if (_inlinematchers == NULL) {
    st->print_cr("  inline: -");
  } else {
    st->print("  inline: ");
    _inlinematchers->print(st);
    InlineMatcher* tmp = _inlinematchers->next();
    while (tmp != NULL) {
      st->print(", ");
      tmp->print(st);
      tmp = tmp->next();
    }
    st->cr();
  }
}

bool DirectiveSet::is_intrinsic_disabled(methodHandle method) {
  vmIntrinsics::ID id = method->intrinsic_id();
  assert(id != vmIntrinsics::_none, "must be a VM intrinsic");

  ResourceMark rm;

  // Create a copy of the string that contains the list of disabled
  // intrinsics. The copy is created because the string
  // will be modified by strtok(). Then, the list is tokenized with
  // ',' as a separator.
  size_t length = strlen(DisableIntrinsicOption);
  char* local_list = NEW_RESOURCE_ARRAY(char, length + 1);
  strncpy(local_list, DisableIntrinsicOption, length + 1);

  char* token = strtok(local_list, ",");
  while (token != NULL) {
    if (strcmp(token, vmIntrinsics::name_at(id)) == 0) {
      return true;
    } else {
      token = strtok(NULL, ",");
    }
  }

  return false;
}

DirectiveSet* DirectiveSet::clone(DirectiveSet const* src) {
  DirectiveSet* set = new DirectiveSet(NULL);
  memcpy(set->_modified, src->_modified, sizeof(src->_modified));

  InlineMatcher* tmp = src->_inlinematchers;
  while (tmp != NULL) {
    set->append_inline(tmp->clone());
    tmp = tmp->next();
  }

  #define copy_members_definition(name, type, dvalue, cc_flag) set->name##Option = src->name##Option;
    compilerdirectives_common_flags(copy_members_definition)
    compilerdirectives_c2_flags(copy_members_definition)
    compilerdirectives_c1_flags(copy_members_definition)

  // Create a local copy of the DisableIntrinsicOption.
  assert(src->DisableIntrinsicOption != NULL, "");
  size_t len = strlen(src->DisableIntrinsicOption) + 1;
  char* s = NEW_C_HEAP_ARRAY(char, len, mtCompiler);
  strncpy(s, src->DisableIntrinsicOption, len);
  assert(s[len-1] == '\0', "");
  set->DisableIntrinsicOption = s;
  return set;
}

// Create a new dirstack and push a default directive
void DirectivesStack::init() {
  CompilerDirectives* _default_directives = new CompilerDirectives();
  char str[] = "*.*";
  const char* error_msg = NULL;
  _default_directives->add_match(str, error_msg);
#ifdef COMPILER1
  _default_directives->_c1_store->EnableOption = true;
#endif
#ifdef COMPILER2
  _default_directives->_c2_store->EnableOption = true;
#endif
  assert(error_msg == NULL, "Must succeed.");
  push(_default_directives);
}

DirectiveSet* DirectivesStack::getDefaultDirective(AbstractCompiler* comp) {
  MutexLockerEx locker(DirectivesStack_lock, Mutex::_no_safepoint_check_flag);

  assert(_bottom != NULL, "Must never be empty");
  return _bottom->get_for(comp);
}

void DirectivesStack::push(CompilerDirectives* directive) {
  MutexLockerEx locker(DirectivesStack_lock, Mutex::_no_safepoint_check_flag);

  directive->inc_refcount();
  if (_top == NULL) {
    assert(_bottom == NULL, "There can only be one default directive");
    _bottom = directive; // default directive, can never be removed.
  }

  directive->set_next(_top);
  _top = directive;
  _depth++;
}

void DirectivesStack::pop() {
  MutexLockerEx locker(DirectivesStack_lock, Mutex::_no_safepoint_check_flag);
  pop_inner();
}

void DirectivesStack::pop_inner() {
  assert(DirectivesStack_lock->owned_by_self(), "");

  if (_top->next() == NULL) {
    return; // Do nothing - don't allow an empty stack
  }
  CompilerDirectives* tmp = _top;
  _top = _top->next();
  _depth--;

  DirectivesStack::release(tmp);
}

bool DirectivesStack::check_capacity(int request_size, outputStream* st) {
  if ((request_size + _depth) > CompilerDirectivesLimit) {
    st->print_cr("Could not add %i more directives. Currently %i/%i directives.", request_size, _depth, CompilerDirectivesLimit);
    return false;
  }
  return true;
}

void DirectivesStack::clear() {
  // holding the lock during the whole operation ensuring consistent result
  MutexLockerEx locker(DirectivesStack_lock, Mutex::_no_safepoint_check_flag);
  while (_top->next() != NULL) {
    pop_inner();
  }
}

void DirectivesStack::print(outputStream* st) {
  MutexLockerEx locker(DirectivesStack_lock, Mutex::_no_safepoint_check_flag);
  CompilerDirectives* tmp = _top;
  while (tmp != NULL) {
    tmp->print(st);
    tmp = tmp->next();
    st->cr();
  }
}

void DirectivesStack::release(DirectiveSet* set) {
  MutexLockerEx locker(DirectivesStack_lock, Mutex::_no_safepoint_check_flag);
  if (set->is_exclusive_copy()) {
    // Old CompilecCmmands forced us to create an exclusive copy
    delete set;
  } else {
    assert(set->directive() != NULL, "");
    release(set->directive());
  }
}


void DirectivesStack::release(CompilerDirectives* dir) {
  assert(DirectivesStack_lock->owned_by_self(), "");
  dir->dec_refcount();
  if (dir->refcount() == 0) {
    delete dir;
  }
}

DirectiveSet* DirectivesStack::getMatchingDirective(methodHandle method, AbstractCompiler *comp) {
  assert(_depth > 0, "Must never be empty");

  DirectiveSet* match = NULL;
  {
    MutexLockerEx locker(DirectivesStack_lock, Mutex::_no_safepoint_check_flag);

    CompilerDirectives* dir = _top;
    assert(dir != NULL, "Must be initialized");

    while (dir != NULL) {
      if (dir->is_default_directive() || dir->match(method)) {
        match = dir->get_for(comp);
        if (match == NULL) {
          // temporary workaround for compilers without directives.
          if (dir->is_default_directive()) {
            // default dir is always enabled
            // match c1 store - it contains all common flags even if C1 is unavailable
            match = dir->_c1_store;
            break;
          }
        }
        if (match->EnableOption) {
          // The directiveSet for this compile is also enabled -> success
          break;
        }
      }
      dir = dir->next();
    }
  }

  guarantee(match != NULL, "There should always be a default directive that matches");
  // Check for legacy compile commands update, without DirectivesStack_lock
  return match->compilecommand_compatibility_init(method);
}
