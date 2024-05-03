/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/block.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/idealGraphPrinter.hpp"
#include "opto/loopnode.hpp"
#include "opto/machnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/regalloc.hpp"
#include "opto/rootnode.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"

//=============================================================================
#define NODE_HASH_MINIMUM_SIZE    255

//------------------------------NodeHash---------------------------------------
NodeHash::NodeHash(Arena *arena, uint est_max_size) :
  _a(arena),
  _max( round_up(est_max_size < NODE_HASH_MINIMUM_SIZE ? NODE_HASH_MINIMUM_SIZE : est_max_size) ),
  _inserts(0), _insert_limit( insert_limit() ),
  _table( NEW_ARENA_ARRAY( _a , Node* , _max ) )
#ifndef PRODUCT
  , _grows(0),_look_probes(0), _lookup_hits(0), _lookup_misses(0),
  _insert_probes(0), _delete_probes(0), _delete_hits(0), _delete_misses(0),
   _total_inserts(0), _total_insert_probes(0)
#endif
{
  // _sentinel must be in the current node space
  _sentinel = new ProjNode(nullptr, TypeFunc::Control);
  memset(_table,0,sizeof(Node*)*_max);
}

//------------------------------hash_find--------------------------------------
// Find in hash table
Node *NodeHash::hash_find( const Node *n ) {
  // ((Node*)n)->set_hash( n->hash() );
  uint hash = n->hash();
  if (hash == Node::NO_HASH) {
    NOT_PRODUCT( _lookup_misses++ );
    return nullptr;
  }
  uint key = hash & (_max-1);
  uint stride = key | 0x01;
  NOT_PRODUCT( _look_probes++ );
  Node *k = _table[key];        // Get hashed value
  if( !k ) {                    // ?Miss?
    NOT_PRODUCT( _lookup_misses++ );
    return nullptr;             // Miss!
  }

  int op = n->Opcode();
  uint req = n->req();
  while( 1 ) {                  // While probing hash table
    if( k->req() == req &&      // Same count of inputs
        k->Opcode() == op ) {   // Same Opcode
      for( uint i=0; i<req; i++ )
        if( n->in(i)!=k->in(i)) // Different inputs?
          goto collision;       // "goto" is a speed hack...
      if( n->cmp(*k) ) {        // Check for any special bits
        NOT_PRODUCT( _lookup_hits++ );
        return k;               // Hit!
      }
    }
  collision:
    NOT_PRODUCT( _look_probes++ );
    key = (key + stride/*7*/) & (_max-1); // Stride through table with relative prime
    k = _table[key];            // Get hashed value
    if( !k ) {                  // ?Miss?
      NOT_PRODUCT( _lookup_misses++ );
      return nullptr;           // Miss!
    }
  }
  ShouldNotReachHere();
  return nullptr;
}

//------------------------------hash_find_insert-------------------------------
// Find in hash table, insert if not already present
// Used to preserve unique entries in hash table
Node *NodeHash::hash_find_insert( Node *n ) {
  // n->set_hash( );
  uint hash = n->hash();
  if (hash == Node::NO_HASH) {
    NOT_PRODUCT( _lookup_misses++ );
    return nullptr;
  }
  uint key = hash & (_max-1);
  uint stride = key | 0x01;     // stride must be relatively prime to table siz
  uint first_sentinel = 0;      // replace a sentinel if seen.
  NOT_PRODUCT( _look_probes++ );
  Node *k = _table[key];        // Get hashed value
  if( !k ) {                    // ?Miss?
    NOT_PRODUCT( _lookup_misses++ );
    _table[key] = n;            // Insert into table!
    debug_only(n->enter_hash_lock()); // Lock down the node while in the table.
    check_grow();               // Grow table if insert hit limit
    return nullptr;             // Miss!
  }
  else if( k == _sentinel ) {
    first_sentinel = key;      // Can insert here
  }

  int op = n->Opcode();
  uint req = n->req();
  while( 1 ) {                  // While probing hash table
    if( k->req() == req &&      // Same count of inputs
        k->Opcode() == op ) {   // Same Opcode
      for( uint i=0; i<req; i++ )
        if( n->in(i)!=k->in(i)) // Different inputs?
          goto collision;       // "goto" is a speed hack...
      if( n->cmp(*k) ) {        // Check for any special bits
        NOT_PRODUCT( _lookup_hits++ );
        return k;               // Hit!
      }
    }
  collision:
    NOT_PRODUCT( _look_probes++ );
    key = (key + stride) & (_max-1); // Stride through table w/ relative prime
    k = _table[key];            // Get hashed value
    if( !k ) {                  // ?Miss?
      NOT_PRODUCT( _lookup_misses++ );
      key = (first_sentinel == 0) ? key : first_sentinel; // ?saw sentinel?
      _table[key] = n;          // Insert into table!
      debug_only(n->enter_hash_lock()); // Lock down the node while in the table.
      check_grow();             // Grow table if insert hit limit
      return nullptr;           // Miss!
    }
    else if( first_sentinel == 0 && k == _sentinel ) {
      first_sentinel = key;    // Can insert here
    }

  }
  ShouldNotReachHere();
  return nullptr;
}

//------------------------------hash_insert------------------------------------
// Insert into hash table
void NodeHash::hash_insert( Node *n ) {
  // // "conflict" comments -- print nodes that conflict
  // bool conflict = false;
  // n->set_hash();
  uint hash = n->hash();
  if (hash == Node::NO_HASH) {
    return;
  }
  check_grow();
  uint key = hash & (_max-1);
  uint stride = key | 0x01;

  while( 1 ) {                  // While probing hash table
    NOT_PRODUCT( _insert_probes++ );
    Node *k = _table[key];      // Get hashed value
    if( !k || (k == _sentinel) ) break;       // Found a slot
    assert( k != n, "already inserted" );
    // if( PrintCompilation && PrintOptoStatistics && Verbose ) { tty->print("  conflict: "); k->dump(); conflict = true; }
    key = (key + stride) & (_max-1); // Stride through table w/ relative prime
  }
  _table[key] = n;              // Insert into table!
  debug_only(n->enter_hash_lock()); // Lock down the node while in the table.
  // if( conflict ) { n->dump(); }
}

//------------------------------hash_delete------------------------------------
// Replace in hash table with sentinel
bool NodeHash::hash_delete( const Node *n ) {
  Node *k;
  uint hash = n->hash();
  if (hash == Node::NO_HASH) {
    NOT_PRODUCT( _delete_misses++ );
    return false;
  }
  uint key = hash & (_max-1);
  uint stride = key | 0x01;
  debug_only( uint counter = 0; );
  for( ; /* (k != nullptr) && (k != _sentinel) */; ) {
    debug_only( counter++ );
    NOT_PRODUCT( _delete_probes++ );
    k = _table[key];            // Get hashed value
    if( !k ) {                  // Miss?
      NOT_PRODUCT( _delete_misses++ );
      return false;             // Miss! Not in chain
    }
    else if( n == k ) {
      NOT_PRODUCT( _delete_hits++ );
      _table[key] = _sentinel;  // Hit! Label as deleted entry
      debug_only(((Node*)n)->exit_hash_lock()); // Unlock the node upon removal from table.
      return true;
    }
    else {
      // collision: move through table with prime offset
      key = (key + stride/*7*/) & (_max-1);
      assert( counter <= _insert_limit, "Cycle in hash-table");
    }
  }
  ShouldNotReachHere();
  return false;
}

//------------------------------round_up---------------------------------------
// Round up to nearest power of 2
uint NodeHash::round_up(uint x) {
  x += (x >> 2);                  // Add 25% slop
  return MAX2(16U, round_up_power_of_2(x));
}

//------------------------------grow-------------------------------------------
// Grow _table to next power of 2 and insert old entries
void  NodeHash::grow() {
  // Record old state
  uint   old_max   = _max;
  Node **old_table = _table;
  // Construct new table with twice the space
#ifndef PRODUCT
  _grows++;
  _total_inserts       += _inserts;
  _total_insert_probes += _insert_probes;
  _insert_probes   = 0;
#endif
  _inserts         = 0;
  _max     = _max << 1;
  _table   = NEW_ARENA_ARRAY( _a , Node* , _max ); // (Node**)_a->Amalloc( _max * sizeof(Node*) );
  memset(_table,0,sizeof(Node*)*_max);
  _insert_limit = insert_limit();
  // Insert old entries into the new table
  for( uint i = 0; i < old_max; i++ ) {
    Node *m = *old_table++;
    if( !m || m == _sentinel ) continue;
    debug_only(m->exit_hash_lock()); // Unlock the node upon removal from old table.
    hash_insert(m);
  }
}

//------------------------------clear------------------------------------------
// Clear all entries in _table to null but keep storage
void  NodeHash::clear() {
#ifdef ASSERT
  // Unlock all nodes upon removal from table.
  for (uint i = 0; i < _max; i++) {
    Node* n = _table[i];
    if (!n || n == _sentinel)  continue;
    n->exit_hash_lock();
  }
#endif

  memset( _table, 0, _max * sizeof(Node*) );
}

//-----------------------remove_useless_nodes----------------------------------
// Remove useless nodes from value table,
// implementation does not depend on hash function
void NodeHash::remove_useless_nodes(VectorSet &useful) {

  // Dead nodes in the hash table inherited from GVN should not replace
  // existing nodes, remove dead nodes.
  uint max = size();
  Node *sentinel_node = sentinel();
  for( uint i = 0; i < max; ++i ) {
    Node *n = at(i);
    if(n != nullptr && n != sentinel_node && !useful.test(n->_idx)) {
      debug_only(n->exit_hash_lock()); // Unlock the node when removed
      _table[i] = sentinel_node;       // Replace with placeholder
    }
  }
}


void NodeHash::check_no_speculative_types() {
#ifdef ASSERT
  uint max = size();
  Unique_Node_List live_nodes;
  Compile::current()->identify_useful_nodes(live_nodes);
  Node *sentinel_node = sentinel();
  for (uint i = 0; i < max; ++i) {
    Node *n = at(i);
    if (n != nullptr &&
        n != sentinel_node &&
        n->is_Type() &&
        live_nodes.member(n)) {
      TypeNode* tn = n->as_Type();
      const Type* t = tn->type();
      const Type* t_no_spec = t->remove_speculative();
      assert(t == t_no_spec, "dead node in hash table or missed node during speculative cleanup");
    }
  }
#endif
}

#ifndef PRODUCT
//------------------------------dump-------------------------------------------
// Dump statistics for the hash table
void NodeHash::dump() {
  _total_inserts       += _inserts;
  _total_insert_probes += _insert_probes;
  if (PrintCompilation && PrintOptoStatistics && Verbose && (_inserts > 0)) {
    if (WizardMode) {
      for (uint i=0; i<_max; i++) {
        if (_table[i])
          tty->print("%d/%d/%d ",i,_table[i]->hash()&(_max-1),_table[i]->_idx);
      }
    }
    tty->print("\nGVN Hash stats:  %d grows to %d max_size\n", _grows, _max);
    tty->print("  %d/%d (%8.1f%% full)\n", _inserts, _max, (double)_inserts/_max*100.0);
    tty->print("  %dp/(%dh+%dm) (%8.2f probes/lookup)\n", _look_probes, _lookup_hits, _lookup_misses, (double)_look_probes/(_lookup_hits+_lookup_misses));
    tty->print("  %dp/%di (%8.2f probes/insert)\n", _total_insert_probes, _total_inserts, (double)_total_insert_probes/_total_inserts);
    // sentinels increase lookup cost, but not insert cost
    assert((_lookup_misses+_lookup_hits)*4+100 >= _look_probes, "bad hash function");
    assert( _inserts+(_inserts>>3) < _max, "table too full" );
    assert( _inserts*3+100 >= _insert_probes, "bad hash function" );
  }
}

Node *NodeHash::find_index(uint idx) { // For debugging
  // Find an entry by its index value
  for( uint i = 0; i < _max; i++ ) {
    Node *m = _table[i];
    if( !m || m == _sentinel ) continue;
    if( m->_idx == (uint)idx ) return m;
  }
  return nullptr;
}
#endif

#ifdef ASSERT
NodeHash::~NodeHash() {
  // Unlock all nodes upon destruction of table.
  if (_table != (Node**)badAddress)  clear();
}
#endif


//=============================================================================
//------------------------------PhaseRemoveUseless-----------------------------
// 1) Use a breadthfirst walk to collect useful nodes reachable from root.
PhaseRemoveUseless::PhaseRemoveUseless(PhaseGVN* gvn, Unique_Node_List& worklist, PhaseNumber phase_num) : Phase(phase_num) {
  C->print_method(PHASE_BEFORE_REMOVEUSELESS, 3);
  // Implementation requires an edge from root to each SafePointNode
  // at a backward branch. Inserted in add_safepoint().

  // Identify nodes that are reachable from below, useful.
  C->identify_useful_nodes(_useful);
  // Update dead node list
  C->update_dead_node_list(_useful);

  // Remove all useless nodes from PhaseValues' recorded types
  // Must be done before disconnecting nodes to preserve hash-table-invariant
  gvn->remove_useless_nodes(_useful.member_set());

  // Remove all useless nodes from future worklist
  worklist.remove_useless_nodes(_useful.member_set());

  // Disconnect 'useless' nodes that are adjacent to useful nodes
  C->disconnect_useless_nodes(_useful, worklist);
}

//=============================================================================
//------------------------------PhaseRenumberLive------------------------------
// First, remove useless nodes (equivalent to identifying live nodes).
// Then, renumber live nodes.
//
// The set of live nodes is returned by PhaseRemoveUseless in the _useful structure.
// If the number of live nodes is 'x' (where 'x' == _useful.size()), then the
// PhaseRenumberLive updates the node ID of each node (the _idx field) with a unique
// value in the range [0, x).
//
// At the end of the PhaseRenumberLive phase, the compiler's count of unique nodes is
// updated to 'x' and the list of dead nodes is reset (as there are no dead nodes).
//
// The PhaseRenumberLive phase updates two data structures with the new node IDs.
// (1) The "worklist" is "C->igvn_worklist()", which is to collect which nodes need to
//     be processed by IGVN after removal of the useless nodes.
// (2) Type information "gvn->types()" (same as "C->types()") maps every node ID to
//     the node's type. The mapping is updated to use the new node IDs as well. We
//     create a new map, and swap it with the old one.
//
// Other data structures used by the compiler are not updated. The hash table for value
// numbering ("C->node_hash()", referenced by PhaseValue::_table) is not updated because
// computing the hash values is not based on node IDs.
PhaseRenumberLive::PhaseRenumberLive(PhaseGVN* gvn,
                                     Unique_Node_List& worklist,
                                     PhaseNumber phase_num) :
  PhaseRemoveUseless(gvn, worklist, Remove_Useless_And_Renumber_Live),
  _new_type_array(C->comp_arena()),
  _old2new_map(C->unique(), C->unique(), -1),
  _is_pass_finished(false),
  _live_node_count(C->live_nodes())
{
  assert(RenumberLiveNodes, "RenumberLiveNodes must be set to true for node renumbering to take place");
  assert(C->live_nodes() == _useful.size(), "the number of live nodes must match the number of useful nodes");
  assert(_delayed.size() == 0, "should be empty");
  assert(&worklist == C->igvn_worklist(), "reference still same as the one from Compile");
  assert(&gvn->types() == C->types(), "reference still same as that from Compile");

  GrowableArray<Node_Notes*>* old_node_note_array = C->node_note_array();
  if (old_node_note_array != nullptr) {
    int new_size = (_useful.size() >> 8) + 1; // The node note array uses blocks, see C->_log2_node_notes_block_size
    new_size = MAX2(8, new_size);
    C->set_node_note_array(new (C->comp_arena()) GrowableArray<Node_Notes*> (C->comp_arena(), new_size, 0, nullptr));
    C->grow_node_notes(C->node_note_array(), new_size);
  }

  assert(worklist.is_subset_of(_useful), "only useful nodes should still be in the worklist");

  // Iterate over the set of live nodes.
  for (uint current_idx = 0; current_idx < _useful.size(); current_idx++) {
    Node* n = _useful.at(current_idx);

    const Type* type = gvn->type_or_null(n);
    _new_type_array.map(current_idx, type);

    assert(_old2new_map.at(n->_idx) == -1, "already seen");
    _old2new_map.at_put(n->_idx, current_idx);

    if (old_node_note_array != nullptr) {
      Node_Notes* nn = C->locate_node_notes(old_node_note_array, n->_idx);
      C->set_node_notes_at(current_idx, nn);
    }

    n->set_idx(current_idx); // Update node ID.

    if (update_embedded_ids(n) < 0) {
      _delayed.push(n); // has embedded IDs; handle later
    }
  }

  // VectorSet in Unique_Node_Set must be recomputed, since IDs have changed.
  worklist.recompute_idx_set();

  assert(_live_node_count == _useful.size(), "all live nodes must be processed");

  _is_pass_finished = true; // pass finished; safe to process delayed updates

  while (_delayed.size() > 0) {
    Node* n = _delayed.pop();
    int no_of_updates = update_embedded_ids(n);
    assert(no_of_updates > 0, "should be updated");
  }

  // Replace the compiler's type information with the updated type information.
  gvn->types().swap(_new_type_array);

  // Update the unique node count of the compilation to the number of currently live nodes.
  C->set_unique(_live_node_count);

  // Set the dead node count to 0 and reset dead node list.
  C->reset_dead_node_list();
}

int PhaseRenumberLive::new_index(int old_idx) {
  assert(_is_pass_finished, "not finished");
  if (_old2new_map.at(old_idx) == -1) { // absent
    // Allocate a placeholder to preserve uniqueness
    _old2new_map.at_put(old_idx, _live_node_count);
    _live_node_count++;
  }
  return _old2new_map.at(old_idx);
}

int PhaseRenumberLive::update_embedded_ids(Node* n) {
  int no_of_updates = 0;
  if (n->is_Phi()) {
    PhiNode* phi = n->as_Phi();
    if (phi->_inst_id != -1) {
      if (!_is_pass_finished) {
        return -1; // delay
      }
      int new_idx = new_index(phi->_inst_id);
      assert(new_idx != -1, "");
      phi->_inst_id = new_idx;
      no_of_updates++;
    }
    if (phi->_inst_mem_id != -1) {
      if (!_is_pass_finished) {
        return -1; // delay
      }
      int new_idx = new_index(phi->_inst_mem_id);
      assert(new_idx != -1, "");
      phi->_inst_mem_id = new_idx;
      no_of_updates++;
    }
  }

  const Type* type = _new_type_array.fast_lookup(n->_idx);
  if (type != nullptr && type->isa_oopptr() && type->is_oopptr()->is_known_instance()) {
    if (!_is_pass_finished) {
        return -1; // delay
    }
    int old_idx = type->is_oopptr()->instance_id();
    int new_idx = new_index(old_idx);
    const Type* new_type = type->is_oopptr()->with_instance_id(new_idx);
    _new_type_array.map(n->_idx, new_type);
    no_of_updates++;
  }

  return no_of_updates;
}

void PhaseValues::init_con_caches() {
  memset(_icons,0,sizeof(_icons));
  memset(_lcons,0,sizeof(_lcons));
  memset(_zcons,0,sizeof(_zcons));
}

//--------------------------------find_int_type--------------------------------
const TypeInt* PhaseValues::find_int_type(Node* n) {
  if (n == nullptr)  return nullptr;
  // Call type_or_null(n) to determine node's type since we might be in
  // parse phase and call n->Value() may return wrong type.
  // (For example, a phi node at the beginning of loop parsing is not ready.)
  const Type* t = type_or_null(n);
  if (t == nullptr)  return nullptr;
  return t->isa_int();
}


//-------------------------------find_long_type--------------------------------
const TypeLong* PhaseValues::find_long_type(Node* n) {
  if (n == nullptr)  return nullptr;
  // (See comment above on type_or_null.)
  const Type* t = type_or_null(n);
  if (t == nullptr)  return nullptr;
  return t->isa_long();
}

//------------------------------~PhaseValues-----------------------------------
#ifndef PRODUCT
PhaseValues::~PhaseValues() {
  // Statistics for NodeHash
  _table.dump();
  // Statistics for value progress and efficiency
  if( PrintCompilation && Verbose && WizardMode ) {
    tty->print("\n%sValues: %d nodes ---> %d/%d (%d)",
      is_IterGVN() ? "Iter" : "    ", C->unique(), made_progress(), made_transforms(), made_new_values());
    if( made_transforms() != 0 ) {
      tty->print_cr("  ratio %f", made_progress()/(float)made_transforms() );
    } else {
      tty->cr();
    }
  }
}
#endif

//------------------------------makecon----------------------------------------
ConNode* PhaseValues::makecon(const Type* t) {
  assert(t->singleton(), "must be a constant");
  assert(!t->empty() || t == Type::TOP, "must not be vacuous range");
  switch (t->base()) {  // fast paths
  case Type::Half:
  case Type::Top:  return (ConNode*) C->top();
  case Type::Int:  return intcon( t->is_int()->get_con() );
  case Type::Long: return longcon( t->is_long()->get_con() );
  default:         break;
  }
  if (t->is_zero_type())
    return zerocon(t->basic_type());
  return uncached_makecon(t);
}

//--------------------------uncached_makecon-----------------------------------
// Make an idealized constant - one of ConINode, ConPNode, etc.
ConNode* PhaseValues::uncached_makecon(const Type *t) {
  assert(t->singleton(), "must be a constant");
  ConNode* x = ConNode::make(t);
  ConNode* k = (ConNode*)hash_find_insert(x); // Value numbering
  if (k == nullptr) {
    set_type(x, t);             // Missed, provide type mapping
    GrowableArray<Node_Notes*>* nna = C->node_note_array();
    if (nna != nullptr) {
      Node_Notes* loc = C->locate_node_notes(nna, x->_idx, true);
      loc->clear(); // do not put debug info on constants
    }
  } else {
    x->destruct(this);          // Hit, destroy duplicate constant
    x = k;                      // use existing constant
  }
  return x;
}

//------------------------------intcon-----------------------------------------
// Fast integer constant.  Same as "transform(new ConINode(TypeInt::make(i)))"
ConINode* PhaseValues::intcon(jint i) {
  // Small integer?  Check cache! Check that cached node is not dead
  if (i >= _icon_min && i <= _icon_max) {
    ConINode* icon = _icons[i-_icon_min];
    if (icon != nullptr && icon->in(TypeFunc::Control) != nullptr)
      return icon;
  }
  ConINode* icon = (ConINode*) uncached_makecon(TypeInt::make(i));
  assert(icon->is_Con(), "");
  if (i >= _icon_min && i <= _icon_max)
    _icons[i-_icon_min] = icon;   // Cache small integers
  return icon;
}

//------------------------------longcon----------------------------------------
// Fast long constant.
ConLNode* PhaseValues::longcon(jlong l) {
  // Small integer?  Check cache! Check that cached node is not dead
  if (l >= _lcon_min && l <= _lcon_max) {
    ConLNode* lcon = _lcons[l-_lcon_min];
    if (lcon != nullptr && lcon->in(TypeFunc::Control) != nullptr)
      return lcon;
  }
  ConLNode* lcon = (ConLNode*) uncached_makecon(TypeLong::make(l));
  assert(lcon->is_Con(), "");
  if (l >= _lcon_min && l <= _lcon_max)
    _lcons[l-_lcon_min] = lcon;      // Cache small integers
  return lcon;
}
ConNode* PhaseValues::integercon(jlong l, BasicType bt) {
  if (bt == T_INT) {
    return intcon(checked_cast<jint>(l));
  }
  assert(bt == T_LONG, "not an integer");
  return longcon(l);
}


//------------------------------zerocon-----------------------------------------
// Fast zero or null constant. Same as "transform(ConNode::make(Type::get_zero_type(bt)))"
ConNode* PhaseValues::zerocon(BasicType bt) {
  assert((uint)bt <= _zcon_max, "domain check");
  ConNode* zcon = _zcons[bt];
  if (zcon != nullptr && zcon->in(TypeFunc::Control) != nullptr)
    return zcon;
  zcon = (ConNode*) uncached_makecon(Type::get_zero_type(bt));
  _zcons[bt] = zcon;
  return zcon;
}



//=============================================================================
Node* PhaseGVN::apply_ideal(Node* k, bool can_reshape) {
  Node* i = BarrierSet::barrier_set()->barrier_set_c2()->ideal_node(this, k, can_reshape);
  if (i == nullptr) {
    i = k->Ideal(this, can_reshape);
  }
  return i;
}

//------------------------------transform--------------------------------------
// Return a node which computes the same function as this node, but
// in a faster or cheaper fashion.
Node* PhaseGVN::transform(Node* n) {
  NOT_PRODUCT( set_transforms(); )

  // Apply the Ideal call in a loop until it no longer applies
  Node* k = n;
  Node* i = apply_ideal(k, /*can_reshape=*/false);
  NOT_PRODUCT(uint loop_count = 1;)
  while (i != nullptr) {
    assert(i->_idx >= k->_idx, "Idealize should return new nodes, use Identity to return old nodes" );
    k = i;
#ifdef ASSERT
    if (loop_count >= K + C->live_nodes()) {
      dump_infinite_loop_info(i, "PhaseGVN::transform");
    }
#endif
    i = apply_ideal(k, /*can_reshape=*/false);
    NOT_PRODUCT(loop_count++;)
  }
  NOT_PRODUCT(if (loop_count != 0) { set_progress(); })

  // If brand new node, make space in type array.
  ensure_type_or_null(k);

  // Since I just called 'Value' to compute the set of run-time values
  // for this Node, and 'Value' is non-local (and therefore expensive) I'll
  // cache Value.  Later requests for the local phase->type of this Node can
  // use the cached Value instead of suffering with 'bottom_type'.
  const Type* t = k->Value(this); // Get runtime Value set
  assert(t != nullptr, "value sanity");
  if (type_or_null(k) != t) {
#ifndef PRODUCT
    // Do not count initial visit to node as a transformation
    if (type_or_null(k) == nullptr) {
      inc_new_values();
      set_progress();
    }
#endif
    set_type(k, t);
    // If k is a TypeNode, capture any more-precise type permanently into Node
    k->raise_bottom_type(t);
  }

  if (t->singleton() && !k->is_Con()) {
    NOT_PRODUCT(set_progress();)
    return makecon(t);          // Turn into a constant
  }

  // Now check for Identities
  i = k->Identity(this);        // Look for a nearby replacement
  if (i != k) {                 // Found? Return replacement!
    NOT_PRODUCT(set_progress();)
    return i;
  }

  // Global Value Numbering
  i = hash_find_insert(k);      // Insert if new
  if (i && (i != k)) {
    // Return the pre-existing node
    NOT_PRODUCT(set_progress();)
    return i;
  }

  // Return Idealized original
  return k;
}

bool PhaseGVN::is_dominator_helper(Node *d, Node *n, bool linear_only) {
  if (d->is_top() || (d->is_Proj() && d->in(0)->is_top())) {
    return false;
  }
  if (n->is_top() || (n->is_Proj() && n->in(0)->is_top())) {
    return false;
  }
  assert(d->is_CFG() && n->is_CFG(), "must have CFG nodes");
  int i = 0;
  while (d != n) {
    n = IfNode::up_one_dom(n, linear_only);
    i++;
    if (n == nullptr || i >= 100) {
      return false;
    }
  }
  return true;
}

#ifdef ASSERT
//------------------------------dead_loop_check--------------------------------
// Check for a simple dead loop when a data node references itself directly
// or through an other data node excluding cons and phis.
void PhaseGVN::dead_loop_check( Node *n ) {
  // Phi may reference itself in a loop
  if (n != nullptr && !n->is_dead_loop_safe() && !n->is_CFG()) {
    // Do 2 levels check and only data inputs.
    bool no_dead_loop = true;
    uint cnt = n->req();
    for (uint i = 1; i < cnt && no_dead_loop; i++) {
      Node *in = n->in(i);
      if (in == n) {
        no_dead_loop = false;
      } else if (in != nullptr && !in->is_dead_loop_safe()) {
        uint icnt = in->req();
        for (uint j = 1; j < icnt && no_dead_loop; j++) {
          if (in->in(j) == n || in->in(j) == in)
            no_dead_loop = false;
        }
      }
    }
    if (!no_dead_loop) n->dump_bfs(100,0,"#");
    assert(no_dead_loop, "dead loop detected");
  }
}


/**
 * Dumps information that can help to debug the problem. A debug
 * build fails with an assert.
 */
void PhaseGVN::dump_infinite_loop_info(Node* n, const char* where) {
  n->dump(4);
  assert(false, "infinite loop in %s", where);
}
#endif

//=============================================================================
//------------------------------PhaseIterGVN-----------------------------------
// Initialize with previous PhaseIterGVN info; used by PhaseCCP
PhaseIterGVN::PhaseIterGVN(PhaseIterGVN* igvn) : _delay_transform(igvn->_delay_transform),
                                                 _worklist(*C->igvn_worklist())
{
  _iterGVN = true;
  assert(&_worklist == &igvn->_worklist, "sanity");
}

//------------------------------PhaseIterGVN-----------------------------------
// Initialize with previous PhaseGVN info from Parser
PhaseIterGVN::PhaseIterGVN(PhaseGVN* gvn) : _delay_transform(false),
                                            _worklist(*C->igvn_worklist())
{
  _iterGVN = true;
  uint max;

  // Dead nodes in the hash table inherited from GVN were not treated as
  // roots during def-use info creation; hence they represent an invisible
  // use.  Clear them out.
  max = _table.size();
  for( uint i = 0; i < max; ++i ) {
    Node *n = _table.at(i);
    if(n != nullptr && n != _table.sentinel() && n->outcnt() == 0) {
      if( n->is_top() ) continue;
      // If remove_useless_nodes() has run, we expect no such nodes left.
      assert(false, "remove_useless_nodes missed this node");
      hash_delete(n);
    }
  }

  // Any Phis or Regions on the worklist probably had uses that could not
  // make more progress because the uses were made while the Phis and Regions
  // were in half-built states.  Put all uses of Phis and Regions on worklist.
  max = _worklist.size();
  for( uint j = 0; j < max; j++ ) {
    Node *n = _worklist.at(j);
    uint uop = n->Opcode();
    if( uop == Op_Phi || uop == Op_Region ||
        n->is_Type() ||
        n->is_Mem() )
      add_users_to_worklist(n);
  }
}

void PhaseIterGVN::shuffle_worklist() {
  if (_worklist.size() < 2) return;
  for (uint i = _worklist.size() - 1; i >= 1; i--) {
    uint j = C->random() % (i + 1);
    swap(_worklist.adr()[i], _worklist.adr()[j]);
  }
}

#ifndef PRODUCT
void PhaseIterGVN::verify_step(Node* n) {
  if (is_verify_def_use()) {
    ResourceMark rm;
    VectorSet visited;
    Node_List worklist;

    _verify_window[_verify_counter % _verify_window_size] = n;
    ++_verify_counter;
    if (C->unique() < 1000 || 0 == _verify_counter % (C->unique() < 10000 ? 10 : 100)) {
      ++_verify_full_passes;
      worklist.push(C->root());
      Node::verify(-1, visited, worklist);
      return;
    }
    for (int i = 0; i < _verify_window_size; i++) {
      Node* n = _verify_window[i];
      if (n == nullptr) {
        continue;
      }
      if (n->in(0) == NodeSentinel) { // xform_idom
        _verify_window[i] = n->in(1);
        --i;
        continue;
      }
      // Typical fanout is 1-2, so this call visits about 6 nodes.
      if (!visited.test_set(n->_idx)) {
        worklist.push(n);
      }
    }
    Node::verify(4, visited, worklist);
  }
}

void PhaseIterGVN::trace_PhaseIterGVN(Node* n, Node* nn, const Type* oldtype) {
  const Type* newtype = type_or_null(n);
  if (nn != n || oldtype != newtype) {
    C->print_method(PHASE_AFTER_ITER_GVN_STEP, 5, n);
  }
  if (TraceIterativeGVN) {
    uint wlsize = _worklist.size();
    if (nn != n) {
      // print old node
      tty->print("< ");
      if (oldtype != newtype && oldtype != nullptr) {
        oldtype->dump();
      }
      do { tty->print("\t"); } while (tty->position() < 16);
      tty->print("<");
      n->dump();
    }
    if (oldtype != newtype || nn != n) {
      // print new node and/or new type
      if (oldtype == nullptr) {
        tty->print("* ");
      } else if (nn != n) {
        tty->print("> ");
      } else {
        tty->print("= ");
      }
      if (newtype == nullptr) {
        tty->print("null");
      } else {
        newtype->dump();
      }
      do { tty->print("\t"); } while (tty->position() < 16);
      nn->dump();
    }
    if (Verbose && wlsize < _worklist.size()) {
      tty->print("  Push {");
      while (wlsize != _worklist.size()) {
        Node* pushed = _worklist.at(wlsize++);
        tty->print(" %d", pushed->_idx);
      }
      tty->print_cr(" }");
    }
    if (nn != n) {
      // ignore n, it might be subsumed
      verify_step((Node*) nullptr);
    }
  }
}

void PhaseIterGVN::init_verifyPhaseIterGVN() {
  _verify_counter = 0;
  _verify_full_passes = 0;
  for (int i = 0; i < _verify_window_size; i++) {
    _verify_window[i] = nullptr;
  }
#ifdef ASSERT
  // Verify that all modified nodes are on _worklist
  Unique_Node_List* modified_list = C->modified_nodes();
  while (modified_list != nullptr && modified_list->size()) {
    Node* n = modified_list->pop();
    if (!n->is_Con() && !_worklist.member(n)) {
      n->dump();
      fatal("modified node is not on IGVN._worklist");
    }
  }
#endif
}

void PhaseIterGVN::verify_PhaseIterGVN() {
#ifdef ASSERT
  // Verify nodes with changed inputs.
  Unique_Node_List* modified_list = C->modified_nodes();
  while (modified_list != nullptr && modified_list->size()) {
    Node* n = modified_list->pop();
    if (!n->is_Con()) { // skip Con nodes
      n->dump();
      fatal("modified node was not processed by IGVN.transform_old()");
    }
  }
#endif

  C->verify_graph_edges();
  if (is_verify_def_use() && PrintOpto) {
    if (_verify_counter == _verify_full_passes) {
      tty->print_cr("VerifyIterativeGVN: %d transforms and verify passes",
                    (int) _verify_full_passes);
    } else {
      tty->print_cr("VerifyIterativeGVN: %d transforms, %d full verify passes",
                  (int) _verify_counter, (int) _verify_full_passes);
    }
  }

#ifdef ASSERT
  if (modified_list != nullptr) {
    while (modified_list->size() > 0) {
      Node* n = modified_list->pop();
      n->dump();
      assert(false, "VerifyIterativeGVN: new modified node was added");
    }
  }

  verify_optimize();
#endif
}
#endif /* PRODUCT */

#ifdef ASSERT
/**
 * Dumps information that can help to debug the problem. A debug
 * build fails with an assert.
 */
void PhaseIterGVN::dump_infinite_loop_info(Node* n, const char* where) {
  n->dump(4);
  _worklist.dump();
  assert(false, "infinite loop in %s", where);
}

/**
 * Prints out information about IGVN if the 'verbose' option is used.
 */
void PhaseIterGVN::trace_PhaseIterGVN_verbose(Node* n, int num_processed) {
  if (TraceIterativeGVN && Verbose) {
    tty->print("  Pop ");
    n->dump();
    if ((num_processed % 100) == 0) {
      _worklist.print_set();
    }
  }
}
#endif /* ASSERT */

void PhaseIterGVN::optimize() {
  DEBUG_ONLY(uint num_processed  = 0;)
  NOT_PRODUCT(init_verifyPhaseIterGVN();)
  NOT_PRODUCT(C->reset_igv_phase_iter(PHASE_AFTER_ITER_GVN_STEP);)
  C->print_method(PHASE_BEFORE_ITER_GVN, 3);
  if (StressIGVN) {
    shuffle_worklist();
  }

  uint loop_count = 0;
  // Pull from worklist and transform the node. If the node has changed,
  // update edge info and put uses on worklist.
  while(_worklist.size()) {
    if (C->check_node_count(NodeLimitFudgeFactor * 2, "Out of nodes")) {
      C->print_method(PHASE_AFTER_ITER_GVN, 3);
      return;
    }
    Node* n  = _worklist.pop();
    if (loop_count >= K * C->live_nodes()) {
      DEBUG_ONLY(dump_infinite_loop_info(n, "PhaseIterGVN::optimize");)
      C->record_method_not_compilable("infinite loop in PhaseIterGVN::optimize");
      C->print_method(PHASE_AFTER_ITER_GVN, 3);
      return;
    }
    DEBUG_ONLY(trace_PhaseIterGVN_verbose(n, num_processed++);)
    if (n->outcnt() != 0) {
      NOT_PRODUCT(const Type* oldtype = type_or_null(n));
      // Do the transformation
      Node* nn = transform_old(n);
      NOT_PRODUCT(trace_PhaseIterGVN(n, nn, oldtype);)
    } else if (!n->is_top()) {
      remove_dead_node(n);
    }
    loop_count++;
  }
  NOT_PRODUCT(verify_PhaseIterGVN();)
  C->print_method(PHASE_AFTER_ITER_GVN, 3);
}

#ifdef ASSERT
void PhaseIterGVN::verify_optimize() {
  if (is_verify_Value()) {
    ResourceMark rm;
    Unique_Node_List worklist;
    bool failure = false;
    // BFS all nodes, starting at root
    worklist.push(C->root());
    for (uint j = 0; j < worklist.size(); ++j) {
      Node* n = worklist.at(j);
      failure |= verify_node_value(n);
      // traverse all inputs and outputs
      for (uint i = 0; i < n->req(); i++) {
        if (n->in(i) != nullptr) {
          worklist.push(n->in(i));
        }
      }
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        worklist.push(n->fast_out(i));
      }
    }
    // If we get this assert, check why the reported nodes were not processed again in IGVN.
    // We should either make sure that these nodes are properly added back to the IGVN worklist
    // in PhaseIterGVN::add_users_to_worklist to update them again or add an exception
    // in the verification code above if that is not possible for some reason (like Load nodes).
    assert(!failure, "Missed optimization opportunity in PhaseIterGVN");
  }
}

// Check that type(n) == n->Value(), return true if we have a failure.
// We have a list of exceptions, see detailed comments in code.
// (1) Integer "widen" changes, but the range is the same.
// (2) LoadNode performs deep traversals. Load is not notified for changes far away.
// (3) CmpPNode performs deep traversals if it compares oopptr. CmpP is not notified for changes far away.
bool PhaseIterGVN::verify_node_value(Node* n) {
  // If we assert inside type(n), because the type is still a null, then maybe
  // the node never went through gvn.transform, which would be a bug.
  const Type* told = type(n);
  const Type* tnew = n->Value(this);
  if (told == tnew) {
    return false;
  }
  // Exception (1)
  // Integer "widen" changes, but range is the same.
  if (told->isa_integer(tnew->basic_type()) != nullptr) { // both either int or long
    const TypeInteger* t0 = told->is_integer(tnew->basic_type());
    const TypeInteger* t1 = tnew->is_integer(tnew->basic_type());
    if (t0->lo_as_long() == t1->lo_as_long() &&
        t0->hi_as_long() == t1->hi_as_long()) {
      return false; // ignore integer widen
    }
  }
  // Exception (2)
  // LoadNode performs deep traversals. Load is not notified for changes far away.
  if (n->is_Load() && !told->singleton()) {
    // MemNode::can_see_stored_value looks up through many memory nodes,
    // which means we would need to notify modifications from far up in
    // the inputs all the way down to the LoadNode. We don't do that.
    return false;
  }
  // Exception (3)
  // CmpPNode performs deep traversals if it compares oopptr. CmpP is not notified for changes far away.
  if (n->Opcode() == Op_CmpP && type(n->in(1))->isa_oopptr() && type(n->in(2))->isa_oopptr()) {
    // SubNode::Value
    // CmpPNode::sub
    // MemNode::detect_ptr_independence
    // MemNode::all_controls_dominate
    // We find all controls of a pointer load, and see if they dominate the control of
    // an allocation. If they all dominate, we know the allocation is after (independent)
    // of the pointer load, and we can say the pointers are different. For this we call
    // n->dominates(sub, nlist) to check if controls n of the pointer load dominate the
    // control sub of the allocation. The problems is that sometimes dominates answers
    // false conservatively, and later it can determine that it is indeed true. Loops with
    // Region heads can lead to giving up, whereas LoopNodes can be skipped easier, and
    // so the traversal becomes more powerful. This is difficult to remidy, we would have
    // to notify the CmpP of CFG updates. Luckily, we recompute CmpP::Value during CCP
    // after loop-opts, so that should take care of many of these cases.
    return false;
  }
  tty->cr();
  tty->print_cr("Missed Value optimization:");
  n->dump_bfs(1, 0, "");
  tty->print_cr("Current type:");
  told->dump_on(tty);
  tty->cr();
  tty->print_cr("Optimized type:");
  tnew->dump_on(tty);
  tty->cr();
  return true;
}
#endif

/**
 * Register a new node with the optimizer.  Update the types array, the def-use
 * info.  Put on worklist.
 */
Node* PhaseIterGVN::register_new_node_with_optimizer(Node* n, Node* orig) {
  set_type_bottom(n);
  _worklist.push(n);
  if (orig != nullptr)  C->copy_node_notes_to(n, orig);
  return n;
}

//------------------------------transform--------------------------------------
// Non-recursive: idealize Node 'n' with respect to its inputs and its value
Node *PhaseIterGVN::transform( Node *n ) {
  if (_delay_transform) {
    // Register the node but don't optimize for now
    register_new_node_with_optimizer(n);
    return n;
  }

  // If brand new node, make space in type array, and give it a type.
  ensure_type_or_null(n);
  if (type_or_null(n) == nullptr) {
    set_type_bottom(n);
  }

  return transform_old(n);
}

Node *PhaseIterGVN::transform_old(Node* n) {
  NOT_PRODUCT(set_transforms());
  // Remove 'n' from hash table in case it gets modified
  _table.hash_delete(n);
#ifdef ASSERT
  if (is_verify_def_use()) {
    assert(!_table.find_index(n->_idx), "found duplicate entry in table");
  }
#endif

  // Allow Bool -> Cmp idealisation in late inlining intrinsics that return a bool
  if (n->is_Cmp()) {
    add_users_to_worklist(n);
  }

  // Apply the Ideal call in a loop until it no longer applies
  Node* k = n;
  DEBUG_ONLY(dead_loop_check(k);)
  DEBUG_ONLY(bool is_new = (k->outcnt() == 0);)
  C->remove_modified_node(k);
  Node* i = apply_ideal(k, /*can_reshape=*/true);
  assert(i != k || is_new || i->outcnt() > 0, "don't return dead nodes");
#ifndef PRODUCT
  verify_step(k);
#endif

  DEBUG_ONLY(uint loop_count = 1;)
  while (i != nullptr) {
#ifdef ASSERT
    if (loop_count >= K + C->live_nodes()) {
      dump_infinite_loop_info(i, "PhaseIterGVN::transform_old");
    }
#endif
    assert((i->_idx >= k->_idx) || i->is_top(), "Idealize should return new nodes, use Identity to return old nodes");
    // Made a change; put users of original Node on worklist
    add_users_to_worklist(k);
    // Replacing root of transform tree?
    if (k != i) {
      // Make users of old Node now use new.
      subsume_node(k, i);
      k = i;
    }
    DEBUG_ONLY(dead_loop_check(k);)
    // Try idealizing again
    DEBUG_ONLY(is_new = (k->outcnt() == 0);)
    C->remove_modified_node(k);
    i = apply_ideal(k, /*can_reshape=*/true);
    assert(i != k || is_new || (i->outcnt() > 0), "don't return dead nodes");
#ifndef PRODUCT
    verify_step(k);
#endif
    DEBUG_ONLY(loop_count++;)
  }

  // If brand new node, make space in type array.
  ensure_type_or_null(k);

  // See what kind of values 'k' takes on at runtime
  const Type* t = k->Value(this);
  assert(t != nullptr, "value sanity");

  // Since I just called 'Value' to compute the set of run-time values
  // for this Node, and 'Value' is non-local (and therefore expensive) I'll
  // cache Value.  Later requests for the local phase->type of this Node can
  // use the cached Value instead of suffering with 'bottom_type'.
  if (type_or_null(k) != t) {
#ifndef PRODUCT
    inc_new_values();
    set_progress();
#endif
    set_type(k, t);
    // If k is a TypeNode, capture any more-precise type permanently into Node
    k->raise_bottom_type(t);
    // Move users of node to worklist
    add_users_to_worklist(k);
  }
  // If 'k' computes a constant, replace it with a constant
  if (t->singleton() && !k->is_Con()) {
    NOT_PRODUCT(set_progress();)
    Node* con = makecon(t);     // Make a constant
    add_users_to_worklist(k);
    subsume_node(k, con);       // Everybody using k now uses con
    return con;
  }

  // Now check for Identities
  i = k->Identity(this);      // Look for a nearby replacement
  if (i != k) {                // Found? Return replacement!
    NOT_PRODUCT(set_progress();)
    add_users_to_worklist(k);
    subsume_node(k, i);       // Everybody using k now uses i
    return i;
  }

  // Global Value Numbering
  i = hash_find_insert(k);      // Check for pre-existing node
  if (i && (i != k)) {
    // Return the pre-existing node if it isn't dead
    NOT_PRODUCT(set_progress();)
    add_users_to_worklist(k);
    subsume_node(k, i);       // Everybody using k now uses i
    return i;
  }

  // Return Idealized original
  return k;
}

//---------------------------------saturate------------------------------------
const Type* PhaseIterGVN::saturate(const Type* new_type, const Type* old_type,
                                   const Type* limit_type) const {
  return new_type->narrow(old_type);
}

//------------------------------remove_globally_dead_node----------------------
// Kill a globally dead Node.  All uses are also globally dead and are
// aggressively trimmed.
void PhaseIterGVN::remove_globally_dead_node( Node *dead ) {
  enum DeleteProgress {
    PROCESS_INPUTS,
    PROCESS_OUTPUTS
  };
  ResourceMark rm;
  Node_Stack stack(32);
  stack.push(dead, PROCESS_INPUTS);

  while (stack.is_nonempty()) {
    dead = stack.node();
    if (dead->Opcode() == Op_SafePoint) {
      dead->as_SafePoint()->disconnect_from_root(this);
    }
    uint progress_state = stack.index();
    assert(dead != C->root(), "killing root, eh?");
    assert(!dead->is_top(), "add check for top when pushing");
    NOT_PRODUCT( set_progress(); )
    if (progress_state == PROCESS_INPUTS) {
      // After following inputs, continue to outputs
      stack.set_index(PROCESS_OUTPUTS);
      if (!dead->is_Con()) { // Don't kill cons but uses
        bool recurse = false;
        // Remove from hash table
        _table.hash_delete( dead );
        // Smash all inputs to 'dead', isolating him completely
        for (uint i = 0; i < dead->req(); i++) {
          Node *in = dead->in(i);
          if (in != nullptr && in != C->top()) {  // Points to something?
            int nrep = dead->replace_edge(in, nullptr, this);  // Kill edges
            assert((nrep > 0), "sanity");
            if (in->outcnt() == 0) { // Made input go dead?
              stack.push(in, PROCESS_INPUTS); // Recursively remove
              recurse = true;
            } else if (in->outcnt() == 1 &&
                       in->has_special_unique_user()) {
              _worklist.push(in->unique_out());
            } else if (in->outcnt() <= 2 && dead->is_Phi()) {
              if (in->Opcode() == Op_Region) {
                _worklist.push(in);
              } else if (in->is_Store()) {
                DUIterator_Fast imax, i = in->fast_outs(imax);
                _worklist.push(in->fast_out(i));
                i++;
                if (in->outcnt() == 2) {
                  _worklist.push(in->fast_out(i));
                  i++;
                }
                assert(!(i < imax), "sanity");
              }
            } else {
              BarrierSet::barrier_set()->barrier_set_c2()->enqueue_useful_gc_barrier(this, in);
            }
            if (ReduceFieldZeroing && dead->is_Load() && i == MemNode::Memory &&
                in->is_Proj() && in->in(0) != nullptr && in->in(0)->is_Initialize()) {
              // A Load that directly follows an InitializeNode is
              // going away. The Stores that follow are candidates
              // again to be captured by the InitializeNode.
              for (DUIterator_Fast jmax, j = in->fast_outs(jmax); j < jmax; j++) {
                Node *n = in->fast_out(j);
                if (n->is_Store()) {
                  _worklist.push(n);
                }
              }
            }
          } // if (in != nullptr && in != C->top())
        } // for (uint i = 0; i < dead->req(); i++)
        if (recurse) {
          continue;
        }
      } // if (!dead->is_Con())
    } // if (progress_state == PROCESS_INPUTS)

    // Aggressively kill globally dead uses
    // (Rather than pushing all the outs at once, we push one at a time,
    // plus the parent to resume later, because of the indefinite number
    // of edge deletions per loop trip.)
    if (dead->outcnt() > 0) {
      // Recursively remove output edges
      stack.push(dead->raw_out(0), PROCESS_INPUTS);
    } else {
      // Finished disconnecting all input and output edges.
      stack.pop();
      // Remove dead node from iterative worklist
      _worklist.remove(dead);
      C->remove_useless_node(dead);
    }
  } // while (stack.is_nonempty())
}

//------------------------------subsume_node-----------------------------------
// Remove users from node 'old' and add them to node 'nn'.
void PhaseIterGVN::subsume_node( Node *old, Node *nn ) {
  if (old->Opcode() == Op_SafePoint) {
    old->as_SafePoint()->disconnect_from_root(this);
  }
  assert( old != hash_find(old), "should already been removed" );
  assert( old != C->top(), "cannot subsume top node");
  // Copy debug or profile information to the new version:
  C->copy_node_notes_to(nn, old);
  // Move users of node 'old' to node 'nn'
  for (DUIterator_Last imin, i = old->last_outs(imin); i >= imin; ) {
    Node* use = old->last_out(i);  // for each use...
    // use might need re-hashing (but it won't if it's a new node)
    rehash_node_delayed(use);
    // Update use-def info as well
    // We remove all occurrences of old within use->in,
    // so as to avoid rehashing any node more than once.
    // The hash table probe swamps any outer loop overhead.
    uint num_edges = 0;
    for (uint jmax = use->len(), j = 0; j < jmax; j++) {
      if (use->in(j) == old) {
        use->set_req(j, nn);
        ++num_edges;
      }
    }
    i -= num_edges;    // we deleted 1 or more copies of this edge
  }

  // Search for instance field data PhiNodes in the same region pointing to the old
  // memory PhiNode and update their instance memory ids to point to the new node.
  if (old->is_Phi() && old->as_Phi()->type()->has_memory() && old->in(0) != nullptr) {
    Node* region = old->in(0);
    for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
      PhiNode* phi = region->fast_out(i)->isa_Phi();
      if (phi != nullptr && phi->inst_mem_id() == (int)old->_idx) {
        phi->set_inst_mem_id((int)nn->_idx);
      }
    }
  }

  // Smash all inputs to 'old', isolating him completely
  Node *temp = new Node(1);
  temp->init_req(0,nn);     // Add a use to nn to prevent him from dying
  remove_dead_node( old );
  temp->del_req(0);         // Yank bogus edge
  if (nn != nullptr && nn->outcnt() == 0) {
    _worklist.push(nn);
  }
#ifndef PRODUCT
  if (is_verify_def_use()) {
    for ( int i = 0; i < _verify_window_size; i++ ) {
      if ( _verify_window[i] == old )
        _verify_window[i] = nn;
    }
  }
#endif
  temp->destruct(this);     // reuse the _idx of this little guy
}

//------------------------------add_users_to_worklist--------------------------
void PhaseIterGVN::add_users_to_worklist0(Node* n, Unique_Node_List& worklist) {
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    worklist.push(n->fast_out(i));  // Push on worklist
  }
}

// Return counted loop Phi if as a counted loop exit condition, cmp
// compares the induction variable with n
static PhiNode* countedloop_phi_from_cmp(CmpNode* cmp, Node* n) {
  for (DUIterator_Fast imax, i = cmp->fast_outs(imax); i < imax; i++) {
    Node* bol = cmp->fast_out(i);
    for (DUIterator_Fast i2max, i2 = bol->fast_outs(i2max); i2 < i2max; i2++) {
      Node* iff = bol->fast_out(i2);
      if (iff->is_BaseCountedLoopEnd()) {
        BaseCountedLoopEndNode* cle = iff->as_BaseCountedLoopEnd();
        if (cle->limit() == n) {
          PhiNode* phi = cle->phi();
          if (phi != nullptr) {
            return phi;
          }
        }
      }
    }
  }
  return nullptr;
}

void PhaseIterGVN::add_users_to_worklist(Node *n) {
  add_users_to_worklist0(n, _worklist);

  Unique_Node_List& worklist = _worklist;
  // Move users of node to worklist
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* use = n->fast_out(i); // Get use
    add_users_of_use_to_worklist(n, use, worklist);
  }
}

void PhaseIterGVN::add_users_of_use_to_worklist(Node* n, Node* use, Unique_Node_List& worklist) {
  if(use->is_Multi() ||      // Multi-definer?  Push projs on worklist
      use->is_Store() )       // Enable store/load same address
    add_users_to_worklist0(use, worklist);

  // If we changed the receiver type to a call, we need to revisit
  // the Catch following the call.  It's looking for a non-null
  // receiver to know when to enable the regular fall-through path
  // in addition to the NullPtrException path.
  if (use->is_CallDynamicJava() && n == use->in(TypeFunc::Parms)) {
    Node* p = use->as_CallDynamicJava()->proj_out_or_null(TypeFunc::Control);
    if (p != nullptr) {
      add_users_to_worklist0(p, worklist);
    }
  }

  uint use_op = use->Opcode();
  if(use->is_Cmp()) {       // Enable CMP/BOOL optimization
    add_users_to_worklist0(use, worklist); // Put Bool on worklist
    if (use->outcnt() > 0) {
      Node* bol = use->raw_out(0);
      if (bol->outcnt() > 0) {
        Node* iff = bol->raw_out(0);
        if (iff->outcnt() == 2) {
          // Look for the 'is_x2logic' pattern: "x ? : 0 : 1" and put the
          // phi merging either 0 or 1 onto the worklist
          Node* ifproj0 = iff->raw_out(0);
          Node* ifproj1 = iff->raw_out(1);
          if (ifproj0->outcnt() > 0 && ifproj1->outcnt() > 0) {
            Node* region0 = ifproj0->raw_out(0);
            Node* region1 = ifproj1->raw_out(0);
            if( region0 == region1 )
              add_users_to_worklist0(region0, worklist);
          }
        }
      }
    }
    if (use_op == Op_CmpI || use_op == Op_CmpL) {
      Node* phi = countedloop_phi_from_cmp(use->as_Cmp(), n);
      if (phi != nullptr) {
        // Input to the cmp of a loop exit check has changed, thus
        // the loop limit may have changed, which can then change the
        // range values of the trip-count Phi.
        worklist.push(phi);
      }
    }
    if (use_op == Op_CmpI) {
      Node* cmp = use;
      Node* in1 = cmp->in(1);
      Node* in2 = cmp->in(2);
      // Notify CmpI / If pattern from CastIINode::Value (left pattern).
      // Must also notify if in1 is modified and possibly turns into X (right pattern).
      //
      // in1  in2                   in1  in2
      //  |    |                     |    |
      //  +--- | --+                 |    |
      //  |    |   |                 |    |
      // CmpINode  |                CmpINode
      //    |      |                   |
      // BoolNode  |                BoolNode
      //    |      |        OR         |
      //  IfNode   |                 IfNode
      //    |      |                   |
      //  IfProj   |                 IfProj   X
      //    |      |                   |      |
      //   CastIINode                 CastIINode
      //
      if (in1 != in2) { // if they are equal, the CmpI can fold them away
        if (in1 == n) {
          // in1 modified -> could turn into X -> do traversal based on right pattern.
          for (DUIterator_Fast i2max, i2 = cmp->fast_outs(i2max); i2 < i2max; i2++) {
            Node* bol = cmp->fast_out(i2); // For each Bool
            if (bol->is_Bool()) {
              for (DUIterator_Fast i3max, i3 = bol->fast_outs(i3max); i3 < i3max; i3++) {
                Node* iff = bol->fast_out(i3); // For each If
                if (iff->is_If()) {
                  for (DUIterator_Fast i4max, i4 = iff->fast_outs(i4max); i4 < i4max; i4++) {
                    Node* if_proj = iff->fast_out(i4); // For each IfProj
                    assert(if_proj->is_IfProj(), "If only has IfTrue and IfFalse as outputs");
                    for (DUIterator_Fast i5max, i5 = if_proj->fast_outs(i5max); i5 < i5max; i5++) {
                      Node* castii = if_proj->fast_out(i5); // For each CastII
                      if (castii->is_CastII() &&
                          castii->as_CastII()->carry_dependency()) {
                        worklist.push(castii);
                      }
                    }
                  }
                }
              }
            }
          }
        } else {
          // Only in2 modified -> can assume X == in2 (left pattern).
          assert(n == in2, "only in2 modified");
          // Find all CastII with input in1.
          for (DUIterator_Fast jmax, j = in1->fast_outs(jmax); j < jmax; j++) {
            Node* castii = in1->fast_out(j);
            if (castii->is_CastII() && castii->as_CastII()->carry_dependency()) {
              // Find If.
              if (castii->in(0) != nullptr && castii->in(0)->in(0) != nullptr && castii->in(0)->in(0)->is_If()) {
                Node* ifnode = castii->in(0)->in(0);
                // Check that if connects to the cmp
                if (ifnode->in(1) != nullptr && ifnode->in(1)->is_Bool() && ifnode->in(1)->in(1) == cmp) {
                  worklist.push(castii);
                }
              }
            }
          }
        }
      }
    }
  }

  // If changed Cast input, notify down for Phi, Sub, and Xor - all do "uncast"
  // Patterns:
  // ConstraintCast+ -> Sub
  // ConstraintCast+ -> Phi
  // ConstraintCast+ -> Xor
  if (use->is_ConstraintCast()) {
    auto push_the_uses_to_worklist = [&](Node* n){
      if (n->is_Phi() || n->is_Sub() || n->Opcode() == Op_XorI || n->Opcode() == Op_XorL) {
        worklist.push(n);
      }
    };
    auto is_boundary = [](Node* n){ return !n->is_ConstraintCast(); };
    use->visit_uses(push_the_uses_to_worklist, is_boundary);
  }
  // If changed LShift inputs, check RShift users for useless sign-ext
  if( use_op == Op_LShiftI ) {
    for (DUIterator_Fast i2max, i2 = use->fast_outs(i2max); i2 < i2max; i2++) {
      Node* u = use->fast_out(i2);
      if (u->Opcode() == Op_RShiftI)
        worklist.push(u);
    }
  }
  // If changed LShift inputs, check And users for shift and mask (And) operation
  if (use_op == Op_LShiftI || use_op == Op_LShiftL) {
    for (DUIterator_Fast i2max, i2 = use->fast_outs(i2max); i2 < i2max; i2++) {
      Node* u = use->fast_out(i2);
      if (u->Opcode() == Op_AndI || u->Opcode() == Op_AndL) {
        worklist.push(u);
      }
    }
  }
  // If changed AddI/SubI inputs, check CmpU for range check optimization.
  if (use_op == Op_AddI || use_op == Op_SubI) {
    for (DUIterator_Fast i2max, i2 = use->fast_outs(i2max); i2 < i2max; i2++) {
      Node* u = use->fast_out(i2);
      if (u->is_Cmp() && (u->Opcode() == Op_CmpU)) {
        worklist.push(u);
      }
    }
  }
  // If changed AddP inputs, check Stores for loop invariant
  if( use_op == Op_AddP ) {
    for (DUIterator_Fast i2max, i2 = use->fast_outs(i2max); i2 < i2max; i2++) {
      Node* u = use->fast_out(i2);
      if (u->is_Mem())
        worklist.push(u);
    }
  }
  // If changed initialization activity, check dependent Stores
  if (use_op == Op_Allocate || use_op == Op_AllocateArray) {
    InitializeNode* init = use->as_Allocate()->initialization();
    if (init != nullptr) {
      Node* imem = init->proj_out_or_null(TypeFunc::Memory);
      if (imem != nullptr) add_users_to_worklist0(imem, worklist);
    }
  }
  // If the ValidLengthTest input changes then the fallthrough path out of the AllocateArray may have become dead.
  // CatchNode::Value() is responsible for killing that path. The CatchNode has to be explicitly enqueued for igvn
  // to guarantee the change is not missed.
  if (use_op == Op_AllocateArray && n == use->in(AllocateNode::ValidLengthTest)) {
    Node* p = use->as_AllocateArray()->proj_out_or_null(TypeFunc::Control);
    if (p != nullptr) {
      add_users_to_worklist0(p, worklist);
    }
  }

  if (use_op == Op_Initialize) {
    Node* imem = use->as_Initialize()->proj_out_or_null(TypeFunc::Memory);
    if (imem != nullptr) add_users_to_worklist0(imem, worklist);
  }
  // Loading the java mirror from a Klass requires two loads and the type
  // of the mirror load depends on the type of 'n'. See LoadNode::Value().
  //   LoadBarrier?(LoadP(LoadP(AddP(foo:Klass, #java_mirror))))
  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  bool has_load_barrier_nodes = bs->has_load_barrier_nodes();

  if (use_op == Op_LoadP && use->bottom_type()->isa_rawptr()) {
    for (DUIterator_Fast i2max, i2 = use->fast_outs(i2max); i2 < i2max; i2++) {
      Node* u = use->fast_out(i2);
      const Type* ut = u->bottom_type();
      if (u->Opcode() == Op_LoadP && ut->isa_instptr()) {
        if (has_load_barrier_nodes) {
          // Search for load barriers behind the load
          for (DUIterator_Fast i3max, i3 = u->fast_outs(i3max); i3 < i3max; i3++) {
            Node* b = u->fast_out(i3);
            if (bs->is_gc_barrier_node(b)) {
              worklist.push(b);
            }
          }
        }
        worklist.push(u);
      }
    }
  }
  if (use->Opcode() == Op_OpaqueZeroTripGuard) {
    assert(use->outcnt() <= 1, "OpaqueZeroTripGuard can't be shared");
    if (use->outcnt() == 1) {
      Node* cmp = use->unique_out();
      worklist.push(cmp);
    }
  }
}

/**
 * Remove the speculative part of all types that we know of
 */
void PhaseIterGVN::remove_speculative_types()  {
  assert(UseTypeSpeculation, "speculation is off");
  for (uint i = 0; i < _types.Size(); i++)  {
    const Type* t = _types.fast_lookup(i);
    if (t != nullptr) {
      _types.map(i, t->remove_speculative());
    }
  }
  _table.check_no_speculative_types();
}

// Check if the type of a divisor of a Div or Mod node includes zero.
bool PhaseIterGVN::no_dependent_zero_check(Node* n) const {
  switch (n->Opcode()) {
    case Op_DivI:
    case Op_ModI: {
      // Type of divisor includes 0?
      if (type(n->in(2)) == Type::TOP) {
        // 'n' is dead. Treat as if zero check is still there to avoid any further optimizations.
        return false;
      }
      const TypeInt* type_divisor = type(n->in(2))->is_int();
      return (type_divisor->_hi < 0 || type_divisor->_lo > 0);
    }
    case Op_DivL:
    case Op_ModL: {
      // Type of divisor includes 0?
      if (type(n->in(2)) == Type::TOP) {
        // 'n' is dead. Treat as if zero check is still there to avoid any further optimizations.
        return false;
      }
      const TypeLong* type_divisor = type(n->in(2))->is_long();
      return (type_divisor->_hi < 0 || type_divisor->_lo > 0);
    }
  }
  return true;
}

//=============================================================================
#ifndef PRODUCT
uint PhaseCCP::_total_invokes   = 0;
uint PhaseCCP::_total_constants = 0;
#endif
//------------------------------PhaseCCP---------------------------------------
// Conditional Constant Propagation, ala Wegman & Zadeck
PhaseCCP::PhaseCCP( PhaseIterGVN *igvn ) : PhaseIterGVN(igvn) {
  NOT_PRODUCT( clear_constants(); )
  assert( _worklist.size() == 0, "" );
  analyze();
}

#ifndef PRODUCT
//------------------------------~PhaseCCP--------------------------------------
PhaseCCP::~PhaseCCP() {
  inc_invokes();
  _total_constants += count_constants();
}
#endif


#ifdef ASSERT
void PhaseCCP::verify_type(Node* n, const Type* tnew, const Type* told) {
  if (tnew->meet(told) != tnew->remove_speculative()) {
    n->dump(1);
    tty->print("told = "); told->dump(); tty->cr();
    tty->print("tnew = "); tnew->dump(); tty->cr();
    fatal("Not monotonic");
  }
  assert(!told->isa_int() || !tnew->isa_int() || told->is_int()->_widen <= tnew->is_int()->_widen, "widen increases");
  assert(!told->isa_long() || !tnew->isa_long() || told->is_long()->_widen <= tnew->is_long()->_widen, "widen increases");
}
#endif //ASSERT

// In this analysis, all types are initially set to TOP. We iteratively call Value() on all nodes of the graph until
// we reach a fixed-point (i.e. no types change anymore). We start with a list that only contains the root node. Each time
// a new type is set, we push all uses of that node back to the worklist (in some cases, we also push grandchildren
// or nodes even further down back to the worklist because their type could change as a result of the current type
// change).
void PhaseCCP::analyze() {
  // Initialize all types to TOP, optimistic analysis
  for (uint i = 0; i < C->unique(); i++)  {
    _types.map(i, Type::TOP);
  }

  // CCP worklist is placed on a local arena, so that we can allow ResourceMarks on "Compile::current()->resource_arena()".
  // We also do not want to put the worklist on "Compile::current()->comp_arena()", as that one only gets de-allocated after
  // Compile is over. The local arena gets de-allocated at the end of its scope.
  ResourceArea local_arena(mtCompiler);
  Unique_Node_List worklist(&local_arena);
  DEBUG_ONLY(Unique_Node_List worklist_verify(&local_arena);)

  // Push root onto worklist
  worklist.push(C->root());

  assert(_root_and_safepoints.size() == 0, "must be empty (unused)");
  _root_and_safepoints.push(C->root());

  // Pull from worklist; compute new value; push changes out.
  // This loop is the meat of CCP.
  while (worklist.size() != 0) {
    Node* n = fetch_next_node(worklist);
    DEBUG_ONLY(worklist_verify.push(n);)
    if (n->is_SafePoint()) {
      // Make sure safepoints are processed by PhaseCCP::transform even if they are
      // not reachable from the bottom. Otherwise, infinite loops would be removed.
      _root_and_safepoints.push(n);
    }
    const Type* new_type = n->Value(this);
    if (new_type != type(n)) {
      DEBUG_ONLY(verify_type(n, new_type, type(n));)
      dump_type_and_node(n, new_type);
      set_type(n, new_type);
      push_child_nodes_to_worklist(worklist, n);
    }
  }
  DEBUG_ONLY(verify_analyze(worklist_verify);)
}

#ifdef ASSERT
// For every node n on verify list, check if type(n) == n->Value()
// We have a list of exceptions, see comments in verify_node_value.
void PhaseCCP::verify_analyze(Unique_Node_List& worklist_verify) {
  bool failure = false;
  while (worklist_verify.size()) {
    Node* n = worklist_verify.pop();
    failure |= verify_node_value(n);
  }
  // If we get this assert, check why the reported nodes were not processed again in CCP.
  // We should either make sure that these nodes are properly added back to the CCP worklist
  // in PhaseCCP::push_child_nodes_to_worklist() to update their type or add an exception
  // in the verification code above if that is not possible for some reason (like Load nodes).
  assert(!failure, "PhaseCCP not at fixpoint: analysis result may be unsound.");
}
#endif

// Fetch next node from worklist to be examined in this iteration.
Node* PhaseCCP::fetch_next_node(Unique_Node_List& worklist) {
  if (StressCCP) {
    return worklist.remove(C->random() % worklist.size());
  } else {
    return worklist.pop();
  }
}

#ifndef PRODUCT
void PhaseCCP::dump_type_and_node(const Node* n, const Type* t) {
  if (TracePhaseCCP) {
    t->dump();
    do {
      tty->print("\t");
    } while (tty->position() < 16);
    n->dump();
  }
}
#endif

// We need to propagate the type change of 'n' to all its uses. Depending on the kind of node, additional nodes
// (grandchildren or even further down) need to be revisited as their types could also be improved as a result
// of the new type of 'n'. Push these nodes to the worklist.
void PhaseCCP::push_child_nodes_to_worklist(Unique_Node_List& worklist, Node* n) const {
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* use = n->fast_out(i);
    push_if_not_bottom_type(worklist, use);
    push_more_uses(worklist, n, use);
  }
}

void PhaseCCP::push_if_not_bottom_type(Unique_Node_List& worklist, Node* n) const {
  if (n->bottom_type() != type(n)) {
    worklist.push(n);
  }
}

// For some nodes, we need to propagate the type change to grandchildren or even further down.
// Add them back to the worklist.
void PhaseCCP::push_more_uses(Unique_Node_List& worklist, Node* parent, const Node* use) const {
  push_phis(worklist, use);
  push_catch(worklist, use);
  push_cmpu(worklist, use);
  push_counted_loop_phi(worklist, parent, use);
  push_loadp(worklist, use);
  push_and(worklist, parent, use);
  push_cast_ii(worklist, parent, use);
  push_opaque_zero_trip_guard(worklist, use);
}


// We must recheck Phis too if use is a Region.
void PhaseCCP::push_phis(Unique_Node_List& worklist, const Node* use) const {
  if (use->is_Region()) {
    for (DUIterator_Fast imax, i = use->fast_outs(imax); i < imax; i++) {
      push_if_not_bottom_type(worklist, use->fast_out(i));
    }
  }
}

// If we changed the receiver type to a call, we need to revisit the Catch node following the call. It's looking for a
// non-null receiver to know when to enable the regular fall-through path in addition to the NullPtrException path.
// Same is true if the type of a ValidLengthTest input to an AllocateArrayNode changes.
void PhaseCCP::push_catch(Unique_Node_List& worklist, const Node* use) {
  if (use->is_Call()) {
    for (DUIterator_Fast imax, i = use->fast_outs(imax); i < imax; i++) {
      Node* proj = use->fast_out(i);
      if (proj->is_Proj() && proj->as_Proj()->_con == TypeFunc::Control) {
        Node* catch_node = proj->find_out_with(Op_Catch);
        if (catch_node != nullptr) {
          worklist.push(catch_node);
        }
      }
    }
  }
}

// CmpU nodes can get their type information from two nodes up in the graph (instead of from the nodes immediately
// above). Make sure they are added to the worklist if nodes they depend on are updated since they could be missed
// and get wrong types otherwise.
void PhaseCCP::push_cmpu(Unique_Node_List& worklist, const Node* use) const {
  uint use_op = use->Opcode();
  if (use_op == Op_AddI || use_op == Op_SubI) {
    for (DUIterator_Fast imax, i = use->fast_outs(imax); i < imax; i++) {
      Node* cmpu = use->fast_out(i);
      if (cmpu->Opcode() == Op_CmpU) {
        // Got a CmpU which might need the new type information from node n.
        push_if_not_bottom_type(worklist, cmpu);
      }
    }
  }
}

// If n is used in a counted loop exit condition, then the type of the counted loop's Phi depends on the type of 'n'.
// Seem PhiNode::Value().
void PhaseCCP::push_counted_loop_phi(Unique_Node_List& worklist, Node* parent, const Node* use) {
  uint use_op = use->Opcode();
  if (use_op == Op_CmpI || use_op == Op_CmpL) {
    PhiNode* phi = countedloop_phi_from_cmp(use->as_Cmp(), parent);
    if (phi != nullptr) {
      worklist.push(phi);
    }
  }
}

// Loading the java mirror from a Klass requires two loads and the type of the mirror load depends on the type of 'n'.
// See LoadNode::Value().
void PhaseCCP::push_loadp(Unique_Node_List& worklist, const Node* use) const {
  BarrierSetC2* barrier_set = BarrierSet::barrier_set()->barrier_set_c2();
  bool has_load_barrier_nodes = barrier_set->has_load_barrier_nodes();

  if (use->Opcode() == Op_LoadP && use->bottom_type()->isa_rawptr()) {
    for (DUIterator_Fast imax, i = use->fast_outs(imax); i < imax; i++) {
      Node* loadp = use->fast_out(i);
      const Type* ut = loadp->bottom_type();
      if (loadp->Opcode() == Op_LoadP && ut->isa_instptr() && ut != type(loadp)) {
        if (has_load_barrier_nodes) {
          // Search for load barriers behind the load
          push_load_barrier(worklist, barrier_set, loadp);
        }
        worklist.push(loadp);
      }
    }
  }
}

void PhaseCCP::push_load_barrier(Unique_Node_List& worklist, const BarrierSetC2* barrier_set, const Node* use) {
  for (DUIterator_Fast imax, i = use->fast_outs(imax); i < imax; i++) {
    Node* barrier_node = use->fast_out(i);
    if (barrier_set->is_gc_barrier_node(barrier_node)) {
      worklist.push(barrier_node);
    }
  }
}

// AndI/L::Value() optimizes patterns similar to (v << 2) & 3 to zero if they are bitwise disjoint.
// Add the AndI/L nodes back to the worklist to re-apply Value() in case the shift value changed.
// Pattern: parent -> LShift (use) -> (ConstraintCast | ConvI2L)* -> And
void PhaseCCP::push_and(Unique_Node_List& worklist, const Node* parent, const Node* use) const {
  uint use_op = use->Opcode();
  if ((use_op == Op_LShiftI || use_op == Op_LShiftL)
      && use->in(2) == parent) { // is shift value (right-hand side of LShift)
    auto push_and_uses_to_worklist = [&](Node* n){
      uint opc = n->Opcode();
      if (opc == Op_AndI || opc == Op_AndL) {
        push_if_not_bottom_type(worklist, n);
      }
    };
    auto is_boundary = [](Node* n) {
      return !(n->is_ConstraintCast() || n->Opcode() == Op_ConvI2L);
    };
    use->visit_uses(push_and_uses_to_worklist, is_boundary);
  }
}

// CastII::Value() optimizes CmpI/If patterns if the right input of the CmpI has a constant type. If the CastII input is
// the same node as the left input into the CmpI node, the type of the CastII node can be improved accordingly. Add the
// CastII node back to the worklist to re-apply Value() to either not miss this optimization or to undo it because it
// cannot be applied anymore. We could have optimized the type of the CastII before but now the type of the right input
// of the CmpI (i.e. 'parent') is no longer constant. The type of the CastII must be widened in this case.
void PhaseCCP::push_cast_ii(Unique_Node_List& worklist, const Node* parent, const Node* use) const {
  if (use->Opcode() == Op_CmpI && use->in(2) == parent) {
    Node* other_cmp_input = use->in(1);
    for (DUIterator_Fast imax, i = other_cmp_input->fast_outs(imax); i < imax; i++) {
      Node* cast_ii = other_cmp_input->fast_out(i);
      if (cast_ii->is_CastII()) {
        push_if_not_bottom_type(worklist, cast_ii);
      }
    }
  }
}

void PhaseCCP::push_opaque_zero_trip_guard(Unique_Node_List& worklist, const Node* use) const {
  if (use->Opcode() == Op_OpaqueZeroTripGuard) {
    push_if_not_bottom_type(worklist, use->unique_out());
  }
}

//------------------------------do_transform-----------------------------------
// Top level driver for the recursive transformer
void PhaseCCP::do_transform() {
  // Correct leaves of new-space Nodes; they point to old-space.
  C->set_root( transform(C->root())->as_Root() );
  assert( C->top(),  "missing TOP node" );
  assert( C->root(), "missing root" );
}

//------------------------------transform--------------------------------------
// Given a Node in old-space, clone him into new-space.
// Convert any of his old-space children into new-space children.
Node *PhaseCCP::transform( Node *n ) {
  assert(n->is_Root(), "traversal must start at root");
  assert(_root_and_safepoints.member(n), "root (n) must be in list");

  ResourceMark rm;
  // Map: old node idx -> node after CCP (or nullptr if not yet transformed or useless).
  Node_List node_map;
  // Pre-allocate to avoid frequent realloc
  GrowableArray <Node *> transform_stack(C->live_nodes() >> 1);
  // track all visited nodes, so that we can remove the complement
  Unique_Node_List useful;

  // Initialize the traversal.
  // This CCP pass may prove that no exit test for a loop ever succeeds (i.e. the loop is infinite). In that case,
  // the logic below doesn't follow any path from Root to the loop body: there's at least one such path but it's proven
  // never taken (its type is TOP). As a consequence the node on the exit path that's input to Root (let's call it n) is
  // replaced by the top node and the inputs of that node n are not enqueued for further processing. If CCP only works
  // through the graph from Root, this causes the loop body to never be processed here even when it's not dead (that
  // is reachable from Root following its uses). To prevent that issue, transform() starts walking the graph from Root
  // and all safepoints.
  for (uint i = 0; i < _root_and_safepoints.size(); ++i) {
    Node* nn = _root_and_safepoints.at(i);
    Node* new_node = node_map[nn->_idx];
    assert(new_node == nullptr, "");
    new_node = transform_once(nn);  // Check for constant
    node_map.map(nn->_idx, new_node); // Flag as having been cloned
    transform_stack.push(new_node); // Process children of cloned node
    useful.push(new_node);
  }

  while (transform_stack.is_nonempty()) {
    Node* clone = transform_stack.pop();
    uint cnt = clone->req();
    for( uint i = 0; i < cnt; i++ ) {          // For all inputs do
      Node *input = clone->in(i);
      if( input != nullptr ) {                 // Ignore nulls
        Node *new_input = node_map[input->_idx]; // Check for cloned input node
        if( new_input == nullptr ) {
          new_input = transform_once(input);   // Check for constant
          node_map.map( input->_idx, new_input );// Flag as having been cloned
          transform_stack.push(new_input);     // Process children of cloned node
          useful.push(new_input);
        }
        assert( new_input == clone->in(i), "insanity check");
      }
    }
  }

  // The above transformation might lead to subgraphs becoming unreachable from the
  // bottom while still being reachable from the top. As a result, nodes in that
  // subgraph are not transformed and their bottom types are not updated, leading to
  // an inconsistency between bottom_type() and type(). In rare cases, LoadNodes in
  // such a subgraph, might be re-enqueued for IGVN indefinitely by MemNode::Ideal_common
  // because their address type is inconsistent. Therefore, we aggressively remove
  // all useless nodes here even before PhaseIdealLoop::build_loop_late gets a chance
  // to remove them anyway.
  if (C->cached_top_node()) {
    useful.push(C->cached_top_node());
  }
  C->update_dead_node_list(useful);
  remove_useless_nodes(useful.member_set());
  _worklist.remove_useless_nodes(useful.member_set());
  C->disconnect_useless_nodes(useful, _worklist);

  Node* new_root = node_map[n->_idx];
  assert(new_root->is_Root(), "transformed root node must be a root node");
  return new_root;
}

//------------------------------transform_once---------------------------------
// For PhaseCCP, transformation is IDENTITY unless Node computed a constant.
Node *PhaseCCP::transform_once( Node *n ) {
  const Type *t = type(n);
  // Constant?  Use constant Node instead
  if( t->singleton() ) {
    Node *nn = n;               // Default is to return the original constant
    if( t == Type::TOP ) {
      // cache my top node on the Compile instance
      if( C->cached_top_node() == nullptr || C->cached_top_node()->in(0) == nullptr ) {
        C->set_cached_top_node(ConNode::make(Type::TOP));
        set_type(C->top(), Type::TOP);
      }
      nn = C->top();
    }
    if( !n->is_Con() ) {
      if( t != Type::TOP ) {
        nn = makecon(t);        // ConNode::make(t);
        NOT_PRODUCT( inc_constants(); )
      } else if( n->is_Region() ) { // Unreachable region
        // Note: nn == C->top()
        n->set_req(0, nullptr);     // Cut selfreference
        bool progress = true;
        uint max = n->outcnt();
        DUIterator i;
        while (progress) {
          progress = false;
          // Eagerly remove dead phis to avoid phis copies creation.
          for (i = n->outs(); n->has_out(i); i++) {
            Node* m = n->out(i);
            if (m->is_Phi()) {
              assert(type(m) == Type::TOP, "Unreachable region should not have live phis.");
              replace_node(m, nn);
              if (max != n->outcnt()) {
                progress = true;
                i = n->refresh_out_pos(i);
                max = n->outcnt();
              }
            }
          }
        }
      }
      replace_node(n,nn);       // Update DefUse edges for new constant
    }
    return nn;
  }

  // If x is a TypeNode, capture any more-precise type permanently into Node
  if (t != n->bottom_type()) {
    hash_delete(n);             // changing bottom type may force a rehash
    n->raise_bottom_type(t);
    _worklist.push(n);          // n re-enters the hash table via the worklist
  }

  // TEMPORARY fix to ensure that 2nd GVN pass eliminates null checks
  switch( n->Opcode() ) {
  case Op_CallStaticJava:  // Give post-parse call devirtualization a chance
  case Op_CallDynamicJava:
  case Op_FastLock:        // Revisit FastLocks for lock coarsening
  case Op_If:
  case Op_CountedLoopEnd:
  case Op_Region:
  case Op_Loop:
  case Op_CountedLoop:
  case Op_Conv2B:
  case Op_Opaque1:
    _worklist.push(n);
    break;
  default:
    break;
  }

  return  n;
}

//---------------------------------saturate------------------------------------
const Type* PhaseCCP::saturate(const Type* new_type, const Type* old_type,
                               const Type* limit_type) const {
  const Type* wide_type = new_type->widen(old_type, limit_type);
  if (wide_type != new_type) {          // did we widen?
    // If so, we may have widened beyond the limit type.  Clip it back down.
    new_type = wide_type->filter(limit_type);
  }
  return new_type;
}

//------------------------------print_statistics-------------------------------
#ifndef PRODUCT
void PhaseCCP::print_statistics() {
  tty->print_cr("CCP: %d  constants found: %d", _total_invokes, _total_constants);
}
#endif


//=============================================================================
#ifndef PRODUCT
uint PhasePeephole::_total_peepholes = 0;
#endif
//------------------------------PhasePeephole----------------------------------
// Conditional Constant Propagation, ala Wegman & Zadeck
PhasePeephole::PhasePeephole( PhaseRegAlloc *regalloc, PhaseCFG &cfg )
  : PhaseTransform(Peephole), _regalloc(regalloc), _cfg(cfg) {
  NOT_PRODUCT( clear_peepholes(); )
}

#ifndef PRODUCT
//------------------------------~PhasePeephole---------------------------------
PhasePeephole::~PhasePeephole() {
  _total_peepholes += count_peepholes();
}
#endif

//------------------------------transform--------------------------------------
Node *PhasePeephole::transform( Node *n ) {
  ShouldNotCallThis();
  return nullptr;
}

//------------------------------do_transform-----------------------------------
void PhasePeephole::do_transform() {
  bool method_name_not_printed = true;

  // Examine each basic block
  for (uint block_number = 1; block_number < _cfg.number_of_blocks(); ++block_number) {
    Block* block = _cfg.get_block(block_number);
    bool block_not_printed = true;

    for (bool progress = true; progress;) {
      progress = false;
      // block->end_idx() not valid after PhaseRegAlloc
      uint end_index = block->number_of_nodes();
      for( uint instruction_index = end_index - 1; instruction_index > 0; --instruction_index ) {
        Node     *n = block->get_node(instruction_index);
        if( n->is_Mach() ) {
          MachNode *m = n->as_Mach();
          // check for peephole opportunities
          int result = m->peephole(block, instruction_index, &_cfg, _regalloc);
          if( result != -1 ) {
#ifndef PRODUCT
            if( PrintOptoPeephole ) {
              // Print method, first time only
              if( C->method() && method_name_not_printed ) {
                C->method()->print_short_name(); tty->cr();
                method_name_not_printed = false;
              }
              // Print this block
              if( Verbose && block_not_printed) {
                tty->print_cr("in block");
                block->dump();
                block_not_printed = false;
              }
              // Print the peephole number
              tty->print_cr("peephole number: %d", result);
            }
            inc_peepholes();
#endif
            // Set progress, start again
            progress = true;
            break;
          }
        }
      }
    }
  }
}

//------------------------------print_statistics-------------------------------
#ifndef PRODUCT
void PhasePeephole::print_statistics() {
  tty->print_cr("Peephole: peephole rules applied: %d",  _total_peepholes);
}
#endif


//=============================================================================
//------------------------------set_req_X--------------------------------------
void Node::set_req_X( uint i, Node *n, PhaseIterGVN *igvn ) {
  assert( is_not_dead(n), "can not use dead node");
#ifdef ASSERT
  if (igvn->hash_find(this) == this) {
    tty->print_cr("Need to remove from hash before changing edges");
    this->dump(1);
    tty->print_cr("Set at i = %d", i);
    n->dump();
    assert(false, "Need to remove from hash before changing edges");
  }
#endif
  Node *old = in(i);
  set_req(i, n);

  // old goes dead?
  if( old ) {
    switch (old->outcnt()) {
    case 0:
      // Put into the worklist to kill later. We do not kill it now because the
      // recursive kill will delete the current node (this) if dead-loop exists
      if (!old->is_top())
        igvn->_worklist.push( old );
      break;
    case 1:
      if( old->is_Store() || old->has_special_unique_user() )
        igvn->add_users_to_worklist( old );
      break;
    case 2:
      if( old->is_Store() )
        igvn->add_users_to_worklist( old );
      if( old->Opcode() == Op_Region )
        igvn->_worklist.push(old);
      break;
    case 3:
      if( old->Opcode() == Op_Region ) {
        igvn->_worklist.push(old);
        igvn->add_users_to_worklist( old );
      }
      break;
    default:
      break;
    }

    BarrierSet::barrier_set()->barrier_set_c2()->enqueue_useful_gc_barrier(igvn, old);
  }
}

void Node::set_req_X(uint i, Node *n, PhaseGVN *gvn) {
  PhaseIterGVN* igvn = gvn->is_IterGVN();
  if (igvn == nullptr) {
    set_req(i, n);
    return;
  }
  set_req_X(i, n, igvn);
}

//-------------------------------replace_by-----------------------------------
// Using def-use info, replace one node for another.  Follow the def-use info
// to all users of the OLD node.  Then make all uses point to the NEW node.
void Node::replace_by(Node *new_node) {
  assert(!is_top(), "top node has no DU info");
  for (DUIterator_Last imin, i = last_outs(imin); i >= imin; ) {
    Node* use = last_out(i);
    uint uses_found = 0;
    for (uint j = 0; j < use->len(); j++) {
      if (use->in(j) == this) {
        if (j < use->req())
              use->set_req(j, new_node);
        else  use->set_prec(j, new_node);
        uses_found++;
      }
    }
    i -= uses_found;    // we deleted 1 or more copies of this edge
  }
}

//=============================================================================
//-----------------------------------------------------------------------------
void Type_Array::grow( uint i ) {
  if( !_max ) {
    _max = 1;
    _types = (const Type**)_a->Amalloc( _max * sizeof(Type*) );
    _types[0] = nullptr;
  }
  uint old = _max;
  _max = next_power_of_2(i);
  _types = (const Type**)_a->Arealloc( _types, old*sizeof(Type*),_max*sizeof(Type*));
  memset( &_types[old], 0, (_max-old)*sizeof(Type*) );
}

//------------------------------dump-------------------------------------------
#ifndef PRODUCT
void Type_Array::dump() const {
  uint max = Size();
  for( uint i = 0; i < max; i++ ) {
    if( _types[i] != nullptr ) {
      tty->print("  %d\t== ", i); _types[i]->dump(); tty->cr();
    }
  }
}
#endif
