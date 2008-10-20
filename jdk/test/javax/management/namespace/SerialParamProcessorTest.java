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
 *
 * @test SerialParamProcessorTest.java 1.8
 * @summary General SerialParamProcessorTest test.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean SerialParamProcessorTest Wombat WombatMBean
 * @compile -XDignore.symbol.file=true  SerialParamProcessorTest.java
 * @run build SerialParamProcessorTest Wombat WombatMBean
 * @run main SerialParamProcessorTest
 */

import com.sun.jmx.namespace.serial.RewritingProcessor;
import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.management.AttributeChangeNotification;
import javax.management.AttributeList;
import javax.management.JMException;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * Class SerialParamProcessorTest
 *
 * @author Sun Microsystems, Inc.
 */
public class SerialParamProcessorTest {

    /**
     * Creates a new instance of SerialParamProcessorTest
     */
    public SerialParamProcessorTest() {
    }

    public static class MyCompositeData implements Serializable {
        private static final long serialVersionUID = 3186492415099133506L;
        public MyCompositeData(ObjectName foobar,ObjectName absolute,
                long count, String name) {
            this(foobar,absolute,count,name,new ObjectName[]{foobar,absolute});
        }
        @ConstructorProperties(value={"fooBar","absolute","count","name",
                                        "allNames"})
        public MyCompositeData(ObjectName foobar,ObjectName absolute,
                long count, String name, ObjectName[] allnames) {
            this.foobar = foobar;
            this.absolute = absolute;
            this.count = count;
            this.name = name;
            this.allnames = allnames;
        }
        ObjectName foobar,absolute,allnames[];
        long count;
        String name;
        public ObjectName getFooBar() {
            return foobar;
        }
        public ObjectName getAbsolute() {
            return absolute;
        }
        public ObjectName[] getAllNames() {
            return allnames;
        }
        public long getCount() {
            return count;
        }
        public String getName() {
            return name;
        }
        private Object[] toArray() {
            final Object[] props = {
                getName(),getFooBar(),getAbsolute(),getAllNames(),getCount()
            };
            return props;
        }
        @Override
        public boolean equals(Object o) {
            if (o instanceof MyCompositeData)
                return Arrays.deepEquals(toArray(),
                        ((MyCompositeData)o).toArray());
            return false;
        }
        @Override
        public int hashCode() {
            return Arrays.deepHashCode(toArray());
        }
    }

    public static interface MyMXBean {
        public Map<String,MyCompositeData> getAll();
        public MyCompositeData lookup(String name);
        public void put(String name, MyCompositeData data);
        public MyCompositeData remove(String name);
    }

    public static class My implements MyMXBean {
        Map<String,MyCompositeData> datas =
                new HashMap<String,MyCompositeData>();
        public Map<String,MyCompositeData> getAll() {
            return datas;
        }
        public MyCompositeData lookup(String name) {
            return datas.get(name);
        }
        public void put(String name, MyCompositeData data) {
            datas.put(name,data);
        }
        public MyCompositeData remove(String name) {
            return datas.remove(name);
        }
    }

    public static class BandicootClass implements Serializable {
        private static final long serialVersionUID = -5494055748633966355L;
        public final Object gloups;
        public BandicootClass(Object gloups) {
            this.gloups = gloups;
        }
        private Object[] toArray() {
            final Object[] one = {gloups};
            return one;
        }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BandicootClass)) return false;
            final Object[] one = {gloups};
            return Arrays.deepEquals(toArray(),((BandicootClass)obj).toArray());
        }
        @Override
        public int hashCode() {
            if (gloups == null) return 0;
            return Arrays.deepHashCode(toArray());
        }
    }

    // Need this to override equals.
    public static class BandicootNotification extends Notification {
        private static final long serialVersionUID = 664758643764049001L;
        public BandicootNotification(String type, Object source, long seq) {
            super(type,source,seq,0L,"");
        }
        private Object[] toArray() {
            final Object[] vals = {getMessage(),getSequenceNumber(),
                getSource(),getTimeStamp(),getType(),getUserData()};
            return vals;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BandicootNotification)) return false;
            return Arrays.deepEquals(toArray(),
                    ((BandicootNotification)o).toArray());
        }
        @Override
        public int hashCode() {
            return Arrays.deepHashCode(toArray());
        }

    }

    // Need this to override equals.
    public static class BandicootAttributeChangeNotification
            extends AttributeChangeNotification {
        private static final long serialVersionUID = -1392435607144396125L;
        public BandicootAttributeChangeNotification(Object source,
                long seq, long time, String msg, String name, String type,
                Object oldv, Object newv) {
            super(source,seq,time,msg,name,type,oldv,newv);
        }
        private Object[] toArray() {
            final Object[] vals = {getMessage(),getSequenceNumber(),
                getSource(),getTimeStamp(),getType(),getUserData(),
                getAttributeName(), getAttributeType(),getNewValue(),
                getOldValue()};
            return vals;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BandicootAttributeChangeNotification))
                return false;
            return Arrays.deepEquals(toArray(),
                    ((BandicootAttributeChangeNotification)o).toArray());
        }
        @Override
        public int hashCode() {
            return Arrays.deepHashCode(toArray());
        }
        @Override
        public String toString() {
            final StringBuilder b = new StringBuilder();
            b.append(this.getClass().getName()).append(": ");
            b.append("[type=").append(getType()).append("]");
            b.append("[source=").append(getSource()).append("]");
            b.append("[message=").append(getMessage()).append("]");
            b.append("[sequence=").append(getSequenceNumber()).append("]");

            b.append("[attribute=").append(getAttributeName()).append("]");
            b.append("[class=").append(getAttributeType()).append("]");
            b.append("[oldvalue=").append(getOldValue()).append("]");
            b.append("[newvalue=").append(getNewValue()).append("]");

            b.append("[time=").append(getTimeStamp()).append("]");
            b.append("[data=").append(getUserData()).append("]");
            return b.toString();
        }
    }

    private static void addToList(Object[] foos, List<Object> foolist) {
        final ArrayList<Object> fal = new ArrayList<Object>(foos.length);
        for (Object f : foos) {
            if (f.getClass().isArray()) {
                foolist.add(new BandicootClass(f));
                fal.add(new BandicootClass(f));
            } else {
                foolist.add(f);
                fal.add(f);
            }
        }
        foolist.add(new BandicootClass(foos));
        foolist.add(fal);
    }

    public static void testSerial(String msg, Object foo, Object bar,
            RewritingProcessor procForFoo,
            RewritingProcessor procForBar, List<Object> foolist,
            List<Object> barlist, boolean recurse) {
        System.err.println(msg+" Testing serial - "+foo.getClass().getName());
        final Object bar1  = procForFoo.rewriteInput(foo);
        final Object foo1  = procForFoo.rewriteOutput(bar);
        final Object bar2  = procForFoo.rewriteInput(foo1);
        final Object foo2  = procForFoo.rewriteOutput(bar1);

        final Object bar3  = procForBar.rewriteOutput(foo);
        final Object foo3  = procForBar.rewriteInput(bar);
        final Object bar4  = procForBar.rewriteOutput(foo3);
        final Object foo4  = procForBar.rewriteInput(bar3);

        final Object bar5  = procForFoo.rewriteInput(foo3);
        final Object foo5  = procForFoo.rewriteOutput(bar3);

        final Object bar6  = procForBar.rewriteOutput(foo1);
        final Object foo6  = procForBar.rewriteInput(bar1);

        final Object[] foos = {foo, foo1, foo2, foo3, foo4, foo5, foo6};
        final Object[] bars = {bar, bar1, bar2, bar3, bar4, bar5, bar6};

        final Object[] foot = { foo };
        final Object[] bart = { bar };
        for (int j=1;j<foos.length;j++) {
            final Object[] foox = { foos[j] };
            final Object[] barx = { bars[j] };
            if (!Arrays.deepEquals(foot,foox)) {
                System.err.println(msg+" foo"+j+" "+foos[j]+" != "+foo);
                throw new RuntimeException(msg+" foo"+j+" != foo");
            }
            if (!Arrays.deepEquals(bart,barx)) {
                System.err.println(msg+" bar"+j+" "+bars[j]+" != "+bar);
                throw new RuntimeException(msg+" bar"+j+" != bar");
            }

        }
        if (recurse) {
            testSerial("Array: " + msg,foos,bars,procForFoo,
                    procForBar,foolist,barlist,false);
            addToList(foos,foolist);
            addToList(bars,barlist);
        }
    }
    public static void testSerial(Object[][] objects,
            RewritingProcessor procForFoo,
            RewritingProcessor procForBar) {
        int i=0;
        final List<Object> foolist  = new LinkedList<Object>();
        final List<Object> barlist = new LinkedList<Object>();
        for (Object[] row : objects) {
            i++;
            Object foo = row[0];
            Object bar = row[1];
            String msg1 = "[" +foo.getClass().getName() + "] step " +
                    i +": ";

            testSerial(msg1,foo,bar,procForFoo,procForBar,foolist,barlist,true);

            final BandicootClass kfoo = new BandicootClass(foo);
            final BandicootClass kbar = new BandicootClass(bar);

            String msg2 = "[" +kfoo.getClass().getName() + "] step " +
                    i +": ";
            testSerial(msg2,kfoo,kbar,procForFoo,procForBar,foolist,barlist,true);
        }
        String msg31 = "foo[] and bar[]: ";
        testSerial(msg31,foolist.toArray(),barlist.toArray(),
                   procForFoo,procForBar,foolist,barlist,false);

        String msg3 = "foolist and barlist: ";
        testSerial(msg3,new LinkedList<Object>(foolist),
                   new LinkedList<Object>(barlist),
                   procForFoo,procForBar,foolist,barlist,false);

        final BandicootClass kfoolist = new BandicootClass(foolist);
        final BandicootClass kbarlist = new BandicootClass(barlist);
        String msg4 = "kfoolist and kbarlist: ";
        testSerial(msg4,kfoolist,kbarlist,procForFoo,procForBar,foolist,barlist,false);
    }

    /**
     * The idea of this  method is to convert {@code foo} things into
     * {@code bar} things...
     * @param foo the string to replace.
     * @param bar the replacement for {@code foo}
     *        ({@code foo} becomes {@code bar}).
     * @param sfoo a string that may contain {@code foo}, that will be embedded
     *        in non-replaceable parts of the domain in order to attempt to
     *        trick the replacement logic.
     * @param sbar a string that may contain {@code bar}, that will be embedded
     *        in non-replaceable parts of the domain in order to attempt to
     *        trick the replacement logic.
     **/
    public static void doSerialTest(String foo, String bar, String sfoo,
                               String sbar) {
        try {
        final RewritingProcessor procForFoo = RewritingProcessor.
                newRewritingProcessor(foo,bar);
        final RewritingProcessor procForBar =RewritingProcessor.
                newRewritingProcessor(bar,foo);
        final String foop = (foo.isEmpty())?foo:foo+"//";
        final String pfoo = (foo.isEmpty())?foo:"//"+foo;
        final String barp = (bar.isEmpty())?bar:bar+"//";
        final String pbar = (bar.isEmpty())?bar:"//"+bar;
        final String sfoop = (sfoo.isEmpty())?sfoo:sfoo+"//";
        final String psfoo = (sfoo.isEmpty())?sfoo:"//"+sfoo;
        final String sbarp = (sbar.isEmpty())?sbar:sbar+"//";
        final String psbar = (sbar.isEmpty())?sbar:"//"+sbar;

        // A trick to avoid writing Open Data by hand...
        final My tricks = new My();

        // A treat to automagically convert trick things into Open Data.
        final StandardMBean treats =
                new StandardMBean(tricks,MyMXBean.class,true);

        // datas[i][0] is expected to be transformed in datas[i][1]
        //
        final MyCompositeData[][] datas = {
            { // this foo thing:
            new MyCompositeData(new ObjectName(foop+sbarp+"x:y=z"),
                    new ObjectName("//"+foop+sbarp+"x:y=z"),1,sfoop+sbarp+"foobar"),
              // should be transformed into this bar thing:
            new MyCompositeData(new ObjectName(barp+sbarp+"x:y=z"),
                    new ObjectName("//"+foop+sbarp+"x:y=z"),1,sfoop+sbarp+"foobar"),
            },
            { // this foo thing:
            new MyCompositeData(new ObjectName(foop+sfoop+"x:y=z"),
                    new ObjectName("//"+foop+sfoop+"x:y=z"),1,sfoop+sbarp+"barfoo"),
              // should be transformed into this bar thing:
            new MyCompositeData(new ObjectName(barp+sfoop+"x:y=z"),
                    new ObjectName("//"+foop+sfoop+"x:y=z"),1,sfoop+sbarp+"barfoo"),
            }
        };

        // objects[i][0] is expected to be transformed into objects[i][1]
        //
        final Object[][] objects = new Object[][] {
            {new Long(1), new Long(1)},
            {
                new ObjectName(foop+sbarp+"x:y=z"),
                        new ObjectName(barp+sbarp+"x:y=z")
            },
            {
                new ObjectName(foop+sfoop+"x:y=z"),
                        new ObjectName(barp+sfoop+"x:y=z")
            },
            {
                new ObjectName("//"+foop+sbarp+"x:y=z"),
                new ObjectName("//"+foop+sbarp+"x:y=z"),
            },
            {
                new ObjectName("//"+foop+sfoop+"x:y=z"),
                new ObjectName("//"+foop+sfoop+"x:y=z")
            },
            {
                foop+sbarp+"x:y=z",foop+sbarp+"x:y=z"
            },
            {
                foop+sfoop+"x:y=z",foop+sfoop+"x:y=z"
            },
            {
                barp+sbarp+"x:y=z",barp+sbarp+"x:y=z"
            },
            {
                barp+sfoop+"x:y=z",barp+sfoop+"x:y=z"
            },
            {
            new BandicootNotification("test",new ObjectName(foop+sfoop+"x:y=z"),1L),
            new BandicootNotification("test",new ObjectName(barp+sfoop+"x:y=z"),1L),
            },
            {
            new BandicootNotification("test",new ObjectName("//"+foop+sfoop+"x:y=z"),2L),
            new BandicootNotification("test",new ObjectName("//"+foop+sfoop+"x:y=z"),2L),
            },
            {
            new BandicootAttributeChangeNotification(
                    new ObjectName(foop+sfoop+"x:y=z"),1L,2L,"blah","attrname",
                    ObjectName.class.getName(),
                    new ObjectName(foop+sfoop+"x:y=old"),
                    new ObjectName(foop+sfoop+"x:y=new")),
            new BandicootAttributeChangeNotification(
                    new ObjectName(barp+sfoop+"x:y=z"),1L,2L,"blah","attrname",
                    ObjectName.class.getName(),
                    new ObjectName(barp+sfoop+"x:y=old"),
                    new ObjectName(barp+sfoop+"x:y=new")),
            },
            {
            new BandicootAttributeChangeNotification(
                    new ObjectName("//"+foop+sfoop+"x:y=z"),1L,2L,"blah","attrname",
                    ObjectName.class.getName(),
                    new ObjectName("//"+foop+sfoop+"x:y=old"),
                    new ObjectName(foop+sfoop+"x:y=new")),
            new BandicootAttributeChangeNotification(
                    new ObjectName("//"+foop+sfoop+"x:y=z"),1L,2L,"blah","attrname",
                    ObjectName.class.getName(),
                    new ObjectName("//"+foop+sfoop+"x:y=old"),
                    new ObjectName(barp+sfoop+"x:y=new")),
            }
        };

        // List that will merge datas & objects & datas converted to open
        // types...
        //
        final List<Object[]> list = new ArrayList<Object[]>();

        // Add all objects...
        //
        list.addAll(Arrays.asList(objects));

        // Build Map<String,MyCompositeData> with datas[i][0] (cfoo)
        //
        for (int i=0;i<datas.length;i++) {
            tricks.put(sfoop+sbarp+"x"+i,datas[i][0]);
        }

        // Let MXBean convert Map<String,MyCompositeData> to TabularData
        // (foo things)
        final Object cfoo = treats.getAttribute("All");
        final AttributeList afoo = treats.getAttributes(new String[] {"All"});

        // Build Map<String,MyCompositeData> with datas[i][1] (cbar)
        //
        for (int i=0;i<datas.length;i++) {
            tricks.remove(sfoop+sbarp+"x"+i);
            tricks.put(sfoop+sbarp+"x"+i,datas[i][1]);
        }

        // Let MXBean convert Map<String,MyCompositeData> to TabularData
        // (bar things)
        final Object cbar = treats.getAttribute("All");
        final AttributeList abar = treats.getAttributes(new String[] {"All"});

        // Add all datas to list
        for (int i=0;i<datas.length;i++) {
            list.add(datas[i]);
        }

        // Add converted TabularDatas to list
        list.add(new Object[] {cfoo,cbar});

        // Add AttributeList containing TabularData to list
        list.add(new Object[] {afoo,abar});

        // Add Arrays of the above to list...
        list.add(new Object[] {new Object[] {cfoo,afoo,1L},
                               new Object[] {cbar,abar,1L}});

        // Add MBeanInfo...
        list.add(new Object[] {treats.getMBeanInfo(),treats.getMBeanInfo()});

        // No ready to test conversion of all foo things into bar things.
        //
        testSerial(list.toArray(new Object[list.size()][]),
                procForFoo,procForBar);
        } catch (JMException x) {
            throw new RuntimeException(x);
        }
    }

    public static void aaaTest() {
        System.err.println("\n--------------------- aaaTest ----------------");
        System.err.println("---------------- 'foo' becomes 'bar' ---------\n");
        doSerialTest("foo","bar","foo","bar");
    }

    public static void aabTest() {
        System.err.println("\n--------------------- aabTest ----------------");
        System.err.println("---------- 'foo//bar' becomes 'bar//foo' -----\n");
        doSerialTest("foo//bar","bar//foo","foo","bar");
    }

    public static void aacTest() {
        System.err.println("\n----------------- aacTest --------------------");
        System.err.println("------------ 'foo//bar' becomes '' -----------\n");
        doSerialTest("foo//bar","","foo","bar");
    }

    public static void aadTest() {
        System.err.println("\n----------------- aadTest --------------------");
        System.err.println("----------- '' becomes 'bar//foo' ------------\n");
        doSerialTest("","bar//foo","","bar//foo");
    }

    public static void aaeTest() {
        System.err.println("\n----------------- aaeTest --------------------");
        System.err.println("----------------- '' becomes '' --------------\n");
        doSerialTest("","","foo","bar//foo");
    }

    // Let's be wild...
    public static void aafTest() {
        System.err.println("\n----------------- aafTest --------------------");
        System.err.println("----------- '' becomes '' -- (bis) -----------\n");
        doSerialTest("","","","");
    }
    public static void aagTest() {
        System.err.println("\n----------------- aagTest --------------------");
        System.err.println("----------- foobar becomes foobar ------------\n");
        doSerialTest("foobar","foobar","foobar","foobar");
    }

    // TODO add test with descriptor, MBeanInfo, Open Types, etc...
    public static void main(String[] args) {
        aaaTest();
        aabTest();
        aacTest();
        aadTest();
        aaeTest();
        aafTest();
        aagTest();

        // TODO: add a test case to test *exactly* the serialization
        // of Notification and AttributeChangeNotification, and not of
        // a subclass of these.
        // This will involve implementing some hack, because we
        // can't use equals() to compare the results.
    }
}
