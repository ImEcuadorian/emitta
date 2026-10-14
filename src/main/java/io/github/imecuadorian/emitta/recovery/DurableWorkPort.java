package io.github.imecuadorian.emitta.recovery;
import java.util.UUID;
import java.util.Optional;
import java.time.Instant;
public interface DurableWorkPort {
 enum Kind { FISCAL,RIDE }
 record Job(UUID id,UUID tenantId,UUID documentId,Kind kind,String state,int attempts,UUID leaseToken,
            Instant nextAttemptAt,String failureCategory) {}
 void enqueue(Kind kind,UUID tenantId,UUID documentId);
 record AdmissionTarget(UUID tenantId,UUID documentId) {}
 default void enqueueFiscalBatch(java.util.List<AdmissionTarget> targets) {
  for(var target:targets)enqueue(Kind.FISCAL,target.tenantId(),target.documentId());
 }
 Optional<Job> find(UUID tenantId,UUID documentId,Kind kind);
 boolean recover(UUID tenantId,UUID jobId);
 default boolean recoverAdmission(UUID tenantId,UUID documentId,UUID eventId) {
  throw new UnsupportedOperationException("Admission recovery unavailable");
 }
}
