/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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
package xmlkit; // -*- mode: java; indent-tabs-mode: nil -*-

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.Annotation.Annotation_element_value;
import com.sun.tools.classfile.Annotation.Array_element_value;
import com.sun.tools.classfile.Annotation.Class_element_value;
import com.sun.tools.classfile.Annotation.Enum_element_value;
import com.sun.tools.classfile.Annotation.Primitive_element_value;
import com.sun.tools.classfile.AnnotationDefault_attribute;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.BootstrapMethods_attribute;
import com.sun.tools.classfile.CharacterRangeTable_attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.CompilationID_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Class_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Module_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Package_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Double_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Fieldref_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Float_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Integer_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_InterfaceMethodref_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_InvokeDynamic_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Long_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_MethodHandle_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_MethodType_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Methodref_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_NameAndType_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_String_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Utf8_info;
import com.sun.tools.classfile.ConstantPool.CPInfo;
import com.sun.tools.classfile.ConstantPool.InvalidIndex;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ConstantValue_attribute;
import com.sun.tools.classfile.DefaultAttribute;
import com.sun.tools.classfile.Deprecated_attribute;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;
import com.sun.tools.classfile.EnclosingMethod_attribute;
import com.sun.tools.classfile.Exceptions_attribute;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.InnerClasses_attribute;
import com.sun.tools.classfile.InnerClasses_attribute.Info;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.Instruction.TypeKind;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.LocalVariableTable_attribute;
import com.sun.tools.classfile.LocalVariableTypeTable_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.MethodParameters_attribute;
import com.sun.tools.classfile.Module_attribute;
import com.sun.tools.classfile.Module_attribute.ExportsEntry;
import com.sun.tools.classfile.Module_attribute.ProvidesEntry;
import com.sun.tools.classfile.Module_attribute.RequiresEntry;
import com.sun.tools.classfile.ModuleHashes_attribute;
import com.sun.tools.classfile.ModuleHashes_attribute.Entry;
import com.sun.tools.classfile.ModuleMainClass_attribute;
import com.sun.tools.classfile.ModuleResolution_attribute;
import com.sun.tools.classfile.ModuleTarget_attribute;
import com.sun.tools.classfile.ModulePackages_attribute;
import com.sun.tools.classfile.NestHost_attribute;
import com.sun.tools.classfile.NestMembers_attribute;
import com.sun.tools.classfile.Opcode;
import com.sun.tools.classfile.RuntimeInvisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeInvisibleParameterAnnotations_attribute;
import com.sun.tools.classfile.RuntimeInvisibleTypeAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleParameterAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleTypeAnnotations_attribute;
import com.sun.tools.classfile.Signature_attribute;
import com.sun.tools.classfile.SourceDebugExtension_attribute;
import com.sun.tools.classfile.SourceFile_attribute;
import com.sun.tools.classfile.SourceID_attribute;
import com.sun.tools.classfile.StackMapTable_attribute;
import com.sun.tools.classfile.StackMapTable_attribute.append_frame;
import com.sun.tools.classfile.StackMapTable_attribute.chop_frame;
import com.sun.tools.classfile.StackMapTable_attribute.full_frame;
import com.sun.tools.classfile.StackMapTable_attribute.same_frame;
import com.sun.tools.classfile.StackMapTable_attribute.same_frame_extended;
import com.sun.tools.classfile.StackMapTable_attribute.same_locals_1_stack_item_frame;
import com.sun.tools.classfile.StackMapTable_attribute.same_locals_1_stack_item_frame_extended;
import com.sun.tools.classfile.StackMap_attribute;
import com.sun.tools.classfile.Synthetic_attribute;
import com.sun.tools.classfile.TypeAnnotation;
import com.sun.tools.classfile.TypeAnnotation.Position;
import static com.sun.tools.classfile.TypeAnnotation.TargetType.THROWS;
import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import xmlkit.XMLKit.Element;

/*
 * @author jrose, ksrini
 */
public class ClassReader {

    private static final CommandLineParser CLP = new CommandLineParser(""
            + "-source:     +> = \n"
            + "-dest:       +> = \n"
            + "-encoding:   +> = \n"
            + "-jcov           $ \n   -nojcov         !-jcov        \n"
            + "-verbose        $ \n   -noverbose      !-verbose     \n"
            + "-keepPath       $ \n   -nokeepPath     !-keepPath    \n"
            + "-keepCP         $ \n   -nokeepCP       !-keepCP      \n"
            + "-keepOrder      $ \n   -nokeepOrder    !-keepOrder   \n"
            + "-continue       $ \n   -nocontinue     !-continue    \n"
            + "-@         >-@  . \n"
            + "-              +? \n"
            + "\n");


    // Protected state for representing the class file.
    protected Element cfile;          // <ClassFile ...>
    protected Element cpool;          // <ConstantPool ...>
    protected Element klass;          // <Class ...>
    protected List<String> thePool;    // stringified flattened Constant Pool

    public static void main(String[] ava) throws IOException {
        ArrayList<String> av = new ArrayList<>(Arrays.asList(ava));
        HashMap<String, String> props = new HashMap<>();
        props.put("-encoding:", "UTF8");  // default
        props.put("-keepOrder", null);    // CLI default
        props.put("-pretty", "1");     // CLI default
        props.put("-continue", "1");     // CLI default
        CLP.parse(av, props);
        //System.out.println(props+" ++ "+av);
        File source = asFile(props.get("-source:"));
        File dest = asFile(props.get("-dest:"));
        String encoding = props.get("-encoding:");
        boolean contError = props.containsKey("-continue");
        ClassReader options = new ClassReader();
        options.copyOptionsFrom(props);
        /*
        if (dest == null && av.size() > 1) {
        dest = File.createTempFile("TestOut", ".dir", new File("."));
        dest.delete();
        if (!dest.mkdir())
        throw new RuntimeException("Cannot create "+dest);
        System.out.println("Writing results to "+dest);
        }
         */
        if (av.isEmpty()) {
            av.add("");  //to enter this loop
        }
        boolean readList = false;
        for (String a : av) {
            if (readList) {
                readList = false;
                InputStream fin;
                if (a.equals("-")) {
                    fin = System.in;
                } else {
                    fin = new FileInputStream(a);
                }

                BufferedReader files = makeReader(fin, encoding);
                for (String file; (file = files.readLine()) != null;) {
                    doFile(file, source, dest, options, encoding, contError);
                }
                if (fin != System.in) {
                    fin.close();
                }
            } else if (a.equals("-@")) {
                readList = true;
            } else if (a.startsWith("-")) {
                throw new RuntimeException("Bad flag argument: " + a);
            } else if (source.getName().endsWith(".jar")) {
                doJar(a, source, dest, options, encoding, contError);
            } else {
                doFile(a, source, dest, options, encoding, contError);
            }
        }
    }

    private static File asFile(String str) {
        return (str == null) ? null : new File(str);
    }

    private static void doFile(String a,
            File source, File dest,
            ClassReader options, String encoding,
            boolean contError) throws IOException  {
        if (!contError) {
            doFile(a, source, dest, options, encoding);
        } else {
            try {
                doFile(a, source, dest, options, encoding);
            } catch (Exception ee) {
                System.out.println("Error processing " + source + ": " + ee);
                ee.printStackTrace();
            }
        }
    }

    private static void doJar(String a, File source, File dest,
                              ClassReader options, String encoding,
                              Boolean contError) throws IOException {
        try {
            JarFile jf = new JarFile(source);
            for (JarEntry je : Collections.list(jf.entries())) {
                String name = je.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                try {
                    doStream(name, jf.getInputStream(je), dest, options, encoding);
                } catch (Exception e) {
                    if (contError) {
                        System.out.println("Error processing " + source + ": " + e);
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        } catch (IOException ioe) {
            throw ioe;
        }
    }

    private static void doStream(String a, InputStream in, File dest,
                                 ClassReader options, String encoding) throws IOException {

        File f = new File(a);
        ClassReader cr = new ClassReader(options);
        Element e;
        if (options.verbose) {
            System.out.println("Reading " + f);
        }
        e = cr.readFrom(in);

        OutputStream out;
        if (dest == null) {
            out = System.out;
        } else {
            File outf = new File(dest, f.isAbsolute() ? f.getName() : f.getPath());
            String outName = outf.getName();
            File outSubdir = outf.getParentFile();
            outSubdir.mkdirs();
            int extPos = outName.lastIndexOf('.');
            if (extPos > 0) {
                outf = new File(outSubdir, outName.substring(0, extPos) + ".xml");
            }
            out = new FileOutputStream(outf);
        }

        Writer outw = makeWriter(out, encoding);
        if (options.pretty || !options.keepOrder) {
            e.writePrettyTo(outw);
        } else {
            e.writeTo(outw);
        }
        if (out == System.out) {
            outw.write("\n");
            outw.flush();
        } else {
            outw.close();
        }
    }

    private static void doFile(String a,
            File source, File dest,
            ClassReader options, String encoding) throws IOException {
        File inf = new File(source, a);
        if (dest != null && options.verbose) {
            System.out.println("Reading " + inf);
        }

        BufferedInputStream in = new BufferedInputStream(new FileInputStream(inf));

        doStream(a, in, dest, options, encoding);

    }

    public static BufferedReader makeReader(InputStream in,
                                            String encoding) throws IOException {
        Reader inw;
        in = new BufferedInputStream(in);  // add buffering
        if (encoding == null) {
            inw = new InputStreamReader(in);
        } else {
            inw = new InputStreamReader(in, encoding);
        }
        return new BufferedReader(inw);  // add buffering
    }

    public static Writer makeWriter(OutputStream out,
                                    String encoding) throws IOException {
        Writer outw;
        if (encoding == null) {
            outw = new OutputStreamWriter(out);
        } else {
            outw = new OutputStreamWriter(out, encoding);
        }
        return new BufferedWriter(outw);  // add buffering
    }

    public Element result() {
        return cfile;
    }

    protected InputStream in;
    protected ByteArrayOutputStream buf = new ByteArrayOutputStream(1024);
    // input options
    public boolean pretty = false;
    public boolean verbose = false;
    public boolean keepPath = false;
    public boolean keepCP = false;
    public boolean keepBytes = false;
    public boolean parseBytes = true;
    public boolean resolveRefs = true;
    public boolean keepOrder = true;
    public boolean keepSizes = false;

    public ClassReader() {
        cfile = new Element("ClassFile");
    }

    public ClassReader(ClassReader options) {
        this();
        copyOptionsFrom(options);
    }

    public void copyOptionsFrom(ClassReader options) {
        pretty = options.pretty;
        verbose = options.verbose;
        keepPath = options.keepPath;
        keepCP = options.keepCP;
        keepOrder = options.keepOrder;
    }

    public void copyOptionsFrom(Map<String, String> options) {
        if (options.containsKey("-pretty")) {
            pretty = (options.get("-pretty") != null);
        }
        if (options.containsKey("-verbose")) {
            verbose = (options.get("-verbose") != null);
        }
        if (options.containsKey("-keepPath")) {
            keepPath = (options.get("-keepPath") != null);
        }
        if (options.containsKey("-keepCP")) {
            keepCP = (options.get("-keepCP") != null);
        }
        if (options.containsKey("-keepOrder")) {
            keepOrder = (options.get("-keepOrder") != null);
        }
    }

    protected String getCpString(int i) {
        return thePool.get(i);
    }

    public Element readFrom(InputStream in) throws IOException {
        try {
            this.in = in;
            ClassFile c = ClassFile.read(in);
            // read the file header
            if (c.magic != 0xCAFEBABE) {
                throw new RuntimeException("bad magic number " +
                        Integer.toHexString(c.magic));
            }
            cfile.setAttr("magic", "" + c.magic);
            int minver = c.minor_version;
            int majver = c.major_version;
            cfile.setAttr("minver", "" + minver);
            cfile.setAttr("majver", "" + majver);
            readCP(c);
            readClass(c);
            return result();
        } catch (InvalidDescriptor | ConstantPoolException ex) {
            throw new IOException("Fatal error", ex);
        }
    }

    public Element readFrom(File file) throws IOException {
        try (InputStream strm = new FileInputStream(file)) {
            Element e = readFrom(new BufferedInputStream(strm));
            if (keepPath) {
                e.setAttr("path", file.toString());
            }
            return e;
        }
    }

    private void readClass(ClassFile c) throws IOException,
                                               ConstantPoolException,
                                               InvalidDescriptor {
        klass = new Element("Class");
        cfile.add(klass);
        String thisk = c.getName();

        klass.setAttr("name", thisk);

        AccessFlags af = new AccessFlags(c.access_flags.flags);
        klass.setAttr("flags", flagString(af, klass));
        if (!"java/lang/Object".equals(thisk)) {
            if (c.super_class != 0) {
                klass.setAttr("super", c.getSuperclassName());
            }
        }
        for (int i : c.interfaces) {
            klass.add(new Element("Interface", "name", getCpString(i)));
        }
        readFields(c, klass);
        readMethods(c, klass);
        readAttributesFor(c, c.attributes, klass);
        klass.trimToSize();
    }

    private void readFields(ClassFile c, Element klass) throws IOException {
        int len = c.fields.length;
        Element fields = new Element(len);
        for (Field f : c.fields) {
            Element field = new Element("Field");
            field.setAttr("name", getCpString(f.name_index));
            field.setAttr("type", getCpString(f.descriptor.index));
            field.setAttr("flags", flagString(f.access_flags.flags, field));
            readAttributesFor(c, f.attributes, field);

            field.trimToSize();
            fields.add(field);
        }
        if (!keepOrder) {
            fields.sort();
        }
        klass.addAll(fields);
    }


    private void readMethods(ClassFile c, Element klass) throws IOException {
        int len = c.methods.length;
        Element methods = new Element(len);
        for (Method m : c.methods) {
            Element member = new Element("Method");
            member.setAttr("name", getCpString(m.name_index));
            member.setAttr("type", getCpString(m.descriptor.index));
            member.setAttr("flags", flagString(m.access_flags.flags, member));
            readAttributesFor(c, m.attributes, member);

            member.trimToSize();
            methods.add(member);
        }
        if (!keepOrder) {
            methods.sort();
        }
        klass.addAll(methods);
    }

    private AccessFlags.Kind getKind(Element e) {
        switch(e.getName()) {
            case "Class":
                return AccessFlags.Kind.Class;
            case "InnerClass":
                return AccessFlags.Kind.InnerClass;
            case "Field":
                return AccessFlags.Kind.Field ;
            case "Method":
                return AccessFlags.Kind.Method;
            default: throw new RuntimeException("should not reach here");
        }
    }

    protected String flagString(int flags, Element holder) {
        return flagString(new AccessFlags(flags), holder);
    }
    protected String flagString(AccessFlags af, Element holder) {
        return flagString(af, holder.getName());
    }
    protected String flagString(int flags, String kind) {
        return flagString(new AccessFlags(flags), kind);
    }
    protected String flagString(AccessFlags af, String kind) {
        Set<String> mods = null;
        switch (kind) {
            case "Class":
                mods = af.getClassFlags();
                break;
            case "InnerClass":
                mods = af.getInnerClassFlags();
                break;
            case "Field":
                mods = af.getFieldFlags();
                break;
            case "Method":
                mods = af.getMethodFlags();
                break;
            default:
                throw new RuntimeException("should not reach here");
        }
        StringBuilder sb = new StringBuilder();
        for (String x : mods) {
            sb.append(x.substring(x.indexOf('_') + 1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }


    protected  void readAttributesFor(ClassFile c, Attributes attrs, Element x) {
        Element container = new Element();
        AttributeVisitor av = new AttributeVisitor(this, c);
        for (Attribute a : attrs) {
            av.visit(a, container);
        }
        if (!keepOrder) {
            container.sort();
        }
        x.addAll(container);
    }

    private int fileSize = 0;
    private HashMap<String, int[]> attrSizes = new HashMap<>();

    private void attachTo(Element x, Object aval0) {
        if (aval0 == null) {
            return;
        }
        if (!(aval0 instanceof Element)) {
            x.add(aval0);
            return;
        }
        Element aval = (Element) aval0;
        if (!aval.isAnonymous()) {
            x.add(aval);
            return;
        }
        for (int imax = aval.attrSize(), i = 0; i < imax; i++) {
            //%%
            attachAttrTo(x, aval.getAttrName(i), aval.getAttr(i));
        }
        x.addAll(aval);
    }

    private void attachAttrTo(Element x, String aname, String aval) {
        String aval0 = x.getAttr(aname);
        if (aval0 != null) {
            aval = aval0 + " " + aval;
        }
        x.setAttr(aname, aval);
    }

    private void readCP(ClassFile c) throws IOException {
        cpool = new Element("ConstantPool", c.constant_pool.size());
        ConstantPoolVisitor cpv = new ConstantPoolVisitor(cpool, c,
                c.constant_pool.size());
        for (int i = 1 ; i < c.constant_pool.size() ; i++) {
            try {
                cpv.visit(c.constant_pool.get(i), i);
            } catch (InvalidIndex ex) {
                // can happen periodically when accessing doubles etc. ignore it
                // ex.printStackTrace();
            }
        }
        thePool = cpv.getPoolList();
        if (verbose) {
            for (int i = 0; i < thePool.size(); i++) {
                System.out.println("[" + i + "]: " + thePool.get(i));
            }
        }
        if (keepCP) {
            cfile.add(cpool);
        }
    }
}

class ConstantPoolVisitor implements ConstantPool.Visitor<String, Integer> {
    final List<String> slist;
    final Element xpool;
    final ClassFile cf;
    final ConstantPool cfpool;
    final List<String> bsmlist;


    public ConstantPoolVisitor(Element xpool, ClassFile cf, int size) {
        slist = new ArrayList<>(size);
        for (int i = 0 ; i < size; i++) {
            slist.add(null);
        }
        this.xpool = xpool;
        this.cf = cf;
        this.cfpool = cf.constant_pool;
        bsmlist = readBSM();
    }

    public List<String> getPoolList() {
        return Collections.unmodifiableList(slist);
    }

    public List<String> getBSMList() {
        return Collections.unmodifiableList(bsmlist);
    }

    public String visit(CPInfo c, int index) {
        return c.accept(this, index);
    }

    private List<String> readBSM() {
        BootstrapMethods_attribute bsmAttr =
                (BootstrapMethods_attribute) cf.getAttribute(Attribute.BootstrapMethods);
        if (bsmAttr != null) {
            List<String> out =
                    new ArrayList<>(bsmAttr.bootstrap_method_specifiers.length);
            for (BootstrapMethods_attribute.BootstrapMethodSpecifier bsms :
                    bsmAttr.bootstrap_method_specifiers) {
                int index = bsms.bootstrap_method_ref;
                try {
                    String value = slist.get(index);
                    String bsmStr = value;
                    if (value == null) {
                        value = visit(cfpool.get(index), index);
                        slist.set(index, value);
                    }
                    bsmStr = value;
                    for (int idx : bsms.bootstrap_arguments) {
                        value = slist.get(idx);
                        if (value == null) {
                            value = visit(cfpool.get(idx), idx);
                            slist.set(idx, value);
                        }
                        bsmStr = bsmStr.concat("," + value);
                    }
                    out.add(bsmStr);
                } catch (InvalidIndex ex) {
                    ex.printStackTrace();
                }
            }
            return out;
        }
        return new ArrayList<>(0);
    }

    @Override
    public String visitClass(CONSTANT_Class_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(c.name_index), c.name_index);
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_Class",
                        new String[]{"id", p.toString()},
                        value));
            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitModule(CONSTANT_Module_info info, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(info.name_index), info.name_index);
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_Module",
                        new String[]{"id", p.toString()},
                        value));
            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitPackage(CONSTANT_Package_info info, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(info.name_index), info.name_index);
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_Package",
                        new String[]{"id", p.toString()},
                        value));
            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitDouble(CONSTANT_Double_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            value = Double.toString(c.value);
            slist.set(p, value);
            xpool.add(new Element("CONSTANT_Double",
                      new String[]{"id", p.toString()},
                      value));
        }
        return value;
    }

    @Override
    public String visitFieldref(CONSTANT_Fieldref_info c, Integer p) {
    String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(c.class_index), c.class_index);
                value = value.concat(" " + visit(cfpool.get(c.name_and_type_index),
                                     c.name_and_type_index));
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_Fieldref",
                          new String[]{"id", p.toString()},
                          value));
            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitFloat(CONSTANT_Float_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            value = Float.toString(c.value);
            slist.set(p, value);
            xpool.add(new Element("CONSTANT_Float",
                      new String[]{"id", p.toString()},
                      value));
        }
        return value;
    }

    @Override
    public String visitInteger(CONSTANT_Integer_info cnstnt, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            value = Integer.toString(cnstnt.value);
            slist.set(p, value);
            xpool.add(new Element("CONSTANT_Integer",
                      new String[]{"id", p.toString()},
                      value));
        }
        return value;
    }

    @Override
    public String visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info c,
                                          Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(c.class_index), c.class_index);
                value = value.concat(" " +
                                     visit(cfpool.get(c.name_and_type_index),
                                     c.name_and_type_index));
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_InterfaceMethodref",
                          new String[]{"id", p.toString()},
                          value));

            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitInvokeDynamic(CONSTANT_InvokeDynamic_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = bsmlist.get(c.bootstrap_method_attr_index) + " "
                        + visit(cfpool.get(c.name_and_type_index), c.name_and_type_index);
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_InvokeDynamic",
                          new String[]{"id", p.toString()},
                          value));

            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitDynamicConstant(ConstantPool.CONSTANT_Dynamic_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = bsmlist.get(c.bootstrap_method_attr_index) + " "
                        + visit(cfpool.get(c.name_and_type_index), c.name_and_type_index);
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_Dynamic",
                                      new String[]{"id", p.toString()},
                                      value));

            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitLong(CONSTANT_Long_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            value = Long.toString(c.value);
            slist.set(p, value);
            xpool.add(new Element("CONSTANT_Long",
                      new String[]{"id", p.toString()},
                      value));
        }
        return value;
    }

    @Override
    public String visitNameAndType(CONSTANT_NameAndType_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(c.name_index), c.name_index);
                value = value.concat(" " +
                        visit(cfpool.get(c.type_index), c.type_index));
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_NameAndType",
                          new String[]{"id", p.toString()},
                          value));
            } catch (InvalidIndex ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitMethodref(CONSTANT_Methodref_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(c.class_index), c.class_index);
                value = value.concat(" " +
                                     visit(cfpool.get(c.name_and_type_index),
                                     c.name_and_type_index));
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_Methodref",
                          new String[]{"id", p.toString()},
                          value));

            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitMethodHandle(CONSTANT_MethodHandle_info c, Integer p) {
    String value = slist.get(p);
        if (value == null) {
            try {
                value = c.reference_kind.name();
                value = value.concat(" "
                        + visit(cfpool.get(c.reference_index), c.reference_index));
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_MethodHandle",
                          new String[]{"id", p.toString()},
                          value));

            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitMethodType(CONSTANT_MethodType_info c, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            try {
                value = visit(cfpool.get(c.descriptor_index), c.descriptor_index);
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_MethodType",
                          new String[]{"id", p.toString()},
                          value));
            } catch (ConstantPoolException ex) {
                ex.printStackTrace();
            }
        }
        return value;
    }

    @Override
    public String visitString(CONSTANT_String_info c, Integer p) {
        try {

            String value = slist.get(p);
            if (value == null) {
                value = c.getString();
                slist.set(p, value);
                xpool.add(new Element("CONSTANT_String",
                          new String[]{"id", p.toString()},
                          value));
            }
            return value;
        } catch (ConstantPoolException ex) {
            throw new RuntimeException("Fatal error", ex);
        }
    }

    @Override
    public String  visitUtf8(CONSTANT_Utf8_info cnstnt, Integer p) {
        String value = slist.get(p);
        if (value == null) {
            value = cnstnt.value;
            slist.set(p, value);
            xpool.add(new Element("CONSTANT_Utf8",
                      new String[]{"id", p.toString()},
                      value));
        }
        return value;

    }
}

class AttributeVisitor implements Attribute.Visitor<Element, Element> {
    final ClassFile cf;
    final ClassReader x;
    final AnnotationsElementVisitor aev;
    final InstructionVisitor iv;

    public AttributeVisitor(ClassReader x, ClassFile cf) {
        this.x = x;
        this.cf = cf;
        iv =  new InstructionVisitor(x, cf);
        aev = new AnnotationsElementVisitor(x, cf);
    }

    public void visit(Attribute a, Element parent) {
        a.accept(this, parent);
    }

    @Override
    public Element visitBootstrapMethods(BootstrapMethods_attribute bm, Element p) {
        Element e = new Element(x.getCpString(bm.attribute_name_index));
        for (BootstrapMethods_attribute.BootstrapMethodSpecifier bsm : bm.bootstrap_method_specifiers) {
            Element be = new Element("BootstrapMethodSpecifier");
            be.setAttr("ref", x.getCpString(bsm.bootstrap_method_ref));
            if (bsm.bootstrap_arguments.length > 0) {
                Element bme = new Element("MethodArguments");
                for (int index : bsm.bootstrap_arguments) {
                    bme.add(x.getCpString(index));
                }
                bme.trimToSize();
                be.add(bme);
            }
            be.trimToSize();
            e.add(be);
        }
        e.trimToSize();
        if (!x.keepOrder) {
            e.sort();
        }
        p.add(e);
        return null;
    }

    @Override
    public Element visitDefault(DefaultAttribute da, Element p) {
        Element e = new Element(x.getCpString(da.attribute_name_index));
        StringBuilder sb = new StringBuilder();
        for (byte x : da.info) {
            sb.append("0x").append(Integer.toHexString(x)).append(" ");
        }
        e.setAttr("bytes", sb.toString().trim());
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitAnnotationDefault(AnnotationDefault_attribute ad, Element p) {
        Element e = new Element(x.getCpString(ad.attribute_name_index));
        e.setAttr("tag", "" + ad.default_value.tag);
        Element child = aev.visit(ad.default_value, e);
        if (child != null) {
            e.add(child);
        }
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitCharacterRangeTable(CharacterRangeTable_attribute crt,
                                            Element p) {
        Element e = new Element(x.getCpString(crt.attribute_name_index));
        for (CharacterRangeTable_attribute.Entry ce : crt.character_range_table) {
            e.setAttr("start_pc", "" + ce.start_pc);
            e.setAttr("end_pc", "" + ce.end_pc);
            e.setAttr("range_start", "" + ce.character_range_start);
            e.setAttr("range_end", "" + ce.character_range_end);
            e.setAttr("flags", x.flagString(ce.flags, "Method"));
        }
        e.trimToSize();
        p.add(e);
        return null;
    }

    private Element instructions(Element code, Code_attribute c) {
        Element ielement = new Element("Instructions");
        for (Instruction ins : c.getInstructions()) {
            ielement.add(iv.visit(ins));
        }
        ielement.trimToSize();
        return ielement;
    }

    @Override
    public Element visitCode(Code_attribute c, Element p) {
        Element e = null;

        e = new Element(x.getCpString(c.attribute_name_index),
                "stack", "" + c.max_stack,
                "local", "" + c.max_locals);

        e.add(instructions(e, c));

        for (Code_attribute.Exception_data edata : c.exception_table) {
            e.add(new Element("Handler",
                    "start", "" + edata.start_pc,
                    "end", "" + edata.end_pc,
                    "catch", "" + edata.handler_pc,
                    "class", x.getCpString(edata.catch_type)));

        }
        this.x.readAttributesFor(cf, c.attributes, e);
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitCompilationID(CompilationID_attribute cid, Element p) {
        Element e = new Element(x.getCpString(cid.attribute_name_index),
                x.getCpString(cid.compilationID_index));
        p.add(e);
        return null;
    }

    @Override
    public Element visitModulePackages(ModulePackages_attribute attr, Element p) {
        Element e = new Element(x.getCpString(attr.attribute_name_index));
        for (int i : attr.packages_index) {
            Element ee = new Element("Package");
            String pkg = x.getCpString(i);
            ee.setAttr("package", pkg);
            e.add(ee);
        }
        e.trimToSize();
        e.sort();
        p.add(e);
        return null;
    }

    @Override
    public Element visitConstantValue(ConstantValue_attribute cv, Element p) {
        Element e = new Element(x.getCpString(cv.attribute_name_index));
        e.add(x.getCpString(cv.constantvalue_index));
        p.add(e);
        return null;
    }

    @Override
    public Element visitDeprecated(Deprecated_attribute d, Element p) {
        Element e = new Element(x.getCpString(d.attribute_name_index));
        p.add(e);
        return null;
    }

    @Override
    public Element visitEnclosingMethod(EnclosingMethod_attribute em, Element p) {
        Element e = new Element(x.getCpString(em.attribute_name_index));
        e.setAttr("class", x.getCpString(em.class_index));
        e.setAttr("desc", x.getCpString(em.method_index));
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitExceptions(Exceptions_attribute e, Element p) {
        Element ee = new Element(x.getCpString(e.attribute_name_index));
        for (int idx : e.exception_index_table) {
            Element n = new Element("Item");
            n.setAttr("class", x.getCpString(idx));
            ee.add(n);
        }
        ee.trimToSize();
        p.add(ee);
        return null;
    }

    @Override
    public Element visitInnerClasses(InnerClasses_attribute ic, Element p) {
        for (Info info : ic.classes) {
            Element e = new Element(x.getCpString(ic.attribute_name_index));
            e.setAttr("class", x.getCpString(info.inner_class_info_index));
            e.setAttr("outer", x.getCpString(info.outer_class_info_index));
            e.setAttr("name", x.getCpString(info.inner_name_index));
            e.setAttr("flags", x.flagString(info.inner_class_access_flags,
                    "InnerClass"));
            e.trimToSize();
            p.add(e);
        }
        return null;
    }

    @Override
    public Element visitLineNumberTable(LineNumberTable_attribute lnt, Element p) {
        String name = x.getCpString(lnt.attribute_name_index);
        for (LineNumberTable_attribute.Entry e : lnt.line_number_table) {
            Element l = new Element(name);
            l.setAttr("bci", "" + e.start_pc);
            l.setAttr("line", "" + e.line_number);
            l.trimToSize();
            p.add(l);
        }
        return null; // already added to parent
    }

    @Override
    public Element visitLocalVariableTable(LocalVariableTable_attribute lvt,
                                                Element p) {
        String name = x.getCpString(lvt.attribute_name_index);
        for (LocalVariableTable_attribute.Entry e : lvt.local_variable_table) {
            Element l = new Element(name);
            l.setAttr("bci", "" + e.start_pc);
            l.setAttr("span", "" + e.length);
            l.setAttr("name", x.getCpString(e.name_index));
            l.setAttr("type", x.getCpString(e.descriptor_index));
            l.setAttr("slot", "" + e.index);
            l.trimToSize();
            p.add(l);
        }
        return null; // already added to parent
    }

    private void parseModuleRequires(RequiresEntry[] res, Element p) {
        for (RequiresEntry re : res) {
            Element er = new Element("Requires");
            er.setAttr("module", x.getCpString(re.requires_index));
            er.setAttr("flags", Integer.toString(re.requires_flags));
            p.add(er);
        }
    }

    private void parseModuleExports(ExportsEntry[] exports, Element p) {
        Element ex = new Element("Exports");
        for (ExportsEntry export : exports) {
            Element exto = new Element("exports");
            exto.setAttr("package", x.getCpString(export.exports_index));
            for (int idx : export.exports_to_index) {
                exto.setAttr("module", x.getCpString(idx));
            }
            ex.add(exto);
        }
        p.add(ex);
    }

    private void parseModuleProvides(ProvidesEntry[] provides, Element p) {
        Element ex = new Element("Provides");
        for (ProvidesEntry provide : provides) {
            ex.setAttr("provides", x.getCpString(provide.provides_index));
            for (int idx : provide.with_index) {
                ex.setAttr("with", x.getCpString(idx));
            }
        }
        p.add(ex);
    }

    @Override
    public Element visitModule(Module_attribute m, Element p) {
        Element e = new Element(x.getCpString(m.attribute_name_index));
        parseModuleRequires(m.requires, e);
        parseModuleExports(m.exports, e);
        for (int idx : m.uses_index) {
            Element ei = new Element("Uses");
            ei.setAttr("used_class", x.getCpString(idx));
            e.add(ei);
        }
        parseModuleProvides(m.provides, e);
        p.add(e);
        return null;
    }

    @Override
    public Element visitLocalVariableTypeTable(LocalVariableTypeTable_attribute lvtt,
                                                    Element p) {
        String name = x.getCpString(lvtt.attribute_name_index);
        for (LocalVariableTypeTable_attribute.Entry e : lvtt.local_variable_table) {
            Element l = new Element(name);
            l.setAttr("bci", "" + e.start_pc);
            l.setAttr("span", "" + e.length);
            l.setAttr("name", x.getCpString(e.name_index));
            l.setAttr("type", x.getCpString(e.signature_index));
            l.setAttr("slot", "" + e.index);
            l.trimToSize();
            p.add(l);
        }
        return null; // already added to parent
    }

    @Override
    public Element visitMethodParameters(MethodParameters_attribute mp, Element p) {
        String name = x.getCpString(mp.attribute_name_index);
        for (MethodParameters_attribute.Entry e : mp.method_parameter_table) {
            Element l = new Element(name);
            l.setAttr("name", x.getCpString(e.name_index));
            l.setAttr("flag", "" + e.flags);
            l.trimToSize();
            p.add(l);
        }
        return null; // already added to parent
    }
    private void parseAnnotation(Annotation anno, Element p) {
        Element ea = new Element("Annotation");
        ea.setAttr("name", "" + x.getCpString(anno.type_index));
        for (Annotation.element_value_pair evp : anno.element_value_pairs) {
            Element evpe = new Element("Element");
            evpe.setAttr("tag", "" + evp.value.tag);
            evpe.setAttr("value", x.getCpString(evp.element_name_index));
            Element child = aev.visit(evp.value, evpe);
            if (child != null) {
                evpe.add(child);
            }
            ea.add(evpe);
        }
        ea.trimToSize();
        p.add(ea);
    }

    private void parseAnnotations(Annotation[] ra, Element p) {
        for (Annotation anno : ra) {
            parseAnnotation(anno, p);
        }
    }

    @Override
    public Element visitRuntimeVisibleAnnotations(RuntimeVisibleAnnotations_attribute rva,
                                                  Element p) {
        Element e = new Element(x.getCpString(rva.attribute_name_index));
        parseAnnotations(rva.annotations, e);
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitRuntimeInvisibleAnnotations(RuntimeInvisibleAnnotations_attribute ria,
                                                    Element p) {
        Element e = new Element(x.getCpString(ria.attribute_name_index));
        parseAnnotations(ria.annotations, e);
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitRuntimeVisibleParameterAnnotations(RuntimeVisibleParameterAnnotations_attribute rvpa,
                                                           Element p) {
        Element e = new Element(x.getCpString(rvpa.attribute_name_index));
        for (Annotation[] pa : rvpa.parameter_annotations) {
           parseAnnotations(pa, e);
        }
        p.add(e);
        return null;
    }

    @Override
    public Element visitRuntimeInvisibleParameterAnnotations(RuntimeInvisibleParameterAnnotations_attribute ripa,
                                                             Element p) {
        Element e = new Element(x.getCpString(ripa.attribute_name_index));
        for (Annotation[] pa : ripa.parameter_annotations) {
            parseAnnotations(pa, e);
        }
        p.add(e);
        return null;
    }

    private void parsePosition(Position ap, Element p) {
        Element te = new Element();
        switch (ap.type) {
            case CLASS_TYPE_PARAMETER: // 0x00
                te.setName("CLASS_TYPE_PARAMETER");
                te.setAttr("idx", "" + ap.parameter_index);
                break;
            case METHOD_TYPE_PARAMETER: // 0x01
                te.setName("METHOD_TYPE_PARAMETER");
                te.setAttr("idx", "" + ap.parameter_index);
                break;
            case CLASS_EXTENDS: // 0x10
                te.setName("CLASS_EXTENDS");
                te.setAttr("idx", "" + ap.type_index);
                break;
            case CLASS_TYPE_PARAMETER_BOUND: // 0x11
                te.setName("CLASS_TYPE_PARAMETER_BOUND");
                te.setAttr("idx1", "" + ap.parameter_index);
                te.setAttr("idx2", "" + ap.bound_index);
                break;
            case METHOD_TYPE_PARAMETER_BOUND: // 0x12
                te.setName("METHOD_TYPE_PARAMETER_BOUND");
                te.setAttr("idx1", "" + ap.parameter_index);
                te.setAttr("idx2", "" + ap.bound_index);
                break;
            case FIELD: // 0x13
                te.setName("FIELD");
                break;
            case METHOD_RETURN: // 0x14
                te.setName("METHOD_RETURN");
                break;
            case METHOD_RECEIVER: // 0x15
                te.setName("METHOD_RECEIVER");
                break;
            case METHOD_FORMAL_PARAMETER: // 0x16
                te.setName("METHOD_FORMAL_PARAMETER");
                te.setAttr("idx", "" + ap.parameter_index);
                break;
            case THROWS: // 0x17
                te.setName("THROWS");
                te.setAttr("idx", "" + ap.type_index);
                break;
            case LOCAL_VARIABLE: // 0x40
                te.setName("LOCAL_VARIABLE");
                for (int i = 0; i < ap.lvarIndex.length; i++) {
                    te.setAttr("lvar_idx_" + i, "" + ap.lvarIndex[i]);
                    te.setAttr("lvar_len_" + i, "" + ap.lvarLength[i]);
                    te.setAttr("lvar_off_" + i, "" + ap.lvarOffset[i]);
                }
                break;
            case RESOURCE_VARIABLE: // 0x41
                te.setName("RESOURCE_VARIABLE");
                for (int i = 0; i < ap.lvarIndex.length ; i++) {
                    te.setAttr("lvar_idx_" + i, "" + ap.lvarIndex[i]);
                    te.setAttr("lvar_len_" + i, "" + ap.lvarLength[i]);
                    te.setAttr("lvar_off_" + i, "" + ap.lvarOffset[i]);
                }
                break;
            case EXCEPTION_PARAMETER: // 0x42
                te.setName("EXCEPTION_PARAMETER");
                te.setAttr("idx", "" + ap.exception_index);
                break;
            case INSTANCEOF: // 0x43
                te.setName("INSTANCE_OF");
                te.setAttr("off", "" + ap.offset);
                break;
            case NEW: // 0x44
                te.setName("NEW");
                te.setAttr("off", "" + ap.offset);
                break;
            case CONSTRUCTOR_REFERENCE: // 0x45
                te.setName("CONSTRUCTOR_REFERENCE_RECEIVER");
                te.setAttr("off", "" + ap.offset);
                break;
            case METHOD_REFERENCE: // 0x46
                te.setName("METHOD_REFERENCE_RECEIVER");
                te.setAttr("off", "" + ap.offset);
                break;
            case CAST: // 0x47
                te.setName("CAST");
                te.setAttr("off", "" + ap.offset);
                te.setAttr("idx", "" + ap.type_index);
                break;
            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT: // 0x48
                te.setName("CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT");
                te.setAttr("off", "" + ap.offset);
                te.setAttr("idx", "" + ap.type_index);
                break;
            case METHOD_INVOCATION_TYPE_ARGUMENT: // 0x49
                te.setName("METHOD_INVOCATION_TYPE_ARGUMENT");
                te.setAttr("off", "" + ap.offset);
                te.setAttr("idx", "" + ap.type_index);
                break;
            case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT: // 0x4A
                te.setName("CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT");
                te.setAttr("off", "" + ap.offset);
                te.setAttr("idx", "" + ap.type_index);
                break;
            case METHOD_REFERENCE_TYPE_ARGUMENT: // 0x4B
                te.setName("METHOD_REFERENCE_TYPE_ARGUMENT");
                te.setAttr("off", "" + ap.offset);
                te.setAttr("idx", "" + ap.type_index);
                break;
            default:
                throw new RuntimeException("not implemented");
        }
        te.trimToSize();
        p.add(te);
    }
    private void parseTypeAnnotations(TypeAnnotation pa, Element p) {
        Element pta = new Element("RuntimeVisibleTypeAnnotation");
        p.add(pta);
        Position pos = pa.position;
        parsePosition(pos, pta);
        parseAnnotation(pa.annotation, pta);
    }

    @Override
    public Element visitRuntimeVisibleTypeAnnotations(RuntimeVisibleTypeAnnotations_attribute rvta, Element p) {
        Element e = new Element(x.getCpString(rvta.attribute_name_index));
        for (TypeAnnotation pa : rvta.annotations) {
            parseTypeAnnotations(pa, e);
        }
        e.sort();
        p.add(e);
        return null;
    }

    @Override
    public Element visitRuntimeInvisibleTypeAnnotations(RuntimeInvisibleTypeAnnotations_attribute rita, Element p) {
        Element e = new Element(x.getCpString(rita.attribute_name_index));
        for (TypeAnnotation pa : rita.annotations) {
            parseTypeAnnotations(pa, e);
        }
        e.sort();
        p.add(e);
        return null;
    }

    @Override
    public Element visitSignature(Signature_attribute s, Element p) {
        String aname = x.getCpString(s.attribute_name_index);
        String sname = x.getCpString(s.signature_index);
        Element se = new Element(aname);
        se.add(sname);
        se.trimToSize();
        p.add(se);
        return null;
    }

    @Override
    public Element visitSourceDebugExtension(SourceDebugExtension_attribute sde,
                                                Element p) {
        String aname = x.getCpString(sde.attribute_name_index);
        Element se = new Element(aname);
        se.setAttr("val", sde.getValue());
        se.trimToSize();
        p.add(se);
        return null;
    }

    @Override
    public Element visitSourceFile(SourceFile_attribute sf, Element p) {
        String aname = x.getCpString(sf.attribute_name_index);
        String sname = x.getCpString(sf.sourcefile_index);
        Element se = new Element(aname);
        se.add(sname);
        se.trimToSize();
        p.add(se);
        return null;
    }

    @Override
    public Element visitSourceID(SourceID_attribute sid, Element p) {
        Element e = new Element(x.getCpString(sid.attribute_name_index));
        e.add(x.getCpString(sid.sourceID_index));
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitStackMap(StackMap_attribute sm, Element p) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Element visitStackMapTable(StackMapTable_attribute smt, Element p) {
        Element stackmap = new Element(x.getCpString(smt.attribute_name_index));
        for (StackMapTable_attribute.stack_map_frame f : smt.entries) {
           StackMapVisitor smv = new StackMapVisitor(x, cf, stackmap);
           stackmap.add(smv.visit(f));
        }
        stackmap.trimToSize();
        p.add(stackmap);
        return null;
    }

    @Override
    public Element visitSynthetic(Synthetic_attribute s, Element p) {
        Element e = new Element(x.getCpString(s.attribute_name_index));
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitModuleHashes(ModuleHashes_attribute attr, Element p) {
        Element e = new Element(x.getCpString(attr.attribute_name_index));
        e.setAttr("Algorithm", x.getCpString(attr.algorithm_index));
        for (Entry entry : attr.hashes_table) {
            Element ee = new Element("Entry");
            String mn = x.getCpString(entry.module_name_index);
            ee.setAttr("module_name", mn);
            ee.setAttr("hash_length", "" + entry.hash.length);
            StringBuilder sb = new StringBuilder();
            for (byte b: entry.hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            ee.setAttr("hash", sb.toString());
            ee.trimToSize();
            e.add(ee);
        }
        e.trimToSize();
        e.sort();
        p.add(e);
        return null;
    }

    @Override
    public Element visitModuleMainClass(ModuleMainClass_attribute attr, Element p) {
        Element e = new Element(x.getCpString(attr.attribute_name_index));
        e.add(x.getCpString(attr.main_class_index));
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitModuleResolution(ModuleResolution_attribute attr, Element p) {
        Element e = new Element("ModuleResolution");
        e.setAttr("flags", Integer.toString(attr.resolution_flags));
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitModuleTarget(ModuleTarget_attribute attr, Element p) {
        Element e = new Element(x.getCpString(attr.attribute_name_index));
        e.add(x.getCpString(attr.target_platform_index));
        e.trimToSize();
        p.add(e);
        return null;
    }

    @Override
    public Element visitNestHost(NestHost_attribute attr, Element p) {
        String aname = x.getCpString(attr.attribute_name_index);
        String hname = x.getCpString(attr.top_index);
        Element se = new Element(aname);
        se.add(hname);
        se.trimToSize();
        p.add(se);
        return null;
    }

    @Override
    public Element visitNestMembers(NestMembers_attribute attr, Element p) {
        Element ee = new Element(x.getCpString(attr.attribute_name_index));
        for (int idx : attr.members_indexes) {
            Element n = new Element("Item");
            n.setAttr("class", x.getCpString(idx));
            ee.add(n);
        }
        ee.trimToSize();
        p.add(ee);
        return null;
    }
}

class StackMapVisitor implements StackMapTable_attribute.stack_map_frame.Visitor<Element, Void> {

    final ClassFile cf;
    final ClassReader x;
    final Element parent;

    public StackMapVisitor(ClassReader x, ClassFile cf, Element parent) {
        this.x = x;
        this.cf = cf;
        this.parent = parent;
    }

    public Element visit(StackMapTable_attribute.stack_map_frame frame) {
        return frame.accept(this, null);
    }

    @Override
    public Element visit_same_frame(same_frame sm_frm, Void p) {
        Element e = new Element("SameFrame");
        e.setAttr("tag", "" + sm_frm.frame_type);
        return e;
    }

    @Override
    public Element visit_same_locals_1_stack_item_frame(same_locals_1_stack_item_frame s, Void p) {
        Element e = new Element("SameLocals1StackItemFrame");
        e.setAttr("tag", "" + s.frame_type);
        e.addAll(getVerificationTypeInfo("Stack", s.stack));
        e.trimToSize();
        return e;
    }

    @Override
    public Element visit_same_locals_1_stack_item_frame_extended(same_locals_1_stack_item_frame_extended s, Void p) {
        Element e = new Element("SameLocals1StackItemFrameExtended");
        e.setAttr("tag", "" + s.frame_type);
        e.addAll(getVerificationTypeInfo("Stack", s.stack));
        e.trimToSize();
        return e;
    }

    @Override
    public Element visit_chop_frame(chop_frame c, Void p) {
        Element e = new Element("Chop" + (251 - c.frame_type));
        e.setAttr("tag", "" + c.frame_type);
        e.setAttr("offset", "" + c.offset_delta);
        return e;
    }

    @Override
    public Element visit_same_frame_extended(same_frame_extended s, Void p) {
        Element e = new Element("SameFrameExtended");
        e.setAttr("tag", "" + s.frame_type);
        e.setAttr("offset", "" + s.offset_delta);
        return e;
    }

    @Override
    public Element visit_append_frame(append_frame a, Void p) {
       Element e = new Element("AppendFrame" + (a.frame_type - 251));
       e.setAttr("tag", "" + a.frame_type);
       e.addAll(getVerificationTypeInfo("Local", a.locals));
       e.trimToSize();
       return e;
    }

    @Override
    public Element visit_full_frame(full_frame fl_frm, Void p) {
         Element e = new Element("FullFrame");
         e.setAttr("tag", "" + fl_frm.frame_type);
         e.addAll(getVerificationTypeInfo("Local", fl_frm.locals));
         e.trimToSize();
         return e;
    }

    private Element getVerificationTypeInfo(String kind,
            StackMapTable_attribute.verification_type_info velems[]) {
        Element container = new Element(velems.length);
        for (StackMapTable_attribute.verification_type_info v : velems) {
            Element ve = null;
            int offset = 0;
            int index = 0;
            switch (v.tag) {
                case StackMapTable_attribute.verification_type_info.ITEM_Top:
                    ve = new Element("ITEM_Top");
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_Integer:
                    ve = new Element("ITEM_Integer");
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_Float:
                    ve = new Element("ITEM_Float");
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_Long:
                    ve = new Element("ITEM_Long");
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_Double:
                    ve = new Element("ITEM_Double");
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_Null:
                    ve = new Element("ITEM_Null");
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_Uninitialized:
                    ve = new Element("ITEM_Uninitialized");
                    offset = ((StackMapTable_attribute.Uninitialized_variable_info) v).offset;
                    ve.setAttr("offset", "" + offset);
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_UninitializedThis:
                    ve = new Element("ITEM_UnitializedtThis");
                    break;
                case StackMapTable_attribute.verification_type_info.ITEM_Object:
                    ve = new Element("ITEM_Object");
                    index = ((StackMapTable_attribute.Object_variable_info) v).cpool_index;
                    ve.setAttr("class", x.getCpString(index));
                    break;
                default:
                    ve = new Element("Unknown");
            }
            Element kindE = new Element(kind);
            kindE.setAttr("tag", "" + v.tag);
            container.add(kindE);
            kindE.add(ve);
        }
        container.trimToSize();
        return container;
    }
}

class InstructionVisitor implements Instruction.KindVisitor<Element, Void> {

    final ClassReader x;
    final ClassFile cf;

    public InstructionVisitor(ClassReader x, ClassFile cf) {
        this.x = x;
        this.cf = cf;
    }

    public Element visit(Instruction i) {
        Element ie =  i.accept(this, null);
        ie.trimToSize();
        return ie;
    }

    @Override
    public Element visitNoOperands(Instruction i, Void p) {
        Opcode o = i.getOpcode();
        Element e = new Element(i.getMnemonic());
        if (o.opcode > 0xab && o.opcode <= 0xb1) {
            e.setAttr("pc", "" + i.getPC());
        }
        return e;
    }

    @Override
    public Element visitArrayType(Instruction i, TypeKind tk, Void p) {
        Element ie = new Element(i.getMnemonic());
        ie.setAttr("num", "" + tk.value);
        ie.setAttr("val", tk.name);
        return ie;
    }

    @Override
    public Element visitBranch(Instruction i, int i1, Void p) {
        Element ie = new Element(i.getMnemonic());
        ie.setAttr("lab", "" + (i.getPC() + i1));
        return ie;
    }

    @Override
    public Element visitConstantPoolRef(Instruction i, int i1, Void p) {
        Element ie = new Element(i.getMnemonic());
        ie.setAttr("ref", x.getCpString(i1));
        return ie;
    }

    @Override
    public Element visitConstantPoolRefAndValue(Instruction i, int i1, int i2, Void p) {
        // workaround for a potential bug in classfile
        Element ie = new Element(i.getMnemonic());
        if (i.getOpcode().equals(Opcode.IINC_W)) {
            ie.setAttr("loc", "" + i1);
            ie.setAttr("num", "" + i2);
        } else {
            ie.setAttr("ref", x.getCpString(i1));
            ie.setAttr("val", "" + i2);
        }
        return ie;
    }

    @Override
    public Element visitLocal(Instruction i, int i1, Void p) {
        Element ie = new Element(i.getMnemonic());
        ie.setAttr("loc", "" + i1);
        return ie;
    }

    @Override
    public Element visitLocalAndValue(Instruction i, int i1, int i2, Void p) {
        Element ie = new Element(i.getMnemonic());
        ie.setAttr("loc", "" + i1);
        ie.setAttr("num", "" + i2);
        return ie;
    }

    @Override
    public Element visitLookupSwitch(Instruction i, int i1, int i2, int[] ints,
                                     int[] ints1, Void p) {
        Element ie = new Element(i.getMnemonic());
        int pc = i.getPC();
        ie.setAttr("lab", "" + (pc + i1));
        for (int k = 0 ; k < i2 ; k++) {
            Element c = new Element("Case");
            c.setAttr("num", "" + (ints[k]));
            c.setAttr("lab", "" + (pc + ints1[k]));
            c.trimToSize();
            ie.add(c);
        }
        return ie;
    }

    @Override
    public Element visitTableSwitch(Instruction i, int i1, int i2, int i3,
                                    int[] ints, Void p) {
        Element ie = new Element(i.getMnemonic());
        int pc = i.getPC();
        ie.setAttr("lab", "" + (pc + i1));
        for (int k : ints) {
            Element c = new Element("Case");
            c.setAttr("num", "" + (k + i2));
            c.setAttr("lab", "" + (pc + k));
            c.trimToSize();
            ie.add(c);
        }
        return ie;
    }

    @Override
    public Element visitValue(Instruction i, int i1, Void p) {
        Element ie = new Element(i.getMnemonic());
        ie.setAttr("num", "" + i1);
        return ie;
    }

    @Override
    public Element visitUnknown(Instruction i, Void p) {
        Element e = new Element(i.getMnemonic());
        e.setAttr("pc", "" + i.getPC());
        e.setAttr("opcode", "" + i.getOpcode().opcode);
        return e;
    }
}

class AnnotationsElementVisitor implements Annotation.element_value.Visitor<Element, Element> {
    final ClassReader x;
    final ClassFile cf;

    public AnnotationsElementVisitor(ClassReader x, ClassFile cf) {
        this.x = x;
        this.cf = cf;
    }

    public Element visit(Annotation.element_value v, Element p) {
        return v.accept(this, p);
    }

    @Override
    public Element visitPrimitive(Primitive_element_value e, Element p) {
        Element el = new Element("String");
        el.setAttr("val", x.getCpString(e.const_value_index));
        el.trimToSize();
        return el;
    }

    @Override
    public Element visitEnum(Enum_element_value e, Element p) {
        Element el = new Element("Enum");
        el.setAttr("name", x.getCpString(e.const_name_index));
        el.setAttr("type", x.getCpString(e.type_name_index));
        el.trimToSize();
        return el;
    }

    @Override
    public Element visitClass(Class_element_value c, Element p) {
        Element el = new Element("Class");
        el.setAttr("name", x.getCpString(c.class_info_index));
        el.trimToSize();
        return el;
    }

    @Override
    public Element visitAnnotation(Annotation_element_value a, Element p) {
        Element el = new Element("Annotation");
        Annotation anno = a.annotation_value;
        for (Annotation.element_value_pair evp : anno.element_value_pairs) {
            Element child = visit(evp.value, el);
            if (child != null) {
                el.add(child);
            }
        }
        el.trimToSize();
        return el;
    }

    @Override
    public Element visitArray(Array_element_value a, Element p) {
        Element el = new Element("Array");
        for (Annotation.element_value v : a.values) {
           Element child = visit(v, el);
           if (child != null) {
               el.add(child);
           }
        }
        el.trimToSize();
        return el;
    }
}
