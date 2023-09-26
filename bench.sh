JAVAB=../base-p256/jdk/bin/java
JAVA1="./build/linux-x86_64-server-fastdebug/images/jdk/bin/java -XX:+UnlockDiagnosticVMOptions"
JAVA2="./build/linux-x86_64-server-fastdebug/images/jdk/bin/java -XX:+UnlockDiagnosticVMOptions -XX:+UseIntPolyIntrinsics"

# $JAVA1 -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -jar ./build/linux-x86_64-server-fastdebug/images/test/micro/benchmarks.jar -f 1 -i 3 -wi 6 -r 100000 -w 5 -p montgomery=true SignatureBench.ECDSA.sign
ECDSA_CMD="-jar ./build/linux-x86_64-server-fastdebug/images/test/micro/benchmarks.jar -f 1 -i 3 -wi 5 -r 10 -w 5 -p montgomery=true SignatureBench.ECDSA"
ECDHE_CMD="-jar ./build/linux-x86_64-server-fastdebug/images/test/micro/benchmarks.jar -f 1 -i 3 -wi 5 -r 10 -w 5 -p montgomery=true KeyAgreementBench.EC"
ECMUL_CMD="-jar ./build/linux-x86_64-server-fastdebug/images/test/micro/benchmarks.jar -f 1 -i 3 -wi 5 -r 10 -w 5 -p isMontBench=true PolynomialP256Bench.benchAssign"
# ECRED_CMD="-XX:CompileCommand=dontinline,sun/security/util/math/intpoly/MontgomeryIntegerPolynomialP256.reduce -jar ./build/linux-x86_64-server-fastdebug/images/test/micro/benchmarks.jar -f 1 -i 3 -wi 5 -r 100000 -w 5 -p isMontBench=true PolynomialP256Bench.benchReduce"

#  
#OPT="-XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining -XX:-TieredCompilation"
#OPT="-XX:+UnlockDiagnosticVMOptions -XX:+UseIntPolyIntrinsics"
# OPT="-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,sun/security/ec/ECOperations\$PointMultiplier.lookup"
# OPT="-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,sun/security/ec/point/ProjectivePoint\$Mutable.conditionalSet"
# ECDSA_CMD="-jar ./build/linux-x86_64-server-fastdebug/images/test/micro/benchmarks.jar -f 1 -i 3 -wi 5 -r 100000 -w 5 SignatureBench.ECDSA.sign"
# echo $JAVA1 $OPT $ECDSA_CMD
# $JAVA1 $OPT $ECDSA_CMD
# exit 0

# echo $JAVAB $ECDSA_CMD
# $JAVAB $ECDSA_CMD
echo $JAVA1 $ECDSA_CMD
$JAVA1 $ECDSA_CMD
echo $JAVA2 $ECDSA_CMD
$JAVA2 $ECDSA_CMD

# echo $JAVAB $ECDHE_CMD
# $JAVAB $ECDHE_CMD
echo $JAVA1 $ECDHE_CMD
$JAVA1 $ECDHE_CMD
echo $JAVA2 $ECDHE_CMD
$JAVA2 $ECDHE_CMD

# echo $JAVAB $ECMUL_CMD
# $JAVAB $ECMUL_CMD
# echo $JAVA1 $ECMUL_CMD
# $JAVA1 $ECMUL_CMD
# echo $JAVA2 $ECMUL_CMD
# $JAVA2 $ECMUL_CMD
