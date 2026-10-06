package io.github.imecuadorian.emitta.accesskey.application.service;

import io.github.imecuadorian.emitta.accesskey.application.command.GenerateAccessKeyCommand;
import io.github.imecuadorian.emitta.accesskey.application.exception.FiscalEnvironmentDisabledException;
import io.github.imecuadorian.emitta.accesskey.application.exception.FiscalResourceInactiveException;
import io.github.imecuadorian.emitta.accesskey.application.exception.FiscalResourceNotFoundException;
import io.github.imecuadorian.emitta.accesskey.application.model.GeneratedAccessKey;
import io.github.imecuadorian.emitta.accesskey.application.port.in.GenerateAccessKeyUseCase;
import io.github.imecuadorian.emitta.accesskey.application.port.out.NumericCodeGenerator;
import io.github.imecuadorian.emitta.accesskey.domain.AccessKey;
import io.github.imecuadorian.emitta.accesskey.domain.AccessKeyComponents;
import io.github.imecuadorian.emitta.accesskey.domain.AccessKeyGenerator;
import io.github.imecuadorian.emitta.documentsequence.application.command.AllocateSequentialCommand;
import io.github.imecuadorian.emitta.documentsequence.application.port.in.AllocateSequentialUseCase;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalData;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalData;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalData;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalLookupUseCase;

import java.util.Objects;
import java.util.UUID;

public final class GenerateAccessKeyService
        implements GenerateAccessKeyUseCase {

    private final PointOfIssueFiscalLookupUseCase
            pointOfIssueLookup;

    private final EstablishmentFiscalLookupUseCase
            establishmentLookup;

    private final TaxpayerFiscalLookupUseCase
            taxpayerLookup;

    private final AllocateSequentialUseCase
            allocateSequentialUseCase;

    private final NumericCodeGenerator
            numericCodeGenerator;

    public GenerateAccessKeyService(
            PointOfIssueFiscalLookupUseCase pointOfIssueLookup,
            EstablishmentFiscalLookupUseCase establishmentLookup,
            TaxpayerFiscalLookupUseCase taxpayerLookup,
            AllocateSequentialUseCase allocateSequentialUseCase,
            NumericCodeGenerator numericCodeGenerator
    ) {
        this.pointOfIssueLookup =
                Objects.requireNonNull(pointOfIssueLookup);

        this.establishmentLookup =
                Objects.requireNonNull(establishmentLookup);

        this.taxpayerLookup =
                Objects.requireNonNull(taxpayerLookup);

        this.allocateSequentialUseCase =
                Objects.requireNonNull(
                        allocateSequentialUseCase
                );

        this.numericCodeGenerator =
                Objects.requireNonNull(
                        numericCodeGenerator
                );
    }

    @Override
    public GeneratedAccessKey generate(
            GenerateAccessKeyCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Generate access key command cannot be null"
        );

        UUID pointOfIssueId =
                Objects.requireNonNull(
                        command.pointOfIssueId(),
                        "Point of issue id cannot be null"
                );

        Objects.requireNonNull(
                command.documentType(),
                "Document type cannot be null"
        );

        Objects.requireNonNull(
                command.environment(),
                "Fiscal environment cannot be null"
        );

        Objects.requireNonNull(
                command.issueDate(),
                "Issue date cannot be null"
        );

        PointOfIssueFiscalData pointOfIssue =
                pointOfIssueLookup
                        .findFiscalDataById(
                                pointOfIssueId
                        )
                        .orElseThrow(
                                () ->
                                        new FiscalResourceNotFoundException(
                                                "Point of issue",
                                                pointOfIssueId
                                        )
                        );

        requireActive(
                "Point of issue",
                pointOfIssue.id(),
                pointOfIssue.active()
        );

        EstablishmentFiscalData establishment =
                establishmentLookup
                        .findFiscalDataById(
                                pointOfIssue.establishmentId()
                        )
                        .orElseThrow(
                                () ->
                                        new FiscalResourceNotFoundException(
                                                "Establishment",
                                                pointOfIssue.establishmentId()
                                        )
                        );

        requireActive(
                "Establishment",
                establishment.id(),
                establishment.active()
        );

        TaxpayerFiscalData taxpayer =
                taxpayerLookup
                        .findFiscalDataById(
                                establishment.taxpayerId()
                        )
                        .orElseThrow(
                                () ->
                                        new FiscalResourceNotFoundException(
                                                "Taxpayer",
                                                establishment.taxpayerId()
                                        )
                        );

        requireActive(
                "Taxpayer",
                taxpayer.id(),
                taxpayer.active()
        );

        requireEnvironmentEnabled(
                taxpayer,
                command.environment()
        );

        SequentialNumber sequential =
                allocateSequentialUseCase.allocate(
                        new AllocateSequentialCommand(
                                pointOfIssue.id(),
                                command.documentType(),
                                command.environment()
                        )
                );

        String numericCode =
                numericCodeGenerator.generate();

        AccessKeyComponents components =
                new AccessKeyComponents(
                        command.issueDate(),
                        command.documentType(),
                        taxpayer.ruc(),
                        command.environment(),
                        establishment.code(),
                        pointOfIssue.code(),
                        sequential.formatted(),
                        numericCode
                );

        AccessKey accessKey =
                AccessKeyGenerator.generate(
                        components
                );

        return new GeneratedAccessKey(
                accessKey,
                sequential,
                numericCode
        );
    }

    private static void requireActive(
            String resource,
            UUID resourceId,
            boolean active
    ) {
        if (!active) {
            throw new FiscalResourceInactiveException(
                    resource,
                    resourceId
            );
        }
    }

    private static void requireEnvironmentEnabled(
            TaxpayerFiscalData taxpayer,
            FiscalEnvironment environment
    ) {

        boolean enabled =
                switch (environment) {
                    case TEST ->
                            taxpayer.testEnabled();

                    case PRODUCTION ->
                            taxpayer.productionEnabled();
                };

        if (!enabled) {
            throw new FiscalEnvironmentDisabledException(
                    taxpayer.id(),
                    environment
            );
        }
    }
}