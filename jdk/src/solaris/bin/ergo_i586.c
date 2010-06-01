/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

#include "ergo.h"

static unsigned long physical_processors(void);

#ifdef __solaris__

/*
 * A utility method for asking the CPU about itself.
 * There's a corresponding version of linux-i586
 * because the compilers are different.
 */
static void
get_cpuid(uint32_t arg,
          uint32_t* eaxp,
          uint32_t* ebxp,
          uint32_t* ecxp,
          uint32_t* edxp) {
#ifdef _LP64
  asm(
  /* rbx is a callee-saved register */
      " movq    %rbx, %r11  \n"
  /* rdx and rcx are 3rd and 4th argument registers */
      " movq    %rdx, %r10  \n"
      " movq    %rcx, %r9   \n"
      " movl    %edi, %eax  \n"
      " cpuid               \n"
      " movl    %eax, (%rsi)\n"
      " movl    %ebx, (%r10)\n"
      " movl    %ecx, (%r9) \n"
      " movl    %edx, (%r8) \n"
  /* Restore rbx */
      " movq    %r11, %rbx");
#else
  /* EBX is a callee-saved register */
  asm(" pushl   %ebx");
  /* Need ESI for storing through arguments */
  asm(" pushl   %esi");
  asm(" movl    8(%ebp), %eax   \n"
      " cpuid                   \n"
      " movl    12(%ebp), %esi  \n"
      " movl    %eax, (%esi)    \n"
      " movl    16(%ebp), %esi  \n"
      " movl    %ebx, (%esi)    \n"
      " movl    20(%ebp), %esi  \n"
      " movl    %ecx, (%esi)    \n"
      " movl    24(%ebp), %esi  \n"
      " movl    %edx, (%esi)      ");
  /* Restore ESI and EBX */
  asm(" popl    %esi");
  /* Restore EBX */
  asm(" popl    %ebx");
#endif /* LP64 */
}

/* The definition of a server-class machine for solaris-i586/amd64 */
jboolean
ServerClassMachineImpl(void) {
  jboolean            result            = JNI_FALSE;
  /* How big is a server class machine? */
  const unsigned long server_processors = 2UL;
  const uint64_t      server_memory     = 2UL * GB;
  /*
   * We seem not to get our full complement of memory.
   *     We allow some part (1/8?) of the memory to be "missing",
   *     based on the sizes of DIMMs, and maybe graphics cards.
   */
  const uint64_t      missing_memory    = 256UL * MB;
  const uint64_t      actual_memory     = physical_memory();

  /* Is this a server class machine? */
  if (actual_memory >= (server_memory - missing_memory)) {
    const unsigned long actual_processors = physical_processors();
    if (actual_processors >= server_processors) {
      result = JNI_TRUE;
    }
  }
  JLI_TraceLauncher("solaris_" LIBARCHNAME "_ServerClassMachine: %s\n",
           (result == JNI_TRUE ? "true" : "false"));
  return result;
}

#endif /* __solaris__ */

#ifdef __linux__

/*
 * A utility method for asking the CPU about itself.
 * There's a corresponding version of solaris-i586
 * because the compilers are different.
 */
static void
get_cpuid(uint32_t arg,
          uint32_t* eaxp,
          uint32_t* ebxp,
          uint32_t* ecxp,
          uint32_t* edxp) {
#ifdef _LP64
  __asm__ volatile (/* Instructions */
                    "   movl    %4, %%eax  \n"
                    "   cpuid              \n"
                    "   movl    %%eax, (%0)\n"
                    "   movl    %%ebx, (%1)\n"
                    "   movl    %%ecx, (%2)\n"
                    "   movl    %%edx, (%3)\n"
                    : /* Outputs */
                    : /* Inputs */
                    "r" (eaxp),
                    "r" (ebxp),
                    "r" (ecxp),
                    "r" (edxp),
                    "r" (arg)
                    : /* Clobbers */
                    "%rax", "%rbx", "%rcx", "%rdx", "memory"
                    );
#else /* _LP64 */
  uint32_t value_of_eax = 0;
  uint32_t value_of_ebx = 0;
  uint32_t value_of_ecx = 0;
  uint32_t value_of_edx = 0;
  __asm__ volatile (/* Instructions */
                        /* ebx is callee-save, so push it */
                    "   pushl   %%ebx      \n"
                    "   movl    %4, %%eax  \n"
                    "   cpuid              \n"
                    "   movl    %%eax, %0  \n"
                    "   movl    %%ebx, %1  \n"
                    "   movl    %%ecx, %2  \n"
                    "   movl    %%edx, %3  \n"
                        /* restore ebx */
                    "   popl    %%ebx      \n"

                    : /* Outputs */
                    "=m" (value_of_eax),
                    "=m" (value_of_ebx),
                    "=m" (value_of_ecx),
                    "=m" (value_of_edx)
                    : /* Inputs */
                    "m" (arg)
                    : /* Clobbers */
                    "%eax", "%ecx", "%edx"
                    );
  *eaxp = value_of_eax;
  *ebxp = value_of_ebx;
  *ecxp = value_of_ecx;
  *edxp = value_of_edx;
#endif /* _LP64 */
}

/* The definition of a server-class machine for linux-i586 */
jboolean
ServerClassMachineImpl(void) {
  jboolean            result            = JNI_FALSE;
  /* How big is a server class machine? */
  const unsigned long server_processors = 2UL;
  const uint64_t      server_memory     = 2UL * GB;
  /*
   * We seem not to get our full complement of memory.
   *     We allow some part (1/8?) of the memory to be "missing",
   *     based on the sizes of DIMMs, and maybe graphics cards.
   */
  const uint64_t      missing_memory    = 256UL * MB;
  const uint64_t      actual_memory     = physical_memory();

  /* Is this a server class machine? */
  if (actual_memory >= (server_memory - missing_memory)) {
    const unsigned long actual_processors = physical_processors();
    if (actual_processors >= server_processors) {
      result = JNI_TRUE;
    }
  }
  JLI_TraceLauncher("linux_" LIBARCHNAME "_ServerClassMachine: %s\n",
           (result == JNI_TRUE ? "true" : "false"));
  return result;
}
#endif /* __linux__ */

/*
 * Routines shared by solaris-i586 and linux-i586.
 */

enum HyperThreadingSupport_enum {
  hts_supported        =  1,
  hts_too_soon_to_tell =  0,
  hts_not_supported    = -1,
  hts_not_pentium4     = -2,
  hts_not_intel        = -3
};
typedef enum HyperThreadingSupport_enum HyperThreadingSupport;

/* Determine if hyperthreading is supported */
static HyperThreadingSupport
hyperthreading_support(void) {
  HyperThreadingSupport result = hts_too_soon_to_tell;
  /* Bits 11 through 8 is family processor id */
# define FAMILY_ID_SHIFT 8
# define FAMILY_ID_MASK 0xf
  /* Bits 23 through 20 is extended family processor id */
# define EXT_FAMILY_ID_SHIFT 20
# define EXT_FAMILY_ID_MASK 0xf
  /* Pentium 4 family processor id */
# define PENTIUM4_FAMILY_ID 0xf
  /* Bit 28 indicates Hyper-Threading Technology support */
# define HT_BIT_SHIFT 28
# define HT_BIT_MASK 1
  uint32_t vendor_id[3] = { 0U, 0U, 0U };
  uint32_t value_of_eax = 0U;
  uint32_t value_of_edx = 0U;
  uint32_t dummy        = 0U;

  /* Yes, this is supposed to be [0], [2], [1] */
  get_cpuid(0, &dummy, &vendor_id[0], &vendor_id[2], &vendor_id[1]);
  JLI_TraceLauncher("vendor: %c %c %c %c %c %c %c %c %c %c %c %c \n",
           ((vendor_id[0] >>  0) & 0xff),
           ((vendor_id[0] >>  8) & 0xff),
           ((vendor_id[0] >> 16) & 0xff),
           ((vendor_id[0] >> 24) & 0xff),
           ((vendor_id[1] >>  0) & 0xff),
           ((vendor_id[1] >>  8) & 0xff),
           ((vendor_id[1] >> 16) & 0xff),
           ((vendor_id[1] >> 24) & 0xff),
           ((vendor_id[2] >>  0) & 0xff),
           ((vendor_id[2] >>  8) & 0xff),
           ((vendor_id[2] >> 16) & 0xff),
           ((vendor_id[2] >> 24) & 0xff));
  get_cpuid(1, &value_of_eax, &dummy, &dummy, &value_of_edx);
  JLI_TraceLauncher("value_of_eax: 0x%x  value_of_edx: 0x%x\n",
           value_of_eax, value_of_edx);
  if ((((value_of_eax >> FAMILY_ID_SHIFT) & FAMILY_ID_MASK) == PENTIUM4_FAMILY_ID) ||
      (((value_of_eax >> EXT_FAMILY_ID_SHIFT) & EXT_FAMILY_ID_MASK) != 0)) {
    if ((((vendor_id[0] >>  0) & 0xff) == 'G') &&
        (((vendor_id[0] >>  8) & 0xff) == 'e') &&
        (((vendor_id[0] >> 16) & 0xff) == 'n') &&
        (((vendor_id[0] >> 24) & 0xff) == 'u') &&
        (((vendor_id[1] >>  0) & 0xff) == 'i') &&
        (((vendor_id[1] >>  8) & 0xff) == 'n') &&
        (((vendor_id[1] >> 16) & 0xff) == 'e') &&
        (((vendor_id[1] >> 24) & 0xff) == 'I') &&
        (((vendor_id[2] >>  0) & 0xff) == 'n') &&
        (((vendor_id[2] >>  8) & 0xff) == 't') &&
        (((vendor_id[2] >> 16) & 0xff) == 'e') &&
        (((vendor_id[2] >> 24) & 0xff) == 'l')) {
      if (((value_of_edx >> HT_BIT_SHIFT) & HT_BIT_MASK) == HT_BIT_MASK) {
        JLI_TraceLauncher("Hyperthreading supported\n");
        result = hts_supported;
      } else {
        JLI_TraceLauncher("Hyperthreading not supported\n");
        result = hts_not_supported;
      }
    } else {
      JLI_TraceLauncher("Not GenuineIntel\n");
      result = hts_not_intel;
    }
  } else {
    JLI_TraceLauncher("not Pentium 4 or extended\n");
    result = hts_not_pentium4;
  }
  return result;
}

/* Determine how many logical processors there are per CPU */
static unsigned int
logical_processors_per_package(void) {
  /*
   * After CPUID with EAX==1, register EBX bits 23 through 16
   * indicate the number of logical processors per package
   */
# define NUM_LOGICAL_SHIFT 16
# define NUM_LOGICAL_MASK 0xff
  unsigned int result                        = 1U;
  const HyperThreadingSupport hyperthreading = hyperthreading_support();

  if (hyperthreading == hts_supported) {
    uint32_t value_of_ebx = 0U;
    uint32_t dummy        = 0U;

    get_cpuid(1, &dummy, &value_of_ebx, &dummy, &dummy);
    result = (value_of_ebx >> NUM_LOGICAL_SHIFT) & NUM_LOGICAL_MASK;
    JLI_TraceLauncher("logical processors per package: %u\n", result);
  }
  return result;
}

/* Compute the number of physical processors, not logical processors */
static unsigned long
physical_processors(void) {
  const long sys_processors = sysconf(_SC_NPROCESSORS_CONF);
  unsigned long result      = sys_processors;

  JLI_TraceLauncher("sysconf(_SC_NPROCESSORS_CONF): %lu\n", sys_processors);
  if (sys_processors > 1) {
    unsigned int logical_processors = logical_processors_per_package();
    if (logical_processors > 1) {
      result = (unsigned long) sys_processors / logical_processors;
    }
  }
  JLI_TraceLauncher("physical processors: %lu\n", result);
  return result;
}
