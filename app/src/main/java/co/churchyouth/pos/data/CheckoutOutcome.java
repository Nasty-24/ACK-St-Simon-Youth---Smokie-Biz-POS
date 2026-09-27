package co.churchyouth.pos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import co.churchyouth.pos.model.CartLine;
import co.churchyouth.pos.model.Product;
import co.churchyouth.pos.model.Sale;
import co.churchyouth.pos.model.SaleItem;

/**
 * NOTE on dates: this whole app deliberately avoids java.time.* (LocalDate,
 * Instant, etc.) — that API only exists from API 26 onward without extra
 * Gradle "core library desugaring" configuration, and the Sunmi V2 Pro is
 * API 25. SimpleDateFormat/Calendar/Date are used everywhere instead,
 * which have worked since API 1.
 */
public class CheckoutOutcome {
    public boolean success;
    public String errorMessage;
    public String receiptNo;
}
