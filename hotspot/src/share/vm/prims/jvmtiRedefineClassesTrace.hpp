/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

// RedefineClasses tracing support via the TraceRedefineClasses
// option. A bit is assigned to each group of trace messages.
// Groups of messages are individually selectable. We have to use
// decimal values on the command line since the command option
// parsing logic doesn't like non-decimal numerics. The HEX values
// are used in the actual RC_TRACE() calls for sanity. To achieve
// the old cumulative behavior, pick the level after the one in
// which you are interested and subtract one, e.g., 33554431 will
// print every tracing message.
//
//    0x00000000 |          0 - default; no tracing messages
//    0x00000001 |          1 - name each target class before loading, after
//                              loading and after redefinition is completed
//    0x00000002 |          2 - print info if parsing, linking or
//                              verification throws an exception
//    0x00000004 |          4 - print timer info for the VM operation
//    0x00000008 |          8 - print subclass counter updates
//    0x00000010 |         16 - unused
//    0x00000020 |         32 - unused
//    0x00000040 |         64 - unused
//    0x00000080 |        128 - unused
//    0x00000100 |        256 - previous class weak reference addition
//    0x00000200 |        512 - previous class weak reference mgmt during
//                              class unloading checks (GC)
//    0x00000400 |       1024 - previous class weak reference mgmt during
//                              add previous ops (GC)
//    0x00000800 |       2048 - previous class breakpoint mgmt
//    0x00001000 |       4096 - detect calls to obsolete methods
//    0x00002000 |       8192 - fail a guarantee() in addition to detection
//    0x00004000 |      16384 - unused
//    0x00008000 |      32768 - old/new method matching/add/delete
//    0x00010000 |      65536 - impl details: CP size info
//    0x00020000 |     131072 - impl details: CP merge pass info
//    0x00040000 |     262144 - impl details: CP index maps
//    0x00080000 |     524288 - impl details: modified CP index values
//    0x00100000 |    1048576 - impl details: vtable updates
//    0x00200000 |    2097152 - impl details: itable updates
//    0x00400000 |    4194304 - impl details: constant pool cache updates
//    0x00800000 |    8388608 - impl details: methodComparator info
//    0x01000000 |   16777216 - impl details: nmethod evolution info
//    0x02000000 |   33554432 - impl details: annotation updates
//    0x04000000 |   67108864 - impl details: StackMapTable updates
//    0x08000000 |  134217728 - impl details: OopMapCache updates
//    0x10000000 |  268435456 - unused
//    0x20000000 |  536870912 - unused
//    0x40000000 | 1073741824 - unused
//    0x80000000 | 2147483648 - unused
//
// Note: The ResourceMark is to cleanup resource allocated args.
//   The "while (0)" is so we can use semi-colon at end of RC_TRACE().
#define RC_TRACE(level, args) \
  if ((TraceRedefineClasses & level) != 0) { \
    ResourceMark rm; \
    tty->print("RedefineClasses-0x%x: ", level); \
    tty->print_cr args; \
  } while (0)

#define RC_TRACE_WITH_THREAD(level, thread, args) \
  if ((TraceRedefineClasses & level) != 0) { \
    ResourceMark rm(thread); \
    tty->print("RedefineClasses-0x%x: ", level); \
    tty->print_cr args; \
  } while (0)

#define RC_TRACE_MESG(args) \
  { \
    ResourceMark rm; \
    tty->print("RedefineClasses: "); \
    tty->print_cr args; \
  } while (0)

// Macro for checking if TraceRedefineClasses has a specific bit
// enabled. Returns true if the bit specified by level is set.
#define RC_TRACE_ENABLED(level) ((TraceRedefineClasses & level) != 0)

// Macro for checking if TraceRedefineClasses has one or more bits
// set in a range of bit values. Returns true if one or more bits
// is set in the range from low..high inclusive. Assumes that low
// and high are single bit values.
//
// ((high << 1) - 1)
//     Yields a mask that removes bits greater than the high bit value.
//     This algorithm doesn't work with highest bit.
// ~(low - 1)
//     Yields a mask that removes bits lower than the low bit value.
#define RC_TRACE_IN_RANGE(low, high) \
(((TraceRedefineClasses & ((high << 1) - 1)) & ~(low - 1)) != 0)

// Timer support macros. Only do timer operations if timer tracing
// is enabled. The "while (0)" is so we can use semi-colon at end of
// the macro.
#define RC_TIMER_START(t) \
  if (RC_TRACE_ENABLED(0x00000004)) { \
    t.start(); \
  } while (0)
#define RC_TIMER_STOP(t) \
  if (RC_TRACE_ENABLED(0x00000004)) { \
    t.stop(); \
  } while (0)
