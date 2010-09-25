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
import xmlkit.XMLKit.*;

import java.util.*;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import xmlkit.XMLKit.Element;
/*
 * @author jrose
 */
public abstract class ClassSyntax {

    public interface GetCPIndex {

        int getCPIndex(int tag, String name);  // cp finder
    }
    public static final int CONSTANT_Utf8 = 1,
            CONSTANT_Integer = 3,
            CONSTANT_Float = 4,
            CONSTANT_Long = 5,
            CONSTANT_Double = 6,
            CONSTANT_Class = 7,
            CONSTANT_String = 8,
            CONSTANT_Fieldref = 9,
            CONSTANT_Methodref = 10,
            CONSTANT_InterfaceMethodref = 11,
            CONSTANT_NameAndType = 12;
    private static final String[] cpTagName = {
        /* 0:  */null,
        /* 1:  */ "Utf8",
        /* 2:  */ null,
        /* 3:  */ "Integer",
        /* 4:  */ "Float",
        /* 5:  */ "Long",
        /* 6:  */ "Double",
        /* 7:  */ "Class",
        /* 8:  */ "String",
        /* 9:  */ "Fieldref",
        /* 10: */ "Methodref",
        /* 11: */ "InterfaceMethodref",
        /* 12: */ "NameAndType",
        null
    };
    private static final Set<String> cpTagNames;

    static {
        Set<String> set = new HashSet<String>(Arrays.asList(cpTagName));
        set.remove(null);
        cpTagNames = Collections.unmodifiableSet(set);
    }
    public static final int ITEM_Top = 0, // replicates by [1..4,1..4]
            ITEM_Integer = 1, // (ditto)
            ITEM_Float = 2,
            ITEM_Double = 3,
            ITEM_Long = 4,
            ITEM_Null = 5,
            ITEM_UninitializedThis = 6,
            ITEM_Object = 7,
            ITEM_Uninitialized = 8,
            ITEM_ReturnAddress = 9,
            ITEM_LIMIT = 10;
    private static final String[] itemTagName = {
        "Top",
        "Integer",
        "Float",
        "Double",
        "Long",
        "Null",
        "UninitializedThis",
        "Object",
        "Uninitialized",
        "ReturnAddress",};
    private static final Set<String> itemTagNames;

    static {
        Set<String> set = new HashSet<String>(Arrays.asList(itemTagName));
        set.remove(null);
        itemTagNames = Collections.unmodifiableSet(set);
    }
    protected static final HashMap<String, String> attrTypesBacking;
    protected static final Map<String, String> attrTypesInit;

    static {
        HashMap<String, String> at = new HashMap<String, String>();

        //at.put("*.Deprecated", "<deprecated=true>");
        //at.put("*.Synthetic", "<synthetic=true>");
        ////at.put("Field.ConstantValue", "<constantValue=>KQH");
        //at.put("Class.SourceFile", "<sourceFile=>RUH");
        at.put("Method.Bridge", "<Bridge>");
        at.put("Method.Varargs", "<Varargs>");
        at.put("Class.Enum", "<Enum>");
        at.put("*.Signature", "<Signature>RSH");
        //at.put("*.Deprecated", "<Deprecated>");
        //at.put("*.Synthetic", "<Synthetic>");
        at.put("Field.ConstantValue", "<ConstantValue>KQH");
        at.put("Class.SourceFile", "<SourceFile>RUH");
        at.put("Class.InnerClasses", "NH[<InnerClass><class=>RCH<outer=>RCH<name=>RUH<flags=>FH]");
        at.put("Code.LineNumberTable", "NH[<LineNumber><bci=>PH<line=>H]");
        at.put("Code.LocalVariableTable", "NH[<LocalVariable><bci=>PH<span=>H<name=>RUH<type=>RSH<slot=>H]");
        at.put("Code.LocalVariableTypeTable", "NH[<LocalVariableType><bci=>PH<span=>H<name=>RUH<type=>RSH<slot=>H]");
        at.put("Method.Exceptions", "NH[<Exception><name=>RCH]");
        at.put("Method.Code", "<Code>...");
        at.put("Code.StackMapTable", "<Frame>...");
        //at.put("Code.StkMapX", "<FrameX>...");
        if (true) {
            at.put("Code.StackMapTable",
                    "[NH[<Frame>(1)]]"
                    + "[TB"
                    + "(64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79"
                    + ",80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95"
                    + ",96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111"
                    + ",112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127"
                    + ")[<SameLocals1StackItemFrame>(4)]"
                    + "(247)[<SameLocals1StackItemExtended>H(4)]"
                    + "(248)[<Chop3>H]"
                    + "(249)[<Chop2>H]"
                    + "(250)[<Chop1>H]"
                    + "(251)[<SameFrameExtended>H]"
                    + "(252)[<Append1>H(4)]"
                    + "(253)[<Append2>H(4)(4)]"
                    + "(254)[<Append3>H(4)(4)(4)]"
                    + "(255)[<FullFrame>H(2)(3)]"
                    + "()[<SameFrame>]]"
                    + "[NH[<Local>(4)]]"
                    + "[NH[<Stack>(4)]]"
                    + "[TB"
                    + ("(0)[<Top>]"
                    + "(1)[<ItemInteger>](2)[<ItemFloat>](3)[<ItemDouble>](4)[<ItemLong>]"
                    + "(5)[<ItemNull>](6)[<ItemUninitializedThis>]"
                    + "(7)[<ItemObject><class=>RCH]"
                    + "(8)[<ItemUninitialized><bci=>PH]"
                    + "()[<ItemUnknown>]]"));
        }

        at.put("Class.EnclosingMethod", "<EnclosingMethod><class=>RCH<desc=>RDH");//RDNH

        // Layouts of metadata attrs:
        String vpf = "[<RuntimeVisibleAnnotation>";
        String ipf = "[<RuntimeInvisibleAnnotation>";
        String apf = "[<Annotation>";
        String mdanno2 = ""
                + "<type=>RSHNH[<Member><name=>RUH(3)]]"
                + ("[TB"
                + "(\\B,\\C,\\I,\\S,\\Z)[<value=>KIH]"
                + "(\\D)[<value=>KDH]"
                + "(\\F)[<value=>KFH]"
                + "(\\J)[<value=>KJH]"
                + "(\\c)[<class=>RSH]"
                + "(\\e)[<type=>RSH<name=>RUH]"
                + "(\\s)[<String>RUH]"
                + "(\\@)[(2)]"
                + "(\\[)[NH[<Element>(3)]]"
                + "()[]"
                + "]");
        String visanno = "[NH[(2)]][(1)]" + vpf + mdanno2;
        String invanno = "[NH[(2)]][(1)]" + ipf + mdanno2;
        String vparamanno = ""
                + "[NB[<RuntimeVisibleParameterAnnotation>(1)]][NH[(2)]]"
                + apf + mdanno2;
        String iparamanno = ""
                + "[NB[<RuntimeInvisibleParameterAnnotation>(1)]][NH[(2)]]"
                + apf + mdanno2;
        String mdannodef = "[<AnnotationDefault>(3)][(1)]" + apf + mdanno2;
        String[] mdplaces = {"Class", "Field", "Method"};
        for (String place : mdplaces) {
            at.put(place + ".RuntimeVisibleAnnotations", visanno);
            at.put(place + ".RuntimeInvisibleAnnotations", invanno);
        }
        at.put("Method.RuntimeVisibleParameterAnnotations", vparamanno);
        at.put("Method.RuntimeInvisibleParameterAnnotations", iparamanno);
        at.put("Method.AnnotationDefault", mdannodef);

        attrTypesBacking = at;
        attrTypesInit = Collections.unmodifiableMap(at);
    }

    ;
    private static final String[] jcovAttrTypes = {
        "Code.CoverageTable=NH[<Coverage><bci=>PH<type=>H<line=>I<pos=>I]",
        "Code.CharacterRangeTable=NH[<CharacterRange><bci=>PH<endbci=>POH<from=>I<to=>I<flag=>H]",
        "Class.SourceID=<SourceID><id=>RUH",
        "Class.CompilationID=<CompilationID><id=>RUH"
    };
    protected static final String[][] modifierNames = {
        {"public"},
        {"private"},
        {"protected"},
        {"static"},
        {"final"},
        {"synchronized"},
        {null, "volatile", "bridge"},
        {null, "transient", "varargs"},
        {null, null, "native"},
        {"interface"},
        {"abstract"},
        {"strictfp"},
        {"synthetic"},
        {"annotation"},
        {"enum"},};
    protected static final String EIGHT_BIT_CHAR_ENCODING = "ISO8859_1";
    protected static final String UTF8_ENCODING = "UTF8";
    // What XML tags are used by this syntax, apart from attributes?
    protected static final Set<String> nonAttrTags;

    static {
        HashSet<String> tagSet = new HashSet<String>();
        Collections.addAll(tagSet, new String[]{
                    "ConstantPool",// the CP
                    "Class", // the class
                    "Interface", // implemented interfaces
                    "Method", // methods
                    "Field", // fields
                    "Handler", // exception handler pseudo-attribute
                    "Attribute", // unparsed attribute
                    "Bytes", // bytecodes
                    "Instructions" // bytecodes, parsed
                });
        nonAttrTags = Collections.unmodifiableSet(tagSet);
    }

    // Accessors.
    public static Set<String> nonAttrTags() {
        return nonAttrTags;
    }

    public static String cpTagName(int t) {
        t &= 0xFF;
        String ts = null;
        if (t < cpTagName.length) {
            ts = cpTagName[t];
        }
        if (ts != null) {
            return ts;
        }
        return ("UnknownTag" + (int) t).intern();
    }

    public static int cpTagValue(String name) {
        for (int t = 0; t < cpTagName.length; t++) {
            if (name.equals(cpTagName[t])) {
                return t;
            }
        }
        return 0;
    }

    public static String itemTagName(int t) {
        t &= 0xFF;
        String ts = null;
        if (t < itemTagName.length) {
            ts = itemTagName[t];
        }
        if (ts != null) {
            return ts;
        }
        return ("UnknownItem" + (int) t).intern();
    }

    public static int itemTagValue(String name) {
        for (int t = 0; t < itemTagName.length; t++) {
            if (name.equals(itemTagName[t])) {
                return t;
            }
        }
        return -1;
    }

    public void addJcovAttrTypes() {
        addAttrTypes(jcovAttrTypes);
    }
    // Public methods for declaring attribute types.
    protected Map<String, String> attrTypes = attrTypesInit;

    public void addAttrType(String opt) {
        int eqpos = opt.indexOf('=');
        addAttrType(opt.substring(0, eqpos), opt.substring(eqpos + 1));
    }

    public void addAttrTypes(String[] opts) {
        for (String opt : opts) {
            addAttrType(opt);
        }
    }

    private void checkAttr(String attr) {
        if (!attr.startsWith("Class.")
                && !attr.startsWith("Field.")
                && !attr.startsWith("Method.")
                && !attr.startsWith("Code.")
                && !attr.startsWith("*.")) {
            throw new IllegalArgumentException("attr name must start with 'Class.', etc.");
        }
        String uattr = attr.substring(attr.indexOf('.') + 1);
        if (nonAttrTags.contains(uattr)) {
            throw new IllegalArgumentException("attr name must not be one of " + nonAttrTags);
        }
    }

    private void checkAttrs(Map<String, String> at) {
        for (String attr : at.keySet()) {
            checkAttr(attr);
        }
    }

    private void modAttrs() {
        if (attrTypes == attrTypesInit) {
            // Make modifiable.
            attrTypes = new HashMap<String, String>(attrTypesBacking);
        }
    }

    public void addAttrType(String attr, String fmt) {
        checkAttr(attr);
        modAttrs();
        attrTypes.put(attr, fmt);
    }

    public void addAttrTypes(Map<String, String> at) {
        checkAttrs(at);
        modAttrs();
        attrTypes.putAll(at);
    }

    public Map<String, String> getAttrTypes() {
        if (attrTypes == attrTypesInit) {
            return attrTypes;
        }
        return Collections.unmodifiableMap(attrTypes);
    }

    public void setAttrTypes(Map<String, String> at) {
        checkAttrs(at);
        modAttrs();
        attrTypes.keySet().retainAll(at.keySet());
        attrTypes.putAll(at);
    }

    // attr format helpers
    protected static boolean matchTag(int tagValue, String caseStr) {
        //System.out.println("matchTag "+tagValue+" in "+caseStr);
        for (int pos = 0, max = caseStr.length(), comma;
                pos < max;
                pos = comma + 1) {
            int caseValue;
            if (caseStr.charAt(pos) == '\\') {
                caseValue = caseStr.charAt(pos + 1);
                comma = pos + 2;
                assert (comma == max || caseStr.charAt(comma) == ',');
            } else {
                comma = caseStr.indexOf(',', pos);
                if (comma < 0) {
                    comma = max;
                }
                caseValue = Integer.parseInt(caseStr.substring(pos, comma));
            }
            if (tagValue == caseValue) {
                return true;
            }
        }
        return false;
    }

    protected static String[] getBodies(String type) {
        ArrayList<String> bodies = new ArrayList<String>();
        for (int i = 0; i < type.length();) {
            String body = getBody(type, i);
            bodies.add(body);
            i += body.length() + 2;  // skip body and brackets
        }
        return bodies.toArray(new String[bodies.size()]);
    }

    protected static String getBody(String type, int i) {
        assert (type.charAt(i) == '[');
        int next = ++i;  // skip bracket
        for (int depth = 1; depth > 0; next++) {
            switch (type.charAt(next)) {
                case '[':
                    depth++;
                    break;
                case ']':
                    depth--;
                    break;
                case '(':
                    next = type.indexOf(')', next);
                    break;
                case '<':
                    next = type.indexOf('>', next);
                    break;
            }
            assert (next > 0);
        }
        --next;  // get before bracket
        assert (type.charAt(next) == ']');
        return type.substring(i, next);
    }

    public Element makeCPDigest(int length) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (java.security.NoSuchAlgorithmException ee) {
            throw new Error(ee);
        }
        int items = 0;
        for (Element e : cpool.elements()) {
            if (items == length) {
                break;
            }
            if (cpTagNames.contains(e.getName())) {
                items += 1;
                md.update((byte) cpTagValue(e.getName()));
                try {
                    md.update(e.getText().toString().getBytes(UTF8_ENCODING));
                } catch (java.io.UnsupportedEncodingException ee) {
                    throw new Error(ee);
                }
            }
        }
        ByteBuffer bb = ByteBuffer.wrap(md.digest());
        String l0 = Long.toHexString(bb.getLong(0));
        String l1 = Long.toHexString(bb.getLong(8));
        while (l0.length() < 16) {
            l0 = "0" + l0;
        }
        while (l1.length() < 16) {
            l1 = "0" + l1;
        }
        return new Element("Digest",
                "length", "" + items,
                "bytes", l0 + l1);
    }

    public Element getCPDigest(int length) {
        if (length == -1) {
            length = cpool.countAll(XMLKit.elementFilter(cpTagNames));
        }
        for (Element md : cpool.findAllElements("Digest").elements()) {
            if (md.getAttrLong("length") == length) {
                return md;
            }
        }
        Element md = makeCPDigest(length);
        cpool.add(md);
        return md;
    }

    public Element getCPDigest() {
        return getCPDigest(-1);
    }

    public boolean checkCPDigest(Element md) {
        return md.equals(getCPDigest((int) md.getAttrLong("length")));
    }

    public static int computeInterfaceNum(String intMethRef) {
        intMethRef = intMethRef.substring(1 + intMethRef.lastIndexOf(' '));
        if (!intMethRef.startsWith("(")) {
            return -1;
        }
        int signum = 1;  // start with one for "this"
        scanSig:
        for (int i = 1; i < intMethRef.length(); i++) {
            char ch = intMethRef.charAt(i);
            signum++;
            switch (ch) {
                case ')':
                    --signum;
                    break scanSig;
                case 'L':
                    i = intMethRef.indexOf(';', i);
                    break;
                case '[':
                    while (ch == '[') {
                        ch = intMethRef.charAt(++i);
                    }
                    if (ch == 'L') {
                        i = intMethRef.indexOf(';', i);
                    }
                    break;
            }
        }
        int num = (signum << 8) | 0;
        //System.out.println("computeInterfaceNum "+intMethRef+" => "+num);
        return num;
    }
    // Protected state for representing the class file.
    protected Element cfile;          // <ClassFile ...>
    protected Element cpool;          // <ConstantPool ...>
    protected Element klass;          // <Class ...>
    protected Element currentMember;  // varies during scans
    protected Element currentCode;    // varies during scans
}
