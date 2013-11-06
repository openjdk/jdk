/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "code/debugInfoRec.hpp"
#include "code/scopeDesc.hpp"
#include "prims/jvmtiExport.hpp"

// Private definition.
// There is one DIR_Chunk for each scope and values array.
// A chunk can potentially be used more than once.
// We keep track of these chunks in order to detect
// repetition and enable sharing.
class DIR_Chunk {
  friend class DebugInformationRecorder;
  int  _offset; // location in the stream of this scope
  int  _length; // number of bytes in the stream
  int  _hash;   // hash of stream bytes (for quicker reuse)

  void* operator new(size_t ignore, DebugInformationRecorder* dir) throw() {
    assert(ignore == sizeof(DIR_Chunk), "");
    if (dir->_next_chunk >= dir->_next_chunk_limit) {
      const int CHUNK = 100;
      dir->_next_chunk = NEW_RESOURCE_ARRAY(DIR_Chunk, CHUNK);
      dir->_next_chunk_limit = dir->_next_chunk + CHUNK;
    }
    return dir->_next_chunk++;
  }

  DIR_Chunk(int offset, int length, DebugInformationRecorder* dir) {
    _offset = offset;
    _length = length;
    unsigned int hash = 0;
    address p = dir->stream()->buffer() + _offset;
    for (int i = 0; i < length; i++) {
      if (i == 6)  break;
      hash *= 127;
      hash += p[i];
    }
    _hash = hash;
  }

  DIR_Chunk* find_match(GrowableArray<DIR_Chunk*>* arr,
                        int start_index,
                        DebugInformationRecorder* dir) {
    int end_index = arr->length();
    int hash = this->_hash, length = this->_length;
    address buf = dir->stream()->buffer();
    for (int i = end_index; --i >= start_index; ) {
      DIR_Chunk* that = arr->at(i);
      if (hash   == that->_hash &&
          length == that->_length &&
          0 == memcmp(buf + this->_offset, buf + that->_offset, length)) {
        return that;
      }
    }
    return NULL;
  }
};

static inline bool compute_recording_non_safepoints() {
  if (JvmtiExport::should_post_compiled_method_load()
      && FLAG_IS_DEFAULT(DebugNonSafepoints)) {
    // The default value of this flag is taken to be true,
    // if JVMTI is looking at nmethod codes.
    // We anticipate that JVMTI may wish to participate in profiling.
    return true;
  }

  // If the flag is set manually, use it, whether true or false.
  // Otherwise, if JVMTI is not in the picture, use the default setting.
  // (This is true in debug, just for the exercise, false in product mode.)
  return DebugNonSafepoints;
}

DebugInformationRecorder::DebugInformationRecorder(OopRecorder* oop_recorder)
  : _recording_non_safepoints(compute_recording_non_safepoints())
{
  _pcs_size   = 100;
  _pcs        = NEW_RESOURCE_ARRAY(PcDesc, _pcs_size);
  _pcs_length = 0;

  _prev_safepoint_pc = PcDesc::lower_offset_limit;

  _stream = new DebugInfoWriteStream(this, 10 * K);
  // make sure that there is no stream_decode_offset that is zero
  _stream->write_byte((jbyte)0xFF);

  // make sure that we can distinguish the value "serialized_null" from offsets
  assert(_stream->position() > serialized_null, "sanity");

  _oop_recorder = oop_recorder;

  _all_chunks    = new GrowableArray<DIR_Chunk*>(300);
  _shared_chunks = new GrowableArray<DIR_Chunk*>(30);
  _next_chunk = _next_chunk_limit = NULL;

  add_new_pc_offset(PcDesc::lower_offset_limit);  // sentinel record

  debug_only(_recording_state = rs_null);
}


void DebugInformationRecorder::add_oopmap(int pc_offset, OopMap* map) {
  // !!!!! Preserve old style handling of oopmaps for now
  _oopmaps->add_gc_map(pc_offset, map);
}

void DebugInformationRecorder::add_safepoint(int pc_offset, OopMap* map) {
  assert(!_oop_recorder->is_complete(), "not frozen yet");
  // Store the new safepoint

  // Add the oop map
  add_oopmap(pc_offset, map);

  add_new_pc_offset(pc_offset);

  assert(_recording_state == rs_null, "nesting of recording calls");
  debug_only(_recording_state = rs_safepoint);
}

void DebugInformationRecorder::add_non_safepoint(int pc_offset) {
  assert(!_oop_recorder->is_complete(), "not frozen yet");
  assert(_recording_non_safepoints, "must be recording non-safepoints");

  add_new_pc_offset(pc_offset);

  assert(_recording_state == rs_null, "nesting of recording calls");
  debug_only(_recording_state = rs_non_safepoint);
}

void DebugInformationRecorder::add_new_pc_offset(int pc_offset) {
  assert(_pcs_length == 0 || last_pc()->pc_offset() < pc_offset,
         "must specify a new, larger pc offset");

  // add the pcdesc
  if (_pcs_length == _pcs_size) {
    // Expand
    int     new_pcs_size = _pcs_size * 2;
    PcDesc* new_pcs      = NEW_RESOURCE_ARRAY(PcDesc, new_pcs_size);
    for (int index = 0; index < _pcs_length; index++) {
      new_pcs[index] = _pcs[index];
    }
    _pcs_size = new_pcs_size;
    _pcs      = new_pcs;
  }
  assert(_pcs_size > _pcs_length, "There must be room for after expanding");

  _pcs[_pcs_length++] = PcDesc(pc_offset, DebugInformationRecorder::serialized_null,
                               DebugInformationRecorder::serialized_null);
}


int DebugInformationRecorder::serialize_monitor_values(GrowableArray<MonitorValue*>* monitors) {
  if (monitors == NULL || monitors->is_empty()) return DebugInformationRecorder::serialized_null;
  assert(_recording_state == rs_safepoint, "must be recording a safepoint");
  int result = stream()->position();
  stream()->write_int(monitors->length());
  for (int index = 0; index < monitors->length(); index++) {
    monitors->at(index)->write_on(stream());
  }
  assert(result != serialized_null, "sanity");

  // (See comment below on DebugInformationRecorder::describe_scope.)
  int shared_result = find_sharable_decode_offset(result);
  if (shared_result != serialized_null) {
    stream()->set_position(result);
    result = shared_result;
  }

  return result;
}


int DebugInformationRecorder::serialize_scope_values(GrowableArray<ScopeValue*>* values) {
  if (values == NULL || values->is_empty()) return DebugInformationRecorder::serialized_null;
  assert(_recording_state == rs_safepoint, "must be recording a safepoint");
  int result = stream()->position();
  assert(result != serialized_null, "sanity");
  stream()->write_int(values->length());
  for (int index = 0; index < values->length(); index++) {
    values->at(index)->write_on(stream());
  }

  // (See comment below on DebugInformationRecorder::describe_scope.)
  int shared_result = find_sharable_decode_offset(result);
  if (shared_result != serialized_null) {
    stream()->set_position(result);
    result = shared_result;
  }

  return result;
}


#ifndef PRODUCT
// These variables are put into one block to reduce relocations
// and make it simpler to print from the debugger.
static
struct dir_stats_struct {
  int chunks_queried;
  int chunks_shared;
  int chunks_reshared;
  int chunks_elided;

  void print() {
    tty->print_cr("Debug Data Chunks: %d, shared %d+%d, non-SP's elided %d",
                  chunks_queried,
                  chunks_shared, chunks_reshared,
                  chunks_elided);
  }
} dir_stats;
#endif //PRODUCT


int DebugInformationRecorder::find_sharable_decode_offset(int stream_offset) {
  // Only pull this trick if non-safepoint recording
  // is enabled, for now.
  if (!recording_non_safepoints())
    return serialized_null;

  NOT_PRODUCT(++dir_stats.chunks_queried);
  int stream_length = stream()->position() - stream_offset;
  assert(stream_offset != serialized_null, "should not be null");
  assert(stream_length != 0, "should not be empty");

  DIR_Chunk* ns = new(this) DIR_Chunk(stream_offset, stream_length, this);

  // Look in previously shared scopes first:
  DIR_Chunk* ms = ns->find_match(_shared_chunks, 0, this);
  if (ms != NULL) {
    NOT_PRODUCT(++dir_stats.chunks_reshared);
    assert(ns+1 == _next_chunk, "");
    _next_chunk = ns;
    return ms->_offset;
  }

  // Look in recently encountered scopes next:
  const int MAX_RECENT = 50;
  int start_index = _all_chunks->length() - MAX_RECENT;
  if (start_index < 0)  start_index = 0;
  ms = ns->find_match(_all_chunks, start_index, this);
  if (ms != NULL) {
    NOT_PRODUCT(++dir_stats.chunks_shared);
    // Searching in _all_chunks is limited to a window,
    // but searching in _shared_chunks is unlimited.
    _shared_chunks->append(ms);
    assert(ns+1 == _next_chunk, "");
    _next_chunk = ns;
    return ms->_offset;
  }

  // No match.  Add this guy to the list, in hopes of future shares.
  _all_chunks->append(ns);
  return serialized_null;
}


// must call add_safepoint before: it sets PcDesc and this routine uses
// the last PcDesc set
void DebugInformationRecorder::describe_scope(int         pc_offset,
                                              ciMethod*   method,
                                              int         bci,
                                              bool        reexecute,
                                              bool        is_method_handle_invoke,
                                              bool        return_oop,
                                              DebugToken* locals,
                                              DebugToken* expressions,
                                              DebugToken* monitors) {
  assert(_recording_state != rs_null, "nesting of recording calls");
  PcDesc* last_pd = last_pc();
  assert(last_pd->pc_offset() == pc_offset, "must be last pc");
  int sender_stream_offset = last_pd->scope_decode_offset();
  // update the stream offset of current pc desc
  int stream_offset = stream()->position();
  last_pd->set_scope_decode_offset(stream_offset);

  // Record flags into pcDesc.
  last_pd->set_should_reexecute(reexecute);
  last_pd->set_is_method_handle_invoke(is_method_handle_invoke);
  last_pd->set_return_oop(return_oop);

  // serialize sender stream offest
  stream()->write_int(sender_stream_offset);

  // serialize scope
  Metadata* method_enc = (method == NULL)? NULL: method->constant_encoding();
  stream()->write_int(oop_recorder()->find_index(method_enc));
  stream()->write_bci(bci);
  assert(method == NULL ||
         (method->is_native() && bci == 0) ||
         (!method->is_native() && 0 <= bci && bci < method->code_size()) ||
         (method->is_compiled_lambda_form() && bci == -99) ||  // this might happen in C1
         bci == -1, "illegal bci");

  // serialize the locals/expressions/monitors
  stream()->write_int((intptr_t) locals);
  stream()->write_int((intptr_t) expressions);
  stream()->write_int((intptr_t) monitors);

  // Here's a tricky bit.  We just wrote some bytes.
  // Wouldn't it be nice to find that we had already
  // written those same bytes somewhere else?
  // If we get lucky this way, reset the stream
  // and reuse the old bytes.  By the way, this
  // trick not only shares parent scopes, but also
  // compresses equivalent non-safepoint PcDescs.
  int shared_stream_offset = find_sharable_decode_offset(stream_offset);
  if (shared_stream_offset != serialized_null) {
    stream()->set_position(stream_offset);
    last_pd->set_scope_decode_offset(shared_stream_offset);
  }
}

void DebugInformationRecorder::dump_object_pool(GrowableArray<ScopeValue*>* objects) {
  guarantee( _pcs_length > 0, "safepoint must exist before describing scopes");
  PcDesc* last_pd = &_pcs[_pcs_length-1];
  if (objects != NULL) {
    for (int i = objects->length() - 1; i >= 0; i--) {
      ((ObjectValue*) objects->at(i))->set_visited(false);
    }
  }
  int offset = serialize_scope_values(objects);
  last_pd->set_obj_decode_offset(offset);
}

void DebugInformationRecorder::end_scopes(int pc_offset, bool is_safepoint) {
  assert(_recording_state == (is_safepoint? rs_safepoint: rs_non_safepoint),
         "nesting of recording calls");
  debug_only(_recording_state = rs_null);

  // Try to compress away an equivalent non-safepoint predecessor.
  // (This only works because we have previously recognized redundant
  // scope trees and made them use a common scope_decode_offset.)
  if (_pcs_length >= 2 && recording_non_safepoints()) {
    PcDesc* last = last_pc();
    PcDesc* prev = prev_pc();
    // If prev is (a) not a safepoint and (b) has the same
    // stream pointer, then it can be coalesced into the last.
    // This is valid because non-safepoints are only sought
    // with pc_desc_near, which (when it misses prev) will
    // search forward until it finds last.
    // In addition, it does not matter if the last PcDesc
    // is for a safepoint or not.
    if (_prev_safepoint_pc < prev->pc_offset() && prev->is_same_info(last)) {
      assert(prev == last-1, "sane");
      prev->set_pc_offset(pc_offset);
      _pcs_length -= 1;
      NOT_PRODUCT(++dir_stats.chunks_elided);
    }
  }

  // We have just recorded this safepoint.
  // Remember it in case the previous paragraph needs to know.
  if (is_safepoint) {
    _prev_safepoint_pc = pc_offset;
  }
}

#ifdef ASSERT
bool DebugInformationRecorder::recorders_frozen() {
  return _oop_recorder->is_complete() || _oop_recorder->is_complete();
}

void DebugInformationRecorder::mark_recorders_frozen() {
  _oop_recorder->freeze();
}
#endif // PRODUCT

DebugToken* DebugInformationRecorder::create_scope_values(GrowableArray<ScopeValue*>* values) {
  assert(!recorders_frozen(), "not frozen yet");
  return (DebugToken*) (intptr_t) serialize_scope_values(values);
}


DebugToken* DebugInformationRecorder::create_monitor_values(GrowableArray<MonitorValue*>* monitors) {
  assert(!recorders_frozen(), "not frozen yet");
  return (DebugToken*) (intptr_t) serialize_monitor_values(monitors);
}


int DebugInformationRecorder::data_size() {
  debug_only(mark_recorders_frozen());  // mark it "frozen" for asserts
  return _stream->position();
}


int DebugInformationRecorder::pcs_size() {
  debug_only(mark_recorders_frozen());  // mark it "frozen" for asserts
  if (last_pc()->pc_offset() != PcDesc::upper_offset_limit)
    add_new_pc_offset(PcDesc::upper_offset_limit);
  return _pcs_length * sizeof(PcDesc);
}


void DebugInformationRecorder::copy_to(nmethod* nm) {
  nm->copy_scopes_data(stream()->buffer(), stream()->position());
  nm->copy_scopes_pcs(_pcs, _pcs_length);
}


void DebugInformationRecorder::verify(const nmethod* code) {
  Unimplemented();
}

#ifndef PRODUCT
void DebugInformationRecorder::print_statistics() {
  dir_stats.print();
}
#endif //PRODUCT
