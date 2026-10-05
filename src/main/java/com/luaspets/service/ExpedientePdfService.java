package com.luaspets.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import com.luaspets.dto.ConsultaExpediente;
import com.luaspets.dto.ExpedienteMascota;

/**
 * Genera el expediente clinico completo de una mascota como PDF, con
 * exactamente los mismos datos que muestran cliente/mascotas/perfil.html y
 * doctor/pacientes/ficha.html (ambos consumen los mismos DTOs de
 * ExpedienteService, asi que el PDF nunca puede desincronizarse de la vista).
 *
 * Generado 100% programaticamente con OpenPDF (sin motor de renderizado HTML)
 * para mantener bajo el consumo de memoria al generar expedientes.
 */
@Service
public class ExpedientePdfService {

    private static final Color COLOR_MARCA = new Color(15, 118, 110);
    private static final Color COLOR_GRIS = new Color(107, 114, 128);
    private static final Color COLOR_GRIS_CLARO = new Color(229, 231, 235);
    private static final Color COLOR_FONDO_TABLA = new Color(249, 250, 251);
    private static final Color COLOR_FONDO_ALERGIA = new Color(254, 226, 226);
    private static final Color COLOR_BORDE_ALERGIA = new Color(220, 38, 38);
    private static final Color COLOR_TEXTO_ALERGIA = new Color(153, 27, 27);
    private static final Color COLOR_NEGRO = new Color(17, 24, 39);

    private static final DateTimeFormatter FORMATO_EMISION = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private BaseFont baseFontNormal;
    private BaseFont baseFontBold;

    private BaseFont baseFontNormal() {
        if (baseFontNormal == null) {
            baseFontNormal = crearBaseFont(BaseFont.HELVETICA);
        }
        return baseFontNormal;
    }

    private BaseFont baseFontBold() {
        if (baseFontBold == null) {
            baseFontBold = crearBaseFont(BaseFont.HELVETICA_BOLD);
        }
        return baseFontBold;
    }

    private BaseFont crearBaseFont(String nombre) {
        try {
            // WINANSI (Windows-1252) cubre tildes y la letra ñ correctamente con
            // las fuentes base de Helvetica, sin necesidad de incrustar una
            // fuente completa (mas peso, mas memoria durante la generacion).
            return BaseFont.createFont(nombre, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (DocumentException | java.io.IOException e) {
            throw new IllegalStateException("No se pudo crear la fuente base para el PDF", e);
        }
    }

    private Font fuente(float tamano, int estilo, Color color) {
        BaseFont base = (estilo & Font.BOLD) != 0 ? baseFontBold() : baseFontNormal();
        return new Font(base, tamano, estilo, color);
    }

    public byte[] generar(ExpedienteMascota expediente, List<ConsultaExpediente> consultas,
            boolean incluirDatosPropietario) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, salida);
            writer.setPageEvent(new PiePaginaEvent());

            document.open();
            agregarEncabezado(document);
            agregarSeccionMascota(document, expediente);
            if (incluirDatosPropietario) {
                agregarSeccionPropietario(document, expediente);
            }
            agregarSeccionAlergias(document, expediente);
            agregarHistorialConsultas(document, consultas);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del expediente", e);
        } finally {
            document.close();
        }

        return salida.toByteArray();
    }

    private void agregarEncabezado(Document document) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] { 1f, 1f });

        PdfPCell celdaIzquierda = new PdfPCell();
        celdaIzquierda.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celdaIzquierda.addElement(new Paragraph("Luas Pets", fuente(20, Font.BOLD, COLOR_MARCA)));
        Paragraph subtitulo = new Paragraph("Clínica Veterinaria", fuente(10, Font.NORMAL, COLOR_GRIS));
        subtitulo.setSpacingBefore(2f);
        celdaIzquierda.addElement(subtitulo);
        tabla.addCell(celdaIzquierda);

        PdfPCell celdaDerecha = new PdfPCell();
        celdaDerecha.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celdaDerecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph titulo = new Paragraph("Expediente clínico", fuente(12, Font.BOLD, COLOR_NEGRO));
        titulo.setAlignment(Element.ALIGN_RIGHT);
        celdaDerecha.addElement(titulo);
        Paragraph fechaEmision = new Paragraph(
                "Emitido el " + LocalDateTime.now().format(FORMATO_EMISION),
                fuente(9, Font.NORMAL, COLOR_GRIS));
        fechaEmision.setAlignment(Element.ALIGN_RIGHT);
        fechaEmision.setSpacingBefore(2f);
        celdaDerecha.addElement(fechaEmision);
        tabla.addCell(celdaDerecha);

        document.add(tabla);

        LineSeparator linea = new LineSeparator(1f, 100f, COLOR_MARCA, Element.ALIGN_CENTER, -4);
        Paragraph lineaParrafo = new Paragraph();
        lineaParrafo.add(new com.lowagie.text.Chunk(linea));
        lineaParrafo.setSpacingAfter(14f);
        document.add(lineaParrafo);
    }

    private void agregarTituloSeccion(Document document, String texto) throws DocumentException {
        Paragraph titulo = new Paragraph(texto, fuente(12, Font.BOLD, COLOR_MARCA));
        titulo.setSpacingBefore(6f);
        titulo.setSpacingAfter(8f);
        document.add(titulo);
    }

    private PdfPTable tablaEtiquetaValor() {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        try {
            tabla.setWidths(new float[] { 1f, 2f });
        } catch (DocumentException e) {
            // Ancho fijo de dos columnas: nunca falla con dos valores validos.
        }
        return tabla;
    }

    private void agregarFila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, fuente(10, Font.NORMAL, COLOR_GRIS)));
        celdaEtiqueta.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celdaEtiqueta.setPaddingBottom(5f);
        tabla.addCell(celdaEtiqueta);

        PdfPCell celdaValor = new PdfPCell(
                new Phrase(valor == null || valor.isBlank() ? "—" : valor, fuente(10, Font.NORMAL, COLOR_NEGRO)));
        celdaValor.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celdaValor.setPaddingBottom(5f);
        tabla.addCell(celdaValor);
    }

    private void agregarSeccionMascota(Document document, ExpedienteMascota expediente) throws DocumentException {
        agregarTituloSeccion(document, "Datos del paciente");

        PdfPTable tabla = tablaEtiquetaValor();
        agregarFila(tabla, "Nombre", expediente.getNombre());
        agregarFila(tabla, "Código", expediente.getCodigo());
        agregarFila(tabla, "Especie", expediente.getEspecie());
        agregarFila(tabla, "Raza", expediente.getRaza());
        agregarFila(tabla, "Sexo", expediente.getSexoTexto());
        agregarFila(tabla, "Edad", expediente.getEdadTexto());
        agregarFila(tabla, "Fecha de nacimiento", expediente.getFechaNacimientoTexto());
        agregarFila(tabla, "Peso actual", expediente.getPesoTexto());
        agregarFila(tabla, "Estado", expediente.getEstadoTexto());
        tabla.setSpacingAfter(12f);
        document.add(tabla);
    }

    private void agregarSeccionPropietario(Document document, ExpedienteMascota expediente)
            throws DocumentException {
        agregarTituloSeccion(document, "Datos del propietario");

        PdfPTable tabla = tablaEtiquetaValor();
        agregarFila(tabla, "Nombre", expediente.getPropietarioNombre());
        agregarFila(tabla, "Correo", expediente.getPropietarioEmail());
        agregarFila(tabla, "Teléfono", expediente.getPropietarioTelefono());
        tabla.setSpacingAfter(12f);
        document.add(tabla);
    }

    private void agregarSeccionAlergias(Document document, ExpedienteMascota expediente) throws DocumentException {
        agregarTituloSeccion(document, "Alergias");

        if (expediente.isTieneAlergias()) {
            PdfPTable contenedor = new PdfPTable(1);
            contenedor.setWidthPercentage(100);
            PdfPCell celda = new PdfPCell();
            celda.setBackgroundColor(COLOR_FONDO_ALERGIA);
            celda.setBorderColor(COLOR_BORDE_ALERGIA);
            celda.setBorderWidth(1f);
            celda.setPadding(10f);

            Paragraph parrafo = new Paragraph();
            parrafo.add(new com.lowagie.text.Chunk("ALERGIAS REGISTRADAS: ", fuente(10, Font.BOLD, COLOR_TEXTO_ALERGIA)));
            parrafo.add(new com.lowagie.text.Chunk(expediente.getAlergiasTexto(), fuente(10, Font.NORMAL, COLOR_NEGRO)));
            celda.addElement(parrafo);
            contenedor.addCell(celda);
            contenedor.setSpacingAfter(12f);
            document.add(contenedor);
        } else {
            Paragraph sinAlergias = new Paragraph("Sin alergias registradas.", fuente(10, Font.NORMAL, COLOR_GRIS));
            sinAlergias.setSpacingAfter(12f);
            document.add(sinAlergias);
        }
    }

    private void agregarHistorialConsultas(Document document, List<ConsultaExpediente> consultas)
            throws DocumentException {
        agregarTituloSeccion(document, "Historial clínico (" + consultas.size() + ")");

        if (consultas.isEmpty()) {
            document.add(new Paragraph("Este paciente aún no tiene consultas registradas.",
                    fuente(10, Font.NORMAL, COLOR_GRIS)));
            return;
        }

        for (ConsultaExpediente consulta : consultas) {
            agregarConsulta(document, consulta);
        }
    }

    private void agregarConsulta(Document document, ConsultaExpediente consulta) throws DocumentException {
        PdfPTable barra = new PdfPTable(2);
        barra.setWidthPercentage(100);
        barra.setWidths(new float[] { 2f, 1f });
        barra.setSpacingBefore(4f);

        PdfPCell celdaTitulo = new PdfPCell(new Phrase(consulta.getTitulo(), fuente(11, Font.BOLD, COLOR_NEGRO)));
        celdaTitulo.setBackgroundColor(COLOR_FONDO_TABLA);
        celdaTitulo.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celdaTitulo.setPadding(6f);
        barra.addCell(celdaTitulo);

        PdfPCell celdaFecha = new PdfPCell(new Phrase(consulta.getFechaTexto(), fuente(10, Font.NORMAL, COLOR_GRIS)));
        celdaFecha.setBackgroundColor(COLOR_FONDO_TABLA);
        celdaFecha.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        celdaFecha.setPadding(6f);
        celdaFecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
        barra.addCell(celdaFecha);

        document.add(barra);

        String infoDoctor = consulta.getDoctorNombre();
        if (consulta.getPesoTexto() != null && !consulta.getPesoTexto().isBlank()) {
            infoDoctor = infoDoctor + " · Peso registrado: " + consulta.getPesoTexto();
        }
        Paragraph doctorParrafo = new Paragraph(infoDoctor, fuente(9, Font.NORMAL, COLOR_GRIS));
        doctorParrafo.setSpacingBefore(4f);
        doctorParrafo.setSpacingAfter(6f);
        document.add(doctorParrafo);

        agregarApartado(document, "DIAGNÓSTICO", consulta.getDiagnostico());
        agregarApartado(document, "TRATAMIENTO", consulta.getTratamiento());
        agregarApartadoLista(document, "MEDICAMENTOS RECETADOS", consulta.getMedicamentosLineas());
        agregarApartado(document, "OBSERVACIONES", consulta.getObservaciones());

        Paragraph espaciador = new Paragraph(" ", fuente(6, Font.NORMAL, COLOR_NEGRO));
        espaciador.setSpacingAfter(4f);
        document.add(espaciador);
    }

    private void agregarApartado(Document document, String etiqueta, String contenido) throws DocumentException {
        if (contenido == null || contenido.isBlank()) {
            return;
        }
        Paragraph etiquetaParrafo = new Paragraph(etiqueta, fuente(9, Font.BOLD, COLOR_GRIS));
        etiquetaParrafo.setSpacingBefore(4f);
        document.add(etiquetaParrafo);

        Paragraph contenidoParrafo = new Paragraph(contenido, fuente(10, Font.NORMAL, COLOR_NEGRO));
        contenidoParrafo.setSpacingAfter(2f);
        document.add(contenidoParrafo);
    }

    private void agregarApartadoLista(Document document, String etiqueta, List<String> lineas)
            throws DocumentException {
        if (lineas == null || lineas.isEmpty()) {
            return;
        }
        Paragraph etiquetaParrafo = new Paragraph(etiqueta, fuente(9, Font.BOLD, COLOR_GRIS));
        etiquetaParrafo.setSpacingBefore(4f);
        document.add(etiquetaParrafo);

        for (String linea : lineas) {
            if (linea == null || linea.isBlank()) {
                continue;
            }
            Paragraph itemParrafo = new Paragraph("•  " + linea, fuente(10, Font.NORMAL, COLOR_NEGRO));
            itemParrafo.setIndentationLeft(10f);
            document.add(itemParrafo);
        }
    }

    /**
     * Pie de pagina en todas las paginas: "Luas Pets · Documento generado
     * automaticamente" a la izquierda y "Pagina X de Y" a la derecha. El total
     * de paginas no se conoce hasta cerrar el documento, asi que se reserva un
     * PdfTemplate vacio en cada pagina (onEndPage) y se rellena una sola vez al
     * final (onCloseDocument); es la tecnica estandar de iText/OpenPDF para
     * este caso.
     */
    private final class PiePaginaEvent extends PdfPageEventHelper {

        private PdfTemplate totalPaginas;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPaginas = writer.getDirectContent().createTemplate(30, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();

            float y = document.bottom() - 20;

            cb.setLineWidth(0.5f);
            cb.setColorStroke(COLOR_GRIS_CLARO);
            cb.moveTo(document.left(), document.bottom() - 8);
            cb.lineTo(document.right(), document.bottom() - 8);
            cb.stroke();

            BaseFont fuentePie = baseFontNormal();
            cb.setColorFill(COLOR_GRIS);
            cb.beginText();
            cb.setFontAndSize(fuentePie, 8);
            cb.showTextAligned(Element.ALIGN_LEFT, "Luas Pets · Documento generado automáticamente",
                    document.left(), y, 0);

            String textoPagina = "Página " + writer.getPageNumber() + " de ";
            float anchoTexto = fuentePie.getWidthPoint(textoPagina, 8);
            cb.setTextMatrix(document.right() - anchoTexto - 30, y);
            cb.showText(textoPagina);
            cb.endText();

            cb.addTemplate(totalPaginas, document.right() - 30, y);
            cb.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPaginas.beginText();
            totalPaginas.setFontAndSize(baseFontNormal(), 8);
            totalPaginas.setColorFill(COLOR_GRIS);
            totalPaginas.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPaginas.endText();
        }
    }
}
