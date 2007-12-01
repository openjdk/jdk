/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.lang.management;

import javax.management.openmbean.CompositeData;
import sun.management.MemoryUsageCompositeData;

/**
 * A <tt>MemoryUsage</tt> object represents a snapshot of memory usage.
 * Instances of the <tt>MemoryUsage</tt> class are usually constructed
 * by methods that are used to obtain memory usage
 * information about individual memory pool of the Java virtual machine or
 * the heap or non-heap memory of the Java virtual machine as a whole.
 *
 * <p> A <tt>MemoryUsage</tt> object contains four values:
 * <ul>
 * <table>
 * <tr>
 * <td valign=top> <tt>init</tt> </td>
 * <td valign=top> represents the initial amount of memory (in bytes) that
 *      the Java virtual machine requests from the operating system
 *      for memory management during startup.  The Java virtual machine
 *      may request additional memory from the operating system and
 *      may also release memory to the system over time.
 *      The value of <tt>init</tt> may be undefined.
 * </td>
 * </tr>
 * <tr>
 * <td valign=top> <tt>used</tt> </td>
 * <td valign=top> represents the amount of memory currently used (in bytes).
 * </td>
 * </tr>
 * <tr>
 * <td valign=top> <tt>committed</tt> </td>
 * <td valign=top> represents the amount of memory (in bytes) that is
 *      guaranteed to be available for use by the Java virtual machine.
 *      The amount of committed memory may change over time (increase
 *      or decrease).  The Java virtual machine may release memory to
 *      the system and <tt>committed</tt> could be less than <tt>init</tt>.
 *      <tt>committed</tt> will always be greater than
 *      or equal to <tt>used</tt>.
 * </td>
 * </tr>
 * <tr>
 * <td valign=top> <tt>max</tt> </td>
 * <td valign=top> represents the maximum amount of memory (in bytes)
 *      that can be used for memory management. Its value may be undefined.
 *      The maximum amount of memory may change over time if defined.
 *      The amount of used and committed memory will always be less than
 *      or equal to <tt>max</tt> if <tt>max</tt> is defined.
 *      A memory allocation may fail if it attempts to increase the
 *      used memory such that <tt>used &gt committed</tt> even
 *      if <tt>used &lt= max</tt> would still be true (for example,
 *      when the system is low on virtual memory).
 * </td>
 * </tr>
 * </table>
 * </ul>
 *
 * Below is a picture showing an example of a memory pool:
 * <p>
 * <pre>
 *        +----------------------------------------------+
 *        +////////////////           |                  +
 *        +////////////////           |                  +
 *        +----------------------------------------------+
 *
 *        |--------|
 *           init
 *        |---------------|
 *               used
 *        |---------------------------|
 *                  committed
 *        |----------------------------------------------|
 *                            max
 * </pre>
 *
 * <h4>MXBean Mapping</h4>
 * <tt>MemoryUsage</tt> is mapped to a {@link CompositeData CompositeData}
 * with attributes as specified in the {@link #from from} method.
 *
 * @author   Mandy Chung
 * @since   1.5
 */
public class MemoryUsage {
    private final long init;
    private final long used;
    private final long committed;
    private final long max;

    /**
     * Constructs a <tt>MemoryUsage</tt> object.
     *
     * @param init      the initial amount of memory in bytes that
     *                  the Java virtual machine allocates;
     *                  or <tt>-1</tt> if undefined.
     * @param used      the amount of used memory in bytes.
     * @param committed the amount of committed memory in bytes.
     * @param max       the maximum amount of memory in bytes that
     *                  can be used; or <tt>-1</tt> if undefined.
     *
     * @throws IllegalArgumentException if
     * <ul>
     * <li> the value of <tt>init</tt> or <tt>max</tt> is negative
     *      but not <tt>-1</tt>; or</li>
     * <li> the value of <tt>used</tt> or <tt>committed</tt> is negative;
     *      or</li>
     * <li> <tt>used</tt> is greater than the value of <tt>committed</tt>;
     *      or</li>
     * <li> <tt>committed</tt> is greater than the value of <tt>max</tt>
     *      <tt>max</tt> if defined.</li>
     * </ul>
     */
    public MemoryUsage(long init,
                       long used,
                       long committed,
                       long max) {
        if (init < -1) {
            throw new IllegalArgumentException( "init parameter = " +
                init + " is negative but not -1.");
        }
        if (max < -1) {
            throw new IllegalArgumentException( "max parameter = " +
                max + " is negative but not -1.");
        }
        if (used < 0) {
            throw new IllegalArgumentException( "used parameter = " +
                used + " is negative.");
        }
        if (committed < 0) {
            throw new IllegalArgumentException( "committed parameter = " +
                committed + " is negative.");
        }
        if (used > committed) {
            throw new IllegalArgumentException( "used = " + used +
                " should be <= committed = " + committed);
        }
        if (max >= 0 && committed > max) {
            throw new IllegalArgumentException( "committed = " + committed +
                " should be < max = " + max);
        }

        this.init = init;
        this.used = used;
        this.committed = committed;
        this.max = max;
    }

    /**
     * Constructs a <tt>MemoryUsage</tt> object from a
     * {@link CompositeData CompositeData}.
     */
    private MemoryUsage(CompositeData cd) {
        // validate the input composite data
        MemoryUsageCompositeData.validateCompositeData(cd);

        this.init = MemoryUsageCompositeData.getInit(cd);
        this.used = MemoryUsageCompositeData.getUsed(cd);
        this.committed = MemoryUsageCompositeData.getCommitted(cd);
        this.max = MemoryUsageCompositeData.getMax(cd);
    }

    /**
     * Returns the amount of memory in bytes that the Java virtual machine
     * initially requests from the operating system for memory management.
     * This method returns <tt>-1</tt> if the initial memory size is undefined.
     *
     * @return the initial size of memory in bytes;
     * <tt>-1</tt> if undefined.
     */
    public long getInit() {
        return init;
    }

    /**
     * Returns the amount of used memory in bytes.
     *
     * @return the amount of used memory in bytes.
     *
     */
    public long getUsed() {
        return used;
    };

    /**
     * Returns the amount of memory in bytes that is committed for
     * the Java virtual machine to use.  This amount of memory is
     * guaranteed for the Java virtual machine to use.
     *
     * @return the amount of committed memory in bytes.
     *
     */
    public long getCommitted() {
        return committed;
    };

    /**
     * Returns the maximum amount of memory in bytes that can be
     * used for memory management.  This method returns <tt>-1</tt>
     * if the maximum memory size is undefined.
     *
     * <p> This amount of memory is not guaranteed to be available
     * for memory management if it is greater than the amount of
     * committed memory.  The Java virtual machine may fail to allocate
     * memory even if the amount of used memory does not exceed this
     * maximum size.
     *
     * @return the maximum amount of memory in bytes;
     * <tt>-1</tt> if undefined.
     */
    public long getMax() {
        return max;
    };

    /**
     * Returns a descriptive representation of this memory usage.
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("init = " + init + "(" + (init >> 10) + "K) ");
        buf.append("used = " + used + "(" + (used >> 10) + "K) ");
        buf.append("committed = " + committed + "(" +
                   (committed >> 10) + "K) " );
        buf.append("max = " + max + "(" + (max >> 10) + "K)");
        return buf.toString();
    }

    /**
     * Returns a <tt>MemoryUsage</tt> object represented by the
     * given <tt>CompositeData</tt>. The given <tt>CompositeData</tt>
     * must contain the following attributes:
     * <p>
     * <blockquote>
     * <table border>
     * <tr>
     *   <th align=left>Attribute Name</th>
     *   <th align=left>Type</th>
     * </tr>
     * <tr>
     *   <td>init</td>
     *   <td><tt>java.lang.Long</tt></td>
     * </tr>
     * <tr>
     *   <td>used</td>
     *   <td><tt>java.lang.Long</tt></td>
     * </tr>
     * <tr>
     *   <td>committed</td>
     *   <td><tt>java.lang.Long</tt></td>
     * </tr>
     * <tr>
     *   <td>max</td>
     *   <td><tt>java.lang.Long</tt></td>
     * </tr>
     * </table>
     * </blockquote>
     *
     * @param cd <tt>CompositeData</tt> representing a <tt>MemoryUsage</tt>
     *
     * @throws IllegalArgumentException if <tt>cd</tt> does not
     *   represent a <tt>MemoryUsage</tt> with the attributes described
     *   above.
     *
     * @return a <tt>MemoryUsage</tt> object represented by <tt>cd</tt>
     *         if <tt>cd</tt> is not <tt>null</tt>;
     *         <tt>null</tt> otherwise.
     */
    public static MemoryUsage from(CompositeData cd) {
        if (cd == null) {
            return null;
        }

        if (cd instanceof MemoryUsageCompositeData) {
            return ((MemoryUsageCompositeData) cd).getMemoryUsage();
        } else {
            return new MemoryUsage(cd);
        }

    }
}
