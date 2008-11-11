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

/*
 * @test
 * @bug 5072267
 * @summary Test that an MBean can handle localized Notification messages.
 * @author Eamonn McManus
 */

import java.util.Collections;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import javax.management.ClientContext;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.SendNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class LocaleAwareBroadcasterTest {
    static final ObjectName mbeanName = ObjectName.valueOf("d:type=LocaleAware");

    static final String
            messageKey = "broken.window",
            defaultMessage = "broken window",
            frenchMessage = "fen\u00eatre bris\u00e9e",
            irishMessage = "fuinneog briste";

    public static class Bundle extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {messageKey, defaultMessage},
            };
        }
    }

    public static class Bundle_fr extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {messageKey, frenchMessage},
            };
        }
    }

    public static class Bundle_ga extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {messageKey, irishMessage},
            };
        }
    }

    static volatile String failure;

    public static interface LocaleAwareMBean {
        public void sendNotification(Notification n);
    }

    public static class LocaleAware
            implements LocaleAwareMBean, NotificationEmitter, SendNotification {

        private final ConcurrentMap<Locale, NotificationBroadcasterSupport>
                localeToEmitter = newConcurrentMap();

        public void sendNotification(Notification n) {
            for (Map.Entry<Locale, NotificationBroadcasterSupport> entry :
                    localeToEmitter.entrySet()) {
                Notification localizedNotif =
                        localizeNotification(n, entry.getKey());
                entry.getValue().sendNotification(localizedNotif);
            }
        }

        public void addNotificationListener(
                NotificationListener listener,
                NotificationFilter filter,
                Object handback)
                throws IllegalArgumentException {
            Locale locale = ClientContext.getLocale();
            NotificationBroadcasterSupport broadcaster;
            broadcaster = localeToEmitter.get(locale);
            if (broadcaster == null) {
                broadcaster = new NotificationBroadcasterSupport();
                NotificationBroadcasterSupport old =
                        localeToEmitter.putIfAbsent(locale, broadcaster);
                if (old != null)
                    broadcaster = old;
            }
            broadcaster.addNotificationListener(listener, filter, handback);
        }

        public void removeNotificationListener(NotificationListener listener)
                throws ListenerNotFoundException {
            Locale locale = ClientContext.getLocale();
            NotificationBroadcasterSupport broadcaster =
                    localeToEmitter.get(locale);
            if (broadcaster == null)
                throw new ListenerNotFoundException();
            broadcaster.removeNotificationListener(listener);
        }

        public void removeNotificationListener(
                NotificationListener listener,
                NotificationFilter filter,
                Object handback)
                throws ListenerNotFoundException {
            Locale locale = ClientContext.getLocale();
            NotificationBroadcasterSupport broadcaster =
                    localeToEmitter.get(locale);
            if (broadcaster == null)
                throw new ListenerNotFoundException();
            broadcaster.removeNotificationListener(listener, filter, handback);
        }

        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[0];
        }
    }

    // Localize notif using the convention that the message looks like
    // [resourcebundlename:resourcekey]defaultmessage
    // for example [foo.bar.Resources:unknown.problem]
    static Notification localizeNotification(Notification n, Locale locale) {
        String msg = n.getMessage();
        if (!msg.startsWith("["))
            return n;
        int close = msg.indexOf(']');
        if (close < 0)
            throw new IllegalArgumentException("Bad notification message: " + msg);
        int colon = msg.indexOf(':');
        if (colon < 0 || colon > close)
            throw new IllegalArgumentException("Bad notification message: " + msg);
        String bundleName = msg.substring(1, colon);
        String key = msg.substring(colon + 1, close);
        ClassLoader loader = LocaleAwareBroadcasterTest.class.getClassLoader();
        ResourceBundle bundle =
                ResourceBundle.getBundle(bundleName, locale, loader);
        try {
            msg = bundle.getString(key);
        } catch (MissingResourceException e) {
            msg = msg.substring(close + 1);
        }
        n = (Notification) n.clone();
        n.setMessage(msg);
        return n;
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(new Locale("en"));
        testLocal();
        testRemote();
        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    static interface AddListenerInLocale {
        public void addListenerInLocale(
                MBeanServerConnection mbsc,
                NotificationListener listener,
                Locale locale) throws Exception;
    }

    private static void testLocal() throws Exception {
        System.out.println("Test local MBeanServer using doWithContext");
        MBeanServer mbs = makeMBS();
        AddListenerInLocale addListener = new AddListenerInLocale() {
            public void addListenerInLocale(
                    final MBeanServerConnection mbsc,
                    final NotificationListener listener,
                    Locale locale) throws Exception {
                Map<String, String> localeContext = Collections.singletonMap(
                        ClientContext.LOCALE_KEY, locale.toString());
                ClientContext.doWithContext(
                        localeContext, new Callable<Void>() {
                    public Void call() throws Exception {
                        mbsc.addNotificationListener(
                                mbeanName, listener, null, null);
                        return null;
                    }
                });
            }
        };
        test(mbs, addListener);
    }

    private static void testRemote() throws Exception {
        System.out.println("Test remote MBeanServer using withLocale");
        MBeanServer mbs = makeMBS();
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://");
        JMXConnectorServer cs =
                JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
        cs.start();
        JMXServiceURL addr = cs.getAddress();
        JMXConnector cc = JMXConnectorFactory.connect(addr);
        MBeanServerConnection mbsc = cc.getMBeanServerConnection();
        AddListenerInLocale addListenerInLocale = new AddListenerInLocale() {
            public void addListenerInLocale(
                    MBeanServerConnection mbsc,
                    NotificationListener listener,
                    Locale locale) throws Exception {
                mbsc = ClientContext.withLocale(mbsc, locale);
                mbsc.addNotificationListener(mbeanName, listener, null, null);
            }
        };
        try {
            test(mbsc, addListenerInLocale);
        } finally {
            try {
                cc.close();
            } catch (Exception e) {}
            cs.stop();
        }
    }

    static class QueueListener implements NotificationListener {
        final BlockingQueue<Notification> queue =
                new ArrayBlockingQueue<Notification>(10);

        public void handleNotification(Notification notification,
                                       Object handback) {
            queue.add(notification);
        }
    }

    private static void test(
            MBeanServerConnection mbsc, AddListenerInLocale addListener)
            throws Exception {
        QueueListener defaultListener = new QueueListener();
        QueueListener frenchListener = new QueueListener();
        QueueListener irishListener = new QueueListener();
        mbsc.addNotificationListener(mbeanName, defaultListener, null, null);
        addListener.addListenerInLocale(mbsc, frenchListener, new Locale("fr"));
        addListener.addListenerInLocale(mbsc, irishListener, new Locale("ga"));

        LocaleAwareMBean proxy =
                JMX.newMBeanProxy(mbsc, mbeanName, LocaleAwareMBean.class);
        String notifMsg = "[" + Bundle.class.getName() + ":" + messageKey + "]" +
                "broken window (default message that should never be seen)";
        Notification notif = new Notification(
                "notif.type", mbeanName, 0L, notifMsg);
        proxy.sendNotification(notif);

        final Object[][] expected = {
            {defaultListener, defaultMessage},
            {frenchListener, frenchMessage},
            {irishListener, irishMessage},
        };
        for (Object[] exp : expected) {
            QueueListener ql = (QueueListener) exp[0];
            String msg = (String) exp[1];
            System.out.println("Checking: " + msg);
            Notification n = ql.queue.poll(1, TimeUnit.SECONDS);
            if (n == null)
                fail("Did not receive expected notif: " + msg);
            if (!n.getMessage().equals(msg)) {
                fail("Received notif with wrong message: got " +
                        n.getMessage() + ", expected " + msg);
            }
            n = ql.queue.poll(2, TimeUnit.MILLISECONDS);
            if (n != null)
                fail("Received unexpected extra notif: " + n);
        }
    }

    private static MBeanServer makeMBS() throws Exception {
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        LocaleAware aware = new LocaleAware();
        mbs.registerMBean(aware, mbeanName);
        return mbs;
    }

    static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
        return new ConcurrentHashMap<K, V>();
    }

    static void fail(String why) {
        System.out.println("FAIL: " + why);
        failure = why;
    }
}
