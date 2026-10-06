package io.github.imecuadorian.emitta.accesskey.application.port.in;

import io.github.imecuadorian.emitta.accesskey.application.command.GenerateAccessKeyCommand;
import io.github.imecuadorian.emitta.accesskey.application.model.GeneratedAccessKey;

public interface GenerateAccessKeyUseCase {

    GeneratedAccessKey generate(
            GenerateAccessKeyCommand command
    );
}