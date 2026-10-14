package io.github.imecuadorian.emitta.outbox.application.port.out;

import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxClaimPort {
    default java.util.Set<UUID> markPublishedBatch(List<UUID> ids,String workerId,Instant publishedAt) {
        var marked=new java.util.HashSet<UUID>();
        for(var id:ids) if(markPublished(id,workerId,publishedAt))marked.add(id);
        return marked;
    }

    List<PendingOutboxEvent> claimBatch(
            String workerId,
            int batchSize,
            Instant staleBefore
    );

    boolean markPublished(
            UUID eventId,
            String workerId,
            Instant publishedAt
    );

    boolean releaseAfterFailure(
            UUID eventId,
            String workerId,
            Instant nextAttemptAt,
            String error
    );
}
