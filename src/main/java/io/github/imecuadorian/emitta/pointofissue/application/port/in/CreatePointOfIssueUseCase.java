package io.github.imecuadorian.emitta.pointofissue.application.port.in;

import io.github.imecuadorian.emitta.pointofissue.application.command.CreatePointOfIssueCommand;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;

public interface CreatePointOfIssueUseCase {

    PointOfIssue create(
            CreatePointOfIssueCommand command
    );
}