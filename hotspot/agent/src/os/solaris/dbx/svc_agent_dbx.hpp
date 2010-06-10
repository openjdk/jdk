/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

#include "shell_imp.h"
#include "IOBuf.hpp"
#include <sys/time.h>
#include <thread_db.h>

typedef td_err_e td_init_fn_t();
typedef td_err_e td_ta_new_fn_t(struct ps_prochandle *, td_thragent_t **);
typedef td_err_e td_ta_delete_fn_t(td_thragent_t *);
typedef td_err_e td_ta_map_id2thr_fn_t(const td_thragent_t *, thread_t,  td_thrhandle_t *);
typedef td_err_e td_thr_getgregs_fn_t(const td_thrhandle_t *, prgregset_t);

class ServiceabilityAgentDbxModule {
public:
  ServiceabilityAgentDbxModule(int major, int minor,
                               shell_imp_interp_t interp, int argc, char *argv[]);
  ~ServiceabilityAgentDbxModule();

  bool install();
  bool uninstall();

  /* This is invoked through the dbx command interpreter. It listens
     on a socket for commands and does not return until it receives an
     "exit" command. At that point control is returned to dbx's main
     loop, at which point if the user sends an exit command to dbx's
     shell the dbx process will exit. Returns true if completed
     successfully, false if an error occurred while running (for
     example, unable to bind listening socket). */
  bool run();

private:

  // This must be shared between the Java and C layers
  static const int PORT = 21928;

  // Command handlers
  bool handleAddressSize(char* data);
  bool handlePeekFailFast(char* data);
  bool handlePeek(char* data);
  bool handlePoke(char* data);
  bool handleMapped(char* data);
  bool handleLookup(char* data);
  bool handleThrGRegs(char* data);

  // Input routines

  // May mutate addr argument even if result is false
  bool scanAddress(char** data, psaddr_t* addr);
  // May mutate num argument even if result is false
  bool scanUnsignedInt(char** data, unsigned int* num);
  // Returns NULL if error occurred while scanning. Otherwise, returns
  // newly-allocated character array which must be freed with delete[].
  char* scanSymbol(char** data);
  // Helper routine: converts ASCII to 4-bit integer. Returns true if
  // character is in range, false otherwise.
  bool charToNibble(char ascii, int* value);

  // Output routines

  // Writes an int with no leading or trailing spaces
  bool writeInt(int val, int fd);
  // Writes an address in hex format with no leading or trailing
  // spaces
  bool writeAddress(psaddr_t addr, int fd);
  // Writes a register in hex format with no leading or trailing
  // spaces (addresses and registers might be of different size)
  bool writeRegister(prgreg_t reg, int fd);
  // Writes a space to given file descriptor
  bool writeSpace(int fd);
  // Writes carriage return to given file descriptor
  bool writeCR(int fd);
  // Writes a bool as [0|1]
  bool writeBoolAsInt(bool val, int fd);
  // Helper routine: converts low 4 bits to ASCII [0..9][A..F]
  char nibbleToChar(unsigned char nibble);

  // Base routine called by most of the above
  bool writeString(const char* str, int fd);

  // Writes a binary character
  bool writeBinChar(char val, int fd);
  // Writes a binary unsigned int in network (big-endian) byte order
  bool writeBinUnsignedInt(unsigned int val, int fd);
  // Writes a binary buffer
  bool writeBinBuf(char* buf, int size, int fd);

  // Routine to flush the socket
  bool flush(int client_socket);

  void cleanup(int client_socket);

  // The shell interpreter on which we can invoke commands (?)
  shell_imp_interp_t _interp;

  // The "command line" arguments passed to us by dbx (?)
  int _argc;
  char **_argv;

  // The installed command in the dbx shell
  shell_imp_command_t _command;

  // Access to libthread_db (dlsym'ed to be able to pick up the
  // version loaded by dbx)
  td_init_fn_t*          td_init_fn;
  td_ta_new_fn_t*        td_ta_new_fn;
  td_ta_delete_fn_t*     td_ta_delete_fn;
  td_ta_map_id2thr_fn_t* td_ta_map_id2thr_fn;
  td_thr_getgregs_fn_t*  td_thr_getgregs_fn;

  // Our "thread agent" -- access to libthread_db
  td_thragent_t* _tdb_agent;

  // Path to libthread.so in target process; free with delete[]
  char* libThreadName;

  // Handle to dlopen'ed libthread_db.so
  void* libThreadDB;

  // Helper callback for finding libthread_db.so
  friend int findLibThreadCB(const rd_loadobj_t* lo, void* data);

  // Support for reading C strings out of the target process (so we
  // can find the correct libthread_db). Returns newly-allocated char*
  // which must be freed with delete[], or null if the read failed.
  char* readCStringFromProcess(psaddr_t addr);

  IOBuf myComm;

  // Output buffer support (used by writeString, writeChar, flush)
  char* output_buffer;
  int output_buffer_size;
  int output_buffer_pos;

  // "Fail fast" flag
  bool peek_fail_fast;

  // Commands
  static const char* CMD_ADDRESS_SIZE;
  static const char* CMD_PEEK_FAIL_FAST;
  static const char* CMD_PEEK;
  static const char* CMD_POKE;
  static const char* CMD_MAPPED;
  static const char* CMD_LOOKUP;
  static const char* CMD_THR_GREGS;
  static const char* CMD_EXIT;
};

// For profiling. Times reported are in milliseconds.
class Timer {
public:
  Timer();
  ~Timer();

  void start();
  void stop();
  long total();
  long average();
  void reset();

private:
  struct timeval startTime;
  long long totalMicroseconds; // stored internally in microseconds
  int counter;
  long long timevalDiff(struct timeval* startTime, struct timeval* endTime);
};
