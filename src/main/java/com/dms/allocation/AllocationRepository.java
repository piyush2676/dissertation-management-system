package com.dms.allocation;

import org.springframework.data.jpa.repository.JpaRepository;
public interface AllocationRepository extends JpaRepository<Allocation, Long>
{
}
