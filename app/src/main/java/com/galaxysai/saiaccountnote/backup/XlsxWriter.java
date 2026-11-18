package com.galaxysai.saiaccountnote.backup;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * 极简 XLSX 写入器。
 *
 * <p>XLSX 本质是一个 ZIP 包,里面放若干 XML。这里只生成一个工作表,
 * 单元格全部用 inlineStr,不依赖 Apache POI 等大体积库。</p>
 *
 * <p>生成的文件可以用 Excel、WPS、Google Sheets 打开。</p>
 */
final class XlsxWriter
{
    private static final String XML_HEADER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private static final String NS_MAIN =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private static final String NS_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String NS_PACKAGE_REL =
            "http://schemas.openxmlformats.org/package/2006/relationships";

    private XlsxWriter()
    {
        // 工具类禁止实例化
    }

    /**
     * @param rows 每一行是若干单元格文本;第一行会作为表头加粗
     */
    static byte[] write(List<String[]> rows) throws Exception
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(buffer))
        {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", sheet(rows));
        }

        return buffer.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception
    {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes()
    {
        return XML_HEADER
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static String rootRels()
    {
        return XML_HEADER
                + "<Relationships xmlns=\"" + NS_PACKAGE_REL + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook()
    {
        return XML_HEADER
                + "<workbook xmlns=\"" + NS_MAIN + "\" xmlns:r=\"" + NS_REL + "\">"
                + "<sheets><sheet name=\"账号\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static String workbookRels()
    {
        return XML_HEADER
                + "<Relationships xmlns=\"" + NS_PACKAGE_REL + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"" + NS_REL + "/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String styles()
    {
        return XML_HEADER
                + "<styleSheet xmlns=\"" + NS_MAIN + "\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"2\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheet(List<String[]> rows)
    {
        StringBuilder xml = new StringBuilder(XML_HEADER);
        xml.append("<worksheet xmlns=\"").append(NS_MAIN).append("\">");

        // 冻结第一行
        xml.append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>");

        // 列宽:文件夹、标题、用户名、密码、网址、邮箱、手机、备注、创建时间、更新时间
        int[] widths = {16, 24, 20, 24, 32, 28, 18, 36, 18, 18};
        xml.append("<cols>");
        for (int i = 0; i < widths.length; i++)
        {
            xml.append("<col min=\"").append(i + 1)
                    .append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(widths[i])
                    .append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols>");

        xml.append("<sheetData>");

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
        {
            String[] cells = rows.get(rowIndex);
            int excelRow = rowIndex + 1;

            xml.append("<row r=\"").append(excelRow).append("\">");

            for (int columnIndex = 0; columnIndex < cells.length; columnIndex++)
            {
                xml.append("<c r=\"")
                        .append(columnName(columnIndex))
                        .append(excelRow)
                        .append("\"");

                // 第一行表头用加粗样式
                if (rowIndex == 0)
                {
                    xml.append(" s=\"1\"");
                }

                xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(cells[columnIndex]))
                        .append("</t></is></c>");
            }

            xml.append("</row>");
        }

        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static String columnName(int index)
    {
        StringBuilder name = new StringBuilder();
        int n = index;

        do
        {
            name.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        while (n >= 0);

        return name.toString();
    }

    /**
     * XML 转义;去掉 XML 不允许的控制字符,防止 Excel 打不开。
     */
    private static String escape(String value)
    {
        if (value == null)
        {
            return "";
        }

        if (value.length() > 32767)
        {
            value = value.substring(0, 32767);
        }

        StringBuilder escaped = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);

            switch (c)
            {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                case '\r':
                    // 统一用 \n
                    break;
                case '\n':
                    escaped.append("&#10;");
                    break;
                case '\t':
                    escaped.append("&#9;");
                    break;
                default:
                    if (c >= 0x20)
                    {
                        escaped.append(c);
                    }
                    break;
            }
        }

        return escaped.toString();
    }
}
