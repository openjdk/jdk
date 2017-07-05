/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_jniHandles.cpp.incl"


JNIHandleBlock* JNIHandles::_global_handles       = NULL;
JNIHandleBlock* JNIHandles::_weak_global_handles  = NULL;
oop             JNIHandles::_deleted_handle       = NULL;


jobject JNIHandles::make_local(oop obj) {
  if (obj == NULL) {
    return NULL;                // ignore null handles
  } else {
    Thread* thread = Thread::current();
    assert(Universe::heap()->is_in_reserved(obj), "sanity check");
    return thread->active_handles()->allocate_handle(obj);
  }
}


// optimized versions

jobject JNIHandles::make_local(Thread* thread, oop obj) {
  if (obj == NULL) {
    return NULL;                // ignore null handles
  } else {
    assert(Universe::heap()->is_in_reserved(obj), "sanity check");
    return thread->active_handles()->allocate_handle(obj);
  }
}


jobject JNIHandles::make_local(JNIEnv* env, oop obj) {
  if (obj == NULL) {
    return NULL;                // ignore null handles
  } else {
    JavaThread* thread = JavaThread::thread_from_jni_environment(env);
    assert(Universe::heap()->is_in_reserved(obj), "sanity check");
    return thread->active_handles()->allocate_handle(obj);
  }
}


jobject JNIHandles::make_global(Handle obj) {
  assert(!Universe::heap()->is_gc_active(), "can't extend the root set during GC");
  jobject res = NULL;
  if (!obj.is_null()) {
    // ignore null handles
    MutexLocker ml(JNIGlobalHandle_lock);
    assert(Universe::heap()->is_in_reserved(obj()), "sanity check");
    res = _global_handles->allocate_handle(obj());
  } else {
    CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
  }

  return res;
}


jobject JNIHandles::make_weak_global(Handle obj) {
  assert(!Universe::heap()->is_gc_active(), "can't extend the root set during GC");
  jobject res = NULL;
  if (!obj.is_null()) {
    // ignore null handles
    MutexLocker ml(JNIGlobalHandle_lock);
    assert(Universe::heap()->is_in_reserved(obj()), "sanity check");
    res = _weak_global_handles->allocate_handle(obj());
  } else {
    CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
  }
  return res;
}

jmethodID JNIHandles::make_jmethod_id(methodHandle mh) {
  return (jmethodID) make_weak_global(mh);
}



void JNIHandles::change_method_associated_with_jmethod_id(jmethodID jmid, methodHandle mh) {
  MutexLocker ml(JNIGlobalHandle_lock); // Is this necessary?
  Handle obj = (Handle)mh;
  oop* jobj = (oop*)jmid;
  *jobj = obj();
}


void JNIHandles::destroy_global(jobject handle) {
  if (handle != NULL) {
    assert(is_global_handle(handle), "Invalid delete of global JNI handle");
    *((oop*)handle) = deleted_handle(); // Mark the handle as deleted, allocate will reuse it
  }
}


void JNIHandles::destroy_weak_global(jobject handle) {
  if (handle != NULL) {
    assert(!CheckJNICalls || is_weak_global_handle(handle), "Invalid delete of weak global JNI handle");
    *((oop*)handle) = deleted_handle(); // Mark the handle as deleted, allocate will reuse it
  }
}

void JNIHandles::destroy_jmethod_id(jmethodID mid) {
  destroy_weak_global((jobject)mid);
}


void JNIHandles::oops_do(OopClosure* f) {
  f->do_oop(&_deleted_handle);
  _global_handles->oops_do(f);
}


void JNIHandles::weak_oops_do(BoolObjectClosure* is_alive, OopClosure* f) {
  _weak_global_handles->weak_oops_do(is_alive, f);
}


void JNIHandles::initialize() {
  _global_handles      = JNIHandleBlock::allocate_block();
  _weak_global_handles = JNIHandleBlock::allocate_block();
  EXCEPTION_MARK;
  // We will never reach the CATCH below since Exceptions::_throw will cause
  // the VM to exit if an exception is thrown during initialization
  klassOop k      = SystemDictionary::Object_klass();
  _deleted_handle = instanceKlass::cast(k)->allocate_permanent_instance(CATCH);
}


bool JNIHandles::is_local_handle(Thread* thread, jobject handle) {
  JNIHandleBlock* block = thread->active_handles();

  // Look back past possible native calls to jni_PushLocalFrame.
  while (block != NULL) {
    if (block->chain_contains(handle)) {
      return true;
    }
    block = block->pop_frame_link();
  }
  return false;
}


// Determine if the handle is somewhere in the current thread's stack.
// We easily can't isolate any particular stack frame the handle might
// come from, so we'll check the whole stack.

bool JNIHandles::is_frame_handle(JavaThread* thr, jobject obj) {
  // If there is no java frame, then this must be top level code, such
  // as the java command executable, in which case, this type of handle
  // is not permitted.
  return (thr->has_last_Java_frame() &&
         (void*)obj < (void*)thr->stack_base() &&
         (void*)obj >= (void*)thr->last_Java_sp());
}


bool JNIHandles::is_global_handle(jobject handle) {
  return _global_handles->chain_contains(handle);
}


bool JNIHandles::is_weak_global_handle(jobject handle) {
  return _weak_global_handles->chain_contains(handle);
}

long JNIHandles::global_handle_memory_usage() {
  return _global_handles->memory_usage();
}

long JNIHandles::weak_global_handle_memory_usage() {
  return _weak_global_handles->memory_usage();
}


class AlwaysAliveClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop obj) { return true; }
  void do_object(oop obj) { assert(false, "Don't call"); }
};

class CountHandleClosure: public OopClosure {
private:
  int _count;
public:
  CountHandleClosure(): _count(0) {}
  virtual void do_oop(oop* unused) {
    _count++;
  }
  virtual void do_oop(narrowOop* unused) { ShouldNotReachHere(); }
  int count() { return _count; }
};

// We assume this is called at a safepoint: no lock is needed.
void JNIHandles::print_on(outputStream* st) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  assert(_global_handles != NULL && _weak_global_handles != NULL,
         "JNIHandles not initialized");

  CountHandleClosure global_handle_count;
  AlwaysAliveClosure always_alive;
  oops_do(&global_handle_count);
  weak_oops_do(&always_alive, &global_handle_count);

  st->print_cr("JNI global references: %d", global_handle_count.count());
  st->cr();
  st->flush();
}

class VerifyHandleClosure: public OopClosure {
public:
  virtual void do_oop(oop* root) {
    (*root)->verify();
  }
  virtual void do_oop(narrowOop* root) { ShouldNotReachHere(); }
};

void JNIHandles::verify() {
  VerifyHandleClosure verify_handle;
  AlwaysAliveClosure always_alive;

  oops_do(&verify_handle);
  weak_oops_do(&always_alive, &verify_handle);
}



void jni_handles_init() {
  JNIHandles::initialize();
}


int             JNIHandleBlock::_blocks_allocated     = 0;
JNIHandleBlock* JNIHandleBlock::_block_free_list      = NULL;
#ifndef PRODUCT
JNIHandleBlock* JNIHandleBlock::_block_list           = NULL;
#endif


void JNIHandleBlock::zap() {
  // Zap block values
  _top  = 0;
  for (int index = 0; index < block_size_in_oops; index++) {
    _handles[index] = badJNIHandle;
  }
}

JNIHandleBlock* JNIHandleBlock::allocate_block(Thread* thread)  {
  assert(thread == NULL || thread == Thread::current(), "sanity check");
  JNIHandleBlock* block;
  // Check the thread-local free list for a block so we don't
  // have to acquire a mutex.
  if (thread != NULL && thread->free_handle_block() != NULL) {
    block = thread->free_handle_block();
    thread->set_free_handle_block(block->_next);
  }
  else {
    // locking with safepoint checking introduces a potential deadlock:
    // - we would hold JNIHandleBlockFreeList_lock and then Threads_lock
    // - another would hold Threads_lock (jni_AttachCurrentThread) and then
    //   JNIHandleBlockFreeList_lock (JNIHandleBlock::allocate_block)
    MutexLockerEx ml(JNIHandleBlockFreeList_lock,
                     Mutex::_no_safepoint_check_flag);
    if (_block_free_list == NULL) {
      // Allocate new block
      block = new JNIHandleBlock();
      _blocks_allocated++;
      if (TraceJNIHandleAllocation) {
        tty->print_cr("JNIHandleBlock " INTPTR_FORMAT " allocated (%d total blocks)",
                      block, _blocks_allocated);
      }
      if (ZapJNIHandleArea) block->zap();
      #ifndef PRODUCT
      // Link new block to list of all allocated blocks
      block->_block_list_link = _block_list;
      _block_list = block;
      #endif
    } else {
      // Get block from free list
      block = _block_free_list;
      _block_free_list = _block_free_list->_next;
    }
  }
  block->_top  = 0;
  block->_next = NULL;
  block->_pop_frame_link = NULL;
  // _last, _free_list & _allocate_before_rebuild initialized in allocate_handle
  debug_only(block->_last = NULL);
  debug_only(block->_free_list = NULL);
  debug_only(block->_allocate_before_rebuild = -1);
  return block;
}


void JNIHandleBlock::release_block(JNIHandleBlock* block, Thread* thread) {
  assert(thread == NULL || thread == Thread::current(), "sanity check");
  JNIHandleBlock* pop_frame_link = block->pop_frame_link();
  // Put returned block at the beginning of the thread-local free list.
  // Note that if thread == NULL, we use it as an implicit argument that
  // we _don't_ want the block to be kept on the free_handle_block.
  // See for instance JavaThread::exit().
  if (thread != NULL ) {
    if (ZapJNIHandleArea) block->zap();
    JNIHandleBlock* freelist = thread->free_handle_block();
    block->_pop_frame_link = NULL;
    thread->set_free_handle_block(block);

    // Add original freelist to end of chain
    if ( freelist != NULL ) {
      while ( block->_next != NULL ) block = block->_next;
      block->_next = freelist;
    }
    block = NULL;
  }
  if (block != NULL) {
    // Return blocks to free list
    // locking with safepoint checking introduces a potential deadlock:
    // - we would hold JNIHandleBlockFreeList_lock and then Threads_lock
    // - another would hold Threads_lock (jni_AttachCurrentThread) and then
    //   JNIHandleBlockFreeList_lock (JNIHandleBlock::allocate_block)
    MutexLockerEx ml(JNIHandleBlockFreeList_lock,
                     Mutex::_no_safepoint_check_flag);
    while (block != NULL) {
      if (ZapJNIHandleArea) block->zap();
      JNIHandleBlock* next = block->_next;
      block->_next = _block_free_list;
      _block_free_list = block;
      block = next;
    }
  }
  if (pop_frame_link != NULL) {
    // As a sanity check we release blocks pointed to by the pop_frame_link.
    // This should never happen (only if PopLocalFrame is not called the
    // correct number of times).
    release_block(pop_frame_link, thread);
  }
}


void JNIHandleBlock::oops_do(OopClosure* f) {
  JNIHandleBlock* current_chain = this;
  // Iterate over chain of blocks, followed by chains linked through the
  // pop frame links.
  while (current_chain != NULL) {
    for (JNIHandleBlock* current = current_chain; current != NULL;
         current = current->_next) {
      assert(current == current_chain || current->pop_frame_link() == NULL,
        "only blocks first in chain should have pop frame link set");
      for (int index = 0; index < current->_top; index++) {
        oop* root = &(current->_handles)[index];
        oop value = *root;
        // traverse heap pointers only, not deleted handles or free list
        // pointers
        if (value != NULL && Universe::heap()->is_in_reserved(value)) {
          f->do_oop(root);
        }
      }
      // the next handle block is valid only if current block is full
      if (current->_top < block_size_in_oops) {
        break;
      }
    }
    current_chain = current_chain->pop_frame_link();
  }
}


void JNIHandleBlock::weak_oops_do(BoolObjectClosure* is_alive,
                                  OopClosure* f) {
  for (JNIHandleBlock* current = this; current != NULL; current = current->_next) {
    assert(current->pop_frame_link() == NULL,
      "blocks holding weak global JNI handles should not have pop frame link set");
    for (int index = 0; index < current->_top; index++) {
      oop* root = &(current->_handles)[index];
      oop value = *root;
      // traverse heap pointers only, not deleted handles or free list pointers
      if (value != NULL && Universe::heap()->is_in_reserved(value)) {
        if (is_alive->do_object_b(value)) {
          // The weakly referenced object is alive, update pointer
          f->do_oop(root);
        } else {
          // The weakly referenced object is not alive, clear the reference by storing NULL
          if (TraceReferenceGC) {
            tty->print_cr("Clearing JNI weak reference (" INTPTR_FORMAT ")", root);
          }
          *root = NULL;
        }
      }
    }
    // the next handle block is valid only if current block is full
    if (current->_top < block_size_in_oops) {
      break;
    }
  }
}


jobject JNIHandleBlock::allocate_handle(oop obj) {
  assert(Universe::heap()->is_in_reserved(obj), "sanity check");
  if (_top == 0) {
    // This is the first allocation or the initial block got zapped when
    // entering a native function. If we have any following blocks they are
    // not valid anymore.
    for (JNIHandleBlock* current = _next; current != NULL;
         current = current->_next) {
      assert(current->_last == NULL, "only first block should have _last set");
      assert(current->_free_list == NULL,
             "only first block should have _free_list set");
      current->_top = 0;
      if (ZapJNIHandleArea) current->zap();
    }
    // Clear initial block
    _free_list = NULL;
    _allocate_before_rebuild = 0;
    _last = this;
    if (ZapJNIHandleArea) zap();
  }

  // Try last block
  if (_last->_top < block_size_in_oops) {
    oop* handle = &(_last->_handles)[_last->_top++];
    *handle = obj;
    return (jobject) handle;
  }

  // Try free list
  if (_free_list != NULL) {
    oop* handle = _free_list;
    _free_list = (oop*) *_free_list;
    *handle = obj;
    return (jobject) handle;
  }
  // Check if unused block follow last
  if (_last->_next != NULL) {
    // update last and retry
    _last = _last->_next;
    return allocate_handle(obj);
  }

  // No space available, we have to rebuild free list or expand
  if (_allocate_before_rebuild == 0) {
      rebuild_free_list();        // updates _allocate_before_rebuild counter
  } else {
    // Append new block
    Thread* thread = Thread::current();
    Handle obj_handle(thread, obj);
    // This can block, so we need to preserve obj accross call.
    _last->_next = JNIHandleBlock::allocate_block(thread);
    _last = _last->_next;
    _allocate_before_rebuild--;
    obj = obj_handle();
  }
  return allocate_handle(obj);  // retry
}


void JNIHandleBlock::rebuild_free_list() {
  assert(_allocate_before_rebuild == 0 && _free_list == NULL, "just checking");
  int free = 0;
  int blocks = 0;
  for (JNIHandleBlock* current = this; current != NULL; current = current->_next) {
    for (int index = 0; index < current->_top; index++) {
      oop* handle = &(current->_handles)[index];
      if (*handle ==  JNIHandles::deleted_handle()) {
        // this handle was cleared out by a delete call, reuse it
        *handle = (oop) _free_list;
        _free_list = handle;
        free++;
      }
    }
    // we should not rebuild free list if there are unused handles at the end
    assert(current->_top == block_size_in_oops, "just checking");
    blocks++;
  }
  // Heuristic: if more than half of the handles are free we rebuild next time
  // as well, otherwise we append a corresponding number of new blocks before
  // attempting a free list rebuild again.
  int total = blocks * block_size_in_oops;
  int extra = total - 2*free;
  if (extra > 0) {
    // Not as many free handles as we would like - compute number of new blocks to append
    _allocate_before_rebuild = (extra + block_size_in_oops - 1) / block_size_in_oops;
  }
  if (TraceJNIHandleAllocation) {
    tty->print_cr("Rebuild free list JNIHandleBlock " INTPTR_FORMAT " blocks=%d used=%d free=%d add=%d",
      this, blocks, total-free, free, _allocate_before_rebuild);
  }
}


bool JNIHandleBlock::contains(jobject handle) const {
  return ((jobject)&_handles[0] <= handle && handle<(jobject)&_handles[_top]);
}


bool JNIHandleBlock::chain_contains(jobject handle) const {
  for (JNIHandleBlock* current = (JNIHandleBlock*) this; current != NULL; current = current->_next) {
    if (current->contains(handle)) {
      return true;
    }
  }
  return false;
}


int JNIHandleBlock::length() const {
  int result = 1;
  for (JNIHandleBlock* current = _next; current != NULL; current = current->_next) {
    result++;
  }
  return result;
}

// This method is not thread-safe, i.e., must be called whule holding a lock on the
// structure.
long JNIHandleBlock::memory_usage() const {
  return length() * sizeof(JNIHandleBlock);
}


#ifndef PRODUCT

bool JNIHandleBlock::any_contains(jobject handle) {
  for (JNIHandleBlock* current = _block_list; current != NULL; current = current->_block_list_link) {
    if (current->contains(handle)) {
      return true;
    }
  }
  return false;
}

void JNIHandleBlock::print_statistics() {
  int used_blocks = 0;
  int free_blocks = 0;
  int used_handles = 0;
  int free_handles = 0;
  JNIHandleBlock* block = _block_list;
  while (block != NULL) {
    if (block->_top > 0) {
      used_blocks++;
    } else {
      free_blocks++;
    }
    used_handles += block->_top;
    free_handles += (block_size_in_oops - block->_top);
    block = block->_block_list_link;
  }
  tty->print_cr("JNIHandleBlocks statistics");
  tty->print_cr("- blocks allocated: %d", used_blocks + free_blocks);
  tty->print_cr("- blocks in use:    %d", used_blocks);
  tty->print_cr("- blocks free:      %d", free_blocks);
  tty->print_cr("- handles in use:   %d", used_handles);
  tty->print_cr("- handles free:     %d", free_handles);
}

#endif
