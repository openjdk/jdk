/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.parsers;

import java.io.FileOutputStream;
import java.util.ArrayList;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.sun.org.apache.bcel.internal.classfile.ClassParser;
import com.sun.org.apache.bcel.internal.classfile.ConstantClass;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.ConstantUtf8;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.generic.ClassGen;
import com.sun.org.apache.bcel.internal.generic.MethodGen;

/*
 * @bug 8003147
 * @summary Test port fix for BCEL bug 39695.
 */
public class Bug8003147Test {

    @Test
    public void test() throws Exception {
        String classfile = getClass().getResource("Bug8003147Test.class").getPath();
        JavaClass jc = new ClassParser(classfile).parse();
        // rename class
        ConstantPool cp = jc.getConstantPool();
        int cpIndex = ((ConstantClass) cp.getConstant(jc.getClassNameIndex())).getNameIndex();
        cp.setConstant(cpIndex, new ConstantUtf8("javax/xml/parsers/Bug8003147TestPrime"));
        ClassGen gen = new ClassGen(jc);
        Method[] methods = jc.getMethods();
        int index;
        for (index = 0; index < methods.length; index++) {
            if (methods[index].getName().equals("doSomething")) {
                break;
            }
        }
        Method m = methods[index];
        MethodGen mg = new MethodGen(m, gen.getClassName(), gen.getConstantPool());
        gen.replaceMethod(m, mg.getMethod());
        String path = classfile.replace("Bug8003147Test", "Bug8003147TestPrime");
        gen.getJavaClass().dump(new FileOutputStream(path));

        try {
            Class.forName("javax.xml.parsers.Bug8003147TestPrime");
        } catch (ClassFormatError cfe) {
            cfe.printStackTrace();
            Assert.fail("modified version of class does not pass verification");
        }
    }

    public void doSomething(double d, ArrayList<Integer> list) {
    }
}
