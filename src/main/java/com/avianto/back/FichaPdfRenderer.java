package com.avianto.back;

import static com.avianto.back.ApiDtos.FichaResponse;
import static com.avianto.back.ApiDtos.FichaTrabajoResponse;
import static com.avianto.back.ApiDtos.RepuestoItemResponse;
import static com.avianto.back.ApiDtos.RepuestoResponse;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.ExceptionConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.awt.Color;

/** Builds the branded PDF representation of a workshop ficha. */
final class FichaPdfRenderer {
  private static final Color AVIANTO_BLUE = new Color(48, 52, 139);
  private static final Color AVIANTO_RED = new Color(229, 37, 40);
  private static final Color INK = new Color(30, 34, 55);
  private static final Color MUTED = new Color(91, 99, 130);
  private static final Color SURFACE = new Color(244, 245, 250);
  private static final Color GRID = new Color(215, 219, 235);
  private static final Color WHITE = Color.WHITE;
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final Font TITLE = new Font(Font.HELVETICA, 20, Font.BOLD, AVIANTO_BLUE);
  private static final Font SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, AVIANTO_BLUE);
  private static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, INK);
  private static final Font MUTED_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
  private static final Font FIELD_LABEL = new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED);
  private static final Font FIELD_VALUE = new Font(Font.HELVETICA, 9, Font.BOLD, AVIANTO_BLUE);
  private static final Font TABLE_HEADER = new Font(Font.HELVETICA, 8, Font.BOLD, WHITE);
  private static final Font FOOTER_BRAND = new Font(Font.HELVETICA, 12, Font.BOLD, AVIANTO_BLUE);
  private static final Font PAGE_NUMBER = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);

  private FichaPdfRenderer() {}

  static byte[] render(FichaResponse ficha, List<RepuestoResponse> repuestos) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4, 42, 42, 34, 46);
    PdfWriter writer = PdfWriter.getInstance(document, output);
    writer.setPageEvent(new BrandPageEvent(loadAsset("/brand/avianto-isotipo.png")));
    document.open();

    addBrandHeader(document);
    addFichaHeading(document, ficha);
    addFichaDetails(document, ficha);

    if (hasText(ficha.observaciones())) {
      addSectionHeading(document, "Observaciones");
      Paragraph observations = new Paragraph(ficha.observaciones().trim(), BODY);
      observations.setLeading(13);
      observations.setSpacingAfter(3);
      document.add(observations);
    }

    addSectionHeading(document, "Trabajos y servicios");
    PdfPTable trabajos = createTable(new float[] {3.2f, 1.1f, 1.1f, 1f, 1.2f},
        "Descripción", "Estado", "Precio", "Desc.", "Subtotal");
    int workRow = 0;
    for (FichaTrabajoResponse trabajo : ficha.trabajos()) {
      Color rowSurface = workRow++ % 2 == 0 ? WHITE : SURFACE;
      addBodyCell(trabajos, display(trabajo.descripcion()), rowSurface);
      addBodyCell(trabajos, display(trabajo.estadoTrabajo()), rowSurface);
      addBodyCell(trabajos, currency(trabajo.precioUnitario()), rowSurface);
      addBodyCell(trabajos, currency(trabajo.descuento()), rowSurface);
      addBodyCell(trabajos, currency(trabajo.subtotal()), rowSurface);
      if (hasText(trabajo.observacionTrabajo())) {
        addSpanCell(trabajos, "Observación: " + trabajo.observacionTrabajo().trim(), MUTED_FONT, 5);
      }
    }
    document.add(trabajos);

    BigDecimal subtotalTrabajos = ficha.trabajos().stream()
        .filter(trabajo -> !"Cancelado".equals(trabajo.estadoTrabajo()))
        .map(FichaTrabajoResponse::subtotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal despuesDescuento = subtotalTrabajos.subtract(ficha.descuentoGlobal()).max(BigDecimal.ZERO);
    BigDecimal iva = ficha.iva() ? ficha.total().subtract(despuesDescuento) : BigDecimal.ZERO;
    PdfPTable totals = createTotalsTable();
    addTotalRow(totals, "Subtotal trabajos", currency(subtotalTrabajos), false);
    if (ficha.descuentoGlobal().signum() > 0) {
      addTotalRow(totals, "Descuento global", currency(ficha.descuentoGlobal()), false);
    }
    if (ficha.iva()) addTotalRow(totals, "IVA 21%", currency(iva), false);
    addTotalRow(totals, "Total ficha", currency(ficha.total()), true);
    document.add(totals);

    addSectionHeading(document, "Pedidos de repuestos y accesorios vinculados");
    BigDecimal totalRepuestos = BigDecimal.ZERO;
    if (repuestos.isEmpty()) {
      document.add(new Paragraph("No hay pedidos vinculados a esta ficha.", MUTED_FONT));
    }
    for (RepuestoResponse pedido : repuestos) {
      Paragraph orderHeading = new Paragraph(
          display(pedido.numero()) + "  ·  " + display(pedido.estado()) + "  ·  Proveedor: " + display(pedido.proveedor()),
          new Font(Font.HELVETICA, 9, Font.BOLD, AVIANTO_BLUE));
      orderHeading.setSpacingBefore(7);
      orderHeading.setSpacingAfter(4);
      document.add(orderHeading);
      if (hasText(pedido.observaciones())) {
        Paragraph note = new Paragraph("Observación del pedido: " + pedido.observaciones().trim(), MUTED_FONT);
        note.setSpacingAfter(4);
        document.add(note);
      }

      PdfPTable items = createTable(new float[] {2.7f, 1f, .7f, 1.2f, 1f, 1.1f},
          "Ítem", "Tipo", "Cant.", "Estado", "Precio", "Subtotal");
      int itemRow = 0;
      for (RepuestoItemResponse item : pedido.items()) {
        Color rowSurface = itemRow++ % 2 == 0 ? WHITE : SURFACE;
        addBodyCell(items, display(item.descripcion()), rowSurface);
        addBodyCell(items, item.tipo().label(), rowSurface);
        addBodyCell(items, quantity(item.cantidad()), rowSurface);
        addBodyCell(items, display(item.estado()), rowSurface);
        addBodyCell(items, currency(item.precio()), rowSurface);
        addBodyCell(items, currency(item.subtotal()), rowSurface);
        if (hasText(item.observaciones())) {
          addSpanCell(items, "Observación: " + item.observaciones().trim(), MUTED_FONT, 6);
        }
      }
      document.add(items);
      if (!"Cancelado".equals(pedido.estado())) totalRepuestos = totalRepuestos.add(pedido.total());
    }

    PdfPTable finalTotal = createTotalsTable();
    addTotalRow(finalTotal, "Total repuestos vinculados", currency(totalRepuestos), false);
    addTotalRow(finalTotal, "TOTAL PRESUPUESTO", currency(ficha.total().add(totalRepuestos)), true);
    finalTotal.setSpacingBefore(8);
    document.add(finalTotal);

    document.close();
    return output.toByteArray();
  }

  private static void addBrandHeader(Document document) throws Exception {
    Image lockup = Image.getInstance(loadAsset("/brand/avianto-lockup.png"));
    lockup.scaleToFit(238, 50);
    lockup.setAlignment(Image.ALIGN_CENTER);
    lockup.setSpacingAfter(3);
    document.add(lockup);

    Paragraph descriptor = new Paragraph("Mecánica integral de motos", MUTED_FONT);
    descriptor.setAlignment(Element.ALIGN_CENTER);
    descriptor.setSpacingAfter(10);
    document.add(descriptor);

    PdfPTable brandRule = new PdfPTable(new float[] {8, 1});
    brandRule.setWidthPercentage(100);
    brandRule.setSpacingAfter(13);
    addRuleCell(brandRule, AVIANTO_BLUE);
    addRuleCell(brandRule, AVIANTO_RED);
    document.add(brandRule);
  }

  private static void addFichaHeading(Document document, FichaResponse ficha) throws DocumentException {
    Paragraph title = new Paragraph("Ficha " + display(ficha.numero()), TITLE);
    title.setSpacingAfter(3);
    document.add(title);

    Paragraph subtitle = new Paragraph("Resumen de trabajo y presupuesto", BODY);
    subtitle.setSpacingAfter(7);
    document.add(subtitle);

    PdfPTable status = new PdfPTable(new float[] {1, 1});
    status.setWidthPercentage(100);
    status.setSpacingAfter(7);
    addStatusCell(status, "ESTADO", display(ficha.estado()), AVIANTO_BLUE);
    addStatusCell(status, "PAGO", display(ficha.estadoPago()), AVIANTO_RED);
    document.add(status);
  }

  private static void addFichaDetails(Document document, FichaResponse ficha) throws DocumentException {
    PdfPTable details = new PdfPTable(3);
    details.setWidthPercentage(100);
    details.setSpacingBefore(3);
    details.setSpacingAfter(4);
    details.setKeepTogether(true);
    addFieldCell(details, "Cliente", ficha.cliente());
    addFieldCell(details, "Moto", ficha.moto());
    addFieldCell(details, "Dominio", ficha.patente());
    addFieldCell(details, "Ingreso", date(ficha.fechaIngreso()));
    addFieldCell(details, "Entrega estimada", date(ficha.fechaEntregaEstimada()));
    addFieldCell(details, "Kilometraje de ingreso", ficha.kilometrajeIngreso() == null ? "—" : ficha.kilometrajeIngreso() + " km");
    document.add(details);
  }

  private static void addSectionHeading(Document document, String title) throws DocumentException {
    Paragraph heading = new Paragraph(title, SECTION);
    heading.setSpacingBefore(9);
    heading.setSpacingAfter(5);
    document.add(heading);
  }

  private static PdfPTable createTable(float[] widths, String... headers) throws DocumentException {
    PdfPTable table = new PdfPTable(widths);
    table.setWidthPercentage(100);
    table.setHeaderRows(1);
    table.setSplitRows(true);
    table.setSpacingAfter(5);
    for (String header : headers) {
      PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER));
      cell.setBackgroundColor(AVIANTO_BLUE);
      cell.setHorizontalAlignment(Element.ALIGN_LEFT);
      cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
      cell.setPadding(6);
      cell.setBorderColor(AVIANTO_BLUE);
      table.addCell(cell);
    }
    return table;
  }

  private static PdfPTable createTotalsTable() throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {3, 1});
    table.setWidthPercentage(100);
    table.setHorizontalAlignment(Element.ALIGN_LEFT);
    table.setSpacingBefore(3);
    table.setSpacingAfter(4);
    return table;
  }

  private static void addTotalRow(PdfPTable table, String label, String value, boolean emphasized) {
    Color background = emphasized ? AVIANTO_BLUE : SURFACE;
    Font font = emphasized ? new Font(Font.HELVETICA, 9, Font.BOLD, WHITE) : BODY;
    PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
    PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
    for (PdfPCell cell : List.of(labelCell, valueCell)) {
      cell.setBackgroundColor(background);
      cell.setBorderColor(emphasized ? AVIANTO_BLUE : GRID);
      cell.setPadding(7);
    }
    valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.addCell(labelCell);
    table.addCell(valueCell);
  }

  private static void addBodyCell(PdfPTable table, String value, Color background) {
    PdfPCell cell = new PdfPCell(new Phrase(value, BODY));
    cell.setBackgroundColor(background);
    cell.setBorderColor(GRID);
    cell.setPadding(5);
    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    table.addCell(cell);
  }

  private static void addSpanCell(PdfPTable table, String value, Font font, int columns) {
    PdfPCell cell = new PdfPCell(new Phrase(value, font));
    cell.setColspan(columns);
    cell.setBackgroundColor(SURFACE);
    cell.setBorderColor(GRID);
    cell.setPadding(5);
    table.addCell(cell);
  }

  private static void addFieldCell(PdfPTable table, String label, String value) {
    Phrase phrase = new Phrase();
    phrase.add(new Chunk(label.toUpperCase(Locale.ROOT) + "\n", FIELD_LABEL));
    phrase.add(new Chunk(display(value), FIELD_VALUE));
    PdfPCell cell = new PdfPCell(phrase);
    cell.setBackgroundColor(SURFACE);
    cell.setBorderColor(GRID);
    cell.setPadding(8);
    cell.setMinimumHeight(38);
    table.addCell(cell);
  }

  private static void addStatusCell(PdfPTable table, String label, String value, Color accent) {
    Phrase phrase = new Phrase();
    phrase.add(new Chunk(label + "\n", FIELD_LABEL));
    phrase.add(new Chunk(value, new Font(Font.HELVETICA, 9, Font.BOLD, accent)));
    PdfPCell cell = new PdfPCell(phrase);
    cell.setBackgroundColor(SURFACE);
    cell.setBorderColor(GRID);
    cell.setPadding(7);
    table.addCell(cell);
  }

  private static void addRuleCell(PdfPTable table, Color color) {
    PdfPCell cell = new PdfPCell(new Phrase(" "));
    cell.setBackgroundColor(color);
    cell.setBorder(Rectangle.NO_BORDER);
    cell.setFixedHeight(3);
    table.addCell(cell);
  }

  private static String currency(BigDecimal amount) {
    BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"));
    DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
    format.setRoundingMode(RoundingMode.HALF_UP);
    return "$ " + format.format(value);
  }

  private static String quantity(BigDecimal amount) {
    return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
  }

  private static String date(LocalDate value) {
    return value == null ? "—" : value.format(DATE_FORMAT);
  }

  private static String display(String value) {
    return value == null || value.isBlank() ? "—" : value.trim();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static byte[] loadAsset(String path) throws IOException {
    try (InputStream stream = FichaPdfRenderer.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing PDF brand asset: " + path);
      return stream.readAllBytes();
    }
  }

  private static final class BrandPageEvent extends PdfPageEventHelper {
    private final byte[] watermarkBytes;

    private BrandPageEvent(byte[] watermarkBytes) {
      this.watermarkBytes = watermarkBytes;
    }

    @Override
    public void onStartPage(PdfWriter writer, Document document) {
      try {
        Image watermark = Image.getInstance(watermarkBytes);
        watermark.scaleToFit(260, 140);
        watermark.setAbsolutePosition(
            (writer.getPageSize().getWidth() - watermark.getScaledWidth()) / 2,
            (writer.getPageSize().getHeight() - watermark.getScaledHeight()) / 2);
        PdfContentByte under = writer.getDirectContentUnder();
        PdfGState state = new PdfGState();
        state.setFillOpacity(0.075f);
        state.setStrokeOpacity(0.075f);
        under.saveState();
        under.setGState(state);
        under.addImage(watermark);
        under.restoreState();
      } catch (Exception exception) {
        throw new ExceptionConverter(exception);
      }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
      PdfContentByte canvas = writer.getDirectContent();
      canvas.saveState();
      canvas.setLineWidth(.7f);
      canvas.setColorStroke(GRID);
      canvas.moveTo(document.left(), 31);
      canvas.lineTo(document.right(), 31);
      canvas.stroke();
      canvas.setColorStroke(AVIANTO_RED);
      canvas.setLineWidth(2f);
      canvas.moveTo(document.left(), 31);
      canvas.lineTo(document.left() + 32, 31);
      canvas.stroke();
      ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
          new Phrase("AVIANTO · Mecánica integral de motos", FOOTER_BRAND),
          (document.left() + document.right()) / 2, 15, 0);
      ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
          new Phrase("Página " + writer.getPageNumber(), PAGE_NUMBER), document.right(), 18, 0);
      canvas.restoreState();
    }
  }
}
