##
## @test @(#)test6941923.sh
## @bug 6941923 
## @summary test new added flags for gc log rotation 
## @author yqi 
## @run shell test6941923.sh
##

## skip on windows
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  Windows_* )
    echo "Test skipped for Windows"
    exit 0 
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

if [ "${JAVA_HOME}" = "" ]
then
  echo "JAVA_HOME not set"
  exit 0
fi

$JAVA_HOME/bin/java ${TESTVMOPTS} -version > $NULL 2>&1

if [ $? != 0 ]; then
  echo "Wrong JAVA_HOME? JAVA_HOME: $JAVA_HOME"
  exit $?
fi

# create a small test case
testname="Test"
if [ -e ${testname}.java ]; then
  rm -rf ${testname}.*
fi

cat >> ${testname}.java << __EOF__
import java.util.Vector;

public class Test implements Runnable
{
  private boolean _should_stop = false;

  public static void main(String[] args) throws Exception {

    long limit = Long.parseLong(args[0]) * 60L * 1000L;   // minutes
    Test t = new Test();
    t.set_stop(false);
    Thread thr = new Thread(t);
    thr.start();

    long time1 = System.currentTimeMillis();
    long time2 = System.currentTimeMillis();
    while (time2 - time1 < limit) {
      try {
        Thread.sleep(2000); // 2 seconds
      }
      catch(Exception e) {}
      time2 = System.currentTimeMillis();
      System.out.print("\r... " + (time2 - time1)/1000 + " seconds");
    }
    System.out.println();
    t.set_stop(true);
  }
  public void set_stop(boolean value) { _should_stop = value; }
  public void run() {
    int cap = 20000;
    int fix_size = 2048;
    int loop = 0;
    Vector< byte[] > v = new Vector< byte[] >(cap);
    while(!_should_stop) {
      byte[] g = new byte[fix_size];
      v.add(g);
      loop++;
      if (loop > cap) {
         v = null;
         cap *= 2;
         if (cap > 80000) cap = 80000;
         v = new Vector< byte[] >(cap);
      }
    }
  }
}
__EOF__

msgsuccess="succeeded"
msgfail="failed"
gclogsize="16K"
filesize=$((16*1024))
$JAVA_HOME/bin/javac ${testname}.java > $NULL 2>&1

if [ $? != 0 ]; then
  echo "$JAVA_HOME/bin/javac ${testname}.java $fail"
  exit -1
fi

# test for 2 minutes, it will complete circulation of gc log rotation
tts=2
logfile="test.log"
hotspotlog="hotspot.log"

if [ -e $logfile  ]; then
  rm -rf $logfile
fi

#also delete $hotspotlog if it exists
if [ -f $hotspotlog ]; then 
  rm -rf $hotspotlog
fi

options="-Xloggc:$logfile -XX:+UseConcMarkSweepGC -XX:+PrintGC -XX:+PrintGCDetails -XX:+UseGCLogFileRotation  -XX:NumberOfGCLogFiles=1 -XX:GCLogFileSize=$gclogsize"
echo "Test gc log rotation in same file, wait for $tts minutes ...."
$JAVA_HOME/bin/java ${TESTVMOPTS} $options $testname $tts
if [ $? != 0 ]; then
  echo "$msgfail"
  exit -1
fi

# rotation file will be $logfile.0 
if [ -f $logfile.0 ]; then
  outfilesize=`ls -l $logfile.0 | awk '{print $5 }'`
  if [ $((outfilesize)) -ge $((filesize)) ]; then
    echo $msgsuccess
  else
    echo $msgfail
  fi
else 
  echo $msgfail
  exit -1
fi

# delete log file 
rm -rf $logfile.0
if [ -f $hotspotlog ]; then
  rm -rf $hotspotlog
fi

#multiple log files
numoffiles=3
options="-Xloggc:$logfile -XX:+UseConcMarkSweepGC -XX:+PrintGC -XX:+PrintGCDetails -XX:+UseGCLogFileRotation  -XX:NumberOfGCLogFiles=$numoffiles -XX:GCLogFileSize=$gclogsize"
echo "Test gc log rotation in $numoffiles files, wait for $tts minutes ...."
$JAVA_HOME/bin/java ${TESTVMOPTS} $options $testname $tts
if [ $? != 0 ]; then
  echo "$msgfail"
  exit -1
fi

atleast=0    # at least size of numoffile-1 files >= $gclogsize
tk=0
while [ $(($tk)) -lt $(($numoffiles)) ]
do
  if [ -f $logfile.$tk ]; then
    outfilesize=`ls -l $logfile.$tk | awk '{ print $5 }'`
    if [ $(($outfilesize)) -ge $(($filesize)) ]; then
      atleast=$((atleast+1))
    fi
  fi
  tk=$((tk+1))
done

rm -rf $logfile.*
rm -rf $testname.*
rm -rf $hotspotlog

if [ $(($atleast)) -ge $(($numoffiles-1)) ]; then
  echo $msgsuccess
else
  echo $msgfail
  exit -1
fi
