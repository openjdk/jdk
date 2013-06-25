/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "runtime/os.hpp"
#include "services/memReporter.hpp"
#include "services/memPtrArray.hpp"
#include "services/memTracker.hpp"

const char* BaselineOutputer::memory_unit(size_t scale) {
  switch(scale) {
    case K: return "KB";
    case M: return "MB";
    case G: return "GB";
  }
  ShouldNotReachHere();
  return NULL;
}


void BaselineReporter::report_baseline(const MemBaseline& baseline, bool summary_only) {
  assert(MemTracker::is_on(), "Native memory tracking is off");
  _outputer.start(scale());
  _outputer.total_usage(
    amount_in_current_scale(baseline.total_malloc_amount() + baseline.total_reserved_amount()),
    amount_in_current_scale(baseline.total_malloc_amount() + baseline.total_committed_amount()));

  _outputer.num_of_classes(baseline.number_of_classes());
  _outputer.num_of_threads(baseline.number_of_threads());

  report_summaries(baseline);
  if (!summary_only && MemTracker::track_callsite()) {
    report_virtual_memory_map(baseline);
    report_callsites(baseline);
  }
  _outputer.done();
}

void BaselineReporter::report_summaries(const MemBaseline& baseline) {
  _outputer.start_category_summary();
  MEMFLAGS type;

  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    type = MemBaseline::MemType2NameMap[index]._flag;
    _outputer.category_summary(type,
      amount_in_current_scale(baseline.reserved_amount(type)),
      amount_in_current_scale(baseline.committed_amount(type)),
      amount_in_current_scale(baseline.malloc_amount(type)),
      baseline.malloc_count(type),
      amount_in_current_scale(baseline.arena_amount(type)),
      baseline.arena_count(type));
  }

  _outputer.done_category_summary();
}

void BaselineReporter::report_virtual_memory_map(const MemBaseline& baseline) {
  _outputer.start_virtual_memory_map();
  MemBaseline* pBL = const_cast<MemBaseline*>(&baseline);
  MemPointerArrayIteratorImpl itr = MemPointerArrayIteratorImpl(pBL->_vm_map);
  VMMemRegionEx* rgn = (VMMemRegionEx*)itr.current();
  while (rgn != NULL) {
    if (rgn->is_reserved_region()) {
      _outputer.reserved_memory_region(FLAGS_TO_MEMORY_TYPE(rgn->flags()),
        rgn->base(), rgn->base() + rgn->size(), amount_in_current_scale(rgn->size()), rgn->pc());
    } else {
      _outputer.committed_memory_region(rgn->base(), rgn->base() + rgn->size(),
        amount_in_current_scale(rgn->size()), rgn->pc());
    }
    rgn = (VMMemRegionEx*)itr.next();
  }

  _outputer.done_virtual_memory_map();
}

void BaselineReporter::report_callsites(const MemBaseline& baseline) {
  _outputer.start_callsite();
  MemBaseline* pBL = const_cast<MemBaseline*>(&baseline);

  pBL->_malloc_cs->sort((FN_SORT)MemBaseline::bl_malloc_sort_by_size);
  pBL->_vm_cs->sort((FN_SORT)MemBaseline::bl_vm_sort_by_size);

  // walk malloc callsites
  MemPointerArrayIteratorImpl malloc_itr(pBL->_malloc_cs);
  MallocCallsitePointer*      malloc_callsite =
                  (MallocCallsitePointer*)malloc_itr.current();
  while (malloc_callsite != NULL) {
    _outputer.malloc_callsite(malloc_callsite->addr(),
        amount_in_current_scale(malloc_callsite->amount()), malloc_callsite->count());
    malloc_callsite = (MallocCallsitePointer*)malloc_itr.next();
  }

  // walk virtual memory callsite
  MemPointerArrayIteratorImpl vm_itr(pBL->_vm_cs);
  VMCallsitePointer*          vm_callsite = (VMCallsitePointer*)vm_itr.current();
  while (vm_callsite != NULL) {
    _outputer.virtual_memory_callsite(vm_callsite->addr(),
      amount_in_current_scale(vm_callsite->reserved_amount()),
      amount_in_current_scale(vm_callsite->committed_amount()));
    vm_callsite = (VMCallsitePointer*)vm_itr.next();
  }
  pBL->_malloc_cs->sort((FN_SORT)MemBaseline::bl_malloc_sort_by_pc);
  pBL->_vm_cs->sort((FN_SORT)MemBaseline::bl_vm_sort_by_pc);
  _outputer.done_callsite();
}

void BaselineReporter::diff_baselines(const MemBaseline& cur, const MemBaseline& prev,
  bool summary_only) {
  assert(MemTracker::is_on(), "Native memory tracking is off");
  _outputer.start(scale());
  size_t total_reserved = cur.total_malloc_amount() + cur.total_reserved_amount();
  size_t total_committed = cur.total_malloc_amount() + cur.total_committed_amount();

  _outputer.diff_total_usage(
    amount_in_current_scale(total_reserved), amount_in_current_scale(total_committed),
    diff_in_current_scale(total_reserved,  (prev.total_malloc_amount() + prev.total_reserved_amount())),
    diff_in_current_scale(total_committed, (prev.total_committed_amount() + prev.total_malloc_amount())));

  _outputer.diff_num_of_classes(cur.number_of_classes(),
       diff(cur.number_of_classes(), prev.number_of_classes()));
  _outputer.diff_num_of_threads(cur.number_of_threads(),
       diff(cur.number_of_threads(), prev.number_of_threads()));

  diff_summaries(cur, prev);
  if (!summary_only && MemTracker::track_callsite()) {
    diff_callsites(cur, prev);
  }
  _outputer.done();
}

void BaselineReporter::diff_summaries(const MemBaseline& cur, const MemBaseline& prev) {
  _outputer.start_category_summary();
  MEMFLAGS type;

  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    type = MemBaseline::MemType2NameMap[index]._flag;
    _outputer.diff_category_summary(type,
      amount_in_current_scale(cur.reserved_amount(type)),
      amount_in_current_scale(cur.committed_amount(type)),
      amount_in_current_scale(cur.malloc_amount(type)),
      cur.malloc_count(type),
      amount_in_current_scale(cur.arena_amount(type)),
      cur.arena_count(type),
      diff_in_current_scale(cur.reserved_amount(type), prev.reserved_amount(type)),
      diff_in_current_scale(cur.committed_amount(type), prev.committed_amount(type)),
      diff_in_current_scale(cur.malloc_amount(type), prev.malloc_amount(type)),
      diff(cur.malloc_count(type), prev.malloc_count(type)),
      diff_in_current_scale(cur.arena_amount(type), prev.arena_amount(type)),
      diff(cur.arena_count(type), prev.arena_count(type)));
  }

  _outputer.done_category_summary();
}

void BaselineReporter::diff_callsites(const MemBaseline& cur, const MemBaseline& prev) {
  _outputer.start_callsite();
  MemBaseline* pBL_cur = const_cast<MemBaseline*>(&cur);
  MemBaseline* pBL_prev = const_cast<MemBaseline*>(&prev);

  // walk malloc callsites
  MemPointerArrayIteratorImpl cur_malloc_itr(pBL_cur->_malloc_cs);
  MemPointerArrayIteratorImpl prev_malloc_itr(pBL_prev->_malloc_cs);

  MallocCallsitePointer*      cur_malloc_callsite =
                  (MallocCallsitePointer*)cur_malloc_itr.current();
  MallocCallsitePointer*      prev_malloc_callsite =
                  (MallocCallsitePointer*)prev_malloc_itr.current();

  while (cur_malloc_callsite != NULL || prev_malloc_callsite != NULL) {
    if (prev_malloc_callsite == NULL ||
        cur_malloc_callsite->addr() < prev_malloc_callsite->addr()) {
      // this is a new callsite
      _outputer.diff_malloc_callsite(cur_malloc_callsite->addr(),
        amount_in_current_scale(cur_malloc_callsite->amount()),
        cur_malloc_callsite->count(),
        diff_in_current_scale(cur_malloc_callsite->amount(), 0),
        diff(cur_malloc_callsite->count(), 0));
      cur_malloc_callsite = (MallocCallsitePointer*)cur_malloc_itr.next();
    } else if (cur_malloc_callsite == NULL ||
               cur_malloc_callsite->addr() > prev_malloc_callsite->addr()) {
      // this callsite is already gone
      _outputer.diff_malloc_callsite(prev_malloc_callsite->addr(),
        amount_in_current_scale(0), 0,
        diff_in_current_scale(0, prev_malloc_callsite->amount()),
        diff(0, prev_malloc_callsite->count()));
      prev_malloc_callsite = (MallocCallsitePointer*)prev_malloc_itr.next();
    } else {  // the same callsite
      _outputer.diff_malloc_callsite(cur_malloc_callsite->addr(),
        amount_in_current_scale(cur_malloc_callsite->amount()),
        cur_malloc_callsite->count(),
        diff_in_current_scale(cur_malloc_callsite->amount(), prev_malloc_callsite->amount()),
        diff(cur_malloc_callsite->count(), prev_malloc_callsite->count()));
      cur_malloc_callsite = (MallocCallsitePointer*)cur_malloc_itr.next();
      prev_malloc_callsite = (MallocCallsitePointer*)prev_malloc_itr.next();
    }
  }

  // walk virtual memory callsite
  MemPointerArrayIteratorImpl cur_vm_itr(pBL_cur->_vm_cs);
  MemPointerArrayIteratorImpl prev_vm_itr(pBL_prev->_vm_cs);
  VMCallsitePointer*          cur_vm_callsite = (VMCallsitePointer*)cur_vm_itr.current();
  VMCallsitePointer*          prev_vm_callsite = (VMCallsitePointer*)prev_vm_itr.current();
  while (cur_vm_callsite != NULL || prev_vm_callsite != NULL) {
    if (prev_vm_callsite == NULL || cur_vm_callsite->addr() < prev_vm_callsite->addr()) {
      // this is a new callsite
      _outputer.diff_virtual_memory_callsite(cur_vm_callsite->addr(),
        amount_in_current_scale(cur_vm_callsite->reserved_amount()),
        amount_in_current_scale(cur_vm_callsite->committed_amount()),
        diff_in_current_scale(cur_vm_callsite->reserved_amount(), 0),
        diff_in_current_scale(cur_vm_callsite->committed_amount(), 0));
      cur_vm_callsite = (VMCallsitePointer*)cur_vm_itr.next();
    } else if (cur_vm_callsite == NULL || cur_vm_callsite->addr() > prev_vm_callsite->addr()) {
      // this callsite is already gone
      _outputer.diff_virtual_memory_callsite(prev_vm_callsite->addr(),
        amount_in_current_scale(0),
        amount_in_current_scale(0),
        diff_in_current_scale(0, prev_vm_callsite->reserved_amount()),
        diff_in_current_scale(0, prev_vm_callsite->committed_amount()));
      prev_vm_callsite = (VMCallsitePointer*)prev_vm_itr.next();
    } else { // the same callsite
      _outputer.diff_virtual_memory_callsite(cur_vm_callsite->addr(),
        amount_in_current_scale(cur_vm_callsite->reserved_amount()),
        amount_in_current_scale(cur_vm_callsite->committed_amount()),
        diff_in_current_scale(cur_vm_callsite->reserved_amount(), prev_vm_callsite->reserved_amount()),
        diff_in_current_scale(cur_vm_callsite->committed_amount(), prev_vm_callsite->committed_amount()));
      cur_vm_callsite  = (VMCallsitePointer*)cur_vm_itr.next();
      prev_vm_callsite = (VMCallsitePointer*)prev_vm_itr.next();
    }
  }

  _outputer.done_callsite();
}

size_t BaselineReporter::amount_in_current_scale(size_t amt) const {
  return (size_t)(((float)amt/(float)_scale) + 0.5);
}

int BaselineReporter::diff_in_current_scale(size_t value1, size_t value2) const {
  return (int)(((float)value1 - (float)value2)/((float)_scale) + 0.5);
}

int BaselineReporter::diff(size_t value1, size_t value2) const {
  return ((int)value1 - (int)value2);
}

void BaselineTTYOutputer::start(size_t scale, bool report_diff) {
  _scale = scale;
  _output->print_cr(" ");
  _output->print_cr("Native Memory Tracking:");
  _output->print_cr(" ");
}

void BaselineTTYOutputer::done() {

}

void BaselineTTYOutputer::total_usage(size_t total_reserved, size_t total_committed) {
  const char* unit = memory_unit(_scale);
  _output->print_cr("Total:  reserved=%d%s,  committed=%d%s",
    total_reserved, unit, total_committed, unit);
}

void BaselineTTYOutputer::start_category_summary() {
  _output->print_cr(" ");
}

/**
 * report a summary of memory type
 */
void BaselineTTYOutputer::category_summary(MEMFLAGS type,
  size_t reserved_amt, size_t committed_amt, size_t malloc_amt,
  size_t malloc_count, size_t arena_amt, size_t arena_count) {

  // we report mtThreadStack under mtThread category
  if (type == mtThreadStack) {
    assert(malloc_amt == 0 && malloc_count == 0 && arena_amt == 0,
      "Just check");
    _thread_stack_reserved = reserved_amt;
    _thread_stack_committed = committed_amt;
  } else {
    const char* unit = memory_unit(_scale);
    size_t total_reserved = (reserved_amt + malloc_amt + arena_amt);
    size_t total_committed = (committed_amt + malloc_amt + arena_amt);
    if (type == mtThread) {
      total_reserved += _thread_stack_reserved;
      total_committed += _thread_stack_committed;
    }

    if (total_reserved > 0) {
      _output->print_cr("-%26s (reserved=%d%s, committed=%d%s)",
        MemBaseline::type2name(type), total_reserved, unit,
        total_committed, unit);

      if (type == mtClass) {
        _output->print_cr("%27s (classes #%d)", " ", _num_of_classes);
      } else if (type == mtThread) {
        _output->print_cr("%27s (thread #%d)", " ", _num_of_threads);
        _output->print_cr("%27s (stack: reserved=%d%s, committed=%d%s)", " ",
          _thread_stack_reserved, unit, _thread_stack_committed, unit);
      }

      if (malloc_amt > 0) {
        if (type != mtChunk) {
          _output->print_cr("%27s (malloc=%d%s, #%d)", " ", malloc_amt, unit,
            malloc_count);
        } else {
          _output->print_cr("%27s (malloc=%d%s)", " ", malloc_amt, unit);
        }
      }

      if (reserved_amt > 0) {
        _output->print_cr("%27s (mmap: reserved=%d%s, committed=%d%s)",
          " ", reserved_amt, unit, committed_amt, unit);
      }

      if (arena_amt > 0) {
        _output->print_cr("%27s (arena=%d%s, #%d)", " ", arena_amt, unit, arena_count);
      }

      _output->print_cr(" ");
    }
  }
}

void BaselineTTYOutputer::done_category_summary() {
  _output->print_cr(" ");
}


void BaselineTTYOutputer::start_virtual_memory_map() {
  _output->print_cr("Virtual memory map:");
}

void BaselineTTYOutputer::reserved_memory_region(MEMFLAGS type, address base, address end,
                                                 size_t size, address pc) {
  const char* unit = memory_unit(_scale);
  char buf[128];
  int  offset;
  _output->print_cr(" ");
  _output->print_cr("[" PTR_FORMAT " - " PTR_FORMAT "] reserved %d%s for %s", base, end, size, unit,
            MemBaseline::type2name(type));
  if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
      _output->print_cr("\t\tfrom [%s+0x%x]", buf, offset);
  }
}

void BaselineTTYOutputer::committed_memory_region(address base, address end, size_t size, address pc) {
  const char* unit = memory_unit(_scale);
  char buf[128];
  int  offset;
  _output->print("\t[" PTR_FORMAT " - " PTR_FORMAT "] committed %d%s", base, end, size, unit);
  if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
      _output->print_cr(" from [%s+0x%x]", buf, offset);
  }
}

void BaselineTTYOutputer::done_virtual_memory_map() {
  _output->print_cr(" ");
}



void BaselineTTYOutputer::start_callsite() {
  _output->print_cr("Details:");
  _output->print_cr(" ");
}

void BaselineTTYOutputer::done_callsite() {
  _output->print_cr(" ");
}

void BaselineTTYOutputer::malloc_callsite(address pc, size_t malloc_amt,
  size_t malloc_count) {
  if (malloc_amt > 0) {
    const char* unit = memory_unit(_scale);
    char buf[128];
    int  offset;
    if (pc == 0) {
      _output->print("[BOOTSTRAP]%18s", " ");
    } else if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
      _output->print_cr("[" PTR_FORMAT "] %s+0x%x", pc, buf, offset);
      _output->print("%28s", " ");
    } else {
      _output->print("[" PTR_FORMAT "]%18s", pc, " ");
    }

    _output->print_cr("(malloc=%d%s #%d)", malloc_amt, unit, malloc_count);
    _output->print_cr(" ");
  }
}

void BaselineTTYOutputer::virtual_memory_callsite(address pc, size_t reserved_amt,
  size_t committed_amt) {
  if (reserved_amt > 0) {
    const char* unit = memory_unit(_scale);
    char buf[128];
    int  offset;
    if (pc == 0) {
      _output->print("[BOOTSTRAP]%18s", " ");
    } else if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
      _output->print_cr("[" PTR_FORMAT "] %s+0x%x", pc, buf, offset);
      _output->print("%28s", " ");
    } else {
      _output->print("[" PTR_FORMAT "]%18s", pc, " ");
    }

    _output->print_cr("(mmap: reserved=%d%s, committed=%d%s)",
      reserved_amt, unit, committed_amt, unit);
    _output->print_cr(" ");
  }
}

void BaselineTTYOutputer::diff_total_usage(size_t total_reserved,
  size_t total_committed, int reserved_diff, int committed_diff) {
  const char* unit = memory_unit(_scale);
  _output->print_cr("Total:  reserved=%d%s  %+d%s, committed=%d%s %+d%s",
    total_reserved, unit, reserved_diff, unit, total_committed, unit,
    committed_diff, unit);
}

void BaselineTTYOutputer::diff_category_summary(MEMFLAGS type,
  size_t cur_reserved_amt, size_t cur_committed_amt,
  size_t cur_malloc_amt, size_t cur_malloc_count,
  size_t cur_arena_amt, size_t cur_arena_count,
  int reserved_diff, int committed_diff, int malloc_diff,
  int malloc_count_diff, int arena_diff, int arena_count_diff) {

  if (type == mtThreadStack) {
    assert(cur_malloc_amt == 0 && cur_malloc_count == 0 &&
      cur_arena_amt == 0, "Just check");
    _thread_stack_reserved = cur_reserved_amt;
    _thread_stack_committed = cur_committed_amt;
    _thread_stack_reserved_diff = reserved_diff;
    _thread_stack_committed_diff = committed_diff;
  } else {
    const char* unit = memory_unit(_scale);
    size_t total_reserved = (cur_reserved_amt + cur_malloc_amt + cur_arena_amt);
    // nothing to report in this category
    if (total_reserved == 0) {
      return;
    }
    int    diff_reserved = (reserved_diff + malloc_diff + arena_diff);

    // category summary
    _output->print("-%26s (reserved=%d%s", MemBaseline::type2name(type),
      total_reserved, unit);

    if (diff_reserved != 0) {
      _output->print(" %+d%s", diff_reserved, unit);
    }

    size_t total_committed = cur_committed_amt + cur_malloc_amt + cur_arena_amt;
    _output->print(", committed=%d%s", total_committed, unit);

    int total_committed_diff = committed_diff + malloc_diff + arena_diff;
    if (total_committed_diff != 0) {
      _output->print(" %+d%s", total_committed_diff, unit);
    }

    _output->print_cr(")");

    // special cases
    if (type == mtClass) {
      _output->print("%27s (classes #%d", " ", _num_of_classes);
      if (_num_of_classes_diff != 0) {
        _output->print(" %+d", _num_of_classes_diff);
      }
      _output->print_cr(")");
    } else if (type == mtThread) {
      // thread count
      _output->print("%27s (thread #%d", " ", _num_of_threads);
      if (_num_of_threads_diff != 0) {
        _output->print_cr(" %+d)", _num_of_threads_diff);
      } else {
        _output->print_cr(")");
      }
      _output->print("%27s (stack: reserved=%d%s", " ", _thread_stack_reserved, unit);
      if (_thread_stack_reserved_diff != 0) {
        _output->print(" %+d%s", _thread_stack_reserved_diff, unit);
      }

      _output->print(", committed=%d%s", _thread_stack_committed, unit);
      if (_thread_stack_committed_diff != 0) {
        _output->print(" %+d%s",_thread_stack_committed_diff, unit);
      }

      _output->print_cr(")");
    }

    // malloc'd memory
    if (cur_malloc_amt > 0) {
      _output->print("%27s (malloc=%d%s", " ", cur_malloc_amt, unit);
      if (malloc_diff != 0) {
        _output->print(" %+d%s", malloc_diff, unit);
      }
      if (type != mtChunk) {
        _output->print(", #%d", cur_malloc_count);
        if (malloc_count_diff) {
          _output->print(" %+d", malloc_count_diff);
        }
      }
      _output->print_cr(")");
    }

    // mmap'd memory
    if (cur_reserved_amt > 0) {
      _output->print("%27s (mmap: reserved=%d%s", " ", cur_reserved_amt, unit);
      if (reserved_diff != 0) {
        _output->print(" %+d%s", reserved_diff, unit);
      }

      _output->print(", committed=%d%s", cur_committed_amt, unit);
      if (committed_diff != 0) {
        _output->print(" %+d%s", committed_diff, unit);
      }
      _output->print_cr(")");
    }

    // arena memory
    if (cur_arena_amt > 0) {
      _output->print("%27s (arena=%d%s", " ", cur_arena_amt, unit);
      if (arena_diff != 0) {
        _output->print(" %+d%s", arena_diff, unit);
      }
      _output->print(", #%d", cur_arena_count);
      if (arena_count_diff != 0) {
        _output->print(" %+d", arena_count_diff);
      }
      _output->print_cr(")");
    }

    _output->print_cr(" ");
  }
}

void BaselineTTYOutputer::diff_malloc_callsite(address pc,
    size_t cur_malloc_amt, size_t cur_malloc_count,
    int malloc_diff, int malloc_count_diff) {
  if (malloc_diff != 0) {
    const char* unit = memory_unit(_scale);
    char buf[128];
    int  offset;
    if (pc == 0) {
      _output->print_cr("[BOOTSTRAP]%18s", " ");
    } else {
      if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
        _output->print_cr("[" PTR_FORMAT "] %s+0x%x", pc, buf, offset);
        _output->print("%28s", " ");
      } else {
        _output->print("[" PTR_FORMAT "]%18s", pc, " ");
      }
    }

    _output->print("(malloc=%d%s", cur_malloc_amt, unit);
    if (malloc_diff != 0) {
      _output->print(" %+d%s", malloc_diff, unit);
    }
    _output->print(", #%d", cur_malloc_count);
    if (malloc_count_diff != 0) {
      _output->print(" %+d", malloc_count_diff);
    }
    _output->print_cr(")");
    _output->print_cr(" ");
  }
}

void BaselineTTYOutputer::diff_virtual_memory_callsite(address pc,
    size_t cur_reserved_amt, size_t cur_committed_amt,
    int reserved_diff, int committed_diff) {
  if (reserved_diff != 0 || committed_diff != 0) {
    const char* unit = memory_unit(_scale);
    char buf[64];
    int  offset;
    if (pc == 0) {
      _output->print_cr("[BOOSTRAP]%18s", " ");
    } else {
      if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
        _output->print_cr("[" PTR_FORMAT "] %s+0x%x", pc, buf, offset);
        _output->print("%28s", " ");
      } else {
        _output->print("[" PTR_FORMAT "]%18s", pc, " ");
      }
    }

    _output->print("(mmap: reserved=%d%s", cur_reserved_amt, unit);
    if (reserved_diff != 0) {
      _output->print(" %+d%s", reserved_diff, unit);
    }
    _output->print(", committed=%d%s", cur_committed_amt, unit);
    if (committed_diff != 0) {
      _output->print(" %+d%s", committed_diff, unit);
    }
    _output->print_cr(")");
    _output->print_cr(" ");
  }
}
