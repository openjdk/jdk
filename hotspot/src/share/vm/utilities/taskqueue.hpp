/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_TASKQUEUE_HPP
#define SHARE_VM_UTILITIES_TASKQUEUE_HPP

#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/mutex.hpp"
#include "utilities/stack.hpp"
#ifdef TARGET_OS_ARCH_linux_x86
# include "orderAccess_linux_x86.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_sparc
# include "orderAccess_linux_sparc.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_zero
# include "orderAccess_linux_zero.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_x86
# include "orderAccess_solaris_x86.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_sparc
# include "orderAccess_solaris_sparc.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_windows_x86
# include "orderAccess_windows_x86.inline.hpp"
#endif

// Simple TaskQueue stats that are collected by default in debug builds.

#if !defined(TASKQUEUE_STATS) && defined(ASSERT)
#define TASKQUEUE_STATS 1
#elif !defined(TASKQUEUE_STATS)
#define TASKQUEUE_STATS 0
#endif

#if TASKQUEUE_STATS
#define TASKQUEUE_STATS_ONLY(code) code
#else
#define TASKQUEUE_STATS_ONLY(code)
#endif // TASKQUEUE_STATS

#if TASKQUEUE_STATS
class TaskQueueStats {
public:
  enum StatId {
    push,             // number of taskqueue pushes
    pop,              // number of taskqueue pops
    pop_slow,         // subset of taskqueue pops that were done slow-path
    steal_attempt,    // number of taskqueue steal attempts
    steal,            // number of taskqueue steals
    overflow,         // number of overflow pushes
    overflow_max_len, // max length of overflow stack
    last_stat_id
  };

public:
  inline TaskQueueStats()       { reset(); }

  inline void record_push()     { ++_stats[push]; }
  inline void record_pop()      { ++_stats[pop]; }
  inline void record_pop_slow() { record_pop(); ++_stats[pop_slow]; }
  inline void record_steal(bool success);
  inline void record_overflow(size_t new_length);

  TaskQueueStats & operator +=(const TaskQueueStats & addend);

  inline size_t get(StatId id) const { return _stats[id]; }
  inline const size_t* get() const   { return _stats; }

  inline void reset();

  // Print the specified line of the header (does not include a line separator).
  static void print_header(unsigned int line, outputStream* const stream = tty,
                           unsigned int width = 10);
  // Print the statistics (does not include a line separator).
  void print(outputStream* const stream = tty, unsigned int width = 10) const;

  DEBUG_ONLY(void verify() const;)

private:
  size_t                    _stats[last_stat_id];
  static const char * const _names[last_stat_id];
};

void TaskQueueStats::record_steal(bool success) {
  ++_stats[steal_attempt];
  if (success) ++_stats[steal];
}

void TaskQueueStats::record_overflow(size_t new_len) {
  ++_stats[overflow];
  if (new_len > _stats[overflow_max_len]) _stats[overflow_max_len] = new_len;
}

void TaskQueueStats::reset() {
  memset(_stats, 0, sizeof(_stats));
}
#endif // TASKQUEUE_STATS

template <unsigned int N>
class TaskQueueSuper: public CHeapObj {
protected:
  // Internal type for indexing the queue; also used for the tag.
  typedef NOT_LP64(uint16_t) LP64_ONLY(uint32_t) idx_t;

  // The first free element after the last one pushed (mod N).
  volatile uint _bottom;

  enum { MOD_N_MASK = N - 1 };

  class Age {
  public:
    Age(size_t data = 0)         { _data = data; }
    Age(const Age& age)          { _data = age._data; }
    Age(idx_t top, idx_t tag)    { _fields._top = top; _fields._tag = tag; }

    Age   get()        const volatile { return _data; }
    void  set(Age age) volatile       { _data = age._data; }

    idx_t top()        const volatile { return _fields._top; }
    idx_t tag()        const volatile { return _fields._tag; }

    // Increment top; if it wraps, increment tag also.
    void increment() {
      _fields._top = increment_index(_fields._top);
      if (_fields._top == 0) ++_fields._tag;
    }

    Age cmpxchg(const Age new_age, const Age old_age) volatile {
      return (size_t) Atomic::cmpxchg_ptr((intptr_t)new_age._data,
                                          (volatile intptr_t *)&_data,
                                          (intptr_t)old_age._data);
    }

    bool operator ==(const Age& other) const { return _data == other._data; }

  private:
    struct fields {
      idx_t _top;
      idx_t _tag;
    };
    union {
      size_t _data;
      fields _fields;
    };
  };

  volatile Age _age;

  // These both operate mod N.
  static uint increment_index(uint ind) {
    return (ind + 1) & MOD_N_MASK;
  }
  static uint decrement_index(uint ind) {
    return (ind - 1) & MOD_N_MASK;
  }

  // Returns a number in the range [0..N).  If the result is "N-1", it should be
  // interpreted as 0.
  uint dirty_size(uint bot, uint top) const {
    return (bot - top) & MOD_N_MASK;
  }

  // Returns the size corresponding to the given "bot" and "top".
  uint size(uint bot, uint top) const {
    uint sz = dirty_size(bot, top);
    // Has the queue "wrapped", so that bottom is less than top?  There's a
    // complicated special case here.  A pair of threads could perform pop_local
    // and pop_global operations concurrently, starting from a state in which
    // _bottom == _top+1.  The pop_local could succeed in decrementing _bottom,
    // and the pop_global in incrementing _top (in which case the pop_global
    // will be awarded the contested queue element.)  The resulting state must
    // be interpreted as an empty queue.  (We only need to worry about one such
    // event: only the queue owner performs pop_local's, and several concurrent
    // threads attempting to perform the pop_global will all perform the same
    // CAS, and only one can succeed.)  Any stealing thread that reads after
    // either the increment or decrement will see an empty queue, and will not
    // join the competitors.  The "sz == -1 || sz == N-1" state will not be
    // modified by concurrent queues, so the owner thread can reset the state to
    // _bottom == top so subsequent pushes will be performed normally.
    return (sz == N - 1) ? 0 : sz;
  }

public:
  TaskQueueSuper() : _bottom(0), _age() {}

  // Return true if the TaskQueue contains/does not contain any tasks.
  bool peek()     const { return _bottom != _age.top(); }
  bool is_empty() const { return size() == 0; }

  // Return an estimate of the number of elements in the queue.
  // The "careful" version admits the possibility of pop_local/pop_global
  // races.
  uint size() const {
    return size(_bottom, _age.top());
  }

  uint dirty_size() const {
    return dirty_size(_bottom, _age.top());
  }

  void set_empty() {
    _bottom = 0;
    _age.set(0);
  }

  // Maximum number of elements allowed in the queue.  This is two less
  // than the actual queue size, for somewhat complicated reasons.
  uint max_elems() const { return N - 2; }

  // Total size of queue.
  static const uint total_size() { return N; }

  TASKQUEUE_STATS_ONLY(TaskQueueStats stats;)
};

template<class E, unsigned int N = TASKQUEUE_SIZE>
class GenericTaskQueue: public TaskQueueSuper<N> {
protected:
  typedef typename TaskQueueSuper<N>::Age Age;
  typedef typename TaskQueueSuper<N>::idx_t idx_t;

  using TaskQueueSuper<N>::_bottom;
  using TaskQueueSuper<N>::_age;
  using TaskQueueSuper<N>::increment_index;
  using TaskQueueSuper<N>::decrement_index;
  using TaskQueueSuper<N>::dirty_size;

public:
  using TaskQueueSuper<N>::max_elems;
  using TaskQueueSuper<N>::size;
  TASKQUEUE_STATS_ONLY(using TaskQueueSuper<N>::stats;)

private:
  // Slow paths for push, pop_local.  (pop_global has no fast path.)
  bool push_slow(E t, uint dirty_n_elems);
  bool pop_local_slow(uint localBot, Age oldAge);

public:
  typedef E element_type;

  // Initializes the queue to empty.
  GenericTaskQueue();

  void initialize();

  // Push the task "t" on the queue.  Returns "false" iff the queue is full.
  inline bool push(E t);

  // Attempts to claim a task from the "local" end of the queue (the most
  // recently pushed).  If successful, returns true and sets t to the task;
  // otherwise, returns false (the queue is empty).
  inline bool pop_local(E& t);

  // Like pop_local(), but uses the "global" end of the queue (the least
  // recently pushed).
  bool pop_global(E& t);

  // Delete any resource associated with the queue.
  ~GenericTaskQueue();

  // apply the closure to all elements in the task queue
  void oops_do(OopClosure* f);

private:
  // Element array.
  volatile E* _elems;
};

template<class E, unsigned int N>
GenericTaskQueue<E, N>::GenericTaskQueue() {
  assert(sizeof(Age) == sizeof(size_t), "Depends on this.");
}

template<class E, unsigned int N>
void GenericTaskQueue<E, N>::initialize() {
  _elems = NEW_C_HEAP_ARRAY(E, N);
}

template<class E, unsigned int N>
void GenericTaskQueue<E, N>::oops_do(OopClosure* f) {
  // tty->print_cr("START OopTaskQueue::oops_do");
  uint iters = size();
  uint index = _bottom;
  for (uint i = 0; i < iters; ++i) {
    index = decrement_index(index);
    // tty->print_cr("  doing entry %d," INTPTR_T " -> " INTPTR_T,
    //            index, &_elems[index], _elems[index]);
    E* t = (E*)&_elems[index];      // cast away volatility
    oop* p = (oop*)t;
    assert((*t)->is_oop_or_null(), "Not an oop or null");
    f->do_oop(p);
  }
  // tty->print_cr("END OopTaskQueue::oops_do");
}

template<class E, unsigned int N>
bool GenericTaskQueue<E, N>::push_slow(E t, uint dirty_n_elems) {
  if (dirty_n_elems == N - 1) {
    // Actually means 0, so do the push.
    uint localBot = _bottom;
    // g++ complains if the volatile result of the assignment is unused.
    const_cast<E&>(_elems[localBot] = t);
    OrderAccess::release_store(&_bottom, increment_index(localBot));
    TASKQUEUE_STATS_ONLY(stats.record_push());
    return true;
  }
  return false;
}

// pop_local_slow() is done by the owning thread and is trying to
// get the last task in the queue.  It will compete with pop_global()
// that will be used by other threads.  The tag age is incremented
// whenever the queue goes empty which it will do here if this thread
// gets the last task or in pop_global() if the queue wraps (top == 0
// and pop_global() succeeds, see pop_global()).
template<class E, unsigned int N>
bool GenericTaskQueue<E, N>::pop_local_slow(uint localBot, Age oldAge) {
  // This queue was observed to contain exactly one element; either this
  // thread will claim it, or a competing "pop_global".  In either case,
  // the queue will be logically empty afterwards.  Create a new Age value
  // that represents the empty queue for the given value of "_bottom".  (We
  // must also increment "tag" because of the case where "bottom == 1",
  // "top == 0".  A pop_global could read the queue element in that case,
  // then have the owner thread do a pop followed by another push.  Without
  // the incrementing of "tag", the pop_global's CAS could succeed,
  // allowing it to believe it has claimed the stale element.)
  Age newAge((idx_t)localBot, oldAge.tag() + 1);
  // Perhaps a competing pop_global has already incremented "top", in which
  // case it wins the element.
  if (localBot == oldAge.top()) {
    // No competing pop_global has yet incremented "top"; we'll try to
    // install new_age, thus claiming the element.
    Age tempAge = _age.cmpxchg(newAge, oldAge);
    if (tempAge == oldAge) {
      // We win.
      assert(dirty_size(localBot, _age.top()) != N - 1, "sanity");
      TASKQUEUE_STATS_ONLY(stats.record_pop_slow());
      return true;
    }
  }
  // We lose; a completing pop_global gets the element.  But the queue is empty
  // and top is greater than bottom.  Fix this representation of the empty queue
  // to become the canonical one.
  _age.set(newAge);
  assert(dirty_size(localBot, _age.top()) != N - 1, "sanity");
  return false;
}

template<class E, unsigned int N>
bool GenericTaskQueue<E, N>::pop_global(E& t) {
  Age oldAge = _age.get();
  uint localBot = _bottom;
  uint n_elems = size(localBot, oldAge.top());
  if (n_elems == 0) {
    return false;
  }

  const_cast<E&>(t = _elems[oldAge.top()]);
  Age newAge(oldAge);
  newAge.increment();
  Age resAge = _age.cmpxchg(newAge, oldAge);

  // Note that using "_bottom" here might fail, since a pop_local might
  // have decremented it.
  assert(dirty_size(localBot, newAge.top()) != N - 1, "sanity");
  return resAge == oldAge;
}

template<class E, unsigned int N>
GenericTaskQueue<E, N>::~GenericTaskQueue() {
  FREE_C_HEAP_ARRAY(E, _elems);
}

// OverflowTaskQueue is a TaskQueue that also includes an overflow stack for
// elements that do not fit in the TaskQueue.
//
// This class hides two methods from super classes:
//
// push() - push onto the task queue or, if that fails, onto the overflow stack
// is_empty() - return true if both the TaskQueue and overflow stack are empty
//
// Note that size() is not hidden--it returns the number of elements in the
// TaskQueue, and does not include the size of the overflow stack.  This
// simplifies replacement of GenericTaskQueues with OverflowTaskQueues.
template<class E, unsigned int N = TASKQUEUE_SIZE>
class OverflowTaskQueue: public GenericTaskQueue<E, N>
{
public:
  typedef Stack<E>               overflow_t;
  typedef GenericTaskQueue<E, N> taskqueue_t;

  TASKQUEUE_STATS_ONLY(using taskqueue_t::stats;)

  // Push task t onto the queue or onto the overflow stack.  Return true.
  inline bool push(E t);

  // Attempt to pop from the overflow stack; return true if anything was popped.
  inline bool pop_overflow(E& t);

  inline overflow_t* overflow_stack() { return &_overflow_stack; }

  inline bool taskqueue_empty() const { return taskqueue_t::is_empty(); }
  inline bool overflow_empty()  const { return _overflow_stack.is_empty(); }
  inline bool is_empty()        const {
    return taskqueue_empty() && overflow_empty();
  }

private:
  overflow_t _overflow_stack;
};

template <class E, unsigned int N>
bool OverflowTaskQueue<E, N>::push(E t)
{
  if (!taskqueue_t::push(t)) {
    overflow_stack()->push(t);
    TASKQUEUE_STATS_ONLY(stats.record_overflow(overflow_stack()->size()));
  }
  return true;
}

template <class E, unsigned int N>
bool OverflowTaskQueue<E, N>::pop_overflow(E& t)
{
  if (overflow_empty()) return false;
  t = overflow_stack()->pop();
  return true;
}

class TaskQueueSetSuper: public CHeapObj {
protected:
  static int randomParkAndMiller(int* seed0);
public:
  // Returns "true" if some TaskQueue in the set contains a task.
  virtual bool peek() = 0;
};

template<class T>
class GenericTaskQueueSet: public TaskQueueSetSuper {
private:
  uint _n;
  T** _queues;

public:
  typedef typename T::element_type E;

  GenericTaskQueueSet(int n) : _n(n) {
    typedef T* GenericTaskQueuePtr;
    _queues = NEW_C_HEAP_ARRAY(GenericTaskQueuePtr, n);
    for (int i = 0; i < n; i++) {
      _queues[i] = NULL;
    }
  }

  bool steal_1_random(uint queue_num, int* seed, E& t);
  bool steal_best_of_2(uint queue_num, int* seed, E& t);
  bool steal_best_of_all(uint queue_num, int* seed, E& t);

  void register_queue(uint i, T* q);

  T* queue(uint n);

  // The thread with queue number "queue_num" (and whose random number seed is
  // at "seed") is trying to steal a task from some other queue.  (It may try
  // several queues, according to some configuration parameter.)  If some steal
  // succeeds, returns "true" and sets "t" to the stolen task, otherwise returns
  // false.
  bool steal(uint queue_num, int* seed, E& t);

  bool peek();
};

template<class T> void
GenericTaskQueueSet<T>::register_queue(uint i, T* q) {
  assert(i < _n, "index out of range.");
  _queues[i] = q;
}

template<class T> T*
GenericTaskQueueSet<T>::queue(uint i) {
  return _queues[i];
}

template<class T> bool
GenericTaskQueueSet<T>::steal(uint queue_num, int* seed, E& t) {
  for (uint i = 0; i < 2 * _n; i++) {
    if (steal_best_of_2(queue_num, seed, t)) {
      TASKQUEUE_STATS_ONLY(queue(queue_num)->stats.record_steal(true));
      return true;
    }
  }
  TASKQUEUE_STATS_ONLY(queue(queue_num)->stats.record_steal(false));
  return false;
}

template<class T> bool
GenericTaskQueueSet<T>::steal_best_of_all(uint queue_num, int* seed, E& t) {
  if (_n > 2) {
    int best_k;
    uint best_sz = 0;
    for (uint k = 0; k < _n; k++) {
      if (k == queue_num) continue;
      uint sz = _queues[k]->size();
      if (sz > best_sz) {
        best_sz = sz;
        best_k = k;
      }
    }
    return best_sz > 0 && _queues[best_k]->pop_global(t);
  } else if (_n == 2) {
    // Just try the other one.
    int k = (queue_num + 1) % 2;
    return _queues[k]->pop_global(t);
  } else {
    assert(_n == 1, "can't be zero.");
    return false;
  }
}

template<class T> bool
GenericTaskQueueSet<T>::steal_1_random(uint queue_num, int* seed, E& t) {
  if (_n > 2) {
    uint k = queue_num;
    while (k == queue_num) k = randomParkAndMiller(seed) % _n;
    return _queues[2]->pop_global(t);
  } else if (_n == 2) {
    // Just try the other one.
    int k = (queue_num + 1) % 2;
    return _queues[k]->pop_global(t);
  } else {
    assert(_n == 1, "can't be zero.");
    return false;
  }
}

template<class T> bool
GenericTaskQueueSet<T>::steal_best_of_2(uint queue_num, int* seed, E& t) {
  if (_n > 2) {
    uint k1 = queue_num;
    while (k1 == queue_num) k1 = randomParkAndMiller(seed) % _n;
    uint k2 = queue_num;
    while (k2 == queue_num || k2 == k1) k2 = randomParkAndMiller(seed) % _n;
    // Sample both and try the larger.
    uint sz1 = _queues[k1]->size();
    uint sz2 = _queues[k2]->size();
    if (sz2 > sz1) return _queues[k2]->pop_global(t);
    else return _queues[k1]->pop_global(t);
  } else if (_n == 2) {
    // Just try the other one.
    uint k = (queue_num + 1) % 2;
    return _queues[k]->pop_global(t);
  } else {
    assert(_n == 1, "can't be zero.");
    return false;
  }
}

template<class T>
bool GenericTaskQueueSet<T>::peek() {
  // Try all the queues.
  for (uint j = 0; j < _n; j++) {
    if (_queues[j]->peek())
      return true;
  }
  return false;
}

// When to terminate from the termination protocol.
class TerminatorTerminator: public CHeapObj {
public:
  virtual bool should_exit_termination() = 0;
};

// A class to aid in the termination of a set of parallel tasks using
// TaskQueueSet's for work stealing.

#undef TRACESPINNING

class ParallelTaskTerminator: public StackObj {
private:
  int _n_threads;
  TaskQueueSetSuper* _queue_set;
  int _offered_termination;

#ifdef TRACESPINNING
  static uint _total_yields;
  static uint _total_spins;
  static uint _total_peeks;
#endif

  bool peek_in_queue_set();
protected:
  virtual void yield();
  void sleep(uint millis);

public:

  // "n_threads" is the number of threads to be terminated.  "queue_set" is a
  // queue sets of work queues of other threads.
  ParallelTaskTerminator(int n_threads, TaskQueueSetSuper* queue_set);

  // The current thread has no work, and is ready to terminate if everyone
  // else is.  If returns "true", all threads are terminated.  If returns
  // "false", available work has been observed in one of the task queues,
  // so the global task is not complete.
  bool offer_termination() {
    return offer_termination(NULL);
  }

  // As above, but it also terminates if the should_exit_termination()
  // method of the terminator parameter returns true. If terminator is
  // NULL, then it is ignored.
  bool offer_termination(TerminatorTerminator* terminator);

  // Reset the terminator, so that it may be reused again.
  // The caller is responsible for ensuring that this is done
  // in an MT-safe manner, once the previous round of use of
  // the terminator is finished.
  void reset_for_reuse();
  // Same as above but the number of parallel threads is set to the
  // given number.
  void reset_for_reuse(int n_threads);

#ifdef TRACESPINNING
  static uint total_yields() { return _total_yields; }
  static uint total_spins() { return _total_spins; }
  static uint total_peeks() { return _total_peeks; }
  static void print_termination_counts();
#endif
};

template<class E, unsigned int N> inline bool
GenericTaskQueue<E, N>::push(E t) {
  uint localBot = _bottom;
  assert((localBot >= 0) && (localBot < N), "_bottom out of range.");
  idx_t top = _age.top();
  uint dirty_n_elems = dirty_size(localBot, top);
  assert(dirty_n_elems < N, "n_elems out of range.");
  if (dirty_n_elems < max_elems()) {
    // g++ complains if the volatile result of the assignment is unused.
    const_cast<E&>(_elems[localBot] = t);
    OrderAccess::release_store(&_bottom, increment_index(localBot));
    TASKQUEUE_STATS_ONLY(stats.record_push());
    return true;
  } else {
    return push_slow(t, dirty_n_elems);
  }
}

template<class E, unsigned int N> inline bool
GenericTaskQueue<E, N>::pop_local(E& t) {
  uint localBot = _bottom;
  // This value cannot be N-1.  That can only occur as a result of
  // the assignment to bottom in this method.  If it does, this method
  // resets the size to 0 before the next call (which is sequential,
  // since this is pop_local.)
  uint dirty_n_elems = dirty_size(localBot, _age.top());
  assert(dirty_n_elems != N - 1, "Shouldn't be possible...");
  if (dirty_n_elems == 0) return false;
  localBot = decrement_index(localBot);
  _bottom = localBot;
  // This is necessary to prevent any read below from being reordered
  // before the store just above.
  OrderAccess::fence();
  const_cast<E&>(t = _elems[localBot]);
  // This is a second read of "age"; the "size()" above is the first.
  // If there's still at least one element in the queue, based on the
  // "_bottom" and "age" we've read, then there can be no interference with
  // a "pop_global" operation, and we're done.
  idx_t tp = _age.top();    // XXX
  if (size(localBot, tp) > 0) {
    assert(dirty_size(localBot, tp) != N - 1, "sanity");
    TASKQUEUE_STATS_ONLY(stats.record_pop());
    return true;
  } else {
    // Otherwise, the queue contained exactly one element; we take the slow
    // path.
    return pop_local_slow(localBot, _age.get());
  }
}

typedef GenericTaskQueue<oop>             OopTaskQueue;
typedef GenericTaskQueueSet<OopTaskQueue> OopTaskQueueSet;

#ifdef _MSC_VER
#pragma warning(push)
// warning C4522: multiple assignment operators specified
#pragma warning(disable:4522)
#endif

// This is a container class for either an oop* or a narrowOop*.
// Both are pushed onto a task queue and the consumer will test is_narrow()
// to determine which should be processed.
class StarTask {
  void*  _holder;        // either union oop* or narrowOop*

  enum { COMPRESSED_OOP_MASK = 1 };

 public:
  StarTask(narrowOop* p) {
    assert(((uintptr_t)p & COMPRESSED_OOP_MASK) == 0, "Information loss!");
    _holder = (void *)((uintptr_t)p | COMPRESSED_OOP_MASK);
  }
  StarTask(oop* p)       {
    assert(((uintptr_t)p & COMPRESSED_OOP_MASK) == 0, "Information loss!");
    _holder = (void*)p;
  }
  StarTask()             { _holder = NULL; }
  operator oop*()        { return (oop*)_holder; }
  operator narrowOop*()  {
    return (narrowOop*)((uintptr_t)_holder & ~COMPRESSED_OOP_MASK);
  }

  StarTask& operator=(const StarTask& t) {
    _holder = t._holder;
    return *this;
  }
  volatile StarTask& operator=(const volatile StarTask& t) volatile {
    _holder = t._holder;
    return *this;
  }

  bool is_narrow() const {
    return (((uintptr_t)_holder & COMPRESSED_OOP_MASK) != 0);
  }
};

class ObjArrayTask
{
public:
  ObjArrayTask(oop o = NULL, int idx = 0): _obj(o), _index(idx) { }
  ObjArrayTask(oop o, size_t idx): _obj(o), _index(int(idx)) {
    assert(idx <= size_t(max_jint), "too big");
  }
  ObjArrayTask(const ObjArrayTask& t): _obj(t._obj), _index(t._index) { }

  ObjArrayTask& operator =(const ObjArrayTask& t) {
    _obj = t._obj;
    _index = t._index;
    return *this;
  }
  volatile ObjArrayTask&
  operator =(const volatile ObjArrayTask& t) volatile {
    _obj = t._obj;
    _index = t._index;
    return *this;
  }

  inline oop obj()   const { return _obj; }
  inline int index() const { return _index; }

  DEBUG_ONLY(bool is_valid() const); // Tasks to be pushed/popped must be valid.

private:
  oop _obj;
  int _index;
};

#ifdef _MSC_VER
#pragma warning(pop)
#endif

typedef OverflowTaskQueue<StarTask>           OopStarTaskQueue;
typedef GenericTaskQueueSet<OopStarTaskQueue> OopStarTaskQueueSet;

typedef OverflowTaskQueue<size_t>             RegionTaskQueue;
typedef GenericTaskQueueSet<RegionTaskQueue>  RegionTaskQueueSet;


#endif // SHARE_VM_UTILITIES_TASKQUEUE_HPP
