/*
 *      Copyright (C) 2012-2015 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.core;

import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;

class ProtocolEvent {

    public enum Type { TOPOLOGY_CHANGE, STATUS_CHANGE, SCHEMA_CHANGE }

    public final Type type;

    private ProtocolEvent(Type type) {
        this.type = type;
    }

    public static ProtocolEvent deserialize(ByteBuf bb, ProtocolVersion version) {
        switch (CBUtil.readEnumValue(Type.class, bb)) {
            case TOPOLOGY_CHANGE:
                return TopologyChange.deserializeEvent(bb);
            case STATUS_CHANGE:
                return StatusChange.deserializeEvent(bb);
            case SCHEMA_CHANGE:
                return SchemaChange.deserializeEvent(bb, version);
        }
        throw new AssertionError();
    }

    public static class TopologyChange extends ProtocolEvent {
        public enum Change { NEW_NODE, REMOVED_NODE, MOVED_NODE }

        public final Change change;
        public final InetSocketAddress node;

        private TopologyChange(Change change, InetSocketAddress node) {
            super(Type.TOPOLOGY_CHANGE);
            this.change = change;
            this.node = node;
        }

        // Assumes the type has already been deserialized
        private static TopologyChange deserializeEvent(ByteBuf bb) {
            Change change = CBUtil.readEnumValue(Change.class, bb);
            InetSocketAddress node = CBUtil.readInet(bb);
            return new TopologyChange(change, node);
        }

        @Override
        public String toString() {
            return change + " " + node;
        }
    }

    public static class StatusChange extends ProtocolEvent {

        public enum Status { UP, DOWN }

        public final Status status;
        public final InetSocketAddress node;

        private StatusChange(Status status, InetSocketAddress node) {
            super(Type.STATUS_CHANGE);
            this.status = status;
            this.node = node;
        }

        // Assumes the type has already been deserialized
        private static StatusChange deserializeEvent(ByteBuf bb) {
            Status status = CBUtil.readEnumValue(Status.class, bb);
            InetSocketAddress node = CBUtil.readInet(bb);
            return new StatusChange(status, node);
        }

        @Override
        public String toString() {
            return status + " " + node;
        }
    }

    public static class SchemaChange extends ProtocolEvent {

        public enum Change { CREATED, UPDATED, DROPPED }
        public enum Target { KEYSPACE, TABLE, TYPE }

        public final Change change;
        public final Target target;
        public final String keyspace;
        public final String name;

        public SchemaChange(Change change, Target target, String keyspace, String name) {
            super(Type.SCHEMA_CHANGE);
            this.change = change;
            this.target = target;
            this.keyspace = keyspace;
            this.name = name;
        }

        // Assumes the type has already been deserialized
        private static SchemaChange deserializeEvent(ByteBuf bb, ProtocolVersion version) {
            Change change;
            Target target;
            String keyspace, name;
            switch (version) {
                case V1:
                case V2:
                    change = CBUtil.readEnumValue(Change.class, bb);
                    keyspace = CBUtil.readString(bb);
                    name = CBUtil.readString(bb);
                    target = name.isEmpty() ? Target.KEYSPACE : Target.TABLE;
                    return new SchemaChange(change, target, keyspace, name);
                case V3:
                    change = CBUtil.readEnumValue(Change.class, bb);
                    target = CBUtil.readEnumValue(Target.class, bb);
                    keyspace = CBUtil.readString(bb);
                    name = (target == Target.KEYSPACE) ? "" : CBUtil.readString(bb);
                    return new SchemaChange(change, target, keyspace, name);
                default:
                    throw version.unsupported();
            }
        }

        @Override
        public String toString() {
            return change.toString() + ' ' + target + ' ' + keyspace + (name.isEmpty() ? "" : '.' + name);
        }
    }

}
