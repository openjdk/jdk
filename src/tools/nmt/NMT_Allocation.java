package nmt;

import java.nio.file.Path;

public class NMT_Allocation
{
    // in bytes for a single allocation data in log file
    static int SIZE = 88;

    // how many threads to use while processing the results
	static final int THREADS_COUNT = 8;

    long time;
    long thread;
    long ptr_curr;
    long ptr_prev;
    long stack1;
    long stack2;
    long stack3;
    long stack4;
    long requested;
    long actual;
    long flags;
    boolean active;

    NMT_Allocation()
    {

    }

    NMT_Allocation(NMT_Allocation copy)
    {
        this.time = copy.time;
        this.thread = copy.thread;
        this.ptr_curr = copy.ptr_curr;
        this.ptr_prev = copy.ptr_prev;
        this.stack1 = copy.stack1;
        this.stack2 = copy.stack2;
        this.stack3 = copy.stack3;
        this.stack4 = copy.stack4;
        this.requested = copy.requested;
        this.actual = copy.actual;
        this.flags = copy.flags;
        this.active = copy.active;
    }

    static int size()
    {
        return SIZE;
    }

    static long getMainThreadId(NMT_Allocation[] allocations)
    {
        return allocations[0].thread; // assumes that the 1st memory allocation is made from main thread
    }

    @Override public String toString()
    {
        String type = "[???????] ";
        if (is_free())
        {
            type = "   [free] ";
        }
        else if (is_malloc())
        {
            type = " [malloc] ";
        }
        else if (is_realloc())
        {
            type = "[realloc] ";
        }
        return new String(type+"0x"+Long.toHexString(time)+", "+thread+", 0x"+Long.toHexString(ptr_curr)+", 0x"+Long.toHexString(ptr_prev)+
        //","+Long.toHexString(stack1)+","+Long.toHexString(stack1)+","+Long.toHexString(stack1)+","+Long.toHexString(stack4)+
        ", "+requested+", "+actual+", "+NMT_Component.name(flags)+" ["+active+"]");
    }

    public void print()
    {
        System.out.println("Allocation:");
        System.out.println("      time: 0x"+Long.toHexString(time));
        System.out.println(" thread id: "+thread);
        System.out.println("       ptr: 0x"+Long.toHexString(ptr_curr));
        System.out.println("       old: 0x"+Long.toHexString(ptr_prev));
        System.out.println("    stack1: 0x"+Long.toHexString(stack1));
        System.out.println("    stack2: 0x"+Long.toHexString(stack2));
        System.out.println("    stack3: 0x"+Long.toHexString(stack3));
        System.out.println("    stack4: 0x"+Long.toHexString(stack4));
        System.out.println(" requested: "+requested);
        System.out.println("    actual: "+actual);
        System.out.println("     flags: 0b"+Long.toBinaryString(flags));
        System.out.println("    active: "+active);
        System.out.println("");
    }
    /*
        # xxd -l 88 hs_nmt_pid58895_allocs_record.log
        00000000: addd 9e42 cf3a 0000 0329 0000 0000 0000  ...B.:...)......
        00000010: 40c2 2d00 0060 0000 0000 0000 0000 0000  @.-..`..........
        00000020: feff ffff ffff ffff feff ffff ffff ffff  ................
        00000030: feff ffff ffff ffff feff ffff ffff ffff  ................
        00000040: 5400 0000 0000 0000 6000 0000 0000 0000  T.......`.......
        00000050: 1300 0000 0000 0000                      ........

        # xxd -l 88 -ps -c 8 hs_nmt_pid58895_allocs_record.log
        addd9e42cf3a0000
        0329000000000000
        40c22d0000600000
        0000000000000000
        feffffffffffffff
        feffffffffffffff
        feffffffffffffff
        feffffffffffffff
        5400000000000000
        6000000000000000
        1300000000000000
        */
    static NMT_Allocation get(LogFile log, int i)
    {
        NMT_Allocation ai = new NMT_Allocation();

        int offset = 0;
        ai.time = log.out.getLong((i*size())+offset); offset += 8;
        ai.thread = log.out.getLong((i*size())+offset); offset += 8;
        ai.ptr_curr = log.out.getLong((i*size())+offset); offset += 8;
        ai.ptr_prev = log.out.getLong((i*size())+offset); offset += 8;
        ai.stack1 = log.out.getLong((i*size())+offset); offset += 8;
        ai.stack2 = log.out.getLong((i*size())+offset); offset += 8;
        ai.stack3 = log.out.getLong((i*size())+offset); offset += 8;
        ai.stack4 = log.out.getLong((i*size())+offset); offset += 8;
        ai.requested = log.out.getLong((i*size())+offset); offset += 8;
        ai.actual = log.out.getLong((i*size())+offset); offset += 8;
        ai.flags = log.out.getLong((i*size())+offset); offset += 8;
        ai.active = true;

        return ai;
    }

    public void deactivate()
    {
        this.active = false;
    }

    public boolean is_active()
    {
        return this.active;
    }

    public boolean is_free()
    {
        return is_active() && (requested == 0L) && (ptr_prev == 0L);
    }

    public boolean is_realloc()
    {
        return is_active() && (requested > 0L) && (ptr_prev != 0L);
    }

    public boolean is_malloc()
    {
        return is_active() && (requested > 0L) && (ptr_prev == 0L);
    }
    //  static bool is_active(Entry* e)   { return (e->active == 1); };
    //  static void deactivate(Entry* e)  { e->active = 0; };
    //  static bool is_type_nmt(Entry* e) { return (e->flags == MEMFLAGS::mtNMT); };
    //  static bool is_free(Entry* e)     { return (e->requested == 0) && (e->old == nullptr); };
    //  static bool is_realloc(Entry* e)  { return (e->requested > 0)  && (e->old != nullptr); };
    //  static bool is_malloc(Entry* e)   { return (e->requested > 0)  && (e->old == nullptr); };
    //  static bool is_alloc(Entry* e)    { return is_malloc(e) || is_realloc(e); };

    public static NMT_Allocation[] read_memory_log(long pid, String path)
    {
        NMT_Allocation[] elements = null;

        String name = Path.of(path, "hs_nmt_pid"+pid+"_allocs_record.log").toString();
        LogFile log = new LogFile(name);

        int elements_count = log.size() / NMT_Allocation.size();
        //System.out.printf(String.format("number of recorded allocation operations:%,d\n", elements_count));

        if (elements_count > 0)
        {
            elements = new NMT_Allocation[elements_count];
            for (int i = 0; i < elements_count; i++)
            {
                elements[i] = NMT_Allocation.get(log, i);
                //System.out.println(elements[i]);
            }
        }

        log.close();
        log = null;

        return elements;
    }

    // multithreaded
    static void consolidateChunk(NMT_Allocation[] allocations, int offset, int stripe, boolean full)
	{
		int start = (allocations.length-1) - offset;
		long stamp = System.currentTimeMillis();
		for (int i = start; i >= 0; i -= stripe)
		{
			NMT_Allocation a = allocations[i];
			// look for a "free" operation, then walk backwards and remove 
			// (deactivate) all "realloc" and the originating alloc
			// (which could be another "realloc" or just "malloc") in this chain
			if (a.is_free())
			{
				//System.err.println("FREE chain:");
				boolean found = false;
				long ptr = a.ptr_curr;
				for (int j = (i-1); j >= 0; j--)
				{
					if (full && (offset == (stripe-1)))
					{
						long now = System.currentTimeMillis();
						if (now-stamp > 1000)
						{
							double o = allocations.length;
							double progress = (100.0 * ((((o-i)*o) + (o-j)) / (o*o)));
							System.out.printf(String.format("%.2f, ", progress));
							stamp = now;
						}
					}
					NMT_Allocation b = allocations[j];
					if (b.is_active() && b.ptr_curr == ptr)
					{
						if (b.is_malloc())
						{
							//System.err.println(" MALLOC");
							found = true;
							b.deactivate();
							break;
						}
						else if (b.is_realloc())
						{
							//System.err.println(" REALLOC");
							found = true;
							b.deactivate();
							ptr = b.ptr_prev; // realloc --> use previous pointer to go backwards in this chain
						}
					}
				}
				if (!found)
				{
					if (a.ptr_curr != 0)
					{
						//throw new RuntimeException("Untracked FREE? "+a);
						System.err.println("Untracked FREE?\n "+a);
					}
				}
				a.deactivate();
			}
		}
		if (full && (offset == (stripe-1)))
		{
			System.out.printf(String.format("\n"));
		}
	}

	public static void consolidate(NMT_Allocation[] allocations, boolean full)
	{
		if (full)
		{
			System.out.println("Consolidating memory...\n");
		}

		Thread[] threads = new Thread[THREADS_COUNT];
		for (int i = 0; i < THREADS_COUNT; i++)
		{
			final int offset = i;
			Runnable runnable = () ->
			{
				consolidateChunk(allocations, offset, THREADS_COUNT, full);
			};
			threads[i] = new Thread(runnable);
			threads[i].start();
		}
		try
		{
			for (int i = 0; i < THREADS_COUNT; i++)
			{
				threads[i].join();
			}
		}
		catch (InterruptedException e)
		{
			System.err.println(e);
		}

		// remove all mallocs and reallocs except the very last realloc
		// in the chain
		for (int i = (allocations.length-1); i >= 0; i--)
		{
			NMT_Allocation a = allocations[i];
			if (a.is_realloc())
			{
				long ptr = a.ptr_prev; // realloc --> use previous pointer to go backwards in this chain
				//System.err.println("REALLOC chain: "+a);
				for (int j = (i-1); j >= 0; j--)
				{
					NMT_Allocation b = allocations[j];
					if (b.ptr_curr == ptr)
					{
						if (b.is_malloc())
						{
							//System.err.println(" MALLOC");
							b.deactivate();
							break;
						}
						else if (b.is_realloc())
						{
							//System.err.println(" REALLOC");
							b.deactivate();
							ptr = b.ptr_prev; // realloc --> use previous pointer to go backwards in this chain
						}
					}
				}
			}
		}

		int count = 0;
		for (int i = 0; i < allocations.length; i++)
		{
			NMT_Allocation a = allocations[i];
			if (a.is_active())
			{
				count++;
			}
		}

		if (full)
		{
			System.out.printf(String.format("After memory consolidation the memory operations count is %,d was %,d - a difference of %,d allocations\n\n", 
								count, allocations.length, (allocations.length-count)));
		}
	}
}