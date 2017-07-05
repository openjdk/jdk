/*
 * Copyright (c) 1999, 2000, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 **********************************************************************
 * Poller.c :
 * JNI code for use with Poller.java, principally to take advantage
 * of poll() or /dev/poll multiplexing.
 *
 * One will need Solaris 8 or Solaris 7 with adequate patches to take
 * advantage of the /dev/poll performance enhancements, though any
 * version of Solaris 7 will automatically use the kernel poll()
 * caching.  And poll() will function in 2.5.1 and 2.6 as well, but
 * will not perform well for large numbers of file descriptors.
 *
 * Several assumptions have been made to simplify this code :
 *  1> At most MAX_HANDLES (32) separate pollable entities are currently
 *     supported.
 *  2> Global synchronization from Java is assumed for all init, create
 *     and destroy routines.  Per Object (handle passed in) synchronization
 *     is required for all AddFd, RemoveFd, IsMember, and Wait routines.
 *  3> It is currently up to the user to handle waking up an
 *     existing nativeWait() call to do an addfd or removefd on
 *     that set...could implement that here with an extra pipe, or
 *     with a pair of loopback sockets in Poller.java or user code.
 *     In most cases interruption is not necessary for deletions,
 *     so long as deletions are queued up outside the Poller class
 *     and then executed the next time waitMultiple() returns.
 *  4> /dev/poll performance could be slightly improved by coalescing
 *     adds/removes so that a write() is only done before the ioctl
 *     (DP_POLL), but this complicates exception handling and sees
 *     only modest performance gains so wasn't done.
 *  5> /dev/poll does not report errors on attempts to remove non-
 *     extant fds, but a future bug fix to the /dev/poll device driver
 *     should solve this problem.
 *  6> Could add simpler code for pre-Solaris 7 releases which will
 *     perform slightly better on those OSs.  But again there
 *     are only modest gains to be had from these new code paths,
 *     so they've been ommitted here.
 *
 * Compile "cc -G -o <dest_dir>/libpoller.so -I ${JAVA_HOME}/include " \
 * -I ${JAVA_HOME}/include/solaris Poller.c" and place the <dest_dir>
 * in your LD_LIBRARY_PATH
 *
 **********************************************************************
 */

#include <stdio.h>
#include <unistd.h>
#include <errno.h>
#include <poll.h>
#include <malloc.h>
#include <fcntl.h>


/*
 * Remove "_NOT"s to turn on features
 * Append "_NOT" to turn off features.
 * Use of /dev/poll requires both the include file and kernel driver.
 */
#define DEBUG_NOT
#define DEVPOLL_NOT

#ifdef DEVPOLL
#include <sys/devpoll.h>
#endif

#include "Poller.h"

#define MAX_HANDLES 32


#ifdef DEBUG
#define DBGMSG(x) printf x
#define ASSERT(x) {if (!(x)) \
                   printf("assertion(%s) failed at line : %d\n",#x,__LINE__);}
#define CHECK_HANDLE(x) check_handle(x)
#else
#define DBGMSG(x)
#define ASSERT(x)
#define CHECK_HANDLE(x)
#endif

/*
 * Globals ...protect all with a global synchronization object.
 */

static int Current_handle = 0;
static int Use_devpoll = 0;
static int Max_index = 0;

/*
 * Per Poller object data.
 * Must be synchronized on a per Poller object basis.
 */

typedef struct ioevent {
  int inuse;
  int devpollfd;
  int last_index;
  int total_free;
  int left_events;
  int max_index;
  pollfd_t *pfd;
} ioevent_t;

static ioevent_t IOE_handles[MAX_HANDLES];

/*
 * Exceptions to be thrown.
 * Note : assuming all illegal argument and NULL pointer checks
 *        have already been done by the Java calling methods.
 */
static jint throwOutOfMemoryError(JNIEnv *env, const char * cause)
{
  (*env)->ThrowNew(env, (*env)->FindClass(env,"java/lang/OutOfMemoryError"),
                   cause);
  return -1;
}
static jint throwInterruptedIOException(JNIEnv *env, const char * cause)
{
  (*env)->ThrowNew(env,
                   (*env)->FindClass(env,"java/io/InterruptedIOException"),
                   cause);
  return -1;
}
static jint throwIllegalStateException(JNIEnv *env, const char * cause)
{
  (*env)->ThrowNew(env,
                   (*env)->FindClass(env,"java/lang/IllegalStateException"),
                   cause);
  return -1;
}

#define MEMORY_EXCEPTION(str) throwOutOfMemoryError(env, "Poller:" ## str)
#define STATE_EXCEPTION(str)  throwIllegalStateException(env, "Poller:" ## str)
#define INTERRUPT_EXCEPTION(str) throwInterruptedIOException(env, \
                                                             "Poller:" ## str)
jint addfd(JNIEnv *, ioevent_t *, jint, jshort);
jint removefd(JNIEnv *, ioevent_t *, jint);

/*
 * Class Poller
 * Method: nativeInit
 * Signature: ()I
 *
 * Only to be called once, right after this library is loaded,
 * so no need to deal with reentrancy here.
 * Could do as a pragma ini, but that isn't as portable.
 */
JNIEXPORT jint JNICALL Java_Poller_nativeInit(JNIEnv *env, jclass cls)
{
  int testdevpollfd;
  int i;

#ifdef DEVPOLL
  /*
   * See if we can use this much faster method
   * Note : must have fix for BUGID # 4223353 or OS can crash!
   */
  testdevpollfd = open("/dev/poll",O_RDWR);
  if (testdevpollfd >= 0) {
    /*
     * If Solaris 7, we need a patch
     * Until we know what string to search for, we'll play it
     * safe and disable this for Solaris 7.
     */

    if (!strcmp(name.release,"5.7"))
      {
        Use_devpoll = 0;
      }
    else
      {
        Use_devpoll = 1;
      }
  }

  DBGMSG(("Use_devpoll=%d\n" ,Use_devpoll));
  close(testdevpollfd);
#endif

  /*
   * For now, we optimize for Solaris 7 if /dev/poll isn't
   * available, as it is only a small % hit for Solaris < 7.
   * if ( (Use_devpoll == 0) && !strcmp(name.release,"5.6") )
   *      Use_sol7opt = 0;
   */
  Current_handle = 0;
  for (i = 0; i < MAX_HANDLES; i++) {
    IOE_handles[i].devpollfd = -1;
    IOE_handles[i].pfd = NULL;
  }

  /*
   * this tells me the max number of open filedescriptors
   */
  Max_index = sysconf(_SC_OPEN_MAX);
  if (Max_index < 0) {
    Max_index = 1024;
  }

  DBGMSG(("got sysconf(_SC_OPEN_MAX)=%d file desc\n",Max_index));

  return 0;
}

JNIEXPORT jint JNICALL Java_Poller_getNumCPUs(JNIEnv *env, jclass cls)
{
  return sysconf(_SC_NPROCESSORS_ONLN);
}

/*
 * Class:     Poller
 * Method:    nativeCreatePoller
 * Signature: (I)I
 * Note : in the case where /dev/poll doesn't exist,
 *        using more than one poll array could hurt
 *        Solaris 7 performance due to kernel caching.
 */

JNIEXPORT jint JNICALL Java_Poller_nativeCreatePoller
  (JNIEnv *env, jobject obj, jint maximum_fds)
{
  int handle, retval, i;
  ioevent_t *ioeh;

  if (maximum_fds == -1) {
    maximum_fds = Max_index;
  }
  handle = Current_handle;
  if (Current_handle >= MAX_HANDLES) {
    for (i = 0; i < MAX_HANDLES; i++) {
      if (IOE_handles[i].inuse == 0) {
        handle = i;
        break;
      }
    }
    if (handle >= MAX_HANDLES) {
      return MEMORY_EXCEPTION("CreatePoller - MAX_HANDLES exceeded");
    }
  } else {
    Current_handle++;
  }

  ioeh = &IOE_handles[handle];

  ioeh->inuse      = 1;

  ioeh->last_index = 0;
  ioeh->total_free = 0;
  ioeh->left_events = 0;
  ioeh->max_index = maximum_fds;

  retval = handle;
  if (Use_devpoll) {
    ioeh->devpollfd = open("/dev/poll",O_RDWR);
    DBGMSG(("Opened /dev/poll, set devpollfd = %d\n",ioeh->devpollfd));
    if (ioeh->devpollfd < 0) {
      Current_handle--;
      return MEMORY_EXCEPTION("CreatePoller - can\'t open /dev/poll");
    }
  }
  ioeh->pfd = malloc(maximum_fds * sizeof(pollfd_t));
  if (ioeh->pfd == NULL) {
    Current_handle--;
    return MEMORY_EXCEPTION("CreatePoller - malloc failure");
  }

  return retval;
}

/*
 * Class:     Poller
 * Method:    nativeDestroyPoller
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_Poller_nativeDestroyPoller
  (JNIEnv *env, jobject obj, jint handle)
{

  ioevent_t *ioeh;

  if (handle < 0 || handle > MAX_HANDLES)
    {
      STATE_EXCEPTION("DestroyPoller - handle out of range");
      return;
    }

  ioeh = &IOE_handles[handle];
  ioeh->inuse = 0;
  if (Use_devpoll) {
    close(ioeh->devpollfd);
  }
  free(ioeh->pfd);
}

#ifdef DEBUG
static void check_handle(ioevent_t *ioeh)
{
  int i,used,unused;

  used=unused=0;
  for (i = 0; i < ioeh->last_index; i++)
    {
      if (ioeh->pfd[i].fd == -1)
        unused++;
      else
        used++;
    }
  if (unused != ioeh->total_free)
    printf("WARNING : found %d free, claimed %d.  Used : %d\n",
           unused, ioeh->total_free, used);
}
#endif

/*
 * Class:     Poller
 * Method:    nativeAddFd
 * Signature: (IIS)I
 *
 * Currently doesn't check to make sure we aren't adding
 * an fd already added (no problem for /dev/poll...just
 * an array waster for poll()).
 */
JNIEXPORT jint JNICALL Java_Poller_nativeAddFd
  (JNIEnv *env, jobject obj, jint handle, jint fd, jshort events)
{
  int retval;
  ioevent_t *ioeh;

  if (handle < 0 || handle > MAX_HANDLES)
    return STATE_EXCEPTION("AddFd - handle out of range");

  ioeh = &IOE_handles[handle];

  CHECK_HANDLE(ioeh);

  #ifdef DEVPOLL
  if (Use_devpoll)
    {
      int i;
      pollfd_t pollelt;

      /*
       * use /dev/poll
       */
      pollelt.fd = fd;
      pollelt.events = events;
      if ((i = write(ioeh->devpollfd, &pollelt, sizeof(pollfd_t))) !=
          sizeof(pollfd_t)) {
        DBGMSG(("write to devpollfd=%d showed %d bytes out of %d\n",
                ioeh->devpollfd,i,sizeof(pollfd_t)));
        return STATE_EXCEPTION("AddFd - /dev/poll add failure");
      }
    else
      {
        retval = fd;
      }
    }
  else
  #endif
    { /* no /dev/poll available */
      retval = addfd(env, ioeh, fd, events);
    }
  return retval;
}

/*
 * Addfd to pollfd array...optimized for Solaris 7
 */
jint addfd(JNIEnv *env, ioevent_t *ioeh, jint fd, jshort events)
{
  int idx;

  if (ioeh->total_free)
    {
      /*
       * Traversing from end because that's where we pad.
       */
      ioeh->total_free--;
      for (idx = ioeh->last_index - 1; idx >= 0; idx--) {
        if (ioeh->pfd[idx].fd == -1)
          break;
      }
    }
  else if (ioeh->last_index >= ioeh->max_index)
    {
      return MEMORY_EXCEPTION("AddFd - too many fds");
    }
  else
    {
      int i;
      int new_total;
      /*
       * For Solaris 7, want to add some growth space
       * and fill extras with fd=-1.  This allows for
       * kernel poll() implementation to perform optimally.
       */
      new_total = ioeh->last_index;
      new_total += (new_total/10) + 1; /* bump size by 10% */
      if (new_total > ioeh->max_index)
        new_total = ioeh->max_index;
      for (i = ioeh->last_index; i <= new_total; i++)
        {
          ioeh->pfd[i].fd = -1;
        }
      idx = ioeh->last_index;
      ioeh->total_free = new_total - ioeh->last_index - 1;
      DBGMSG(("Just grew from %d to %d in size\n",
              ioeh->last_index, new_total));
      ioeh->last_index = new_total;
    }
  ASSERT((idx >= 0) && (idx <= ioeh->max_index));
  ASSERT(ioeh->pfd[idx].fd == -1);
  ioeh->pfd[idx].fd = fd;
  ioeh->pfd[idx].events = events;
  ioeh->pfd[idx].revents = 0;

  CHECK_HANDLE(ioeh);

  return fd;
}

/*
 * Class:     Poller
 * Method:    nativeRemoveFd
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_Poller_nativeRemoveFd
  (JNIEnv *env, jobject obj, jint handle, jint fd)
{
  ioevent_t *ioeh;

  if (handle < 0 || handle > MAX_HANDLES)
    return STATE_EXCEPTION("RemoveFd - handle out of range");

  ioeh = &IOE_handles[handle];

  #ifdef DEVPOLL
  if (Use_devpoll)
    {
      /*
       * use /dev/poll - currently no need for locking here.
       */
      pollfd_t pollelt;

      pollelt.fd = fd;
      pollelt.events = POLLREMOVE;
      if (write(ioeh->devpollfd, &pollelt,
                sizeof(pollfd_t) ) != sizeof(pollfd_t))
        {
          return STATE_EXCEPTION("RemoveFd - /dev/poll failure");
        }
    }
  else
  #endif DEVPOLL
    {
      return removefd(env, ioeh,fd);
    }
}
/*
 * remove from pollfd array...optimize for Solaris 7
 */
jint removefd(JNIEnv *env, ioevent_t *ioeh, jint fd)
{
  int i;
  int found = 0;

    { /* !Use_devpoll */
      for (i = 0; i < ioeh->last_index; i++)
        {
          if (ioeh->pfd[i].fd == fd)
            {
              ioeh->pfd[i].fd = -1;
              found = 1;
              break;
            }
        }
      if (!found)
        {
          return STATE_EXCEPTION("RemoveFd - no such fd");
        }
      ioeh->left_events = 0; /* Have to go back to the kernel */
      ioeh->total_free++;
      /*
       * Shrinking pool if > 33% empty. Just don't do this often!
       */
      if ( (ioeh->last_index > 100) &&
           (ioeh->total_free > (ioeh->last_index / 3)) )
        {
          int j;
          /*
           * we'll just bite the bullet here, since we're > 33% empty.
           * walk through and eliminate -1 fd values, shrink total
           * size to still have ~ 10 fd==-1 values at end.
           * Start at end (since we pad here) and, when we find fd != -1,
           * swap with an earlier fd == -1 until we have all -1 values
           * at the end.
           */
          CHECK_HANDLE(ioeh);
          for (i = ioeh->last_index - 1, j = 0; i > j; i--)
            {
              if (ioeh->pfd[i].fd != -1)
                {
                  while ( (j < i) && (ioeh->pfd[j].fd != -1) )
                    j++;
                  DBGMSG( ("i=%d,j=%d,ioeh->pfd[j].fd=%d\n",
                           i, j, ioeh->pfd[j].fd) );
                  if (j < i)
                      {
                        ASSERT(ioeh->pfd[j].fd == -1);
                        ioeh->pfd[j].fd = ioeh->pfd[i].fd;
                        ioeh->pfd[j].events = ioeh->pfd[i].events;
                        ioeh->pfd[i].fd = -1;
                      }
                }
            }
          DBGMSG(("Just shrunk from %d to %d in size\n",
                  ioeh->last_index, j+11));
          ioeh->last_index = j + 11; /* last_index always 1 greater */
          ioeh->total_free = 10;
          CHECK_HANDLE(ioeh);
        }
    } /* !Use_devpoll */

  return 1;
}

/*
 * Class:     Poller
 * Method:    nativeIsMember
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_Poller_nativeIsMember
  (JNIEnv *env, jobject obj, jint handle, jint fd)
{
  int found = 0;
  int i;
  ioevent_t *ioeh;

  if (handle < 0 || handle > MAX_HANDLES)
    return STATE_EXCEPTION("IsMember - handle out of range");

  ioeh = &IOE_handles[handle];

  #ifdef DEVPOLL
  if (Use_devpoll)
    {
      pollfd_t pfd;
      /*
       * DEVPOLL ioctl DP_ISPOLLED call to determine if fd is polled.
       */
      pfd.fd = fd;
      pfd.events = 0;
      pfd.revents = 0;
      found = ioctl(ioeh->devpollfd, DP_ISPOLLED, &pfd);
      if (found == -1)
        {
          return STATE_EXCEPTION("IsMember - /dev/poll failure");
        }
    }
  else
  #endif
    {
      for (i = 0; i < ioeh->last_index; i++)
        {
          if (fd == ioeh->pfd[i].fd)
            {
              found = 1;
              break;
            }
        }
    }

  return found;
}

/*
 * Class:     Poller
 * Method:    nativeWait
 * Signature: (II[I[SJ)I
 */
JNIEXPORT jint JNICALL Java_Poller_nativeWait
  (JNIEnv *env, jobject obj, jint handle, jint maxEvents,
   jintArray jfds, jshortArray jrevents, jlong timeout)
{
  int useEvents, count, idx;
  short *reventp;
  jint  *fdp;
  int   retval;
  ioevent_t *ioeh;
  jboolean isCopy1,isCopy2;

  if (handle < 0 || handle > MAX_HANDLES)
    return STATE_EXCEPTION("nativeWait - handle out of range");

  ioeh = &IOE_handles[handle];

  if (maxEvents == 0) /* just doing a kernel delay! */
    {
      useEvents = poll(NULL,0L,timeout);
      return 0;
    }

  #ifdef DEVPOLL
  if (Use_devpoll)
    {
      struct dvpoll dopoll;
      /*
       * DEVPOLL ioctl DP_POLL call, reading
       */
      dopoll.dp_timeout = timeout;
      dopoll.dp_nfds=maxEvents;
      dopoll.dp_fds=ioeh->pfd;

      useEvents = ioctl(ioeh->devpollfd, DP_POLL, &dopoll);
      while ((useEvents == -1) && (errno == EAGAIN))
            useEvents = ioctl(ioeh->devpollfd, DP_POLL, &dopoll);

      if (useEvents == -1)
        {
          if (errno == EINTR)
            return INTERRUPT_EXCEPTION("nativeWait - /dev/poll failure EINTR");
          else
            return STATE_EXCEPTION("nativeWait - /dev/poll failure");
        }

      reventp =(*env)->GetShortArrayElements(env,jrevents,&isCopy1);
      fdp =(*env)->GetIntArrayElements(env,jfds,&isCopy2);
      for (idx = 0,count = 0; idx < useEvents; idx++)
        {
          if (ioeh->pfd[idx].revents)
            {
              fdp[count] = ioeh->pfd[idx].fd;
              reventp[count] = ioeh->pfd[idx].revents;
              count++;
            }
        }
      if (count < useEvents)
        return STATE_EXCEPTION("Wait - Corrupted internals");

      if (isCopy1 == JNI_TRUE)
        (*env)->ReleaseShortArrayElements(env,jrevents,reventp,0);
      if (isCopy2 == JNI_TRUE)
        (*env)->ReleaseIntArrayElements(env,jfds,fdp,0);
    }
  else
  #endif
    { /* !Use_devpoll */

    /* no leftovers=>go to kernel */
      if (ioeh->left_events == 0)
        {
          useEvents = poll(ioeh->pfd,ioeh->last_index, timeout);
          while ((useEvents == -1) && (errno == EAGAIN))
            useEvents = poll(ioeh->pfd,ioeh->last_index, timeout);
          if (useEvents == -1)
            {
              if (errno == EINTR)
                return INTERRUPT_EXCEPTION("Wait - poll() failure EINTR-" \
                                           "IO interrupted.");
              else if (errno == EINVAL)
                return STATE_EXCEPTION("Wait - poll() failure EINVAL-" \
                                       "invalid args (is fdlim cur < max?)");
              else
                return STATE_EXCEPTION("Wait - poll() failure");
            }
          ioeh->left_events = useEvents;
          DBGMSG(("waitnative : poll returns : %d\n",useEvents));
        }
      else
        {  /* left over from last call */
          useEvents = ioeh->left_events;
        }

      if (useEvents > maxEvents)
        {
          useEvents = maxEvents;
        }

      ioeh->left_events -= useEvents; /* left to process */

      DBGMSG(("waitnative : left %d, use %d, max %d\n",ioeh->left_events,
              useEvents,maxEvents));

      if (useEvents > 0)
        {
          reventp =(*env)->GetShortArrayElements(env,jrevents,&isCopy1);
          fdp =(*env)->GetIntArrayElements(env,jfds,&isCopy2);
          for (idx = 0,count = 0; (idx < ioeh->last_index) &&
                 (count < useEvents); idx++)
            {
              if (ioeh->pfd[idx].revents)
                {
                  fdp[count] = ioeh->pfd[idx].fd;
                  reventp[count] = ioeh->pfd[idx].revents;
                  /* in case of leftover for next walk */
                  ioeh->pfd[idx].revents = 0;
                  count++;
                }
            }
          if (count < useEvents)
            {
              ioeh->left_events = 0;
              return STATE_EXCEPTION("Wait - Corrupted internals");
            }
          if (isCopy1 == JNI_TRUE)
            (*env)->ReleaseShortArrayElements(env,jrevents,reventp,0);
          if (isCopy2 == JNI_TRUE)
            (*env)->ReleaseIntArrayElements(env,jfds,fdp,0);
        }
    } /* !Use_devpoll */

  return useEvents;
}
