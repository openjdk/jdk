/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_RECORDER_STORAGE_JFRSTORAGEUTILS_HPP
#define SHARE_VM_JFR_RECORDER_STORAGE_JFRSTORAGEUTILS_HPP

#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/thread.hpp"

template <typename Operation, typename NextOperation>
class CompositeOperation {
 private:
  Operation* _op;
  NextOperation* _next;
 public:
  CompositeOperation(Operation* op, NextOperation* next) : _op(op), _next(next) {
    assert(_op != NULL, "invariant");
  }
  typedef typename Operation::Type Type;
  bool process(Type* t = NULL) {
    return _next == NULL ? _op->process(t) : _op->process(t) && _next->process(t);
  }
  size_t processed() const {
    return _next == NULL ? _op->processed() : _op->processed() + _next->processed();
  }
};

template <typename T>
class UnBufferedWriteToChunk {
 private:
  JfrChunkWriter& _writer;
  size_t _processed;
 public:
  typedef T Type;
  UnBufferedWriteToChunk(JfrChunkWriter& writer) : _writer(writer), _processed(0) {}
  bool write(Type* t, const u1* data, size_t size);
  size_t processed() { return _processed; }
};

template <typename T>
class DefaultDiscarder {
 private:
  size_t _processed;
 public:
  typedef T Type;
  DefaultDiscarder() : _processed() {}
  bool discard(Type* t, const u1* data, size_t size);
  size_t processed() const { return _processed; }
};

template <typename Operation>
class ConcurrentWriteOp {
 private:
  Operation& _operation;
 public:
  typedef typename Operation::Type Type;
  ConcurrentWriteOp(Operation& operation) : _operation(operation) {}
  bool process(Type* t);
  size_t processed() const { return _operation.processed(); }
};

template <typename Operation>
class ConcurrentWriteOpExcludeRetired : private ConcurrentWriteOp<Operation> {
 public:
  typedef typename Operation::Type Type;
  ConcurrentWriteOpExcludeRetired(Operation& operation) : ConcurrentWriteOp<Operation>(operation) {}
  bool process(Type* t);
  size_t processed() const { return ConcurrentWriteOp<Operation>::processed(); }
};


template <typename Operation>
class MutexedWriteOp {
 private:
  Operation& _operation;
 public:
  typedef typename Operation::Type Type;
  MutexedWriteOp(Operation& operation) : _operation(operation) {}
  bool process(Type* t);
  size_t processed() const { return _operation.processed(); }
};

enum jfr_operation_mode {
  mutexed = 1,
  concurrent
};

template <typename Operation>
class DiscardOp {
 private:
  Operation _operation;
  jfr_operation_mode _mode;
 public:
  typedef typename Operation::Type Type;
  DiscardOp(jfr_operation_mode mode = concurrent) : _operation(), _mode(mode) {}
  bool process(Type* t);
  size_t processed() const { return _operation.processed(); }
};

#endif // SHARE_VM_JFR_RECORDER_STORAGE_JFRSTORAGEUTILS_HPP
