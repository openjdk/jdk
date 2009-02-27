/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifdef LP64
typedef juint TAG_TYPE;
// for a taskqueue size of 4M
#define LOG_TASKQ_SIZE 22
#else
typedef jushort TAG_TYPE;
// for a taskqueue size of 16K
#define LOG_TASKQ_SIZE 14
#endif

class TaskQueueSuper: public CHeapObj {
protected:
  // The first free element after the last one pushed (mod _n).
  volatile uint _bottom;

  // log2 of the size of the queue.
  enum SomeProtectedConstants {
    Log_n = LOG_TASKQ_SIZE
  };
#undef LOG_TASKQ_SIZE

  // Size of the queue.
  uint n() { return (1 << Log_n); }
  // For computing "x mod n" efficiently.
  uint n_mod_mask() { return n() - 1; }

  struct Age {
    TAG_TYPE _top;
    TAG_TYPE _tag;

    TAG_TYPE tag() const { return _tag; }
    TAG_TYPE top() const { return _top; }

    Age() { _tag = 0; _top = 0; }

    friend bool operator ==(const Age& a1, const Age& a2) {
      return a1.tag() == a2.tag() && a1.top() == a2.top();
    }
  };
  Age _age;
  // These make sure we do single atomic reads and writes.
  Age get_age() {
    uint res = *(volatile uint*)(&_age);
    return *(Age*)(&res);
  }
  void set_age(Age a) {
    *(volatile uint*)(&_age) = *(uint*)(&a);
  }

  TAG_TYPE get_top() {
    return get_age().top();
  }

  // These both operate mod _n.
  uint increment_index(uint ind) {
    return (ind + 1) & n_mod_mask();
  }
  uint decrement_index(uint ind) {
    return (ind - 1) & n_mod_mask();
  }

  // Returns a number in the range [0.._n).  If the result is "n-1", it
  // should be interpreted as 0.
  uint dirty_size(uint bot, uint top) {
    return ((int)bot - (int)top) & n_mod_mask();
  }

  // Returns the size corresponding to the given "bot" and "top".
  uint size(uint bot, uint top) {
    uint sz = dirty_size(bot, top);
    // Has the queue "wrapped", so that bottom is less than top?
    // There's a complicated special case here.  A pair of threads could
    // perform pop_local and pop_global operations concurrently, starting
    // from a state in which _bottom == _top+1.  The pop_local could
    // succeed in decrementing _bottom, and the pop_global in incrementing
    // _top (in which case the pop_global will be awarded the contested
    // queue element.)  The resulting state must be interpreted as an empty
    // queue.  (We only need to worry about one such event: only the queue
    // owner performs pop_local's, and several concurrent threads
    // attempting to perform the pop_global will all perform the same CAS,
    // and only one can succeed.  Any stealing thread that reads after
    // either the increment or decrement will see an empty queue, and will
    // not join the competitors.  The "sz == -1 || sz == _n-1" state will
    // not be modified  by concurrent queues, so the owner thread can reset
    // the state to _bottom == top so subsequent pushes will be performed
    // normally.
    if (sz == (n()-1)) return 0;
    else return sz;
  }

public:
  TaskQueueSuper() : _bottom(0), _age() {}

  // Return "true" if the TaskQueue contains any tasks.
  bool peek();

  // Return an estimate of the number of elements in the queue.
  // The "careful" version admits the possibility of pop_local/pop_global
  // races.
  uint size() {
    return size(_bottom, get_top());
  }

  uint dirty_size() {
    return dirty_size(_bottom, get_top());
  }

  void set_empty() {
    _bottom = 0;
    _age = Age();
  }

  // Maximum number of elements allowed in the queue.  This is two less
  // than the actual queue size, for somewhat complicated reasons.
  uint max_elems() { return n() - 2; }

};

template<class E> class GenericTaskQueue: public TaskQueueSuper {
private:
  // Slow paths for push, pop_local.  (pop_global has no fast path.)
  bool push_slow(E t, uint dirty_n_elems);
  bool pop_local_slow(uint localBot, Age oldAge);

public:
  // Initializes the queue to empty.
  GenericTaskQueue();

  void initialize();

  // Push the task "t" on the queue.  Returns "false" iff the queue is
  // full.
  inline bool push(E t);

  // If succeeds in claiming a task (from the 'local' end, that is, the
  // most recently pushed task), returns "true" and sets "t" to that task.
  // Otherwise, the queue is empty and returns false.
  inline bool pop_local(E& t);

  // If succeeds in claiming a task (from the 'global' end, that is, the
  // least recently pushed task), returns "true" and sets "t" to that task.
  // Otherwise, the queue is empty and returns false.
  bool pop_global(E& t);

  // Delete any resource associated with the queue.
  ~GenericTaskQueue();

  // apply the closure to all elements in the task queue
  void oops_do(OopClosure* f);

private:
  // Element array.
  volatile E* _elems;
};

template<class E>
GenericTaskQueue<E>::GenericTaskQueue():TaskQueueSuper() {
  assert(sizeof(Age) == sizeof(int), "Depends on this.");
}

template<class E>
void GenericTaskQueue<E>::initialize() {
  _elems = NEW_C_HEAP_ARRAY(E, n());
  guarantee(_elems != NULL, "Allocation failed.");
}

template<class E>
void GenericTaskQueue<E>::oops_do(OopClosure* f) {
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


template<class E>
bool GenericTaskQueue<E>::push_slow(E t, uint dirty_n_elems) {
  if (dirty_n_elems == n() - 1) {
    // Actually means 0, so do the push.
    uint localBot = _bottom;
    _elems[localBot] = t;
    _bottom = increment_index(localBot);
    return true;
  } else
    return false;
}

template<class E>
bool GenericTaskQueue<E>::
pop_local_slow(uint localBot, Age oldAge) {
  // This queue was observed to contain exactly one element; either this
  // thread will claim it, or a competing "pop_global".  In either case,
  // the queue will be logically empty afterwards.  Create a new Age value
  // that represents the empty queue for the given value of "_bottom".  (We
  // must also increment "tag" because of the case where "bottom == 1",
  // "top == 0".  A pop_global could read the queue element in that case,
  // then have the owner thread do a pop followed by another push.  Without
  // the incrementing of "tag", the pop_global's CAS could succeed,
  // allowing it to believe it has claimed the stale element.)
  Age newAge;
  newAge._top = localBot;
  newAge._tag = oldAge.tag() + 1;
  // Perhaps a competing pop_global has already incremented "top", in which
  // case it wins the element.
  if (localBot == oldAge.top()) {
    Age tempAge;
    // No competing pop_global has yet incremented "top"; we'll try to
    // install new_age, thus claiming the element.
    assert(sizeof(Age) == sizeof(int), "Assumption about CAS unit.");
    *(uint*)&tempAge = Atomic::cmpxchg(*(uint*)&newAge, (volatile uint*)&_age, *(uint*)&oldAge);
    if (tempAge == oldAge) {
      // We win.
      assert(dirty_size(localBot, get_top()) != n() - 1,
             "Shouldn't be possible...");
      return true;
    }
  }
  // We fail; a completing pop_global gets the element.  But the queue is
  // empty (and top is greater than bottom.)  Fix this representation of
  // the empty queue to become the canonical one.
  set_age(newAge);
  assert(dirty_size(localBot, get_top()) != n() - 1,
         "Shouldn't be possible...");
  return false;
}

template<class E>
bool GenericTaskQueue<E>::pop_global(E& t) {
  Age newAge;
  Age oldAge = get_age();
  uint localBot = _bottom;
  uint n_elems = size(localBot, oldAge.top());
  if (n_elems == 0) {
    return false;
  }
  t = _elems[oldAge.top()];
  newAge = oldAge;
  newAge._top = increment_index(newAge.top());
  if ( newAge._top == 0 ) newAge._tag++;
  Age resAge;
  *(uint*)&resAge = Atomic::cmpxchg(*(uint*)&newAge, (volatile uint*)&_age, *(uint*)&oldAge);
  // Note that using "_bottom" here might fail, since a pop_local might
  // have decremented it.
  assert(dirty_size(localBot, newAge._top) != n() - 1,
         "Shouldn't be possible...");
  return (resAge == oldAge);
}

template<class E>
GenericTaskQueue<E>::~GenericTaskQueue() {
  FREE_C_HEAP_ARRAY(E, _elems);
}

// Inherits the typedef of "Task" from above.
class TaskQueueSetSuper: public CHeapObj {
protected:
  static int randomParkAndMiller(int* seed0);
public:
  // Returns "true" if some TaskQueue in the set contains a task.
  virtual bool peek() = 0;
};

template<class E> class GenericTaskQueueSet: public TaskQueueSetSuper {
private:
  uint _n;
  GenericTaskQueue<E>** _queues;

public:
  GenericTaskQueueSet(int n) : _n(n) {
    typedef GenericTaskQueue<E>* GenericTaskQueuePtr;
    _queues = NEW_C_HEAP_ARRAY(GenericTaskQueuePtr, n);
    guarantee(_queues != NULL, "Allocation failure.");
    for (int i = 0; i < n; i++) {
      _queues[i] = NULL;
    }
  }

  bool steal_1_random(uint queue_num, int* seed, E& t);
  bool steal_best_of_2(uint queue_num, int* seed, E& t);
  bool steal_best_of_all(uint queue_num, int* seed, E& t);

  void register_queue(uint i, GenericTaskQueue<E>* q);

  GenericTaskQueue<E>* queue(uint n);

  // The thread with queue number "queue_num" (and whose random number seed
  // is at "seed") is trying to steal a task from some other queue.  (It
  // may try several queues, according to some configuration parameter.)
  // If some steal succeeds, returns "true" and sets "t" the stolen task,
  // otherwise returns false.
  bool steal(uint queue_num, int* seed, E& t);

  bool peek();
};

template<class E>
void GenericTaskQueueSet<E>::register_queue(uint i, GenericTaskQueue<E>* q) {
  assert(i < _n, "index out of range.");
  _queues[i] = q;
}

template<class E>
GenericTaskQueue<E>* GenericTaskQueueSet<E>::queue(uint i) {
  return _queues[i];
}

template<class E>
bool GenericTaskQueueSet<E>::steal(uint queue_num, int* seed, E& t) {
  for (uint i = 0; i < 2 * _n; i++)
    if (steal_best_of_2(queue_num, seed, t))
      return true;
  return false;
}

template<class E>
bool GenericTaskQueueSet<E>::steal_best_of_all(uint queue_num, int* seed, E& t) {
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

template<class E>
bool GenericTaskQueueSet<E>::steal_1_random(uint queue_num, int* seed, E& t) {
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

template<class E>
bool GenericTaskQueueSet<E>::steal_best_of_2(uint queue_num, int* seed, E& t) {
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

template<class E>
bool GenericTaskQueueSet<E>::peek() {
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

  // As above, but it also terminates of the should_exit_termination()
  // method of the terminator parameter returns true. If terminator is
  // NULL, then it is ignored.
  bool offer_termination(TerminatorTerminator* terminator);

  // Reset the terminator, so that it may be reused again.
  // The caller is responsible for ensuring that this is done
  // in an MT-safe manner, once the previous round of use of
  // the terminator is finished.
  void reset_for_reuse();

#ifdef TRACESPINNING
  static uint total_yields() { return _total_yields; }
  static uint total_spins() { return _total_spins; }
  static uint total_peeks() { return _total_peeks; }
  static void print_termination_counts();
#endif
};

#define SIMPLE_STACK 0

template<class E> inline bool GenericTaskQueue<E>::push(E t) {
#if SIMPLE_STACK
  uint localBot = _bottom;
  if (_bottom < max_elems()) {
    _elems[localBot] = t;
    _bottom = localBot + 1;
    return true;
  } else {
    return false;
  }
#else
  uint localBot = _bottom;
  assert((localBot >= 0) && (localBot < n()), "_bottom out of range.");
  TAG_TYPE top = get_top();
  uint dirty_n_elems = dirty_size(localBot, top);
  assert((dirty_n_elems >= 0) && (dirty_n_elems < n()),
         "n_elems out of range.");
  if (dirty_n_elems < max_elems()) {
    _elems[localBot] = t;
    _bottom = increment_index(localBot);
    return true;
  } else {
    return push_slow(t, dirty_n_elems);
  }
#endif
}

template<class E> inline bool GenericTaskQueue<E>::pop_local(E& t) {
#if SIMPLE_STACK
  uint localBot = _bottom;
  assert(localBot > 0, "precondition.");
  localBot--;
  t = _elems[localBot];
  _bottom = localBot;
  return true;
#else
  uint localBot = _bottom;
  // This value cannot be n-1.  That can only occur as a result of
  // the assignment to bottom in this method.  If it does, this method
  // resets the size( to 0 before the next call (which is sequential,
  // since this is pop_local.)
  uint dirty_n_elems = dirty_size(localBot, get_top());
  assert(dirty_n_elems != n() - 1, "Shouldn't be possible...");
  if (dirty_n_elems == 0) return false;
  localBot = decrement_index(localBot);
  _bottom = localBot;
  // This is necessary to prevent any read below from being reordered
  // before the store just above.
  OrderAccess::fence();
  t = _elems[localBot];
  // This is a second read of "age"; the "size()" above is the first.
  // If there's still at least one element in the queue, based on the
  // "_bottom" and "age" we've read, then there can be no interference with
  // a "pop_global" operation, and we're done.
  TAG_TYPE tp = get_top();    // XXX
  if (size(localBot, tp) > 0) {
    assert(dirty_size(localBot, tp) != n() - 1,
           "Shouldn't be possible...");
    return true;
  } else {
    // Otherwise, the queue contained exactly one element; we take the slow
    // path.
    return pop_local_slow(localBot, get_age());
  }
#endif
}

typedef oop Task;
typedef GenericTaskQueue<Task>         OopTaskQueue;
typedef GenericTaskQueueSet<Task>      OopTaskQueueSet;


#define COMPRESSED_OOP_MASK  1

// This is a container class for either an oop* or a narrowOop*.
// Both are pushed onto a task queue and the consumer will test is_narrow()
// to determine which should be processed.
class StarTask {
  void*  _holder;        // either union oop* or narrowOop*
 public:
  StarTask(narrowOop *p) { _holder = (void *)((uintptr_t)p | COMPRESSED_OOP_MASK); }
  StarTask(oop *p)       { _holder = (void*)p; }
  StarTask()             { _holder = NULL; }
  operator oop*()        { return (oop*)_holder; }
  operator narrowOop*()  {
    return (narrowOop*)((uintptr_t)_holder & ~COMPRESSED_OOP_MASK);
  }

  // Operators to preserve const/volatile in assignments required by gcc
  void operator=(const volatile StarTask& t) volatile { _holder = t._holder; }

  bool is_narrow() const {
    return (((uintptr_t)_holder & COMPRESSED_OOP_MASK) != 0);
  }
};

typedef GenericTaskQueue<StarTask>     OopStarTaskQueue;
typedef GenericTaskQueueSet<StarTask>  OopStarTaskQueueSet;

typedef size_t RegionTask;  // index for region
typedef GenericTaskQueue<RegionTask>    RegionTaskQueue;
typedef GenericTaskQueueSet<RegionTask> RegionTaskQueueSet;

class RegionTaskQueueWithOverflow: public CHeapObj {
 protected:
  RegionTaskQueue              _region_queue;
  GrowableArray<RegionTask>*   _overflow_stack;

 public:
  RegionTaskQueueWithOverflow() : _overflow_stack(NULL) {}
  // Initialize both stealable queue and overflow
  void initialize();
  // Save first to stealable queue and then to overflow
  void save(RegionTask t);
  // Retrieve first from overflow and then from stealable queue
  bool retrieve(RegionTask& region_index);
  // Retrieve from stealable queue
  bool retrieve_from_stealable_queue(RegionTask& region_index);
  // Retrieve from overflow
  bool retrieve_from_overflow(RegionTask& region_index);
  bool is_empty();
  bool stealable_is_empty();
  bool overflow_is_empty();
  uint stealable_size() { return _region_queue.size(); }
  RegionTaskQueue* task_queue() { return &_region_queue; }
};

#define USE_RegionTaskQueueWithOverflow
