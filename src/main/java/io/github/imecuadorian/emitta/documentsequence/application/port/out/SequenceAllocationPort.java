package io.github.imecuadorian.emitta.documentsequence.application.port.out;

import io.github.imecuadorian.emitta.documentsequence.domain.SequenceScope;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;

public interface SequenceAllocationPort {

    SequentialNumber allocateNext(
            SequenceScope scope
    );
}