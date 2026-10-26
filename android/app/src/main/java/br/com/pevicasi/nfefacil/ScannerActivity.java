package br.com.pevicasi.nfefacil;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.*;
import androidx.camera.core.Camera;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** CameraX owns camera lifecycle; both ML Kit models are bundled and work in airplane mode. */
public class ScannerActivity extends ComponentActivity {
    private PreviewView preview;
    private TextView status;
    private Button torch, capture, readCrop, resume;
    private SeekBar zoom;
    private CropImageView photo;
    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageCapture imageCapture;
    private ImageAnalysis analysis;
    private int lens = CameraSelector.LENS_FACING_BACK;
    private boolean torchOn;
    private volatile boolean photoMode, choosing, destroyed;
    private long lastOcr;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BarcodeScanner barcode = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128).build());
    private final TextRecognizer ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> { if (granted) startCamera(); else status.setText("Câmera sem permissão. Use Galeria ou permita o acesso nas configurações do Android."); });
    private final ActivityResultLauncher<String> gallery = registerForActivityResult(
        new ActivityResultContracts.GetContent(), uri -> { if (uri != null) loadPhoto(uri); });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(12, 12, 12, 12);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(12 + insets.getSystemWindowInsetLeft(), 12 + insets.getSystemWindowInsetTop(),
                12 + insets.getSystemWindowInsetRight(), 12 + insets.getSystemWindowInsetBottom()); return insets;
        });
        TextView title = new TextView(this); title.setText("Ler chave NF-e · CODE 128 e OCR local"); title.setTextSize(20); root.addView(title);
        status = new TextView(this); status.setText("Toque para focar. Use pinça ou a barra para ampliar. Centralize os 44 dígitos."); root.addView(status);
        FrameLayout frame = new FrameLayout(this);
        preview = new PreviewView(this); preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE); frame.addView(preview);
        photo = new CropImageView(this); photo.setVisibility(View.GONE); frame.addView(photo);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        zoom = new SeekBar(this); zoom.setContentDescription("Zoom da câmera"); zoom.setMax(100); zoom.setEnabled(false); root.addView(zoom);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser && camera != null) camera.getCameraControl().setLinearZoom(value / 100f); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout controls = new LinearLayout(this);
        torch = button(controls, "Lanterna", this::toggleTorch); torch.setEnabled(false);
        button(controls, "Trocar", this::switchCamera);
        button(controls, "Galeria", () -> gallery.launch("image/*")); root.addView(controls);
        LinearLayout actions = new LinearLayout(this);
        capture = button(actions, "Foto / recorte", this::takePhoto); capture.setEnabled(false);
        readCrop = button(actions, "Ler recorte", this::readCrop); readCrop.setVisibility(View.GONE);
        resume = button(actions, "Voltar à câmera", this::resumeCamera); resume.setVisibility(View.GONE);
        button(actions, "Fechar", this::finish); root.addView(actions); setContentView(root);
        ScaleGestureDetector scale = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (camera == null || camera.getCameraInfo().getZoomState().getValue() == null) return true;
                ZoomState state = camera.getCameraInfo().getZoomState().getValue();
                camera.getCameraControl().setZoomRatio(Math.max(state.getMinZoomRatio(), Math.min(state.getMaxZoomRatio(), state.getZoomRatio() * detector.getScaleFactor()))); return true;
            }
        });
        GestureDetector tap = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (camera != null) {
                    MeteringPoint point = preview.getMeteringPointFactory().createPoint(e.getX(), e.getY());
                    camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build());
                    status.setText("Foco solicitado. Mantenha a chave bem iluminada.");
                }
                preview.performClick(); return true;
            }
        });
        preview.setOnTouchListener((v, event) -> { scale.onTouchEvent(event); if (!scale.isInProgress()) tap.onTouchEvent(event); return true; });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (photoMode) resumeCamera(); else finish(); }
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else permission.launch(Manifest.permission.CAMERA);
    }
    private static androidx.camera.core.resolutionselector.ResolutionSelector resolution(int width, int height) {
        return new androidx.camera.core.resolutionselector.ResolutionSelector.Builder().setResolutionStrategy(
            new androidx.camera.core.resolutionselector.ResolutionStrategy(new android.util.Size(width, height),
                androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build();
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (destroyed) return;
            try { provider = future.get(); bindCamera(); }
            catch (Exception e) { status.setText("Câmera indisponível. Use a Galeria ou tente novamente."); }
        }, ContextCompat.getMainExecutor(this));
    }
    private void bindCamera() throws Exception {
        if (camera != null) { camera.getCameraInfo().getZoomState().removeObservers(this); camera.getCameraInfo().getTorchState().removeObservers(this); }
        provider.unbindAll(); camera = null;
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lens).build();
        if (!provider.hasCamera(selector)) { lens = lens == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK; selector = new CameraSelector.Builder().requireLensFacing(lens).build(); }
        Preview live = new Preview.Builder().build(); live.setSurfaceProvider(preview.getSurfaceProvider());
        imageCapture = new ImageCapture.Builder().setResolutionSelector(resolution(1920, 1080)).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        analysis = new ImageAnalysis.Builder().setResolutionSelector(resolution(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        analysis.setAnalyzer(executor, this::analyze);
        camera = provider.bindToLifecycle(this, selector, live, imageCapture, analysis);
        torchOn = false; torch.setText("Lanterna"); torch.setEnabled(camera.getCameraInfo().hasFlashUnit()); capture.setEnabled(true); zoom.setEnabled(true);
        camera.getCameraInfo().getZoomState().observe(this, state -> zoom.setProgress(Math.round(state.getLinearZoom() * 100)));
        camera.getCameraInfo().getTorchState().observe(this, state -> { torchOn = state == TorchState.ON; torch.setText(torchOn ? "Apagar" : "Lanterna"); });
    }
    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void analyze(ImageProxy frame) {
        if (destroyed || photoMode || choosing || !busy.compareAndSet(false, true)) { frame.close(); return; }
        android.media.Image media = frame.getImage();
        if (media == null) { frame.close(); busy.set(false); return; }
        InputImage image = InputImage.fromMediaImage(media, frame.getImageInfo().getRotationDegrees());
        barcode.process(image).addOnSuccessListener(results -> {
            Set<String> keys = new LinkedHashSet<>();
            for (com.google.mlkit.vision.barcode.common.Barcode result : results) { String key = NfeKey.barcode(result.getRawValue()); if (!key.isEmpty()) keys.add(key); }
            if (!keys.isEmpty()) { if (!photoMode) offerKeys(keys); frame.close(); busy.set(false); }
            else if (!destroyed && !photoMode && System.currentTimeMillis() - lastOcr > 1500) {
                lastOcr = System.currentTimeMillis();
                ocr.process(image).addOnSuccessListener(text -> { if (!photoMode) offerKeys(NfeKey.fromOcr(text.getText())); })
                    .addOnCompleteListener(task -> { frame.close(); busy.set(false); });
            } else { frame.close(); busy.set(false); }
        }).addOnFailureListener(error -> { frame.close(); busy.set(false); });
    }
    private void offerKeys(Set<String> keys) {
        if (destroyed || isFinishing() || choosing || keys.isEmpty() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        choosing = true;
        String[] values = keys.toArray(new String[0]);
        new android.app.AlertDialog.Builder(this).setTitle("Confirme a chave de 44 dígitos")
            .setItems(values, (dialog, index) -> {
                setResult(RESULT_OK, new Intent().putExtra("key", values[index])); finish();
            }).setNegativeButton("Continuar lendo", (d, w) -> choosing = false)
            .setOnCancelListener(d -> choosing = false).show();
    }
    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
        boolean enabled = !torchOn;
        com.google.common.util.concurrent.ListenableFuture<Void> change = camera.getCameraControl().enableTorch(enabled);
        change.addListener(() -> {
            try { change.get(); } catch (Exception e) { if (!destroyed) status.setText("Não foi possível alterar a lanterna."); }
        }, ContextCompat.getMainExecutor(this));
    }
    private void switchCamera() {
        if (provider == null || photoMode) return;
        lens = lens == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        try { bindCamera(); } catch (Exception e) { status.setText("Não foi possível trocar de câmera."); }
    }
    private void takePhoto() {
        if (imageCapture == null) { status.setText("Câmera indisponível. Use a galeria."); return; }
        photoMode = true; capture.setEnabled(false);
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override public void onCaptureSuccess(ImageProxy image) {
                try {
                    Bitmap bitmap = image.toBitmap(); Matrix rotation = new Matrix(); rotation.postRotate(image.getImageInfo().getRotationDegrees());
                    showPhoto(Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotation, true));
                } catch (RuntimeException e) { status.setText("Não foi possível preparar a foto."); photoMode = false; capture.setEnabled(true); }
                finally { image.close(); }
            }
            @Override public void onError(ImageCaptureException e) { photoMode = false; capture.setEnabled(true); status.setText("Não foi possível fotografar. Tente novamente."); }
        });
    }
    private void loadPhoto(Uri uri) {
        status.setText("Abrindo imagem…");
        executor.execute(() -> {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, options); }
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2600) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap bitmap;
                try (InputStream in = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
                if (bitmap == null) throw new java.io.IOException("Imagem inválida");
                Matrix matrix = new Matrix();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    ExifInterface exif = new ExifInterface(in);
                    if (exif.isFlipped()) matrix.postScale(-1, 1);
                    matrix.postRotate(exif.getRotationDegrees());
                }
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                runOnUiThread(() -> { if (!destroyed) showPhoto(rotated); });
            } catch (Exception e) { runOnUiThread(() -> status.setText("Imagem inválida ou sem acesso. Escolha outra imagem.")); }
        });
    }
    private void showPhoto(Bitmap bitmap) {
        photoMode = true; if (camera != null) camera.getCameraControl().enableTorch(false);
        torchOn = false; torch.setEnabled(false); zoom.setEnabled(false);
        photo.setBitmap(bitmap); photo.setVisibility(View.VISIBLE); preview.setVisibility(View.INVISIBLE);
        capture.setVisibility(View.GONE); readCrop.setVisibility(View.VISIBLE); resume.setVisibility(View.VISIBLE);
        status.setText("Arraste para mover o recorte; arraste o canto inferior direito para redimensionar. Selecione a chave ou código e toque em Ler recorte.");
    }
    private void readCrop() {
        if (!busy.compareAndSet(false, true)) return;
        Bitmap crop = photo.crop(); if (crop == null) { busy.set(false); return; }
        readCrop.setEnabled(false); status.setText("Lendo código e texto localmente…");
        InputImage image = InputImage.fromBitmap(crop, 0);
        barcode.process(image).addOnSuccessListener(results -> {
            Set<String> keys = new LinkedHashSet<>();
            for (com.google.mlkit.vision.barcode.common.Barcode result : results) { String key = NfeKey.barcode(result.getRawValue()); if (!key.isEmpty()) keys.add(key); }
            ocr.process(image).addOnSuccessListener(text -> {
                keys.addAll(NfeKey.fromOcr(text.getText()));
                if (keys.isEmpty()) status.setText("Nenhuma chave válida encontrada. Ajuste o recorte, foco ou iluminação e tente novamente.");
                else offerKeys(keys);
            }).addOnFailureListener(e -> status.setText("OCR indisponível nesta imagem. Tente outra foto."))
                .addOnCompleteListener(task -> { busy.set(false); readCrop.setEnabled(true); });
        }).addOnFailureListener(e -> { busy.set(false); readCrop.setEnabled(true); status.setText("Falha na leitura. Tente outra imagem."); });
    }
    private void resumeCamera() {
        photoMode = false; photo.setVisibility(View.GONE); preview.setVisibility(View.VISIBLE);
        capture.setVisibility(View.VISIBLE); capture.setEnabled(camera != null); readCrop.setVisibility(View.GONE); resume.setVisibility(View.GONE);
        zoom.setEnabled(camera != null); torch.setEnabled(camera != null && camera.getCameraInfo().hasFlashUnit()); torch.setText("Lanterna");
        status.setText("Centralize a chave. Toque para focar e use pinça para ampliar.");
    }
    private Button button(LinearLayout row, String title, Runnable action) {
        Button result = new Button(this); result.setText(title); result.setTextSize(11); result.setOnClickListener(v -> action.run());
        row.addView(result, new LinearLayout.LayoutParams(0, -2, 1)); return result;
    }
    @Override protected void onStop() { if (camera != null) camera.getCameraControl().enableTorch(false); torchOn = false; torch.setText("Lanterna"); super.onStop(); }
    @Override protected void onDestroy() {
        destroyed = true; if (analysis != null) analysis.clearAnalyzer(); if (provider != null) provider.unbindAll();
        // Close clients after in-flight tasks finish; executor shutdown rejects no already-running task.
        barcode.close(); ocr.close(); executor.shutdown(); super.onDestroy();
    }
}
