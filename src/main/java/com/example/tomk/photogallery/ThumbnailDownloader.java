package com.example.tomk.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ThumbnailDownloader<Token> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private LruCache<String, Bitmap> lruCache;

    Handler mHandler;
    Map<Token, String> requestMap =
            Collections.synchronizedMap(new HashMap<Token, String>());

    Handler mResponseHandler;
    Listener<Token> mListener;

    public interface Listener<Token> {
        void onThumbnailDownloaded(Token token, Bitmap thumbnail);
    }

    public void setListener(Listener<Token> listener) {
        mListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
        lruCache = new LruCache<>(100);
    }

    @Override
    protected void onLooperPrepared() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    @SuppressWarnings("unchecked")
                    Token token = (Token) msg.obj;
                    Log.i(TAG, "Got a request for url: " + requestMap.get(token));
                    handleRequest(token);
                }
            }
        };
    }

    private void handleRequest(final Token token) {
        try {
            final String url = requestMap.get(token);
            if (url == null) {
                return;
            }
            if (lruCache.get(url) != null) {

                final Bitmap bitmap = lruCache.get(url);
                mResponseHandler.post(new Runnable() {
                    public void run() {
                        if (requestMap.get(token) != url) return;
                        requestMap.remove(token);
                        mListener.onThumbnailDownloaded(token, bitmap);
                    }
                });

            } else {
                byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                lruCache.put(url, bitmap);
                Log.i(TAG, "Bitmap created");

                mResponseHandler.post(new Runnable() {
                    public void run() {
                        if (requestMap.get(token) != url) return;
                        requestMap.remove(token);
                        mListener.onThumbnailDownloaded(token, bitmap);
                    }
                });
            }
        } catch (IOException ioe) {
            Log.e(TAG, "Error download image", ioe);
        }
    }

    public void clearQueue() {
        mHandler.removeMessages(MESSAGE_DOWNLOAD);
        requestMap.clear();
    }

    public void queueThumbnail(Token token, String url) {
        requestMap.put(token, url);
        mHandler.obtainMessage(MESSAGE_DOWNLOAD, token).sendToTarget();
        Log.i(TAG, "Got a url:" + url);
    }
}
