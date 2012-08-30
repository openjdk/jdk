/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_Canonicalizer.hpp"
#include "c1/c1_IR.hpp"
#include "c1/c1_ValueMap.hpp"
#include "utilities/bitMap.inline.hpp"


#ifndef PRODUCT

  int ValueMap::_number_of_finds = 0;
  int ValueMap::_number_of_hits = 0;
  int ValueMap::_number_of_kills = 0;

  #define TRACE_VALUE_NUMBERING(code) if (PrintValueNumbering) { code; }

#else

  #define TRACE_VALUE_NUMBERING(code)

#endif


ValueMap::ValueMap()
  : _nesting(0)
  , _entries(ValueMapInitialSize, NULL)
  , _killed_values()
  , _entry_count(0)
{
  NOT_PRODUCT(reset_statistics());
}


ValueMap::ValueMap(ValueMap* old)
  : _nesting(old->_nesting + 1)
  , _entries(old->_entries.length())
  , _killed_values()
  , _entry_count(old->_entry_count)
{
  for (int i = size() - 1; i >= 0; i--) {
    _entries.at_put(i, old->entry_at(i));
  }
  _killed_values.set_from(&old->_killed_values);
}


void ValueMap::increase_table_size() {
  int old_size = size();
  int new_size = old_size * 2 + 1;

  ValueMapEntryList worklist(8);
  ValueMapEntryArray new_entries(new_size, NULL);
  int new_entry_count = 0;

  TRACE_VALUE_NUMBERING(tty->print_cr("increasing table size from %d to %d", old_size, new_size));

  for (int i = old_size - 1; i >= 0; i--) {
    ValueMapEntry* entry;
    for (entry = entry_at(i); entry != NULL; entry = entry->next()) {
      if (!is_killed(entry->value())) {
        worklist.push(entry);
      }
    }

    while (!worklist.is_empty()) {
      entry = worklist.pop();
      int new_index = entry_index(entry->hash(), new_size);

      if (entry->nesting() != nesting() && new_entries.at(new_index) != entry->next()) {
        // changing entries with a lower nesting than the current nesting of the table
        // is not allowed because then the same entry is contained in multiple value maps.
        // clone entry when next-pointer must be changed
        entry = new ValueMapEntry(entry->hash(), entry->value(), entry->nesting(), NULL);
      }
      entry->set_next(new_entries.at(new_index));
      new_entries.at_put(new_index, entry);
      new_entry_count++;
    }
  }

  _entries = new_entries;
  _entry_count = new_entry_count;
}


Value ValueMap::find_insert(Value x) {
  const intx hash = x->hash();
  if (hash != 0) {
    // 0 hash means: exclude from value numbering
    NOT_PRODUCT(_number_of_finds++);

    for (ValueMapEntry* entry = entry_at(entry_index(hash, size())); entry != NULL; entry = entry->next()) {
      if (entry->hash() == hash) {
        Value f = entry->value();

        if (!is_killed(f) && f->is_equal(x)) {
          NOT_PRODUCT(_number_of_hits++);
          TRACE_VALUE_NUMBERING(tty->print_cr("Value Numbering: %s %c%d equal to %c%d  (size %d, entries %d, nesting-diff %d)", x->name(), x->type()->tchar(), x->id(), f->type()->tchar(), f->id(), size(), entry_count(), nesting() - entry->nesting()));

          if (entry->nesting() != nesting() && f->as_Constant() == NULL) {
            // non-constant values of of another block must be pinned,
            // otherwise it is possible that they are not evaluated
            f->pin(Instruction::PinGlobalValueNumbering);
          }
          assert(x->type()->tag() == f->type()->tag(), "should have same type");

          return f;

        }
      }
    }

    // x not found, so insert it
    if (entry_count() >= size_threshold()) {
      increase_table_size();
    }
    int idx = entry_index(hash, size());
    _entries.at_put(idx, new ValueMapEntry(hash, x, nesting(), entry_at(idx)));
    _entry_count++;

    TRACE_VALUE_NUMBERING(tty->print_cr("Value Numbering: insert %s %c%d  (size %d, entries %d, nesting %d)", x->name(), x->type()->tchar(), x->id(), size(), entry_count(), nesting()));
  }

  return x;
}


#define GENERIC_KILL_VALUE(must_kill_implementation)                                     \
  NOT_PRODUCT(_number_of_kills++);                                                       \
                                                                                         \
  for (int i = size() - 1; i >= 0; i--) {                                                \
    ValueMapEntry* prev_entry = NULL;                                                    \
    for (ValueMapEntry* entry = entry_at(i); entry != NULL; entry = entry->next()) {     \
      Value value = entry->value();                                                      \
                                                                                         \
      must_kill_implementation(must_kill, entry, value)                                  \
                                                                                         \
      if (must_kill) {                                                                   \
        kill_value(value);                                                               \
                                                                                         \
        if (prev_entry == NULL) {                                                        \
          _entries.at_put(i, entry->next());                                             \
          _entry_count--;                                                                \
        } else if (prev_entry->nesting() == nesting()) {                                 \
          prev_entry->set_next(entry->next());                                           \
          _entry_count--;                                                                \
        } else {                                                                         \
          prev_entry = entry;                                                            \
        }                                                                                \
                                                                                         \
        TRACE_VALUE_NUMBERING(tty->print_cr("Value Numbering: killed %s %c%d  (size %d, entries %d, nesting-diff %d)", value->name(), value->type()->tchar(), value->id(), size(), entry_count(), nesting() - entry->nesting()));   \
      } else {                                                                           \
        prev_entry = entry;                                                              \
      }                                                                                  \
    }                                                                                    \
  }                                                                                      \

#define MUST_KILL_MEMORY(must_kill, entry, value)                                        \
  bool must_kill = value->as_LoadField() != NULL || value->as_LoadIndexed() != NULL;

#define MUST_KILL_ARRAY(must_kill, entry, value)                                         \
  bool must_kill = value->as_LoadIndexed() != NULL                                       \
                   && value->type()->tag() == type->tag();

#define MUST_KILL_FIELD(must_kill, entry, value)                                         \
  /* ciField's are not unique; must compare their contents */                            \
  LoadField* lf = value->as_LoadField();                                                 \
  bool must_kill = lf != NULL                                                            \
                   && lf->field()->holder() == field->holder()                           \
                   && (all_offsets || lf->field()->offset() == field->offset());

#define MUST_KILL_EXCEPTION(must_kill, entry, value)                                     \
  assert(entry->nesting() < nesting(), "must not find bigger nesting than current");     \
  bool must_kill = (entry->nesting() == nesting() - 1);


void ValueMap::kill_memory() {
  GENERIC_KILL_VALUE(MUST_KILL_MEMORY);
}

void ValueMap::kill_array(ValueType* type) {
  GENERIC_KILL_VALUE(MUST_KILL_ARRAY);
}

void ValueMap::kill_field(ciField* field, bool all_offsets) {
  GENERIC_KILL_VALUE(MUST_KILL_FIELD);
}

void ValueMap::kill_exception() {
  GENERIC_KILL_VALUE(MUST_KILL_EXCEPTION);
}


void ValueMap::kill_map(ValueMap* map) {
  assert(is_global_value_numbering(), "only for global value numbering");
  _killed_values.set_union(&map->_killed_values);
}

void ValueMap::kill_all() {
  assert(is_local_value_numbering(), "only for local value numbering");
  for (int i = size() - 1; i >= 0; i--) {
    _entries.at_put(i, NULL);
  }
  _entry_count = 0;
}


#ifndef PRODUCT

void ValueMap::print() {
  tty->print_cr("(size %d, entries %d, nesting %d)", size(), entry_count(), nesting());

  int entries = 0;
  for (int i = 0; i < size(); i++) {
    if (entry_at(i) != NULL) {
      tty->print("  %2d: ", i);
      for (ValueMapEntry* entry = entry_at(i); entry != NULL; entry = entry->next()) {
        Value value = entry->value();
        tty->print("%s %c%d (%s%d) -> ", value->name(), value->type()->tchar(), value->id(), is_killed(value) ? "x" : "", entry->nesting());
        entries++;
      }
      tty->print_cr("NULL");
    }
  }

  _killed_values.print();
  assert(entry_count() == entries, "entry_count incorrect");
}

void ValueMap::reset_statistics() {
  _number_of_finds = 0;
  _number_of_hits = 0;
  _number_of_kills = 0;
}

void ValueMap::print_statistics() {
  float hit_rate = 0;
  if (_number_of_finds != 0) {
    hit_rate = (float)_number_of_hits / _number_of_finds;
  }

  tty->print_cr("finds:%3d  hits:%3d   kills:%3d  hit rate: %1.4f", _number_of_finds, _number_of_hits, _number_of_kills, hit_rate);
}

#endif



class ShortLoopOptimizer : public ValueNumberingVisitor {
 private:
  GlobalValueNumbering* _gvn;
  BlockList             _loop_blocks;
  bool                  _too_complicated_loop;

  // simplified access to methods of GlobalValueNumbering
  ValueMap* current_map()                        { return _gvn->current_map(); }
  ValueMap* value_map_of(BlockBegin* block)      { return _gvn->value_map_of(block); }

  // implementation for abstract methods of ValueNumberingVisitor
  void      kill_memory()                                 { _too_complicated_loop = true; }
  void      kill_field(ciField* field, bool all_offsets)  { current_map()->kill_field(field, all_offsets); };
  void      kill_array(ValueType* type)                   { current_map()->kill_array(type); };

 public:
  ShortLoopOptimizer(GlobalValueNumbering* gvn)
    : _gvn(gvn)
    , _loop_blocks(ValueMapMaxLoopSize)
    , _too_complicated_loop(false)
  {
  }

  bool process(BlockBegin* loop_header);
};


bool ShortLoopOptimizer::process(BlockBegin* loop_header) {
  TRACE_VALUE_NUMBERING(tty->print_cr("** loop header block"));

  _too_complicated_loop = false;
  _loop_blocks.clear();
  _loop_blocks.append(loop_header);

  for (int i = 0; i < _loop_blocks.length(); i++) {
    BlockBegin* block = _loop_blocks.at(i);
    TRACE_VALUE_NUMBERING(tty->print_cr("processing loop block B%d", block->block_id()));

    if (block->is_set(BlockBegin::exception_entry_flag)) {
      // this would be too complicated
      return false;
    }

    // add predecessors to worklist
    for (int j = block->number_of_preds() - 1; j >= 0; j--) {
      BlockBegin* pred = block->pred_at(j);

      ValueMap* pred_map = value_map_of(pred);
      if (pred_map != NULL) {
        current_map()->kill_map(pred_map);
      } else if (!_loop_blocks.contains(pred)) {
        if (_loop_blocks.length() >= ValueMapMaxLoopSize) {
          return false;
        }
        _loop_blocks.append(pred);
      }
    }

    // use the instruction visitor for killing values
    for (Value instr = block->next(); instr != NULL; instr = instr->next()) {
      instr->visit(this);
      if (_too_complicated_loop) {
        return false;
      }
    }
  }

  TRACE_VALUE_NUMBERING(tty->print_cr("** loop successfully optimized"));
  return true;
}


GlobalValueNumbering::GlobalValueNumbering(IR* ir)
  : _current_map(NULL)
  , _value_maps(ir->linear_scan_order()->length(), NULL)
{
  TRACE_VALUE_NUMBERING(tty->print_cr("****** start of global value numbering"));

  ShortLoopOptimizer short_loop_optimizer(this);
  int subst_count = 0;

  BlockList* blocks = ir->linear_scan_order();
  int num_blocks = blocks->length();

  BlockBegin* start_block = blocks->at(0);
  assert(start_block == ir->start() && start_block->number_of_preds() == 0 && start_block->dominator() == NULL, "must be start block");
  assert(start_block->next()->as_Base() != NULL && start_block->next()->next() == NULL, "start block must not have instructions");

  // initial, empty value map with nesting 0
  set_value_map_of(start_block, new ValueMap());

  for (int i = 1; i < num_blocks; i++) {
    BlockBegin* block = blocks->at(i);
    TRACE_VALUE_NUMBERING(tty->print_cr("**** processing block B%d", block->block_id()));

    int num_preds = block->number_of_preds();
    assert(num_preds > 0, "block must have predecessors");

    BlockBegin* dominator = block->dominator();
    assert(dominator != NULL, "dominator must exist");
    assert(value_map_of(dominator) != NULL, "value map of dominator must exist");

    // create new value map with increased nesting
    _current_map = new ValueMap(value_map_of(dominator));

    if (num_preds == 1) {
      assert(dominator == block->pred_at(0), "dominator must be equal to predecessor");
      // nothing to do here

    } else if (block->is_set(BlockBegin::linear_scan_loop_header_flag)) {
      // block has incoming backward branches -> try to optimize short loops
      if (!short_loop_optimizer.process(block)) {
        // loop is too complicated, so kill all memory loads because there might be
        // stores to them in the loop
        current_map()->kill_memory();
      }

    } else {
      // only incoming forward branches that are already processed
      for (int j = 0; j < num_preds; j++) {
        BlockBegin* pred = block->pred_at(j);
        ValueMap* pred_map = value_map_of(pred);

        if (pred_map != NULL) {
          // propagate killed values of the predecessor to this block
          current_map()->kill_map(value_map_of(pred));
        } else {
          // kill all memory loads because predecessor not yet processed
          // (this can happen with non-natural loops and OSR-compiles)
          current_map()->kill_memory();
        }
      }
    }

    if (block->is_set(BlockBegin::exception_entry_flag)) {
      current_map()->kill_exception();
    }

    TRACE_VALUE_NUMBERING(tty->print("value map before processing block: "); current_map()->print());

    // visit all instructions of this block
    for (Value instr = block->next(); instr != NULL; instr = instr->next()) {
      assert(!instr->has_subst(), "substitution already set");

      // check if instruction kills any values
      instr->visit(this);

      if (instr->hash() != 0) {
        Value f = current_map()->find_insert(instr);
        if (f != instr) {
          assert(!f->has_subst(), "can't have a substitution");
          instr->set_subst(f);
          subst_count++;
        }
      }
    }

    // remember value map for successors
    set_value_map_of(block, current_map());
  }

  if (subst_count != 0) {
    SubstitutionResolver resolver(ir);
  }

  TRACE_VALUE_NUMBERING(tty->print("****** end of global value numbering. "); ValueMap::print_statistics());
}
