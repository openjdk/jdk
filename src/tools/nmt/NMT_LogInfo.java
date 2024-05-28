package nmt;

import java.nio.file.Path;

public class NMT_LogInfo
{
    static public final int UNKNOWN = 0;
    static public final int OFF = UNKNOWN+1;
    static public final int SUMMARY = OFF+1;
    static public final int DETAIL = SUMMARY+1;
    private final String names[] = {"NMT_unknown", "NMT_off", "NMT_summary", "NMT_detail"};
    private final int level;
    private final int overhead;
    
    static public NMT_LogInfo read_status_log(long pid, String path)
    {
        return new NMT_LogInfo(pid, path);
    }
    
    public NMT_LogInfo(long pid, String path)
    {
        String name = Path.of(path, "hs_nmt_pid"+pid+"_info_record.log").toString();
        LogFile log = new LogFile(name);

        /*
            00000000: 0300 0000 0000 0000 1000 0000 0000 0000  ................
            */
        this.level = log.out.getInt(0);
        this.overhead = log.out.getInt(8);
        //System.err.println("this.level:"+this.level);
        //System.err.println("this.overhead:"+this.overhead);

        log.close();
        log = null;
    }

    public String levelAsString()
    {
        return names[level()];
    }

    public int level()
    {
        return this.level;
    }

    public int overhead()
    {
        return this.overhead;
    }

    public boolean equals(int l)
    {
        return (level() == l);
    }
}