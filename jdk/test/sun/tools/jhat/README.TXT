#

jhat heap dump parsing tests:

There are three hprof binary format dump files in this directory.
These dumps were created by jmap and hprof profiler against a
simple infinite looping Java program.

1. minimal.bin - minimal dump that has nothing! - not even java.lang.Class!
  - This was created by java -Xrunhprof:format=b,heap=sites MainClass. 

2. jmap.bin - created by jmap -dump option

3. hprof.bin - created by java -Xrunhprof:heap=all,format=b MainClass

We can run jhat -parseonly true <dump-file> against these dumps. 
