/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6501362
 * @summary DescriptorSupport(String) could recognize "name=value" as well as XML format
 * @author Jean-Francois Denise
 * @run clean DescriptorConstructorTest
 * @run build DescriptorConstructorTest
 * @run main DescriptorConstructorTest
 */

import javax.management.modelmbean.DescriptorSupport;

public class DescriptorConstructorTest {
    public static void main(String[] args) throws Exception {
        DescriptorSupport d1 = new DescriptorSupport("MyName1=MyValue1");
        if(!d1.getFieldValue("MyName1").equals("MyValue1"))
            throw new Exception("Invalid parsing");
        DescriptorSupport d2 = new DescriptorSupport("<Descriptor>" +
                "<field name=\"MyName2\" value=\"MyValue2\"></field></Descriptor>");
        if(!d2.getFieldValue("MyName2").equals("MyValue2"))
            throw new Exception("Invalid parsing");
    }
}
