/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zDriverPort.hpp"
#include "gc/z/zFuture.inline.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "utilities/debug.hpp"

ZDriverRequest::ZDriverRequest()
  : ZDriverRequest(GCCause::_no_gc, 0, 0) {}

ZDriverRequest::ZDriverRequest(GCCause::Cause cause, uint young_nworkers, uint old_nworkers)
  : _cause(cause),
    _young_nworkers(young_nworkers),
    _old_nworkers(old_nworkers) {}

bool ZDriverRequest::operator==(const ZDriverRequest& other) const {
  return _cause == other._cause;
}

GCCause::Cause ZDriverRequest::cause() const {
  return _cause;
}

uint ZDriverRequest::young_nworkers() const {
  return _young_nworkers;
}

uint ZDriverRequest::old_nworkers() const {
  return _old_nworkers;
}

class ZDriverPortEntry {
  friend class ZList<ZDriverPortEntry>;

private:
  const ZDriverRequest        _message;
  uint64_t                    _seqnum;
  ZFuture<ZDriverRequest>     _result;
  ZListNode<ZDriverPortEntry> _node;

public:
  ZDriverPortEntry(const ZDriverRequest& message)
    : _message(message),
      _seqnum(0) {}

  void set_seqnum(uint64_t seqnum) {
    _seqnum = seqnum;
  }

  uint64_t seqnum() const {
    return _seqnum;
  }

  ZDriverRequest message() const {
    return _message;
  }

  void wait() {
    const ZDriverRequest message = _result.get();
    assert(message == _message, "Message mismatch");
  }

  void satisfy(const ZDriverRequest& message) {
    _result.set(message);
  }
};

ZDriverPort::ZDriverPort()
  : _lock(),
    _has_message(false),
    _seqnum(0),
    _queue() {}

bool ZDriverPort::is_busy() const {
  ZLocker<ZConditionLock> locker(&_lock);
  return _has_message;
}

void ZDriverPort::send_sync(const ZDriverRequest& message) {
  ZDriverPortEntry entry(message);

  {
    // Enqueue message
    ZLocker<ZConditionLock> locker(&_lock);
    entry.set_seqnum(_seqnum);
    _queue.insert_last(&entry);
    _lock.notify();
  }

  // Wait for completion
  entry.wait();

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

void ZDriverPort::send_async(const ZDriverRequest& message) {
  ZLocker<ZConditionLock> locker(&_lock);
  if (!_has_message) {
    // Post message
    _message = message;
    _has_message = true;
    _lock.notify();
  }
}

ZDriverRequest ZDriverPort::receive() {
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
  ZListIterator<ZDriverPortEntry> iter(&_queue);
  for (ZDriverPortEntry* entry; iter.next(&entry);) {
    if (entry->message() == _message && entry->seqnum() < _seqnum) {
      // Dequeue and satisfy request. Note that the dequeue operation must
      // happen first, since the request will immediately be deallocated
      // once it has been satisfied.
      _queue.remove(entry);
      entry->satisfy(_message);
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
