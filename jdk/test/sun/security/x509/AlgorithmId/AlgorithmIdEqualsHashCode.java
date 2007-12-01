/*
 * Copyright 1999-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @author Gary Ellison
 * @bug 4170635
 * @summary Verify equals()/hashCode() contract honored
 */

import java.io.*;

import sun.security.x509.*;

public class AlgorithmIdEqualsHashCode {

    public static void main(String[] args) throws Exception {

        AlgorithmId ai1 = AlgorithmId.get("DH");
        AlgorithmId ai2 = AlgorithmId.get("DH");
        AlgorithmId ai3 = AlgorithmId.get("DH");


        // supposedly transitivity is broken
        // System.out.println(ai1.equals(ai2));
        // System.out.println(ai2.equals(ai3));
        // System.out.println(ai1.equals(ai3));

        if ( (ai1.equals(ai2)) == (ai2.equals(ai3)) == (ai1.equals(ai3)))
            System.out.println("PASSED transitivity test");
        else
            throw new Exception("Failed equals transitivity() contract");

        if ( (ai1.equals(ai2)) == (ai1.hashCode()==ai2.hashCode()) )
            System.out.println("PASSED equals()/hashCode() test");
        else
            throw new Exception("Failed equals()/hashCode() contract");

    }
}
