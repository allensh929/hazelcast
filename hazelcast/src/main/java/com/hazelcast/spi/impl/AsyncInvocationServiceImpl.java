package com.hazelcast.spi.impl;

import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.AsyncInvocationService;
import com.hazelcast.spi.Invocation;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.util.ResponseQueueFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * @mdogan 1/21/13
 */
public class AsyncInvocationServiceImpl implements AsyncInvocationService {

    private static final int TIMEOUT = 10;
    private static final int CAPACITY = 1000;

    private final NodeEngine nodeEngine;
    private final Executor executor;
    private final ILogger logger;
    private final AtomicInteger size = new AtomicInteger(0);

    public AsyncInvocationServiceImpl(NodeEngineImpl nodeEngine) {
        this.nodeEngine = nodeEngine;
        String executorName = "hz:async-service";
        this.executor = nodeEngine.getExecutionService().getExecutor(executorName);
        logger = nodeEngine.getLogger(AsyncInvocationService.class.getName());
    }

    public Future invoke(final Invocation invocation) {
        final FutureProxy futureProxy = new FutureProxy();
        invoke((InvocationImpl) invocation, futureProxy);
        return futureProxy;
    }

    public void invoke(Invocation invocation, ExecutionCallback callback) {
        invoke((InvocationImpl) invocation, new ExecutionCallbackAdapter(callback));
    }

    private void invoke(final InvocationImpl invocation, final Callback<Object> responseCallback) {
        if (size.get() >= CAPACITY) {
            logger.log(Level.WARNING, "Capacity overloaded! Executing " + invocation
                    + " in current thread, instead of invoking asynchronously.");
            invokeInCurrentThread(invocation, responseCallback);
        } else {
            size.incrementAndGet();
            executor.execute(new AsyncInvocation(invocation, responseCallback));
        }
    }

    private void invokeInCurrentThread(InvocationImpl invocation, Callback<Object> responseCallback) {
        invocation.invoke();
        waitAndSetResult(invocation, responseCallback, Long.MAX_VALUE);
    }

    private boolean waitAndSetResult(InvocationImpl invocation, Callback<Object> responseCallback, long timeout) {
        Object result = null;
        try {
            result = invocation.doGet(timeout, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            result = e;
        }
        if (result != InvocationImpl.TIMEOUT_RESPONSE) { // if timeout is smaller than long.max
            responseCallback.notify(result);
            return true;
        }
        return false;
    }

    private class AsyncInvocation implements Runnable {
        private final InvocationImpl invocation;
        private final Callback<Object> responseCallback;

        private AsyncInvocation(InvocationImpl invocation, Callback<Object> responseCallback) {
            this.invocation = invocation;
            this.responseCallback = responseCallback;
        }

        public void run() {
            try {
                invocation.setCallback(new AsyncInvocationCallback(responseCallback));
                invocation.invoke();
            } finally {
                size.decrementAndGet();
            }
        }
    }

    private class AsyncInvocationCallback implements Callback<InvocationImpl> {

        private final Callback<Object> responseCallback;

        private AsyncInvocationCallback(Callback<Object> responseCallback) {
            this.responseCallback = responseCallback;
        }

        public void notify(InvocationImpl invocation) {
            if (responseCallback instanceof FutureProxy) {
                waitAndSetResult(invocation, responseCallback, TIMEOUT);
            } else {
                executor.execute(new AsyncInvocationNotifier(invocation, responseCallback));
            }
        }
    }

    private class AsyncInvocationNotifier implements Runnable {
        private final InvocationImpl invocation;
        private final Callback<Object> responseCallback;

        private AsyncInvocationNotifier(InvocationImpl invocation, Callback<Object> responseCallback) {
            this.invocation = invocation;
            this.responseCallback = responseCallback;
        }

        public void run() {
            waitAndSetResult(invocation, responseCallback, TIMEOUT);
        }
    }

    private class ExecutionCallbackAdapter implements Callback<Object> {

        private final ExecutionCallback executionCallback;

        private ExecutionCallbackAdapter(ExecutionCallback executionCallback) {
            this.executionCallback = executionCallback;
        }

        public void notify(Object response) {
            try {
                if (response instanceof Throwable) {
                    executionCallback.onFailure((Throwable) response);
                } else {
                    executionCallback.onResponse(response);
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    private class FutureProxy implements Future, Callback<Object> {

        private final BlockingQueue<Object> ref = ResponseQueueFactory.newResponseQueue();
        private volatile boolean done = false;

        public Object get() throws InterruptedException, ExecutionException {
            final Object result = ref.take();
            try {
                return InvocationImpl.resolveResponse(result);
            } catch (TimeoutException e) {
                throw new HazelcastException("Never happens!", e);
            }
        }

        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            final Object result = ref.poll(timeout, unit);
            if (result == null) {
                throw new TimeoutException();
            }
            return InvocationImpl.resolveResponse(result);
        }

        public void notify(Object response) {
            ref.offer(response);
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            done = true;
            return false;
        }

        public boolean isCancelled() {
            return false;
        }

        public boolean isDone() {
            return done;
        }
    }

    void shutdown() {
    }

}
