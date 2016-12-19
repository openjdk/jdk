/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.dfa;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;

/**
 * Ensures that an element is only in the worklist once.
 *
 */
class UniqueWorkList extends ArrayDeque<AbstractBlockBase<?>> {
    private static final long serialVersionUID = 8009554570990975712L;
    BitSet valid;

    UniqueWorkList(int size) {
        this.valid = new BitSet(size);
    }

    @Override
    public AbstractBlockBase<?> poll() {
        AbstractBlockBase<?> result = super.poll();
        if (result != null) {
            valid.set(result.getId(), false);
        }
        return result;
    }

    @Override
    public boolean add(AbstractBlockBase<?> pred) {
        if (!valid.get(pred.getId())) {
            valid.set(pred.getId(), true);
            return super.add(pred);
        }
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends AbstractBlockBase<?>> collection) {
        boolean changed = false;
        for (AbstractBlockBase<?> element : collection) {
            if (!valid.get(element.getId())) {
                valid.set(element.getId(), true);
                super.add(element);
                changed = true;
            }
        }
        return changed;
    }
}
