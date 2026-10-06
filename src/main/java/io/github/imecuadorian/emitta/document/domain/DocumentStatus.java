package io.github.imecuadorian.emitta.document.domain;

public enum DocumentStatus {

    RECEIVED,
    QUEUED,
    GENERATING,
    SIGNED,
    SUBMITTED,
    RETRY_PENDING,
    AUTHORIZED,
    REJECTED,
    FAILED
}