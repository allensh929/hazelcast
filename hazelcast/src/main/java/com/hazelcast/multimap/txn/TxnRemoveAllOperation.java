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

package com.hazelcast.multimap.txn;

import com.hazelcast.multimap.*;
import com.hazelcast.multimap.operations.MultiMapKeyBasedOperation;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.util.Clock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * @author ali 4/10/13
 */
public class TxnRemoveAllOperation extends MultiMapKeyBasedOperation {

    Collection<Long> recordIds;
    transient long begin = -1;
    transient Collection<MultiMapRecord> removed;

    public TxnRemoveAllOperation() {
    }

    public TxnRemoveAllOperation(String name, Data dataKey, Collection<MultiMapRecord> records) {
        super(name, dataKey);
        this.recordIds = new ArrayList<Long>();
        for (MultiMapRecord record: records){
            recordIds.add(record.getRecordId());
        }
    }

    public void run() throws Exception {
        begin = Clock.currentTimeMillis();
        MultiMapContainer container = getOrCreateContainer();
        MultiMapWrapper wrapper = container.getOrCreateMultiMapWrapper(dataKey);
        response = true;
        for (Long recordId: recordIds){
            if(!wrapper.containsRecordId(recordId)){
                response = false;
                return;
            }
        }
        Collection<MultiMapRecord> coll = wrapper.getCollection();
        removed = new LinkedList<MultiMapRecord>();
        for (Long recordId: recordIds){
            Iterator<MultiMapRecord> iter = coll.iterator();
            while (iter.hasNext()){
                MultiMapRecord record = iter.next();
                if (record.getRecordId() == recordId){
                    iter.remove();
                    removed.add(record);
                    break;
                }
            }
        }
        if (coll.isEmpty()) {
            remove();
        }

    }

    public void afterRun() throws Exception {
        long elapsed = Math.max(0, Clock.currentTimeMillis()-begin);
        final MultiMapService service = getService();
        service.getLocalMultiMapStatsImpl(name).incrementRemoves(elapsed);
        if (removed != null) {
            getOrCreateContainer().update();
            for (MultiMapRecord record : removed) {
                publishEvent(EntryEventType.REMOVED, dataKey, record.getObject());
            }
        }
    }

    public Collection<Long> getRecordIds() {
        return recordIds;
    }

    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeInt(recordIds.size());
        for (Long recordId: recordIds){
            out.writeLong(recordId);
        }
    }

    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        int size = in.readInt();
        recordIds = new ArrayList<Long>();
        for (int i=0; i<size; i++){
            recordIds.add(in.readLong());
        }
    }

    public int getId() {
        return MultiMapDataSerializerHook.TXN_REMOVE_ALL;
    }
}
