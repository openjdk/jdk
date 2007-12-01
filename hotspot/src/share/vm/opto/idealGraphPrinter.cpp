/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_idealGraphPrinter.cpp.incl"

#ifndef PRODUCT

// Constants
// Keep consistent with Java constants
const char *IdealGraphPrinter::INDENT = "  ";
const char *IdealGraphPrinter::TOP_ELEMENT = "graphDocument";
const char *IdealGraphPrinter::GROUP_ELEMENT = "group";
const char *IdealGraphPrinter::GRAPH_ELEMENT = "graph";
const char *IdealGraphPrinter::PROPERTIES_ELEMENT = "properties";
const char *IdealGraphPrinter::EDGES_ELEMENT = "edges";
const char *IdealGraphPrinter::PROPERTY_ELEMENT = "p";
const char *IdealGraphPrinter::EDGE_ELEMENT = "edge";
const char *IdealGraphPrinter::NODE_ELEMENT = "node";
const char *IdealGraphPrinter::NODES_ELEMENT = "nodes";
const char *IdealGraphPrinter::REMOVE_EDGE_ELEMENT = "removeEdge";
const char *IdealGraphPrinter::REMOVE_NODE_ELEMENT = "removeNode";
const char *IdealGraphPrinter::METHOD_NAME_PROPERTY = "name";
const char *IdealGraphPrinter::METHOD_IS_PUBLIC_PROPERTY = "public";
const char *IdealGraphPrinter::METHOD_IS_STATIC_PROPERTY = "static";
const char *IdealGraphPrinter::TRUE_VALUE = "true";
const char *IdealGraphPrinter::NODE_NAME_PROPERTY = "name";
const char *IdealGraphPrinter::EDGE_NAME_PROPERTY = "name";
const char *IdealGraphPrinter::NODE_ID_PROPERTY = "id";
const char *IdealGraphPrinter::FROM_PROPERTY = "from";
const char *IdealGraphPrinter::TO_PROPERTY = "to";
const char *IdealGraphPrinter::PROPERTY_NAME_PROPERTY = "name";
const char *IdealGraphPrinter::GRAPH_NAME_PROPERTY = "name";
const char *IdealGraphPrinter::INDEX_PROPERTY = "index";
const char *IdealGraphPrinter::METHOD_ELEMENT = "method";
const char *IdealGraphPrinter::INLINE_ELEMENT = "inline";
const char *IdealGraphPrinter::BYTECODES_ELEMENT = "bytecodes";
const char *IdealGraphPrinter::METHOD_BCI_PROPERTY = "bci";
const char *IdealGraphPrinter::METHOD_SHORT_NAME_PROPERTY = "shortName";
const char *IdealGraphPrinter::CONTROL_FLOW_ELEMENT = "controlFlow";
const char *IdealGraphPrinter::BLOCK_NAME_PROPERTY = "name";
const char *IdealGraphPrinter::BLOCK_DOMINATOR_PROPERTY = "dom";
const char *IdealGraphPrinter::BLOCK_ELEMENT = "block";
const char *IdealGraphPrinter::SUCCESSORS_ELEMENT = "successors";
const char *IdealGraphPrinter::SUCCESSOR_ELEMENT = "successor";
const char *IdealGraphPrinter::ASSEMBLY_ELEMENT = "assembly";

int IdealGraphPrinter::_file_count = 0;

IdealGraphPrinter *IdealGraphPrinter::printer() {
  if (PrintIdealGraphLevel == 0) return NULL;

  JavaThread *thread = JavaThread::current();
  if (!thread->is_Compiler_thread()) return NULL;

  CompilerThread *compiler_thread = (CompilerThread *)thread;
  if (compiler_thread->ideal_graph_printer() == NULL) {
    IdealGraphPrinter *printer = new IdealGraphPrinter();
    compiler_thread->set_ideal_graph_printer(printer);
  }

  return compiler_thread->ideal_graph_printer();
}

void IdealGraphPrinter::clean_up() {
  JavaThread *p;
  for (p = Threads::first(); p; p = p->next()) {
    if (p->is_Compiler_thread()) {
      CompilerThread *c = (CompilerThread *)p;
      IdealGraphPrinter *printer = c->ideal_graph_printer();
      if (printer) {
        delete printer;
      }
      c->set_ideal_graph_printer(NULL);
    }
  }
}

// Constructor, either file or network output
IdealGraphPrinter::IdealGraphPrinter() {

  _traverse_outs = false;
  _should_send_method = true;
  _output = NULL;
  buffer[0] = 0;
  _depth = 0;
  _current_method = NULL;
  assert(!_current_method, "current method must be initialized to NULL");
  _arena = new Arena();

  _stream = new (ResourceObj::C_HEAP) networkStream();

  if (PrintIdealGraphFile != NULL) {
    ThreadCritical tc;
    // User wants all output to go to files
    if (_file_count != 0) {
      ResourceMark rm;
      stringStream st;
      const char* dot = strrchr(PrintIdealGraphFile, '.');
      if (dot) {
        st.write(PrintIdealGraphFile, dot - PrintIdealGraphFile);
        st.print("%d%s", _file_count, dot);
      } else {
        st.print("%s%d", PrintIdealGraphFile, _file_count);
      }
      _output = new (ResourceObj::C_HEAP) fileStream(st.as_string());
    } else {
      _output = new (ResourceObj::C_HEAP) fileStream(PrintIdealGraphFile);
    }
    _file_count++;
  } else {
    // Try to connect to visualizer
    if (_stream->connect(PrintIdealGraphAddress, PrintIdealGraphPort)) {
      char c = 0;
      _stream->read(&c, 1);
      if (c != 'y') {
        tty->print_cr("Client available, but does not want to receive data!");
        _stream->close();
        delete _stream;
        _stream = NULL;
        return;
      }
      _output = _stream;
    } else {
      // It would be nice if we could shut down cleanly but it should
      // be an error if we can't connect to the visualizer.
      fatal2("Couldn't connect to visualizer at %s:%d", PrintIdealGraphAddress, PrintIdealGraphPort);
    }
  }

  start_element(TOP_ELEMENT);
}

// Destructor, close file or network stream
IdealGraphPrinter::~IdealGraphPrinter() {

  end_element(TOP_ELEMENT);

  if (_stream) {
    delete _stream;
    if (_stream == _output) {
      _output = NULL;
    }
    _stream = NULL;
  }

  if (_output) {
    delete _output;
    _output = NULL;
  }
}

void IdealGraphPrinter::print_ifg(PhaseIFG* ifg) {

  // Code to print an interference graph to tty, currently not used

  /*
  if (!_current_method) return;
   // Remove neighbor colors

  for (uint i = 0; i < ifg._maxlrg; i++) {

    IndexSet *s = ifg.neighbors(i);
    IndexSetIterator elements(s);
    uint neighbor;
    while ((neighbor = elements.next()) != 0) {
        tty->print_cr("Edge between %d and %d\n", i, neighbor);
    }
  }


  for (uint i = 0; i < ifg._maxlrg; i++) {
    LRG &l = ifg.lrgs(i);
    if (l._def) {
      OptoReg::Name name = l.reg();
      tty->print("OptoReg::dump: ");
      OptoReg::dump(name);
      tty->print_cr("");
      tty->print_cr("name=%d\n", name);
      if (name) {
        if (OptoReg::is_stack(name)) {
          tty->print_cr("Stack number %d\n", OptoReg::reg2stack(name));

        } else if (!OptoReg::is_valid(name)) {
          tty->print_cr("BAD!!!");
        } else {

          if (OptoReg::is_reg(name)) {
          tty->print_cr(OptoReg::regname(name));
          } else {
            int x = 0;
          }
        }
        int x = 0;
      }

      if (l._def == NodeSentinel) {
        tty->print("multiple mapping from %d: ", i);
        for (int j=0; j<l._defs->length(); j++) {
          tty->print("%d ", l._defs->at(j)->_idx);
        }
        tty->print_cr("");
      } else {
        tty->print_cr("mapping between %d and %d\n", i, l._def->_idx);
      }
    }
  }*/
}

void IdealGraphPrinter::print_method(ciMethod *method, int bci, InlineTree *tree) {

  Properties properties;
  stringStream str;
  method->print_name(&str);

  stringStream shortStr;
  method->print_short_name(&shortStr);


  properties.add(new Property(METHOD_NAME_PROPERTY, str.as_string()));
  properties.add(new Property(METHOD_SHORT_NAME_PROPERTY, shortStr.as_string()));
  properties.add(new Property(METHOD_BCI_PROPERTY, bci));
  start_element(METHOD_ELEMENT, &properties);

  start_element(BYTECODES_ELEMENT);
  output()->print_cr("<![CDATA[");
  method->print_codes_on(output());
  output()->print_cr("]]>");
  end_element(BYTECODES_ELEMENT);

  start_element(INLINE_ELEMENT);
  if (tree != NULL) {
    GrowableArray<InlineTree *> subtrees = tree->subtrees();
    for (int i = 0; i < subtrees.length(); i++) {
      print_inline_tree(subtrees.at(i));
    }
  }
  end_element(INLINE_ELEMENT);

  end_element(METHOD_ELEMENT);
  output()->flush();
}

void IdealGraphPrinter::print_inline_tree(InlineTree *tree) {

  if (tree == NULL) return;

  ciMethod *method = tree->method();
  print_method(tree->method(), tree->caller_bci(), tree);

}

void IdealGraphPrinter::clear_nodes() {
 // for (int i = 0; i < _nodes.length(); i++) {
 //   _nodes.at(i)->clear_node();
 // }
}

void IdealGraphPrinter::print_inlining(Compile* compile) {

  // Print inline tree
  if (_should_send_method) {
    InlineTree *inlineTree = compile->ilt();
    if (inlineTree != NULL) {
      print_inline_tree(inlineTree);
    } else {
      // print this method only
    }
  }
}

// Has to be called whenever a method is compiled
void IdealGraphPrinter::begin_method(Compile* compile) {

  ciMethod *method = compile->method();
  assert(_output, "output stream must exist!");
  assert(method, "null methods are not allowed!");
  assert(!_current_method, "current method must be null!");

  _arena->destruct_contents();

  start_element(GROUP_ELEMENT);

  // Print properties
  Properties properties;

  // Add method name
  stringStream strStream;
  method->print_name(&strStream);
  properties.add(new Property(METHOD_NAME_PROPERTY, strStream.as_string()));

  if (method->flags().is_public()) {
    properties.add(new Property(METHOD_IS_PUBLIC_PROPERTY, TRUE_VALUE));
  }

  if (method->flags().is_static()) {
    properties.add(new Property(METHOD_IS_STATIC_PROPERTY, TRUE_VALUE));
  }

  properties.print(this);

  if (_stream) {
    char answer = 0;
    _stream->flush();
    int result = _stream->read(&answer, 1);
    _should_send_method = (answer == 'y');
  }

  this->_nodes = GrowableArray<NodeDescription *>(_arena, 2, 0, NULL);
  this->_edges = GrowableArray< EdgeDescription * >(_arena, 2, 0, NULL);


  this->_current_method = method;



  _output->flush();
}

// Has to be called whenever a method has finished compilation
void IdealGraphPrinter::end_method() {

//  if (finish && !in_method) return;

  nmethod* method = (nmethod*)this->_current_method->code();

  start_element(ASSEMBLY_ELEMENT);
 // Disassembler::decode(method, _output);
  end_element(ASSEMBLY_ELEMENT);


  end_element(GROUP_ELEMENT);
  _current_method = NULL;
  _output->flush();
  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc) {
      delete desc;
      _nodes.at_put(i, NULL);
    }
  }
  this->_nodes.clear();


  for (int i = 0; i < _edges.length(); i++) {
   // for (int j=0; j<_edges.at(i)->length(); j++) {
      EdgeDescription *conn = _edges.at(i);
      conn->print(this);
      if (conn) {
        delete conn;
        _edges.at_put(i, NULL);
      }
    //}
    //_edges.at(i)->clear();
    //delete _edges.at(i);
    //_edges.at_put(i, NULL);
  }
  this->_edges.clear();

//  in_method = false;
}

// Outputs an XML start element
void IdealGraphPrinter::start_element(const char *s, Properties *properties /* = NULL */, bool print_indent /* = false */, bool print_return /* = true */) {

  start_element_helper(s, properties, false, print_indent, print_return);
  _depth++;

}

// Outputs an XML start element without body
void IdealGraphPrinter::simple_element(const char *s, Properties *properties /* = NULL */, bool print_indent /* = false */) {
  start_element_helper(s, properties, true, print_indent, true);
}

// Outputs an XML start element. If outputEnd is true, the element has no body.
void IdealGraphPrinter::start_element_helper(const char *s, Properties *properties, bool outputEnd, bool print_indent /* = false */, bool print_return /* = true */) {

  assert(_output, "output stream must exist!");

  if (print_indent) this->print_indent();
  _output->print("<");
  _output->print(s);
  if (properties) properties->print_as_attributes(this);

  if (outputEnd) {
    _output->print("/");
  }

  _output->print(">");
  if (print_return) _output->print_cr("");

}

// Print indent
void IdealGraphPrinter::print_indent() {
  for (int i = 0; i < _depth; i++) {
    _output->print(INDENT);
  }
}

// Outputs an XML end element
void IdealGraphPrinter::end_element(const char *s, bool print_indent /* = true */, bool print_return /* = true */) {

  assert(_output, "output stream must exist!");

  _depth--;

  if (print_indent) this->print_indent();
  _output->print("</");
  _output->print(s);
  _output->print(">");
  if (print_return) _output->print_cr("");

}

bool IdealGraphPrinter::traverse_outs() {
  return _traverse_outs;
}

void IdealGraphPrinter::set_traverse_outs(bool b) {
  _traverse_outs = b;
}

void IdealGraphPrinter::walk(Node *start) {


  VectorSet visited(Thread::current()->resource_area());
  GrowableArray<Node *> nodeStack(Thread::current()->resource_area(), 0, 0, NULL);
  nodeStack.push(start);
  visited.test_set(start->_idx);
  while(nodeStack.length() > 0) {

    Node *n = nodeStack.pop();
    IdealGraphPrinter::pre_node(n, this);

    if (_traverse_outs) {
      for (DUIterator i = n->outs(); n->has_out(i); i++) {
        Node* p = n->out(i);
        if (!visited.test_set(p->_idx)) {
          nodeStack.push(p);
        }
      }
    }

    for ( uint i = 0; i < n->len(); i++ ) {
      if ( n->in(i) ) {
        if (!visited.test_set(n->in(i)->_idx)) {
          nodeStack.push(n->in(i));
        }
      }
    }
  }
}

void IdealGraphPrinter::compress(int index, GrowableArray<Block>* blocks) {
  Block *block = blocks->adr_at(index);

  int ancestor = block->ancestor();
  assert(ancestor != -1, "");

  Block *ancestor_block = blocks->adr_at(ancestor);
  if (ancestor_block->ancestor() != -1) {
    compress(ancestor, blocks);

    int label = block->label();
    Block *label_block = blocks->adr_at(label);

    int ancestor_label = ancestor_block->label();
    Block *ancestor_label_block = blocks->adr_at(label);
    if (ancestor_label_block->semi() < label_block->semi()) {
      block->set_label(ancestor_label);
    }

    block->set_ancestor(ancestor_block->ancestor());
  }
}

int IdealGraphPrinter::eval(int index, GrowableArray<Block>* blocks) {
  Block *block = blocks->adr_at(index);
  if (block->ancestor() == -1) {
    return index;
  } else {
    compress(index, blocks);
    return block->label();
  }
}

void IdealGraphPrinter::link(int index1, int index2, GrowableArray<Block>* blocks) {
  Block *block2 = blocks->adr_at(index2);
  block2->set_ancestor(index1);
}

void IdealGraphPrinter::build_dominators(GrowableArray<Block>* blocks) {

  if (blocks->length() == 0) return;

  GrowableArray<int> stack;
  stack.append(0);

  GrowableArray<Block *> array;

  assert(blocks->length() > 0, "");
  blocks->adr_at(0)->set_dominator(0);

  int n = 0;
  while(!stack.is_empty()) {
    int index = stack.pop();
    Block *block = blocks->adr_at(index);
    block->set_semi(n);
    array.append(block);
    n = n + 1;
    for (int i = 0; i < block->succs()->length(); i++) {
      int succ_index = block->succs()->at(i);
      Block *succ = blocks->adr_at(succ_index);
      if (succ->semi() == -1) {
        succ->set_parent(index);
        stack.push(succ_index);
      }
      succ->add_pred(index);
    }
  }

  for (int i=n-1; i>0; i--) {
    Block *block = array.at(i);
    int block_index = block->index();
    for (int j=0; j<block->pred()->length(); j++) {
      int pred_index = block->pred()->at(j);
      int cur_index = eval(pred_index, blocks);

      Block *cur_block = blocks->adr_at(cur_index);
      if (cur_block->semi() < block->semi()) {
        block->set_semi(cur_block->semi());
      }
    }

    int semi_index = block->semi();
    Block *semi_block = array.at(semi_index);
    semi_block->add_to_bucket(block_index);

    link(block->parent(), block_index, blocks);
    Block *parent_block = blocks->adr_at(block->parent());

    for (int j=0; j<parent_block->bucket()->length(); j++) {
      int cur_index = parent_block->bucket()->at(j);
      int new_index = eval(cur_index, blocks);
      Block *cur_block = blocks->adr_at(cur_index);
      Block *new_block = blocks->adr_at(new_index);
      int dom = block->parent();

      if (new_block->semi() < cur_block->semi()) {
        dom = new_index;
      }

      cur_block->set_dominator(dom);
    }

    parent_block->clear_bucket();
  }

  for (int i=1; i < n; i++) {

    Block *block = array.at(i);
    int block_index = block->index();

    int semi_index = block->semi();
    Block *semi_block = array.at(semi_index);

    if (block->dominator() != semi_block->index()) {
      int new_dom = blocks->adr_at(block->dominator())->dominator();
      block->set_dominator(new_dom);
    }
  }

  for (int i = 0; i < blocks->length(); i++) {
    if (blocks->adr_at(i)->dominator() == -1) {
      blocks->adr_at(i)->set_dominator(0);
    }
  }

  // Build dominates array
  for (int i=1; i < blocks->length(); i++) {
    Block *block = blocks->adr_at(i);
    int dominator = block->dominator();
    Block *dom_block = blocks->adr_at(dominator);
    dom_block->add_dominates(i);
    dom_block->add_child(i);

    while(dominator != 0) {
      dominator = dom_block->dominator();
      dom_block = blocks->adr_at(dominator);
      dom_block->add_child(i);
    }
  }
}

void IdealGraphPrinter::build_common_dominator(int **common_dominator, int index, GrowableArray<Block>* blocks) {

  common_dominator[index][index] = index;
  Block *block = blocks->adr_at(index);
  for (int i = 0; i < block->dominates()->length(); i++) {
    Block *dominated = blocks->adr_at(block->dominates()->at(i));

    for (int j=0; j<dominated->children()->length(); j++) {
      Block *child = blocks->adr_at(dominated->children()->at(j));
      common_dominator[index][child->index()] = common_dominator[child->index()][index] = index;

      for (int k=0; k<i; k++) {
        Block *other_dominated = blocks->adr_at(block->dominates()->at(k));
        common_dominator[child->index()][other_dominated->index()] = common_dominator[other_dominated->index()][child->index()] = index;

        for (int l=0 ; l<other_dominated->children()->length(); l++) {
          Block *other_child = blocks->adr_at(other_dominated->children()->at(l));
          common_dominator[child->index()][other_child->index()] = common_dominator[other_child->index()][child->index()] = index;
        }
      }
    }

    build_common_dominator(common_dominator, dominated->index(), blocks);
  }
}

void IdealGraphPrinter::schedule_latest(int **common_dominator, GrowableArray<Block>* blocks) {

  int queue_size = _nodes.length() + 1;
  NodeDescription **queue = NEW_RESOURCE_ARRAY(NodeDescription *, queue_size);
  int queue_start = 0;
  int queue_end = 0;
  Arena *a = new Arena();
  VectorSet on_queue(a);

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc) {
      desc->init_succs();
    }
  }

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc) {
      for (uint j=0; j<desc->node()->len(); j++) {
        Node *n = desc->node()->in(j);
        if (n) {
          NodeDescription *other_desc = _nodes.at(n->_idx);
          other_desc->add_succ(desc);
        }
      }
    }
  }

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc && desc->block_index() == -1) {

      // Put Phi into same block as region
      if (desc->node()->is_Phi() && desc->node()->in(0) && _nodes.at(desc->node()->in(0)->_idx)->block_index() != -1) {
        int index = _nodes.at(desc->node()->in(0)->_idx)->block_index();
        desc->set_block_index(index);
        blocks->adr_at(index)->add_node(desc);

      // Put Projections to same block as parent
      } else if (desc->node()->is_block_proj() && _nodes.at(desc->node()->is_block_proj()->_idx)->block_index() != -1) {
        int index = _nodes.at(desc->node()->is_block_proj()->_idx)->block_index();
        desc->set_block_index(index);
        blocks->adr_at(index)->add_node(desc);
      } else {
        queue[queue_end] = desc;
        queue_end++;
        on_queue.set(desc->node()->_idx);
      }
    }
  }


  int z = 0;
  while(queue_start != queue_end && z < 10000) {

    NodeDescription *desc = queue[queue_start];
    queue_start = (queue_start + 1) % queue_size;
    on_queue >>= desc->node()->_idx;

    Node* node = desc->node();

    if (desc->succs()->length() == 0) {
      int x = 0;
    }

    int block_index = -1;
    if (desc->succs()->length() != 0) {
      for (int i = 0; i < desc->succs()->length(); i++) {
          NodeDescription *cur_desc = desc->succs()->at(i);
          if (cur_desc != desc) {
            if (cur_desc->succs()->length() == 0) {

              // Ignore nodes with 0 successors

            } else if (cur_desc->block_index() == -1) {

              // Let this node schedule first
              block_index = -1;
              break;

            } else if (cur_desc->node()->is_Phi()){

              // Special treatment for Phi functions
              PhiNode *phi = cur_desc->node()->as_Phi();
              assert(phi->in(0) && phi->in(0)->is_Region(), "Must have region node in first input");
              RegionNode *region = phi->in(0)->as_Region();

              for (uint j=1; j<phi->len(); j++) {
                Node *cur_phi_input = phi->in(j);
                if (cur_phi_input == desc->node() && region->in(j)) {
                  NodeDescription *cur_region_input = _nodes.at(region->in(j)->_idx);
                  if (cur_region_input->block_index() == -1) {

                    // Let this node schedule first
                    block_index = -1;
                    break;
                  } else {
                    if (block_index == -1) {
                      block_index = cur_region_input->block_index();
                    } else {
                      block_index = common_dominator[block_index][cur_region_input->block_index()];
                    }
                  }
                }
              }

            } else {
              if (block_index == -1) {
                block_index = cur_desc->block_index();
              } else {
                block_index = common_dominator[block_index][cur_desc->block_index()];
              }
            }
          }
      }
    }

    if (block_index == -1) {
      queue[queue_end] = desc;
      queue_end = (queue_end + 1) % queue_size;
      on_queue.set(desc->node()->_idx);
      z++;
    } else {
      assert(desc->block_index() == -1, "");
      desc->set_block_index(block_index);
      blocks->adr_at(block_index)->add_node(desc);
      z = 0;
    }
  }

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc && desc->block_index() == -1) {

      //if (desc->node()->is_Proj() || desc->node()->is_Con()) {
        Node *parent = desc->node()->in(0);
        uint cur = 1;
        while(!parent && cur < desc->node()->len()) {
          parent = desc->node()->in(cur);
          cur++;
        }

        if (parent && _nodes.at(parent->_idx)->block_index() != -1) {
          int index = _nodes.at(parent->_idx)->block_index();
          desc->set_block_index(index);
          blocks->adr_at(index)->add_node(desc);
        } else {
          desc->set_block_index(0);
          blocks->adr_at(0)->add_node(desc);
          //ShouldNotReachHere();
        }
      //}
      /*
      if (desc->node()->is_block_proj() && _nodes.at(desc->node()->is_block_proj()->_idx)->block_index() != -1) {
        int index = _nodes.at(desc->node()->is_block_proj()->_idx)->block_index();
        desc->set_block_index(index);
        blocks->adr_at(index)->add_node(desc);
      } */
    }
  }

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc) {
      desc->clear_succs();
    }
  }

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc) {
      int block_index = desc->block_index();

      assert(block_index >= 0 && block_index < blocks->length(), "Block index must be in range");
      assert(blocks->adr_at(block_index)->nodes()->contains(desc), "Node must be child of block");
    }
  }
  a->destruct_contents();
}

void IdealGraphPrinter::build_blocks(Node *root) {

  Arena *a = new Arena();
  Node_Stack stack(a, 100);

  VectorSet visited(a);
  stack.push(root, 0);
  GrowableArray<Block> blocks(a, 2, 0, Block(0));

  for (int i = 0; i < _nodes.length(); i++) {
    if (_nodes.at(i)) _nodes.at(i)->set_block_index(-1);
  }


  // Order nodes such that node index is equal to idx
  for (int i = 0; i < _nodes.length(); i++) {

    if (_nodes.at(i)) {
      NodeDescription *node = _nodes.at(i);
      int index = node->node()->_idx;
      if (index != i) {
        _nodes.at_grow(index);
        NodeDescription *tmp = _nodes.at(index);
        *(_nodes.adr_at(index)) = node;
        *(_nodes.adr_at(i)) = tmp;
        i--;
      }
    }
  }

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *node = _nodes.at(i);
    if (node) {
      assert(node->node()->_idx == (uint)i, "");
    }
  }

  while(stack.is_nonempty()) {

    //Node *n = stack.node();
    //int index = stack.index();
    Node *proj = stack.node();//n->in(index);
    const Node *parent = proj->is_block_proj();
    if (parent == NULL) {
      parent = proj;
    }

    if (!visited.test_set(parent->_idx)) {

      NodeDescription *end_desc = _nodes.at(parent->_idx);
      int block_index = blocks.length();
      Block block(block_index);
      blocks.append(block);
      Block *b = blocks.adr_at(block_index);
      b->set_start(end_desc);
     // assert(end_desc->block_index() == -1, "");
      end_desc->set_block_index(block_index);
      b->add_node(end_desc);

      // Skip any control-pinned middle'in stuff
      Node *p = proj;
      NodeDescription *start_desc = NULL;
      do {
        proj = p;                   // Update pointer to last Control
        if (p->in(0) == NULL) {
          start_desc = end_desc;
          break;
        }
        p = p->in(0);               // Move control forward
        start_desc = _nodes.at(p->_idx);
        assert(start_desc, "");

        if (start_desc != end_desc && start_desc->block_index() == -1) {
          assert(start_desc->block_index() == -1, "");
          assert(block_index < blocks.length(), "");
          start_desc->set_block_index(block_index);
          b->add_node(start_desc);
        }
     } while( !p->is_block_proj() &&
               !p->is_block_start() );

      for (uint i = 0; i < start_desc->node()->len(); i++) {

          Node *pred_node = start_desc->node()->in(i);


          if (pred_node && pred_node != start_desc->node()) {
            const Node *cur_parent = pred_node->is_block_proj();
            if (cur_parent != NULL) {
              pred_node = (Node *)cur_parent;
            }

            NodeDescription *pred_node_desc = _nodes.at(pred_node->_idx);
            if (pred_node_desc->block_index() != -1) {
              blocks.adr_at(pred_node_desc->block_index())->add_succ(block_index);
            }
          }
      }

      for (DUIterator_Fast dmax, i = end_desc->node()->fast_outs(dmax); i < dmax; i++) {
        Node* cur_succ = end_desc->node()->fast_out(i);
        NodeDescription *cur_succ_desc = _nodes.at(cur_succ->_idx);

        DUIterator_Fast dmax2, i2 = cur_succ->fast_outs(dmax2);
        if (cur_succ->is_block_proj() && i2 < dmax2 && !cur_succ->is_Root()) {

          for (; i2<dmax2; i2++) {
            Node *cur_succ2 = cur_succ->fast_out(i2);
            if (cur_succ2) {
              cur_succ_desc = _nodes.at(cur_succ2->_idx);
              if (cur_succ_desc == NULL) {
                // dead node so skip it
                continue;
              }
              if (cur_succ2 != end_desc->node() && cur_succ_desc->block_index() != -1) {
                b->add_succ(cur_succ_desc->block_index());
              }
            }
          }

        } else {

          if (cur_succ != end_desc->node() && cur_succ_desc && cur_succ_desc->block_index() != -1) {
            b->add_succ(cur_succ_desc->block_index());
          }
        }
      }


      int num_preds = p->len();
      int bottom = -1;
      if (p->is_Region() || p->is_Phi()) {
        bottom = 0;
      }

      int pushed = 0;
      for (int i=num_preds - 1; i > bottom; i--) {
        if (p->in(i) != NULL && p->in(i) != p) {
          stack.push(p->in(i), 0);
          pushed++;
        }
      }

      if (pushed == 0 && p->is_Root() && !_matcher) {
        // Special case when backedges to root are not yet built
        for (int i = 0; i < _nodes.length(); i++) {
          if (_nodes.at(i) && _nodes.at(i)->node()->is_SafePoint() && _nodes.at(i)->node()->outcnt() == 0) {
            stack.push(_nodes.at(i)->node(), 0);
          }
        }
      }

    } else {
      stack.pop();
    }
  }

  build_dominators(&blocks);

  int **common_dominator = NEW_RESOURCE_ARRAY(int *, blocks.length());
  for (int i = 0; i < blocks.length(); i++) {
    int *cur = NEW_RESOURCE_ARRAY(int, blocks.length());
    common_dominator[i] = cur;

    for (int j=0; j<blocks.length(); j++) {
      cur[j] = 0;
    }
  }

  for (int i = 0; i < blocks.length(); i++) {
    blocks.adr_at(i)->add_child(blocks.adr_at(i)->index());
  }
  build_common_dominator(common_dominator, 0, &blocks);

  schedule_latest(common_dominator, &blocks);

  start_element(CONTROL_FLOW_ELEMENT);

  for (int i = 0; i < blocks.length(); i++) {
    Block *block = blocks.adr_at(i);

    Properties props;
    props.add(new Property(BLOCK_NAME_PROPERTY, i));
    props.add(new Property(BLOCK_DOMINATOR_PROPERTY, block->dominator()));
    start_element(BLOCK_ELEMENT, &props);

    if (block->succs()->length() > 0) {
      start_element(SUCCESSORS_ELEMENT);
      for (int j=0; j<block->succs()->length(); j++) {
        int cur_index = block->succs()->at(j);
        if (cur_index != 0 /* start_block has must not have inputs */) {
          Properties properties;
          properties.add(new Property(BLOCK_NAME_PROPERTY, cur_index));
          simple_element(SUCCESSOR_ELEMENT, &properties);
        }
      }
      end_element(SUCCESSORS_ELEMENT);
    }

    start_element(NODES_ELEMENT);

    for (int j=0; j<block->nodes()->length(); j++) {
      NodeDescription *n = block->nodes()->at(j);
      Properties properties;
      properties.add(new Property(NODE_ID_PROPERTY, n->id()));
      simple_element(NODE_ELEMENT, &properties);
    }

    end_element(NODES_ELEMENT);

    end_element(BLOCK_ELEMENT);
  }


  end_element(CONTROL_FLOW_ELEMENT);

  a->destruct_contents();
}

void IdealGraphPrinter::print_method(Compile* compile, const char *name, int level, bool clear_nodes) {
  print(compile, name, (Node *)compile->root(), level, clear_nodes);
}

// Print current ideal graph
void IdealGraphPrinter::print(Compile* compile, const char *name, Node *node, int level, bool clear_nodes) {

//  if (finish && !in_method) return;
  if (!_current_method || !_should_send_method || level > PrintIdealGraphLevel) return;

  assert(_current_method, "newMethod has to be called first!");

  if (clear_nodes) {
    int x = 0;
  }

  _clear_nodes = clear_nodes;

  // Warning, unsafe cast?
  _chaitin = (PhaseChaitin *)compile->regalloc();
  _matcher = compile->matcher();


  // Update nodes
  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc) {
      desc->set_state(Invalid);
    }
  }
  Node *n = node;
  walk(n);

  // Update edges
  for (int i = 0; i < _edges.length(); i++) {
      _edges.at(i)->set_state(Invalid);
  }

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc && desc->state() != Invalid) {

      int to = desc->id();
      uint len = desc->node()->len();
      for (uint j=0; j<len; j++) {
        Node *n = desc->node()->in(j);

        if (n) {


          intptr_t from = (intptr_t)n;

          // Assert from node is valid
          /*
          bool ok = false;
          for (int k=0; k<_nodes.length(); k++) {
            NodeDescription *desc = _nodes.at(k);
            if (desc && desc->id() == from) {
              assert(desc->state() != Invalid, "");
              ok = true;
            }
          }
          assert(ok, "");*/

          uint index = j;
          if (index >= desc->node()->req()) {
            index = desc->node()->req();
          }

          print_edge(from, to, index);
        }
      }
    }
  }

  bool is_different = false;

  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc && desc->state() != Valid) {
      is_different = true;
      break;
    }
  }

  if (!is_different) {
    for (int i = 0; i < _edges.length(); i++) {
      EdgeDescription *conn = _edges.at(i);
      if (conn && conn->state() != Valid) {
        is_different = true;
        break;
      }
    }
  }

  // No changes -> do not print graph
  if (!is_different) return;

  Properties properties;
  properties.add(new Property(GRAPH_NAME_PROPERTY, (const char *)name));
  start_element(GRAPH_ELEMENT, &properties);

  start_element(NODES_ELEMENT);
  for (int i = 0; i < _nodes.length(); i++) {
    NodeDescription *desc = _nodes.at(i);
    if (desc) {
      desc->print(this);
      if (desc->state() == Invalid) {
        delete desc;
        _nodes.at_put(i, NULL);
      } else {
        desc->set_state(Valid);
      }
    }
  }
  end_element(NODES_ELEMENT);

  build_blocks(node);

  start_element(EDGES_ELEMENT);
  for (int i = 0; i < _edges.length(); i++) {
    EdgeDescription *conn = _edges.at(i);

    // Assert from and to nodes are valid
    /*
    if (!conn->state() == Invalid) {
      bool ok1 = false;
      bool ok2 = false;
      for (int j=0; j<_nodes.length(); j++) {
        NodeDescription *desc = _nodes.at(j);
        if (desc && desc->id() == conn->from()) {
          ok1 = true;
        }

        if (desc && desc->id() == conn->to()) {
          ok2 = true;
        }
      }

      assert(ok1, "from node not found!");
      assert(ok2, "to node not found!");
    }*/

    conn->print(this);
    if (conn->state() == Invalid) {
      _edges.remove_at(i);
      delete conn;
      i--;
    }
  }

  end_element(EDGES_ELEMENT);

  end_element(GRAPH_ELEMENT);

  _output->flush();
}

// Print edge
void IdealGraphPrinter::print_edge(int from, int to, int index) {

  EdgeDescription *conn = new EdgeDescription(from, to, index);
  for (int i = 0; i < _edges.length(); i++) {
    if (_edges.at(i)->equals(conn)) {
      conn->set_state(Valid);
      delete _edges.at(i);
      _edges.at_put(i, conn);
      return;
    }
  }

  _edges.append(conn);
}

extern const char *NodeClassNames[];

// Create node description
IdealGraphPrinter::NodeDescription *IdealGraphPrinter::create_node_description(Node* node) {

#ifndef PRODUCT
  node->_in_dump_cnt++;
  NodeDescription *desc = new NodeDescription(node);
  desc->properties()->add(new Property(NODE_NAME_PROPERTY, (const char *)node->Name()));

  const Type *t = node->bottom_type();
  desc->properties()->add(new Property("type", (const char *)Type::msg[t->base()]));

  desc->properties()->add(new Property("idx", node->_idx));
#ifdef ASSERT
  desc->properties()->add(new Property("debug_idx", node->_debug_idx));
#endif


  const jushort flags = node->flags();
  if (flags & Node::Flag_is_Copy) {
    desc->properties()->add(new Property("is_copy", "true"));
  }
  if (flags & Node::Flag_is_Call) {
    desc->properties()->add(new Property("is_call", "true"));
  }
  if (flags & Node::Flag_rematerialize) {
    desc->properties()->add(new Property("rematerialize", "true"));
  }
  if (flags & Node::Flag_needs_anti_dependence_check) {
    desc->properties()->add(new Property("needs_anti_dependence_check", "true"));
  }
  if (flags & Node::Flag_is_macro) {
    desc->properties()->add(new Property("is_macro", "true"));
  }
  if (flags & Node::Flag_is_Con) {
    desc->properties()->add(new Property("is_con", "true"));
  }
  if (flags & Node::Flag_is_cisc_alternate) {
    desc->properties()->add(new Property("is_cisc_alternate", "true"));
  }
  if (flags & Node::Flag_is_Branch) {
    desc->properties()->add(new Property("is_branch", "true"));
  }
  if (flags & Node::Flag_is_block_start) {
    desc->properties()->add(new Property("is_block_start", "true"));
  }
  if (flags & Node::Flag_is_Goto) {
    desc->properties()->add(new Property("is_goto", "true"));
  }
  if (flags & Node::Flag_is_dead_loop_safe) {
    desc->properties()->add(new Property("is_dead_loop_safe", "true"));
  }
  if (flags & Node::Flag_may_be_short_branch) {
    desc->properties()->add(new Property("may_be_short_branch", "true"));
  }
  if (flags & Node::Flag_is_safepoint_node) {
    desc->properties()->add(new Property("is_safepoint_node", "true"));
  }
  if (flags & Node::Flag_is_pc_relative) {
    desc->properties()->add(new Property("is_pc_relative", "true"));
  }

  if (_matcher) {
    if (_matcher->is_shared(desc->node())) {
      desc->properties()->add(new Property("is_shared", "true"));
    } else {
      desc->properties()->add(new Property("is_shared", "false"));
    }

    if (_matcher->is_dontcare(desc->node())) {
      desc->properties()->add(new Property("is_dontcare", "true"));
    } else {
      desc->properties()->add(new Property("is_dontcare", "false"));
    }
  }

  if (node->is_Proj()) {
    desc->properties()->add(new Property("con", (int)node->as_Proj()->_con));
  }

  if (node->is_Mach()) {
    desc->properties()->add(new Property("idealOpcode", (const char *)NodeClassNames[node->as_Mach()->ideal_Opcode()]));
  }





  outputStream *oldTty = tty;
  buffer[0] = 0;
  stringStream s2(buffer, sizeof(buffer) - 1);

  node->dump_spec(&s2);
  assert(s2.size() < sizeof(buffer), "size in range");
  desc->properties()->add(new Property("dump_spec", buffer));

  if (node->is_block_proj()) {
    desc->properties()->add(new Property("is_block_proj", "true"));
  }

  if (node->is_block_start()) {
    desc->properties()->add(new Property("is_block_start", "true"));
  }

  const char *short_name = "short_name";
  if (strcmp(node->Name(), "Parm") == 0 && node->as_Proj()->_con >= TypeFunc::Parms) {
      int index = node->as_Proj()->_con - TypeFunc::Parms;
      if (index >= 10) {
        desc->properties()->add(new Property(short_name, "PA"));
      } else {
        sprintf(buffer, "P%d", index);
        desc->properties()->add(new Property(short_name, buffer));
      }
  } else if (strcmp(node->Name(), "IfTrue") == 0) {
     desc->properties()->add(new Property(short_name, "T"));
  } else if (strcmp(node->Name(), "IfFalse") == 0) {
     desc->properties()->add(new Property(short_name, "F"));
  } else if ((node->is_Con() && node->is_Type()) || node->is_Proj()) {

    if (t->base() == Type::Int && t->is_int()->is_con()) {
      const TypeInt *typeInt = t->is_int();
      assert(typeInt->is_con(), "must be constant");
      jint value = typeInt->get_con();

      // max. 2 chars allowed
      if (value >= -9 && value <= 99) {
        sprintf(buffer, "%d", value);
        desc->properties()->add(new Property(short_name, buffer));
      }
      else
      {
        desc->properties()->add(new Property(short_name, "I"));
      }
    } else if (t == Type::TOP) {
      desc->properties()->add(new Property(short_name, "^"));
    } else if (t->base() == Type::Long && t->is_long()->is_con()) {
      const TypeLong *typeLong = t->is_long();
      assert(typeLong->is_con(), "must be constant");
      jlong value = typeLong->get_con();

      // max. 2 chars allowed
      if (value >= -9 && value <= 99) {
        sprintf(buffer, "%d", value);
        desc->properties()->add(new Property(short_name, buffer));
      }
      else
      {
        desc->properties()->add(new Property(short_name, "L"));
      }
    } else if (t->base() == Type::KlassPtr) {
      const TypeKlassPtr *typeKlass = t->is_klassptr();
      desc->properties()->add(new Property(short_name, "CP"));
    } else if (t->base() == Type::Control) {
      desc->properties()->add(new Property(short_name, "C"));
    } else if (t->base() == Type::Memory) {
      desc->properties()->add(new Property(short_name, "M"));
    } else if (t->base() == Type::Abio) {
      desc->properties()->add(new Property(short_name, "IO"));
    } else if (t->base() == Type::Return_Address) {
      desc->properties()->add(new Property(short_name, "RA"));
    } else if (t->base() == Type::AnyPtr) {
      desc->properties()->add(new Property(short_name, "P"));
    } else if (t->base() == Type::RawPtr) {
      desc->properties()->add(new Property(short_name, "RP"));
    } else if (t->base() == Type::AryPtr) {
      desc->properties()->add(new Property(short_name, "AP"));
    }
  }

  if (node->is_SafePoint()) {
    SafePointNode *safePointNode = node->as_SafePoint();
    if (safePointNode->jvms()) {
      stringStream bciStream;
      bciStream.print("%d ", safePointNode->jvms()->bci());
      JVMState *caller = safePointNode->jvms()->caller();
      while(caller) {
        bciStream.print("%d ", caller->bci());

        caller = caller->caller();
      }
      desc->properties()->add(new Property("bci", bciStream.as_string()));
    }
  }

  if (_chaitin && _chaitin != (PhaseChaitin *)0xdeadbeef) {
    buffer[0] = 0;
    _chaitin->dump_register(node, buffer);
    desc->properties()->add(new Property("reg", buffer));
    desc->properties()->add(new Property("lrg", _chaitin->n2lidx(node)));
  }


  node->_in_dump_cnt--;
  return desc;
#else
  return NULL;
#endif
}

void IdealGraphPrinter::pre_node(Node* node, void *env) {

  IdealGraphPrinter *printer = (IdealGraphPrinter *)env;

  NodeDescription *newDesc = printer->create_node_description(node);

  if (printer->_clear_nodes) {

    printer->_nodes.append(newDesc);
  } else {

    NodeDescription *desc = printer->_nodes.at_grow(node->_idx, NULL);

    if (desc && desc->equals(newDesc)) {
      //desc->set_state(Valid);
      //desc->set_node(node);
      delete desc;
      printer->_nodes.at_put(node->_idx, NULL);
      newDesc->set_state(Valid);
      //printer->_nodes.at_put(node->_idx, newDesc);
    } else {

      if (desc && desc->id() == newDesc->id()) {
        delete desc;
        printer->_nodes.at_put(node->_idx, NULL);
        newDesc->set_state(New);

      }

      //if (desc) {
      //  delete desc;
      //}

      //printer->_nodes.at_put(node->_idx, newDesc);
    }

    printer->_nodes.append(newDesc);
  }
}

void IdealGraphPrinter::post_node(Node* node, void *env) {
}

outputStream *IdealGraphPrinter::output() {
  return _output;
}

IdealGraphPrinter::Description::Description() {
  _state = New;
}

void IdealGraphPrinter::Description::print(IdealGraphPrinter *printer) {
  if (_state == Invalid) {
    print_removed(printer);
  } else if (_state == New) {
    print_changed(printer);
  }
}

void IdealGraphPrinter::Description::set_state(State s) {
  _state = s;
}

IdealGraphPrinter::State IdealGraphPrinter::Description::state() {
  return _state;
}

void IdealGraphPrinter::Block::set_proj(NodeDescription *n) {
  _proj = n;
}

void IdealGraphPrinter::Block::set_start(NodeDescription *n) {
  _start = n;
}

int IdealGraphPrinter::Block::semi() {
  return _semi;
}

int IdealGraphPrinter::Block::parent() {
  return _parent;
}

GrowableArray<int>* IdealGraphPrinter::Block::bucket() {
  return &_bucket;
}

GrowableArray<int>* IdealGraphPrinter::Block::children() {
  return &_children;
}

void IdealGraphPrinter::Block::add_child(int i) {
  _children.append(i);
}

GrowableArray<int>* IdealGraphPrinter::Block::dominates() {
  return &_dominates;
}

void IdealGraphPrinter::Block::add_dominates(int i) {
  _dominates.append(i);
}

void IdealGraphPrinter::Block::add_to_bucket(int i) {
  _bucket.append(i);
}

void IdealGraphPrinter::Block::clear_bucket() {
  _bucket.clear();
}

void IdealGraphPrinter::Block::set_dominator(int i) {
  _dominator = i;
}

void IdealGraphPrinter::Block::set_label(int i) {
  _label = i;
}

int IdealGraphPrinter::Block::label() {
  return _label;
}

int IdealGraphPrinter::Block::ancestor() {
  return _ancestor;
}

void IdealGraphPrinter::Block::set_ancestor(int i) {
  _ancestor = i;
}

int IdealGraphPrinter::Block::dominator() {
  return _dominator;
}

int IdealGraphPrinter::Block::index() {
  return _index;
}

void IdealGraphPrinter::Block::set_parent(int i) {
  _parent = i;
}

GrowableArray<int>* IdealGraphPrinter::Block::pred() {
  return &_pred;
}

void IdealGraphPrinter::Block::set_semi(int i) {
  _semi = i;
}

IdealGraphPrinter::Block::Block() {
}

IdealGraphPrinter::Block::Block(int index) {
  _index = index;
  _label = index;
  _semi = -1;
  _ancestor = -1;
  _dominator = -1;
}

void IdealGraphPrinter::Block::add_pred(int i) {
  _pred.append(i);
}

IdealGraphPrinter::NodeDescription *IdealGraphPrinter::Block::proj() {
  return _proj;
}

IdealGraphPrinter::NodeDescription *IdealGraphPrinter::Block::start() {
  return _start;
}

GrowableArray<int>* IdealGraphPrinter::Block::succs() {
  return &_succs;
}

void IdealGraphPrinter::Block::add_succ(int index) {

  if (this->_index == 16 && index == 15) {
    int x = 0;
  }

  if (!_succs.contains(index)) {
    _succs.append(index);
  }
}


void IdealGraphPrinter::Block::add_node(NodeDescription *n) {
  if (!_nodes.contains(n)) {
    _nodes.append(n);
  }
}

GrowableArray<IdealGraphPrinter::NodeDescription *>* IdealGraphPrinter::Block::nodes() {
  return &_nodes;
}

int IdealGraphPrinter::NodeDescription::count = 0;

IdealGraphPrinter::NodeDescription::NodeDescription(Node* node) : _node(node) {
  _id = (intptr_t)(node);
  _block_index = -1;
}

IdealGraphPrinter::NodeDescription::~NodeDescription() {
  _properties.clean();
}

// void IdealGraphPrinter::NodeDescription::set_node(Node* node) {
//   //this->_node = node;
// }

int IdealGraphPrinter::NodeDescription::block_index() {
  return _block_index;
}


GrowableArray<IdealGraphPrinter::NodeDescription *>* IdealGraphPrinter::NodeDescription::succs() {
  return &_succs;
}

void IdealGraphPrinter::NodeDescription::clear_succs() {
  _succs.clear();
}

void IdealGraphPrinter::NodeDescription::init_succs() {
  _succs = GrowableArray<NodeDescription *>();
}

void IdealGraphPrinter::NodeDescription::add_succ(NodeDescription *desc) {
  _succs.append(desc);
}

void IdealGraphPrinter::NodeDescription::set_block_index(int i) {
  _block_index = i;
}

bool IdealGraphPrinter::NodeDescription::equals(NodeDescription *desc) {
  if (desc == NULL) return false;
  if (desc->id() != id()) return false;
  return properties()->equals(desc->properties());
}

Node* IdealGraphPrinter::NodeDescription::node() {
  return _node;
}

IdealGraphPrinter::Properties* IdealGraphPrinter::NodeDescription::properties() {
  return &_properties;
}

uint IdealGraphPrinter::NodeDescription::id() {
  return _id;
}

void IdealGraphPrinter::NodeDescription::print_changed(IdealGraphPrinter *printer) {


  Properties properties;
  properties.add(new Property(NODE_ID_PROPERTY, id()));
  printer->start_element(NODE_ELEMENT, &properties);

  this->properties()->print(printer);


  printer->end_element(NODE_ELEMENT);
}

void IdealGraphPrinter::NodeDescription::print_removed(IdealGraphPrinter *printer) {

  Properties properties;
  properties.add(new Property(NODE_ID_PROPERTY, id()));
  printer->simple_element(REMOVE_NODE_ELEMENT, &properties);
}

IdealGraphPrinter::EdgeDescription::EdgeDescription(int from, int to, int index) {
  this->_from = from;
  this->_to = to;
  this->_index = index;
}

IdealGraphPrinter::EdgeDescription::~EdgeDescription() {
}

int IdealGraphPrinter::EdgeDescription::from() {
  return _from;
}

int IdealGraphPrinter::EdgeDescription::to() {
  return _to;
}

void IdealGraphPrinter::EdgeDescription::print_changed(IdealGraphPrinter *printer) {

  Properties properties;
  properties.add(new Property(INDEX_PROPERTY, _index));
  properties.add(new Property(FROM_PROPERTY, _from));
  properties.add(new Property(TO_PROPERTY, _to));
  printer->simple_element(EDGE_ELEMENT, &properties);
}

void IdealGraphPrinter::EdgeDescription::print_removed(IdealGraphPrinter *printer) {

  Properties properties;
  properties.add(new Property(INDEX_PROPERTY, _index));
  properties.add(new Property(FROM_PROPERTY, _from));
  properties.add(new Property(TO_PROPERTY, _to));
  printer->simple_element(REMOVE_EDGE_ELEMENT, &properties);
}

bool IdealGraphPrinter::EdgeDescription::equals(IdealGraphPrinter::EdgeDescription *desc) {
  if (desc == NULL) return false;
  return (_from == desc->_from && _to == desc->_to && _index == desc->_index);
}

IdealGraphPrinter::Properties::Properties() : list(new (ResourceObj::C_HEAP) GrowableArray<Property *>(2, 0, NULL, true)) {
}

IdealGraphPrinter::Properties::~Properties() {
  clean();
  delete list;
}

void IdealGraphPrinter::Properties::add(Property *p) {
  assert(p != NULL, "Property not NULL");
  list->append(p);
}

void IdealGraphPrinter::Properties::print(IdealGraphPrinter *printer) {
  printer->start_element(PROPERTIES_ELEMENT);

  for (int i = 0; i < list->length(); i++) {
    list->at(i)->print(printer);
  }

  printer->end_element(PROPERTIES_ELEMENT);
}

void IdealGraphPrinter::Properties::clean() {
  for (int i = 0; i < list->length(); i++) {
    delete list->at(i);
    list->at_put(i, NULL);
  }
  list->clear();
  assert(list->length() == 0, "List cleared");
}

void IdealGraphPrinter::Properties::remove(const char *name) {
  for (int i = 0; i < list->length(); i++) {
    if (strcmp(list->at(i)->name(), name) == 0) {
      delete list->at(i);
      list->remove_at(i);
      i--;
    }
  }
}

void IdealGraphPrinter::Properties::print_as_attributes(IdealGraphPrinter *printer) {

  for (int i = 0; i < list->length(); i++) {
    assert(list->at(i) != NULL, "Property not null!");
    printer->output()->print(" ");
    list->at(i)->print_as_attribute(printer);
  }
}

bool IdealGraphPrinter::Properties::equals(Properties* p) {
  if (p->list->length() != this->list->length()) return false;

  for (int i = 0; i < list->length(); i++) {
    assert(list->at(i) != NULL, "Property not null!");
    if (!list->at(i)->equals(p->list->at(i))) return false;
  }

  return true;
}

IdealGraphPrinter::Property::Property() {
  _name = NULL;
  _value = NULL;
}

const char *IdealGraphPrinter::Property::name() {
  return _name;
}

IdealGraphPrinter::Property::Property(const Property* p) {

  this->_name = NULL;
  this->_value = NULL;

  if (p->_name != NULL) {
    _name = dup(p->_name);
  }

  if (p->_value) {
    _value = dup(p->_value);
  }
}

IdealGraphPrinter::Property::~Property() {

  clean();
}

IdealGraphPrinter::Property::Property(const char *name, const char *value) {

  assert(name, "Name must not be null!");
  assert(value, "Value must not be null!");

  _name = dup(name);
  _value = dup(value);
}

IdealGraphPrinter::Property::Property(const char *name, int intValue) {
  _name = dup(name);

  stringStream stream;
  stream.print("%d", intValue);
  _value = dup(stream.as_string());
}

void IdealGraphPrinter::Property::clean() {
  if (_name) {
    delete _name;
    _name = NULL;
  }

  if (_value) {
    delete _value;
    _value = NULL;
  }
}


bool IdealGraphPrinter::Property::is_null() {
  return _name == NULL;
}

void IdealGraphPrinter::Property::print(IdealGraphPrinter *printer) {

  assert(!is_null(), "null properties cannot be printed!");
  Properties properties;
  properties.add(new Property(PROPERTY_NAME_PROPERTY, _name));
  printer->start_element(PROPERTY_ELEMENT, &properties, false, false);
  printer->print_xml(_value);
  printer->end_element(PROPERTY_ELEMENT, false, true);
}

void IdealGraphPrinter::Property::print_as_attribute(IdealGraphPrinter *printer) {

  printer->output()->print(_name);
  printer->output()->print("=\"");
  printer->print_xml(_value);
  printer->output()->print("\"");
}


bool IdealGraphPrinter::Property::equals(Property* p) {

  if (is_null() && p->is_null()) return true;
  if (is_null()) return false;
  if (p->is_null()) return false;

  int cmp1 = strcmp(p->_name, _name);
  if (cmp1 != 0) return false;

  int cmp2 = strcmp(p->_value, _value);
  if (cmp2 != 0) return false;

  return true;
}

void IdealGraphPrinter::print_xml(const char *value) {
  size_t len = strlen(value);

  char buf[2];
  buf[1] = 0;
  for (size_t i = 0; i < len; i++) {
    char c = value[i];

    switch(c) {
      case '<':
        output()->print("&lt;");
        break;

      case '>':
        output()->print("&gt;");
        break;

      default:
        buf[0] = c;
        output()->print(buf);
        break;
    }
  }
}

#endif
