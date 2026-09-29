
# parallel vector enabled
echo "starting run" > completelog.log
# make CONF=linux-x86_64-server-release install-hsdis test TEST="micro:org.openjdk.bench.javax.crypto.full.AESBench.encryptCTR" MICRO="JAVA_OPTIONS=-XX:+UnlockDiagnosticVMOptions;FORK=1;ITER=3;TIME=10;WARMUP_ITER=12;WARMUP_TIME=10;OPTIONS=-prof perfasm -p algorithm=AES/CTR/NoPadding" 2>&1 | tee -a completelog.log
./build/linux-x86_64-server-release/images/jdk/bin/java -jar ./build/linux-x86_64-server-release/images/test/micro/benchmarks.jar -f 1 -i 3 -r 10 -wi 12 -w 10 -prof perfasm -p algorithm=AES/CTR/NoPadding -p dataSize=16,32,64,128,256,512,1024,2048,4096,8192,16384,32768,65536
 org.openjdk.bench.javax.crypto.full.AESBench 2>&1 | tee -a completelog.log

echo "starting run" > completelog-base.log
# make CONF=base install-hsdis test TEST="micro:org.openjdk.bench.javax.crypto.full.AESBench.encryptCTR" MICRO="JAVA_OPTIONS=-XX:+UnlockDiagnosticVMOptions;FORK=1;ITER=3;TIME=10;WARMUP_ITER=12;WARMUP_TIME=10;OPTIONS=-prof perfasm -p algorithm=AES/CTR/NoPadding" 2>&1 | tee -a completelog-base.log


./build/base/images/jdk/bin/java -jar ./build/base/images/test/micro/benchmarks.jar -f 1 -i 3 -r 10 -wi 12 -w 10 -prof perfasm -p algorithm=AES/CTR/NoPadding -p dataSize=16,32,64,128,256,512,1024,2048,4096,8192,16384,32768,65536
 org.openjdk.bench.javax.crypto.full.AESBench 2>&1 | tee -a completelog-base.log
