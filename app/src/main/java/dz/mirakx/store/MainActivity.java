package dz.mirakx.store;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 101;
    private static final int REQ_PERMS = 102;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        String[] perms;
        if (Build.VERSION.SDK_INT <= 28) {
            perms = new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
        } else {
            perms = new String[]{Manifest.permission.CAMERA};
        }
        boolean need = false;
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need = true;
        }
        if (need) requestPermissions(perms, REQ_PERMS);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= 26) web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);

        WebView.setWebContentsDebuggingEnabled(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(
                        "window.print = function(){ try{ AndroidBridge.printPage(); }catch(e){} };", null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (checkSelfPermission(Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED) {
                            request.grant(request.getResources());
                        } else {
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMS);
                            request.deny();
                        }
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Choisir une sauvegarde"), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    toast("Aucun gestionnaire de fichiers trouvé.");
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    private class Bridge {

        @JavascriptInterface
        public void saveFile(final String name, final String base64) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(data);
                fos.close();
                toast("Enregistré : Téléchargements/" + name);
            } catch (Exception e) {
                try {
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    File out = new File(getExternalFilesDir(null), name);
                    FileOutputStream fos = new FileOutputStream(out);
                    fos.write(data);
                    fos.close();
                    toast("Enregistré : " + out.getAbsolutePath());
                } catch (Exception e2) {
                    toast("Échec de l'enregistrement : " + e2.getMessage());
                }
            }
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        String job = "MirakX Store";
                        pm.print(job, web.createPrintDocumentAdapter(job),
                                new PrintAttributes.Builder().build());
                    } catch (Exception e) {
                        toast("Impression indisponible : " + e.getMessage());
                    }
                }
            });
        }
    }

    private void toast(final String m) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] r = null;
            if (res == RESULT_OK && data != null && data.getData() != null) {
                r = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(r);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            web.evaluateJavascript(
                "(function(){" +
                " var p=document.getElementById('printview');" +
                " if(p && p.style.display==='block'){ closeSheet(); return 'sheet'; }" +
                " var m=document.getElementById('modal');" +
                " if(m && m.innerHTML.trim()!==''){ if(typeof closeCamera==='function'){closeCamera();}else{closeModal();} return 'modal'; }" +
                " if(document.body.classList.contains('menu')){ document.body.classList.remove('menu'); return 'menu'; }" +
                " return 'none';})();",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (value != null && value.contains("none")) {
                            finish();
                        }
                    }
                });
            return true;
        }
        return super.onKeyDown(code, e);
    }
}
