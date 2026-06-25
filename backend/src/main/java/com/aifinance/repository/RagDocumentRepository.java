package com.aifinance.repository;

import com.aifinance.entity.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagDocumentRepository extends JpaRepository<RagDocument, Long> {
    List<RagDocument> findAllByOrderByUpdatedAtDesc();

    long countByDocType(String docType);
}
