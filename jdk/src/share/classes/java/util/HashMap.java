/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Hash table based implementation of the <tt>Map</tt> interface.  This
 * implementation provides all of the optional map operations, and permits
 * <tt>null</tt> values and the <tt>null</tt> key.  (The <tt>HashMap</tt>
 * class is roughly equivalent to <tt>Hashtable</tt>, except that it is
 * unsynchronized and permits nulls.)  This class makes no guarantees as to
 * the order of the map; in particular, it does not guarantee that the order
 * will remain constant over time.
 *
 * <p>This implementation provides constant-time performance for the basic
 * operations (<tt>get</tt> and <tt>put</tt>), assuming the hash function
 * disperses the elements properly among the buckets.  Iteration over
 * collection views requires time proportional to the "capacity" of the
 * <tt>HashMap</tt> instance (the number of buckets) plus its size (the number
 * of key-value mappings).  Thus, it's very important not to set the initial
 * capacity too high (or the load factor too low) if iteration performance is
 * important.
 *
 * <p>An instance of <tt>HashMap</tt> has two parameters that affect its
 * performance: <i>initial capacity</i> and <i>load factor</i>.  The
 * <i>capacity</i> is the number of buckets in the hash table, and the initial
 * capacity is simply the capacity at the time the hash table is created.  The
 * <i>load factor</i> is a measure of how full the hash table is allowed to
 * get before its capacity is automatically increased.  When the number of
 * entries in the hash table exceeds the product of the load factor and the
 * current capacity, the hash table is <i>rehashed</i> (that is, internal data
 * structures are rebuilt) so that the hash table has approximately twice the
 * number of buckets.
 *
 * <p>As a general rule, the default load factor (.75) offers a good tradeoff
 * between time and space costs.  Higher values decrease the space overhead
 * but increase the lookup cost (reflected in most of the operations of the
 * <tt>HashMap</tt> class, including <tt>get</tt> and <tt>put</tt>).  The
 * expected number of entries in the map and its load factor should be taken
 * into account when setting its initial capacity, so as to minimize the
 * number of rehash operations.  If the initial capacity is greater
 * than the maximum number of entries divided by the load factor, no
 * rehash operations will ever occur.
 *
 * <p>If many mappings are to be stored in a <tt>HashMap</tt> instance,
 * creating it with a sufficiently large capacity will allow the mappings to
 * be stored more efficiently than letting it perform automatic rehashing as
 * needed to grow the table.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a hash map concurrently, and at least one of
 * the threads modifies the map structurally, it <i>must</i> be
 * synchronized externally.  (A structural modification is any operation
 * that adds or deletes one or more mappings; merely changing the value
 * associated with a key that an instance already contains is not a
 * structural modification.)  This is typically accomplished by
 * synchronizing on some object that naturally encapsulates the map.
 *
 * If no such object exists, the map should be "wrapped" using the
 * {@link Collections#synchronizedMap Collections.synchronizedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the map:<pre>
 *   Map m = Collections.synchronizedMap(new HashMap(...));</pre>
 *
 * <p>The iterators returned by all of this class's "collection view methods"
 * are <i>fail-fast</i>: if the map is structurally modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * <tt>remove</tt> method, the iterator will throw a
 * {@link ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the
 * future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw <tt>ConcurrentModificationException</tt> on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness: <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Doug Lea
 * @author  Josh Bloch
 * @author  Arthur van Hoff
 * @author  Neal Gafter
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Map
 * @see     TreeMap
 * @see     Hashtable
 * @since   1.2
 */

public class HashMap<K,V>
        extends AbstractMap<K,V>
    implements Map<K,V>, Cloneable, Serializable
{

    /**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * An empty table instance to share when the table is not inflated.
     */
    static final Object[] EMPTY_TABLE = {};

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     */
    transient Object[] table = EMPTY_TABLE;

    /**
     * The number of key-value mappings contained in this map.
     */
    transient int size;

    /**
     * The next size value at which to resize (capacity * load factor).
     * @serial
     */
    // If table == EMPTY_TABLE then this is the initial capacity at which the
    // table will be created when inflated.
    int threshold;

    /**
     * The load factor for the hash table.
     *
     * @serial
     */
    final float loadFactor;

    /**
     * The number of times this HashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the HashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the HashMap fail-fast.  (See ConcurrentModificationException).
     */
    transient int modCount;

    /**
     * Holds values which can't be initialized until after VM is booted.
     */
    private static class Holder {
        static final sun.misc.Unsafe UNSAFE;

        /**
         * Offset of "final" hashSeed field we must set in
         * readObject() method.
         */
        static final long HASHSEED_OFFSET;

        static final boolean USE_HASHSEED;

        static {
            String hashSeedProp = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction(
                        "jdk.map.useRandomSeed"));
            boolean localBool = (null != hashSeedProp)
                    ? Boolean.parseBoolean(hashSeedProp) : false;
            USE_HASHSEED = localBool;

            if (USE_HASHSEED) {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    HASHSEED_OFFSET = UNSAFE.objectFieldOffset(
                        HashMap.class.getDeclaredField("hashSeed"));
                } catch (NoSuchFieldException | SecurityException e) {
                    throw new InternalError("Failed to record hashSeed offset", e);
                }
            } else {
                UNSAFE = null;
                HASHSEED_OFFSET = 0;
            }
        }
    }

    /*
     * A randomizing value associated with this instance that is applied to
     * hash code of keys to make hash collisions harder to find.
     *
     * Non-final so it can be set lazily, but be sure not to set more than once.
     */
    transient final int hashSeed;

    /*
     * TreeBin/TreeNode code from CHM doesn't handle the null key.  Store the
     * null key entry here.
     */
    transient Entry<K,V> nullKeyEntry = null;

    /*
     * In order to improve performance under high hash-collision conditions,
     * HashMap will switch to storing a bin's entries in a balanced tree
     * (TreeBin) instead of a linked-list once the number of entries in the bin
     * passes a certain threshold (TreeBin.TREE_THRESHOLD), if at least one of
     * the keys in the bin implements Comparable.  This technique is borrowed
     * from ConcurrentHashMap.
     */

    /*
     * Code based on CHMv8
     *
     * Node type for TreeBin
     */
    final static class TreeNode<K,V> {
        TreeNode parent;  // red-black tree links
        TreeNode left;
        TreeNode right;
        TreeNode prev;    // needed to unlink next upon deletion
        boolean red;
        final HashMap.Entry<K,V> entry;

        TreeNode(HashMap.Entry<K,V> entry, Object next, TreeNode parent) {
            this.entry = entry;
            this.entry.next = next;
            this.parent = parent;
        }
    }

    /**
     * Returns a Class for the given object of the form "class C
     * implements Comparable<C>", if one exists, else null.  See the TreeBin
     * docs, below, for explanation.
     */
    static Class<?> comparableClassFor(Object x) {
        Class<?> c, s, cmpc; Type[] ts, as; Type t; ParameterizedType p;
        if ((c = x.getClass()) == String.class) // bypass checks
            return c;
        if ((cmpc = Comparable.class).isAssignableFrom(c)) {
            while (cmpc.isAssignableFrom(s = c.getSuperclass()))
                c = s; // find topmost comparable class
            if ((ts  = c.getGenericInterfaces()) != null) {
                for (int i = 0; i < ts.length; ++i) {
                    if (((t = ts[i]) instanceof ParameterizedType) &&
                        ((p = (ParameterizedType)t).getRawType() == cmpc) &&
                        (as = p.getActualTypeArguments()) != null &&
                        as.length == 1 && as[0] == c) // type arg is c
                        return c;
                }
            }
        }
        return null;
    }

    /*
     * Code based on CHMv8
     *
     * A specialized form of red-black tree for use in bins
     * whose size exceeds a threshold.
     *
     * TreeBins use a special form of comparison for search and
     * related operations (which is the main reason we cannot use
     * existing collections such as TreeMaps). TreeBins contain
     * Comparable elements, but may contain others, as well as
     * elements that are Comparable but not necessarily Comparable<T>
     * for the same T, so we cannot invoke compareTo among them. To
     * handle this, the tree is ordered primarily by hash value, then
     * by Comparable.compareTo order if applicable.  On lookup at a
     * node, if elements are not comparable or compare as 0 then both
     * left and right children may need to be searched in the case of
     * tied hash values. (This corresponds to the full list search
     * that would be necessary if all elements were non-Comparable and
     * had tied hashes.)  The red-black balancing code is updated from
     * pre-jdk-collections
     * (http://gee.cs.oswego.edu/dl/classes/collections/RBCell.java)
     * based in turn on Cormen, Leiserson, and Rivest "Introduction to
     * Algorithms" (CLR).
     */
    final class TreeBin {
        /*
         * The bin count threshold for using a tree rather than list for a bin. The
         * value reflects the approximate break-even point for using tree-based
         * operations.
         */
        static final int TREE_THRESHOLD = 16;

        TreeNode<K,V> root;  // root of tree
        TreeNode<K,V> first; // head of next-pointer list

        /*
         * Split a TreeBin into lo and hi parts and install in given table.
         *
         * Existing Entrys are re-used, which maintains the before/after links for
         * LinkedHashMap.Entry.
         *
         * No check for Comparable, though this is the same as CHM.
         */
        final void splitTreeBin(Object[] newTable, int i, TreeBin loTree, TreeBin hiTree) {
            TreeBin oldTree = this;
            int bit = newTable.length >>> 1;
            int loCount = 0, hiCount = 0;
            TreeNode<K,V> e = oldTree.first;
            TreeNode<K,V> next;

            // This method is called when the table has just increased capacity,
            // so indexFor() is now taking one additional bit of hash into
            // account ("bit").  Entries in this TreeBin now belong in one of
            // two bins, "i" or "i+bit", depending on if the new top bit of the
            // hash is set.  The trees for the two bins are loTree and hiTree.
            // If either tree ends up containing fewer than TREE_THRESHOLD
            // entries, it is converted back to a linked list.
            while (e != null) {
                // Save entry.next - it will get overwritten in putTreeNode()
                next = (TreeNode<K,V>)e.entry.next;

                int h = e.entry.hash;
                K k = (K) e.entry.key;
                V v = e.entry.value;
                if ((h & bit) == 0) {
                    ++loCount;
                    // Re-using e.entry
                    loTree.putTreeNode(h, k, v, e.entry);
                } else {
                    ++hiCount;
                    hiTree.putTreeNode(h, k, v, e.entry);
                }
                // Iterate using the saved 'next'
                e = next;
            }
            if (loCount < TREE_THRESHOLD) { // too small, convert back to list
                HashMap.Entry loEntry = null;
                TreeNode<K,V> p = loTree.first;
                while (p != null) {
                    @SuppressWarnings("unchecked")
                    TreeNode<K,V> savedNext = (TreeNode<K,V>) p.entry.next;
                    p.entry.next = loEntry;
                    loEntry = p.entry;
                    p = savedNext;
                }
                // assert newTable[i] == null;
                newTable[i] = loEntry;
            } else {
                // assert newTable[i] == null;
                newTable[i] = loTree;
            }
            if (hiCount < TREE_THRESHOLD) { // too small, convert back to list
                HashMap.Entry hiEntry = null;
                TreeNode<K,V> p = hiTree.first;
                while (p != null) {
                    @SuppressWarnings("unchecked")
                    TreeNode<K,V> savedNext = (TreeNode<K,V>) p.entry.next;
                    p.entry.next = hiEntry;
                    hiEntry = p.entry;
                    p = savedNext;
                }
                // assert newTable[i + bit] == null;
                newTable[i + bit] = hiEntry;
            } else {
                // assert newTable[i + bit] == null;
                newTable[i + bit] = hiTree;
            }
        }

        /*
         * Popuplate the TreeBin with entries from the linked list e
         *
         * Assumes 'this' is a new/empty TreeBin
         *
         * Note: no check for Comparable
         * Note: I believe this changes iteration order
         */
        @SuppressWarnings("unchecked")
        void populate(HashMap.Entry e) {
            // assert root == null;
            // assert first == null;
            HashMap.Entry next;
            while (e != null) {
                // Save entry.next - it will get overwritten in putTreeNode()
                next = (HashMap.Entry)e.next;
                // Re-using Entry e will maintain before/after in LinkedHM
                putTreeNode(e.hash, (K)e.key, (V)e.value, e);
                // Iterate using the saved 'next'
                e = next;
            }
        }

        /**
         * Copied from CHMv8
         * From CLR
         */
        private void rotateLeft(TreeNode p) {
            if (p != null) {
                TreeNode r = p.right, pp, rl;
                if ((rl = p.right = r.left) != null) {
                    rl.parent = p;
                }
                if ((pp = r.parent = p.parent) == null) {
                    root = r;
                } else if (pp.left == p) {
                    pp.left = r;
                } else {
                    pp.right = r;
                }
                r.left = p;
                p.parent = r;
            }
        }

        /**
         * Copied from CHMv8
         * From CLR
         */
        private void rotateRight(TreeNode p) {
            if (p != null) {
                TreeNode l = p.left, pp, lr;
                if ((lr = p.left = l.right) != null) {
                    lr.parent = p;
                }
                if ((pp = l.parent = p.parent) == null) {
                    root = l;
                } else if (pp.right == p) {
                    pp.right = l;
                } else {
                    pp.left = l;
                }
                l.right = p;
                p.parent = l;
            }
        }

        /**
         * Returns the TreeNode (or null if not found) for the given
         * key.  A front-end for recursive version.
         */
        final TreeNode getTreeNode(int h, K k) {
            return getTreeNode(h, k, root, comparableClassFor(k));
        }

        /**
         * Returns the TreeNode (or null if not found) for the given key
         * starting at given root.
         */
        @SuppressWarnings("unchecked")
        final TreeNode getTreeNode (int h, K k, TreeNode p, Class<?> cc) {
            // assert k != null;
            while (p != null) {
                int dir, ph;  Object pk;
                if ((ph = p.entry.hash) != h)
                    dir = (h < ph) ? -1 : 1;
                else if ((pk = p.entry.key) == k || k.equals(pk))
                    return p;
                else if (cc == null || comparableClassFor(pk) != cc ||
                         (dir = ((Comparable<Object>)k).compareTo(pk)) == 0) {
                    // assert pk != null;
                    TreeNode r, pl, pr; // check both sides
                    if ((pr = p.right) != null &&
                        (r = getTreeNode(h, k, pr, cc)) != null)
                        return r;
                    else if ((pl = p.left) != null)
                        dir = -1;
                    else // nothing there
                        break;
                }
                p = (dir > 0) ? p.right : p.left;
            }
            return null;
        }

        /*
         * Finds or adds a node.
         *
         * 'entry' should be used to recycle an existing Entry (e.g. in the case
         * of converting a linked-list bin to a TreeBin).
         * If entry is null, a new Entry will be created for the new TreeNode
         *
         * @return the TreeNode containing the mapping, or null if a new
         * TreeNode was added
         */
        @SuppressWarnings("unchecked")
        TreeNode putTreeNode(int h, K k, V v, HashMap.Entry<K,V> entry) {
            // assert k != null;
            //if (entry != null) {
                // assert h == entry.hash;
                // assert k == entry.key;
                // assert v == entry.value;
            // }
            Class<?> cc = comparableClassFor(k);
            TreeNode pp = root, p = null;
            int dir = 0;
            while (pp != null) { // find existing node or leaf to insert at
                int ph;  Object pk;
                p = pp;
                if ((ph = p.entry.hash) != h)
                    dir = (h < ph) ? -1 : 1;
                else if ((pk = p.entry.key) == k || k.equals(pk))
                    return p;
                else if (cc == null || comparableClassFor(pk) != cc ||
                         (dir = ((Comparable<Object>)k).compareTo(pk)) == 0) {
                    TreeNode r, pr;
                    if ((pr = p.right) != null &&
                        (r = getTreeNode(h, k, pr, cc)) != null)
                        return r;
                    else // continue left
                        dir = -1;
                }
                pp = (dir > 0) ? p.right : p.left;
            }

            // Didn't find the mapping in the tree, so add it
            TreeNode f = first;
            TreeNode x;
            if (entry != null) {
                x = new TreeNode(entry, f, p);
            } else {
                x = new TreeNode(newEntry(h, k, v, null), f, p);
            }
            first = x;

            if (p == null) {
                root = x;
            } else { // attach and rebalance; adapted from CLR
                TreeNode xp, xpp;
                if (f != null) {
                    f.prev = x;
                }
                if (dir <= 0) {
                    p.left = x;
                } else {
                    p.right = x;
                }
                x.red = true;
                while (x != null && (xp = x.parent) != null && xp.red
                        && (xpp = xp.parent) != null) {
                    TreeNode xppl = xpp.left;
                    if (xp == xppl) {
                        TreeNode y = xpp.right;
                        if (y != null && y.red) {
                            y.red = false;
                            xp.red = false;
                            xpp.red = true;
                            x = xpp;
                        } else {
                            if (x == xp.right) {
                                rotateLeft(x = xp);
                                xpp = (xp = x.parent) == null ? null : xp.parent;
                            }
                            if (xp != null) {
                                xp.red = false;
                                if (xpp != null) {
                                    xpp.red = true;
                                    rotateRight(xpp);
                                }
                            }
                        }
                    } else {
                        TreeNode y = xppl;
                        if (y != null && y.red) {
                            y.red = false;
                            xp.red = false;
                            xpp.red = true;
                            x = xpp;
                        } else {
                            if (x == xp.left) {
                                rotateRight(x = xp);
                                xpp = (xp = x.parent) == null ? null : xp.parent;
                            }
                            if (xp != null) {
                                xp.red = false;
                                if (xpp != null) {
                                    xpp.red = true;
                                    rotateLeft(xpp);
                                }
                            }
                        }
                    }
                }
                TreeNode r = root;
                if (r != null && r.red) {
                    r.red = false;
                }
            }
            return null;
        }

        /*
         * From CHMv8
         *
         * Removes the given node, that must be present before this
         * call.  This is messier than typical red-black deletion code
         * because we cannot swap the contents of an interior node
         * with a leaf successor that is pinned by "next" pointers
         * that are accessible independently of lock. So instead we
         * swap the tree linkages.
         */
        final void deleteTreeNode(TreeNode p) {
            TreeNode next = (TreeNode) p.entry.next; // unlink traversal pointers
            TreeNode pred = p.prev;
            if (pred == null) {
                first = next;
            } else {
                pred.entry.next = next;
            }
            if (next != null) {
                next.prev = pred;
            }
            TreeNode replacement;
            TreeNode pl = p.left;
            TreeNode pr = p.right;
            if (pl != null && pr != null) {
                TreeNode s = pr, sl;
                while ((sl = s.left) != null) // find successor
                {
                    s = sl;
                }
                boolean c = s.red;
                s.red = p.red;
                p.red = c; // swap colors
                TreeNode sr = s.right;
                TreeNode pp = p.parent;
                if (s == pr) { // p was s's direct parent
                    p.parent = s;
                    s.right = p;
                } else {
                    TreeNode sp = s.parent;
                    if ((p.parent = sp) != null) {
                        if (s == sp.left) {
                            sp.left = p;
                        } else {
                            sp.right = p;
                        }
                    }
                    if ((s.right = pr) != null) {
                        pr.parent = s;
                    }
                }
                p.left = null;
                if ((p.right = sr) != null) {
                    sr.parent = p;
                }
                if ((s.left = pl) != null) {
                    pl.parent = s;
                }
                if ((s.parent = pp) == null) {
                    root = s;
                } else if (p == pp.left) {
                    pp.left = s;
                } else {
                    pp.right = s;
                }
                replacement = sr;
            } else {
                replacement = (pl != null) ? pl : pr;
            }
            TreeNode pp = p.parent;
            if (replacement == null) {
                if (pp == null) {
                    root = null;
                    return;
                }
                replacement = p;
            } else {
                replacement.parent = pp;
                if (pp == null) {
                    root = replacement;
                } else if (p == pp.left) {
                    pp.left = replacement;
                } else {
                    pp.right = replacement;
                }
                p.left = p.right = p.parent = null;
            }
            if (!p.red) { // rebalance, from CLR
                TreeNode x = replacement;
                while (x != null) {
                    TreeNode xp, xpl;
                    if (x.red || (xp = x.parent) == null) {
                        x.red = false;
                        break;
                    }
                    if (x == (xpl = xp.left)) {
                        TreeNode sib = xp.right;
                        if (sib != null && sib.red) {
                            sib.red = false;
                            xp.red = true;
                            rotateLeft(xp);
                            sib = (xp = x.parent) == null ? null : xp.right;
                        }
                        if (sib == null) {
                            x = xp;
                        } else {
                            TreeNode sl = sib.left, sr = sib.right;
                            if ((sr == null || !sr.red)
                                    && (sl == null || !sl.red)) {
                                sib.red = true;
                                x = xp;
                            } else {
                                if (sr == null || !sr.red) {
                                    if (sl != null) {
                                        sl.red = false;
                                    }
                                    sib.red = true;
                                    rotateRight(sib);
                                    sib = (xp = x.parent) == null ?
                                        null : xp.right;
                                }
                                if (sib != null) {
                                    sib.red = (xp == null) ? false : xp.red;
                                    if ((sr = sib.right) != null) {
                                        sr.red = false;
                                    }
                                }
                                if (xp != null) {
                                    xp.red = false;
                                    rotateLeft(xp);
                                }
                                x = root;
                            }
                        }
                    } else { // symmetric
                        TreeNode sib = xpl;
                        if (sib != null && sib.red) {
                            sib.red = false;
                            xp.red = true;
                            rotateRight(xp);
                            sib = (xp = x.parent) == null ? null : xp.left;
                        }
                        if (sib == null) {
                            x = xp;
                        } else {
                            TreeNode sl = sib.left, sr = sib.right;
                            if ((sl == null || !sl.red)
                                    && (sr == null || !sr.red)) {
                                sib.red = true;
                                x = xp;
                            } else {
                                if (sl == null || !sl.red) {
                                    if (sr != null) {
                                        sr.red = false;
                                    }
                                    sib.red = true;
                                    rotateLeft(sib);
                                    sib = (xp = x.parent) == null ?
                                        null : xp.left;
                                }
                                if (sib != null) {
                                    sib.red = (xp == null) ? false : xp.red;
                                    if ((sl = sib.left) != null) {
                                        sl.red = false;
                                    }
                                }
                                if (xp != null) {
                                    xp.red = false;
                                    rotateRight(xp);
                                }
                                x = root;
                            }
                        }
                    }
                }
            }
            if (p == replacement && (pp = p.parent) != null) {
                if (p == pp.left) // detach pointers
                {
                    pp.left = null;
                } else if (p == pp.right) {
                    pp.right = null;
                }
                p.parent = null;
            }
        }
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and load factor.
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);
        this.loadFactor = loadFactor;
        threshold = initialCapacity;
        hashSeed = initHashSeed();
        init();
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and the default load factor (0.75).
     *
     * @param  initialCapacity the initial capacity.
     * @throws IllegalArgumentException if the initial capacity is negative.
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the default initial capacity
     * (16) and the default load factor (0.75).
     */
    public HashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new <tt>HashMap</tt> with the same mappings as the
     * specified <tt>Map</tt>.  The <tt>HashMap</tt> is created with
     * default load factor (0.75) and an initial capacity sufficient to
     * hold the mappings in the specified <tt>Map</tt>.
     *
     * @param   m the map whose mappings are to be placed in this map
     * @throws  NullPointerException if the specified map is null
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
        inflateTable(threshold);

        putAllForCreate(m);
        // assert size == m.size();
    }

    private static int roundUpToPowerOf2(int number) {
        // assert number >= 0 : "number must be non-negative";
        int rounded = number >= MAXIMUM_CAPACITY
                ? MAXIMUM_CAPACITY
                : (rounded = Integer.highestOneBit(number)) != 0
                    ? (Integer.bitCount(number) > 1) ? rounded << 1 : rounded
                    : 1;

        return rounded;
    }

    /**
     * Inflates the table.
     */
    private void inflateTable(int toSize) {
        // Find a power of 2 >= toSize
        int capacity = roundUpToPowerOf2(toSize);

        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        table = new Object[capacity];
    }

    // internal utilities

    /**
     * Initialization hook for subclasses. This method is called
     * in all constructors and pseudo-constructors (clone, readObject)
     * after HashMap has been initialized but before any entries have
     * been inserted.  (In the absence of this method, readObject would
     * require explicit knowledge of subclasses.)
     */
    void init() {
    }

    /**
     * Return an initial value for the hashSeed, or 0 if the random seed is not
     * enabled.
     */
    final int initHashSeed() {
        if (sun.misc.VM.isBooted() && Holder.USE_HASHSEED) {
            int seed = ThreadLocalRandom.current().nextInt();
            return (seed != 0) ? seed : 1;
        }
        return 0;
    }

    /**
     * Retrieve object hash code and applies a supplemental hash function to the
     * result hash, which defends against poor quality hash functions. This is
     * critical because HashMap uses power-of-two length hash tables, that
     * otherwise encounter collisions for hashCodes that do not differ
     * in lower bits.
     */
    final int hash(Object k) {
        int  h = hashSeed ^ k.hashCode();

        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * Returns index for hash code h.
     */
    static int indexFor(int h, int length) {
        // assert Integer.bitCount(length) == 1 : "length must be a non-zero power of 2";
        return h & (length-1);
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise
     * it returns {@code null}.  (There can be at most one such mapping.)
     *
     * <p>A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     *
     * @see #put(Object, Object)
     */
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        Entry<K,V> entry = getEntry(key);

        return null == entry ? null : entry.getValue();
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        Entry<K,V> entry = getEntry(key);

        return (entry == null) ? defaultValue : entry.getValue();
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the
     * specified key.
     *
     * @param   key   The key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     * key.
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * Returns the entry associated with the specified key in the
     * HashMap.  Returns null if the HashMap contains no mapping
     * for the key.
     */
    @SuppressWarnings("unchecked")
    final Entry<K,V> getEntry(Object key) {
        if (isEmpty()) {
            return null;
        }
        if (key == null) {
            return nullKeyEntry;
        }
        int hash = hash(key);
        int bin = indexFor(hash, table.length);

        if (table[bin] instanceof Entry) {
            Entry<K,V> e = (Entry<K,V>) table[bin];
            for (; e != null; e = (Entry<K,V>)e.next) {
                Object k;
                if (e.hash == hash &&
                    ((k = e.key) == key || key.equals(k))) {
                    return e;
                }
            }
        } else if (table[bin] != null) {
            TreeBin e = (TreeBin)table[bin];
            TreeNode p = e.getTreeNode(hash, (K)key);
            if (p != null) {
                // assert p.entry.hash == hash && p.entry.key.equals(key);
                return (Entry<K,V>)p.entry;
            } else {
                return null;
            }
        }
        return null;
    }


    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
       if (key == null)
            return putForNullKey(value);
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        boolean checkIfNeedTree = false; // Might we convert bin to a TreeBin?

        if (table[i] instanceof Entry) {
            // Bin contains ordinary Entries.  Search for key in the linked list
            // of entries, counting the number of entries.  Only check for
            // TreeBin conversion if the list size is >= TREE_THRESHOLD.
            // (The conversion still may not happen if the table gets resized.)
            int listSize = 0;
            Entry<K,V> e = (Entry<K,V>) table[i];
            for (; e != null; e = (Entry<K,V>)e.next) {
                Object k;
                if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                    V oldValue = e.value;
                    e.value = value;
                    e.recordAccess(this);
                    return oldValue;
                }
                listSize++;
            }
            // Didn't find, so fall through and call addEntry() to add the
            // Entry and check for TreeBin conversion.
            checkIfNeedTree = listSize >= TreeBin.TREE_THRESHOLD;
        } else if (table[i] != null) {
            TreeBin e = (TreeBin)table[i];
            TreeNode p = e.putTreeNode(hash, key, value, null);
            if (p == null) { // putTreeNode() added a new node
                modCount++;
                size++;
                if (size >= threshold) {
                    resize(2 * table.length);
                }
                return null;
            } else { // putTreeNode() found an existing node
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                V oldVal = pEntry.value;
                pEntry.value = value;
                pEntry.recordAccess(this);
                return oldVal;
            }
        }
        modCount++;
        addEntry(hash, key, value, i, checkIfNeedTree);
        return null;
    }

    /**
     * Offloaded version of put for null keys
     */
    private V putForNullKey(V value) {
        if (nullKeyEntry != null) {
            V oldValue = nullKeyEntry.value;
            nullKeyEntry.value = value;
            nullKeyEntry.recordAccess(this);
            return oldValue;
        }
        modCount++;
        size++; // newEntry() skips size++
        nullKeyEntry = newEntry(0, null, value, null);
        return null;
    }

    private void putForCreateNullKey(V value) {
        // Look for preexisting entry for key.  This will never happen for
        // clone or deserialize.  It will only happen for construction if the
        // input Map is a sorted map whose ordering is inconsistent w/ equals.
        if (nullKeyEntry != null) {
            nullKeyEntry.value = value;
        } else {
            nullKeyEntry = newEntry(0, null, value, null);
            size++;
        }
    }


    /**
     * This method is used instead of put by constructors and
     * pseudoconstructors (clone, readObject).  It does not resize the table,
     * check for comodification, etc, though it will convert bins to TreeBins
     * as needed.  It calls createEntry rather than addEntry.
     */
    @SuppressWarnings("unchecked")
    private void putForCreate(K key, V value) {
        if (null == key) {
            putForCreateNullKey(value);
            return;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        boolean checkIfNeedTree = false; // Might we convert bin to a TreeBin?

        /**
         * Look for preexisting entry for key.  This will never happen for
         * clone or deserialize.  It will only happen for construction if the
         * input Map is a sorted map whose ordering is inconsistent w/ equals.
         */
        if (table[i] instanceof Entry) {
            int listSize = 0;
            Entry<K,V> e = (Entry<K,V>) table[i];
            for (; e != null; e = (Entry<K,V>)e.next) {
                Object k;
                if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                    e.value = value;
                    return;
                }
                listSize++;
            }
            // Didn't find, fall through to createEntry().
            // Check for conversion to TreeBin done via createEntry().
            checkIfNeedTree = listSize >= TreeBin.TREE_THRESHOLD;
        } else if (table[i] != null) {
            TreeBin e = (TreeBin)table[i];
            TreeNode p = e.putTreeNode(hash, key, value, null);
            if (p != null) {
                p.entry.setValue(value); // Found an existing node, set value
            } else {
                size++; // Added a new TreeNode, so update size
            }
            // don't need modCount++/check for resize - just return
            return;
        }

        createEntry(hash, key, value, i, checkIfNeedTree);
    }

    private void putAllForCreate(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            putForCreate(e.getKey(), e.getValue());
    }

    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     *
     * If current capacity is MAXIMUM_CAPACITY, this method does not
     * resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * @param newCapacity the new capacity, MUST be a power of two;
     *        must be greater than current capacity unless current
     *        capacity is MAXIMUM_CAPACITY (in which case value
     *        is irrelevant).
     */
    void resize(int newCapacity) {
        Object[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Object[] newTable = new Object[newCapacity];
        transfer(newTable);
        table = newTable;
        threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }

    /**
     * Transfers all entries from current table to newTable.
     *
     * Assumes newTable is larger than table
     */
    @SuppressWarnings("unchecked")
    void transfer(Object[] newTable) {
        Object[] src = table;
        // assert newTable.length > src.length : "newTable.length(" +
        //   newTable.length + ") expected to be > src.length("+src.length+")";
        int newCapacity = newTable.length;
        for (int j = 0; j < src.length; j++) {
             if (src[j] instanceof Entry) {
                // Assume: since wasn't TreeBin before, won't need TreeBin now
                Entry<K,V> e = (Entry<K,V>) src[j];
                while (null != e) {
                    Entry<K,V> next = (Entry<K,V>)e.next;
                    int i = indexFor(e.hash, newCapacity);
                    e.next = (Entry<K,V>) newTable[i];
                    newTable[i] = e;
                    e = next;
                }
            } else if (src[j] != null) {
                TreeBin e = (TreeBin) src[j];
                TreeBin loTree = new TreeBin();
                TreeBin hiTree = new TreeBin();
                e.splitTreeBin(newTable, j, loTree, hiTree);
            }
        }
        Arrays.fill(table, null);
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0)
            return;

        if (table == EMPTY_TABLE) {
            inflateTable((int) Math.max(numKeysToBeAdded * loadFactor, threshold));
        }

        /*
         * Expand the map if the map if the number of mappings to be added
         * is greater than or equal to threshold.  This is conservative; the
         * obvious condition is (m.size() + size) >= threshold, but this
         * condition could result in a map with twice the appropriate capacity,
         * if the keys to be added overlap with the keys already in this map.
         * By using the conservative calculation, we subject ourself
         * to at most one extra resize.
         */
        if (numKeysToBeAdded > threshold && table.length < MAXIMUM_CAPACITY) {
            resize(table.length * 2);
        }

        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
        }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    public V remove(Object key) {
        Entry<K,V> e = removeEntryForKey(key);
       return (e == null ? null : e.value);
   }

   // optimized implementations of default methods in Map

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        final int expectedModCount = modCount;
        if (nullKeyEntry != null) {
            forEachNullKey(expectedModCount, action);
        }
        Object[] tab = this.table;
        for (int index = 0; index < tab.length; index++) {
            Object item = tab[index];
            if (item == null) {
                continue;
            }
            if (item instanceof HashMap.TreeBin) {
                eachTreeNode(expectedModCount, ((TreeBin)item).first, action);
                continue;
            }
            @SuppressWarnings("unchecked")
            Entry<K, V> entry = (Entry<K, V>)item;
            while (entry != null) {
                action.accept(entry.key, entry.value);
                entry = (Entry<K, V>)entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    private void eachTreeNode(int expectedModCount, TreeNode<K, V> node, BiConsumer<? super K, ? super V> action) {
        while (node != null) {
            @SuppressWarnings("unchecked")
            Entry<K, V> entry = (Entry<K, V>)node.entry;
            action.accept(entry.key, entry.value);
            node = (TreeNode<K, V>)entry.next;

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    private void forEachNullKey(int expectedModCount, BiConsumer<? super K, ? super V> action) {
        action.accept(null, nullKeyEntry.value);

        if (expectedModCount != modCount) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        final int expectedModCount = modCount;
        if (nullKeyEntry != null) {
            replaceforNullKey(expectedModCount, function);
        }
        Object[] tab = this.table;
        for (int index = 0; index < tab.length; index++) {
            Object item = tab[index];
            if (item == null) {
                continue;
            }
            if (item instanceof HashMap.TreeBin) {
                replaceEachTreeNode(expectedModCount, ((TreeBin)item).first, function);
                continue;
            }
            @SuppressWarnings("unchecked")
            Entry<K, V> entry = (Entry<K, V>)item;
            while (entry != null) {
                entry.value = function.apply(entry.key, entry.value);
                entry = (Entry<K, V>)entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    private void replaceEachTreeNode(int expectedModCount, TreeNode<K, V> node, BiFunction<? super K, ? super V, ? extends V> function) {
        while (node != null) {
            @SuppressWarnings("unchecked")
            Entry<K, V> entry = (Entry<K, V>)node.entry;
            entry.value = function.apply(entry.key, entry.value);
            node = (TreeNode<K, V>)entry.next;

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    private void replaceforNullKey(int expectedModCount, BiFunction<? super K, ? super V, ? extends V> function) {
        nullKeyEntry.value = function.apply(null, nullKeyEntry.value);

        if (expectedModCount != modCount) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        if (key == null) {
            if (nullKeyEntry == null || nullKeyEntry.value == null) {
                putForNullKey(value);
                return null;
            } else {
                return nullKeyEntry.value;
            }
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        boolean checkIfNeedTree = false; // Might we convert bin to a TreeBin?

        if (table[i] instanceof Entry) {
            int listSize = 0;
            Entry<K,V> e = (Entry<K,V>) table[i];
            for (; e != null; e = (Entry<K,V>)e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    if (e.value != null) {
                        return e.value;
                    }
                    e.value = value;
                    e.recordAccess(this);
                    return null;
                }
                listSize++;
            }
            // Didn't find, so fall through and call addEntry() to add the
            // Entry and check for TreeBin conversion.
            checkIfNeedTree = listSize >= TreeBin.TREE_THRESHOLD;
        } else if (table[i] != null) {
            TreeBin e = (TreeBin)table[i];
            TreeNode p = e.putTreeNode(hash, key, value, null);
            if (p == null) { // not found, putTreeNode() added a new node
                modCount++;
                size++;
                if (size >= threshold) {
                    resize(2 * table.length);
                }
                return null;
            } else { // putTreeNode() found an existing node
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                V oldVal = pEntry.value;
                if (oldVal == null) { // only replace if maps to null
                    pEntry.value = value;
                    pEntry.recordAccess(this);
                }
                return oldVal;
            }
        }
        modCount++;
        addEntry(hash, key, value, i, checkIfNeedTree);
        return null;
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (isEmpty()) {
            return false;
        }
        if (key == null) {
            if (nullKeyEntry != null &&
                 Objects.equals(nullKeyEntry.value, value)) {
                removeNullKey();
                return true;
            }
            return false;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);

        if (table[i] instanceof Entry) {
            @SuppressWarnings("unchecked")
            Entry<K,V> prev = (Entry<K,V>) table[i];
            Entry<K,V> e = prev;
            while (e != null) {
                @SuppressWarnings("unchecked")
                Entry<K,V> next = (Entry<K,V>) e.next;
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    if (!Objects.equals(e.value, value)) {
                        return false;
                    }
                    modCount++;
                    size--;
                    if (prev == e)
                        table[i] = next;
                    else
                        prev.next = next;
                    e.recordRemoval(this);
                    return true;
                }
                prev = e;
                e = next;
            }
        } else if (table[i] != null) {
            TreeBin tb = ((TreeBin) table[i]);
            TreeNode p = tb.getTreeNode(hash, (K)key);
            if (p != null) {
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                // assert pEntry.key.equals(key);
                if (Objects.equals(pEntry.value, value)) {
                    modCount++;
                    size--;
                    tb.deleteTreeNode(p);
                    pEntry.recordRemoval(this);
                    if (tb.root == null || tb.first == null) {
                        // assert tb.root == null && tb.first == null :
                        //         "TreeBin.first and root should both be null";
                        // TreeBin is now empty, we should blank this bin
                        table[i] = null;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (isEmpty()) {
            return false;
        }
        if (key == null) {
            if (nullKeyEntry != null &&
                 Objects.equals(nullKeyEntry.value, oldValue)) {
                putForNullKey(newValue);
                return true;
            }
            return false;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);

        if (table[i] instanceof Entry) {
            @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>) table[i];
            for (; e != null; e = (Entry<K,V>)e.next) {
                if (e.hash == hash && Objects.equals(e.key, key) && Objects.equals(e.value, oldValue)) {
                    e.value = newValue;
                    e.recordAccess(this);
                    return true;
                }
            }
            return false;
        } else if (table[i] != null) {
            TreeBin tb = ((TreeBin) table[i]);
            TreeNode p = tb.getTreeNode(hash, key);
            if (p != null) {
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                // assert pEntry.key.equals(key);
                if (Objects.equals(pEntry.value, oldValue)) {
                    pEntry.value = newValue;
                    pEntry.recordAccess(this);
                    return true;
                }
            }
        }
        return false;
    }

   @Override
    public V replace(K key, V value) {
        if (isEmpty()) {
            return null;
        }
        if (key == null) {
            if (nullKeyEntry != null) {
                return putForNullKey(value);
            }
            return null;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        if (table[i] instanceof Entry) {
            @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>)table[i];
            for (; e != null; e = (Entry<K,V>)e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V oldValue = e.value;
                    e.value = value;
                    e.recordAccess(this);
                    return oldValue;
                }
            }

            return null;
        } else if (table[i] != null) {
            TreeBin tb = ((TreeBin) table[i]);
            TreeNode p = tb.getTreeNode(hash, key);
            if (p != null) {
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                // assert pEntry.key.equals(key);
                V oldValue = pEntry.value;
                pEntry.value = value;
                pEntry.recordAccess(this);
                return oldValue;
            }
        }
        return null;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        if (key == null) {
            if (nullKeyEntry == null || nullKeyEntry.value == null) {
                V newValue = mappingFunction.apply(key);
                if (newValue != null) {
                    putForNullKey(newValue);
                }
                return newValue;
            }
            return nullKeyEntry.value;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        boolean checkIfNeedTree = false; // Might we convert bin to a TreeBin?

        if (table[i] instanceof Entry) {
            int listSize = 0;
            @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>)table[i];
            for (; e != null; e = (Entry<K,V>)e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V oldValue = e.value;
                    if (oldValue == null) {
                        V newValue = mappingFunction.apply(key);
                        if (newValue != null) {
                            e.value = newValue;
                            e.recordAccess(this);
                        }
                        return newValue;
                    }
                    return oldValue;
                }
                listSize++;
            }
            // Didn't find, fall through to call the mapping function
            checkIfNeedTree = listSize >= TreeBin.TREE_THRESHOLD;
        } else if (table[i] != null) {
            TreeBin e = (TreeBin)table[i];
            V value = mappingFunction.apply(key);
            if (value == null) { // Return the existing value, if any
                TreeNode p = e.getTreeNode(hash, key);
                if (p != null) {
                    return (V) p.entry.value;
                }
                return null;
            } else { // Put the new value into the Tree, if absent
                TreeNode p = e.putTreeNode(hash, key, value, null);
                if (p == null) { // not found, new node was added
                    modCount++;
                    size++;
                    if (size >= threshold) {
                        resize(2 * table.length);
                    }
                    return value;
                } else { // putTreeNode() found an existing node
                    Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                    V oldVal = pEntry.value;
                    if (oldVal == null) { // only replace if maps to null
                        pEntry.value = value;
                        pEntry.recordAccess(this);
                        return value;
                    }
                    return oldVal;
                }
            }
        }
        V newValue = mappingFunction.apply(key);
        if (newValue != null) { // add Entry and check for TreeBin conversion
            modCount++;
            addEntry(hash, key, newValue, i, checkIfNeedTree);
        }

        return newValue;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (isEmpty()) {
            return null;
        }
        if (key == null) {
            V oldValue;
            if (nullKeyEntry != null && (oldValue = nullKeyEntry.value) != null) {
                V newValue = remappingFunction.apply(key, oldValue);
                if (newValue != null ) {
                    putForNullKey(newValue);
                    return newValue;
                } else {
                    removeNullKey();
                }
            }
            return null;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        if (table[i] instanceof Entry) {
            @SuppressWarnings("unchecked")
            Entry<K,V> prev = (Entry<K,V>)table[i];
            Entry<K,V> e = prev;
            while (e != null) {
                Entry<K,V> next = (Entry<K,V>)e.next;
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V oldValue = e.value;
                    if (oldValue == null)
                        break;
                    V newValue = remappingFunction.apply(key, oldValue);
                    if (newValue == null) {
                        modCount++;
                        size--;
                        if (prev == e)
                            table[i] = next;
                        else
                            prev.next = next;
                        e.recordRemoval(this);
                    } else {
                        e.value = newValue;
                        e.recordAccess(this);
                    }
                    return newValue;
                }
                prev = e;
                e = next;
            }
        } else if (table[i] != null) {
            TreeBin tb = (TreeBin)table[i];
            TreeNode p = tb.getTreeNode(hash, key);
            if (p != null) {
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                // assert pEntry.key.equals(key);
                V oldValue = pEntry.value;
                if (oldValue != null) {
                    V newValue = remappingFunction.apply(key, oldValue);
                if (newValue == null) { // remove mapping
                    modCount++;
                    size--;
                    tb.deleteTreeNode(p);
                    pEntry.recordRemoval(this);
                    if (tb.root == null || tb.first == null) {
                        // assert tb.root == null && tb.first == null :
                        //     "TreeBin.first and root should both be null";
                        // TreeBin is now empty, we should blank this bin
                        table[i] = null;
                    }
                } else {
                    pEntry.value = newValue;
                    pEntry.recordAccess(this);
                }
                return newValue;
            }
        }
        }
        return null;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        if (key == null) {
            V oldValue = nullKeyEntry == null ? null : nullKeyEntry.value;
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != oldValue || (oldValue == null && nullKeyEntry != null)) {
                if (newValue == null) {
                    removeNullKey();
                } else {
                    putForNullKey(newValue);
                }
            }
            return newValue;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        boolean checkIfNeedTree = false; // Might we convert bin to a TreeBin?

        if (table[i] instanceof Entry) {
            int listSize = 0;
            @SuppressWarnings("unchecked")
            Entry<K,V> prev = (Entry<K,V>)table[i];
            Entry<K,V> e = prev;

            while (e != null) {
                Entry<K,V> next = (Entry<K,V>)e.next;
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V oldValue = e.value;
                    V newValue = remappingFunction.apply(key, oldValue);
                    if (newValue != oldValue || oldValue == null) {
                        if (newValue == null) {
                            modCount++;
                            size--;
                            if (prev == e)
                                table[i] = next;
                            else
                                prev.next = next;
                            e.recordRemoval(this);
                        } else {
                            e.value = newValue;
                            e.recordAccess(this);
                        }
                    }
                    return newValue;
                }
                prev = e;
                e = next;
                listSize++;
            }
            checkIfNeedTree = listSize >= TreeBin.TREE_THRESHOLD;
        } else if (table[i] != null) {
            TreeBin tb = (TreeBin)table[i];
            TreeNode p = tb.getTreeNode(hash, key);
            V oldValue = p == null ? null : (V)p.entry.value;
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != oldValue || (oldValue == null && p != null)) {
                if (newValue == null) {
                    Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                    modCount++;
                    size--;
                    tb.deleteTreeNode(p);
                    pEntry.recordRemoval(this);
                    if (tb.root == null || tb.first == null) {
                        // assert tb.root == null && tb.first == null :
                        //         "TreeBin.first and root should both be null";
                        // TreeBin is now empty, we should blank this bin
                        table[i] = null;
                    }
                } else {
                    if (p != null) { // just update the value
                        Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                        pEntry.value = newValue;
                        pEntry.recordAccess(this);
                    } else { // need to put new node
                        p = tb.putTreeNode(hash, key, newValue, null);
                        // assert p == null; // should have added a new node
                        modCount++;
                        size++;
                        if (size >= threshold) {
                            resize(2 * table.length);
                        }
                    }
                }
            }
            return newValue;
        }

        V newValue = remappingFunction.apply(key, null);
        if (newValue != null) {
            modCount++;
            addEntry(hash, key, newValue, i, checkIfNeedTree);
        }

        return newValue;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        if (key == null) {
            V oldValue = nullKeyEntry == null ? null : nullKeyEntry.value;
            V newValue = oldValue == null ? value : remappingFunction.apply(oldValue, value);
            if (newValue != null) {
                putForNullKey(newValue);
            } else if (nullKeyEntry != null) {
                removeNullKey();
            }
            return newValue;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        boolean checkIfNeedTree = false; // Might we convert bin to a TreeBin?

        if (table[i] instanceof Entry) {
            int listSize = 0;
            @SuppressWarnings("unchecked")
            Entry<K,V> prev = (Entry<K,V>)table[i];
            Entry<K,V> e = prev;

            while (e != null) {
                Entry<K,V> next = (Entry<K,V>)e.next;
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V oldValue = e.value;
                    V newValue = (oldValue == null) ? value :
                                 remappingFunction.apply(oldValue, value);
                    if (newValue == null) {
                        modCount++;
                        size--;
                        if (prev == e)
                            table[i] = next;
                        else
                            prev.next = next;
                        e.recordRemoval(this);
                    } else {
                        e.value = newValue;
                        e.recordAccess(this);
                    }
                    return newValue;
                }
                prev = e;
                e = next;
                listSize++;
            }
            // Didn't find, so fall through and (maybe) call addEntry() to add
            // the Entry and check for TreeBin conversion.
            checkIfNeedTree = listSize >= TreeBin.TREE_THRESHOLD;
        } else if (table[i] != null) {
            TreeBin tb = (TreeBin)table[i];
            TreeNode p = tb.getTreeNode(hash, key);
            V oldValue = p == null ? null : (V)p.entry.value;
            V newValue = (oldValue == null) ? value :
                         remappingFunction.apply(oldValue, value);
            if (newValue == null) {
                if (p != null) {
                    Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                    modCount++;
                    size--;
                    tb.deleteTreeNode(p);
                    pEntry.recordRemoval(this);

                    if (tb.root == null || tb.first == null) {
                        // assert tb.root == null && tb.first == null :
                        //         "TreeBin.first and root should both be null";
                        // TreeBin is now empty, we should blank this bin
                        table[i] = null;
                    }
                }
                return null;
            } else if (newValue != oldValue) {
                if (p != null) { // just update the value
                    Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                    pEntry.value = newValue;
                    pEntry.recordAccess(this);
                } else { // need to put new node
                    p = tb.putTreeNode(hash, key, newValue, null);
                    // assert p == null; // should have added a new node
                    modCount++;
                    size++;
                    if (size >= threshold) {
                        resize(2 * table.length);
                    }
                }
            }
            return newValue;
        }
        if (value != null) {
            modCount++;
            addEntry(hash, key, value, i, checkIfNeedTree);
        }
        return value;
    }

    // end of optimized implementations of default methods in Map

    /**
     * Removes and returns the entry associated with the specified key
     * in the HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     *
     * We don't bother converting TreeBins back to Entry lists if the bin falls
     * back below TREE_THRESHOLD, but we do clear bins when removing the last
     * TreeNode in a TreeBin.
     */
    final Entry<K,V> removeEntryForKey(Object key) {
        if (isEmpty()) {
            return null;
        }
        if (key == null) {
            if (nullKeyEntry != null) {
                return removeNullKey();
            }
            return null;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);

        if (table[i] instanceof Entry) {
            @SuppressWarnings("unchecked")
            Entry<K,V> prev = (Entry<K,V>)table[i];
            Entry<K,V> e = prev;

            while (e != null) {
                @SuppressWarnings("unchecked")
                Entry<K,V> next = (Entry<K,V>) e.next;
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    modCount++;
                    size--;
                    if (prev == e)
                        table[i] = next;
                    else
                        prev.next = next;
                    e.recordRemoval(this);
                    return e;
                }
                prev = e;
                e = next;
            }
        } else if (table[i] != null) {
            TreeBin tb = ((TreeBin) table[i]);
            TreeNode p = tb.getTreeNode(hash, (K)key);
            if (p != null) {
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                // assert pEntry.key.equals(key);
                modCount++;
                size--;
                tb.deleteTreeNode(p);
                pEntry.recordRemoval(this);
                if (tb.root == null || tb.first == null) {
                    // assert tb.root == null && tb.first == null :
                    //             "TreeBin.first and root should both be null";
                    // TreeBin is now empty, we should blank this bin
                    table[i] = null;
                }
                return pEntry;
            }
        }
        return null;
    }

    /**
     * Special version of remove for EntrySet using {@code Map.Entry.equals()}
     * for matching.
     */
    final Entry<K,V> removeMapping(Object o) {
        if (isEmpty() || !(o instanceof Map.Entry))
            return null;

        Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
        Object key = entry.getKey();

        if (key == null) {
            if (entry.equals(nullKeyEntry)) {
                return removeNullKey();
            }
            return null;
        }

        int hash = hash(key);
        int i = indexFor(hash, table.length);

        if (table[i] instanceof Entry) {
            @SuppressWarnings("unchecked")
                Entry<K,V> prev = (Entry<K,V>)table[i];
            Entry<K,V> e = prev;

            while (e != null) {
                @SuppressWarnings("unchecked")
                Entry<K,V> next = (Entry<K,V>)e.next;
                if (e.hash == hash && e.equals(entry)) {
                    modCount++;
                    size--;
                    if (prev == e)
                        table[i] = next;
                    else
                        prev.next = next;
                    e.recordRemoval(this);
                    return e;
                }
                prev = e;
                e = next;
            }
        } else if (table[i] != null) {
            TreeBin tb = ((TreeBin) table[i]);
            TreeNode p = tb.getTreeNode(hash, (K)key);
            if (p != null && p.entry.equals(entry)) {
                @SuppressWarnings("unchecked")
                Entry<K,V> pEntry = (Entry<K,V>)p.entry;
                // assert pEntry.key.equals(key);
                modCount++;
                size--;
                tb.deleteTreeNode(p);
                pEntry.recordRemoval(this);
                if (tb.root == null || tb.first == null) {
                    // assert tb.root == null && tb.first == null :
                    //             "TreeBin.first and root should both be null";
                    // TreeBin is now empty, we should blank this bin
                    table[i] = null;
                }
                return pEntry;
            }
        }
        return null;
    }

    /*
     * Remove the mapping for the null key, and update internal accounting
     * (size, modcount, recordRemoval, etc).
     *
     * Assumes nullKeyEntry is non-null.
     */
    private Entry<K,V> removeNullKey() {
        // assert nullKeyEntry != null;
        Entry<K,V> retVal = nullKeyEntry;
        modCount++;
        size--;
        retVal.recordRemoval(this);
        nullKeyEntry = null;
        return retVal;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        modCount++;
        if (nullKeyEntry != null) {
            nullKeyEntry = null;
        }
        Arrays.fill(table, null);
        size = 0;
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     */
    public boolean containsValue(Object value) {
        if (value == null) {
            return containsNullValue();
        }
        Object[] tab = table;
        for (int i = 0; i < tab.length; i++) {
            if (tab[i] instanceof Entry) {
                Entry<?,?> e = (Entry<?,?>)tab[i];
                for (; e != null; e = (Entry<?,?>)e.next) {
                    if (value.equals(e.value)) {
                        return true;
                    }
                }
            } else if (tab[i] != null) {
                TreeBin e = (TreeBin)tab[i];
                TreeNode p = e.first;
                for (; p != null; p = (TreeNode) p.entry.next) {
                    if (value == p.entry.value || value.equals(p.entry.value)) {
                        return true;
                    }
                }
            }
        }
        // Didn't find value in table - could be in nullKeyEntry
        return (nullKeyEntry != null && (value == nullKeyEntry.value ||
                                         value.equals(nullKeyEntry.value)));
    }

    /**
     * Special-case code for containsValue with null argument
     */
    private boolean containsNullValue() {
        Object[] tab = table;
        for (int i = 0; i < tab.length; i++) {
            if (tab[i] instanceof Entry) {
                Entry<K,V> e = (Entry<K,V>)tab[i];
                for (; e != null; e = (Entry<K,V>)e.next) {
                    if (e.value == null) {
                        return true;
                    }
                }
            } else if (tab[i] != null) {
                TreeBin e = (TreeBin)tab[i];
                TreeNode p = e.first;
                for (; p != null; p = (TreeNode) p.entry.next) {
                    if (p.entry.value == null) {
                        return true;
                    }
                }
            }
        }
        // Didn't find value in table - could be in nullKeyEntry
        return (nullKeyEntry != null && nullKeyEntry.value == null);
    }

    /**
     * Returns a shallow copy of this <tt>HashMap</tt> instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map
     */
    @SuppressWarnings("unchecked")
    public Object clone() {
        HashMap<K,V> result = null;
        try {
            result = (HashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // assert false;
        }
        if (result.table != EMPTY_TABLE) {
            result.inflateTable(Math.min(
                (int) Math.min(
                    size * Math.min(1 / loadFactor, 4.0f),
                    // we have limits...
                    HashMap.MAXIMUM_CAPACITY),
                table.length));
        }
        result.entrySet = null;
        result.modCount = 0;
        result.size = 0;
        result.nullKeyEntry = null;
        result.init();
        result.putAllForCreate(this);

        return result;
    }

    static class Entry<K,V> implements Map.Entry<K,V> {
        final K key;
        V value;
        Object next; // an Entry, or a TreeNode
        final int hash;

        /**
         * Creates new entry.
         */
        Entry(int h, K k, V v, Object n) {
            value = v;
            next = n;
            key = k;
            hash = h;
        }

        public final K getKey() {
            return key;
        }

        public final V getValue() {
            return value;
        }

        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            Object k1 = getKey();
            Object k2 = e.getKey();
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                Object v1 = getValue();
                Object v2 = e.getValue();
                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                    return true;
                }
            return false;
        }

        public final int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }

        public final String toString() {
            return getKey() + "=" + getValue();
        }

        /**
         * This method is invoked whenever the value in an entry is
         * overwritten for a key that's already in the HashMap.
         */
        void recordAccess(HashMap<K,V> m) {
        }

        /**
         * This method is invoked whenever the entry is
         * removed from the table.
         */
        void recordRemoval(HashMap<K,V> m) {
        }
    }

    void addEntry(int hash, K key, V value, int bucketIndex) {
        addEntry(hash, key, value, bucketIndex, true);
    }

    /**
     * Adds a new entry with the specified key, value and hash code to
     * the specified bucket.  It is the responsibility of this
     * method to resize the table if appropriate.  The new entry is then
     * created by calling createEntry().
     *
     * Subclass overrides this to alter the behavior of put method.
     *
     * If checkIfNeedTree is false, it is known that this bucket will not need
     * to be converted to a TreeBin, so don't bothering checking.
     *
     * Assumes key is not null.
     */
    void addEntry(int hash, K key, V value, int bucketIndex, boolean checkIfNeedTree) {
        // assert key != null;
        if ((size >= threshold) && (null != table[bucketIndex])) {
            resize(2 * table.length);
            hash = hash(key);
            bucketIndex = indexFor(hash, table.length);
        }
        createEntry(hash, key, value, bucketIndex, checkIfNeedTree);
    }

    /**
     * Called by addEntry(), and also used when creating entries
     * as part of Map construction or "pseudo-construction" (cloning,
     * deserialization).  This version does not check for resizing of the table.
     *
     * This method is responsible for converting a bucket to a TreeBin once
     * TREE_THRESHOLD is reached. However if checkIfNeedTree is false, it is known
     * that this bucket will not need to be converted to a TreeBin, so don't
     * bother checking.  The new entry is constructed by calling newEntry().
     *
     * Assumes key is not null.
     *
     * Note: buckets already converted to a TreeBin don't call this method, but
     * instead call TreeBin.putTreeNode() to create new entries.
     */
    void createEntry(int hash, K key, V value, int bucketIndex, boolean checkIfNeedTree) {
        // assert key != null;
        @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>)table[bucketIndex];
        table[bucketIndex] = newEntry(hash, key, value, e);
        size++;

        if (checkIfNeedTree) {
            int listSize = 0;
            for (e = (Entry<K,V>) table[bucketIndex]; e != null; e = (Entry<K,V>)e.next) {
                listSize++;
                if (listSize >= TreeBin.TREE_THRESHOLD) { // Convert to TreeBin
                    if (comparableClassFor(key) != null) {
                        TreeBin t = new TreeBin();
                        t.populate((Entry)table[bucketIndex]);
                        table[bucketIndex] = t;
                    }
                    break;
                }
            }
        }
    }

    /*
     * Factory method to create a new Entry object.
     */
    Entry<K,V> newEntry(int hash, K key, V value, Object next) {
        return new HashMap.Entry<>(hash, key, value, next);
    }


    private abstract class HashIterator<E> implements Iterator<E> {
        Object next;            // next entry to return, an Entry or a TreeNode
        int expectedModCount;   // For fast-fail
        int index;              // current slot
        Object current;         // current entry, an Entry or a TreeNode

        HashIterator() {
            expectedModCount = modCount;
            if (size > 0) { // advance to first entry
                if (nullKeyEntry != null) {
                    // assert nullKeyEntry.next == null;
                    // This works with nextEntry(): nullKeyEntry isa Entry, and
                    // e.next will be null, so we'll hit the findNextBin() call.
                    next = nullKeyEntry;
                } else {
                    findNextBin();
                }
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        @SuppressWarnings("unchecked")
        final Entry<K,V> nextEntry() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            Object e = next;
            Entry<K,V> retVal;

            if (e == null)
                throw new NoSuchElementException();

            if (e instanceof TreeNode) { // TreeBin
                retVal = (Entry<K,V>)((TreeNode)e).entry;
                next = retVal.next;
            } else {
                retVal = (Entry<K,V>)e;
                next = ((Entry<K,V>)e).next;
            }

            if (next == null) { // Move to next bin
                findNextBin();
            }
            current = e;
            return retVal;
        }

        public void remove() {
            if (current == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            K k;

            if (current instanceof Entry) {
                k = ((Entry<K,V>)current).key;
            } else {
                k = ((Entry<K,V>)((TreeNode)current).entry).key;

            }
            current = null;
            HashMap.this.removeEntryForKey(k);
            expectedModCount = modCount;
        }

        /*
         * Set 'next' to the first entry of the next non-empty bin in the table
         */
        private void findNextBin() {
            // assert next == null;
            Object[] t = table;

            while (index < t.length && (next = t[index++]) == null)
                ;
            if (next instanceof HashMap.TreeBin) { // Point to the first TreeNode
                next = ((TreeBin) next).first;
                // assert next != null; // There should be no empty TreeBins
            }
        }
    }

    private final class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    private final class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().getKey();
        }
    }

    private final class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    // Subclass overrides these to alter behavior of views' iterator() method
    Iterator<K> newKeyIterator()   {
        return new KeyIterator();
    }
    Iterator<V> newValueIterator()   {
        return new ValueIterator();
    }
    Iterator<Map.Entry<K,V>> newEntryIterator()   {
        return new EntryIterator();
    }


    // Views

    private transient Set<Map.Entry<K,V>> entrySet = null;

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    private final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return newKeyIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return HashMap.this.removeEntryForKey(o) != null;
        }
        public void clear() {
            HashMap.this.clear();
        }

        public Spliterator<K> spliterator() {
            if (HashMap.this.getClass() == HashMap.class)
                return new KeySpliterator<K,V>(HashMap.this, 0, -1, 0, 0);
            else
                return Spliterators.spliterator
                        (this, Spliterator.SIZED | Spliterator.DISTINCT);
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own <tt>remove</tt> operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null ? vs : (values = new Values()));
    }

    private final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return newValueIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            HashMap.this.clear();
        }

        public Spliterator<V> spliterator() {
            if (HashMap.this.getClass() == HashMap.class)
                return new ValueSpliterator<K,V>(HashMap.this, 0, -1, 0, 0);
            else
                return Spliterators.spliterator
                        (this, Spliterator.SIZED);
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation, or through the
     * <tt>setValue</tt> operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt> and
     * <tt>clear</tt> operations.  It does not support the
     * <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        return entrySet0();
    }

    private Set<Map.Entry<K,V>> entrySet0() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return newEntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Entry<K,V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }
        public boolean remove(Object o) {
            return removeMapping(o) != null;
        }
        public int size() {
            return size;
        }
        public void clear() {
            HashMap.this.clear();
        }

        public Spliterator<Map.Entry<K,V>> spliterator() {
            if (HashMap.this.getClass() == HashMap.class)
                return new EntrySpliterator<K,V>(HashMap.this, 0, -1, 0, 0);
            else
                return Spliterators.spliterator
                        (this, Spliterator.SIZED | Spliterator.DISTINCT);
        }
    }

    /**
     * Save the state of the <tt>HashMap</tt> instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     *             bucket array) is emitted (int), followed by the
     *             <i>size</i> (an int, the number of key-value
     *             mappings), followed by the key (Object) and value (Object)
     *             for each key-value mapping.  The key-value mappings are
     *             emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();

        // Write out number of buckets
        if (table==EMPTY_TABLE) {
            s.writeInt(roundUpToPowerOf2(threshold));
        } else {
            s.writeInt(table.length);
        }

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        if (size > 0) {
            for(Map.Entry<K,V> e : entrySet0()) {
                s.writeObject(e.getKey());
                s.writeObject(e.getValue());
            }
        }
    }

    private static final long serialVersionUID = 362498820763181265L;

    /**
     * Reconstitute the {@code HashMap} instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        // Read in the threshold (ignored), loadfactor, and any hidden stuff
        s.defaultReadObject();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new InvalidObjectException("Illegal load factor: " +
                                               loadFactor);
        }

        // set other fields that need values
        if (Holder.USE_HASHSEED) {
            int seed = ThreadLocalRandom.current().nextInt();
            Holder.UNSAFE.putIntVolatile(this, Holder.HASHSEED_OFFSET,
                                         (seed != 0) ? seed : 1);
        }
        table = EMPTY_TABLE;

        // Read in number of buckets
        s.readInt(); // ignored.

        // Read number of mappings
        int mappings = s.readInt();
        if (mappings < 0)
            throw new InvalidObjectException("Illegal mappings count: " +
                                               mappings);

        // capacity chosen by number of mappings and desired load (if >= 0.25)
        int capacity = (int) Math.min(
                mappings * Math.min(1 / loadFactor, 4.0f),
                // we have limits...
                HashMap.MAXIMUM_CAPACITY);

        // allocate the bucket array;
        if (mappings > 0) {
            inflateTable(capacity);
        } else {
            threshold = capacity;
        }

        init();  // Give subclass a chance to do its thing.

        // Read the keys and values, and put the mappings in the HashMap
        for (int i=0; i<mappings; i++) {
            @SuppressWarnings("unchecked")
            K key = (K) s.readObject();
            @SuppressWarnings("unchecked")
            V value = (V) s.readObject();
            putForCreate(key, value);
        }
    }

    // These methods are used when serializing HashSets
    int   capacity()     { return table.length; }
    float loadFactor()   { return loadFactor;   }

    /**
     * Standin until HM overhaul; based loosely on Weak and Identity HM.
     */
    static class HashMapSpliterator<K,V> {
        final HashMap<K,V> map;
        Object current;             // current node, can be Entry or TreeNode
        int index;                  // current index, modified on advance/split
        int fence;                  // one past last index
        int est;                    // size estimate
        int expectedModCount;       // for comodification checks
        boolean acceptedNull;       // Have we accepted the null key?
                                    // Without this, we can't distinguish
                                    // between being at the very beginning (and
                                    // needing to accept null), or being at the
                                    // end of the list in bin 0.  In both cases,
                                    // current == null && index == 0.

        HashMapSpliterator(HashMap<K,V> m, int origin,
                               int fence, int est,
                               int expectedModCount) {
            this.map = m;
            this.index = origin;
            this.fence = fence;
            this.est = est;
            this.expectedModCount = expectedModCount;
            this.acceptedNull = false;
        }

        final int getFence() { // initialize fence and size on first use
            int hi;
            if ((hi = fence) < 0) {
                HashMap<K,V> m = map;
                est = m.size;
                expectedModCount = m.modCount;
                hi = fence = m.table.length;
            }
            return hi;
        }

        public final long estimateSize() {
            getFence(); // force init
            return (long) est;
        }
    }

    static final class KeySpliterator<K,V>
        extends HashMapSpliterator<K,V>
        implements Spliterator<K> {
        KeySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                       int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public KeySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            if (lo >= mid || current != null) {
                return null;
            } else {
                KeySpliterator<K,V> retVal = new KeySpliterator<K,V>(map, lo,
                                     index = mid, est >>>= 1, expectedModCount);
                // Only 'this' Spliterator chould check for null.
                retVal.acceptedNull = true;
                return retVal;
            }
        }

        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super K> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Object[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = tab.length;
            }
            else
                mc = expectedModCount;

            if (!acceptedNull) {
                acceptedNull = true;
                if (m.nullKeyEntry != null) {
                    action.accept(m.nullKeyEntry.key);
                }
            }
            if (tab.length >= hi && (i = index) >= 0 &&
                (i < (index = hi) || current != null)) {
                Object p = current;
                current = null;
                do {
                    if (p == null) {
                        p = tab[i++];
                        if (p instanceof HashMap.TreeBin) {
                            p = ((HashMap.TreeBin)p).first;
                        }
                    } else {
                        HashMap.Entry<K,V> entry;
                        if (p instanceof HashMap.Entry) {
                            entry = (HashMap.Entry<K,V>)p;
                        } else {
                            entry = (HashMap.Entry<K,V>)((TreeNode)p).entry;
                        }
                        action.accept(entry.key);
                        p = entry.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super K> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Object[] tab = map.table;
            hi = getFence();

            if (!acceptedNull) {
                acceptedNull = true;
                if (map.nullKeyEntry != null) {
                    action.accept(map.nullKeyEntry.key);
                    if (map.modCount != expectedModCount)
                        throw new ConcurrentModificationException();
                    return true;
                }
            }
            if (tab.length >= hi && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null) {
                        current = tab[index++];
                        if (current instanceof HashMap.TreeBin) {
                            current = ((HashMap.TreeBin)current).first;
                        }
                    } else {
                        HashMap.Entry<K,V> entry;
                        if (current instanceof HashMap.Entry) {
                            entry = (HashMap.Entry<K,V>)current;
                        } else {
                            entry = (HashMap.Entry<K,V>)((TreeNode)current).entry;
                        }
                        K k = entry.key;
                        current = entry.next;
                        action.accept(k);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                Spliterator.DISTINCT;
        }
    }

    static final class ValueSpliterator<K,V>
        extends HashMapSpliterator<K,V>
        implements Spliterator<V> {
        ValueSpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public ValueSpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            if (lo >= mid || current != null) {
                return null;
            } else {
                ValueSpliterator<K,V> retVal = new ValueSpliterator<K,V>(map,
                                 lo, index = mid, est >>>= 1, expectedModCount);
                // Only 'this' Spliterator chould check for null.
                retVal.acceptedNull = true;
                return retVal;
            }
        }

        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super V> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Object[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = tab.length;
            }
            else
                mc = expectedModCount;

            if (!acceptedNull) {
                acceptedNull = true;
                if (m.nullKeyEntry != null) {
                    action.accept(m.nullKeyEntry.value);
                }
            }
            if (tab.length >= hi && (i = index) >= 0 &&
                (i < (index = hi) || current != null)) {
                Object p = current;
                current = null;
                do {
                    if (p == null) {
                        p = tab[i++];
                        if (p instanceof HashMap.TreeBin) {
                            p = ((HashMap.TreeBin)p).first;
                        }
                    } else {
                        HashMap.Entry<K,V> entry;
                        if (p instanceof HashMap.Entry) {
                            entry = (HashMap.Entry<K,V>)p;
                        } else {
                            entry = (HashMap.Entry<K,V>)((TreeNode)p).entry;
                        }
                        action.accept(entry.value);
                        p = entry.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super V> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Object[] tab = map.table;
            hi = getFence();

            if (!acceptedNull) {
                acceptedNull = true;
                if (map.nullKeyEntry != null) {
                    action.accept(map.nullKeyEntry.value);
                    if (map.modCount != expectedModCount)
                        throw new ConcurrentModificationException();
                    return true;
                }
            }
            if (tab.length >= hi && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null) {
                        current = tab[index++];
                        if (current instanceof HashMap.TreeBin) {
                            current = ((HashMap.TreeBin)current).first;
                        }
                    } else {
                        HashMap.Entry<K,V> entry;
                        if (current instanceof HashMap.Entry) {
                            entry = (Entry<K,V>)current;
                        } else {
                            entry = (Entry<K,V>)((TreeNode)current).entry;
                        }
                        V v = entry.value;
                        current = entry.next;
                        action.accept(v);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0);
        }
    }

    static final class EntrySpliterator<K,V>
        extends HashMapSpliterator<K,V>
        implements Spliterator<Map.Entry<K,V>> {
        EntrySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public EntrySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            if (lo >= mid || current != null) {
                return null;
            } else {
                EntrySpliterator<K,V> retVal = new EntrySpliterator<K,V>(map,
                                 lo, index = mid, est >>>= 1, expectedModCount);
                // Only 'this' Spliterator chould check for null.
                retVal.acceptedNull = true;
                return retVal;
            }
        }

        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super Map.Entry<K,V>> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Object[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = tab.length;
            }
            else
                mc = expectedModCount;

            if (!acceptedNull) {
                acceptedNull = true;
                if (m.nullKeyEntry != null) {
                    action.accept(m.nullKeyEntry);
                }
            }
            if (tab.length >= hi && (i = index) >= 0 &&
                (i < (index = hi) || current != null)) {
                Object p = current;
                current = null;
                do {
                    if (p == null) {
                        p = tab[i++];
                        if (p instanceof HashMap.TreeBin) {
                            p = ((HashMap.TreeBin)p).first;
                        }
                    } else {
                        HashMap.Entry<K,V> entry;
                        if (p instanceof HashMap.Entry) {
                            entry = (HashMap.Entry<K,V>)p;
                        } else {
                            entry = (HashMap.Entry<K,V>)((TreeNode)p).entry;
                        }
                        action.accept(entry);
                        p = entry.next;

                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Object[] tab = map.table;
            hi = getFence();

            if (!acceptedNull) {
                acceptedNull = true;
                if (map.nullKeyEntry != null) {
                    action.accept(map.nullKeyEntry);
                    if (map.modCount != expectedModCount)
                        throw new ConcurrentModificationException();
                    return true;
                }
            }
            if (tab.length >= hi && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null) {
                        current = tab[index++];
                        if (current instanceof HashMap.TreeBin) {
                            current = ((HashMap.TreeBin)current).first;
                        }
                    } else {
                        HashMap.Entry<K,V> e;
                        if (current instanceof HashMap.Entry) {
                            e = (Entry<K,V>)current;
                        } else {
                            e = (Entry<K,V>)((TreeNode)current).entry;
                        }
                        current = e.next;
                        action.accept(e);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                Spliterator.DISTINCT;
        }
    }
}
