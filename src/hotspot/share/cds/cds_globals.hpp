/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_CDS_GLOBALS_HPP
#define SHARE_CDS_CDS_GLOBALS_HPP

#include "runtime/globals_shared.hpp"

//
// Defines all globals flags used by CDS.
//

#define CDS_FLAGS(develop,                                                  \
                  develop_pd,                                               \
                  product,                                                  \
                  product_pd,                                               \
                  range,                                                    \
                  constraint)                                               \
  /* Shared spaces */                                                       \
                                                                            \
  product(bool, VerifySharedSpaces, false,                                  \
          "Verify integrity of shared spaces")                              \
                                                                            \
  product(bool, RecordDynamicDumpInfo, false,                               \
          "Record class info for jcmd VM.cds dynamic_dump")                 \
                                                                            \
  product(bool, AutoCreateSharedArchive, false,                             \
          "Create shared archive at exit if cds mapping failed")            \
                                                                            \
  product(bool, PrintSharedArchiveAndExit, false,                           \
          "Print shared archive file contents")                             \
                                                                            \
  product(size_t, SharedBaseAddress, LP64_ONLY(32*G)                        \
          NOT_LP64(LINUX_ONLY(2*G) NOT_LINUX(0)),                           \
          "Address to allocate shared memory region for class data")        \
          range(0, SIZE_MAX)                                                \
                                                                            \
  product(ccstr, SharedArchiveConfigFile, nullptr,                          \
          "Data to add to the CDS archive file")                            \
                                                                            \
  product(uint, SharedSymbolTableBucketSize, 4,                             \
          "Average number of symbols per bucket in shared table")           \
          range(2, 246)                                                     \
                                                                            \
  product(bool, AllowArchivingWithJavaAgent, false, DIAGNOSTIC,             \
          "Allow Java agent to be run with CDS dumping (not applicable"     \
          " to AOT")                                                        \
                                                                            \
  develop(ccstr, ArchiveHeapTestClass, nullptr,                             \
          "For JVM internal testing only. The static field named "          \
          "\"archivedObjects\" of the specified class is stored in the "    \
          "CDS archive heap")                                               \
                                                                            \
  develop(ccstr, AOTInitTestClass, nullptr,                                 \
          "For JVM internal testing only. The specified class is stored "   \
          "in the initialized state in the AOT cache ")                     \
                                                                            \
  product(ccstr, DumpLoadedClassList, nullptr,                              \
          "Dump the names all loaded classes, that could be stored into "   \
          "the CDS archive, in the specified file")                         \
                                                                            \
  product(ccstr, SharedClassListFile, nullptr,                              \
          "Override the default CDS class list")                            \
                                                                            \
  product(ccstr, SharedArchiveFile, nullptr,                                \
          "Override the default location of the CDS archive file")          \
                                                                            \
  product(ccstr, ArchiveClassesAtExit, nullptr,                             \
          "The path and name of the dynamic archive file")                  \
                                                                            \
  product(ccstr, ExtraSharedClassListFile, nullptr,                         \
          "Extra classlist for building the CDS archive file")              \
                                                                            \
  product(int, ArchiveRelocationMode, 1, DIAGNOSTIC,                        \
           "(0) first map at preferred address, and if "                    \
           "unsuccessful, map at alternative address; "                     \
           "(1) always map at alternative address (default); "              \
           "(2) always map at preferred address, and if unsuccessful, "     \
           "do not map the archive")                                        \
           range(0, 2)                                                      \
                                                                            \
  /*========== New "AOT" flags =========================================*/  \
  /* The following 3 flags are aliases of -Xshare:dump,                 */  \
  /* -XX:SharedArchiveFile=..., etc. See CDSConfig::check_flag_aliases()*/  \
                                                                            \
  product(ccstr, AOTMode, nullptr,                                          \
          "Specifies how AOTCache should be created or used. Valid values " \
          "are: off, record, create, auto, on; the default is auto")        \
          constraint(AOTModeConstraintFunc, AtParse)                        \
                                                                            \
  product(ccstr, AOTConfiguration, nullptr,                                 \
          "The configuration file written by -XX:AOTMode=record, and "      \
          "loaded by -XX:AOTMode=create. This file contains profiling data "\
          "for deciding what contents should be added to AOTCache. ")       \
          constraint(AOTConfigurationConstraintFunc, AtParse)               \
                                                                            \
  product(ccstr, AOTCache, nullptr,                                         \
          "Cache for improving start up and warm up")                       \
          constraint(AOTCacheConstraintFunc, AtParse)                       \
                                                                            \
  product(ccstr, AOTCacheOutput, nullptr,                                   \
          "Specifies the file name for writing the AOT cache")              \
          constraint(AOTCacheOutputConstraintFunc, AtParse)                 \
                                                                            \
  product(bool, AOTInvokeDynamicLinking, false, DIAGNOSTIC,                 \
          "AOT-link JVM_CONSTANT_InvokeDynamic entries in cached "          \
          "ConstantPools")                                                  \
                                                                            \
  product(bool, AOTClassLinking, false,                                     \
          "Load/link all archived classes for the boot/platform/app "       \
          "loaders before application main")                                \
                                                                            \
  product(bool, AOTCacheParallelRelocation, true, DIAGNOSTIC,               \
          "Use parallel relocation code to speed up startup.")              \
                                                                            \
  /* flags to control training and deployment modes  */                     \
                                                                            \
  product(bool, AOTRecordTraining, false, DIAGNOSTIC,                       \
          "Request output of training data for improved deployment.")       \
                                                                            \
  product(bool, AOTReplayTraining, false, DIAGNOSTIC,                       \
          "Read training data, if available, for use in this execution")    \
                                                                            \
  product(bool, AOTPrintTrainingInfo, false, DIAGNOSTIC,                    \
          "Print additional information about training")                    \
                                                                            \
  product(bool, AOTVerifyTrainingData, trueInDebug, DIAGNOSTIC,             \
          "Verify archived training data")                                  \
                                                                            \
  product(bool, AOTCompileEagerly, false, EXPERIMENTAL,                     \
          "Compile methods as soon as possible")                            \
                                                                            \
  /* AOT Code flags */                                                      \
                                                                            \
  product(bool, AOTAdapterCaching, false, DIAGNOSTIC,                       \
          "Enable saving and restoring i2c2i adapters in AOT cache")        \
                                                                            \
  product(bool, AOTStubCaching, false, DIAGNOSTIC,                          \
          "Enable saving and restoring stubs and code blobs in AOT cache")  \
                                                                            \
  product(uint, AOTCodeMaxSize, 10*M, DIAGNOSTIC,                           \
          "Buffer size in bytes for AOT code caching")                      \
          range(1*M, max_jint)                                              \
                                                                            \
  product(bool, AbortVMOnAOTCodeFailure, false, DIAGNOSTIC,                 \
          "Abort VM on the first occurrence of AOT code load or store "     \
          "failure. By default VM will continue execute without AOT code.") \
                                                                            \
  develop(bool, TestAOTAdapterLinkFailure, false,                           \
          "Test failure of adapter linking when loading from AOT cache.")   \

// end of CDS_FLAGS

DECLARE_FLAGS(CDS_FLAGS)

#endif // SHARE_CDS_CDS_GLOBALS_HPP
