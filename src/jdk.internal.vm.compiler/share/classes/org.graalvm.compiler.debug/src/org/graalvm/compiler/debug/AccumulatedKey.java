/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

abstract class AccumulatedKey extends AbstractKey {
    protected final AbstractKey flat;

    static final String ACCUMULATED_KEY_SUFFIX = "_Accm";
    static final String FLAT_KEY_SUFFIX = "_Flat";

    protected AccumulatedKey(AbstractKey flat, String nameFormat, Object nameArg1, Object nameArg2) {
        super(nameFormat, nameArg1, nameArg2);
        this.flat = flat;
    }

    @Override
    protected String createName(String format, Object arg1, Object arg2) {
        return super.createName(format, arg1, arg2) + ACCUMULATED_KEY_SUFFIX;
    }

    @Override
    public String getDocName() {
        String name = getName();
        return name.substring(0, name.length() - ACCUMULATED_KEY_SUFFIX.length());
    }
}
