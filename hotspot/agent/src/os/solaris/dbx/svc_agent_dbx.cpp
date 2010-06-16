/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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

// This is the implementation of a very simple dbx import module which
// handles requests from the VM which come in over a socket. The
// higher-level Java wrapper for dbx starts the debugger, attaches to
// the process, imports this command, and runs it. After that, the SA
// writes commands to this agent via its own private communications
// channel. The intent is to move away from the text-based front-end
// completely in the near future (no more calling "debug" by printing
// text to dbx's stdin).

#include <stdio.h>
#include <errno.h>
#include <ctype.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <string.h>
#include <stropts.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

#include <proc_service.h>
#include <sys/procfs_isa.h>
#include <rtld_db.h>
#include "proc_service_2.h"
#include "svc_agent_dbx.hpp"

static ServiceabilityAgentDbxModule* module = NULL;
#define NEEDS_CLEANUP

// Useful for debugging
#define VERBOSE_DEBUGGING

#ifdef VERBOSE_DEBUGGING
# define debug_only(x) x
#else
# define debug_only(x)
#endif

// For profiling
//#define PROFILING

#ifdef PROFILING
#define PROFILE_COUNT 200
static Timer scanTimer;
static Timer workTimer;
static Timer writeTimer;
static int numRequests = 0;
#endif /* PROFILING */

const char* ServiceabilityAgentDbxModule::CMD_ADDRESS_SIZE   = "address_size";
const char* ServiceabilityAgentDbxModule::CMD_PEEK_FAIL_FAST = "peek_fail_fast";
const char* ServiceabilityAgentDbxModule::CMD_PEEK           = "peek";
const char* ServiceabilityAgentDbxModule::CMD_POKE           = "poke";
const char* ServiceabilityAgentDbxModule::CMD_MAPPED         = "mapped";
const char* ServiceabilityAgentDbxModule::CMD_LOOKUP         = "lookup";
const char* ServiceabilityAgentDbxModule::CMD_THR_GREGS      = "thr_gregs";
const char* ServiceabilityAgentDbxModule::CMD_EXIT           = "exit";

// The initialization routines must not have C++ name mangling
extern "C" {

/** This is the initialization routine called by dbx upon importing of
    this module. Returns 0 upon successful initialization, -1 upon
    failure. */
int shell_imp_init(int major, int minor,
                   shell_imp_interp_t interp, int argc, char *argv[])
{
  // Ensure shell interpreter data structure is laid out the way we
  // expect
  if (major != SHELL_IMP_MAJOR) {
    debug_only(fprintf(stderr, "Serviceability agent: unexpected value for SHELL_IMP_MAJOR (got %d, expected %d)\n", major, SHELL_IMP_MAJOR);)
    return -1;
  }
  if (minor < SHELL_IMP_MINOR) {
    debug_only(fprintf(stderr, "Serviceability agent: unexpected value for SHELL_IMP_MINOR (got %d, expected >= %d)\n", minor, SHELL_IMP_MINOR);)
    return -1;
  }

  if (module != NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: module appears to already be initialized (should not happen)\n");)
    // Already initialized. Should not happen.
    return -1;
  }

  module = new ServiceabilityAgentDbxModule(major, minor, interp, argc, argv);
  if (!module->install()) {
    debug_only(fprintf(stderr, "Serviceability agent: error installing import module\n");)
    delete module;
    module = NULL;
    return -1;
  }

  // Installation was successful. Next step will be for the user to
  // enter the appropriate command on the command line, which will
  // make the SA's dbx module wait for commands to come in over the
  // socket.
  return 0;
}

/** This is the routine called by dbx upon unloading of this module.
    Returns 0 upon success, -1 upon failure. */
int
shell_imp_fini(shell_imp_interp_t)
{
  if (module == NULL) {
    return -1;
  }

  bool res = module->uninstall();
  delete module;
  module = NULL;
  if (!res) {
    return -1;
  }
  return 0;
}

} // extern "C"

/** This is the routine which is called by the dbx shell when the user
    requests the serviceability agent module to run. This delegates to
    ServiceabilityAgentDbxModule::run. This routine's signature must
    match that of shell_imp_fun_t. */
extern "C" {
static int
svc_agent_run(shell_imp_interp_t, int, char **, void *) {
  if (module == NULL) {
    return -1;
  }

  module->run();
  return 0;
}
}

/*
 * Implementation of ServiceabilityAgentDbxModule class
 */

// NOTE: we need to forward declare the special "ps_get_prochandle2"
// function which allows examination of core files as well. It isn't
// currently in proc_service_2.h. Note also that it has name mangling
// because it isn't declared extern "C".
//const struct ps_prochandle *ps_get_prochandle2(int cores_too);

ServiceabilityAgentDbxModule::ServiceabilityAgentDbxModule(int, int, shell_imp_interp_t interp,
                                                           int argc, char *argv[])
  :myComm(32768, 131072)
{
  _interp = interp;
  _argc = argc;
  _argv = argv;
  _tdb_agent = NULL;
  peek_fail_fast = false;
  libThreadName = NULL;
}

ServiceabilityAgentDbxModule::~ServiceabilityAgentDbxModule() {
  if (_command != NULL) {
    uninstall();
  }
}

char*
readCStringFromProcess(psaddr_t addr) {
  char c;
  int num = 0;
  ps_prochandle* cur_proc = (ps_prochandle*) ps_get_prochandle2(1);

  // Search for null terminator
  do {
    if (ps_pread(cur_proc, addr + num, &c, 1) != PS_OK) {
      return NULL;
    }
    ++num;
  } while (c != 0);

  // Allocate string
  char* res = new char[num];
  if (ps_pread(cur_proc, addr, res, num) != PS_OK) {
    delete[] res;
    return NULL;
  }
  return res;
}

int
findLibThreadCB(const rd_loadobj_t* lo, void* data) {
  ServiceabilityAgentDbxModule* module = (ServiceabilityAgentDbxModule*) data;
  char* name = readCStringFromProcess(lo->rl_nameaddr);
  if (strstr(name, "libthread.so") != NULL) {
    module->libThreadName = name;
    return 0;
  } else {
    delete[] name;
    return 1;
  }
}

bool
ServiceabilityAgentDbxModule::install() {
  // NOTE interdependency between here and Java side wrapper
  // FIXME: casts of string literal to char * to match prototype
  _command = shell_imp_define_command((char *) "svc_agent_run",
                                      &svc_agent_run,
                                      0,
                                      NULL,
                                      (char *) "Run the serviceability agent's dbx module.\n"
                                      "This routine causes the module to listen on a socket for requests.\n"
                                      "It does not return until the Java-side code tells it to exit, at\n"
                                      "which point control is returned to the dbx shell.");
  if (_command == NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: Failed to install svc_agent_run command\n"));
    return false;
  }

  // This is fairly painful. Since dbx doesn't currently load
  // libthread_db with RTLD_GLOBAL, we can't just use RTLD_DEFAULT for
  // the argument to dlsym. Instead, we have to use rtld_db to search
  // through the loaded objects in the target process for libthread.so and

  // Try rtld_db
  if (rd_init(RD_VERSION) != RD_OK) {
    debug_only(fprintf(stderr, "Serviceability agent: Unable to init rtld_db\n"));
    return false;
  }

  rd_agent_t* rda = rd_new((struct ps_prochandle*) ps_get_prochandle2(1));
  if (rda == NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: Unable to allocate rtld_db agent\n"));
    return false;
  }

  if (rd_loadobj_iter(rda, (rl_iter_f*) findLibThreadCB, this) != RD_OK) {
    debug_only(fprintf(stderr, "Serviceability agent: Loadobject iteration failed\n"));
    return false;
  }

  if (libThreadName == NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: Failed to find pathname to libthread.so in target process\n"));
    return false;
  }

  // Find and open libthread_db.so
  char* slash = strrchr(libThreadName, '/');
  if (slash == NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: can't parse path to libthread.so \"%s\"\n"));
    return false;
  }

  int slashPos = slash - libThreadName;
  char* buf = new char[slashPos + strlen("libthread_db.so") + 20]; // slop
  if (buf == NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: error allocating libthread_db.so pathname\n"));
    return false;
  }
  strncpy(buf, libThreadName, slashPos + 1);

  // Check dbx's data model; use sparcv9/ subdirectory if 64-bit and
  // if target process is 32-bit
  if ((sizeof(void*) == 8) &&
      (strstr(libThreadName, "sparcv9") == NULL)) {
    strcpy(buf + slashPos + 1, "sparcv9/");
    slashPos += strlen("sparcv9/");
  }

  strcpy(buf + slashPos + 1, "libthread_db.so");

  libThreadDB = dlopen(buf, RTLD_LAZY);
  void* tmpDB = libThreadDB;
  if (libThreadDB == NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: Warning: unable to find libthread_db.so at \"%s\"\n", buf));
    // Would like to handle this case as well. Maybe dbx has a better
    // idea of where libthread_db.so lies. If the problem with dbx
    // loading libthread_db without RTLD_GLOBAL specified ever gets
    // fixed, we could run this code all the time.
    tmpDB = RTLD_DEFAULT;
  }

  delete[] buf;

  // Initialize access to libthread_db
  td_init_fn          = (td_init_fn_t*)          dlsym(tmpDB, "td_init");
  td_ta_new_fn        = (td_ta_new_fn_t*)        dlsym(tmpDB, "td_ta_new");
  td_ta_delete_fn     = (td_ta_delete_fn_t*)     dlsym(tmpDB, "td_ta_delete");
  td_ta_map_id2thr_fn = (td_ta_map_id2thr_fn_t*) dlsym(tmpDB, "td_ta_map_id2thr");
  td_thr_getgregs_fn  = (td_thr_getgregs_fn_t*)  dlsym(tmpDB, "td_thr_getgregs");

  if (td_init_fn == NULL ||
      td_ta_new_fn == NULL ||
      td_ta_delete_fn == NULL ||
      td_ta_map_id2thr_fn == NULL ||
      td_thr_getgregs_fn == NULL) {
    debug_only(fprintf(stderr, "Serviceability agent: Failed to find one or more libthread_db symbols:\n"));
    debug_only(if (td_init_fn == NULL)          fprintf(stderr, "  td_init\n"));
    debug_only(if (td_ta_new_fn == NULL)        fprintf(stderr, "  td_ta_new\n"));
    debug_only(if (td_ta_delete_fn == NULL)     fprintf(stderr, "  td_ta_delete\n"));
    debug_only(if (td_ta_map_id2thr_fn == NULL) fprintf(stderr, "  td_ta_map_id2thr\n"));
    debug_only(if (td_thr_getgregs_fn == NULL)  fprintf(stderr, "  td_thr_getgregs\n"));
    return false;
  }

  if ((*td_init_fn)() != TD_OK) {
    debug_only(fprintf(stderr, "Serviceability agent: Failed to initialize libthread_db\n"));
    return false;
  }

  return true;
}

bool
ServiceabilityAgentDbxModule::uninstall() {
  if (_command == NULL) {
    return false;
  }

  if (libThreadDB != NULL) {
    dlclose(libThreadDB);
    libThreadDB = NULL;
  }

  int res = shell_imp_undefine_command(_command);

  if (res != 0) {
    return false;
  }

  return true;
}

bool
ServiceabilityAgentDbxModule::run() {
  // This is where most of the work gets done.
  // The command processor loop looks like the following:
  //  - create listening socket
  //  - accept a connection (only one for now)
  //  - while that connection is open and the "exit" command has not
  //    been received:
  //    - read command
  //    - if it's the exit command, cleanup and return
  //    - otherwise, process command and write result

  int listening_socket = socket(AF_INET, SOCK_STREAM, 0);
  if (listening_socket < 0) {
    return false;
  }

  // Set the SO_REUSEADDR property on the listening socket. This
  // prevents problems with calls to bind() to the same port failing
  // after this process exits. This seems to work on all platforms.
  int reuse_address = 1;
  if (setsockopt(listening_socket, SOL_SOCKET, SO_REUSEADDR,
                 (char *)&reuse_address, sizeof(reuse_address)) < 0) {
    close(listening_socket);
    return false;
  }

  sockaddr_in server_address;
  // Build the server address. We can bind the listening socket to the
  // INADDR_ANY internet address.
  memset((char*)&server_address, 0, sizeof(server_address));
  server_address.sin_family = AF_INET;
  server_address.sin_addr.s_addr = (unsigned long)htonl(INADDR_ANY);
  server_address.sin_port = htons((short)PORT);

  // Bind socket to port
  if (bind(listening_socket, (sockaddr*) &server_address,
           sizeof(server_address)) < 0) {
    close(listening_socket);
    return false;
  }

  // Arbitrarily chosen backlog of 5 (shouldn't matter since we expect
  // at most one connection)
  if (listen(listening_socket, 5) < 0) {
    close(listening_socket);
    return false;
  }

  // OK, now ready to wait for a data connection. This call to
  // accept() will block.
  struct sockaddr_in client_address;
  int address_len   = sizeof(client_address);
  int client_socket = accept(listening_socket, (sockaddr*) &client_address,
                         &address_len);
  // Close listening socket regardless of whether accept() succeeded.
  // (FIXME: this may be annoying, especially during debugging, but I
  // really feel that robustness and multiple connections should be
  // handled higher up, e.g., at the Java level -- multiple clients
  // could conceivably connect to the SA via RMI, and that would be a
  // more robust solution than implementing multiple connections at
  // this level)
  NEEDS_CLEANUP;

  // NOTE: the call to shutdown() usually fails, so don't panic if this happens
  shutdown(listening_socket, 2);

  if (close(listening_socket) < 0) {
    debug_only(fprintf(stderr, "Serviceability agent: Error closing listening socket\n"));
    return false;
  }

  if (client_socket < 0) {
    debug_only(fprintf(stderr, "Serviceability agent: Failed to open client socket\n"));
    // No more cleanup necessary
    return false;
  }

  // Attempt to disable TCP buffering on this socket. We send small
  // amounts of data back and forth and don't want buffering.
  int buffer_val = 1;
  if (setsockopt(client_socket, IPPROTO_IP, TCP_NODELAY, (char *) &buffer_val, sizeof(buffer_val)) < 0) {
    debug_only(fprintf(stderr, "Serviceability agent: Failed to set TCP_NODELAY option on client socket\n"));
    cleanup(client_socket);
    return false;
  }

  // OK, we have the data socket through which we will communicate
  // with the Java side. Wait for commands or until reading or writing
  // caused an error.

  bool should_continue = true;

  myComm.setSocket(client_socket);

#ifdef PROFILING
  scanTimer.reset();
  workTimer.reset();
  writeTimer.reset();
#endif

  // Allocate a new thread agent for libthread_db
  if ((*td_ta_new_fn)((ps_prochandle*) ps_get_prochandle2(1), &_tdb_agent) !=
      TD_OK) {
    debug_only(fprintf(stderr, "Serviceability agent: Failed to allocate thread agent\n"));
    cleanup(client_socket);
    return false;
  }

  do {
    // Decided to use text to communicate between these processes.
    // Probably will make debugging easier -- could telnet in if
    // necessary. Will make scanning harder, but probably doesn't
    // matter.

    // Why not just do what workshop does and parse dbx's console?
    // Probably could do that, but at least this way we are in control
    // of the text format on both ends.

    // FIXME: should have some way of synchronizing these commands
    // between the C and Java sources.

    NEEDS_CLEANUP;

    // Do a blocking read of a line from the socket.
    char *input_buffer = myComm.readLine();
    if (input_buffer == NULL) {
      debug_only(fprintf(stderr, "Serviceability agent: error during read: errno = %d\n", errno));
      debug_only(perror("Serviceability agent"));
      // Error occurred during read.
      // FIXME: should guard against SIGPIPE
      cleanup(client_socket);
      return false;
    }

    // OK, now ready to scan. See README-commands.txt for syntax
    // descriptions.

    bool res = false;
    if (!strncmp(input_buffer, CMD_ADDRESS_SIZE, strlen(CMD_ADDRESS_SIZE))) {
      res = handleAddressSize(input_buffer + strlen(CMD_ADDRESS_SIZE));
    } else if (!strncmp(input_buffer, CMD_PEEK_FAIL_FAST, strlen(CMD_PEEK_FAIL_FAST))) {
      res = handlePeekFailFast(input_buffer + strlen(CMD_PEEK_FAIL_FAST));
    } else if (!strncmp(input_buffer, CMD_PEEK, strlen(CMD_PEEK))) {
      res = handlePeek(input_buffer + strlen(CMD_PEEK));
    } else if (!strncmp(input_buffer, CMD_POKE, strlen(CMD_POKE))) {
      res = handlePoke(input_buffer + strlen(CMD_POKE));
    } else if (!strncmp(input_buffer, CMD_MAPPED, strlen(CMD_MAPPED))) {
      res = handleMapped(input_buffer + strlen(CMD_MAPPED));
    } else if (!strncmp(input_buffer, CMD_LOOKUP, strlen(CMD_LOOKUP))) {
      res = handleLookup(input_buffer + strlen(CMD_LOOKUP));
    } else if (!strncmp(input_buffer, CMD_THR_GREGS, strlen(CMD_THR_GREGS))) {
      res = handleThrGRegs(input_buffer + strlen(CMD_THR_GREGS));
    } else if (!strncmp(input_buffer, CMD_EXIT, strlen(CMD_EXIT))) {
      should_continue = false;
    }

    if (should_continue) {
      if (!res) {
        cleanup(client_socket);
        return false;
      }
    }

#ifdef PROFILING
    if (++numRequests == PROFILE_COUNT) {
      fprintf(stderr, "%d requests: %d ms scanning, %d ms work, %d ms writing\n",
              PROFILE_COUNT, scanTimer.total(), workTimer.total(), writeTimer.total());
      fflush(stderr);
      scanTimer.reset();
      workTimer.reset();
      writeTimer.reset();
      numRequests = 0;
    }
#endif

  } while (should_continue);

  // Successful exit
  cleanup(client_socket);
  return true;
}

void
ServiceabilityAgentDbxModule::cleanup(int client_socket) {
  shutdown(client_socket, 2);
  close(client_socket);
  if (_tdb_agent != NULL) {
    (*td_ta_delete_fn)(_tdb_agent);
  }
}

bool
ServiceabilityAgentDbxModule::handleAddressSize(char* data) {
  int data_model;
  ps_err_e result = ps_pdmodel((ps_prochandle*) ps_get_prochandle2(1),
                               &data_model);
  if (result != PS_OK) {
    myComm.writeString("0");
    myComm.flush();
    return false;
  }

  int val;
  switch (data_model) {
  case PR_MODEL_ILP32:
    val = 32;
    break;
  case PR_MODEL_LP64:
    val = 64;
    break;
  default:
    val = 0;
    break;
  }

  if (!myComm.writeInt(val)) {
    return false;
  }
  if (!myComm.writeEOL()) {
    return false;
  }
  return myComm.flush();
}

bool
ServiceabilityAgentDbxModule::handlePeekFailFast(char* data) {
  unsigned int val;
  if (!scanUnsignedInt(&data, &val)) {
    return false;
  }
  peek_fail_fast = (val ? true : false);
  return true;
}

bool
ServiceabilityAgentDbxModule::handlePeek(char* data) {
  // Scan hex address, return false if failed
  psaddr_t addr;
#ifdef PROFILING
  scanTimer.start();
#endif /* PROFILING */
  if (!scanAddress(&data, &addr)) {
    return false;
  }
  unsigned int num;
  if (!scanUnsignedInt(&data, &num)) {
    return false;
  }
  if (num == 0) {
#ifdef PROFILING
    writeTimer.start();
#endif /* PROFILING */
    myComm.writeBinChar('B');
    myComm.writeBinChar(1);
    myComm.writeBinUnsignedInt(0);
    myComm.writeBinChar(0);
#ifdef PROFILING
    writeTimer.stop();
#endif /* PROFILING */
    return true;
  }
#ifdef PROFILING
  scanTimer.stop();
  workTimer.start();
#endif /* PROFILING */
  char* buf = new char[num];
  ps_prochandle* cur_proc = (ps_prochandle*) ps_get_prochandle2(1);
  ps_err_e result = ps_pread(cur_proc, addr, buf, num);
  if (result == PS_OK) {
    // Fast case; entire read succeeded.
#ifdef PROFILING
    workTimer.stop();
    writeTimer.start();
#endif /* PROFILING */
    myComm.writeBinChar('B');
    myComm.writeBinChar(1);
    myComm.writeBinUnsignedInt(num);
    myComm.writeBinChar(1);
    myComm.writeBinBuf(buf, num);
#ifdef PROFILING
    writeTimer.stop();
#endif /* PROFILING */
  } else {
#ifdef PROFILING
    workTimer.stop();
#endif /* PROFILING */

    if (peek_fail_fast) {
#ifdef PROFILING
    writeTimer.start();
#endif /* PROFILING */
      // Fail fast
      myComm.writeBinChar('B');
      myComm.writeBinChar(1);
      myComm.writeBinUnsignedInt(num);
      myComm.writeBinChar(0);
#ifdef PROFILING
    writeTimer.stop();
#endif /* PROFILING */
    } else {
      // Slow case: try to read one byte at a time
      // FIXME: need better way of handling this, a la VirtualQuery

      unsigned int  strideLen      = 0;
      int           bufIdx         = 0;
      bool          lastByteMapped = (ps_pread(cur_proc, addr, buf, 1) == PS_OK ? true : false);

#ifdef PROFILING
      writeTimer.start();
#endif /* PROFILING */
      myComm.writeBinChar('B');
      myComm.writeBinChar(1);
#ifdef PROFILING
      writeTimer.stop();
#endif /* PROFILING */

      for (int i = 0; i < num; ++i, ++addr) {
#ifdef PROFILING
        workTimer.start();
#endif /* PROFILING */
        result = ps_pread(cur_proc, addr, &buf[bufIdx], 1);
#ifdef PROFILING
        workTimer.stop();
#endif /* PROFILING */
        bool tmpMapped = (result == PS_OK ? true : false);
#ifdef PROFILING
        writeTimer.start();
#endif /* PROFILING */
        if (tmpMapped != lastByteMapped) {
          // State change. Write the length of the last stride.
          myComm.writeBinUnsignedInt(strideLen);
          if (lastByteMapped) {
            // Stop gathering data. Write the data of the last stride.
            myComm.writeBinChar(1);
            myComm.writeBinBuf(buf, strideLen);
            bufIdx = 0;
          } else {
            // Start gathering data to write.
            myComm.writeBinChar(0);
          }
          strideLen = 0;
          lastByteMapped = tmpMapped;
        }
#ifdef PROFILING
        writeTimer.stop();
#endif /* PROFILING */
        if (lastByteMapped) {
          ++bufIdx;
        }
        ++strideLen;
      }

      // Write last stride (must be at least one byte long by definition)
#ifdef PROFILING
      writeTimer.start();
#endif /* PROFILING */
      myComm.writeBinUnsignedInt(strideLen);
      if (lastByteMapped) {
        myComm.writeBinChar(1);
        myComm.writeBinBuf(buf, strideLen);
      } else {
        myComm.writeBinChar(0);
      }
#ifdef PROFILING
      writeTimer.stop();
#endif /* PROFILING */
    }
  }
  delete[] buf;
  myComm.flush();
  return true;
}

bool
ServiceabilityAgentDbxModule::handlePoke(char* data) {
  // FIXME: not yet implemented
  NEEDS_CLEANUP;
  bool res = myComm.writeBoolAsInt(false);
  myComm.flush();
  return res;
}

bool
ServiceabilityAgentDbxModule::handleMapped(char* data) {
  // Scan address
  psaddr_t addr;
  if (!scanAddress(&data, &addr)) {
    return false;
  }
  unsigned int num;
  if (!scanUnsignedInt(&data, &num)) {
    return false;
  }
  unsigned char val;
  ps_prochandle* cur_proc = (ps_prochandle*) ps_get_prochandle2(1);
  char* buf = new char[num];
  if (ps_pread(cur_proc, addr, buf, num) == PS_OK) {
    myComm.writeBoolAsInt(true);
  } else {
    myComm.writeBoolAsInt(false);
  }
  delete[] buf;
  myComm.writeEOL();
  myComm.flush();
  return true;
}

extern "C"
int loadobj_iterator(const rd_loadobj_t* loadobj, void *) {
  if (loadobj != NULL) {
    fprintf(stderr, "loadobj_iterator: visited loadobj \"%p\"\n", (void*) loadobj->rl_nameaddr);
    return 1;
  }

  fprintf(stderr, "loadobj_iterator: NULL loadobj\n");
  return 0;
}

bool
ServiceabilityAgentDbxModule::handleLookup(char* data) {
  // Debugging: iterate over loadobjs
  /*
  rd_agent_t* rld_agent = rd_new((ps_prochandle*) ps_get_prochandle2(1));
  rd_loadobj_iter(rld_agent, &loadobj_iterator, NULL);
  rd_delete(rld_agent);
  */

#ifdef PROFILING
  scanTimer.start();
#endif /* PROFILING */

  char* object_name = scanSymbol(&data);
  if (object_name == NULL) {
    return false;
  }
  char* symbol_name = scanSymbol(&data);
  if (symbol_name == NULL) {
    delete[] object_name;
    return false;
  }

#ifdef PROFILING
  scanTimer.stop();
  workTimer.start();
#endif /* PROFILING */

  ps_sym_t sym;
  // FIXME: check return values from write routines
  ps_prochandle* process = (ps_prochandle*) ps_get_prochandle2(1);
  ps_err_e lookup_res = ps_pglobal_sym(process,
                                       object_name, symbol_name, &sym);
#ifdef PROFILING
  workTimer.stop();
  writeTimer.start();
#endif /* PROFILING */

  delete[] object_name;
  delete[] symbol_name;
  if (lookup_res != PS_OK) {
    // This is too noisy
    //    debug_only(fprintf(stderr, "ServiceabilityAgentDbxModule::handleLookup: error %d\n", lookup_res));
    myComm.writeString("0x0");
  } else {
    myComm.writeAddress((void *)sym.st_value);
  }
  myComm.writeEOL();
  myComm.flush();

#ifdef PROFILING
  writeTimer.stop();
#endif /* PROFILING */

  return true;
}

bool
ServiceabilityAgentDbxModule::handleThrGRegs(char* data) {
#ifdef PROFILING
  scanTimer.start();
#endif /* PROFILING */

  unsigned int num;
  // Get the thread ID
  if (!scanUnsignedInt(&data, &num)) {
    return false;
  }

#ifdef PROFILING
  scanTimer.stop();
  workTimer.start();
#endif /* PROFILING */

  // Map tid to thread handle
  td_thrhandle_t thread_handle;
  if ((*td_ta_map_id2thr_fn)(_tdb_agent, num, &thread_handle) != TD_OK) {
    //    fprintf(stderr, "Error mapping thread ID %d to thread handle\n", num);
    return false;
  }

  // Fetch register set
  prgregset_t reg_set;
  memset(reg_set, 0, sizeof(reg_set));
  td_err_e result = (*td_thr_getgregs_fn)(&thread_handle, reg_set);
  if ((result != TD_OK) && (result != TD_PARTIALREG)) {
    //    fprintf(stderr, "Error fetching registers for thread handle %d: error = %d\n", num, result);
    return false;
  }

#ifdef PROFILING
  workTimer.stop();
  writeTimer.start();
#endif /* PROFILING */

#if (defined(__sparc) || defined(__i386))
  myComm.writeInt(NPRGREG);
  myComm.writeSpace();
  for (int i = 0; i < NPRGREG; i++) {
    myComm.writeAddress((void *)reg_set[i]);
    if (i == NPRGREG - 1) {
      myComm.writeEOL();
    } else {
      myComm.writeSpace();
    }
  }
#else
#error  Please port ServiceabilityAgentDbxModule::handleThrGRegs to your current platform
#endif

  myComm.flush();

#ifdef PROFILING
  writeTimer.stop();
#endif /* PROFILING */

  return true;
}

//
// Input routines
//

bool
ServiceabilityAgentDbxModule::scanAddress(char** data, psaddr_t* addr) {
  *addr = 0;

  // Skip whitespace
  while ((**data != 0) && (isspace(**data))) {
    ++*data;
  }

  if (**data == 0) {
    return false;
  }

  if (strncmp(*data, "0x", 2) != 0) {
    return false;
  }

  *data += 2;

  while ((**data != 0) && (!isspace(**data))) {
    int val;
    bool res = charToNibble(**data, &val);
    if (!res) {
      return false;
    }
    *addr <<= 4;
    *addr |= val;
    ++*data;
  }

  return true;
}

bool
ServiceabilityAgentDbxModule::scanUnsignedInt(char** data, unsigned int* num) {
  *num = 0;

  // Skip whitespace
  while ((**data != 0) && (isspace(**data))) {
    ++*data;
  }

  if (**data == 0) {
    return false;
  }

  while ((**data != 0) && (!isspace(**data))) {
    char cur = **data;
    if ((cur < '0') || (cur > '9')) {
      return false;
    }
    *num *= 10;
    *num += cur - '0';
    ++*data;
  }

  return true;
}

char*
ServiceabilityAgentDbxModule::scanSymbol(char** data) {
  // Skip whitespace
  while ((**data != 0) && (isspace(**data))) {
    ++*data;
  }

  if (**data == 0) {
    return NULL;
  }

  // First count length
  int len = 1; // Null terminator
  char* tmpData = *data;
  while ((*tmpData != 0) && (!isspace(*tmpData))) {
    ++tmpData;
    ++len;
  }
  char* buf = new char[len];
  strncpy(buf, *data, len - 1);
  buf[len - 1] = 0;
  *data += len - 1;
  return buf;
}

bool
ServiceabilityAgentDbxModule::charToNibble(char ascii, int* value) {
  if (ascii >= '0' && ascii <= '9') {
    *value = ascii - '0';
    return true;
  } else if (ascii >= 'A' && ascii <= 'F') {
    *value = 10 + ascii - 'A';
    return true;
  } else if (ascii >= 'a' && ascii <= 'f') {
    *value = 10 + ascii - 'a';
    return true;
  }

  return false;
}


char*
ServiceabilityAgentDbxModule::readCStringFromProcess(psaddr_t addr) {
  char c;
  int num = 0;
  ps_prochandle* cur_proc = (ps_prochandle*) ps_get_prochandle2(1);

  // Search for null terminator
  do {
    if (ps_pread(cur_proc, addr + num, &c, 1) != PS_OK) {
      return NULL;
    }
    ++num;
  } while (c != 0);

  // Allocate string
  char* res = new char[num];
  if (ps_pread(cur_proc, addr, res, num) != PS_OK) {
    delete[] res;
    return NULL;
  }
  return res;
}


//--------------------------------------------------------------------------------
// Class Timer
//

Timer::Timer() {
  reset();
}

Timer::~Timer() {
}

void
Timer::start() {
  gettimeofday(&startTime, NULL);
}

void
Timer::stop() {
  struct timeval endTime;
  gettimeofday(&endTime, NULL);
  totalMicroseconds += timevalDiff(&startTime, &endTime);
  ++counter;
}

long
Timer::total() {
  return (totalMicroseconds / 1000);
}

long
Timer::average() {
  return (long) ((double) total() / (double) counter);
}

void
Timer::reset() {
  totalMicroseconds = 0;
  counter = 0;
}

long long
Timer::timevalDiff(struct timeval* start, struct timeval* end) {
  long long secs = end->tv_sec - start->tv_sec;
  secs *= 1000000;
  long long usecs = end->tv_usec - start->tv_usec;
  return (secs + usecs);
}
