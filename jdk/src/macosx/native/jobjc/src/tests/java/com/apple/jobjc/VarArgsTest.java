/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.apple.jobjc;

import com.apple.jobjc.foundation.FoundationFramework;
import com.apple.jobjc.foundation.NSDictionary;
import com.apple.jobjc.foundation.NSString;

public class VarArgsTest extends PooledTestCase {
    FoundationFramework FND = JObjC.getInstance().Foundation();

    public void testNSString_initWithFormat(){
        String expected = "1 + 0.2 = 1.2 abracadabra";
        NSString format = Utils.get().strings().nsString("%d + %.1f = %.1f %@");

        NSString abra = Utils.get().strings().nsString("abracadabra");

        NSString nstr = ((NSString)FND.NSString().alloc()).initWithFormat(format, 1, 0.2, 1.2, abra);
        String actual = Utils.get().strings().javaString(nstr);

        assertEquals(expected, actual);
    }

    public void testNSDictionary(){
        NSString v1 = Utils.get().strings().nsString("value1");
        NSString v2 = Utils.get().strings().nsString("value2");
        NSString k1 = Utils.get().strings().nsString("key1");
        NSString k2 = Utils.get().strings().nsString("key2");

        NSDictionary dict = ((NSDictionary)FND.NSDictionary().alloc()).initWithObjectsAndKeys(v1, k1, v2, k2, null);

        NSString nsdescr = dict.description();
        String jdescr = Utils.get().strings().javaString(nsdescr);

        assertEquals("{\n    key1 = value1;\n    key2 = value2;\n}", jdescr);
    }

    public static void main(String[] args){
        junit.textui.TestRunner.run(VarArgsTest.class);
    }
}
