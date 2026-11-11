package com.guardpay.learning.domain;

/** Append-only learning audit event types. */
public enum LearningEventType {
  DATASET_BUILT,
  DATASET_INVALIDATED,
  TRAINING_FAILED,
  CANDIDATE_TRAINED,
  MODEL_INVALIDATED,
  CANDIDATE_APPROVED,
  CANDIDATE_REJECTED,
  MODEL_DEPLOYED,
  MODEL_RETIRED,
  MODEL_ROLLED_BACK
}
