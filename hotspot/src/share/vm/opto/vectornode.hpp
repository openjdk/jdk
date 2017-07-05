/*
 * Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
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

//------------------------------VectorNode--------------------------------------
// Vector Operation
class VectorNode : public Node {
 protected:
  uint _length; // vector length
  virtual BasicType elt_basic_type() const = 0; // Vector element basic type

  static const Type* vect_type(BasicType elt_bt, uint len);
  static const Type* vect_type(const Type* elt_type, uint len) {
    return vect_type(elt_type->array_element_basic_type(), len);
  }

 public:
  friend class VectorLoadNode;  // For vect_type
  friend class VectorStoreNode; // ditto.

  VectorNode(Node* n1, uint vlen) : Node(NULL, n1), _length(vlen) {
    init_flags(Flag_is_Vector);
  }
  VectorNode(Node* n1, Node* n2, uint vlen) : Node(NULL, n1, n2), _length(vlen) {
    init_flags(Flag_is_Vector);
  }
  virtual int Opcode() const;

  uint length() const { return _length; } // Vector length

  static uint max_vlen(BasicType bt) { // max vector length
    return (uint)(Matcher::vector_width_in_bytes() / type2aelembytes(bt));
  }

  // Element and vector type
  const Type* elt_type()  const { return Type::get_const_basic_type(elt_basic_type()); }
  const Type* vect_type() const { return vect_type(elt_basic_type(), length()); }

  virtual const Type *bottom_type() const { return vect_type(); }
  virtual uint        ideal_reg()   const { return Matcher::vector_ideal_reg(); }

  // Vector opcode from scalar opcode
  static int opcode(int sopc, uint vlen, const Type* opd_t);

  static VectorNode* scalar2vector(Compile* C, Node* s, uint vlen, const Type* opd_t);

  static VectorNode* make(Compile* C, int sopc, Node* n1, Node* n2, uint vlen, const Type* elt_t);

};

//===========================Vector=ALU=Operations====================================

//------------------------------AddVBNode---------------------------------------
// Vector add byte
class AddVBNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  AddVBNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------AddVCNode---------------------------------------
// Vector add char
class AddVCNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  AddVCNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------AddVSNode---------------------------------------
// Vector add short
class AddVSNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  AddVSNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------AddVINode---------------------------------------
// Vector add int
class AddVINode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  AddVINode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------AddVLNode---------------------------------------
// Vector add long
class AddVLNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_LONG; }
 public:
  AddVLNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------AddVFNode---------------------------------------
// Vector add float
class AddVFNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  AddVFNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------AddVDNode---------------------------------------
// Vector add double
class AddVDNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  AddVDNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------SubVBNode---------------------------------------
// Vector subtract byte
class SubVBNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  SubVBNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------SubVCNode---------------------------------------
// Vector subtract char
class SubVCNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  SubVCNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------SubVSNode---------------------------------------
// Vector subtract short
class SubVSNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  SubVSNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------SubVINode---------------------------------------
// Vector subtract int
class SubVINode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  SubVINode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------SubVLNode---------------------------------------
// Vector subtract long
class SubVLNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_LONG; }
 public:
  SubVLNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------SubVFNode---------------------------------------
// Vector subtract float
class SubVFNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  SubVFNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------SubVDNode---------------------------------------
// Vector subtract double
class SubVDNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  SubVDNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------MulVFNode---------------------------------------
// Vector multiply float
class MulVFNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  MulVFNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------MulVDNode---------------------------------------
// Vector multiply double
class MulVDNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  MulVDNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------DivVFNode---------------------------------------
// Vector divide float
class DivVFNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  DivVFNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------DivVDNode---------------------------------------
// Vector Divide double
class DivVDNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  DivVDNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVBNode---------------------------------------
// Vector lshift byte
class LShiftVBNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  LShiftVBNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVCNode---------------------------------------
// Vector lshift chars
class LShiftVCNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  LShiftVCNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVSNode---------------------------------------
// Vector lshift shorts
class LShiftVSNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  LShiftVSNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVINode---------------------------------------
// Vector lshift ints
class LShiftVINode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  LShiftVINode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVBNode---------------------------------------
// Vector urshift bytes
class URShiftVBNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  URShiftVBNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVCNode---------------------------------------
// Vector urshift char
class URShiftVCNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  URShiftVCNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVSNode---------------------------------------
// Vector urshift shorts
class URShiftVSNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  URShiftVSNode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVINode---------------------------------------
// Vector urshift ints
class URShiftVINode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  URShiftVINode(Node* in1, Node* in2, uint vlen) : VectorNode(in1,in2,vlen) {}
  virtual int Opcode() const;
};

//------------------------------AndVNode---------------------------------------
// Vector and
class AndVNode : public VectorNode {
 protected:
  BasicType _bt;
  virtual BasicType elt_basic_type() const { return _bt; }
 public:
  AndVNode(Node* in1, Node* in2, uint vlen, BasicType bt) : VectorNode(in1,in2,vlen), _bt(bt) {}
  virtual int Opcode() const;
};

//------------------------------OrVNode---------------------------------------
// Vector or
class OrVNode : public VectorNode {
 protected:
  BasicType _bt;
  virtual BasicType elt_basic_type() const { return _bt; }
 public:
  OrVNode(Node* in1, Node* in2, uint vlen, BasicType bt) : VectorNode(in1,in2,vlen), _bt(bt) {}
  virtual int Opcode() const;
};

//------------------------------XorVNode---------------------------------------
// Vector xor
class XorVNode : public VectorNode {
 protected:
  BasicType _bt;
  virtual BasicType elt_basic_type() const { return _bt; }
 public:
  XorVNode(Node* in1, Node* in2, uint vlen, BasicType bt) : VectorNode(in1,in2,vlen), _bt(bt) {}
  virtual int Opcode() const;
};

//================================= M E M O R Y ==================================


//------------------------------VectorLoadNode--------------------------------------
// Vector Load from memory
class VectorLoadNode : public LoadNode {
  virtual uint size_of() const { return sizeof(*this); }

 protected:
  virtual BasicType elt_basic_type()  const = 0; // Vector element basic type
  // For use in constructor
  static const Type* vect_type(const Type* elt_type, uint len) {
    return VectorNode::vect_type(elt_type, len);
  }

 public:
  VectorLoadNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const Type *rt)
    : LoadNode(c,mem,adr,at,rt) {
      init_flags(Flag_is_Vector);
  }
  virtual int Opcode() const;

  virtual uint  length() const = 0; // Vector length

  // Element and vector type
  const Type* elt_type()  const { return Type::get_const_basic_type(elt_basic_type()); }
  const Type* vect_type() const { return VectorNode::vect_type(elt_basic_type(), length()); }

  virtual uint ideal_reg() const  { return Matcher::vector_ideal_reg(); }
  virtual BasicType memory_type() const { return T_VOID; }
  virtual int memory_size() const { return length()*type2aelembytes(elt_basic_type()); }

  // Vector opcode from scalar opcode
  static int opcode(int sopc, uint vlen);

  static VectorLoadNode* make(Compile* C, int opc, Node* ctl, Node* mem,
                              Node* adr, const TypePtr* atyp, uint vlen);
};

//------------------------------Load16BNode--------------------------------------
// Vector load of 16 bytes (8bits signed) from memory
class Load16BNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Load16BNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::BYTE)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,16)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store16B; }
  virtual uint length() const { return 16; }
};

//------------------------------Load8BNode--------------------------------------
// Vector load of 8 bytes (8bits signed) from memory
class Load8BNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Load8BNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::BYTE)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,8)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store8B; }
  virtual uint length() const { return 8; }
};

//------------------------------Load4BNode--------------------------------------
// Vector load of 4 bytes (8bits signed) from memory
class Load4BNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Load4BNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::BYTE)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,4)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store4B; }
  virtual uint length() const { return 4; }
};

//------------------------------Load8CNode--------------------------------------
// Vector load of 8 chars (16bits unsigned) from memory
class Load8CNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Load8CNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::CHAR)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,8)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store8C; }
  virtual uint length() const { return 8; }
};

//------------------------------Load4CNode--------------------------------------
// Vector load of 4 chars (16bits unsigned) from memory
class Load4CNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Load4CNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::CHAR)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,4)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store4C; }
  virtual uint length() const { return 4; }
};

//------------------------------Load2CNode--------------------------------------
// Vector load of 2 chars (16bits unsigned) from memory
class Load2CNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Load2CNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::CHAR)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,2)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store2C; }
  virtual uint length() const { return 2; }
};

//------------------------------Load8SNode--------------------------------------
// Vector load of 8 shorts (16bits signed) from memory
class Load8SNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  Load8SNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::SHORT)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,8)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store8C; }
  virtual uint length() const { return 8; }
};

//------------------------------Load4SNode--------------------------------------
// Vector load of 4 shorts (16bits signed) from memory
class Load4SNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  Load4SNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::SHORT)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,4)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store4C; }
  virtual uint length() const { return 4; }
};

//------------------------------Load2SNode--------------------------------------
// Vector load of 2 shorts (16bits signed) from memory
class Load2SNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  Load2SNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::SHORT)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,2)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store2C; }
  virtual uint length() const { return 2; }
};

//------------------------------Load4INode--------------------------------------
// Vector load of 4 integers (32bits signed) from memory
class Load4INode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  Load4INode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::INT)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,4)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store4I; }
  virtual uint length() const { return 4; }
};

//------------------------------Load2INode--------------------------------------
// Vector load of 2 integers (32bits signed) from memory
class Load2INode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  Load2INode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeInt *ti = TypeInt::INT)
    : VectorLoadNode(c,mem,adr,at,vect_type(ti,2)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store2I; }
  virtual uint length() const { return 2; }
};

//------------------------------Load2LNode--------------------------------------
// Vector load of 2 longs (64bits signed) from memory
class Load2LNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_LONG; }
 public:
  Load2LNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeLong *tl = TypeLong::LONG)
    : VectorLoadNode(c,mem,adr,at,vect_type(tl,2)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store2L; }
  virtual uint length() const { return 2; }
};

//------------------------------Load4FNode--------------------------------------
// Vector load of 4 floats (32bits) from memory
class Load4FNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  Load4FNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const Type *t = Type::FLOAT)
    : VectorLoadNode(c,mem,adr,at,vect_type(t,4)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store4F; }
  virtual uint length() const { return 4; }
};

//------------------------------Load2FNode--------------------------------------
// Vector load of 2 floats (32bits) from memory
class Load2FNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  Load2FNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const Type *t = Type::FLOAT)
    : VectorLoadNode(c,mem,adr,at,vect_type(t,2)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store2F; }
  virtual uint length() const { return 2; }
};

//------------------------------Load2DNode--------------------------------------
// Vector load of 2 doubles (64bits) from memory
class Load2DNode : public VectorLoadNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  Load2DNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const Type *t = Type::DOUBLE)
    : VectorLoadNode(c,mem,adr,at,vect_type(t,2)) {}
  virtual int Opcode() const;
  virtual int store_Opcode() const { return Op_Store2D; }
  virtual uint length() const { return 2; }
};


//------------------------------VectorStoreNode--------------------------------------
// Vector Store to memory
class VectorStoreNode : public StoreNode {
  virtual uint size_of() const { return sizeof(*this); }

 protected:
  virtual BasicType elt_basic_type()  const = 0; // Vector element basic type

 public:
  VectorStoreNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : StoreNode(c,mem,adr,at,val) {
      init_flags(Flag_is_Vector);
  }
  virtual int Opcode() const;

  virtual uint  length() const = 0; // Vector length

  // Element and vector type
  const Type* elt_type()  const { return Type::get_const_basic_type(elt_basic_type()); }
  const Type* vect_type() const { return VectorNode::vect_type(elt_basic_type(), length()); }

  virtual uint ideal_reg() const  { return Matcher::vector_ideal_reg(); }
  virtual BasicType memory_type() const { return T_VOID; }
  virtual int memory_size() const { return length()*type2aelembytes(elt_basic_type()); }

  // Vector opcode from scalar opcode
  static int opcode(int sopc, uint vlen);

  static VectorStoreNode* make(Compile* C, int opc, Node* ctl, Node* mem,
                               Node* adr, const TypePtr* atyp, VectorNode* val,
                               uint vlen);
};

//------------------------------Store16BNode--------------------------------------
// Vector store of 16 bytes (8bits signed) to memory
class Store16BNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Store16BNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 16; }
};

//------------------------------Store8BNode--------------------------------------
// Vector store of 8 bytes (8bits signed) to memory
class Store8BNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Store8BNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 8; }
};

//------------------------------Store4BNode--------------------------------------
// Vector store of 4 bytes (8bits signed) to memory
class Store4BNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Store4BNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 4; }
};

//------------------------------Store8CNode--------------------------------------
// Vector store of 8 chars (16bits signed/unsigned) to memory
class Store8CNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Store8CNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 8; }
};

//------------------------------Store4CNode--------------------------------------
// Vector store of 4 chars (16bits signed/unsigned) to memory
class Store4CNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Store4CNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 4; }
};

//------------------------------Store2CNode--------------------------------------
// Vector store of 2 chars (16bits signed/unsigned) to memory
class Store2CNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Store2CNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 2; }
};

//------------------------------Store4INode--------------------------------------
// Vector store of 4 integers (32bits signed) to memory
class Store4INode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  Store4INode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 4; }
};

//------------------------------Store2INode--------------------------------------
// Vector store of 2 integers (32bits signed) to memory
class Store2INode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  Store2INode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 2; }
};

//------------------------------Store2LNode--------------------------------------
// Vector store of 2 longs (64bits signed) to memory
class Store2LNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_LONG; }
 public:
  Store2LNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 2; }
};

//------------------------------Store4FNode--------------------------------------
// Vector store of 4 floats (32bits) to memory
class Store4FNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  Store4FNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 4; }
};

//------------------------------Store2FNode--------------------------------------
// Vector store of 2 floats (32bits) to memory
class Store2FNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  Store2FNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 2; }
};

//------------------------------Store2DNode--------------------------------------
// Vector store of 2 doubles (64bits) to memory
class Store2DNode : public VectorStoreNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  Store2DNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : VectorStoreNode(c,mem,adr,at,val) {}
  virtual int Opcode() const;
  virtual uint length() const { return 2; }
};

//=========================Promote_Scalar_to_Vector====================================

//------------------------------Replicate16BNode---------------------------------------
// Replicate byte scalar to be vector of 16 bytes
class Replicate16BNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Replicate16BNode(Node* in1) : VectorNode(in1, 16) {}
  virtual int Opcode() const;
};

//------------------------------Replicate8BNode---------------------------------------
// Replicate byte scalar to be vector of 8 bytes
class Replicate8BNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Replicate8BNode(Node* in1) : VectorNode(in1, 8) {}
  virtual int Opcode() const;
};

//------------------------------Replicate4BNode---------------------------------------
// Replicate byte scalar to be vector of 4 bytes
class Replicate4BNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Replicate4BNode(Node* in1) : VectorNode(in1, 4) {}
  virtual int Opcode() const;
};

//------------------------------Replicate8CNode---------------------------------------
// Replicate char scalar to be vector of 8 chars
class Replicate8CNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Replicate8CNode(Node* in1) : VectorNode(in1, 8) {}
  virtual int Opcode() const;
};

//------------------------------Replicate4CNode---------------------------------------
// Replicate char scalar to be vector of 4 chars
class Replicate4CNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Replicate4CNode(Node* in1) : VectorNode(in1, 4) {}
  virtual int Opcode() const;
};

//------------------------------Replicate2CNode---------------------------------------
// Replicate char scalar to be vector of 2 chars
class Replicate2CNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Replicate2CNode(Node* in1) : VectorNode(in1, 2) {}
  virtual int Opcode() const;
};

//------------------------------Replicate8SNode---------------------------------------
// Replicate short scalar to be vector of 8 shorts
class Replicate8SNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  Replicate8SNode(Node* in1) : VectorNode(in1, 8) {}
  virtual int Opcode() const;
};

//------------------------------Replicate4SNode---------------------------------------
// Replicate short scalar to be vector of 4 shorts
class Replicate4SNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  Replicate4SNode(Node* in1) : VectorNode(in1, 4) {}
  virtual int Opcode() const;
};

//------------------------------Replicate2SNode---------------------------------------
// Replicate short scalar to be vector of 2 shorts
class Replicate2SNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  Replicate2SNode(Node* in1) : VectorNode(in1, 2) {}
  virtual int Opcode() const;
};

//------------------------------Replicate4INode---------------------------------------
// Replicate int scalar to be vector of 4 ints
class Replicate4INode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  Replicate4INode(Node* in1) : VectorNode(in1, 4) {}
  virtual int Opcode() const;
};

//------------------------------Replicate2INode---------------------------------------
// Replicate int scalar to be vector of 2 ints
class Replicate2INode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  Replicate2INode(Node* in1) : VectorNode(in1, 2) {}
  virtual int Opcode() const;
};

//------------------------------Replicate2LNode---------------------------------------
// Replicate long scalar to be vector of 2 longs
class Replicate2LNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_LONG; }
 public:
  Replicate2LNode(Node* in1) : VectorNode(in1, 2) {}
  virtual int Opcode() const;
};

//------------------------------Replicate4FNode---------------------------------------
// Replicate float scalar to be vector of 4 floats
class Replicate4FNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  Replicate4FNode(Node* in1) : VectorNode(in1, 4) {}
  virtual int Opcode() const;
};

//------------------------------Replicate2FNode---------------------------------------
// Replicate float scalar to be vector of 2 floats
class Replicate2FNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  Replicate2FNode(Node* in1) : VectorNode(in1, 2) {}
  virtual int Opcode() const;
};

//------------------------------Replicate2DNode---------------------------------------
// Replicate double scalar to be vector of 2 doubles
class Replicate2DNode : public VectorNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  Replicate2DNode(Node* in1) : VectorNode(in1, 2) {}
  virtual int Opcode() const;
};

//========================Pack_Scalars_into_a_Vector==============================

//------------------------------PackNode---------------------------------------
// Pack parent class (not for code generation).
class PackNode : public VectorNode {
 public:
  PackNode(Node* in1)  : VectorNode(in1, 1) {}
  PackNode(Node* in1, Node* n2)  : VectorNode(in1, n2, 2) {}
  virtual int Opcode() const;

  void add_opd(Node* n) {
    add_req(n);
    _length++;
    assert(_length == req() - 1, "vector length matches edge count");
  }

  // Create a binary tree form for Packs. [lo, hi) (half-open) range
  Node* binaryTreePack(Compile* C, int lo, int hi);

  static PackNode* make(Compile* C, Node* s, const Type* elt_t);
};

//------------------------------PackBNode---------------------------------------
// Pack byte scalars into vector
class PackBNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  PackBNode(Node* in1)  : PackNode(in1) {}
  virtual int Opcode() const;
};

//------------------------------PackCNode---------------------------------------
// Pack char scalars into vector
class PackCNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  PackCNode(Node* in1)  : PackNode(in1) {}
  virtual int Opcode() const;
};

//------------------------------PackSNode---------------------------------------
// Pack short scalars into a vector
class PackSNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_SHORT; }
 public:
  PackSNode(Node* in1)  : PackNode(in1) {}
  virtual int Opcode() const;
};

//------------------------------PackINode---------------------------------------
// Pack integer scalars into a vector
class PackINode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_INT; }
 public:
  PackINode(Node* in1)  : PackNode(in1) {}
  PackINode(Node* in1, Node* in2) : PackNode(in1, in2) {}
  virtual int Opcode() const;
};

//------------------------------PackLNode---------------------------------------
// Pack long scalars into a vector
class PackLNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_LONG; }
 public:
  PackLNode(Node* in1)  : PackNode(in1) {}
  PackLNode(Node* in1, Node* in2) : PackNode(in1, in2) {}
  virtual int Opcode() const;
};

//------------------------------PackFNode---------------------------------------
// Pack float scalars into vector
class PackFNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_FLOAT; }
 public:
  PackFNode(Node* in1)  : PackNode(in1) {}
  PackFNode(Node* in1, Node* in2) : PackNode(in1, in2) {}
  virtual int Opcode() const;
};

//------------------------------PackDNode---------------------------------------
// Pack double scalars into a vector
class PackDNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_DOUBLE; }
 public:
  PackDNode(Node* in1)  : PackNode(in1) {}
  PackDNode(Node* in1, Node* in2) : PackNode(in1, in2) {}
  virtual int Opcode() const;
};

// The Pack2xN nodes assist code generation.  They are created from
// Pack4C, etc. nodes in final_graph_reshape in the form of a
// balanced, binary tree.

//------------------------------Pack2x1BNode-----------------------------------------
// Pack 2 1-byte integers into vector of 2 bytes
class Pack2x1BNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_BYTE; }
 public:
  Pack2x1BNode(Node *in1, Node* in2) : PackNode(in1, in2) {}
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------Pack2x2BNode---------------------------------------
// Pack 2 2-byte integers into vector of 4 bytes
class Pack2x2BNode : public PackNode {
 protected:
  virtual BasicType elt_basic_type() const { return T_CHAR; }
 public:
  Pack2x2BNode(Node *in1, Node* in2) : PackNode(in1, in2) {}
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegI; }
};

//========================Extract_Scalar_from_Vector===============================

//------------------------------ExtractNode---------------------------------------
// Extract a scalar from a vector at position "pos"
class ExtractNode : public Node {
 public:
  ExtractNode(Node* src, ConINode* pos) : Node(NULL, src, (Node*)pos) {
    assert(in(2)->get_int() >= 0, "positive constants");
  }
  virtual int Opcode() const;
  uint  pos() const { return in(2)->get_int(); }

  static Node* make(Compile* C, Node* v, uint position, const Type* opd_t);
};

//------------------------------ExtractBNode---------------------------------------
// Extract a byte from a vector at position "pos"
class ExtractBNode : public ExtractNode {
 public:
  ExtractBNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractCNode---------------------------------------
// Extract a char from a vector at position "pos"
class ExtractCNode : public ExtractNode {
 public:
  ExtractCNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractSNode---------------------------------------
// Extract a short from a vector at position "pos"
class ExtractSNode : public ExtractNode {
 public:
  ExtractSNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractINode---------------------------------------
// Extract an int from a vector at position "pos"
class ExtractINode : public ExtractNode {
 public:
  ExtractINode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractLNode---------------------------------------
// Extract a long from a vector at position "pos"
class ExtractLNode : public ExtractNode {
 public:
  ExtractLNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------ExtractFNode---------------------------------------
// Extract a float from a vector at position "pos"
class ExtractFNode : public ExtractNode {
 public:
  ExtractFNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//------------------------------ExtractDNode---------------------------------------
// Extract a double from a vector at position "pos"
class ExtractDNode : public ExtractNode {
 public:
  ExtractDNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};
