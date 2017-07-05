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
 * @bug 6450834
 * @summary Forward MBeanRegistration calls
 * @author JF Denise
 * @run main RegistrationTest
 */

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import javax.management.*;

public class RegistrationTest {
    static boolean preRegisterCalled;
    static boolean postRegisterCalled;
    static boolean preDeregisterCalled;
    static boolean postDeregisterCalled;

    static void checkResult(boolean expected) throws Exception {
        if((preRegisterCalled != expected ||
            postRegisterCalled != expected ||
            preDeregisterCalled != expected ||
            postDeregisterCalled != expected))
            throw new Exception("Mismatch preRegisterCalled = "
                    + preRegisterCalled + ", postRegisterCalled = "
                    + postRegisterCalled + ", preDeregisterCalled = "
                    + preDeregisterCalled + ", postDeregisterCalled = "
                    + postDeregisterCalled);
    }
    static class Wrapped implements MBeanRegistration,Serializable {

        public ObjectName preRegister(MBeanServer server, ObjectName name)
                throws Exception {
            preRegisterCalled = true;
            return name;
        }

        public void postRegister(Boolean registrationDone) {
            postRegisterCalled = true;
        }

        public void preDeregister() throws Exception {
            preDeregisterCalled = true;
        }

        public void postDeregister() {
            postDeregisterCalled = true;
        }

    }

    public static void main(String[] args) throws Exception {
       StandardMBean std = new StandardMBean(new Wrapped(),
               Serializable.class);
       ObjectName name = ObjectName.valueOf(":type=Test");
       ManagementFactory.getPlatformMBeanServer().registerMBean(std,name);
       ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
       checkResult(false);
       StandardMBean.Options opt = new StandardMBean.Options();
       opt.setMBeanRegistrationForwarded(true);
       std = new StandardMBean(new Wrapped(),
               Serializable.class, opt );
       ManagementFactory.getPlatformMBeanServer().registerMBean(std,name);
       ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
       checkResult(true);
       System.out.println("Test OK");
    }
}
