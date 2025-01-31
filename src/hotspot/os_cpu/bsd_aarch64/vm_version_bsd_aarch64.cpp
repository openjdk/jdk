/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2019, Red Hat Inc. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "vm_version_aarch64.hpp"

#ifdef __APPLE__
#include <sys/sysctl.h>
#endif

void VM_Version::get_compatible_board(char *buf, int buflen) {
  assert(buf != nullptr, "invalid argument");
  assert(buflen >= 1, "invalid argument");
  *buf = '\0';
}

int VM_Version::get_current_sve_vector_length() {
  ShouldNotCallThis();
  return -1;
}

int VM_Version::set_and_get_current_sve_vector_length(int length) {
  ShouldNotCallThis();
  return -1;
}

#ifdef __APPLE__

static bool cpu_has(const char* optional) {
  uint32_t val;
  size_t len = sizeof(val);
  if (sysctlbyname(optional, &val, &len, nullptr, 0)) {
    return false;
  }
  return val;
}

void VM_Version::get_os_cpu_info() {
  size_t sysctllen;

  // cpu_has() uses sysctlbyname function to check the existence of CPU
  // features. References: Apple developer document [1] and XNU kernel [2].
  // [1] https://developer.apple.com/documentation/kernel/1387446-sysctlbyname/determining_instruction_set_characteristics
  // [2] https://github.com/apple-oss-distributions/xnu/blob/main/bsd/kern/kern_mib.c
  //
  // Note that for some features (e.g., LSE, SHA512 and SHA3) there are two
  // parameters for sysctlbyname, which are invented at different times.
  // Considering backward compatibility, we check both here.
  //
  // Floating-point and Advance SIMD features are standard in Apple processors
  // beginning with M1 and A7, and don't need to be checked [1].
  // 1) hw.optional.floatingpoint always returns 1 [2].
  // 2) ID_AA64PFR0_EL1 describes AdvSIMD always equals to FP field.
  //    See the Arm ARM, section "ID_AA64PFR0_EL1, AArch64 Processor Feature
  //    Register 0".
  _features = CPU_FP | CPU_ASIMD;

  // All Apple-darwin Arm processors have AES, PMULL, SHA1 and SHA2.
  // See https://github.com/apple-oss-distributions/xnu/blob/main/osfmk/arm/commpage/commpage.c#L412
  // Note that we ought to add assertions to check sysctlbyname parameters for
  // these four CPU features, e.g., "hw.optional.arm.FEAT_AES", but the
  // corresponding string names are not available before xnu-8019 version.
  // Hence, assertions are omitted considering backward compatibility.
  _features |= CPU_AES | CPU_PMULL | CPU_SHA1 | CPU_SHA2;

  if (cpu_has("hw.optional.armv8_crc32")) {
    _features |= CPU_CRC32;
  }
  if (cpu_has("hw.optional.arm.FEAT_LSE") ||
      cpu_has("hw.optional.armv8_1_atomics")) {
    _features |= CPU_LSE;
  }
  if (cpu_has("hw.optional.arm.FEAT_SHA512") ||
      cpu_has("hw.optional.armv8_2_sha512")) {
    _features |= CPU_SHA512;
  }
  if (cpu_has("hw.optional.arm.FEAT_SHA3") ||
      cpu_has("hw.optional.armv8_2_sha3")) {
    _features |= CPU_SHA3;
  }

  int cache_line_size;
  int hw_conf_cache_line[] = { CTL_HW, HW_CACHELINE };
  sysctllen = sizeof(cache_line_size);
  if (sysctl(hw_conf_cache_line, 2, &cache_line_size, &sysctllen, nullptr, 0)) {
    cache_line_size = 16;
  }
  _icache_line_size = 16; // minimal line length CCSIDR_EL1 can hold
  _dcache_line_size = cache_line_size;

  uint64_t dczid_el0;
  __asm__ (
    "mrs %0, DCZID_EL0\n"
    : "=r"(dczid_el0)
  );
  if (!(dczid_el0 & 0x10)) {
    _zva_length = 4 << (dczid_el0 & 0xf);
  }

  int family;
  sysctllen = sizeof(family);
  if (sysctlbyname("hw.cpufamily", &family, &sysctllen, nullptr, 0)) {
    family = 0;
  }

  _model = family;
  _cpu = CPU_APPLE;
}

bool VM_Version::is_cpu_emulated() {
  return false;
}

#else // __APPLE__

#include <machine/armreg.h>
#if defined (__FreeBSD__)
#include <machine/elf.h>
#endif

#ifndef HWCAP_ASIMD
#define HWCAP_ASIMD (1<<1)
#endif

#ifndef HWCAP_AES
#define HWCAP_AES   (1<<3)
#endif

#ifndef HWCAP_PMULL
#define HWCAP_PMULL (1<<4)
#endif

#ifndef HWCAP_SHA1
#define HWCAP_SHA1  (1<<5)
#endif

#ifndef HWCAP_SHA2
#define HWCAP_SHA2  (1<<6)
#endif

#ifndef HWCAP_CRC32
#define HWCAP_CRC32 (1<<7)
#endif

#ifndef HWCAP_ATOMICS
#define HWCAP_ATOMICS (1<<8)
#endif

#ifndef ID_AA64PFR0_AdvSIMD_SHIFT
#define ID_AA64PFR0_AdvSIMD_SHIFT 20
#endif

#ifndef ID_AA64PFR0_AdvSIMD
#define ID_AA64PFR0_AdvSIMD(x) ((x) & (UL(0xf) << ID_AA64PFR0_AdvSIMD_SHIFT))
#endif

#ifndef ID_AA64PFR0_AdvSIMD_IMPL
#define ID_AA64PFR0_AdvSIMD_IMPL (UL(0x0) << ID_AA64PFR0_AdvSIMD_SHIFT)
#endif

#ifndef ID_AA64PFR0_AdvSIMD_HP
#define ID_AA64PFR0_AdvSIMD_HP (UL(0x1) << ID_AA64PFR0_AdvSIMD_SHIFT)
#endif

#ifndef ID_AA64ISAR0_AES_VAL
#define ID_AA64ISAR0_AES_VAL ID_AA64ISAR0_AES
#endif

#ifndef ID_AA64ISAR0_SHA1_VAL
#define ID_AA64ISAR0_SHA1_VAL ID_AA64ISAR0_SHA1
#endif

#ifndef ID_AA64ISAR0_SHA2_VAL
#define ID_AA64ISAR0_SHA2_VAL ID_AA64ISAR0_SHA2
#endif

#ifndef ID_AA64ISAR0_CRC32_VAL
#define ID_AA64ISAR0_CRC32_VAL ID_AA64ISAR0_CRC32
#endif

#define	CPU_IMPL_ARM		0x41
#define	CPU_IMPL_BROADCOM	0x42
#define	CPU_IMPL_CAVIUM		0x43
#define	CPU_IMPL_DEC		0x44
#define	CPU_IMPL_INFINEON	0x49
#define	CPU_IMPL_FREESCALE	0x4D
#define	CPU_IMPL_NVIDIA		0x4E
#define	CPU_IMPL_APM		0x50
#define	CPU_IMPL_QUALCOMM	0x51
#define	CPU_IMPL_MARVELL	0x56
#define	CPU_IMPL_INTEL		0x69

/* ARM Part numbers */
#define	CPU_PART_FOUNDATION	0xD00
#define	CPU_PART_CORTEX_A35	0xD04
#define	CPU_PART_CORTEX_A53	0xD03
#define	CPU_PART_CORTEX_A55	0xD05
#define	CPU_PART_CORTEX_A57	0xD07
#define	CPU_PART_CORTEX_A72	0xD08
#define	CPU_PART_CORTEX_A73	0xD09
#define	CPU_PART_CORTEX_A75	0xD0A

/* Cavium Part numbers */
#define	CPU_PART_THUNDERX	0x0A1
#define	CPU_PART_THUNDERX_81XX	0x0A2
#define	CPU_PART_THUNDERX_83XX	0x0A3
#define	CPU_PART_THUNDERX2	0x0AF

#define	CPU_REV_THUNDERX_1_0	0x00
#define	CPU_REV_THUNDERX_1_1	0x01

#define	CPU_REV_THUNDERX2_0	0x00

#define	CPU_IMPL(midr)	(((midr) >> 24) & 0xff)
#define	CPU_PART(midr)	(((midr) >> 4) & 0xfff)
#define	CPU_VAR(midr)	(((midr) >> 20) & 0xf)
#define	CPU_REV(midr)	(((midr) >> 0) & 0xf)
#define UL(x)   UINT64_C(x)

struct cpu_desc {
	u_int		cpu_impl;
	u_int		cpu_part_num;
	u_int		cpu_variant;
	u_int		cpu_revision;
	const char	*cpu_impl_name;
	const char	*cpu_part_name;
};

struct cpu_parts {
	u_int		part_id;
	const char	*part_name;
};
#define	CPU_PART_NONE	{ 0, "Unknown Processor" }

struct cpu_implementers {
	u_int			impl_id;
	const char		*impl_name;
	/*
	 * Part number is implementation defined
	 * so each vendor will have its own set of values and names.
	 */
	const struct cpu_parts	*cpu_parts;
};
#define	CPU_IMPLEMENTER_NONE	{ 0, "Unknown Implementer", cpu_parts_none }

/*
 * Per-implementer table of (PartNum, CPU Name) pairs.
 */
/* ARM Ltd. */
static const struct cpu_parts cpu_parts_arm[] = {
	{ CPU_PART_FOUNDATION, "Foundation-Model" },
	{ CPU_PART_CORTEX_A35, "Cortex-A35" },
	{ CPU_PART_CORTEX_A53, "Cortex-A53" },
	{ CPU_PART_CORTEX_A55, "Cortex-A55" },
	{ CPU_PART_CORTEX_A57, "Cortex-A57" },
	{ CPU_PART_CORTEX_A72, "Cortex-A72" },
	{ CPU_PART_CORTEX_A73, "Cortex-A73" },
	{ CPU_PART_CORTEX_A75, "Cortex-A75" },
	CPU_PART_NONE,
};
/* Cavium */
static const struct cpu_parts cpu_parts_cavium[] = {
	{ CPU_PART_THUNDERX, "ThunderX" },
	{ CPU_PART_THUNDERX2, "ThunderX2" },
	CPU_PART_NONE,
};

/* Unknown */
static const struct cpu_parts cpu_parts_none[] = {
	CPU_PART_NONE,
};

/*
 * Implementers table.
 */
const struct cpu_implementers cpu_implementers[] = {
	{ CPU_IMPL_ARM,		"ARM",		cpu_parts_arm },
	{ CPU_IMPL_BROADCOM,	"Broadcom",	cpu_parts_none },
	{ CPU_IMPL_CAVIUM,	"Cavium",	cpu_parts_cavium },
	{ CPU_IMPL_DEC,		"DEC",		cpu_parts_none },
	{ CPU_IMPL_INFINEON,	"IFX",		cpu_parts_none },
	{ CPU_IMPL_FREESCALE,	"Freescale",	cpu_parts_none },
	{ CPU_IMPL_NVIDIA,	"NVIDIA",	cpu_parts_none },
	{ CPU_IMPL_APM,		"APM",		cpu_parts_none },
	{ CPU_IMPL_QUALCOMM,	"Qualcomm",	cpu_parts_none },
	{ CPU_IMPL_MARVELL,	"Marvell",	cpu_parts_none },
	{ CPU_IMPL_INTEL,	"Intel",	cpu_parts_none },
	CPU_IMPLEMENTER_NONE,
};

#ifdef __FreeBSD__
static unsigned long os_get_processor_features() {
  unsigned long auxv = 0;
  uint64_t id_aa64isar0, id_aa64pfr0;

  id_aa64isar0 = READ_SPECIALREG(id_aa64isar0_el1);
  id_aa64pfr0 = READ_SPECIALREG(id_aa64pfr0_el1);

  if (ID_AA64ISAR0_AES_VAL(id_aa64isar0) == ID_AA64ISAR0_AES_BASE) {
    auxv = auxv | HWCAP_AES;
  }

  if (ID_AA64ISAR0_AES_VAL(id_aa64isar0) == ID_AA64ISAR0_AES_PMULL) {
    auxv = auxv | HWCAP_PMULL;
  }

  if (ID_AA64ISAR0_SHA1_VAL(id_aa64isar0) == ID_AA64ISAR0_SHA1_BASE) {
    auxv = auxv | HWCAP_SHA1;
  }

  if (ID_AA64ISAR0_SHA2_VAL(id_aa64isar0) == ID_AA64ISAR0_SHA2_BASE) {
    auxv = auxv | HWCAP_SHA2;
  }

  if (ID_AA64ISAR0_CRC32_VAL(id_aa64isar0) == ID_AA64ISAR0_CRC32_BASE) {
    auxv = auxv | HWCAP_CRC32;
  }

  if (ID_AA64PFR0_AdvSIMD(id_aa64pfr0) == ID_AA64PFR0_AdvSIMD_IMPL || \
      ID_AA64PFR0_AdvSIMD(id_aa64pfr0) == ID_AA64PFR0_AdvSIMD_HP ) {
    auxv = auxv | HWCAP_ASIMD;
  }

  return auxv;
}
#endif

void VM_Version::get_os_cpu_info() {
#ifdef __OpenBSD__
  // READ_SPECIALREG is not available from userland on OpenBSD.
  // Hardcode these values to the "lowest common denominator"
  _cpu = CPU_IMPL_ARM;
  _model = CPU_PART_CORTEX_A53;
  _variant = 0;
  _revision = 0;
  _features = HWCAP_ASIMD;
#elif defined(__FreeBSD__)
  struct cpu_desc cpu_desc[1];
  struct cpu_desc user_cpu_desc;

  uint32_t midr;
  uint32_t impl_id;
  uint32_t part_id;
  uint32_t cpu = 0;
  size_t i;
  const struct cpu_parts *cpu_partsp = nullptr;

  midr = READ_SPECIALREG(midr_el1);

  impl_id = CPU_IMPL(midr);
  for (i = 0; i < nitems(cpu_implementers); i++) {
    if (impl_id == cpu_implementers[i].impl_id ||
      cpu_implementers[i].impl_id == 0) {
      cpu_desc[cpu].cpu_impl = impl_id;
      cpu_desc[cpu].cpu_impl_name = cpu_implementers[i].impl_name;
      cpu_partsp = cpu_implementers[i].cpu_parts;
      break;
    }
  }
  part_id = CPU_PART(midr);
  for (i = 0; &cpu_partsp[i] != nullptr; i++) {
    if (part_id == cpu_partsp[i].part_id || cpu_partsp[i].part_id == 0) {
      cpu_desc[cpu].cpu_part_num = part_id;
      cpu_desc[cpu].cpu_part_name = cpu_partsp[i].part_name;
      break;
    }
  }

  cpu_desc[cpu].cpu_revision = CPU_REV(midr);
  cpu_desc[cpu].cpu_variant = CPU_VAR(midr);

  _cpu = cpu_desc[cpu].cpu_impl;
  _variant = cpu_desc[cpu].cpu_variant;
  _model = cpu_desc[cpu].cpu_part_num;
  _revision = cpu_desc[cpu].cpu_revision;

  uint64_t auxv = os_get_processor_features();

  _features = auxv & (
      HWCAP_FP      |
      HWCAP_ASIMD   |
      HWCAP_EVTSTRM |
      HWCAP_AES     |
      HWCAP_PMULL   |
      HWCAP_SHA1    |
      HWCAP_SHA2    |
      HWCAP_CRC32   |
      HWCAP_ATOMICS |
      HWCAP_DCPOP   |
      HWCAP_SHA3    |
      HWCAP_SHA512  |
      HWCAP_SVE);
#endif

  uint64_t ctr_el0;
  uint64_t dczid_el0;
  __asm__ (
    "mrs %0, CTR_EL0\n"
    "mrs %1, DCZID_EL0\n"
    : "=r"(ctr_el0), "=r"(dczid_el0)
  );

  _icache_line_size = (1 << (ctr_el0 & 0x0f)) * 4;
  _dcache_line_size = (1 << ((ctr_el0 >> 16) & 0x0f)) * 4;

  if (!(dczid_el0 & 0x10)) {
    _zva_length = 4 << (dczid_el0 & 0xf);
  }
}

#endif // __APPLE__
