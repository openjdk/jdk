/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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

#include "precompiled.hpp"

#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderHierarchyDCmd.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"


ClassLoaderHierarchyDCmd::ClassLoaderHierarchyDCmd(outputStream* output, bool heap)
  : DCmdWithParser(output, heap),
   _show_classes("show-classes", "Print loaded classes.", "BOOLEAN", false, "false"),
  _verbose("verbose", "Print detailed information.", "BOOLEAN", false, "false"),
  _fold("fold", "Show loaders of the same name and class as one.", "BOOLEAN", false, "true") {
  _dcmdparser.add_dcmd_option(&_show_classes);
  _dcmdparser.add_dcmd_option(&_verbose);
  _dcmdparser.add_dcmd_option(&_fold);
}

// Helper class for drawing the branches to the left of a node.
class BranchTracker : public StackObj {
  //       "<x>"
  //       " |---<y>"
  //       " |    |
  //       " |   <z>"
  //       " |    |---<z1>
  //       " |    |---<z2>
  //       ^^^^^^^ ^^^
  //        A       B

  // Some terms for the graphics:
  // - branch: vertical connection between a node's ancestor to a later sibling.
  // - branchwork: (A) the string to print as a prefix at the start of each line, contains all branches.
  // - twig (B): Length of the dashed line connecting a node to its branch.
  // - branch spacing: how many spaces between branches are printed.

public:

  enum { max_depth = 64, twig_len = 2, branch_spacing = 5 };

private:

  char _branches[max_depth];
  int _pos;

public:
  BranchTracker()
    : _pos(0) {}

  void push(bool has_branch) {
    if (_pos < max_depth) {
      _branches[_pos] = has_branch ? '|' : ' ';
    }
    _pos ++; // beyond max depth, omit branch drawing but do count on.
  }

  void pop() {
    assert(_pos > 0, "must be");
    _pos --;
  }

  void print(outputStream* st) {
    for (int i = 0; i < _pos; i ++) {
      st->print("%c%.*s", _branches[i], branch_spacing, "          ");
    }
  }

  class Mark {
    BranchTracker& _tr;
  public:
    Mark(BranchTracker& tr, bool has_branch_here)
      : _tr(tr)  { _tr.push(has_branch_here); }
    ~Mark() { _tr.pop(); }
  };

}; // end: BranchTracker

struct LoadedClassInfo : public ResourceObj {
public:
  LoadedClassInfo* _next;
  Klass* const _klass;
  const ClassLoaderData* const _cld;

  LoadedClassInfo(Klass* klass, const ClassLoaderData* cld)
    : _next(nullptr), _klass(klass), _cld(cld) {}

};

class LoaderTreeNode : public ResourceObj {

  // We walk the CLDG and, for each CLD which is findable, add
  // a tree node.
  // To add a node we need its parent node; if the parent node does not yet
  // exist - because we have not yet encountered the CLD for the parent loader -
  // we add a preliminary empty LoaderTreeNode for it. This preliminary node
  // just contains the loader oop and nothing else. Once we encounter the CLD of
  // this parent loader, we fill in all the other details.

  const oop _loader_oop;
  const ClassLoaderData* _cld; // May be null if loader never loaded anything

  LoaderTreeNode* _child;
  LoaderTreeNode* _next;

  LoadedClassInfo* _classes;
  int _num_classes;

  LoadedClassInfo* _hidden_classes;
  int _num_hidden_classes;

  // In default view, similar tree nodes (same loader class, same name or no name)
  // are folded into each other to make the output more readable.
  // _num_folded contains the number of nodes which have been folded into this
  // one.
  int _num_folded;

  // Returns Klass of loader; null for bootstrap loader
  const Klass* loader_klass() const {
    return (_loader_oop != nullptr) ? _loader_oop->klass() : nullptr;
  }

  // Returns ResourceArea-allocated class name of loader class; "" if there is no klass (bootstrap loader)
  const char* loader_class_name() const {
    const Klass* klass = loader_klass();
    return klass != nullptr ? klass->external_name() : "";
  }

  // Returns oop of loader name; null for bootstrap; null if no name was set
  oop loader_name_oop() const {
    return (_loader_oop != nullptr) ? java_lang_ClassLoader::name(_loader_oop) : nullptr;
  }

  // Returns ResourceArea-allocated name of loader, "" if none is set
  const char* loader_name() const {
    oop name_oop = loader_name_oop();
    return name_oop != nullptr ? java_lang_String::as_utf8_string(name_oop) : "";
  }

  bool is_bootstrap() const {
    if (_loader_oop == nullptr) {
      assert(_cld != nullptr && _cld->is_boot_class_loader_data(), "bootstrap loader must have CLD");
      return true;
    }
    return false;
  }

  void print_with_child_nodes(outputStream* st, BranchTracker& branchtracker,
      bool print_classes, bool verbose) const {

    assert(SafepointSynchronize::is_at_safepoint(), "invariant");

    ResourceMark rm;

    // Retrieve information.
    const Klass* const the_loader_klass = loader_klass();
    const char* const the_loader_class_name = loader_class_name();
    const char* const the_loader_name = loader_name();

    branchtracker.print(st);

    // e.g. +-- "app", jdk.internal.loader.ClassLoaders$AppClassLoader
    st->print("+%.*s", BranchTracker::twig_len, "----------");
    if (is_bootstrap()) {
      st->print(" <bootstrap>");
    } else {
      if (the_loader_name[0] != '\0') {
        st->print(" \"%s\",", the_loader_name);
      }
      st->print(" %s", the_loader_class_name);
      if (_num_folded > 0) {
        st->print(" (+ %d more)", _num_folded);
      }
    }
    st->cr();

    // Output following this node (node details and child nodes) - up to the next sibling node
    // needs to be prefixed with "|" if there is a follow up sibling.
    const bool have_sibling = _next != nullptr;
    BranchTracker::Mark trm(branchtracker, have_sibling);

    {
      // optional node details following this node needs to be prefixed with "|"
      // if there are follow up child nodes.
      const bool have_child = _child != nullptr;
      BranchTracker::Mark trm(branchtracker, have_child);

      // Empty line
      branchtracker.print(st);
      st->cr();

      const int indentation = 18;

      if (verbose) {
        branchtracker.print(st);
        st->print_cr("%*s " PTR_FORMAT, indentation, "Loader Oop:", p2i(_loader_oop));
        branchtracker.print(st);
        st->print_cr("%*s " PTR_FORMAT, indentation, "Loader Data:", p2i(_cld));
        branchtracker.print(st);
        st->print_cr("%*s " PTR_FORMAT, indentation, "Loader Klass:", p2i(the_loader_klass));

        // Empty line
        branchtracker.print(st);
        st->cr();
      }

      if (print_classes) {
        if (_classes != nullptr) {
          assert(_cld != nullptr, "we have classes, we should have a CLD");
          for (LoadedClassInfo* lci = _classes; lci; lci = lci->_next) {
            // non-strong hidden classes should not live in
            // the primary CLD of their loaders.
            assert(lci->_cld == _cld, "must be");

            branchtracker.print(st);
            if (lci == _classes) { // first iteration
              st->print("%*s ", indentation, "Classes:");
            } else {
              st->print("%*s ", indentation, "");
            }
            st->print("%s", lci->_klass->external_name());
            st->cr();
          }
          branchtracker.print(st);
          st->print("%*s ", indentation, "");
          st->print_cr("(%u class%s)", _num_classes, (_num_classes == 1) ? "" : "es");

          // Empty line
          branchtracker.print(st);
          st->cr();
        }

        if (_hidden_classes != nullptr) {
          assert(_cld != nullptr, "we have classes, we should have a CLD");
          for (LoadedClassInfo* lci = _hidden_classes; lci; lci = lci->_next) {
            branchtracker.print(st);
            if (lci == _hidden_classes) { // first iteration
              st->print("%*s ", indentation, "Hidden Classes:");
            } else {
              st->print("%*s ", indentation, "");
            }
            st->print("%s", lci->_klass->external_name());
            // For non-strong hidden classes, also print CLD if verbose. Should be a
            // different one than the primary CLD.
            assert(lci->_cld != _cld, "must be");
            if (verbose) {
              st->print("  (Loader Data: " PTR_FORMAT ")", p2i(lci->_cld));
            }
            st->cr();
          }
          branchtracker.print(st);
          st->print("%*s ", indentation, "");
          st->print_cr("(%u hidden class%s)", _num_hidden_classes,
                       (_num_hidden_classes == 1) ? "" : "es");

          // Empty line
          branchtracker.print(st);
          st->cr();
        }

      } // end: print_classes

    } // Pop branchtracker mark

    // Print children, recursively
    LoaderTreeNode* c = _child;
    while (c != nullptr) {
      c->print_with_child_nodes(st, branchtracker, print_classes, verbose);
      c = c->_next;
    }

  }

  // Helper: Attempt to fold this node into the target node. If success, returns true.
  // Folding can be done if both nodes are leaf nodes and they refer to the same loader class
  // and they have the same name or no name (note: leaf check is done by caller).
  bool can_fold_into(const LoaderTreeNode* target_node) const {
    assert(is_leaf() && target_node->is_leaf(), "must be leaf");

    // Must have the same non-null klass
    const Klass* k = loader_klass();
    if (k == nullptr || k != target_node->loader_klass()) {
      return false;
    }

    // Must have the same loader name, or none
    if (::strcmp(loader_name(), target_node->loader_name()) != 0) {
      return false;
    }

    return true;
  }

public:

  LoaderTreeNode(const oop loader_oop)
    : _loader_oop(loader_oop), _cld(nullptr), _child(nullptr), _next(nullptr),
      _classes(nullptr), _num_classes(0), _hidden_classes(nullptr),
      _num_hidden_classes(0), _num_folded(0)
    {}

  void set_cld(const ClassLoaderData* cld) {
    assert(_cld == nullptr, "there should be only one primary CLD per loader");
    _cld = cld;
  }

  void add_child(LoaderTreeNode* info) {
    info->_next = _child;
    _child = info;
  }

  void add_sibling(LoaderTreeNode* info) {
    assert(info->_next == nullptr, "must be");
    info->_next = _next;
    _next = info;
  }

  void add_classes(LoadedClassInfo* first_class, int num_classes, bool has_class_mirror_holder) {
    LoadedClassInfo** p_list_to_add_to;
    bool is_hidden = first_class->_klass->is_hidden();
    if (has_class_mirror_holder) {
      p_list_to_add_to = &_hidden_classes;
    } else {
      p_list_to_add_to = &_classes;
    }
    // Search tail.
    while ((*p_list_to_add_to) != nullptr) {
      p_list_to_add_to = &(*p_list_to_add_to)->_next;
    }
    *p_list_to_add_to = first_class;
    if (has_class_mirror_holder) {
      _num_hidden_classes += num_classes;
    } else {
      _num_classes += num_classes;
    }
  }

  LoaderTreeNode* find(const oop loader_oop) {
    LoaderTreeNode* result = nullptr;
    if (_loader_oop == loader_oop) {
      result = this;
    } else {
      LoaderTreeNode* c = _child;
      while (c != nullptr && result == nullptr) {
        result = c->find(loader_oop);
        c = c->_next;
      }
    }
    return result;
  }

  bool is_leaf() const { return _child == nullptr; }

  // Attempt to fold similar nodes among this node's children. We only fold leaf nodes
  // (no child class loaders).
  // For non-leaf nodes (class loaders with child class loaders), do this recursively.
  void fold_children() {
    LoaderTreeNode* node = _child;
    LoaderTreeNode* prev = nullptr;
    ResourceMark rm;
    while (node != nullptr) {
      LoaderTreeNode* matching_node = nullptr;
      if (node->is_leaf()) {
        // Look among the preceding node siblings for a match.
        for (LoaderTreeNode* node2 = _child; node2 != node && matching_node == nullptr;
            node2 = node2->_next) {
          if (node2->is_leaf() && node->can_fold_into(node2)) {
            matching_node = node2;
          }
        }
      } else {
        node->fold_children();
      }
      if (matching_node != nullptr) {
        // Increase fold count for the matching node and remove folded node from the child list.
        matching_node->_num_folded ++;
        assert(prev != nullptr, "Sanity"); // can never happen since we do not fold the first node.
        prev->_next = node->_next;
      } else {
        prev = node;
      }
      node = node->_next;
    }
  }

  void print_with_child_nodes(outputStream* st, bool print_classes, bool print_add_info) const {
    BranchTracker bwt;
    print_with_child_nodes(st, bwt, print_classes, print_add_info);
  }

};

class LoadedClassCollectClosure : public KlassClosure {
public:
  LoadedClassInfo* _list;
  const ClassLoaderData* _cld;
  int _num_classes;
  LoadedClassCollectClosure(const ClassLoaderData* cld)
    : _list(nullptr), _cld(cld), _num_classes(0) {}
  void do_klass(Klass* k) {
    LoadedClassInfo* lki = new LoadedClassInfo(k, _cld);
    lki->_next = _list;
    _list = lki;
    _num_classes ++;
  }
};

class LoaderInfoScanClosure : public CLDClosure {

  const bool _print_classes;
  const bool _verbose;
  LoaderTreeNode* _root;

  static void fill_in_classes(LoaderTreeNode* info, const ClassLoaderData* cld) {
    assert(info != nullptr && cld != nullptr, "must be");
    LoadedClassCollectClosure lccc(cld);
    const_cast<ClassLoaderData*>(cld)->classes_do(&lccc);
    if (lccc._num_classes > 0) {
      info->add_classes(lccc._list, lccc._num_classes, cld->has_class_mirror_holder());
    }
  }

  LoaderTreeNode* find_node_or_add_empty_node(oop loader_oop) {

    assert(_root != nullptr, "root node must exist");

    if (loader_oop == nullptr) {
      return _root;
    }

    // Check if a node for this oop already exists.
    LoaderTreeNode* info = _root->find(loader_oop);

    if (info == nullptr) {
      // It does not. Create a node.
      info = new LoaderTreeNode(loader_oop);

      // Add it to tree.
      LoaderTreeNode* parent_info = nullptr;

      // Recursively add parent nodes if needed.
      const oop parent_oop = java_lang_ClassLoader::parent(loader_oop);
      if (parent_oop == nullptr) {
        parent_info = _root;
      } else {
        parent_info = find_node_or_add_empty_node(parent_oop);
      }
      assert(parent_info != nullptr, "must be");

      parent_info->add_child(info);
    }
    return info;
  }


public:
  LoaderInfoScanClosure(bool print_classes, bool verbose)
    : _print_classes(print_classes), _verbose(verbose), _root(nullptr) {
    _root = new LoaderTreeNode(nullptr);
  }

  void print_results(outputStream* st) const {
    _root->print_with_child_nodes(st, _print_classes, _verbose);
  }

  void do_cld (ClassLoaderData* cld) {

    // We do not display unloading loaders, for now.
    if (!cld->is_alive()) {
      return;
    }

    const oop loader_oop = cld->class_loader();

    LoaderTreeNode* info = find_node_or_add_empty_node(loader_oop);
    assert(info != nullptr, "must be");

    // Update CLD in node, but only if this is the primary CLD for this loader.
    if (cld->has_class_mirror_holder() == false) {
      info->set_cld(cld);
    }

    // Add classes.
    fill_in_classes(info, cld);
  }

  void fold() {
    _root->fold_children();
  }

};


class ClassLoaderHierarchyVMOperation : public VM_Operation {
  outputStream* const _out;
  const bool _show_classes;
  const bool _verbose;
  const bool _fold;
public:
  ClassLoaderHierarchyVMOperation(outputStream* out, bool show_classes, bool verbose, bool fold) :
    _out(out), _show_classes(show_classes), _verbose(verbose), _fold(fold)
  {}

  VMOp_Type type() const {
    return VMOp_ClassLoaderHierarchyOperation;
  }

  void doit() {
    assert(SafepointSynchronize::is_at_safepoint(), "must be a safepoint");
    ResourceMark rm;
    LoaderInfoScanClosure cl (_show_classes, _verbose);
    ClassLoaderDataGraph::loaded_cld_do(&cl);
    // In non-verbose and non-show-classes mode, attempt to fold the tree.
    if (_fold) {
      if (!_verbose && !_show_classes) {
        cl.fold();
      }
    }
    cl.print_results(_out);
  }
};

// This command needs to be executed at a safepoint.
void ClassLoaderHierarchyDCmd::execute(DCmdSource source, TRAPS) {
  ClassLoaderHierarchyVMOperation op(output(), _show_classes.value(), _verbose.value(), _fold.value());
  VMThread::execute(&op);
}
