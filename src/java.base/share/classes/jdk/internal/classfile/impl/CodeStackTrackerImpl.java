/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.components.CodeStackTracker;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

public final class CodeStackTrackerImpl implements CodeStackTracker {

    private static record Item(TypeKind type, Item next) {
    }

    private final class Stack extends AbstractCollection<TypeKind> {

        private Item top;
        private int count, realSize;

        Stack(Item top, int count, int realSize) {
            this.top = top;
            this.count = count;
            this.realSize = realSize;
        }

        @Override
        public Iterator<TypeKind> iterator() {
            return new Iterator<TypeKind>() {
                Item i = top;

                @Override
                public boolean hasNext() {
                    return i != null;
                }

                @Override
                public TypeKind next() {
                    if (i == null) {
                        throw new NoSuchElementException();
                    }
                    var t = i.type;
                    i = i.next;
                    return t;
                }
            };
        }

        @Override
        public int size() {
            return count;
        }

        private void push(TypeKind type) {
            top = new Item(type, top);
            realSize += type.slotSize();
            count++;
            if (maxSize != null && realSize > maxSize) maxSize = realSize;
        }

        private TypeKind pop() {
            var t = top.type;
            realSize -= t.slotSize();
            count--;
            top = top.next;
            return t;
        }
    }

    private Stack stack = new Stack(null, 0, 0);
    private Integer maxSize = 0;

    public CodeStackTrackerImpl(TypeKind... initialStack) {
        for (int i = initialStack.length - 1; i >= 0; i--)
            push(initialStack[i]);
    }

    @Override
    public Optional<Collection<TypeKind>> stack() {
        return Optional.ofNullable(fork());
    }

    @Override
    public Optional<Integer> maxStackSize() {
        return Optional.ofNullable(maxSize);
    }

    private final Map<Label, Stack> map = new HashMap<>();

    private void push(TypeKind type) {
        if (stack != null) {
            if (type != TypeKind.VoidType) stack.push(type);
        } else {
            maxSize = null;
        }
    }

    private void pop(int i) {
        if (stack != null) {
            while (i-- > 0) stack.pop();
        } else {
            maxSize = null;
        }
    }

    private Stack fork() {
        return stack == null ? null : new Stack(stack.top, stack.count, stack.realSize);
    }

    private void withStack(Consumer<Stack> c) {
        if (stack != null) c.accept(stack);
        else maxSize = null;
    }

    @Override
    public void accept(CodeBuilder cb, CodeElement el) {
        cb.with(el);
        switch (el) {
            case ArrayLoadInstruction i -> {
                pop(2);push(i.typeKind());
            }
            case ArrayStoreInstruction i ->
                pop(3);
            case BranchInstruction i -> {
                if (i.opcode() == Opcode.GOTO || i.opcode() == Opcode.GOTO_W) {
                    map.put(i.target(), stack);
                    stack = null;
                } else {
                    pop(1);
                    map.put(i.target(), fork());
                }
            }
            case ConstantInstruction i ->
                push(i.typeKind());
            case ConvertInstruction i -> {
                pop(1);push(i.toType());
            }
            case FieldInstruction i -> {
                switch (i.opcode()) {
                    case GETSTATIC ->
                        push(TypeKind.fromDescriptor(i.type().stringValue()));
                    case GETFIELD -> {
                        pop(1);push(TypeKind.fromDescriptor(i.type().stringValue()));
                    }
                    case PUTSTATIC ->
                        pop(1);
                    case PUTFIELD ->
                        pop(2);
                }
            }
            case InvokeDynamicInstruction i -> {
                var type = i.typeSymbol();
                pop(type.parameterCount());
                push(TypeKind.from(type.returnType()));
            }
            case InvokeInstruction i -> {
                var type = i.typeSymbol();
                pop(type.parameterCount());
                if (i.opcode() != Opcode.INVOKESTATIC) pop(1);
                push(TypeKind.from(type.returnType()));
            }
            case LoadInstruction i ->
                push(i.typeKind());
            case StoreInstruction i ->
                pop(1);
            case LookupSwitchInstruction i -> {
                map.put(i.defaultTarget(), stack);
                for (var c : i.cases()) map.put(c.target(), fork());
                stack = null;
            }
            case MonitorInstruction i ->
                pop(1);
            case NewMultiArrayInstruction i -> {
                pop(i.dimensions());push(TypeKind.ReferenceType);
            }
            case NewObjectInstruction i ->
                push(TypeKind.ReferenceType);
            case NewPrimitiveArrayInstruction i -> {
                pop(1);push(TypeKind.ReferenceType);
            }
            case NewReferenceArrayInstruction i -> {
                pop(1);push(TypeKind.ReferenceType);
            }
            case NopInstruction i -> {}
            case OperatorInstruction i -> {
                switch (i.opcode()) {
                    case ARRAYLENGTH, INEG, LNEG, FNEG, DNEG -> pop(1);
                    default -> pop(2);
                }
                push(i.typeKind());
            }
            case ReturnInstruction i ->
                stack = null;
            case StackInstruction i -> {
                switch (i.opcode()) {
                    case POP -> pop(1);
                    case POP2 -> withStack(s -> {
                        if (s.pop().slotSize() == 1) s.pop();
                    });
                    case DUP ->  withStack(s -> {
                        var v = s.pop();s.push(v);s.push(v);
                    });
                    case DUP2 -> withStack(s -> {
                        var v1 = s.pop();
                        if (v1.slotSize() == 1) {
                            var v2 = s.pop();
                            s.push(v2);s.push(v1);
                            s.push(v2);s.push(v1);
                        } else {
                            s.push(v1);s.push(v1);
                        }
                    });
                    case DUP_X1 -> withStack(s -> {
                        var v1 = s.pop();
                        var v2 = s.pop();
                        s.push(v1);s.push(v2);s.push(v1);
                    });
                    case DUP_X2 -> withStack(s -> {
                        var v1 = s.pop();
                        var v2 = s.pop();
                        if (v2.slotSize() == 1) {
                            var v3 = s.pop();
                            s.push(v1);s.push(v3);s.push(v2);s.push(v1);
                        } else {
                            s.push(v1);s.push(v2);s.push(v1);
                        }
                    });
                    case DUP2_X1 -> withStack(s -> {
                        var v1 = s.pop();
                        var v2 = s.pop();
                        if (v1.slotSize() == 1) {
                            var v3 = s.pop();
                            s.push(v2);s.push(v1);s.push(v3);s.push(v2);s.push(v1);
                        } else {
                            s.push(v1);s.push(v2);s.push(v1);
                        }
                    });
                    case DUP2_X2 -> withStack(s -> {
                        var v1 = s.pop();
                        var v2 = s.pop();
                        if (v1.slotSize() == 1) {
                            var v3 = s.pop();
                            if (v3.slotSize() == 1) {
                                var v4 = s.pop();
                                s.push(v2);s.push(v1);s.push(v4);s.push(v3);s.push(v2);s.push(v1);
                            } else {
                                s.push(v2);s.push(v1);s.push(v3);s.push(v2);s.push(v1);
                            }
                        } else {
                            if (v2.slotSize() == 1) {
                                var v3 = s.pop();
                                s.push(v1);s.push(v3);s.push(v2);s.push(v1);
                            } else {
                                s.push(v1);s.push(v2);s.push(v1);
                            }
                        }
                    });
                    case SWAP -> withStack(s -> {
                        var v1 = s.pop();
                        var v2 = s.pop();
                        s.push(v1);s.push(v2);
                    });
                }
            }
            case TableSwitchInstruction i -> {
                map.put(i.defaultTarget(), stack);
                for (var c : i.cases()) map.put(c.target(), fork());
                stack = null;
            }
            case ThrowInstruction i ->
                stack = null;
            case TypeCheckInstruction i -> {
                switch (i.opcode()) {
                    case CHECKCAST -> {
                        pop(1);push(TypeKind.ReferenceType);
                    }
                    case INSTANCEOF -> {
                        pop(1);push(TypeKind.IntType);
                    }
                }
            }
            case ExceptionCatch i ->
                map.put(i.handler(), new Stack(new Item(TypeKind.ReferenceType, null), 1, 1));
            case LabelTarget i ->
                stack = map.getOrDefault(i.label(), stack);
            default -> {}
        }
    }
}
