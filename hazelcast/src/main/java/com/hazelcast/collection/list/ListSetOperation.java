package com.hazelcast.collection.list;

import com.hazelcast.collection.CollectionDataSerializerHook;
import com.hazelcast.collection.CollectionItem;
import com.hazelcast.collection.CollectionBackupAwareOperation;
import com.hazelcast.core.ItemEventType;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.Operation;

import java.io.IOException;

/**
 * @ali 8/31/13
 */
public class ListSetOperation extends CollectionBackupAwareOperation {

    private int index;

    private Data value;

    private transient long itemId = -1;

    private transient long oldItemId = -1;

    public ListSetOperation() {
    }

    public ListSetOperation(String name, int index, Data value) {
        super(name);
        this.index = index;
        this.value = value;
    }

    public boolean shouldBackup() {
        return true;
    }

    public Operation getBackupOperation() {
        return new ListSetBackupOperation(name, oldItemId, itemId, value);
    }

    public int getId() {
        return CollectionDataSerializerHook.LIST_SET;
    }

    public void beforeRun() throws Exception {

    }

    public void run() throws Exception {
        final ListContainer container = getOrCreateListContainer();
        itemId = container.nextId();
        final CollectionItem item = container.set(index, itemId, value);
        oldItemId = item.getItemId();
        response = item.getValue();
    }

    public void afterRun() throws Exception {
        publishEvent(ItemEventType.REMOVED, (Data)response);
        publishEvent(ItemEventType.ADDED, value);
    }

    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeInt(index);
        value.writeData(out);
    }

    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        index = in.readInt();
        value = new Data();
        value.readData(in);
    }
}
