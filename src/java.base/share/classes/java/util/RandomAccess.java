/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Marker interface used by {@code List} implementations to indicate that
 * they support fast (generally constant time 常量时间) random access.
 * The primary purpose of this interface is to allow generic algorithms
 * to alter their behavior to provide good performance
 * when applied to either random or sequential access lists.
 *
 * fixme
 *      标记 支持快速随机访问 的List实现类，即O(1)时间访问list中的元素；
 *      这个接口的主要目的是，当随机或者顺序访问列表的时候，
 *      允许常用的方法修改列表的行为、提供好的性能。
 *
 *      随机访问列表：ArrayList；
 *      顺序访问列表：LinkedList；
 *
 *
 * <p>The best algorithms for manipulating random access lists (such as {@code ArrayList})
 * can produce quadratic(二次方的) behavior when applied to sequential access lists (such as {@code LinkedList}).
 *
 * Generic list algorithms are encouraged to check whether the given list is an
 * {@code instanceof} RandomAccess before applying an algorithm
 * that would provide poor performance if it were applied to a sequential(顺序访问列表) access list,
 * and to alter their behavior if necessary to guarantee acceptable performance.
 *
 * <p>It is recognized that the distinction between random and sequential
 * access is often fuzzy.  For example, some {@code List} implementations
 * provide asymptotically linear access times if they get huge, but constant
 * access times in practice.  Such a {@code List} implementation
 * should generally implement this interface.  As a rule of thumb, a
 * {@code List} implementation should implement this interface if,
 * for typical instances of the class, this loop:
 * <pre>
 *     for (int i=0, n=list.size(); i &lt; n; i++)
 *         list.get(i);
 * </pre>
 * runs faster than this loop:
 * <pre>
 *     for (Iterator i=list.iterator(); i.hasNext(); )
 *         i.next();
 * </pre>
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @since 1.4
 */
public interface RandomAccess {
}
