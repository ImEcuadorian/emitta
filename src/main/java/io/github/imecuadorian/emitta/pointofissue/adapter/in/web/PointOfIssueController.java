package io.github.imecuadorian.emitta.pointofissue.adapter.in.web;

import io.github.imecuadorian.emitta.pointofissue.application.command.CreatePointOfIssueCommand;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.CreatePointOfIssueUseCase;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/establishments/{establishmentId}/points-of-issue"
)
@Tag(
        name = "Points of Issue",
        description = "Fiscal point of issue administration"
)
public class PointOfIssueController {

    private final CreatePointOfIssueUseCase createPointOfIssueUseCase;

    public PointOfIssueController(
            CreatePointOfIssueUseCase createPointOfIssueUseCase
    ) {
        this.createPointOfIssueUseCase =
                createPointOfIssueUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Create a point of issue",
            description = """
                    Creates a fiscal point of issue under an
                    existing establishment.
                    """
    )
    public ResponseEntity<PointOfIssueResponse> create(
            @PathVariable UUID establishmentId,
            @Valid @RequestBody CreatePointOfIssueRequest request
    ) {

        CreatePointOfIssueCommand command =
                new CreatePointOfIssueCommand(
                        establishmentId,
                        request.code(),
                        request.name()
                );

        PointOfIssue pointOfIssue =
                createPointOfIssueUseCase.create(
                        command
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        PointOfIssueResponse.from(
                                pointOfIssue
                        )
                );
    }
}