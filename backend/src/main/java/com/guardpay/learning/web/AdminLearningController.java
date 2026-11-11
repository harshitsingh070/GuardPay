package com.guardpay.learning.web;

import com.guardpay.intelligence.web.AdminGuard;
import com.guardpay.learning.service.LearningDatasetService;
import com.guardpay.learning.service.LearningModelService;
import com.guardpay.learning.web.dto.LearningDtos.DatasetDto;
import com.guardpay.learning.web.dto.LearningDtos.EventDto;
import com.guardpay.learning.web.dto.LearningDtos.ExclusionDto;
import com.guardpay.learning.web.dto.LearningDtos.ModelDto;
import com.guardpay.learning.web.dto.LearningDtos.OverviewDto;
import com.guardpay.learning.web.dto.LearningDtos.TrainingResultDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only continuous-learning console: datasets, candidates, deployment. */
@RestController
@RequestMapping("/api/admin/learning")
public class AdminLearningController {

  private final LearningDatasetService datasets;
  private final LearningModelService models;
  private final DemoUserService demoUser;
  private final AdminGuard adminGuard;

  public AdminLearningController(
      LearningDatasetService datasets,
      LearningModelService models,
      DemoUserService demoUser,
      AdminGuard adminGuard) {
    this.datasets = datasets;
    this.models = models;
    this.demoUser = demoUser;
    this.adminGuard = adminGuard;
  }

  @GetMapping("/overview")
  public OverviewDto overview() {
    adminGuard.requireAdmin(demoUser.currentUser());
    return models.overview();
  }

  @GetMapping("/datasets")
  public List<DatasetDto> listDatasets() {
    adminGuard.requireAdmin(demoUser.currentUser());
    return datasets.list();
  }

  @PostMapping("/datasets")
  public DatasetDto buildDataset() {
    User admin = requireAdmin();
    return datasets.build(admin.getId());
  }

  @GetMapping("/datasets/{datasetId}")
  public DatasetDto dataset(@PathVariable("datasetId") UUID datasetId) {
    adminGuard.requireAdmin(demoUser.currentUser());
    return datasets.get(datasetId);
  }

  @GetMapping("/datasets/{datasetId}/exclusions")
  public List<ExclusionDto> exclusions(@PathVariable("datasetId") UUID datasetId) {
    adminGuard.requireAdmin(demoUser.currentUser());
    datasets.require(datasetId);
    return datasets.exclusions(datasetId).stream()
        .map(
            exclusion ->
                new ExclusionDto(
                    exclusion.getFeedbackId() == null
                        ? null
                        : exclusion.getFeedbackId().toString(),
                    exclusion.getReasonCode(),
                    exclusion.getDetail(),
                    exclusion.getCreatedAt()))
        .toList();
  }

  @PostMapping("/datasets/{datasetId}/train")
  public TrainingResultDto train(@PathVariable("datasetId") UUID datasetId) {
    User admin = requireAdmin();
    return models.train(datasetId, admin.getId());
  }

  @GetMapping("/models")
  public List<ModelDto> listModels() {
    adminGuard.requireAdmin(demoUser.currentUser());
    return models.list();
  }

  @GetMapping("/models/{modelId}")
  public ModelDto model(@PathVariable("modelId") UUID modelId) {
    adminGuard.requireAdmin(demoUser.currentUser());
    return models.get(modelId);
  }

  @PostMapping("/models/{modelId}/approve")
  public ModelDto approve(@PathVariable("modelId") UUID modelId) {
    User admin = requireAdmin();
    return models.approve(modelId, admin.getId());
  }

  @PostMapping("/models/{modelId}/reject")
  public ModelDto reject(@PathVariable("modelId") UUID modelId) {
    User admin = requireAdmin();
    return models.reject(modelId, admin.getId());
  }

  @PostMapping("/models/{modelId}/deploy")
  public ModelDto deploy(@PathVariable("modelId") UUID modelId) {
    User admin = requireAdmin();
    return models.deploy(modelId, admin.getId());
  }

  @PostMapping("/models/{modelId}/rollback")
  public ModelDto rollback(@PathVariable("modelId") UUID modelId) {
    User admin = requireAdmin();
    return models.rollback(modelId, admin.getId());
  }

  @GetMapping("/events")
  public List<EventDto> events() {
    adminGuard.requireAdmin(demoUser.currentUser());
    return models.events();
  }

  private User requireAdmin() {
    User admin = demoUser.currentUser();
    adminGuard.requireAdmin(admin);
    return admin;
  }
}
