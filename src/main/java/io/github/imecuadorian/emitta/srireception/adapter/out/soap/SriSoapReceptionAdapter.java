package io.github.imecuadorian.emitta.srireception.adapter.out.soap;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionMessage;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionRequest;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionPort;
import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SriSoapReceptionAdapter
        implements SriReceptionPort {

    private static final String SOAP_CONTENT_TYPE =
            "text/xml; charset=UTF-8";

    private final HttpClient httpClient;

    private final URI testEndpoint;
    private final URI productionEndpoint;

    private final Duration requestTimeout;

    public SriSoapReceptionAdapter(
            HttpClient httpClient,
            URI testEndpoint,
            URI productionEndpoint,
            Duration requestTimeout
    ) {

        this.httpClient =
                Objects.requireNonNull(
                        httpClient
                );

        this.testEndpoint =
                Objects.requireNonNull(
                        testEndpoint
                );

        this.productionEndpoint =
                Objects.requireNonNull(
                        productionEndpoint
                );

        this.requestTimeout =
                Objects.requireNonNull(
                        requestTimeout
                );

        if (
                requestTimeout.isZero()
                        || requestTimeout.isNegative()
        ) {

            throw new IllegalArgumentException(
                    "SRI reception timeout must be positive"
            );
        }
    }

    @Override
    public SriReceptionResult submit(
            SriReceptionRequest request
    ) {

        Objects.requireNonNull(
                request,
                "SRI reception request cannot be null"
        );

        URI endpoint =
                endpointFor(
                        request.environment()
                );

        byte[] envelope =
                buildSoapEnvelope(
                        request.signedXml()
                );

        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(
                                endpoint
                        )
                        .timeout(
                                requestTimeout
                        )
                        .header(
                                "Content-Type",
                                SOAP_CONTENT_TYPE
                        )
                        .header(
                                "SOAPAction",
                                "\"\""
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofByteArray(
                                        envelope
                                )
                        )
                        .build();

        HttpResponse<byte[]> response;

        try {

            response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofByteArray()
                    );

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();

            throw new SriReceptionException(
                    "SRI reception request was interrupted",
                    exception
            );

        } catch (IOException exception) {

            throw new SriReceptionException(
                    "Unable to communicate with SRI reception service",
                    exception
            );
        }

        if (
                response.statusCode() < 200
                        || response.statusCode() >= 300
        ) {

            throw new SriReceptionException(
                    "SRI reception service returned HTTP "
                            + response.statusCode()
            );
        }

        return parseResponse(
                response.body()
        );
    }

    private URI endpointFor(
            FiscalEnvironment environment
    ) {

        return switch (environment) {

            case TEST ->
                    testEndpoint;

            case PRODUCTION ->
                    productionEndpoint;
        };
    }

    private static byte[] buildSoapEnvelope(
            byte[] signedXml
    ) {

        String encodedXml =
                Base64.getEncoder()
                        .encodeToString(
                                signedXml
                        );

        String envelope =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope
                    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:ec="http://ec.gob.sri.ws.recepcion">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <ec:validarComprobante>
                            <xml>%s</xml>
                        </ec:validarComprobante>
                    </soapenv:Body>
                </soapenv:Envelope>
                """
                        .formatted(
                                encodedXml
                        );

        return envelope.getBytes(
                StandardCharsets.UTF_8
        );
    }

    private static SriReceptionResult parseResponse(
            byte[] response
    ) {

        Document document =
                parseXml(
                        response
                );

        NodeList faults =
                document.getElementsByTagNameNS(
                        "*",
                        "Fault"
                );

        if (faults.getLength() > 0) {

            Element fault =
                    (Element) faults.item(
                            0
                    );

            String faultMessage =
                    optionalText(
                            fault,
                            "faultstring"
                    );

            throw new SriReceptionException(
                    faultMessage == null
                            ? "SRI reception SOAP Fault"
                            : "SRI reception SOAP Fault: "
                            + faultMessage
            );
        }

        String rawStatus =
                requiredText(
                        document.getDocumentElement(),
                        "estado"
                );

        SriReceptionStatus status =
                switch (
                        rawStatus
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                )
                        ) {

                    case "RECIBIDA" ->
                            SriReceptionStatus.RECEIVED;

                    case "DEVUELTA" ->
                            SriReceptionStatus.RETURNED;

                    default ->
                            throw new SriReceptionException(
                                    "Unknown SRI reception status: "
                                            + rawStatus
                            );
                };

        List<SriReceptionMessage> messages =
                parseMessages(
                        document
                );

        return new SriReceptionResult(
                status,
                messages
        );
    }

    private static List<SriReceptionMessage> parseMessages(
            Document document
    ) {

        NodeList messageNodes =
                document.getElementsByTagNameNS(
                        "*",
                        "mensaje"
                );

        List<SriReceptionMessage> messages =
                new ArrayList<>();

        for (
                int index = 0;
                index < messageNodes.getLength();
                index++
        ) {

            Node node =
                    messageNodes.item(
                            index
                    );

            if (
                    !(node instanceof Element element)
            ) {

                continue;
            }

            String identifier =
                    optionalText(
                            element,
                            "identificador"
                    );

            String message =
                    optionalText(
                            element,
                            "mensaje"
                    );

            String additionalInformation =
                    optionalText(
                            element,
                            "informacionAdicional"
                    );

            String type =
                    optionalText(
                            element,
                            "tipo"
                    );

            if (identifier == null || message == null) {
                continue;
            }

            messages.add(
                    new SriReceptionMessage(
                            identifier,
                            message,
                            additionalInformation,
                            Objects.requireNonNullElse(
                                    type,
                                    "NO_INFORMADO"
                            )
                    )
            );
        }

        return List.copyOf(
                messages
        );
    }

    private static Document parseXml(
            byte[] xml
    ) {

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory
                            .newInstance();

            factory.setNamespaceAware(
                    true
            );

            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );

            return factory
                    .newDocumentBuilder()
                    .parse(
                            new ByteArrayInputStream(
                                    xml
                            )
                    );

        } catch (Exception exception) {

            throw new SriReceptionException(
                    "Invalid SOAP response from SRI reception service",
                    exception
            );
        }
    }

    private static String requiredText(
            Element root,
            String localName
    ) {

        String value =
                optionalText(
                        root,
                        localName
                );

        if (value == null) {

            throw new SriReceptionException(
                    "SRI reception response does not contain "
                            + localName
            );
        }

        return value;
    }

    private static String optionalText(
            Element root,
            String localName
    ) {

        NodeList nodes =
                root.getElementsByTagNameNS(
                        "*",
                        localName
                );

        if (nodes.getLength() == 0) {

            return null;
        }

        String value =
                nodes.item(0)
                        .getTextContent();

        if (value == null) {

            return null;
        }

        value =
                value.trim();

        return value.isEmpty()
                ? null
                : value;
    }
}