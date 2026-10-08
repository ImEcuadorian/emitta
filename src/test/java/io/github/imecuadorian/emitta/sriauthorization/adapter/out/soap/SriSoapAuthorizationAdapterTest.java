package io.github.imecuadorian.emitta.sriauthorization.adapter.out.soap;

import com.sun.net.httpserver.HttpServer;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.exception.SriAuthorizationException;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SriSoapAuthorizationAdapterTest {

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    private HttpServer server;
    private SriSoapAuthorizationAdapter adapter;

    private volatile String responseXml;
    private volatile int responseStatus;

    private final AtomicReference<String> lastRequest =
            new AtomicReference<>();

    private final AtomicReference<String> lastPath =
            new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {

        responseStatus = 200;

        server =
                HttpServer.create(
                        new InetSocketAddress(
                                "127.0.0.1",
                                0
                        ),
                        0
                );

        server.createContext(
                "/test",
                exchange -> respond(exchange)
        );

        server.createContext(
                "/production",
                exchange -> respond(exchange)
        );

        server.start();

        int port =
                server.getAddress().getPort();

        adapter =
                new SriSoapAuthorizationAdapter(
                        HttpClient.newHttpClient(),
                        URI.create(
                                "http://127.0.0.1:"
                                        + port + "/test"
                        ),
                        URI.create(
                                "http://127.0.0.1:"
                                        + port + "/production"
                        ),
                        Duration.ofSeconds(3)
                );
    }

    @AfterEach
    void tearDown() {

        server.stop(0);
    }

    @Test
    void shouldParseAuthorizedDocument() {

        responseXml =
                soapResponse(
                        ACCESS_KEY,
                        """
                        <numeroComprobantes>1</numeroComprobantes>
                        <autorizaciones>
                          <autorizacion>
                            <estado>AUTORIZADO</estado>
                            <numeroAutorizacion>%s</numeroAutorizacion>
                            <fechaAutorizacion>2026-10-07T17:00:00-05:00</fechaAutorizacion>
                            <ambiente>PRUEBAS</ambiente>
                            <comprobante><![CDATA[<factura version="2.1.0"/>]]></comprobante>
                            <mensajes>
                              <mensaje>
                                <identificador>60</identificador>
                                <mensaje>Ambiente de pruebas</mensaje>
                                <tipo>ADVERTENCIA</tipo>
                              </mensaje>
                            </mensajes>
                          </autorizacion>
                        </autorizaciones>
                        """.formatted(ACCESS_KEY)
                );

        SriAuthorizationResult result =
                adapter.query(
                        request(FiscalEnvironment.TEST)
                );

        assertEquals(
                SriAuthorizationStatus.AUTHORIZED,
                result.status()
        );

        assertEquals(
                ACCESS_KEY,
                result.authorizationNumber()
        );

        assertEquals(
                Instant.parse("2026-10-07T22:00:00Z"),
                result.authorizedAt()
        );

        assertEquals(
                "<factura version=\"2.1.0\"/>",
                result.authorizedXml()
        );

        assertEquals(
                1,
                result.messages().size()
        );

        assertEquals(
                "60",
                result.messages().getFirst().identifier()
        );

        assertTrue(
                lastRequest.get().contains(
                        "<claveAccesoComprobante>"
                                + ACCESS_KEY
                                + "</claveAccesoComprobante>"
                )
        );

        assertEquals(
                "/test",
                lastPath.get()
        );
    }

    @Test
    void shouldReturnNotFoundWhenSriHasNoRecords() {

        responseXml =
                soapResponse(
                        ACCESS_KEY,
                        """
                        <numeroComprobantes>0</numeroComprobantes>
                        <autorizaciones/>
                        """
                );

        SriAuthorizationResult result =
                adapter.query(
                        request(FiscalEnvironment.TEST)
                );

        assertEquals(
                SriAuthorizationStatus.NOT_FOUND,
                result.status()
        );

        assertNull(
                result.authorizationNumber()
        );
    }

    @Test
    void shouldParseNotAuthorizedResponse() {

        responseXml =
                soapResponse(
                        ACCESS_KEY,
                        """
                        <numeroComprobantes>1</numeroComprobantes>
                        <autorizaciones>
                          <autorizacion>
                            <estado>NO AUTORIZADO</estado>
                            <fechaAutorizacion>2026-10-07T17:00:00-05:00</fechaAutorizacion>
                            <mensajes>
                              <mensaje>
                                <identificador>46</identificador>
                                <mensaje>RUC no existe</mensaje>
                                <tipo>ERROR</tipo>
                              </mensaje>
                            </mensajes>
                          </autorizacion>
                        </autorizaciones>
                        """
                );

        SriAuthorizationResult result =
                adapter.query(
                        request(FiscalEnvironment.TEST)
                );

        assertEquals(
                SriAuthorizationStatus.NOT_AUTHORIZED,
                result.status()
        );

        assertEquals(
                "46",
                result.messages().getFirst().identifier()
        );
    }

    @Test
    void shouldRejectSoapFault() {

        responseStatus = 500;

        responseXml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <soap:Fault>
                      <faultcode>soap:Server</faultcode>
                      <faultstring>SRI internal error</faultstring>
                    </soap:Fault>
                  </soap:Body>
                </soap:Envelope>
                """;

        SriAuthorizationException exception =
                assertThrows(
                        SriAuthorizationException.class,
                        () -> adapter.query(
                                request(FiscalEnvironment.TEST)
                        )
                );

        assertTrue(
                exception.getMessage().contains(
                        "SRI SOAP Fault"
                )
        );
    }

    @Test
    void shouldRejectDifferentAccessKey() {

        responseXml =
                soapResponse(
                        "0".repeat(49),
                        """
                        <numeroComprobantes>0</numeroComprobantes>
                        <autorizaciones/>
                        """
                );

        assertThrows(
                SriAuthorizationException.class,
                () -> adapter.query(
                        request(FiscalEnvironment.TEST)
                )
        );
    }

    @Test
    void shouldUseProductionEndpoint() {

        responseXml =
                soapResponse(
                        ACCESS_KEY,
                        """
                        <numeroComprobantes>0</numeroComprobantes>
                        <autorizaciones/>
                        """
                );

        adapter.query(
                request(FiscalEnvironment.PRODUCTION)
        );

        assertEquals(
                "/production",
                lastPath.get()
        );
    }

    @Test
    void shouldRejectXmlWithDoctype() {

        responseXml = """
                <!DOCTYPE soap [
                  <!ENTITY attack "unexpected">
                ]>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <soap:Fault>
                      <faultstring>&attack;</faultstring>
                    </soap:Fault>
                  </soap:Body>
                </soap:Envelope>
                """;

        assertThrows(
                SriAuthorizationException.class,
                () -> adapter.query(
                        request(FiscalEnvironment.TEST)
                )
        );
    }

    private SriAuthorizationRequest request(
            FiscalEnvironment environment
    ) {

        return new SriAuthorizationRequest(
                environment,
                ACCESS_KEY
        );
    }

    private String soapResponse(
            String accessKey,
            String authorizationContent
    ) {

        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <ns2:autorizacionComprobanteResponse
                        xmlns:ns2="http://ec.gob.sri.ws.autorizacion">
                      <RespuestaAutorizacionComprobante>
                        <claveAccesoConsultada>%s</claveAccesoConsultada>
                        %s
                      </RespuestaAutorizacionComprobante>
                    </ns2:autorizacionComprobanteResponse>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(
                accessKey,
                authorizationContent
        );
    }

    private void respond(
            com.sun.net.httpserver.HttpExchange exchange
    ) throws IOException {

        lastPath.set(
                exchange.getRequestURI().getPath()
        );

        lastRequest.set(
                new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                )
        );

        byte[] bytes =
                responseXml.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders().set(
                "Content-Type",
                "text/xml; charset=UTF-8"
        );

        exchange.sendResponseHeaders(
                responseStatus,
                bytes.length
        );

        try (var output = exchange.getResponseBody()) {

            output.write(bytes);
        }
    }
}