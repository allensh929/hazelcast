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

package com.hazelcast.map;

import com.hazelcast.nio.IOUtil;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.AbstractOperation;

import java.io.IOException;

public class AddInterceptorOperation extends AbstractOperation {

    MapService mapService;
    String id;
    MapInterceptor mapInterceptor;
    String mapName;


    public AddInterceptorOperation(String id, MapInterceptor mapInterceptor, String mapName) {
        this.id = id;
        this.mapInterceptor = mapInterceptor;
        this.mapName = mapName;
    }

    public AddInterceptorOperation() {
    }

    public void run() {
        mapService = getService();
        mapService.getMapContainer(mapName).addInterceptor(mapInterceptor, id);
    }

    @Override
    public boolean returnsResponse() {
        return true;
    }

    @Override
    public void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        mapName = in.readUTF();
        id = in.readUTF();
        mapInterceptor = IOUtil.readNullableObject(in);
    }

    @Override
    public void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeUTF(mapName);
        out.writeUTF(id);
        IOUtil.writeNullableObject(out, mapInterceptor);
    }

    @Override
    public String toString() {
        return "AddInterceptorOperation{}";
    }

}