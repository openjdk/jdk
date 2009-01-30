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
 * @test %M% %I%
 * @bug 6675526
 * @summary Test MBeans named with &#64;ObjectNameTemplate
 * @author Jean-Francois Denise
 * @run main/othervm ObjectNameTemplateTest
 */
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.ImmutableDescriptor;
import javax.management.InvalidAttributeValueException;
import javax.management.JMX;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MXBean;
import javax.management.MBean;
import javax.management.ManagedAttribute;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ObjectNameTemplate;
import javax.management.ReflectionException;
import javax.management.StandardMBean;

public class ObjectNameTemplateTest {

    private static MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    private static final String NAME_TEMPLATE_MULTI =
            "com.example:type=MultiStdCache,name={Name}";
    private static final String NAME_TEMPLATE_MONO =
            "com.example:{Type}={TypeValue}";
    private static final String NAME_TEMPLATE_QUOTED =
            "com.example:type=Quotted,name=\"{Name}\"";
    private static final String NAME_TEMPLATE_WRAPPED =
            "com.example:type=MgtInterface,id={Id}";
    private static final String NAME_TEMPLATE_FULL =
            "{Naming}";
    private static final String FULL_NAME = "com.example:type=NotAdvised";
    private static final String NAME1 = "toto1";
    private static final String NAME2 = "toto2";
    private static final String TYPE_KEY = "thisIsTheType";
    private static final String TYPE_VALUE = "aTypeValue";
    private static final String INVALID_NAME = "?,=*,\n, ";
    private static final int ID = 999;
    private static Object[] EMPTY_PARAMS = {};
    private static String[] EMPTY_SIGNATURE = {};
    private static final ObjectName OBJECTNAME_CACHE =
            ObjectName.valueOf("com.example:type=Cache");
    private static final ObjectName OBJECTNAME_SUBCACHE =
            ObjectName.valueOf("com.example:type=SubCache");
    private static final ObjectName OBJECTNAME_CACHEMX =
            ObjectName.valueOf("com.example:type=CacheMX");
    private static final ObjectName OBJECTNAME_SUBCACHEMX =
            ObjectName.valueOf("com.example:type=SubCacheMX");
    private static final ObjectName OBJECTNAME_DYNACACHE =
            ObjectName.valueOf("com.example:type=DynaCache");
    private static final ObjectName OBJECTNAME_STDCACHE =
            ObjectName.valueOf("com.example:type=StdCache");
    private static final ObjectName OBJECTNAME_STDCACHEMX =
            ObjectName.valueOf("com.example:type=StdCacheMX");
    private static final ObjectName OBJECTNAME_MULTI_1 =
            ObjectName.valueOf("com.example:" +
            "type=MultiStdCache,name=" + NAME1);
    private static final ObjectName OBJECTNAME_MULTI_2 =
            ObjectName.valueOf("com.example:" +
            "type=MultiStdCache,name=" + NAME2);
    private static final ObjectName OBJECTNAME_MONO =
            ObjectName.valueOf("com.example:" + TYPE_KEY + "=" +
            TYPE_VALUE);
    private static final ObjectName OBJECTNAME_QUOTED =
            ObjectName.valueOf("com.example:type=Quotted," +
            "name="+ObjectName.quote(INVALID_NAME));
    private static final ObjectName OBJECTNAME_WRAPPED_RESOURCE =
            ObjectName.valueOf("com.example:type=MgtInterface,id=" + ID);
    private static final ObjectName OBJECTNAME_FULL =
            ObjectName.valueOf(FULL_NAME);

    private static void test(Class<?> mbean, Object[] params,
            String[] signature, ObjectName name, String template)
            throws Exception {
        mbs.createMBean(mbean.getName(), null, params, signature);
        test(name, template);
        List<Class<?>> parameters = new ArrayList<Class<?>>();
        for (String sig : signature) {
            parameters.add(Class.forName(sig));
        }
        Class<?> classes[] = new Class<?>[parameters.size()];
        Constructor ctr = mbean.getConstructor(parameters.toArray(classes));
        Object inst = ctr.newInstance(params);
        test(inst, name, template);
    }

    private static void test(Object obj, ObjectName name, String template)
            throws Exception {
        mbs.registerMBean(obj, null);
        test(name, template);
    }

    private static void test(ObjectName name, String template)
            throws Exception {
        if (!mbs.isRegistered(name)) {
            throw new Exception("Wrong " + name + " name");
        }
        if (template != null && !mbs.getMBeanInfo(name).getDescriptor().
                getFieldValue("objectNameTemplate").equals(template)) {
            throw new Exception("Invalid Derscriptor");
        }
        mbs.unregisterMBean(name);
    }

    public static void main(String[] args) throws Exception {
        test(Cache.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_CACHE,
                OBJECTNAME_CACHE.toString());

        test(CacheMX.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_CACHEMX,
                OBJECTNAME_CACHEMX.toString());

        test(SubCache.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_SUBCACHE,
                OBJECTNAME_SUBCACHE.toString());

        test(SubCacheMX.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_SUBCACHEMX,
                OBJECTNAME_SUBCACHEMX.toString());

        test(DynaCache.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_DYNACACHE,
                null);

        test(StdCacheMX.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_STDCACHEMX,
                OBJECTNAME_STDCACHEMX.toString());

        test(StdCache.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_STDCACHE,
                OBJECTNAME_STDCACHE.toString());
        String[] sig = {String.class.getName()};
        Object[] params = {NAME1};
        test(MultiStdCache.class, params, sig, OBJECTNAME_MULTI_1,
                NAME_TEMPLATE_MULTI);
        Object[] params2 = {NAME2};
        test(MultiStdCache.class, params2, sig, OBJECTNAME_MULTI_2,
                NAME_TEMPLATE_MULTI);

        test(MonoStdCache.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_MONO,
                NAME_TEMPLATE_MONO);

        test(Quoted.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_QUOTED,
                NAME_TEMPLATE_QUOTED);

        test(new StandardMBean(new WrappedResource(), MgtInterface.class),
                OBJECTNAME_WRAPPED_RESOURCE, NAME_TEMPLATE_WRAPPED);

        test(FullName.class, EMPTY_PARAMS, EMPTY_SIGNATURE, OBJECTNAME_FULL,
                NAME_TEMPLATE_FULL);
        try {
            test(Wrong.class, EMPTY_PARAMS, EMPTY_SIGNATURE, null, null);
            throw new Exception("No treceived expected Exception");
        } catch (NotCompliantMBeanException ncex) {
            if (!(ncex.getCause() instanceof AttributeNotFoundException)) {
                throw new Exception("Invalid initCause");
            }
        }
    }

    @MBean
    @ObjectNameTemplate("{Naming}")
    public static class FullName {

        @ManagedAttribute
        public String getNaming() {
            return FULL_NAME;
        }
    }

    @ObjectNameTemplate("com.example:type=MgtInterface,id={Id}")
    public interface MgtInterface {

        public int getId();
    }

    public static class WrappedResource implements MgtInterface {

        public int getId() {
            return ID;
        }
    }

    @MBean
    @ObjectNameTemplate("com.example:type=Cache")
    public static class Cache {
    }

    @ObjectNameTemplate("com.example:type=SubCache")
    public static class SubCache extends Cache {
    }

    @MXBean
    @ObjectNameTemplate("com.example:type=CacheMX")
    public static class CacheMX {
    }

    @ObjectNameTemplate("com.example:type=SubCacheMX")
    public static class SubCacheMX extends CacheMX {
    }

    @ObjectNameTemplate("com.example:type=StdCache")
    public interface StdCacheMBean {
    }

    public static class StdCache implements StdCacheMBean {
    }

    @ObjectNameTemplate("com.example:type=StdCacheMX")
    public interface StdCacheMXBean {
    }

    public static class StdCacheMX implements StdCacheMXBean {
    }

    public static class DynaCache implements DynamicMBean {

        public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public AttributeList getAttributes(String[] attributes) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public AttributeList setAttributes(AttributeList attributes) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public MBeanInfo getMBeanInfo() {
            ImmutableDescriptor d = new ImmutableDescriptor(JMX.OBJECT_NAME_TEMPLATE + "=com.example:type=DynaCache");

            return new MBeanInfo("DynaCache", "Description", null, null, null, null, d);
        }
    }

    @ObjectNameTemplate("com.example:type=MultiStdCache,name={Name}")
    public interface MultiStdCacheMXBean {

        public String getName();
    }

    public static class MultiStdCache implements MultiStdCacheMXBean {

        private String name;

        public MultiStdCache(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @ObjectNameTemplate("com.example:{Type}={TypeValue}")
    public interface MonoStdCacheMXBean {

        public String getTypeValue();

        public String getType();
    }

    public static class MonoStdCache implements MonoStdCacheMXBean {

        public String getTypeValue() {
            return TYPE_VALUE;
        }

        public String getType() {
            return TYPE_KEY;
        }
    }

    @ObjectNameTemplate("com.example:type=Quotted,name=\"{Name}\"")
    public interface QuottedMXBean {

        public String getName();
    }

    public static class Quoted implements QuottedMXBean {

        public String getName() {
            return INVALID_NAME;
        }
    }

    @ObjectNameTemplate("com.example:{Type}={TypeValue}, name={Name}")
    public interface WrongMXBean {

        public String getTypeValue();

        public String getType();
    }

    public static class Wrong implements WrongMXBean {

        public String getTypeValue() {
            return TYPE_VALUE;
        }

        public String getType() {
            return TYPE_KEY;
        }
    }
}
