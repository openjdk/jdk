/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

/**
 * Describes a C++ field exposed via {@link HotSpotVMConfigAccess}.
 *
 * @param name    fully qualified name of the represented field (e.g., "Klass::_name").
 * @param type    the represented field's type (e.g., "Symbol*"). This may be {@code null}.
 * @param offset  if the represented field is non-static, this is its offset within the containing structure.
 * @param address if the represented field is static, this is its address. Otherwise, this is 0.
 * @param value   Value of the field represented as a boxed boolean if its C++ type is bool otherwise as a boxed long;
 *                only valid for non-oop static fields. This value is only captured once, during JVMCI initialization.
 *                If {@link #type} cannot be meaningfully (e.g., a struct) or safely (e.g., an oop) expressed as a
 *                boxed object, this is {@code null}.
 */
public record VMField(String name,
                      String type,
                      long offset,
                      long address,
                      Object value) {

    /**
     * Determines if the represented field is static.
     */
    public boolean isStatic() {
        return address != 0;
    }

    @Override
    public String toString() {
        String val = value == null ? "null" : (type.contains("*") ? String.format("0x%x", value) : String.format("%s", value));
        return String.format("Field[name=%s, type=%s, offset=%d, address=0x%x, value=%s]", name, type, offset, address, val);
    }
}
