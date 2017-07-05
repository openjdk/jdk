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
 * @bug 6669137
 * @summary Test the constructors of InstanceNotFoundExceptionTest.
 * @author Daniel Fuchs
 * @compile InstanceNotFoundExceptionTest.java
 * @run main InstanceNotFoundExceptionTest
 */

import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;

public class InstanceNotFoundExceptionTest {
    public static void main(String[] args) throws Exception {
        final InstanceNotFoundException x =
                new InstanceNotFoundException();
        System.out.println("InstanceNotFoundException(): "+x.getMessage());

        final String msg = "who is toto?";
        final InstanceNotFoundException x2 =
                new InstanceNotFoundException(msg);
        if (!msg.equals(x2.getMessage()))
            throw new Exception("Bad message: expected "+msg+
                    ", got "+x2.getMessage());
        System.out.println("InstanceNotFoundException(" +
                msg+"): "+x2.getMessage());

        final InstanceNotFoundException x3 =
                new InstanceNotFoundException((String)null);
        if (x3.getMessage() != null)
            throw new Exception("Bad message: expected "+null+
                    ", got "+x3.getMessage());
        System.out.println("InstanceNotFoundException((String)null): "+
                x3.getMessage());

        final ObjectName n = new ObjectName("who is toto?:type=msg");
        final InstanceNotFoundException x4 =
                new InstanceNotFoundException(n);
        if (!String.valueOf(n).equals(x4.getMessage()))
            throw new Exception("Bad message: expected "+n+
                    ", got "+x4.getMessage());
        System.out.println("InstanceNotFoundException(" +
                n+"): "+x4.getMessage());

        final InstanceNotFoundException x5 =
                new InstanceNotFoundException((ObjectName)null);
        if (!String.valueOf((ObjectName)null).equals(x5.getMessage()))
            throw new Exception("Bad message: expected " +
                    String.valueOf((ObjectName)null)+" got "+x5.getMessage());
        System.out.println("InstanceNotFoundException((ObjectName)null): "+
                x5.getMessage());
    }
}
