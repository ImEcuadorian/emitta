package io.github.imecuadorian.emitta.documentsequence.application.port.in;

import io.github.imecuadorian.emitta.documentsequence.application.command.AllocateSequentialCommand;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;

public interface AllocateSequentialUseCase {

    SequentialNumber allocate(
            AllocateSequentialCommand command
    );
}