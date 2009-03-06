/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef _DICT_
#define _DICT_
// Dictionaries - An Abstract Data Type
//INTERFACE
class ostream;
class Dict;

// These dictionaries define a key-value mapping.  They can be inserted to,
// searched or deleted from.  They grow and shrink as needed.  The key is a
// pointer to something (or anything which can be stored in a pointer).  A
// key comparison routine determines if two keys are equal or not.  A hash
// function can be provided; if it's not provided the key itself is used
// instead.  A nice string hash function is included.
typedef int32 (*CmpKey)(const void *key1, const void *key2);
typedef int  (*Hash)(const void *key);
typedef void (*FuncDict)(const void *key, const void *val, Dict *d);

class Dict : public ResourceObj { // Dictionary structure
 private:
  class Arena *_arena;          // Where to draw storage from
  class bucket *_bin;           // Hash table is array of buckets
  uint _size;                   // Size (# of slots) in hash table
  uint32 _cnt;                  // Number of key-value pairs in hash table
  const Hash _hash;             // Hashing function
  const CmpKey _cmp;            // Key comparison function
  void doubhash( void );        // Double hash table size

 public:
  friend class DictI;            // Friendly iterator function

  // cmp is a key comparision routine.  hash is a routine to hash a key.
  Dict( CmpKey cmp, Hash hash );
  Dict( CmpKey cmp, Hash hash, Arena *arena, int size=16 );
  ~Dict();

  Dict( const Dict & );         // Deep-copy guts
  Dict &operator =( const Dict & );

  // Zap to empty; ready for re-use
  void Clear();

  // Return # of key-value pairs in dict
  uint32 Size(void) const { return _cnt; }

  // Insert inserts the given key-value pair into the dictionary.  The prior
  // value of the key is returned; NULL if the key was not previously defined.
  void *Insert(void *key, void *val, bool replace = true); // A new key-value
  void *Delete(void *key);        // Delete & return old

  // Find finds the value of a given key; or NULL if not found.
  // The dictionary is NOT changed.
  void *operator [](const void *key) const;  // Do a lookup

  // == compares two dictionaries; they must have the same keys (their keys
  // must match using CmpKey) and they must have the same values (pointer
  // comparison).  If so 1 is returned, if not 0 is returned.
  int32 operator ==(const Dict &d) const;   // Compare dictionaries for equal

  // Print out the dictionary contents as key-value pairs
  void print();
};

// Hashing functions
int hashstr(const void *s);        // Nice string hash
// Slimey cheap hash function; no guaranteed performance.  Better than the
// default for pointers, especially on MS-DOS machines.
int hashptr(const void *key);
// Slimey cheap hash function; no guaranteed performance.
int hashkey(const void *key);

// Key comparators
int32 cmpstr(const void *k1, const void *k2);
// Slimey cheap key comparator.
int32 cmpkey(const void *key1, const void *key2);

//------------------------------Iteration--------------------------------------
// The class of dictionary iterators.  Fails in the presences of modifications
// to the dictionary during iteration (including searches).
// Usage:  for( DictI i(dict); i.test(); ++i ) { body = i.key; body = i.value;}
class DictI {
 private:
  const Dict *_d;               // Dictionary being iterated over
  uint _i;                      // Counter over the bins
  uint _j;                      // Counter inside each bin
 public:
  const void *_key, *_value;    // Easy access to the key-value pair
  DictI( const Dict *d ) {reset(d);}; // Create a new iterator
  void reset( const Dict *dict );     // Reset existing iterator
  void operator ++(void);             // Increment iterator
  int test(void) { return _i<_d->_size;} // Test for end of iteration
};

#endif // _DICT_
