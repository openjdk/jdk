/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.classfile.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class Klass extends Metadata implements ClassConstants {

  public static class KlassKind {

    public static KlassKind InstanceKlassKind;
    public static KlassKind InlineKlassKind;
    public static KlassKind InstanceRefKlassKind;
    public static KlassKind InstanceMirrorKlassKind;
    public static KlassKind InstanceClassLoaderKlassKind;
    public static KlassKind InstanceStackChunkKlassKind;
    public static KlassKind TypeArrayKlassKind;
    public static KlassKind ObjArrayKlassKind;
    public static KlassKind RefArrayKlassKind;
    public static KlassKind FlatArrayKlassKind;
    public static KlassKind UnknownKlassKind;

    private final int value;

    static {
      VM.registerVMInitializedObserver((_, _) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
      Type type = db.lookupType("Klass::KlassKind");

      InstanceKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::InstanceKlassKind").intValue());
      InlineKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::InlineKlassKind").intValue());
      InstanceRefKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::InstanceRefKlassKind").intValue());
      InstanceMirrorKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::InstanceMirrorKlassKind").intValue());
      InstanceClassLoaderKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::InstanceClassLoaderKlassKind").intValue());
      InstanceStackChunkKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::InstanceStackChunkKlassKind").intValue());
      TypeArrayKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::TypeArrayKlassKind").intValue());
      ObjArrayKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::ObjArrayKlassKind").intValue());
      RefArrayKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::RefArrayKlassKind").intValue());
      FlatArrayKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::FlatArrayKlassKind").intValue());
      UnknownKlassKind = new KlassKind(db.lookupIntConstant("Klass::KlassKind::UnknownKlassKind").intValue());
    }

    private KlassKind(int value) {
      this.value = value;
    }

    public static KlassKind valueOf(int rawValue) {
      if (rawValue == InstanceKlassKind.value) {
        return InstanceKlassKind;
      } else if (rawValue == InlineKlassKind.value) {
        return InlineKlassKind;
      } else if (rawValue == InstanceRefKlassKind.value) {
        return InstanceRefKlassKind;
      } else if (rawValue == InstanceMirrorKlassKind.value) {
        return InstanceMirrorKlassKind;
      } else if (rawValue == InstanceClassLoaderKlassKind.value) {
        return InstanceClassLoaderKlassKind;
      } else if (rawValue == InstanceStackChunkKlassKind.value) {
        return InstanceStackChunkKlassKind;
      } else if (rawValue == TypeArrayKlassKind.value) {
        return TypeArrayKlassKind;
      } else if (rawValue == ObjArrayKlassKind.value) {
        return ObjArrayKlassKind;
      } else if (rawValue == RefArrayKlassKind.value) {
        return RefArrayKlassKind;
      } else if (rawValue == FlatArrayKlassKind.value) {
        return FlatArrayKlassKind;
      } else if (rawValue == UnknownKlassKind.value) {
        return UnknownKlassKind;
      } else {
        throw new IllegalArgumentException("Out of range");
      }
    }

    @Override
    public boolean equals(Object obj) {
      if ((obj != null) && (obj instanceof KlassKind k)) {
          return k.value == this.value;
      }
      return false;
    }
  }

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  // anon-enum constants for _layout_helper.
  public static int LH_INSTANCE_SLOW_PATH_BIT;
  public static int LH_LOG2_ELEMENT_SIZE_SHIFT;
  public static int LH_ELEMENT_TYPE_SHIFT;
  public static int LH_HEADER_SIZE_SHIFT;
  public static int LH_ARRAY_TAG_SHIFT;
  public static int LH_ARRAY_TAG_TYPE_VALUE;


  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type    = db.lookupType("Klass");
    javaMirrorFieldOffset = type.getField("_java_mirror").getOffset();
    kindField    = new CIntField(type.getCIntegerField("_kind"), 0);
    superField   = new MetadataField(type.getAddressField("_super"), 0);
    layoutHelper = new IntField(type.getJIntField("_layout_helper"), 0);
    name         = type.getAddressField("_name");
    try {
      traceIDField  = type.getField("_trace_id");
    } catch(Exception e) {
    }
    subklass     = new MetadataField(type.getAddressField("_subklass"), 0);
    nextSibling  = new MetadataField(type.getAddressField("_next_sibling"), 0);
    nextLink     = new MetadataField(type.getAddressField("_next_link"), 0);
    vtableLen    = new CIntField(type.getCIntegerField("_vtable_len"), 0);
    classLoaderData = type.getAddressField("_class_loader_data");

    LH_INSTANCE_SLOW_PATH_BIT  = db.lookupIntConstant("Klass::_lh_instance_slow_path_bit").intValue();
    LH_LOG2_ELEMENT_SIZE_SHIFT = db.lookupIntConstant("Klass::_lh_log2_element_size_shift").intValue();
    LH_ELEMENT_TYPE_SHIFT      = db.lookupIntConstant("Klass::_lh_element_type_shift").intValue();
    LH_HEADER_SIZE_SHIFT       = db.lookupIntConstant("Klass::_lh_header_size_shift").intValue();
    LH_ARRAY_TAG_SHIFT         = db.lookupIntConstant("Klass::_lh_array_tag_shift").intValue();
    LH_ARRAY_TAG_TYPE_VALUE    = db.lookupIntConstant("Klass::_lh_array_tag_type_value").intValue();
  }


  public Klass(Address addr) {
    super(addr);
  }

  // jvmdi support - see also class_status in VM code
  public int getClassStatus() {
    return 0; // overridden in derived classes
  }

  public boolean isKlass()             { return true; }
  public boolean isArrayKlass()        { return false; }

  // Fields
  private static long javaMirrorFieldOffset;
  private static CIntField kindField;
  private static MetadataField  superField;
  private static IntField layoutHelper;
  private static AddressField  name;
  private static MetadataField  subklass;
  private static MetadataField  nextSibling;
  private static MetadataField  nextLink;
  private static sun.jvm.hotspot.types.Field traceIDField;
  private static CIntField vtableLen;
  private static AddressField classLoaderData;

  protected Symbol getSymbol(AddressField field) {
    return Symbol.create(addr.getAddressAt(field.getOffset()));
  }

  // Accessors for declared fields
  public Instance getJavaMirror() {
    Address addr = getAddress().addOffsetTo(javaMirrorFieldOffset);
    VMOopHandle vmOopHandle = VMObjectFactory.newObject(VMOopHandle.class, addr);
    return vmOopHandle.resolve();
  }
  public KlassKind getKind()            { return KlassKind.valueOf((int)kindField.getValue(this)); }
  public Klass    getSuper()            { return (Klass)    superField.getValue(this);   }
  public Klass    getJavaSuper()        { return null;  }
  public int      getLayoutHelper()     { return            layoutHelper.getValue(this); }
  public Symbol   getName()             { return            getSymbol(name); }
  public Klass    getSubklassKlass()    { return (Klass)    subklass.getValue(this);     }
  public Klass    getNextSiblingKlass() { return (Klass)    nextSibling.getValue(this);  }
  public Klass    getNextLinkKlass()    { return (Klass)    nextLink.getValue(this);  }
  public long     getVtableLen()        { return            vtableLen.getValue(this); }

  public ClassLoaderData getClassLoaderData() { return ClassLoaderData.instantiateWrapperFor(classLoaderData.getValue(getAddress())); }
  public Oop             getClassLoader()     { return   getClassLoaderData().getClassLoader(); }

  public long traceID() {
    if (traceIDField == null) return 0;
    return traceIDField.getJLong(addr);
  }

  // subclass check
  public boolean isSubclassOf(Klass k) {
    if (k != null) {
      Klass t = this;
      // Run up the super chain and check
      while (t != null) {
        if (t.equals(k)) return true;
        t = t.getSuper();
      }
    }
    return false;
  }

  // subtype check
  public boolean isSubtypeOf(Klass k) {
    return computeSubtypeOf(k);
  }

  boolean computeSubtypeOf(Klass k) {
    return isSubclassOf(k);
  }

  // Find LCA (Least Common Ancester) in class hierarchy
  public Klass lca( Klass k2 ) {
    Klass k1 = this;
    while ( true ) {
      if ( k1.isSubtypeOf(k2) ) return k2;
      if ( k2.isSubtypeOf(k1) ) return k1;
      k1 = k1.getSuper();
      k2 = k2.getSuper();
    }
  }

  public void printValueOn(PrintStream tty) {
    tty.print("Klass");
  }

  public void iterateFields(MetadataVisitor visitor) {
      // visitor.doOop(javaMirror, true);
    visitor.doMetadata(superField, true);
      visitor.doInt(layoutHelper, true);
      // visitor.doOop(name, true);
    visitor.doMetadata(subklass, true);
    visitor.doMetadata(nextSibling, true);
    visitor.doCInt(vtableLen, true);
    }

  public long getObjectSize() {
    throw new RuntimeException("should not reach here");
  }

  /** Array class with specific rank */
  public Klass arrayKlass(int rank)       { return arrayKlassImpl(false, rank); }
  /** Array class with this klass as element type */
  public Klass arrayKlass()               { return arrayKlassImpl(false);       }
  /** These will return null instead of allocating on the heap */
  public Klass arrayKlassOrNull(int rank) { return arrayKlassImpl(true, rank);  }
  public Klass arrayKlassOrNull()         { return arrayKlassImpl(true);        }

  public Klass arrayKlassImpl(boolean orNull, int rank) {
    throw new RuntimeException("array_klass should be dispatched to InstanceKlass, ObjArrayKlass or TypeArrayKlass");
  }

  public Klass arrayKlassImpl(boolean orNull) {
    throw new RuntimeException("array_klass should be dispatched to InstanceKlass, ObjArrayKlass or TypeArrayKlass");
  }

  // This returns the name in the form java/lang/String which isn't really a signature
  // The subclasses override this to produce the correct form, eg
  //   Ljava/lang/String; For ArrayKlasses getName itself is the signature.
  public String signature() { return getName().asString(); }
}
