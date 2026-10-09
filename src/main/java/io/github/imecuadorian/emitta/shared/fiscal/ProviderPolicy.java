package io.github.imecuadorian.emitta.shared.fiscal;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Operator-reviewed regulatory decision; never derived from the issuer or certificate. */
public record ProviderPolicy(Mode mode, String ruc, String justification,
                             String normativeReference, String evidencePath, String evidenceSha256) {
    public enum Mode { UNRESOLVED, EXTERNAL_PROVIDER, VERIFIED_NOT_APPLICABLE }

    public ProviderPolicy {
        Objects.requireNonNull(mode);
        if (mode == Mode.EXTERNAL_PROVIDER && (ruc == null || !ruc.matches("[0-9]{13}")))
            throw new IllegalArgumentException("An identified external provider requires a 13-digit RUC");
        if (mode == Mode.VERIFIED_NOT_APPLICABLE && ruc != null && !ruc.isBlank())
            throw new IllegalArgumentException("An omission decision cannot contain a provider RUC");
    }

    public static ProviderPolicy unresolved() {
        return new ProviderPolicy(Mode.UNRESOLVED, null, null, null, null, null);
    }

    public String xmlProviderRuc() {
        if (mode == Mode.UNRESOLVED)
            throw new IllegalStateException("Provider regulatory scenario is unresolved; generation/submission blocked");
        verifyReview();
        return mode == Mode.EXTERNAL_PROVIDER ? ruc : null;
    }

    private void verifyReview() {
        if (justification == null || justification.isBlank() || normativeReference == null
                || evidencePath == null || evidenceSha256 == null || !evidenceSha256.matches("[a-fA-F0-9]{64}"))
            throw new IllegalStateException("A documented operator review with verifiable evidence is required");
        try {
            URI reference = URI.create(normativeReference);
            String host = reference.getHost();
            if (!"https".equals(reference.getScheme()) || host == null
                    || !(host.equals("sri.gob.ec") || host.endsWith(".sri.gob.ec")
                    || host.equals("registroficial.gob.ec") || host.endsWith(".registroficial.gob.ec")))
                throw new IllegalStateException("Review must cite an official SRI/Registro Oficial source");
            Path path = Path.of(evidencePath);
            if (!Files.isRegularFile(path) || Files.size(path) == 0)
                throw new IllegalStateException("Regulatory evidence is missing");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(evidenceSha256))
                throw new IllegalStateException("Regulatory evidence hash does not match reviewed evidence");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot verify regulatory evidence");
        }
    }
}
