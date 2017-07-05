/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

import com.sun.org.apache.bcel.internal.classfile.*;
import com.sun.org.apache.bcel.internal.generic.*;
import com.sun.org.apache.bcel.internal.Repository;
import com.sun.org.apache.bcel.internal.Constants;
import java.io.*;

/**
 * This class takes a given JavaClass object and converts it to a
 * Java program that creates that very class using BCEL. This
 * gives new users of BCEL a useful example showing how things
 * are done with BCEL. It does not cover all features of BCEL,
 * but tries to mimic hand-written code as close as possible.
 *
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class BCELifier extends com.sun.org.apache.bcel.internal.classfile.EmptyVisitor {
  private JavaClass         _clazz;
  private PrintWriter       _out;
  private ConstantPoolGen   _cp;

  /** @param clazz Java class to "decompile"
   * @param out where to output Java program
   */
  public BCELifier(JavaClass clazz, OutputStream out) {
    _clazz = clazz;
    _out = new PrintWriter(out);
    _cp = new ConstantPoolGen(_clazz.getConstantPool());
  }

  /** Start Java code generation
   */
  public void start() {
    visitJavaClass(_clazz);
    _out.flush();
  }

  public void visitJavaClass(JavaClass clazz) {
    String class_name   = clazz.getClassName();
    String super_name   = clazz.getSuperclassName();
    String package_name = clazz.getPackageName();
    String inter        = Utility.printArray(clazz.getInterfaceNames(),
                                             false, true);
    if(!"".equals(package_name)) {
      class_name = class_name.substring(package_name.length() + 1);
      _out.println("package " + package_name + ";\n");
     }

    _out.println("import com.sun.org.apache.bcel.internal.generic.*;");
    _out.println("import com.sun.org.apache.bcel.internal.classfile.*;");
    _out.println("import com.sun.org.apache.bcel.internal.*;");
    _out.println("import java.io.*;\n");

    _out.println("public class " + class_name + "Creator implements Constants {");
    _out.println("  private InstructionFactory _factory;");
    _out.println("  private ConstantPoolGen    _cp;");
    _out.println("  private ClassGen           _cg;\n");

    _out.println("  public " + class_name  + "Creator() {");
    _out.println("    _cg = new ClassGen(\"" +
                 (("".equals(package_name))? class_name :
                  package_name + "." + class_name) +
                 "\", \"" + super_name + "\", " +
                 "\"" + clazz.getSourceFileName() + "\", " +
                 printFlags(clazz.getAccessFlags(), true) + ", " +
                 "new String[] { " + inter + " });\n");

    _out.println("    _cp = _cg.getConstantPool();");
    _out.println("    _factory = new InstructionFactory(_cg, _cp);");
    _out.println("  }\n");

    printCreate();

    Field[] fields = clazz.getFields();

    if(fields.length > 0) {
      _out.println("  private void createFields() {");
      _out.println("    FieldGen field;");

      for(int i=0; i < fields.length; i++) {
        fields[i].accept(this);
      }

      _out.println("  }\n");
    }

    Method[] methods = clazz.getMethods();

    for(int i=0; i < methods.length; i++) {
      _out.println("  private void createMethod_" + i + "() {");

      methods[i].accept(this);
      _out.println("  }\n");
    }

    printMain();
    _out.println("}");
  }

  private void printCreate() {
    _out.println("  public void create(OutputStream out) throws IOException {");

    Field[] fields = _clazz.getFields();
    if(fields.length > 0) {
      _out.println("    createFields();");
    }

    Method[] methods = _clazz.getMethods();
    for(int i=0; i < methods.length; i++) {
      _out.println("    createMethod_" + i + "();");
    }

    _out.println("    _cg.getJavaClass().dump(out);");

    _out.println("  }\n");
  }

  private void printMain() {
    String   class_name   = _clazz.getClassName();

    _out.println("  public static void _main(String[] args) throws Exception {");
    _out.println("    " + class_name + "Creator creator = new " +
                 class_name + "Creator();");
    _out.println("    creator.create(new FileOutputStream(\"" + class_name +
                 ".class\"));");
    _out.println("  }");
  }

  public void visitField(Field field) {
    _out.println("\n    field = new FieldGen(" +
                 printFlags(field.getAccessFlags()) +
                 ", " + printType(field.getSignature()) + ", \"" +
                 field.getName() + "\", _cp);");

    ConstantValue cv = field.getConstantValue();

    if(cv != null) {
      String value = cv.toString();
      _out.println("    field.setInitValue(" + value + ")");
    }

    _out.println("    _cg.addField(field.getField());");
  }

  public void visitMethod(Method method) {
    MethodGen mg = new MethodGen(method, _clazz.getClassName(), _cp);

    Type   result_type = mg.getReturnType();
    Type[] arg_types   = mg.getArgumentTypes();

    _out.println("    InstructionList il = new InstructionList();");
    _out.println("    MethodGen method = new MethodGen(" +
                 printFlags(method.getAccessFlags()) +
                 ", " + printType(result_type) +
                 ", " + printArgumentTypes(arg_types) + ", " +
                 "new String[] { " +
                 Utility.printArray(mg.getArgumentNames(), false, true) +
                 " }, \"" + method.getName() + "\", \"" +
                 _clazz.getClassName() + "\", il, _cp);\n");

    BCELFactory factory = new BCELFactory(mg, _out);
    factory.start();

    _out.println("    method.setMaxStack();");
    _out.println("    method.setMaxLocals();");
    _out.println("    _cg.addMethod(method.getMethod());");
    _out.println("    il.dispose();");
  }

  static String printFlags(int flags) {
    return printFlags(flags, false);
  }

  static String printFlags(int flags, boolean for_class) {
    if(flags == 0)
      return "0";

    StringBuffer buf = new StringBuffer();
    for(int i=0, pow=1; i <= Constants.MAX_ACC_FLAG; i++) {
      if((flags & pow) != 0) {
        if((pow == Constants.ACC_SYNCHRONIZED) && for_class)
          buf.append("ACC_SUPER | ");
        else
          buf.append("ACC_" + Constants.ACCESS_NAMES[i].toUpperCase() + " | ");
      }

      pow <<= 1;
    }

    String str = buf.toString();
    return str.substring(0, str.length() - 3);
  }

  static String printArgumentTypes(Type[] arg_types) {
    if(arg_types.length == 0)
      return "Type.NO_ARGS";

    StringBuffer args = new StringBuffer();

    for(int i=0; i < arg_types.length; i++) {
      args.append(printType(arg_types[i]));

      if(i < arg_types.length - 1)
        args.append(", ");
    }

    return "new Type[] { " + args.toString() + " }";
  }

  static String printType(Type type) {
    return printType(type.getSignature());
  }

  static String printType(String signature) {
    Type type = Type.getType(signature);
    byte t    = type.getType();

    if(t <= Constants.T_VOID) {
      return "Type." + Constants.TYPE_NAMES[t].toUpperCase();
    } else if(type.toString().equals("java.lang.String")) {
      return "Type.STRING";
    } else if(type.toString().equals("java.lang.Object")) {
      return "Type.OBJECT";
    } else if(type.toString().equals("java.lang.StringBuffer")) {
      return "Type.STRINGBUFFER";
    } else if(type instanceof ArrayType) {
      ArrayType at = (ArrayType)type;

      return "new ArrayType(" + printType(at.getBasicType()) +
        ", " + at.getDimensions() + ")";
    } else {
      return "new ObjectType(\"" + Utility.signatureToString(signature, false) +
        "\")";
    }
  }

  /** Default _main method
   */
  public static void _main(String[] argv) throws Exception {
    JavaClass java_class;
    String    name = argv[0];

    if((java_class = Repository.lookupClass(name)) == null)
      java_class = new ClassParser(name).parse(); // May throw IOException

    BCELifier bcelifier = new BCELifier(java_class, System.out);
    bcelifier.start();
  }
}
