/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import com.sun.management.VMOption;
import java.io.InvalidObjectException;
import java.util.Objects;
import javax.management.openmbean.OpenDataException;
import sun.management.MappedMXBeanType;

/*
 * @test
 * @bug     8042901
 * @summary Check that MappedMXBeanType.toOpenTypeData supports VMOption
 * @modules java.management/sun.management
 *          jdk.management/com.sun.management
 * @author  Shanliang Jiang
 */
public class VMOptionOpenDataTest {
    public static void main(String[] args) throws Exception {
        System.out.println("--- VMOptionOpenDataTest-main: Checking that "
                + "MappedMXBeanType.toOpenTypeData supports VMOption");
        Exception failed = null;
        try {
            VMOption vo = new VMOption("toto", "titi", true, VMOption.Origin.OTHER);
            System.out.println("--- Construct a VMOption object: \"" + vo + "\"");

            Object open = MappedMXBeanType.toOpenTypeData(vo, VMOption.class);
            System.out.println("--- Map it to an open type:  \"" + open +" \"");

            Object back = MappedMXBeanType.toJavaTypeData(open, VMOption.class);
            System.out.println("--- Map it back to java type:  \"" + back +" \"");

            if (back == null) {
                failed = new RuntimeException("Failed, mapping back got null.");
            } else if (!(back instanceof VMOption)) {
                failed = new RuntimeException("Failed, not mapped back to a VMOption: "
                        +back.getClass());
            } else {
                VMOption mapBack = (VMOption)back;
                if (!Objects.equals(vo.getName(), mapBack.getName()) ||
                        !Objects.equals(vo.getOrigin(), mapBack.getOrigin()) ||
                        !Objects.equals(vo.getValue(), mapBack.getValue()) ||
                        vo.isWriteable() != mapBack.isWriteable()) {
                    failed = new RuntimeException(
                            "Failed, failed to map back the original VMOtion.");
                }
            }
        } catch (OpenDataException | InvalidObjectException ode) {
            failed = ode;
        }
        if (failed == null) {
            System.out.println("--- PASSED!");
        } else {
            System.out.println("--- Failed: "+failed.getMessage());
            throw failed;
        }
    }
}
