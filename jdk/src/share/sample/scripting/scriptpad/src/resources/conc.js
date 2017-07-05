/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * Concurrency utilities for JavaScript. These are based on
 * java.lang and java.util.concurrent API. The following functions 
 * provide a simpler API for scripts. Instead of directly using java.lang
 * and java.util.concurrent classes, scripts can use functions and
 * objects exported from here. 
 */

/**
 * Wrapper for java.lang.Object.wait
 *
 * can be called only within a sync method
 */
function wait(object) {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var waitMethod = objClazz.getMethod('wait', null);
    waitMethod.invoke(object, null);
}
wait.docString = "convenient wrapper for java.lang.Object.wait method";


/**
 * Wrapper for java.lang.Object.notify
 *
 * can be called only within a sync method
 */
function notify(object) {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var notifyMethod = objClazz.getMethod('notify', null);
    notifyMethod.invoke(object, null);
}
notify.docString = "convenient wrapper for java.lang.Object.notify method";


/**
 * Wrapper for java.lang.Object.notifyAll
 *
 * can be called only within a sync method
 */
function notifyAll(object)  {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var notifyAllMethod = objClazz.getMethod('notifyAll', null);
    notifyAllMethod.invoke(object, null);
}
notifyAll.docString = "convenient wrapper for java.lang.Object.notifyAll method";


/**
 * Creates a java.lang.Runnable from a given script
 * function.
 */
Function.prototype.runnable = function() {
    var args = arguments;
    var func = this;
    return new java.lang.Runnable() {
        run: function() {
            func.apply(null, args);
        }
    }
}

/**
 * Executes the function on a new Java Thread.
 */
Function.prototype.thread = function() {
    var t = new java.lang.Thread(this.runnable.apply(this, arguments));
    t.start();
    return t;
}

/**
 * Executes the function on a new Java daemon Thread.
 */
Function.prototype.daemon = function() {
    var t = new java.lang.Thread(this.runnable.apply(this, arguments));
    t.setDaemon(true);
    t.start();
    return t;
}

/**
 * Creates a java.util.concurrent.Callable from a given script
 * function.
 */
Function.prototype.callable = function() {
    var args = arguments;
    var func = this;
    return new java.util.concurrent.Callable() {
          call: function() { return func.apply(null, args); }
    }
}

/**
 * Registers the script function so that it will be called exit.
 */
Function.prototype.atexit = function () {
    var args = arguments;
    java.lang.Runtime.getRuntime().addShutdownHook(
         new java.lang.Thread(this.runnable.apply(this, args)));
}

/**
 * Executes the function asynchronously.  
 *
 * @return a java.util.concurrent.FutureTask
 */
Function.prototype.future = (function() {
    // default executor for future
    var juc = java.util.concurrent;
    var theExecutor = juc.Executors.newSingleThreadExecutor();
    // clean-up the default executor at exit
    (function() { theExecutor.shutdown(); }).atexit();
    return function() {
        return theExecutor.submit(this.callable.apply(this, arguments));
    }
})();

// shortcut for j.u.c lock classes
var Lock = java.util.concurrent.locks.ReentrantLock;
var RWLock = java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Executes a function after acquiring given lock. On return,
 * (normal or exceptional), lock is released.
 *
 * @param lock lock that is locked and unlocked
 */
Function.prototype.sync = function (lock) {
    if (arguments.length == 0) {
        throw "lock is missing";
    }
    var res = new Array(arguments.length - 1);
    for (var i = 0; i < res.length; i++) {
        res[i] = arguments[i + 1];
    }
    lock.lock();
    try {
        this.apply(null, res);
    } finally {
        lock.unlock();
    }
}

/**
 * Causes current thread to sleep for specified
 * number of milliseconds
 *
 * @param interval in milliseconds
 */
function sleep(interval) {
    java.lang.Thread.sleep(interval);
}
sleep.docString = "wrapper for java.lang.Thread.sleep method";

/**
 * Schedules a task to be executed once in
 * every N milliseconds specified. 
 *
 * @param callback function or expression to evaluate
 * @param interval in milliseconds to sleep
 * @return timeout ID (which is nothing but Thread instance)
 */
function setTimeout(callback, interval) {
    if (! (callback instanceof Function)) {
        callback = new Function(callback);
    }

    // start a new thread that sleeps given time
    // and calls callback in an infinite loop
    return (function() {
         while (true) {
             sleep(interval);
             callback();
         }
    }).daemon();
}
setTimeout.docString = "calls given callback once after specified interval"

/** 
 * Cancels a timeout set earlier.
 * @param tid timeout ID returned from setTimeout
 */
function clearTimeout(tid) {
    // we just interrupt the timer thread
    tid.interrupt();
}

/**
 * Simple access to thread local storage. 
 *
 * Script sample:
 *
 *  __thread.x = 44;
 *  function f() { 
 *      __thread.x = 'hello'; 
 *      print(__thread.x); 
 *  }
 *  f.thread();       // prints 'hello'
 * print(__thread.x); // prints 44 in main thread
 */
var __thread = (function () {
    var map = new Object();
    return new JSAdapter() {
        __has__: function(name) {
            return map[name] != undefined;
        },
        __get__: function(name) {
            if (map[name] != undefined) {
                return map[name].get();
            } else {
                return undefined;
            }
        },
        __put__: sync(function(name, value) {
            if (map[name] == undefined) {
                var tmp = new java.lang.ThreadLocal();
                tmp.set(value);
                map[name] = tmp;
            } else {
                map[name].set(value);
            }
        }),
        __delete__: function(name) {
            if (map[name] != undefined) {
                map[name].set(null);
            }            
        }
    }
})();

