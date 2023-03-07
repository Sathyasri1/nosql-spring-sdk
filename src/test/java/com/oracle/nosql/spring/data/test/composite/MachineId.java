/*-
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.  All rights reserved.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 *  https://oss.oracle.com/licenses/upl/
 */

package com.oracle.nosql.spring.data.test.composite;

import com.oracle.nosql.spring.data.core.mapping.NoSqlKey;
import com.oracle.nosql.spring.data.core.mapping.NoSqlKeyClass;

import java.io.Serializable;
import java.util.Objects;

@NoSqlKeyClass
public class MachineId implements Serializable {
    private String version;
    private String name;

    public MachineId(String version, String name) {
        this.version = version;
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MachineId)) {
            return false;
        }
        MachineId machineId = (MachineId) o;
        return Objects.equals(version, machineId.version) &&
                Objects.equals(name, machineId.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, name);
    }
}
