/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.Repository;
import com.sun.org.apache.bcel.internal.classfile.ClassParser;
import com.sun.org.apache.bcel.internal.classfile.ConstantValue;
import com.sun.org.apache.bcel.internal.classfile.ExceptionTable;
import com.sun.org.apache.bcel.internal.classfile.Field;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.classfile.Utility;
import com.sun.org.apache.bcel.internal.generic.ArrayType;
import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.MethodGen;
import com.sun.org.apache.bcel.internal.generic.Type;

/**
 * This class takes a given JavaClass object and converts it to a Java program that creates that very class using BCEL.
 * This gives new users of BCEL a useful example showing how things are done with BCEL. It does not cover all features
 * of BCEL, but tries to mimic hand-written code as close as possible.
 *
 * @LastModified: Feb 2023
 */
public class BCELifier extends com.sun.org.apache.bcel.internal.classfile.EmptyVisitor {

    /**
     * Enum corresponding to flag source.
     */
    public enum FLAGS {
        UNKNOWN, CLASS, METHOD,
    }

    // The base package name for imports; assumes Const is at the top level
    // N.B we use the class so renames will be detected by the compiler/IDE
    private static final String BASE_PACKAGE = Const.class.getPackage().getName();
    private static final String CONSTANT_PREFIX = Const.class.getSimpleName() + ".";

    // Needs to be accessible from unit test code
    static JavaClass getJavaClass(final String name) throws ClassNotFoundException, IOException {
        JavaClass javaClass;
        if ((javaClass = Repository.lookupClass(name)) == null) {
            javaClass = new ClassParser(name).parse(); // May throw IOException
        }
        return javaClass;
    }

    /**
     * Default main method
     */
    public static void _main(final String[] argv) throws Exception {
        if (argv.length != 1) {
            System.out.println("Usage: BCELifier className");
            System.out.println("\tThe class must exist on the classpath");
            return;
        }
        final BCELifier bcelifier = new BCELifier(getJavaClass(argv[0]), System.out);
        bcelifier.start();
    }

    static String printArgumentTypes(final Type[] argTypes) {
        if (argTypes.length == 0) {
            return "Type.NO_ARGS";
        }
        final StringBuilder args = new StringBuilder();
        for (int i = 0; i < argTypes.length; i++) {
            args.append(printType(argTypes[i]));
            if (i < argTypes.length - 1) {
                args.append(", ");
            }
        }
        return "new Type[] { " + args.toString() + " }";
    }

    static String printFlags(final int flags) {
        return printFlags(flags, FLAGS.UNKNOWN);
    }

    /**
     * Return a string with the flag settings
     *
     * @param flags the flags field to interpret
     * @param location the item type
     * @return the formatted string
     * @since 6.0 made public
     */
    public static String printFlags(final int flags, final FLAGS location) {
        if (flags == 0) {
            return "0";
        }
        final StringBuilder buf = new StringBuilder();
        for (int i = 0, pow = 1; pow <= Const.MAX_ACC_FLAG_I; i++) {
            if ((flags & pow) != 0) {
                if (pow == Const.ACC_SYNCHRONIZED && location == FLAGS.CLASS) {
                    buf.append(CONSTANT_PREFIX).append("ACC_SUPER | ");
                } else if (pow == Const.ACC_VOLATILE && location == FLAGS.METHOD) {
                    buf.append(CONSTANT_PREFIX).append("ACC_BRIDGE | ");
                } else if (pow == Const.ACC_TRANSIENT && location == FLAGS.METHOD) {
                    buf.append(CONSTANT_PREFIX).append("ACC_VARARGS | ");
                } else if (i < Const.ACCESS_NAMES_LENGTH) {
                    buf.append(CONSTANT_PREFIX).append("ACC_").append(Const.getAccessName(i).toUpperCase(Locale.ENGLISH)).append(" | ");
                } else {
                    buf.append(String.format(CONSTANT_PREFIX + "ACC_BIT %x | ", pow));
                }
            }
            pow <<= 1;
        }
        final String str = buf.toString();
        return str.substring(0, str.length() - 3);
    }

    static String printType(final String signature) {
        final Type type = Type.getType(signature);
        final byte t = type.getType();
        if (t <= Const.T_VOID) {
            return "Type." + Const.getTypeName(t).toUpperCase(Locale.ENGLISH);
        }
        if (type.toString().equals("java.lang.String")) {
            return "Type.STRING";
        }
        if (type.toString().equals("java.lang.Object")) {
            return "Type.OBJECT";
        }
        if (type.toString().equals("java.lang.StringBuffer")) {
            return "Type.STRINGBUFFER";
        }
        if (type instanceof ArrayType) {
            final ArrayType at = (ArrayType) type;
            return "new ArrayType(" + printType(at.getBasicType()) + ", " + at.getDimensions() + ")";
        }
        return "new ObjectType(\"" + Utility.signatureToString(signature, false) + "\")";
    }

    static String printType(final Type type) {
        return printType(type.getSignature());
    }

    private final JavaClass clazz;

    private final PrintWriter printWriter;

    private final ConstantPoolGen constantPoolGen;

    /**
     * Constructs a new instance.
     *
     * @param clazz Java class to "decompile".
     * @param out where to print the Java program in UTF-8.
     */
    public BCELifier(final JavaClass clazz, final OutputStream out) {
        this.clazz = clazz;
        this.printWriter = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), false);
        this.constantPoolGen = new ConstantPoolGen(this.clazz.getConstantPool());
    }

    private void printCreate() {
        printWriter.println("  public void create(OutputStream out) throws IOException {");
        final Field[] fields = clazz.getFields();
        if (fields.length > 0) {
            printWriter.println("    createFields();");
        }
        final Method[] methods = clazz.getMethods();
        for (int i = 0; i < methods.length; i++) {
            printWriter.println("    createMethod_" + i + "();");
        }
        printWriter.println("    _cg.getJavaClass().dump(out);");
        printWriter.println("  }");
        printWriter.println();
    }

    private void printMain() {
        final String className = clazz.getClassName();
        printWriter.println("  public static void main(String[] args) throws Exception {");
        printWriter.println("    " + className + "Creator creator = new " + className + "Creator();");
        printWriter.println("    creator.create(new FileOutputStream(\"" + className + ".class\"));");
        printWriter.println("  }");
    }

    /**
     * Start Java code generation
     */
    public void start() {
        visitJavaClass(clazz);
        printWriter.flush();
    }

    @Override
    public void visitField(final Field field) {
        printWriter.println();
        printWriter.println(
            "    field = new FieldGen(" + printFlags(field.getAccessFlags()) + ", " + printType(field.getSignature()) + ", \"" + field.getName() + "\", _cp);");
        final ConstantValue cv = field.getConstantValue();
        if (cv != null) {
            printWriter.print("    field.setInitValue(");
            if (field.getType() == Type.CHAR) {
                printWriter.print("(char)");
            }
            if (field.getType() == Type.SHORT) {
                printWriter.print("(short)");
            }
            if (field.getType() == Type.BYTE) {
                printWriter.print("(byte)");
            }
            printWriter.print(cv);
            if (field.getType() == Type.LONG) {
                printWriter.print("L");
            }
            if (field.getType() == Type.FLOAT) {
                printWriter.print("F");
            }
            if (field.getType() == Type.DOUBLE) {
                printWriter.print("D");
            }
            printWriter.println(");");
        }
        printWriter.println("    _cg.addField(field.getField());");
    }

    @Override
    public void visitJavaClass(final JavaClass clazz) {
        String className = clazz.getClassName();
        final String superName = clazz.getSuperclassName();
        final String packageName = clazz.getPackageName();
        final String inter = Utility.printArray(clazz.getInterfaceNames(), false, true);
        if (packageName != null && !packageName.trim().isEmpty()) {
            className = className.substring(packageName.length() + 1);
            printWriter.println("package " + packageName + ";");
            printWriter.println();
        }
        printWriter.println("import " + BASE_PACKAGE + ".generic.*;");
        printWriter.println("import " + BASE_PACKAGE + ".classfile.*;");
        printWriter.println("import " + BASE_PACKAGE + ".*;");
        printWriter.println("import java.io.*;");
        printWriter.println();
        printWriter.println("public class " + className + "Creator {");
        printWriter.println("  private InstructionFactory _factory;");
        printWriter.println("  private ConstantPoolGen    _cp;");
        printWriter.println("  private ClassGen           _cg;");
        printWriter.println();
        printWriter.println("  public " + className + "Creator() {");
        printWriter.println("    _cg = new ClassGen(\"" + (packageName.isEmpty() ? className : packageName + "." + className) + "\", \"" + superName
            + "\", " + "\"" + clazz.getSourceFileName() + "\", " + printFlags(clazz.getAccessFlags(), FLAGS.CLASS) + ", " + "new String[] { " + inter + " });");
        printWriter.println("    _cg.setMajor(" + clazz.getMajor() + ");");
        printWriter.println("    _cg.setMinor(" + clazz.getMinor() + ");");
        printWriter.println();
        printWriter.println("    _cp = _cg.getConstantPool();");
        printWriter.println("    _factory = new InstructionFactory(_cg, _cp);");
        printWriter.println("  }");
        printWriter.println();
        printCreate();
        final Field[] fields = clazz.getFields();
        if (fields.length > 0) {
            printWriter.println("  private void createFields() {");
            printWriter.println("    FieldGen field;");
            for (final Field field : fields) {
                field.accept(this);
            }
            printWriter.println("  }");
            printWriter.println();
        }
        final Method[] methods = clazz.getMethods();
        for (int i = 0; i < methods.length; i++) {
            printWriter.println("  private void createMethod_" + i + "() {");
            methods[i].accept(this);
            printWriter.println("  }");
            printWriter.println();
        }
        printMain();
        printWriter.println("}");
    }

    @Override
    public void visitMethod(final Method method) {
        final MethodGen mg = new MethodGen(method, clazz.getClassName(), constantPoolGen);
        printWriter.println("    InstructionList il = new InstructionList();");
        printWriter.println("    MethodGen method = new MethodGen(" + printFlags(method.getAccessFlags(), FLAGS.METHOD) + ", " + printType(mg.getReturnType())
            + ", " + printArgumentTypes(mg.getArgumentTypes()) + ", " + "new String[] { " + Utility.printArray(mg.getArgumentNames(), false, true) + " }, \""
            + method.getName() + "\", \"" + clazz.getClassName() + "\", il, _cp);");
        final ExceptionTable exceptionTable = method.getExceptionTable();
        if (exceptionTable != null) {
            final String[] exceptionNames = exceptionTable.getExceptionNames();
            for (final String exceptionName : exceptionNames) {
                printWriter.print("    method.addException(\"");
                printWriter.print(exceptionName);
                printWriter.println("\");");
            }
        }
        printWriter.println();
        final BCELFactory factory = new BCELFactory(mg, printWriter);
        factory.start();
        printWriter.println("    method.setMaxStack();");
        printWriter.println("    method.setMaxLocals();");
        printWriter.println("    _cg.addMethod(method.getMethod());");
        printWriter.println("    il.dispose();");
    }
}
