package com.guardpay.learning.repository;

import com.guardpay.learning.domain.DatasetStatus;
import com.guardpay.learning.domain.LearningDataset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for immutable dataset snapshots. */
public interface LearningDatasetRepository extends JpaRepository<LearningDataset, UUID> {

  Optional<LearningDataset> findByDatasetVersion(String datasetVersion);

  Optional<LearningDataset> findByChecksum(String checksum);

  List<LearningDataset> findAllByOrderByBuiltAtDesc();

  List<LearningDataset> findByStatusInOrderByBuiltAtDesc(Collection<DatasetStatus> statuses);

  long countByStatus(DatasetStatus status);
}
