/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

// Production is base class for all elements of the IR-tree.
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.DoWhile;
import jdk.test.lib.jittester.loops.While;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.visitors.Visitor;

public abstract class IRNode {
    private IRNode parent;
    private final List<IRNode> children = new ArrayList<>();
    protected IRNode klass;
    protected int level;
    //TODO
    //private boolean isCFDeviation;

    public abstract <T> T accept(Visitor<T> v);

    public void setKlass(TypeKlass klass) {
        this.klass = klass;
        if (Objects.isNull(klass)) {
            System.out.println(getClass().getName() + " null");
            for (StackTraceElement s : Thread.currentThread().getStackTrace()) {
                System.out.println(s.toString());
            }
        }
    }

    public void addChild(IRNode child) {
        children.add(child);
        if (!Objects.isNull(child)) {
            child.parent = this;
        }
    }

    public void addChildren(List<? extends IRNode> ch) {
        if (!Objects.isNull(ch)) {
            children.addAll(ch);
            for (IRNode c : ch) {
                if (!Objects.isNull(c)) {
                    c.parent = this;
                }
            }
        }
    }

    public List<IRNode> getChildren() {
        return children;
    }

    public IRNode getChild(int i) {
        return i < children.size() ? children.get(i) : null;
    }

    public IRNode getKlass() {
        return klass;
    }

    public IRNode getParent() {
        return parent;
    }

    public void setChild(int index, IRNode child) {
        children.set(index, child);
        if (!Objects.isNull(child)) {
            child.parent = this;
        }
    }

    public boolean removeChild(IRNode l) {
        return children.remove(l);
    }

    public boolean removeSelf() {
        return parent.children.remove(this);
    }

    public void resizeUpChildren(int size) {
        for (int i = children.size(); i < size; ++i) {
            children.add(null);
        }
    }

    public void removeAllChildren() {
        children.clear();
    }

    public String getTreeTextView(int indent) {
        StringBuilder sb = new StringBuilder();
        if (level > 0) {
        for (int i = 0; i < indent; ++i) {
            sb.append("\t");
        }
        sb.append(getName())
                .append(" [")
                .append(level)
                .append("]")
                    .append(System.lineSeparator());
        }
        children.stream()
                .filter(ch -> !Objects.isNull(ch))
                .forEach(ch -> sb.append(ch.getTreeTextView(indent + 1)));
        return sb.toString();
    }

    protected IRNode evolve() {
        throw new Error("Not implemented");
    }

    public int getLevel() {
        return level;
    }

    public long complexity() {
        return 0L;
    }

    @Override
    public final String toString() {
        throw new Error("Should be toJavaCode");
    }

    public String getName() {
        return this.getClass().getName();
    }

    public static long countDepth(Collection<IRNode> input) {
        return input.stream()
                .filter(c -> !Objects.isNull(c))
                .mapToLong(IRNode::countDepth)
                .max().orElse(0L);
    }

    public long countDepth() {
        return IRNode.countDepth(children);
    }

    public List<IRNode> getStackableLeaves() {
        List<IRNode> result = new ArrayList<>();
        children.stream()
                .filter(c -> !Objects.isNull(c))
                .forEach(c -> {
                    if (countDepth() == c.level && (c instanceof Block)) {
                        result.add(c);
                    } else {
                        result.addAll(c.getStackableLeaves());
                    }
                });
        return result;
    }

    public List<IRNode> getDeviantBlocks(long depth) {
        List<IRNode> result = new ArrayList<>();
        children.stream()
                .filter(c -> !Objects.isNull(c))
                .forEach(c -> {
            if (depth == c.level && c.isCFDeviation()) {
                        result.add(c);
                    } else {
                        result.addAll(c.getDeviantBlocks(depth));
                    }
                });
        return result;
    }

    // TODO: add field instead this function
    public boolean isCFDeviation() {
        return this instanceof If || this instanceof Switch
            || this instanceof For || this instanceof While
            || this instanceof DoWhile
            || (this instanceof Block && this.parent instanceof Block);
    }
}
