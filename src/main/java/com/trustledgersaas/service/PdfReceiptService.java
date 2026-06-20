package com.trustledgersaas.service;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.entity.Payment;
import com.trustledgersaas.repository.PaymentRepository;
import com.trustledgersaas.util.InterestCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class PdfReceiptService {

    @Autowired
    private PaymentRepository paymentRepository;

    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font FONT_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font FONT_NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 12);
    private static final Font FONT_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font FONT_SMALL_ITALIC = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

    public byte[] generatePaymentReceipt(Payment payment) {
        GoldLoan loan = payment.getGoldLoan();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, loan.getShop().getShopName(), loan.getShop().getAddress(), loan.getShop().getPhone());

            Paragraph title = new Paragraph("PAYMENT RECEIPT", FONT_TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            BigDecimal totalInterest = InterestCalculator.calculateTotalInterestAccrued(loan.getLoanAmount(), loan.getInterestRate(), loan.getLoanDate());
            BigDecimal totalPaid = paymentRepository.findByGoldLoanIdOrderByPaymentDateDesc(loan.getId())
                    .stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = loan.getLoanAmount().add(totalInterest).subtract(totalPaid);
            if(balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO;

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);
            
            addRow(table, "Receipt Number:", payment.getReceiptNumber());
            addRow(table, "Payment Date:", payment.getPaymentDate().toString());
            addRow(table, "Customer Name:", loan.getCustomer().getFullName());
            addRow(table, "Customer Phone:", loan.getCustomer().getPhone());
            addRow(table, "Loan ID:", loan.getLoanId());
            addRow(table, "Gold Item:", loan.getGoldItemType() + " (" + loan.getGoldWeight() + "g, " + loan.getGoldPurity() + ")");
            addRow(table, "Interest Rate:", loan.getInterestRate() + "% per month");
            addRow(table, "Amount Paid:", "Rs. " + payment.getAmount());
            addRow(table, "Payment Mode:", payment.getPaymentMode());
            addRow(table, "Balance Remaining:", "Rs. " + balance);
            
            document.add(table);

            addFooter(document);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF receipt", e);
            throw new RuntimeException("Failed to generate PDF receipt", e);
        }
    }

    public byte[] generateClosureReceipt(GoldLoan loan) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, loan.getShop().getShopName(), loan.getShop().getAddress(), loan.getShop().getPhone());

            Paragraph title = new Paragraph("LOAN CLOSED — FULL AND FINAL SETTLEMENT", FONT_TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingBefore(10f);
            infoTable.setSpacingAfter(20f);
            
            addRow(infoTable, "Loan ID:", loan.getLoanId());
            addRow(infoTable, "Customer:", loan.getCustomer().getFullName());
            addRow(infoTable, "Principal Amount:", "Rs. " + loan.getLoanAmount());
            addRow(infoTable, "Interest Rate:", loan.getInterestRate() + "% per month");
            addRow(infoTable, "Loan Date:", loan.getLoanDate().toString());
            addRow(infoTable, "Closure Date:", LocalDate.now().toString());
            addRow(infoTable, "Gold Item:", loan.getGoldItemType() + " (" + loan.getGoldWeight() + "g)  [RETURNED]");
            
            document.add(infoTable);

            Paragraph historyTitle = new Paragraph("Payment History", FONT_HEADER);
            historyTitle.setSpacingAfter(10);
            document.add(historyTitle);

            List<Payment> payments = paymentRepository.findByGoldLoanIdOrderByPaymentDateDesc(loan.getId());
            
            PdfPTable historyTable = new PdfPTable(4);
            historyTable.setWidthPercentage(100);
            historyTable.setWidths(new float[]{2f, 2f, 2f, 2f});
            
            addTableHeader(historyTable, "Date");
            addTableHeader(historyTable, "Amount");
            addTableHeader(historyTable, "Mode");
            addTableHeader(historyTable, "Receipt No");

            BigDecimal totalPaid = BigDecimal.ZERO;
            for (Payment payment : payments) {
                historyTable.addCell(new Phrase(payment.getPaymentDate().toString(), FONT_NORMAL));
                historyTable.addCell(new Phrase("Rs. " + payment.getAmount(), FONT_NORMAL));
                historyTable.addCell(new Phrase(payment.getPaymentMode(), FONT_NORMAL));
                historyTable.addCell(new Phrase(payment.getReceiptNumber() != null ? payment.getReceiptNumber() : "N/A", FONT_NORMAL));
                totalPaid = totalPaid.add(payment.getAmount());
            }
            
            document.add(historyTable);

            document.add(new Paragraph(" "));
            
            BigDecimal totalInterest = totalPaid.subtract(loan.getLoanAmount());
            if(totalInterest.compareTo(BigDecimal.ZERO) < 0) totalInterest = BigDecimal.ZERO;

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            addRow(summaryTable, "Total Paid:", "Rs. " + totalPaid);
            addRow(summaryTable, "Total Interest Paid:", "Rs. " + totalInterest);
            document.add(summaryTable);

            addFooter(document);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF closure receipt", e);
            throw new RuntimeException("Failed to generate PDF receipt", e);
        }
    }

    private void addHeader(Document document, String shopName, String shopAddress, String shopPhone) throws DocumentException {
        try (InputStream imageStream = getClass().getResourceAsStream("/static/images/logo-light.png")) {
            if (imageStream != null) {
                Image logo = Image.getInstance(imageStream.readAllBytes());
                logo.scaleAbsoluteHeight(40f);
                logo.scaleAbsoluteWidth(logo.getWidth() * (40f / logo.getHeight()));
                logo.setAlignment(Element.ALIGN_LEFT);
                document.add(logo);
            }
        } catch (Exception e) {
            log.warn("Could not load logo for PDF", e);
        }

        Paragraph name = new Paragraph(shopName != null ? shopName : "Shop", FONT_HEADER);
        name.setAlignment(Element.ALIGN_RIGHT);
        document.add(name);

        Paragraph details = new Paragraph((shopAddress != null ? shopAddress : "") + " | Phone: " + (shopPhone != null ? shopPhone : ""), FONT_NORMAL);
        details.setAlignment(Element.ALIGN_RIGHT);
        details.setSpacingAfter(20);
        document.add(details);
    }

    private void addRow(PdfPTable table, String key, String value) {
        PdfPCell cellKey = new PdfPCell(new Phrase(key, FONT_BOLD));
        cellKey.setBorder(Rectangle.NO_BORDER);
        cellKey.setPaddingBottom(8f);
        
        PdfPCell cellValue = new PdfPCell(new Phrase(value != null ? value : "N/A", FONT_NORMAL));
        cellValue.setBorder(Rectangle.NO_BORDER);
        cellValue.setPaddingBottom(8f);
        
        table.addCell(cellKey);
        table.addCell(cellValue);
    }
    
    private void addTableHeader(PdfPTable table, String header) {
        PdfPCell cell = new PdfPCell(new Phrase(header, FONT_BOLD));
        cell.setPaddingBottom(8f);
        table.addCell(cell);
    }

    private void addFooter(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        String footerText = "Digitally generated receipt from Trust Ledger on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Paragraph footer = new Paragraph(footerText, FONT_SMALL_ITALIC);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }
}
