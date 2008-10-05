/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.util.Random;
import java.util.Set;
import javax.management.AttributeChangeNotification;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.StandardMBean;


/**
 * Dynamic MBean based on StandardMBean
 * Class Wombat
 * Wombat Description
 * @author dfuchs
 */
public class Wombat extends StandardMBean
        implements WombatMBean, NotificationEmitter, MBeanRegistration {

    /**
     * Attribute : Caption
     */
    private String caption = "I'm a wombat";

    private final long MAX_SEED = 36000;
    private final long seed;
    private final long period;
    private volatile int mood = 0;

    public int getMood() {
        final long  degree = seed + (System.currentTimeMillis()/period)%MAX_SEED;
        final double angle = ((double)degree)/100;
        mood = (int)(100.0*Math.sin(angle));
        return mood;
    }

    public Wombat() throws NotCompliantMBeanException {
        this(WombatMBean.class);
    }

    public Wombat(Class<? extends WombatMBean> clazz)
            throws NotCompliantMBeanException {
        super(clazz);
        final Random r = new Random();
        seed = ((r.nextLong() % MAX_SEED) + MAX_SEED)%MAX_SEED;
        period = 200 + (((r.nextLong()%80)+80)%80)*10;
    }

    /**
     * Next are the methods to compute MBeanInfo.
     * You shouldn't update these methods.
     */
    @Override
    protected String getDescription(MBeanInfo info) {
        return "Wombats are strange beasts. You will find them down under " +
                "and in some computer programms.";
    }

    @Override
    protected String getDescription(MBeanAttributeInfo info) {
        String description = null;
        if (info.getName().equals("Caption")) {
            description = "A simple caption to describe a wombat";
        }
        if (info.getName().equals("Mood")) {
            description = "This Wombat's mood on a [-100,+100] scale."+
                      " -100 means that this wombat is very angry.";
        }
        return description;
    }

    @Override
    protected String getDescription(MBeanOperationInfo op,
            MBeanParameterInfo param,
            int sequence) {
        return null;
    }

    @Override
    protected String getParameterName(MBeanOperationInfo op,
            MBeanParameterInfo param,
            int sequence) {
        return null;
    }

    @Override
    protected String getDescription(MBeanOperationInfo info) {
        String description = null;
        return description;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        MBeanInfo mbinfo = super.getMBeanInfo();
        return new MBeanInfo(mbinfo.getClassName(),
                mbinfo.getDescription(),
                mbinfo.getAttributes(),
                mbinfo.getConstructors(),
                mbinfo.getOperations(),
                getNotificationInfo());
    }

    /**
     * Get A simple caption to describe a wombat
     */
    public synchronized String getCaption() {
        return caption;
    }

    /**
     * Set A simple caption to describe a wombat
     */
    public void setCaption(String value) {
        final String oldValue;
        synchronized (this) {
            oldValue = caption;
            caption = value;
        }
        final AttributeChangeNotification notif =
                new AttributeChangeNotification(objectName,
                    getNextSeqNumber(),
                    System.currentTimeMillis(),
                    "Caption changed","Caption",
                    String.class.getName(),oldValue,value);
        broadcaster.sendNotification(notif);
    }

    /**
     * MBeanNotification support
     * You shouldn't update these methods
     */
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws IllegalArgumentException {
        broadcaster.addNotificationListener(listener, filter, handback);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(new String[] {
                AttributeChangeNotification.ATTRIBUTE_CHANGE},
                javax.management.AttributeChangeNotification.class.getName(),
                "Sent when the caption changes")
            };
    }

    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }

    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener, filter, handback);
    }

    private synchronized long getNextSeqNumber() {
        return seqNumber++;
    }

    private long seqNumber;

    private final NotificationBroadcasterSupport broadcaster = new NotificationBroadcasterSupport();

    /**
     * Allows the MBean to perform any operations it needs before being
     * registered in the MBean server. If the name of the MBean is not
     * specified, the MBean can provide a name for its registration. If
     * any exception is raised, the MBean will not be registered in the
     * MBean server.
     * @param server The MBean server in which the MBean will be registered.
     * @param name The object name of the MBean. This name is null if the
     * name parameter to one of the createMBean or registerMBean methods in
     * the MBeanServer interface is null. In that case, this method must
     * return a non-null ObjectName for the new MBean.
     * @return The name under which the MBean is to be registered. This value
     * must not be null. If the name parameter is not null, it will usually
     * but not necessarily be the returned value.
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        objectName = name;
        mbeanServer = server;
        return super.preRegister(server, name);
    }

    /**
     * Allows the MBean to perform any operations needed after having
     * been registered in the MBean server or after the registration has
     * failed.
     * @param registrationDone Indicates wether or not the MBean has been
     * successfully registered in the MBean server. The value false means
     * that the registration has failed.
     */
    @Override
    public void postRegister(Boolean registrationDone) {
        super.postRegister(registrationDone);
    }

    /**
     * Allows the MBean to perform any operations it needs before being
     * unregistered by the MBean server.
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    @Override
    public void preDeregister() throws Exception {
        super.preDeregister();
    }

    /**
     * Allows the MBean to perform any operations needed after having been
     * unregistered in the MBean server.
     */
    @Override
    public void postDeregister() {
        super.postDeregister();
    }

    public Set<ObjectName> listMatching(ObjectName pattern) {
        return mbeanServer.queryNames(pattern, null);
    }

    private MBeanServer mbeanServer;

    private ObjectName objectName;
}
