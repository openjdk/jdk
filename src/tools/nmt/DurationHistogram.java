package nmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import javax.xml.validation.Validator;

public class DurationHistogram
{
    HashMap<Long, NMT_Duration> size_count_malloc;
    long size_count_malloc_max = 0;
    long size_count_malloc_total = 0;

    HashMap<Long, NMT_Duration> size_count_realloc;
    long size_count_realloc_max = 0;
    long size_count_realloc_total = 0;

    HashMap<Long, NMT_Duration> duration_count_malloc;
    long duration_count_malloc_max = 0;
    long duration_count_malloc_total = 0;

    HashMap<Long, NMT_Duration> duration_count_realloc;
    long duration_count_realloc_max = 0;
    long duration_count_realloc_total = 0;

    HashMap<Long, NMT_Duration> duration_count_free;
    long duration_count_free_max = 0;
    long duration_count_free_total = 0;

    Object durations_count_size_malloc[];
    long durations_count_size_malloc_max[];
    long durations_count_size_malloc_total[];

    Object durations_count_size_realloc[];
    long durations_count_size_realloc_max[];
    long durations_count_size_realloc_total[];

    static long initMapCounts(NMT_Duration[] durations, HashMap<Long, NMT_Duration> map, byte type)
    {
        long max = 0;
        for (int i = 0; i < durations.length; i++)
        {
            NMT_Duration d = durations[i];
            if (d.is_type(type))
            {
                NMT_Duration v = map.get(d.actual());
                if (v == null)
                {
                    v = d.clone();
                    map.put(d.actual(), v); 
                }
                else
                {
                    v.add();
                }
                max = Long.max(max, v.count());
            }
        }
        return max;
    }

    static long initMap(NMT_Duration[] durations, HashMap<Long, NMT_Duration> map, byte type)
    {
        return initMap(durations, map, type, 0);
    }
    static long initMap(NMT_Duration[] durations, HashMap<Long, NMT_Duration> map, byte type, long actual)
    {
        long max = 0;
        for (int i = 0; i < durations.length; i++)
        {
            NMT_Duration d = durations[i];
            if (d.is_type(type))
            {
                if ((actual == 0) || (d.actual() == actual))
                {
                    NMT_Duration v = map.get(d.duration());
                    if (v == null)
                    {
                        v = d.clone();
                        map.put(d.duration(), v);
                    }
                    else
                    {
                        v.add();
                    }
                    max = Long.max(max, v.count());
                }
            }
        }
        return max;
    }

    static long countTotal(HashMap<Long, NMT_Duration> map)
    {
        long total = 0;
        ArrayList<Long> keys = new ArrayList<>(map.keySet());
        for (int i = 0; i < keys.size(); i++)
        {
            long duration = keys.get(i);
            NMT_Duration d = map.get(duration);
            total += d.count();
        }
        return total;
    }

    public DurationHistogram(NMT_Duration[] durations)
    {
        // for (int i = 0; i < durations.length; i++)
        // {
        //     NMT_Duration d = durations[i];
        //     System.err.println(d);
        // }

        this.size_count_malloc = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
        this.size_count_malloc_max = initMapCounts(durations, this.size_count_malloc, NMT_Duration.MALLOC_TYPE);
        this.size_count_malloc_total = countTotal(this.size_count_malloc);

        this.size_count_realloc = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
        this.size_count_realloc_max = initMapCounts(durations, this.size_count_realloc, NMT_Duration.REALLOC_TYPE);
        this.size_count_realloc_total = countTotal(this.size_count_realloc);

        this.duration_count_malloc = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
        this.duration_count_malloc_max = initMap(durations, this.duration_count_malloc, NMT_Duration.MALLOC_TYPE);
        this.duration_count_malloc_total = countTotal(this.duration_count_malloc);
        
        this.duration_count_realloc = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
        this.duration_count_realloc_max = initMap(durations, this.duration_count_realloc, NMT_Duration.REALLOC_TYPE);
        this.duration_count_realloc_total = countTotal(this.duration_count_realloc);

        this.duration_count_free = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
        this.duration_count_free_max = initMap(durations, this.duration_count_free, NMT_Duration.FREE_TYPE);
        this.duration_count_free_total = countTotal(this.duration_count_free);

        ArrayList<Long> keys = null;

        keys = new ArrayList<>(this.size_count_malloc.keySet());
        Collections.sort(keys);
        this.durations_count_size_malloc = new Object[keys.size()];
        this.durations_count_size_malloc_max = new long[keys.size()];
        this.durations_count_size_malloc_total = new long[keys.size()];
        for (int i = 0; i < this.durations_count_size_malloc.length; i++)
        {
            HashMap<Long, NMT_Duration> h = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
            this.durations_count_size_malloc[i] = h;
            this.durations_count_size_malloc_max[i] = initMap(durations, h, NMT_Duration.MALLOC_TYPE, keys.get(i));
            this.durations_count_size_malloc_total[i] = countTotal(h);
        }

        keys = new ArrayList<>(this.size_count_realloc.keySet());
        Collections.sort(keys);
        this.durations_count_size_realloc = new Object[keys.size()];
        this.durations_count_size_realloc_max = new long[keys.size()];
        this.durations_count_size_realloc_total = new long[keys.size()];
        for (int i = 0; i < this.durations_count_size_realloc.length; i++)
        {
            HashMap<Long, NMT_Duration> h = new HashMap<>(Constants.HISTOGRAM_BUCKETS);
            this.durations_count_size_realloc[i] = h;
            this.durations_count_size_realloc_max[i] = initMap(durations, h, NMT_Duration.REALLOC_TYPE, keys.get(i));
            this.durations_count_size_realloc_total[i] = countTotal(h);
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

    void printLine(double percentage, long count, long max, int width)
    {
        double line = 0.0;
        double a = count;
        double b = max;

        boolean uses_linear = true;
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

    void printMapCounts(int width, HashMap<Long, NMT_Duration> map, long max)
    {
        System.out.printf("idx:         size:      count:\n");
        int index = 1;
        ArrayList<Long> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (Long k : keys)
        {
            NMT_Duration v = map.get(k);
            double percentage = 100.0 * ((double)v.count() / (double)max); // [0..100]
            if (percentage > Constants.HISTOGRAM_LINE_CUTOUT)
            {
                System.out.printf(String.format("%4d   %,11d     %,7d  ", index, v.actual(), v.count()));
                printLine(percentage, v.count(), max, width);
            }
            index++;
        }
        System.out.printf("\n");
    }

    void printMap(int width, Object m, long max)
    {
        HashMap<Long, NMT_Duration> map = (HashMap<Long, NMT_Duration>)m;
        System.out.printf("idx:     duration:      count:\n");
        @SuppressWarnings("unchecked")
        HashMap<Long, NMT_Duration> h = (HashMap<Long, NMT_Duration>)map;
        ArrayList<Long> keys = new ArrayList<>(h.keySet());
        Collections.sort(keys);
        int index = 1;
        for (Long k : keys)
        {
            NMT_Duration v = h.get(k);
            double percentage = 100.0 * ((double)v.count() / (double)max); // [0..100]
            if (percentage > Constants.HISTOGRAM_LINE_CUTOUT)
            {
                System.out.printf(String.format("%4d   %,11d     %,7d  ", index, v.duration(), v.count()));
                printLine(percentage, v.count(), max, width);
            }
            index++;
        }
        System.out.printf("\n");
    }

    public void printLines(int width)
    {
        System.out.printf(String.format("summary \"free\" histogram of durations & count [#%,d]:\n",
            this.duration_count_free_total));
        printMap(width, this.duration_count_free, this.duration_count_free_max);

        System.out.printf(String.format("summary \"malloc\" histogram of durations & count [#%,d]:\n",
            this.duration_count_malloc_total));
        printMap(width, this.duration_count_malloc, this.duration_count_malloc_max);
    
        System.out.printf(String.format("summary \"realloc\" histogram of durations & count [#%,d]:\n",
            this.duration_count_realloc_total));
        printMap(width, this.duration_count_realloc, this.duration_count_realloc_max);

        System.out.printf(String.format("summary \"malloc\" histogram of sizes & count [#%,d]:\n",
            this.size_count_malloc_total));
        printMapCounts(width, this.size_count_malloc, this.size_count_malloc_max);

        System.out.printf(String.format("summary \"realloc\" histogram of sizes & count [#%,d]:\n",
            this.size_count_realloc_total));
        printMapCounts(width, this.size_count_realloc, this.size_count_realloc_max);
    
        ArrayList<Long> keys = null;

        keys = new ArrayList<>(this.size_count_malloc.keySet());
        Collections.sort(keys);
        for (int i = 0; i < this.durations_count_size_malloc.length; i++)
        {
            System.out.printf(String.format("detail \"malloc\" histogram of durations & count for size %,d [#%,d]:\n", 
                keys.get(i), this.durations_count_size_malloc_total[i]));
            printMap(width, this.durations_count_size_malloc[i], this.durations_count_size_malloc_max[i]);
        }

        keys = new ArrayList<>(this.size_count_realloc.keySet());
        Collections.sort(keys);
        for (int i = 0; i < this.durations_count_size_realloc.length; i++)
        {
            System.out.printf(String.format("detail \"realloc\" histogram of durations & count for size %,d [#%,d]:\n", 
                keys.get(i), this.durations_count_size_realloc_total[i]));
            printMap(width, this.durations_count_size_realloc[i], this.durations_count_size_realloc_max[i]);
        }
    }

    void printKeyValues()
    {
        printCounters();
        ArrayList<Long> sizes = new ArrayList<>(this.size_count_malloc.keySet());
        Collections.sort(sizes);
        int index = 1;
        for (Long s : sizes)
        {
            System.out.printf(String.format("index:%3d key:%,9d value: %12s\n", 
                                            index, s, this.size_count_malloc.get(s)));
            index++;
        }			
        System.out.printf("\n");
    }

    void printCounters()
    {
        System.out.printf(String.format(" max_count:%,d length:%,d total\n", 
                                        this.size_count_malloc_max, this.size_count_malloc.size()));
    }

    void printMemorySizes()
    {
        ArrayList<Long> sizes = new ArrayList<>(this.size_count_malloc.keySet());
        System.out.printf(String.format("----> Malloc allocation sizes [%,d]: ", sizes.size()));
        Collections.sort(sizes);
        for (Long s : sizes)
        {
            NMT_Duration v = this.size_count_malloc.get(s);
            System.out.printf(String.format("%,d ", v.actual()));
        }			
        System.out.printf("\n\n");
    }

    void print(int width)
    {
        //printKeyValues();
        //printMemorySizes();
        printLines(width);
        System.out.println("");
    }
}