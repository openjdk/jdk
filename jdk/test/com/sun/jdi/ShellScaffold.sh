#!/bin/sh

#
# Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

#
#
# jtreg runs this in a scratch dir.
# It (and runregress -no) sets these env vars:
#    TESTSRC:      The dir that contains this file
#    TESTCLASSES:  Where .class files are compiled to
#    TESTJAVA:     The jdk to run
#
# This is a 'library' script that is included by
# shell script test cases that want to run a .java file as the debuggee
# and use jdb as the debugger.  This file contains
# several functions that support such a test.

# The caller script can also set these shell vars before
# including this file:
#    pkg=<package name>       To use a package, define it here and put
#                                package $pkg
#                             in your java file
#    classname=<classnam>     Omit this to use the default class name, 'shtest'.

#    compileOptions=<string>  compile options for at least the first compile, 
#                             eg, compileOptions=-g
#    compileOptions2=<string> Options for the 2nd, ..., compile. compileOptions1
#                             is used if this is not set.  To use no compile
#                             options for the 2nd ... compiles, do 
#                             compileOptions2=none
#
#    mode=-Xcomp or mode=-Xint to run in these modes.  These should not
#                              really be used since the tests are normally
#                              run in both modes.
#    javacCmd=path-to-javac    to use a non-standard javac for compiling
#    compileOptions=<string>   Options to pass to javac
#
# See RedefineException.sh as an example of a caller script.
#
# To do RedefineClasses operations, embed @1 tags in the .java
# file to tell this script how to modify it to produce the 2nd
# version of the .class file to be used in the redefine operation.
# Here are examples of each editting tag and what change
# it causes in the new file.  Note that blanks are not preserved
# in these editing operations.
#
# @1 uncomment
#  orig:   // @1 uncomment   gus = 89;
#  new:         gus = 89;
#
# @1 commentout
#  orig:   gus = 89      // @1 commentout
#  new: // gus = 89      // @1 commentout
#
# @1 delete
#  orig:  gus = 89      // @1 delete
#  new:   entire line deleted
#
# @1 newline
#  orig:  gus = 89;     // @1 newline gus++;
#  new:   gus = 89;     //
#         gus++;
#
# @1 replace
#  orig:  gus = 89;     // @1 replace gus = 90;
#  new:   gus = 90;
#
# The only other tag supported is @1 breakpoint.  The setbkpts function
# sets bkpts at all lines that contain this string.
# 
# Currently, all these tags are start with @1.  It is envisioned that this script
# could be ehanced to allow multiple cycles of redefines by allowing
# @2, @3, ... tags.  IE, processing the @i tags in the ith version of
# the file will produce the i+1th version of the file.
# 
# There are problem with jtreg leaving behind orphan java and jdb processes
# when this script is run.  Sometimes, on some platforms, it just doesn't
# get them all killed properly.
# The solution is to put a magic word in the cmd lines of background java
# and jdb processes this script launches.  We can then do the right kind
# of ps cmds to find all these processes and kill them.  We do this by
# trapping the completion of this script.
#
# An associated problem is that our trap handler (cleanup) doesn't
# always get called when jtreg terminates a test.  This can leave tests
# hanging but following tests should run ok because each test uses
# unique names for the port and temp files (based on the PID returned
# by $$).
#
# mks 6.2a on win 98 presents two problems:
#   $! returns the PID as a negative number whereas ps returns
#      it in the form 0xFFF....  This means our trick of 
#      of using $! to get the PIDs of the jdb and debuggee processes
#      doesn't work.  This will cause some error cases to fail
#      with a jtreg timeout instead of failing more gracefully.
#
#   There is no form of the ps command that will show the whole
#   cmd line.  Thus, the magic keyword trick doesn't work.  We
#   resort to just killing java.exe and jdb.exes
#
# pid usage:
#   debuggeepid: used in jdb process to detect if debuggee has died.
#                - waitForDebuggeeMsg: fail if debuggee is gone
#
#   jdbpid:   dofail: used to detect if in main process or jdb process
#             waitforfinish: quit if the jdb process is gone

#killcmd=/bin/kill
killcmd=kill

# This can be increased if timing seems to be an issue.
sleep_seconds=1

echo "ShellScaffold.sh: Version" >& 2
topPid=$$

# Be careful to echo to >& in these general functions.
# If they are called from the functions that are sending
# cmds to jdb, then stdout is redirected to jdb.
cleanup()
{
    if [ -r "$failFile" ] ; then
        ls -l "$failFile" >&2
        echo "<cleanup:_begin_failFile_contents>" >&2
        cat "$failFile" >&2
        echo "<cleanup:_end_failFile_contents>" >&2
    fi

    # Kill all processes that have our special
    # keyword in their cmd line.
    killOrphans cleanup $jdbKeyword
    killOrphans cleanup $debuggeeKeyword
}

# Kill all processes with $2 in their cmd lines
# Print a msg about this using $1 as the prefix
killOrphans()
{
    str=$2

    if [ -z "$isCygwin" ] ; then
        toBeKilled=`$psCmd | $grep -v grep | $grep -i $str | awk '{print $1}' | tr '\n\r' '  '`
    else
        # The cygwin ps command doesn't show the options passed to a cmd.
        # We will use jps to get the win PID of the command, and
        # then use ps to find the cygwin pid to be killed.
        # The form of a ps output line is
        # ^   ddddd    dddd    dddd    dddd.*
        # where the 4th digits are the win pid and the first 
        # are the cygwin pid.
        if [ -r "$jdk/bin/$jstack" ] ; then
            winPid=`$jdk/bin/jps -v | $grep -i $str | sed -e 's@ .*@@'`
            if [ ! -z "$winPid" ] ; then
                # Here is a way to kill using a win cmd and the win PID.
                #echo "$1: taskkill /F $winPid"  >& 2
                #taskkill /F /PID $winPid

                toBeKilled=`$psCmd | $grep -v grep | \
                            $grep '^ +[0-9]+ +[0-9]+ +[0-9]+ +'"$winPid" |\
                            awk '{print $1}' | tr '\n\r' '  '`
            fi
        else
            # Well, too bad - we can't find what to kill.  
            toBeKilled=
        fi
    fi

    if [ ! -z "$toBeKilled" ] ; then
        echo "$1: kill -9 $toBeKilled"  >& 2
        kill -9 $toBeKilled
    fi
}    

findPid()
{
    # Return 0 if $1 is the pid of a running process.
    if [ -z "$isWin98" ] ; then
        if [ "$osname" = SunOS ] ; then
            # Solaris and OpenSolaris use pgrep and not ps in psCmd
            findPidCmd="$psCmd"
        else
            #   Never use plain 'ps', which requires a "controlling terminal"
            #     and will fail  with a "ps: no controlling terminal" error.
            #     Running under 'rsh' will cause this ps error.
            # cygwin ps puts an I in column 1 for some reason.
            findPidCmd="$psCmd -e"
        fi
	$findPidCmd | $grep '^I* *'"$1 " > $devnull 2>&1
        return $?
    fi

    # mks 6.2a on win98 has $! getting a negative
    # number and in ps, it shows up as 0x...
    # Thus, we can't search in ps output for 
    # PIDs gotten via $!
    # We don't know if it is running or not - assume it is.
    # We don't really care about win98 anymore.
    return 0
}

setup()
{
    failed=
    # This is used to tag each java and jdb cmd we issue so
    # we can kill them at the end of the run.

    orphanKeyword=HANGINGJAVA-$$
    debuggeeKeyword=${orphanKeyword}_DEB
    jdbKeyword=${orphanKeyword}_JDB
    baseArgs=-D${debuggeeKeyword}
    if [ -z "$TESTCLASSES" ] ; then
        echo "--Warning:  TESTCLASSES is not defined; using TESTCLASSES=."
        echo "  You should run: "
        echo "    runregress $0 -no"
        echo "  or"
        echo "    (setenv TESTCLASSES .; $0 $*)"
        TESTCLASSES=.
    fi
    if [ ! -z "$TESTJAVA" ] ; then
        jdk="$TESTJAVA"
    else
        echo "--Error: TESTJAVA must be defined as the pathname of a jdk to test."
        exit 1
    fi
    
    ulimitCmd=
    osname=`uname -s`
    isWin98=
    isCygwin=
    case "$osname" in
       Windows* | CYGWIN*)	   
         devnull=NUL
	 if [ "$osname" = Windows_98 -o "$osname" = Windows_ME ]; then
             isWin98=1
             debuggeeKeyword='we_cant_kill_debuggees_on_win98'
             jdbKeyword='jdb\.exe'
	 fi

         case "$osname" in
           CYGWIN*)
             isCygwin=1
             devnull=/dev/null
             ;;
         esac

         if [ -r $jdk/bin/dt_shmem.dll -o -r $jdk/jre/bin/dt_shmem.dll ] ; then
            transport=dt_shmem
            address=kkkk.$$
         else
            transport=dt_socket
            address=
         fi
         baseArgs="$baseArgs -XX:-ShowMessageBoxOnError"
         # jtreg puts \\s in TESTCLASSES and some uses, eg. echo
         # treat them as control chars on mks (eg \t is tab)
         # Oops; windows mks really seems to want this cat line
         # to start in column 1
         if [ -w "$SystemRoot" ] ; then
            tmpFile=$SystemRoot/tmp.$$
         elif [ -w "$SYSTEMROOT" ] ; then
            tmpFile=$SYSTEMROOT/tmp.$$
         else
            tmpFile=tmp.$$
         fi
cat <<EOF >$tmpFile
$TESTCLASSES
EOF
         TESTCLASSES=`cat $tmpFile | sed -e 's@\\\\@/@g'`
         rm -f $tmpFile
         # on mks
         grep=egrep
         psCmd=ps
         jstack=jstack.exe
         ;;
       SunOS | Linux | Darwin)
         transport=dt_socket
         address=
         devnull=/dev/null
         grep=egrep
         jstack=jstack
         # On linux, core files take a long time, and can leave
         # zombie processes
         if [ "$osname" = SunOS ] ; then
             # Experiments show Solaris '/usr/ucb/ps -axwww' and
             # '/usr/bin/pgrep -f -l' provide the same small amount of the
             # argv string (PRARGSZ=80 in /usr/include/sys/procfs.h)
             #  1) This seems to have been working OK in ShellScaffold.
             #  2) OpenSolaris does not provide /usr/ucb/ps, so use pgrep
             #     instead
             # The alternative would be to use /usr/bin/pargs [pid] to get
             # all the args for a process, splice them back into one
             # long string, then grep.
             UU=`/usr/xpg4/bin/id -u -n`
             psCmd="pgrep -f -l -U $UU"
         else
             ulimit -c 0
             # See bug 6238593.
             psCmd="ps axwww"
         fi
         ;;
       *)
         echo "--Error:  Unknown result from 'uname -s':  $osname"
         exit 1
         ;;
    esac


    tmpFileDir=$TESTCLASSES/aa$$
    TESTCLASSES=$tmpFileDir

    mkdir -p $tmpFileDir

    # This must not contain 'jdb' or it shows up
    # in grep of ps output for some platforms
    jdbOutFile=$tmpFileDir/jxdbOutput.txt
    rm -f $jdbOutFile
    touch $jdbOutFile

    debuggeeOutFile=$tmpFileDir/debuggeeOutput.txt
    failFile=$tmpFileDir/testFailed
    debuggeepidFile=$tmpFileDir/debuggeepid
    rm -f $failFile $debuggeepidFile
    if [ -f "$failFile" ]; then
        echo "ERROR: unable to delete existing failFile:" >&2
        ls -l "$failFile" >&2
    fi

    if [ -z "$pkg" ] ; then
        pkgSlash=
        pkgDot=
        redefineSubdir=.
    else
        pkgSlash=$pkg/
        pkgDot=$pkg.
        redefineSubdir=$pkgSlash
    fi
    if [ -z "$classname" ] ; then
        classname=shtest
    fi

    if [ -z "$java" ] ; then
        java=java
    fi

    if [ -z "$jdb" ] ; then
        jdb=$jdk/bin/jdb
    fi

####################################################3
####################################################3
####################################################3
####################################################3
#  sol:  this gets all processes killed but 
#        no jstack
#  linux same as above
#  win mks:  No dice; processes still running
    trap "cleanup" 0 1 2 3 4 6 9 10 15
    
    jdbOptions="$jdbOptions -J-D${jdbKeyword}"
}

docompile()
{
    if [ "$compile" = 0 ] ; then
        return
    fi
    saveDir=`pwd`
    cd $tmpFileDir
    rm -f *.java
    createJavaFile $classname

    # Compile two versions of the file, the original and with the
    # indicated lines modified.
    cp $classname.java.1 $classname.java
    echo "--Compiling first version of `pwd`/$classname.java with options: $compileOptions"
    # Result is in $pkgSlash$classname.class
    
    if [ -z "$javacCmd" ] ; then
        javacCmd=$jdk/bin/javac
    fi

    echo "compiling " `ls *.java`
    $javacCmd $compileOptions -d . *.java
    if [ $? != 0 ] ; then
       dofail "First compile failed"
    fi
    if [ -r vers1 ] ; then
        rm -rf vers1
    fi
    mkdir -p vers1
    mv *.class vers1
    if [ ! -z "$compileOptions2" ] ; then
        if [ "$compileOptions2" = none ] ; then
            compileOptions=
        else
            compileOptions=$compileOptions2
        fi
    fi

    while [ 1 = 1 ] ; do
        # Not really a loop; just a way to avoid goto
        # by using breaks
        sed -e '/@1 *delete/ d' \
            -e 's! *// *@1 *uncomment!     !' \
            -e 's!\(.*@1 *commentout\)!//\1!' \
            -e 's/@1 *newline/\
                 /' \
            -e 's/.*@1 *replace//' \
            $classname.java.1  >$classname.java

        cmp -s $classname.java.1 $classname.java
        if [ $? = 0 ] ; then
            break
        fi
        echo 
        echo "--Compiling second version of `pwd`/$classname.java with $compileOptions"
        $javacCmd $compileOptions -d . $classname.java
        if [ $? != 0 ] ; then
            dofail "Second compile failed"
        fi
        if [ -r vers2 ] ; then
            rm -rf vers2
        fi
        mkdir -p vers2
        mv *.class vers2
        mv $classname.java $classname.java.2
        cp $classname.java.1 $classname.java

        ###### Do the same for @2, and @3 allowing 3 redefines to occur.
        ###### If I had more time to write sed cmds, I would do
        ###### this in a loop.  But, I don't think we will ever need
        ###### more than 3 redefines.
        sed -e '/@2 *delete/ d' \
            -e 's! *// *@2 *uncomment!     !' \
            -e 's!\(.*@2 *commentout\)!//\1!' \
            -e 's/@2 *newline/\
                 /' \
            -e 's/.*@2 *replace//' \
            $classname.java.2 >$classname.java
        cmp -s $classname.java.2 $classname.java
        if [ $? = 0 ] ; then
            break
        fi
        echo 
        echo "--Compiling third version of `pwd`/$classname.java with $compileOptions"
        $javacCmd $compileOptions -d . $classname.java
        if [ $? != 0 ] ; then
            dofail "Third compile failed"
        fi
        if [ -r vers3 ] ; then
            rm -rf vers3
        fi
        mkdir -p vers3
        mv *.class vers3
        mv $classname.java $classname.java.3
        cp $classname.java.1 $classname.java

        ########
        sed -e '/@3 *delete/ d' \
            -e 's! *// *@3 *uncomment!     !' \
            -e 's!\(.*@3 *commentout\)!//\1!' \
            -e 's/@3 *newline/\
                    /' \
            -e 's/.*@3 *replace//' \
            $classname.java.3 >$classname.java
        cmp -s $classname.java.3 $classname.java
        if [ $? = 0 ] ; then
            break
        fi
        echo 
        echo "--Compiling fourth version of `pwd`/$classname.java with $compileOptions"
        $javacCmd $compileOptions -d . $classname.java
        if [ $? != 0 ] ; then
            dofail "fourth compile failed"
        fi
        if [ -r vers4 ] ; then
            rm -rf vers4
        fi
        mkdir -p vers4
        mv *.class vers4
        mv $classname.java $classname.java.4
        cp $classname.java.1 $classname.java
        break
        fgrep @4 $classname.java
        if [ $? = 0 ] ; then
            echo "--Error: @4 and above are not yet allowed"
            exit 1
        fi
    done

    cp vers1/* $redefineSubdir
    cd $saveDir
}

# Send a cmd to jdb and wait for the jdb prompt to appear.
# We don't want to allow > as a prompt because if the debuggee
# runs for awhile after a command, jdb will show this prompt
# but is not really ready to accept another command for the
# debuggee - ie, a cont in this state will be ignored.
# If it ever becomes necessary to send a jdb command before
# a  main[10] form of prompt appears, then this
# code will have to be modified.
cmd() 
{
    if [ $1 = quit -o -r "$failFile" ] ; then
        # if jdb got a cont cmd that caused the debuggee
        # to run to completion, jdb can be gone before
        # we get here.
        echo "--Sending cmd: quit" >& 2
        echo quit
        # See 6562090. Maybe there is a way that the exit
        # can cause jdb to not get the quit.
        sleep 5

        # The exit code value here doesn't matter since this function
        # is called as part of a pipeline and it is not the last command
        # in the pipeline.
        exit 1
    fi
    
    # $jdbOutFile always exists here and is non empty
    # because after starting jdb, we waited 
    # for the prompt.
    fileSize=`wc -c $jdbOutFile | awk '{ print $1 }'`
    echo "--Sending cmd: " $* >&2

    # jjh: We have a few intermittent failures here.
    # It is as if every so often, jdb doesn't
    # get the first cmd that is sent to it here.  
    # (actually, I have seen it get the first cmd ok,
    # but then not get some subsequent cmd).
    # It seems like jdb really doesn't get the cmd; jdb's response
    # does not appear in the jxdboutput file. It contains:
    # main[1] 
    # The application has been disconnected

    # Is it possible
    # that jdb got the cmd ok, but its response didn't make
    # it to the jxdboutput file?  If so, why did 'The application
    # has been disconnected' make it?

    # This causes the following loop to timeout and the test to fail.
    # The above echo works because the cmd (stop at ...)
    # is in the System.err shown in the .jtr file.
    # Also, the cmd is shown in the 'jdb never responded ...'
    # msg output below after the timeout.
    # And, we know jdb is started because the main[1] output is in the .jtr
    # file.  And, we wouldn't have gotten here if mydojdbcmds hadn't
    # seen the ].  
    echo $*

    # Now we have to wait for the next jdb prompt.  We wait for a pattern
    # to appear in the last line of jdb output.  Normally, the prompt is
    #
    # 1) ^main[89] @
    #
    # where ^ means start of line, and @ means end of file with no end of line
    # and 89 is the current command counter. But we have complications e.g.,
    # the following jdb output can appear:
    #
    # 2) a[89] = 10
    #
    # The above form is an array assignment and not a prompt.
    #
    # 3) ^main[89] main[89] ...
    #
    # This occurs if the next cmd is one that causes no jdb output, e.g.,
    # 'trace methods'.
    #
    # 4) ^main[89] [main[89]] .... > @
    #
    # jdb prints a > as a prompt after something like a cont.
    # Thus, even though the above is the last 'line' in the file, it
    # isn't the next prompt we are waiting for after the cont completes.
    # HOWEVER, sometimes we see this for a cont command:
    #
    #   ^main[89] $
    #      <lines output for hitting a bkpt>
    #
    # 5) ^main[89] > @
    #
    # i.e., the > prompt comes out AFTER the prompt we we need to wait for.
    #
    # So, how do we know when the next prompt has appeared??
    # 1.  Search for 
    #         main[89] $
    #     This will handle cases 1, 2, 3
    # 2.  This leaves cases 4 and 5.
    #
    # What if we wait for 4 more chars to appear and then search for
    #
    #    main[89] [>]$
    #
    # on the last line?
    #
    # a.  if we are currently at
    #
    #       ^main[89] main[89] @
    #
    #     and a 'trace methods comes in, we will wait until at least
    #
    #       ^main[89] main[89] main@
    #
    #     and then the search will find the new prompt when it completes.
    #
    # b.  if we are currently at
    #
    #       ^main[89] main[89] @
    #
    #     and the first form of cont comes in, then we will see
    #
    #       ^main[89] main[89] > $
    #       ^x@
    #
    #     where x is the first char of the msg output when the bkpt is hit
    #     and we will start our search, which will find the prompt
    #     when it comes out after the bkpt output, with or without the
    #     trailing >
    #

    # wait for 4 new chars to appear in the jdb output
    count=0
    desiredFileSize=`expr $fileSize + 4`
    msg1=`echo At start: cmd/size/waiting : $* / $fileSize / \`date\``
    while [ 1 = 1 ] ; do
        newFileSize=`wc -c $jdbOutFile | awk '{ print $1 } '`
        #echo jj: desired = $desiredFileSize, new = $newFileSize >& 2

        done=`expr $newFileSize \>= $desiredFileSize`
        if [ $done = 1 ] ; then
            break
        fi
        sleep ${sleep_seconds}
        count=`expr $count + 1`
        if [ $count = 30 -o $count = 60 ] ; then
            # record some debug info.
            echo "--DEBUG: jdb $$ didn't responded to command in $count secs: $*" >& 2
            echo "--DEBUG:" $msg1 >& 2
            echo "--DEBUG: "done size/waiting : / $newFileSize  / `date` >& 2
            echo "-- $jdbOutFile follows-------------------------------" >& 2
            cat $jdbOutFile >& 2
            echo "------------------------------------------" >& 2
            dojstack
            #$psCmd | sed -e '/com.sun.javatest/d' -e '/nsk/d' >& 2
            if [ $count = 60 ] ; then
                dofail "jdb never responded to command: $*"
            fi
        fi
    done
    # Note that this assumes just these chars in thread names.
    waitForJdbMsg '[a-zA-Z0-9_-][a-zA-Z0-9_-]*\[[1-9][0-9]*\] [ >]*$' \
        1 allowExit
}

setBkpts()
{
    # Can set multiple bkpts, but only in one class.
    # $1 is the bkpt name, eg, @1
    allLines=`$grep -n "$1 *breakpoint" $tmpFileDir/$classname.java.1 | sed -e 's@^\([0-9]*\).*@\1@g'`
    for ii in $allLines ; do
        cmd stop at $pkgDot$classname:$ii
    done
}

runToBkpt()
{
    cmd run
    # Don't need to do this - the above waits for the next prompt which comes out
    # AFTER the Breakpoint hit message.
    # Wait for jdb to hit the bkpt
    #waitForJdbMsg "Breakpoint hit" 5
}

contToBkpt()
{
    cmd cont
    # Don't need to do this - the above waits for the next prompt which comes out
    # AFTER the Breakpoint hit message.
    # Wait for jdb to hit the bkpt
    #waitForJdbMsg "Breakpoint hit" 5
}


# Wait until string $1 appears in the output file, within the last $2 lines
# If $3 is allowExit, then don't fail if jdb exits before
# the desired string appears.
waitForJdbMsg()
{
    # This can be called from the jdb thread which doesn't
    # have access to $debuggeepid, so we have to read it from the file.
    nlines=$2
    allowExit="$3"
    myCount=0
    timeLimit=40  # wait a max of this many secs for a response from a jdb command
    while [ 1 = 1 ] ; do 
        if [  -r $jdbOutFile ] ; then
            # Something here causes jdb to complain about Unrecognized cmd on x86.
            tail -$nlines $jdbOutFile | $grep -s "$1" > $devnull 2>&1
            if [ $? = 0 ] ; then
                # Found desired string
                break
            fi
	fi
	tail -2 $jdbOutFile | $grep -s "The application exited" > $devnull 2>&1
	if [ $? = 0 ] ; then
            # Found 'The application exited'
            if [ ! -z "$allowExit" ] ; then
                break
            fi
            # Otherwise, it is an error if we don't find $1
	    if [  -r $jdbOutFile ] ; then 
		tail -$nlines $jdbOutFile | $grep -s "$1" > $devnull 2>&1		
                if [ $? = 0 ] ; then
		   break
		fi
	    fi
            dofail "Waited for jdb msg $1, but it never appeared"	            
	fi

        sleep ${sleep_seconds}
        findPid $topPid
        if [ $? != 0 ] ; then
            # Top process is dead.  We better die too
            dojstack
            exit 1
        fi

        myCount=`expr $myCount + ${sleep_seconds}`
        if [ $myCount -gt $timeLimit ] ; then
            echo "--Fail: waitForJdbMsg timed out after $timeLimit seconds, looking for /$1/, in $nlines lines; exitting" >> $failFile
            echo "vv jdbOutFile  vvvvvvvvvvvvvvvvvvvvvvvvvvvv" >& 2
            cat $jdbOutFile >& 2
            echo "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^" >& 2
            dojstack
            exit 1
        fi
    done

}

# $1 is the string to print.  If $2 exists,
# it is the name of a file to print, ie, the name
# of the file that contains the $1 string.
dofail()
{
    if [ ! -z "$jdbpid" ] ; then
        # we are in the main process instead of the jdb process
        echo " " >> $failFile
        echo "--Fail: main: $*" >> $failFile
    else
        # Kill the debuggee ; it could be hung so
        # we want to get rid of it as soon as possible.
        killOrphans "killing debuggee" $debuggeeKeyword

        echo " "  >>$failFile
        echo "--Fail: $*" >> $failFile
        echo quit
    fi
    if [ ! -z "$2" ] ; then
        echo  "---- contents of $2 follows -------" >> $failFile
        cat "$2" >> $failFile
        echo "---------------" >>$failFile
    fi
    exit 1
}


redefineClass() 
{
    if [ -z "$1" ] ; then
        vers=2
    else
        vers=`echo $1 | sed -e 's/@//'`
        vers=`expr $vers + 1`
    fi
        
    cmd redefine $pkgDot$classname $tmpFileDir/vers$vers/$classname.class

    cp $tmpFileDir/$classname.java.$vers \
       $tmpFileDir/$classname.java
}

mydojdbCmds()
{
   # Wait for jdb to start before we start sending cmds
   waitForJdbMsg ']' 1
   dojdbCmds
   cmd quit
}

startJdb()
{
    if [ ! -r "$jdb" -a ! -r "$jdb.exe" ] ; then
        dofail "$jdb does not exist"
    fi
    echo
    echo "--Starting jdb, address=$address"
    if [ -z "$address" ] ; then
       # Let jdb choose the port and write it to stdout
       mydojdbCmds | $jdb $jdbOptions -listenany | tee $jdbOutFile &

       while [ 1 ] ; do
           lastLine=`$grep 'Listening at address' $jdbOutFile`
           if [ ! -z "$lastLine" ] ; then
               break
           fi
           sleep 1
       done
       # jjh: we got the address ok, and seemed to start the debuggee
       address=`echo $lastLine | sed -e 's@.*: *@@'`
    else
       mydojdbCmds | $jdb $jdbOptions -listen $address | tee $jdbOutFile &
    fi
    #echo address = $address


    # this gets the pid of tee, at least it does on solaris
    jdbpid=$!

    # This fails on linux because there is an entry for each thread in jdb
    # so we get a list of numbers in jdbpid
    # jdbpid=`$psCmd | $grep -v grep | $grep ${orphanKeyword}_JDB | awk '{print $1}'  | tr '\n\r' '  '`
}

startDebuggee()
{
    args=
    # Note that @debuggeeVMOptions is unique to a test run instead of
    # a test in a run.  It is not modified during a test run.
    if [ -r $TESTCLASSES/../@debuggeeVMOptions ] ; then
       args=`cat $TESTCLASSES/../@debuggeeVMOptions`
    fi
    
    if [ ! -z "$args" ] ; then
       echo "--Starting debuggee with args from @debuggeeVMOptions: $args"
    else
       echo "--Starting debuggee"
    fi

    debuggeepid=
    waitForJdbMsg Listening 4

    beOption="-agentlib:jdwp=transport=$transport,address=$address,server=n,suspend=y"
#   beOption="-Xdebug -Xrunjdwp:transport=$transport,address=$address,server=n,suspend=y"

    thecmd="$jdk/bin/$java $mode -classpath $tmpFileDir $baseArgs $args \
            -Djtreg.classDir=$TESTCLASSES \
            -showversion \
             $beOption \
             $pkgDot$classname"
    echo "Cmd: $thecmd"

    sh -c "$thecmd | tee $debuggeeOutFile" &

    # Note that the java cmd and the tee cmd will be children of
    # the sh process.  We can use that to find them to kill them.
    debuggeepid=$!

    # Save this in a place where the jdb process can find it.
    # Note that it is possible for the java cmd to abort during startup
    # due to a bad classpath or whatever.
    echo $debuggeepid > $debuggeepidFile
}

dojstack()
{
    if [ -r "$jdk/bin/$jstack" ] ; then
        # If jstack exists, so will jps
        # Show stack traces of jdb and debuggee as a possible debugging aid.
        jdbCmd=`$jdk/bin/jps -v | $grep $jdbKeyword`
        realJdbPid=`echo "$jdbCmd" | sed -e 's@ TTY.*@@'`
        if [ ! -z "$realJdbPid" ] ; then
            echo "-- jdb process info ----------------------" >&2
            echo "      $jdbCmd"                              >&2
            echo "-- jdb threads: jstack $realJdbPid"         >&2
            $jdk/bin/$jstack $realJdbPid                      >&2
            echo "------------------------------------------" >&2
            echo                                              >&2
        fi
        debuggeeCmd=`$jdk/bin/jps -v | $grep $debuggeeKeyword`
        realDebuggeePid=`echo "$debuggeeCmd" | sed -e 's@ .*@@'`
        if [ ! -z "$realDebuggeePid" ] ; then
            if [ -r "$jdk/lib/sa-jdi.jar" ] ; then
                # disableVersionCheck can be removed after 6475822
                # is fixed.
                moption="-m -J-Dsun.jvm.hotspot.runtime.VM.disableVersionCheck"
            else
                moption=
            fi

            echo "-- debuggee process info ----------------------" >&2
            echo "      $debuggeeCmd"                              >&2
            echo "-- debuggee threads: jstack $moption $realDebuggeePid" >&2
            $jdk/bin/$jstack $moption $realDebuggeePid             >&2
            echo "============================================="   >&2
            echo                                                   >&2
        fi
    fi
}

waitForFinish()
{
    # This is the main process
    # Wait for the jdb process to finish, or some error to occur

    while [ 1 = 1 ] ; do
        findPid $jdbpid
        if [ $? != 0 ] ; then
            break
        fi
        if [ ! -z "$isWin98" ] ; then
           $psCmd | $grep -i 'JDB\.EXE' >$devnull 2>&1 
           if [ $? != 0 ] ; then
               break;
           fi
        fi
        $grep -s 'Input stream closed' $jdbOutFile > $devnull 2>&1
        if [ $? = 0 ] ; then
            #something went wrong
            dofail "jdb input stream closed prematurely"
        fi

        # If a failure has occured, quit
        if [ -r "$failFile" ] ; then
            break
        fi

        sleep ${sleep_seconds}
    done

    if [ -r "$failFile" ] ; then
        ls -l "$failFile" >&2
        echo "<waitForFinish:_begin_failFile_contents>" >&2
        cat "$failFile" >&2
        echo "<waitForFinish:_end_failFile_contents>" >&2
        exit 1
    fi
}

# $1 is the filename, $2 is the string to look for,
# $3 is the number of lines to search (from the end)
grepForString()
{
    if [ -z "$3" ] ; then
        theCmd=cat
    else
        theCmd="tail -$3"
    fi

    case "$2" in 
    *\>*)
        # Target string contains a '>' so we better not ignore it
        $theCmd $1 | $grep -s "$2"  > $devnull 2>&1
        stat="$?"
        ;;
    *)
        # Target string does not contain a '>'.
        # NOTE:  if $1 does not end with a new line, piping it to sed
        # doesn't include the chars on the last line.  Detect this
        # case, and add a new line.
        theFile="$1"
        if [ `tail -1 "$theFile" | wc -l | sed -e 's@ @@g'` = 0 ] ; then
            # The target file doesn't end with a new line so we have
            # add one to a copy of the target file so the sed command
            # below can filter that last line.
            cp "$theFile" "$theFile.tmp"
            theFile="$theFile.tmp"
            echo >> "$theFile"
        fi

        # See bug 6220903. Sometimes the jdb prompt chars ('> ') can
        # get interleaved in the target file which can keep us from
        # matching the target string.
        $theCmd "$theFile" | sed -e 's@> @@g' -e 's@>@@g' \
            | $grep -s "$2" > $devnull 2>&1
        stat=$?
        if [ "$theFile" != "$1" ]; then
            # remove the copy of the target file
            rm -f "$theFile"
        fi
        unset theFile
    esac
    return $stat
}

# $1 is the filename, $2 is the regexp to match and return,
# $3 is the number of lines to search (from the end)
matchRegexp()
{
    if [ -z "$3" ] ; then
        theCmd=cat
    else
        theCmd="tail -$3"
    fi

    case "$2" in 
    *\>*)
        # Target string contains a '>' so we better not ignore it
        res=`$theCmd $1 | sed -e "$2"`
        ;;
    *)
        # Target string does not contain a '>'.
        # NOTE:  if $1 does not end with a new line, piping it to sed
        # doesn't include the chars on the last line.  Detect this
        # case, and add a new line.
        theFile="$1"
        if [ `tail -1 "$theFile" | wc -l | sed -e 's@ @@g'` = 0 ] ; then
            # The target file doesn't end with a new line so we have
            # add one to a copy of the target file so the sed command
            # below can filter that last line.
            cp "$theFile" "$theFile.tmp"
            theFile="$theFile.tmp"
            echo >> "$theFile"
        fi

        # See bug 6220903. Sometimes the jdb prompt chars ('> ') can
        # get interleaved in the target file which can keep us from
        # matching the target string.
        res=`$theCmd "$theFile" | sed -e 's@> @@g' -e 's@>@@g' \
            | sed -e "$2"`
        if [ "$theFile" != "$1" ]; then
            # remove the copy of the target file
            rm -f "$theFile"
        fi
        unset theFile
    esac
    return $res
}

# $1 is the filename, $2 is the string to look for,
# $3 is the number of lines to search (from the end)
failIfPresent()
{
    if [ -r "$1" ] ; then
        grepForString "$1" "$2" "$3"
        if [ $? = 0 ] ; then
            dofail "Error output found: \"$2\" in $1" $1
        fi
    fi
}

# $1 is the filename, $2 is the string to look for
# $3 is the number of lines to search (from the end)
failIfNotPresent()
{
    if [ ! -r "$1" ] ; then
        dofail "Required output \"$2\" not found in $1"
    fi
    grepForString "$1" "$2" "$3"
    if [ $? != 0 ] ; then
        dofail "Required output \"$2\" not found in $1" $1
    fi

}

# fail if $1 is not in the jdb output
# $2 is the number of lines to search (from the end)
jdbFailIfNotPresent()
{
    failIfNotPresent $jdbOutFile "$1" $2
}

# fail if $1 is not in the debuggee output
# $2 is the number of lines to search (from the end)
debuggeeFailIfNotPresent()
{
    failIfNotPresent $debuggeeOutFile "$1" $2
}

# fail if $1 is in the jdb output
# $2 is the number of lines to search (from the end)
jdbFailIfPresent()
{
    failIfPresent $jdbOutFile "$1" $2
}

# fail if $1 is in the debuggee output
# $2 is the number of lines to search (from the end)
debuggeeFailIfPresent()
{
    failIfPresent $debuggeeOutFile "$1" $2
}

# match and return the output from the regexp $1 in the debuggee output
# $2 is the number of lines to search (from the end)
debuggeeMatchRegexp()
{
    matchRegexp $debuggeeOutFile "$1" $2
}


# This should really be named 'done' instead of pass.
pass()
{
    if [ ! -r "$failFile" ] ; then
        echo
        echo "--Done: test passed"
        exit 0
    else
        ls -l "$failFile" >&2
        echo "<pass:_begin_failFile_contents>" >&2
        cat "$failFile" >&2
        echo "<pass:_end_failFile_contents>" >&2
    fi
}

runit()
{
    setup
    runitAfterSetup
}

runitAfterSetup()
{
    docompile
    startJdb 
    startDebuggee
    waitForFinish

    # in hs_err file from 1.3.1
    debuggeeFailIfPresent "Virtual Machine Error"

    # in hs_err file from 1.4.2, 1.5:  An unexpected error
    debuggeeFailIfPresent "An unexpected error"

    # in hs_err file from 1.4.2, 1.5:  Internal error
    debuggeeFailIfPresent "Internal error"


    # Don't know how this arises
    debuggeeFailIfPresent "An unexpected exception"

    # Don't know how this arises
    debuggeeFailIfPresent "Internal exception"
}


