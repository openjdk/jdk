package nmt;

public class NMT_Component implements Statistical, Comparable<NMT_Component>
{
    static public final int JAVA_HEAP = 0;
    static public final int CLASS = JAVA_HEAP+1;
    static public final int THREAD = CLASS+1;
    static public final int THREAD_STACK = THREAD+1;
    static public final int CODE = THREAD_STACK+1;
    static public final int GC = CODE+1;
    static public final int GC_CARD_SET = GC+1;
    static public final int COMPILER = GC_CARD_SET+1;
    static public final int JVMCI = COMPILER+1;
    static public final int INTERNAL = JVMCI+1;
    static public final int OTHER = INTERNAL+1;
    static public final int SYMBOL = OTHER+1;
    static public final int NATIVE_MEMORY_TRACKING = SYMBOL+1;
    static public final int SHARED_CLASS_SPACE = NATIVE_MEMORY_TRACKING+1;
    static public final int ARENA_CHUNK = SHARED_CLASS_SPACE+1;
    static public final int TEST = ARENA_CHUNK+1;
    static public final int TRACING = TEST+1;
    static public final int LOGGING = TRACING+1;
    static public final int STATISTICS = LOGGING+1;
    static public final int ARGUMENTS = STATISTICS+1;
    static public final int MODULE = ARGUMENTS+1;
    static public final int SAFEPOINT = MODULE+1;
    static public final int SYNCHRONIZATION = SAFEPOINT+1;
    static public final int SERVICEABILITY = SYNCHRONIZATION+1;
    static public final int METASPACE = SERVICEABILITY+1;
    static public final int STRING_DEDUPLICATION = METASPACE+1;
    static public final int OBJECT_MONITORS = STRING_DEDUPLICATION+1;
    static public final int UNKNOWN = OBJECT_MONITORS+1;
    static public final int PREINIT = UNKNOWN+1;
    static public final int ALL = PREINIT+1;
    static private final String names[] = {"Java Heap", "Class", "Thread", "Thread Stack", "Code", 
    "GC", "GCCardSet", "Compiler", "JVMCI", "Internal", "Other", "Symbol", "Native Memory Tracking", 
    "Shared class space", "Arena Chunk", "Test", "Tracing", "Logging", "Statistics", "Arguments", 
    "Module", "Safepoint", "Synchronization", "Serviceability", "Metaspace", "String Deduplication", 
    "Object Monitors", "Unknown", "Pre Init", "All"};
    int flag;
    MemoryStats stats;

    public NMT_Component(int f)
    {
        this.flag = f;
        this.stats = new MemoryStats();
    }

    public static NMT_Component[] get()
    {
        NMT_Component[] components = new NMT_Component[names.length-1];
        for (int n = 0; n < names.length-1; n++)
        {
            components[n] = new NMT_Component(n);
        }
        return components;
    }

    public static String name(long f)
    {
        return name((int)f);
    }

    public static String name(int f)
    {
        return names[f];
    }

    public String toString()
    {
        return names[this.flag];
    }

    @Override public MemoryStats getStatistics()
    {
        return this.stats;
    }

    @Override public void clearStatistics()
    {
        this.stats = new MemoryStats();
    }

    @Override public boolean addStatistics(NMT_Allocation ai)
    {
        if (this.flag == ai.flags)
        {
            return this.stats.process(ai);
        }
        else
        {
            return false;
        }
    }

    @Override public int compareTo(NMT_Component c)
    {
        return (int)(this.getStatistics().compareTo(c.getStatistics()));
    }
}
