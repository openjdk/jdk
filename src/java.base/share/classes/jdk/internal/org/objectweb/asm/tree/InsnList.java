/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package jdk.internal.org.objectweb.asm.tree;

import java.util.ListIterator;
import java.util.NoSuchElementException;

import jdk.internal.org.objectweb.asm.MethodVisitor;

/**
 * A doubly linked list of {@link AbstractInsnNode} objects. <i>This
 * implementation is not thread safe</i>.
 */
public class InsnList {

    /**
     * The number of instructions in this list.
     */
    private int size;

    /**
     * The first instruction in this list. May be <tt>null</tt>.
     */
    private AbstractInsnNode first;

    /**
     * The last instruction in this list. May be <tt>null</tt>.
     */
    private AbstractInsnNode last;

    /**
     * A cache of the instructions of this list. This cache is used to improve
     * the performance of the {@link #get} method.
     */
    AbstractInsnNode[] cache;

    /**
     * Returns the number of instructions in this list.
     *
     * @return the number of instructions in this list.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the first instruction in this list.
     *
     * @return the first instruction in this list, or <tt>null</tt> if the list
     *         is empty.
     */
    public AbstractInsnNode getFirst() {
        return first;
    }

    /**
     * Returns the last instruction in this list.
     *
     * @return the last instruction in this list, or <tt>null</tt> if the list
     *         is empty.
     */
    public AbstractInsnNode getLast() {
        return last;
    }

    /**
     * Returns the instruction whose index is given. This method builds a cache
     * of the instructions in this list to avoid scanning the whole list each
     * time it is called. Once the cache is built, this method run in constant
     * time. This cache is invalidated by all the methods that modify the list.
     *
     * @param index
     *            the index of the instruction that must be returned.
     * @return the instruction whose index is given.
     * @throws IndexOutOfBoundsException
     *             if (index &lt; 0 || index &gt;= size()).
     */
    public AbstractInsnNode get(final int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        if (cache == null) {
            cache = toArray();
        }
        return cache[index];
    }

    /**
     * Returns <tt>true</tt> if the given instruction belongs to this list. This
     * method always scans the instructions of this list until it finds the
     * given instruction or reaches the end of the list.
     *
     * @param insn
     *            an instruction.
     * @return <tt>true</tt> if the given instruction belongs to this list.
     */
    public boolean contains(final AbstractInsnNode insn) {
        AbstractInsnNode i = first;
        while (i != null && i != insn) {
            i = i.next;
        }
        return i != null;
    }

    /**
     * Returns the index of the given instruction in this list. This method
     * builds a cache of the instruction indexes to avoid scanning the whole
     * list each time it is called. Once the cache is built, this method run in
     * constant time. The cache is invalidated by all the methods that modify
     * the list.
     *
     * @param insn
     *            an instruction <i>of this list</i>.
     * @return the index of the given instruction in this list. <i>The result of
     *         this method is undefined if the given instruction does not belong
     *         to this list</i>. Use {@link #contains contains} to test if an
     *         instruction belongs to an instruction list or not.
     */
    public int indexOf(final AbstractInsnNode insn) {
        if (cache == null) {
            cache = toArray();
        }
        return insn.index;
    }

    /**
     * Makes the given visitor visit all of the instructions in this list.
     *
     * @param mv
     *            the method visitor that must visit the instructions.
     */
    public void accept(final MethodVisitor mv) {
        AbstractInsnNode insn = first;
        while (insn != null) {
            insn.accept(mv);
            insn = insn.next;
        }
    }

    /**
     * Returns an iterator over the instructions in this list.
     *
     * @return an iterator over the instructions in this list.
     */
    public ListIterator<AbstractInsnNode> iterator() {
        return iterator(0);
    }

    /**
     * Returns an iterator over the instructions in this list.
     *
     * @param index
     *            index of instruction for the iterator to start at
     *
     * @return an iterator over the instructions in this list.
     */
    @SuppressWarnings("unchecked")
    public ListIterator<AbstractInsnNode> iterator(int index) {
        return new InsnListIterator(index);
    }

    /**
     * Returns an array containing all of the instructions in this list.
     *
     * @return an array containing all of the instructions in this list.
     */
    public AbstractInsnNode[] toArray() {
        int i = 0;
        AbstractInsnNode elem = first;
        AbstractInsnNode[] insns = new AbstractInsnNode[size];
        while (elem != null) {
            insns[i] = elem;
            elem.index = i++;
            elem = elem.next;
        }
        return insns;
    }

    /**
     * Replaces an instruction of this list with another instruction.
     *
     * @param location
     *            an instruction <i>of this list</i>.
     * @param insn
     *            another instruction, <i>which must not belong to any
     *            {@link InsnList}</i>.
     */
    public void set(final AbstractInsnNode location, final AbstractInsnNode insn) {
        AbstractInsnNode next = location.next;
        insn.next = next;
        if (next != null) {
            next.prev = insn;
        } else {
            last = insn;
        }
        AbstractInsnNode prev = location.prev;
        insn.prev = prev;
        if (prev != null) {
            prev.next = insn;
        } else {
            first = insn;
        }
        if (cache != null) {
            int index = location.index;
            cache[index] = insn;
            insn.index = index;
        } else {
            insn.index = 0; // insn now belongs to an InsnList
        }
        location.index = -1; // i no longer belongs to an InsnList
        location.prev = null;
        location.next = null;
    }

    /**
     * Adds the given instruction to the end of this list.
     *
     * @param insn
     *            an instruction, <i>which must not belong to any
     *            {@link InsnList}</i>.
     */
    public void add(final AbstractInsnNode insn) {
        ++size;
        if (last == null) {
            first = insn;
            last = insn;
        } else {
            last.next = insn;
            insn.prev = last;
        }
        last = insn;
        cache = null;
        insn.index = 0; // insn now belongs to an InsnList
    }

    /**
     * Adds the given instructions to the end of this list.
     *
     * @param insns
     *            an instruction list, which is cleared during the process. This
     *            list must be different from 'this'.
     */
    public void add(final InsnList insns) {
        if (insns.size == 0) {
            return;
        }
        size += insns.size;
        if (last == null) {
            first = insns.first;
            last = insns.last;
        } else {
            AbstractInsnNode elem = insns.first;
            last.next = elem;
            elem.prev = last;
            last = insns.last;
        }
        cache = null;
        insns.removeAll(false);
    }

    /**
     * Inserts the given instruction at the begining of this list.
     *
     * @param insn
     *            an instruction, <i>which must not belong to any
     *            {@link InsnList}</i>.
     */
    public void insert(final AbstractInsnNode insn) {
        ++size;
        if (first == null) {
            first = insn;
            last = insn;
        } else {
            first.prev = insn;
            insn.next = first;
        }
        first = insn;
        cache = null;
        insn.index = 0; // insn now belongs to an InsnList
    }

    /**
     * Inserts the given instructions at the begining of this list.
     *
     * @param insns
     *            an instruction list, which is cleared during the process. This
     *            list must be different from 'this'.
     */
    public void insert(final InsnList insns) {
        if (insns.size == 0) {
            return;
        }
        size += insns.size;
        if (first == null) {
            first = insns.first;
            last = insns.last;
        } else {
            AbstractInsnNode elem = insns.last;
            first.prev = elem;
            elem.next = first;
            first = insns.first;
        }
        cache = null;
        insns.removeAll(false);
    }

    /**
     * Inserts the given instruction after the specified instruction.
     *
     * @param location
     *            an instruction <i>of this list</i> after which insn must be
     *            inserted.
     * @param insn
     *            the instruction to be inserted, <i>which must not belong to
     *            any {@link InsnList}</i>.
     */
    public void insert(final AbstractInsnNode location,
            final AbstractInsnNode insn) {
        ++size;
        AbstractInsnNode next = location.next;
        if (next == null) {
            last = insn;
        } else {
            next.prev = insn;
        }
        location.next = insn;
        insn.next = next;
        insn.prev = location;
        cache = null;
        insn.index = 0; // insn now belongs to an InsnList
    }

    /**
     * Inserts the given instructions after the specified instruction.
     *
     * @param location
     *            an instruction <i>of this list</i> after which the
     *            instructions must be inserted.
     * @param insns
     *            the instruction list to be inserted, which is cleared during
     *            the process. This list must be different from 'this'.
     */
    public void insert(final AbstractInsnNode location, final InsnList insns) {
        if (insns.size == 0) {
            return;
        }
        size += insns.size;
        AbstractInsnNode ifirst = insns.first;
        AbstractInsnNode ilast = insns.last;
        AbstractInsnNode next = location.next;
        if (next == null) {
            last = ilast;
        } else {
            next.prev = ilast;
        }
        location.next = ifirst;
        ilast.next = next;
        ifirst.prev = location;
        cache = null;
        insns.removeAll(false);
    }

    /**
     * Inserts the given instruction before the specified instruction.
     *
     * @param location
     *            an instruction <i>of this list</i> before which insn must be
     *            inserted.
     * @param insn
     *            the instruction to be inserted, <i>which must not belong to
     *            any {@link InsnList}</i>.
     */
    public void insertBefore(final AbstractInsnNode location,
            final AbstractInsnNode insn) {
        ++size;
        AbstractInsnNode prev = location.prev;
        if (prev == null) {
            first = insn;
        } else {
            prev.next = insn;
        }
        location.prev = insn;
        insn.next = location;
        insn.prev = prev;
        cache = null;
        insn.index = 0; // insn now belongs to an InsnList
    }

    /**
     * Inserts the given instructions before the specified instruction.
     *
     * @param location
     *            an instruction <i>of this list</i> before which the
     *            instructions must be inserted.
     * @param insns
     *            the instruction list to be inserted, which is cleared during
     *            the process. This list must be different from 'this'.
     */
    public void insertBefore(final AbstractInsnNode location,
            final InsnList insns) {
        if (insns.size == 0) {
            return;
        }
        size += insns.size;
        AbstractInsnNode ifirst = insns.first;
        AbstractInsnNode ilast = insns.last;
        AbstractInsnNode prev = location.prev;
        if (prev == null) {
            first = ifirst;
        } else {
            prev.next = ifirst;
        }
        location.prev = ilast;
        ilast.next = location;
        ifirst.prev = prev;
        cache = null;
        insns.removeAll(false);
    }

    /**
     * Removes the given instruction from this list.
     *
     * @param insn
     *            the instruction <i>of this list</i> that must be removed.
     */
    public void remove(final AbstractInsnNode insn) {
        --size;
        AbstractInsnNode next = insn.next;
        AbstractInsnNode prev = insn.prev;
        if (next == null) {
            if (prev == null) {
                first = null;
                last = null;
            } else {
                prev.next = null;
                last = prev;
            }
        } else {
            if (prev == null) {
                first = next;
                next.prev = null;
            } else {
                prev.next = next;
                next.prev = prev;
            }
        }
        cache = null;
        insn.index = -1; // insn no longer belongs to an InsnList
        insn.prev = null;
        insn.next = null;
    }

    /**
     * Removes all of the instructions of this list.
     *
     * @param mark
     *            if the instructions must be marked as no longer belonging to
     *            any {@link InsnList}.
     */
    void removeAll(final boolean mark) {
        if (mark) {
            AbstractInsnNode insn = first;
            while (insn != null) {
                AbstractInsnNode next = insn.next;
                insn.index = -1; // insn no longer belongs to an InsnList
                insn.prev = null;
                insn.next = null;
                insn = next;
            }
        }
        size = 0;
        first = null;
        last = null;
        cache = null;
    }

    /**
     * Removes all of the instructions of this list.
     */
    public void clear() {
        removeAll(false);
    }

    /**
     * Reset all labels in the instruction list. This method should be called
     * before reusing same instructions list between several
     * <code>ClassWriter</code>s.
     */
    public void resetLabels() {
        AbstractInsnNode insn = first;
        while (insn != null) {
            if (insn instanceof LabelNode) {
                ((LabelNode) insn).resetLabel();
            }
            insn = insn.next;
        }
    }

    // this class is not generified because it will create bridges
    @SuppressWarnings("rawtypes")
    private final class InsnListIterator implements ListIterator {

        AbstractInsnNode next;

        AbstractInsnNode prev;

        AbstractInsnNode remove;

        InsnListIterator(int index) {
            if (index == size()) {
                next = null;
                prev = getLast();
            } else {
                next = get(index);
                prev = next.prev;
            }
        }

        public boolean hasNext() {
            return next != null;
        }

        public Object next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            AbstractInsnNode result = next;
            prev = result;
            next = result.next;
            remove = result;
            return result;
        }

        public void remove() {
            if (remove != null) {
                if (remove == next) {
                    next = next.next;
                } else {
                    prev = prev.prev;
                }
                InsnList.this.remove(remove);
                remove = null;
            } else {
                throw new IllegalStateException();
            }
        }

        public boolean hasPrevious() {
            return prev != null;
        }

        public Object previous() {
            AbstractInsnNode result = prev;
            next = result;
            prev = result.prev;
            remove = result;
            return result;
        }

        public int nextIndex() {
            if (next == null) {
                return size();
            }
            if (cache == null) {
                cache = toArray();
            }
            return next.index;
        }

        public int previousIndex() {
            if (prev == null) {
                return -1;
            }
            if (cache == null) {
                cache = toArray();
            }
            return prev.index;
        }

        public void add(Object o) {
            if (next != null) {
                InsnList.this.insertBefore(next, (AbstractInsnNode) o);
            } else if (prev != null) {
                InsnList.this.insert(prev, (AbstractInsnNode) o);
            } else {
                InsnList.this.add((AbstractInsnNode) o);
            }
            prev = (AbstractInsnNode) o;
            remove = null;
        }

        public void set(Object o) {
            if (remove != null) {
                InsnList.this.set(remove, (AbstractInsnNode) o);
                if (remove == prev) {
                    prev = (AbstractInsnNode) o;
                } else {
                    next = (AbstractInsnNode) o;
                }
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
