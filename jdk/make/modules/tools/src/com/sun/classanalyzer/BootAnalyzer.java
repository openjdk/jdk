/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.classanalyzer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sun.tools.classfile.*;
import com.sun.tools.classfile.ConstantPool.*;
import static com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.Instruction.TypeKind;
import com.sun.tools.classfile.Type.*;

/**
 * Generate the module config for the boot module with
 * a given set of roots (classes or methods) and exclude list.
 *
 * This tool does method-level dependency analysis starting
 * from the root set and follows references transitively as follows:
 * <ul>
 * <li>For a given class, it will parse the ClassFile to
 *     find its superclass and superinterfaces and also
 *     its static initializer &lt;clinit&gt;.</li>
 * <li>For each method, it will parse its Code attribute
 *     to look for a Methodref, Fieldref, and InterfaceMethodref.
 *     </li>
 * <li>For each Fieldref, it will include the type of
 *     the field in the dependency.</li>
 * <li>For each MethodRef, it will follow all references in
 *     that method.</li>
 * <li>For each InterfaceMethodref, it will follow all references in
 *     that method defined its implementation classes in
 *     the resulting dependency list.</li>
 * </ul>
 *
 * Limitation:
 * <ul>
 * <li>For each Methodref, it only parses the method of
 *     the specified type.  It doesn't analyze the class hierarchy
 *     and follow references of its subclasses since it ends up
 *     pulls in many unnecessary dependencies.  For now,
 *     the list of subclasses and methods need to be listed in
 *     the root set.</li>
 * </ul>
 *
 * @author Mandy Chung
 */
public class BootAnalyzer {

    public static void main(String[] args) throws Exception {
        String jdkhome = null;
        String config = null;
        String output = ".";
        boolean printClassList = false;

        // process arguments
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-jdkhome")) {
                if (i < args.length) {
                    jdkhome = args[i++];
                } else {
                    usage();
                }
            } else if (arg.equals("-config")) {
                config = args[i++];
            } else if (arg.equals("-output")) {
                output = args[i++];
            } else if (arg.equals("-classlist")) {
                printClassList = true;
            } else {
                usage();
            }
        }



        if (jdkhome == null || config == null) {
            usage();
        }

        File jre = new File(jdkhome, "jre");
        if (jre.exists()) {
            ClassPath.setJDKHome(jdkhome);
        } else {
            File classes = new File(jdkhome, "classes");
            if (classes.exists()) {
                ClassPath.setClassPath(classes.getCanonicalPath());
            } else {
                throw new RuntimeException("Invalid jdkhome: " + jdkhome);
            }
        }

        parseConfigFile(config);
        followRoots();

        // create output directory if it doesn't exist
        File dir = new File(output);
        if (!dir.isDirectory()) {
            if (!dir.exists()) {
                boolean created = dir.mkdir();
                if (!created) {
                    throw new RuntimeException("Unable to create `" + dir + "'");
                }
            }
        }

        String bootmodule = "boot";
        String bootconfig = resolve(dir, bootmodule, "config");
        printBootConfig(bootconfig, bootmodule);

        List<ModuleConfig> list = ModuleConfig.readConfigurationFile(bootconfig);
        Module module = Module.addModule(list.get(0));
        for (Klass k : Klass.getAllClasses()) {
            module.addKlass(k);
        }
        module.fixupDependencies();

        if (printClassList) {
            module.printClassListTo(resolve(dir, bootmodule, "classlist"));
            module.printSummaryTo(resolve(dir, bootmodule, "summary"));
        }
    }

    // print boot.config file as an input to the ClassAnalyzer
    private static void printBootConfig(String output, String bootmodule) throws IOException {

        File f = new File(output);
        PrintWriter writer = new PrintWriter(f);
        try {
            int count = 0;
            writer.format("module %s {%n", bootmodule);
            for (Klass k : Klass.getAllClasses()) {
                if (count++ == 0) {
                    writer.format("%4s%7s %s", "", "include", k);
                } else {
                    writer.format(",%n");
                    writer.format("%4s%7s %s", "", "", k);
                }
            }
            writer.format(";%n}%n");
        } finally {
            writer.close();
        }
    }

    private static String resolve(File dir, String mname, String suffix) {
        File f = new File(dir, mname + "." + suffix);
        return f.toString();

    }
    static List<MethodDescriptor> methods = new LinkedList<MethodDescriptor>();
    static Deque<MethodDescriptor> pending = new ArrayDeque<MethodDescriptor>();
    static Deque<MethodDescriptor> interfaceMethodRefs = new ArrayDeque<MethodDescriptor>();
    static Filter filter = new Filter();

    private static void followRoots() throws IOException {
        MethodDescriptor md = null;

        while ((md = pending.poll()) != null) {
            if (!methods.contains(md)) {
                methods.add(md);
                if (md.classname.isEmpty()) {
                    trace("Warning: class missing %s%n", md);
                    continue;
                }

                if (filter.isExcluded(md.classname)) {
                    trace("excluded %s%n", md);
                } else {
                    KlassInfo kinfo = getKlassInfo(md.classname);
                    if (kinfo.classname.contains("$")) {
                        int pos = kinfo.classname.lastIndexOf('$');
                        String outer = kinfo.classname.substring(0, pos);
                        if (!cache.containsKey(outer)) {
                            trace("  include outer class %s%n", outer);
                            getKlassInfo(outer).ensureParse();
                        }
                    }

                    kinfo.ensureParse();
                    if (md.methodname.length() > 0) {
                        if (filter.isExcluded(md.name)) {
                            trace("excluded %s%n", md);
                        } else {
                            if (md.interfaceMethodRef) {
                                trace("interface methodref %s%n", md);
                                interfaceMethodRefs.add(md);
                            } else {
                                List<String> descriptors = kinfo.parse(md);
                                if (descriptors.isEmpty()) {
                                    if (kinfo.getSuperclass() != null) {
                                        String sn = kinfo.getSuperclass().classname;
                                        MethodDescriptor superMD = new MethodDescriptor(sn + "." + md.methodname, md.descriptor, false);
                                        if (!methods.contains(superMD) && !pending.contains(superMD)) {
                                            trace("  delegated %s to %s%n", md, superMD);
                                            pending.add(superMD);
                                        }
                                    } else if (kinfo.isClass()) {
                                        trace("  %s (not found)%n", md);
                                    } else {
                                        trace("  %s (interface)%n", md);
                                    }
                                } else {
                                    if (md.descriptor.equals("*")) {
                                        trace("  parsed %s : ", md.name);
                                        for (String s : descriptors) {
                                            trace(" %s", s);
                                        }
                                        trace("%n");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pending.isEmpty()) {
                for (Klass k : Klass.getAllClasses()) {
                    if (k.getFileSize() == 0) {
                        getKlassInfo(k.getClassName()).ensureParse();
                    }
                }
                while ((md = interfaceMethodRefs.poll()) != null) {
                    addSubClassMethods(md);
                }
            }
        }
    }

    static void addSubClassMethods(MethodDescriptor md) throws IOException {
        for (KlassInfo kinfo : getSubClasses(md.classname)) {
            String methodname = kinfo.classname + "." + md.methodname;
            MethodDescriptor other = new MethodDescriptor(methodname, md.descriptor, false);
            if (!methods.contains(other) && !pending.contains(other)) {
                trace("Warning: subclass from %s to %s%n", md.classname, other);
                pending.add(other);
            }
        }
    }
    private final static String privilegedActionInterf = "java.security.PrivilegedAction";
    private final static String privilegedExceptionActionInterf = "java.security.PrivilegedExceptionAction";

    static boolean isPrivilegedAction(String classname) {
        if (classname.isEmpty()) {
            return false;
        }
        KlassInfo kinfo = getKlassInfo(classname);
        for (KlassInfo ki : kinfo.getInterfaces()) {
            String interf = ki.classname;
            if (interf.equals(privilegedActionInterf) ||
                    interf.equals(privilegedExceptionActionInterf)) {
                return true;
            }
        }
        return false;
    }
    static Map<String, KlassInfo> cache = new HashMap<String, KlassInfo>();

    static KlassInfo getKlassInfo(String classname) {
        classname = classname.replace('/', '.');

        KlassInfo kinfo = cache.get(classname);
        if (kinfo == null) {
            kinfo = new KlassInfo(classname);
            cache.put(classname, kinfo);
        }
        return kinfo;
    }

    static class KlassInfo {

        final String classname;
        private ClassFileParser parser;
        private KlassInfo superclass;
        private List<KlassInfo> interfaces = new LinkedList<KlassInfo>();

        KlassInfo(String classname) {
            this.classname = classname;
        }

        boolean isClass() {
            ensureParse();
            return parser.classfile.isClass();
        }

        KlassInfo getSuperclass() {
            ensureParse();
            return superclass;
        }

        List<KlassInfo> getInterfaces() {
            ensureParse();
            return java.util.Collections.unmodifiableList(interfaces);
        }

        void ensureParse() {
            try {
                getClassFileParser();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        synchronized ClassFileParser getClassFileParser() throws IOException {
            if (parser == null) {
                parser = ClassPath.parserForClass(classname);
                if (parser != null) {
                    parseClassFile();
                    List<String> descriptors = parse(new MethodDescriptor(classname + ".<clinit>", "()V", false));
                }
            }
            return parser;
        }

        List<String> parse(MethodDescriptor md) {
            ensureParse();
            try {
                List<String> descriptors = new LinkedList<String>();
                for (Method m : parser.classfile.methods) {
                    String name = m.getName(parser.classfile.constant_pool);
                    String desc = parser.constantPoolParser.getDescriptor(m.descriptor.index);
                    if (name.equals(md.methodname)) {
                        if (md.descriptor.equals("*") || md.descriptor.equals(desc)) {
                            parseMethod(parser, m);
                            descriptors.add(desc);
                        }
                    }
                }
                return descriptors;
            } catch (ConstantPoolException ex) {
                throw new RuntimeException(ex);
            }
        }

        private void parseClassFile() throws IOException {
            parser.parseClassInfo();

            ClassFile classfile = parser.classfile;
            try {
                if (classfile.super_class > 0) {
                    superclass = getKlassInfo(classfile.getSuperclassName());
                }
                if (classfile.interfaces != null) {
                    for (int i = 0; i < classfile.interfaces.length; i++) {
                        interfaces.add(getKlassInfo(classfile.getInterfaceName(i)));
                    }
                }
            } catch (ConstantPoolException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    static List<KlassInfo> getSubClasses(String classname) throws IOException {
        List<KlassInfo> result = new LinkedList<KlassInfo>();
        List<KlassInfo> list = new LinkedList<KlassInfo>();
        list.addAll(cache.values());
        for (KlassInfo kinfo : list) {
            if (kinfo.getSuperclass() != null && classname.equals(kinfo.getSuperclass().classname)) {
                result.add(kinfo);
            }
            for (KlassInfo interf : kinfo.getInterfaces()) {
                if (classname.equals(interf.classname)) {
                    result.add(kinfo);
                }
            }
        }
        return result;
    }

    private static void parseConfigFile(String config) throws IOException {
        FileInputStream in = new FileInputStream(config);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if ((line = line.trim()).length() > 0) {
                    if (line.startsWith("#")) {
                        continue;
                    }

                    String[] s = line.split("\\s+");
                    if ("exclude".equals(s[0])) {
                        filter.exclude(s[1]);
                    } else {
                        String name = s[0].replace('/', '.');
                        if (name.length() > 0) {
                            String classname = name.replace('/', '.');
                            if (s.length == 2) {
                                // method name
                                int pos = classname.lastIndexOf('.');
                                classname = classname.substring(0, pos);
                            }

                            KlassInfo kinfo = getKlassInfo(classname);
                            if (kinfo.getClassFileParser() != null) {
                                // class exists
                                MethodDescriptor md = (s.length == 1) ? new MethodDescriptor(name) : new MethodDescriptor(name, s[1], false);
                                if (!pending.contains(md)) {
                                    pending.add(md);
                                }
                            } else {
                                // class not found
                                trace("Class %s not found%n", classname);
                            }
                        }
                    }
                }
            }

        } finally {
            in.close();
        }
    }

    private static void parseMethod(ClassFileParser cfparser, Method m) {
        Klass.Method kmethod = cfparser.parseMethod(m);
        Code_attribute c_attr = (Code_attribute) m.attributes.get(Attribute.Code);
        if (c_attr != null) {
            LineNumberTable_attribute lineNumTable =
                    (LineNumberTable_attribute) c_attr.attributes.get(Attribute.LineNumberTable);
            InstructorVisitor visitor = new InstructorVisitor(cfparser, lineNumTable);
            trace("parseMethod %s %s %n", cfparser.this_klass, kmethod);
            for (Instruction instr : c_attr.getInstructions()) {
                try {
                    instr.accept(visitor, kmethod);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new RuntimeException("error at or after byte " + instr.getPC());
                }

            }

            if (c_attr.exception_table_langth > 0) {
                for (int i = 0; i <
                        c_attr.exception_table.length; i++) {
                    Code_attribute.Exception_data handler = c_attr.exception_table[i];
                    int catch_type = handler.catch_type;
                    if (catch_type > 0) {
                        visitor.addConstantPoolRef(catch_type, kmethod, handler.start_pc);
                    }

                }
            }
        }
    }

    static class MethodDescriptor {

        final String name;
        final String classname;
        final String methodname;
        final String descriptor;
        final boolean interfaceMethodRef;

        MethodDescriptor(String classname) {
            this.classname = classname.replace('/', '.');
            this.name = this.classname;
            this.methodname = "";
            this.descriptor = "";
            this.interfaceMethodRef = false;
            if (this.classname.length() == 1) {
                throw new RuntimeException("invalid " + this);
            }
        }

        MethodDescriptor(String name, String descriptor, boolean interfaceMethodRef) {
            name = name.replace('/', '.');
            this.name = name;
            int pos = name.lastIndexOf('.');
            this.classname = name.substring(0, pos);
            this.methodname = name.substring(pos + 1, name.length());
            this.descriptor = descriptor;
            this.interfaceMethodRef = interfaceMethodRef;
            if (this.classname.length() == 1) {
                throw new RuntimeException("invalid " + this);
            }
        }

        @Override
        public boolean equals(Object obj) {
            MethodDescriptor m = (MethodDescriptor) obj;

            return this.name.equals(m.name) &&
                    this.descriptor.equals(m.descriptor);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (this.name != null ? this.name.hashCode() : 0);
            hash = 97 * hash + (this.descriptor != null ? this.descriptor.hashCode() : 0);
            return hash;
        }

        public String toString() {
            if (descriptor.isEmpty()) {
                return name;
            } else {
                return name + " : " + descriptor;
            }
        }
    }

    static class Filter {

        private Set<String> excludes = new TreeSet<String>();

        Filter exclude(String pattern) {
            excludes.add(pattern);
            return this;
        }

        boolean isExcluded(String klass) {
            for (String pattern : excludes) {
                if (matches(klass, pattern)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matches(String klass, String pattern) {
            int pos = klass.lastIndexOf('.');
            String packageName = pos > 0 ? klass.substring(0, pos) : "<unnamed>";
            if (pattern.endsWith("**")) {
                String p = pattern.substring(0, pattern.length() - 2);
                return klass.startsWith(p);
            } else if (pattern.endsWith("*")) {
                pos = pattern.lastIndexOf('.');
                String pkg = pos > 0 ? pattern.substring(0, pos) : "<unnamed>";
                if (packageName.equals(pkg)) {
                    // package name has to be exact match
                    String p = pattern.substring(0, pattern.length() - 1);
                    return klass.startsWith(p);
                } else {
                    return false;
                }
            } else {
                // exact match or inner class
                return klass.equals(pattern) || klass.startsWith(pattern + "$");
            }
        }
    }

    static class InstructorVisitor implements Instruction.KindVisitor<Void, Klass.Method> {

        private final ClassFileParser parser;
        private final LineNumberTable_attribute lineNumTable;

        InstructorVisitor(ClassFileParser parser, LineNumberTable_attribute lineNumTable) {
            this.parser = parser;
            this.lineNumTable = lineNumTable;
        }

        int getLineNumber(int pc) {
            if (lineNumTable != null) {
                int start_pc = 0;
                int lineno = 0;
                for (int i = 0; i < lineNumTable.line_number_table_length; i++) {
                    int cur_start_pc = lineNumTable.line_number_table[i].start_pc;
                    if (pc == 0 && cur_start_pc == 0) {
                        return lineNumTable.line_number_table[i].line_number;
                    } else if (pc >= start_pc && pc < cur_start_pc) {
                        return lineno;
                    }
                    start_pc = cur_start_pc;
                    lineno = lineNumTable.line_number_table[i].line_number;
                }
            }
            return 0;
        }

        void addConstantPoolRef(int index, Klass.Method m, int pc) {
            try {
                CPInfo cpInfo = parser.classfile.constant_pool.get(index);
                String name = cpInfo.accept(typeFinder, null);
                if (name != null) {
                    trace("   %s %s at line %d%n", parser.constantPoolParser.tagName(index), name, getLineNumber(pc));
                }
            } catch (InvalidIndex ex) {
                throw new RuntimeException(ex);
            }
        }

        public Void visitNoOperands(Instruction instr, Klass.Method m) {
            return null;
        }

        public Void visitArrayType(Instruction instr, TypeKind kind, Klass.Method m) {
            return null;
        }

        public Void visitBranch(Instruction instr, int offset, Klass.Method m) {
            return null;
        }

        public Void visitConstantPoolRef(Instruction instr, int index, Klass.Method m) {
            addConstantPoolRef(index, m, instr.getPC());
            return null;
        }

        public Void visitConstantPoolRefAndValue(Instruction instr, int index, int value, Klass.Method m) {
            addConstantPoolRef(index, m, instr.getPC());
            return null;
        }

        public Void visitLocal(Instruction instr, int index, Klass.Method m) {
            return null;
        }

        public Void visitLocalAndValue(Instruction instr, int index, int value, Klass.Method m) {
            return null;
        }

        public Void visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, Klass.Method m) {
            return null;
        }

        public Void visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, Klass.Method m) {
            return null;
        }

        public Void visitValue(Instruction instr, int value, Klass.Method m) {
            return null;
        }

        public Void visitUnknown(Instruction instr, Klass.Method m) {
            return null;
        }
        private ConstantPool.Visitor<String, Void> typeFinder = new ConstantPool.Visitor<String, Void>() {

            String getClassName(CPRefInfo info, Void p) {
                try {
                    return parser.checkClassName(info.getClassName()).replace('/', '.');
                } catch (ConstantPoolException ex) {
                    throw new RuntimeException(ex);
                }
            }

            boolean addReferencedClass(String name) {
                if (Klass.findKlass(name) == null) {
                    MethodDescriptor md = new MethodDescriptor(name);
                    if (!methods.contains(md) && !pending.contains(md)) {
                        pending.add(md);
                    }
                    return true;
                }
                return false;
            }
            private String privilegedActionClass = "";

            void cachePrivilegedAction(String classname) {
                trace("   found PrivilegedAction %s%n", classname);
                privilegedActionClass = classname;
            }

            void doPrivilegedCall(String method) {
                if (privilegedActionClass.length() > 0) {
                    MethodDescriptor md = new MethodDescriptor(privilegedActionClass + ".run", "*", false);
                    if (!methods.contains(md) && !pending.contains(md)) {
                        trace("   doPrivileged %s%n", md);
                        pending.add(md);
                    }
                }
            }

            private String addMethodDescriptor(CPRefInfo info, Void p) {
                try {
                    String classname = getClassName(info, null);
                    String method = classname + "." + info.getNameAndTypeInfo().getName();
                    String descriptor = info.getNameAndTypeInfo().getType();

                    if (method.endsWith(".<init>") && isPrivilegedAction(classname)) {
                        cachePrivilegedAction(classname);
                    }
                    if (method.equals("java.security.AccessController.doPrivileged")) {
                        doPrivilegedCall(method);
                        return method;
                    }

                    boolean interfaceMethodRef = info instanceof CONSTANT_InterfaceMethodref_info;
                    MethodDescriptor md = new MethodDescriptor(method, descriptor, interfaceMethodRef);
                    if (!methods.contains(md) && !pending.contains(md)) {
                        pending.add(md);
                    }
                    return method;
                } catch (ConstantPoolException e) {
                    throw new RuntimeException(e);
                }
            }

            public String visitClass(CONSTANT_Class_info info, Void p) {
                try {
                    String classname = parser.checkClassName(info.getName()).replace('/', '.');
                    if (classname.length() > 0) {
                        addReferencedClass(classname);
                    }
                    return classname;
                } catch (ConstantPoolException ex) {
                    throw new RuntimeException(ex);
                }
            }

            public String visitDouble(CONSTANT_Double_info info, Void p) {
                // skip
                return null;
            }

            public String visitFieldref(CONSTANT_Fieldref_info info, Void p) {
                try {
                    String classname = getClassName(info, p);
                    if (classname.length() > 0) {
                        addReferencedClass(classname);
                    }

                    String type = info.getNameAndTypeInfo().getType();
                    String fieldType = parser.checkClassName(type).replace('/', '.');
                    if (fieldType.length() > 0) {
                        addReferencedClass(classname);
                    }
                    return parser.constantPoolParser.stringValue(info);
                } catch (ConstantPoolException e) {
                    throw new RuntimeException(e);
                }
            }

            public String visitFloat(CONSTANT_Float_info info, Void p) {
                // skip
                return null;
            }

            public String visitInteger(CONSTANT_Integer_info info, Void p) {
                // skip
                return null;
            }

            public String visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info info, Void p) {
                return addMethodDescriptor(info, p);
            }

            public String visitLong(CONSTANT_Long_info info, Void p) {
                // skip
                return null;
            }

            public String visitNameAndType(CONSTANT_NameAndType_info info, Void p) {
                // skip
                return null;
            }

            public String visitMethodref(CONSTANT_Methodref_info info, Void p) {
                return addMethodDescriptor(info, p);
            }

            public String visitString(CONSTANT_String_info info, Void p) {
                // skip
                return null;
            }

            public String visitUtf8(CONSTANT_Utf8_info info, Void p) {
                return null;
            }
        };
    }
    static boolean traceOn = System.getProperty("classanalyzer.debug") != null;

    private static void trace(String format, Object... args) {
        if (traceOn) {
            System.out.format(format, args);
        }
    }

    private static void usage() {
        System.out.println("Usage: BootAnalyzer <options>");
        System.out.println("Options: ");
        System.out.println("\t-jdkhome <JDK home> where all jars will be parsed");
        System.out.println("\t-config  <roots for the boot module>");
        System.out.println("\t-output  <output dir>");
        System.out.println("\t-classlist print class list and summary");
        System.exit(-1);
    }
}
