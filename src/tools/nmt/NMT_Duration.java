package nmt;

import java.nio.file.Path;

public class NMT_Duration
{
    // in bytes for a single duration data in log file
    static int SIZE = (8+8+8+1);

    private long count = 1;

    private long duration;
    private long requested;
    private long actual;
    private byte type;

    static final public byte MALLOC_TYPE  = 1;
    static final public byte REALLOC_TYPE = 2;
    static final public byte FREE_TYPE    = 4;

    public NMT_Duration(long d, long r, long a, byte t)
    {
        this.duration = d;
        this.requested = r;
        this.actual = a;
        this.type = t;
    }

    public void add()
    {
        this.count++;
    }

    public long count()
    {
        return this.count;
    }

    public long duration()
    {
        return this.duration;
    }

    public long actual()
    {
        return this.actual;
    }

    public long requested()
    {
        return this.requested;
    }

    public byte type()
    {
        return this.type;
    }

    public boolean is_type(byte t)
    {
        return (this.type == t);
    }

    public boolean is_alloc()
    {
        return (this.type == MALLOC_TYPE);
    }

    public boolean is_realloc()
    {
        return (this.type == REALLOC_TYPE);
    }

    public boolean is_free()
    {
        return (this.type == FREE_TYPE);
    }

    @Override
    public String toString()
    {
        return "duration:"+this.duration+" requested:"+this.requested+" actual:"+this.actual+" type:"+this.type;
    }
    
    @Override
    public NMT_Duration clone()
    {
        return new NMT_Duration(this.duration, this.requested, this.actual, this.type);
    }

    static int size()
    {
        return SIZE;
    }

    static NMT_Duration get(LogFile log, int i)
    {
        int offset = 0;
        long duration = log.out.getLong((i*size())+offset); offset += 8;
        long requested = log.out.getLong((i*size())+offset); offset += 8;
        long actual = log.out.getLong((i*size())+offset); offset += 8;
        byte type = log.out.get((i*size())+offset); offset += 1;

        return new NMT_Duration(duration, requested, actual, type);
    }

    public static NMT_Duration[] read_benchmark_log(long pid, String path)
    {
        NMT_Duration[] elements = null;

        // hs_nmt_pid83457_benchmark.log
        String name = Path.of(path, "hs_nmt_pid"+pid+"_benchmark.log").toString();
        LogFile log = new LogFile(name);

        int elements_count = log.size() / NMT_Duration.size();
        //System.out.printf(String.format("number of recorded allocation operations:%,d\n", elements_count));

        if (elements_count > 0)
        {
            elements = new NMT_Duration[elements_count];
            for (int i = 0; i < elements_count; i++)
            {
                elements[i] = NMT_Duration.get(log, i);
                //System.out.println(elements[i]);
            }
        }

        log.close();
        log = null;

        return elements;
    }
}
