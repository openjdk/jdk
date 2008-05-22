/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.tools.javap;

import java.util.Hashtable;
import java.util.Vector;


public class Tables implements Constants {
    /**
     * Define mnemocodes table.
     */
  static  Hashtable<String,Integer> mnemocodes = new Hashtable<String,Integer>(301, 0.5f);
  static  String opcExtNamesTab[]=new String[128];
  static  String opcPrivExtNamesTab[]=new String[128];
  static  void defineNonPriv(int opc, String mnem) {
        mnemocodes.put(opcExtNamesTab[opc]=mnem, opc_nonpriv*256+opc);
  }
  static  void definePriv(int opc, String mnem) {
        mnemocodes.put(opcPrivExtNamesTab[opc]="priv_"+mnem, opc_priv*256+opc);
  }
  static  void defineExt(int opc, String mnem) {
        defineNonPriv(opc, mnem);
        definePriv(opc, mnem);
  }
  static { int k;
        for (k=0; k<opc_wide; k++) {
                mnemocodes.put(opcNamesTab[k], k);
        }
        for (k=opc_wide+1; k<opcNamesTab.length; k++) {
                mnemocodes.put(opcNamesTab[k], k);
        }
        mnemocodes.put("invokenonvirtual", opc_invokespecial);

        mnemocodes.put("iload_w", opc_iload_w);
        mnemocodes.put("lload_w", opc_lload_w);
        mnemocodes.put("fload_w", opc_fload_w);
        mnemocodes.put("dload_w", opc_dload_w);
        mnemocodes.put("aload_w", opc_aload_w);
        mnemocodes.put("istore_w", opc_istore_w);
        mnemocodes.put("lstore_w", opc_lstore_w);
        mnemocodes.put("fstore_w", opc_fstore_w);
        mnemocodes.put("dstore_w", opc_dstore_w);
        mnemocodes.put("astore_w", opc_astore_w);
        mnemocodes.put("ret_w", opc_ret_w);
        mnemocodes.put("iinc_w", opc_iinc_w);

        mnemocodes.put("nonpriv", opc_nonpriv);
        mnemocodes.put("priv", opc_priv);

        defineExt(0, "load_ubyte");
        defineExt(1, "load_byte");
        defineExt(2, "load_char");
        defineExt(3, "load_short");
        defineExt(4, "load_word");
        defineExt(10, "load_char_oe");
        defineExt(11, "load_short_oe");
        defineExt(12, "load_word_oe");
        defineExt(16, "ncload_ubyte");
        defineExt(17, "ncload_byte");
        defineExt(18, "ncload_char");
        defineExt(19, "ncload_short");
        defineExt(20, "ncload_word");
        defineExt(26, "ncload_char_oe");
        defineExt(27, "ncload_short_oe");
        defineExt(28, "ncload_word_oe");
        defineExt(30, "cache_flush");
        defineExt(32, "store_byte");
        defineExt(34, "store_short");
        defineExt(36, "store_word");
        defineExt(42, "store_short_oe");
        defineExt(44, "store_word_oe");
        defineExt(48, "ncstore_byte");
        defineExt(50, "ncstore_short");
        defineExt(52, "ncstore_word");
        defineExt(58, "ncstore_short_oe");
        defineExt(60, "ncstore_word_oe");
        defineExt(62, "zero_line");
        defineNonPriv(5, "ret_from_sub");
        defineNonPriv(63, "enter_sync_method");
        definePriv(5, "ret_from_trap");
        definePriv(6, "read_dcache_tag");
        definePriv(7, "read_dcache_data");
        definePriv(14, "read_icache_tag");
        definePriv(15, "read_icache_data");
        definePriv(22, "powerdown");
        definePriv(23, "read_scache_data");
        definePriv(31, "cache_index_flush");
        definePriv(38, "write_dcache_tag");
        definePriv(39, "write_dcache_data");
        definePriv(46, "write_icache_tag");
        definePriv(47, "write_icache_data");
        definePriv(54, "reset");
        definePriv(55, "write_scache_data");
        for (k=0; k<32; k++) {
                definePriv(k+64, "read_reg_"+k);
        }
        for (k=0; k<32; k++) {
                definePriv(k+96, "write_reg_"+k);
        }
 }

  public static int opcLength(int opc) throws ArrayIndexOutOfBoundsException {
        switch (opc>>8) {
          case 0:
                return opcLengthsTab[opc];
          case opc_wide:
                switch (opc&0xFF) {
                  case opc_aload: case opc_astore:
                  case opc_fload: case opc_fstore:
                  case opc_iload: case opc_istore:
                  case opc_lload: case opc_lstore:
                  case opc_dload: case opc_dstore:
                  case opc_ret:
                        return  4;
                  case opc_iinc:
                        return  6;
                  default:
                        throw new ArrayIndexOutOfBoundsException();
                }
          case opc_nonpriv:
          case opc_priv:
                return 2;
          default:
                throw new ArrayIndexOutOfBoundsException();
        }
  }

  public static String opcName(int opc) {
        try {
                switch (opc>>8) {
                  case 0:
                        return opcNamesTab[opc];
                  case opc_wide: {
                        String mnem=opcNamesTab[opc&0xFF]+"_w";
                        if (mnemocodes.get(mnem) == null)
                                return null; // non-existent opcode
                        return mnem;
                  }
                  case opc_nonpriv:
                        return opcExtNamesTab[opc&0xFF];
                  case opc_priv:
                        return opcPrivExtNamesTab[opc&0xFF];
                  default:
                        return null;
                }
        } catch (ArrayIndexOutOfBoundsException e) {
                switch (opc) {
                  case opc_nonpriv:
                        return "nonpriv";
                  case opc_priv:
                        return "priv";
                  default:
                        return null;
                }
        }
  }

  public static int opcode(String mnem) {
        Integer Val=mnemocodes.get(mnem);
        if (Val == null) return -1;
        return Val.intValue();
  }

    /**
     * Initialized keyword and token Hashtables
     */
  static Vector<String> keywordNames = new Vector<String>(40);
  private static void defineKeywordName(String id, int token) {

        if (token>=keywordNames.size()) {
                keywordNames.setSize(token+1);
        }
        keywordNames.setElementAt(id, token);
  }
  public static String keywordName(int token) {
        if (token==-1) return "EOF";
        if (token>=keywordNames.size()) return null;
        return keywordNames.elementAt(token);
  }
  static {
        defineKeywordName("ident", IDENT);
        defineKeywordName("STRINGVAL", STRINGVAL);
        defineKeywordName("intVal", INTVAL);
        defineKeywordName("longVal", LONGVAL);
        defineKeywordName("floatVal", FLOATVAL);
        defineKeywordName("doubleVal", DOUBLEVAL);
        defineKeywordName("SEMICOLON", SEMICOLON);
        defineKeywordName("COLON", COLON);
        defineKeywordName("LBRACE", LBRACE);
        defineKeywordName("RBRACE", RBRACE);
  }

  static Hashtable<String,Integer> keywords = new Hashtable<String,Integer>(40);
  public static int keyword(String idValue) {
        Integer val=keywords.get(idValue);
        if (val == null) return IDENT;
        return val.intValue();
  }

  private static void defineKeyword(String id, int token) {
        keywords.put(id, token);
        defineKeywordName(id, token);
  }
  static {
        // Modifier keywords
        defineKeyword("private", PRIVATE);
        defineKeyword("public", PUBLIC);
        defineKeyword("protected",      PROTECTED);
        defineKeyword("static", STATIC);
        defineKeyword("transient",      TRANSIENT);
        defineKeyword("synchronized",   SYNCHRONIZED);
        defineKeyword("super",  SUPER);
        defineKeyword("native", NATIVE);
        defineKeyword("abstract",       ABSTRACT);
        defineKeyword("volatile", VOLATILE);
        defineKeyword("final",  FINAL);
        defineKeyword("interface",INTERFACE);
        defineKeyword("synthetic",SYNTHETIC);
        defineKeyword("strict",STRICT);

        // Declaration keywords
        defineKeyword("package",PACKAGE);
        defineKeyword("class",CLASS);
        defineKeyword("extends",EXTENDS);
        defineKeyword("implements",IMPLEMENTS);
        defineKeyword("const",  CONST);
        defineKeyword("throws",THROWS);
        defineKeyword("interface",INTERFACE);
        defineKeyword("Method",METHODREF);
        defineKeyword("Field",FIELDREF);
        defineKeyword("stack",STACK);
        defineKeyword("locals",LOCAL);

        // used in switchtables
        defineKeyword("default",        DEFAULT);

        // used in inner class declarations
        defineKeyword("InnerClass",     INNERCLASS);
        defineKeyword("of",     OF);

        // misc
        defineKeyword("bits",BITS);
        defineKeyword("Infinity",INF);
        defineKeyword("Inf",INF);
        defineKeyword("NaN",NAN);
  }

   /**
     * Define tag table.
     */
  private static Vector<String> tagNames = new Vector<String>(10);
  private static Hashtable<String,Integer> Tags = new Hashtable<String,Integer>(10);
  static {
        defineTag("Asciz",CONSTANT_UTF8);
        defineTag("int",CONSTANT_INTEGER);
        defineTag("float",CONSTANT_FLOAT);
        defineTag("long",CONSTANT_LONG);
        defineTag("double",CONSTANT_DOUBLE);
        defineTag("class",CONSTANT_CLASS);
        defineTag("String",CONSTANT_STRING);
        defineTag("Field",CONSTANT_FIELD);
        defineTag("Method",CONSTANT_METHOD);
        defineTag("InterfaceMethod",CONSTANT_INTERFACEMETHOD);
        defineTag("NameAndType",CONSTANT_NAMEANDTYPE);
  }
  private static void defineTag(String id, int val) {
        Tags.put(id, val);
        if (val>=tagNames.size()) {
                tagNames.setSize(val+1);
        }
        tagNames.setElementAt(id, val);
  }
  public static String tagName(int tag) {
        if (tag>=tagNames.size()) return null;
        return tagNames.elementAt(tag);
  }
  public static int tagValue(String idValue) {
        Integer Val=Tags.get(idValue);
        if (Val == null) return 0;
        return Val.intValue();
  }

   /**
     * Define type table. These types used in "newarray" instruction only.
     */
  private static Vector<String> typeNames = new Vector<String>(10);
  private static Hashtable<String,Integer> Types = new Hashtable<String,Integer>(10);
  static {
        defineType("int",T_INT);
        defineType("long",T_LONG);
        defineType("float",T_FLOAT);
        defineType("double",T_DOUBLE);
        defineType("class",T_CLASS);
        defineType("boolean",T_BOOLEAN);
        defineType("char",T_CHAR);
        defineType("byte",T_BYTE);
        defineType("short",T_SHORT);
  }
  private static void defineType(String id, int val) {
        Types.put(id, val);
        if (val>=typeNames.size()) {
                typeNames.setSize(val+1);
        }
        typeNames.setElementAt(id, val);
  }
  public static int typeValue(String idValue) {
        Integer Val=Types.get(idValue);
        if (Val == null) return -1;
        return Val.intValue();
  }
  public static String typeName(int type) {
        if (type>=typeNames.size()) return null;
        return typeNames.elementAt(type);
  }

   /**
     * Define MapTypes table.
     * These constants used in stackmap tables only.
     */
  private static Vector<String> mapTypeNames = new Vector<String>(10);
  private static Hashtable<String,Integer> MapTypes = new Hashtable<String,Integer>(10);
  static {
        defineMapType("bogus",             ITEM_Bogus);
        defineMapType("int",               ITEM_Integer);
        defineMapType("float",             ITEM_Float);
        defineMapType("double",            ITEM_Double);
        defineMapType("long",              ITEM_Long);
        defineMapType("null",              ITEM_Null);
        defineMapType("this",              ITEM_InitObject);
        defineMapType("CP",                ITEM_Object);
        defineMapType("uninitialized",     ITEM_NewObject);
  }
  private static void defineMapType(String id, int val) {
        MapTypes.put(id, val);
        if (val>=mapTypeNames.size()) {
                mapTypeNames.setSize(val+1);
        }
        mapTypeNames.setElementAt(id, val);
  }
  public static int mapTypeValue(String idValue) {
        Integer Val=MapTypes.get(idValue);
        if (Val == null) return -1;
        return Val.intValue();
  }
  public static String mapTypeName(int type) {
        if (type>=mapTypeNames.size()) return null;
        return mapTypeNames.elementAt(type);
  }

}
