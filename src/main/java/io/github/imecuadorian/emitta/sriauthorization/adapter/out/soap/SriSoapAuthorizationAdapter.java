package io.github.imecuadorian.emitta.sriauthorization.adapter.out.soap;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.exception.SriAuthorizationException;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationMessage;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationPort;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SriSoapAuthorizationAdapter
        implements SriAuthorizationPort {

    private static final String SOAP_NAMESPACE =
            "http://schemas.xmlsoap.org/soap/envelope/";

    private static final String AUTHORIZATION_NAMESPACE =
            "http://ec.gob.sri.ws.autorizacion";

    private static final String SOAP_REQUEST = """
            <soap:Envelope
                xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:aut="http://ec.gob.sri.ws.autorizacion">
              <soap:Body>
                <aut:autorizacionComprobante>
                  <claveAccesoComprobante>%s</claveAccesoComprobante>
                </aut:autorizacionComprobante>
              </soap:Body>
            </soap:Envelope>
            """;

    private final HttpClient httpClient;
    private final URI testEndpoint;
    private final URI productionEndpoint;
    private final Duration requestTimeout;

    public SriSoapAuthorizationAdapter(
            HttpClient httpClient,
            URI testEndpoint,
            URI productionEndpoint,
            Duration requestTimeout
    ) {

        this.httpClient =
                Objects.requireNonNull(httpClient);

        this.testEndpoint =
                Objects.requireNonNull(testEndpoint);

        this.productionEndpoint =
                Objects.requireNonNull(productionEndpoint);

        this.requestTimeout =
                Objects.requireNonNull(requestTimeout);

        if (requestTimeout.isZero()
                || requestTimeout.isNegative()) {

            throw new IllegalArgumentException(
                    "SRI authorization timeout must be positive"
            );
        }
    }

    @Override
    public SriAuthorizationResult query(
            SriAuthorizationRequest request
    ) {

        Objects.requireNonNull(
                request,
                "SRI authorization request cannot be null"
        );

        URI endpoint =
                request.environment() == FiscalEnvironment.TEST
                        ? testEndpoint
                        : productionEndpoint;

        HttpRequest httpRequest =
                HttpRequest.newBuilder(endpoint)
                        .timeout(requestTimeout)
                        .header(
                                "Content-Type",
                                "text/xml; charset=UTF-8"
                        )
                        .header(
                                "SOAPAction",
                                "\"\""
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        SOAP_REQUEST.formatted(
                                                request.accessKey()
                                        )
                                )
                        )
                        .build();

        try {

            HttpResponse<byte[]> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofByteArray()
                    );

            return parseResponse(
                    response.body(),
                    response.statusCode(),
                    request.accessKey()
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new SriAuthorizationException(
                    "SRI authorization request was interrupted",
                    exception
            );

        } catch (IOException exception) {

            throw new SriAuthorizationException(
                    "SRI authorization communication failed",
                    exception
            );
        }
    }

    private SriAuthorizationResult parseResponse(
            byte[] responseBody,
            int httpStatus,
            String requestedAccessKey
    ) {

        Element envelope = parseXml(responseBody);

        if (!SOAP_NAMESPACE.equals(envelope.getNamespaceURI())
                || !matches(envelope, "Envelope")) {

            throw new SriAuthorizationException(
                    "Invalid SRI SOAP envelope"
            );
        }

        Element body =
                requiredChild(envelope, "Body");

        if (!SOAP_NAMESPACE.equals(body.getNamespaceURI())) {

            throw new SriAuthorizationException(
                    "Invalid SRI SOAP body"
            );
        }

        Element fault =
                child(body, "Fault");

        if (fault != null) {

            throw new SriAuthorizationException(
                    "SRI SOAP Fault: "
                            + requiredText(fault, "faultstring")
            );
        }

        if (httpStatus != 200) {

            throw new SriAuthorizationException(
                    "SRI authorization HTTP status: "
                            + httpStatus
            );
        }

        Element wrapper =
                requiredChild(
                        body,
                        "autorizacionComprobanteResponse"
                );

        if (!AUTHORIZATION_NAMESPACE.equals(
                wrapper.getNamespaceURI())) {

            throw new SriAuthorizationException(
                    "Unexpected SRI authorization namespace"
            );
        }

        Element answer =
                requiredChild(
                        wrapper,
                        "RespuestaAutorizacionComprobante"
                );

        String returnedAccessKey =
                requiredText(
                        answer,
                        "claveAccesoConsultada"
                );

        if (!requestedAccessKey.equals(returnedAccessKey)) {

            throw new SriAuthorizationException(
                    "SRI returned a different access key"
            );
        }

        String rawCount =
                optionalText(answer, "numeroComprobantes");

        Integer numberOfDocuments = null;

        if (rawCount != null) {
            try {
                numberOfDocuments =
                        Integer.parseInt(rawCount);

                if (numberOfDocuments < 0) {
                    throw new NumberFormatException(
                            "Negative document count"
                    );
                }

            } catch (NumberFormatException exception) {
                throw new SriAuthorizationException(
                        "Invalid SRI authorization document count",
                        exception
                );
            }
        }

        Element authorizations =
                child(answer, "autorizaciones");

        List<Element> entries =
                authorizations == null
                        ? List.of()
                        : children(
                        authorizations,
                        "autorizacion"
                );

        if (entries.isEmpty()) {

            if (numberOfDocuments == null
                    || numberOfDocuments == 0) {

                return new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_FOUND,
                        null,
                        null,
                        null,
                        List.of()
                );
            }

            throw new SriAuthorizationException(
                    "Unexpected SRI authorization record count"
            );
        }

        if (entries.size() != 1
                || (numberOfDocuments != null
                && numberOfDocuments != 1)) {

            throw new SriAuthorizationException(
                    "Unexpected SRI authorization record count"
            );
        }


        Element authorization =
                entries.getFirst();

        String rawStatus =
                requiredText(
                        authorization,
                        "estado"
                );

        SriAuthorizationStatus status =
                switch (rawStatus) {

                    case "AUTORIZADO" ->
                            SriAuthorizationStatus.AUTHORIZED;

                    case "NO AUTORIZADO",
                         "RECHAZADO",
                         "RECHAZADA" ->
                            SriAuthorizationStatus.NOT_AUTHORIZED;

                    default ->
                            throw new SriAuthorizationException(
                                    "Unsupported SRI authorization status: "
                                            + rawStatus
                            );
                };
        String authorizationNumber =
                optionalText(
                        authorization,
                        "numeroAutorizacion"
                );

        String rawDate =
                optionalText(
                        authorization,
                        "fechaAutorizacion"
                );

        Instant authorizedAt =
                rawDate == null
                        ? null
                        : parseDate(rawDate);

        String authorizedXml =
                optionalText(
                        authorization,
                        "comprobante"
                );

        List<SriAuthorizationMessage> messages =
                parseMessages(
                        authorization
                );

        if (status == SriAuthorizationStatus.AUTHORIZED) {

            if (!requestedAccessKey.equals(
                    authorizationNumber)) {

                throw new SriAuthorizationException(
                        "SRI authorization number does not match access key"
                );
            }

            if (authorizedAt == null
                    || authorizedXml == null) {

                throw new SriAuthorizationException(
                        "Incomplete authorized SRI document"
                );
            }
        }

        return new SriAuthorizationResult(
                status,
                authorizationNumber,
                authorizedAt,
                authorizedXml,
                messages
        );
    }

    private static List<SriAuthorizationMessage> parseMessages(
            Element authorization
    ) {

        Element container =
                child(authorization, "mensajes");

        if (container == null) {
            return List.of();
        }

        List<SriAuthorizationMessage> result =
                new ArrayList<>();

        for (Element message : children(
                container,
                "mensaje"
        )) {

            result.add(
                    new SriAuthorizationMessage(
                            requiredText(
                                    message,
                                    "identificador"
                            ),
                            requiredText(
                                    message,
                                    "mensaje"
                            ),
                            optionalText(
                                    message,
                                    "informacionAdicional"
                            ),
                            Objects.requireNonNullElse(
                                    optionalText(message, "tipo"),
                                    "NO_INFORMADO"
                            )
                    )
            );
        }

        return List.copyOf(result);
    }

    private static Instant parseDate(
            String value
    ) {

        try {

            return OffsetDateTime.parse(value)
                    .toInstant();

        } catch (DateTimeParseException exception) {

            throw new SriAuthorizationException(
                    "Invalid SRI authorization date",
                    exception
            );
        }
    }

    private static Element parseXml(
            byte[] xml
    ) {

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
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

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );

            Document document =
                    factory.newDocumentBuilder()
                            .parse(
                                    new ByteArrayInputStream(xml)
                            );

            return document.getDocumentElement();

        } catch (
                ParserConfigurationException
                | SAXException
                | IOException exception
        ) {

            throw new SriAuthorizationException(
                    "Invalid SRI authorization SOAP response",
                    exception
            );
        }
    }

    private static Element requiredChild(
            Element parent,
            String name
    ) {

        Element result =
                child(parent, name);

        if (result == null) {

            throw new SriAuthorizationException(
                    "Missing SRI response element: " + name
            );
        }

        return result;
    }

    private static Element child(
            Element parent,
            String name
    ) {

        for (Element element : children(parent, name)) {
            return element;
        }

        return null;
    }

    private static List<Element> children(
            Element parent,
            String name
    ) {

        List<Element> matches =
                new ArrayList<>();

        for (
                Node node = parent.getFirstChild();
                node != null;
                node = node.getNextSibling()
        ) {

            if (node instanceof Element element
                    && matches(element, name)) {

                matches.add(element);
            }
        }

        return matches;
    }

    private static boolean matches(
            Element element,
            String name
    ) {

        String localName =
                element.getLocalName() == null
                        ? element.getNodeName()
                        : element.getLocalName();

        return name.equals(localName);
    }

    private static String requiredText(
            Element parent,
            String name
    ) {

        String value =
                optionalText(parent, name);

        if (value == null) {

            throw new SriAuthorizationException(
                    "Missing SRI response value: " + name
            );
        }

        return value;
    }

    private static String optionalText(
            Element parent,
            String name
    ) {

        Element element =
                child(parent, name);

        if (element == null) {
            return null;
        }

        String value =
                element.getTextContent();

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }
}