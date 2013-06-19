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

package com.hazelcast.map.client;

import com.hazelcast.client.ClientEndpoint;
import com.hazelcast.client.InvocationClientRequest;
import com.hazelcast.client.RetryableRequest;
import com.hazelcast.instance.MemberImpl;
import com.hazelcast.map.*;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import com.hazelcast.query.Predicate;
import com.hazelcast.spi.Invocation;
import com.hazelcast.util.ExceptionUtil;
import com.hazelcast.util.IterationType;
import com.hazelcast.util.QueryDataResultStream;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

import static com.hazelcast.map.MapService.SERVICE_NAME;

abstract class AbstractMapQueryRequest extends InvocationClientRequest implements Portable, RetryableRequest {

    private String name;
    private IterationType iterationType;

    public AbstractMapQueryRequest() {
    }

    public AbstractMapQueryRequest(String name, IterationType iterationType) {
        this.name = name;
        this.iterationType = iterationType;
    }

    @Override
    protected final void invoke() {
        Collection<MemberImpl> members = getClientEngine().getClusterService().getMemberList();
        int partitionCount = getClientEngine().getPartitionService().getPartitionCount();
        Set<Integer> plist = new HashSet<Integer>(partitionCount);
        final ClientEndpoint endpoint = getEndpoint();
        QueryDataResultStream result = new QueryDataResultStream(iterationType, true);
        try {
            List<Future> flist = new ArrayList<Future>();
            final Predicate predicate = getPredicate();
            for (MemberImpl member : members) {
                Invocation invocation = createInvocationBuilder(SERVICE_NAME, new QueryOperation(name, predicate), member.getAddress()).build();
                Future future = invocation.invoke();
                flist.add(future);
            }
            for (Future future : flist) {
                QueryResult queryResult = (QueryResult) future.get();
                if (queryResult != null) {
                    final List<Integer> partitionIds = queryResult.getPartitionIds();
                    if (partitionIds != null) {
                        plist.addAll(partitionIds);
                        result.addAll(queryResult.getResult());
                    }
                }
            }
            if (plist.size() == partitionCount) {
                getClientEngine().sendResponse(endpoint, result);
            }

            List<Integer> missingList = new ArrayList<Integer>();
            for (int i = 0; i < partitionCount; i++) {
                if (!plist.contains(i)) {
                    missingList.add(i);
                }
            }
            List<Future> futures = new ArrayList<Future>(missingList.size());
            for (Integer pid : missingList) {
                QueryPartitionOperation queryPartitionOperation = new QueryPartitionOperation(name, predicate);
                queryPartitionOperation.setPartitionId(pid);
                try {
                    Future f = createInvocationBuilder(SERVICE_NAME, queryPartitionOperation, pid).build().invoke();
                    futures.add(f);
                } catch (Throwable t) {
                    throw ExceptionUtil.rethrow(t);
                }
            }
            for (Future future : futures) {
                QueryResult queryResult = (QueryResult) future.get();
                result.addAll(queryResult.getResult());
            }
        } catch (Throwable t) {
            throw ExceptionUtil.rethrow(t);
        } finally {
            result.end();
        }

        getClientEngine().sendResponse(endpoint, result);
    }

    protected abstract Predicate getPredicate();

    public final String getServiceName() {
        return MapService.SERVICE_NAME;
    }

    public final int getFactoryId() {
        return MapPortableHook.F_ID;
    }

    public void writePortable(PortableWriter writer) throws IOException {
        writer.writeUTF("n", name);
        writer.writeUTF("t", iterationType.toString());
        writePortableInner(writer);
    }

    protected abstract void writePortableInner(PortableWriter writer) throws IOException;

    public void readPortable(PortableReader reader) throws IOException {
        name = reader.readUTF("n");
        iterationType = IterationType.valueOf(reader.readUTF("t"));
        readPortableInner(reader);
    }

    protected abstract void readPortableInner(PortableReader reader) throws IOException;
}