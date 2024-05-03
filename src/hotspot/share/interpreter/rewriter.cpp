/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/cdsConfig.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/vmClasses.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/rewriter.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/generateOopMap.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/checkedCast.hpp"

// Computes a CPC map (new_index -> original_index) for constant pool entries
// that are referred to by the interpreter at runtime via the constant pool cache.
// Also computes a CP map (original_index -> new_index).
// Marks entries in CP which require additional processing.
void Rewriter::compute_index_maps() {
  const int length  = _pool->length();
  init_maps(length);
  bool saw_mh_symbol = false;
  for (int i = 0; i < length; i++) {
    int tag = _pool->tag_at(i).value();
    switch (tag) {
      case JVM_CONSTANT_Fieldref          :
        _cp_map.at_put(i, _field_entry_index);
        _field_entry_index++;
        _initialized_field_entries.push(ResolvedFieldEntry((u2)i));
        break;
      case JVM_CONSTANT_InterfaceMethodref: // fall through
      case JVM_CONSTANT_Methodref         :
        _cp_map.at_put(i, _method_entry_index);
        _method_entry_index++;
        _initialized_method_entries.push(ResolvedMethodEntry((u2)i));
        break;
      case JVM_CONSTANT_Dynamic:
        assert(_pool->has_dynamic_constant(), "constant pool's _has_dynamic_constant flag not set");
        add_resolved_references_entry(i);
        break;
      case JVM_CONSTANT_String            : // fall through
      case JVM_CONSTANT_MethodHandle      : // fall through
      case JVM_CONSTANT_MethodType        : // fall through
        add_resolved_references_entry(i);
        break;
      case JVM_CONSTANT_Utf8:
        if (_pool->symbol_at(i) == vmSymbols::java_lang_invoke_MethodHandle() ||
            _pool->symbol_at(i) == vmSymbols::java_lang_invoke_VarHandle()) {
          saw_mh_symbol = true;
        }
        break;
    }
  }

  // Record limits of resolved reference map for constant pool cache indices
  record_map_limits();

  guarantee(_initialized_field_entries.length() - 1 <= (int)((u2)-1), "All resolved field indices fit in a u2");
  guarantee(_initialized_method_entries.length() - 1 <= (int)((u2)-1), "All resolved method indices fit in a u2");

  if (saw_mh_symbol) {
    _method_handle_invokers.at_grow(length, 0);
  }
}

// Unrewrite the bytecodes if an error occurs.
void Rewriter::restore_bytecodes(Thread* thread) {
  int len = _methods->length();
  bool invokespecial_error = false;

  for (int i = len-1; i >= 0; i--) {
    Method* method = _methods->at(i);
    scan_method(thread, method, true, &invokespecial_error);
    assert(!invokespecial_error, "reversing should not get an invokespecial error");
  }
}

// Creates a constant pool cache given a CPC map
void Rewriter::make_constant_pool_cache(TRAPS) {
  ClassLoaderData* loader_data = _pool->pool_holder()->class_loader_data();
  assert(_field_entry_index == _initialized_field_entries.length(), "Field entry size mismatch");
  assert(_method_entry_index == _initialized_method_entries.length(), "Method entry size mismatch");
  ConstantPoolCache* cache =
      ConstantPoolCache::allocate(loader_data, _invokedynamic_references_map,
                                  _initialized_indy_entries, _initialized_field_entries, _initialized_method_entries,
                                  CHECK);

  // initialize object cache in constant pool
  _pool->set_cache(cache);
  cache->set_constant_pool(_pool());

  // _resolved_references is stored in pool->cache(), so need to be done after
  // the above lines.
  _pool->initialize_resolved_references(loader_data, _resolved_references_map,
                                        _resolved_reference_limit,
                                        THREAD);
#if INCLUDE_CDS
  if (!HAS_PENDING_EXCEPTION && CDSConfig::is_dumping_archive()) {
    if (_pool->pool_holder()->is_shared()) {
      assert(CDSConfig::is_dumping_dynamic_archive(), "must be");
      // We are linking a shared class from the base archive. This
      // class won't be written into the dynamic archive, so there's no
      // need to save its CpCaches.
    }
  }
#endif

  // Clean up constant pool cache if initialize_resolved_references() failed.
  if (HAS_PENDING_EXCEPTION) {
    MetadataFactory::free_metadata(loader_data, cache);
    _pool->set_cache(nullptr);  // so the verifier isn't confused
  }
}



// The new finalization semantics says that registration of
// finalizable objects must be performed on successful return from the
// Object.<init> constructor.  We could implement this trivially if
// <init> were never rewritten but since JVMTI allows this to occur, a
// more complicated solution is required.  A special return bytecode
// is used only by Object.<init> to signal the finalization
// registration point.  Additionally local 0 must be preserved so it's
// available to pass to the registration function.  For simplicity we
// require that local 0 is never overwritten so it's available as an
// argument for registration.

void Rewriter::rewrite_Object_init(const methodHandle& method, TRAPS) {
  RawBytecodeStream bcs(method);
  while (!bcs.is_last_bytecode()) {
    Bytecodes::Code opcode = bcs.raw_next();
    switch (opcode) {
      case Bytecodes::_return: *bcs.bcp() = Bytecodes::_return_register_finalizer; break;

      case Bytecodes::_istore:
      case Bytecodes::_lstore:
      case Bytecodes::_fstore:
      case Bytecodes::_dstore:
      case Bytecodes::_astore:
        if (bcs.get_index() != 0) continue;

        // fall through
      case Bytecodes::_istore_0:
      case Bytecodes::_lstore_0:
      case Bytecodes::_fstore_0:
      case Bytecodes::_dstore_0:
      case Bytecodes::_astore_0:
        THROW_MSG(vmSymbols::java_lang_IncompatibleClassChangeError(),
                  "can't overwrite local 0 in Object.<init>");
        break;

      default:
        break;
    }
  }
}


void Rewriter::rewrite_field_reference(address bcp, int offset, bool reverse) {
  address p = bcp + offset;
  if (!reverse) {
    int cp_index = Bytes::get_Java_u2(p);
    int field_entry_index = _cp_map.at(cp_index);
    Bytes::put_native_u2(p, checked_cast<u2>(field_entry_index));
  } else {
    int field_entry_index = Bytes::get_native_u2(p);
    int pool_index = _initialized_field_entries.at(field_entry_index).constant_pool_index();
    Bytes::put_Java_u2(p, checked_cast<u2>(pool_index));
  }
}

void Rewriter::rewrite_method_reference(address bcp, int offset, bool reverse) {
  address p = bcp + offset;
  if (!reverse) {
    int  cp_index    = Bytes::get_Java_u2(p);
    int  method_entry_index = _cp_map.at(cp_index);
    Bytes::put_native_u2(p, (u2)method_entry_index);
    if (!_method_handle_invokers.is_empty()) {
      maybe_rewrite_invokehandle(p - 1, cp_index, method_entry_index, reverse);
    }
  } else {
    int method_entry_index = Bytes::get_native_u2(p);
    int pool_index = _initialized_method_entries.at(method_entry_index).constant_pool_index();
    Bytes::put_Java_u2(p, (u2)pool_index);
    if (!_method_handle_invokers.is_empty()) {
      maybe_rewrite_invokehandle(p - 1, pool_index, method_entry_index, reverse);
    }
  }
}

// If the constant pool entry for invokespecial is InterfaceMethodref,
// we need to add a separate cpCache entry for its resolution, because it is
// different than the resolution for invokeinterface with InterfaceMethodref.
// These cannot share cpCache entries.
void Rewriter::rewrite_invokespecial(address bcp, int offset, bool reverse, bool* invokespecial_error) {
  address p = bcp + offset;
  if (!reverse) {
    int cp_index = Bytes::get_Java_u2(p);
    if (_pool->tag_at(cp_index).is_interface_method()) {
      _initialized_method_entries.push(ResolvedMethodEntry((u2)cp_index));
      Bytes::put_native_u2(p, (u2)_method_entry_index);
      _method_entry_index++;
      if (_method_entry_index != (int)(u2)_method_entry_index) {
        *invokespecial_error = true;
      }
    } else {
      rewrite_method_reference(bcp, offset, reverse);
    }
  } else {
    rewrite_method_reference(bcp, offset, reverse);
  }
}

// Adjust the invocation bytecode for a signature-polymorphic method (MethodHandle.invoke, etc.)
void Rewriter::maybe_rewrite_invokehandle(address opc, int cp_index, int cache_index, bool reverse) {
  if (!reverse) {
    if ((*opc) == (u1)Bytecodes::_invokevirtual ||
        // allow invokespecial as an alias, although it would be very odd:
        ((*opc) == (u1)Bytecodes::_invokespecial)) {
          assert(_pool->tag_at(cp_index).is_method(), "wrong index");
      // Determine whether this is a signature-polymorphic method.
      if (cp_index >= _method_handle_invokers.length())  return;
      int status = _method_handle_invokers.at(cp_index);
      assert(status >= -1 && status <= 1, "oob tri-state");
      if (status == 0) {
        if (_pool->uncached_klass_ref_at_noresolve(cp_index) == vmSymbols::java_lang_invoke_MethodHandle() &&
            MethodHandles::is_signature_polymorphic_name(vmClasses::MethodHandle_klass(),
                                                         _pool->uncached_name_ref_at(cp_index))) {
          // we may need a resolved_refs entry for the appendix
          int resolved_index = add_invokedynamic_resolved_references_entry(cp_index, cache_index);
          _initialized_method_entries.at(cache_index).set_resolved_references_index((u2)resolved_index);
          status = +1;
        } else if (_pool->uncached_klass_ref_at_noresolve(cp_index) == vmSymbols::java_lang_invoke_VarHandle() &&
                   MethodHandles::is_signature_polymorphic_name(vmClasses::VarHandle_klass(),
                                                                _pool->uncached_name_ref_at(cp_index))) {
          // we may need a resolved_refs entry for the appendix
          int resolved_index = add_invokedynamic_resolved_references_entry(cp_index, cache_index);
          _initialized_method_entries.at(cache_index).set_resolved_references_index((u2)resolved_index);
          status = +1;
        } else {
          status = -1;
        }
        _method_handle_invokers.at(cp_index) = status;
      }
      // We use a special internal bytecode for such methods (if non-static).
      // The basic reason for this is that such methods need an extra "appendix" argument
      // to transmit the call site's intended call type.
      if (status > 0) {
        (*opc) = (u1)Bytecodes::_invokehandle;
      }
    }
  } else {
    // Do not need to look at cp_index.
    if ((*opc) == (u1)Bytecodes::_invokehandle) {
      (*opc) = (u1)Bytecodes::_invokevirtual;
      // Ignore corner case of original _invokespecial instruction.
      // This is safe because (a) the signature polymorphic method was final, and
      // (b) the implementation of MethodHandle will not call invokespecial on it.
    }
  }
}


void Rewriter::rewrite_invokedynamic(address bcp, int offset, bool reverse) {
  address p = bcp + offset;
  assert(p[-1] == Bytecodes::_invokedynamic, "not invokedynamic bytecode");
  if (!reverse) {
    int cp_index = Bytes::get_Java_u2(p);
    int resolved_index = add_invokedynamic_resolved_references_entry(cp_index, -1); // Indy no longer has a CPCE
    // Replace the trailing four bytes with an index to the array of
    // indy resolution information in the CPC. There is one entry for
    // each bytecode, even if they make the same call. In other words,
    // the CPC-to-CP relation is many-to-one for invokedynamic entries.
    // This means we must use a larger index size than u2 to address
    // all these entries.  That is the main reason invokedynamic
    // must have a five-byte instruction format.  (Of course, other JVM
    // implementations can use the bytes for other purposes.)
    // Note: We use native_u4 format exclusively for 4-byte indexes.
    Bytes::put_native_u4(p, (u2)_invokedynamic_index);
    _invokedynamic_index++;

    // Collect invokedynamic information before creating ResolvedInvokeDynamicInfo array
    _initialized_indy_entries.push(ResolvedIndyEntry((u2)resolved_index, (u2)cp_index));
  } else {
    // Should do nothing since we are not patching this bytecode
    int cache_index = Bytes::get_native_u4(p);
    int cp_index = _initialized_indy_entries.at(cache_index).constant_pool_index();
    assert(_pool->tag_at(cp_index).is_invoke_dynamic(), "wrong index");
    // zero out 4 bytes
    Bytes::put_Java_u4(p, 0);
    Bytes::put_Java_u2(p, (u2)cp_index);
  }
}

// Rewrite some ldc bytecodes to _fast_aldc
void Rewriter::maybe_rewrite_ldc(address bcp, int offset, bool is_wide,
                                 bool reverse) {
  if (!reverse) {
    assert((*bcp) == (is_wide ? Bytecodes::_ldc_w : Bytecodes::_ldc), "not ldc bytecode");
    address p = bcp + offset;
    int cp_index = is_wide ? Bytes::get_Java_u2(p) : (u1)(*p);
    constantTag tag = _pool->tag_at(cp_index).value();

    if (tag.is_method_handle() ||
        tag.is_method_type() ||
        tag.is_string() ||
        (tag.is_dynamic_constant() &&
         // keep regular ldc interpreter logic for condy primitives
         is_reference_type(Signature::basic_type(_pool->uncached_signature_ref_at(cp_index))))
        ) {
      int ref_index = cp_entry_to_resolved_references(cp_index);
      if (is_wide) {
        (*bcp) = Bytecodes::_fast_aldc_w;
        assert(ref_index == (u2)ref_index, "index overflow");
        Bytes::put_native_u2(p, (u2)ref_index);
      } else {
        (*bcp) = Bytecodes::_fast_aldc;
        assert(ref_index == (u1)ref_index, "index overflow");
        (*p) = (u1)ref_index;
      }
    }
  } else {
    Bytecodes::Code rewritten_bc =
              (is_wide ? Bytecodes::_fast_aldc_w : Bytecodes::_fast_aldc);
    if ((*bcp) == rewritten_bc) {
      address p = bcp + offset;
      int ref_index = is_wide ? Bytes::get_native_u2(p) : (u1)(*p);
      int pool_index = resolved_references_entry_to_pool_index(ref_index);
      if (is_wide) {
        (*bcp) = Bytecodes::_ldc_w;
        assert(pool_index == (u2)pool_index, "index overflow");
        Bytes::put_Java_u2(p, (u2)pool_index);
      } else {
        (*bcp) = Bytecodes::_ldc;
        assert(pool_index == (u1)pool_index, "index overflow");
        (*p) = (u1)pool_index;
      }
    }
  }
}


// Rewrites a method given the index_map information
void Rewriter::scan_method(Thread* thread, Method* method, bool reverse, bool* invokespecial_error) {

  int nof_jsrs = 0;
  bool has_monitor_bytecodes = false;
  Bytecodes::Code c;

  // Bytecodes and their length
  const address code_base = method->code_base();
  const int code_length = method->code_size();

  int bc_length;
  for (int bci = 0; bci < code_length; bci += bc_length) {
    address bcp = code_base + bci;
    int prefix_length = 0;
    c = (Bytecodes::Code)(*bcp);

    // Since we have the code, see if we can get the length
    // directly. Some more complicated bytecodes will report
    // a length of zero, meaning we need to make another method
    // call to calculate the length.
    bc_length = Bytecodes::length_for(c);
    if (bc_length == 0) {
      bc_length = Bytecodes::length_at(method, bcp);

      // length_at will put us at the bytecode after the one modified
      // by 'wide'. We don't currently examine any of the bytecodes
      // modified by wide, but in case we do in the future...
      if (c == Bytecodes::_wide) {
        prefix_length = 1;
        c = (Bytecodes::Code)bcp[1];
      }
    }

    // Continuing with an invalid bytecode will fail in the loop below.
    // So guarantee here.
    guarantee(bc_length > 0, "Verifier should have caught this invalid bytecode");

    switch (c) {
      case Bytecodes::_lookupswitch   : {
#ifndef ZERO
        Bytecode_lookupswitch bc(method, bcp);
        (*bcp) = (
          bc.number_of_pairs() < BinarySwitchThreshold
          ? Bytecodes::_fast_linearswitch
          : Bytecodes::_fast_binaryswitch
        );
#endif
        break;
      }
      case Bytecodes::_fast_linearswitch:
      case Bytecodes::_fast_binaryswitch: {
#ifndef ZERO
        (*bcp) = Bytecodes::_lookupswitch;
#endif
        break;
      }

      case Bytecodes::_invokespecial  : {
        rewrite_invokespecial(bcp, prefix_length+1, reverse, invokespecial_error);
        break;
      }

      case Bytecodes::_putstatic      :
      case Bytecodes::_putfield       : {
        if (!reverse) {
          // Check if any final field of the class given as parameter is modified
          // outside of initializer methods of the class. Fields that are modified
          // are marked with a flag. For marked fields, the compilers do not perform
          // constant folding (as the field can be changed after initialization).
          //
          // The check is performed after verification and only if verification has
          // succeeded. Therefore, the class is guaranteed to be well-formed.
          InstanceKlass* klass = method->method_holder();
          u2 bc_index = Bytes::get_Java_u2(bcp + prefix_length + 1);
          constantPoolHandle cp(thread, method->constants());
          Symbol* ref_class_name = cp->klass_name_at(cp->uncached_klass_ref_index_at(bc_index));

          if (klass->name() == ref_class_name) {
            Symbol* field_name = cp->uncached_name_ref_at(bc_index);
            Symbol* field_sig = cp->uncached_signature_ref_at(bc_index);

            fieldDescriptor fd;
            if (klass->find_field(field_name, field_sig, &fd) != nullptr) {
              if (fd.access_flags().is_final()) {
                if (fd.access_flags().is_static()) {
                  if (!method->is_static_initializer()) {
                    fd.set_has_initialized_final_update(true);
                  }
                } else {
                  if (!method->is_object_initializer()) {
                    fd.set_has_initialized_final_update(true);
                  }
                }
              }
            }
          }
        }
      }
      // fall through
      case Bytecodes::_getstatic      : // fall through
      case Bytecodes::_getfield       : // fall through
        rewrite_field_reference(bcp, prefix_length+1, reverse);
        break;
      case Bytecodes::_invokevirtual  : // fall through
      case Bytecodes::_invokestatic   :
      case Bytecodes::_invokeinterface:
      case Bytecodes::_invokehandle   : // if reverse=true
        rewrite_method_reference(bcp, prefix_length+1, reverse);
        break;
      case Bytecodes::_invokedynamic:
        rewrite_invokedynamic(bcp, prefix_length+1, reverse);
        break;
      case Bytecodes::_ldc:
      case Bytecodes::_fast_aldc:  // if reverse=true
        maybe_rewrite_ldc(bcp, prefix_length+1, false, reverse);
        break;
      case Bytecodes::_ldc_w:
      case Bytecodes::_fast_aldc_w:  // if reverse=true
        maybe_rewrite_ldc(bcp, prefix_length+1, true, reverse);
        break;
      case Bytecodes::_jsr            : // fall through
      case Bytecodes::_jsr_w          : nof_jsrs++;                   break;
      case Bytecodes::_monitorenter   : // fall through
      case Bytecodes::_monitorexit    : has_monitor_bytecodes = true; break;

      default: break;
    }
  }

  // Update flags
  if (has_monitor_bytecodes) {
    method->set_has_monitor_bytecodes();
  }

  // The present of a jsr bytecode implies that the method might potentially
  // have to be rewritten, so we run the oopMapGenerator on the method
  if (nof_jsrs > 0) {
    method->set_has_jsrs();
  }
}

// After constant pool is created, revisit methods containing jsrs.
methodHandle Rewriter::rewrite_jsrs(const methodHandle& method, TRAPS) {
  ResourceMark rm(THREAD);
  ResolveOopMapConflicts romc(method);
  methodHandle new_method = romc.do_potential_rewrite(CHECK_(methodHandle()));
  // Update monitor matching info.
  if (romc.monitor_safe()) {
    new_method->set_guaranteed_monitor_matching();
  }

  return new_method;
}

void Rewriter::rewrite_bytecodes(TRAPS) {
  assert(_pool->cache() == nullptr, "constant pool cache must not be set yet");

  // determine index maps for Method* rewriting
  compute_index_maps();

  if (_klass->name() == vmSymbols::java_lang_Object()) {
    bool did_rewrite = false;
    int i = _methods->length();
    while (i-- > 0) {
      Method* method = _methods->at(i);
      if (method->intrinsic_id() == vmIntrinsics::_Object_init) {
        // rewrite the return bytecodes of Object.<init> to register the
        // object for finalization if needed.
        methodHandle m(THREAD, method);
        rewrite_Object_init(m, CHECK);
        did_rewrite = true;
        break;
      }
    }
    assert(did_rewrite, "must find Object::<init> to rewrite it");
  }

  // rewrite methods, in two passes
  int len = _methods->length();
  bool invokespecial_error = false;

  for (int i = len-1; i >= 0; i--) {
    Method* method = _methods->at(i);
    scan_method(THREAD, method, false, &invokespecial_error);
    if (invokespecial_error) {
      // If you get an error here, there is no reversing bytecodes
      // This exception is stored for this class and no further attempt is
      // made at verifying or rewriting.
      THROW_MSG(vmSymbols::java_lang_InternalError(),
                "This classfile overflows invokespecial for interfaces "
                "and cannot be loaded");
      return;
     }
  }
}

void Rewriter::rewrite(InstanceKlass* klass, TRAPS) {
#if INCLUDE_CDS
  if (klass->is_shared()) {
    assert(!klass->is_rewritten(), "rewritten shared classes cannot be rewritten again");
  }
#endif // INCLUDE_CDS
  ResourceMark rm(THREAD);
  constantPoolHandle cpool(THREAD, klass->constants());
  Rewriter     rw(klass, cpool, klass->methods(), CHECK);
  // (That's all, folks.)
}

Rewriter::Rewriter(InstanceKlass* klass, const constantPoolHandle& cpool, Array<Method*>* methods, TRAPS)
  : _klass(klass),
    _pool(cpool),
    _methods(methods),
    _cp_map(cpool->length()),
    _reference_map(cpool->length()),
    _resolved_references_map(cpool->length() / 2),
    _invokedynamic_references_map(cpool->length() / 2),
    _method_handle_invokers(cpool->length()),
    _invokedynamic_index(0),
    _field_entry_index(0),
    _method_entry_index(0)
{

  // Rewrite bytecodes - exception here exits.
  rewrite_bytecodes(CHECK);

  // Stress restoring bytecodes
  if (StressRewriter) {
    restore_bytecodes(THREAD);
    rewrite_bytecodes(CHECK);
  }

  // allocate constant pool cache, now that we've seen all the bytecodes
  make_constant_pool_cache(THREAD);

  // Restore bytecodes to their unrewritten state if there are exceptions
  // rewriting bytecodes or allocating the cpCache
  if (HAS_PENDING_EXCEPTION) {
    restore_bytecodes(THREAD);
    return;
  }

  // Relocate after everything, but still do this under the is_rewritten flag,
  // so methods with jsrs in custom class lists in aren't attempted to be
  // rewritten in the RO section of the shared archive.
  // Relocated bytecodes don't have to be restored, only the cp cache entries
  int len = _methods->length();
  for (int i = len-1; i >= 0; i--) {
    methodHandle m(THREAD, _methods->at(i));

    if (m->has_jsrs()) {
      m = rewrite_jsrs(m, THREAD);
      // Restore bytecodes to their unrewritten state if there are exceptions
      // relocating bytecodes.  If some are relocated, that is ok because that
      // doesn't affect constant pool to cpCache rewriting.
      if (HAS_PENDING_EXCEPTION) {
        restore_bytecodes(THREAD);
        return;
      }
      // Method might have gotten rewritten.
      methods->at_put(i, m());
    }
  }
}
