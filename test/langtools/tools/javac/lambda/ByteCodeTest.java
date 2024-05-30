/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug     8011738
 * @author  sogoel
 * @summary Code translation test for Lambda expressions, method references
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 * @run main ByteCodeTest
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.constantpool.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandleInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ByteCodeTest {

    static boolean IS_DEBUG = false;
    public static void main(String[] args) {
        File classFile = null;
        int err = 0;
        boolean verifyResult = false;
        for(TestCases tc : TestCases.values()) {
            classFile = getCompiledFile(tc.name(), tc.srcCode);
            if(classFile == null) { // either testFile or classFile was not created
               err++;
            } else {
                verifyResult = verifyClassFileAttributes(classFile, tc);
                if(!verifyResult)
                    System.out.println("Bootstrap class file attributes did not match for " + tc.name());
            }
        }
        if(err > 0)
            throw new RuntimeException("Found " + err + " found");
        else
            System.out.println("Test passed");
    }

    private static boolean verifyClassFileAttributes(File classFile, TestCases tc) {
        ClassModel c = null;
        try {
            c = ClassFile.of().parse(classFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert c != null;
        ConstantPoolVisitor cpv = new ConstantPoolVisitor(c, c.constantPool().size());
        Map<Integer, String> hm = cpv.getBSMMap();

        List<String> expectedValList = tc.getExpectedArgValues();
        expectedValList.add(tc.bsmSpecifier.specifier);
        if(!(hm.values().containsAll(new HashSet<String>(expectedValList)))) {
            System.out.println("Values do not match");
            return false;
        }
        return true;
    }

    private static File getCompiledFile(String fname, String srcString) {
        File testFile = null, classFile = null;
        boolean isTestFileCreated = true;

        try {
            testFile = writeTestFile(fname+".java", srcString);
        } catch(IOException ioe) {
            isTestFileCreated = false;
            System.err.println("fail to write" + ioe);
        }

        if(isTestFileCreated) {
            try {
                classFile = compile(testFile);
            } catch (Error err) {
                System.err.println("fail compile. Source:\n" + srcString);
                throw err;
            }
        }
        return classFile;
    }

    static File writeTestFile(String fname, String source) throws IOException {
        File f = new File(fname);
          PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
          out.println(source);
          out.close();
          return f;
    }

    static File compile(File f) {
        int rc = com.sun.tools.javac.Main.compile(new String[] {
                "-g", f.getPath() });
        if (rc != 0)
                throw new Error("compilation failed. rc=" + rc);
            String path = f.getPath();
            return new File(path.substring(0, path.length() - 5) + ".class");
    }

    static void debugln(String str) {
        if(IS_DEBUG)
            System.out.println(str);
    }

    enum BSMSpecifier {
        SPECIFIER1("REF_invokeStatic java/lang/invoke/LambdaMetafactory metaFactory " +
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" +
                "Ljava/lang/invoke/CallSite;"),
        SPECIFIER2("REF_invokeStatic java/lang/invoke/LambdaMetafactory altMetaFactory " +
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                        "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;");

        String specifier;
        private BSMSpecifier(String specifier) {
            this.specifier = specifier;
        }
    }

    enum TestCases {
        // Single line lambda expression
        TC1("class TC1 {\n" +
            "    public static void main(String[] args) {\n" +
            "        Object o = (Runnable) () -> { System.out.println(\"hi\");};\n" +
            "    }\n"+
            "}", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface java/lang/Runnable run ()V");
                valList.add("REF_invokeStatic TC1 lambda$0 ()V");
                valList.add("()V");
                return valList;
            }
        },

        // Lambda expression in a for loop
        TC2("import java.util.*;\n" +
            "public class TC2 {\n" +
            "    void TC2_test() {\n" +
            "        List<String> list = new ArrayList<>();\n" +
            "        list.add(\"A\");\n" +
            "        list.add(\"B\");\n" +
            "        list.stream().forEach( s -> { System.out.println(s); } );\n" +
            "    }\n" +
            "    public static void main(String[] args) {\n" +
            "        new TC2().TC2_test();\n" +
            "    }\n" +
            "}", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface java/util/function/Consumer accept (Ljava/lang/Object;)V");
                valList.add("REF_invokeStatic TC2 lambda$0 (Ljava/lang/String;)V");
                valList.add("(Ljava/lang/String;)V");
                return valList;
            }
        },

        // Lambda initializer
        TC3("class TC3 {\n" +
            "    interface SAM {\n" +
            "        void m(int i);\n" +
            "    }\n" +
            "    SAM lambda_03 = (int pos) -> { };\n" +
            "}", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface TC3$SAM m (I)V");
                valList.add("REF_invokeStatic TC3 lambda$0 (I)V");
                valList.add("(I)V");
                return valList;
            }
        },

        // Array initializer
        TC4("class TC4 {\n" +
            "    interface Block<T> {\n" +
            "        void m(T t);\n" +
            "     }\n" +
            "     void test1() {\n" +
            "         Block<?>[] arr1 =  { t -> { }, t -> { } };\n" +
            "     }\n" +
            "}", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface TC4$Block m (Ljava/lang/Object;)V");
                valList.add("REF_invokeStatic TC4 lambda$0 (Ljava/lang/Object;)V");
                valList.add("(Ljava/lang/Object;)V");
                valList.add("REF_invokeStatic TC4 lambda$1 (Ljava/lang/Object;)V");
                return valList;
            }
        },

        //Lambda expression as a method arg
        TC5("class TC5 {\n"+
            "    interface MapFun<T,R> {  R m( T n); }\n" +
            "    void meth( MapFun<String,Integer> mf ) {\n" +
            "        assert( mf.m(\"four\") == 4);\n" +
            "    }\n"+
            "    void test(Integer i) {\n" +
            "        meth(s -> { Integer len = s.length(); return len; } );\n" +
            "    }\n"+
            "}", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface TC5$MapFun m (Ljava/lang/Object;)Ljava/lang/Object;");
                valList.add("REF_invokeStatic TC5 lambda$0 (Ljava/lang/String;)Ljava/lang/Integer;");
                valList.add("(Ljava/lang/String;)Ljava/lang/Integer;");
                return valList;
            }
        },

        //Inner class of Lambda expression
        TC6("class TC6 {\n" +
            "    interface MapFun<T, R> {  R m( T n); }\n" +
            "    MapFun<Class<?>,String> cs;\n" +
            "    void test() {\n" +
            "        cs = c -> {\n" +
            "                 class innerClass   {\n" +
            "                    Class<?> icc;\n" +
            "                    innerClass(Class<?> _c) { icc = _c; }\n" +
            "                    String getString() { return icc.toString(); }\n" +
            "                  }\n" +
            "             return new innerClass(c).getString();\n"+
            "             };\n" +
            "    }\n" +
            "}\n", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface TC6$MapFun m (Ljava/lang/Object;)Ljava/lang/Object;");
                valList.add("REF_invokeSpecial TC6 lambda$0 (Ljava/lang/Class;)Ljava/lang/String;");
                valList.add("(Ljava/lang/Class;)Ljava/lang/String;");
                return valList;
            }
        },

        // Method reference
        TC7("class TC7 {\n" +
            "    static interface SAM {\n" +
            "       void m(Integer i);\n" +
            "    }\n" +
            "    void m(Integer i) {}\n" +
            "       SAM s = this::m;\n" +
            "}\n", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface TC7$SAM m (Ljava/lang/Integer;)V");
                valList.add("REF_invokeVirtual TC7 m (Ljava/lang/Integer;)V");
                valList.add("(Ljava/lang/Integer;)V");
                return valList;
            }
        },

        // Constructor reference
        TC8("public class TC8 {\n" +
            "    static interface A {Fee<String> m();}\n" +
            "    static class Fee<T> {\n" +
            "        private T t;\n" +
            "        public Fee() {}\n" +
            "    }\n" +
            "    public static void main(String[] args) {\n" +
            "        A a = Fee<String>::new; \n" +
            "    }\n" +
            "}\n", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface TC8$A m ()LTC8$Fee;");
                valList.add("REF_newInvokeSpecial TC8$Fee <init> ()V");
                valList.add("()LTC8$Fee;");
                return valList;
            }
        },

        // Recursive lambda expression
        TC9("class TC9 {\n" +
            "    interface Recursive<T, R> { T apply(R n); };\n" +
            "    Recursive<Integer,Integer> factorial;\n" +
            "    void test(Integer j) {\n" +
            "        factorial = i -> { return i == 0 ? 1 : i * factorial.apply( i - 1 ); };\n" +
            "    }\n" +
            "}\n", BSMSpecifier.SPECIFIER1) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeInterface TC9$Recursive apply (Ljava/lang/Object;)Ljava/lang/Object;");
                valList.add("REF_invokeSpecial TC9 lambda$0 (Ljava/lang/Integer;)Ljava/lang/Integer;");
                valList.add("(Ljava/lang/Integer;)Ljava/lang/Integer;");
                return valList;
            }
        },

        //Serializable Lambda
        TC10("import java.io.Serializable;\n" +
              "class TC10 {\n" +
              "    interface Foo { int m(); }\n" +
              "    public static void main(String[] args) {\n" +
              "        Foo f1 = (Foo & Serializable)() -> 3;\n" +
              "    }\n" +
              "}\n", BSMSpecifier.SPECIFIER2) {

            @Override
            List<String> getExpectedArgValues() {
                List<String> valList = new ArrayList<>();
                valList.add("REF_invokeStatic java/lang/invoke/LambdaMetafactory altMetaFactory (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;");
                valList.add("REF_invokeInterface TC10$Foo m ()I");
                valList.add("REF_invokeStatic TC10 lambda$main$3231c38a$0 ()I");
                valList.add("()I");
                valList.add("1");
                return valList;
            }
        };

        String srcCode;
        BSMSpecifier bsmSpecifier;

        TestCases(String src, BSMSpecifier bsmSpecifier) {
            this.srcCode = src;
            // By default, all test cases will have bootstrap method specifier as Lambda.MetaFactory
            // For serializable lambda test cases, bootstrap method specifier changed to altMetaFactory
            this.bsmSpecifier = bsmSpecifier;
        }

        List<String> getExpectedArgValues() {
            return null;
        }

        void setSrcCode(String src) {
            srcCode = src;
        }
    }

    static class ConstantPoolVisitor {
        final List<String> slist;
        final ClassModel cf;
        final ConstantPool cfpool;
        final Map<Integer, String> bsmMap;


        public ConstantPoolVisitor(ClassModel cf, int size) {
            slist = new ArrayList<>(size);
            for (int i = 0 ; i < size; i++) {
                slist.add(null);
            }
            this.cf = cf;
            this.cfpool = cf.constantPool();
            bsmMap = readBSM();
        }

        public Map<Integer, String> getBSMMap() {
            return Collections.unmodifiableMap(bsmMap);
        }

        public String visit(PoolEntry c, int index) {
            return switch (c) {
                case ClassEntry entry -> visitClass(entry, index);
                case DoubleEntry entry -> visitDouble(entry, index);
                case FieldRefEntry entry -> visitFieldref(entry, index);
                case FloatEntry entry -> visitFloat(entry, index);
                case IntegerEntry entry -> visitInteger(entry, index);
                case InterfaceMethodRefEntry entry -> visitInterfaceMethodref(entry, index);
                case InvokeDynamicEntry entry -> visitInvokeDynamic(entry, index);
                case ConstantDynamicEntry entry -> visitDynamicConstant(entry, index);
                case LongEntry entry -> visitLong(entry, index);
                case NameAndTypeEntry entry -> visitNameAndType(entry, index);
                case MethodRefEntry entry -> visitMethodref(entry, index);
                case MethodHandleEntry entry -> visitMethodHandle(entry, index);
                case MethodTypeEntry entry -> visitMethodType(entry, index);
                case ModuleEntry entry -> visitModule(entry, index);
                case PackageEntry entry -> visitPackage(entry, index);
                case StringEntry entry -> visitString(entry, index);
                case Utf8Entry entry -> visitUtf8(entry, index);
                default -> throw new AssertionError();
            };
        }

        private Map<Integer, String> readBSM() {
            BootstrapMethodsAttribute bsmAttr = cf.findAttribute(Attributes.bootstrapMethods()).orElse(null);
            if (bsmAttr != null) {
                Map<Integer, String> out =
                        new HashMap<>(bsmAttr.bootstrapMethodsSize());
                for (BootstrapMethodEntry bsms : bsmAttr.bootstrapMethods()) {
                    int index = bsms.bootstrapMethod().index();
                    try {
                        String value = slist.get(index);
                        if (value == null) {
                            value = visit(cfpool.entryByIndex(index), index);
                            debugln("[SG]: index " + index);
                            debugln("[SG]: value " + value);
                            slist.set(index, value);
                            out.put(index, value);
                        }
                        for (LoadableConstantEntry ce : bsms.arguments()) {
                            int idx = ce.index();
                            value = slist.get(idx);
                            if (value == null) {
                                value = visit(cfpool.entryByIndex(idx), idx);
                                debugln("[SG]: idx " + idx);
                                debugln("[SG]: value " + value);
                                slist.set(idx, value);
                                out.put(idx, value);
                            }
                        }
                    } catch (ConstantPoolException ex) {
                        ex.printStackTrace();
                    }
                }
                return out;
            }
            return new HashMap<>(0);
        }

        public String visitClass(ClassEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.name().index()), c.index());
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitDouble(DoubleEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                value = Double.toString(c.doubleValue());
                slist.set(p, value);
            }
            return value;
        }

        public String visitFieldref(FieldRefEntry c, Integer p) {

        String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.owner().index()), c.owner().index());
                    value = value.concat(" " + visit(cfpool.entryByIndex(c.nameAndType().index()),
                                         c.nameAndType().index()));
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitFloat(FloatEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                value = Float.toString(c.floatValue());
                slist.set(p, value);
            }
            return value;
        }

        public String visitInteger(IntegerEntry cnstnt, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                value = Integer.toString(cnstnt.intValue());
                slist.set(p, value);
            }
            return value;
        }

        public String visitInterfaceMethodref(InterfaceMethodRefEntry c,
                                              Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.owner().index()), c.owner().index());
                    value = value.concat(" " +
                                         visit(cfpool.entryByIndex(c.nameAndType().index()),
                                         c.nameAndType().index()));
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitInvokeDynamic(InvokeDynamicEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = bsmMap.get(c.bootstrap().bsmIndex()) + " "
                            + visit(cfpool.entryByIndex(c.nameAndType().index()), c.nameAndType().index());
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitDynamicConstant(ConstantDynamicEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = bsmMap.get(c.bootstrap().bsmIndex()) + " "
                            + visit(cfpool.entryByIndex(c.nameAndType().index()), c.nameAndType().index());
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitLong(LongEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                value = Long.toString(c.longValue());
                slist.set(p, value);
            }
            return value;
        }

        public String visitNameAndType(NameAndTypeEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.name().index()), c.name().index());
                    value = value.concat(" " +
                            visit(cfpool.entryByIndex(c.type().index()), c.type().index()));
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitMethodref(MethodRefEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.owner().index()), c.owner().index());
                    value = value.concat(" " +
                                         visit(cfpool.entryByIndex(c.nameAndType().index()),
                                         c.nameAndType().index()));
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitMethodHandle(MethodHandleEntry c, Integer p) {

        String value = slist.get(p);
            if (value == null) {
                try {
                    value = MethodHandleInfo.referenceKindToString(c.kind());
                    value = value.concat(" "
                            + visit(cfpool.entryByIndex(c.reference().index()), c.reference().index()));
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitMethodType(MethodTypeEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.descriptor().index()), c.descriptor().index());
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitModule(ModuleEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.name().index()), c.name().index());
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitPackage(PackageEntry c, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                try {
                    value = visit(cfpool.entryByIndex(c.name().index()), c.name().index());
                    slist.set(p, value);
                } catch (ConstantPoolException ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        public String visitString(StringEntry c, Integer p) {

            try {
                String value = slist.get(p);
                if (value == null) {
                    value = c.stringValue();
                    slist.set(p, value);
                }
                return value;
            } catch (ConstantPoolException ex) {
                throw new RuntimeException("Fatal error", ex);
            }
        }

        public String  visitUtf8(Utf8Entry cnstnt, Integer p) {

            String value = slist.get(p);
            if (value == null) {
                value = cnstnt.stringValue();
                slist.set(p, value);
            }
            return value;
        }
    }
}

