/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.invoke.anon;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;

import static sun.invoke.anon.ConstantPoolVisitor.*;

/** A class and its patched constant pool.
 *
 *  This class allow to modify (patch) a constant pool
 *  by changing the value of its entry.
 *  Entry are referenced using index that can be get
 *  by parsing the constant pool using
 *  {@link ConstantPoolParser#parse(ConstantPoolVisitor)}.
 *
 * @see ConstantPoolVisitor
 * @see ConstantPoolParser#createPatch()
 */
public class ConstantPoolPatch {
    final ConstantPoolParser outer;
    final Object[] patchArray;

    ConstantPoolPatch(ConstantPoolParser outer) {
        this.outer      = outer;
        this.patchArray = new Object[outer.getLength()];
    }

    /** Create a {@link ConstantPoolParser} and
     *  a {@link ConstantPoolPatch} in one step.
     *  Equivalent to {@code new ConstantPoolParser(classFile).createPatch()}.
     *
     * @param classFile an array of bytes containing a class.
     * @see #ConstantPoolParser(Class)
     */
    public ConstantPoolPatch(byte[] classFile) throws InvalidConstantPoolFormatException {
        this(new ConstantPoolParser(classFile));
    }

    /** Create a {@link ConstantPoolParser} and
     *  a {@link ConstantPoolPatch} in one step.
     *  Equivalent to {@code new ConstantPoolParser(templateClass).createPatch()}.
     *
     * @param templateClass the class to parse.
     * @see #ConstantPoolParser(Class)
     */
    public ConstantPoolPatch(Class<?> templateClass) throws IOException, InvalidConstantPoolFormatException {
        this(new ConstantPoolParser(templateClass));
    }


    /** Creates a patch from an existing patch.
     *  All changes are copied from that patch.
     * @param patch a patch
     *
     * @see ConstantPoolParser#createPatch()
     */
    public ConstantPoolPatch(ConstantPoolPatch patch) {
        outer      = patch.outer;
        patchArray = patch.patchArray.clone();
    }

    /** Which parser built this patch? */
    public ConstantPoolParser getParser() {
        return outer;
    }

    /** Report the tag at the given index in the constant pool. */
    public byte getTag(int index) {
        return outer.getTag(index);
    }

    /** Report the current patch at the given index of the constant pool.
     *  Null means no patch will be made.
     *  To observe the unpatched entry at the given index, use
     *  {@link #getParser()}{@code .}@link ConstantPoolParser#parse(ConstantPoolVisitor)}
     */
    public Object getPatch(int index) {
        Object value = patchArray[index];
        if (value == null)  return null;
        switch (getTag(index)) {
        case CONSTANT_Fieldref:
        case CONSTANT_Methodref:
        case CONSTANT_InterfaceMethodref:
            if (value instanceof String)
                value = stripSemis(2, (String) value);
            break;
        case CONSTANT_NameAndType:
            if (value instanceof String)
                value = stripSemis(1, (String) value);
            break;
        }
        return value;
    }

    /** Clear all patches. */
    public void clear() {
        Arrays.fill(patchArray, null);
    }

    /** Clear one patch. */
    public void clear(int index) {
        patchArray[index] = null;
    }

    /** Produce the patches as an array. */
    public Object[] getPatches() {
        return patchArray.clone();
    }

    /** Produce the original constant pool as an array. */
    public Object[] getOriginalCP() throws InvalidConstantPoolFormatException {
        return getOriginalCP(0, patchArray.length, -1);
    }

    /** Walk the constant pool, applying patches using the given map.
     *
     * @param utf8Map Utf8 strings to modify, if encountered
     * @param classMap Classes (or their names) to modify, if encountered
     * @param valueMap Constant values to modify, if encountered
     * @param deleteUsedEntries if true, delete map entries that are used
     */
    public void putPatches(final Map<String,String> utf8Map,
                           final Map<String,Object> classMap,
                           final Map<Object,Object> valueMap,
                           boolean deleteUsedEntries) throws InvalidConstantPoolFormatException {
        final HashSet<String> usedUtf8Keys;
        final HashSet<String> usedClassKeys;
        final HashSet<Object> usedValueKeys;
        if (deleteUsedEntries) {
            usedUtf8Keys  = (utf8Map  == null) ? null : new HashSet<String>();
            usedClassKeys = (classMap == null) ? null : new HashSet<String>();
            usedValueKeys = (valueMap == null) ? null : new HashSet<Object>();
        } else {
            usedUtf8Keys = null;
            usedClassKeys = null;
            usedValueKeys = null;
        }

        outer.parse(new ConstantPoolVisitor() {

            @Override
            public void visitUTF8(int index, byte tag, String utf8) {
                putUTF8(index, utf8Map.get(utf8));
                if (usedUtf8Keys != null)  usedUtf8Keys.add(utf8);
            }

            @Override
            public void visitConstantValue(int index, byte tag, Object value) {
                putConstantValue(index, tag, valueMap.get(value));
                if (usedValueKeys != null)  usedValueKeys.add(value);
            }

            @Override
            public void visitConstantString(int index, byte tag, String name, int nameIndex) {
                if (tag == CONSTANT_Class) {
                    putConstantValue(index, tag, classMap.get(name));
                    if (usedClassKeys != null)  usedClassKeys.add(name);
                } else {
                    assert(tag == CONSTANT_String);
                    visitConstantValue(index, tag, name);
                }
            }
        });
        if (usedUtf8Keys != null)   utf8Map.keySet().removeAll(usedUtf8Keys);
        if (usedClassKeys != null)  classMap.keySet().removeAll(usedClassKeys);
        if (usedValueKeys != null)  valueMap.keySet().removeAll(usedValueKeys);
    }

    Object[] getOriginalCP(final int startIndex,
                           final int endIndex,
                           final int tagMask) throws InvalidConstantPoolFormatException {
        final Object[] cpArray = new Object[endIndex - startIndex];
        outer.parse(new ConstantPoolVisitor() {

            void show(int index, byte tag, Object value) {
                if (index < startIndex || index >= endIndex)  return;
                if (((1 << tag) & tagMask) == 0)  return;
                cpArray[index - startIndex] = value;
            }

            @Override
            public void visitUTF8(int index, byte tag, String utf8) {
                show(index, tag, utf8);
            }

            @Override
            public void visitConstantValue(int index, byte tag, Object value) {
                assert(tag != CONSTANT_String);
                show(index, tag, value);
            }

            @Override
            public void visitConstantString(int index, byte tag,
                                            String value, int j) {
                show(index, tag, value);
            }

            @Override
            public void visitMemberRef(int index, byte tag,
                    String className, String memberName,
                    String signature,
                    int j, int k) {
                show(index, tag, new String[]{ className, memberName, signature });
            }

            @Override
            public void visitDescriptor(int index, byte tag,
                    String memberName, String signature,
                    int j, int k) {
                show(index, tag, new String[]{ memberName, signature });
            }
        });
        return cpArray;
    }

    /** Write the head (header plus constant pool)
     *  of the patched class file to the indicated stream.
     */
    void writeHead(OutputStream out) throws IOException {
        outer.writePatchedHead(out, patchArray);
    }

    /** Write the tail (everything after the constant pool)
     *  of the patched class file to the indicated stream.
     */
    void writeTail(OutputStream out) throws IOException {
        outer.writeTail(out);
    }

    private void checkConstantTag(byte tag, Object value) {
        if (value == null)
            throw new IllegalArgumentException(
                    "invalid null constant value");
        if (classForTag(tag) != value.getClass())
            throw new IllegalArgumentException(
                    "invalid constant value"
                    + (tag == CONSTANT_None ? ""
                        : " for tag "+tagName(tag))
                    + " of class "+value.getClass());
    }

    private void checkTag(int index, byte putTag) {
        byte tag = outer.tags[index];
        if (tag != putTag)
            throw new IllegalArgumentException(
                "invalid put operation"
                + " for " + tagName(putTag)
                + " at index " + index + " found " + tagName(tag));
    }

    private void checkTagMask(int index, int tagBitMask) {
        byte tag = outer.tags[index];
        int tagBit = ((tag & 0x1F) == tag) ? (1 << tag) : 0;
        if ((tagBit & tagBitMask) == 0)
            throw new IllegalArgumentException(
                "invalid put operation"
                + " at index " + index + " found " + tagName(tag));
    }

    private static void checkMemberName(String memberName) {
        if (memberName.indexOf(';') >= 0)
            throw new IllegalArgumentException("memberName " + memberName + " contains a ';'");
    }

    /** Set the entry of the constant pool indexed by index to
     *  a new string.
     *
     * @param index an index to a constant pool entry containing a
     *        {@link ConstantPoolVisitor#CONSTANT_Utf8} value.
     * @param utf8 a string
     *
     * @see ConstantPoolVisitor#visitUTF8(int, byte, String)
     */
    public void putUTF8(int index, String utf8) {
        if (utf8 == null) { clear(index); return; }
        checkTag(index, CONSTANT_Utf8);
        patchArray[index] = utf8;
    }

    /** Set the entry of the constant pool indexed by index to
     *  a new value, depending on its dynamic type.
     *
     * @param index an index to a constant pool entry containing a
     *        one of the following structures:
     *        {@link ConstantPoolVisitor#CONSTANT_Integer},
     *        {@link ConstantPoolVisitor#CONSTANT_Float},
     *        {@link ConstantPoolVisitor#CONSTANT_Long},
     *        {@link ConstantPoolVisitor#CONSTANT_Double},
     *        {@link ConstantPoolVisitor#CONSTANT_String}, or
     *        {@link ConstantPoolVisitor#CONSTANT_Class}
     * @param value a boxed int, float, long or double; or a string or class object
     * @throws IllegalArgumentException if the type of the constant does not
     *         match the constant pool entry type,
     *         as reported by {@link #getTag(int)}
     *
     * @see #putConstantValue(int, byte, Object)
     * @see ConstantPoolVisitor#visitConstantValue(int, byte, Object)
     * @see ConstantPoolVisitor#visitConstantString(int, byte, String, int)
     */
    public void putConstantValue(int index, Object value) {
        if (value == null) { clear(index); return; }
        byte tag = tagForConstant(value.getClass());
        checkConstantTag(tag, value);
        checkTag(index, tag);
        patchArray[index] = value;
    }

    /** Set the entry of the constant pool indexed by index to
     *  a new value.
     *
     * @param index an index to a constant pool entry matching the given tag
     * @param tag one of the following values:
     *        {@link ConstantPoolVisitor#CONSTANT_Integer},
     *        {@link ConstantPoolVisitor#CONSTANT_Float},
     *        {@link ConstantPoolVisitor#CONSTANT_Long},
     *        {@link ConstantPoolVisitor#CONSTANT_Double},
     *        {@link ConstantPoolVisitor#CONSTANT_String}, or
     *        {@link ConstantPoolVisitor#CONSTANT_Class}
     * @param value a boxed number, string, or class object
     * @throws IllegalArgumentException if the type of the constant does not
     *         match the constant pool entry type, or if a class name contains
     *         '/' or ';'
     *
     * @see #putConstantValue(int, Object)
     * @see ConstantPoolVisitor#visitConstantValue(int, byte, Object)
     * @see ConstantPoolVisitor#visitConstantString(int, byte, String, int)
     */
    public void putConstantValue(int index, byte tag, Object value) {
        if (value == null) { clear(index); return; }
        checkTag(index, tag);
        if (tag == CONSTANT_Class && value instanceof String) {
            checkClassName((String) value);
        } else if (tag == CONSTANT_String) {
            // the JVM accepts any object as a patch for a string
        } else {
            // make sure the incoming value is the right type
            checkConstantTag(tag, value);
        }
        checkTag(index, tag);
        patchArray[index] = value;
    }

    /** Set the entry of the constant pool indexed by index to
     *  a new {@link ConstantPoolVisitor#CONSTANT_NameAndType} value.
     *
     * @param index an index to a constant pool entry containing a
     *        {@link ConstantPoolVisitor#CONSTANT_NameAndType} value.
     * @param memberName a memberName
     * @param signature a signature
     * @throws IllegalArgumentException if memberName contains the character ';'
     *
     * @see ConstantPoolVisitor#visitDescriptor(int, byte, String, String, int, int)
     */
    public void putDescriptor(int index, String memberName, String signature) {
        checkTag(index, CONSTANT_NameAndType);
        checkMemberName(memberName);
        patchArray[index] = addSemis(memberName, signature);
    }

    /** Set the entry of the constant pool indexed by index to
     *  a new {@link ConstantPoolVisitor#CONSTANT_Fieldref},
     *  {@link ConstantPoolVisitor#CONSTANT_Methodref}, or
     *  {@link ConstantPoolVisitor#CONSTANT_InterfaceMethodref} value.
     *
     * @param index an index to a constant pool entry containing a member reference
     * @param className a class name
     * @param memberName a field or method name
     * @param signature a field or method signature
     * @throws IllegalArgumentException if memberName contains the character ';'
     *             or signature is not a correct signature
     *
     * @see ConstantPoolVisitor#visitMemberRef(int, byte, String, String, String, int, int)
     */
    public void putMemberRef(int index, byte tag,
                    String className, String memberName, String signature) {
        checkTagMask(tag, CONSTANT_MemberRef_MASK);
        checkTag(index, tag);
        checkClassName(className);
        checkMemberName(memberName);
        if (signature.startsWith("(") == (tag == CONSTANT_Fieldref))
            throw new IllegalArgumentException("bad signature: "+signature);
        patchArray[index] = addSemis(className, memberName, signature);
    }

    static private final int CONSTANT_MemberRef_MASK =
              CONSTANT_Fieldref
            | CONSTANT_Methodref
            | CONSTANT_InterfaceMethodref;

    private static final Map<Class<?>, Byte> CONSTANT_VALUE_CLASS_TAG
        = new IdentityHashMap<Class<?>, Byte>();
    private static final Class<?>[] CONSTANT_VALUE_CLASS = new Class<?>[16];
    static {
        Object[][] values = {
            {Integer.class, CONSTANT_Integer},
            {Long.class, CONSTANT_Long},
            {Float.class, CONSTANT_Float},
            {Double.class, CONSTANT_Double},
            {String.class, CONSTANT_String},
            {Class.class, CONSTANT_Class}
        };
        for (Object[] value : values) {
            Class<?> cls = (Class<?>)value[0];
            Byte     tag = (Byte) value[1];
            CONSTANT_VALUE_CLASS_TAG.put(cls, tag);
            CONSTANT_VALUE_CLASS[(byte)tag] = cls;
        }
    }

    static Class<?> classForTag(byte tag) {
        if ((tag & 0xFF) >= CONSTANT_VALUE_CLASS.length)
            return null;
        return CONSTANT_VALUE_CLASS[tag];
    }

    static byte tagForConstant(Class<?> cls) {
        Byte tag = CONSTANT_VALUE_CLASS_TAG.get(cls);
        return (tag == null) ? CONSTANT_None : (byte)tag;
    }

    private static void checkClassName(String className) {
        if (className.indexOf('/') >= 0 || className.indexOf(';') >= 0)
            throw new IllegalArgumentException("invalid class name " + className);
    }

    static String addSemis(String name, String... names) {
        StringBuilder buf = new StringBuilder(name.length() * 5);
        buf.append(name);
        for (String name2 : names) {
            buf.append(';').append(name2);
        }
        String res = buf.toString();
        assert(stripSemis(names.length, res)[0].equals(name));
        assert(stripSemis(names.length, res)[1].equals(names[0]));
        assert(names.length == 1 ||
               stripSemis(names.length, res)[2].equals(names[1]));
        return res;
    }

    static String[] stripSemis(int count, String string) {
        String[] res = new String[count+1];
        int pos = 0;
        for (int i = 0; i < count; i++) {
            int pos2 = string.indexOf(';', pos);
            if (pos2 < 0)  pos2 = string.length();  // yuck
            res[i] = string.substring(pos, pos2);
            pos = pos2;
        }
        res[count] = string.substring(pos);
        return res;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(this.getClass().getName());
        buf.append("{");
        Object[] origCP = null;
        for (int i = 0; i < patchArray.length; i++) {
            if (patchArray[i] == null)  continue;
            if (origCP != null) {
                buf.append(", ");
            } else {
                try {
                    origCP = getOriginalCP();
                } catch (InvalidConstantPoolFormatException ee) {
                    origCP = new Object[0];
                }
            }
            Object orig = (i < origCP.length) ? origCP[i] : "?";
            buf.append(orig).append("=").append(patchArray[i]);
        }
        buf.append("}");
        return buf.toString();
    }
}
