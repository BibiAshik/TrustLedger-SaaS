package com.trustledgersaas.service;

import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.entity.Payment;
import com.trustledgersaas.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * PdfReceiptService generates simple PDF receipts without an external library.
 *
 * This local implementation keeps the project dependency-light and compileable.
 * It creates a basic single-page PDF containing receipt text. Later, this can be
 * upgraded to iText/PDFBox for richer tables and logo images.
 */
@Service
@Slf4j
public class PdfReceiptService {

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * Purpose: Generate a payment receipt PDF for one payment.
     * Input: The Payment entity selected by customer/shop.
     * Output: PDF bytes for browser download.
     */
    public byte[] generatePaymentReceipt(Payment payment) {
        GoldLoan loan = payment.getGoldLoan();
        StringBuilder text = new StringBuilder();
        text.append("Trust Ledger - Protect Gold. Preserve Trust.\n\n");
        text.append("Payment Receipt\n");
        text.append("Receipt Number: ").append(payment.getReceiptNumber()).append("\n");
        text.append("Payment Date: ").append(payment.getPaymentDate()).append("\n");
        text.append("Amount Paid: Rs. ").append(payment.getAmount()).append("\n");
        text.append("Payment Mode: ").append(payment.getPaymentMode()).append("\n\n");
        text.append("Loan ID: ").append(loan.getLoanId()).append("\n");
        text.append("Customer: ").append(loan.getCustomer().getFullName()).append("\n");
        text.append("Gold Item: ").append(loan.getGoldItemType()).append("\n");
        text.append("Shop: ").append(loan.getShop().getShopName()).append("\n");

        log.info("Generated simple payment receipt for {}", payment.getReceiptNumber());
        return buildSimplePdf(text.toString());
    }

    /**
     * Purpose: Generate a final closure receipt PDF for a closed loan.
     * Input: The GoldLoan entity being closed.
     * Output: PDF bytes for browser download.
     */
    public byte[] generateClosureReceipt(GoldLoan loan) {
        StringBuilder text = new StringBuilder();
        text.append("Trust Ledger - Protect Gold. Preserve Trust.\n\n");
        text.append("LOAN CLOSED - FULL AND FINAL SETTLEMENT\n\n");
        text.append("Loan ID: ").append(loan.getLoanId()).append("\n");
        text.append("Customer: ").append(loan.getCustomer().getFullName()).append("\n");
        text.append("Principal Amount: Rs. ").append(loan.getLoanAmount()).append("\n");
        text.append("Interest Rate: ").append(loan.getInterestRate()).append("% per month\n");
        text.append("Loan Date: ").append(loan.getLoanDate()).append("\n");
        text.append("Closure Date: ").append(LocalDate.now()).append("\n");
        text.append("Gold Item Returned: ").append(loan.getGoldItemType()).append("\n\n");
        text.append("Payment History:\n");

        List<Payment> payments = paymentRepository.findByGoldLoanIdOrderByPaymentDateDesc(loan.getId());
        if (payments.isEmpty()) {
            text.append("No payments recorded.\n");
        } else {
            for (Payment payment : payments) {
                text.append(payment.getPaymentDate())
                        .append(" | ")
                        .append(payment.getReceiptNumber())
                        .append(" | ")
                        .append(payment.getPaymentMode())
                        .append(" | Rs. ")
                        .append(payment.getAmount())
                        .append("\n");
            }
        }

        log.info("Generated simple closure receipt for {}", loan.getLoanId());
        return buildSimplePdf(text.toString());
    }

    /**
     * Purpose: Build a minimal valid PDF containing plain text.
     * Input: Text lines to place on the PDF page.
     * Output: Complete PDF document bytes.
     */
    private byte[] buildSimplePdf(String text) {
        String escapedText = escapePdfText(text).replace("\n", ") Tj T* (");
        String stream = "BT /F1 11 Tf 50 780 Td 14 TL (" + escapedText + ") Tj ET";

        String object1 = "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n";
        String object2 = "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n";
        String object3 = "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n";
        String object4 = "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n";
        String object5 = "5 0 obj << /Length " + stream.getBytes(StandardCharsets.UTF_8).length
                + " >> stream\n" + stream + "\nendstream endobj\n";

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write("%PDF-1.4\n".getBytes(StandardCharsets.UTF_8));

            int xref1 = output.size();
            output.write(object1.getBytes(StandardCharsets.UTF_8));
            int xref2 = output.size();
            output.write(object2.getBytes(StandardCharsets.UTF_8));
            int xref3 = output.size();
            output.write(object3.getBytes(StandardCharsets.UTF_8));
            int xref4 = output.size();
            output.write(object4.getBytes(StandardCharsets.UTF_8));
            int xref5 = output.size();
            output.write(object5.getBytes(StandardCharsets.UTF_8));
            int xrefStart = output.size();

            String xref = "xref\n0 6\n0000000000 65535 f \n"
                    + formatXref(xref1) + " 00000 n \n"
                    + formatXref(xref2) + " 00000 n \n"
                    + formatXref(xref3) + " 00000 n \n"
                    + formatXref(xref4) + " 00000 n \n"
                    + formatXref(xref5) + " 00000 n \n"
                    + "trailer << /Size 6 /Root 1 0 R >>\nstartxref\n"
                    + xrefStart + "\n%%EOF";
            output.write(xref.getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate receipt PDF. Please try again.");
        }
    }

    private String formatXref(int value) {
        return String.format("%010d", value);
    }

    private String escapePdfText(String text) {
        return text.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\r", "");
    }
}
