package com.ffs.efdl.imports;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Lecture de la première feuille d'un classeur Excel (.xlsx / .xls) : en-têtes normalisés (sans accents, espaces
 * ni ponctuation, pour retrouver « N°Dossier » comme « N° Dossier ») et valeurs des cellules en texte, nombre ou date.
 */
final class ExcelReader {

    private static final DataFormatter FORMAT = new DataFormatter(Locale.FRANCE);

    final List<String> headers = new ArrayList<>();
    final List<Row> rows = new ArrayList<>();

    /** Nom de la feuille lue. */
    final String sheetName;

    ExcelReader(InputStream in) throws IOException {
        try (Workbook workbook = open(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            sheetName = sheet.getSheetName();
            read(sheet);
        }
    }

    private ExcelReader(Sheet sheet) {
        sheetName = sheet.getSheetName();
        read(sheet);
    }

    /** Toutes les feuilles du classeur (ex. localisations : feuille « base » et feuille de population). */
    static List<ExcelReader> allSheets(InputStream in) throws IOException {
        try (Workbook workbook = open(in)) {
            List<ExcelReader> sheets = new ArrayList<>();
            for (Sheet sheet : workbook) sheets.add(new ExcelReader(sheet));
            return sheets;
        }
    }

    private static Workbook open(InputStream in) {
        try {
            return WorkbookFactory.create(in);
        } catch (IOException | RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Fichier Excel illisible : enregistrez-le au format .xlsx");
        }
    }

    private void read(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header != null) {
            for (int i = 0; i < header.getLastCellNum(); i++) headers.add(normalize(text(header.getCell(i))));
        }
        for (Row row : sheet) {
            if (row.getRowNum() > 0) rows.add(row);
        }
    }

    /** Index de la première colonne dont l'en-tête normalisé vaut l'un des noms donnés (-1 si absente). */
    int column(String... names) {
        for (String name : names) {
            int i = headers.indexOf(normalize(name));
            if (i >= 0) return i;
        }
        return -1;
    }

    /** Index de toutes les colonnes portant cet en-tête (ex. les blocs « Dénomination » BE, entreprise, fournisseur). */
    List<Integer> columns(String name) {
        List<Integer> found = new ArrayList<>();
        String n = normalize(name);
        for (int i = 0; i < headers.size(); i++) if (headers.get(i).equals(n)) found.add(i);
        return found;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String v = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static String text(Cell cell) {
        if (cell == null) return null;
        String v = cell.getCellType() == CellType.FORMULA ? FORMAT.formatCellValue(cell, cell.getSheet().getWorkbook()
                .getCreationHelper().createFormulaEvaluator()) : FORMAT.formatCellValue(cell);
        v = v == null ? null : v.trim();
        return v == null || v.isEmpty() ? null : v;
    }

    static String text(Row row, int column) {
        return column < 0 ? null : text(row.getCell(column));
    }

    /** Nombre (« 1 500 000,50 », « 1500000.5 ») ; null si vide ; exception si illisible. */
    static Double number(Row row, int column) {
        if (column < 0) return null;
        Cell cell = row.getCell(column);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        String v = text(cell);
        if (v == null) return null;
        String clean = v.replace(' ', ' ').replace(" ", "").replace("%", "").replace(',', '.');
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("nombre illisible « " + v + " »");
        }
    }

    /** Date Excel ou texte jj/mm/aaaa ; null si vide ; exception si illisible. */
    static Date date(Row row, int column) {
        if (column < 0) return null;
        Cell cell = row.getCell(column);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue();
        if (cell.getCellType() == CellType.NUMERIC) return DateUtil.getJavaDate(cell.getNumericCellValue());
        String v = text(cell);
        if (v == null) return null;
        for (String pattern : List.of("dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yy")) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pattern);
                f.setLenient(false);
                return f.parse(v);
            } catch (ParseException ignored) {
                // format suivant
            }
        }
        throw new IllegalArgumentException("date illisible « " + v + " »");
    }
}
