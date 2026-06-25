package com.labs.server.repository;

import com.labs.server.entity.LabRejectionReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabRejectionReasonRepository extends JpaRepository<LabRejectionReason, String> {

    List<LabRejectionReason> findByActiveTrueOrderByDisplayOrderAsc();

    List<LabRejectionReason> findAllByOrderByDisplayOrderAsc();
}
