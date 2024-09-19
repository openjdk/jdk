#
# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

define print_mutex
  set $mutex = $arg0
  set $_owner =  $mutex._owner
  if $_owner != 0
    printf "Mutex: %s owned by %p\n", $mutex._name, $_owner
  end
end

define print_mutexes
  set $i = 0
  set $owned = 0
  while $i < _num_mutex
    print_mutex _mutex_array[$i]
    set $i = $i + 1
  end
  if $owned == 0
    printf "None\n"
  end
end

define print_safepoint
  printf "_state: "
  p SafepointSynchronize::_state
  printf "\n"
end

define print_thread
  set $thread = $arg0
  printf "\nThread: \n"
  print $thread
  printf "LWP ID: "
  print $thread._osthread._thread_id
  printf "OSThread state: "
  print $thread._osthread._state
  printf "Thread info: "
end


define print_vmthread
  set $thread = VMThread::_vm_thread
  print_thread $thread
end

define print_java_thread
  set $thread = $arg0
  print_thread $thread
  printf "JavaThreadState: "
  print $thread._thread_state
  printf "ThreadSafepointState: "
  print *$thread._safepoint_state
  printf "Suspend flags: "
  print $thread._suspend_flags
end

define print_non_java_thread
  set $thread = $arg0
  print_thread $thread
end


define print_java_thread_raw
  set $thread = $arg0
  print *$thread
  print *$thread._osthread._thread_d
end

define print_java_threads
  set $threads = ThreadsSMRSupport::_java_thread_list._threads
  set $length = ThreadsSMRSupport::_java_thread_list._length
  set $i = 0
  while $i < $length
    print_java_thread $threads[$i]
    set $i = $i + 1
  end
end

define print_non_java_threads
  set $thread = NonJavaThread::_the_list._head
  while $thread != 0
    print_non_java_thread $thread
    set $thread = $thread._next
  end
end

printf "==============================================================================================\n"
printf "The list of owned mutexes:\n"
printf "==============================================================================================\n"
print_mutexes
printf "\n"
printf "\n"

printf "==============================================================================================\n"
printf "The Safepoint:\n"
printf "==============================================================================================\n"
print_safepoint
printf "\n"
printf "\n"

printf "==============================================================================================\n"
printf "The VMThread:\n"
printf "==============================================================================================\n"
print_vmthread
printf "\n"
printf "\n"


printf "==============================================================================================\n"
printf "The JavaThreads (ThreadsSMRSupport::_java_thread_list) :\n"
printf "==============================================================================================\n"
print_java_threads
printf "\n"
printf "\n"

printf "==============================================================================================\n"
printf "The NonJavaThreads (NonJavaThread::_the_list) :\n"
printf "==============================================================================================\n"
print_non_java_threads
printf "\n"
printf "\n"



printf "==============================================================================================\n"
printf "Thread info (info threads):\n"
printf "==============================================================================================\n"
info threads
printf "\n"
printf "\n"
printf "==============================================================================================\n"
printf "All stack traces (thread apply all backtrace):\n"
printf "==============================================================================================\n"
thread apply all backtrace
