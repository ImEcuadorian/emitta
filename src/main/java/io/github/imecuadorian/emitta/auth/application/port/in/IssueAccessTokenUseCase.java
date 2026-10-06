package io.github.imecuadorian.emitta.auth.application.port.in;

import io.github.imecuadorian.emitta.auth.application.command.IssueAccessTokenCommand;
import io.github.imecuadorian.emitta.auth.application.model.IssuedAccessToken;

public interface IssueAccessTokenUseCase {

    IssuedAccessToken issue(
            IssueAccessTokenCommand command
    );
}