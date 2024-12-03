package com.example.pdfreader;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class ViewPdfActivity extends AppCompatActivity {

    private static final String TAG = "ViewPdfActivity";
    private ImageView pdfImageView;
    private Button prevButton, nextButton;
    private ImageButton micButton; // Microphone button for text-to-speech
    private String pdfUrl;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor fileDescriptor;
    private int currentPageIndex = 0;
    private TextToSpeech textToSpeech; // Text-to-Speech object for reading text aloud

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_pdf);

        pdfImageView = findViewById(R.id.pdfImageView);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);
        micButton = findViewById(R.id.micButton); // Initialize mic button

        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.ENGLISH); // Set language to English
            }
        });

        // Get the PDF URL from the intent
        pdfUrl = getIntent().getStringExtra("pdfUrl");

        if (pdfUrl == null || pdfUrl.isEmpty()) {
            Toast.makeText(this, "Failed to load PDF URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load the PDF from the URL
        loadPdfFromUrl(pdfUrl);

        prevButton.setOnClickListener(v -> showPage(currentPageIndex - 1));
        nextButton.setOnClickListener(v -> showPage(currentPageIndex + 1));

        // Set up microphone button to read the entire PDF
        micButton.setOnClickListener(v -> readEntirePdf());
    }

    private void loadPdfFromUrl(String pdfUrl) {
        new Thread(() -> {
            try {
                URL url = new URL(pdfUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoInput(true);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                File tempFile = File.createTempFile("tempPdf", ".pdf", getCacheDir());
                FileOutputStream outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                runOnUiThread(() -> displayPdf(tempFile));
            } catch (Exception e) {
                Log.e(TAG, "Error loading PDF: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(ViewPdfActivity.this, "Failed to load PDF", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void displayPdf(File pdfFile) {
        try {
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            showPage(0);
        } catch (Exception e) {
            Log.e(TAG, "Error displaying PDF: " + e.getMessage());
            Toast.makeText(this, "Failed to display PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pdfRenderer.getPageCount()) {
            return;
        }

        if (currentPage != null) {
            currentPage.close();
        }

        currentPage = pdfRenderer.openPage(pageIndex);
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        pdfImageView.setImageBitmap(bitmap);
        currentPageIndex = pageIndex;

        // Update the buttons' enabled state
        prevButton.setEnabled(currentPageIndex > 0);
        nextButton.setEnabled(currentPageIndex < pdfRenderer.getPageCount() - 1);
    }

    private void readEntirePdf() {
        new Thread(() -> {
            try {
                File tempFile = new File(getCacheDir(), "tempPdf.pdf");

                // Vérifie si le fichier existe
                if (!tempFile.exists()) {
                    Log.e(TAG, "PDF file does not exist");
                    runOnUiThread(() -> Toast.makeText(ViewPdfActivity.this, "Failed to read PDF: File not found", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Charge le document PDF en utilisant PDFBox
                PDDocument document = PDDocument.load(tempFile);
                PDFTextStripper pdfStripper = new PDFTextStripper();
                String text = pdfStripper.getText(document);
                document.close();

                // Si aucun texte n'est trouvé, essayer d'utiliser OCR
                if (text.isEmpty()) {
                    Log.e(TAG, "No text extracted from PDF, attempting OCR");
                    Bitmap pageBitmap = renderPageToBitmap(0);
                    extractTextFromImage(pageBitmap);
                } else {
                    runOnUiThread(() -> textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error reading PDF: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(ViewPdfActivity.this, "Failed to read PDF", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void extractTextFromImage(Bitmap bitmap) {
        TessBaseAPI tessBaseAPI = new TessBaseAPI();
        // Initialize Tesseract with the path to the trained data files
        String tessDataPath = getFilesDir() + "/tesseract/";
        tessBaseAPI.init(tessDataPath, "eng");  // Use "eng" for English
        tessBaseAPI.setImage(bitmap);
        String extractedText = tessBaseAPI.getUTF8Text();
        tessBaseAPI.end();

        if (extractedText.isEmpty()) {
            Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show();
        } else {
            textToSpeech.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }



    // Fonction pour rendre la page du PDF en Bitmap
    private Bitmap renderPageToBitmap(int pageIndex) {
        Bitmap bitmap = null;
        try {
            if (pdfRenderer != null) {
                PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
                bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error rendering page to bitmap: " + e.getMessage());
        }
        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentPage != null) {
            currentPage.close();
        }
        if (pdfRenderer != null) {
            pdfRenderer.close();
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing file descriptor: " + e.getMessage());
            }
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}
