package co.churchyouth.pos.util;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.InnerResultCallback;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import java.util.List;
import java.util.Locale;

import co.churchyouth.pos.model.Sale;
import co.churchyouth.pos.model.SaleItem;

/**
 * Prints receipts on the Sunmi V2 Pro's built-in 58mm thermal printer.
 *
 * Uses Sunmi's printerlibrary AAR (declared in app/build.gradle) rather
 * than hand-copied .aidl files. The library bundles the printer interfaces
 * internally and selects the right ones for whichever Sunmi model the app
 * is running on, so there are no files to fetch and no chance of shipping
 * an interface that mismatches the device's firmware. Everything printer-
 * specific in this whole app lives in this one file.
 */
public class PrinterHelper {

    private static final String TAG = "PrinterHelper";

    private SunmiPrinterService printerService;

    private final InnerPrinterCallback connectionCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            printerService = service;
        }

        @Override
        protected void onDisconnected() {
            printerService = null;
        }
    };

    /** Call from an Activity's onCreate. Binding is asynchronous — isReady() may be false for a moment after this returns. */
    public void bind(Context context) {
        try {
            InnerPrinterManager.getInstance().bindService(context.getApplicationContext(), connectionCallback);
        } catch (InnerPrinterException e) {
            Log.e(TAG, "Could not bind the Sunmi printer service", e);
        }
    }

    /** Call from the matching onDestroy so the service connection isn't leaked. */
    public void unbind(Context context) {
        try {
            InnerPrinterManager.getInstance().unBindService(context.getApplicationContext(), connectionCallback);
        } catch (InnerPrinterException e) {
            Log.e(TAG, "Could not unbind the Sunmi printer service", e);
        }
    }

    public boolean isReady() {
        return printerService != null;
    }

    public interface PrintCallback {
        void onDone(boolean success, String message);
    }

    public void printReceipt(Sale sale, String businessName, String currencySymbol, String footer, PrintCallback callback) {
        if (printerService == null) {
            // Either the app is running on non-Sunmi hardware, or the bind
            // hasn't completed yet. Either way, say so plainly rather than
            // failing silently — the cashier needs to know the sale is
            // saved but the paper didn't come out.
            callback.onDone(false, "Printer not ready. The sale is saved — try Print again in a moment.");
            return;
        }

        try {
            InnerResultCallback noop = new InnerResultCallback() {
                @Override public void onRunResult(boolean isSuccess) { }
                @Override public void onReturnString(String result) { }
                @Override public void onRaiseException(int code, String msg) {
                    Log.e(TAG, "Printer exception " + code + ": " + msg);
                }
                @Override public void onPrintResult(int code, String msg) { }
            };

            printerService.printerInit(noop);

            // --- Header ---
            printerService.setAlignment(1, noop); // 0 left, 1 center, 2 right
            printerService.printTextWithFont(businessName + "\n", null, 26f, noop);
            printerService.printText("Receipt " + sale.receiptNo + "\n", noop);
            if ("voided".equals(sale.status)) {
                printerService.printText("*** VOIDED ***\n", noop);
            }

            // --- Meta ---
            printerService.setAlignment(0, noop);
            printerService.printText(formatDateTime(sale.saleAtEpoch) + "\n", noop);
            printerService.printText("Till: " + (sale.cashierName != null ? sale.cashierName : "") + "\n", noop);
            printerService.printText("Customer: " + (sale.customerName != null ? sale.customerName : "Walk-in") + "\n", noop);
            printerService.printText("--------------------------------\n", noop);

            // --- Line items, as two columns: description left, amount right ---
            List<SaleItem> items = sale.items;
            if (items != null) {
                for (SaleItem item : items) {
                    String left = item.productName + " x" + trimQty(item.quantity);
                    String right = String.format(Locale.US, "%.2f", item.lineTotalCents / 100.0);
                    printerService.printColumnsText(
                        new String[]{left, right},
                        new int[]{2, 1},   // relative column widths
                        new int[]{0, 2},   // left-align description, right-align amount
                        noop);
                }
            }

            // --- Totals ---
            printerService.printText("--------------------------------\n", noop);
            printerService.setAlignment(2, noop);
            printerService.printTextWithFont(
                String.format(Locale.US, "TOTAL %s %.2f\n", currencySymbol, sale.totalCents / 100.0),
                null, 24f, noop);

            printerService.setAlignment(0, noop);
            printerService.printText(String.format(Locale.US, "Paid (%s): %s %.2f\n",
                sale.paymentMethod.toUpperCase(Locale.US), currencySymbol, sale.amountPaidCents / 100.0), noop);

            long balance = sale.balanceDueCents();
            if (balance > 0) {
                printerService.printText(String.format(Locale.US, "Balance on account: %s %.2f\n",
                    currencySymbol, balance / 100.0), noop);
            }

            // --- Footer + feed + cut ---
            printerService.setAlignment(1, noop);
            printerService.printText("\n" + footer + "\n", noop);
            printerService.lineWrap(3, noop);
            printerService.cutPaper(noop);

            callback.onDone(true, "Printed");
        } catch (RemoteException e) {
            Log.e(TAG, "Print failed", e);
            callback.onDone(false, "Printing failed — check the paper roll and try again.");
        }
    }

    private String trimQty(double qty) {
        if (qty == Math.floor(qty)) return String.valueOf((long) qty);
        return String.valueOf(qty);
    }

    private String formatDateTime(long epochSeconds) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US);
        return fmt.format(new java.util.Date(epochSeconds * 1000L));
    }
}
