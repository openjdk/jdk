/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_parNewGeneration.cpp.incl"

#ifdef _MSC_VER
#pragma warning( push )
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif
ParScanThreadState::ParScanThreadState(Space* to_space_,
                                       ParNewGeneration* gen_,
                                       Generation* old_gen_,
                                       int thread_num_,
                                       ObjToScanQueueSet* work_queue_set_,
                                       size_t desired_plab_sz_,
                                       ParallelTaskTerminator& term_) :
  _to_space(to_space_), _old_gen(old_gen_), _thread_num(thread_num_),
  _work_queue(work_queue_set_->queue(thread_num_)), _to_space_full(false),
  _ageTable(false), // false ==> not the global age table, no perf data.
  _to_space_alloc_buffer(desired_plab_sz_),
  _to_space_closure(gen_, this), _old_gen_closure(gen_, this),
  _to_space_root_closure(gen_, this), _old_gen_root_closure(gen_, this),
  _older_gen_closure(gen_, this),
  _evacuate_followers(this, &_to_space_closure, &_old_gen_closure,
                      &_to_space_root_closure, gen_, &_old_gen_root_closure,
                      work_queue_set_, &term_),
  _is_alive_closure(gen_), _scan_weak_ref_closure(gen_, this),
  _keep_alive_closure(&_scan_weak_ref_closure),
  _pushes(0), _pops(0), _steals(0), _steal_attempts(0), _term_attempts(0),
  _strong_roots_time(0.0), _term_time(0.0)
{
  _survivor_chunk_array =
    (ChunkArray*) old_gen()->get_data_recorder(thread_num());
  _hash_seed = 17;  // Might want to take time-based random value.
  _start = os::elapsedTime();
  _old_gen_closure.set_generation(old_gen_);
  _old_gen_root_closure.set_generation(old_gen_);
}
#ifdef _MSC_VER
#pragma warning( pop )
#endif

void ParScanThreadState::record_survivor_plab(HeapWord* plab_start,
                                              size_t plab_word_size) {
  ChunkArray* sca = survivor_chunk_array();
  if (sca != NULL) {
    // A non-null SCA implies that we want the PLAB data recorded.
    sca->record_sample(plab_start, plab_word_size);
  }
}

bool ParScanThreadState::should_be_partially_scanned(oop new_obj, oop old_obj) const {
  return new_obj->is_objArray() &&
         arrayOop(new_obj)->length() > ParGCArrayScanChunk &&
         new_obj != old_obj;
}

void ParScanThreadState::scan_partial_array_and_push_remainder(oop old) {
  assert(old->is_objArray(), "must be obj array");
  assert(old->is_forwarded(), "must be forwarded");
  assert(Universe::heap()->is_in_reserved(old), "must be in heap.");
  assert(!_old_gen->is_in(old), "must be in young generation.");

  objArrayOop obj = objArrayOop(old->forwardee());
  // Process ParGCArrayScanChunk elements now
  // and push the remainder back onto queue
  int start     = arrayOop(old)->length();
  int end       = obj->length();
  int remainder = end - start;
  assert(start <= end, "just checking");
  if (remainder > 2 * ParGCArrayScanChunk) {
    // Test above combines last partial chunk with a full chunk
    end = start + ParGCArrayScanChunk;
    arrayOop(old)->set_length(end);
    // Push remainder.
    bool ok = work_queue()->push(old);
    assert(ok, "just popped, push must be okay");
    note_push();
  } else {
    // Restore length so that it can be used if there
    // is a promotion failure and forwarding pointers
    // must be removed.
    arrayOop(old)->set_length(end);
  }
  // process our set of indices (include header in first chunk)
  oop* start_addr = start == 0 ? (oop*)obj : obj->obj_at_addr(start);
  oop* end_addr   = obj->base() + end; // obj_at_addr(end) asserts end < length
  MemRegion mr((HeapWord*)start_addr, (HeapWord*)end_addr);
  if ((HeapWord *)obj < young_old_boundary()) {
    // object is in to_space
    obj->oop_iterate(&_to_space_closure, mr);
  } else {
    // object is in old generation
    obj->oop_iterate(&_old_gen_closure, mr);
  }
}


void ParScanThreadState::trim_queues(int max_size) {
  ObjToScanQueue* queue = work_queue();
  while (queue->size() > (juint)max_size) {
    oop obj_to_scan;
    if (queue->pop_local(obj_to_scan)) {
      note_pop();

      if ((HeapWord *)obj_to_scan < young_old_boundary()) {
        if (obj_to_scan->is_objArray() &&
            obj_to_scan->is_forwarded() &&
            obj_to_scan->forwardee() != obj_to_scan) {
          scan_partial_array_and_push_remainder(obj_to_scan);
        } else {
          // object is in to_space
          obj_to_scan->oop_iterate(&_to_space_closure);
        }
      } else {
        // object is in old generation
        obj_to_scan->oop_iterate(&_old_gen_closure);
      }
    }
  }
}

HeapWord* ParScanThreadState::alloc_in_to_space_slow(size_t word_sz) {

  // Otherwise, if the object is small enough, try to reallocate the
  // buffer.
  HeapWord* obj = NULL;
  if (!_to_space_full) {
    ParGCAllocBuffer* const plab = to_space_alloc_buffer();
    Space*            const sp   = to_space();
    if (word_sz * 100 <
        ParallelGCBufferWastePct * plab->word_sz()) {
      // Is small enough; abandon this buffer and start a new one.
      plab->retire(false, false);
      size_t buf_size = plab->word_sz();
      HeapWord* buf_space = sp->par_allocate(buf_size);
      if (buf_space == NULL) {
        const size_t min_bytes =
          ParGCAllocBuffer::min_size() << LogHeapWordSize;
        size_t free_bytes = sp->free();
        while(buf_space == NULL && free_bytes >= min_bytes) {
          buf_size = free_bytes >> LogHeapWordSize;
          assert(buf_size == (size_t)align_object_size(buf_size),
                 "Invariant");
          buf_space  = sp->par_allocate(buf_size);
          free_bytes = sp->free();
        }
      }
      if (buf_space != NULL) {
        plab->set_word_size(buf_size);
        plab->set_buf(buf_space);
        record_survivor_plab(buf_space, buf_size);
        obj = plab->allocate(word_sz);
        // Note that we cannot compare buf_size < word_sz below
        // because of AlignmentReserve (see ParGCAllocBuffer::allocate()).
        assert(obj != NULL || plab->words_remaining() < word_sz,
               "Else should have been able to allocate");
        // It's conceivable that we may be able to use the
        // buffer we just grabbed for subsequent small requests
        // even if not for this one.
      } else {
        // We're used up.
        _to_space_full = true;
      }

    } else {
      // Too large; allocate the object individually.
      obj = sp->par_allocate(word_sz);
    }
  }
  return obj;
}


void ParScanThreadState::undo_alloc_in_to_space(HeapWord* obj,
                                                size_t word_sz) {
  // Is the alloc in the current alloc buffer?
  if (to_space_alloc_buffer()->contains(obj)) {
    assert(to_space_alloc_buffer()->contains(obj + word_sz - 1),
           "Should contain whole object.");
    to_space_alloc_buffer()->undo_allocation(obj, word_sz);
  } else {
    SharedHeap::fill_region_with_object(MemRegion(obj, word_sz));
  }
}

class ParScanThreadStateSet: private ResourceArray {
public:
  // Initializes states for the specified number of threads;
  ParScanThreadStateSet(int                     num_threads,
                        Space&                  to_space,
                        ParNewGeneration&       gen,
                        Generation&             old_gen,
                        ObjToScanQueueSet&      queue_set,
                        size_t                  desired_plab_sz,
                        ParallelTaskTerminator& term);
  inline ParScanThreadState& thread_sate(int i);
  int pushes() { return _pushes; }
  int pops()   { return _pops; }
  int steals() { return _steals; }
  void reset();
  void flush();
private:
  ParallelTaskTerminator& _term;
  ParNewGeneration&       _gen;
  Generation&             _next_gen;
  // staticstics
  int _pushes;
  int _pops;
  int _steals;
};


ParScanThreadStateSet::ParScanThreadStateSet(
  int num_threads, Space& to_space, ParNewGeneration& gen,
  Generation& old_gen, ObjToScanQueueSet& queue_set,
  size_t desired_plab_sz, ParallelTaskTerminator& term)
  : ResourceArray(sizeof(ParScanThreadState), num_threads),
    _gen(gen), _next_gen(old_gen), _term(term),
    _pushes(0), _pops(0), _steals(0)
{
  assert(num_threads > 0, "sanity check!");
  // Initialize states.
  for (int i = 0; i < num_threads; ++i) {
    new ((ParScanThreadState*)_data + i)
        ParScanThreadState(&to_space, &gen, &old_gen, i, &queue_set,
                           desired_plab_sz, term);
  }
}

inline ParScanThreadState& ParScanThreadStateSet::thread_sate(int i)
{
  assert(i >= 0 && i < length(), "sanity check!");
  return ((ParScanThreadState*)_data)[i];
}


void ParScanThreadStateSet::reset()
{
  _term.reset_for_reuse();
}

void ParScanThreadStateSet::flush()
{
  for (int i = 0; i < length(); ++i) {
    ParScanThreadState& par_scan_state = thread_sate(i);

    // Flush stats related to To-space PLAB activity and
    // retire the last buffer.
    par_scan_state.to_space_alloc_buffer()->
      flush_stats_and_retire(_gen.plab_stats(),
                             false /* !retain */);

    // Every thread has its own age table.  We need to merge
    // them all into one.
    ageTable *local_table = par_scan_state.age_table();
    _gen.age_table()->merge(local_table);

    // Inform old gen that we're done.
    _next_gen.par_promote_alloc_done(i);
    _next_gen.par_oop_since_save_marks_iterate_done(i);

    // Flush stats related to work queue activity (push/pop/steal)
    // This could conceivably become a bottleneck; if so, we'll put the
    // stat's gathering under the flag.
    if (PAR_STATS_ENABLED) {
      _pushes += par_scan_state.pushes();
      _pops   += par_scan_state.pops();
      _steals += par_scan_state.steals();
      if (ParallelGCVerbose) {
        gclog_or_tty->print("Thread %d complete:\n"
                            "  Pushes: %7d    Pops: %7d    Steals %7d (in %d attempts)\n",
                            i, par_scan_state.pushes(), par_scan_state.pops(),
                            par_scan_state.steals(), par_scan_state.steal_attempts());
        if (par_scan_state.overflow_pushes() > 0 ||
            par_scan_state.overflow_refills() > 0) {
          gclog_or_tty->print("  Overflow pushes: %7d    "
                              "Overflow refills: %7d for %d objs.\n",
                              par_scan_state.overflow_pushes(),
                              par_scan_state.overflow_refills(),
                              par_scan_state.overflow_refill_objs());
        }

        double elapsed = par_scan_state.elapsed();
        double strong_roots = par_scan_state.strong_roots_time();
        double term = par_scan_state.term_time();
        gclog_or_tty->print(
                            "  Elapsed: %7.2f ms.\n"
                            "    Strong roots: %7.2f ms (%6.2f%%)\n"
                            "    Termination:  %7.2f ms (%6.2f%%) (in %d entries)\n",
                           elapsed * 1000.0,
                           strong_roots * 1000.0, (strong_roots*100.0/elapsed),
                           term * 1000.0, (term*100.0/elapsed),
                           par_scan_state.term_attempts());
      }
    }
  }
}


ParScanClosure::ParScanClosure(ParNewGeneration* g,
                               ParScanThreadState* par_scan_state) :
  OopsInGenClosure(g), _par_scan_state(par_scan_state), _g(g)
{
  assert(_g->level() == 0, "Optimized for youngest generation");
  _boundary = _g->reserved().end();
}

ParScanWeakRefClosure::ParScanWeakRefClosure(ParNewGeneration* g,
                                             ParScanThreadState* par_scan_state)
  : ScanWeakRefClosure(g), _par_scan_state(par_scan_state)
{
}

#ifdef WIN32
#pragma warning(disable: 4786) /* identifier was truncated to '255' characters in the browser information */
#endif

ParEvacuateFollowersClosure::ParEvacuateFollowersClosure(
    ParScanThreadState* par_scan_state_,
    ParScanWithoutBarrierClosure* to_space_closure_,
    ParScanWithBarrierClosure* old_gen_closure_,
    ParRootScanWithoutBarrierClosure* to_space_root_closure_,
    ParNewGeneration* par_gen_,
    ParRootScanWithBarrierTwoGensClosure* old_gen_root_closure_,
    ObjToScanQueueSet* task_queues_,
    ParallelTaskTerminator* terminator_) :

    _par_scan_state(par_scan_state_),
    _to_space_closure(to_space_closure_),
    _old_gen_closure(old_gen_closure_),
    _to_space_root_closure(to_space_root_closure_),
    _old_gen_root_closure(old_gen_root_closure_),
    _par_gen(par_gen_),
    _task_queues(task_queues_),
    _terminator(terminator_)
{}

void ParEvacuateFollowersClosure::do_void() {
  ObjToScanQueue* work_q = par_scan_state()->work_queue();

  while (true) {

    // Scan to-space and old-gen objs until we run out of both.
    oop obj_to_scan;
    par_scan_state()->trim_queues(0);

    // We have no local work, attempt to steal from other threads.

    // attempt to steal work from promoted.
    par_scan_state()->note_steal_attempt();
    if (task_queues()->steal(par_scan_state()->thread_num(),
                             par_scan_state()->hash_seed(),
                             obj_to_scan)) {
      par_scan_state()->note_steal();
      bool res = work_q->push(obj_to_scan);
      assert(res, "Empty queue should have room for a push.");

      par_scan_state()->note_push();
      //   if successful, goto Start.
      continue;

      // try global overflow list.
    } else if (par_gen()->take_from_overflow_list(par_scan_state())) {
      continue;
    }

    // Otherwise, offer termination.
    par_scan_state()->start_term_time();
    if (terminator()->offer_termination()) break;
    par_scan_state()->end_term_time();
  }
  // Finish the last termination pause.
  par_scan_state()->end_term_time();
}

ParNewGenTask::ParNewGenTask(ParNewGeneration* gen, Generation* next_gen,
                HeapWord* young_old_boundary, ParScanThreadStateSet* state_set) :
    AbstractGangTask("ParNewGeneration collection"),
    _gen(gen), _next_gen(next_gen),
    _young_old_boundary(young_old_boundary),
    _state_set(state_set)
  {}

void ParNewGenTask::work(int i) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  // Since this is being done in a separate thread, need new resource
  // and handle marks.
  ResourceMark rm;
  HandleMark hm;
  // We would need multiple old-gen queues otherwise.
  guarantee(gch->n_gens() == 2,
     "Par young collection currently only works with one older gen.");

  Generation* old_gen = gch->next_gen(_gen);

  ParScanThreadState& par_scan_state = _state_set->thread_sate(i);
  par_scan_state.set_young_old_boundary(_young_old_boundary);

  par_scan_state.start_strong_roots();
  gch->gen_process_strong_roots(_gen->level(),
                                true, // Process younger gens, if any,
                                      // as strong roots.
                                false,// not collecting perm generation.
                                SharedHeap::SO_AllClasses,
                                &par_scan_state.older_gen_closure(),
                                &par_scan_state.to_space_root_closure());
  par_scan_state.end_strong_roots();

  // "evacuate followers".
  par_scan_state.evacuate_followers_closure().do_void();
}

#ifdef _MSC_VER
#pragma warning( push )
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif
ParNewGeneration::
ParNewGeneration(ReservedSpace rs, size_t initial_byte_size, int level)
  : DefNewGeneration(rs, initial_byte_size, level, "PCopy"),
  _overflow_list(NULL),
  _is_alive_closure(this),
  _plab_stats(YoungPLABSize, PLABWeight)
{
  _task_queues = new ObjToScanQueueSet(ParallelGCThreads);
  guarantee(_task_queues != NULL, "task_queues allocation failure.");

  for (uint i1 = 0; i1 < ParallelGCThreads; i1++) {
    ObjToScanQueuePadded *q_padded = new ObjToScanQueuePadded();
    guarantee(q_padded != NULL, "work_queue Allocation failure.");

    _task_queues->register_queue(i1, &q_padded->work_queue);
  }

  for (uint i2 = 0; i2 < ParallelGCThreads; i2++)
    _task_queues->queue(i2)->initialize();

  if (UsePerfData) {
    EXCEPTION_MARK;
    ResourceMark rm;

    const char* cname =
         PerfDataManager::counter_name(_gen_counters->name_space(), "threads");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_None,
                                     ParallelGCThreads, CHECK);
  }
}
#ifdef _MSC_VER
#pragma warning( pop )
#endif

// ParNewGeneration::
ParKeepAliveClosure::ParKeepAliveClosure(ParScanWeakRefClosure* cl) :
  DefNewGeneration::KeepAliveClosure(cl), _par_cl(cl) {}

void
// ParNewGeneration::
ParKeepAliveClosure::do_oop(oop* p) {
  // We never expect to see a null reference being processed
  // as a weak reference.
  assert (*p != NULL, "expected non-null ref");
  assert ((*p)->is_oop(), "expected an oop while scanning weak refs");

  _par_cl->do_oop_nv(p);

  if (Universe::heap()->is_in_reserved(p)) {
    _rs->write_ref_field_gc_par(p, *p);
  }
}

// ParNewGeneration::
KeepAliveClosure::KeepAliveClosure(ScanWeakRefClosure* cl) :
  DefNewGeneration::KeepAliveClosure(cl) {}

void
// ParNewGeneration::
KeepAliveClosure::do_oop(oop* p) {
  // We never expect to see a null reference being processed
  // as a weak reference.
  assert (*p != NULL, "expected non-null ref");
  assert ((*p)->is_oop(), "expected an oop while scanning weak refs");

  _cl->do_oop_nv(p);

  if (Universe::heap()->is_in_reserved(p)) {
    _rs->write_ref_field_gc_par(p, *p);
  }
}

void ScanClosureWithParBarrier::do_oop(oop* p) {
  oop obj = *p;
  // Should we copy the obj?
  if (obj != NULL) {
    if ((HeapWord*)obj < _boundary) {
      assert(!_g->to()->is_in_reserved(obj), "Scanning field twice?");
      if (obj->is_forwarded()) {
        *p = obj->forwardee();
      } else {
        *p = _g->DefNewGeneration::copy_to_survivor_space(obj, p);
      }
    }
    if (_gc_barrier) {
      // If p points to a younger generation, mark the card.
      if ((HeapWord*)obj < _gen_boundary) {
        _rs->write_ref_field_gc_par(p, obj);
      }
    }
  }
}

class ParNewRefProcTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
public:
  ParNewRefProcTaskProxy(ProcessTask& task, ParNewGeneration& gen,
                         Generation& next_gen,
                         HeapWord* young_old_boundary,
                         ParScanThreadStateSet& state_set);

private:
  virtual void work(int i);

private:
  ParNewGeneration&      _gen;
  ProcessTask&           _task;
  Generation&            _next_gen;
  HeapWord*              _young_old_boundary;
  ParScanThreadStateSet& _state_set;
};

ParNewRefProcTaskProxy::ParNewRefProcTaskProxy(
    ProcessTask& task, ParNewGeneration& gen,
    Generation& next_gen,
    HeapWord* young_old_boundary,
    ParScanThreadStateSet& state_set)
  : AbstractGangTask("ParNewGeneration parallel reference processing"),
    _gen(gen),
    _task(task),
    _next_gen(next_gen),
    _young_old_boundary(young_old_boundary),
    _state_set(state_set)
{
}

void ParNewRefProcTaskProxy::work(int i)
{
  ResourceMark rm;
  HandleMark hm;
  ParScanThreadState& par_scan_state = _state_set.thread_sate(i);
  par_scan_state.set_young_old_boundary(_young_old_boundary);
  _task.work(i, par_scan_state.is_alive_closure(),
             par_scan_state.keep_alive_closure(),
             par_scan_state.evacuate_followers_closure());
}

class ParNewRefEnqueueTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::EnqueueTask EnqueueTask;
  EnqueueTask& _task;

public:
  ParNewRefEnqueueTaskProxy(EnqueueTask& task)
    : AbstractGangTask("ParNewGeneration parallel reference enqueue"),
      _task(task)
  { }

  virtual void work(int i)
  {
    _task.work(i);
  }
};


void ParNewRefProcTaskExecutor::execute(ProcessTask& task)
{
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->kind() == CollectedHeap::GenCollectedHeap,
         "not a generational heap");
  WorkGang* workers = gch->workers();
  assert(workers != NULL, "Need parallel worker threads.");
  ParNewRefProcTaskProxy rp_task(task, _generation, *_generation.next_gen(),
                                 _generation.reserved().end(), _state_set);
  workers->run_task(&rp_task);
  _state_set.reset();
}

void ParNewRefProcTaskExecutor::execute(EnqueueTask& task)
{
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  WorkGang* workers = gch->workers();
  assert(workers != NULL, "Need parallel worker threads.");
  ParNewRefEnqueueTaskProxy enq_task(task);
  workers->run_task(&enq_task);
}

void ParNewRefProcTaskExecutor::set_single_threaded_mode()
{
  _state_set.flush();
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  gch->set_par_threads(0);  // 0 ==> non-parallel.
  gch->save_marks();
}

ScanClosureWithParBarrier::
ScanClosureWithParBarrier(ParNewGeneration* g, bool gc_barrier) :
  ScanClosure(g, gc_barrier) {}

EvacuateFollowersClosureGeneral::
EvacuateFollowersClosureGeneral(GenCollectedHeap* gch, int level,
                                OopsInGenClosure* cur,
                                OopsInGenClosure* older) :
  _gch(gch), _level(level),
  _scan_cur_or_nonheap(cur), _scan_older(older)
{}

void EvacuateFollowersClosureGeneral::do_void() {
  do {
    // Beware: this call will lead to closure applications via virtual
    // calls.
    _gch->oop_since_save_marks_iterate(_level,
                                       _scan_cur_or_nonheap,
                                       _scan_older);
  } while (!_gch->no_allocs_since_save_marks(_level));
}


bool ParNewGeneration::_avoid_promotion_undo = false;

void ParNewGeneration::adjust_desired_tenuring_threshold() {
  // Set the desired survivor size to half the real survivor space
  _tenuring_threshold =
    age_table()->compute_tenuring_threshold(to()->capacity()/HeapWordSize);
}

// A Generation that does parallel young-gen collection.

void ParNewGeneration::collect(bool   full,
                               bool   clear_all_soft_refs,
                               size_t size,
                               bool   is_tlab) {
  assert(full || size > 0, "otherwise we don't want to collect");
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->kind() == CollectedHeap::GenCollectedHeap,
    "not a CMS generational heap");
  AdaptiveSizePolicy* size_policy = gch->gen_policy()->size_policy();
  WorkGang* workers = gch->workers();
  _next_gen = gch->next_gen(this);
  assert(_next_gen != NULL,
    "This must be the youngest gen, and not the only gen");
  assert(gch->n_gens() == 2,
         "Par collection currently only works with single older gen.");
  // Do we have to avoid promotion_undo?
  if (gch->collector_policy()->is_concurrent_mark_sweep_policy()) {
    set_avoid_promotion_undo(true);
  }

  // If the next generation is too full to accomodate worst-case promotion
  // from this generation, pass on collection; let the next generation
  // do it.
  if (!collection_attempt_is_safe()) {
    gch->set_incremental_collection_will_fail();
    return;
  }
  assert(to()->is_empty(), "Else not collection_attempt_is_safe");

  init_assuming_no_promotion_failure();

  if (UseAdaptiveSizePolicy) {
    set_survivor_overflow(false);
    size_policy->minor_collection_begin();
  }

  TraceTime t1("GC", PrintGC && !PrintGCDetails, true, gclog_or_tty);
  // Capture heap used before collection (for printing).
  size_t gch_prev_used = gch->used();

  SpecializationStats::clear();

  age_table()->clear();
  to()->clear();

  gch->save_marks();
  assert(workers != NULL, "Need parallel worker threads.");
  ParallelTaskTerminator _term(workers->total_workers(), task_queues());
  ParScanThreadStateSet thread_state_set(workers->total_workers(),
                                         *to(), *this, *_next_gen, *task_queues(),
                                         desired_plab_sz(), _term);

  ParNewGenTask tsk(this, _next_gen, reserved().end(), &thread_state_set);
  int n_workers = workers->total_workers();
  gch->set_par_threads(n_workers);
  gch->change_strong_roots_parity();
  gch->rem_set()->prepare_for_younger_refs_iterate(true);
  // It turns out that even when we're using 1 thread, doing the work in a
  // separate thread causes wide variance in run times.  We can't help this
  // in the multi-threaded case, but we special-case n=1 here to get
  // repeatable measurements of the 1-thread overhead of the parallel code.
  if (n_workers > 1) {
    workers->run_task(&tsk);
  } else {
    tsk.work(0);
  }
  thread_state_set.reset();

  if (PAR_STATS_ENABLED && ParallelGCVerbose) {
    gclog_or_tty->print("Thread totals:\n"
               "  Pushes: %7d    Pops: %7d    Steals %7d (sum = %7d).\n",
               thread_state_set.pushes(), thread_state_set.pops(),
               thread_state_set.steals(),
               thread_state_set.pops()+thread_state_set.steals());
  }
  assert(thread_state_set.pushes() == thread_state_set.pops() + thread_state_set.steals(),
         "Or else the queues are leaky.");

  // For now, process discovered weak refs sequentially.
#ifdef COMPILER2
  ReferencePolicy *soft_ref_policy = new LRUMaxHeapPolicy();
#else
  ReferencePolicy *soft_ref_policy = new LRUCurrentHeapPolicy();
#endif // COMPILER2

  // Process (weak) reference objects found during scavenge.
  IsAliveClosure is_alive(this);
  ScanWeakRefClosure scan_weak_ref(this);
  KeepAliveClosure keep_alive(&scan_weak_ref);
  ScanClosure               scan_without_gc_barrier(this, false);
  ScanClosureWithParBarrier scan_with_gc_barrier(this, true);
  set_promo_failure_scan_stack_closure(&scan_without_gc_barrier);
  EvacuateFollowersClosureGeneral evacuate_followers(gch, _level,
    &scan_without_gc_barrier, &scan_with_gc_barrier);
  if (ref_processor()->processing_is_mt()) {
    ParNewRefProcTaskExecutor task_executor(*this, thread_state_set);
    ref_processor()->process_discovered_references(
        soft_ref_policy, &is_alive, &keep_alive, &evacuate_followers,
        &task_executor);
  } else {
    thread_state_set.flush();
    gch->set_par_threads(0);  // 0 ==> non-parallel.
    gch->save_marks();
    ref_processor()->process_discovered_references(
      soft_ref_policy, &is_alive, &keep_alive, &evacuate_followers,
      NULL);
  }
  if (!promotion_failed()) {
    // Swap the survivor spaces.
    eden()->clear();
    from()->clear();
    swap_spaces();

    assert(to()->is_empty(), "to space should be empty now");
  } else {
    assert(HandlePromotionFailure,
      "Should only be here if promotion failure handling is on");
    if (_promo_failure_scan_stack != NULL) {
      // Can be non-null because of reference processing.
      // Free stack with its elements.
      delete _promo_failure_scan_stack;
      _promo_failure_scan_stack = NULL;
    }
    remove_forwarding_pointers();
    if (PrintGCDetails) {
      gclog_or_tty->print(" (promotion failed)");
    }
    // All the spaces are in play for mark-sweep.
    swap_spaces();  // Make life simpler for CMS || rescan; see 6483690.
    from()->set_next_compaction_space(to());
    gch->set_incremental_collection_will_fail();

    // Reset the PromotionFailureALot counters.
    NOT_PRODUCT(Universe::heap()->reset_promotion_should_fail();)
  }
  // set new iteration safe limit for the survivor spaces
  from()->set_concurrent_iteration_safe_limit(from()->top());
  to()->set_concurrent_iteration_safe_limit(to()->top());

  adjust_desired_tenuring_threshold();
  if (ResizePLAB) {
    plab_stats()->adjust_desired_plab_sz();
  }

  if (PrintGC && !PrintGCDetails) {
    gch->print_heap_change(gch_prev_used);
  }

  if (UseAdaptiveSizePolicy) {
    size_policy->minor_collection_end(gch->gc_cause());
    size_policy->avg_survived()->sample(from()->used());
  }

  update_time_of_last_gc(os::javaTimeMillis());

  SpecializationStats::print();

  ref_processor()->set_enqueuing_is_done(true);
  if (ref_processor()->processing_is_mt()) {
    ParNewRefProcTaskExecutor task_executor(*this, thread_state_set);
    ref_processor()->enqueue_discovered_references(&task_executor);
  } else {
    ref_processor()->enqueue_discovered_references(NULL);
  }
  ref_processor()->verify_no_references_recorded();
}

static int sum;
void ParNewGeneration::waste_some_time() {
  for (int i = 0; i < 100; i++) {
    sum += i;
  }
}

static const oop ClaimedForwardPtr = oop(0x4);

// Because of concurrency, there are times where an object for which
// "is_forwarded()" is true contains an "interim" forwarding pointer
// value.  Such a value will soon be overwritten with a real value.
// This method requires "obj" to have a forwarding pointer, and waits, if
// necessary for a real one to be inserted, and returns it.

oop ParNewGeneration::real_forwardee(oop obj) {
  oop forward_ptr = obj->forwardee();
  if (forward_ptr != ClaimedForwardPtr) {
    return forward_ptr;
  } else {
    return real_forwardee_slow(obj);
  }
}

oop ParNewGeneration::real_forwardee_slow(oop obj) {
  // Spin-read if it is claimed but not yet written by another thread.
  oop forward_ptr = obj->forwardee();
  while (forward_ptr == ClaimedForwardPtr) {
    waste_some_time();
    assert(obj->is_forwarded(), "precondition");
    forward_ptr = obj->forwardee();
  }
  return forward_ptr;
}

#ifdef ASSERT
bool ParNewGeneration::is_legal_forward_ptr(oop p) {
  return
    (_avoid_promotion_undo && p == ClaimedForwardPtr)
    || Universe::heap()->is_in_reserved(p);
}
#endif

void ParNewGeneration::preserve_mark_if_necessary(oop obj, markOop m) {
  if ((m != markOopDesc::prototype()) &&
      (!UseBiasedLocking || (m != markOopDesc::biased_locking_prototype()))) {
    MutexLocker ml(ParGCRareEvent_lock);
    DefNewGeneration::preserve_mark_if_necessary(obj, m);
  }
}

// Multiple GC threads may try to promote an object.  If the object
// is successfully promoted, a forwarding pointer will be installed in
// the object in the young generation.  This method claims the right
// to install the forwarding pointer before it copies the object,
// thus avoiding the need to undo the copy as in
// copy_to_survivor_space_avoiding_with_undo.

oop ParNewGeneration::copy_to_survivor_space_avoiding_promotion_undo(
        ParScanThreadState* par_scan_state, oop old, size_t sz, markOop m) {
  // In the sequential version, this assert also says that the object is
  // not forwarded.  That might not be the case here.  It is the case that
  // the caller observed it to be not forwarded at some time in the past.
  assert(is_in_reserved(old), "shouldn't be scavenging this oop");

  // The sequential code read "old->age()" below.  That doesn't work here,
  // since the age is in the mark word, and that might be overwritten with
  // a forwarding pointer by a parallel thread.  So we must save the mark
  // word in a local and then analyze it.
  oopDesc dummyOld;
  dummyOld.set_mark(m);
  assert(!dummyOld.is_forwarded(),
         "should not be called with forwarding pointer mark word.");

  oop new_obj = NULL;
  oop forward_ptr;

  // Try allocating obj in to-space (unless too old)
  if (dummyOld.age() < tenuring_threshold()) {
    new_obj = (oop)par_scan_state->alloc_in_to_space(sz);
    if (new_obj == NULL) {
      set_survivor_overflow(true);
    }
  }

  if (new_obj == NULL) {
    // Either to-space is full or we decided to promote
    // try allocating obj tenured

    // Attempt to install a null forwarding pointer (atomically),
    // to claim the right to install the real forwarding pointer.
    forward_ptr = old->forward_to_atomic(ClaimedForwardPtr);
    if (forward_ptr != NULL) {
      // someone else beat us to it.
        return real_forwardee(old);
    }

    new_obj = _next_gen->par_promote(par_scan_state->thread_num(),
                                       old, m, sz);

    if (new_obj == NULL) {
      if (!HandlePromotionFailure) {
        // A failed promotion likely means the MaxLiveObjectEvacuationRatio flag
        // is incorrectly set. In any case, its seriously wrong to be here!
        vm_exit_out_of_memory(sz*wordSize, "promotion");
      }
      // promotion failed, forward to self
      _promotion_failed = true;
      new_obj = old;

      preserve_mark_if_necessary(old, m);
    }

    old->forward_to(new_obj);
    forward_ptr = NULL;
  } else {
    // Is in to-space; do copying ourselves.
    Copy::aligned_disjoint_words((HeapWord*)old, (HeapWord*)new_obj, sz);
    forward_ptr = old->forward_to_atomic(new_obj);
    // Restore the mark word copied above.
    new_obj->set_mark(m);
    // Increment age if obj still in new generation
    new_obj->incr_age();
    par_scan_state->age_table()->add(new_obj, sz);
  }
  assert(new_obj != NULL, "just checking");

  if (forward_ptr == NULL) {
    oop obj_to_push = new_obj;
    if (par_scan_state->should_be_partially_scanned(obj_to_push, old)) {
      // Length field used as index of next element to be scanned.
      // Real length can be obtained from real_forwardee()
      arrayOop(old)->set_length(0);
      obj_to_push = old;
      assert(obj_to_push->is_forwarded() && obj_to_push->forwardee() != obj_to_push,
             "push forwarded object");
    }
    // Push it on one of the queues of to-be-scanned objects.
    if (!par_scan_state->work_queue()->push(obj_to_push)) {
      // Add stats for overflow pushes.
      if (Verbose && PrintGCDetails) {
        gclog_or_tty->print("queue overflow!\n");
      }
      push_on_overflow_list(old);
      par_scan_state->note_overflow_push();
    }
    par_scan_state->note_push();

    return new_obj;
  }

  // Oops.  Someone beat us to it.  Undo the allocation.  Where did we
  // allocate it?
  if (is_in_reserved(new_obj)) {
    // Must be in to_space.
    assert(to()->is_in_reserved(new_obj), "Checking");
    if (forward_ptr == ClaimedForwardPtr) {
      // Wait to get the real forwarding pointer value.
      forward_ptr = real_forwardee(old);
    }
    par_scan_state->undo_alloc_in_to_space((HeapWord*)new_obj, sz);
  }

  return forward_ptr;
}


// Multiple GC threads may try to promote the same object.  If two
// or more GC threads copy the object, only one wins the race to install
// the forwarding pointer.  The other threads have to undo their copy.

oop ParNewGeneration::copy_to_survivor_space_with_undo(
        ParScanThreadState* par_scan_state, oop old, size_t sz, markOop m) {

  // In the sequential version, this assert also says that the object is
  // not forwarded.  That might not be the case here.  It is the case that
  // the caller observed it to be not forwarded at some time in the past.
  assert(is_in_reserved(old), "shouldn't be scavenging this oop");

  // The sequential code read "old->age()" below.  That doesn't work here,
  // since the age is in the mark word, and that might be overwritten with
  // a forwarding pointer by a parallel thread.  So we must save the mark
  // word here, install it in a local oopDesc, and then analyze it.
  oopDesc dummyOld;
  dummyOld.set_mark(m);
  assert(!dummyOld.is_forwarded(),
         "should not be called with forwarding pointer mark word.");

  bool failed_to_promote = false;
  oop new_obj = NULL;
  oop forward_ptr;

  // Try allocating obj in to-space (unless too old)
  if (dummyOld.age() < tenuring_threshold()) {
    new_obj = (oop)par_scan_state->alloc_in_to_space(sz);
    if (new_obj == NULL) {
      set_survivor_overflow(true);
    }
  }

  if (new_obj == NULL) {
    // Either to-space is full or we decided to promote
    // try allocating obj tenured
    new_obj = _next_gen->par_promote(par_scan_state->thread_num(),
                                       old, m, sz);

    if (new_obj == NULL) {
      if (!HandlePromotionFailure) {
        // A failed promotion likely means the MaxLiveObjectEvacuationRatio
        // flag is incorrectly set. In any case, its seriously wrong to be
        // here!
        vm_exit_out_of_memory(sz*wordSize, "promotion");
      }
      // promotion failed, forward to self
      forward_ptr = old->forward_to_atomic(old);
      new_obj = old;

      if (forward_ptr != NULL) {
        return forward_ptr;   // someone else succeeded
      }

      _promotion_failed = true;
      failed_to_promote = true;

      preserve_mark_if_necessary(old, m);
    }
  } else {
    // Is in to-space; do copying ourselves.
    Copy::aligned_disjoint_words((HeapWord*)old, (HeapWord*)new_obj, sz);
    // Restore the mark word copied above.
    new_obj->set_mark(m);
    // Increment age if new_obj still in new generation
    new_obj->incr_age();
    par_scan_state->age_table()->add(new_obj, sz);
  }
  assert(new_obj != NULL, "just checking");

  // Now attempt to install the forwarding pointer (atomically).
  // We have to copy the mark word before overwriting with forwarding
  // ptr, so we can restore it below in the copy.
  if (!failed_to_promote) {
    forward_ptr = old->forward_to_atomic(new_obj);
  }

  if (forward_ptr == NULL) {
    oop obj_to_push = new_obj;
    if (par_scan_state->should_be_partially_scanned(obj_to_push, old)) {
      // Length field used as index of next element to be scanned.
      // Real length can be obtained from real_forwardee()
      arrayOop(old)->set_length(0);
      obj_to_push = old;
      assert(obj_to_push->is_forwarded() && obj_to_push->forwardee() != obj_to_push,
             "push forwarded object");
    }
    // Push it on one of the queues of to-be-scanned objects.
    if (!par_scan_state->work_queue()->push(obj_to_push)) {
      // Add stats for overflow pushes.
      push_on_overflow_list(old);
      par_scan_state->note_overflow_push();
    }
    par_scan_state->note_push();

    return new_obj;
  }

  // Oops.  Someone beat us to it.  Undo the allocation.  Where did we
  // allocate it?
  if (is_in_reserved(new_obj)) {
    // Must be in to_space.
    assert(to()->is_in_reserved(new_obj), "Checking");
    par_scan_state->undo_alloc_in_to_space((HeapWord*)new_obj, sz);
  } else {
    assert(!_avoid_promotion_undo, "Should not be here if avoiding.");
    _next_gen->par_promote_alloc_undo(par_scan_state->thread_num(),
                                      (HeapWord*)new_obj, sz);
  }

  return forward_ptr;
}

void ParNewGeneration::push_on_overflow_list(oop from_space_obj) {
  oop cur_overflow_list = _overflow_list;
  // if the object has been forwarded to itself, then we cannot
  // use the klass pointer for the linked list.  Instead we have
  // to allocate an oopDesc in the C-Heap and use that for the linked list.
  if (from_space_obj->forwardee() == from_space_obj) {
    oopDesc* listhead = NEW_C_HEAP_ARRAY(oopDesc, 1);
    listhead->forward_to(from_space_obj);
    from_space_obj = listhead;
  }
  while (true) {
    from_space_obj->set_klass_to_list_ptr(cur_overflow_list);
    oop observed_overflow_list =
      (oop)Atomic::cmpxchg_ptr(from_space_obj, &_overflow_list, cur_overflow_list);
    if (observed_overflow_list == cur_overflow_list) break;
    // Otherwise...
    cur_overflow_list = observed_overflow_list;
  }
}

bool
ParNewGeneration::take_from_overflow_list(ParScanThreadState* par_scan_state) {
  ObjToScanQueue* work_q = par_scan_state->work_queue();
  // How many to take?
  int objsFromOverflow = MIN2(work_q->max_elems()/4,
                              (juint)ParGCDesiredObjsFromOverflowList);

  if (_overflow_list == NULL) return false;

  // Otherwise, there was something there; try claiming the list.
  oop prefix = (oop)Atomic::xchg_ptr(NULL, &_overflow_list);

  if (prefix == NULL) {
    return false;
  }
  // Trim off a prefix of at most objsFromOverflow items
  int i = 1;
  oop cur = prefix;
  while (i < objsFromOverflow && cur->klass() != NULL) {
    i++; cur = oop(cur->klass());
  }

  // Reattach remaining (suffix) to overflow list
  if (cur->klass() != NULL) {
    oop suffix = oop(cur->klass());
    cur->set_klass_to_list_ptr(NULL);

    // Find last item of suffix list
    oop last = suffix;
    while (last->klass() != NULL) {
      last = oop(last->klass());
    }
    // Atomically prepend suffix to current overflow list
    oop cur_overflow_list = _overflow_list;
    while (true) {
      last->set_klass_to_list_ptr(cur_overflow_list);
      oop observed_overflow_list =
        (oop)Atomic::cmpxchg_ptr(suffix, &_overflow_list, cur_overflow_list);
      if (observed_overflow_list == cur_overflow_list) break;
      // Otherwise...
      cur_overflow_list = observed_overflow_list;
    }
  }

  // Push objects on prefix list onto this thread's work queue
  assert(cur != NULL, "program logic");
  cur = prefix;
  int n = 0;
  while (cur != NULL) {
    oop obj_to_push = cur->forwardee();
    oop next        = oop(cur->klass());
    cur->set_klass(obj_to_push->klass());
    if (par_scan_state->should_be_partially_scanned(obj_to_push, cur)) {
      obj_to_push = cur;
      assert(arrayOop(cur)->length() == 0, "entire array remaining to be scanned");
    }
    work_q->push(obj_to_push);
    cur = next;
    n++;
  }
  par_scan_state->note_overflow_refill(n);
  return true;
}

void ParNewGeneration::ref_processor_init()
{
  if (_ref_processor == NULL) {
    // Allocate and initialize a reference processor
    _ref_processor = ReferenceProcessor::create_ref_processor(
        _reserved,                  // span
        refs_discovery_is_atomic(), // atomic_discovery
        refs_discovery_is_mt(),     // mt_discovery
        NULL,                       // is_alive_non_header
        ParallelGCThreads,
        ParallelRefProcEnabled);
  }
}

const char* ParNewGeneration::name() const {
  return "par new generation";
}
