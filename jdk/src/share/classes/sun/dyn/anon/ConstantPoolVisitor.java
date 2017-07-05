/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.dyn.anon;

/**
 * A visitor called by {@link ConstantPoolParser#parse(ConstantPoolVisitor)}
 * when a constant pool entry is parsed.
 * <p>
 * A visit* method is called when a constant pool entry is parsed.
 * The first argument is always the constant pool index.
 * The second argument is always the constant pool tag,
 * even for methods like {@link #visitUTF8(int, byte, String)} which only apply to one tag.
 * String arguments refer to Utf8 or NameAndType entries declared elsewhere,
 * and are always accompanied by the indexes of those entries.
 * <p>
 * The order of the calls to the visit* methods is not necessarily related
 * to the order of the entries in the constant pool.
 * If one entry has a reference to another entry, the latter (lower-level)
 * entry will be visited first.
 * <p>
 * The following table shows the relation between constant pool entry
 * types and the corresponding visit* methods:
 *
 * <table border=1 cellpadding=5 summary="constant pool visitor methods">
 * <tr><th>Tag(s)</th><th>Method</th></tr>
 * <tr>
 *   <td>{@link #CONSTANT_Utf8}</td>
 *   <td>{@link #visitUTF8(int, byte, String)}</td>
 * </tr><tr>
 *   <td>{@link #CONSTANT_Integer}, {@link #CONSTANT_Float},
 *       {@link #CONSTANT_Long}, {@link #CONSTANT_Double}</td>
 *   <td>{@link #visitConstantValue(int, byte, Object)}</td>
 * </tr><tr>
 *   <td>{@link #CONSTANT_String}, {@link #CONSTANT_Class}</td>
 *   <td>{@link #visitConstantString(int, byte, String, int)}</td>
 * </tr><tr>
 *   <td>{@link #CONSTANT_NameAndType}</td>
 *   <td>{@link #visitDescriptor(int, byte, String, String, int, int)}</td>
 * </tr><tr>
 *   <td>{@link #CONSTANT_Fieldref},
 *       {@link #CONSTANT_Methodref},
 *       {@link #CONSTANT_InterfaceMethodref}</td>
 *   <td>{@link #visitMemberRef(int, byte, String, String, String, int, int)}</td>
 * </tr>
 * </table>
 *
 * @see ConstantPoolPatch
 * @author Remi Forax
 * @author jrose
 */
public class ConstantPoolVisitor {
  /** Called each time an UTF8 constant pool entry is found.
   * @param index the constant pool index
   * @param tag always {@link #CONSTANT_Utf8}
   * @param utf8 string encoded in modified UTF-8 format passed as a {@code String}
   *
   * @see ConstantPoolPatch#putUTF8(int, String)
   */
  public void visitUTF8(int index, byte tag, String utf8) {
    // do nothing
  }

  /** Called for each constant pool entry that encodes an integer,
   *  a float, a long, or a double.
   *  Constant strings and classes are not managed by this method but
   *  by {@link #visitConstantString(int, byte, String, int)}.
   *
   * @param index the constant pool index
   * @param tag one of {@link #CONSTANT_Integer},
   *            {@link #CONSTANT_Float},
   *            {@link #CONSTANT_Long},
   *            or {@link #CONSTANT_Double}
   * @param value encoded value
   *
   * @see ConstantPoolPatch#putConstantValue(int, Object)
   */
  public void visitConstantValue(int index, byte tag, Object value) {
    // do nothing
  }

  /** Called for each constant pool entry that encodes a string or a class.
   * @param index the constant pool index
   * @param tag one of {@link #CONSTANT_String},
   *            {@link #CONSTANT_Class},
   * @param name string body or class name (using dot separator)
   * @param nameIndex the index of the Utf8 string for the name
   *
   * @see ConstantPoolPatch#putConstantValue(int, byte, Object)
   */
  public void visitConstantString(int index, byte tag,
                                  String name, int nameIndex) {
    // do nothing
  }

  /** Called for each constant pool entry that encodes a name and type.
   * @param index the constant pool index
   * @param tag always {@link #CONSTANT_NameAndType}
   * @param memberName a field or method name
   * @param signature the member signature
   * @param memberNameIndex index of the Utf8 string for the member name
   * @param signatureIndex index of the Utf8 string for the signature
   *
   * @see ConstantPoolPatch#putDescriptor(int, String, String)
   */
  public void visitDescriptor(int index, byte tag,
                              String memberName, String signature,
                              int memberNameIndex, int signatureIndex) {
    // do nothing
  }

  /** Called for each constant pool entry that encodes a field or method.
   * @param index the constant pool index
   * @param tag one of {@link #CONSTANT_Fieldref},
   *            or {@link #CONSTANT_Methodref},
   *            or {@link #CONSTANT_InterfaceMethodref}
   * @param className the class name (using dot separator)
   * @param memberName name of the field or method
   * @param signature the field or method signature
   * @param classNameIndex index of the Utf8 string for the class name
   * @param descriptorIndex index of the NameAndType descriptor constant
   *
   * @see ConstantPoolPatch#putMemberRef(int, byte, String, String, String)
   */
  public void visitMemberRef(int index, byte tag,
                             String className, String memberName, String signature,
                             int classNameIndex, int descriptorIndex) {
    // do nothing
  }

    public static final byte
      CONSTANT_None = 0,
      CONSTANT_Utf8 = 1,
      //CONSTANT_Unicode = 2,               /* unused */
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

    private static String[] TAG_NAMES = {
        "Empty",
        "Utf8",
        null, //"Unicode",
        "Integer",
        "Float",
        "Long",
        "Double",
        "Class",
        "String",
        "Fieldref",
        "Methodref",
        "InterfaceMethodref",
        "NameAndType"
    };

    public static String tagName(byte tag) {
        String name = null;
        if ((tag & 0xFF) < TAG_NAMES.length)
            name = TAG_NAMES[tag];
        if (name == null)
            name = "Unknown#"+(tag&0xFF);
        return name;
    }
}
