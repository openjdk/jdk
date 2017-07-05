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
 * @bug 6250014
 * @summary Test that Exceptions are added to the MbeanInfo
 * @author Jean-Francois Denise
 * @run main/othervm ExceptionsDescriptorTest
 */
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;
import javax.management.Descriptor;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ObjectName;

public class ExceptionsDescriptorTest {

    private static final ObjectName OBJECT_NAME = ObjectName.valueOf(":type=Foo");
    final static String EXCEPTION_NAME = Exception.class.getName();
    final static String ILLEGAL_ARGUMENT_EXCEPTION_NAME =
            IllegalArgumentException.class.getName();
    final static Set<String> ONE_EXCEPTION = new HashSet<String>();
    final static Set<String> TWO_EXCEPTION = new HashSet<String>();
    static {
        ONE_EXCEPTION.add(EXCEPTION_NAME);
        TWO_EXCEPTION.add(EXCEPTION_NAME);
        TWO_EXCEPTION.add(ILLEGAL_ARGUMENT_EXCEPTION_NAME);
    }
    public interface TestMBean {

        public void doIt();

        public void doIt(String str) throws Exception;

        public void doIt(String str, boolean b) throws Exception,
                IllegalArgumentException;

        public String getThat();

        public void setThat(String that);

        public String getThe() throws Exception;

        public void setThe(String the);

        public String getThese();

        public void setThese(String the) throws Exception;

        public String getIt() throws Exception;

        public void setIt(String str) throws Exception;

        public String getThis() throws Exception, IllegalArgumentException;

        public void setThose(String str) throws Exception,
                IllegalArgumentException;
    }

    public static class Test implements TestMBean {

        public Test() {
        }

        public Test(int i) throws Exception {
        }

        public Test(int i, int j) throws Exception, IllegalArgumentException {
        }

        public void doIt() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void doIt(String str) throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void doIt(String str, boolean b) throws Exception, IllegalArgumentException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getThat() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setThat(String that) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getThe() throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setThe(String the) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getThese() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setThese(String the) throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getIt() throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setIt(String str) throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getThis() throws Exception, IllegalArgumentException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setThose(String str) throws Exception, IllegalArgumentException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static void check(Descriptor d,
            Set<String> exceptionsExpectedValue,
            boolean exceptionsExpected,
            Set<String> setExceptionsExpectedValue,
            boolean setExceptionsExpected) throws Exception {
        String[] exceptionsValues = (String[]) d.getFieldValue(JMX.EXCEPTIONS_FIELD);
        String[] setExceptionsValues = (String[]) d.getFieldValue(JMX.SET_EXCEPTIONS_FIELD);

        if (exceptionsExpected && exceptionsValues == null) {
            throw new Exception("exceptions is expected but null value");
        }
        if (!exceptionsExpected && exceptionsValues != null) {
            throw new Exception("exceptions is not expected but non null value");
        }
        if (setExceptionsExpected && setExceptionsValues == null) {
            throw new Exception("setExceptions is expected but null value");
        }
        if (!setExceptionsExpected && setExceptionsValues != null) {
            throw new Exception("setExceptions is not expected but non null value");
        }

        if (exceptionsExpected) {
            checkValues(exceptionsExpectedValue, exceptionsValues);
        }
        if (setExceptionsExpected) {
            checkValues(setExceptionsExpectedValue, setExceptionsValues);
        }
    }

    private static void checkValues(Set<String> expectedValuesSet,
            String[] realValues) throws Exception {

        Set<String> realValuesSet = new HashSet<String>();
        for (String ex : realValues) {
            realValuesSet.add(ex);
        }
        if (!realValuesSet.equals(expectedValuesSet)) {
            throw new Exception("Invalid content for exceptions. Was expecting " +
                    expectedValuesSet + ". Found " + realValuesSet);
        }
    }

    public static void main(String[] args) throws Exception {
        Test t = new Test();
        ManagementFactory.getPlatformMBeanServer().registerMBean(t, OBJECT_NAME);
        MBeanInfo info = ManagementFactory.getPlatformMBeanServer().
                getMBeanInfo(OBJECT_NAME);
        //Constructors
        for (MBeanConstructorInfo ctr : info.getConstructors()) {
            if (ctr.getSignature().length == 0) {
                check(ctr.getDescriptor(), null, false, null, false);
            }
            if (ctr.getSignature().length == 1) {
                check(ctr.getDescriptor(), ONE_EXCEPTION, true, null, false);
            }
            if (ctr.getSignature().length == 2) {
                check(ctr.getDescriptor(),TWO_EXCEPTION,true, null, false);
            }
        }
        //Attributes
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            if (attr.getName().equals("That")) {
                check(attr.getDescriptor(), null, false, null, false);
            }
            if (attr.getName().equals("The")) {
                check(attr.getDescriptor(), ONE_EXCEPTION,true,null, false);
            }
            if (attr.getName().equals("These")) {
                check(attr.getDescriptor(), null, false, ONE_EXCEPTION,true);
            }
            if (attr.getName().equals("It")) {
                check(attr.getDescriptor(), ONE_EXCEPTION,true,ONE_EXCEPTION,
                        true);
            }
            if (attr.getName().equals("This")) {
                check(attr.getDescriptor(), TWO_EXCEPTION,true,null,false);
            }
            if (attr.getName().equals("Those")) {
                check(attr.getDescriptor(), null,false,TWO_EXCEPTION,true);
            }
        }
        //Operations
        for (MBeanOperationInfo oper : info.getOperations()) {
            if (oper.getSignature().length == 0) {
                check(oper.getDescriptor(), null, false, null, false);
            }
            if (oper.getSignature().length == 1) {
                check(oper.getDescriptor(), ONE_EXCEPTION, true, null, false);
            }
            if (oper.getSignature().length == 2) {
                check(oper.getDescriptor(), TWO_EXCEPTION,true, null, false);
            }
        }
        System.out.println("Test passed");
    }
}
