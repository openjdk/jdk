/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.management;

import javax.management.openmbean.CompositeData;
import sun.management.MonitorInfoCompositeData;

/**
 * Information about an object monitor lock.  An object monitor is locked
 * when entering a synchronization block or method on that object.
 *
 * <h4>MXBean Mapping</h4>
 * <tt>MonitorInfo</tt> is mapped to a {@link CompositeData CompositeData}
 * with attributes as specified in
 * the {@link #from from} method.
 *
 * @author  Mandy Chung
 * @since   1.6
 */
public class MonitorInfo extends LockInfo {

    private int    stackDepth;
    private StackTraceElement stackFrame;

    /**
     * Construct a <tt>MonitorInfo</tt> object.
     *
     * @param className the fully qualified name of the class of the lock object.
     * @param identityHashCode the {@link System#identityHashCode
     *                         identity hash code} of the lock object.
     * @param stackDepth the depth in the stack trace where the object monitor
     *                   was locked.
     * @param stackFrame the stack frame that locked the object monitor.
     * @throws IllegalArgumentException if
     *    <tt>stackDepth</tt> &ge; 0 but <tt>stackFrame</tt> is <tt>null</tt>,
     *    or <tt>stackDepth</tt> &lt; 0 but <tt>stackFrame</tt> is not
     *       <tt>null</tt>.
     */
    public MonitorInfo(String className,
                       int identityHashCode,
                       int stackDepth,
                       StackTraceElement stackFrame) {
        super(className, identityHashCode);
        if (stackDepth >= 0 && stackFrame == null) {
            throw new IllegalArgumentException("Parameter stackDepth is " +
                stackDepth + " but stackFrame is null");
        }
        if (stackDepth < 0 && stackFrame != null) {
            throw new IllegalArgumentException("Parameter stackDepth is " +
                stackDepth + " but stackFrame is not null");
        }
        this.stackDepth = stackDepth;
        this.stackFrame = stackFrame;
    }

    /**
     * Returns the depth in the stack trace where the object monitor
     * was locked.  The depth is the index to the <tt>StackTraceElement</tt>
     * array returned in the {@link ThreadInfo#getStackTrace} method.
     *
     * @return the depth in the stack trace where the object monitor
     *         was locked, or a negative number if not available.
     */
    public int getLockedStackDepth() {
        return stackDepth;
    }

    /**
     * Returns the stack frame that locked the object monitor.
     *
     * @return <tt>StackTraceElement</tt> that locked the object monitor,
     *         or <tt>null</tt> if not available.
     */
    public StackTraceElement getLockedStackFrame() {
        return stackFrame;
    }

    /**
     * Returns a <tt>MonitorInfo</tt> object represented by the
     * given <tt>CompositeData</tt>.
     * The given <tt>CompositeData</tt> must contain the following attributes
     * as well as the attributes specified in the
     * <a href="LockInfo.html#MappedType">
     * mapped type</a> for the {@link LockInfo} class:
     * <blockquote>
     * <table border>
     * <tr>
     *   <th align=left>Attribute Name</th>
     *   <th align=left>Type</th>
     * </tr>
     * <tr>
     *   <td>lockedStackFrame</td>
     *   <td><tt>CompositeData as specified in the
     *       <a href="ThreadInfo.html#StackTrace">stackTrace</a>
     *       attribute defined in the {@link ThreadInfo#from
     *       ThreadInfo.from} method.
     *       </tt></td>
     * </tr>
     * <tr>
     *   <td>lockedStackDepth</td>
     *   <td><tt>java.lang.Integer</tt></td>
     * </tr>
     * </table>
     * </blockquote>
     *
     * @param cd <tt>CompositeData</tt> representing a <tt>MonitorInfo</tt>
     *
     * @throws IllegalArgumentException if <tt>cd</tt> does not
     *   represent a <tt>MonitorInfo</tt> with the attributes described
     *   above.

     * @return a <tt>MonitorInfo</tt> object represented
     *         by <tt>cd</tt> if <tt>cd</tt> is not <tt>null</tt>;
     *         <tt>null</tt> otherwise.
     */
    public static MonitorInfo from(CompositeData cd) {
        if (cd == null) {
            return null;
        }

        if (cd instanceof MonitorInfoCompositeData) {
            return ((MonitorInfoCompositeData) cd).getMonitorInfo();
        } else {
            MonitorInfoCompositeData.validateCompositeData(cd);
            String className = MonitorInfoCompositeData.getClassName(cd);
            int identityHashCode = MonitorInfoCompositeData.getIdentityHashCode(cd);
            int stackDepth = MonitorInfoCompositeData.getLockedStackDepth(cd);
            StackTraceElement stackFrame = MonitorInfoCompositeData.getLockedStackFrame(cd);
            return new MonitorInfo(className,
                                   identityHashCode,
                                   stackDepth,
                                   stackFrame);
        }
    }

}
