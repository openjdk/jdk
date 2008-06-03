/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javap;

import java.util.Collection;
import java.util.List;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Exceptions_attribute;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Signature;
import com.sun.tools.classfile.Signature_attribute;
import com.sun.tools.classfile.SourceFile_attribute;
import com.sun.tools.classfile.Type;

import static com.sun.tools.classfile.AccessFlags.*;

/*
 *  The main javap class to write the contents of a class file as text.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ClassWriter extends BasicWriter {
    static ClassWriter instance(Context context) {
        ClassWriter instance = context.get(ClassWriter.class);
        if (instance == null)
            instance = new ClassWriter(context);
        return instance;
    }

    protected ClassWriter(Context context) {
        super(context);
        context.put(ClassWriter.class, this);
        options = Options.instance(context);
        attrWriter = AttributeWriter.instance(context);
        codeWriter = CodeWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
    }

    ClassFile getClassFile() {
        return classFile;
    }

    Method getMethod() {
        return method;
    }

    public void write(ClassFile cf) {
        classFile = cf;
        constant_pool = classFile.constant_pool;

        Attribute sfa = cf.getAttribute(Attribute.SourceFile);
        if (sfa instanceof SourceFile_attribute) {
            println("Compiled from \"" + getSourceFile((SourceFile_attribute) sfa) + "\"");
        }

        String name = getJavaName(classFile);
        AccessFlags flags = cf.access_flags;

        writeModifiers(flags.getClassModifiers());

        if (classFile.isClass())
            print("class ");
        else if (classFile.isInterface())
            print("interface ");

        print(name);

        Signature_attribute sigAttr = getSignature(cf.attributes);
        if (sigAttr == null) {
            // use info from class file header
            if (classFile.isClass()) {
                if (classFile.super_class != 0 ) {
                    String sn = getJavaSuperclassName(cf);
                    if (!sn.equals("java.lang.Object") || options.compat) { // BUG XXXXXXXX
                        print(" extends ");
                        print(sn);
                    }
                }
            }
            for (int i = 0; i < classFile.interfaces.length; i++) {
                print(i == 0 ? (classFile.isClass() ? " implements " : " extends ") : ",");
                print(getJavaInterfaceName(classFile, i));
            }
        } else {
            try {
                Type t = sigAttr.getParsedSignature().getType(constant_pool);
                // The signature parser cannot disambiguate between a
                // FieldType and a ClassSignatureType that only contains a superclass type.
                if (t instanceof Type.ClassSigType)
                    print(t);
                else if (!t.isObject()) {
                    print(" extends ");
                    print(t);
                }
            } catch (ConstantPoolException e) {
                print(report(e));
            }
        }

        if (options.verbose) {
            println();
            attrWriter.write(cf, cf.attributes, constant_pool);
            println("  minor version: " + cf.minor_version);
            println("  major version: " + cf.major_version);
            if (!options.compat)
              writeList("  flags: ", flags.getClassFlags(), NEWLINE);
            constantWriter.writeConstantPool();
            println();
        } else {
            if (!options.compat)
                print(" ");
        }

        println("{");
        writeFields();
        writeMethods();
        println("}");
        println();
    }

    void writeFields() {
        for (Field f: classFile.fields) {
            writeField(f);
        }
    }

    void writeField(Field f) {
        if (!options.checkAccess(f.access_flags))
            return;

        if (!(options.showLineAndLocalVariableTables
                || options.showDisassembled
                || options.verbose
                || options.showInternalSignatures
                || options.showAllAttrs)) {
            print("    ");
        }

        AccessFlags flags = f.access_flags;
        writeModifiers(flags.getFieldModifiers());
        Signature_attribute sigAttr = getSignature(f.attributes);
        if (sigAttr == null)
            print(getFieldType(f.descriptor));
        else {
            try {
                Type t = sigAttr.getParsedSignature().getType(constant_pool);
                print(t);
            } catch (ConstantPoolException e) {
                // report error?
                // fall back on non-generic descriptor
                print(getFieldType(f.descriptor));
            }
        }
        print(" ");
        print(getFieldName(f));
        print(";");
        println();

        if (options.showInternalSignatures)
            println("  Signature: " + getValue(f.descriptor));

        if (options.verbose && !options.compat)
            writeList("  flags: ", flags.getFieldFlags(), NEWLINE);

        if (options.showAllAttrs) {
            for (Attribute attr: f.attributes)
                attrWriter.write(f, attr, constant_pool);
            println();
        }

        if (options.showDisassembled || options.showLineAndLocalVariableTables)
            println();
    }

    void writeMethods() {
        for (Method m: classFile.methods)
            writeMethod(m);
    }

    void writeMethod(Method m) {
        if (!options.checkAccess(m.access_flags))
            return;

        method = m;

        if (!(options.showLineAndLocalVariableTables
                || options.showDisassembled
                || options.verbose
                || options.showInternalSignatures
                || options.showAllAttrs)) {
            print("    ");
        }

        AccessFlags flags = m.access_flags;

        Descriptor d;
        Type.MethodType methodType;
        List<? extends Type> methodExceptions;

        Signature_attribute sigAttr = getSignature(m.attributes);
        if (sigAttr == null) {
            d = m.descriptor;
            methodType = null;
            methodExceptions = null;
        } else {
            Signature methodSig = sigAttr.getParsedSignature();
            d = methodSig;
            try {
                methodType = (Type.MethodType) methodSig.getType(constant_pool);
                methodExceptions = methodType.throwsTypes;
                if (methodExceptions != null && methodExceptions.size() == 0)
                    methodExceptions = null;
            } catch (ConstantPoolException e) {
                // report error?
                // fall back on standard descriptor
                methodType = null;
                methodExceptions = null;
            }
        }

        writeModifiers(flags.getMethodModifiers());
        if (methodType != null) {
            writeListIfNotEmpty("<", methodType.typeArgTypes, "> ");
        }
        if (getName(m).equals("<init>")) {
            print(getJavaName(classFile));
            print(getParameterTypes(d, flags));
        } else if (getName(m).equals("<clinit>")) {
            print("{}");
        } else {
            print(getReturnType(d));
            print(" ");
            print(getName(m));
            print(getParameterTypes(d, flags));
        }

        Attribute e_attr = m.attributes.get(Attribute.Exceptions);
        if (e_attr != null) { // if there are generic exceptions, there must be erased exceptions
            if (e_attr instanceof Exceptions_attribute) {
                Exceptions_attribute exceptions = (Exceptions_attribute) e_attr;
                if (options.compat) { // Bug XXXXXXX whitespace
                    if (!(options.showLineAndLocalVariableTables
                            || options.showDisassembled
                            || options.verbose
                            || options.showInternalSignatures
                            || options.showAllAttrs)) {
                        print("    ");
                    }
                    print("  ");
                }
                print(" throws ");
                if (methodExceptions != null) { // use generic list if available
                    writeList("", methodExceptions, "");
                } else {
                    for (int i = 0; i < exceptions.number_of_exceptions; i++) {
                        if (i > 0)
                            print(", ");
                        print(attrWriter.getJavaException(exceptions, i));
                    }
                }
            } else {
                report("Unexpected or invalid value for Exceptions attribute");
            }
        }

        print(";");
        println();

        if (options.showInternalSignatures)
            println("  Signature: " + getValue(m.descriptor));

        if (options.verbose && !options.compat)
            writeList("  flags: ", flags.getMethodFlags(), NEWLINE);

        Code_attribute code = null;
        Attribute c_attr = m.attributes.get(Attribute.Code);
        if (c_attr != null) {
            if (c_attr instanceof Code_attribute)
                code = (Code_attribute) c_attr;
            else
                report("Unexpected or invalid value for Code attribute");
        }

        if (options.showDisassembled && !options.showAllAttrs) {
            if (code != null) {
                println("  Code:");
                codeWriter.writeInstrs(code);
                codeWriter.writeExceptionTable(code);
            }
            println();
        }

        if (options.showLineAndLocalVariableTables) {
            if (code != null)
                attrWriter.write(code, code.attributes.get(Attribute.LineNumberTable), constant_pool);
            println();
            if (code != null)
                attrWriter.write(code, code.attributes.get(Attribute.LocalVariableTable), constant_pool);
            println();
            println();
        }

        if (options.showAllAttrs) {
            Attribute[] attrs = m.attributes.attrs;
            for (Attribute attr: attrs)
                attrWriter.write(m, attr, constant_pool);

//            // the following condition is to mimic old javap
//            if (!(attrs.length > 0 &&
//                    attrs[attrs.length - 1] instanceof Exceptions_attribute))
            println();
        }
    }

    void writeModifiers(Collection<String> items) {
        for (Object item: items) {
            print(item);
            print(" ");
        }
    }

    void writeList(String prefix, Collection<?> items, String suffix) {
        print(prefix);
        String sep = "";
        for (Object item: items) {
            print(sep);
            print(item);
            sep = ", ";
        }
        print(suffix);
    }

    void writeListIfNotEmpty(String prefix, List<?> items, String suffix) {
        if (items != null && items.size() > 0)
            writeList(prefix, items, suffix);
    }

    Signature_attribute getSignature(Attributes attributes) {
        if (options.compat) // javap does not recognize recent attributes
            return null;
        return (Signature_attribute) attributes.get(Attribute.Signature);
    }

    String adjustVarargs(AccessFlags flags, String params) {
        if (flags.is(ACC_VARARGS) && !options.compat) {
            int i = params.lastIndexOf("[]");
            if (i > 0)
                return params.substring(0, i) + "..." + params.substring(i+2);
        }

        return params;
    }

    String getJavaName(ClassFile cf) {
        try {
            return getJavaName(cf.getName());
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    String getJavaSuperclassName(ClassFile cf) {
        try {
            return getJavaName(cf.getSuperclassName());
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    String getJavaInterfaceName(ClassFile cf, int index) {
        try {
            return getJavaName(cf.getInterfaceName(index));
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    String getFieldType(Descriptor d) {
        try {
            return d.getFieldType(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        } catch (DescriptorException e) {
            return report(e);
        }
    }

    String getReturnType(Descriptor d) {
        try {
            return d.getReturnType(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        } catch (DescriptorException e) {
            return report(e);
        }
    }

    String getParameterTypes(Descriptor d, AccessFlags flags) {
        try {
            return adjustVarargs(flags, d.getParameterTypes(constant_pool));
        } catch (ConstantPoolException e) {
            return report(e);
        } catch (DescriptorException e) {
            return report(e);
        }
    }

    String getValue(Descriptor d) {
        try {
            return d.getValue(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    String getFieldName(Field f) {
        try {
            return f.getName(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    String getName(Method m) {
        try {
            return m.getName(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    static String getJavaName(String name) {
        return name.replace('/', '.');
    }

    String getSourceFile(SourceFile_attribute attr) {
        try {
            return attr.getSourceFile(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    private Options options;
    private AttributeWriter attrWriter;
    private CodeWriter codeWriter;
    private ConstantWriter constantWriter;
    private ClassFile classFile;
    private ConstantPool constant_pool;
    private Method method;
    private static final String NEWLINE = System.getProperty("line.separator", "\n");
}
