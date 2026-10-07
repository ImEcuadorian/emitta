package io.github.imecuadorian.emitta.invoicexml.adapter.out.xml;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlGeneratorPort;
import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;
import io.github.imecuadorian.emitta.invoicexml.domain.SriInvoiceSchema;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

public final class SriInvoiceXmlGenerator
        implements InvoiceXmlGeneratorPort {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy"
            );

    private final XMLOutputFactory outputFactory =
            XMLOutputFactory.newFactory();

    @Override
    public GeneratedInvoiceXml generate(
            InvoiceXmlData data
    ) {

        try {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            XMLStreamWriter xml =
                    outputFactory.createXMLStreamWriter(
                            output,
                            StandardCharsets.UTF_8.name()
                    );

            writeInvoice(
                    xml,
                    data
            );

            xml.close();

            return new GeneratedInvoiceXml(
                    output.toString(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (XMLStreamException exception) {

            throw new IllegalStateException(
                    "Unable to generate SRI invoice XML",
                    exception
            );
        }
    }

    private static void writeInvoice(
            XMLStreamWriter xml,
            InvoiceXmlData data
    ) throws XMLStreamException {

        xml.writeStartDocument(
                StandardCharsets.UTF_8.name(),
                "1.0"
        );

        xml.writeStartElement(
                "factura"
        );

        xml.writeAttribute(
                "id",
                SriInvoiceSchema.ROOT_ID
        );

        xml.writeAttribute(
                "version",
                SriInvoiceSchema.VERSION
        );

        writeTaxInformation(
                xml,
                data
        );

        writeInvoiceInformation(
                xml,
                data
        );

        writeItems(
                xml,
                data
        );

        writeAdditionalInformation(
                xml,
                data
        );

        xml.writeEndElement();
        xml.writeEndDocument();
    }

    private static void writeTaxInformation(
            XMLStreamWriter xml,
            InvoiceXmlData data
    ) throws XMLStreamException {

        xml.writeStartElement(
                "infoTributaria"
        );

        element(
                xml,
                "ambiente",
                data.environmentCode()
        );

        element(
                xml,
                "tipoEmision",
                SriInvoiceSchema.NORMAL_EMISSION_CODE
        );

        element(
                xml,
                "razonSocial",
                data.legalName()
        );

        optionalElement(
                xml,
                "nombreComercial",
                data.tradeName()
        );

        element(
                xml,
                "ruc",
                data.ruc()
        );

        element(
                xml,
                "claveAcceso",
                data.accessKey()
        );

        element(
                xml,
                "codDoc",
                SriInvoiceSchema.DOCUMENT_CODE
        );

        element(
                xml,
                "estab",
                data.establishmentCode()
        );

        element(
                xml,
                "ptoEmi",
                data.pointOfIssueCode()
        );

        element(
                xml,
                "secuencial",
                data.sequential()
        );

        element(
                xml,
                "dirMatriz",
                data.mainAddress()
        );

        xml.writeEndElement();
    }

    private static void writeInvoiceInformation(
            XMLStreamWriter xml,
            InvoiceXmlData data
    ) throws XMLStreamException {

        xml.writeStartElement(
                "infoFactura"
        );

        element(
                xml,
                "fechaEmision",
                DATE_FORMAT.format(
                        data.issueDate()
                )
        );

        optionalElement(
                xml,
                "dirEstablecimiento",
                data.establishmentAddress()
        );

        element(
                xml,
                "tipoIdentificacionComprador",
                data.buyer()
                        .identificationType()
        );

        element(
                xml,
                "razonSocialComprador",
                data.buyer()
                        .name()
        );

        element(
                xml,
                "identificacionComprador",
                data.buyer()
                        .identification()
        );

        optionalElement(
                xml,
                "direccionComprador",
                data.buyer()
                        .address()
        );

        element(
                xml,
                "totalSinImpuestos",
                money(
                        data.totalWithoutTaxes()
                )
        );

        element(
                xml,
                "totalDescuento",
                money(
                        data.discountTotal()
                )
        );

        xml.writeStartElement(
                "totalConImpuestos"
        );

        for (InvoiceXmlData.TaxTotal tax
                : data.taxTotals()) {

            xml.writeStartElement(
                    "totalImpuesto"
            );

            element(
                    xml,
                    "codigo",
                    tax.taxCode()
            );

            element(
                    xml,
                    "codigoPorcentaje",
                    tax.percentageCode()
            );

            element(
                    xml,
                    "baseImponible",
                    money(
                            tax.taxableBase()
                    )
            );

            element(
                    xml,
                    "valor",
                    money(
                            tax.amount()
                    )
            );

            xml.writeEndElement();
        }

        xml.writeEndElement();

        element(
                xml,
                "propina",
                money(
                        data.tip()
                )
        );

        element(
                xml,
                "importeTotal",
                money(
                        data.total()
                )
        );

        optionalElement(
                xml,
                "moneda",
                data.currency()
        );

        writePayments(
                xml,
                data
        );

        xml.writeEndElement();
    }

    private static void writePayments(
            XMLStreamWriter xml,
            InvoiceXmlData data
    ) throws XMLStreamException {

        xml.writeStartElement(
                "pagos"
        );

        for (InvoiceXmlData.Payment payment
                : data.payments()) {

            xml.writeStartElement(
                    "pago"
            );

            element(
                    xml,
                    "formaPago",
                    payment.method()
            );

            element(
                    xml,
                    "total",
                    money(
                            payment.total()
                    )
            );

            if (payment.term() != null) {

                element(
                        xml,
                        "plazo",
                        decimal(
                                payment.term()
                        )
                );

                element(
                        xml,
                        "unidadTiempo",
                        payment.unitTime()
                );
            }

            xml.writeEndElement();
        }

        xml.writeEndElement();
    }

    private static void writeItems(
            XMLStreamWriter xml,
            InvoiceXmlData data
    ) throws XMLStreamException {

        xml.writeStartElement(
                "detalles"
        );

        for (InvoiceXmlData.Item item
                : data.items()) {

            xml.writeStartElement(
                    "detalle"
            );

            element(
                    xml,
                    "codigoPrincipal",
                    item.code()
            );

            element(
                    xml,
                    "descripcion",
                    item.description()
            );

            element(
                    xml,
                    "cantidad",
                    decimal(
                            item.quantity()
                    )
            );

            element(
                    xml,
                    "precioUnitario",
                    decimal(
                            item.unitPrice()
                    )
            );

            element(
                    xml,
                    "descuento",
                    money(
                            item.discount()
                    )
            );

            element(
                    xml,
                    "precioTotalSinImpuesto",
                    money(
                            item.subtotal()
                    )
            );

            xml.writeStartElement(
                    "impuestos"
            );

            for (InvoiceXmlData.ItemTax tax
                    : item.taxes()) {

                xml.writeStartElement(
                        "impuesto"
                );

                element(
                        xml,
                        "codigo",
                        tax.taxCode()
                );

                element(
                        xml,
                        "codigoPorcentaje",
                        tax.percentageCode()
                );

                element(
                        xml,
                        "tarifa",
                        decimal(
                                tax.rate()
                        )
                );

                element(
                        xml,
                        "baseImponible",
                        money(
                                tax.taxableBase()
                        )
                );

                element(
                        xml,
                        "valor",
                        money(
                                tax.amount()
                        )
                );

                xml.writeEndElement();
            }

            xml.writeEndElement();

            xml.writeEndElement();
        }

        xml.writeEndElement();
    }

    private static void writeAdditionalInformation(
            XMLStreamWriter xml,
            InvoiceXmlData data
    ) throws XMLStreamException {

        xml.writeStartElement(
                "infoAdicional"
        );

        xml.writeStartElement(
                "campoAdicional"
        );

        xml.writeAttribute(
                "nombre",
                "RUC Proveedor"
        );

        xml.writeCharacters(
                data.providerRuc()
        );

        xml.writeEndElement();

        xml.writeEndElement();
    }

    private static void element(
            XMLStreamWriter xml,
            String name,
            String value
    ) throws XMLStreamException {

        xml.writeStartElement(
                name
        );

        xml.writeCharacters(
                value
        );

        xml.writeEndElement();
    }

    private static void optionalElement(
            XMLStreamWriter xml,
            String name,
            String value
    ) throws XMLStreamException {

        if (value == null
                || value.isBlank()) {

            return;
        }

        element(
                xml,
                name,
                value
        );
    }

    private static String money(
            BigDecimal value
    ) {

        return value
                .setScale(
                        2,
                        RoundingMode.UNNECESSARY
                )
                .toPlainString();
    }

    private static String decimal(
            BigDecimal value
    ) {

        return value
                .stripTrailingZeros()
                .toPlainString();
    }
}