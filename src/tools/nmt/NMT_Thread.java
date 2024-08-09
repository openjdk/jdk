package nmt;

import java.nio.file.Path;

public class NMT_Thread implements Statistical, Comparable<NMT_Thread>
{
    static int SIZE = 40;
    static byte[] BYTES = new byte[32];
    
    String name;
    long id;
    MemoryStats stats;

    public NMT_Thread()
    {
        super();
    }
    public NMT_Thread(long main)
    {
        this();
        this.name = "Main";
        this.id = main;
    }

    static public int size()
    {
        return SIZE;
    }

    public String toString()
    {
        return this.name;
    }

    public void print()
    {
        System.out.println("NMT_Thread:");
        System.out.println(" name: \""+this.name+"\"");
        System.out.println("   id: "+this.id);
        System.out.println("");
    }
    /*
        # xxd -l 40 hs_nmt_pid58895_threads_record.log
        00000000: 4743 2054 6872 6561 6423 3000 0000 0000  GC Thread#0.....
        00000010: 0000 0000 0000 0000 0000 0000 0000 0000  ................
        00000020: 0337 0000 0000 0000                      .7......

        # xxd -l 40 -ps -c 8 hs_nmt_pid58895_threads_record.log
        4743205468726561
        6423300000000000
        0000000000000000
        0000000000000000
        0337000000000000
    */
    public static NMT_Thread get(LogFile log, int i)
    {
        NMT_Thread ti = new NMT_Thread();

        int offset = i*size();
        for (int j = 0; j < 32; j++)
        {
            NMT_Thread.BYTES[j] = log.out.get(offset++);
        }
        int length = 32;
        for (int j = 31; j >= 0; j--)
        {
            if (NMT_Thread.BYTES[j] != 0)
            {
                break;
            }
            else
            {
                length--;
            }
        }
        
        ti.name = new String(NMT_Thread.BYTES, 0, length);
        ti.id = log.out.getInt(offset);

        return ti;
    }

    @Override public boolean addStatistics(NMT_Allocation ai)
    {
        if (this.id == ai.thread)
        {
            return this.stats.process(ai);
        }
        else
        {
            return false;
        }
    }

    @Override public MemoryStats getStatistics()
    {
        return this.stats;
    }

    @Override public void clearStatistics()
    {
        this.stats = new MemoryStats();
    }

    @Override public int compareTo(NMT_Thread t)
    {
        return (int)(this.getStatistics().compareTo(t.getStatistics()));
    }

    public static NMT_Thread[] read_thread_log(long pid, String path, long main_thread_id)
    {
        NMT_Thread[] elements = null;
        
        String name = Path.of(path, "hs_nmt_pid"+pid+"_threads_record.log").toString();
        LogFile log = new LogFile(name);
        // for (int i = 0; i < 10; i++)
        // {
        // 	log.print(i);
        // }

        int elements_count = (log.size() / NMT_Thread.size());
        //System.out.println("number of recorded threads: "+elements_count+" (plus the main thread)");

        if (elements_count > 0)
        {
            elements = new NMT_Thread[elements_count+1];
            elements[0] = new NMT_Thread(main_thread_id);
            for (int i = 0; i < elements_count; i++)
            {
                elements[i+1] = NMT_Thread.get(log, i);
                if (i < 3)
                {
                    //elements[i].print();
                }
            }
        }

        log.close();
        log = null;
                
        return elements;
    }
}
