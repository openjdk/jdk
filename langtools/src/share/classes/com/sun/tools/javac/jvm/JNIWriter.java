/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.jvm;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.StringTokenizer;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import static com.sun.tools.javac.main.Option.*;

/** This class provides operations to write native header files for classes.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JNIWriter {
    protected static final Context.Key<JNIWriter> jniWriterKey =
        new Context.Key<JNIWriter>();

    /** Access to files. */
    private final JavaFileManager fileManager;

    JavacElements elements;
    JavacTypes types;

    /** The log to use for verbose output.
     */
    private final Log log;

    /** Switch: verbose output.
     */
    private boolean verbose;

    /** Switch: check all nested classes of top level class
     */
    private boolean checkAll;

    private Mangle mangler;

    private Context context;

    private Symtab syms;

    private String lineSep;

    private final boolean isWindows =
        System.getProperty("os.name").startsWith("Windows");

    /** Get the ClassWriter instance for this context. */
    public static JNIWriter instance(Context context) {
        JNIWriter instance = context.get(jniWriterKey);
        if (instance == null)
            instance = new JNIWriter(context);
        return instance;
    }

    /** Construct a class writer, given an options table.
     */
    private JNIWriter(Context context) {
        context.put(jniWriterKey, this);
        fileManager = context.get(JavaFileManager.class);
        log = Log.instance(context);

        Options options = Options.instance(context);
        verbose = options.isSet(VERBOSE);
        checkAll = options.isSet("javah:full");

        this.context = context; // for lazyInit()
        syms = Symtab.instance(context);

        lineSep = System.getProperty("line.separator");
    }

    private void lazyInit() {
        if (mangler == null) {
            elements = JavacElements.instance(context);
            types = JavacTypes.instance(context);
            mangler = new Mangle(elements, types);
        }
    }

    public boolean needsHeader(ClassSymbol c) {
        if (c.isLocal() || (c.flags() & Flags.SYNTHETIC) != 0)
            return false;

        if (checkAll)
            return needsHeader(c.outermostClass(), true);
        else
            return needsHeader(c, false);
    }

    private boolean needsHeader(ClassSymbol c, boolean checkNestedClasses) {
        if (c.isLocal() || (c.flags() & Flags.SYNTHETIC) != 0)
            return false;

        for (Scope.Entry i = c.members_field.elems; i != null; i = i.sibling) {
            if (i.sym.kind == Kinds.MTH && (i.sym.flags() & Flags.NATIVE) != 0)
                return true;
            for (Attribute.Compound a: i.sym.annotations.getDeclarationAttributes()) {
                if (a.type.tsym == syms.nativeHeaderType.tsym)
                    return true;
            }
        }
        if (checkNestedClasses) {
            for (Scope.Entry i = c.members_field.elems; i != null; i = i.sibling) {
                if ((i.sym.kind == Kinds.TYP) && needsHeader(((ClassSymbol) i.sym), true))
                    return true;
            }
        }
        return false;
    }

    /** Emit a class file for a given class.
     *  @param c      The class from which a class file is generated.
     */
    public FileObject write(ClassSymbol c)
        throws IOException
    {
        String className = c.flatName().toString();
        FileObject outFile
            = fileManager.getFileForOutput(StandardLocation.NATIVE_HEADER_OUTPUT,
                "", className.replaceAll("[.$]", "_") + ".h", null);
        Writer out = outFile.openWriter();
        try {
            write(out, c);
            if (verbose)
                log.printVerbose("wrote.file", outFile);
            out.close();
            out = null;
        } finally {
            if (out != null) {
                // if we are propogating an exception, delete the file
                out.close();
                outFile.delete();
                outFile = null;
            }
        }
        return outFile; // may be null if write failed
    }

    public void write(Writer out, ClassSymbol sym)
            throws IOException {
        lazyInit();
        try {
            String cname = mangler.mangle(sym.fullname, Mangle.Type.CLASS);
            println(out, fileTop());
            println(out, includes());
            println(out, guardBegin(cname));
            println(out, cppGuardBegin());

            writeStatics(out, sym);
            writeMethods(out, sym, cname);

            println(out, cppGuardEnd());
            println(out, guardEnd(cname));
        } catch (TypeSignature.SignatureException e) {
            throw new IOException(e);
        }
    }

    protected void writeStatics(Writer out, ClassSymbol sym) throws IOException {
        List<VariableElement> classfields = getAllFields(sym);

        for (VariableElement v: classfields) {
            if (!v.getModifiers().contains(Modifier.STATIC))
                continue;
            String s = null;
            s = defineForStatic(sym, v);
            if (s != null) {
                println(out, s);
            }
        }
    }

    /**
     * Including super class fields.
     */
    List<VariableElement> getAllFields(TypeElement subclazz) {
        List<VariableElement> fields = new ArrayList<VariableElement>();
        TypeElement cd = null;
        Stack<TypeElement> s = new Stack<TypeElement>();

        cd = subclazz;
        while (true) {
            s.push(cd);
            TypeElement c = (TypeElement) (types.asElement(cd.getSuperclass()));
            if (c == null)
                break;
            cd = c;
        }

        while (!s.empty()) {
            cd = s.pop();
            fields.addAll(ElementFilter.fieldsIn(cd.getEnclosedElements()));
        }

        return fields;
    }

    protected String defineForStatic(TypeElement c, VariableElement f) {
        CharSequence cnamedoc = c.getQualifiedName();
        CharSequence fnamedoc = f.getSimpleName();

        String cname = mangler.mangle(cnamedoc, Mangle.Type.CLASS);
        String fname = mangler.mangle(fnamedoc, Mangle.Type.FIELDSTUB);

        Assert.check(f.getModifiers().contains(Modifier.STATIC));

        if (f.getModifiers().contains(Modifier.FINAL)) {
            Object value = null;

            value = f.getConstantValue();

            if (value != null) { /* so it is a ConstantExpression */
                String constString = null;
                if ((value instanceof Integer)
                    || (value instanceof Byte)
                    || (value instanceof Short)) {
                    /* covers byte, short, int */
                    constString = value.toString() + "L";
                } else if (value instanceof Boolean) {
                    constString = ((Boolean) value) ? "1L" : "0L";
                } else if (value instanceof Character) {
                    Character ch = (Character) value;
                    constString = String.valueOf(((int) ch) & 0xffff) + "L";
                } else if (value instanceof Long) {
                    // Visual C++ supports the i64 suffix, not LL.
                    if (isWindows)
                        constString = value.toString() + "i64";
                    else
                        constString = value.toString() + "LL";
                } else if (value instanceof Float) {
                    /* bug for bug */
                    float fv = ((Float)value).floatValue();
                    if (Float.isInfinite(fv))
                        constString = ((fv < 0) ? "-" : "") + "Inff";
                    else
                        constString = value.toString() + "f";
                } else if (value instanceof Double) {
                    /* bug for bug */
                    double d = ((Double)value).doubleValue();
                    if (Double.isInfinite(d))
                        constString = ((d < 0) ? "-" : "") + "InfD";
                    else
                        constString = value.toString();
                }

                if (constString != null) {
                    StringBuilder s = new StringBuilder("#undef ");
                    s.append(cname); s.append("_"); s.append(fname); s.append(lineSep);
                    s.append("#define "); s.append(cname); s.append("_");
                    s.append(fname); s.append(" "); s.append(constString);
                    return s.toString();
                }

            }
        }

        return null;
    }


    protected void writeMethods(Writer out, ClassSymbol sym, String cname)
            throws IOException, TypeSignature.SignatureException {
        List<ExecutableElement> classmethods = ElementFilter.methodsIn(sym.getEnclosedElements());
        for (ExecutableElement md: classmethods) {
            if(md.getModifiers().contains(Modifier.NATIVE)){
                TypeMirror mtr = types.erasure(md.getReturnType());
                String sig = signature(md);
                TypeSignature newtypesig = new TypeSignature(elements);
                CharSequence methodName = md.getSimpleName();
                boolean longName = false;
                for (ExecutableElement md2: classmethods) {
                    if ((md2 != md)
                        && (methodName.equals(md2.getSimpleName()))
                        && (md2.getModifiers().contains(Modifier.NATIVE)))
                        longName = true;

                }
                println(out, "/*");
                println(out, " * Class:     " + cname);
                println(out, " * Method:    " +
                           mangler.mangle(methodName, Mangle.Type.FIELDSTUB));
                println(out, " * Signature: " + newtypesig.getTypeSignature(sig, mtr));
                println(out, " */");
                println(out, "JNIEXPORT " + jniType(mtr) +
                           " JNICALL " +
                           mangler.mangleMethod(md, sym,
                                               (longName) ?
                                               Mangle.Type.METHOD_JNI_LONG :
                                               Mangle.Type.METHOD_JNI_SHORT));
                print(out, "  (JNIEnv *, ");
                List<? extends VariableElement> paramargs = md.getParameters();
                List<TypeMirror> args = new ArrayList<TypeMirror>();
                for (VariableElement p: paramargs) {
                    args.add(types.erasure(p.asType()));
                }
                if (md.getModifiers().contains(Modifier.STATIC))
                    print(out, "jclass");
                else
                    print(out, "jobject");

                for (TypeMirror arg: args) {
                    print(out, ", ");
                    print(out, jniType(arg));
                }
                println(out, ");"
                        + lineSep);
            }
        }
    }

    // c.f. MethodDoc.signature
    String signature(ExecutableElement e) {
        StringBuilder sb = new StringBuilder("(");
        String sep = "";
        for (VariableElement p: e.getParameters()) {
            sb.append(sep);
            sb.append(types.erasure(p.asType()).toString());
            sep = ",";
        }
        sb.append(")");
        return sb.toString();
    }

    protected final String jniType(TypeMirror t) {
        TypeElement throwable = elements.getTypeElement("java.lang.Throwable");
        TypeElement jClass = elements.getTypeElement("java.lang.Class");
        TypeElement jString = elements.getTypeElement("java.lang.String");
        Element tclassDoc = types.asElement(t);


        switch (t.getKind()) {
            case ARRAY: {
                TypeMirror ct = ((ArrayType) t).getComponentType();
                switch (ct.getKind()) {
                    case BOOLEAN:  return "jbooleanArray";
                    case BYTE:     return "jbyteArray";
                    case CHAR:     return "jcharArray";
                    case SHORT:    return "jshortArray";
                    case INT:      return "jintArray";
                    case LONG:     return "jlongArray";
                    case FLOAT:    return "jfloatArray";
                    case DOUBLE:   return "jdoubleArray";
                    case ARRAY:
                    case DECLARED: return "jobjectArray";
                    default: throw new Error(ct.toString());
                }
            }

            case VOID:     return "void";
            case BOOLEAN:  return "jboolean";
            case BYTE:     return "jbyte";
            case CHAR:     return "jchar";
            case SHORT:    return "jshort";
            case INT:      return "jint";
            case LONG:     return "jlong";
            case FLOAT:    return "jfloat";
            case DOUBLE:   return "jdouble";

            case DECLARED: {
                if (tclassDoc.equals(jString))
                    return "jstring";
                else if (types.isAssignable(t, throwable.asType()))
                    return "jthrowable";
                else if (types.isAssignable(t, jClass.asType()))
                    return "jclass";
                else
                    return "jobject";
            }
        }

        Assert.check(false, "jni unknown type");
        return null; /* dead code. */
    }

    protected String fileTop() {
        return "/* DO NOT EDIT THIS FILE - it is machine generated */";
    }

    protected String includes() {
        return "#include <jni.h>";
    }

    /*
     * Deal with the C pre-processor.
     */
    protected String cppGuardBegin() {
        return "#ifdef __cplusplus" + lineSep
                + "extern \"C\" {" + lineSep
                + "#endif";
    }

    protected String cppGuardEnd() {
        return "#ifdef __cplusplus" + lineSep
                + "}" + lineSep
                + "#endif";
    }

    protected String guardBegin(String cname) {
        return "/* Header for class " + cname + " */" + lineSep
                + lineSep
                + "#ifndef _Included_" + cname + lineSep
                + "#define _Included_" + cname;
    }

    protected String guardEnd(String cname) {
        return "#endif";
    }

    protected void print(Writer out, String text) throws IOException {
        out.write(text);
    }

    protected void println(Writer out, String text) throws IOException {
        out.write(text);
        out.write(lineSep);
    }


    private static class Mangle {

        public static class Type {
            public static final int CLASS            = 1;
            public static final int FIELDSTUB        = 2;
            public static final int FIELD            = 3;
            public static final int JNI              = 4;
            public static final int SIGNATURE        = 5;
            public static final int METHOD_JDK_1     = 6;
            public static final int METHOD_JNI_SHORT = 7;
            public static final int METHOD_JNI_LONG  = 8;
        };

        private Elements elems;
        private Types types;

        Mangle(Elements elems, Types types) {
            this.elems = elems;
            this.types = types;
        }

        public final String mangle(CharSequence name, int mtype) {
            StringBuilder result = new StringBuilder(100);
            int length = name.length();

            for (int i = 0; i < length; i++) {
                char ch = name.charAt(i);
                if (isalnum(ch)) {
                    result.append(ch);
                } else if ((ch == '.') &&
                           mtype == Mangle.Type.CLASS) {
                    result.append('_');
                } else if (( ch == '$') &&
                           mtype == Mangle.Type.CLASS) {
                    result.append('_');
                    result.append('_');
                } else if (ch == '_' && mtype == Mangle.Type.FIELDSTUB) {
                    result.append('_');
                } else if (ch == '_' && mtype == Mangle.Type.CLASS) {
                    result.append('_');
                } else if (mtype == Mangle.Type.JNI) {
                    String esc = null;
                    if (ch == '_')
                        esc = "_1";
                    else if (ch == '.')
                        esc = "_";
                    else if (ch == ';')
                        esc = "_2";
                    else if (ch == '[')
                        esc = "_3";
                    if (esc != null) {
                        result.append(esc);
                    } else {
                        result.append(mangleChar(ch));
                    }
                } else if (mtype == Mangle.Type.SIGNATURE) {
                    if (isprint(ch)) {
                        result.append(ch);
                    } else {
                        result.append(mangleChar(ch));
                    }
                } else {
                    result.append(mangleChar(ch));
                }
            }

            return result.toString();
        }

        public String mangleMethod(ExecutableElement method, TypeElement clazz,
                                          int mtype) throws TypeSignature.SignatureException {
            StringBuilder result = new StringBuilder(100);
            result.append("Java_");

            if (mtype == Mangle.Type.METHOD_JDK_1) {
                result.append(mangle(clazz.getQualifiedName(), Mangle.Type.CLASS));
                result.append('_');
                result.append(mangle(method.getSimpleName(),
                                     Mangle.Type.FIELD));
                result.append("_stub");
                return result.toString();
            }

            /* JNI */
            result.append(mangle(getInnerQualifiedName(clazz), Mangle.Type.JNI));
            result.append('_');
            result.append(mangle(method.getSimpleName(),
                                 Mangle.Type.JNI));
            if (mtype == Mangle.Type.METHOD_JNI_LONG) {
                result.append("__");
                String typesig = signature(method);
                TypeSignature newTypeSig = new TypeSignature(elems);
                String sig = newTypeSig.getTypeSignature(typesig,  method.getReturnType());
                sig = sig.substring(1);
                sig = sig.substring(0, sig.lastIndexOf(')'));
                sig = sig.replace('/', '.');
                result.append(mangle(sig, Mangle.Type.JNI));
            }

            return result.toString();
        }
        //where
            private String getInnerQualifiedName(TypeElement clazz) {
                return elems.getBinaryName(clazz).toString();
            }

        public final String mangleChar(char ch) {
            String s = Integer.toHexString(ch);
            int nzeros = 5 - s.length();
            char[] result = new char[6];
            result[0] = '_';
            for (int i = 1; i <= nzeros; i++)
                result[i] = '0';
            for (int i = nzeros+1, j = 0; i < 6; i++, j++)
                result[i] = s.charAt(j);
            return new String(result);
        }

        // Warning: duplicated in Gen
        private String signature(ExecutableElement e) {
            StringBuilder sb = new StringBuilder();
            String sep = "(";
            for (VariableElement p: e.getParameters()) {
                sb.append(sep);
                sb.append(types.erasure(p.asType()).toString());
                sep = ",";
            }
            sb.append(")");
            return sb.toString();
        }

        /* Warning: Intentional ASCII operation. */
        private static boolean isalnum(char ch) {
            return ch <= 0x7f && /* quick test */
                ((ch >= 'A' && ch <= 'Z') ||
                 (ch >= 'a' && ch <= 'z') ||
                 (ch >= '0' && ch <= '9'));
        }

        /* Warning: Intentional ASCII operation. */
        private static boolean isprint(char ch) {
            return ch >= 32 && ch <= 126;
        }
    }

    private static class TypeSignature {
        static class SignatureException extends Exception {
            private static final long serialVersionUID = 1L;
            SignatureException(String reason) {
                super(reason);
            }
        }

        Elements elems;

        /* Signature Characters */

        private static final String SIG_VOID                   = "V";
        private static final String SIG_BOOLEAN                = "Z";
        private static final String SIG_BYTE                   = "B";
        private static final String SIG_CHAR                   = "C";
        private static final String SIG_SHORT                  = "S";
        private static final String SIG_INT                    = "I";
        private static final String SIG_LONG                   = "J";
        private static final String SIG_FLOAT                  = "F";
        private static final String SIG_DOUBLE                 = "D";
        private static final String SIG_ARRAY                  = "[";
        private static final String SIG_CLASS                  = "L";



        public TypeSignature(Elements elems){
            this.elems = elems;
        }

        /*
         * Returns the type signature of a field according to JVM specs
         */
        public String getTypeSignature(String javasignature) throws SignatureException {
            return getParamJVMSignature(javasignature);
        }

        /*
         * Returns the type signature of a method according to JVM specs
         */
        public String getTypeSignature(String javasignature, TypeMirror returnType)
                throws SignatureException {
            String signature = null; //Java type signature.
            String typeSignature = null; //Internal type signature.
            List<String> params = new ArrayList<String>(); //List of parameters.
            String paramsig = null; //Java parameter signature.
            String paramJVMSig = null; //Internal parameter signature.
            String returnSig = null; //Java return type signature.
            String returnJVMType = null; //Internal return type signature.
            int dimensions = 0; //Array dimension.

            int startIndex = -1;
            int endIndex = -1;
            StringTokenizer st = null;
            int i = 0;

            // Gets the actual java signature without parentheses.
            if (javasignature != null) {
                startIndex = javasignature.indexOf("(");
                endIndex = javasignature.indexOf(")");
            }

            if (((startIndex != -1) && (endIndex != -1))
                &&(startIndex+1 < javasignature.length())
                &&(endIndex < javasignature.length())) {
                signature = javasignature.substring(startIndex+1, endIndex);
            }

            // Separates parameters.
            if (signature != null) {
                if (signature.indexOf(",") != -1) {
                    st = new StringTokenizer(signature, ",");
                    if (st != null) {
                        while (st.hasMoreTokens()) {
                            params.add(st.nextToken());
                        }
                    }
                } else {
                    params.add(signature);
                }
            }

            /* JVM type signature. */
            typeSignature = "(";

            // Gets indivisual internal parameter signature.
            while (params.isEmpty() != true) {
                paramsig = params.remove(i).trim();
                paramJVMSig  = getParamJVMSignature(paramsig);
                if (paramJVMSig != null) {
                    typeSignature += paramJVMSig;
                }
            }

            typeSignature += ")";

            // Get internal return type signature.

            returnJVMType = "";
            if (returnType != null) {
                dimensions = dimensions(returnType);
            }

            //Gets array dimension of return type.
            while (dimensions-- > 0) {
                returnJVMType += "[";
            }
            if (returnType != null) {
                returnSig = qualifiedTypeName(returnType);
                returnJVMType += getComponentType(returnSig);
            } else {
                System.out.println("Invalid return type.");
            }

            typeSignature += returnJVMType;

            return typeSignature;
        }

        /*
         * Returns internal signature of a parameter.
         */
        private String getParamJVMSignature(String paramsig) throws SignatureException {
            String paramJVMSig = "";
            String componentType ="";

            if(paramsig != null){

                if(paramsig.indexOf("[]") != -1) {
                    // Gets array dimension.
                    int endindex = paramsig.indexOf("[]");
                    componentType = paramsig.substring(0, endindex);
                    String dimensionString =  paramsig.substring(endindex);
                    if(dimensionString != null){
                        while(dimensionString.indexOf("[]") != -1){
                            paramJVMSig += "[";
                            int beginindex = dimensionString.indexOf("]") + 1;
                            if(beginindex < dimensionString.length()){
                                dimensionString = dimensionString.substring(beginindex);
                            }else
                                dimensionString = "";
                        }
                    }
                } else componentType = paramsig;

                paramJVMSig += getComponentType(componentType);
            }
            return paramJVMSig;
        }

        /*
         * Returns internal signature of a component.
         */
        private String getComponentType(String componentType) throws SignatureException {

            String JVMSig = "";

            if(componentType != null){
                if(componentType.equals("void")) JVMSig += SIG_VOID ;
                else if(componentType.equals("boolean"))  JVMSig += SIG_BOOLEAN ;
                else if(componentType.equals("byte")) JVMSig += SIG_BYTE ;
                else if(componentType.equals("char"))  JVMSig += SIG_CHAR ;
                else if(componentType.equals("short"))  JVMSig += SIG_SHORT ;
                else if(componentType.equals("int"))  JVMSig += SIG_INT ;
                else if(componentType.equals("long"))  JVMSig += SIG_LONG ;
                else if(componentType.equals("float")) JVMSig += SIG_FLOAT ;
                else if(componentType.equals("double"))  JVMSig += SIG_DOUBLE ;
                else {
                    if(!componentType.equals("")){
                        TypeElement classNameDoc = elems.getTypeElement(componentType);

                        if(classNameDoc == null){
                            throw new SignatureException(componentType);
                        }else {
                            String classname = classNameDoc.getQualifiedName().toString();
                            String newclassname = classname.replace('.', '/');
                            JVMSig += "L";
                            JVMSig += newclassname;
                            JVMSig += ";";
                        }
                    }
                }
            }
            return JVMSig;
        }

        int dimensions(TypeMirror t) {
            if (t.getKind() != TypeKind.ARRAY)
                return 0;
            return 1 + dimensions(((ArrayType) t).getComponentType());
        }


        String qualifiedTypeName(TypeMirror type) {
            TypeVisitor<Name, Void> v = new SimpleTypeVisitor8<Name, Void>() {
                @Override
                public Name visitArray(ArrayType t, Void p) {
                    return t.getComponentType().accept(this, p);
                }

                @Override
                public Name visitDeclared(DeclaredType t, Void p) {
                    return ((TypeElement) t.asElement()).getQualifiedName();
                }

                @Override
                public Name visitPrimitive(PrimitiveType t, Void p) {
                    return elems.getName(t.toString());
                }

                @Override
                public Name visitNoType(NoType t, Void p) {
                    if (t.getKind() == TypeKind.VOID)
                        return elems.getName("void");
                    return defaultAction(t, p);
                }

                @Override
                public Name visitTypeVariable(TypeVariable t, Void p) {
                    return t.getUpperBound().accept(this, p);
                }
            };
            return v.visit(type).toString();
        }
    }

}
