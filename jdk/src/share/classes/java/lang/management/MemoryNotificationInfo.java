/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
import sun.management.MemoryNotifInfoCompositeData;

/**
 * The information about a memory notification.
 *
 * <p>
 * A memory notification is emitted by {@link MemoryMXBean}
 * when the Java virtual machine detects that the memory usage
 * of a memory pool is exceeding a threshold value.
 * The notification emitted will contain the memory notification
 * information about the detected condition:
 * <ul>
 *   <li>The name of the memory pool.</li>
 *   <li>The memory usage of the memory pool when the notification
 *       was constructed.</li>
 *   <li>The number of times that the memory usage has crossed
 *       a threshold when the notification was constructed.
 *       For usage threshold notifications, this count will be the
 *       {@link MemoryPoolMXBean#getUsageThresholdCount usage threshold
 *       count}.  For collection threshold notifications,
 *       this count will be the
 *       {@link MemoryPoolMXBean#getCollectionUsageThresholdCount
 *       collection usage threshold count}.
 *       </li>
 * </ul>
 *
 * <p>
 * A {@link CompositeData CompositeData} representing
 * the <tt>MemoryNotificationInfo</tt> object
 * is stored in the
 * {@link javax.management.Notification#setUserData user data}
 * of a {@link javax.management.Notification notification}.
 * The {@link #from from} method is provided to convert from
 * a <tt>CompositeData</tt> to a <tt>MemoryNotificationInfo</tt>
 * object. For example:
 *
 * <blockquote><pre>
 *      Notification notif;
 *
 *      // receive the notification emitted by MemoryMXBean and set to notif
 *      ...
 *
 *      String notifType = notif.getType();
 *      if (notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) ||
 *          notifType.equals(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {
 *          // retrieve the memory notification information
 *          CompositeData cd = (CompositeData) notif.getUserData();
 *          MemoryNotificationInfo info = MemoryNotificationInfo.from(cd);
 *          ....
 *      }
 * </pre></blockquote>
 *
 * <p>
 * The types of notifications emitted by <tt>MemoryMXBean</tt> are:
 * <ul>
 *   <li>A {@link #MEMORY_THRESHOLD_EXCEEDED
 *       usage threshold exceeded notification}.
 *       <br>This notification will be emitted when
 *       the memory usage of a memory pool is increased and has reached
 *       or exceeded its
 *       <a href="MemoryPoolMXBean.html#UsageThreshold"> usage threshold</a> value.
 *       Subsequent crossing of the usage threshold value does not cause
 *       further notification until the memory usage has returned
 *       to become less than the usage threshold value.
 *       <p></li>
 *   <li>A {@link #MEMORY_COLLECTION_THRESHOLD_EXCEEDED
 *       collection usage threshold exceeded notification}.
 *       <br>This notification will be emitted when
 *       the memory usage of a memory pool is greater than or equal to its
 *       <a href="MemoryPoolMXBean.html#CollectionThreshold">
 *       collection usage threshold</a> after the Java virtual machine
 *       has expended effort in recycling unused objects in that
 *       memory pool.</li>
 * </ul>
 *
 * @author  Mandy Chung
 * @since   1.5
 *
 */
public class MemoryNotificationInfo {
    private final String poolName;
    private final MemoryUsage usage;
    private final long count;

    /**
     * Notification type denoting that
     * the memory usage of a memory pool has
     * reached or exceeded its
     * <a href="MemoryPoolMXBean.html#UsageThreshold"> usage threshold</a> value.
     * This notification is emitted by {@link MemoryMXBean}.
     * Subsequent crossing of the usage threshold value does not cause
     * further notification until the memory usage has returned
     * to become less than the usage threshold value.
     * The value of this notification type is
     * <tt>java.management.memory.threshold.exceeded</tt>.
     */
    public static final String MEMORY_THRESHOLD_EXCEEDED =
        "java.management.memory.threshold.exceeded";

    /**
     * Notification type denoting that
     * the memory usage of a memory pool is greater than or equal to its
     * <a href="MemoryPoolMXBean.html#CollectionThreshold">
     * collection usage threshold</a> after the Java virtual machine
     * has expended effort in recycling unused objects in that
     * memory pool.
     * This notification is emitted by {@link MemoryMXBean}.
     * The value of this notification type is
     * <tt>java.management.memory.collection.threshold.exceeded</tt>.
     */
    public static final String MEMORY_COLLECTION_THRESHOLD_EXCEEDED =
        "java.management.memory.collection.threshold.exceeded";

    /**
     * Constructs a <tt>MemoryNotificationInfo</tt> object.
     *
     * @param poolName The name of the memory pool which triggers this notification.
     * @param usage Memory usage of the memory pool.
     * @param count The threshold crossing count.
     */
    public MemoryNotificationInfo(String poolName,
                                  MemoryUsage usage,
                                  long count) {
        if (poolName == null) {
            throw new NullPointerException("Null poolName");
        }
        if (usage == null) {
            throw new NullPointerException("Null usage");
        }

        this.poolName = poolName;
        this.usage = usage;
        this.count = count;
    }

    MemoryNotificationInfo(CompositeData cd) {
        MemoryNotifInfoCompositeData.validateCompositeData(cd);

        this.poolName = MemoryNotifInfoCompositeData.getPoolName(cd);
        this.usage = MemoryNotifInfoCompositeData.getUsage(cd);
        this.count = MemoryNotifInfoCompositeData.getCount(cd);
    }

    /**
     * Returns the name of the memory pool that triggers this notification.
     * The memory pool usage has crossed a threshold.
     *
     * @return the name of the memory pool that triggers this notification.
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * Returns the memory usage of the memory pool
     * when this notification was constructed.
     *
     * @return the memory usage of the memory pool
     * when this notification was constructed.
     */
    public MemoryUsage getUsage() {
        return usage;
    }

    /**
     * Returns the number of times that the memory usage has crossed
     * a threshold when the notification was constructed.
     * For usage threshold notifications, this count will be the
     * {@link MemoryPoolMXBean#getUsageThresholdCount threshold
     * count}.  For collection threshold notifications,
     * this count will be the
     * {@link MemoryPoolMXBean#getCollectionUsageThresholdCount
     * collection usage threshold count}.
     *
     * @return the number of times that the memory usage has crossed
     * a threshold when the notification was constructed.
     */
    public long getCount() {
        return count;
    }

    /**
     * Returns a <tt>MemoryNotificationInfo</tt> object represented by the
     * given <tt>CompositeData</tt>.
     * The given <tt>CompositeData</tt> must contain
     * the following attributes:
     * <blockquote>
     * <table border summary="The attributes and the types the given CompositeData contains">
     * <tr>
     *   <th align=left>Attribute Name</th>
     *   <th align=left>Type</th>
     * </tr>
     * <tr>
     *   <td>poolName</td>
     *   <td><tt>java.lang.String</tt></td>
     * </tr>
     * <tr>
     *   <td>usage</td>
     *   <td><tt>javax.management.openmbean.CompositeData</tt></td>
     * </tr>
     * <tr>
     *   <td>count</td>
     *   <td><tt>java.lang.Long</tt></td>
     * </tr>
     * </table>
     * </blockquote>
     *
     * @param cd <tt>CompositeData</tt> representing a
     *           <tt>MemoryNotificationInfo</tt>
     *
     * @throws IllegalArgumentException if <tt>cd</tt> does not
     *   represent a <tt>MemoryNotificationInfo</tt> object.
     *
     * @return a <tt>MemoryNotificationInfo</tt> object represented
     *         by <tt>cd</tt> if <tt>cd</tt> is not <tt>null</tt>;
     *         <tt>null</tt> otherwise.
     */
    public static MemoryNotificationInfo from(CompositeData cd) {
        if (cd == null) {
            return null;
        }

        if (cd instanceof MemoryNotifInfoCompositeData) {
            return ((MemoryNotifInfoCompositeData) cd).getMemoryNotifInfo();
        } else {
            return new MemoryNotificationInfo(cd);
        }
    }
}
