package io.github.imecuadorian.emitta.ride.adapter.out.xml;
import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.ride.application.model.InvoiceRideRequest;
import io.github.imecuadorian.emitta.ride.application.port.out.AuthorizedInvoiceRideReaderPort;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
/** Reads the stored authorized XML, never current mutable taxpayer or product data. */
public final class AuthorizedInvoiceRideReader implements AuthorizedInvoiceRideReaderPort {
    @Override public InvoiceRideRequest read(byte[] xml, String accessKey, Instant authorizedAt) {
        try {
            var f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
            if (!"factura".equals(root.getTagName()) || !"2.1.0".equals(root.getAttribute("version")))
                throw new IllegalArgumentException("Only invoice XML 2.1.0 is supported");
            var t=child(root,"infoTributaria"); var i=child(root,"infoFactura");
            if (!accessKey.equals(text(t,"claveAcceso"))) throw new IllegalArgumentException("Access key mismatch");
            List<InvoiceXmlData.Item> items=new ArrayList<>();
            for(var d: children(child(root,"detalles"),"detalle")) {
                List<InvoiceXmlData.ItemTax> taxes=new ArrayList<>();
                for(var tax: children(child(d,"impuestos"),"impuesto")) taxes.add(new InvoiceXmlData.ItemTax(
                        text(tax,"codigo"),text(tax,"codigoPorcentaje"),decimal(tax,"tarifa"),
                        decimal(tax,"baseImponible"),decimal(tax,"valor")));
                items.add(new InvoiceXmlData.Item(text(d,"codigoPrincipal"),text(d,"descripcion"),
                        decimal(d,"cantidad"),decimal(d,"precioUnitario"),decimal(d,"descuento"),
                        decimal(d,"precioTotalSinImpuesto"),taxes));
            }
            List<InvoiceXmlData.TaxTotal> totals=new ArrayList<>();
            for(var tax:children(child(i,"totalConImpuestos"),"totalImpuesto")) totals.add(new InvoiceXmlData.TaxTotal(
                    text(tax,"codigo"),text(tax,"codigoPorcentaje"),decimal(tax,"baseImponible"),decimal(tax,"valor")));
            List<InvoiceXmlData.Payment> payments=new ArrayList<>();
            var paymentRoot=optionalChild(i,"pagos");
            if(paymentRoot!=null) for(var pay:children(paymentRoot,"pago")) payments.add(new InvoiceXmlData.Payment(
                    text(pay,"formaPago"),decimal(pay,"total"),optionalDecimal(pay,"plazo"),optionalText(pay,"unidadTiempo")));
            String provider=null;
            var fields=new java.util.LinkedHashMap<String,String>();
            var additional=optionalChild(root,"infoAdicional");
            if(additional!=null) for(var field:children(additional,"campoAdicional")) {
                String name=field.getAttribute("nombre");
                if(fields.putIfAbsent(name,field.getTextContent())!=null) throw new IllegalArgumentException("Duplicate additional field");
                if("RUC Proveedor".equals(name)) provider=field.getTextContent();
            }
            for(String name:List.of("obligadoContabilidad","contribuyenteEspecial")) {
                String value=optionalText(i,name); if(value!=null) fields.put(name,value);
            }
            for(String name:List.of("contribuyenteRimpe","agenteRetencion","granContribuyente")) {
                String value=optionalText(t,name); if(value!=null) fields.put(name,value);
            }
            var invoice=new InvoiceXmlData(text(t,"ambiente"),text(t,"razonSocial"),optionalText(t,"nombreComercial"),
                    text(t,"ruc"),accessKey,text(t,"estab"),text(t,"ptoEmi"),text(t,"secuencial"),text(t,"dirMatriz"),
                    LocalDate.parse(text(i,"fechaEmision"),DateTimeFormatter.ofPattern("dd/MM/yyyy")),optionalText(i,"dirEstablecimiento"),
                    new InvoiceXmlData.Buyer(text(i,"tipoIdentificacionComprador"),text(i,"identificacionComprador"),
                            text(i,"razonSocialComprador"),optionalText(i,"direccionComprador")),
                    decimal(i,"totalSinImpuestos"),decimal(i,"totalDescuento"),totals,decimal(i,"propina"),
                    decimal(i,"importeTotal"),text(i,"moneda"),items,payments,provider);
            return new InvoiceRideRequest(invoice,accessKey,authorizedAt,fields);
        } catch (Exception failure) { throw new IllegalArgumentException("Invalid authorized invoice XML", failure); }
    }
    private static List<Element> children(Element parent,String name) {
        List<Element> result=new ArrayList<>();
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element e && name.equals(e.getTagName())) result.add(e);
        return result;
    }
    private static Element optionalChild(Element parent,String name) {
        var list=children(parent,name);
        if(list.size()>1) throw new IllegalArgumentException("Duplicate fiscal element");
        return list.isEmpty()?null:list.getFirst();
    }
    private static Element child(Element p,String name) {
        var e=optionalChild(p,name); if(e==null) throw new IllegalArgumentException("Missing fiscal element"); return e;
    }
    private static String text(Element p,String name) {
        var value=child(p,name).getTextContent(); if(value.isBlank()) throw new IllegalArgumentException("Empty fiscal element"); return value;
    }
    private static String optionalText(Element p,String name) { var e=optionalChild(p,name);return e==null?null:e.getTextContent(); }
    private static BigDecimal decimal(Element p,String name) { return new BigDecimal(text(p,name)); }
    private static BigDecimal optionalDecimal(Element p,String name) {var value=optionalText(p,name);return value==null?null:new BigDecimal(value);}
}
