package com.clearing.book;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<TransferEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transfers SET status = 'HELD' WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int markHeld(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transfers SET status = 'CLEARED' WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int markCleared(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transfers SET status = 'POSTED' WHERE id = :id AND status IN ('PENDING', 'CLEARED')", nativeQuery = true)
    int markPosted(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transfers SET status = 'RETURNED' WHERE id = :id AND status IN ('PENDING', 'CLEARED')", nativeQuery = true)
    int markReturned(@Param("id") String id);
}
