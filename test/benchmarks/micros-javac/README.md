# Javac microbenchmarks

The Javac Microbenchmarks is a collection of microbenchmarks for measuring
the performance of Javac API using the
[JMH](http://openjdk.java.net/projects/code-tools/jmh/) framework.


## Building and running the project

Currently, the project can be built and run with JDK 9 and later. This is
a Maven project and is built by:

    $ mvn clean install

After building, the executable jar is target/micros-javac-[version].jar.
Run the benchmarks with:

    $ java -jar target/micros-javac-*.jar [optional jmh parameters]

See the entire list of benchmarks using:

    $ java -jar target/micros-javacs-*.jar -l [optional regex to select benchmarks]

For example:

    $ java -jar target/micros-javac-1.0-SNAPSHOT.jar -l
    Benchmarks:
    org.openjdk.bench.langtools.javac.GroupJavacBenchmark.coldGroup
    org.openjdk.bench.langtools.javac.GroupJavacBenchmark.hotGroup
    org.openjdk.bench.langtools.javac.SingleJavacBenchmark.compileCold
    org.openjdk.bench.langtools.javac.SingleJavacBenchmark.compileHot

And the same regex syntax works to run some test:

    $ java -jar target/micros-javac-1.0-SNAPSHOT.jar SingleJavacBenchmark.compileHot

## Troubleshooting

### Build of micros-javac module got stuck

If you build got stuck on `[get] Getting: https://download.java.net/openjdk/jdk11/ri/openjdk-11+28_windows-x64_bin.zip` then you are probably experiencing some networking or web proxy obstacles.

One solution is to download required reference JDK from [https://download.java.net/openjdk/jdk11/ri/openjdk-11+28_windows-x64_bin.zip](https://download.java.net/openjdk/jdk11/ri/openjdk-11+28_windows-x64_bin.zip) manually and then build the project with property pointing to the local copy:

    $ mvn clean install -Djavac.benchmark.openjdk.zip.download.url=file:///<your download location>/openjdk-11+28_windows-x64_bin.zip

Note: Please use `openjdk-11+28_windows-x64_bin.zip` to build the project no matter what target platform is.

Another solution might be to add proxy settings:

    $ mvn -Dhttps.proxyHost=... -Dhttps.proxyPort=... clean install

### Execution of micros-javac benchmarks takes several hours

micros-javac benchmarks consist of two sets of benchmarks:
 * `SingleJavacBenchmark` (which is parametrized) measures each single javac compilation stage in an isolated run. This benchmark is designed for exact automated performance regression testing and it takes several hours to execute completely.
 * `GroupJavacBenchmark` is grouping the measurements of all javac compilation stages into one run and its execution should take less than 30 minutes on a regular developers computer.

Solution to speed up javac benchmarking is to select only `GroupJavacBenchmark` for execution using following command line:

    $ java -jar target/micros-javac-1.0-SNAPSHOT.jar .*GroupJavacBenchmark.*
