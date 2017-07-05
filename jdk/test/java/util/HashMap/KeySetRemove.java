/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * @test
 * @bug 4286765
 * @summary HashMap and TreeMap entrySet().remove(k) spuriously returned
 *          false if the Map previously mapped k to null.
 */

import java.util.*;

public class KeySetRemove {
    public static void main(String args[]) throws Exception {
        Map m[] = {new HashMap(), new TreeMap()};
        for (int i=0; i<m.length; i++) {
            m[i].put("bananas", null);
            if (!m[i].keySet().remove("bananas"))
                throw new Exception("Yes, we have no bananas: "+i);
        }
    }
}
