/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/circularStringBuffer.hpp"
#include "runtime/os.inline.hpp"

// LogDecorator::None applies to 'constant initialization' because of its constexpr constructor.
const LogDecorations& CircularStringBuffer::None = LogDecorations(
    LogLevel::Warning, LogTagSetMapping<LogTag::__NO_TAG>::tagset(), LogDecorators::None);

const char* allocation_failure_msg = "Failed to allocate async logging buffer";

#ifdef LINUX
CircularMapping::CircularMapping(size_t size)
  : size(size) {
  assert(is_aligned(size, os::vm_page_size()), "must be");
  file = tmpfile();
  if (file == nullptr) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }
  const int fd = fileno(file);
  if (fd == -1) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }
  int ret = ftruncate(fd, size);
  if (ret != 0) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }
  buffer = (char*)mmap(nullptr, size * 2, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (buffer == MAP_FAILED) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }
  void* mmap_ret = mmap(buffer, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_FIXED, fd, 0);
  if (mmap_ret == MAP_FAILED) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }
  mmap_ret = mmap(buffer + size, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_FIXED, fd, 0);
  if (mmap_ret == MAP_FAILED) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }

  // Success, notify NMT.
  MemTracker::record_virtual_memory_reserve(buffer, size, CURRENT_PC, mtLogging);
  MemTracker::record_virtual_memory_commit(buffer, size, CURRENT_PC);
}
#else
CircularMapping::CircularMapping(size_t size)
  : buffer(nullptr),
    size(size) {
  buffer = os::reserve_memory(size, false, mtLogging);
  if (buffer == nullptr) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }
  bool ret = os::commit_memory(buffer, size, false);
  if (!ret) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", allocation_failure_msg);
  }
}
#endif // LINUX


CircularStringBuffer::CircularStringBuffer(StatisticsMap& map, PlatformMonitor& stats_lock, size_t size, bool should_stall)
  : _should_stall(should_stall),
    _stats(map),
    _stats_lock(stats_lock),
    circular_mapping(size),
    tail(0),
    head(0) {}

size_t CircularStringBuffer::used() {
  size_t h = Atomic::load(&head);
  size_t t = Atomic::load(&tail);
  if (h <= t) {
    return t - h;
  } else {
    return circular_mapping.size - (h - t);
  }
}
size_t CircularStringBuffer::unused() {
  return circular_mapping.size - used();
}

size_t CircularStringBuffer::calc_mem(size_t sz) {
  return align_up(sz, alignof(Message));
}

// Size including NUL byte
void CircularStringBuffer::enqueue_locked(const char* str, size_t size, LogFileStreamOutput* output,
                                   const LogDecorations decorations) {
  const size_t required_memory = calc_mem(size);
  size_t unused = this->unused();
  auto not_enough_memory = [&]() {
    return unused < (required_memory + sizeof(Message)*(output == nullptr ? 1 : 2));
  };
  // We need space for an additional Message in case of a flush token
  assert(!(output == nullptr) || unused >= sizeof(Message), "invariant");
  if (not_enough_memory()) {
    if (_should_stall) {
      while (not_enough_memory()) {
        _write_lock.wait(0);
        unused = this->unused();
      }
    } else {
      _stats_lock.lock();
      bool p_created;
      uint32_t* counter = _stats.put_if_absent(output, 0, &p_created);
      *counter = *counter + 1;
      _stats_lock.unlock();
      return;
    }
  }
  // Load the tail.
  size_t t = tail;
  // Write the Message
  Message msg{required_memory, output, decorations};
  circular_mapping.write_bytes(t, (char*)&msg, sizeof(Message));
  // Move t forward
  t = (t +  sizeof(Message)) % circular_mapping.size;
  // Write the string
  circular_mapping.write_bytes(t, str, size);
  // Finally move the tail, making the message available for consumers.
  tail = (t + required_memory) % circular_mapping.size;
  // We're done, notify the reader.
  _read_lock.notify();
  return;
}

void CircularStringBuffer::enqueue(const char* msg, size_t size, LogFileStreamOutput* output,
                                   const LogDecorations decorations) {
  ProducerLocker wl(this);
  enqueue_locked(msg, size, output, decorations);
}

void CircularStringBuffer::enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  ProducerLocker wl(this);
  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    const char* str = msg_iterator.message();
    size_t len = strlen(str);
    enqueue_locked(str, len+1, &output, msg_iterator.decorations());
  }
}

CircularStringBuffer::DequeueResult CircularStringBuffer::dequeue(Message* out_msg, char* out, size_t out_size) {
  ConsumerLocker rl(this);

  size_t h = head;
  size_t t = tail;
  // Check if there's something to read
  if (h == t) {
    return NoMessage;
  }

  // Read the message
  circular_mapping.read_bytes(h, (char*)out_msg, sizeof(Message));
  const size_t str_size = out_msg->size;
  if (str_size > out_size) {
    // Not enough space
    return TooSmall;
  }
  // Move h forward
  h = (h + sizeof(Message)) % circular_mapping.size;

  // Now read the string
  circular_mapping.read_bytes(h, out, str_size);
  // Done, move the head forward
  head = (h + out_msg->size) % circular_mapping.size;
  // Notify a writer that more memory is available
  _write_lock.notify();
  // Release the lock
  return OK;
}

void CircularStringBuffer::flush() {
  enqueue("", 0, nullptr, CircularStringBuffer::None);
  _read_lock.notify();
  _flush_sem.wait();
}

void CircularStringBuffer::signal_flush() {
  _flush_sem.signal();
}

bool CircularStringBuffer::has_message() {
  size_t h = Atomic::load(&head);
  size_t t = Atomic::load(&tail);
  return !(h == t);
}

void CircularStringBuffer::await_message() {
  while (true) {
    ConsumerLocker rl(this);
    while (head == tail) {
      _read_lock.wait(0 /* no timeout */);
    }
    break;
  }
}
