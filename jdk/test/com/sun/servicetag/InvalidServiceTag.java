/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * @bug     6622366
 * @summary Basic Test for ServiceTag.newServiceTag() to test invalid fields.
 * @author  Mandy Chung
 *
 * @run build InvalidServiceTag
 * @run main InvalidServiceTag
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class InvalidServiceTag {
    private final static int MAX_CONTAINER_LEN = 64 - 1;
    public static void main(String[] argv) throws Exception {
        // all fields valid
        ServiceTag st1 = ServiceTag.newInstance("product name",
                                                "product version",
                                                "product urn",
                                                "product parent",
                                                "product parent urn",
                                                "product defined instance ID",
                                                "product vendor",
                                                "platform arch",
                                                "container",
                                                "source");
        // empty optional field
        ServiceTag st2 = ServiceTag.newInstance("product name",
                                                "product version",
                                                "product urn",
                                                "product parent",
                                                "",
                                                "",
                                                "product vendor",
                                                "platform arch",
                                                "container",
                                                "source");
        // Invalid - empty required field
        setInvalidContainer("");
        // Invalid - required field exceeds max length.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= MAX_CONTAINER_LEN; i++) {
            sb.append('x');
        }
        setInvalidContainer(sb.toString());
        System.out.println("Test passed.");
    }
    private static void setInvalidContainer(String container) {
        boolean exceptionThrown = false;
        try {
            ServiceTag st2 = ServiceTag.newInstance("product name",
                                                    "product version",
                                                    "product urn",
                                                    "product parent",
                                                    "product parent urn",
                                                    "product defined instance ID",
                                                    "product vendor",
                                                    "platform arch",
                                                    container,
                                                    "source");
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new RuntimeException("IllegalArgumentException not thrown");
        }
    }
}
