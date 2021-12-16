#include "unittest.hpp"
#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"

TEST(NetworkPerformance, NetworkUtiliazation) {
    NetworkPerformanceInterface* netperf = new NetworkPerformanceInterface();
    NetworkInterface* netperf_stats;

    netperf->initialize();
    netperf->network_utilization(&netperf_stats);

    int n_interfaces = 0;
    for(const NetworkInterface* ptr = netperf_stats; ptr; ptr = ptr->next()) {
        // TODO: Test the interface metadata?
        n_interfaces++;
    }
    ASSERT_GT(n_interfaces, 0) << "expected non-zero number of interfaces";
}

TEST(CPUPerformance, CPULoad) {
    int lcpu;
    double load;
    CPUPerformanceInterface* cpu_perf = new CPUPerformanceInterface();

    cpu_perf->initialize();
    load = 0.0;

    for(lcpu=0; cpu_perf->cpu_load(lcpu,&load) == OS_OK; lcpu++) {
        ASSERT_GE(load, 0.0);
        ASSERT_LE(load, 1.0);
    }

    ASSERT_GT(lcpu, 0);
}

TEST(CPUPerformance, ContextSwitchRate) {
    double rate;
    CPUPerformanceInterface* cpu_perf = new CPUPerformanceInterface();

    cpu_perf->initialize();
    rate = 0.0;

    cpu_perf->context_switch_rate(&rate);

    ASSERT_GT(rate, 0.0);
}

TEST(CPUPerformance, CPULoadTotalProcess) {
    double load;
    CPUPerformanceInterface* cpu_perf = new CPUPerformanceInterface();

    cpu_perf->initialize();
    load = 0.0;

    cpu_perf->cpu_load_total_process(&load);

    ASSERT_GE(load, 0.0);
}

TEST(CPUPerformance, CPULoadsProcess) {
    double jvm_uload, jvm_kload, sys_load;
    CPUPerformanceInterface* cpu_perf = new CPUPerformanceInterface();

    cpu_perf->initialize();
    jvm_uload = 0.0;
    jvm_kload = 0.0;
    sys_load  = 0.0;

    cpu_perf->cpu_loads_process(&jvm_uload, &jvm_kload, &sys_load);

    ASSERT_GE(jvm_uload, 0.0);
    ASSERT_GE(jvm_kload, 0.0);
    ASSERT_GE(sys_load,  0.0);

    ASSERT_LE(jvm_uload, 1.0);
    ASSERT_LE(jvm_kload, 1.0);
    ASSERT_LE(sys_load,  1.0);
}

TEST(SystemProcessInterface, SystemProcesses) {
   SystemProcessInterface* proc_info = new SystemProcessInterface();
   SystemProcess* proc;
   int n_procs;

   n_procs = 0;
   proc_info->initialize();
   proc_info->system_processes(&proc, &n_procs);
   ASSERT_GT(n_procs, 0) << "expected non-zero number of processes";

   for( ; proc ; proc = proc->next()) {
       ASSERT_NE(0, proc->pid());
       ASSERT_NE(nullptr, proc->name());
   }
}
