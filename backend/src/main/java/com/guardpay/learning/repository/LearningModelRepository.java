package com.guardpay.learning.repository;

import com.guardpay.learning.domain.LearningModel;
import com.guardpay.learning.domain.ModelStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for versioned calibration candidates and the active champion. */
public interface LearningModelRepository extends JpaRepository<LearningModel, UUID> {

  Optional<LearningModel> findByModelVersion(String modelVersion);

  List<LearningModel> findAllByOrderByCreatedAtDesc();

  List<LearningModel> findByStatusOrderByCreatedAtDesc(ModelStatus status);

  List<LearningModel> findByStatusInOrderByCreatedAtDesc(Collection<ModelStatus> statuses);

  List<LearningModel> findByDatasetId(UUID datasetId);

  Optional<LearningModel> findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
      ModelStatus status, String modelType);

  Optional<LearningModel> findFirstByStatusInAndModelTypeOrderByActivatedAtDesc(
      Collection<ModelStatus> statuses, String modelType);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from LearningModel m where m.id = :id")
  Optional<LearningModel> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select m from LearningModel m
      where m.modelType = :modelType
        and m.status = com.guardpay.learning.domain.ModelStatus.ACTIVE
      """)
  List<LearningModel> findActiveByType(@Param("modelType") String modelType);

  long countByStatus(ModelStatus status);
}
