package nmt;

import java.util.Arrays;

public class Benchmark
{
	private static double OverheadPercentage(double before, double after)
	{
		if ((before > 0.0) && (after > 0.0))
		{
			return ((after/before - 1.0) * 100.0);
		}
		else
		{
			return 0.0;
		}
	}

	private static void print_summary(String java_path, NMT_LogInfo nmt_log_info, NMT_Allocation[] allocations, NMT_Component[] components, MemoryStats total_stats, AllocationHistogram histogram_all)
	{
		System.out.println("\n");
        System.out.println("-----------------------------------------------------------------------------------------------------------------------------");
		System.out.println(String.format("                                     NMT_LogInfo: %s", nmt_log_info.levelAsString()));
		System.out.println("\n");

		System.out.println(String.format("                            Requested (with NMT): %,11d bytes", total_stats.requested_bytes));
		System.out.println(String.format("                            Allocated (with NMT): %,11d bytes", total_stats.allocated_bytes));

		long overhead_in_bytes = total_stats.overheadBytes();
		double malloc_overhead = OverheadPercentage(total_stats.requested_bytes, total_stats.allocated_bytes);
		System.out.println(String.format("   Overhead due to malloc rounding up (with NMT): %,11d bytes [%.3f%%]", overhead_in_bytes, malloc_overhead));
		System.out.println("");

		if (nmt_log_info.equals(NMT_LogInfo.SUMMARY) || nmt_log_info.equals(NMT_LogInfo.DETAIL))
		{
			MemoryStats preinit_stats = null;
			for (int c = 0; c < components.length; c++)
			{
				if (components[c].flag == NMT_Component.PREINIT)
				{
					preinit_stats = components[c].getStatistics();
					break;
				}
			}

			MemoryStats nmt_stats = null;
			for (int c = 0; c < components.length; c++)
			{
				if (components[c].flag == NMT_Component.NATIVE_MEMORY_TRACKING)
				{
					nmt_stats = components[c].getStatistics();
					break;
				}
			}

			MemoryStats no_nmt_stats = histogram_all.estimateStatsNoNMT(java_path, allocations, nmt_log_info.overhead());
			long requested_no_nmt = no_nmt_stats.requested_bytes;
			long allocated_no_nmt = no_nmt_stats.allocated_bytes;
			long no_nmt_overhead_in_bytes = allocated_no_nmt - requested_no_nmt;
			double no_nmt_overhead = OverheadPercentage(requested_no_nmt, allocated_no_nmt);
			System.out.println(String.format("                         Requested (est. no NMT): %,11d bytes", requested_no_nmt));
			System.out.println(String.format("                         Allocated (est. no NMT): %,11d bytes", allocated_no_nmt));
			System.out.println(String.format("Overhead due to malloc rounding up (est. no NMT): %,11d bytes [%.3f%%]", no_nmt_overhead_in_bytes, no_nmt_overhead));
			System.out.println("");

			long nmt_headers_count = total_stats.mallocs_count + total_stats.reallocs_count - preinit_stats.mallocs_count - preinit_stats.reallocs_count;
			long nmt_headers_bytes = nmt_headers_count * nmt_log_info.overhead();
			double nmt_headers_overhead = OverheadPercentage(total_stats.requested_bytes-nmt_headers_bytes, total_stats.requested_bytes);
			System.out.println(String.format("         Overhead due to NMT headers (requested): %,11d bytes [%.3f%%] [#%d]", nmt_headers_bytes, nmt_headers_overhead, nmt_headers_count));

			long nmt_objects_count = nmt_stats.mallocs_count + nmt_stats.reallocs_count;
			long nmt_objects_bytes = nmt_stats.requested_bytes - (nmt_objects_count * nmt_log_info.overhead()); // substract the NMT header cost
			double nmt_objects_overhead = OverheadPercentage(total_stats.requested_bytes-nmt_objects_bytes, total_stats.requested_bytes);
			System.out.println(String.format("         Overhead due to NMT objects (requested): %,11d bytes [%.3f%%] [#%d]", nmt_objects_bytes, nmt_objects_overhead, nmt_objects_count));

			long total_nmt_bytes = nmt_headers_bytes + nmt_objects_bytes;
			double total_nmt_overhead = OverheadPercentage(total_stats.requested_bytes-total_nmt_bytes, total_stats.requested_bytes);
			System.out.println(String.format("                 Overhead due to NMT (requested): %,11d bytes [%.3f%%]", total_nmt_bytes, total_nmt_overhead));
			System.out.println("");

			System.out.println(String.format("               NMT overhead (requested) increase: %11.3f %%", OverheadPercentage(requested_no_nmt, total_stats.requested_bytes)));
			System.out.println(String.format("               NMT overhead (allocated) increase: %11.3f %%", OverheadPercentage(allocated_no_nmt, total_stats.allocated_bytes)));
		}
		System.out.println("\n\n");
	}

	private static void print_memory_pointers_bits_coverage(NMT_Allocation[] allocations)
	{
		System.out.println("memory pointers bits coverage (percentile of 1 vs 0 appearing in bits 63..0):");
		double bits_counter_total = 0;
		long bits_counters[] = new long[64];
		for (int i = 0; i < 64; i++)
		{
			bits_counters[i] = 0;
		}
		for (int i = 0; i < allocations.length; i++)
		{
			NMT_Allocation a = allocations[i];
			long ptr = a.ptr_curr;
			if (ptr != 0)
			{
				bits_counter_total++;
				for (int j = 0; j < 64; j++)
				{
					bits_counters[j] += (ptr >> j) & 0b1;
				}
			}
		}
		for (int j = 63; j >= 0; j--)
		{
			System.out.printf("%3d ", Math.round(100.0*(double)bits_counters[j]/bits_counter_total));
		}
		System.out.println("");
	}

	private static void print_memory_histograms(NMT_Allocation[] allocations, NMT_Component[] components, AllocationHistogram histogram)
	{
		boolean print_sizes = true;
		int memory_type = AllocationHistogram.MEMORY_BOTH;
		int count_type = AllocationHistogram.COUNT_OVERHEAD;
		AllocationHistogram h = null;
		for (int i = 0; i < components.length; i++)
		{
			NMT_Component c = components[i];
			System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------");
			System.out.println("Histograms for NMT component \""+c+"\" [line cutoff="+Constants.HISTOGRAM_LINE_CUTOUT+"]:\n");
			h = new AllocationHistogram(allocations, c.flag);
			h.print(memory_type, count_type, print_sizes, Constants.HISTOGRAM_WIDTH);
		}
		System.out.println("\n");
        System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------");
		histogram.print(memory_type, count_type, print_sizes, Constants.HISTOGRAM_WIDTH);
		System.out.println("\n");
	}
	
    private static void print_performance_histograms(NMT_Duration[] durations)
    {
        // for (int i = 0; i < durations.length; i++)
        // {
        //     System.err.println("-->"+durations[i].duration+","+durations[i].requested+","+durations[i].actual+","+durations[i].type);
        // }
        DurationHistogram h = new DurationHistogram(durations);
        h.print(Constants.HISTOGRAM_WIDTH);
    }

	public static long examine_recording_with_pid(String mode, String java_path, long baseline_pid, String path) throws Exception
	{
		NMT_LogInfo nmt_log_info = NMT_LogInfo.read_status_log(baseline_pid, path);		
		NMT_Allocation[] allocations = NMT_Allocation.read_memory_log(baseline_pid, path);
		NMT_Thread[] threads = NMT_Thread.read_thread_log(baseline_pid, path, NMT_Allocation.getMainThreadId(allocations));
		NMT_Component[] components = NMT_Component.get();

		boolean consolidateMemory = true;
		if (consolidateMemory)
		{
			// apply "free" to the allocations to get the current picture of the memory
			NMT_Allocation.consolidate(allocations, mode.equals("All"));
		}

		AllocationHistogram histogram = new AllocationHistogram(allocations);
		if (mode.equals("All") || mode.equals("MemoryHistograms"))
		{
			print_memory_histograms(allocations, components, histogram);
		}

		if (mode.equals("All") || mode.equals("PerformanceHistograms"))
		{
            NMT_Duration[] durations = NMT_Duration.read_benchmark_log(baseline_pid, path);
			print_performance_histograms(durations);
		}

        if (mode.equals("All") || mode.equals("MemorySummary"))
		{
			// prints current summary of memory allocations
			MemoryStats totals = find_totals(allocations, threads);
			print_totals("Current summary of memory allocations by thread:", totals, threads);
		}

		MemoryStats total_stats = find_totals(allocations, components);
		if (mode.equals("All") || mode.equals("MemorySummary"))
		{
			print_totals("Current summary of memory allocations by component:", total_stats, components);
		}

		if (mode.equals("All") || mode.equals("MemorySummary"))
		{
			// prints total lifetime summary of memory allocations
			MemoryStats totals = find_totals(allocations, threads);
			print_totals("Total lifetime summary of memory allocations by thread:", totals, threads);
			totals = find_totals(allocations, components);
			print_totals("Total lifetime summary of memory allocations component:", totals, components);
		}

		print_summary(java_path, nmt_log_info, allocations, components, total_stats, histogram);
	
		// unrelated to benchmarking, but the following code shows bits of memory pointer that are unused by the os,
		// so they could be used for memory pointer coloring
		//print_memory_pointers_bits_coverage(allocations);

		return total_stats.requested_bytes;
	}

	private static void print_totals(String title, MemoryStats totals, Statistical[] items)
	{
		System.out.printf("%s\n", title);
		System.out.printf("-----------------------------------------------------------------------------------------------------------------------------\n");
		System.out.printf(String.format("%40s %10s %10s %10s %11s %12s %12s %13s\n", "name:", "mallocs:", "reallocs:", "frees:", "requested:", "allocated:", "overhead:", "overhead:"));
		System.out.printf(String.format("%40s %10s %10s %10s %11s %12s %12s %14s\n", " ", "(count)", "(count)", "(count)", "(bytes)", "(bytes)", "(bytes)", "(mem diff %%)"));
		System.out.printf("-----------------------------------------------------------------------------------------------------------------------------\n");
		for (int t = 0; t < items.length; t++)
		{
			Statistical item = items[t];
			MemoryStats stats = item.getStatistics();
			stats.print(item.toString());
		}
		System.out.printf("-----------------------------------------------------------------------------------------------------------------------------\n");
		totals.print("TOTALS:");
		System.out.printf("-------------------------------------------------------------------------\n");
		System.out.printf(String.format("%40s %,10d\n", "TOTALS:", totals.count()));
		System.out.printf("-----------------------------------------------------------------------------------------------------------------------------\n");
		System.out.print("\n");
	}

	private static MemoryStats find_totals(NMT_Allocation[] allocations, Statistical[] items)
	{
		if (items == null)
		{
			return new MemoryStats();
		}

		for (int j = 0; j < items.length; j++)
		{
			items[j].clearStatistics();	
		}

		boolean all = false;
		for (int i = 0; i < allocations.length; i++)
		{
			for (int j = 0; j < items.length; j++)
			{
				NMT_Allocation a = allocations[i];
				if (all || (a.is_active()))
				{
					items[j].addStatistics(allocations[i]);
				}
			}
		}

		Arrays.sort(items);

		MemoryStats totals = new MemoryStats();
		for (int t = 0; t < items.length; t++)
		{
			Statistical item = items[t];
			MemoryStats stats = item.getStatistics();
			totals.add(stats);
		}

		for (int t = 0; t < items.length; t++)
		{
			Statistical item = items[t];
			MemoryStats stats = item.getStatistics();
			stats.calculateOverheadPercentage(totals.overheadBytes());
		}
		totals.calculateOverheadPercentage(totals.overheadBytes());
		return totals;
	}
}
