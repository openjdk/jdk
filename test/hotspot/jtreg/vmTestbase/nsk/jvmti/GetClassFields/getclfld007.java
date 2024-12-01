/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.GetClassFields;

import java.io.PrintStream;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;


public class getclfld007 {

    final static int JCK_STATUS_BASE = 95;

    static {
        try {
            System.loadLibrary("getclfld007");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load getclfld007 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    native static void check(Class cls, String[] expectedFields);
    native static int getRes();

    public static void main(String args[]) {
        args = nsk.share.jvmti.JVMTITest.commonInit(args);

        // produce JCK-like exit status.
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String args[], PrintStream out) {
        try {
            check(Class.forName(InnerClass1.class.getName()));
            check(Class.forName(InnerInterface.class.getName()));
            check(Class.forName(InnerClass2.class.getName()));
            check(Class.forName(OuterClass1.class.getName()));
            check(Class.forName(OuterClass2.class.getName()));
            check(Class.forName(OuterClass3.class.getName()));
            check(Class.forName(OuterInterface1.class.getName()));
            check(Class.forName(OuterInterface2.class.getName()));
            check(Class.forName(OuterClass4.class.getName()));
            check(Class.forName(OuterClass5.class.getName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return getRes();
    }


    static void check(Class cls) throws Exception {
        FieldExplorer explorer = new FieldExplorer(cls);
        List<String> fields = explorer.get();
        check(cls, fields.toArray(new String[0]));
    }

    // helper class to get list of the class fields
    // in the order they appear in the class file
    static class FieldExplorer extends ClassVisitor {
        private final Class cls;
        private List<String> fieldNameAndSig = new ArrayList<>();
        private FieldExplorer(Class cls) {
            super(Opcodes.ASM7);
            this.cls = cls;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            System.out.println("  field '" + name + "', type = " + descriptor);
            fieldNameAndSig.add(name);
            fieldNameAndSig.add(descriptor);
            return super.visitField(access, name, descriptor, signature, value);
        }

        private InputStream getClassBytes() throws Exception {
            String clsName = cls.getName();
            String clsPath = clsName.replace('.', '/') + ".class";
            return cls.getClassLoader().getResourceAsStream(clsPath);
        }

        // each field is represented by 2 Strings in the list: name and type descriptor
        public List<String> get() throws Exception {
            System.out.println("Class " + cls.getName());
            try (InputStream classBytes = getClassBytes()) {
                ClassReader classReader = new ClassReader(classBytes);
                classReader.accept(this, 0);
            }
            return fieldNameAndSig;
        }
    }

    static class InnerClass1 {
        String fld_1;
        void meth(String s) {
            fld_1 = s;
        }
    }

    static interface InnerInterface {
        int fld_n1 = 1;
        public void meth();
    }

    static class InnerClass2 implements InnerInterface {
        static int fld_n2 = 0;
        public void meth() {
            fld_n2++;
        }
    }
}

class OuterClass1 extends getclfld007 {
}

class OuterClass2 extends OuterClass1 {
    int fld_o2;
}

class OuterClass3 {
    int fld_o3;
    int meth() {
        return 3;
    }
}

interface OuterInterface1 {
    int fld_i1 = 0;
    int meth_i1();
}

interface OuterInterface2 extends OuterInterface1 {
    int fld_i2 = 0;
    int meth_i2();
}

abstract class OuterClass4 extends OuterClass3 implements OuterInterface2 {
    int fld_i2 = 2;
    public int meth_i2() {
        return 2;
    }
}

// class with multiple fields to verify correctness of the field order
class OuterClass5 extends OuterClass4 {
    int fld_i1 = 1;
    String fld_s1 = "str";
    int fld_i2 = 2;
    String fld_s2 = "str2";

    public int meth_i1() {
        return 1;
    }
}
