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
public class ScannerActivity extends AppearanceActivity {
    private PreviewView preview;
    private TextView status;
    private ScannerOverlay overlay;
    private android.app.AlertDialog confirmation;
    private volatile int cameraGeneration;
    private volatile long retryAfter;
    private String[] pendingKeys;
    private int selectedKeyIndex = -1;
    private int photoGeneration;
    private boolean readersClosed;
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
        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128).enableAllPotentialBarcodes().build());
    private final TextRecognizer ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> { if (granted) startCamera(); else status.setText("Câmera sem permissão. Use Galeria ou permita o acesso nas configurações do Android."); });
    private final ActivityResultLauncher<String> gallery = registerForActivityResult(
        new ActivityResultContracts.GetContent(), uri -> { if (uri != null) loadPhoto(uri); });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { pendingKeys = state.getStringArray("pendingKeys"); selectedKeyIndex = state.getInt("selectedKeyIndex", -1); }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(12, 12, 12, 12);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(12 + insets.getSystemWindowInsetLeft(), 12 + insets.getSystemWindowInsetTop(),
                12 + insets.getSystemWindowInsetRight(), 12 + insets.getSystemWindowInsetBottom()); return insets;
        });
        addAppearanceControls(root);
        TextView title = new TextView(this); title.setText("Ler chave NF-e · CODE 128 e OCR local"); title.setTextSize(20); root.addView(title);
        status = new TextView(this); status.setText("Toque para focar. Use pinça ou a barra para ampliar. Centralize os 44 dígitos."); root.addView(status);
        FrameLayout frame = new FrameLayout(this);
        preview = new PreviewView(this); preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE); frame.addView(preview);
        overlay = new ScannerOverlay(this); frame.addView(overlay);
        photo = new CropImageView(this); photo.setVisibility(View.GONE); frame.addView(photo);
        int previewHeight = Math.round(Math.max(160, Math.min(320, getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density * .4f)) * getResources().getDisplayMetrics().density);
        root.addView(frame, new LinearLayout.LayoutParams(-1, previewHeight));
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
        button(actions, "Fechar", this::finish); root.addView(actions);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
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
        getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
            @Override public void onResume(androidx.lifecycle.LifecycleOwner owner) {
                // Activity.onPostResume can precede Lifecycle's RESUMED event.
                if (pendingKeys != null && !choosing) offerKeys(new LinkedHashSet<>(Arrays.asList(pendingKeys)));
            }
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
        cameraGeneration++; overlay.clear();
        provider.unbindAll(); camera = null;
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lens).build();
        if (!provider.hasCamera(selector)) { lens = lens == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK; selector = new CameraSelector.Builder().requireLensFacing(lens).build(); }
        Preview live = new Preview.Builder().build(); live.setSurfaceProvider(preview.getSurfaceProvider());
        imageCapture = new ImageCapture.Builder().setResolutionSelector(resolution(1920, 1080)).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        analysis = new ImageAnalysis.Builder().setResolutionSelector(resolution(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        analysis.setAnalyzer(executor, this::analyze);
        if (preview.getViewPort() == null) { preview.postDelayed(() -> { if (!destroyed) { try { bindCamera(); } catch (Exception e) { status.setText("Câmera indisponível."); } } }, 50); return; }
        UseCaseGroup group = new UseCaseGroup.Builder().setViewPort(preview.getViewPort())
            .addUseCase(live).addUseCase(imageCapture).addUseCase(analysis).build();
        camera = provider.bindToLifecycle(this, selector, group);
        torchOn = false; torch.setText("Lanterna"); torch.setEnabled(camera.getCameraInfo().hasFlashUnit()); capture.setEnabled(true); zoom.setEnabled(true);
        camera.getCameraInfo().getZoomState().observe(this, state -> zoom.setProgress(Math.round(state.getLinearZoom() * 100)));
        camera.getCameraInfo().getTorchState().observe(this, state -> { torchOn = state == TorchState.ON; torch.setText(torchOn ? "Apagar" : "Lanterna"); });
    }
    @androidx.annotation.OptIn(markerClass = {androidx.camera.core.ExperimentalGetImage.class, androidx.camera.view.TransformExperimental.class})
    private void analyze(ImageProxy frame) {
        synchronized (busy) {
            if (destroyed || photoMode || android.os.SystemClock.uptimeMillis() < retryAfter || !busy.compareAndSet(false, true)) { frame.close(); return; }
        }
        android.media.Image media = frame.getImage();
        if (media == null) { frame.close(); busy.set(false); runOnUiThread(this::closeReadersWhenIdle); return; }
        final int generation = cameraGeneration;
        final androidx.camera.view.transform.OutputTransform source = sourceTransform(frame);
        InputImage image = InputImage.fromMediaImage(media, frame.getImageInfo().getRotationDegrees());
        Bitmap converted = null;
        try {
            Bitmap raw = frame.toBitmap(); Matrix rotation = new Matrix(); rotation.postRotate(frame.getImageInfo().getRotationDegrees());
            try { converted = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), rotation, true); }
            finally { if (raw != converted) raw.recycle(); }
        } catch (RuntimeException | OutOfMemoryError error) { /* Keep the original media-image reader and OCR available. */ }
        final Bitmap upright = converted;
        FiscalScanner.scan(barcode, image, upright, executor).addOnSuccessListener(detected -> {
            if (destroyed || generation != cameraGeneration || photoMode) { releaseFrame(frame, upright); return; }
            showDetections(detected, source, 900);
            if (!detected.keys().isEmpty()) { releaseFrame(frame, upright); }
            else if (!destroyed && !photoMode && System.currentTimeMillis() - lastOcr > 1500) {
                lastOcr = System.currentTimeMillis();
                ocr.process(image).addOnSuccessListener(text -> {
                    if (!destroyed && generation == cameraGeneration && !photoMode)
                        showDetections(DetectedKeys.text(text), source, 1800);
                }).addOnCompleteListener(task -> releaseFrame(frame, upright));
            } else { releaseFrame(frame, upright); }
        }).addOnFailureListener(error -> releaseFrame(frame, upright));
    }
    private void releaseFrame(ImageProxy frame, Bitmap upright) {
        if (upright != null) upright.recycle();
        frame.close(); busy.set(false); closeReadersWhenIdle();
    }
    private void closeReadersWhenIdle() {
        if (destroyed && !busy.get() && !readersClosed) {
            readersClosed = true;
            barcode.close(); ocr.close(); executor.shutdown();
        }
    }
    @androidx.annotation.OptIn(markerClass = androidx.camera.view.TransformExperimental.class)
    private void showDetections(DetectedKeys detected, androidx.camera.view.transform.OutputTransform source, long duration) {
        // A task already in flight when the dialog was dismissed must not reopen it.
        if (android.os.SystemClock.uptimeMillis() < retryAfter) return;
        String selected = choosing && pendingKeys != null && selectedKeyIndex >= 0 ? pendingKeys[selectedKeyIndex] : null;
        Rect bounds = detected.bounds(selected);
        if (choosing && selected == null) overlay.clear();
        else if (bounds != null) trackBounds(bounds, source, true, duration);
        else if (!choosing) overlay.clear();
        if (!choosing && !detected.keys().isEmpty()) {
            if (detected.keys().size() > 1) {
                overlay.clear(); status.setText("Várias chaves válidas encontradas. Escolha explicitamente a nota desejada.");
            }
            offerKeys(detected.keys());
        }
    }
    @androidx.annotation.OptIn(markerClass = androidx.camera.view.TransformExperimental.class)
    static androidx.camera.view.transform.OutputTransform sourceTransform(ImageProxy frame) {
        androidx.camera.view.transform.ImageProxyTransformFactory transforms = new androidx.camera.view.transform.ImageProxyTransformFactory();
        transforms.setUsingRotationDegrees(true); // ML Kit returns coordinates in the rotated full image.
        transforms.setUsingCropRect(false); // InputImage uses the entire media image, not crop-relative pixels.
        return transforms.getOutputTransform(frame);
    }
    @androidx.annotation.OptIn(markerClass = androidx.camera.view.TransformExperimental.class)
    private void trackBounds(Rect bounds, androidx.camera.view.transform.OutputTransform source, boolean valid, long duration) {
        if (bounds == null || destroyed || photoMode) return;
        androidx.camera.view.transform.OutputTransform destination = preview.getOutputTransform();
        if (destination == null) return;
        RectF mapped = new RectF(bounds);
        new androidx.camera.view.transform.CoordinateTransform(source, destination).mapRect(mapped);
        overlay.track(mapped, valid, duration);
    }
    void offerKeys(Set<String> keys) {
        if (destroyed || isFinishing() || choosing || keys.isEmpty() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        String[] values = keys.stream().map(NfeKey::barcode).filter(k -> !k.isEmpty()).distinct().toArray(String[]::new);
        if (values.length == 0) return;
        choosing = true; pendingKeys = values;
        selectedKeyIndex = values.length == 1 ? 0 : Math.max(-1, Math.min(selectedKeyIndex, values.length - 1));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density); content.setPadding(padding, padding, padding, 0);
        TextView digits = new TextView(this); digits.setTextSize(20); digits.setTypeface(Typeface.MONOSPACE);
        digits.setTextIsSelectable(true); digits.setText(selectedKeyIndex < 0 ? "Selecione uma das chaves encontradas." : values[selectedKeyIndex]); content.addView(digits);
        final int[] selected = {selectedKeyIndex};
        if (values.length > 1) {
            Spinner choices = new Spinner(this);
            choices.setContentDescription("Escolha a chave encontrada");
            String[] labels = new String[values.length + 1]; labels[0] = "Selecione a chave da nota";
            System.arraycopy(values, 0, labels, 1, values.length);
            choices.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    return fiscalChoiceRow(super.getView(position, recycled, parent));
                }
                @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
                    return fiscalChoiceRow(super.getDropDownView(position, recycled, parent));
                }
            });
            choices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selected[0] = position - 1; selectedKeyIndex = position - 1;
                    digits.setText(position == 0 ? "Selecione uma das chaves encontradas." : values[position - 1]);
                    overlay.clear();
                    if (confirmation != null) confirmation.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(position > 0); }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            }); choices.setSelection(selectedKeyIndex + 1); content.addView(choices);
        }
        ScrollView choicesScroll = new ScrollView(this); choicesScroll.addView(content);
        confirmation = new android.app.AlertDialog.Builder(this).setTitle("Chave encontrada")
            .setView(choicesScroll)
            .setPositiveButton("Confirmar chave", (dialog, which) -> {
                if (isFinishing() || selected[0] < 0) return;
                setResult(RESULT_OK, new Intent().putExtra("key", values[selected[0]])); finish();
            }).setNegativeButton("Ler novamente", (dialog, which) -> retryReading())
            .setOnCancelListener(dialog -> retryReading()).create();
        showOwnedDialog(confirmation);
        confirmation.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(selectedKeyIndex >= 0);
    }
    private void retryReading() {
        cameraGeneration++; // Discard results started before the explicit retry.
        pendingKeys = null; selectedKeyIndex = -1; choosing = false; retryAfter = android.os.SystemClock.uptimeMillis() + 1000;
        overlay.clear();
        if (photoMode) resumeCamera();
    }
    private View fiscalChoiceRow(View row) {
        styleOwnedView(row);
        TextView text = (TextView)row;
        text.setSingleLine(false); text.setMaxLines(Integer.MAX_VALUE);
        text.setHorizontallyScrolling(false); text.setEllipsize(null);
        text.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        text.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        // Spinner measures dropdown content with UNSPECIFIED width before clamping
        // its window. Bound the actual row too, so long numeric keys wrap, not clip.
        text.setMaxWidth(Math.max(1,getResources().getDisplayMetrics().widthPixels-Math.round(48*getResources().getDisplayMetrics().density)));
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
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
        cameraGeneration++; photoGeneration++; photoMode = true; overlay.clear(); overlay.setVisibility(View.GONE); if (camera != null) camera.getCameraControl().enableTorch(false);
        torchOn = false; torch.setEnabled(false); zoom.setEnabled(false);
        photo.setBitmap(bitmap); photo.setVisibility(View.VISIBLE); preview.setVisibility(View.INVISIBLE);
        capture.setVisibility(View.GONE); readCrop.setVisibility(View.VISIBLE); resume.setVisibility(View.VISIBLE);
        status.setText("Arraste para mover o recorte; arraste o canto inferior direito para redimensionar. Selecione a chave ou código e toque em Ler recorte.");
    }
    private void readCrop() {
        if (destroyed || !busy.compareAndSet(false, true)) return;
        Bitmap crop = photo.crop(); if (crop == null) { busy.set(false); return; }
        final int generation = photoGeneration;
        readCrop.setEnabled(false); status.setText("Lendo código e texto localmente…");
        InputImage image = InputImage.fromBitmap(crop, 0);
        FiscalScanner.scan(barcode, image, crop, executor).addOnSuccessListener(found -> {
            if (destroyed || !photoMode || generation != photoGeneration) { busy.set(false); if (!destroyed) readCrop.setEnabled(true); closeReadersWhenIdle(); return; }
            Set<String> keys = found.keys();
            ocr.process(image).addOnSuccessListener(text -> {
                if (destroyed || !photoMode || generation != photoGeneration) return;
                keys.addAll(NfeKey.fromOcr(text.getText()));
                if (keys.isEmpty()) status.setText("Nenhuma chave válida encontrada. Ajuste o recorte, foco ou iluminação e tente novamente.");
                else offerKeys(keys);
            }).addOnFailureListener(e -> {
                if (destroyed || !photoMode || generation != photoGeneration) return;
                if (!keys.isEmpty()) offerKeys(keys);
                else status.setText("OCR indisponível nesta imagem. Tente outra foto.");
            })
                .addOnCompleteListener(task -> { busy.set(false); if (!destroyed) readCrop.setEnabled(true); closeReadersWhenIdle(); });
        }).addOnFailureListener(e -> { busy.set(false); if (!destroyed) { readCrop.setEnabled(true); status.setText("Falha na leitura. Tente outra imagem."); } closeReadersWhenIdle(); });
    }
    private void resumeCamera() {
        cameraGeneration++; photoGeneration++; overlay.clear(); overlay.setVisibility(View.VISIBLE); photoMode = false; photo.setVisibility(View.GONE); preview.setVisibility(View.VISIBLE);
        capture.setVisibility(View.VISIBLE); capture.setEnabled(camera != null); readCrop.setVisibility(View.GONE); resume.setVisibility(View.GONE);
        zoom.setEnabled(camera != null); torch.setEnabled(camera != null && camera.getCameraInfo().hasFlashUnit()); torch.setText("Lanterna");
        status.setText("Centralize a chave. Toque para focar e use pinça para ampliar.");
    }
    private Button button(LinearLayout row, String title, Runnable action) {
        Button result = new Button(this); result.setText(title); result.setTextSize(14); result.setOnClickListener(v -> action.run());
        row.addView(result, new LinearLayout.LayoutParams(0, -2, 1)); return result;
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (choosing && pendingKeys != null) { state.putStringArray("pendingKeys", pendingKeys); state.putInt("selectedKeyIndex", selectedKeyIndex); }
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() { if (camera != null) camera.getCameraControl().enableTorch(false); torchOn = false; torch.setText("Lanterna"); super.onStop(); }
    @Override protected void onDestroy() {
        synchronized (busy) { destroyed = true; }
        cameraGeneration++; overlay.clear(); if (confirmation != null) confirmation.dismiss(); if (analysis != null) analysis.clearAnalyzer(); if (provider != null) provider.unbindAll();
        // Regional continuations must finish before closing their clients/executor.
        closeReadersWhenIdle(); super.onDestroy();
    }
}
