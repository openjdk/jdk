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
#include "mutex_posix.hpp"
#include "logging/circularStringBuffer.hpp"

#ifdef LINUX
FILE* make_buffer(size_t size) {
  FILE* f = tmpfile();
  if (f == nullptr) {
    // TODO: Fail
  }
  const int fd = fileno(f);
  if (fd == -1) {
    // TODO: Fail
  }
  int ret = ftruncate(fd, size);
  if (ret != 0) {
    // TODO: Fail
  }
  return f;
}
char* make_mirrored_mapping(size_t size, FILE* file) {
  const int fd = fileno(file);
  char* buffer = (char*)mmap(nullptr, size * 2, PROT_READ | PROT_WRITE,
                             MAP_NORESERVE | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (buffer == MAP_FAILED) {
    vm_exit_out_of_memory(size * 2, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
  }
  void* ret = mmap(buffer, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_FIXED, fd, 0);
  if (ret == MAP_FAILED) {
    vm_exit_out_of_memory(size*2, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
  }
  ret = mmap(buffer + size, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_FIXED, fd, 0);
  if (ret == MAP_FAILED) {
    vm_exit_out_of_memory(size * 2, OOM_MMAP_ERROR, "Failed to allocate async logging buffer");
  }
  return buffer;
}
#endif

// LogDecorator::None applies to 'constant initialization' because of its constexpr constructor.
const LogDecorations& CircularStringBuffer::None = LogDecorations(
    LogLevel::Warning, LogTagSetMapping<LogTag::__NO_TAG>::tagset(), LogDecorators::None);

CircularStringBuffer::CircularStringBuffer(StatisticsMap& map, PlatformMonitor& stats_lock, size_t size)
  : _stats(map),
    _stats_lock(stats_lock),
    tail(0),
    head(0),
    buffer(nullptr),
    bufsize(size) {
  assert(is_aligned(size, os::vm_page_size()), "must");

  underlying_buffer = make_buffer(size);
  buffer = make_mirrored_mapping(size, underlying_buffer);
}

CircularStringBuffer::~CircularStringBuffer() {
  munmap(buffer, bufsize * 2);
  fclose(underlying_buffer);
}
size_t CircularStringBuffer::used() {
  // Load the state
  size_t h = Atomic::load(&head);
  size_t t = Atomic::load(&tail);
  if (h <= t) {
    return t - h;
  } else {
    return bufsize - (h - t);
  }
}
size_t CircularStringBuffer::unused() {
  return bufsize - used();
}

size_t CircularStringBuffer::calc_mem(size_t sz) {
  return align_up(sz, alignof(Message));
}

// Size including NUL byte
void CircularStringBuffer::enqueue_locked(const char* msg, size_t size, LogFileStreamOutput* output,
                                   const LogDecorations decorations) {
  const size_t required_memory = calc_mem(size);
  // We need space for an additional Descriptor in case of a flush token
  if (unused() < (required_memory + sizeof(Message))) {
    _stats_lock.lock();
    bool p_created;
    uint32_t* counter = _stats.put_if_absent(output, 0, &p_created);
    *counter = *counter + 1;
    _stats_lock.unlock();
    return;
  }
  // Load the tail, no need for atomic load as we're using the lock.
  size_t t = Atomic::load(&tail);
  // Write the Descriptor
  new (&buffer[t]) Message{required_memory, output, decorations};
  // Write the string
  memcpy(&buffer[t] + sizeof(Message), msg, size);
  // Finally move the tail, making the message available for consumers.
  Atomic::store(&tail, (t + required_memory + sizeof(Message)) % bufsize);
  // We're done, notify the writer.
  _write_lock.notify();
  return;
}

void CircularStringBuffer::enqueue(const char* msg, size_t size, LogFileStreamOutput* output,
                                   const LogDecorations decorations) {
  ReadLocker rl(this);
  enqueue_locked(msg, size, output, decorations);
}

void CircularStringBuffer::enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  WriteLocker wl(this);
  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    const char* str = msg_iterator.message();
    size_t len = strlen(str);
    enqueue_locked(str, len, &output, msg_iterator.decorations());
  }
}

void CircularStringBuffer::dequeue(Message* out_descriptor, char* out, size_t out_size) {
  ReadLocker rl(this);

  size_t h = Atomic::load(&head);
  size_t t = Atomic::load(&tail);
  // Check if there's something to read
  if (h == t) {
    return;
  }

  // Read the descriptor
  Message* desc = (Message*)&buffer[h];
  const size_t str_size = desc->size;
  if (str_size > out_size) {
    // Not enough space
    return;
  }
  ::new (out_descriptor) Message(*desc);
  // OK, we can read
  char* str = &buffer[h] + sizeof(Message);
  memcpy(out, str, str_size);
  // Done, move the head
  Atomic::store(&head, (h + desc->size + sizeof(Message)) % bufsize);
  // Release the lock
  return;
}
void CircularStringBuffer::flush() {
  enqueue(nullptr, 0, nullptr, CircularStringBuffer::None);
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
    WriteLocker wl(this);
    while (!has_message()) {
      _write_lock.wait(0 /* no timeout */);
    }
    break;
  }
}
