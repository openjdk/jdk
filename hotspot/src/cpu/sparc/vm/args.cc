/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>

static const int R_O0_num = 1000;
static const int R_I0_num = 2000;
static const int R_F0_num = 3000;
static const int R_F1_num = R_F0_num + 1;
static const int R_F2_num = R_F0_num + 2;
static const int STACK_num= 4000;

static bool LP64 = false;
static bool LONGS_IN_ONE_ENTRY = false;

static const int Op_RegI = 'I';
static const int Op_RegP = 'P';
static const int Op_RegF = 'F';
static const int Op_RegD = 'D';
static const int Op_RegL = 'L';
static const int SPARC_ARGS_IN_REGS_NUM=6;

static void print_reg( int reg ) {
  if( reg == 0 )
    printf("__");               // halve's
  else if( reg >= STACK_num && reg < STACK_num+100 )
    printf("S%d_",reg - STACK_num);
  else if( reg >= R_F0_num && reg < R_F0_num+100 )
    printf("F%d_",reg - R_F0_num);
  else if( reg >= R_O0_num && reg < R_O0_num+100 ) {
    if( LONGS_IN_ONE_ENTRY ) {
      reg -= R_O0_num;
      printf("O%d",reg>>1);
      printf(reg&1 ? "H" : "L");
    } else
      printf("O%d_",reg - R_O0_num);
  } else
    printf("Wretched: %d\n", reg);
}

static void print_convention( int *sig, const char *s, int length ) {
  // Print it out
  for( int i = 0; i < length; i++) {
    if( sig[i] == 0 ) continue; // do not print 'halves'
    print_reg( sig[i] & 0xFFFF );
    int reg = sig[i] >> 16;
    if( reg ) {
      printf(":");
      print_reg( reg );
    } else {
      printf("    ");
    }
    printf("  ");
  }
  printf("\n");
}

static int INT_SCALE( int x ) {
  return LONGS_IN_ONE_ENTRY ? (x<<1) : x;
}

static void java_convention( int *sig, const char *s, int length ) {
  if( LP64 && !LONGS_IN_ONE_ENTRY ) {
    printf("LP64 and 2-reg longs not supported\n");
    return;
  }
  for( int i = 0; i < length; i++ )
    sig[i] = s[i];              // Reset sig array
  bool is_outgoing = true;

  int int_base = (is_outgoing ? R_O0_num : R_I0_num);

  // Convention is to pack the first 6 int/oop args into the first 6
  // registers (I0-I5), extras spill to the stack.  Then pack the first
  // 32 float args into F0-F31, extras spill to the stack.  Then pad
  // all register sets to align.  Then put longs and doubles into the
  // same registers as they fit, else spill to the stack.
  int int_reg_max = SPARC_ARGS_IN_REGS_NUM;
  int flt_reg_max = 32;
  
  // Count int/oop and float args.  See how many stack slots we'll need
  // and where the longs & doubles will go.
  int int_reg_cnt   = 0;
  int flt_reg_cnt   = 0;
  int stk_reg_pairs = 0;
  for( int i = 0; i < length; i++) {
    switch( sig[i] ) {
    case Op_RegL:               // Longs-in-1-reg compete with int args
      if( LONGS_IN_ONE_ENTRY ) { 
        if( int_reg_cnt < int_reg_max ) int_reg_cnt++;
      }
      break;
    case Op_RegP:
      if( int_reg_cnt < int_reg_max ) int_reg_cnt++;
      else if( !LP64 )                stk_reg_pairs++;
      break;
    case Op_RegI:
      if( int_reg_cnt < int_reg_max ) int_reg_cnt++;
      else                            stk_reg_pairs++;
      break;
    case Op_RegF:
      if( flt_reg_cnt < flt_reg_max ) flt_reg_cnt++;
      else                            stk_reg_pairs++;
      break;
    }
  }

  // This is where the longs/doubles start on the stack.
  stk_reg_pairs = (stk_reg_pairs+1) & ~1; // Round
  
  int int_reg_pairs = (int_reg_cnt+1) & ~1; // 32-bit 2-reg longs only
  int flt_reg_pairs = (flt_reg_cnt+1) & ~1;

  int stk_reg = 0;
  int int_reg = 0;
  int flt_reg = 0;
  
  // Now do the signature layout
  for( int i = 0; i < length; i++) {
    int tmp = sig[i];
    if( tmp == Op_RegP )
      tmp = LP64 ? Op_RegL : Op_RegI;   // Treat ptrs and ints or long accordingly
    switch( tmp ) {
    case Op_RegI:
//  case Op_RegP: 
      if( int_reg < int_reg_max) tmp = INT_SCALE(int_reg++) + int_base;
      else                       tmp = STACK_num + stk_reg++;
      sig[i] = tmp;
      break;

    case Op_RegL: 
      if( sig[i] != Op_RegP && sig[i+1] != 'h' ) { printf("expecting (h)alf, found %c\n", sig[i+1]); return; }
//  case Op_RegP: 
      if( LONGS_IN_ONE_ENTRY ) {
        if( int_reg < int_reg_max ) {
          tmp = INT_SCALE(int_reg++) + int_base;
        } else {
          tmp = STACK_num + stk_reg_pairs;
          stk_reg_pairs += 2;
        }
      } else {
        if( int_reg_pairs < int_reg_max ) {
          tmp = int_reg_pairs + int_base;
          int_reg_pairs += 2;
        } else {
          tmp = STACK_num + stk_reg_pairs;
          stk_reg_pairs += 2;
        }
      }
      sig[i] = tmp | (tmp+1)<<16; // Smear to pair
      break;

    case Op_RegF: 
      sig[i] = (flt_reg < flt_reg_max) ? (R_F0_num + flt_reg++) : STACK_num + stk_reg++;
      break;
    case Op_RegD: 
      if( sig[i+1] != 'h' ) { printf("expecting (h)alf, found %c\n", sig[i+1]); return; }
      if( flt_reg_pairs < flt_reg_max ) {
        tmp = R_F0_num + flt_reg_pairs;
        flt_reg_pairs += 2;
      } else {
        tmp = STACK_num + stk_reg_pairs;
        stk_reg_pairs += 2;
      }
      sig[i] = tmp | (tmp+1)<<16; // Smear to pair
      break;
    case 'h': sig[i] = 0; break;
    default:
      printf("Bad character: %c\n", sig[i] );
      return;
    }
  }

  printf("java ");
  printf(LP64 ? "LP64 " : "LP32 ");
  printf(LONGS_IN_ONE_ENTRY ? "long1: " : "long2: ");
  print_convention(sig,s,length);
}

static int int_stk_helper( int i ) {
  if( i < 6 ) return R_O0_num + (LONGS_IN_ONE_ENTRY ? i<<1 : i);
  else        return STACK_num + (LP64              ? i<<1 : i);
}

static void native_convention( int *sig, const char *s, int length ) {
  if( LP64 && !LONGS_IN_ONE_ENTRY ) {
    printf("LP64 and 2-reg longs not supported\n");
    return;
  }
  for( int i = 0; i < length; i++ )
    sig[i] = s[i];              // Reset sig array

  // The native convention is V8 if !LP64, which means the V8 convention is
  // used both with and without LONGS_IN_ONE_ENTRY, an unfortunate split.  The
  // same actual machine registers are used, but they are named differently in
  // the LONGS_IN_ONE_ENTRY mode.  The LP64 convention is the V9 convention
  // which is slightly more sane.
  
  if( LP64 ) {
    // V9 convention: All things "as-if" on double-wide stack slots.
    // Hoist any int/ptr/long's in the first 6 to int regs.
    // Hoist any flt/dbl's in the first 16 dbl regs.
    int j = 0;                  // Count of actual args, not HALVES
    for( int i=0; i<length; i++, j++ ) {
      int tmp;
      switch( sig[i] ) {
      case Op_RegI:
        sig[i] = int_stk_helper( j );
        break;
      case Op_RegL:
        if( sig[i+1] != 'h' ) { printf("expecting (h)alf, found %c\n", sig[i+1]); return; }
      case Op_RegP:
        tmp = int_stk_helper( j );
        sig[i] = tmp | ((tmp+1) << 16); // Smear to pair
        break;
      case Op_RegF:                 // V9ism: floats go in ODD registers
        sig[i] = ((j < 16) ? R_F1_num : (STACK_num + 1)) + (j<<1);
        break;
      case Op_RegD:                 // V9ism: doubles go in EVEN/ODD regs
        tmp    = ((j < 16) ? R_F0_num : STACK_num) + (j<<1);
        sig[i] = tmp | ((tmp+1) << 16); // Smear to pair
        break;
      case 'h': sig[i] = 0; j--; break; // Do not count HALVES
      default:
        printf("Bad character: %c\n", sig[i] );
        return;
      }
    }

  } else {
    // V8 convention: first 6 things in O-regs, rest on stack.
    // Alignment is willy-nilly.
    for( int i=0; i<length; i++ ) {
      int tmp;
      switch( sig[i] ) {
      case Op_RegI:
      case Op_RegP:
      case Op_RegF:
        sig[i] = int_stk_helper( i );
        break;
      case Op_RegL:
      case Op_RegD:
        if( sig[i+1] != 'h' ) { printf("expecting (h)alf, found %c\n", sig[i+1]); return; }
        tmp = int_stk_helper( i );
        sig[i] = tmp | (int_stk_helper( i+1 ) << 16);
        break;
      case 'h': sig[i] = 0; break;
      default:
        printf("Bad character: %c\n", sig[i] );
        return;
      }
    }
  }

  printf("natv ");
  printf(LP64 ? "LP64 " : "LP32 ");
  printf(LONGS_IN_ONE_ENTRY ? "long1: " : "long2: ");
  print_convention(sig,s,length);
}

int main( int argc, char **argv ) {

  if( argc != 2 ) {
    printf("Usage: args IPFLhDh... (Java argument string)\n");
    printf("Returns argument layout\n");
    return -1;
  }

  const char *s = argv[1];
  int length = strlen(s);
  int sig[1000];

  LP64 = false; LONGS_IN_ONE_ENTRY = false;
  java_convention( sig, s, length );
  LP64 = false; LONGS_IN_ONE_ENTRY = true;
  java_convention( sig, s, length );
  LP64 = true ; LONGS_IN_ONE_ENTRY = true;
  java_convention( sig, s, length );

  LP64 = false; LONGS_IN_ONE_ENTRY = false;
  native_convention( sig, s, length );
  LP64 = false; LONGS_IN_ONE_ENTRY = true;
  native_convention( sig, s, length );
  LP64 = true ; LONGS_IN_ONE_ENTRY = true;
  native_convention( sig, s, length );
}

