package nmt;

public class MemoryStats implements Comparable<MemoryStats>
{
    long mallocs_count;
    long reallocs_count;
    long free_count;
    long requested_bytes;
    long allocated_bytes;
    long overheadBytes;
    double overheadPercentage;

    public void add(MemoryStats stats)
    {
        this.mallocs_count += stats.mallocs_count;
        this.reallocs_count += stats.reallocs_count;
        this.free_count += stats.free_count;
        this.requested_bytes += stats.requested_bytes;
        this.allocated_bytes += stats.allocated_bytes;
    }

    public void calculateOverheadPercentage(long total)
    {
        if (total > 0)
        {
            this.overheadPercentage = (100.0 * ((double)overheadBytes()/(double)total));
        }
    }

    public boolean process(NMT_Allocation ai)
    {
        boolean processed = false;
        if (ai.is_free())
        {
            processed = true;
            this.free_count++;
        }
        else if (ai.is_realloc())
        {
            processed = true;
            this.reallocs_count++;
            this.requested_bytes += ai.requested;
            this.allocated_bytes += ai.actual;
        }
        else if (ai.is_malloc())
        {
            processed = true;
            this.mallocs_count++;
            this.requested_bytes += ai.requested;
            this.allocated_bytes += ai.actual;
        }
        return processed;
    }

    public long overheadBytes()
    {
        this.overheadBytes = (this.allocated_bytes-this.requested_bytes);
        return this.overheadBytes;
    }

    public double overheadPercentage()
    {
        return this.overheadPercentage;
    }

    public long count()
    {
        return this.mallocs_count+this.reallocs_count+this.free_count;
    }

    public void print(String item)
    {
        System.out.printf(String.format("%40s ", item));
        System.out.print(String.format("%,10d ", this.mallocs_count));
        System.out.print(String.format("%,10d ", this.reallocs_count));
        System.out.print(String.format("%,10d ", this.free_count));
        System.out.print(String.format("%,11d ", this.requested_bytes));
        System.out.print(String.format("%,12d ", this.allocated_bytes));
        System.out.print(String.format("%,12d ", overheadBytes()));
        System.out.print(String.format("%,13.3f  ", overheadPercentage()));
        System.out.print("\n");
    }

    @Override public int compareTo(MemoryStats s)
    {
        int comparison = (int)(s.overheadBytes() - overheadBytes());
        if (comparison == 0)
        {
            comparison = (int)(s.allocated_bytes - allocated_bytes);
        }
        return comparison;
    }
}