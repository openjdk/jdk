/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
package xmlkit; // -*- mode: java; indent-tabs-mode: nil -*-

import java.util.*;
import java.util.jar.*;
import java.lang.reflect.*;
import java.io.*;
import xmlkit.XMLKit.Element;

/*
 * @author jrose
 */
public class ClassReader extends ClassSyntax {

    private static final CommandLineParser CLP = new CommandLineParser(""
            + "-source:     +> = \n"
            + "-dest:       +> = \n"
            + "-encoding:   +> = \n"
            + "-jcov           $ \n   -nojcov         !-jcov        \n"
            + "-verbose        $ \n   -noverbose      !-verbose     \n"
            + "-pretty         $ \n   -nopretty       !-pretty      \n"
            + "-keepPath       $ \n   -nokeepPath     !-keepPath    \n"
            + "-keepCP         $ \n   -nokeepCP       !-keepCP      \n"
            + "-keepBytes      $ \n   -nokeepBytes    !-keepBytes   \n"
            + "-parseBytes     $ \n   -noparseBytes   !-parseBytes  \n"
            + "-resolveRefs    $ \n   -noresolveRefs  !-resolveRefs \n"
            + "-keepOrder      $ \n   -nokeepOrder    !-keepOrder   \n"
            + "-keepSizes      $ \n   -nokeepSizes    !-keepSizes   \n"
            + "-continue       $ \n   -nocontinue     !-continue    \n"
            + "-attrDef        & \n"
            + "-@         >-@  . \n"
            + "-              +? \n"
            + "\n");

    public static void main(String[] ava) throws IOException {
        ArrayList<String> av = new ArrayList<String>(Arrays.asList(ava));
        HashMap<String, String> props = new HashMap<String, String>();
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
            av.add("doit");  //to enter this loop
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
            boolean contError) throws IOException {
        if (!contError) {
            doFile(a, source, dest, options, encoding);
        } else {
            try {
                doFile(a, source, dest, options, encoding);
            } catch (Exception ee) {
                System.out.println("Error processing " + source + ": " + ee);
            }
        }
    }

    private static void doJar(String a, File source, File dest, ClassReader options,
            String encoding, Boolean contError) throws IOException {
        try {
            JarFile jf = new JarFile(source);
            for (JarEntry je : Collections.list((Enumeration<JarEntry>) jf.entries())) {
                String name = je.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                doStream(name, jf.getInputStream(je), dest, options, encoding);
            }
        } catch (IOException ioe) {
            if (contError) {
                System.out.println("Error processing " + source + ": " + ioe);
            } else {
                throw ioe;
            }
        }
    }

    private static void doStream(String a, InputStream in, File dest,
            ClassReader options, String encoding) throws IOException {

        File f = new File(a);
        ClassReader cr = new ClassReader(options);
        Element e = cr.readFrom(in);

        OutputStream out;
        if (dest == null) {
            //System.out.println(e.prettyString());
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

    public static BufferedReader makeReader(InputStream in, String encoding) throws IOException {
        // encoding in DEFAULT, '', UTF8, 8BIT, , or any valid encoding name
        if (encoding.equals("8BIT")) {
            encoding = EIGHT_BIT_CHAR_ENCODING;
        }
        if (encoding.equals("UTF8")) {
            encoding = UTF8_ENCODING;
        }
        if (encoding.equals("DEFAULT")) {
            encoding = null;
        }
        if (encoding.equals("-")) {
            encoding = null;
        }
        Reader inw;
        in = new BufferedInputStream(in);  // add buffering
        if (encoding == null) {
            inw = new InputStreamReader(in);
        } else {
            inw = new InputStreamReader(in, encoding);
        }
        return new BufferedReader(inw);  // add buffering
    }

    public static Writer makeWriter(OutputStream out, String encoding) throws IOException {
        // encoding in DEFAULT, '', UTF8, 8BIT, , or any valid encoding name
        if (encoding.equals("8BIT")) {
            encoding = EIGHT_BIT_CHAR_ENCODING;
        }
        if (encoding.equals("UTF8")) {
            encoding = UTF8_ENCODING;
        }
        if (encoding.equals("DEFAULT")) {
            encoding = null;
        }
        if (encoding.equals("-")) {
            encoding = null;
        }
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
    protected byte cpTag[];
    protected String cpName[];
    protected String[] callables;     // varies
    public static final String REF_PREFIX = "#";
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
        super.cfile = new Element("ClassFile");
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
        keepBytes = options.keepBytes;
        parseBytes = options.parseBytes;
        resolveRefs = options.resolveRefs;
        keepSizes = options.keepSizes;
        keepOrder = options.keepOrder;
        attrTypes = options.attrTypes;
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
        if (options.containsKey("-keepBytes")) {
            keepBytes = (options.get("-keepBytes") != null);
        }
        if (options.containsKey("-parseBytes")) {
            parseBytes = (options.get("-parseBytes") != null);
        }
        if (options.containsKey("-resolveRefs")) {
            resolveRefs = (options.get("-resolveRefs") != null);
        }
        if (options.containsKey("-keepSizes")) {
            keepSizes = (options.get("-keepSizes") != null);
        }
        if (options.containsKey("-keepOrder")) {
            keepOrder = (options.get("-keepOrder") != null);
        }
        if (options.containsKey("-attrDef")) {
            addAttrTypes(options.get("-attrDef").split(" "));
        }
        if (options.get("-jcov") != null) {
            addJcovAttrTypes();
        }
    }

    public Element readFrom(InputStream in) throws IOException {
        this.in = in;
        // read the file header
        int magic = u4();
        if (magic != 0xCAFEBABE) {
            throw new RuntimeException("bad magic number " + Integer.toHexString(magic));
        }
        cfile.setAttr("magic", "" + magic);
        int minver = u2();
        int majver = u2();
        cfile.setAttr("minver", "" + minver);
        cfile.setAttr("majver", "" + majver);
        readCP();
        readClass();
        return result();
    }

    public Element readFrom(File file) throws IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            Element e = readFrom(new BufferedInputStream(in));
            if (keepPath) {
                e.setAttr("path", file.toString());
            }
            return e;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private void readClass() throws IOException {
        klass = new Element("Class");
        cfile.add(klass);
        int flags = u2();
        String thisk = cpRef();
        String superk = cpRef();
        klass.setAttr("name", thisk);
        boolean flagsSync = ((flags & Modifier.SYNCHRONIZED) != 0);
        flags &= ~Modifier.SYNCHRONIZED;
        String flagString = flagString(flags, klass);
        if (!flagsSync) {
            if (flagString.length() > 0) {
                flagString += " ";
            }
            flagString += "!synchronized";
        }
        klass.setAttr("flags", flagString);
        klass.setAttr("super", superk);
        for (int len = u2(), i = 0; i < len; i++) {
            String interk = cpRef();
            klass.add(new Element("Interface", "name", interk));
        }
        Element fields = readMembers("Field");
        klass.addAll(fields);
        Element methods = readMembers("Method");
        if (!keepOrder) {
            methods.sort();
        }
        klass.addAll(methods);
        readAttributesFor(klass);
        klass.trimToSize();
        if (keepSizes) {
            attachTo(cfile, formatAttrSizes());
        }
        if (paddingSize != 0) {
            cfile.setAttr("padding", "" + paddingSize);
        }
    }

    private Element readMembers(String kind) throws IOException {
        int len = u2();
        Element members = new Element(len);
        for (int i = 0; i < len; i++) {
            Element member = new Element(kind);
            int flags = u2();
            String name = cpRef();
            String type = cpRef();
            member.setAttr("name", name);
            member.setAttr("type", type);
            member.setAttr("flags", flagString(flags, member));
            readAttributesFor(member);
            member.trimToSize();
            members.add(member);
        }
        return members;
    }

    protected String flagString(int flags, Element holder) {
        // Superset of Modifier.toString.
        int kind = 0;
        if (holder.getName() == "Field") {
            kind = 1;
        }
        if (holder.getName() == "Method") {
            kind = 2;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; flags != 0; i++, flags >>>= 1) {
            if ((flags & 1) != 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                if (i < modifierNames.length) {
                    String[] names = modifierNames[i];
                    String name = (kind < names.length) ? names[kind] : null;
                    for (String name2 : names) {
                        if (name != null) {
                            break;
                        }
                        name = name2;
                    }
                    sb.append(name);
                } else {
                    sb.append("#").append(1 << i);
                }
            }
        }
        return sb.toString();
    }

    private void readAttributesFor(Element x) throws IOException {
        Element prevCurrent;
        Element y = new Element();
        if (x.getName() == "Code") {
            prevCurrent = currentCode;
            currentCode = x;
        } else {
            prevCurrent = currentMember;
            currentMember = x;
        }
        for (int len = u2(), i = 0; i < len; i++) {
            int ref = u2();
            String uname = cpName(ref).intern();
            String refName = uname;
            if (!resolveRefs) {
                refName = (REF_PREFIX + ref).intern();
            }
            String qname = (x.getName() + "." + uname).intern();
            String wname = ("*." + uname).intern();
            String type = attrTypes.get(qname);
            if (type == null || "".equals(type)) {
                type = attrTypes.get(wname);
            }
            if ("".equals(type)) {
                type = null;
            }
            int size = u4();
            int[] countVar = attrSizes.get(qname);
            if (countVar == null) {
                attrSizes.put(qname, countVar = new int[2]);
            }
            countVar[0] += 1;
            countVar[1] += size;
            buf.reset();
            for (int j = 0; j < size; j++) {
                buf.write(u1());
            }
            if (type == null && size == 0) {
                y.add(new Element(uname)); // <Bridge>, etc.
            } else if (type == null) {
                //System.out.println("Warning:  No attribute type description: "+qname);
                // write cdata attribute
                Element a = new Element("Attribute",
                        new String[]{"Name", refName},
                        buf.toString(EIGHT_BIT_CHAR_ENCODING));
                a.addContent(getCPDigest());
                y.add(a);
            } else if (type.equals("")) {
                // ignore this attribute...
            } else {
                InputStream in0 = in;
                int fileSize0 = fileSize;
                ByteArrayInputStream in1 = new ByteArrayInputStream(buf.toByteArray());
                boolean ok = false;
                try {
                    in = in1;
                    // parse according to type desc.
                    Element aval;
                    if (type.equals("<Code>...")) {
                        // delve into Code attribute
                        aval = readCode();
                    } else if (type.equals("<Frame>...")) {
                        // delve into StackMap attribute
                        aval = readStackMap(false);
                    } else if (type.equals("<FrameX>...")) {
                        // delve into StackMap attribute
                        aval = readStackMap(true);
                    } else if (type.startsWith("[")) {
                        aval = readAttributeCallables(type);
                    } else {
                        aval = readAttribute(type);
                    }
                    //System.out.println("attachTo 1 "+y+" <- "+aval);
                    attachTo(y, aval);
                    if (false
                            && in1.available() != 0) {
                        throw new RuntimeException("extra bytes in " + qname + " :" + in1.available());
                    }
                    ok = true;
                } finally {
                    in = in0;
                    fileSize = fileSize0;
                    if (!ok) {
                        System.out.println("*** Failed to read " + type);
                    }
                }
            }
        }
        if (x.getName() == "Code") {
            currentCode = prevCurrent;
        } else {
            currentMember = prevCurrent;
        }
        if (!keepOrder) {
            y.sort();
            y.sortAttrs();
        }
        //System.out.println("attachTo 2 "+x+" <- "+y);
        attachTo(x, y);
    }
    private int fileSize = 0;
    private int paddingSize = 0;
    private HashMap<String, int[]> attrSizes = new HashMap<String, int[]>();

    private Element formatAttrSizes() {
        Element e = new Element("Sizes");
        e.setAttr("fileSize", "" + fileSize);
        for (Map.Entry<String, int[]> ie : attrSizes.entrySet()) {
            int[] countVar = ie.getValue();
            e.add(new Element("AttrSize",
                    "name", ie.getKey().toString(),
                    "count", "" + countVar[0],
                    "size", "" + countVar[1]));
        }
        return e;
    }

    private void attachTo(Element x, Object aval0) {
        if (aval0 == null) {
            return;
        }
        //System.out.println("attachTo "+x+" : "+aval0);
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
        //System.out.println("attachAttrTo "+x+" : "+aname+"="+aval);
        String aval0 = x.getAttr(aname);
        if (aval0 != null) {
            aval = aval0 + " " + aval;
        }
        x.setAttr(aname, aval);
    }

    private Element readAttributeCallables(String type) throws IOException {
        assert (callables == null);
        callables = getBodies(type);
        Element res = readAttribute(callables[0]);
        callables = null;
        return res;
    }

    private Element readAttribute(String type) throws IOException {
        //System.out.println("readAttribute "+type);
        Element aval = new Element();
        String nextAttrName = null;
        for (int len = type.length(), next, i = 0; i < len; i = next) {
            String value;
            switch (type.charAt(i)) {
                case '<':
                    assert (nextAttrName == null);
                    next = type.indexOf('>', ++i);
                    String form = type.substring(i, next++);
                    if (form.indexOf('=') < 0) {
                        //  elem_placement = '<' elemname '>'
                        assert (aval.attrSize() == 0);
                        assert (aval.isAnonymous());
                        aval.setName(form.intern());
                    } else {
                        //  attr_placement = '<' attrname '=' (value)? '>'
                        int eqPos = form.indexOf('=');
                        nextAttrName = form.substring(0, eqPos).intern();
                        if (eqPos != form.length() - 1) {
                            value = form.substring(eqPos + 1);
                            attachAttrTo(aval, nextAttrName, value);
                            nextAttrName = null;
                        }
                        // ...else subsequent type parsing will find the attr value
                        // and add it as "nextAttrName".
                    }
                    continue;
                case '(':
                    next = type.indexOf(')', ++i);
                    int callee = Integer.parseInt(type.substring(i, next++));
                    attachTo(aval, readAttribute(callables[callee]));
                    continue;
                case 'N': // replication = 'N' int '[' type ... ']'
                {
                    int count = getInt(type.charAt(i + 1), false);
                    assert (count >= 0);
                    next = i + 2;
                    String type1 = getBody(type, next);
                    next += type1.length() + 2;  // skip body and brackets
                    for (int j = 0; j < count; j++) {
                        attachTo(aval, readAttribute(type1));
                    }
                }
                continue;
                case 'T': // union = 'T' any_int union_case* '(' ')' '[' body ']'
                    int tagValue;
                    if (type.charAt(++i) == 'S') {
                        tagValue = getInt(type.charAt(++i), true);
                    } else {
                        tagValue = getInt(type.charAt(i), false);
                    }
                    attachAttrTo(aval, "tag", "" + tagValue);  // always named "tag"
                    ++i;  // skip the int type char
                    // union_case = '(' uc_tag (',' uc_tag)* ')' '[' body ']'
                    // uc_tag = ('-')? digit+
                    for (boolean foundCase = false;; i = next) {
                        assert (type.charAt(i) == '(');
                        next = type.indexOf(')', ++i);
                        assert (next >= i);
                        if (type.charAt(next - 1) == '\\'
                                && type.charAt(next - 2) != '\\') // Skip an escaped paren.
                        {
                            next = type.indexOf(')', next + 1);
                        }
                        String caseStr = type.substring(i, next++);
                        String type1 = getBody(type, next);
                        next += type1.length() + 2;  // skip body and brackets
                        boolean lastCase = (caseStr.length() == 0);
                        if (!foundCase
                                && (lastCase || matchTag(tagValue, caseStr))) {
                            foundCase = true;
                            // Execute this body.
                            attachTo(aval, readAttribute(type1));
                        }
                        if (lastCase) {
                            break;
                        }
                    }
                    continue;
                case 'B':
                case 'H':
                case 'I': // int = oneof "BHI"
                    next = i + 1;
                    value = "" + getInt(type.charAt(i), false);
                    break;
                case 'K':
                    assert ("IJFDLQ".indexOf(type.charAt(i + 1)) >= 0);
                    assert (type.charAt(i + 2) == 'H');  // only H works for now
                    next = i + 3;
                    value = cpRef();
                    break;
                case 'R':
                    assert ("CSDFMIU?".indexOf(type.charAt(i + 1)) >= 0);
                    assert (type.charAt(i + 2) == 'H');  // only H works for now
                    next = i + 3;
                    value = cpRef();
                    break;
                case 'P':  // bci = 'P' int
                    next = i + 2;
                    value = "" + getInt(type.charAt(i + 1), false);
                    break;
                case 'S':  // signed_int = 'S' int
                    next = i + 2;
                    value = "" + getInt(type.charAt(i + 1), true);
                    break;
                case 'F':
                    next = i + 2;
                    value = flagString(getInt(type.charAt(i + 1), false), currentMember);
                    break;
                default:
                    throw new RuntimeException("bad attr format '" + type.charAt(i) + "': " + type);
            }
            // store the value
            if (nextAttrName != null) {
                attachAttrTo(aval, nextAttrName, value);
                nextAttrName = null;
            } else {
                attachTo(aval, value);
            }
        }
        //System.out.println("readAttribute => "+aval);
        assert (nextAttrName == null);
        return aval;
    }

    private int getInt(char ch, boolean signed) throws IOException {
        if (signed) {
            switch (ch) {
                case 'B':
                    return (byte) u1();
                case 'H':
                    return (short) u2();
                case 'I':
                    return (int) u4();
            }
        } else {
            switch (ch) {
                case 'B':
                    return u1();
                case 'H':
                    return u2();
                case 'I':
                    return u4();
            }
        }
        assert ("BHIJ".indexOf(ch) >= 0);
        return 0;
    }

    private Element readCode() throws IOException {
        int stack = u2();
        int local = u2();
        int length = u4();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) u1());
        }
        String bytecodes = sb.toString();
        Element e = new Element("Code",
                "stack", "" + stack,
                "local", "" + local);
        Element bytes = new Element("Bytes", (String[]) null, bytecodes);
        if (keepBytes) {
            e.add(bytes);
        }
        if (parseBytes) {
            e.add(parseByteCodes(bytecodes));
        }
        for (int len = u2(), i = 0; i < len; i++) {
            int start = u2();
            int end = u2();
            int catsh = u2();
            String clasz = cpRef();
            e.add(new Element("Handler",
                    "start", "" + start,
                    "end", "" + end,
                    "catch", "" + catsh,
                    "class", clasz));
        }
        readAttributesFor(e);
        e.trimToSize();
        return e;
    }

    private Element parseByteCodes(String bytecodes) {
        Element e = InstructionSyntax.parse(bytecodes);
        for (Element ins : e.elements()) {
            Number ref = ins.getAttrNumber("ref");
            if (ref != null && resolveRefs) {
                int id = ref.intValue();
                String val = cpName(id);
                if (ins.getName().startsWith("ldc")) {
                    // Yuck:  Arb. string cannot be an XML attribute.
                    ins.add(val);
                    val = "";
                    byte tag = (id >= 0 && id < cpTag.length) ? cpTag[id] : 0;
                    if (tag != 0) {
                        ins.setAttrLong("tag", tag);
                    }
                }
                if (ins.getName() == "invokeinterface"
                        && computeInterfaceNum(val) == ins.getAttrLong("num")) {
                    ins.setAttr("num", null);  // garbage bytes
                }
                ins.setAttr("ref", null);
                ins.setAttr("val", val);
            }
        }
        return e;
    }

    private Element readStackMap(boolean hasXOption) throws IOException {
        Element result = new Element();
        Element bytes = currentCode.findElement("Bytes");
        assert (bytes != null && bytes.size() == 1);
        int byteLength = ((String) bytes.get(0)).length();
        boolean uoffsetIsU4 = (byteLength >= (1 << 16));
        boolean ulocalvarIsU4 = currentCode.getAttrLong("local") >= (1 << 16);
        boolean ustackIsU4 = currentCode.getAttrLong("stack") >= (1 << 16);
        if (hasXOption || uoffsetIsU4 || ulocalvarIsU4 || ustackIsU4) {
            Element flags = new Element("StackMapFlags");
            if (hasXOption) {
                flags.setAttr("hasXOption", "true");
            }
            if (uoffsetIsU4) {
                flags.setAttr("uoffsetIsU4", "true");
            }
            if (ulocalvarIsU4) {
                flags.setAttr("ulocalvarIsU4", "true");
            }
            if (ustackIsU4) {
                flags.setAttr("ustackIsU4", "true");
            }
            currentCode.add(flags);
        }
        int frame_count = (uoffsetIsU4 ? u4() : u2());
        for (int i = 0; i < frame_count; i++) {
            int bci = (uoffsetIsU4 ? u4() : u2());
            int flags = (hasXOption ? u1() : 0);
            Element frame = new Element("Frame");
            result.add(frame);
            if (flags != 0) {
                frame.setAttr("flags", "" + flags);
            }
            frame.setAttr("bci", "" + bci);
            // Scan local and stack types in this frame:
            final int LOCALS = 0, STACK = 1;
            for (int j = LOCALS; j <= STACK; j++) {
                int typeSize;
                if (j == LOCALS) {
                    typeSize = (ulocalvarIsU4 ? u4() : u2());
                } else { // STACK
                    typeSize = (ustackIsU4 ? u4() : u2());
                }
                Element types = new Element(j == LOCALS ? "Local" : "Stack");
                for (int k = 0; k < typeSize; k++) {
                    int tag = u1();
                    Element type = new Element(itemTagName(tag));
                    types.add(type);
                    switch (tag) {
                        case ITEM_Object:
                            type.setAttr("class", cpRef());
                            break;
                        case ITEM_Uninitialized:
                        case ITEM_ReturnAddress:
                            type.setAttr("bci", "" + (uoffsetIsU4 ? u4() : u2()));
                            break;
                    }
                }
                if (types.size() > 0) {
                    frame.add(types);
                }
            }
        }
        return result;
    }

    private void readCP() throws IOException {
        int cpLen = u2();
        cpTag = new byte[cpLen];
        cpName = new String[cpLen];
        int cpTem[][] = new int[cpLen][];
        for (int i = 1; i < cpLen; i++) {
            cpTag[i] = (byte) u1();
            switch (cpTag[i]) {
                case CONSTANT_Utf8:
                    buf.reset();
                    for (int len = u2(), j = 0; j < len; j++) {
                        buf.write(u1());
                    }
                    cpName[i] = buf.toString(UTF8_ENCODING);
                    break;
                case CONSTANT_Integer:
                    cpName[i] = String.valueOf((int) u4());
                    break;
                case CONSTANT_Float:
                    cpName[i] = String.valueOf(Float.intBitsToFloat(u4()));
                    break;
                case CONSTANT_Long:
                    cpName[i] = String.valueOf(u8());
                    i += 1;
                    break;
                case CONSTANT_Double:
                    cpName[i] = String.valueOf(Double.longBitsToDouble(u8()));
                    i += 1;
                    break;
                case CONSTANT_Class:
                case CONSTANT_String:
                    cpTem[i] = new int[]{u2()};
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                case CONSTANT_NameAndType:
                    cpTem[i] = new int[]{u2(), u2()};
                    break;
            }
        }
        for (int i = 1; i < cpLen; i++) {
            switch (cpTag[i]) {
                case CONSTANT_Class:
                case CONSTANT_String:
                    cpName[i] = cpName[cpTem[i][0]];
                    break;
                case CONSTANT_NameAndType:
                    cpName[i] = cpName[cpTem[i][0]] + " " + cpName[cpTem[i][1]];
                    break;
            }
        }
        // do fieldref et al after nameandtype are all resolved
        for (int i = 1; i < cpLen; i++) {
            switch (cpTag[i]) {
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                    cpName[i] = cpName[cpTem[i][0]] + " " + cpName[cpTem[i][1]];
                    break;
            }
        }
        cpool = new Element("ConstantPool", cpName.length);
        for (int i = 0; i < cpName.length; i++) {
            if (cpName[i] == null) {
                continue;
            }
            cpool.add(new Element(cpTagName(cpTag[i]),
                    new String[]{"id", "" + i},
                    cpName[i]));
        }
        if (keepCP) {
            cfile.add(cpool);
        }
    }

    private String cpRef() throws IOException {
        int ref = u2();
        if (resolveRefs) {
            return cpName(ref);
        } else {
            return REF_PREFIX + ref;
        }
    }

    private String cpName(int id) {
        if (id >= 0 && id < cpName.length) {
            return cpName[id];
        } else {
            return "[CP#" + Integer.toHexString(id) + "]";
        }
    }

    private long u8() throws IOException {
        return ((long) u4() << 32) + (((long) u4() << 32) >>> 32);
    }

    private int u4() throws IOException {
        return (u2() << 16) + u2();
    }

    private int u2() throws IOException {
        return (u1() << 8) + u1();
    }

    private int u1() throws IOException {
        int x = in.read();
        if (x < 0) {
            paddingSize++;
            return 0;  // error recovery
        }
        fileSize++;
        assert (x == (x & 0xFF));
        return x;
    }
}

