/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_INTRINSICNODE_HPP
#define SHARE_OPTO_INTRINSICNODE_HPP

#include "opto/connode.hpp"
#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "opto/type.hpp"


//----------------------PartialSubtypeCheckNode--------------------------------
// The 2nd slow-half of a subtype check.  Scan the subklass's 2ndary superklass
// array for an instance of the superklass.  Set a hidden internal cache on a
// hit (cache is checked with exposed code in gen_subtype_check()).  Return
// not zero for a miss or zero for a hit.
class PartialSubtypeCheckNode : public Node {
 public:
  PartialSubtypeCheckNode(Node* c, Node* sub, Node* super) : Node(c,sub,super) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeRawPtr::BOTTOM; }
  virtual uint ideal_reg() const { return Op_RegP; }
};

//------------------------------StrIntrinsic-------------------------------
// Base class for Ideal nodes used in String intrinsic code.
class StrIntrinsicNode: public Node {
 public:
  // Possible encodings of the parameters passed to the string intrinsic.
  // 'L' stands for Latin1 and 'U' stands for UTF16. For example, 'LU' means that
  // the first string is Latin1 encoded and the second string is UTF16 encoded.
  // 'L' means that the single string is Latin1 encoded
  typedef enum ArgEncoding { LL, LU, UL, UU, L, U, none } ArgEnc;

 protected:
  // Encoding of strings. Used to select the right version of the intrinsic.
  const ArgEncoding _encoding;
  virtual uint size_of() const { return sizeof(StrIntrinsicNode); }
  virtual uint hash() const { return Node::hash() + _encoding; }
  virtual bool cmp(const Node& n) const {
    return Node::cmp(n) && _encoding == static_cast<const StrIntrinsicNode&>(n)._encoding;
  }

 public:
  StrIntrinsicNode(Node* control, Node* char_array_mem,
                   Node* s1, Node* c1, Node* s2, Node* c2, ArgEncoding encoding):
  Node(control, char_array_mem, s1, c1, s2, c2), _encoding(encoding) {
  }

  StrIntrinsicNode(Node* control, Node* char_array_mem,
                   Node* s1, Node* s2, Node* c, ArgEncoding encoding):
  Node(control, char_array_mem, s1, s2, c), _encoding(encoding) {
  }

  StrIntrinsicNode(Node* control, Node* char_array_mem,
                   Node* s1, Node* s2, ArgEncoding encoding):
  Node(control, char_array_mem, s1, s2), _encoding(encoding) {
  }

  virtual bool depends_only_on_test() const { return false; }
  virtual uint match_edge(uint idx) const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  ArgEncoding encoding() const { return _encoding; }
};

//------------------------------StrComp-------------------------------------
class StrCompNode: public StrIntrinsicNode {
 public:
  StrCompNode(Node* control, Node* char_array_mem,
              Node* s1, Node* c1, Node* s2, Node* c2, ArgEncoding encoding):
  StrIntrinsicNode(control, char_array_mem, s1, c1, s2, c2, encoding) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }

private:
  virtual const TypePtr* out_adr_type_impl() const { return nullptr; }
  virtual const TypePtr* in_adr_type_impl() const { return TypeAryPtr::BYTES; }
};

//------------------------------StrEquals-------------------------------------
class StrEqualsNode: public StrIntrinsicNode {
 public:
  StrEqualsNode(Node* control, Node* char_array_mem,
                Node* s1, Node* s2, Node* c, ArgEncoding encoding):
  StrIntrinsicNode(control, char_array_mem, s1, s2, c, encoding) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }

private:
  virtual const TypePtr* out_adr_type_impl() const { return nullptr; }
  virtual const TypePtr* in_adr_type_impl() const { return TypeAryPtr::BYTES; }
};

//------------------------------StrIndexOf-------------------------------------
class StrIndexOfNode: public StrIntrinsicNode {
 public:
  StrIndexOfNode(Node* control, Node* char_array_mem,
                 Node* s1, Node* c1, Node* s2, Node* c2, ArgEncoding encoding):
  StrIntrinsicNode(control, char_array_mem, s1, c1, s2, c2, encoding) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }

private:
  virtual const TypePtr* out_adr_type_impl() const { return nullptr; }
  virtual const TypePtr* in_adr_type_impl() const { return TypeAryPtr::BYTES; }
};

//------------------------------StrIndexOfChar-------------------------------------
class StrIndexOfCharNode: public StrIntrinsicNode {
 public:
  StrIndexOfCharNode(Node* control, Node* char_array_mem,
                     Node* s1, Node* c1, Node* c, ArgEncoding encoding):
  StrIntrinsicNode(control, char_array_mem, s1, c1, c, encoding) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }

private:
  virtual const TypePtr* out_adr_type_impl() const { return nullptr; }
  virtual const TypePtr* in_adr_type_impl() const { return TypeAryPtr::BYTES; }
};

//--------------------------StrCompressedCopy-------------------------------
class StrCompressedCopyNode: public StrIntrinsicNode {
private:
  const TypePtr* const _adr_type;

public:
  StrCompressedCopyNode(Node* control, Node* arymem, const TypePtr* adr_type,
                        Node* s1, Node* s2, Node* c):
  StrIntrinsicNode(control, arymem, s1, s2, c, none), _adr_type(adr_type) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

private:
  virtual uint size_of() const { return sizeof(StrCompressedCopyNode); }
  virtual uint hash() const { return StrIntrinsicNode::hash() + (uint)(uintptr_t) _adr_type; }
  virtual bool cmp(const Node& n) const {
    return StrIntrinsicNode::cmp(n) && _adr_type == static_cast<const StrCompressedCopyNode&>(n)._adr_type;
  }
  virtual const TypePtr* out_adr_type_impl() const { return _adr_type; }
};

//--------------------------StrInflatedCopy---------------------------------
class StrInflatedCopyNode: public StrIntrinsicNode {
private:
  const TypePtr* const _adr_type;

public:
  StrInflatedCopyNode(Node* control, Node* arymem, const TypePtr* adr_type,
                      Node* s1, Node* s2, Node* c):
  StrIntrinsicNode(control, arymem, s1, s2, c, none), _adr_type(adr_type) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::MEMORY; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

private:
  virtual uint size_of() const { return sizeof(StrInflatedCopyNode); }
  virtual uint hash() const { return StrIntrinsicNode::hash() + (uint)(uintptr_t) _adr_type; }
  virtual bool cmp(const Node& n) const {
    const StrInflatedCopyNode& s = static_cast<const StrInflatedCopyNode&>(n);
    return StrIntrinsicNode::cmp(n) && _adr_type == s._adr_type;
  }
  virtual const TypePtr* out_adr_type_impl() const { return _adr_type; }
};

//------------------------------AryEq---------------------------------------
class AryEqNode: public StrIntrinsicNode {
private:
  const TypeAryPtr* const _in_adr_type;

public:
  AryEqNode(Node* control, Node* char_array_mem, const TypeAryPtr* in_adr_type,
            Node* s1, Node* s2, ArgEncoding encoding):
  StrIntrinsicNode(control, char_array_mem, s1, s2, encoding), _in_adr_type(in_adr_type) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }

private:
  virtual uint size_of() const { return sizeof(AryEqNode); }
  virtual uint hash() const { return StrIntrinsicNode::hash() + (uint)(uintptr_t) _in_adr_type; }
  virtual bool cmp(const Node& n) const {
    return StrIntrinsicNode::cmp(n) && _in_adr_type == static_cast<const AryEqNode&>(n)._in_adr_type;
  }
  virtual const TypePtr* out_adr_type_impl() const { return nullptr; }
  virtual const TypePtr* in_adr_type_impl() const { return _in_adr_type; }
};

//------------------------------CountPositives------------------------------
class CountPositivesNode: public StrIntrinsicNode {
 public:
  CountPositivesNode(Node* control, Node* char_array_mem, Node* s1, Node* c1):
  StrIntrinsicNode(control, char_array_mem, s1, c1, none) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::POS; }

private:
  virtual const TypePtr* out_adr_type_impl() const { return nullptr; }
  virtual const TypePtr* in_adr_type_impl() const { return TypeAryPtr::BYTES; }
};

//------------------------------VectorizedHashCodeNode----------------------
class VectorizedHashCodeNode: public Node {
private:
  const TypeAryPtr* const _in_adr_type;

public:
  VectorizedHashCodeNode(Node* control, Node* ary_mem, const TypeAryPtr* in_adr_type, Node* arg1, Node* cnt1, Node* result, Node* basic_type)
    : Node(control, ary_mem, arg1, cnt1, result, basic_type), _in_adr_type(in_adr_type) {};
  virtual int Opcode() const;
  virtual bool depends_only_on_test() const { return false; }
  virtual const Type* bottom_type() const { return TypeInt::INT; }
  virtual uint match_edge(uint idx) const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;

private:
  virtual uint size_of() const { return sizeof(VectorizedHashCodeNode); }
  virtual uint hash() const { return Node::hash() + (uint)(uintptr_t) _in_adr_type; }
  virtual bool cmp(const Node& n) const {
    return Node::cmp(n) && _in_adr_type == static_cast<const VectorizedHashCodeNode&>(n)._in_adr_type;
  }
  virtual const TypePtr* out_adr_type_impl() const { return nullptr; }
  virtual const TypePtr* in_adr_type_impl() const { return _in_adr_type; }
};

//------------------------------EncodeISOArray--------------------------------
// encode char[] to byte[] in ISO_8859_1 or ASCII
class EncodeISOArrayNode: public Node {
  const TypePtr* const _adr_type;
  bool _ascii;

public:
  EncodeISOArrayNode(Node* control, Node* arymem, const TypePtr* adr_type, Node* s1, Node* s2, Node* c, bool ascii)
    : Node(control, arymem, s1, s2, c), _adr_type(adr_type), _ascii(ascii) {}

  bool is_ascii() { return _ascii; }
  virtual int Opcode() const;
  virtual bool depends_only_on_test() const { return false; }
  virtual const Type* bottom_type() const { return TypeInt::INT; }
  virtual uint match_edge(uint idx) const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;

private:
  virtual uint size_of() const { return sizeof(EncodeISOArrayNode); }
  virtual uint hash() const { return Node::hash() + (uint)(uintptr_t) _adr_type + _ascii; }
  virtual bool cmp(const Node& n) const {
    const EncodeISOArrayNode& e = static_cast<const EncodeISOArrayNode&>(n);
    return Node::cmp(n) && _ascii == e._ascii && _adr_type == e._adr_type;
  }
  virtual const TypePtr* out_adr_type_impl() const { return _adr_type; }
};

//-------------------------------DigitNode----------------------------------------
class DigitNode : public Node {
public:
  DigitNode(Node* control, Node *in1) : Node(control, in1) {}
  virtual int Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------LowerCaseNode------------------------------------
class LowerCaseNode : public Node {
public:
  LowerCaseNode(Node* control, Node *in1) : Node(control, in1) {}
  virtual int Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------UpperCaseNode------------------------------------
class UpperCaseNode : public Node {
public:
  UpperCaseNode(Node* control, Node *in1) : Node(control, in1) {}
  virtual int Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------WhitespaceCode-----------------------------------
class WhitespaceNode : public Node {
public:
  WhitespaceNode(Node* control, Node *in1) : Node(control, in1) {}
  virtual int Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------CopySign-----------------------------------------
class CopySignDNode : public Node {
 protected:
  CopySignDNode(Node* in1, Node* in2, Node* in3) : Node(nullptr, in1, in2, in3) {}
 public:
  static CopySignDNode* make(PhaseGVN& gvn, Node* in1, Node* in2);
  virtual int Opcode() const;
  const Type* bottom_type() const { return TypeLong::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

class CopySignFNode : public Node {
 public:
  CopySignFNode(Node* in1, Node* in2) : Node(nullptr, in1, in2) {}
  virtual int Opcode() const;
  const Type* bottom_type() const { return TypeLong::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//------------------------------Signum-------------------------------------------
class SignumDNode : public Node {
 protected:
  SignumDNode(Node* in1, Node* in2, Node* in3) : Node(nullptr, in1, in2, in3) {}
 public:
  static SignumDNode* make(PhaseGVN& gvn, Node* in);
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

class SignumFNode : public Node {
 protected:
  SignumFNode(Node* in1, Node* in2, Node* in3) : Node(nullptr, in1, in2, in3) {}
 public:
  static SignumFNode* make(PhaseGVN& gvn, Node* in);
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//----------------------------CompressBits/ExpandBits---------------------------
class CompressBitsNode : public TypeNode {
 public:
  CompressBitsNode(Node* in1, Node* in2, const Type* type) : TypeNode(type, 3) {
    init_req(1, in1);
    init_req(2, in2);
  }
  virtual int Opcode() const;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  static jlong compress_bits(jlong src, jlong mask, int bit_size);
};

class ExpandBitsNode : public TypeNode {
 public:
  ExpandBitsNode(Node* in1, Node* in2, const Type* type) : TypeNode(type, 3) {
    init_req(1, in1);
    init_req(2, in2);
  }
  virtual int Opcode() const;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  static jlong expand_bits(jlong src, jlong mask, int bit_size);
};

//---------- IsInfiniteFNode -----------------------------------------------------
class IsInfiniteFNode : public Node {
  public:
  IsInfiniteFNode(Node* in1) : Node(nullptr, in1) {}
  virtual int   Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//---------- IsInfiniteDNode -----------------------------------------------------
class IsInfiniteDNode : public Node {
  public:
  IsInfiniteDNode(Node* in1) : Node(nullptr, in1) {}
  virtual int   Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//---------- IsFiniteFNode -----------------------------------------------------
class IsFiniteFNode : public Node {
  public:
  IsFiniteFNode(Node* in1) : Node(nullptr, in1) {}
  virtual int   Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//---------- IsFiniteDNode -----------------------------------------------------
class IsFiniteDNode : public Node {
  public:
  IsFiniteDNode(Node* in1) : Node(nullptr, in1) {}
  virtual int   Opcode() const;
  const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

#endif // SHARE_OPTO_INTRINSICNODE_HPP
