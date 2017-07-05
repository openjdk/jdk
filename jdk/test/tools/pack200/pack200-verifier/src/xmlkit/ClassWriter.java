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
import java.lang.reflect.*;
import java.io.*;
import xmlkit.XMLKit.Element;
/*
 * @author jrose
 */
public class ClassWriter extends ClassSyntax implements ClassSyntax.GetCPIndex {

    private static final CommandLineParser CLP = new CommandLineParser(""
            + "-source:     +>  = \n"
            + "-dest:       +>  = \n"
            + "-encoding:   +>  = \n"
            + "-parseBytes      $ \n"
            + "-               *? \n"
            + "\n");

    public static void main(String[] ava) throws IOException {
        ArrayList<String> av = new ArrayList<String>(Arrays.asList(ava));
        HashMap<String, String> props = new HashMap<String, String>();
        props.put("-encoding:", "UTF8");  // default
        CLP.parse(av, props);
        File source = asFile(props.get("-source:"));
        File dest = asFile(props.get("-dest:"));
        String encoding = props.get("-encoding:");
        boolean parseBytes = props.containsKey("-parseBytes");
        boolean destMade = false;

        for (String a : av) {
            File f;
            File inf = new File(source, a);
            System.out.println("Reading " + inf);
            Element e;
            if (inf.getName().endsWith(".class")) {
                ClassReader cr = new ClassReader();
                cr.parseBytes = parseBytes;
                e = cr.readFrom(inf);
                f = new File(a);
            } else if (inf.getName().endsWith(".xml")) {
                InputStream in = new FileInputStream(inf);
                Reader inw = ClassReader.makeReader(in, encoding);
                e = XMLKit.readFrom(inw);
                e.findAllInTree(XMLKit.and(XMLKit.elementFilter(nonAttrTags()),
                        XMLKit.methodFilter(Element.method("trimText"))));
                //System.out.println(e);
                inw.close();
                f = new File(a.substring(0, a.length() - ".xml".length()) + ".class");
            } else {
                System.out.println("Warning: unknown input " + a);
                continue;
            }
            // Now write it:
            if (!destMade) {
                destMade = true;
                if (dest == null) {
                    dest = File.createTempFile("TestOut", ".dir", new File("."));
                    dest.delete();
                    System.out.println("Writing results to " + dest);
                }
                if (!(dest.isDirectory() || dest.mkdir())) {
                    throw new RuntimeException("Cannot create " + dest);
                }
            }
            File outf = new File(dest, f.isAbsolute() ? f.getName() : f.getPath());
            outf.getParentFile().mkdirs();
            new ClassWriter(e).writeTo(outf);
        }
    }

    private static File asFile(String str) {
        return (str == null) ? null : new File(str);
    }

    public void writeTo(File file) throws IOException {
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            writeTo(out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
    protected String[] callables;     // varies
    protected int cpoolSize = 0;
    protected HashMap<String, String> attrTypesByTag;
    protected OutputStream out;
    protected HashMap<String, int[]> cpMap = new HashMap<String, int[]>();
    protected ArrayList<ByteArrayOutputStream> attrBufs = new ArrayList<ByteArrayOutputStream>();

    private void setupAttrTypes() {
        attrTypesByTag = new HashMap<String, String>();
        for (String key : attrTypes.keySet()) {
            String pfx = key.substring(0, key.indexOf('.') + 1);
            String val = attrTypes.get(key);
            int pos = val.indexOf('<');
            if (pos >= 0) {
                String tag = val.substring(pos + 1, val.indexOf('>', pos));
                attrTypesByTag.put(pfx + tag, key);
            }
        }
        //System.out.println("attrTypesByTag: "+attrTypesByTag);
    }

    protected ByteArrayOutputStream getAttrBuf() {
        int nab = attrBufs.size();
        if (nab == 0) {
            return new ByteArrayOutputStream(1024);
        }
        ByteArrayOutputStream ab = attrBufs.get(nab - 1);
        attrBufs.remove(nab - 1);
        return ab;
    }

    protected void putAttrBuf(ByteArrayOutputStream ab) {
        ab.reset();
        attrBufs.add(ab);
    }

    public ClassWriter(Element root) {
        this(root, null);
    }

    public ClassWriter(Element root, ClassSyntax cr) {
        if (cr != null) {
            attrTypes = cr.attrTypes;
        }
        setupAttrTypes();
        if (root.getName() == "ClassFile") {
            cfile = root;
            cpool = root.findElement("ConstantPool");
            klass = root.findElement("Class");
        } else if (root.getName() == "Class") {
            cfile = new Element("ClassFile",
                    new String[]{
                        "magic", String.valueOf(0xCAFEBABE),
                        "minver", "0", "majver", "46",});
            cpool = new Element("ConstantPool");
            klass = root;
        } else {
            throw new IllegalArgumentException("bad element type " + root.getName());
        }
        if (cpool == null) {
            cpool = new Element("ConstantPool");
        }

        int cpLen = 1 + cpool.size();
        for (Element c : cpool.elements()) {
            int id = (int) c.getAttrLong("id");
            int tag = cpTagValue(c.getName());
            setCPIndex(tag, c.getText().toString(), id);
            switch (tag) {
                case CONSTANT_Long:
                case CONSTANT_Double:
                    cpLen += 1;
            }
        }
        cpoolSize = cpLen;
    }

    public int findCPIndex(int tag, String name) {
        if (name == null) {
            return 0;
        }
        int[] ids = cpMap.get(name.toString());
        return (ids == null) ? 0 : ids[tag];
    }

    public int getCPIndex(int tag, String name) {
        //System.out.println("getCPIndex "+cpTagName(tag)+" "+name);
        if (name == null) {
            return 0;
        }
        int id = findCPIndex(tag, name);
        if (id == 0) {
            id = cpoolSize;
            cpoolSize += 1;
            setCPIndex(tag, name, id);
            cpool.add(new Element(cpTagName(tag),
                    new String[]{"id", "" + id},
                    new Object[]{name}));
            int pos;
            switch (tag) {
                case CONSTANT_Long:
                case CONSTANT_Double:
                    cpoolSize += 1;
                    break;
                case CONSTANT_Class:
                case CONSTANT_String:
                    getCPIndex(CONSTANT_Utf8, name);
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                    pos = name.indexOf(' ');
                    getCPIndex(CONSTANT_Class, name.substring(0, pos));
                    getCPIndex(CONSTANT_NameAndType, name.substring(pos + 1));
                    break;
                case CONSTANT_NameAndType:
                    pos = name.indexOf(' ');
                    getCPIndex(CONSTANT_Utf8, name.substring(0, pos));
                    getCPIndex(CONSTANT_Utf8, name.substring(pos + 1));
                    break;
            }
        }
        return id;
    }

    public void setCPIndex(int tag, String name, int id) {
        //System.out.println("setCPIndex id="+id+" tag="+tag+" name="+name);
        int[] ids = cpMap.get(name);
        if (ids == null) {
            cpMap.put(name, ids = new int[13]);
        }
        if (ids[tag] != 0 && ids[tag] != id) {
            System.out.println("Warning: Duplicate CP entries for " + ids[tag] + " and " + id);
        }
        //assert(ids[tag] == 0 || ids[tag] == id);
        ids[tag] = id;
    }

    public int parseFlags(String flagString) {
        int flags = 0;
        int i = -1;
        for (String[] names : modifierNames) {
            ++i;
            for (String name : names) {
                if (name == null) {
                    continue;
                }
                int pos = flagString.indexOf(name);
                if (pos >= 0) {
                    flags |= (1 << i);
                }
            }
        }
        return flags;
    }

    public void writeTo(OutputStream realOut) throws IOException {
        OutputStream headOut = realOut;
        ByteArrayOutputStream tailOut = new ByteArrayOutputStream();

        // write the body of the class file first
        this.out = tailOut;
        writeClass();

        // write the file header last
        this.out = headOut;
        u4((int) cfile.getAttrLong("magic"));
        u2((int) cfile.getAttrLong("minver"));
        u2((int) cfile.getAttrLong("majver"));
        writeCP();

        // recopy the file tail
        this.out = null;
        tailOut.writeTo(realOut);
    }

    void writeClass() throws IOException {
        int flags = parseFlags(klass.getAttr("flags"));
        flags ^= Modifier.SYNCHRONIZED;
        u2(flags);
        cpRef(CONSTANT_Class, klass.getAttr("name"));
        cpRef(CONSTANT_Class, klass.getAttr("super"));
        Element interfaces = klass.findAllElements("Interface");
        u2(interfaces.size());
        for (Element e : interfaces.elements()) {
            cpRef(CONSTANT_Class, e.getAttr("name"));
        }
        for (int isMethod = 0; isMethod <= 1; isMethod++) {
            Element members = klass.findAllElements(isMethod != 0 ? "Method" : "Field");
            u2(members.size());
            for (Element m : members.elements()) {
                writeMember(m, isMethod != 0);
            }
        }
        writeAttributesFor(klass);
    }

    private void writeMember(Element member, boolean isMethod) throws IOException {
        //System.out.println("writeMember "+member);
        u2(parseFlags(member.getAttr("flags")));
        cpRef(CONSTANT_Utf8, member.getAttr("name"));
        cpRef(CONSTANT_Utf8, member.getAttr("type"));
        writeAttributesFor(member);
    }

    protected void writeAttributesFor(Element x) throws IOException {
        LinkedHashSet<String> attrNames = new LinkedHashSet<String>();
        for (Element e : x.elements()) {
            attrNames.add(e.getName());  // uniquifying
        }
        attrNames.removeAll(nonAttrTags());
        u2(attrNames.size());
        if (attrNames.isEmpty()) {
            return;
        }
        Element prevCurrent;
        if (x.getName() == "Code") {
            prevCurrent = currentCode;
            currentCode = x;
        } else {
            prevCurrent = currentMember;
            currentMember = x;
        }
        OutputStream realOut = this.out;
        for (String utag : attrNames) {
            String qtag = x.getName() + "." + utag;
            String wtag = "*." + utag;
            String key = attrTypesByTag.get(qtag);
            if (key == null) {
                key = attrTypesByTag.get(wtag);
            }
            String type = attrTypes.get(key);
            //System.out.println("tag "+qtag+" => key "+key+"; type "+type);
            Element attrs = x.findAllElements(utag);
            ByteArrayOutputStream attrBuf = getAttrBuf();
            if (type == null) {
                if (attrs.size() != 1 || !attrs.get(0).equals(new Element(utag))) {
                    System.out.println("Warning:  No attribute type description: " + qtag);
                }
                key = wtag;
            } else {
                try {
                    this.out = attrBuf;
                    // unparse according to type desc.
                    if (type.equals("<Code>...")) {
                        writeCode((Element) attrs.get(0));  // assume only 1
                    } else if (type.equals("<Frame>...")) {
                        writeStackMap(attrs, false);
                    } else if (type.equals("<FrameX>...")) {
                        writeStackMap(attrs, true);
                    } else if (type.startsWith("[")) {
                        writeAttributeRecursive(attrs, type);
                    } else {
                        writeAttribute(attrs, type);
                    }
                } finally {
                    //System.out.println("Attr Bytes = \""+attrBuf.toString(EIGHT_BIT_CHAR_ENCODING).replace('"', (char)('"'|0x80))+"\"");
                    this.out = realOut;
                }
            }
            cpRef(CONSTANT_Utf8, key.substring(key.indexOf('.') + 1));
            u4(attrBuf.size());
            attrBuf.writeTo(out);
            putAttrBuf(attrBuf);
        }
        if (x.getName() == "Code") {
            currentCode = prevCurrent;
        } else {
            currentMember = prevCurrent;
        }
    }

    private void writeAttributeRecursive(Element aval, String type) throws IOException {
        assert (callables == null);
        callables = getBodies(type);
        writeAttribute(aval, callables[0]);
        callables = null;
    }

    private void writeAttribute(Element aval, String type) throws IOException {
        //System.out.println("writeAttribute "+aval+"  using "+type);
        String nextAttrName = null;
        boolean afterElemHead = false;
        for (int len = type.length(), next, i = 0; i < len; i = next) {
            int value;
            char intKind;
            int tag;
            int sigChar;
            String attrValue;
            switch (type.charAt(i)) {
                case '<':
                    assert (nextAttrName == null);
                    next = type.indexOf('>', i);
                    String form = type.substring(i + 1, next++);
                    if (form.indexOf('=') < 0) {
                        //  elem_placement = '<' elemname '>'
                        if (aval.isAnonymous()) {
                            assert (aval.size() == 1);
                            aval = (Element) aval.get(0);
                        }
                        assert (aval.getName().equals(form)) : aval + " // " + form;
                        afterElemHead = true;
                    } else {
                        //  attr_placement = '(' attrname '=' (value)? ')'
                        int eqPos = form.indexOf('=');
                        assert (eqPos >= 0);
                        nextAttrName = form.substring(0, eqPos).intern();
                        if (eqPos != form.length() - 1) {
                            // value is implicit, not placed in file
                            nextAttrName = null;
                        }
                        afterElemHead = false;
                    }
                    continue;
                case '(':
                    next = type.indexOf(')', ++i);
                    int callee = Integer.parseInt(type.substring(i, next++));
                    writeAttribute(aval, callables[callee]);
                    continue;
                case 'N': // replication = 'N' int '[' type ... ']'
                {
                    assert (nextAttrName == null);
                    afterElemHead = false;
                    char countType = type.charAt(i + 1);
                    next = i + 2;
                    String type1 = getBody(type, next);
                    Element elems = aval;
                    if (type1.startsWith("<")) {
                        // Select only matching members of aval.
                        String elemName = type1.substring(1, type1.indexOf('>'));
                        elems = aval.findAllElements(elemName);
                    }
                    putInt(elems.size(), countType);
                    next += type1.length() + 2;  // skip body and brackets
                    for (Element elem : elems.elements()) {
                        writeAttribute(elem, type1);
                    }
                }
                continue;
                case 'T': // union = 'T' any_int union_case* '(' ')' '[' body ']'
                    // write the value
                    value = (int) aval.getAttrLong("tag");
                    assert (aval.getAttr("tag") != null) : aval;
                    intKind = type.charAt(++i);
                    if (intKind == 'S') {
                        intKind = type.charAt(++i);
                    }
                    putInt(value, intKind);
                    nextAttrName = null;
                    afterElemHead = false;
                    ++i;  // skip the int type char
                    // union_case = '(' ('-')? digit+ ')' '[' body ']'
                    for (boolean foundCase = false;;) {
                        assert (type.charAt(i) == '(');
                        next = type.indexOf(')', ++i);
                        assert (next >= i);
                        String caseStr = type.substring(i, next++);
                        String type1 = getBody(type, next);
                        next += type1.length() + 2;  // skip body and brackets
                        boolean lastCase = (caseStr.length() == 0);
                        if (!foundCase
                                && (lastCase || matchTag(value, caseStr))) {
                            foundCase = true;
                            // Execute this body.
                            writeAttribute(aval, type1);
                        }
                        if (lastCase) {
                            break;
                        }
                    }
                    continue;
                case 'B':
                case 'H':
                case 'I': // int = oneof "BHI"
                    value = (int) aval.getAttrLong(nextAttrName);
                    intKind = type.charAt(i);
                    next = i + 1;
                    break;
                case 'K':
                    sigChar = type.charAt(i + 1);
                    if (sigChar == 'Q') {
                        assert (currentMember.getName() == "Field");
                        assert (aval.getName() == "ConstantValue");
                        String sig = currentMember.getAttr("type");
                        sigChar = sig.charAt(0);
                        switch (sigChar) {
                            case 'Z':
                            case 'B':
                            case 'C':
                            case 'S':
                                sigChar = 'I';
                                break;
                        }
                    }
                    switch (sigChar) {
                        case 'I':
                            tag = CONSTANT_Integer;
                            break;
                        case 'J':
                            tag = CONSTANT_Long;
                            break;
                        case 'F':
                            tag = CONSTANT_Float;
                            break;
                        case 'D':
                            tag = CONSTANT_Double;
                            break;
                        case 'L':
                            tag = CONSTANT_String;
                            break;
                        default:
                            assert (false);
                            tag = 0;
                    }
                    assert (type.charAt(i + 2) == 'H');  // only H works for now
                    next = i + 3;
                    assert (afterElemHead || nextAttrName != null);
                    //System.out.println("get attr "+nextAttrName+" in "+aval);
                    if (nextAttrName != null) {
                        attrValue = aval.getAttr(nextAttrName);
                        assert (attrValue != null);
                    } else {
                        assert (aval.isText()) : aval;
                        attrValue = aval.getText().toString();
                    }
                    value = getCPIndex(tag, attrValue);
                    intKind = 'H'; //type.charAt(i+2);
                    break;
                case 'R':
                    sigChar = type.charAt(i + 1);
                    switch (sigChar) {
                        case 'C':
                            tag = CONSTANT_Class;
                            break;
                        case 'S':
                            tag = CONSTANT_Utf8;
                            break;
                        case 'D':
                            tag = CONSTANT_Class;
                            break;
                        case 'F':
                            tag = CONSTANT_Fieldref;
                            break;
                        case 'M':
                            tag = CONSTANT_Methodref;
                            break;
                        case 'I':
                            tag = CONSTANT_InterfaceMethodref;
                            break;
                        case 'U':
                            tag = CONSTANT_Utf8;
                            break;
                        //case 'Q': tag = CONSTANT_Class; break;
                        default:
                            assert (false);
                            tag = 0;
                    }
                    assert (type.charAt(i + 2) == 'H');  // only H works for now
                    next = i + 3;
                    assert (afterElemHead || nextAttrName != null);
                    //System.out.println("get attr "+nextAttrName+" in "+aval);
                    if (nextAttrName != null) {
                        attrValue = aval.getAttr(nextAttrName);
                    } else if (aval.hasText()) {
                        attrValue = aval.getText().toString();
                    } else {
                        attrValue = null;
                    }
                    value = getCPIndex(tag, attrValue);
                    intKind = 'H'; //type.charAt(i+2);
                    break;
                case 'P':  // bci = 'P' int
                case 'S':  // signed_int = 'S' int
                    next = i + 2;
                    value = (int) aval.getAttrLong(nextAttrName);
                    intKind = type.charAt(i + 1);
                    break;
                case 'F':
                    next = i + 2;
                    value = parseFlags(aval.getAttr(nextAttrName));
                    intKind = type.charAt(i + 1);
                    break;
                default:
                    throw new RuntimeException("bad attr format '" + type.charAt(i) + "': " + type);
            }
            // write the value
            putInt(value, intKind);
            nextAttrName = null;
            afterElemHead = false;
        }
        assert (nextAttrName == null);
    }

    private void putInt(int x, char ch) throws IOException {
        switch (ch) {
            case 'B':
                u1(x);
                break;
            case 'H':
                u2(x);
                break;
            case 'I':
                u4(x);
                break;
        }
        assert ("BHI".indexOf(ch) >= 0);
    }

    private void writeCode(Element code) throws IOException {
        //System.out.println("writeCode "+code);
        //Element m = new Element(currentMember); m.remove(code);
        //System.out.println("       in "+m);
        int stack = (int) code.getAttrLong("stack");
        int local = (int) code.getAttrLong("local");
        Element bytes = code.findElement("Bytes");
        Element insns = code.findElement("Instructions");
        String bytecodes;
        if (insns == null) {
            bytecodes = bytes.getText().toString();
        } else {
            bytecodes = InstructionSyntax.assemble(insns, this);
            // Cache the assembled bytecodes:
            bytes = new Element("Bytes", (String[]) null, bytecodes);
            code.add(0, bytes);
        }
        u2(stack);
        u2(local);
        int length = bytecodes.length();
        u4(length);
        for (int i = 0; i < length; i++) {
            u1((byte) bytecodes.charAt(i));
        }
        Element handlers = code.findAllElements("Handler");
        u2(handlers.size());
        for (Element handler : handlers.elements()) {
            int start = (int) handler.getAttrLong("start");
            int end = (int) handler.getAttrLong("end");
            int catsh = (int) handler.getAttrLong("catch");
            u2(start);
            u2(end);
            u2(catsh);
            cpRef(CONSTANT_Class, handler.getAttr("class"));
        }
        writeAttributesFor(code);
    }

    protected void writeStackMap(Element attrs, boolean hasXOption) throws IOException {
        Element bytes = currentCode.findElement("Bytes");
        assert (bytes != null && bytes.size() == 1);
        int byteLength = ((String) bytes.get(0)).length();
        boolean uoffsetIsU4 = (byteLength >= (1 << 16));
        boolean ulocalvarIsU4 = currentCode.getAttrLong("local") >= (1 << 16);
        boolean ustackIsU4 = currentCode.getAttrLong("stack") >= (1 << 16);
        if (uoffsetIsU4) {
            u4(attrs.size());
        } else {
            u2(attrs.size());
        }
        for (Element frame : attrs.elements()) {
            int bci = (int) frame.getAttrLong("bci");
            if (uoffsetIsU4) {
                u4(bci);
            } else {
                u2(bci);
            }
            if (hasXOption) {
                u1((int) frame.getAttrLong("flags"));
            }
            // Scan local and stack types in this frame:
            final int LOCALS = 0, STACK = 1;
            for (int j = LOCALS; j <= STACK; j++) {
                Element types = frame.findElement(j == LOCALS ? "Local" : "Stack");
                int typeSize = (types == null) ? 0 : types.size();
                if (j == LOCALS) {
                    if (ulocalvarIsU4) {
                        u4(typeSize);
                    } else {
                        u2(typeSize);
                    }
                } else { // STACK
                    if (ustackIsU4) {
                        u4(typeSize);
                    } else {
                        u2(typeSize);
                    }
                }
                if (types == null) {
                    continue;
                }
                for (Element type : types.elements()) {
                    int tag = itemTagValue(type.getName());
                    u1(tag);
                    switch (tag) {
                        case ITEM_Object:
                            cpRef(CONSTANT_Class, type.getAttr("class"));
                            break;
                        case ITEM_Uninitialized:
                        case ITEM_ReturnAddress: {
                            int offset = (int) type.getAttrLong("bci");
                            if (uoffsetIsU4) {
                                u4(offset);
                            } else {
                                u2(offset);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    public void writeCP() throws IOException {
        int cpLen = cpoolSize;
        u2(cpLen);
        ByteArrayOutputStream buf = getAttrBuf();
        for (Element c : cpool.elements()) {
            if (!c.isText()) {
                System.out.println("## !isText " + c);
            }
            int id = (int) c.getAttrLong("id");
            int tag = cpTagValue(c.getName());
            String name = c.getText().toString();
            int pos;
            u1(tag);
            switch (tag) {
                case CONSTANT_Utf8: {
                    int done = 0;
                    buf.reset();
                    int nameLen = name.length();
                    while (done < nameLen) {
                        int next = name.indexOf((char) 0, done);
                        if (next < 0) {
                            next = nameLen;
                        }
                        if (done < next) {
                            buf.write(name.substring(done, next).getBytes(UTF8_ENCODING));
                        }
                        if (next < nameLen) {
                            buf.write(0300);
                            buf.write(0200);
                            next++;
                        }
                        done = next;
                    }
                    u2(buf.size());
                    buf.writeTo(out);
                }
                break;
                case CONSTANT_Integer:
                    u4(Integer.parseInt(name));
                    break;
                case CONSTANT_Float:
                    u4(Float.floatToIntBits(Float.parseFloat(name)));
                    break;
                case CONSTANT_Long:
                    u8(Long.parseLong(name));
                    //i += 1;  // no need:  extra cp slot is implicit
                    break;
                case CONSTANT_Double:
                    u8(Double.doubleToLongBits(Double.parseDouble(name)));
                    //i += 1;  // no need:  extra cp slot is implicit
                    break;
                case CONSTANT_Class:
                case CONSTANT_String:
                    u2(getCPIndex(CONSTANT_Utf8, name));
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                    pos = name.indexOf(' ');
                    u2(getCPIndex(CONSTANT_Class, name.substring(0, pos)));
                    u2(getCPIndex(CONSTANT_NameAndType, name.substring(pos + 1)));
                    break;
                case CONSTANT_NameAndType:
                    pos = name.indexOf(' ');
                    u2(getCPIndex(CONSTANT_Utf8, name.substring(0, pos)));
                    u2(getCPIndex(CONSTANT_Utf8, name.substring(pos + 1)));
                    break;
            }
        }
        putAttrBuf(buf);
    }

    public void cpRef(int tag, String name) throws IOException {
        u2(getCPIndex(tag, name));
    }

    public void u8(long x) throws IOException {
        u4((int) (x >>> 32));
        u4((int) (x >>> 0));
    }

    public void u4(int x) throws IOException {
        u2(x >>> 16);
        u2(x >>> 0);
    }

    public void u2(int x) throws IOException {
        u1(x >>> 8);
        u1(x >>> 0);
    }

    public void u1(int x) throws IOException {
        out.write(x & 0xFF);
    }
}

