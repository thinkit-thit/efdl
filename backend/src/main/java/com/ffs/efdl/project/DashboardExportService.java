package com.ffs.efdl.project;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.project.DashboardService.Bar;
import com.ffs.efdl.project.DashboardService.Dashboard;
import com.ffs.efdl.project.DashboardService.TimelineRow;
import com.lowagie.text.Chunk;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Export du tableau de bord d'un programme en PDF ou Word : en-tête « Nom du programme,
 * Financement, Objectif Global », indicateurs, carte des districts bénéficiaires (image envoyée par l'écran, identique
 * à la carte affichée), situations « achevé » / « échu » et situation du programme.
 */
@Service
public class DashboardExportService {

    public enum Format { PDF, DOCX }

    /** Contenu commun aux deux formats. */
    /** {@code projet} : tableau de bord d'un projet ; {@code title} : « PROGRAMME - projet » pour un projet. */
    private record Report(boolean projet, String title, String financement, String objectifGlobal, List<String[]> indicators, String mapTitle,
                          byte[] map, List<String[]> produits, List<Bar> acheves, List<Bar> echus, List<String[]> frise, String date) {
    }

    private final DashboardService dashboards;
    private final MongoTemplate mongo;

    public DashboardExportService(DashboardService dashboards, MongoTemplate mongo) {
        this.dashboards = dashboards;
        this.mongo = mongo;
    }

    public byte[] programme(String school, String id, Format format, byte[] mapPng) throws IOException {
        return build(dashboards.programme(school, id), programme(school, id), false, format, mapPng);
    }

    /** Tableau de bord d'un projet (sous-programme) : « PROGRAMME - projet », financement et objectif de son programme. */
    public byte[] subProgramme(String school, String programmeId, String id, Format format, byte[] mapPng) throws IOException {
        return build(dashboards.subProgramme(school, programmeId, id), programme(school, programmeId), true, format, mapPng);
    }

    private Document programme(String school, String id) {
        Document programme = mongo.findOne(Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").is(id)
                .and("status").ne(BaseEntity.STATUS_DELETED)), Document.class, "Programme");
        if (programme == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme introuvable");
        return programme;
    }

    private byte[] build(Dashboard d, Document programme, boolean projet, Format format, byte[] mapPng) throws IOException {
        if (mapPng != null && mapPng.length > 0 && ImageIO.read(new ByteArrayInputStream(mapPng)) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image de la carte illisible");
        }
        NumberFormat n = NumberFormat.getNumberInstance(Locale.FRANCE);
        String pct = NumberFormat.getNumberInstance(Locale.FRANCE).format(Math.round(d.avancementMoyen() * 10000) / 100.0);
        int shown = d.districtMap().size();
        String name = projet ? nz(programme.getString("title")) + " - " + nz(d.title()) : nz(d.title());
        Report r = new Report(projet, name, nz(programme.getString("financement")), nz(programme.getString("objectifGlobal")),
                List.of(new String[] { projet ? "Budget total du projet" : "Budget total du programme", number(n, d.budget()) + " MGA" },
                        new String[] { projet ? "Nombre de sous-projets Actifs" : "Nombre de Projets Actifs", d.projets() + " projets" },
                        new String[] { "Avancement Physique Moyen", pct + " %" },
                        new String[] { "Nombre de Districts Bénéficiaires", d.districts() + plural(" district", d.districts()) },
                        new String[] { "Nombre de Communes Bénéficiaires", d.communes() + plural(" commune", d.communes()) }),
                "Les " + shown + plural(" district", shown) + plural(" bénéficiaire", shown) + (projet ? " du projet " : " de ") + name,
                mapPng == null || mapPng.length == 0 ? null : mapPng,
                d.indicateurs().stream().map(i -> new String[] { i.label(), number(n, i.value()) }).toList(),
                d.acheves(), d.echus(), frise(d.frise()),
                new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        return format == Format.PDF ? pdf(r) : docx(r);
    }

    // ---------- PDF ----------

    private static byte[] pdf(Report r) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document doc = new com.lowagie.text.Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            Paragraph t = new Paragraph((r.projet() ? "TABLEAU DE BORD PROJET : " : "TABLEAU DE BORD - ") + r.title(), title);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingAfter(12);
            doc.add(t);
            // En-tête : nom, financement, objectif global.
            PdfPTable header = table(new float[] { 1.2f, 3f });
            row(header, r.projet() ? "Nom du projet" : "Nom du programme", r.title(), bold, normal);
            row(header, "Financement", r.financement(), bold, normal);
            row(header, "Objectif Global", r.objectifGlobal(), bold, normal);
            doc.add(header);

            doc.add(section("Indicateurs", h2));
            PdfPTable kpi = table(new float[] { 2f, 1.5f });
            for (String[] i : r.indicators()) row(kpi, i[0], i[1], normal, bold);
            doc.add(kpi);

            doc.add(section(r.mapTitle(), h2));
            if (r.map() != null) {
                Image img = Image.getInstance(r.map());
                img.scaleToFit(320, 480);
                img.setAlignment(Element.ALIGN_CENTER);
                doc.add(img);
            } else {
                doc.add(new Paragraph("Carte non disponible.", small));
            }

            if (!r.produits().isEmpty()) {
                doc.add(section("Indicateurs de produit", h2));
                PdfPTable produits = table(new float[] { 3f, 1f });
                for (String[] p : r.produits()) row(produits, p[0], p[1], normal, bold);
                doc.add(produits);
            }
            doc.add(section("Situation des sous-projets (achevé)", h2));
            doc.add(bars(r.acheves(), bold, normal));
            doc.add(section("Situation des sous-projets (échu)", h2));
            doc.add(bars(r.echus(), bold, normal));
            doc.add(section(r.projet() ? "Situation du projet" : "Situation du programme", h2));
            PdfPTable frise = table(new float[] { 0.6f, 2.4f, 1.6f, 1.6f, 1.2f });
            for (String h : new String[] { "Ordre", "Jalon", "Prévision", "Réel", "Statut" }) frise.addCell(cell(h, bold, true));
            if (r.frise().isEmpty()) {
                PdfPCell none = cell("Aucun jalon renseigné.", normal, false);
                none.setColspan(5);
                frise.addCell(none);
            }
            for (String[] f : r.frise()) for (String v : f) frise.addCell(cell(v, normal, false));
            doc.add(frise);

            Paragraph footer = new Paragraph("Exporté d'eFDL le " + r.date(), small);
            footer.setSpacingBefore(12);
            doc.add(footer);
        } catch (DocumentException e) {
            throw new IOException("PDF impossible", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingAfter(6);
        return t;
    }

    private static void row(PdfPTable t, String label, String value, Font labelFont, Font valueFont) {
        t.addCell(cell(label, labelFont, true));
        t.addCell(cell(value, valueFont, false));
    }

    private static PdfPCell cell(String text, Font font, boolean shaded) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(5);
        if (shaded) c.setBackgroundColor(new Color(0xF1, 0xF5, 0xFA));
        return c;
    }

    private static Paragraph section(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    private static com.lowagie.text.Element bars(List<Bar> bars, Font bold, Font normal) {
        if (bars.isEmpty()) return new Paragraph(new Chunk("Aucun sous-projet.", normal));
        PdfPTable t = table(new float[] { 3f, 1f });
        t.addCell(cell("Jalon", bold, true));
        t.addCell(cell("Sous-projets", bold, true));
        for (Bar b : bars) {
            t.addCell(cell(b.label(), normal, false));
            t.addCell(cell(String.valueOf(b.count()), normal, false));
        }
        return t;
    }

    // ---------- Word ----------

    private static byte[] docx(Report r) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph t = doc.createParagraph();
            t.setAlignment(ParagraphAlignment.CENTER);
            run(t, (r.projet() ? "TABLEAU DE BORD PROJET : " : "TABLEAU DE BORD - ") + r.title(), true, 16);

            XWPFTable header = doc.createTable(3, 2);
            fill(header.getRow(0), r.projet() ? "Nom du projet" : "Nom du programme", r.title());
            fill(header.getRow(1), "Financement", r.financement());
            fill(header.getRow(2), "Objectif Global", r.objectifGlobal());
            header.setWidth("100%");

            heading(doc, "Indicateurs");
            XWPFTable kpi = doc.createTable(r.indicators().size(), 2);
            for (int i = 0; i < r.indicators().size(); i++) fill(kpi.getRow(i), r.indicators().get(i)[0], r.indicators().get(i)[1]);
            kpi.setWidth("100%");

            heading(doc, r.mapTitle());
            XWPFParagraph map = doc.createParagraph();
            map.setAlignment(ParagraphAlignment.CENTER);
            if (r.map() != null) {
                var image = ImageIO.read(new ByteArrayInputStream(r.map()));
                double scale = Math.min(300.0 / image.getWidth(), 440.0 / image.getHeight());
                try {
                    map.createRun().addPicture(new ByteArrayInputStream(r.map()), XWPFDocument.PICTURE_TYPE_PNG, "carte.png",
                            Units.toEMU(image.getWidth() * scale), Units.toEMU(image.getHeight() * scale));
                } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
                    throw new IOException("Image de la carte", e);
                }
            } else {
                run(map, "Carte non disponible.", false, 9);
            }

            if (!r.produits().isEmpty()) {
                heading(doc, "Indicateurs de produit");
                XWPFTable produits = doc.createTable(r.produits().size(), 2);
                for (int i = 0; i < r.produits().size(); i++) fill(produits.getRow(i), r.produits().get(i)[0], r.produits().get(i)[1]);
                produits.setWidth("100%");
            }
            heading(doc, "Situation des sous-projets (achevé)");
            barsTable(doc, r.acheves());
            heading(doc, "Situation des sous-projets (échu)");
            barsTable(doc, r.echus());
            heading(doc, r.projet() ? "Situation du projet" : "Situation du programme");
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[] { "Ordre", "Jalon", "Prévision", "Réel", "Statut" });
            rows.addAll(r.frise().isEmpty() ? List.<String[]>of(new String[] { "", "Aucun jalon renseigné.", "", "", "" }) : r.frise());
            XWPFTable frise = doc.createTable(rows.size(), 5);
            for (int i = 0; i < rows.size(); i++) {
                for (int c = 0; c < 5; c++) text(frise.getRow(i).getCell(c).getParagraphs().get(0), rows.get(i)[c], i == 0);
            }
            frise.setWidth("100%");

            XWPFParagraph footer = doc.createParagraph();
            run(footer, "Exporté d'eFDL le " + r.date(), false, 8);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void barsTable(XWPFDocument doc, List<Bar> bars) {
        if (bars.isEmpty()) {
            run(doc.createParagraph(), "Aucun sous-projet.", false, 10);
            return;
        }
        XWPFTable t = doc.createTable(bars.size() + 1, 2);
        fill(t.getRow(0), "Jalon", "Sous-projets");
        for (int i = 0; i < bars.size(); i++) fill(t.getRow(i + 1), bars.get(i).label(), String.valueOf(bars.get(i).count()));
        t.setWidth("100%");
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        run(p, text, true, 12);
    }

    private static void fill(XWPFTableRow row, String label, String value) {
        text(row.getCell(0).getParagraphs().get(0), label, true);
        text(row.getCell(1).getParagraphs().get(0), value, false);
    }

    private static void text(XWPFParagraph p, String value, boolean bold) {
        run(p, value == null ? "" : value, bold, 10);
    }

    private static void run(XWPFParagraph p, String text, boolean bold, int size) {
        XWPFRun r = p.createRun();
        r.setBold(bold);
        r.setFontSize(size);
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) r.addBreak();
            r.setText(lines[i]);
        }
    }

    // ---------- utilitaires ----------

    /** Lignes de la frise, avec le statut de l'écran (Terminé, Terminé en retard, En retard, En cours). */
    private static List<String[]> frise(List<TimelineRow> rows) {
        SimpleDateFormat day = new SimpleDateFormat("dd/MM/yyyy");
        Date today = new Date();
        List<String[]> result = new ArrayList<>();
        for (TimelineRow r : rows) {
            String statut = r.reel() != null ? (r.prevision() != null && r.prevision().before(r.reel()) ? "Terminé en retard" : "Terminé")
                    : r.prevision() != null && r.prevision().before(today) ? "En retard" : "En cours";
            result.add(new String[] { nz(r.rang()), nz(r.title()), range(day, r.debutPrevision(), r.prevision()),
                    range(day, r.debutReel(), r.reel()), statut });
        }
        return result;
    }

    private static String range(SimpleDateFormat day, Date from, Date to) {
        List<String> parts = new ArrayList<>();
        if (from != null) parts.add(day.format(from));
        if (to != null) parts.add(day.format(to));
        return String.join(" → ", parts);
    }

    /** Nombre au format français, séparateur de milliers = espace normale (lisible par toutes les polices PDF). */
    private static String number(NumberFormat n, double value) {
        return n.format(value).replace('\u202F', ' ').replace('\u00A0', ' ');
    }

    private static String plural(String word, long n) {
        return n > 1 ? word + "s" : word;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
