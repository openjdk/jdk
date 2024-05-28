package nmt;

public interface Statistical
{
    public boolean addStatistics(NMT_Allocation ai);
    public void clearStatistics();
    public MemoryStats getStatistics();
}
