package io.github.imecuadorian.emitta.srireception.adapter.out.soap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionRequest;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;
import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.URI;

import java.net.http.HttpClient;

import java.nio.charset.StandardCharsets;

import java.time.Duration;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SriSoapReceptionAdapterTest {

    private HttpServer server;

    private URI testEndpoint;
    private URI productionEndpoint;

    private final AtomicReference<String> requestBody =
            new AtomicReference<>();

    @BeforeEach
    void setUp()
            throws Exception {

        server =
                HttpServer.create(
                        new InetSocketAddress(
                                "127.0.0.1",
                                0
                        ),
                        0
                );

        server.start();

        int port =
                server.getAddress()
                        .getPort();

        testEndpoint =
                URI.create(
                        "http://127.0.0.1:"
                                + port
                                + "/test"
                );

        productionEndpoint =
                URI.create(
                        "http://127.0.0.1:"
                                + port
                                + "/production"
                );
    }

    @AfterEach
    void tearDown() {

        server.stop(
                0
        );
    }

    @Test
    void shouldMapReceivedResponse()
            throws Exception {

        server.createContext(
                "/test",
                exchange ->
                        respond(
                                exchange,
                                """
                                <?xml version="1.0" encoding="UTF-8"?>
                                <soap:Envelope
                                    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                                    <soap:Body>
                                        <ns2:validarComprobanteResponse
                                            xmlns:ns2="http://ec.gob.sri.ws.recepcion">
                                            <RespuestaRecepcionComprobante>
                                                <estado>RECIBIDA</estado>
                                                <comprobantes/>
                                            </RespuestaRecepcionComprobante>
                                        </ns2:validarComprobanteResponse>
                                    </soap:Body>
                                </soap:Envelope>
                                """
                        )
        );

        SriSoapReceptionAdapter adapter =
                adapter();

        byte[] xml =
                "<factura id=\"comprobante\"/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        SriReceptionResult result =
                adapter.submit(
                        new SriReceptionRequest(
                                FiscalEnvironment.TEST,
                                xml
                        )
                );

        assertEquals(
                SriReceptionStatus.RECEIVED,
                result.status()
        );

        assertTrue(
                result.messages()
                        .isEmpty()
        );

        String body =
                requestBody.get();

        assertTrue(
                body.contains(
                        "<ec:validarComprobante>"
                )
        );

        assertTrue(
                body.contains(
                        Base64.getEncoder()
                                .encodeToString(
                                        xml
                                )
                )
        );
    }

    @Test
    void shouldMapReturnedResponseAndMessages()
            throws Exception {

        server.createContext(
                "/test",
                exchange ->
                        respond(
                                exchange,
                                """
                                <?xml version="1.0" encoding="UTF-8"?>
                                <soap:Envelope
                                    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                                    <soap:Body>
                                        <ns2:validarComprobanteResponse
                                            xmlns:ns2="http://ec.gob.sri.ws.recepcion">
                                            <RespuestaRecepcionComprobante>
                                                <estado>DEVUELTA</estado>
                                                <comprobantes>
                                                    <comprobante>
                                                        <claveAcceso>
                                                            0710202601179001234500120010010000000011234567811
                                                        </claveAcceso>
                                                        <mensajes>
                                                            <mensaje>
                                                                <identificador>35</identificador>
                                                                <mensaje>DOCUMENTO INVÁLIDO</mensaje>
                                                                <informacionAdicional>
                                                                    Error de validación
                                                                </informacionAdicional>
                                                                <tipo>ERROR</tipo>
                                                            </mensaje>
                                                        </mensajes>
                                                    </comprobante>
                                                </comprobantes>
                                            </RespuestaRecepcionComprobante>
                                        </ns2:validarComprobanteResponse>
                                    </soap:Body>
                                </soap:Envelope>
                                """
                        )
        );

        SriReceptionResult result =
                adapter().submit(
                        request(
                                FiscalEnvironment.TEST
                        )
                );

        assertEquals(
                SriReceptionStatus.RETURNED,
                result.status()
        );

        assertEquals(
                1,
                result.messages()
                        .size()
        );

        assertEquals(
                "35",
                result.messages()
                        .get(0)
                        .identifier()
        );

        assertEquals(
                "DOCUMENTO INVÁLIDO",
                result.messages()
                        .get(0)
                        .message()
        );

        assertEquals(
                "Error de validación",
                result.messages()
                        .get(0)
                        .additionalInformation()
        );

        assertEquals(
                "ERROR",
                result.messages()
                        .get(0)
                        .type()
        );
    }

    @Test
    void shouldRejectSoapFault()
            throws Exception {

        server.createContext(
                "/test",
                exchange ->
                        respond(
                                exchange,
                                """
                                <?xml version="1.0" encoding="UTF-8"?>
                                <soap:Envelope
                                    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                                    <soap:Body>
                                        <soap:Fault>
                                            <faultcode>soap:Server</faultcode>
                                            <faultstring>SRI unavailable</faultstring>
                                        </soap:Fault>
                                    </soap:Body>
                                </soap:Envelope>
                                """
                        )
        );

        SriReceptionException exception =
                assertThrows(
                        SriReceptionException.class,
                        () ->
                                adapter().submit(
                                        request(
                                                FiscalEnvironment.TEST
                                        )
                                )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "SRI unavailable"
                        )
        );
    }

    @Test
    void shouldUseProductionEndpoint()
            throws Exception {

        server.createContext(
                "/production",
                exchange ->
                        respond(
                                exchange,
                                """
                                <?xml version="1.0" encoding="UTF-8"?>
                                <soap:Envelope
                                    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                                    <soap:Body>
                                        <ns2:validarComprobanteResponse
                                            xmlns:ns2="http://ec.gob.sri.ws.recepcion">
                                            <RespuestaRecepcionComprobante>
                                                <estado>RECIBIDA</estado>
                                                <comprobantes/>
                                            </RespuestaRecepcionComprobante>
                                        </ns2:validarComprobanteResponse>
                                    </soap:Body>
                                </soap:Envelope>
                                """
                        )
        );

        SriReceptionResult result =
                adapter().submit(
                        request(
                                FiscalEnvironment.PRODUCTION
                        )
                );

        assertEquals(
                SriReceptionStatus.RECEIVED,
                result.status()
        );
    }

    private SriSoapReceptionAdapter adapter() {

        return new SriSoapReceptionAdapter(
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        2
                                )
                        )
                        .build(),
                testEndpoint,
                productionEndpoint,
                Duration.ofSeconds(
                        5
                )
        );
    }

    private static SriReceptionRequest request(
            FiscalEnvironment environment
    ) {

        return new SriReceptionRequest(
                environment,
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );
    }

    private void respond(
            HttpExchange exchange,
            String response
    ) throws IOException {

        requestBody.set(
                new String(
                        exchange
                                .getRequestBody()
                                .readAllBytes(),
                        StandardCharsets.UTF_8
                )
        );

        byte[] content =
                response.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/xml; charset=UTF-8"
                );

        exchange.sendResponseHeaders(
                200,
                content.length
        );

        exchange.getResponseBody()
                .write(
                        content
                );

        exchange.close();
    }
}