/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2024 SAP SE. All rights reserved.
 * Copyright (c) 2022, IBM Corp.
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

// Encapsulates the libperfstat library.
//
// The purpose of this code is to dynamically load the libperfstat library
// instead of statically linking against it. The libperfstat library is an
// AIX-specific library which only exists on AIX, not on PASE. If I want to
// share binaries between AIX and PASE, I cannot directly link against libperfstat.so.

#ifndef OS_AIX_LIBPERFSTAT_AIX_HPP
#define OS_AIX_LIBPERFSTAT_AIX_HPP

#include <sys/types.h>
#include <stdlib.h>

///////////////////////////////////////////////////////////////////////////////////////////////
// These are excerpts from the AIX 7.1 libperfstat.h -
// this is all we need from libperfstat.h and I want to avoid having to include <libperfstat.h>

#define IDENTIFIER_LENGTH  64    /* length of strings included in the structures */
#define FIRST_CPU          ""    /* pseudo-name for fist CPU */
#define FIRST_NETINTERFACE ""    /* pseudo-name for first NETINTERFACE */


typedef struct { /* structure element identifier */
  char name[IDENTIFIER_LENGTH]; /* name of the identifier */
} perfstat_id_t;

#define CEC_ID_LEN 40           /* CEC identifier length */
#define MAXCORRALNAMELEN 25     /* length of the wpar name */
#define FIRST_WPARNAME ""       /* pseudo-name for the first WPAR */
#define FIRST_WPARID -1         /* pseudo-id for the first WPAR */

typedef unsigned short cid_t;   /* workload partition identifier */

typedef struct { /* Virtual memory utilization */
  u_longlong_t virt_total;    /* total virtual memory (in 4KB pages) */
  u_longlong_t real_total;    /* total real memory (in 4KB pages) */
  u_longlong_t real_free;     /* free real memory (in 4KB pages) */
  u_longlong_t real_pinned;   /* real memory which is pinned (in 4KB pages) */
  u_longlong_t real_inuse;    /* real memory which is in use (in 4KB pages) */
  u_longlong_t pgbad;         /* number of bad pages */
  u_longlong_t pgexct;        /* number of page faults */
  u_longlong_t pgins;         /* number of pages paged in */
  u_longlong_t pgouts;        /* number of pages paged out */
  u_longlong_t pgspins;       /* number of page ins from paging space */
  u_longlong_t pgspouts;      /* number of page outs from paging space */
  u_longlong_t scans;         /* number of page scans by clock */
  u_longlong_t cycles;        /* number of page replacement cycles */
  u_longlong_t pgsteals;      /* number of page steals */
  u_longlong_t numperm;       /* number of frames used for files (in 4KB pages) */
  u_longlong_t pgsp_total;    /* total paging space (in 4KB pages) */
  u_longlong_t pgsp_free;     /* free paging space (in 4KB pages) */
  u_longlong_t pgsp_rsvd;     /* reserved paging space (in 4KB pages) */
  u_longlong_t real_system;   /* real memory used by system segments (in 4KB pages). This is the sum of all the used pages in segment marked for system usage.
                               * Since segment classifications are not always guaranteed to be accurate, this number is only an approximation. */
  u_longlong_t real_user;     /* real memory used by non-system segments (in 4KB pages). This is the sum of all pages used in segments not marked for system usage.
                               * Since segment classifications are not always guaranteed to be accurate, this number is only an approximation. */
  u_longlong_t real_process;  /* real memory used by process segments (in 4KB pages). This is real_total-real_free-numperm-real_system. Since real_system is an
                               * approximation, this number is too. */
  u_longlong_t virt_active;   /* Active virtual pages. Virtual pages are considered active if they have been accessed */

} perfstat_memory_total_t;

typedef struct { /* global cpu information AIX 7.1 */
  int ncpus;                            /* number of active logical processors */
  int ncpus_cfg;                        /* number of configured processors */
  char description[IDENTIFIER_LENGTH];  /* processor description (type/official name) */
  u_longlong_t processorHZ;             /* processor speed in Hz */
  u_longlong_t user;                    /* raw total number of clock ticks spent in user mode */
  u_longlong_t sys;                     /* raw total number of clock ticks spent in system mode */
  u_longlong_t idle;                    /* raw total number of clock ticks spent idle */
  u_longlong_t wait;                    /* raw total number of clock ticks spent waiting for I/O */
  u_longlong_t pswitch;                 /* number of process switches (change in currently running process) */
  u_longlong_t syscall;                 /* number of system calls executed */
  u_longlong_t sysread;                 /* number of read system calls executed */
  u_longlong_t syswrite;                /* number of write system calls executed */
  u_longlong_t sysfork;                 /* number of forks system calls executed */
  u_longlong_t sysexec;                 /* number of execs system calls executed */
  u_longlong_t readch;                  /* number of characters transferred with read system call */
  u_longlong_t writech;                 /* number of characters transferred with write system call */
  u_longlong_t devintrs;                /* number of device interrupts */
  u_longlong_t softintrs;               /* number of software interrupts */
  time_t lbolt;                         /* number of ticks since last reboot */
  u_longlong_t loadavg[3];              /* (1<<SBITS) times the average number of runnables processes during the last 1, 5 and 15 minutes.
                                               * To calculate the load average, divide the numbers by (1<<SBITS). SBITS is defined in <sys/proc.h>. */
  u_longlong_t runque;                  /* length of the run queue (processes ready) */
  u_longlong_t swpque;                  /* ength of the swap queue (processes waiting to be paged in) */
  u_longlong_t bread;                   /* number of blocks read */
  u_longlong_t bwrite;                  /* number of blocks written */
  u_longlong_t lread;                   /* number of logical read requests */
  u_longlong_t lwrite;                  /* number of logical write requests */
  u_longlong_t phread;                  /* number of physical reads (reads on raw devices) */
  u_longlong_t phwrite;                 /* number of physical writes (writes on raw devices) */
  u_longlong_t runocc;                  /* updated whenever runque is updated, i.e. the runqueue is occupied.
                                               * This can be used to compute the simple average of ready processes  */
  u_longlong_t swpocc;                  /* updated whenever swpque is updated. i.e. the swpqueue is occupied.
                                               * This can be used to compute the simple average processes waiting to be paged in */
  u_longlong_t iget;                    /* number of inode lookups */
  u_longlong_t namei;                   /* number of vnode lookup from a path name */
  u_longlong_t dirblk;                  /* number of 512-byte block reads by the directory search routine to locate an entry for a file */
  u_longlong_t msg;                     /* number of IPC message operations */
  u_longlong_t sema;                    /* number of IPC semaphore operations */
  u_longlong_t rcvint;                  /* number of tty receive interrupts */
  u_longlong_t xmtint;                  /* number of tyy transmit interrupts */
  u_longlong_t mdmint;                  /* number of modem interrupts */
  u_longlong_t tty_rawinch;             /* number of raw input characters  */
  u_longlong_t tty_caninch;             /* number of canonical input characters (always zero) */
  u_longlong_t tty_rawoutch;            /* number of raw output characters */
  u_longlong_t ksched;                  /* number of kernel processes created */
  u_longlong_t koverf;                  /* kernel process creation attempts where:
                                               * -the user has forked to their maximum limit
                                               * -the configuration limit of processes has been reached */
  u_longlong_t kexit;                   /* number of kernel processes that became zombies */
  u_longlong_t rbread;                  /* number of remote read requests */
  u_longlong_t rcread;                  /* number of cached remote reads */
  u_longlong_t rbwrt;                   /* number of remote writes */
  u_longlong_t rcwrt;                   /* number of cached remote writes */
  u_longlong_t traps;                   /* number of traps */
  int ncpus_high;                       /* index of highest processor online */
  u_longlong_t puser;                   /* raw number of physical processor tics in user mode */
  u_longlong_t psys;                    /* raw number of physical processor tics in system mode */
  u_longlong_t pidle;                   /* raw number of physical processor tics idle */
  u_longlong_t pwait;                   /* raw number of physical processor tics waiting for I/O */
  u_longlong_t decrintrs;               /* number of decrementer tics interrupts */
  u_longlong_t mpcrintrs;               /* number of mpc's received interrupts */
  u_longlong_t mpcsintrs;               /* number of mpc's sent interrupts */
  u_longlong_t phantintrs;              /* number of phantom interrupts */
  u_longlong_t idle_donated_purr;       /* number of idle cycles donated by a dedicated partition enabled for donation */
  u_longlong_t idle_donated_spurr;      /* number of idle spurr cycles donated by a dedicated partition enabled for donation */
  u_longlong_t busy_donated_purr;       /* number of busy cycles donated by a dedicated partition enabled for donation */
  u_longlong_t busy_donated_spurr;      /* number of busy spurr cycles donated by a dedicated partition enabled for donation */
  u_longlong_t idle_stolen_purr;        /* number of idle cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t idle_stolen_spurr;       /* number of idle spurr cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t busy_stolen_purr;        /* number of busy cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t busy_stolen_spurr;       /* number of busy spurr cycles stolen by the hypervisor from a dedicated partition */
  short iowait;                         /* number of processes that are asleep waiting for buffered I/O */
  short physio;                         /* number of processes waiting for raw I/O */
  longlong_t twait;                     /* number of threads that are waiting for filesystem direct(cio) */
  u_longlong_t hpi;                     /* number of hypervisor page-ins */
  u_longlong_t hpit;                    /* Time spent in hypervisor page-ins (in nanoseconds) */
  u_longlong_t puser_spurr;             /* number of spurr cycles spent in user mode */
  u_longlong_t psys_spurr;              /* number of spurr cycles spent in kernel mode */
  u_longlong_t pidle_spurr;             /* number of spurr cycles spent in idle mode */
  u_longlong_t pwait_spurr;             /* number of spurr cycles spent in wait mode */
  int spurrflag;                        /* set if running in spurr mode */
  u_longlong_t  version;                /* version number (1, 2, etc.,) */
/*      >>>>> END OF STRUCTURE DEFINITION <<<<<         */
/* #define CURR_VERSION_CPU_TOTAL 1              Incremented by one for every new release *
                                               * of perfstat_cpu_total_t data structure   */
} perfstat_cpu_total_t_71;

typedef struct { /* global cpu information AIX 7.2  / 6.1 TL6 (see oslevel -r) */
  int ncpus;                /* number of active logical processors */
  int ncpus_cfg;             /* number of configured processors */
  char description[IDENTIFIER_LENGTH]; /* processor description (type/official name) */
  u_longlong_t processorHZ; /* processor speed in Hz */
  u_longlong_t user;        /*  raw total number of clock ticks spent in user mode */
  u_longlong_t sys;         /* raw total number of clock ticks spent in system mode */
  u_longlong_t idle;        /* raw total number of clock ticks spent idle */
  u_longlong_t wait;        /* raw total number of clock ticks spent waiting for I/O */
  u_longlong_t pswitch;     /* number of process switches (change in currently running process) */
  u_longlong_t syscall;     /* number of system calls executed */
  u_longlong_t sysread;     /* number of read system calls executed */
  u_longlong_t syswrite;    /* number of write system calls executed */
  u_longlong_t sysfork;     /* number of forks system calls executed */
  u_longlong_t sysexec;     /* number of execs system calls executed */
  u_longlong_t readch;      /* number of characters transferred with read system call */
  u_longlong_t writech;     /* number of characters transferred with write system call */
  u_longlong_t devintrs;    /* number of device interrupts */
  u_longlong_t softintrs;   /* number of software interrupts */
  time_t lbolt;             /* number of ticks since last reboot */
  u_longlong_t loadavg[3];  /* (1<<SBITS) times the average number of runnables processes during the last 1, 5 and 15 minutes.    */
                            /* To calculate the load average, divide the numbers by (1<<SBITS). SBITS is defined in <sys/proc.h>. */
  u_longlong_t runque;      /* length of the run queue (processes ready) */
  u_longlong_t swpque;      /* ength of the swap queue (processes waiting to be paged in) */
  u_longlong_t bread;       /* number of blocks read */
  u_longlong_t bwrite;      /* number of blocks written */
  u_longlong_t lread;       /* number of logical read requests */
  u_longlong_t lwrite;      /* number of logical write requests */
  u_longlong_t phread;      /* number of physical reads (reads on raw devices) */
  u_longlong_t phwrite;     /* number of physical writes (writes on raw devices) */
  u_longlong_t runocc;      /* updated whenever runque is updated, i.e. the runqueue is occupied.
                             * This can be used to compute the simple average of ready processes  */
  u_longlong_t swpocc;      /* updated whenever swpque is updated. i.e. the swpqueue is occupied.
                             * This can be used to compute the simple average processes waiting to be paged in */
  u_longlong_t iget;        /* number of inode lookups */
  u_longlong_t namei;       /* number of vnode lookup from a path name */
  u_longlong_t dirblk;      /* number of 512-byte block reads by the directory search routine to locate an entry for a file */
  u_longlong_t msg;         /* number of IPC message operations */
  u_longlong_t sema;        /* number of IPC semaphore operations */
  u_longlong_t rcvint;      /* number of tty receive interrupts */
  u_longlong_t xmtint;      /* number of tyy transmit interrupts */
  u_longlong_t mdmint;      /* number of modem interrupts */
  u_longlong_t tty_rawinch; /* number of raw input characters  */
  u_longlong_t tty_caninch; /* number of canonical input characters (always zero) */
  u_longlong_t tty_rawoutch;/* number of raw output characters */
  u_longlong_t ksched;      /* number of kernel processes created */
  u_longlong_t koverf;      /* kernel process creation attempts where:
                             * -the user has forked to their maximum limit
                             * -the configuration limit of processes has been reached */
  u_longlong_t kexit;       /* number of kernel processes that became zombies */
  u_longlong_t rbread;      /* number of remote read requests */
  u_longlong_t rcread;      /* number of cached remote reads */
  u_longlong_t rbwrt;       /* number of remote writes */
  u_longlong_t rcwrt;       /* number of cached remote writes */
  u_longlong_t traps;       /* number of traps */
  int ncpus_high;           /* index of highest processor online */
  u_longlong_t puser;       /* raw number of physical processor tics in user mode */
  u_longlong_t psys;        /* raw number of physical processor tics in system mode */
  u_longlong_t pidle;       /* raw number of physical processor tics idle */
  u_longlong_t pwait;       /* raw number of physical processor tics waiting for I/O */
  u_longlong_t decrintrs;   /* number of decrementer tics interrupts */
  u_longlong_t mpcrintrs;   /* number of mpc's received interrupts */
  u_longlong_t mpcsintrs;   /* number of mpc's sent interrupts */
  u_longlong_t phantintrs;  /* number of phantom interrupts */
  u_longlong_t idle_donated_purr; /* number of idle cycles donated by a dedicated partition enabled for donation */
  u_longlong_t idle_donated_spurr;/* number of idle spurr cycles donated by a dedicated partition enabled for donation */
  u_longlong_t busy_donated_purr; /* number of busy cycles donated by a dedicated partition enabled for donation */
  u_longlong_t busy_donated_spurr;/* number of busy spurr cycles donated by a dedicated partition enabled for donation */
  u_longlong_t idle_stolen_purr;  /* number of idle cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t idle_stolen_spurr; /* number of idle spurr cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t busy_stolen_purr;  /* number of busy cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t busy_stolen_spurr; /* number of busy spurr cycles stolen by the hypervisor from a dedicated partition */
  short iowait;             /* number of processes that are asleep waiting for buffered I/O */
  short physio;             /* number of processes waiting for raw I/O */
  longlong_t twait;         /* number of threads that are waiting for filesystem direct(cio) */
  u_longlong_t hpi;         /* number of hypervisor page-ins */
  u_longlong_t hpit;        /* Time spent in hypervisor page-ins (in nanoseconds) */
  u_longlong_t puser_spurr; /* number of spurr cycles spent in user mode */
  u_longlong_t psys_spurr;  /* number of spurr cycles spent in kernel mode */
  u_longlong_t pidle_spurr; /* number of spurr cycles spent in idle mode */
  u_longlong_t pwait_spurr; /* number of spurr cycles spent in wait mode */
  int spurrflag;            /* set if running in spurr mode */
  u_longlong_t  version;    /* version number (1, 2, etc.,) */
  u_longlong_t tb_last;     /*time base counter */
  u_longlong_t purr_coalescing;   /* If the calling partition is
                                   * authorized to see pool wide statistics then
                                   * PURR cycles consumed to coalesce data
                                   * else set to zero.*/
  u_longlong_t spurr_coalescing;  /* If the calling partition is
                                   * authorized to see pool wide statistics then
                                   * SPURR cycles consumed to coalesce data
                                   * else set to zero.*/

/*      >>>>> END OF STRUCTURE DEFINITION <<<<<         */
#define CURR_VERSION_CPU_TOTAL 2 /* Incremented by one for every new release *
                                  * of perfstat_cpu_total_t data structure   */
} perfstat_cpu_total_t_72;

typedef struct { /* component perfstat_cpu_t from AIX 7.2 documentation */
  char name [IDENTIFIER_LENGTH];            /* Logical processor name (processor0, processor1,.). */
  ulong_t state;                            /* Specifies whether the CPU is offline or online.
                                             * (NOTE: The type of 'state' is not specified in the documentation, but
                                             * ulong_t is the correct length) */
  u_longlong_t user;                        /* Raw number of clock ticks spent in user mode. */
  u_longlong_t sys;                         /* Raw number of clock ticks spent in system mode. */
  u_longlong_t idle;                        /* Raw number of clock ticks spent idle. */
  u_longlong_t wait;                        /* Raw number of clock ticks spent waiting for I/O. */
  u_longlong_t pswitch;                     /* Number of context switches (changes of currently running process). */
  u_longlong_t syscall;                     /* Number of system calls executed. */
  u_longlong_t sysread;                     /* Number of read system calls executed. */
  u_longlong_t syswrite;                    /* Number of write system calls executed. */
  u_longlong_t sysfork;                     /* Number of fork system call executed. */
  u_longlong_t sysexec;                     /* Number of exec system call executed. */
  u_longlong_t readch;                      /* Number of characters transferred with read system call. */
  u_longlong_t writech;                     /* Number of characters transferred with write system call. */
  u_longlong_t bread;                       /* Number of block reads. */
  u_longlong_t bwrite;                      /* Number of block writes. */
  u_longlong_t lread;                       /* Number of logical read requests. */
  u_longlong_t lwrite;                      /* Number of logical write requests. */
  u_longlong_t phread;                      /* Number of physical reads (reads on raw device). */
  u_longlong_t phwrite;                     /* Number of physical writes (writes on raw device). */
  u_longlong_t iget;                        /* Number of inode lookups. */
  u_longlong_t namei;                       /* Number of vnode lookup from a path name. */
  u_longlong_t dirblk;                      /* Number of 512-byte blocks reads by the directory search routine to locate an entry for a file. */
  u_longlong_t msg;                         /* Number of interprocess communication (IPC) message operations. */
  u_longlong_t sema;                        /* Number of IPC semaphore operations. */
  u_longlong_t minfaults;                   /* Number of page faults with no I/O. */
  u_longlong_t majfaults;                   /* Number of page faults with disk I/O. */
  u_longlong_t puser;                       /* Raw number of physical processor ticks in user mode. */
  u_longlong_t psys;                        /* Raw number of physical processor ticks in system mode. */
  u_longlong_t pidle;                       /* Raw number of physical processor ticks idle. */
  u_longlong_t pwait;                       /* Raw number of physical processor ticks waiting for I/O. */
  u_longlong_t redisp_sd0;                  /* Number of thread redispatches within the scheduler affinity domain 0. */
  u_longlong_t redisp_sd1;                  /* Number of thread redispatches within the scheduler affinity domain 1. */
  u_longlong_t redisp_sd2;                  /* Number of thread redispatches within the scheduler affinity domain 2. */
  u_longlong_t redisp_sd3;                  /* Number of thread redispatches within the scheduler affinity domain 3. */
  u_longlong_t redisp_sd4;                  /* Number of thread redispatches within the scheduler affinity domain 4. */
  u_longlong_t redisp_sd5;                  /* Number of thread redispatches within the scheduler affinity domain 5. */
  u_longlong_t migration_push;              /* Number of thread migrations from the local runque to another queue due to starvation load balancing. */
  u_longlong_t migration_S3grq;             /* Number of thread migrations from the global runque to the local runque resulting in a move across scheduling domain 3. */
  u_longlong_t migration_S3pull;            /* Number of thread migrations from another processor's runque resulting in a move across scheduling domain 3. */
  u_longlong_t invol_cswitch;               /* Number of involuntary thread context switches. */
  u_longlong_t vol_cswitch;                 /* Number of voluntary thread context switches. */
  u_longlong_t runque;                      /* Number of threads on the runque. */
  u_longlong_t bound;                       /* Number of bound threads. */
  u_longlong_t decrintrs;                   /* Number of decrementer interrupts. */
  u_longlong_t mpcrintrs;                   /* Number of received interrupts for MPC. */
  u_longlong_t mpcsintrs;                   /* Number of sent interrupts for MPC. */
  u_longlong_t devintrs;                    /* Number of device interrupts. */
  u_longlong_t softintrs;                   /* Number of offlevel handlers called. */
  u_longlong_t phantintrs;                  /* Number of phantom interrupts. */
  u_longlong_t idle_donated_purr;           /* Number of idle cycles donated by a dedicated partition enabled for donation. */
  u_longlong_t idle_donated_spurr;          /* Number of idle spurr cycles donated by a dedicated partition enabled for donation. */
  u_longlong_t busy_donated_purr;           /* Number of busy cycles donated by a dedicated partition enabled for donation. */
  u_longlong_t busy_donated_spurr;          /* Number of busy spurr cycles donated by a dedicated partition enabled for donation. */
  u_longlong_t idle_stolen_purr;            /* Number of idle cycles stolen by the hypervisor from a dedicated partition. */
  u_longlong_t idle_stolen_spurr;           /* Number of idle spurr cycles stolen by the hypervisor from a dedicated partition. */
  u_longlong_t busy_stolen_purr;            /* Number of busy cycles stolen by the hypervisor from a dedicated partition. */
  u_longlong_t busy_stolen_spurr;           /* Number of busy spurr cycles stolen by the hypervisor from a dedicated partition.*/
  u_longlong_t shcpus_in_sys;               /* Number of physical processors allocated for shared processor use, across all shared processors pools. */
  u_longlong_t entitled_pool_capacity;      /* Entitled processor capacity of partition’s pool. */
  u_longlong_t pool_max_time;               /* Summation of maximum time that can be consumed by the pool (nanoseconds). */
  u_longlong_t pool_busy_time;              /* Summation of busy (nonidle) time accumulated across all partitions in the pool (nanoseconds). */
  u_longlong_t pool_scaled_busy_time;       /* Scaled summation of busy (nonidle) time accumulated across all partitions in the pool (nanoseconds). */
  u_longlong_t shcpu_tot_time;              /* Summation of total time across all physical processors allocated for shared processor use (nanoseconds). */
  u_longlong_t shcpu_busy_time;             /* Summation of busy (nonidle) time accumulated across all shared processor partitions (nanoseconds). */
  u_longlong_t shcpu_scaled_busy_time;      /* Scaled summation of busy time accumulated across all shared processor partitions (nanoseconds). */
  int ams_pool_id;                          /* AMS pool ID of the pool the LPAR belongs to. */
  int var_mem_weight;                       /* Variable memory capacity weight. */
  u_longlong_t iome;                        /* I/O memory entitlement of the partition in bytes. */
  u_longlong_t pmem;                        /* Physical memory currently backing the partition's logical memory in bytes. */
  u_longlong_t hpi;                         /* Number of hypervisor page-ins. */
  u_longlong_t hpit;                        /* Time spent in hypervisor page-ins (in nanoseconds). */
  u_longlong_t hypv_pagesize;               /* Hypervisor page size in KB. */
  uint online_lcpus;                        /* Number of online logical processors. */
  uint smt_thrds;                           /* Number of SMT threads. */
} perfstat_cpu_t;

typedef struct {
  char name[IDENTIFIER_LENGTH];             /* Name of the interface. */
  char description[IDENTIFIER_LENGTH];      /* Interface description (from ODM, similar to lscfg output). */
  uchar type;                               /* Ethernet, token ring, and so on. Interpretation can be done using the /usr/include/net/if_types.h file. */
  u_longlong_t mtu;                         /* Network frame size. */
  u_longlong_t ipacets;                     /* Number of packets received on interface. */
  u_longlong_t ibytes;                      /* Number of bytes received on interface. */
  u_longlong_t ierrors;                     /* Number of input errors on interface. */
  u_longlong_t opackets;                    /* Number of packets sent on interface. */
  u_longlong_t obytes;                      /* Number of bytes sent on interface. */
  u_longlong_t oerrors;                     /* Number of output errors on interface. */
  u_longlong_t collisions;                  /* Number of collisions on csma interface. */
  u_longlong_t bitrate;                     /* Adapter rating in bit per second. */
  u_longlong_t if_iqdrops;                  /* Dropped on input, this interface. */
  u_longlong_t if_arpdrops;                 /* Dropped because no arp response. */
} perfstat_netinterface_t;

typedef union {
  uint    w;
  struct {
          unsigned smt_capable :1;          /* OS supports SMT mode */
          unsigned smt_enabled :1;          /* SMT mode is on */
          unsigned lpar_capable :1;         /* OS supports logical partitioning */
          unsigned lpar_enabled :1;         /* logical partitioning is on */
          unsigned shared_capable :1;       /* OS supports shared processor LPAR */
          unsigned shared_enabled :1;       /* partition runs in shared mode */
          unsigned dlpar_capable :1;        /* OS supports dynamic LPAR */
          unsigned capped :1;               /* partition is capped */
          unsigned kernel_is_64 :1;         /* kernel is 64 bit */
          unsigned pool_util_authority :1;  /* pool utilization available */
          unsigned donate_capable :1;       /* capable of donating cycles */
          unsigned donate_enabled :1;       /* enabled for donating cycles */
          unsigned ams_capable:1;           /* 1 = AMS(Active Memory Sharing) capable, 0 = Not AMS capable */
          unsigned ams_enabled:1;           /* 1 = AMS(Active Memory Sharing) enabled, 0 = Not AMS enabled */
          unsigned power_save:1;            /* 1 = Power saving mode is enabled */
          unsigned ame_enabled:1;           /* Active Memory Expansion is enabled */
          unsigned shared_extended :1;
          unsigned spare :15;               /* reserved for future usage */
  } b;
} perfstat_partition_type_t;

typedef struct { /* partition total information AIX 7.1 */
  char name[IDENTIFIER_LENGTH];         /* name of the logical partition */
  perfstat_partition_type_t type;       /* set of bits describing the partition */
  int lpar_id;                          /* logical partition identifier */
  int group_id;                         /* identifier of the LPAR group this partition is a member of */
  int pool_id;                          /* identifier of the shared pool of physical processors this partition is a member of */
  int online_cpus;                      /* number of virtual CPUs currently online on the partition */
  int max_cpus;                         /* maximum number of virtual CPUs this partition can ever have */
  int min_cpus;                         /* minimum number of virtual CPUs this partition must have */
  u_longlong_t online_memory;           /* amount of memory currently online */
  u_longlong_t max_memory;              /* maximum amount of memory this partition can ever have */
  u_longlong_t min_memory;              /* minimum amount of memory this partition must have */
  int entitled_proc_capacity;           /* number of processor units this partition is entitled to receive */
  int max_proc_capacity;                /* maximum number of processor units this partition can ever have */
  int min_proc_capacity;                /* minimum number of processor units this partition must have */
  int proc_capacity_increment;          /* increment value to the entitled capacity */
  int unalloc_proc_capacity;            /* number of processor units currently unallocated in the shared processor pool this partition belongs to */
  int var_proc_capacity_weight;         /* partition priority weight to receive extra capacity */
  int unalloc_var_proc_capacity_weight; /* number of variable processor capacity weight units currently unallocated  in the shared processor pool this partition belongs to */
  int online_phys_cpus_sys;             /* number of physical CPUs currently active in the system containing this partition */
  int max_phys_cpus_sys;                /* maximum possible number of physical CPUs in the system containing this partition */
  int phys_cpus_pool;                   /* number of the physical CPUs currently in the shared processor pool this partition belong to */
  u_longlong_t puser;                   /* raw number of physical processor tics in user mode */
  u_longlong_t psys;                    /* raw number of physical processor tics in system mode */
  u_longlong_t pidle;                   /* raw number of physical processor tics idle */
  u_longlong_t pwait;                   /* raw number of physical processor tics waiting for I/O */
  u_longlong_t pool_idle_time;          /* number of clock tics a processor in the shared pool was idle */
  u_longlong_t phantintrs;              /* number of phantom interrupts received by the partition */
  u_longlong_t invol_virt_cswitch;      /* number involuntary virtual CPU context switches */
  u_longlong_t vol_virt_cswitch;        /* number voluntary virtual CPU context switches */
  u_longlong_t timebase_last;           /* most recently cpu time base */
  u_longlong_t reserved_pages;          /* Currently number of 16GB pages. Cannot participate in DR operations */
  u_longlong_t reserved_pagesize;       /* Currently 16GB pagesize Cannot participate in DR operations */
  u_longlong_t idle_donated_purr;       /* number of idle cycles donated by a dedicated partition enabled for donation */
  u_longlong_t idle_donated_spurr;      /* number of idle spurr cycles donated by a dedicated partition enabled for donation */
  u_longlong_t busy_donated_purr;       /* number of busy cycles donated by a dedicated partition enabled for donation */
  u_longlong_t busy_donated_spurr;      /* number of busy spurr cycles donated by a dedicated partition enabled for donation */
  u_longlong_t idle_stolen_purr;        /* number of idle cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t idle_stolen_spurr;       /* number of idle spurr cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t busy_stolen_purr;        /* number of busy cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t busy_stolen_spurr;       /* number of busy spurr cycles stolen by the hypervisor from a dedicated partition */
  u_longlong_t shcpus_in_sys;           /* Number of physical processors allocated for shared processor use */
  u_longlong_t max_pool_capacity;       /* Maximum processor capacity of partitions pool */
  u_longlong_t entitled_pool_capacity;  /* Entitled processor capacity of partitions pool */
  u_longlong_t pool_max_time;           /* Summation of maximum time that could be consumed by the pool (nano seconds) */
  u_longlong_t pool_busy_time;          /* Summation of busy (non-idle) time accumulated across all partitions in the pool (nano seconds) */
  u_longlong_t pool_scaled_busy_time;   /* Scaled summation of busy (non-idle) time accumulated across all partitions in the pool (nano seconds) */
  u_longlong_t shcpu_tot_time;          /* Summation of total time across all physical processors allocated for shared processor use (nano seconds) */
  u_longlong_t shcpu_busy_time;         /* Summation of busy (non-idle) time accumulated across all shared processor partitions (nano seconds) */
  u_longlong_t shcpu_scaled_busy_time;  /* Scaled summation of busy time accumulated across all shared processor partitions (nano seconds) */
  int ams_pool_id;                      /* AMS pool id of the pool the LPAR belongs to */
  int var_mem_weight;                   /* variable memory capacity weight */
  u_longlong_t iome;                    /* I/O memory entitlement of the partition in bytes*/
  u_longlong_t pmem;                    /* Physical memory currently backing the partition's logical memory in bytes*/
  u_longlong_t hpi;                     /* number of hypervisor page-ins */
  u_longlong_t hpit;                    /* Time spent in hypervisor page-ins (in nanoseconds)*/
  u_longlong_t hypv_pagesize;           /* Hypervisor page size in KB*/
  uint online_lcpus;                    /* number of online logical cpus */
  uint smt_thrds;                       /* number of hardware threads that are running */
  u_longlong_t puser_spurr;             /* number of spurr cycles spent in user mode */
  u_longlong_t psys_spurr;              /* number of spurr cycles spent in kernel mode */
  u_longlong_t pidle_spurr;             /* number of spurr cycles spent in idle mode */
  u_longlong_t pwait_spurr;             /* number of spurr cycles spent in wait mode */
  int spurrflag;                        /* set if running in spurr mode */
  char hardwareid[CEC_ID_LEN];          /* CEC Identifier */
        uint power_save_mode;                 /* Power save mode for the LPAR. Introduced through LI 53K PRF : Feature 728 292*/
        ushort ame_version;                   /* AME Version */
        u_longlong_t true_memory;             /* True Memory Size in 4KB pages */
        u_longlong_t expanded_memory;         /* Expanded Memory Size in 4KB pages */
        u_longlong_t target_memexp_factr;     /* Target Memory Expansion Factor scaled by 100 */
        u_longlong_t current_memexp_factr;    /* Current Memory Expansion Factor scaled by 100 */
        u_longlong_t target_cpool_size;       /* Target Compressed Pool Size in bytes */
        u_longlong_t max_cpool_size;          /* Max Size of Compressed Pool in bytes */
        u_longlong_t min_ucpool_size;         /* Min Size of Uncompressed Pool in bytes */
        u_longlong_t ame_deficit_size;        /*Deficit memory size in bytes */
        u_longlong_t version;                 /* version number (1, 2, etc.,) */
        u_longlong_t cmcs_total_time;         /* Total CPU time spent due to active memory expansion */
} perfstat_partition_total_t_71;

typedef struct { /* partition total information AIX 7.1 >= TL1*/
        char name[IDENTIFIER_LENGTH];         /* name of the logical partition */
        perfstat_partition_type_t type;       /* set of bits describing the partition */
        int lpar_id;                          /* logical partition identifier */
        int group_id;                         /* identifier of the LPAR group this partition is a member of */
        int pool_id;                          /* identifier of the shared pool of physical processors this partition is a member of */
        int online_cpus;                      /* number of virtual CPUs currently online on the partition */
        int max_cpus;                         /* maximum number of virtual CPUs this partition can ever have */
        int min_cpus;                         /* minimum number of virtual CPUs this partition must have */
        u_longlong_t online_memory;           /* amount of memory currently online */
        u_longlong_t max_memory;              /* maximum amount of memory this partition can ever have */
        u_longlong_t min_memory;              /* minimum amount of memory this partition must have */
        int entitled_proc_capacity;           /* number of processor units this partition is entitled to receive */
        int max_proc_capacity;                /* maximum number of processor units this partition can ever have */
        int min_proc_capacity;                /* minimum number of processor units this partition must have */
        int proc_capacity_increment;          /* increment value to the entitled capacity */
        int unalloc_proc_capacity;            /* number of processor units currently unallocated in the shared processor pool this partition belongs to */
        int var_proc_capacity_weight;         /* partition priority weight to receive extra capacity */
        int unalloc_var_proc_capacity_weight; /* number of variable processor capacity weight units currently unallocated  in the shared processor pool this partition belongs to */
        int online_phys_cpus_sys;             /* number of physical CPUs currently active in the system containing this partition */
        int max_phys_cpus_sys;                /* maximum possible number of physical CPUs in the system containing this partition */
        int phys_cpus_pool;                   /* number of the physical CPUs currently in the shared processor pool this partition belong to */
        u_longlong_t puser;                   /* raw number of physical processor tics in user mode */
        u_longlong_t psys;                    /* raw number of physical processor tics in system mode */
        u_longlong_t pidle;                   /* raw number of physical processor tics idle */
        u_longlong_t pwait;                   /* raw number of physical processor tics waiting for I/O */
        u_longlong_t pool_idle_time;          /* number of clock tics a processor in the shared pool was idle */
        u_longlong_t phantintrs;              /* number of phantom interrupts received by the partition */
        u_longlong_t invol_virt_cswitch;      /* number involuntary virtual CPU context switches */
        u_longlong_t vol_virt_cswitch;        /* number voluntary virtual CPU context switches */
        u_longlong_t timebase_last;           /* most recently cpu time base */
        u_longlong_t reserved_pages;          /* Currently number of 16GB pages. Cannot participate in DR operations */
        u_longlong_t reserved_pagesize;       /* Currently 16GB pagesize Cannot participate in DR operations */
        u_longlong_t idle_donated_purr;       /* number of idle cycles donated by a dedicated partition enabled for donation */
        u_longlong_t idle_donated_spurr;      /* number of idle spurr cycles donated by a dedicated partition enabled for donation */
        u_longlong_t busy_donated_purr;       /* number of busy cycles donated by a dedicated partition enabled for donation */
        u_longlong_t busy_donated_spurr;      /* number of busy spurr cycles donated by a dedicated partition enabled for donation */
        u_longlong_t idle_stolen_purr;        /* number of idle cycles stolen by the hypervisor from a dedicated partition */
        u_longlong_t idle_stolen_spurr;       /* number of idle spurr cycles stolen by the hypervisor from a dedicated partition */
        u_longlong_t busy_stolen_purr;        /* number of busy cycles stolen by the hypervisor from a dedicated partition */
        u_longlong_t busy_stolen_spurr;       /* number of busy spurr cycles stolen by the hypervisor from a dedicated partition */
        u_longlong_t shcpus_in_sys;           /* Number of physical processors allocated for shared processor use */
        u_longlong_t max_pool_capacity;       /* Maximum processor capacity of partitions pool */
        u_longlong_t entitled_pool_capacity;  /* Entitled processor capacity of partitions pool */
        u_longlong_t pool_max_time;           /* Summation of maximum time that could be consumed by the pool (nano seconds) */
        u_longlong_t pool_busy_time;          /* Summation of busy (non-idle) time accumulated across all partitions in the pool (nano seconds) */
        u_longlong_t pool_scaled_busy_time;   /* Scaled summation of busy (non-idle) time accumulated across all partitions in the pool (nano seconds) */
        u_longlong_t shcpu_tot_time;          /* Summation of total time across all physical processors allocated for shared processor use (nano seconds) */
        u_longlong_t shcpu_busy_time;         /* Summation of busy (non-idle) time accumulated across all shared processor partitions (nano seconds) */
        u_longlong_t shcpu_scaled_busy_time;  /* Scaled summation of busy time accumulated across all shared processor partitions (nano seconds) */
        int ams_pool_id;                      /* AMS pool id of the pool the LPAR belongs to */
        int var_mem_weight;                   /* variable memory capacity weight */
        u_longlong_t iome;                    /* I/O memory entitlement of the partition in bytes*/
        u_longlong_t pmem;                    /* Physical memory currently backing the partition's logical memory in bytes*/
        u_longlong_t hpi;                     /* number of hypervisor page-ins */
        u_longlong_t hpit;                    /* Time spent in hypervisor page-ins (in nanoseconds)*/
        u_longlong_t hypv_pagesize;           /* Hypervisor page size in KB*/
        uint online_lcpus;                    /* number of online logical cpus */
        uint smt_thrds;                       /* number of hardware threads that are running */
        u_longlong_t puser_spurr;             /* number of spurr cycles spent in user mode */
        u_longlong_t psys_spurr;              /* number of spurr cycles spent in kernel mode */
        u_longlong_t pidle_spurr;             /* number of spurr cycles spent in idle mode */
        u_longlong_t pwait_spurr;             /* number of spurr cycles spent in wait mode */
        int spurrflag;                        /* set if running in spurr mode */
        char hardwareid[CEC_ID_LEN];          /* CEC Identifier */
        uint power_save_mode;                 /* Power save mode for the LPAR. Introduced through LI 53K PRF : Feature 728 292*/
        ushort ame_version;                   /* AME Version */
        u_longlong_t true_memory;             /* True Memory Size in 4KB pages */
        u_longlong_t expanded_memory;         /* Expanded Memory Size in 4KB pages */
        u_longlong_t target_memexp_factr;     /* Target Memory Expansion Factor scaled by 100 */
        u_longlong_t current_memexp_factr;    /* Current Memory Expansion Factor scaled by 100 */
        u_longlong_t target_cpool_size;       /* Target Compressed Pool Size in bytes */
        u_longlong_t max_cpool_size;          /* Max Size of Compressed Pool in bytes */
        u_longlong_t min_ucpool_size;         /* Min Size of Uncompressed Pool in bytes */
        u_longlong_t ame_deficit_size;        /*Deficit memory size in bytes */
        u_longlong_t version;                 /* version number (1, 2, etc.,) */
        u_longlong_t cmcs_total_time;         /* Total CPU time spent due to active memory expansion */
        u_longlong_t purr_coalescing;         /* If the calling partition is authorized to see pool wide statistics then PURR cycles consumed to coalesce data else set to zero.*/
        u_longlong_t spurr_coalescing;        /* If the calling partition is authorized to see pool wide statistics then SPURR cycles consumed to coalesce data else set to zero.*/
        u_longlong_t MemPoolSize;             /* Indicates the memory pool size of the pool that the partition belongs to (in bytes)., mpsz */
        u_longlong_t IOMemEntInUse;           /* I/O memory entitlement of the LPAR in use in bytes. iomu */
        u_longlong_t IOMemEntFree;            /* free I/O memory entitlement in bytes.  iomf */
        u_longlong_t IOHighWaterMark;         /* high water mark of I/O memory entitlement usage in bytes. iohwn */
        u_longlong_t purr_counter;            /* number of purr cycles spent in user + kernel mode */
        u_longlong_t spurr_counter;           /* number of spurr cycles spent in user + kernel mode */

        /* Marketing Requirement(MR): MR1124083744  */
        u_longlong_t real_free;               /* free real memory (in 4KB pages) */
        u_longlong_t real_avail;              /* number of pages available for user application (memfree + numperm - minperm - minfree) */
        /*      >>>>> END OF STRUCTURE DEFINITION <<<<<         */
#define CURR_VERSION_PARTITION_TOTAL 5        /* Incremented by one for every new release     *
                                               * of perfstat_partition_total_t data structure */
} perfstat_partition_total_t_71_1;

typedef struct {
        u_longlong_t version;                 /* Version number of the data structure. */
        u_longlong_t pid;                     /* Process ID. */
        char proc_name[64];                   /* Name of the process. */
        int proc_priority;                    /* Process priority. */
        u_longlong_t num_threads;             /* Thread count. */
        u_longlong_t proc_uid;                /* Owner information. */
        u_longlong_t proc_classid;            /* WLM class name. */
        u_longlong_t proc_size;               /* Virtual size of the process (exclusive usage, leaving all shared library text & shared file pages, shared memory, and memory mapped). */
        u_longlong_t proc_real_mem_data;      /* Real memory used for data in KB. */
        u_longlong_t proc_real_mem_text;      /* Real memory used for text in KB. */
        u_longlong_t proc_virt_mem_data;      /* Virtual memory used for data in KB. */
        u_longlong_t proc_virt_mem_text;      /* Virtual memory used for text in KB. */
        u_longlong_t shared_lib_data_size;    /* Data size from shared library in KB. */
        u_longlong_t heap_size;               /* Heap size in KB. */
        u_longlong_t real_inuse;              /* The Real memory (in KB) in use by the process including all kind of segments (excluding system segments). This includes text, data, shared library text, shared library data, file pages, shared memory, and memory mapped. */
        u_longlong_t virt_inuse;              /* The virtual memory (in KB) in use by the process including all kind of segments (excluding system segments). This includes text, data, shared library text, shared library data, file pages, shared memory, and memory mapped. */
        u_longlong_t pinned;                  /* Pinned memory (in KB) for this process inclusive of all segments. */
        u_longlong_t pgsp_inuse;              /* Paging space used (in KB) inclusive of all segments. */
        u_longlong_t filepages;               /* File pages used (in KB) including shared pages. */
        u_longlong_t real_inuse_map;          /* Real memory used (in KB) for shared memory and memory mapped regions */
        u_longlong_t virt_inuse_map;          /* Virtual memory used (in KB) for shared memory and memory mapped regions. */
        u_longlong_t pinned_inuse_map;        /* Pinned memory used (in KB) for shared memory and memory mapped regions. */
        double ucpu_time;                     /* User mode CPU time is in percentage or milliseconds, which is based on, whether it is filled by perfstat_process_util or perfstat_process respectively. */
        double scpu_time;                     /* System mode CPU time is in percentage or milliseconds, which is based on whether it is filled by perfstat_process_util or perfstat_process respectively. */
        u_longlong_t last_timebase;           /* Timebase counter. */
        u_longlong_t inBytes;                 /* Bytes written to disk. */
        u_longlong_t outBytes;                /* Bytes read from disk. */
        u_longlong_t inOps;                   /* In operations from disk. */
        u_longlong_t outOps;                  /* Out operations from disk */
} perfstat_process_t;

typedef union { /* WPAR Type & Flags */
        uint    w;
        struct {
                unsigned app_wpar :1;        /* Application WPAR */
                unsigned cpu_rset :1;        /* WPAR restricted to CPU resource set */
                unsigned cpu_xrset:1;        /* WPAR restricted to CPU Exclusive resource set */
                unsigned cpu_limits :1;      /* CPU resource limits enforced */
                unsigned mem_limits :1;      /* Memory resource limits enforced */
                unsigned spare :27;          /* reserved for future usage */
        } b;
} perfstat_wpar_type_t;

typedef struct { /* Workload partition Information AIX 7.1*/
       char name[MAXCORRALNAMELEN+1]; /* name of the Workload Partition */
       perfstat_wpar_type_t type;     /* set of bits describing the wpar */
       cid_t wpar_id;                 /* workload partition identifier */
       uint  online_cpus;             /* Number of Virtual CPUs in partition rset or  number of virtual CPUs currently online on the Global partition*/
       int   cpu_limit;               /* CPU limit in 100ths of % - 1..10000 */
       int   mem_limit;               /* Memory limit in 100ths of % - 1..10000 */
       u_longlong_t online_memory;    /* amount of memory currently online in Global Partition */
       int entitled_proc_capacity;    /* number of processor units this partition is entitled to receive */
       u_longlong_t version;          /* version number (1, 2, etc.,)                  */
/*      >>>>> END OF STRUCTURE DEFINITION <<<<<         */
#define CURR_VERSION_WPAR_TOTAL 1     /* Incremented by one for every new release      *
                                       * of perfstat_wpar_total_t data structure       */
} perfstat_wpar_total_t_71;

typedef void * rsethandle_t;  /* Type to identify a resource set handle: rsethandle_t */

typedef enum { WPARNAME, WPARID, RSETHANDLE } wparid_specifier; /* Type of wparid_specifier */

typedef struct { /* WPAR identifier */
        wparid_specifier spec;  /* Specifier to choose wpar id or name */
        union  {
                cid_t wpar_id;                      /* WPAR ID */
                rsethandle_t rset;                  /* Rset Handle */
                char wparname[MAXCORRALNAMELEN+1];  /* WPAR NAME */
        } u;
        char name[IDENTIFIER_LENGTH]; /* name of the structure element identifier */
} perfstat_id_wpar_t;



// end: libperfstat.h (AIX 7.1)
//////////////////////////////////////////////////////////////////////////////////////////////////////////////

#define PERFSTAT_PARTITON_TOTAL_T_LATEST perfstat_partition_total_t_71_1 /* latest perfstat_partition_total_t structure */
#define PERFSTAT_PROCESS_T_LATEST perfstat_process_t                     /* latest perfstat_process_t structure */
#define PERFSTAT_CPU_TOTAL_T_LATEST perfstat_cpu_total_t_72              /* latest perfstat_cpu_total_t structure */
#define PERFSTAT_CPU_T_LATEST perfstat_cpu_t                             /* latest perfstat_cpu_t structure */
#define PERFSTAT_NETINTERFACE_T_LATEST perfstat_netinterface_t           /* latest perfstat_netinterface_t structure */
#define PERFSTAT_WPAR_TOTAL_T_LATEST perfstat_wpar_total_t_71            /* latest perfstat_wpar_total_t structure */

typedef PERFSTAT_CPU_TOTAL_T_LATEST perfstat_cpu_total_t;

class libperfstat {

public:

  // Load the libperfstat library (must be in LIBPATH).
  // Returns true if succeeded, false if error.
  static bool init();
  static void cleanup();

  // Direct wrappers for the libperfstat functionality. All they do is
  // to call the functions with the same name via function pointers.
  // Get all available data also on newer AIX versions (PERFSTAT_CPU_TOTAL_T_LATEST).
  static int perfstat_cpu_total(perfstat_id_t *name, PERFSTAT_CPU_TOTAL_T_LATEST* userbuff,
                                int sizeof_userbuff, int desired_number);

  static int perfstat_memory_total(perfstat_id_t *name, perfstat_memory_total_t* userbuff,
                                   int sizeof_userbuff, int desired_number);

  static int perfstat_partition_total(perfstat_id_t *name, PERFSTAT_PARTITON_TOTAL_T_LATEST* userbuff,
                                      int sizeof_userbuff, int desired_number);

  static void perfstat_reset();

  static int perfstat_wpar_total(perfstat_id_wpar_t *name, PERFSTAT_WPAR_TOTAL_T_LATEST* userbuff,
                                 int sizeof_userbuff, int desired_number);

  static cid_t wpar_getcid();

  static int perfstat_cpu(perfstat_id_t *name, PERFSTAT_CPU_T_LATEST* userbuff,
                          int sizeof_userbuff, int desired_number);

  static int perfstat_process(perfstat_id_t *name, PERFSTAT_PROCESS_T_LATEST* userbuff,
                              int sizeof_userbuff, int desired_number);

  static int perfstat_netinterface(perfstat_id_t *name, PERFSTAT_NETINTERFACE_T_LATEST* userbuff,
                                   int sizeof_userbuff, int desired_number);

  ////////////////////////////////////////////////////////////////
  // The convenience functions get_partitioninfo(), get_cpuinfo(), get_wparinfo() return
  // information about partition, cpu and wpars, respectively. They can be used without
  // regard for which OS release we are on. On older AIX release, some output structure
  // members will be 0.

  // Result struct for get_partitioninfo().
  struct partitioninfo_t {
    // partition type info
    unsigned smt_capable :1;          /* OS supports SMT mode */
    unsigned smt_enabled :1;          /* SMT mode is on */
    unsigned lpar_capable :1;         /* OS supports logical partitioning */
    unsigned lpar_enabled :1;         /* logical partitioning is on */
    unsigned shared_capable :1;       /* OS supports shared processor LPAR */
    unsigned shared_enabled :1;       /* partition runs in shared mode */
    unsigned dlpar_capable :1;        /* OS supports dynamic LPAR */
    unsigned capped :1;               /* partition is capped */
    unsigned kernel_is_64 :1;         /* kernel is 64 bit */
    unsigned pool_util_authority :1;  /* pool utilization available */
    unsigned donate_capable :1;       /* capable of donating cycles */
    unsigned donate_enabled :1;       /* enabled for donating cycles */
    unsigned ams_capable:1;           /* 1 = AMS(Active Memory Sharing) capable, 0 = Not AMS capable */
    unsigned ams_enabled:1;           /* 1 = AMS(Active Memory Sharing) enabled, 0 = Not AMS enabled */
    unsigned power_save:1;            /* 1 = Power saving mode is enabled */
    unsigned ame_enabled:1;           /* Active Memory Expansion is enabled */
    // partition total info
    int online_cpus;                  /* number of virtual CPUs currently online on the partition */
    int entitled_proc_capacity;       /* number of processor units this partition is entitled to receive */
    int var_proc_capacity_weight;     /* partition priority weight to receive extra capacity */
    int phys_cpus_pool;               /* number of the physical CPUs currently in the shared processor pool this partition belong to */
    int pool_id;                      /* identifier of the shared pool of physical processors this partition is a member of */
    u_longlong_t entitled_pool_capacity;  /* Entitled processor capacity of partitions pool */
    char name[IDENTIFIER_LENGTH];     /* name of the logical partition */

    u_longlong_t timebase_last;       /* most recently cpu time base (an incremented long int on PowerPC) */
    u_longlong_t pool_idle_time;      /* pool idle time = number of clock tics a processor in the shared pool was idle */
    u_longlong_t pcpu_tics_user;      /* raw number of physical processor tics in user mode */
    u_longlong_t pcpu_tics_sys;       /* raw number of physical processor tics in system mode */
    u_longlong_t pcpu_tics_idle;      /* raw number of physical processor tics idle */
    u_longlong_t pcpu_tics_wait;      /* raw number of physical processor tics waiting for I/O */

    u_longlong_t true_memory;          /* True Memory Size in 4KB pages */
    u_longlong_t expanded_memory;      /* Expanded Memory Size in 4KB pages */
    u_longlong_t target_memexp_factr;  /* Target Memory Expansion Factor scaled by 100 */
    u_longlong_t current_memexp_factr; /* Current Memory Expansion Factor scaled by 100 */
    u_longlong_t cmcs_total_time;      /* Total CPU time spent due to active memory expansion */
  };

  // Result struct for get_cpuinfo().
  struct cpuinfo_t {
    char description[IDENTIFIER_LENGTH];  // processor description (type/official name)
    u_longlong_t processorHZ;             // processor speed in Hz
    int ncpus;                            // number of active logical processors
    double loadavg[3];                    // (1<<SBITS) times the average number of runnables processes during the last 1, 5 and 15 minutes.
                                          // To calculate the load average, divide the numbers by (1<<SBITS). SBITS is defined in <sys/proc.h>.
    unsigned long long user_clock_ticks;  // raw total number of clock ticks spent in user mode
    unsigned long long sys_clock_ticks;   // raw total number of clock ticks spent in system mode
    unsigned long long idle_clock_ticks;  // raw total number of clock ticks spent idle
    unsigned long long wait_clock_ticks;  // raw total number of clock ticks spent waiting for I/O
  };

  // Result struct for get_wparinfo().
  struct wparinfo_t {
    char name[MAXCORRALNAMELEN+1];  /* name of the Workload Partition */
    unsigned short wpar_id;         /* workload partition identifier */
    unsigned app_wpar :1;           /* Application WPAR */
    unsigned cpu_rset :1;           /* WPAR restricted to CPU resource set */
    unsigned cpu_xrset:1;           /* WPAR restricted to CPU Exclusive resource set */
    unsigned cpu_limits :1;         /* CPU resource limits enforced */
    unsigned mem_limits :1;         /* Memory resource limits enforced */
    int cpu_limit;                  /* CPU limit in 100ths of % - 1..10000 */
    int mem_limit;                  /* Memory limit in 100ths of % - 1..10000 */
  };

  static bool get_partitioninfo(partitioninfo_t* ppi);
  static bool get_cpuinfo(cpuinfo_t* pci);
  static bool get_wparinfo(wparinfo_t* pwi);
};

#endif // OS_AIX_LIBPERFSTAT_AIX_HPP
