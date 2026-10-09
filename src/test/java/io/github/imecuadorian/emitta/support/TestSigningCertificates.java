package io.github.imecuadorian.emitta.support;

import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;

/** Test-only CA and leaf. Never use these generated identities with SRI. */
public final class TestSigningCertificates {
    public record Fixture(byte[] pkcs12, X509Certificate leaf, X509Certificate ca, List<java.security.cert.X509CRL> crls) {}
    private TestSigningCertificates() {}

    public static Fixture create(String password) {
        return create(password,false);
    }

    public static Fixture create(String password, boolean revoked) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("emitta-test-cert-");
            Path caStore = directory.resolve("ca.p12"), leafStore = directory.resolve("leaf.p12");
            Path caCert = directory.resolve("ca.cer"), request = directory.resolve("request.csr"), reply = directory.resolve("reply.cer");
            keytool("-genkeypair","-alias","ca","-keyalg","RSA","-keysize","2048","-dname","CN=Emitta Test CA",
                    "-startdate","2026/01/01 00:00:00","-validity","3650","-ext","bc:c","-ext","ku=keyCertSign,cRLSign",
                    "-storetype","PKCS12","-keystore",caStore.toString(),"-storepass",password,"-noprompt");
            keytool("-exportcert","-alias","ca","-keystore",caStore.toString(),"-storepass",password,"-file",caCert.toString());
            keytool("-genkeypair","-alias","emitta-test","-keyalg","RSA","-keysize","2048",
                    "-dname","CN=Emitta Test Signer,SERIALNUMBER=1790012345001,OID.2.5.4.97=1790012345001,C=EC",
                    "-startdate","2026/01/01 00:00:00","-validity","3650","-storetype","PKCS12",
                    "-keystore",leafStore.toString(),"-storepass",password,"-noprompt");
            keytool("-certreq","-alias","emitta-test","-keystore",leafStore.toString(),"-storepass",password,"-file",request.toString());
            keytool("-gencert","-alias","ca","-keystore",caStore.toString(),"-storepass",password,
                    "-infile",request.toString(),"-outfile",reply.toString(),"-startdate","2026/01/01 00:00:00",
                    "-validity","3650","-ext","ku=digitalSignature,nonRepudiation","-ext","bc=ca:false");
            keytool("-importcert","-alias","ca","-keystore",leafStore.toString(),"-storepass",password,"-file",caCert.toString(),"-noprompt");
            keytool("-importcert","-alias","emitta-test","-keystore",leafStore.toString(),"-storepass",password,"-file",reply.toString(),"-noprompt");
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(leafStore)) { store.load(input,password.toCharArray()); }
            Path crlFile=directory.resolve("test.crl");
            List<String> crlArgs=new ArrayList<>(List.of("-gencrl","-alias","ca","-keystore",caStore.toString(),
                    "-storepass",password,"-file",crlFile.toString()));
            if (revoked) crlArgs.addAll(List.of("-id",((X509Certificate)store.getCertificate("emitta-test")).getSerialNumber().toString()));
            keytool(crlArgs.toArray(String[]::new));
            java.security.cert.X509CRL crl;
            try (var input=Files.newInputStream(crlFile)) {
                crl=(java.security.cert.X509CRL)java.security.cert.CertificateFactory.getInstance("X.509").generateCRL(input);
            }
            return new Fixture(Files.readAllBytes(leafStore),(X509Certificate)store.getCertificate("emitta-test"),
                    (X509Certificate)store.getCertificate("ca"),List.of(crl));
        } catch (Exception e) { throw new IllegalStateException("Unable to generate test certificate chain",e); }
        finally {
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (Exception e) { throw new IllegalStateException("Cannot remove temporary test keys",e); }
            }
        }
    }

    private static void keytool(String... args) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"),"bin",windows ? "keytool.exe" : "keytool").toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var output = process.getInputStream()) { output.readAllBytes(); }
        if (process.waitFor()!=0) throw new IllegalStateException("Test keytool failed");
    }
}
