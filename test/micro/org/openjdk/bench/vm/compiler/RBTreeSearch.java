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
 */

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/*
 * This benchmark is used as easy reproducer of JDK-8305995
 *
 * This benchmark contains simplified and minimized RB-tree
 * which is based on fasutils with iterators that jumps.
 *
 * At the end it contains a tree serialized as lines, and
 * maxPattern which is used to search in this tree.
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class RBTreeSearch {

    private final Tree pattern;

    private final Tree[] nodes;

    private final Tree root;

    private final int[] idxStack;
    private final Tree[] objStack;

    public RBTreeSearch() {
        idxStack = new int[maxPattern];

        objStack = new Tree[maxPattern];

        pattern = new Tree();
        for (int i = 0; i <= maxPattern; i++) {
            pattern.put(i, i);
        }

        nodes = new Tree[directions.length];
        for (int i = 0; i < directions.length; i++) {
            if (directions[i] == null) {
                continue;
            }
            Tree kids = new Tree();
            nodes[i] = kids;
            for (String pair : directions[i].split(", ")) {
                String[] kv = pair.split("=>");
                kids.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
            }
        }

        root = nodes[0];
    }

    @Benchmark
    public void search() {
        Tree.Iterator sliceIt = pattern.keyIterator();

        int stackSize = 0;
        idxStack[stackSize] = pattern.firstIntKey();
        objStack[stackSize++] = root;

        while (stackSize > 0) {
            stackSize--;
            Tree node = objStack[stackSize];

            final int startPoint = Math.max(idxStack[stackSize], node.firstIntKey()) - 1;
            final Tree.Iterator rootIt = node.keyIterator(startPoint);

            sliceIt.jump(startPoint);
            while (sliceIt.hasNext() && rootIt.hasNext()) {
                final int sliceElem = sliceIt.nextInt();
                final int rootElem = rootIt.nextInt();
                if (sliceElem < rootElem) {
                    rootIt.previousInt();
                    if (sliceIt.nextInt() >= rootElem) {
                        sliceIt.previousInt();
                    } else {
                        sliceIt.jump(rootElem - 1);
                    }
                } else if (sliceElem == rootElem) {
                    final int childrenIdx = node.get(sliceElem);
                    final Tree children = nodes[childrenIdx];

                    if (children != null) {
                        idxStack[stackSize] = sliceElem;
                        objStack[stackSize++] = children;
                    }
                }
            }
        }
    }

    public static class Tree {

        protected transient Entry root;

        protected transient Entry firstEntry;

        protected transient Entry lastEntry;

        private final transient boolean[] dirPath = new boolean[64];

        private final transient Entry[] nodePath = new Entry[64];

        public int put(final int k, final int v) {
            Entry e = add(k);
            final int oldValue = e.value;
            e.value = v;
            return oldValue;
        }

        private Entry add(final int k) {
            int maxDepth = 0;
            Entry e;
            if (root == null) {
                e = root = lastEntry = firstEntry = new Entry(k, 0);
            }
            else {
                Entry p = root;
                int cmp, i = 0;
                while(true) {
                    if ((cmp = Integer.compare(k, p.key)) == 0) {
                        while(i-- != 0) nodePath[i] = null;
                        return p;
                    }
                    nodePath[i] = p;
                    if (dirPath[i++] = cmp > 0) {
                        if (p.succ()) {
                            e = new Entry(k, 0);
                            if (p.right == null) lastEntry = e;
                            e.left = p;
                            e.right = p.right;
                            p.right(e);
                            break;
                        }
                        p = p.right;
                    }
                    else {
                        if (p.pred()) {
                            e = new Entry(k, 0);
                            if (p.left == null) firstEntry = e;
                            e.right = p;
                            e.left = p.left;
                            p.left(e);
                            break;
                        }
                        p = p.left;
                    }
                }
                maxDepth = i--;
                while(i > 0 && ! nodePath[i].black()) {
                    if (! dirPath[i - 1]) {
                        Entry y = nodePath[i - 1].right;
                        if (! nodePath[i - 1].succ() && ! y.black()) {
                            nodePath[i].black(true);
                            y.black(true);
                            nodePath[i - 1].black(false);
                            i -= 2;
                        }
                        else {
                            Entry x;
                            if (! dirPath[i]) y = nodePath[i];
                            else {
                                x = nodePath[i];
                                y = x.right;
                                x.right = y.left;
                                y.left = x;
                                nodePath[i - 1].left = y;
                                if (y.pred()) {
                                    y.pred(false);
                                    x.succ(y);
                                }
                            }
                            x = nodePath[i - 1];
                            x.black(false);
                            y.black(true);
                            x.left = y.right;
                            y.right = x;
                            if (i < 2) root = y;
                            else {
                                if (dirPath[i - 2]) nodePath[i - 2].right = y;
                                else nodePath[i - 2].left = y;
                            }
                            if (y.succ()) {
                                y.succ(false);
                                x.pred(y);
                            }
                            break;
                        }
                    }
                    else {
                        Entry y = nodePath[i - 1].left;
                        if (! nodePath[i - 1].pred() && ! y.black()) {
                            nodePath[i].black(true);
                            y.black(true);
                            nodePath[i - 1].black(false);
                            i -= 2;
                        }
                        else {
                            Entry x;
                            if (dirPath[i]) y = nodePath[i];
                            else {
                                x = nodePath[i];
                                y = x.left;
                                x.left = y.right;
                                y.right = x;
                                nodePath[i - 1].right = y;
                                if (y.succ()) {
                                    y.succ(false);
                                    x.pred(y);
                                }
                            }
                            x = nodePath[i - 1];
                            x.black(false);
                            y.black(true);
                            x.right = y.left;
                            y.left = x;
                            if (i < 2) root = y;
                            else {
                                if (dirPath[i - 2]) nodePath[i - 2].right = y;
                                else nodePath[i - 2].left = y;
                            }
                            if (y.pred()){
                                y.pred(false);
                                x.succ(y);
                            }
                            break;
                        }
                    }
                }
            }
            root.black(true);
            while(maxDepth-- != 0) nodePath[maxDepth] = null;
            return e;
        }

        private static final class Entry {
            int key;
            int value;

            private static final int BLACK_MASK = 1;

            private static final int SUCC_MASK = 1 << 31;

            private static final int PRED_MASK = 1 << 30;

            Entry left, right;

            int info;

            Entry(final int k, final int v) {
                key = k;
                value = v;
                info = SUCC_MASK | PRED_MASK;
            }

            Entry left() {
                return (info & PRED_MASK) != 0 ? null : left;
            }

            Entry right() {
                return (info & SUCC_MASK) != 0 ? null : right;
            }

            boolean pred() {
                return (info & PRED_MASK) != 0;
            }

            boolean succ() {
                return (info & SUCC_MASK) != 0;
            }

            void pred(final boolean pred) {
                if (pred) info |= PRED_MASK;
                else info &= ~PRED_MASK;
            }

            void succ(final boolean succ) {
                if (succ) info |= SUCC_MASK;
                else info &= ~SUCC_MASK;
            }

            void pred(final Entry pred) {
                info |= PRED_MASK;
                left = pred;
            }

            void succ(final Entry succ) {
                info |= SUCC_MASK;
                right = succ;
            }

            void left(final Entry left) {
                info &= ~PRED_MASK;
                this.left = left;
            }

            void right(final Entry right) {
                info &= ~SUCC_MASK;
                this.right = right;
            }

            boolean black() {
                return (info & BLACK_MASK) != 0;
            }

            void black(final boolean black) {
                if (black) info |= BLACK_MASK;
                else info &= ~BLACK_MASK;
            }

            Entry next() {
                Entry next = this.right;
                if ((info & SUCC_MASK) == 0) while ((next.info & PRED_MASK) == 0) next = next.left;
                return next;
            }

            Entry prev() {
                Entry prev = this.left;
                if ((info & PRED_MASK) == 0) while ((prev.info & SUCC_MASK) == 0) prev = prev.right;
                return prev;
            }
        }

        public int get(final int k) {
            Entry e = root;
            int cmp;
            while (e != null && (cmp = Integer.compare(k, e.key)) != 0) {
                e = cmp < 0 ? e.left() : e.right();
            }
            return e == null ? 0 : e.value;
        }

        public int firstIntKey() {
            return firstEntry.key;
        }

        interface Iterator {
            boolean hasNext();
            int nextInt();
            int previousInt();
            void jump(final int fromElement);
        }

        private class KeyIteratorImpl implements Iterator {
            Entry prev;

            Entry next;

            Entry curr;

            int index = 0;

            KeyIteratorImpl() {
                next = firstEntry;
            }

            KeyIteratorImpl(final int k) {
                if ((next = locateKey(k)) != null) {
                    if (next.key <= k) {
                        prev = next;
                        next = next.next();
                    }
                    else prev = next.prev();
                }
            }

            private Entry locateKey(final int k) {
                Entry e = root, last = root;
                int cmp = 0;
                while (e != null && (cmp = Integer.compare(k, e.key)) != 0) {
                    last = e;
                    e = cmp < 0 ? e.left() : e.right();
                }
                return cmp == 0 ? e : last;
            }

            public boolean hasNext() { return next != null; }

            Entry nextEntry() {
                curr = prev = next;
                index++;
                next = next.next();
                return curr;
            }

            Entry previousEntry() {
                curr = next = prev;
                index--;
                prev = prev.prev();
                return curr;
            }
            public void jump(final int fromElement) {
                if ((next = locateKey(fromElement)) != null) {
                    if (next.key <= fromElement) {
                        prev = next;
                        next = next.next();
                    }
                    else prev = next.prev();
                }
            }

            public int nextInt() { return nextEntry().key; }

            public int previousInt() { return previousEntry().key; }

        }

        public Iterator keyIterator() {
            return new KeyIteratorImpl();
        }

        public Iterator keyIterator(final int from) {
            return new KeyIteratorImpl(from);
        }
    }

    private static final int maxPattern = 39;

    private static final String[] directions = {
            "0=>1, 1=>4, 2=>2, 4=>3, 7=>5",
            "13=>628, 14=>627, 15=>626, 17=>629, 18=>630",
            "13=>473, 14=>472, 15=>471, 17=>474, 18=>475",
            "13=>318, 14=>317, 15=>316, 17=>319, 18=>320",
            "13=>163, 14=>162, 15=>161, 17=>164, 18=>165",
            "13=>8, 14=>7, 15=>6, 17=>9, 18=>10",
            "22=>135, 23=>134, 24=>132, 26=>133, 27=>131",
            "22=>105, 23=>104, 24=>102, 26=>103, 27=>101",
            "22=>75, 23=>74, 24=>72, 26=>73, 27=>71",
            "22=>45, 23=>44, 24=>42, 26=>43, 27=>41",
            "22=>15, 23=>14, 24=>12, 26=>13, 27=>11",
            "31=>38, 32=>39, 33=>36, 34=>40, 35=>37",
            "31=>33, 32=>34, 33=>31, 34=>35, 35=>32",
            "31=>28, 32=>29, 33=>26, 34=>30, 35=>27",
            "31=>23, 32=>24, 33=>21, 34=>25, 35=>22",
            "31=>18, 32=>19, 33=>16, 34=>20, 35=>17",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>68, 32=>69, 33=>66, 34=>70, 35=>67",
            "31=>63, 32=>64, 33=>61, 34=>65, 35=>62",
            "31=>58, 32=>59, 33=>56, 34=>60, 35=>57",
            "31=>53, 32=>54, 33=>51, 34=>55, 35=>52",
            "31=>48, 32=>49, 33=>46, 34=>50, 35=>47",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>98, 32=>99, 33=>96, 34=>100, 35=>97",
            "31=>93, 32=>94, 33=>91, 34=>95, 35=>92",
            "31=>88, 32=>89, 33=>86, 34=>90, 35=>87",
            "31=>83, 32=>84, 33=>81, 34=>85, 35=>82",
            "31=>78, 32=>79, 33=>76, 34=>80, 35=>77",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>128, 32=>129, 33=>126, 34=>130, 35=>127",
            "31=>123, 32=>124, 33=>121, 34=>125, 35=>122",
            "31=>118, 32=>119, 33=>116, 34=>120, 35=>117",
            "31=>113, 32=>114, 33=>111, 34=>115, 35=>112",
            "31=>108, 32=>109, 33=>106, 34=>110, 35=>107",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>158, 32=>159, 33=>156, 34=>160, 35=>157",
            "31=>153, 32=>154, 33=>151, 34=>155, 35=>152",
            "31=>148, 32=>149, 33=>146, 34=>150, 35=>147",
            "31=>143, 32=>144, 33=>141, 34=>145, 35=>142",
            "31=>138, 32=>139, 33=>136, 34=>140, 35=>137",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "22=>290, 23=>289, 24=>287, 26=>288, 27=>286",
            "22=>260, 23=>259, 24=>257, 26=>258, 27=>256",
            "22=>230, 23=>229, 24=>227, 26=>228, 27=>226",
            "22=>200, 23=>199, 24=>197, 26=>198, 27=>196",
            "22=>170, 23=>169, 24=>167, 26=>168, 27=>166",
            "31=>193, 32=>194, 33=>191, 34=>195, 35=>192",
            "31=>188, 32=>189, 33=>186, 34=>190, 35=>187",
            "31=>183, 32=>184, 33=>181, 34=>185, 35=>182",
            "31=>178, 32=>179, 33=>176, 34=>180, 35=>177",
            "31=>173, 32=>174, 33=>171, 34=>175, 35=>172",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>223, 32=>224, 33=>221, 34=>225, 35=>222",
            "31=>218, 32=>219, 33=>216, 34=>220, 35=>217",
            "31=>213, 32=>214, 33=>211, 34=>215, 35=>212",
            "31=>208, 32=>209, 33=>206, 34=>210, 35=>207",
            "31=>203, 32=>204, 33=>201, 34=>205, 35=>202",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>253, 32=>254, 33=>251, 34=>255, 35=>252",
            "31=>248, 32=>249, 33=>246, 34=>250, 35=>247",
            "31=>243, 32=>244, 33=>241, 34=>245, 35=>242",
            "31=>238, 32=>239, 33=>236, 34=>240, 35=>237",
            "31=>233, 32=>234, 33=>231, 34=>235, 35=>232",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>283, 32=>284, 33=>281, 34=>285, 35=>282",
            "31=>278, 32=>279, 33=>276, 34=>280, 35=>277",
            "31=>273, 32=>274, 33=>271, 34=>275, 35=>272",
            "31=>268, 32=>269, 33=>266, 34=>270, 35=>267",
            "31=>263, 32=>264, 33=>261, 34=>265, 35=>262",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>313, 32=>314, 33=>311, 34=>315, 35=>312",
            "31=>308, 32=>309, 33=>306, 34=>310, 35=>307",
            "31=>303, 32=>304, 33=>301, 34=>305, 35=>302",
            "31=>298, 32=>299, 33=>296, 34=>300, 35=>297",
            "31=>293, 32=>294, 33=>291, 34=>295, 35=>292",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "22=>445, 23=>444, 24=>442, 26=>443, 27=>441",
            "22=>415, 23=>414, 24=>412, 26=>413, 27=>411",
            "22=>385, 23=>384, 24=>382, 26=>383, 27=>381",
            "22=>355, 23=>354, 24=>352, 26=>353, 27=>351",
            "22=>325, 23=>324, 24=>322, 26=>323, 27=>321",
            "31=>348, 32=>349, 33=>346, 34=>350, 35=>347",
            "31=>343, 32=>344, 33=>341, 34=>345, 35=>342",
            "31=>338, 32=>339, 33=>336, 34=>340, 35=>337",
            "31=>333, 32=>334, 33=>331, 34=>335, 35=>332",
            "31=>328, 32=>329, 33=>326, 34=>330, 35=>327",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>378, 32=>379, 33=>376, 34=>380, 35=>377",
            "31=>373, 32=>374, 33=>371, 34=>375, 35=>372",
            "31=>368, 32=>369, 33=>366, 34=>370, 35=>367",
            "31=>363, 32=>364, 33=>361, 34=>365, 35=>362",
            "31=>358, 32=>359, 33=>356, 34=>360, 35=>357",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>408, 32=>409, 33=>406, 34=>410, 35=>407",
            "31=>403, 32=>404, 33=>401, 34=>405, 35=>402",
            "31=>398, 32=>399, 33=>396, 34=>400, 35=>397",
            "31=>393, 32=>394, 33=>391, 34=>395, 35=>392",
            "31=>388, 32=>389, 33=>386, 34=>390, 35=>387",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>438, 32=>439, 33=>436, 34=>440, 35=>437",
            "31=>433, 32=>434, 33=>431, 34=>435, 35=>432",
            "31=>428, 32=>429, 33=>426, 34=>430, 35=>427",
            "31=>423, 32=>424, 33=>421, 34=>425, 35=>422",
            "31=>418, 32=>419, 33=>416, 34=>420, 35=>417",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>468, 32=>469, 33=>466, 34=>470, 35=>467",
            "31=>463, 32=>464, 33=>461, 34=>465, 35=>462",
            "31=>458, 32=>459, 33=>456, 34=>460, 35=>457",
            "31=>453, 32=>454, 33=>451, 34=>455, 35=>452",
            "31=>448, 32=>449, 33=>446, 34=>450, 35=>447",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "22=>600, 23=>599, 24=>597, 26=>598, 27=>596",
            "22=>570, 23=>569, 24=>567, 26=>568, 27=>566",
            "22=>540, 23=>539, 24=>537, 26=>538, 27=>536",
            "22=>510, 23=>509, 24=>507, 26=>508, 27=>506",
            "22=>480, 23=>479, 24=>477, 26=>478, 27=>476",
            "31=>503, 32=>504, 33=>501, 34=>505, 35=>502",
            "31=>498, 32=>499, 33=>496, 34=>500, 35=>497",
            "31=>493, 32=>494, 33=>491, 34=>495, 35=>492",
            "31=>488, 32=>489, 33=>486, 34=>490, 35=>487",
            "31=>483, 32=>484, 33=>481, 34=>485, 35=>482",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>533, 32=>534, 33=>531, 34=>535, 35=>532",
            "31=>528, 32=>529, 33=>526, 34=>530, 35=>527",
            "31=>523, 32=>524, 33=>521, 34=>525, 35=>522",
            "31=>518, 32=>519, 33=>516, 34=>520, 35=>517",
            "31=>513, 32=>514, 33=>511, 34=>515, 35=>512",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>563, 32=>564, 33=>561, 34=>565, 35=>562",
            "31=>558, 32=>559, 33=>556, 34=>560, 35=>557",
            "31=>553, 32=>554, 33=>551, 34=>555, 35=>552",
            "31=>548, 32=>549, 33=>546, 34=>550, 35=>547",
            "31=>543, 32=>544, 33=>541, 34=>545, 35=>542",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>593, 32=>594, 33=>591, 34=>595, 35=>592",
            "31=>588, 32=>589, 33=>586, 34=>590, 35=>587",
            "31=>583, 32=>584, 33=>581, 34=>585, 35=>582",
            "31=>578, 32=>579, 33=>576, 34=>580, 35=>577",
            "31=>573, 32=>574, 33=>571, 34=>575, 35=>572",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>623, 32=>624, 33=>621, 34=>625, 35=>622",
            "31=>618, 32=>619, 33=>616, 34=>620, 35=>617",
            "31=>613, 32=>614, 33=>611, 34=>615, 35=>612",
            "31=>608, 32=>609, 33=>606, 34=>610, 35=>607",
            "31=>603, 32=>604, 33=>601, 34=>605, 35=>602",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "22=>755, 23=>754, 24=>752, 26=>753, 27=>751",
            "22=>725, 23=>724, 24=>722, 26=>723, 27=>721",
            "22=>695, 23=>694, 24=>692, 26=>693, 27=>691",
            "22=>665, 23=>664, 24=>662, 26=>663, 27=>661",
            "22=>635, 23=>634, 24=>632, 26=>633, 27=>631",
            "31=>658, 32=>659, 33=>656, 34=>660, 35=>657",
            "31=>653, 32=>654, 33=>651, 34=>655, 35=>652",
            "31=>648, 32=>649, 33=>646, 34=>650, 35=>647",
            "31=>643, 32=>644, 33=>641, 34=>645, 35=>642",
            "31=>638, 32=>639, 33=>636, 34=>640, 35=>637",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>688, 32=>689, 33=>686, 34=>690, 35=>687",
            "31=>683, 32=>684, 33=>681, 34=>685, 35=>682",
            "31=>678, 32=>679, 33=>676, 34=>680, 35=>677",
            "31=>673, 32=>674, 33=>671, 34=>675, 35=>672",
            "31=>668, 32=>669, 33=>666, 34=>670, 35=>667",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>718, 32=>719, 33=>716, 34=>720, 35=>717",
            "31=>713, 32=>714, 33=>711, 34=>715, 35=>712",
            "31=>708, 32=>709, 33=>706, 34=>710, 35=>707",
            "31=>703, 32=>704, 33=>701, 34=>705, 35=>702",
            "31=>698, 32=>699, 33=>696, 34=>700, 35=>697",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>748, 32=>749, 33=>746, 34=>750, 35=>747",
            "31=>743, 32=>744, 33=>741, 34=>745, 35=>742",
            "31=>738, 32=>739, 33=>736, 34=>740, 35=>737",
            "31=>733, 32=>734, 33=>731, 34=>735, 35=>732",
            "31=>728, 32=>729, 33=>726, 34=>730, 35=>727",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "31=>778, 32=>779, 33=>776, 34=>780, 35=>777",
            "31=>773, 32=>774, 33=>771, 34=>775, 35=>772",
            "31=>768, 32=>769, 33=>766, 34=>770, 35=>767",
            "31=>763, 32=>764, 33=>761, 34=>765, 35=>762",
            "31=>758, 32=>759, 33=>756, 34=>760, 35=>757",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    };
}
