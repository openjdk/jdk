/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.internal;

import java.util.Objects;
import java.util.Set;

/**
 * @since 17
 */
public final class NonInheritableRecordingContextBinding extends RecordingContextBinding {

    private final static ThreadLocal<NonInheritableRecordingContextBinding> current = ThreadLocal.withInitial(() -> null);

    public NonInheritableRecordingContextBinding(Set<RecordingContextEntry> entries) {
        super(current.get(), Objects.requireNonNull(entries));
        current.set(this);
    }

    public static NonInheritableRecordingContextBinding current() {
        return current.get();
    }

    @Override
    protected boolean isInheritable() {
        return false;
    }

    // There is no `setCurrent` method since it makes no sense to set the current context when
    // it's not inheritable because the only place the context can come from is from the current
    // thread

    @Override
    public void close() {
        super.close();
        current.set((NonInheritableRecordingContextBinding)previous());
    }
}
