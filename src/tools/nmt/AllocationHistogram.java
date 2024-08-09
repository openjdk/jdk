package nmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class AllocationHistogram
{
    class HistogramValue
    {
        long requested;
        long actual;
        long count;
        long overhead;
        public HistogramValue(long r, long a)
        {
            this.requested = r;
            this.actual = a;
            this.count = 1;
            this.overhead = (a-r);
        }

        public void add(long r, long a)
        {
            this.count++;
            this.overhead += (a-r);
        }

        public long requested()
        {
            return this.requested;
        }

        public long actual()
        {
            return this.actual;
        }

        public long count()
        {
            return this.count;
        }

        public long overhead()
        {
            return this.overhead;
        }

        @Override public String toString()
        {
            return ""+this.count+" "+this.overhead;
        }
    }
    
    HashMap<Long, HistogramValue> map_requested;
    HashMap<Long, HistogramValue> map_actual;

    long max_count_requested = Long.MIN_VALUE;
    long max_count_actual = Long.MIN_VALUE;
    long max_overhead = Long.MIN_VALUE;
    long total_overhead;

    int nmt_flag;

    public AllocationHistogram(NMT_Allocation[] allocations)
    {
        this(allocations, NMT_Component.ALL);
    }
    public AllocationHistogram(NMT_Allocation[] allocations, int nmt_type)
    {
        this.total_overhead = 0;

        this.nmt_flag = nmt_type;
        this.map_requested = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
        this.map_actual = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
        for (int i = 0; i < allocations.length; i++)
        {
            NMT_Allocation a = allocations[i];
            if (a.active)
            {
                if ((this.nmt_flag == NMT_Component.ALL) || (this.nmt_flag == a.flags))
                {
                    long requested = a.requested;
                    long actual = a.actual;

                    HistogramValue v = this.map_requested.get(requested);
                    if (v == null)
                    {
                        this.map_requested.put(requested, new HistogramValue(requested, actual)); 
                    }
                    else
                    {
                        v.add(requested, actual);
                    }

                    v = this.map_actual.get(actual);
                    if (v == null)
                    {
                        this.map_actual.put(actual, new HistogramValue(requested, actual)); 
                    }
                    else
                    {
                        v.add(requested, actual);
                    }
                }
            }
        }

        long totalOverheadRequested = 0;
        ArrayList<Long> sizes = new ArrayList<>(this.map_requested.keySet());
        if (sizes.size() > 0)
        {
            Collections.sort(sizes);
            for (Long s : sizes)
            {
                HistogramValue v = this.map_requested.get(s);
                this.max_count_requested = Long.max(this.max_count_requested, v.count());
                long overhead = v.overhead();
                totalOverheadRequested += overhead;
            }
        }
        else
        {
            this.max_count_requested = 0;
        }

        long totalOverheadActual = 0;
        sizes = new ArrayList<>(this.map_actual.keySet());
        if (sizes.size() > 0)
        {
            Collections.sort(sizes);
            for (Long s : sizes)
            {
                HistogramValue v = this.map_actual.get(s);
                this.max_count_actual = Long.max(this.max_count_actual, v.count());
                long overhead = v.overhead();
                totalOverheadActual += overhead;
                this.max_overhead = Long.max(this.max_overhead, overhead);
            }
        }
        else
        {
            this.max_count_actual = 0;
            this.total_overhead = 0;
            this.max_overhead = 0;
        }
        
        if (totalOverheadRequested != totalOverheadActual)
        {
            throw new RuntimeException("totalOverheadRequested != totalOverheadActual ["+totalOverheadRequested+" != "+totalOverheadActual+"]");
        }
        this.total_overhead = totalOverheadRequested;
    }

    void printHeader(boolean memoryTypeActual, boolean countTypeSimple)
    {
        System.out.printf("Histogram of memory [NMT component: \""+NMT_Component.name(this.nmt_flag)+"\"] ");
        if (countTypeSimple)
        {
            System.out.printf("[count type: simple] ");
        }
        else
        {
            System.out.printf("[count type: ratio] ");
        }

        if (memoryTypeActual)
        {
            System.out.printf("[memory type: actual] ");
        }
        else
        {
            System.out.printf("[memory type: requested] ");
        }

        //boolean uses_linear = countTypeSimple;
        boolean uses_linear = false;
        if (uses_linear)
        {
            System.out.printf("[scale: linear]");
        }
        else
        {
            System.out.printf("[scale: quadratic]");
        }
        System.out.printf("\n");

        //printCounters(memoryTypeActual);

        if (countTypeSimple)
        {
            if (memoryTypeActual)
            {
                System.out.printf(String.format("%3s %11s %11s %9s\n", "#", " ", "actual:", "<count:>"));
            }
            else
            {
                System.out.printf(String.format("%3s %11s %11s %9s\n", "#", "requested:", "actual:", "<count:>"));
            }
        }
        else
        {
            if (memoryTypeActual)
            {
                System.out.printf(String.format("%3s %11s %11s %7s %10s %9s [ratio == overhead / total overhead]\n",
                            "#", " ", "actual:", "count:", "overhead:", "<ratio:>"));				}
            else
            {
                System.out.printf(String.format("%3s %11s %11s %7s %10s %9s [ratio == overhead / total overhead]\n",
                            "#", "requested:", "actual:", "count:", "overhead:", "<ratio:>"));				}
        }
    }

    void printLineGraph(double line, int width)
    {
        int l;
        for (l = 0; l < (int)line; l++)
        {
            System.out.printf("%c", Constants.HISTOGRAM_BIG_DOT_ASCII);
        }
        for (; l < width; l++)
        {
            System.out.printf("%c", Constants.HISTOGRAM_SMALL_DOT_ASCII);
        }
        System.out.printf("\n");
    }

    void printLine(boolean memoryTypeActual, boolean useCount, int width, int index, Long s)
    {
        HistogramValue v = null;
        if (memoryTypeActual)
        {
            v = this.map_actual.get(s);
        }
        else
        {
            v = this.map_requested.get(s);
        }
        long overhead_in_bytes = v.overhead(); // [0..this.max_overhead]
        double percentage;
        if (useCount)
        {
            double max = 0.0;
            if (memoryTypeActual)
            {
                max = this.max_count_actual;
            }
            else
            {
                max = this.max_count_requested;
            }
            percentage = 100.0 * ((double)v.count / (double)max); // [0..100]
        }
        else
        {
            percentage = 100.0 * (double)overhead_in_bytes / (double)this.total_overhead; // [0..100]
        }
        if (percentage > Constants.HISTOGRAM_LINE_CUTOUT)
        {
            double line = 0.0;
            double a = 0.0;
            double b = 0.0;

            if (useCount)
            {
                a = v.count;
                if (memoryTypeActual)
                {
                    System.out.printf(String.format("%3d %11s %,11d  %,7d  ", index, " ", v.actual(), v.count()));
                    b = this.max_count_actual;
                }
                else
                {
                    System.out.printf(String.format("%3d %,11d %,11d  %,7d  ", index, v.requested(), v.actual(), v.count()));
                    b = this.max_count_requested;
                }
            }
            else
            {
                if (memoryTypeActual)
                {
                    System.out.printf(String.format("%3d %11s %,11d %,7d %,10d  %7.3f  ", index, " ", v.actual(), v.count(), overhead_in_bytes, percentage));
                    b = this.max_count_actual;
                }
                else
                {
                    System.out.printf(String.format("%3d %,11d %,11d %,7d %,10d  %7.3f  ", index, v.requested(), v.actual(), v.count(), overhead_in_bytes, percentage));
                    b = this.max_count_requested;
                }
                a = overhead_in_bytes;
                b = this.max_overhead;
            }

            //boolean uses_linear = countTypeSimple;
            boolean uses_linear = false;
            if (uses_linear)
            {
                line = (a / b) * (double)width; // [0..width]
            }
            else
            {
                // quadratic function which goes through 3 points: (0,0) (25,50) (100,100)
                // https://www.mathepower.com/en/quadraticfunctions.php
                // y = -0.013*x*x + 2.333*x, x=[0..100], y=[0..100]
                double x = 100.0 * a / b; // [0..100]
                double y = Double.min(-0.013*x*x + 2.333*x, 100.0);
                line = (double)width * y / 100.0;
            }

            printLineGraph(line, width);
        }
    }

    public void print(boolean memoryTypeActual, boolean useCount, int width)
    {
        if (this.total_overhead != 0)
        {
            ArrayList<Long> sizes = null;
            if (memoryTypeActual)
            {
                sizes = new ArrayList<>(this.map_actual.keySet());
            }
            else
            {
                sizes = new ArrayList<>(this.map_requested.keySet());
            }
            Collections.sort(sizes);
            printHeader(memoryTypeActual, useCount);
            int index = 1;
            for (Long s : sizes)
            {
                printLine(memoryTypeActual, useCount, width ,index, s);
                index++;
            }
            System.out.printf("\n");
        }
    }

    void printKeyValues()
    {
        printCounters(false);
        ArrayList<Long> sizes = new ArrayList<>(this.map_requested.keySet());
        Collections.sort(sizes);
        int index = 1;
        for (Long s : sizes)
        {
            System.out.printf(String.format("index:%3d key:%,9d value: %12s\n", 
                                            index, s, this.map_requested.get(s)));
            index++;
        }			
        System.out.printf("\n");
    }

    void printCounters(boolean useActual)
    {
        HashMap<Long, HistogramValue> map = null;
        if (useActual)
        {
            map = this.map_actual;
            System.out.printf(String.format(" max_count_actual:%,d length:%,d total overhead:%,d\n", 
                                            this.max_count_actual, map.size(), this.total_overhead));
        }
        else
        {
            map = this.map_requested;
            System.out.printf(String.format(" max_count_requested:%,d length:%,d total overhead:%,d\n", 
                                            this.max_count_requested, map.size(), this.total_overhead));
        }
    }

    void printMemorySizes(boolean memoryTypeActual)
    {
        HashMap<Long, HistogramValue> map = null;
        ArrayList<Long> sizes = null;
        if (memoryTypeActual)
        {
            map = this.map_actual;
            sizes = new ArrayList<>(map.keySet());
            System.out.printf(String.format("Malloc allocation sizes [%,d]: ", sizes.size()));
        }
        else
        {
            map = this.map_requested;
            sizes = new ArrayList<>(map.keySet());
            System.out.printf(String.format("Malloc request sizes [%,d]: ", sizes.size()));
        }
        Collections.sort(sizes);
        for (Long s : sizes)
        {
            HistogramValue v = map.get(s);
            if (memoryTypeActual)
            {
                System.out.printf(String.format("%,d ", v.actual()));
            }
            else
            {
                System.out.printf(String.format("%,d ", v.requested()));
            }
        }			
        System.out.printf("\n\n");
    }

    void print(int memory_type, int count_type, boolean print_sizes, int width)
    {
        //printKeyValues();
        if ((count_type == COUNT_BOTH) || (count_type == COUNT_OVERHEAD))
        {
            if ((memory_type == MEMORY_BOTH) || (memory_type == MEMORY_REQUESTED))
            {
                print(true, false, width);
            }
            if ((memory_type == MEMORY_BOTH) || (memory_type == MEMORY_ACTUAL))
            {
                print(false, false, width);
            }
        }
        if ((count_type == COUNT_BOTH) || (count_type == COUNT_SIMPLE))
        {
            if ((memory_type == MEMORY_BOTH) || (memory_type == MEMORY_REQUESTED))
            {
                print(true, true, width);
            }
            if ((memory_type == MEMORY_BOTH) || (memory_type == MEMORY_ACTUAL))
            {
                print(false, true, width);
            }
        }
        if (print_sizes)
        {
            if ((memory_type == MEMORY_BOTH) || (memory_type == MEMORY_REQUESTED))
            {
                printMemorySizes(false);
            }
            if ((memory_type == MEMORY_BOTH) || (memory_type == MEMORY_ACTUAL))
            {
                printMemorySizes(true);
            }
        }
        System.out.println("");
    }

    MemoryStats estimateStatsNoNMT(String java_path, NMT_Allocation[] allocations, int overhead_per_malloc)
    {
        findMissingSizes(java_path, allocations, overhead_per_malloc);

        MemoryStats no_nmt_stats = new MemoryStats();
        for (int a = 0; a < allocations.length; a++)
        {
            NMT_Allocation alloc = allocations[a];
            if (alloc.is_active() && !alloc.is_free())
            {
                if (alloc.flags != NMT_Component.NATIVE_MEMORY_TRACKING)
                {
                    long requested = alloc.requested;
                    if (alloc.flags != NMT_Component.PREINIT)
                    {
                        requested -= overhead_per_malloc;
                    }
                    no_nmt_stats.requested_bytes += requested;
                    HistogramValue v = this.map_requested.get(requested);
                    if (v != null)
                    {
                        long actual = v.actual();
                        if (actual==0)
                        {
                            throw new RuntimeException("missing actual size for "+requested);
                        }
                        no_nmt_stats.allocated_bytes += actual;
                    }
                    else
                    {
                        throw new RuntimeException("missing HistogramValue for "+requested);
                    }
                }
            }
        }
        return no_nmt_stats;
    }

    // since we are pretending here that NMT is OFF, we might end up with allocation sizes
    // that we haven't had to use yet, so identify those and then ask the VM itself
    // (using NMTPrintMemoryAllocationsSizesFor) to find out the actual sizes for these estimated requests
    void findMissingSizes(String java_path, NMT_Allocation[] allocations, int overhead_per_malloc)
    {
        HashMap<Long, String> missing = new HashMap<>(256);
        for (int a = 0; a < allocations.length; a++)
        {
            NMT_Allocation alloc = allocations[a];
            if (alloc.is_active() && !alloc.is_free())
            {
                if (alloc.flags != NMT_Component.NATIVE_MEMORY_TRACKING)
                {
                    long requested = alloc.requested;
                    if (alloc.flags != NMT_Component.PREINIT)
                    {
                        requested -= overhead_per_malloc;
                    }
                    HistogramValue v = this.map_requested.get(requested);
                    if (v == null)
                    {
                        // collect allocation sizes that we have not recorded yet
                        missing.putIfAbsent(requested, Long.toString(requested));
                    }
                }
            }
        }

        if (missing.size() > 0)
        {
            ArrayList<Long> sizes = new ArrayList<>(missing.keySet());
            String list = "";
            for (Long s : sizes)
            {
                list += s;
                if (!s.equals(sizes.getLast()))
                {
                    list += ",";
                }
            }

            //System.err.println("list:["+list+"]");
            // send the list of missing allocations off to "java" process
            String[] cmd = {java_path, "-XX:+UnlockDiagnosticVMOptions", "-XX:NMTPrintMemoryAllocationsSizesFor="+list};
            String string = Execute.execCmd(cmd).output();
            String[] found = string.split(",");

            // add the newly found allocations
            for (int i = 0; i < found.length; i++)
            {
                long requested = sizes.get(i).longValue();
                long actual = Long.parseLong(found[i]);
                this.map_requested.put(requested, new HistogramValue(requested, actual));
            }
        }
    }

    static final int MEMORY_REQUESTED  = 1;
    static final int MEMORY_ACTUAL     = 2;
    static final int MEMORY_BOTH       = 3;
    static final int COUNT_SIMPLE      = 4;
    static final int COUNT_OVERHEAD    = 5;
    static final int COUNT_BOTH        = 6;
}