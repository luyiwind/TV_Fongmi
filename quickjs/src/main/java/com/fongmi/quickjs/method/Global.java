package com.fongmi.quickjs.method;

import android.util.Base64;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.fongmi.quickjs.bean.Req;
import com.fongmi.quickjs.utils.Connect;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Parser;
import com.fongmi.quickjs.utils.Proxy;
import com.github.catvod.utils.Trans;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSMethod;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class Global {

    private final ExecutorService executor;
    private final QuickJSContext ctx;
    private final Parser parser;
    private final Timer timer;

    public static Global create(QuickJSContext ctx, ExecutorService executor) {
        return new Global(ctx, executor);
    }

    private Global(QuickJSContext ctx, ExecutorService executor) {
        this.parser = new Parser();
        this.executor = executor;
        this.timer = new Timer();
        this.ctx = ctx;
    }

    public void setProperty() {
        for (Method method : getClass().getMethods()) {
            if (!method.isAnnotationPresent(JSMethod.class)) continue;
            ctx.getGlobalObject().setProperty(method.getName(), args -> {
                try {
                    return method.invoke(this, args);
                } catch (Exception e) {
                    return null;
                }
            });
        }
    }

    private void submit(Runnable runnable) {
        if (!executor.isShutdown()) executor.submit(runnable);
    }

    @Keep
    @JSMethod
    public String s2t(String text) {
        return Trans.s2t(false, text);
    }

    @Keep
    @JSMethod
    public String t2s(String text) {
        return Trans.t2s(false, text);
    }

    @Keep
    @JSMethod
    public String getProxy(Boolean local) {
        return Proxy.getUrl() + "?do=js";
    }

    @Keep
    @JSMethod
    public String js2Proxy(Boolean dynamic, Integer siteType, String siteKey, String url, JSObject headers) {
        return getProxy(true) + "&from=catvod" + "&siteType=" + siteType + "&siteKey=" + siteKey + "&header=" + URLEncoder.encode(headers.stringify()) + "&url=" + URLEncoder.encode(url);
    }

    @Keep
    @JSMethod
    public Object setTimeout(JSFunction func, Integer delay) {
        func.hold();
        schedule(func, delay);
        return null;
    }

    @Keep
    @JSMethod
    public JSObject _http(String url, JSObject options) {
        JSFunction complete = options.getJSFunction("complete");
        if (complete == null) return req(url, options);
        Req req = Req.objectFrom(ctx.stringify(options));
        Connect.to(url, req).enqueue(getCallback(complete, req));
        return null;
    }

    @Keep
    @JSMethod
    public JSObject req(String url, JSObject options) {
        try {
            Req req = Req.objectFrom(ctx.stringify(options));
            Response res = Connect.to(url, req).execute();
            return Connect.success(ctx, req, res);
        } catch (Exception e) {
            return Connect.error(ctx);
        }
    }

    @Keep
    @JSMethod
    public String pd(String html, String rule, String urlKey) {
        return parser.pdfh(html, rule, urlKey);
    }

    @Keep
    @JSMethod
    public String pdfh(String html, String rule) {
        return parser.pdfh(html, rule, "");
    }

    @Keep
    @JSMethod
    public JSArray pdfa(String html, String rule) {
        return JSUtil.toArray(ctx, parser.pdfa(html, rule));
    }

    @Keep
    @JSMethod
    public JSArray pdfl(String html, String rule, String texts, String urls, String urlKey) {
        return JSUtil.toArray(ctx, parser.pdfl(html, rule, texts, urls, urlKey));
    }

    @Keep
    @JSMethod
    public String joinUrl(String parent, String child) {
        return parser.joinUrl(parent, child);
    }

    @Keep
    @JSMethod
    public String aesX(String mode, boolean encrypt, String input, boolean inBase64, String key, String iv, boolean outBase64) {
        try {
            byte[] keyBuf = key.getBytes();
            if (keyBuf.length < 16) keyBuf = Arrays.copyOf(keyBuf, 16);
            byte[] ivBuf = iv == null ? new byte[0] : iv.getBytes();
            if (ivBuf.length < 16) ivBuf = Arrays.copyOf(ivBuf, 16);
            Cipher cipher = Cipher.getInstance(mode + "Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBuf, "AES");
            if (iv == null) cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec);
            else cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivBuf));
            byte[] inBuf = inBase64 ? Base64.decode(input, Base64.DEFAULT) : input.getBytes("UTF-8");
            return outBase64 ? Base64.encodeToString(cipher.doFinal(inBuf), Base64.DEFAULT) : new String(cipher.doFinal(inBuf), "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private Callback getCallback(JSFunction complete, Req req) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response res) {
                submit(() -> complete.call(Connect.success(ctx, req, res)));
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                submit(() -> complete.call(Connect.error(ctx)));
            }
        };
    }

    private void schedule(JSFunction func, int delay) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                submit(func::call);
            }
        }, delay);
    }
}
