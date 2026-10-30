/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "memory/resourceArea.hpp"
#include "oops/fieldInfo.inline.hpp"
#include "oops/layoutKind.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "unittest.hpp"

TEST_VM(fieldInfo, layout_kind) {
    JavaThread* THREAD = JavaThread::current();
    ThreadInVMfromNative invm(THREAD);

    FieldInfo flat;
    flat.field_flags_addr()->update_flat(true);
    flat.field_flags_addr()->update_null_free_value_type(true);
    flat.set_layout_kind(LayoutKind::NULL_FREE_NON_ATOMIC_FLAT);

    ResourceMark rm;
    GrowableArray<FieldInfo> fields(3);
    fields.append(FieldInfo());
    fields.append(flat);
    fields.append(FieldInfo());

    ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
    Array<u1>* stream = FieldInfoStream::create_FieldInfoStream(&fields, 3, 0, cld, THREAD);
    ASSERT_NE(stream, nullptr);

    FieldInfoReader reader(stream);
    int java_fields, injected_fields;
    reader.read_field_counts(&java_fields, &injected_fields);

    EXPECT_EQ(java_fields, 3);
    EXPECT_EQ(injected_fields, 0);

    const LayoutKind expected_layouts[] = {
        LayoutKind::REFERENCE,
        LayoutKind::NULL_FREE_NON_ATOMIC_FLAT,
        LayoutKind::REFERENCE
    };

    FieldInfo decoded;
    for (LayoutKind layout : expected_layouts) {
        ASSERT_TRUE(reader.has_next());
        reader.read_field_info(decoded);
        EXPECT_EQ(decoded.layout_kind(), layout);
        EXPECT_EQ(decoded.field_flags().is_flat(), layout == LayoutKind::NULL_FREE_NON_ATOMIC_FLAT);
    }

    EXPECT_FALSE(reader.has_next());
}