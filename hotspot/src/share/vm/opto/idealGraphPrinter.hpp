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

#ifndef PRODUCT

class Compile;
class PhaseIFG;
class PhaseChaitin;
class Matcher;
class Node;
class InlineTree;
class ciMethod;

class IdealGraphPrinter
{
private:

  enum State
  {
    Invalid,
    Valid,
    New
  };

private:

  static const char *INDENT;
  static const char *TOP_ELEMENT;
  static const char *GROUP_ELEMENT;
  static const char *GRAPH_ELEMENT;
  static const char *PROPERTIES_ELEMENT;
  static const char *EDGES_ELEMENT;
  static const char *PROPERTY_ELEMENT;
  static const char *EDGE_ELEMENT;
  static const char *NODE_ELEMENT;
  static const char *NODES_ELEMENT;
  static const char *CONTROL_FLOW_ELEMENT;
  static const char *REMOVE_EDGE_ELEMENT;
  static const char *REMOVE_NODE_ELEMENT;
  static const char *METHOD_NAME_PROPERTY;
  static const char *BLOCK_NAME_PROPERTY;
  static const char *BLOCK_DOMINATOR_PROPERTY;
  static const char *BLOCK_ELEMENT;
  static const char *SUCCESSORS_ELEMENT;
  static const char *SUCCESSOR_ELEMENT;
  static const char *METHOD_IS_PUBLIC_PROPERTY;
  static const char *METHOD_IS_STATIC_PROPERTY;
  static const char *TRUE_VALUE;
  static const char *NODE_NAME_PROPERTY;
  static const char *EDGE_NAME_PROPERTY;
  static const char *NODE_ID_PROPERTY;
  static const char *FROM_PROPERTY;
  static const char *TO_PROPERTY;
  static const char *PROPERTY_NAME_PROPERTY;
  static const char *GRAPH_NAME_PROPERTY;
  static const char *INDEX_PROPERTY;
  static const char *METHOD_ELEMENT;
  static const char *INLINE_ELEMENT;
  static const char *BYTECODES_ELEMENT;
  static const char *METHOD_BCI_PROPERTY;
  static const char *METHOD_SHORT_NAME_PROPERTY;
  static const char *ASSEMBLY_ELEMENT;

  class Property {

  private:

    const char *_name;
    const char *_value;

  public:

    Property();
    Property(const Property* p);
    ~Property();
    Property(const char *name, const char *value);
    Property(const char *name, int value);
    bool equals(Property* p);
    void print(IdealGraphPrinter *printer);
    void print_as_attribute(IdealGraphPrinter *printer);
    bool is_null();
    void clean();
    const char *name();

    static const char* dup(const char *str) {
      char * copy = new char[strlen(str)+1];
      strcpy(copy, str);
      return copy;
    }

  };

  class Properties {

  private:

    GrowableArray<Property *> *list;

  public:

    Properties();
    ~Properties();
    void add(Property *p);
    void remove(const char *name);
    bool equals(Properties* p);
    void print(IdealGraphPrinter *printer);
    void print_as_attributes(IdealGraphPrinter *printer);
    void clean();

  };


  class Description {

  private:

    State _state;

  public:

    Description();

    State state();
    void set_state(State s);
    void print(IdealGraphPrinter *printer);
    virtual void print_changed(IdealGraphPrinter *printer) = 0;
    virtual void print_removed(IdealGraphPrinter *printer) = 0;

  };

  class NodeDescription : public Description{

  public:

    static int count;

  private:

    GrowableArray<NodeDescription *> _succs;
    int _block_index;
    uintptr_t _id;
    Properties _properties;
    Node* _node;

  public:

    NodeDescription(Node* node);
    ~NodeDescription();
    Node* node();

    // void set_node(Node* node);
    GrowableArray<NodeDescription *>* succs();
    void init_succs();
    void clear_succs();
    void add_succ(NodeDescription *desc);
    int block_index();
    void set_block_index(int i);
    Properties* properties();
    virtual void print_changed(IdealGraphPrinter *printer);
    virtual void print_removed(IdealGraphPrinter *printer);
    bool equals(NodeDescription *desc);
    uint id();

  };

  class Block {

  private:

    NodeDescription *_start;
    NodeDescription *_proj;
    GrowableArray<int> _succs;
    GrowableArray<NodeDescription *> _nodes;
    GrowableArray<int> _dominates;
    GrowableArray<int> _children;
    int _semi;
    int _parent;
    GrowableArray<int> _pred;
    GrowableArray<int> _bucket;
    int _index;
    int _dominator;
    int _ancestor;
    int _label;

  public:

    Block();
    Block(int index);

    void add_node(NodeDescription *n);
    GrowableArray<NodeDescription *>* nodes();
    GrowableArray<int>* children();
    void add_child(int i);
    void add_succ(int index);
    GrowableArray<int>* succs();
    GrowableArray<int>* dominates();
    void add_dominates(int i);
    NodeDescription *start();
    NodeDescription *proj();
    void set_start(NodeDescription *n);
    void set_proj(NodeDescription *n);

    int label();
    void set_label(int i);
    int ancestor();
    void set_ancestor(int i);
    int index();
    int dominator();
    void set_dominator(int i);
    int parent();
    void set_parent(int i);
    int semi();
    GrowableArray<int>* bucket();
    void add_to_bucket(int i);
    void clear_bucket();
    GrowableArray<int>* pred();
    void set_semi(int i);
    void add_pred(int i);

  };

  class EdgeDescription : public Description {

  private:

    int _from;
    int _to;
    int _index;
  public:

    EdgeDescription(int from, int to, int index);
    ~EdgeDescription();

    virtual void print_changed(IdealGraphPrinter *printer);
    virtual void print_removed(IdealGraphPrinter *printer);
    bool equals(EdgeDescription *desc);
    int from();
    int to();
  };


  static int _file_count;
  networkStream *_stream;
  outputStream *_output;
  ciMethod *_current_method;
  GrowableArray<NodeDescription *> _nodes;
  GrowableArray<EdgeDescription *> _edges;
  int _depth;
  Arena *_arena;
  char buffer[128];
  bool _should_send_method;
  PhaseChaitin* _chaitin;
  bool _clear_nodes;
  Matcher* _matcher;
  bool _traverse_outs;

  void start_element_helper(const char *name, Properties *properties, bool endElement, bool print_indent = false, bool print_return = true);
  NodeDescription *create_node_description(Node* node);

  static void pre_node(Node* node, void *env);
  static void post_node(Node* node, void *env);

  void schedule_latest(int **common_dominator, GrowableArray<Block>* blocks);
  void build_common_dominator(int **common_dominator, int index, GrowableArray<Block>* blocks);
  void compress(int index, GrowableArray<Block>* blocks);
  int eval(int index, GrowableArray<Block>* blocks);
  void link(int index1, int index2, GrowableArray<Block>* blocks);
  void build_dominators(GrowableArray<Block>* blocks);
  void build_blocks(Node *node);
  void walk(Node *n);
  void start_element(const char *name, Properties *properties = NULL, bool print_indent = false, bool print_return = true);
  void simple_element(const char *name, Properties *properties = NULL, bool print_indent = false);
  void end_element(const char *name, bool print_indent = false, bool print_return = true);
  void print_edge(int from, int to, int index);
  void print_indent();
  void print_method(ciMethod *method, int bci, InlineTree *tree);
  void print_inline_tree(InlineTree *tree);
  void clear_nodes();

  IdealGraphPrinter();
  ~IdealGraphPrinter();

public:

  static void clean_up();
  static IdealGraphPrinter *printer();

  bool traverse_outs();
  void set_traverse_outs(bool b);
  void print_ifg(PhaseIFG* ifg);
  outputStream *output();
  void print_inlining(Compile* compile);
  void begin_method(Compile* compile);
  void end_method();
  void print_method(Compile* compile, const char *name, int level=1, bool clear_nodes = false);
  void print(Compile* compile, const char *name, Node *root, int level=1, bool clear_nodes = false);
  void print_xml(const char *name);


};

#endif
