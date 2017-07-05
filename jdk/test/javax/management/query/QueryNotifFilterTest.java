/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test QueryNotifFilterTest
 * @bug 6610917
 * @summary Test the QueryNotificationFilter class
 * @author Eamonn McManus
 */

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryEval;
import javax.management.QueryExp;
import javax.management.QueryNotificationFilter;

public class QueryNotifFilterTest {
    private static class Case {
        final Notification notif;
        final QueryExp query;
        final boolean expect;
        final Class<? extends Notification> notifClass;
        Case(Notification notif, String query, boolean expect) {
            this(notif, query, notif.getClass(), expect);
        }
        Case(Notification notif, String query,
                Class<? extends Notification> notifClass, boolean expect) {
            this(notif, Query.fromString(query), notifClass, expect);
        }
        Case(Notification notif, QueryExp query, boolean expect) {
            this(notif, query, notif.getClass(), expect);
        }
        Case(Notification notif, QueryExp query,
                Class<? extends Notification> notifClass, boolean expect) {
            this.notif = notif;
            this.query = query;
            this.expect = expect;
            this.notifClass = notifClass;
        }
    }

    /* In principle users can create their own implementations of QueryExp
     * and use them with QueryNotificationFilter.  If they do so, then
     * they can call any MBeanServer method.  Not all of those methods
     * will work with the special MBeanServer we concoct to analyze a
     * Notification, but some will, including some that are not called
     * by the standard queries.  So we check each of those cases too.
     */
    private static class ExoticCase {
        final Notification trueNotif;
        final Notification falseNotif;
        final QueryExp query;
        ExoticCase(Notification trueNotif, Notification falseNotif, QueryExp query) {
            this.trueNotif = trueNotif;
            this.falseNotif = falseNotif;
            this.query = query;
        }
    }

    private static abstract class ExoticQuery
            extends QueryEval implements QueryExp {
        private final String queryString;
        ExoticQuery(String queryString) {
            this.queryString = queryString;
        }
        abstract boolean apply(MBeanServer mbs, ObjectName name) throws Exception;
        //@Override - doesn't override in JDK5
        public boolean apply(ObjectName name) {
            try {
                return apply(getMBeanServer(), name);
            } catch (Exception e) {
                e.printStackTrace(System.out);
                return false;
            }
        }
        @Override
        public String toString() {
            return queryString;
        }
    }

    private static ObjectName makeObjectName(String s) {
        try {
            return new ObjectName(s);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    public static class CustomNotification extends Notification {
        public CustomNotification(String type, Object source, long seqNo) {
            super(type, source, seqNo);
        }

        public String getName() {
            return "claude";
        }

        public boolean isInteresting() {
            return true;
        }
    }

    private static final Notification simpleNotif =
            new Notification("mytype", "source", 0L);
    private static final Notification attrChangeNotif =
            new AttributeChangeNotification(
                    "x", 0L, 0L, "msg", "AttrName", "int", 2, 3);
    private static final ObjectName testObjectName = makeObjectName("a:b=c");
    private static final Notification sourcedNotif =
            new Notification("mytype", testObjectName, 0L);
    private static final Notification customNotif =
            new CustomNotification("mytype", testObjectName, 0L);

    private static final Case[] testCases = {
        new Case(simpleNotif, "Type = 'mytype'", true),
        new Case(simpleNotif, "Type = 'mytype'",
                Notification.class, true),
        new Case(simpleNotif, "Type = 'mytype'",
                AttributeChangeNotification.class, false),
        new Case(simpleNotif, "Type != 'mytype'", false),
        new Case(simpleNotif, "Type = 'somethingelse'", false),
        new Case(attrChangeNotif, "AttributeName = 'AttrName'", true),
        new Case(attrChangeNotif,
                "instanceof 'javax.management.AttributeChangeNotification'",
                true),
        new Case(attrChangeNotif,
                "instanceof 'javax.management.Notification'",
                true),
        new Case(attrChangeNotif,
                "instanceof 'javax.management.relation.MBeanServerNotification'",
                false),
        new Case(attrChangeNotif,
                "class = 'javax.management.AttributeChangeNotification'",
                true),
        new Case(attrChangeNotif,
                "javax.management.AttributeChangeNotification#AttributeName = 'AttrName'",
                true),
        new Case(sourcedNotif,
                testObjectName,
                true),
        new Case(sourcedNotif,
                makeObjectName("a*:b=*"),
                true),
        new Case(sourcedNotif,
                makeObjectName("a*:c=*"),
                false),
        new Case(customNotif, "Name = 'claude'", true),
        new Case(customNotif, "Name = 'tiddly'", false),
        new Case(customNotif, "Interesting = true", true),
        new Case(customNotif, "Interesting = false", false),
    };

    private static final ExoticCase[] exoticTestCases = {
        new ExoticCase(
                simpleNotif, new Notification("notmytype", "source", 0L),
                new ExoticQuery("getAttributes") {
                    boolean apply(MBeanServer mbs, ObjectName name)
                            throws Exception {
                        List<Attribute> attrs = mbs.getAttributes(
                                name, new String[] {"Type", "Source"}).asList();
                        return (attrs.get(0).equals(new Attribute("Type", "mytype")) &&
                                attrs.get(1).equals(new Attribute("Source", "source")));
                    }
                }),
        new ExoticCase(
                new Notification("mytype", "source", 0L) {},
                simpleNotif,
                new ExoticQuery("getClassLoaderFor") {
                    boolean apply(MBeanServer mbs, ObjectName name)
                            throws Exception {
                        return (mbs.getClassLoaderFor(name) ==
                                this.getClass().getClassLoader());
                    }
                }),
        new ExoticCase(
                sourcedNotif, simpleNotif,
                new ExoticQuery("getDomains") {
                    boolean apply(MBeanServer mbs, ObjectName name)
                            throws Exception {
                        return Arrays.equals(mbs.getDomains(),
                                new String[] {testObjectName.getDomain()});
                    }
                }),
        new ExoticCase(
                simpleNotif, attrChangeNotif,
                new ExoticQuery("getMBeanInfo") {
                    boolean apply(MBeanServer mbs, ObjectName name)
                            throws Exception {
                        MBeanInfo mbi = mbs.getMBeanInfo(name);
                        // If we ever add a constructor to Notification then
                        // we will have to change the 4 below.
                        if (mbi.getOperations().length > 0 ||
                                mbi.getConstructors().length != 4 ||
                                mbi.getNotifications().length > 0)
                            return false;
                        Set<String> expect = new HashSet<String>(
                            Arrays.asList(
                                "Class", "Message", "SequenceNumber", "Source",
                                "TimeStamp", "Type", "UserData"));
                        Set<String> actual = new HashSet<String>();
                        for (MBeanAttributeInfo mbai : mbi.getAttributes())
                            actual.add(mbai.getName());
                        return actual.equals(expect);
                    }
                }),
        new ExoticCase(
                simpleNotif, attrChangeNotif,
                new ExoticQuery("getObjectInstance") {
                    boolean apply(MBeanServer mbs, ObjectName name)
                            throws Exception {
                        ObjectInstance oi = mbs.getObjectInstance(name);
                        return oi.getClassName().equals(Notification.class.getName());
                    }
                }),
        new ExoticCase(
                sourcedNotif, simpleNotif,
                new ExoticQuery("queryNames") {
                    boolean apply(MBeanServer mbs, ObjectName name)
                            throws Exception {
                        Set<ObjectName> names = mbs.queryNames(null,
                                Query.eq(Query.attr("Type"), Query.value("mytype")));
                        return names.equals(Collections.singleton(testObjectName));
                    }
                }),
        new ExoticCase(
                sourcedNotif, simpleNotif,
                new ExoticQuery("queryMBeans") {
                    boolean apply(MBeanServer mbs, ObjectName name)
                            throws Exception {
                        Set<ObjectInstance> insts = mbs.queryMBeans(null,
                                Query.eq(Query.attr("Type"), Query.value("mytype")));
                        if (insts.size() != 1)
                            return false;
                        ObjectInstance inst = insts.iterator().next();
                        return (inst.getObjectName().equals(testObjectName) &&
                                inst.getClassName().equals(Notification.class.getName()));
                    }
                }),
    };

    private static enum Test {
        QUERY_EXP("query"), STRING("string"), STRING_PLUS_CLASS("string with class");
        private final String name;
        Test(String name) {
            this.name = name;
        }
        @Override
        public String toString() {
            return name;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean allok = true;
        for (Case testCase : testCases) {
            for (Test test : Test.values()) {
                QueryNotificationFilter nf;
                String queryString;
                switch (test) {
                case QUERY_EXP: {
                    QueryExp inst = Query.isInstanceOf(
                            Query.value(testCase.notifClass.getName()));
                    QueryExp and = Query.and(inst, testCase.query);
                    queryString = Query.toString(and);
                    nf = new QueryNotificationFilter(and);
                    break;
                }
                case STRING: {
                    String s = "instanceof '" + testCase.notifClass.getName() + "'";
                    queryString = s + " and " + Query.toString(testCase.query);
                    nf = new QueryNotificationFilter(queryString);
                    break;
                }
                case STRING_PLUS_CLASS:
                    queryString = null;
                    nf = new QueryNotificationFilter(
                            testCase.notifClass, Query.toString(testCase.query));
                    break;
                default:
                    throw new AssertionError();
                }
                boolean accept = nf.isNotificationEnabled(testCase.notif);
                if (queryString != null) {
                    queryString = Query.toString(Query.fromString(queryString));
                    if (!queryString.equals(Query.toString(nf.getQuery()))) {
                        System.out.println("FAIL: query string mismatch: expected " +
                                "\"" + queryString + "\", got \"" +
                                Query.toString(nf.getQuery()));
                        allok = false;
                    }
                }
                boolean ok = (accept == testCase.expect);
                System.out.println((ok ? "pass" : "FAIL") + ": " +
                        testCase.query + " (" + test + ")");
                allok &= ok;
            }
        }
        for (ExoticCase testCase : exoticTestCases) {
            NotificationFilter nf = new QueryNotificationFilter(testCase.query);
            for (boolean expect : new boolean[] {true, false}) {
                Notification n = expect ? testCase.trueNotif : testCase.falseNotif;
                boolean accept = nf.isNotificationEnabled(n);
                boolean ok = (accept == expect);
                System.out.println((ok ? "pass" : "FAIL") + ": " +
                        testCase.query + ": " + n);
                allok &= ok;
            }
        }
        if (!allok)
            throw new Exception("TEST FAILED");
    }
}
