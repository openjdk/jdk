/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP
#define SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP

#include "memory/metaspaceClosure.hpp"

class Method;
class ResolvedIndyInfo {
    friend class VMStructs;

    Method* _method;
    u2 _resolved_references_index;
    u2 _cpool_index;
    u2 _number_of_parameters;
    u1 _return_type;
    bool _has_appendix;
    bool _resolution_failed;
    // make flag for has_appendix and resolution_failed
    //u1 _flags;
    // flags [000|local signature|final|vfinal|has appendix|resolution failed]


public:
    ResolvedIndyInfo() :
        _method(nullptr),
        _resolved_references_index(0),
        _cpool_index(0),
        _number_of_parameters(0),
        _return_type(0),
        _has_appendix(false),
        _resolution_failed(false) {}
    ResolvedIndyInfo(u2 resolved_references_index, u2 cpool_index) :
        _method(nullptr),
        _resolved_references_index(resolved_references_index),
        _cpool_index(cpool_index),
        _number_of_parameters(0),
        _return_type(0),
        _has_appendix(false),
        _resolution_failed(false) {}

    // Getters
    Method* method() const               { return _method;                    }
    u2 resolved_references_index() const { return _resolved_references_index; }
    u2 cpool_index() const               { return _cpool_index;               }
    u2 num_parameters() const            { return _number_of_parameters;      }
    u1 return_type() const               { return _return_type;               }
    bool has_appendix() const            { return _has_appendix;              }
    bool has_local_signature() const     { return true;                       } // might not be guaranteed to be true
    bool is_vfinal() const               { return true;                       } // ask Lois, what does this mean??
    bool is_final() const                { return true;                       }
    bool is_resolved() const             { return _method != nullptr;         }
    bool resolution_failed()             { return _resolution_failed;         }

    // Printing
    void print_on(outputStream* st) const;

    // Initialize with fields available before resolution
    void init(u2 resolved_references_index, u2 cpool_index) {
        _resolved_references_index = resolved_references_index;
        _cpool_index = cpool_index;
    }

    // Fill remaining fields
    void fill_in(Method* m, u2 num_params, u1 return_type, bool has_appendix) {
        _number_of_parameters = num_params; // might be parameter size()
        _return_type = return_type;
        _has_appendix = has_appendix;
        //_method = m;
        Atomic::release_store(&_method, m);
    }

    void set_resolution_failed() {
        _resolution_failed = true;
    }

    void adjust_method_entry(Method* new_method) { _method = new_method; }
    bool check_no_old_or_obsolete_entry();

    // void metaspace_pointers_do(MetaspaceClosure* it);

    void remove_unshareable_info();

    // Offsets
    static ByteSize method_offset()                    { return byte_offset_of(ResolvedIndyInfo, _method);                    }
    static ByteSize resolved_references_index_offset() { return byte_offset_of(ResolvedIndyInfo, _resolved_references_index); }
    static ByteSize result_type_offset()               { return byte_offset_of(ResolvedIndyInfo, _return_type);               }
    static ByteSize has_appendix_offset()              { return byte_offset_of(ResolvedIndyInfo, _has_appendix);              }
    static ByteSize num_parameters_offset()            { return byte_offset_of(ResolvedIndyInfo, _number_of_parameters);      }
};

#endif // SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP
