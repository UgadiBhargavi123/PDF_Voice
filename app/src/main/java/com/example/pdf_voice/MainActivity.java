package com.example.pdf_voice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.barteksc.pdfviewer.PDFView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private PDFView pdfView;
    private TextView welcome_tv, durationTime_tv;
    private FloatingActionButton openPdfBtn, pause_resume_btn;
    private TextToSpeech textToSpeech;
    ImageView addPdf_iv;
    private SeekBar seekBar;

    private String extractedText = "";
    private int currentPosition = 0;
    private boolean isPaused = false, isRepeat = false;

    private int totalWordCount = 0;
    private boolean isUserSeeking = false;

    private String totalTime = "00:00";

    private final ActivityResultLauncher<Intent> pdfPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        welcome_tv.setVisibility(View.GONE);
                        pdfView.setVisibility(View.VISIBLE);

                        Log.e("URI", uri.toString());
                        pdfView.fromUri(uri).load();

                        extractTextAndSpeak(uri);
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        pdfView = findViewById(R.id.pdf_view);
        welcome_tv = findViewById(R.id.welocme_tv);
        openPdfBtn = findViewById(R.id.add_btn);
        seekBar = findViewById(R.id.seekbar);
        addPdf_iv = findViewById(R.id.add_new_pdf);
        durationTime_tv = findViewById(R.id.time);
        addPdf_iv.setVisibility(View.GONE);
        pause_resume_btn = findViewById(R.id.pause_resume_btn);

        addPdf_iv.setOnClickListener(view -> openPdfPicker());

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.ENGLISH);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported");
                }
            } else {
                Log.e("TTS", "Initialization failed");
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && extractedText != null && !extractedText.isEmpty()) {
                    isUserSeeking = true;
                    updateElapsedTime(progress);
                    currentPosition = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;

                if (extractedText != null && !extractedText.isEmpty()) {
                    textToSpeech.stop();
                    String partialText = extractedText.substring(0, currentPosition).trim();
                    int wordsUntilNow = partialText.isEmpty() ? 0 : partialText.split("\\s+").length;
                    elapsedSeconds = (int) Math.ceil(wordsUntilNow / 2.5);

                    if (currentPosition >= extractedText.length() - 1) {
                        pause_resume_btn.setImageResource(R.drawable.ic_baseline_restart_alt_24);
                        isRepeat = true;
                        addPdf_iv.setVisibility(View.VISIBLE);
                    } else {
                        startSpeechFromPosition(currentPosition);
                        pause_resume_btn.setImageResource(R.drawable.ic_baseline_pause_24);
                        isPaused = false;
                    }
                }
            }
        });

        // ✅ Pause / Resume button
        pause_resume_btn.setOnClickListener(view -> {
            if (isRepeat) {
                isRepeat = false;
                isPaused = false;
                seekBar.setProgress(0);
                pause_resume_btn.setImageResource(R.drawable.ic_baseline_pause_24);
                startSpeechFromPosition(0);
            } else {
                if (!isPaused) {
                    if (textToSpeech != null && textToSpeech.isSpeaking()) {
                        textToSpeech.stop();
                        isPaused = true;
                        pause_resume_btn.setImageResource(R.drawable.ic_baseline_stop_24);
                    }
                } else {
                    if (extractedText != null && !extractedText.isEmpty()) {
                        startSpeechFromPosition(currentPosition);
                        isPaused = false;
                        pause_resume_btn.setImageResource(R.drawable.ic_baseline_pause_24);
                    }
                }
            }
        });

        openPdfBtn.setOnClickListener(v -> openPdfPicker());
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pdfPickerLauncher.launch(intent);
    }

    private void startSpeechFromPosition(int position) {
        if (textToSpeech != null && position < extractedText.length()) {
            textToSpeech.stop();
            currentPosition = position;
            seekBar.setMax(extractedText.length());

            int chunkSize = 500;
            int start = position;

            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) { }

                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        if (!utteranceId.equals("LAST")) {
                            currentPosition = Integer.parseInt(utteranceId);
                            seekBar.setProgress(currentPosition);
                            pause_resume_btn.setImageResource(R.drawable.ic_baseline_pause_24);

                        } else {
                            pause_resume_btn.setImageResource(R.drawable.ic_baseline_restart_alt_24);
                            isPaused = true;
                        }
                    });
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e("TTS", "Error on chunk: " + utteranceId);
                }
            });

            while (start < extractedText.length()) {
                int end = Math.min(start + chunkSize, extractedText.length());
                String chunk = extractedText.substring(start, end);

                if (end < extractedText.length()) {
                    int lastSpace = chunk.lastIndexOf(" ");
                    if (lastSpace > 0) {
                        end = start + lastSpace;
                        chunk = extractedText.substring(start, end);
                    }
                }

                String utteranceId = (end >= extractedText.length()) ? "LAST" : String.valueOf(end);
                textToSpeech.speak(chunk, TextToSpeech.QUEUE_ADD, null, utteranceId);
                start = end;
            }
        }
    }

    private void extractTextAndSpeak(Uri pdfUri) {
        try {
            PDFBoxResourceLoader.init(getApplicationContext());
            InputStream inputStream = getContentResolver().openInputStream(pdfUri);
            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
            document.close();

            if (extractedText != null && !extractedText.isEmpty()) {
                totalWordCount = extractedText.trim().split("\\s+").length;

                int totalSeconds = (int) Math.ceil(totalWordCount / 2.5);
                int min = totalSeconds / 60;
                int sec = totalSeconds % 60;
                totalTime = String.format("%02d:%02d", min, sec);

                durationTime_tv.setText("00:00 / " + totalTime);

                seekBar.setMax(extractedText.length());
                pause_resume_btn.setVisibility(View.VISIBLE);
                openPdfBtn.setVisibility(View.GONE);

                startSpeechFromPosition(0);
                startElapsedTimer(totalSeconds, totalTime);
            } else {
                Log.e("TTS", "No text found in PDF");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private Handler timerHandler = new Handler();
    private int elapsedSeconds = 0;

    private void startElapsedTimer(int totalSeconds, String totalTime) {
        elapsedSeconds = 0;
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (textToSpeech != null && textToSpeech.isSpeaking()) {

                    if (!isUserSeeking) {
                        elapsedSeconds++;
                        int min = elapsedSeconds / 60;
                        int sec = elapsedSeconds % 60;
                        String elapsed = String.format("%02d:%02d", min, sec);
                        durationTime_tv.setText(elapsed + " / " + totalTime);
                    }

                    timerHandler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    // ✅ Helper method to update time instantly when dragging
    private void updateElapsedTime(int progress) {
        String partialText = extractedText.substring(0, progress).trim();
        int wordsUntilNow = partialText.isEmpty() ? 0 : partialText.split("\\s+").length;

        int secondsElapsed = (int) Math.ceil(wordsUntilNow / 2.5);
        int min = secondsElapsed / 60;
        int sec = secondsElapsed % 60;

        durationTime_tv.setText(
                String.format("%02d:%02d / %s", min, sec, totalTime)
        );
    }
}
