/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_TASKQUEUE_HPP
#define SHARE_VM_GC_SHARED_TASKQUEUE_HPP

#include "memory/allocation.hpp"
#include "utilities/stack.hpp"

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

// TaskQueueSuper collects functionality common to all GenericTaskQueue instances.

template <unsigned int N, MEMFLAGS F>
class TaskQueueSuper: public CHeapObj<F> {
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

    Age cmpxchg(const Age new_age, const Age old_age) volatile;

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

//
// GenericTaskQueue implements an ABP, Aurora-Blumofe-Plaxton, double-
// ended-queue (deque), intended for use in work stealing. Queue operations
// are non-blocking.
//
// A queue owner thread performs push() and pop_local() operations on one end
// of the queue, while other threads may steal work using the pop_global()
// method.
//
// The main difference to the original algorithm is that this
// implementation allows wrap-around at the end of its allocated
// storage, which is an array.
//
// The original paper is:
//
// Arora, N. S., Blumofe, R. D., and Plaxton, C. G.
// Thread scheduling for multiprogrammed multiprocessors.
// Theory of Computing Systems 34, 2 (2001), 115-144.
//
// The following paper provides an correctness proof and an
// implementation for weakly ordered memory models including (pseudo-)
// code containing memory barriers for a Chase-Lev deque. Chase-Lev is
// similar to ABP, with the main difference that it allows resizing of the
// underlying storage:
//
// Le, N. M., Pop, A., Cohen A., and Nardell, F. Z.
// Correct and efficient work-stealing for weak memory models
// Proceedings of the 18th ACM SIGPLAN symposium on Principles and
// practice of parallel programming (PPoPP 2013), 69-80
//

template <class E, MEMFLAGS F, unsigned int N = TASKQUEUE_SIZE>
class GenericTaskQueue: public TaskQueueSuper<N, F> {
protected:
  typedef typename TaskQueueSuper<N, F>::Age Age;
  typedef typename TaskQueueSuper<N, F>::idx_t idx_t;

  using TaskQueueSuper<N, F>::_bottom;
  using TaskQueueSuper<N, F>::_age;
  using TaskQueueSuper<N, F>::increment_index;
  using TaskQueueSuper<N, F>::decrement_index;
  using TaskQueueSuper<N, F>::dirty_size;

public:
  using TaskQueueSuper<N, F>::max_elems;
  using TaskQueueSuper<N, F>::size;

#if  TASKQUEUE_STATS
  using TaskQueueSuper<N, F>::stats;
#endif

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
  inline bool pop_local(volatile E& t);

  // Like pop_local(), but uses the "global" end of the queue (the least
  // recently pushed).
  bool pop_global(volatile E& t);

  // Delete any resource associated with the queue.
  ~GenericTaskQueue();

  // Apply fn to each element in the task queue.  The queue must not
  // be modified while iterating.
  template<typename Fn> void iterate(Fn fn);

private:
  // Element array.
  volatile E* _elems;
};

template<class E, MEMFLAGS F, unsigned int N>
GenericTaskQueue<E, F, N>::GenericTaskQueue() {
  assert(sizeof(Age) == sizeof(size_t), "Depends on this.");
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
template<class E, MEMFLAGS F, unsigned int N = TASKQUEUE_SIZE>
class OverflowTaskQueue: public GenericTaskQueue<E, F, N>
{
public:
  typedef Stack<E, F>               overflow_t;
  typedef GenericTaskQueue<E, F, N> taskqueue_t;

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

class TaskQueueSetSuper {
protected:
  static int randomParkAndMiller(int* seed0);
public:
  // Returns "true" if some TaskQueue in the set contains a task.
  virtual bool peek() = 0;
};

template <MEMFLAGS F> class TaskQueueSetSuperImpl: public CHeapObj<F>, public TaskQueueSetSuper {
};

template<class T, MEMFLAGS F>
class GenericTaskQueueSet: public TaskQueueSetSuperImpl<F> {
private:
  uint _n;
  T** _queues;

public:
  typedef typename T::element_type E;

  GenericTaskQueueSet(int n);

  bool steal_best_of_2(uint queue_num, int* seed, E& t);

  void register_queue(uint i, T* q);

  T* queue(uint n);

  // The thread with queue number "queue_num" (and whose random number seed is
  // at "seed") is trying to steal a task from some other queue.  (It may try
  // several queues, according to some configuration parameter.)  If some steal
  // succeeds, returns "true" and sets "t" to the stolen task, otherwise returns
  // false.
  bool steal(uint queue_num, int* seed, E& t);

  bool peek();

  uint size() const { return _n; }
};

template<class T, MEMFLAGS F> void
GenericTaskQueueSet<T, F>::register_queue(uint i, T* q) {
  assert(i < _n, "index out of range.");
  _queues[i] = q;
}

template<class T, MEMFLAGS F> T*
GenericTaskQueueSet<T, F>::queue(uint i) {
  return _queues[i];
}

template<class T, MEMFLAGS F>
bool GenericTaskQueueSet<T, F>::peek() {
  // Try all the queues.
  for (uint j = 0; j < _n; j++) {
    if (_queues[j]->peek())
      return true;
  }
  return false;
}

// When to terminate from the termination protocol.
class TerminatorTerminator: public CHeapObj<mtInternal> {
public:
  virtual bool should_exit_termination() = 0;
};

// A class to aid in the termination of a set of parallel tasks using
// TaskQueueSet's for work stealing.

#undef TRACESPINNING

class ParallelTaskTerminator: public StackObj {
private:
  uint _n_threads;
  TaskQueueSetSuper* _queue_set;
  uint _offered_termination;

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
  ParallelTaskTerminator(uint n_threads, TaskQueueSetSuper* queue_set);

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
  void reset_for_reuse(uint n_threads);

#ifdef TRACESPINNING
  static uint total_yields() { return _total_yields; }
  static uint total_spins() { return _total_spins; }
  static uint total_peeks() { return _total_peeks; }
  static void print_termination_counts();
#endif
};

typedef GenericTaskQueue<oop, mtGC>             OopTaskQueue;
typedef GenericTaskQueueSet<OopTaskQueue, mtGC> OopTaskQueueSet;

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
    (void)const_cast<oop&>(_obj = t._obj);
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

typedef OverflowTaskQueue<StarTask, mtClass>           OopStarTaskQueue;
typedef GenericTaskQueueSet<OopStarTaskQueue, mtClass> OopStarTaskQueueSet;

typedef OverflowTaskQueue<size_t, mtInternal>             RegionTaskQueue;
typedef GenericTaskQueueSet<RegionTaskQueue, mtClass>     RegionTaskQueueSet;


#endif // SHARE_VM_GC_SHARED_TASKQUEUE_HPP
