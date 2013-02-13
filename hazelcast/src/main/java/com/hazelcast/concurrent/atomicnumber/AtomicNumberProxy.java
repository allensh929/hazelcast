/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.concurrent.atomicnumber;

import com.hazelcast.core.AtomicNumber;
import com.hazelcast.monitor.LocalAtomicNumberStats;
import com.hazelcast.spi.AbstractDistributedObject;
import com.hazelcast.spi.Invocation;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.util.ExceptionUtil;

import java.util.concurrent.Future;

// author: sancar - 21.12.2012
public class AtomicNumberProxy extends AbstractDistributedObject<AtomicNumberService> implements AtomicNumber {

    private final String name;
    private final int partitionId;

    public AtomicNumberProxy(String name, NodeEngine nodeEngine) {
        super(nodeEngine, null);
        this.name = name;
        this.partitionId = nodeEngine.getPartitionService().getPartitionId(nodeEngine.toData(name));
    }

    public String getName() {
        return name;
    }

    public long addAndGet(long delta) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            AddAndGetOperation operation = new AddAndGetOperation(name, delta);
            Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(AtomicNumberService.SERVICE_NAME, operation, partitionId).build();
            Future f = inv.invoke();
            return (Long) f.get();
        } catch (Throwable throwable) {
            return (Long) ExceptionUtil.rethrow(throwable);
        }
    }

    public boolean compareAndSet(long expect, long update) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            CompareAndSetOperation operation = new CompareAndSetOperation(name, expect, update);
            Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(AtomicNumberService.SERVICE_NAME, operation, partitionId).build();
            Future f = inv.invoke();
            return (Boolean) f.get();
        } catch (Throwable throwable) {
            return (Boolean) ExceptionUtil.rethrow(throwable);
        }
    }

    public long decrementAndGet() {
        return addAndGet(-1);
    }

    public long incrementAndGet() {
        return addAndGet(1);
    }

    public long getAndIncrement() {
        return getAndAdd(1);
    }

    public long get() {
        return addAndGet(0);
    }

    public long getAndAdd(long delta) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            GetAndAddOperation operation = new GetAndAddOperation(name, delta);
            Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(AtomicNumberService.SERVICE_NAME, operation, partitionId).build();
            Future f = inv.invoke();
            return (Long) f.get();
        } catch (Throwable throwable) {
            return (Long) ExceptionUtil.rethrow(throwable);
        }
    }

    public long getAndSet(long newValue) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            GetAndSetOperation operation = new GetAndSetOperation(name, newValue);
            Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(AtomicNumberService.SERVICE_NAME, operation, partitionId).build();
            Future f = inv.invoke();
            return (Long) f.get();
        } catch (Throwable throwable) {
            return (Long) ExceptionUtil.rethrow(throwable);
        }
    }

    public void set(long newValue) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            SetOperation operation = new SetOperation(name, newValue);
            Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(AtomicNumberService.SERVICE_NAME, operation, partitionId).build();
            inv.invoke();
        } catch (Throwable throwable) {
            ExceptionUtil.rethrow(throwable);
        }
    }

    public LocalAtomicNumberStats getLocalAtomicNumberStats() {
        return null;
    }

    public Object getId() {
        return name;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public String getServiceName() {
        return AtomicNumberService.SERVICE_NAME;
    }
}