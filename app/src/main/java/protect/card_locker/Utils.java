package protect.card_locker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.EnumMap;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wanderwildwood.satsuire.R;

/**
 * Catima's helpers, cut down to the ones its database, importers, exporter and file readers
 * still call. Everything that drew Catima's own screens went with them.
 */
public class Utils {
    private static final String TAG = "Catima";

    public static final String CARD_IMAGE_FILENAME_REGEX = "^(card_)(\\d+)(_(?:front|back|icon)\\.png)$";

    private static final double LargestPreciseDouble = (double) (1l << 53);
    static{
        assert (LargestPreciseDouble + 1.0) == LargestPreciseDouble;
        assert (LargestPreciseDouble - 1.0) != LargestPreciseDouble;
    }

    static public List<ParseResult> retrieveBarcodesFromImage(Context context, Uri uri) throws FileNotFoundException {
        Log.i(TAG, "Received image file with possible barcode");

        if (uri == null) {
            throw new FileNotFoundException("Uri did not contain any data");
        }

        Bitmap bitmap;
        try {
            bitmap = retrieveImageFromUri(context, uri);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error reading image file", e);
        }

        // Returns barcodes or an empty list if nothing found
        return getBarcodesFromBitmap(bitmap);
    }

    static public List<ParseResult> retrieveBarcodesFromPkPass(Context context, Uri uri) throws FileNotFoundException {
        Log.i(TAG, "Received Pkpass file with possible barcode");
        if (uri == null) {
            throw new FileNotFoundException("Uri did not contain any data");
        }

        PkpassParser pkpassParser;
        try {
             pkpassParser = new PkpassParser(context, uri);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error reading pkpass file", e);
        }

        List<String> locales = pkpassParser.listLocales();
        if (locales.isEmpty()) {
            try {
                return Collections.singletonList(new ParseResult(ParseResultType.FULL, pkpassParser.toLoyaltyCard(null)));
            } catch (Exception e) {
                throw new IllegalArgumentException("Error calling toLoyaltyCard on pkpass file", e);
            }
        }

        List<ParseResult> parseResultList = new ArrayList<>();
        for (String locale : locales) {
            ParseResult parseResult;
            try {
                 parseResult = new ParseResult(ParseResultType.FULL, pkpassParser.toLoyaltyCard(locale));
            } catch (Exception e) {
                throw new IllegalArgumentException("Error calling toLoyaltyCard on pkpass file", e);
            }
            parseResult.setNote(locale);
            parseResultList.add(parseResult);
        }

        return parseResultList;
    }

    static public List<ParseResult> retrieveBarcodesFromPkPasses(Context context, Uri uri) throws FileNotFoundException {
        Log.i(TAG, "Received Pkpasses file with possible barcode");
        if (uri == null) {
            throw new FileNotFoundException("Uri did not contain any data");
        }

        PkpassesParser pkpassesParser;
        try {
            pkpassesParser = new PkpassesParser(context, uri);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error reading pkpasses file", e);
        }

        List<ParseResult> parseResultList = new ArrayList<>();
        int i = 0;
        for (PkpassParser pkpassParser : pkpassesParser.getPkpassParsers()) {
            ParseResult parseResult;
            List<String> locales = pkpassParser.listLocales();
            if (locales.isEmpty()) {
                try {
                    parseResult = new ParseResult(ParseResultType.FULL, pkpassParser.toLoyaltyCard(null));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Error calling toLoyaltyCard on pkpass file", e);
                }
                parseResult.setNote(String.format(context.getString(R.string.cardWithNumber), i+1));
                parseResultList.add(parseResult);
            } else {
                for (String locale : locales) {
                    try {
                        parseResult = new ParseResult(ParseResultType.FULL, pkpassParser.toLoyaltyCard(locale));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Error calling toLoyaltyCard on pkpass file", e);
                    }
                    parseResult.setNote(String.format(context.getString(R.string.cardWithNumberAndLocale), i+1, locale));
                    parseResultList.add(parseResult);
                }
            }

            i++;
        }

        return parseResultList;
    }

    static public List<ParseResult> retrieveBarcodesFromPdf(Context context, Uri uri) throws FileNotFoundException {
        Log.i(TAG, "Received PDF file with possible barcode");
        if (uri == null) {
            throw new FileNotFoundException("Uri did not contain any data");
        }

        ParcelFileDescriptor parcelFileDescriptor = null;
        PdfRenderer renderer = null;
        List<ParseResult> barcodesFromPdfPages = new ArrayList<>();

        try {
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            if (parcelFileDescriptor != null) {
                renderer = new PdfRenderer(parcelFileDescriptor);

                // Loop over all pages to find barcodes
                Bitmap renderedPage;
                for (int i = 0; i < renderer.getPageCount(); i++) {
                    PdfRenderer.Page page = renderer.openPage(i);
                    renderedPage = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);

                    // Ensure the page has a background
                    // Fixes some transparent PDF files not being read well
                    Canvas canvas = new Canvas(renderedPage);
                    canvas.drawColor(Color.WHITE);
                    canvas.drawBitmap(renderedPage, 0, 0, null);

                    page.render(renderedPage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();

                    List<ParseResult> barcodesFromPage = getBarcodesFromBitmap(renderedPage);
                    for (ParseResult parseResult : barcodesFromPage) {
                        parseResult.setNote(String.format(context.getString(R.string.pageWithNumber), i+1));
                        barcodesFromPdfPages.add(parseResult);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Error reading PDF file", e);
        } finally {
            // Resource handling
            if (renderer != null) {
                renderer.close();
            }
            if (parcelFileDescriptor != null) {
                try {
                    parcelFileDescriptor.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing ParcelFileDescriptor", e);
                }
            }
        }

        // Returns barcodes or an empty list if nothing found
        return barcodesFromPdfPages;
    }

    static public Bitmap retrieveImageFromUri(Context context, Uri data) throws IOException {
        ImageDecoder.Source image_source = ImageDecoder.createSource(context.getContentResolver(), data);
        return ImageDecoder.decodeBitmap(image_source, (decoder, info, source) -> {
            decoder.setMutableRequired(true);
            // A barcode read off a photograph needs its detail, but a camera's full frame
            // decoded whole is tens of megabytes. Halve it until its long side is under 4000.
            int longSide = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
            int sample = 1;
            while (longSide / sample > 4000) sample *= 2;
            decoder.setTargetSampleSize(sample);
        });
    }

    static public List<ParseResult> getBarcodesFromBitmap(Bitmap bitmap) {
        // This function is vulnerable to OOM, so we try again with a smaller bitmap is we get OOM
        for (int i = 0; i < 10; i++) {
            try {
                return Utils.getBarcodesFromBitmapReal(bitmap);
            } catch (OutOfMemoryError e) {
                Log.w(TAG, "Ran OOM in getBarcodesFromBitmap! Trying again with smaller picture! Retry " + i + " of 10.");
                bitmap = Bitmap.createScaledBitmap(bitmap, (int) Math.round(0.75 * bitmap.getWidth()), (int) Math.round(0.75 * bitmap.getHeight()), false);
            }
        }

        // Give up
        return new ArrayList<>();
    }

    static private List<ParseResult> getBarcodesFromBitmapReal(Bitmap bitmap) {
        // In order to decode it, the Bitmap must first be converted into a pixel array...
        int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // ...and then turned into a binary bitmap from its luminance. The hybrid binarizer is
        // tried first because it copes with a photograph's uneven light; the global one, which
        // was Catima's only one, reads a clean screenshot and stays as the second try.
        LuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
        List<ParseResult> parseResultList = new ArrayList<>();
        for (BinaryBitmap binaryBitmap : new BinaryBitmap[]{
                new BinaryBitmap(new HybridBinarizer(source)),
                new BinaryBitmap(new GlobalHistogramBinarizer(source))}) {
            try {
                MultiFormatReader multiFormatReader = new MultiFormatReader();
                MultipleBarcodeReader multipleBarcodeReader = new GenericMultipleBarcodeReader(multiFormatReader);

                Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
                hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
                hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

                Result[] barcodeResults = multipleBarcodeReader.decodeMultiple(binaryBitmap, hints);

                for (Result barcodeResult : barcodeResults) {
                    // What was read is a card number, so it is not written to the log.
                    Log.i(TAG, "Read a barcode, format " + barcodeResult.getBarcodeFormat().name());

                    LoyaltyCard loyaltyCard = new LoyaltyCard();
                    loyaltyCard.setCardId(barcodeResult.getText());
                    loyaltyCard.setBarcodeType(CatimaBarcode.fromBarcode(barcodeResult.getBarcodeFormat()));
                    parseResultList.add(new ParseResult(ParseResultType.BARCODE_ONLY, loyaltyCard));
                }

                if (!parseResultList.isEmpty()) {
                    return parseResultList;
                }
            } catch (NotFoundException e) {
                // Nothing with this binarizer; try the next.
            }
        }
        return parseResultList;
    }

    static public Boolean isNotYetValid(Date validFromDate) {
        // The note in `hasExpired` does not apply here, since the bug was fixed before this feature was added.
        return validFromDate.after(getStartOfToday().getTime());
    }

    static public Boolean hasExpired(Date expiryDate) {
        // Note: In #1083 it was discovered that `DatePickerFragment` may sometimes store the expiryDate
        // at 12:00 PM instead of 12:00 AM in the DB. While this has been fixed and the 12-hour difference
        // is not a problem for the way the comparison currently works, it's good to keep in mind such
        // dates may exist in the DB in case the comparison changes in the future and the new one relies
        // on both dates being set at 12:00 AM.
        return expiryDate.before(getStartOfToday().getTime());
    }

    static private Calendar getStartOfToday() {
        // today
        Calendar date = new GregorianCalendar();
        // reset hour, minutes, seconds and millis
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        return date;
    }

    static public String formatBalance(Context context, BigDecimal value, Currency currency) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(false);

        if (currency == null) {
            numberFormat.setMaximumFractionDigits(0);
            return context.getResources().getQuantityString(R.plurals.balancePoints, value.intValue(), numberFormat.format(value));
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
        currencyFormat.setGroupingUsed(false);
        currencyFormat.setCurrency(currency);
        currencyFormat.setMinimumFractionDigits(currency.getDefaultFractionDigits());
        currencyFormat.setMaximumFractionDigits(currency.getDefaultFractionDigits());

        return currencyFormat.format(value);
    }

    static public String formatBalanceWithoutCurrencySymbol(BigDecimal value, Currency currency) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(false);

        if (currency == null) {
            numberFormat.setMaximumFractionDigits(0);
            return numberFormat.format(value);
        }

        numberFormat.setMinimumFractionDigits(currency.getDefaultFractionDigits());
        numberFormat.setMaximumFractionDigits(currency.getDefaultFractionDigits());

        return numberFormat.format(value);
    }

    private static BigDecimal fromParsed(Number parsed){
        if(parsed instanceof BigDecimal)
            return (BigDecimal) parsed;

        final double d = parsed.doubleValue();
        if(d >= LargestPreciseDouble)
            return new BigDecimal(parsed.longValue());
        return new BigDecimal(d);
    }

    static public BigDecimal parseBalance(String value, Currency currency) throws ParseException {
        // This function expects the input string to not have any grouping (thousand separators).
        // It will refuse to work otherwise
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(false);

        if (numberFormat instanceof DecimalFormat) {
            ((DecimalFormat) numberFormat).setParseBigDecimal(true);
        }

        if (currency == null) {
            numberFormat.setMaximumFractionDigits(0);
        } else {
            int fractionDigits = currency.getDefaultFractionDigits();

            numberFormat.setMinimumFractionDigits(fractionDigits);
            numberFormat.setMaximumFractionDigits(fractionDigits);

            if (numberFormat instanceof DecimalFormat) {
                // If the string contains both thousand separators and decimals separators, fail hard
                DecimalFormatSymbols decimalFormatSymbols = ((DecimalFormat) numberFormat).getDecimalFormatSymbols();
                char decimalSeparator = decimalFormatSymbols.getDecimalSeparator();

                // Translate all non-digits to decimal separators, failing if we find more than 1.
                // We loop over the codepoints to make sure eastern arabic numerals are not mistakenly
                // treated as a separator.
                boolean separatorFound = false;
                StringBuilder translatedValue = new StringBuilder();
                for (int i = 0; i < value.length();) {
                    int character = value.codePointAt(i);

                    if (Character.isDigit(character)) {
                        translatedValue.append(value.charAt(i));
                    } else {
                        if (separatorFound) {
                            throw new ParseException("Contains multiple separators", i);
                        }

                        separatorFound = true;
                        translatedValue.append(decimalSeparator);
                    }

                    i += Character.charCount(character);
                }

                value = translatedValue.toString();
            }
        }

        return fromParsed(numberFormat.parse(value));
    }

    static public byte[] bitmapToByteArray(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
        return bos.toByteArray();
    }

    static public String getCardImageFileName(int loyaltyCardId, ImageLocationType type) {
        StringBuilder cardImageFileNameBuilder = new StringBuilder();

        cardImageFileNameBuilder.append("card_");
        cardImageFileNameBuilder.append(loyaltyCardId);
        cardImageFileNameBuilder.append("_");
        if (type == ImageLocationType.front) {
            cardImageFileNameBuilder.append("front");
        } else if (type == ImageLocationType.back) {
            cardImageFileNameBuilder.append("back");
        } else if (type == ImageLocationType.icon) {
            cardImageFileNameBuilder.append("icon");
        } else {
            throw new IllegalArgumentException("Unknown image type");
        }
        cardImageFileNameBuilder.append(".png");

        return cardImageFileNameBuilder.toString();
    }

    static public String getRenamedCardImageFileName(final String fileName, final Map<Integer, Integer> idMap) {
        Pattern pattern = Pattern.compile(CARD_IMAGE_FILENAME_REGEX);
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.matches()) {
            StringBuilder cardImageFileNameBuilder = new StringBuilder();
            cardImageFileNameBuilder.append(matcher.group(1));
            try {
                int id = Integer.parseInt(matcher.group(2));
                cardImageFileNameBuilder.append(idMap.getOrDefault(id, id));
            } catch (NumberFormatException _e) {
                return null;
            }
            cardImageFileNameBuilder.append(matcher.group(3));
            return cardImageFileNameBuilder.toString();
        }
        return null;
    }

    static public void saveCardImage(Context context, Bitmap bitmap, String fileName) throws FileNotFoundException {
        if (bitmap == null) {
            context.deleteFile(fileName);
            return;
        }

        FileOutputStream out = context.openFileOutput(fileName, Context.MODE_PRIVATE);

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }

    static public void saveCardImage(Context context, Bitmap bitmap, int loyaltyCardId, ImageLocationType type) throws FileNotFoundException {
        saveCardImage(context, bitmap, getCardImageFileName(loyaltyCardId, type));
    }

    public static File retrieveCardImageAsFile(Context context, String fileName) {
        return context.getFileStreamPath(fileName);
    }

    public static File retrieveCardImageAsFile(Context context, int loyaltyCardId, ImageLocationType type) {
        return retrieveCardImageAsFile(context, getCardImageFileName(loyaltyCardId, type));
    }

    static public Bitmap retrieveCardImage(Context context, String fileName) {
        FileInputStream in;
        try {
            in = context.openFileInput(fileName);
        } catch (FileNotFoundException e) {
            return null;
        }

        return BitmapFactory.decodeStream(in);
    }

    static public Bitmap retrieveCardImage(Context context, int loyaltyCardId, ImageLocationType type) {
        return retrieveCardImage(context, getCardImageFileName(loyaltyCardId, type));
    }

    static public <T, U> U mapGetOrDefault(Map<T, U> map, T key, U defaultValue) {
        U value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    static public long getUnixTime() {
        return System.currentTimeMillis() / 1000;
    }

    public static File createTempFile(Context context, String name) {
        return new File(context.getCacheDir() + "/" + name);
    }

    public static File copyToTempFile(Context context, InputStream input, String name) throws IOException {
        File file = createTempFile(context, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = input.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return file;
        }
    }

    public static String saveTempImage(Context context, Bitmap in, String name, Bitmap.CompressFormat format) {
        File image = createTempFile(context, name);
        try (FileOutputStream out = new FileOutputStream(image)) {
            in.compress(format, 100, out);
            return image.getAbsolutePath();
        } catch (IOException e) {
            Log.d("store temp image", "failed writing temp file for temporary image, name: " + name);
            return null;
        }
    }

    public static @Nullable Bitmap loadImage(String path) {
        try {
            return BitmapFactory.decodeStream(new FileInputStream(path));
        } catch (IOException e) {
            Log.d("load image", "failed loading image from " + path);
            return null;
        }
    }

    public static @Nullable Bitmap loadTempImage(Context context, String name) {
        return loadImage(context.getCacheDir() + "/" + name);
    }

    public static int getRandomHeaderColor(Context context) {
        TypedArray colors = context.getResources().obtainTypedArray(R.array.letter_tile_colors);
        final int color = (int) (Math.random() * colors.length());
        return colors.getColor(color, Color.BLACK);
    }

    public static String checksum(InputStream input) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[4096];
            int len;
            while ((len = input.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException _e) {
            return null;
        }
    }

    public static boolean equals(final Object a, final Object b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
