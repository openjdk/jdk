/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */
package jdk.test.lib.jfr;

import jdk.jfr.EventType;

/**
 * Contains id for events that are shipped with the JDK.
 *
 */
public class EventNames {

    public static final String PREFIX = "jdk.";
    private static final String GC_CATEGORY = "GC";

    // JVM Configuration
    public static final String JVMInformation = PREFIX + "JVMInformation";
    public static final String InitialSystemProperty = PREFIX + "InitialSystemProperty";
    public static final String IntFlag = PREFIX + "IntFlag";
    public static final String UnsignedIntFlag = PREFIX + "UnsignedIntFlag";
    public static final String LongFlag = PREFIX + "LongFlag";
    public static final String UnsignedLongFlag = PREFIX + "UnsignedLongFlag";
    public static final String DoubleFlag = PREFIX + "DoubleFlag";
    public static final String BooleanFlag = PREFIX + "BooleanFlag";
    public static final String StringFlag = PREFIX + "StringFlag";
    public static final String IntFlagChanged = PREFIX + "IntFlagChanged";
    public static final String UnsignedIntFlagChanged = PREFIX + "UnsignedIntFlagChanged";
    public static final String LongFlagChanged = PREFIX + "LongFlagChanged";
    public static final String UnsignedLongFlagChanged = PREFIX + "UnsignedLongFlagChanged";
    public static final String DoubleFlagChanged = PREFIX + "DoubleFlagChanged";
    public static final String BooleanFlagChanged = PREFIX + "BooleanFlagChanged";
    public static final String StringFlagChanged = PREFIX + "StringFlagChanged";

    // Runtime
    public static final String ThreadStart = PREFIX + "ThreadStart";
    public static final String ThreadEnd = PREFIX + "ThreadEnd";
    public static final String ThreadSleep = PREFIX + "ThreadSleep";
    public static final String ThreadPark = PREFIX + "ThreadPark";
    public static final String JavaMonitorEnter = PREFIX + "JavaMonitorEnter";
    public static final String JavaMonitorWait = PREFIX + "JavaMonitorWait";
    public static final String JavaMonitorInflate = PREFIX + "JavaMonitorInflate";
    public static final String SyncOnValueBasedClass = PREFIX + "SyncOnValueBasedClass";
    public static final String ClassLoad = PREFIX + "ClassLoad";
    public static final String ClassDefine = PREFIX + "ClassDefine";
    public static final String ClassUnload = PREFIX + "ClassUnload";
    public static final String SafepointBegin = PREFIX + "SafepointBegin";
    public static final String SafepointStateSynchronization = PREFIX + "SafepointStateSynchronization";
    public static final String SafepointCleanup = PREFIX + "SafepointCleanup";
    public static final String SafepointCleanupTask = PREFIX + "SafepointCleanupTask";
    public static final String SafepointEnd = PREFIX + "SafepointEnd";
    public static final String ExecuteVMOperation = PREFIX + "ExecuteVMOperation";
    public static final String Shutdown = PREFIX + "Shutdown";
    public static final String JavaThreadStatistics = PREFIX + "JavaThreadStatistics";
    public static final String ClassLoadingStatistics = PREFIX + "ClassLoadingStatistics";
    public static final String ClassLoaderStatistics = PREFIX + "ClassLoaderStatistics";
    public static final String ThreadAllocationStatistics = PREFIX + "ThreadAllocationStatistics";
    public static final String ExecutionSample = PREFIX + "ExecutionSample";
    public static final String NativeMethodSample = PREFIX + "NativeMethodSample";
    public static final String ThreadDump = PREFIX + "ThreadDump";
    public static final String OldObjectSample = PREFIX + "OldObjectSample";
    public static final String SymbolTableStatistics = PREFIX + "SymbolTableStatistics";
    public static final String StringTableStatistics = PREFIX + "StringTableStatistics";
    public static final String RedefineClasses = PREFIX + "RedefineClasses";
    public static final String RetransformClasses = PREFIX + "RetransformClasses";
    public static final String ClassRedefinition = PREFIX + "ClassRedefinition";
    public static final String FinalizerStatistics = PREFIX + "FinalizerStatistics";
    public static final String NativeMemoryUsage = PREFIX + "NativeMemoryUsage";
    public static final String NativeMemoryUsageTotal = PREFIX + "NativeMemoryUsageTotal";
    public static final String JavaAgent = PREFIX + "JavaAgent";
    public static final String NativeAgent = PREFIX + "NativeAgent";

    // This event is hard to test
    public static final String ReservedStackActivation = PREFIX + "ReservedStackActivation";

    // GC
    public static final String GCHeapMemoryUsage = PREFIX + "GCHeapMemoryUsage";
    public static final String GCHeapMemoryPoolUsage = PREFIX + "GCHeapMemoryPoolUsage";
    public static final String GCHeapSummary = PREFIX + "GCHeapSummary";
    public static final String MetaspaceSummary = PREFIX + "MetaspaceSummary";
    public static final String MetaspaceGCThreshold = PREFIX + "MetaspaceGCThreshold";
    public static final String MetaspaceAllocationFailure = PREFIX + "MetaspaceAllocationFailure";
    public static final String MetaspaceOOM = PREFIX + "MetaspaceOOM";
    public static final String MetaspaceChunkFreeListSummary = PREFIX + "MetaspaceChunkFreeListSummary";
    public static final String PSHeapSummary = PREFIX + "PSHeapSummary";
    public static final String G1HeapSummary = PREFIX + "G1HeapSummary";
    public static final String G1HeapRegionInformation = PREFIX + "G1HeapRegionInformation";
    public static final String G1HeapRegionTypeChange = PREFIX + "G1HeapRegionTypeChange";
    public static final String ShenandoahHeapRegionInformation = PREFIX + "ShenandoahHeapRegionInformation";
    public static final String ShenandoahHeapRegionStateChange = PREFIX + "ShenandoahHeapRegionStateChange";
    public static final String TenuringDistribution = PREFIX + "TenuringDistribution";
    public static final String GarbageCollection = PREFIX + "GarbageCollection";
    public static final String ParallelOldGarbageCollection = PREFIX + "ParallelOldGarbageCollection";
    public static final String ParallelOldCollection = ParallelOldGarbageCollection;
    public static final String YoungGarbageCollection = PREFIX + "YoungGarbageCollection";
    public static final String OldGarbageCollection = PREFIX + "OldGarbageCollection";
    public static final String G1GarbageCollection = PREFIX + "G1GarbageCollection";
    public static final String G1MMU = PREFIX + "G1MMU";
    public static final String EvacuationInformation = PREFIX + "EvacuationInformation";
    public static final String GCReferenceStatistics = PREFIX + "GCReferenceStatistics";
    public static final String ObjectCountAfterGC = PREFIX + "ObjectCountAfterGC";
    public static final String PromoteObjectInNewPLAB = PREFIX + "PromoteObjectInNewPLAB";
    public static final String PromoteObjectOutsidePLAB = PREFIX + "PromoteObjectOutsidePLAB";
    public static final String PromotionFailed = PREFIX + "PromotionFailed";
    public static final String EvacuationFailed = PREFIX + "EvacuationFailed";
    public static final String ConcurrentModeFailure = PREFIX + "ConcurrentModeFailure";
    public static final String GCPhasePause = PREFIX + "GCPhasePause";
    public static final String GCPhasePauseLevel1 = PREFIX + "GCPhasePauseLevel1";
    public static final String GCPhasePauseLevel2 = PREFIX + "GCPhasePauseLevel2";
    public static final String GCPhasePauseLevel3 = PREFIX + "GCPhasePauseLevel3";
    public static final String GCPhasePauseLevel4 = PREFIX + "GCPhasePauseLevel4";
    public static final String ObjectCount = PREFIX + "ObjectCount";
    public static final String GCConfiguration = PREFIX + "GCConfiguration";
    public static final String GCSurvivorConfiguration = PREFIX + "GCSurvivorConfiguration";
    public static final String GCTLABConfiguration = PREFIX + "GCTLABConfiguration";
    public static final String GCHeapConfiguration = PREFIX + "GCHeapConfiguration";
    public static final String YoungGenerationConfiguration = PREFIX + "YoungGenerationConfiguration";
    public static final String G1AdaptiveIHOP = PREFIX + "G1AdaptiveIHOP";
    public static final String G1EvacuationYoungStatistics = PREFIX + "G1EvacuationYoungStatistics";
    public static final String G1EvacuationOldStatistics = PREFIX + "G1EvacuationOldStatistics";
    public static final String G1BasicIHOP = PREFIX + "G1BasicIHOP";
    public static final String AllocationRequiringGC = PREFIX + "AllocationRequiringGC";
    public static final String GCPhaseParallel = PREFIX + "GCPhaseParallel";
    public static final String GCPhaseConcurrent = PREFIX + "GCPhaseConcurrent";
    public static final String GCPhaseConcurrentLevel1 = PREFIX + "GCPhaseConcurrentLevel1";
    public static final String GCPhaseConcurrentLevel2 = PREFIX + "GCPhaseConcurrentLevel2";
    public static final String ZYoungGarbageCollection = PREFIX + "ZYoungGarbageCollection";
    public static final String ZOldGarbageCollection = PREFIX + "ZOldGarbageCollection";
    public static final String ZAllocationStall = PREFIX + "ZAllocationStall";
    public static final String ZPageAllocation = PREFIX + "ZPageAllocation";
    public static final String ZRelocationSet = PREFIX + "ZRelocationSet";
    public static final String ZRelocationSetGroup = PREFIX + "ZRelocationSetGroup";
    public static final String ZUncommit = PREFIX + "ZUncommit";
    public static final String ZUnmap = PREFIX + "ZUnmap";
    public static final String GCLocker = PREFIX + "GCLocker";
    public static final String SystemGC = PREFIX + "SystemGC";
    public static final String GCCPUTime = PREFIX + "GCCPUTime";

    // Compiler
    public static final String Compilation = PREFIX + "Compilation";
    public static final String CompilerPhase = PREFIX + "CompilerPhase";
    public static final String CompilationFailure = PREFIX + "CompilationFailure";
    public static final String CompilerInlining = PREFIX + "CompilerInlining";
    public static final String CompilerQueueUtilization = PREFIX + "CompilerQueueUtilization";
    public static final String CompilerStatistics = PREFIX + "CompilerStatistics";
    public static final String CompilerConfiguration = PREFIX + "CompilerConfiguration";
    public static final String CodeCacheStatistics = PREFIX + "CodeCacheStatistics";
    public static final String CodeCacheConfiguration = PREFIX + "CodeCacheConfiguration";
    public static final String CodeCacheFull = PREFIX + "CodeCacheFull";
    public static final String ObjectAllocationInNewTLAB = PREFIX + "ObjectAllocationInNewTLAB";
    public static final String ObjectAllocationOutsideTLAB = PREFIX + "ObjectAllocationOutsideTLAB";
    public static final String ObjectAllocationSample = PREFIX + "ObjectAllocationSample";
    public static final String Deoptimization = PREFIX + "Deoptimization";
    public static final String JITRestart = PREFIX + "JITRestart";

    // OS
    public static final String OSInformation = PREFIX + "OSInformation";
    public static final String VirtualizationInformation = PREFIX + "VirtualizationInformation";
    public static final String CPUInformation = PREFIX + "CPUInformation";
    public static final String CPULoad = PREFIX + "CPULoad";
    public static final String ThreadCPULoad = PREFIX + "ThreadCPULoad";
    public static final String SystemProcess = PREFIX + "SystemProcess";
    public static final String ThreadContextSwitchRate = PREFIX + "ThreadContextSwitchRate";
    public static final String InitialEnvironmentVariable = PREFIX + "InitialEnvironmentVariable";
    public static final String NativeLibrary = PREFIX + "NativeLibrary";
    public static final String NativeLibraryLoad = PREFIX + "NativeLibraryLoad";
    public static final String NativeLibraryUnload = PREFIX + "NativeLibraryUnload";
    public static final String PhysicalMemory = PREFIX + "PhysicalMemory";
    public static final String NetworkUtilization = PREFIX + "NetworkUtilization";
    public static final String ProcessStart = PREFIX + "ProcessStart";
    public static final String ResidentSetSize = PREFIX + "ResidentSetSize";

    // JDK
    public static final String FileForce  = PREFIX + "FileForce";
    public static final String FileRead = PREFIX + "FileRead";
    public static final String FileWrite = PREFIX + "FileWrite";
    public static final String SocketRead = PREFIX + "SocketRead";
    public static final String SocketWrite = PREFIX + "SocketWrite";
    public static final String ExceptionStatistics = PREFIX + "ExceptionStatistics";
    public static final String JavaExceptionThrow = PREFIX + "JavaExceptionThrow";
    public static final String JavaErrorThrow = PREFIX + "JavaErrorThrow";
    public static final String ModuleRequire = PREFIX + "ModuleRequire";
    public static final String ModuleExport = PREFIX + "ModuleExport";
    public static final String TLSHandshake = PREFIX + "TLSHandshake";
    public static final String X509Certificate = PREFIX + "X509Certificate";
    public static final String X509Validation = PREFIX + "X509Validation";
    public static final String InitialSecurityProperty = PREFIX + "InitialSecurityProperty";
    public static final String SecurityProperty = PREFIX + "SecurityPropertyModification";
    public static final String SecurityProviderService = PREFIX + "SecurityProviderService";
    public static final String DirectBufferStatistics = PREFIX + "DirectBufferStatistics";
    public static final String Deserialization = PREFIX + "Deserialization";
    public static final String VirtualThreadStart = PREFIX + "VirtualThreadStart";
    public static final String VirtualThreadEnd = PREFIX + "VirtualThreadEnd";
    public static final String VirtualThreadPinned = PREFIX + "VirtualThreadPinned";
    public static final String VirtualThreadSubmitFailed = PREFIX + "VirtualThreadSubmitFailed";

    // Containers
    public static final String ContainerConfiguration = PREFIX + "ContainerConfiguration";
    public static final String ContainerCPUUsage = PREFIX + "ContainerCPUUsage";
    public static final String ContainerCPUThrottling = PREFIX + "ContainerCPUThrottling";
    public static final String ContainerMemoryUsage = PREFIX + "ContainerMemoryUsage";
    public static final String ContainerIOUsage = PREFIX + "ContainerIOUsage";

    // Flight Recorder
    public static final String DumpReason = PREFIX + "DumpReason";
    public static final String DataLoss = PREFIX + "DataLoss";
    public static final String CPUTimeStampCounter = PREFIX + "CPUTimeStampCounter";
    public static final String ActiveRecording = PREFIX + "ActiveRecording";
    public static final String ActiveSetting = PREFIX + "ActiveSetting";
    public static final String Flush = PREFIX + "Flush";

    // Diagnostics
    public static final String HeapDump = PREFIX + "HeapDump";

    public static boolean isGcEvent(EventType et) {
        return et.getCategoryNames().contains(GC_CATEGORY);
    }

}
