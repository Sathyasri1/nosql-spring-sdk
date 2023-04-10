/*-
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.  All rights reserved.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 *  https://oss.oracle.com/licenses/upl/
 */

package com.oracle.nosql.spring.data.test.composite;

import com.oracle.nosql.spring.data.repository.NosqlRepository;
import com.oracle.nosql.spring.data.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MachineRepository extends NosqlRepository<Machine, MachineId> {
    //basic
    List<Machine> findByMachineIdVersion(String version);
    List<Machine> findByMachineIdName(String name);
    List<Machine> findByLocation(String location);

    //and
    List<Machine> findByMachineIdNameAndMachineIdVersion(String name,
                                                         String version);
    List<Machine> findByMachineIdNameAndLocation(String name, String location);
    //or
    List<Machine> findByMachineIdNameOrMachineIdVersion(String name,
                                                        String version);

    //sorting and paging
    List<Machine> findByMachineIdVersionOrderByMachineIdNameAsc(String version);
    Page<Machine> findByMachineIdName(String name, Pageable pageable);

    //native
    @Query("SELECT * FROM Machine m WHERE m.VERSION='version1'")
    List<Machine> findByVersionNative();

    //Ignore case
    List<Machine> findByMachineIdNameIgnoreCase(String name);

    //projection
    List<MachineProjection> findAllByLocation(String location);
    List<MachineProjectionDTO> findAllByMachineIdName(String name);
}