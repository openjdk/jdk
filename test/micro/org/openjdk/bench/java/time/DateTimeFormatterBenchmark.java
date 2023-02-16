@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class DateTimeFormatterBenchmark {

    private static final DateTimeFormatter df = new DateTimeFormatterBuilder().appendPattern("yyyy:MM:dd:HH:mm:v").toFormatter();
    private static final String TEXT = "2015:03:10:12:13:ECT";

    @Setup
    public void setUp() {
        ZonedDateTime.parse(TEXT, df);
    }

    @Benchmark
    public ZonedDateTime parse() {
        return ZonedDateTime.parse(TEXT, df);
    }
}