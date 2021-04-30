/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/z/zDriverPort.hpp"
#include "gc/z/zFuture.inline.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "utilities/debug.hpp"

class ZDriverRequest {
  friend class ZList<ZDriverRequest>;

private:
  const GCCause::Cause      _message;
  uint64_t                  _seqnum;
  ZFuture<GCCause::Cause>   _result;
  ZListNode<ZDriverRequest> _node;

public:
  ZDriverRequest(GCCause::Cause message) :
      _message(message),
      _seqnum(0) {}

  void set_seqnum(uint64_t seqnum) {
    _seqnum = seqnum;
  }

  uint64_t seqnum() const {
    return _seqnum;
  }

  GCCause::Cause message() const {
    return _message;
  }

  void wait() {
    const GCCause::Cause message = _result.get();
    assert(message == _message, "Message mismatch");
  }

  void satisfy(GCCause::Cause message) {
    _result.set(message);
  }
};

ZDriverPort::ZDriverPort() :
    _lock(),
    _has_message(false),
    _seqnum(0),
    _queue() {}

void ZDriverPort::send_sync(GCCause::Cause message) {
  ZDriverRequest request(message);

  {
    // Enqueue message
    ZLocker<ZConditionLock> locker(&_lock);
    request.set_seqnum(_seqnum);
    _queue.insert_last(&request);
    _lock.notify();
  }

  // Wait for completion
  request.wait();

  {
    // Guard deletion of underlying semaphore. This is a workaround for a
    // bug in sem_post() in glibc < 2.21, where it's not safe to destroy
    // the semaphore immediately after returning from sem_wait(). The
    // reason is that sem_post() can touch the semaphore after a waiting
    // thread have returned from sem_wait(). To avoid this race we are
    // forcing the waiting thread to acquire/release the lock held by the
    // posting thread. https://sourceware.org/bugzilla/show_bug.cgi?id=12674
    ZLocker<ZConditionLock> locker(&_lock);
  }
}

void ZDriverPort::send_async(GCCause::Cause message) {
  ZLocker<ZConditionLock> locker(&_lock);
  if (!_has_message) {
    // Post message
    _message = message;
    _has_message = true;
    _lock.notify();
  }
}

GCCause::Cause ZDriverPort::receive() {
  ZLocker<ZConditionLock> locker(&_lock);

  // Wait for message
  while (!_has_message && _queue.is_empty()) {
    _lock.wait();
  }

  // Increment request sequence number
  _seqnum++;

  if (!_has_message) {
    // Message available in the queue
    _message = _queue.first()->message();
    _has_message = true;
  }

  return _message;
}

void ZDriverPort::ack() {
  ZLocker<ZConditionLock> locker(&_lock);

  if (!_has_message) {
    // Nothing to ack
    return;
  }

  // Satisfy requests (and duplicates) in queue
  ZListIterator<ZDriverRequest> iter(&_queue);
  for (ZDriverRequest* request; iter.next(&request);) {
    if (request->message() == _message && request->seqnum() < _seqnum) {
      // Dequeue and satisfy request. Note that the dequeue operation must
      // happen first, since the request will immediately be deallocated
      // once it has been satisfied.
      _queue.remove(request);
      request->satisfy(_message);
    }
  }

  if (_queue.is_empty()) {
    // Queue is empty
    _has_message = false;
  } else {
    // Post first message in queue
    _message = _queue.first()->message();
  }
}
